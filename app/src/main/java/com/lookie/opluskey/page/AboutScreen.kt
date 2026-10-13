package com.lookie.opluskey.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** 「关于模块」页 —— 从首页 ⋮ 菜单进来（见 [Screen.ABOUT]）。 */
@Composable
internal fun SettingsPage.AboutScreen(scrollBehavior: ScrollBehavior) {
    val tick = rememberTick()
    Column(
        Modifier
            .fillMaxSize()
            // 大标题随滚动折叠：`nestedScroll` 必须在 `verticalScroll` 之前
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(bottom = PAGE_BOTTOM_PADDING),
    ) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。这一页首行是卡片，没有别页首行 `SmallTitle`
        // 自带的上内边距，要另加 [PAGE_TOP_PADDING]，否则贴着顶栏。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + PAGE_TOP_PADDING))

        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            BasicComponent(
                title = "版本",
                // 摘要里已含构建类型（在版号后面）
                summary = appVersionText(),
                startAction = { Icon(PageIcons.Info, null) },
                // 只读信息行，不给 onClick（给了反而像能点）
                cardListPosition = cardListPositionOf(0, OTHER_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = "查看地址",
                summary = REPO_URL,
                onClick = {
                    tick()
                    openUrl(REPO_URL)
                },
                startAction = { Icon(PageIcons.Address, null) },
                cardListPosition = cardListPositionOf(1, OTHER_ROWS),
            )
        }
    }
}
