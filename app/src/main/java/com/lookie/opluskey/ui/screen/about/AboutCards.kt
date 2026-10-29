package com.lookie.opluskey.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.linkChecks
import com.lookie.opluskey.ui.model.AppTheme
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.PagePalette
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.model.paletteLabel
import com.lookie.opluskey.ui.model.themeLabel
import com.lookie.opluskey.ui.setFollowDisplaySize
import com.lookie.opluskey.ui.setPageScale
import com.lookie.opluskey.ui.state.deviceModel
import com.lookie.opluskey.ui.state.deviceSupport
import com.lookie.opluskey.ui.switchToNotice
import com.lookie.opluskey.ui.writeAndNotice
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「更多选项」页里那几张卡 —— 从 `AboutScreen.kt` 拆出来。
//
//  `AboutScreen` 原来是一个 378 行的函数，从头到尾平铺五段内容 + 三个菜单。按**一张卡**切开之后：
//   · 本文件              —— 运行状态卡 / 配置卡；
//   · `AboutMachineCard.kt` —— 「关于」卡（本机 / 版本 / 签名 / 作者 / 反馈）；
//   · `AboutMenus.kt`     —— 那三行各自弹出来的菜单。
//  页面本身只剩「铺顺序」这一件事。

/**
 * 「运行状态」（内部叫链路自检）—— 模块要正常工作，得**几段**都通（宿主侧的服务、注入侧的
 * 接管点、配置送达、以及宿主真读到配置），外加一段只能靠实践拿到的证据。
 *
 *  只看「宿主报活了没有」一个布尔的话，答不了「注入侧压根没装」这种（那种情况下宿主照样报活、
 *  页面全绿）。只读信息，没有可点的行；明细**只列不通的那几段**（正常的不占地方），
 *  一行明细都没有时由「整体状态」那一行报「全部正常」。
 *
 *  注意：数据是**读缓存**、不是「进页时现取」：`linkChecks()` 读的是 `hostServiceUp` /
 *  `injectStatus` 这些**已经缓存在页面状态里**的结论，本页**不发任何问句**；主动报活只有首页
 *  那条自检会做，注入侧也只应答、不主动报。所以进本页看到的是「最近一次自检的结论」，
 *  想刷新就回首页走一遍。
 *
 *  这一页是「细节」的落点，措辞仍是给用户看的：段名与取值都不出现内部结构（不写「注入侧 /
 *  同步 / 读取 / 就绪」），想知道「怎么坏的」去看日志。
 */
@Composable
internal fun SettingsPage.LinkStatusCard() {
    val checks = linkChecks()
    val broken = checks.firstOrNull { !it.ok && it.blocking }
    val adapted = deviceSupport == DeviceSupport.Support.ADAPTED
    //  明细**只列真故障**：正常那几段不占地方 —— 这一段本来就只在出问题时才需要看。
    //  「主开关已关闭」与「尚未使用」两种不 ok 不算故障（[LinkCheck.blocking] 那句），
    //  留下它们会让这一页平时也恒挂两行，还与「整体状态」那句结论打架。
    //  汇总行常留：它负责回答「到底有没有事」，一行明细都没用时靠它报「全部正常」。
    val detail =
        if (broken == null) {
            I18n.get(Strings.LINK_CHECK_ALL_NORMAL, "全部正常")
        } else {
            I18n.format(Strings.LINK_CHECK_BROKEN_FORMAT, broken.name)
        }
    CardList(
        rows =
            buildList {
                // 机型是这一张卡的**第一行**：它答的也是「这台机器现在什么状态」，与下面那几段同类。
                //  只读、**不给 `onClick`**（给了反而像能点）。
                add(
                    CardRow.Item(
                        title = localDeviceTitle(),
                        summary = adaptVerdict(deviceSupport),
                        // 适配命中 / 没命中 —— 用「这台机器认不认得」两个字形
                        icon = if (adapted) PageIcons.DeviceAdapted else PageIcons.DeviceUnsupported,
                        // **一律常规色**：这一行只是「这一组验过没有」的提示，不是故障。
                        //  原先没命中时染成 error 红，用户口径是「很多用户以为不能用」——
                        //  与 AGENTS 一、13 同一条：清单不参与任何「看起来坏了」的表达。
                        //  认不认得靠字形分，不靠颜色骂人。
                        iconTint = COUITheme.colorScheme.onSurfaceContainer,
                    ),
                )
                // 整体结论那一行（缺哪一段直接点名）
                add(statusRow(I18n.get(Strings.LINK_CHECK_OVERALL_STATUS, "整体状态"), broken == null, detail))
                // 明细行：只有故障那几段
                checks.filter { !it.ok && it.blocking }.forEach { add(statusRow(it.name, false, it.detail)) }
            },
    )
}

/**
 * 本机那一行的主标题 —— 机型号与代号都摆出来。
 *
 *  清单里那台机器用清单上记录的名字（自带代号）；不在清单里就只有代号，毕竟它的名字我们也不知道。
 *  代号都读不出来时给空串（标题不画）—— 读不出来是「没读到」、不是结论，副标题那句结论照常给。
 */
private fun localDeviceTitle(): String = DeviceSupport.adaptedNameOf(deviceModel).ifEmpty { deviceModel }

/**
 * 「适配清单」里「本机」那一行的结论 —— 判定三档各一句（见 `core/model/DeviceSupport`）。
 *
 *  三句话说的是**三件不同的事**，所以不合并成两档：已适配 / **这一版系统没实测过** /
 *  这个**型号压根没实测过**。后两档的措辞是同一套：都说「没实测过，请自行尝试
 *  是否可用」，区别只在「版本」还是「型号」—— 前者是清单里有这机器、只是 ROM 版本没登记，
 *  后者是这台机器压根没见过。原来那句「不在适配清单」只说了事实、没告诉用户该怎么办。
 *  清单只是「这一组验过没有」的提示，不拦注入（AGENTS.md 一、13）。
 */
private fun adaptVerdict(support: DeviceSupport.Support): String =
    when (support) {
        DeviceSupport.Support.ADAPTED -> {
            I18n.get(Strings.ADAPTATION_ADAPTED, "已适配")
        }

        DeviceSupport.Support.PENDING -> {
            I18n.get(Strings.ADAPTATION_PENDING, "此版本没有实测过，请自行尝试是否可用")
        }

        DeviceSupport.Support.UNSUPPORTED -> {
            I18n.get(Strings.ADAPTATION_UNSUPPORTED, "此型号没有实测过，请自行尝试是否可用")
        }
    }

/**
 * 运行状态里的一行 —— 字形按「正常 / 故障」两档取色（明细进到这里只剩故障那一档，
 * 汇总行正常时走常规色）。
 */
@Composable
private fun statusRow(
    title: String,
    ok: Boolean,
    detail: String,
): CardRow =
    CardRow.Item(
        title = title,
        summary = detail,
        icon = if (ok) PageIcons.LinkOk else PageIcons.LinkBroken,
        iconTint = if (ok) COUITheme.colorScheme.onSurfaceContainer else COUITheme.colorScheme.error,
    )

/**
 * 「配置」卡（语言 / 切换配置 / 导出 / 导入）。
 *
 *  导出走系统的「保存到」、导入走系统的「打开」，两边都由 hook 侧实现
 * （`SettingsPageHooks`：SAF + `Activity#onActivityResult`），这里只负责派发。
 *
 *  中间两行（切换 / 导出）都是**点开一层菜单**：铺的是同一份「这台机器上有哪几份配置」，
 *  一件事在页面上不该有两种形状 —— 以前切换是 `⋮` 菜单里一项 + 选择器弹窗、导出又一张弹窗。
 */
@Composable
internal fun SettingsPage.ConfigCard(
    currentLang: AppLanguage,
    ui: AboutUiState,
) {
    //  除「默认」之外已满三份 ⇒ 「导入配置」这一行也灰掉（「新建配置」在切换菜单里，同一道闸）。
    //  只能放进 remember：判一次要列目录，不能每次重组都去碰文件系统。
    val profilesFull = remember(configState.config.value, ui.profiles.size) { ConfigProfiles.isFull() }
    CardList(
        rows =
            listOf(
                // 「语言」：点开的是一层菜单，行尾给选择器字形（不是右箭头）
                CardRow.Item(
                    title = I18n.get(Strings.MORE_LANGUAGE, "语言"),
                    summary = languageLabel(currentLang),
                    icon = PageIcons.Language,
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(ui.langClick),
                    onClick = {
                        ui.showLangMenu = true
                    },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.PROFILE_SWITCH_TITLE, "切换配置"),
                    // 副标题写**当前配置**的名字 —— 与「语言」那一行写当前语言同一档
                    summary = currentProfileName(),
                    icon = PageIcons.Profiles,
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(ui.profileClick),
                    onClick = {
                        //  列表是盘上扫出来的（每份要读文件取名字），点这一行时读一次即可
                        ui.profiles = ConfigProfiles.list()
                        ui.showProfileMenu = true
                    },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.MORE_EXPORT_CONFIG, "导出配置"),
                    // 副标题只报「做什么」的一句话，不解释机制 —— 用户界面不写
                    // 「把配置与自定义命令存成一份文件」那种交代细节的写法
                    summary = I18n.get(Strings.MORE_EXPORT_CONFIG_SUMMARY, "保存到本地文件"),
                    icon = PageIcons.ConfigExport,
                    // 行尾选择器：点开的是一层「导出哪份」的菜单
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(ui.exportClick),
                    onClick = {
                        ui.profiles = ConfigProfiles.list()
                        ui.showExportMenu = true
                    },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.MORE_IMPORT_CONFIG, "导入配置"),
                    summary = I18n.get(Strings.MORE_IMPORT_CONFIG_SUMMARY, "从本地文件读取"),
                    icon = PageIcons.ConfigImport,
                    arrow = true,
                    //  到上限时灰掉：选完文件再报「最多三份」等于白让人挑一遍
                    enabled = !profilesFull,
                    disabledHint = if (profilesFull) I18n.get(Strings.PROFILE_FULL, "最多三份配置") else null,
                    onClick = {
                        hooks.onImportConfig?.invoke()
                    },
                ),
            ),
    )
}

/** 当前配置在界面上的叫法（「切换配置」那一行的副标题）。 */
private fun SettingsPage.currentProfileName(): String =
    configState.config.value.profileName
        .ifBlank { I18n.get(Strings.PROFILE_DEFAULT, "默认配置") }

/**
 * 「外观」那张卡 —— 主题（跟随系统 / 浅色 / 深色）+ 强调色（页面强调色）。
 *
 *  行序：**先深浅、后强调色** —— 深浅定的是整页的明暗，强调色是在它上面挑一个点缀；
 *  反过来摆的话，用户会先去挑颜色，再发现整页明暗变了、那一套色在他刚看到的底色上并不好看。
 *
 *  两行都是同一种交互（点开一层菜单、行尾给选择器、副标题写当前那一档），与上面「配置」卡里的
 *  「语言」完全一致 —— 挑「一共五档里的哪一档」这件事，页面上不该有两种形状。
 *
 *  两处都**没有「保存」这一步**：点一下即写盘、整页当场换（`PageContent` 读配置决定配色与深浅）。
 *  这一卡改的是外观，改错了再点一下就行，不需要一次确认。
 */
@Composable
internal fun SettingsPage.AppearanceCard(
    currentTheme: AppTheme,
    currentPalette: PagePalette,
    ui: AboutUiState,
) {
    CardList(
        rows =
            listOf(
                // 第 1 行：主题
                CardRow.Item(
                    title = I18n.get(Strings.MORE_THEME, "主题"),
                    summary = themeLabel(currentTheme),
                    icon = PageIcons.Theme,
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(ui.themeClick),
                    onClick = {
                        ui.showThemeMenu = true
                    },
                ),
                // 第 2 行：强调色 —— 副标题写的是**当前那一套的名字**，不是「页面强调色」这种说明句：
                //  这一行回答的是「现在是哪一套」，与「语言」那一行同一个口径。
                //  行首直接摆**当前那一套的色块**（与菜单里那五行同一颗字形、同一个种子色）：
                //  这一行回答的就是「现在是什么色」，画一颗调色板图标等于把答案藏起来。
                CardRow.Item(
                    title = I18n.get(Strings.MORE_PALETTE, "强调色"),
                    summary = paletteLabel(currentPalette),
                    icon = PageIcons.PaletteSwatch,
                    iconTint = currentPalette.seed,
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(ui.paletteClick),
                    onClick = {
                        ui.showPaletteMenu = true
                    },
                ),
                // 第 3 行：跟随系统显示大小 —— 关掉后模块页面按出厂标准大小渲染（见 GlobalConfig）
                CardRow.Switch(
                    title = I18n.get(Strings.MORE_FOLLOW_DISPLAY_SIZE, "跟随系统显示大小"),
                    summaryOn =
                        I18n.get(Strings.MORE_FOLLOW_DISPLAY_SIZE_ON, "页面比例随系统变化"),
                    summaryOff =
                        I18n.get(Strings.MORE_FOLLOW_DISPLAY_SIZE_OFF, "页面显示为按标准大小"),
                    icon = PageIcons.TextSize,
                    checked = configState.config.value.global.followDisplaySize,
                    onCheckedChange = {
                        setFollowDisplaySize(it)
                    },
                ),
                // 第 4 行：页面缩放 —— **只在「跟随系统显示大小」关掉时出现**（跟着它一起进出场）。
                //  跟随开着时再摆一根缩放，等于两处都能调页面大小，谁也说不清最后是多少。
                //  整行另起一支（[PageScaleRow]）：这一卡已经四行了，再平铺下去就数不清行序。
                CardRow.Custom(visible = !configState.config.value.global.followDisplaySize) { position ->
                    PageScaleRow(position)
                },
            ),
    )
}

/** 「页面缩放」那一行 —— 从 [SettingsPage.AppearanceCard] 里分出来，让那一卡只留行序。 */
@Composable
private fun SettingsPage.PageScaleRow(position: CardListPosition) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.MORE_PAGE_SCALE, "页面缩放"),
                summary = I18n.get(Strings.MORE_PAGE_SCALE_DESC, "只影响模块页面"),
                icon = PageIcons.ZoomIn,
                min = GlobalConfig.PAGE_SCALE_MIN,
                max = GlobalConfig.PAGE_SCALE_MAX,
                value = configState.config.value.global.pageScale,
                default = GlobalConfig.DEFAULT_PAGE_SCALE,
                // 5% 一档：70~150 之间 17 档，拖起来细但不至于挪一格要半天
                step = 5,
                // 行尾把当前档写成「100%」—— 这一行问的就是「缩到多少」，不写就看不出来
                valueText = { "$it%" },
            ),
    ) { setPageScale(it) }
}

/**
 * 把主题写进配置。
 *
 *  与滑杆那几行同一档：**不判 `linkReady`**（写的是外观、落盘就生效，不需要注入侧回话）。
 *  写完弹一句「主题 已切换到「深色」」—— 回执带主语，与「强调色」「语言」那两行同一个句式
 *  （只说「已切换到「深色」」时看不出是哪一项变了，见 `switchToNotice`）。
 */
internal fun SettingsPage.setTheme(code: String) {
    val cfg = configState.config.value
    if (cfg.global.theme == code) return
    writeAndNotice(cfg.copy(global = cfg.global.copy(theme = code)), "theme=$code") {
        switchToNotice(
            I18n.get(Strings.MORE_THEME, "主题"),
            themeLabel(AppTheme.ofCode(code)),
        )
    }
}

/** 把强调色写进配置 —— 与 [setTheme] 同一档，回执「强调色 已切换到「蓝色」」。 */
internal fun SettingsPage.setPalette(id: String) {
    val cfg = configState.config.value
    if (cfg.global.palette == id) return
    writeAndNotice(cfg.copy(global = cfg.global.copy(palette = id)), "palette=$id") {
        switchToNotice(
            I18n.get(Strings.MORE_PALETTE, "强调色"),
            paletteLabel(PagePalette.ofId(id)),
        )
    }
}
