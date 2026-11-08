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
 *  @param collectMs 相邻两颗成员的按下时刻最多相差多久 —— 这一档自己的那一个
 *    （`Config.comboWindowFor`：设过就用它，没设就是公共那份 `GlobalConfig.comboWindowMs`），
 *    两颗与多成员走同一个数
 *  @param longPressMs 凑齐之后一起按住多久算长按（这一档自己的值，不取某个成员键的）
 *  @param judgeable 判定器认不认这个形态。现在是「够两颗就认」（多成员这一轮已接线），
 *    这一位留着是为了**下次放开上限时**还能在日志里说清「配了、但判定器不认」，
 *    而不是让那种状态重新变成「按了没反应、日志里什么也没有」
 */
data class ComboPlan(
    val id: String,
    val keys: Set<String>,
    val collectMs: Int,
    val longPressMs: Int,
    val judgeable: Boolean,
) {
    /** 成员数 —— 判定凑齐与否、多档候选时挑哪一档，都按它比。 */
    val size: Int get() = keys.size

    /** 判据行：一眼看出「这一档要几颗、等多久、现在能不能判出来」。 */
    override fun toString(): String =
        "$id：${keys.size} 颗（${keys.sorted().joinToString("+")}）/ 窗口 ${collectMs}ms" +
            " / 长按 ${longPressMs}ms / ${if (judgeable) "可判" else "判定器暂不认这个形态"}"
}

/**
 * 一档组合 → 判定计划（**只读投影**，不改配置、不影响当前判定）。
 *
 *  @param collectMs 由调用方给：`Config.comboWindowFor`（这一组自己的，没设就是公共那一档）。
 */
fun ComboKey.plan(collectMs: Int): ComboPlan =
    ComboPlan(
        id = id,
        keys = keys.toSet(),
        collectMs = collectMs,
        longPressMs = longPressMs,
        //  判定器现在认得任意成员数（两颗走两两配对、3 颗起走成员全集），
        //  只要求「够两颗」——与 `ComboKey.isUsable` 同一条下限。
        judgeable = keys.size >= ComboKey.MIN_KEYS,
    )

/**
 * 这份配置里**用得上**的组合各自的判定计划 —— 关掉的、成员不足的都不进来
 * （判据与 `Config.comboFor` 一致：`isUsable`）。
 *
 *  成员数已经不再影响能不能判：手改文件写进来的三颗组合也在表里、也是 `judgeable`，
 *  判定侧（`ComboDetector` 的多成员路径）直接照它凑。
 */
fun Config.comboPlans(): List<ComboPlan> =
    combos
        .filter { it.isUsable }
        .map { it.plan(comboWindowFor(it)) }
