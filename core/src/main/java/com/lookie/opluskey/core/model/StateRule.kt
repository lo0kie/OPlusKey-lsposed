package com.lookie.opluskey.core.model

/**
 * 一条**条件分支**：这个手势在 [conditions] **全部成立**时改执行 [action]。
 *
 * 挂在 `KeyConfig.rules` 上，**按顺序匹配、第一条成立的赢**（见 `KeyConfig.actionFor`）；
 * 一条都不中就走这颗键这一档**本来绑定的动作**（`KeyConfig.actionOf`）。表是空的（默认）⇒
 * 行为与没有这一档完全相同。
 *
 * ## 一个块里的多条条件是「且」（AND）
 *
 *  一块 = 一行 = 一条规则，它可以带 **0..n 条**条件，判定时逐条都要成立（见 [appliesTo]）。
 *  「在微信里、并且插着耳机」这种组合因此写得出来，而不必拆成两行 —— 拆行表达不了「且」，
 *  只会变成「先命中哪一行算哪一行」。
 *
 *  界面目前一次只编辑一条（`condition` 这个只读口子给的就是第一条），但**数据层与判定层已经
 *  按多条做**：手改配置、以后加「再添一条条件」的入口、以及跨设备导入的配置，走的都是同一条路。
 *
 * ## 为什么分叉不占绑定
 *
 * 按下去先看这张表，命中就捕获下来执行、没命中才轮到按键绑定本身 —— 于是「加一条条件」
 * 永远不必先想清楚「那原来的绑定去哪了」，两件事各自独立。
 *
 * ## 手势是分支的一部分
 *
 * 而不是「每个手势一张表」：用户的心智是「在微信里，短按改成扫码」，一条一条写得出来；
 * 拆成三张表则要求每条规则先选对表，配置文件和界面上都多一层无意义的分类。
 * 界面上按当前手势过滤，看到的就是「这一档的规则」。
 *
 * ## 判据不在这里
 *
 * 条件成不成立要读系统开关（前台应用 / 亮屏 / 响铃状态…），那是宿主侧的事
 * （`ConditionProbe`）。所以匹配由调用方传进来，这一层只管「顺序」「且」与「第一条赢」。
 *
 * ## 一条条件都没有 = 还没配完
 *
 * 界面上「添加条件」只是往表里插一行，**不替用户先填一个默认条件**（先填「亮屏中」看着像
 * 用户自己选的，实际上会静默接管这一档，比空着更危险）。空条件的行就是「还没选」：
 * 它在执行侧**永不成立**（见 [appliesTo]），界面上拦着不许保存（见 [isReady]）。
 */
data class StateRule(
    val gesture: Gesture,
    /** 这一块的**全部**条件（顺序即书写顺序，判定时逐条都要成立）。空 = 还没选。 */
    val conditions: List<BranchCondition> = emptyList(),
    val action: Action,
) {
    /**
     * **第一条**条件 —— 给「一次只编辑一条」的界面与那一批老调用点用。
     *
     *  只读：写回去走 [withCondition] / [addingCondition]（`copy(condition = …)` 那种写法会静默
     *  把其余几条抹掉，所以刻意不给 setter）。
     */
    val condition: BranchCondition? get() = conditions.firstOrNull()

    /**
     * 配完了没有 —— 至少一条条件、且每一条都**认得出并且取值填了**（`isUsable`）。
     *
     *  「配完」不等于「可能成立」：同一档条件给了两个互斥取值（亮屏 + 锁屏）也是配完的，
     *  只是永远不成立 —— 那种情况由 [isContradictory] 单独说，两者不要混成一个布尔。
     */
    val isReady: Boolean get() = conditions.isNotEmpty() && conditions.all { it.isUsable }

    /**
     * 这块里**「前台应用」那一条**的下标（`-1` = 没有）。
     *
     *  为什么要有这个名字：挑应用页「点一行改的是哪一条」、`SettingsPage` 回填高亮「这一行绑的是
     *  哪个应用」都在找它。三处各写一遍 `firstOrNull { it.kind == APP }` 时，只要有一处写成
     *  `firstOrNull()`，就会在「应用条件排在第二位」的配置上静默走错路。
     */
    val appConditionIndex: Int get() = conditions.indexOfFirst { it.kind == ConditionKind.APP }

    /**
     * 这块里「前台应用」那一条（`null` = 没有）。
     *
     *  一块里**只该有一条**：两个不同应用是「既在 A 又在 B」，永远不成立（见 [isContradictory]）；
     *  同一个应用写两遍是冗余。写回去时按 [appConditionIndex] 换槽位，不会多加一条。
     */
    val appCondition: BranchCondition? get() = conditions.getOrNull(appConditionIndex)

    /**
     * 这块**自己跟自己打架**吗 —— 同一档条件出现了两个不同的取值（「亮屏 + 锁屏」）。
     *
     *  判据取 `effectiveValue`（老配置没有取值的按默认档理解），只比认得出的档。
     *  **「前台应用」也算**：一条要求微信在前台、另一条要求相机在前台，同样是永远不成立 ——
     *  它最初被当成「用户写了个不可能的组合」排除在外，但那是**结果**，判据本身没理由放过它。
     *  同一个应用写两遍不算（`distinct()` 之后只剩一个）。
     *
     *  这条只说明「永远不成立」，与「配完了没有」（[isReady]）是两件事：配完的块照样可能永远不成立。
     */
    val isContradictory: Boolean
        get() =
            conditions
                .mapNotNull { c -> c.kind?.let { it to c.effectiveValue } }
                .groupBy({ it.first }, { it.second })
                .any { (_, values) -> values.distinct().size > 1 }

    /**
     * 这一条在「[gesture] 这次手势」下成立吗 —— [matches] 是单条条件的判据。
     *
     *  **逐条都要成立**（且）；一条条件都没有的行**不成立**：宁可落到下面一条 / 落到按键绑定
     *  那一档，也不执行一条用户还没想清楚的条件（那是「填了一半的规则」）。
     *
     *  `all` 本身会短路，所以配了三条条件的块在第一条不成立时不会再读后两个系统开关。
     */
    fun appliesTo(
        gesture: Gesture,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = this.gesture == gesture && conditions.isNotEmpty() && conditions.all(matches)

    /**
     * 换掉第 [at] 条条件 —— **其余的留着**。
     *
     *  这是「界面上一行改一条」的写法：那块只有一条时它就是「换成这条」，有几条时只动那一个槽位，
     *  不会把别的悄悄抹掉（`copy(conditions = listOf(…))` 是另一种意思，走 [single]）。
     *
     *  · 表是空的 ⇒ 直接放进去（等价于「添第一条」）；
     *  · [at] 越界 ⇒ 退回改第一条（调用方算错了下标时，宁可变错一面，也别把这一行清干净）。
     */
    fun replacingCondition(
        at: Int,
        condition: BranchCondition,
    ): StateRule =
        when {
            conditions.isEmpty() -> copy(conditions = listOf(condition))
            at !in conditions.indices -> replacingCondition(0, condition)
            else -> copy(conditions = conditions.toMutableList().also { it[at] = condition })
        }

    /** 在这块**末尾再添一条**条件（同一档重复取值由 [isContradictory] / 界面的置灰去管）。 */
    fun addingCondition(condition: BranchCondition): StateRule = copy(conditions = conditions + condition)

    /** 去掉第 [at] 条条件（越界时原样返回）。 */
    fun withoutCondition(at: Int): StateRule =
        if (at !in conditions.indices) this else copy(conditions = conditions.filterIndexed { index, _ -> index != at })

    companion object {
        /** 单条件写法 —— 那批「一行只有一条条件」的入口用它，读起来比 `listOfNotNull` 直白。 */
        fun single(
            gesture: Gesture,
            condition: BranchCondition?,
            action: Action,
        ): StateRule = StateRule(gesture, listOfNotNull(condition), action)
    }
}
