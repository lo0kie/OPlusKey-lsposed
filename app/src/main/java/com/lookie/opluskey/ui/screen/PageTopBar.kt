package com.lookie.opluskey.ui.screen

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.PageHeader
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appVersionLabel
import com.lookie.opluskey.ui.model.ABOUT_TITLE
import com.lookie.opluskey.ui.model.APP_PICKER_TITLE
import com.lookie.opluskey.ui.model.BINDING_TITLE
import com.lookie.opluskey.ui.model.COMMANDS_TITLE
import com.lookie.opluskey.ui.model.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.apppicker.AppPickerMenu
import com.lookie.opluskey.ui.screen.apppicker.AppSearchBand
import com.lookie.opluskey.ui.screen.binding.BindingTabBand
import com.lookie.opluskey.ui.screen.command.CommandKindBand
import com.lookie.opluskey.ui.screen.home.ResetMenu
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

/**
 * 顶栏 —— 标题 / 副标题 / 右上角动作 / 底下那条固定带，全部按页分派。
 *
 *  顶栏本身（[PageHeader]）不认识页面，只留几个槽位；「这一页该显示什么」全在这里。
 */
@Composable
internal fun SettingsPage.PageTopBar(
    s: Screen,
    scrollBehavior: ScrollBehavior,
    hazeState: HazeState,
    pagerState: PagerState,
    commandPager: PagerState,
) {
    PageHeader(
        title = pageTitle(s),
        // 大标题**下面**那行（库原本的底线副标题槽位，自己占一行）：首页 = 模块版本。
        // 与「更多选项」页的「版本」行**同源但不同串**：那边总是带构建时刻，这边按构建类型决定
        // （见 appVersionLabel / appVersionText）。
        largeTitleSubtitle = if (s == Screen.SHORTCUTS) remember { appVersionLabel() } else "",
        // 大标题**右边**那行小标题（补丁新增的槽位，折叠后保留），内容见 [pageSubtitle]。
        subtitle = pageSubtitle(s),
        // 「选择应用」页开着小窗打开时，那行小字后面补一枚「小窗起」—— 与网格里「打开应用」
        // 那格同一口径（见 `BindingScreen` 的 `appSmallWindow`）：那一行字只说明「选了哪个
        // 应用」，看不出这一趟是要普通打开还是小窗起。
        trailingSubtitleIcon =
            if (s == Screen.APP_PICKER && picker.smallWindow.value) PageIcons.TileSmallWindow else null,
        // 右上角那几颗动作键按页分派：**只有首页与「选择应用」页有**
        // （前者是打开默认界面 / 清空配置，后者是「显示系统应用」那三项）。
        // 顶栏只留一个 `actions` 槽位、不认识页面（见 [PageHeader]），
        // 两份菜单各自跟着自己那一屏走（[ResetMenu] / [AppPickerMenu]）。
        //
        //  「按键绑定」页那一套「新增命令」菜单晚些时候**撤掉了**，不再有 ⋮。
        // 进「自定义命令」页的唯一入口现在是那一页右下角的那颗 FAB（见 [BindingScreen]）。
        actions = { PageActions(s) },
        scrollBehavior = scrollBehavior,
        state = hazeState,
        // 顶栏底下那条**固定带**（三条带子各归各的页），见 [PageBottomBand]。
        bottomContent = { PageBottomBand(s, pagerState, commandPager) },
    )
}

/** 顶栏标题按页分派。 */
private fun SettingsPage.pageTitle(s: Screen): String =
    when (s) {
        // 首页标题平时是**宿主 Activity 自己的标题**（hook 侧读来的，见 SettingsPageHooks）；
        // 字典里有这一档时按语言来，没有才用宿主那串。
        Screen.SHORTCUTS -> I18n.get(Strings.HOME_TITLE, hooks.homeTitle)

        Screen.BINDING -> BINDING_TITLE

        Screen.COMMANDS -> COMMANDS_TITLE

        Screen.APP_PICKER -> APP_PICKER_TITLE

        Screen.ABOUT -> ABOUT_TITLE

        Screen.DONATE_LIST -> DONATE_LIST_TITLE
    }

/**
 * 顶栏右侧那行小标题按页分派：
 * 「自定义命令」= 已保存的命令条数（[ConfigState.commands] 变了跟着重组）；
 * 「选择应用」= 当前已选的应用名（两条入口见 [appPickerSelectedPkg]，查不到清单就退回包名）。
 */
private fun SettingsPage.pageSubtitle(s: Screen): String =
    when (s) {
        // 一条都没保存时不报数（「已保存0条命令」读起来像统计，也不成句）——
        // 直接说「没有保存的命令」，与页内那块空态同一句话。
        Screen.COMMANDS -> {
            if (configState.commands.value.isEmpty()) {
                I18n.get(Strings.COMMAND_NO_SAVED_COMMANDS, "没有保存的命令")
            } else {
                I18n.format(Strings.COMMAND_SAVED_COUNT, configState.commands.value.size)
            }
        }

        Screen.APP_PICKER -> {
            val pkg = appPickerSelectedPkg()
            if (pkg == null) {
                // 没选过就不摆一行「已选择: 无」—— 那种写法像是有个叫「无」的应用
                I18n.get(Strings.APP_PICKER_UNSELECTED, "还未选择应用")
            } else {
                val label =
                    picker.cachedApps.value
                        ?.firstOrNull { it.packageName == pkg }
                        ?.label ?: pkg
                I18n.format(Strings.APP_PICKER_SELECTED_FORMAT, label)
            }
        }

        else -> {
            ""
        }
    }

/** 顶栏右上角那几颗动作键 —— 只有首页与「选择应用」页有。 */
@Composable
private fun SettingsPage.PageActions(s: Screen) {
    when (s) {
        Screen.SHORTCUTS -> ResetMenu()
        Screen.APP_PICKER -> AppPickerMenu()
        else -> Unit
    }
}

/**
 * 顶栏底下那条**固定带**（三条带子各归各的页）。
 *
 *  不能各页各叠一层浮层（自带取样层 / 模糊 / 半透明底 / 内容里等高占位），统一搬进顶栏这个
 *  `bottomContent` 槽位：位置、底色、模糊、下沿那条细线都由顶栏那一份负责，见 [PageHeader]
 *  与三个带子自己的 KDoc。
 *  带子跨度与内容高度都各自报给页面级状态（[bindingBandHeight] 那三个），页内那个占位
 *  Spacer 读 —— 带子与内容分处两棵树，只有这一条路能对齐。
 */
@Composable
private fun SettingsPage.PageBottomBand(
    s: Screen,
    pagerState: PagerState,
    commandPager: PagerState,
) {
    when (s) {
        Screen.BINDING -> {
            BindingTabBand(
                pagerState,
                // 首页那个主开关（[KeyConfig.enabled]）—— 关掉时带子灰掉且吃掉输入（与那颗 FAB 同款）
                enabled = plusKeyEnabled(),
            )
        }

        Screen.COMMANDS -> {
            CommandKindBand(commandPager)
        }

        Screen.APP_PICKER -> {
            AppSearchBand()
        }

        // 首页 / 「关于模块」/「捐赠列表」都没有固定带（后两页都是「短内容」页，见 [pageScrollBehavior]）
        Screen.SHORTCUTS, Screen.ABOUT, Screen.DONATE_LIST -> {
            // 这几页不摆固定带
        }
    }
}
