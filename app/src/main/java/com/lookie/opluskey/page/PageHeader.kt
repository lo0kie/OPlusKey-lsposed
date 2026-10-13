package com.lookie.opluskey.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarTrailingSubtitleAlignment
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.blur
import io.github.suqi8.coui.kmp.blur.drawBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.KeyId

/** 顶栏右上角的菜单集合。 */
internal enum class HeaderMenu { NONE, HOME, APP_PICKER }

/** 顶栏的系统栏顶边补偿 —— [currentHeaderHeight] 与 [collapsedHeaderHeight] 共用这一笔。 */
@Composable
internal fun SettingsPage.headerStatusBarPadding(): Dp =
    WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

/**
 * 顶栏当前高度：折叠高 + 折叠位移 + 系统栏顶边。
 *
 * 展开高不硬编码 107dp：库在测量期才把它写进 `heightOffsetLimit`，所以这里按「折叠高 − limit + offset」推。
 *
 * ⚠️ `heightOffsetLimit` 是**普通 var** —— 布局期改它**不触发重组**，而库默认值是
 * `-Float.MAX_VALUE`。拿它直接做减法会得到天文数字，`Modifier.height(...).roundToPx()`
 * 再整型溢出成 **0**：顶部让位的 Spacer 变 0、内容整段钻到顶栏底下（真机实证 2026-10-13，
 * 「按键设置」卡被顶栏盖住）。所以先认这个哨兵，量到之前一律按「展开高」算。
 */
@Composable
internal fun SettingsPage.currentHeaderHeight(scrollBehavior: ScrollBehavior): Dp {
    val state = scrollBehavior.state
    val limit = state.heightOffsetLimit
    val offset = state.heightOffset
    val barHeight = with(LocalDensity.current) {
        val measured = limit.isFinite() && limit > SENTINEL_MIN
        val collapsed = if (measured) {
            TopAppBarDefaults.CollapsedHeight - limit.toDp()
        } else {
            TopAppBarDefaults.ExpandedHeight
        }
        val shift = if (offset.isFinite() && offset > SENTINEL_MIN) offset.toDp() else 0.dp
        maxOf(TopAppBarDefaults.ExpandedHeight, collapsed) + shift
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
internal fun SettingsPage.collapsedHeaderHeight(): Dp =
    TopAppBarDefaults.CollapsedHeight + headerStatusBarPadding()

/**
 * 给一个浮层挂上磨砂：从 `layerBackdrop` 记录的内容层里按 [shape] 取样，做 [FROSTED_BLUR_RADIUS] 半径的模糊。
 *
 * 取样的节点不能落进它自己取样那一层，否则渲染节点链无限增长、RenderThread 栈溢出（真机崩过一次）。
 *
 * @param backdrop 被取样的那一层的 [Backdrop]
 * @param shape 取样区形状，需与浮层自身形状一致
 */
@Composable
internal fun Modifier.frosted(backdrop: Backdrop, shape: Shape): Modifier {
    val blurRadiusPx = with(LocalDensity.current) { FROSTED_BLUR_RADIUS.toPx() }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(blurRadiusPx) },
    )
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
 * @param largeTitleSubtitle 大标题**下面**那行小字（空 = 不显示）。走库原本的底线副标题槽位
 *   （`subtitle`，补丁没动它）——它与上面那个是**两个独立槽位**，可以同时给。它**自己占一行**，
 *   所以顶栏展开高度会因此长高一行；页面内容的自让高度走 [currentHeaderHeight]（按库上报的
 *   折叠量算），跟着一起长，不会被顶栏压住。
 * @param menu 右上角菜单，见 [HeaderMenu]
 * @param scrollBehavior 与该页滚动容器共享的折叠行为
 * @param backdrop 内容层的背景
 */
@Composable
internal fun SettingsPage.PageHeader(
    title: String,
    subtitle: String = "",
    largeTitleSubtitle: String = "",
    menu: HeaderMenu,
    scrollBehavior: ScrollBehavior,
    backdrop: Backdrop,
    /**
     * 顶栏底下那一块（库 `TopAppBar` 的 `bottomContent` 槽位）——「按键绑定」的分段控件、
     * 「自定义命令」的类型选择器、「选择应用」的搜索框这三条固定带都走这里。
     *
     * 带子成了顶栏这个节点的一部分 ⇒ 顶栏的半透明底与模糊一起盖住它，顶栏底边那条细线也落在带子下沿；
     * 带子高度由它自己 `onSizeChanged` 报给页面级状态。
     */
    bottomContent: @Composable () -> Unit = {},
) {
    // 模糊半径按像素给（`blur` 收的是 px）—— 换算与取样都在 [frosted] 里。
    TopAppBar(
        title = title,
        // 库原本的底线副标题槽位：大标题**下面**单独一行（补丁没动它）
        subtitle = largeTitleSubtitle,
        // 补丁新增的槽位：大标题**右边**那行（与上面那个互不影响）
        trailingSubtitle = subtitle,
        // 小标题贴大标题的哪条边 —— 补丁给的两个档（`Top` / `Bottom`），库默认 `Bottom`
        trailingSubtitleAlignment = TopAppBarTrailingSubtitleAlignment.Bottom,
        // 两个槽位折叠后都保留（右边那条跟着大标题一起缩小；下面那条留在折叠态里）
        hideSubtitleOnCollapse = false,
        scrollBehavior = scrollBehavior,
        color = COUITheme.colorScheme.surface.copy(alpha = FROSTED_SURFACE_ALPHA),
        // 顶栏是整条矩形，不切圆角
        modifier = Modifier.frosted(backdrop, RectangleShape),
        navigationIcon = {
            // 用库的 `IconButton`：尺寸走它的 `minWidth/minHeight`、圆角给一半，按压高亮（squircle）就落在 40dp 圆内；
            // 尺寸必须自己给，库默认 48dp / 圆角 24dp。
            IconButton(
                onClick = {
                    if (!goBack()) {
                        XLog.i("顶栏返回：已在首页，关页面")
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
                Icon(PageIcons.Back, contentDescription = "返回")
            }
        },
        actions = {
            when (menu) {
                HeaderMenu.NONE -> Unit
                HeaderMenu.HOME -> ResetMenu()
                HeaderMenu.APP_PICKER -> AppPickerMenu()
            }
        },
        bottomContent = bottomContent,
    )
}

/**
 * 「选择应用」页顶栏右侧的菜单按钮 + 浮层菜单 —— 三项：「显示系统应用」「隐藏不可打开」「小窗打开」，
 * 都是开关。
 *
 * 列表来自 `MAIN + CATEGORY_LAUNCHER` 查询，只给得出有启动图标的应用；没有启动图标的系统应用与第三方应用
 * （典型是 LSPosed 模块）要靠「显示系统应用」才选得到，其中多数按了会弹「找不到应用」，所以另有「隐藏不可打开」。
 * 「小窗打开」不筛列表，只决定挑中的应用写成 `app:` 还是 `window:`。
 * 选中态交给 [MenuEntry.Row] 的 `selected`（行首图标恒为菜单各自那颗）。浮层弹在手指按下那一点（`preciseClickAnchor`）。
 */
@Composable
internal fun SettingsPage.AppPickerMenu() {
    var open by remember { mutableStateOf(false) }
    val preciseClick = rememberPreciseClickState()
    Box {
        IconButton(
            onClick = { open = true },
            minWidth = HEADER_ICON_SIZE,
            minHeight = HEADER_ICON_SIZE,
            cornerRadius = HEADER_ICON_SIZE / 2,
            modifier = Modifier
                .padding(end = 8.dp)
                .preciseClickAnchor(preciseClick),
        ) {
            Icon(PageIcons.Menu, contentDescription = "菜单")
        }

        MenuPopup(
            show = open,
            preciseClick = preciseClick,
            onDismiss = { open = false },
            entries = listOf(
                MenuEntry.Row(
                    title = "显示系统应用",
                    // 行首图标恒为「系统应用」网格，选中态由 `selected` 负责（行尾对勾 + 文字主题色）
                    icon = PageIcons.SystemApps,
                    selected = showSystemApps.value,
                    // 先关菜单再派发（`open` 得自己复位，否则下次进这一页它还开着）
                    onPick = {
                        open = false
                        showSystemApps.value = !showSystemApps.value
                        // 落盘（本地持久，不进用户配置 —— 见 [UiState]）：写在翻档这一下，读在进页那一次
                        UiState.writeShowSystemApps(showSystemApps.value)
                        XLog.i("选择应用：显示系统应用 = ${showSystemApps.value}（已落盘，重查列表）")
                    },
                ),
                // 把「按下去必弹失败提示」的那些藏起来 —— 判据 [LaunchableApp.launchable] 与执行端同一把尺子；
                // 它是纯显示过滤，不重查列表
                MenuEntry.Row(
                    title = "隐藏失效应用",
                    icon = PageIcons.HideUnopenable,
                    selected = hideUnopenable.value,
                    onPick = {
                        open = false
                        hideUnopenable.value = !hideUnopenable.value
                        UiState.writeHideUnopenable(hideUnopenable.value)
                        XLog.i("选择应用：隐藏失效 = ${hideUnopenable.value}（已落盘）")
                    },
                ),
                // 开着时挑中的应用写成 `window:<包名>` —— 用小窗起，见执行侧 [AppLauncher.launchAppInWindow]
                MenuEntry.Row(
                    title = "以小窗口打开",
                    icon = PageIcons.SmallWindow,
                    selected = smallWindow.value,
                    onPick = {
                        open = false
                        smallWindow.value = !smallWindow.value
                        UiState.writeSmallWindow(smallWindow.value)
                        XLog.i("选择应用：小窗打开 = ${smallWindow.value}（已落盘）")
                    },
                ),
            ),
        )
    }
}

/** 顶栏右侧那颗菜单按钮 + 它的浮层菜单（见 [HeaderMenu]）。 */
@Composable
internal fun SettingsPage.ResetMenu() {
    var open by remember { mutableStateOf(false) }
    // 两层的「页面离开收起」各自登记：⋮ 菜单由 MenuPopup 管，确认弹窗由 ConfirmDialog 管（都不是本函数登记）
    val preciseClick = rememberPreciseClickState()
    // 主开关关着、**或宿主服务没在跑**时，只留「默认界面」与「关于模块」可点（这两项与模块运行状态无关）；
    // 其余三项都在动模块运行时 / 数据，一律置灰。
    //  「默认界面」特意不禁用：模块没生效时它是唯一的逃生口（回宿主原版页面）。
    val enabled = config.value.key(KeyId.PLUS).enabled && hostServiceUp

    Box {
        // 触发键同样是库的 IconButton，只是多挂 `preciseClickAnchor`：浮层弹在按下的那一点
        IconButton(
            onClick = { open = true },
            minWidth = HEADER_ICON_SIZE,
            minHeight = HEADER_ICON_SIZE,
            cornerRadius = HEADER_ICON_SIZE / 2,
            modifier = Modifier
                .padding(end = 8.dp)
                .preciseClickAnchor(preciseClick),
        ) {
            Icon(PageIcons.Menu, contentDescription = "菜单")
        }

        MenuPopup(
            show = open,
            preciseClick = preciseClick,
            onDismiss = { open = false },
            entries = listOf(
                // 第一项：打开未替换的宿主原版页面 —— 永远可用
                MenuEntry.Row(title = "默认界面", icon = PageIcons.MenuStock) {
                    open = false
                    openStockPage()
                },
                MenuEntry.RowLine,
                // 第二项：重启服务 —— 跟着主开关（它动的是宿主进程那一层，配置不丢，见 [restartModule]）。
                //  叫「服务」不叫「模块」：这一步只是杀宿主主进程、让系统把页面整棵拉起，
                // **不会**重新加载 system_server 里那份注入（那个只能整机重启）。
                MenuEntry.Row(title = "重启服务", icon = PageIcons.ModuleRestart, enabled = enabled) {
                    open = false
                    restartModule()
                },
                MenuEntry.RowLine,
                // 第三项：导出日志 —— 同样跟着主开关
                MenuEntry.Row(title = "导出日志", icon = PageIcons.MenuExport, enabled = enabled) {
                    open = false
                    onExportLog?.invoke()
                },
                MenuEntry.RowLine,
                // 第四项：关于模块 —— 永远可用
                MenuEntry.Row(title = ABOUT_TITLE, icon = PageIcons.Info) {
                    open = false
                    navTo(Screen.ABOUT)
                },
                // 组间加深带：下面这项是破坏性动作，与上面四项不是一类，细线分不出来
                MenuEntry.GroupLine,
                // 第五项：跟着主开关 —— 关掉时置灰且吃掉点击（由 MenuRow 那层透明吃事件 Box 兜住，菜单不关、不派发）
                MenuEntry.Row(
                    title = "重置配置",
                    icon = PageIcons.MenuReset,
                    enabled = enabled,
                    danger = true,
                ) {
                    open = false
                    confirmReset.value = true
                },
            ),
        )

        ConfirmDialog(
            show = confirmReset.value,
            title = "重置所有配置",
            summary = "主开关、动作绑定与全部设置将恢复默认，已保存的命令不受影响。",
            action = "重置",
            onDismiss = { confirmReset.value = false },
            onConfirm = {
                confirmReset.value = false
                resetAll()
            },
        )
    }
}
