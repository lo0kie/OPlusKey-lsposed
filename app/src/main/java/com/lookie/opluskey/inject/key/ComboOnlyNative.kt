package com.lookie.opluskey.inject.key

import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.inject.input.ComboDetector
import java.util.concurrent.ConcurrentHashMap

/**
 * 「补发出去的原生事件」这份账能活多久（毫秒）—— 见 [ComboOnlyNative.echo]。
 *
 *  异步投递通常几毫秒就回来，取 2 秒是留余量。**必须有上限**：投失败留下的残账会让
 *  「用户下一次真的按这颗键」被当成回声放行，那颗键就静默失效一次。
 */
private const val ECHO_TTL_MS = 2_000L

/**
 * 补发过原生 DOWN 之后，等真实抬手的上限（毫秒）—— 见 [ComboOnlyNative.scheduleHoldGuard]。
 *
 *  取 10 秒：电源长按弹关机菜单最长也就几秒，再久说明这一条抬手根本不会来了（ROM 在长按之后
 *  换了颗码上报抬手，那条 UP 认不到这颗键）。到点必须替它补一条抬起 ——
 *  系统那边「一直按着」的表现是音量一路涨到底，比少一次长按难收拾得多。
 */
private const val HOLD_GUARD_MS = 10_000L

/**
 * 「只参与组合」那颗键的收尾账本 —— **压住的那一下怎么还给系统**，只有这一处说了算。
 *
 * ## 为什么按下要压住
 *
 *  组合判定要在窗口内看「第二颗键会不会来」。这颗键的原生动作（音量 ± / 电源）一旦发生就
 *  撤不回来，所以**还可能配成组合的那几下**先吞，等判定出结论再决定归谁：凑成组合就永久吞，
 *  没凑成就还回去。**已经不可能配成的那几下不压**（见下节第三种情形）—— 压了只会白慢一拍。
 *
 * ## 压到什么时候：两种要压、一种一下都不压
 *
 *  - **等窗口那几档的成员**（`ComboKey.isWait`）压到窗口到点为止：那个数就是「第二颗还来不来得及」
 *    的答案，到点还没凑成就不必再等（用户口径：先吞后补，单按只晚一个窗口）。
 *  - **按住那几档的起点键**（`ComboKey.starterKey`，见 `ConfigChannel.holdStarter`）**没有上界**：
 *    判据只要求那一颗还按着，按住三秒再补第二颗照样成立。它**压到抬手**（[deferred]）——
 *    窗口到点就把原生事件漏出去的话，之后凑成的按住组合只能是「一次按压两个动作」。
 *    代价写在 `docs/runtime/key-to-gesture.md`：那颗键在这一档里**不再出原生长按**
 *    （音量连续调 / 电源长按菜单），只补一次短按。
 *  - **两者都不是 ⇒ 一下都不压**：那一组既不认窗口、这颗键又不是先按住的那一颗，按下那一刻
 *    就已有定论 —— 另一颗若正按着，组合在 `combo.onDown` 里当场认领（这一下照吞）；没按着，
 *    则这一下**永远配不上**（按住那条要求另一颗先按着，而窗口那条根本不认）。所以原生事件
 *    原样交给系统，一秒都不压，也不用再补一条注入事件回去。
 *
 * ## 为什么「还回去」不是一次短按就完事
 *
 *  还回去的那一下要**接着还原用户真正的按法**：
 *
 *  - 窗口到点时用户**已经松手** ⇒ 补一对 DOWN + UP（系统只该看到一闪，这就是原生短按）；
 *  - 窗口到点时用户**还按着** ⇒ 只补 DOWN，抬手那一下等它与真实事件一起收尾。
 *    先补 UP 就等于把这一按截成短按 —— 系统的长按计时从收到 DOWN 起算，UP 一到就结束，
 *    于是原生长按（音量连续调 / 电源长按菜单）永远出不来。
 *
 *  按住的过程中真实重复事件也要转发：注入的 DOWN 不走 InputReader，系统自己产不出 repeat，
 *  而「按住连续调音量」正是靠 repeat 一档档往上走的。
 *
 * ## 四张表的分工
 *
 *  - [swallowed]：这一下按下被吞过 ⇒ 抬手要跟着吞（否则系统只收到半截，键卡在按住状态）；
 *  - [pending]：压住了、**等着窗口到点补发**，值兼作这一下的版本号；
 *  - [holding]：**已经补发过 DOWN**、等真实抬手来收尾 —— 进这张表就意味着系统那边按着，
 *    所以离开它时必须补一条 UP（真实抬手、配置变更、兜底超时三个出口都走同一个函数）；
 *  - [deferred]：按住那条**先按住的那一颗**，**压到抬手**再补一对（没有窗口任务，见上面那段）。
 *
 * ## 线程
 *
 *  写都在输入线程（`SideKeyInterceptor` 的按键回调），读在主 looper 的延迟任务上。单独一张表
 *  各自是原子的，但**一次收尾要同时动两张**（「还按着吗」→「记进 holding」；「从 holding 摘掉」
 *  →「补一条 UP」），两条线程交错时会出现「DOWN 补了、UP 没人补」—— 系统那边一直按着，
 *  表现是音量一路涨到底。所以三张表的转移一律在 [lock] 里做，**投递也放在锁里**：
 *  「公布的顺序」必须等于「系统收到事件的顺序」。
 */
internal class ComboOnlyNative(
    private val handler: Handler,
    private val combo: ComboDetector,
    /** 这一颗键按下之后要压多久才轮到窗口那条还回去（`ConfigChannel.holdWindowMsFor`）。 */
    private val windowMs: (KeyId) -> Long,
    /** 这一颗键会不会被接在后面 —— 即它参与的某一组认窗口那条（`ConfigChannel.joinsCombo`）。 */
    private val windowMember: (KeyId) -> Boolean,
    /** 这一颗键可能是某一组按住组合里**先按住的那一颗**（`ConfigChannel.holdStarter`）—— 是就压到抬手。 */
    private val holdStarter: (KeyId) -> Boolean,
) {
    /**
     * 三张表转移的闸 —— 见类注释的「线程」。
     *
     *  持锁期间只做三件事：改表、投一条事件、排一枪。投递之所以留在锁里是因为它**就是那条
     *  「顺序」本身**；投递是异步的（`INJECT_MODE_ASYNC`），不会在这把锁上回头再进来。
     */
    private val lock = Any()

    /** 这一下已经吞过了的那几颗键 id。 */
    private val swallowed = ConcurrentHashMap.newKeySet<String>()

    /** 压住了、等窗口到点补发 —— 键 id → 按下时刻（兼作版本号）。 */
    private val pending = ConcurrentHashMap<String, Long>()

    /** 已经补发了原生 DOWN、等真实抬手来收尾 —— 键 id → 那一颗的键码。 */
    private val holding = ConcurrentHashMap<String, Int>()

    /** 压着、等**抬手**再补一对 —— 键 id → 那一颗的键码（按住那条的第一颗，见类注释）。 */
    private val deferred = ConcurrentHashMap<String, Int>()

    /** 补发出去、还没被拦截链认领的事件 —— keyCode → 剩余条数与过期时刻。 */
    private val echo = ConcurrentHashMap<Int, Echo>()

    /** 一份回声账 —— 剩余条数与过期时刻。 */
    private class Echo(
        val left: Int,
        val until: Long,
    )

    /**
     * 这一下是不是**我们自己补发出去的回声** —— 是就销一次账并返回 true（调用方放行）。
     *
     *  补发的事件会重新走一遍输入分发、也就是再进一次拦截链：认不出来就是死循环
     *  （吞掉 → 补发 → 又被吞 → 又补发）。
     *
     *  这一支**不进 [lock]**：它只动 [echo]，而 [echo] 自己的 `compute` 就是原子的；
     *  它在拦截链的最前面，每次按键都要问一遍。
     */
    fun consumeEcho(keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        var seen = false
        echo.compute(keyCode) { _, cur ->
            when {
                cur == null || now > cur.until -> {
                    null
                }

                else -> {
                    seen = true
                    if (cur.left <= 1) null else Echo(cur.left - 1, cur.until)
                }
            }
        }
        return seen
    }

    /**
     * 喂一个按键事件。
     *
     * @param point 只用于日志（同一次物理按压可能被不止一层看到）
     * @return true = 吞掉，false = 原样放行给系统
     */
    fun onKey(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
    ): Boolean =
        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> onDown(id, point, keyEvent)
            KeyEvent.ACTION_UP -> onUp(id, point, keyEvent)
            else -> false
        }

    private fun onDown(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
    ): Boolean {
        //  按住不放的重复：**已经补发过原生 DOWN 就转给系统**（长按连续动作靠它），
        //  否则连着吞 —— 放行的话系统会收到一个没有 DOWN 的 repeat，那颗键在它眼里从没被按过。
        //  读 `holding` 与转发同处一把锁：抬手正好在中间就会转出一条「已经松开之后」的 repeat。
        if (keyEvent.repeatCount > 0) {
            synchronized(lock) {
                val code = holding[id.id]
                if (code != null) forward(code, KeyEvent.ACTION_DOWN, keyEvent.repeatCount)
            }
            return true
        }
        val downAt = SystemClock.uptimeMillis()
        val taken = combo.onDown(id) == ComboDetector.Down.TAKEN
        //  两条「要不要压」的判据各查一次表（按键热路径，别为了一句注释查两遍）
        val windowMemberHere = windowMember(id)
        val holdStarterHere = holdStarter(id)
        //  **这一下没有任何理由压住**（见类注释的第三种情形）：那一组不认窗口、这颗键也不是
        //  先按住的那一颗 —— 组合要么已经在上面那一句里当场成立，要么永远配不上。原样交给系统，
        //  一秒都不压，也不必再补一条注入事件回去。
        if (!taken && !windowMemberHere && !holdStarterHere) {
            XLog.d { "${id.label}（$point）：不认窗口、也不是先按住的那颗 → 原样交给系统" }
            return false
        }
        //  压到哪一步：窗口那条压到窗口到点，按住那条压到抬手（见类注释）。
        val waitForRelease = !taken && holdStarterHere
        synchronized(lock) {
            //  走到这里就是「这一下要压」（上面那两级判据已经排除掉不必压的）：先吞，
            //  凑成了永久吞、没凑成再由窗口到点或抬手还回去（用户口径：组合判定不该触发原生事件）。
            swallowed.add(id.id)
            when {
                taken -> {
                    pending.remove(id.id)
                    deferred.remove(id.id)
                }

                waitForRelease -> {
                    //  **时间戳当版本号**只在窗口那条有用；这一档没有窗口任务，抬手那次自己认这一笔
                    deferred[id.id] = keyEvent.keyCode
                }

                else -> {
                    pending[id.id] = downAt
                }
            }
        }
        if (taken) {
            XLog.w("${id.label}（$point）：这一下归组合（只参与组合），连原生事件一起吞掉")
        } else if (waitForRelease) {
            XLog.w("${id.label}（$point）：按下先压住（只参与组合，按住手势），抬手时再还回去")
        } else {
            //  时间戳当这一下的**版本号**：同一颗键在窗口内又被按到时，旧任务到点会发现
            //  账已经被新的一次改写了，不会替新的那一下做主（补发错次数比不补发更难查）。
            scheduleFallback(id, keyEvent.keyCode, downAt)
            XLog.w("${id.label}（$point）：按下先压住（只参与组合），窗口内没凑成就把原生事件还回去")
        }
        return true
    }

    private fun onUp(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
    ): Boolean {
        //  组合那笔账要结（`down` / `consumed` / 待发的短按都在 `onUp` 里）。
        //  返回值 = 这一下被组合认领了 —— 压到抬手那一档据此决定还要不要补发。
        val claimed = combo.onUp(id, short = !keyEvent.isCanceled)
        var heldCode: Int? = null
        var deferredCode: Int? = null
        synchronized(lock) {
            pending.remove(id.id)
            heldCode = holding.remove(id.id)
            deferredCode = deferred.remove(id.id)
            //  补发过 DOWN ⇒ 抬手也跟着补，真实那条吞掉。**不能放行真实抬手**：系统收到的
            //  那个 DOWN 是我们注入的，两条事件的来源对不上，按同一颗键收尾会错位。
            //
            //  压到抬手的那一下（[deferred]）在这里才补一对：系统只该看到一闪，这就是原生短按。
            //  已经被组合认领的（`claimed`）什么都不补 —— 这一下归组合。
            when {
                heldCode != null -> {
                    forward(heldCode, KeyEvent.ACTION_UP, 0)
                }

                deferredCode != null && !claimed -> {
                    forward(deferredCode, KeyEvent.ACTION_DOWN, 0)
                    forward(deferredCode, KeyEvent.ACTION_UP, 0)
                }
            }
        }
        when {
            heldCode != null -> {
                XLog.w("${id.label}（$point）：补发的原生按下已松开（只参与组合）")
                return true
            }

            deferredCode != null && !claimed -> {
                XLog.w("${id.label}（$point）：没凑成组合（只参与组合），抬手时把原生短按补发回去")
                return true
            }
        }
        return swallowed.remove(id.id)
    }

    /**
     * 窗口走完：没被任何一组组合认领 ⇒ 把这一下还给系统。
     *
     * ## 为什么必须等满窗口
     *
     *  抬手早于窗口时组合**还可能凑成**（先后按下也算组合）—— 那时提前补发就是双重触发：
     *  系统先收到一次原生按下，组合又执行一次。
     *
     * ## 窗口按这一颗键自己那一档取
     *
     *  `ConfigChannel.holdWindowMsFor` = 它参与的几组里最长的那个整串跨度。按全局最宽的那一档
     *  等是白等：别的组合成员更多、窗口更宽，与这一颗毫无关系，而多等的每一毫秒都是
     *  这一颗键原生行为被推迟的量。
     *
     *  **只服务窗口那条**：按住那条的第一颗压根不排这一枪（见 [deferred]）。
     */
    private fun scheduleFallback(
        id: KeyId,
        keyCode: Int,
        downAt: Long,
    ) {
        val wait = (downAt + windowMs(id) - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        handler.postDelayed(
            {
                //  四种结局各自只做一件事，「改表」与「投递」在同一把锁里：
                //  · 账已经换过（同一颗键又被按下）⇒ 什么都不做，别替新的那一下做主；
                //  · 被组合认领了（晚到）⇒ 什么都不做；
                //  · 还按着 ⇒ 补 DOWN，记进 holding，等真实抬手收尾；
                //  · 已经松手 ⇒ 补一对（系统只该看到一闪）。
                var outcome = 0
                synchronized(lock) {
                    if (pending.remove(id.id) == downAt) {
                        when {
                            combo.comboCovers(id) -> {
                                outcome = 1
                            }

                            swallowed.contains(id.id) -> {
                                holding[id.id] = keyCode
                                forward(keyCode, KeyEvent.ACTION_DOWN, 0)
                                outcome = 2
                            }

                            else -> {
                                forward(keyCode, KeyEvent.ACTION_DOWN, 0)
                                forward(keyCode, KeyEvent.ACTION_UP, 0)
                                outcome = 3
                            }
                        }
                    }
                }
                when (outcome) {
                    1 -> {
                        XLog.w("${id.label}：这一下被组合认领（晚到），不再补发原生事件")
                    }

                    2 -> {
                        scheduleHoldGuard(id, keyCode)
                        XLog.w("${id.label}：没凑成组合（只参与组合），原生按下已补发（按住可出长按）")
                    }

                    3 -> {
                        XLog.w("${id.label}：没凑成组合（只参与组合），已把原生短按补发回去")
                    }
                }
            },
            wait,
        )
    }

    /**
     * 补过 DOWN 之后替它收尾的兜底 —— 见 [HOLD_GUARD_MS]。
     *
     *  正常路径上这一枪到点时账早就被真实抬手摘掉了（空操作）。
     */
    private fun scheduleHoldGuard(
        id: KeyId,
        keyCode: Int,
    ) {
        handler.postDelayed(
            {
                var orphan = false
                synchronized(lock) {
                    if (holding.remove(id.id) != null) {
                        forward(keyCode, KeyEvent.ACTION_UP, 0)
                        orphan = true
                    }
                }
                if (orphan) {
                    XLog.w("${id.label}：按住超过 ${HOLD_GUARD_MS}ms 仍没等到抬手，替它补了一条抬起")
                }
            },
            HOLD_GUARD_MS,
        )
    }

    /**
     * 配置变了 / 全部键都不接管了 —— 把账清干净。
     *
     *  **已经补发过 DOWN 的那几颗要补一条 UP**：不清的话系统那边那颗键永远按着，
     *  表现是音量一路涨到底。压着还没补的那几下（窗口那条的 [pending] 与按住那条的
     *  [deferred]）直接丢：新配置下不该再替旧的一下做主。
     */
    fun reset() {
        synchronized(lock) {
            pending.clear()
            deferred.clear()
            swallowed.clear()
            val held = holding.values.toList()
            holding.clear()
            held.forEach { code -> forward(code, KeyEvent.ACTION_UP, 0) }
        }
    }

    /**
     * 投一条原生事件，并记一笔回声账。
     *
     *  **先记账再投**：投出去之后事件可能**立刻**在另一条线程上被拦到，后记就来不及
     *  （漏掉的那一次会被当成新的按下再压一个窗口）。
     */
    private fun forward(
        keyCode: Int,
        action: Int,
        repeat: Int,
    ) {
        echo.compute(keyCode) { _, cur ->
            Echo((cur?.left ?: 0) + 1, SystemClock.uptimeMillis() + ECHO_TTL_MS)
        }
        if (!injectNativeKey(keyCode, action, repeat)) {
            //  投失败：销掉刚记的那一条，否则 2 秒内用户真的按这颗键会被当成回声放行
            echo.compute(keyCode) { _, cur ->
                when {
                    cur == null || cur.left <= 1 -> null
                    else -> Echo(cur.left - 1, cur.until)
                }
            }
            XLog.w("只参与组合：原生事件没补出去（keyevent:$keyCode action=$action）")
        }
    }
}
