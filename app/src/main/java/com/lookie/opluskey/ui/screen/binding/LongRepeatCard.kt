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
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.core.model.withLongPress
import com.lookie.opluskey.core.rom.RomProfile
import com.lookie.opluskey.ui.BindingTarget
import com.lookie.opluskey.ui.LongPressSettingsCard
import com.lookie.opluskey.ui.LongPressSpec
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.actionIcon
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
 * @param disabledReason 非 null ⇒ **整张卡**再压一道灰（`pageEnabled` 之外的原因，如「跟随默认」）——
 *   点灰行时说的就是这一句
 */
@Composable
internal fun SettingsPage.LongPressCard(
    target: BindingTarget,
    pageEnabled: Boolean,
    disabledReason: String? = null,
) {
    val cfg = configState.config.value
    val owner = target.longPress(cfg) ?: return
    //  整张卡的置灰只有两个来源：整页闸门、调用点给的额外原因（这一档根本不归这一页管）。
    val blockedReason = longPressBlockedReason(pageEnabled, disabledReason)
    val blocked = blockedReason != null
    //  「额外动作」这一行的取值**不滤 `Action.None`**：无操作就是这一档的默认值，不是「还没配」。
    //  滤掉它会让界面多出一个「未设置」状态 —— 那与「无操作」是同一个意思，却要另配文案与图标
    //  （原先那颗还借了网格里「系统按键」的，撞了）。
    //  「跟随默认」只属于场景块的三档：历史配置里可能已经存进来过（那一版抽屉把它列在了这一行），
    //  而它在这一档没有语义 —— 行上按「无操作」念，与执行侧同一个口径（见到它什么都不做）。
    val releaseAction = owner.longRelease.takeUnless { it is Action.Follow } ?: Action.None
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
            //  回调拿到的是**目标写法**（`999@包名/类名`），不是包名 ⇒ 必须走 `appNameOf`
            //  （`appLabelOf` 只认包名，查不到时**把传进去的整串原样吐回来**，那一行就成了
            //  「松手: 999@com.foo/.Bar」）
            releaseLabel = releaseActionTitle(releaseAction.token) { target -> appNameOf(target) },
            //  配了动作，行首就换成**那个动作自己的图标**（与网格里那一格同一颗，见 `actionIcon`）：
            //  这一行答的是「松手做什么」，图标跟着答案走才读得出来
            releaseIcon = actionIcon(releaseAction.token),
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

/**
 * 长按那张卡**整卡置灰**的那一句（`null` = 能点）。
 *
 *  两个来源，前者优先：调用点给的原因（这一档根本不归这一页管，如「跟随默认」）、
 *  整页闸门（主开关 / 链路 / 只读）。
 *
 *  主开关自己关着不是「模块没生效」：`STATUS_DISABLED` 的正文是「确认作用域含 system，
 *  换包后需重启」—— 拿它来报这一档，等于把用户赶去 LSPosed 里翻一圈。
 */
private fun longPressBlockedReason(
    pageEnabled: Boolean,
    disabledReason: String?,
): String? =
    when {
        disabledReason != null -> {
            disabledReason
        }

        !pageEnabled -> {
            I18n.get(Strings.STATUS_KEY_DISABLED, "按键绑定已关闭，按系统原生行为")
        }

        else -> {
            null
        }
    }
