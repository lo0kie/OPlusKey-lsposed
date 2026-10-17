package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.escapeEmphasis

// 「选择应用」页那两张**拦人的脸** —— 都是「留在这一页」，什么都不写。
//
// 从 `AppPicker.kt` 拆出来：它们挂在本页末尾，读的是页面状态里那两个挂起项，
// 与列表、加载没有关系。

/**
 * 两张确认窗的挂载点。
 *
 * @param onPick 「不支持小窗」那一路的兜底：用户点了确定 ⇒ 按普通打开写下去（`window = false`）。
 */
@Composable
internal fun SettingsPage.AppPickerDialogs(
    ui: AppPickerUiState,
    onPick: (String, Boolean) -> Unit,
) {
    UnopenableDialog(ui)
    SmallWindowUnsupportedDialog(ui, onPick)
}

/**
 * (1)「此应用无法打开」：点确认键时挑中的是个拉不起来的应用。
 *
 *  **没有正文**（`summary = null`；给空串会画出一行空白），两颗按钮都只是把这句提示关掉、
 *  回选择页 —— 什么都不写、什么都不改。
 */
@Composable
private fun SettingsPage.UnopenableDialog(ui: AppPickerUiState) {
    ConfirmDialog(
        show = ui.blockedUnopenable != null,
        title = I18n.get(Strings.DIALOG_APP_UNOPENABLE_TITLE, "此应用无法打开"),
        summary = null,
        action = I18n.get(Strings.COMMON_CONFIRM, "确定"),
        positiveDanger = false,
        onDismiss = { ui.blockedUnopenable = null },
        onConfirm = { ui.blockedUnopenable = null },
    )
}

/**
 * (2)「不支持以小窗口打开」：**三处触发点共用这一张脸** —— 切应用 / 点菜单里那一项
 *  （`SmallWindowGuard` 里）、点确认键（`AppConfirmFab` 里，`fromPick = true`）。
 *
 *  确认 ⇒ 菜单里那一项拨回关；从点确认键来的还顺手按 `app:<包名>` 写下去并离开本页。
 *  取消 ⇒ 什么都不做。正文里的应用名用 `「」` 夹住；动作项不上红（不丢用户的东西）。
 */
@Composable
private fun SettingsPage.SmallWindowUnsupportedDialog(
    ui: AppPickerUiState,
    onPick: (String, Boolean) -> Unit,
) {
    ConfirmDialog(
        show = ui.blockedSmallWindow != null,
        title = I18n.get(Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_TITLE, "不支持以小窗口打开"),
        summary =
            I18n.format(
                Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_MESSAGE,
                escapeEmphasis(ui.blockedSmallWindow?.app?.label ?: ""),
            ),
        action = I18n.get(Strings.COMMON_CONFIRM, "确定"),
        positiveDanger = false,
        onDismiss = { ui.blockedSmallWindow = null },
        onConfirm = {
            val blocked = ui.blockedSmallWindow
            ui.blockedSmallWindow = null
            // 拨回关：菜单里那一项跟着回去，用户看得见这次选择了什么
            picker.smallWindow.value = false
            XLog.w("选择应用：该应用不支持小窗，已把「以小窗口打开」拨回关")
            // 点确认键那一路：用户要的就是「取消小窗这个勾选、照常存」—— 按普通打开写下去
            if (blocked?.fromPick == true) {
                XLog.w("选择应用：${blocked.app.packageName} 改为普通打开并写入")
                onPick(blocked.app.packageName, false)
            }
        },
    )
}
