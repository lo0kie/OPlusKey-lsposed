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
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.appVersionLabel
import com.lookie.opluskey.ui.model.ABOUT_TITLE
import com.lookie.opluskey.ui.model.ACTIVITY_PICKER_TITLE
import com.lookie.opluskey.ui.model.APP_PICKER_TITLE
import com.lookie.opluskey.ui.model.BINDING_TITLE
import com.lookie.opluskey.ui.model.BRANCHES_TITLE
import com.lookie.opluskey.ui.model.COMMANDS_TITLE
import com.lookie.opluskey.ui.model.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.GENERAL_TITLE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.editingKeyName
import com.lookie.opluskey.ui.screen.apppicker.ActivitySearchBand
import com.lookie.opluskey.ui.screen.apppicker.AppPickerMenu
import com.lookie.opluskey.ui.screen.apppicker.AppSearchBand
import com.lookie.opluskey.ui.screen.binding.BindingKeyMenu
import com.lookie.opluskey.ui.screen.binding.BindingTabBand
import com.lookie.opluskey.ui.screen.command.CommandKindBand
import com.lookie.opluskey.ui.screen.command.branchDefaultLabel
import com.lookie.opluskey.ui.screen.home.ResetMenu
import com.lookie.opluskey.ui.state.KeyWatchState
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
    // 「按键绑定」页顶栏那枚禁用标记要读的两位：**当前在编辑哪颗**（界面状态）× **它开没开**（配置）。
    //  判据直接借页内网格那支 [keyEnabled] —— 同一句「这一颗键开没开」不该有两份写法。
    val editingKeyDisabled = !keyEnabled(KeyWatchState.editing(configState.config.value))
    PageHeader(
        title = pageTitle(s),
        // 大标题**下面**那行（库原本的底线副标题槽位，自己占一行）：首页 = 模块版本。
        // 与「更多选项」页的「版本」行**同源但不同串**：那边总是带构建时刻，这边按构建类型决定
        // （见 appVersionLabel / appVersionText）。
        largeTitleSubtitle = largeTitleSubtitle(s),
        // 大标题**右边**那行小标题（补丁新增的槽位，折叠后保留），内容见 [pageSubtitle]。
        subtitle = pageSubtitle(s),
        // 尾随标题后面那枚小图标 —— 现在两处：
        //  · 「选择应用」页开着小窗打开时补一枚「小窗起」：与网格里「打开应用」那格同一口径
        //    （见 `BindingScreen` 的 `appSmallWindow`）。那一行字只说明「选了哪个应用」，
        //    看不出这一趟是要普通打开还是小窗起；
        //  · 「按键绑定」页**当前这颗键被停用**时补一枚禁用标记：名字旁边不标的话，这一页
        //    整片灰只能从「可能是模块关了 / 链路没通」里猜，而实际只是这一颗键不接管
        //    （与 ⋮ 菜单里那一行同一个字形，见 [MenuEntry.Row.stateIcon]）。
        trailingSubtitleIcon =
            when {
                // 「小窗起」只在**真选中了应用**时才标（否则那行是「还未选择应用」，图标会读成
                //  「这个还没选的应用会用小窗打开」）
                s == Screen.APP_PICKER && picker.smallWindow.value && appPickerChosenTarget() != null -> {
                    PageIcons.TileSmallWindow
                }

                s == Screen.BINDING && editingKeyDisabled -> {
                    PageIcons.Cancel
                }

                else -> {
                    null
                }
            },
        // 右上角那几颗动作键按页分派：**首页 / 「按键绑定」/「选择应用」三页有**
        // （首页是打开默认界面 / 清空配置，「按键绑定」是「快捷键」「自定义按键」两组，
        // 「选择应用」是「显示系统应用」那三项）。
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

        Screen.BRANCHES -> BRANCHES_TITLE

        Screen.APP_PICKER -> APP_PICKER_TITLE

        Screen.ACTIVITY_PICKER -> ACTIVITY_PICKER_TITLE

        Screen.ABOUT -> ABOUT_TITLE

        Screen.DONATE_LIST -> DONATE_LIST_TITLE

        Screen.GENERAL -> GENERAL_TITLE
    }

/**
 * 大标题**下面**那行小标题按页分派（库原本那个槽位，折叠后跟着标题缩）：
 *  · 首页 = 模块版本 —— **只读一次**：它要读包信息，首页每重组一帧都重读一遍没有意义；
 *  · 其余页都不占这一槽（「按键绑定」那一组名挪去了大标题右边的尾随标题，见 [pageSubtitle]）。
 */
@Composable
private fun SettingsPage.largeTitleSubtitle(s: Screen): String =
    when (s) {
        Screen.SHORTCUTS -> remember { appVersionLabel() }
        else -> ""
    }

/**
 * 顶栏右侧那行小标题（大标题右边的尾随标题）按页分派：
 * 「按键绑定」= **现在这一组**叫什么（「快捷键」或收集来的那颗键的名字）；
 * 「自定义命令」= 已保存的命令条数（[ConfigState.commands] 变了跟着重组）；
 * 「选择应用」= 这一趟要绑的应用名（先看本次草稿，再看 [appPickerSelectedPkg]；名字怎么取见 [appNameOf]）。
 */
private fun SettingsPage.pageSubtitle(s: Screen): String =
    when (s) {
        // 停在哪一颗就写哪一颗：侧键那一组仍叫「快捷键」，收集来的键用它自己的名字。
        //  与顶栏 ⋮ 那份菜单（[BindingKeyMenu]）同一个口径：菜单第一项就是这句话 ——
        //  这一行是菜单里那个「当前值」在大标题右边的复述，不进菜单也知道自己在哪一组。
        //  以前它挂在大标题**下面**（`largeTitleSubtitle`），会平白多占一行高度。
        Screen.BINDING -> {
            editingKeyName()
        }

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
            //  判据见 [appPickerChosenTarget]（草稿优先）。
            val target = appPickerChosenTarget()
            if (target == null) {
                // 没选过就不摆一行「已选择: 无」—— 那种写法像是有个叫「无」的应用
                I18n.get(Strings.APP_PICKER_UNSELECTED, "还未选择应用")
            } else {
                I18n.format(Strings.APP_PICKER_SELECTED_FORMAT, selectedLabel(target))
            }
        }

        // 分叉页：这一行写**在编哪一档** —— `侧键-单击`。分叉是按「键 + 手势」各一份的，
        //  标题只写「条件执行」看不出在改哪一档。
        Screen.BRANCHES -> {
            branchDefaultLabel()
        }

        // 二级页：这行小字说明**在看哪个应用**（页面标题只写「选择界面」，那一行放不下应用名）
        Screen.ACTIVITY_PICKER -> {
            picker.activityTarget?.let { selectedLabel(it) }.orEmpty()
        }

        else -> {
            ""
        }
    }

/**
 * 「选择应用」页这一趟**已经选中**的目标（草稿优先）；`null` = 还没选。
 *
 *  顶栏那行小字与它后面那枚「小窗起」图标共用这一个判据：没选应用时那一行是「还未选择应用」，
 *  后面再挂一枚小窗图标，读起来就像「这个还没选的应用会用小窗打开」（真机反馈）。
 *
 *  **先看本次草稿**（二级页刚挑完、还没提交的那一条），没有才退回「当前绑定」：
 *  这一行说的是「这一趟将要绑成什么」，草稿才是那个答案。只看当前绑定的话，
 *  挑完某个具体界面 / 快捷方式退回来，顶栏仍写着上一个应用的名字（而页里已经亮了新的）。
 *  草稿在每次进页时由 `AppPickerUiState.beginVisit` 清掉，不会跨趟残留在这一行上。
 */
private fun SettingsPage.appPickerChosenTarget(): String? = picker.draft?.target ?: appPickerSelectedPkg()

/**
 * 「已选择」那一行要显示的名字 —— 优先级只有一处（[appNameOf]）：
 * 配置里单独存的名字 → 老配置 token 里的 `|名字` → 现查 → 包名。
 *
 *  以前这里是「存下的名字 → 本次清单 → 包名」，而**清单那一步用的是 `AppTarget.same`**
 *  （连 `/类名` 一起比）：绑了具体界面 / 快捷方式时，清单里那一行是纯包名，永远匹配不上
 *  ⇒ 落到包名兜底（真机反馈的「选完快捷方式 / 界面，再进来显示的是包名」就是这个）。
 *  现在名字随绑定一起落盘，这一位不再需要清单参与。
 */
private fun SettingsPage.selectedLabel(target: String): String = appNameOf(target)

/** 顶栏右上角那几颗动作键 —— 首页 / 「按键绑定」/「选择应用」三页各一颗。 */
@Composable
private fun SettingsPage.PageActions(s: Screen) {
    when (s) {
        Screen.SHORTCUTS -> ResetMenu()

        // 「按键绑定」页的 ⋮ 只管**分组**（快捷键 / 自定义按键），不发命令 ——
        // 进「自定义命令」页的唯一入口仍是那一页右下角那颗 FAB（见 [BindingScreen]）。
        Screen.BINDING -> BindingKeyMenu()

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
                // 与整页同一个闸门：**当前在编辑的那颗键**自己的 `enabled`（关掉时带子灰掉
                //  且吃掉输入，与那颗 FAB 同款）
                enabled = keyEnabled(KeyWatchState.editing(configState.config.value)),
            )
        }

        Screen.COMMANDS -> {
            CommandKindBand(commandPager)
        }

        Screen.APP_PICKER -> {
            AppSearchBand()
        }

        // 二级页那条搜索带与上面那条**同一份**（同一个位置、同一档边距），只是搜索词各归各的
        Screen.ACTIVITY_PICKER -> {
            ActivitySearchBand()
        }

        // 首页 / 「关于模块」/「捐赠列表」都没有固定带（后两页都是「短内容」页，见 [pageScrollBehavior]）。
        //  「条件执行」也没有：它没有类型可选，那一页的内容直接顶在顶栏下面。
        Screen.SHORTCUTS, Screen.ABOUT, Screen.DONATE_LIST, Screen.GENERAL, Screen.BRANCHES -> {
            // 这几页不摆固定带
        }
    }
}
