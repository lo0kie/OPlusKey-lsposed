package com.lookie.opluskey.core.judge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑动判定的两块纯判据 —— 方向的主轴选择与「这一段算不算数」。
 *
 *  原先都是 `SwipeJudge.directionOf` / `finish` 里的条件，`:app` 没有 test 源集
 *  （AGENTS 二、1.2）：45° 那条边界、峰值位移那条口径只能靠手划。
 */
class SwipeMathTest {
    // ---- 方向 ----

    @Test
    fun directionFollowsTheMainAxis() {
        assertEquals(SwipeDirection.RIGHT, swipeDirection(30f, 10f))
        assertEquals(SwipeDirection.LEFT, swipeDirection(-30f, 10f))
        assertEquals(SwipeDirection.DOWN, swipeDirection(10f, 30f))
        assertEquals(SwipeDirection.UP, swipeDirection(10f, -30f))
    }

    @Test
    fun exactDiagonalIsHorizontal() {
        //  两轴相等时固定归横向：不固定的话，同一条斜线换个分辨率就会翻档
        assertEquals(SwipeDirection.RIGHT, swipeDirection(20f, 20f))
        assertEquals(SwipeDirection.LEFT, swipeDirection(-20f, -20f))
        //  差分只在第二轴上也不翻档
        assertEquals(SwipeDirection.RIGHT, swipeDirection(20f, 19f))
        assertEquals(SwipeDirection.DOWN, swipeDirection(19f, 20f))
    }

    @Test
    fun zeroDistanceReportsLeft() {
        //  点按才会有零位移，它在「算不算数」那一关就被拦掉了，方向到不了上层
        assertEquals(SwipeDirection.LEFT, swipeDirection(0f, 0f))
    }

    // ---- 算不算数 ----

    @Test
    fun swipeNeedsPointerDistanceAndDuration() {
        assertTrue(swipeAccepted(samePointer = true, distancePx = 60f, slopPx = 28f, durationMs = 200L, maxDurationMs = 800L))
    }

    @Test
    fun pointerSwapVoidsTheSegment() {
        assertFalse(swipeAccepted(samePointer = false, distancePx = 60f, slopPx = 28f, durationMs = 200L, maxDurationMs = 800L))
    }

    @Test
    fun distanceUnderSlopIsATap() {
        //  差 1px 也还是点按：不许假装成「零距离的滑动」
        assertFalse(swipeAccepted(samePointer = true, distancePx = 27.9f, slopPx = 28f, durationMs = 200L, maxDurationMs = 800L))
        //  正好等于 slop 算划了
        assertTrue(swipeAccepted(samePointer = true, distancePx = 28f, slopPx = 28f, durationMs = 200L, maxDurationMs = 800L))
    }

    @Test
    fun slowDragIsNotASwipe() {
        assertFalse(swipeAccepted(samePointer = true, distancePx = 60f, slopPx = 28f, durationMs = 801L, maxDurationMs = 800L))
        //  正好等于上限仍算（判据是 `<=`）
        assertTrue(swipeAccepted(samePointer = true, distancePx = 60f, slopPx = 28f, durationMs = 800L, maxDurationMs = 800L))
    }

    @Test
    fun speedIsZeroForAZeroLengthSegment() {
        val swipe = Swipe(SwipeDirection.UP, 0f, -50f, 50f, 0L, 3)
        assertEquals(0f, swipe.speedPxPerMs, 0.0001f)
        assertEquals(0.25f, Swipe(SwipeDirection.UP, 0f, -50f, 50f, 200L, 3).speedPxPerMs, 0.0001f)
    }
}
