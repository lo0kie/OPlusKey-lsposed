package com.lookie.opluskey.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lookie.opluskey.XLog

/**
 * 用户提示（一句话，底部短暂出现）。
 *
 * 文案一律走这里，调用点只传 2~4 字的结论；写盘失败这类没有可见结果的也报一句，
 * 免得「点了没反应」。
 */
internal fun SettingsPage.toast(text: String) {
    try {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Throwable) {
        XLog.w("提示失败（忽略）：${e.message}")
    }
}

/**
 * UI 触觉反馈的唯一判据 —— 只看系统状态（静音不振）。
 *
 * 模块的「振动反馈」开关不参与：它管的是命令执行时的振动（判定在 `gesture/GestureReceiver`）。滑杆的
 * `hapticEffect` 是组合期参数，静音状态变化要等重组才反映（页面无 ticker，只有 `SyncToHost` 的 300ms 轮询）。
 */
internal fun SettingsPage.hapticsAllowed(): Boolean =
    audioManager?.ringerMode != android.media.AudioManager.RINGER_MODE_SILENT

/**
 * 通用振动通道 —— 界面所有点击 / 长按的触觉都走这里。
 *
 * 用预建的 [vibrator] 走一次短促 click 效果，不走 `View.performHapticFeedback`（它首次调用会在主线程同步
 * 创建 SystemVibrator，砸在 push 动画第一帧上）。
 */
@Composable
internal fun SettingsPage.rememberTick(): () -> Unit = remember { { tickNow() } }

/** 实际执行一次点击触觉。判据只有 [hapticsAllowed]（系统状态），与模块开关无关。 */
internal fun SettingsPage.tickNow() {
    if (!hapticsAllowed()) return
    try {
        vibrator?.vibrate(
            android.os.VibrationEffect.createPredefined(
                android.os.VibrationEffect.EFFECT_CLICK,
            ),
        )
    } catch (e: Throwable) {
        XLog.w("触觉反馈失败（忽略）：${e.message}")
    }
}
