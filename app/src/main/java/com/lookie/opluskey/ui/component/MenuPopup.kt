package com.lookie.opluskey.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.MENU_ICON_SIZE
import com.lookie.opluskey.ui.model.MENU_ROW_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.MENU_ROW_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.POPUP_FROSTED_ALPHA
import com.lookie.opluskey.ui.model.POPUP_PANEL_RADIUS
import com.lookie.opluskey.ui.model.POPUP_SURFACE_DARK
import com.lookie.opluskey.ui.model.POPUP_SURFACE_LIGHT
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.rememberTick
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownHeaderItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
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
 *  - [selected]：单选组那一档（行尾对勾 + 文字/图标走 `primary`），进场时还会**滚进视窗**
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
private val MENU_ROW_CONTENT_HEIGHT =
    DropdownDefaults.PopupItemMinHeight -
        DropdownDefaults.MiddleVerticalPadding * 2

/**
 * [MenuPopup] 的「上一次内容」持有 —— 见那边关于退场动画的注释。
 *
 * 刻意**不是** `mutableStateOf`：写它不该触发重组。调用方每次重组都会新建一份
 * entries（里面还有新 lambda），用 state 存就会变成「组合期写 state → 重组 → 再写」
 * 的每帧循环；这里只要「上一次那份」这个事实。
 */
private class MenuHold(
    var entries: List<MenuEntry>,
    var click: PreciseClickState,
)

/**
 * **项目里唯一的浮层菜单** —— 顶栏 ⋮、命令长按、响铃档位、「添加命令」一级与二级都走它。
 *
 * 定位一律是库的 `DropdownPositionProvider` + 触点锚点（[preciseClick]）：横向以触点为中、
 * 下方放不下翻到上方、不跟随滚动。
 *
 *  注意：**启用行必须是 `ListPopupColumn` 的直接子项** —— 给它套一层 `Box` 之后，点它只会
 * 把浮层关掉、`onSelectedIndexChange` 一次都不派发（真机踩到：点「打开默认界面」
 * 菜单关了，页面没切）。所以只有**禁用行**才套「吃事件的透明 Box」（见 [MenuRow]）。
 *
 *  注意：**滑动不许选中**：库在抬指那一刻派发行点击，它判「滚动还是选行」只看内部状态，
 *  长列表上会偶发把滑动判成选行（真机两笔，见 `docs/device-findings.md` 二十）。整列外面挂一层
 *  [GestureProbe] 记账，[MenuRow] 派发前按位移兜一道 —— 越过阈值就吞掉这次选中。
 *
 * @param preciseClick 触发键（或二级菜单的锚点行）上记下的按下点 —— 浮层弹在手指那一点
 * @param entries 菜单内容：行 / 组标题 / 细线 / 组间带，按显示顺序排
 * @param maxHeight 面板最高多少（「添加命令」那份长列表要限高，其余不限）
 * @param enableWindowDim 二级浮层要压暗整屏（连同留着的一级面板），一级默认不压
 * @param columnAnchor 非 null 时整列挂锚点 —— 「添加命令」的二级要从一级面板那一行取锚点
 * @param onLeave 页面离开时收哪一层（默认 = [onDismiss]；「添加命令」的一级还要顺带把二级
 *   一起收掉，所以它显式传了一个把两级都关掉的 lambda）
 */
@Composable
internal fun SettingsPage.MenuPopup(
    show: Boolean,
    preciseClick: PreciseClickState,
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    maxHeight: Dp? = null,
    enableWindowDim: Boolean = false,
    columnAnchor: PreciseClickState? = null,
    onLeave: (() -> Unit)? = null,
) {
    // 页面离开时跟着一起收（见 [SettingsPage.closeOverlays]）
    OverlayCloser(show = show) { (onLeave ?: onDismiss)() }
    // 整列的手势账本 —— 抓「滑动被当成点一下」那种偶发现场（见 [GestureProbe]）。
    val probe = rememberGestureProbe("浮层列表")
    //  每开一次把账本归零：对象跨开合不重建，留着上一次的
    //  `contentY` 基线只会把日志里那行「列表内容移位」算错 —— 判定（`isDrag`）不受影响，
    //  但那行日志是排查时的判据。
    LaunchedEffect(show) { if (show) probe.reset() }
    // —— 退场动画期间，**内容与锚点都得保持住**——
    //  调用方一关就把内容清空（网格入口格那份 `modes` 直接跟着 `tileMenu` 变成空表、
    // 「添加命令」二级跟着 `subTile` 变空）、锚点也换回别的格子：库那条淡出淡的就是
    // 「一个已经空掉、还换了位置的面板」—— 面板高度塌成 0，观感上等于**没有关闭动画**
    // （真机反馈「网格的浮动菜单没有关闭动画」）。
    //  库的 `show = false` 只负责**开始**淡出、跑完才卸载，所以这段时间里得继续供上一次的内容。
    //  写的是普通对象（见 [MenuHold]）：不触发重组，不会和调用方每帧新建的那份 list 打架。
    val held = remember { MenuHold(entries, preciseClick) }
    if (show) {
        held.entries = entries
        held.click = preciseClick
    }
    OverlayListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        preciseClickState = held.click,
        maxHeight = maxHeight,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismiss,
    ) {
        // 退场期间用的就是 held 里那一次的内容（见上）。
        val shown = held.entries
        val column: @Composable () -> Unit = {
            ListPopupColumn {
                val optionSize = shown.count { it is MenuEntry.Row }
                var index = -1
                shown.forEachIndexed { position, entry ->
                    when (entry) {
                        is MenuEntry.Row -> {
                            index++
                            MenuRow(
                                row = entry,
                                index = index,
                                optionSize = optionSize,
                                // 「首行 / 末行」看的是**整列**的首尾元素：组标题占首行时，
                                // 标题下面那一行**不算**首行 —— 否则会多出一次首行内衬，
                                // 与组标题之间凭空多 2dp（查「小标题那笔账」时发现）。
                                isFirst = position == 0,
                                isLast = position == shown.lastIndex,
                                // 淡出期间（`show = false`，内容还按 held 那一次留着）**不再派发**
                                // —— 见 [MenuRow] 的 `interactive`。
                                interactive = show,
                                probe = probe,
                            )
                        }

                        is MenuEntry.Group -> {
                            DropdownHeaderItem(entry.text, isFirst = position == 0)
                        }

                        MenuEntry.RowLine -> {
                            PopupRowDivider()
                        }

                        MenuEntry.GroupLine -> {
                            HorizontalDivider(
                                thickness = DropdownDefaults.PopupGroupDividerThickness,
                                // 组间那条**槽**：暗色下加深（`#333` 面板上压到 `#1f` 才看得出是一道槽），
                                // 浅色下仍是那点灰。见 [popupDividerColor]。
                                color =
                                    if (COUITheme.colorScheme.background.luminance() < 0.5f) {
                                        Color.Black.copy(alpha = MENU_GROUP_LINE_DARK_ALPHA)
                                    } else {
                                        Color.Black.copy(alpha = MENU_GROUP_LINE_LIGHT_ALPHA)
                                    },
                            )
                        }
                    }
                }
            }
        }
        // `ListPopupColumn` 没有 `modifier` 参数 ⇒ 整列锚点 / 手势账本 / 磨砂只能套在它外面。
        //
        // 磨砂：库的面板自己是不透明的（`ListPopupContent` 用 `couiColorSurfaceTop`，
        //  浅色 `#FFFFFF` / 深色 `#333333`）、圆角 12dp 且**不裁子级**，所以不能借它的底 ——
        //  得在面板里侧再糊一层：从**页面内容层**取样、自己按 [POPUP_PANEL_SHAPE] 收边。
        //  面板那一层不透明底会被这层糊掉（取样是全幅的），底色由 [POPUP_FROSTED_ALPHA] 那层半透明
        //  顶上来 —— 观感与顶栏同一套（[frosted]），本色比顶栏那一档亮、alpha 与它同一量级。
        //  注意：取样的是页面那一层，本节点画在 Scaffold 的 `COUIPopupHost` 里（页面那层之外）——
        //  落进自己取样的那一层就是渲染节点链无限增长、RenderThread 栈溢出（真机崩过一次）。
        val panelShape = remember { RoundedCornerShape(POPUP_PANEL_RADIUS) }
        val source = pageHazeState
        if (source == null && !frostDegradeLogged) {
            frostDegradeLogged = true
            XLog.w("浮层磨砂跳过：页面取样层未就绪 ⇒ 这一轮仍用不透明底")
        }
        val frost: Modifier =
            source?.let { bd ->
                Modifier
                    .frosted(bd, panelShape)
                    .background(
                        // 底色取**库那一对面板本色**（`POPUP_SURFACE_*`），不能取主题里那几档 `surface*`：
                        //  暗色下页面是纯黑、`surfaceContainerHighest` 只有 `#252525`，用它当底色会比库原生
                        //  面板（`#333333`）还暗（真机反馈「暗色下底色太暗」）。
                        popupSurfaceColor().copy(alpha = POPUP_FROSTED_ALPHA),
                        panelShape,
                    )
            } ?: Modifier
        if (columnAnchor == null) {
            Box(Modifier.gestureProbe(probe).then(frost)) { column() }
        } else {
            Box(Modifier.preciseClickAnchor(columnAnchor).gestureProbe(probe).then(frost)) { column() }
        }
    }
}

/**
 * 菜单里的一行 —— 行首图标 + 文字，整行可点。**全项目就这一份实现**。
 *
 * 图标 tint 必须自己补：库只管文字那一档（`alert` / `selected` / 禁用色都是库上的），
 * 不补就会出现「对勾是蓝的、图标还是灰的」「文字红了、图标没红」。
 *
 * 动画值放 `remember` **外面**（icon lambda 执行时才读值，键里不加它，免得动画每帧重建
 * item）；但**键里必须有 `selected` / `danger`** —— 颜色由它们决定，只按标题记的话换一档
 * 之后取回来的还是上一次那个 `DropdownItem`（颜色不跟着走）。
 *
 * @param interactive 浮层现在是不是**真的开着**（= `MenuPopup` 的 `show`）。`false` 时这一行
 *   **不再派发点击** —— 库的 `show = false` 只负责开始淡出、跑完才卸载，这段时间里内容仍在
 *   （[MenuPopup] 用 `held` 那一次的内容供着它），行照样可点 ⇒ 淡出期再点一下会**二次派发**：
 *   「编辑」把草稿再灌一遍、「删除」把确认窗再弹一次。
 */
@Composable
private fun SettingsPage.MenuRow(
    row: MenuEntry.Row,
    index: Int,
    optionSize: Int,
    isFirst: Boolean,
    isLast: Boolean,
    /** 浮层是不是真开着（见上面的 `@param`）。 */
    interactive: Boolean,
    /** 整列的手势账本（见 [GestureProbe]）：点击日志要带上「这一段滑了多远」。 */
    probe: GestureProbe?,
) {
    val tick = rememberTick()
    val iconColor by animateColorAsState(
        targetValue =
            when {
                !row.enabled -> COUITheme.colorScheme.disabledOnSecondaryVariant
                row.danger -> COUITheme.colorScheme.error
                row.selected -> COUITheme.colorScheme.primary
                else -> COUITheme.colorScheme.onSurfaceContainer
            },
        label = "MenuRowIcon",
    )
    // 进场定位到当前那一行：requester 挂在**整行内容区一样高**的盒子上，请求的是**上下各
    // 扩一档内边距**的矩形 —— 无参的 `bringIntoView()` 只保证「这个节点」可见，行的上下
    // 内边距会被裁掉（真机反馈「当前行没有整行（包括上下内边距）显示出来」）。
    // 行内边距有两档（中间行 8dp、首尾行 10dp），取大的那档一次覆盖。
    val bringIntoView = remember { BringIntoViewRequester() }
    if (row.selected) {
        val rowRect =
            with(LocalDensity.current) {
                val pad = DropdownDefaults.FirstLastVerticalPadding.toPx()
                Rect(
                    left = 0f,
                    top = -pad,
                    right = MENU_ICON_SIZE.toPx(),
                    bottom = MENU_ROW_CONTENT_HEIGHT.toPx() + pad,
                )
            }
        // 等一帧再请求：组合刚结束时节点还没 attach / 布局，此时 `bringIntoView()` 是空操作。
        LaunchedEffect(bringIntoView) {
            withFrameNanos { }
            runCatching { bringIntoView.bringIntoView(rowRect) }
        }
    }
    val item =
        remember(
            row.title,
            row.icon,
            row.enabled,
            row.danger,
            row.selected,
            row.trailingIcon,
        ) {
            DropdownItem(
                // 显示文本过一道截断（[ellipsizeMenuTitle]）：库里标题最多三行，长名字会撑宽整张菜单
                text = ellipsizeMenuTitle(row.title),
                enabled = row.enabled,
                // 文字由库按 `alert` 走 `DropdownColors.alertContentColor` —— 与图标的红同一档
                alert = row.danger,
                // 行尾那颗「选中」的对勾走我们自己的 Tabler 字形：库默认那颗是 `COUIIcons.Basic.Check`
                // （描边 1.4、又宽又扁），与行首的 Tabler 图标（描边 2）不是一支笔，见 [PageIcons.MenuCheck]。
                // 恒传：没选中的行库本来就把这颗画成透明，只有选中行看得见。
                checkIcon = PageIcons.MenuCheck,
                // 进下一层那些行（「响铃状态」「选择应用」）的行尾右箭头同样换掉 —— 库默认的
                // `COUIIcons.Basic.ArrowRight` 也是 COUI 那一套（12x24、描边 1.4），见 [PageIcons.EnterArrow]。
                //  逐行可换（[MenuEntry.Row.trailingIcon]）：跳页面那些行用「窗口 + 加号」，与网格一致。
                chevronIcon = row.trailingIcon ?: PageIcons.EnterArrow,
                icon = { m ->
                    val base = m.height(MENU_ROW_CONTENT_HEIGHT).bringIntoViewRequester(bringIntoView)
                    // 第 0 行兼当「列表滚了没有」的探针：挂在行内容上 —— 滚的是列表**内层**，
                    // 外层那一圈与浮层窗口自己都不动（见 [GestureProbe.onContentPosition]）。
                    val tracked =
                        if (index == 0 && probe != null) {
                            base.onGloballyPositioned { c -> probe.onContentPosition(c.positionInWindow().y) }
                        } else {
                            base
                        }
                    Box(tracked, contentAlignment = Alignment.Center) {
                        Icon(row.icon, null, Modifier.size(MENU_ICON_SIZE), tint = iconColor)
                    }
                },
            )
        }
    //  行底交给面板那一层：把行自己的 `containerColor` 抹成透明，浮层那层磨砂才透得出来
    // （未选中行本来就是这个色，面板自己也铺同一档 —— 抹掉不改观感，改的只是「谁能盖住谁」）。
    //  库那个 `dropdownColors()` 自己是 `remember` 的，等它一次再换掉那一格。
    //  注意：**禁用行也要同一份** —— 只给启用行传 `dropdownColors` 的话，
    //  禁用行落到库默认的**不透明** `containerColor` —— 磨砂半透明面板上会现出一块实色底。
    val baseColors = DropdownDefaults.dropdownColors()
    val frostedColors =
        remember(baseColors) {
            baseColors.copy(containerColor = Color.Transparent)
        }
    if (row.enabled) {
        //  **直放**：中间套一层 `Box` 会让库收不到点击（见 [MenuPopup] 的警告）。
        DropdownImpl(
            item = item,
            optionSize = optionSize,
            isSelected = row.selected,
            index = index,
            isFirst = isFirst,
            isLast = isLast,
            hasSubmenu = row.submenu,
            dropdownColors = frostedColors,
            onSelectedIndexChange = {
                // 「滑动不许选中」：库在**抬指那一刻**派发这一行，而它判「滚动还是选行」只看内部
                //  `isDynamicSelection`（= 量不出滚动范围才允许拖拽选行）——长列表上会偶发判错，
                //  真机两笔：位移 261px / 273px、列表跟着滚了 261px / 273px，
                //  抬指却选中了手指下那一行（见 `docs/device-findings.md` 二十）。
                //  这里按**这一段手势自己的位移**兜一道：越过阈值就是滑动，吞掉这次选中。
                //  真点击是 0~几 px（同批日志），不会误吞。
                val dragged = probe?.isDrag() == true
                XLog.w(
                    "浮层点选：${row.title} —— ${probe?.describe() ?: "没有位移账本"}" +
                        if (!interactive) {
                            "；浮层正在淡出 ⇒ 已吞掉这次选中"
                        } else if (dragged) {
                            "；这一段是滑动 ⇒ 已吞掉这次选中"
                        } else {
                            ""
                        },
                )
                if (interactive && !dragged) {
                    tick()
                    row.onPick()
                }
            },
        )
    } else {
        //  禁用的这一行库里**根本不挂 `clickable`**，这一下没人 consume、直接穿到浮层自己的
        // 「点外面就关」那套逻辑上 —— 菜单照样会关。压一层**透明的吃事件层**把它挡住
        // （不给任何视觉，「灰」由 `DropdownItem(enabled = false)` 自己画），
        // 效果就是「点了没反应、菜单还开着」。
        //  `propagateMinConstraints = true`：这一层只是**吃事件**用的，不该改变行的尺寸 ——
        //  `Box` 默认把进来的 min 约束**放松成 0** 再交给子级，而行的宽度正是靠那一条 min 约束撑满面板的
        //  （库的 `ListPopupColumn` 按 `minWidth = listWidth` 量每一行）⇒ 少传这一位，同一个
        //  `MenuEntry.Row` 启用时行尾图标贴边、一禁用就缩到文字后面。
        Box(propagateMinConstraints = true) {
            DropdownImpl(
                item = item,
                optionSize = optionSize,
                isSelected = row.selected,
                index = index,
                isFirst = isFirst,
                isLast = isLast,
                //  与启用行**同值**：库里这一位决定「行尾那一格画不画」，不传就落到默认的 `false`
                //  —— 于是同一个 `MenuEntry.Row`，启用时行尾有图标、一禁用整格消失。
                hasSubmenu = row.submenu,
                //  禁用行也用磨砂那一档：不传的话落到库默认不透明底，
                //  半透明面板上会现出一块实色。
                dropdownColors = frostedColors,
                // 走不到（这一行是禁用的），留着只为满足签名
                onSelectedIndexChange = {},
            )
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                //  注意：**只吞「抬起」** —— 把每一帧的
                                //  `changes.forEach { it.consume() }` 全吞 —— **MOVE 也吞了**，
                                //  祖先 `ListPopupColumn` 的滚动收不到未消费事件 ⇒ 禁用行成了一块
                                //  滚不动的死区。点击（行自己 / 浮层「点外面就关」）的判定点都是
                                //  抬起那一下，只吞它就够了：按下照常落、滚动照常走。
                                //  用公开的 `changedToUp()`（`…IgnoreConsumed` 那支在库内部是 private，
                                //  引不到）：Initial pass 上还没有谁消费过，两者等价。
                                awaitPointerEvent().changes.forEach { change ->
                                    if (change.changedToUp()) change.consume()
                                }
                            }
                        }
                    },
            )
        }
    }
}
