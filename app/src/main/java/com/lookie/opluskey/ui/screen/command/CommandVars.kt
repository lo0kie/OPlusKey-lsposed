package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.SYSTEM_VAR_KINDS
import com.lookie.opluskey.core.model.SYSTEM_VAR_TEXT_SUFFIX
import com.lookie.opluskey.core.model.systemVarName
import com.lookie.opluskey.core.model.systemVarRef
import com.lookie.opluskey.core.model.systemVarTextRef
import com.lookie.opluskey.host.action.condition.ConditionProbe
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.dialog.PickerActionDialog
import com.lookie.opluskey.ui.dialog.rememberMenuState
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.icon.conditionIcon
import com.lookie.opluskey.ui.model.text.conditionKindLabel
import com.lookie.opluskey.ui.model.text.conditionValueText
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.RowEndIcon
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.titleWithCount
import com.lookie.opluskey.ui.state.command.CommandUiState
import com.lookie.opluskey.ui.state.page.CommandForm
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Variable
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「命令详情」下面那一档**系统状态变量** —— 把系统场景那几档判定当成只读变量写进命令。
//
//  名单与取值都**不在这里**：名单是 `SYSTEM_VAR_KINDS`（场景条件那一份），取值是
//  `ConditionProbe.current`（场景条件那条判据），文案是 `conditionKindLabel` / `conditionValueText`。
//  这一份只负责「一个入口 + 一层菜单 + 一层选择窗」—— 任何一张名单在这里再写一遍，
//  都等于给「加一种条件」多留一处会漏的地方。

/**
 * 「可用变量」那一档 —— 页面上只有**一行入口**，点开是一层菜单（一行一个系统状态）。
 *
 *  为什么是一层菜单而不是常驻的一列：这些状态是「偶尔插一条」的东西，十一行常驻会把正文输入框
 *  到「命令设置」之间撑得很长；而菜单**每次打开都现读**（`PageMenu(state)` 的行表在开合之间重算），
 *  状态随时在变，进页那一刻的快照本来也不该当真。
 *
 *  菜单里每行的小字就是这一档**此刻**的值（`静音` / `开` / `14:30`）；点一行再问「要哪种写法」
 *  （[VarPicker]）：输出值（`silent`，与场景条件同一套词表）还是文案值（「静音」，跟着界面语言）。
 *  这个选择不该由默认值替用户做 —— 拿去比较的串与弹给人看的字是两回事，选错了界面上看不出来。
 *
 * @param form 单条命令那一份表单 —— 这一档只出现在单条页（多条命令的步骤没有「命令详情」这一栏）
 * @param enabled 排序态里整页控件都停着
 */
@Composable
internal fun SettingsPage.CommandVarsCard(
    form: CommandForm,
    ui: CommandUiState,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val menu = rememberMenuState()
    //  哪一行开着选择窗 —— 与条件菜单同一套「记住最后那一档」：窗在退场时 `picking` 已经是 null，
    //  标题与两行选项还要按它画完那一截，否则弹窗先变成空窗再消失。
    var picking by remember { mutableStateOf<ConditionKind?>(null) }
    var lastKind by remember { mutableStateOf<ConditionKind?>(null) }
    picking?.let { lastKind = it }
    //  小标题说的是这一档的名字，行里说的是「进去能挑什么」—— 两处不写同一句话。
    SectionTitle(I18n.get(Strings.COMMAND_VARS_TITLE, "可用变量"))
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = I18n.get(Strings.COMMAND_VARS_ENTRY_TITLE, "系统状态"),
                    summary = I18n.get(Strings.COMMAND_VARS_ENTRY_DESC, "取值与系统场景条件相同"),
                    //  与变量页那一列同一颗（`variable`）—— 这一档进去挑的就是「某个量此刻是什么」
                    icon = TablerIcons.Outline.Variable,
                    //  行尾是 **selector**（「点了要选点什么」）而不是箭头：这一行不进下一层，
                    //  弹的是一层菜单 + 一层选择窗（字形怎么选见 `RowEndIcon` 的说明）
                    trailing = {
                        RowEndIcon(
                            PageIcons.Selector,
                            tint =
                                if (enabled) {
                                    COUITheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    COUITheme.colorScheme.disabledOnSecondaryVariant
                                },
                        )
                    },
                    enabled = enabled,
                    //  浮层按按下的位置弹：锚点必须挂在这一行上（行内自己那颗图标够不着）
                    modifier = Modifier.preciseClickAnchor(menu.anchor),
                    onClick = { menu.show() },
                ),
            ),
    )
    //  行表只在**开着时**求值（见 `PageMenu(state)`）—— 于是每打开一次都重读一遍状态，
    //  不需要另给一颗刷新键，也就不会留下一份「进页那一刻」的旧值。
    PageMenu(state = menu) {
        listOf(
            MenuEntry.Section(
                //  条数拼进小标题（`可用变量 (8)`）—— 与浮层菜单别处同一写法
                //  （`PopupSectionTitle` / `titleWithCount`），数的是这一层菜单实际列出几行。
                title =
                    titleWithCount(
                        I18n.get(Strings.COMMAND_VARS_TITLE, "可用变量"),
                        SYSTEM_VAR_KINDS.size,
                    ),
                rows =
                    SYSTEM_VAR_KINDS.map { kind ->
                        MenuEntry.Row(
                            title = conditionKindLabel(kind),
                            summary = stateText(kind, ConditionProbe.current(kind, context)),
                            icon = conditionIcon(kind),
                            onPick = {
                                menu.dismiss()
                                picking = kind
                            },
                        )
                    },
            ),
        )
    }
    (picking ?: lastKind)?.let { kind ->
        VarPicker(
            kind = kind,
            show = picking != null,
            onDismiss = { picking = null },
            onPick = { asText ->
                val ref = if (asText) kind.systemVarTextRef else kind.systemVarRef
                ref?.let { insertVar(it, form, ui) }
            },
        )
    }
}

/**
 * 点一行之后那个选择窗 —— 两种写法，各自写出**那一串展开之后的样子**
 * （`输出值（on）` / `文案值（开）`），于是点之前就能看见两种结果长什么样。
 *
 *  两行都不在这里判断「哪种形态该念什么」：直接拿两个名字去问执行侧那一份
 *  （[ConditionProbe.varValue]）—— 于是「时间那一档的输出值是时间戳、文案值是 `14:30`」
 *  这种分叉只有一处，界面上照抄它的结果即可（在这儿另写一遍的话，两行会显示成同一个值）。
 *
 *  形态用 [PickerActionDialog] 那一份（与「变量操作」页同一套）：这一窗问的是「给我哪一种写法」，
 *  不是「当前选的是哪一个」，所以不打勾、也没有当前项 —— 点一行就办、办完就收。
 *
 * @param show `false` = 正在退场（内容照旧画完）
 * @param onPick `true` = 要文案值（`${sys.x.text}`），`false` = 要输出值（`${sys.x}`）
 */
@Composable
private fun SettingsPage.VarPicker(
    kind: ConditionKind,
    show: Boolean,
    onDismiss: () -> Unit,
    onPick: (asText: Boolean) -> Unit,
) {
    val context = LocalContext.current
    //  **开窗这一刻读一次**（键是档位）：与菜单里那一行是同一份判据，前后差几毫秒不影响判断
    val name = kind.systemVarName.orEmpty()
    val output = remember(kind) { ConditionProbe.varValue(name, context) }
    val label = remember(kind) { ConditionProbe.varValue(name + SYSTEM_VAR_TEXT_SUFFIX, context) }
    val missing = I18n.get(Strings.COMMAND_VARS_UNKNOWN, "读取不到")
    PickerActionDialog(
        show = show,
        //  标题就是要插进正文的那一串（`${sys.wifi}`）—— 与下面两行是同一族写法，一眼对得上
        title = kind.systemVarRef.orEmpty(),
        labels =
            listOf(
                I18n.format(Strings.COMMAND_VARS_PICK_RAW, output ?: missing),
                I18n.format(Strings.COMMAND_VARS_PICK_TEXT, label ?: missing),
            ),
        onDismiss = onDismiss,
        onPick = { index -> onPick(index == 1) },
    )
}

/**
 * 这一档此刻的值**在界面上**怎么念 —— 「静音」「开」「14:30」。
 *
 *  走场景条件**唯一**那条翻译（[conditionValueText]）—— 两边各写一份的话，以后加一档取值必有一处
 *  继续念旧词。判不出时说「读取不到」：这时 `${sys.xxx}` 会按原文留在命令里
 *  （见 `SYSTEM_VAR_PREFIX` 那段说明），菜单那一行正好把这件事说出来。
 *
 *  只给菜单的小字用；选择窗那两行不经过这里（它要显示的是**展开之后**的样子，见 [VarPicker]）。
 */
private fun stateText(
    kind: ConditionKind,
    value: String?,
): String =
    if (value.isNullOrBlank()) {
        I18n.get(Strings.COMMAND_VARS_UNKNOWN, "读取不到")
    } else {
        conditionValueText(kind, BranchCondition.of(kind, value)) { it }
    }

/**
 * 把 [ref] **原样接在正文末尾** —— 点一行就是把这一行写的东西插进去（与「支持的命令格式」同一套手感）。
 *
 *  接末尾而不是插在光标处：这里够不着输入框的选区，而「末尾」是唯一说得准的落点 ——
 *  变量本来就是正文的一部分，接上去读着通顺。
 *
 *  **一个字符都不动**：不补分隔空格、也不收掉正文末尾已有的空白。原先这一支在「正文不以空白
 *  结尾」时自己补一个空格 —— 于是 `fluid:bluetooth|` 点一下系统状态就变成
 *  `fluid:bluetooth| ${sys.wifi}`，那个空格不是用户打的（用户口径「找到底哪里多了空格」）。
 *  空白该不该有由用户自己写：`shell:echo ` 末尾那个空格他要就自己打。
 */
private fun insertVar(
    ref: String,
    form: CommandForm,
    ui: CommandUiState,
) {
    val text = form.single
    if (text.length + ref.length > MAX_SINGLE_LENGTH) {
        XLog.w("系统状态变量：正文已经 ${text.length} 字，再加 $ref 会超上限（$MAX_SINGLE_LENGTH），这一下不填")
        return
    }
    XLog.w("系统状态变量：把 $ref 接进「命令详情」")
    form.single = text + ref
    //  与「支持的命令格式」同一条收尾：让输入框那一侧把页面滚回顶部（字接到了哪儿得看得见）。
    //  同样不聚焦 —— 这一点与格式卡同一条口径。
    ui.singleScrollRequest++
}
