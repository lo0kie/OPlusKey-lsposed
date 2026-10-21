package com.lookie.opluskey.ui

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.model.specIcon
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.AppPickerUiState
import com.lookie.opluskey.ui.state.BandHeight
import com.lookie.opluskey.ui.state.BranchDraftState
import com.lookie.opluskey.ui.state.CommandDraftState
import com.lookie.opluskey.ui.state.ConfigState
import com.lookie.opluskey.ui.state.HintCenter
import com.lookie.opluskey.ui.state.HostLinkState
import com.lookie.opluskey.ui.state.KeyWatchState
import io.github.suqi8.coui.kmp.nav.core.NavBackStack
import io.github.suqi8.coui.kmp.nav.core.navBackStackOf

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
     * 触觉反馈那一域（Vibrator / AudioManager 两个服务 + 「该不该振」的判据）。
     *
     *  两个服务在建页期就拿好：第一次 `performHapticFeedback` 会同步创建 `SystemVibrator`，
     *  砸在 push 动画第一帧上。口径见 [PageHaptics]。
     */
    internal val haptics = PageHaptics(context)

    /** 深浅色 / 磨砂取样那一域（建页期定一次、之后跟着系统与栈顶页走）。 */
    internal val chrome = PageChrome()

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
        // 换页就把软键盘收掉（理由见 [dismissIme]）
        dismissIme()
        val index = navStack.indexOf(target)
        if (index < 0) {
            navStack.add(target)
            return
        }
        // 一次删到底（含栈顶）—— 库把「一次组合里少 N 个 entry」当成**一次多级 pop**：整叠一起
        //  滑回目标页。与 [onHostReentered] 清栈那条同一口径。
        while (navStack.size > index + 1) navStack.removeAt(navStack.lastIndex)
    }

    /**
     * 平台返回事件喂给页面栈的入口（见 [NavBackInput]）。
     *
     *  hook 侧的 `OnBackAnimationCallback` 拿到系统手势后调它的方法；它要先把
     * `Page()` 组合期挂到 dispatcher 上才有人接（见 [Page] 里那个 `DisposableEffect`）。
     */
    internal val navBackInput = NavBackInput()

    /**
     * 浮层与「问一句」那一域（浮层计数 / 统一收起 / 三个确认开关）。
     *
     *  判据（要不要接管返回）留在这里，因为它还要看 [navStack] 与草稿是否脏 —— 那是页面
     *  自己的账，见 [wouldConsumeBack]。口径见 [PageOverlays]。
     */
    internal val overlays = PageOverlays()

    // 这一页有没有「改过还没保存」的东西 —— 退出要不要问那一句的唯一判据。
    //  两处都要看：命令页看命令草稿，分叉页看条件表草稿（它不是命令，草稿也不共用）。
    private fun hasUnsavedDraft(s: Screen): Boolean =
        (s.isCommandForm && commandDraft.draft.dirty) || (s == Screen.BRANCHES && branchDraft.draft.dirty)

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
            overlays.openCount.intValue == 0 &&
            !hasUnsavedDraft(screen)

    /** 此刻有浮窗开着吗 —— 开着时系统返回**整个吞掉**（见 `BackInterception.handleDiscreteBack`）。 */
    internal fun hasOpenOverlays(): Boolean = overlays.hasOpen()

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

    /** View 树那三样：根 View 与兜底 owner（见 [PageViewTree]）。 */
    internal val viewTree = PageViewTree()

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
     * 放在类上而不是组合里：读它的几处在**不同的树**里（网格在 `BindingScreen`、「添加命令」
     * 浮层在 `CommandScreen`），各自 `remember` 会各建一份、还可能对不上。
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

    /** 「选择界面」页那条搜索带 —— 与「选择应用」那条同一套版式，高度各量各的。 */
    internal val activitySearchBandHeight = BandHeight()

    /**
     * 「选择应用」页那一域的状态（搜索词 / 两个落盘的筛选开关 / 小窗开关 / 这趟为谁挑）。
     *
     *  收在一个对象里而不是摊在页面上：读写它的人分布在四处（顶栏那棵树、页内容那棵树、
     *  两个入口），摊开时「谁改过这一项」得把四个文件翻一遍 —— 见 [AppPickerState] 的类注释。
     *  各字段为什么提到页面对象上（而不是留在页面里 `remember`）写在各自那里。
     */
    internal val picker = AppPickerState()

    /**
     * 「选择应用」页那一趟的加载与选择状态 —— **页面级**，不随页面组合销毁。
     *
     *  为什么不能 `remember` 在页面里：进「选择界面」时栈顶换成那一页，被盖住的「选择应用」
     *  会退出组合 —— `remember` 的那一份随之销毁，退回来就是新的一份：清单重查、点过的行没了、
     *  `askedPkg` 归零（于是「此应用不支持小窗」再弹一次，用户一关就把「以小窗口打开」也关了）。
     *  提到页面级之后，只有 [AppPickerState.clearTarget] 与 `AppPickerUiState.beginVisit` 这两个
     *  **明确时机**才会清它 —— 页面之间来回不再丢。
     */
    internal val appPickerUi = AppPickerUiState(picker)

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
     * 判据就是 [editingAction]：正在编辑的那个动作本来就是小窗那一档 ⇒ 开 ——
     *  `window:<目标>` 与 `appshortcut:w:…` **两档都算**（后者是「用小窗起快捷方式」）；
     * `app:<目标>` / 不带 `w:` 的快捷方式 / 别的动作 / 新绑（`null`）⇒ 关。
     */
    internal fun resetSmallWindowForPicker() {
        val on =
            when (val action = editingAction()) {
                is Action.AppWindow -> true
                is Action.AppShortcut -> action.window
                else -> false
            }
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

    /**
     * 「条件执行」页的草稿 —— 当前这颗键这一档的条件表（[BranchDraftState]）。
     *
     *  与 [commandDraft] 分开：分叉不是命令库的一条记录，两者连草稿都不该共用。
     */
    internal val branchDraft = BranchDraftState()

    /**
     * 「按键绑定」页**当前停在哪一档手势**（0 = 单击 / 1 = 双击 / 2 = 长按）。
     *
     *  分叉页要它：从哪个手势页签进来，编的就是哪一档的条件表，顶栏小标题也写这一档
     *  （`侧键-单击`）。pager 状态在 `PageContent` 里 remember、跨页拿不到，所以在这一层记一份。
     */
    internal var branchGesture by mutableIntStateOf(0)

    // 「清空配置」确认弹窗的置位在 [overlays] 那一域（[PageOverlays.confirmReset]）。

    /** hook 侧塞进来的那条标题与六个回调（见 [PageHooks]）。 */
    internal val hooks = PageHooks()

    /**
     * 从子页返回上一级 —— 把**栈顶弹掉**。上一级就是压在当前页下面的那一页（不查表）。
     *
     * 注意：两处**拦住不让退**（都返回 true = 这次返回已被消费）：「自定义命令」页草稿有改动
     * （先问一句，见 [PageOverlays.confirmLeaveCommands]）；栈里只剩首页（交给宿主关页面）。
     */
    internal fun goBack(): Boolean {
        val current = screen
        // 「打开应用」页返回：**原样保留进页前的选择** —— 挑中了写新值（那边自己 `pick`），
        // 没挑就什么都不动，蓝框本来就在选中项上。
        if (current == Screen.APP_PICKER) {
            // 「为谁挑」整组一并清掉 —— 不管这次是挑完了、还是没挑就退出来
            picker.clearTarget()
        }
        // 注意：「选择界面」那一页的「在看哪个应用」**不在这里清** —— 这里清掉之后，那一页
        // 会在**退场动画**期间重组成「状态丢了」的分支，那一支自己还会再退一页（一次返回退两层）。
        // 它由 `ActivityPickerPage` 的 `onDispose` 负责（离开组合时清，退场动画照常画完）。
        // 「自定义命令」页：表单里改过还没保存 ⇒ 不退，先弹一句确认。判据用 `draft.dirty`
        // （与载入时的快照比），不是 `hasContent` —— 后者会把「点开一条看一眼、原样返回」也算成有改动。
        //  「条件执行」页用的是**同一份草稿**、同一套保存按钮，所以走同一条拦截。
        if (hasUnsavedDraft(current)) {
            if (!overlays.confirmLeaveCommands) {
                XLog.w("退出命令编辑页（${current.name}）：表单改过还没保存，先弹确认")
                overlays.confirmLeaveCommands = true
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
        // 退页同样收键盘（理由见 [dismissIme]）：两条动栈的路都堵上，
        // 以后谁再新开一条压 / 弹栈的路，也不必记得补这一句。
        dismissIme()
        navStack.removeAt(navStack.lastIndex)
        return true
    }

    /**
     * 切页 / 退页时把软键盘收掉。
     *
     *  宿主设置页里的搜索框（「选择应用」「控制中心磁贴」「选择界面」）一旦露出键盘，切页时
     *  转场会**带着键盘一起过去**：新页面上根本没有输入框，半块屏幕却被键盘占着，用户得先
     *  按一下返回把键盘收掉，才看得见「这次退的是键盘还是页面」。
     *
     *  只在**真的动栈**时调（[navTo] / [popBackStack]），不动栈的早退（已经在目标页）不碰 ——
     *  那种情况用户还在原地打字。收键盘不清焦点（见 [hideIme]），退回上一页时搜索框的内容与
     *  光标都还在。
     */
    private fun dismissIme() {
        activity?.let { hideIme(it) }
    }

    // 两个确认开关都在 [overlays] 那一域（[PageOverlays.confirmLeaveCommands] / [PageOverlays.pendingDraftLoad]）。

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
            overlays.pendingDraftLoad = cmd
            return
        }
        commandDraft.draft.load(cmd)
    }

    /**
     * 「打开应用」页哪一行是**当前绑定的那个**（`AppPickerScreen` 的 `selected`：行尾图标据此画，
     *  同时决定进页滚到谁眼前）。
     *
     * 两个入口各有来源，读的都是**进这一页时**记下来的东西（这一页不属于 pager，不能读当前页）：
     *  · 网格入口（「按键绑定」页的「打开应用」格）→ [AppPickerState.gesture] 那个手势绑的
     *    `app:` 动作的包名；
     *  · 「自定义命令」页那条路 → 被**换掉**的那一步自己（[AppPickerState.replaceStep]）：
     *    它本来就是 `app:<包名>` 时才算数；「加一步」没有目标 ⇒ null。
     *
     * 之前第二条恒为 null，于是「换一步」进这一页时看不出当前选的是哪个应用
     *（行尾那枚图标不亮、也不滚到它眼前）—— 与网格入口那套提示不是一回事。
     */
    internal fun appPickerSelectedPkg(): String? = appPickerSelectedTarget()?.substringBefore('/')

    /**
     * 二级页「选择界面」该点亮哪一行 —— **完整**目标（含 `/类名`），与 [appPickerSelectedPkg] 同源。
     *
     *  与它分开是因为两级要认的东西不一样：应用那一级只认到包名（一行一个包），
     *  界面那一级要认到类名 —— 否则挑完某个界面再进来，那一行永远不亮，看着像没选过。
     */
    internal fun appPickerSelectedTarget(): String? {
        // 给「条件执行」挑的那一趟**先看那一支**：条件记的是包名、动作记的是 token，两者都只认到
        //  应用（那一页不挑界面，点一行就是选中）。走 [editingAction] 认不出来 —— 那两个入口
        //  既没有手势也不在多条命令里，`editingAction` 对它们恒为 `null`。
        val forCommand = picker.branchCommandTarget != AppPickerState.NOT_FOR_BRANCH
        val branchAt = if (forCommand) picker.branchCommandTarget else picker.branchTarget
        if (branchAt != AppPickerState.NOT_FOR_BRANCH) {
            val rule = branchDraft.draft.rules.getOrNull(branchAt)
            return when {
                rule == null -> {
                    null
                }

                // 动作那一头取 token 里的目标（`app:包名` / `appshortcut:w:包名|名字` 都认）
                forCommand -> {
                    rule.action.appTarget?.let(AppTarget::pkg)
                }

                // 条件那一头直接就是包名
                else -> {
                    rule.condition?.value?.takeIf { it.isNotBlank() }
                }
            }
        }
        // 两种动作都要认：`app:`（普通打开）与 `window:`（小窗，[Action.AppWindow]）——
        // 与「按键绑定」页那一格取应用名同口径。少认一种时，开着「以小窗口打开」挑完应用回来
        // 这页一行都不点亮、也不滚过去，看起来像没选过。
        //  来源与「以小窗口打开」的重算同源（[editingAction]）—— 两处判据必须一致。
        return when (val action = editingAction()) {
            is Action.App, is Action.AppWindow -> {
                action.appTarget
            }

            // 绑的是快捷方式时给出完整写法（`包名/id|名字`）—— 二级页靠它把那一行滚到眼前；
            //  应用那一级取 `substringBefore('/')`，仍然是包名
            is Action.AppShortcut -> {
                action.plainTarget
            }

            else -> {
                null
            }
        }
    }

    /**
     * 当前绑定如果是**快捷方式**，返回它的 id；否则 `null`。
     *
     *  「选择应用」页的行尾要在「里面某个界面」与「某个快捷方式」之间分开画（`device-screen` /
     *  `table-shortcut`），而这两者在配置里都长成 `包名/xxx`（一边是类名、一边是快捷方式 id），
     *  只有这一位分得出来。
     */
    internal fun bindingShortcutId(): String? = (editingAction() as? Action.AppShortcut)?.shortcutId

    /**
     * 「链路 / 报活」那几项结论（宿主在不在、注入侧回没回包）—— 见 [HostLinkState]。
     */
    internal val link = HostLinkState

    /**
     * 页面上那两条提示条（常驻结论 + 操作回执）—— 见 [HintCenter]。
     *
     *  与 [link] 分开：提示条有生命周期（关页要收作用域），链路诊断状态没有。
     */
    internal val hints = HintCenter()

    /**
     * 从设置页重进模块时（`SettingsPageHooks`）—— 把页面栈清回首页。
     */
    fun onHostReentered() {
        // 应用名缓存在这期间可能已经过期（改名 / 卸载 / 换图标包）：重进模块时清一遍。
        clearAppLabelCache()
        picker.clearTarget()
        // 二级页那一位也要清：栈被清回首页之后，再进「选择应用」不该还记着上次在看谁
        picker.forgetActivities()
        // 「在编辑哪一颗键」同样只在这一次停留里有效：栈被清回首页 = 这一趟结束，
        //  下次进来该从侧键开始（否则用户以为在改侧键，写的却是上次那颗自定义键）。
        KeyWatchState.select(null)
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
     * 页面离开时**主动收掉所有浮窗** —— 实现与理由都在 [PageOverlays.closeAll]。
     *
     *  留这一层转发而不是让调用点直接写 `overlays.closeAll(...)`：调用点有十来处
     *  （换页 / 关页 / 露出原版界面 / 从设置重进 …），它们说的是「这一页要离开了」，
     *  不该各自知道「离开时要收浮层」这个细节。
     */
    internal fun closeOverlays(reason: String) = overlays.closeAll(reason)

    /**
     * 浮窗在组合期登记自己（见 [PageOverlays.Closer]）—— 同样只留一层转发，调用点写法不变。
     */
    @Composable
    internal fun OverlayCloser(
        show: Boolean,
        onDismiss: () -> Unit,
    ) = overlays.Closer(show, onDismiss)

    // 深浅切换有两条路，都收在 [SyncToHost] 里：(1) `Settings.Secure.ui_night_mode` 的
    // `ContentObserver`（手动切走这条，即时）＋ (2) 每 [NIGHT_POLL_MS] 对一次账的轮询（定时切换兜底）。
    // 两条路的判据是同一个 —— [systemNight] 读到的**系统全局配置**。
    //
    // 真机抓到它把宿主**过期的 night 配置**（宿主声明了
    // `configChanges=uiMode`，配置停在旧值）喂进来，亮色进页先黑一秒再被轮询纠正。
    // 那条路已删：**回调只能当信号，值一律自己重新读系统全局配置**。

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

/**
 * 取出这一趟「选择应用」的状态 —— [appPickerUi] 的唯一访问口。
 *
 *  它挂在**页面对象**上，不是 `remember` 出来的：清单、加载态、刷新指示器都要跨
 *  「进页 → 二级页 → 退回来」活着 —— 每次进页都重建一次的话，退回来会先闪一屏加载态
 *  （清单还在后台重查）。
 */
@Composable
internal fun SettingsPage.rememberAppPickerUiState(): AppPickerUiState = appPickerUi
