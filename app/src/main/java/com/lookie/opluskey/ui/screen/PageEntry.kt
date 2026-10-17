package com.lookie.opluskey.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.model.SNACKBAR_SHADOW_ROOM
import com.lookie.opluskey.ui.replaceStep
import com.lookie.opluskey.ui.screen.about.AboutScreen
import com.lookie.opluskey.ui.screen.apppicker.AppPickerScreen
import com.lookie.opluskey.ui.screen.binding.BindingScreen
import com.lookie.opluskey.ui.screen.binding.pick
import com.lookie.opluskey.ui.screen.command.CommandActions
import com.lookie.opluskey.ui.screen.command.CommandScreen
import com.lookie.opluskey.ui.screen.donate.DonateListScreen
import com.lookie.opluskey.ui.screen.home.HomeScreen
import com.lookie.opluskey.ui.tickNow
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Snackbar
import io.github.suqi8.coui.kmp.basic.SnackbarHost
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

    Box(
        Modifier
            .fillMaxSize()
            // 每个页面自带不透明底：转场交叠时不透出下层残影
            .background(surfaceColor),
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

        // 首页那条「模块未生效」提示（见 `HostLinkState.snackbar`）。
        //  只挂在首页；**不占布局位置** —— `SnackbarHost` 内部就是底部对齐的浮层，
        // 页面内容照常铺满（落点在屏幕底边，与顶部横幅的 TopTips 不是一个位置）。
        if (s == Screen.SHORTCUTS) {
            SnackbarHost(
                state = link.snackbar,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                //  胶囊外面要自己撑出一圈空档，否则它那圈影子被库的视窗裁掉
                // ——为什么、以及为什么必须撑在这里，见 [SNACKBAR_SHADOW_ROOM]。
                content = { data ->
                    Box(Modifier.padding(SNACKBAR_SHADOW_ROOM)) {
                        Snackbar(data = data)
                    }
                },
            )
        }

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
        // 只在这一页显示；整页 push / pop 转场时它跟着一起滑
        // （挂在页里，不是挂在页面之外）。
        if (s == Screen.COMMANDS) {
            CommandActions(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = COMMAND_TOOLBAR_BOTTOM),
            )
        }

        // 顶栏：叠在内容**之上**（`Box` 里后声明的画在上面），
        // 半透明底 + 模糊从它底下滚过去的内容 —— 见 [PageHeader]。
        PageTopBar(s, scrollBehavior, hazeState, pagerState, commandPager)
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
        // 「首页」「关于模块」「捐赠列表」三页钉住标题（不折叠），其余几页照常折叠
        // —— 后两页都是「短内容」页，没什么可折的（名单长起来之后要放开折叠，改的就是这一行）。
        canScroll = {
            s != Screen.SHORTCUTS &&
                s != Screen.ABOUT &&
                s != Screen.DONATE_LIST
        },
    )

/**
 * 内容区 —— 「当前屏是哪一页」那一棵 `when`，每支只做一件事：把参数喂给那一页。
 *
 *  六页里只有「按键绑定」与「选择应用」两支带接线（各自的回调写在
 *  [openAppPickerForGesture] / [onAppPicked] 里），其余四支是一行。
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
                // 首页那个主开关（[KeyConfig.enabled]）—— 关掉时整页禁用
                enabled = plusKeyEnabled(),
                onOpenAppPicker = { g -> openAppPickerForGesture(g) },
                scrollBehavior = scrollBehavior,
            )
        }

        Screen.COMMANDS -> {
            CommandScreen(scrollBehavior, commandPager)
        }

        // 挑应用。**两个入口共用这一页**（挑中的东西不一样），接线见 [onAppPicked]。
        Screen.APP_PICKER -> {
            AppPickerScreen(
                // 「当前已选的那个应用」：行尾点亮单选圈 + 进页滚到它眼前。
                // 两条入口的来源见 [appPickerSelectedPkg] —— 网格那条读手势，
                // 「自定义命令」那条读**被换掉的那一步**。
                selected = appPickerSelectedPkg(),
                onPick = { pkg, window -> onAppPicked(pkg, window) },
                scrollBehavior = scrollBehavior,
            )
        }

        // 「关于模块」：版本 / 仓库地址两行，从首页 ⋮ 菜单进
        Screen.ABOUT -> {
            AboutScreen(scrollBehavior)
        }

        // 「捐赠列表」：名单是手写常量（见 `ui/model/Donors.kt`）
        Screen.DONATE_LIST -> {
            DonateListScreen(scrollBehavior)
        }
    }
}

/** 首页那个主开关（[KeyConfig.enabled]）—— 关掉时「按键绑定」页整页禁用。 */
internal fun SettingsPage.plusKeyEnabled(): Boolean =
    configState.config.value
        .key(KeyId.PLUS)
        .enabled

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
    navTo(Screen.APP_PICKER)
}

/**
 * 「选择应用」页挑中一个应用 —— 两条入口的**唯一**落点。
 *
 *   · 「加一步 / 换一步」（`picker.forStep`）：换成 `app:<包名>` 这一**步**，进的是当前多条命令；
 *   · 网格入口：把**进这一页时那个手势**绑成 `app:<包名>`（走现成的 `pick()`，它同时负责
 *     「选动作顺手把主开关打开」那条纪律）。
 *
 *  写成哪个动作由菜单里那项「小窗打开」定（见 [AppPickerState.smallWindow]）：
 *  `app:` 普通打开、`window:` 小窗起。
 */
private fun SettingsPage.onAppPicked(
    pkg: String,
    window: Boolean,
) {
    tickNow()
    val token = if (window) Action.AppWindow(pkg).token else Action.App(pkg).token
    if (picker.forStep) {
        // 「加一步 / 换一步」：这里**不用 `goBack()`** —— `addStep()` / `replaceStep()` 自己会把
        // 页面切回「自定义命令」页（那才是这次挑应用的来处），从「选择应用」退到那一页
        // 就是它的 pop 转场。
        val target = picker.takeStepTarget()
        if (target >= 0) replaceStep(target, token) else addStep(token)
        return
    }
    // 写的是**进这一页时那个手势**（见 picker.gesture），不是 pager 的当前页
    // —— 这一页不属于 pager。
    val g = picker.gesture ?: return
    val ok =
        pick(
            // 只为把取值交给 `pick`（它读的是 token），name 这一位在这条路上不参与显示。
            ActionCatalog.Spec(name = pkg, token = token),
            g,
        )
    //  注意：**写成了才退页、才清手势**：不看 `pick` 的结果的话，被拦（链路未就绪 / 机型
    //  不支持）或写盘失败时照样 `goBack()` 并把 `picker.gesture` 清掉 —— 用户挑的那个应用
    //  就这么丢了（页面退回去了，看起来像选好了，其实一个字节都没写，而且连「再试一次」
    //  都做不到：手势来源已经清空）。失败原因 `pick` 自己已经报过，这里只负责留在原地。
    if (ok) {
        picker.clearTarget()
        goBack()
    } else {
        XLog.w("选择应用：$pkg 没写进绑定，留在本页（不 goBack）")
    }
}
