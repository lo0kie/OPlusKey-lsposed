package com.lookie.opluskey.ui.feedback

import android.annotation.SuppressLint
import android.content.Context
import com.lookie.opluskey.core.log.XLog

/**
 * 设置页的**触觉反馈**那一域：两个系统服务 + 「该不该振」的判据 + 真的振一下。
 *
 * ## 为什么服务要在建页期就拿好
 *
 *  第一次 `View.performHapticFeedback` 会同步走 `SystemVibrator Created`（binder + 服务查询），
 *  砸在 push 动画第一帧上，就是「进子页偶发卡顿」。提前拿到这里，点击路径上只剩一次 `vibrate`。
 *  宿主已持有 `android.permission.VIBRATE`（`dumpsys` 确认过，模块跑在宿主进程里共用）。
 *
 * ## 判据：系统状态 + 模块自己的「界面振动反馈」
 *
 *  [allowed] 看两样：系统铃声模式（静音不振，跟随系统礼节），以及模块的「界面振动反馈」
 *  （`GlobalConfig.uiVibrate`，「更多选项 → 外观」里那一颗）。它**不看**「振动反馈」开关
 *  —— 后者管的是命令执行时的振动，判定在 `host/GestureReceiver`。
 *  判定放在每次振动时现查（[tick] 每振一次查一次），拨完开关 / 系统静音一变立刻生效。
 *
 * ## 为什么这一组要收
 *
 *  两个服务字段 + 两处判据原先散在 [SettingsPage] 与 `PageFeedback.kt` 里，而它们说的是
 *  同一件事：「这台机器的触觉现在能不能用」。摊开时「为什么静音不振」要在两个文件之间找。
 */
internal class PageHaptics(
    context: Context,
    /**
     * 模块自己的「界面振动反馈」开关（`GlobalConfig.uiVibrate`）。
     *
     *  每次现查（[tick] 每振一次查一次），拨完开关立刻生效，不必重建页面。
     *  默认 `true`：没有配置来源时（别的入口 new 一个）按「振」处理，与 [allowed] 读不到
     *  响铃模式时宁可多振同一个口径。
     */
    private val uiVibrate: () -> Boolean = { true },
) {
    private val vibrator: android.os.Vibrator? =
        try {
            context.getSystemService(android.os.Vibrator::class.java)
        } catch (e: Throwable) {
            XLog.w("拿 Vibrator 失败（触觉反馈不可用）：${e.message}")
            null
        }

    private val audioManager: android.media.AudioManager? =
        try {
            context.getSystemService(android.media.AudioManager::class.java)
        } catch (e: Throwable) {
            XLog.w("拿 AudioManager 失败（响铃状态不可读）：${e.message}")
            null
        }

    /**
     * 这一刻该不该振：**模块的「界面振动反馈」开着 且 系统没静音**。
     *
     *  静音不振是跟随系统礼节；模块那一颗管的是「这套界面要不要有触觉」——两者都得成立。
     *  读不到响铃模式时**振**（宁可多振），但开关关着时一律不振。
     */
    fun allowed(): Boolean = uiVibrate() && audioManager?.ringerMode != android.media.AudioManager.RINGER_MODE_SILENT

    /**
     * 实际执行一次点击触觉。
     *
     *  `MissingPermission` 这一条按「谁在执行」判：设置页整套跑在**宿主**进程
     *  （`com.oplus.gesture` / `com.oplus.gesture.ui`）里，而宿主自己声明并被授予了
     *  `android.permission.VIBRATE`（真机 `dumpsys package` 核对过）。模块清单里不声明它 ——
     *  写上也管不到宿主那个 UID。
     */
    @SuppressLint("MissingPermission")
    fun tick() {
        if (!allowed()) return
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
}
