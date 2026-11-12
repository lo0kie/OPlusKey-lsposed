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
 *  **这一行跟着这一档的启停走**（用户口径）：这一份停用了，它与「独立设置」一起灰掉 ——
 *  停用的键 / 组合不接管按键，表进去也改不出效果；灰着点一下说的是同一句停用原因。
 *  这一条与 [BindingTarget.pageBlockedReason] 的说明同步改过（那一段原先写的是反过来的规矩）。
 *
 * @param enabled 这一份自己**还开着**吗（判据由调用点给，见 `bindingTargetStopped`）
 * @param disabledReason 灰着那一句（同上）
 * @param onOpen 点这一行进「系统场景」—— 调用方负责把档位摆到眼前这一页再导航
 */
internal fun SettingsPage.scenarioEntry(
    target: BindingTarget,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onOpen: () -> Unit,
): CardRow.Item =
    CardRow.Item(
        title =
            scenarioCountNotice(target.rulesIn(configState.config.value))
                ?: I18n.get(Strings.COMMAND_BRANCH_SCENARIO_NONE, "没有添加系统场景"),
        icon = PageIcons.CommandBranch,
        arrow = true,
        enabled = enabled,
        disabledHint = disabledReason,
        onClick = onOpen,
    )

/** 规则表里有启用中的那几条，就报出条数；一条都没有 / 全停用 → `null`（整段不画）。 */
internal fun scenarioCountNotice(rules: List<SceneRule>): String? {
    val count = rules.count { it.enabled }
    if (count == 0) return null
    return I18n.format(Strings.COMMAND_BRANCH_SCENARIO_COUNT, count)
}
