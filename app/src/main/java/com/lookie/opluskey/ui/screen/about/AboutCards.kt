package com.lookie.opluskey.ui.screen.about

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkChecks
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_ACCEL
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.MORE_CONFIG_ROWS
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER_MS
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT_MS
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.state.LinkCheck
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
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
        //  只传个计数的话它渲染不出那几行，半场会瞬间塌成 0 高、过渡等于没有。
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
                    LinkStatusRow(row, i, visible.size)
                }
            }
        }
    }
}

/** 运行状态里的一行：字形颜色按「正常 / 故障 / 不参与判定」三档走，带过渡。 */
@Composable
private fun LinkStatusRow(
    row: LinkCheck,
    index: Int,
    count: Int,
) {
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
        cardListPosition = cardListPositionOf(index, count),
    )
}

/**
 * 「配置」卡里「语言」那一行 —— 点开的是一层菜单，行尾给选择器字形（不是右箭头）。
 */
@Composable
private fun SettingsPage.LanguageRow(
    currentLang: AppLanguage,
    ui: AboutUiState,
    tick: () -> Unit,
) {
    BasicComponent(
        title = I18n.get(Strings.MORE_LANGUAGE, "语言"),
        summary = languageLabel(currentLang),
        onClick = {
            tick()
            ui.showLangMenu = true
        },
        startAction = { Icon(PageIcons.Language, null) },
        // 行尾给选择器字形（不是右箭头）：这一行点开的是**一层菜单**，见 [RowEndIcon]。
        endActions = { RowEndIcon(PageIcons.Selector) },
        cardListPosition = cardListPositionOf(0, MORE_CONFIG_ROWS),
        modifier = Modifier.preciseClickAnchor(ui.langClick),
    )
}

/**
 * 「配置」卡（语言 / 导出 / 导入）。
 *
 *  导出走系统的「保存到」、导入走系统的「打开」，两边都由 hook 侧实现
 * （`SettingsPageHooks`：SAF + `Activity#onActivityResult`），这里只负责派发。
 *  两张卡的行数各算各的（见 [MORE_CONFIG_ROWS]），不能合成一个数。
 */
@Composable
internal fun SettingsPage.ConfigCard(
    currentLang: AppLanguage,
    ui: AboutUiState,
) {
    val tick = rememberTick()
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        LanguageRow(currentLang, ui, tick)
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        BasicComponent(
            title = I18n.get(Strings.MORE_EXPORT_CONFIG, "导出配置"),
            // 副标题只报「做什么」的一句话，不解释机制 —— 用户界面不写
            // 「把配置与自定义命令存成一份文件」那种交代细节的写法
            summary = I18n.get(Strings.MORE_EXPORT_CONFIG_SUMMARY, "保存到本地文件"),
            onClick = {
                tick()
                hooks.onExportConfig?.invoke()
            },
            startAction = { Icon(PageIcons.ConfigExport, null) },
            // 行尾箭头：这一行点了有反应（开系统的「保存到」）。本页的口径见 [RowEndIcon]。
            endActions = { RowEndIcon() },
            cardListPosition = cardListPositionOf(1, MORE_CONFIG_ROWS),
        )
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        BasicComponent(
            title = I18n.get(Strings.MORE_IMPORT_CONFIG, "导入配置"),
            summary = I18n.get(Strings.MORE_IMPORT_CONFIG_SUMMARY, "从本地文件读取"),
            onClick = {
                tick()
                hooks.onImportConfig?.invoke()
            },
            startAction = { Icon(PageIcons.ConfigImport, null) },
            endActions = { RowEndIcon() },
            cardListPosition = cardListPositionOf(2, MORE_CONFIG_ROWS),
        )
    }
}
