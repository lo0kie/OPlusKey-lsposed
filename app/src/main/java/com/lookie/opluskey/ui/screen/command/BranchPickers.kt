package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.StateRule
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
) {
    picker.openForBranch(index, forCommand = forCommand)
    // 搜索词清空（页面级状态，不再随页面出栈注销）
    picker.filter.value = ""
    resetSmallWindowForPicker()
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId())
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
        .any { rule ->
            val other = rule.condition ?: return@any false
            other.kind == ConditionKind.APP && other.value == pkg
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
    token: String,
    index: Int,
): Boolean {
    val pkg = AppTarget.pkg(Action.parse(token).appTarget.orEmpty())
    if (pkg.isBlank()) {
        XLog.w("分叉条件：挑中的应用解析不出包名（token=${XLog.redactToken(token)}），不改这一条")
        return false
    }
    // **同一个应用只能当一次条件**：顺序表里两条相同条件时，后一条永远轮不到（第一条就赢了），
    //  留着它只会让人以为「这条也在起作用」。不同应用可以有多条 —— 那正是这一档存在的意义。
    //  这里拦下并留在选择应用页，用户换个应用或直接返回都不会写脏数据。
    if (branchConditionUsed(pkg, index)) {
        XLog.w("分叉条件：$pkg 已经用在别的行上了，不改这一条")
        hint(I18n.get(Strings.COMMAND_BRANCH_DUPLICATE_APP, "这个应用已经加过了"))
        return false
    }
    val condition = BranchCondition.app(pkg)
    val old = branchDraft.draft.rules.getOrNull(index)
    if (old == null) {
        branchDraft.draft.add(StateRule(branchGestureNow(), condition, Action.None))
    } else {
        branchDraft.draft.update(index, old.copy(condition = condition))
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
