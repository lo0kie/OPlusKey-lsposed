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
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.bindsToken
import com.lookie.opluskey.core.model.mapActions
import com.lookie.opluskey.host.action.ActionRunner
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMMAND
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.tickNow
import com.lookie.opluskey.ui.model.page.GRID_GAP
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.state.command.TestRunnerCache
import com.lookie.opluskey.ui.write.saveCommands
import com.lookie.opluskey.ui.write.write
import kotlin.math.roundToInt

internal fun SettingsPage.testCommand(cmd: Command) {
    XLog.w("测试命令：${cmd.name}（${cmd.kind.id}）—— 在设置页进程里直接执行")
    // 与页面其它点击同一条触觉通道（判据只有系统状态）
    tickNow()
    //  **空命令在派发之前就拦掉**：没有正文 / 没有步骤时点「测试」什么都不会发生，
    //  派发下去也只会走到执行器那条失败提示上，界面还要先弹一句「已触发」——
    //  先派发再报错，读起来像「试过了，没反应」。
    if (!cmd.hasContent) {
        XLog.w("测试命令：这条命令是空的，不派发")
        hint(I18n.get(Strings.COMMAND_FAILURE_EMPTY, "这条命令是空的，补上内容再试"), slot = HINT_SLOT_COMMAND)
        return
    }
    //  **格式不支持的同样拦在派发之前**（用户口径：不支持的命令也不允许测试）：那种正文解析出来
    //  是 `none`，执行侧什么都不做 —— 与「空命令」同一个道理，先派发再报错只会读成「试过了、没反应」。
    //  判据与保存那一栏共用 `hasSupportedFormat`（只针对单条：多条的每一步都是从列表里挑的，
    //  天然是支持的格式）。两条入口（命令列表的菜单、表单页的菜单）都走这一支，闸只加在这里。
    if (cmd.kind == CommandKind.SINGLE && !Action.hasSupportedFormat(cmd.single)) {
        XLog.w("测试命令：正文格式不支持，不派发（${cmd.single.take(32)}）")
        hint(I18n.get(Strings.COMMAND_FORMAT_REQUIRED, "必须以支持的格式开头"), slot = HINT_SLOT_COMMAND)
        return
    }
    //  **只写了前缀也不算有内容**（`shell:` 后面空着、或只有空格）：它过了格式闸，
    //  派下去却是一条空命令 —— 与「空命令」同一个症状（点了测试、什么都没发生）。
    if (cmd.kind == CommandKind.SINGLE && !Action.hasBody(cmd.single)) {
        XLog.w("测试命令：只有前缀、没有正文，不派发（${cmd.single.take(32)}）")
        hint(I18n.get(Strings.COMMAND_FAILURE_EMPTY, "这条命令是空的，补上内容再试"), slot = HINT_SLOT_COMMAND)
        return
    }
    // 页面动作失败绝不外抛（本文件里其它入口都是这条纪律）：测试命令直接跑在点击回调上，
    //  从执行器里抛出来会打到 Compose 的点击处理里。
    try {
        testRunner(context).runTest(cmd)
    } catch (e: Throwable) {
        XLog.w("测试命令失败（已忽略）：${cmd.name}", e)
        hint(I18n.get(Strings.TOAST_COMMAND_TEST_FAILED, "测试没触发"), slot = HINT_SLOT_COMMAND)
        return
    }
    //  注意：这里只能保证「**已派发**」，不是「已完成」：单条 `keyevent:` 是
    //  同步执行的、这一刻已经生效；但 `shell:`（起进程后即返回、后台线程等结果）与多条命令
    //  （`postDelayed` 续步）的**完成**晚于这一行 —— 说「已执行」是把派发说成了完成。
    //  命令可能一点可见动静都没有，没有这句分不清「发了」和「没反应」，所以保留提示、改口径
    //  （失败仍会由 `CommandFailure` 那条另弹一句）。
    hint(I18n.get(Strings.TOAST_COMMAND_TRIGGERED, "已触发"), slot = HINT_SLOT_COMMAND)
}

/**
 * 测试命令用的执行器 —— 见 [TestRunnerCache]：本进程只建一份（内部执行器持进程级的
 * Handler、系统服务与手电筒回调注册，每点一次新建会平白多出注册），页面关掉时释放。
 */
private fun testRunner(context: android.content.Context): ActionRunner = TestRunnerCache.acquire(context)

/**
 * 草稿 → 一条**可执行**的 [Command]（不落盘、不查重）—— 给「测试命令」用。
 *
 *  与 `draftToLibrary`（在 `PageText.kt`）的分工：那个回答「存下去之后长什么样」
 *  （要名字、要处理改名后的引用图），这个只回答「眼前这份内容能不能跑」，
 *  所以名字可以空着 —— 新命令写到一半就想试是常事。
 *
 *  住在这一份文件里而不是跟着 `draftToLibrary`：它是「测试」这条路的入口，与 [testCommand] 是一对。
 */
internal fun SettingsPage.draftCommand(): Command {
    val draft = commandDraft.draft
    val name = CommandLibrary.sanitizeName(draft.name)
    return when (draft.kind) {
        CommandKind.SINGLE -> {
            Command(name, CommandKind.SINGLE, single = draft.single.trim(), root = draft.root)
        }

        CommandKind.SERIAL -> {
            Command(
                name = name,
                kind = CommandKind.SERIAL,
                steps = draft.steps.toList(),
                stepDelayMs = draft.stepDelayMs,
            )
        }
    }
}

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
 * 一行命令卡的**布局参数** —— 从签名里挪出来的一格。
 *
 *  这几个值本来就是**一起**定的：卡数与列数由同一个
 *  `chunked(columns)` 与分宽共用，两个上下限由「这一行里有没有多条命令」一起算
 *  （见 `SavedCommands`）。散成四个参数时，调用点读起来像四件互不相干的事。
 *
 * @param tileCount 这一行的卡数（不足 [columns] 时右侧留空）
 * @param columns 这一行按几列分宽度 —— 由调用点用 `gridColumnCount` 算好传进来，
 *   与它 `chunked` 用的是**同一个**数（分块与分宽各算一遍会在宽度卡在临界点时让最后一行错位）。
 * @param minHeightRatio 行高下限 = 卡宽 × 本值（单条给 [SINGLE_CARD_MIN_HEIGHT_RATIO]，多条给 `1f`）。
 * @param maxHeightRatio 行高上限 = 卡宽 × 本值；`0f` 表示不封顶。
 *
 *   现在两条都封在 `1f`（正方形）：一条命令的摘要 / 步骤长短差得很远，长的那几条能把卡片撑到
 *   两三个正方形高，一列里只剩它一张，网格看着像断了。放不下的内容由**卡片自己**收
 *   （单条摘要在 `maxLines` 上封顶、多条把装不下的步骤换成一行「…」，见 `SavedCommands`）——
 *   不靠这一层硬裁：硬裁是从某一行中间断开的，连省略号都没有。
 *   `0f` 这一档仍然留着：封顶是调用点的口径，不是这个布局的能力。
 */
internal class TileRowSpec(
    val tileCount: Int,
    val columns: Int,
    val minHeightRatio: Float,
    val maxHeightRatio: Float = 0f,
)

/**
 * 命令卡片的一行：宽度平分、同行等高、且不矮于正方形。
 *
 * `Row` 做不到（`weight` 只分宽度、`IntrinsicSize.Min` 量不出正方形下限），所以自己量
 * 两趟：先按宽度平分量各卡自然高度，行高取「最高那张」与卡宽的较大者，再按行高重量。
 *
 * 布局参数收在 [TileRowSpec] 里（理由见那边）。
 */
@Composable
internal fun CommandTileRow(
    spec: TileRowSpec,
    modifier: Modifier = Modifier,
    tile: @Composable (Int) -> Unit,
) {
    Layout(
        content = { repeat(spec.tileCount) { index -> tile(index) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val columns = spec.columns
        val gapPx = GRID_GAP.roundToPx()
        // 宽度平分：可用宽 − (列数−1) 个列距，除以列数 —— 与这行有几张卡无关。
        val tileWidth = (constraints.maxWidth - gapPx * (columns - 1)) / columns
        // 各卡自然高度用固有测量（`minIntrinsicHeight`）拿，不能 `measure()` 两次 —— 真机 R8 包会
        // `IllegalStateException` 崩掉宿主进程；固有测量不占那次唯一的 measure 配额。
        val contentHeights = measurables.map { it.minIntrinsicHeight(tileWidth) }
        val natural = maxOf((tileWidth * spec.minHeightRatio).roundToInt(), contentHeights.max())
        val rowHeight =
            if (spec.maxHeightRatio > 0f) {
                minOf(natural, (tileWidth * spec.maxHeightRatio).roundToInt())
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
 * 删一条命令 —— 绑定上那一处要清，否则留下的是指向空处的东西：
 *  绑到手势上的 `cmd:<名字>` → 改成「无操作」（不然按下去永远解析不到）。
 *
 *  **别的命令里那一步引用不在这里清**：多条命令的步骤可以引用单条（见
 *  `StepSheetFilter.singleCommandsOnly`），删掉被引用的那条之后，那一步会在展开时找不到目标、
 * 跳过并报 `NOT_FOUND`（`CustomCommandRunner.resolveStep`）。清它得再扫一遍整个命令库并整份重写，
 * 而结果只是把「跳过 + 一句提示」换成「这一步凭空少掉」—— 后者更难看出发生过什么。
 */
internal fun SettingsPage.removeCommand(cmd: Command) {
    val cfg = configState.config.value
    if (!clearCommandBindings(cfg, cmd)) return

    // 删的就是正在编辑的那条 ⇒ 草稿一并清掉：留着的话「修改」按钮还挂着，
    // 点一下会把刚删的命令原样写回去。注意：这一步挪到**命令库真删掉之后**（见下）：
    // 库没删成时草稿要留着，否则用户正在编的那条被清空、而库里那条还在。
    val next = configState.commands.value.filterNot { it.name == cmd.name }

    when (saveCommands(next, "删除命令：${cmd.name}")) {
        CommandsSave.Ok -> {
            clearDeletedCommandFromDrafts(cmd)
            hint(I18n.get(Strings.TOAST_COMMAND_DELETED, "已删除 [%s]").format(cmd.name), slot = HINT_SLOT_COMMAND)
        }

        // 库已经删掉了（只是没通知到注入侧，提示由 saveCommands 给）——草稿该清还是清，
        //  但不再报「已删除」：那句与失败提示叠在一起，读起来像发生了两件事。
        CommandsSave.BroadcastFailed -> {
            clearDeletedCommandFromDrafts(cmd)
        }

        CommandsSave.Failed -> {
            if (cfg.bindsToken(cmd.token)) {
                // 注意：命令库没删成、绑定却已经清了 ⇒ 会留下「手势=无操作 + 命令仍在库」这种半份状态
                // ：把绑定**写回原样**，两边一起保持原状。
                //  失败原因 [saveCommands] 已经报过（「保存失败」），这里只补回滚与它的判据行。
                val rolled = write(cfg, "删除命令失败，回滚绑定：${cmd.name}") != CommandsSave.Failed
                XLog.w("删除命令：命令库写入失败，已把绑定回滚（回滚成功=$rolled）：${cmd.name}")
            }
        }
    }
}

/**
 * 删掉 [cmd] 之后两份表单（单条 / 多条）怎么收尾 —— **判据只有一条：这一份是不是在编它**。
 *
 *  · 在编 ⇒ 整份清掉（连 `editingName` 一起）：留着的话那一页的「修改」还挂着，点一下就把
 *    刚删掉的命令原样写回库；
 *  · 不在编 ⇒ 什么都不用动：草稿里没有指向别的命令的东西（多条步骤里的引用按 token 存着、
 *    由执行侧展开，不在这份草稿的编辑态里）。
 *
 *  **两页都要走**：删命令发生在列表那一页，只清当前 tab 的话，另一页仍留着同名的编辑态 ——
 *  切回去点「修改」还是会把刚删掉的命令写回库。判据写在这里一份就够，不按 tab 各写一遍。
 */
private fun SettingsPage.clearDeletedCommandFromDrafts(cmd: Command) {
    val draft = commandDraft.draft
    for (form in listOf(draft.singleForm, draft.serialForm)) {
        if (form.editingName == cmd.name) {
            form.reset()
            continue
        }
        if (Action.parse(form.single).token == cmd.token) form.single = Action.None.token
        val left = form.steps.filterNot { it == cmd.token }
        if (left.size != form.steps.size) {
            form.steps.clear()
            form.steps.addAll(left)
        }
    }
}

/**
 * 删命令前把**每一颗键**上的 `cmd:` 引用清掉，并摘掉「常用命令」里指向它的那几条。
 *
 *  自定义键同样能绑 `cmd:`，只清侧键的话那些键按下去永远 NOT_FOUND（与改名那条路同一套口径，
 *  见 `PageText.saveCommand`）。
 *
 *  **常用命令要反向删**（用户口径）：那张清单存的是一条条指向动作的快捷方式，源命令被删之后
 *  留下的条目只剩一个念不出名字的 token —— 用户既看不懂、也删不掉（除非还记得它原来指向什么）。
 *  与「移除常用命令」同一个口径：只摘清单，不动别人已经绑好的那一处。
 *
 * @return false = 绑定没清成（配置只读 / 写盘失败），调用方**不能**继续删命令
 */
private fun SettingsPage.clearCommandBindings(
    cfg: Config,
    cmd: Command,
): Boolean {
    // 五处动作位（三格手势 / 条件规则 / 组合键两档）由 `mapActions` 一次走完 —— 这里只回答
    //  「这一处是不是它」。早先逐处手写 `copy(...)` 时，组合键那两档就是这样漏掉的
    //  （组合按下拿不到命令、只报 NOT_FOUND），所以遍历只留那一处。
    //
    //  规则里的动作同样只换成「无操作」、不摘整条规则：摘掉会让用户的判断条件一起消失。
    val mapped = cfg.mapActions { if (it.token == cmd.token) Action.None else it }
    //  **常用命令里指向这一条的条目一并摘掉**（用户口径）：那是一条快捷方式，源命令没了它只剩
    //  一个念不出名字的 token。与「移除常用命令」同一个口径 —— 只摘清单、不动别人的绑定。
    //  挂在同一笔写入里：分两次写就会出现「清单还指着一条已经不存在的命令」的中间态。
    val stale = mapped.global.commonCommands.filter { it.token == cmd.token }
    val next =
        if (stale.isEmpty()) {
            mapped
        } else {
            mapped.copy(
                global =
                    mapped.global.copy(
                        commonCommands = mapped.global.commonCommands.filterNot { it.token == cmd.token },
                    ),
            )
        }
    if (next == cfg) return true
    // 只读态下这次写会被拒（返回值不能丢）：命令库那份照删，配置里就留下悬空的 `cmd:`。
    val ok = write(next, "删除命令时清掉绑定：${cmd.name}") != CommandsSave.Failed
    if (!ok) XLog.w("删除命令中止：绑定没能清掉（配置只读），命令库保持原样：${cmd.name}")
    if (ok && stale.isNotEmpty()) {
        XLog.w("删除命令：顺带摘掉 ${stale.size} 条指向它的常用命令（${cmd.token}）")
    }
    return ok
}
