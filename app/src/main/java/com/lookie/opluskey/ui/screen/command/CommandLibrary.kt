package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.host.action.ActionRunner
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.draftToLibrary
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.saveCommands
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.TestRunnerCache
import com.lookie.opluskey.ui.tickNow
import com.lookie.opluskey.ui.toast
import com.lookie.opluskey.ui.write
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
 * 测试命令用的执行器 —— 见 [TestRunnerCache]：本进程只建一份（内部执行器持进程级的
 * Handler、系统服务与手电筒回调注册，每点一次新建会平白多出注册），页面关掉时释放。
 */
private fun testRunner(context: android.content.Context): ActionRunner = TestRunnerCache.acquire(context)

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
    /**
     * 行高上限 = 卡宽 × 本值；`0f` 表示不封顶。
     *
     *  单条命令那一行封在 `1f`（正方形）：一条命令的摘要长短差得很远，长的那几条能把卡片撑到
     *  两三个正方形高，一列里只剩它一张，网格看着像断了。多条命令不封顶 —— 它的步骤是逐条列的，
     *  截掉就等于把「有几步」这件事藏了。
     */
    maxHeightRatio: Float = 0f,
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
        val natural = maxOf((tileWidth * minHeightRatio).roundToInt(), contentHeights.max())
        val rowHeight =
            if (maxHeightRatio > 0f) {
                minOf(natural, (tileWidth * maxHeightRatio).roundToInt())
            } else {
                natural
            }
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
            ) != CommandsSave.Failed
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
                val rolled = write(cfg, "删除命令失败，回滚绑定：${cmd.name}") != CommandsSave.Failed
                XLog.w("删除命令：命令库写入失败，已把绑定回滚（回滚成功=$rolled）：${cmd.name}")
            }
        }
    }
}
