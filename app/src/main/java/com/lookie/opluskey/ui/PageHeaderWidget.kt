package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
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

@Composable
internal fun SettingsPage.PageHeader(
    title: String,
    subtitle: String = "",
    trailingSubtitleIcon: ImageVector? = null,
    largeTitleSubtitle: String = "",
    scrollBehavior: ScrollBehavior,
    state: HazeState,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    // 顶栏里那条固定带的高度（没有带子时为 0）：上报顶栏高度时扣掉它。
    val bandHeightPx = remember { mutableIntStateOf(0) }
    val headerBand = currentHeaderBand()
    // 磨砂规格（含模糊半径）统一由 [frosted] 挂，见 [HAZE_FROSTED_STYLE]。
    TopAppBar(
        title = title,
        subtitle = largeTitleSubtitle,
        trailingSubtitle = subtitle,
        trailingSubtitleIcon = trailingSubtitleIcon,
        trailingSubtitleAlignment = TopAppBarTrailingSubtitleAlignment.Bottom,
        hideSubtitleOnCollapse = false,
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
            IconButton(
                onClick = {
                    if (!goBack()) {
                        XLog.w("顶栏返回：已在首页，关页面")
                        closeOverlays("顶栏返回关页面")
                        activity?.finish()
                    }
                },
                minWidth = HEADER_ICON_SIZE,
                minHeight = HEADER_ICON_SIZE,
                cornerRadius = HEADER_ICON_SIZE / 2,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(PageIcons.Back, contentDescription = I18n.get(Strings.COMMON_BACK, "返回"))
            }
        },
        actions = actions,
        bottomContent = {
            Box(Modifier.onSizeChanged { bandHeightPx.value = it.height }) { bottomContent() }
        },
    )
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
    IconButton(
        onClick = onOpen,
        minWidth = HEADER_ICON_SIZE,
        minHeight = HEADER_ICON_SIZE,
        cornerRadius = HEADER_ICON_SIZE / 2,
        modifier =
            Modifier
                .padding(end = 8.dp)
                .preciseClickAnchor(preciseClick),
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
