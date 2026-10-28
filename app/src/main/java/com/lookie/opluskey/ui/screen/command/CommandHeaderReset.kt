package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「自定义命令」页顶栏右侧那颗「重置 / 取消」—— 原先在底部那条浮动工具条里。
 *
 *  挪上来之后底栏只剩「保存 / 修改」一颗：两个动作分处上下两头，误按「重置」清空表单的概率
 *  比并排两颗低得多（重置是不可撤销的，清完要重打一遍）。
 *
 *  **表单与步骤都空着时置灰**（判据 [CommandDraft.hasContent]），点它一下说清没有可清的东西 ——
 *  不干脆不画那颗：顶栏右侧的按钮时有时无，大标题的可用宽度会跟着一跳一跳。
 *
 *  「重置」这一下做什么在 [resetCommandDraft]（与「已保存的命令」菜单里「再点一次编辑」同一份）。
 */
@Composable
internal fun SettingsPage.CommandHeaderReset() {
    val tick = rememberTick()
    val draft = commandDraft.draft
    //  表单（名称 / 正文）与步骤列表都空着 ⇒ 没有可清的东西
    val canReset = draft.hasContent
    //  文案跟着编辑态走：编一条已有命令时它是「取消」（这一下等于放弃这次修改），
    //  新建时是「重置」（清空重来）—— 与它原先在底栏时的口径一致。
    val label =
        if (draft.editingName != null) I18n.get(Strings.COMMON_CANCEL, "取消") else I18n.get(Strings.COMMON_RESET, "重置")
    //  灰着被点一下时说清为什么（顶栏按钮不吃点击，不给这一句就成了「点了没反应」）
    val blocked = I18n.get(Strings.COMMAND_RESET_EMPTY, "表单是空的，没有可重置的内容")
    DisabledHint(if (canReset) null else blocked) {
        IconButton(
            onClick = {
                tick()
                resetCommandDraft()
            },
            enabled = canReset,
            minWidth = HEADER_ICON_SIZE,
            minHeight = HEADER_ICON_SIZE,
            cornerRadius = HEADER_ICON_SIZE / 2,
            // 与顶栏那颗 ⋮ 同一档右边距（见 `HeaderMenuButton`）
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Icon(
                PageIcons.MenuReset,
                contentDescription = label,
                // 顶栏按钮不给图标上色 ⇒ 禁用态得自己给（与行首图标同一个禁用色）
                tint =
                    if (canReset) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.disabledOnSecondaryVariant
                    },
            )
        }
    }
}
