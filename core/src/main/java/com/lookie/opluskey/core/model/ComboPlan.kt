package com.lookie.opluskey.core.model

/**
 * 一档组合键的**判定计划** —— 判定侧在第一颗键按下之前就要知道的那几件事。
 *
 *  为什么要单独一层：判定要的不是「两颗」这个数字，而是**这一档的形态描述** ——
 *  成员是谁、相邻两颗最多隔多久、凑齐之后按住多久算长按。有了它，判定侧只回答
 *  「这一串按下是否凑成了某一档」，与「一档到底几颗」解耦；两颗那一档仍走两两配对
 *  （`ComboDetector.pairFor`），3 颗起走多成员凑齐（`multiFor`），两条路都读这份计划。
 *
 *  @param keys 成员（**集合语义**：顺序不是身份，见 `ComboKey` 的类注释）
 *  @param collectMs **相邻**两颗成员的按下时刻最多相差多久 —— 这一档自己的那一个
 *    （`Config.comboWindowFor`：设过就用它，没设就是公共那份 `GlobalConfig.comboWindowMs`），
 *    两颗与多成员走同一个数。判「凑没凑齐」时按成员数放大成整串的跨度：
 *    n 颗 ⇒ `collectMs × (n-1)`（`Config.comboSpanFor`，判定器与镜像同一口径）——
 *    三颗也按「第一颗到第三颗只隔一个窗口」判的话，手指根本按不出来。
 *  @param longPressMs 凑齐之后一起按住多久算长按（这一档自己的值，不取某个成员键的）
 *  @param mode 这一档的触发方式（`ComboKey.MODE_*`）—— 判定侧按它选判据，日志按它说清
 *    「按住哪一颗才算」；解析时已经由 `ComboKey.modeFor` 夹过（三颗及以上只会是窗口那条），
 *    所以这里不再有「配了但判不出来」的形态
 */
data class ComboPlan(
    val id: String,
    val keys: Set<String>,
    val collectMs: Int,
    val longPressMs: Int,
    val mode: Int,
) {
    /** 成员数 —— 判定凑齐与否、多档候选时挑哪一档，都按它比。 */
    val size: Int get() = keys.size

    /** 判据行：一眼看出「这一档要几颗、怎么触发、等多久」。 */
    override fun toString(): String =
        "$id：${keys.size} 颗（${keys.sorted().joinToString("+")}）/ " +
            "${if (mode == ComboKey.MODE_WAIT) "等窗口" else "先按住第 $mode 颗"} / 窗口 ${collectMs}ms" +
            " / 长按 ${longPressMs}ms"
}

/**
 * 一档组合 → 判定计划（**只读投影**，不改配置、不影响当前判定）。
 *
 *  @param collectMs 由调用方给：`Config.comboWindowFor`（这一档自己的，没设就是公共那一档）。
 */
fun ComboKey.plan(collectMs: Int): ComboPlan =
    ComboPlan(
        id = id,
        keys = keys.toSet(),
        collectMs = collectMs,
        longPressMs = longPressMs,
        mode = mode,
    )

/**
 * 这份配置里**用得上**的组合各自的判定计划 —— 凑不齐的都不进来。
 *
 *  口径与判定侧同一条（[Config.liveCombos]：自己关着、成员不足数、或任一成员键被停用），
 *  于是「日志里这一档在表里」与「按键时真的会为它等窗口」永远说的是同一件事。
 *
 *  成员数不影响能不能判：手改文件写进来的三颗组合也在表里、也是 `judgeable`，
 *  判定侧（`ComboDetector` 的多成员路径）直接照它凑。
 */
fun Config.comboPlans(): List<ComboPlan> = liveCombos().map { it.plan(comboWindowFor(it)) }
