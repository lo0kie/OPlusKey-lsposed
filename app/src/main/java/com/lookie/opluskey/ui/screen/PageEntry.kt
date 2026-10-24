package com.lookie.opluskey.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.LocalHeaderBoxHeight
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.headerReady
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.screen.about.AboutScreen
import com.lookie.opluskey.ui.screen.apppicker.ActivityPickerScreen
import com.lookie.opluskey.ui.screen.apppicker.AppPickerScreen
import com.lookie.opluskey.ui.screen.binding.BindingScreen
import com.lookie.opluskey.ui.screen.command.BranchScreen
import com.lookie.opluskey.ui.screen.command.CommandActions
import com.lookie.opluskey.ui.screen.command.CommandScreen
import com.lookie.opluskey.ui.screen.donate.DonateListScreen
import com.lookie.opluskey.ui.screen.general.GeneralScreen
import com.lookie.opluskey.ui.screen.home.HomeScreen
import com.lookie.opluskey.ui.state.BandHeight
import com.lookie.opluskey.ui.state.KeyWatchState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    pagerState: PagerState,
    commandPager: PagerState,
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
        onDispose { chrome.hazeStatesByPage.remove(s) }
    }
    //  这一页组合完成时若它正是栈顶，就把取样源摆过来 —— 与 [PageEffects] 里那个
    //  `LaunchedEffect(navStack.lastOrNull())` 互为兜底（那边可能早一帧，见它自己的注释）。
    //  幂等：同一个实例写回去不会触发重组。
    LaunchedEffect(s) {
        if (navStack.lastOrNull() == s) chrome.pageHazeState = hazeState
    }
    val headerBand = remember { BandHeight() }
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
                PageBody(s, scrollBehavior, pagerState, commandPager)
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
            val toolbarModifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = COMMAND_TOOLBAR_BOTTOM)
            when (s) {
                Screen.COMMANDS -> CommandActions(toolbarModifier)
                else -> Unit
            }

            // 顶栏：叠在内容**之上**（`Box` 里后声明的画在上面），
            // 半透明底 + 模糊从它底下滚过去的内容 —— 见 [PageHeader]。
            PageTopBar(s, scrollBehavior, hazeState, pagerState, commandPager)
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
 *  「首页」「关于模块」「捐赠列表」的标题**不折叠**（定案）：库的开关是 `canScroll` ——
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
        // 「首页」「关于模块」「捐赠列表」三页钉住标题（不折叠），其余几页照常折叠。
        //  后两页本来就是「短内容」页，没什么可折的。
        //  （名单长起来之后要放开折叠，改的就是这一行。）
        canScroll = {
            s != Screen.SHORTCUTS &&
                s != Screen.ABOUT &&
                s != Screen.DONATE_LIST &&
                s != Screen.GENERAL
        },
    )

/**
 * 内容区 —— 「当前屏是哪一页」那一棵 `when`，每支只做一件事：把参数喂给那一页。
 *
 *  六页里只有「按键绑定」与「选择应用」两支带接线（各自的回调写在
 *  [openAppPickerForGesture] / [onDraftPicked] 里），其余四支是一行。
 */
@Composable
private fun SettingsPage.PageBody(
    s: Screen,
    scrollBehavior: ScrollBehavior,
    pagerState: PagerState,
    commandPager: PagerState,
) {
    when (s) {
        Screen.SHORTCUTS -> {
            HomeScreen(scrollBehavior)
        }

        Screen.BINDING -> {
            BindingScreen(
                pagerState,
                // 闸门是**当前在编辑的那颗键自己的** `enabled`（侧键也有一份，⋮ 菜单里能停用）：
                //  模块总闸另有一层（关着时整页走 `blockedReason`），这里只管这一颗键。
                //  自定义键收集时默认不开，第一次绑动作时自动打开（见 `pick`）。
                enabled = keyEnabled(KeyWatchState.editing(configState.config.value)),
                onOpenAppPicker = { g -> openAppPickerForGesture(g) },
                scrollBehavior = scrollBehavior,
            )
        }

        Screen.COMMANDS -> {
            CommandScreen(scrollBehavior, commandPager)
        }

        // 「条件执行」：与「自定义命令」页同一套表单骨架，只是固定成 BRANCH 那一档，
        //  没有类型选择器带子（见 [BranchScreen]）。
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

        // 「捐赠列表」：名单是手写常量（见 `ui/model/Donors.kt`）
        Screen.DONATE_LIST -> {
            DonateListScreen(scrollBehavior)
        }

        // 「通用设置」：接口选择 + 按键行为（两节都是全局项）
        Screen.GENERAL -> {
            GeneralScreen(scrollBehavior)
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
 *  首页与「通用设置」都要它：两处各写一遍时，加一条闸门（比如以后只读再加一档）就得记得改两处，
 *  漏一处的那一页会「看起来能改、点了被拦」。
 */
internal fun SettingsPage.pageWritable(): Boolean = configState.config.value.global.enabled && linkReady && !configState.readOnly

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
 * 网格里点「打开应用」那一格 → 进「选择应用」页（**为当前手势**挑）。
 *
 *  三件事都得在进页那一刻做掉：记下为谁挑、清掉上一次的搜索词、「以小窗口打开」按这次要
 *  编辑的动作重算（见 [resetSmallWindowForPicker]）。父级不用记 —— 压它下面的是谁，返回就退到谁。
 */
private fun SettingsPage.openAppPickerForGesture(g: Gesture) {
    picker.openForGesture(g)
    // 搜索词清空（页面级状态，不再随页面出栈注销，见 [AppPickerState.filter]；
    // 另一条入口在 [openAppPickerForStep]）
    picker.filter.value = ""
    // 「以小窗口打开」按**这次要编辑的动作**重算（不落盘、不跨次记忆，必须在两个入口都调）
    resetSmallWindowForPicker()
    // 进页唯一复位点：点过的行回到当前绑定，定位 / 拦人窗 / 刷新指示器归零（清单不清）
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId())
    navTo(Screen.APP_PICKER)
}
