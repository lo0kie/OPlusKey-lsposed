package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.HeaderIconButton
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import io.github.suqi8.coui.kmp.basic.Icon

// 「条件执行」页顶栏右上角那颗「添加」—— 与 `PageTopBar` 的 `PageActions` 那几颗同一位置、同一尺寸。

/**
 * 顶栏右上角那颗「添加」—— 这一页**唯一的加法入口**。
 *
 *  它加的是**一条空规则**（条件和动作都空着），落在表尾并播自己的进场；表尾那条还空着时
 *  不往下加，就地给一句原因（见 [addBranchRule]）。
 *
 * ## 加到上限之后
 *
 *  一个手势最多 [StateRule.MAX_PER_GESTURE] 条系统场景（上限是**当前这一档手势**的，不是全页
 *  的：切到另一个 tab 又是另一份表）。到顶时这颗按钮**置灰**，点一下由 [DisabledHint] 那条
 *  通道说明原因 —— 只灰不解释的话，「点不动」和「坏了」分不开，而用户多半还想再挤一条进来。
 *
 *  置灰只管手感，真正的闸在 [addBranchRule] 里（菜单、手改配置、将来别的入口都走那里）。
 *
 * ## 为什么从右下角的 FAB 挪到顶栏
 *
 *  「添加」是**往这张表里加一行**，不是这一页的「主操作」——主操作是右下角那颗「确认」。
 *  两颗同色实心圆叠在角落时，它们的权重看起来一样，而这一页真正要人按的只有后者
 *  （用户口径：「去掉条件执行里的添加 FAB，改成页面右上角添加按钮」）。
 *  挪到顶栏之后：角落只剩一颗确认，加法留在「这一页有什么」那一层的工具位置上，
 *  与其它页「右上角进下一步」的位置也一致。
 */
@Composable
internal fun SettingsPage.BranchHeaderAdd() {
    val rules = branchDraftNow().rules
    val atLimit = rules.size >= StateRule.MAX_PER_GESTURE
    DisabledHint(
        //  只在到顶时给这一句：没到顶时这颗按钮能点，`null` 就是「这一块没有自己的说法」——
        //  不许拿页面那句「整页为什么改不了」来顶替（那一句说的是另一件事）。
        text = if (atLimit) I18n.format(Strings.COMMAND_BRANCH_LIMIT, StateRule.MAX_PER_GESTURE) else null,
    ) {
        //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`），这里不再自己振
        HeaderIconButton(
            enabled = !atLimit,
            onClick = { addBranchRule(rules) { branchDraftNow().freshId = it } },
        ) {
            Icon(
                PageIcons.StepAdd,
                contentDescription = I18n.get(Strings.COMMAND_BRANCH_ADD, "添加场景"),
            )
        }
    }
}
