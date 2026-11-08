package com.lookie.opluskey.ui.screen.home

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons

// 首页的「按键操作」卡 —— 两行：进「按键功能」页 / 进「公共设置」页。
//
//  清单本身（有哪几颗键 / 配了哪几组）与「添加按键」都在「按键功能」页里，见 `KeysScreen`；
//  公共那几项（接口选择 / 振动 / 组合按键）在「公共设置」页里，见 `GeneralScreen`；
//  每颗键自己的参数在「按键设置」页，从「按键绑定」页顶栏那颗设置键进（见 `KeySettingsScreen`）。
//  这一卡只留两个入口，回答的是「跟按键有关的东西去哪配」。

/**
 * 「按键操作」卡 —— 两行：「按键功能」与「公共设置」。
 *
 *  · 「按键功能」不再叫「组合按键」、也不再按「有没有自定义键」置灰（用户口径）：
 *    它指向的是**整页按键功能**，不只是组合那一列 —— 一颗自定义键都没收时那一页照样有用
 *    （侧键在里面，添加按键也在里面），灰掉等于把入口关了。
 *  · 「公共设置」（原「模块设置」）**从「其他」卡挪进这一卡**（用户口径）：那一页改的都是
 *    「按键怎么工作」—— 走哪套接口、执行时振不振动、组合键怎么判，与这张卡是同一件事；
 *    摆在「其他」卡里反倒像「模块自己的设置」。整页总闸照旧吃（[pageEnabled]）。
 */
@Composable
internal fun SettingsPage.HomeKeyOpsCard(pageEnabled: Boolean) {
    CardList(rows = listOf(keysEntryRow(pageEnabled), settingsEntryRow(pageEnabled)))
}

/**
 * 「按键功能」那一行。
 *
 *  副标题只有一句「这一页里有什么」：原来还分一档「已添加 N 组」，那是**统计**而不是入口的
 *  说明（用户口径：去掉那个小标题）—— 配了几组进去就看得见，不必在首页报数。
 *
 *  抽成函数只为让 [HomeKeyOpsCard] 留在 detekt 的 `LongMethod` 阈值内。
 */
private fun SettingsPage.keysEntryRow(pageEnabled: Boolean): CardRow.Item =
    CardRow.Item(
        title = I18n.get(Strings.KEYS_ENTRY, "按键功能"),
        summary = I18n.get(Strings.KEYS_ENTRY_DESC, "按键列表与组合按键"),
        icon = PageIcons.MenuKeyFinger,
        enabled = pageEnabled,
        arrow = true,
        onClick = {
            XLog.w("首页：进「按键功能」页")
            navTo(Screen.KEYS)
        },
    )

/**
 * 「公共设置」那一行 —— 进 [Screen.GENERAL]（接口选择 / 按键行为 / 组合按键设置三节）。
 *
 *  **进的是公共项，不是某颗键的设置**：那颗在「按键绑定」页顶栏（`Screen.KEY_SETTINGS`）。
 *
 *  标题与那一页顶栏**同名**（共用 `Strings.GENERAL_TITLE`）：点的那一行与进去之后看到的标题
 *  是同一句话。这一行会改配置，所以与「按键功能」同一档闸（[pageEnabled]，含主开关自己）。
 *
 *  **这一行是公共设置的唯一入口**（「按键功能」页顶栏原先进这里的那颗齿轮已经拿掉了）。
 *  「按键绑定」/「组合键编辑」两页顶栏那颗设置键通向的是**另一页**（`Screen.KEY_SETTINGS`，
 *  这一颗键 / 这一组组合自己的覆盖），不是这里。
 */
private fun SettingsPage.settingsEntryRow(pageEnabled: Boolean): CardRow.Item =
    CardRow.Item(
        title = I18n.get(Strings.GENERAL_TITLE, "按键设置"),
        summary = I18n.get(Strings.HOME_GENERAL_DESC, "接口选择、振动与组合按键"),
        icon = PageIcons.SettingsCog,
        enabled = pageEnabled,
        arrow = true,
        onClick = {
            XLog.w("首页：进「公共设置」页")
            navTo(Screen.GENERAL)
        },
    )
