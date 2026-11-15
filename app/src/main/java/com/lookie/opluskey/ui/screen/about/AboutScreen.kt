package com.lookie.opluskey.ui.screen.about

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.seal.SEAL_ENABLED
import com.lookie.opluskey.core.seal.Seal
import com.lookie.opluskey.core.seal.Verdict
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.appVersionBadge
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.chrome.updateHost
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.feedback.HINT_SLOT_PAGE
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.retireHint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.AUTHOR
import com.lookie.opluskey.ui.model.page.REPO_URL
import com.lookie.opluskey.ui.model.theme.AppTheme
import com.lookie.opluskey.ui.model.theme.PagePalette
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.ExpandableRow
import com.lookie.opluskey.ui.row.ExpandableRowSpec
import com.lookie.opluskey.ui.row.RowEndIcon
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.state.host.UpdateState
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 「正在检查更新…」那条提示的兜底时长 —— 检查最多 10 秒（见 `UpdateState`），再加 1 秒余量。 */
private const val UPDATE_CHECKING_HOLD_MS = 11_000L

// 「更多选项」页 —— 从首页「其他 → 更多选项」那一行进来（见 `Screen.ABOUT`）。
//
// 这一层只做三件事：**算**那几个进页一次的值（版本 / 签名）、**建**菜单那三个锚点、
// 按顺序把卡铺出来。内容在隔壁几个文件：
//   · `AboutCards.kt`       —— 运行状态卡 / 配置卡；
//   · `AboutMachineCard.kt` —— 「关于」卡；
//   · `AboutMenus.kt`       —— 那三行各自弹出来的菜单。
// 拆之前它是一个 378 行的函数，五段内容 + 三个菜单全平铺在一起。

/** 「更多选项」页。 */
@Composable
internal fun SettingsPage.AboutScreen(scrollBehavior: ScrollBehavior) {
    // 取版本号要过 PackageManager（见 [moduleVersionName]），本页每次重组都算一遍纯属白花。
    // 「运行状态」那张卡是 `@Composable` 读状态、会频繁重组，而版本号在整个进程生命里不变。
    //  这一页走 [appVersionBadge]（`version-1.1.0-release-2610201546`）：这一页本来就回答
    //  「我这个包是哪一个」，「什么时候出的」正是它要说的事。首页那条副标题走 [appVersionLabel]，
    //  仍按构建类型决定带不带。
    val version = remember { appVersionBadge() }
    // 「签名」那一行：也要跨进程问 PMS，同样进页算一次。
    //  判据读的是**本包**（`ModuleInfo.PACKAGE`）而不是当前进程所属的宿主，见 `core/seal/Seal`。
    val context = LocalContext.current
    val currentLang = AppLanguage.fromCode(configState.config.value.global.language)
    // 界面颜色那三档（跟随系统 / 浅色 / 深色）—— 与语言同一个读法：配置里存 code，界面认枚举。
    val currentTheme = AppTheme.ofCode(configState.config.value.global.theme)
    // 系统配色同理：配置里存色板 id，界面认枚举（认不出的落回默认档）。
    val currentPalette = PagePalette.ofId(configState.config.value.global.palette)
    //  **实际生效的语言档**才是这两行的缓存键：`signatureNote` / `signatureTitle` 里都嵌着
    //  字典文案（「签名」/「调试版本（不校验签名）」），只按 `context` 记的话切档不会重算。
    //  用配置档（`currentLang`）不够：AUTO 档下系统语言变了、生效语言跟着变，配置档仍是 AUTO。
    //  指纹本身与语言无关，切档时多算一次的代价只是一次 PackageManager 查询。
    val effectiveLang = I18n.effectiveLanguage
    //  指纹与语言无关（一次 PackageManager 查询），单独算一次；那一行**收起时**显示的是下面那句
    //  一行结论，指纹只在展开后才列出来（见 `ui/ExpandableRow`）。
    val fingerprint = remember(context) { Seal.ownFingerprint(context) }
    val signatureNote = remember(context, effectiveLang) { signatureNote(context) }
    // 标题平时是「签名」；签名与本包不符时**标题自己改口**（结论上移，摘要只剩指纹）
    val signatureTitle = remember(context, effectiveLang) { signatureTitle(context) }
    // 这只是个布尔（与语言无关），不必跟着语言重算
    val signatureOfficial = remember(context) { Seal.verdict(context) == Verdict.OFFICIAL }
    // 三行菜单的锚点与开关（语言 / 反馈问题 / 作者）—— 行与菜单共用一份，见 [AboutUiState]。
    val ui = remember { AboutUiState() }

    //  「检查更新」点下去就跑一趟（绕过「每进程一次」的闸）：先给一句「正在检查」，查完按结局
    //  给回执（有新版弹窗、已最新 / 没查到给提示）。跑在 `LaunchedEffect` 里是因为它要等回包。
    //
    //  **不能用 `checkUpdateNow` 当 effect 的 key**：里面会把这一位改回 false，等于在检查跑到
    //  一半时把 effect 自己取消重启 —— `checkManually` 之后的回执永远执行不到（真机口径
    //  「检查结束还是没有任何提示」）。所以 key 用 `Unit`，改用 `snapshotFlow` 盯这个触发位。
    LaunchedEffect(Unit) {
        snapshotFlow { ui.checkUpdateNow }
            .collect { want ->
                if (!want) return@collect
                ui.checkUpdateNow = false
                //  「正在检查」这条用「本页杂项」那一档 holdMs 撑不住 10 秒的检查（2 秒就自己没了，
                //  中间空一截）；给它一个长兜底，真正的收场是查完那句回执顶上来。
                hint(I18n.get(Strings.UPDATE_CHECKING), holdMs = UPDATE_CHECKING_HOLD_MS)
                val outcome = UpdateState.checkManually(this@AboutScreen.updateHost())
                val text =
                    when (outcome) {
                        UpdateState.CheckOutcome.LATEST -> I18n.get(Strings.UPDATE_LATEST)
                        else -> I18n.get(Strings.UPDATE_FAILED)
                    }
                //  有新版时已经弹窗了，不再叠一条提示 —— 而且要把「正在检查」那条**立刻收掉**：
                //  它的 holdMs 是 11 秒的兜底（见上面那句），不该真让它在弹窗后面挂那么久。
                if (outcome != UpdateState.CheckOutcome.OFFERED) hint(text) else retireHint(HINT_SLOT_PAGE)
            }
    }

    PageScrollColumn(scrollBehavior = scrollBehavior) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。这一页首行是 `SmallTitle`（下面「运行状态」
        // 那一个），它自带 8dp 上内边距，**不另加** [PAGE_TOP_PADDING] —— 那一档是给首行是卡片的
        // 「选择应用」的。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        //  块与块之间的间距由小标题自带（上 16 / 下 0），页面不再写 `Spacer`。
        SectionTitle(I18n.get(Strings.MORE_SECTION_STATUS))
        LinkStatusCard()

        SectionTitle(I18n.get(Strings.MORE_SECTION_APPEARANCE))
        AppearanceCard(currentTheme, currentPalette, ui)

        SectionTitle(I18n.get(Strings.MORE_SECTION_CONFIG))
        ConfigCard(currentLang, ui)

        SectionTitle(I18n.get(Strings.MORE_SECTION_ABOUT))
        MachineCard(
            version,
            SignatureInfo(signatureTitle, signatureNote, fingerprint, signatureOfficial),
            ui,
        )

        //  **浮层宿主**（本身不占布局）：摆在列容器外面，否则块间距会在它们前后各留一档空档。
        FeedbackMenu(ui)
        AuthorMenu(ui)
        VersionMenu(ui)
        LanguageMenu(ui, currentLang)
        ThemeMenu(ui, currentTheme)
        PaletteMenu(ui, currentTheme, currentPalette)
        // 「配置」卡里中间那两行：切换配置 / 导出配置，都是同一份配置列表的菜单
        ProfileMenu(ui)
        ProfileExportMenu(ui)
        // 新建 / 重命名那个输入窗（入口在「切换配置」菜单的底部）
        ProfileNamingDialog(ui)
        // 外链的确认窗（版本 / 作者 / 反馈那几行跳浏览器前先问一句）
        ExternalLinkDialog(ui)
        // 删除配置的确认窗（同一个位置点进来，删完才真删）
        ProfileDeleteConfirmDialog(ui)
        // 重置配置的确认窗（入口在「切换配置」菜单里，与删除同一套控件）
        ProfileResetConfirmDialog()
        // 删「读不出来的那一份」的确认窗（那一行点进去只剩这一条路）
        ProfileBrokenDeleteDialog(ui)
        // 导入配置选目标那一窗（文件校验通过后由 hook 侧置位 [SettingsPage.pendingImport]）
        ImportTargetDialog()
        // 覆盖某一份之前的那一次确认（选择器关掉之后才弹，见 `ImportTargetDialog` 的 `onDismissFinished`）
        ImportOverwriteConfirmDialog()
    }
}

/**
 * 「关于」卡里「签名」那一行要显示的四样 —— 标题 / 一句结论 / 指纹 / 是不是官方。
 *
 *  打成一个类，顺带把「这四样是同一行的事」写明。
 *
 * @param title 平时是「签名」，不符时改口成结论（见 [signatureTitle]）
 * @param note 收起时那一行（官方版是空串）
 * @param fingerprint 展开后列出的子行；读不到是 `null`
 * @param official 只决定行首图标的颜色
 */
internal class SignatureInfo(
    val title: String,
    val note: String,
    val fingerprint: String?,
    val official: Boolean,
)

/**
 * 「关于」卡里「签名」那一行的**标题** —— 平时就是「签名」，签名与本包不符时改口。
 *
 *  不符时把结论放到标题上（`签名不符`）：那一行只剩指纹，扫一眼标题就知道这包不是原版。
 *  而「是不是官方」这层结论**平时不占一行** —— 标题已经叫「签名」、指纹就摆在下面，
 *  再补一句「官方正版」只是把同一件事说两遍。
 */
private fun signatureTitle(context: Context): String =
    when (Seal.verdict(context)) {
        Verdict.UNOFFICIAL -> I18n.get(Strings.MORE_SIGNATURE_MISMATCH)
        else -> I18n.get(Strings.MORE_ABOUT_SIGNATURE)
    }

/**
 * 「关于」卡里「签名」那一行**收起时**显示的那一行 —— 一句结论，没有结论就是空串。
 *
 *  为什么这一行存在：封存那一层（`core/seal/Seal`）能挡住「换图重打包」，但**挡不住**
 *  「把比对与派生一起 patch 掉」的人；而**签名指纹他改不了** —— 改包必然重签，指纹就变。
 *  所以「一眼分真假」靠的是这一行：指纹展开就能拿去与发布页上那串对一遍。
 *
 *  三档各去哪儿：**官方**不给结论（标题已经叫「签名」）；**不符**的结论上了标题；
 *  **读不到**（PMS 拒绝 / 包不在）既不是「官方」也不是「被改过」，仍要说一句 ——
 *  混进任何一边都会误导（见 `Verdict`）。
 */
private fun signatureNote(context: Context): String =
    when (Seal.verdict(context)) {
        // 结论已上标题，这一行只留指纹（展开后看）
        Verdict.UNOFFICIAL -> {
            ""
        }

        Verdict.UNKNOWN -> {
            I18n.get(Strings.MORE_SIGNATURE_UNKNOWN)
        }

        // debug 包的结论也是 OFFICIAL，但那一侧**根本没校验**（`SealPin` 那份里 SEAL_ENABLED=false），
        //  必须说明 —— 否则调试包看起来像「已验过的正版」。
        Verdict.OFFICIAL -> {
            if (SEAL_ENABLED) "" else I18n.get(Strings.MORE_SIGNATURE_DEBUG)
        }
    }

/**
 * 「更多选项」页上那三个**行内菜单**的锚点与开关（语言 / 反馈问题 / 作者）。
 *
 *  三样东西长得一样，以前是三对 `remember { }` 摊在 `AboutScreen` 的开头 —— 谁和谁是一对
 *  只能靠变量名认；收成一个对象之后，`ui.langClick` / `ui.showLangMenu` 天然成组，
 *  菜单那三个 composable 也只需要一个参数。
 *
 *  注意：锚点必须**跟着那一行**（`preciseClickAnchor` 挂在行上），所以它建在这里、由行与菜单
 *  两边共用一份 —— 各建一份的话菜单会弹在别处。
 */
internal class AboutUiState {
    /** 「语言」那一行。 */
    val langClick = PreciseClickState()
    var showLangMenu by mutableStateOf(false)

    /** 「反馈问题」那一行（三个反馈渠道）。 */
    val feedbackClick = PreciseClickState()
    var showFeedbackMenu by mutableStateOf(false)

    /** 「作者」那一行（GitHub / 酷安两个去处，见 [com.lookie.opluskey.ui.model.target.AuthorTarget]）。 */
    val authorClick = PreciseClickState()
    var showAuthorMenu by mutableStateOf(false)

    /** 「版本」那一行的菜单（检查更新 / 项目仓库两个去处）。 */
    val versionClick = PreciseClickState()
    var showVersionMenu by mutableStateOf(false)

    /**
     * 等用户点头才跳的**外部地址**（`null` = 没有待确认的）—— 见 `ExternalLinkDialog`。
     *
     *  跳浏览器这件事一律先问一句：地址是别人给的（仓库 / 群 / 频道），点错一下就把人送出应用。
     */
    var pendingUrl by mutableStateOf<String?>(null)

    /** 「界面颜色」那一行（跟随系统 / 浅色 / 深色）。 */
    val themeClick = PreciseClickState()
    var showThemeMenu by mutableStateOf(false)

    /** 「系统配色」那一行（五套强调色，见 [com.lookie.opluskey.ui.model.theme.PagePalette]）。 */
    val paletteClick = PreciseClickState()
    var showPaletteMenu by mutableStateOf(false)

    /**
     * 「切换配置」那一行 —— 点开的是一层**菜单**（先列配置，底部是管理那三行）。
     *
     *  与其他几行同一个形状（语言 / 配色）：一行一档、点一下即作数。配置的**全部**管理动作
     *  （切换 / 新建 / 重命名 / 删除）都收在这一层菜单里 —— 首页 `⋮` 恢复原样，不再有配置相关项。
     */
    val profileClick = PreciseClickState()
    var showProfileMenu by mutableStateOf(false)

    /**
     * 「新建 / 重命名」那个输入窗：`null` = 关着。
     *
     *  两条路只在**标题**与**确认之后干什么**上不同，所以共用一个窗、一个状态（见 [ProfileNaming]）。
     *  起点（输入框的初值）也在里面 —— 新建是「配置 N」（要读一次盘上现在有几份），重命名是当前名字；
     *  两者都在**点那一行的那一刻**算好，不在组合期读盘。
     */
    var naming by mutableStateOf<ProfileNaming?>(null)

    /**
     * 「删除配置」的确认窗开着没有。
     *
     *  删掉一整份配置（连同它自己的命令库）不可恢复，与「重置配置」「删除命令」同一档代价：
     *  点一下就走太轻，所以先问一句。
     */
    var showDeleteConfirm by mutableStateOf(false)

    /**
     * 「读不出来的那一份」的删除确认 —— `null` = 关着。
     *
     *  那一份切不过去、也没有名字可改，列表里点它只剩「删掉」这一条路；删掉之后那个槽才腾出来
     *  （`isFull` 按槽位算，不删的话「新建 / 导入」会永久灰着）。
     */
    var brokenToDelete by mutableStateOf<ConfigProfiles.Item?>(null)

    /**
     * 「导出配置」那一行 —— 先选一份（同样是菜单）。
     *
     *  只有一份时这层菜单里也只有一行（「默认」）—— 仍然照常弹：**配置列表永远是同一形状**，
     *  不为「只有一份」特例出一个「点了就直接保存」的分支。
     */
    val exportClick = PreciseClickState()
    var showExportMenu by mutableStateOf(false)

    /**
     * 那几层菜单共用的那一份**配置列表** —— 点哪一行时读一次（盘上扫出来的，要读文件）。
     *
     *  同一时刻只可能开着一层，所以共用一格；关着时是上一次的结果（只用来铺行、数「有几份能删」）。
     */
    var profiles by mutableStateOf<List<ConfigProfiles.Item>>(emptyList())

    /**
     * 「签名」那一行**展开了没有** —— 指纹平时收着，点一下才列出来（见 `ui/ExpandableRow`）。
     *
     *  放进这里而不是 `remember` 在行里：这一位要在整页重组（比如切语言）之后仍保持，
     *  与上面几个菜单开关同一档。
     */
    var signatureExpanded by mutableStateOf(false)

    /** 「检查更新」那一行被点了 —— 由页面级副作用去跑一趟（见 `AboutScreen`）。 */
    var checkUpdateNow by mutableStateOf(false)
}

/**
 * 「新建 / 重命名」那个输入窗这一次要做什么。
 *
 * @param kind 新建一份，还是给当前配置改名
 * @param initial 输入框的起点（新建给「配置 N」，重命名给当前配置的名字）
 */
internal data class ProfileNaming(
    val kind: Kind,
    val initial: String,
) {
    enum class Kind { NEW, RENAME }
}

// 「版本」那一行的菜单 —— 与 `AboutMenus.kt` 里那几层（语言 / 主题 / 配色 / 配置）分开。
//
// 这一层与「检查更新」同属版本那一行的事，单独成文件读起来才不夹在别层菜单中间。

/** 「版本」那一行的菜单 —— 「检查更新」+「项目仓库」两处。 */
@Composable
internal fun SettingsPage.VersionMenu(ui: AboutUiState) {
    //  已经查到新版时，这一行改口叫「发现新版本」、图标从「重新查」换成「去下载」：
    //  结果已经有了，还写「检查更新」等于让人白点一次（点下去照样会弹窗，见 `AboutScreen`）。
    val found = UpdateState.hasUpdate
    PageMenu(
        entries =
            listOfNotNull(
                // 检查更新：点一下立即查，有新版弹窗、没新给句回执（见 `AboutScreen` 那个副作用）
                MenuEntry.Row(
                    title =
                        if (found) {
                            I18n.get(Strings.UPDATE_FOUND_ROW)
                        } else {
                            I18n.get(Strings.UPDATE_CHECK_ROW)
                        },
                    icon = if (found) PageIcons.Download else PageIcons.CheckUpdate,
                    //  行尾不挂红点：有新版这件事首页顶上那块「模块状态」已经说了（见 `HomeActivationCard`），
                    //  这条只负责改口与换图标 —— 点进去照样会弹窗
                ) {
                    ui.showVersionMenu = false
                    ui.checkUpdateNow = true
                },
                // 项目仓库：跳浏览器前先问一句（与「作者 / 反馈」同一条账）
                MenuEntry.Row(
                    title = I18n.get(Strings.MORE_ABOUT_REPO),
                    icon = PageIcons.BrandGithub,
                    submenu = true,
                    trailingIcon = PageIcons.Link,
                ) {
                    ui.showVersionMenu = false
                    ui.pendingUrl = REPO_URL
                },
            ).takeIf { ui.showVersionMenu }
                .orEmpty(),
        preciseClick = ui.versionClick,
        onDismiss = { ui.showVersionMenu = false },
    )
}

/**
 * 「关于」卡 —— 版本 / 签名 / 作者 / 反馈问题，四行固定。
 *
 *  本机那一行已经挪到「运行状态」卡的最上面：那是「这台机器现在什么状态」，与链路那几段同属一类。
 *
 *  放在整页**最下面**：上面两张答的是「怎么用、现在什么状态」，这一张是「这台机器 + 这个包」的
 *  自述，看过一次就够。
 *
 *  @param version 版号 + 构建类型 + 构建时刻（含日期），见 `appVersionText`
 *  @param signature 签名那一行的四样（标题 / 结论 / 指纹 / 是否官方），见 [SignatureInfo]
 */
@Composable
internal fun SettingsPage.MachineCard(
    version: String,
    signature: SignatureInfo,
    ui: AboutUiState,
) {
    CardList(
        rows =
            buildList {
                // 版本那一行 —— 点一下开一层菜单（检查更新 / 项目仓库），不再直接跳外链。
                add(
                    CardRow.Item(
                        title = I18n.get(Strings.MORE_ABOUT_VERSION),
                        summary = version,
                        icon = PageIcons.Versions,
                        //  行尾不挂红点：有新版由首页顶上那块「模块状态」说（见 `HomeActivationCard`）
                        trailing = { RowEndIcon(PageIcons.Selector) },
                        modifier = Modifier.preciseClickAnchor(ui.versionClick),
                        onClick = {
                            ui.showVersionMenu = true
                        },
                    ),
                )
                // 签名那一行自己是一整行（可展开的指纹），位置由列表递进来
                add(CardRow.Custom { position -> SignatureRow(signature, ui, position) })
                // 作者那一行 —— 点开的是一层菜单（GitHub / 酷安两个去处），不直接跳外部。
                //  行尾给选择器字形（不是链接符号）：链接符号留给菜单里那两行。
                add(
                    CardRow.Item(
                        title = I18n.get(Strings.MORE_ABOUT_AUTHOR),
                        summary = AUTHOR,
                        icon = PageIcons.UserScreen,
                        trailing = { RowEndIcon(PageIcons.Selector) },
                        modifier = Modifier.preciseClickAnchor(ui.authorClick),
                        onClick = {
                            ui.showAuthorMenu = true
                        },
                    ),
                )
                // 反馈与交流那一行 —— 同样是一层菜单；不给副标题（渠道名在菜单里逐条列着）。
                add(
                    CardRow.Item(
                        title = I18n.get(Strings.MORE_ABOUT_FEEDBACK),
                        icon = PageIcons.Feedback,
                        trailing = { RowEndIcon(PageIcons.Selector) },
                        modifier = Modifier.preciseClickAnchor(ui.feedbackClick),
                        onClick = {
                            ui.showFeedbackMenu = true
                        },
                    ),
                )
            },
    )
}

/**
 * 签名那一行 —— 结论在标题上（见 `signatureTitle`），指纹**收起来**、点一下才展开。
 *
 *  指纹是一长串十六进制，摊在摘要里会把这一行撑成两三行高，而它平时并不需要看；
 *  收起之后整张卡的行高齐了，要看时再展开（见 `ui/ExpandableRow`）。
 */
@Composable
private fun SettingsPage.SignatureRow(
    signature: SignatureInfo,
    ui: AboutUiState,
    position: CardListPosition,
) {
    // 值就是原来那一行的摘要（一句结论 + 指纹，中间是硬换行）。展开后与不折叠时**一模一样**，
    //  差别只在 `maxLines`，见 `ui/ExpandableRow`。
    val text = listOfNotNull(signature.note.ifEmpty { null }, signature.fingerprint).joinToString("\n")
    ExpandableRow(
        spec =
            ExpandableRowSpec(
                title = signature.title,
                text = text,
                icon = PageIcons.Signature,
                // 与「关于本机」同一套色：官方用常规色、「不是这包」用 error
                iconTint =
                    if (signature.official) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.error
                    },
                position = position,
            ),
        expanded = ui.signatureExpanded,
        onToggle = { ui.signatureExpanded = !ui.signatureExpanded },
    )
}
