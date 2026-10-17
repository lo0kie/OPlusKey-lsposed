package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.FROSTED_BLUR_RADIUS
import com.lookie.opluskey.ui.model.FROSTED_SURFACE_ALPHA
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PageIcons
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
