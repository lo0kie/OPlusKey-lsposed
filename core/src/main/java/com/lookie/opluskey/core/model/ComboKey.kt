package com.lookie.opluskey.core.model

/**
 * 组合按键：**两颗键一起按下**时执行的动作（成员数恒为 [ComboKey.MIN_KEYS] = 两颗）。
 *
 * ## 只支持短按与长按
 *
 * 双击这一档在组合键上不存在：判双击要的是「两次按压都落在同一个窗口内」，而组合键先要判
 * 「这几颗键同时按着」，两个窗口叠在一起没有能讲清楚的口径。所以 [actionOf] 对
 * [Gesture.DOUBLE] 恒返回 [Action.None] —— 不是「还没实现」，是这个档位在这里没有定义。
 *
 * ## 身份是 [keys] 这个集合，不是按下的顺序
 *
 * 两颗键「同时按」时，上报顺序由驱动决定、用户也控制不了。拿顺序当判据等于让配置依赖硬件细节，
 * 所以判等只看集合（[matches]）。
 *
 * ## 与单键的关系
 *
 * 参与组合的键同时也是普通的键：它们各自的单击 / 双击 / 长按照常在 `KeyConfig` 里配。谁先响应、
 * 要不要把单键那一下吞掉，是**判定侧**的事（不在本类）—— 这里只回答「这几颗键凑齐了是哪一档」。
 */
data class ComboKey(
    /** 稳定身份（`cmb1` `cmb2`）—— 与 `KeyId.id` 同一套用法：改成员不换身份。 */
    val id: String,
    /** 参与的键（`KeyId.id`，去重后正好 [MIN_KEYS] 颗 —— 下限与上限现在是同一个数）。 */
    val keys: List<String>,
    val enabled: Boolean = true,
    /** 短按：按下之后在 [longPressMs] 之内松开。 */
    val single: Action = Action.None,
    /** 长按：一起按住超过 [longPressMs]。 */
    val long: Action = Action.None,
    val longPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
    /**
     * 「长按连续触发」—— 两颗键一起按住不放时，按 [longRepeatIntervalMs] 反复执行 [long]。
     *
     *  与单键那一档同一套语义（`KeyConfig.longRepeat`）：松手就停，抬手事件丢了有上限兜底。
     *  默认关：按住不放重复执行是个响得很大的行为，不该一开始就有。
     */
    val longRepeat: Boolean = false,
    /** 连续触发的间隔（毫秒）—— 只在 [longRepeat] 为真时有意义。 */
    val longRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    /** 连续触发时每一次重复都振动（关 = 只在判出长按那一下振一次）。 */
    val longRepeatVibrate: Boolean = false,
    /**
     * **条件执行**（与单键 `KeyConfig.rules` 同一套 [StateRule]、同一个判定口径）。
     *
     *  按顺序匹配、第一条成立的赢；一条都不中才走 [actionOf] 那一档绑定。
     *  空表（默认）⇒ 行为与没有这一项完全相同 —— 老配置读回来就是这个状态。
     *
     *  为什么组合键也值得有：组合键本身就是「我特意按了两颗键」，它比单键更常需要
     *  「在什么情况下才做」的区分（比如「在公司电脑上才切窗口」）。
     *
     *  手势只有 [Gesture.SINGLE] / [Gesture.LONG] 两档（见类注释），所以这里的规则也只认这两档；
     *  手改配置写进来的 `double` 规则永远不会命中（[actionOf] 对双击恒 `None` 同一条道理）。
     */
    val rules: List<StateRule> = emptyList(),
) {
    /** 这一档现在算不算数 —— 关掉了、或成员不足两键（配置文件被手改坏）都不算。 */
    val isUsable: Boolean
        get() = enabled && keys.size >= MIN_KEYS

    /** [gesture] 这一档的动作；双击恒为 `None`（见类注释）。 */
    fun actionOf(gesture: Gesture): Action =
        when (gesture) {
            Gesture.SINGLE -> single
            Gesture.LONG -> long
            Gesture.DOUBLE -> Action.None
        }

    /**
     * **带条件表**的取动作：先看 [rules]（顺序、第一条赢），都不中才落到 [actionOf]。
     *
     *  与单键 `KeyConfig.actionFor` 是同一份语义，两份实现只差在类上 —— 合并它俩得先让
     *  `KeyConfig` 与 `ComboKey` 共一个父类型，而它们除了这一处之外没有共同字段。
     *
     *  `matches` 由调用方给（读系统开关的那一层，见 `ConditionProbe`）：这一层不认识系统接口。
     */
    fun resolve(
        gesture: Gesture,
        matches: (BranchCondition) -> Boolean,
    ): Action = rules.firstOrNull { it.appliesTo(gesture, matches) }?.action ?: actionOf(gesture)

    /** [down] 里包含这一档的全部成员吗（不看顺序、允许同时按着别的键）。 */
    fun matches(down: Set<String>): Boolean = keys.all { it in down }

    companion object {
        /** 组合键 id 的前缀：`cmb1` / `cmb2` ……（序号由收集侧维护，只增不减）。 */
        const val ID_PREFIX = "cmb"

        /** 至少两颗 —— 一颗不叫组合。 */
        const val MIN_KEYS = 2

        /**
         * 成员上限：两颗（用户口径 —— 一个组合就是**两颗键一起按**，不多不少）。
         *
         *  与 [MIN_KEYS] 相等，于是「组合」在模型上只有一种形状：两颗。判定侧也只认两颗
         *  （`ComboDetector` 从第二颗键按下的时刻起算长按），多配的那几颗永远等不到。
         *
         *  这两者是两个常量、不是一个：将来真要放开到三颗，改上限即可，下限那一颗的意思
         *  （「一颗不叫组合」）不会跟着上限走。
         */
        const val MAX_KEYS = 2

        /** 第 [seq] 档组合键的 id。 */
        fun idOf(seq: Int): String = ID_PREFIX + seq
    }
}
