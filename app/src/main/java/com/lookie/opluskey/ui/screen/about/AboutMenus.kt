package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.MenuPopup
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.AuthorTarget
import com.lookie.opluskey.ui.model.FeedbackTarget
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.screen.home.openUrl
import com.lookie.opluskey.ui.setLanguage

// 「更多选项」页上那三个**行内菜单**，一个菜单一个函数。
//
// 它们原来夹在 `AboutScreen` 的卡片之间 —— 三块各四十来行，把「哪张卡、哪一层菜单、哪个锚点」
// 这三件事搅在一起。挪出来之后菜单与它那一行各归各的，页面只剩「铺顺序」。

/**
 * 「反馈问题」那一行的菜单（三个渠道）。
 *
 *  三条**都列出来**（未配地址的档置灰、点了不派发，见 `MenuEntry.Row.enabled`）
 *  ——「有这个渠道、只是还没接上」比「菜单里少一项」说得清。
 */
@Composable
internal fun SettingsPage.FeedbackMenu(ui: AboutUiState) {
    MenuPopup(
        show = ui.showFeedbackMenu,
        preciseClick = ui.feedbackClick,
        onDismiss = { ui.showFeedbackMenu = false },
        entries =
            buildList {
                FeedbackTarget.entries.forEachIndexed { index, target ->
                    if (index > 0) add(MenuEntry.RowLine)
                    add(
                        MenuEntry.Row(
                            title = target.label,
                            icon = target.icon,
                            // 行尾要那颗链接字形 —— 得**自己声明** `submenu` 把那一格打开
                            //  （本组件不替调用点猜，见 `MenuEntry.Row.submenu` 的说明）
                            submenu = true,
                            trailingIcon = PageIcons.Link,
                        ) {
                            ui.showFeedbackMenu = false
                            openUrl(target.url)
                        },
                    )
                }
            },
    )
}

/** 「作者」那一行的菜单（GitHub / 酷安）—— 两处**一律可点**，菜单里不留灰行。 */
@Composable
internal fun SettingsPage.AuthorMenu(ui: AboutUiState) {
    MenuPopup(
        show = ui.showAuthorMenu,
        preciseClick = ui.authorClick,
        onDismiss = { ui.showAuthorMenu = false },
        entries =
            buildList {
                AuthorTarget.entries.forEachIndexed { index, target ->
                    if (index > 0) add(MenuEntry.RowLine)
                    add(
                        MenuEntry.Row(
                            title = target.label,
                            icon = target.icon,
                            submenu = true,
                            trailingIcon = PageIcons.Link,
                        ) {
                            ui.showAuthorMenu = false
                            openUrl(target.url)
                        },
                    )
                }
            },
    )
}

/** 「语言」那一行的菜单（跟随系统 / 简体中文 / English）。 */
@Composable
internal fun SettingsPage.LanguageMenu(
    ui: AboutUiState,
    currentLang: AppLanguage,
) {
    MenuPopup(
        show = ui.showLangMenu,
        preciseClick = ui.langClick,
        onDismiss = { ui.showLangMenu = false },
        entries =
            buildList {
                AppLanguage.entries.forEachIndexed { index, lang ->
                    // 相邻两行一条细线 —— 与 ⋮ 菜单 / 长按菜单 /「添加命令」同一件（`MenuEntry.RowLine`）
                    if (index > 0) add(MenuEntry.RowLine)
                    add(
                        MenuEntry.Row(
                            title = languageLabel(lang),
                            // 三项各自的字形（齿轮 / 汉字「文」/ abc）—— 见 [PageIcons.LanguageAuto] 那一段
                            // 的注释：共用一颗地球的话，菜单里三行看起来一模一样。
                            icon =
                                when (lang) {
                                    AppLanguage.AUTO -> PageIcons.LanguageAuto
                                    AppLanguage.ZH_CN -> PageIcons.LanguageChinese
                                    AppLanguage.EN -> PageIcons.LanguageEnglish
                                },
                            selected = lang == currentLang,
                        ) {
                            ui.showLangMenu = false
                            if (lang != currentLang) {
                                setLanguage(lang.code)
                            }
                        },
                    )
                }
            },
    )
}
