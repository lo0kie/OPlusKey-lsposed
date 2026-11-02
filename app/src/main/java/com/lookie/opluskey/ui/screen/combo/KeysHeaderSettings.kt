package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PageIcons
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton

// 「按键功能」页顶栏右上角那颗「设置」—— 与 `PageTopBar` 的 `PageActions` 那几颗同一位置、同一尺寸。

/**
 * 顶栏右上角那颗「设置」—— 直接进「按键设置」页。
 *
 *  为什么这一页要有它：那一页里放着**与按键功能直接相关**的两项（组合按键设置：无操作时怎么走 /
 *  两颗键之间等多久）。原来它们就在这一页页尾，现在挪去了「按键设置」，留一颗入口，
 *  改完组合键不用先退回首页再点「按键设置」。
 *
 *  走 `navTo` 而不是替换栈顶：从这一页退回来应当回到按键功能，而不是直接回首页。
 */
@Composable
internal fun SettingsPage.KeysHeaderSettings() {
    //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`），这里不再自己振
    IconButton(
        onClick = { navTo(Screen.GENERAL) },
        minWidth = HEADER_ICON_SIZE,
        minHeight = HEADER_ICON_SIZE,
        cornerRadius = HEADER_ICON_SIZE / 2,
        // 与顶栏那颗 ⋮ 同一档右边距（见 `HeaderMenuButton`）—— 这一页只有这一颗，不用再让位
        modifier = Modifier.padding(end = 8.dp),
    ) {
        Icon(
            PageIcons.SettingsCog,
            contentDescription = I18n.get(Strings.GENERAL_TITLE, "按键设置"),
        )
    }
}
