package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HeaderIconButton
import com.lookie.opluskey.ui.feedback.DisabledHint
import com.lookie.opluskey.ui.feedback.HintText
import com.lookie.opluskey.ui.model.icon.FluidIconRow
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.command.CommandListEditState
import com.lookie.opluskey.ui.state.page.CommandForm
import com.lookie.opluskey.ui.style.AnimatedColumn
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
        if (draft.editingName != null) I18n.get(Strings.COMMON_CANCEL) else I18n.get(Strings.COMMON_RESET)
    //  灰着被点一下时说清为什么（顶栏按钮不吃点击，不给这一句就成了「点了没反应」）
    val blocked =
        when {
            sorting -> I18n.get(Strings.COMMAND_RESET_SORTING)
            else -> I18n.get(Strings.COMMAND_RESET_EMPTY)
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
    //  字形随编辑态换（编辑 与 完成）：同位置替换**不做淡入淡出**（用户口径），直接换这颗字形；
    //  颜色仍是状态驱动的过渡（`animateColorAsState`），字形与底色同一次到位。
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
                        I18n.get(Strings.COMMAND_UNREADABLE)
                    } else {
                        I18n.get(Strings.COMMAND_NO_SAVED_COMMANDS)
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
            Icon(
                if (editing) PageIcons.Confirm else PageIcons.CommandEdit,
                contentDescription =
                    if (editing) {
                        I18n.get(Strings.COMMON_DONE)
                    } else {
                        I18n.get(Strings.COMMON_EDIT)
                    },
                tint = iconTint,
            )
        }
    }
}

/**
 * 单条命令的「图标」那一行 —— 只在正文是 `fluid:` 时出现。
 *
 *  点开是网格选择器（`FluidIconPicker`）：模块在用的那批 Tabler 字形全在里面，点一颗就把它
 *  渲染成 png 存进宿主的共享目录，同时把命令改成 `fluid:<id>|正文`。
 *  命令里只记 id，执行时按 id 找文件（`FluidCard.nameOf`），所以换图标只重渲染点中的那一颗。
 *
 *  行本体与「常用命令」那一段是**同一份**（`ui/model/icon/FluidIconRow`）：形状、副标题、
 *  选择器都只有一处实现，这里只负责「写回正文」，那里负责「写回清单」。
 *
 *  显隐走**统一那一档**（`AnimatedColumn` = 淡入 + 纵向展开，顶边缘推开）：正文从 `fluid:`
 *  改成别的（或反过来）时这一档不该「啪」地出现 / 消失 —— 它夹在「可用变量」与「命令设置」
 *  之间，硬切会把下面那一大块整体顶一下。
 */
@Composable
internal fun SettingsPage.CommandFluidIconCard(
    form: CommandForm,
    enabled: Boolean,
) {
    //  只认前缀，不认 `parse` 的结果：正文写成 `fluid:` 就该有这一行 —— 哪怕后面还没写字、
    //  或者写了什么让 `parse` 判成别的，也不该让这一档忽隐忽现。
    val text = form.single.trim()
    val isFluid = text.startsWith(Action.FLUID_PREFIX)
    val fluid = remember(text) { Action.parse(text) as? Action.Fluid }
    val message = fluid?.message ?: text.removePrefix(Action.FLUID_PREFIX).trim()
    //  小标题与卡片一起进退（`AnimatedColumn` 里面自带 `Column`，两行不会叠在一处）
    AnimatedColumn(visible = isFluid) {
        SectionTitle(I18n.get(Strings.COMMAND_FLUID_ICON_TITLE))
        FluidIconRow(
            //  显隐已由外面那一档管着，这一行只在本档在场时才组合 ⇒ 恒为显示
            show = true,
            enabled = enabled,
            //  只换图标那一段，正文一个字不动
            onPick = { id -> form.single = Action.Fluid(message, id).token },
        )
    }
}

// 「条件执行」页顶栏右上角那颗「添加」—— 与 `PageTopBar` 的 `PageActions` 那几颗同一位置、同一尺寸。

/**
 * 顶栏右上角那颗「添加」—— **新增一整块场景**的入口（[addScene]）。
 *
 *  它加的是**一块空场景**（条件和动作都空着），落在表尾并播自己的进场；表里**只要还有一块
 *  没选条件就不往下加**，就地给一句原因（见 [addScene]）—— 两个空块并排放着，谁也说不清
 *  该填哪个。
 *
 * ## 加到上限之后
 *
 *  一张表最多 [SceneRule.MAX_SCENES] 块（上限是**这一份表**的，不是整页的：换一颗键又是另一张表）。
 *  到顶时这颗按钮**置灰**，点一下由 [DisabledHint] 那条通道说明原因 —— 只灰不解释的话，
 *  「点不动」和「坏了」分不开，而用户多半还想再挤一块进来。
 *
 *  置灰只管手感，真正的上限判断在 [addScene] 里。
 *
 * ## 为什么从右下角的 FAB 挪到顶栏
 *
 *  「添加」是**往这张表里加一行**，不是这一页的「主操作」——主操作是右下角那颗「确认」。
 *  两颗同色实心圆叠在角落时，它们的权重看起来一样，而这一页真正要人按的只有后者
 *  （用户口径：「去掉条件执行里的添加 FAB，改成页面右上角添加按钮」）。
 *  挪到顶栏之后：角落只剩一颗确认，加法留在「这一页有什么」那一层的工具位置上，
 *  与其它页「右上角进下一步」的位置也一致。
 *  每一块场景卡末尾另有一行「添加条件」—— 它往**那一块**里再加一条条件，与这颗加整块
 *  不是同一个入口，别把两者混为一谈。
 */
@Composable
internal fun SettingsPage.BranchHeaderAdd() {
    val scenes = branchDraftNow().scenes
    val atLimit = scenes.size >= SceneRule.MAX_SCENES
    //  **整页闸门也要盖到这颗**（主开关 / 链路 / 只读）：只盖行的话，只读态下还能插一块空场景，
    //  而空条件让 `isReady` 为假 —— 草稿既退不出也存不下。原因与行上那颗同一句。
    val blocked = branchWritableHint()
    DisabledHint(
        //  到顶与「整页改不动」是两件事，各说各的；都没事时 `null` = 这一块没有自己的说法。
        //  （不许拿页面那句「整页为什么改不了」去顶替到顶那一句 —— 那一句说的是另一件事。）
        text =
            when {
                blocked != null -> HintText(blocked)
                atLimit -> HintText(I18n.format(Strings.COMMAND_BRANCH_LIMIT, SceneRule.MAX_SCENES))
                else -> null
            },
    ) {
        //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`），这里不再自己振
        HeaderIconButton(
            enabled = blocked == null && !atLimit,
            onClick = { addScene() },
        ) {
            Icon(
                PageIcons.StepAdd,
                contentDescription = I18n.get(Strings.COMMAND_BRANCH_ADD),
            )
        }
    }
}
