package com.lookie.opluskey.inject.input

import android.os.Handler
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
 * @param feed 到点把压着的按下补交给单键判定器（通常是 `GestureDetector::onKeyDown`）
 * @param feedUp 松手早于窗口时，到点连着把抬起一起补交（`GestureDetector::onKeyUp`）
 */
internal class HeldPresses(
    private val handler: Handler,
    private val feed: (KeyId, KeyConfig) -> Unit,
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
        lateinit var press: Press
        press =
            Press(
                keyConfig,
                Runnable {
                    //  被后一次按压顶掉 / 已被组合收走 ⇒ 这一枪作废
                    if (!held.remove(id, press)) return@Runnable
                    deliver(id, press)
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
        press.released = true
        return true
    }

    /** 丢掉压着的那一颗（`null` = 全部丢掉）—— 组合接管了它，或配置变了不该再补旧的那一下。 */
    fun drop(id: KeyId?) {
        if (id != null) {
            held.remove(id)?.let { handler.removeCallbacks(it.shot) }
            return
        }
        held.keys.toList().forEach { drop(it) }
    }

    /** 把压着的一按按原顺序补交给单键判定器（先 DOWN；松过手再补 UP）。 */
    private fun deliver(
        id: KeyId,
        press: Press,
    ) {
        try {
            feed(id, press.keyConfig)
            //  松手早于窗口：这一按在窗口里没等到第二颗键，按「一次完整按压」补进去
            if (press.released) feedUp(id, press.keyConfig)
        } catch (e: Throwable) {
            XLog.w("补发单键失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }

    /** 压着等窗口的一次按压。 */
    private class Press(
        val keyConfig: KeyConfig,
        val shot: Runnable,
    ) {
        /** 手指已经松了（抬手早于窗口）—— 到点补发时要连着 UP 一起补。 */
        @Volatile
        var released: Boolean = false
    }
}
