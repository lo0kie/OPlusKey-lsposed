package com.lookie.opluskey.core.judge

import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.KeyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `core/judge` 那几件纯算术的判据 —— 组合匹配里**最容易错、又最难在真机上复现**的边界。
 *
 *  这一族原来住在 `inject/input/ComboDetector` 里，而 `:app` 没有 test 源集（AGENTS 二、1.2），
 *  所以那些边界全靠手按：窗口差 1ms、首键早就不在窗口里、一颗成员都没有、连击数超过封顶。
 *  搬出来之后这些都能在这里钉住 —— 这也是把它们搬出来的**唯一理由**。
 */
class JudgeMathTest {
    private val a = KeyId.custom(KeyId.customId(1), "A", 101)
    private val b = KeyId.custom(KeyId.customId(2), "B", 102)
    private val c = KeyId.custom(KeyId.customId(3), "C", 103)

    // ---- 候选池 ----

    @Test
    fun poolTakesWindowedAndStillHeld() {
        //  a 在窗口内、c 早就不在窗口里但**还按着**（按住档的首键就是这种）、b 既过期又松了
        val presses = mapOf(a to 1_000L, b to 500L, c to 100L)
        val pool = poolWithinWindow(now = 1_100L, window = 200L, presses = presses, held = setOf(c), self = a)
        assertEquals("仍按着的那颗要进池，过期又松手的不进", listOf(c), pool.filter { it != a })
    }

    @Test
    fun poolIgnoresKeysNeitherInWindowNorHeld() {
        val presses = mapOf(b to 500L)
        val pool = poolWithinWindow(now = 1_100L, window = 200L, presses = presses, held = emptySet(), self = a)
        assertEquals("过期且已松手的键不该进池", listOf(a), pool)
    }

    @Test
    fun poolStartsWithSelfAndKeepsItOnce() {
        //  self 自己也在这张表里（判定器先记按下、再拼池）—— 池里必须只有一份，且排在最前
        val presses = mapOf(a to 1_050L, b to 1_040L)
        val pool = poolWithinWindow(now = 1_100L, window = 200L, presses = presses, held = emptySet(), self = a)
        assertEquals("自身排最前且只出现一次", 2, pool.size)
        assertEquals(a, pool.first())
        assertEquals(listOf(a, b), pool)
    }

    // ---- 成员新鲜度 ----

    @Test
    fun followersFreshCountsWholeWindowFromFirstMember() {
        //  固定窗口起点：从**第一个成员**起算一个整窗口 —— 后面那颗再新，超过就是过期
        assertTrue(
            followersFresh(
                now = 1_300L,
                followerPressAt = listOf(1_100L, 1_290L),
                fixedStart = true,
                wholeWindowMs = 300L,
                perFollowerWindowMs = 50L,
            ),
        )
        assertFalse(
            followersFresh(
                now = 1_450L,
                followerPressAt = listOf(1_100L, 1_440L),
                fixedStart = true,
                wholeWindowMs = 300L,
                perFollowerWindowMs = 50L,
            ),
        )
    }

    @Test
    fun followersFreshIsTrueWithoutFollowers() {
        //  两颗的组合（首键 + 一颗成员）没有「成员之间」的间隔可量
        assertTrue(
            followersFresh(now = 9_999L, followerPressAt = emptyList(), fixedStart = true, wholeWindowMs = 1L, perFollowerWindowMs = 1L),
        )
        assertTrue(
            followersFresh(now = 9_999L, followerPressAt = emptyList(), fixedStart = false, wholeWindowMs = 1L, perFollowerWindowMs = 1L),
        )
    }

    @Test
    fun followersFreshChecksEachGapWhenNotFixedStart() {
        //  逐次等：每一颗各自往前一个窗口（量的是相邻两次按下）
        assertTrue(
            followersFresh(
                now = 1_100L,
                followerPressAt = listOf(1_000L, 1_050L),
                fixedStart = false,
                wholeWindowMs = 10L,
                perFollowerWindowMs = 100L,
            ),
        )
        //  只要有一颗超了，整档不成立（不是「平均够」也不是「最宽那颗够」）
        assertFalse(
            followersFresh(
                now = 1_100L,
                followerPressAt = listOf(1_000L, 1_050L, 900L),
                fixedStart = false,
                wholeWindowMs = 10L,
                perFollowerWindowMs = 100L,
            ),
        )
    }

    // ---- 整串跨度 ----

    @Test
    fun spanUsesEarliestMemberNotConfigOrder() {
        //  配置序是 [首键, B]，可用户先按的是 C 那一颗 —— 跨度必须按最早的算
        assertEquals(300L, spanFrom(now = 1_300L, memberPressAt = listOf(1_100L, 1_000L)))
    }

    @Test
    fun spanIsZeroWithoutMembers() {
        assertEquals(0L, spanFrom(now = 1_300L, memberPressAt = emptyList()))
    }

    // ---- 派发击数 ----

    @Test
    fun fireCountClampsBothEnds() {
        assertEquals("0 击也要按 1 击派", 1, fireCount(clicks = 0, cap = 3))
        assertEquals("超过封顶按封顶派", 3, fireCount(clicks = 5, cap = 3))
        assertEquals("正常值原样", 2, fireCount(clicks = 2, cap = 3))
        assertEquals("封顶只有 1 时恒派 1 击", 1, fireCount(clicks = 3, cap = 1))
    }

    // ---- 按下序号 ----

    @Test
    fun pressOrderSortsBySequenceAndPutsUnknownLast() {
        val seq = mapOf(b to 1, a to 2)
        assertEquals("按序号排；查不到序号的排末尾", listOf(b.id, a.id, c.id), pressOrder(listOf(a, b, c), seq) { it.id })
    }

    // ---- 有序档筛选 ----

    @Test
    fun orderMatchReturnsSameListWhenNothingIsOrdered() {
        val candidates = listOf(ComboKey(id = "cmb1", keys = listOf(a.id, b.id)), ComboKey(id = "cmb2", keys = listOf(b.id, a.id)))
        //  全是等窗口 / 不分顺序那一档 ⇒ 原表原样返回（省掉一次遍历的那条近路）
        assertSame(candidates, orderMatch(candidates, listOf(b.id, a.id), ordered = { it.ordered }) { k, s -> k.matchesOrdered(s) })
    }

    @Test
    fun orderMatchDropsOrderedCombosThatDoNotFitTheSequence() {
        val forward = ComboKey(id = "cmb1", keys = listOf(a.id, b.id), ordered = true)
        val backward = ComboKey(id = "cmb2", keys = listOf(b.id, a.id), ordered = true)
        val anyOrder = ComboKey(id = "cmb3", keys = listOf(a.id, b.id))
        val matched =
            orderMatch(listOf(forward, backward, anyOrder), listOf(a.id, b.id), ordered = { it.ordered }) { k, s -> k.matchesOrdered(s) }
        assertEquals("只有顺着按的那一档与不分顺序那档留下", listOf(forward, anyOrder), matched)
    }

    @Test
    fun orderedComboIgnoresAnEarlierUnrelatedKey() {
        val ordered = ComboKey(id = "cmb1", keys = listOf(a.id, b.id, c.id), ordered = true)
        //  这一组开始之前先点过一下 c（还在同一个扫描窗口里），真实顺序是 c → a → b → c
        val matched = orderMatch(listOf(ordered), listOf(c.id, a.id, b.id, c.id), ordered = { it.ordered }) { k, s -> k.matchesOrdered(s) }
        assertEquals("开头多一颗不该把有序组合判掉", listOf(ordered), matched)
    }

    @Test
    fun orderedComboStillRejectsAKeyBetweenMembers() {
        val ordered = ComboKey(id = "cmb1", keys = listOf(a.id, b.id, c.id), ordered = true)
        //  成员之间夹了一颗别的键：结尾三位是 c → b → c，与 a → b → c 不同
        assertTrue(
            orderMatch(listOf(ordered), listOf(a.id, c.id, b.id, c.id), ordered = { it.ordered }) { k, s -> k.matchesOrdered(s) }.isEmpty(),
        )
    }

    // ---- 挂住的组合自愈 ----

    @Test
    fun stuckTimesOutEvenWhileStillHeld() {
        //  挂得比上限还久 ⇒ 一律自愈（抬手丢了的时候，「还按着」正是假象）
        assertEquals(ComboStuck.TIMEOUT, comboStuck(formedAt = 0L, now = 30_001L, anyDown = true, waiting = false, maxMs = 30_000L))
    }

    @Test
    fun stuckExactlyAtTheLimitIsStillNormal() {
        //  边界：判据是严格的 `>`，正好等于上限还不算挂住
        assertEquals(ComboStuck.NONE, comboStuck(formedAt = 0L, now = 30_000L, anyDown = true, waiting = false, maxMs = 30_000L))
    }

    @Test
    fun stuckWhenNoMemberIsHeld() {
        //  没超时、一颗都不按着、也没在等更宽的一档 ⇒ 抬手全丢了
        assertEquals(ComboStuck.NO_MEMBER_DOWN, comboStuck(formedAt = 0L, now = 500L, anyDown = false, waiting = false, maxMs = 30_000L))
    }

    @Test
    fun waitingForAWiderComboIsNormal() {
        //  顺序按下也算组合：等更宽那一档时成员本来就可以全部松手
        assertEquals(ComboStuck.NONE, comboStuck(formedAt = 0L, now = 500L, anyDown = false, waiting = true, maxMs = 30_000L))
    }

    @Test
    fun timeoutWinsOverEveryOtherReason() {
        assertEquals(ComboStuck.TIMEOUT, comboStuck(formedAt = 0L, now = 40_000L, anyDown = false, waiting = true, maxMs = 30_000L))
    }
}
