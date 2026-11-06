package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.ui.model.tileIcon
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 入口格那一层浮层 —— 「入口格的档位」加上「系统开关再往下的那一问」，全项目只此一份。
 *
 *  出现过的几处（网格的入口格 / 「添加命令」抽屉 / 条件执行的动作菜单）本来就是同一件事：
 *  点一格 → 列出它那几档 → 其中某个开关还要再问一句「切换 / 打开 / 关闭」。
 *
 * ## 第一层是菜单，第二层是**弹窗**
 *
 *  档位是「挑一个」—— 贴着手指弹出来、点一下就收，菜单最合适。
 *  而系统开关那三档（取反 / 定成开 / 定成关）与灵感语音的开始 / 结束，问的都不是「换哪个动作」，
 *  而是**这一档怎么用** —— 需要看清、选完才作数的决定，不是随手划一下的清单；叠第二层菜单时
 *  它挤在手指旁边、和第一层抢地方，两层窗口互相抢焦点还要额外的守卫。所以那一层改成
 *  **模态弹窗**：标题写这一档的名字、一行一个取值，当前绑的那一行行尾换成对勾、整行走主色。
 *
 *  点那一行时菜单**先收**，弹窗紧接着起来：菜单留着的话它压在弹窗底下、还带着自己的锚点与
 *  退场动画，两层叠在一起只会显得脏；直接换成一个居中的弹窗，视线也就跟着定下来了。
 *
 * ## 调用方只表达两件事
 *
 *  · [target] 是哪一格开着（`null` = 关）：行与小标题都由它算，调用方不用挑模式；
 *  · [onPick] 选中了哪一档（弹窗里那三行也是同一个回调）。
 *
 *  哪一行点下去还有后续由模型回答（[ActionCatalog.stateMenu]）—— 界面侧不认 token。
 *
 * @param preciseClick 弹层锚点：网格传那一格自己的，抽屉里传行内触点
 * @param target 哪一格开着（`null` = 没开）
 * @param current 当前绑的动作（`null` = 不知道，谁都不打勾）
 * @param onDismiss 这一格流程该收起时调（含「点空白处」）。弹窗自己那一层不经过它
 */
@Composable
internal fun SettingsPage.ModeMenuPopups(
    preciseClick: PreciseClickState,
    target: ModeMenuTarget?,
    current: Action?,
    onPick: (ActionCatalog.Spec) -> Unit,
    onDismiss: () -> Unit,
) {
    // 弹窗开着的是哪一档（`null` = 没开）。只在组件内部流转，调用方看不见也不用管。
    //  它**不跟着 `target` 清**：菜单在点开弹窗那一下就已经收了，两者本来就不同生共死。
    var stateEntry by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    // 亮度那一行点开的是哪一档（`null` = 没开）—— 与 `stateEntry` 同一套，两条线互不相干。
    var brightEntry by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    // 音量那一行点开的是哪一档 —— 与 `brightEntry` 完全平行的一条线
    var volumeEntry by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    // 点开的是「还要挑一档」的那一行（灵感语音：开始 / 结束）—— 同一套：菜单先收、选择器再起
    var nestedEntry by remember { mutableStateOf<ActionCatalog.Spec?>(null) }

    PageMenu(
        preciseClick = preciseClick,
        onDismiss = onDismiss,
        entries =
            target
                ?.let { t ->
                    ActionCatalog.modeMenu(t.tile.token)?.let { modes ->
                        modeEntries(t.tile.label, modes, current) { picked ->
                            // 这一档还有后续：收菜单、起弹窗（顺序反了会闪一帧空菜单）。
                            // 两种后续都在这两支里：亮度的百分比（滑杆）、系统开关的三态（选择器）。
                            if (ActionCatalog.brightnessModeOf(picked.token) != null) {
                                brightEntry = picked
                                onDismiss()
                            } else if (ActionCatalog.volumeModeOf(picked.token) != null) {
                                volumeEntry = picked
                                onDismiss()
                            } else if (ActionCatalog.stateMenu(picked.token) != null) {
                                stateEntry = picked
                                onDismiss()
                            } else if (ActionCatalog.modeMenu(picked.token) != null) {
                                // 这一行自己还带一层（灵感语音 → 开始 / 结束）：收菜单、起选择器
                                nestedEntry = picked
                                onDismiss()
                            } else {
                                onDismiss()
                                onPick(picked)
                            }
                        }
                    }
                }.orEmpty(),
    )

    // 两条「点一行之后还有一问」的后续，各自一个弹窗组件（拆出去是为了让这一支只留接线，
    //  两块的退场处理、标题取法都各自独立，混在一起时这一支会顶破 detekt 的长度 / 圈复杂度上限）。
    SwitchStatePopup(stateEntry, current, onDismiss = { stateEntry = null }, onPick = onPick)
    BrightnessPopup(brightEntry, current, onDismiss = { brightEntry = null }, onPick = onPick)
    VolumePopup(volumeEntry, current, onDismiss = { volumeEntry = null }, onPick = onPick)
    NestedPickerPopup(nestedEntry, current, onDismiss = { nestedEntry = null }, onPick = onPick)
}

/**
 * 系统开关的「后续写法」—— 切换 / 打开 / 关闭，单选即结论。
 *
 *  单档的开关（手电筒 / 免打扰，执行只有「切换」一种语义）走不到这里：`stateMenu` 在
 *  只有一条时返回 `null`，它们在上一层点一下就是结论。
 *
 *  标题与那几行都照 `shown` 画，**不是照 [entry]**：关窗是「先清数据、再播退场」，
 *  `entry = null` 当帧 `states` 就空了、标题也空，而库的退场还要走两百毫秒 ——
 *  那一截里面板会塌成一条（真机反馈「选中器关闭时高度塌陷」）。所以退场期间照最后画过的
 *  那一份画，与 `DonateDialog` 的 `Last` 同一个理由。
 */
@Composable
private fun SettingsPage.SwitchStatePopup(
    entry: ActionCatalog.Spec?,
    current: Action?,
    onDismiss: () -> Unit,
    onPick: (ActionCatalog.Spec) -> Unit,
) {
    val lastPick = remember { LastPick() }
    entry?.let { lastPick.entry = it }
    val shown = entry ?: lastPick.entry
    val states = shown?.let { it1 -> ActionCatalog.switchStates(it1.token) }.orEmpty()
    val switchName = shown?.label.orEmpty()
    //  当前绑的是哪一行 —— 判据与各页网格同一把尺子：必须精确到状态（`isBoundExactly`），
    //  这几行之间只差一个后缀，用 `isBound`（把同一个开关的几档当一回事）会全部打勾。
    val selectedIndex =
        states.indexOfFirst { state ->
            current?.let { ActionCatalog.isBoundExactly(state, it) } ?: false
        }
    //  单选选择器（公共组件，见 `PagePicker`）：点一行就选完、自己关窗。
    //  退场期间画的是 `shown`（最后画过的那一份），不是 [entry] —— 理由见上面那段说明。
    SinglePickerDialog(
        show = entry != null,
        title = switchName,
        labels = states.map { state -> stateRowLabel(state, switchName) },
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onPick = { index ->
            val state = states.getOrNull(index) ?: return@SinglePickerDialog
            onDismiss()
            onPick(state)
        },
    )
}

/**
 * 点开的是「还有一档要挑」的那一行（此刻只有灵感语音：开始 / 结束）—— 一格单选弹窗。
 *
 *  与 [SwitchStatePopup] 同一个形状（菜单先收、弹窗再起）：它问的也是「这一行到底怎么用」，
 *  而不是「换哪个动作」。行与标题都由 `entry` 自己那一层菜单（[ActionCatalog.modeMenu]）给 ——
 *  界面侧不认 token，将来再多一层入口也只是加一行。
 *
 *  退场期间照最后画过的那一份画，理由见 [SwitchStatePopup]。
 */
@Composable
private fun SettingsPage.NestedPickerPopup(
    entry: ActionCatalog.Spec?,
    current: Action?,
    onDismiss: () -> Unit,
    onPick: (ActionCatalog.Spec) -> Unit,
) {
    val lastPick = remember { LastPick() }
    entry?.let { lastPick.entry = it }
    val shown = entry ?: lastPick.entry
    val rows = shown?.let { ActionCatalog.modeMenu(it.token) }.orEmpty()
    //  两档之间只差一个 token，逐行精确比即可（不像系统开关那样还差一截后缀、要走 `isBoundExactly`）
    val selectedIndex = rows.indexOfFirst { row -> current?.let { ActionCatalog.isBound(row, it) } == true }
    SinglePickerDialog(
        show = entry != null,
        title = shown?.label.orEmpty(),
        labels = rows.map { row -> row.label },
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onPick = { index ->
            val row = rows.getOrNull(index) ?: return@SinglePickerDialog
            onDismiss()
            onPick(row)
        },
    )
}

/**
 * 亮度的「还要问一个百分比」—— 一根滑杆，确定之后才落盘。
 *
 *  与 [SwitchStatePopup] 同一个形状（菜单先收、弹窗再起），退场期间同样照最后画过的那一份画
 *  （否则标题与数值会空一帧，面板塌陷）。
 */
@Composable
private fun SettingsPage.BrightnessPopup(
    entry: ActionCatalog.Spec?,
    current: Action?,
    onDismiss: () -> Unit,
    onPick: (ActionCatalog.Spec) -> Unit,
) {
    val lastShown = remember { LastPick() }
    entry?.let { lastShown.entry = it }
    val shown = entry ?: lastShown.entry
    val mode = ActionCatalog.brightnessModeOf(shown?.token.orEmpty())
    //  初值：同一档已经绑过就用它自己的数值（打开就能在原来那个位置接着调），
    //  没绑过才用各档的默认值 —— 每次都重置成 10 会让「改成 25、再微调」变成两步。
    val initial =
        mode?.let { m ->
            (current as? Action.Brightness)?.takeIf { it.mode == m }?.percent
                ?: if (m == Action.Brightness.Mode.FIX) DEFAULT_SET_PERCENT else DEFAULT_STEP_PERCENT
        } ?: DEFAULT_STEP_PERCENT
    BrightnessDialog(
        show = entry != null,
        mode = mode ?: Action.Brightness.Mode.ADD,
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = { percent ->
            onDismiss()
            //  mode 为空只可能是「退场途中又点了一次确定」，那种情况下什么都不落
            if (mode != null) onPick(ActionCatalog.brightnessSpecOf(mode, percent))
        },
    )
}

/**
 * 音量的「还要问一个百分比」—— 与 [BrightnessPopup] 同一个形状，只是换成 [SettingsPage.VolumeDialog]。
 *
 *  两块的退场处理各自独立（照最后画过的那一份画），理由见 [SwitchStatePopup]。
 */
@Composable
private fun SettingsPage.VolumePopup(
    entry: ActionCatalog.Spec?,
    current: Action?,
    onDismiss: () -> Unit,
    onPick: (ActionCatalog.Spec) -> Unit,
) {
    val lastShown = remember { LastPick() }
    entry?.let { lastShown.entry = it }
    val shown = entry ?: lastShown.entry
    val mode = ActionCatalog.volumeModeOf(shown?.token.orEmpty())
    //  同一档已经绑过就接着用它自己的数值，没绑过才用各档的默认值
    val initial =
        mode?.let { m ->
            (current as? Action.Volume)?.takeIf { it.mode == m }?.percent
                ?: if (m == Action.Volume.Mode.FIX) DEFAULT_SET_PERCENT else DEFAULT_STEP_PERCENT
        } ?: DEFAULT_STEP_PERCENT
    VolumeDialog(
        show = entry != null,
        mode = mode ?: Action.Volume.Mode.ADD,
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = { percent ->
            onDismiss()
            //  mode 为空只可能是「退场途中又点了一次确定」，那种情况下什么都不落
            if (mode != null) onPick(ActionCatalog.volumeSpecOf(mode, percent))
        },
    )
}

/** 亮度 / 音量「增加 / 减少」的默认百分比。 */
private const val DEFAULT_STEP_PERCENT = 10

/** 亮度「设置为」的默认百分比。 */
private const val DEFAULT_SET_PERCENT = 50

/**
 * 退场期间要画的那一份（见 [ModeMenuPopups] 里那段说明）。
 *
 *  只留一个格子就够：标题与那几行都由 `entry` 现算，留着它等于留住了整块面板。
 */
private class LastPick {
    var entry: ActionCatalog.Spec? = null
}

/**
 * 选择器里那一行的文案 —— **动作 + 开关名**（「切换免打扰」）。
 *
 *  只写「切换」时单看一行不知道切的是谁；三行挨在一起读的时候，名字跟手一点省一次回看标题。
 *  三种写法各给一条**带 `%s` 的字典条目**，不做字符串拼接：英文那边词与名之间要空格，
 *  中文不要，这个差别只能由译文自己带。
 */
private fun stateRowLabel(
    state: ActionCatalog.Spec,
    switchName: String,
): String =
    when {
        state.token.endsWith(Action.ON_SUFFIX) -> I18n.get(Strings.CATALOG_MODE_TURN_ON_OF, "打开%s")
        state.token.endsWith(Action.OFF_SUFFIX) -> I18n.get(Strings.CATALOG_MODE_TURN_OFF_OF, "关闭%s")
        else -> I18n.get(Strings.CATALOG_MODE_TOGGLE_OF, "切换%s")
    }.format(switchName)

/**
 * 入口格那一层菜单的行（响铃四档 / 媒体五档 / 系统开关那串）。
 *
 *  住在**组件层**而不是某个屏里：网格的入口格、「添加命令」的二级、条件执行的动作菜单都用它，
 *  三处各写一份的话「这一行点了还有没有下一层」的判据就会走形（`ui` 也不许反过来认识具体屏）。
 *
 * 第一行是**小标题**（库的 `DropdownHeaderItem`），写的是**父标题** —— 也就是一级菜单里
 * 那一行的标题（响铃状态 / 媒体控制 / 系统导航 / 系统按键）。这一层是压在一级上面的另一张浮层，
 * 没有这行小标题时只看得到「静音 / 振动」这几项，认不出自己是点哪个入口进来的。
 *
 * 行之间的分隔线按行自己带的那一档给（[ActionCatalog.Spec.dividerBefore]）：标了的用**加深带**
 * 把菜单切成两段（媒体控制的「播放 / 暂停 / 播放 / 暂停」与「上一首 / 下一首」、响铃状态的
 * 「循环」与那三档），其余仍是相邻两行那条细线。
 *
 * @param parentTitle 父标题（一级菜单里那一行的标题）
 * 每一行的字形按需取（[tileIcon]）—— 这一层只建真被画到的那些行。
 * @param current 当前绑的动作（`null` = 不知道），用来把那一档标出来。
 */
private fun modeEntries(
    parentTitle: String,
    modes: List<ActionCatalog.Spec>,
    current: Action?,
    onPick: (ActionCatalog.Spec) -> Unit,
): List<MenuEntry> =
    // 这一层只有「标题 + 一组选项」：小标题下面那条细线与行间细线都由菜单自己加。
    //  组内再要分组靠 `spec.dividerBefore`（那一行前面换成加深带）。
    listOf(
        MenuEntry.Section(
            title = parentTitle,
            rows =
                modes.map { spec ->
                    MenuEntry.Row(
                        title = spec.label,
                        icon = tileIcon(spec),
                        //  必须用 [ActionCatalog.isBound]（一行一行比），不能用 `isSelected` ——
                        // 后者对整组都算选中，会让菜单同时勾上多项。
                        selected = current?.let { ActionCatalog.isBound(spec, it) } ?: false,
                        //  行尾**默认不留指示位**：点了还有后续的那几行（系统开关 / 灵感语音）后面跟着的是
                        //  弹窗，不是第二层菜单，行尾再挂个「下一层」记号说反了；不留这一格，行也算得宽。
                        //  例外只有「打开应用 / 结束应用」：它们点下去是**另一张页面**（挑应用），
                        //  与网格里那些跳页的格子同一件事 —— 行尾留出那一格，字形用默认右箭头
                        //  （`PageIcons.EnterArrow`，与网格、列表同一颗）。
                        submenu = opensAppPicker(spec),
                        dividerBefore = spec.dividerBefore,
                        onPick = { onPick(spec) },
                    )
                },
        ),
    )

/**
 * 这一行点下去是不是**另一张页面** —— 目前只有「应用控制」那一层菜单的两行
 * （打开应用 / 结束应用，选完都进同一张挑应用页，见 `ActionCatalog.APP_CONTROL_MODES`）。
 *
 *  与「点了弹一层」的行分开判：那些行（系统开关 / 亮度 / 音量）后面跟的是弹窗，
 *  行尾挂「下一层」记号说反了；而这两行确实要换页，行尾该有那颗右箭头。
 *  判据用 token 而不是 glyph：glyph 是「画什么」，与「点了去哪」无关。
 */
private fun opensAppPicker(spec: ActionCatalog.Spec): Boolean =
    spec.token == ActionCatalog.APP_CONTROL_OPEN || spec.token == ActionCatalog.APP_CONTROL_STOP
