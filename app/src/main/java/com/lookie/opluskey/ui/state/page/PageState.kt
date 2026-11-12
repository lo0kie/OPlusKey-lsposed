package com.lookie.opluskey.ui.state.page

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
    private var singleText by mutableStateOf("")

    /**
     * 单条命令的正文 —— **写进来的一律折成一行**（[singleLine]）。
     *
     *  这一道闸是这一栏「看不见文字」的根治点：页面那栏是**单行**输入框，带换行的值进去之后
     *  单行文本节点会把可见区滚到光标所在的那一屏之后（光标落在被裁掉的那几行上）—— 框里
     *  一个字都看不见，而值确实还在。折在**写入口**而不是各个调用点：粘贴、全屏编辑写回、
     *  载入老配置三条路都从这里过，谁都不用记得折一次。
     */
    var single: String
        get() = singleText
        set(value) {
            singleText = singleLine(value)
        }

    val steps = mutableStateListOf<String>()

    /**
     * 跨进程的步骤（最近任务 / 通知中心 / 控制中心 / shell）落地要几百毫秒，不等待就会
     * 「第 3 步打在概览还没起来的桌面上」。
     */
    var stepDelayMs by mutableStateOf(Command.DEFAULT_STEP_DELAY_MS)

    /**
     * 这条命令要不要以 root 执行（单条页那行开关）。
     *
     *  只对单条有意义：多条命令的步骤没有各自的开关，读它的是 `CustomCommandRunner` 的单条那一支。
     *  默认 `false` = 不申请 root（只走 `sh`）；要用 root 的由用户在那行开关上显式打开 ——
     *  打开那一刻会申请一次授权（见 `ui/screen/command/RootRequest`）。
     */
    var root by mutableStateOf(false)

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
        root = false
        editingName = null
        loaded = null
    }

    /**
     * 这一页有没有**改过、还没保存**的东西 —— 与它自己那份 [loaded] 快照逐项比。
     *
     *  两页各问各的：问「要不要放弃未保存的改动」时，判的必须是**将被覆盖的那一页**
     *  （载入一条命令只覆盖它自己那一页，见 `SettingsPage.loadDraft`）—— 拿当前停着的那一页判，
     *  就会出现「问了、确认了，却一点没丢」（那是另一页的内容）或者反过来把这一页的输入无提示吞掉。
     *
     *  新写一条（没有 [loaded] 快照）看 [hasContent]：只用这个当判据时，「点开一条已保存的命令
     *  看一眼、原样返回」也会挨一句「放弃未保存的命令」—— 那句话是假的。
     */
    val dirty: Boolean
        get() {
            val base = loaded ?: return hasContent
            return when (kind) {
                CommandKind.SINGLE -> name != base.name || single != base.single || root != base.root
                CommandKind.SERIAL -> name != base.name || stepDelayMs != base.stepDelayMs || steps.toList() != base.steps
            }
        }

    /** 把已保存的 [cmd] 灌进这一页，进入编辑态。 */
    fun load(cmd: Command) {
        name = cmd.name
        //  走 setter：带换行的老配置在这里折成一行（否则这一栏一打开就是空的）
        single = cmd.single
        steps.clear()
        steps.addAll(cmd.steps)
        stepDelayMs = cmd.stepDelayMs
        root = cmd.root
        editingName = cmd.name
        // 快照按**折过之后**那份记：只改表单不动它，[CommandDraft.dirty] 就是「与它比有没有变」——
        //  记原文的话，打开一条带换行的老命令会立刻被判成「改过」，退出时白问一句。
        loaded = cmd.copy(single = single)
    }
}

/**
 * 命令正文的**单行形态** —— 带换行的折成一行，没换行的原样返回。
 *
 * ## 为什么要折
 *
 *  页面那一栏是单行输入框（库的 `InputField`），带换行的值在里面**一个字都看不见**：
 *  单行文本节点把 `\n` 当硬换行、`maxLines = 1` 又把后几行裁掉，而可见区是跟着光标走的
 *  —— 光标在末尾（被裁掉的那一行），整块可见区就滚到了正文之后。用户看到的是「框是空的」，
 *  存下去的值却完好。
 *
 *  而「命令正文是单行」本来就是这一页的口径（要串几步请用「多条命令」），所以换行在写进草稿
 *  那一刻就折掉：粘贴一段脚本、或者在带换行的老配置上改一个字，都不会再出现那个空格子。
 *
 * ## 怎么折
 *
 *  · 先归一 CRLF / CR；逐行去首尾空白、**丢掉空行**（`a` `空行` `b` 折出来不能是 `a; ; b`
 *    —— 那是语法错误）；
 *  · **整行的注释丢掉**（去空白后以 `#` 开头，含 `#!` 那行）：折成一行之后 `#` 会把**后面所有行**
 *    都变成注释 —— 一行注释就能让半条命令静默失效，那是比看不见字更糟的一种「看起来正常」；
 *    行内注释（`echo a # b`）没有处理：要分清它，得先做一遍引号解析，那是另一个体量的东西；
 *  · 两行之间补 `; `：换行的语义就是「依次执行」，`;` 是它最短的对应物；
 *  · 上一行末尾已经是 `&&` / `||` / `|` / `;` / `:` / `=` / `(` 时只补一个空格 ——
 *    这些记号**等着右边的操作数**，再插一个 `;` 就是 `a &&; b` 那种语法错误。
 *    `shell:` 单独占一行（从聊天里连前缀一起粘过来就是这种形状）也靠这一条接上；
 *  · 行尾的反斜杠是「连行」，把反斜杠去掉、直接补一个空格（它本来就把两行连成一条命令）。
 *
 * ## 为什么没换行要原样返回
 *
 *  逐行 trim 会把**正在输入的空格**吃掉（`echo ` 的尾空格），那等于让这一栏敲不出参数间隔。
 *  只在真的有换行时才动它，正常打字一个字符都不碰。
 */
internal fun singleLine(raw: String): String {
    if (!raw.contains('\n') && !raw.contains('\r')) return raw
    val lines =
        raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    val out = StringBuilder(raw.length)
    lines.forEachIndexed { i, line ->
        if (i > 0) {
            val prev = lines[i - 1]
            when {
                prev.endsWith("\\") -> {
                    //  连行：把上一行末尾那个反斜杠（连同它前面的空白）摘掉，两段直接接上 ——
                    //  反斜杠后面紧跟一个空格，字符串里就会多出一个空格
                    out.setLength(out.length - 1)
                    while (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
                    out.append(' ')
                }

                JOIN_WITH_SPACE.any { prev.endsWith(it) } -> {
                    out.append(' ')
                }

                else -> {
                    out.append("; ")
                }
            }
        }
        out.append(line)
    }
    return out.toString()
}

/**
 * 行尾是这几个记号时，折行只补空格、不补 `;` —— 它们都等着右边的操作数。
 *
 *  `:` 收的是 `shell:` / `cmd:` / `keyevent:` 这些前缀（从聊天里粘脚本时前缀常常单独一行）。
 */
private val JOIN_WITH_SPACE = listOf("&&", "||", "|", ";", ":", "=", "(")

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

    var root: Boolean
        get() = form.root
        set(value) {
            form.root = value
        }

    var editingName: String?
        get() = form.editingName
        set(value) {
            form.editingName = value
        }

    val hasContent: Boolean
        get() = form.hasContent

    /**
     * 当前这一页有没有改过还没保存的东西 —— [CommandForm.dirty] 的快捷方式。
     *
     *  只看当前这一页：另一页写过什么、载入过什么，都不该在这一页退出时挨一句警告。
     *  要问**某一类**那一页（载入一条命令之前），用 `formOf(kind).dirty`。
     */
    val dirty: Boolean
        get() = form.dirty

    /**
     * 清空**当前这一页**的表单 —— 另一页原样不动。
     *
     *  两页是两个**互不相干**的编辑会话：存完一条、清空一栏、从别处新开一条，动的都只是它自己
     *  那一页。两页一起清会无提示吞掉另一页正在写的内容（「在多条页编了一半，切到单条页存一条，
     *  回来发现多条页空了」就是这么来的）。
     *
     *  整页归零那一档（页面出栈）走 [resetAll]。
     *
     *  **不碰 [kind]**：`kind` 是「pager 停在哪一页」的镜像，由 `CommandScreen` 里那条
     *  `snapshotFlow { currentPage }` **单向**同步 —— 在这里强设成 `SINGLE` 会与 pager 打架：
     *  停在「多条」页保存完一条，`kind` 已经变成单条而 pager 还在多条 ⇒ 这一页接下来新增的步骤
     *  会按**单条**分支保存（步骤静默丢掉），直到用户手动切一次 tab 才自愈。归位交给 pager。
     */
    fun reset() {
        form.reset()
    }

    /**
     * **两页一起清** —— 只有「这一页整个出栈」用（`CommandDraftState.discard`）。
     *
     *  与 [reset] 分开而不是留一个 `both: Boolean` 参数：两处的意图相反，参数化之后每个调用点
     *  都要读一遍实参才知道它清了几页 —— 而这一处踩过的正是「存完一条，顺手把另一页也清了」。
     */
    fun resetAll() {
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
     * 放掉这一套 —— 换页时被 [com.lookie.opluskey.ui.chrome.PageViewTree.attachOne] 调（那一页已经不在）。
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
