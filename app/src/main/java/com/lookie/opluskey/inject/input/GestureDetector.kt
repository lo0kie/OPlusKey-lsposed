package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import java.util.concurrent.ConcurrentHashMap

/**
 * 「同一次按压的两种形态」之间的时间窗（毫秒）。
 *
 * 策略类那条路，同一次按压会给出两种形态：没有 KeyEvent 的直判与带 KeyEvent 的 DOWN/UP，
 * 两者是同一串调用、前后只差毫秒级。不能拿双击窗口（`doubleClickMs`）当这个窗口 ——
 * 那样紧接着的下一次按压会被当成同一下吞掉。而两次按压之间至少隔着一次抬手：
 * 两者的下限里更小的是 100ms（双击窗口；长按阈值是 200ms）—— 50ms 既盖得住同一串调用，
 * 又碰不到下一次按压。
 */
private const val DIRECT_BURST_MS = 50L

/**
 * 「长按连续触发」的外部终止上限（毫秒）：距按下超过它仍未见抬手，视为 UP 已丢
 * （锁屏打断 / 非FLAG_CANCELED 的丢弃），强制停掉自循环派发链。
 */
private const val REPEAT_MAX_MS = 30_000L

/**
 * 按键的手势判定状态机 —— 本模块的核心逻辑。
 *
 * 线程归属：`interceptKeyBeforeQueueing` 由 InputReader / Dispatcher 线程调，计时器走调用方
 * 给的 `handler`（main looper）—— `Pending` 的字段被这两条线程**都写也都读**（输入线程写
 * `sawFirstRelease` / `pressAt`，主 looper 那一枪写 `longPressAt` / `longPressFired`）。所以：
 *  · 跨线程的字段一律 `@Volatile`，**没有例外**：`doubleFired` 的写者除了输入线程，
 *    还有 [reset] 的两条外部调用路径 —— 主 looper 的连发任务、以及配置广播线程
 *    （`SideKeyInterceptor.cancelPending`）。它不参与「先判长按还是先判双击」那种读改写竞态，
 *    但清零必须让输入线程看得见，否则上一按的标记会漏到下一按。
 *  · 抬手时**摘掉**长按那一枪（[cancelLongPressShot]）：到点读一眼 `sawFirstRelease` 是「再看一眼」，
 *    摘枪是「到点之前就没了」，后者与线程调度无关；
 *  · 但 `check → act` 那几段仍无锁，极端竞态下「既判长按又判单击」只是变小、没消失 ——
 *    彻底收口要把状态机归到一个线程上。也不能改用 `handler.post`（`onIntercept` 须同步返回是否接管）。
 */
internal class GestureDetector(
    private val handler: Handler,
    /**
     * 每判出一个手势回调一次（链路自检记「最后一次判出手势的时刻」）。
     *
     * 由装配方注入（`KeyHooks` 装配时传 `noteGesture`），判定器自己不认识 `KeyHooks` ——
     * 直接回写那个单例会形成 `inject.input` 与 `inject` 的包级环。
     */
    private val onGestureNoted: () -> Unit,
    /**
     * 判定出一个手势就回调一次。
     *
     * 第四个参数是「长按连续触发」的重复派发标记：只有长按会重复，
     * 重复那几次不能当新的一次按键（据此跳过振动反馈）。
     */
    private val onGesture: (KeyId, Gesture, KeyConfig, Boolean) -> Unit,
) {
    /**
     * 一个键的待定状态 —— 字段被两条线程读写，跨线程的一律 `@Volatile`（见类注释）。
     *
     *  `@Suppress("LongParameterList")`：这不是「参数太多、该拆对象」那种函数，而是一份**状态表**
     *  ——每一格都是独立的一个状态位 / 一个待摘的定时器句柄，拆成子对象只是把它们挪个地方，
     *  判定处还得一层层解引用。参数个数就是状态位数。
     */
    @Suppress("LongParameterList")
    private class Pending(
        @Volatile var longPressAt: Long = 0L,
        @Volatile var doubleWindowAt: Long = 0L,
        /**
         * 这一枪双击窗口的长度（`onKeyUp` 排窗时按当时的配置写死）。
         *
         * 必须在排窗那一刻**定格**：`doubleClickMs` 是 volatile，排窗时读一次、到点再读一次，
         * 中途用户改了「单击延迟」就会让两处口径不一致 —— 判定用的窗口与排出的窗口不是同一个，
         * 300ms 内的合法新单击可能被误吞。
         */
        @Volatile var doubleWindowMs: Long = 0L,
        @Volatile var sawFirstRelease: Boolean = false,
        /**
         * 本次按下已经判过长按。
         *
         * 必须单独一个字段：长按定时器触发时会清零 `longPressAt`，用 `longPressAt == 0L` 当判据，
         * 抬手时那个判断恒为假，一次按键会既判长按又判单击。
         */
        @Volatile var longPressFired: Boolean = false,
        /**
         * 本次按下已经判过双击。
         *
         * 与 longPressFired 同理、也是必须的：判双击发生在第二次按下那一刻，而第二次抬手照样会走
         * `onKeyUp`，没有这个标记就会再开一个单击窗口、把双击变成「双击 + 单击」。
         */
        @Volatile var doubleFired: Boolean = false,
        /**
         * 本次按下的时刻（`onKeyDown` 里写、`reset` 里清）。
         *
         * 用来判「这一下是不是已经被直判过了」—— 直判与 DOWN/UP 是同一次按压的两种形态。
         * 必须是时刻、不能只用一个布尔：布尔会被上一次按压的直判污染。
         */
        @Volatile var pressAt: Long = 0L,
        /** 「长按连续触发」正在跑的重复任务（抬手 / 重置时取消）。 */
        @Volatile var repeatRunnable: Runnable? = null,
        /**
         * 长按那一枪本身（`postDelayed` 的那个 Runnable），抬手 / 重置时摘掉（[cancelLongPressShot]）。
         *
         * 为什么要把 Runnable 存下来：原来只在到点时读一眼 `sawFirstRelease` 决定响不响，而那次读
         * 跨线程 —— 读到陈旧值就是「明明抬手了还判长按」。摘枪是确定的，不依赖那次读。
         */
        @Volatile var longPressRunnable: Runnable? = null,
        /**
         * 双击窗口那一枪（`postDelayed` 的那个 Runnable），抬手 / 重置 / 排新枪时摘掉。
         *
         * 与 [longPressRunnable] 同一个道理，原来它**没存**（只在到点时读状态），于是
         *  `reset()` 摘不掉它、也没法判「这一枪属于哪一次按压」—— 迟到的旧窗口会读到后来那次
         *  按压的 `sawFirstRelease` / `doubleWindowAt`，抢先补一次单击并把标志清掉。
         */
        @Volatile var doubleWindowRunnable: Runnable? = null,
    )

    private val pendingByKey = ConcurrentHashMap<KeyId, Pending>()

    /**
     * 最近一次「当场判单击」（双击为无操作那条路）的时刻，
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
    fun onKeyDown(
        id: KeyId,
        config: KeyConfig,
        now: Long = SystemClock.uptimeMillis(),
    ) {
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
    fun onKeyUp(
        id: KeyId,
        config: KeyConfig,
        now: Long = SystemClock.uptimeMillis(),
    ) {
        val p = pending(id)

        // 抬手 ⇒ 长按那一枪作废。**摘枪**，不是只靠到点读一眼 `sawFirstRelease`：那次读跨线程，
        //  读到陈旧值就是「明明抬手了还判长按」。长按延迟压到 100ms 这一档时，抬手与到点只隔
        //  几毫秒，正是这种读最容易失手的位置。
        cancelLongPressShot(id)

        // 长按已在按下期间触发过 —— 抬手不再重复判单击
        // （判据是 longPressFired，不是 longPressAt：后者已被长按定时器清零）
        if (p.longPressFired) {
            reset(id)
            return
        }

        // 双击已经判过 —— 这次抬手是双击的第二下，不能再开一个单击窗口
        // （否则 `doubleClickMs` 之后会补一次单击，双击变成「双击 + 单击」两下）
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
            val alreadyDispatched =
                if (p.pressAt != 0L) {
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
            // 与上面那条直判去重同一笔账：这一下已经派发过单击了，
            // 紧接着那次 DOWN/UP（同一次按压被转发两遍时）与长按计时器都要据此让路。
            lastDirectSingleAt = now
            dispatch(id, Gesture.SINGLE, config, repeat = false)
            return
        }

        p.sawFirstRelease = true
        // 窗口长度在这一刻定格（见 Pending.doubleWindowMs）：排窗用的窗口与到点判定用的窗口
        // 必须是同一个数，否则中途改「单击延迟」会让两者悄悄变成两个数。
        val window = doubleClickMs.toLong()
        p.doubleWindowMs = window
        p.doubleWindowAt = now + window
        scheduleDoubleWindow(id, config)
    }

    /** 清空某个键的状态与计时器（含「长按连续触发」正在跑的重复任务）。 */
    fun reset(id: KeyId) {
        val p = pending(id)
        cancelLongPressShot(id)
        p.repeatRunnable?.let { handler.removeCallbacks(it) }
        p.repeatRunnable = null
        // 双击窗口那一枪也要摘：它与长按枪一样是「到点才读状态」，留着会在下一次按压期间醒来
        p.doubleWindowRunnable?.let { handler.removeCallbacks(it) }
        p.doubleWindowRunnable = null
        p.longPressAt = 0L
        p.doubleWindowAt = 0L
        p.doubleWindowMs = 0L
        p.sawFirstRelease = false
        p.longPressFired = false
        p.doubleFired = false
        p.pressAt = 0L
    }

    private fun scheduleLongPress(
        id: KeyId,
        config: KeyConfig,
    ) {
        // 这一枪属于哪一次按下：`pressAt` 就是那一次按下的时刻（`onKeyDown` 写、`reset` 清）。
        // 到点时它变了（中途又按过一次）或清零了（抬手 / 重置过）⇒ 这一枪作废；
        // 否则第 1 次排下的枪会在第 N 次按住期间醒来，凭空判出一次长按。
        val downAt = pending(id).pressAt
        val shot =
            Runnable {
                try {
                    val p = pending(id)
                    p.longPressRunnable = null
                    if (p.pressAt != downAt) {
                        XLog.d("${id.label}：这一枪属于上一次按下（已作废），不判长按")
                        return@Runnable
                    }
                    // 到点了还没抬手 → 长按。
                    // 若转发顺序是先 SINGLE 后 DOWN，那个 DOWN 会把 `longPressAt` 重新排上，
                    // 一个 `longPressMs` 之后照样判长按 ⇒ 又是两个动作。
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
                } catch (e: Throwable) {
                    // 计时器跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("长按判定失败（忽略）：${id.label}", e)
                }
            }
        pending(id).longPressRunnable = shot
        handler.postDelayed(shot, config.longPressMs.toLong())
    }

    /**
     * 摘掉还挂着的那一枪长按 —— [onKeyUp] 与 [reset] 都走它。
     *
     * `removeCallbacks` 可以从输入线程安全地调（Handler 自己带锁），而且是**确定**的：
     * 只要这一枪还没被 looper 取走，它就再也不会响。
     */
    private fun cancelLongPressShot(id: KeyId) {
        val p = pending(id)
        p.longPressRunnable?.let { handler.removeCallbacks(it) }
        p.longPressRunnable = null
    }

    /** 「长按连续触发」（`plus_long_repeat`）的重复派发。 */
    private fun scheduleRepeat(
        id: KeyId,
        config: KeyConfig,
    ) {
        val interval =
            config.longRepeatIntervalMs
                .coerceAtLeast(KeyConfig.MIN_REPEAT_INTERVAL_MS)
                .toLong()
        val runnable =
            Runnable {
                try {
                    val p = pending(id)
                    // 松手 / 重置过了 → 停
                    if (!p.longPressFired || p.sawFirstRelease) {
                        p.repeatRunnable = null
                        return@Runnable
                    }
                    // 外部终止：UP 丢失（锁屏打断 / 非FLAG_CANCELED 的丢弃）时没有任何回调会停这条链，
                    // 每 interval 自循环派发 —— 距按下超过 30s 视为抬手事件已丢，强制停止。
                    if (p.pressAt != 0L && SystemClock.uptimeMillis() - p.pressAt > REPEAT_MAX_MS) {
                        XLog.w("${id.label}：连续触发超过 ${REPEAT_MAX_MS}ms 未见抬手，强制停止（UP 可能已丢）")
                        p.repeatRunnable = null
                        reset(id)
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
    private fun scheduleDoubleWindow(
        id: KeyId,
        config: KeyConfig,
    ) {
        // 排窗用的窗口长度在排窗这一刻定格，不再到点重读 `doubleClickMs`
        // （那一读会读到用户中途改过的新值，与 `p.doubleWindowAt` 的口径对不上）。
        //
        // 这一枪属于哪一次按下：与长按枪同一条身份判据（见 [scheduleLongPress] 的 `downAt`）——
        //  `pressAt` 在 `onKeyDown` 写、`reset` 清。不判的话，迟到的旧窗口会在**后来那次**按压
        //  期间醒来，读到它的 `sawFirstRelease` / `doubleWindowAt` 而抢先补一次单击并清掉标志，
        //  真正该补的那次单击（本次按压排的枪）反而落空。
        val p0 = pending(id)
        val pressAt = p0.pressAt
        val window = p0.doubleWindowMs
        p0.doubleWindowRunnable?.let { handler.removeCallbacks(it) }
        val shot =
            Runnable {
                try {
                    val p = pending(id)
                    p.doubleWindowRunnable = null
                    if (p.pressAt != pressAt) {
                        XLog.d("${id.label}：双击窗口属于上一次按压（已作废），不补单击")
                        return@Runnable
                    }
                    // 窗口到了、期间没有第二次按下 → 单击
                    if (p.sawFirstRelease && p.doubleWindowAt != 0L) {
                        // 这一次按压到此为止 —— 把长按那一枪**作废**（清 `longPressAt` 与 `pressAt`）。
                        //  不作废的后果：按住时长 < `longPressMs − doubleClickMs`
                        // （当前默认 400 − 300 = 100ms）时，长按枪排在**本回调之后**到点，而本回调
                        //  只清 `sawFirstRelease` 的话，长按枪的三条栅栏（`longPressAt != 0` /
                        //  `!sawFirstRelease` / `!directJustFired`）**全部通过** ⇒ 一次短按既判单击、
                        //  又判长按。默认配置走 `onKeyUp` 的「立即判单击」分支（那条会 `reset`），
                        //  所以从未暴露；只有「双击绑了动作」或关了 `instantSingle` 时才走这条窗口路径。
                        //  下面两条分支都要作废，所以在进分支时先清。
                        p.longPressAt = 0L
                        p.pressAt = 0L
                        // 这次按压已经当场判过单击（同一次按压被转发两遍）
                        // ⇒ 不再补一次单击，否则一次按压会执行两个单击动作。
                        // 这里的窗口同样用定格值 —— 与排窗一致。
                        if (SystemClock.uptimeMillis() - lastDirectSingleAt < p.doubleWindowMs) {
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
                } catch (e: Throwable) {
                    // 计时器跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("双击窗口判定失败（忽略）：${id.label}", e)
                }
            }
        p0.doubleWindowRunnable = shot
        handler.postDelayed(shot, window)
    }

    /**
     * 执行一个手势对应的动作。
     *
     * `repeat` 只影响「长按连续触发」的重复派发，由 `onGesture` 一路带到执行侧。
     */
    private fun dispatch(
        id: KeyId,
        gesture: Gesture,
        config: KeyConfig,
        repeat: Boolean,
    ) {
        // 链路自检的**端到端实证**：真判出一个手势就记一笔。
        //  接管点装上只说明反射找对了类与方法，说明不了这版 ROM 的侧键事件真的走到这里 ——
        //  只有这一笔能证明整条链通了（页面见 `ui/LinkCheck.kt`）。
        onGestureNoted()
        // 触觉反馈**不在这里**：它已经挪回宿主侧、动作即将执行那一刻
        // （`host/GestureReceiver.runAction`）。理由见那边的 KDoc —— 一句话：
        // 判定侧振不出「服务在跑」这个事实（它判出手势就振，可宿主在不在它不知道），
        // 而用户要的是「不振就别让人以为功能能用」。
        onGesture(id, gesture, config, repeat)
    }
}
