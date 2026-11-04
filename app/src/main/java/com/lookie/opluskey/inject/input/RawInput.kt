package com.lookie.opluskey.inject.input

/**
 * 判定层吃的**唯一一种输入** —— 按键与触摸都先归一到这上面。
 *
 *  为什么先归一：判定器（相位 / 组合 / 滑动）本可以各自直接吃 `KeyEvent` 或 `MotionEvent`，
 *  但那样「这事件算不算我管的、时间戳怎么取」就得在**每个采集点**各写一份，而采集点只会越来越
 *  多（策略回调、输入事件、将来的触摸源），判定器却只有一份。归一之后采集点的职责只剩
 *  「原样转成 [RawInput]」，判定的全部前提（时间基准、手指数、是不是主指针）都在这一层说清。
 *
 *  时间基准（[atMs]）由**采集点**给，判定只比较同一条流内的差值：按键那一路用的是
 *  `SystemClock.elapsedRealtime()`（含深睡，见 `GestureDetector` 里那段真机记录：熄屏深睡时
 *  `uptimeMillis()` 不走，跨睡眠的双击窗口会判错）；触摸那一路用事件自己的 `eventTime`
 *  （同一段轨迹里的时长才是准的，合成事件的自检也靠它）。
 *
 *  本阶段只**铺路**：触摸事件暂时没有采集点（系统进程里那个入口还没在真机上确认），
 *  判定结果也不派发任何动作。接入时按「一个采集点 = 一个把原始事件转成 [RawInput] 的适配器」加。
 */
internal sealed interface RawInput {
    /** 事件发生的时刻 —— 基准见类注释，判定只用同一条流内的差值。 */
    val atMs: Long

    /** 一颗键按下。 */
    data class KeyDown(
        val keyCode: Int,
        override val atMs: Long,
    ) : RawInput

    /** 一颗键抬起。 */
    data class KeyUp(
        val keyCode: Int,
        override val atMs: Long,
    ) : RawInput

    /**
     * 一根手指按下。
     *
     * [pointers] 是**这一刻**屏上的手指数。滑动只认单指，多指由判定器作废（见 [SwipeJudge]），
     * 所以采集点要**原样带上手指数**，不能自己先筛掉 —— 筛在采集点就等于「谁来定义什么算单指」
     * 又散回各处。
     */
    data class TouchDown(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val pointers: Int,
        override val atMs: Long,
    ) : RawInput

    /** 一根手指移动（高频：真机 60~120 条/秒）。 */
    data class TouchMove(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val pointers: Int,
        override val atMs: Long,
    ) : RawInput

    /** 一根手指抬起 —— 这一段的结论在它这里算。 */
    data class TouchUp(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val pointers: Int,
        override val atMs: Long,
    ) : RawInput

    /** 这一段被系统收走了（来电、手势导航抢走、窗口切换）—— 判定器要立刻丢掉手上的轨迹。 */
    data class TouchCancel(
        override val atMs: Long,
    ) : RawInput
}
