package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lookie.opluskey.core.log.XLog

/**
 * 用户提示（一句话，底部短暂出现）。
 *
 * 文案一律走这里，调用点只传 2~4 字的结论；写盘失败这类没有可见结果的也报一句，
 * 免得「点了没反应」。
 */
internal fun SettingsPage.toast(text: String) {
    try {
        android.widget.Toast
            .makeText(context, text, android.widget.Toast.LENGTH_SHORT)
            .show()
    } catch (e: Throwable) {
        XLog.w("提示失败（忽略）：${e.message}")
    }
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
