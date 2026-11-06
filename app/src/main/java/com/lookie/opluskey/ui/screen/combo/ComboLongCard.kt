package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.LongPressSettingsCard
import com.lookie.opluskey.ui.LongPressSpec
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appLabelOf
import com.lookie.opluskey.ui.model.releaseActionTitle
import com.lookie.opluskey.ui.openAppPickerForRelease
import com.lookie.opluskey.ui.screen.command.StepPickerPage
import com.lookie.opluskey.ui.screen.command.StepPickerSheet
import com.lookie.opluskey.ui.screen.command.StepSheetFilter
import com.lookie.opluskey.ui.screen.command.StepSheetSpec
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.writeAndNotice

// 「组合键」长按那一档的设置卡 —— 与单键的 `LongRepeatCard` **同一张卡**（`ui/LongPressSettingsCard.kt`：
// 行序 / 文案 / 可见性判据都在那边，改一次两边一起走）。
// 这里只答两件事：读 `ComboKey` 的那几位、每次改动落到这一条组合的哪一格。

/**
 * 长按那一档的**唯一**一张卡：连续触发（+ 连续触发时振动 + 触发间隔）+ 长按延迟。
 *
 *  比单键那一张少两行（「松手才触发」与「松手执行额外动作」）—— 组合没有「松手」这条通道，
 *  见下面 `releaseSupported` 那段。
 *
 * @param enabled 整页闸门（总闸 + 链路就绪 + 配置可写）—— 关着时整张卡不吃点击
 */
@Composable
internal fun SettingsPage.ComboLongCard(
    combo: ComboKey,
    enabled: Boolean,
) {
    //  挑松手动作那个抽屉的开合位（与单键那张卡同一个形状）
    var pickingRelease by remember { mutableStateOf(false) }
    LongPressSettingsCard(
        LongPressSpec(
            repeat = combo.longRepeat,
            vibrate = combo.longRepeatVibrate,
            intervalMs = combo.longRepeatIntervalMs,
            pressMs = combo.longPressMs,
            fireOnRelease = combo.longFireOnRelease,
            //  「松手」这两行现在组合键也有了（判定侧 `ComboDetector.onUp` 会补发一条
            //  `GestureTrigger.Release`，执行侧按 `ComboKey.longRelease` 取动作）。
            releaseSupported = true,
            releaseOn = combo.longReleaseOn,
            releaseLabel =
                combo.longRelease
                    .takeIf { it !is Action.None }
                    ?.let { releaseActionTitle(it.token) { pkg -> appLabelOf(pkg) } },
            releaseBound = combo.releaseBound,
            enabled = enabled,
            delayEnabled = enabled,
            onRepeat = { on -> setComboRepeat(combo.id, on) },
            onVibrate = { on -> setComboRepeatVibrate(combo.id, on) },
            onInterval = { v -> updateCombo(combo.id, "long_repeat_interval_ms=$v") { it.copy(longRepeatIntervalMs = v) } },
            onPressMs = { v -> updateCombo(combo.id, "long_press_ms=$v") { it.copy(longPressMs = v) } },
            onFireOnReleaseToggle = { on ->
                updateCombo(combo.id, "long_fire_on_release=$on") { it.copy(longFireOnRelease = on) }
            },
            onReleaseToggle = { on -> updateCombo(combo.id, "long_release_on=$on") { it.copy(longReleaseOn = on) } },
            onReleasePick = { pickingRelease = true },
        ),
    )
    //  挑松手动作：与单键那张卡**同一个抽屉**（项目里挑动作只有这一个清单），
    //  只是写回落在这一档组合的 `longRelease` 上。
    StepPickerSheet(
        show = pickingRelease,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手执行额外动作"),
                current = combo.longRelease.takeIf { it !is Action.None },
                onOpenPicker = { page ->
                    pickingRelease = false
                    when (page) {
                        StepPickerPage.App -> openAppPickerForRelease(combo = combo.id)
                        StepPickerPage.AppStop -> openAppPickerForRelease(stop = true, combo = combo.id)
                        StepPickerPage.Vars -> Unit
                    }
                },
                onPick = { token -> writeComboLongRelease(combo.id, Action.parse(token)) },
                onDismiss = { pickingRelease = false },
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}

/** 组合键的「松手执行额外动作」写回 —— 与单键 `writeLongRelease` 同一套（字段同名）。 */
internal fun SettingsPage.writeComboLongRelease(
    comboId: String,
    action: Action,
): Boolean = updateCombo(comboId, "long_release=${action.token}") { it.copy(longRelease = action) }

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
