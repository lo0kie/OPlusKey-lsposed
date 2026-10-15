package com.lookie.opluskey.ui.component

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
import com.lookie.opluskey.ui.screen.LaunchableApp
import com.lookie.opluskey.ui.state.UiState
import com.lookie.opluskey.ui.state.deviceSupported
import com.lookie.opluskey.ui.state.linkReady
import com.lookie.opluskey.ui.state.openStockPage
import com.lookie.opluskey.ui.state.resetAll
import com.lookie.opluskey.ui.state.restartModule
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarTrailingSubtitleAlignment
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 顶栏右上角的菜单集合。 */
internal enum class HeaderMenu { NONE, HOME, APP_PICKER }

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
            val collapsed =
                if (measured) {
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
 * @param menu 右上角菜单，见 [HeaderMenu]
 * @param scrollBehavior 与该页滚动容器共享的折叠行为
 * @param state 本页内容层的取样状态（见 `SettingsPage` 的 `pageHazeState`）
 */
@Composable
internal fun SettingsPage.PageHeader(
    title: String,
    subtitle: String = "",
    trailingSubtitleIcon: ImageVector? = null,
    largeTitleSubtitle: String = "",
    menu: HeaderMenu,
    scrollBehavior: ScrollBehavior,
    state: HazeState,
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
        // 那行小字后面那枚小图标（见 [trailingSubtitleIcon] 的说明）
        trailingSubtitleIcon = trailingSubtitleIcon,
        // 小标题贴大标题的哪条边 —— 补丁给的两个档（`Top` / `Bottom`），库默认 `Bottom`
        trailingSubtitleAlignment = TopAppBarTrailingSubtitleAlignment.Bottom,
        // 两个槽位折叠后都保留（右边那条跟着大标题一起缩小；下面那条留在折叠态里）
        hideSubtitleOnCollapse = false,
        scrollBehavior = scrollBehavior,
        color = COUITheme.colorScheme.surface.copy(alpha = FROSTED_SURFACE_ALPHA),
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
 * 「选择应用」页顶栏右侧的菜单按钮 + 浮层菜单 —— 三项：「显示系统应用」「隐藏失效应用」「小窗打开」，
 * 都是开关（行标题以本文件里的 `title` 为准，这里与它逐字对齐）。
 *
 * 列表来自 `MAIN + CATEGORY_LAUNCHER` 查询，只给得出有启动图标的应用；没有启动图标的系统应用与第三方应用
 * （典型是 LSPosed 模块）要靠「显示系统应用」才选得到，其中多数按了会弹「找不到应用」，所以另有「隐藏失效应用」。
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
            modifier =
                Modifier
                    .padding(end = 8.dp)
                    .preciseClickAnchor(preciseClick),
        ) {
            Icon(PageIcons.Menu, contentDescription = I18n.get(Strings.COMMON_MENU, "菜单"))
        }

        MenuPopup(
            show = open,
            preciseClick = preciseClick,
            onDismiss = { open = false },
            entries =
                listOf(
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_SHOW_SYSTEM_APPS, "显示系统应用"),
                        // 行首图标恒为「系统应用」网格，选中态由 `selected` 负责（行尾对勾 + 文字主题色）
                        icon = PageIcons.SystemApps,
                        selected = showSystemApps.value,
                        // 先关菜单再派发（`open` 得自己复位，否则下次进这一页它还开着）
                        onPick = {
                            open = false
                            showSystemApps.value = !showSystemApps.value
                            // 落盘（本地持久，不进用户配置 —— 见 [UiState]）：写在翻档这一下，读在进页那一次。
                            //  写失败就**把开关拨回去**：留着新值会让用户以为这份偏好已经存下来了，
                            //  下次进页又变回去。
                            if (!UiState.writeShowSystemApps(showSystemApps.value)) {
                                showSystemApps.value = !showSystemApps.value
                                XLog.w("选择应用：显示系统应用改不动（界面状态写盘失败），已拨回 ${showSystemApps.value}")
                            } else {
                                // 判据级（i 在 release 被编译期消除，见 XLog 注释）
                                XLog.w("选择应用：显示系统应用 = ${showSystemApps.value}（已落盘，重查列表）")
                            }
                        },
                    ),
                    // 把「按下去必弹失败提示」的那些**也列出来** —— 判据 [LaunchableApp.launchable] 与执行端同一把尺子；
                    // 它是纯显示过滤，不重查列表。默认关（= 不显示），用户口径。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_SHOW_INVALID_APPS, "显示失效应用"),
                        icon = PageIcons.ShowUnopenable,
                        selected = showUnopenable.value,
                        onPick = {
                            open = false
                            showUnopenable.value = !showUnopenable.value
                            if (!UiState.writeShowUnopenable(showUnopenable.value)) {
                                showUnopenable.value = !showUnopenable.value
                                XLog.w("选择应用：显示失效改不动（界面状态写盘失败），已拨回 ${showUnopenable.value}")
                            } else {
                                XLog.w("选择应用：显示失效 = ${showUnopenable.value}（已落盘）")
                            }
                        },
                    ),
                    // 开着时挑中的应用写成 `window:<包名>` —— 用小窗起，见执行侧 [MiniWindowLauncher.launchAppInWindow]。
                    // 注意：这一项**不落盘**：它描述的是「这一趟要把动作写成哪个」，
                    //    不是「这一页该怎么显示」—— 每次进这一页都会按正在编辑的动作重算
                    //    （见 [SettingsPage.smallWindow] / [SettingsPage.resetSmallWindowForPicker]）。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_OPEN_IN_MINI_WINDOW, "以小窗口打开"),
                        icon = PageIcons.SmallWindow,
                        selected = smallWindow.value,
                        onPick = {
                            open = false
                            smallWindow.value = !smallWindow.value
                            XLog.w("选择应用：以小窗口打开 = ${smallWindow.value}（临时档，不落盘）")
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
    // ⋮ **本身永远可点**：「默认界面」那一项必须始终够得着
    // —— 它是模块彻底坏掉时唯一的逃生口（回宿主原版页面）。
    //  跟着 [rowsEnabled] 走的只有那三项**动模块运行时 / 数据**的（重启服务 / 导出日志 / 清空配置）：
    // 模块没就绪时它们改不了什么，放用户去点只会以为生效了。
    //
    //  注意：「更多选项」那一项**已从菜单里删掉**（同日用户要求）：它只留首页那一行，
    // 且那一行**不吃任何闸** —— 页面整页禁用时，用户仍能从那儿进去看「运行状态」到底断在哪一段
    // （见 [HomeScreen] 里那一行的注释）。
    //
    //  注意：三个条件都要：`linkReady`（链路）**且** `!configReadOnly`（配置这次读成功了）**且**
    //  `deviceSupported`（本机那一组「机型 + 系统版本」在适配清单里）。只读态下**只留那两项**：
    //  那时什么都写不进去，放「清空配置」出来点，正好会把「读不懂的那份」当成用户的意图覆盖掉。
    //  清单外的组合同理（只注入了界面层，那三项一个都做不成）。
    //  这两种状态的退出方式都是重进页面（会重读一次）或走「默认界面」。
    //
    //  注意：中途试过「整颗 ⋮ 一起禁用」，被用户否掉 —— 那会把上面那两条命脉一起锁掉。
    //  也别把 `enabled` 加到那颗 `IconButton` 上：库里 `startAction` / `actions` 槽位是**原样渲染**的，
    // 它只给它自己画的东西按 `enabled` 上色，图标不会跟着灰（真机量过：`enabled = false` 时
    // ⋮ 与返回箭头同为 `(24,24,24)`）—— 真要禁用它，得像 [IconAction] 那样自己算 tint。
    val rowsEnabled = linkReady && !configReadOnly && deviceSupported

    Box {
        // 触发键同样是库的 IconButton，只是多挂 `preciseClickAnchor`：浮层弹在按下的那一点
        IconButton(
            onClick = { open = true },
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

        MenuPopup(
            show = open,
            preciseClick = preciseClick,
            onDismiss = { open = false },
            entries =
                listOf(
                    // 「默认界面」：打开未替换的宿主原版页面 —— **永远可用**（逃生口，见上面那段）。
                    //  写显式 `enabled = true`：多年后被「统一置灰」顺手改掉时，这一行会提醒改动者它不能吃那个闸。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_DEFAULT_PAGE, "默认界面"),
                        icon = PageIcons.MenuStock,
                        enabled = true,
                    ) {
                        open = false
                        openStockPage()
                    },
                    MenuEntry.RowLine,
                    // 第二项：重启服务 —— 跟着 [rowsEnabled]（它动的是两个进程，配置不丢，见 [restartModule]）。
                    //  叫「服务」不叫「模块」：这一步杀宿主主进程与本页进程、由系统重新拉起，
                    // **不会**重新加载 system_server 里那份注入（那个只能整机重启）。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_RESTART_SERVICE, "重启服务"),
                        icon = PageIcons.ModuleRestart,
                        enabled = rowsEnabled,
                    ) {
                        open = false
                        restartModule()
                    },
                    MenuEntry.RowLine,
                    // 第三项：导出日志 —— 同样跟着 [rowsEnabled]
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_EXPORT_LOG, "导出日志"),
                        icon = PageIcons.MenuExport,
                        enabled = rowsEnabled,
                    ) {
                        open = false
                        onExportLog?.invoke()
                    },
                    // 「更多」那一项**已删**（「去掉菜单里的更多」）：
                    // 进「更多选项」页只留首页那一行，且那一行**不吃任何闸**（见 [HomeScreen]）——
                    // 它现在同时承担「日常入口」与「模块坏掉时看运行状态的地方」两个角色。
                    // 组间加深带：下面这项是破坏性动作，与上面三项不是一类，细线分不出来
                    MenuEntry.GroupLine,
                    // 第四项：跟着 [rowsEnabled] —— 未就绪时置灰且吃掉点击（由 MenuRow 那层透明吃事件 Box 兜住，菜单不关、不派发）
                    //  文案是「清空」不是「重置」：这一下**真的把两份配置都清掉**
                    // （含已保存的命令），叫「重置」会让人以为只是恢复默认、数据还在。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_CLEAR_CONFIG, "清空配置"),
                        icon = PageIcons.MenuReset,
                        enabled = rowsEnabled,
                        danger = true,
                    ) {
                        open = false
                        confirmReset.value = true
                    },
                ),
        )

        ConfirmDialog(
            show = confirmReset.value,
            title = I18n.get(Strings.DIALOG_CLEAR_CONFIG_TITLE, "清空配置"),
            summary = I18n.get(Strings.DIALOG_CLEAR_CONFIG_MESSAGE, "设置与已保存的命令都会被清空，**不可恢复**。"),
            action = I18n.get(Strings.DIALOG_CLEAR_CONFIG_CONFIRM, "清空"),
            onDismiss = { confirmReset.value = false },
            onConfirm = {
                confirmReset.value = false
                resetAll()
            },
        )
    }
}
