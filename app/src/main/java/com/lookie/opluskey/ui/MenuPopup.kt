package com.lookie.opluskey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.ui.model.MENU_ROW_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.MENU_ROW_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.POPUP_SURFACE_DARK
import com.lookie.opluskey.ui.model.POPUP_SURFACE_LIGHT
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

// 浮层菜单那一族：分隔线、配色、菜单项模型、菜单本体与其中一行。

/**
 * 浮层菜单两项之间的细线 —— 顶栏 ⋮ 菜单、已保存命令的长按菜单、「添加命令」选择器都用它。
 *
 * 左端从**行的内容左边界**起（库的 `InsideHorizontalPadding`）—— 于是它会**穿过行首图标那一格**。
 * 不要缩进到标题文字的左边界（16 + 24 + 12 = 52dp）：那样线只在文字下面那一段，图标区会把线切断。
 * 右端仍让出行尾那一格（对勾 / 右箭头 / 提示），与左端同取库的内边距。
 *
 * 颜色自己给（见 [popupDividerColor]）：面板磨砂后半透明，库默认那一档暗色下几乎看不见。
 *
 * @param modifier 透给库的 `HorizontalDivider`，与这里自己的左右内边距叠加（默认无）。
 */
@Composable
internal fun PopupRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier =
            modifier.padding(
                start = DropdownDefaults.InsideHorizontalPadding,
                end = DropdownDefaults.InsideHorizontalPadding,
            ),
        color = popupDividerColor(darkAlpha = MENU_ROW_LINE_DARK_ALPHA, lightAlpha = MENU_ROW_LINE_LIGHT_ALPHA),
    )
}

/**
 * 浮层里分隔的颜色：暗色走**亮**线、浅色走黑线。
 *
 *  「暗色下看不清，加深分隔条」（真机反馈）：面板磨砂后是半透明的深灰（`#2e` 附近），
 *  库默认那一档线压在上面几乎看不见 —— 暗色下压暗只会更糊，得反过来提亮。
 */
@Composable
private fun popupDividerColor(
    darkAlpha: Float,
    lightAlpha: Float,
): Color =
    if (COUITheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = darkAlpha)
    } else {
        Color.Black.copy(alpha = lightAlpha)
    }

/**
 * 面板本色（[POPUP_SURFACE_LIGHT] / [POPUP_SURFACE_DARK]）—— 分档照抄库 `ListPopupContent`
 * 的 `popupSurfaceColor()`：按主题背景亮度切。
 *
 *  浮层菜单与确认弹窗**共用这一份**：两处都要「半透本色压在糊上」，色号分家迟早走形。
 */
@Composable
internal fun popupSurfaceColor(): Color =
    if (COUITheme.colorScheme.background.luminance() < 0.5f) {
        POPUP_SURFACE_DARK
    } else {
        POPUP_SURFACE_LIGHT
    }

/**
 * 浮层菜单的**一行**（[MenuPopup] 的入参模型）—— 四处菜单（顶栏 ⋮ / 命令长按 / 响铃档位 /
 * 「添加命令」一级与二级）共用这一张小表。
 *
 * 一行能表达现状的全部形态：
 *  - [enabled] `false`：置灰 + **吃掉点击**（菜单不关、也不派发）；
 *  - [selected]：单选组那一档（**行首图标换成对勾** + 文字/图标走 `primary`；行尾不再有对勾那一格，
 *    见 `showCheck` 的说明），进场时还会**滚进视窗**
 *    （「换一步」时当前那一步常在第 7 项之后，不滚就看不见自己选的是哪个）；
 *  - [danger]：删除 / 重置这类破坏性动作（红）；
 *  - [submenu]：行尾**留出那一格**（库里就是 `DropdownImpl(hasSubmenu = …)`），不置时那一格不画。
 *  - [trailingIcon]：换掉那一格里的字形（`null` = 用默认的右箭头）。
 *    **注意：两位是分开的 —— 要行尾图标的行必须自己置上 [submenu]**。本组件只把 [submenu] 原样
 *    交给库，不替调用点猜「给了图标就顺手把那一格打开」。库里那一位是**唯一的开关**，只给
 *    [trailingIcon] 的话一个像素都不画（真机踩到过：「反馈问题」菜单三行只传了图标，行尾全空）。
 *    置上之后字形怎么选：
 *     · 进下一层的行 → 不传 [trailingIcon]，用默认右箭头；
 *     · 跳**另一个页面**的行 → 换别处那一套（网格里「打开应用」那一格用的是「窗口 + 加号」，
 *       见 `PageIcons.BrowserPlus`）；
 *     · 弹一层菜单的行 → `PageIcons.Selector`；开外部链接的行 → `PageIcons.Link`。
 */
internal sealed interface MenuEntry {
    data class Row(
        val title: String,
        val icon: ImageVector,
        val enabled: Boolean = true,
        val selected: Boolean = false,
        val danger: Boolean = false,
        val submenu: Boolean = false,
        val trailingIcon: ImageVector? = null,
        val onPick: () -> Unit,
    ) : MenuEntry

    /** 组标题（库的 `DropdownHeaderItem`）。 */
    data class Group(
        val text: String,
    ) : MenuEntry

    /** 相邻两行之间的细线（[PopupRowDivider]）。 */
    data object RowLine : MenuEntry

    /** 组与组之间的**加深带**（颜色 / 厚度取库常量）。 */
    data object GroupLine : MenuEntry
}

/**
 * 菜单行标题的**格数上限** —— 六个汉字（[displayWidth] 折算的 12 格）。
 *
 *  浮层的宽度由最长那一行决定，而库里给标题的行数上限是 `DropdownDefaults.TitleMaxLines`（3 行）：
 *  不截的话，一条长命令名会先把那一行撑成两三行、再把整张菜单拉宽，同一份菜单在不同命令下宽度还不一样。
 *
 *  按**格**算而不是按 `length` 算：`English`（7 字符 / 7 格）本来就放得下，按字符数判会把它砍成
 *  `Engli…`；汉字两格，所以这一档对中文就是「六个字」。
 */
private const val MENU_TITLE_MAX_WIDTH = 12

/**
 * 菜单行标题的**显示文本**：超过 [MENU_TITLE_MAX_WIDTH] 就从尾部砍掉、补一个省略号。
 *
 * 只改**画出来的那一份** —— 功能不受影响，日志（[MenuRow] 里的点选那一行）仍写全名，
 * 否则截断后的名字在日志里认不出是哪一条命令。
 */
internal fun ellipsizeMenuTitle(title: String): String {
    if (displayWidth(title) <= MENU_TITLE_MAX_WIDTH) return title
    var width = 0
    // 按**码点**前进、只在码点边界上记可截位置：按 `Char` 数再 `take(chars)` 会把代理对
    //  （emoji）从中间切开，留下半个代理字符（画出来是乱码）。
    var cut = 0
    var i = 0
    while (i < title.length) {
        val cp = title.codePointAt(i)
        val step = Character.charCount(cp)
        val next = width + if (isFullWidth(title[i])) 2 else 1
        if (next > MENU_TITLE_MAX_WIDTH) break
        width = next
        i += step
        cut = i
    }
    return title.take(cut) + "…"
}

/**
 * 浮层行**内容区**的高度 —— 库的行高 `DropdownDefaults.PopupItemMinHeight`（48dp）
 * 减掉它自己上下各 `MiddleVerticalPadding`（8dp）的内边距。
 *
 * 只服务「进场定位到当前那一行」（见 [MenuRow]）：`bringIntoView` 只保证**挂 requester
 * 的那个节点**可见，所以那个节点必须**和整行内容区一样高** —— 挂在 20dp 的图标上只会让
 * 图标可见，行被上下各裁掉一截（真机反馈「滚动位置不够，选中的行没有完整显示」）。
 *
 *  行高是 `heightIn(min = …)`，文本多行时会长高 ⇒ 这个常量算的是**单行**那一档；
 * 浮层里的行都是单行标题（动作名 / 命令名），够用。
 */
internal val MENU_ROW_CONTENT_HEIGHT =
    DropdownDefaults.PopupItemMinHeight -
        DropdownDefaults.MiddleVerticalPadding * 2

/**
 * [MenuPopup] 的「上一次内容」持有 —— 见那边关于退场动画的注释。
 *
 * 刻意**不是** `mutableStateOf`：写它不该触发重组。调用方每次重组都会新建一份
 * entries（里面还有新 lambda），用 state 存就会变成「组合期写 state → 重组 → 再写」
 * 的每帧循环；这里只要「上一次那份」这个事实。
 */
internal class MenuHold(
    var entries: List<MenuEntry>,
    var click: PreciseClickState,
)
