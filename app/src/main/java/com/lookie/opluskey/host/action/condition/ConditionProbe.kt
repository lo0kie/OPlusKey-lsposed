package com.lookie.opluskey.host.action.condition

import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.host.action.app.ForegroundApp

/**
 * 条件执行里每一种条件「此刻成不成立」—— 全项目唯一的判据出口。
 *
 * ## 为什么在这里集中
 *
 *  条件会一种一种往上加，每种都要读一个系统开关。读法散在各处时，「同一个条件两处判得不一样」
 *  只能靠人眼查；收成一个 `when` 之后，加一种条件就是加一行。
 *
 * ## 只有派发在这里
 *
 *  具体怎么读拆在 `ConditionChecks.kt`（屏幕 / 充电 / Wi-Fi / 媒体 / 响铃 / 方向）与本文件下半
 *  （耳机 —— 「接着没有」要扫一遍音频输出设备）。加一种条件仍然只动两处：这里加一行、那边加一个函数。
 *
 * ## 权限
 *
 *  这些判据**都不需要额外权限**，而且权限在本项目里是按 **uid** 判的（见 `AndroidManifest.xml`
 *  顶部的说明：界面代码跑在宿主进程里，在清单里声明什么对宿主都没用）。所以这里只用
 *  「宿主 uid 本来就拿得到」的东西：`PowerManager.isInteractive`、sticky 电量广播、
 *  `AudioManager`、`Settings.Global` 读、`resources.configuration`。
 *
 * ## 判不出就返回 `false`
 *
 *  拿不到某个系统服务、读到异常值，一律当**这个条件不成立**（走兜底）。反过来（当成立）会执行
 *  用户完全没预期的那条命令，代价大得多。
 */
internal object ConditionProbe {
    fun matches(
        condition: BranchCondition,
        ctx: Context,
    ): Boolean {
        val kind = condition.kind ?: return false
        if (!condition.isUsable) return false
        return try {
            // 空取值退回这一档的默认值（老的 `wifi` / `media` 就是没有取值的，见 `defaultChoice`）
            judge(kind, condition.effectiveValue, ctx)
        } catch (e: Throwable) {
            XLog.w("分叉场景：判 ${kind.id} 时抛了异常，按不成立处理", e)
            false
        }
    }

    private fun judge(
        kind: ConditionKind,
        value: String,
        ctx: Context,
    ): Boolean =
        when (kind) {
            // 应用那一族两档：差别只在「要不要取反」，判据共用 [appMatches] 一份。
            ConditionKind.APP -> {
                appMatches(value, anyInForeground = true)
            }

            ConditionKind.APP_NOT -> {
                appMatches(value, anyInForeground = false)
            }

            ConditionKind.SCREEN -> {
                screenMatches(ctx, value)
            }

            ConditionKind.CHARGING -> {
                chargingMatches(ctx, value)
            }

            ConditionKind.HEADSET -> {
                headsetMatches(ctx, value)
            }

            ConditionKind.MEDIA -> {
                mediaMatches(ctx, value)
            }

            ConditionKind.WIFI -> {
                wifiMatches(ctx, value)
            }

            ConditionKind.RINGER -> {
                ringerMatches(ctx, value)
            }

            ConditionKind.ORIENTATION -> {
                orientationMatches(ctx, value)
            }

            // 时间区间不读系统开关，只比「现在几点」—— 跨午夜那个「或」在 `TimeRange` 里
            ConditionKind.TIME -> {
                timeMatches(value)
            }

            //  这四个是「开 / 关」型系统开关：判据同一条（`switchMatches`），只有读哪个键不同。
            //  原先一格一分支，四行一模一样的话把这一处 `when` 的复杂度顶到上限。
            ConditionKind.BLUETOOTH, ConditionKind.CELLULAR,
            ConditionKind.AIRPLANE, ConditionKind.FLASHLIGHT,
            -> {
                switchByKind(kind, ctx, value)
            }
        }

    /**
     * 应用那一族的两档共用这一支：[anyInForeground] 为真 = 「选中的**任一**在前台」，
     * 为假 = 「选中的**都不**在前台」（[ConditionKind.APP_NOT]，就是取反）。
     *
     *  一条条件里可以多选（`包1||包2`），所以两边都是对**整组**下结论 —— 与块内多条条件之间的
     *  「且」不是一回事：那是几条同时成立，这里一条内部自己就是「或」。
     *
     *  取值可能带用户前缀（`999@包名`，分身那一档，见 `appKeyOf`）：判据只比包名
     *  —— `ForegroundApp` 拿到的只有包名，本机没有可靠的「前台属于哪个用户」判据，
     *  所以分身与主用户在这一条上等价（不这么做的话，选了分身的那条条件永远不命中）。
     *
     *  **读不到前台应用时返回 `false`**（两档都是）：判不出不能当成「不是」，否则探针失败时
     *  「前台应用以外」那一条会在所有界面上凭空成立。
     */
    private fun appMatches(
        value: String,
        anyInForeground: Boolean,
    ): Boolean {
        val foreground = ForegroundApp.packageName() ?: return false
        val hit = value.split(BranchCondition.APP_SEPARATOR).any { AppTarget.pkg(it.trim()) == foreground }
        return hit == anyInForeground
    }

    /**
     * 四个「开 / 关」型系统开关各读哪个键 —— **键名只在 `ConditionSwitchChecks` 里写一次**，
     * 这里只负责按种类分派（不复述字符串）。
     */
    private fun switchByKind(
        kind: ConditionKind,
        ctx: Context,
        value: String,
    ): Boolean =
        when (kind) {
            ConditionKind.BLUETOOTH -> bluetoothMatches(ctx, value)
            ConditionKind.CELLULAR -> cellularMatches(ctx, value)
            ConditionKind.AIRPLANE -> airplaneMatches(ctx, value)
            ConditionKind.FLASHLIGHT -> flashlightMatches(ctx, value)
            else -> false
        }

    /**
     * **耳机状态**：`connected`（接着）/ `disconnected`（没接）。
     *
     *  `disconnected` 直接取反 —— 设备要么在、要么不在，没有「第三种都不知道」的状态
     *  （与屏幕那三档不同：探针读不到时那三档都不成立）。
     */
    private fun headsetMatches(
        ctx: Context,
        value: String,
    ): Boolean {
        //  拿不到 AudioManager = **判不出**，两档都不成立（与 `ConditionChecks` 里充电 / 媒体
        //  那两档同一口径）。不能把 `null` 当成「没接耳机」—— `disconnected` 是取反得来的，
        //  那样它会在读不到服务时凭空成立。
        val connected = hasHeadset(ctx) ?: return false
        return when (value) {
            "disconnected" -> !connected
            "connected" -> connected
            else -> false
        }
    }

    /**
     * 接着耳机 —— 有线 / USB / 蓝牙 A2DP 三种都算；**取不到 AudioManager 返回 `null`**。
     *
     *  `isWiredHeadsetOn` 已废弃且在部分机型上恒 `false`，所以走 `getDevices(GET_DEVICES_OUTPUTS)`
     *  逐个看类型：蓝牙那档只算 A2DP（那是听媒体用的），不算 SCO（通话用）。
     */
    private fun hasHeadset(ctx: Context): Boolean? {
        val manager = audio(ctx) ?: return null
        val types =
            manager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .orEmpty()
                .map { it.type }
        return types.any {
            it == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    }
}

// 「条件执行」里那些**带取值的判据** —— 一组条件一条，`ConditionProbe` 只负责派发。
//
// 从 `ConditionProbe` 拆出来：那边是「哪一种条件怎么判」的总入口，随着条件种类往上加，
// 一个 object 里塞进了十四个函数（detekt 的 `TooManyFunctions` 顶到 11 就报）。
// 拆法按「有没有取值」切：这里全是「要看 value 才成立」的档（屏幕 / 充电 / Wi-Fi / 媒体 /
// 响铃 / 方向），`ConditionProbe` 留下派发与那几个无取值的。
//
//  这些函数都是**顶层 internal**：同一个 `when` 里派发时不需要带接收者，
//  与 `ConditionProbe` 里原来那几个私有函数一一对应（逻辑一字未改）。

/**
 * **屏幕状态**：`on`（亮屏）/ `off`（熄屏）/ `locked`（锁屏）—— **三档互斥**。
 *
 *  判据是两个探针的组合，不能只看其中一个（真机上三种状态各错判过一次）：
 *  · `PowerManager.isInteractive` = 屏幕亮着没有；
 *  · `KeyguardManager.isKeyguardLocked` = **要解锁没有** —— 注意它**灭屏时也是 true**
 *    （点亮之后仍然要解锁），所以它单独不能当「锁屏」用，否则熄屏会被判成锁屏。
 *
 *  于是三档这样切：
 *  · `off`：灭着（**只看** `isInteractive`，不读锁屏）；
 *  · `locked`：**亮着且锁着**（锁屏界面就在眼前）；
 *  · `on`：亮着且没锁。
 *
 *  探针读不到 = **判不出**：用到它的那几档不成立（契约见 `ConditionProbe` 头部）。
 */
internal fun screenMatches(
    ctx: Context,
    value: String,
): Boolean {
    val interactive = power(ctx)?.isInteractive
    val locked = keyguard(ctx)?.isKeyguardLocked
    return when (value) {
        //  锁屏必须**同时**亮着：灭屏时系统同样报 locked（要点亮才谈得上解锁）
        "locked" -> interactive == true && locked == true

        //  亮屏 = 亮着且没锁
        "on" -> interactive == true && locked == false

        //  熄屏只看屏幕本身 —— 这一档不读锁屏，灭着时 `locked` 恰好也是真的
        "off" -> interactive == false

        else -> false
    }
}

/**
 * **充电状态**：`charging`（充电中，含充满）/ `idle`（没在充电）。
 *
 *  `idle` 就是「不在充电」，取反即可 —— 与屏幕那两档不同：这两个是真正的正反两面
 *  （电量广播要么在充、要么不在），不存在「第三种都不知道」的状态。
 */
internal fun chargingMatches(
    ctx: Context,
    value: String,
): Boolean {
    //  读不到电量广播 = **判不出**，两档都不成立。不能按 `false` 当成「没在充电」——
    //  那样 `idle` 会在读不到时凭空成立（与 `ConditionProbe` 头部那条契约相反）。
    val charging = isCharging(ctx) ?: return false
    return when (value) {
        "idle" -> !charging
        "charging" -> charging
        else -> false
    }
}

/**
 * Wi-Fi **开关状态**：`on` / `off`（`WIFI_ON` 1 = 开）。取不到或认不出的取值 ⇒ 不成立。
 *
 *  读不到（键不在 / 值不是 0/1）**不能按「关」算**：那会让 `off` 在取不到时凭空成立，去执行一条
 *  用户没预期的命令。`-1` 是哨位 —— `WIFI_ON` 的合法取值是 0（关）/ 1（开）/ 2（正在开）/ 3（正在关）。
 */
internal fun wifiMatches(
    ctx: Context,
    value: String,
): Boolean {
    val state =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.WIFI_ON, -1) }
            .getOrDefault(-1)
    if (state != 0 && state != 1) {
        //  2 / 3 是「正在开 / 正在关」，与读不到同一档：此刻既不算开也不算关。
        XLog.d("Wi-Fi 状态读不到确定的开/关（WIFI_ON=$state）⇒ 这条条件按不成立")
        return false
    }
    return when (value) {
        "off" -> state == 0
        "on" -> state == 1
        else -> false
    }
}

/**
 * 媒体**播放状态**：`playing` / `paused`。
 *
 *  `isMusicActive` 为 `true` = 眼下有音频在放。反过来只能说明「没有音频在放」——
 *  可能是暂停、也可能是根本没起过播放器。对「暂停中」这一档来说那就是用户的意思
 *  （现在没声音），所以直接取反；要做到「真有会话且暂停」得读 `MediaSessionManager`，
 *  那需要通知读取权限，宿主 uid 拿不到。
 */
internal fun mediaMatches(
    ctx: Context,
    value: String,
): Boolean {
    //  取不到 AudioManager = **判不出**，两档都不成立。不能把 `null` 当成「没在放」——
    //  那样 `paused` 会在系统服务拿不到时凭空成立，去执行一条用户没预期的命令
    //  （`ConditionProbe` 头部的契约：判不出就不成立）。
    val playing = audio(ctx)?.isMusicActive ?: return false
    return when (value) {
        "paused" -> !playing
        "playing" -> playing
        else -> false
    }
}

/** 响铃档位：`normal` / `vibrate` / `silent`。取不到或认不出的取值 ⇒ 不成立。 */
internal fun ringerMatches(
    ctx: Context,
    value: String,
): Boolean {
    val mode = audio(ctx)?.ringerMode ?: return false
    val now =
        when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            AudioManager.RINGER_MODE_SILENT -> "silent"
            else -> return false
        }
    return now == value
}

/** 屏幕方向：`portrait` / `landscape`。取不到或认不出的取值 ⇒ 不成立。 */
internal fun orientationMatches(
    ctx: Context,
    value: String,
): Boolean {
    val now =
        when (ctx.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            else -> return false
        }
    return now == value
}

// 「条件执行」里那几个**开 / 关型的系统开关** —— 从 `ConditionChecks` 拆出来。
//
//  拆的判据是「读哪张表」：这里全是 `Settings.Global` / `Settings.Secure` 里的 `0/1`，
//  而那边（`ConditionChecks`）是亮屏 / 充电 / Wi-Fi / 媒体 / 响铃 / 方向这些要靠服务问的档。
//  两边凑在一个文件里时，`ConditionChecks` 的函数数正好顶到 detekt 的上限。
//
//  这些函数仍是**顶层 internal**：`ConditionProbe` 的 `when` 里派发时不需要带接收者。

/**
 * 一个「开 / 关」型的系统开关 —— 这几个键都是 `Settings.Global` 里的 `0/1`。
 *
 *  与 [wifiMatches] 同一个读法：取不到（键不存在）时按 `0` 算，也就是「关着」。
 *  这一档不能读失败就整体不成立：那样「蓝牙关 = 成立」在没蓝牙的机器上永远不成立，
 *  而用户的规则会静默落到兜底 —— 宁可当它「关着」。
 *
 *  **键名用字面量**（`Settings.Global.BLUETOOTH_ON` 是 @hide）：与宿主侧
 *  `SettingSwitch.readOn` 读的是同一批键，两处保持一致的写法，也不依赖编译期的隐藏字段。
 */
internal fun switchMatches(
    ctx: Context,
    key: String,
    value: String,
): Boolean {
    val on = Settings.Global.getInt(ctx.contentResolver, key, 0) == 1
    return when (value) {
        "off" -> !on
        "on" -> on
        else -> false
    }
}

/** 蓝牙开关（`bluetooth_on`）。 */
internal fun bluetoothMatches(
    ctx: Context,
    value: String,
): Boolean = switchMatches(ctx, "bluetooth_on", value)

/** 移动数据（`mobile_data`）。 */
internal fun cellularMatches(
    ctx: Context,
    value: String,
): Boolean = switchMatches(ctx, "mobile_data", value)

/** 飞行模式（`airplane_mode_on`）。 */
internal fun airplaneMatches(
    ctx: Context,
    value: String,
): Boolean = switchMatches(ctx, "airplane_mode_on", value)

/**
 * 手电筒**开关状态**：`on` / `off`。
 *
 *  读 `Settings.Secure.flashlight_enabled` —— SystemUI 的 `FlashlightControllerImpl` 在 torch
 *  回调里同步的那一位（`1` 开 / `0` 关，见 `docs/device/actions/toggles.md` 23.1），宿主
 *  `platform_app` 直接读得到，不用开相机、也不用 root。
 *
 *  与 [switchMatches] 相反，这里**读不到就判不出**：这个键在第一次开灯之前可能根本不存在，
 *  按 `0` 当成「关着」会让一条「手电筒关着」的场景在从没开过灯的机器上凭空成立。
 */
internal fun flashlightMatches(
    ctx: Context,
    value: String,
): Boolean {
    val on =
        runCatching { Settings.Secure.getInt(ctx.contentResolver, "flashlight_enabled") }
            .getOrNull()
            ?: return false
    return when (value) {
        "off" -> on == 0
        "on" -> on != 0
        else -> false
    }
}
