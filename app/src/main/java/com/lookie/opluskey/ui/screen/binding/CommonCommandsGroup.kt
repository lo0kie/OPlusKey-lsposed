package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.present.CommonCommands
import com.lookie.opluskey.ui.BindingTarget
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.titleWithCount
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「常用命令」那一段 —— 模块维护的那几条命令（微信扫一扫这类）的一键绑定入口，只出现在绑定页上。
//
//  它属于**绑定页**而不是命令库页：这里回答的是「这一档手势绑什么」，用户在这一页就该能
//  直接拿到一条马上能用的命令，不必先回「自定义命令」页手工建一条再回来挑。
//
//  与「自定义命令」那一片是**两套东西**：这一段由模块维护（表在 `core/present/CommonCommands`），
//  不写进用户的命令库文件，命令页里也一条都不出现 —— 所以它有自己的一段标题、自己的行。

/**
 * 「常用命令」那一段（[CommonCommands.SHOW] 关着、或 `GlobalConfig.showCommonCommands` 关掉时整段不画）。
 *
 *  三种目标共用（按键绑定 / 组合键编辑 / 场景动作）：这一段的产物是一条 `cmd:<id>` 绑定，
 *  三种目标都收得住 —— 场景块那一支写的是草稿（见 `BindingActions.commitBinding`）。
 *
 * @param target 这一页在给谁绑
 * @param enabled 整形页那道闸（主开关 + 链路 + 只读 + 这一份自己的启停）
 */
@Composable
internal fun SettingsPage.CommonCommandsGroup(
    target: BindingTarget,
    enabled: Boolean,
) {
    //  这一档功能还没发布：整段先收起来（`CommonCommands.SHOW`）。判断放在这一支里而不是调用点，
    //  这样调用点那边一行都不动 —— 改回 `true` 时不必回去找哪一页少画了一段。
    if (!CommonCommands.SHOW) return
    //  显示与否再读配置，不看页面：关掉之后整段消失，别处（网格 / 自定义命令）一个字都不动
    if (!configState.config.value.global.showCommonCommands) return
    val entries = CommonCommands.ENTRIES
    if (entries.isEmpty()) return
    //  这一档现在绑的是哪一条 —— 「勾」只服务这一段的**独立**身份：它不在下面那个网格里，
    //  网格不会替它点亮任何一格，不在这里标就完全看不出绑没绑上。
    val current = target.actionIn(configState.config.value)
    //  条数拼进标题（与内置动作那几组同一个写法）
    SectionTitle(titleWithCount(I18n.get(Strings.BINDING_COMMON_COMMANDS, "常用命令"), entries.size))
    CardList(
        rows =
            entries.map { entry ->
                CardRow.Item(
                    title = I18n.get(entry.nameKey, entry.nameFallback),
                    summary = I18n.get(entry.summaryKey, entry.summaryFallback),
                    icon = PageIcons.BrandWechat,
                    //  与网格格子同一把尺子（`enabled && linkReady`）：链路没就绪时绑上去也执行不了
                    enabled = enabled && linkReady,
                    trailing =
                        if (current.token == entry.token) {
                            { RowEndIcon(PageIcons.TileChecked, tint = COUITheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    onClick = {
                        //  直接落 `cmd:<id>`：命令正文由模块维护，**不建进用户命令库**
                        //  （执行侧按名字查不到时会退到 `CommonCommands`，见 `CustomCommandRunner`）
                        XLog.w("常用命令：${target.what} → ${entry.token}")
                        pickToken(entry.token, target)
                    },
                )
            },
    )
}
