package com.lookie.opluskey.ui.component

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import com.lookie.opluskey.core.log.XLog
import kotlin.math.abs

/**
 * 一段手势的「位移账本」—— **只记数，不改行为**。
 *
 *  用来抓「滑动被当成一次点击」这种偶发现场：行的点击是在**抬指那一刻**派发的，判「这是滑动还是
 * 点一下」的只有各处组件自己那点逻辑，出问题时日志里只剩一个结果（某一行被点了 / 某个菜单开了），
 * 看不出那一下其实滑了多远、列表跟着动没有。这里把按下→抬起的**竖直位移 / 峰值 / 移动次数 / 用时**
 * 与列表内容有没有跟着移位一并记下，抬指时打一行，点击那一行再打一行 —— 两行配起来就是判据。
 *
 *  [label] 只进日志前缀，用来分清是哪一块（浮层列表 / 步骤卡 / 添加命令那一行）。
 */
@Stable
internal class GestureProbe(private val label: String) {

    private var downAtMs = 0L
    private var downY = 0f
    private var lastY = 0f
    private var peak = 0f
    private var moves = 0
    private var slop = 0f
    private var contentY = 0f
    private var contentYAtDown = 0f

    fun onDown(y: Float, slopPx: Float) {
        downAtMs = android.os.SystemClock.uptimeMillis()
        downY = y
        lastY = y
        peak = 0f
        moves = 0
        slop = slopPx
        contentYAtDown = contentY
    }

    fun onMove(y: Float) {
        moves++
        lastY = y
        peak = maxOf(peak, abs(y - downY))
    }

    /**
     * 记一下某一行在窗口里的位置 —— 它是列表的「有没有滚」探针：位置变了就是滚了。
     *
     *  挂在一行的**内容**上（列表内层），不能挂在列表外面那一圈：滚的是内层，
     *  外圈那层不会动（浮层窗口自己也不动）。
     */
    fun onContentPosition(y: Float) {
        contentY = y
    }

    /** 这一段该算滑动（位移越过阈值），不是点一下。 */
    fun isDrag(): Boolean = peak > slop

    /** 一句话账本，供抬指那行与点击那行共用。 */
    fun describe(): String {
        val scrolled = (contentY - contentYAtDown).toInt()
        return "位移 ${(lastY - downY).toInt()}px / 峰值 ${peak.toInt()}px（阈值 ${slop.toInt()}px），" +
            "移动 $moves 次，用时 ${android.os.SystemClock.uptimeMillis() - downAtMs}ms" +
            (if (scrolled != 0) "，列表内容移位 ${scrolled}px" else "，列表没动")
    }

    fun logPrefix(): String = "$label："
}

/** 建一份账本；[label] 进日志前缀。 */
@Composable
internal fun rememberGestureProbe(label: String): GestureProbe = remember(label) { GestureProbe(label) }

/**
 * 挂账本：读这一段手势的位移（`PointerEventPass.Initial`，抢在任何消费之前）。
 *
 *  **不消费任何事件**（`requireUnconsumed = false` 且不调 `consume`）—— 滚动、点击、拖动全照旧，
 *  这一层只记账；抬指时若位移越过阈值就打一行「这一段是滑动」。
 */
@Composable
internal fun Modifier.gestureProbe(probe: GestureProbe): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop
    return pointerInput(probe, slop) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                probe.onDown(down.position.y, slop)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    probe.onMove(change.position.y)
                    if (change.changedToUpIgnoreConsumed()) break
                    if (!change.pressed) break
                }
                if (probe.isDrag()) {
                    XLog.w("${probe.logPrefix()}这一段是滑动 —— ${probe.describe()}")
                }
            }
        }
    }
}
