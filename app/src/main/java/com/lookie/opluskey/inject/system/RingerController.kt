package com.lookie.opluskey.inject.system

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
) {

    /** 免打扰期间内部档位读不到时的兜底：自己记的上一档（只在写入成功时按**实际落到的档位**更新）。 */
    @Volatile private var rememberedMode: Int = AudioManager.RINGER_MODE_NORMAL

    /** 切下一档。 */
    fun cycle() {
        val ctx = context() ?: run {
            XLog.w("没有 Context，切换响铃模式跳过")
            return
        }
        try {
            val am = ctx.getSystemService(AudioManager::class.java) ?: run {
                XLog.w("拿不到 AudioManager，切换响铃模式跳过")
                return
            }
            val zen = isZenOn(ctx)
            val current = readCurrentMode(ctx, am, zen)
            val next = when (current) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL // 静音（或未知）→ 响铃
            }
            val landed = applyMode(am, current, next, zen)
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
        val ctx = context() ?: run {
            XLog.w("没有 Context，设置响铃模式跳过")
            return
        }
        val target = when (mode) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> {
                XLog.w("不认识的响铃模式（忽略）：$mode")
                return
            }
        }
        try {
            val am = ctx.getSystemService(AudioManager::class.java) ?: run {
                XLog.w("拿不到 AudioManager，设置响铃模式跳过")
                return
            }
            val zen = isZenOn(ctx)
            val current = readCurrentMode(ctx, am, zen)
            if (current == target) {
                XLog.w("响铃模式：已经是${modeName(target)}，不动")
                return
            }
            val landed = applyMode(am, current, target, zen)
            if (landed != null) rememberedMode = landed
        } catch (e: Throwable) {
            XLog.w("设置响铃模式失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 读当前档位：免打扰开着读内部档位，否则读外部视图。
     *
     * 外部视图在免打扰下被压成静音，按它推「下一档」会每次都写「响铃」。
     * 内部档位走 `AudioManager.getService()`（hidden 静态）→ binder 的 `getRingerModeInternal()`；
     * 方法不存在（ROM 差异）时退回 rememberedMode，两个都拿不到才用外部视图。
     */
    private fun readCurrentMode(ctx: Context, am: AudioManager, zen: Boolean): Int {
        if (!zen) return am.ringerMode
        val service = Reflect.callStatic(AudioManager::class.java, "getService")
        if (service != null) {
            val internal = Reflect.call(service, "getRingerModeInternal")
            if (internal is Int) return internal
        }
        XLog.w("免打扰开启且内部档位读不到，按上一档处理（${modeName(rememberedMode)}）")
        return rememberedMode
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
     * 切到 `next`，返回**实际落到的档位**（`null` = 两条件路都没成，调用方据此更新 rememberedMode）。
     *
     * 优先走内部通道 —— 那是唯一能真正切到静音档的路，也是免打扰期间唯一能落下去的路
     * （公共接口切响铃/振动会触发 zen 联动检查，宿主无「勿扰访问」授权会被 `SecurityException` 拒）。
     *
     * 返回「实际档位」而不是布尔：走公共接口时 ColorOS 会把静音档降级成振动，
     * 只看「调没调进去」会把 rememberedMode 记成与实际相反的那一档，下一次 `cycle()` 就从错的地方起步。
     */
    private fun applyMode(am: AudioManager, current: Int, next: Int, zen: Boolean): Int? {
        val note = if (zen) "（免打扰开启，写的是内部档位）" else ""
        if (setRingerModeInternal(am, next)) {
            //  内部通道也要**回读**：写进去不等于落定，而公共接口那条
            //  是回读的 —— 两条路口径不一致，而且「内部通道不会被降级」只是**推断**、不是证据。
            //  回读一次才能把 rememberedMode 记对（记错了下一次 cycle 就从错的地方起步），
            //  也才能在真机上看见它到底落成哪一档。
            val landed = runCatching { am.ringerMode }.getOrNull()
            val degraded = landed != null && landed != next
            XLog.w(
                "响铃模式：${modeName(current)} → ${modeName(next)}" +
                    (if (degraded) "（回读发现实际落成 ${modeName(landed)}）" else "") + "$note",
            )
            return landed ?: next
        }
        try {
            am.ringerMode = next
            // 写完回读：公共接口对静音档的降级（ColorOS 上是静音 → 振动）只有回读才看得见。
            val landed = runCatching { am.ringerMode }.getOrNull()
            val degraded = landed != null && landed != next
            XLog.w(
                "响铃模式：${modeName(current)} → " +
                    (if (degraded) "${modeName(next)}（实际落成 ${modeName(landed)}）" else modeName(next)) +
                    "$note（内部通道不可用，走的是公共接口；静音档会被 ColorOS 降级成振动）",
            )
            return landed ?: next
        } catch (e: Throwable) {
            XLog.w(
                "切换响铃模式失败（内部通道与公共接口都不行）：" +
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
    private fun setRingerModeInternal(am: AudioManager, mode: Int): Boolean {
        val method = Reflect.method(
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
    private fun modeName(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "静音"
        AudioManager.RINGER_MODE_VIBRATE -> "振动"
        AudioManager.RINGER_MODE_NORMAL -> "响铃"
        else -> "未知($mode)"
    }
}
