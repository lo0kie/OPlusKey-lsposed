package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.state.CommandsSave

/**
 * 操作提示 —— 用户做完一个动作之后的回执（一句话，底部短暂出现）。
 *
 *  文案一律走这里，调用点只传一句结论；写盘失败这类没有可见结果的也报一句，免得「点了没反应」。
 *
 *  ## 为什么不是 Android Toast
 *
 *  Toast 是**系统窗口**：它的样式跟着系统走，与 COUI 那套胶囊完全两回事 —— 同一页里
 *  一条圆角磨砂的常驻提示旁边飘一个方头方脑的 Toast，看着就不是一个应用。而且 Toast
 *  在设置页这种「整页覆盖层」里还容易被宿主的窗口层级压住。所以统一走 COUI 的 `Snackbar`：
 *  与首页那条常驻提示同一个组件、同一个落点，样式自动跟着主题。
 *
 *  落点在页面级那个提示槽（`ui/screen/PageContent.kt` 的 Scaffold **浮层槽**，
 *  排在弹窗宿主之后）—— 各页都能看到、不随内容滚动，而且**压在弹窗之上**。
 *
 *  **不进提示条的唯一一处**：命令执行失败那条（`host/action/CommandFailure`）—— 它发生在模块
 *  **不在前台**的时候，页面根本没在画。
 *
 *  （「收集按键」弹窗里的拒绝理由原先也走正文，是因为当时提示条在 `snackbarHost` 槽、
 *  永远被弹窗盖住；2026-10-19 提示槽挪到浮层槽之后那条理由不成立了，它已改走 [SettingsPage.hint]。）
 */
internal fun SettingsPage.hint(text: String) {
    // 留一行判据：提示条有没有被弹出来，从日志就能答（真机上「点了没反应」时先看这句）
    XLog.w("提示：$text")
    link.hint(text)
}

/**
 * 拨开关的回执 —— 「已开启「振动反馈」」/「已关闭「振动反馈」」。
 *
 *  [title] 由调用点把自己那一行的标题递进来（含译文），不在这里按功能列表反查：开关散在
 *  四个文件里，一处分档就得改三处；由那一行自己报名字，改文案只跟着那一行走。
 */
internal fun switchNotice(
    title: String,
    on: Boolean,
): String =
    I18n
        .get(
            if (on) Strings.TOAST_SWITCH_ON else Strings.TOAST_SWITCH_OFF,
            if (on) "已开启「%s」" else "已关闭「%s」",
        ).format(title)

/**
 * 写一次配置 + 报一句回执（拨开关、改绑定这类「改完就完事」的动作都走它）。
 *
 *  **只在 [CommandsSave.Ok] 时报**：[write] 对「没落盘」与「落盘了但广播没出去」各自已经报过
 *  自己的那一条，这里再补一句「已开启」会把那条警告顶掉 —— 底部只有一行操作回执。
 *
 *  [notice] 用 lambda 而不是字符串：切语言那一条的名字必须在**写盘之后**取
 *  （[write] 里换了 [I18n.currentLanguage]），先算好的话整句话还是旧语言。
 */
internal fun SettingsPage.writeAndNotice(
    next: Config,
    what: String,
    notice: () -> String,
) {
    if (write(next, what) == CommandsSave.Ok) hint(notice())
}

/**
 * UI 触觉反馈的唯一判据 —— 只看系统状态（静音不振）。实现在 [PageHaptics.allowed]。
 *
 * 模块的「振动反馈」开关不参与：它管的是命令执行时的振动（判定在 `host/GestureReceiver`）。
 * 判定在调用时现查一次（[tickNow] 每振一次查一次），系统静音一变立刻生效。
 */
internal fun SettingsPage.hapticsAllowed(): Boolean = haptics.allowed()

/**
 * 通用振动通道 —— 界面所有点击 / 长按的触觉都走这里（实现在 [PageHaptics.tick]）。
 */
@Composable
internal fun SettingsPage.rememberTick(): () -> Unit = remember { { tickNow() } }

/**
 * 实际执行一次点击触觉。判据只有 [hapticsAllowed]（系统状态），与模块开关无关。
 * `MissingPermission` 为什么成立写在 [PageHaptics.tick] 那里。
 */
internal fun SettingsPage.tickNow() = haptics.tick()
