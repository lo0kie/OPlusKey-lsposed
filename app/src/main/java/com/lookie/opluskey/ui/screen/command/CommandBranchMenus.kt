package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.BranchDraft
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowDown
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.Plus

// 分叉那几张菜单 —— 第一层（行本身：加条件 / 停用 / 上移 / 下移 / 删除）、条件种类菜单（每种一行）、
// 取值二级。
//
//  **动作那一档不再有菜单**：点动作行进「场景动作」页编那一档（与「按键绑定」页同一张网格、
//  同一条三档手势带），所以这里没有「选命令」那一张了。

/**
 * 两张菜单的装配 —— 一行分两列，各点各的：
 *
 * ```
 * 左列（条件）→ 条件种类菜单 ─┬─ 前台应用 → 进「选择应用」页
 *                             ├─ 其余每一档 → 取值（二级）
 *                             └─ 上移 / 下移 / 删除
 * 右列（动作）→ **换页**（`Screen.SCENE_BINDING`），不弹菜单
 * ```
 */
@Composable
internal fun SettingsPage.BranchMenus(
    /** **这一页**那一份草稿 —— 这一页只有一页，改的就是它的表（见 `BranchScreen`）。 */
    draft: BranchDraft,
    rules: List<SceneRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    BranchConditionFlow(draft = draft, rules = rules, menus = menus, tick = tick)
    BranchRowMenu(draft = draft, rules = rules, menus = menus)
}

/**
 * 行尾那颗「更多」菜单 —— 再加一条条件 / 上移 / 下移 / 删除。
 *
 *  两列各点各的（换条件 / 换动作），这一颗管**这一行本身**：顺序就是判定顺序，
 *  所以要能挪；删也放在这里，不与换值混在一起。
 *
 *  「再加一条条件」也归这一颗而不是条件菜单：它说的是**这一行里多一条条件**（一块可以带好几条，
 *  逐条都要成立），而条件菜单只管「当前那一条是什么」。
 */
@Composable
private fun SettingsPage.BranchRowMenu(
    draft: BranchDraft,
    rules: List<SceneRule>,
    menus: BranchMenuState,
) {
    val at = menus.menuAt
    PageMenu(
        entries =
            buildList {
                //  「添加条件」—— 一块可以带**好几条**条件（逐条都要成立），所以「再加一条」
                //  是这一行的操作，和上移 / 下移 / 删除一起放在这里。
                //  它单占第一段（下面用加深带断开）：那三件是「这一行本身怎么摆」，这一件是
                //  「这一行里的条件再多一条」，两类不同。
                //
                //  **点了直接插一条空条件，不弹菜单**：用户要的是「先占个位」。空条件在执行侧
                //  永不成立（`BranchCondition.isUsable` 假），而 `isReady` 为假会拦住保存 ——
                //  正好是「还没配完」该有的样子；页面上那一行显示「未选择条件」，点它才开菜单。
                //
                //  **这一行还没有可用的条件**就不再插：末尾那条还是空的，或者**一条都还没有**
                //  （那种时候页面上那一行「未选择条件」是**占位**，不是真条件 —— 只看
                //  `lastOrNull()` 的话它返回 null、判不出「已经有待配的那一行」，于是照样插进去，
                //  变成两行「未选择条件」，用户还得分清哪行是刚加的）。
                //  两种情况都给同一句提示，把注意力推回那一行。
                add(addConditionRow(draft, at, menus))
                //  停用 / 启用紧跟在「添加场景」下面（同属「这一行现在怎么样」）——
                //  行本体在 `CommandBranchParts.kt`：这一支的行数已经到了 detekt 的上限。
                add(branchToggleRow(rules, at, menus))
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_MOVE_UP, "上移"),
                        icon = TablerIcons.Outline.ArrowUp,
                        enabled = at > 0,
                        //  禁用的行必须自带说法：不给就会落到整页那条（讲的是模块/宿主链路）
                        disabledHint = I18n.get(Strings.COMMAND_BRANCH_TOP_REACHED, "已经在最上面"),
                        dividerBefore = true,
                    ) {
                        draft.move(at, at - 1)
                        menus.menuAt = -1
                    },
                )
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_MOVE_DOWN, "下移"),
                        icon = TablerIcons.Outline.ArrowDown,
                        enabled = at >= 0 && at < rules.lastIndex,
                        disabledHint = I18n.get(Strings.COMMAND_BRANCH_BOTTOM_REACHED, "已经在最下面"),
                    ) {
                        draft.move(at, at + 1)
                        menus.menuAt = -1
                    },
                )
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_DELETE, "删除"),
                        icon = PageIcons.CommandDelete,
                        danger = true,
                        // 删除与上面两步「挪位置」不是一类：一条加深带隔开
                        dividerBefore = true,
                    ) {
                        // **不在这里删**：删除是这一页唯一不可逆的动作，先收起菜单、弹确认窗
                        //  （窗在 `BranchDeleteDialog`，由 `BranchCards` 挂在页面里）
                        menus.menuAt = -1
                        menus.deleteAt = at
                    },
                )
            }.takeIf { at in rules.indices }
                .orEmpty(),
        preciseClick = menus.menuAnchor,
        onDismiss = { menus.menuAt = -1 },
    )
}

/**
 * 条件那两张菜单与它的落条件动作。
 *
 *  一级选**种类**、二级选**那一档取值**，两级拼成一次完整的选择：「响铃状态」+「振动」。
 *  二级是**浮层**（一级还留着，挂在它上面），不是先关一次再开一次 —— 那两次的开合在真机上
 *  就是「菜单闪一下又换了一张」。
 */
@Composable
private fun SettingsPage.BranchConditionFlow(
    draft: BranchDraft,
    rules: List<SceneRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    BranchConditionMenu(draft = draft, rules = rules, menus = menus, tick = tick)

    //  时间区间那个选择窗 —— 独立弹窗，不挂在菜单上（菜单在点它的时候已经关掉了）。
    //  初值取**这一条**现在的区间：改时间时看到的是当前值，而不是又跳回默认。
    val timeAt = menus.timeAt
    BranchTimeRangeDialog(
        show = timeAt != AppPickerState.NOT_FOR_BRANCH,
        initial =
            rules
                .getOrNull(timeAt)
                ?.conditions
                ?.getOrNull(menus.timeCondIndex)
                ?.takeIf { it.kind == ConditionKind.TIME }
                ?.value
                .orEmpty(),
        onDismiss = { menus.timeAt = AppPickerState.NOT_FOR_BRANCH },
        onConfirm = { range ->
            tick()
            val condIndex = menus.timeCondIndex
            menus.timeAt = AppPickerState.NOT_FOR_BRANCH
            //  **时间区间不许重叠**：同一档手势下两条区间一重叠，先命中的那条永远赢、后一条永远
            //  轮不到，而界面上两行都好好列着。选完就拦（与「同一个应用只能当一次条件」同一套），
            //  窗已经关了 —— 提示里说清楚，让用户回去改。
            if (branchTimeOverlaps(range, timeAt, condIndex)) {
                hint(I18n.get(Strings.COMMAND_COND_TIME_OVERLAP, "时间区间不允许重叠"))
            } else {
                applyBranchCondition(draft, timeAt, condIndex, BranchCondition.of(ConditionKind.TIME, range), menus)
            }
        },
    )
}

/**
 * 「添加条件」那一行 —— 点了**直接插一条空条件**，不弹菜单。
 *
 *  抽成一支只为让 [BranchRowMenu] 留在 detekt 的长度上限内；判据见那边那段注释：
 *  用户要的是「先占个位」，空条件在执行侧永不成立、会拦住保存，正好是「还没配完」的样子。
 *  这一行**还没有可用的条件**时不再插，只给一句提示把注意力推回那一行。
 */
private fun SettingsPage.addConditionRow(
    draft: BranchDraft,
    at: Int,
    menus: BranchMenuState,
): MenuEntry.Row =
    MenuEntry.Row(
        title = I18n.get(Strings.COMMAND_BRANCH_ADD_CONDITION, "添加条件"),
        icon = TablerIcons.Outline.Plus,
        onPick = { appendCondition(draft, at, menus) },
    )

/**
 * 「这一块再加一条条件」—— 菜单里那一行与**卡片末尾那一行**共用这一支（权威实现只有这份）。
 *
 *  两张入口说的是同一件事：「这一块的场景再多一条」。放在卡片末尾是用户口径
 *  （不必点「更多」再找，列表滚到哪儿就能就地加）。
 *
 *  判据是「**还没有可用的条件**」，不是「最后一条是空的」：条件列表为空时页面同样已经摆了
 *  一行「未选择条件」占位，那也是「有待配的一行」—— 再插一条就变成两行都空，用户还得分清
 *  哪行是刚加的。
 *
 *  空条件在执行侧永不成立（`BranchCondition.isUsable` 假），`isReady` 为假会拦住保存 ——
 *  正好是「还没配完」该有的样子；页面上那一行显示「未选择条件」，点它才开菜单。
 */
internal fun SettingsPage.appendCondition(
    draft: BranchDraft,
    at: Int,
    menus: BranchMenuState,
) {
    menus.menuAt = -1
    val old = draft.scenes.getOrNull(at) ?: return
    val pending = old.conditions.isEmpty() || old.conditions.lastOrNull()?.isUsable == false
    if (pending) {
        //  与其它「为什么这一步做不了」共用同一个位
        hint(
            I18n.get(Strings.COMMAND_BRANCH_CONDITION_PENDING, "有场景还没选"),
            slot = HINT_SLOT_DISABLED,
        )
        return
    }
    val next = old.addingCondition(BranchCondition(""))
    draft.update(at, next)
    //  刚插的那一条先收起再展开（见 `BranchMenuState.freshCond`）
    menus.freshCond = CondRef(at, next.conditions.lastIndex)
}

/**
 * 「删除这一条条件」那一行（**最后一条不给删**：删到 0 条等于「这条规则还没配完」，
 * 而「不要这条规则」有行尾菜单里那个删除）。
 *
 *  抽成一支只为把 [BranchConditionMenu] 压回 `LongMethod` 的阈值。
 *
 * @return 不该出现这一行时 `null`
 */
internal fun SettingsPage.conditionDeleteRow(
    conditions: List<BranchCondition>,
    condIndex: Int,
    at: Int,
    menus: BranchMenuState,
): MenuEntry.Row? {
    if (conditions.size <= 1 || condIndex < 0) return null
    return MenuEntry.Row(
        title = I18n.get(Strings.COMMON_DELETE, "删除"),
        icon = PageIcons.CommandDelete,
        danger = true,
        dividerBefore = true,
        onPick = {
            //  **先问一句**：删一条条件不可逆，而「删除」这一行就在「换这一条」的正下方，
            //  点歪一下少一条条件、判定结果当场变了。确认窗见 `BranchConditionDeleteDialog`。
            //
            //  菜单同时关掉，不把下标挪到前一条继续开着：用户点的是「删掉这一条」，
            //  再自动支起另一条的条件菜单等于替他又开了一次编辑器。
            menus.deletingCond = CondRef(at, condIndex)
            menus.closeConditionMenu()
        },
    )
}

/**
 * 把一条条件写到第 [index] 条规则上（[AppPickerState.NEW_BRANCH] = 新加一条）。
 *
 *  @param condIndex 写进**这块里的第几条条件**：`>= 0` = 换那一条，越界 / `-1` = 追加一条。
 *    一块可以带好几条条件（逐条都要成立），所以「写到哪一条」必须由调用方明确指出来 ——
 *    这里不替它猜（`replacingCondition` 对越界会退回改第 0 条，那会把用户已有的第一条换掉）。
 *
 *  新加的那一条三档动作都是「跟随默认命令」（`SceneRule` 的默认值）：条件与动作是同一个场景的
 *  两半，**先配条件**、动作进「场景动作」页慢慢挑 —— 这一页不该逼用户先挑一条命令。
 */
internal fun SettingsPage.applyBranchCondition(
    draft: BranchDraft,
    index: Int,
    condIndex: Int,
    condition: BranchCondition,
    /** 追加时要把「刚插的是第几条」告诉界面（它要先收起再展开，见 `BranchMenuState.freshCond`）。 */
    menus: BranchMenuState,
) {
    //  这张表就是这一页那一份草稿的表（调用点传进来的 `rules` 与它同一个对象）
    val old = draft.scenes.getOrNull(index)
    //  `NEW_BRANCH` 是 -1，`getOrNull` 对它本来就给 null —— 不必再单判一次
    if (old == null) {
        draft.add(SceneRule(conditions = listOf(condition)))
    } else if (condIndex in old.conditions.indices) {
        //  改第 condIndex 条：只换那一个槽位，其余几条原样留着
        draft.update(index, old.replacingCondition(condIndex, condition))
    } else {
        //  追加一条（「再加一条条件」进来的）。不走 `replacingCondition`：那支越界会退回改第 0 条，
        //  等于把用户已有的第一条悄悄换掉 —— 多条件里这是最不能接受的一种「猜」。
        val next = old.addingCondition(condition)
        draft.update(index, next)
        //  **这一块原来一条条件都没有**时，这一下不是「往表里插一行」，而是把占位行
        //  「未选择场景」原地换成它 —— 不设 `freshCond`，那一行就不播「先收起再展开」的插入动画，
        //  只做内容淡换（真机上先收起再展开的表现是「占位行瞬间消失、高度塌掉，新行再滑进来」）。
        //  已有条件时才是真的插入，照旧让新行从 0 高展开。
        if (old.conditions.isNotEmpty()) {
            menus.freshCond = CondRef(index, next.conditions.lastIndex)
        }
    }
}

/**
 * 删掉这一块的第 [condIndex] 条条件。
 *
 *  **最后一条不删**（调用方也只在两条以上时才给入口）：删到 0 条等于「这条规则还没配完」，
 * 而用户想表达的「不要这条规则」有行尾菜单里那个删除。这里再兜一次，免得以后多一个入口忘了。
 */
internal fun SettingsPage.deleteBranchCondition(
    draft: BranchDraft,
    index: Int,
    condIndex: Int,
) {
    val old = draft.scenes.getOrNull(index) ?: return
    if (old.conditions.size <= 1) return
    if (condIndex !in old.conditions.indices) return
    draft.update(index, old.withoutCondition(condIndex))
    XLog.w("系统场景：第 ${index + 1} 条规则删掉第 ${condIndex + 1} 条场景（剩 ${old.conditions.size - 1} 条）")
}
