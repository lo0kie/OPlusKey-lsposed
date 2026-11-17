package com.lookie.opluskey.ui.write

import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_OWN_SETTINGS
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.state.command.CommandsSave

// 「某一颗键的参数」这一族 —— 写回 `keys[id]`。
//
// 与 `ui/write/GlobalWrite.kt`（写 `global.*`）并列：那边是一份全局项，这里是逐键一份。
// 「改一档组合」那一支形状与此相同，住在 `ui/screen/combo/ComboActions.kt`（组合键自己那一页的写回）。
//
// **只有这一处直接拼 `keys[id]`**：别处要改一颗键的某一位，都走 [updateKey] ——
// 现读配置、值没变就不落盘、写盘说明带上键名，这三件事各写一遍的话，
// 「先拨开关再点卡片」那一下会把卡片刚写进去的值顶回去（见 `commitOwnField` 的说明）。

/**
 * 改一颗键的某个属性 —— 开关、滑杆、下拉都走它。
 *
 * @param id 哪一颗键
 * @param what 写盘说明里那一段（`double_click=1` 这种）：等号前那一截就是这条提示的语义位
 * @param notice 拨开关的回执（`switchNotice(...)`）；不给就只有写盘、没有底部提示 ——
 *   滑杆与文案窗都走这一档（滑杆松手会连发几次，弹回执等于把提示条刷成跑马灯）
 * @param slot 回执落在哪个语义位；默认就是这一页那一个（见 [HINT_SLOT_OWN_SETTINGS]）
 * @param change 把这一位换掉，其余字段不动
 * @return `true` = 已经是目标值（落盘了，或者本来就是这个值）
 */
internal fun SettingsPage.updateKey(
    id: KeyId,
    what: String,
    notice: (() -> String)? = null,
    slot: String = HINT_SLOT_OWN_SETTINGS,
    change: (KeyConfig) -> KeyConfig,
): Boolean {
    val cfg = configState.config.value
    val key = cfg.key(id)
    val next = change(key)
    //  值没变就不落盘：拖滑杆松手时常常是同一个值，写一次就是一次广播 + 一次重问注入侧
    if (next == key) return true
    val cfgNext = cfg.copy(keys = cfg.keys + (id to next))
    val log = "key:${id.label}:$what"
    if (notice == null) return write(cfgNext, log) != CommandsSave.Failed
    return writeAndNotice(cfgNext, log, slot = slot) { notice() } != CommandsSave.Failed
}
