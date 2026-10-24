package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton

// 「条件执行」页顶栏右上角那颗「添加」—— 与 `PageTopBar` 的 `PageActions` 那几颗同一位置、同一尺寸。

/**
 * 顶栏右上角那颗「添加」—— 这一页**唯一的加法入口**。
 *
 *  它加的是**一条空规则**（条件和动作都空着），落在表尾并播自己的进场；表尾那条还空着时
 *  不往下加，就地给一句原因（见 [addBranchRule]）。
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
    val tick = rememberTick()
    IconButton(
        onClick = {
            tick()
            addBranchRule(branchDraft.draft.rules, tick) { branchDraft.draft.freshId = it }
        },
        minWidth = HEADER_ICON_SIZE,
        minHeight = HEADER_ICON_SIZE,
        cornerRadius = HEADER_ICON_SIZE / 2,
        // 与顶栏那颗 ⋮ 同一档右边距（见 `HeaderMenuButton`）—— 这一页只有这一颗，不用再让位
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Icon(
            PageIcons.StepAdd,
            contentDescription = I18n.get(Strings.COMMAND_BRANCH_ADD, "添加条件"),
        )
    }
}
