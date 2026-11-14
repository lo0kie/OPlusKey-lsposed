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

/**
 * 组合键里每颗键名外面那一对框 —— **紧贴名字、不留空格**（`[侧键]`，不是 `[ 侧键 ]`）。
 *
 *  框的用处只有一个：把「两颗键的名字」和「一句话」分开（`[侧键]×[电量 +]` 一眼看出是两颗键）。
 *  所以只有**组合**才套它；单键直接写名字，见 [keyName]。
 */
private const val COMBO_KEY_OPEN = "["
private const val COMBO_KEY_CLOSE = "]"

/**
 * 一颗键在界面上的名字 —— **直接写名字，不套框**（用户口径：只有一个键名时不用框）。
 *
 *  内置键的名字走字典（`侧键` 在英文界面下是 `Side key`），自定义键用用户起的名字。
 *
 *  这一条也是**执行时提示**（`GestureExec`）与「按键功能」页清单的唯一来源 ——
 *  两处原先各有一份实现，改一处就会有一处还是旧叫法。
 */
fun keyName(id: KeyId): String = if (id.isCustom) id.label else I18n.get(Strings.KEY_SIDE, id.label)

/**
 * 一档组合键的**成员键名**（`["侧键", "电量 +"]`）—— 需要**逐个上样式**的地方用它。
 *
 *  顶栏副标题把每颗键画成一枚圆角框（与列表行、场景条件同一套 tag），拼成一整串文字就画不出来；
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
 * 一档组合键的显示名。
 *
 *  · **等待组合**：成员连起来、每颗各套一对框 —— `[侧键]×[电量 +]`。成员是「一起按下的一堆」，
 *    顺序不影响判据，所以按成员顺序写。
 *  · **按住组合**：写成一句 **`按住 [侧键] 后再按 [电量 +]`** —— 那两档的判据就是「谁先按住」，
 *    顺序是它的一半身份（`ComboKey.mode`），只念成员名等于把两档写成同一个名字。
 *
 *  给弹窗 / 回执 / 停用提示 / **列表行**那些地方用。列表行里等待组合那一种仍会把每颗画成圆角框
 *  （见 `ComboTitle`），按住组合那一种直接用这一句文本 —— 句子里的框正是为了把名字与措辞分开。
 *
 *  **只有一颗时不套框**（成员全被删掉、只剩 id 兜底那种畸形配置，以及用户口径那条规则）：
 *  一个名字旁边没有第二个名字需要分开，框只剩噪声。
 */
fun comboLabel(
    cfg: Config,
    combo: ComboKey,
): String {
    val names = comboKeyNames(cfg, combo)
    //  按住那一档：**首键在前、后续成员跟一句**（`按住 [侧键] 后再按 [电量 +]×[相机]`）。
    //  首键就是 `keys` 第一位（迁移之后老配置也已经是这个形状），所以这里不再有「要不要倒过来」。
    //
    //  不写成 `I18n.format(…)`：那条按顺序填占位，模板里只有两个 `%s`，第三颗会被静默丢掉；
    //  这里按段替换，后续成员有几个就连几个。
    //  成员不足两颗（配置被手改坏 / 迁移中途）：那句「按住 A 后再按 B」的后半截没有内容，
    //  会念成「按住 [侧键] 后再按 」—— 退回普通念法，至少是句完整的话。
    if (combo.isHold && names.size >= 2) {
        return I18n
            .get(Strings.COMBO_MODE_HOLD, "按住 %1\$s 后再按 %2\$s")
            .replace("%1\$s", chipOrBare(names.firstOrNull().orEmpty()))
            .replace("%2\$s", names.drop(1).map(::chipOrBare).joinToString(" $COMBO_PAIR_SEPARATOR "))
    }
    return comboLabelOf(names)
}

/** 句子里的一个键名 —— 两颗以上时套框（与 [comboLabelOf] 同一条「单颗不套框」的规则）。 */
private fun chipOrBare(name: String): String = "$COMBO_KEY_OPEN$name$COMBO_KEY_CLOSE"

/**
 * 一串键名压成一句话 —— 单颗直接写名字，两颗以上各套一对框（`[侧键]×[电量 +]`）。
 *
 *  与 [comboKeyNames] 分开一支：需要**逐个上样式**的调用点（顶栏副标题、列表行）拿名单，
 *  只需要一句话的调用点（回执 / 提示 / 弹窗标题）拿这个 —— 两边共用同一份「单颗不套框」的规则。
 */
fun comboLabelOf(names: List<String>): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    return names.joinToString(" $COMBO_PAIR_SEPARATOR ") { "$COMBO_KEY_OPEN$it$COMBO_KEY_CLOSE" }
}
