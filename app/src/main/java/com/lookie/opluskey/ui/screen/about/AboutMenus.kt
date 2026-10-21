package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.AppTheme
import com.lookie.opluskey.ui.model.AuthorTarget
import com.lookie.opluskey.ui.model.FeedbackTarget
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.PagePalette
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.model.paletteLabel
import com.lookie.opluskey.ui.model.themeLabel
import com.lookie.opluskey.ui.screen.home.openUrl
import com.lookie.opluskey.ui.setLanguage

// 「更多选项」页上那三个**行内菜单**，一个菜单一个函数。
//
// 它们原来夹在 `AboutScreen` 的卡片之间 —— 三块各四十来行，把「哪张卡、哪一层菜单、哪个锚点」
// 这三件事搅在一起。挪出来之后菜单与它那一行各归各的，页面只剩「铺顺序」。
//
// 五个菜单都走 [PageMenu]：只给「行」与「锚点」，`show` 由「行表空不空」表达
// （`takeIf { 开着 }.orEmpty()`），弹层本身那些参数一个都不写。

/**
 * 「反馈问题」那一行的菜单（三个渠道）。
 *
 *  三条**都列出来**（未配地址的档置灰、点了不派发，见 `MenuEntry.Row.enabled`）
 *  ——「有这个渠道、只是还没接上」比「菜单里少一项」说得清。
 */
@Composable
internal fun SettingsPage.FeedbackMenu(ui: AboutUiState) {
    PageMenu(
        entries =
            FeedbackTarget.entries
                .map { target ->
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
                    }
                }.takeIf { ui.showFeedbackMenu }
                .orEmpty(),
        preciseClick = ui.feedbackClick,
        onDismiss = { ui.showFeedbackMenu = false },
    )
}

/** 「作者」那一行的菜单（GitHub / 酷安）—— 两处**一律可点**，菜单里不留灰行。 */
@Composable
internal fun SettingsPage.AuthorMenu(ui: AboutUiState) {
    PageMenu(
        entries =
            AuthorTarget.entries
                .map { target ->
                    MenuEntry.Row(
                        title = target.label,
                        icon = target.icon,
                        submenu = true,
                        trailingIcon = PageIcons.Link,
                    ) {
                        ui.showAuthorMenu = false
                        openUrl(target.url)
                    }
                }.takeIf { ui.showAuthorMenu }
                .orEmpty(),
        preciseClick = ui.authorClick,
        onDismiss = { ui.showAuthorMenu = false },
    )
}

/** 「语言」那一行的菜单（跟随系统 / 简体中文 / English）。 */
@Composable
internal fun SettingsPage.LanguageMenu(
    ui: AboutUiState,
    currentLang: AppLanguage,
) {
    PageMenu(
        entries =
            AppLanguage.entries
                .map { lang ->
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
                    }
                }.takeIf { ui.showLangMenu }
                .orEmpty(),
        preciseClick = ui.langClick,
        onDismiss = { ui.showLangMenu = false },
    )
}

/**
 * 「界面颜色」那一行的菜单（跟随系统 / 浅色 / 深色）。
 *
 *  与 [LanguageMenu] 同一套：三档全列出来、当前那一档打勾、点别的档才写配置。
 *  三档各有各的字形 —— 共用一颗的话菜单里三行长得一模一样（语言那边踩过同一个坑）。
 */
@Composable
internal fun SettingsPage.ThemeMenu(
    ui: AboutUiState,
    currentTheme: AppTheme,
) {
    PageMenu(
        entries =
            AppTheme.entries
                .map { theme ->
                    MenuEntry.Row(
                        title = themeLabel(theme),
                        icon =
                            when (theme) {
                                AppTheme.AUTO -> PageIcons.ThemeAuto
                                AppTheme.LIGHT -> PageIcons.ThemeLight
                                AppTheme.DARK -> PageIcons.ThemeDark
                            },
                        selected = theme == currentTheme,
                    ) {
                        ui.showThemeMenu = false
                        if (theme != currentTheme) {
                            setTheme(theme.code)
                        }
                    }
                }.takeIf { ui.showThemeMenu }
                .orEmpty(),
        preciseClick = ui.themeClick,
        onDismiss = { ui.showThemeMenu = false },
    )
}

/**
 * 「系统配色」那一行的菜单（五套强调色）。
 *
 *  与 [ThemeMenu] 同一套：全列出来、当前那一套打勾、点别的才写配置。
 *  行首是**这一套的色块**（[PageIcons.PaletteSwatch] 的方块 + `iconTint` 给的那个种子色），
 *  于是「蓝 / 绿 / 橙 / 紫 / 玫红」不用读字也分得出来。
 */
@Composable
internal fun SettingsPage.PaletteMenu(
    ui: AboutUiState,
    currentPalette: PagePalette,
) {
    PageMenu(
        entries =
            PagePalette.entries
                .map { palette ->
                    MenuEntry.Row(
                        title = paletteLabel(palette),
                        icon = PageIcons.PaletteSwatch,
                        // 行首那块色就是这一套的强调色
                        iconTint = palette.seed,
                        selected = palette == currentPalette,
                    ) {
                        ui.showPaletteMenu = false
                        if (palette != currentPalette) {
                            setPalette(palette.id)
                        }
                    }
                }.takeIf { ui.showPaletteMenu }
                .orEmpty(),
        preciseClick = ui.paletteClick,
        onDismiss = { ui.showPaletteMenu = false },
    )
}
