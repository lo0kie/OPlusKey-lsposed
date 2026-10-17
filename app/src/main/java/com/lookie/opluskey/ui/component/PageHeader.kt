package com.lookie.opluskey.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.FROSTED_BLUR_RADIUS
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PageIcons
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarTrailingSubtitleAlignment
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 顶栏的系统栏顶边补偿 —— [currentHeaderHeight] 与 [collapsedHeaderHeight] 共用这一笔。 */
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
internal fun SettingsPage.currentHeaderHeight(scrollBehavior: ScrollBehavior): Dp {
    val state = scrollBehavior.state
    val limit = state.heightOffsetLimit
    val offset = state.heightOffset
    val barHeight =
        with(LocalDensity.current) {
            val measured = limit.isFinite() && limit > SENTINEL_MIN
            // 名字按**它算出来是什么**取：`limit` 是负数（= 展开高 − 折叠高），`CollapsedHeight - limit`
            //  比折叠高**大**，正是当前这一档的展开高 —— 叫 `collapsed` 会读成「折叠高」，与实际相反。
            val expanded =
                if (measured) {
                    TopAppBarDefaults.CollapsedHeight - limit.toDp()
                } else {
                    TopAppBarDefaults.ExpandedHeight
                }
            val shift = if (offset.isFinite() && offset > SENTINEL_MIN) offset.toDp() else 0.dp
            maxOf(TopAppBarDefaults.ExpandedHeight, expanded) + shift
        }
    return barHeight + headerStatusBarPadding()
}

/** 认哨兵的阈值（像素）：一屏也就几千 px，比它更负的一定是库的 `-Float.MAX_VALUE` 初值。 */
private const val SENTINEL_MIN = -1_000_000f

/**
 * 顶栏折叠到底时的高度 —— [currentHeaderHeight] 的同一笔账，折叠位移取满。
 *
 * 系统栏顶边那一档不能漏，漏了目标行会被压在顶栏底下只露一半。
 */
@Composable
internal fun SettingsPage.collapsedHeaderHeight(): Dp = TopAppBarDefaults.CollapsedHeight + headerStatusBarPadding()

/**
 * 给一个浮层挂上磨砂：从 [state] 那套取样源里按 [shape] 取一块，做 [FROSTED_BLUR_RADIUS] 的模糊。
 *
 *  前提：取样方不能落进它自己取样的那个 `hazeSource` 里面，否则渲染节点链无限增长、
 *  RenderThread 栈溢出（真机崩过一次）—— 页面内容那层**里面**的节点都不满足这条。
 *
 *  形状得自己裁：Haze 2 的模糊 API 里没有 `shape`（1.x 的 `HazeEffectScope.shape` 已去掉），
 *  官方示例靠容器自己的 `shape` 裁 —— 我们这几处容器都没形状，所以在链上补一个 `clip`。
 *  注意 `clip` 只裁**它之后**的链：底部工具栏那处特意把阴影留在更外层，正是为了不被裁掉。
 *
 * @param state 被取样的那一层的取样状态（页面那一层是 [SettingsPage.pageHazeState]）
 * @param shape 取样区形状，需与浮层自身形状一致
 */
internal fun Modifier.frosted(
    state: HazeState,
    shape: Shape,
): Modifier =
    this
        .clip(shape)
        .hazeBlur(input = HazeInput.Sources(state), style = HAZE_FROSTED_STYLE)

/**
 * 磨砂的模糊规格 —— 顶栏 / 浮层菜单 / 底部工具栏 / 确认弹窗共用这一份。
 *
 * [FROSTED_BLUR_RADIUS] 直接就是 dp：Haze 的 `blurRadius` 收 dp，不必再按 density 换算成像素。
 * 噪点显式置 0 —— 库默认 0.15，会在糊上叠一层细颗粒，与这套观感对不上。
 */
private val HAZE_FROSTED_STYLE: HazeBlurStyle =
    HazeBlurStyle {
        blurRadius(FROSTED_BLUR_RADIUS)
        noiseFactor(0f)
    }

/**
 * 页面顶栏：大标题 + 返回键，随内容滚动折叠（展开 107dp / 折叠 52dp），内容滚到栏下时底边渐显分割线。
 *
 * [scrollBehavior] 由调用方创建，同一实例必须同时挂给该页滚动容器，否则顶栏恒停在展开态。
 *
 * @param title 标题文案
 * @param subtitle 大标题**右边**那行小标题（空 = 不显示）。走补丁**新增**的「大标题右侧小标题」
 *   能力（`trailingSubtitle`，见 `patches/coui/`）——它是在库原有底线副标题之外**新增**的槽位，
 *   不替代、也不改变库 `subtitle` 的语义。跟在大标题右边、跟着折叠一起移动、字号缩小
 *   （14dp → 12dp），折叠后保留（`hideSubtitleOnCollapse = false`）；
 *   贴上边还是下边由 `trailingSubtitleAlignment` 选。
 * @param trailingSubtitleIcon [subtitle] 后面那枚小图标（`null` = 不画）。走补丁新增的
 *   `trailingSubtitleIcon`（见 `patches/coui/0004-…`）：它与那行小字在**同一个盒子**里，
 *   所以跟着小字一起缩（折叠动画里同步）。现在只有「选择应用」页用它 —— 开着小窗打开时补一枚
 *   「小窗起」，与网格那格同一口径。
 * @param largeTitleSubtitle 大标题**下面**那行小字（空 = 不显示）。走库原本的底线副标题槽位
 *   （`subtitle`，补丁没动它）——它与上面那个是**两个独立槽位**，可以同时给。它**自己占一行**，
 *   所以顶栏展开高度会因此长高一行；页面内容的自让高度走 [currentHeaderHeight]（按库上报的
 *   折叠量算），跟着一起长，不会被顶栏压住。
 * @param scrollBehavior 与该页滚动容器共享的折叠行为
 * @param state 本页内容层的取样状态（见 `SettingsPage` 的 `pageHazeState`）
 * @param actions 右上角那几颗动作键（槽位带行的 `RowScope` —— 库的 `actions` 就是这个形状） —— **顶栏本身不认识任何页面**：哪一页摆哪一颗菜单由调用方给
 *   （现有一份 [SettingsPage.ResetMenu]、一份 [SettingsPage.AppPickerMenu]），不传就是没有 ⋮。
 * @param bottomContent 顶栏底下那一块（库 `TopAppBar` 的 `bottomContent` 槽位）——「按键绑定」的分段控件、
 *   「自定义命令」的类型选择器、「选择应用」的搜索框这三条固定带都走这里。
 *
 *   带子成了顶栏这个节点的一部分 ⇒ 顶栏的半透明底与模糊一起盖住它，顶栏底边那条细线也落在带子下沿；
 *   带子高度由它自己 `onSizeChanged` 报给页面级状态。
 */
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
    // 磨砂规格（含模糊半径）统一由 [frosted] 挂，见 [HAZE_FROSTED_STYLE]。
    TopAppBar(
        title = title,
        // 库原本的底线副标题槽位：大标题**下面**单独一行（补丁没动它）
        subtitle = largeTitleSubtitle,
        // 补丁新增的槽位：大标题**右边**那行（与上面那个互不影响）
        trailingSubtitle = subtitle,
        // 那行小字后面那枚小图标（见 [trailingSubtitleIcon] 的说明）
        trailingSubtitleIcon = trailingSubtitleIcon,
        // 小标题贴大标题的哪条边 —— 补丁给的两个档（`Top` / `Bottom`），库默认 `Bottom`
        trailingSubtitleAlignment = TopAppBarTrailingSubtitleAlignment.Bottom,
        // 两个槽位折叠后都保留（右边那条跟着大标题一起缩小；下面那条留在折叠态里）
        hideSubtitleOnCollapse = false,
        scrollBehavior = scrollBehavior,
        // 按**比例**缩这一档，不整个换 alpha（口径见 [PageConst.FROSTED_SURFACE_ALPHA]：
        //  深浅两套色板里同名色可能一个是实色、一个是半透，替换会把后者顶成亮色）。
        color =
            COUITheme.colorScheme.surface.copy(
                alpha = COUITheme.colorScheme.surface.alpha * FROSTED_SURFACE_ALPHA,
            ),
        // 顶栏是整条矩形，不切圆角
        modifier = Modifier.frosted(state, RectangleShape),
        navigationIcon = {
            // 用库的 `IconButton`：尺寸走它的 `minWidth/minHeight`、圆角给一半，按压高亮（squircle）就落在 40dp 圆内；
            // 尺寸必须自己给，库默认 48dp / 圆角 24dp。
            IconButton(
                onClick = {
                    if (!goBack()) {
                        XLog.w("顶栏返回：已在首页，关页面")
                        // 关页面之前先收浮窗：宿主是 singleInstance，页面不一定会被销毁，浮窗留着会跟着下次进页面一起回来。
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
        bottomContent = bottomContent,
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
