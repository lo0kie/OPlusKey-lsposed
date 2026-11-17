package com.lookie.opluskey.core.judge

import com.lookie.opluskey.core.model.ComboKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两颗组合的三处挑法 —— 「配不配」「挑哪一档」「能不能升级」。
 *
 *  原先都是 `ComboDetector.pairFor` / `absorb` 里的条件与循环，`:app` 没有 test 源集
 *  （AGENTS 二、1.2），于是「按住档优先」「扫最宽的、判自己的」这些顺序只能靠手按。
 *  这里把三条判据各自钉住。
 */
class PairMathTest {
    private val wait = ComboKey(id = "cmb1", keys = listOf("k1", "k2"))
    private val hold = ComboKey(id = "cmb2", keys = listOf("k1", "k2"), mode = ComboKey.MODE_HOLD)

    // ---- 等窗口那一档 ----

    @Test
    fun waitPairNeedsBothTheScanWindowAndItsOwn() {
        assertTrue("两条都满足才算", pairWaitMatches(span = 200L, scanWindow = 300L, ownSpanMs = 250L))
        //  扫得到（在最宽窗口内）但这一档自己的跨度更窄 ⇒ 不算
        assertFalse(pairWaitMatches(span = 280L, scanWindow = 300L, ownSpanMs = 250L))
        //  这一档自己够宽，可压根没进最宽的那个窗口（不该走到这儿，但判据必须自洽）
        assertFalse(pairWaitMatches(span = 320L, scanWindow = 300L, ownSpanMs = 400L))
    }

    @Test
    fun waitPairAcceptsTheExactWindow() {
        //  边界：正好等于窗口仍算成立（判据是 `<=`）
        assertTrue(pairWaitMatches(span = 300L, scanWindow = 300L, ownSpanMs = 300L))
    }

    // ---- 按住那一档 ----

    @Test
    fun holdPairNeedsAllFour() {
        assertTrue(pairHoldMatches(span = 150L, stillDown = true, holdMinMs = 100L, isStarter = true, starterLongPassed = false))
    }

    @Test
    fun holdPairRejectsEachMissingCondition() {
        //  松了手就不是「按住」
        assertFalse(pairHoldMatches(span = 150L, stillDown = false, holdMinMs = 100L, isStarter = true, starterLongPassed = false))
        //  没按满下限：与「一起按」分不开（少 1ms 也不行）
        assertFalse(pairHoldMatches(span = 99L, stillDown = true, holdMinMs = 100L, isStarter = true, starterLongPassed = false))
        //  先按的那颗不是这一档的起点键
        assertFalse(pairHoldMatches(span = 150L, stillDown = true, holdMinMs = 100L, isStarter = false, starterLongPassed = false))
        //  长按已经判出来了：那颗键的动作已经执行过，再认组合就是一次按压两个动作
        assertFalse(pairHoldMatches(span = 150L, stillDown = true, holdMinMs = 100L, isStarter = true, starterLongPassed = true))
    }

    @Test
    fun holdPairAcceptsTheExactHoldMin() {
        assertTrue(pairHoldMatches(span = 100L, stillDown = true, holdMinMs = 100L, isStarter = true, starterLongPassed = false))
    }

    // ---- 逐档挑法 ----

    @Test
    fun pickPairReturnsNullWhenNothingMatches() {
        assertNull(pickPairHit(listOf(wait, hold)) { false })
        assertNull(pickPairHit(emptyList()) { true })
    }

    @Test
    fun pickPairPrefersTheHoldCombo() {
        //  两档都命中 ⇒ 取按住那档（前提更严、表态更明确），与它在表里的先后无关
        assertEquals(hold, pickPairHit(listOf(wait, hold)) { true })
        assertEquals(hold, pickPairHit(listOf(hold, wait)) { true })
    }

    @Test
    fun pickPairTakesTheLastWaitingHitWhenNoHoldMatches() {
        val other = ComboKey(id = "cmb3", keys = listOf("k1", "k2"))
        //  等窗口那档不会提前收工：后命中的覆盖前面的（与原实现「逐个赋值」一致）
        assertEquals(other, pickPairHit(listOf(wait, other)) { true })
        //  按住那档没命中时不参与，剩下最后命中的等窗口档
        assertEquals(other, pickPairHit(listOf(wait, hold, other)) { it != hold })
    }

    @Test
    fun pickPairStopsAtTheFirstHoldHit() {
        val later = ComboKey(id = "cmb4", keys = listOf("k1", "k2"))
        //  命中按住档就收工：后面的等窗口档不得再覆盖它
        assertEquals(hold, pickPairHit(listOf(hold, later)) { true })
    }

    // ---- 升级判据 ----

    @Test
    fun upgradeNeedsMoreMembersAndRoomLeft() {
        assertTrue("成员更多、窗口还没用完 ⇒ 升", isUpgrade(candidateSize = 3, currentSize = 2, elapsedMs = 200L, ownSpanMs = 250L))
        //  成员数一样（或更少）不是「更宽」
        assertFalse(isUpgrade(candidateSize = 2, currentSize = 2, elapsedMs = 200L, ownSpanMs = 250L))
        //  就算按下去也来不及：跨度已经用完
        assertFalse(isUpgrade(candidateSize = 3, currentSize = 2, elapsedMs = 251L, ownSpanMs = 250L))
    }

    @Test
    fun upgradeAcceptsTheExactSpan() {
        assertTrue(isUpgrade(candidateSize = 4, currentSize = 3, elapsedMs = 250L, ownSpanMs = 250L))
    }
}
