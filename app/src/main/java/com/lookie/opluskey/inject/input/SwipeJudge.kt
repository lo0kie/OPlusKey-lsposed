package com.lookie.opluskey.inject.input

import android.view.MotionEvent
import com.lookie.opluskey.core.judge.Swipe
import com.lookie.opluskey.core.judge.swipeAccepted
import com.lookie.opluskey.core.judge.swipeDirection
import com.lookie.opluskey.core.log.XLog

/**
 * 单指滑动的判定器 —— 纯逻辑：喂 [RawInput]，吐 [Swipe] 或 `null`。
 *
 *  结果类型 [Swipe] 与两句判据（[swipeDirection] / [swipeAccepted]）住在 `core/judge`：
 *  它们跟数字与几何有关，与采集点无关，搬过去才能被 `:core:test` 钉住。
 *  留在这里的只有「这一段跟到哪了」那点状态。
 *
 * ## 边界（都是判定，不是参数）
 *
 *  · **只认单指**：整段里出现过第二根手指就作废（多指缩放 / 三指截屏这类不能顺带触发滑动）。
 *  · **只认一段**：上一段还没抬手时又来一个 DOWN，说明前一段的 UP 丢了 —— 前一段作废，
 *    按新的 DOWN 重开，绝不把两段拼成一条。
 *  · **慢拖不算**：超过 [maxDurationMs] 还在走，抬手也不算滑动（按住不放是在调别的，不是划）。
 *  · **判据是峰值位移**：手指划出去又划回来（终点贴着起点）仍然是滑动 —— 拿终点算会被判成点按，
 *    所以整段记的是**离起点最远那一点**，方向也按它算。
 *  · **点按不产出**：峰值位移还在 [slopPx] 里就不是滑动，返回 `null`，不会假装成「零距离滑动」。
 *
 * ## 线程
 *
 *  状态机与采集点**同线程**（真机上的按键/触摸采集都在 `InputReader` 线程，见
 *  `SideKeyInterceptor` 的线程记录），所以字段不加锁。接新采集点时若它跑在别的线程上，
 *  要先把这一段收敛到一个线程（`GameLoop` 那种「到点看一眼」的写法在这里不成立：
 *  判定必须在区间结束时同步出结果）。
 *
 * @param slopPx 判定「划了」的最小位移。默认 28px：与界面层那条滑动探针同一个阈值
 *   （`docs/device/coui.md` 二十），点按的位移是 0~几 px，不会误判。
 * @param maxDurationMs 一段最长多久还算是滑动；超过就当用户在长按/拖别的。
 */
internal class SwipeJudge(
    private val slopPx: Float = DEFAULT_SLOP_PX,
    private val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
) {
    /** 当前这一段的累计（`null` = 没有在跟的一段）。 */
    private var track: Track? = null

    /**
     * 喂一条事件。
     *
     * @return 只在**一段结束且判成滑动**时返回结论；其余情况（点按 / 多指 / 被收走 / 按键事件）
     *   一律 `null` —— 判定层不替调用方区分「不是滑动」与「滑动但没判成」，要区分就记日志那一行。
     */
    fun submit(input: RawInput): Swipe? =
        when (input) {
            is RawInput.TouchDown -> {
                begin(input)
                null
            }

            is RawInput.TouchMove -> {
                move(input)
                null
            }

            is RawInput.TouchUp -> {
                finish(input)
            }

            is RawInput.TouchCancel -> {
                abort("触摸被系统收走")
                null
            }

            //  按键不归滑动判定管：相位判定在 `GestureDetector`，这里连看都不看
            is RawInput.KeyDown, is RawInput.KeyUp -> {
                null
            }
        }

    private fun begin(event: RawInput.TouchDown) {
        if (event.pointers > 1) {
            abort("按下时就有 ${event.pointers} 根手指")
            return
        }
        track?.let { XLog.d { "滑动判定：上一段没有抬手就来了新的按下，前一段作废" } }
        track =
            Track(
                pointerId = event.pointerId,
                startX = event.x,
                startY = event.y,
                startAt = event.atMs,
                peakX = event.x,
                peakY = event.y,
            )
    }

    private fun move(event: RawInput.TouchMove) {
        val t = track ?: return
        if (event.pointers > 1) {
            abort("滑到一半多了一根手指（共 ${event.pointers} 根）")
            return
        }
        //  指针对不上：这一段的主指针已经被换掉了（系统重分发 / 另一根手指接了主位）⇒ 作废
        if (event.pointerId != t.pointerId) {
            abort("移动事件的指针 ${event.pointerId} 与按下的 ${t.pointerId} 不同")
            return
        }
        //  超时的不再累计：再跟下去只会把一条慢拖算成滑动，而且状态要一直挂着
        if (event.atMs - t.startAt > maxDurationMs) {
            abort("超过 ${maxDurationMs}ms 还没抬手")
            return
        }
        t.samples++
        if (t.peakDistanceFrom(event.x, event.y) > t.peakDistanceFrom(t.peakX, t.peakY)) {
            t.peakX = event.x
            t.peakY = event.y
        }
    }

    private fun finish(event: RawInput.TouchUp): Swipe? {
        val t = track ?: return null
        track = null
        //  抬手的不是这一段的主指针 ⇒ 作废：拿别人的终点算时长，等于把两段轨迹拼成一条
        val samePointer = event.pointerId == t.pointerId
        if (!samePointer) {
            XLog.d { "滑动判定：抬手的指针 ${event.pointerId} 与按下的 ${t.pointerId} 不同，这一段作废" }
        }
        val duration = event.atMs - t.startAt
        val dx = t.peakX - t.startX
        val dy = t.peakY - t.startY
        val distance = t.peakDistanceFrom(t.peakX, t.peakY)
        //  点按（还在 slop 里）/ 慢拖（超过上限）/ 指针换了：三种都不产出，
        //  也不许假装成「零距离的滑动」交给上层的动作表去匹配（三条判据见 `swipeAccepted`）。
        if (!swipeAccepted(samePointer, distance, slopPx, duration, maxDurationMs)) return null
        return Swipe(
            direction = swipeDirection(dx, dy),
            dxPx = dx,
            dyPx = dy,
            distancePx = distance,
            durationMs = duration,
            samples = t.samples,
        )
    }

    private fun abort(reason: String) {
        if (track != null) XLog.d { "滑动判定：这一段作废 —— $reason" }
        track = null
    }

    /** 一段的累计状态 —— 只活在 [SwipeJudge] 里，判定结束即丢。 */
    private class Track(
        val pointerId: Int,
        val startX: Float,
        val startY: Float,
        val startAt: Long,
        var peakX: Float,
        var peakY: Float,
    ) {
        var samples: Int = 0

        fun peakDistanceFrom(
            x: Float,
            y: Float,
        ): Float {
            val dx = x - startX
            val dy = y - startY
            return kotlin.math.hypot(dx, dy)
        }
    }

    companion object {
        /** 与界面层滑动探针同一条阈值（`docs/device/coui.md` 二十）。 */
        private const val DEFAULT_SLOP_PX = 28f

        /** 一段超过这个时长就当用户在长按 / 拖别的东西，抬手不算滑动。 */
        private const val DEFAULT_MAX_DURATION_MS = 800L
    }
}

/**
 * 触摸手势判定的**唯一入口** —— 将来任何一个触摸采集点，都只调 [onMotionEvent] 这一个方法。
 *
 *  为什么要这一层：采集点可能不止一个（策略回调、输入事件、将来的按下滑动入口），判定器却只能
 *  有一份。把「适配 → 判定 → 判据日志」串在这里，采集点的职责就只剩「拿到事件就喂进来」，
 *  换判定器、改阈值、改日志口径都不必回头找每个采集点。
 *
 *  ## 现在没有采集点
 *
 *  系统进程里能拿到触摸事件的那个入口还没在真机上确认（`docs/device/` 里没有这一项），
 *  所以本阶段**只铺路、不装 Hook、不派发动作**：判出来一段滑动只落一行 `w` 日志。
 *  自检入口是诊断广播的 `touch` op（见 `ProbeOps`），它在进程内造几条合成轨迹跑通这整条链。
 *
 *  接入真实采集点时只做两件事：在候选表里登记入口、在回调里调 [onMotionEvent]。
 *  **别在这里派发动作** —— 判定在执行侧之前，动作是宿主进程的事（见 `GestureForwarder` 那条分工）。
 */
internal object TouchJudge {
    /** 单指滑动的判定器 —— 进程内一份，状态就是「当前这一段轨迹」。 */
    private val swipeJudge = SwipeJudge()

    /**
     * 喂一条原始触摸事件；判出一段滑动时返回它。
     *
     *  线程：必须与采集点同线程（状态机不加锁，见 [SwipeJudge] 的「线程」一节）。
     */
    fun onMotionEvent(event: MotionEvent): Swipe? {
        val raw = TouchAdapter.toRaw(event) ?: return null
        return submit(raw)
    }

    /**
     * 喂一条已经归一化的事件 —— 自检（合成轨迹）走这一条，省掉造 `MotionEvent` 的那几步。
     *
     *  注入侧纪律：判定抛出去会顺着采集点的回调打到系统进程里，所以这里兜一层，最坏只是这段没判出来。
     */
    fun submit(input: RawInput): Swipe? {
        val swipe =
            try {
                swipeJudge.submit(input)
            } catch (e: Throwable) {
                //  **判据级**：判定器抛异常意味着这一档手势从此判不出来，而 `d` 在 release 里
                //  被编译期消除 —— 那正是「滑动绑了没反应」唯一该看的一行。真每划都抛的话，
                //  刷屏本身就是要修的症状，不该靠降档把它藏起来。
                XLog.w("滑动判定抛了（已忽略）：${e.javaClass.simpleName}: ${e.message}")
                return null
            } ?: return null
        //  判据级：一段滑动只在这一处落一行（适配器里不打日志 —— MOVE 是 60~120 条/秒）
        XLog.w("滑动：$swipe")
        return swipe
    }
}
