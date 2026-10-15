package com.lookie.opluskey.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.DeviceSupport
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.MenuEntry
import com.lookie.opluskey.ui.component.MenuPopup
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.model.ABOUT_ROWS
import com.lookie.opluskey.ui.model.AUTHOR
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_ACCEL
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.ISSUE_URL
import com.lookie.opluskey.ui.model.MORE_CONFIG_ROWS
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PROFILE_URL
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.REPO_URL
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER_MS
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT_MS
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.state.LinkCheck
import com.lookie.opluskey.ui.state.deviceModel
import com.lookie.opluskey.ui.state.deviceSupport
import com.lookie.opluskey.ui.state.linkChecks
import com.lookie.opluskey.ui.state.rememberTick
import com.lookie.opluskey.ui.state.setLanguage
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** 「更多选项」页 —— 从首页「其他 → 更多选项」那一行进来（见 [Screen.ABOUT]）。 */
@Composable
internal fun SettingsPage.AboutScreen(scrollBehavior: ScrollBehavior) {
    val tick = rememberTick()
    // 取版本号要过 PackageManager（见 [moduleVersionName]），本页每次重组都算一遍纯属白花。
    // 「运行状态」那张卡是 `@Composable` 读状态、会频繁重组，而版本号在整个进程生命里不变。
    //  这一页走 [appVersionText] —— **总是带构建时刻**（含日期）：这一页本来就回答「我这个包是
    //  哪一个」，「什么时候出的」正是它要说的事。首页那条副标题走 [appVersionLabel]，
    //  仍按构建类型决定带不带。
    val version = remember { appVersionText() }
    Column(
        Modifier
            .fillMaxSize()
            // 大标题随滚动折叠：`nestedScroll` 必须在 `verticalScroll` 之前
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical()
            .verticalScroll(rememberScrollState())
            .padding(bottom = PAGE_BOTTOM_PADDING),
    ) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）。这一页首行是 `SmallTitle`（下面「配置」那一个），
        // 它自带 8dp 上内边距，**不另加** [PAGE_TOP_PADDING] —— 那一档是给首行是卡片的「选择应用」的。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        val langClick = remember { PreciseClickState() }
        var showLangMenu by remember { mutableStateOf(false) }
        val currentLang = AppLanguage.fromCode(config.value.global.language)

        // 「运行状态」（内部叫链路自检）—— 模块要正常工作，得**几段**都通（宿主侧的服务、
        // 注入侧的接管点、配置送达、以及宿主真读到配置），外加一段只能靠实践拿到的证据。
        // 只看「宿主报活了没有」一个布尔的话，答不了「注入侧压根没装」这种
        // （那种情况下宿主照样报活、页面全绿）。
        //  只读信息，没有可点的行；明细**只列不通的那几段**（正常的不占地方，见下面 rows 那一段），
        //  一行明细都没有时由「整体状态」那一行报「全部正常」。
        //  注意：数据是**读缓存**、不是「进页时现取」：不能写
        //  「进页时现取一次（见 [linkChecks]），不轮询」—— 与实际相反。`linkChecks()` 读的是
        //  `hostServiceUp` / `injectStatus` 这些**已经缓存在页面状态里**的结论，本页**不发任何问句**；
        //  主动报活只有首页那条自检（`HomeScreen`）会做，注入侧也只应答、不主动报
        //  （`inject/KeyHooks` 只在收到 `ACTION_INJECT_QUERY` 时回包）。
        //  所以进本页看到的是「最近一次自检的结论」，想刷新就回首页走一遍。
        //  注意：**这一页是「细节」的落点，措辞仍是给用户看的**：
        // 段名与取值都不出现内部结构（不写「注入侧 / 同步 / 读取 / 就绪」），
        // 想知道「怎么坏的」去看日志（`XLog`）。
        Spacer(Modifier.height(GRID_ROW_GAP))
        SmallTitle(I18n.get(Strings.MORE_SECTION_STATUS, "运行状态"))
        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            val checks = linkChecks()
            val broken = checks.firstOrNull { !it.ok && it.blocking }
            // 明细**只列真故障**：正常那几段不占地方 —— 这一段本来就只在出问题时才需要看。
            //  「主开关已关闭」与「尚未使用」两种不 ok 不算故障（[LinkCheck.blocking] 那句），
            //  留下它们会让这一页平时也恒挂两行，还与「整体状态」那句结论打架。
            //  汇总行常留：它负责回答「到底有没有事」，一行明细都没用时靠它报「全部正常」。
            val rows =
                buildList {
                    // 第 0 行是**整体结论**，缺哪一段直接点名（后面列的明细只会是故障那几段）
                    add(
                        LinkCheck(
                            name = I18n.get(Strings.LINK_CHECK_OVERALL_STATUS, "整体状态"),
                            ok = broken == null,
                            detail =
                                if (broken == null) {
                                    I18n.get(Strings.LINK_CHECK_ALL_NORMAL, "全部正常")
                                } else {
                                    I18n.format(Strings.LINK_CHECK_BROKEN_FORMAT, broken.name)
                                },
                        ),
                    )
                    addAll(checks.filter { !it.ok && it.blocking })
                }
            // 行的增删（正常 ⟷ 出问题）走交叉过渡：容器高度由 [SizeTransform] 从旧值补到新值，
            //  观感就是故障行展开出来、恢复时收回去。
            //  注意：目标态传的是**行列表本身**，不是行数：`AnimatedContent` 把**旧值**交给退场那一侧，
            //  只传个计数的话它渲染不出那几行，半场会瞬间塌成 0 高、过渡等于没有
            // （与 `CommandScreen` 里「已保存的命令」那处同一条）。
            //  `contentKey` 只取「段名序列」⇒ 取值变化（「正常」→「未运行」）原地更新，
            //  不把整块重播一遍。
            AnimatedContent(
                targetState = rows,
                contentKey = { it.map(LinkCheck::name) },
                transitionSpec = {
                    // 用 `ContentTransform` 显式给尺寸动画，而不是 `using(SizeTransform(...))`：
                    // 这个 compose 版本里没有 `using` 那个中缀（`CommandScreen` 里实测 `Unresolved reference`）。
                    ContentTransform(
                        targetContentEnter =
                            fadeIn(
                                tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
                            ),
                        initialContentExit =
                            fadeOut(
                                tween(VISIBILITY_EXIT_MS, easing = EASING_EMPHASIZED_ACCEL),
                            ),
                        sizeTransform =
                            SizeTransform(
                                clip = true,
                                sizeAnimationSpec = { _, _ ->
                                    tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL)
                                },
                            ),
                    )
                },
                label = "LinkCheckRows",
            ) { visible ->
                Column {
                    visible.forEachIndexed { i, row ->
                        if (i > 0) {
                            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
                        }
                        val statusTint by animateColorAsState(
                            targetValue =
                                when {
                                    row.ok -> COUITheme.colorScheme.onSurfaceContainer
                                    row.blocking -> COUITheme.colorScheme.error
                                    else -> COUITheme.colorScheme.onSurfaceVariantSummary
                                },
                            animationSpec = COLOR_TRANSITION_SPEC,
                            label = "LinkStatusTint",
                        )
                        BasicComponent(
                            title = row.name,
                            summary = row.detail,
                            startAction = {
                                Icon(
                                    if (row.ok) PageIcons.LinkOk else PageIcons.LinkBroken,
                                    contentDescription = null,
                                    // 明细进到这里只剩故障那一档（红）；汇总行正常时走常规色。
                                    //  三档判断留着：过滤口径一改（比如把「尚未使用」也放进来）它还用得上。
                                    tint = statusTint,
                                )
                            },
                            cardListPosition = cardListPositionOf(i, visible.size),
                        )
                    }
                }
            }
        }

        // 「配置」卡（语言 / 导出 / 导入）：
        //  导出走系统的「保存到」、导入走系统的「打开」，两边都由 hook 侧实现
        // （`SettingsPageHooks`：SAF + `Activity#onActivityResult`），这里只负责派发。
        //  两张卡的行数各算各的（见 [MORE_CONFIG_ROWS]），不能合成一个数。
        SmallTitle(I18n.get(Strings.MORE_SECTION_CONFIG, "配置"))
        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            BasicComponent(
                title = I18n.get(Strings.MORE_LANGUAGE, "语言"),
                summary = languageLabel(currentLang),
                onClick = {
                    tick()
                    showLangMenu = true
                },
                startAction = { Icon(PageIcons.Language, null) },
                // 行尾给选择器字形（不是右箭头）：这一行点开的是**一层菜单**，见 [RowEndIcon]。
                endActions = { RowEndIcon(PageIcons.Selector) },
                cardListPosition = cardListPositionOf(0, MORE_CONFIG_ROWS),
                modifier = Modifier.preciseClickAnchor(langClick),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = I18n.get(Strings.MORE_EXPORT_CONFIG, "导出配置"),
                // 副标题只报「做什么」的一句话，不解释机制 —— 用户界面不写
                // 「把配置与自定义命令存成一份文件」那种交代细节的写法
                summary = I18n.get(Strings.MORE_EXPORT_CONFIG_SUMMARY, "保存到本地文件"),
                onClick = {
                    tick()
                    onExportConfig?.invoke()
                },
                startAction = { Icon(PageIcons.ConfigExport, null) },
                // 行尾箭头：这一行点了有反应（开系统的「保存到」）。本页的口径见 [RowChevron]。
                endActions = { RowEndIcon() },
                cardListPosition = cardListPositionOf(1, MORE_CONFIG_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = I18n.get(Strings.MORE_IMPORT_CONFIG, "导入配置"),
                summary = I18n.get(Strings.MORE_IMPORT_CONFIG_SUMMARY, "从本地文件读取"),
                onClick = {
                    tick()
                    onImportConfig?.invoke()
                },
                startAction = { Icon(PageIcons.ConfigImport, null) },
                endActions = { RowEndIcon() },
                cardListPosition = cardListPositionOf(2, MORE_CONFIG_ROWS),
            )
        }

        MenuPopup(
            show = showLangMenu,
            preciseClick = langClick,
            onDismiss = { showLangMenu = false },
            entries =
                buildList {
                    AppLanguage.entries.forEachIndexed { index, lang ->
                        // 相邻两行一条细线 —— 与 ⋮ 菜单 / 长按菜单 /「添加命令」同一件（[MenuEntry.RowLine]）
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
                                showLangMenu = false
                                if (lang != currentLang) {
                                    setLanguage(lang.code)
                                }
                            },
                        )
                    }
                },
        )

        // 「关于」（本机 / 版本 / 作者 / 反馈问题）—— 放在**最下面**：上面两张答的是
        //  「怎么用、现在什么状态」，这一张是「这台机器 + 这个包」的自述，看过一次就够。
        //  行数见 [ABOUT_ROWS]（四行固定）。本机这一行只读、不给 onClick（给了反而像能点）。
        Spacer(Modifier.height(GRID_ROW_GAP))
        SmallTitle(I18n.get(Strings.MORE_SECTION_ABOUT, "关于"))
        Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            val adapted = deviceSupport == DeviceSupport.Support.ADAPTED
            //  本机那一行（机型 + 是否适配）—— 与「版本」同属「这台机器 / 这个包」的自述，
            //  所以并进这张卡、摆在版本上面。
            BasicComponent(
                title = localDeviceTitle(),
                summary = adaptVerdict(deviceSupport),
                startAction = {
                    Icon(
                        // 适配命中 / 没命中 —— 用「这台机器认不认得」两个字形（见 [PageIcons.DeviceAdapted]）
                        if (adapted) PageIcons.DeviceAdapted else PageIcons.DeviceUnsupported,
                        contentDescription = null,
                        // 与「运行状态」同一套色：命中用常规色、没命中用 error
                        tint =
                            if (adapted) {
                                COUITheme.colorScheme.onSurfaceContainer
                            } else {
                                COUITheme.colorScheme.error
                            },
                    )
                },
                cardListPosition = cardListPositionOf(0, ABOUT_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = I18n.get(Strings.MORE_ABOUT_VERSION, "版本"),
                // 摘要 = 版号 + 构建类型 + 构建时刻（含日期），见 [appVersionText]
                summary = version,
                // 点一下开项目仓库（与「作者 / 反馈问题」一样是跳外部链接）
                onClick = {
                    tick()
                    openUrl(REPO_URL)
                },
                startAction = { Icon(PageIcons.Versions, null) },
                endActions = { RowEndIcon(PageIcons.Link) },
                cardListPosition = cardListPositionOf(1, ABOUT_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            BasicComponent(
                title = I18n.get(Strings.MORE_ABOUT_AUTHOR, "作者"),
                summary = AUTHOR,
                onClick = {
                    tick()
                    openUrl(PROFILE_URL)
                },
                startAction = { Icon(PageIcons.UserScreen, null) },
                endActions = { RowEndIcon(PageIcons.Link) },
                cardListPosition = cardListPositionOf(2, ABOUT_ROWS),
            )
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            //  摘要是一条 URL（长），所以不用库那个 `summary` 槽 —— 它那个 `Text` 不限行，
            //  URL 会把卡片折成两行顶高。改成自己画：单行 + 超出省略，见 [TitleSummary]。
            BasicComponent(
                onClick = {
                    tick()
                    openUrl(ISSUE_URL)
                },
                startAction = { Icon(PageIcons.Bug, null) },
                endActions = { RowEndIcon(PageIcons.Link) },
                cardListPosition = cardListPositionOf(3, ABOUT_ROWS),
            ) {
                TitleSummary(I18n.get(Strings.MORE_ABOUT_FEEDBACK, "反馈问题"), ISSUE_URL)
            }
        }
    }
}

/**
 * 「关于」卡里「反馈问题」那一行的标题与摘要 —— **单行 + 超出省略**。
 *
 *  为什么不用库的 `summary` 槽：那一槽的 `Text` **不限行**（`Component.kt` 里没给 `maxLines`），
 *  而这一行的摘要是一条 URL，长起来会折成两行、把卡片顶高。
 *  度量照库那一份抄：标题 `headline1` + Medium + `onBackground`、摘要 `body2` +
 *  `onSurfaceVariantSummary`、与标题隔 2dp（库那边是 private 的 `MarginBetweenLine`）。
 *
 *  两个 `Text` 直接摆进调用点的 `Column` —— composable 函数不引入布局节点，所以它们与库自己
 *  画的那两行是同一层。
 */
@Composable
private fun TitleSummary(
    title: String,
    summary: String,
) {
    Text(
        text = title,
        style = COUITheme.textStyles.headline1,
        fontWeight = FontWeight.Medium,
        color = COUITheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = summary,
        modifier = Modifier.padding(top = 2.dp),
        style = COUITheme.textStyles.body2,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 「更多选项」页里可点那几行的**行尾图标** —— 本页共用一份（规格只有这一处，免得各写各的走形）。
 *
 *  默认是那颗右箭头（[PageIcons.EnterArrow]），口径就是「带 `onClick` 的行都给」：本页这几行的
 *  下一站是**外部**的（开 URL、开系统的「保存到 / 打开」），不是页内的下一层 —— 与
 *  `EnterArrow` 那条「只给会进下一层的地方」分工不同，但同样需要一个「点了有反应」的指示。
 *  只读的行（适配清单、运行状态、版本那一行）一律不给。
 *
 *  两种例外（字形见 [icon]）：
 *   · 「语言」那一行给 [PageIcons.Selector]：它点开的是**一层菜单**，选择器字形比右箭头更贴
 *     「点了要选点什么」；
 *   · 「关于」卡那三行（作者 / 查看地址 / 反馈问题）给 [PageIcons.Link]：它们点了都是**跳出去**
 *     （开外部链接），链接字形比箭头具体。
 *
 *  尺寸与取色照「按键绑定」页那处（24dp、`onSurfaceVariantActions`）：行尾图标是同一个东西，
 *  几页摆在一起不该有两种大小、两种深浅。本页这几行都没有禁用态，所以只有一档。
 *
 * @param icon 行尾那颗字形
 */
@Composable
private fun RowEndIcon(icon: ImageVector = PageIcons.EnterArrow) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = COUITheme.colorScheme.onSurfaceVariantActions,
    )
}

/**
 * 「关于」卡里本机那一行的主标题 —— 机型号与代号都摆出来。
 *
 *  清单里那台机器用清单上那份名字（自带代号，「一加 15（PLK110）」）；不在清单里就只有代号，
 *  毕竟它的名字我们也不知道。代号都读不出来时给空串（标题不画）—— 读不出来是「没读到」、
 *  不是结论，副标题那句结论照常给。
 */
private fun localDeviceTitle(): String = DeviceSupport.adaptedNameOf(deviceModel).ifEmpty { deviceModel }

/**
 * 「适配清单」里「本机」那一行的结论 —— 判定三档各一句（见 `core/model/DeviceSupport`）。
 *
 * 只有「等待适配」这一档是「等作者」而不是「本机不行」（首页那条提示分两句，见 `ui/model/PageConst`），
 * 所以三句话不合并成两档。
 */
private fun adaptVerdict(support: DeviceSupport.Support): String =
    when (support) {
        DeviceSupport.Support.ADAPTED -> I18n.get(Strings.ADAPTATION_ADAPTED, "已适配")
        DeviceSupport.Support.PENDING -> I18n.get(Strings.ADAPTATION_PENDING, "等待适配")
        DeviceSupport.Support.UNSUPPORTED -> I18n.get(Strings.ADAPTATION_UNSUPPORTED, "不在适配清单")
    }
