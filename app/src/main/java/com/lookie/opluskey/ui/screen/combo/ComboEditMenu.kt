package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.HeaderMenuButton
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.BRANCHES_TITLE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.ComboEditState
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 「组合按键编辑」页顶栏那颗 ⋮ —— 现在只有一行「删除组合按键」。
 *
 *  为什么删在这个页而不在列表那一行：列表一行只有「进这一页」一件事，行尾再挂一颗删除图标
 *  会挤掉「进下一页」那枚箭头（`CardRow.Item` 的 `trailing` 与 `arrow` 互斥）——
 *  而删除本来就不是高频动作，放菜单里、再加一道确认，误触代价最小。
 *
 *  删完**退回列表**：这一页的内容已经不存在了，留在原地只剩一个空壳。
 */
@Composable
internal fun SettingsPage.ComboEditMenu() {
    val cfg = configState.config.value
    val id = ComboEditState.editingId
    val combo = id?.let { cid -> cfg.combos.firstOrNull { it.id == cid } }
    //  确认窗里每个键名各套一对括号（`「侧键」+「电量 +」`）：整串再包一层会读成
    //  「删除『侧键 + 电量 +』」—— 那是把两件东西写成了一个名字。
    val label = combo?.let { comboLabel(cfg, it, bracketed = true) }.orEmpty()

    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val preciseClick: PreciseClickState = rememberPreciseClickState()

    Box {
        HeaderMenuButton(preciseClick) { open = true }

        PageMenu(
            entries =
                comboMenuEntries(
                    id = id,
                    open = open,
                    onOpenBranches = {
                        open = false
                        id?.let(::openBranchesForCombo)
                    },
                    onAskDelete = {
                        open = false
                        confirm = true
                    },
                ),
            preciseClick = preciseClick,
            onDismiss = { open = false },
        )
    }

    ConfirmDialog(
        show = confirm,
        title = I18n.format(Strings.COMBO_DELETE_CONFIRM, label),
        action = I18n.get(Strings.COMBO_DELETE, "删除组合按键"),
        onDismiss = { confirm = false },
        onConfirm = {
            confirm = false
            val target = id ?: return@ConfirmDialog
            XLog.w("组合键：从编辑页删除 $target")
            comboWriteGuard("删除组合按键") { removeCombo(target) }
            ComboEditState.clear()
            goBack()
        },
    )
}

/**
 * 这一页 ⋮ 的条目 —— 全抽出来只为让 [ComboEditMenu] 留在 detekt 的长度上限内。
 *
 * @param id 正在编辑的那一档（`null` = 刚删完、退场那一帧）
 * @param onOpenBranches 「条件执行」那一行
 * @param onAskDelete 「删除组合按键」那一行
 */
private fun comboMenuEntries(
    id: String?,
    open: Boolean,
    onOpenBranches: () -> Unit,
    onAskDelete: () -> Unit,
): List<MenuEntry> =
    if (!open || id == null) {
        //  一档都没了（刚删完、退场那一帧）就不弹：空菜单在库那层是「不显示」，但 keep 住会
        //  在去列表的路上留一层看不见的浮层。
        emptyList()
    } else {
        listOf(
            //  「条件执行」放第一行：它是**进另一页**，与下面那条「删除这组」不是一类事，用加深带隔开。
            MenuEntry.Row(
                title = BRANCHES_TITLE,
                icon = PageIcons.CommandBranch,
                submenu = true,
                trailingIcon = PageIcons.EnterArrow,
                onPick = onOpenBranches,
            ),
            MenuEntry.Row(
                title = I18n.get(Strings.COMBO_DELETE, "删除组合按键"),
                icon = PageIcons.CommandDelete,
                danger = true,
                dividerBefore = true,
                onPick = onAskDelete,
            ),
        )
    }

/**
 * 「条件执行」：把这一档组合键指过去再切页。
 *
 *  这里**不** `load`：那一页的闸门是 `draft.owner`，提前写反而会让它判成「已经载入过」而跳过重灌。
 *  用户停在短按页还是长按页，就编哪一档 —— 组合键只有这两档；`Gesture.entries` 是
 *  单击(0) / 双击(1) / 长按(2)，长按不是页号 1，必须按枚举取。
 */
private fun SettingsPage.openBranchesForCombo(id: String) {
    branchDraft.draft.comboId = id
    branchGesture = if (ComboEditState.page == 0) Gesture.SINGLE.ordinal else Gesture.LONG.ordinal
    XLog.w("组合键：进「$BRANCHES_TITLE」页（$id）")
    navTo(Screen.BRANCHES)
}
