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
 *  而系统开关那三档是在问「这个开关**怎么用**」（取反 / 定成开 / 定成关），是个需要看清、
 *  选完才作数的决定，不是随手划一下的清单；叠第二层菜单时它挤在手指旁边、和第一层抢地方，
 *  两层窗口互相抢焦点还要额外的守卫。所以那一层改成**模态弹窗**：标题写开关名、一行一个状态，
 *  当前绑的那一行行首换成对勾、整行走主色。
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
 * @param target 哪一格开着（`null` = 没开），连同「这一层要不要给只在长按成立的行」
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

    PageMenu(
        preciseClick = preciseClick,
        onDismiss = onDismiss,
        entries =
            target
                ?.let { t ->
                    ActionCatalog.modeMenu(t.tile.token, t.withLongOnly)?.let { modes ->
                        modeEntries(t.tile.label, modes, current) { picked ->
                            // 这一档还有后续：收菜单、起弹窗（顺序反了会闪一帧空菜单）
                            if (ActionCatalog.stateMenu(picked.token) != null) {
                                stateEntry = picked
                                onDismiss()
                            } else {
                                onDismiss()
                                onPick(picked)
                            }
                        }
                    }
                }.orEmpty(),
    )

    // —— 系统开关的「后续写法」——
    //  单档的开关（手电筒 / 免打扰，执行只有「切换」一种语义）走不到这里：`stateMenu` 在
    //  只有一条时返回 `null`，它们在上一层点一下就是结论。
    //
    //  标题与那几行都照 [shown] 画，**不是照 `stateEntry`**：关窗是「先清数据、再播退场」，
    //  `stateEntry = null` 当帧 `states` 就空了、标题也空，而库的退场还要走两百毫秒 ——
    //  那一截里面板会塌成一条（真机反馈「选中器关闭时高度塌陷」）。所以退场期间照最后画过的
    //  那一份画，与 `DonateDialog` 的 `Last` 同一个理由。
    val lastPick = remember { LastPick() }
    stateEntry?.let { lastPick.entry = it }
    val shown = stateEntry ?: lastPick.entry
    val states = shown?.let { entry -> ActionCatalog.switchStates(entry.token) }.orEmpty()
    val switchName = shown?.label.orEmpty()
    //  当前绑的是哪一行 —— 判据与各页网格同一把尺子：必须精确到状态（`isBoundExactly`），
    //  这几行之间只差一个后缀，用 `isBound`（把同一个开关的几档当一回事）会全部打勾。
    val selectedIndex =
        states.indexOfFirst { state ->
            current?.let { ActionCatalog.isBoundExactly(state, it) } ?: false
        }
    //  单选选择器（公共组件，见 `PagePicker`）：点一行就选完、自己关窗。
    //  退场期间画的是 `shown`（最后画过的那一份），不是 `stateEntry` —— 理由见上面那段说明。
    SinglePickerDialog(
        show = stateEntry != null,
        title = switchName,
        labels = states.map { state -> stateRowLabel(state, switchName) },
        selectedIndex = selectedIndex,
        onDismiss = { stateEntry = null },
        onPick = { index ->
            val state = states.getOrNull(index) ?: return@SinglePickerDialog
            onDismiss()
            onPick(state)
        },
    )
}

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
                        //  行尾**不留指示位**：点了还有后续的那几行（系统开关）后面跟着的是弹窗，
                        //  不是第二层菜单，行尾再挂个「下一层」记号说反了；不留这一格，行也算得宽。
                        dividerBefore = spec.dividerBefore,
                        onPick = { onPick(spec) },
                    )
                },
        ),
    )
