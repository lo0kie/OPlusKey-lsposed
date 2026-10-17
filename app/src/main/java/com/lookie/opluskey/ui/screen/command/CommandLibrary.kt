package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.host.action.ActionRunner
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.screen.apppicker.appLabelOf
import com.lookie.opluskey.ui.screen.binding.BindingScreen
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.saveCommands
import com.lookie.opluskey.ui.state.tickNow
import com.lookie.opluskey.ui.state.toast
import com.lookie.opluskey.ui.state.write
import kotlin.math.roundToInt

internal fun SettingsPage.testCommand(cmd: Command) {
    XLog.w("测试命令：${cmd.name}（${cmd.kind.id}）—— 在设置页进程里直接执行")
    // 与页面其它点击同一条触觉通道（判据只有系统状态）
    tickNow()
    // 页面动作失败绝不外抛（本文件里其它入口都是这条纪律）：测试命令直接跑在点击回调上，
    //  从执行器里抛出来会打到 Compose 的点击处理里。
    try {
        testRunner(context).runTest(cmd.name)
    } catch (e: Throwable) {
        XLog.w("测试命令失败（已忽略）：${cmd.name}", e)
        toast(I18n.get(Strings.TOAST_COMMAND_TEST_FAILED, "测试失败"))
        return
    }
    //  注意：这里只能保证「**已派发**」，不是「已完成」：单条 `keyevent:` 是
    //  同步执行的、这一刻已经生效；但 `shell:`（起进程后即返回、后台线程等结果）与多条命令
    //  （`postDelayed` 续步）的**完成**晚于这一行 —— 说「已执行」是把派发说成了完成。
    //  命令可能一点可见动静都没有，没有这句分不清「发了」和「没反应」，所以保留提示、改口径
    //  （失败仍会由 `CommandFailure` 那条另弹一句）。
    toast(I18n.get(Strings.TOAST_COMMAND_TRIGGERED, "已触发"))
}

/**
 * 测试命令用的执行器 —— 本进程只建一份：内部执行器持进程级的东西（Handler、系统服务），
 * 每点一次新建会平白多出注册（如手电筒 `TorchCallback`）。
 *
 * 必须传 Application Context：`ActionRunner` 会把传进来的 Context 一直拿着，而这里又是
 * 进程级缓存 —— 传 Activity Context 等于永久钉住一个已销毁的 Activity 及其整棵 View 树。
 */
private var testRunnerCache: ActionRunner? = null

internal fun testRunner(context: android.content.Context): ActionRunner =
    testRunnerCache ?: ActionRunner(
        context.applicationContext,
        android.os.Handler(android.os.Looper.getMainLooper()),
    ).also { testRunnerCache = it }

/**
 * 「名称与某条已有命令重名」的判定（单一来源）：编辑态下与原命令同名不算重复，
 * 只有改成别的已存在命令的名字才是冲突。
 *
 * 注意：两侧都必须按存库口径（[CommandLibrary.sanitizeName]，含 trim）比：落盘时名字会 trim，
 * 所以「前后各打一个空格」的那份草稿在库里与已有命令**同名** —— 按未 trim 的原样比会判成不重复、
 * 放它写下去，结果静默覆盖掉同名命令（用户只看到一句「已覆盖」）。
 */
internal fun SettingsPage.commandNameDuplicated(
    name: String,
    editingName: String?,
): Boolean {
    val n = CommandLibrary.sanitizeName(name)
    if (n.isEmpty()) return false
    val editing = editingName?.let { CommandLibrary.sanitizeName(it) }
    return configState.commands.value.any { it.name == n && it.name != editing }
}

/**
 * 「这次保存会不会让 `cmd:` 引用成环」的判定（单一来源）：把**当前草稿**当成库里那条命令的新定义
 * 放进去（[draftToLibrary]），再从它出发走一遍引用图（[Command.cyclePath]）。
 *
 *  必须带草稿判、不能只看文件里那份：环常常是「编辑这一步」才产生的（把某一步改成 `cmd:` 引到
 *  自己上游那条）。成环本身不会卡死（执行侧有 `MAX_COMMAND_DEPTH` 兜底），但会**重复副作用** ——
 *  整条链跑到深度上限才停，写 shell 的那种动作会真跑 4~5 遍，所以保存这一步直接拦掉。
 *
 * @return 环上的名字（首尾同一个，如 `[A, B, A]`），没有环返回 `null`
 */
internal fun SettingsPage.commandReferenceCycle(): List<String>? {
    val (candidate, next) = draftToLibrary() ?: return null
    return Command.cyclePath(candidate, next)
}

/**
 * 命令卡片的一行：宽度平分、同行等高、且不矮于正方形。
 *
 * `Row` 做不到（`weight` 只分宽度、`IntrinsicSize.Min` 量不出正方形下限），所以自己量
 * 两趟：先按宽度平分量各卡自然高度，行高取「最高那张」与卡宽的较大者，再按行高重量。
 *
 * @param tileCount 这一行的卡数（不足 [COLUMNS] 时右侧留空）
 */
@Composable
internal fun CommandTileRow(
    tileCount: Int,
    /** 行高下限 = 卡宽 × 本值（单条给 [SINGLE_CARD_MIN_HEIGHT_RATIO]，多条给 `1f`）。 */
    minHeightRatio: Float,
    modifier: Modifier = Modifier,
    tile: @Composable (Int) -> Unit,
) {
    Layout(
        content = { repeat(tileCount) { index -> tile(index) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val gapPx = GRID_GAP.roundToPx()
        // 宽度平分：可用宽 − (COLUMNS−1) 个列距，除以 COLUMNS —— 与这行有几张卡无关。
        val tileWidth = (constraints.maxWidth - gapPx * (COLUMNS - 1)) / COLUMNS
        // 各卡自然高度用固有测量（`minIntrinsicHeight`）拿，不能 `measure()` 两次 —— 真机 R8 包会
        // `IllegalStateException` 崩掉宿主进程；固有测量不占那次唯一的 measure 配额。
        val contentHeights = measurables.map { it.minIntrinsicHeight(tileWidth) }
        val rowHeight = maxOf((tileWidth * minHeightRatio).roundToInt(), contentHeights.max())
        // 唯一一次 measure：按行高定量 —— 同行等高、不矮于下限
        val placeables =
            measurables.map {
                it.measure(Constraints.fixed(tileWidth, rowHeight))
            }
        layout(constraints.maxWidth, rowHeight) {
            placeables.forEachIndexed { index, p -> p.place(index * (tileWidth + gapPx), 0) }
        }
    }
}

/**
 * 草稿 → 「存下去之后的那条命令」与「存下去之后的那份命令库」。
 *
 * **单一来源**：[addCommand] 存的就是它、[commandReferenceCycle] 判的也是它 —— 两处各写一遍的话，
 * 判的与存的就不是同一件事（改了名字、挪了引用之后，环也可能跟着变）。
 *
 * @return 名字为空（草稿还没填名字）时返回 `null`
 */
internal fun SettingsPage.draftToLibrary(): Pair<Command, List<Command>>? {
    val name = CommandLibrary.sanitizeName(commandDraft.draft.name)
    if (name.isEmpty()) return null
    val cmd =
        when (commandDraft.draft.kind) {
            CommandKind.SINGLE -> {
                Command(name, CommandKind.SINGLE, single = commandDraft.draft.single.trim())
            }

            CommandKind.SERIAL -> {
                Command(
                    name = name,
                    kind = CommandKind.SERIAL,
                    steps = commandDraft.draft.steps.toList(),
                    stepDelayMs = commandDraft.draft.stepDelayMs,
                )
            }
        }
    // (1) 改名保存必须把**原名那条一起摘掉**：只 `filterNot { it.name == name }` 的话旧名与新名
    //   两条并存在命令库里（列表凭空 +1 条），而手势上绑的仍是旧那条 `cmd:旧名` —— 用户以为改了，
    //   侧键行为一点没变。编辑态下 `editingFrom == name` 时两个条件指同一条，去重天然成立。
    // (2) 其余命令里对旧名字的引用（多条命令的步骤 / 单条正文里的 `cmd:旧名`）也要跟着挪，
    //   否则它们指向一条已经不在库里的命令，按下去永远 NOT_FOUND。
    val editingFrom = commandDraft.draft.editingName
    val next =
        configState.commands.value
            .filterNot { it.name == name || it.name == editingFrom }
            .map { it.renamedReference(editingFrom, name) } + cmd
    return cmd to next
}

/**
 * 把草稿里的那条命令存进命令库（不是立刻绑到某个手势上）。
 *
 * 重名时表单保存按钮已被 [commandNameDuplicated] 禁掉、成环时被 [commandReferenceCycle] 禁掉；
 * 本函数保留「按名字写入」的语义，是给编辑态（名字没变 = 它自己）兜底的。多条命令按名字引用单条命令
 * （步骤里存 `cmd:<名字>`），所以改名 / 删命令只能走编辑页，别在库层面直接动名字。
 */
internal fun SettingsPage.addCommand() {
    val (cmd, next) = draftToLibrary() ?: return
    val name = cmd.name
    val editingFrom = commandDraft.draft.editingName
    val replaced = configState.commands.value.any { it.name == name }
    // 结果一句话（改 / 覆盖 / 新增各一条；失败由 [saveCommands] 自己报）
    val tip =
        when {
            editingFrom != null -> I18n.get(Strings.TOAST_COMMAND_MODIFIED, "已修改")
            replaced -> I18n.get(Strings.TOAST_COMMAND_OVERWRITTEN, "已覆盖")
            else -> I18n.get(Strings.TOAST_COMMAND_SAVED, "已保存")
        }
    // 改名回滚要用它：库先落盘、绑定后挪，绑定没挪成时得把库退回改名前的样子（见下面那条）。
    val previousLibrary = configState.commands.value
    val saveResult =
        saveCommands(
            next,
            when {
                // 编辑态：提交的是修改（名字没变 = 原地更新；改了名 = 挪到新名字下）
                editingFrom != null && editingFrom != name -> "改名保存：$editingFrom → $name（${cmd.kind.id}）"

                editingFrom != null -> "修改命令：$name（${cmd.kind.id}）"

                replaced -> "覆盖命令：$name（${cmd.kind.id}）"

                else -> "新增命令：$name（${cmd.kind.id}）"
            },
        )
    // 没落盘就**到此为止**：下面那套 `reset` + 退出会把用户刚编的整份内容丢掉，
    //  而文件里一个字节都没变（失败原因 [saveCommands] 已经报过「保存失败」）。
    //  必须看返回值：写盘失败 / 只读态**不能**照样清表单并退出。
    if (saveResult == CommandsSave.Failed) {
        XLog.w("保存命令失败：表单保持原样，不 reset、不退出（$name）")
        return
    }
    // (3) 手势上绑的还是 `cmd:旧名`，旧那条已经不在库里 —— 跟 [removeCommand] 同一套做法，
    //   把绑定一起挪到新名字下，否则那句「已修改」是句空话。
    var bindingMoved = true
    if (editingFrom != null && editingFrom != name) {
        val cfg = configState.config.value
        val key = cfg.key(KeyId.PLUS)
        val oldToken = Command.PREFIX + editingFrom

        fun move(a: Action) = if (a.token == oldToken) Action.Custom(name) else a
        val updated =
            key.copy(
                single = move(key.single),
                double = move(key.double),
                long = move(key.long),
            )
        if (updated != key) {
            bindingMoved =
                write(
                    cfg.copy(keys = cfg.keys + (KeyId.PLUS to updated)),
                    "改名后把绑定挪到新名字：$editingFrom → $name",
                )
        }
    }
    // 绑定没挪成 ⇒ 把命令库**退回改名前的样子**：库里是新名、手势上还指着 `cmd:旧名` 的话，
    //  那只手按下去就是「这条命令已经不在了」（NOT_FOUND），而用户看到的却是「已修改」。
    //  与 [removeCommand] 同一条纪律 —— 两边要么一起变、要么都不变。
    // 绑定挪成了、但上面广播没发出去（[CommandsSave.BroadcastFailed]）时不报「已修改」：
    //  那句会让用户以为侧键行为也跟过来了，而 saveCommands 已经报过「通知注入侧失败」。
    if (!bindingMoved) {
        saveCommands(previousLibrary, "改名失败，回滚命令库：$editingFrom → $name")
    } else if (saveResult == CommandsSave.Ok) {
        toast(tip)
    }
    // `editing` 必须在 `draft.reset()` 之前读 —— reset 会把 `editingName` 清掉。
    val editing = editingFrom != null
    commandDraft.draft.reset()
    // 只在「新增」这条路上退（从「按键绑定」页右下角那颗 FAB 进来的那条）；编辑态
    // （从「已保存的命令」列表点进来改）留在原地 —— 改完要能立刻在下面那张列表里看到结果。
    if (!editing) goBack()
}

/**
 * 删一条命令 —— 三处引用都要清，否则留下的是指向空处的东西：
 *  1. 绑到手势上的 `cmd:<名字>` → 改成「无操作」（不然按下去永远解析不到）；
 *  2. 别的多条命令里对它的引用 → 摘掉那一步；
 *  3. 别的**单条**命令正文里写的 `cmd:<被删名字>` → 改成 `none`（同理；早先只扫 SERIAL 的步骤，
 *    单条正文里那条引用会被留下来）。
 */
internal fun SettingsPage.removeCommand(cmd: Command) {
    val cfg = configState.config.value
    val key = cfg.key(KeyId.PLUS)

    fun clean(a: Action) = if (a.token == cmd.token) Action.None else a
    val updated =
        key.copy(
            single = clean(key.single),
            double = clean(key.double),
            long = clean(key.long),
        )
    val bindingsCleared = updated != key
    if (bindingsCleared) {
        // 只读态下这次写会被拒（返回值不能丢）：命令库那份照删，配置里就留下悬空的
        //  `cmd:` —— 按下去永远 NOT_FOUND。所以绑定没清掉时**不能**继续删命令，先告诉用户。
        val ok =
            write(
                cfg.copy(keys = cfg.keys + (KeyId.PLUS to updated)),
                "删除命令时清掉绑定：${cmd.name}",
            )
        if (!ok) {
            XLog.w("删除命令中止：绑定没能清掉（配置只读），命令库保持原样：${cmd.name}")
            return
        }
    }

    // 删的就是正在编辑的那条 ⇒ 草稿一并清掉：留着的话「修改」按钮还挂着，
    // 点一下会把刚删的命令原样写回去。注意：这一步挪到**命令库真删掉之后**（见下）：
    // 库没删成时草稿要留着，否则用户正在编的那条被清空、而库里那条还在。
    val next =
        configState.commands.value
            .filterNot { it.name == cmd.name }
            .map { it.dropReference(cmd.token) }

    // 删的若是**另一条**命令（草稿正在编 B、删的是 A），草稿里对 A 的引用也要一起摘 ——
    //  与命令库同一条纪律（见上面 [Command.dropReference]）。留着的话用户随手一保存就把它写回库：
    //  库里重新出现指向不存在命令的条目，而**重名与成环两道闸都不认它**（重名只看名字；
    //  成环遇到不存在的目标直接判「没有环」）。
    //  口径与库里那两处一致：单条正文里那条引用改成「无操作」，多条命令里那一步整条摘掉。
    fun cleanDraftRefs() {
        if (Action.parse(commandDraft.draft.single).token == cmd.token) commandDraft.draft.single = Action.None.token
        val left = commandDraft.draft.steps.filterNot { it == cmd.token }
        if (left.size != commandDraft.draft.steps.size) {
            commandDraft.draft.steps.clear()
            commandDraft.draft.steps.addAll(left)
        }
    }

    when (saveCommands(next, "删除命令：${cmd.name}")) {
        CommandsSave.Ok -> {
            if (commandDraft.draft.editingName == cmd.name) commandDraft.draft.reset() else cleanDraftRefs()
            toast(I18n.get(Strings.TOAST_COMMAND_DELETED, "已删除"))
        }

        // 库已经删掉了（只是没通知到注入侧，提示由 saveCommands 给）——草稿该清还是清，
        //  但不再报「已删除」：那句与失败提示叠在一起，读起来像发生了两件事。
        CommandsSave.BroadcastFailed -> {
            if (commandDraft.draft.editingName == cmd.name) commandDraft.draft.reset() else cleanDraftRefs()
        }

        CommandsSave.Failed -> {
            if (bindingsCleared) {
                // 注意：命令库没删成、绑定却已经清了 ⇒ 会留下「手势=无操作 + 命令仍在库」这种半份状态
                // ：把绑定**写回原样**，两边一起保持原状。
                //  失败原因 [saveCommands] 已经报过（「保存失败」），这里只补回滚与它的判据行。
                val rolled = write(cfg, "删除命令失败，回滚绑定：${cmd.name}")
                XLog.w("删除命令：命令库写入失败，已把绑定回滚（回滚成功=$rolled）：${cmd.name}")
            }
        }
    }
}
