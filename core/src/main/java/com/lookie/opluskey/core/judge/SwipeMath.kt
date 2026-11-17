package com.lookie.opluskey.core.judge

import kotlin.math.abs

// 一段滑动 —— 只有几何与时间，**不含**「这是哪个手势、该干什么」（那是它上面那一层的事）。
//
//  与 [JudgeMath] / [ClickChainMath] 同一族：纯数据 + 纯判据，不碰状态、不读时钟、不调
//  Android API。`SwipeJudge` 那个状态机仍在 `:app`（它要跟着采集点的线程），但「主轴在哪一轴」
//  与「这一段算不算数」这两条判据在这里 —— `:app` 没有 test 源集，45° 那条边界原先只能靠手划。

/**
 * 一段滑动的方向，按**主轴**分四档 —— 斜着划也算它偏得多的那一轴。
 *
 * @param durationMs 按下到抬起的时长
 * @param samples 这一段收到了多少条 MOVE（判定余量够不够看它；真机点按约 0~2 条）
 */
data class Swipe(
    val direction: SwipeDirection,
    val dxPx: Float,
    val dyPx: Float,
    val distancePx: Float,
    val durationMs: Long,
    val samples: Int,
) {
    /** 平均速度（px/ms）—— 快扫与慢拖的区别，判定阈值之外由调用方自己定。 */
    val speedPxPerMs: Float get() = if (durationMs <= 0) 0f else distancePx / durationMs

    override fun toString(): String =
        "${direction.name} 位移 ${distancePx.toInt()}px（dx=${dxPx.toInt()}, dy=${dyPx.toInt()}）" +
            " 用时 ${durationMs}ms / $samples 条移动"
}

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * 主轴在哪一轴就报哪一档；**正好 45° 归横向**（两轴相等时取谁本来就没有依据，固定下来即可
 * —— 不固定的话，同一条斜线打在不同设备的分辨率上会翻档）。
 *
 *  零位移（`dx` / `dy` 都是 0）报 [SwipeDirection.LEFT]：它只可能在点按里出现，而那一段在
 *  [swipeAccepted] 里就被拦掉了，方向取谁都到不了上层。
 */
fun swipeDirection(
    dx: Float,
    dy: Float,
): SwipeDirection =
    if (abs(dx) >= abs(dy)) {
        if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
    } else {
        if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP
    }

/**
 * 这一段算不算一次滑动 —— 三条缺一不可。
 *
 *  · [samePointer]：抬手的必须还是按下的那一根手指（拿别人的终点算时长，等于把两段轨迹拼成一条）；
 *  · [distancePx] 够 [slopPx]：还在 slop 里就是点按，不该产出「零距离的滑动」交给动作表去匹配；
 *  · [durationMs] 没超 [maxDurationMs]：超过就当用户在长按 / 拖别的东西，不是划。
 *
 *  **判据是峰值位移**（调用方传的是离起点最远那一点）：划出去又划回来仍然算滑动，
 *  拿终点算会把它判成点按。
 */
fun swipeAccepted(
    samePointer: Boolean,
    distancePx: Float,
    slopPx: Float,
    durationMs: Long,
    maxDurationMs: Long,
): Boolean = samePointer && distancePx >= slopPx && durationMs <= maxDurationMs
