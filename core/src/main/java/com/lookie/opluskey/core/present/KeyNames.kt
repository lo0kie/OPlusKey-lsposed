package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId

// 键名在界面上怎么写的**唯一出口**。
//
//  原先有三份「同一件事」：`ui/model/ComboNames.kt` 的 `keyName` / `comboLabel`、
//  `host/GestureExec.kt` 里抄的一份 `keyName`、以及各处调用点自己决定的「套不套方框」。
//  三份的形态还不一样（有的带空格、有的不带），于是同一个 `[侧键]` 在顶栏、回执、执行提示里
//  长出三种长相。
//
//  收在 `core` 而不是 `ui`：这条边界由 `tools/audit-coupling.py` 管着 —— `host` 不许认识 `ui`，
//  而执行时那条提示（`GestureExec`）也念键名。要一份实现给两侧共用，只能放在两边都许引的
//  `core` 里（与 `ActionLabels` / `CommandTitle` 同一层）。

/**
 * 一档组合里两颗键之间的分隔符 —— 用「×」不用「+」。
 *
 *  键名本身就可能带加号（用户把一颗键叫「电量 +」），用「+」拼出来
 *  `[侧键]+[电量 +]` 分不清哪个加号是分隔符（用户口径「中间的 + 改成 x」）。
 */
const val COMBO_PAIR_SEPARATOR = "×"

/** 按顺序触发那一档里两颗键之间的分隔符 —— 用「→」表先后（与「×」的「一起按下」分开）。 */
const val COMBO_ORDER_SEPARATOR = "→"

/**
 * 组合键里每颗键名外面那一对框 —— **紧贴名字、不留空格**（`[侧键]`，不是 `[ 侧键 ]`）。
 *
 *  框的用处只有一个：把「两颗键的名字」和「一句话」分开（`[侧键]×[电量 +]` 一眼看出是两颗键）。
 *  所以只有**组合**才套它；单键直接写名字，见 [keyName]。
 */
private const val COMBO_KEY_OPEN = "["
private const val COMBO_KEY_CLOSE = "]"

/**
 * 一颗键在界面上的名字 —— **直接写名字，不加方括号**（用户口径：只有一个键名时不加记号）。
 *
 *  内置键的名字走字典（`侧键` 在英文界面下是 `Side key`），自定义键用用户起的名字。
 *
 *  这一条也是**执行时提示**（`GestureExec`）与「按键功能」页清单的唯一来源 ——
 *  两处原先各有一份实现，改一处就会有一处还是旧叫法。
 */
fun keyName(id: KeyId): String = if (id.isCustom) id.label else I18n.get(Strings.KEY_SIDE, id.label)

/**
 * 一档组合键的**成员键名**（`["侧键", "电量 +"]`）—— 需要**逐个各归各位**的地方用它。
 *
 *  顶栏副标题要把每颗键单独摆进一行（装不下换行），拼成一整串文字就做不到；
 *  而「成员有哪些、名字怎么取」只该有一份实现 —— [comboLabel] 也走它，于是同一个组合在顶栏、
 *  回执、执行提示里不会长出不同叫法。
 *
 *  成员全被删掉时（手改的配置）退回 id：至少有一句能认出来的话，不能是空表。
 */
fun comboKeyNames(
    cfg: Config,
    combo: ComboKey,
): List<String> = combo.keys.mapNotNull { id -> cfg.keyIdOf(id)?.let(::keyName) }.ifEmpty { listOf(combo.id) }

/**
 * 这一档的**顺序**写成一句副标题 —— 只有「不分顺序」那一档才有（`null` = 不写）。
 *
 *  有序档不用副标题：它的先后由**标题**用「→」直接表达（见 `ComboTitle`）；
 *  副标题只留给「不分顺序」——那一档标题上读不出「顺序不限」，得单独说一句。
 *
 *  · 等待档：整组有顺序可言（≥ 2 颗）→ 不分顺序时说一句；
 *  · 按住档：看**成员键**（首键之外）—— 只有一个成员时没有先后之分，不写。
 *
 *  与 [comboLabel] 分开：那是「一句话」的完整叫法（含「按住 …」句式），这里只标顺序状态。
 */
fun comboOrderSummary(combo: ComboKey): String? {
    //  有没有「顺序」这件事：等待档看整组，按住档看成员键（首键之外）
    val members = if (combo.isHold) combo.keys.drop(1) else combo.keys
    if (members.size < 2) return null
    //  有序档的先后由标题的「→」表达，不再重复写副标题
    if (combo.ordered) return null
    return I18n.get(Strings.COMBO_ORDER_ANY, "不分顺序")
}

/**
 * 一档组合键的显示名（**一句话**，键名带一对括号）。
 *
 *  · **等待组合**：成员连起来 —— `[侧键]×[电量 +]`（按顺序时 `[侧键]→[电量 +]`）；
 *  · **按住组合**：写成一句 **`按住 [侧键] 后再按 [电量 +]`** —— 那档的判据就是「谁先按住」，
 *    顺序是它的一半身份（`ComboKey.mode`），只念成员名等于把两档写成同一个名字。
 *
 *  给弹窗 / 回执 / 停用提示那些「一句话」场合用。**列表行用 [comboTitleText]**：那一行是标题，
 *  不带括号（见那边的说明）。
 *
 *  **只有一颗时不套框**（成员全被删掉、只剩 id 兜底那种畸形配置，以及用户口径那条规则）：
 *  一个名字旁边没有第二个名字需要分开，框只剩噪声。
 */
fun comboLabel(
    cfg: Config,
    combo: ComboKey,
): String = joinComboNames(comboKeyNames(cfg, combo), combo, chip = true)

/**
 * 组合列表行那一行标题 —— **纯文字**：不带括号。
 *
 *  等待档 `侧键 × 音量 +`、按住档 `按住 侧键 后再按 音量 +`；按顺序那一档分隔符换成「→」。
 *
 *  与 [comboLabel] 只差一层装饰：那句是「一句话」的完整叫法，键名套一对括号（`[侧键]`）——
 *  列表行里这是**标题**而不是句子，括号会变成噪声。句式、单颗不展开这些判据与它同一条
 *  （见 [joinComboNames]）。
 */
fun comboTitleText(
    cfg: Config,
    combo: ComboKey,
): String = joinComboNames(comboKeyNames(cfg, combo), combo, chip = false)

/**
 * 一串键名按这一档的句式拼成一句话 —— [comboLabel] 与 [comboTitleText] 的公共部分。
 *
 *  ## 按住那一档：首键在前、后续成员跟一句
 *
 *  首键就是 `keys` 第一位（迁移之后老配置也已经是这个形状），所以这里不再有「要不要倒过来」。
 *  不写成 `I18n.format(…)`：那条按顺序填占位，模板里只有两个 `%s`，第三颗会被静默丢掉；
 *  这里按段替换，后续成员有几个就连几个。成员不足两颗（配置被手改坏 / 迁移中途）时退回普通念法，
 *  否则会念成「按住 侧键 后再按 」。
 *
 *  ## 分隔符跟着 [ComboKey.ordered] 走
 *
 *  不分顺序用「×」（一起按下）、按顺序用「→」（先后）—— 两档的读法本来就不同。
 *
 * @param chip 键名要不要套一对括号（`[侧键]`）—— 一句话场合套，列表行标题不套
 */
private fun joinComboNames(
    names: List<String>,
    combo: ComboKey,
    chip: Boolean,
): String {
    val sep = if (combo.ordered) COMBO_ORDER_SEPARATOR else COMBO_PAIR_SEPARATOR

    fun name(it: String): String = if (chip) chipOrBare(it) else it
    if (combo.isHold && names.size >= 2) {
        return I18n
            .get(Strings.COMBO_MODE_HOLD, "按住 %1\$s 后再按 %2\$s")
            .replace("%1\$s", name(names.first()))
            .replace("%2\$s", names.drop(1).joinToString(" $sep ") { name(it) })
    }
    //  **单颗不展开、也不套括号**：一个名字旁边没有第二个名字要分开
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    return names.joinToString(" $sep ") { name(it) }
}

/** 句子里加一对 `[` `]` 的键名 —— 两颗以上时才加（与 [comboLabelOf] 同一条「单颗不加」的规则）。 */
private fun chipOrBare(name: String): String = "$COMBO_KEY_OPEN$name$COMBO_KEY_CLOSE"

/**
 * 一串键名压成一句话 —— 单颗直接写名字，两颗以上各套一对 `[` `]`（`[侧键]×[电量 +]`）。
 *
 *  与 [comboKeyNames] 分开一支：需要**逐个各归各位**的调用点（顶栏副标题、列表行）拿名单，
 *  只需要一句话的调用点（回执 / 提示 / 弹窗标题）拿这个 —— 两边共用同一份「单颗不加」的规则。
 */
fun comboLabelOf(names: List<String>): String = comboLabelOf(names, COMBO_PAIR_SEPARATOR)

/** 同上，但用 [sep] 当两颗键之间的分隔符（按顺序触发那档传「→」）。 */
fun comboLabelOf(
    names: List<String>,
    sep: String,
): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    return names.joinToString(" $sep ") { "$COMBO_KEY_OPEN$it$COMBO_KEY_CLOSE" }
}
