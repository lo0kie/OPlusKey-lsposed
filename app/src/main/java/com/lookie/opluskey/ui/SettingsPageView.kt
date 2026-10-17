package com.lookie.opluskey.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.ui.component.MenuPopup
import com.lookie.opluskey.ui.component.PageHeader
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.model.ABOUT_TITLE
import com.lookie.opluskey.ui.model.APP_PICKER_TITLE
import com.lookie.opluskey.ui.model.BINDING_TITLE
import com.lookie.opluskey.ui.model.COMMANDS_TITLE
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.model.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.NIGHT_POLL_MS
import com.lookie.opluskey.ui.model.PAGE_COVER_DIM
import com.lookie.opluskey.ui.model.PAGE_TRANSITION
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.SNACKBAR_SHADOW_ROOM
import com.lookie.opluskey.ui.model.specIcon
import com.lookie.opluskey.ui.screen.about.AboutScreen
import com.lookie.opluskey.ui.screen.apppicker.AppPickerMenu
import com.lookie.opluskey.ui.screen.apppicker.AppPickerScreen
import com.lookie.opluskey.ui.screen.apppicker.AppSearchBand
import com.lookie.opluskey.ui.screen.binding.ActionTile
import com.lookie.opluskey.ui.screen.binding.BindingScreen
import com.lookie.opluskey.ui.screen.binding.BindingTabBand
import com.lookie.opluskey.ui.screen.binding.actionOf
import com.lookie.opluskey.ui.screen.binding.pick
import com.lookie.opluskey.ui.screen.command.CommandActions
import com.lookie.opluskey.ui.screen.command.CommandKindBand
import com.lookie.opluskey.ui.screen.command.CommandScreen
import com.lookie.opluskey.ui.screen.command.addCommand
import com.lookie.opluskey.ui.screen.donate.DonateListScreen
import com.lookie.opluskey.ui.screen.home.HomeScreen
import com.lookie.opluskey.ui.screen.home.ResetMenu
import com.lookie.opluskey.ui.screen.home.appVersionLabel
import com.lookie.opluskey.ui.screen.home.appVersionText
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.CommandDraft
import com.lookie.opluskey.ui.state.CommandDraftState
import com.lookie.opluskey.ui.state.ConfigState
import com.lookie.opluskey.ui.state.FallbackOwners
import com.lookie.opluskey.ui.state.HostLinkState
import com.lookie.opluskey.ui.state.InjectStatus
import com.lookie.opluskey.ui.state.UiState
import com.lookie.opluskey.ui.state.addStep
import com.lookie.opluskey.ui.state.installI18nBridge
import com.lookie.opluskey.ui.state.openAppPickerForStep
import com.lookie.opluskey.ui.state.publishConfig
import com.lookie.opluskey.ui.state.replaceStep
import com.lookie.opluskey.ui.state.saveCommands
import com.lookie.opluskey.ui.state.tickNow
import com.lookie.opluskey.ui.theme.SyncToHost
import com.lookie.opluskey.ui.theme.colorsFor
import com.lookie.opluskey.ui.theme.pageBackgroundColor
import com.lookie.opluskey.ui.theme.systemNight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Snackbar
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.nav.core.NavBackStack
import io.github.suqi8.coui.kmp.nav.core.NavDisplay
import io.github.suqi8.coui.kmp.nav.core.NavDisplayEffects
import io.github.suqi8.coui.kmp.nav.core.navBackStackOf
import io.github.suqi8.coui.kmp.theme.COUITheme

// 整页渲染 —— 「当前屏是哪一页」那棵 when 树与每页的接线。
// 它是这个页面对象上最大的一块，搬出来做扩展函数（Kotlin 不能把一个类拆到两个文件）。

@Composable
internal fun SettingsPage.Page() {
    val night = dark.value
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
                    //  见 entry 里那个 `LaunchedEffect(s)`。
                    LaunchedEffect(navStack.lastOrNull()) {
                        val top = navStack.lastOrNull() as? Screen
                        val picked = top?.let { hazeStatesByPage[it] }
                        if (picked != null) {
                            pageHazeState = picked
                        } else {
                            // 正常路径不会走到（那一页组合时自己会兜一次底，见 entry 里那段）。
                            // 真走到这里说明取样源还是空的 —— 接着开的浮层不会有磨砂，留一行凭据。
                            XLog.w("磨砂取样源查不到：${top?.name ?: "无"} 那一页还没登记取样状态")
                        }
                    }
                    // 调试通道：只有 debug 源集有实现，release 是同名空壳（`register` 恒 null，
                    // 这段是死代码）。让 `am broadcast` 能直接驱动这个页面，省掉「dump 找坐标 →
                    // 模拟点击 → 打字被输入法吃掉」那一套。
                    DisposableEffect(Unit) {
                        val receiver = PageDebugChannel.register(context) { onDebugCommand(it) }
                        onDispose { receiver?.let { runCatching { context.unregisterReceiver(it) } } }
                    }
                    // 两条「链路自检」的回音 —— 一个接收器兼收，省一份注册 / 注销的壳：
                    //  · 宿主侧报活（[ModuleWire.ACTION_HOST_SERVICE_UP]）：模块加载好了。
                    //    首页据此把「模块未生效」的提示收掉、解除整页禁用。**正式通道，release 也有**。
                    //  · 注入侧回包（[ModuleWire.ACTION_INJECT_READY]）：注入侧那套装上没、配置到没到。
                    //    发送方是 system_server（uid 1000），`NOT_EXPORTED` 照样可达。
                    DisposableEffect(Unit) {
                        val receiver =
                            object : BroadcastReceiver() {
                                override fun onReceive(
                                    c: Context?,
                                    intent: android.content.Intent?,
                                ) {
                                    when (intent?.action) {
                                        ModuleWire.ACTION_HOST_SERVICE_UP -> {
                                            XLog.w("宿主侧模块报活（收到广播）⇒ 首页解除禁用")
                                            link.hostServiceUp = true
                                            link.hostAliveToken++
                                            // 报活里带的两条诊断字段（链路自检用）
                                            link.hostEnabled =
                                                intent.getBooleanExtra(
                                                    ModuleWire.EXTRA_HOST_ENABLED,
                                                    true,
                                                )
                                            link.hostLastActionAt =
                                                intent.getLongExtra(
                                                    ModuleWire.EXTRA_HOST_LAST_ACTION_AT,
                                                    0L,
                                                )
                                        }

                                        ModuleWire.ACTION_INJECT_READY -> {
                                            val status =
                                                InjectStatus(
                                                    hooks = intent.getIntExtra(ModuleWire.EXTRA_INJECT_HOOKS, 0),
                                                    configOk =
                                                        intent.getBooleanExtra(
                                                            ModuleWire.EXTRA_INJECT_CONFIG_OK,
                                                            false,
                                                        ),
                                                    configTag =
                                                        intent
                                                            .getStringExtra(
                                                                ModuleWire.EXTRA_INJECT_CONFIG_TAG,
                                                            ).orEmpty(),
                                                    lastGestureAt =
                                                        intent.getLongExtra(
                                                            ModuleWire.EXTRA_INJECT_LAST_GESTURE_AT,
                                                            0L,
                                                        ),
                                                )
                                            XLog.w(
                                                "注入侧自检回包：接管点=${status.hooks} " +
                                                    "配置已送达=${status.configOk} " +
                                                    "指纹=${status.configTag.ifEmpty { "无" }}",
                                            )
                                            link.injectStatus = status
                                            link.injectAliveToken++
                                        }
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
                    LaunchedEffect(navStack.size, openOverlays.intValue) {
                        onBackInterceptionChanged?.invoke()
                    }
                    // ── 页面级状态：**不随页面出栈注销**的那几样 ──
                    //
                    //  分页状态与全量图标表都提在这里创建，而不是各自页面里 `remember`：
                    //   · [pagerState] / [commandPager] 各有一条**固定带**要读（带子挂在顶栏的
                    //     `bottomContent` 里、内容在页里，两棵树只能共用同一份状态）；
                    //  [tileIcons] 那张图标表也在类上（见它自己的注释）：读它的两处在不同的树里，
                    //  各自 `remember` 会各建一份。这里不再重复建表。
                    val pagerState = rememberPagerState(pageCount = { Gesture.entries.size })
                    val commandPager = rememberPagerState(pageCount = { CommandKind.entries.size })
                    // 调试通道要切手势 tab —— `pagerState` 活在组合里，只能这样接：
                    // 看到请求就滚过去、然后清掉（见 [debugGesture]）。
                    LaunchedEffect(debugGesture.value) {
                        debugGesture.value?.let {
                            pagerState.scrollToPage(it.ordinal)
                            debugGesture.value = null
                        }
                    }
                    //  图标表不在这里建：[tileIcons] 是类上的那一份（随 `commands` 重算）。

                    // —— push 时把**目标页**的分页状态拨回它该在的那一档 ——
                    //
                    //  上面那两个分页状态是**页面级**的、不随页面出栈注销（见它们各处的注释）
                    // ⇒「出栈的页整个销毁、重新进来是新的一份」这条栈语义得手动补上：
                    // 退回首页再进「按键绑定」，手势 tab 会停在上次那一档（真机反馈过）。
                    //  判据取**栈深变深** —— 那就是 push。**pop 不重置**：往回退时下面是原来
                    // 那一页，状态本来就该原样留着（「打开应用」挑完退回「按键绑定」，
                    // 还得停在**刚才那个手势**上）。
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

                    // —— 页面栈 ——
                    //
                    //  一页一个 entry，但 `entry` 只注册一次：库按 key 的**运行时类**取注册
                    // （`entry<T>` 就是拿 `T::class` 建的索引），而 `Screen` 是个枚举 ——
                    //  五个分量的运行时类是同一个，所以注册一次就够，页与页的区别全在下面那个
                    // `when`。每页独享的东西（`scrollBehavior`、取样状态 `hazeState`）写在 entry
                    //  的内容里：那是**每个 entry 各自一份**的组合，`remember` 天然逐页独立。
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
                            // 大标题的滚动折叠（COUI「TopAppBar → 大标题顶部栏滚动行为」）——
                            // 每页**各持一份**：滚动行为是「这一页的列表滚到哪」的账，
                            // 共用一份的话翻页之后标题的折叠进度会串页。
                            //
                            //  顶栏与滚动容器**必须吃同一个实例**：顶栏读它的
                            // `state` 画折叠进度，滚动容器把 `nestedScrollConnection`
                            // 挂在链上把余量喂进来。只给一头等于没接（顶栏恒停在展开态）。
                            //
                            //  「首页」「更多选项」的标题**不折叠**（定案）：库的
                            // 开关是 `canScroll` —— false 时顶栏不再消费滚动事件
                            // （`ExitUntilCollapsedScrollBehavior` 里 `if (!canScroll()) return Zero`），
                            // 大标题钉在原位、内容照常从它底下滚过；其余三页照常折叠。
                            //
                            //  注意：**每页都必须自己给初始 `heightOffsetLimit`**（只给钉住的两页的话，
                            // 折叠那三页靠「频繁重组自愈」，实测会卡在坏态）：
                            //  库默认是 `-Float.MAX_VALUE`，而那个字段是**普通 var** —— 顶栏在布局期
                            //  把它改写成真实折叠量时**不触发重组**。于是 [currentHeaderHeight] 首帧
                            //  读到 -MAX，`52dp - (-1.2e38dp)` 让 `Modifier.height(...).roundToPx()`
                            //  整型溢出成 0：顶部让位的 Spacer 变 0、内容整段顶到屏幕最上被顶栏盖住
                            // （「关于模块」页与「按键绑定」页各实证一次）。折叠页只在
                            //  恰好有别的重组时自愈 ⇒ 表现为「偶尔有些页面没给 Header 让位」。
                            //  初始值给「展开 − 折叠」那一档（像素）；[currentHeaderHeight] 另有一道
                            //  认哨兵的兜底，双保险。
                            val scrollBehavior =
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
                                    // 「首页」「关于模块」「捐赠列表」三页钉住标题（不折叠），
                                    // 其余几页照常折叠 —— 后两页都是「短内容」页，没什么可折的
                                    // （名单长起来之后要放开折叠，改的就是这一行）。
                                    canScroll = {
                                        s != Screen.SHORTCUTS &&
                                            s != Screen.ABOUT &&
                                            s != Screen.DONATE_LIST
                                    },
                                )

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
                                hazeStatesByPage[s] = hazeState
                                onDispose { hazeStatesByPage.remove(s) }
                            }
                            //  这一页组合完成时若它正是栈顶，就把取样源摆过来 —— 与页面级那个
                            //  `LaunchedEffect(navStack.lastOrNull())` 互为兜底（那边可能早一帧，
                            //  见它自己的注释）。幂等：同一个实例写回去不会触发重组。
                            LaunchedEffect(s) {
                                if (navStack.lastOrNull() == s) pageHazeState = hazeState
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
                                    when (s) {
                                        Screen.SHORTCUTS -> {
                                            HomeScreen(scrollBehavior)
                                        }

                                        Screen.BINDING -> {
                                            BindingScreen(
                                                pagerState,
                                                // 首页那个主开关（[KeyConfig.enabled]）—— 关掉时整页禁用
                                                enabled =
                                                    configState.config.value
                                                        .key(KeyId.PLUS)
                                                        .enabled,
                                                onOpenAppPicker = { g ->
                                                    picker.openForGesture(g)
                                                    // 搜索词清空（页面级状态，不再随页面出栈注销，
                                                    // 见 [AppPickerState.filter]；另一条入口在 [openAppPickerForStep]）
                                                    picker.filter.value = ""
                                                    // 「以小窗口打开」按**这次要编辑的动作**重算
                                                    // （不落盘、不跨次记忆，必须在两个入口都调）
                                                    resetSmallWindowForPicker()
                                                    // 父级不用记：压它下面的是谁，返回就退到谁
                                                    navTo(Screen.APP_PICKER)
                                                },
                                                scrollBehavior = scrollBehavior,
                                            )
                                        }

                                        Screen.COMMANDS -> {
                                            CommandScreen(scrollBehavior, commandPager)
                                        }

                                        // 挑应用。**两个入口共用这一页**（挑中的东西不一样）：
                                        //  · 网格里的入口格「打开应用」→ 把**当前手势**绑成
                                        //    `app:<包名>`（走现成的 `pick()`，它同时负责
                                        //    「选动作顺手把主开关打开」那条纪律）；
                                        //  · 「添加命令」浮层里的「选择应用」→ 把 `app:<包名>`
                                        //    作为**一步**加进当前多条命令（走 `addStep()`，见
                                        //    `openAppPickerForStep`）。
                                        Screen.APP_PICKER -> {
                                            AppPickerScreen(
                                                // 「当前已选的那个应用」：行尾点亮单选圈 + 进页滚到它眼前。
                                                // 两条入口的来源见 [appPickerSelectedPkg] —— 网格那条读手势，
                                                // 「自定义命令」那条读**被换掉的那一步**。
                                                selected = appPickerSelectedPkg(),
                                                onPick = { pkg, window ->
                                                    tickNow()
                                                    // 写成哪个动作由菜单里那项「小窗打开」定（见 [AppPickerState.smallWindow]）
                                                    val token = if (window) Action.AppWindow(pkg).token else Action.App(pkg).token
                                                    // 判据是「这一趟是加一步 / 换一步」那一组标记（见 [AppPickerState.forStep]）
                                                    if (picker.forStep) {
                                                        // 「加一步 / 换一步」：这里**不用 `goBack()`** ——
                                                        // `addStep()` / `replaceStep()` 自己会把页面切回
                                                        // 「自定义命令」页（那才是这次挑应用的来处），
                                                        // 从「打开应用」退到那一页就是它的 pop 转场。
                                                        val target = picker.takeStepTarget()
                                                        if (target >= 0) {
                                                            replaceStep(target, token)
                                                        } else {
                                                            addStep(token)
                                                        }
                                                    } else {
                                                        // 写的是**进这一页时那个手势**（见 picker.gesture），
                                                        // 不是 pager 的当前页 —— 这一页不属于 pager。
                                                        val g = picker.gesture
                                                        val ok =
                                                            g != null &&
                                                                pick(
                                                                    // 只为把取值交给 `pick`（它读的是 token），
                                                                    //  name 这一位在这条路上不参与显示。
                                                                    ActionCatalog.Spec(name = pkg, token = token),
                                                                    g,
                                                                )
                                                        //  注意：**写成了才退页、才清手势**：
                                                        //  不看 `pick` 的结果的话，被拦（链路未就绪 / 机型不支持）
                                                        //  或写盘失败时照样 `goBack()` 并把 `picker.gesture`
                                                        //  清掉 —— 用户挑的那个应用就这么丢了（页面退回去了，
                                                        //  看起来像选好了，其实一个字节都没写，而且连"再试一次"
                                                        //  都做不到：手势来源已经清空）。
                                                        //  失败原因 `pick` 自己已经报过，这里只负责留在原地。
                                                        if (ok) {
                                                            picker.clearTarget()
                                                            goBack()
                                                        } else {
                                                            XLog.w("选择应用：$pkg 没写进绑定，留在本页（不 goBack）")
                                                        }
                                                    }
                                                },
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

                                // 首页那条「模块未生效」提示（见 [HostLinkState.snackbar]）。
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
                                PageHeader(
                                    title =
                                        when (s) {
                                            // 首页标题平时是**宿主 Activity 自己的标题**（hook 侧读来的，
                                            //  见 SettingsPageHooks）；字典里有这一档时按语言来，没有才用宿主那串。
                                            Screen.SHORTCUTS -> I18n.get(Strings.HOME_TITLE, homeTitle)

                                            Screen.BINDING -> BINDING_TITLE

                                            Screen.COMMANDS -> COMMANDS_TITLE

                                            Screen.APP_PICKER -> APP_PICKER_TITLE

                                            Screen.ABOUT -> ABOUT_TITLE

                                            Screen.DONATE_LIST -> DONATE_LIST_TITLE
                                        },
                                    // 大标题**下面**那行（库原本的底线副标题槽位，自己占一行）：
                                    // 首页 = 模块版本。与「更多选项」页的「版本」行**同源但不同串**：
                                    // 那边总是带构建时刻，这边按构建类型决定（见 appVersionLabel / appVersionText）。
                                    largeTitleSubtitle =
                                        when (s) {
                                            Screen.SHORTCUTS -> remember { appVersionLabel() }
                                            else -> ""
                                        },
                                    // 大标题**右边**那行小标题（补丁新增的槽位，折叠后保留）：
                                    // 「自定义命令」= 已保存的命令条数（[ConfigState.commands] 变了跟着重组）；
                                    // 「选择应用」= 当前已选的应用名（两条入口见
                                    // [appPickerSelectedPkg]，查不到清单就退回包名）。
                                    subtitle =
                                        when (s) {
                                            // 一条都没保存时不报数（「已保存0条命令」读起来像统计，也不成句）——
                                            // 直接说「没有保存的命令」，与页内那块空态同一句话。
                                            Screen.COMMANDS -> {
                                                if (configState.commands.value.isEmpty()) {
                                                    I18n.get(Strings.COMMAND_NO_SAVED_COMMANDS, "没有保存的命令")
                                                } else {
                                                    I18n.format(Strings.COMMAND_SAVED_COUNT, configState.commands.value.size)
                                                }
                                            }

                                            Screen.APP_PICKER -> {
                                                val pkg = appPickerSelectedPkg()
                                                if (pkg == null) {
                                                    // 没选过就不摆一行「已选择: 无」—— 那种写法像是有个叫「无」的应用
                                                    I18n.get(Strings.APP_PICKER_UNSELECTED, "还未选择应用")
                                                } else {
                                                    val label =
                                                        picker.cachedApps.value
                                                            ?.firstOrNull { it.packageName == pkg }
                                                            ?.label ?: pkg
                                                    I18n.format(Strings.APP_PICKER_SELECTED_FORMAT, label)
                                                }
                                            }

                                            else -> {
                                                ""
                                            }
                                        },
                                    // 「选择应用」页开着小窗打开时，那行小字后面补一枚「小窗起」
                                    // —— 与网格里「打开应用」那格同一口径
                                    // （见 `BindingScreen` 的 `appSmallWindow`）：那一行字只说明
                                    // 「选了哪个应用」，看不出这一趟是要普通打开还是小窗起。
                                    trailingSubtitleIcon =
                                        if (
                                            s == Screen.APP_PICKER && picker.smallWindow.value
                                        ) {
                                            PageIcons.TileSmallWindow
                                        } else {
                                            null
                                        },
                                    // 右上角那几颗动作键按页分派：**只有首页与「选择应用」页有**
                                    // （前者是打开默认界面 / 清空配置，后者是「显示系统应用」那三项）。
                                    // 顶栏只留一个 `actions` 槽位、不认识页面（见 [PageHeader]），
                                    // 两份菜单各自跟着自己那一屏走（[ResetMenu] / [AppPickerMenu]）。
                                    //
                                    //  「按键绑定」页那一套「新增命令」菜单晚些时候**撤掉了**
                                    // 不再有 ⋮。进「自定义命令」页的唯一入口现在是那一页右下角的
                                    // 那颗 FAB（见 [BindingScreen]）。
                                    actions = {
                                        when (s) {
                                            Screen.SHORTCUTS -> ResetMenu()
                                            Screen.APP_PICKER -> AppPickerMenu()
                                            else -> Unit
                                        }
                                    },
                                    scrollBehavior = scrollBehavior,
                                    state = hazeState,
                                    // 顶栏底下那条**固定带**（三条带子各归各的页）——
                                    // 不能各页各叠一层浮层（自带取样层 / 模糊 /
                                    // 半透明底 / 内容里等高占位），统一搬进顶栏这个
                                    // `bottomContent` 槽位：位置、底色、模糊、下沿那条细线都由
                                    // 顶栏那一份负责，见 [PageHeader] 与三个带子自己的 KDoc。
                                    //  带子跨度与内容高度都各自报给页面级状态（[bindingBandHeight]
                                    // 那三个），页内那个占位 Spacer 读 —— 带子与内容分处两棵树，
                                    // 只有这一条路能对齐。
                                    bottomContent = {
                                        when (s) {
                                            Screen.BINDING -> {
                                                BindingTabBand(
                                                    pagerState,
                                                    // 首页那个主开关（[KeyConfig.enabled]）——
                                                    // 关掉时带子灰掉且吃掉输入（与那颗 FAB 同款）
                                                    enabled =
                                                        configState.config.value
                                                            .key(KeyId.PLUS)
                                                            .enabled,
                                                )
                                            }

                                            Screen.COMMANDS -> {
                                                CommandKindBand(commandPager)
                                            }

                                            Screen.APP_PICKER -> {
                                                AppSearchBand()
                                            }

                                            // 首页 / 「关于模块」/「捐赠列表」都没有固定带
                                            //  （后两页都是「短内容」页，见 [canScroll] 那一档）
                                            Screen.SHORTCUTS, Screen.ABOUT, Screen.DONATE_LIST -> {
                                                // 这几页不摆固定带
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
