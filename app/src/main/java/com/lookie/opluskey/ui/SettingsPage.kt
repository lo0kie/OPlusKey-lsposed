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
import com.lookie.opluskey.ui.screen.apppicker.AppPickerScreen
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
import com.lookie.opluskey.ui.screen.home.appVersionLabel
import com.lookie.opluskey.ui.screen.home.appVersionText
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.BandHeight
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

/**
 * 模块自己的侧键设置页 —— **整套用 COUI（Compose）渲染，不再依赖宿主的任何样式**。
 */
internal class SettingsPage(
    internal val context: Context,
) {
    /** 页面所在的 Activity（取色 / 引 owner / 退出页面用；挖不到就退回 null 走自建那套）。 */
    internal val activity: android.app.Activity? =
        generateSequence(context as Context?) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>()
            .firstOrNull()

    /**
     * 第一次 `View.performHapticFeedback` 会同步走 `SystemVibrator Created`
     * （binder + 服务查询），砸在 push 动画第一帧上，就是「进子页偶发卡顿」；
     * 提前到页面构建期创建，点击时只剩一次 `vibrate` 调用。
     * 宿主已持有 `android.permission.VIBRATE`（dumpsys 确认过，模块跑在宿主进程里共用）。
     */
    internal val vibrator: android.os.Vibrator? =
        try {
            context.getSystemService(android.os.Vibrator::class.java)
        } catch (e: Throwable) {
            XLog.w("拿 Vibrator 失败（触觉反馈不可用）：${e.message}")
            null
        }

    /**
     * 静音（`RINGER_MODE_SILENT`）时不振，跟随系统礼节。
     * 与 [vibrator] 同样建页期拿好，点击路径上只剩一次 getter。
     */
    internal val audioManager: android.media.AudioManager? =
        try {
            context.getSystemService(android.media.AudioManager::class.java)
        } catch (e: Throwable) {
            XLog.w("拿 AudioManager 失败（响铃状态不可读）：${e.message}")
            null
        }

    internal val dark = mutableStateOf(false)

    /**
     * **当前栈顶页**内容那一层的取样状态 —— 顶栏 / 浮层菜单 / 底部工具栏的磨砂都从它取样。
     *
     *  记在页面上而不是一路当参数传：浮层菜单是 [SettingsPage] 的扩展（`ui/component/CommandMenus`
     *  里那五处菜单入口），再多穿一个参数不值当。
     *
     *  注意：**必须按「谁在栈顶」来选，不能按「谁最后组合」**。旧写法是
     *  `if (pageBackdrop !== backdrop) pageBackdrop = backdrop` —— 最后组合的那一页说了算；
     *  而深度 2 往返还栈时，被 cull 的首页会重新组合、时机晚于栈顶页，于是取样源被它抢走：
     *  浮层菜单取到的是**另一页**的内容（真机：「磨砂处似乎是取样的两个 switch，但菜单底下
     *  并没有 switch」）。选法见 [Page] 里那个 `LaunchedEffect(navStack.lastOrNull())`。
     *
     *  注意：取样方**不能落进它自己取样的那个 `hazeSource`** —— 落进去就是渲染节点链无限增长、
     *  RenderThread 递归到栈溢出（真机崩过一次）。浮层菜单画在 Scaffold 的 `COUIPopupHost` 里
     *（页面那层之外）才是安全的，见 `ui/component/MenuPopup.kt` 里 `MenuPopup` 的注释。
     */
    internal var pageHazeState: HazeState? by mutableStateOf(null)

    /**
     * 每页各一份取样状态（键是页）—— 各页在 `DisposableEffect(s)` 里把自己那份登记进来、并在
     *  `onDispose` 摘掉（见 [Page] 里 `NavDisplay` 的 entry）。页被 cull 后重建会换一份新的进来，
     *  旧的那份随 `onDispose` 出表（它的源节点已 detach）。
     */
    internal val hazeStatesByPage = mutableMapOf<Screen, HazeState>()

    /**
     * 「取样层没就绪 ⇒ 这次用不透明底」那一条日志的闸 —— 建页期只报一次，免得每个消费方各刷一行。
     * 见 [pageHazeState] 与 `ui/component/MenuPopup.kt` / `ui/screen/command/CommandScreen` 里那两处判定。
     */
    internal var frostDegradeLogged = false

    /**
     * **页面栈**（coui-nav 的返回栈）。
     *
     *  它是一个普通的 `SnapshotStateList<NavKey>`：谁在谁上面、当前在哪一页，全由它说了算。
     * 读栈顶用 [screen]，往深走用 [navTo]，往浅走用 [goBack] —— 调用点不必知道下面这些。
     *
     *  ## 为什么换成库的栈
     *
     *  改之前那套是手写的：一个 `Animatable<Float>`（浮点 ordinal）当栈深度，`forEach` 遍历
     *  整张页面表、按 `ordinal <= stackTop` 决定组合谁，再自己算每页的位移。它已经能跑，问题是
     *  **每一样都得自己维护**：转场的两段公式、屏外页不画的裁剪、出栈那一刻的收缩、
     *  「转场首帧不做重活」的记账……而 `NavDisplay` 把这些做成了同一件事的几种视图。
     *
     *  ## 换过来之后拿到的、与拿不到的
     *
     *  拿到的：
     *   · **栈语义是真的** —— 库按 `key(contentKey)` + `movableContentOf` 渲染整条栈，
     *     栈里的页**常驻组合**（页内裸 `remember` 不丢），出栈的页在转场结束后才卸载；
     *   · 每 entry 自带 `LifecycleOwner` / `ViewModelStoreOwner` / `SaveableStateHolder`；
     *   · 屏外页不产生绘制命令（`opaqueDepth = 1f`：只有栈顶与它下面那一层会被画）。
     *
     *  ## 系统预测返回是**桥**过来的
     *
     *  库的 `NavDisplay` 内部用 `PredictiveBackHandler` 接系统返回手势，事件源是
     *  composition 里的 `LocalNavigationEventDispatcherOwner` —— 而我们的页面挂在宿主
     *  Activity 的 View 树上、宿主不提供这个 owner（见 [Page] 里那句 `parent = null` 的自建根
     *  dispatcher），所以那条链**从根上就断在外面**。接法是在 hook 侧另建一头：
     *  `SettingsPageHooks` 的 `OnBackAnimationCallback` 拿到系统手势的**起点与逐帧进度**，
     *  喂给 [navBackInput] ⇒ 本页 dispatcher ⇒ 库自己驱动转场（见 [NavBackInput]）。
     *
     *  注意：这条桥只对**子页与浮层**有效：首页那一档 hook 侧**故意不注册**返回回调，把这一退整个
     *  留给系统（见 [needsBackInterception] 里那笔账）。
     *
     *  ## 为什么用 `navBackStackOf` 而不是 `rememberNavBackStack`
     *
     *  后者带 `rememberSaveable` 持久化，**要求路由 key `@Serializable`**。我们不要恢复栈：
     *  页面进程死了（宿主被杀）再进来就该是首页那一档，恢复一个「上次停在自定义命令页」反而怪
     *  ——`onHostReentered` 里那条「从设置重进清回首页」正是这个意思。
     *
     *  注意：这个 `SnapshotStateList` 建在**类的初始化**里（不在组合里）：[SettingsPage] 实例的存活
     *  期就是「这一次进设置页」，与原来那个 `mutableStateOf(Screen.SHORTCUTS)` 完全等价。
     */
    internal val navStack: NavBackStack = navBackStackOf(Screen.SHORTCUTS)

    /** 当前在哪一级页面（栈顶）。 */
    internal val screen: Screen get() = navStack.last() as Screen

    /**
     * 往 [target] 走一步。
     *
     *  语义是「**切到那一页**」而不是「压一层」：
     *   · [target] 不在栈里 ⇒ 压栈（push）；
     *   · 已经在栈里 ⇒ 把它上面那些**全部弹掉**，一步回到它。
     *     「自定义命令」页里挑完应用回到「自定义命令」（`addStep` / `replaceStep`）走的就是这一支
     *     —— 从「打开应用」退到「自定义命令」正好是它的 pop 转场，与手写那套完全一致。
     *
     *  调用点只报「我要去哪」，不再自己算「该 push 还是该 pop」：
     *  `screen.value = X` 是同一套语义但分散在各处，且「打开应用」的返回目标还得靠
     *  `_appPickerReturn` 另外记一笔（已删）。
     */
    internal fun navTo(target: Screen) {
        if (navStack.lastOrNull() == target) return
        val index = navStack.indexOf(target)
        if (index >= 0) {
            while (navStack.size > index + 1) navStack.removeAt(navStack.lastIndex)
        } else {
            navStack.add(target)
        }
    }

    /**
     * 平台返回事件喂给页面栈的入口（见 [NavBackInput]）。
     *
     *  hook 侧的 `OnBackAnimationCallback` 拿到系统手势后调它的方法；它要先把
     * `Page()` 组合期挂到 dispatcher 上才有人接（见 [Page] 里那个 `DisposableEffect`）。
     */
    internal val navBackInput = NavBackInput()

    /** 此刻开着几个浮层（`OverlayCloser` 组合期维护；返回的接管判定与它自己的 handler 都读它）。 */
    internal val openOverlays = mutableIntStateOf(0)

    /**
     * 系统返回要交给**页面栈**处理吗 —— **只判断，不动手**（`onBackStarted` 那一刻不能有副作用，
     * 所以不能调 [goBack] 来探）。**三种不接管**：
     *
     *   · 栈里只剩一页（首页）⇒ 那是「关掉整个设置页」，归宿主；
     *   · 浮层开着 ⇒ COUI 的浮层自己不接返回，喂进去没人消费，最后仍是页面栈弹
     *     （表现为「浮层盖着、底下页面跟着手指滑」）；
     *   · 注意：**这一退会被 [goBack] 拦住**（「自定义命令」页草稿改过 ⇒ 先弹「放弃未保存的命令」）：
     *     库的提交是 `onCommit = { currentOnBack.value() }`，**弹栈交给 [goBack]** —— 接管了就等于
     *     页面先滑走、栈却没弹，页面停在滑出去的位置回不来。判据与 [goBack] 那条**同一份**，一起改。
     */
    internal fun wouldConsumeBack(): Boolean =
        navStack.size > 1 &&
            openOverlays.intValue == 0 &&
            !(screen == Screen.COMMANDS && commandDraft.draft.dirty)

    /** 此刻有浮窗开着吗 —— 开着时系统返回**整个吞掉**（见 `BackInterception.handleDiscreteBack`）。 */
    internal fun hasOpenOverlays(): Boolean = openOverlays.intValue > 0

    /**
     * 这一档「系统返回」**必须由模块自己接**吗 —— hook 侧据此决定要不要在宿主上
     * **注册返回回调**（见 `BackInterception.syncBackCallback`）。
     *
     *  ## 为什么「注册」要按需
     *
     *  往宿主上挂 `OnBackInvokedCallback` 就是在告诉系统「这一退我自己处理」——系统收到这个信号后
     *  **不做窗口动画**了（它不知道我们要做什么）。而「首页返回 ⇒ 关掉整个设置页」这一退，系统本来
     *  就做得比我们好：跟手缩放、露出下面那一层（设置 / 桌面）——**只有系统做得出来**（下面那一层
     *  是另一个窗口，我们画不了）。所以首页这一档**不注册**，把这一退留给系统走它自己的预测返回。
     *
     *  ## 两种必须自己接
     *
     *   · **有子页**（`navStack.size > 1`）⇒ 这一退是弹栈；
     *   · **浮层开着** ⇒ 这一退是收浮层（见 [hasOpenOverlays]）。
     *
     *  注意：判据与 [wouldConsumeBack] **不是同一份**，别合并：
     *   · 「草稿脏」那一档 [wouldConsumeBack] 为 false（那时库的提交是「先弹问话、不退页」，接管了
     *     反倒会把页面滑出去——理由见它自己的注释），但**这一退仍归我们处理**（问话是 [goBack] 弹的）
     *     ⇒ 这里必须**不带**那条判定；
     *   · 键盘开着那一档这里**不用管**：能露键盘的输入框都在子页与浮层里（首页没有输入框），上面两种
     *     情况已经覆盖 —— `BackInterception.consumeBackByIme` 仍在回调里做兜底。
     */
    internal fun needsBackInterception(): Boolean = hasOpenOverlays() || navStack.size > 1

    /**
     * 建好的页面 View（[createView] 里赋值）。
     *
     * 只为一件事：进「按键绑定」子页时把 **Android 层的焦点**抢到这个 View 上
     * —— 方向键事件按焦点派发，没焦点就收不到（见 [ActionTile] 的注释）。
     */
    internal var rootView: View? = null

    /**
     * 配置与命令库（主配置 / 命令库 / 两个只读位）—— 见 [ConfigState]。
     *
     *  一域一个对象：这一域的读写原来摊在本类上，分布在好几个文件里；收进来之后
     *  「谁改过这一项」一眼可列（见 `docs/decoupling.md` 动作 5 的写入者清单）。
     */
    internal val configState = ConfigState()

    /**
     * **界面处处共用的那一份图标表** —— [ActionCatalog.queue] 那条队列 → 图标。
     *
     * 队列与图标都只有这一个来源：网格格子、格子里那行小字、「添加命令」浮层的行、入口格菜单
     * 的行，一律读这一张表 —— 同一格在哪一处露面都是同一颗字形，也不再各自现调 [specIcon]。
     *
     * 放在类上而不是组合里：读它的几处在**不同的树**里（网格在 [BindingScreen]、「添加命令」
     * 浮层在 [CommandScreen]），各自 `remember` 会各建一份、还可能对不上。
     * 随 [ConfigState.commands] 重算（命令库变了，新增的格子才有图标），`derivedStateOf` 只在有人读它时才算。
     */
    internal val tileIcons: State<Map<ActionCatalog.Spec, ImageVector>> =
        derivedStateOf {
            ActionCatalog.queue(configState.commands.value).associateWith { specIcon(it) }
        }

    /**
     * 三条**固定带**的高度 —— 分段控件带 / 类型选择器带 / 搜索带，各一页一份。
     *
     *  量（顶栏那棵树）与读（页内容那棵树）分处两棵树，走 [BandHeight] 这条通道，
     *  它自己带量测上报与 dp 读取两端（口径见那边的类注释）。
     */
    internal val bindingBandHeight = BandHeight()
    internal val commandBandHeight = BandHeight()
    internal val appSearchBandHeight = BandHeight()

    /**
     * 「选择应用」页那一域的状态（搜索词 / 两个落盘的筛选开关 / 小窗开关 / 这趟为谁挑）。
     *
     *  收在一个对象里而不是摊在页面上：读写它的人分布在四处（顶栏那棵树、页内容那棵树、
     *  两个入口），摊开时「谁改过这一项」得把四个文件翻一遍 —— 见 [AppPickerState] 的类注释。
     *  各字段为什么提到页面对象上（而不是留在页面里 `remember`）写在各自那里。
     */
    internal val picker = AppPickerState()

    /**
     * 这一趟「选择应用」页在**编辑哪个动作** —— 两个入口各有来源（都在进页那一刻记下）：
     *  · 网格入口（「按键绑定」页的「打开应用」格）→ [AppPickerState.gesture] 那个手势绑的动作；
     *  · 「自定义命令」页那条路 → 被**换掉**的那一步（[AppPickerState.replaceStep]）。
     *
     * `null` = 新绑（没在编辑任何动作）。[appPickerSelectedPkg] 与 [resetSmallWindowForPicker]
     * 都要它，两处必须同源 —— 否则会出现「行点亮了、开关却是关的」这种自相矛盾的界面。
     */
    internal fun editingAction(): Action? =
        if (picker.forStep) {
            picker.replaceStep
                .takeIf { it >= 0 }
                ?.let { commandDraft.draft.steps.getOrNull(it) }
                ?.let { runCatching { Action.parse(it) }.getOrNull() }
        } else {
            picker.gesture?.let { actionOf(configState.config.value, it) }
        }

    /**
     * 进「选择应用」页时把「以小窗口打开」**重算**一遍（入口两处各调一次）。
     *
     * 判据就是 [editingAction]：正在编辑的那个动作本来就是 `window:<包名>` ⇒ 开；
     * `app:<包名>` / 别的动作 / 新绑（`null`）⇒ 关。
     */
    internal fun resetSmallWindowForPicker() {
        val on = editingAction() is Action.AppWindow
        picker.smallWindow.value = on
        XLog.w("选择应用：以小窗口打开按正在编辑的动作重算 = $on")
    }

    /**
     * **调试通道**要切手势 tab 时往这里放一个请求（见 [onDebugCommand]）。
     *
     * [Page] 里一个 `LaunchedEffect` 看着这个值 —— 有请求就滚过去、然后清掉。
     * release 包里没人写它（那条注册是死分支），恒为 `null`。
     */
    internal val debugGesture = mutableStateOf<Gesture?>(null)

    /**
     * 「自定义命令」页的草稿与「重置」那套行级退场标记 —— 见 [CommandDraftState]。
     */
    internal val commandDraft = CommandDraftState()

    /** 「清空配置」确认弹窗（调试通道经 `action=CONFIRM_RESET` 置 true 弹出）。 */
    internal val confirmReset = mutableStateOf(false)

    /** 首页标题 —— 宿主 Activity 的原文，hook 侧在 [createView] 之前赋值。 */
    var homeTitle: String = "快捷键"

    /** 系统深浅变化回调（给 hook 重刷状态栏配色）。 */
    var onSystemThemeChanged: (() -> Unit)? = null

    /**
     * 「这一刻还要不要自己接返回」变了（进出子页 / 浮层开合）—— 由 [Page] 在组合期回报，
     * hook 侧据此在宿主上注册 / 注销返回回调（见 [needsBackInterception]）。
     *
     *  **只有需要时的那一档才值得注册**：注册了系统就不做关页动画了，而首页那一退正需要它。
     */
    var onBackInterceptionChanged: (() -> Unit)? = null

    /** 菜单里「打开自带的快捷键界面」——由 hook 侧实现（藏模块页面、露出宿主原版页面）。 */
    var onOpenStock: (() -> Unit)? = null

    /** 「导出日志」—— hook 侧实现：弹系统的「保存到」选择器，写完再回报（见 [SettingsPageHooks]）。 */
    var onExportLog: (() -> Unit)? = null

    /**
     * 「更多 → 导出配置」—— hook 侧实现：弹系统的「保存到」选择器，把主配置 + 命令库写成一份文件。
     *
     * 与 [onExportLog] 同一条 SAF 路（结果只能从宿主的 `onActivityResult` 收，见 [SettingsPageHooks]）。
     */
    var onExportConfig: (() -> Unit)? = null

    /** 「更多 → 导入配置」—— hook 侧实现：弹系统的「打开」选择器，读回文件再写进配置与命令库。 */
    var onImportConfig: (() -> Unit)? = null

    /**
     * 建页期读一次命令库（三态，与主配置的 [ConfigState.readOnly] 对称）。
     *
     *  「读不懂」这一档必须与「空表」分开：界面把空表当真相之后，用户随便改一条命令就会把它
     *  写回盘、把那份读不懂的文件覆盖掉 —— 用户的命令这就没了。所以读不懂时置 [ConfigState.commandsReadOnly]，
     *  由 [saveCommands] 拒绝一切写回。
     */
    internal fun readCommands() {
        when (val l = CommandLibrary.loadState()) {
            is CommandLibrary.Load.Ok -> {
                configState.commands.value = l.commands
                configState.commandsReadOnly = false
            }

            CommandLibrary.Load.Absent -> {
                XLog.w("还没有命令库文件（首装 / 刚清过数据）—— 用空表，可以写回")
                configState.commands.value = emptyList()
                configState.commandsReadOnly = false
            }

            CommandLibrary.Load.Unreadable -> {
                // 这条必须是 w：它是「命令库为什么进只读」的唯一判据行
                XLog.w("命令库读不懂 ⇒ 命令库进只读，本次不许写回（否则会把用户的命令覆盖掉）")
                configState.commands.value = emptyList()
                configState.commandsReadOnly = true
            }
        }
    }

    /** 页面底色 —— COUI 色板的 `surface`（深色 `#000000`、浅色 `#F0F1F2`，与系统设置页一致）。 */
    fun createView(): View? {
        // 先把字典订阅装上，再读配置（下面几行会按配置里的语言档重设 `I18n.currentLanguage`）：
        //  装晚了的话，那一次换档发生在界面层那份状态接上之前，首帧就会停在上一个语言的文案上。
        //  字典本体在 core（普通 Map），订阅这一半必须在界面层 —— 见 `ui/state/I18nBridge.kt`。
        installI18nBridge()
        dark.value = systemNight()
        // 配置读成**三态**：只有「读到过正常文件」与「还没写过」才允许之后写回。
        //  读不懂 ⇒ 只读（[ConfigState.readOnly]）：手上那份默认值不是用户的真相，不能让它落盘。
        when (val l = ConfigStore.load()) {
            is ConfigStore.Load.Ok -> {
                configState.config.value = l.config
                I18n.currentLanguage = AppLanguage.fromCode(l.config.global.language)
                configState.readOnly = false
            }

            ConfigStore.Load.Absent -> {
                XLog.w("还没有配置文件（首装 / 刚清过数据）—— 用默认值，可以写回")
                configState.config.value = Config.DEFAULT
                I18n.currentLanguage = AppLanguage.fromCode(Config.DEFAULT.global.language)
                configState.readOnly = false
            }

            ConfigStore.Load.Unreadable -> {
                // 这条必须是 w：它是「页面为什么变只读」的唯一判据行
                XLog.w("配置读不懂 ⇒ 页面进只读，本次不许写回（否则会把默认值当真相固化）")
                configState.config.value = Config.DEFAULT
                I18n.currentLanguage = AppLanguage.fromCode(Config.DEFAULT.global.language)
                configState.readOnly = true
            }
        }

        // 命令库与配置一起在建页期读好。与主配置**同一条纪律**（见上面那三态）：
        //  读不懂 ⇒ 命令库也进只读（[ConfigState.commandsReadOnly]），否则用户随手改一条命令就会把那份
        //  读不懂的文件覆盖成「只有这一条」。拆成 [readCommands] 只是为了让 [createView] 别太长。
        readCommands()

        // 界面状态（本地持久，**不是用户配置** —— 见 [UiState]）与上面两份一起读一次：
        // 读不到 / 解析不了都退默认（关）。放这里是因为它同样只读一次、且必须早于首帧
        // （顶栏菜单那两项要按它画选中态，列表也要按它决定查哪一份）。
        //  一次读全（readAll）：两项各读一遍会把同一个文件 open + parse 两遍，而这跑在主线程上。
        //  只读两项 —— 「以小窗口打开」**不落盘**（见 [AppPickerState.smallWindow]），不在这一份里。
        val ui = UiState.readAll()
        picker.showSystemApps.value = ui.first
        picker.showUnopenable.value = ui.second
        XLog.w(
            "界面状态：显示全部应用=${picker.showSystemApps.value}，显示失效=${picker.showUnopenable.value}",
        )

        // 建页时把**完整配置**主动同步一次给注入侧（判定在那一侧，见 [publishConfig]）：
        // 「装好后没动过任何设置」的状态下，注入侧只能等宿主进程的解锁广播，
        // 打开这一页就立刻同步一次，作为兜底。
        publishConfig(configState.config.value)

        XLog.w(
            "命令库：${configState.commands.value.size} 条" +
                "（单条 ${configState.commands.value.count { it.kind == CommandKind.SINGLE }}" +
                " / 多条 ${configState.commands.value.count { it.kind == CommandKind.SERIAL }}）",
        )

        val view =
            ComposeView(context).apply {
                // 首帧之前先把底色铺成页面底色，避免「先黑一下再画出来」
                setBackgroundColor(pageBackgroundColor or 0xFF000000.toInt())
                // 当前有焦点的 View** —— 手机是触摸模式，一个没打开 `isFocusableInTouchMode`
                // 的 View 拿不到 Android 焦点，事件就永远到不了 Compose（Compose 的按键处理挂在
                // `AndroidComposeView.dispatchKeyEvent` 上，见 [ActionTile] 的注释）。
                isFocusable = true
                isFocusableInTouchMode = true
                setContent { Page() }
            }
        rootView = view

        // 自建 owner（宿主那棵树一个都没有）并先挂到自己身上；
        // 祖先链由 hook 侧在 `container.addView` **之后**回调 [attachOwnersUpward] 挂。
        //
        //  宿主三件套齐全时 [ownOwners] 返回 null —— **绝不能用占位实例去挂**：`attachOne`
        //  是「只挂不覆盖」，此刻 `view` 还没进树（`parent == null`），占位实例会被钉在
        //  `ComposeView` 自身上，之后 `attachOwnersUpward` 换不掉它，Compose 于是拿到那份
        //  停在 `INITIALIZED` 的 lifecycle ⇒ 页面一片空白。挂了 null 就让 Compose 沿
        //  parent 链自己去宿主那棵树上找（那正是它该走的路）。
        val own =
            ownOwners(
                activity as? androidx.lifecycle.LifecycleOwner,
                activity as? androidx.lifecycle.ViewModelStoreOwner,
                activity as? androidx.savedstate.SavedStateRegistryOwner,
            )
        pendingOwners = own
        if (own != null) attachOne(view, own)

        XLog.w(
            "设置页用 COUI（Compose）渲染：night=${dark.value}" +
                " surface=#${Integer.toHexString(pageBackgroundColor)}",
        )
        return view
    }

    /**
     * 从子页返回上一级 —— 把**栈顶弹掉**。上一级就是压在当前页下面的那一页（不查表）。
     *
     * 注意：两处**拦住不让退**（都返回 true = 这次返回已被消费）：「自定义命令」页草稿有改动
     * （先问一句，见 [confirmLeaveCommands]）；栈里只剩首页（交给宿主关页面）。
     */
    internal fun goBack(): Boolean {
        val current = screen
        // 「打开应用」页返回：**原样保留进页前的选择** —— 挑中了写新值（那边自己 `pick`），
        // 没挑就什么都不动，蓝框本来就在选中项上。
        if (current == Screen.APP_PICKER) {
            // 「为谁挑」整组一并清掉 —— 不管这次是挑完了、还是没挑就退出来
            picker.clearTarget()
        }
        // 「自定义命令」页：表单里改过还没保存 ⇒ 不退，先弹一句确认。判据用 `draft.dirty`
        // （与载入时的快照比），不是 `hasContent` —— 后者会把「点开一条看一眼、原样返回」也算成有改动。
        if (current == Screen.COMMANDS && commandDraft.draft.dirty) {
            if (!confirmLeaveCommands) {
                XLog.w("退出自定义命令页：表单改过还没保存，先弹确认")
                confirmLeaveCommands = true
            }
            return true
        }
        return popBackStack()
    }

    /**
     * 真的把栈顶弹掉（[goBack] 的各种拦截都过了以后走它）。
     * 单独一个函数是给那张确认窗用：「放弃」之后要**绕过** [goBack] 里的拦截直接退。
     *
     * @return true = 真弹掉了一页（false = 栈里只剩首页，没得弹）
     */
    internal fun popBackStack(): Boolean {
        if (navStack.size <= 1) return false
        navStack.removeAt(navStack.lastIndex)
        return true
    }

    /**
     * 退出「自定义命令」页时的「放弃未保存的命令」确认 —— 该弹没弹。
     * 触发点在 [goBack]（草稿 `draft.dirty` 时置位并**拦住不退**），窗由 `CommandScreen` 自己画。
     * 放类上而不是页面内 `remember`：置位的是 [goBack]，两处必须同一份。
     */
    internal var confirmLeaveCommands by mutableStateOf(false)

    /**
     * 「表单里有没保存的改动，但用户要换成另一条命令」—— 待确认的那一条（`null` = 没在弹）。
     *
     *  触发点是 [loadDraft]（草稿 [CommandDraft.dirty] 时置位、**先问一句**），窗由 `CommandScreen` 画。
     *  与 [confirmLeaveCommands] 分开：那个问的是「退不退页」，这个问的是「换不换内容」。
     */
    internal var pendingDraftLoad by mutableStateOf<Command?>(null)

    /**
     * 把已保存的 [cmd] 灌进表单 —— **载入前先看草稿脏不脏**。
     *
     *  直接 `draft.load` 会把表单里没保存的改动整份覆盖掉，而且 `load` 连快照一起换 ⇒ `dirty`
     *  反而变 false，退出时那句「放弃未保存」也不会问（静默丢输入、无提示无撤销）。
     *  脏的时候改成**先弹一句确认**，确认了才 `load`（窗见 `CommandScreen` 里那个同名弹窗）。
     *
     *  注意：只在「自定义命令」页上调用：草稿脏了必然还在这一页 —— 退出时 [goBack] 已经问过并
     *  `draft.reset()`，所以窗画在那一页就够。
     */
    internal fun loadDraft(cmd: Command) {
        if (commandDraft.draft.dirty) {
            XLog.w("载入命令前先问：表单里有未保存的改动（要换成「${cmd.name}」）")
            pendingDraftLoad = cmd
            return
        }
        commandDraft.draft.load(cmd)
    }

    /**
     * 「打开应用」页该**点亮哪一行**（[AppPickerScreen] 的 `selected`，同时决定进页滚到谁眼前）。
     *
     * 两个入口各有来源，读的都是**进这一页时**记下来的东西（这一页不属于 pager，不能读当前页）：
     *  · 网格入口（「按键绑定」页的「打开应用」格）→ [AppPickerState.gesture] 那个手势绑的
     *    `app:` 动作的包名；
     *  · 「自定义命令」页那条路 → 被**换掉**的那一步自己（[AppPickerState.replaceStep]）：
     *    它本来就是 `app:<包名>` 时才算数；「加一步」没有目标 ⇒ null。
     *
     * 之前第二条恒为 null，于是「换一步」进这一页时看不出当前选的是哪个应用
     *（行尾单选圈不亮、也不滚到它眼前）—— 与网格入口那套提示不是一回事。
     */
    internal fun appPickerSelectedPkg(): String? {
        // 两种动作都要认：`app:`（普通打开）与 `window:`（小窗，[Action.AppWindow]）——
        // 与「按键绑定」页那一格取应用名同口径。少认一种时，开着「以小窗口打开」挑完应用回来
        // 这页一行都不点亮、也不滚过去，看起来像没选过。
        //  来源与「以小窗口打开」的重算同源（[editingAction]）—— 两处判据必须一致。
        val target =
            when (val action = editingAction()) {
                is Action.App, is Action.AppWindow -> action.appTarget
                else -> null
            }
        return target?.substringBefore('/')
    }

    /**
     * 「链路 / 报活」那几项结论（宿主在不在、注入侧回没回包）—— 见 [HostLinkState]。
     */
    internal val link = HostLinkState()

    /**
     * 从设置页重进模块时（`SettingsPageHooks`）—— 把页面栈清回首页。
     */
    fun onHostReentered() {
        picker.clearTarget()
        // 栈顶没变时下面的 LaunchedEffect 不触发，浮窗要在这里主动收：
        // 首页开着 ⋮ 菜单按 Home / 熄屏再回来，菜单不能还盖在页面上。
        closeOverlays("从设置重进模块")
        if (navStack.size > 1) {
            val previous = navStack.last()
            XLog.w("从设置重进模块：页面栈清回首页（原 $previous）")
            // 一次删到底 —— 库把它当**一次多级 pop** 处理：整叠一起滑回首页，而不是
            // 「一串页面依次滑过去」。所以不需要那个「跳过转场直落」的开关
            // （手写那套只能播一条跨级的长滑页，才必须直落；见 [PAGE_TRANSITION]）。
            while (navStack.size > 1) navStack.removeAt(navStack.lastIndex)
        }
    }

    /**
     * 页面里**所有浮窗**的「统一收起」通道（见 [closeOverlays]）。
     *
     * 只存「怎么把我关掉」，不存「谁开着」—— 关一个本来就关着的浮窗是一次**无副作用的空写**
     * （`mutableStateOf` 写同值不触发重组），所以收的时候不必先判在不在。
     * 返回值 = 这一层刚才**真的开着**（只用来把日志写准）。
     */
    internal val overlayClosers = mutableListOf<() -> Boolean>()

    /**
     * 页面离开时**主动收掉所有浮窗**。
     *
     * 为什么必须「主动」：COUI 的 `OverlayListPopup` / `OverlayDialog` 都渲染在
     * **根 Scaffold 的 `popupHost`** 那一层（见库的 `Scaffold` 文档），它**不属于任何一页**
     * —— 既不跟着页面转场走，也不随「那一页出栈」以外的任何事消失；而首页顶栏那颗菜单
     * 更是**永远在栈里**（首页常驻），它的浮窗状态永远不会被组合释放。于是不主动收的话：
     * 从「设置」重进、切后台回来，菜单还开着；换页时浮窗还会**钉在屏幕上不动**，
     * 等那一页出栈才跟着消失。
     *
     * 触发点（都在「页面离开」这条需求路径上）：
     *  · 换页 / 清栈回首页 —— [Page] 里那个看着 `screen` 的 `LaunchedEffect`；
     *  · 从设置重进模块（栈顶未变时 LaunchedEffect 不触发）—— [onHostReentered]；
     *  · 顶栏返回键在首页（关页面）—— [PageHeader]；
     *  · 露出宿主原版界面 / 系统返回关页面 / 宿主 Activity `onStop` —— [SettingsPageHooks]。
     *
     * @param reason 只进日志，便于真机上核对「是哪条路把它收掉的」
     */
    internal fun closeOverlays(reason: String) {
        if (overlayClosers.isEmpty()) return
        // 复制一份再遍历：关闭过程中会触发重组，进而增删注册表
        val closers = overlayClosers.toList()
        val closed = closers.count { runCatching { it() }.getOrDefault(false) }
        // 一层都没开着时不写日志：页面离开是高频动作，每次都留一行「收掉 0 个」只会淹掉别的
        if (closed > 0) XLog.w("页面离开（$reason）：已收起 $closed 个浮窗")
    }

    /**
     * 浮窗在**组合期**把自己登记进 [overlayClosers] —— 各浮窗调用点只需要加一行：
     * `OverlayCloser(show = open) { open = false }`。
     *
     * 登记时机是「宿主组件进组合」，**不是**「浮窗弹出来」：`show` 只是浮窗自己的一个参数，
     * 组件一直挂着（`show = false` 时只是不渲染内容）—— 所以收起时那一下写的是「本来就该是
     * 的值」，不判空也不会误伤。
     *
     * `rememberUpdatedState` 是为了拿到**最新**的 `show` / `onDismiss`：那些值每次重组都会变，
     * 而注册只在第一次组合时做一次。
     *
     * @param show 这一层此刻开着没有 —— 只决定「这一层算不算被收掉了一个」（日志用）
     */
    @Composable
    internal fun OverlayCloser(
        show: Boolean,
        onDismiss: () -> Unit,
    ) {
        val latestShow by rememberUpdatedState(show)
        val latestDismiss by rememberUpdatedState(onDismiss)
        // 「有没有浮层开着」的计数 —— 预测返回的 onBackStarted 要用它决定接不接管
        // （见 [wouldConsumeBack]）。键是 `show`：翻转时旧的 effect 收尾、新的进来，
        // 一次翻转恰好各加减一次；组件出组合时若还开着也会补减。
        DisposableEffect(show) {
            if (show) openOverlays.intValue++
            onDispose { if (show) openOverlays.intValue-- }
        }
        DisposableEffect(Unit) {
            val closer = {
                if (latestShow) {
                    latestDismiss()
                    true
                } else {
                    false
                }
            }
            overlayClosers += closer
            onDispose { overlayClosers -= closer }
        }
    }

    // 深浅切换有两条路，都收在 [SyncToHost] 里：(1) `Settings.Secure.ui_night_mode` 的
    // `ContentObserver`（手动切走这条，即时）＋ (2) 每 [NIGHT_POLL_MS] 对一次账的轮询（定时切换兜底）。
    // 两条路的判据是同一个 —— [systemNight] 读到的**系统全局配置**。
    //
    // 真机抓到它把宿主**过期的 night 配置**（宿主声明了
    // `configChanges=uiMode`，配置停在旧值）喂进来，亮色进页先黑一秒再被轮询纠正。
    // 那条路已删：**回调只能当信号，值一律自己重新读系统全局配置**。

    /** 建好的 owner 集合，等 [attachOwnersUpward] 在 addView 之后往祖先链上挂。 */
    internal var pendingOwners: FallbackOwners? = null

    /** 已自建的兜底 owner（同一页面实例内复用，见 [ownOwners]）。 */
    internal var fallbackOwners: FallbackOwners? = null

    /**
     * 把 owner 挂到 [root] 的**整条祖先链**上（含 `root` 自己）。
     *
     *  **必须在 `container.addView(view)` 之后调用** —— 在那之前 `parent` 还是 null。
     * 真机第三轮崩溃换来的：`resolveParentCompositionContext` 只沿 **parent 链**找
     * （报错原文点名宿主布局根 `androidx.constraintlayout.widget.ConstraintLayout`）。
     *
     * @return 挂到了几层（日志用）
     */
    fun attachOwnersUpward(root: View): Int {
        val own = pendingOwners ?: return 0
        var node: View? = root
        var depth = 0
        while (node != null) {
            attachOne(node, own)
            node = node.parent as? View
            depth++
        }
        XLog.w("ViewTree owner 已挂满祖先链 $depth 层（ComposeView → decorView）")
        return depth
    }

    /** 往单个 View 挂三个 owner（**只挂不覆盖**）。 */
    internal fun attachOne(
        target: View,
        own: FallbackOwners,
    ) {
        try {
            if (target.findViewTreeLifecycleOwner() == null) target.setViewTreeLifecycleOwner(own)
            if (target.findViewTreeViewModelStoreOwner() == null) target.setViewTreeViewModelStoreOwner(own)
            if (target.findViewTreeSavedStateRegistryOwner() == null) {
                target.setViewTreeSavedStateRegistryOwner(own)
            }
        } catch (e: Throwable) {
            XLog.w("挂 ViewTree owner 失败（${target.javaClass.simpleName}）", e)
        }
    }

    /**
     * 自建的「三合一 owner」。
     *
     * 只在宿主确实缺 owner 时才创建；已创建过就复用（同一 Activity 反复进设置页时
     * 不自建第二份 —— 两份 `LifecycleRegistry` 会让 Compose 的 `Recomposer` 状态错乱）。
     *
     * **宿主三件套齐全时返回 `null`，而不是一个「用不上」的占位实例**：占位实例一旦被
     * [attachOne] 钉进 ViewTree（`attachOne` 只挂不覆盖），后面就再也换不掉，Compose
     * 会拿到那份停在 `INITIALIZED` 的 lifecycle —— 页面一片空白（见本文件里的调用点注释）。
     */
    internal fun ownOwners(
        hostLifecycle: androidx.lifecycle.LifecycleOwner?,
        hostViewModel: androidx.lifecycle.ViewModelStoreOwner?,
        hostSavedState: androidx.savedstate.SavedStateRegistryOwner?,
    ): FallbackOwners? {
        if (hostLifecycle != null && hostViewModel != null && hostSavedState != null) {
            return null
        }
        fallbackOwners?.let { return it }
        val created = FallbackOwners()
        fallbackOwners = created
        XLog.w(
            "宿主不提供 ViewTree owner（lifecycle=${hostLifecycle != null}" +
                " viewModel=${hostViewModel != null} savedState=${hostSavedState != null}）" +
                "⇒ 已自建 LifecycleRegistry + ViewModelStore + SavedStateRegistry 兜底",
        )
        return created
    }

    // FallbackOwners（宿主不提供 ViewTree owner 时的兜底三件套）与 CommandDraft
    // （「自定义命令」页的表单草稿）已搬到 `ui/state/PageState.kt` —— 两者都不引用本类的任何东西。
}

/**
 * 平台返回事件喂给 coui-nav 的入口。
 *
 *  系统的预测返回手势（`OnBackAnimationCallback`）在 hook 侧收到事件后调这里的方法，
 * 事件顺着 [NavigationEventInput] 的 protected 派发口进 dispatcher，再由 dispatcher 派给
 * 此刻开着的那个 handler —— COUI 的浮层（菜单 / 弹窗）也走同一个 dispatcher 且按
 * 「后组合的、开着的优先」仲裁，所以「浮层开着时先收浮层」不用在这里判，dispatcher 自己会
 * 挑对对象。
 *
 *  必须借 [NavigationEventInput] 而不是直接调 dispatcher：dispatcher 的 `onBack*`
 * 派发口是 internal，公开入口只有「挂一个 input」这一种。
 */
internal class NavBackInput : NavigationEventInput() {
    /** 手势落下（预测返回的第一帧）。 */
    internal fun started(event: NavigationEvent) = dispatchOnBackStarted(event)

    /** 手势拖动中（每一帧）。 */
    internal fun progressed(event: NavigationEvent) = dispatchOnBackProgressed(event)

    /** 手势提交（松手时过了判定线 ⇒ 弹栈，转场由库自己收尾）。 */
    internal fun completed() = dispatchOnBackCompleted()

    /** 手势取消（松手时没过判定线 ⇒ 库把页面弹回原位）。 */
    internal fun cancelled() = dispatchOnBackCancelled()
}
