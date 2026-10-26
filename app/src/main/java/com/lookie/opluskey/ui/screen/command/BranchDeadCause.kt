package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.SettingsPage

// 「哪几行永远轮不到」的**唯一一处扫描** —— 与 `CommandBranches.kt` 分开一支，一是让那个文件级
// 函数数量留在 detekt 的 `TooManyFunctions` 之下，二是这件事本来就只有「怎么算」一个问题。

/**
 * 这一行为什么永远轮不到 —— 两种原因，要说的话不一样。
 *
 *  · [BranchDeadCause.SATURATED]：前面某一档条件的**取值已经凑齐**（亮屏 / 锁屏各一条）⇒ 从那之后每一行都轮不到；
 *  · [BranchDeadCause.SUBSUMED]：前面某一行的条件**更宽、且被这一行包含**（`[亮屏]` 在前、`[亮屏 + 微信]` 在后）⇒
 *    这一行即使成立，也已经有一条在前面赢了。
 *
 *  为什么要分开：两种说法完全不同 —— 前者是「整张表已经恒成立」，后者是「这一条被前面那条盖住了」。
 *  合成一句「后面的条件不会执行」会把第二种说错（前面那条并不恒成立）。
 */
internal enum class BranchDeadCause {
    SATURATED,
    SUBSUMED,
}

/**
 * 点「确认」时那句拦截文案（`null` = 没有死分支，可以存）。
 *
 *  与行上那枚「不会执行」（[deadIndexesOf]）同一条扫描，所以「拦不拦保存」与「哪几行标死」
 *  永远是同一批行：一处改了另一处不会各说各的。
 */
internal fun SettingsPage.branchDeadReason(): String? =
    when (deadEndOf(branchDraft.draft.rules)?.second) {
        null -> {
            null
        }

        BranchDeadCause.SATURATED -> {
            I18n.get(Strings.COMMAND_BRANCH_SATURATED, "当前场景恒成立，后面的场景不会执行")
        }

        //  文案只说结论，不讲「为什么轮不到」：哪一行不会执行，行上那枚标记已经写着
        BranchDeadCause.SUBSUMED -> {
            I18n.get(Strings.COMMAND_BRANCH_SUBSUMED, "有一条场景不会执行")
        }
    }

/** 哪几条是**死分支** —— 从第一条「已经轮不到」的行起，一直到表尾。 */
internal fun deadIndexesOf(rules: List<StateRule>): Set<Int> {
    val from = deadEndOf(rules)?.first ?: return emptySet()
    return (from..rules.lastIndex).toSet()
}

/**
 * 按顺序扫一遍，返回**第一条永远轮不到的行**以及原因 —— `null` = 没有死分支。
 *
 *  分叉是「从上往下找、第一条成立的赢」，所以「轮不到」只有两种来路（见 [BranchDeadCause]）：
 *
 *  ## 一、某一档的取值在前面凑齐了
 *
 *  凑齐之后，不管这一行要求什么，都已经有一条在前面赢过 —— **从这一行起，后面全部是死的**。
 *  注意判的是「**前面**凑齐过没有」，不是「这一行自己那条条件的档凑齐没有」：
 *  `[亮屏, 锁屏, 充电中]` 里第三行自己的档（充电）当然没凑齐，可屏幕那两条已经把每一下都接走了。
 *  （这一条写错过一次，用户报「死分支判断怎么没了」，所以摆在最显眼的地方。）
 *
 *  ## 二、前面某一条**更宽**，把这一条整个包含住了
 *
 *  这是「组合条件」要的细致判定：只看「某一档凑齐」抓不到 `[亮屏]` 之后那条 `[亮屏 + 微信]` ——
 *  屏幕那一档并没有凑齐（只写了亮屏），可是**只要 `[亮屏 + 微信]` 成立，`[亮屏]` 必定也成立**，
 *  而它在前面。所以判据是**条件集合的包含关系**：前面某一行的全部条件 ⊆ 这一行的条件 ⇒ 这一行
 *  永远轮不到。同一条规则原样写两遍（集合相等）也落在这一支。
 *
 *  两条都成立时先报第一条（凑齐）：它说明的是「整张表到头了」，比「这一条被盖住」更根本。
 *
 * ## 记账只算单条件的行
 *
 *  「凑齐」要的是「这一档的每个取值都有人接」，而多条件的行要求别的也成立，它赢了**不等于**这一档
 *  就有人接了 —— 让它记账会把后面的行误判成死的。反过来，多条件的行**被判死**没这个问题：无论按
 *  上面哪一条，它本来就已经轮不到了。
 *
 *  空条件 / 取值没配完的行、没有候选的档（前台应用）不进账：它们本来就不参与「恒成立」。
 */
private fun deadEndOf(rules: List<StateRule>): Pair<Int, BranchDeadCause>? {
    //  每一档**已经出现过**的取值 + **已经凑齐**的档（都只看当前位置**之前**）
    val seen = mutableMapOf<ConditionKind, MutableSet<String>>()
    val saturated = mutableSetOf<ConditionKind>()
    //  前面那些「条件集合」—— 判「更宽的条件」用
    val earlier = mutableListOf<Set<Pair<String, String>>>()
    for ((index, rule) in rules.withIndex()) {
        //  先判死、再记账：反过来的话「把这一档凑齐的那一行」会把自己标死（它还要接最后一个取值）
        if (saturated.isNotEmpty()) return index to BranchDeadCause.SATURATED
        val signature = signatureOf(rule)
        if (signature != null && earlier.any { it.isNotEmpty() && signature.containsAll(it) }) {
            return index to BranchDeadCause.SUBSUMED
        }
        if (signature != null) earlier += signature
        //  记账：只有「单条件、取值可用」的行才替它那一档凑数
        val only = rule.conditions.singleOrNull()?.takeIf { it.isUsable }
        val kind = only?.kind?.takeIf { it.choices.isNotEmpty() }
        if (only != null && kind != null) {
            val mine = seen.getOrPut(kind) { mutableSetOf() }
            mine += only.effectiveValue
            if (kind.choices.all { it in mine }) saturated += kind
        }
    }
    return null
}

/**
 * 这一行的条件集合（`档 id + 取值`）—— 判「谁包含谁」用。
 *
 *  `null` = 这一行不参与：没有条件，或者有认不出 / 取值没配完的那一条（那样的行本来就不该成立，
 *  拿它去比包含关系只会得出假结论）。
 */
private fun signatureOf(rule: StateRule): Set<Pair<String, String>>? {
    if (rule.conditions.isEmpty()) return null
    val keys =
        rule.conditions.map { condition ->
            val kind = condition.kind
            if (kind == null || !condition.isUsable) return null
            kind.id to condition.effectiveValue
        }
    return keys.toSet()
}
