package com.lookie.opluskey.inject.input

import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboPlan

/**
 * 一次按键按下 —— 判定「凑没凑齐」只要这两样：哪颗键、什么时候。
 *
 *  `atMs` 的基准由采集点给，判据只比较**同一条流内**的差值（与 [RawInput] 同一条纪律）。
 */
internal data class ComboDown(
    val keyId: String,
    val atMs: Long,
)

/** 「这一串按下凑成了什么」—— 只回答形态，不含动作、不含长按（那要抬手时刻，见 [ComboSequence]）。 */
internal sealed interface ComboGather {
    /** 凑齐了。`atMs` 是最后一颗到齐的时刻 —— 长按从这一刻起算。 */
    data class Gathered(
        val plan: ComboPlan,
        val atMs: Long,
    ) : ComboGather

    /** 还差几颗，但这条路仍然成立（等下一颗）。 */
    data class Pending(
        val plan: ComboPlan,
        val missing: Int,
        val lastAtMs: Long,
    ) : ComboGather

    /** 一条路都不成立。 */
    data object None : ComboGather
}

/**
 * 「这一串依次按下」算不算某一档组合 —— **纯判据**，无状态、不读配置、不碰事件流。
 *
 * ## 与判定器是同一套规则的两种形态
 *
 *  `ComboDetector` 是**事件状态机**：它管吞键、管退回单键判定、管长按计时与连续触发 ——
 *  那一套必须长在事件流上。同一套「凑齐没有」的规则在这里写成**无状态的纯函数**，用途只有一个：
 *  让 `probe/combo` 在不造假事件流的前提下把规则跑一遍（真机上一次按压只有一条证据，
 *  判错了没人能重放）。
 *
 *  **改动必须两边同改**：判断器那边是 `ComboDetector.pairFor`（两颗）与 `multiFor`（3 颗起），
 *  这里是它的镜像。两边口径差一条，探针就会在「真机坏了」的时候报正常 —— 那比没有探针更糟。
 *
 * ## 判据（与判定器逐条对齐）
 *
 *  1. **顺序按下也算凑齐**（现有语义：几颗的 DOWN 相差不超过窗口，不要求同时按着）；
 *  2. 窗口按**最后一下**算：每一颗与最后那一颗相差不超过这一档的量 —— 而这个量是**按成员数
 *     放大的**：`collectMs` 说的是「相邻两颗最多隔多久」，n 颗就允许 (n-1) 个窗口
 *     （判定器那边同一个数，见 `ComboWindows.spanFor` / `Config.comboSpanFor`）。
 *     两颗时它就是单个窗口；三颗按一个窗口判的话，三根手指必须几乎同时落下，真机等于凑不齐；
 *  3. 这一串里每颗都必须是该档成员，**夹在中间的非成员键不影响结果**（对应判定器里
 *     「配不上的候选不占位」；早先写成「作废」是错的：那会让「A → 蹭到 C → B」这种
 *     真实按法整个落回单击）；**同一颗按了两次**按最后那一次算（判定器的 `recent` 只留
 *     最后一条，它的 `down` 认出「上一次的 UP 丢了」时还会整块重置重算）；
 *  4. 几档同时可能时，**成员多的赢**（与 `Config.comboFor` 的 `maxByOrNull { keys.size }` 一致）；
 *     成员数相同取先凑齐的那一档，再相同按 id 定序 —— 不靠列表顺序，配置重排不该改结果。
 */
internal object ComboSequence {
    /**
     * @param downs 这一串按下，按发生顺序
     * @param plans 候选（调用方**先滤掉判定器认不出的形态**：`judgeable == false` 的那几档）
     */
    fun resolve(
        downs: List<ComboDown>,
        plans: List<ComboPlan>,
    ): ComboGather {
        if (downs.isEmpty() || plans.isEmpty()) return ComboGather.None
        val results = plans.mapNotNull { gather(it, downs) }
        //  **先看有没有真凑齐的**：凑齐比「还差几颗」优先，否则一颗键的按下会盖住已经成立的组合
        results
            .filterIsInstance<ComboGather.Gathered>()
            .minWithOrNull(
                compareByDescending<ComboGather.Gathered> { it.plan.size }
                    .thenBy { it.atMs }
                    .thenBy { it.plan.id },
            )?.let { return it }
        return results
            .filterIsInstance<ComboGather.Pending>()
            .minWithOrNull(
                compareByDescending<ComboGather.Pending> { it.plan.size }
                    .thenBy { it.plan.id },
            ) ?: ComboGather.None
    }

    /**
     * 单档的判定：这一串按下对它来说是「凑齐 / 还差几颗 / 此路不通」。
     *
     *  一条路不成立时返回 `null`（而不是 `None`）—— 多档并行判断时，`None` 只有「全都不成立」才算。
     */
    private fun gather(
        plan: ComboPlan,
        downs: List<ComboDown>,
    ): ComboGather? {
        //  一颗不叫组合：单成员的「组合」配得出来也没有判定意义（下限是 [MIN_SIZE]）
        if (plan.size < MIN_SIZE) return null
        //  只看属于这一档的那些按下 —— 夹在中间的非成员键不影响结果（判据 3）。
        //  同一颗按了两次按**最后那一次**算（`associateBy` 留最后一条），与判定器 `recent` 同一条。
        val hits = downs.filter { it.keyId in plan.keys }.associateBy { it.keyId }.values
        val first = hits.minOfOrNull { it.atMs } ?: return null
        val last = hits.maxOf { it.atMs }
        //  窗口按最后一下算：每一颗与最后那一颗相差不超过这一档的**整串跨度**（判据 2）——
        //  `collectMs` 是相邻两颗的量，n 颗要 (n-1) 个窗口。封顶与 `Config.comboSpanFor` 同一条。
        val gaps = (plan.size - 1).coerceIn(1, ComboKey.MAX_KEYS - 1)
        if (last - first > plan.collectMs * gaps) return null
        return if (hits.size >= plan.size) {
            ComboGather.Gathered(plan, last)
        } else {
            ComboGather.Pending(plan, plan.size - hits.size, last)
        }
    }

    /** 一颗不叫组合 —— 与 `ComboKey.MIN_KEYS` 同一个意思，判据这一层自己留一份。 */
    private const val MIN_SIZE = 2

    /** 自检用：把结论打成人话（诊断通道与将来的日志都用它，别各写一份）。 */
    fun describe(gather: ComboGather): String =
        when (gather) {
            is ComboGather.Gathered -> "凑齐 ${gather.plan.id}（${gather.plan.size} 颗，到齐于 ${gather.atMs}ms）"
            is ComboGather.Pending -> "还差 ${gather.missing} 颗（${gather.plan.id}）"
            ComboGather.None -> "没有凑成任何一档"
        }
}
