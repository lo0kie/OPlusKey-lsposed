package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.model.PageIcons

/**
 * 「这一档的系统场景入口」那一行 —— 「按键绑定」页与「组合键编辑」页共用这一份。
 *
 *  两页这一行说的是同一件事（场景先判、命中就轮不到下面那些绑定），摆的位置也一样；
 *  各写一遍的话，「停用了还点不点得进去」「提示说的是哪一句」迟早分叉 —— 而它们本来就该同步。
 *
 *  **停用只灰这一行**（用户口径）：那颗键 / 那一档组合停用之后，场景表本身不动，
 *  只是这个入口进不去 —— 键都不接管了，进去改那份表没有任何去处。
 *
 * @param notice 这一档的场景说明（「有 3 个系统场景判断」/「没有添加系统场景」）
 * @param enabled 这一档现在算不算数（键或组合的 `enabled`）
 * @param disabledHint 灰着被点时说的那一句（键与组合各自的措辞由调用点给）
 */
internal fun scenarioEntryRow(
    notice: String,
    enabled: Boolean,
    disabledHint: String?,
    onClick: () -> Unit,
): CardRow.Item =
    CardRow.Item(
        title = notice,
        icon = PageIcons.CommandBranch,
        arrow = true,
        enabled = enabled,
        disabledHint = if (enabled) null else disabledHint,
        onClick = onClick,
    )

/**
 * 某一档的**场景入口整行** —— 说明、闸门、灰着的说法全按 [target] 算。
 *
 *  单键页与组合页都调它：两个页面在这一行上**没有区别**，差别只在 `target` 是谁。
 *  在此之前两页各算一遍（一个 `branchScenarioNotice` + 一堆 `enabled` 判断，一个
 *  `comboScenarioNotice` + 另一堆），措辞与闸门迟早对不上。
 *
 * @param onOpen 点这一行进「系统场景」—— 调用方负责把档位摆到眼前这一页再导航
 */
internal fun SettingsPage.scenarioEntry(
    target: BindingTarget,
    onOpen: () -> Unit,
): CardRow.Item {
    val cfg = configState.config.value
    val blocked = target.scenarioBlockedReason(cfg)
    return scenarioEntryRow(
        notice =
            scenarioCountNotice(target.rulesIn(cfg))
                ?: I18n.get(Strings.COMMAND_BRANCH_SCENARIO_NONE, "没有添加系统场景"),
        enabled = blocked == null,
        disabledHint = blocked,
        onClick = onOpen,
    )
}

/** 规则表里有启用中的那几条，就报出条数；一条都没有 / 全停用 → `null`（整段不画）。 */
internal fun scenarioCountNotice(rules: List<SceneRule>): String? {
    val count = rules.count { it.enabled }
    if (count == 0) return null
    return I18n.format(Strings.COMMAND_BRANCH_SCENARIO_COUNT, count)
}
