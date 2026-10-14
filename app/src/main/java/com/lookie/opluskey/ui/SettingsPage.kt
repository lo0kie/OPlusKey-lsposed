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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.component.HeaderMenu
import com.lookie.opluskey.ui.component.PageHeader
import com.lookie.opluskey.ui.model.ABOUT_TITLE
import com.lookie.opluskey.ui.model.APP_PICKER_TITLE
import com.lookie.opluskey.ui.model.ActionCatalog
import com.lookie.opluskey.ui.model.BINDING_TITLE
import com.lookie.opluskey.ui.model.COMMANDS_TITLE
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.model.PAGE_COVER_DIM
import com.lookie.opluskey.ui.model.PAGE_TRANSITION
import com.lookie.opluskey.ui.model.SNACKBAR_SHADOW_ROOM
import com.lookie.opluskey.ui.model.specIcon
import com.lookie.opluskey.ui.screen.AboutScreen
import com.lookie.opluskey.ui.screen.AppPickerScreen
import com.lookie.opluskey.ui.screen.AppSearchBand
import com.lookie.opluskey.ui.screen.BindingScreen
import com.lookie.opluskey.ui.screen.BindingTabBand
import com.lookie.opluskey.ui.screen.CommandActions
import com.lookie.opluskey.ui.screen.CommandKindBand
import com.lookie.opluskey.ui.screen.CommandScreen
import com.lookie.opluskey.ui.screen.HomeScreen
import com.lookie.opluskey.ui.screen.actionOf
import com.lookie.opluskey.ui.screen.addCommand
import com.lookie.opluskey.ui.screen.addStep
import com.lookie.opluskey.ui.screen.appVersionLabel
import com.lookie.opluskey.ui.screen.cachedApps
import com.lookie.opluskey.ui.screen.pick
import com.lookie.opluskey.ui.screen.replaceStep
import com.lookie.opluskey.ui.screen.saveCommands
import com.lookie.opluskey.ui.state.CommandDraft
import com.lookie.opluskey.ui.state.FallbackOwners
import com.lookie.opluskey.ui.state.InjectStatus
import com.lookie.opluskey.ui.state.UiState
import com.lookie.opluskey.ui.state.publishConfig
import com.lookie.opluskey.ui.state.tickNow
import com.lookie.opluskey.ui.theme.SyncToHost
import com.lookie.opluskey.ui.theme.colorsFor
import com.lookie.opluskey.ui.theme.pageBackgroundColor
import com.lookie.opluskey.ui.theme.systemNight
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Snackbar
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
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
    internal val vibrator: android.os.Vibrator? = try {
        context.getSystemService(android.os.Vibrator::class.java)
    } catch (e: Throwable) {
        XLog.w("拿 Vibrator 失败（触觉反馈不可用）：${e.message}")
        null
    }

    /**
     * 静音（`RINGER_MODE_SILENT`）时不振，跟随系统礼节。
     * 与 [vibrator] 同样建页期拿好，点击路径上只剩一次 getter。
     */
    internal val audioManager: android.media.AudioManager? = try {
        context.getSystemService(android.media.AudioManager::class.java)
    } catch (e: Throwable) {
        XLog.w("拿 AudioManager 失败（响铃状态不可读）：${e.message}")
        null
    }

    internal val dark = mutableStateOf(false)

    /**
     * **页面栈**（coui-nav 的返回栈，2026-10-13 起）。
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
     *  拿不到的（**本集成方式下的已知缺口，不是遗漏**）：**系统预测返回**。
     *  库的 `NavDisplay` 内部用 `PredictiveBackHandler` 接系统返回手势，事件源是
     *  composition 里的 `LocalNavigationEventDispatcherOwner` —— 而我们的页面挂在宿主
     *  Activity 的 View 树上、宿主不提供这个 owner（见 [Page] 里那句 `parent = null` 的自建根
     *  dispatcher），所以那条链**从根上就断在外面**：本页的系统返回仍然由 `SettingsPageHooks`
     *  拦下来调 [goBack]。真要用上预测返回，得在 hook 侧把宿主的返回派发桥到这个 dispatcher 上
     *  ——那是独立的一件事，本轮没做。
     *
     *  ## 为什么用 `navBackStackOf` 而不是 `rememberNavBackStack`
     *
     *  后者带 `rememberSaveable` 持久化，**要求路由 key `@Serializable`**。我们不要恢复栈：
     *  页面进程死了（宿主被杀）再进来就该是首页那一档，恢复一个「上次停在自定义命令页」反而怪
     *  ——`onHostReentered` 里那条「从设置重进清回首页」正是这个意思。
     *
     *  ⚠️ 这个 `SnapshotStateList` 建在**类的初始化**里（不在组合里）：[SettingsPage] 实例的存活
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
     *  调用点只报「我要去哪」，不再自己算「该 push 还是该 pop」——原来那些
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
    private val openOverlays = mutableIntStateOf(0)

    /**
     * 系统返回要交给**页面栈**处理吗 —— **只判断，不动手**（`onBackStarted` 那一刻不能有副作用，
     * 所以不能调 [goBack] 来探）。**三种不接管**：
     *
     *   · 栈里只剩一页（首页）⇒ 那是「关掉整个设置页」，归宿主；
     *   · 浮层开着 ⇒ COUI 的浮层自己不接返回，喂进去没人消费，最后仍是页面栈弹
     *     （表现为「浮层盖着、底下页面跟着手指滑」）；
     *   · ⚠️ **这一退会被 [goBack] 拦住**（「自定义命令」页草稿改过 ⇒ 先弹「放弃未保存的命令」）：
     *     库的提交是 `onCommit = { currentOnBack.value() }`，**弹栈交给 [goBack]** —— 接管了就等于
     *     页面先滑走、栈却没弹，页面停在滑出去的位置回不来。判据与 [goBack] 那条**同一份**，一起改。
     */
    internal fun wouldConsumeBack(): Boolean =
        navStack.size > 1 &&
            openOverlays.intValue == 0 &&
            !(screen == Screen.COMMANDS && draft.dirty)

    /** 此刻有浮窗开着吗 —— 开着时系统返回**整个吞掉**（见 `SettingsPageHooks.handleDiscreteBack`）。 */
    internal fun hasOpenOverlays(): Boolean = openOverlays.intValue > 0

    /**
     * 建好的页面 View（[createView] 里赋值）。
     *
     * 只为一件事：进「按键绑定」子页时把 **Android 层的焦点**抢到这个 View 上
     * —— 方向键事件按焦点派发，没焦点就收不到（见 [ActionTile] 的注释）。
     */
    internal var rootView: View? = null

    /** 当前配置（页面自己读自己写）。 */
    internal val config = mutableStateOf(Config.DEFAULT)

    /**
     * 建页那次**没读成功**（文件在、但读不了 / 读不懂）⇒ 页面进**只读**：任何写回都被拒。
     *
     * 为什么要有这个开关：默认值长这样 —— 主开关**关**、三个手势全是「无操作」、**不振动**。
     * 读失败时若照常拿它渲染、又照常写回，用户配的东西就被整份替换成这份默认值。
     * 2026-10-14 真机上真的发生过（先丢的是「振动反馈」，只剩这一个开关被人一眼看出来），
     * 根因是读失败只有「退化成默认值」一条路、写方那边没有再拦一道（见 `ConfigStore.Load`）。
     *
     * 退出方式：**重进页面**（[createView] 会重读一次）；或走 ⋮ 菜单的「默认界面」离开；
     * 或点「清空配置」——那是用户明说要丢掉重来，`ui/state/ConfigActions.resetAll` 会先把这一位清掉再写。
     *
     *  全项目只有两处写它：[createView] 按读到的那一态赋值，以及 [resetAll] 的放行。
     * 所以没加 `private set`（同文件的扩展函数要写）—— 别在别处顺手置 false，那正是这道闸要防的事。
     */
    internal var configReadOnly by mutableStateOf(false)

    /**
     * **自定义命令库**（`core/config/CommandLibrary` 里那一串）—— 界面自己的状态，与 [config] 分开。
     *
     * 命令的**内容**存在命令库里，绑定到手势时写进主配置的只是 `cmd:<名字>` 这个引用
     * （与别的动作一样只是 `plus_single` 里的一个取值，注入侧不需要知道名字背后是什么）。
     * 这份列表变了要重组的是 [ActionCatalog.forGesture] 的调用点
     * （见 [Page] 里的 `bindingIcons` 与 [BindingList]）。
     */
    internal val commands = mutableStateOf<List<Command>>(emptyList())

    /**
     * 三条**固定带**的高度（px）—— 分段控件带 / 类型选择器带 / 搜索带。
     *
     *  带子挂在**顶栏的 `bottomContent`** 里自己 `onSizeChanged` 量，页内那个等高的占位
     * Spacer 读（见 [PageHeader] 的 `bottomContent`）。两者分处两棵树（顶栏 / 页内容），
     * 只能走页面级状态；首帧还没量到时是 0（占位少一截），下一帧自纠。
     */
    internal val bindingBandHeight = mutableStateOf(0)
    internal val commandBandHeight = mutableStateOf(0)
    internal val appSearchBandHeight = mutableStateOf(0)

    /**
     * 「选择应用」页的搜索词。
     *
     *  放在类上（而不是那一页里 `remember`）：搜索框现在挂在**顶栏**里（[AppSearchBand]），
     * 页面里的过滤逻辑与它读同一份 —— 两棵树共用，只能提上来。
     *  ⚠️ 随之多一笔账：那份 `remember` 原来是**随页面出栈一起注销**的（再进来天然是空的），
     * 提到类上就得**手动清** —— 两个入口（「按键绑定」页的「打开应用」格、添加命令浮层的
     * 「选择应用」）都补了 `appFilter.value = ""`。
     */
    internal val appFilter = mutableStateOf("")

    /**
     * 「选择应用」页顶栏菜单里那一项「显示全部应用」（见 [AppPickerMenu]）。
     *
     *  开关在**顶栏那棵树**上、列表在页内容那棵树里，所以同样提到类上（与 [appFilter] 一个理由）。
     *  ⚠️ 与 [appFilter] 相反：它**不随进页清空** —— 那是「我想看到全部应用」这个**意图**，
     * 不是一次输入。而且它**会落盘**：本地持久化在 [UiState]（**不进用户配置**，见那边的注释），
     * 进页时读一次（[createView]），所以页面重建 / 宿主 Activity 重建之后仍是上次那一档。
     *
     *  ⚠️ 叫 `showSystemApps` 是**历史包袱**：这一档原先只把系统应用追进来（菜单也叫
     * 「显示系统应用」），后来去掉了 `FLAG_SYSTEM` 那条筛子、把没有启动图标的第三方应用
     * （典型是各个 LSPosed 模块）一并列出来，菜单名改了，变量名没跟着改 —— 落盘的键
     * `show_system_apps` 也保持原名，改名会让用户的这一档回默认。
     */
    internal val showSystemApps = mutableStateOf(false)

    /**
     * 「选择应用」页顶栏菜单里那一项「隐藏不可打开」（见 [AppPickerMenu]）。
     *
     *  与 [showSystemApps] 同一套账：提到类上（开关在顶栏那棵树、列表在页内容那棵树）、
     * **不随进页清空**、**落盘**在 [UiState]，进页读一次（[createView]）。
     *
     *  判据是 [LaunchableApp.launchable]（[com.lookie.opluskey.host.action.AppEntry.resolve] 解不解得出来）——
     *  与执行端同一把尺子，所以藏掉的正是「按下去必弹 toast」的那些。
     */
    internal val hideUnopenable = mutableStateOf(false)

    /**
     * 「选择应用」菜单里那一项「以小窗口打开」（见 [AppPickerMenu]）。
     *
     *  它不筛列表，只决定**挑中的应用写成哪个动作**：开着写 `window:<包名>`（小窗起），
     *  关着写 `app:<包名>`。
     *
     * ⚠️ **它是临时的，不是偏好**（2026-10-14 用户口径）：**不落盘**、**没有跨次记忆** ——
     * 每次进「选择应用」页都由 [resetSmallWindowForPicker] 按**正在编辑的那个动作**重算
     *（本来就是 `window:` 就开着、`app:` 或新绑就关着）。与 [showSystemApps] / [hideUnopenable]
     * 那两项**相反**（那两项是显示偏好，落在 [UiState] 里）。
     *  理由：这一项描述的是「这一次要往哪个动作上写」，不是「这一页该怎么显示」——
     *  留着上一次的 `true` 会让下一次挑应用**静默**变成小窗，用户看不出自己什么时候开的。
     *
     * ⚠️ **为什么不能像页面自己那样随出栈销毁**（与 [appFilter] 同一笔账）：这一项是**顶栏菜单**里
     * 的一项，而顶栏（`PageHeader` → [AppPickerMenu]）与页内容是本文件同一层 `Box` 里的**两棵并列树**
     * —— 页面本体挂在 `when (s)` 的分支里、出栈即注销，顶栏不走。所以：
     *  · 页面那棵树的 `remember` 顶栏**读不到**（`AppPickerScreen` 不是顶栏的祖先）；
     *  · 状态只能提到两棵树共同的祖先（本类）上，于是**不会**随页面出栈销毁，
     *    也就必须像 [appFilter] 那样在进页时**自己初始化** —— 这就是 [resetSmallWindowForPicker] 那笔账。
     * 页面里凡是真的只归页面自己用的（比如那句确认窗的挂起项），照旧用 `remember`、随出栈注销。
     */
    internal val smallWindow = mutableStateOf(false)

    /**
     * 这一趟「选择应用」页在**编辑哪个动作** —— 两个入口各有来源（都在进页那一刻记下）：
     *  · 网格入口（「按键绑定」页的「打开应用」格）→ [_appPickerGesture] 那个手势绑的动作；
     *  · 「自定义命令」页那条路 → 被**换掉**的那一步（[_appPickerReplaceStep]）。
     *
     * `null` = 新绑（没在编辑任何动作）。[appPickerSelectedPkg] 与 [resetSmallWindowForPicker]
     * 都要它，两处必须同源 —— 否则会出现「行点亮了、开关却是关的」这种自相矛盾的界面。
     */
    private fun editingAction(): Action? = if (_appPickerForStep) {
        _appPickerReplaceStep.takeIf { it >= 0 }
            ?.let { draft.steps.getOrNull(it) }
            ?.let { runCatching { Action.parse(it) }.getOrNull() }
    } else {
        _appPickerGesture?.let { actionOf(config.value, it) }
    }

    /**
     * 进「选择应用」页时把「以小窗口打开」**重算**一遍（入口两处各调一次）。
     *
     * 判据就是 [editingAction]：正在编辑的那个动作本来就是 `window:<包名>` ⇒ 开；
     * `app:<包名>` / 别的动作 / 新绑（`null`）⇒ 关。
     */
    internal fun resetSmallWindowForPicker() {
        val on = editingAction() is Action.AppWindow
        smallWindow.value = on
        XLog.w("选择应用：以小窗口打开按正在编辑的动作重算 = $on")
    }

    /**
     * 「自定义命令」页正在编辑的那条命令（草稿）。
     *
     *  **提到类上、而不是放在 [CommandScreen] 里**：它要**跨重组**活着。
     * 「添加命令」现在是页内浮层（[StepPickerPopup]），不再是单独一级页面，
     * 但草稿仍然属于整个「自定义命令」页 —— 从别的页面回来时不该被重新组合清掉；
     * 每次进这一页由 [BindingList] 的跳转按钮显式 [CommandDraft.reset]。
     */
    internal val draft = CommandDraft()

    /**
     * 「重置」走**行级退场**时的挂起标记（2026-10-13）。
     *
     *  工具条的重置对「有多条步骤的多条命令」不直接 [CommandDraft.reset]：
     * 整批清空会让所有步骤行瞬时消失。先置这里，[SerialSteps] 里每一行播完
     * 自己的 shrink 退场之后，由它真正 reset（见 CommandScreen 的收尾协程）。
     */
    internal var draftResetPending by mutableStateOf(false)

    /**
     * **调试通道**要切手势 tab 时往这里放一个请求（见 [onDebugCommand]）。
     *
     * [Page] 里一个 `LaunchedEffect` 看着这个值 —— 有请求就滚过去、然后清掉。
     * release 包里没人写它（那条注册是死分支），恒为 `null`。
     */
    internal val debugGesture = mutableStateOf<Gesture?>(null)

    /** 「清空配置」确认弹窗（调试通道经 `action=CONFIRM_RESET` 置 true 弹出）。 */
    internal val confirmReset = mutableStateOf(false)

    /** 首页标题 —— 宿主 Activity 的原文，hook 侧在 [createView] 之前赋值。 */
    var homeTitle: String = "快捷键"

    /** 系统深浅变化回调（给 hook 重刷状态栏配色）。 */
    var onSystemThemeChanged: (() -> Unit)? = null

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

    /** 页面底色 —— COUI 色板的 `surface`（深色 `#000000`、浅色 `#F0F1F2`，与系统设置页一致）。 */
    fun createView(): View? {
        dark.value = systemNight()
        // 配置读成**三态**：只有「读到过正常文件」与「还没写过」才允许之后写回。
        //  读不懂 ⇒ 只读（[configReadOnly]）：手上那份默认值不是用户的真相，不能让它落盘。
        when (val l = ConfigStore.load()) {
            is ConfigStore.Load.Ok -> {
                config.value = l.config
                configReadOnly = false
            }

            ConfigStore.Load.Absent -> {
                XLog.w("还没有配置文件（首装 / 刚清过数据）—— 用默认值，可以写回")
                config.value = Config.DEFAULT
                configReadOnly = false
            }

            ConfigStore.Load.Unreadable -> {
                // 这条必须是 w：它是「页面为什么变只读」的唯一判据行
                XLog.w("配置读不懂 ⇒ 页面进只读，本次不许写回（否则会把默认值当真相固化）")
                config.value = Config.DEFAULT
                configReadOnly = true
            }
        }

        // 命令库与配置一起在建页期读好（读不到按空表，见 CommandLibrary 的降级纪律）
        commands.value = CommandLibrary.load()

        // 界面状态（本地持久，**不是用户配置** —— 见 [UiState]）与上面两份一起读一次：
        // 读不到 / 解析不了都退默认（关）。放这里是因为它同样只读一次、且必须早于首帧
        //（顶栏菜单那两项要按它画选中态，列表也要按它决定查哪一份）。
        //  一次读全（readAll）：两项各读一遍会把同一个文件 open + parse 两遍，而这跑在主线程上。
        //  只读两项 —— 「以小窗口打开」**不落盘**（见 [smallWindow]），不在这一份里。
        val ui = UiState.readAll()
        showSystemApps.value = ui.first
        hideUnopenable.value = ui.second
        XLog.w(
            "界面状态：显示全部应用=${showSystemApps.value}，隐藏失效=${hideUnopenable.value}",
        )

        // 建页时把**完整配置**主动同步一次给注入侧（判定在那一侧，见 [publishConfig]）：
        // 「装好后没动过任何设置」的状态下，注入侧只能等宿主进程的解锁广播，
        // 打开这一页就立刻同步一次，作为兜底。
        publishConfig(config.value)

        XLog.w(
            "命令库：${commands.value.size} 条" +
                    "（单条 ${commands.value.count { it.kind == CommandKind.SINGLE }}" +
                    " / 多条 ${commands.value.count { it.kind == CommandKind.SERIAL }}）",
        )

        val view = ComposeView(context).apply {
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
        val own = ownOwners(
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

    @Composable
    private fun Page() {
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
                    //（顶栏 107dp 含状态栏 inset，底色铺到 y=0），所以顶栏的模糊、
                    // 浮层的 dim 都自然作用到它 —— 2026-10-12 之前那条盖着 hook 侧的
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
                        //（见 [closeOverlays]），不收的话它会**钉在屏幕上**：页面滑走了它还在，
                        // 要等那一页出栈才跟着消失。
                        LaunchedEffect(navStack.lastOrNull()) { closeOverlays("换页 → ${navStack.lastOrNull()}") }
                        // 调试通道：只有 debug 源集有实现，release 是同名空壳（`register` 恒 null，
                        // 这段是死代码）。让 `am broadcast` 能直接驱动这个页面，省掉「dump 找坐标 →
                        // 模拟点击 → 打字被输入法吃掉」那一套。
                        DisposableEffect(Unit) {
                            val receiver = PageDebugChannel.register(context) { onDebugCommand(it) }
                            onDispose { receiver?.let { runCatching { context.unregisterReceiver(it) } } }
                        }
                        // 两条「链路自检」的回音 —— 一个接收器兼收，省一份注册 / 注销的壳：
                        //  · 宿主侧报活（[KeyModule.ACTION_HOST_SERVICE_UP]）：模块加载好了。
                        //    首页据此把「模块未生效」的提示收掉、解除整页禁用。**正式通道，release 也有**。
                        //  · 注入侧回包（[KeyModule.ACTION_INJECT_READY]）：注入侧那套装上没、配置到没到。
                        //    发送方是 system_server（uid 1000），`NOT_EXPORTED` 照样可达。
                        DisposableEffect(Unit) {
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(c: Context?, intent: android.content.Intent?) {
                                    when (intent?.action) {
                                        KeyModule.ACTION_HOST_SERVICE_UP -> {
                                            XLog.w("宿主侧模块报活（收到广播）⇒ 首页解除禁用")
                                            hostServiceUp = true
                                            hostAliveToken++
                                            // 报活里带的两条诊断字段（链路自检用）
                                            hostEnabled = intent.getBooleanExtra(
                                                KeyModule.EXTRA_HOST_ENABLED, true,
                                            )
                                            hostLastActionAt = intent.getLongExtra(
                                                KeyModule.EXTRA_HOST_LAST_ACTION_AT, 0L,
                                            )
                                        }

                                        KeyModule.ACTION_INJECT_READY -> {
                                            val status = InjectStatus(
                                                hooks = intent.getIntExtra(KeyModule.EXTRA_INJECT_HOOKS, 0),
                                                configOk = intent.getBooleanExtra(
                                                    KeyModule.EXTRA_INJECT_CONFIG_OK, false,
                                                ),
                                                configTag = intent.getStringExtra(
                                                    KeyModule.EXTRA_INJECT_CONFIG_TAG,
                                                ).orEmpty(),
                                                takeover = intent.getBooleanExtra(
                                                    KeyModule.EXTRA_INJECT_TAKEOVER, true,
                                                ),
                                                lastGestureAt = intent.getLongExtra(
                                                    KeyModule.EXTRA_INJECT_LAST_GESTURE_AT, 0L,
                                                ),
                                            )
                                            XLog.w(
                                                "注入侧自检回包：接管点=${status.hooks} " +
                                                    "配置已送达=${status.configOk} " +
                                                    "指纹=${status.configTag.ifEmpty { "无" }}",
                                            )
                                            injectStatus = status
                                            injectAliveToken++
                                        }
                                    }
                                }
                            }
                            val ok = runCatching {
                                context.registerReceiver(
                                    receiver,
                                    IntentFilter().apply {
                                        addAction(KeyModule.ACTION_HOST_SERVICE_UP)
                                        addAction(KeyModule.ACTION_INJECT_READY)
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
                        //（coui-nav 的浮层与 [NavDisplay] 也都往同一个 dispatcher 挂 handler，
                        // 「谁先消费」由它仲裁）。页面销毁自动摘掉。
                        DisposableEffect(navOwner) {
                            val dispatcher = navOwner.navigationEventDispatcher
                            dispatcher.addInput(navBackInput)
                            onDispose { dispatcher.removeInput(navBackInput) }
                        }
                        // ── 页面级状态：**不随页面出栈注销**的那几样 ──
                        //
                        //  分页状态与全量图标表都提在这里创建，而不是各自页面里 `remember`：
                        //   · [pagerState] / [commandPager] 各有一条**固定带**要读（带子挂在顶栏的
                        //     `bottomContent` 里、内容在页里，两棵树只能共用同一份状态）；
                        //   · [bindingIcons] 是全量图标表（每个手势 × 它能选的每个动作），
                        //     `remember` 的键是 `cmds` —— 命令库不变就不重算，页面进出也不重算。
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
                        //  `remember` 的键是 `cmds`：命令库变了图标表必须跟着重建，
                        // 否则新增的那几个格子取不到图标（`icons[spec]` 拿到 null，格子里空一块）。
                        val cmds = commands.value
                        val bindingIcons = remember(cmds) {
                            Gesture.entries.associateWith { g ->
                                ActionCatalog.forGesture(g, cmds).associateWith { specIcon(it) }
                            }
                        }

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
                                Screen.BINDING -> pagerState.scrollToPage(0)
                                // 「自定义命令」页**按草稿的类型**定位，不是一律回第一页：
                                // 从「按键绑定」网格长按 / 已选中再点一下某条命令进来时，
                                // `draft.load(cmd)` 已经把 `kind` 设成那条命令的类型 —— 一律回 0
                                // 的话单条 / 多条那条 tab 会停在错的档上，紧接着 `draft.kind`
                                // 还会被分页器反着覆盖回「单条」（真机反馈「长按自定义命令跳转后，
                                // 多条命令的 tab 没有对应切换」）。
                                //  从 FAB 进来时草稿刚 `reset()`（kind = SINGLE），等价于回第一页 ✓。
                                Screen.COMMANDS -> commandPager.scrollToPage(
                                    CommandKind.entries.indexOf(draft.kind).coerceAtLeast(0),
                                )

                                else -> Unit
                            }
                        }

                        // —— 页面栈 ——
                        //
                        //  一页一个 entry，但 `entry` 只注册一次：库按 key 的**运行时类**取注册
                        //（`entry<T>` 就是拿 `T::class` 建的索引），而 `Screen` 是个枚举 ——
                        //  五个分量的运行时类是同一个，所以注册一次就够，页与页的区别全在下面那个
                        // `when`。每页独享的东西（`scrollBehavior`、取样层 `backdrop`）写在 entry
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
                            effects = NavDisplayEffects(
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
                                //  「首页」「更多选项」的标题**不折叠**（2026-10-13 定案）：库的
                                // 开关是 `canScroll` —— false 时顶栏不再消费滚动事件
                                //（`ExitUntilCollapsedScrollBehavior` 里 `if (!canScroll()) return Zero`），
                                // 大标题钉在原位、内容照常从它底下滚过；其余三页照常折叠。
                                //
                                //  ⚠️ **每页都必须自己给初始 `heightOffsetLimit`**（原来只给钉住的两页，
                                // 折叠那三页靠「频繁重组自愈」，实测会卡在坏态）：
                                //  库默认是 `-Float.MAX_VALUE`，而那个字段是**普通 var** —— 顶栏在布局期
                                //  把它改写成真实折叠量时**不触发重组**。于是 [currentHeaderHeight] 首帧
                                //  读到 -MAX，`52dp - (-1.2e38dp)` 让 `Modifier.height(...).roundToPx()`
                                //  整型溢出成 0：顶部让位的 Spacer 变 0、内容整段顶到屏幕最上被顶栏盖住
                                //（「关于模块」页与「按键绑定」页 2026-10-13 各实证一次）。折叠页只在
                                //  恰好有别的重组时自愈 ⇒ 表现为「偶尔有些页面没给 Header 让位」。
                                //  初始值给「展开 − 折叠」那一档（像素）；[currentHeaderHeight] 另有一道
                                //  认哨兵的兜底，双保险。
                                val scrollBehavior = COUIScrollBehavior(
                                    state = rememberTopAppBarState(
                                        initialHeightOffsetLimit = with(LocalDensity.current) {
                                            -(TopAppBarDefaults.ExpandedHeight -
                                                    TopAppBarDefaults.CollapsedHeight).roundToPx().toFloat()
                                        },
                                    ),
                                    // 只有「首页」「关于模块」两页钉住标题（不折叠），其余三页照常折叠。
                                    canScroll = { s != Screen.SHORTCUTS && s != Screen.ABOUT },
                                )

                                // 顶栏模糊的背景层（COUI「模糊效果」：`layerBackdrop` 抓、
                                // `drawBackdrop` 在顶栏上取样）。
                                //
                                //  `layerBackdrop` **只抓这个节点自己画的内容**，不含父级背景
                                // —— 不铺底色的话，透明区域会让模糊把颜色扩散进去、糊成色块。
                                val surfaceColor = COUITheme.colorScheme.surface
                                val backdrop = rememberLayerBackdrop {
                                    drawRect(surfaceColor)
                                    drawContent()
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
                                            .layerBackdrop(backdrop),
                                    ) {
                                        when (s) {
                                            Screen.SHORTCUTS -> HomeScreen(scrollBehavior)
                                            Screen.BINDING -> BindingScreen(
                                                pagerState,
                                                bindingIcons,
                                                // 首页那个主开关（[KeyConfig.enabled]）—— 关掉时整页禁用
                                                enabled = config.value.key(KeyId.PLUS).enabled,
                                                onOpenAppPicker = { g ->
                                                    _appPickerGesture = g
                                                    _appPickerForStep = false
                                                    // 搜索词清空（页面级状态，不再随页面出栈注销，
                                                    // 见 [appFilter]；另一条入口在 [openAppPickerForStep]）
                                                    appFilter.value = ""
                                                    // 「以小窗口打开」按**这次要编辑的动作**重算
                                                    //（不落盘、不跨次记忆，必须在两个入口都调）
                                                    resetSmallWindowForPicker()
                                                    // 父级不用记：压它下面的是谁，返回就退到谁
                                                    navTo(Screen.APP_PICKER)
                                                },
                                                scrollBehavior = scrollBehavior,
                                            )

                                            Screen.COMMANDS -> CommandScreen(scrollBehavior, commandPager)
                                            // 挑应用。**两个入口共用这一页**（挑中的东西不一样）：
                                            //  · 网格里的入口格「打开应用」→ 把**当前手势**绑成
                                            //    `app:<包名>`（走现成的 `pick()`，它同时负责
                                            //    「选动作顺手把主开关打开」那条纪律）；
                                            //  · 「添加命令」浮层里的「选择应用」→ 把 `app:<包名>`
                                            //    作为**一步**加进当前多条命令（走 `addStep()`，见
                                            //    `openAppPickerForStep`）。
                                            Screen.APP_PICKER -> AppPickerScreen(
                                                // 「当前已选的那个应用」：行尾点亮单选圈 + 进页滚到它眼前。
                                                // 两条入口的来源见 [appPickerSelectedPkg] —— 网格那条读手势，
                                                // 「自定义命令」那条读**被换掉的那一步**。
                                                selected = appPickerSelectedPkg(),
                                                onPick = { pkg, window ->
                                                    tickNow()
                                                    // 写成哪个动作由菜单里那项「小窗打开」定（见 [smallWindow]）
                                                    val token = if (window) "window:$pkg" else "app:$pkg"
                                                    if (_appPickerForStep) {
                                                        // 「加一步 / 换一步」： 这里**不用 `goBack()`** ——
                                                        // `addStep()` / `replaceStep()` 自己会把页面切回
                                                        // 「自定义命令」页（那才是这次挑应用的来处），
                                                        // 从「打开应用」退到那一页就是它的 pop 转场。
                                                        _appPickerForStep = false
                                                        val target = _appPickerReplaceStep
                                                        _appPickerReplaceStep = -1
                                                        if (target >= 0) {
                                                            replaceStep(target, token)
                                                        } else {
                                                            addStep(token)
                                                        }
                                                    } else {
                                                        // 写的是**进这一页时那个手势**（见 _appPickerGesture），
                                                        // 不是 pager 的当前页 —— 这一页不属于 pager。
                                                        _appPickerGesture?.let { g ->
                                                            pick(
                                                                ActionCatalog.Spec(
                                                                    title = pkg,
                                                                    summary = token,
                                                                    token = token,
                                                                ),
                                                                g,
                                                            )
                                                        }
                                                        //  挑完了要清掉：goBack() 靠它判断「是不是没挑就返回」
                                                        _appPickerGesture = null
                                                        goBack()
                                                    }
                                                },
                                                scrollBehavior = scrollBehavior,
                                            )

                                            // 「关于模块」：版本 / 仓库地址两行，从首页 ⋮ 菜单进
                                            Screen.ABOUT -> AboutScreen(scrollBehavior)
                                        }
                                    }

                                    // 首页那条「模块未生效」提示（见 [hostServiceSnackbar]）。
                                    //  只挂在首页；**不占布局位置** —— `SnackbarHost` 内部就是底部对齐的浮层，
                                    // 页面内容照常铺满（落点在屏幕底边，与顶部横幅的 TopTips 不是一个位置）。
                                    if (s == Screen.SHORTCUTS) {
                                        SnackbarHost(
                                            state = hostServiceSnackbar,
                                            modifier = Modifier
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
                                    //  它必须挂在这里（页内、但那个模糊**记录层之外**）：
                                    // 顶栏模糊取样的就是上面那个 `layerBackdrop` 节点 ——
                                    // `drawBackdrop` 的节点**不能落进它自己取样的那一层**，
                                    // 否则记录层为了画自己得再画它、它又去取样记录层……渲染节点链
                                    // 无限增长，最终 RenderThread **栈溢出**（`RenderNode::prepareTreeImpl`
                                    // 递归 500+ 层），宿主 UI 进程当场被杀 —— 真机崩过一次，就是这个。
                                    // 所以工具栏与顶栏同一个挂法：页内、记录层外，只是贴在底部。
                                    // （它自己那层模糊暂时没上，理由见 [CommandActions] 的注释。）
                                    //
                                    // 只在这一页显示；整页 push / pop 转场时它跟着一起滑
                                    //（挂在页里，不是挂在页面之外）。
                                    if (s == Screen.COMMANDS) {
                                        CommandActions(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = COMMAND_TOOLBAR_BOTTOM),
                                        )
                                    }

                                    // 顶栏：叠在内容**之上**（`Box` 里后声明的画在上面），
                                    // 半透明底 + 模糊从它底下滚过去的内容 —— 见 [PageHeader]。
                                    PageHeader(
                                        title = when (s) {
                                            Screen.SHORTCUTS -> homeTitle
                                            Screen.BINDING -> BINDING_TITLE
                                            Screen.COMMANDS -> COMMANDS_TITLE
                                            Screen.APP_PICKER -> APP_PICKER_TITLE
                                            Screen.ABOUT -> ABOUT_TITLE
                                        },
                                        // 大标题**下面**那行（库原本的底线副标题槽位，自己占一行）：
                                        // 首页 = 模块版本（与「关于模块」的「版本」行同源）。
                                        largeTitleSubtitle = when (s) {
                                            Screen.SHORTCUTS -> remember { appVersionLabel() }
                                            else -> ""
                                        },
                                        // 大标题**右边**那行小标题（补丁新增的槽位，折叠后保留）：
                                        // 「自定义命令」= 已保存的命令条数（[commands] 变了跟着重组）；
                                        // 「选择应用」= 当前已选的应用名（两条入口见
                                        // [appPickerSelectedPkg]，查不到清单就退回包名）。
                                        subtitle = when (s) {
                                            // 一条都没保存时不报数（「已保存0条命令」读起来像统计，也不成句）——
                                            // 直接说「没有保存的命令」，与页内那块空态同一句话（2026-10-14 用户口径）。
                                            Screen.COMMANDS -> if (commands.value.isEmpty()) {
                                                "没有保存的命令"
                                            } else {
                                                "已保存${commands.value.size}条命令"
                                            }
                                            Screen.APP_PICKER -> {
                                                val pkg = appPickerSelectedPkg()
                                                if (pkg == null) {
                                                    // 没选过就不摆一行「已选择: 无」—— 那种写法像是有个叫「无」的应用
                                                    "还未选择应用"
                                                } else {
                                                    val label = cachedApps
                                                        ?.firstOrNull { it.packageName == pkg }
                                                        ?.label ?: pkg
                                                    "已选择: $label"
                                                }
                                            }

                                            else -> ""
                                        },
                                        // 菜单按页分派：**只有首页与「选择应用」页有菜单**
                                        //（前者是打开默认界面 / 清空配置，后者只有一项
                                        // 「显示系统应用」，见 [AppPickerMenu]）。
                                        //
                                        //  「按键绑定」页那一套「新增命令」菜单 2026-10-11 晚些时候**撤掉了**
                                        // 不再有 ⋮。进「自定义命令」页的唯一入口现在是那一页右下角的
                                        // 那颗 FAB（见 [BindingScreen]）。
                                        menu = when (s) {
                                            Screen.SHORTCUTS -> HeaderMenu.HOME
                                            Screen.APP_PICKER -> HeaderMenu.APP_PICKER
                                            else -> HeaderMenu.NONE
                                        },
                                        scrollBehavior = scrollBehavior,
                                        backdrop = backdrop,
                                        // 顶栏底下那条**固定带**（三条带子各归各的页）——
                                        // 它们原先是各页自己叠的一层浮层（自带取样层 / 模糊 /
                                        // 半透明底 / 内容里等高占位），2026-10-13 统一搬进顶栏这个
                                        // `bottomContent` 槽位：位置、底色、模糊、下沿那条细线都由
                                        // 顶栏那一份负责，见 [PageHeader] 与三个带子自己的 KDoc。
                                        //  带子跨度与内容高度都各自报给页面级状态（[bindingBandHeight]
                                        // 那三个），页内那个占位 Spacer 读 —— 带子与内容分处两棵树，
                                        // 只有这一条路能对齐。
                                        bottomContent = {
                                            when (s) {
                                                Screen.BINDING -> BindingTabBand(
                                                    pagerState,
                                                    // 首页那个主开关（[KeyConfig.enabled]）——
                                                    // 关掉时带子灰掉且吃掉输入（与那颗 FAB 同款）
                                                    enabled = config.value.key(KeyId.PLUS).enabled,
                                                )

                                                Screen.COMMANDS -> CommandKindBand(commandPager)
                                                Screen.APP_PICKER -> AppSearchBand()
                                                // 首页与「关于模块」没有固定带
                                                Screen.SHORTCUTS, Screen.ABOUT -> Unit
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

    /**
     * 顶栏**当前**占掉的高度（含它自己那块状态栏 inset）—— 内容顶部要留出这么多。
     */
    /**
     * 调试通道的处理体 —— **只有 debug 包会走到这里**（实现只在 `src/debug/` 源集）。
     *
     * 支持的动作见 [PageDebugChannel] 的类注释（`screen` / `gesture` / `kind` /
     * `name` / `cmd` / `steps` / `del` / `reset` / `save` / `action` / `state`，一次可以带多个）。
     *
     * 执行顺序是**固定的**：先跳页、再切 tab、再填字段、最后才 reset / save —— 这样
     * 「`--es screen COMMANDS --es kind SERIAL --es name X --ez save true`」这一条广播
     * 就是一个完整的用例。认不出的取值只记日志、不抛异常。
     */
    private fun onDebugCommand(intent: android.content.Intent) {
        intent.getStringExtra("screen")?.let { raw ->
            val target = Screen.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            if (target != null) {
                XLog.w("调试：跳页 → $target")
                // 走 [navTo]（不是「压一层」）：目标已经在栈里就直接回到它，否则压上去。
                // 于是 `--es screen COMMANDS` 从首页一步落到「自定义命令」页，
                // 上面那条「push 时拨分页状态」的 effect 照样会跑到（栈深变深了）。
                navTo(target)
            } else {
                XLog.w("调试：认不出的 screen=$raw（可用：${Screen.entries.joinToString()}）")
            }
        }
        intent.getStringExtra("gesture")?.let { raw ->
            val target = Gesture.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            if (target != null) {
                XLog.w("调试：手势 tab → $target")
                debugGesture.value = target
            } else {
                XLog.w("调试：认不出的 gesture=$raw（可用：${Gesture.entries.joinToString()}）")
            }
        }
        intent.getStringExtra("kind")?.let { raw ->
            val target = CommandKind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            if (target != null) {
                XLog.w("调试：命令类型 → ${target.id}")
                draft.kind = target
            } else {
                XLog.w("调试：认不出的 kind=$raw（可用：${CommandKind.entries.joinToString()}）")
            }
        }
        // 字段一律走**与界面同一条清洗**（名称过 `sanitizeName`），否则填进去的值
        // 与手敲的不是一回事，验出来的结论就不作数
        intent.getStringExtra("name")?.let { draft.name = CommandLibrary.sanitizeName(it) }
        intent.getStringExtra("cmd")?.let { draft.single = it }
        intent.getStringExtra("steps")?.let { raw ->
            draft.steps.clear()
            draft.steps.addAll(raw.split(';').map { it.trim() }.filter { it.isNotEmpty() })
        }
        if (intent.getBooleanExtra("reset", false)) {
            XLog.w("调试：重置表单（等于点「重置 / 取消」）")
            draft.reset()
        }
        // 删一条命令 —— 给「填进去 → 保存 → 验完 → 清掉」这种自测收尾用，
        intent.getStringExtra("del")?.let { raw ->
            val name = CommandLibrary.sanitizeName(raw)
            if (commands.value.none { it.name == name }) {
                XLog.w("调试：没有叫 '$name' 的命令，不用删")
            } else {
                XLog.w("调试：删除命令 '$name'")
                saveCommands(commands.value.filterNot { it.name == name }, "调试删除：$name")
            }
        }
        if (intent.getBooleanExtra("save", false)) {
            XLog.w("调试：触发保存（等于点「保存 / 修改」）")
            addCommand()
        }
        // 通用动作派发：`--es action NAME` —— 页面级调试动作统一走这张表，加新动作只动这里
        intent.getStringExtra("action")?.let { raw ->
            when (raw.uppercase()) {
                "CONFIRM_RESET" -> {
                    XLog.w("调试：action=CONFIRM_RESET → 弹出清空确认弹窗")
                    confirmReset.value = true
                }

                else -> XLog.w(
                    "调试：认不出的 action=$raw（可用：CONFIRM_RESET）",
                )
            }
        }
        if (intent.getBooleanExtra("state", false)) logDebugState()
    }

    /**
     * 把当前状态打进日志（TAG `OplusKey`）—— 省掉「`uiautomator dump` 一遍再看」。
     *
     * 打的是**配置里的真值**（三个手势各绑了什么、主开关）与**表单草稿**，
     * 验「保存后到底写进去没有」「返回时有没有把绑定冲掉」这类问题时直接看这一行。
     */
    private fun logDebugState() {
        val key = config.value.key(KeyId.PLUS)
        XLog.w(
            "调试·状态：screen=$screen 栈=${navStack.joinToString(">")} 主开关=${key.enabled} " +
                    "单击=${key.single.token} 双击=${key.double.token} 长按=${key.long.token} | " +
                    "表单 kind=${draft.kind.id} editing=${draft.editingName} " +
                    "name='${draft.name}' cmd='${draft.single}' steps=${draft.steps.toList()} | " +
                    "命令库=${commands.value.map { it.name }}",
        )
    }

    /**
     * 从子页返回上一级 —— 把**栈顶弹掉**。上一级就是压在当前页下面的那一页（不查表）。
     *
     * ⚠️ 两处**拦住不让退**（都返回 true = 这次返回已被消费）：「自定义命令」页草稿有改动
     * （先问一句，见 [confirmLeaveCommands]）；栈里只剩首页（交给宿主关页面）。
     */
    internal fun goBack(): Boolean {
        val current = screen
        // 「打开应用」页返回：**原样保留进页前的选择** —— 挑中了写新值（那边自己 `pick`），
        // 没挑就什么都不动，蓝框本来就在选中项上。
        if (current == Screen.APP_PICKER) {
            _appPickerGesture = null
            // 「给多条命令加一步 / 换一步」那些标记也清掉 —— 不管这次是挑完了、还是没挑就退出来
            _appPickerForStep = false
            _appPickerReplaceStep = -1
        }
        // 「自定义命令」页：表单里改过还没保存 ⇒ 不退，先弹一句确认。判据用 `draft.dirty`
        // （与载入时的快照比），不是 `hasContent` —— 后者会把「点开一条看一眼、原样返回」也算成有改动。
        if (current == Screen.COMMANDS && draft.dirty) {
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
     * 进「打开应用」页时对应的**手势**（null = 没在挑应用，或者不是给手势挑的）。
     *
     *  用途：挑中应用时知道该写哪个手势。不能靠 pager 的当前页 —— 那一页不属于 pager。
     *  没挑就返回**什么都不动**（见 [goBack]），所以这个字段只服务「挑中了」那一条路。
     *
     *  从「添加命令」浮层的「选择应用」进来时这个字段是 **null**（挑中的应用是**一步**、
     * 不绑手势）—— 给多条命令加一步却没挑，本来就该什么都不发生。
     */
    internal var _appPickerGesture: Gesture? = null

    /**
     * 也加一个选择应用，同样打开这个界面」）—— 挑中的包名走 [addStep]，不写手势绑定。
     */
    internal var _appPickerForStep = false

    /**
     * 这次进「打开应用」页是**替换多条命令的第几步**（点「已添加的那一行」弹出的选择菜单
     * `-1` = 追加为一步（走 [addStep]）。与 [_appPickerForStep] 同生共死。
     */
    internal var _appPickerReplaceStep = -1

    /**
     * 退出「自定义命令」页时的「放弃未保存的命令」确认 —— 该弹没弹。
     * 触发点在 [goBack]（草稿 `draft.dirty` 时置位并**拦住不退**），窗由 `CommandScreen` 自己画。
     * 放类上而不是页面内 `remember`：置位的是 [goBack]，两处必须同一份。
     */
    internal var confirmLeaveCommands by mutableStateOf(false)

    /**
     * 「打开应用」页该**点亮哪一行**（[AppPickerScreen] 的 `selected`，同时决定进页滚到谁眼前）。
     *
     * 两个入口各有来源，读的都是**进这一页时**记下来的东西（这一页不属于 pager，不能读当前页）：
     *  · 网格入口（「按键绑定」页的「打开应用」格）→ [_appPickerGesture] 那个手势绑的
     *    `app:` 动作的包名；
     *  · 「自定义命令」页那条路 → 被**换掉**的那一步自己（[_appPickerReplaceStep]）：
     *    它本来就是 `app:<包名>` 时才算数；「加一步」没有目标 ⇒ null。
     *
     *  2026-10-13 之前第二条恒为 null，于是「换一步」进这一页时看不出当前选的是哪个应用
     *（行尾单选圈不亮、也不滚到它眼前）—— 与网格入口那套提示不是一回事。
     */
    private fun appPickerSelectedPkg(): String? {
        // 两种动作都要认：`app:`（普通打开）与 `window:`（小窗，[Action.AppWindow]）——
        // 与「按键绑定」页那一格取应用名同口径。少认一种时，开着「以小窗口打开」挑完应用回来
        // 这页一行都不点亮、也不滚过去，看起来像没选过。
        //  来源与「以小窗口打开」的重算同源（[editingAction]）—— 两处判据必须一致。
        val target = when (val action = editingAction()) {
            is Action.App -> action.target
            is Action.AppWindow -> action.target
            else -> null
        }
        return target?.substringBefore('/')
    }

    /**
     * 宿主侧的模块有没有在跑 —— 也就是**模块执行侧有没有在工作**。
     *
     *  判据与流程都在首页那个 `LaunchedEffect` 里：进页发一句「在不在」、等 `HOST_ALIVE_TIMEOUT_MS`、
     * 再看 [hostAliveToken] 有没有变（变了才有回音）。注意它**只看宿主那一侧** ——
     * 注入侧那一段是 [injectStatus]，两者再加上「设置生效」的配置侧那半才是整页总闸
     *（`ui/state/LinkCheck.kt` 的 `linkReady`）。
     *
     * 初值取 `true`：首帧还没查，先按「正常」画 —— 反过来的话每次进页面都会闪一下「未生效」的提示。
     * 首页那个 `LaunchedEffect` 组合完立刻查一次，查完再按真实结果画。
     */
    internal var hostServiceUp by mutableStateOf(true)

    /**
     * 宿主侧报活的**代次** —— 每收到一条报活就自增。
     *
     *  首页进页时记下它、发一句「在不在」，等一档再看它有没有变：变了就是模块在，没变就是没在
     *（见 [HOST_ALIVE_TIMEOUT_MS]）。用它而不是直接看 [hostServiceUp]，是因为后者初值必须是
     * `true`（否则每次进页面都会闪一下提示）。
     */
    internal var hostAliveToken by mutableIntStateOf(0)

    /**
     * 注入侧（system_server）回包的内容 —— `null` = 问过但**没回音**（注入侧没装上，
     * 或它那支 AMS 广播入口没挂上）。
     *
     *  初值就是 `null`：没问到之前一律按「未就绪」算。它、[hostServiceUp] 与配置侧那半一起构成
     * 整页总闸（见 `ui/state/LinkCheck.kt` 的 `linkReady`）—— 注入侧那半只没装时页面整体禁用，
     * 逃生口是 ⋮ 菜单里那颗「默认界面」。
     */
    internal var injectStatus by mutableStateOf<InjectStatus?>(null)

    /** 注入侧回包的**代次** —— 与 [hostAliveToken] 同一个道理：初值不能当判据，只看有没有新回音。 */
    internal var injectAliveToken by mutableIntStateOf(0)

    /** 宿主报活带回来的「它读到的主开关真值」—— `false` ⇒ 它读到的是默认值（主开关关着 / 还没解锁）。 */
    internal var hostEnabled by mutableStateOf(true)

    /** 宿主报活带回来的「最后一次执行动作」（`elapsedRealtime()`，0 = 从未）。 */
    internal var hostLastActionAt by mutableLongStateOf(0L)

    /**
     * 首页那条「模块未生效」提示的宿主状态 —— COUI 的 `SnackbarHost` 用它排队 / 显隐。
     *
     *  提示由首页（[HomeScreen]）弹，宿主却挂在这一层：`SnackbarHost` 要的是「底部对齐、
     * 不随内容滚动」的位置，而首页自己整个是滚动容器 —— 挂在页内就只能跟着内容滚了。
     */
    internal val hostServiceSnackbar = SnackbarHostState()

    /**
     * 见 `SettingsPageHooks`）—— 把页面栈清回首页。
     */
    fun onHostReentered() {
        _appPickerGesture = null
        _appPickerForStep = false
        _appPickerReplaceStep = -1
        // 栈顶没变时下面的 LaunchedEffect 不触发，浮窗要在这里主动收：
        // 首页开着 ⋮ 菜单按 Home / 熄屏再回来，菜单不能还盖在页面上。
        closeOverlays("从设置重进模块")
        if (navStack.size > 1) {
            val previous = navStack.last()
            XLog.w("从设置重进模块：页面栈清回首页（原 $previous）")
            // 一次删到底 —— 库把它当**一次多级 pop** 处理：整叠一起滑回首页，而不是
            // 「一串页面依次滑过去」。所以不再需要原来那个「跳过转场直落」的开关
            //（手写那套只能播一条跨级的长滑页，才必须直落；见 [PAGE_TRANSITION]）。
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
    private val overlayClosers = mutableListOf<() -> Boolean>()

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
    internal fun OverlayCloser(show: Boolean, onDismiss: () -> Unit) {
        val latestShow by rememberUpdatedState(show)
        val latestDismiss by rememberUpdatedState(onDismiss)
        // 「有没有浮层开着」的计数 —— 预测返回的 onBackStarted 要用它决定接不接管
        //（见 [wouldConsumeBack]）。键是 `show`：翻转时旧的 effect 收尾、新的进来，
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

    /**
     * 深浅切换有两条路，都收在 [SyncToHost] 里：① `Settings.Secure.ui_night_mode` 的
     * `ContentObserver`（手动切走这条，即时）＋ ② 每 [NIGHT_POLL_MS] 对一次账的轮询（定时切换兜底）。
     * 两条路的判据是同一个 —— [systemNight] 读到的**系统全局配置**。
     *
     * 2026-10-11 真机抓到它把宿主**过期的 night 配置**（宿主声明了
     * `configChanges=uiMode`，配置停在旧值）喂进来，亮色进页先黑一秒再被轮询纠正。
     * 那条路已删：**回调只能当信号，值一律自己重新读系统全局配置**。
     */

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
    private fun attachOne(target: View, own: FallbackOwners) {
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
    private fun ownOwners(
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
