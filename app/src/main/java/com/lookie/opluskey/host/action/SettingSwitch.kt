package com.lookie.opluskey.host.action

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.lookie.opluskey.bridge.SettingBridge
import com.lookie.opluskey.bridge.host.FlashlightController
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.SwitchKind

/**
 * 「系统开关」那几条开关的**宿主侧** —— 读当前状态、算出目标值，再交给 system_server 执行。
 *
 * ## 为什么读在宿主、写在 system_server
 *
 *  · **写**要权限：`setWifiEnabled` / `setAirplaneMode` 这些要 `NETWORK_SETTINGS`、`MODIFY_PHONE_STATE`
 *    之类的特权，宿主（`platform_app`）拿不到；模块本来就注入在 system_server（uid 1000），那边过得了。
 *  · **读**要 `ContentResolver`：`Settings` 在宿主里随便读（这些键都是公开可读的），而 system_server
 *    里借来的 Context 用 ContentResolver **必 NPE**（见 `docs/device/lsposed.md` 六之五）。
 *
 *  所以两边各做一件自己做得来的事：宿主说「它现在是开着的，切成关」，system_server 只负责切。
 *  这也顺带解决了「读不到状态就不能取反」—— 读不到就**不做**，绝不盲调（盲调会把开着的关掉）。
 *
 * ## 自动旋转是例外
 *
 *  「锁没锁」只有 `window` 自己知道（`isRotationFrozen`），没有对应的 Settings 键可读，
 *  所以这一条只把 id 转过去，让 [SettingBridge] 自己读、自己取反。
 */
internal object SettingSwitch {
    /**
     * 「写 Settings 就够了」的那几条 —— 值是 `Settings.Global` 里的键名。
     *
     *  写这几个键等于替系统按了开关：`mobile_data` 由 Phone 的 `DataEnabledSettings` 观察，
     *  `low_power` 由 `PowerManagerService` 观察（真机验过：写完 `dumpsys power` 里
     *  `Battery Saver is currently: ON`）。
     */
    private val SETTINGS_KEYS =
        mapOf(
            "cell" to "mobile_data",
            "powersave" to "low_power",
        )

    /**
     * 另外几条也是 `Settings.System` 里的 0 / 1 键 —— 与 [SETTINGS_KEYS] 分开，那张表是
     * `Settings.Global` 的键名。
     *
     *  它们各自的服务盯着这个键：写完颜色显示服务当场翻 `eyeProtectEnabled`（护眼），
     *  电池应用立刻把旁路目标下发到充电 IC（旁路充电）。
     */
    private val SYSTEM_BOOL_KEYS =
        mapOf(
            SwitchKind.EYEPROTECT.id to KEY_EYE_PROTECT,
            SwitchKind.BYPASSCHARGE.id to KEY_BYPASS_CHARGE,
        )

    /**
     * 这几条的「现在开着没有」只有 system_server 那边读得到，宿主把 id 整个转过去、由那边读状态取反：
     * 旋转锁不在 Settings 里、`nfc_on` 读不到、极暗的状态只在颜色显示服务里、
     * 热点更是连 Settings 键都没有（只有 `wifi` 服务的 `getWifiApEnabledState()` 知道）。
     */
    private val SYSTEM_SIDE_READS: Set<String> =
        setOf(
            SwitchKind.ROTATION.id,
            SwitchKind.NFC.id,
            SwitchKind.EXTRADIM.id,
            SwitchKind.HOTSPOT.id,
        )

    /** 自动亮度那个键（`Settings.System`）—— 1 = 自动、0 = 手动。 */
    private const val KEY_BRIGHTNESS_MODE = "screen_brightness_mode"

    /** 定位那个键（`Settings.Secure`）—— 由 `LocationManagerService` 维护，见 [SwitchKind.LOCATION]。 */
    private const val KEY_LOCATION_MODE = "location_mode"

    /** 暗色模式那个键（`Settings.Secure`）—— `ui_night_mode`：1 = 亮、2 = 暗。 */
    private const val KEY_NIGHT_MODE = "ui_night_mode"

    /** 护眼模式那个键（`Settings.System`）—— 颜色显示服务盯着它，见 [SwitchKind.EYEPROTECT]。 */
    private const val KEY_EYE_PROTECT = "oplus_customize_eye_protect_enable"

    /** 旁路充电那个键（`Settings.System`）—— 电池应用的 `BypassChargeController` 盯着它。 */
    private const val KEY_BYPASS_CHARGE = "bypass_charge_state"

    /** `location_mode` 的两档：0 = 关；3 = 高精度（1 仅设备 / 2 省电也算开着）。 */
    private const val LOCATION_MODE_OFF = 0

    private const val LOCATION_MODE_HIGH_ACCURACY = 3

    /**
     * `setting:<id>[:on|:off]` 的分派 —— 手电筒那一支**不走**下面那条链路。
     *
     *  它的真开关就在宿主里（[FlashlightController] 调 `CameraManager.setTorchMode`），
     *  交给 [toggle] 会发一条 system_server 那边没人接的广播（那边只接了
     *  wifi / bt / airplane / rotation / nfc / dnd / darkmode）。
     *  清单里手电筒与免打扰都收进了 `setting:` 族（三态要能把目标状态写进 token），执行各走各的通道。
     */
    fun toggleOrFlashlight(
        context: Context?,
        action: Action.Setting,
        flashlight: FlashlightController,
        onFailure: (CommandFailure) -> Unit,
    ) {
        if (action.id == SwitchKind.FLASHLIGHT.id) {
            val on = action.on
            if (on == null) flashlight.toggle() else flashlight.setEnabled(on)
            return
        }
        toggle(context, action.id, action.on, onFailure)
    }

    /**
     * 切一下 [id]。
     *
     * @param on 目标状态：`null` = 切换（先读当前状态再取反）、`true` / `false` = 明确打开 / 关闭
     *   （这两种**不读状态**：用户已经把目标写死了，读不到也能执行）
     * @param onFailure 读不到状态 / 通道发不出去时的回调（与 `shell:` 动作同一套分档）
     */
    fun toggle(
        context: Context?,
        id: String,
        on: Boolean?,
        onFailure: (CommandFailure) -> Unit,
    ) {
        if (context == null) {
            XLog.w("没有 Context，系统开关 $id 跳过")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        // 明确的开 / 关不需要读状态：直接把目标交给执行侧
        if (on != null) {
            XLog.w("系统开关：$id 明确${if (on) "打开" else "关闭"}")
            write(context, id, on, onFailure)
            return
        }
        // 这几条没有宿主读得到的当前状态（见 [SYSTEM_SIDE_READS]），状态由 system_server
        // 那边的服务自己报，这里只把 id 转过去。
        if (id in SYSTEM_SIDE_READS) {
            XLog.w("系统开关：$id 交给 system_server 自己读状态取反")
            SettingBridge.send(context, id, null)
            return
        }
        val current = readOn(context, id)
        if (current == null) {
            XLog.w("系统开关：读不到 $id 当前状态，这次不切（盲调会把开着的关掉）")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        val target = !current
        XLog.w("系统开关：$id 现在是${if (current) "开" else "关"}，切成${if (target) "开" else "关"}")
        write(context, id, target, onFailure)
    }

    /**
     * 把目标状态写下去：能直接写 `Settings` 的就写，其余交给 system_server。
     *
     *  这几条系统自己盯着 Settings 里的键（Phone 的 `DataEnabledSettings` / `PowerManagerService`
     *  都有 ContentObserver，`LocationManagerService` 也盯着 `location_mode`），宿主有
     *  `WRITE_SECURE_SETTINGS`，写一下就够了。
     */
    private fun write(
        context: Context,
        id: String,
        target: Boolean,
        onFailure: (CommandFailure) -> Unit,
    ) {
        //  自动亮度写的是**别的表**（`Settings.System`），所以不进 [SETTINGS_KEYS]
        //  （那张表是「`Settings.Global` 键名」）。
        //  极暗**没有**这一支：它要 `CONTROL_DISPLAY_COLOR_TRANSFORMS`，宿主拿不到这个权限，
        //  所以落到最后那句 `SettingBridge.send`，由 system_server 去调颜色显示服务
        //  （见 `SettingCalls.extraDim`）。
        if (id == SwitchKind.AUTOBRIGHTNESS.id) {
            writeInt(context, secure = false, key = KEY_BRIGHTNESS_MODE, value = if (target) 1 else 0, onFailure = onFailure)
            return
        }
        //  定位：`location_mode` 不是 0/1 —— 0 = 关，1 = 仅设备 / 2 = 省电 / 3 = 高精度都算开。
        //  打开时给 3（高精度，系统默认那一档）；关闭只需把模式压成 0。
        if (id == SwitchKind.LOCATION.id) {
            writeInt(
                context,
                secure = true,
                key = KEY_LOCATION_MODE,
                value = if (target) LOCATION_MODE_HIGH_ACCURACY else LOCATION_MODE_OFF,
                onFailure = onFailure,
            )
            return
        }
        //  护眼模式与旁路充电都是 `Settings.System` 里的 0/1 键（见 [SYSTEM_BOOL_KEYS]）。
        val systemKey = SYSTEM_BOOL_KEYS[id]
        if (systemKey != null) {
            writeInt(context, secure = false, key = systemKey, value = if (target) 1 else 0, onFailure = onFailure)
            return
        }
        val key = SETTINGS_KEYS[id]
        if (key != null) {
            // `putInt` 自己返回布尔：false = 写入被拒（权限 / 只读档），它与「抛异常」是两件事，
            //  只看 `isSuccess` 会把被拒报成成功。
            val wrote =
                try {
                    Settings.Global.putInt(context.contentResolver, key, if (target) 1 else 0)
                } catch (e: Throwable) {
                    XLog.w("系统开关：写 $key 失败：${e.javaClass.simpleName}")
                    false
                }
            if (!wrote) {
                XLog.w("系统开关：写 $key 被拒（putInt=false），这次没切成")
                onFailure(CommandFailure.UNKNOWN)
            }
            return
        }
        SettingBridge.send(context, id, target)
    }

    /**
     * 写 `Settings.System` / `Settings.Secure` 里的一个 int 键（自动亮度、定位走这里）——
     * 与 [write] 那条 Global 同一种报错口径。
     *
     * @param secure true = 写 `Settings.Secure`（定位）、false = `Settings.System`（自动亮度）
     * @param value 要写的值；**不一定是 0/1**（定位的模式是 0..3），所以这一层收 int 而不是布尔
     */
    private fun writeInt(
        context: Context,
        secure: Boolean,
        key: String,
        value: Int,
        onFailure: (CommandFailure) -> Unit,
    ) {
        val wrote =
            try {
                if (secure) {
                    Settings.Secure.putInt(context.contentResolver, key, value)
                } else {
                    Settings.System.putInt(context.contentResolver, key, value)
                }
            } catch (e: Throwable) {
                XLog.w("系统开关：写 $key 失败：${e.javaClass.simpleName}")
                false
            }
        if (!wrote) {
            XLog.w("系统开关：写 $key 被拒（putInt=false），这次没切成")
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * 读当前状态；读不到（键不存在 / 值不是 0/1）返回 `null`。
     *
     *  分两张表走：[READ_GLOBAL_KEYS] 那几条是 `Settings.Global` 的 0 / 1 键；
     *  其余几条交给 [readLocalBool]（表不同，或者值不是 0 / 1 —— 定位的模式是 0..3）。
     *  这几个键都由对应服务自己维护（`svc` / `cmd` 切过之后会跟着翻，真机验过其中四个），
     *  所以「宿主读、系统写」不会两头打架。
     */
    private fun readOn(
        context: Context,
        id: String,
    ): Boolean? {
        val cr = context.contentResolver
        val globalKey = READ_GLOBAL_KEYS[id]
        return if (globalKey != null) onOff(Settings.Global.getInt(cr, globalKey, -1)) else readLocalBool(cr, id)
    }

    /**
     * `Settings.Global` 里那几条 0 / 1 的键。
     *
     *  读不到时 `getInt` 给 -1（键不存在 / 类型不对）—— 那就是「不知道」，不能当「关着」。
     */
    private val READ_GLOBAL_KEYS =
        mapOf(
            "wifi" to "wifi_on",
            "bt" to "bluetooth_on",
            "cell" to "mobile_data",
            "airplane" to "airplane_mode_on",
            "powersave" to "low_power",
            // 免打扰：由 NotificationManagerService 自己维护（ZEN_MODE_OFF = 0，其余三档都算「开着」）。
            // 写不走 Settings，得由 system_server 调 `setZenMode` —— 见 `SettingCalls.dnd`。
            SwitchKind.DND.id to "zen_mode",
        )

    /**
     * 其余几条的读法 —— 表不同（`Settings.System` / `Settings.Secure`），或者值不是 0 / 1。
     */
    private fun readLocalBool(
        cr: ContentResolver,
        id: String,
    ): Boolean? =
        when (id) {
            // ui_night_mode：MODE_NIGHT_NO = 1、MODE_NIGHT_YES = 2（0 / auto 都算没开）
            SwitchKind.DARKMODE.id -> {
                nightMode(Settings.Secure.getInt(cr, KEY_NIGHT_MODE, -1))
            }

            // 自动亮度：`Settings.System.screen_brightness_mode`（1 = 自动、0 = 手动）
            SwitchKind.AUTOBRIGHTNESS.id -> {
                onOff(Settings.System.getInt(cr, KEY_BRIGHTNESS_MODE, -1))
            }

            // 定位：`Settings.Secure.location_mode`（0 = 关，1 / 2 / 3 都是开着）
            SwitchKind.LOCATION.id -> {
                onOff(Settings.Secure.getInt(cr, KEY_LOCATION_MODE, -1))
            }

            // 护眼模式 / 旁路充电：`Settings.System` 里的 0 / 1
            SwitchKind.EYEPROTECT.id -> {
                onOff(Settings.System.getInt(cr, KEY_EYE_PROTECT, -1))
            }

            SwitchKind.BYPASSCHARGE.id -> {
                onOff(Settings.System.getInt(cr, KEY_BYPASS_CHARGE, -1))
            }

            // 极暗**不在这里读**：状态只有颜色显示服务知道，宿主没有那个权限（见 `toggle` 里
            // 把它整条转给 system_server 的那一支）。`Settings.*.reduce_brightness` 这两个键
            // 在这台机器上没人维护，读它只会得到假的「关着」。
            else -> {
                null
            }
        }

    /** `0/1` 那种开关的判读。 */
    private fun onOff(value: Int): Boolean? = if (value < 0) null else value > 0

    /** 深色模式那一档：只有 2 算「开着」。 */
    private fun nightMode(value: Int): Boolean? = if (value < 0) null else value == 2
}
