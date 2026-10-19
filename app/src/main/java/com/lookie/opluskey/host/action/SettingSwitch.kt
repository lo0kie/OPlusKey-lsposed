package com.lookie.opluskey.host.action

import android.content.Context
import android.provider.Settings
import com.lookie.opluskey.bridge.SettingBridge
import com.lookie.opluskey.core.log.XLog

/**
 * 「系统开关」那几条开关的**宿主侧** —— 读当前状态、算出目标值，再交给 system_server 执行。
 *
 * ## 为什么读在宿主、写在 system_server
 *
 *  · **写**要权限：`setWifiEnabled` / `setAirplaneMode` 这些要 `NETWORK_SETTINGS`、`MODIFY_PHONE_STATE`
 *    之类的特权，宿主（`platform_app`）拿不到；模块本来就注入在 system_server（uid 1000），那边过得了。
 *  · **读**要 `ContentResolver`：`Settings` 在宿主里随便读（这些键都是公开可读的），而 system_server
 *    里借来的 Context 用 ContentResolver **必 NPE**（见 `docs/device-findings.md` 六之五）。
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
     * 切一下 [id]。
     *
     * @param onFailure 读不到状态 / 通道发不出去时的回调（与 `shell:` 动作同一套分档）
     */
    fun toggle(
        context: Context?,
        id: String,
        onFailure: (CommandFailure) -> Unit,
    ) {
        if (context == null) {
            XLog.w("没有 Context，系统开关 $id 跳过")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        // 这两条没有可读的 Settings 键（旋转锁不在 Settings 里、`nfc_on` 也读不到），
        // 状态由 system_server 那边的服务自己报，这里只把 id 转过去
        if (id == "rotation" || id == "nfc") {
            XLog.w("系统开关：$id 交给 system_server 自己读状态取反")
            SettingBridge.send(context, id, null)
            return
        }
        val on = readOn(context, id)
        if (on == null) {
            XLog.w("系统开关：读不到 $id 当前状态，这次不切（盲调会把开着的关掉）")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        val target = !on
        XLog.w("系统开关：$id 现在是${if (on) "开" else "关"}，切成${if (target) "开" else "关"}")
        // 这两条系统自己盯着 Settings 里的键（Phone 的 DataEnabledSettings / PowerManagerService
        //  都有 ContentObserver），宿主有 WRITE_SECURE_SETTINGS，写一下就够了
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
     * 读当前状态；读不到（键不存在 / 值不是 0/1）返回 `null`。
     *
     *  这几个键都由对应服务自己维护（`svc` / `cmd` 切过之后会跟着翻，真机验过其中四个），
     *  所以「宿主读、系统写」不会两头打架。
     */
    private fun readOn(
        context: Context,
        id: String,
    ): Boolean? {
        val cr = context.contentResolver

        // 读不到时这两个 getInt 都返回 -1（键不存在 / 类型不对）—— 那就是「不知道」，不能当「关着」
        fun value(
            key: String,
            secure: Boolean = false,
        ): Int = if (secure) Settings.Secure.getInt(cr, key, -1) else Settings.Global.getInt(cr, key, -1)
        return when (id) {
            "wifi" -> onOff(value("wifi_on"))

            "bt" -> onOff(value("bluetooth_on"))

            "cell" -> onOff(value("mobile_data"))

            "airplane" -> onOff(value("airplane_mode_on"))

            "powersave" -> onOff(value("low_power"))

            // ui_night_mode：MODE_NIGHT_NO = 1、MODE_NIGHT_YES = 2（0 / auto 都算没开）
            "darkmode" -> nightMode(value("ui_night_mode", secure = true))

            else -> null
        }
    }

    /** `0/1` 那种开关的判读。 */
    private fun onOff(value: Int): Boolean? = if (value < 0) null else value > 0

    /** 深色模式那一档：只有 2 算「开着」。 */
    private fun nightMode(value: Int): Boolean? = if (value < 0) null else value == 2
}
