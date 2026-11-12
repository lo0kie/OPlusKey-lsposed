package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.config.isDefaultProfile
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.target.AuthorTarget
import com.lookie.opluskey.ui.model.target.FeedbackTarget
import com.lookie.opluskey.ui.model.text.languageLabel
import com.lookie.opluskey.ui.model.text.paletteLabel
import com.lookie.opluskey.ui.model.text.themeTag
import com.lookie.opluskey.ui.model.text.themeTitle
import com.lookie.opluskey.ui.model.theme.AppTheme
import com.lookie.opluskey.ui.model.theme.PagePalette
import com.lookie.opluskey.ui.screen.home.openUrl
import com.lookie.opluskey.ui.wire.linkReady
import com.lookie.opluskey.ui.write.setLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                        ui.pendingUrl = target.url
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
                        ui.pendingUrl = target.url
                    }
                }.takeIf { ui.showAuthorMenu }
                .orEmpty(),
        preciseClick = ui.authorClick,
        onDismiss = { ui.showAuthorMenu = false },
    )
}

/**
 * 跳浏览器前的确认窗 —— **所有外链都走它**（用户口径）。
 *
 *  版本那一行、作者菜单、反馈菜单里点下去的都是别人给的地址：一下点歪就把人送出应用，
 *  回来还得重新翻到这一页。所以先把地址原样摆出来、问一句「打开网页？」。
 *
 *  正文就是那串地址本身（不另写一句「将离开本应用」）：要确认的正是「去哪」，
 *  而网址是唯一能核对的东西。
 *
 *  退场那一帧 `pendingUrl` 已经清空、正文会变空，所以**记一份最后一次的地址**给退场用
 *  （[lastUrl]）；否则窗在关的那半秒里会先「瘪」成一句标题。
 */
@Composable
internal fun SettingsPage.ExternalLinkDialog(ui: AboutUiState) {
    val url = ui.pendingUrl
    var lastUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        if (url != null) lastUrl = url
    }
    ConfirmDialog(
        show = url != null,
        title = I18n.get(Strings.MORE_LINK_OPEN_TITLE, "打开网页？"),
        summary = url ?: lastUrl,
        action = I18n.get(Strings.MORE_LINK_OPEN_ACTION, "打开"),
        //  跳个网页不是破坏性动作 —— 动作项用中性色，不用那档红
        positiveDanger = false,
        onDismiss = { ui.pendingUrl = null },
        onConfirm = {
            val target = url
            ui.pendingUrl = null
            if (target != null) openUrl(target)
        },
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
 * 「界面颜色」那一行的菜单（跟随系统 / 跟随系统 (Monet) / 浅色 / 深色）。
 *
 *  与 [LanguageMenu] 同一套：各档全列出来、当前那一档打勾、点别的档才写配置。
 *  各档各有各的字形 —— 共用一颗的话菜单里几行长得一模一样（语言那边踩过同一个坑）。
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
                        title = themeTitle(theme),
                        //  Monet 那一档的说明放在子标题（标题只写「跟随系统」，与上一档同一句）
                        summary = themeTag(theme),
                        icon =
                            when (theme) {
                                AppTheme.AUTO -> PageIcons.ThemeAuto
                                AppTheme.MONET -> PageIcons.ThemeMonet
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
 * 「切换配置」那一行的菜单 —— 上半是配置列表（当前配置打勾），底部压着管理那几行。
 *
 *  与 [LanguageMenu] 同一个形状（一行一档、点一下即作数）：配置也是「挑一档」这件事，不该用弹窗。
 *  管理动作（新建 / 重命名 / 重置 / 删除）**不单独占卡片里的行** —— 它们只对「配置」这东西
 *  有意义，跟着列表一起放进这一层，卡片里就只剩「切换 / 导出 / 导入」三件事。
 *
 *  四行都作用于**当前配置**：新建是「复制当前、起个名字、切过去」，重命名、重置与删除也是当前
 *  这份 —— 菜单里点过的哪一份就是哪一份，不用再选一次（删除也不再开二级菜单）。
 */
@Composable
internal fun SettingsPage.ProfileMenu(ui: AboutUiState) {
    //  到上限了没有 —— **与 `ConfigProfiles.createAs` 内部那把尺子同一个**（按**槽位数**，
    //  不按「有名字的份数」）。名字丢过的那几份同样是占着槽位的配置，按名字数会算少，
    //  菜单里于是不灰、点下去才报「最多三份配置」（真机踩过）。
    //  `ui.profiles` 是本层菜单打开时刚读的那一份，`remember` 的 key 就是它 —— 每次打开重算一次，
    //  不是每帧读盘。
    val full = remember(ui.profiles) { ConfigProfiles.isFull() }
    //  管理那几行里「新建配置」要先数一遍现有份数（`ConfigProfiles.list()` 是扫目录），
    //  那一下放到这个 scope 的 IO 上去（见 [profileManageRows]）。
    val scope = rememberCoroutineScope()
    PageMenu(
        entries =
            if (!ui.showProfileMenu) {
                emptyList()
            } else {
                buildList {
                    addAll(
                        profileRows(ui, markCurrent = true) { item ->
                            ui.showProfileMenu = false
                            //  点当前配置等于没点：不落盘、也不报「已切到」
                            if (!item.isCurrent) switchToProfile(item)
                        },
                    )
                    //  一条加深带隔开「列表」与「对列表动手」—— 上面是选一份，下面三行是改这份清单
                    addAll(profileManageRows(ui, full, scope))
                }
            },
        preciseClick = ui.profileClick,
        onDismiss = { ui.showProfileMenu = false },
    )
}

/**
 * 菜单底部那几行**管理动作**（新建 / 重命名 / 重置配置 / 删除配置）—— 从 [ProfileMenu] 里分出来。
 *
 *  四行都作用于**当前这份配置**，顺序按代价从小到大排：新建（复制当前、切过去）→ 改名 →
 *  重置（内容清空、按默认重建）→ 删除（整份消失）。重置与删除都吃危险色，但**不另起分隔带** ——
 *  它们与上面两行是同一类动作（对这份配置动手），隔开反而读成两件事。
 *
 * @param full 除「默认」之外已经三份了 ⇒ 「新建配置」灰掉
 * @param scope 「新建配置」数现有份数用的 —— 那一下要扫目录，不能占着主线程（见下）
 */
private fun SettingsPage.profileManageRows(
    ui: AboutUiState,
    full: Boolean,
    scope: CoroutineScope,
): List<MenuEntry> {
    //  「重置配置」原来挂在首页 `⋮` 菜单上（那会儿叫「清空配置」），闸还是同一个：链路没通、
    //  或这次配置读不出来时不放出来点 —— 只读态下点它，正好会把「读不懂的那份」当成用户的意图覆盖掉
    //  （见 `resetCurrentProfile`）。
    val canReset = linkReady && !configState.readOnly
    //  默认配置是升级时落下的退路，**不许改名、不许删除**（用户口径）。这两行直接灰掉并说明，
    //  而不是点了再被拒 —— 拦在门口比事后报错省一轮。
    val isDefault = configState.config.value.isDefaultProfile
    return listOf(
        MenuEntry.Row(
            title = I18n.get(Strings.PROFILE_NEW, "新建配置"),
            icon = PageIcons.ProfileAdd,
            dividerBefore = true,
            //  除「默认」之外最多三份：到顶之后这一行灰掉并说明
            enabled = !full,
            disabledHint = if (full) I18n.get(Strings.PROFILE_FULL, "最多三份配置") else null,
        ) {
            ui.showProfileMenu = false
            //  名字的起点要在点这一刻算好：只数**自定义**的份数（「默认」不占号，第一份就是「配置 1」）。
            //  `ConfigProfiles.list()` 是扫目录 + 逐份解码，原来就在这个点击回调里同步跑；挪到 IO 上，
            //  取回页面状态（`ui.naming`）那一步仍回主线程。
            scope.launch {
                val named =
                    withContext(Dispatchers.IO) {
                        ConfigProfiles.list().count { !it.isDefault }
                    }
                ui.naming = ProfileNaming(ProfileNaming.Kind.NEW, I18n.format(Strings.PROFILE_NEW_NAME, named + 1))
            }
        },
        MenuEntry.Row(
            title = I18n.get(Strings.PROFILE_RENAME, "重命名"),
            icon = PageIcons.ProfileRename,
            //  默认配置的名字空着就是它的身份（见 `Config.isDefaultProfile`）：改了名之后
            //  「升级时落下的退路」在列表里就认不出来了 —— 所以这一行在默认配置上直接灰掉。
            //  置灰行必须带自己的说法：不给的话点了会弹**整页那条**（「这一页现在为什么改不了」），
            //  那是答非所问。
            enabled = !isDefault,
            disabledHint = if (isDefault) I18n.get(Strings.PROFILE_DEFAULT_RENAME_LOCKED, "默认配置不能改名") else null,
        ) {
            ui.showProfileMenu = false
            val now = configState.config.value.profileName
            ui.naming = ProfileNaming(ProfileNaming.Kind.RENAME, now)
        },
        //  夹在「重命名」与「删除」中间：这三行都是**对当前这份配置动手**，按代价从小到大排
        //  （改名 → 清空内容 → 删掉整份）。它清的是主配置 + 命令库两份文件（见 `resetCurrentProfile`），
        //  所以 `danger` 照给 —— 名字叫「重置」，代价由确认窗那句话交代（`ProfileClearConfirmDialog`）。
        MenuEntry.Row(
            title = I18n.get(Strings.MENU_RESET_CONFIG, "重置配置"),
            icon = PageIcons.ProfileReset,
            danger = true,
            enabled = canReset,
            disabledHint = if (canReset) null else I18n.get(Strings.MENU_RESET_CONFIG_OFF, "模块未就绪"),
        ) {
            ui.showProfileMenu = false
            overlays.confirmReset = true
        },
        MenuEntry.Row(
            title = I18n.get(Strings.PROFILE_DELETE, "删除配置"),
            icon = PageIcons.ProfileDelete,
            danger = true,
            //  删的是**当前配置**：先换一份上来，再把原来配置删掉（见 `deleteCurrentProfile`）。
            //  闸两道：还有别的配置能退回去；以及当前这份**不是默认配置**（退路不许删）。
            //  原因是「默认配置」时才给这一行自己的说法（同上一行）；「只剩一份」不另写文案。
            enabled = ui.profiles.size > 1 && !isDefault,
            disabledHint = if (isDefault) I18n.get(Strings.PROFILE_DEFAULT_DELETE_LOCKED, "默认配置不能删除") else null,
        ) {
            ui.showProfileMenu = false
            ui.showDeleteConfirm = true
        },
    )
}

/**
 * 「导出配置」那一行的菜单 —— 铺的是**同一份**配置列表，点哪份就导出哪份。
 *
 *  与 [ProfileMenu] 共用 [profileRows]：两处只差「点了之后干什么」，以及**要不要打勾** ——
 *  切换那层要标出「现在生效的是哪份」，导出与「当前」无关（点哪份导哪份），所以一行勾都不打，
 *  单纯列出来（[markCurrent] 传 `false`）。
 *  只有一份配置时这层菜单里也只有一行，仍然照常弹 —— 配置列表永远是同一形状。
 *
 *  行尾给**箭头**（[profileRows] 的 `enter = true`）：这一层每行点下去是进系统的「保存到」，
 *  与切换那层「就地选一份」不是同一种交互 —— 用箭头把「还会再开一层」说出来。
 */
@Composable
internal fun SettingsPage.ProfileExportMenu(ui: AboutUiState) {
    PageMenu(
        entries =
            profileRows(ui, markCurrent = false, brokenDeletable = false, enter = true) { item ->
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
 *  行首按**第几份**取数字牌面（`number-1` / `number-2` / `number-3`），与列表顺序一致；
 *  「默认」不参与编号，仍用「一叠纸」—— 它是升级时落下的退路，不是用户建的第 N 份。
 *
 *  **读不出来的那一份**（`broken`）只在切换那层出现，且按危险色画、点了直接弹删除确认 ——
 *  它切不过去、也没有名字可改，留着只是占一个槽位（`isFull` 按槽位算）。导出那层不列它：
 *  导出来的只会是一份空配置。
 *
 * @param markCurrent 要不要标出当前生效的那份（[MenuEntry.Row.selected]：行首换成对勾、
 *   文字走主色，并**滚进视窗**）。切换那层要；导出那层不要 —— 那一层与「当前」无关。
 * @param brokenDeletable 读不出来的那几份要不要列出来（切换那层 `true`，导出那层 `false`）
 * @param enter 行尾要不要给箭头 —— 导出那一层要，切换那一层不要。判据是「点下去之后去了哪」：
 *   导出那一层每行都**进下一层**（系统的「保存到」），切换那一层是就地选一份（[MenuEntry.Row.selected]
 *   打勾）、点完菜单就关，没有下一层可进。
 */
private fun SettingsPage.profileRows(
    ui: AboutUiState,
    markCurrent: Boolean,
    brokenDeletable: Boolean = true,
    enter: Boolean = false,
    onPick: (ConfigProfiles.Item) -> Unit,
): List<MenuEntry> {
    //  只给自定义的那几份编号（默认那份排在首行，跳过后序号从 1 开始）
    val numbered = ui.profiles.filter { !it.isDefault }
    return ui.profiles.mapNotNull { item ->
        if (item.broken) {
            if (!brokenDeletable) return@mapNotNull null
            return@mapNotNull MenuEntry.Row(
                title = item.displayName,
                icon = PageIcons.ProfileDelete,
                danger = true,
                dividerBefore = item == ui.profiles.first(),
            ) {
                //  这一行唯一的动作是「删掉这个坏掉的槽」，不问切换。
                //  只在切换那层出现，所以这里直接收掉那层菜单（导出层不列它，见 `brokenDeletable`）
                ui.showProfileMenu = false
                ui.brokenToDelete = item
            }
        }
        MenuEntry.Row(
            title = item.displayName,
            icon =
                if (item.isDefault) {
                    PageIcons.Profiles
                } else {
                    PageIcons.menuKeyNumber(numbered.indexOf(item))
                },
            selected = markCurrent && item.isCurrent,
            //  `submenu` 是行尾那一格的**唯一开关**（不置的话给不给字形都不画），
            //  不传 `trailingIcon` ⇒ 用默认的右箭头
            submenu = enter,
        ) {
            onPick(item)
        }
    }
}

/**
 * 「系统配色」那一行的菜单（五套常量色 + 一档「系统取色」）。
 *
 *  与 [ThemeMenu] 同一套：全列出来、当前那一套打勾、点别的才写配置。
 *  行首是**这一套的色块**（[PageIcons.accentSwatch]，颜色由 `iconTint` 给的那个种子色），
 *  于是「蓝 / 绿 / 橙 / 紫 / 玫红」不用读字也分得出来。
 *
 *  色块形状跟主题走：Monet 那一档整页按系统取色，色块画成四格色阶；其余档只有一个强调色，
 *  画成**一个整块** —— 四格在那几档会被读成「四块不同的色」。
 *
 * @param currentTheme 只用来选色块形状（见 `PageIcons.accentSwatch`）
 */
@Composable
internal fun SettingsPage.PaletteMenu(
    ui: AboutUiState,
    currentTheme: AppTheme,
    currentPalette: PagePalette,
) {
    val context = LocalContext.current
    PageMenu(
        entries =
            PagePalette.entries
                .map { palette ->
                    MenuEntry.Row(
                        title = paletteLabel(palette),
                        icon = PageIcons.accentSwatch(currentTheme),
                        // 行首那块色就是这一套的强调色（「系统取色」那一档现取系统强调色）
                        iconTint = palette.seed(context),
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
