package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.core.model.TimeRange
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.state.picker.AppPickerState

// 「条件执行」页与「选择应用」页之间的往返 —— 进去挑应用、挑完写回哪一处。
//
// 从 `CommandBranches.kt` 搬出来：那一边管的是**表怎么画**（行、空态、注脚、按钮），
// 这一边是**两个页面之间的接口**（进页前的复位、写回哪条规则的哪一半）。两件事各有一个
// 稳定的改动理由。

/**
 * 进「选择应用」页给第 [index] 块挑**条件应用**（[AppPickerState.NEW_BRANCH] = 新加一块）。
 *
 *  与 `openAppPickerForStep` 同一条路：进页前把搜索词清掉、「以小窗口打开」复位 ——
 *  这一页的状态提到页面级、不随出栈注销，不手动清的话下次进来就是一份被过滤过的列表。
 *
 *  **只挑条件**：条件只认包名（`AppPickerState.appOnly`），不挑界面、也不挑小窗。
 *  场景里「某一档要执行什么应用」不走这条路 —— 那一档在「场景动作」页的网格里挑
 *  （`BindingTarget.Scene`），与网格同一套。
 *
 *  @param kind 这一趟写回去的是**哪一档**（[ConditionKind.APP] / [ConditionKind.APP_NOT]）。
 *    应用那一族两档**共用这一整页**：同一个列表、同一套多选、同一份预填 —— 只有写回时的
 *    `kind` 与两三条文案不同（见 [ConditionKind.isAppFamily]）。
 */
internal fun SettingsPage.openAppPickerForBranch(
    index: Int,
    conditionIndex: Int = -1,
    kind: ConditionKind = ConditionKind.APP,
) {
    picker.openForBranch(index, conditionIndex = conditionIndex, kind = kind)
    // 搜索词清空（页面级状态，不再随页面出栈注销）
    picker.filter.value = ""
    resetSmallWindowForPicker()
    //  给**条件**挑的那一趟支持多选：把这块里**这一档**已有的包名预填进去（编辑时那几个勾要亮着）。
    //  按 `kind` 取那一条：一块里 `前台是微信` 与 `前台不是钉钉` 可以同时存在，
    //  拿「第一条应用条件」去预填会把另一档的选择搬到这一档上来。
    val picked =
        branchDraftNow()
            .scenes
            .getOrNull(index)
            ?.appCondition(kind)
            ?.appPackages
            ?.toSet()
            .orEmpty()
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId(), picked = picked)
    //  说一句「可以多选」：这一页以前是单选（点一行只亮一行），不提示的话没人会去点第二下。
    //  两档那半句的结论相反（「任一在前台即通过」/「都不在前台才通过」），所以各有各的一句。
    hint(
        if (kind == ConditionKind.APP_NOT) {
            I18n.get(Strings.APP_PICKER_BRANCH_MULTI_HINT_NOT, "可以多选：选中的应用都不在前台才通过")
        } else {
            I18n.get(Strings.APP_PICKER_BRANCH_MULTI_HINT, "可以多选：任一应用在前台即通过")
        },
    )
    navTo(Screen.APP_PICKER)
}

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
    //  改成「先滤出别的那些时间区间、再看有没有重叠」。
    val others =
        branchDraftNow().scenes.withIndex().flatMap { (at, rule) ->
            //  **只与同一块里的其它时间条件比**：块与块之间是「哪个应用在前台」这类互斥的替代关系，
            //  `[18-22 微信]` 与 `[18-22 相机]` 本来就该并存 —— 跨块比会把合法配置拦成「时间重叠」。
            //  停用的行也不参与：它的区间不该挡住新配置。
            if (at != exceptAt || !rule.enabled) return@flatMap emptyList()
            rule.conditions.withIndex().mapNotNull { (condAt, cond) ->
                //  跳过正在改的那一条，只留时间区间
                val skip = condAt == exceptCond
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
    branchDraftNow().scenes.withIndex().any { (at, rule) ->
        rule.conditions.withIndex().any { (condAt, cond) ->
            cond.kind == ConditionKind.TIME && branchTimeOverlaps(cond.effectiveValue, at, condAt)
        }
    }

/**
 * 把挑中的应用写成第 [index] 条规则的**条件**（应用那一族；[AppPickerState.NEW_BRANCH] = 新加一条）。
 *
 *  只改草稿、不落盘 —— 规则随右下角那颗「确定」一起入库。
 *
 *  条件只认包名：判的是「哪个应用在前台」（或**不在**前台，见 `picker.branchKind`），
 *  与怎么打开它无关。
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
    //  同一个应用可以当**不同块**的条件：`[微信 + 充电]` 与 `[微信 + 耳机]` 是两条不同的场景，
    //  拿「整张表里用过没有」去拦，第二块就配不出来了（跨块不禁，见 `blockKindsOf`）。
    //  一条条件内部仍去重（上面那句 `distinct()`）。
    val condition = BranchCondition.apps(picker.branchKind, pkgs)
    val old = branchDraftNow().scenes.getOrNull(index)
    if (old == null) {
        branchDraftNow().add(SceneRule(conditions = listOf(condition)))
    } else {
        //  **写进哪一条**（一块可以带好几条条件，同种类之间「或」、不同种类之间「且」）：
        //   · 这一趟明确指了槽位（从「条件 2」那一行进来的）⇒ 换那一条；
        //   · 没指（「再加一条条件」进来的）⇒ 这块**已经有「前台应用」那一条**就换它
        //     （那一条自己就能多选 `包1||包2`，两行只是把同一件事写成两处），**否则追加**一条。
        //  旧写法是 `appConditionIndex.coerceAtLeast(0)`，那块没有前台应用时会把**第 0 条**
        //  （可能是「屏幕状态」）替换掉 —— 用户点「再加一条条件」却丢了一条老条件。
        val explicit = picker.branchConditionIndex
        val slot =
            when {
                explicit in old.conditions.indices -> explicit
                old.appConditionIndex(picker.branchKind) >= 0 -> old.appConditionIndex(picker.branchKind)
                else -> -1
            }
        branchDraftNow().update(
            index,
            if (slot >= 0) old.replacingCondition(slot, condition) else old.addingCondition(condition),
        )
    }
    picker.clearTarget()
    popBackStack()
    return true
}
