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
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SectionTitleClick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.commonCommandIcon
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
 *  三种目标共用（按键绑定 / 组合键编辑 / 场景动作）：这一段的产物是一条动作绑定，
 *  三种目标都收得住 —— 场景块那一支写的是草稿（见 `BindingActions.commitBinding`）。
 *
 *  **画的是配置里那张清单**（`GlobalConfig.commonCommands`），不是模块的静态表：用户在
 * 「常用命令」页钉了哪几条、每条叫什么、哪些要出现在这里，全由那份配置说了算；
 *  清单只由用户自己收录（模块不预置任何条目，见 `GlobalConfig.commonCommands`）。
 *
 * @param target 这一页在给谁绑
 * @param enabled 整形页那道闸（主开关 + 链路 + 只读 + 这一份自己的启停）
 */
@Composable
internal fun SettingsPage.CommonCommandsGroup(
    target: BindingTarget,
    enabled: Boolean,
) {
    //  总开关（`CommonCommands.SHOW`）关着就整段不画。判断放在这一支里而不是调用点，
    //  这样调用点那边一行都不动 —— 关回去时也不必回去找哪一页少画了一段。
    if (!CommonCommands.SHOW) return
    //  显示与否再读配置，不看页面：关掉之后整段消失，别处（网格 / 自定义命令）一个字都不动
    if (!configState.config.value.global.showCommonCommands) return
    //  只画用户勾了「在按键绑定页显示」的那些。
    //
    //  一张清单都没钉住时**这一段仍在**，只是下面换成一行「去添加」（用户口径「没有常用命令时
    //  按键绑定里也显示」）：整段不画的话，这一页上就看不出还有「常用命令」这回事，
    //  用户也不会想到去别处先建一条再回来。这与下面「自定义命令」那一段同一个读法。
    val all = configState.config.value.global.commonCommands
    val entries = all.filter { it.showInBinding }
    //  这一档现在绑的是哪一条 —— 「勾」只服务这一段的**独立**身份：它不在下面那个网格里，
    //  网格不会替它点亮任何一格，不在这里标就完全看不出绑没绑上。
    val current = target.actionIn(configState.config.value)
    //  条数拼进标题（与内置动作那几组同一个写法）。
    //  **小标题点一下进「常用命令」页**（与下面「自定义命令」那一段同一个读法）：
    //  这一段回答「绑什么」，要改「这一段有哪几条」就得去那一页 —— 摆同一个入口省一次找路。
    //  条数按**这一段画出来的条数**走（`entries`）：标题说 0 就是这一格里没有东西，
    //  与下面那行「去添加」对得上；清单非空但一条都没勾在绑定页显示时，也一样是 0。
    SectionTitle(
        text = titleWithCount(I18n.get(Strings.BINDING_COMMON_COMMANDS, "常用命令"), entries.size),
        //  整页闸关着（主开关 / 链路 / 只读）时不接点击：跳过去也是一页改不动的清单
        click = if (enabled) SectionTitleClick.Jump { navTo(Screen.COMMON_COMMANDS) } else null,
    )
    //  一条都没有（或者一条都没勾「在按键绑定页显示」）时，这一段只剩一行「去添加」：
    //  它进的是「常用命令」页，挑一条收录就回来了。
    if (entries.isEmpty()) {
        AddCommonCommandRow(enabled = enabled, neverAdded = all.isEmpty(), onAdd = { navTo(Screen.COMMON_COMMANDS) })
        return
    }
    CardList(
        rows =
            entries.map { cmd ->
                val picked = current.token == cmd.token
                CardRow.Item(
                    title = CommonCommands.displayName(cmd),
                    //  模块自带那几条带一句手写的小字（「打开微信的扫码界面」）；
                    //  用户钉进来的动作没有，那一行就只留标题（小字说 token 只是噪音）。
                    summary = CommonCommands.entryOf(cmd.token)?.let { I18n.get(it.summaryKey, it.summaryFallback) },
                    icon = commonCommandIcon(cmd.token),
                    //  选中的那一条画一圈内边框 —— 与网格、挑应用那几页同一个件（`CardRow.Item.selected`）。
                    //  行尾那颗勾一起留着：圈说「这一行就是当前这一档」，勾说「选中的是这一条」。
                    selected = picked,
                    //  标题跟着上主题色（与「选择应用 / 选择界面」同一口径）：那一圈线说的是
                    //  「这一行是当前这一档」，标题说的是「念的是哪一条」—— 一眼扫下来先落到字上。
                    titleTint = if (picked) COUITheme.colorScheme.primary else null,
                    //  与网格格子同一把尺子（`enabled && linkReady`）：链路没就绪时绑上去也执行不了
                    enabled = enabled && linkReady,
                    trailing =
                        if (picked) {
                            { RowEndIcon(PageIcons.TileChecked, tint = COUITheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    onClick = {
                        //  原样落这一条的 token（`cmd:common.*` 或任意动作写法）：
                        //  执行侧不需要认识这张清单，绑定与普通动作是同一条路
                        XLog.w("常用命令：${target.what} → ${cmd.token}")
                        pickToken(cmd.token, target)
                    },
                )
            },
    )
}

/**
 * 这一段没有可挑的条目时那一行 —— 整行可点，进「常用命令」页去添加。
 *
 *  与「自定义命令」那一段的 `CreateCommandRow` 同一套写法（同一张卡、同一个行尾箭头）：
 *  两段都回答「这里现在是空的，下一步去哪」，摆两个不一样的样式反而让人以为是两件事。
 *
 *  **不画行首图标**：`CommandCode`（代码加号）是「这一段是什么」的标记，清单里那几条命令才带它；
 *  一行「去添加」的入口再画一遍，读起来像「已经有一条叫『添加命令』的命令」（用户口径
 *  「不要用这个行首图标」）。`CreateCommandRow` 那边同理，行首只有文字。
 *
 *  它**不受 `linkReady` 那一道闸限制**：链路没就绪时绑不了，但「去添加一条」与链路无关 ——
 *  这里只跟整页那道闸（主开关 / 只读 / 自己停用）走。
 *
 *  ## 小字不采用「点右下角加号」
 *
 *  本页右下角那颗加号形 FAB 进入的是「自定义命令」页，与常用命令无关（见 `BindingFab`：
 *  两页共用同一颗，`contentDescription` 即「自定义命令」）。沿用清单页那句「点右下角加号添加」，
 *  指向的是本页不存在的入口，用户据此操作会进入另一处。两种情形各述其步骤：
 *   · 清单为空 → 在「常用命令」页添加（`BINDING_COMMON_COMMANDS_ADD_DESC`）；
 *   · 有清单但均未勾选「在按键绑定页显示」→ 在那一页开启该开关（复用 `SHOW_IN_BINDING`，
 *     它本是菜单项名，此处读作「开启『显示在按键绑定页』」）。
 */
@Composable
private fun AddCommonCommandRow(
    enabled: Boolean,
    neverAdded: Boolean,
    onAdd: () -> Unit,
) {
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = I18n.get(Strings.COMMON_COMMANDS_ADD, "添加命令"),
                    summary =
                        if (neverAdded) {
                            I18n.get(Strings.BINDING_COMMON_COMMANDS_ADD_DESC, "在常用命令页添加")
                        } else {
                            //  有清单、只是没勾显示：下一步是拨开关，不是再添加一条
                            I18n.get(Strings.COMMON_COMMANDS_SHOW_IN_BINDING, "显示在按键绑定页")
                        },
                    //  行尾箭头：「这一行后面还有一页」（与 `CreateCommandRow` 同一条理由）
                    arrow = true,
                    enabled = enabled,
                    onClick = onAdd,
                ),
            ),
    )
}
