package com.lookie.opluskey.ui.screen

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.LongPressEdit
import com.lookie.opluskey.core.model.appKeyOf
import com.lookie.opluskey.core.model.mergeCommandSeeds
import com.lookie.opluskey.core.model.trigger
import com.lookie.opluskey.core.model.withLongPress
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.LocalHeaderBoxHeight
import com.lookie.opluskey.ui.chrome.headerReady
import com.lookie.opluskey.ui.chrome.loadPageState
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.tickNow
import com.lookie.opluskey.ui.screen.about.AboutScreen
import com.lookie.opluskey.ui.screen.apppicker.ActivityPickerScreen
import com.lookie.opluskey.ui.screen.apppicker.AppPickerScreen
import com.lookie.opluskey.ui.screen.binding.BindingPagerScreen
import com.lookie.opluskey.ui.screen.binding.KeySettingsScreen
import com.lookie.opluskey.ui.screen.binding.comboGestureSlots
import com.lookie.opluskey.ui.screen.binding.pickToken
import com.lookie.opluskey.ui.screen.combo.KeysScreen
import com.lookie.opluskey.ui.screen.command.BranchScreen
import com.lookie.opluskey.ui.screen.command.COMMAND_PAGE_COUNT
import com.lookie.opluskey.ui.screen.command.CommandScreen
import com.lookie.opluskey.ui.screen.command.branchDraftNow
import com.lookie.opluskey.ui.screen.command.sceneGesturePages
import com.lookie.opluskey.ui.screen.command.writeBranchCondition
import com.lookie.opluskey.ui.screen.common.CommonCommandsScreen
import com.lookie.opluskey.ui.screen.donate.DonateListScreen
import com.lookie.opluskey.ui.screen.general.GeneralScreen
import com.lookie.opluskey.ui.screen.home.HomeScreen
import com.lookie.opluskey.ui.screen.variables.VarManageState
import com.lookie.opluskey.ui.screen.variables.VariableScreen
import com.lookie.opluskey.ui.state.binding.KeyWatchState
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.state.host.installI18nBridge
import com.lookie.opluskey.ui.state.page.CommandSeed
import com.lookie.opluskey.ui.state.picker.AppPickerState
import com.lookie.opluskey.ui.state.picker.PickTarget
import com.lookie.opluskey.ui.state.picker.PickedDetail
import com.lookie.opluskey.ui.state.signal.BandHeight
import com.lookie.opluskey.ui.style.pageBackgroundColor
import com.lookie.opluskey.ui.style.systemNight
import com.lookie.opluskey.ui.wire.appNameOf
import com.lookie.opluskey.ui.wire.linkReady
import com.lookie.opluskey.ui.write.BindingTarget
import com.lookie.opluskey.ui.write.addStep
import com.lookie.opluskey.ui.write.replaceStep
import com.lookie.opluskey.ui.write.saveCommands
import com.lookie.opluskey.ui.write.write
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

/**
 * 一页 —— 每页**各持一份**的那些东西（滚动行为、磨砂取样状态），加上三块浮在内容之上的东西
 * （首页提示条、命令页工具栏、顶栏）。
 *
 *  每页独享的东西必须写在 entry 的内容里：那是**每个 entry 各自一份**的组合，`remember`
 *  天然逐页独立。
 */
@Composable
internal fun SettingsPage.PageEntry(
    s: Screen,
    pagers: PagePagers,
) {
    val scrollBehavior = pageScrollBehavior(s)
    // 顶栏 / 浮层菜单 / 底部工具栏的磨砂**取样源**（Haze 的 `hazeSource`）。
    //
    //  `hazeSource` **只抓这个节点往里的那一串**，不含父级背景 ——
    //  所以底色得挂在它**之后**（见下面那句 `background`）：写在它前面
    //  就成了记录层外的另一笔，透明区域照样把颜色扩散进去、糊成色块。
    val surfaceColor = COUITheme.colorScheme.surface
    val hazeState = rememberHazeState()
    //  登记给页面级：浮层菜单 / 底部工具栏按**栈顶页**取用（见 [pageHazeState]）。
    //  写在 effect 里而不是组合期：组合期写共享可变结构是反模式（写普通 Map
    //  不触发重组，读到的是不是这一帧说不准）；页被 cull / 出栈时由 `onDispose`
    //  把本条摘掉，旧实例（源节点已 detach）不会一直留在表里。
    DisposableEffect(s) {
        chrome.hazeStatesByPage[s] = hazeState
        onDispose {
            //  **只在「表里还是我这一份」时才摘**：退场动画期间同一个 `Screen` 可能已经被新实例
            //  重新登记（手快时「返回上一页 → 立刻再点进同一页」必现）—— 无条件 `remove(s)`
            //  抹掉的是**新那一份**，之后顶栏取样到一个 attach 不上的节点：磨砂不跟着内容走、
            //  或者整条带子发黑。判等用引用比（`===`）：新实例的 `hazeState` 一定是另一个对象。
            if (chrome.hazeStatesByPage[s] === hazeState) chrome.hazeStatesByPage.remove(s)
        }
    }
    //  这一页组合完成时若它正是栈顶，就把取样源摆过来 —— 与 [PageEffects] 里那个
    //  `LaunchedEffect(navStack.lastOrNull())` 互为兜底（那边可能早一帧，见它自己的注释）。
    //  幂等：同一个实例写回去不会触发重组。
    LaunchedEffect(s) {
        if (navStack.lastOrNull() == s) chrome.pageHazeState = hazeState
    }
    //  从**上一次量到的值**起步（按页缓存）：出栈再回来时不至于先按兜底常量排一帧
    //  ——那一帧的差就是「回首页内容上移一点点」。
    val headerBand =
        remember(s) {
            BandHeight(
                initialPx = chrome.headerHeights[s] ?: 0,
                onReport = { chrome.headerHeights[s] = it },
            )
        }
    CompositionLocalProvider(LocalHeaderBoxHeight provides headerBand) {
        Box(
            Modifier
                .fillMaxSize()
                // 每个页面自带不透明底：转场交叠时不透出下层残影
                .background(surfaceColor)
                .alpha(if (headerReady(headerBand)) 1f else 0f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    //  采集层的底色（挂在 `hazeSource` **之后**，见上）
                    .background(surfaceColor),
            ) {
                PageBody(s, scrollBehavior, pagers)
            }

            // 首页那条「模块未生效」提示与操作回执一起挂在根 `Scaffold` 的**浮层槽**
            //  （`popupHost`，排在弹窗宿主之后，见 [PageSnackbars]）—— 那里不随页走、也不跟着
            //  内容滚，所以这里不再有它。

            // —— 自定义命令页的**底部操作悬浮工具栏** ——
            //
            //  它必须挂在这里（页内、但那个取样源**之外**）：
            // 顶栏取样的就是上面那个 `hazeSource` 节点 ——
            // 取样的节点**不能落进它自己取样那一份**，
            // 否则记录层为了画自己得再画它、它又去取样记录层……渲染节点链
            // 无限增长，最终 RenderThread **栈溢出**（`RenderNode::prepareTreeImpl`
            // 递归 500+ 层），宿主 UI 进程当场被杀 —— 真机崩过一次，就是这个。
            // 所以工具栏与顶栏同一个挂法：页内、取样源外，只是贴在底部。
            // （它自己那层模糊挂上了，与顶栏同一个取样层。）
            //
            // 只在「自定义命令」那一页显示；整页 push / pop 转场时它跟着一起滑
            // （挂在页里，不是挂在页面之外）。
            //  「条件执行」页**不用**它：那一页是两颗一叠的 FAB（右下角「添加」常驻、「确定」
            //  有改动才出现，见 `BranchScreen`），落盘点跟着动作走，不再单独占一条底栏。
            // 顶栏：叠在内容**之上**（`Box` 里后声明的画在上面），
            // 半透明底 + 模糊从它底下滚过去的内容 —— 见 [PageHeader]。
            PageTopBar(s, scrollBehavior, hazeState, pagers)
        }
    }
}

/**
 * 大标题的滚动折叠（COUI「TopAppBar → 大标题顶部栏滚动行为」）—— **每页各持一份**：
 * 滚动行为是「这一页的列表滚到哪」的账，共用一份的话翻页之后标题的折叠进度会串页。
 *
 *  顶栏与滚动容器**必须吃同一个实例**：顶栏读它的 `state` 画折叠进度，滚动容器把
 *  `nestedScrollConnection` 挂在链上把余量喂进来。只给一头等于没接（顶栏恒停在展开态）。
 *
 *  **只有首页**的标题不折叠（定案），其余每一页都折：库的开关是 `canScroll` ——
 *  false 时顶栏不再消费滚动事件（`ExitUntilCollapsedScrollBehavior` 里
 *  `if (!canScroll()) return Zero`），大标题钉在原位、内容照常从它底下滚过。
 *
 *  注意：**每页都必须自己给初始 `heightOffsetLimit`**（只给钉住的几页的话，
 *  折叠那几页靠「频繁重组自愈」，实测会卡在坏态）：库默认是 `-Float.MAX_VALUE`，而那个
 *  字段是**普通 var** —— 顶栏在布局期把它改写成真实折叠量时**不触发重组**。于是
 *  [currentHeaderHeight] 首帧读到 -MAX，`52dp - (-1.2e38dp)` 让
 *  `Modifier.height(...).roundToPx()` 整型溢出成 0：顶部让位的 Spacer 变 0、内容整段顶到
 *  屏幕最上被顶栏盖住（「关于模块」页与「按键绑定」页各实证一次）。折叠页只在恰好有别的
 *  重组时自愈 ⇒ 表现为「偶尔有些页面没给 Header 让位」。
 *  初始值给「展开 − 折叠」那一档（像素）；[currentHeaderHeight] 另有一道认哨兵的兜底，双保险。
 */
@Composable
private fun SettingsPage.pageScrollBehavior(s: Screen) =
    COUIScrollBehavior(
        state =
            rememberTopAppBarState(
                initialHeightOffsetLimit =
                    with(LocalDensity.current) {
                        -(
                            TopAppBarDefaults.ExpandedHeight -
                                TopAppBarDefaults.CollapsedHeight
                        ).roundToPx().toFloat()
                    },
            ),
        // **只有首页**钉住标题（不折叠），其余全部照常折叠（用户口径：除首页外都允许折叠）。
        //  首页是这一趟的起点、内容又短，顶栏折起来只剩一条细线，退回到这里时看着像没加载完。
        //  原先「关于模块」「捐赠列表」也在不折叠名单里（理由是「短内容页没什么可折」）——
        //  用户口径改为一律可折：这两页在字体放大 / 小屏上照样会超一屏。
        //
        //  **「组合键列表」与「按键设置」原先也在这份名单里**，用户口径两页都改成允许折叠：
        //  前者五档加设置块也过一屏；后者本来就一屏多一点，折起来能多露几行。
        canScroll = {
            s != Screen.SHORTCUTS
        },
    )

/**
 * 内容区 —— 「当前屏是哪一页」那一棵 `when`，每支只做一件事：把参数喂给那一页。
 *
 *  带接线的只有「按键绑定」「场景动作」「选择应用」几支（各自的回调写在
 *  [openAppPickerForGesture] / [onDraftPicked] 里），其余是一行。
 *
 *  `CyclomaticComplexMethod` 在这类「一个大 `when`、每个分支一句常量」的地方没有意义
 *  （它的「复杂度」等于页数，加一页就报一次）—— 与 `PageTopBar.pageTitle` 同一个口径显式压掉，
 *  **不是因为改不动**。真出现需要判断的分支时，再拆。
 */
@Composable
private fun SettingsPage.PageBody(
    s: Screen,
    scrollBehavior: ScrollBehavior,
    pagers: PagePagers,
) {
    when (s) {
        Screen.SHORTCUTS -> {
            HomeScreen(scrollBehavior)
        }

        //  「一档手势一屏动作」的那一页 —— **三个入口共用同一份实现**（`BindingPagerScreen`）：
        //  这一趟在编谁、几档、开不开、写到哪儿，全由 `s` 从页面级状态里取。
        //  · BINDING      → 当前这颗键的三档（写配置）
        //  · COMBO_EDIT   → 那一档组合的短按 / 长按（写配置）
        //  · SCENE_BINDING→ 那一块场景的那几档（写草稿）
        //  Screen 值仍然分开：栈里这三个位置可能同时存在，而取样源 / 顶栏高度 / 分页器都按 Screen 索引。
        Screen.BINDING, Screen.COMBO_EDIT, Screen.SCENE_BINDING -> {
            BindingPagerScreen(s, scrollBehavior, pagers.gesturesOf(s))
        }

        Screen.COMMANDS -> {
            CommandScreen(scrollBehavior, pagers.commands)
        }

        // 「常用命令」：那一张清单（钉哪几条 / 每条叫什么 / 哪些出现在按键绑定页）。
        Screen.COMMON_COMMANDS -> {
            CommonCommandsScreen(scrollBehavior)
        }

        // 「按键设置」：**这一档**对公共项的覆盖（接口 / 振动 / 提示，组合另有无操作与等待）。
        //  闸门是 `pageWritable()`（主开关 + 链路 + 只读），**不叠加这一颗键自己的 `enabled`**：
        //  停用的键也要能进来把参数改好、再回去启用（这是常见顺序），整页灰掉等于先把顺序堵死。
        //  （上一版注释写着「另加这颗键自己的 enabled……但页面会整片灰」，代码从来没这么做过。）
        Screen.KEY_SETTINGS -> {
            KeySettingsScreen(
                scrollBehavior = scrollBehavior,
                pageEnabled = pageWritable(),
            )
        }

        // 「系统场景」：一张场景表，每块自带三档（不按手势分页，见 [BranchScreen]）。
        Screen.BRANCHES -> {
            BranchScreen(scrollBehavior)
        }

        // 挑应用。**两个入口共用这一页**（挑中的东西不一样）；点一行进下一级，
        // 二级页只记草稿（[onDraftPicked]），落盘在我们这一支的 FAB 上（[onAppPicked]）。
        Screen.APP_PICKER -> {
            AppPickerPage(scrollBehavior)
        }

        // 挑具体界面（二级页）：从「选择应用」页点某一行进来，接线见 [onDraftPicked]。
        Screen.ACTIVITY_PICKER -> {
            ActivityPickerPage(scrollBehavior)
        }

        // 「自定义按键」：**已不是一页** —— 收集改成「按键绑定」页顶栏 ⋮ 里那一项弹的
        //  对话框（见 `BindingKeyMenu` / `CollectKeyDialog`），这里不再有分支。

        // 「关于模块」：版本 / 仓库地址两行，从首页 ⋮ 菜单进
        Screen.ABOUT -> {
            AboutScreen(scrollBehavior)
        }

        // 「捐赠列表」：名单是手写常量（见 `ui/model/target/Donor.kt`）
        Screen.DONATE_LIST -> {
            DonateListScreen(scrollBehavior)
        }

        // 「按键设置」：接口选择 + 按键行为 + 组合按键（三节都是全局项）
        Screen.GENERAL -> {
            GeneralScreen(scrollBehavior)
        }

        // 「按键功能」页：按键列表 + 组合按键列表（两个清单收在一页）
        Screen.KEYS -> {
            KeysScreen(scrollBehavior)
        }

        // 「自定义变量」：一列变量 + 顶栏那颗新建；点一行弹编辑 / 删除那张窗
        Screen.VARIABLES -> {
            VariableScreen(scrollBehavior)
        }
    }
}

/**
 * **这一颗键**开没开（[KeyConfig.enabled]）—— 「按键绑定」页那片网格的闸门。
 *
 *  它与**模块总闸**是两层：总闸（`GlobalConfig.enabled`）关掉时整页被拦，判据在
 *  `LinkCheck.blockedReason` 那一处；这一颗键停用只让**它的**控件变灰，⋮ 菜单里那一行能开回来。
 *  侧键同样走这一位（以前它兼任总闸，所以「停用侧键」等于关模块 —— 已拆开）。
 */
internal fun SettingsPage.keyEnabled(id: KeyId): Boolean =
    configState.config.value
        .key(id)
        .enabled

/**
 * 「这一页现在能不能改」—— 主开关开着、链路就绪、配置不是只读。
 *
 *  首页与「按键设置」都要它：两处各写一遍时，加一条闸门（比如以后只读再加一档）就得记得改两处，
 *  漏一处的那一页会「看起来能改、点了被拦」。
 *
 *  **它是最严的那一档**，下面两个是它的两个「少一项」变体 —— 三个判据合起来只有这三份，
 *  各自为什么少一项都写在各自的 KDoc 里（原先五处各拼一遍、没有一处写理由）。
 */
internal fun SettingsPage.pageWritable(): Boolean = configState.config.value.global.enabled && linkReady && !configState.readOnly

/**
 * 「链路就绪、且这次配置读得出来」—— **不吃主开关**。
 *
 *  给「主开关关着也必须点得动」的入口用：首页 `⋮` 菜单里那几行、「重置配置」那一行、
 *  首页那张激活卡片上那颗开关。主开关关着是**用户的意图状态**，把它算进闸里就等于
 *  「关掉之后再也开不回来」—— 那颗开关自己就是靠这一位活着的。
 *
 *  `!configState.readOnly` 这一条是被真机逼出来的：只读态下刚进页时链路还没报到 ⇒ 入口是死的；
 *  等注入侧回包一到、`linkReady` 变 true，它就自己活过来，用户当场点了三下（每次都被 `write` 拒掉）。
 *  **「点了没反应」比「一开始就灰着」更难读**。
 */
internal val SettingsPage.linkSettled: Boolean
    get() = linkReady && !configState.readOnly

/**
 * 「配置这份文件写得回去」—— **只看建页那次读成功没有**，不看主开关、也不看链路。
 *
 *  给「与链路无关的读写」用：导入 / 导出配置（导出只读盘、导入只写盘，都不经注入侧），
 *  以及「免责声明还没接受」那道弹窗（它要写回 `config`，只读时写了也会被拒）。
 *
 *  只读态下 `configState.config.value` 是**默认值 + 空命令表** —— 那时导出来是一份「默认配置」
 *  还报「已导出」，比直接失败更误导（见 `ConfigTransfer` 里那条分支）。
 */
internal val SettingsPage.configWritable: Boolean
    get() = !configState.readOnly

/**
 * 「选择应用」页 —— 两个入口共用（挑中的东西写到哪儿去由 `picker` 那一组标记决定）。
 *
 * 「当前已选的那个应用」= 行尾那枚图标（箭头 / 勾 / 一块屏）+ 进页滚到它眼前，来源见
 * [appPickerSelectedTarget]：网格那条读手势，「自定义命令」那条读**被换掉的那一步**。
 */
@Composable
private fun SettingsPage.AppPickerPage(scrollBehavior: ScrollBehavior) {
    AppPickerScreen(
        // 点 FAB：把这个应用整体写下去（不指定界面）
        onPick = { picked, window -> onAppPicked(picked, window) },
        // 点某一行 → 记下在看哪个应用，再压一页挑它的界面（振动由那一行自己给，这里不再补）
        onOpenActivities = { target ->
            // 搜索词清空（页面级状态，不随页面出栈注销，见 `AppPickerState.activityFilter`）
            picker.activityFilter.value = ""
            picker.openActivities(target)
            navTo(Screen.ACTIVITY_PICKER)
        },
        scrollBehavior = scrollBehavior,
    )
}

/**
 * 「选择界面」页（二级）—— 看 `picker.activityTarget` 那个应用能打开哪些界面。
 *
 *  `selected` 给的是**完整**目标（可能带 `/类名`），二级页靠它点亮「自动」或某一个界面那一行。
 *  这一页自带搜索框，顶栏那条「搜索应用」的带子不挂（见 `PageBottomBand`）。
 */
@Composable
private fun SettingsPage.ActivityPickerPage(scrollBehavior: ScrollBehavior) {
    // 「在看哪个应用」**不在这里清**。
    //
    //  这里原来挂着一个 `DisposableEffect { onDispose { clearActivities() } }`，本意是「离开
    //  这一页就把记账收掉」。但退场动画期间这一页还活着，而用户可能已经回到上一级、点了另一个
    //  应用 —— 旧实例的 `onDispose` 于是把**新设的**目标抹掉，新页读到 null 就落进下面那个
    //  「状态丢了」分支、自己再退一次。真机日志（手快时必现）：
    //      openActivities(同一个应用) → navTo(ACTIVITY_PICKER) → clearActivities → 退回上一页
    //  按值相等去判也救不了：两次看的是同一个应用时，值天然相等。
    //
    //  现在这一位只由 `openActivities` 写（每次进页都会覆盖），宿主重进模块时由
    //  `forgetActivities` 无条件清 —— 退页时什么都不做，竞态从根上没有了。
    val target = picker.activityTarget
    if (target == null) {
        // 真丢了状态（宿主 Activity 重建后栈还在，而这一位不落盘）：别摆一个空壳页，
        // 直接退回上一页。只在**本页确实是栈顶**时才退 —— 退场途中不再补一刀。
        LaunchedEffect(Unit) {
            XLog.w("ActivityPickerPage：activityTarget 丢了，栈=$navStack → 退回上一页")
            if (navStack.lastOrNull() == Screen.ACTIVITY_PICKER) goBack()
        }
        return
    }
    LaunchedEffect(target) { XLog.w("ActivityPickerPage：进页看 $target，栈=$navStack") }
    // 该点亮哪一行：**这一趟的草稿优先**（只在看的是同一个应用时才算），否则按当前绑定反推。
    //  草稿不落盘，所以配置里还是「进来之前那一条」—— 只按配置取的话，刚挑完退回一级、
    //  再点进同一个应用，高亮会退回成首次打开时的样子（用户挑的那条看着像没记住）。
    val draft = picker.draft
    val fromDraft = draft != null && AppTarget.sameApp(draft.target, target)
    ActivityPickerScreen(
        target = target,
        selected = if (fromDraft) draft.target else appPickerSelectedTarget(),
        // 快捷方式的**身份**要单独递进去（`PickedDetail.shortcutId`）：清单还在查 / 查失败时，
        //  二级页拼不出行来，只能靠这一位原样把 `appshortcut:…` 写回去。少一位就降级成
        //  `app:<包名>` —— 一条能用的快捷方式绑定被改成永远解析不了的 token。
        selectedShortcutId = if (fromDraft) draft.shortcutId else bindingShortcutId(),
        onPick = { picked, shortcutId, label -> onDraftPicked(picked, shortcutId, label) },
        scrollBehavior = scrollBehavior,
    )
}

/**
 * 网格里点「应用控制」那一格 → 弹一层菜单（打开 / 结束），选完进**同一张**「选择应用」页。
 *
 *  三件事都得在进页那一刻做掉：记下为谁挑、清掉上一次的搜索词、「以小窗口打开」按这次要
 *  编辑的动作重算（见 [resetSmallWindowForPicker]）。父级不用记 —— 压它下面的是谁，返回就退到谁。
 *
 *  「为谁」整份带下去（[BindingTarget]）：单键那一页是「当前这颗键的这一档」，组合键那一页是
 *  「这一档组合的短按 / 长按」—— 挑完回来写哪儿由它决定（见 `PageEntryWrite.onAppPicked`）。
 */
internal fun SettingsPage.openAppPickerForTarget(target: BindingTarget) {
    picker.openForGesture(target.gesture, (target as? BindingTarget.Combo)?.id, sceneAt = target.sceneAt)
    // 搜索词清空（页面级状态，不再随页面出栈注销，见 [AppPickerState.filter]；
    // 另一条入口在 [openAppPickerForStep]）
    picker.filter.value = ""
    // 「以小窗口打开」按**这次要编辑的动作**重算（不落盘、不跨次记忆，必须在两个入口都调）
    resetSmallWindowForPicker()
    // 进页唯一复位点：点过的行回到当前绑定，定位 / 拦人窗 / 刷新指示器归零（清单不清）。
    //  绑的是快捷方式时同样按它的包名定位 —— 快捷方式属于「打开应用」那一族
    //  （菜单里那一行的勾是同一族判据，见 `ActionCatalog.isBound`）。
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId())
    navTo(Screen.APP_PICKER)
}

/**
 * 「应用控制」那一格的分派：打开走 [openAppPickerForTarget]，结束走 [openAppPickerForStopApp]。
 *
 *  两档进的是**同一张**页面，差别只在写出去的 token；这一支只是把「哪一档」翻成对应的入口，
 *  免得每个调用点（按键绑定页 / 组合键页）各写一遍 `if (stop) … else …`。
 */
internal fun SettingsPage.openAppPickerForControl(
    target: BindingTarget,
    stop: Boolean,
) {
    if (stop) openAppPickerForStopApp(target) else openAppPickerForTarget(target)
}

/**
 * 「应用控制」菜单里选了**结束**那一档 → 进同一张「选择应用」页，挑中的应用写成 `proc:stop:包名`。
 *
 *  与 [openAppPickerForTarget] 只差「这一趟写什么」：多记一位 [AppPickerState.stopApp]，
 *  其余（为谁挑、搜索词、复位、进场定位）一字不差 —— 两档共用同一页正是这么落地的。
 *
 *  小窗那一档在这里没有意义（`proc:stop:` 不是打开动作），所以**不重算、恒关**：
 *  菜单里那一行也不会出现（见 `AppPickerState.appOnly`）。
 */
internal fun SettingsPage.openAppPickerForStopApp(target: BindingTarget) {
    picker.openForStopApp(target.gesture, (target as? BindingTarget.Combo)?.id, sceneAt = target.sceneAt)
    picker.filter.value = ""
    picker.smallWindow.value = false
    //  原版动画那一档同样恒关（`proc:stop:` 没有「怎么起」这回事），与上一行同一个理由
    picker.stockAnim.value = false
    //  这一趟不重算（两条恒关），所以基线也得在这儿记 —— 否则退出页面时会拿上一趟的基线比，
    //  把「什么都没拨」判成「拨过」（见 `AppPickerState.windowTogglesChanged`）
    picker.markWindowTogglesBaseline()
    appPickerUi.beginVisit(appPickerSelectedTarget(), shortcutId = null)
    navTo(Screen.APP_PICKER)
}

/**
 * 页面级的三个横向分页器 —— 一路传下去的那个包。
 *
 *  为什么打包：这些都建在 `PageContent` 那一层（页面级，不随页重建），而用它们的是
 *  两棵树 —— 内容（`PageBody`）与顶栏（`PageTopBar` 底下那条分段控件）。原来两个 pager
 *  是各自一个参数，加第三个（组合键的短按 / 长按）时每个签名都要多一格；
 *  打成一个包之后，将来再多一个分页器只改这里与创建点。
 *
 *  各管一页，**不共用**：
 *  · [gestures] —— 「按键绑定」页的几档手势（单击 / 双击 / 长按，打开多击才多出三击 / 四击，
 *    清单见 `GestureTrigger.slotsOn`）；
 *  · [commands] —— 「自定义命令」页的类型那一排；
 *  · [combos] —— 「组合键」编辑页的短按 / 长按（长按只在不分顺序时才存在，见 `comboGestureSlots`）；
 *  · [scenes] —— 「场景动作」页的几档（组合键的场景块只有短按 / 长按两档）。
 *
 *  带子底下那三页（按键绑定 / 组合键 / 场景动作）**共用一份页面实现**
 *  （`BindingPagerScreen`），但各持一条分页器 —— 见 [PagePagers.gesturesOf]。
 *
 *  **「系统场景」页没有分页器**：翻转之后一张表里每块自带三档（见 `SceneRule`），
 *  页面不再按手势分页，原来那条 `branches` 分页器跟着手势维度一起删掉了。
 */
internal class PagePagers(
    val gestures: PagerState,
    val commands: PagerState,
    /** 「组合键」那一页短按 / 长按的分页器 —— 与 [gestures] 同一把尺子，各持一份位置。 */
    val combos: PagerState,
    /** 「场景动作」页的手势分页器 —— 页数跟着 `sceneGesturePages()` 走（键三档、组合两档）。 */
    val scenes: PagerState,
) {
    /**
     * 「一档手势一屏」那三页各用哪一条分页器。
     *
     *  **不共用一个实例**：这三页可能同时在栈里（按键绑定 → 系统场景 → 场景动作），
     *  各自要记住自己停在哪一档；共用一条会在转场时被邻居拽走。
     */
    fun gesturesOf(s: Screen): PagerState =
        when (s) {
            Screen.COMBO_EDIT -> combos
            Screen.SCENE_BINDING -> scenes
            else -> gestures
        }
}

/**
 * 几个分页器一次建好 —— 建在页面级（`PageContent`），不随页重建。
 */
@Composable
internal fun SettingsPage.rememberPagePagers(): PagePagers {
    //  按键绑定页那几档**跟着在编那颗键的开关走**（`GestureTrigger.slotsOn`）：
    //  三击 / 四击没打开就只排三页 —— 页数不再是常量，开关一拨页数当场变。
    val gestures = rememberPagerState(pageCount = { bindingSlotCount() })
    val commands = rememberPagerState(pageCount = { COMMAND_PAGE_COUNT })
    //  组合键那一页**一档一页，档数不是常量**：长按只在「不分顺序」时存在
    //  （`ComboKey.hasLong`）—— 与页里摆什么同一个来源（`comboGestureSlots`），
    //  各读一份就会演出「带子只剩一档、分页器还是两页」。
    val combos = rememberPagerState(pageCount = { comboGestureSlots().size })
    //  页数**会缩小**：先在「不分顺序」那一档停在长按页，退出去再进一档按顺序触发的组合，
    //  位置还留在第 2 页而页数只剩 1 —— 带子与内容两处都夹过一道（读出来是对的），
    //  但分页器自己停在越界的那一页时，横向那一屏是空的。所以一换页数就把位置收回来。
    //  `pageCount` 由 `rememberPagerState` 每次重组回写，这里读它等于「页数一变就跑一次」。
    LaunchedEffect(combos.pageCount) {
        val last = combos.pageCount - 1
        if (last >= 0 && combos.currentPage > last) combos.scrollToPage(last)
    }
    //  场景动作页的档数**跟当前那张表走**：一颗键三档、一组组合两档（见 `sceneGesturePages`）。
    //  读的是 `branchDraft.comboId` 这个页面级状态 —— 换了一份表，页数当场跟着变。
    val scenes = rememberPagerState(pageCount = { sceneGesturePages().size })
    //  **这个包本身也要 remember**：`PagePagers` 是普通类（按引用相等），每次重组新建一个实例
    //  就等于「分页器包换了」—— 凡是拿它当 `remember` 键的地方（路由 DSL 那一份）都会跟着重建，
    //  而库那边的路由表是按 lambda 引用记的 ⇒ 整页内容被销毁重建。包里的四个分页器本来就是
    //  `remember` 出来的，这个包只是把它们捆在一起，理应与它们同寿命。
    return remember(gestures, commands, combos, scenes) {
        PagePagers(gestures = gestures, commands = commands, combos = combos, scenes = scenes)
    }
}

/**
 * 「按键绑定」页现在排**几页** —— 在编那颗键的三档还是五档。
 *
 *  与 `bindingTargetsOf` 同一份清单（`GestureTrigger.slotsOn`）：页数与页里摆什么必须同一个判据，
 *  否则会出现「页数还是五、第四页拿到 `null` 目标」。
 */
private fun SettingsPage.bindingSlotCount(): Int {
    val cfg = configState.config.value
    val key = cfg.key(KeyWatchState.editing(cfg))
    return GestureTrigger.slotsOn(key.extraClicksOn, key.clickThenLongOn, key.doubleClickOn).size
}

// 「挑中的东西怎么落到配置里」—— 与 `PageEntry.kt` 分开：那边是**铺页面**（每页一份滚动行为、
// 顶栏与浮层），这里是「写回」三颗（拼好的 token 写哪儿去 / 应用页那颗 FAB / 界面页确认）。
// 两族改的理由不同，且合在一起时那个文件已经贴着 `TooManyFunctions` 的阈值。

/**
 * 把一个**已经拼好的 token** 写成本次挑应用的取值 —— **不负责退页**。
 *
 *  两页（应用 / 界面）最后都落到这里：应用那页拼 `app:` / `window:`，界面那页还可能拼
 *  `appshortcut:`。拼 token 的地方只剩调用点，这一层只管「写到哪儿去」。
 *
 * @return true = 写下去了；false = 没地方写（手势那条入口没记下来）或被拦
 *   （链路未就绪 / 写盘失败）。调用方据此决定留在原地。
 */
internal fun SettingsPage.writeToken(
    token: String,
    appName: String?,
): Boolean {
    // 「写到哪儿去」只有一处分派：分叉的条件 / 分叉某一支要执行的应用 / 多条命令的某一步 /
    //   手势绑定。
    return when (val target = picker.takePickTarget()) {
        is PickTarget.Branch -> {
            // 条件这一趟**可能多选**：整组包名在 `picker.pickedApps` 里（`takePickTarget()` 不动它，
            //  `clearTarget()` 才清）。空集合说明状态被谁提前清了 —— 退回 token 里那一个包名。
            val pkgs =
                picker.pickedApps.value
                    .toList()
                    .ifEmpty { listOf(appKeyOf(Action.parse(token).appTarget.orEmpty())) }
            // `takePickTarget()` 是**一次性消费**：写失败了得把「为谁挑」放回去，否则这一页
            //  立刻退化成「普通挑应用」—— 点一行会去挑界面、按确定还会写到手势绑定上。
            //  **连条件槽位一起放回**：从「条件 2」那一行进来的这一趟，重挑时必须还写那一条；
            //  只传 index 会让 branchConditionIndex 退回 -1，落到 `old.appConditionIndex(kind)`
            //  或追加，把同一块里另一条条件覆盖掉。**档位（正 / 反）也一起放回** —— 少了它，
            //  写失败重挑一次就悄悄变成「前台应用」。
            if (!writeBranchCondition(pkgs, target.index)) {
                picker.openForBranch(target.index, picker.branchConditionIndex, picker.branchKind)
                false
            } else {
                true
            }
        }

        // 「松手执行额外动作」：写这一档的松手格，然后自己退页（与分支那两条同一条纪律 ——
        //  调用方拿到 true 就不要再补一次 `goBack()`）。写哪儿见 [writeReleaseTo]。
        is PickTarget.Release -> {
            if (writeReleaseTo(target, Action.parse(token))) {
                popBackStack()
                true
            } else {
                //  写失败：把「为谁挑」**原样**放回去，留在这一页（否则这一页会退化成普通挑应用）。
                //  四位都要放回：少了 `chain`，「单击后长按」那一格的松手动作会落进长按的松手槽；
                //  少了 `stop`，「结束应用」那一趟重挑一次就变成「打开应用」。
                picker.openForRelease(
                    stop = picker.stopApp,
                    combo = target.comboId,
                    sceneAt = target.sceneAt,
                    chain = target.chain,
                )
                false
            }
        }

        // 「加一步 / 换一步」：`addStep()` / `replaceStep()` 自己会把页面切回「自定义命令」
        // （那才是这次挑应用的来处），所以这条路上调用方**不再退页**。
        //  真加进去了才算写成功：满员时 `addStep` 只 toast、不发车，这里若照样返回 true，
        //  调用方会当作「已落盘」直接 return，刚挑的应用就凭空消失。
        is PickTarget.Step -> {
            if (target.index >= 0) replaceStep(target.index, token) else addStep(token)
        }

        // 「常用命令」那张清单：挑中的应用 / 结束应用追加成一条、或换掉「修改」那一趟指的那一条，
        //  然后自己退页（同 [PickTarget.Release] 的纪律 —— 调用方拿到 true 就不要再补一次 `goBack()`）。
        is PickTarget.CommonCommand -> {
            writeCommonCommand(Action.parse(token), target.replaceToken)
        }

        // 写的是**进这一页时那个手势**（见 picker.gesture），不是 pager 的当前页
        // —— 这一页不属于 pager。
        null -> {
            val g =
                picker.gesture ?: run {
                    //  **别静默返回**：正常路径由 [openAppPickerForGesture] 先记下手势，但经
                    //  调试通道直接 `navTo(APP_PICKER)` 时没有那一步 —— 那时这一页的确认会
                    //  一声不响地什么都不做（用户看到的就是「FAB 点了没反应」）。
                    XLog.w("选择应用：这一趟没有目标手势（gesture=null），确认无效 —— 请从「按键绑定」页那一格进来")
                    return false
                }
            // 写回**进这一页时那一格**：场景动作页（`picker.sceneAt` 非负）写那一块的这一档，
            //  组合键那一页（`picker.comboId` 非空）写它那一档，否则写当前在编辑的那颗键。
            //  判据只有这两处 —— 不按「当前在哪一页」判：退场动画期间栈顶可能已经变了，
            //  按页面判会把动作写到上一页那颗键上。三选一那一支见 [pickTargetOf]。
            pickToken(
                token,
                pickTargetOf(
                    gesture = g,
                    sceneAt = picker.sceneAt,
                    comboId = picker.comboId,
                    scene = { BindingTarget.Scene(branchDraftNow(), picker.sceneAt, g, branchDraft.comboId) },
                    key = BindingTarget.Key(KeyWatchState.editing(configState.config.value), g),
                ),
                appName,
            )
        }
    }
}

/**
 * 「写回哪一格」的三选一 —— 判据只有 [sceneAt] / [comboId] 两处（见 `writeToken` 里那段说明）。
 *
 *  单独一支：那一位是**落点表的分派器**，每多一个落点必然多一个分支，把三选一的判定
 *  也摊在里面会搅成一团。这里不碰页面状态
 *  （[scene] / [key] 由调用点现算，用 lambda 只为在走不到时不算 [key]）。
 */
private fun pickTargetOf(
    gesture: GestureTrigger,
    sceneAt: Int,
    comboId: String?,
    scene: () -> BindingTarget,
    key: BindingTarget,
): BindingTarget {
    val combo = comboId?.let { BindingTarget.Combo(it, gesture) }
    return when {
        sceneAt >= 0 -> scene()
        combo != null -> combo
        else -> key
    }
}

/**
 * 「选择应用」页点 FAB —— 把**当前选中**的那一条写下去。
 *
 *  **这是整趟挑应用唯一的落盘点**：二级页点确认只写 [AppPickerState.draft]（草稿），
 *  到这一颗才真正写配置。所以 token 优先取草稿 —— 有草稿说明用户刚在二级页挑过，
 *  照它写 `app:包名/类名` / `appshortcut:`；没有草稿才是「就绑这个应用」（`app:包名`）。
 *
 *  写成了才退页：被拦（链路未就绪）或写盘失败时留在原地，
 *  否则用户挑的东西看起来像存好了，其实一个字节都没写。
 */
internal fun SettingsPage.onAppPicked(
    picked: PickedDetail,
    window: Boolean,
) {
    tickNow()
    // `forStep` 必须在写之前读：`writeToken` 里那个 `takePickTarget()` 会把它清掉
    val forStep = picker.forStep
    // 分叉那两条路同理（条件 / 那一支要执行的应用），而且它们**自己会退页**
    //  （见 `writeBranchCondition`）—— 这里不能再补一次
    //  `goBack()`，否则一次退两层、还会把「放弃未保存的命令」弹出来。
    val forBranch = picker.forBranch
    // 「松手执行额外动作」那一趟同样自己退页（`PickTarget.Release` 分支里 `popBackStack()`），
    //  这里再补一次 `goBack()` 会一次退两层、直接回到首页。
    val forRelease = picker.forRelease
    // 「常用命令」那一趟同理（见 `PickTarget.CommonCommand`）。
    val forCommonCommand = picker.forCommonCommand
    // 「应用控制」→ 结束那一趟：写的是 `proc:stop:包名`（不挑界面、也没有小窗）
    val stopApp = picker.stopApp
    // 刚在「选择界面」页确认过就照那一条写（`app:包名/类名` / `appshortcut:`）；
    // 没确认过就照 `pending` 这一份写 —— 它带着快捷方式的 id，不能退化成 `app:包名/id`。
    //  给「条件执行」挑的那一趟**没有二级页**（不挑界面），所以那边一律按 `pending` 写：
    //  草稿里若留着上一趟的 `包名/类名`，照它写会把条件弄成「某个界面」，而条件只认包名。
    //  给**分支动作**挑的那一趟不在此列 —— 它与网格同一套（草稿 + 小窗都算数）。
    val forCondition = picker.branchTarget != AppPickerState.NOT_FOR_BRANCH
    //  「以小窗口打开」与「使用原版动画」是同一格的两种起法（互斥）：`window` 由调用点带货
    //  （「不支持小窗」那张窗会强制传 false，不走页面状态，所以这里读参数而不是再读一遍），
    //  `stockAnim` 只有页面状态这一个来源。
    val stockAnim = picker.stockAnim.value
    val token =
        when {
            stopApp -> Action.Process(AppTarget.pkg(picked.target)).token
            forCondition -> picked.token(window = false)
            else -> picker.draft?.token(window, stockAnim) ?: picked.token(window, stockAnim)
        }
    // 名字与绑定**同一次写盘**（`Config.appNames`）—— 分两次写要多落一次盘 + 一次广播，
    //  而中间那一下读到的配置只有新绑定、没有名字，顶栏会当场闪一下包名。
    //  快捷方式带的是它自己的名字（「扫一扫」），其余按目标现取应用名。
    val name = picker.draft?.label ?: picked.label ?: appNameOf(picked.target)
    //  **这一笔账在写之前清**：`writeToken` 里那两条分支路径会当场 `popBackStack()` 退回来源页，
    //  来源页的 effect 立刻读 `resumeSheetAt` 把抽屉支起来 —— 写成功后再清已经晚了一帧，
    //  真机上就是「点了确认、抽屉照样弹出来」（用户口径）。
    //  没写成的极小概率（配置只读 / 链路未就绪）会少一次「回来支抽屉」，那也比每次确认都弹好。
    picker.cancelResume()
    if (!writeToken(token, name)) {
        XLog.w("选择应用：${picked.target} 没写进绑定，留在本页（不 goBack）")
        return
    }
    //  「这几趟自己会退页」合成一位：它们本来就是同一件事（这个 `writeToken` 自己
    //  `popBackStack` 了，调用方别再补 `goBack()`）。
    val popsItself = forBranch || forRelease || forCommonCommand
    if (forStep || popsItself) return
    picker.clearTarget()
    //  「以小窗口打开」/「使用原版动画」也**已经随这一条写下去了**，所以把进页那一刻的基线挪到当下：
    //  不挪的话，`goBack()` 的 `hasUnconfirmedPick` 仍在拿这两个开关与进页基线比（草稿刚被
    //  `clearTarget` 清掉，只剩这一条还能为真）—— 用户拨过开关、再点确认，写盘成功了却当场
    //  弹一张「放弃这次选择」。
    picker.markWindowTogglesBaseline()
    goBack()
}

/**
 * 「松手执行额外动作」写到这一档 —— 三种落点只差「写给谁」，判据在 [pick] 里。
 *
 *  · 场景块（`sceneAt >= 0`）：写**草稿里那一块**，与这一页的三档动作同一个规矩（点「确认」才落盘）；
 *  · 其他：写那颗键 / 那一档组合键的 `longRelease`，当场落盘。
 *
 *  之前这里只有后面那一支（`writeLongReleaseTo`），于是从「场景动作」页进来挑的应用被写进了
 *  **整颗键**的松手格，草稿里那一格还是空的 —— 返回后那一行照样显示「无操作」。
 *
 *  @return 写下去没有（场景块那一支返回「那一块还在不在」；另一支返回「链路未就绪 / 写盘失败」）
 */
private fun SettingsPage.writeReleaseTo(
    pick: PickTarget.Release,
    action: Action,
): Boolean {
    //  「单击后长按」那一档的松手格自成一路（`clickThenLongRelease`）：挑法、清单、回填
    //  与长按的松手共用，只有落点不同 —— 所以只在落点这里分一次
    if (pick.chain) {
        //  场景块走草稿（与 `writeReleaseTo` 的长按那一支同一条纪律：这一页只改草稿）
        if (pick.sceneAt >= 0) {
            val block = BindingTarget.Scene(branchDraftNow(), pick.sceneAt, GestureTrigger.ClickThenLong, pick.comboId)
            val ok = block.bindChainRelease(action = action)
            XLog.w("额外动作：${block.what} ← ${action.token}（草稿）${if (ok) "" else "（那一块已不在草稿里）"}")
            return ok
        }
        //  组合键那一侧：写这一档的**成员「单击后长按」松手格**（`ComboKey.memberClickThenLongRelease`）。
        //  与单键那一支同一件事、另一张表 —— 组合没有「这颗键」，动作挂在组合自己身上。
        if (pick.comboId != null) {
            val cfg = configState.config.value
            val combo = cfg.combos.firstOrNull { it.id == pick.comboId }
            if (combo == null) {
                XLog.w("额外动作：组合 ${pick.comboId} 已不在配置里，忽略")
                return false
            }
            val ok =
                write(
                    cfg.copy(
                        combos =
                            cfg.combos.map {
                                if (it.id == combo.id) it.copy(memberClickThenLongRelease = action) else it
                            },
                    ),
                    "member_click_then_long_release combo=${combo.id}",
                ) != CommandsSave.Failed
            XLog.w("额外动作：组合 ${combo.id} 的成员单击后长按松手 ← ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
            return ok
        }
        val cfg = configState.config.value
        val id = KeyWatchState.editing(cfg)
        val key = cfg.key(id)
        val ok =
            write(
                cfg.copy(keys = cfg.keys + (id to key.copy(clickThenLongRelease = action))),
                "click_then_long_release key=${id.id}",
            ) != CommandsSave.Failed
        XLog.w("额外动作：单击后长按松手 ← ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
        return ok
    }
    if (pick.sceneAt >= 0) {
        val block = BindingTarget.Scene(branchDraftNow(), pick.sceneAt, Gesture.LONG.trigger(), pick.comboId)
        val ok = block.bindRelease(action = action)
        XLog.w("额外动作：${block.what} ← ${action.token}（草稿）${if (ok) "" else "（那一块已不在草稿里）"}")
        return ok
    }
    return writeLongReleaseTo(pick.comboId, action)
}

/**
 * 「松手执行额外动作」写到这一颗键 / 这一档组合 —— 两档**只差写到哪张表**，判据在 `BindingTarget` 里。
 *
 *  之前这里是 `if (comboId != null) writeComboLongRelease(…) else writeLongRelease(…)`：
 *  两个函数、两张表各写一遍同一件事。现在两条路合成一条，都走 `withLongPress`。
 *
 *  @param comboId 这一趟绑的是哪一档组合键（`null` = 当前在编辑的那颗键）
 *  @return 写下去没有（`false` = 链路未就绪 / 写盘失败）
 */
private fun SettingsPage.writeLongReleaseTo(
    comboId: String?,
    action: Action,
): Boolean {
    val cfg = configState.config.value
    val target =
        if (comboId != null) {
            BindingTarget.Combo(comboId, Gesture.LONG.trigger())
        } else {
            BindingTarget.Key(KeyWatchState.editing(cfg), Gesture.LONG.trigger())
        }
    val ok =
        write(target.withLongPress(cfg, LongPressEdit(release = action)), "${target.what}:long_release") !=
            CommandsSave.Failed
    XLog.w("额外动作：${target.what} ← ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
    return ok
}

/**
 * 往「常用命令」那张清单里**追加一条**（见 [PickTarget.CommonCommand]）。
 *
 *  与抽屉里那一支同一个口径：已经在清单里就**不重复收录**，但这一趟仍然算成功 —— 用户点的是
 *  「把它加进来」，它本来就在里面，结果与期待一致（再报一句「已经有了」只会让人以为没生效）。
 *
 *  整份写回（`setCommonCommands` 同一套）：这张表本来就小，按 token 打补丁要额外处理中间态。
 *
 * @return 写下去没有（`false` = 这条动作根本不能执行 / 链路未就绪 / 写盘失败）
 */
internal fun SettingsPage.addCommonCommand(action: Action): Boolean {
    //  入口格那种「标记」写法（`appctl:open`）不是能执行的动作，读盘那一道也会把它丢掉
    if (action is Action.None) return false
    val cfg = configState.config.value
    val list = cfg.global.commonCommands
    if (list.any { it.token == action.token }) return true
    //  已经有这条就先进了上面那一支（「本来就在里面」不算新增）：上限只拦**真的多一条**。
    if (list.size >= GlobalConfig.MAX_COMMON_COMMANDS) {
        XLog.w("常用命令：已到上限（${GlobalConfig.MAX_COMMON_COMMANDS} 个），拒绝添加 ${action.token}")
        hint(I18n.format(Strings.COMMON_COMMANDS_LIMIT, GlobalConfig.MAX_COMMON_COMMANDS))
        return false
    }
    val ok =
        write(
            cfg.copy(global = cfg.global.copy(commonCommands = list + CommonCommand(token = action.token))),
            "common_commands=add",
        ) != CommandsSave.Failed
    XLog.w("常用命令：← ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
    return ok
}

/**
 * 把**内置命令**补进「自定义命令」与「常用命令」两张表 —— 用户在内置选择窗里点确认时走这一支。
 *
 *  ## 两笔写、两个去重
 *
 *  1) **命令库**：走 [mergeCommandSeeds]（正文相同 / 同名都跳过），它是「预设补进命令库」那条规则的
 *     唯一实现。新增了才落盘，没有新增就整段不写 —— 免得每次点确认都把文件重写一遍。
 *  2) **常用命令**：按 `cmd:<命令名>` 引用去重（清单里已有这一条就不再钉），并受
 *     [GlobalConfig.MAX_COMMON_COMMANDS] 收口。名字取自**合并后**的命令库，所以「用户早就自己建过
 *     同一条、名字不同」时钉的是他那条现在的名字，而不会指向一条不存在的命令。
 *
 *  顺序是**先命令库、后主配置**：配置写失败时命令仍在命令页可见，只是没钉上；反过来先写配置的话，
 *  命令库失败会留下指向不存在命令的清单项。
 *
 *  @return 有没有出过错（`false` = 命令库写失败，清单那一步没做）
 */
internal fun SettingsPage.addBuiltInCommands(seeds: List<CommandSeed>): Boolean {
    if (seeds.isEmpty()) return false
    val before = configState.commands.value
    val merged = mergeCommandSeeds(before, seeds.map { it.command })
    val added = merged.size - before.size
    if (added > 0 && saveCommands(merged, "builtin=add=$added") == CommandsSave.Failed) return false
    //  **按正文在合并后的库里找**：用户可能早就手写过同一条（名字不同），钉的该是他那一条。
    val bodies = merged.associateBy { it.single.trim() }
    val cfg = configState.config.value
    val existing = cfg.global.commonCommands
    val tokens = existing.mapTo(HashSet()) { it.token }
    val pins =
        seeds
            .asSequence()
            .mapNotNull { seed -> bodies[seed.command.single.trim()]?.let { it to seed.icon } }
            .filter { (cmd, _) -> Command.PREFIX + cmd.name !in tokens }
            .distinctBy { (cmd, _) -> cmd.name }
            .take((GlobalConfig.MAX_COMMON_COMMANDS - existing.size).coerceAtLeast(0))
            .map { (cmd, icon) ->
                CommonCommand(token = Command.PREFIX + cmd.name, showInBinding = true, icon = icon)
            }.toList()
    if (pins.isNotEmpty()) {
        val ok =
            write(
                cfg.copy(global = cfg.global.copy(commonCommands = existing + pins)),
                "common_commands=builtin=${pins.size}",
            ) != CommandsSave.Failed
        if (!ok) return false
    }
    XLog.w("内置命令：命令库 +$added，常用命令 +${pins.size}（跳过 ${seeds.size - pins.size}）")
    val skipped = seeds.size - pins.size
    hint(
        if (skipped > 0) {
            I18n.format(Strings.COMMON_COMMANDS_BUILTIN_SKIPPED, pins.size, skipped)
        } else {
            I18n.format(Strings.COMMON_COMMANDS_BUILTIN_ADDED, pins.size)
        },
    )
    return true
}

/**
 * 把「常用命令」清单里的某一条**换成另一个动作**（行菜单的「修改」→ 抽屉 → 挑一条）。
 *
 *  与 [addCommonCommand] 分开：那边是**多一条**（还要吃上限），这边是**换这一条**（条数不变）。
 *  名字 / 图标 / 固定位都留着 —— 改的是它指向什么，不是用户给它起的名字与挑的脸。
 *
 *  **不许撞上清单里已有的另一条**：`token` 是这张表的身份（改名 / 换图标都按它找行），
 *  两条同一个 token 会一起被改。撞上了就当作「已经是这一条」，不写、也不报错
 *  （与添加那条「本来就在里面就算成功」同一口径）。
 *
 * @param from 被换掉的那一条的 token
 * @return 写下去没有（`false` = 这条动作根本不能执行 / 链路未就绪 / 写盘失败）
 */
internal fun SettingsPage.replaceCommonCommand(
    from: String,
    action: Action,
): Boolean {
    //  入口格那种「标记」写法不是能执行的动作，读盘那一道也会把它丢掉
    if (action is Action.None) return false
    val cfg = configState.config.value
    val list = cfg.global.commonCommands
    if (action.token == from) return true
    if (list.any { it.token == action.token }) return true
    val ok =
        write(
            cfg.copy(
                global =
                    cfg.global.copy(
                        commonCommands = list.map { row -> if (row.token == from) row.copy(token = action.token) else row },
                    ),
            ),
            "common_commands=replace",
        ) != CommandsSave.Failed
    XLog.w("常用命令：$from → ${action.token}（${if (ok) "已落盘" else "没写进去"}）")
    return ok
}

/**
 * 「常用命令」那一趟：追加进清单（[replaceToken] 非空时是换掉那一条），成功就自己退页
 * （见 [PickTarget.CommonCommand]）。
 *
 *  为什么单独一支：`writeToken` 那个 `when` 已经把落点分派摊开了，把「写 + 退页 + 写失败
 *  放回目标」再摊在那里会搅成一团；而这三种结局本来就该在一处读。
 *
 * @param replaceToken 非空 = 换掉清单里的这一条（「修改」那一趟）
 * @return 写下去了没有（`false` = 没地方写 / 这条动作不能执行 / 写盘失败，调用方留在原地）
 */
private fun SettingsPage.writeCommonCommand(
    action: Action,
    replaceToken: String? = null,
): Boolean {
    val ok = if (replaceToken != null) replaceCommonCommand(replaceToken, action) else addCommonCommand(action)
    if (ok) {
        //  **这一趟做了什么就说什么**：这两支原先一声不响地退页 —— 回到清单页只看见多了一行 /
        //  换了一行，而「已经加进去了」「换的是哪一条」全靠用户自己认。与固定 / 改名 / 换图标
        //  那几处同一套说法：动作做成之后报动作，不报「已保存」。
        hint(
            I18n.get(
                if (replaceToken != null) Strings.COMMON_COMMANDS_EDIT_DONE else Strings.COMMON_COMMANDS_ADDED,
            ),
        )
        popBackStack()
        return true
    }
    //  写失败：把「为谁挑」放回去，否则这一页会退化成普通挑应用（点一行就绑到手势上）
    picker.openForCommonCommand(picker.stopApp, replaceToken)
    return false
}

/**
 * 「选择界面」页点确认 —— **只记草稿，不写配置**。
 *
 *  这一页是「挑」，落盘在上一级（「选择应用」页那颗 FAB）。所以这里做两件事：
 *   · 把挑中的那一条存进 [AppPickerState.draft]（连同「以小窗口打开」那一档，等提交时再拼 token）；
 *   · 退回一级页，并把那一页的选中也挪过来 —— 行尾图标要立刻显示成「一块屏」。
 *
 *  用户在这之后按返回走人，配置一个字节都不会动（草稿随这一趟结束一起作废，
 *  见 [AppPickerState.clearTarget]）。
 */
internal fun SettingsPage.onDraftPicked(
    target: String,
    shortcutId: String?,
    label: String?,
) {
    tickNow()
    val detail = PickedDetail(target, shortcutId, label)
    // 「这一条吃不到原版动画」不在这里判：开关该不该拨回关要到**上一级那颗 FAB** 才说得准
    //  （那一处与小窗共用一份判据，见 `AppConfirmFab.blockedToggleOf`）。
    // 草稿与「选中位」装同一份：行尾靠它的 shortcutId 画 table-shortcut，FAB 靠它取目标
    picker.draft = detail
    appPickerUi.pending = detail
    goBack()
}

/**
 * 网格里点「自定义变量」那一格 → 进「自定义变量」列表页（**为这一格**挑一步）。
 *
 *  与 `openAppPickerForTarget` 一样先记下「为谁挑」，但**不碰挑应用那几笔账**
 *  （搜索词 / 小窗口 / 列表选中复位）—— 那些只属于那一页。
 *
 *  变量只有**那一个页面**：两趟入口的区别只写进 `VarManageState.enter`（这一趟是「挑一步」），
 *  点列表里的一颗才会弹那张操作窗，挑完落成一步。
 */
internal fun SettingsPage.openVarOpsForTarget(target: BindingTarget) {
    picker.openForGesture(target.gesture, (target as? BindingTarget.Combo)?.id, sceneAt = target.sceneAt)
    VarManageState.enter(VarManageState.Mode.Step)
    navTo(Screen.VARIABLES)
}

/**
 * 「添加 / 选择命令」抽屉里点「自定义变量」那一行 → 进同一页，挑完**落成一步**
 * （[replaceIndex] `< 0` = 追加一步，否则换掉那一步）。
 */
internal fun SettingsPage.openVarOpsForStep(replaceIndex: Int) {
    picker.openForStep(replaceIndex)
    VarManageState.enter(VarManageState.Mode.Step)
    navTo(Screen.VARIABLES)
}

// 建页 → 首帧，以及把页面 View 交出去。
//
// 「等数据到位」与「画整棵树」拆成两档（[Page] / [PageContent]）是有原因的：
//  建页要读的三份文件（配置 / 命令库 / 界面偏好）现在在组合期、IO 线程上读（见 [loadPageState]），
//  读回来之前页面状态里全是**默认值**（主开关关、三个手势全是无操作、命令库空）。
//  这一档必须**整棵页面树都不组合**，不能只把内容藏起来：
//   · 藏起来（alpha=0 / size=0）仍然会组合 —— 副作用照跑（链路自检接收器、调试通道、
//     换页收浮窗），而且首帧会先按默认值画出「模块未开」的界面、几十毫秒后再翻成真值，
//     用户看到的是开关自己弹一下；
//   · 整棵不组合时，等的那几毫秒露出来的是 [PageSplash] 那一屏底色 —— 与 `ComposeView`
//     自己刷的底色是同一个值（见 [createView]），所以中间那一帧看不出来。

@Composable
internal fun SettingsPage.Page() {
    // 键是 Unit：整页只跑一次。`loaded` 翻 true 会重跑本函数，但这里不会重启。
    //  载入失败不能把宿主 `:ui` 进程带走（在组合协程里抛出去就是未捕获异常）：兜住并留一行 w，
    //  页面会停在启动底色上，排查时看得到这句话。
    LaunchedEffect(Unit) {
        //  **失败重试一次**：读盘 / 反混淆偶发失败（正好撞上另一个进程在写）时，一次就放弃会把
        //  整页永久停在启动底色上 —— 用户看到的是「点进来一片空白」，只能杀进程再来。
        //  两次都不成才留那一行错误日志（界面仍然只有底色，但没有比让它卡死更糟的选择）。
        repeat(LOAD_ATTEMPTS) { attempt ->
            try {
                loadPageState()
                return@LaunchedEffect
            } catch (e: Throwable) {
                //  载入失败不能把宿主 `:ui` 进程带走（组合协程里的未捕获异常会直接崩）。
                if (attempt == LOAD_ATTEMPTS - 1) {
                    XLog.w("载入页面状态失败（已重试，页面停在启动底色）：${e.javaClass.simpleName}", e)
                } else {
                    XLog.w("载入页面状态失败，重试一次：${e.javaClass.simpleName}")
                    delay(LOAD_RETRY_MS)
                }
            }
        }
    }
    // 系统栏顶边在**首帧是 0**（注入进来的 ComposeView 还没收到 insets），而顶栏与内容的顶部
    //  让位都按它算 ⇒ 那一帧内容整体偏上约 40dp，下一帧才落回（真机录屏：让位 107dp → 165dp）。
    //  所以这一帧先只铺底色（与页面底色同值，看不出来），等 inset 到了再画整棵树。
    //  兜底：某些全屏 / 无状态栏场景它恒为 0，不能因此一直不画 —— 150ms 后无论如何都画。
    val barTop = with(LocalDensity.current) { WindowInsets.systemBars.getTop(this).toDp() }
    var insetsReady by remember { mutableStateOf(false) }
    var forced by remember { mutableStateOf(false) }
    SideEffect {
        if (barTop > 0.dp) insetsReady = true
    }
    LaunchedEffect(Unit) {
        delay(150)
        forced = true
    }
    if (configState.loaded && (insetsReady || forced)) {
        PageContent()
    } else {
        PageSplash()
    }
}

/** 读盘还没回来时铺的那一屏 —— 与 `ComposeView` 的底色同值，见 [Page] 的注释。 */
@Composable
private fun SettingsPage.PageSplash() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(pageBackgroundColor)),
    )
}

/**
 * 把模块页面建出来交给 hook 侧塞进宿主的容器里。
 *
 * 这里**一次读盘都不做** —— 建页要的三份文件（配置 / 命令库 / 界面偏好）归 [loadPageState]，
 * 由 [Page] 在组合期放到 IO 线程上读。这个方法只做三件不碰盘的事：装字典订阅、定深浅、
 * 把 `ComposeView` 与它需要的 owner 搭起来。
 */
internal fun SettingsPage.createView(): View? {
    // 先把字典订阅装上：配置里的语言档不在这一步读（现在是异步的），
    //  读回来之后由 `applyPageLoad` 换 `I18n.currentLanguage`（见 `ui/chrome/PageLoad.kt`）。
    //  订阅必须早于那次换档 —— 晚装的话，换档发生在界面层那份状态接上之前，
    //  首帧就会停在上一个语言的文案上。字典本体在 core（普通 Map），订阅这一半必须在界面层
    //  —— 见 `ui/state/host/I18nBridge.kt`。
    installI18nBridge()
    // 深浅是 binder 读一次系统全局配置（不是读盘），首帧底色与 [PageSplash] 都要它，留在同步路径上。
    chrome.dark = systemNight()

    val view =
        ComposeView(context).apply {
            // 首帧之前先把底色铺成页面底色，避免「先黑一下再画出来」
            setBackgroundColor(pageBackgroundColor or 0xFF000000.toInt())
            // 当前有焦点的 View** —— 手机是触摸模式，一个没打开 `isFocusableInTouchMode`
            // 的 View 拿不到 Android 焦点，事件就永远到不了 Compose（Compose 的按键处理挂在
            // `AndroidComposeView.dispatchKeyEvent` 上，见 `ActionTile` 的注释）。
            isFocusable = true
            isFocusableInTouchMode = true
            setContent { Page() }
        }
    viewTree.rootView = view

    // 自建 owner（宿主那棵树一个都没有）并先挂到自己身上；
    // 祖先链由 hook 侧在 `container.addView` **之后**回调 [PageViewTree.attachOwnersUpward] 挂。
    //
    //  宿主三件套齐全时 [PageViewTree.ownOwners] 返回 null —— **绝不能用占位实例去挂**：`attachOne`
    //  是「只挂不覆盖」，此刻 `view` 还没进树（`parent == null`），占位实例会被钉在
    //  `ComposeView` 自身上，之后 `attachOwnersUpward` 换不掉它，Compose 于是拿到那份
    //  停在 `INITIALIZED` 的 lifecycle ⇒ 页面一片空白。挂了 null 就让 Compose 沿
    //  parent 链自己去宿主那棵树上找（那正是它该走的路）。
    val own =
        viewTree.ownOwners(
            activity as? androidx.lifecycle.LifecycleOwner,
            activity as? androidx.lifecycle.ViewModelStoreOwner,
            activity as? androidx.savedstate.SavedStateRegistryOwner,
        )
    viewTree.pendingOwners = own
    if (own != null) viewTree.attachOne(view, own)

    XLog.w(
        "设置页用 COUI（Compose）渲染：night=${chrome.dark}" +
            " surface=#${Integer.toHexString(pageBackgroundColor)}",
    )
    return view
}

/** 建页读盘最多试几次（见 [Page] 里那段重试）。 */
private const val LOAD_ATTEMPTS = 2

/** 两次读盘之间等多久 —— 另一个进程正好在写盘时，让它先写完。 */
private const val LOAD_RETRY_MS = 250L
