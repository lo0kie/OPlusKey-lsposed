package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ConditionKind
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowsHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowsVertical
import io.github.lyxnx.compose.ui.tablericons.outline.Battery
import io.github.lyxnx.compose.ui.tablericons.outline.BatteryCharging
import io.github.lyxnx.compose.ui.tablericons.outline.Bell
import io.github.lyxnx.compose.ui.tablericons.outline.BellOff
import io.github.lyxnx.compose.ui.tablericons.outline.BellRinging
import io.github.lyxnx.compose.ui.tablericons.outline.Bluetooth
import io.github.lyxnx.compose.ui.tablericons.outline.BluetoothConnected
import io.github.lyxnx.compose.ui.tablericons.outline.BluetoothOff
import io.github.lyxnx.compose.ui.tablericons.outline.Brightness
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignal4
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignal5
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignalOff
import io.github.lyxnx.compose.ui.tablericons.outline.Clock
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobile
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobileVibration
import io.github.lyxnx.compose.ui.tablericons.outline.Headphones
import io.github.lyxnx.compose.ui.tablericons.outline.HeadphonesOff
import io.github.lyxnx.compose.ui.tablericons.outline.Headset
import io.github.lyxnx.compose.ui.tablericons.outline.Lock
import io.github.lyxnx.compose.ui.tablericons.outline.Music
import io.github.lyxnx.compose.ui.tablericons.outline.Plane
import io.github.lyxnx.compose.ui.tablericons.outline.PlaneInflight
import io.github.lyxnx.compose.ui.tablericons.outline.PlaneOff
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPause
import io.github.lyxnx.compose.ui.tablericons.outline.PlayerPlay
import io.github.lyxnx.compose.ui.tablericons.outline.Plug
import io.github.lyxnx.compose.ui.tablericons.outline.Rotate
import io.github.lyxnx.compose.ui.tablericons.outline.Router
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
 *  ## 这一族给的是「**类别**」记号，不是状态记号
 *
 *  一级菜单回答的是「选哪一类」，所以这里一律给类别：插头（充电）、路由器（WiFi）、
 *  耳机（耳机）、屏的亮度（屏幕）……**取值那几档的字形在 `conditionChoiceIcon` 里**，
 *  两族之间不许撞 —— 撞了就是「一级菜单和两态一模一样」（真机口径「一级菜单与两态有重复」）。
 *  所以两态那几档的「开」不许直接用类别那颗（`Wifi` / `Bluetooth` / `CellSignal4` / `Plane`
 *  都归取值那边），这里的类别各退一档到 `Router` / `Bluetooth` …`PlaneInflight` 这类写法。
 *
 *  **全项目唯一一处**（与 `systemSettingIcon` 同一条规矩）：加一种 [ConditionKind] 时，
 *  在这支 `when` 里加一行 —— 漏了会掉到 [TablerIcons.Outline.Dots]。
 */
internal fun conditionIcon(kind: ConditionKind): ImageVector =
    when (kind) {
        ConditionKind.APP -> PageIcons.AppFront

        // 屏幕状态：一颗「亮度」当类别，亮屏 / 锁屏那两颗在取值那边
        ConditionKind.SCREEN -> TablerIcons.Outline.Brightness

        // 充电：一颗插头（类别）；电池 / 充电中那是**状态**
        ConditionKind.CHARGING -> TablerIcons.Outline.Plug

        // 耳机：带麦的 `headset` 当类别，`headphones` / `-off` 留给取值
        ConditionKind.HEADSET -> TablerIcons.Outline.Headset

        ConditionKind.MEDIA -> TablerIcons.Outline.Music

        // WiFi：路由器当类别（`wifi` / `wifi-off` 是那两档取值）
        ConditionKind.WIFI -> TablerIcons.Outline.Router

        ConditionKind.RINGER -> TablerIcons.Outline.Bell

        // 方向：旋转箭头当类别（`arrows-vertical` / `-horizontal` 是竖 / 横两档）
        ConditionKind.ORIENTATION -> TablerIcons.Outline.Rotate

        //  时间区间给「一个钟」：它不是某一刻，但同一族里只有它是「几点」这一维
        ConditionKind.TIME -> TablerIcons.Outline.Clock

        ConditionKind.BLUETOOTH -> TablerIcons.Outline.Bluetooth

        //  移动数据用信号格（与「系统开关」里那格同一颗，那格也是 `cell`）；
        //  取值的「开」用 `cell-signal-5`，免得与这里撞
        ConditionKind.CELLULAR -> TablerIcons.Outline.CellSignal4

        // 飞行模式：带航线的飞机当类别（`plane` / `plane-off` 是那两档取值）
        ConditionKind.AIRPLANE -> TablerIcons.Outline.PlaneInflight
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
 *
 *  **每一对都不许与 [conditionIcon] 里那一档的类别字形相同**（「一级与两态重复」那次的教训）：
 *  `wifi` / `bluetooth` / `cell-signal-5` / `plane` / `battery-charging` / `headphones` /
 *  `device-mobile` 这些「正是那个状态」的字形一律归这一边，类别那边各退一档。
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
        //  蓝牙「开」用带连接记号的 `bluetooth-connected`：类别那一档已经占着 `bluetooth`
        ConditionKind.BLUETOOTH to
            ChoiceIcons("off", TablerIcons.Outline.BluetoothOff, TablerIcons.Outline.BluetoothConnected),
        //  移动数据「开」用满格 `cell-signal-5`：类别那一档占着 `cell-signal-4`
        ConditionKind.CELLULAR to
            ChoiceIcons("off", TablerIcons.Outline.CellSignalOff, TablerIcons.Outline.CellSignal5),
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

/**
 * 屏幕方向那两档：竖 = 竖箭头、横 = 横箭头。
 *
 *  原先给的是 `device-mobile` / `rotate`：前者与「屏幕状态 = 亮屏」那颗撞，后者与类别那颗撞
 *  （「一级与两态重复」）。方向本身说的是「竖 / 横」，一对箭头既准确又不占别人的字形。
 */
private fun orientationChoiceIcon(value: String): ImageVector =
    when (value) {
        "landscape" -> TablerIcons.Outline.ArrowsHorizontal
        else -> TablerIcons.Outline.ArrowsVertical
    }
