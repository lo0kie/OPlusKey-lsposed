package com.lookie.opluskey.bridge.host

import android.content.Context
import android.media.AudioManager
import android.os.Process
import android.os.VibrationEffect
import com.lookie.opluskey.core.log.XLog

/**
 * 「切换响铃状态」那一下触觉 —— 与**系统侧键切档时同一份效果**。
 *
 * ## 档位 → 效果号（见 `docs/device-findings.md` 二十七）
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
