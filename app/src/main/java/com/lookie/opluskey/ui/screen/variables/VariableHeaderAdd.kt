package com.lookie.opluskey.ui.screen.variables

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

// 「自定义变量」页顶栏右上角那颗「新建」—— 与 `PageTopBar` 的 `PageActions` 那几颗同一位置、同一尺寸。

/**
 * 顶栏右上角那颗「新建变量」—— 这一页**唯一的加法入口**。
 *
 *  点开的是新建抽屉（`VariablePageState.creating`），不在页里当场加一行：名字 / 类型 / 初始值
 *  三样得先填，抽屉才是那个填法。
 *
 * ## 为什么从右下角的 FAB 挪到顶栏
 *
 *  这一页只有「一列变量」这一个内容，右下角那颗实心圆会**一直压着最后一行的右侧**，
 *  滚到底时那一行的箭头就压在它下面。挪到顶栏之后：加法留在「这一页有什么」那一层的
 *  工具位置上，与「条件执行」页那颗添加键（`BranchHeaderAdd`）同一个位置、同一个理由。
 */
@Composable
internal fun SettingsPage.VariableHeaderAdd() {
    val tick = rememberTick()
    IconButton(
        onClick = {
            tick()
            VariablePageState.creating = true
        },
        minWidth = HEADER_ICON_SIZE,
        minHeight = HEADER_ICON_SIZE,
        cornerRadius = HEADER_ICON_SIZE / 2,
        // 与顶栏那颗 ⋮ 同一档右边距（见 `HeaderMenuButton`）—— 这一页只有这一颗，不用再让位
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Icon(
            PageIcons.CommandAdd,
            contentDescription = I18n.get(Strings.VARIABLES_ADD, "新建变量"),
        )
    }
}
