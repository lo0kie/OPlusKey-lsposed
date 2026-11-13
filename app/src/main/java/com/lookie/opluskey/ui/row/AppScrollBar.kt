package com.lookie.opluskey.ui.row

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.feedback.scrollBarTick
import io.github.suqi8.coui.kmp.basic.ScrollBarAdapter
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi

// 竖向滚动条 —— 全项目**唯一**那一份。
//
// 库的 `VerticalScrollBar` 只画条：常亮与否、振动、轨道两端留白这三件事都得调用点自己配。
// 三处（浮层菜单 / 选择应用列表 / 图标网格）原先各写一份，振动那段甚至有一处是内联复制出来的
// —— 改一处漏两处，表现就是「这条滚起来有振动、那条没有」。

/**
 * 轨道上下让出去的留白 —— 8dp（库自带的那一档）。
 *
 *  面板 / 弹窗都有圆角：轨道贴着边画时，拇指滑到顶 / 底会压在那圈弧上，看着像「滑出面板」。
 *  上下各收一档，两端都停在圆角之内。
 *
 *  **个别地方要更宽时由调用点自己传**（菜单那条取小标题上留白那一档，见 `PageMenuPopup`）——
 *  默认值不为某一个调用点让步。
 */
private val SCROLLBAR_TRACK_PADDING = 8.dp

/**
 * **通用竖向滚动条** —— 三处共用（浮层菜单 `MenuScrollBar`、选择应用列表 `PickerScrollBar`、
 * 图标选择器），几何（宽度 / 高度 / 边距）由调用点给。
 *
 *  三件事收在这一层，调用点不必再各配一遍：
 *   · **常亮**（`alwaysVisible`）：这些地方都不是「滑动时给你的反馈」，而是「这下面还有内容」的
 *     唯一提示 —— 按库默认那种「不碰就淡出」等于没有提示（用户口径）。
 *   · **按住滑块的振动**走通用通道（[scrollBarTick]）：判据是「DOWN 已被库里 consume」，
 *     按在轨道空白处不振。
 *   · **轨道两端留白**（[SCROLLBAR_TRACK_PADDING]）：拇指不压在圆角上。
 *
 * @param adapter 由 [rememberScrollBarAdapter] 给（三个便捷重载会替你建）
 * @param modifier 几何与位置 —— 宽度、高度、贴哪条边由调用点定
 * @param trackPadding 轨道两端让出去的留白；传 `PaddingValues()` 可关掉
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    adapter: ScrollBarAdapter,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    VerticalScrollBar(
        adapter = adapter,
        trackPadding = trackPadding,
        modifier = modifier.scrollBarTick(),
        alwaysVisible = true,
    )
}

/** 页面级滚动（`Column` + `verticalScroll`）那一档 —— 浮层菜单用它。 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    AppScrollBar(rememberScrollBarAdapter(scrollState), modifier, trackPadding)
}

/** `LazyColumn` 那一档 —— 选择应用 / 选择界面两页用它。 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    AppScrollBar(rememberScrollBarAdapter(listState), modifier, trackPadding)
}

/** `LazyVerticalGrid` 那一档 —— 图标选择器用它。 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun AppScrollBar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    trackPadding: PaddingValues = PaddingValues(vertical = SCROLLBAR_TRACK_PADDING),
) {
    AppScrollBar(rememberScrollBarAdapter(gridState), modifier, trackPadding)
}
