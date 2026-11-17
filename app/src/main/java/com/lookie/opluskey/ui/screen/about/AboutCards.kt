package com.lookie.opluskey.ui.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.DEBUG_PROBE
import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.config.isDefaultProfile
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.rom.DeviceSupport
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.switchToNotice
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.text.languageLabel
import com.lookie.opluskey.ui.model.text.paletteLabel
import com.lookie.opluskey.ui.model.text.themeTitle
import com.lookie.opluskey.ui.model.theme.AppTheme
import com.lookie.opluskey.ui.model.theme.PagePalette
import com.lookie.opluskey.ui.row.AccordionCard
import com.lookie.opluskey.ui.row.AccordionChevron
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.RowEndIcon
import com.lookie.opluskey.ui.row.RowEndSummary
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.row.rememberAccordionState
import com.lookie.opluskey.ui.state.page.deviceModel
import com.lookie.opluskey.ui.state.page.deviceSupport
import com.lookie.opluskey.ui.wire.linkChecks
import com.lookie.opluskey.ui.write.setDetailedKeySettings
import com.lookie.opluskey.ui.write.setMonetColors
import com.lookie.opluskey.ui.write.setPageScale
import com.lookie.opluskey.ui.write.setPredictiveBack
import com.lookie.opluskey.ui.write.setRandomIconTint
import com.lookie.opluskey.ui.write.setReduceIcons
import com.lookie.opluskey.ui.write.setUiVibrate
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *  页面全绿）。整块是**手风琴**：平时只摆「整体状态」一行，点它把每一段都滑出来
 *  （正常那几段也列，回答「到底查了哪几段」），再点收起。
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
    //  调试通道（`--es checks all`）：**四段全列出来、每段都换成「坏」的字形**（汇总行同）——
    //  只看行与字形，不反映真实结论。平时只列 `blocking` 那几段。
    val showAllBroken = DEBUG_PROBE && link.debugShowAllChecks
    val abnormal = if (showAllBroken) checks else checks.filter { !it.ok && it.blocking }
    val broken = if (showAllBroken) checks.firstOrNull() else checks.firstOrNull { !it.ok && it.blocking }
    //  明细**只列真故障**：正常那几段不占地方 —— 这一段本来就只在出问题时才需要看。
    //  「主开关已关闭」与「尚未使用」两种不 ok 不算故障（[LinkCheck.blocking] 那句），所以不进明细
    //  （排查时用调试通道 `--es checks all` 把四段全列出来，那时它们照自己的结论画）；
    //  但「主开关关着」会经 [LinkCheck.inSummary] 出现在**汇总行**（它意味着所有按键都不接管），
    //  「尚未使用」不会（那只是还没拿到证据，不是结论）。
    //  汇总行常留：它负责回答「到底有没有事」，一行明细都没用时靠它报「全部正常」。
    //  **不红不等于「一切正常」**：主开关关着 / 每颗键都停用这两档不报红（是用户的意图），
    //  但它们意味着**一颗键都不会被接管** —— 那时说「全部正常」，用户按下去却是系统的功能，
    //  而页面上一个字都没提为什么（真机撞过：自检全绿、所有按键都用不了）。
    //  有这种话要说时，整行按「不 ok」画（常规色、不用红），副标题直接说那句话。
    val offNote = checks.firstOrNull { !it.ok && it.inSummary }
    val detail =
        when {
            broken != null -> I18n.format(Strings.LINK_CHECK_BROKEN_FORMAT, broken.name)
            offNote != null -> offNote.detail
            else -> I18n.get(Strings.LINK_CHECK_ALL_NORMAL)
        }
    val overallOk = abnormal.isEmpty() && offNote == null
    //  展开之前只有汇总那一行：**点它**把每一段都滑出来（正常的也列，回答「到底查了哪几段」）。
    //  展开后正文行走 `CardList` 的 `visible`，收起时连行间分隔线一起缩回去。
    val accordion = rememberAccordionState()
    AccordionCard(
        state = accordion,
        header = { expanded, toggle ->
            // 汇总那一行（缺哪一段直接点名）。字形只分两档：正常 `home-check`、
            //  不正常 `home-cancel` —— 这一行回答的就是「合起来好不好」，别去细分是哪一段
            //  （那是展开后明细行的活）。
            statusRow(
                icon = if (overallOk) PageIcons.LinkOk else PageIcons.LinkBroken,
                title = I18n.get(Strings.LINK_CHECK_OVERALL_STATUS),
                ok = overallOk,
                detail = detail,
                onClick = toggle,
                expanded = expanded,
            )
        },
        body = { visible ->
            // 每一段各占一行（全部列出，不只异常的那几段）。每段自带字形
            //  （`LinkCheck.icon` / `iconBroken`）—— 服务 / 按键 / 设置 / 使用各一颗，
            //  几段一起坏时也能一眼看出断在哪一环，而不是一整列一样的叉。
            checks.map { check ->
                //  字形与行尾那句一起换成「坏」的那一档；调试档里正常的段也按异常画
                val bad = showAllBroken || !check.ok
                statusRow(
                    icon = if (bad) check.iconBroken else check.icon,
                    title = check.name,
                    ok = !bad,
                    detail = if (bad) check.detailBroken else check.detail,
                    //  「使用验证」那一档**始终不标红**：没走过是还没拿到证据，不是故障。
                    red = bad && !check.neverRed,
                    visible = visible,
                    rowKey = check.name,
                )
            }
        },
    )
}

/**
 * 「关于」卡里的机型那一行 —— 从「运行状态」卡挪过来，放在版本与签名之间。
 *
 *  它答的是「这台机器 + 这个包」里的前半句（版本、签名答后半句），所以与那一张卡同类。
 *  只读、**不给 `onClick`**（给了反而像能点）。
 *
 *  结论是一整句话（「这一版系统没实测过，请自行尝试」），仍走副标题：行尾那一格单行、
 *  放不下就把话说掉一半。
 */
@Composable
internal fun SettingsPage.deviceInfoRow(): CardRow {
    val adapted = deviceSupport == DeviceSupport.Support.ADAPTED
    return CardRow.Item(
        title = localDeviceTitle(),
        summary = adaptVerdict(deviceSupport),
        // 适配命中 / 没命中 —— 用「这台机器认不认得」两个字形
        icon = if (adapted) PageIcons.DeviceAdapted else PageIcons.DeviceUnsupported,
        //  颜色跟着「随机图标配色」走（不指定 = 由行自己算，见 `rememberRowIconColor`）：
        //  它只是「这一组验过没有」的提示、不是故障，所以**不染红**
        //  （AGENTS 一、13：清单不参与任何「看起来坏了」的表达）。
        //  原先没命中时染成 error 红，用户口径是「很多用户以为不能用」。
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
            I18n.get(Strings.ADAPTATION_ADAPTED)
        }

        DeviceSupport.Support.PENDING -> {
            I18n.get(Strings.ADAPTATION_PENDING)
        }

        DeviceSupport.Support.UNSUPPORTED -> {
            I18n.get(Strings.ADAPTATION_UNSUPPORTED)
        }
    }

/**
 * 运行状态里的一行 —— **字形说的是「哪一段 + 好不好」、颜色也一起说、行尾那句话说的是「怎么坏的」**。
 *
 *  早先这两件事都由字形承担（正常一颗 `home-check`、故障一颗 `home-cancel`），于是几段一起坏时
 *  排下来是一整列一模一样的叉；后来改成「环节一颗字形 + 颜色分好坏」，再后来每段自己有了
 *  两颗字形（`LinkCheck.icon` / `iconBroken`，如 `server` / `server-off`）——
 *  字形与颜色一起说结论，行尾那句在异常时换成「坏」的那一档（`LinkCheck.detailBroken`）。
 */
@Composable
private fun statusRow(
    icon: ImageVector,
    title: String,
    ok: Boolean,
    detail: String,
    /** 这一行标不标红（默认跟着 [ok]）；「使用验证」那一档 `ok = false` 也传 `false`。 */
    red: Boolean = !ok,
    visible: Boolean? = null,
    rowKey: Any? = null,
    onClick: (() -> Unit)? = null,
    /** 非 `null` = 这一行是手风琴表头：行尾在结论后面再跟一颗会转的箭头。 */
    expanded: Boolean? = null,
): CardRow.Item =
    CardRow.Item(
        title = title,
        icon = icon,
        iconTint = if (red) COUITheme.colorScheme.error else COUITheme.colorScheme.onSurfaceContainer,
        visible = visible,
        rowKey = rowKey,
        onClick = onClick,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RowEndSummary(detail, icon = null)
                if (expanded != null) AccordionChevron(expanded)
            }
        },
    )

/**
 * 「显示更详细的按键设置」那一行**先临时隐藏** —— 配置项（`detailedKeySettings`）与写回逻辑不动，
 * 只是这一行先不摆出来；恢复时改回 `true`。
 */
private const val SHOW_DETAILED_KEY_SETTINGS_ROW = false

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
    //  副标题与那层菜单写的是**同一份**「当前是哪份、叫什么」：都从 `list()` 取（它按 id 判身份）。
    //  **读盘放 IO、结果进 state**：`list()` 要逐份读并解码，在组合期做就是把整页第一帧压在
    //  文件系统上（这一页还是盖在宿主 Activity 上的）。
    //  「已满三份」不由这一页判：导入本来支持**覆盖**已有那份（见 `ImportTargetDialog` 的
    //  `ImportTarget.Into`），拿满额把整行灰掉会把这条合法路径一并挡死。满额只挡「新建」，
    //  那道闸在切换菜单里（`AboutMenus`），导入窗自己也会按 `full` 灰掉「新建」那一行。
    var currentDisplay by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(configState.config.value, ui.profiles.size) {
        currentDisplay = withContext(Dispatchers.IO) { ConfigProfiles.list().firstOrNull { it.isCurrent }?.displayName }
    }
    //  「切换配置」/「导出配置」两行点开的都是「盘上有哪几份」——`ConfigProfiles.list()` 要
    //  **逐份读文件再解码**。原先是在点击回调里同步调，点一下就在主线程上做（这一页还盖在
    //  宿主 Activity 上），用户看到的是「点了没反应、过一会儿菜单才弹」。
    //  读盘放 IO，拿到之后回主线程填状态再弹菜单 —— 与上面那条副标题同一口径。
    val scope = rememberCoroutineScope()
    val loadProfilesThen: ((Boolean) -> Unit) -> Unit = { open ->
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { ConfigProfiles.list() }
            ui.profiles = loaded
            open(true)
        }
    }
    CardList(
        rows =
            listOfNotNull(
                // 第 1 行：显示更详细的按键设置 —— 按键页收不收全部参数。
                //  **这一位还没有读取方**（先把配置键跑通）：拨完只有回执，按键页暂时不变。
                //  与「外观」那几行同一类（页面怎么画），所以不吃总闸。
                //  **先临时隐藏**（配置项与写回逻辑不动）；恢复时把 [SHOW_DETAILED_KEY_SETTINGS_ROW]
                //  改回 `true` 即可。
                if (SHOW_DETAILED_KEY_SETTINGS_ROW) {
                    CardRow.Switch(
                        title = I18n.get(Strings.MORE_DETAILED_KEY_SETTINGS_TITLE),
                        icon = PageIcons.DetailedKeySettings,
                        checked = configState.config.value.global.detailedKeySettings,
                        onCheckedChange = { setDetailedKeySettings(it) },
                    )
                } else {
                    null
                },
                // 「语言」：点开的是一层菜单，行尾给选择器字形（不是右箭头）
                CardRow.Item(
                    title = I18n.get(Strings.MORE_LANGUAGE),
                    icon = PageIcons.Language,
                    trailing = { RowEndSummary(languageLabel(currentLang)) },
                    modifier = Modifier.preciseClickAnchor(ui.langClick),
                    onClick = {
                        ui.showLangMenu = true
                    },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.PROFILE_SWITCH_TITLE),
                    //  行尾写**当前配置**的名字 —— 与「语言」那一行写当前语言同一档
                    icon = PageIcons.Profiles,
                    trailing = { RowEndSummary(currentDisplay ?: currentProfileName()) },
                    modifier = Modifier.preciseClickAnchor(ui.profileClick),
                    onClick = {
                        //  列表是盘上扫出来的（每份要读文件取名字）⇒ 在 IO 上读，读到再弹菜单
                        loadProfilesThen { open -> ui.showProfileMenu = open }
                    },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.MORE_EXPORT_CONFIG),
                    icon = PageIcons.ConfigExport,
                    // 行尾选择器：点开的是一层「导出哪份」的菜单
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    modifier = Modifier.preciseClickAnchor(ui.exportClick),
                    onClick = {
                        //  同「切换配置」：盘上扫出来的列表，读盘放 IO
                        loadProfilesThen { open -> ui.showExportMenu = open }
                    },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.MORE_IMPORT_CONFIG),
                    icon = PageIcons.ConfigImport,
                    arrow = true,
                    onClick = {
                        hooks.onImportConfig?.invoke()
                    },
                ),
            ),
    )
}

/**
 * 当前配置在界面上的叫法（「切换配置」那一行行尾、两个重置确认窗的正文）。
 *
 *  **按身份判，不按「名字空不空」判**：名字空的普通配置（位次 ≥ 1，名字被抹过）不是默认那份，
 *  写成「默认配置」会让它与真默认撞名 —— 用户看到的正是「两份默认配置、我这份动不了」。
 */
internal fun SettingsPage.currentProfileName(): String {
    val current = configState.config.value
    return when {
        current.isDefaultProfile -> I18n.get(Strings.PROFILE_DEFAULT)
        current.profileName.isBlank() -> I18n.get(Strings.PROFILE_UNNAMED)
        else -> current.profileName
    }
}

/**
 * 「外观」那张卡 —— 模块页面**自己的样子与手感**：主题、使用 Monet 配色、强调色、随机图标配色、
 * 减少界面图标、界面缩放、界面振动反馈。这一卡里没有一项碰「按键怎么执行」，
 * 所以它们与总闸无关，主开关关着时照样能改。
 *
 *  行序：**先深浅、后强调色** —— 深浅定的是整页的明暗，强调色是在它上面挑一个点缀；
 *  反过来摆的话，用户会先去挑颜色，再发现整页明暗变了、那一套色在他刚看到的底色上并不好看。
 *
 *  两行都是同一种交互（点开一层菜单、行尾写当前那一档再跟一颗选择器），与上面「配置」卡里的
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
                // 第 1 行：主题 —— 只管深浅（跟随系统 / 浅色 / 深色），配色是下面那一行
                CardRow.Item(
                    title = I18n.get(Strings.MORE_THEME),
                    icon = PageIcons.Theme,
                    trailing = { RowEndSummary(themeTitle(currentTheme)) },
                    modifier = Modifier.preciseClickAnchor(ui.themeClick),
                    onClick = {
                        ui.showThemeMenu = true
                    },
                ),
                // 第 2 行：使用 Monet 配色 —— 整页那套色板换不换成系统按壁纸生成的那一套。
                //  紧挨着「主题」摆：这两行是同一件事的两半（深浅 + 配色），隔开就分家了。
                //  纯显示项：拨完整页当场重画（`PageContent` 那一层读它算色板），不必重进。
                CardRow.Switch(
                    title = I18n.get(Strings.MORE_MONET_COLORS),
                    icon = PageIcons.MonetColors,
                    checked = configState.config.value.global.monetColors,
                    onCheckedChange = { setMonetColors(it) },
                ),
                // 第 3 行：强调色 —— 行尾写的是**当前那一套的名字**，不是「页面强调色」这种说明句：
                //  这一行回答的是「现在是哪一套」，与「语言」那一行同一个口径。
                //  行首直接摆**当前那一套的色块**（与菜单里那五行同一颗字形、同一个种子色）：
                //  这一行回答的就是「现在是什么色」，画一颗调色板图标等于把答案藏起来。
                CardRow.Item(
                    title = I18n.get(Strings.MORE_PALETTE),
                    //  色块按「用不用 Monet 配色」选：开了是四格色阶，关着是**一个整块**
                    //  （见 `accentSwatch`）
                    icon = PageIcons.accentSwatch(configState.config.value.global.monetColors),
                    iconTint = currentPalette.seed(LocalContext.current),
                    trailing = { RowEndSummary(paletteLabel(currentPalette)) },
                    modifier = Modifier.preciseClickAnchor(ui.paletteClick),
                    onClick = {
                        ui.showPaletteMenu = true
                    },
                ),
                // 第 3 行：随机图标配色 —— 开的那一下生成一个种子并落盘，之后一直用这一份
                //  （关掉再打开才换一批，见 `setRandomIconTint`）。颜色本身是手挑的色相，
                //  不是随机 RGB（见 `IconTint`）。
                CardRow.Switch(
                    title = I18n.get(Strings.MORE_RANDOM_ICON_TINT),
                    icon = PageIcons.RandomIconTint,
                    checked = configState.config.value.global.iconTintSeed != 0,
                    onCheckedChange = { setRandomIconTint(it) },
                ),
                // 第 4 行：减少界面图标 —— 卡片行行首那些字形收起（应用图标与网格不收，见
                //  `LocalReduceRowIcons`）。纯显示项，拨完整页当场重画。
                CardRow.Switch(
                    title = I18n.get(Strings.MORE_REDUCE_ICONS),
                    icon = PageIcons.ReduceIcons,
                    checked = configState.config.value.global.reduceIcons,
                    onCheckedChange = { setReduceIcons(it) },
                ),
                // 第 5 行：界面缩放 —— 页面大小的**唯一**一档（不再跟随系统「显示大小」，
                //  见 `GlobalConfig.pageScale`）。整行另起一支（[PageScaleRow]）：这一卡已经四行了，
                //  再平铺下去就数不清行序。
                CardRow.Custom { position ->
                    PageScaleRow(position)
                },
                // 第 6 行：界面振动反馈 —— 点行 / 拨开关 / 拖滑杆那一下的触觉。
                //  **它在这一页、不在「按键设置」页**：那一页管的是「按键触发之后振不振」
                //  （`home.vibrate_title` 那一颗），这一颗管的是**模块页面自己**的手感，
                //  与上面那几行「页面长什么样、多大」同一类。
                //  与这一页其它几行一样**不吃总闸**：主开关关着时页面照样能点，照样该有手感。
                CardRow.Switch(
                    title = I18n.get(Strings.MORE_UI_VIBRATE_TITLE),
                    icon = PageIcons.Vibrate,
                    checked = configState.config.value.global.uiVibrate,
                    onCheckedChange = { setUiVibrate(it) },
                ),
                // 第 7 行：使用预测性返回手势 —— 返回时整页跟不跟手。
                //  与上面几行同一类（页面自己的手感），同样不吃总闸；拨完立刻重挂返回回调
                //  （见 `setPredictiveBack`），不需要退出重进。
                CardRow.Switch(
                    title = I18n.get(Strings.MORE_PREDICTIVE_BACK_TITLE),
                    icon = PageIcons.PredictiveBack,
                    checked = configState.config.value.global.predictiveBack,
                    onCheckedChange = { setPredictiveBack(it) },
                ),
            ),
    )
}

/** 「界面缩放」那一行 —— 从 [SettingsPage.AppearanceCard] 里分出来，让那一卡只留行序。 */
@Composable
private fun SettingsPage.PageScaleRow(position: CardListPosition) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.MORE_PAGE_SCALE),
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
            I18n.get(Strings.MORE_THEME),
            themeTitle(AppTheme.ofCode(code)),
        )
    }
}

/** 把强调色写进配置 —— 与 [setTheme] 同一档，回执「强调色 已切换到「蓝色」」。 */
internal fun SettingsPage.setPalette(id: String) {
    val cfg = configState.config.value
    if (cfg.global.palette == id) return
    writeAndNotice(cfg.copy(global = cfg.global.copy(palette = id)), "palette=$id") {
        switchToNotice(
            I18n.get(Strings.MORE_PALETTE),
            paletteLabel(PagePalette.ofId(id)),
        )
    }
}
