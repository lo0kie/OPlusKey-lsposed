package com.lookie.opluskey.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.core.present.comboLabelOf
import com.lookie.opluskey.core.present.keyName
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageHeader
import com.lookie.opluskey.ui.model.page.HEADER_ICON_GAP
import com.lookie.opluskey.ui.model.text.ABOUT_TITLE
import com.lookie.opluskey.ui.model.text.ACTIVITY_PICKER_TITLE
import com.lookie.opluskey.ui.model.text.BINDING_TITLE
import com.lookie.opluskey.ui.model.text.BRANCHES_TITLE
import com.lookie.opluskey.ui.model.text.COMBO_TITLE
import com.lookie.opluskey.ui.model.text.COMMANDS_TITLE
import com.lookie.opluskey.ui.model.text.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.text.GENERAL_TITLE
import com.lookie.opluskey.ui.model.text.KEYS_TITLE
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.theme.SUBTITLE_DIM_FACTOR
import com.lookie.opluskey.ui.row.ChipLook
import com.lookie.opluskey.ui.row.ChipSentence
import com.lookie.opluskey.ui.row.NameChipRow
import com.lookie.opluskey.ui.screen.apppicker.ActivitySearchBand
import com.lookie.opluskey.ui.screen.apppicker.AppPickerMenu
import com.lookie.opluskey.ui.screen.apppicker.AppSearchBand
import com.lookie.opluskey.ui.screen.apppicker.pickerSubtitle
import com.lookie.opluskey.ui.screen.binding.BindingKeyMenu
import com.lookie.opluskey.ui.screen.binding.HeaderSettingsButton
import com.lookie.opluskey.ui.screen.binding.bindingTabBand
import com.lookie.opluskey.ui.screen.binding.bindingTargetOff
import com.lookie.opluskey.ui.screen.combo.ComboEditMenu
import com.lookie.opluskey.ui.screen.combo.KeysHeaderEdit
import com.lookie.opluskey.ui.screen.command.BranchHeaderAdd
import com.lookie.opluskey.ui.screen.command.COMMAND_LIST_PAGE
import com.lookie.opluskey.ui.screen.command.CommandHeaderReset
import com.lookie.opluskey.ui.screen.command.CommandKindBand
import com.lookie.opluskey.ui.screen.command.branchOwnerLabel
import com.lookie.opluskey.ui.screen.command.branchOwnerNames
import com.lookie.opluskey.ui.screen.home.HomeHeaderMenu
import com.lookie.opluskey.ui.screen.variables.VariableHeaderAdd
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.state.binding.KeyWatchState
import com.lookie.opluskey.ui.state.page.SettingsTarget
import com.lookie.opluskey.ui.state.page.SettingsTargetState
import com.lookie.opluskey.ui.wire.appNameOf
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
    // 这一页正在编辑的那一份**被停用**了吗 —— 按键绑定 / 组合键 / 场景动作三页看完的是一句
    //  `bindingTargetOff`，「按键设置」另看这颗键（那一页装的全是它的参数）。
    //  它只剩一个用处：把标题下面那行副标题压暗一档（判据与页内控件同一句）。
    val editingDisabled =
        when (s) {
            Screen.BINDING, Screen.COMBO_EDIT, Screen.SCENE_BINDING -> bindingTargetOff(s)

            //  「按键设置」也看这颗键：停用时整片灰
            Screen.KEY_SETTINGS -> !keyEnabled(KeyWatchState.editing(configState.config.value))

            else -> false
        }
    //  这行小标题的压暗带过渡：拔掉这一颗键 / 这一组时，页内内容整片在渐变，只有它硬切会像闪了一下。
    val subtitleTint by animateColorAsState(
        targetValue =
            if (editingDisabled) {
                COUITheme.colorScheme.onSurfaceVariantSummary.let { it.copy(alpha = it.alpha * SUBTITLE_DIM_FACTOR) }
            } else {
                COUITheme.colorScheme.onSurfaceVariantSummary
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "HeaderSubtitle",
    )
    //  装**键名**的那几页把名字画成圆角框（tag）—— 与列表行、场景条件同一套长相；
    //  拼成一整串纯文字就画不出框（`[侧键]×[电量 +]` 那种方括号写法就此退场）。
    //  其余页仍是纯文字（[pageSubtitle]）：它们装的是句子或应用名，框对它们没有意义。
    val cfg = configState.config.value
    val keyChips: (@Composable () -> Unit)? =
        when (s) {
            Screen.BINDING -> {
                keyChipsOf(listOf(editingKeyName()), subtitleTint)
            }

            Screen.KEY_SETTINGS -> {
                //  **按住那一档的独立设置也写成一句话**（用户口径）：这一页与它自己的列表行、
                //  编辑页顶栏说的是同一档组合，三处不该有两种念法。
                val target = SettingsTargetState.current
                val combo =
                    (target as? SettingsTarget.Combo)
                        ?.let { t -> cfg.combos.firstOrNull { it.id == t.id } }
                if (combo != null && combo.isHold) {
                    holdComboChips(cfg, combo, subtitleTint)
                } else {
                    keyChipsOf(settingsTargetNames(cfg, target), subtitleTint)
                }
            }

            Screen.BRANCHES -> {
                //  这一页在编「哪一份」的场景表：归属是**按住组合**时与列表 / 编辑页 / 独立设置
                //  同一句话（用户口径）—— 同一档组合在这四处不该有两种念法。
                val combo =
                    branchDraft.comboId
                        ?.let { id -> cfg.combos.firstOrNull { it.id == id } }
                if (combo != null && combo.isHold) {
                    holdComboChips(cfg, combo, subtitleTint)
                } else {
                    keyChipsOf(branchOwnerNames(cfg, branchDraft.comboId, editingKeyName()), subtitleTint)
                }
            }

            //  组合键编辑页：那一组还在时才有名字；刚被删掉（退场那一帧）时退回纯文字那句兜底。
            //
            //  **按住那两档写成一句话**（用户口径「副标题改成和列表一样，按住 A 后再按 B」）：
            //  那两档的判据就是「谁先按住」，只摆两枚框读不出顺序，而顺序正是这两档的区分所在。
            //  与组合列表那一行同一份实现（`ChipSentence`），键名仍是框。
            Screen.COMBO_EDIT -> {
                val combo =
                    ComboEditState.editingId
                        ?.let { id -> cfg.combos.firstOrNull { it.id == id } }
                when {
                    combo == null -> {
                        null
                    }

                    combo.isHold -> {
                        holdComboChips(cfg, combo, subtitleTint)
                    }

                    else -> {
                        keyChipsOf(comboKeyNames(cfg, combo), subtitleTint)
                    }
                }
            }

            else -> {
                null
            }
        }
    PageHeader(
        title = pageTitle(s),
        // 大标题**下面**那行小标题（库的 `subtitle` 槽位，折叠后保留），内容见 [pageSubtitle]。
        subtitle = if (keyChips == null) pageSubtitle(s) else "",
        subtitleContent = keyChips,
        //  这一颗键 / 这一档组合停用 ⇒ 内容区整片压暗了，这行小标题也跟着**压低一档**：它是这一页上唯一还亮着的字，
        //  满亮度留着会读成「这一区还是活的」。
        //
        //  **压暗是「乘」不是「盖」**：`onSurfaceVariantSummary` 本身就是 `0x66000000`（alpha 0.4，
        //  见 coui 的 `Colors.kt`）。第一版写成 `copy(alpha = 0.5f)` —— 那是把它从 0.4 **抬**到 0.5，
        //  比原来还清楚，看起来当然「没生效」。所以按现有 alpha 乘一个系数（目标值在 [subtitleTint] 里算）。
        subtitleColor = subtitleTint,
        // 右上角那几颗动作键按页分派：**首页 / 「按键绑定」/「选择应用」三页有**
        // （首页是打开默认界面 / 重启服务 / 导出日志，「按键绑定」是「快捷键」「自定义按键」两组，
        // 「选择应用」是「以小窗口打开」那颗开关 + ⋮「显示系统应用 / 显示失效应用」）。
        // 顶栏只留一个 `actions` 槽位、不认识页面（见 [PageHeader]），
        // 两份菜单各自跟着自己那一屏走（[HomeHeaderMenu] / [AppPickerMenu]）。
        //
        //  「按键绑定」页那一套「新增命令」菜单晚些时候**撤掉了**，不再有 ⋮。
        // 进「自定义命令」页的唯一入口现在是那一页右下角的那颗 FAB（见 `BindingPagerScreen`）。
        actions = { PageActions(s, pagers) },
        scrollBehavior = scrollBehavior,
        state = hazeState,
        // 顶栏底下那条**固定带**（三条带子各归各的页），见 [PageBottomBand]。
        bottomContent = { PageBottomBand(s, pagers) },
    )
}

/**
 * 这一页的大标题 —— **一张纯映射表**：一页一行，没有业务分支。
 *
 *  `CyclomaticComplexMethod` 在这类「一个大 `when`、每个分支一句常量」的地方没有意义
 *  （它的「复杂度」等于页数），拆成几张表反而要多维护几处；所以这里显式压掉，
 *  **不是因为改不动**。真出现需要判断的分支时，再拆。
 */
private fun SettingsPage.pageTitle(s: Screen): String =
    when (s) {
        // 首页标题 = **模块自己的名字**（`OPlusKey`，与 `app_name` 同一串）。
        //  不再显示宿主 Activity 那句「快捷键」：这一页是模块的门面，标题该说「这是谁」，
        //  而不是复述宿主给这一格起的名 —— 宿主那串只在字典缺这一档时兜底（`hooks.homeTitle`）。
        Screen.SHORTCUTS -> {
            I18n.get(Strings.HOME_TITLE, hooks.homeTitle)
        }

        Screen.BINDING -> {
            BINDING_TITLE
        }

        // 「独立设置」= 这一档对公共项的覆盖，与公共那一页（「按键设置」）**不同名**：
        //  两页都叫「按键设置」时，用户分不清进的是默认值还是覆盖值。副标题写「在改哪一颗」
        //  （见 [pageSubtitle]）。
        Screen.KEY_SETTINGS -> {
            I18n.get(Strings.KEY_SETTINGS_TITLE, "独立设置")
        }

        Screen.COMMANDS -> {
            COMMANDS_TITLE
        }

        // 「常用命令」：与绑定页那一段同名 —— 同一个东西在两处叫两个名字只是多一处要记。
        Screen.COMMON_COMMANDS -> {
            I18n.get(Strings.BINDING_COMMON_COMMANDS, "常用命令")
        }

        Screen.BRANCHES -> {
            BRANCHES_TITLE
        }

        //  **不与「系统场景」同名**：那一页是这张表，这一页是表里某一块的某一档 ——
        //  两页都写「系统场景」时分不清退一层会回到哪里。
        Screen.SCENE_BINDING -> {
            I18n.get(Strings.COMMAND_BRANCH_BINDING_TITLE, "场景动作")
        }

        Screen.APP_PICKER -> {
            //  这一页两个状态共用，主标题直接说这一趟做什么，挑的是谁交给标题下面那行小标题
            //  （「已选择: 微信」）—— 原来两件事挤在标题一个「选择应用」里，下面那行再拼一遍
            //  「打开应用 · 微信」，重复。
            //
            //  **多选那一趟（给条件挑前台应用）说的是「多选应用」**：那一趟点一行是勾选、右下角
            //  才落盘，写「打开应用」会被读成「点一下就打开它」。三档的判据与行尾图标、
            //  点行行为同一个 `appOnly` 家族，不另立一套。
            when {
                picker.forBranch -> I18n.get(Strings.APP_PICKER_MULTI_TITLE, "多选应用")
                picker.stopApp -> I18n.get(Strings.TOOL_STOP_APP, "结束应用")
                else -> I18n.get(Strings.TOOL_OPEN_APP, "打开应用")
            }
        }

        Screen.ACTIVITY_PICKER -> {
            ACTIVITY_PICKER_TITLE
        }

        Screen.ABOUT -> {
            ABOUT_TITLE
        }

        Screen.DONATE_LIST -> {
            DONATE_LIST_TITLE
        }

        Screen.GENERAL -> {
            GENERAL_TITLE
        }

        // 两页不同名：列表页写「组合键列表」，编辑页写「组合键」——
        //  顶栏一眼能看出自己在哪一层（在编哪一组由标题下面那行小标题说，见 [pageSubtitle]）。
        Screen.KEYS -> {
            KEYS_TITLE
        }

        Screen.COMBO_EDIT -> {
            COMBO_TITLE
        }

        // 变量页标题与「快捷功能」里那一格同名：用户是从那一格进来的，两处叫法要一致
        Screen.VARIABLES -> {
            I18n.get(Strings.TOOL_VARIABLES, "自定义变量")
        }
    }

/**
 * 顶栏大标题下面那行小标题按页分派 —— **每一页都有**（用户口径：副标题缺一页时
 * 顶栏矮一截，翻页 / 返回时整页跟着跳）。
 *
 * 首页那一行是**这个模块是干什么的**（`home.subtitle`），不是版本号：大标题已经是模块名，
 * 下面再跟一串 `version-1.4.0-debug-…` 对用户没有意义 —— 要看版本的去「更多选项」页，
 * 首页那张「激活状态」卡里也有一行。
 *
 * 「按键绑定」= **现在这一组**叫什么（「快捷键」或收集来的那颗键的名字）；
 * 「自定义命令」= 已保存的命令条数（[ConfigState.commands] 变了跟着重组）；
 * 「选择应用」= 这一趟要绑的应用名（见 `pickerSubtitle`）；
 * 其余各页要么是「在编谁」（[editingKeyName] / [branchOwnerLabel] / 组合名），
 * 要么一句静态的「这一页是什么」。
 *
 * `CyclomaticComplexMethod` 与 [pageTitle] 同一个口径显式压掉：这是一个「一页一行」的映射表，
 * 复杂度等于页数，没有可拆的分支。
 */
private fun SettingsPage.pageSubtitle(s: Screen): String =
    when (s) {
        // 首页这一行**不写版本号**（用户口径）：一句模块是干什么的，短，且不随构建变。
        //  版本只在下面「激活状态」那张卡里（见 `appVersionLabel`）。
        Screen.SHORTCUTS -> {
            I18n.get(Strings.HOME_SUBTITLE, "把任意动作绑到侧键与自定义按键")
        }

        // 停在哪一颗就写哪一颗：侧键那一组仍叫「快捷键」，收集来的键用它自己的名字。
        //  与顶栏 ⋮ 那份菜单（[BindingKeyMenu]）同一个口径：菜单第一项就是这句话 ——
        //  这一行是菜单里那个「当前值」在大标题下面的复述，不进菜单也知道自己在哪一组。
        Screen.BINDING -> {
            editingKeyName()
        }

        //  「独立设置」页：写**这一趟在改谁** —— 单键是键名、组合是成员键名连起来那一串。
        //  它由 `SettingsTargetState` 决定，与「从哪一页进来」无关（两页进的是同一页）。
        Screen.KEY_SETTINGS -> {
            settingsTargetLabel(SettingsTargetState.current)
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

        // 这一行说**这张清单有几条**（与绑定页那一段标题同一个数）：页内那一段自己带条数，
        // 顶栏这一行说的则是「钉住的有几条」，两者是同一件事的两种呈现。
        //
        //  条数**嵌进句子**（「已添加 3 个动作」）而不是拼成 `标题 (3)`：这一行是副标题，
        //  读的是一句说明，不是一个小标题 + 一格计数 —— 与「已保存 3 条命令」同一写法。
        //  一条都没有时同样不报数（「已添加 0 个动作」读起来像统计，也不成句），
        //  与上面「已保存的命令」那一页同一个口径。
        Screen.COMMON_COMMANDS -> {
            val count = configState.config.value.global.commonCommands.size
            if (count == 0) {
                I18n.get(Strings.COMMON_COMMANDS_SUBTITLE_EMPTY, "没有添加的动作")
            } else {
                I18n.format(Strings.COMMON_COMMANDS_SUBTITLE, count)
            }
        }

        Screen.APP_PICKER -> {
            pickerSubtitle()
        }

        // 「条件执行」页这一行写**在编哪一颗键**（`「快捷键」` / `「侧键」+「电量 +」`）：
        //  这一页是从「按键绑定」的某颗键上进来的，进来之后整页再没有别处提过它是哪一颗 ——
        //  而「改的是哪一颗键」正是这一页最容易搞错的东西。哪一档手势不写在这里：它由顶栏下
        //  那条手势带说，两边各说一半（见 [branchOwnerLabel]）。
        Screen.BRANCHES -> {
            //  这一页的副标题实际画的是框（见 [PageTopBar] 的 `keyChips`）；这句只在
            //  「没有框」的极端情形下兜底 —— 与框里的名字同一份来源。
            branchOwnerLabel()
        }

        // 场景动作页：这一行写**在编第几块**（「场景 3」）—— 标题只写「场景动作」，
        //  而这一页改的是表里某一格，不写就分不出改的是哪一块。哪一档手势不写在这里：
        //  它由顶栏下那条手势带说，两边各说一半（与场景页那套同一个分工）。
        Screen.SCENE_BINDING -> {
            //  `+1` 之后再夹一道：这一位在**出栈清回 -1 之后**还会被画上几百毫秒（退场动画），
            //  不夹就会在那一段写着「场景 0」。
            I18n.format(Strings.COMMAND_BRANCH_ITEM, (sceneBinding.at + 1).coerceAtLeast(1))
        }

        // 二级页：这行小字说明**在看哪个应用**（页面标题只写「选择界面」，那一行放不下应用名）
        Screen.ACTIVITY_PICKER -> {
            picker.activityTarget?.let { appNameOf(it) }
                ?: I18n.get(Strings.APP_PICKER_NONE_SELECTED, "没有选择应用")
        }

        // 组合键编辑页：这一行写**在编哪一组**（`侧键 × 电量 +`）—— 标题只写「组合键」，
        //  而这一页改的是其中一档，不写就分不出在看哪一组。
        //  那一组刚被删掉（退场那一帧）时没有名字可写，交给下面那句兜底。
        //  实际画的是框（见 [pageSubtitleChips]），这一句同样只兜「没有框」的极端情形。
        Screen.COMBO_EDIT -> {
            val cfg = configState.config.value
            ComboEditState.editingId
                ?.let { id -> cfg.combos.firstOrNull { it.id == id } }
                ?.let { comboLabelOf(comboKeyNames(cfg, it)) }
                ?: I18n.get(Strings.COMBO_PICK_KEYS, "选择按键")
        }

        // 「按键功能」：这一页就是两张清单，标题已经说了叫什么，这一行说清单里是什么。
        Screen.KEYS -> {
            I18n.get(Strings.KEYS_ENTRY_DESC, "按键列表与组合按键")
        }

        // 「更多选项」：这一页是三节 + 一节关于，一句「装了什么」比重复版本号有用。
        Screen.ABOUT -> {
            I18n.get(Strings.MORE_SUBTITLE, "运行状态、外观与配置")
        }

        // 「捐赠列表」：一句谢谢（与页内空态同一句，不改口径）。
        Screen.DONATE_LIST -> {
            I18n.get(Strings.DONATE_LIST_EMPTY_SUMMARY, "收到的每一份支持都会记在这里")
        }

        // 「按键设置」（公共那份）：与每颗键自己那份「独立设置」相对 —— 这里改的是默认值。
        Screen.GENERAL -> {
            I18n.get(Strings.GENERAL_SUBTITLE, "所有按键共用")
        }

        // 变量：一句「它是干什么用的」—— 这一页没有「在编谁」可言（清单是整份）。
        Screen.VARIABLES -> {
            I18n.get(Strings.VARIABLES_SUBTITLE, "命令与场景里可以引用")
        }
    }

/** 顶栏右上角那几颗动作键 —— 首页 / 「按键绑定」/「选择应用」三页各一颗。 */
@Composable
private fun SettingsPage.PageActions(
    s: Screen,
    pagers: PagePagers,
) {
    when (s) {
        Screen.SHORTCUTS -> {
            HomeHeaderMenu()
        }

        // 「按键绑定」页的 ⋮ 只管**分组**（快捷键 / 自定义按键），不发命令 ——
        // 进「自定义命令」页的唯一入口仍是那一页右下角那颗 FAB（见 `BindingPagerScreen`）。
        //  它**左边**多一颗设置键：进的是**这一档的「独立设置」**（`Screen.KEY_SETTINGS`，
        //  接口 / 振动 / 提示这几项对这一颗键的覆盖）。公共那一份在首页「按键操作」卡第二行
        //  （`Screen.GENERAL`，见 `GeneralScreen`）—— 两页名字只差一个字，别混。
        Screen.BINDING -> {
            HeaderSettingsButton(SettingsTarget.Key(KeyWatchState.editing(configState.config.value)))
            //  两颗圆键之间要给一条缝：库那排是 `Arrangement.End`、一个挨一个，
            //  贴在一起时两圈描边连成一条线（见 [HEADER_ICON_GAP]）
            Spacer(Modifier.width(HEADER_ICON_GAP))
            BindingKeyMenu()
        }

        Screen.APP_PICKER -> {
            AppPickerMenu()
        }

        // 「条件执行」页那颗「添加」：这一页唯一的加法入口。右下角只留「确认」一颗 FAB，
        //  加法放在「这一页有什么」那一层的工具位置上（用户口径）。
        Screen.BRANCHES -> {
            BranchHeaderAdd()
        }

        // 「自定义命令」页那颗「重置 / 取消」：底栏只剩「保存」，重置挪到这一头（用户口径）
        Screen.COMMANDS -> {
            CommandHeaderReset(onListPage = pagers.commands.currentPage == COMMAND_LIST_PAGE)
        }

        // 组合键编辑页的 ⋮：只有「删除组合键」一行（见 [ComboEditMenu]）。
        //  它**左边**同样有一颗「独立设置」—— 与「按键绑定」页那颗是同一个组件、同一页目标：
        //  组合也要能自己决定接口 / 振动 / 提示（用户口径：按键与组合键功能完全一样）。
        Screen.COMBO_EDIT -> {
            ComboEditState.editingId?.let { HeaderSettingsButton(SettingsTarget.Combo(it)) }
            Spacer(Modifier.width(HEADER_ICON_GAP))
            ComboEditMenu()
        }

        // 「按键功能」页那颗「编辑 / 完成」：进 / 出这一页的编辑态（勾选 → 删除按键，
        //  见 [KeysHeaderEdit]）。编辑态里它自己换成「完成」。
        Screen.KEYS -> {
            KeysHeaderEdit()
        }

        // 「自定义变量」页那颗「新建」：这一页唯一的加法入口（原先在右下角那颗 FAB 上）
        Screen.VARIABLES -> {
            VariableHeaderAdd()
        }

        // 「变量操作」页顶栏没有动作：那一页只剩「挑一步」（挑完点页里那颗 FAB 落盘）。
        //  删掉一颗变量的入口在「自定义变量」页点一行弹的那张窗里（见 `VarManageDialog`）。
        else -> {
            // 其余页顶栏没有动作
        }
    }
}

/**
 * 顶栏底下那条**固定带**（三条带子各归各的页）。
 *
 *  不能各页各叠一层浮层（自带取样层 / 模糊 / 半透明底 / 内容里等高占位），统一搬进顶栏这个
 *  `bottomContent` 槽位：位置、底色、模糊、下沿那条细线都由顶栏那一份负责，见 [PageHeader]
 *  与三个带子自己的 KDoc。
 *  带子跨度与内容高度都各自报给页面级状态（[SettingsPage.bands]），页内那个占位
 *  Spacer 读 —— 带子与内容分处两棵树，只有这一条路能对齐。
 */
@Composable
private fun SettingsPage.PageBottomBand(
    s: Screen,
    pagers: PagePagers,
) {
    when (s) {
        //  带子底下那三页**共用一份实现**，带子也只有一条（`bindingTabBand`）——
        //  原来是三处分段控件各写一遍，加一页就得记着补一处。
        Screen.BINDING, Screen.COMBO_EDIT, Screen.SCENE_BINDING -> {
            bindingTabBand(s, pagers)
        }

        Screen.COMMANDS -> {
            CommandKindBand(pagers.commands)
        }

        // 「常用命令」页没有带子：它不是「某一档手势」，也没有分页器 —— 一张清单到底。
        Screen.COMMON_COMMANDS -> {
        }

        // 「系统场景」页**没有带子**：翻转之后一张表里每块自带三档，页面不再按手势分页，
        //  所以这一页既没有分段控件、也不占带子那一段高度（见 `BranchScreen` 的布局）。
        Screen.BRANCHES -> {
            //  这一页没有带子（见上一行）
        }

        Screen.APP_PICKER -> {
            AppSearchBand()
        }

        // 二级页那条搜索带与上面那条**同一份**（同一个位置、同一档边距），只是搜索词各归各的
        Screen.ACTIVITY_PICKER -> {
            ActivitySearchBand()
        }

        // 首页 / 「关于模块」/「捐赠列表」都没有固定带（后两页都是「短内容」页，见 [pageScrollBehavior]）。
        //  「自定义变量」同样没有：那一页没有分页器，也就没有带子。
        //  「按键设置」（按键级那一页）也不摆：它是一次把四节看完的直列，没有档可切。
        Screen.SHORTCUTS, Screen.ABOUT, Screen.DONATE_LIST, Screen.GENERAL,
        Screen.KEY_SETTINGS, Screen.KEYS, Screen.VARIABLES,
        -> {
            // 这几页不摆固定带
        }
    }
}

// 副标题要**画成框**的那几页都在 [PageTopBar] 的 `keyChips` 那一支里（就地算，见那里的注释）。
//
//  只有「这一行装的是键名」的那几页画框：单键一枚，组合每颗各一枚、中间一个 `×`。
//  其余页仍是纯文字（[pageSubtitle]）：它们装的是句子或应用名，框对它们没有意义。
//
//  框的字号就是副标题那一档（`body2`），颜色跟着 [PageTopBar] 算好的压暗值走 ——
//  这一页整片变灰时框与里面的字一起暗下去，不会只剩一圈框还亮着。

/** 一串键名 → 顶栏那一行（单行 `body2` 圆角框，超宽由 [NameChipRow] 裁掉）。 */
private fun keyChipsOf(
    names: List<String>,
    color: Color,
): @Composable () -> Unit =
    {
        NameChipRow(names = names, look = ChipLook(COUITheme.textStyles.body2, color, enabled = true))
    }

/**
 * 当前这颗键在界面上叫什么 —— 侧键那一组仍叫「快捷键」，收集来的键用它自己的名字。
 *
 *  与「按键绑定」页那组名同一个口径（顶栏 ⋮ 菜单第一项就是这句话）：那一页的顶栏副标题、
 *  分叉页的「在编哪一档」都用它，免得同一个东西两处两个叫法。
 *
 *  住在这一层而不是 `ui/model/PageCopy`：它读的是页面状态（在编辑哪颗键），
 *  而 `ui/model` 只放纯展示数据，不许反持页面。
 */
internal fun SettingsPage.editingKeyName(): String =
    KeyWatchState
        .editing(configState.config.value)
        .let { if (it.isCustom) it.label else I18n.get(Strings.BINDING_SECTION_KEYS, "快捷键") }

/**
 * 「独立设置」页那一行副标题 —— 在改**哪一颗键 / 哪一组组合**。
 *
 *  组合走 `comboLabel`（成员键名连起来，`「侧键」×「电量 +」`）：它才是用户认得出的名字，
 *  `cmb1` 只是内部 id，写出来等于什么都没说。单键走 `KeyId.label`（侧键 / 用户起的名字）。
 *
 *  放在这一层而不是 `SettingsTarget` 上：取名字要用 `comboLabel` 与配置里的键名表，
 *  那是界面层的东西，`ui/state` 不该反持。
 */
internal fun SettingsPage.settingsTargetLabel(target: SettingsTarget): String =
    comboLabelOf(settingsTargetNames(configState.config.value, target))

/**
 * 同一个东西的**成员键名**（一档组合有几颗就是几个名字）—— 需要逐个上样式的调用点用它。
 *
 *  顶栏副标题把每颗画成一枚圆角框，所以那里要名单而不是拼好的句子；而「单键叫什么、组合的
 *  成员是谁」只有这一份实现，[settingsTargetLabel] 由它压成一串 —— 两处不会长出不同叫法。
 *
 *  `cfg` 由调用点给（而不是收成 `SettingsPage` 的扩展）：这一支是纯取名，不碰页面状态，
 *  于是「页面扩展成员」这本账不会因为多一个读者就涨一格。
 */
internal fun settingsTargetNames(
    cfg: Config,
    target: SettingsTarget,
): List<String> =
    when (target) {
        is SettingsTarget.Key -> {
            //  单键直接写名字（`keyName`）：内置键的 `label` 只是配置里那一份，字典换成英文时
            //  它不会跟着变 —— 走同一个出口才与「按键功能」页清单、执行提示同一个叫法。
            listOf(keyName(target.id))
        }

        is SettingsTarget.Combo -> {
            cfg.combos
                .firstOrNull { it.id == target.id }
                ?.let { comboKeyNames(cfg, it) }
                // 那一组刚被删掉（退场那一帧）：报 id 总比空串强，页面自己会退
                ?: listOf(target.id)
        }
    }

/**
 * **按住**那一档的副标题 —— 一句话「按住 A 后再按 B」，键名仍是名字框。
 *
 *  那两档的判据就是「谁先按住」：只摆两枚框 `A × B` 读不出顺序，而顺序正是这两档的区分所在。
 *  与组合列表那一行是同一份实现（`ChipSentence`）：语序在模板里、框在 `NameChip` 里，
 *  区别只是这里不套整列那个对齐用的框宽（这一行是一句话）。
 *
 *  三处顶栏共用这一支：组合编辑页、「按键设置」里那一档组合的独立设置 —— 它们说的是同一档组合，
 *  不该两种念法。
 *
 * @param color 这一行的字色（`subtitleTint`：停用那一档会压暗）
 */
private fun holdComboChips(
    cfg: Config,
    combo: ComboKey,
    color: Color,
): @Composable () -> Unit =
    {
        val names = comboKeyNames(cfg, combo)
        //  第二颗先按住那一档，句子里的顺序要对调（与列表同一处判据）
        val ordered = if (combo.mode == ComboKey.MODE_HOLD_SECOND) names.reversed() else names
        ChipSentence(
            template = I18n.get(Strings.COMBO_MODE_HOLD, "按住 %s 后再按 %s"),
            names = ordered,
            look = ChipLook(COUITheme.textStyles.body2, color, enabled = true),
            style = COUITheme.textStyles.body2,
            color = color,
        )
    }
