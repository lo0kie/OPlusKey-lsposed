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
 *  还有一层菜单都不给的那一档：**录屏**（[ActionCatalog.stateChoices]）。它的格子名就是动作名，
 *  三行是同一个动作的三种状态 —— 中间再夹一张只写着「录屏」的菜单，等于把同一句话说两遍，
 *  所以点格子直接起弹窗（见 [StateChoicesPopup]）。
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

    //  这一格**本身就是那个动作**（录屏）—— 点开直接弹「开始 / 结束 / 切换」那个三行弹窗，
    //  中间不再夹一层只写着「录屏」的菜单：一个动作不该有两层入口（用户口径「网格 / 菜单里
    //  视为一个动作，点击弹 dialog 选」）。判据在 `ActionCatalog.stateChoices`，界面不认 token。
    val stateTile = target?.tile?.takeIf { ActionCatalog.stateChoices(it.token) != null }

    PageMenu(
        preciseClick = preciseClick,
        onDismiss = onDismiss,
        entries =
            if (stateTile != null) {
                //  这一格走弹窗：菜单这一层不许它露面（`PageMenu` 只按 entries 非空才显示）
                emptyList()
            } else {
                target
                    ?.let { t ->
                        ActionCatalog.modeMenu(t.tile.token)?.let { modes ->
                            //  「应用控制」那两行要一枚子标题：当前绑的是哪个应用（用户口径：选中
                            //  「打开应用 / 结束应用」之后再打开这层菜单，标题下面要写出是哪个应用）。
                            //  别的行不查名字 —— `boundAppName` 要走 `PackageManager`（分身那档还问
                            //  `UserManager`），没用到它的菜单不该为它掏 binder。
                            val boundApp =
                                if (t.tile.token == ActionCatalog.APP_CONTROL_TILE) {
                                    //  菜单行宽，要带「具体是哪一个」：这两行的行标题只说打开 / 结束
                                    current?.let { boundAppName(it, withDetail = true) }
                                } else {
                                    null
                                }
                            modeEntries(t.tile.label, modes, current, { spec -> boundRowSummary(spec, current, boundApp) }) { picked ->
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
                                } else if (ActionCatalog.hasDeeperLevel(picked)) {
                                    // 这一行自己还带一层（灵感语音 → 开始 / 结束）：收菜单、起选择器
                                    //  判据**不是** `modeMenu(picked.token) != null`：媒体控制那层菜单的
                                    //  首行（「播放/暂停」）与格子共用同一串 token，那张表就是它自己所在的
                                    //  这一层 —— 照 `modeMenu` 判会再弹一张一模一样的清单（真机反馈
                                    //  「点播放/暂停时弹出个多余的选择器」）。见 `hasDeeperLevel`。
                                    nestedEntry = picked
                                    onDismiss()
                                } else {
                                    onDismiss()
                                    onPick(picked)
                                }
                            }
                        }
                    }.orEmpty()
            },
    )

    // 三条「点一行之后还有一问」的后续，各自一个弹窗组件（拆出去是为了让这一支只留接线，
    //  两块的退场处理、标题取法都各自独立，混在一起时这一支会顶破 detekt 的长度 / 圈复杂度上限）。
    //  录屏那一档在**最前面**：它没有菜单那一层，点格子就是它自己的三行。
    StateChoicesPopup(stateTile, current, onDismiss = onDismiss, onPick = onPick)
    SwitchStatePopup(stateEntry, current, onDismiss = { stateEntry = null }, onPick = onPick)
    BrightnessPopup(brightEntry, current, onDismiss = { brightEntry = null }, onPick = onPick)
    VolumePopup(volumeEntry, current, onDismiss = { volumeEntry = null }, onPick = onPick)
    NestedPickerPopup(nestedEntry, current, onDismiss = { nestedEntry = null }, onPick = onPick)
}

/**
 * 录屏那一档的后续 —— **一行一档的三行单选弹窗**（开始 / 结束 / 切换）。
 *
 *  与 [SwitchStatePopup] 同一个形状（点格子 → 弹窗直接起），区别只有「行从哪来」：
 *  那一边是开关的三档（[ActionCatalog.stateMenu]），这一边是 [ActionCatalog.stateChoices]。
 *
 *  为什么不在菜单里露这三行：菜单意味着「这一格底下还有一层清单」，于是格子行尾挂着选择器
 *  字形、菜单顶上还要写一遍「录屏」这个小标题 —— 而那三行本来就是这个动作的三种状态，
 *  没有新东西可列（用户口径「录屏不用换图标了，改成用选择器选三态」「行尾不用 selector」）。
 *
 *  退场期间照最后画过的那一份画，理由见 [SwitchStatePopup]。
 *  点行时 `SinglePickerDialog` 自己会先调 [onDismiss] 再回调，这一支不必再调一次。
 */
@Composable
private fun SettingsPage.StateChoicesPopup(
    tile: ActionCatalog.Spec?,
    current: Action?,
    onDismiss: () -> Unit,
    onPick: (ActionCatalog.Spec) -> Unit,
) {
    val lastShown = remember { LastPick() }
    tile?.let { lastShown.entry = it }
    val shown = tile ?: lastShown.entry
    val rows = shown?.let { ActionCatalog.stateChoices(it.token) }.orEmpty()
    //  三档之间只差一个 token，逐行精确比即可
    val selectedIndex = rows.indexOfFirst { row -> current?.let { ActionCatalog.isBound(row, it) } == true }
    //  行标题带上这一格的名字（「切换录屏」）：这三行**只在**这里露面，弹窗标题虽然写着「录屏」，
    //  一列动词单看还是不知道说的是哪件事。名字从字典里拿出来拼，不是写死在每档的条目里
    //  （见 `stateRowLabel`）—— 句子那一侧由 `CommandTitle` 拼成「录屏 - 切换」，两处共用
    //  同一份动词。
    val owner = shown?.label.orEmpty()
    SinglePickerDialog(
        show = tile != null,
        title = owner,
        labels = rows.map { row -> stateRowLabel(row, owner) },
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onPick = { index ->
            val row = rows.getOrNull(index) ?: return@SinglePickerDialog
            onPick(row)
        },
    )
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
 *  而不是「换哪个动作」。标题与那几档都由 `entry` 自己那一层菜单（[ActionCatalog.modeMenu]）给，
 *  行名再按 [stateRowLabel] 把父名拼上去（`开始灵感语音`）—— 菜单里那一档的名字只是三段式里的
 *  「状态」那一截（`灵感语音 - 开始`），单独摆在弹窗里认不出开的是什么。界面侧不认 token，
 *  将来再多一层入口也只是加一行。
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
    //  行名与系统开关、录屏三态同一条规矩：**动词 + 宾语**（`开始灵感语音`）。
    //  菜单给的那一档名字只是三段式里的「状态」那一截（`灵感语音 - 开始`），
    //  光看弹窗里两行「开始 / 结束」认不出开的是什么 —— 拼完整短语是这一层的事。
    val owner = shown?.label.orEmpty()
    SinglePickerDialog(
        show = entry != null,
        title = owner,
        labels = rows.map { row -> stateRowLabel(row, owner) },
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
 * 选择器里那一行的文案 —— **动作 + 宾语**（「切换免打扰」「开始录屏」）。
 *
 *  只写「切换」时单看一行不知道切的是谁；几行挨在一起读的时候，名字跟手一点省一次回看标题。
 *  两段都从字典来：动词是那一档自己的名字（`catalog.mode_toggle` = 「切换」、
 *  `catalog.screen_record_start` = 「开始」），宾语是它所属那一格 / 那个开关的名字。
 *
 *  **不再按 token 后缀挑格式串**（原先 `*_on` 走「打开%s」、`*_off` 走「关闭%s」、其余走
 *  「切换%s」）：录屏 / 录音那三档的后缀是 `_start` / `_stop`，落进「其余」那支就变成
 *  「切换录屏」—— 而字典里那条本来就叫「开始录屏」，等于同一个动作在字典与拼装里各写一遍。
 *  现在只认「动词 + 名字」一种形状，词与名之间要不要空格交给译文
 *  （[Strings.COMMON_VERB_NAME_FORMAT]）。
 */
private fun stateRowLabel(
    state: ActionCatalog.Spec,
    ownerName: String,
): String = I18n.format(Strings.COMMON_VERB_NAME_FORMAT, state.label, ownerName)

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
 * @param selectedSummary 选中那一行标题下面的小字（那一行「现状」的一份说明）。每行自己算 ——
 *   三种入口各问各的（见 [boundRowSummary]）。只有**被选中那一行**会显示它：其余行那个位置不放
 *   东西，否则「这一行绑着什么」会读到别的行上去。
 */
private fun modeEntries(
    parentTitle: String,
    modes: List<ActionCatalog.Spec>,
    current: Action?,
    selectedSummary: (ActionCatalog.Spec) -> String? = { null },
    onPick: (ActionCatalog.Spec) -> Unit,
): List<MenuEntry> =
    // 这一层只有「标题 + 一组选项」：小标题下面那条细线与行间细线都由菜单自己加。
    //  组内再要分组靠 `spec.dividerBefore`（那一行前面换成加深带）。
    listOf(
        MenuEntry.Section(
            title = parentTitle,
            rows =
                modes.map { spec ->
                    //  必须用 [ActionCatalog.isBound]（一行一行比），不能用 `isSelected` ——
                    // 后者对整组都算选中，会让菜单同时勾上多项。
                    val bound = current?.let { ActionCatalog.isBound(spec, it) } ?: false
                    MenuEntry.Row(
                        title = spec.label,
                        icon = tileIcon(spec),
                        //  子标题只说「这一行现在是什么样」，且只在它正是当前那一档时给
                        summary = if (bound) selectedSummary(spec) else null,
                        selected = bound,
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
 * 这一行「现在是什么样」—— 只在它被选中时写进子标题。
 *
 *  · 「应用控制」那两行（打开应用 / 结束应用）不是取值，绑的是哪个应用由调用方算好递进来
 *    （[boundApp] 要走 `PackageManager`，只有那一层菜单才查）；
 *  · 「系统开关」那一行（Wi‑Fi / 蓝牙…）→ 绑的那一档：`切换` / `打开` / `关闭`；
 *  · 「灵感语音」那一行 → 绑的是 `开始` 还是 `结束`（那一行自己还带一层，
 *    `boundModeOf` 会取到最里面那一档 —— 只写「灵感语音」看不出是开还是停）。
 *    这里给的是三段式里的**状态**那一截，紧挨着行标题「灵感语音」读；整句
 *    `灵感语音 - 开始` 由 `CommandTitle` 拼，弹窗里那两行才是完整短语（见 `NestedPickerPopup`）。
 *
 *  其余档位（响铃四档 / 媒体五档那些叶子）返回 `null`：行标题本身已经说完了。
 *
 * ## 系统开关为什么不走 [ActionCatalog.boundModeOf]
 *
 *  那一行本身**就是一个开关**（`setting:wifi`），不在 [ActionCatalog.MODE_MENUS] 那张入口格表里
 *  —— `boundModeOf` 查的是「这一格点开的那一层」，对开关行只会返回 `null`（第一版就是这么写的，
 *  真机上「系统开关选中时没有小字」）。它自己的三档要直接问 [ActionCatalog.stateMenu]。
 *  读状态用 `switchStateLabel(current)`（按**动作**那一侧读），逐档 `isBoundExactly` 也能写，
 *  但那样会漏掉老写法（`oplus:no_disturb` 那种 token 与任何一档都不完全相等）。
 */
private fun boundRowSummary(
    spec: ActionCatalog.Spec,
    current: Action?,
    boundApp: String?,
): String? {
    if (current == null) return null
    if (
        spec.token == ActionCatalog.APP_CONTROL_OPEN ||
        spec.token == ActionCatalog.APP_CONTROL_STOP
    ) {
        return boundApp
    }
    //  系统开关那一行：`切换` / `打开` / `关闭`（单档开关没有这一层，`stateMenu` 为 null）
    if (ActionCatalog.stateMenu(spec.token) != null) return ActionCatalog.switchStateLabel(current)
    //  「这一行还带一层」的那些（灵感语音）：取最里面那一档；取到自己就不要（会与行标题重复）
    return ActionCatalog.boundModeOf(spec, current)?.takeIf { it.token != spec.token }?.label
}

/**
 * 这一行点下去是不是**另一张页面** —— 判据是 `ActionCatalog.clickKind`（换页那一档），
 * 这里不认 token。
 *
 *  与「点了弹一层」的行分开：那些行（系统开关 / 亮度 / 音量）后面跟的是弹窗，
 *  行尾挂「下一层」记号说反了；而「打开应用 / 结束应用」确实要换页，行尾该有那颗右箭头。
 *  以前这里自己列 `APP_CONTROL_OPEN` / `APP_CONTROL_STOP` 两个 token —— 那是清单的答案，
 *  不是界面的判断，多一处就多一个漂移点。
 */
private fun opensAppPicker(spec: ActionCatalog.Spec): Boolean =
    when (ActionCatalog.clickKind(spec)) {
        ActionCatalog.ClickKind.PAGE_APP -> true
        else -> false
    }
