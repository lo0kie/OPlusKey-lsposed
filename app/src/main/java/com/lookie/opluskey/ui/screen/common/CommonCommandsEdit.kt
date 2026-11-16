package com.lookie.opluskey.ui.screen.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HEADER_ACTION_GAP
import com.lookie.opluskey.ui.chrome.HeaderIconButton
import com.lookie.opluskey.ui.chrome.PageCornerFab
import com.lookie.opluskey.ui.chrome.PageFabButton
import com.lookie.opluskey.ui.chrome.PageFabIcon
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.feedback.DisabledHint
import com.lookie.opluskey.ui.feedback.HintText
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.state.common.CommonCommandsEditState
import com.lookie.opluskey.ui.wire.disabledClickReason
import com.lookie.opluskey.ui.write.write
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「常用命令」页的编辑态那一层 —— 顶栏那颗「编辑 / 完成」、右下角那两颗、以及两个确认窗。
//
//  与「按键功能」页同一套（`KeysHeaderEdit` / `KeysEditFabs`）：进编辑态之后点一行是**勾选**，
//  勾了东西才从右下角滑进那两颗 —— 上面「固定 / 取消固定」、下面「删除」。
//  编辑态本身在 `CommonCommandsEditState`（页面级：顶栏与清单分处两个组合）。

/**
 * 顶栏右上角那颗「编辑 / 完成」—— 与「按键功能」「命令列表」那两颗同一个形状与口径。
 *
 *  按一下进这一页的**编辑态**（每一行行尾换成勾选框，勾了东西就从右下角滑进那两颗），
 *  编辑态里它自己换成「完成」（一颗按钮两档，退出编辑不用去别处找），退出时选中一起清掉。
 *
 *  一条都没有、或者整页写不了（总闸关 / 链路没通 / 配置只读）时置灰并说清原因：编辑态的终点
 *  是删配置，写不进去时让人先勾一堆再发现删不掉，比一开始就按不动更费事。
 */
@Composable
internal fun SettingsPage.CommonCommandsHeaderEdit() {
    val editing = CommonCommandsEditState.editing
    val empty =
        configState.config.value.global.commonCommands
            .isEmpty()
    val canEdit = !empty && pageWritable()
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
        label = "CommonCommandsEditIcon",
    )
    //  **编辑态里它左边还多一颗「全选」**：一列清单要逐条勾太费事，而「整批固定 / 整批删」
    //  正是这一页最常见的两种用法（与「按键功能」页那颗同一个样式、同一个位置）。
    if (editing) CommonCommandsHeaderSelectAll(canEdit)

    DisabledHint(
        when {
            canEdit -> {
                null
            }

            //  空着是这一页自己的事实（清单里一条都没有），不是整页那条（讲的是链路 / 只读）
            empty -> {
                HintText(I18n.get(Strings.COMMON_COMMANDS_EMPTY))
            }

            else -> {
                disabledClickReason()
            }
        },
    ) {
        HeaderIconButton(
            //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`）
            onClick = { CommonCommandsEditState.toggleEditing() },
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
 * 编辑态右下角那两颗 —— 上面「固定 / 取消固定」、下面「删除」。
 *
 *  与「按键功能」页那两颗同一份规格（`KeysEditFabs`）：删除是破坏性的，压在离手更远的那一格
 *  （拇指扫过时先碰到的是上面那颗）；两颗各自一个色号，不靠字形区分。
 *
 *  显隐判据在调用点（编辑态 + 勾选不为空）；它自己只负责画。
 *
 * @param unpinning 这一下是「取消固定」还是「固定」（见 [pickedAnyPinned]）
 */
@Composable
internal fun BoxScope.CommonCommandsEditFabs(
    visible: Boolean,
    unpinning: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    PageCornerFab(visible = visible) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(COMMANDS_FAB_GAP),
        ) {
            PageFabButton(
                onClick = onToggle,
                //  主题色：这一颗是「能按」的那一档，与「确认」那一族同一个色号
                containerColor = COUITheme.colorScheme.primary,
            ) {
                //  **图标跟着这一下要做什么**：固定用 `pinned`、取消固定用 `pinned-off`（用户口径）。
                //  行尾那个「已固定」标记仍是 `PinnedInBinding`（`pin`）：那是状态，不是按钮。
                PageFabIcon(
                    if (unpinning) PageIcons.PinnedOff else PageIcons.Pinned,
                    contentDescription =
                        if (unpinning) {
                            I18n.get(Strings.COMMON_COMMANDS_UNPIN)
                        } else {
                            I18n.get(Strings.COMMON_COMMANDS_PINNED)
                        },
                )
            }
            PageFabButton(
                onClick = onDelete,
                //  危险色：删除不可撤销，与上面那颗主题色分开 —— 拇指扫过来先看到的颜色是那道保险
                containerColor = COUITheme.colorScheme.error,
            ) {
                PageFabIcon(
                    PageIcons.CommandDelete,
                    contentDescription = I18n.get(Strings.COMMON_DELETE),
                    //  `onError` 而不是写死白色：Monet 深色档的 `error` 是一档浅红，配白字会糊
                    tint = COUITheme.colorScheme.onError,
                )
            }
        }
    }
}

/** 两颗 FAB 之间那一档间距 —— 12dp（与「按键功能」页那两颗同一档）。 */
private val COMMANDS_FAB_GAP = 12.dp

/**
 * 勾中的那几条是不是**全都固定着**（`showInBinding`）。
 *
 *  这一条同时决定两件事：那颗 FAB 显示「固定」还是「取消固定」、以及按下去之后全部置成哪一档。
 *  收成一处是因为两边各算一次就会出现「图标写着取消固定、按下去却全固定了」这种自相矛盾。
 *
 *  **判「全都」而不是「还有没有」**（用户口径）：勾里混杂了固定与未固定时，这一下按**固定**走
 *  —— 只有一条都没固定、或者混杂，结果都是「全部固定」；只有整批本来就固定着，才轮到取消固定。
 *  这样连按两下必定来回翻转，而「取反一次还剩一半固定」那种像什么都没发生的结果不会出现。
 */
internal fun pickedAllPinned(cfg: Config): Boolean {
    val pickedRows = cfg.global.commonCommands.filter { it.token in CommonCommandsEditState.picked }
    return pickedRows.isNotEmpty() && pickedRows.all { it.showInBinding }
}

/**
 * 把勾中的那几条**统一**改成固定 / 取消固定 —— 右下角那颗图钉按钮调的。
 *
 *  **不是逐条取反**：勾了「一固定一未固定」两条，取反之后还是一固定一未固定 —— 按下去像什么都
 *  没发生。判据是 [pickedAllPinned]：全都固定着才整批取消固定，否则（含混杂）一律固定
 *  （与「按键功能」页那颗「停用 / 启用」同一条口径）。
 *
 *  **一次落盘**：勾了几条就写一遍。写成功之后退出编辑态 —— 这一趟的活干完了，留着一排勾没有下一步。
 *
 * @return `true` = 已落盘
 */
internal fun SettingsPage.togglePickedPinned(): Boolean {
    val picked = CommonCommandsEditState.picked
    if (picked.isEmpty()) return false
    val cfg = configState.config.value
    val pin = !pickedAllPinned(cfg)
    val next = cfg.global.commonCommands.map { if (it.token in picked) it.copy(showInBinding = pin) else it }
    if (write(cfg.copy(global = cfg.global.copy(commonCommands = next)), "common_commands=pin") == CommandsSave.Failed) {
        return false
    }
    //  **勾一条时说「已固定」，勾多条才报个数**：`已固定 1 项` 是句报数的话，而这一下就动了
    //  一条 —— 与行菜单里那一支（`onToggleShow`）同一个说法，两处对得上。
    hint(
        if (picked.size == 1) {
            I18n.get(if (pin) Strings.COMMON_COMMANDS_PINNED_DONE else Strings.COMMON_COMMANDS_UNPINNED_DONE)
        } else {
            I18n.format(
                if (pin) Strings.COMMON_COMMANDS_PINNED_N else Strings.COMMON_COMMANDS_UNPINNED_N,
                picked.size,
            )
        },
    )
    CommonCommandsEditState.toggleEditing()
    return true
}

/**
 * 批量删除的确认窗 —— 勾了几条就报几条，删了不可撤销。
 *
 *  与单条那个（`removeDialog`）分开：那一个只说要紧的那一句（不删源命令），这一条先报个数
 *  （勾十条时把十个名字铺满一屏没有意义），第二句才说「不会删除源命令」。
 *  真删仍不在这一层：确认之后交给调用点先播退场、再落盘。
 *
 *  **正文不再借「按键功能」页那一句**（`keys.delete_confirm`）：那页删的是按键本身，这里删的只是
 *  一张快捷方式清单，同一句话在两处意思不同 —— 借来用会漏掉「源命令还在」这半句（用户报的）。
 */
@Composable
internal fun SettingsPage.CommonCommandsDeleteConfirm(
    show: Boolean,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        show = show,
        title = I18n.get(Strings.COMMON_COMMANDS_REMOVE_TITLE),
        summary = I18n.format(Strings.COMMON_COMMANDS_DELETE_PICKED_BODY, count),
        action = I18n.get(Strings.COMMON_COMMANDS_REMOVE),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 编辑态里按返回那一问 —— 「退出编辑？」。
 *
 *  置位在 `SettingsPage.goBack`（它同时**拦住了这一退**），窗画在这里：判据留在页面那一层，
 *  长什么样归这一页。确认之后**只退出编辑态**、人留在这页 —— 这一退本来就不该把人带走。
 *
 *  不危险动作：退出编辑只是丢掉勾选，不动配置，所以动作项**不是**红的那一档。
 *  文案与「按键功能」页那一问共用（同一句话，同一件事）。
 */
@Composable
internal fun SettingsPage.CommonCommandsLeaveEditConfirm() {
    ConfirmDialog(
        show = CommonCommandsEditState.confirmLeaveEdit,
        title = I18n.get(Strings.KEYS_LEAVE_EDIT_TITLE),
        //  正文报个数：这一问只在勾了东西时出现，写清丢的是哪几项，比一句「会被清空」有用
        summary = I18n.format(Strings.KEYS_LEAVE_EDIT_DESC, CommonCommandsEditState.count),
        action = I18n.get(Strings.KEYS_LEAVE_EDIT_OK),
        positiveDanger = false,
        onDismiss = { CommonCommandsEditState.confirmLeaveEdit = false },
        onConfirm = {
            CommonCommandsEditState.confirmLeaveEdit = false
            CommonCommandsEditState.resetForLeave()
        },
    )
}

/**
 * 编辑态里那颗「全选」—— 按一下把清单**一次勾满**，已经勾满时再按一下**收回**。
 *
 *  与「按键功能」页那颗（`KeysHeaderSelectAll`）同一个样式：同一颗 `PageIcons.SelectAll`、
 *  同一档间距、勾满那一档走强调色 —— 那一页管两张清单，这一页只有一张，判据因此更简单。
 *
 *  「全勾上了没有」由 [CommonCommandsEditState.toggleAll] 现算，这一层只管画哪一档文案：
 *  收回那一档如果还写「全选」，用户会以为按了没反应。
 */
@Composable
private fun SettingsPage.CommonCommandsHeaderSelectAll(canEdit: Boolean) {
    val all =
        configState.config.value.global.commonCommands
            .map { it.token }
            .toSet()
    val every = all.isNotEmpty() && CommonCommandsEditState.picked == all
    //  勾满那一档用**强调色**（与「按键功能」页那颗同一套）：只看图标分不出两档，
    //  文案在无障碍里，眼睛看不到。
    val iconTint by animateColorAsState(
        targetValue =
            when {
                !canEdit -> COUITheme.colorScheme.disabledOnSecondaryVariant
                every -> COUITheme.colorScheme.primary
                else -> COUITheme.colorScheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CommonCommandsSelectAllIcon",
    )
    DisabledHint(if (canEdit) null else disabledClickReason()) {
        HeaderIconButton(
            onClick = { CommonCommandsEditState.toggleAll(all) },
            enabled = canEdit,
        ) {
            Icon(
                PageIcons.SelectAll,
                contentDescription =
                    if (every) {
                        I18n.get(Strings.KEYS_DESELECT_ALL)
                    } else {
                        I18n.get(Strings.KEYS_SELECT_ALL)
                    },
                tint = iconTint,
            )
        }
    }
    //  间距挂在**左边那颗**身上（与「按键功能」页同一套写法）：两颗都带就会叠成两档
    Spacer(Modifier.width(HEADER_ACTION_GAP))
}
