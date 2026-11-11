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
 * ## 同种类之间「或」，不同种类之间「且」
 *
 *  [conditions] 里**同一个种类**可以有好几条（上限是 `choices.size - 1`，见 `blockKindsOf`）：
 *  它们说的是「这几种状态里任一种」—— `[亮屏, 熄屏]` 就是「没停在锁屏界面」，三条全加等于没有
 *  条件，所以上限卡在少一档。不同种类之间仍是**且**：`[亮屏, 熄屏]` 与 `[微信]` 要同时成立。
 *
 *  一张表从上往下找，**第一条「条件成立且这一档有值」的块赢**（见 `pickAction`）。
 *  一块条件都没配 = 还没配完：它在判定侧**永不成立**（[isReady] 为假，界面上拦着不许保存）。
 *
 * ## 判据不在这里
 *
 *  条件成不成立要读系统开关（前台应用 / 亮屏 / 响铃状态…），那是宿主侧的事
 *  （`ConditionProbe`）。所以匹配由调用方传进来，这一层只管顺序、「或 / 且」与「第一条赢」。
 *
 * ## 组合键只用得到两档
 *
 *  组合键没有双击（`ComboKey` 的类注释），[double] 在那条路上永远是 [Action.Follow]；数据层留着这一格
 *  只是为了让单键与组合键共用同一个块类型。
 */
data class SceneRule(
    /** 这一块的**全部**条件（顺序即书写顺序，同种类之间「或」、不同种类之间「且」）。空 = 还没选。 */
    val conditions: List<BranchCondition> = emptyList(),
    /** 单击那一档；默认 [Action.Follow] = **不参与**（见类注释）。 */
    val single: Action = Action.Follow,
    /** 双击那一档；默认跟随。组合键不看这一档。 */
    val double: Action = Action.Follow,
    /** 长按那一档；默认跟随。 */
    val long: Action = Action.Follow,
    /**
     * 「松手执行额外动作」的开关 —— 这一块命中长按时，抬手那一刻再执行 [release] 那一格。
     *
     *  与 `KeyConfig.longReleaseOn` 同义、同一个理由分开存：开着但还没挑动作是合法状态（松手什么都不做）。
     */
    val releaseOn: Boolean = false,
    /**
     * **松手那一档**（`GestureTrigger.Release`）的动作；默认 [Action.None]。
     *
     *  为什么它不像前三档那样有「跟随默认」：长按一旦被这一块接管，**按键自己那个松手动作就不会
     *  再执行**（`GestureExec.resolveAction` 的「一次按压一个结果」）。所以这一格
     *  [Action.None] 就是「这一块命中时松手什么都不做」—— 也正是这一格出现之前的行为，
     *  老配置读进来逐字不变。
     */
    val release: Action = Action.None,
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
     * 这一块**有没有自己的松手动作** —— 开关开着且挑过动作。
     *
     *  两个读者：判定侧（`releaseBound` 要不要为真）与广播（宿主算给注入侧那一位）。
     *  停用的块不算：它命中不了，抬手那一刻也不该为它发 `Release`。
     */
    val hasRelease: Boolean get() = enabled && releaseOn && release.isBound

    /**
     * 配完了没有 —— 至少一条条件、且每一条都**认得出并且取值填了**（`isUsable`）。
     *
     *  「配完」只说明每一条都填了取值，不代表这块会命中：成不成立要看当时的系统状态
     *  （[appliesTo]），这一层不认识系统接口。
     */
    val isReady: Boolean get() = conditions.isNotEmpty() && conditions.all { it.isUsable }

    /** 这块里**「前台应用」那一条**的下标（`-1` = 没有）。 */
    val appConditionIndex: Int get() = conditions.indexOfFirst { it.kind == ConditionKind.APP }

    /**
     * 这块里「前台应用」那一条（`null` = 没有）。
     *
     *  一块里**只该有一条**：那一条自己就能多选（`包1||包2`，任一在前台即通过），再开一行只是
     *  把同一件事写成两处（判定侧按「或」算，两行同样成立，但没有第二种必要）。
     *  写回去时按 [appConditionIndex] 换槽位，不会多加一条。
     */
    val appCondition: BranchCondition? get() = conditions.getOrNull(appConditionIndex)

    /**
     * 条件成不成立 —— **同种类之间「或」，不同种类之间「且」**。
     *
     *  按种类分组之后逐组问「这一组里有没有一条成立」：屏幕那两行 `亮屏` / `熄屏` 任一条成立即可
     *  （合起来就是「没停在锁屏界面」），而「屏幕」这一组与「前台应用」那一组之间仍要同时成立。
     *
     *  为什么必须只有这一份：块内几条条件的组合方式（且 / 或）一旦在界面、判定、提示里各写一遍，
     *  差一处就是「界面上配得出来、按下去不走那一支」，而且只在某个开关打开那天现形、不报任何错。
     */
    fun appliesTo(matches: (BranchCondition) -> Boolean): Boolean =
        conditions.groupBy { it.kind }.values.all { sameKind -> sameKind.any(matches) }

    /**
     * 这一块在 [gesture] 这一档下成立吗 —— [appliesTo] 是条件那一半的判据。
     *
     *  一条条件都没有的块**不成立**：宁可落到下一块 / 落到按键绑定那一档，也不执行一条用户
     *  还没想清楚的条件（那是「填了一半的规则」）。停用的块（[enabled] 为假）一律不成立。
     *
     *  **动作有没有配也在这一步判**（[Action.Follow] = 这一档不接管），[pickAction] 直接取赢家。
     */
    fun matches(
        gesture: Gesture,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = enabled && conditions.isNotEmpty() && appliesTo(matches) && actionOf(gesture) !is Action.Follow

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

    /** 在这块**末尾再添一条**条件（同一种类能加几条由 `blockKindsOf` 管，判定侧按「或」算）。 */
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

        /**
         * 一块里最多几条条件 —— 九条（用户口径）。
         *
         *  比「同一种类最多几条」（`blockKindsOf`，按各档取值数封顶）高一层：那一条管的是
         *  「同一种类别写重了」，这一条管的是**一块整体**的规模 —— 判定侧每块都要逐条过一遍，
         *  九条以上的一块已经读不动了（界面上也一屏放不下），而真需要那么多条件时，
         *  本来该拆成两块。
         *
         *  到这一档时：块尾那行「添加场景」置灰并说明原因，一级菜单里每一档也一并点不动
         *  （见 `appendCondition` 与 `conditionKindRows` 的 `blockFull`）。
         */
        const val MAX_CONDITIONS = 9

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

/**
 * 「松手」那一档该执行什么 —— 全项目唯一实现（[pickAction] 的松手版）。
 *
 *  长按被某一块接管时，这一次按压归**那一块**：它自己那一格说了算 —— **[SceneRule.hasRelease]**
 *  （开关开着 + 挑过动作）为真才执行那个动作，为假就是 `None`：这一下什么都不做，
 *  **不回落**按键自己那个松手动作。一块都不中才落到 [fallback]。
 *
 *  为什么这一格必须归块：松手动作是「这一档做什么」的另一半。留在按键身上时，场景一命中
 *  它就整条失效（真机口径「配了额外动作，按下去什么也没发生」）。
 *
 *  **开关与动作必须一起看**（[SceneRule.hasRelease] / 调用方传进来的 [fallback] 已经带过开关）：
 *  只看「存下来的动作」，块里挑过动作之后再把开关关掉照样会执行 —— 表现就是
 *  「这一档明明没开额外动作，按下去却冒出来一个」，也就是用户口中的「穿透」。
 */
internal fun pickReleaseAction(
    scenes: List<SceneRule>,
    fallback: Action,
    matches: (BranchCondition) -> Boolean,
): Action {
    val owner = scenes.firstOrNull { it.matches(Gesture.LONG, matches) } ?: return fallback
    return if (owner.hasRelease) owner.release else Action.None
}
