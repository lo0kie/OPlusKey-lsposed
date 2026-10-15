package com.lookie.opluskey.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_ACCEL
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER_MS
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT_MS
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.FAB_SCROLL_DIRECTION_THRESHOLD
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.screen.CommandScreen
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.FloatingActionButtonDefaults
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 右下角那颗 FAB 那一族：背景取样、按钮本体、页面角落定位、按滚动方向显隐。 */

/**
 * 给 [PageFabButton] 记一层取样层 —— 调用点把它挂到**FAB 要糊的那块内容**上（列表 / pager），
 * FAB 挂成它的**兄弟**（理由见 [PageFabButton]：FAB 落在页面那一层里面，不能拿那一层取样）。
 *
 *  与页面那一层同一个写法：先 `drawRect(surface)` 铺底色、再 `drawContent()` ——
 *  不铺的话透明区域会把颜色扩散进去、糊成色块（见 `SettingsPage` 里边那段的注释）。
 */
@Composable
internal fun rememberFabBackdrop(): LayerBackdrop {
    // 底色要在**外面**读：`rememberLayerBackdrop` 那个 lambda 不是 @Composable 上下文，
    //  里头读不了 `COUITheme.colorScheme`（页面那一层也是这么写的）。
    val surfaceColor = COUITheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 页面右下角那颗 FAB 的**本体** —— 磨砂 / 形状 / 底色 / 阴影全收在这里，[PageCornerFab] 的两个调用点共用
 *（各自写一份迟早走形：这几样必须两处一致）。
 *
 *  注意：**取样层必须是「不含这颗 FAB」的那一层** —— 这点与顶栏 / 浮层菜单 / 底部工具栏**都不一样**：
 *  那三处画在 `SettingsPage` 那个 `layerBackdrop`（[SettingsPage.pageBackdrop]）**外面**，所以能直接拿
 *  页面那一层取样；而 FAB 是**页面内容**的一员、落在那层**里面** —— 拿它取样就是自己取样自己：
 *  记录层为了画自己得再画 FAB、FAB 又去取样记录层……渲染节点链无限增长，
 *  `RenderNode::prepareTreeImpl` 一路递归到栈溢出、宿主 UI 进程当场被杀
 *（真机崩过一次：tombstone 里 500+ 层全是 `prepareTreeImpl`）。
 *  所以调用点**自己记一层**（[rememberFabBackdrop]）、挂在 FAB 要糊的内容上，FAB 只是它的兄弟：
 *  那一层里没有 FAB ⇒ 不成环。
 *
 *  注意：**阴影必须自己补，且补在磨砂外面**（与底部工具栏同一条教训，见 `CommandScreen.CommandActions`）：
 *  磨砂内部是 `placeWithLayer(clip = true, shape = 圆)`，而库 `Surface` 自己那层阴影挂在它**里面**
 *  （`graphicsLayer(shadowElevation = …, shape, clip = false)`）、阴影又画在形状**外** ⇒ 被裁得一干二净。
 *  所以这里把库的 `shadowElevation` 传 `0.dp`，按库同一手、同一个默认值
 *（[FloatingActionButtonDefaults.ShadowElevation]）在裁剪层**外面**补一层。
 *
 *  注意：底色走**主题原色**（`colorScheme.primary`），**不再**乘 `FROSTED_SURFACE_ALPHA`
 *（用户反馈「FAB 的透明度导致颜色和主题色有偏差」）：`primary` 两套色板都是**实色**，
 *  乘一档只是把它掺进底下的糊里、显得发白 —— 那正是「和主题色有偏差」。
 *  代价是实色底把糊完全盖住，磨砂在 FAB 上看不出来；挂载保留（想调回半透明只动下面那一行）。
 *
 * @param backdrop 调用点自己记的那一层（**不含这颗 FAB**，见上）
 * @param onClick 点击回调
 * @param modifier 调用点自己那点修饰（「按键绑定」页拿它套 `alpha` 做禁用观感）
 * @param content FAB 上的内容（图标）—— 内容色由调用点给（`onPrimary`）
 */
@Composable
internal fun PageFabButton(
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val circle = remember { CircleShape }
    val shadowPx = with(LocalDensity.current) {
        FloatingActionButtonDefaults.ShadowElevation.toPx()
    }
    val shell = Modifier.graphicsLayer(shadowElevation = shadowPx, shape = circle, clip = false)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.then(shell.frosted(backdrop, circle)),
        containerColor = COUITheme.colorScheme.primary,
        // 库自己那层阴影在磨砂的裁剪层里面、必被裁光（见上面那段注释），改由我们在外面补
        shadowElevation = 0.dp,
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
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = PAGE_MARGIN * 2, bottom = PAGE_BOTTOM_PADDING * 2),
        enter = slideInVertically(
            animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
        ) { it + slidePx } + fadeIn(tween(180, easing = LinearOutSlowInEasing)),
        exit = slideOutVertically(
            animationSpec = tween(VISIBILITY_EXIT_MS, easing = EASING_EMPHASIZED_ACCEL),
        ) { it + slidePx } + fadeOut(tween(140, easing = FastOutLinearInEasing)),
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
