package com.lookie.opluskey.ui.chrome

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.page.opticalTightTop
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.theme.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.state.signal.BandHeight
import com.lookie.opluskey.ui.style.ellipsizeToColumns
import com.lookie.opluskey.ui.style.frosted
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.utils.TiltFeedback
import io.github.suqi8.coui.kmp.utils.pressable

// 顶栏那一整套：**骨架**（高度通道、折叠几何、那条 `TopAppBar` 本身）、右侧那几颗圆键与
// 「按一下振一下」、以及副标题按可用宽度截断的两档账本。
//
//  三块各管一件事：骨架管「栏怎么摆、多高、怎么折」，圆键管「栏上能点哪几颗」，副标题管
//  「这一行字怎么截」。

/**
 * 「本页顶栏实测高度」的通道 —— **每页一份**，由 `PageEntry` 提供。
 *
 *  不能做成页面级单值（`SettingsPage` 上的字段）：转场期间相邻两屏同时在组合里，
 *  共用一个值会互相改对方的占位 —— 退页时读到上一页还没归零的高度，内容就缩到顶栏底下
 *  （真机实证）。固定带那条通道（`BandHeight` 的类注释）早就写明了这条纪律。
 */
internal val LocalHeaderBoxHeight = androidx.compose.runtime.staticCompositionLocalOf<BandHeight?> { null }

/**
 * 本页的顶栏高度通道 —— **没提供就是编程错误**（页面不挂在 `PageEntry` 里）。
 *
 *  以前默认给一份空实例：漏 provide 时静默读到 0，内容按兜底常量排、整体偏上一截，
 *  很难看出是“通道没接上”。debug 包（`STRICT_UI`）直接抛，release 兜底但留一行日志。
 */
@Composable
internal fun currentHeaderBand(): BandHeight =
    LocalHeaderBoxHeight.current ?: run {
        if (com.lookie.opluskey.core.STRICT_UI) {
            error("顶栏高度通道没提供：PageHeader 只能挂在 PageEntry 里（见 LocalHeaderBoxHeight）")
        }
        XLog.w("顶栏高度通道没提供：按兜底常量排版（这一页没挂在 PageEntry 里？）")
        BandHeight()
    }

@Composable
internal fun SettingsPage.headerStatusBarPadding(): Dp = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

/**
 * 顶栏的**展开高度**（含系统栏顶边）—— 内容从哪儿开始就是按它算的。
 *
 *  展开高不硬编码 107dp：`PageHeader` 在布局期把自己量到的高度上报给 [BandHeight]，这里直接用
 *  那份实测值；还没量到（首帧 / 隐藏页）时退回库的 `ExpandedHeight` 加系统栏顶边。
 */
@Composable
internal fun SettingsPage.currentHeaderHeight(
    @Suppress("UNUSED_PARAMETER") scrollBehavior: ScrollBehavior,
): Dp {
    // 只给**展开态**高度 —— 它是「内容从哪儿开始」的那个常量，与折叠进度无关。
    //
    //  这里**不能**加减折叠位移（`heightOffset`）：加上它以后，顶栏每折一点、顶部占位就矮一点，
    //  内容跟着一起上移 —— 表现就是「内容区和 header 一起滚」，而不是先折完再滚；
    //  从子页退回时 `heightOffset` 还留着子页的值，占位再矮一截，内容直接缩到顶栏底下。
    //  折叠本身由顶栏自己处理（整体上移 + 磨砂盖住列表），内容不参与排版。
    val measured = currentHeaderBand().dp()
    return if (measured > 0.dp) measured else TopAppBarDefaults.ExpandedHeight + headerStatusBarPadding()
}

/**
 * 把顶栏一次性折到底 —— 程序性跳转（`scrollToItem`）不走嵌套滚动，顶栏不会自己折。
 *
 *  只在 `heightOffsetLimit` 是有限负值时写。库在测量前给的是 `-Float.MAX_VALUE`（也满足这个
 *  条件），所以调用点必须在测量完成之后再调；把那个哨兵写进 `heightOffset` 会让整页的
 *  「当前顶栏高度」失真 —— 表现出来就是「定位跳完，目标行被压在展开的顶栏底下」。
 */
internal fun SettingsPage.collapseHeader(scrollBehavior: ScrollBehavior) {
    val limit = scrollBehavior.state.heightOffsetLimit
    // 同 [currentHeaderHeight]：首帧那个 `0` 不是「量到了」，写它等于没折。
    if (limit.isFinite() && limit < 0f) {
        scrollBehavior.state.heightOffset = limit
    }
}

/**
 * 顶栏折叠到底时的高度 —— [currentHeaderHeight] 的同一笔账，折叠位移取满。
 *
 * 系统栏顶边那一档不能漏，漏了目标行会被压在顶栏底下只露一半。
 */
@Composable
internal fun SettingsPage.collapsedHeaderHeight(scrollBehavior: ScrollBehavior): Dp {
    // 与 `currentHeaderHeight` 同一把尺子：展开高是**量**出来的，折叠量取库给的 `heightOffsetLimit`
    //  （负值，绝对值就是折叠掉多少）。不拿库的 `CollapsedHeight` 常量对账 —— 它与实测的展开高
    //  差十几 dp，定位时目标行会跟着偏那么多。
    val expanded = currentHeaderBand().dp()
    val limit = scrollBehavior.state.heightOffsetLimit
    val folded =
        with(LocalDensity.current) {
            if (limit.isFinite() && limit < 0f) (-limit).toDp() else 0.dp
        }
    return if (expanded > 0.dp) {
        (expanded - folded).coerceAtLeast(0.dp)
    } else {
        TopAppBarDefaults.CollapsedHeight + headerStatusBarPadding()
    }
}

/**
 * 本页顶栏 —— 标题 / 副标题 / 右上角动作 / 底下那条固定带（内容按页在 `PageTopBar` 里分派）。
 *
 *  **副标题一律排在标题下面、自己占一行**（库的 `subtitle` 槽位）：它装的是键名 / 应用名 /
 *  命令名，长度不受控，挤在大标题同一行只会把大标题压成省略号。宽度在这一层夹，调用点不该
 *  各自截一遍 —— 夹多少**按这一页当刻的可用宽度算**，展开 / 折叠两档分开判，账本在
 *  `HeaderSubtitleBar`。
 *
 *  `subtitleContent` 给**要画圆角框**的那几页（装键名的：按键绑定 / 按键设置 / 场景动作 /
 *  组合键编辑）：框按名字宽度自己排，那本「按格数截断」的账套不上，所以与 `subtitle` 二选一。
 *
 *  `subtitleColor` 是标题下面那行的颜色，默认就是库那一档次要色。留成参数是为了
 *  「这一页整体按不动」时把它也压暗一档：内容区整片变灰了，这一行还按满亮度亮着，读起来像
 *  那一区仍然是活的（见 `PageTopBar` 里停用那支）。
 *
 *  注：参数**不逐条写 KDoc** —— 要解释的写在这里。
 *
 */
@Composable
internal fun SettingsPage.PageHeader(
    title: String,
    subtitle: String = "",
    subtitleColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
    subtitleContent: (@Composable () -> Unit)? = null,
    scrollBehavior: ScrollBehavior,
    state: HazeState,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    //  两侧按钮的「按一下振一下」在这一层挂好（见 [headerPressTick]）—— 这一页所有顶栏按钮
    //  都走它，调用点不必各自 rememberTick。
    val tick = rememberTick()
    // 顶栏里那条固定带的高度（没有带子时为 0）：上报顶栏高度时扣掉它。
    val bandHeightPx = remember { mutableIntStateOf(0) }
    val headerBand = currentHeaderBand()
    //  副标题的**最大宽度**在这一层夹，调用点只管传全文（键名 / 应用名 / 命令名都会很长）：
    //  不夹的话会铺满整行、读起来像正文。只改**画出来的那一份** —— 日志与配置仍是原文。
    //  宽度**不是**一个常数：展开态与折叠态各算一档，右侧按钮几颗也进账（口径见
    //  `HeaderSubtitleBar`）。这里只负责把三个宽度量出来写进账本。
    val subtitleBar = rememberHeaderSubtitleBar(scrollBehavior)
    // 磨砂规格（含模糊半径）统一由 [frosted] 挂，见 [HAZE_FROSTED_STYLE]。
    TopAppBar(
        title = title,
        //  只走库的 `subtitle` 槽位（标题**下面**、自己占一行，见补丁 `0001`）——
        //  同一行那个 `trailingSubtitle` 槽位不再用：它贴在大标题右边，长一点的键名 / 命令
        //  就把大标题压成省略号，而这一行装的全是不受控的名字（口径见本函数的 KDoc）。
        //
        //  两档二选一：给了 [subtitleContent]（要画圆角框的那几页）就不再画文字 ——
        //  框自己会按名字宽度排，`subtitleBar` 那套「按格数截断」是给纯文字算的，套不到框上。
        subtitle = if (subtitleContent == null) subtitleBar.shown(subtitle) else "",
        subtitleContent = subtitleContent,
        //  副标题这一行紧挨着主标题，可两个行盒各有一截汉字不占的留白（上面那行的 descent、
        //  这一行的 ascent），3.5dp 的设计间隙看着像 11.7dp —— 副标题像被推远了一截。
        //  上提「行盒顶 → 墨迹顶」那一档（量出来的，见 `opticalTightTop`）：真机上量到 5px
        //  @560dpi ≈ 1.4dp，就是这一档 —— 再往上补 3dp 会贴得太紧（试过，被否掉）。
        //  `offset` 只挪墨迹、不动测量，所以栏高、折叠几何、上面那本宽度账本都不受影响。
        subtitleModifier = Modifier.opticalTightTop(COUITheme.textStyles.body2),
        subtitleColor = subtitleColor,
        hideSubtitleOnCollapse = false,
        //  三个水平边距统一到**卡片那一档**（[PAGE_MARGIN]）：整页所有卡片的外边距、固定带里
        //  的分段控件、选择应用页的搜索框都是这一档，顶栏两侧的圆键与大标题跟着走同一条竖线
        //  （原来用库默认的 `TitlePadding` / `NavigationIconPadding` / `ActionIconPadding`
        //  三处 16dp，比卡片多 4dp，左边一片看着「差一点点」）。
        //  `navigationIcon` 那一格内部还额外垫了 8dp（见 `HeaderBackIcon`）—— 那是给折叠标题
        //  让位，不算外边距，别把它折算进这一档。
        titlePadding = PAGE_MARGIN,
        navigationIconPadding = PAGE_MARGIN,
        actionIconPadding = PAGE_MARGIN,
        scrollBehavior = scrollBehavior,
        // 按比例缩这一档，不整个换 alpha（口径见 FROSTED_SURFACE_ALPHA）
        color =
            COUITheme.colorScheme.surface.copy(
                alpha = COUITheme.colorScheme.surface.alpha * FROSTED_SURFACE_ALPHA,
            ),
        modifier =
            Modifier
                .frosted(state, RectangleShape)
                // 顶栏自己报高度（含系统栏顶边、**不含下面那条固定带**）——
                //  页内容顶上那个占位 Spacer 读它，见 `currentHeaderHeight`。
                //  带子高度各页已各自读过一遍，这里必须扣掉，否则有带子的页面让两次。
                .onSizeChanged {
                    headerBand.report(it.height - bandHeightPx.value)
                    //  副标题两档上限都按这条栏的宽度算（见 `HeaderSubtitleBar`）
                    subtitleBar.barWidthPx.value = it.width
                },
        navigationIcon = { HeaderBackIcon(tick) { subtitleBar.navigationWidthPx.value = it } },
        actions = {
            //  右侧那一组同样只挂振动：`Row` 保持「一个挨一个靠右」，与库那排的排法一致。
            //  顺带量宽度：**多一颗按钮就少一格字**，折叠态的副标题得让开它（见
            //  `collapsedSubtitleColumns`）。
            Row(
                modifier =
                    Modifier
                        .headerPressTick(tick)
                        .onSizeChanged { subtitleBar.actionsWidthPx.value = it.width },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                content = actions,
            )
        },
        bottomContent = {
            Box(Modifier.onSizeChanged { bandHeightPx.value = it.height }) { bottomContent() }
        },
    )
}

/**
 * 这一页的顶栏量到高度了没有 —— 没量到就先别画内容（底色与页面同值，看不出来）。
 *
 *  首帧的组合早于布局，顶栏高度那一刻还不存在；先按兜底值把内容排上、下一帧再落回，
 *  就是用户看到的那一次「闪」。150ms 兜底：极少数情况下顶栏尺寸为 0（隐藏页），不能一直不画。
 */
@Composable
internal fun headerReady(headerBand: BandHeight): Boolean {
    val measured = headerBand.dp()
    var forced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        forced = true
    }
    return measured > 0.dp || forced
}

// 页面顶栏那行副标题（`PageHeaderWidget` 的一支）—— 三个宽度量出来交给它。

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
     *  **能放多少就画多少**：上限就是那一档实测出来的可用宽度，不再另压一道格数天花板
     *  （两端留白由 [PAGE_MARGIN] 与折叠态那套让位算法给）。
     *  只有量不到宽度时（主题字号为 0 / 栏宽为 0）才退回 [FALLBACK_SUBTITLE_COLUMNS] ——
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
        val columns = if (measured > 0) measured else FALLBACK_SUBTITLE_COLUMNS
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
 * 量不到宽度时的**兜底格数** —— 32 格 = 16 个汉字。
 *
 *  正常路径走不到它：整条栏的宽度首帧就有（[HeaderSubtitleBar.barWidthPx] 的初值就是窗口宽），
 *  展开态第一帧就能算出真实格数。只有主题字号为 0 / 栏宽为 0 那种病态情形才退回这里 ——
 *  宁可长一点，也别画成只剩一个省略号。
 *
 *  早先这一档还是**天花板**（`minOf(measured, 32)`）：在 1272px / 560dpi 上实测只用到可用宽度的
 *  七成（46 格可用、画 32 格），长包名 / 长命令名右边明明还有空却被截了。两端留白本来就由
 *  [PAGE_MARGIN] 给，不需要再压一道。
 */
private const val FALLBACK_SUBTITLE_COLUMNS = 32

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
 *  算不出来（还没量到宽度）时给 0，由调用点退回 [FALLBACK_SUBTITLE_COLUMNS]。
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

// 顶栏右侧那几颗按钮（返回 / 刷新 / 菜单）与它们共用的「按一下振一下」。

/**
 * 顶栏两侧按钮共用的「按一下振一下」—— 挂在两个槽位的**容器**上，一次管住这一页所有顶栏按钮。
 *
 *  为什么不是逐个按钮加：顶栏按钮有六七处（返回 / ⋮ / 添加 / 新建 / 重置 / 设置），
 *  漏一处就是「这一颗按下去没手感」，而它们长得一模一样，用户分不出哪颗有哪颗没有；
 *  挂在容器上之后，**新加一颗顶栏按钮自动带振动**，调用点只写自己的 onClick。
 *
 *  不消费事件、不改排版：只在按下那一刻振一下，点击照旧由里面的按钮自己处理
 *  （`Main` 那一趟里子节点先拿到事件，这里既不会抢走点击、也不会重复触发）。
 *  代价是**置灰的按钮也会振一下** —— 那颗按钮此刻确实被按到了，只是不做任何事。
 */
internal fun Modifier.headerPressTick(tick: () -> Unit): Modifier =
    pointerInput(tick) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                tick()
                // 等这一下收场再收下一按下：拖动 / 取消都不额外振
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })
            }
        }
    }

/**
 * 顶栏圆键**描边**的不透明度系数 —— 乘在 `dividerLine` 自己那一档 alpha 上。
 *
 *  0.3：压在磨砂顶栏上只勾出「这儿有个圆」，不至于读成一枚端着的按钮（用户口径「边框再降
 *  亮度」）。要再淡 / 再亮都只动这一个数，别去换颜色角色 —— 换角色会把 Monet 那一档的
 *  `outlineVariant` 也一起换掉。
 */
private const val HEADER_BORDER_ALPHA = 0.3f

/**
 * 顶栏两颗圆键之间的间距 —— 两个圆不贴在一起（首页那排、选择应用页那两颗、按键页编辑态那两颗
 * 共用这一档；各页各写一个数迟早会差出来）。挂在**左边那颗**后面。
 */
internal val HEADER_ACTION_GAP = 8.dp

/**
 * 顶栏上的一颗圆键 —— 返回 / ⋮ / 添加 / 新建 / 重置 / 设置全走这一份。
 *
 *  顶栏底下是**磨砂**（半透的页面底色 + 模糊），纯透明的圆键压在上面只剩一个字形，看不出
 *  「这里按得动」；所以垫一层**纯色圆底 + 一圈细描边**。
 *
 *  ## 配色只认角色，不判主题
 *
 *  底色 `surfaceContainer`（不是 `surfaceContainerHigh`：高一档压在磨砂顶栏上偏亮，深色下尤其
 *  刺眼），描边 `dividerLine`（分隔线那一档，比 `outline` 弱一级 —— 顶栏上要的只是「这里有一块
 *  可按的地方」，不是一枚端着的按钮）。这两个角色在 Monet 那一档就是 MD3 的
 *  `surfaceContainer` / `outlineVariant`（跟着壁纸走），其余主题档是 COUI 标定过的那两档 ——
 *  所以这里**不需要**按 `LocalAppTheme` 分支，换主题时颜色自己跟着换。
 *
 *  禁用时退到 `disabledSecondaryVariant`（被冲淡的那一档容器色）：灰着的键不该还亮着
 *  （顶栏的「重置」有这一档，见 `CommandHeaderReset`）。
 *
 *  ## 禁用样式是**这一层的能力**（圆底 + 描边 + 字形三件）
 *
 *  这个圆是本项目自己画的（库的 `IconButton` 那层底色置透明），所以「灰」也得自己画全：
 *  只灰底与描边、字形还亮着，看着像「这颗还能按，只是底色淡了」。
 *  而字形是调用点传进来的（`content` 里那颗 `Icon`），默认 tint 取库的 `LocalContentColor`
 *  —— 于是这里把内容色按 `enabled` 两档递下去，**调用点仍然只写 `Icon(图标)`**，
 *  不必逐个记着判启用状态、也不必各自挑一个灰。
 *
 *  能用那一档取**环境里当前那一刻的值**（不是写死某个色）：顶栏上这几颗原本是什么色就是什么色，
 *  这一层只负责在停用时把它换成 [COUITheme.colorScheme] 的 `disabledOnSecondaryVariant`
 *  —— 与行首图标禁用时同一档（见 `rememberRowIconColor`），全项目一处口径。
 *
 * @param enabled 与按钮自己的可用性同一判据 —— 圆底 / 描边 / 字形三样都跟着它走；
 *   点不点得动由 `onClick` 那边定
 * @param content 图标本体（`Icon` 自带 `contentDescription`，不在这一层传）
 */
@Composable
internal fun HeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    //  圆底与描边随 `enabled` 两档（顶栏的「重置」那颗会在编辑途中变灰 / 变亮），带过渡 ——
    //  页里其它禁用来回都走同一条曲线（[COLOR_TRANSITION_SPEC]），顶栏这一颗不能「啪」一下。
    val fill by animateColorAsState(
        targetValue = if (enabled) scheme.surfaceContainer else scheme.disabledSecondaryVariant,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HeaderButtonFill",
    )
    //  描边：整档 `dividerLine` 压在磨砂顶栏上仍会勾出一圈清楚的轮廓，再压到三成一档就只剩
    //  「圆有个边界」（用户口径「边框再淡」）。按当前档的 alpha 折算，Monet 与 COUI 那两套
    //  都跟着淡下去；嫌淡 / 嫌亮都只改 [HEADER_BORDER_ALPHA] 这一个数。
    val border by animateColorAsState(
        targetValue =
            if (enabled) {
                scheme.dividerLine.copy(alpha = scheme.dividerLine.alpha * HEADER_BORDER_ALPHA)
            } else {
                scheme.disabledSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HeaderButtonBorder",
    )
    //  按下去跟着触点轻微倾斜 —— 与首页那张「模块状态」卡同一档手感（库的 `TiltFeedback`）：
    //  卡片那边是 `pressFeedbackType = Tilt` 挂的，圆键这颗库没给这个参数，所以自己挂一层。
    //  `pressable` 只做「按下 / 抬起」与那层指示，**不带 onClick、也不消费事件** ——
    //  点击仍旧交给里面那颗库按钮（含它那条「禁用时给提示」的路）。
    //  `delay = null` 与卡片一致：按下即倾，不等那一档长按判定。
    val tiltSource = remember { MutableInteractionSource() }
    //  字形颜色（第三件禁用样式）—— 见上面那段「禁用样式是这一层的能力」。
    //  能用的那一档读**环境值**：这几颗圆键原本吃的是页面给的内容色，写死会改变现有观感。
    val ambientIcon = LocalContentColor.current
    val iconColor by animateColorAsState(
        targetValue = if (enabled) ambientIcon else scheme.disabledOnSecondaryVariant,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HeaderButtonIcon",
    )
    //  **圆底与描边必须用 `CircleShape` 自己画**：库的 `IconButton` 走的是 squircle（超椭圆），
    //  圆角取边长一半时也只是「很圆的方」，四角仍比正圆鼓一点（用户口径「感觉这个按钮不是圆的」）。
    //  所以库那层底色置透明，圆底 / 描边 / 裁剪全在这里做。
    Box(
        modifier
            //  挂在底色**外面**：倾斜的是整颗圆（圆底 + 描边 + 字形），不是只有里面那支字
            .pressable(interactionSource = tiltSource, indication = TiltFeedback(), delay = null)
            .background(color = fill, shape = CircleShape)
            .border(width = 1.dp, color = border, shape = CircleShape)
            //  最后这一档裁子级：按压涟漪是画在按钮节点上的，不裁就会从圆的四角溢出去
            .clip(CircleShape),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            backgroundColor = Color.Transparent,
            minWidth = HEADER_ICON_SIZE,
            minHeight = HEADER_ICON_SIZE,
            cornerRadius = HEADER_ICON_SIZE / 2,
        ) {
            //  字形跟着圆底一起灰（见上面那段说明）：`Icon` 的默认 tint 就是这一位。
            CompositionLocalProvider(LocalContentColor provides iconColor) { content() }
        }
    }
}

/**
 * 顶栏左侧那颗「返回」—— 退一页；已经在首页就关掉整页。
 *
 *  **末端那 8dp 是给折叠后的标题让位**：库把折叠标题放在「导航图标宽 + 4dp」处
 *  （`TopAppBarDefaults.NavigationIconGap`），那颗圆键收进栏里之后离标题只有 4dp，挤成一团；
 *  把这 8dp 加在**图标槽内部**，圆键本身距屏幕边缘仍是 `PAGE_MARGIN`（与展开的大标题、与卡片
 *  同一条线），只有折叠标题的起点右移。
 *
 *  顺带把这一格的**外框宽度**报出去（[onWidth]）：折叠态副标题要按它算可用宽度
 *  （见 `HeaderSubtitleBar`）。报的是外框、含末端那 8dp —— 正好是库算 `collapsedTitleX` 用的那个数。
 */
@Composable
internal fun SettingsPage.HeaderBackIcon(
    tick: () -> Unit,
    onWidth: (Int) -> Unit,
) {
    Box(
        Modifier
            .headerPressTick(tick)
            .onSizeChanged { onWidth(it.width) }
            .padding(end = 8.dp),
    ) {
        HeaderIconButton(
            onClick = {
                if (!goBack()) {
                    XLog.w("顶栏返回：已在首页，关页面")
                    closeOverlays("顶栏返回关页面")
                    activity?.finish()
                }
            },
        ) {
            Icon(PageIcons.Back, contentDescription = I18n.get(Strings.COMMON_BACK, "返回"))
        }
    }
}

/**
 * 顶栏右侧那颗 ⋮ ——「选择应用」与「重置」两处菜单的触发键**逐字相同**（同一颗图标、同一个
 * 按压槽位、浮层都锚在按下的那一点），收在这里一处。
 */
@Composable
internal fun SettingsPage.HeaderMenuButton(
    preciseClick: PreciseClickState,
    onOpen: () -> Unit,
) {
    HeaderIconButton(
        onClick = onOpen,
        modifier = Modifier.preciseClickAnchor(preciseClick),
    ) {
        Icon(PageIcons.Menu, contentDescription = I18n.get(Strings.COMMON_MENU, "菜单"))
    }
}
