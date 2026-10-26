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
 * @param feedUp 抬手早于窗口时，连着把抬起一起补交（`GestureDetector::onKeyUp`）
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
     *  同一颗键连按两次时后一次会顶掉前一次（那一枪到点发现自己已经被换掉就作废）。
     */
    fun hold(
        id: KeyId,
        keyConfig: KeyConfig,
        windowMs: Long,
    ) {
        lateinit var press: Press
        press =
            Press(
                keyConfig,
                Runnable {
                    //  被后一次按压顶掉 / 已被组合收走 ⇒ 这一枪作废
                    if (!held.remove(id, press)) return@Runnable
                    try {
                        feed(id, press.keyConfig)
                    } catch (e: Throwable) {
                        XLog.w("补发单键失败（已忽略）：${e.javaClass.simpleName}", e)
                    }
                },
            )
        held[id] = press
        handler.postDelayed(press.shot, windowMs)
        //  d 级：调大窗口之后这一行会多起来，属于「正常但值得看一眼」的那一档
        XLog.d { "${id.label}：参与组合，先等 ${windowMs}ms 再判单键" }
    }

    /**
     * 抬手时把压着的这一下**立刻放掉** —— 手指都松了，组合不可能再成立，没必要继续等窗口。
     *
     *  按原顺序补：先 DOWN 再 UP（单键判定器靠这一对判单击 / 双击），时长仍是真实按下的时长。
     *
     *  @return 这一下是不是压着还没喂（是的话调用方不必再走组合 / 单键那两条路）
     */
    fun release(
        id: KeyId,
        keyConfig: KeyConfig,
    ): Boolean {
        val press = held.remove(id) ?: return false
        handler.removeCallbacks(press.shot)
        try {
            feed(id, press.keyConfig)
            feedUp(id, keyConfig)
        } catch (e: Throwable) {
            XLog.w("补发单键失败（已忽略）：${e.javaClass.simpleName}", e)
        }
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

    /** 压着等窗口的一次按压。 */
    private class Press(
        val keyConfig: KeyConfig,
        val shot: Runnable,
    )
}
