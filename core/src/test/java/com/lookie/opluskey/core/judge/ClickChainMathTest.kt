package com.lookie.opluskey.core.judge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连击链的三处判据 —— 边界全在「差 1ms」「数到第几击」「开关开着但没有动作」这几种位置上。
 *
 *  这一族原先散在 `GestureDetector.onKeyDownLocked` / `onKeyUpLocked` / `shotDecision`
 *  三个方法的分支里，`:app` 没有 test 源集（AGENTS 二、1.2），只能靠手按。搬出来的目的
 *  就是把这几条边界钉住：按一次键出两个动作、双击绑的动作不执行、单击晚一个窗口，都从这来。
 */
class ClickChainMathTest {
    // ---- 按下那一刻 ----

    @Test
    fun pressInsideWindowContinuesTheChain() {
        assertEquals(PressEntry.CONTINUE, pressEntry(now = 1_000L, sawFirstRelease = true, windowAt = 1_200L))
        //  截止时刻这一刻仍算在窗口里（`<=`，与排窗那侧写的 `now + window` 是同一个数）
        assertEquals(PressEntry.CONTINUE, pressEntry(now = 1_200L, sawFirstRelease = true, windowAt = 1_200L))
    }

    @Test
    fun pressAfterWindowCatchesUp() {
        //  过期 1ms 就该补派发：不补的话前一次的单击就此丢掉
        assertEquals(PressEntry.CATCH_UP, pressEntry(now = 1_201L, sawFirstRelease = true, windowAt = 1_200L))
    }

    @Test
    fun pressBeforeFirstReleaseIsFresh() {
        //  首击还按着（没抬手）⇒ 这不是第二击，是同一按里的重复 DOWN
        assertEquals(PressEntry.FRESH, pressEntry(now = 1_100L, sawFirstRelease = false, windowAt = 1_200L))
    }

    @Test
    fun pressWithNoWindowIsFresh() {
        //  抬过手但还没排窗（`windowAt == 0`）—— 落到全新一轮，而不是 CATCH_UP
        assertEquals(PressEntry.FRESH, pressEntry(now = 1_100L, sawFirstRelease = true, windowAt = 0L))
    }

    // ---- 进了链之后 ----

    @Test
    fun secondClickWithChainThenLongAndNoThirdIsFinal() {
        assertEquals(ChainStep.CHAINED_FINAL, chainStep(clicks = 2, cap = 2, clickThenLongOn = true))
    }

    @Test
    fun secondClickWithChainThenLongAndMoreToWaitArmsTheLongShot() {
        assertEquals(ChainStep.CHAINED_WAITING, chainStep(clicks = 2, cap = 3, clickThenLongOn = true))
        assertEquals(ChainStep.CHAINED_WAITING, chainStep(clicks = 2, cap = 4, clickThenLongOn = true))
    }

    @Test
    fun secondClickWithoutChainThenLongKeepsWaiting() {
        //  这一档关着 ⇒ 第 2 击只是普通连击，不排长按枪
        assertEquals(ChainStep.KEEP_WAITING, chainStep(clicks = 2, cap = 3, clickThenLongOn = false))
        assertEquals(ChainStep.KEEP_WAITING, chainStep(clicks = 1, cap = 3, clickThenLongOn = true))
    }

    @Test
    fun reachingCapFires() {
        assertEquals(ChainStep.FIRE, chainStep(clicks = 3, cap = 3, clickThenLongOn = true))
        //  超过封顶也照样判（用户连按了第 4 下而这一档只认到第 3 击，击数由 fireCount 夹回去）
        assertEquals(ChainStep.FIRE, chainStep(clicks = 4, cap = 3, clickThenLongOn = false))
        //  cap = 1 时第 1 击就当到顶（正常走不到：`releaseStep` 把这一支提前收成当场单击）
        assertEquals(ChainStep.FIRE, chainStep(clicks = 1, cap = 1, clickThenLongOn = false))
    }

    // ---- 抬手那一刻 ----

    @Test
    fun longPressedWinsOverEverything() {
        //  长按已经在按下期间触发过 ⇒ 抬手只收尾，绝不能再补一次单击
        val step =
            releaseStep(
                longPressFired = true,
                doubleFired = true,
                clicks = 1,
                cap = 1,
                fixedWindowStart = true,
                windowAt = 1_200L,
                now = 1_000L,
            )
        assertEquals(ReleaseStep.FINISH_LONG, step)
    }

    @Test
    fun chainedReleaseFiresCountedClicks() {
        val step =
            releaseStep(
                longPressFired = false,
                doubleFired = true,
                clicks = 3,
                cap = 3,
                fixedWindowStart = false,
                windowAt = 1_200L,
                now = 1_000L,
            )
        assertEquals(ReleaseStep.FIRE_CHAIN, step)
    }

    @Test
    fun nothingToWaitForIsSettledOnRelease() {
        //  三样多击开关都关着 ⇒ 抬手当场单击，不排窗口（否则单击白等一个窗口）
        val step =
            releaseStep(
                longPressFired = false,
                doubleFired = false,
                clicks = 1,
                cap = 1,
                fixedWindowStart = true,
                windowAt = 1_200L,
                now = 1_000L,
            )
        assertEquals(ReleaseStep.SINGLE_NOW, step)
    }

    @Test
    fun fixedStartFirstReleaseStartsTheOnlyWindow() {
        val step =
            releaseStep(
                longPressFired = false,
                doubleFired = false,
                clicks = 1,
                cap = 3,
                fixedWindowStart = true,
                windowAt = 0L,
                now = 1_000L,
            )
        assertEquals(ReleaseStep.START_WINDOW, step)
    }

    @Test
    fun fixedStartHoldsWhileWindowIsOpenAndFiresWhenItLapses() {
        val open =
            releaseStep(
                longPressFired = false,
                doubleFired = false,
                clicks = 2,
                cap = 3,
                fixedWindowStart = true,
                windowAt = 1_200L,
                now = 1_199L,
            )
        assertEquals(ReleaseStep.STILL_WAITING, open)
        //  到点这一刻（`now == windowAt`）就判：那一枪可能还没跑到，不能干等
        val lapsed =
            releaseStep(
                longPressFired = false,
                doubleFired = false,
                clicks = 2,
                cap = 3,
                fixedWindowStart = true,
                windowAt = 1_200L,
                now = 1_200L,
            )
        assertEquals(ReleaseStep.FIRE_EXPIRED, lapsed)
    }

    @Test
    fun slidingStartRearmsOnEveryRelease() {
        //  逐次等：哪怕窗口还开着，这次抬手也要重新起算一个
        val step =
            releaseStep(
                longPressFired = false,
                doubleFired = false,
                clicks = 2,
                cap = 3,
                fixedWindowStart = false,
                windowAt = 1_200L,
                now = 1_000L,
            )
        assertEquals(ReleaseStep.START_WINDOW, step)
    }

    // ---- 长按那一枪 ----

    @Test
    fun longShotWaitsOnlyWhileStillOnTheSecondClick() {
        assertTrue(longShotStillWaiting(longPressAt = 1_500L, sawFirstRelease = false, clicks = 2))
        assertTrue(longShotStillWaiting(longPressAt = 1_500L, sawFirstRelease = false, clicks = 1))
        //  已数到第 3 击 ⇒ 用户还在连击，这一枪作废
        assertFalse(longShotStillWaiting(longPressAt = 1_500L, sawFirstRelease = false, clicks = 3))
        //  已经抬过手
        assertFalse(longShotStillWaiting(longPressAt = 1_500L, sawFirstRelease = true, clicks = 2))
        //  没排枪 / 已经判过
        assertFalse(longShotStillWaiting(longPressAt = 0L, sawFirstRelease = false, clicks = 2))
    }

    @Test
    fun longShotIsChainedOnlyFromTheSecondClickWithTheSwitchOn() {
        assertTrue(longShotIsChained(clickThenLongOn = true, clicks = 2))
        assertFalse(longShotIsChained(clickThenLongOn = true, clicks = 1))
        assertFalse(longShotIsChained(clickThenLongOn = false, clicks = 2))
    }
}
