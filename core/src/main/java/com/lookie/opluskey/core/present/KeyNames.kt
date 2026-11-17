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
 * 等待组合里两颗键之间的分隔符 —— 「和」（英文界面是 `and`，按当前语言取）。
 *
 *  键名本身就可能带加号（用户把一颗键叫「电量 +」），所以分隔符不能用「+」拼。
 *  「和」读起来就是「一起按下」，与顺序那一档的「先 A 后 B 再 C」（见 [comboOrderSummary]）
 *  分开 —— 后者只落在副标题里。
 */
val comboMemberSeparator: String get() = I18n.get(Strings.COMBO_MEMBER_SEPARATOR)

/** 按顺序那一档第一颗与第二颗之间的连接词 —— 「后」（英文界面是 `then`，按当前语言取）。 */
val comboOrderedBetween: String get() = I18n.get(Strings.COMBO_ORDERED_BETWEEN)

/** 第二颗之后每一颗前面的连接词 —— 「再」（英文界面同样 `then`）。 */
val comboOrderedAgain: String get() = I18n.get(Strings.COMBO_ORDERED_AGAIN)

/**
 * 组合键里每颗键名外面那一对框 —— **紧贴名字、不留空格**（`[侧键]`，不是 `[ 侧键 ]`）。
 *
 *  框的用处只有一个：把「两颗键的名字」和「一句话」分开（`[侧键]和[电量 +]` 一眼看出是两颗键）。
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
fun keyName(id: KeyId): String = if (id.isCustom) id.label else I18n.get(Strings.KEY_SIDE)

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
 * 这一档的**顺序**写成一句副标题 —— 该有顺序时总有（`null` = 不写）。
 *
 *  · 等待档按顺序 → 「从左往右依次按下」：**不再把先后念一遍**，只指给标题看 —— 那几个名字
 *    从左到右就是按下的次序（用户口径）；
 *  · 按住档按顺序 → 「按住首键后从左往右依次按下」：先按住首键是这一档的前提，
 *    剩下那几颗照标题从左到右念一遍；
 *  · 不分前后顺序（两档都一样）→ 「不分前后顺序」：标题上没有先后可读，得单独说一句。
 *
 *  **参与先后的只有「一起按」的那几颗**：等待档是整组，按住档是首键之外 —— 只剩一颗时
 *  没有先后可言，不写。
 *
 *  与 [comboLabel] 分开：那是「一句话」的完整叫法（含「按住 …」句式），这里只标顺序。
 */
fun comboOrderSummary(
    cfg: Config,
    combo: ComboKey,
): String? {
    val names = comboKeyNames(cfg, combo)
    //  按住档的首键（`按住` 那颗）不参与先后；等待档整组参与
    val ordered = if (combo.isHold) names.drop(1) else names
    if (ordered.size < 2) return null
    if (!combo.ordered) return I18n.get(Strings.COMBO_ORDER_ANY)
    if (combo.isHold) return I18n.get(Strings.COMBO_ORDER_LTR_HOLD)
    return I18n.get(Strings.COMBO_ORDER_LTR)
}

/**
 * 一档组合键的显示名（**一句话**，键名带一对括号）。
 *
 *  · **等待组合**：成员连起来 —— `[侧键]和[电量 +]`（按顺序时 `[侧键] 后 [电量 +]`）；
 *  · **按住组合**：写成一句 **`按住 [侧键] 按 [电量 +]`** —— 那档的判据就是「谁先按住」，
 *    顺序是它的一半身份（`ComboKey.mode`），只念成员名等于把两档写成同一个名字。
 *
 *  给弹窗 / 回执 / 停用提示那些「一句话」场合用。**列表行用 [comboTitleParts]**：那一行是标题，
 *  不带括号、也不念先后（顺序写在它自己的副标题里，见 [comboOrderSummary]）。
 *
 *  **只有一颗时不套框**（成员全被删掉、只剩 id 兜底那种畸形配置，以及用户口径那条规则）：
 * 一个名字旁边没有第二个名字需要分开，框只剩噪声。
 */
fun comboLabel(
    cfg: Config,
    combo: ComboKey,
): String {
    val names = comboKeyNames(cfg, combo)
    //  **单颗不展开、也不套括号**：一个名字旁边没有第二个名字要分开
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    if (combo.isHold) {
        val sep = if (combo.ordered) comboOrderedBetween else comboMemberSeparator
        return I18n
            .get(Strings.COMBO_MODE_HOLD)
            .replace("%1\$s", chipOrBare(names.first()))
            .replace("%2\$s", names.drop(1).joinToString(" $sep ") { chipOrBare(it) })
    }
    if (combo.ordered) return comboOrderedLabelOf(names, chip = true)
    return names.joinToString(" $comboMemberSeparator ") { chipOrBare(it) }
}

/**
 * 列表行标题的**一段**：要么是一颗键名，要么是键名之外的那些词（`按住` / `后` / `和`）。
 *
 *  分得开才能**只把连接词排小一号**（用户口径：键名保持标题字号，其余缩小）——
 *  拼成一整串之后界面认不出哪几个字是名字。
 */
class ComboTitlePart(
    val text: String,
    /** `true` = 这一段是一颗键名（按标题字号画）；`false` = 连接词（排小一号）。 */
    val isKeyName: Boolean,
)

/**
 * 组合列表行标题的**分段** —— 这一行标题的权威来源（[comboTitleText] 由它拼出来）。
 *
 *  等待档 `侧键` + ` 和 ` + `音量 +`；按住档 `按住 ` + `侧键` + ` 后 ` + `音量 +`，
 *  三颗时后面再接 ` 和 ` + `电量 +`。两档都**不区分顺序**：按不按先后由副标题说
 *  （见 [comboOrderSummary]），标题只回答「是哪几颗键、以哪种方式按」。
 *
 *  与 [comboLabel] 差两层：键名不套括号（这一行是**标题**不是句子，括号会变成噪声），
 *  也不念「后 / 再」这些先后说法。单颗不展开与它同一条规则。
 */
fun comboTitleParts(
    cfg: Config,
    combo: ComboKey,
): List<ComboTitlePart> {
    val names = comboKeyNames(cfg, combo)
    //  **单颗不展开**：一个名字旁边没有第二个名字要分开，也就没有连接词
    if (names.size <= 1) return listOf(ComboTitlePart(names.firstOrNull().orEmpty(), true))
    if (!combo.isHold) return names.withSeparators()
    //  按住档的句式在字典里（英文那句才排得顺）：拆成「首键之前 / 首键之后」两段文字
    val template = I18n.get(Strings.COMBO_MODE_HOLD_ORDERED)
    val head = template.substringBefore("%1\$s")
    val between = template.substringAfter("%1\$s").substringBefore("%2\$s")
    return buildList {
        //  模板也可能**以槽开头**（`%1$s按住后 %2$s`）：那一段是空的，不必塞进分段表
        if (head.isNotEmpty()) add(ComboTitlePart(head, false))
        add(ComboTitlePart(names.first(), true))
        add(ComboTitlePart(between, false))
        addAll(names.drop(1).withSeparators())
    }
}

/** 一串键名，两两之间插一段「和」—— 第一个名字前面什么也不插（前面由调用点自己接）。 */
private fun List<String>.withSeparators(): List<ComboTitlePart> =
    flatMapIndexed { i, name ->
        if (i == 0) {
            listOf(ComboTitlePart(name, true))
        } else {
            listOf(ComboTitlePart(" $comboMemberSeparator ", false), ComboTitlePart(name, true))
        }
    }

/**
 * 组合列表行标题的**纯文字**形态（[comboTitleParts] 拼起来）—— 给只需要一句话的通道用
 * （无障碍、日志）。界面按 [comboTitleParts] 分段上样式。
 */
fun comboTitleText(
    cfg: Config,
    combo: ComboKey,
): String = comboTitleParts(cfg, combo).joinToString("") { it.text }

/** 句子里加一对 `[` `]` 的键名 —— 两颗以上时才加（与 [comboLabelOf] 同一条「单颗不加」的规则）。 */
private fun chipOrBare(name: String): String = "$COMBO_KEY_OPEN$name$COMBO_KEY_CLOSE"

/**
 * 一串键名压成一句话 —— 单颗直接写名字，两颗以上各套一对 `[` `]`（`[侧键]和[电量 +]`）。
 *
 *  与 [comboKeyNames] 分开一支：需要**逐个各归各位**的调用点（顶栏副标题、列表行）拿名单，
 *  只需要一句话的调用点（回执 / 提示 / 弹窗标题）拿这个 —— 两边共用同一份「单颗不加」的规则。
 */
fun comboLabelOf(names: List<String>): String = comboLabelOf(names, comboMemberSeparator)

/** 同上，但用 [sep] 当两颗键之间的分隔符（按顺序那一档改用 [comboOrderedLabelOf]）。 */
fun comboLabelOf(
    names: List<String>,
    sep: String,
): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    return names.joinToString(" $sep ") { "$COMBO_KEY_OPEN$it$COMBO_KEY_CLOSE" }
}

/**
 * 按顺序那一档的成员念法 —— 「A 后 B 再 C」（英文 `A then B then C`）。
 *
 *  第一颗到第二颗用「后」，第二颗之后每一颗用「再」；与 [comboLabelOf] 的「和」分开，
 *  这里的「后 / 再」讲的是按下的先后，不是「一起按下」。单颗不展开（与它同一条规则）。
 *
 * @param chip 键名要不要套一对括号（`[侧键]`）
 */
fun comboOrderedLabelOf(
    names: List<String>,
    chip: Boolean = false,
): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()

    fun name(it: String): String = if (chip) chipOrBare(it) else it
    val head = name(names[0]) + " " + comboOrderedBetween + " " + name(names[1])
    return head + names.drop(2).joinToString("") { " " + comboOrderedAgain + " " + name(it) }
}
