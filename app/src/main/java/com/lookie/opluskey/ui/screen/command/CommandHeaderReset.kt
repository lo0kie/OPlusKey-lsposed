package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HeaderIconButton
import com.lookie.opluskey.ui.feedback.DisabledHint
import com.lookie.opluskey.ui.feedback.HintText
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.theme.COLOR_FADE_SPEC
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.command.CommandListEditState
import com.lookie.opluskey.ui.wire.disabledClickReason
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「自定义命令」页顶栏右侧那颗按钮 —— 两个页签各一档：
 *
 *  · **单条 / 多条**（表单页）：**「重置 / 取消」**。原先在底部那条浮动工具条里，挪上来之后底栏只剩
 *    「保存 / 修改」一颗：两个动作分处上下两头，误按「重置」清空表单的概率比并排两颗低得多。
 *    **表单与步骤都空着时置灰**（判据 [CommandDraft.hasContent]），点它一下说清没有可清的东西。
 *  · **命令列表**（这一页）：**「编辑 / 完成」**，与「按键功能」页那颗同一个形状
 *    （见 `KeysHeaderEdit`）—— 列表那一页没有表单可清，原来那颗「重置」在那儿只能是灰的；
 *    现在它管的是这一页自己的编辑态（勾选 + 删除，见 `CommandListEditState`）。
 *
 *  「重置」这一下做什么在 [resetCommandDraft]（只清**当前这一页**那份表单）。
 */
@Composable
internal fun SettingsPage.CommandHeaderReset(
    /** 现在停在「命令列表」那一页吗 —— 那一页没有表单，这颗换成「编辑 / 完成」。 */
    onListPage: Boolean,
) {
    if (onListPage) {
        CommandHeaderEdit()
        return
    }
    val draft = commandDraft.draft
    //  排序态里也没有可清的东西（改的是顺序，不是内容 —— 那一状态下页里别的控件也都禁用）
    val sorting = commandDraft.stepEditing
    val canReset = !sorting && draft.hasContent
    //  文案跟着编辑态走：编一条已有命令时它是「取消」（这一下等于放弃这次修改），
    //  新建时是「重置」（清空重来）—— 与它原先在底栏时的口径一致。
    val label =
        if (draft.editingName != null) I18n.get(Strings.COMMON_CANCEL, "取消") else I18n.get(Strings.COMMON_RESET, "重置")
    //  灰着被点一下时说清为什么（顶栏按钮不吃点击，不给这一句就成了「点了没反应」）
    val blocked =
        when {
            sorting -> I18n.get(Strings.COMMAND_RESET_SORTING, "排序中不能重置")
            else -> I18n.get(Strings.COMMAND_RESET_EMPTY, "表单是空的，没有可重置的内容")
        }
    DisabledHint(if (canReset) null else blocked) {
        HeaderIconButton(
            //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`）
            onClick = { resetCommandDraft() },
            enabled = canReset,
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

/**
 * 「命令列表」页顶栏那颗「编辑 / 完成」—— 与「按键功能」页那颗同一个形状与口径
 * （见 `KeysHeaderEdit`）：按一下进编辑态（卡片上出多选框、右下角那颗换成「删除」），
 * 编辑态里它自己换成「完成」，退出时选中一起清掉。
 *
 *  一条命令都没有、或者整页写不了（总闸关 / 链路没通 / 配置只读）时置灰并说清原因：
 *  编辑态的终点是删命令，写不进去时让人先勾一堆再发现删不掉，比一开始就按不动更费事。
 */
@Composable
private fun SettingsPage.CommandHeaderEdit() {
    val editing = CommandListEditState.editing
    val canEdit = configState.commands.value.isNotEmpty() && pageWritable()
    //  字形随编辑态换（编辑 与 完成）：形状没法插值 ⇒ 交叉淡过去；图标色与它同一条曲线，
    //  免得「色在渐变、字形啪地换掉」。
    val iconTint by animateColorAsState(
        targetValue =
            if (canEdit) {
                COUITheme.colorScheme.onSurfaceContainer
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CommandsEditIcon",
    )
    DisabledHint(
        when {
            canEdit -> {
                null
            }

            configState.commands.value.isEmpty() -> {
                //  空着的两种原因分开说：读不出来时「没有保存的命令」是错的（用户没删过任何东西）。
                HintText(
                    if (configState.commandsReadOnly) {
                        I18n.get(Strings.COMMAND_UNREADABLE, "命令库无法读取（文件损坏或权限不足），已切换为只读")
                    } else {
                        I18n.get(Strings.COMMAND_NO_SAVED_COMMANDS, "没有保存的命令")
                    },
                )
            }

            else -> {
                disabledClickReason()
            }
        },
    ) {
        HeaderIconButton(
            onClick = { CommandListEditState.toggleEditing() },
            enabled = canEdit,
        ) {
            Crossfade(
                targetState = editing,
                animationSpec = COLOR_FADE_SPEC,
                label = "CommandsEditGlyph",
            ) { on ->
                Icon(
                    if (on) PageIcons.Confirm else PageIcons.CommandEdit,
                    contentDescription =
                        if (on) {
                            I18n.get(Strings.COMMON_DONE, "完成")
                        } else {
                            I18n.get(Strings.COMMON_EDIT, "编辑")
                        },
                    tint = iconTint,
                )
            }
        }
    }
}
