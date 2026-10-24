package com.lookie.opluskey.inject

import android.view.KeyEvent
import com.lookie.opluskey.core.log.XLog
import io.github.libxposed.api.XposedInterface

/**
 * ROM 自己判的「电源 + 音量」组合键 —— 两颗键都归模块接管时，把这次判定吞掉。
 *
 *  为什么要在输入层再拦一次：侧键接管点落在 policy 的策略类上，而组合键在**输入层**
 *  （`com.android.server.input` 那一层）就判完并派发下去了，比 policy 早。policy 上吞掉的
 *  那一下对它不可见 —— 表现就是「两颗键都加到自定义按键里了，一起按还是截图」。
 *
 *  判据只有一条：**这一组里的两颗键都归模块管**（都在配置里、且都开着接管）。只接管其中一颗时
 *  不动 —— 另一颗还在 ROM 手里，它的组合该由 ROM 说了算。
 */
internal object ChordGuard {
    /** ROM 会判的两组：电源 + 音量下（截图）、电源 + 音量上（可配的那一档）。 */
    private val PAIRS =
        listOf(
            intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_DOWN),
            intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP),
        )

    /**
     * 一次 `handleKeyGesture` 调用。
     *
     * @return `null` = 吞掉（不执行 ROM 那套）；其余 = 原样放行
     */
    fun handle(chain: XposedInterface.Chain): Any? {
        //  键码表是这一层的唯一入口判据：拿不到就当不是组合键，原样放行。
        val codes = firstIntArray(chain.args) ?: return chain.proceed()
        val pair = PAIRS.firstOrNull { p -> p.all { it in codes } } ?: return chain.proceed()
        val owned =
            try {
                owns(pair)
            } catch (e: Throwable) {
                //  读配置失败按「不归模块管」处理：这一下交给 ROM，与「读不到配置就走默认值」同一口径。
                XLog.w("组合键：判定失败（放行给 ROM）：${e.javaClass.simpleName}", e)
                false
            }
        if (!owned) return chain.proceed()
        //  判据行：这一行出现就说明「组合键确实走到输入层」且被拦下了 —— 排查时先看它。
        XLog.w("组合键（${pair.joinToString("+")}）：两颗键都归模块接管 ⇒ 吞掉 ROM 的这次判定")
        return null
    }

    /** 这一组键是不是都归模块管 —— 都在配置里、且都开着接管。 */
    private fun owns(pair: IntArray): Boolean {
        val config = ConfigChannel.config
        return pair.all { code ->
            val key = config.keyForRawCode(code) ?: return@all false
            //  `enabledOf` 里已含主开关：总闸关着时一律 false，行为与原生一致。
            ConfigChannel.enabledOf(key.id)
        }
    }
}

/** 从实参里挑出第一个 `IntArray`（键码表）—— 热路径，不要 `filterIsInstance`。 */
private fun firstIntArray(args: List<Any?>): IntArray? {
    for (a in args) if (a is IntArray) return a
    return null
}
