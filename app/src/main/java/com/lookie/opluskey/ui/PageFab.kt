package com.lookie.opluskey.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.lookie.opluskey.ui.model.FAB_SCROLL_DIRECTION_THRESHOLD
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.edgeSlideIn
import com.lookie.opluskey.ui.model.edgeSlideOut
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.theme.COUITheme

// 右下角那颗 FAB 那一族：按钮本体、页面角落定位、按滚动方向显隐。

/**
 * 页面右下角那颗 FAB 的**本体** —— 形状 / 底色 / 阴影收在这里，[PageCornerFab] 的两个调用点共用
 *（各自写一份迟早走形：这几样必须两处一致）。
 *
 *  **不挂磨砂** —— 这颗按钮上它两头都不成立：
 *
 *   · 底色是主题原色（`colorScheme.primary`）。糊要看得见，底色就得压成半透，颜色立刻被底下那页
 *     掺淡；调回实色，糊又被盖得一点不剩。
 *   · 库的按下反馈缩的是 `Surface` **自己**那一层（`PressedScale = 0.92`），而糊只能挂在它外面
 *     —— 缩放只缩里面的面板，糊层不跟着缩，按住时从面板边上露出一圈没缩的糊。
 *
 *  56dp 的实心圆本来也剩不下多少地方给糊看，不值得为它赔上颜色。
 *
 *  形状与阴影走库默认那一档（`CircleShape`、`FloatingActionButtonDefaults.ShadowElevation` 12dp）：
 *  库自己那层 `graphicsLayer(shadowElevation, shape, clip = false)` 外面没有 `clip` 去裁它，
 *  阴影画得出来，不必自己另补一层。
 *
 * @param onClick 点击回调
 * @param modifier 调用点自己那点修饰（「按键绑定」页拿它套 `alpha` 做禁用观感）
 * @param content FAB 上的内容（图标）—— 内容色由调用点给（`onPrimary`）
 * @param containerColor 底色，默认主题原色。**禁用态要换底色，不要在外面套 `alpha`**：
 *   `Modifier.alpha` 会单独合成一层，层的边界就是这颗按钮的方框 —— 库内部那层
 *   `graphicsLayer(shadowElevation)` 画的阴影落进层里，就被裁成**正方形**（真机踩过）。
 *   底色 / 字形色都是能跟着状态动画的，阴影不该为「变灰」陪葬。
 */
@Composable
internal fun PageFabButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = COUITheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
    ) { content() }
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
 *  注意：做成**独立 composable**，而不是让调用点直接写 `AnimatedVisibility`：
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
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PAGE_MARGIN * 2, bottom = PAGE_BOTTOM_PADDING * 2),
        // 与底部工具栏同一份规格（见 [edgeSlideIn]）：差别只在多滑的距离
        enter = edgeSlideIn(slidePx),
        exit = edgeSlideOut(slidePx),
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
 *  注意：不能只看「有没有滚过」、也不能不设门槛：手指轻轻抖一下也会换向，FAB 就会一闪一闪。
 *  所以单次增量要超过 [FAB_SCROLL_DIRECTION_THRESHOLD] 才认作换向（同一个方向上的连续
 *  滚动只在那一下判一次，不会反复翻转）。
 *
 * @param scrollState 当前页的滚动状态 —— **传当前那一页的那一份**（三个手势页各持一份）。
 *   换页时它变了 ⇒ [visible] 跟着重置成「露」，不会把上一页藏起来的状态带过来。
 */
@Composable
internal fun rememberFabVisibleOnScroll(scrollState: ScrollState): Boolean {
    val thresholdPx =
        with(LocalDensity.current) {
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
