package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 空态 / 加载态在**可见区**里居中那一块的账 —— 全项目一处。
 *
 *  三处在用：已保存的命令（`CommandListPage`）、变量列表、场景块。三处原先各写一遍
 *  「视口 − 顶栏 − 页底留白」，算式一样、注释各说一遍 —— 而漏减一项的后果是**中线偏上或偏下**
 *  （真机口径「没有垂直居中」），那种偏差对着一屏空白根本看不出来，只能照着账核。
 *
 *  **为什么不能在这些位置直接用 `fillMaxHeight`**：它们都在滚动容器（或 `LazyColumn` 的项）里，
 *  子项拿到的是无限高度约束，`fillMaxHeight()` 在里面没有意义 —— 只能由外层量一次视口再传进来。
 *
 *  @param maxHeight 视口高度（外层 `BoxWithConstraints` 的 `maxHeight`）
 *  @param topInset 压在内容上的顶部浮层（顶栏、分页带、搜索带）
 *  @param bottomInset 页底那档留白：不减的话空态比可见区高出一截，居中位置会偏下、还多出一段能拖的空白
 */
internal fun visibleAreaHeight(
    maxHeight: Dp,
    topInset: Dp,
    bottomInset: Dp = 0.dp,
): Dp = (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp)

/**
 * 「让出顶部浮层、在剩下的那块里居中」—— 空态与加载态**唯一的一层摆位**。
 *
 *  @param modifier 高度从哪来由调用点定：`fillMaxSize`（浮层）、`fillParentMaxHeight`（列表项）、
 *    `height(...)`（滚动内容里量好的那一块）
 */
@Composable
internal fun CenteredInInsets(
    modifier: Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(top = topInset, bottom = bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 滚动内容里的空态那一块 —— 撑满「可见区那一块」并居中。
 *
 *  与 [Feedback] 分工：那个管「空态里面长什么样」（字形 + 一句话），这个管「摆在哪」。
 *  三处滚动页原先各写一个 `Box(fillMaxWidth().height(...), Center)`，同一个意思三个写法。
 */
@Composable
internal fun VisibleAreaCentered(
    height: Dp,
    content: @Composable () -> Unit,
) {
    CenteredInInsets(Modifier.fillMaxWidth().height(height)) { content() }
}
