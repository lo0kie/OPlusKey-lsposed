package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.BandHeight
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarTrailingSubtitleAlignment
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

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
 * 顶栏当前高度：折叠高 + 折叠位移 + 系统栏顶边。
 *
 * 展开高不硬编码 107dp：库在测量期才把它写进 `heightOffsetLimit`，所以这里按「折叠高 − limit + offset」推。
 *
 * 注意：`heightOffsetLimit` 是**普通 var** —— 布局期改它**不触发重组**，而库默认值是
 * `-Float.MAX_VALUE`。拿它直接做减法会得到天文数字，`Modifier.height(...).roundToPx()`
 * 再整型溢出成 **0**：顶部让位的 Spacer 变 0、内容整段钻到顶栏底下（真机实证，
 * 「按键设置」卡被顶栏盖住）。所以先认这个哨兵，量到之前一律按「展开高」算。
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
 *  **量到之前不许写**：`heightOffsetLimit` 在库测量之前是 `-Float.MAX_VALUE`（见
 * [currentHeaderHeight] 那段），把那个哨兵写进 `heightOffset` 会让整页的「当前顶栏高度」
 * 失真 —— 表现出来就是「定位跳完，目标行被压在展开的顶栏底下」。
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
    //  （负值，绝对值就是折叠掉多少）。以前这里用库的 `CollapsedHeight` 常量，与实测的展开高
    //  差十几 dp —— 定位时目标行就跟着偏那么多。
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
 *  `subtitleColor` 是尾随标题（大标题右边那行）的颜色，默认就是库那一档次要色。留成参数是为了
 *  「这一页整体按不动」时把它也压暗一档：内容区整片变灰了，这一行还按满亮度亮着，读起来像
 *  那一区仍然是活的（见 `PageTopBar` 里停用那支）。
 *
 *  注：参数**不逐条写 KDoc** —— detekt 的基线 ID 会把参数上的注释一起抄进去，
 *  那段文字一改基线就得跟着改。要解释的写在这里。
 */
@Composable
internal fun SettingsPage.PageHeader(
    title: String,
    subtitle: String = "",
    subtitleColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
    trailingSubtitleIcon: ImageVector? = null,
    largeTitleSubtitle: String = "",
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
    // 磨砂规格（含模糊半径）统一由 [frosted] 挂，见 [HAZE_FROSTED_STYLE]。
    TopAppBar(
        title = title,
        subtitle = largeTitleSubtitle,
        trailingSubtitle = subtitle,
        trailingSubtitleColor = subtitleColor,
        trailingSubtitleIcon = trailingSubtitleIcon,
        trailingSubtitleAlignment = TopAppBarTrailingSubtitleAlignment.Bottom,
        hideSubtitleOnCollapse = false,
        //  三个水平边距统一到**卡片那一档**（[PAGE_MARGIN]）：整页所有卡片的外边距、固定带里
        //  的分段控件、选择应用页的搜索框都是这一档，顶栏两侧的圆键与大标题跟着走同一条竖线
        //  （原来用库默认的 `TitlePadding` / `NavigationIconPadding` / `ActionIconPadding`
        //  三处 16dp，比卡片多 4dp，左边一片看着「差一点点」）。
        //  `navigationIcon` 那一格内部还额外垫了 8dp（见下面的注释）—— 那是给折叠标题让位，
        //  不算外边距，别把它折算进这一档。
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
                .onSizeChanged { headerBand.report(it.height - bandHeightPx.value) },
        navigationIcon = {
            //  盒子只负责挂振动：单子节点，排版与不加这一层完全一样。
            //  **末端那 8dp 是给折叠后的标题让位**：库把折叠标题放在「导航图标宽 + 4dp」处
            //  （`TopAppBarDefaults.NavigationIconGap`），那颗圆键收进栏里之后离标题只有 4dp，
            //  挤成一团；把这 8dp 加在**图标槽内部**，圆键本身距屏幕边缘仍是 [PAGE_MARGIN]
            //  （与展开的大标题、与卡片同一条线），只有折叠标题的起点右移。
            Box(Modifier.headerPressTick(tick).padding(end = 8.dp)) {
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
        },
        actions = {
            //  右侧那一组同样只挂振动：`Row` 保持「一个挨一个靠右」，与库那排的排法一致
            Row(
                modifier = Modifier.headerPressTick(tick),
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
private fun Modifier.headerPressTick(tick: () -> Unit): Modifier =
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
 * @param enabled 与按钮自己的可用性同一判据 —— 只影响这两层配色，点不点得动由 `onClick` 那边定
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
    //  **圆底与描边必须用 `CircleShape` 自己画**：库的 `IconButton` 走的是 squircle（超椭圆），
    //  圆角取边长一半时也只是「很圆的方」，四角仍比正圆鼓一点（用户口径「感觉这个按钮不是圆的」）。
    //  所以库那层底色置透明，圆底 / 描边 / 裁剪全在这里做。
    Box(
        modifier
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
            content()
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
