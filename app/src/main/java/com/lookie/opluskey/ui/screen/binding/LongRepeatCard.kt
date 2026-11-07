package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.LongPressEdit
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.core.model.withLongPress
import com.lookie.opluskey.ui.BindingTarget
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
import com.lookie.opluskey.ui.write

// 「长按」那一档的设置卡 —— **单键与组合键共用这一支**。
//
// 卡片本体（行序 / 文案 / 可见性）在 `ui/LongPressSettingsCard.kt`；这里只做两件事：
// 把 `target` 那份参数递进去、把改动落回 `target` 指的那一条配置。
// 参数读取走 `LongPressParams`（两者共用的接口），写回走 `LongPressEdit`（变更包）——
// 所以这一支里**没有「是单键还是组合」的分支**。

/**
 * 长按那一档的**唯一**一张卡。
 *
 * @param target 这一档往哪儿写（单键的一档 / 组合键的一档）—— 读参数、写改动都按它来
 * @param pageEnabled 整页闸门（总闸 + 链路就绪 + 配置可写）
 */
@Composable
internal fun SettingsPage.LongPressCard(
    target: BindingTarget,
    pageEnabled: Boolean,
) {
    val cfg = configState.config.value
    val owner = target.longPress(cfg) ?: return
    //  整张卡的置灰只有两个来源：整页闸门、这一档自己停用（`scenarioBlockedReason` 那套判据
    //  是给场景入口用的，这里只要「这份东西还算不算数」）。
    val blockedReason =
        if (!pageEnabled) {
            // 主开关自己关着，不是「模块没生效」：`STATUS_DISABLED` 的正文是「确认作用域含 system，
            //  换包后需重启」—— 拿它来报这一档，等于把用户赶去 LSPosed 里翻一圈。
            I18n.get(Strings.STATUS_KEY_DISABLED, "按键绑定已关闭，侧键交还系统")
        } else {
            null
        }
    val blocked = blockedReason != null
    val releaseAction = owner.longRelease.takeUnless { it is Action.None }
    var pickingRelease by remember { mutableStateOf(false) }
    LongPressSettingsCard(
        LongPressSpec(
            repeat = owner.longRepeat,
            vibrate = owner.longRepeatVibrate,
            intervalMs = owner.longRepeatIntervalMs,
            pressMs = owner.longPressMs,
            fireOnRelease = owner.longFireOnRelease,
            //  「松手」两档单键与组合都有（判定侧两边都会补发 `GestureTrigger.Release`）
            releaseSupported = true,
            releaseOn = owner.longReleaseOn,
            //  行上写的是**动作名**：用户挑的可能是某条命令，只写 token 等于让他自己读 `cmd:喝水`
            releaseLabel = releaseAction?.let { releaseActionTitle(it.token) { pkg -> appLabelOf(pkg) } },
            //  时序的互斥只在数据层有一份：这里把算好的结论递进去，界面不重算
            releaseBound = owner.releaseBound,
            enabled = !blocked,
            delayEnabled = !blocked,
            blockedReason = blockedReason,
            repeatBlockedReason = repeatBlockedReason(target.actionIn(cfg)),
            onRepeat = { on -> edit(target, LongPressEdit(repeat = on)) { "long_repeat=${if (on) 1 else 0}" } },
            onVibrate = { on ->
                edit(target, LongPressEdit(repeatVibrate = on)) { "long_repeat_vibrate=${if (on) 1 else 0}" }
            },
            onInterval = { v -> edit(target, LongPressEdit(repeatIntervalMs = v)) { "long_repeat_interval_ms=$v" } },
            onPressMs = { v -> edit(target, LongPressEdit(pressMs = v)) { "long_press_ms=$v" } },
            onFireOnReleaseToggle = { on -> edit(target, LongPressEdit(fireOnRelease = on)) { "long_fire_on_release=$on" } },
            onReleaseToggle = { on -> edit(target, LongPressEdit(releaseOn = on)) { "long_release_on=$on" } },
            onReleasePick = { pickingRelease = true },
        ),
    )
    //  挑额外动作：与命令页「挑一步」**同一个抽屉**（项目里挑动作只有这一个清单）。
    StepPickerSheet(
        show = pickingRelease,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手执行额外动作"),
                current = releaseAction,
                onOpenPicker = { page ->
                    pickingRelease = false
                    when (page) {
                        //  目标还是这一档：挑完回来写的就是它的 `longRelease`
                        StepPickerPage.App -> openAppPickerForRelease(combo = target.comboId)

                        StepPickerPage.AppStop -> openAppPickerForRelease(stop = true, combo = target.comboId)

                        //  变量那一格这一趟不列（见 `StepSheetFilter.excludeTiles`），走不到这里
                        StepPickerPage.Vars -> Unit
                    }
                },
                onPick = { token -> edit(target, LongPressEdit(release = Action.parse(token))) { "long_release=$token" } },
                onDismiss = { pickingRelease = false },
                //  「无操作」也要列：松手那一下本来就是可选的一步，「配了但这次不做事」是一档真取值
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}

/** 把一次长按改动写回这一档；滑杆要「写盘成没成」，所以回一个布尔。 */
private fun SettingsPage.edit(
    target: BindingTarget,
    change: LongPressEdit,
    what: () -> String,
): Boolean =
    write(target.withLongPress(configState.config.value, change), "${target.what}:${what()}") !=
        com.lookie.opluskey.ui.state.CommandsSave.Failed

/**
 * 这一档的长按动作**自己**不支持「连续触发」的原因；`null` = 支持。
 *
 *  两条判据与判定侧同一个口径（`KeyConfig.longIsSerial` / `longIsVoiceMemory`）：
 *  多条命令反复重放整条链没有意义；灵感语音那一族按一下就完事。
 *  只该管「连续触发」那一族，不连坐时序与长按延迟。
 */
private fun SettingsPage.repeatBlockedReason(current: Action): String? {
    if (isSerialCommand(configState.commands.value, current.token)) {
        return I18n.get(Strings.BINDING_SERIAL_UNSUPPORTED_REPEAT, "多条命令不支持连续触发")
    }
    val voice = (current as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true
    return if (voice) I18n.get(Strings.BINDING_VOICE_UNSUPPORTED_REPEAT, "灵感语音不支持连续触发") else null
}
