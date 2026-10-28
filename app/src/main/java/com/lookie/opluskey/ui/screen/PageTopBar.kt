package com.lookie.opluskey.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.PageHeader
import com.lookie.opluskey.ui.PageTabBand
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.appVersionLabel
import com.lookie.opluskey.ui.model.ABOUT_TITLE
import com.lookie.opluskey.ui.model.ACTIVITY_PICKER_TITLE
import com.lookie.opluskey.ui.model.APP_PICKER_TITLE
import com.lookie.opluskey.ui.model.BINDING_TITLE
import com.lookie.opluskey.ui.model.BRANCHES_TITLE
import com.lookie.opluskey.ui.model.COMBO_TITLE
import com.lookie.opluskey.ui.model.COMMANDS_TITLE
import com.lookie.opluskey.ui.model.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.GENERAL_TITLE
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.SUBTITLE_DIM_FACTOR
import com.lookie.opluskey.ui.model.gestureLabel
import com.lookie.opluskey.ui.screen.apppicker.ActivitySearchBand
import com.lookie.opluskey.ui.screen.apppicker.AppPickerMenu
import com.lookie.opluskey.ui.screen.apppicker.AppSearchBand
import com.lookie.opluskey.ui.screen.binding.BindingKeyMenu
import com.lookie.opluskey.ui.screen.combo.ComboEditMenu
import com.lookie.opluskey.ui.screen.combo.comboLabel
import com.lookie.opluskey.ui.screen.command.BranchHeaderAdd
import com.lookie.opluskey.ui.screen.command.COMMAND_LIST_PAGE
import com.lookie.opluskey.ui.screen.command.CommandHeaderReset
import com.lookie.opluskey.ui.screen.command.CommandKindBand
import com.lookie.opluskey.ui.screen.command.branchDefaultLabel
import com.lookie.opluskey.ui.screen.home.ResetMenu
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.state.KeyWatchState
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    pagers: PagePagers,
) {
    // 这一页正在编辑的那一档**被停用**了吗 —— 「按键绑定」看那颗键，「组合键编辑」看那一组。
    //  判据直接借页内那一支（[keyEnabled] / 组合那一份配置）—— 同一句「开没开」不该有两份写法。
    //  两页共用同一枚标记、同一档压暗：用户看到的都是「这一份东西现在不接管」。
    val editingDisabled =
        when (s) {
            Screen.BINDING -> !keyEnabled(KeyWatchState.editing(configState.config.value))
            Screen.COMBO_EDIT -> editingComboOff()
            else -> false
        }
    PageHeader(
        title = pageTitle(s),
        // 大标题**下面**那行（库原本的底线副标题槽位，自己占一行）：首页 = 模块版本。
        // 与「更多选项」页的「版本」行**同源但不同串**：那边总是带构建时刻，这边按构建类型决定
        // （见 appVersionLabel / appVersionText）。
        largeTitleSubtitle = largeTitleSubtitle(s),
        // 大标题**右边**那行小标题（补丁新增的槽位，折叠后保留），内容见 [pageSubtitle]。
        subtitle = pageSubtitle(s),
        //  这一颗键 / 这一档组合停用 ⇒ 内容区整片压暗了，这行小标题也跟着**压低一档**：它是这一页上唯一还亮着的字，
        //  满亮度留着会读成「这一区还是活的」。
        //
        //  **压暗是「乘」不是「盖」**：`onSurfaceVariantSummary` 本身就是 `0x66000000`（alpha 0.4，
        //  见 coui 的 `Colors.kt`）。第一版写成 `copy(alpha = 0.5f)` —— 那是把它从 0.4 **抬**到 0.5，
        //  比原来还清楚，看起来当然「没生效」。所以按现有 alpha 乘一个系数。
        subtitleColor =
            if (editingDisabled) {
                COUITheme.colorScheme.onSurfaceVariantSummary.let { it.copy(alpha = it.alpha * SUBTITLE_DIM_FACTOR) }
            } else {
                COUITheme.colorScheme.onSurfaceVariantSummary
            },
        // 尾随标题后面那枚小图标 —— 现在两处：
        //  · 「选择应用」页开着小窗打开时补一枚「小窗起」：与网格里「打开应用」那格同一口径
        //    （见 `BindingScreen` 的 `appSmallWindow`）。那一行字只说明「选了哪个应用」，
        //    看不出这一趟是要普通打开还是小窗起；
        //  · 「按键绑定」页 /「组合键编辑」页**当前那一份被停用**时补一枚禁用标记：名字旁边不标的话，这一页
        //    整片灰只能从「可能是模块关了 / 链路没通」里猜，而实际只是这一颗键 / 这一档组合不接管
        //    （与 ⋮ 菜单里那一行同一个字形）。
        trailingSubtitleIcon =
            when {
                // 「小窗起」只在**真选中了应用**时才标（否则那行是「还未选择应用」，图标会读成
                //  「这个还没选的应用会用小窗打开」）
                s == Screen.APP_PICKER && picker.smallWindow.value && appPickerChosenTarget() != null -> {
                    PageIcons.TileSmallWindow
                }

                editingDisabled -> {
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
        actions = { PageActions(s, pagers) },
        scrollBehavior = scrollBehavior,
        state = hazeState,
        // 顶栏底下那条**固定带**（三条带子各归各的页），见 [PageBottomBand]。
        bottomContent = { PageBottomBand(s, pagers) },
    )
}

/**
 * 正在编辑的那一档组合被停用了吗 —— 顶栏那枚标记、那行小字的压暗、分段控件的闸门都要它。
 *
 *  找不到那一档（刚删完、退场那一帧）按**没停用**处理：那一帧这一页正在往下退，
 *  再压暗一次只会看到闪一下。
 */
private fun SettingsPage.editingComboOff(): Boolean {
    val cfg = configState.config.value
    val id = ComboEditState.editingId ?: return false
    return cfg.combos.firstOrNull { it.id == id }?.enabled == false
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

        // 两页不同名：列表页写「组合键列表」，编辑页写「组合键」——
        //  顶栏一眼能看出自己在哪一层（在编哪一组由尾随小标题说，见 [pageSubtitle]）。
        Screen.COMBOS -> COMBO_TITLE

        Screen.COMBO_EDIT -> COMBO_TITLE
    }

/**
 * 大标题**下面**那行小标题按页分派（库原本那个槽位，折叠后跟着标题缩）：
 *  · 首页 = 模块版本 —— **只读一次**：它要读包信息，首页每重组一帧都重读一遍没有意义；
 *  · 条件执行 = **在编哪一档**（`「侧键」-单击`）—— 这一行比其它页的尾随标题长（键名 + 手势），
 *    挂在大标题右边要跟标题抢宽度，放到下面独占一行才读得完整；
 *  · 其余页都不占这一槽（「按键绑定」那一组名在大标题右边的尾随标题，见 [pageSubtitle]）。
 */
@Composable
private fun SettingsPage.largeTitleSubtitle(s: Screen): String =
    when (s) {
        Screen.SHORTCUTS -> remember { appVersionLabel() }
        Screen.BRANCHES -> branchDefaultLabel()
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

        // 分叉页那一行不在这里：它写的是「在编哪一档」（`「侧键」-单击`），键名 + 手势连起来
        //  比其它页的尾随标题长得多，挂在大标题右边要跟标题抢宽度 —— 放到大标题下面独占一行
        //  （[largeTitleSubtitle]）。

        // 二级页：这行小字说明**在看哪个应用**（页面标题只写「选择界面」，那一行放不下应用名）
        Screen.ACTIVITY_PICKER -> {
            picker.activityTarget?.let { selectedLabel(it) }.orEmpty()
        }

        // 组合键编辑页：这一行写**在编哪一组**（`侧键 + 电量 +`）—— 标题只写「组合键」，
        //  而这一页改的是其中一档，不写就分不出在看哪一组。
        Screen.COMBO_EDIT -> {
            val cfg = configState.config.value
            ComboEditState.editingId
                ?.let { id -> cfg.combos.firstOrNull { it.id == id } }
                ?.let { comboLabel(cfg, it, bracketed = true) }
                .orEmpty()
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
private fun SettingsPage.PageActions(
    s: Screen,
    pagers: PagePagers,
) {
    when (s) {
        Screen.SHORTCUTS -> ResetMenu()

        // 「按键绑定」页的 ⋮ 只管**分组**（快捷键 / 自定义按键），不发命令 ——
        // 进「自定义命令」页的唯一入口仍是那一页右下角那颗 FAB（见 [BindingScreen]）。
        Screen.BINDING -> BindingKeyMenu()

        Screen.APP_PICKER -> AppPickerMenu()

        // 「条件执行」页那颗「添加」：这一页唯一的加法入口。右下角只留「确认」一颗 FAB，
        //  加法放在「这一页有什么」那一层的工具位置上（用户口径）。
        Screen.BRANCHES -> BranchHeaderAdd()

        // 「自定义命令」页那颗「重置 / 取消」：底栏只剩「保存」，重置挪到这一头（用户口径）
        Screen.COMMANDS -> CommandHeaderReset(onListPage = pagers.commands.currentPage == COMMAND_LIST_PAGE)

        // 组合键编辑页的 ⋮：只有「删除组合键」一行（见 [ComboEditMenu]）
        Screen.COMBO_EDIT -> ComboEditMenu()

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
    pagers: PagePagers,
) {
    when (s) {
        Screen.BINDING -> {
            PageTabBand(
                pagers.gestures,
                labels = Gesture.entries.map { gestureLabel(it) },
                // 与整页同一个闸门：**当前在编辑的那颗键**自己的 `enabled`（关掉时带子灰掉
                //  且吃掉输入，与那颗 FAB 同款）
                enabled = keyEnabled(KeyWatchState.editing(configState.config.value)),
                band = bindingBandHeight,
            )
        }

        Screen.COMMANDS -> {
            CommandKindBand(pagers.commands)
        }

        // 「组合键」编辑页：短按 / 长按两档，与「按键绑定」那条带子同一个组件、同一个落点
        Screen.COMBO_EDIT -> {
            PageTabBand(
                pagers.combos,
                labels =
                    listOf(
                        I18n.get(Strings.COMBO_SHORT, "短按"),
                        I18n.get(Strings.COMBO_LONG, "长按"),
                    ),
                // 闸门是**整页那道 + 这一档自己的开关**（与上面「按键绑定」那条同构：那一页吃的是
                //  那颗键的 `enabled`）。停用之后分段控件灰掉，两档都翻不动 —— 与网格同一层判断。
                enabled = pageWritable() && !editingComboOff(),
                band = comboBandHeight,
            )
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
        Screen.SHORTCUTS, Screen.ABOUT, Screen.DONATE_LIST, Screen.GENERAL, Screen.BRANCHES,
        Screen.COMBOS,
        -> {
            // 这几页不摆固定带
        }
    }
}

/**
 * 当前这颗键在界面上叫什么 —— 侧键那一组仍叫「快捷键」，收集来的键用它自己的名字。
 *
 *  与「按键绑定」页那组名同一个口径（顶栏 ⋮ 菜单第一项就是这句话）：那一页的尾随小标题、
 *  分叉页的「在编哪一档」都用它，免得同一个东西两处两个叫法。
 *
 *  住在这一层而不是 `ui/model/PageCopy`：它读的是页面状态（在编辑哪颗键），
 *  而 `ui/model` 只放纯展示数据，不许反持页面。
 */
internal fun SettingsPage.editingKeyName(): String =
    KeyWatchState
        .editing(configState.config.value)
        .let { if (it.isCustom) it.label else I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键") }
