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
 * 「自定义命令」页正在编辑的那条命令（草稿）。
 *
 * 用 Compose 的 `mutableStateOf` / `mutableStateListOf` 存，所以表单是「边输边存」的：
 * push 到「添加命令」那一级再回来，名称 / 类型 / 已经挑好的步骤都还在。
 */
internal class CommandDraft {
    var kind by mutableStateOf(CommandKind.SINGLE)
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
     */
    var editingName by mutableStateOf<String?>(null)

    /**
     * 表单里有没有**用户填过的东西**（含刚载入的已保存命令）。「保存」那类判定用它；
     * **退出要不要问「放弃未保存的命令」不要用它** —— 用 [dirty]。
     */
    val hasContent: Boolean
        get() = name.isNotBlank() || single.isNotBlank() || steps.isNotEmpty()

    /** [load] 进来的那份快照（[reset] 清掉）—— 只服务 [dirty]。 */
    private var loaded: Command? = null

    /**
     * 有没有**改过、还没保存**的东西 —— 退出「自定义命令」页要不要问那一句，判据就是它。
     * 编辑一条已保存的命令时与**载入时那份快照**逐项比（一字未改不算），新写一条则看 [hasContent]。
     *
     *  不用 [hasContent] 当这个判据：退出时每次返回都过一遍，「点开一条已保存的命令看一眼、
     *  原样返回」也会挨一句「放弃未保存的命令」——那句话是假的。
     *
     *  注意：**不比 [kind]**：它是「pager 停在哪一页」的镜像、不是用户改的内容
     *  （`CommandScreen` 那条 `snapshotFlow { currentPage }` 单向同步它）。换 tab 时它必然与
     *  `loaded.kind` 不同，于是「只是翻了下 tab、什么都没改」也会判脏 ⇒ 退出时挨一句
     *  「放弃未保存的命令」。而 [reset] 本来就不碰 `kind`、[hasContent] 也不含它 —— 把它从比较里
     *  去掉，与另外两条口径一致。
     */
    val dirty: Boolean
        get() {
            val base = loaded ?: return hasContent
            return name != base.name || single != base.single ||
                stepDelayMs != base.stepDelayMs || steps.toList() != base.steps
        }

    /**
     * 表单内容的**指纹** —— 给「重置收尾」那段时间里用户又输入了没有当判据（见 `CommandScreen`）。
     *
     *  「重置」对有多条步骤的串行命令走行级退场：先让步骤行播完 shrink（`STEP_ROW_EXIT_MS` 再加
     *  40ms 余量），再由页面级协程真正 [reset]。这段时间里用户完全可能已经又开始敲字（焦点还在
     *  输入框上），此时无条件 `reset()` 会把刚敲进去的内容一起吞掉。指纹相等才收尾。
     *
     *  **不含 [kind]**：它是 pager 停在哪一页的镜像，[reset] 不碰它、「改没改内容」也不看它
     *  （见 [dirty]）。把它算进指纹的话，退场那几百毫秒里顺手切一次 tab 就会被判成「用户又改了」，
     *  重置静默放弃 —— 而那一次切页什么都没写。
     */
    fun contentFingerprint(): String = "$name\u0000$single\u0000$stepDelayMs\u0000${steps.joinToString("\u0001")}"

    /**
     * 保存成功、或从菜单重新进来时清空。
     *
     * 注意：**不碰 [kind]**：`kind` 是「pager 停在哪一页」的镜像，由
     *  `CommandScreen` 里那条 `snapshotFlow { currentPage }` **单向**同步 —— 在这里强设成
     *  `SINGLE` 会与 pager 打架：停在「多条」页保存完一条，`kind` 已经变成单条而 pager 还在
     *  多条 ⇒ 这一页接下来新增的步骤会按**单条**分支保存（步骤静默丢掉），直到用户手动切一次
     *  tab 才自愈。归位交给 pager：它是这一份状态的唯一来源。
     */
    fun reset() {
        name = ""
        single = ""
        steps.clear()
        stepDelayMs = Command.DEFAULT_STEP_DELAY_MS
        editingName = null
        loaded = null
    }

    /** 把已保存的 [cmd] 灌进表单，进入编辑态。 */
    fun load(cmd: Command) {
        kind = cmd.kind
        name = cmd.name
        single = cmd.single
        steps.clear()
        steps.addAll(cmd.steps)
        stepDelayMs = cmd.stepDelayMs
        editingName = cmd.name
        // 快照按**载入进来那一份**记：之后只改表单不动它，[dirty] 就是「与它比有没有变」
        loaded = cmd
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
