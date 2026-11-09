package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

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
 * 一档组合键的显示名 —— 成员键名连起来，每颗各套一对框：`[侧键]×[电量 +]`。
 *
 *  给弹窗 / 回执 / 顶栏副标题 / 停用提示那些「只有一句话」的地方用。**列表行不用这一份**：
 *  那一列按颗数排圆角框（见 `ComboTitle`），拼成一整串做不到对齐。
 *
 *  **只有一颗时不套框**（成员全被删掉、只剩 id 兜底那种畸形配置，以及用户口径那条规则）：
 *  一个名字旁边没有第二个名字需要分开，框只剩噪声。
 */
fun comboLabel(
    cfg: Config,
    combo: ComboKey,
): String {
    // 成员全被删掉时（手改的配置）至少给一句能认出来的话，不能是空串
    val names = combo.keys.mapNotNull { id -> cfg.keyIdOf(id)?.let(::keyName) }.ifEmpty { listOf(combo.id) }
    if (names.size == 1) return names.first()
    return names.joinToString(" $COMBO_PAIR_SEPARATOR ") { "$COMBO_KEY_OPEN$it$COMBO_KEY_CLOSE" }
}
