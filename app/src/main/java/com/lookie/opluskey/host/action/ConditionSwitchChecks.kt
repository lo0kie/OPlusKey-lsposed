package com.lookie.opluskey.host.action

import android.content.Context
import android.provider.Settings

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
