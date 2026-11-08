package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.opticalTightTop
import com.lookie.opluskey.ui.state.BandHeight
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

// 顶栏的**骨架**：高度通道、折叠几何、以及那条 `TopAppBar` 本身。
//
//  另两半各自成文件（都是「同一件事的另一半」，不按字母切）：
//  · `PageHeaderButtons.kt` —— 顶栏上那几颗圆键与「按一下振一下」；
//  · `PageHeaderSubtitle.kt` —— 副标题按可用宽度截断的两档账本。
//  这么切有一半是 detekt 的 `TooManyFunctions` 逼的（单文件上限 11 个函数），但切完确实
//  三块各管一件事：这一份只管「栏怎么摆、多高、怎么折」。

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
 *  **副标题一律排在标题下面、自己占一行**（库的 `subtitle` 槽位）：它装的是键名 / 应用名 /
 *  命令名，长度不受控，挤在大标题同一行只会把大标题压成省略号。宽度在这一层夹，调用点不该
 *  各自截一遍 —— 夹多少**按这一页当刻的可用宽度算**，展开 / 折叠两档分开判，账本在
 *  `HeaderSubtitleBar`。
 *
 *  `subtitleColor` 是标题下面那行的颜色，默认就是库那一档次要色。留成参数是为了
 *  「这一页整体按不动」时把它也压暗一档：内容区整片变灰了，这一行还按满亮度亮着，读起来像
 *  那一区仍然是活的（见 `PageTopBar` 里停用那支）。
 *
 *  注：参数**不逐条写 KDoc** —— detekt 的基线 ID 会把参数上的注释一起抄进去，
 *  那段文字一改基线就得跟着改。要解释的写在这里。
 *
 *  `@Suppress("LongParameterList")`：这些参数就是**顶栏那几个槽位本身**（标题 / 副标题 /
 *  动作 / 固定带），拆成一个 spec 对象只是把它们挪个地方、再让调用点多套一层壳。
 *  这一条原先压在 detekt 基线里（按签名记的条目），动一个参数就得同步改基线 —— 就地改成注解
 *  之后那一条基线随之删掉（条目数只减不增）。
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.PageHeader(
    title: String,
    subtitle: String = "",
    subtitleColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
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
        subtitle = subtitleBar.shown(subtitle),
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
