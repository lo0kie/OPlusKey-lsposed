package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.state.AppPickerUiState

// 「选择应用」页那两张**拦人的脸** —— 都是「留在这一页」，什么都不写。
//
// 两张都只在**点 FAB** 那一刻弹：点行只是往下一级走，到不了写入那一步。

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
 * (1)「此应用无法打开」：点 FAB 时选中的是个拉不起来的应用。
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
 * (2)「不支持以小窗口打开」：**只有点 FAB 这一个触发点**（`AppConfirmFab` 里，`fromPick = true`）。
 *
 *  确认 ⇒ 菜单里那一项拨回关，并按**原来那个完整目标**普通打开写下去（用户要的就是「取消勾选、
 *  照常存」）；取消 ⇒ 什么都不做。正文里的应用名用 `「」` 夹住；动作项不上红（不丢用户的东西）。
 *
 *  「完整」这三个字是重点：`999@包名/类名` 里的用户前缀是分身的身份，`/类名` 是用户在二级页
 *  刚挑好的具体界面 —— 早先这里交的是 `app.packageName`，写回去就成了「主用户、自动入口」，
 *  用户看到的是「我就关了个小窗开关，绑的东西怎么变了」。
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
            if (blocked?.fromPick == true) {
                //  优先用 `pending`（可能带 `/类名`、`/快捷方式id`，也可能带 `999@`）——
                //  这一页的 FAB 就是按它弹的这张窗；`app.target` 只是「这个应用」那一档。
                val target = ui.pending?.target ?: blocked.app.target
                XLog.w("选择应用：$target 改为普通打开并写入")
                onPick(target, false)
            }
        },
    )
}
