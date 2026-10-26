package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
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

// 「更多选项」页上那些**行内菜单**，一个菜单一个函数。
//
// 它们原来夹在 `AboutScreen` 的卡片之间 —— 三块各四十来行，把「哪张卡、哪一层菜单、哪个锚点」
// 这三件事搅在一起。挪出来之后菜单与它那一行各归各的，页面只剩「铺顺序」。
//
// 全部走 [PageMenu]：只给「行」与「锚点」，`show` 由「行表空不空」表达
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
 * 「切换配置」那一行的菜单 —— 上半是配置列表（当前配置打勾），底部压着管理那三行。
 *
 *  与 [LanguageMenu] 同一个形状（一行一档、点一下即作数）：配置也是「挑一档」这件事，不该用弹窗。
 *  管理动作（新建 / 重命名 / 删除）**不单独占卡片里的行** —— 它们只对「配置」这东西有意义，
 *  跟着列表一起放进这一层，卡片里就只剩「切换 / 导出 / 导入」三件事。
 *
 *  三行都作用于**当前配置**：新建是「复制当前、起个名字、切过去」，重命名与删除也是当前这份 ——
 *  菜单里点过的哪一份就是哪一份，不用再选一次（删除也不再开二级菜单）。
 */
@Composable
internal fun SettingsPage.ProfileMenu(ui: AboutUiState) {
    //  当前配置是不是「默认」（名字为空）—— 是的话不许改名、也不许删（删了就没地方回去了）
    val currentIsDefault =
        configState.config.value.profileName
            .isBlank()
    //  除「默认」之外还能不能再建一份（列表是本层菜单打开时读的那一份）
    val named = ui.profiles.count { !it.isDefault }
    val full = named >= ConfigProfiles.MAX_PROFILES
    PageMenu(
        entries =
            if (!ui.showProfileMenu) {
                emptyList()
            } else {
                buildList {
                    addAll(
                        profileRows(ui) { item ->
                            ui.showProfileMenu = false
                            //  点当前配置等于没点：不落盘、也不报「已切到」
                            if (!item.isCurrent) switchToProfile(item)
                        },
                    )
                    //  一条加深带隔开「列表」与「对列表动手」—— 上面是选一份，下面三行是改这份清单
                    addAll(profileManageRows(ui, currentIsDefault, full))
                }
            },
        preciseClick = ui.profileClick,
        onDismiss = { ui.showProfileMenu = false },
    )
}

/**
 * 菜单底部那三行**管理动作**（新建 / 重命名 / 删除）—— 从 [ProfileMenu] 里分出来：
 * 三个 `MenuEntry.Row` 连着写会把那个函数顶过 detekt 的 `LongMethod`。
 *
 *  三行都作用于**当前配置**：新建是复制当前这份、重命名与删除也认当前那一行。
 *
 * @param full 除「默认」之外已经三份了 ⇒ 「新建配置」灰掉
 */
private fun SettingsPage.profileManageRows(
    ui: AboutUiState,
    currentIsDefault: Boolean,
    full: Boolean,
): List<MenuEntry> =
    listOf(
        MenuEntry.Row(
            title = I18n.get(Strings.PROFILE_NEW, "新建配置"),
            icon = PageIcons.ProfileAdd,
            dividerBefore = true,
            //  除「默认」之外最多三份：到顶之后这一行灰掉并说明
            enabled = !full,
            disabledHint = if (full) I18n.get(Strings.PROFILE_FULL, "最多三份配置") else null,
        ) {
            ui.showProfileMenu = false
            //  名字的起点在点这一刻算好（要读一次盘上现在有几份）
            val initial = I18n.format(Strings.PROFILE_NEW_NAME, ConfigProfiles.list().size + 1)
            ui.naming = ProfileNaming(ProfileNaming.Kind.NEW, initial)
        },
        MenuEntry.Row(
            title = I18n.get(Strings.PROFILE_RENAME, "重命名配置"),
            icon = PageIcons.MenuRename,
            //  「默认」配置是升级时落下的退路，它的名字空着才认得出 —— 不给改
            enabled = !currentIsDefault,
            disabledHint = if (currentIsDefault) I18n.get(Strings.PROFILE_RENAME_DEFAULT, "默认配置不许改名") else null,
        ) {
            ui.showProfileMenu = false
            val now = configState.config.value.profileName
            ui.naming = ProfileNaming(ProfileNaming.Kind.RENAME, now)
        },
        MenuEntry.Row(
            title = I18n.get(Strings.PROFILE_DELETE, "删除配置"),
            icon = PageIcons.ProfileDelete,
            danger = true,
            //  删的是**当前配置**：先换「默认」上来，再把原来配置删掉（见 `deleteCurrentProfile`）
            enabled = !currentIsDefault,
            disabledHint = if (currentIsDefault) I18n.get(Strings.PROFILE_DELETE_DEFAULT, "默认配置不许删除") else null,
        ) {
            ui.showProfileMenu = false
            ui.showDeleteConfirm = true
        },
    )

/**
 * 「导出配置」那一行的菜单 —— 铺的是**同一份**配置列表，点哪份就导出哪份。
 *
 *  与 [ProfileMenu] 共用 [profileRows]：两处只差「点了之后干什么」。
 *  只有一份配置时这层菜单里也只有一行，仍然照常弹 —— 配置列表永远是同一形状。
 */
@Composable
internal fun SettingsPage.ProfileExportMenu(ui: AboutUiState) {
    PageMenu(
        entries =
            profileRows(ui) { item ->
                ui.showExportMenu = false
                hooks.onExportConfig?.invoke(item.slot)
            }.takeIf { ui.showExportMenu }
                .orEmpty(),
        preciseClick = ui.exportClick,
        onDismiss = { ui.showExportMenu = false },
    )
}

/**
 * 配置列表的菜单行 —— 切换与导出两处共用。
 *
 *  行首**一律是同一颗**「一叠纸」：这里每一行都是同一类东西（用户自己建的一份配置），
 *  区分靠名字；语言 / 配色那种**固定几档**才需要各自一颗字形。
 */
private fun SettingsPage.profileRows(
    ui: AboutUiState,
    onPick: (ConfigProfiles.Item) -> Unit,
): List<MenuEntry> =
    ui.profiles.map { item ->
        MenuEntry.Row(
            title = item.displayName,
            icon = PageIcons.Profiles,
            selected = item.isCurrent,
        ) {
            onPick(item)
        }
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
