package com.lookie.opluskey.inject.input

import com.lookie.opluskey.core.judge.ThenLongParams

/**
 * 判定器判一次按键**需要知道的全部** —— 十来个时间与开关，没有一样认识配置、不认识动作。
 *
 * ## 为什么不是 `ClickChainParams`
 *
 *  原先判定器直接吃 `core.model.ClickChainParams`（单键配置与组合成员连击各自实现它）。那一型
 *  是**业务视图**：它带着动作（`longRelease` / `clickThenLongRelease` 是 `Action`）、带着
 *  「这一档有没有事可做」（`clickThenLongActionExists` / `releaseBound`）、还带着场景表算出来的
 *  结论（`sceneChainRelease`）。判定器从来只用其中**两样**的非布尔部分：**动作在不在**（决定要不要
 *  派发这一档）与**排不排连发**。动作**是什么**它完全不看。
 *
 *  所以这一层只要一个值对象：所有字段都是「毫秒 / 布尔 / 次数」。从配置到它只有一处转换
 *  （`inject/key/GestureBridge` 的 `specOf`），判定层于是与业务模型彻底分开 —— 业务那边改字段、
 *  加动作、换场景判据，这一层都不用动，只要那一处转换跟得上。
 *
 * ## 每个字段的语义
 *
 *  这些名字与判定链上的判据一一对应（`core/judge/ClickChainMath`），判定器内部**不再做二次解释**：
 *
 * @param clickWindowMs 连击链的窗口（两击之间最多隔多久，也是单击要等多久）
 * @param fixedWindowStart `true` = 窗口只在首击抬手排一次（固定起点）；`false` = 每次松手重新起算
 * @param judgeCap 数到第几击就**封顶**（按开着的档算，不看绑没绑动作，至少 1）
 * @param clickTiers 这一档键**开着的点击档位**（1 恒在；2 = 双击；3 / 4 = 多击里开着的那几档）。
 *  收线时按它把击数往下取 —— 开了双击 + 四击、没开三击时，三击落在一个没有动作的空档里，
 *  见 `core.judge.settleClicks`
 * @param doubleClickOn 双击这一档开着吗
 * @param clickThenLongOn 「单击后长按」开着吗 —— 判据是**开关**，不是「有没有动作」
 * @param thenLongClicks 哪几击按住会变「后长按」—— 元素是按下那一刻的击数：
 *  2 = 单击后长按、3 = 双击后长按、4 = 三击后长按（见 `GestureTrigger.ClickThenLong.count`）
 * @param clickThenLongPressMs 第二击按住多久算「单击后长按」
 * @param clickThenLongFireOnRelease 这一档松手才触发
 * @param clickThenLongRepeatIntervalMs 「单击后长按」连续触发的间隔
 * @param clickThenLongRepeats 「单击后长按」排不排连发
 * @param clickThenLongReleaseActionExists 「单击后长按」之后松手还有事可做（决定要不要派发
 *   [Gesture.ClickThenLongRelease]）；场景块给这一档配的松手也折进这一位
 * @param longPressMs 长按阈值
 * @param longFireOnRelease 长按松手才触发
 * @param longReleaseActionExists 那个松手动作配了吗（决定要不要发 [Gesture.Release]）
 * @param longRepeats 长按排不排连发
 * @param longRepeatIntervalMs 长按连发的间隔
 */
internal data class ChainSpec(
    val clickWindowMs: Long,
    val fixedWindowStart: Boolean,
    val judgeCap: Int,
    val clickTiers: Set<Int>,
    val doubleClickOn: Boolean,
    val clickThenLongOn: Boolean,
    val thenLongClicks: Set<Int>,
    /**
     * 击数（2 / 3 / 4）→ 那一档「后长按」的判定参数；没开的那几档不在表里。
     *
     *  三档各有一套按住时长 / 连发 / 松手判据（`core.judge.ThenLongParams`），判定器只按击数取，
     *  不再读单份的 `clickThenLong*`（那几个字段只留给「这一档本身开没开」与成员连击的松手档）。
     */
    val thenLong: Map<Int, ThenLongParams>,
    val clickThenLongPressMs: Long,
    val clickThenLongFireOnRelease: Boolean,
    val clickThenLongRepeatIntervalMs: Long,
    val clickThenLongRepeats: Boolean,
    val clickThenLongReleaseActionExists: Boolean,
    val longPressMs: Long,
    val longFireOnRelease: Boolean,
    val longReleaseActionExists: Boolean,
    val longRepeats: Boolean,
    val longRepeatIntervalMs: Long,
) {
    companion object {
        /**
         * 连发间隔的下限 —— 配置里手改出来的 0 / 负数会让 `postDelayed` 变成忙等，
         *  把输入线程钉死。
         *
         *  配置层已经收过口：盘（`core/config/ConfigDto`）与广播（`core/wire/Wire`）两条读入
         *  通道都按 `ConfigFields.F_REPEAT_INTERVAL_MS` 的区间钳，下限就是
         *  `KeyConfig.MIN_REPEAT_INTERVAL_MS`（50）。所以正常装配路径上这一道夹不到任何东西。
         *
         *  判定层仍自己留一份：这一层不吃 `core.model`（见 `tools/audit-coupling.py` 那条禁令），
         *  引不到配置层那个常量，也不该假设输入只来自配置。值必须与配置层同档 —— 取更大的数
         *  会静默顶掉用户配的值，所以**改那一处时这里跟着改**。
         */
        const val MIN_REPEAT_INTERVAL_MS = 50L
    }
}

/**
 * 一档组合的**四个窗口**（毫秒）—— 判定器只要这四个数，不要配置那边的 `ComboWindows`。
 *
 *  与 `ComboWindows` 同名同义、同一种分法（为什么是四格而不是一个数，见那一型的注释）：
 *  判定层自己留一份，装配方在 `inject/key/GestureBridge` 里逐个字段搬过来。搬而不是直接引用，
 *  是为了让判定层不认识配置：那一型将来加一格、改名、换语义，判定层不受牵连。
 *
 * @param wait 等待组合、固定窗口起点**开**：从首个成员按下起，整串共用一个窗口
 * @param waitPair 等待组合、**关**：相邻两颗之间（n 颗整串 = 它 × (n-1)）
 * @param hold 按住组合、**开**：后续成员要在首个成员按下后的这个窗口内落完
 * @param holdFollower 按住组合、**关**：后续成员每颗各自往前等多久
 */
internal data class DetectorWindows(
    val wait: Long,
    val waitPair: Long,
    val hold: Long,
    val holdFollower: Long,
)
