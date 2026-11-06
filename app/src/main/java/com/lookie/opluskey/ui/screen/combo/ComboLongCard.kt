package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.LongPressSettingsCard
import com.lookie.opluskey.ui.LongPressSpec
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.writeAndNotice

// 「组合键」长按那一档的设置卡 —— 与单键的 `LongRepeatCard` **同一张卡**（`ui/LongPressSettingsCard.kt`：
// 行序 / 文案 / 可见性判据都在那边，改一次两边一起走）。
// 这里只答两件事：读 `ComboKey` 的那几位、每次改动落到这一条组合的哪一格。

/**
 * 长按那一档的**唯一**一张卡：连续触发（+ 连续触发时振动 + 触发间隔）+ 长按延迟。
 *
 *  比单键那一张少两行（「松手才触发」与「松手后再执行」）—— 组合没有「松手」这条通道，
 *  见下面 `releaseSupported` 那段。
 *
 * @param enabled 整页闸门（总闸 + 链路就绪 + 配置可写）—— 关着时整张卡不吃点击
 */
@Composable
internal fun SettingsPage.ComboLongCard(
    combo: ComboKey,
    enabled: Boolean,
) {
    LongPressSettingsCard(
        LongPressSpec(
            repeat = combo.longRepeat,
            vibrate = combo.longRepeatVibrate,
            intervalMs = combo.longRepeatIntervalMs,
            pressMs = combo.longPressMs,
            fireOnRelease = false,
            //  「松手」这条通道目前只有单键那一档：组合的判定侧只回「哪一档、短按还是长按」，
            //  「到点先不动、抬手再执行」与「松手再执行另一个动作」这两档都不存在 ——
            //  所以这一页那两行都不画。摆一个拨了没反应的开关比没有更糟。
            releaseSupported = false,
            releaseOn = false,
            releaseLabel = null,
            releaseBound = false,
            enabled = enabled,
            delayEnabled = enabled,
            onRepeat = { on -> setComboRepeat(combo.id, on) },
            onVibrate = { on -> setComboRepeatVibrate(combo.id, on) },
            onInterval = { v -> updateCombo(combo.id, "long_repeat_interval_ms=$v") { it.copy(longRepeatIntervalMs = v) } },
            onPressMs = { v -> updateCombo(combo.id, "long_press_ms=$v") { it.copy(longPressMs = v) } },
            onFireOnReleaseToggle = { },
            onReleaseToggle = { },
            onReleasePick = { },
        ),
    )
}

/** 拨「长按连续触发」—— 整页一个位（[HINT_SLOT_COMBO]），后一句改前一句的字。 */
private fun SettingsPage.setComboRepeat(
    id: String,
    on: Boolean,
) {
    writeAndNotice(
        configState.config.value.copy(
            combos =
                configState.config.value.combos.map {
                    if (it.id == id) it.copy(longRepeat = on) else it
                },
        ),
        "combo:$id:long_repeat=${if (on) 1 else 0}",
        slot = HINT_SLOT_COMBO,
    ) {
        switchNotice(I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"), on)
    }
}

/** 拨「连续触发时振动」—— 与 [setComboRepeat] 同一档、同一个位。 */
private fun SettingsPage.setComboRepeatVibrate(
    id: String,
    on: Boolean,
) {
    writeAndNotice(
        configState.config.value.copy(
            combos =
                configState.config.value.combos.map {
                    if (it.id == id) it.copy(longRepeatVibrate = on) else it
                },
        ),
        "combo:$id:long_repeat_vibrate=${if (on) 1 else 0}",
        slot = HINT_SLOT_COMBO,
    ) {
        switchNotice(I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"), on)
    }
}
