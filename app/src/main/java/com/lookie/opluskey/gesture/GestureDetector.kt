package com.lookie.opluskey.gesture

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.Gesture
import com.lookie.opluskey.config.GlobalConfig
import com.lookie.opluskey.config.KeyConfig
import com.lookie.opluskey.config.KeyId
import java.util.concurrent.ConcurrentHashMap

/**
 * 「同一次按压的两种形态」之间的时间窗（毫秒）。
 *
 * 策略类那条路，同一次按压会给出两种形态：没有 KeyEvent 的直判与带 KeyEvent 的 DOWN/UP，
 * 两者是同一串调用、前后只差毫秒级。不能拿双击窗口（`doubleClickMs`）当这个窗口 ——
 * 那样紧接着的下一次按压会被当成同一下吞掉。一次按压本身 ≥100ms（`KeyConfig.MIN_LONG_PRESS_MS`）、
 * 两次完整按压 ≥200ms（`GlobalConfig.MIN_DOUBLE_CLICK_MS`）—— 50ms 既盖得住同一串调用，又碰不到下一次按压。
 */
private const val DIRECT_BURST_MS = 50L

/**
 * 按键的手势判定状态机 —— 本模块的核心逻辑。
 *
 * 线程归属：`interceptKeyBeforeQueueing` 由 InputReader / Dispatcher 线程调，计时器走调用方
 * 给的 `handler`（main looper）—— `Pending` 的字段可能被两条线程碰，无 `@Volatile` 也无锁
 * （`lastDirectSingleAt` 除外）；`pressAt != downAt` 那道栅栏可能读到陈旧值（偶发误判长按）。
 * 量清线程归属前不要乱加同步，也不能改成 `handler.post`（`onIntercept` 须同步返回是否接管）。
 */
internal class GestureDetector(
    private val handler: Handler,
    /**
     * 判定出一个手势就回调一次。
     *
     * 第四个参数是「长按连续触发」的重复派发标记：只有长按会重复，
     * 重复那几次不能当新的一次按键（据此跳过振动反馈）。
     */
    private val onGesture: (KeyId, Gesture, KeyConfig, Boolean) -> Unit,
    /** 需要「放行一次」时回调。 */
    private val onPassthrough: (KeyId, KeyConfig) -> Unit,
) {

    /** 一个键的待定状态。 */
    private class Pending(
        var longPressAt: Long = 0L,
        var doubleWindowAt: Long = 0L,
        var sawFirstRelease: Boolean = false,
        /**
         * 本次按下已经判过长按。
         *
         * 必须单独一个字段：长按定时器触发时会清零 `longPressAt`，用 `longPressAt == 0L` 当判据，
         * 抬手时那个判断恒为假，一次按键会既判长按又判单击。
         */
        var longPressFired: Boolean = false,
        /**
         * 本次按下已经判过双击。
         *
         * 与 longPressFired 同理、也是必须的：判双击发生在第二次按下那一刻，而第二次抬手照样会走
         * `onKeyUp`，没有这个标记就会再开一个单击窗口、把双击变成「双击 + 单击」。
         */
        var doubleFired: Boolean = false,
        /**
         * 本次按下的时刻（`onKeyDown` 里写、`reset` 里清）。
         *
         * 用来判「这一下是不是已经被直判过了」—— 直判与 DOWN/UP 是同一次按压的两种形态。
         * 必须是时刻、不能只用一个布尔：布尔会被上一次按压的直判污染。
         */
        var pressAt: Long = 0L,
        /** 「长按连续触发」正在跑的重复任务（抬手 / 重置时取消）。 */
        var repeatRunnable: Runnable? = null,
    )

    private val pendingByKey = ConcurrentHashMap<KeyId, Pending>()

    /**
     * 最近一次直接判单击（`fireSingleDirect`）的时刻，
     * 用来压掉同一次物理按压被转发两遍造成的重复派发。
     */
    @Volatile
    private var lastDirectSingleAt = 0L

    /**
     * 取（必要时建）某个键的待定状态。
     *
     * 用 `computeIfAbsent` 而不是 Kotlin 的 `getOrPut`：后者是「先取后写」，
     * 并发首按（两条线程同时进）会各建一个 `Pending`、先写进去的那份状态被丢掉。
     */
    private fun pending(id: KeyId): Pending = pendingByKey.computeIfAbsent(id) { Pending() }

    /**
     * 双击窗口长度，由调用方每次按键前按当前配置写入 —— 改「单击延迟」立刻生效，不用重启。
     */
    @Volatile
    var doubleClickMs: Int = 300

    /**
     * 双击为「无操作」时立即触发单击（`GlobalConfig.instantSingle`）。
     *
     * 与 `doubleClickMs` 同一条路，每次按键前按当前配置写入；开着时抬手当场判单击、不等双击窗口。
     */
    @Volatile
    var instantSingle: Boolean = GlobalConfig.DEFAULT_INSTANT_SINGLE

    /** 一次按键按下。 */
    fun onKeyDown(id: KeyId, config: KeyConfig, now: Long = SystemClock.uptimeMillis()) {
        val p = pending(id)

        // 双击窗口内又来一次按下 → 双击
        if (p.sawFirstRelease && now <= p.doubleWindowAt) {
            // 「判出手势」这几行是验收判据链的第二环（见 AGENTS.md 二、1.2）——
            // 必须是 W：注入侧的 i/d 在 LSPosed 日志页里看不到。
            XLog.w("${id.label}：双击")
            reset(id)
            // reset 会把这个标记清掉，所以在 reset 之后再置位：
            // 这一下的抬手属于双击的第二下，`onKeyUp` 要据此不再开单击窗口。
            p.doubleFired = true
            dispatch(id, Gesture.DOUBLE, config, repeat = false)
            return
        }

        p.sawFirstRelease = false
        p.longPressFired = false
        p.doubleFired = false
        p.longPressAt = now + config.longPressMs
        p.doubleWindowAt = 0L
        // 记下这一下是什么时候按下的 —— onKeyUp 的「立即触发单击」靠它认领同一次按压的直判
        p.pressAt = now

        scheduleLongPress(id, config)
    }

    /** 一次按键抬起。 */
    fun onKeyUp(id: KeyId, config: KeyConfig, now: Long = SystemClock.uptimeMillis()) {
        val p = pending(id)

        // 长按已在按下期间触发过 —— 抬手不再重复判单击
        //（判据是 longPressFired，不是 longPressAt：后者已被长按定时器清零）
        if (p.longPressFired) {
            reset(id)
            return
        }

        // 双击已经判过 —— 这次抬手是双击的第二下，不能再开一个单击窗口
        //（否则 `doubleClickMs` 之后会补一次单击，双击变成「双击 + 单击」两下）
        if (p.doubleFired) {
            reset(id)
            return
        }

        // 「双击 = 无操作」且开了「立即触发单击」⇒ 不必再等双击窗口：抬手当场判单击。
        // 代价：不等就没法再判双击 —— 连按两下会变成两次单击（关着时第二下被吞掉）。
        // 两种语义都说得通，由配置项 instantSingle 决定。
        if (instantSingle && config.actionOf(Gesture.DOUBLE) is Action.None) {
            // 这一下是不是已经被直判过了（同一次按压被转发两遍）：
            // 记到了按下时刻 ⇒ 判「按下那一刻前后 DIRECT_BURST_MS 之内有没有直判」，用本次按下当锚点，
            //   才不会把上一次按压的直判算到这一下头上；
            // 没记到按下时刻（只收到抬手的怪形态）⇒ 退回「刚刚这几毫秒内有没有直判」。
            val alreadyDispatched = if (p.pressAt != 0L) {
                lastDirectSingleAt >= p.pressAt - DIRECT_BURST_MS
            } else {
                now - lastDirectSingleAt < DIRECT_BURST_MS
            }
            // reset 会取消那个还挂着的长按计时器 —— 不取消的话它到点还会再判一次长按
            reset(id)
            if (alreadyDispatched) {
                XLog.d("${id.label}：这次按压已直判过单击，不再补判（双击为无操作）")
                return
            }
            XLog.w("${id.label}：单击（双击为无操作 ⇒ 不等双击窗口）")
            // 与 fireSingleDirect 同一笔账：这一下已经派发过单击了，
            // 紧接着那次 DOWN/UP（同一次按压被转发两遍时）与长按计时器都要据此让路。
            lastDirectSingleAt = now
            dispatch(id, Gesture.SINGLE, config, repeat = false)
            return
        }

        p.sawFirstRelease = true
        p.doubleWindowAt = now + doubleClickMs
        scheduleDoubleWindow(id, config)
    }

    /**
     * 直接判一次单击（不走 DOWN/UP 状态机）—— 给策略类路径用：那条路拿到的可能只是
     * 「执行侧键动作」的一次调用，没有成对的 DOWN/UP 事件。
     *
     * 有 DOWN/UP 的版本（参数里带 `KeyEvent`）不要用这个，否则一次按键会被判两次。
     *
     * 当前无调用方：唯一那个调用点（`SideKeyInterceptor` 拿不到 `KeyEvent` 的分支）已改为放行
     * —— 拿不到 `KeyEvent` 无法校验是不是侧键，不该吞键。留着以防那条分支恢复。
     */
    fun fireSingleDirect(id: KeyId, config: KeyConfig) {
        // 同一次物理按压可能既走 DOWN/UP、又走这条直判（策略类那条路会给出两种形态）。
        // 先 reset 才能取消已排上的长按计时器 —— 否则 250ms 后照样触发，变成「单击 + 长按」。
        reset(id)
        // 记一笔：紧接着那次 DOWN/UP 若也转来了，scheduleDoubleWindow 要据此不再补一次单击
        lastDirectSingleAt = SystemClock.uptimeMillis()
        dispatch(id, Gesture.SINGLE, config, repeat = false)
    }

    /** 清空某个键的状态与计时器（含「长按连续触发」正在跑的重复任务）。 */
    fun reset(id: KeyId) {
        val p = pending(id)
        p.repeatRunnable?.let { handler.removeCallbacks(it) }
        p.repeatRunnable = null
        p.longPressAt = 0L
        p.doubleWindowAt = 0L
        p.sawFirstRelease = false
        p.longPressFired = false
        p.doubleFired = false
        p.pressAt = 0L
    }

    private fun scheduleLongPress(id: KeyId, config: KeyConfig) {
        // 这一枪属于哪一次按下：`pressAt` 就是那一次按下的时刻（`onKeyDown` 写、`reset` 清）。
        // 到点时它变了（中途又按过一次）或清零了（抬手 / 重置过）⇒ 这一枪作废；
        // 否则第 1 次排下的枪会在第 N 次按住期间醒来，凭空判出一次长按。
        val downAt = pending(id).pressAt
        handler.postDelayed(
            {
                val p = pending(id)
                if (p.pressAt != downAt) {
                    XLog.d("${id.label}：这一枪属于上一次按下（已作废），不判长按")
                    return@postDelayed
                }
                // 到点了还没抬手 → 长按。
                // 若转发顺序是先 SINGLE 后 DOWN，那个 DOWN 会把 `longPressAt` 重新排上，
                // 250ms 后照样判长按 ⇒ 又是两个动作。
                val directJustFired =
                    SystemClock.uptimeMillis() - lastDirectSingleAt < config.longPressMs
                if (p.longPressAt != 0L && !p.sawFirstRelease && !directJustFired) {
                    XLog.w("${id.label}：长按")
                    p.longPressAt = 0L
                    p.longPressFired = true
                    dispatch(id, Gesture.LONG, config, repeat = false)
                    // 「长按连续触发」：按住不放就一直重复（松手由 reset 停掉）。
                    // 多条命令（`cmd:<名字>` 指串行命令）反复重放没意义 —— 界面长按 tab 也按同一条判定隐藏。
                    // `longIsSerial` 来自广播：注入侧读不到命令库文件，自己算必然恒 false。
                    if (config.longRepeat && !config.longIsSerial) {
                        scheduleRepeat(id, config)
                    }
                }
            },
            config.longPressMs.toLong(),
        )
    }

    /** 「长按连续触发」（`plus_long_repeat`）的重复派发。 */
    private fun scheduleRepeat(id: KeyId, config: KeyConfig) {
        val interval = config.longRepeatIntervalMs
            .coerceAtLeast(KeyConfig.MIN_REPEAT_INTERVAL_MS)
            .toLong()
        val runnable = Runnable {
            try {
                val p = pending(id)
                // 松手 / 重置过了 → 停
                if (!p.longPressFired || p.sawFirstRelease) {
                    p.repeatRunnable = null
                    return@Runnable
                }
                dispatch(id, Gesture.LONG, config, repeat = true)
                scheduleRepeat(id, config)
            } catch (e: Throwable) {
                XLog.w("长按连续触发派发失败（已停止）：${id.label}", e)
            }
        }
        pending(id).repeatRunnable = runnable
        handler.postDelayed(runnable, interval)
    }

    /** 抬手后开一个双击窗口：窗口内没有第二次按下就补一次单击。 */
    private fun scheduleDoubleWindow(id: KeyId, config: KeyConfig) {
        handler.postDelayed(
            {
                val p = pending(id)
                // 窗口到了、期间没有第二次按下 → 单击
                if (p.sawFirstRelease && p.doubleWindowAt != 0L) {
                    // 这次按压已经由 fireSingleDirect 直判过了（同一次按压被转发两遍）
                    // ⇒ 不再补一次单击，否则一次按压会执行两个单击动作。
                    if (SystemClock.uptimeMillis() - lastDirectSingleAt < doubleClickMs) {
                        XLog.d("${id.label}：这次按压已直判过单击，跳过补判")
                        p.sawFirstRelease = false
                        p.doubleWindowAt = 0L
                    } else {
                        XLog.w("${id.label}：单击")
                        p.sawFirstRelease = false
                        p.doubleWindowAt = 0L
                        dispatch(id, Gesture.SINGLE, config, repeat = false)
                    }
                }
            },
            doubleClickMs.toLong(),
        )
    }

    /**
     * 执行一个手势对应的动作。
     *
     * `native` / `passthrough` 都归到「放行」：判定走到这里说明这个键确实被拦了，补一次放行。
     * `repeat` 只影响「长按连续触发」的重复派发，由 `onGesture` 一路带到执行侧。
     */
    private fun dispatch(id: KeyId, gesture: Gesture, config: KeyConfig, repeat: Boolean) {
        when (config.actionOf(gesture)) {
            is Action.Native, is Action.Passthrough -> onPassthrough(id, config)
            else -> onGesture(id, gesture, config, repeat)
        }
    }
}
