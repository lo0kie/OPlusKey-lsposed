package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.MENU_ICON_SIZE
import com.lookie.opluskey.ui.model.POPUP_FROSTED_ALPHA
import com.lookie.opluskey.ui.model.POPUP_PANEL_RADIUS
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.STEP_PICKER_MAX_HEIGHT
import com.lookie.opluskey.ui.model.isDarkScheme
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 展开之后的一项 —— 渲染层只认这五种（调用方永远看不到它们）。 */
private sealed interface MenuPart {
    data class Title(
        val text: String,
    ) : MenuPart

    data class Item(
        val row: MenuEntry.Row,
    ) : MenuPart

    data object Line : MenuPart

    /**
     * 小标题下面那条线 —— 与 [Line] 同一档颜色 / 粗细，但**通栏**（连行首图标那一格一起画）。
     *
     *  标题本身顶在面板左缘，线若也缩进到「行标题」那条竖线，读起来是「标题下面只划了半截」。
     */
    data object TitleLine : MenuPart

    data object HeavyLine : MenuPart
}

/**
 * 把调用方那份「只有内容」的列表展开成「内容 + 分隔」。
 *
 *  **分隔全在这里生成**，调用方一个都不写：
 *   · 小标题与第一行之间一条细线；
 *   · 组内行与行之间一条细线（某一行的 `dividerBefore` 换成加深带）；
 *   · 相邻两组之间（含组与上面那些裸行之间）一条加深带；
 *   · 顶层裸行之间也是一条细线。
 */
private fun expandMenu(entries: List<MenuEntry>): List<MenuPart> =
    buildList {
        entries.forEach { entry ->
            when (entry) {
                is MenuEntry.Row -> {
                    if (lastOrNull() is MenuPart.Item) {
                        add(if (entry.dividerBefore) MenuPart.HeavyLine else MenuPart.Line)
                    }
                    add(MenuPart.Item(entry))
                }

                is MenuEntry.Section -> {
                    //  **空组整段不出**：只剩标题加一条通栏细线、下面什么都没有，读起来像「渲染坏了」。
                    //  调用方按条件现算行表时（这一档暂时没有可选项）很容易给到空表。
                    if (entry.rows.isEmpty()) return@forEach
                    if (isNotEmpty()) add(MenuPart.HeavyLine)
                    add(MenuPart.Title(entry.title))
                    // 小标题下**默认**一条细线：标题与第一行贴在一起时读成「这一行是标题的一部分」。
                    //  这条线是**通栏**的（`TitleLine`）：标题顶在面板左缘，线只划到行标题那条竖线
                    //  就止住，看着像半截。
                    add(MenuPart.TitleLine)
                    entry.rows.forEachIndexed { index, row ->
                        if (index > 0) add(if (row.dividerBefore) MenuPart.HeavyLine else MenuPart.Line)
                        add(MenuPart.Item(row))
                    }
                }
            }
        }
    }

/**
 * **单层菜单的唯一入口** —— 调用方只给「行」和「手指锚点」，别的一律不写。
 *
 *  `show` 由「行表空不空」表达：关掉那一刻给空表即可（退场动画期间的锚点与内容由
 *  [MenuPopup] 内部保住）。弹层位置、分隔、最大高度、磨砂这些都由这里与 [MenuPopup] 定，
 *  调用方一个都碰不到 —— 以前十几处各写一遍 `show / maxHeight / enableWindowDim / columnAnchor`，
 *  漏一处就是「这张菜单没有关闭动画」或者「这一层长到屏幕外还滚不动」。
 *
 *  入口格那类「点一行还要再问一句」的不要用它，用 [ModeMenuPopups]：一层菜单加一层弹窗的
 *  编排（连同「弹窗开着时别被菜单的 dismiss 带走」那条守卫）只有那一份实现。
 *
 * @param entries 行；空表 = 关闭
 * @param preciseClick 开菜单那个控件自己的触点锚点
 * @param columnAnchor 只给「还要再弹一层」的调用方：把这一层里的按下点记下来当下一层的锚点
 * @param dim 叠在另一层上面时压暗背景（单层菜单不用管）
 */
@Composable
internal fun SettingsPage.PageMenu(
    entries: List<MenuEntry>,
    preciseClick: PreciseClickState,
    onDismiss: () -> Unit,
    columnAnchor: PreciseClickState? = null,
    dim: Boolean = false,
) {
    MenuPopup(
        show = entries.isNotEmpty(),
        preciseClick = preciseClick,
        entries = entries,
        onDismiss = onDismiss,
        options =
            MenuPopupOptions(
                // 长菜单一律封顶：不给的话面板按内容自然高度长，条数一多就顶出屏幕、而且滚不动
                //  （「添加命令」那张、系统开关那十个开关都踩过）。给了之后超出部分由库的列表自己滚。
                maxHeight = STEP_PICKER_MAX_HEIGHT,
                dim = dim,
                columnAnchor = columnAnchor,
            ),
    )
}

/**
 * 一个浮层菜单的「开 / 关 + 锚点」—— 这两样是同一件事的两面（「这块内容此刻弹没弹出来、
 * 从哪个触点弹的」），三处调用点原先各写一遍 `remember { mutableStateOf(false) }` +
 * `rememberPreciseClickState()`，再在 `PageMenu` 那处拼空表与 `onDismiss`。
 *
 *  取值一律走 [open] / [show] / [dismiss]：外部**没有**直接写这一位的口子 —— 写 false 与写 true
 *  的时机必须与锚点、与调用方那份行表在同一帧上对齐，散在各处写迟早对不齐。
 */
internal class MenuState(
    /** 开菜单那个控件自己的触点锚点（弹层定位用）。 */
    val anchor: PreciseClickState,
) {
    var open by mutableStateOf(false)
        private set

    fun show() {
        open = true
    }

    fun dismiss() {
        open = false
    }
}

/** 记住一份 [MenuState]（锚点跟着组合一起建）。 */
@Composable
internal fun rememberMenuState(): MenuState {
    val anchor = rememberPreciseClickState()
    return remember(anchor) { MenuState(anchor) }
}

/**
 * 「有状态的菜单」—— [MenuState] 那一版：[entries] 只在开着时求值，空表自动当作关闭。
 *
 *  比裸 [PageMenu] 少掉三件每次都要写对的事：`if (menu) … else emptyList()`、把锚点递进去、
 *  以及 `onDismiss` 复位。行表在关着时**根本不求值**（`entries` 是 lambda），所以里面可以放心
 *  用「当前选中值」现算，不必在开合之间维持一份快照。
 */
@Composable
internal fun SettingsPage.PageMenu(
    state: MenuState,
    entries: () -> List<MenuEntry>,
) {
    val rows = if (state.open) entries() else emptyList()
    //  **行表空就把 `open` 一起复位**：弹层靠「行表空」判关闭，只靠它的话 `open` 会留在 true，
    //  标题箭头显示「展开中」，再点一次写同值不触发重组 —— 这张菜单再也打不开。
    LaunchedEffect(rows.isEmpty()) {
        if (rows.isEmpty() && state.open) state.dismiss()
    }
    PageMenu(
        entries = rows,
        preciseClick = state.anchor,
        onDismiss = state::dismiss,
    )
}

/**
 * [MenuPopup] 那三样「面板怎么摆」—— 合成一个对象，参数才压得回 detekt 的 5 个以内，
 * 也让调用点一眼看出「这几样只影响面板本身」。
 *
 * @param maxHeight 面板最高多少（`null` = 按内容长）
 * @param dim 叠在另一层上面时压暗背景
 * @param columnAnchor 只给「还要再弹一层」的调用方：把这一层里的按下点记下来当下一层的锚点
 */
internal class MenuPopupOptions(
    val maxHeight: Dp? = null,
    val dim: Boolean = false,
    val columnAnchor: PreciseClickState? = null,
)

@Composable
internal fun SettingsPage.MenuPopup(
    show: Boolean,
    preciseClick: PreciseClickState,
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    options: MenuPopupOptions = MenuPopupOptions(),
) {
    // 页面离开时跟着一起收（见 [SettingsPage.closeOverlays]）
    OverlayCloser(show = show) { onDismiss() }
    //  **这一退交给菜单自己接**（与底部抽屉同一档，见 `PageSheet` 的那一行）：
    //  `OverlayListPopup` 内部挂着 `NavigationBackHandler`，只有把系统的预测返回进度喂进
    //  dispatcher，菜单才是「跟着手势收掉」；不登记的话 `wouldConsumeBack` 会把这一退判成
    //  「浮层自己不接」⇒ hook 侧直接把整段手势吞掉、只留一次性收起（用户口径：菜单没有预测手势）。
    //  全项目的菜单都从这一处出（`PageMenu` / `ModeMenuPopups` / 各列表行菜单），所以登记一次就够。
    overlays.BackCapturing(show = show)
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
        maxHeight = options.maxHeight,
        enableWindowDim = options.dim,
        onDismissRequest = onDismiss,
    ) {
        // 退场期间用的就是 held 里那一次的内容（见上）。
        val shown = held.entries
        //  整列的滚动状态 —— 一处建、两处用：库的 `ListPopupColumn` 用它滚，右边那条滚动条
        //  用它画。库自己 `remember` 一份的话，外面就再也拿不到「这一列现在能不能滚」。
        val menuScroll = rememberScrollState()
        val column: @Composable () -> Unit = {
            //  **库自己那次振动改走通用通道**：按住不放、上下滑动换行时，库每换一行振一次
            //  （`willChangeRow` → `CouiHaptic` → `LocalView.performHapticFeedback`），
            //  那条路不看系统静音、也不与其它点击同一档 —— 用户口径「按住滑动时的振动没走通用振动」。
            //  这一层把 `LocalView` 换掉之后，那一下落到 `LocalTick` 上（见 [TickHapticView]）。
            TickHapticScope {
                ListPopupColumn(scrollState = menuScroll) {
                    MenuEntries(shown, interactive = show, probe = probe)
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
        //  抽屉开着时取样层里还有**抽屉自己**那一份（`PageSheet` 把它也登记进同一个 state），
        //  所以这里照旧只认 `pageHazeState`：菜单糊出来的是「页面 + 抽屉」叠在一起的那一层。
        val source = chrome.pageHazeState
        if (source == null && !chrome.frostDegradeLogged) {
            chrome.frostDegradeLogged = true
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
        val anchor = options.columnAnchor
        val boxModifier = if (anchor == null) Modifier.gestureProbe(probe) else Modifier.preciseClickAnchor(anchor).gestureProbe(probe)
        Box(boxModifier.then(frost)) {
            column()
            //  滚动条那一层自成一支（见 [MenuScrollBarLayer]）：它压在行上面，占了多宽就吃掉多宽的行点击
            if (menuScroll.maxValue > 0) MenuScrollBarLayer(menuScroll, Modifier.matchParentSize())
        }
    }
}

/**
 * 浮层右侧那条滚动条所在的**一层** —— 与面板同框，里层只占右边一条窄带。
 *
 *  外层 `matchParentSize` 只吃父级量出来的尺寸（不参与测量，换成 `fillMaxHeight` 会把面板顶到
 *  `maxHeight`，菜单会变成永远满高）；里层那条窄带见 [MENU_SCROLLBAR_WIDTH]。
 *
 * @param modifier 由调用点给（`matchParentSize` 只能在 `BoxScope` 里取到）
 */
@Composable
private fun SettingsPage.MenuScrollBarLayer(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        MenuScrollBar(scrollState, Modifier.fillMaxHeight().width(MENU_SCROLLBAR_WIDTH))
    }
}

/**
 * 浮层右侧那条滚动条 —— 只在**这一列真的能滚**时出现（调用点按 `maxValue` 判），且**始终显示**：
 * 浮层那种面板里，滚动条不是「你滑动时给你的反馈」，而是「这下面还有内容」的唯一提示 ——
 * 按 COUI 默认那种「不碰它就淡出」等于没有提示（用户口径）。
 *
 *  与页面里那条（`PickerScrollBar`）同一套用法，差别只有两处：
 *   · 这里只占面板右侧一条窄带、与面板同高（由调用点给，见上）；
 *   · 按住滑块那一下同样走通用振动通道（[rememberTick]）—— 判据仍是「DOWN 有没有被库里 consume」，
 *     按在轨道空白处不振。
 *
 * @param scrollState 与 `ListPopupColumn` 共用的那一份（见调用点）
 * @param modifier 几何由调用点定（宽窄与高度见调用点那段注释）
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun SettingsPage.MenuScrollBar(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(scrollState),
        //  上下各让出一档：轨道与拇指都收在面板圆角之内，滑到顶 / 底时不会顶到那圈弧
        trackPadding = PaddingValues(vertical = MENU_SCROLLBAR_TRACK_PADDING),
        //  按住滑块 / 点轨道那一下的振动：判据收在 `Modifier.scrollBarTick`，与更新弹窗共用
        modifier = modifier.scrollBarTick(),
        alwaysVisible = true,
    )
}

/**
 * 滚动条那条窄带的宽度 —— 12dp。
 *
 *  不能按库的触摸目标（48dp）给：这一层压在菜单行上面，它有多宽就吃掉多宽的行点击。
 *  12dp 只盖住面板右内边距（库的 `InsideHorizontalPadding` = 16dp）里的一小条，行文字碰不到；
 *  拇指本身 3dp + 右端 5.5dp 留白，量起来仍够点中。
 */
private val MENU_SCROLLBAR_WIDTH = 12.dp

/**
 * 滚动条轨道上下让出去的留白 —— 8dp（`trackPadding`，库自带的那一档）。
 *
 *  面板圆角是 [POPUP_PANEL_RADIUS]：轨道贴着边画时，拇指滑到顶 / 底会压在那圈弧上，
 *  看起来像「滑出面板」。上下各收一档，两端都停在圆角之内。
 */
private val MENU_SCROLLBAR_TRACK_PADDING = 8.dp

/**
 * 一列菜单的**内容** —— 先把调用方那份「只有行与小标题」的列表展开成「内容 + 分隔」的展示序列
 * （见 [expandMenu]），再逐项画。
 *
 *  抽成一支只为把 [MenuPopup] 压回 `LongMethod` 的阈值：这里与「面板怎么摆、磨砂取哪一层、
 *  退场期间用哪一份内容」无关，纯渲染。
 *
 * @param interactive 淡出期间（`show = false`，内容还留着）传 `false` —— 那一帧不再派发点击，
 *   见 [MenuRow] 的 `interactive`。
 */
@Composable
private fun SettingsPage.MenuEntries(
    entries: List<MenuEntry>,
    interactive: Boolean,
    probe: GestureProbe?,
) {
    val parts = expandMenu(entries)
    val optionSize = parts.count { it is MenuPart.Item }
    var index = -1
    parts.forEachIndexed { position, part ->
        when (part) {
            is MenuPart.Item -> {
                index++
                MenuRow(
                    row = part.row,
                    index = index,
                    optionSize = optionSize,
                    // 「首行 / 末行」看的是**整列**的首尾元素：组标题占首行时，
                    // 标题下面那一行**不算**首行 —— 否则会多出一次首行内衬，
                    // 与组标题之间凭空多 2dp（查「小标题那笔账」时发现）。
                    isFirst = position == 0,
                    isLast = position == parts.lastIndex,
                    interactive = interactive,
                    probe = probe,
                )
            }

            is MenuPart.Title -> {
                PopupSectionTitle(part.text)
            }

            MenuPart.Line -> {
                PopupRowDivider()
            }

            MenuPart.TitleLine -> {
                PopupSectionDivider()
            }

            MenuPart.HeavyLine -> {
                HorizontalDivider(
                    thickness = DropdownDefaults.PopupGroupDividerThickness,
                    // 组间那条**槽**：暗色下加深（`#333` 面板上压到 `#1f` 才看得出是一道槽），
                    // 浅色下仍是那点灰。见 [popupDividerColor]。
                    color =
                        if (COUITheme.colorScheme.isDarkScheme) {
                            Color.Black.copy(alpha = MENU_GROUP_LINE_DARK_ALPHA)
                        } else {
                            Color.Black.copy(alpha = MENU_GROUP_LINE_LIGHT_ALPHA)
                        },
                )
            }
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
    // 禁用行被点时先收菜单再说原因（提示条在页面底部、被浮层压着）。
    //  走 CompositionLocal 而不是加参数：`MenuRow` 已经贴着 detekt 的三条上限，
    //  改签名还会让基线里那几条失效。
    val currentDisabledHint by rememberUpdatedState(row.disabledHint)
    //  `MenuRow` 下面那个 `item` 是 `remember` 出来的：直接闭包会把**创建那一刻**的
    //  `interactive` 钉死，淡出期间的禁用行照样会说「为什么不能点」。
    val currentInteractive by rememberUpdatedState(interactive)
    val iconColor by animateColorAsState(
        targetValue =
            when {
                !row.enabled -> COUITheme.colorScheme.disabledOnSecondaryVariant
                row.danger -> COUITheme.colorScheme.error
                row.selected -> COUITheme.colorScheme.primary
                else -> COUITheme.colorScheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "MenuRowIcon",
    )
    //  「随机图标配色」那一档（见 [LocalIconTint]）同样要过渡：开关一拨，整列图标的色相一起换，
    //  硬切就是一列字在跳。与上面那几档合成一个目标，避免两条动画各走各的。
    val randomTint = row.icon.takeIf { row.enabled && !row.danger && !row.selected }?.tinted()
    val rowIconTint by animateColorAsState(
        targetValue = row.iconTint ?: randomTint ?: iconColor,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "MenuRowTint",
    )
    // 进场定位到当前那一行：requester 挂在**整行内容区一样高**的盒子上，请求的是**上下各
    // 扩一档内边距**的矩形 —— 无参的 `bringIntoView()` 只保证「这个节点」可见，行的上下
    // 内边距会被裁掉（真机反馈「当前行没有整行（包括上下内边距）显示出来」）。
    // 行内边距有两档（中间行 8dp、首尾行 10dp），取大的那档一次覆盖。
    //  **行高按实测**，不按单行那条常量猜（带子标题的行更高），见 [rememberMenuRowBringIntoView]。
    val bringIntoView = remember { BringIntoViewRequester() }
    val measureRow = rememberMenuRowBringIntoView(row.selected, bringIntoView)
    //  小字比库默认的 `body2`（14sp）**降一档**到 `footnote1`（13sp）：body2 与行标题
    //  （`body1` 16sp）只差 2sp，两行摞在一起读起来像两个标题；降一档之后是「注解」。
    //  在 `remember` 外面取：`COUITheme.textStyles` 是 @Composable 的，而下面那块不是组合上下文。
    val summaryStyle = COUITheme.textStyles.footnote1
    val item =
        remember(
            row.title,
            row.summary,
            summaryStyle,
            row.icon,
            row.iconTint,
            row.enabled,
            row.danger,
            row.selected,
            row.trailingIcon,
            row.disabledHint,
        ) {
            DropdownItem(
                // 显示文本过一道截断（[ellipsizeMenuTitle]）：库里标题最多三行，长名字会撑宽整张菜单
                text = ellipsizeMenuTitle(row.title),
                // 标题下面那行小字（现状说明）；`null` = 单行。库自己管行数（标题 2 行、小字 2 行）
                summary = row.summary,
                summaryStyle = summaryStyle,
                enabled = row.enabled,
                // 禁用行照样能点：点一下先说清「为什么不能点」，再把菜单收掉
                //  （不收的话提示条被浮层压着，等于没说）。
                onDisabledClick =
                    if (row.enabled) {
                        null
                    } else {
                        { tapDisabledRow(row.title, currentDisabledHint, probe, currentInteractive) }
                    },
                // 文字由库按 `alert` 走 `DropdownColors.alertContentColor` —— 与图标的红同一档
                alert = row.danger,
                // **行尾那一格不画**（`showCheck = false`）：选中态改由**行首图标**换成对勾来表示
                //  （见下面的 `icon`）。库里那一格是恒画的 —— 一颗 24dp 字形 + 起始间距，未选中时
                //  只是把它画成透明，**布局上照样占着**，于是每一行右边都挂着一块永远空着的位子。
                showCheck = false,
                // 进下一层那些行（「响铃状态」「选择应用」）的行尾右箭头同样换掉 —— 库默认的
                // `COUIIcons.Basic.ArrowRight` 也是 COUI 那一套（12x24、描边 1.4），见 [PageIcons.EnterArrow]。
                //  逐行可换（[MenuEntry.Row.trailingIcon]）：跳页面那些行用「窗口 + 加号」，与网格一致。
                chevronIcon = row.trailingIcon ?: PageIcons.EnterArrow,
                icon = { m ->
                    val base =
                        m
                            .height(MENU_ROW_CONTENT_HEIGHT)
                            .bringIntoViewRequester(bringIntoView)
                            .then(measureRow)
                    // 第 0 行兼当「列表滚了没有」的探针：挂在行内容上 —— 滚的是列表**内层**，
                    // 外层那一圈与浮层窗口自己都不动（见 [GestureProbe.onContentPosition]）。
                    val tracked =
                        if (index == 0 && probe != null) {
                            base.onGloballyPositioned { c -> probe.onContentPosition(c.positionInWindow().y) }
                        } else {
                            base
                        }
                    Box(tracked, contentAlignment = Alignment.Center) {
                        Icon(
                            // 选中那一行：行首这颗**一律**换成对勾（行尾那颗已经关掉了，见上面的 `showCheck`）。
                            //  自带颜色的行（系统配色那几行的色块）也换 —— 但**颜色仍用这一行的色**
                            //  （`iconTint`，见下面的 `tint`）：对勾的**形状**说「当前是这一项」，
                            //  对勾的**颜色**说「是哪一套色」，两条信息都留着
                            //  （用户口径：「强调色菜单选中项行首用 checked」）。
                            if (row.selected) PageIcons.MenuCheck else row.icon,
                            null,
                            Modifier.size(MENU_ICON_SIZE),
                            //  随机配色只在「能点、不是危险项、不是选中项」时生效：
                            //  那三档的颜色各自带语义（灰 / 红 / 主色），染成随机色就丢了意思。
                            //  三档合出来的目标见 [rowIconTint]。
                            tint = rowIconTint,
                        )
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
}

/**
 * 禁用行被点那一下 —— **只有真的是点一下**才说原因（用户口径：点禁用行菜单别关、
 * 也不再闪按下色，见补丁 0008 的 `disabledClickable`）。
 *
 * ## 为什么要与启用行分开兜一道
 *
 *  启用行那次点选会经过 `onSelectedIndexChange`，那里已经按**这一段手势的位移**
 *  拦下了「滑动被当成选中」（见 `MenuRow` 里那段注释）。禁用行走的是另一条路：
 *  库里 `PopupListGestureHost.onUp` 直接派发 `handle.onClick`（`enabled || disabledClickable`），
 *  **绕过了**那道闸 —— 于是「按住往下滑到一行禁用项、松手」会把「为什么不能点」的提示弹出来。
 *
 *  只有**装得下的菜单**才看得出来：列表能滚的时候（内容超过最大高度）拖拽是在滚动，
 *  库走 `cancel` 不选行；装得下时 `isDynamicSelection` 为真，库跟着手指换行、抬手选中手指下那一行 ——
 *  正是一次「滑动」被当成了一次「点选」。
 *
 * @param hintText 这一行自己的限制（`MenuEntry.Row.disabledHint`）；`null` = 用页面的统一原因
 */
private fun SettingsPage.tapDisabledRow(
    title: String,
    hintText: String?,
    probe: GestureProbe?,
    interactive: Boolean,
) {
    val dragged = probe?.isDrag() == true
    XLog.w(
        "浮层点选（禁用行）：$title —— ${probe?.describe() ?: "没有位移账本"}" +
            when {
                !interactive -> "；浮层正在淡出 ⇒ 已吞掉这次点选"
                dragged -> "；这一段是滑动 ⇒ 已吞掉这次点选"
                else -> " → 只说明原因，菜单保持打开"
            },
    )
    if (!interactive || dragged) return
    //  **空串当没有**：`?:` 只在 `null` 时才落到页面那条统一原因，写成 `""` 的行会让这次点击
    //  变成「什么都没说」，而屏幕上还留着上一条提示（用户口径：空文案会回退到上一个消息文案）。
    val say = hintText?.takeIf { it.isNotBlank() } ?: disabledClickReason()
    hint(say, slot = HINT_SLOT_DISABLED)
}
