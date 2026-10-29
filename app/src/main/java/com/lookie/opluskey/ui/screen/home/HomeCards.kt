package com.lookie.opluskey.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.variables.openVariablesForManage
import com.lookie.opluskey.ui.setModuleEnabled
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Variable
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor

// 首页那两张卡片 —— 从 `HomeScreen.kt` 拆出来。
//
// `HomeScreen` 原来是一个 434 行的函数：状态、链路自检、提示条、三张卡、两个确认窗、
// 一个菜单全平铺在一起。按**一张卡**切开之后，页面只剩「铺顺序 + 那两条 effect」，
// 卡里的人只管自己那几行。

/**
 * 「按键绑定」那张卡 —— **只有一行**：模块总闸（`GlobalConfig.enabled`）。
 *
 *  「按键绑定」那个入口行**搬到了下面那张「按键」卡**（见 [HomeKeysCard]）：一颗键一行，
 * 点哪颗进哪颗 —— 留着「总入口」的话，它会先带你去侧键，再让你在绑定页的菜单里找别的键。
 *
 *  这一行**不吃整页总闸**：主开关不能被自己锁死（见下）。「通用设置」原先在这张卡的第二行，
 *  已挪到「其他」卡的第一行（用户口径：放到「更多选项」上面）。
 */
@Composable
internal fun SettingsPage.HomeBindingCard(ui: HomeUiState) {
    // 主开关写的是**模块总闸**（`GlobalConfig.enabled`），不是「侧键那颗键的开关」——
    //  两者拆开之后，「停用这颗键」只停那一颗（⋮ 菜单里），这个开关停的是整个模块。
    val moduleOn = configState.config.value.global.enabled
    CardList(
        rows =
            listOf(
                // 主开关是这张卡的第一行，**不能被自己锁死**：它一旦关掉，全页（含这一页其它控件、
                // 「按键绑定」子页、顶栏菜单三项）都禁用 —— 它要是跟着一起禁用，就再也开不回来了。
                //  所以它的 `enabled` 吃的是 [linkReady]（宿主侧在跑 **且** 注入侧报到了）**外加**
                // `!configReadOnly`，**不吃 `pageEnabled`**（后者含它自己）。
                //  这几个条件里都没有主开关的任何成分，所以「关掉它 ⇒ 它自己被禁用」这条死路不存在。
                //
                //  注意：`!configReadOnly` 这一条是被真机逼出来的：只读态下刚进页时链路还没报到
                //  （`linkReady=false`）⇒ 主开关是灰的；等注入侧回包一到、`linkReady` 变 true，
                //  它就**自己重新亮起来**了，用户当场拨了三下（每次都被 `write` 拒掉）。
                //  **「拨了没反应」比「一开始就灰着」更难读**，所以这一档必须一起禁。
                //
                //  它写的是 [GlobalConfig.enabled] —— 执行侧的总闸（注入侧 `ConfigChannel.enabledOf`
                //  与宿主侧各有一道 `if (!…enabled) return`），所以这一开关不只是界面置灰，是真的把功能关掉。
                CardRow.Switch(
                    title = I18n.get(Strings.SETTING_ENABLE_BINDING, "启用按键绑定"),
                    // 真假各一句，都说「现在是什么状态」（口径见 `SwitchRow` 的说明）
                    summaryOn = I18n.get(Strings.HOME_SWITCH_DESC_ON, "按键由模块接管，执行绑定动作"),
                    summaryOff = I18n.get(Strings.HOME_SWITCH_DESC_OFF, "按键交还系统，不执行任何绑定"),
                    // 拨到右的开关 = 「这一行是总开关」。
                    icon = PageIcons.MasterSwitch,
                    checked = moduleOn,
                    enabled = linkReady && !configState.readOnly,
                ) { on ->
                    // 打开之前先要一次确认：没确认过就只弹窗、**不写盘**。
                    //  顺序不能反过来 —— 先写后弹等于「先启用了再问你同不同意」。
                    //  关闭不需要确认，直接写。
                    if (on && !configState.config.value.disclaimerAccepted) {
                        XLog.w("免责声明：还没确认过，弹窗（总闸先不打开）")
                        ui.disclaimerPending = true
                    } else {
                        setModuleEnabled(on)
                    }
                },
                //  这张卡**只有主开关这一行**：接口选择 / 振动 / 执行提示都是**全局**项、与「这颗键绑了
                //  什么」无关，但它们的入口「通用设置」已挪到下面「其他」卡（在「更多选项」上面）。
                //  「响铃状态」那两档（`setRingerModeInternal`）已删，理由见「按键绑定」小标题上方那段注释。
            ),
    )
}

/**
 * 「其他」那张卡 —— 通用设置 / 更多选项 / 捐赠打赏。
 *
 *  **闸门不是一刀切**（用户口径「更多行不跟随总闸禁用」，而「通用设置」跟随）：
 *   · 「通用设置」是这一页里**唯一会改配置**的那一行（接口选择 / 振动 / 执行提示），它吃整页总闸
 *     （[pageEnabled]）—— 与「按键」卡里那几颗键同一档：别的都灰着、这一行还能点进去改，就是个漏洞；
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
                // 「通用设置」：原先在「按键绑定」卡第二行，用户口径「移到更多选项上面」。
                CardRow.Item(
                    title = I18n.get(Strings.GENERAL_TITLE, "通用设置"),
                    summary = I18n.get(Strings.HOME_GENERAL_DESC, "接口选择、振动与执行提示"),
                    icon = PageIcons.SettingsCog,
                    enabled = pageEnabled,
                    arrow = true,
                    onClick = { navTo(Screen.GENERAL) },
                ),
                // 「自定义命令」：紧贴在「通用设置」下面（用户口径）。它进的是命令库那一页 ——
                //  名称格、单条 / 多条两栏、「已保存的命令」都在那边。
                //  标题与那一页顶栏**同名**（与「通用设置」「更多选项」同一条口径：点的那一行
                //  与进去之后看到的标题是同一句话）。
                //  这一行会写配置（存命令），所以与「通用设置」同一档：吃整页总闸。
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
                CardRow.Item(
                    title = I18n.get(Strings.TOOL_VARIABLES, "自定义变量"),
                    summary = I18n.get(Strings.HOME_VARIABLES_DESC, "命令里能读、也能改的值"),
                    icon = TablerIcons.Outline.Variable,
                    enabled = pageEnabled,
                    arrow = true,
                    //  这一趟是**管数据**：点列表里的一颗会弹「编辑 / 删除 + 当场改值」那张窗
                    onClick = { openVariablesForManage() },
                ),
                CardRow.Item(
                    title = I18n.get(Strings.MORE_OPTIONS_TITLE, "更多选项"),
                    // 副标题只报这一页有什么，不交代机制（「一起改短」）
                    summary = I18n.get(Strings.HOME_MORE_OPTIONS_SUMMARY, "运行状态与配置备份"),
                    icon = PageIcons.MoreOptions,
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
 */
private fun SettingsPage.openCommandsPage() {
    commandDraft.draft.reset()
    navTo(Screen.COMMANDS)
}
