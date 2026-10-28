package com.lookie.opluskey.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.lookie.opluskey.core.ProcClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.ui.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.LocalDisabledHint
import com.lookie.opluskey.ui.LocalHint
import com.lookie.opluskey.ui.LocalTick
import com.lookie.opluskey.ui.PageDebugChannel
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SyncToHost
import com.lookie.opluskey.ui.disabledClickReason
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PAGE_COVER_DIM
import com.lookie.opluskey.ui.model.PAGE_TRANSITION
import com.lookie.opluskey.ui.model.PagePalette
import com.lookie.opluskey.ui.model.colorsOf
import com.lookie.opluskey.ui.onDebugCommand
import com.lookie.opluskey.ui.screen.home.HomeLinkSelfCheck
import com.lookie.opluskey.ui.state.CONFIG_IN_FLIGHT_GRACE_MS
import com.lookie.opluskey.ui.state.InjectStatus
import com.lookie.opluskey.ui.state.UiState
import com.lookie.opluskey.ui.tickNow
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.nav.core.NavDisplay
import io.github.suqi8.coui.kmp.nav.core.NavDisplayEffects
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import kotlinx.coroutines.delay

// 整页渲染 —— 主题、根 Scaffold、页面栈的壳，以及页面级的**副作用**。
//
// 从 `PageRoot.kt` 拆出来，按「谁管哪一段」分成三个文件：
//   · `PageContent.kt` 本文件 —— 主题 / Scaffold / 页面栈 / 页面级的副作用；
//   · `PageEntry.kt`   一页一份的东西（滚动行为、磨砂取样源）与「这一页画哪一块」；
//   · `PageTopBar.kt`  顶栏的标题 / 副标题 / 右上角动作 / 底下那条固定带。
//
// 拆的判据不是行数，是**生命周期**：副作用的挂/摘时机与内容的重组时机不是一回事，
// 混在一个函数里时「为什么这段只在进页时跑一次」要靠读注释才知道；现在靠函数边界就知道。

/**
 * 整页 —— 主题、根 Scaffold、以及那条按 `Screen` 分派的页面栈。
 *
 * 两个分页状态（[PagerState]）建在这一层而不是各页里：顶栏那条**固定带**与页内内容分处两棵树，
 * 只能共用同一份；而三页的手势绑定页共用一个 pager。详见 `ui/state/PageState.kt` 里那几条注释。
 */
@Composable
internal fun SettingsPage.PageContent() {
    val night = chrome.dark
    //  COUI 的浮层菜单（`OverlayListPopup`）内部走 `NavigationBackHandler`，
    // 而后者要求 composition 里有 `LocalNavigationEventDispatcherOwner` ——
    // 宿主那棵树上没有（我们只补了 Lifecycle / ViewModelStore / SavedState）。
    // 缺了会当场 `IllegalStateException: No NavigationEventDispatcher was provided`（真机崩溃实证）。
    // `parent = null` 是官方指定的「自建根 dispatcher」用法（见其 KDoc）。
    val navOwner = rememberNavigationEventDispatcherOwner(parent = null)

    //  系统配色：配置里存的是 id 字符串（共用层不认识色板清单），认不出就落回默认档。
    //  读的是 `configState.config`，所以选完色当场重组、整页配色跟着换 —— 不需要重进页面。
    val palette = PagePalette.ofId(configState.config.value.global.palette)

    // 「跟随系统显示大小」：**开着**时把整棵页面树换成系统那份 `Density`，关着时保持宿主这一份。
    //
    //  真机（PLK110，显示大小调大：`wm density` = Override 476 / Physical 560）量出来的方向是：
    //   · `LocalDensity.current`（宿主 Activity 的 resources）= **560**，即页面本来就不随系统的
    //     「显示大小」变 —— 宿主那份 Configuration 里密度还是物理值；
    //   · `Resources.getSystem().displayMetrics` = **476**，系统全局配置，跟着「显示大小」走。
    //  所以「跟随」= 用系统那份，「不跟随」= 保持现状（宿主那份）。第一版写反了，真机一拨就看出来。
    //
    //  字号同样取系统那份的 `fontScale`（「字体大小」那一档也一起跟随）。
    //  只包这一棵页面树：宿主自己的顶栏 / 状态栏不归我们管，也不该跟着变。
    //
    // 「页面缩放」在这条基准上再乘一档（只在**不跟随**时生效，那一根滑杆也只在那时出现）：
    //  跟随开着时页面大小由系统那一档说了算，再乘一个缩放等于两处都能调、谁也说不清最后是多少。
    //  乘的是 `Density.density` ⇒ dp 与 sp 一起缩（字号也跟着变，这才是「缩放」而不是「只缩间距」）。
    val global = configState.config.value.global
    val followDisplaySize = global.followDisplaySize
    val hostDensity = LocalDensity.current
    val systemDensity =
        remember {
            val res = Resources.getSystem()
            Density(res.displayMetrics.density, res.configuration.fontScale)
        }
    val pageDensity =
        remember(hostDensity, systemDensity, followDisplaySize, global.pageScale) {
            val base = if (followDisplaySize) systemDensity else hostDensity
            val factor = if (followDisplaySize) 1f else global.pageScale / 100f
            // 100% 时原样返回：否则每重组一次都会换一个新 `Density` 实例，
            //  下面那层 `CompositionLocalProvider` 就得把整棵页面树重画一遍。
            if (factor == 1f) base else Density(base.density * factor, base.fontScale)
        }
    //  提示条那一层（`popupHost`）是 **SubcomposeLayout 另起的子组合**，读不到页面级的窗口 inset
    //  （同一个坑见 `LocalHeaderBoxHeight` 那条注释：在那里 `error()` 过一次）。在 `popupHost`
    //  里面调 `statusBarsPadding()` 拿到的是 **0**，提示条于是画到 y=0 —— 真机视频里它盖住了
    //  系统状态栏的时间与电量。所以在这一层（页面级、Scaffold 之外）读好，按 Dp 传下去。
    val statusBarTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    COUITheme(colors = colorsOf(night, palette)) {
        CompositionLocalProvider(
            LocalDensity provides pageDensity,
            LocalNavigationEventDispatcherOwner provides navOwner,
            //  禁用态那几格点一下要说明原因（见 `ui/DisabledHint.kt`）：
            //  提示怎么弹、以及「整页现在为什么改不了」，都从页面这一层传下去 ——
            //  控件层不带 `SettingsPage` 接收者，拿不到这两样。
            //  原因取**点击那一刻**的结论，不是组合期快照：禁用期间链路可能刚好恢复。
            //  `remember` 住：与下面那条 `LocalDisabledClick` 同一个理由 ——
            //  `hint` 在**调用那一刻**才读，每次重组换一个新实例会让所有读它的控件白白重组一遍。
            LocalHint provides remember { { hint(it) } },
            //  卡片行的「这一行为什么不能点」—— **与整页那条同一个槽位**：
            //  行级说法与本页原因共用一个位，连点几行只会改同一条提示里的字，不叠条。
            LocalDisabledHint provides remember { { hint(it, slot = HINT_SLOT_DISABLED) } },
            //  能点的行默认振一下（见 `LocalTick`）：控件层拿不到页面实例，走这一条下来。
            //  `remember` 住同一个理由：每次重组换新实例会让读它的控件白白重组。
            LocalTick provides remember { { tickNow() } },
            //  禁用态被点一下要说的话：库里所有可点控件（`BasicComponent` / `IconButton` /
            //  可交互 `Card` / 菜单行）在 `enabled = false` 时把这一下点击交给它
            //  —— 见 `patches/coui/0008-disabled-click-explains.patch`。
            //  `remember` 住这个 lambda：`hint` / `disabledClickReason()` 都在**调用那一刻**才读，
            //  每次重组换一个新实例会让所有读它的控件白白重组一遍。
            //  同一个语义位（[HINT_SLOT_DISABLED]）：连点几下是这一条在改字，不叠一串
            LocalDisabledClick provides remember { { hint(disabledClickReason(), slot = HINT_SLOT_DISABLED) } },
        ) {
            Scaffold(
                containerColor = COUITheme.colorScheme.surface,
                //  整页**边到边**，insets 一概不加：状态栏那一条由页面自己画
                // （顶栏 107dp 含状态栏 inset，底色铺到 y=0），所以顶栏的模糊、
                // 浮层的 dim 都自然作用到它 —— 之前那条盖着 hook 侧的
                // 实心遮罩，模糊 / dim 都到不了，只能一个个单独处理（已删）。
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                //  提示条（常驻结论 + 操作回执）挂**浮层槽**、排在弹窗宿主**之后**：
                //   · 内容之上、不随滚动 —— 挂进页内就得跟着列表滚，而各页都是滚动容器；
                //   · 位置在**页面最顶部**（状态栏之下）：贴着底边时它会压在弹窗那一排按钮上
                //     （弹窗是居中/偏下的，二者必然重叠）。挪到顶部之后两者不再抢地方，
                //     而「弹窗开着时的提示」（「这颗键不能添加」）照样看得见。
                //     **只让状态栏这一条**，不要让顶栏 —— 提示条本来就浮在顶栏那一行，
                //     让了顶栏会看着「离顶部很远」（真机反馈）。
                //   · 状态栏那条 inset **不在这里加**：它加在这一层的话，提示条入场那层 alpha
                //     的上边界就是状态栏下沿，而阴影往上要溢出 22dp ⇒ 那 150ms 里阴影上沿被切平
                //     （真机反馈「提示条顶部阴影在入场出场时被裁切」）。让给提示条自己加
                //     （见 `PageSnackbar.HintSlot`）：画布从**屏幕顶**开始，胶囊位置不变。
                popupHost = {
                    COUIPopupHost()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        PageSnackbars(statusBarTop)
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        //  裁掉转场出画的部分：push/pop 时旧页滑出、新页滑入，
                        .clipToBounds(),
                ) {
                    //  三个分页器一次建好、打成一个包往下传（见 [PagePagers]）
                    val pagers =
                        PagePagers(
                            gestures = rememberPagerState(pageCount = { Gesture.entries.size }),
                            commands = rememberPagerState(pageCount = { CommandKind.FORM_TABS.size }),
                            //  组合键只有短按 / 长按两档（`Gesture.entries` 是三档，别直接借它）
                            combos = rememberPagerState(pageCount = { 2 }),
                        )
                    PageEffects(navOwner, pagers)
                    PageBackStack(pagers)
                }
            }
        }
    }
}

/**
 * 页面级的**副作用**，进页时各挂一次、离开时各摘一次 —— 与「此刻画哪一页」无关。
 *
 *  这一组全放在同一个函数里，是因为它们的挂/摘时机是同一件事（进/出这个页面），
 *  而不是因为它们相关：混在内容树里写时，每次改「这一页画什么」都得重新确认
 *  「这几条 effect 是不是还在同一个组合位置上」。
 */
@Composable
private fun SettingsPage.PageEffects(
    navOwner: NavigationEventDispatcherOwner,
    pagers: PagePagers,
) {
    // 深浅变化同步给 hook 侧（只挂一次，不随子页重复挂）
    SyncToHost()
    // 换页（往深走 / 往浅走 / 清栈回首页）时**先收掉所有浮窗** ——
    // 浮窗渲染在根 Scaffold 的 `popupHost` 里、不跟着页面转场走
    // （见 [closeOverlays]），不收的话它会**钉在屏幕上**：页面滑走了它还在，
    // 要等那一页出栈才跟着消失。
    LaunchedEffect(navStack.lastOrNull()) { closeOverlays("换页 → ${navStack.lastOrNull()}") }
    //  磨砂取样源跟着**栈顶页**走（理由见 [pageHazeState]）：往回退（pop）时下面
    //  那一页不会重新组合，只有这个 effect 能把取样源摆回去。
    //
    //  但它**单独用不够**：新页的 entry 可能比它晚一帧才组合，那一刻 [hazeStatesByPage]
    //  里还没有那一页 ⇒ 取样源被摆成 null，紧接着打开的浮层菜单就没有磨砂
    // （真机反馈「所有带菜单的页首次进入、开菜单都没糊」，日志里那一行是
    //  「浮层磨砂跳过：页面取样层未就绪」）。所以各页组合时还各自兜一次底，
    //  见 [PageEntry] 里那个 `LaunchedEffect(s)`。
    LaunchedEffect(navStack.lastOrNull()) {
        val top = navStack.lastOrNull() as? Screen
        val picked = top?.let { chrome.hazeStatesByPage[it] }
        if (picked != null) {
            chrome.pageHazeState = picked
        } else {
            // 正常路径不会走到（那一页组合时自己会兜一次底，见 [PageEntry]）。
            // 真走到这里说明取样源还是空的 —— 接着开的浮层不会有磨砂，留一行凭据。
            XLog.w("磨砂取样源查不到：${top?.name ?: "无"} 那一页还没登记取样状态")
        }
    }
    DebugChannelBridge()
    LinkCheckBridge()
    // 「自己刚写下去的那一份还在路上」的**到期**：回包一到就提前结束（见 `onInjectReady`），
    //  一直等不到就在宽限期满时摘掉这一位 —— 页面随即按真实结论判「未同步」。
    //  少了这条到期，乐观态会一直挂着，注入侧真出问题时页面上看不出来。
    LaunchedEffect(link.pendingConfigSince) {
        val since = link.pendingConfigSince
        if (since == 0L) return@LaunchedEffect
        delay(CONFIG_IN_FLIGHT_GRACE_MS)
        if (link.pendingConfigSince == since) link.clearConfigPending()
    }
    // —— 平台返回 → 页面栈 ——
    //
    //  预测返回：hook 侧的 `OnBackAnimationCallback` 拿到系统手势的进度后调
    // [navBackInput] 的方法；这里把那个 input 挂到 dispatcher 上才有人接
    // （coui-nav 的浮层与 [NavDisplay] 也都往同一个 dispatcher 挂 handler，
    // 「谁先消费」由它仲裁）。页面销毁自动摘掉。
    DisposableEffect(navOwner) {
        val dispatcher = navOwner.navigationEventDispatcher
        dispatcher.addInput(navBackInput)
        onDispose { dispatcher.removeInput(navBackInput) }
    }
    // —— 「这一退还要不要自己接」回报给 hook 侧 ——
    //
    //  进 / 出子页、浮层开合都会改这个答案（见 [needsBackInterception]），
    //  hook 侧据此在宿主上注册 / 注销返回回调：首页那一档**不注册**，系统才会
    //  给「关掉整个设置页」那一下做跟手动画。首帧也会跑一次（重同步，无害）。
    //  注意：浮层那个计数是在 `DisposableEffect` 里加减的、与这里差一帧，所以「刚开 /
    //  刚关浮层那一瞬」的注册时机晚一帧 —— 手速到不了那一帧，不必额外对账。
    LaunchedEffect(navStack.size, overlays.openCount.intValue) {
        hooks.onBackInterceptionChanged?.invoke()
    }
    DebugGestureSwitch(pagers.gestures)
    PushResetsPagers(pagers)
    //  提示条那条**页面级**副作用（一次性提醒）—— 提示条全局只有这一叠，
    //  所以弹它的人也只能在页面级这一处（实现见 `PageSnackbar.kt`，那里是提示条的家）。
    //  常驻结论那条已经删掉：见 `HintQueue` 的类注释。
    //  链路自检也在页面级：它要把 `injectChecked` 置 true，而那一位正是一堆判据的门
    //  （「系统侧还是旧版」「页面还在检查」都等它）。挂在首页时一离开那一页就被取消 ——
    //  于是「留在首页才看得到提示」，与本意（一趟进程只说一次）相反。
    HomeLinkSelfCheck()
    PageNoticeHint()
    PageCheckingHint()
}

/**
 * 调试通道的接收器 —— 只有 debug 源集有实现，release 是同名空壳（`register` 恒 null，
 * 这里就是死代码）。让 `am broadcast` 能直接驱动这个页面，省掉「dump 找坐标 → 模拟点击 →
 * 打字被输入法吃掉」那一套。
 */
@Composable
private fun SettingsPage.DebugChannelBridge() {
    DisposableEffect(Unit) {
        val receiver = PageDebugChannel.register(context) { onDebugCommand(it) }
        onDispose { receiver?.let { runCatching { context.unregisterReceiver(it) } } }
    }
}

/**
 * 两条「链路自检」的回音 —— 一个接收器兼收，省一份注册 / 注销的壳：
 *  · 宿主侧报活（[ModuleWire.ACTION_HOST_SERVICE_UP]）：模块加载好了。
 *    首页据此把「模块未生效」的提示收掉、解除整页禁用。**正式通道，release 也有**。
 *  · 注入侧回包（[ModuleWire.ACTION_INJECT_READY]）：注入侧那套装上没、配置到没到。
 *    发送方是 system_server（uid 1000），`NOT_EXPORTED` 照样可达。
 */
@Composable
private fun SettingsPage.LinkCheckBridge() {
    DisposableEffect(Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) {
                    // 先判空再分派：`when (intent?.action)` 那几个分支里 `intent` 仍是可空的，
                    // 到了处理函数还得再兜一次。
                    if (intent == null) return
                    when (intent.action) {
                        ModuleWire.ACTION_HOST_SERVICE_UP -> onHostServiceUp(intent)
                        ModuleWire.ACTION_INJECT_READY -> onInjectReady(intent)
                    }
                }
            }
        val ok =
            runCatching {
                context.registerReceiver(
                    receiver,
                    IntentFilter().apply {
                        addAction(ModuleWire.ACTION_HOST_SERVICE_UP)
                        addAction(ModuleWire.ACTION_INJECT_READY)
                    },
                    // 同包广播，不必对外导出
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }.isSuccess
        if (!ok) XLog.w("注册链路自检接收器失败（链路自检会一直显示未就绪）")
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}

/** 宿主侧模块报活 —— 首页据此解除禁用（见 [LinkCheckBridge]）。 */
private fun SettingsPage.onHostServiceUp(intent: Intent) {
    XLog.w("宿主侧模块报活（收到广播）⇒ 首页解除禁用")
    link.hostServiceUp = true
    link.hostAliveToken++
    // 报活里带的两条诊断字段（链路自检用）
    //  缺省 false：报活里没带这一位（老宿主）时不该据此声称「已启用」，
    //  宁可显示未启用 —— 那一档只是首页一条诊断行，谎报会让人以为模块开着却没生效。
    link.hostEnabled = intent.getBooleanExtra(ModuleWire.EXTRA_HOST_ENABLED, false)
    link.hostLastActionAt = intent.getLongExtra(ModuleWire.EXTRA_HOST_LAST_ACTION_AT, 0L)
    link.hostProcStart = intent.getLongExtra(ModuleWire.EXTRA_HOST_PROC_START, 0L)
}

/** 注入侧自检回包 —— 链路自检那一段据此显示「接管点 / 配置送达 / 指纹」。 */
private fun SettingsPage.onInjectReady(intent: Intent) {
    val status =
        InjectStatus(
            hooks = intent.getIntExtra(ModuleWire.EXTRA_INJECT_HOOKS, 0),
            configOk = intent.getBooleanExtra(ModuleWire.EXTRA_INJECT_CONFIG_OK, false),
            configTag = intent.getStringExtra(ModuleWire.EXTRA_INJECT_CONFIG_TAG).orEmpty(),
            lastGestureAt = intent.getLongExtra(ModuleWire.EXTRA_INJECT_LAST_GESTURE_AT, 0L),
            buildId = intent.getStringExtra(ModuleWire.EXTRA_INJECT_BUILD_ID).orEmpty(),
            procStart = intent.getLongExtra(ModuleWire.EXTRA_INJECT_PROC_START, 0L),
        )
    XLog.w(
        "注入侧自检回包：接管点=${status.hooks} " +
            "配置已送达=${status.configOk} " +
            "指纹=${status.configTag.ifEmpty { "无" }}",
    )
    // 判据级：这一侧**跑的是不是盘上这份包**。判据就是「进程启动时刻 vs 包更新时间」——
    //  只在真的判成旧包时才写一行，正常机器上一条都不会有。
    if (link.installedAt > 0 && status.procStart > 0 && link.installedAt > status.procStart) {
        XLog.w(
            "注入侧跑的可能是旧包：进程起于 ${status.procStart}，" +
                "包更新于 ${link.installedAt} ⇒ 要整机重启",
        )
    }
    // 「它跑起来过」这件事只增不减 —— 用来把「从没用过」与「用过、这次没起来」分开。
    //  落盘只在值真的变时才发生（见 `noteInjectAlive`）；写盘本身挪到后台线程：
    //  这里是广播回调（宿主主线程），一次几十字节不值得占首帧。
    link.noteInjectAlive(ProcClock.bootWall()) { boot ->
        Thread { UiState.writeInjectAlive(boot) }.start()
        true
    }
    link.injectStatus = status
    // 回包带回来的正是我们刚写下去的那一份 ⇒ 「在途」到此结束（见 `HostLinkState.configPending`）
    if (status.configTag == link.pendingConfigTag) link.clearConfigPending()
    link.injectAliveToken++
}

/**
 * 调试通道要切手势 tab —— `pagerState` 活在组合里，只能这样接：
 * 看到请求就滚过去、然后清掉（见 [debugGesture]）。release 包里没人写那个位，恒为 null。
 */
@Composable
private fun SettingsPage.DebugGestureSwitch(pagerState: PagerState) {
    LaunchedEffect(debugGesture.value) {
        debugGesture.value?.let {
            pagerState.scrollToPage(it.ordinal)
            debugGesture.value = null
        }
    }
}

/**
 * push 时把**目标页**的分页状态拨回它该在的那一档。
 *
 *  两个分页状态是**页面级**的、不随页面出栈注销（见 [PageContent]）⇒「出栈的页整个销毁、
 *  重新进来是新的一份」这条栈语义得手动补上：退回首页再进「按键绑定」，手势 tab 会停在
 *  上次那一档（真机反馈过）。
 *  判据取**栈深变深** —— 那就是 push。**pop 不重置**：往回退时下面是原来那一页，状态本来
 *  就该原样留着（「打开应用」挑完退回「按键绑定」，还得停在**刚才那个手势**上）。
 */
@Composable
private fun SettingsPage.PushResetsPagers(pagers: PagePagers) {
    var previousDepth by remember { mutableIntStateOf(navStack.size) }
    LaunchedEffect(navStack.size) {
        val depth = navStack.size
        val pushed = depth > previousDepth
        previousDepth = depth
        if (!pushed) return@LaunchedEffect
        when (screen) {
            Screen.BINDING -> {
                pagers.gestures.scrollToPage(0)
            }

            // 组合键编辑页每次都从「短按」那一档开始 —— 上一组停在长按，换一组进来还停在长按，
            //  看着像「这一组没有短按的网格」
            Screen.COMBO_EDIT -> {
                pagers.combos.scrollToPage(0)
            }

            // 「自定义命令」页**按草稿的类型**定位，不是一律回第一页：
            // 从「按键绑定」网格长按 / 已选中再点一下某条命令进来时，
            // `draft.load(cmd)` 已经把 `kind` 设成那条命令的类型 —— 一律回 0
            // 的话单条 / 多条那条 tab 会停在错的档上，紧接着 `draft.kind`
            // 还会被分页器反着覆盖回「单条」（真机反馈「长按自定义命令跳转后，
            // 多条命令的 tab 没有对应切换」）。
            //  从 FAB 进来时草稿刚 `reset()`（kind = SINGLE），等价于回第一页。
            //  分叉那一档不在这个分页器里（它是独立页）：`indexOf` 给出 -1 ⇒ 落到第一页，
            //  紧接着 `CommandTabSync` 会把 `draft.kind` 拨回单条 —— 这一页本来也只认这两档。
            Screen.COMMANDS -> {
                pagers.commands.scrollToPage(
                    CommandKind.FORM_TABS.indexOf(commandDraft.draft.kind).coerceAtLeast(0),
                )
            }

            else -> {
                // 其余页不归这个分页器管
            }
        }
    }
}

/**
 * 页面栈。一页一个 entry，但 `entry` 只注册一次：库按 key 的**运行时类**取注册
 * （`entry<T>` 就是拿 `T::class` 建的索引），而 `Screen` 是个枚举 —— 六页的运行时类是同一个，
 * 所以注册一次就够，页与页的区别全在 [PageEntry] 里那个 `when`。
 */
@Composable
private fun SettingsPage.PageBackStack(pagers: PagePagers) {
    NavDisplay(
        backStack = navStack,
        //  系统返回那条链在本集成方式下是断的（见 [navStack] 的注释），这个回调
        // 只在 dispatcher 真收到事件时兜底 —— 语义与 [goBack] 一致。
        onBack = { goBack() },
        transition = PAGE_TRANSITION,
        //  圆角裁剪关掉（整幅平移，没有卡片式缩放，不必跟着屏幕圆角切角）；
        // 压暗用库的通用开关 [NavDisplayEffects.dimAmount]：栈顶之下那一页
        // 垫一层黑，静止恒定压着、转场随进度渐变（见 [PAGE_COVER_DIM]）。
        effects =
            NavDisplayEffects(
                enableCornerClip = false,
                dimAmount = PAGE_COVER_DIM,
            ),
        modifier = Modifier.fillMaxSize(),
    ) {
        entry<Screen> { s ->
            PageEntry(s, pagers)
        }
    }
}
