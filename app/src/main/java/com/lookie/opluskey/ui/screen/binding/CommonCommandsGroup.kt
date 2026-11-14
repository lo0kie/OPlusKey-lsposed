package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.present.CommonCommands
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.tickNow
import com.lookie.opluskey.ui.model.icon.commonCommandIcon
import com.lookie.opluskey.ui.model.page.GRID_GAP
import com.lookie.opluskey.ui.model.page.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.page.gridColumnCount
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.SectionTitleClick
import com.lookie.opluskey.ui.row.titleWithCount
import com.lookie.opluskey.ui.wire.linkReady
import com.lookie.opluskey.ui.write.BindingTarget

// 「常用命令」那一段 —— 模块维护的那几条命令（微信扫一扫这类）的一键绑定入口，只出现在绑定页上。
//
//  它属于**绑定页**而不是命令库页：这里回答的是「这一档手势绑什么」，用户在这一页就该能
//  直接拿到一条马上能用的命令，不必先回「自定义命令」页手工建一条再回来挑。
//
//  与「自定义命令」那一片是**两套东西**：这一段由模块维护（表在 `core/present/CommonCommands`），
//  不写进用户的命令库文件，命令页里也一条都不出现 —— 所以它有自己的一段标题、自己的行。

/**
 * 「常用命令」那一段（[CommonCommands.SHOW] 关着时整段不画）。
 *
 *  **每条自己决定露不露**（`CommonCommand.showInBinding`，在「常用命令」页的行菜单里切）：
 *  清单里有几条就列几条的前提是那条没关 —— 空清单 / 一条都没勾时只剩「去添加」一行。
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
    //  两档总闸：模块自带的 `CommonCommands.SHOW`（编译期常量），以及用户在「按键设置」里
    //  拨的 `GlobalConfig.showCommonCommands`（纯界面项，只落盘、不进广播）—— 任一关着就整段不画。
    //  判断放在这一支里而不是调用点，这样调用点那边一行都不动 —— 关回去时也不必回去找哪一页少画了一段。
    val global = configState.config.value.global
    if (!CommonCommands.SHOW || !global.showCommonCommands) return
    //  **只画这一条勾了「在按键绑定页显示」的那些**（每条各有自己的开关，见「常用命令」页的行菜单）。
    //  没有「全显示 / 全隐藏」的总闸：常用命令只是快捷方式，哪几条值得常按由用户自己挑。
    //
    //  一张清单都没钉住、或者一条都没勾显示时，这一段**仍在**，只是下面换成一行「去添加」——
    //  整段不画的话，这一页上就看不出还有「常用命令」这回事，用户也不会想到去别处先建一条再回来。
    //  这与下面「自定义命令」那一段同一个读法。
    val all = global.commonCommands
    val entries = all.filter { it.showInBinding }
    //  这一档现在绑的是哪一条 —— 「勾」只服务这一段的**独立**身份：它不在下面那个网格里，
    //  网格不会替它点亮任何一格，不在这里标就完全看不出绑没绑上。
    val current = target.actionIn(configState.config.value)
    //  条数拼进标题（与内置动作那几组同一个写法）。
    //  **小标题点一下进「常用命令」页**（与下面「自定义命令」那一段同一个读法）：
    //  这一段回答「绑什么」，要改「这一段有哪几条」就得去那一页 —— 摆同一个入口省一次找路。
    //  条数按**这一段画出来的条数**走（`entries`）：标题说 0 就是这一格里没有东西，
    //  与下面那行「去添加」对得上。
    SectionTitle(
        text = titleWithCount(I18n.get(Strings.BINDING_COMMON_COMMANDS, "常用命令"), entries.size),
        //  整页闸关着（主开关 / 链路 / 只读）时不接点击：跳过去也是一页改不动的清单
        click = if (enabled) SectionTitleClick.Jump { navTo(Screen.COMMON_COMMANDS) } else null,
    )
    //  一条都没有（清单空，或者一条都没勾「在按键绑定页显示」）时，这一段只剩一行「去添加」：
    //  它进的是「常用命令」页，在那里收录一条、顺手打开它的开关就回来了。
    if (entries.isEmpty()) {
        AddCommonCommandRow(enabled = enabled, onAdd = { navTo(Screen.COMMON_COMMANDS) })
        return
    }
    //  **网格**（1~3 列，列数按可用宽度算 —— 与下面内置动作 / 自定义命令那两段同一个量具）：
    //  这一段原先是一列纵向卡片行，比网格矮一截却占满整行，一页里两种「同样的东西」两种排法。
    BoxWithConstraints(Modifier.padding(horizontal = PAGE_MARGIN)) {
        val columns = gridColumnCount(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP)) {
            for (rowEntries in entries.chunked(columns)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                ) {
                    rowEntries.forEach { cmd ->
                        CommonCommandTile(
                            cmd = cmd,
                            bound = current.token == cmd.token,
                            //  与网格格子同一把尺子（`enabled && linkReady`）：链路没就绪时绑上去也执行不了
                            enabled = enabled && linkReady,
                            modifier = Modifier.weight(1f),
                            target = target,
                        )
                    }
                    // 最后一行不满时补空位：不补的话剩下那格会被 weight 撑到整行宽
                    repeat(columns - rowEntries.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 常用命令的一格 —— 与网格（[ActionTile]）是**同一份实现**，只把行尾换成那枚「就是它」的圆点。
 *
 *  它给不出 `ActionCatalog.Spec`（常用命令是模块自带的一份清单，不在 `ActionCatalog.ALL` 里），
 *  所以走 `spec = null` + `title` + `trailingDot`；外形、图标色、标题与小字的排版、
 *  行尾那颗的尺寸与间距全在 [ActionTile] 里，这里只声明「行尾画圆点」。
 *
 * @param bound 这一档当前绑的就是它 —— 行尾那枚圆点只在这是 `true` 时出现
 */
@Composable
private fun SettingsPage.CommonCommandTile(
    cmd: CommonCommand,
    bound: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    target: BindingTarget,
) {
    val name = CommonCommands.displayName(cmd)
    //  **直接复用网格那套格子**（[ActionTile]）：外形、图标色、标题与小字、行尾槽位都是同一份实现 ——
    //  这里只多说一句「行尾画那枚圆点」。
    //
    //  「常用命令」的条目是模块自带清单，**不在 `ActionCatalog.ALL` 里**（给不出 `Spec`），
    //  所以走 `spec = null` + `title` + `trailingDot`：格子外壳仍是同一份，不再自己拼一套。
    ActionTile(
        spec = null,
        title = name,
        //  与「常用命令」页那一行同一个答案：换过图标就画换的那颗
        icon = commonCommandIcon(cmd),
        selected = bound,
        focused = false,
        focusRequester = remember { FocusRequester() },
        modifier = modifier,
        enabled = enabled,
        trailingDot = true,
    ) { onPickCommon(cmd, name, bound, target) }
}

/**
 * 点中一格 —— 绑定 + 一句回执。
 *
 *  **已经绑的就是它时补一句**：那条路在 `writeBinding` 里被「值没变，不重复写」直接放走了、
 *  一句话都不出。这一格现在只有一枚圆点做标记，点了不出声就是「点不动」。
 */
private fun SettingsPage.onPickCommon(
    cmd: CommonCommand,
    name: String,
    bound: Boolean,
    target: BindingTarget,
) {
    //  **这一格是网格里的格子、不是 `CardRow`**，所以 `CardRow` 那条统一的振动到不了它 ——
    //  与网格里其它格子（`ActionTile` / 自定义命令那一格）一样，自己叫一声，
    //  走页面那条通用通道（`tickNow`），判据与列表行完全同一份。
    tickNow()
    //  原样落这一条的 token（`cmd:common.*` 或任意动作写法）：
    //  执行侧不需要认识这张清单，绑定与普通动作是同一条路
    XLog.w("常用命令：${target.what} → ${cmd.token}")
    val ok = pickToken(cmd.token, target)
    if (ok && bound) hint(I18n.format(Strings.TOAST_BOUND, name), slot = HINT_SLOT_BINDING)
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
 *  指向的是本页不存在的入口，用户据此操作会进入另一处 —— 所以这里说的是**它自己**的下一步：
 *  在「常用命令」页添加（`BINDING_COMMON_COMMANDS_ADD_DESC`）。
 *
 *  **只剩这一种情形**：原先还有「有清单但均未勾选『在按键绑定页显示』」那一支，而那一项
 *  已经改成固定（用户口径），清单非空就一定有格子，这个函数只服务「清单为空」。
 */
@Composable
private fun AddCommonCommandRow(
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = I18n.get(Strings.COMMON_COMMANDS_ADD, "添加命令"),
                    summary = I18n.get(Strings.BINDING_COMMON_COMMANDS_ADD_DESC, "在常用命令页添加"),
                    //  行尾箭头：「这一行后面还有一页」（与 `CreateCommandRow` 同一条理由）
                    arrow = true,
                    enabled = enabled,
                    onClick = onAdd,
                ),
            ),
    )
}
