package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.StateRule

// 「这一档 / 这个取值是不是已经加过了」的判据 —— 从 `CommandBranchMenus.kt` 分出来，一是让那边
// 的文件级函数数量留在 detekt 的 `TooManyFunctions` 之下，二是「重复」这件事本来就只有一个判据：
// 一级那一行灰不灰、二级那个取值灰不灰，必须同一份账。

/**
 * 判定「用过没有」的最小单位：**条件种类 + 取值**（`screen:on` 与 `screen:locked` 是两条）。
 *
 *  取值一律走 [BranchCondition.effectiveValue]：老配置里没有取值的那几条按默认档理解，
 *  否则「亮屏」那条会被当成「空取值」，跟谁都不算重复。
 */
internal data class UsedCondition(
    val kind: ConditionKind,
    val value: String,
)

/**
 * 表里已经用掉的「条件 + 取值」。
 *
 * @param skipAt 正在改的那一条规则（`-1` / 越界 = 不跳过）
 * @param skipCondition 那条规则里的**第几条条件** —— 只跳过**它自己**，同一块里的其余几条
 *   照样算「用掉了」。
 *
 *   为什么必须精确到条而不是整块：一块可以带好几条条件（逐条都要成立），
 *   「同一块里两条一样的条件」没有任何意义 —— 第二条永远不会改变结果，而用户看不出它没用。
 *   以前这里跳过**整块**，于是同一个取值能被加两遍（真机反馈）。
 */
internal fun usedConditionsOf(
    rules: List<StateRule>,
    skipAt: Int,
    skipCondition: Int = -1,
): Set<UsedCondition> =
    rules
        .flatMapIndexed { at, rule ->
            rule.conditions.mapIndexedNotNull { index, cond ->
                //  跳过「正在改的那一条」：它自己的取值不该把自己置灰（改回同一个值要能选）
                if (at == skipAt && index == skipCondition) {
                    null
                } else {
                    cond.kind?.let { UsedCondition(it, cond.effectiveValue) }
                }
            }
        }.toSet()

/**
 * 这一档是不是**已经加满**了（每一种取值都有了一条）—— `true` ⇒ 一级那一行置灰。
 *
 *  · 带候选的档（屏幕 / 充电 / 耳机 / 媒体 / WiFi / 蓝牙 / 移动数据 / 飞行模式 / 响铃 / 方向）：
 *    候选全用过才算满；
 *  · 没有候选的档：只有「前台应用」与「时间区间」—— 它们**永远不会**因为「用过」被灰掉
 *    （跨规则行各一条是合理的：不同行各判各的前台应用；时间段同理，`18:00-22:00` 与
 *    `22:00-07:00` 是两种不同的情况）。**同一块里**应用只许一条，那条由 [blockKindsOf] 管，
 *    这里不重复判 —— 应用那一档现在多选，能装下所有应用，不该靠「再开一行」来加第二个。
 *  · 取值空着的条件不计入（它还没选，不该把一档堵死）。
 */
internal fun ConditionKind.exhausted(used: Set<UsedCondition>): Boolean {
    //  自由取值的那两档不参与：`choices` 是空的，按下面那条规则会被判成「用过一个就满」，
    //  于是加过一次「前台应用」之后整档灰掉（真机反馈过）。时间区间同此。
    if (this == ConditionKind.APP || this == ConditionKind.TIME) return false
    val mine = used.filter { it.kind == this }.map { it.value }.toSet()
    if (mine.isEmpty()) return false
    return choices.isEmpty() || choices.all { it in mine }
}

/**
 * 一级菜单里**同块内已经占掉**的那些档 —— 同一块里同一个「父级条件」只许有一条。
 *
 *  只有「时间区间」除外（不同时段各一条，各自是不同的情况）。
 *  **「前台应用」不除外**：它本身就能多选（一条条件里 `包1||包2`，任一命中即通过），
 *  再开第二行只会把「且」与「或」混在同一块里。
 *  跨块不禁：不同规则行各要一条「亮屏」是合理的，那由 `exhausted` 那套去管。
 *
 *  抽成一支只为把 [BranchConditionMenu] 的分析复杂度压回阈值。
 *
 * @param skipIndex 正在改的那一条（它自己不算「已经占了」）
 */
internal fun blockKindsOf(
    conditions: List<BranchCondition>,
    skipIndex: Int,
): Set<ConditionKind> =
    conditions
        .filterIndexed { index, _ -> index != skipIndex }
        .mapNotNull { it.kind }
        .filter { it != ConditionKind.TIME }
        .toSet()
