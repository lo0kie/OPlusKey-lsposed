package com.lookie.opluskey.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.variables.openVariablesForManage
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Variable
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor

// 首页那两张卡片 —— 从 `HomeScreen.kt` 拆出来。
//
// `HomeScreen` 原来是一个 434 行的函数：状态、链路自检、提示条、三张卡、两个确认窗、
// 一个菜单全平铺在一起。按**一张卡**切开之后，页面只剩「铺顺序 + 那两条 effect」，
// 卡里的人只管自己那几行。

/**
 * 「其他」那张卡 —— 自定义命令 / 自定义变量 / 更多选项 / 捐赠打赏。
 *
 *  「公共设置」（原「模块设置」）**不在这一卡里**：它跟「按键怎么工作」有关，已挪进上面的
 *  「按键操作」卡（见 `HomeKeyOpsCard`）。
 *
 *  **闸门不是一刀切**（用户口径「更多行不跟随总闸禁用」，而这几行改配置的跟随）：
 *   · 「自定义命令」「自定义变量」是这一页里**会改配置**的那两行（存命令 / 建改变量），它们吃整页
 *     总闸（[pageEnabled]）—— 与「按键」卡里那几颗键同一档：别的都灰着、这两行还能点进去改，就是个漏洞；
 *   · 「更多选项」是进那一页的**唯一入口**，而那一页里有「运行状态」—— 链路没齐 / 配置只读 /
 *     主开关关着时，用户正需要进去看断在哪一段（机型清单不参与任何禁用，见 AGENTS.md 一、13）；
 *   · 打赏与模块启没启用无关，整页禁用时它照样该能点。
 */
@Composable
internal fun SettingsPage.HomeOtherCard(
    pageEnabled: Boolean,
    ui: HomeUiState,
) {
    CardList(
        rows =
            listOf(
                // 「自定义命令」：这一卡的第一行。它进的是命令库那一页 ——
                //  名称格、单条 / 多条两栏、「已保存的命令」都在那边。
                //  标题与那一页顶栏**同名**（与「按键设置」「更多选项」同一条口径：点的那一行
                //  与进去之后看到的标题是同一句话）。
                //  这一行会写配置（存命令），所以吃整页总闸。
                CardRow.Item(
                    title = I18n.get(Strings.ACTION_CUSTOM_COMMAND, "自定义命令"),
                    summary = I18n.get(Strings.BINDING_CREATE_CUSTOM_COMMAND_DESC, "一条命令或多个动作"),
                    icon = PageIcons.CommandCode,
                    enabled = pageEnabled,
                    arrow = true,
                    onClick = { openCommandsPage() },
                ),
                // 「自定义变量」：与「自定义命令」并排 —— 两者是同一类东西（用户自己定义、之后
                //  在命令里用），摆在一起比塞进「更多选项」更好找。
                //  这一页只有变量的清单与新建，不吃整页总闸以外的东西：建 / 改变量要写盘，
                //  所以与上面两行同一档（`pageEnabled`）。
                //  **入口临时收起来**（[ActionCatalog.SHOW_VARIABLES]，用户口径「UI 入口先藏、功能不动」）：
                //  走行的 `visible`，连它下面那条细线一起藏，不留孤零零一条线。
                CardRow.Item(
                    title = I18n.get(Strings.TOOL_VARIABLES, "自定义变量"),
                    summary = I18n.get(Strings.HOME_VARIABLES_DESC, "命令里能读、也能改的值"),
                    icon = TablerIcons.Outline.Variable,
                    enabled = pageEnabled,
                    arrow = true,
                    visible = ActionCatalog.SHOW_VARIABLES,
                    //  这一趟是**管数据**：点列表里的一颗会弹「编辑 / 删除 + 当场改值」那张窗
                    onClick = { openVariablesForManage() },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.MORE_OPTIONS_TITLE, "更多选项"),
                    // 副标题只报这一页有什么，不交代机制（「一起改短」）
                    summary = I18n.get(Strings.HOME_MORE_OPTIONS_SUMMARY, "运行状态与配置备份"),
                    icon = PageIcons.MoreOptions,
                    //  行尾**不挂红点**：有新版这件事由首页顶上那块「模块状态」说（见 `HomeActivationCard`），
                    //  同一条信息在一页里出现两处，读起来像两件事
                    arrow = true,
                    onClick = { navTo(Screen.ABOUT) },
                ),
                // 「捐赠打赏」：点开一层菜单（三个去处），选中一个再弹**居中**的收款码窗口。
                //  与「更多选项」并排在这一张卡里 —— 两行都是「偶尔才点一次」的去处。
                //  行尾给选择器字形（读作「点了要选点什么」），不是进下一页的箭头，与「语言」那一行同一套。
                CardRow.Item(
                    title = I18n.get(Strings.HOME_DONATE, "捐赠打赏"),
                    summary = I18n.get(Strings.HOME_DONATE_SUMMARY, "您的慷慨将帮助此项目持续发展"),
                    icon = PageIcons.Donate,
                    // 菜单的落点由这一行记下的触点给（`MenuPopup` 的 `preciseClick`）。
                    //  少了它，锚点永远是空的 ⇒ 菜单退回库的默认位置，读起来就是「没有跟随手指」。
                    modifier = Modifier.preciseClickAnchor(ui.donateClick),
                    trailing = { RowEndIcon(PageIcons.Selector) },
                    onClick = {
                        ui.showDonateMenu = true
                    },
                ),
            ),
    )
}

/**
 * 进「自定义命令」页 —— 这是**新建**的入口，与「按键绑定」页那颗 FAB 同一条规矩：
 * 先把草稿清干净再进（`goBack` 只在草稿脏时才 reset，「点开一条看一眼、原样返回」那种
 * 不脏的退页不会清 `editingName`，留着的话下次进来表单还挂着「修改」）。编辑已有命令那条路
 * 走的是「已保存的命令」卡片，不经过这里。
 *
 *  清的是**当前这一页**（`draft.kind`）：两页是两个独立会话，另一页的草稿不动 ——
 *  整页归零只在出栈时发生（`CommandDraftState.discard`）。
 */
private fun SettingsPage.openCommandsPage() {
    commandDraft.draft.reset()
    //  首页这个入口落**「命令列表」**那一页（用户口径）：从首页来多半是「看看都有哪些命令 /
    //  改一条已经存在的」，而不是「现在就要新建一条」—— 新建在那一页右下角那颗 FAB 上，
    //  点一下就进表单。与「按键绑定」页那条「已保存的命令」入口同一个落点（见 `SavedCommandsTitle`）。
    commandDraft.openListPage = true
    navTo(Screen.COMMANDS)
}
