package com.lookie.opluskey.ui.state.command

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.bridge.host.CommandPlatformImpl
import com.lookie.opluskey.command.ActionRunner
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.state.page.CommandDraft
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 步骤列表**展示层**的一行 —— `draft.steps` 里某个 token 的镜像，带一个稳定 id。
 *
 *  动画只活在展示层：数据（`draft.steps`）是立即改的，行是「播完退场再摘」。
 */
internal class StepRowItem(
    val id: Int,
    token: String,
) {
    /**
     * 这一步的动作 token。
     *
     *  **可写**：换一步（同长度、只有一个位置对不上）时那一行**就地换字**，不播退场 / 入场
     *  （见 [StepRowDisplay.sync]）。是 state 因为组合期要读它取标题与图标。
     */
    var token by mutableStateOf(token)

    val state = MutableTransitionState(false).apply { targetState = true }

    /**
     * 退场中：target 已切 false，播完 shrink+fade 就从展示层摘除。
     *
     *  组合期会读它（`count { !it.exiting }`、几处 `enabled = !item.exiting`），所以必须是 state：
     *  普通 `var` 只在「每个写点都恰好与 `state.targetState` 同帧写」时看着能工作（靠那一次重组
     *  顺带把读点重读一遍），那是巧合、不是保证。
     */
    var exiting by mutableStateOf(false)

    /**
     * 退场行的插回锚。**只在 [StepRowDisplay.sync] 里读写**，
     * 组合期不读 ⇒ 普通 `var` 就够，不必是 state。
     */
    var anchorId = -1
}

/**
 * 步骤行的展示层镜像 —— 增 / 删 / 换 / 移都在这里做**差分**。
 *
 * ## 为什么住在页面级状态，而不是 `SerialSteps()` 里的 `remember`
 *
 *  「自定义命令」页是两个 tab 共用一个分页器，切 tab 时**那一页的组合会被注销**：
 *  展示层跟着没了，切回来重新建一遍 ⇒ 整张表重播一次进场动画（真机反馈
 *  「切换 tab，步骤的列表动画又跑一遍」就是这个）。放到页面级之后，切回来时
 *  行还是原来那些、`AnimatedVisibility` 的 currentState 仍是 true，不会再播。
 *
 * ## 差分怎么算
 *
 *  未退场的展示行按展示顺序与 `steps` 做**多重集匹配**（同 token 按先后配对）：
 *  配上的保留、配不上的播退场（记下前面最近的保留行当锚，收缩期间插回它下面）、
 *  缺的建新条目播入场。
 */
internal class StepRowDisplay {
    val rows = mutableStateListOf<StepRowItem>()

    /**
     * 「换一步」在下标几 —— 判据是**同长度、恰好一个位置对不上、其余按 token 完全对上**。
     *
     *  只认这一个形态：多出 / 少掉一行、或顺序变了（上移下移）都仍然走差分那条路，
     *  那些要的是进 / 出场与位移动画，不是就地换字。
     *
     *  @return 那个下标；不是「换一步」时 `null`
     */
    private fun replaceIndex(
        kept: List<StepRowItem>,
        steps: List<String>,
    ): Int? {
        if (kept.size != steps.size) return null
        val diff = kept.indices.filter { kept[it].token != steps[it] }
        if (diff.size != 1) return null
        val at = diff[0]
        val rest = kept.indices.filter { it != at }
        return if (rest.all { kept[it].token == steps[it] }) at else null
    }

    /** 下一个新行的 id。只在 [sync] 里读写（组合期不读）⇒ 普通 `var` 就够。 */
    private var nextId = 0

    /** 与 [steps] 对齐（见类注释里那段差分规则）。 */
    fun sync(steps: List<String>) {
        //  退场动画播完的行**没有第二个摘除点**：下面只把「正在退场」的行按锚插回去，
        //  `clear()` 又只在出栈时调 —— 于是反复删 / 加步骤时 `rows` 只增不减，每次重组
        //  都要为这些看不见的行再算一遍 `indexOf` 与整张 `CardList`。这里先摘掉**上一批**里
        //  已经收干净的那些（`isIdle && !targetState` = 退场动画结束）。
        rows.removeAll { it.exiting && it.state.isIdle && !it.state.targetState }
        val kept = rows.filter { !it.exiting }
        //  **换一步**：同长度、只有一个位置对不上 ⇒ 那一行就地换字。
        //  走差分的话旧行收缩、新行展开，用户看到的是「这一行先变矮又长回来」。
        replaceIndex(kept, steps)?.let { at ->
            kept[at].token = steps[at]
            return
        }
        val matched = arrayOfNulls<StepRowItem>(steps.size)
        val pool = kept.toMutableList()
        steps.forEachIndexed { si, token ->
            val i = pool.indexOfFirst { it.token == token }
            if (i >= 0) matched[si] = pool.removeAt(i)
        }
        // 没匹配上的 → 退场；锚 = 前面最近的保留行（收缩期间插回它下面）
        var prevId = -1
        //  `kept` 就是 `rows.filter { !it.exiting }`，所以这里不必再判一次 `!row.exiting`
        //  （恒真）—— 写出来只会让人以为「已退场的行也可能在这一支里」。
        kept.forEach { row ->
            if (matched.contains(row)) {
                prevId = row.id
            } else {
                row.exiting = true
                row.anchorId = prevId
                row.state.targetState = false
            }
        }
        // 重建展示顺序：steps 顺序放匹配行（缺的建新行），其间插入挂锚的退场行
        val exitingByAnchor = rows.filter { it.exiting }.groupBy { it.anchorId }
        val newOrder = mutableListOf<StepRowItem>()
        newOrder += exitingByAnchor[-1].orEmpty().sortedBy { it.id }
        steps.forEachIndexed { si, token ->
            val m = matched[si]
            if (m == null) {
                newOrder += StepRowItem(nextId++, token).also(rows::add)
            } else {
                newOrder += m
            }
            newOrder += exitingByAnchor[m?.id].orEmpty().sortedBy { it.id }
        }
        //  次序没变就**一点不动这张表**：`clear()` + `addAll()` 会让组合层看到一次「空表」——
        //  那一帧里所有 `key(item.id)` 的组都会被注销，下一帧再重建时 `AnimatedVisibility`
        //  是一份新组合，整张表于是重新展开一遍（切 tab 回来会跑这一趟 `sync`，
        //  用户的「每次都重新展开了」就是这么来的）。同样的次序、同样的对象 ⇒ 不写就不脏。
        val sameOrder = rows.size == newOrder.size && rows.indices.all { rows[it] === newOrder[it] }
        if (!sameOrder) {
            rows.clear()
            rows.addAll(newOrder)
        }
    }

    /** 页面出栈：清空 —— 下一趟进来从干净的一份开始（id 也从头数）。 */
    fun clear() {
        rows.clear()
        nextId = 0
    }
}

/**
 * 命令库落盘的结果 —— 「落盘了、但广播没发出去」必须与「压根没落盘」**分开**。
 *
 *  前者数据已经是对的（该跟着挪的绑定照样得挪、草稿该清还是清），只是注入侧不知道；
 *  后者什么都没变，调用方必须收手。原来只用一个 `Boolean` 表达「有没有落盘」，
 *  而 `publishConfig` 的结果被直接丢掉：命令库改完没广播出去时界面一声不响。
 */
internal enum class CommandsSave {
    /** 落盘与广播都成了。 */
    Ok,

    /** 落盘成了、广播没发出去（注入侧仍是旧配置；提示已由 [saveCommands] 给过）。 */
    BroadcastFailed,

    /** 没落盘（只读态 / 写盘失败）—— 调用方必须按「什么都没变」处理。 */
    Failed,
}

/**
 * 「自定义命令」页上那几个**一次只会开一个**的浮层与弹窗 —— 谁开着、为谁开着。
 *
 *  收在一个对象里而不是摊成四个 `remember { mutableStateOf(...) }`：它们的读写点分布在
 *  两个文件、四处组合里（页内的命令卡长按 / 顶栏那一侧没有 / 五个确认窗），摊开时
 *  「这个位是谁置的、谁清的」得来回翻；收起来之后一眼能列全。
 *
 *  这些位**不落盘**、也不跨进页存活：`CommandUiState` 跟着这一页的组合走，
 *  出页即丢（与 [CommandDraftState] 同一条纪律 —— 但草稿是用户内容，这里是界面的瞬时态）。
 */
internal class CommandUiState {
    /**
     * 首次进这一页的风险提示还在等确认（见 `CommandScreen` 里那段 `LaunchedEffect`）。
     *
     *  这一页能让用户写任意 shell 命令，而我们**不做任何检验或保护**。不确认就退出这一页，
     *  确认了才把那一位记进配置。`configReadOnly` 时不弹 —— 那时写盘会被拒，确认了也记不下。
     */
    var disclaimerPending by mutableStateOf(false)

    /**
     * 哪一张命令卡的菜单开着、**锚在哪**（`null` = 没开着）。
     *
     *  名字是主键 —— 命令名在库里唯一，用它认身份比用位次稳（删一条之后位次会整体前移）。
     *  锚点跟着**从哪儿打开**走：长按走卡片自己那份（弹在手指那一点），右上角那颗「更多」走它自己那份
     *  （弹在那颗字上）。合成一个对象，于是「开着谁、从哪儿弹」只有一处真相。
     */
    var menu by mutableStateOf<TileMenu?>(null)

    /** 待确认的删除：非空 = 弹着确认窗（真删在确认之后）。 */
    var deleting by mutableStateOf<Command?>(null)

    /**
     * 命令预览窗口：非空 = 开着（看的是这一条）。
     *
     *  与菜单**互斥**：开预览之前先把菜单收掉（见 `CommandTile`）。
     */
    var detail by mutableStateOf<Command?>(null)

    /**
     * 待确认的「填前缀」：非空 = 弹着确认窗。
     *
     *  点「支持的命令格式」那一行是把前缀**整段替换**进输入框（不是插入），输入框里已经有内容时
     *  那是不可逆的，先问一句。
     */
    var pendingFormatPrefix by mutableStateOf<String?>(null)

    /**
     * 「这条 shell 命令保存了也跑不了」那一问还等着回答（见 `CommandFloatingButton` 与
     * `ShellSaveConfirmDialog`）。
     *
     *  与 [disclaimerPending] 不是一回事：那是「进这一页先认一次风险」，一次性的门；这是
     *  「这一条命令**此刻的样子**让它能存下去、但按下去不会有任何效果」—— 每条命令各问各的
     *  （见 `shellSaveNeedsConfirm`），换一条命令该重新问。
     */
    var confirmShellSave by mutableStateOf(false)

    /**
     * 把输入框**滚回视野**的请求 —— 点一下「支持的命令格式」那一行就 +1。
     *
     *  用**自增的计数**而不是一个布尔位：连着点两行时布尔会停在同一个值上（true → true），
     *  第二次就不再是「变化」，`LaunchedEffect` 不会重跑。计数每次都不同，天然可重复触发；
     *  初值 0 是「还没点过」，接收方据此跳过首次组合。
     *
     *  **只管滚，不管焦点**（用户口径：点命令格式不再聚焦）。那一栏在页面下方、输入框在上方，
     *  点完不滚回去等于「填了个看不见」；但焦点仍在键盘该在的地方 —— 点格式是「挑一个前缀」，
     *  不是「要在这儿打字」，抢焦点会把键盘顶起来盖住那张格式卡本身。
     *  触发点够不着本页的滚动状态，所以还得由输入框那一侧替它滚。
     */
    var singleScrollRequest by mutableStateOf(0)
}

/**
 * 一张命令卡的菜单：[name] 是哪一条命令，[anchor] 是**这一下**按在哪 —— 浮层菜单的落点。
 *
 *  两处都能开它（卡片长按 / 右上角那颗「更多」），各自手里只有自己那份触点账本，
 *  所以由开的那一处把锚点一起递上来，菜单层不再猜。
 */
internal class TileMenu(
    val name: String,
    val anchor: PreciseClickState,
)

/**
 * 「自定义命令」页那一域的状态 —— 草稿本体 + 「重置」那套行级退场的三个标记。
 *
 * 这一份收在页面对象上（不是页面里 `remember`）：草稿要**跨重组**活着 —— 从别的页面回来时
 * 不该被重新组合清掉；每次进这一页由「按键绑定」那张列表的跳转按钮显式 reset。
 * 三个标记跟着草稿一同生共死，所以同属这一域。
 */
internal class CommandDraftState {
    /** 正在编辑的那条命令的草稿。 */
    val draft = CommandDraft()

    /**
     * 「步骤排序」编辑态 —— 由「命令列表」卡右下角那颗 FAB 开关。
     *
     *  开着的这段时间里：步骤行行尾换成上移 / 下移 / 删除三颗行内按钮，行的轻点不再换命令，
     *  页面其余控件一律禁用、底部工具条滑出隐藏。它是**整页**的态（表单与列表都要读），
     *  所以住在这一域、而不是某个 composable 的 `remember` 里。
     *
     *  换 tab 时由 `CommandTabSync` 摘掉：另一页没有步骤可排，带着它过去只会让那一页莫名其妙地全灰。
     */
    var stepEditing by mutableStateOf(false)

    /**
     * 步骤行的**展示层**（动画用）—— 住在这一域，不住 `SerialSteps()` 里。
     *
     *  切 tab 时那一页的组合会被注销，`remember` 的展示层跟着没了，切回来整张表重播进场动画。
     *  放到页面级之后，切回来时行还是原来那些，`AnimatedVisibility` 的 currentState 仍是 true，
     *  不会再播一次（见 [StepRowDisplay] 的类注释）。
     */
    val stepRows = StepRowDisplay()

    /**
     * 下一次进「自定义命令」页要停在**命令列表**那一格 —— 一次性意图，读到就清掉。
     *
     *  手势页那条「已保存的命令」标题点下去是「去管命令库」，落点就该是列表那页；
     *  而进页的分页定位在 `PageContent.PushResetsPagers` 里、按草稿类型算，那一层看不到
     *  「用户想从哪进来的」—— 于是由发起方在这儿留一句，那边读一次就清。
     *
     *  消费方一定跑：它挂在 push 那一刻（`LaunchedEffect(navStack.size)`），与这次导航同一拍。
     */
    var openListPage by mutableStateOf(false)

    /** 读一次并清掉 [openListPage]。 */
    fun consumeOpenListPage(): Boolean {
        val want = openListPage
        openListPage = false
        return want
    }

    /**
     * 页面**离开栈**：草稿与 tab 镜像一起归位。
     *
     *  与 [CommandDraft.reset] 的差别有两处：这里**两页都清**（页面没了，两份表单都是它留下的），
     *  而且连 `kind` 也清回单条。平时 `kind` 由分页器单向同步（见那边的注释），但页面出栈之后
     *  没人再替它归位，而下次进页时 `PushResetsPagers` 要按它定位 tab —— 不清就会停在上次那一档。
     */
    fun discard() {
        draft.resetAll()
        draft.kind = CommandKind.SINGLE
        stepEditing = false
        stepRows.clear()
        openListPage = false
    }
}

/**
 * 「测试命令」按钮用的执行器（本进程只建一份）。
 *
 *  ## 为什么要缓存
 *
 *  执行器内部持进程级的东西：一个 Handler、几个系统服务的引用，以及 **手电筒那条
 *  `CameraManager.TorchCallback` 注册**（`FlashlightController` 在第一次 toggle 时注册，
 *  进程里不会自己摘）。每点一次命令就新建一个执行器，就是每次多注册一份回调。
 *
 *  ## 为什么要有 [release]
 *
 *  上面这些在**宿主主进程**里是「进程活多久就活多久」的，那里本来就不需要释放。
 *  但这一份活在 `:ui`（设置页进程）里，执行器拿着的是宿主的 Application Context ——
 *  页面关掉之后它仍然钉着那个 Context 与那份回调。开着「手电筒」命令试几次、
 *  再进出设置页几轮，就是几份不会再被用到的注册挂在系统里。
 *
 *  所以页面关掉时（`SettingsPageHooks.clearPageRefs`）显式扔掉这一份：
 *  下次进页第一次点「测试」再新建，代价只是重新注册一次回调。
 *
 *  ## 为什么不用 Application Context 也能接受
 *
 *  [acquire] 只接 `Application.Context`：执行器会把传进来的 Context 一直拿着，
 *  传 Activity Context 就等于永久钉住一个已销毁的 Activity 及其整棵 View 树 ——
 *  那是**无界**泄漏，比上面那点注册严重得多。
 */
internal object TestRunnerCache {
    @Volatile
    private var cached: ActionRunner? = null

    /** 取本进程那一份执行器（没有就建）。 */
    fun acquire(context: Context): ActionRunner {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: ActionRunner(
                context.applicationContext,
                Handler(Looper.getMainLooper()),
                //  设置页这一份「试一下」也走同一条命令能力层：端口实现与宿主主进程那一份同一处
                CommandPlatformImpl { context.applicationContext },
            ).also { cached = it }
        }
    }

    /** 扔掉本进程那一份（页面关掉时调）。下一次 [acquire] 会新建。 */
    fun release() {
        synchronized(this) {
            //  置 null 只是「不再复用」，执行器还钉着那份 torch 回调 —— 要真的摘掉，
            //  否则每轮进出设置页都在系统里留一份（见类注释那段）。
            cached?.releaseTorch()
            if (cached != null) XLog.w("关页：释放测试命令执行器（torch 回调一并注销，下次进页重建）")
            cached = null
        }
    }
}

/**
 * 「命令列表」那一页的**编辑态** —— 顶栏那颗「编辑 / 完成」、卡片上的多选框、右下角那颗「删除」
 * 共用这一份。
 *
 *  与 [KeysEditState] 同一套做法：**界面状态，不落盘**；选中集合存的是**命令名**（它是命令库的主键），
 *  不存对象 —— 每写一次盘命令库都会换一份新对象，存下来的那份会过期。
 *
 *  为什么放在页面级（`object`）而不是 `remember` 在页面里：那颗「编辑 / 完成」长在**顶栏**那棵树
 *  （`PageTopBar` 的 `PageActions`），与列表分处两个组合；只有页面级这一份能把两边接起来。
 *  返回拦截（`SettingsPage.goBack`）读的也是它。
 */
internal object CommandListEditState {
    /** 现在是不是编辑态。 */
    var editing by mutableStateOf(false)

    /** 勾中的命令名（只有编辑态下才有意义；退出即清空）。 */
    var picked by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 两个确认窗的开关 —— 放在这里而不是 `CommandUiState`：**返回拦截**（`SettingsPage.goBack`）
     * 长在页面那一层，够不着页内 `remember` 的那个对象，只有页面级这份能接起来。
     */
    var confirmDeletePicked by mutableStateOf(false)
    var confirmLeaveEdit by mutableStateOf(false)

    /** 一共勾了几条 —— 那颗「删除」与确认窗都只看它。 */
    val count: Int get() = picked.size

    /** 点一张卡：加进选中 / 从选中里去掉。 */
    fun toggle(name: String) {
        picked = if (name in picked) picked - name else picked + name
    }

    /**
     * 顶栏那颗「全选」：把清单里能勾的**一次勾满**，已经勾满时再按一下**收回**。
     *
     *  集合由调用方现算：清单随时在变（加一条 / 删一条 / 改名），「全选」的含义也跟着变。
     */
    fun toggleAll(names: Set<String>) {
        picked = if (names.isNotEmpty() && picked == names) emptySet() else names
    }

    /** 顶栏那颗按钮：编辑 ⇄ 完成。退出编辑时选中一起清掉。 */
    fun toggleEditing() {
        editing = !editing
        if (!editing) picked = emptySet()
    }

    /** 只清选中，不动编辑态（落盘之后退出编辑那一趟用）。 */
    fun clearPicked() {
        picked = emptySet()
    }

    /**
     * 离页时整份归位（`SettingsPage.discardPageStateOf(COMMANDS)` 调）。
     *
     *  与 [toggleEditing] 分开：那个是用户点顶栏按钮的语义（编辑 ⇄ 完成），这个是「这一页走了」——
     *  编辑态、勾选、两个确认窗的开关一起收。留着它们的后果是**跨次误删**：上次勾着三条退出，
     *  下次进来点「删除」会把那三条一起删掉，而用户早忘了。
     */
    fun resetForLeave() {
        editing = false
        picked = emptySet()
        confirmDeletePicked = false
        confirmLeaveEdit = false
    }
}
