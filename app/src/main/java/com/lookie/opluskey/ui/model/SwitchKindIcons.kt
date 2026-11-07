package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.model.SwitchKind
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.BatteryEco
import io.github.lyxnx.compose.ui.tablericons.outline.Bluetooth
import io.github.lyxnx.compose.ui.tablericons.outline.BrightnessAuto
import io.github.lyxnx.compose.ui.tablericons.outline.CellSignal4
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.lyxnx.compose.ui.tablericons.outline.Location
import io.github.lyxnx.compose.ui.tablericons.outline.Moon
import io.github.lyxnx.compose.ui.tablericons.outline.MoonStars
import io.github.lyxnx.compose.ui.tablericons.outline.Nfc
import io.github.lyxnx.compose.ui.tablericons.outline.Plane
import io.github.lyxnx.compose.ui.tablericons.outline.RotateRectangle
import io.github.lyxnx.compose.ui.tablericons.outline.SunLow
import io.github.lyxnx.compose.ui.tablericons.outline.Wifi

// 「系统开关」那一族的字形 —— 单独立一个文件，因为 `ActionIcons.kt` 已经顶到 detekt 的
// 文件级函数数上限，而这一族与「动作清单」那套是**两件事**：那边按 `Spec.glyph` 翻译，
// 这边按开关种类翻译。

/**
 * 开关**种类**那一层的字形 —— 对 [SwitchKind] 做 `when`，**没有 `else`**。
 *
 *  这是这一族唯一一张表。加一条开关（`SwitchKind` 加一项）忘了配字形时，这个 `when` 不完备
 *  ⇒ **当场编译失败**（`'when' expression must be exhaustive. Add the 'XXX' branch`），
 *  而不是像原先那样掉到 `Dots`（界面上看着像「这条本来就没配图标」）。
 *
 *  实测过：往 `SwitchKind` 里加一个不配字形的值，编译报的就是上面那句。
 *
 *  ## 为什么不再是 `Map<String, () -> ImageVector>`
 *
 *  原先是一张 `id → lambda` 的 map：lambda 是为了「读一颗建一颗」（`ImageVector` 是顶层 val，
 *  整表预建是冷启动后第一次进页的那笔掉帧）。这个取舍现在还在 —— `when` 的每个分支是
 *  **表达式**，只在这一格真被画到时才求值，冷启动开销与之前相同。
 *
 *  换来的是「漏了会报错」，而 map 缺键只能静默兜底。两者不可兼得，优先要前者：
 *  这张表一共十三项、一年也未必加一条，编译期检查比启动快几微秒值钱得多。
 */
@Suppress("CyclomaticComplexMethod")
private fun switchKindIcon(kind: SwitchKind): ImageVector =
    when (kind) {
        SwitchKind.BT -> TablerIcons.Outline.Bluetooth

        SwitchKind.WIFI -> TablerIcons.Outline.Wifi

        SwitchKind.CELL -> TablerIcons.Outline.CellSignal4

        SwitchKind.ROTATION -> TablerIcons.Outline.RotateRectangle

        SwitchKind.NFC -> TablerIcons.Outline.Nfc

        SwitchKind.AIRPLANE -> TablerIcons.Outline.Plane

        // 定位服务：与系统「位置信息」那颗准星同一套读法
        SwitchKind.LOCATION -> TablerIcons.Outline.Location

        // 免打扰（月亮：勿扰 / 夜间的通用读法）—— 老配置里 `oplus:no_disturb` 也落到这里
        SwitchKind.DND -> TablerIcons.Outline.Moon

        // 手电筒：与网格里那一格（`Action.FLASHLIGHT`）同一颗，同一个意思不该有两个长相
        SwitchKind.FLASHLIGHT -> DrawnIcons.Flashlight

        SwitchKind.DARKMODE -> TablerIcons.Outline.MoonStars

        SwitchKind.POWERSAVE -> TablerIcons.Outline.BatteryEco

        // 半个太阳（「再压一档亮度」），与 darkmode 的月亮分开：那条改配色，这条改屏幕暗到什么程度
        SwitchKind.EXTRADIM -> TablerIcons.Outline.SunLow

        // 自动亮度：太阳 + 一个 A（与「跟随系统」那行同一颗），与上下两档「亮度本身」分开
        SwitchKind.AUTOBRIGHTNESS -> TablerIcons.Outline.BrightnessAuto
    }

/**
 * 开关**种类**那一层的字形（字符串 id 那一版）—— 给「手改配置里认不出的 id」兜底。
 *
 *  认得出就走 [switchKindIcon]（那张 `when` 是穷尽的，漏不了）；认不出给 `Dots`：
 *  那时确实没有什么可画，而不是「我们忘了配」—— 这两件事在界面上长得一样，在这里分得开。
 */
internal fun systemSwitchKindIcon(id: String): ImageVector = SwitchKind.of(id)?.let(::switchKindIcon) ?: TablerIcons.Outline.Dots
