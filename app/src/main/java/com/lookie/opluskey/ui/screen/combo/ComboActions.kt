package com.lookie.opluskey.ui.screen.combo

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.keyBrackets
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.write
import com.lookie.opluskey.ui.writeAndNotice

// 「组合键」那两页共用的读写 —— 列表页（加 / 删 / 摘要）与编辑页（标题上那行成员名）都走这里。

/**
 * 一组组合键最多几档。
 *
 *  取 5：列表行首用 1~5 的数字图标标「第几组」（用户口径），再多一个数字就不够用了 ——
 *  上限跟着那排数字走，界面与模型不会各说一个数。
 *
 *  为什么不是 9：一组组合要占两格动作（短按 + 长按），五组就是一整页网格的量；
 *  再多用户自己也记不住哪一组是什么，列表行与数字图标都失去意义。
 */
internal const val MAX_COMBOS = 5

/**
 * 一档组合的显示名 —— 成员键名连起来（`侧键 + 电量 +`）。
 *
 *  内置键的名字走字典（`侧键` 在英文界面下是 `Side key`），自定义键用用户起的名字：
 *  与 `GestureExec.comboTriggerLabel`（执行时提示的主语）同一套口径。
 */
internal fun SettingsPage.comboLabel(
    cfg: Config,
    combo: ComboKey,
    /**
     * 每个键名外面套一对括号（中文 `「」`、英文 `“”`，见 [keyBrackets]）。
     *
     *  **除了纯日志之外一律要它**（用户口径）：列表行、顶栏副标题、删除确认窗、
     *  「已添加 / 已删除」的回执 —— `侧键 + 电量 +` 读起来像一句话，
     *  `「侧键」+「电量 +」` 才看得出这是两颗键的名字。
     *  模板里**不要再套一层引号**（`删除 %s？`，不是 `删除「%s」？`）：套两层会把两颗键读成一个名字。
     */
    bracketed: Boolean = false,
): String {
    val names = combo.keys.mapNotNull { id -> cfg.keyIdOf(id)?.let(::keyName) }
    val (open, close) = if (bracketed) keyBrackets() else "" to ""
    // 成员全被删掉时（手改的配置）至少给一句能认出来的话，不能是空串
    return names
        .ifEmpty { listOf(combo.id) }
        .joinToString(" + ") { "$open$it$close" }
}

/** 一颗键在界面上的名字（内置键走字典，自定义键用用户起的名字）。 */
internal fun SettingsPage.keyName(id: KeyId): String = if (id.isCustom) id.label else I18n.get(Strings.KEY_SIDE, id.label)

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
        //  这一句是给调试通道与手改的配置兜底 —— 判定侧只认两颗，多的那颗永远等不到。
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
    val combo = ComboKey(id = ComboKey.idOf(seq), keys = ids)
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

/** 删掉一档组合（列表行的删除按钮 → 确认窗）。 */
internal fun SettingsPage.removeCombo(id: String) {
    val cfg = configState.config.value
    val combo = cfg.combos.firstOrNull { it.id == id } ?: return
    XLog.w("组合键：删掉 ${combo.id}（${comboLabel(cfg, combo)}）")
    //  回执与删除确认窗同一个口径：每个键名各套一对括号（见 `addCombo`）
    val label = comboLabel(cfg, combo, bracketed = true)
    writeAndNotice(
        cfg.copy(combos = cfg.combos.filterNot { it.id == id }),
        "combo_del=$id",
        slot = HINT_SLOT_COMBO,
    ) {
        I18n.format(Strings.COMBO_REMOVED, label)
    }
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
) {
    try {
        block()
    } catch (e: Throwable) {
        XLog.w("组合键：$what 失败（忽略）：${e.javaClass.simpleName}", e)
        comboWriteFailed()
    }
}

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
