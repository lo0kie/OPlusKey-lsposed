package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import java.util.concurrent.ConcurrentHashMap

/**
 * **参与组合的键按下之后「先等一个窗口」**那笔账（见 `SideKeyInterceptor` 里那张卡片的等待时间）。
 *
 * ## 为什么要压着
 *
 *  组合键的窗口既是「两颗键算不算同时按」的判据，也是「第一颗键要不要等第二颗」的等待：不等的话，
 *  `ComboDetector.Down.HOLD` 这条状态就没有意义 —— 单键已经先跑掉了，第二颗键再来也凑不成组合。
 *
 * ## 为什么松手了还要等满窗口
 *
 *  组合的配对判据是「两颗键的 DOWN 相差不超过窗口」，**不要求两颗同时按着**（见 `ComboDetector`
 *  类注释）。所以手指松开那一刻还看不出结果：窗口内第二颗键仍可能按下来。
 *  早先一抬手就把这一按补进单键判定器，结果就是这个 —— 先按 A、松手、紧接着按 B：
 *  A 的单击已经跑掉（还会再等双击窗口才派发），B 到时组合又成立，用户看到「各自的单击 + 组合」
 *  三下全来了。
 *
 *  现在抬手只记一笔，到点那一枪按「先 DOWN 后 UP」把整按补进去（时长仍是真实按下的时长）；
 *  这期间组合要是成立了，`drop` 会把这一枪摘掉 —— 于是只有组合，没有单击。
 *
 * ## 为什么要把按下时刻一起带过去
 *
 *  补发晚了一个窗口。判定器如果拿**补发这一刻**当按下时刻，`pressAt` / `longPressAt` 就整体
 *  后移一个窗口（窗口 80ms ⇒ 长按 400ms 变成 480ms；窗口 1000ms ⇒ 1400ms），长按阈值跟着漂。
 *  所以 [Press] 记下真实按下时刻，补发时原样交给判定器 —— 等的是窗口，判的还是那一下。
 *
 * ## 为什么单独一个类
 *
 *  它只是一个「到点补发」的小簿记，与拦截器那套「认领 / 派发」没有关系；挤在拦截器里会把那个类
 *  顶到 detekt 的 `TooManyFunctions`（它本来就贴着线）。
 *
 * ## 线程
 *
 *  写在输入线程（DOWN / UP），读在 main looper 的定时任务里 —— 与 `ComboDetector.down` 同一条
 *  理由：volatile 只管引用，集合必须用并发容器。
 *
 * @param feed 到点把压着的按下补交给单键判定器（`GestureDetector::onKeyDown`）；
 *   第三个参数是**原始按下时刻**，判定器据此算长按截止，而不是按补发时刻算
 * @param feedUp 松手早于窗口时，到点连着把抬起一起补交（`GestureDetector::onKeyUp`）
 */
internal class HeldPresses(
    private val handler: Handler,
    private val feed: (KeyId, KeyConfig, Long) -> Unit,
    private val feedUp: (KeyId, KeyConfig) -> Unit,
) {
    /** 压着的那几颗键 —— key → 那一枪 + 按下那一刻的判定参数。 */
    private val held = ConcurrentHashMap<KeyId, Press>()

    /**
     * 压住 [id] 这一下，[windowMs] 之后再补给单键判定器。
     *
     *  同一颗键又按下来时，压着的那一下**先补进去再换新的**：新的 DOWN 就是「上一次按压已经结束」
     *  的证据。直接顶掉的话，快速双击的第一下会整个消失（第二下到来时第一下还在窗口里没补发）。
     */
    fun hold(
        id: KeyId,
        keyConfig: KeyConfig,
        windowMs: Long,
    ) {
        held.remove(id)?.let { prev ->
            handler.removeCallbacks(prev.shot)
            deliver(id, prev)
        }
        //  按下这一刻就记下来（本方法由 DOWN 那条路同步调进来）：补发要晚一个窗口，
        //  这一份时刻是「这一按到底什么时候按的」的唯一凭据（见类注释）。
        val downAt = SystemClock.elapsedRealtime()
        lateinit var press: Press
        press =
            Press(
                keyConfig,
                downAt,
                Runnable {
                    //  与 [release] 抢同一把锁：摘 `held` 与「读 released」必须是一段临界区。
                    //  否则松手恰好落在两者中间时，调用方吞掉了那条 UP，而这里已经读过
                    //  `released = false` 不补 UP —— 单键判定器认为键一直按着，一次单击被判成长按。
                    synchronized(press) {
                        //  被后一次按压顶掉 / 已被组合收走 / 已被 [drop] 撤销 ⇒ 这一枪作废
                        if (press.revoked || !held.remove(id, press)) return@synchronized
                        press.dispatching = true
                        deliver(id, press)
                    }
                },
            )
        held[id] = press
        handler.postDelayed(press.shot, windowMs)
        //  d 级：调大窗口之后这一行会多起来，属于「正常但值得看一眼」的那一档
        XLog.d { "${id.label}：参与组合，先等 ${windowMs}ms 再判单键" }
    }

    /**
     * 抬手只是**记一笔**「松了」，不补发 —— 组合的等待压过单键的单击（见类注释）。
     *
     *  到点那一枪会按原顺序补：先 DOWN 再 UP（单键判定器靠这一对判单击 / 双击）。
     *
     * @return 这一下是不是还压着（是的话调用方吞掉这条 UP，别再走单键那条路）
     */
    fun release(id: KeyId): Boolean {
        val press = held[id] ?: return false
        synchronized(press) {
            //  补发已经开始了（它读到的是 `released = false`）：这一下交回给调用方按普通 UP 处理。
            //  这里若返回 true，那条 UP 就被吞掉，而补发那一侧不会替它补 —— 见那个 Runnable 的注释。
            if (press.dispatching) return false
            press.released = true
            return true
        }
    }

    /** 丢掉压着的那一颗（`null` = 全部丢掉）—— 组合接管了它，或配置变了不该再补旧的那一下。 */
    fun drop(id: KeyId?) {
        if (id == null) {
            held.keys.toList().forEach { drop(it) }
            return
        }
        val press = held[id] ?: return
        //  与到点补发那一枪**同一把锁**：两边都在摘 `held`，不在锁里会和「已经起跑的那一枪」
        //  交错 —— 同一按既被组合收走、又被补进单键判定器。
        synchronized(press) {
            //  **先记「已撤销」再摘表**：那一枪可能已经被 looper 取走、正等这把锁 —— 只摘 `held`
            //  的话它靠「表里没有这一份了」也会退出，但那条判据只在本类的写法不变时成立；
            //  `revoked` 是补发那一侧亲口看的凭据，两处判据一起用。
            press.revoked = true
            //  `dispatching` = 补发已经开跑（DOWN 已经交出去了，见 [hold] 的 Runnable）：
            //  这一下停不掉 —— 半路掐断会留下「只补了 DOWN、没补 UP」的单键状态，代价比多一下单击更大。
            //  按实例移除：`held` 里可能已经换成了后一次按压的那一份。
            if (!press.dispatching && held.remove(id, press)) handler.removeCallbacks(press.shot)
        }
    }

    /** 把压着的一按按原顺序补交给单键判定器（先 DOWN；松过手再补 UP）。 */
    private fun deliver(
        id: KeyId,
        press: Press,
    ) {
        try {
            //  带上原始按下时刻：判定器的长按截止 = 按下时刻 + `longPressMs`，不是补发时刻 +
            //  `longPressMs`，否则参与组合的键长按阈值会整体后移一个窗口。
            feed(id, press.keyConfig, press.downAt)
            //  松手早于窗口：这一按在窗口里没等到第二颗键，按「一次完整按压」补进去
            if (press.released) feedUp(id, press.keyConfig)
        } catch (e: Throwable) {
            XLog.w("补发单键失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }

    /** 压着等窗口的一次按压。 */
    private class Press(
        val keyConfig: KeyConfig,
        /** 手指按下那一刻（`elapsedRealtime`）—— 补发晚一个窗口，判定仍要用它当按下时刻。 */
        val downAt: Long,
        val shot: Runnable,
    ) {
        /** 手指已经松了（抬手早于窗口）—— 到点补发时要连着 UP 一起补。 */
        @Volatile
        var released: Boolean = false

        /**
         * 这一按**已经不算数**了（[drop]：组合接管了它 / 配置变了不该再补旧的那一下）。
         *
         *  锁内读写，与 [release] / [drop] / 补发那一枪用同一把锁 —— `held` 里查不到这一份
         *  只是「表里没有了」，这个标志表达的是「补发侧看见了、主动放弃」。
         */
        var revoked: Boolean = false

        /** 补发已经开始（锁内读写，见 [hold] 那一枪与 [release]）。 */
        var dispatching: Boolean = false
    }
}
