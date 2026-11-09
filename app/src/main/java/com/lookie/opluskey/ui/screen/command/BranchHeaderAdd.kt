package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.HeaderIconButton
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import io.github.suqi8.coui.kmp.basic.Icon

// 「条件执行」页顶栏右上角那颗「添加」—— 与 `PageTopBar` 的 `PageActions` 那几颗同一位置、同一尺寸。

/**
 * 顶栏右上角那颗「添加」—— 与表尾那行「添加场景」是**同一个入口**（[addScene]）。
 *
 *  它加的是**一块空场景**（条件和动作都空着），落在表尾并播自己的进场；表里**只要还有一块
 *  没选条件就不往下加**，就地给一句原因（见 [addScene]）—— 两个空块并排放着，谁也说不清
 *  该填哪个。
 *
 * ## 加到上限之后
 *
 *  一张表最多 [SceneRule.MAX_SCENES] 块（上限是**这一份表**的，不是整页的：换一颗键又是另一张表）。
 *  到顶时这颗按钮**置灰**，点一下由 [DisabledHint] 那条通道说明原因 —— 只灰不解释的话，
 *  「点不动」和「坏了」分不开，而用户多半还想再挤一块进来。
 *
 *  置灰只管手感，真正的闸在 [addScene] 里（表尾那行、菜单、手改配置都走那里）。
 *
 * ## 为什么从右下角的 FAB 挪到顶栏
 *
 *  「添加」是**往这张表里加一行**，不是这一页的「主操作」——主操作是右下角那颗「确认」。
 *  两颗同色实心圆叠在角落时，它们的权重看起来一样，而这一页真正要人按的只有后者
 *  （用户口径：「去掉条件执行里的添加 FAB，改成页面右上角添加按钮」）。
 *  挪到顶栏之后：角落只剩一颗确认，加法留在「这一页有什么」那一层的工具位置上，
 *  与其它页「右上角进下一步」的位置也一致。
 *  表尾另有一行同样去处的「添加场景」（见 `BranchRuleColumn`）—— 列表长了之后不必抬手回顶栏。
 */
@Composable
internal fun SettingsPage.BranchHeaderAdd() {
    val scenes = branchDraftNow().scenes
    val atLimit = scenes.size >= SceneRule.MAX_SCENES
    DisabledHint(
        //  只在到顶时给这一句：没到顶时这颗按钮能点，`null` 就是「这一块没有自己的说法」——
        //  不许拿页面那句「整页为什么改不了」来顶替（那一句说的是另一件事）。
        text = if (atLimit) I18n.format(Strings.COMMAND_BRANCH_LIMIT, SceneRule.MAX_SCENES) else null,
    ) {
        //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`），这里不再自己振
        HeaderIconButton(
            enabled = !atLimit,
            onClick = { addScene() },
        ) {
            Icon(
                PageIcons.StepAdd,
                contentDescription = I18n.get(Strings.COMMAND_BRANCH_ADD, "添加场景"),
            )
        }
    }
}
