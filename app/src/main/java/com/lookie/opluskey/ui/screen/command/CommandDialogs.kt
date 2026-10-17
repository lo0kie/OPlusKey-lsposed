package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.acceptCommandDisclaimer
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.model.COMMAND_DISCLAIMER_TEXT
import com.lookie.opluskey.ui.model.COMMAND_DISCLAIMER_TITLE
import com.lookie.opluskey.ui.state.CommandUiState

// 「自定义命令」页那五个确认窗 —— 从 `CommandScreen.kt` 拆出来。
//
//  它们共同的特点：**状态不在本页手里也算数**。
//   · 三个的触发点在别处（`goBack` / `loadDraft` 置的位），这里只负责画；
//   · 两个是页内自己的（风险提示、删命令、填前缀）。
//  混在页面骨架里时，「这一句是谁弹的」得往上翻一百行才知道。

/**
 * 这一页所有确认窗的挂载点 —— 一次画五个，各自看自己那一位。
 *
 * @param onScrollToCurrentPage 「填前缀」确认之后滚回顶部。用**当前页**而不是某一次进页时的页序：
 *   确认窗弹着的这段时间里用户滑到另一栏去了，要滚的是他现在看的这一栏。
 */
@Composable
internal fun SettingsPage.CommandDialogs(
    ui: CommandUiState,
    onScrollToCurrentPage: () -> Unit,
) {
    DisclaimerDialog(ui)
    LeaveDiscardDialog()
    SwitchDraftDiscardDialog()
    DeleteCommandDialog(ui)
    FillPrefixDialog(ui, onScrollToCurrentPage)
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

/**
 * 退出这一页时的「放弃未保存的命令」（触发点在 `SettingsPage.goBack`，这里只负责画）。
 *  确认 ⇒ 丢掉草稿并**真退**（`popBackStack` 绕开 goBack 的拦截，否则又弹回自己）；
 *  取消 ⇒ 关掉窗，留在页里继续编。
 */
@Composable
private fun SettingsPage.LeaveDiscardDialog() {
    ConfirmDialog(
        show = overlays.confirmLeaveCommands,
        title = I18n.get(Strings.DIALOG_DISCARD_COMMAND_TITLE, "放弃未保存的命令"),
        action = I18n.get(Strings.COMMON_DISCARD, "放弃"),
        onDismiss = { overlays.confirmLeaveCommands = false },
        onConfirm = {
            overlays.confirmLeaveCommands = false
            commandDraft.draft.reset()
            popBackStack()
        },
    )
}

/**
 * 「换成另一条命令」撞上未保存的改动（触发点在 `SettingsPage.loadDraft`，这里只负责画）。
 *  确认 ⇒ 丢掉当前草稿、载入那一条（这里直接 `draft.load`：刚确认过，再走 `loadDraft`
 *  会因为它此刻仍是脏的而**再问一遍**）；取消 ⇒ 关掉窗，表单原样留着继续编。
 */
@Composable
private fun SettingsPage.SwitchDraftDiscardDialog() {
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
            next?.let { commandDraft.draft.load(it) }
        },
    )
}

/** 删除确认：与「清空配置」同一套控件（取消中性色 + 动作项红）。 */
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

/** 点「支持的命令格式」那一行要整段替换输入框 ⇒ 里面有东西时先确认（不是插入，没有撤销）。 */
@Composable
private fun SettingsPage.FillPrefixDialog(
    ui: CommandUiState,
    onScrollToCurrentPage: () -> Unit,
) {
    val prefix = ui.pendingFormatPrefix
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
                XLog.w("命令格式：已确认整段替换，把前缀 $prefix 填进「命令」输入框")
                commandDraft.draft.single = prefix
                onScrollToCurrentPage()
            }
        },
    )
}
