package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appTargetLabelOf
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.state.AppPickerUiState
import com.lookie.opluskey.ui.state.PickedDetail

// 「选择应用」页那三张**拦人的脸** —— 都是「留在这一页」，什么都不写。
//
// 前两张只在**点 FAB** 那一刻弹：点行只是往下一级走，到不了写入那一步。
// 第三张是**退页**那一刻弹（触发点在 `SettingsPage.goBack`）。

/**
 * 三张确认窗的挂载点。
 *
 * @param onPick 「不支持小窗」那一路的兜底：用户点了确定 ⇒ 按普通打开写下去（`window = false`）。
 */
@Composable
internal fun SettingsPage.AppPickerDialogs(
    ui: AppPickerUiState,
    onPick: (PickedDetail, Boolean) -> Unit,
) {
    UnopenableDialog(ui)
    SmallWindowUnsupportedDialog(ui, onPick)
    DiscardPickDialog()
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
    onPick: (PickedDetail, Boolean) -> Unit,
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
                //  优先用 `pending`（可能带 `/类名`、快捷方式的 id，也可能带 `999@`）——
                //  这一页的 FAB 就是按它弹的这张窗；`app.target` 只是「这个应用」那一档。
                val picked = ui.pending ?: PickedDetail(blocked.app.target, shortcutId = null)
                XLog.w("选择应用：${picked.target} 改为普通打开并写入")
                onPick(picked, false)
            }
        },
    )
}

/**
 * (3)「放弃这次选择」：这一趟已经挑中过一条（二级页点了确认）、还没按右下角那颗提交，用户按了返回。
 *
 *  触发点在 `SettingsPage.goBack`（`picker.draft` 非空时置位并拦住不退），这里只负责画。
 *  不拦的话这一退会把草稿连同整趟挑的整个丢掉 —— 而页面离栈时 `resetSession` 连草稿一起清，
 *  用户只是「挑完想先看看别的」回来就没有了。
 *
 *  ## 正文里念的是**格式化后**的那一句
 *
 *  `draft.label` 只是那一行自己的名字（应用名 / 界面名 / 快捷方式名），单独拿出来念，
 *  用户分不清丢的是「微信」还是「微信(扫一扫)」—— 而这页列表很长，挑错了要滚回去重找。
 *  所以走 [appTargetLabelOf]：应用名 [+ 小窗] [+ 组件名 / 快捷方式名]，与顶栏「已选择」那行、
 *  网格那一格的小字、命令卡摘要**同一套拼法**（三处各拼一半的老毛病不再带进来）。
 *
 *  调用点把草稿的 `shortcutId` / 「小窗口打开」那一档一起交过去：快捷方式不带 `/类名` 的后缀，
 *  勾着小窗的目标要念成「微信(小窗)」而不是光一个应用名。
 *
 *  确认 ⇒ 丢掉草稿并**真退**（`popBackStack` 绕开 `goBack` 的拦截，否则又弹回自己）；
 *  取消 ⇒ 关掉窗，回到这一页继续挑（草稿原样留着，那颗 FAB 仍在）。
 */
@Composable
private fun SettingsPage.DiscardPickDialog() {
    //  名字要在**弹窗那一刻**取：草稿一清（确认那一下）就没有来源了。
    val picked = picker.draft
    ConfirmDialog(
        show = overlays.confirmLeavePick,
        title = I18n.get(Strings.DIALOG_DISCARD_PICK_TITLE, "放弃这次选择"),
        summary =
            picked?.let {
                I18n.format(
                    Strings.DIALOG_DISCARD_PICK_MESSAGE,
                    escapeEmphasis(
                        appTargetLabelOf(
                            target = it.target,
                            shortcut = it.shortcutId != null,
                            window = picker.smallWindow.value,
                            draftLabel = it.label,
                        ),
                    ),
                )
            },
        action = I18n.get(Strings.COMMON_DISCARD, "放弃"),
        onDismiss = { overlays.confirmLeavePick = false },
        onConfirm = {
            overlays.confirmLeavePick = false
            XLog.w("选择应用：放弃这一趟挑中的 ${picked?.target}，退页")
            popBackStack()
        },
    )
}
