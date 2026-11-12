package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig

// 按键那一档「这一行叫什么」—— 从 `ui/screen/binding/InstantSingleCard.kt` 搬出来的：
//  同一句话有两个读者（那一行本身、拨完开关那条回执），而回执那一侧在 `ui/` 根
//  （`KeyParamsWrite.setInstantSingle`）—— 放回屏里就是「共用件反向依赖具体屏」，
//  分层审计（`tools/audit-coupling.py` 的 `ui → ui.screen` 那一档）会当场拦下。

/**
 * 「忽视无操作多击手势」那一行的标题 —— 开了「单击后长按」时它管的是**多击与两段式**
 * 两类无操作手势，标题跟着说全；否则用户看到「忽视无操作多击手势」会以为按住那一下不归它管。
 *
 *  行与写回回执（`KeyParamsWrite.setInstantSingle`）**共用这一份**：两处各拼一次时，
 *  拨开关时弹出的「已关闭…」会与实际那一行的标题对不上（真机上见过）。
 */
internal fun instantSingleTitle(key: KeyConfig): String =
    if (key.clickThenLongOn) {
        I18n.get(Strings.SETTING_IGNORE_DOUBLE_CHAIN, "忽视无操作多击与单击后长按手势")
    } else {
        I18n.get(Strings.SETTING_IGNORE_DOUBLE, "忽视无操作多击手势")
    }
