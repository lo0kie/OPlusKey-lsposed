package com.lookie.opluskey.core.config

import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey

/**
 * **老文件（v58 以前）那一档组合**的触发方式与它当年用的那对动作槽。
 *
 * @param mode 由那两条开关 + 加过的按住顺序推出来的触发方式（[ComboKey.MODE_WAIT] 及以上）
 * @param single 短按该落到哪一个动作（老模型里按住那两档另有 A / B 两套槽）
 * @param long 长按同理
 */
internal class LegacyComboSlot(
    val mode: Int,
    val single: Action,
    val long: Action,
)

/**
 * 老文件的组合键怎么读成「一种触发方式 + 一对动作槽」—— **只在 [ComboDto.toCombo] 调一次**。
 *
 * 为什么单独一页：这套推演只服务「从老版本升上来的那一份文件」，而它读的七个字段
 * （`windowOn` / `holdOn` / `holdOrders` / `singleA` / `longA` / `singleB` / `longB`）在
 * v58 之后**写回恒为 `null` / 空表**。等确认没人再从老版本升上来，连同那一页一起删即可 ——
 * 留在 [ComboDto.toCombo] 里的话，新文件的正常解析会一直夹在这段历史里。
 *
 * 字段本身暂时不能删：`ignoreUnknownKeys` 会把不认识的键直接丢掉，字段一删，老文件里那几位
 * 就再也读不到，「用户配过按住手势」这件事会静默变成「这一档只是等窗口」
 * （见 [ComboDto.windowOn] 的说明）。
 *
 * ## 推演顺序
 *
 * 1. 认窗口（`windowOn` 缺省或为真）⇒ [ComboKey.MODE_WAIT]。窗口那条从组合键第一天起就有、
 *    而且是默认开，所以「没写过这一位」与「写过 true」同解；
 * 2. 不认窗口、也没认按住 ⇒ 那一档本来永远不成立，落到窗口那条（至少能编辑）；
 * 3. 只认按住 ⇒ 看加过哪一种顺序：只有「先按住第二颗」才推成 [ComboKey.MODE_HOLD_SECOND]，
 *    其余（含两种都加过）取 [ComboKey.MODE_HOLD]。**后者要由调用方把键序反过来** —— 现在
 *    「先按住哪一颗」由 `keys` 第一位表达，不再有第二个模式位（见 `ComboDto.toCombo`）。
 *    老模型里两种并存的组合只能留一种，另一种得再建一档。
 *
 * @return `null` = 新文件（[ComboDto.mode] 有值），调用点照常读 `single` / `long`
 */
internal fun ComboDto.legacySlot(): LegacyComboSlot? {
    //  新文件：不用迁移，调用点照常读通用那两位
    if (mode != null) return null
    return built()
}

/** 老文件那一档的推演 —— 与 [legacySlot] 同一条规则，只是不必再判 `mode` 有没有值。 */
private fun ComboDto.built(): LegacyComboSlot {
    val resolved =
        when {
            //  认窗口 ⇒ 窗口那条（`null` / `true` 同解）
            windowOn != false -> ComboKey.MODE_WAIT

            //  两条都关着的那一档本来永远不成立，落到窗口那条
            holdOn != true -> ComboKey.MODE_WAIT

            //  只加了「先按住第二颗」
            ComboKey.MODE_HOLD_SECOND in holdOrders && ComboKey.MODE_HOLD !in holdOrders -> ComboKey.MODE_HOLD_SECOND

            //  加过第一颗（含两种都加过）
            ComboKey.MODE_HOLD in holdOrders -> ComboKey.MODE_HOLD

            else -> ComboKey.MODE_WAIT
        }
    if (resolved == ComboKey.MODE_WAIT) {
        //  窗口那一档当年就用通用那两位，不必从 A / B 槽搬
        return LegacyComboSlot(ComboKey.MODE_WAIT, Action.parse(single), Action.parse(long))
    }
    val first = resolved == ComboKey.MODE_HOLD
    return LegacyComboSlot(
        resolved,
        Action.parse(if (first) singleA else singleB),
        Action.parse(if (first) longA else longB),
    )
}
