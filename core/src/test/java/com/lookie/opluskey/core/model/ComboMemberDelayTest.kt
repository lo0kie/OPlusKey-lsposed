package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.config.ConfigCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 组合成员「连击后长按延迟」的**两层取值**判据 —— 与单键 `clickThenLongPressMs` 同一套结构：
 * 这一档自己一份（`ComboKey.memberClickThenLongPressMs`）、公共一份
 * （`GlobalConfig.comboMemberClickThenLongPressMs`），没开「使用独立设置」的档读公共那一份。
 *
 *  这三条各自的失效方式都不一样，所以分开写：
 *  · 哨兵 `0` 被当成毫秒数 ⇒ 成员后长按一到点就触发；
 *  · 总开关失效 ⇒ 关掉独立设置之后行为没变（「关了没关掉」）；
 *  · DTO 少了这一格 / 写回时丢了 ⇒ 用户改的数下次进设置页就回到出厂值。
 */
class ComboMemberDelayTest {
    private fun combo(
        own: Boolean,
        ms: Int,
    ): ComboKey =
        ComboKey(
            id = "cmb1",
            keys = listOf(KeyId.PLUS.id, "other"),
            mode = ComboKey.MODE_HOLD,
            useOwnSettings = own,
            memberClickThenLongPressMs = ms,
        )

    private fun config(globalMs: Int): Config =
        Config.DEFAULT.copy(global = Config.DEFAULT.global.copy(comboMemberClickThenLongPressMs = globalMs))

    @Test
    fun ownSettingsOffFollowsTheGlobalOne() {
        val cfg = config(globalMs = 600)
        assertEquals(600, cfg.comboMemberThenLongPressFor(combo(own = false, ms = 250)))
    }

    @Test
    fun ownSettingsOnUsesItsOwnValue() {
        val cfg = config(globalMs = 600)
        assertEquals(250, cfg.comboMemberThenLongPressFor(combo(own = true, ms = 250)))
    }

    @Test
    fun ownSettingsOnWithoutAValueFallsBackToTheFactoryOne() {
        // 哨兵 0 = 「没写过」：独立设置开着却一格没写过时取出厂值，**不抄公共值**
        // （口径与 `Config.comboHoldMinFor` 同一条）
        val cfg = config(globalMs = 600)
        val untouched = combo(own = true, ms = ComboKey.MEMBER_CTL_PRESS_MS_FOLLOW_GLOBAL)
        assertEquals(KeyConfig.DEFAULT_LONG_PRESS_MS, cfg.comboMemberThenLongPressFor(untouched))
    }

    @Test
    fun bothLayersSurviveTheRoundTrip() {
        val cfg =
            config(globalMs = 480).copy(
                combos = listOf(combo(own = true, ms = 260)),
            )
        val text = ConfigCodec.serialize(cfg)
        val back = ConfigCodec.parseOrNull(text)
        assertNotNull("刚写出去的配置读不回来", back)
        val combo = back!!.combos.single()
        assertEquals("这一档自己的那份没落盘", 260, combo.memberClickThenLongPressMs)
        assertEquals("公共那份没落盘", 480, back.global.comboMemberClickThenLongPressMs)
        assertEquals(260, back.comboMemberThenLongPressFor(combo))
    }
}
