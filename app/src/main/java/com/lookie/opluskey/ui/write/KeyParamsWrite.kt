package com.lookie.opluskey.ui.write

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.model.text.instantSingleTitle
import com.lookie.opluskey.ui.state.binding.KeyWatchState

// 「**这一颗键**的参数」这一族 —— 与 `ui/write/ConfigWrite.kt`（全局开关 + 唯一的写入口 [write]）
// 分开：两者改的不是同一段配置（`keys[id]` 对 `global`），错放会让「这一项属于谁」看不出来。
//
// 长按阈值 / 触发间隔那几档是滑杆，各自在 `LongRepeatCard` 里直接调 [write]，不在这里再包一层。
//
// **回执的位一律 [HINT_SLOT_BINDING]**：这一族只在「按键设置」页被调用（那一页的三张卡），
// 回执共用一条消息（理由见 [HINT_SLOT_BINDING] 的注释）。

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
internal fun SettingsPage.setInstantSingle(on: Boolean) {
    //  回执的主语与那一行**同一个口径**（见 `instantSingleTitle`）：开了「单击后长按」时它管两档手势，
    //  两处各拼一次会让「已关闭…」与页面上那行标题对不上。
    val key = configState.config.value.key(KeyWatchState.editing(configState.config.value))
    return toggleKeyParam(
        "instant_single=${if (on) 1 else 0}",
        instantSingleTitle(key),
        on,
    ) { it.copy(instantSingle = on) }
}

/**
 * 切「逐次等满多击窗口」—— 判定时序，改的是**当前编辑的那颗键**（`KeyConfig.waitMultiWindow`）。
 *
 *  判定侧的行为见 `inject/input/GestureDetector.onKeyDownLocked`：关着时数够要的那一档就判，
 *  开着时每一下都等满窗口、按实际击数判。
 */
internal fun SettingsPage.setWaitMultiWindow(on: Boolean) {
    toggleKeyParam(
        "wait_multi_window=${if (on) 1 else 0}",
        I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW, "逐次等满多击窗口"),
        on,
    ) { it.copy(waitMultiWindow = on) }
}
