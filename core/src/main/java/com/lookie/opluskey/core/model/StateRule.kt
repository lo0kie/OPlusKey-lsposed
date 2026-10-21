package com.lookie.opluskey.core.model

/**
 * 一条**条件分支**：这个手势在 [condition] 成立时改执行 [action]。
 *
 * 挂在 `KeyConfig.rules` 上，**按顺序匹配、第一条成立的赢**（见 `KeyConfig.actionFor`）；
 * 一条都不中就走这颗键这一档**本来绑定的动作**（`KeyConfig.actionOf`）。表是空的（默认）⇒
 * 行为与没有这一档完全相同。
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
 * 条件成不成立要读系统开关（前台应用 / 亮屏 / 响铃档位…），那是宿主侧的事
 * （`ConditionProbe`）。所以匹配由调用方传进来，这一层只管「顺序」与「第一条赢」。
 *
 * ## [condition] 可以为空（还没选）
 *
 * 界面上「添加条件」只是往表里插一行，**不替用户先填一个默认条件**（先填「亮屏中」看着像
 * 用户自己选的，实际上会静默接管这一档，比空着更危险）。空条件的那一行就是「还没选」：
 * 它在执行侧**永不成立**（见 [appliesTo]），界面上拦着不许保存。
 */
data class StateRule(
    val gesture: Gesture,
    val condition: BranchCondition?,
    val action: Action,
) {
    /**
     * 这一条在「[gesture] 这次手势」下成立吗 —— [matches] 是那条条件的判据。
     *
     *  条件空着的行**不成立**：宁可落到下面一条 / 落到按键绑定那一档，也不执行一条用户还没
     *  想清楚的条件（那是「填了一半的规则」）。
     */
    fun appliesTo(
        gesture: Gesture,
        matches: (BranchCondition) -> Boolean,
    ): Boolean {
        val cond = condition ?: return false
        return this.gesture == gesture && matches(cond)
    }
}
