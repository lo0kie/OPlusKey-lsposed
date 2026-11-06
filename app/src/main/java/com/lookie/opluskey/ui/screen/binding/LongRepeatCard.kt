package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.ui.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.LongPressSettingsCard
import com.lookie.opluskey.ui.LongPressSpec
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appLabelOf
import com.lookie.opluskey.ui.model.releaseActionTitle
import com.lookie.opluskey.ui.screen.command.StepPickerSheet
import com.lookie.opluskey.ui.screen.command.StepSheetFilter
import com.lookie.opluskey.ui.screen.command.StepSheetSpec
import com.lookie.opluskey.ui.setLongRepeat
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.write
import com.lookie.opluskey.ui.writeAndNotice

// 「长按」那一档的设置卡 —— 从 `BindingList.kt` 搬出来（它不属于「网格」，只属于长按这一档）。
//
// 卡片本体（行序 / 文案 / 可见性）在 `ui/LongPressSettingsCard.kt`，与「组合键」页共用一张；
// 这里只答两件事：**读哪一颗键**、**每次改动写进配置的哪一条**。

/**
 * 「长按」tab 下**唯一**的那张卡 —— 把当前正在编辑的那一颗键的长按设置递给共用的那张卡。
 *
 * @param blockedReason 非 null ⇒ **整张卡**置灰、不吃点击（时序两行、连续触发那族、长按延迟），
 *   只放**整页级**的原因（主开关关 / 链路没通 / 配置只读）
 * @param repeatBlockedReason 非 null ⇒ 只有「连续触发」那一族置灰：这一档的长按动作自己不支持连发
 *   （多条命令 / 灵感语音）。它不连坐时序与延迟 —— 那三项跟连不连发无关
 * @param masterEnabled 首页那个「按键绑定」主开关 —— 与 [blockedReason] 一起决定「长按延迟」那行
 */
@Composable
internal fun SettingsPage.LongRepeatCard(
    blockedReason: String?,
    repeatBlockedReason: String?,
    masterEnabled: Boolean,
) {
    val blocked = blockedReason != null
    val cfg = configState.config.value
    // **当前在编辑的那一颗**：写死侧键的话，停在自定义键上拨开关 / 拖滑杆改的全是侧键
    //  （卡片上的值却还是侧键的，看着像「拖了没反应」）。
    val editing = KeyWatchState.editing(cfg)
    val key = cfg.key(editing)
    //  「松手后再执行」那一档：`Action.None` 就是没配（与「这一档出现之前」同一个状态）
    val releaseAction = key.longRelease.takeUnless { it is Action.None }
    var pickingRelease by remember { mutableStateOf(false) }
    LongPressSettingsCard(
        LongPressSpec(
            repeat = key.longRepeat,
            vibrate = key.longRepeatVibrate,
            intervalMs = key.longRepeatIntervalMs,
            pressMs = key.longPressMs,
            fireOnRelease = key.longFireOnRelease,
            releaseSupported = true,
            releaseOn = key.longReleaseOn,
            //  行上写的是**动作名**（与命令页那几步同一套念法）：用户挑的可能是某条命令，
            //  只写 token 等于让他自己读 `cmd:喝水`。
            releaseLabel = releaseAction?.let { releaseActionTitle(it.token) { pkg -> appLabelOf(pkg) } },
            //  三条时序的互斥只在数据层有一份：这里把配置算好的结论递进去，界面不重算
            releaseBound = key.releaseBound,
            enabled = !blocked,
            // 「长按延迟」跟着**整张卡**走（主开关 + 置灰原因）：这个参数对多条命令其实仍然生效
            //  （判定长按的时机与绑的是什么动作无关），但同一张卡里上面那行已经置灰、它独自亮着，
            //  会让人以为「这行还能改 ⇒ 多条命令也许还能连续触发」（用户报的）。
            delayEnabled = masterEnabled && !blocked,
            blockedReason = blockedReason,
            repeatBlockedReason = repeatBlockedReason,
            onRepeat = { setLongRepeat(it) },
            onVibrate = { setRepeatVibrate(it) },
            onInterval = { writeRepeatInterval(it) },
            onPressMs = { writeLongPressDelay(it) },
            onFireOnReleaseToggle = { setLongFireOnRelease(it) },
            onReleaseToggle = { setLongReleaseOn(it) },
            onReleasePick = { pickingRelease = true },
        ),
    )
    //  挑松手动作：与命令页「挑一步」**同一个抽屉**（项目里挑动作只有这一个清单），
    //  只是挑完不落成一步，而是写进这颗键的 `longRelease`。
    StepPickerSheet(
        show = pickingRelease,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手后再执行"),
                current = releaseAction,
                onOpenPicker = { pickingRelease = false },
                onPick = { token -> writeLongRelease(Action.parse(token)) },
                onDismiss = { pickingRelease = false },
                filter = StepSheetFilter(excludeTiles = true),
            ),
    )
}

/**
 * 拨「松手才触发」—— 只写这一个时序位。
 *
 *  它**不清** `longReleaseOn` / `longRelease`：「松手后再执行」那一档这一刻让位（数据侧的
 *  `releaseBound` 已经把「松手才触发」算进去了），关掉这一位之后用户之前配的松手动作原样回来 ——
 *  切换时序不该顺手把他挑过的动作扔掉。
 */
private fun SettingsPage.setLongFireOnRelease(on: Boolean) {
    val now = configState.config.value
    val id = KeyWatchState.editing(now)
    writeAndNotice(
        now.copy(keys = now.keys + (id to now.key(id).copy(longFireOnRelease = on))),
        "long_fire_on_release=${if (on) 1 else 0} key=${id.id}",
        slot = HINT_SLOT_BINDING,
    ) {
        switchNotice(I18n.get(Strings.BINDING_LONG_PRESS_FIRE_ON_RELEASE_TITLE, "松手才触发"), on)
    }
}

/**
 * 拨「松手后再执行」—— 只写开关位。
 *
 *  动作那一格单独挑（[writeLongRelease]）：开关开着、动作还没挑是合法状态（松手什么都不做）。
 *  合成一位的话，用户在开关上点一下就会立刻执行一个他没挑过的动作。
 */
private fun SettingsPage.setLongReleaseOn(on: Boolean) {
    val now = configState.config.value
    val id = KeyWatchState.editing(now)
    writeAndNotice(
        now.copy(keys = now.keys + (id to now.key(id).copy(longReleaseOn = on))),
        "long_release_on=${if (on) 1 else 0} key=${id.id}",
        slot = HINT_SLOT_BINDING,
    ) {
        switchNotice(I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE, "松手后再执行"), on)
    }
}

/** 「松手后再执行」写盘 —— 写当前在编辑那颗键的 `longRelease`。 */
private fun SettingsPage.writeLongRelease(action: Action): Boolean {
    val now = configState.config.value
    val id = KeyWatchState.editing(now)
    return write(
        now.copy(keys = now.keys + (id to now.key(id).copy(longRelease = action))),
        "long_release=${action.token} key=${id.id}",
    ) != CommandsSave.Failed
}

/** 「触发间隔」写盘 —— 长按连续触发隔多久重放一次。 */
private fun SettingsPage.writeRepeatInterval(v: Int): Boolean {
    val now = configState.config.value
    val id = KeyWatchState.editing(now)
    return write(
        now.copy(keys = now.keys + (id to now.key(id).copy(longRepeatIntervalMs = v))),
        "long_repeat_interval_ms=$v key=${id.id}",
    ) != CommandsSave.Failed
}

/** 「长按延迟」写盘 —— 多久算长按。 */
private fun SettingsPage.writeLongPressDelay(v: Int): Boolean {
    val now = configState.config.value
    val id = KeyWatchState.editing(now)
    return write(
        now.copy(keys = now.keys + (id to now.key(id).copy(longPressMs = v))),
        "long_press_ms=$v key=${id.id}",
    ) != CommandsSave.Failed
}

/**
 * 切「连续触发时振动」—— 与 [setLongRepeat] 同一颗键。
 *
 *  它只在 `KeyConfig.longRepeat` 为真时有意义（那一行也只在那时出现）；关掉「长按连续触发」不清
 *  这一位：再打开时用户之前选的那一档应当还在。
 *  执行侧读它见 `host/GestureReceiver.runAction`。
 */
private fun SettingsPage.setRepeatVibrate(on: Boolean) {
    val cfg = configState.config.value
    val id = KeyWatchState.editing(cfg)
    writeAndNotice(
        cfg.copy(keys = cfg.keys + (id to cfg.key(id).copy(longRepeatVibrate = on))),
        "long_repeat_vibrate=${if (on) 1 else 0} key=${id.id}",
        //  整页一个位（[HINT_SLOT_BINDING]）：这一页的开关不各占一格，后一句改前一句的字
        slot = HINT_SLOT_BINDING,
    ) { switchNotice(I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"), on) }
}
