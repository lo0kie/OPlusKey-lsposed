package com.lookie.opluskey.bridge.host

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 响铃模式（`keyevent:KEYCODE_VOLUME_MUTE` / 164）—— 响铃 → 振动 → 静音 → 响铃。
 *
 * 免打扰（Zen Mode）把外部档位视图压成静音（`am.ringerMode` 恒读静音），但内部档位仍归
 * AudioService 管：内部通道写入不被 zen 拦、也不联动关 zen。所以免打扰期间改读内部档位
 * （`AudioService.getRingerModeInternal()`，反射），循环与定点切换照常走内部通道、免打扰原样保留；
 * 读不到时退回自己记的上一档（rememberedMode）。
 */
internal class RingerController(
    private val context: () -> Context?,
    /** 「使用 ColorOS 接口」这一档由装配处传入（见 `ActionRunner`）—— 本类不自己去读配置。 */
    private val isColorOsApi: () -> Boolean,
) {
    /** 免打扰期间内部档位读不到时的兜底：自己记的上一档（只在写入成功时按**实际落到的档位**更新）。 */
    @Volatile private var rememberedMode: Int = AudioManager.RINGER_MODE_NORMAL

    /** 切下一档。 */
    fun cycle() {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，切换响铃模式跳过")
                return
            }
        try {
            val am =
                ctx.getSystemService(AudioManager::class.java) ?: run {
                    XLog.w("拿不到 AudioManager，切换响铃模式跳过")
                    return
                }
            val zen = isZenOn(ctx)
            val current = readCurrentMode(am, zen, ctx)
            val next =
                when (current) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL // 静音（或未知）→ 响铃
                }
            val landed = applyMode(am, current, next, zen, ctx)
            if (landed != null) rememberedMode = landed
        } catch (e: Throwable) {
            XLog.w("切换响铃模式失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 切到指定的一档（`ringer:normal` / `ringer:vibrate` / `ringer:silent`）。
     *
     * 已经在目标档位就什么都不做，避免把当前档位原样再写一次系统；免打扰开启时比的是内部档位。
     * 模式名认不出时只记日志、不改动系统档位，不往外抛（调用方在按键回调下游）。
     */
    fun setMode(mode: String) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，设置响铃模式跳过")
                return
            }
        val target =
            when (mode) {
                "normal" -> {
                    AudioManager.RINGER_MODE_NORMAL
                }

                "vibrate" -> {
                    AudioManager.RINGER_MODE_VIBRATE
                }

                "silent" -> {
                    AudioManager.RINGER_MODE_SILENT
                }

                else -> {
                    XLog.w("不认识的响铃模式（忽略）：$mode")
                    return
                }
            }
        try {
            val am =
                ctx.getSystemService(AudioManager::class.java) ?: run {
                    XLog.w("拿不到 AudioManager，设置响铃模式跳过")
                    return
                }
            val zen = isZenOn(ctx)
            val current = readCurrentMode(am, zen, ctx)
            if (current == target) {
                XLog.w("响铃模式：已经是${modeName(target)}，不动")
                return
            }
            val landed = applyMode(am, current, target, zen, ctx)
            if (landed != null) rememberedMode = landed
        } catch (e: Throwable) {
            XLog.w("设置响铃模式失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 读当前档位：
     * - ColorOS 模式：优先走内部通道 `AudioService.getRingerModeInternal()`（或 Settings.Global.mode_ringer），
     *   免打扰开启时不被压制成静音（0）。内部档位读不到才退回 rememberedMode。
     * - AOSP 标准模式：走公共视图 `am.ringerMode`（免打扰下若读不到退回 rememberedMode）。
     */
    private fun readCurrentMode(
        am: AudioManager,
        zen: Boolean,
        ctx: Context,
    ): Int {
        if (isColorOsApi()) {
            val service = Reflect.callStatic(AudioManager::class.java, "getService")
            val internal = service?.let { Reflect.call(it, "getRingerModeInternal") }
            if (internal is Int) return internal
            // 两条反射在部分 ROM 上取不到 —— 说出来：否则「读到的档位为什么是这个」
            //  在日志里没有任何依据，而下一档是拿它推出来的。
            XLog.w("响铃档位：内部通道（AudioService.getRingerModeInternal）读不到，改读 Settings.Global")

            val settingsMode =
                runCatching {
                    android.provider.Settings.Global
                        .getInt(ctx.contentResolver, MODE_RINGER_KEY, -1)
                }.getOrDefault(-1)
            if (settingsMode in 0..2) {
                XLog.w("响铃档位：用 Settings.Global.$MODE_RINGER_KEY=$settingsMode 兜底")
                return settingsMode
            }
            XLog.w(
                "响铃档位：内部通道与 Settings.Global.$MODE_RINGER_KEY 都没读到" +
                    (if (zen) "，免打扰开着 ⇒ 按上一档处理" else "，改读公共视图"),
            )
        }
        if (zen) {
            XLog.w("免打扰开启且内部档位读不到，按上一档处理（${modeName(rememberedMode)}）")
            return rememberedMode
        }
        return am.ringerMode
    }

    /**
     * 免打扰是否开着：公共 `currentInterruptionFilter`，不需要任何授权。
     * 读不到（拿不到 NM / 抛异常 / UNKNOWN）一律按「没开」处理。
     */
    private fun isZenOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val filter = runCatching { nm.currentInterruptionFilter }.getOrNull() ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /**
     * 切到 `next`，返回**实际落到的档位**（`null` = 两条路都没成，调用方据此更新 rememberedMode）。
     *
     * - ColorOS 模式：优先走内部通道 —— 防 `mRingerModeDelegate` 降级改写，免打扰期间不被拦截；
     * - AOSP 标准模式：走公共接口 `am.ringerMode`。
     */
    private fun applyMode(
        am: AudioManager,
        current: Int,
        next: Int,
        zen: Boolean,
        ctx: Context,
    ): Int? {
        val useColorOs = isColorOsApi()
        val note = if (zen) "（免打扰开启）" else ""
        if (useColorOs) {
            if (setRingerModeInternal(am, next)) {
                val landed = runCatching { readCurrentMode(am, zen, ctx) }.getOrNull()
                val degraded = landed != null && landed != next
                XLog.w(
                    "响铃模式（ColorOS 接口）：${modeName(current)} → ${modeName(next)}" +
                        (if (degraded) "（回读发现实际落成 ${modeName(landed)}）" else "") + note,
                )
                val finalMode = landed ?: next
                SeedlingClient.notifyRingMode(ctx, finalMode)
                return finalMode
            }
            XLog.w("ColorOS 内部通道 setRingerModeInternal 失败，退回公共接口")
        }
        try {
            am.ringerMode = next
            val landed = runCatching { am.ringerMode }.getOrNull()
            val degraded = landed != null && landed != next
            val tag = if (useColorOs) "ColorOS 降级公共接口" else "AOSP 标准接口"
            XLog.w(
                "响铃模式（$tag）：${modeName(current)} → " +
                    (if (degraded) "${modeName(next)}（实际落成 ${modeName(landed)}）" else modeName(next)) +
                    note,
            )
            val finalMode = landed ?: next
            if (useColorOs) {
                SeedlingClient.notifyRingMode(ctx, finalMode)
            }
            return finalMode
        } catch (e: Throwable) {
            XLog.w(
                "切换响铃模式失败（${if (useColorOs) "内部与公共接口均失败" else "AOSP 公共接口被拒"}）：" +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            return null
        }
    }

    /**
     * 走 `AudioService.setRingerModeInternal(int)`（`@hide`，反射）。
     *
     * 返回是否确实调进去了 —— 方法不存在或调用抛异常都返回 false，由 applyMode 决定是否退回公共接口。
     * 方法查找走 `Reflect.method`（自带缓存，每次按键不会重复反射）。
     */
    private fun setRingerModeInternal(
        am: AudioManager,
        mode: Int,
    ): Boolean {
        val method =
            Reflect.method(
                AudioManager::class.java,
                "setRingerModeInternal",
                Int::class.javaPrimitiveType!!,
            ) ?: run {
                XLog.w("AudioManager.setRingerModeInternal 没找到，退回公共接口")
                return false
            }
        return try {
            method.invoke(am, mode)
            true
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 服务端抛的异常包在 InvocationTargetException 里 —— 只打外层等于没打
            XLog.w("setRingerModeInternal 被拒：${e.cause?.javaClass?.name}: ${e.cause?.message}")
            false
        } catch (e: Throwable) {
            XLog.w("调 setRingerModeInternal 失败：${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** 响铃模式的可读名（日志用）。 */
    private fun modeName(mode: Int): String =
        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> "静音"
            AudioManager.RINGER_MODE_VIBRATE -> "振动"
            AudioManager.RINGER_MODE_NORMAL -> "响铃"
            else -> "未知($mode)"
        }

    private companion object {
        /**
         * 响铃档位在 `Settings.Global` 里的键 —— **AOSP 标准键**（不是 ColorOS 私有）。
         *
         *  只在「ColorOS 模式且内部通道读不到」时用它兜底：`AudioService` 内部档位那条反射
         *  在某些 ROM 上取不到，而这个键与它是同一个真值的另一份落点。换 ROM 时先回查它。
         */
        const val MODE_RINGER_KEY = "mode_ringer"
    }
}
