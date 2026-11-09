package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId

// 组合键在界面上的**名字** —— 列表、弹窗、顶栏副标题、停用提示都用这一份。
//
//  住在 `ui/model` 而不是 `ui/screen/combo`：用它的人不止那两页 —— `ui/BindingTarget.kt`
//  也要（组合被停用时那一句「`「侧键」×「电量 +」` 已停用」），而 `ui/` 不许反过来认识
//  `ui/screen/*`（那条边由 `tools/audit-coupling.py` 核）。这两个函数本来就不读任何界面状态
//  （只要 `Config` + `ComboKey`），放在这里才是它们该在的层。

/**
 * 一档组合里两颗键之间的分隔符 —— 用「×」不用「+」。
 *
 *  键名本身就可能带加号（用户把一颗键叫「电量 +」），用「+」拼出来
 *  `「侧键」+「电量 +」` 分不清哪个加号是分隔符（用户口径「中间的 + 改成 x」）。
 */
internal const val COMBO_PAIR_SEPARATOR = "×"

/** 一颗键在界面上的名字（内置键走字典，自定义键用用户起的名字）。 */
internal fun keyName(id: KeyId): String = if (id.isCustom) id.label else I18n.get(Strings.KEY_SIDE, id.label)

/**
 * 一档组合的显示名 —— 成员键名连起来（`侧键 × 电量 +`）。
 *
 *  内置键的名字走字典（`侧键` 在英文界面下是 `Side key`），自定义键用用户起的名字：
 *  与 `GestureExec.comboTriggerLabel`（执行时提示的主语）同一套口径。
 *
 *  **列表行不用这一份**：那一列按颗数排圆角框（见 `ComboTitle`），拼成一整串做不到对齐。
 *  这里是给弹窗 / 回执 / 顶栏副标题 / 停用提示那些「只有一句话」的地方用的。
 */
internal fun comboLabel(
    cfg: Config,
    combo: ComboKey,
    /**
     * 每个键名外面套一对括号（中文 `「」`、英文 `｢｣`，见 [keyBrackets]）。
     *
     *  **除了纯日志之外一律要它**（用户口径）：列表行、顶栏副标题、删除确认窗、
     *  「已添加 / 已删除」的回执 —— `侧键 × 电量 +` 读起来像一句话，
     *  `「侧键」×「电量 +」` 才看得出这是两颗键的名字。
     *  模板里**不要再套一层引号**（`删除 %s？`，不是 `删除「%s」？`）：套两层会把两颗键读成一个名字。
     */
    bracketed: Boolean = false,
): String {
    val names = combo.keys.mapNotNull { id -> cfg.keyIdOf(id)?.let(::keyName) }
    val (open, close) = if (bracketed) keyBrackets() else "" to ""
    // 成员全被删掉时（手改的配置）至少给一句能认出来的话，不能是空串
    return names
        .ifEmpty { listOf(combo.id) }
        .joinToString(" $COMBO_PAIR_SEPARATOR ") { "$open$it$close" }
}
