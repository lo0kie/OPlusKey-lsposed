package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.model.COMMAND_DISCLAIMER_TEXT
import com.lookie.opluskey.ui.model.COMMAND_DISCLAIMER_TITLE
import com.lookie.opluskey.ui.state.CommandListEditState
import com.lookie.opluskey.ui.state.CommandUiState
import com.lookie.opluskey.ui.write
import kotlinx.coroutines.delay

// 「自定义命令」页那五个确认窗 —— 从 `CommandScreen.kt` 拆出来。
//
//  它们共同的特点：**状态不在本页手里也算数**。
//   · 三个的触发点在别处（`goBack` / `loadDraft` 置的位），这里只负责画；
//   · 两个是页内自己的（风险提示、删命令、填前缀）。
//  混在页面骨架里时，「这一句是谁弹的」得往上翻一百行才知道。

/**
 * 这一页所有确认窗的挂载点 —— 一次画五个，各自看自己那一位。
 *
 * @param onLoaded 换命令的确认窗点「放弃」之后要**切到那一条所属的页**（由调用点给：
 *   它就是「点列表里一张卡」那条路用的同一支 —— 滚回顶部再滑进去）。放在这里而不是
 *   调用点里，是因为切页必须等确认：先切再问的话，窗背后已经是另一页了。
 */
@Composable
internal fun SettingsPage.CommandDialogs(
    ui: CommandUiState,
    onLoaded: (Command) -> Unit,
) {
    DisclaimerDialog(ui)
    LeaveDiscardDialog()
    SwitchDraftDiscardDialog(onLoaded)
    DeleteCommandDialog(ui)
    //  这两支的状态在 `CommandListEditState` 里（页面级的编辑态），不读 `ui`
    DeletePickedDialog()
    LeaveListEditDialog()
    FillPrefixDialog(ui)
}

/**
 * 「命令列表」编辑态里删掉勾中的那几条 —— 删了不可撤销，而且**绑到按键上的会改成「无操作」**
 * （见 [removeCommand]），所以先问一句。
 *
 *  一条一条调 [removeCommand]：它自带「三处引用一起清」的账（绑定、别的命令里的引用、单条正文里
 *  的 `cmd:`），逐条走同一条路比自己在这里再写一份批量清理稳。代价是 N 次落盘，命令库本来就只有
 *  个位数条，不值当为它再造一条批量通道。
 */
@Composable
private fun SettingsPage.DeletePickedDialog() {
    val picked = CommandListEditState.picked
    ConfirmDialog(
        show = CommandListEditState.confirmDeletePicked,
        title = I18n.get(Strings.COMMAND_DELETE_PICKED_TITLE, "删除选中的命令"),
        summary = I18n.format(Strings.COMMAND_DELETE_PICKED_BODY, picked.size),
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = { CommandListEditState.confirmDeletePicked = false },
        onConfirm = {
            CommandListEditState.confirmDeletePicked = false
            val targets = configState.commands.value.filter { it.name in picked }
            targets.forEach { removeCommand(it) }
            CommandListEditState.clearPicked()
            CommandListEditState.editing = false
        },
    )
}

/**
 * 编辑态里按返回、且勾着东西时那一问 —— 退出编辑只丢勾选，不删东西，所以动作项不是红的。
 *
 *  与「按键功能」页那一问同一个口径（见 `KeysScreen` 的 `KeysLeaveEditDialog`）：
 *  确认之后**只退出编辑态**，人留在这页。
 */
@Composable
private fun SettingsPage.LeaveListEditDialog() {
    ConfirmDialog(
        show = CommandListEditState.confirmLeaveEdit,
        title = I18n.get(Strings.COMMAND_LEAVE_EDIT_TITLE, "退出编辑"),
        summary = I18n.format(Strings.COMMAND_LEAVE_EDIT_BODY, CommandListEditState.count),
        action = I18n.get(Strings.COMMON_CONFIRM, "确定"),
        positiveDanger = false,
        onDismiss = { CommandListEditState.confirmLeaveEdit = false },
        onConfirm = {
            CommandListEditState.confirmLeaveEdit = false
            CommandListEditState.toggleEditing()
        },
    )
}

/**
 * 首次进这一页那一次风险提示：**不接受就退出去**（`goBack()`），接受则记下这一笔。
 * 与首页那份是两个独立的记录，互不影响。
 *  给红（`positiveDanger`）：这一份说的不是「注入有风险」，而是「你写什么就跑什么」，
 *  比首页那份更该让人停一下。
 */
@Composable
private fun SettingsPage.DisclaimerDialog(ui: CommandUiState) {
    ConfirmDialog(
        show = ui.disclaimerPending,
        title = COMMAND_DISCLAIMER_TITLE,
        summary = COMMAND_DISCLAIMER_TEXT,
        action = I18n.get(Strings.DIALOG_DISCLAIMER_ACKNOWLEDGE, "我已了解"),
        positiveDanger = true,
        onDismiss = {
            XLog.w("自定义命令：没确认风险提示 ⇒ 退出这一页")
            ui.disclaimerPending = false
            goBack()
        },
        onConfirm = {
            XLog.w("自定义命令：已确认风险提示")
            ui.disclaimerPending = false
            acceptCommandDisclaimer()
        },
    )
}

private fun SettingsPage.acceptCommandDisclaimer() {
    write(
        configState.config.value.copy(commandDisclaimerAccepted = true),
        "command_disclaimer_accepted=true",
    )
}

/**
 * 退出这一页时的「放弃未保存的命令 / 场景」（触发点在 `SettingsPage.goBack`，这里只负责画）。
 *  确认 ⇒ 丢掉草稿并**真退**（`popBackStack` 绕开 goBack 的拦截，否则又弹回自己）；
 *  取消 ⇒ 关掉窗，留在页里继续编。
 *
 * @param onDiscard 点「放弃」之后、弹栈之前要做的收尾 —— `null` = 什么都不用做，直接弹栈。
 *
 *   「场景动作」页要传一支：那一页改的是**共享草稿的其中一块**，草稿不在离栈时整份清
 *   （它属于还留在栈里的「系统场景」页）。只弹栈的话，改动仍躺在草稿里，退回上一页后
 *   那颗「确认」照样能把它写下去 —— 真机上就是「点了放弃、选的命令还是生效了」。
 */
@Composable
internal fun SettingsPage.LeaveDiscardDialog(
    /** 标题由调用页给：这一页的草稿里是命令还是条件，只有调用点知道。 */
    title: String = I18n.get(Strings.DIALOG_DISCARD_COMMAND_TITLE, "放弃未保存的命令"),
    onDiscard: (() -> Unit)? = null,
) {
    ConfirmDialog(
        show = overlays.confirmLeaveCommands,
        title = title,
        action = I18n.get(Strings.COMMON_DISCARD, "放弃"),
        onDismiss = { overlays.confirmLeaveCommands = false },
        onConfirm = {
            overlays.confirmLeaveCommands = false
            //  只弹栈：**当前页**那一份草稿由离栈收尾（`discardPageStateOf`）按 leaving 页清。
            //  两页的草稿是两份，这个窗说的「放弃未保存的命令」只指当前页那份 ——
            //  在这里把另一份也 reset 会无提示吞掉另一页的输入。
            //  要「只丢这一趟」的页由 `onDiscard` 自己回滚那一小块（见参数说明）。
            onDiscard?.invoke()
            popBackStack()
        },
    )
}

/**
 * 「换成另一条命令」撞上未保存的改动（触发点在 `SettingsPage.loadDraft`，这里只负责画）。
 *  确认 ⇒ 丢掉当前草稿、载入那一条、**再切到它所属那一页**（顺序不能反：先切后问的话，
 *  窗背后已经换了页）；取消 ⇒ 关掉窗，表单原样留着继续编、页面也不动。
 *
 *  这里直接 `draft.load`（刚确认过，再走 `loadDraft` 会因为它此刻仍是脏的而**再问一遍**）。
 */
@Composable
private fun SettingsPage.SwitchDraftDiscardDialog(onLoaded: (Command) -> Unit) {
    ConfirmDialog(
        show = overlays.pendingDraftLoad != null,
        title = I18n.get(Strings.DIALOG_DISCARD_COMMAND_TITLE, "放弃未保存的命令"),
        summary =
            I18n.format(
                Strings.DIALOG_DISCARD_COMMAND_EDIT_MESSAGE,
                escapeEmphasis(overlays.pendingDraftLoad?.name ?: ""),
            ),
        action = I18n.get(Strings.COMMON_DISCARD, "放弃"),
        onDismiss = { overlays.pendingDraftLoad = null },
        onConfirm = {
            val next = overlays.pendingDraftLoad
            overlays.pendingDraftLoad = null
            next?.let {
                commandDraft.draft.load(it)
                onLoaded(it)
            }
        },
    )
}

/** 删除确认：与「重置配置」同一套控件（取消中性色 + 动作项红）。 */
@Composable
private fun SettingsPage.DeleteCommandDialog(ui: CommandUiState) {
    val target = ui.deleting
    ConfirmDialog(
        show = target != null,
        title = I18n.get(Strings.DIALOG_DELETE_COMMAND_TITLE, "删除命令"),
        // 文案分两种：多条命令不会被别的命令引用（浮层里只能挑单条），只提按键绑定那一条路。
        summary =
            when {
                target == null -> {
                    ""
                }

                target.kind == CommandKind.SERIAL -> {
                    I18n.format(
                        Strings.DIALOG_DELETE_COMMAND_SERIAL_MESSAGE,
                        escapeEmphasis(target.name),
                    )
                }

                else -> {
                    I18n.format(
                        Strings.DIALOG_DELETE_COMMAND_SINGLE_MESSAGE,
                        escapeEmphasis(target.name),
                    )
                }
            },
        action = I18n.get(Strings.COMMON_DELETE, "删除"),
        onDismiss = { ui.deleting = null },
        onConfirm = {
            ui.deleting?.let { removeCommand(it) }
            ui.deleting = null
        },
    )
}

/**
 * 点「支持的命令格式」那一行要**整段替换**输入框（输入框里不带格式、却有别的内容）⇒ 先确认
 *（不是插入，没有撤销）。已经带格式的那种走「只换前缀」，不经过这里（见 `CommandFormats.fillPrefix`）。
 *
 * ## 为什么「聚焦」要等窗收场
 *
 *  字在确认那一下就换掉（用户在窗后面看得见），但**举手让输入框抢焦点**必须挪到
 *  [ConfirmDialog] 的 `onDismissFinished`：这个窗是独立窗口，库在退场动画结束时会把键盘
 * 收掉；退场途中叫起来的键盘，窗一销毁就被平台一起带走 —— 真机表现是「键盘弹一下又没了」。
 *  与「收集按键 → 添加按键」那两个窗交替时同一条教训（见 `FrostedDialog` 的注释）。
 *
 *  只有**真替换过**才举手：点「取消」关掉的窗不该把焦点塞进输入框。
 */
@Composable
private fun SettingsPage.FillPrefixDialog(ui: CommandUiState) {
    val prefix = ui.pendingFormatPrefix
    // 这一下确认里到底换没换 —— 「取消」与「替换」走的是同一个收场回调，得分开
    var replaced by remember { mutableStateOf(false) }
    // 收场回调**不保证来**（`CollectKeyDialog` 那处也为此留了兜底）：到点还没等到就照样举手。
    //  只是把「聚焦」推迟一档，比「键盘压根不起来」好；正常路径 250ms 左右回调就到，不等这一档。
    LaunchedEffect(replaced) {
        if (!replaced) return@LaunchedEffect
        delay(PREFIX_FOCUS_FALLBACK_MS)
        if (replaced) {
            replaced = false
            XLog.w("命令格式：收场回调没来，按兜底时长举手聚焦")
            ui.singleFocusRequest++
        }
    }
    ConfirmDialog(
        show = prefix != null,
        title = I18n.get(Strings.DIALOG_FILL_PREFIX_TITLE, "替换已输入的命令"),
        summary =
            I18n.format(
                Strings.DIALOG_FILL_PREFIX_MESSAGE,
                escapeEmphasis(prefix ?: ""),
            ),
        action = I18n.get(Strings.COMMON_REPLACE, "替换"),
        onDismiss = { ui.pendingFormatPrefix = null },
        onConfirm = {
            ui.pendingFormatPrefix = null
            if (prefix != null) {
                XLog.w("命令格式：已确认整段替换")
                setFormatPrefix(prefix)
                replaced = true
            }
        },
        onDismissFinished = {
            if (replaced) {
                replaced = false
                // 窗彻底没了，这时候抢焦点键盘才留得住
                ui.singleFocusRequest++
            }
        },
    )
}

/**
 * 等「替换」确认窗收场的最长时间（毫秒）。
 *
 *  比库的退场动画（250ms）留一倍余量，与 `CollectKeyDialog` 的 `COLLECT_CLOSE_TIMEOUT_MS`
 *  同一档。只在 `onDismissFinished` 没来时起作用。
 */
private const val PREFIX_FOCUS_FALLBACK_MS = 600L
