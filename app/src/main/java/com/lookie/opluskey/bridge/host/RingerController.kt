package com.lookie.opluskey.bridge.host

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Process
import android.os.VibrationEffect
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 响铃模式（`keyevent:KEYCODE_VOLUME_MUTE` / 164）—— 响铃 → 振动 → 静音 → 响铃。
 *
 *  落档一律走内部通道（`AudioService.setRingerModeInternal`，反射），公共接口只作退路：
 *  这台 ROM 把公共接口的静音请求交给 delegate 改写（实测会变成「打开手动免打扰」），
 *  只有内部通道三档都落得下去。「使用 ColorOS 接口」开关只管切完那一下的触觉与流体云。
 *
 * 免打扰（Zen Mode）把外部档位视图压成静音（`am.ringerMode` 恒读静音），但内部档位仍归
 * AudioService 管：内部通道写入不被 zen 拦、也不联动关 zen。所以读档位同样走内部通道，
 * 读不到时退回自己记的上一档（rememberedMode）。
 */
internal class RingerController(
    private val context: () -> Context?,
    /** 「使用 ColorOS 接口」这一档由装配处传入（见 `ActionRunner`）—— 本类不自己去读配置。 */
    private val isColorOsApi: () -> Boolean,
    /**
     * 「振动反馈」**这一档生效值** —— 同样由装配处传入，只决定切完档要不要振那一下。
     *
     *  注意「这一档」：那个开关在「独立设置」里可以每颗键 / 每组组合各设一份
     *  （`Config.effectiveFor(owner).vibrate`），所以装配处每次执行前写一格、这里读那一格
     *  （`ActionRunner.runVibrate`）—— 直接读 `global.vibrate` 会让独立设置对它失效。
     */
    private val vibrate: () -> Boolean = { true },
) {
    /** 免打扰期间内部档位读不到时的兜底：自己记的上一档（只在写入成功时按**实际落到的档位**更新）。 */
    @Volatile
    private var rememberedMode: Int = AudioManager.RINGER_MODE_NORMAL

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
                //  **档位不动，但那颗胶囊还是要弹**：流体云是 ColorOS 接口弹的（[SeedlingClient]），
                //  不是我们执行动作时的通用提示 —— 只有这里主动发一条它才出来。已经在目标档时
                //  系统自身不会弹，用户按下去就只剩「什么都看不见」，所以补发同一条事件。
                if (isColorOsApi()) {
                    SeedlingClient.notifyRingMode(ctx, target)
                    //  **档位没变也要有那一下触觉**：ColorOS 接口开着时调用方不再放通用振动
                    //  （见 [hapticOnSwitched] 的注释），这里不补就变成「按了、弹了胶囊、
                    //  手上什么都没有」—— 与「胶囊照样弹」是同一条理由。
                    hapticOnSwitched(ctx, target)
                }
                return
            }
            val landed = applyMode(am, current, target, zen, ctx)
            if (landed != null) rememberedMode = landed
        } catch (e: Throwable) {
            XLog.w("设置响铃模式失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 切完档那一下触觉 —— **只在「使用 ColorOS 接口」开着时**放（见 [RingerHaptics]）。
     *
     *  看的是那个开关，不是这一次实际走了哪条通道（通道一律内部优先）。关掉时什么都不做：
     *  那一下由调用方在动作执行前放通用振动（`host/GestureExec` 的 `haptic`），这里再放就是两下。
     */
    private fun hapticOnSwitched(
        ctx: Context,
        mode: Int,
    ) {
        if (!isColorOsApi()) {
            XLog.w("响铃触觉：跳过（未使用 ColorOS 接口）")
            return
        }
        if (!vibrate()) {
            XLog.w("响铃触觉：跳过（振动反馈关着）")
            return
        }
        //  `false` = 两条马达通道在这台机器上都没放出去（非 ColorOS / 没有线性马达 / 服务拿不到）：
        //  这一档就**没有触觉**了。只记一行，不补普通振动 —— 那会与「系统自己切档那一下」重复，
        //  而这正是 [RingerHaptics] 存在的意义（复刻系统那一下，而不是多振一下）。
        if (!RingerHaptics.play(ctx, mode)) {
            XLog.w("响铃触觉：两条通道都放不出来（mode=$mode），这一下没有触觉")
        }
    }

    /**
     * 读当前档位 —— **内部通道优先，与写入那条路保持一致**：
     * 先 `AudioService.getRingerModeInternal()`（反射），读不到用 `Settings.Global.mode_ringer`，
     * 两者都没有才退回公共视图 `am.ringerMode`。
     *
     *  为什么不在免打扰关闭时就信任 `am.ringerMode`：它的写与读必须同一条通道，否则「写进去的」
     *  与「回读到的」可能不是一个东西（免打扰下公共视图恒读静音），`rememberedMode` 跟着记错，
     *  下一次 `cycle()` 就从错的档位起步。
     */
    private fun readCurrentMode(
        am: AudioManager,
        zen: Boolean,
        ctx: Context,
    ): Int {
        //  两条通道的读法只有一份（[readModeOrNull]）：条件执行判 `ringer:*` 也读它 ——
        //  只在一边读公共视图，免打扰下就会「动作按内部档位切、条件按静音判」，两边一正一反。
        readModeOrNull(ctx)?.let { return it }
        XLog.w(
            "响铃档位：内部通道与 Settings.Global.$MODE_RINGER_KEY 都没读到" +
                (if (zen) "，免打扰开着 ⇒ 按上一档处理" else "，改读公共视图"),
        )
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
     *  内部通道 `AudioService.setRingerModeInternal` **永远先走**：公共接口那条要把请求交给
     *  `mRingerModeDelegate` 改写（这台 ROM 上是把静音变成「开手动免打扰」），只有内部通道
     *  才三档都落得下去。「使用 ColorOS 接口」开关管的是切完那一下的触觉与流体云，
     *  **不决定走哪条通道** —— 通道选择错了会让「切到静音」变成「进免打扰」。
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
        if (setRingerModeInternal(am, next)) {
            val landed = runCatching { readCurrentMode(am, zen, ctx) }.getOrNull()
            val degraded = landed != null && landed != next
            XLog.w(
                "响铃模式（内部通道）：${modeName(current)} → ${modeName(next)}" +
                    (if (degraded) "（回读发现实际落成 ${modeName(landed)}）" else "") + note,
            )
            val finalMode = landed ?: next
            if (useColorOs) {
                SeedlingClient.notifyRingMode(ctx, finalMode)
                hapticOnSwitched(ctx, finalMode)
            }
            return finalMode
        }
        XLog.w("内部通道 setRingerModeInternal 失败，退回公共接口")
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
                hapticOnSwitched(ctx, finalMode)
            }
            return finalMode
        } catch (e: Throwable) {
            XLog.w(
                "切换响铃模式失败（内部与公共接口均失败）：" +
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

    companion object {
        /**
         * 响铃档位在 `Settings.Global` 里的键 —— **AOSP 标准键**（不是 ColorOS 私有）。
         *
         *  只在「ColorOS 模式且内部通道读不到」时用它兜底：`AudioService` 内部档位那条反射
         *  在某些 ROM 上取不到，而这个键与它是同一个真值的另一份落点。换 ROM 时先回查它。
         */
        private const val MODE_RINGER_KEY = "mode_ringer"

        /**
         * 档位的**判据口径**读法：内部通道 → `Settings.Global.mode_ringer` → `null`。
         *
         *  两份读者：切换档位那条路（[readCurrentMode] 读不到时再按 zen / 公共视图兜底），
         *  以及条件执行（`ConditionProbe.ringerMatches` 读不到就判「条件不成立」）。
         *  合一份的理由：公共视图在免打扰下**恒读静音** —— 条件那边照它判，会把「响铃 / 振动」
         *  判成「静音」，与动作实际落的档位正好相反。
         *
         *  @return `null` = 两条通道都读不到（判不出；**不拿公共视图凑一个值**）
         */
        fun readModeOrNull(ctx: Context?): Int? {
            val context = ctx ?: return null
            val service = Reflect.callStatic(AudioManager::class.java, "getService")
            val internal = service?.let { Reflect.call(it, "getRingerModeInternal") }
            if (internal is Int) return internal
            // 两条反射在部分 ROM 上取不到 —— 说出来：否则「读到的档位为什么是这个」
            //  在日志里没有任何依据，而下一档是拿它推出来的。
            XLog.w("响铃档位：内部通道（AudioService.getRingerModeInternal）读不到，改读 Settings.Global")
            val settingsMode =
                runCatching {
                    android.provider.Settings.Global
                        .getInt(context.contentResolver, MODE_RINGER_KEY, -1)
                }.getOrDefault(-1)
            if (settingsMode in 0..2) {
                XLog.w("响铃档位：用 Settings.Global.$MODE_RINGER_KEY=$settingsMode 兜底")
                return settingsMode
            }
            return null
        }
    }
}

/**
 * 「切换响铃状态」那一下触觉 —— 与**系统侧键切档时同一份效果**。
 *
 * ## 档位 → 效果号（见 `docs/device/actions/audio.md` 二十七）
 *
 *  · 切到**振动** → `430`（两下）
 *  · 切到**响铃** → `308`（低沉那一下）
 *  · 切到**静音** → `0`（落到马达上是 1 号，45ms 的轻一下）
 *
 *  静音那一档**不是**「不振」，也**不是**响铃那个号：系统侧键切档（`AudioServiceExtImpl.vibrateMM`）
 *  对静音档取的就是 `0`，再交给 `createWaveformEffect` 解析成轻击。**三段式滑块那条路不一样**
 *  （`AlertSliderVibratorPolicy.setDown` 用 `308`），两条路别互相推 —— 本模块跟的是侧键。
 *
 * ## 强度也要跟系统一样
 *
 *  系统那条调用是 `createWaveformEffect(效果号, -1, -1)` + 带 `VibrationAttributes` 的
 *  `vibrate(uid, 包名, effect, tag, attrs)`：`-1` 让强度走马达默认档，dumpsys 里是 `2000`。
 *  只走 `vibrate(WaveformEffect)` 那条（不传强度与 attributes）会落到最大值 `2400`，听感比系统重一截。
 *
 *  这张表按 smali 偏移推过两版都错，`dumpsys vibrator_manager` 也只答了「哪个号被放了」，
 *  答不了「这个号属于哪一档」—— 而 `settings get global mode_ringer` 比 AudioService 的内部档位
 *  慢一拍，拿它对齐会把每一次效果都算到**前一步**上。判据只有一条：让用户听完说哪一档对不上。
 *
 *  再往下是 `OplusVibrateHelper.vibrate(tag, linearMotorEffectId, strength = -1, nomalEffectId = 5)`：
 *  有线性马达就走 `com.oplus.os.LinearmotorVibrator`，没有才退回普通 `Vibrator` +
 *  `VibrationEffect.get(5)`。这里两条都照着复刻。
 *
 * ## 为什么不直接调 SystemUI 那两个类
 *
 *  它们在 `com.android.systemui` 里（SystemUI 自己的 dex），普通进程加载不到。真正干活的是
 *  **框架里那两个公开可用的类**（`com.oplus.os.WaveformEffect` / `LinearmotorVibrator`，
 *  在 boot classpath 的 oplus-framework 里，不是 hidden API），所以这里直接对它们下手 ——
 *  效果号与调用形状照着系统切档那条路抄，语义就是同一个。
 *
 * ## 失败一律不出声
 *
 *  没有线性马达、类不在（非 ColorOS 机器）、服务拿不到 —— 都返回 `false`，由调用方决定
 *  要不要退回原来那一下普通振动。振不动绝不能影响切档本身。
 */
internal object RingerHaptics {
    /** 切到振动时的效果号（两下）。 */
    const val EFFECT_VIBRATE = 430

    /** 切到响铃时的效果号（低沉那一下）。 */
    const val EFFECT_NORMAL = 308

    /**
     * 切到静音时的效果号 —— `0` 是**系统那边的入口值**，不是「不振」：`createWaveformEffect(0, …)`
     * 落到马达上是 1 号（45ms 的轻一下），比响铃那个号轻得多。
     */
    const val EFFECT_SILENT = 0

    /**
     * 没有线性马达时的退路 —— SystemUI 那边是 `VibrationEffect.get(5)`（`@hide`），
     * 这里用等价的公开写法 `createPredefined(5)`（`EFFECT_HEAVY_CLICK`）。
     */
    private const val FALLBACK_PREDEFINED = VibrationEffect.EFFECT_HEAVY_CLICK

    /** 线性马达那个系统服务的名字（`OplusVibrateHelper` 里就是 `getSystemService("linearmotor")`）。 */
    private const val LINEAR_MOTOR_SERVICE = "linearmotor"

    private const val VIBRATION_ATTRIBUTES = "android.os.VibrationAttributes"

    /**
     * `VibrationAttributes.createForUsage(18)` —— 系统切档那条就是这么传的（`AudioServiceExtImpl`
     * 与 `AlertSliderVibratorPolicy` 都是 `const/16 18`）。
     */
    private const val VIBRATION_USAGE = 18

    /** dumpsys 里「这条是谁放的」那一栏的标签。 */
    private const val HAPTIC_TAG = "OplusKey ringerMode"

    /**
     * 切到 [mode] 之后放那一下 —— `true` = 已经放过（含「这一档本来就不振」），
     * `false` = 这台机器没有这条通道，调用方可以退回普通振动。
     *
     * @param mode `AudioManager.RINGER_MODE_*`
     */
    fun play(
        ctx: Context,
        mode: Int,
    ): Boolean {
        val effectId =
            when (mode) {
                AudioManager.RINGER_MODE_VIBRATE -> {
                    EFFECT_VIBRATE
                }

                AudioManager.RINGER_MODE_NORMAL -> {
                    EFFECT_NORMAL
                }

                // 静音是**轻一下**（系统那边传 0、落到 1 号），不是响铃那个号 —— 两者听感差很远。
                AudioManager.RINGER_MODE_SILENT -> {
                    EFFECT_SILENT
                }

                else -> {
                    XLog.w("响铃触觉：未知档位 $mode，不振")
                    return true
                }
            }
        //  两条通道是**先后**关系：线性马达那条没走通才退回普通马达。
        //  两者都没放出去时必须返回 false —— 调用方据此知道「这一下没有触觉」，
        //  而不是以为已经振过（原来无脑返回 true，等于把失败吞了）。
        val byLinear = playByLinearMotor(ctx, effectId)
        val played = if (byLinear) true else playByVibrator(ctx)
        // 判据级一行（每次切档一条，不刷屏）：档位 / 效果号 / 走的是哪条马达 ——
        //  「手感对不上」时先看这一行，别再从偏移去猜常量。
        XLog.w(
            "响铃触觉：mode=$mode 效果号=$effectId " +
                (
                    if (byLinear) {
                        "走线性马达"
                    } else if (played) {
                        "走普通马达（线性马达那条没走通）"
                    } else {
                        "两条通道都没放出去"
                    }
                ),
        )
        return played
    }

    /**
     * 线性马达那条 —— 与系统切档**同一条调用**：
     * `createWaveformEffect(效果号, -1, -1)` 拿 `VibrationEffect`，再用
     * `vibrate(uid, 包名, effect, tag, attributes)` 放出去。
     *
     *  强度那两个 `-1` 与 `attributes` 都不是装饰：系统靠它们落到马达的默认档（dumpsys 里
     *  `mEffectStrength=2000`），只走 `vibrate(WaveformEffect)` 那条会落到最大档 `2400`，
     *  同一颗键听上去比系统重一截。
     */
    private fun playByLinearMotor(
        ctx: Context,
        effectId: Int,
    ): Boolean =
        try {
            val lmv =
                ctx.getSystemService(LINEAR_MOTOR_SERVICE) ?: run {
                    XLog.w("响铃触觉：拿不到 \"$LINEAR_MOTOR_SERVICE\" 服务，改用普通马达")
                    return false
                }
            val cls = lmv.javaClass
            val intType = Int::class.javaPrimitiveType!!
            val effect =
                cls
                    .getMethod("createWaveformEffect", intType, intType, intType)
                    .invoke(lmv, effectId, -1, -1)
            val attrsClass = Class.forName(VIBRATION_ATTRIBUTES)
            val attrs = attrsClass.getMethod("createForUsage", intType).invoke(null, VIBRATION_USAGE)
            cls
                .getMethod(
                    "vibrate",
                    intType,
                    String::class.java,
                    VibrationEffect::class.java,
                    String::class.java,
                    attrsClass,
                ).invoke(lmv, Process.myUid(), ctx.opPackageName, effect, HAPTIC_TAG, attrs)
            true
        } catch (e: Throwable) {
            // 类不在 / 服务没实现 / 方法签名换了 —— 都是「这台机器没有这条通道」，交给退路
            XLog.w("响铃触觉：线性马达那条没走通（${e.javaClass.simpleName}），改用普通振动")
            false
        }

    /** 普通马达那条：SystemUI 在无线性马达时就是放一个预置效果（`get(5)`）。 */
    @Suppress("MissingPermission")
    private fun playByVibrator(ctx: Context): Boolean =
        try {
            val v = ctx.getSystemService(android.os.Vibrator::class.java) ?: return false
            v.vibrate(VibrationEffect.createPredefined(FALLBACK_PREDEFINED))
            true
        } catch (e: Throwable) {
            XLog.w("响铃触觉：普通振动也失败（忽略）：${e.javaClass.simpleName}")
            false
        }
}
