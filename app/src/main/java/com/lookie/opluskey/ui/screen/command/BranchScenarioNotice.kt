package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.SettingsPage

// 「这一档配了系统场景」那句常驻提示的文案 —— 从 `CommandBranches.kt` 拆出来：
//  那边是条件表本身（一行一条规则的编辑区），这一支只回答「这一档会不会被场景先拦下」。

/**
 * 这一档配了「系统场景」时该说的那句话；null = 没配（或那几条全停用了）。
 *
 *  消费方是页面里那条**常驻**提示（按键绑定页 / 组合键编辑页）：场景先判、命中就轮不到
 *  这一档绑的动作。不说一句，改完绑定按下去走的不是它。
 *  只算**启用中**的规则 —— 停用的拦不住任何东西，提它只会误导。
 */
internal fun SettingsPage.branchScenarioNotice(
    key: KeyId,
    gesture: Gesture,
): String? =
    scenarioNotice(
        configState.config.value
            .key(key)
            .rules
            .filter { it.gesture == gesture },
    )

/** 组合键那一侧的同一句话（规则挂在 combos 上，不在 keys 上）。 */
internal fun SettingsPage.comboScenarioNotice(
    comboId: String,
    gesture: Gesture,
): String? {
    val combo =
        configState.config.value.combos
            .firstOrNull { it.id == comboId } ?: return null
    return scenarioNotice(combo.rules.filter { it.gesture == gesture })
}

/** 规则表里有启用中的那几条，就报出条数；一条都没有 / 全停用 → null（整段不画）。 */
private fun scenarioNotice(rules: List<StateRule>): String? {
    val count = rules.count { it.enabled }
    if (count == 0) return null
    return I18n.format(Strings.COMMAND_BRANCH_SCENARIO_COUNT, count)
}
