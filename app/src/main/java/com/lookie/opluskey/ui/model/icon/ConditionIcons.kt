package com.lookie.opluskey.ui.model.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.ConditionKind
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AppsOff
import io.github.lyxnx.compose.ui.tablericons.outline.Bell
import io.github.lyxnx.compose.ui.tablericons.outline.Bluetooth
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignal4
import io.github.lyxnx.compose.ui.tablericons.outline.Clock24
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceAirpods
import io.github.lyxnx.compose.ui.tablericons.outline.DeviceMobile
import io.github.lyxnx.compose.ui.tablericons.outline.MusicCog
import io.github.lyxnx.compose.ui.tablericons.outline.Plane
import io.github.lyxnx.compose.ui.tablericons.outline.Plug
import io.github.lyxnx.compose.ui.tablericons.outline.RotateRectangle
import io.github.lyxnx.compose.ui.tablericons.outline.Wifi

// 分叉条件的字形 —— 只剩「种类」这一层。
//
// 从 `ActionIcons.kt` 拆出来：那边是「动作」的字形表，条件是另一族（`ConditionKind`），
// 两族各自的 when 都在长；拆开之后「加一种条件」只碰这一个文件。

/**
 * 分叉**条件种类**的字形 —— 「选择条件」那张菜单里，一行一个条件。
 *
 *  每种条件共用一颗 `arrows-split` 的话，菜单就是一串长得一模一样的字，
 *  只剩标题能区分；每档各给一颗，一眼能扫到「我要的是哪一个」。
 *
 *  **只给「类别」这一层**：取值那几档（WiFi 开 / 关、亮屏 / 熄屏、充电 / 未充电…）不再各给
 *  一颗 —— 命令编辑页里每一行条件一律用父图标 `PageIcons.Scene`（见 `conditionLineIcon`），
 *  所以「一级菜单与两态撞形」这件事在这里已经没有第二个参与者。
 *
 *  **全项目唯一一处**（与 `systemSettingIcon` 同一条规矩）：加一种 [ConditionKind] 时，
 *  在这支 `when` 里加一行。
 *
 *  刻意**不留兜底**：漏一行会悄悄掉到某个默认字形、从代码上看不出来，而留空 `else` 又会让
 *  编译器不再检查完备性 —— 于是加一种条件时不补字形，界面上只是「少了颗图标」。
 *
 */
internal fun conditionIcon(kind: ConditionKind): ImageVector =
    when (kind) {
        ConditionKind.APP -> PageIcons.AppFront

        //  「前台应用以外」＝ 与上一档同一族的反面，用同一族带一道斜杠的那颗，
        //  「是 / 不是」不必再读一遍文字才分得出来。
        ConditionKind.APP_NOT -> TablerIcons.Outline.AppsOff

        // 屏幕状态：一台手机（这一档说的是「屏幕现在什么状态」，手机是它的载体）
        ConditionKind.SCREEN -> TablerIcons.Outline.DeviceMobile

        // 充电：一颗插头（说的是「插着电没有」这件事）
        ConditionKind.CHARGING -> TablerIcons.Outline.Plug

        // 耳机：一副耳机
        ConditionKind.HEADSET -> TablerIcons.Outline.DeviceAirpods

        // 媒体状态：音符 + 齿轮（「正在播什么」这件事的设定）
        ConditionKind.MEDIA -> TablerIcons.Outline.MusicCog

        ConditionKind.WIFI -> TablerIcons.Outline.Wifi

        // 响铃状态：与「命令」那一侧的响铃状态同一颗（`ActionIcons` 里 `ringer:normal` 也是它）
        ConditionKind.RINGER -> TablerIcons.Outline.Bell

        // 方向：旋转的矩形（竖 / 横由那一行的文字说）
        ConditionKind.ORIENTATION -> TablerIcons.Outline.RotateRectangle

        //  时间区间给「24 小时的钟」：它不是某一刻，而是一段「几点到几点」
        ConditionKind.TIME -> TablerIcons.Outline.Clock24

        ConditionKind.BLUETOOTH -> TablerIcons.Outline.Bluetooth

        //  移动数据用信号格（与「系统开关」里那格同一颗，那格也是 `cell`）
        ConditionKind.CELLULAR -> TablerIcons.Outline.CellSignal4

        // 飞行模式：一架飞机
        ConditionKind.AIRPLANE -> TablerIcons.Outline.Plane

        //  手电筒：与「系统开关」里那一格同一颗自绘灯（同一个意思不该有两个长相）
        ConditionKind.FLASHLIGHT -> DrawnIcons.Flashlight
    }
