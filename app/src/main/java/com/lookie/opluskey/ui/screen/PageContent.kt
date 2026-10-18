package com.lookie.opluskey.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.ui.PageDebugChannel
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SyncToHost
import com.lookie.opluskey.ui.colorsFor
import com.lookie.opluskey.ui.model.PAGE_COVER_DIM
import com.lookie.opluskey.ui.model.PAGE_TRANSITION
import com.lookie.opluskey.ui.onDebugCommand
import com.lookie.opluskey.ui.state.CONFIG_IN_FLIGHT_GRACE_MS
import com.lookie.opluskey.ui.state.InjectStatus
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.nav.core.NavDisplay
import io.github.suqi8.coui.kmp.nav.core.NavDisplayEffects
import io.github.suqi8.coui.kmp.theme.COUITheme
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

    COUITheme(colors = colorsFor(night)) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navOwner) {
            Scaffold(
                containerColor = COUITheme.colorScheme.surface,
                //  整页**边到边**，insets 一概不加：状态栏那一条由页面自己画
                // （顶栏 107dp 含状态栏 inset，底色铺到 y=0），所以顶栏的模糊、
                // 浮层的 dim 都自然作用到它 —— 之前那条盖着 hook 侧的
                // 实心遮罩，模糊 / dim 都到不了，只能一个个单独处理（已删）。
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        //  裁掉转场出画的部分：push/pop 时旧页滑出、新页滑入，
                        .clipToBounds(),
                ) {
                    val pagerState = rememberPagerState(pageCount = { Gesture.entries.size })
                    val commandPager = rememberPagerState(pageCount = { CommandKind.entries.size })
                    PageEffects(navOwner, pagerState, commandPager)
                    PageBackStack(pagerState, commandPager)
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
    pagerState: PagerState,
    commandPager: PagerState,
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
    DebugGestureSwitch(pagerState)
    PushResetsPagers(pagerState, commandPager)
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
    link.hostEnabled = intent.getBooleanExtra(ModuleWire.EXTRA_HOST_ENABLED, true)
    link.hostLastActionAt = intent.getLongExtra(ModuleWire.EXTRA_HOST_LAST_ACTION_AT, 0L)
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
        )
    XLog.w(
        "注入侧自检回包：接管点=${status.hooks} " +
            "配置已送达=${status.configOk} " +
            "指纹=${status.configTag.ifEmpty { "无" }}",
    )
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
private fun SettingsPage.PushResetsPagers(
    pagerState: PagerState,
    commandPager: PagerState,
) {
    var previousDepth by remember { mutableIntStateOf(navStack.size) }
    LaunchedEffect(navStack.size) {
        val depth = navStack.size
        val pushed = depth > previousDepth
        previousDepth = depth
        if (!pushed) return@LaunchedEffect
        when (screen) {
            Screen.BINDING -> {
                pagerState.scrollToPage(0)
            }

            // 「自定义命令」页**按草稿的类型**定位，不是一律回第一页：
            // 从「按键绑定」网格长按 / 已选中再点一下某条命令进来时，
            // `draft.load(cmd)` 已经把 `kind` 设成那条命令的类型 —— 一律回 0
            // 的话单条 / 多条那条 tab 会停在错的档上，紧接着 `draft.kind`
            // 还会被分页器反着覆盖回「单条」（真机反馈「长按自定义命令跳转后，
            // 多条命令的 tab 没有对应切换」）。
            //  从 FAB 进来时草稿刚 `reset()`（kind = SINGLE），等价于回第一页。
            Screen.COMMANDS -> {
                commandPager.scrollToPage(
                    CommandKind.entries.indexOf(commandDraft.draft.kind).coerceAtLeast(0),
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
private fun SettingsPage.PageBackStack(
    pagerState: PagerState,
    commandPager: PagerState,
) {
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
            PageEntry(s, pagerState, commandPager)
        }
    }
}
