package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.present.comboKeyNames
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.PickerOption
import com.lookie.opluskey.ui.dialog.SinglePickerDialog
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.write.BindingTarget

// 「按法」那一族 —— 同一档手势底下有三种按法（等窗口 / 先按住 A / 先按住 B），换的是
// **正在编哪一张动作表**。
//
// 为什么不排成页签：三种按法不是三条并列的手势，是**同一条手势的三种按法**
// （`ComboKey.actionOfHold` 那一段说清了三种按法各自的动作槽）。排成四页会读成「短按 / 长按 /
// 先按 A 短按 / 先按 A 长按」四个平级的东西，页签越往后越难和「我到底在编哪一档」对上。
//
// 所以页签仍然只有「短按 / 长按」两条（`bindingTargetsOf`），按法收成**右下角那颗 FAB**
// （`BindingFab` 里叠在「新增命令」上面那颗）：点它弹一个单选窗，三行就是三种按法。
// 手势是主语，按法是修饰；动作槽各自独立（`single`/`long` 与 `singleA`/`longA` 与
// `singleB`/`longB`），换一档就是在换那一套 —— 换完网格按新的一套读写。
//
// 三种按法归**两条触发方式**管：等窗口看「使用等待手势」，另两种看「使用按住手势」——
// 那两颗开关在公共设置与某一组的独立设置里各有一处（这里只读生效值）。

/**
 * 这一档组合**当前能选哪几种按法**（顺序即弹窗里的行序）。
 *
 *  等窗口只在「使用等待手势」开着时出现，两种按住顺序只在「使用按住手势」开着、且成员正好
 *  两颗时出现 —— 判定侧那两条判据不成立时，列出来也只是「点了没反应」。
 *
 *  两处调用（FAB 显不显示、弹窗列哪几行）都只走这一份：分头写一次就会出现「FAB 在、
 *  弹窗里只有一行」这种错位。
 */
internal fun Config.comboPressModes(combo: ComboKey): List<Int> =
    buildList {
        if (comboWindowOnFor(combo)) add(ComboKey.HOLD_WINDOW)
        if (comboHoldFor(combo) && combo.keys.size == 2) addAll(HOLD_ORDERS)
    }

/** 两种按住顺序 —— 与 `ComboKey.HOLD_FIRST` / `ComboKey.HOLD_SECOND` 同一份清单。 */
private val HOLD_ORDERS = listOf(ComboKey.HOLD_FIRST, ComboKey.HOLD_SECOND)

/**
 * 「按法」选择窗 —— 三行：按下 A 或 B 后等另一颗 / 按住 A 后再按 B / 按住 B 后再按 A。
 *
 *  点一行就换到那一套动作表（并且关窗）。**还没添加过的按住顺序照常占一行**：点它就是
 *  「加这一种顺序」—— 先弹那一问「把先按住的那颗改成长按『松手时触发』」，确认后落盘并切过去
 *  （见 [addHoldOrder] 与 `HoldGestureAsk`）；取消则什么都不写，弹窗也不会自己再来一次。
 *
 *  弹窗与 [HoldGestureAskDialog] 一起挂在这里：后者是「选了一行之后」的那一问，两者同生同灭。
 *
 * @param cfg 当前配置（取这一组的成员键名、两条触发方式的生效值）
 * @param target 这一页当前那一档 —— `target.hold` 就是**现在选中**的那一种按法
 */
@Composable
internal fun SettingsPage.ComboPressModeDialog(
    show: Boolean,
    cfg: Config,
    target: BindingTarget.Combo,
    onDismiss: () -> Unit,
) {
    //  「这一种顺序需要松手时触发」那一问 —— 只在**组合动作页**挂一次（顺序是这一页的东西）。
    //  放在下面那段提前退之前：顺序还在加的时候，选择窗已经关了。
    HoldGestureAskDialog()
    val combo = cfg.combos.firstOrNull { it.id == target.id } ?: return
    if (!show) return
    //  成员名取不出来时（手改坏的配置里某颗键认不出）用这一档的 id 顶上：
    //  留一个空的 `[]` 比写 `cmb1` 更让人摸不着头脑。
    val names = comboKeyNames(cfg, combo)
    val pair = listOf(names.getOrNull(0) ?: combo.id, names.getOrNull(1) ?: combo.id)
    val modes = cfg.comboPressModes(combo)
    //  一档都选不了时这一窗没有意义（FAB 那边也不会露出来）：什么都不画，让外面那次点击落空 ——
    //  这里**不能**顺手调 `onDismiss`，那是在组合期改状态。
    if (modes.isEmpty()) return
    //  `holdOf` 夹过 `target.hold`；这里再夹一次，免得配置刚被改时选中行落到表格外面
    val selected = modes.indexOf(target.hold).coerceAtLeast(0)
    val page = COMBO_GESTURES.indexOf(target.gesture).coerceAtLeast(0)
    SinglePickerDialog(
        show = true,
        title = I18n.get(Strings.COMBO_PRESS_MODE_TITLE, "按法"),
        //  键名走**名字框**（`PickerOption.chips`）：这个界面里键名一直是一枚圆角框，
        //  拼进文案就长出第二种长相（`按下 [快捷键] 或 [音量加] 后等另一颗`）。
        options = modes.map { PressModeOption.of(it, pair) },
        selectedIndex = selected,
        onDismiss = onDismiss,
        onPick = { index ->
            val hold = modes.getOrNull(index) ?: return@SinglePickerDialog
            //  没加过的顺序：先走那一问（确认之后 `writeHoldOrder` 自己会切过来，回执是
            //  「已添加 …」）；已经加过的（以及等窗口那一档）只是换正在编的那张动作表。
            if (hold != ComboKey.HOLD_WINDOW && !combo.hasHoldOrder(hold)) {
                addHoldOrder(combo, hold)
            } else if (hold != target.hold) {
                ComboEditState.selectHold(page, hold)
                //  换了按法要说一句：这一窗关了之后，页面上唯一的变化是格子里那几行字
                //  （它们分属三套动作，长得都一样），不说一声就像「点了没反应」。
                //  键名在这一句里也是**名字框**（`hint` 的 `chips`）：与这一窗那几行同一个长相。
                //  与那一组其余项共用同一个回执位（`HINT_SLOT_COMBO`）。
                hint(
                    pressModeSwitchedTemplate(hold),
                    slot = HINT_SLOT_COMBO,
                    chips = PressModeOption.chipOrder(hold, pair),
                )
            }
        },
    )
}

/**
 * 「已切换按法：……」那一句的模板 —— 前缀与三行里同一份模板拼起来（`%s` 留给名字框）。
 *
 *  拼而不是另写三条整句：语序只在 [PressModeOption] 那两份模板里定义一次，这里改不了它。
 */
private fun pressModeSwitchedTemplate(hold: Int): String =
    I18n.get(Strings.COMBO_PRESS_MODE_SWITCHED, "已切换按法：") +
        PressModeOption.template(hold)

/**
 * 一种按法在弹窗里那一行 —— 文案是**带占位符的模板**，键名由 [PickerOption.chips] 画成名字框。
 *
 *  · 等窗口：`按下 %s 或 %s 后等另一颗`
 *  · 先按住第一颗：`按住 %s 后再按 %s`
 *  · 先按住第二颗：`按住 %s 后再按 %s`（两个参数对调，模板只有一份）
 */
private object PressModeOption {
    /** 这一种按法的文案模板（`%s` 就是键名）。 */
    fun template(hold: Int): String =
        if (hold == ComboKey.HOLD_WINDOW) {
            I18n.get(Strings.COMBO_PRESS_MODE_WINDOW, "按下 %s 或 %s 后等另一颗")
        } else {
            I18n.get(Strings.COMBO_PRESS_MODE_HOLD, "按住 %s 后再按 %s")
        }

    /** 模板里那两个位置该填哪两颗键 —— 「先按住第二颗」那一档顺序对调。 */
    fun chipOrder(
        hold: Int,
        names: List<String>,
    ): List<String> {
        val first = names.getOrNull(0).orEmpty()
        val second = names.getOrNull(1).orEmpty()
        return if (hold == ComboKey.HOLD_SECOND) listOf(second, first) else listOf(first, second)
    }

    fun of(
        hold: Int,
        names: List<String>,
    ): PickerOption = PickerOption(label = template(hold), chips = chipOrder(hold, names))
}
