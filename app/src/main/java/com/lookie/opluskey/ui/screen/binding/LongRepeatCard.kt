package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.LongPressSettingsCard
import com.lookie.opluskey.ui.LongPressSpec
import com.lookie.opluskey.ui.SettingsPage
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
 * @param blockedReason 非 null ⇒ **整张卡**置灰、不吃点击（连续触发那行与长按延迟那行），
 * @param masterEnabled 首页那个「按键绑定」主开关 —— 与 [blockedReason] 一起决定「长按延迟」那行
 */
@Composable
internal fun SettingsPage.LongRepeatCard(
    blockedReason: String?,
    masterEnabled: Boolean,
) {
    val blocked = blockedReason != null
    val cfg = configState.config.value
    // **当前在编辑的那一颗**：写死侧键的话，停在自定义键上拨开关 / 拖滑杆改的全是侧键
    //  （卡片上的值却还是侧键的，看着像「拖了没反应」）。
    val editing = KeyWatchState.editing(cfg)
    val key = cfg.key(editing)
    LongPressSettingsCard(
        LongPressSpec(
            repeat = key.longRepeat,
            vibrate = key.longRepeatVibrate,
            intervalMs = key.longRepeatIntervalMs,
            pressMs = key.longPressMs,
            enabled = !blocked,
            // 「长按延迟」跟着**整张卡**走（主开关 + 置灰原因）：这个参数对多条命令其实仍然生效
            //  （判定长按的时机与绑的是什么动作无关），但同一张卡里上面那行已经置灰、它独自亮着，
            //  会让人以为「这行还能改 ⇒ 多条命令也许还能连续触发」（用户报的）。
            delayEnabled = masterEnabled && !blocked,
            blockedReason = blockedReason,
            onRepeat = { setLongRepeat(it) },
            onVibrate = { setRepeatVibrate(it) },
            onInterval = { writeRepeatInterval(it) },
            onPressMs = { writeLongPressDelay(it) },
        ),
    )
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
