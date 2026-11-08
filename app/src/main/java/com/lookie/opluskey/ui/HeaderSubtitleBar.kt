package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

// 顶栏副标题的**宽度账本** —— 「这一行能放多少字、放不下怎么截」全在这里，`PageHeader` 只负责
// 把三个宽度量出来交给它。单独一个文件是因为 `PageHeaderWidget.kt` 已经到了 detekt 的
// `TooManyFunctions` 上限，而这几件事确实是一件事（副标题的宽度），按「同一件事的另一半」切，
// 不按字母切。

/**
 * 顶栏副标题的宽度账本 —— 展开态与折叠态**分开算**，右侧按钮几颗也进账。
 *
 * ## 为什么要分两档
 *
 *  同一句话在两档占的是两块不一样大的地方：
 *  · **展开态**：副标题独占标题下面那一行，左右各让一个 [PAGE_MARGIN] 就完事。右侧那排圆键
 *    这一刻坐在栏顶那一行（垂直居中于 52dp），与副标题那一行不重叠 ⇒ 不用为它们让位。
 *  · **折叠态**：副标题被滑进 52dp 那条栏里，左边贴着「返回」那一格（库的 `collapsedTitleX`），
 *    右边顶着动作键那一组（库的 `collapsedTitleEnd`）。圆键是垂直居中的，副标题占的是栏底那一行
 *    —— 两者在竖直方向重叠十几 dp，字一长就压到圆键底下。
 *
 *  所以「右侧有没有第二颗按钮」只在折叠态才影响可用宽度，而它一旦变窄，画出去的字也必须跟着变少。
 *
 * ## 为什么是「换一次文案」而不是逐帧算
 *
 *  库把折叠做在**布局期**（读 `heightOffset` 算位置），所以逐帧跟着算宽度就等于每帧重组整条顶栏。
 *  这里用一个 `derivedStateOf` 只观察「折到底了没有」这一个布尔值：它在 `collapsedFraction`
 *  翻过 [SUBTITLE_SWAP_AT_COLLAPSED] 那一刻才通知一次，两档之间不来回抖。
 *
 * ## 宽度从哪来
 *
 *  三个 `MutableIntState` 由 `PageHeader` 在布局里量到后写入：整条栏、左边「返回」那一格、
 *  右边动作那一组。首帧还没量到时按「窗口宽 + 两个 0」算 —— 那时页面一定是展开的，
 *  折叠态那一档用不上，展开态则与最终值相等（竖屏下栏宽就是屏宽）。
 *
 * @param density 换算用（`sp` / `dp` → px）
 * @param scrollBehavior 这一页顶栏的滚动状态（读折叠进度）
 * @param screenWidthPx 首帧的栏宽估计值（见上面「宽度从哪来」）
 * @param columnWidthPx 一格多宽（= `body2` 字号的一半），由 [rememberHeaderSubtitleBar] 按主题算
 */
internal class HeaderSubtitleBar(
    private val density: Density,
    private val scrollBehavior: ScrollBehavior,
    screenWidthPx: Int,
    private val columnWidthPx: Float,
) {
    /** 整条栏的实测宽度（px）—— 两档上限都从它出发。 */
    val barWidthPx = mutableIntStateOf(screenWidthPx)

    /** 左边「返回」那一格的外框宽度（px，含末端给折叠标题让位的 8dp）。 */
    val navigationWidthPx = mutableIntStateOf(0)

    /** 右边动作那一组的实测宽度（px）—— 多一颗按钮就少一格字。 */
    val actionsWidthPx = mutableIntStateOf(0)

    /** 折到底了没有（与库换折叠标题同一档，见 [SUBTITLE_SWAP_AT_COLLAPSED]）。 */
    private val collapsed =
        derivedStateOf { scrollBehavior.state.collapsedFraction >= SUBTITLE_SWAP_AT_COLLAPSED }

    /**
     * 这一页此刻**该画出去**的那一行副标题（只改画出来的那一份，日志与配置仍是全文）。
     *
     *  量不到宽度时（首帧、或整页宽度为 0）退回天花板 [BELOW_SUBTITLE_MAX_COLUMNS] ——
     *  宁可长一点，也别画成只剩一个省略号。
     */
    fun shown(subtitle: String): String {
        if (subtitle.isEmpty()) return subtitle
        val measured =
            if (collapsed.value) {
                collapsedSubtitleColumns(
                    barWidthPx = barWidthPx.value,
                    navigationWidthPx = navigationWidthPx.value,
                    actionsWidthPx = actionsWidthPx.value,
                    columnWidthPx = columnWidthPx,
                    density = density,
                )
            } else {
                expandedSubtitleColumns(barWidthPx.value, columnWidthPx, density)
            }
        val columns = if (measured > 0) minOf(measured, BELOW_SUBTITLE_MAX_COLUMNS) else BELOW_SUBTITLE_MAX_COLUMNS
        return ellipsizeToColumns(subtitle, columns)
    }
}

/**
 * 拿这一页的 [HeaderSubtitleBar]。
 *
 *  一格 = 半个全角字 = `body2` 字号的一半（14sp → 7dp）。取主题里那一档而不是写死 14sp，
 *  按 `sp.toPx()` 换算，所以系统字体放大时格数自动跟着变少。
 */
@Composable
internal fun rememberHeaderSubtitleBar(scrollBehavior: ScrollBehavior): HeaderSubtitleBar {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val columnWidthPx = with(density) { (COUITheme.textStyles.body2.fontSize / 2).toPx() }
    return remember(scrollBehavior, columnWidthPx) {
        HeaderSubtitleBar(
            density = density,
            scrollBehavior = scrollBehavior,
            screenWidthPx = with(density) { screenWidthDp.roundToPx() },
            columnWidthPx = columnWidthPx,
        )
    }
}

/**
 * 副标题的**格数天花板** —— 32 格 = 16 个汉字。
 *
 *  真正的上限是「这一档能放多少」（[expandedSubtitleColumns] / [collapsedSubtitleColumns]），
 *  这一档只是**封顶**：它独占标题下面那一行，本来就够宽，但铺满整屏时读起来像正文；
 *  留出两端空白，一眼还是「这一页的一句说明」。口径按**格**算（见 `displayWidth`），
 *  所以英文 / 数字不会被误砍。
 */
private const val BELOW_SUBTITLE_MAX_COLUMNS = 32

/**
 * 省略号自己占的格数 —— 从预算里先扣掉。
 *
 *  `ellipsizeToColumns` 是「砍到 N 格，**再**补一个省略号」，省略号不在它的预算里；
 *  不先扣，截断后的那一行就比算出来的宽度多出两格，折叠态下正好顶到圆键边上。
 *  按 2 格算（`…` 在中文字体里通常是全角，`isFullWidth` 那套粗判不认识它）。
 */
private const val SUBTITLE_ELLIPSIS_COLUMNS = 2

/**
 * 折叠到**几分之几**才换成窄的那一版副标题。
 *
 *  与库换折叠标题那一档对齐（`CollapsedTitleSwapFraction`）：两边同一次「落定」，
 *  看起来像顶栏整体收好了，而不是文字自己先跳一下。中间过程不换 —— 那一段副标题还在
 *  往上滑，换文案会看成闪一下。
 */
private const val SUBTITLE_SWAP_AT_COLLAPSED = 0.9999f

/**
 * 这段宽度放得下多少**格**字 —— 副标题两档上限共用的那把尺子。
 *
 *  [columnWidthPx] 是「一格多宽」，由调用点按主题字号算好传进来（这里不读主题：
 *  `COUITheme.textStyles` 是 @Composable 的，读在这儿会让下面几个纯函数跟着变 @Composable）。
 *  算不出来（还没量到宽度）时给 0，由调用点退回天花板。
 */
private fun columnsForWidth(
    widthPx: Int,
    columnWidthPx: Float,
): Int {
    if (widthPx <= 0 || columnWidthPx <= 0f) return 0
    return (widthPx / columnWidthPx).toInt() - SUBTITLE_ELLIPSIS_COLUMNS
}

/**
 * **展开态**的副标题格数 —— 标题下面那一行是它自己的，左右各让一个 [PAGE_MARGIN] 就完事。
 *
 *  右侧那排圆键在展开态坐在栏顶那一行（垂直居中于 52dp），与副标题那一行不重叠，
 *  所以**不用**为它们让位：这一档的宽度只跟屏宽有关。
 */
private fun expandedSubtitleColumns(
    barWidthPx: Int,
    columnWidthPx: Float,
    density: Density,
): Int = columnsForWidth(barWidthPx - 2 * with(density) { PAGE_MARGIN.roundToPx() }, columnWidthPx)

/**
 * **折叠态**的副标题格数 —— 这一档窄得多，两头都要让位。
 *
 *  折叠后副标题被滑进 52dp 那条栏里，左边贴着「返回」那一格（库的 `collapsedTitleX`），
 *  右边顶着动作键那一组（库的 `collapsedTitleEnd`）；圆键是垂直居中的，副标题占的是
 *  栏底那一行 —— 两者在竖直方向重叠十几 dp，字一长就压到圆键底下（这就是要分开判的原因）。
 *
 *  让位量与库自己那套算法**逐项对齐**（`TopAppBarLayout` 的 measure 里那几行）：
 *  `maxWidth − actionIconPadding − actions − ActionIconGap` 减
 *  `navigationIconPadding + nav + NavigationIconGap`。
 *  其中两个 Gap 直接取库的常量，不抄数值 —— 抄了以后库一调就错位。
 *  这样「右侧有几颗按钮」自然进了账：多一颗就是多一个 `HEADER_ACTION_GAP` + 一个圆键。
 */
private fun collapsedSubtitleColumns(
    barWidthPx: Int,
    navigationWidthPx: Int,
    actionsWidthPx: Int,
    columnWidthPx: Float,
    density: Density,
): Int {
    val marginPx = with(density) { PAGE_MARGIN.roundToPx() }
    val navigationGapPx = with(density) { TopAppBarDefaults.NavigationIconGap.roundToPx() }
    val actionGapPx = with(density) { TopAppBarDefaults.ActionIconGap.roundToPx() }
    val available =
        barWidthPx - marginPx - navigationWidthPx - navigationGapPx -
            marginPx - actionsWidthPx - actionGapPx
    return columnsForWidth(available, columnWidthPx)
}
