package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * **v98 及更早的逐字段格式，读进来必须一分不差。**
 *
 *  这一条是这次改格式的兜底：新格式只有 `actions` + `params` 两层，老文件全靠
 *  [LegacyConfigDto] 那一份冻结副本还原。它一坏，用户机器上所有存量配置当场变默认
 *  （页面还照常打开、不报错），比任何别的回归都难查。
 *
 *  做法：用旧类型**写出一份 v98 文本**，再走正式的 `parseOrNull` 读回来，两个 `Config` 逐字段比。
 */
class LegacyMigrationTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    /** 每一族都放一个非出厂值：单键动作 / 后长按 / 多击 / 独立设置、组合的成员连击与窗口。 */
    private fun sample(): Config {
        val plus =
            KeyConfig
                .defaultFor(KeyId.PLUS)
                .copy(
                    single = Action.parse("flashlight"),
                    long = Action.parse("flashlight"),
                    longPressMs = 420,
                    clickThenLongOn = true,
                    clickThenLong = Action.parse("flashlight"),
                    clickThenLongPressMs = 380,
                    extraClicksOn = setOf(3),
                    extraClicks = mapOf(3 to Action.parse("flashlight")),
                    useOwnSettings = true,
                    vibrate = true,
                    vibrateStrength = 2,
                    toastTemplate = "开了 %s",
                )
        val combo =
            ComboKey(
                id = "cmb1",
                keys = listOf(KeyId.PLUS.id, "volup"),
            ).copy(
                mode = ComboKey.MODE_HOLD,
                single = Action.parse("flashlight"),
                memberClicks = mapOf(3 to Action.parse("flashlight")),
                memberClicksOn = setOf(3),
                memberClickThenLongPressMs = 300,
                holdWindowMs = 260,
            )
        return Config.DEFAULT.copy(
            keys = Config.DEFAULT.keys + (KeyId.PLUS to plus),
            combos = listOf(combo),
        )
    }

    @Test
    fun `v98 的逐字段格式读回来一分不差`() {
        val expected = sample()
        val text = json.encodeToString(LegacyConfigDto.from(expected, ConfigCodec.VERSION - 1, md5 = ""))
        val read = ConfigCodec.parseOrNull(text)
        assertNotNull("v98 文件读不出来（Legacy 那条路断了）", read)
        assertEquals(expected, read)
    }

    /** 新格式往返：写出 → 读回 → 再写出，两段文本逐字相同（md5 与全量写两条口径一起验）。 */
    @Test
    fun `新格式往返之后的文本逐字相同`() {
        val first = ConfigCodec.serialize(sample())
        val read = ConfigCodec.parseOrNull(first)
        assertNotNull("新格式读不回来", read)
        assertEquals(first, ConfigCodec.serialize(read!!))
    }
}
