package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.SceneRule

// 一张场景表里**哪几块是白写的** —— 全项目唯一的判据，分两层：
//   · 块内（[blockKindsOf]）：这一块里还能不能再加一档条件；
//   · 块间（[duplicateBlockIndexes]）：这一块跟上面某一块是不是同一块。
//
//  从 `CommandBranchMenus.kt` 分出来，一是让那边的文件级函数数量留在 detekt 的
//  `TooManyFunctions` 之下，二是这两件事本来就只有一个问题：判定侧「从上往下第一条成立的赢」，
//  那么哪些块永远轮不到，必须只有一份账。

/**
 * 一级菜单里**这一块已经加满**的那些档 —— 加满了就点不动了。
 *
 *  同一种类可以加**好几条**（判定侧按「或」算，见 `SceneRule.appliesTo`），上限是
 *  [ConditionKind.choices] **少一档**：屏幕那三档全加就是恒成立，那样的条件等于没写；
 *  少一档才说得出一件事（`[亮屏, 熄屏]` = 没停在锁屏界面）。
 *
 *  · 枚举取值的档（屏幕 / 响铃 / 方向 / 开关那几档）⇒ 最多 `choices.size - 1` 条。
 *    两档的那几种（开 / 关）因此仍然只许一条 —— 两条就是「开着或关着」，恒成立。
 *  · 「前台应用」⇒ 仍然只许一条：那一条自己就能多选（`包1||包2`，任一命中即通过），
 *    开两行只是把同一件事写成两处。
 *  · 「时间区间」⇒ 不限：不同时段各一条，各自是不同的情况（两块时间区间重叠另有拦截）。
 *
 *  ## 跨块不禁
 *
 *  判据**只看这一块**：别的行各要一条「亮屏」、各要一个「前台应用」，都是合理的 ——
 *  `[亮屏 + 微信]` 与 `[亮屏 + 相机]` 是两条不同的场景，前者不许把后者那条「亮屏」堵掉。
 *  跨块比是这里犯过的错：拿「整张表里用过没有」当判据，于是第二块想配 `A开 + B关` 时，
 *  「A开」在别的块里出现过就点不动了（与 `branchTimeOverlaps` 那条「跨块比会把合法配置拦成
 *  时间重叠」同一个道理）。跨块那件事由 [duplicateBlockIndexes] 按**整块**管。
 *
 *  @param skipIndex 正在改的那一条（它自己不算「已经占了」）
 */
internal fun blockKindsOf(
    conditions: List<BranchCondition>,
    skipIndex: Int,
): Set<ConditionKind> =
    conditions
        .filterIndexed { index, _ -> index != skipIndex }
        .mapNotNull { it.kind }
        .groupingBy { it }
        .eachCount()
        .filter { (kind, count) -> count >= blockConditionCap(kind) }
        .keys

/**
 * 一块里同一种条件最多几条 —— 见 [blockKindsOf]。
 *
 *  `coerceAtLeast(1)` 是给「只有一档取值」的种类兜的底：那样算出来是 0 条，这一档会在菜单里
 *  永远点不动，等于从界面上删掉了它。现在没有这种种类，别让以后加的那一种撞上。
 */
private fun blockConditionCap(kind: ConditionKind): Int =
    when {
        kind == ConditionKind.TIME -> Int.MAX_VALUE
        kind.choices.isEmpty() -> 1
        else -> (kind.choices.size - 1).coerceAtLeast(1)
    }

/**
 * 一块场景里**一条条件**归一之后的样子 —— 判「两块是不是同一块」就比这个。
 *
 *  ## 三档取值按**取值**比，不按种类比
 *
 *  屏幕状态的 `on` / `off` / `locked` 是三件不同的事（判据见 `ConditionChecks.screenMatches`，
 *  三档互斥）：`[熄屏]` 与 `[锁屏]` 是两块不同的场景。拿种类当答案的话，这两块会被判成同一块，
 *  用户就再也配不出第二块 —— 而这三档里**没有哪一档能代替另一档**。
 *
 *  取值一律走 [BranchCondition.effectiveValue]：老配置里没有取值的（`wifi` / `media` / `screen_on`）
 *  按它当时那个意思解释，与判定侧同一个口径。
 */
internal data class BlockAnswer(
    val kind: ConditionKind,
    val value: String,
)

/**
 * 一块场景的**条件答案**（排好序、去过重）；`null` = 这块还没配完，不参与比较。
 *
 *  排序是为了让条件的书写顺序不影响判定：`[微信 + 充电]` 与 `[充电 + 微信]` 是同一块
 *  （块内几条条件是「且」，先后本来就没有意义）。
 */
internal fun blockAnswersOf(rule: SceneRule): List<BlockAnswer>? {
    if (!rule.isReady) return null
    val answers = ArrayList<BlockAnswer>(rule.conditions.size)
    for (condition in rule.conditions) {
        val kind = condition.kind ?: return null
        answers += BlockAnswer(kind, answerOf(kind, condition))
    }
    answers.sortWith(ANSWER_ORDER)
    return answers.distinct()
}

/**
 * 一条条件的答案取值 —— 「前台应用」那一条要先归一。
 *
 *  那一条是**「或」**（`包1||包2`，任一在前台即通过），所以 `包1||包2` 与 `包2||包1` 是同一组
 *  应用：排个序再比，否则同一组应用写两遍会被当成两块不同的场景。
 */
private fun answerOf(
    kind: ConditionKind,
    condition: BranchCondition,
): String =
    if (kind.isAppFamily) {
        condition.appPackages
            .distinct()
            .sorted()
            .joinToString(BranchCondition.APP_SEPARATOR)
    } else {
        condition.effectiveValue
    }

/** 答案的排序：先按种类（枚举顺序，稳定），同一种再按取值。 */
private val ANSWER_ORDER: Comparator<BlockAnswer> =
    compareBy<BlockAnswer> { it.kind.ordinal }.thenBy { it.value }

/**
 * 表里**条件与上面某一块完全一样**的那几块。
 *
 * ## 为什么比「块」而不比「条件」
 *
 *  条件那一层不能去重：`[微信 + 充电]` 与 `[微信 + 耳机]` 是两条不同的场景，前者不许把后者
 *  那条「微信」堵掉（见 [blockKindsOf] 的「跨块不禁」）。**整块一样**才是真的重复。
 *
 * ## 只看条件，不看手势
 *
 *  一块本来就是「一组条件 + 三档手势」（见 `SceneRule`），同一组条件在表里只该有一块 ——
 *  真要按手势分工，把三档都配在同一块里就行。分成两块的话，判定侧「从上往下第一条成立的赢」
 *  会让后一块那几档轮不到，而界面上两块长得一模一样，用户分不清哪一块在管哪一档。
 *
 *  **这条判据不能看手势**：一块刚加进来时三档全是「跟随默认」，而这一页的顺序本来就是
 *  「先配条件、动作进「场景动作」页慢慢挑」。拿「配了动作的那几档」当判据的话，用户在配条件
 *  这一步永远看不到重复，要等配完动作才冒出来 —— 那时两块之间已经改过一遍了。
 *
 * ## 停用的块不算
 *
 *  停用的块整块跳过（`SceneRule.matches`），既不参与判定、也就不挡后面的块；它自己也不该
 *  被标成重复 —— 启用回来的时候要能照常生效。
 *
 * @return 那几块的下标（**后出现的那一块**才在下标里：先出现的永远赢）
 */
internal fun duplicateBlockIndexes(scenes: List<SceneRule>): Set<Int> {
    val seen = HashSet<List<BlockAnswer>>(scenes.size)
    val duplicated = LinkedHashSet<Int>()
    scenes.forEachIndexed { index, rule ->
        if (!rule.enabled) return@forEachIndexed
        val answers = blockAnswersOf(rule) ?: return@forEachIndexed
        //  只有**先出现**的那一块占得住这个答案，后面的都算重复
        if (!seen.add(answers)) duplicated += index
    }
    return duplicated
}
