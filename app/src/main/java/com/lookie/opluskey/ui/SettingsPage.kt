package com.lookie.opluskey.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.model.PAGE_TRANSITION_MS
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.AppPickerUiState
import com.lookie.opluskey.ui.state.BandHeight
import com.lookie.opluskey.ui.state.BranchDraftState
import com.lookie.opluskey.ui.state.CommandDraftState
import com.lookie.opluskey.ui.state.CommandListEditState
import com.lookie.opluskey.ui.state.ConfigState
import com.lookie.opluskey.ui.state.HintCenter
import com.lookie.opluskey.ui.state.HostLinkState
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.KeysEditState
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
        //  被弹掉的每一页都要走一遍离栈收尾（命令表单页的界面状态出栈即销），但清理要等转场播完
        //  —— 见 [discardAfterExit]。
        while (navStack.size > index + 1) {
            val leaving = navStack.removeAt(navStack.lastIndex) as Screen
            discardAfterExit(leaving)
        }
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

    /**
     * 这一页「还处在编辑里」吗 —— [goBack] 与 [wouldConsumeBack] 共用这一条判据。
     *
     * ## 判据是「在编辑」，不是「改动过」
     *
     *  用户口径：**所有页面，只要有编辑就拦**。原先这里比的是 `draft.dirty`（与载入时的快照比）：
     *  「点开一条命令看一眼、原样返回」不弹窗、直接退 —— 那一次编辑会话就这么没了，
     *  用户分不清「我改的没生效」还是「我根本没改」。所以改成看**表单里有没有东西**。
     *
     * ## 两页表单都要看
     *
     *  `form` 只是「当前 pager 停在哪一页」的快捷方式；停在「命令列表」页时它指的还是上次那一页。
     *  只看它，就会漏掉另一页里正编着的那条 —— 那正是「不在 tab 下就不拦」的来源。
     */
    private fun hasUnsavedDraft(s: Screen): Boolean =
        when (s) {
            Screen.COMMANDS -> {
                commandDraft.draft.singleForm.hasContent || commandDraft.draft.serialForm.hasContent
            }

            //  场景页判**改过没**，不判「表里有没有东西」：这一页有那颗「确认」，存完就该能直接退。
            //  原先还看 `scenes.isNotEmpty()`，于是「存好了再按返回」照样弹「放弃未保存的场景」
            //  ——那张窗在说什么都没丢（真机反馈）。
            //  清空整张表也是一种改动（`dirty` 覆盖这一档），所以删光之后仍会被拦。
            Screen.BRANCHES -> {
                branchDraft.now().dirty
            }

            //  「场景动作」页改的是同一份草稿的其中一块 —— 那一退只是回上一条路，**不丢东西**
            //  （草稿是页面级的，「系统场景」页那颗「确认」还等着写它）。所以这里既不看整份草稿的
            //  `dirty`（上一页的改动会算到这一页头上、误弹），也不看表非空，**只看这一块**
            //  （判据见 [sceneBindingDirty]）。
            Screen.SCENE_BINDING -> {
                sceneBindingDirty()
            }

            else -> {
                false
            }
        }

    /**
     * 「场景动作」页**这一块**改过没 —— 返回拦截与那颗「确认」的显隐共用这一条判据。
     *
     *  比的不是整份草稿的 `dirty`：同一份草稿在「系统场景」页上可能已经改过别的块，那不算
     *  这一页的改动（拿它判会让这一页一进来就「像改过」）。判据是与进来时那份
     *  [sceneBindingBaseline] 逐块比。
     *
     *  baseline 为空（调试通道直接跳页）时退回整份 `dirty` —— 总比「永远判没改」好：
     *  那种入口下至少还拦得住真改动。
     */
    internal fun sceneBindingDirty(): Boolean =
        sceneBindingBaseline?.let { base ->
            branchDraft.now().scenes.getOrNull(sceneBindingAt) != base
        } ?: branchDraft.now().dirty

    /**
     * 系统返回要交给**页面栈**处理吗 —— **只判断，不动手**（`onBackStarted` 那一刻不能有副作用，
     * 所以不能调 [goBack] 来探）。**四种不接管**：
     *
     *   · 栈里只剩一页（首页）⇒ 那是「关掉整个设置页」，归宿主；
     *   · 浮层开着 ⇒ COUI 的浮层自己不接返回，喂进去没人消费，最后仍是页面栈弹
     *     （表现为「浮层盖着、底下页面跟着手指滑」）；
     *   · 「按键功能」页的**编辑态**（见下）；
     *   · 注意：**这一退会被 [goBack] 拦住**（「自定义命令」页草稿改过 ⇒ 先弹「放弃未保存的命令」；
     *     「选择应用」页挑中过一条还没提交 ⇒ 先弹「放弃这次选择」）：
     *     库的提交是 `onCommit = { currentOnBack.value() }`，**弹栈交给 [goBack]** —— 接管了就等于
     *     页面先滑走、栈却没弹，页面停在滑出去的位置回不来。判据与 [goBack] 那条**同一份**，一起改。
     */
    internal fun wouldConsumeBack(): Boolean {
        //  抽屉（自己接返回的浮层）开着：这一退**一定**归页面 —— 喂进 dispatcher 之后是它先接
        //  （跟手下滑、松手收起），弹栈那条轮不到它。所以草稿脏不脏、`openCount` 几都不影响这一档：
        //  「草稿脏 ⇒ 先弹问话」那条闸是给 `goBack` 的，而这一退根本走不到 `goBack`
        //  —— 条件执行页草稿一脏就走不到那一档。
        if (overlays.backCapturing.intValue > 0) return true
        //  「按键功能」页的编辑态：这一退**根本不弹栈**（[goBack] 把它收成「退出编辑态」，
        //  勾了东西时先弹一句问话），所以**不**喂进预测返回 —— 接管了就是页面跟着手指滑出去半截、
        //  提交时又被确认窗拦住，停在滑出去的位置回不来（用户口径「预测手势停在一半」）。
        //  与「草稿脏」那一档同一条账，只是它走「不给动」而不是「给动但不弹」。
        if (inKeysEdit()) return false
        //  「命令列表」的编辑态同理：那一退是「退出编辑」，人留在这页
        if (inCommandsEdit()) return false
        return navStack.size > 1 &&
            overlays.openCount.intValue == 0 &&
            !hasUnsavedDraft(screen) &&
            !hasUnconfirmedPick(screen)
    }

    /** 「按键功能」页现在是不是编辑态 —— [goBack] 与 [wouldConsumeBack] 共用这一条判据。 */
    private fun inKeysEdit(): Boolean = screen == Screen.KEYS && KeysEditState.editing

    /**
     * 「命令列表」那一页现在是不是编辑态 —— [goBack] 与 [wouldConsumeBack] 共用这一条判据。
     *
     *  与 [inKeysEdit] 同一档：那一页的三个页签共用一颗「编辑」按钮，编辑态只属于「命令列表」。
     */
    private fun inCommandsEdit(): Boolean = screen == Screen.COMMANDS && CommandListEditState.editing

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
     *  「按键功能」页在编辑态里也走上面第一条（它本来就是压上来的一页）——**注册**照旧，
     *  但**不进预测动画**：那条判据在 [wouldConsumeBack]，两件事别混。
     *
     *  注意：判据与 [wouldConsumeBack] **不是同一份**，别合并：
     *   · 「草稿脏」那一档 [wouldConsumeBack] 为 false（那时库的提交是「先弹问话、不退页」，接管了
     *     反倒会把页面滑出去——理由见它自己的注释），但**这一退仍归我们处理**（问话是 [goBack] 弹的）
     *     ⇒ 这里必须**不带**那条判定；
     *   · 「按键功能」页的编辑态同理（见 [wouldConsumeBack] 那一条）；
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
     *  「谁改过这一项」一眼可列（见 `docs/architecture/decoupling.md` 动作 5 的写入者清单）。
     */
    internal val configState = ConfigState()

    /**
     * 三条**固定带**的高度 —— 分段控件带 / 类型选择器带 / 搜索带，各一页一份。
     *
     *  量（顶栏那棵树）与读（页内容那棵树）分处两棵树，走 [BandHeight] 这条通道，
     *  它自己带量测上报与 dp 读取两端（口径见那边的类注释）。
     */
    internal val bindingBandHeight = BandHeight()

    /**
     * 「组合键」编辑页那条分段控件的高度 —— 与 [bindingBandHeight] 同一套、**不共用**：
     * 两页的带子可能同时在组合里（转场），共用一个值只会互相改对方的占位。
     */
    internal val comboBandHeight = BandHeight()
    internal val commandBandHeight = BandHeight()
    internal val appSearchBandHeight = BandHeight()

    /**
     * 「场景动作」页那条手势带 —— 同上、各页一份：它可能与「按键绑定」页的带子同时在组合里
     * （转场），共用一个值只会互相改对方的占位。
     */
    internal val sceneBandHeight = BandHeight()

    /** 「条件执行」页那条手势带 —— 同上，各页一份。 */
    internal val branchesBandHeight = BandHeight()

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
     *  · 网格入口（「按键绑定」页或「组合键」页的「应用控制」格）→ [AppPickerState.gesture]
     *    那一档绑的动作，**给组合键挑时读的是组合那张表**（[AppPickerState.comboId]）；
     *  · 「自定义命令」页那条路 → 被**换掉**的那一步（[AppPickerState.replaceStep]）。
     *
     * `null` = 新绑（没在编辑任何动作）。[appPickerSelectedTarget] 与 [resetSmallWindowForPicker]
     * 都要它，两处必须同源 —— 否则会出现「行点亮了、开关却是关的」这种自相矛盾的界面。
     */
    internal fun editingAction(): Action? {
        if (picker.forStep) {
            return picker.replaceStep
                .takeIf { it >= 0 }
                ?.let { commandDraft.draft.steps.getOrNull(it) }
                ?.let { runCatching { Action.parse(it) }.getOrNull() }
        }
        val cfg = configState.config.value
        //  「松手执行额外动作」那一趟**走的不是手势槽**：`openForRelease` 会把 `gesture` /
        //  `comboId` / `forStep` 一起清掉（那一趟写的是键上的 `longRelease`），照下面两条判都会
        //  拿到 `null` —— 于是小窗开关恒关、当前绑定不高亮，用户重挑一次就把 `window:` 静默降级成 `app:`。
        if (picker.forRelease) {
            val current =
                picker.releaseComboId
                    ?.let { id -> cfg.combos.firstOrNull { it.id == id }?.longRelease }
                    ?: cfg.key(KeyWatchState.editing(cfg)).longRelease
            return current.takeIf { it.isBound }
        }
        val g = picker.gesture
        val comboId = picker.comboId
        //  场景动作页那一趟**读草稿里那一块**（`sceneAt` 非负）—— 它既不在 `keys` 也不在
        //  `combos` 里，少一条判据就会读到「这颗键」的动作：那一格明明绑着应用，
        //  重进这一页却一行都不亮。本文件在 `ui/` 根，读 `branchDraft` 是页面级状态，不算反向依赖。
        val sceneAt = picker.sceneAt
        //  组合键那一趟**读组合那张表**：判据只有 [AppPickerState.comboId] 一处，与写回侧
        //  （`PageEntryWrite.writeToken`）同一个判据。少了这一支，从组合键页进来读到的永远是
        //  **当前那颗键**的动作 —— 那一格明明绑着应用，重进这一页却一行都不亮、确认键也不出现。
        //  不转调 `BindingTarget.Combo.actionIn`：本文件在 `ui/` 根，耦合审计不许它反向依赖
        //  `ui/screen/`；两处都只是「按 id 取那一档组合再取动作」，口径在 `ComboKey.actionOf`。
        return when {
            //  两个入口都没记下来（调试通道直接跳页时是这样）⇒ 新绑
            g == null -> {
                null
            }

            sceneAt >= 0 -> {
                branchDraft
                    .now()
                    .scenes
                    .getOrNull(sceneAt)
                    ?.actionOf(g)
            }

            comboId == null -> {
                actionOf(cfg, g)
            }

            //  组合被删了就不该退回「那颗键的动作」：那会点亮一个与本趟无关的绑定
            else -> {
                cfg.combos.firstOrNull { it.id == comboId }?.actionOf(g) ?: Action.None
            }
        }
    }

    /**
     * 进「选择应用」页时把「以小窗口打开」「使用原版动画」**重算**一遍（入口两处各调一次）。
     *
     * 判据就是 [editingAction]：正在编辑的那个动作本来就是小窗那一档 ⇒ 小窗开 ——
     *  `window:<目标>` 与 `appshortcut:w:…` **两档都算**（后者是「用小窗起快捷方式」）；
     * `app:<目标>` / 不带 `w:` 的快捷方式 / 别的动作 / 新绑（`null`）⇒ 关。
     *
     * 「使用原版动画」同理，只看 `app:a:…`（[Action.App.stockAnim]）。两者**互斥**，
     * 所以这里顺带保证算完之后最多只有一个为真 —— 新挑的时候由界面在拨动时互斥（见
     * `AppPickerHeaderMenu`），而回进来重算这一档可能遇上老配置里两个标记都写着的值。
     */
    internal fun resetSmallWindowForPicker() {
        val action = editingAction()
        val window =
            when (action) {
                is Action.AppWindow -> true
                is Action.AppShortcut -> action.window
                else -> false
            }
        val stockAnim = (action as? Action.App)?.stockAnim == true
        picker.smallWindow.value = window && !stockAnim
        picker.stockAnim.value = stockAnim && !window
        //  把这一刻记成基线：两个开关是**绑定的一部分**（拨完再挑应用才落盘），退出页面时
        //  要按「挑中过一条」同一句去问（见 `hasUnconfirmedPick`）。
        picker.markWindowTogglesBaseline()
        XLog.w("选择应用：以小窗口打开 = ${picker.smallWindow.value}／原版动画 = ${picker.stockAnim.value}（按正在编辑的动作重算）")
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
     * 「条件执行」页**现在编的是哪一档手势**（0 = 单击 / 1 = 双击 / 2 = 长按）。
     *
     *  两个写入方：进页时由「按键绑定」页的 pager 同步一份（从哪一档点进来就编哪一档），
     *  进页之后由这一页顶栏那条手势带改（切 tab 就是换一档草稿）。
     *  pager 状态在 `PageContent` 里 remember、跨页拿不到，所以在这一层记一份。
     */
    internal var branchGesture by mutableIntStateOf(0)

    /**
     * 「一档手势一屏动作」那三页各自的**滚动位置**（每个 `Screen` 一组，每档一份）。
     *
     * ## 为什么不能留在页内 `remember`
     *
     *  那三页的 `ScrollState` 原本建在 `BindingPagerScreen` 里，而**页面被另一页盖住时整棵组合
     *  会被销毁**（`NavDisplay` 只保留栈顶那一页）—— 从「按键绑定」进「选择应用」挑一条、按
     *  FAB 退回来，页内 `remember` 全没了，列表当场回到顶部（用户反馈「点 FAB 返回绑定页后
     *  滚动距离归零」）。页内 `rememberSaveable` 也救不了：它不是配置变更，不会走 save/restore。
     *
     *  所以提到页面级 —— 与 [branchGesture] 同一个理由（跨页要活下来的状态只能挂在这里）。
     *  离开绑定页时由 `discardPageStateOf` 清掉，下次进来是新的位置。
     *
     * ## 为什么按 `Screen` 分三份
     *
     *  三页的档数不同（键三档 / 组合两档 / 场景块两三档），共用一组 `ScrollState` 会互相顶掉；
     *  而 `ScrollState` 内部只有一个 `scrollableState`，两页同时在场时也会打架。
     */
    internal val bindingScrolls = mutableMapOf<Screen, List<ScrollState>>()

    /**
     * 「场景动作」页在编**第几块**场景（`-1` = 不在编任何一块）。
     *
     *  与 [ComboEditState.editingId] 同一档性质：栈里这一页只有一个位置，参数走 `Screen` 枚举
     *  带不进去，只能记在页面级。写入方只有 `openSceneBinding`（点场景块里那一行动作行），
     *  出栈时由 [discardPageStateOf] 清回 `-1`。
     *
     *  **与 [branchDraft] 配对使用**：`branchDraft` 说「在编谁的那张表」，这一位说「表里第几块」。
     *  两个都要，因为这一页是**从那张表里点进来的**，而草稿本身要跨「挑应用」往返存活。
     */
    internal var sceneBindingAt by mutableIntStateOf(-1)

    /**
     * 「场景动作」页进来时停在哪一档手势 —— 点单击那一行进来就停单击。
     *
     *  顶栏那条分段控件的分页器（`PagePagers.scenes`）在 push 那一刻按它归位
     *  （见 `PushResetsPagers`）。组合键的场景块只有短按 / 长按两档，所以这里存的是**手势本身**
     *  而不是页号 —— 页号由 `sceneGesturePage()` 按当前那张表换算。
     */
    internal var sceneBindingGesture by mutableStateOf(Gesture.SINGLE)

    /**
     * 「场景动作」页**进来时那一块长什么样** —— 这一页的「改动」与「放弃」都按它判。
     *
     * ## 为什么不能只看整份草稿的 `dirty`
     *
     *  这一页改的是**共享草稿的其中一块**（`BindingTarget.Scene`），而同一份草稿还有别的块
     *  在「系统场景」页上可能已经改过。拿 `BranchDraft.dirty` 当这一页的改动判据，会得到：
     *  在上一页改了点东西（还没确认）→ 进这一页看一眼、「什么都没动」→ 按返回却弹
     *  「放弃未保存的场景」。用户丢的不是这一页的东西。
     *
     * ## 它也是「放弃」要回滚到的值
     *
     *  这一页的「放弃」语义是「这一趟改的不要了」—— 但草稿是页面级的，直接 `popBackStack`
     *  只把人带回去，改动仍躺在草稿里，退回上一页后那颗「确认」照样能把它写下去（真机反馈：
     *  「点了放弃、命令还是生效了」）。所以放弃时必须把这一块还原成进来时那一份。
     *
     *  写入方只有 [com.lookie.opluskey.ui.openSceneBinding]（点场景块里那一行动作行），
     *  出栈时由 [discardPageStateOf] 清回 `null`。
     */
    internal var sceneBindingBaseline by mutableStateOf<SceneRule?>(null)

    // 「清空配置」确认弹窗的置位在 [overlays] 那一域（[PageOverlays.confirmReset]）。

    /** hook 侧塞进来的那条标题与六个回调（见 [PageHooks]）。 */
    internal val hooks = PageHooks()

    /**
     * 导入配置「读文件 → 校验通过 → 挑目标」里，**待挑目标**的那份内容 —— `null` = 没有待处理的导入。
     *
     *  导入现在是两段：文件在 hook 侧（`ConfigTransfer`）读进来、按本机状态合并好，先存到这里，
     *  再由「更多选项」页弹出选择器让用户挑「盖到哪一份 / 新建一份」。所以它要挂在页面级
     *  （`ConfigTransfer` 通过 [com.lookie.opluskey.ui.hook.HostSession.page] 只拿得到页面对象，
     *  拿不到「更多选项」页里 `remember` 的那份 [com.lookie.opluskey.ui.screen.about.AboutUiState]）。
     */
    internal var pendingImport by mutableStateOf<PendingImport?>(null)

    /**
     * 导入**覆盖**某一份配置之前的那一次确认 —— `null` = 没有待确认的覆盖。
     *
     *  覆盖是整份配置（连同命令库）被换掉，代价与「删除配置」同档，所以点完选择器还要再问一句。
     *  选择器关了之后这一位才置上（两层弹窗不叠着开，见 `FrostedDialog` 的 `onDismissFinished`），
     *  确认窗由「更多选项」页画。
     */
    internal var pendingImportOverwrite by mutableStateOf<ImportOverwrite?>(null)

    /**
     * 从子页返回上一级 —— 把**栈顶弹掉**。上一级就是压在当前页下面的那一页（不查表）。
     *
     * 注意：两处**拦住不让退**（都返回 true = 这次返回已被消费）：命令表单页（「自定义命令」/
     * 「条件执行」）自己那份草稿有改动（先问一句，见 [PageOverlays.confirmLeaveCommands]）；
     * 栈里只剩首页（交给宿主关页面）。
     */
    internal fun goBack(): Boolean {
        val current = screen
        //  「按键功能」页的编辑态：这一退是**退出编辑态**，人留在这页（用户口径）——
        //  编辑态只是这一页的一种用法，退它不该把人一起带走；再退一次才是离开这一页。
        //  勾了东西才问（勾选会没了）；一项都没勾时直接退，什么都不丢。
        if (inKeysEdit()) return consumeKeysEditBack()
        // 「命令列表」的编辑态：同一套账（见 [consumeCommandsEditBack]）。
        if (inCommandsEdit()) return consumeCommandsEditBack()
        // 「打开应用」页那一趟的收尾（「为谁挑」、搜索词、临时的小窗口开关）**不在这一处**：
        //  统一归 [discardPageStateOf] —— 它挂在「真的离栈」那一刻，一次返回退两层（多级 pop）
        //  与从设置重进模块清栈也走同一条，不会漏。
        // 注意：「选择界面」那一页的「在看哪个应用」**不在这里清** —— 这里清掉之后，那一页
        // 会在**退场动画**期间重组成「状态丢了」的分支，那一支自己还会再退一页（一次返回退两层）。
        // 它由 `ActivityPickerPage` 的 `onDispose` 负责（离开组合时清，退场动画照常画完），
        // 整趟结束时再由 [discardPageStateOf] 的 `APP_PICKER` 分支收一次。
        // 命令表单页（「自定义命令」/「条件执行」）：**只要还在编辑里**就不退，先问一句。
        //  判据见 [hasUnsavedDraft] —— 「在编辑」而不是「改过」（用户口径：有编辑就拦）。
        //  「选择应用」页那一趟已经挑过、还没按右下角那颗提交 ⇒ 同一套账：这一退会把草稿连同
        //  这一趟挑的整个丢掉。判据见 [hasUnconfirmedPick]（进页看一眼就回来的那一趟拦不住）。
        //  两支并成一处判：`goBack` 的 `return` 额度已经被上面那两个编辑态占去两条，再各写一支
        //  就过 detekt 的 `ReturnCount`；而这两张窗本来就只差「哪一张」。
        return when {
            hasUnsavedDraft(current) -> blockForDraftConfirm(current)
            hasUnconfirmedPick(current) -> blockForPickConfirm()
            else -> popBackStack()
        }
    }

    /**
     * 「命令表单页草稿改过」那一支：把「放弃未保存的命令」支起来并**拦住这一退**。
     *
     *  抽成一支只为让 [goBack] 里每张窗各占一行 —— 那里每多一个 `return` 就离 detekt 的
     *  `ReturnCount` 上限近一步，而两支的骨架本来就一模一样（判据不同而已）。
     */
    private fun blockForDraftConfirm(s: Screen): Boolean {
        if (!overlays.confirmLeaveCommands) {
            XLog.w("退出命令编辑页（${s.name}）：表单改过还没保存，先弹确认")
            overlays.confirmLeaveCommands = true
        }
        return true
    }

    /** [blockForDraftConfirm] 的同款，换「选择应用」页那一张（挑中过一条 / 拨过打开方式但还没提交）。 */
    private fun blockForPickConfirm(): Boolean {
        if (!overlays.confirmLeavePick) {
            XLog.w("退出选择应用页：挑中过一条或拨过打开方式，都还没提交，先弹确认")
            overlays.confirmLeavePick = true
        }
        return true
    }

    /**
     * 「选择应用」页这一趟**挑中了却没提交**吗 —— [goBack] 与 [wouldConsumeBack] 共用这一条判据。
     *
     *  草稿只由二级页点确认写（见 `PageEntry.onDraftPicked`），提交在右下角那颗 FAB 上；
     *  两者之间这一段就是「user 已经做过选择、但还没落盘」的窗口。草稿在每次进页时被
     *  `AppPickerUiState.beginVisit` 清掉，所以看见非 null 就一定属于这一趟。
     *
     *  **拨过「以小窗口打开」/「使用原版动画」也算**：那两个开关是绑定的一部分（拨完再挑应用、
     *  按确认才落盘），拨了却没挑就退出去，用户以为改过了，实际一个字节都没写。判据收在
     *  `AppPickerState.windowTogglesChanged`（与进页那一刻的基线比）。
     */
    private fun hasUnconfirmedPick(s: Screen): Boolean = s == Screen.APP_PICKER && picker.unconfirmedChanges

    /**
     * 「按键功能」页的编辑态里按返回：一项都没勾就直接退出，勾了就先把那一问支起来。
     *
     *  两条路都**返回 true**（这次返回已被消费，人留在这一页）—— 从 [goBack] 里抠出来，
     *  只为把那个函数的 return 条数留在 detekt 的上限内。
     */
    private fun consumeKeysEditBack(): Boolean {
        if (KeysEditState.count == 0) {
            XLog.w("退出按键页编辑态：没有勾选，直接退出")
            KeysEditState.clear()
            return true
        }
        if (!overlays.confirmLeaveEdit) {
            XLog.w("退出按键页编辑态：先问一句（已勾 ${KeysEditState.count} 项）")
            overlays.confirmLeaveEdit = true
        }
        return true
    }

    /**
     * 「命令列表」编辑态里按返回 —— 与 [consumeKeysEditBack] 完全同一套账：
     * 没勾就退出编辑态，勾了先问一句，两条路都消费掉这次返回。
     */
    private fun consumeCommandsEditBack(): Boolean {
        if (CommandListEditState.count == 0) {
            XLog.w("退出命令列表编辑态：没有勾选，直接退出")
            CommandListEditState.toggleEditing()
            return true
        }
        if (!CommandListEditState.confirmLeaveEdit) {
            XLog.w("退出命令列表编辑态：先问一句（已勾 ${CommandListEditState.count} 条）")
            CommandListEditState.confirmLeaveEdit = true
        }
        return true
    }

    /**
     * 一页**离开栈**时的收尾 —— 把这一页引入的**页面级状态**复位成「下次进来是新的一份」。
     *
     *  为什么要手动做：这些位挂在**页面对象**上（[commandDraft] / [branchDraft] / [picker] /
     *  [branchGesture]），提上来是因为它们要在「被上层页盖住、组合被注销」时存活（顶栏与页内容
     *  是两棵树）。代价就是：栈语义里「出栈的页整个销毁」这一条得在这里补上，否则它们会带着上一次
     *  的残留回到下一次。
     *
     *  **只处理真的离栈的页**：push 新页（如命令页压一层「选择应用」）时草稿必须原样留着，
     *  所以三处调用点（[popBackStack] / [navTo] 的批量回退 / [onHostReentered] 的清栈）
     *  都只对**被弹掉的那些**调它。
     *
     *  不在这里的两类：
     *   · **落盘的偏好**（「显示系统应用」）是**意图**不是页面状态 —— 进页读一次、
     *     跨次留存，清了等于改用户设置；
     *   · **分页器位置**（手势 tab / 命令页 tab）是 `PageContent` 里 `remember` 的、这一层拿不到 ——
     *     它们的归位由 `PushResetsPagers` 在**下一次 push** 时按「这一页该从哪一档开始」做，
     *     这里把草稿的 `kind` 清成默认，等于给了它同一个答案。
     */
    private fun discardPageStateOf(leaving: Screen) {
        when (leaving) {
            Screen.BINDING -> {
                //  「从哪一档手势进的分叉」只在这一次停留里有效：退回首页再进来，
                //  该重新按手势 tab 当前那一档算（否则顶栏小标题会写着上一次那一档）。
                branchGesture = 0
                //  滚动位置同理：离开绑定页就不再是「这一次停留」的了
                bindingScrolls.remove(Screen.BINDING)
            }

            Screen.COMMANDS -> {
                //  草稿与 tab 镜像一起归位（`kind` 的例外说在 `CommandDraftState.discard` 那边）
                commandDraft.discard()
                //  「挑完应用回来要把抽屉支起来」是这一趟的账，命令页走了就该作废
                picker.cancelResume()
                //  **列表编辑态也是这一趟的账**：勾着几条命令退出去，回来还留着旧勾选 ——
                //  下一次进编辑态再点「删除」就把上次勾的那些一起删了（跨次误删）。
                CommandListEditState.resetForLeave()
            }

            Screen.BRANCHES -> {
                // 条件执行页用另一份草稿（分叉不是命令库的一条记录），同样出栈即销
                branchDraft.discard()
                //  **不碰 [branchGesture]**：它是「按键绑定页当前停在哪一档」，不是这一页的状态。
                //  在这里清成 0 的话，退出去再进来读到的是单击 —— 而那个值的唯一写入方
                //  （`BindingPagerScreen` 里那条 `LaunchedEffect(s, page)`）**不会重跑**
                //  （tab 没变，key 没变），于是一整趟都停在错的档上。
                //  离开按键绑定页时那一条照样清（见上一个分支），那一档才有意义。
                picker.cancelResume()
            }

            Screen.SCENE_BINDING -> {
                //  「在编第几块」只在这一次停留里有效：下一次进来由点的那一行重新给。
                //  **草稿不动**（它属于「系统场景」那一页，那一页还在栈里）—— 放弃那一趟的
                //  改动由窗自己按 `sceneBindingBaseline` 还原（见 `BindingPagerScreen`）。
                sceneBindingAt = -1
                sceneBindingGesture = Gesture.SINGLE
                sceneBindingBaseline = null
                bindingScrolls.remove(Screen.SCENE_BINDING)
                picker.cancelResume()
            }

            Screen.COMBO_EDIT -> {
                //  滚动位置属于「这一次停留」：换一组组合再进来该从头看，不能接着上一组的偏移
                bindingScrolls.remove(Screen.COMBO_EDIT)
                picker.cancelResume()
            }

            Screen.APP_PICKER -> {
                //  这一趟挑应用结束（挑完了 / 没挑就退出来）：把这一页引入的界面状态整份复位。
                //  具体的字段清单归 [AppPickerState.resetSession] —— 页面骨架只报「这一页走了」，
                //  不去认识那一页有哪些字段（那一域只有一个写入口）。
                picker.resetSession()
            }

            Screen.ACTIVITY_PICKER -> {
                //  单退这一页时**不能动** `activityTarget`：挑完界面退回「选择应用」还得按它写回，
                //  而退场动画期间用户可能已经点了下一个应用（见 `AppPickerState.activityTarget`）。
                //  整趟结束的清理归上面那条 `APP_PICKER` 分支。
                //
                //  **「回来要开抽屉」这笔账在这一页退掉**（用户口径：从「应用组件」页不选直接返回，
                //  不该再弹出抽屉）：那一趟用户已经进到二级页看过、也决定不选，回去再把抽屉
                //  支起来只是打断他。没进过二级页（在选择应用页直接返回）仍按原样恢复。
                picker.cancelResume()
            }

            else -> {
                //  SHORTCUTS（栈底，永远不出栈）/ ABOUT / DONATE_LIST / GENERAL：
                //  这几页没有页面级状态，页里的东西都是 `remember`，随组合注销自然销毁。
            }
        }
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
        val leaving = navStack.removeAt(navStack.lastIndex) as Screen
        discardAfterExit(leaving)
        return true
    }

    /**
     * 退页的**状态清理**推迟到转场结束之后（[PAGE_TRANSITION_MS] + 一点余量）。
     *
     *  退场那 350ms 里这一页还在画 —— Compose 会继续组合它。当场把草稿清掉，表单会在滑出
     *  过程中**先变成空白再滑走**（命令页 / 条件页都看得到）。
     *
     *  到期时再确认一次「它没回到栈里」：这期间用户若又进了同一页，那是新的一次停留，
     *  不该把刚编的新草稿清掉。
     */
    private fun discardAfterExit(leaving: Screen) {
        Handler(Looper.getMainLooper()).postDelayed(
            { if (leaving !in navStack) discardPageStateOf(leaving) },
            PAGE_TRANSITION_MS + PAGE_STATE_DISCARD_MARGIN_MS,
        )
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
     *  注意：只在「自定义命令」页上调用：草稿脏了必然还在这一页 —— 退出时 [goBack] 已经问过，
     *  整页出栈时两页草稿也一起清了（`CommandDraftState.discard`），所以窗画在那一页就够。
     *
     *  @return `true` = 已经灌进表单；`false` = 脏着，只挂起了那张确认窗 ——
     *   **调用方要等确认之后再切页**（切页那一下现在由窗自己发起，见 `CommandDialogs`）。
     *   原先这里没有返回值、调用点又无条件切页 ⇒ 窗还摆在屏幕上，页面已经跳到表单页、
     *   而表单里还是旧内容（用户看到的是「确认框背后已经换了页」）。
     */
    internal fun loadDraft(cmd: Command): Boolean {
        //  问不问，判的是**这条命令所属那一页**有没有未保存的改动 —— 载入只会覆盖那一页
        //  （两页是两个互不相干的编辑会话）。拿当前停着的那一页判会问错人：问完「放弃未保存」
        //  却一点没丢（那是另一页的东西），反过来这边真编着的内容会无提示被覆盖。
        if (commandDraft.draft.formOf(cmd.kind).dirty) {
            XLog.w("载入命令前先问：那一页有未保存的改动（要换成「${cmd.name}」）")
            overlays.pendingDraftLoad = cmd
            return false
        }
        commandDraft.draft.load(cmd)
        return true
    }

    /**
     * 「选择应用 / 选择界面」两级页该点亮哪一行 —— **完整**目标（含 `/类名`）。
     *
     *  两级读同一份，只是各自比到不同的深度：应用那一级比到包名、界面那一级比到类名 ——
     *  否则挑完某个界面再进来，那一行永远不亮，看着像没选过。
     *
     *  **不要再从它切出一个「只要包名」的版本**：切包名一律走 [AppTarget.pkg]（先剥分身前缀
     *  `999@` 再切 `/`）。原先这里有一个 `appPickerSelectedPkg()` 写的是
     *  `substringBefore('/')`，分身应用会切出 `999@包名` —— 跟行身份对不上，那一行永远不亮。
     *  它零调用点，已删（保留一份错的实现比没有更危险）。
     *
     * ## 两个入口各有来源，读的都是**进这一页时**记下来的东西
     *
     *  这一页不属于 pager，不能读当前页：
     *  · 网格入口（「按键绑定」页的「应用控制」格）→ [AppPickerState.gesture] 那个手势绑的动作；
     *  · 「自定义命令」页那条路 → 被**换掉**的那一步自己（[AppPickerState.replaceStep]）：
     *    它本来就是 `app:<包名>` 时才算数；「加一步」没有目标 ⇒ `null`。
     *
     *  之前第二条恒为 `null`，于是「换一步」进这一页时看不出当前选的是哪个应用
     *（行尾那枚图标不亮、也不滚到它眼前）—— 与网格入口那套提示不是一回事。
     *
     *  绑的是**快捷方式**时也照给（`包名/id`）：它属于「打开应用」那一族 —— 菜单里那一行正是
     *  靠同一族判据打勾的，进这一页却不点亮那个应用，就成了「菜单说绑着、页面说没绑」。
     *  二级页（「选择界面」）同样靠这一位把当前那一条滚到眼前。
     */
    internal fun appPickerSelectedTarget(): String? {
        // 给「条件执行」挑**条件**的那一趟先看那一块：条件记的是包名。走 [editingAction] 认不出来
        //  —— 那个入口既没有手势也不在多条命令里，`editingAction` 对它恒为 `null`。
        //  （场景某一档的动作那一趟不走这里：它有手势，走 [editingAction] 那条路。）
        val branchAt = picker.branchTarget
        if (branchAt != AppPickerState.NOT_FOR_BRANCH) {
            val scene = branchDraft.now().scenes.getOrNull(branchAt) ?: return null
            // 条件那一头直接就是包名 —— 一块可能带好几条条件，要取的是**「前台应用」那一条**
            //  （只取第一条的话，手改的配置里应用条件排在第二位时这一页一行都不亮）。
            //  槽位口径只有一处（`SceneRule.appCondition`）。
            // 条件可以**多选**（`包1||包2`，任一在前台即通过）：这一位只回答
            //  「滚到哪一行、行尾亮哪个」，所以取第一个；完整集合在 `picker.pickedApps`。
            return scene.appCondition?.appPackages?.firstOrNull()
        }
        // 两种动作都要认：`app:`（普通打开）与 `window:`（小窗，[Action.AppWindow]）——
        // 与「按键绑定」页那一格取应用名同口径。少认一种时，开着「以小窗口打开」挑完应用回来
        // 这页一行都不点亮、也不滚过去，看起来像没选过。
        //  来源与「以小窗口打开」的重算同源（[editingAction]）—— 两处判据必须一致。
        //
        //  **两档只认自己那一族**（[AppPickerState.stopApp]）：这一页两个状态共用，
        //  点「结束应用」进来时那颗键绑的若是 `proc:stop:`，就**不该**在「打开应用」那一趟里
        //  被点亮、更不该把列表滚过去 —— 那会让人以为这一趟要绑的是同一个动作。
        val stopMode = picker.stopApp
        return when (val action = editingAction()) {
            is Action.App, is Action.AppWindow -> {
                action.appTarget.takeUnless { stopMode }
            }

            // 绑的是快捷方式时给出完整写法（`包名/id|名字`）：一级页据此点亮那个应用、
            //  二级页据此把那一行滚到眼前。快捷方式是「打开应用」那一族的一条来源，
            //  不是另一个动作 —— 两边判据必须一致（见函数头）。
            is Action.AppShortcut -> {
                action.plainTarget.takeUnless { stopMode }
            }

            // 结束应用（`proc:stop:包名`）只存包名：这一趟进来时把那一行点亮、滚到眼前，
            //  用户才看得出这颗键现在管着谁（打开那一趟同理不认它）。
            is Action.Process -> {
                action.pkg.takeIf { stopMode }
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
            // 这一趟停留到此结束：每一页都走一遍离栈收尾（命令表单页的草稿出栈即销）。
            //  **与另两条离栈路径（popBackStack / navTo 的批量回退）同一时机**：都交给
            //  `discardAfterExit` 推迟到转场结束 —— 转场那 350ms 里这几页还在画，
            //  就地清掉的话表单会先变成空白再滑走（真机上看得见）。
            while (navStack.size > 1) {
                val leaving = navStack.removeAt(navStack.lastIndex) as Screen
                discardAfterExit(leaving)
            }
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
     * **系统返回**收一层浮窗（只收最上面那层）—— 实现与理由都在 [PageOverlays.closeTop]。
     *
     *  与 [closeOverlays] 分开是有意的：那个是「页面要走了，全部收干净」，这个是「退一步」，
     *  用户按一次返回不该把两层一起关掉。
     */
    internal fun closeTopOverlay(reason: String): Boolean = overlays.closeTop(reason)

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

/**
 * 退页状态清理比转场多等的余量。
 *
 *  转场本身是 [PAGE_TRANSITION_MS]；多留 60ms 是为了让「最后那一帧」也画完 —— 清理本身不花时间，
 *  但 handler 的回调可能挤在转场最后一帧之前执行。
 */
private const val PAGE_STATE_DISCARD_MARGIN_MS = 60L

/**
 * 导入配置「待挑目标」的那份内容 —— 见 [SettingsPage.pendingImport]。
 *
 *  放在 `ui/` 这一层（而不是 `core/` 或屏里）：它是「导入这条链路」的中间态，hook 侧
 *  （`ConfigTransfer`）写它、「更多选项」页读它 —— 两边都在界面层，不需要下沉到共用层。
 *
 * @param config 已经按本机状态合并过的配置（总闸 / 逐键开关 / 免责协议都换回本机值），写盘前不再改
 * @param commands 去重后的命令库
 * @param name 那份配置的名字（「新建一份」时用；覆盖已有配置时保留目标自己的名字，这一位用不到）
 */
internal data class PendingImport(
    val config: Config,
    val commands: List<Command>,
    val name: String,
)

/**
 * 导入**覆盖**某一份配置之前的确认 —— 见 [SettingsPage.pendingImportOverwrite]。
 *
 * @param content 待写入的那份内容（选择器关掉之后仍要留着，等用户在这张窗里点头）
 * @param slot 要覆盖的目标槽
 * @param displayName 目标那份的显示名（窗里点名用，免得用户点错）
 */
internal data class ImportOverwrite(
    val content: PendingImport,
    val slot: String,
    val displayName: String,
)
