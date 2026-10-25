package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ConditionKind
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Battery
import io.github.lyxnx.compose.ui.tablericons.outline.BatteryCharging
import io.github.lyxnx.compose.ui.tablericons.outline.Bell
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
import io.github.lyxnx.compose.ui.tablericons.outline.Bluetooth
import io.github.lyxnx.compose.ui.tablericons.outline.BluetoothOff
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignal4
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignalOff
import io.github.lyxnx.compose.ui.tablericons.outline.Clock
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobile
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.Headphones
import io.github.lyxnx.compose.ui.tablericons.outline.HeadphonesOff
import io.github.lyxnx.compose.ui.tablericons.outline.Lock
import io.github.lyxnx.compose.ui.tablericons.outline.Music
import io.github.lyxnx.compose.ui.tablericons.outline.Plane
import io.github.lyxnx.compose.ui.tablericons.outline.PlaneOff
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPause
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPlay
import io.github.lyxnx.compose.ui.tablericons.outline.Rotate
import io.github.lyxnx.compose.ui.tablericons.outline.Wifi
import io.github.lyxnx.compose.ui.tablericons.outline.WifiOff

// 分叉条件的字形 —— 种类一颗、取值一颗。
//
// 从 `ActionIcons.kt` 拆出来：那边是「动作」的字形表，条件是另一族（`ConditionKind`），
// 两族各自的 when 都在长，合在一个文件里迟早顶到 detekt 的函数数上限；
// 拆开之后「加一种条件」只碰这一个文件。

/**
 * 分叉**条件种类**的字形 —— 「选择条件」那张菜单里，一行一个条件。
 *
 *  每种条件共用一颗 `arrows-split` 的话，菜单就是一串长得一模一样的字，
 *  只剩标题能区分；每档各给一颗，一眼能扫到「我要的是哪一个」。
 *
 *  **全项目唯一一处**（与 `systemSettingIcon` 同一条规矩）：加一种 [ConditionKind] 时，
 *  在这支 `when` 里加一行 —— 漏了会掉到 `TablerIcons.Outline.Dots`。
 *
 *  屏幕那一档给的是「一块屏」（`device-mobile`）而不是灯泡：它说的是屏幕的状态，
 *  灯泡更像「手电筒」；取值行里「锁屏」那一档另有锁头。
 */
internal fun conditionIcon(kind: ConditionKind): ImageVector =
    when (kind) {
        ConditionKind.APP -> PageIcons.AppFront

        ConditionKind.SCREEN -> TablerIcons.Outline.DeviceMobile

        ConditionKind.CHARGING -> TablerIcons.Outline.BatteryCharging

        ConditionKind.HEADSET -> TablerIcons.Outline.Headphones

        ConditionKind.MEDIA -> TablerIcons.Outline.Music

        ConditionKind.WIFI -> TablerIcons.Outline.Wifi

        ConditionKind.RINGER -> TablerIcons.Outline.Bell

        ConditionKind.ORIENTATION -> TablerIcons.Outline.Rotate

        //  时间区间给「一个钟」：它不是某一刻，但同一族里只有它是「几点」这一维
        ConditionKind.TIME -> TablerIcons.Outline.Clock

        ConditionKind.BLUETOOTH -> TablerIcons.Outline.Bluetooth

        //  移动数据用信号格（与「系统开关」里那格同一颗，那格也是 `cell`）
        ConditionKind.CELLULAR -> TablerIcons.Outline.CellSignal4

        ConditionKind.AIRPLANE -> TablerIcons.Outline.Plane
    }

/**
 * 条件**取值**的字形 —— 同一档条件里那几行，各自含义差得远（正常 / 振动 / 静音、
 * 竖屏 / 横屏、开 / 关），共用一颗同样只剩文字能分。
 *
 *  分派只做「哪一族」，每族各自一颗函数：`when` 里再嵌 `when` 会让复杂度一路顶到
 *  detekt 的上限（加过两档后就是 19）。
 *
 *  @param kind 哪一档条件
 *  @param value 具体取值（[ConditionKind.choices] 里的那一串）
 */
internal fun conditionChoiceIcon(
    kind: ConditionKind,
    value: String,
): ImageVector =
    when (kind) {
        ConditionKind.RINGER -> {
            ringerChoiceIcon(value)
        }

        ConditionKind.ORIENTATION -> {
            orientationChoiceIcon(value)
        }

        //  其余「两档」条件全在下面那张表里：一档一个 `if` 写在这里，复杂度会跟着
        //  `ConditionKind` 一路涨（加两档就从 18 到 20，detekt 是 15）
        else -> {
            choiceIconsOf(kind, value) ?: conditionIcon(kind)
        }
    }

/** 两档条件的一对字形：取 [firstValue] 时用 [onFirst]，其余用 [onSecond]。 */
private class ChoiceIcons(
    val firstValue: String,
    val onFirst: ImageVector,
    val onSecond: ImageVector,
)

/**
 * 「只有两档」的那些条件 —— 关 / 开、暂停 / 播放、未连接 / 已连接、锁屏 / 亮屏、未充电 / 充电中。
 *
 *  做成表而不是 `when` 分支：每加一种条件只是多一行数据，不会把谁的分析复杂度顶上去。
 */
private val CHOICE_ICONS: Map<ConditionKind, ChoiceIcons> =
    mapOf(
        ConditionKind.WIFI to ChoiceIcons("off", TablerIcons.Outline.WifiOff, TablerIcons.Outline.Wifi),
        ConditionKind.MEDIA to ChoiceIcons("paused", TablerIcons.Outline.PlayerPause, TablerIcons.Outline.PlayerPlay),
        ConditionKind.HEADSET to
            ChoiceIcons("disconnected", TablerIcons.Outline.HeadphonesOff, TablerIcons.Outline.Headphones),
        ConditionKind.SCREEN to ChoiceIcons("locked", TablerIcons.Outline.Lock, TablerIcons.Outline.DeviceMobile),
        ConditionKind.CHARGING to
            ChoiceIcons("idle", TablerIcons.Outline.Battery, TablerIcons.Outline.BatteryCharging),
        ConditionKind.BLUETOOTH to ChoiceIcons("off", TablerIcons.Outline.BluetoothOff, TablerIcons.Outline.Bluetooth),
        ConditionKind.CELLULAR to ChoiceIcons("off", TablerIcons.Outline.CellSignalOff, TablerIcons.Outline.CellSignal4),
        ConditionKind.AIRPLANE to ChoiceIcons("off", TablerIcons.Outline.PlaneOff, TablerIcons.Outline.Plane),
    )

/** 表里那一对字形；这个条件不是两档型（没有取值可挑）⇒ `null`。 */
private fun choiceIconsOf(
    kind: ConditionKind,
    value: String,
): ImageVector? = CHOICE_ICONS[kind]?.let { if (value == it.firstValue) it.onFirst else it.onSecond }

/** 响铃三档：正常的响铃、振动、静音各一颗。 */
private fun ringerChoiceIcon(value: String): ImageVector =
    when (value) {
        Action.Ringer.NORMAL -> TablerIcons.Outline.BellRinging
        Action.Ringer.VIBRATE -> TablerIcons.Outline.DeviceMobileVibration
        Action.Ringer.SILENT -> TablerIcons.Outline.BellOff
        else -> TablerIcons.Outline.Bell
    }

/** 屏幕方向：竖屏给「一块竖着的屏」、横屏给旋转箭头。 */
private fun orientationChoiceIcon(value: String): ImageVector =
    when (value) {
        "portrait" -> TablerIcons.Outline.DeviceMobile
        "landscape" -> TablerIcons.Outline.Rotate
        else -> TablerIcons.Outline.DeviceMobile
    }
