package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.writeAndNotice
import io.github.suqi8.coui.kmp.basic.CardListPosition

// 「组合键」长按那一档的设置卡 —— 与单键的 `LongRepeatCard` 同一个形状、同一套行序，
// 只是写的是 `ComboKey` 的那几位（`longRepeat` / `longRepeatVibrate` / `longRepeatIntervalMs` / `longPressMs`）。
//
// 为什么不直接把单键那张卡参数化：那张卡读的是 `KeyWatchState.editing` 与 `configState.config.keys`，
// 而组合这边要写的是 `configState.config.combos` 里的某一档 —— 抽取一层「可读写长按设置」的抽象
// 会让那张 190 行的卡里每一行都多一次间接；两边的字段名本来就一一对应，各写一份更直白。
// 两边**行序、文案、可见性判据必须一致**（用户口径「和另一个一样」）—— 改这里时对着那边一起看。

/**
 * 长按那一档的**唯一**一张卡：连续触发（+ 连续触发时振动 + 触发间隔）+ 长按延迟。
 *
 * @param enabled 整页闸门（总闸 + 链路就绪 + 配置可写）—— 关着时整张卡不吃点击
 */
@Composable
internal fun SettingsPage.ComboLongCard(
    combo: ComboKey,
    enabled: Boolean,
) {
    CardList(
        rows =
            listOf(
                // 「长按连续触发」—— 副标题按开关真假各一句（与单键那张卡同一个句式）
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"),
                    summaryOn = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_ON, "按住不放，按间隔重复执行"),
                    summaryOff = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_OFF, "按住不放只执行一次"),
                    icon = PageIcons.LongRepeat,
                    checked = combo.longRepeat,
                    enabled = enabled,
                ) { on ->
                    writeAndNotice(
                        configState.config.value.copy(
                            combos =
                                configState.config.value.combos.map {
                                    if (it.id == combo.id) it.copy(longRepeat = on) else it
                                },
                        ),
                        "combo:${combo.id}:long_repeat=${if (on) 1 else 0}",
                        slot = HINT_SLOT_COMBO,
                    ) {
                        switchNotice(I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"), on)
                    }
                },
                // 「连续触发时振动」与「触发间隔」只在开关打开时存在 —— 与单键那张卡同一套
                // （先振动、后间隔；显隐连分隔线一起做）
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"),
                    summaryOn = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_ON, "每次重复都振动"),
                    summaryOff = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_OFF, "只在长按触发时振动一次"),
                    icon = PageIcons.Vibrate,
                    checked = combo.longRepeatVibrate,
                    enabled = enabled,
                    visible = combo.longRepeat,
                ) { on ->
                    writeAndNotice(
                        configState.config.value.copy(
                            combos =
                                configState.config.value.combos.map {
                                    if (it.id == combo.id) it.copy(longRepeatVibrate = on) else it
                                },
                        ),
                        "combo:${combo.id}:long_repeat_vibrate=${if (on) 1 else 0}",
                        slot = HINT_SLOT_COMBO,
                    ) {
                        switchNotice(I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"), on)
                    }
                },
                CardRow.Custom(visible = combo.longRepeat) { position ->
                    ComboRepeatIntervalRow(position, combo, enabled)
                },
                // 「长按延迟」—— 多久算长按（这一档自己的阈值，判定侧读它排那一枪）
                CardRow.Custom { position ->
                    ComboLongPressRow(position, combo, enabled)
                },
            ),
    )
}

/** 「触发间隔」那一行 —— 连续触发隔多久重放一次。 */
@Composable
private fun SettingsPage.ComboRepeatIntervalRow(
    position: CardListPosition,
    combo: ComboKey,
    enabled: Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.BINDING_REPEAT_INTERVAL_TITLE, "触发间隔"),
                icon = PageIcons.DelayRepeat,
                summary = I18n.get(Strings.BINDING_REPEAT_INTERVAL_DESC, "长按连续触发的重复间隔"),
                min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                value = combo.longRepeatIntervalMs,
                default = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                enabled = enabled,
            ),
    ) { v ->
        updateCombo(combo.id, "long_repeat_interval_ms=$v") { it.copy(longRepeatIntervalMs = v) }
    }
}

/** 「长按延迟」那一行 —— 两颗键一起按住多久算长按。 */
@Composable
private fun SettingsPage.ComboLongPressRow(
    position: CardListPosition,
    combo: ComboKey,
    enabled: Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_TITLE, "长按延迟"),
                summary = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_DESC, "多久算长按，比它短算单击"),
                icon = PageIcons.DelayHold,
                min = KeyConfig.MIN_LONG_PRESS_MS,
                max = KeyConfig.MAX_LONG_PRESS_MS,
                value = combo.longPressMs,
                default = KeyConfig.DEFAULT_LONG_PRESS_MS,
                enabled = enabled,
            ),
    ) { v ->
        updateCombo(combo.id, "long_press_ms=$v") { it.copy(longPressMs = v) }
    }
}
