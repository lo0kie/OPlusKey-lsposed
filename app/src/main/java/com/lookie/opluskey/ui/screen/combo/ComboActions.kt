package com.lookie.opluskey.ui.screen.combo

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.comboLabel
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeysAsk
import com.lookie.opluskey.ui.write

// 「组合键」那两页共用的读写 —— 列表页（加 / 删 / 摘要）与编辑页（标题上那行成员名）都走这里。

/**
 * 一组组合键最多几档 —— 九档（用户口径）。
 *
 *  列表行首用数字图标标「第几组」（1~9）：上限跟着那排数字走，界面与模型不会各说一个数
 *  （`PageIcons.menuKeyNumber` 只到 9 —— 图标库的圈数字到这里为止，不为第 10 档手绘）。
 *
 *  为什么不再往下放：一组组合要占两格动作（短按 + 长按），九组已是一整页网格的量；
 *  再多用户自己也记不住哪一组是什么，列表行与数字图标都失去意义。
 */
internal const val MAX_COMBOS = 9

//  `COMBO_PAIR_SEPARATOR` / `comboLabel` / `keyName` 已挪到 `ui/model/ComboNames.kt`：
//  `ui/BindingTarget.kt` 也要用（组合被停用时那句提示里的名字），而 `ui/` 不许反过来认识
//  `ui/screen/*`（那条边由 `tools/audit-coupling.py` 核）。这两个函数本来就不读任何界面状态。

/**
 * 加一档组合（选键弹窗点了「添加」）。
 *
 *  **只有一组键只留一档**：同一个成员集合加两次时，判定那边（`Config.comboFor`）取第一档，
 *  第二档永远不会生效 —— 与其让用户配出一档死配置，不如当场说一句。
 *
 * @return `true` = 已经落盘
 */
internal fun SettingsPage.addCombo(keys: List<KeyId>): Boolean {
    val cfg = configState.config.value
    val ids = keys.map { it.id }.distinct()
    if (ids.size !in ComboKey.MIN_KEYS..ComboKey.MAX_KEYS) {
        //  界面那边颗数不对时「添加」是灰的（点一下会说原因），走不到这里；
        //  这一句是给**新增入口**兜底：判定侧认得更多成员，界面这一侧仍然只造到
        //  `MAX_KEYS`（三颗）—— 别让某个新入口绕过这条线。
        XLog.w("组合键：成员 ${ids.size} 颗不在 ${ComboKey.MIN_KEYS}..${ComboKey.MAX_KEYS} 之内，不添加（${ids.joinToString("+")}）")
        hint(I18n.format(Strings.COMBO_MAX_KEYS, ComboKey.MAX_KEYS), slot = HINT_SLOT_COMBO)
        return false
    }
    if (cfg.combos.size >= MAX_COMBOS) {
        hint(I18n.format(Strings.COMBO_LIMIT, MAX_COMBOS), slot = HINT_SLOT_COMBO)
        return false
    }
    if (cfg.combos.any { it.keys.sorted() == ids.sorted() }) {
        //  同一个成员集合：成员是按下的那一组键，顺序不是身份（见 `ComboKey` 的类注释）。
        //  界面那边「已存在」时按钮就是灰的，走不到这里；这一句是给手改的配置与将来的入口兜底。
        hint(I18n.get(Strings.COMBO_DUPLICATE, "这组按键已经添加过了"), slot = HINT_SLOT_COMBO)
        return false
    }
    //  序号只增不减：删掉中间一档之后再加，不会复用那个 id（复用会让「在编哪一档」指到别人身上）
    val seq =
        (cfg.combos.mapNotNull { it.id.removePrefix(ComboKey.ID_PREFIX).toIntOrNull() }.maxOrNull() ?: 0) + 1
    val combo = ComboKey(id = ComboKey.idOf(seq), keys = ids, enabled = comboEnableByDefault(cfg, ids))
    val label = comboLabel(cfg, combo)
    val result = write(cfg.copy(combos = cfg.combos + combo), "combo_add=${combo.id}")
    // 与 [writeAndNotice] 同一套口径：只在真的落盘并广播出去时报一句；失败时 [write] 自己已经报过。
    //  回执里每个键名各套一对括号（`已添加「侧键」+「电量 +」`）—— 整串再包一层会把两颗键
    //  读成一个名字。
    if (result == CommandsSave.Ok) {
        hint(I18n.format(Strings.COMBO_ADDED, comboLabel(cfg, combo, bracketed = true)), slot = HINT_SLOT_COMBO)
    }
    XLog.w("组合键：加了一档 ${combo.id}=${combo.keys.joinToString("+")}（$label）")
    return result == CommandsSave.Ok
}

/**
 * 删掉一档组合按键。
 *
 * @return `true` = 已经落盘（或本来就不在了）；`false` = 写盘被拒 / 失败。调用方据此决定
 *   要不要收掉编辑目标与退页 —— 写失败时那一档还在配置里，页面不能先走。
 */
internal fun SettingsPage.removeCombo(id: String): Boolean {
    val cfg = configState.config.value
    val combo = cfg.combos.firstOrNull { it.id == id } ?: return true
    XLog.w("组合键：删掉 ${combo.id}（${comboLabel(cfg, combo)}）")
    //  回执与删除确认窗同一个口径：每个键名各套一对括号（见 `addCombo`）
    val label = comboLabel(cfg, combo, bracketed = true)
    //  判「没成」只看 [CommandsSave.Failed]：`BroadcastFailed` 是「盘上删了、只是没通知到注入侧」，
    //  那时这一档确实已经不在了，调用方不能按「什么都没变」处理（`write` 的契约就是这么写的）。
    if (write(cfg.copy(combos = cfg.combos.filterNot { it.id == id }), "combo_del=$id") !=
        CommandsSave.Failed
    ) {
        hint(I18n.format(Strings.COMBO_REMOVED, label), slot = HINT_SLOT_COMBO)
        return true
    }
    return false
}

/**
 * 新加的这一档**默认跟成员键的实际状态**：成员里还有停用的键，它从一开始就是停用。
 *
 *  否则会造出「配置说启用、列表说停用」的一档 —— 等那颗键被开回来时这一组还会自己活过来。
 *  选键弹窗里那颗键的启停态不明显（它只列名字），所以这里不弹窗问，直接按事实落位：
 *  想开这一组，用户会去那颗键上开，或者在这一组的 ⋮ 菜单里点启用（那时才问「要不要把键也开」）。
 */
private fun comboEnableByDefault(
    cfg: Config,
    ids: List<String>,
): Boolean = ids.all { cfg.keyIdOf(it)?.let { kid -> cfg.key(kid).enabled } == true }

/**
 * 停用 / 启用一档组合 —— 与单键那一支（`setKeyEnabledFromMenu`）同一套行为。
 *
 *  写的是这一档自己的 [ComboKey.enabled]，与模块总闸（`GlobalConfig.enabled`）是两层：
 *  总闸关 ⇒ 一颗键都不接管；这一档关 ⇒ 只有这一组不成立（两颗键各按自己的绑定照常执行 ——
 *  判定侧读 `ComboKey.isUsable`，`Config.comboFor` 与 `ConfigChannel.joinsCombo` 两处都不认它，
 *  于是「参与组合的键要等一个窗口」也随之消失）。
 *
 *  **没变就不写**：菜单里点到当前那一档时，不该落一次盘、更不该报「已停用」。
 */
internal fun SettingsPage.setComboEnabled(
    id: String,
    on: Boolean,
) {
    val cfg = configState.config.value
    val combo = cfg.combos.firstOrNull { it.id == id } ?: return
    if (combo.enabled == on) return
    //  **回执与「已添加 / 已删除」同一个口径**：成员名各带自己的括号，整串不再套引号
    val label = comboLabel(cfg, combo, bracketed = true)
    //  **启用之前先看成员键**（与按键列表那颗开关、绑定页 ⋯ 菜单同一套口径）：
    //  直接写 `enabled=true` 会留下一份「配置说启用、列表说停用」的状态 ——
    //  等那颗键被开回来时这一组还会自己活过来，用户看到的是「我没动它，它自己开了」。
    if (on && !comboEnableAllowed(cfg, combo, label)) return
    XLog.w("组合键：${if (on) "启用" else "停用"} $id（$label）")
    if (updateCombo(id, "enabled=${if (on) 1 else 0}") { it.copy(enabled = on) }) {
        hint(
            I18n.format(if (on) Strings.COMBO_ENABLED else Strings.COMBO_DISABLED, label),
            slot = HINT_SLOT_COMBO,
        )
    }
}

/**
 * 启用一档组合之前的闸 —— `true` = 放行。
 *
 *  两档拦法：
 *  1) **成员键已经不在配置里**（手改 / 导入带来的）：这一组本来就废了，直接拒；
 *     这里**不能**走「一起启用」那一问 —— 那颗键根本不存在，答应了也只会留下一个更假的状态。
 *  2) **成员键里有停用的**：问一句「要不要把键也开」（`KeysAsk`，与按键列表那颗 FAB 共用一个窗）。
 *     答「否」时这一组保持停用（这一档凑不齐，`Config.liveCombos` 也会把它排除在判定与等待之外）。
 */
private fun SettingsPage.comboEnableAllowed(
    cfg: Config,
    combo: ComboKey,
    label: String,
): Boolean {
    if (combo.keys.any { cfg.keyIdOf(it) == null }) {
        XLog.w("组合键：${combo.id} 的成员键已不在配置里，不启用（$label）")
        hint(
            I18n.get(Strings.KEYS_COMBO_NEEDS_KEYS, "成员键没启用，这组也停用"),
            slot = HINT_SLOT_COMBO,
        )
        return false
    }
    val dead =
        combo.keys
            .mapNotNull { cfg.keyIdOf(it) }
            .filterNot { cfg.key(it).enabled }
            .map { it.id }
            .toSet()
    if (dead.isEmpty()) return true
    KeysAsk.ask(
        KeysAsk.Kind.ENABLE,
        keys = emptySet(),
        combos = setOf(combo.id),
        extraKeys = dead,
        slot = HINT_SLOT_COMBO,
    )
    return false
}

/** 组合里有没有已经删掉的键（列表那一行据此标一句）。 */
internal fun SettingsPage.comboHasMissingKey(
    cfg: Config,
    combo: ComboKey,
): Boolean = combo.keys.any { cfg.keyIdOf(it) == null }

/** 写盘结果的回执位 —— 这两页一次只改一件事，共用一个位就够（见 [HINT_SLOT_COMBO]）。 */
internal fun SettingsPage.comboWriteFailed() {
    hint(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"), slot = HINT_SLOT_COMBO)
}

/** 写盘发生异常时也报一句 —— 与 `pick` 同一道兜底（点了必须有说法）。 */
internal fun SettingsPage.comboWriteGuard(
    what: String,
    block: () -> Unit,
) = XLog.guard(
    what = "组合键：$what",
    note = "这一次没写下去",
    onError = { comboWriteFailed() },
    block = block,
)

/**
 * 改一档组合的某个属性 —— 编辑页那几行（连续触发 / 振动 / 间隔 / 长按延迟）都走它。
 *
 *  与单键那几张卡同一个口径：值没变就不落盘（拖滑杆松手时常常是同一个值），
 *  写盘说明里带上档位 id 与改的是谁 —— 日志里能对上是哪一组。
 *
 * @param what 写盘说明里那一段（`long_repeat=1` 这种）
 * @return `true` = 已经是目标值（落盘了，或者本来就是这个值）
 */
internal fun SettingsPage.updateCombo(
    id: String,
    what: String,
    change: (ComboKey) -> ComboKey,
): Boolean {
    val cfg = configState.config.value
    val combo = cfg.combos.firstOrNull { it.id == id } ?: return false
    val next = change(combo)
    if (next == combo) return true
    return write(cfg.copy(combos = cfg.combos.map { if (it.id == id) next else it }), "combo:$id:$what") !=
        CommandsSave.Failed
}

/**
 * 写「按键之间的等待」（毫秒）—— 两颗键按下最多隔多久才算一起按下。
 *
 *  这一位**改的是注入侧的手感**，所以走通用写入通道（落盘 + 广播 + 重问注入侧），
 *  与别的全局项同一条路。返回「写盘成没成」：滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 */
internal fun SettingsPage.setComboWindow(ms: Int): Boolean {
    val cfg = configState.config.value
    val value = ms.coerceIn(GlobalConfig.COMBO_WINDOW_MIN_MS, GlobalConfig.COMBO_WINDOW_MAX_MS)
    if (cfg.global.comboWindowMs == value) return true
    return write(cfg.copy(global = cfg.global.copy(comboWindowMs = value)), "combo_window_ms=$value") != CommandsSave.Failed
}

/**
 * 写**这一组自己**的按键间隔（毫秒）。
 *
 *  与 [setComboWindow] 不是同一个落点：那个写公共那一档（没单独设过的组都用它），
 *  这个写这一组自己的值 —— 判定侧两处都读（`Config.comboWindowFor`）。
 *
 * ## 「回到跟随公共」不是这一条路
 *
 *  那个状态的入口只有一个：**关掉这一组的「使用独立设置」**
 *  （`Config.comboWindowFor` 的第一支：关着就整份看公共）。开着独立设置时不存在「跟随」——
 *  打开那一刻已经把公共值播进这一组了（见 `KeySettingsScreen.withSeededComboFields`），
 *  之后改公共它一项都不动；关掉再打开用的还是这一组上次那套。
 *
 *  所以下面 `ms <= 0` 那一支**界面上到不了**：滑杆的下限是
 *  [GlobalConfig.COMBO_WINDOW_MIN_MS]（20ms）。它留着是因为**老配置**里可能已经是
 *  [ComboKey.WINDOW_FOLLOW_GLOBAL]（这一位出现之前写下的 0），读取侧的 `?: global` 靠它兜底；
 *  手改配置文件写 0 也照样表达「跟随」。
 */
internal fun SettingsPage.setComboOwnWindow(
    id: String,
    ms: Int,
): Boolean =
    updateCombo(id, "window_ms=$ms") { combo ->
        val value =
            if (ms <= 0) {
                ComboKey.WINDOW_FOLLOW_GLOBAL
            } else {
                ms.coerceIn(GlobalConfig.COMBO_WINDOW_MIN_MS, GlobalConfig.COMBO_WINDOW_MAX_MS)
            }
        combo.copy(windowMs = value)
    }
