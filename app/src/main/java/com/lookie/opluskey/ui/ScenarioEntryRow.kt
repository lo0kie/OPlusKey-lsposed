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
 *  **这一行不看这一档的启停**：停用的键 / 组合照样能进去把场景配好、再回去启用。
 *  灰掉这一行等于先把「先配好再开」这条顺序堵死 —— 与「独立设置」那条入口同一条口径。
 *
 * @param onOpen 点这一行进「系统场景」—— 调用方负责把档位摆到眼前这一页再导航
 */
internal fun SettingsPage.scenarioEntry(
    target: BindingTarget,
    onOpen: () -> Unit,
): CardRow.Item =
    CardRow.Item(
        title =
            scenarioCountNotice(target.rulesIn(configState.config.value))
                ?: I18n.get(Strings.COMMAND_BRANCH_SCENARIO_NONE, "没有添加系统场景"),
        icon = PageIcons.CommandBranch,
        arrow = true,
        onClick = onOpen,
    )

/** 规则表里有启用中的那几条，就报出条数；一条都没有 / 全停用 → `null`（整段不画）。 */
internal fun scenarioCountNotice(rules: List<SceneRule>): String? {
    val count = rules.count { it.enabled }
    if (count == 0) return null
    return I18n.format(Strings.COMMAND_BRANCH_SCENARIO_COUNT, count)
}
