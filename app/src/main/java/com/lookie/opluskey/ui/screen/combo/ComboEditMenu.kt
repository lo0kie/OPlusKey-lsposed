package com.lookie.opluskey.ui.screen.combo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.core.present.comboLabel
import com.lookie.opluskey.core.present.comboMemberSeparator
import com.lookie.opluskey.core.present.comboOrderedAgain
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HEADER_ACTION_GAP
import com.lookie.opluskey.ui.chrome.HeaderIconButton
import com.lookie.opluskey.ui.chrome.HeaderMenuButton
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.feedback.DisabledHint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.text.BRANCHES_TITLE
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.row.ChipSentence
import com.lookie.opluskey.ui.row.NameFlow
import com.lookie.opluskey.ui.screen.binding.openKeySettings
import com.lookie.opluskey.ui.screen.command.scenarioMenuSummary
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.state.binding.KeysEditState
import com.lookie.opluskey.ui.state.page.SettingsTarget
import com.lookie.opluskey.ui.wire.disabledClickReason
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「组合按键编辑」页顶栏那颗 ⋮ —— 现在四行：系统场景 / 独立设置 / 停用启用 / 删除。
 *
 *  为什么删在这个页而不在列表那一行：列表一行只有「进这一页」一件事，行尾再挂一颗删除图标
 *  会挤掉「进下一页」那枚箭头（`CardRow.Item` 的 `trailing` 与 `arrow` 互斥）——
 *  而删除本来就不是高频动作，放菜单里、再加一道确认，误触代价最小。
 *
 *  「系统场景」与「独立设置」都是**进下一页**（同一组，压在上面；场景在前、独立设置在后，
 *  与「按键绑定」页那份菜单同一口径）；「停用 / 启用」与
 *  「按键绑定」页那三行**同一套**（见 `BindingKeyMenu` 的 `keyActionEntries`）：
 *  停用在前、删除压最下面；它写的是这一档自己的 `ComboKey.enabled`，与模块总闸是两层。
 *
 *  删完**退回列表**：这一页的内容已经不存在了，留在原地只剩一个空壳。
 */
@Composable
internal fun SettingsPage.ComboEditMenu() {
    val cfg = configState.config.value
    val id = ComboEditState.editingId
    val combo = id?.let { cid -> cfg.combos.firstOrNull { it.id == cid } }
    //  确认窗里每个键名各套一对括号（`「侧键」+「电量 +」`）：整串再包一层会读成
    //  「删除『侧键 + 电量 +』」—— 那是把两件东西写成了一个名字。
    val label = combo?.let { comboLabel(cfg, it) }.orEmpty()

    //  三个浮层的开合位（删除 / 停用 / 触发方式）—— 同一时刻至多一个，见 [ComboMenuDialogs]
    val dialogs = remember { ComboMenuDialogs() }
    var open by remember { mutableStateOf(false) }
    val preciseClick: PreciseClickState = rememberPreciseClickState()

    Box {
        HeaderMenuButton(preciseClick) { open = true }

        PageMenu(
            entries =
                comboMenuEntries(
                    id = id,
                    cfg = cfg,
                    open = open,
                    actions =
                        ComboMenuActions(
                            //  与顶栏那颗设置键同一个落点（同一条 `openKeySettings`）
                            onOpenSettings = {
                                open = false
                                id?.let { openKeySettings(SettingsTarget.Combo(it)) }
                            },
                            onOpenBranches = {
                                open = false
                                id?.let(::openBranchesForCombo)
                            },
                            //  启用不必确认（那是把它开回来，没有可失去的东西）；停用先弹窗 ——
                            //  这一行与下面「删除组合按键」只差两个字，后果却是两种。
                            onToggleEnabled = {
                                open = false
                                if (id != null) {
                                    if (combo?.enabled == false) {
                                        comboWriteGuard("启用组合按键") { setComboEnabled(id, true) }
                                    } else {
                                        dialogs.toggle = true
                                    }
                                }
                            },
                            onAskDelete = {
                                open = false
                                dialogs.delete = true
                            },
                        ),
                ),
            preciseClick = preciseClick,
            onDismiss = { open = false },
        )
    }

    ComboMenuDialogHost(id = id, label = label, dialogs = dialogs)
}

/**
 * 这一页那颗 ⋮ 点出来的三个浮层的开合位 —— 同一时刻至多一个。
 *
 *  单独一个对象：`ComboEditMenu` 已经把菜单本身、两处动作说明都扛着了。
 */
private class ComboMenuDialogs {
    var delete by mutableStateOf(false)
    var toggle by mutableStateOf(false)
}

/**
 * 两个确认窗的落点 —— 抽成一支同样只为长度上限；各自「确认之后干什么」写在这里。
 *
 *  @param id 正在编辑的那一档（`null` = 已经退场那一帧，什么都不做）
 */
@Composable
private fun SettingsPage.ComboMenuDialogHost(
    id: String?,
    label: String,
    dialogs: ComboMenuDialogs,
) {
    //  启用这一档时若成员键还有停用的，`setComboEnabled` 会先问一句 —— 窗挂在这里
    //  （与菜单、两个确认窗同一处），三个弹窗共用一份 `KeysAsk` 状态。
    KeysAskDialog()
    ComboDisableDialog(
        show = dialogs.toggle,
        onDismiss = { dialogs.toggle = false },
        onConfirm = {
            dialogs.toggle = false
            id?.let { target -> comboWriteGuard("停用组合按键") { setComboEnabled(target, false) } }
        },
    )
    ComboDeleteDialog(
        show = dialogs.delete,
        label = label,
        onDismiss = { dialogs.delete = false },
        onConfirm = {
            dialogs.delete = false
            id?.let { target ->
                XLog.w("组合键：从编辑页删除 $target")
                //  **写成了才收场**：写失败时这一档还在配置里，页面却退走、编辑目标被清空，
                //  回来只剩一个「找不到」的空壳。
                comboWriteGuard("删除组合按键") {
                    if (removeCombo(target)) {
                        ComboEditState.clear()
                        goBack()
                    }
                }
            }
        },
    )
}

/**
 * 删掉这一档之前的确认窗 —— 抽成一支（它与停用那道窗并排挂）。
 */
@Composable
private fun SettingsPage.ComboDeleteDialog(
    show: Boolean,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        show = show,
        title = I18n.format(Strings.COMBO_DELETE_CONFIRM, label),
        action = I18n.get(Strings.COMBO_DELETE),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 停用这一档组合之前问一句 —— 与「按键绑定」页、场景那两处**同一句规矩**：
 * 停用只是让它不成立，两档动作都还在；而它下面那一行就是「删除组合按键」，
 * 两者只差两个字，不确认很容易点错。
 *
 */
@Composable
private fun SettingsPage.ComboDisableDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        show = show,
        title = I18n.get(Strings.COMBO_DISABLE),
        action = I18n.get(Strings.COMMON_DISABLE),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 这一页 ⋮ 上那四行要干的事 —— 合成一个对象（与 `BindingKeyMenu` 的 `KeyMenuActions` 同一个写法）。
 *
 * @param onOpenSettings 「独立设置」那一行（进另一页；与顶栏那颗设置键同一个落点）
 * @param onOpenBranches 「条件执行」那一行（进另一页）
 * @param onToggleEnabled 「停用 / 启用」那一行（文案与图标跟着当前状态换）
 * @param onAskDelete 「删除组合按键」那一行
 */
private class ComboMenuActions(
    val onOpenSettings: () -> Unit,
    val onOpenBranches: () -> Unit,
    val onToggleEnabled: () -> Unit,
    val onAskDelete: () -> Unit,
)

/**
 * 这一页 ⋮ 的条目。
 *
 *  四行：最上面两行是**进下一页**（独立设置 / 条件执行），一条加深带隔开，下面是
 *  对这一档动手（停用 / 启用、删除），删除压最下面。
 *
 *  「独立设置」**跟着这一档的启停走**：停用的组合不接管按键，这一页改的全是它的覆盖项，
 *  进去改也没有去处（与「按键绑定」页那一行、两页顶栏那颗设置键同一份判据）。
 *  上面「系统场景」不跟 —— 停用期间先把场景表配好、再回去启用，是常见顺序。
 *
 * @param id 正在编辑的那一档（`null` = 刚删完、退场那一帧）
 */
private fun SettingsPage.comboMenuEntries(
    id: String?,
    cfg: Config,
    open: Boolean,
    actions: ComboMenuActions,
): List<MenuEntry> =
    if (!open || id == null) {
        //  一档都没了（刚删完、退场那一帧）就不弹：空菜单在库那层是「不显示」，但 keep 住会
        //  在去列表的路上留一层看不见的浮层。
        emptyList()
    } else {
        val combo = cfg.combos.firstOrNull { it.id == id }
        val enabled = combo?.enabled ?: true
        //  「独立设置」跟着这一档的启停走：停用的组合不接管按键，这一页改的全是它的覆盖项，
        //  进去改也没有去处（用户口径：按键 / 组合键禁用时，设置入口也禁用）。判据与那句提示
        //  都出自 `SettingsTarget.disabledReason`，与「按键绑定」页那一行、两页顶栏那颗设置键
        //  同一份实现。上面「系统场景」不跟：停用期间先把场景表配好，是常见顺序。
        val settingsBlocked = SettingsTarget.Combo(id).disabledReason(cfg)
        listOf(
            //  「系统场景」与下一行同属「进下一页」，与下面那两行不是一类事，用加深带隔开。
            //  顺序（用户口径）：**场景在前、独立设置其次** —— 场景说的是这一档「怎么分流」，
            //  优先级高于「这一档的显示 / 触发怎么调」。
            //  这一行**跟着这一档的启停走**（用户口径：按键 / 组合键禁用时，场景入口也禁用）——
            //  与下一行同一份判据、同一句提示。停用的组合不接管按键，表进去也改不出效果。
            MenuEntry.Row(
                title = BRANCHES_TITLE,
                //  子标题报这一档手势的条数（只数启用的），与编辑页里那条横幅同一份判据
                summary = scenarioMenuSummary(combo?.scenes.orEmpty()),
                icon = PageIcons.Scene,
                submenu = true,
                trailingIcon = PageIcons.EnterArrow,
                enabled = settingsBlocked == null,
                disabledHint = settingsBlocked?.template,
                disabledChips = settingsBlocked?.chips.orEmpty(),
                onPick = actions.onOpenBranches,
            ),
            MenuEntry.Row(
                title = I18n.get(Strings.KEY_SETTINGS_TITLE),
                icon = PageIcons.SettingsCog,
                submenu = true,
                trailingIcon = PageIcons.EnterArrow,
                enabled = settingsBlocked == null,
                disabledHint = settingsBlocked?.template,
                disabledChips = settingsBlocked?.chips.orEmpty(),
                onPick = actions.onOpenSettings,
            ),
            MenuEntry.Row(
                title =
                    if (enabled) {
                        I18n.get(Strings.COMBO_DISABLE)
                    } else {
                        I18n.get(Strings.COMBO_ENABLE)
                    },
                //  停用 / 启用各一颗（锁 / 开锁）：这一行本来就随状态换文案，图标跟着换才一致
                icon = if (enabled) PageIcons.KeyLocked else PageIcons.KeyUnlocked,
                onPick = actions.onToggleEnabled,
            ),
            MenuEntry.Row(
                title = I18n.get(Strings.COMBO_DELETE),
                icon = PageIcons.CommandDelete,
                danger = true,
                onPick = actions.onAskDelete,
            ),
        )
    }

/**
 * 「条件执行」：把这一档组合键指过去再切页。
 *
 *  这里**不** `load`：那一页的闸门是 `draft.owner`，提前写反而会让它判成「已经载入过」而跳过重灌。
 *  用户停在短按页还是长按页，就编哪一档 —— 组合键只有这两档；`Gesture.entries` 是
 *  单击(0) / 双击(1) / 长按(2)，长按不是页号 1，必须按枚举取。
 */
internal fun SettingsPage.openBranchesForCombo(id: String) {
    branchDraft.comboId = id
    branchGesture = if (ComboEditState.page == 0) Gesture.SINGLE.ordinal else Gesture.LONG.ordinal
    XLog.w("组合键：进「$BRANCHES_TITLE」页（$id）")
    navTo(Screen.BRANCHES)
}

// 「拦下这一下的是谁」—— 拦截窗正文里那一串要点名念出来的东西。
//
//  删不掉（挡路的是几档组合）、连带停用（跟着停的是几档组合）、开不了某个开关（挡路的是
//  几档组合）、组合开不起来（还关着的是几颗键）—— 这几张窗原先只在句子里报一个数，
//  用户读完仍不知道自己该去处理哪一条。现在由调用方把**具体那几项**交进来，这里只负责排。

/** 一档一行之间的间距 —— 比段间距小一档，读起来是「同一段里的几个例子」。 */
private val LINE_GAP = 6.dp

/**
 * 几档组合，一档一行、水平居中。
 *
 *  两类写法分开（与组合列表同一套）：等待组合是一串名字（`A 和 B`），按住组合是一句话
 *  （`按住 A 按 B`）—— 只摆一串名字读不出「谁先按住」，而那正是那一档的判据。
 *  有序档念「A 后 B 再 C」（先后），不分顺序是「A 和 B」（一起按下），与列表行同一个判据。
 *
 * @param cfg 取键名用（成员键被删掉的那种畸形配置由 `comboKeyNames` 兜底）
 */
@Composable
internal fun ComboNameLines(
    cfg: Config,
    combos: List<ComboKey>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
    ) {
        combos.forEach { combo ->
            val names = comboKeyNames(cfg, combo)
            val ordered = combo.ordered
            val holdSeparator = if (ordered) comboOrderedAgain else comboMemberSeparator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (combo.isHold) {
                    ChipSentence(
                        template = holdSentenceTemplate(names.size - 1, holdSeparator),
                        names = names,
                        style = style,
                        color = color,
                        //  这一句自己撑满整行（它是 `BasicText`），外层那个 `Arrangement.Center`
                        //  只摆得动盒子、摆不动盒子里的字 —— 按住组合那几档得在这里点名居中
                        textAlign = TextAlign.Center,
                    )
                } else if (ordered) {
                    //  「A 后 B 再 C」：第一颗与第二颗之间「后」，之后每颗前面「再」——
                    //  与纯文字那几处（`comboLabel` / `comboTitleText`）同一个念法。
                    ChipSentence(
                        template = orderedWaitSentenceTemplate(names.size),
                        names = names,
                        style = style,
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    NameFlow(names = names, style = style, color = color)
                }
            }
        }
    }
}

/**
 * 几颗键，一颗一行、水平居中。
 *
 *  与 [ComboNameLines] 并列：那一档报的是「哪几档组合」，这一档报的是「哪几颗键」
 *  （组合开不起来时挡路的是成员键有没有启用，不是哪一档组合）。
 */
@Composable
internal fun KeyNameLines(
    names: List<String>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LINE_GAP),
    ) {
        names.forEach { name ->
            Text(
                text = name,
                style = style.copy(textAlign = TextAlign.Center),
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// 「按键功能」页顶栏右上角那两颗 —— 编辑态多一颗「全选」，与 `PageTopBar` 的 `PageActions`
// 同一位置、同一尺寸（那一格是 `RowScope`，两颗按顺序排、中间隔 [HEADER_ACTION_GAP]）。

/**
 * 顶栏右上角那颗「编辑」—— 按一下进这一页的**编辑态**（列表里每一颗键的行换成多选框，
 * 选中任意一颗就从右下角滑进「删除」；见 [KeysListCard] 与 `KeysScreen`）。
 *
 *  编辑态里它自己换成「完成」（用户口径）：**一颗按钮两档**，退出编辑不用去别处找。
 *  退出时选中一起清掉 —— 「完成」的意思是这一趟改完了，留着选中只会让下次进来先看到一排勾。
 *
 *  编辑态里它**左边**还多一颗「全选」（见 [KeysHeaderSelectAll]）：一列键 + 一列组合都勾上
 *  要点十几下，而「删掉全部自定义键」是这一页最常见的整批操作。
 *
 *  整页闸门关着（总闸关 / 链路没通 / 配置只读）时置灰并说清原因：编辑态的终点是删配置，
 *  写不进去的时候让人先勾一堆再发现删不掉，比一开始就按不动更费事。
 */
@Composable
internal fun SettingsPage.KeysHeaderEdit() {
    val editing = KeysEditState.editing
    val canEdit = pageWritable()
    val iconTint = keysHeaderTint(canEdit)

    //  **只在编辑态出现**：平时这一格没有可勾的东西，摆一颗永远灰着的按钮只是噪音。
    if (editing) KeysHeaderSelectAll(canEdit)

    //  「按一下振一下」由顶栏那一层统一挂（见 `PageHeader` 的 `headerPressTick`），这里不再自己振
    //  字形随编辑态换（编辑 与 完成）：同位置替换**不做淡入淡出**（用户口径），直接换这颗字形；
    //  颜色仍是状态驱动的过渡（`animateColorAsState`），字形与底色同一次到位。
    DisabledHint(if (canEdit) null else disabledClickReason()) {
        HeaderIconButton(
            onClick = { KeysEditState.toggleEditing() },
            enabled = canEdit,
        ) {
            Icon(
                if (editing) PageIcons.Confirm else PageIcons.CommandEdit,
                contentDescription =
                    if (editing) {
                        I18n.get(Strings.COMMON_DONE)
                    } else {
                        I18n.get(Strings.COMMON_EDIT)
                    },
                // 顶栏按钮不给图标上色 ⇒ 禁用态得自己给（与行首图标同一个禁用色）
                tint = iconTint,
            )
        }
    }
}

/**
 * 编辑态里那颗「全选」—— 按一下把两张清单里能勾的**一次勾满**，已经勾满时再按一下**收回**。
 *
 *  「全勾上了没有」由 [KeysEditState.toggleAll] 现算（两份清单 + 当前选中），这一层只管
 *  画哪一档文案：收回那一档如果还写「全选」，用户会以为按了没反应。
 */
@Composable
private fun SettingsPage.KeysHeaderSelectAll(canEdit: Boolean) {
    val cfg = configState.config.value
    val allKeys = cfg.allKeys.map { it.id }.toSet()
    val allCombos = cfg.combos.map { it.id }.toSet()
    val all = KeysEditState.pickedKeys == allKeys && KeysEditState.pickedCombos == allCombos
    //  勾满那一档用**强调色**（与「选择应用」页那颗小窗开关选中时同一套）：这颗按钮说的是
    //  「现在是不是全勾上了」，只看图标本身分不出两档 —— 文案在无障碍里，眼睛看不到。
    val iconTint = keysHeaderTint(canEdit, active = all)
    DisabledHint(if (canEdit) null else disabledClickReason()) {
        HeaderIconButton(
            onClick = { KeysEditState.toggleAll(allKeys, allCombos) },
            enabled = canEdit,
        ) {
            Icon(
                PageIcons.SelectAll,
                contentDescription =
                    if (all) {
                        I18n.get(Strings.KEYS_DESELECT_ALL)
                    } else {
                        I18n.get(Strings.KEYS_SELECT_ALL)
                    },
                tint = iconTint,
            )
        }
    }
    //  间距挂在**左边那颗**身上（与选择应用页那两颗同一套写法）：两颗都带就会叠成两档
    Spacer(Modifier.width(HEADER_ACTION_GAP))
}

/** 顶栏图标在这一页的着色（可点 / 置灰两档，带过渡）—— 两颗按钮共用一份判据。
 *
 *  @param active 这一颗「正生效」（全选已经勾满）—— 走强调色；默认 `false` = 普通那档。
 */
@Composable
private fun keysHeaderTint(
    canEdit: Boolean,
    active: Boolean = false,
): Color =
    animateColorAsState(
        targetValue =
            when {
                !canEdit -> COUITheme.colorScheme.disabledOnSecondaryVariant
                active -> COUITheme.colorScheme.primary
                else -> COUITheme.colorScheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "KeysEditIcon",
    ).value
