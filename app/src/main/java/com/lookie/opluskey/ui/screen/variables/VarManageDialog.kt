package com.lookie.opluskey.ui.screen.variables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.config.VariableStore
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.VarOp
import com.lookie.opluskey.core.model.VarType
import com.lookie.opluskey.core.model.Variable
import com.lookie.opluskey.core.model.VariableRefs
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.NameDialog
import com.lookie.opluskey.ui.PickerActionDialog
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SinglePickerDialog
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.writeToken
import com.lookie.opluskey.ui.state.VarListSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 变量只有**一个页面**：「自定义变量」列表页。两个入口都进它：
//   1) 首页「其他」卡那一行 —— 这一趟是**管数据**（改值 / 删除）；
//   2) 命令里引用（绑定网格那一格、命令步骤抽屉、条件分支动作）—— 这一趟是**挑一步**，
//      挑中的操作要写回 [`picker`] 记的那一格 / 那一步 / 那一条规则。
//
//  两趟都是**点列表里的一颗 → 弹这张居中窗**，操作都在窗里，不再在列表下面另起一块。
//  差别只有两处：第一行叫「编辑」还是「设为…」，以及最后一行有没有「删除」。
//
//  这一页的「点一行 → 弹窗」由 `VariableScreen` 接，窗本身在这里。

/**
 * 「自定义变量」列表点一颗变量弹的窗：标题是变量名，正文是一列**对这颗变量能做的事**。
 *
 *  行是选择器那一套（通栏纯文字、行间细线）：这一层问的不是「要不要」，而是「做哪一件」。
 *  点一行就算做完：
 *   · 「编辑 / 设为…」→ 关掉本窗、弹值输入窗；
 *   · 「删除」（只有管数据那一趟有）→ 关掉本窗、弹确认窗；
 *   · 取反 / 加一 / 减一 / 恢复初始值 → 管数据那一趟当场落盘，挑一步那一趟落成一步。
 *
 *  ## 为什么换窗要等退场播完
 *
 *  两层弹窗是两个独立窗口，平台在窗口销毁时会把键盘一起带走 —— 叠着开（或退场途中就开下一层）
 *  的表现是「输入框刚聚焦、键盘闪一下又没了」。所以下一层的开关写在 `onDismissFinished` 里，
 *  与「收集按键 → 添加按键」那一对同一个做法。
 */
@Composable
internal fun SettingsPage.VarManageDialog() {
    val scope = rememberCoroutineScope()
    //  关窗之后接着弹哪一层（带着那时要操作的那一颗 —— 面板淡出时标题与行还得留着）
    var next by remember { mutableStateOf<VarNext?>(null) }
    var valueFor by remember { mutableStateOf<Variable?>(null) }
    var deleteFor by remember { mutableStateOf<Variable?>(null) }
    //  这一颗被多少处引用着（删除前反查，见 `refsOf`）—— 有引用就不让删
    var deleteRefs by remember { mutableIntStateOf(0) }
    LaunchedEffect(deleteFor) {
        deleteRefs = deleteFor?.let { v -> withContext(Dispatchers.IO) { refsOf(v.name) } } ?: 0
    }
    val step = VarManageState.mode == VarManageState.Mode.Step

    //  取反 / 加一 / 减一 / 恢复初始值：本窗收起，然后按这一趟的用途落下去
    val land: (Action.VarSet) -> Unit =
        if (step) {
            { set -> commitStep(set) }
        } else {
            { set -> applyNow(scope, set) }
        }

    VarManageBody(
        step = step,
        onValue = { v -> next = VarNext(VarNextStep.Value, v) },
        onLand = land,
        //  删除只在管数据那一趟出现
        onDelete = { next = VarNext(VarNextStep.Delete, it) },
        onClosed = {
            next?.let { layer ->
                when (layer.step) {
                    VarNextStep.Value -> valueFor = layer.variable
                    VarNextStep.Delete -> deleteFor = layer.variable
                }
            }
            next = null
        },
    )

    VarValueLayer(editing = valueFor, step = step, onDismiss = { valueFor = null }, onLand = land)

    VarDeleteDialog(
        deleting = deleteFor,
        refs = deleteRefs,
        onDismiss = { deleteFor = null },
        onConfirm = {
            deleteFor = null
            deleteVar(scope, it)
        },
    )
}

/**
 * 改值那一层 —— 「编辑 / 设为…」之后的下一步。
 *
 *  布尔**不给输入框**：它只有两个取值，手打 `true` / `false` 只会打错，摆成 True / False 两行让人挑；
 *  其余两档才是输入。两支都常挂着、只翻 `show` —— 按 `show` 拆支的话收起时是硬切，没有退场那一段。
 *
 * @param step 挑一步那一趟：标题写「设为…」，落盘交给写回；否则写「编辑」，当场改值
 */
@Composable
private fun SettingsPage.VarValueLayer(
    editing: Variable?,
    step: Boolean,
    onDismiss: () -> Unit,
    onLand: (Action.VarSet) -> Unit,
) {
    val title = if (step) I18n.get(Strings.VAR_OP_SET, "设为…") else I18n.get(Strings.COMMON_EDIT, "编辑")
    val boolean = editing?.type == VarType.BOOLEAN
    SinglePickerDialog(
        show = boolean,
        title = title,
        labels =
            listOf(
                I18n.get(Strings.VAR_VALUE_TRUE, "True"),
                I18n.get(Strings.VAR_VALUE_FALSE, "False"),
            ),
        //  打勾的是**当前值**：一打开就知道现在是什么，不用去记得
        selectedIndex =
            when {
                editing == null -> -1
                editing.value == VarType.TEXT_TRUE -> 0
                else -> 1
            },
        onDismiss = onDismiss,
        onPick = { index ->
            editing?.let { variable ->
                onLand(
                    Action.VarSet(
                        variable.name,
                        VarOp.SET,
                        if (index == 0) VarType.TEXT_TRUE else VarType.TEXT_FALSE,
                    ),
                )
            }
        },
    )
    if (!boolean) {
        NameDialog(
            show = editing != null,
            title = title,
            label = I18n.get(Strings.VAR_FIELD_VALUE, "值"),
            icon = editing?.let { typeIcon(it.type) } ?: PageIcons.VarInitial,
            initial = editing?.value.orEmpty(),
            onDismiss = onDismiss,
            onConfirm = { raw ->
                editing?.let { variable -> onLand(Action.VarSet(variable.name, VarOp.SET, raw)) }
            },
        )
    }
}

/**
 * 窗本体：标题 + 一列动作。开关与出口由 [VarManageState] 说，这里只画。
 *
 *  **收起之后 `target` 不清**：面板还在淡出，标题与那几行得留着 —— 清了就是「先塌成空壳再淡出」。
 */
@Composable
private fun SettingsPage.VarManageBody(
    step: Boolean,
    onValue: (Variable) -> Unit,
    onLand: (Action.VarSet) -> Unit,
    onDelete: (Variable) -> Unit,
    onClosed: () -> Unit,
) {
    val target = VarManageState.target
    val rows = target?.let { varRows(it, step, onValue, onLand, onDelete) }.orEmpty()
    PickerActionDialog(
        show = VarManageState.visible,
        title = target?.name.orEmpty(),
        labels = rows.map { it.label },
        onDismiss = { VarManageState.close() },
        onDismissFinished = onClosed,
        onPick = { index -> rows.getOrNull(index)?.let { it.run() } },
    )
}

/**
 * 删除那一颗的二次确认 —— 破坏性动作一律过一道确认窗。
 *
 *  **还有人在用它就不让删**（见 [refsOf]）：删掉之后那些引用会变成空引用，按下去只会得到一句
 *  「变量不存在」，用户还得回头找是谁在用它。拦住的理由就写在正文里，那颗按钮灰着，
 *  点一下也照样把这句话再说一遍。
 *
 * @param refs 还有多少处在引用它（0 = 可以删）
 */
@Composable
private fun SettingsPage.VarDeleteDialog(
    deleting: Variable?,
    refs: Int,
    onDismiss: () -> Unit,
    onConfirm: (Variable) -> Unit,
) {
    val blocked = refs > 0
    val summary =
        if (blocked) {
            I18n.format(Strings.VAR_DELETE_BLOCKED, refs)
        } else {
            I18n.get(Strings.VAR_DELETE_SUMMARY, "删掉之后引用它的命令会当它不存在")
        }
    ConfirmDialog(
        show = deleting != null,
        title = I18n.format(Strings.VAR_DELETE_TITLE, deleting?.name.orEmpty()),
        summary = summary,
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        positiveEnabled = !blocked,
        //  灰着也要说清为什么（见 `ConfirmDialog` 的 `onBlocked`）
        onBlocked = { hint(summary) },
        onDismiss = onDismiss,
        onConfirm = {
            deleting?.let { variable -> onConfirm(variable) }
        },
    )
}

/**
 * 命令库读不懂时的「引用数」—— 当成**有引用**，宁可拦着不让删。
 */
private const val REFS_UNREADABLE = 1

/**
 * 这一颗被多少处引用着 —— 绑定 / 命令 / 条件分支一起扫（判据在 `VariableRefs`）。
 *
 *  读在 IO 线程：要读整份命令库（删除是低频动作，值得这一次读盘）。
 */
private fun SettingsPage.refsOf(name: String): Int {
    //  读不懂时按空表算会漏判 —— 那种情况下一律**当成有引用**（宁可拦着不让删）
    val commands =
        when (val loaded = CommandLibrary.loadState()) {
            is CommandLibrary.Load.Ok -> {
                loaded.commands
            }

            CommandLibrary.Load.Absent -> {
                emptyList()
            }

            CommandLibrary.Load.Unreadable -> {
                return REFS_UNREADABLE
            }
        }
    return VariableRefs.count(name, configState.config.value, commands)
}

/**
 * 管数据那一趟：**当场**改这颗变量的当前值 —— 与执行侧同一条 [VariableStore.apply]。
 *
 *  写盘在 IO 线程；成不成都要说一句（弹窗已经收起，不说的话看不出是「改成了」还是「点错了」）。
 */
private fun SettingsPage.applyNow(
    scope: CoroutineScope,
    set: Action.VarSet,
) {
    VarManageState.close()
    scope.launch {
        val ok = withContext(Dispatchers.IO) { VariableStore.apply(set) }
        if (ok) {
            VarListSignal.bump()
            hint(I18n.get(Strings.VAR_SAVED, "已保存"))
        } else {
            hint(I18n.get(Strings.VAR_FAILED, "改不了"))
        }
    }
}

/**
 * 挑一步那一趟：把这个 token 写进 [`picker`] 记的目标（绑定 / 加一步 / 换一步 / 分支动作），
 * 然后退页 —— 这一趟是从「自定义变量」页进来的，写完要回到原来那一页。
 */
private fun SettingsPage.commitStep(set: Action.VarSet) {
    VarManageState.close()
    if (!writeToken(set.token, null)) return
    //  落盘了就不再恢复抽屉：抽屉那边记着「挑完回来把抽屉支起来」，那是**还没选完**时的账
    //  （见 `CommandSteps` 那笔 `resumeSheetAt`）。已经挑成一步了，回去再弹出来等于让人对着
    //  同一条命令再选一遍 —— 与 `onAppPicked` 同一口径。
    picker.cancelResume()
    //  加一步 / 换一步、分支那两条路自己会切页（与挑应用那条同一口径），这一页要被弹掉
    if (picker.forStep || picker.forBranch) return
    picker.clearTarget()
    goBack()
}

/** 删一颗变量：重读 → 摘掉它 → 落盘，成功才刷新清单（`VarListSignal`）。 */
private fun SettingsPage.deleteVar(
    scope: CoroutineScope,
    gone: Variable,
) {
    scope.launch {
        val ok =
            withContext(Dispatchers.IO) {
                VariableStore.save(readVars().filterNot { it.name == gone.name })
            }
        //  删掉了不另说一句：那一行**当场从列表里消失**，比一条提示更直接
        if (ok) {
            VarListSignal.bump()
        } else {
            hint(I18n.get(Strings.VAR_FAILED, "改不了"))
        }
    }
}

/**
 * 这两个入口都进**同一个页面**（「自定义变量」列表页），这一页这一趟是干什么的记在这里。
 */
internal object VarManageState {
    /** 这一趟为什么点开变量：管数据，还是挑一步写回去。 */
    enum class Mode { Manage, Step }

    var mode: Mode by mutableStateOf(Mode.Manage)

    /** 弹窗此刻冲着哪一颗（`null` = 还没点过）。 */
    var target: Variable? by mutableStateOf(null)

    /** 弹窗弹着没有。 */
    var visible: Boolean by mutableStateOf(false)

    /**
     * 进页前调：把这一趟是哪一种写清（首页那一行 / 命令里那三条入口各自调一次）。
     *
     *  顺带把上一趟残留的选中与弹窗清掉 —— 换一趟不该还挂着上一次点开的那一颗。
     */
    fun enter(mode: Mode) {
        this.mode = mode
        target = null
        visible = false
    }

    /** 点列表里一行：冲着这颗弹出来。 */
    fun open(variable: Variable) {
        target = variable
        visible = true
    }

    /** 收起（`target` 留着给退场那一帧画，见 [VarManageDialog]）。 */
    fun close() {
        visible = false
    }
}

/** 首页那一行进来：这一趟是管数据。 */
internal fun SettingsPage.openVariablesForManage() {
    VarManageState.enter(VarManageState.Mode.Manage)
    navTo(Screen.VARIABLES)
}

/** 关掉这张窗之后要接着弹的那一层（两层窗**不叠着开**，见 [VarManageDialog]）。 */
private enum class VarNextStep { Value, Delete }

/** 下一层是哪一层、冲着哪一颗。 */
private class VarNext(
    val step: VarNextStep,
    val variable: Variable,
)

/** 弹窗里的一行动作：文案 + 点下去干什么（两样同生共死，所以合成一个对象）。 */
internal class VarAction(
    val label: String,
    val run: () -> Unit,
)
