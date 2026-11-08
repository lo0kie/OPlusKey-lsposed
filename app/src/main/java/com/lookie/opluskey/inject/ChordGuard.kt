package com.lookie.opluskey.inject

import android.view.KeyEvent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.reflect.Reflect
import io.github.libxposed.api.XposedInterface

/**
 * ROM 自己判的「电源 + 音量」组合键 —— 这一对键**确实绑定了模块的组合**时，把这次判定吞掉。
 *
 *  为什么要在输入层再拦一次：侧键接管点落在 policy 的策略类上，而组合键在**输入层**
 *  （`com.android.server.input` 那一层）就判完并派发下去了，比 policy 早。policy 上吞掉的
 *  那一下对它不可见 —— 表现就是「两颗键都加到自定义按键里了，一起按还是截图」。
 *
 *  判据有两条，缺一不可：
 *  1. **这一组里的两颗键都归模块管**（都在配置里、且都开着接管）；
 *  2. **这一对键恰好绑定了某一档可用组合** —— 只满足第 1 条时不能吞：用户可能只是把电源与
 *     音量都收成了单键、根本没配这一组，那时吞掉 ROM 的判定等于**把系统截图弄没了**，
 *     而模块这边只会各执行一次单键动作。
 *
 *  返回值按原方法的**声明返回类型**回填（[swallowValue]）：`void` 用 `null`（框架里 `null`
 *  就是「吞掉」），`boolean` 按「已消费」给 `true`，其余原始类型给零值 —— 给原始类型返回点
 *  返回 `null` 会在框架侧拆箱时 NPE，把「这一下不生效」升级成 system_server 崩溃。
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
     * @return 吞掉时按 [swallowValue] 回填；不吞时原样放行
     */
    fun handle(chain: XposedInterface.Chain): Any? {
        //  键码表是这一层的唯一入口判据：拿不到就当不是组合键，原样放行。
        val codes = firstIntArray(chain.args) ?: return chain.proceed()
        val pair = PAIRS.firstOrNull { p -> p.all { it in codes } } ?: return chain.proceed()
        val keys =
            try {
                ownedPair(pair)
            } catch (e: Throwable) {
                //  读配置失败按「不归模块管」处理：这一下交给 ROM，与「读不到配置就走默认值」同一口径。
                XLog.w("组合键：判定失败（放行给 ROM）：${e.javaClass.simpleName}", e)
                null
            } ?: return chain.proceed()
        //  判据行：这一行出现就说明「组合键确实走到输入层」且被拦下了 —— 排查时先看它。
        XLog.w("组合键（${pair.joinToString("+")}）：两颗键都归模块管且已配这一组 ⇒ 吞掉 ROM 的这次判定")
        return swallowValue(chain, keys)
    }

    /**
     * 这一对键是否「都归模块管、且恰好绑定了某一档可用组合」—— 是则返回它们对应的键配置。
     *
     *  「恰好」与 `ConfigChannel.comboFor` 的「子集即算」不同（那边问的是「按下这几颗时该由
     * 哪一档接管」）：这里只有这一对键本身有没有配组合这一个问题。用子集判的话，用户把电源与
     * 音量都收成单键、又配了「音量上+音量下」这类组合时，截图那一组会被误吞。
     */
    private fun ownedPair(pair: IntArray): List<KeyConfig>? {
        val config = ConfigChannel.config
        val found = ArrayList<KeyConfig>(pair.size)
        for (code in pair) {
            val key = config.keyForRawCode(code) ?: return null
            //  `enabledOf` 里已含主开关：总闸关着时一律 false，行为与原生一致。
            if (!ConfigChannel.enabledOf(key.id)) return null
            found += key
        }
        val ids = found.mapTo(HashSet(found.size * 2)) { it.id.id }
        val combo = config.combos.firstOrNull { it.isUsable && it.keys.toSet() == ids } ?: return null
        //  **配了组合 ≠ 该吞**：这一档一个动作都没绑、而且「无操作时执行按键命令」开着时，吞下去
        //  的结果就是「系统那一下没了、模块也什么都不做」（电源 + 音量 = 系统截图，被吃掉的
        //  表现是「截图键突然不灵了」）。这种情形按「不归我们管」放行，让 ROM 自己那一下照常发生。
        return if (actsOrBlocks(config, combo)) {
            found
        } else {
            XLog.w("组合键（${ids.joinToString("+")}）：这一档没绑动作且透传开着 ⇒ 不吞，交回 ROM")
            null
        }
    }

    /**
     * 这一档凑成了到底要不要**吞掉 ROM 的判定**。
     *
     *  · 有动作可做（短按 / 长按 / 松手 / 条件分支任一）⇒ 吞，否则会和系统动作撞车；
     *  · 一个都没绑、「无操作时执行按键命令」开着 ⇒ **不吞**（让 ROM 那一下照常发生，
     *    这正是那一项对单键的语义，组合也该一致）；
     *  · 一个都没绑、那一项关着 ⇒ 吞（用户明确要这两颗键什么都不做）。
     */
    private fun actsOrBlocks(
        config: Config,
        combo: ComboKey,
    ): Boolean {
        val hasAction = combo.single.isBound || combo.long.isBound || combo.releaseBound || combo.rules.isNotEmpty()
        return hasAction || !config.comboPassthroughFor(combo)
    }

    /**
     * 吞掉时的返回值 —— **按原方法的声明返回类型**给，不能一律 `null`。
     *
     *  见 `Reflect.zeroOf` 的注释：`null` 喂给原始类型返回点就是拆箱 NPE。`void` 那一档
     *  `zeroOf` 本来就返回 `null`，正是框架里「吞掉」的含义；`boolean` 单独给 `true`：
     *  我们对这一下的意图就是「已消费」，给 `false` 会让上游把它当成「没处理」继续派发，
     *  等于白吞一次并丢掉这次判定。
     */
    private fun swallowValue(
        chain: XposedInterface.Chain,
        keys: List<KeyConfig>,
    ): Any? {
        val type = (chain.executable as? java.lang.reflect.Method)?.returnType
        val value =
            when (type) {
                java.lang.Boolean.TYPE -> true
                else -> Reflect.zeroOf(type)
            }
        //  一次按压一条：这一行同时是「回填成什么」的唯一凭据（返回类型对不上时先看这里）。
        XLog.d("组合键（${keys.joinToString("+") { it.id.id }}）：按返回类型 $type 回填 $value")
        return value
    }
}

/** 从实参里挑出第一个 `IntArray`（键码表）—— 热路径，不要 `filterIsInstance`。 */
private fun firstIntArray(args: List<Any?>): IntArray? {
    for (a in args) if (a is IntArray) return a
    return null
}
