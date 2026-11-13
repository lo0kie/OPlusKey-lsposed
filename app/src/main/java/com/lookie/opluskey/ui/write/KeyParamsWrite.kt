package com.lookie.opluskey.ui.write

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.feedback.writeAndNotice

// 「**这一颗键**的参数」这一族 —— 与 `ui/write/ConfigWrite.kt`（全局开关 + 唯一的写入口 [write]）
// 分开：两者改的不是同一段配置（`keys[id]` 对 `global`），错放会让「这一项属于谁」看不出来。
//
// 长按阈值 / 触发间隔那几档是滑杆，各自在 `LongRepeatCard` 里直接调 [write]，不在这里再包一层。
//
// **回执的位一律 [HINT_SLOT_BINDING]**：这一族只在「按键设置」页被调用（那一页的三张卡），
// 回执共用一条消息（理由见 [HINT_SLOT_BINDING] 的注释）。

/**
 * 改**这一颗键**的一个开关：取配置 → 取键 → 应用 [transform] → 走 [writeAndNotice]。
 *
 *  这一族两个函数的差别只有「改哪个字段 / 日志写什么 / 回执主语是什么」—— 形状收在这里一处。
 *  回执的位固定 [HINT_SLOT_BINDING]（这一族只在「按键绑定」页被调用，那一页共用一条消息）。
 *
 *  **改哪一颗由调用方给**（`keyId`）：原先在这里现取 `KeyWatchState.editing`，那是「按键绑定页
 *  当前选中的那颗」—— 这一族现在也从独立设置页被调用，那一份状态不再代表那一页在改谁。
 *
 * @param on 回执要说的目标状态（`switchNotice` 的第二个参数）
 */
internal fun SettingsPage.toggleKeyParam(
    keyId: KeyId,
    what: String,
    title: String,
    on: Boolean,
    transform: (KeyConfig) -> KeyConfig,
) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(keys = cfg.keys + (keyId to transform(cfg.key(keyId)))),
        "$what key=${keyId.id}",
        slot = HINT_SLOT_BINDING,
    ) { switchNotice(title, on) }
}

/**
 * 写「固定窗口起点」那一颗 —— 落的是 [KeyConfig.waitMultiWindow]（**反面**）。
 *
 *  判定侧的行为见 `inject/input/GestureDetector.onKeyDownLocked`：`waitMultiWindow = false`
 *  时从首次按下只等一个窗口、窗口内数到的多击算一次触发；`true` 时每一击之后都重新等一个
 *  完整窗口、按实际击数判。
 *
 *  **取反只在界面那一行做**（`waitMultiWindowRow` 的 `checked = !key.waitMultiWindow`）：
 *  这里收到的已经是存储值，回执却要说界面上那颗的状态 —— 所以传给 `toggleKeyParam` 的是
 *  `!waitMultiWindow`，别照抄。
 */
internal fun SettingsPage.setWaitMultiWindow(
    keyId: KeyId,
    waitMultiWindow: Boolean,
) {
    toggleKeyParam(
        keyId,
        "wait_multi_window=${if (waitMultiWindow) 1 else 0}",
        I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW, "固定窗口起点"),
        !waitMultiWindow,
    ) { it.copy(waitMultiWindow = waitMultiWindow) }
}
