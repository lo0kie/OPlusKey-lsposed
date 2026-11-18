package com.lookie.opluskey.inject.input

/**
 * 判定层的手势档位 —— 这一层**自己的**词汇，不认识配置文件、不认识动作。
 *
 * ## 为什么判定层要有一份自己的手势类型
 *
 *  判定器回答的问题只有一个：「这一下按成了什么形态」。它不该知道这个形态叫「双击」还是
 *  `click2`、也不该知道用户在那一档上绑了什么 —— 那些是配置与展示层的事。原来它直接吐
 *  `core.model.GestureTrigger`（那一型带着配置 id、界面文案、甚至绑定的动作），于是判定层与
 *  业务模型绑在一起：业务那边加一档手势，这边要跟着理解它。
 *
 *  收成这五档之后，判定层**只会产出它真的判得出来的五种形态**（1 到 N 击、按住、按住后松手、
 *  单击后按住、单击后按住再松手）。哪一档对应哪个动作、要不要等你设的窗口，全在装配方翻译
 *  （见 `inject/key/GestureBridge`）。
 *
 * ## 与 `GestureTrigger` 的关系
 *
 *  一一对应，但不是同一个类型：`GestureTrigger` 是落盘 / 广播 / 界面用的档位 id，可以随配置长
 *  出新取值（例如将来某一档两段式再分叉）；这一层只在形态上分，**不多也不该多**。
 */
sealed interface Gesture {
    /** 连击 [count] 下（1 = 单击）。 */
    data class Click(
        val count: Int,
    ) : Gesture

    /** 按住不放超过长按阈值。 */
    data object LongPress : Gesture

    /** 长按之后松手那一刻（只有这一按已经判成长按才会发）。 */
    data object Release : Gesture

    /**
     * 连点 [count] 下之后再按住不放（两段式的下一击按住）。
     *
     *  [count] 是**前面那几击的击数**：1 = 单击后长按（第 2 击按住）、2 = 双击后长按、
     *  3 = 三击后长按。判定层只按形态分档，具体哪一档对应什么动作仍由装配方翻译。
     */
    data class ClickThenLong(
        val count: Int = 1,
    ) : Gesture

    /**
     * 「后长按」那一档的松手 —— 三档各有自己那一格松手动作，`count` 与
     * [ClickThenLong.count] **同一个口径**（前面那几击的击数）。
     *
     *  不带这一位的话，注入侧只能报「有一档松手该执行」，装配方无从知道是三档里的哪一档 ——
     *  三档会全部落到第 1 击那一格（`clickThenLongRelease`）。
     */
    data class ClickThenLongRelease(
        val count: Int = 1,
    ) : Gesture

    /** 日志用的人话 —— 判定层只说到「形态」为止，不进字典、不带配置 id。 */
    val label: String
        get() =
            when (this) {
                is Click -> if (count == 1) "单击" else "$count 击"
                LongPress -> "长按"
                Release -> "长按松手"
                is ClickThenLong -> if (count == 1) "单击后长按" else "$count 击后长按"
                is ClickThenLongRelease -> if (count == 1) "单击后长按松手" else "$count 击后长按松手"
            }
}

/**
 * 判定器眼里的一颗键 —— **只有身份与名字，没有绑定**。
 *
 * ## 为什么不用 `KeyId`
 *
 *  `KeyId` 带着键码、认领白名单、`isCustom` 这些「这颗键怎么被认出来」的东西 —— 那是拦截层
 *  的判断，不是判定的输入。判定器唯一用得到的是「这是哪颗键」（分开各键的状态机）与「日志里
 *  怎么写它」（[name]）。
 *
 *  收成这一型之后，判定层不再认识 `core.model.KeyId`：拦住按键的那一层把 `KeyId` 翻成
 *  [KeyRef] 再喂进来，翻译只有一处。
 */
class KeyRef(
    /** 稳定身份：同一颗键的两次按下拿到相等的 [KeyRef]。 */
    val key: Any,
    /** 日志里怎么称呼这颗键。 */
    val name: String,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is KeyRef && other.key == key)

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = name
}
