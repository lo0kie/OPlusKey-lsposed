package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind

// 页面用到的两个状态容器：`CommandDraft` 是纯表单模型，`FallbackOwners` 是宿主不给 owner
// 时的兜底实现。两者都不引用外层类的任何东西，从 `ui/SettingsPage` 原样拆出（去掉 `inner`）。

/**
 * 一页表单的全部状态 —— 名称、正文 / 步骤、间隔、**编辑态**与**载入快照**都归它。
 *
 *  单条页与多条页各一份（见 [CommandDraft.singleForm] / [CommandDraft.serialForm]）：切 tab
 *  只换「现在看的是哪一份」，什么都不清 —— 名字不会丢、编辑态不会变回「新增」、
 *  红字也不会跑到另一页（校验由**这一份**的名字算出来，见 `commandNameProblem`）。
 */
internal class CommandForm(
    /** 这一份属于哪一类命令。 */
    val kind: CommandKind,
) {
    var name by mutableStateOf("")
    var single by mutableStateOf("")
    val steps = mutableStateListOf<String>()

    /**
     * 跨进程的步骤（最近任务 / 通知中心 / 控制中心 / shell）落地要几百毫秒，不等待就会
     * 「第 3 步打在概览还没起来的桌面上」。
     */
    var stepDelayMs by mutableStateOf(Command.DEFAULT_STEP_DELAY_MS)

    /**
     * 「点击已保存的命令时可编辑」—— 加载后记着原名，UI 据此把卡片标题 / 提交按钮切成
     * 「修改命令 / 修改」；`addCommand` 据此判「名字改没改」（改了 = 真替换，没改 = 原地覆盖）。
     *
     *  **每页各一份**：编着多条命令时翻到单条页、再翻回来，仍然是在编那一条 ——
     *  原先只有一份、切 tab 时被迫清掉，于是「来回切一下就变成新增态」。
     */
    var editingName by mutableStateOf<String?>(null)

    /** [load] 进来的那份快照（[reset] 清掉）—— 只服务 [CommandDraft.dirty]。 */
    var loaded: Command? = null

    /**
     * 这一页的表单里有没有**用户填过的东西**（含刚载入的已保存命令）。
     *
     *  只看**本页**的字段：单条页看名称 + 正文，多条页看名称 + 步骤 —— 另一页写了什么与它无关。
     */
    val hasContent: Boolean
        get() =
            name.isNotBlank() ||
                when (kind) {
                    CommandKind.SINGLE -> single.isNotBlank()
                    CommandKind.SERIAL -> steps.isNotEmpty()
                }

    /** 清空这一页。 */
    fun reset() {
        name = ""
        single = ""
        steps.clear()
        stepDelayMs = Command.DEFAULT_STEP_DELAY_MS
        editingName = null
        loaded = null
    }

    /** 把已保存的 [cmd] 灌进这一页，进入编辑态。 */
    fun load(cmd: Command) {
        name = cmd.name
        single = cmd.single
        steps.clear()
        steps.addAll(cmd.steps)
        stepDelayMs = cmd.stepDelayMs
        editingName = cmd.name
        // 快照按**载入进来那一份**记：之后只改表单不动它，[CommandDraft.dirty] 就是「与它比有没有变」
        loaded = cmd
    }
}

/**
 * 「自定义命令」页正在编辑的那条命令（草稿）—— **两页各一份表单**（[singleForm] / [serialForm]）。
 *
 * 用 Compose 的 `mutableStateOf` / `mutableStateListOf` 存，所以表单是「边输边存」的：
 * push 到「添加命令」那一级再回来，名称 / 类型 / 已经挑好的步骤都还在。
 *
 *  下面这些字段都是**当前这一页**的快捷方式：`kind` 是 pager 停在哪一页的镜像，读写都落到对应
 *  那一份表单上。调用点因此一行都不用改，而两页的内容、编辑态与校验互不影响。
 */
internal class CommandDraft {
    var kind by mutableStateOf(CommandKind.SINGLE)

    /** 单条那一页的表单。 */
    val singleForm = CommandForm(CommandKind.SINGLE)

    /** 多条那一页的表单。 */
    val serialForm = CommandForm(CommandKind.SERIAL)

    /** 当前这一页那一份（`kind` 由 `CommandScreen` 那条 `snapshotFlow` 单向同步）。 */
    val form: CommandForm
        get() = formOf(kind)

    /**
     * 某一类命令对应的那份表单 —— 两页各一份，[form] 是「当前这一页」的快捷方式。
     *
     *  按**某条命令的类型**去动表单时用它（例如列表页里对一条命令点「再点一次编辑」＝重置，
     *  那一下要清的是**这条命令所属那一类**的表单，不一定是当前停在那一页的那份）。
     */
    fun formOf(kind: CommandKind): CommandForm = if (kind == CommandKind.SINGLE) singleForm else serialForm

    var name: String
        get() = form.name
        set(value) {
            form.name = value
        }

    var single: String
        get() = form.single
        set(value) {
            form.single = value
        }

    val steps: MutableList<String>
        get() = form.steps

    var stepDelayMs: Int
        get() = form.stepDelayMs
        set(value) {
            form.stepDelayMs = value
        }

    var editingName: String?
        get() = form.editingName
        set(value) {
            form.editingName = value
        }

    val hasContent: Boolean
        get() = form.hasContent

    /**
     * 有没有**改过、还没保存**的东西 —— 退出「自定义命令」页要不要问那一句，判据就是它。
     *
     *  编辑一条已保存的命令时与**载入时那份快照**逐项比（一字未改不算），新写一条则看 [hasContent]。
     *  不用 [hasContent] 当这个判据：退出时每次返回都过一遍，「点开一条已保存的命令看一眼、
     *  原样返回」也会挨一句「放弃未保存的命令」——那句话是假的。
     *
     *  **只看当前这一页**：另一页写过什么、载入过什么，都不该在这一页退出时挨一句警告 ——
     *  那是它的东西（见 [CommandForm]）。
     */
    val dirty: Boolean
        get() {
            val form = form
            val base = form.loaded ?: return form.hasContent
            return when (form.kind) {
                CommandKind.SINGLE -> {
                    form.name != base.name || form.single != base.single
                }

                CommandKind.SERIAL -> {
                    form.name != base.name || form.stepDelayMs != base.stepDelayMs || form.steps.toList() != base.steps
                }
            }
        }

    /**
     * 保存成功、或从菜单重新进来时清空**两页**。
     *
     *  注意：**不碰 [kind]**：`kind` 是「pager 停在哪一页」的镜像，由
     *  `CommandScreen` 里那条 `snapshotFlow { currentPage }` **单向**同步 —— 在这里强设成
     *  `SINGLE` 会与 pager 打架：停在「多条」页保存完一条，`kind` 已经变成单条而 pager 还在
     *  多条 ⇒ 这一页接下来新增的步骤会按**单条**分支保存（步骤静默丢掉），直到用户手动切一次
     *  tab 才自愈。归位交给 pager：它是这一份状态的唯一来源。
     */
    fun reset() {
        singleForm.reset()
        serialForm.reset()
    }

    /** 把已保存的 [cmd] 灌进**它自己那一页**的表单，进入编辑态。 */
    fun load(cmd: Command) {
        kind = cmd.kind
        form.load(cmd)
    }
}

/**
 * 宿主不提供时，我们自建的那套 ViewTree owner。
 *
 * 三件事必须做对，否则 Compose 在 attach 时抛异常（真机三轮踩出来的）：
 *  1. `LifecycleRegistry` 要推到 `RESUMED` —— 停在 `INITIALIZED` 时 `WindowRecomposer`
 *     认为窗口不可见，组合不跑（页面一片空白）；
 *  2. `SavedStateRegistryController.performAttach()` + `performRestore(null)` 必须在
 *     推进 lifecycle **之前**完成，否则抛
 *     `IllegalStateException: You must call performAttach() before calling performRestore()`；
 *  3. **绝不推进到 `DESTROYED`** —— Compose 的 `Recomposer` 收到就永久取消。
 *
 * **只在宿主确实三件套不齐时才创建**：宿主给全了就让 Compose 沿 parent 链去拿宿主那一套
 * （那才是正路）。绝不能造一个「用不上」的占位实例去挂 —— `attachOne` 是「只挂不覆盖」，
 * 占位实例被钉进 `ComposeView` 之后就再也换不掉，Compose 会把那份停在 `INITIALIZED`
 * 的 lifecycle 当成真的 ⇒ 页面一片空白。
 */
internal class FallbackOwners :
    androidx.lifecycle.LifecycleOwner,
    androidx.lifecycle.ViewModelStoreOwner,
    androidx.savedstate.SavedStateRegistryOwner {
    private val registry = androidx.lifecycle.LifecycleRegistry(this)

    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry

    override val viewModelStore: androidx.lifecycle.ViewModelStore = androidx.lifecycle.ViewModelStore()

    private val savedController = androidx.savedstate.SavedStateRegistryController.create(this)

    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = savedController.savedStateRegistry

    init {
        try {
            savedController.performAttach()
            savedController.performRestore(null)
        } catch (e: Throwable) {
            XLog.w("SavedStateRegistry 初始化失败（Compose 可能起不来）", e)
        }
        try {
            registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        } catch (e: Throwable) {
            XLog.w("推进兜底 Lifecycle 到 RESUMED 失败", e)
        }
    }

    /**
     * 放掉这一套 —— 换页时被 [com.lookie.opluskey.ui.PageViewTree.attachOne] 调（那一页已经不在）。
     *
     *  只清 `ViewModelStore`：里面的 ViewModel 不该跟着宿主活到进程结束。**不动 lifecycle**：
     *  上一页的 View 可能还在退场动画里被引用着，把它置成 DESTROYED 会让还在组合里的东西当场抛。
     */
    fun dispose() {
        try {
            viewModelStore.clear()
        } catch (e: Throwable) {
            XLog.w("清兜底 ViewModelStore 失败（已忽略）", e)
        }
    }
}
