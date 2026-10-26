package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.core.model.TimeRange
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.state.AppPickerState

// 「条件执行」页与「选择应用」页之间的往返 —— 进去挑应用、挑完写回哪一处。
//
// 从 `CommandBranches.kt` 搬出来：那一边管的是**表怎么画**（行、空态、注脚、按钮），
// 这一边是**两个页面之间的接口**（进页前的复位、写回哪条规则的哪一半）。两件事各有一个
// 稳定的改动理由，而且搬走之后两边都离 detekt 的函数 / 长度上限更远。

/**
 * 进「选择应用」页给第 [index] 条规则挑应用（[AppPickerState.NEW_BRANCH] = 新加一条）。
 *
 *  与 `openAppPickerForStep` 同一条路：进页前把搜索词清掉、「以小窗口打开」复位 ——
 *  这一页的状态提到页面级、不随出栈注销，不手动清的话下次进来就是一份被过滤过的列表。
 *
 *  那一趟**不挑界面、也不挑小窗**（判据是 `AppPickerState.forBranch`）：条件只认包名。
 */
internal fun SettingsPage.openAppPickerForBranch(
    index: Int,
    forCommand: Boolean = false,
    conditionIndex: Int = -1,
) {
    picker.openForBranch(index, forCommand = forCommand, conditionIndex = conditionIndex)
    // 搜索词清空（页面级状态，不再随页面出栈注销）
    picker.filter.value = ""
    resetSmallWindowForPicker()
    //  给**条件**挑的那一趟支持多选：把这条规则里已有的包名预填进去（编辑时那几个勾要亮着）。
    //  给**动作**挑的那一趟只有单个目标，预填空集 —— 那一趟的选中走 `pending`。
    val picked =
        if (forCommand) {
            emptySet()
        } else {
            branchDraft.draft.rules
                .getOrNull(index)
                ?.appCondition
                ?.appPackages
                ?.toSet()
                .orEmpty()
        }
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId(), picked = picked)
    //  说一句「可以多选」：这一页以前是单选（点一行只亮一行），不提示的话没人会去点第二下。
    if (!forCommand) {
        hint(I18n.get(Strings.APP_PICKER_BRANCH_MULTI_HINT, "可以多选：任一应用在前台即通过"))
    }
    navTo(Screen.APP_PICKER)
}

/**
 * 这个包名是不是已经被**别的行**当成条件了 —— 同一个应用只能当一次条件。
 *
 *  判据只在这里：选择应用页「点一行」先拦一次（点下去就该知道），写回时再拦一次兜底
 *  （列表里点得快、两次之间状态又变了的情况）。
 *
 * @param exceptIndex 这一趟在改第几条（它自己那条不算重复）
 */
internal fun SettingsPage.branchConditionUsed(
    pkg: String,
    exceptIndex: Int,
): Boolean =
    branchDraft.draft.rules
        .filterIndexed { at, _ -> at != exceptIndex }
        // 一条条件可以带好几个包名（多选，`包1||包2`）：判「用过了没有」要看**整组**，
        //  只比 `value == pkg` 在多选之后永远不成立。
        .any { rule -> rule.appCondition?.appPackages?.contains(pkg) == true }

/**
 * [range] 这一段与表里**别的**时间区间有没有重叠。
 *
 *  为什么拦：同一档手势下两条区间一重叠，先命中的那条永远赢、后一条永远轮不到，
 *  而界面上两行都好好列着 —— 用户配了个不会生效的规则却看不出来。
 *  判据只有一处（[TimeRange.overlaps]），端点按左闭右开算：`18:00-22:00` 与 `22:00-07:00`
 *  相邻但**不算重叠**。
 *
 * @param exceptAt 这一趟在改第几行（它自己那条不算）
 * @param exceptCond 那一行里的第几条
 */
internal fun SettingsPage.branchTimeOverlaps(
    range: String,
    exceptAt: Int,
    exceptCond: Int,
): Boolean {
    val mine = TimeRange.parse(range) ?: return false
    //  改成「先滤出别的那些时间区间、再看有没有重叠」：循环里两个 `continue` + 一个
    //  `return` 会顶到 detekt 的 `LoopWithTooManyJumpStatements`
    val others =
        branchDraft.draft.rules.withIndex().flatMap { (at, rule) ->
            rule.conditions.withIndex().mapNotNull { (condAt, cond) ->
                //  跳过正在改的那一条，只留时间区间
                val skip = at == exceptAt && condAt == exceptCond
                cond.takeIf { !skip && it.kind == ConditionKind.TIME }
            }
        }
    return others.any { cond -> TimeRange.parse(cond.effectiveValue)?.overlaps(mine) == true }
}

/**
 * 整张表里**有没有**两条时间区间重叠 —— 保存前的兜底（选的时候已经拦过一次，
 * 但配置可以是手改 / 导入来的，那一步不经过界面）。
 */
internal fun SettingsPage.branchTimesOverlap(): Boolean =
    branchDraft.draft.rules.withIndex().any { (at, rule) ->
        rule.conditions.withIndex().any { (condAt, cond) ->
            cond.kind == ConditionKind.TIME && branchTimeOverlaps(cond.effectiveValue, at, condAt)
        }
    }

/**
 * 把挑中的应用写成第 [index] 条规则的**条件**（前台应用；[AppPickerState.NEW_BRANCH] = 新加一条）。
 *
 *  只改草稿、不落盘 —— 规则随右下角那颗「确定」一起入库。
 *
 *  条件只认包名：判的是「哪个应用在前台」，与怎么打开它无关。
 *
 *  ## 它自己退页（调用方**不要再退一次**）
 *
 *  写成功就退回压在这一页下面的那一页（也就是「条件执行」页），且用 [popBackStack] 而不是
 *  `goBack`：这一趟刚改完草稿必然是脏的，走 `goBack` 会先问「放弃未保存的」—— 而用户只是
 *  刚挑完一个应用。
 */
internal fun SettingsPage.writeBranchCondition(
    packages: List<String>,
    index: Int,
): Boolean {
    //  一条「前台应用」条件可以**多选**（`包1||包2`，任一在前台即通过）：这里收的是整组，
    //  空表 / 全空串说明这一趟什么都没选，不写。
    val pkgs = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (pkgs.isEmpty()) {
        XLog.w("分叉场景：这一趟没有挑中任何应用，不改这一条")
        return false
    }
    // **同一个应用只能当一次条件**：顺序表里两条相同条件时，后一条永远轮不到（第一条就赢了），
    //  留着它只会让人以为「这条也在起作用」。不同应用可以有多条 —— 那正是这一档存在的意义。
    //  这里拦下并留在选择应用页，用户换个应用或直接返回都不会写脏数据。
    val dup = pkgs.firstOrNull { branchConditionUsed(it, index) }
    if (dup != null) {
        XLog.w("分叉场景：$dup 已经用在别的行上了，不改这一条")
        hint(I18n.get(Strings.COMMAND_BRANCH_DUPLICATE_APP, "这个应用已经加过了"))
        return false
    }
    val condition = BranchCondition.apps(pkgs)
    val old = branchDraft.draft.rules.getOrNull(index)
    if (old == null) {
        branchDraft.draft.add(StateRule.single(branchGestureNow(), condition, Action.None))
    } else {
        //  **写进哪一条**（一块可以带好几条条件，逐条都要成立）：
        //   · 这一趟明确指了槽位（从「条件 2」那一行进来的）⇒ 换那一条；
        //   · 没指（「再加一条条件」进来的）⇒ 这块**已经有「前台应用」那一条**就换它
        //     （一块里两个前台应用必然矛盾，`isContradictory` 会判成永远不成立），
        //     **否则追加**一条。
        //  旧写法是 `appConditionIndex.coerceAtLeast(0)`，那块没有前台应用时会把**第 0 条**
        //  （可能是「屏幕状态」）替换掉 —— 用户点「再加一条条件」却丢了一条老条件。
        val explicit = picker.branchConditionIndex
        val slot =
            when {
                explicit in old.conditions.indices -> explicit
                old.appConditionIndex >= 0 -> old.appConditionIndex
                else -> -1
            }
        branchDraft.draft.update(
            index,
            if (slot >= 0) old.replacingCondition(slot, condition) else old.addingCondition(condition),
        )
    }
    picker.clearTarget()
    popBackStack()
    return true
}

/**
 * 把挑中的应用写成第 [index] 条规则的**动作**（`app:` / `window:`）。
 *
 *  与 [writeBranchCondition] 一样**自己退页**，调用方不要再退一次。
 */
internal fun SettingsPage.writeBranchCommand(
    token: String,
    index: Int,
): Boolean {
    val action = Action.parse(token)
    if (action is Action.None) {
        XLog.w("分叉动作：挑中的应用解析不出动作（token=${XLog.redactToken(token)}），不改这一条")
        return false
    }
    val old = branchDraft.draft.rules.getOrNull(index)
    if (old == null) {
        XLog.w("分叉动作：要改的第 $index 条已经不在了（草稿被重置过），不改")
        return false
    }
    branchDraft.draft.update(index, old.copy(action = action))
    picker.clearTarget()
    popBackStack()
    return true
}
