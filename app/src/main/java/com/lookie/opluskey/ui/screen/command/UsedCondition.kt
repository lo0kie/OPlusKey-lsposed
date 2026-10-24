package com.lookie.opluskey.ui.screen.command

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

/** 表里已经用掉的「条件 + 取值」—— [skipAt] 是正在改的那一条（它自己不算「用掉」）。 */
internal fun usedConditionsOf(
    rules: List<StateRule>,
    skipAt: Int,
): Set<UsedCondition> =
    rules
        .filterIndexed { at, _ -> at != skipAt }
        //  一块带几条条件就都算「用过」：置灰要按**整张表**算，漏掉某一块里的第二条，
        //  用户就能把它在别处再加一遍，而两条同样的条件永远不会轮流生效
        .flatMap { it.conditions }
        .mapNotNull { cond -> cond.kind?.let { UsedCondition(it, cond.effectiveValue) } }
        .toSet()

/**
 * 这一档是不是**已经加满**了（每一种取值都有了一条）—— `true` ⇒ 一级那一行置灰。
 *
 *  · 带候选的档（屏幕 / 充电 / 耳机 / 媒体 / WiFi / 响铃 / 方向）：候选全用过才算满；
 *  · 没有候选的档：只有「前台应用」，它**永远不会**因为「用过」被灰掉（可多条，只要应用不同）；
 *  · 取值空着的条件不计入（它还没选，不该把一档堵死）。
 */
internal fun ConditionKind.exhausted(used: Set<UsedCondition>): Boolean {
    if (this == ConditionKind.APP) return false
    val mine = used.filter { it.kind == this }.map { it.value }.toSet()
    if (mine.isEmpty()) return false
    return choices.isEmpty() || choices.all { it in mine }
}
