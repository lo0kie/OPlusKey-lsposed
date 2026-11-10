package com.lookie.opluskey.core.model

/**
 * 一个**场景块** —— 一组条件 + **完整的三档手势**。
 *
 * ## 为什么是「块里装三档」，而不是「档里列场景」
 *
 *  一条规则只认一个手势时，用户要写三次「在微信里」才能把三档都配出来；而三档之间本来是
 *  同一个场景的三件事 —— 切到微信之后单击做什么、双击做什么、按住做什么。
 *
 *  现在一块就是那个场景：`conditions` 成立 ⇒ 这一块的 [single] / [double] / [long] 接管这三档。
 *  于是「加一个场景」是一次动作，不是三次。
 *
 * ## 三档默认都是「跟随默认」（= 这一档不参与）
 *
 *  [Action.Follow] 与 [Action.None] 是**两件事**：
 *  · [Action.Follow]（界面上「跟随默认」，也是**默认值**）⇒ 这一档**不被这一块接管**，
 *    继续往下找条件成立的块，最后回落到按键绑定里这一档绑的动作 —— 老配置迁移过来就是这个状态
 *    （见 `ConfigDto`），所以升级前后行为逐字一致；
 *  · [Action.None]（界面上选的「无操作」）⇒ 这一块命中时**这一档什么都不做**（吞掉）。
 *
 *  合并成一个的话，「只给长按配了动作的场景」会把单击 / 双击一起变成无操作 —— 那不是用户写的。
 *
 * ## 顺序与「且」
 *
 *  [conditions] 是**且**：一块可以带 0..n 条条件，判定时逐条都要成立。
 *  一张表从上往下找，**第一条「条件成立且这一档有值」的块赢**（见 `pickAction`）。
 *  一块条件都没配 = 还没配完：它在判定侧**永不成立**（[isReady] 为假，界面上拦着不许保存）。
 *
 * ## 判据不在这里
 *
 *  条件成不成立要读系统开关（前台应用 / 亮屏 / 响铃状态…），那是宿主侧的事
 *  （`ConditionProbe`）。所以匹配由调用方传进来，这一层只管顺序、「且」与「第一条赢」。
 *
 * ## 组合键只用得到两档
 *
 *  组合键没有双击（`ComboKey` 的类注释），[double] 在那条路上永远是 [Action.Follow]；数据层留着这一格
 *  只是为了让单键与组合键共用同一个块类型。
 */
data class SceneRule(
    /** 这一块的**全部**条件（顺序即书写顺序，判定时逐条都要成立）。空 = 还没选。 */
    val conditions: List<BranchCondition> = emptyList(),
    /** 单击那一档；默认 [Action.Follow] = **不参与**（见类注释）。 */
    val single: Action = Action.Follow,
    /** 双击那一档；默认跟随。组合键不看这一档。 */
    val double: Action = Action.Follow,
    /** 长按那一档；默认跟随。 */
    val long: Action = Action.Follow,
    /**
     * 这一块**参不参与判定**（界面上的「停用场景」）—— 停用的块整块跳过，条件与三档动作都还留着。
     *
     *  与「删掉」是两件事：删了就没了，停用只是让它这一趟不生效。
     *  判定侧只在 [matches] 一处读它，所以「停用」对按键与组合键两条路同时生效。
     */
    val enabled: Boolean = true,
) {
    /** [gesture] 这一档在这一块里写的动作（[Action.Follow] = 这一档没配）。 */
    fun actionOf(gesture: Gesture): Action =
        when (gesture) {
            Gesture.SINGLE -> single
            Gesture.DOUBLE -> double
            Gesture.LONG -> long
        }

    /** 把 [gesture] 这一档换成 [action]（[Action.Follow] = 退回「跟随默认」）。 */
    fun withAction(
        gesture: Gesture,
        action: Action,
    ): SceneRule =
        when (gesture) {
            Gesture.SINGLE -> copy(single = action)
            Gesture.DOUBLE -> copy(double = action)
            Gesture.LONG -> copy(long = action)
        }

    /**
     * 配完了没有 —— 至少一条条件、且每一条都**认得出并且取值填了**（`isUsable`）。
     *
     *  「配完」不等于「可能成立」：同一档条件给了两个互斥取值（亮屏 + 锁屏）也是配完的，
     *  只是永远不成立 —— 那种情况由 [isContradictory] 单独说，两者不要混成一个布尔。
     */
    val isReady: Boolean get() = conditions.isNotEmpty() && conditions.all { it.isUsable }

    /**
     * 这一块**三档都是跟随默认** —— 它命中也不会改变任何东西（界面上三行都写「跟随默认」）。
     *
     *  留着这种块不算错（用户可能先把场景写下来、动作慢慢填），但它对判定毫无影响，
     *  所以「添加场景」时不必拦，保存时也不必拦。
     */
    val isEmpty: Boolean get() = single is Action.Follow && double is Action.Follow && long is Action.Follow

    /** 这块里**「前台应用」那一条**的下标（`-1` = 没有）。 */
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
     *  「前台应用」也算：一条要求微信在前台、另一条要求相机在前台，同样是永远不成立。
     *  同一个应用写两遍不算（`distinct()` 之后只剩一个）。
     */
    val isContradictory: Boolean
        get() =
            conditions
                .mapNotNull { c -> c.kind?.let { it to c.effectiveValue } }
                .groupBy({ it.first }, { it.second })
                .any { (_, values) -> values.distinct().size > 1 }

    /**
     * 这一块在 [gesture] 这一档下成立吗 —— [matches] 是单条条件的判据。
     *
     *  **逐条都要成立**（且）；一条条件都没有的块**不成立**：宁可落到下一块 / 落到按键绑定
     * 那一档，也不执行一条用户还没想清楚的条件（那是「填了一半的规则」）。
     * 停用的块（[enabled] 为假）一律不成立。
     *
     *  **动作有没有配也在这一步判**（[Action.Follow] = 这一档不接管），[pickAction] 直接取赢家。
     */
    fun matches(
        gesture: Gesture,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = enabled && conditions.isNotEmpty() && conditions.all(matches) && actionOf(gesture) !is Action.Follow

    /**
     * 换掉第 [at] 条条件 —— **其余的留着**。
     *
     *  · 表是空的 ⇒ 直接放进去（等价于「添第一条」）；
     *  · [at] 越界 ⇒ 退回改第一条（调用方算错了下标时，宁可变错一面，也别把这一行清干净）。
     */
    fun replacingCondition(
        at: Int,
        condition: BranchCondition,
    ): SceneRule =
        when {
            conditions.isEmpty() -> copy(conditions = listOf(condition))
            at !in conditions.indices -> replacingCondition(0, condition)
            else -> copy(conditions = conditions.toMutableList().also { it[at] = condition })
        }

    /** 在这块**末尾再添一条**条件（同一档重复取值由 [isContradictory] / 界面的置灰去管）。 */
    fun addingCondition(condition: BranchCondition): SceneRule = copy(conditions = conditions + condition)

    /** 去掉第 [at] 条条件（越界时原样返回）。 */
    fun withoutCondition(at: Int): SceneRule =
        if (at !in conditions.indices) this else copy(conditions = conditions.filterIndexed { index, _ -> index != at })

    companion object {
        /**
         * 一颗键（或一档组合）最多几个场景块 —— 二十块（用户口径）。
         *
         *  一块顶过去最多三条规则，所以这个数比原来那张表「宽松」不了多少：
         *  判定侧每按一次要走一遍这张表（时间区间 + 系统开关），而它是顺序匹配、第一条成立就赢。
         *
         *  到这一档时「添加场景」那颗按钮**置灰并说明原因**（见 `BranchHeaderAdd`），
         *  加行入口本身也拦一道（`addScene`）—— 手改配置 / 别的入口不该能越过它。
         */
        const val MAX_SCENES = 20

        /** 空块（三档都没配）—— 「添加场景」插的就是它。 */
        val EMPTY = SceneRule()
    }
}

/**
 * 从一张**场景表**里挑这一档该执行的动作 —— 全项目唯一实现。
 *
 *  语义两句：
 *  1. 从上往下找**条件成立、且这一档有值**的第一块，用它那一档的动作；
 *  2. 一块都不中 ⇒ 落到 [fallback]（这一档直接绑的那个动作）。表是空的 ⇒ 直接给 [fallback]。
 *
 *  「这一档有值」是 [Action.Follow] 与 [Action.None] 的区别（见 [SceneRule] 类注释）：没配的档不算接管，
 *  所以「只给长按配了动作的场景」不会把单击 / 双击一起吞掉。
 *
 *  为什么这句必须只有一份：条件分支的错**只在真机上看得见** —— 某个系统开关打开时本该走
 *  另一块，结果走了默认那条；两份实现里只要有一份判据差一点，就只会在那个开关打开那天现形，
 *  且不报任何错。
 *
 * @param matches 条件是否成立 —— 由调用方给（读系统开关的那一层是 `ConditionProbe`）：
 *   这一层不认识系统接口。
 */
internal fun pickAction(
    scenes: List<SceneRule>,
    gesture: Gesture,
    fallback: Action,
    matches: (BranchCondition) -> Boolean,
): Action = scenes.firstOrNull { it.matches(gesture, matches) }?.actionOf(gesture) ?: fallback

/**
 * 这一档**有没有被某个场景块接管** —— 松手那一档要判「长按那一档命中系统场景就让位」。
 *
 *  与 [pickAction] 同一条判据（条件成立 + 这一档有值），只是不要动作、只要一个是非。
 */
internal fun sceneTakes(
    scenes: List<SceneRule>,
    gesture: Gesture,
    matches: (BranchCondition) -> Boolean,
): Boolean = scenes.any { it.matches(gesture, matches) }
