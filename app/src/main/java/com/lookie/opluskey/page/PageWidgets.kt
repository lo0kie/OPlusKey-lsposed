package com.lookie.opluskey.page

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownHeaderItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.InputViewDefaults
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldDefaults
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.layout.DialogButtonBarDefaults
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * **设置页里可复用的、库里没有对应物的小零件** —— 尺寸常量 + 一组手写的 Composable。
 */

/**
 * 一个格子的高度：`BasicComponent` 的最小高度 48dp + `CardListPosition.Full`
 * 上下各补的 2dp（COUI 的 `coui_list_card_head_or_tail_padding`）。
 * 一屏放得下十来行（这一页是 `verticalScroll`，真放不下也能滚）。
 */
internal val TILE_HEIGHT = 52.dp

internal val TILE_SIDE_PADDING = 12.dp

/**
 * 行尾图标按钮的**触达区边长**（正方形，圆角给一半 ⇒ 圆形高亮圈）。
 *
 *  这两个尺寸现在是给 `IconButton` 的 `minWidth/minHeight` 用的 ——
 * 库的按压高亮是画在 `defaultMinSize` 那一层上的 squircle，**尺寸不由内容决定**，
 * 所以「高亮圈多大」这件事只能靠这两个数说（默认 48dp/24dp 见下）。
 */
internal val ICON_ACTION_SIZE = 32.dp

/** 行尾图标按钮里的图标边长。 */
internal val ICON_ACTION_ICON = 20.dp

/**
 * 行尾的一颗**图标按钮**（32dp 圆形点击区 + 20dp 图标）—— 步骤的
 * 上移 / 下移 / 删除都用它。
 *
 * `Box(size) + clip(CircleShape) + clickable` 拼的圆，现在换成库的
 * [`IconButton`]（`basic/IconButton.kt`：`Box` + `defaultMinSize` + squircle 高亮 + `clickable`）。
 * 「高亮圈超出触达区一大圈」的那一档；给 [ICON_ACTION_SIZE] 与它的一半，
 * 高亮就只落在 32dp 圆内，与换之前手拼的那颗**逐像素一致**。
 *
 * @param enabled false 时上灰、且**不吃点击**（首行的「上移」、末行的「下移」）
 * @param error true 时用 `error` 色 —— 有破坏性的操作按 COUI 的 `couiColorError` 上色
 * @param tint 非 null 时**覆盖**上面那两档配色 —— 给「这颗是主题色的主操作」用
 *   （已保存命令卡片右上角那颗「测试命令」）。`enabled = false` 仍然优先，照样上灰。
 */
@Composable
internal fun IconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    error: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    //  颜色随 `enabled` / `error` / `tint` 切换，带过渡：置灰与恢复不再「啪」地跳
    //（`tint` 那一档是调用点传进来的常量色，动画从当前值走到它，同样自然）。
    val color by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.disabledOnSecondaryVariant
            tint != null -> tint
            error -> scheme.error
            else -> scheme.onSurfaceContainer
        },
        label = "IconAction",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = ICON_ACTION_SIZE,
        minHeight = ICON_ACTION_SIZE,
        cornerRadius = ICON_ACTION_SIZE / 2,
    ) {
        Icon(icon, contentDescription = description, Modifier.size(ICON_ACTION_ICON), tint = color)
    }
}

/**
 * 浮层菜单两项之间的细线 —— 顶栏 ⋮ 菜单、已保存命令的长按菜单、「添加命令」选择器都用它。
 */
@Composable
internal fun PopupRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 16.dp),
    )
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
 *  - [submenu]：行尾画右箭头（这一行会进下一层）。
 */
internal sealed interface MenuEntry {
    data class Row(
        val title: String,
        val icon: ImageVector,
        val enabled: Boolean = true,
        val selected: Boolean = false,
        val danger: Boolean = false,
        val submenu: Boolean = false,
        val onPick: () -> Unit,
    ) : MenuEntry

    /** 组标题（库的 `DropdownHeaderItem`）。 */
    data class Group(val text: String) : MenuEntry

    /** 相邻两行之间的细线（[PopupRowDivider]）。 */
    data object RowLine : MenuEntry

    /** 组与组之间的**加深带**（颜色 / 厚度取库常量）。 */
    data object GroupLine : MenuEntry
}

/**
 * 浮层行**内容区**的高度 —— 库的行高 `DropdownDefaults.PopupItemMinHeight`（48dp）
 * 减掉它自己上下各 `MiddleVerticalPadding`（8dp）的内边距。
 *
 * 只服务「进场定位到当前那一行」（见 [MenuRow]）：`bringIntoView` 只保证**挂 requester
 * 的那个节点**可见，所以那个节点必须**和整行内容区一样高** —— 挂在 20dp 的图标上只会让
 * 图标可见，行被上下各裁掉一截（2026-10-13 真机反馈「滚动位置不够，选中的行没有完整显示」）。
 *
 *  行高是 `heightIn(min = …)`，文本多行时会长高 ⇒ 这个常量算的是**单行**那一档；
 * 浮层里的行都是单行标题（动作名 / 命令名），够用。
 */
private val MENU_ROW_CONTENT_HEIGHT = DropdownDefaults.PopupItemMinHeight -
    DropdownDefaults.MiddleVerticalPadding * 2

/**
 * **项目里唯一的浮层菜单** —— 顶栏 ⋮、命令长按、响铃档位、「添加命令」一级与二级都走它。
 *
 * 定位一律是库的 `DropdownPositionProvider` + 触点锚点（[preciseClick]）：横向以触点为中、
 * 下方放不下翻到上方、不跟随滚动。
 *
 *  ⚠️ **启用行必须是 `ListPopupColumn` 的直接子项** —— 给它套一层 `Box` 之后，点它只会
 * 把浮层关掉、`onSelectedIndexChange` 一次都不派发（2026-10-11 真机踩到：点「打开默认界面」
 * 菜单关了，页面没切）。所以只有**禁用行**才套「吃事件的透明 Box」（见 [MenuRow]）。
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
    OverlayListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        preciseClickState = preciseClick,
        maxHeight = maxHeight,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismiss,
    ) {
        val column: @Composable () -> Unit = {
            ListPopupColumn {
                val optionSize = entries.count { it is MenuEntry.Row }
                var index = -1
                entries.forEachIndexed { position, entry ->
                    when (entry) {
                        is MenuEntry.Row -> {
                            index++
                            MenuRow(
                                row = entry,
                                index = index,
                                optionSize = optionSize,
                                // 「首行 / 末行」看的是**整列**的首尾元素：组标题占首行时，
                                // 标题下面那一行**不算**首行 —— 否则会多出一次首行内衬，
                                // 与组标题之间凭空多 2dp（2026-10-13 查「小标题那笔账」时发现）。
                                isFirst = position == 0,
                                isLast = position == entries.lastIndex,
                            )
                        }

                        is MenuEntry.Group -> DropdownHeaderItem(entry.text, isFirst = position == 0)
                        MenuEntry.RowLine -> PopupRowDivider()
                        MenuEntry.GroupLine -> HorizontalDivider(
                            thickness = DropdownDefaults.PopupGroupDividerThickness,
                            color = DropdownDefaults.PopupGroupDividerColor,
                        )
                    }
                }
            }
        }
        // `ListPopupColumn` 没有 `modifier` 参数 ⇒ 整列锚点只能套在它外面。
        if (columnAnchor == null) column() else Box(Modifier.preciseClickAnchor(columnAnchor)) { column() }
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
 */
@Composable
private fun SettingsPage.MenuRow(
    row: MenuEntry.Row,
    index: Int,
    optionSize: Int,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val tick = rememberTick()
    val iconColor by animateColorAsState(
        targetValue = when {
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
        val rowRect = with(LocalDensity.current) {
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
    val item = remember(row.title, row.icon, row.enabled, row.danger, row.selected) {
        DropdownItem(
            text = row.title,
            enabled = row.enabled,
            // 文字由库按 `alert` 走 `DropdownColors.alertContentColor` —— 与图标的红同一档
            alert = row.danger,
            // 行尾那颗「选中」的对勾走我们自己的 Tabler 字形：库默认那颗是 `COUIIcons.Basic.Check`
            //（描边 1.4、又宽又扁），与行首的 Tabler 图标（描边 2）不是一支笔，见 [PageIcons.MenuCheck]。
            // 恒传：没选中的行库本来就把这颗画成透明，只有选中行看得见。
            checkIcon = PageIcons.MenuCheck,
            // 进下一层那些行（「响铃状态」「选择应用」）的行尾右箭头同样换掉 —— 库默认的
            // `COUIIcons.Basic.ArrowRight` 也是 COUI 那一套（12x24、描边 1.4），见 [PageIcons.EnterArrow]。
            chevronIcon = PageIcons.EnterArrow,
            icon = { m ->
                Box(
                    m.height(MENU_ROW_CONTENT_HEIGHT).bringIntoViewRequester(bringIntoView),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(row.icon, null, Modifier.size(MENU_ICON_SIZE), tint = iconColor)
                }
            },
        )
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
            onSelectedIndexChange = {
                tick()
                row.onPick()
            },
        )
    } else {
        //  禁用的这一行库里**根本不挂 `clickable`**，这一下没人 consume、直接穿到浮层自己的
        // 「点外面就关」那套逻辑上 —— 菜单照样会关。压一层**透明的吃事件层**把它挡住
        //（不给任何视觉，「灰」由 `DropdownItem(enabled = false)` 自己画），
        // 效果就是「点了没反应、菜单还开着」。
        Box {
            DropdownImpl(
                item = item,
                optionSize = optionSize,
                isSelected = row.selected,
                index = index,
                isFirst = isFirst,
                isLast = isLast,
                // 走不到（这一行是禁用的），留着只为满足签名
                onSelectedIndexChange = {},
            )
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}

/**
 * 页面**右下角那颗 FAB** —— 位置与显隐动画**走同一份**，两个调用点共用。
 *
 *  · 「按键绑定」的「新增命令」：往下滚就藏、往上滚就露（[rememberFabVisibleOnScroll]）；
 *  · 「选择应用」的「滚到顶部」：搜索中、或已经在顶部时不出现。
 *
 *  位置（右 [PAGE_MARGIN]×2、下 [PAGE_BOTTOM_PADDING]×2）、滑动方向、时长全在这一处，
 *  免得两条 FAB 各走各的。
 *
 *  滑动是**竖直**的：从底边**往上滑入**、**往下滑出**（`slideIn/OutVertically`）。
 *  滑出要**滑到屏幕下沿之外**：它们离底边还有 [PAGE_BOTTOM_PADDING]×2 那一档，
 *  只滑自身高度的话会停在半空 —— 那段距离一起算进去。
 *
 *  ⚠️ 做成**独立 composable**，而不是让调用点直接写 `AnimatedVisibility`：
 *  这两处都在 `Column` 里的那个 `Box` 中，`AnimatedVisibility` 在那儿会同时看到
 *  `BoxScope` 与 `ColumnScope` 两个隐式接收者，解析到 `ColumnScope` 那个重载并报
 *  「cannot be called with an implicit receiver」。
 *
 * @param visible 显不显示（判据在调用点）
 * @param content FAB 本体（各页自己那颗）
 */
@Composable
internal fun BoxScope.PageCornerFab(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val slidePx = with(LocalDensity.current) { (PAGE_BOTTOM_PADDING * 2).roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = PAGE_MARGIN * 2, bottom = PAGE_BOTTOM_PADDING * 2),
        enter = slideInVertically { it + slidePx },
        exit = slideOutVertically { it + slidePx },
    ) { content() }
}

/**
 * 「**往下滚就藏、往上滚就露**」—— 页面右下角那颗 FAB 的显隐判据（配 [PageCornerFab]）。
 *
 *  用法：`val fabVisible = rememberFabVisibleOnScroll(scrollState)`。
 *
 *  判的是 [ScrollState.value] 的**增量符号**：`value` 变大 = 内容往上走 = 「往下滚」⇒ 藏；
 *  变小 = 「往上滚」⇒ 露。
 *
 *  ⚠️ 不能只看「有没有滚过」、也不能不设门槛：手指轻轻抖一下也会换向，FAB 就会一闪一闪。
 *  所以单次增量要超过 [FAB_SCROLL_DIRECTION_THRESHOLD] 才认作换向（同一个方向上的连续
 *  滚动只在那一下判一次，不会反复翻转）。
 *
 * @param scrollState 当前页的滚动状态 —— **传当前那一页的那一份**（三个手势页各持一份）。
 *   换页时它变了 ⇒ [visible] 跟着重置成「露」，不会把上一页藏起来的状态带过来。
 */
@Composable
internal fun rememberFabVisibleOnScroll(scrollState: ScrollState): Boolean {
    val thresholdPx = with(LocalDensity.current) {
        FAB_SCROLL_DIRECTION_THRESHOLD.roundToPx()
    }
    var visible by remember(scrollState) { mutableStateOf(true) }
    LaunchedEffect(scrollState, thresholdPx) {
        var last = scrollState.value
        snapshotFlow { scrollState.value }.collect { value ->
            val delta = value - last
            last = value
            when {
                delta > thresholdPx -> visible = false
                delta < -thresholdPx -> visible = true
            }
        }
    }
    return visible
}

/**
 * **动态显隐的一段控件** —— 统一动画的唯一入口（规格见 [VISIBILITY_ENTER]）。
 *
 * 为什么要有这个包装：`AnimatedVisibility` 的 `enter` / `exit` 每个调用点各写一遍
 * 容易写岔（时长、方向、少写一半），而「所有动态显隐的控件动画一致」正是这件事的目的。
 * 用法：把原来那个 `if (条件) { 控件… }` 直接换成 `AnimatedColumn(visible = 条件) { 控件… }`
 * —— 条件里那个 `if` 没了，**里面的缩进不用动**（多出来的那层 `Column` 在本函数里，
 * 见下）。
 *
 *  ⚠️ 里面那个 `Column` **不能省**：`AnimatedVisibility` 的内容槽位不是 `Column`，
 * 多个子项（本项目里常见「一档 `Spacer` + 小标题 + 一张卡」这种组合）会**叠在同一处**。
 * 所以这里固定套一层 `Column` —— 这也正是调用点不必再动缩进的原因。
 *
 * @param visible 显不显示（原来那个 `if` 的条件）
 */
@Composable
internal fun AnimatedColumn(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = VISIBILITY_ENTER,
        exit = VISIBILITY_EXIT,
    ) {
        Column(content = content)
    }
}

/**
 * 同 [AnimatedColumn]，但**只淡入淡出、不动高度** —— 给「位置本来就在文档流里、
 * 高度由别处定死」的东西用（格子里的那行小字、居中那圈加载提示）：
 * 给它们套展开 / 收起会把所在的行 / 卡片撑一下，反而是抖。
 */
@Composable
internal fun AnimatedFade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(VISIBILITY_MS)),
        exit = fadeOut(tween(VISIBILITY_MS)),
    ) { content() }
}

/**
 * 确认弹窗（销毁性操作）：左侧「取消」中性色、右侧动作项 error 红 —— 与「重置所有配置」
 * 用的是同一份控件。
 */
@Composable
internal fun SettingsPage.ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    /** 动作项文案（「重置」/「删除」…）。 */
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 页面离开时跟着一起收（见 [SettingsPage.closeOverlays]）—— 确认窗是**模态**的，
    // 留着它比留着菜单更糟：页面都走了它还在那儿等一个不会来的点击。
    OverlayCloser(show = show, onDismiss = onDismiss)
    OverlayDialog(
        show = show,
        title = title,
        // 库默认把标题与摘要都上 `onBackground`（同一档，主次分不出来）—— 正文压一档
        titleColor = COUITheme.colorScheme.onBackground,
        summaryColor = COUITheme.colorScheme.onBackgroundVariant,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        DialogButtonRow(
            negative = "取消",
            positive = action,
            positiveDanger = true,
            onNegative = onDismiss,
            onPositive = onConfirm,
        )
    }
}

/**
 * 弹窗底部那条**横向按钮栏**：左「取消」中性色 + 竖分隔线 + 右动作项。
 *
 * 库的 `DialogButtonBar` 只能给 negative / neutral / positive **同一套** `TextButtonColors`，
 * 分不出「取消是中性色、动作项是红/主题色」，所以整条自己拼（见其 KDoc 里的实测记录）。
 *
 * @param positiveDanger 动作项是否按破坏性上色（红）。非破坏性动作走主题色 ——
 *   「前往下载」这类给红会读成「这是危险操作」。
 */
@Composable
internal fun DialogButtonRow(
    negative: String,
    positive: String,
    positiveDanger: Boolean,
    onNegative: () -> Unit,
    onPositive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        TextButton(
            text = negative,
            onClick = onNegative,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            pressScaleEnabled = false,
            cornerRadius = DialogButtonBarDefaults.ButtonCornerRadius,
            minHeight = DialogDefaults.ButtonBarMinHeight,
            colors = ButtonDefaults.textButtonColorsBorderless(
                textColor = COUITheme.colorScheme.onSurfaceSecondary,
                disabledTextColor = COUITheme.colorScheme.disabledOnSurface,
            ),
            insideMargin = DialogDefaults.ButtonBarInsideMargin,
        )
        Box(
            modifier = Modifier
                .padding(
                    top = DialogDefaults.ButtonBarDividerInsetTop,
                    bottom = DialogDefaults.ButtonBarDividerInsetBottom,
                )
                .width(DialogDefaults.ButtonBarDividerThickness)
                .fillMaxHeight()
                .background(COUITheme.colorScheme.dividerLine),
        )
        TextButton(
            text = positive,
            onClick = onPositive,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            pressScaleEnabled = false,
            cornerRadius = DialogButtonBarDefaults.ButtonCornerRadius,
            minHeight = DialogDefaults.ButtonBarMinHeight,
            colors = ButtonDefaults.textButtonColorsBorderless(
                textColor = if (positiveDanger) {
                    COUITheme.colorScheme.error
                } else {
                    COUITheme.colorScheme.primary
                },
            ),
            insideMargin = DialogDefaults.ButtonBarInsideMargin,
        )
    }
}

/**
 * **长按的振动只走通用通道**（[rememberTick] / [tickNow]）。
 *
 * 库的交互式 `Card` 内部用的是 `Modifier.combinedClickable`，它默认还会**自己**振一次
 * `HapticFeedbackType.LongPress`（经 `LocalHapticFeedback` → 宿主 `View.performHapticFeedback`）——
 * 那条路不看系统静音，于是长按会比单击多出一次不受系统状态约束的振动。
 *
 * 做法：在**那一格**外面把 `LocalHapticFeedback` 换成本实现（见 [ActionTile] 与
 * 「自定义命令」页里已保存命令那组格子）。只包格子 —— 滑杆 / 浮层自己的触觉不走这里。
 */
internal val NoHapticFeedback: HapticFeedback = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * 开关那一行的 `LocalView` —— 只为一件事：把库 `Switch` 自己那次振动吞掉。
 *
 * 库的 `Switch` 在 `checked` 变化时**无条件**振一次 `CouiHapticEffect.Switch`（`SwitchKt`
 * 里那个 `LaunchedEffect` 先调 `onCheckedChange`、紧接着调振动），不看系统静音。
 * 于是拨开关会比点别的控件多出一次不受系统状态约束的振动。
 *
 *  它走的是 **`LocalView.performHapticFeedback`**、不是 `LocalHapticFeedback`，
 * 所以 [NoHapticFeedback] 拦不住，必须连 `LocalView` 一起换。
 *  只包开关那一行：库里读 `LocalView` 的只有 `CouiHaptic` / `ListPopup` /
 * `WindowNavigationEventScope`，后两者都在这一行之外（浮层与页面级导航事件）。
 */
internal class NoHapticView(context: Context) : View(context) {
    override fun performHapticFeedback(feedbackConstant: Int): Boolean = false

    override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean = false
}

/**
 * 把一行开关包进「触觉只走通用通道」的作用域 —— 见 [NoHapticView] 与 [NoHapticFeedback]。
 *
 * 包两层是必要的：库自己的振动走 `LocalView`，而 `Modifier.clickable` 那一系的
 * 长按振动走 `LocalHapticFeedback`，两条路都得堵，开关的振动才只剩 [tickNow] 一处。
 */
@Composable
internal fun NoSwitchHaptic(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = remember(context) { NoHapticView(context) }
    CompositionLocalProvider(
        LocalView provides view,
        LocalHapticFeedback provides NoHapticFeedback,
    ) { content() }
}

/**
 * 格子外面的**焦点圈**（键盘 / 遥控走焦点时那一圈高亮）。
 *
 * 圈的**亮起 / 熄灭带过渡**（`primary` ⇄ 透明）—— 硬切的话它会「啪」地跳出来，
 * 而卡片里的标题 / 类型图标这时正在**渐变**（见 `CommandScreen` 里那处），
 * 两者不同步就很难看。过渡参数用 `animateColorAsState` 的默认值，
 * 与页里其他状态色（[IconAction] / `SliderRow` / `BindingScreen` 的格子）同一组。
 */
@Composable
internal fun TileFocusRing(focused: Boolean) {
    val ringColor by animateColorAsState(
        targetValue = if (focused) COUITheme.colorScheme.primary else Color.Transparent,
        label = "TileFocusRing",
    )
    Box(
        Modifier
            .fillMaxSize()
            .squircleBorder(
                width = 2.dp,
                color = ringColor,
                cornerRadius = CardDefaults.CornerRadius,
            ),
    )
}

/** 「带图标的卡片输入」（[IconInputView]）里行首图标的边长 —— 与全页图标同一个 22dp 纪律。 */
internal val INPUT_ICON_SIZE = 22.dp

/** 行首图标与右侧内容之间的间距。 */
internal val INPUT_ICON_GAP = 8.dp

/** 错误提示淡入 / 淡出的时长（毫秒）与缓动 —— 与库的 `InputView` 同一组值（其文档给出）。 */
private const val ERROR_FADE_IN_MS = 217
private const val ERROR_FADE_OUT_MS = 283
private val INPUT_ERROR_EASE = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * **带行首图标的卡片输入** —— 库的 `InputView` 做不到这件事，所以在这里拼一个同款的。
 *
 * `InputView` 的 `leadingIcon` 是**直接透传给内部 `TextField`** 的（见库的 `InputView.kt`）：
 * 它只把输入框里的文字挤开，而卡片里那行 `title` 是库在内容左边界自己画的，**不跟着让位**；
 * `insideMargin` 之类的参数又会让两者**一起**平移，分不开。
 *
 * 于是这里用库的公开件自己拼，排版逐项照抄 `InputView`：
 *  · 卡片：[Card]（12dp 圆角 + `surfaceContainer`，与 `InputViewDefaults` 同值）；
 *  · 标题：`body2`（14sp，2026-10-13 由库的 16sp `titleTextStyle()` 降一档）+ 上下 12 / 4dp + 最小高 22dp；
 *  · 输入框：[TextField]，`textStyle` 用库默认（16sp）+ `TextFieldMode.None` + `insideMargin = DpSize(0, 0)`；
 *  · 错误提示：卡片**外**那行 10sp 红字，淡入 217ms / 淡出 283ms（库文档给的那组数值）。
 * 差别只有一处：**行首图标占左侧一格，标题与输入框都从它右边起**（图标 [INPUT_ICON_SIZE]
 * + 间距 [INPUT_ICON_GAP]）。
 *
 * @param icon 行首图标（按 [INPUT_ICON_SIZE] 画，与全页图标同一个尺寸纪律）
 */
@Composable
internal fun SettingsPage.IconInputView(
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String = "",
    enabled: Boolean = true,
) {
    val colors = InputViewDefaults.inputViewColors()
    // ⚠️ 标题**不能**用 `colors.titleColor`：它是 `onSurface`，而输入内容的颜色走
    // `TextField` 的 `LocalContentColor`（本组件里就是 `Card` 的 `contentColor` =
    // `onSurfaceContainer`）—— **这两个色值完全相同**（`0xE6000000` / `0xE6FFFFFF`），
    // 撞在一起卡片里就分不出「字段名」和「输入内容」。
    //  于是标题分两档，按**有没有内容**切：
    //  · **空值时**：输入框里什么都没有，标题就替它站那一位 ⇒ 用其他行主标题那一档
    //    （`onBackground`，与 `BasicComponentDefaults.titleColor()` 同色）；
    //  · **有内容时**：退回 `onSurfaceSecondary`（与输入框自己的 label / 占位符同色）——
    //    层级变成「弱标题 + 强内容」，字段名不会跟内容抢。
    //  同样带过渡：打字 / 清空、主开关置灰 / 恢复时都不跳变（与 [IconAction] / `SliderRow`
    // 同一组参数）。
    val titleColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledTitleColor
            value.isEmpty() -> COUITheme.colorScheme.onBackground
            else -> COUITheme.colorScheme.onSurfaceSecondary
        },
        label = "InputTitle",
    )
    //  行首图标**自己一档**：与其他行的行首图标同色（`onSurfaceContainer`）。
    // 它原先跟着标题走（`onSurfaceSecondary`），比页里别的行首都暗一档。
    val iconColor by animateColorAsState(
        targetValue = if (enabled) {
            COUITheme.colorScheme.onSurfaceContainer
        } else {
            COUITheme.colorScheme.disabledOnSecondaryVariant
        },
        label = "InputIcon",
    )
    val isError = errorMessage.isNotEmpty()
    // 淡出期间要**留着最后那条非空消息**，否则红字先消失、再走淡出（库同此做法）
    var shownError by remember { mutableStateOf(errorMessage) }
    if (isError && shownError != errorMessage) shownError = errorMessage

    Column(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = InputViewDefaults.CornerRadius,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = InputViewDefaults.ContentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(INPUT_ICON_SIZE),
                    tint = iconColor,
                )
                Spacer(Modifier.width(INPUT_ICON_GAP))
                // 标题 + 输入框：**这一列**整体让出图标那一格，所以两者不用各自再加左内缩
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        //  标题**降一档**（2026-10-13）：库的 `titleTextStyle()` 是 16sp Medium，
                        // 压过下面的输入内容 —— 输入框应该是「内容大、字段名小」。
                        // 降到 body2（14sp）。
                        style = COUITheme.textStyles.body2,
                        color = titleColor,
                        modifier = Modifier
                            .padding(
                                top = InputViewDefaults.TitlePaddingTop,
                                bottom = InputViewDefaults.TitlePaddingBottom,
                            )
                            .defaultMinSize(minHeight = InputViewDefaults.TitleMinHeight),
                    )
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = InputViewDefaults.FieldPaddingBottomWithTitle),
                        backgroundMode = TextFieldMode.None,
                        insideMargin = DpSize(0.dp, 0.dp),
                        label = label,
                        enabled = enabled,
                        isError = isError,
                        //  库默认的 16sp（2026-10-13 试过升到 `paragraph` 17sp 又还原）：
                        // `TextField` 会拿 `textStyle` 的字号**顺带推算浮动 label 的大小**
                        //（源码：`labelNormalFontSize = resolvedTextStyle.fontSize`），
                        // value 一升，上面那行「请输入…」跟着变大，
                        // 标题与内容之间的空隙就被撑开了 —— 动这里必须连着 label 一起看。
                        textStyle = TextFieldDefaults.textStyle(TextFieldMode.None),
                        singleLine = true,
                        maxLines = 1,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(tween(ERROR_FADE_IN_MS, easing = INPUT_ERROR_EASE)),
            exit = fadeOut(tween(ERROR_FADE_OUT_MS, easing = INPUT_ERROR_EASE)),
        ) {
            Text(
                text = shownError,
                fontSize = InputViewDefaults.ErrorFontSize,
                color = colors.errorColor,
                modifier = Modifier.padding(
                    top = InputViewDefaults.ErrorPaddingTop,
                    start = InputViewDefaults.ContentPadding,
                    end = InputViewDefaults.ContentPadding,
                ),
            )
        }
    }
}
