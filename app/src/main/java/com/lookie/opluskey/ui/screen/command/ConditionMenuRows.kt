package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.conditionChoiceIcon
import com.lookie.opluskey.ui.model.conditionChoiceLabel
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionKindLabel

// 条件菜单那两层各自的行 —— 一级「选种类」、二级「选取值」。
// 从 `CommandBranchMenus.kt` 拆出来：那边是「菜单什么时候开、二级挂在哪儿」，
// 这边只是「一行怎么画」，两件事各自涨。

/**
 * 一级菜单那几行 —— 一种条件一行，`used` 里的置灰、`current` 那一档打勾。
 *
 *  抽成一支只为把 [BranchConditionMenu] 压回 `LongMethod` 的阈值：这里只有「行怎么画」，
 *  与「菜单开没开、二级挂在哪儿」无关。
 *
 * @param current 这一条现在那一档（画成选中）；`-1` 那一趟（再加一条）传 `null`
 * @param disabled 表里**取值已经全用掉**的档（点一下只说「这个条件已经加过了」）
 * @param onPick 种类定下来之后的落盘（挑应用页 / 时间窗 / 直接写，见 [BranchConditionMenu] 的 `commit`）
 * @param onSub 要开二级取值的那一档
 */
internal fun conditionKindRows(
    current: ConditionKind?,
    disabled: Set<ConditionKind>,
    onPick: (ConditionKind, String) -> Unit,
    onSub: (ConditionKind) -> Unit,
): List<MenuEntry.Row> =
    ConditionKind.entries.map { kind ->
        MenuEntry.Row(
            title = conditionKindLabel(kind),
            icon = conditionIcon(kind),
            // 取值全用掉了才置灰（本条自己原来那档不计入 —— 它算「还是它」）
            enabled = kind !in disabled,
            disabledHint = I18n.get(Strings.COMMAND_BRANCH_DUPLICATE, "这个条件已经加过了"),
            // 这一条现在是这一档 ⇒ 画成选中（行首对勾 + 文字主题色）
            selected = kind == current,
            // 每一行点下去都还有下一层（跳页面 / 弹二级取值）—— 行尾那格一律留出
            submenu = true,
            trailingIcon = if (kind == ConditionKind.APP) PageIcons.EnterArrow else PageIcons.Selector,
        ) {
            // 「前台应用」先去挑是哪个应用（进另一页）、「时间区间」弹时间窗 —— 这两档都没有
            //  可列的候选；其余每一档都开二级选值。
            //  关一级菜单由调用点做：它得先读 `condAt` 才知道落哪一条。
            if (kind == ConditionKind.APP || kind == ConditionKind.TIME) onPick(kind, "") else onSub(kind)
        }
    }

/**
 * 取值那一层的行 —— [ConditionKind.choices] 非空的那几档用（WIFI / 媒体状态 / 响铃状态 / 屏幕方向…）。
 *
 *  **用过的取值**在这一层挡，而不是把整档在一级灰掉（理由见 [BranchConditionMenu] 里那段注释）：
 *  同一张表里「屏幕状态 = 亮屏」可以出现一次，「锁屏」是另一条，两条都该能加。
 *
 * @param currentValue 这一档**现在**的取值（用来画选中）；空值由调用方按默认档给
 * @param used 表里已经用掉的「条件 + 取值」（不含正在改的这一条）
 */
internal fun conditionChoiceRows(
    kind: ConditionKind,
    currentValue: String,
    used: Set<UsedCondition>,
    onPick: (String) -> Unit,
): List<MenuEntry.Row> =
    kind.choices.map { value ->
        MenuEntry.Row(
            title = conditionChoiceLabel(kind, value),
            icon = conditionChoiceIcon(kind, value),
            // 这一档这一个取值已经有一条了 ⇒ 置灰（重复的两条永远轮不到后一条）
            enabled = UsedCondition(kind, value) !in used,
            disabledHint = I18n.get(Strings.COMMAND_BRANCH_DUPLICATE_VALUE, "这个取值已经加过了"),
            selected = value == currentValue,
            onPick = { onPick(value) },
        )
    }
