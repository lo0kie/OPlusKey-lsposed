package com.lookie.opluskey.ui.chrome

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
import androidx.compose.ui.platform.LocalFocusManager
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.page.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.theme.AppTheme
import com.lookie.opluskey.ui.model.theme.DISABLED_ALPHA
import com.lookie.opluskey.ui.model.theme.isDarkScheme
import com.lookie.opluskey.ui.state.signal.BandHeight
import com.lookie.opluskey.ui.style.LocalAppTheme
import io.github.suqi8.coui.kmp.basic.TabRowColors
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

// 顶栏底下那条**分段控件带子** —— 「按键绑定」（三个手势）、「组合键」编辑页（短按 / 长按）、
// 「自定义命令」（单条 / 多条）共用这一份；「条件执行」用它的**无分页器**那一档（见下）。

/**
 * 一条**分段控件带**：挂在**顶栏的 `bottomContent`** 里（见 `PageHeader` 的 `bottomContent`
 * 与 `SettingsPage.PageBottomBand` 那处分派）。
 *
 * 三件事都在这一层定死，调用点只给「哪几档、可不可点、高度报给谁」：
 *  1. **亮哪一档由分页器说了算**（`currentPage`）：点它去翻页（`animateScrollToPage`），
 *     反过来标签的粗细与颜色跟着 `currentPage` 切 —— 内容才是真相。
 *  2. **滑块跟手**：按 `currentPage + currentPageOffsetFraction` 实时插值（见下面那段），
 *     滑块与页面同进同出。
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
    val focusManager = LocalFocusManager.current
    //  页码必须夹在标签表里：场景那一档的页数会随「组合键 / 单键」变（三档与两档），
    //  页数缩小时 `currentPage` 会先停在旧值上 —— 不夹就会画出轨道外的滑块，库按下标取
    //  tab 时还会越界。`BindingPagerScreen` 对同一现象已经夹过一道。
    val lastIndex = (labels.size - 1).coerceAtLeast(0)
    TabBandSurface(
        look = BandLook(labels, enabled, band),
        selectedIndex = pagerState.currentPage.coerceIn(0, lastIndex),
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
        selectedPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
    ) {
        tick()
        //  点这一下就**摘焦点**：输入框失焦后不再为光标发 `bringIntoView`，
        //  否则分页器（也是滚动容器）会把刚翻过去的那一页又拽回焦点所在那一页
        //  —— 真机口径「输入框有值时切 tab 点也回弹、滑也回弹」。
        //  放在起动画之前：让键盘先收，别在动画半途改布局。
        focusManager.clearFocus()
        scope.launch { pagerState.animateScrollToPage(it) }
    }
}

/**
 * 同 [PageTabBand]，但**没有分页器**：它只换「现在看哪一档」，内容不跟着横滑。
 *
 *  「条件执行」页用它 —— 那一页一屏只有一张表，换手势是把**草稿整份换成另一档**（不是翻页，
 *  也没有可以横滑的第二屏），所以带子只需要「亮哪一档 + 点哪一档」，滑块直接照着 [selected] 画。
 *  换档的后果由调用方定（那里要拦「还没保存」那种情况）。
 */
@Composable
internal fun SettingsPage.PageTabBand(
    selected: Int,
    labels: List<String>,
    enabled: Boolean,
    band: BandHeight,
    onSelect: (Int) -> Unit,
) {
    val tick = rememberTick()
    val focusManager = LocalFocusManager.current
    TabBandSurface(
        look = BandLook(labels, enabled, band),
        selectedIndex = selected,
        //  没有可拖的中间态，浮点位置恒等于整档 —— 库走「照它画」那条，不做弹簧
        selectedPosition = selected.toFloat(),
    ) {
        tick()
        //  与上面那一份同一个理由：换档前先摘焦点，别让输入框把内容拽回去
        focusManager.clearFocus()
        onSelect(it)
    }
}

/**
 * 一条带子的「长什么样 + 高度报给谁」—— 这三样只在**建带子的那一处**定，滑动时不变。
 *
 *  与「亮哪一档 / 滑块画在哪」分开：后者每帧都变，前者整趟就一份。
 */
private class BandLook(
    val labels: List<String>,
    val enabled: Boolean,
    val band: BandHeight,
)

/**
 * 两个入口共用的那一条 —— 只有「亮哪一档 / 滑块画在哪 / 点了干什么」不同，外观与禁用态一份。
 *
 * @param selectedIndex 标签的粗细与配色跟着它
 * @param selectedPosition 滑块的浮点位置（分页器那条会落在两档之间）
 */
@Composable
private fun TabBandSurface(
    /** 「这一条带子长什么样、高度报给谁」—— 与下面两位（随手指变）分开，见 [BandLook]。 */
    look: BandLook,
    selectedIndex: Int,
    selectedPosition: Float,
    onSelect: (Int) -> Unit,
) {
    val labels = look.labels
    val disabledAlpha by animateFloatAsState(
        targetValue = if (look.enabled) 1f else DISABLED_ALPHA,
        label = "PageTabBandDisabledAlpha",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { look.band.report(it.height) },
    ) {
        TabRowWithContour(
            tabs = labels,
            selectedTabIndex = selectedIndex,
            selectedTabPosition = selectedPosition,
            onTabSelected = onSelect,
            colors = tabBandColors(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 左右取**卡片那一档**（[PAGE_MARGIN]）：带子里的控件、下面那些卡片、顶栏
                    //  两侧的按钮与大标题现在都在同一条竖线上（顶栏那三个 padding 也是这一档，
                    //  见 `PageHeader`）。
                    // 纵向：**只带下边一档**（[PAGE_TOP_PADDING]）
                    // —— 上边那 8dp 去掉，带子直接贴着折叠栏下沿；
                    // 下边这一档就是「带子与内容之间」的间距，与没有带子的页（首页 / 更多）
                    // 由内容 Spacer 加的是**同一档**，各页一致（见 [PAGE_TOP_PADDING]）。
                    .padding(
                        start = PAGE_MARGIN,
                        end = PAGE_MARGIN,
                        bottom = PAGE_TOP_PADDING,
                    ).alpha(disabledAlpha),
        )
        if (!look.enabled) {
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

/**
 * 这条带子的配色 —— 其余主题档交给库的默认值，**Monet 那一档自己给**。
 *
 *  库的默认是两块固定灰（轨道 `0x0F000000` / 滑块纯白，深色下 `0x1FFFFFFF` / `0x1AFFFFFF`）：
 *  整页底色被壁纸染过之后，这条带子是页面上唯一一块「没跟上配色」的灰（与格子小字、浮层面板
 *  同一类问题）。
 *
 *  取法沿用 `popupSurfaceColor` 那条判据 —— **深色必须抬一档**才分得开：
 *  · 浅色：轨道 `surfaceContainerHighest`（比页面底色低一档）、滑块 `surface`（页面底色本身，
 *    读作「从轨道里抠出来的一格」）；
 *  · 深色：轨道 `surfaceContainer`、滑块 `surfaceContainerHighest` —— 深色下明暗差是唯一的分层
 *    线索，滑块必须比轨道**亮**（浅色是反过来的：越亮越靠上）。
 *
 *  文字两档都保持库默认的 `onSurface`：轨道与滑块都在 surface 那一族里，中性字在两者上都读得清，
 *  换成强调色反而会和「选中」这件事抢注意力。
 */
@Composable
private fun tabBandColors(): TabRowColors {
    val scheme = COUITheme.colorScheme
    if (LocalAppTheme.current != AppTheme.MONET) return TabRowDefaults.tabRowColors()
    return if (scheme.isDarkScheme) {
        TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainer,
            selectedBackgroundColor = scheme.surfaceContainerHighest,
        )
    } else {
        TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainerHighest,
            selectedBackgroundColor = scheme.surface,
        )
    }
}
