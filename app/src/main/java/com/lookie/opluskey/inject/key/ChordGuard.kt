package com.lookie.opluskey.inject.key

import android.view.KeyEvent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.inject.wire.ConfigChannel
import io.github.libxposed.api.XposedInterface

/**
 * ROM 自己判的「电源 + 音量」组合键 —— 这一对键**都归模块管**时，把这次判定吞掉。
 *
 *  为什么要在输入层再拦一次：侧键接管点落在 policy 的策略类上，而组合键在**输入层**
 *  （`com.android.server.input` 那一层）就判完并派发下去了，比 policy 早。policy 上吞掉的
 *  那一下对它不可见 —— 表现就是「两颗键都加到自定义按键里了，一起按还是截图」。
 *
 * ## 判据：**接管即归我们**（用户口径）
 *
 *  两颗键都在配置里、且都开着接管 ⇒ 吞。不再额外要求「这一对恰好配了一档组合」：
 *  「接管」这个动作的语义就是「这套键归模块」，系统那一下该让位 —— 接管了却还让 ROM 截一张图，
 *  用户读到的是「接管没生效」。
 *
 *  想让系统那一下照常发生，唯一的出口是把这两颗键里的一颗停用（或关掉总闸）——
 *  「这一档没绑动作」不算出口：留空只表示「这一档什么都不做」，不代表这一次要交回 ROM。
 *
 *  返回值按原方法的**声明返回类型**回填（[swallowValueOf]）：`void` 用 `null`（框架里 `null`
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
     * @return 吞掉时按 [swallowValueOf] 回填；不吞时原样放行
     */
    fun handle(chain: XposedInterface.Chain): Any? {
        //  键码表是这一层的唯一入口判据：两种参数形态都要认（见 [codesOf]），一只都取不到就当
        //  不是组合键，原样放行。
        val codes = codesOf(chain.args)
        if (codes == null) {
            val method = (chain.executable as? java.lang.reflect.Method)?.toGenericString()
            XLog.w("组合键手势：两种参数形态都取不到键码（$method）")
            return chain.proceed()
        }
        //  判据级：ROM 这一次交给 `handleKeyGesture` 的键码表原样打出来。
        //  下面那条 pair 判据要求「两颗键都在表里」；机型不同、ROM 改版时这一层给出的表也
        //  不一样（有的只报当前按下的那一颗），那时整组都拦不到 —— 这一行是认出那种形态的
        //  唯一证据，排查「某一组组合不生效、另一组正常」时先看它。
        XLog.w("组合键手势：keycodes=[${codes.joinToString(",")}]")
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
        XLog.w("组合键（${pair.joinToString("+")}）：两颗键都归模块管 ⇒ 吞掉 ROM 的这次判定")
        return swallowValueOf(chain, keys.joinToString("+") { it.id.id })
    }

    /**
     * 这一对键是否**都归模块管** —— 是则返回它们对应的键配置。
     *
     *  「都归模块管」= 都在配置里、且都开着接管（`enabledOf` 里已含主开关：总闸关着时一律 false，
     *  行为与原生一致）。这一条成立就吞，不再看「有没有配这一组组合」—— 理由见类注释。
     *
     *  组合那一档**有没有绑动作不参与判断**：配了组合就是「这两颗键交给模块」，
     *  某一格留空只表示「这一档什么都不做」，不该把这一次又交回 ROM。
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
        return found
    }
}

/**
 * 「吞掉这一次调用」时该返回什么 —— **按原方法的声明返回类型**给，不能一律 `null`。
 *
 *  见 `Reflect.zeroOf` 的注释：`null` 喂给原始类型返回点就是拆箱 NPE。`void` 那一档
 *  `zeroOf` 本来就返回 `null`，正是框架里「吞掉」的含义；`boolean` 单独给 `true`：
 *  我们对这一下的意图就是「已消费」，给 `false` 会让上游把它当成「没处理」继续派发，
 *  等于白吞一次并丢掉这次判定。
 *
 *  这一处是**唯一**口径：`ChordGuard` 与 `KeyHooks.onIntercept` 都走它，两处各写一份时
 *  曾出现「一处返回 null、一处回填零值」，其中一处必然是错的。
 *
 *  @param label 只进日志：这一次吞掉的是哪条路径（组合键 / 侧键接管点）
 */
internal fun swallowValueOf(
    chain: XposedInterface.Chain,
    label: String,
): Any? {
    val type = (chain.executable as? java.lang.reflect.Method)?.returnType
    val value =
        when (type) {
            java.lang.Boolean.TYPE -> true
            else -> Reflect.zeroOf(type)
        }
    //  一次按压一条：这一行同时是「回填成什么」的唯一凭据（返回类型对不上时先看这里）。
    XLog.d("$label：按返回类型 $type 回填 $value")
    return value
}

/** 从实参里挑出第一个 `IntArray`（键码表）—— 热路径，不要 `filterIsInstance`。 */
private fun firstIntArray(args: List<Any?>): IntArray? {
    for (a in args) if (a is IntArray) return a
    return null
}

/**
 * 从实参里取键码表 —— **两种参数形态都要认**。
 *
 *  · 直接给表：`handleKeyGesture(int[] keycodes, …)`（或某个带 `int[]` 的重载）；
 *  · 给事件对象：`handleKeyGesture(AidlKeyGestureEvent, IBinder)` —— 那张表在事件的
 *    `keycodes` 字段里（AOSP 的 `android.hardware.input.AidlKeyGestureEvent` 就是这一格）。
 *
 *  为什么必须认第二种：真机上两个重载**交替**被调（日志里 `keycodes=[…]` 与「取不到键码」
 *  成对出现），只认 `int[]` 的话，事件对象那一路会把判定整支交给 ROM —— 表现就是
 *  「两颗键都接管了，一起按还是截图」。
 *
 *  只对名字里带 `KeyGesture` 的实参尝试读字段：同一份实参表里还有 `IBinder` 之类，
 *  对它们逐个反射只会刷一串「找不到字段」。
 */
private fun codesOf(args: List<Any?>): IntArray? {
    firstIntArray(args)?.let { return it }
    for (a in args) {
        if (a == null || a is IntArray) continue
        if (!a.javaClass.simpleName.contains("KeyGesture")) continue
        (Reflect.fieldValue(a, a.javaClass, "keycodes") as? IntArray)?.let { return it }
    }
    return null
}
