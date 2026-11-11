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
import com.lookie.opluskey.ui.state.PickerToggle

// 「选择应用」页那三张**拦人的脸** —— 都是「留在这一页」，什么都不写。
//
// 前两张只在**点 FAB** 那一刻弹：点行只是往下一级走，到不了写入那一步。
// 第三张是**退页**那一刻弹（触发点在 `SettingsPage.goBack`）。

/**
 * 三张确认窗的挂载点。
 *
 * @param onPick 「这一档开关用不了」那一路的兜底：用户点了确定 ⇒ 按普通方式写下去。
 */
@Composable
internal fun SettingsPage.AppPickerDialogs(
    ui: AppPickerUiState,
    onPick: (PickedDetail, Boolean) -> Unit,
) {
    UnopenableDialog(ui)
    BlockedToggleDialog(ui, onPick)
    DiscardPickDialog()
}

/**
 * 把 [toggle] 那一档开关拨回关 —— 「这一档用不了」那张窗点确定时调。
 *
 *  两档各一个字段、判据只有「哪一档」，所以收在这一处；窗那边不必认识具体字段
 *  （与「两档共用一张窗」同一个理由，见 [ToggleBlock]）。
 */
private fun SettingsPage.turnOff(toggle: PickerToggle) {
    when (toggle) {
        PickerToggle.MiniWindow -> picker.smallWindow.value = false
        PickerToggle.StockAnim -> picker.stockAnim.value = false
    }
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
 * (2)「这一档开关这一次用不了」：**只有点 FAB 这一个触发点**（`AppConfirmFab` 里，
 * 判据见 `blockedToggleOf`）。两档共用这一张，只有文案与「拨哪一个开关」不同：
 *  · 小窗 —— 框架不支持这个应用开小窗；
 *  · 原版动画 —— 分身 / 快捷方式 / 具体界面吃不到（见 `AppPickerState.stockAnimUnsupported`）。
 *
 *  确认 ⇒ 菜单里那一项拨回关，并按**原来那个完整目标**照常写下去（用户要的就是「取消勾选、
 *  照常存」）；取消 ⇒ 什么都不做。正文里的名字用 `「」` 夹住；动作项不上红（不丢用户的东西）。
 *
 *  「完整」这三个字是重点：`999@包名/类名` 里的用户前缀是分身的身份，`/类名` 是用户在二级页
 *  刚挑好的具体界面 —— 早先这里交的是 `app.packageName`，写回去就成了「主用户、自动入口」，
 *  用户看到的是「我就关了个开关，绑的东西怎么变了」。
 */
@Composable
private fun SettingsPage.BlockedToggleDialog(
    ui: AppPickerUiState,
    onPick: (PickedDetail, Boolean) -> Unit,
) {
    val blocked = ui.blockedToggle
    val kind = blocked?.toggle
    ConfirmDialog(
        show = blocked != null,
        title =
            when (kind) {
                PickerToggle.StockAnim -> I18n.get(Strings.DIALOG_STOCK_ANIM_DROPPED_TITLE, "无法使用原版动画")
                else -> I18n.get(Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_TITLE, "不支持以小窗口打开")
            },
        summary =
            blocked?.let {
                I18n.format(
                    when (it.toggle) {
                        PickerToggle.StockAnim -> Strings.DIALOG_STOCK_ANIM_DROPPED_MESSAGE
                        PickerToggle.MiniWindow -> Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_MESSAGE
                    },
                    escapeEmphasis(it.label),
                )
            },
        action = I18n.get(Strings.COMMON_CONFIRM, "确定"),
        positiveDanger = false,
        onDismiss = { ui.blockedToggle = null },
        onConfirm = {
            ui.blockedToggle = null
            // 拨回关：菜单里那一项跟着回去，用户看得见这次选择了什么
            blocked?.let {
                turnOff(it.toggle)
                XLog.w("选择应用：这一档用不了，已把「${it.toggle}」拨回关")
            }
            //  写下去的仍是 `pending`（可能带 `/类名`、快捷方式的 id，也可能带 `999@`）——
            //  这一页的 FAB 就是按它弹的这张窗。
            ui.pending?.let { picked ->
                XLog.w("选择应用：${picked.target} 改为普通方式写入")
                onPick(picked, picker.smallWindow.value)
            }
        },
    )
}

/**
 * (3)「放弃这次选择」：这一趟已经挑中过一条（二级页点了确认）**或拨过打开方式**、
 * 还没按右下角那颗提交，用户按了返回。
 *
 *  触发点在 `SettingsPage.goBack`（`picker.draft` 非空、或 `picker.windowTogglesChanged` 时
 *  置位并拦住不退），这里只负责画。不拦的话这一退会把草稿连同整趟挑的整个丢掉 ——
 *  而页面离栈时 `resetSession` 连草稿一起清，用户只是「挑完想先看看别的」回来就没有了。
 *  拨开关那一档同理：小窗 / 原版动画是绑定的一部分，拨了没挑就退出去等于什么都没改。
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
 *  **没有草稿（只拨过开关）就不带正文**：那句要嵌「哪一条」，而这时没有目标可念 ——
 *  同一张窗、同一个标题与同一颗「放弃」就够说清这一退会丢什么；不为这一档另写一句文案。
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
        title = I18n.get(Strings.DIALOG_DISCARD_PICK_TITLE, "放弃本次选择"),
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
                            stockAnim = picker.stockAnim.value,
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
