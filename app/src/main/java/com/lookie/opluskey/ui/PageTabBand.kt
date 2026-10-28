package com.lookie.opluskey.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.lookie.opluskey.ui.model.DISABLED_ALPHA
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.state.BandHeight
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import kotlinx.coroutines.launch

// 顶栏底下那条**分页带子** —— 「按键绑定」（三个手势）、「组合键」编辑页（短按 / 长按）、
// 「自定义命令」（单条 / 多条）共用这一份。

/**
 * 一条**分段控件带**：挂在**顶栏的 `bottomContent`** 里（见 `PageHeader` 的 `bottomContent`
 * 与 `SettingsPage.PageBottomBand` 那处分派）。
 *
 * 三件事都在这一层定死，调用点只给「哪几档、可不可点、高度报给谁」：
 *  1. **亮哪一档由分页器说了算**（`currentPage`）：点它去翻页（`animateScrollToPage`），
 *     反过来标签的粗细与颜色跟着 `currentPage` 切 —— 内容才是真相。
 *  2. **滑块跟手**：按 `currentPage + currentPageOffsetFraction` 实时插值（见下面
 *     `selectedTabPosition` 那一段），滑块与页面同进同出。
 *  3. **禁用只能自己造**：库里 `TabRowWithContour` 没有 `enabled` 参数（`TabRowKt` 的签名里没有），
 *     于是整条套 `alpha` 变灰 + 上面压一层**透明的吃事件 Box**（`pointerInput` 里把每个 change 都
 *     consume 掉）。只把 `onTabSelected` 改成空实现是不够的 —— 那样控件看着还能点、点了却没反应，
 *     比灰着更让人困惑。
 *
 * 高度报给页面级那一份 [BandHeight]（每页一份）：内容顶上那个占位 Spacer 读它。带子与内容分处
 * 两棵树（带子挂在顶栏），只有这一条路能对齐。
 *
 * @param labels 每一档的标签 —— 条数必须等于页数
 * @param enabled 整条可不可点（与整页那个闸门同一档：单键页看那颗键的 `enabled`，命令页看排态…）
 * @param band 带子高度报到哪一份
 */
@Composable
internal fun SettingsPage.PageTabBand(
    pagerState: PagerState,
    labels: List<String>,
    enabled: Boolean,
    band: BandHeight,
) {
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val disabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_ALPHA,
        label = "PageTabBandDisabledAlpha",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { band.report(it.height) },
    ) {
        TabRowWithContour(
            tabs = labels,
            selectedTabIndex = pagerState.currentPage,
            //  滑块位置按**浮点页码**实时算，滑动时 1:1 跟着页面走。
            //
            //  `currentPage + currentPageOffsetFraction` 是连续量：拖到一半时前者已翻到下一页、
            //  后者恰好变成负数补回来（0.5 附近两段接得上），所以它不会跳。
            //
            //  滑块**不能**只喂 `selectedTabIndex` 让库自己弹过去 —— 库内部是阻尼比 1 的弹簧
            //  （stiffness≈439），起步慢、落点还带余量：手指已经滑过一格，滑块才走到一半，
            //  读起来就是「跟页面对不上拍」。这条路以前走过一次、当时撤掉了，原因就在这。
            //  这次给库加了浮点入参（`patches/coui/0007-fractional-tab-position.patch`）：
            //  浮点位置非空时库跳过弹簧、直接照它画滑块，才真正做到跟手。
            selectedTabPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            onTabSelected = {
                tick()
                scope.launch { pagerState.animateScrollToPage(it) }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 左右与卡片同一档 [PAGE_MARGIN]。纵向：**只带下边一档**（[PAGE_TOP_PADDING]）
                    // —— 上边那 8dp 去掉，带子直接贴着折叠栏下沿；
                    // 下边这一档就是「带子与内容之间」的间距，与没有带子的页（首页 / 更多）
                    // 由内容 Spacer 加的是**同一档**，各页一致（见 [PAGE_TOP_PADDING]）。
                    .padding(
                        start = PAGE_MARGIN,
                        end = PAGE_MARGIN,
                        bottom = PAGE_TOP_PADDING,
                    ).alpha(disabledAlpha),
        )
        if (!enabled) {
            // 透明吃事件层：不给任何视觉（灰是上面那层 alpha 做的），只负责拦住输入
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
