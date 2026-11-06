package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

// 「执行时提示」那一对写入：**开关**与**文案模板**。
//
// 单独一个文件而不是塞进 `GlobalWrite.kt`：那支已经贴着 detekt 的单文件函数数上限，
// 而这两项本来就是一件事的两半（开关决定弹不弹、模板决定弹什么），与 `DisplayWrite.kt`
// 收「页面大小」那两半同一个理由。

/**
 * 切「执行时提示」（执行命令时弹一条 Toast）—— 行点击与开关共用这一个入口。
 * 起从每条命令自己的字段提成全局开关（见 `GlobalConfig.toastOnRun`）；
 * 判定侧在 `host/GestureExec`，宿主进程每次执行现读配置，拨完立刻生效。
 */
internal fun SettingsPage.setToastOnRun(on: Boolean) =
    toggleGlobal("toast_on_run=${if (on) 1 else 0}", I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"), on) {
        it.copy(toastOnRun = on)
    }

/**
 * 写「执行时提示」的文案模板（`$key` / `$gesture` / `$action`，见 `ToastNotice`）。
 *
 *  **空串就是「用默认那句」**：默认文案只在 `ToastNotice.DEFAULT_TEMPLATE` 存一份，
 *  这里不塞字面量 —— 否则「默认是什么」会有两处各写一遍。
 */
internal fun SettingsPage.setToastTemplate(template: String) {
    val cfg = configState.config.value
    write(
        cfg.copy(global = cfg.global.copy(toastTemplate = template)),
        "toast_template=${template.length}字",
    )
}
