package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.state.KeyWatchState

// 「**这一颗键**的参数」这一族 —— 与 `ui/ConfigWrite.kt`（全局开关 + 唯一的写入口 [write]）
// 分开：两者改的不是同一段配置（`keys[id]` 对 `global`），错放会让「这一项属于谁」看不出来。
//
// 长按阈值 / 触发间隔那几档是滑杆，各自在 `LongRepeatCard` 里直接调 [write]，不在这里再包一层。
//
// **回执的位一律 [HINT_SLOT_BINDING]**：这一族只在「按键绑定」页被调用（`InstantSingleCard` /
//  `LongRepeatCard`），那页的所有回执共用一条消息（理由见 [HINT_SLOT_BINDING] 的注释）。

/**
 * 改**当前在编辑的那颗键**的一个开关：取配置 → 取键 → 应用 [transform] → 走 [writeAndNotice]。
 *
 *  这一族两个函数的差别只有「改哪个字段 / 日志写什么 / 回执主语是什么」—— 形状收在这里一处。
 *  回执的位固定 [HINT_SLOT_BINDING]（这一族只在「按键绑定」页被调用，那一页共用一条消息）。
 *
 * @param on 回执要说的目标状态（`switchNotice` 的第二个参数）
 */
internal fun SettingsPage.toggleKeyParam(
    what: String,
    title: String,
    on: Boolean,
    transform: (KeyConfig) -> KeyConfig,
) {
    val cfg = configState.config.value
    val id = KeyWatchState.editing(cfg)
    writeAndNotice(
        cfg.copy(keys = cfg.keys + (id to transform(cfg.key(id)))),
        "$what key=${id.id}",
        slot = HINT_SLOT_BINDING,
    ) { switchNotice(title, on) }
}

/**
 * 切「立即触发单击」（双击为无操作时不等双击窗口）—— 行点击与开关共用这一个入口。
 *
 *  这一位是**每颗键各一份**（`KeyConfig.instantSingle`）：写在当前编辑的那颗键上，
 *  语义与判定侧见 `inject/input/GestureDetector.onKeyUp`。
 */
internal fun SettingsPage.setInstantSingle(on: Boolean) =
    toggleKeyParam(
        "instant_single=${if (on) 1 else 0}",
        I18n.get(Strings.SETTING_IGNORE_DOUBLE, "忽视无操作双击手势"),
        on,
    ) { it.copy(instantSingle = on) }

/**
 * 切「长按连续触发」—— 行点击与开关共用这一个入口。
 *
 *  写的是**当前在编辑的那颗键**：这一行属于「按键设置」卡，卡里三档（长按阈值 / 连续触发 /
 *  忽视无操作双击）都是那颗键自己的参数。
 */
internal fun SettingsPage.setLongRepeat(on: Boolean) =
    toggleKeyParam(
        "long_repeat=${if (on) 1 else 0}",
        I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"),
        on,
    ) { it.copy(longRepeat = on) }
