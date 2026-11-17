package com.lookie.opluskey.core.intercept

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 拦截侧的两句归属判据 —— 顺序就是语义，全靠这几条钉住。
 *
 *  原先它们是 `SideKeyInterceptor.decide` 与 `ComboOnlyNative.onDown` 里的 `when` 条件，
 *  `:app` 没有 test 源集（AGENTS 二、1.2）：判据顺序一改，现象是「按一下触发两个动作」
 *  或者「按了没反应」，而这两种都只能真机手按才看得出来。
 */
class KeyClaimTest {
    // ---- 归模块哪一档 ----

    @Test
    fun comboOnlyBeatsRomGesture() {
        //  这颗键要的正是不判单键，而 ROM 手势码本身就是单键手势 —— 认成手势就反了
        assertEquals(ClaimKind.COMBO_ONLY, claimKindOf(comboOnly = true, romGesture = true, romMember = true))
    }

    @Test
    fun romGestureOnlyWhenNothingElseClaims() {
        assertEquals(ClaimKind.ROM_GESTURE, claimKindOf(comboOnly = false, romGesture = true, romMember = true))
        assertEquals(ClaimKind.ROM_GESTURE, claimKindOf(comboOnly = false, romGesture = true, romMember = false))
    }

    @Test
    fun romMiddleCodeIsSwallowed() {
        assertEquals(ClaimKind.ROM_SWALLOW, claimKindOf(comboOnly = false, romGesture = false, romMember = true))
    }

    @Test
    fun plainKeyIsClaimed() {
        assertEquals(ClaimKind.CLAIMED, claimKindOf(comboOnly = false, romGesture = false, romMember = false))
    }

    // ---- 压不压、压到什么时候 ----

    @Test
    fun claimedPressIsNeverHeld() {
        //  归组合的那一下永久吞：不管它还兼不兼着窗口成员
        assertEquals(HoldStep.TAKEN, holdStepOf(taken = true, windowMember = true, holdStarter = true))
        assertEquals(HoldStep.TAKEN, holdStepOf(taken = true, windowMember = false, holdStarter = false))
    }

    @Test
    fun holdStarterIsHeldUntilRelease() {
        //  按住那档的起点键没有上界（只看还按着没有），所以不能按窗口到点就漏出去
        assertEquals(HoldStep.UNTIL_RELEASE, holdStepOf(taken = false, windowMember = false, holdStarter = true))
        //  两样都占时按更严的那一档：压到抬手
        assertEquals(HoldStep.UNTIL_RELEASE, holdStepOf(taken = false, windowMember = true, holdStarter = true))
    }

    @Test
    fun windowMemberIsHeldUntilTheWindowEnds() {
        assertEquals(HoldStep.UNTIL_WINDOW, holdStepOf(taken = false, windowMember = true, holdStarter = false))
    }

    @Test
    fun neitherMeansPassthrough() {
        //  既不认窗口、也不是先按住的那颗：这一下永远配不上，一秒都不该压
        assertEquals(HoldStep.PASS, holdStepOf(taken = false, windowMember = false, holdStarter = false))
    }
}
