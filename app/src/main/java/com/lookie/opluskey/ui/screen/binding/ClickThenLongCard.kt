package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.actionIcon
import com.lookie.opluskey.ui.model.text.releaseActionTitle
import com.lookie.opluskey.ui.row.LongPressSections
import com.lookie.opluskey.ui.row.LongPressSettingsCard
import com.lookie.opluskey.ui.row.LongPressSpec
import com.lookie.opluskey.ui.screen.command.StepPickerPage
import com.lookie.opluskey.ui.screen.command.StepPickerSheet
import com.lookie.opluskey.ui.screen.command.StepSheetFilter
import com.lookie.opluskey.ui.screen.command.StepSheetSpec
import com.lookie.opluskey.ui.state.binding.KeyWatchState
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.wire.appNameOf
import com.lookie.opluskey.ui.write.openAppPickerForRelease
import com.lookie.opluskey.ui.write.write

// 「单击后长按」那一页的设置卡 —— 复用长按那张卡的行，参数换成这一档自己的。
//
//  为什么不新画一张：这几行的**文案、行序、互斥判据**与长按那一档是同一件事
//  （按住多久算数 / 按住期间重复几次 / 松手还做不做别的），各画一遍迟早只在一边生效。
//  差别只有两个：读写的字段（`clickThenLong*`）、摆不摆「松手才触发」那一行
//  （见 `LongPressSections.NoTiming`）。

/**
 * 「单击后长按」的三族设置 —— 挂在「按键绑定」页的这一档上。
 *
 * @param pageEnabled 整页闸门（总闸 + 链路 + 配置可写）
 * @param disabledReason 非 null ⇒ 整张卡再压一道灰（`pageEnabled` 之外的原因，如「跟随默认」）
 */
@Composable
internal fun SettingsPage.ClickThenLongCard(
    pageEnabled: Boolean,
    disabledReason: String? = null,
) {
    val cfg = configState.config.value
    val id = KeyWatchState.editing(cfg)
    val key = cfg.key(id)
    val release = key.clickThenLongRelease
    var picking by remember { mutableStateOf(false) }
    LongPressSettingsCard(
        LongPressSpec(
            repeat = key.clickThenLongRepeat,
            vibrate = key.clickThenLongRepeatVibrate,
            intervalMs = key.clickThenLongRepeatIntervalMs,
            pressMs = key.clickThenLongPressMs,
            //  这一页不摆「松手才触发」（见 `LongPressSections.NoTiming`），传 false 只是为了
            //  让置灰结论那一支算得对 —— 它关着，连发与松手两族都亮着
            fireOnRelease = false,
            releaseSupported = true,
            releaseOn = key.clickThenLongReleaseOn,
            //  行上写**动作名**（与长按那张卡同一条）：用户挑的可能是某条命令
            releaseLabel = releaseActionTitle(release.token) { appNameOf(it) },
            releaseIcon = actionIcon(release.token),
            releaseBound = key.clickThenLongReleaseOn && release.isBound,
            //  这一档本身是「无操作」时，松手额外动作那一族跟着焊死（同长按那张卡）
            releaseAvailable = key.clickThenLongActionExists,
            enabled = pageEnabled,
            delayEnabled = pageEnabled,
            sections = LongPressSections.NoTiming,
            //  多条命令 / 灵感语音不支持连发 —— 与长按那张卡同一份判据（`repeatBlockedReason`）
            repeatBlockedReason = repeatBlockedReason(key.clickThenLong),
            blockedReason =
                disabledReason
                    ?: if (!pageEnabled) I18n.get(Strings.STATUS_KEY_DISABLED, "按键绑定已关闭，按系统原生行为") else null,
            onRepeat = { on -> commit(id) { it.copy(clickThenLongRepeat = on) } },
            onVibrate = { on -> commit(id) { it.copy(clickThenLongRepeatVibrate = on) } },
            onInterval = { v -> commit(id) { it.copy(clickThenLongRepeatIntervalMs = v) } },
            onPressMs = { v -> commit(id) { it.copy(clickThenLongPressMs = v) } },
            //  这一页没有那一行（见上面的 sections），不会有人拨它
            onFireOnReleaseToggle = {},
            onReleaseToggle = { on -> commit(id) { it.copy(clickThenLongReleaseOn = on) } },
            onReleasePick = { picking = true },
        ),
    )
    //  挑额外动作：与长按那张卡**同一个抽屉**（项目里挑动作只有这一个清单）
    StepPickerSheet(
        show = picking,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手执行额外动作"),
                current = release,
                onOpenPicker = { page ->
                    picking = false
                    when (page) {
                        //  `chain = true`：挑完写的是这一档的松手格（见 `PickTarget.Release.chain`）
                        StepPickerPage.App -> openAppPickerForRelease(chain = true)

                        StepPickerPage.AppStop -> openAppPickerForRelease(stop = true, chain = true)

                        StepPickerPage.Vars -> Unit
                    }
                },
                onPick = { token -> commit(id) { it.copy(clickThenLongRelease = Action.parse(token)) } },
                onDismiss = { picking = false },
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}

/**
 * 把这一档的一次改动写回配置 —— **现读一次**再改。
 *
 *  与这一页其它卡片同一条口径：先拨开关再拖滑杆时，渲染那一帧的 `key` 会把刚写进去的值顶回去。
 */
private fun SettingsPage.commit(
    id: KeyId,
    change: (KeyConfig) -> KeyConfig,
): Boolean {
    val now: Config = configState.config.value
    val next = change(now.key(id))
    return write(
        now.copy(keys = now.keys + (id to next)),
        "click_then_long key=${id.id}",
    ) != CommandsSave.Failed
}
