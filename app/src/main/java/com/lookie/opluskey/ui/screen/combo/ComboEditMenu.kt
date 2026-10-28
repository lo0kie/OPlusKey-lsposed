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
import com.lookie.opluskey.core.model.Config
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
 * 「组合按键编辑」页顶栏那颗 ⋮ —— 现在三行：条件执行 / 停用启用 / 删除。
 *
 *  为什么删在这个页而不在列表那一行：列表一行只有「进这一页」一件事，行尾再挂一颗删除图标
 *  会挤掉「进下一页」那枚箭头（`CardRow.Item` 的 `trailing` 与 `arrow` 互斥）——
 *  而删除本来就不是高频动作，放菜单里、再加一道确认，误触代价最小。
 *
 *  「停用 / 启用」与「按键绑定」页那三行**同一套**（见 `BindingKeyMenu` 的 `keyActionEntries`）：
 *  停用在前、删除压最下面；它写的是这一档自己的 `ComboKey.enabled`，与模块总闸是两层。
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

    //  两个确认窗的开合位（删除 / 停用）—— 同一时刻至多一个，见 [ComboMenuDialogs]
    val dialogs = remember { ComboMenuDialogs() }
    var open by remember { mutableStateOf(false) }
    val preciseClick: PreciseClickState = rememberPreciseClickState()

    Box {
        HeaderMenuButton(preciseClick) { open = true }

        PageMenu(
            entries =
                comboMenuEntries(
                    id = id,
                    cfg = cfg,
                    open = open,
                    actions =
                        ComboMenuActions(
                            onOpenBranches = {
                                open = false
                                id?.let(::openBranchesForCombo)
                            },
                            //  启用不必确认（那是把它开回来，没有可失去的东西）；停用先弹窗 ——
                            //  这一行与下面「删除组合按键」只差两个字，后果却是两种。
                            onToggleEnabled = {
                                open = false
                                if (id != null) {
                                    if (combo?.enabled == false) {
                                        comboWriteGuard("启用组合按键") { setComboEnabled(id, true) }
                                    } else {
                                        dialogs.toggle = true
                                    }
                                }
                            },
                            onAskDelete = {
                                open = false
                                dialogs.delete = true
                            },
                        ),
                ),
            preciseClick = preciseClick,
            onDismiss = { open = false },
        )
    }

    ComboMenuDialogHost(id = id, label = label, dialogs = dialogs)
}

/**
 * 这一页两个确认窗的开合位 —— 与按键菜单那份 enum 同一个意思（同一时刻只有一个窗）。
 *
 *  单独一个对象：`ComboEditMenu` 已经把菜单本身、两处动作说明都扛着了，再挂两个
 *  `remember { mutableStateOf(false) }` 就过 detekt 的长度上限。
 */
private class ComboMenuDialogs {
    var delete by mutableStateOf(false)
    var toggle by mutableStateOf(false)
}

/**
 * 两个确认窗的落点 —— 抽成一支同样只为长度上限；各自「确认之后干什么」写在这里。
 *
 *  @param id 正在编辑的那一档（`null` = 已经退场那一帧，什么都不做）
 */
@Composable
private fun SettingsPage.ComboMenuDialogHost(
    id: String?,
    label: String,
    dialogs: ComboMenuDialogs,
) {
    ComboDisableDialog(
        show = dialogs.toggle,
        label = label,
        onDismiss = { dialogs.toggle = false },
        onConfirm = {
            dialogs.toggle = false
            id?.let { target -> comboWriteGuard("停用组合按键") { setComboEnabled(target, false) } }
        },
    )
    ComboDeleteDialog(
        show = dialogs.delete,
        label = label,
        onDismiss = { dialogs.delete = false },
        onConfirm = {
            dialogs.delete = false
            id?.let { target ->
                XLog.w("组合键：从编辑页删除 $target")
                comboWriteGuard("删除组合按键") { removeCombo(target) }
                ComboEditState.clear()
                goBack()
            }
        },
    )
}

/**
 * 删掉这一档之前的确认窗 —— 抽成一支只为让 [ComboEditMenu] 留在 detekt 的长度上限内
 * （它与停用那道窗并排挂，两支都留在母函数里就超了）。
 */
@Composable
private fun SettingsPage.ComboDeleteDialog(
    show: Boolean,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        show = show,
        title = I18n.format(Strings.COMBO_DELETE_CONFIRM, label),
        action = I18n.get(Strings.COMBO_DELETE, "删除组合按键"),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 停用这一档组合之前问一句 —— 与「按键绑定」页、场景那两处**同一句规矩**：
 * 停用只是让它不成立，两档动作都还在；而它下面那一行就是「删除组合按键」，
 * 两者只差两个字，不确认很容易点错。
 *
 *  抽成一支只为让 [ComboEditMenu] 留在 detekt 的长度上限内。
 */
@Composable
private fun SettingsPage.ComboDisableDialog(
    show: Boolean,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        show = show,
        title = I18n.get(Strings.COMBO_DISABLE, "停用组合按键"),
        summary = I18n.format(Strings.COMBO_DISABLE_MESSAGE, label),
        action = I18n.get(Strings.COMMON_DISABLE, "停用"),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 这一页 ⋮ 上那三行要干的事 —— 合成一个对象，条目那支才留在 detekt 的参数上限内
 * （与 `BindingKeyMenu` 的 `KeyMenuActions` 同一个写法）。
 *
 * @param onOpenBranches 「条件执行」那一行（进另一页）
 * @param onToggleEnabled 「停用 / 启用」那一行（文案与图标跟着当前状态换）
 * @param onAskDelete 「删除组合按键」那一行
 */
private class ComboMenuActions(
    val onOpenBranches: () -> Unit,
    val onToggleEnabled: () -> Unit,
    val onAskDelete: () -> Unit,
)

/**
 * 这一页 ⋮ 的条目 —— 全抽出来只为让 [ComboEditMenu] 留在 detekt 的长度上限内。
 *
 *  三行的分工与「按键绑定」页那份菜单一致：最上面是**进下一页**（条件执行），一条加深带隔开，
 *  下面两行才是对这一档动手（停用 / 启用、删除），删除压最下面。
 *
 * @param id 正在编辑的那一档（`null` = 刚删完、退场那一帧）
 */
private fun SettingsPage.comboMenuEntries(
    id: String?,
    cfg: Config,
    open: Boolean,
    actions: ComboMenuActions,
): List<MenuEntry> =
    if (!open || id == null) {
        //  一档都没了（刚删完、退场那一帧）就不弹：空菜单在库那层是「不显示」，但 keep 住会
        //  在去列表的路上留一层看不见的浮层。
        emptyList()
    } else {
        val combo = cfg.combos.firstOrNull { it.id == id }
        val enabled = combo?.enabled ?: true
        val label = combo?.let { comboLabel(cfg, it, bracketed = true) }.orEmpty()
        listOf(
            //  「条件执行」放第一行：它是**进另一页**，与下面那两行不是一类事，用加深带隔开。
            //  这一档停用了 ⇒ 也按不动：条件表说的是「这一档怎么分流」，这一档都不成立了，
            //  进去改那份表没有任何去处（与按键那边同一条口径，提示语同一句形状）。
            MenuEntry.Row(
                title = BRANCHES_TITLE,
                icon = PageIcons.Scene,
                submenu = true,
                trailingIcon = PageIcons.EnterArrow,
                enabled = enabled,
                disabledHint = if (enabled) null else I18n.format(Strings.COMBO_DISABLED_ITEM, label),
                onPick = actions.onOpenBranches,
            ),
            MenuEntry.Row(
                title =
                    if (enabled) {
                        I18n.get(Strings.COMBO_DISABLE, "停用组合按键")
                    } else {
                        I18n.get(Strings.COMBO_ENABLE, "启用组合按键")
                    },
                //  停用 / 启用各一颗（锁 / 开锁）：这一行本来就随状态换文案，图标跟着换才一致
                icon = if (enabled) PageIcons.KeyLocked else PageIcons.KeyUnlocked,
                dividerBefore = true,
                onPick = actions.onToggleEnabled,
            ),
            MenuEntry.Row(
                title = I18n.get(Strings.COMBO_DELETE, "删除组合按键"),
                icon = PageIcons.CommandDelete,
                danger = true,
                onPick = actions.onAskDelete,
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
