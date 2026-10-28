package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.getValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage

/**
 * 载荷**还差什么**（`null` = 够了）—— 「保存」能不能点、灰着时说什么，判据只有这一处。
 *
 *  单条那一档三句话各自对应一种真实的半成品：没写 / 没写对格式 / 写了前缀没写正文。最后那档
 *  （`shell:` 这种只有前缀的）没有一句在说它 —— 保存按钮灰着、点一下只回一句
 *  「这一项现在不可用」，说不出是哪一种。多条那一档只差「有没有步骤」。
 *
 *  **这一支必须能在填好之后返回 `null`**：`saveBlockedReason()` 拿它当唯一判据，
 *  永远非空就等于「保存」按钮永远点不动。
 */
private fun SettingsPage.payloadBlockedReason(): String? =
    when (commandDraft.draft.kind) {
        CommandKind.SINGLE -> {
            singlePayloadBlockedReason()
        }

        CommandKind.SERIAL -> {
            if (commandDraft.draft.steps.isEmpty()) I18n.get(Strings.COMMAND_NEED_STEP, "还没添加步骤") else null
        }
    }

/** 单条那一档的载荷判据（[payloadBlockedReason] 的一半，判据仍只有那一处）。 */
private fun SettingsPage.singlePayloadBlockedReason(): String? {
    val raw = commandDraft.draft.single
    if (raw.isBlank()) return I18n.get(Strings.COMMAND_CMD_HINT, "请输入命令")
    if (!Action.hasSupportedFormat(raw)) {
        return I18n.get(Strings.COMMAND_FORMAT_REQUIRED, "开头要写 shell: / cmd: 这类前缀")
    }
    // 写法合法之后，再看这段字**解析出来到底做不做事**：`shell:` / `cmd:` 这种只有前缀的
    //  （[Action.isBarePrefix]），以及 `keyevent:` 空正文 / 认不出的键码 / `none`（解析成
    //  [Action.None]）——存下去都是一条注定什么都不做的命令。
    val action = Action.parse(raw)
    return if (action.isBarePrefix || action == Action.None) {
        I18n.get(Strings.COMMAND_PAYLOAD_INCOMPLETE, "命令内容还不完整")
    } else {
        // 这里**必须**能返回 `null`：它是「能不能存」的唯一判据，一直非空就等于保存按钮永远灰着。
        null
    }
}

/**
 * 名字那一格的红字 —— 与「保存被禁用时那句话」共用这一支（同一件事只说一处）。
 *
 *  两条判据本身在 `CommandLibrary`（重名扫一遍命令库、`cmd:` 成环走引用图），这里只负责措辞。
 */
internal fun SettingsPage.commandNameProblem(): String? =
    when {
        commandNameDuplicated(commandDraft.draft.name, commandDraft.draft.editingName) -> {
            I18n.get(Strings.COMMAND_NAME_DUPLICATE, "与已有命令重名，请换个名字")
        }

        else -> {
            commandReferenceCycle()?.let { I18n.format(Strings.COMMAND_CYCLE_ERROR, it.joinToString(" → ")) }
        }
    }

/**
 * 「保存」此刻**为什么**点不动（`null` = 能点）—— 保存按钮的 `enabled` 就是「它是不是 `null`」。
 *
 *  灰着被点一下时说的也是这一句（见 [ToolbarButtons] 那层 `DisabledHint`）：兜底的
 *  「这一项现在不可用」对「写了 `shell:`、正文还空着」的人等于什么都没说。
 */
internal fun SettingsPage.saveBlockedReason(): String? {
    if (commandDraft.draft.name.isBlank()) return I18n.get(Strings.COMMAND_NAME_HINT, "请输入名称")
    payloadBlockedReason()?.let { return it }
    return commandNameProblem()
}

/**
 * 「重置」这一下做的事 —— 顶栏那颗「重置 / 取消」调它。
 *
 *  **只清当前这一页那份表单**：两页各自独立（见 `CommandForm`），在「单条」页点重置不该把
 *  「多条」页写了一半的步骤一起清掉 —— 那时候另一页的内容根本不在屏幕上。
 *
 *  清的是 [CommandDraft.form]，步骤行不会「瞬时消失」—— `steps` 一变，
 *  `StepRowDisplay.sync` 把对不上的行标成退场，各自 shrink 完才摘除（见那个类的注释）。
 *
 *  不要再引入「先置挂起标记、等退场播完再由页面级协程收尾」那一套：那套的收尾协程一旦没接上
 *  （或那段时间里用户又敲了字），`reset()` 就永远不执行 —— 表现正是「点了重置、表单还在」。
 */
internal fun SettingsPage.resetCommandDraft() {
    commandDraft.draft.form.reset()
}

/**
 * 「这一页现在有没有值得保存的东西」—— 右下角那颗 FAB 出不出现看它，顶栏那颗「重置」也看它
 * （同一个判据，不会出现「FAB 在、重置不在」这种半套）。
 *
 *  排序态（`stepEditing`）下 FAB 仍在，只是换了文案（「完成」，见 `CommandFloatingButton`）：
 *  那时改的是步骤顺序、不是命令内容，保存没有意义，出路只有那一颗。顶栏那颗「重置」不画。
 */
internal fun SettingsPage.commandDraftHasContent(): Boolean =
    !commandDraft.stepEditing &&
        (commandDraft.draft.editingName != null || commandDraft.draft.name.isNotBlank() || payloadBlockedReason() == null)
