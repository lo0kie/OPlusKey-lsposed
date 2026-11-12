package com.lookie.opluskey.ui.row

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.ui.model.page.STEP_MS
import com.lookie.opluskey.ui.model.text.durationText
import kotlin.math.roundToInt

/**
 * 一根滑杆的**全部规格**：显示什么（标题 / 摘要 / 行首图标）+ 量什么（区间、当前值、出厂值、步长）。
 *
 *  合成一个类型是为了让 [SettingsPage.SliderRow] 的参数表停在一只手数得过来的量级 ——
 *  这些本来就是一件事（「这一行是一根什么样的滑杆」），一起给比散在五个形参里更好读。
 *
 * @param step 相邻两档的**目标**步长 —— 轨道上有几档由它算；区间不整除时真实档距是均分出来的
 *   （见 [ticks] / [tickStep]），提示里报的是真实那一档。
 * @param enabled 这一根滑杆能不能动
 * @param disabledHint 灰着被点一下时**这一行自己的说法**（`null` = 用整页那句，见 [DisabledHint]）。
 *   与 `CardRow.Item.disabledHint` / `CardRow.Switch.disabledHint` 同一个意思、同一套判据：
 *   整页那条答的是「这一页为什么改不了」（主开关 / 链路 / 只读），有些滑杆是**因为它自己
 *   这一行的条件不够**才灰的（「使用独立设置」没开、长按动作不支持连发、排序中整页锁住），
 *   拿整页那句回答它，等于答了另一个问题。只在 `enabled = false` 时生效。
 * @param valueText 主标题**右侧**那个「当前值」怎么写（毫秒那几档默认走 [durationText]）；
 *   `null` = **不画**（档位那种一眼看得出当前值的滑杆，再写一遍是重复）
 * @param keyPoints 要在轨道上**画出来的**刻度点；`null` = 只画出厂默认值那一个圆点。
 *   库那边 `showKeyPoints` 单独打开不画东西 —— 它读的是 `keyPoints`，两者要一起给
 *   （见 COUI Slider 文档：`showKeyPoints` 仅当 `keyPoints` 不为 null 时有效）。
 */
internal data class SliderSpec(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val min: Int,
    val max: Int,
    val value: Int,
    val default: Int,
    val step: Int = STEP_MS,
    val enabled: Boolean = true,
    val disabledHint: String? = null,
    val valueText: ((Int) -> String)? = { durationText(it) },
    val keyPoints: List<Float>? = null,
) {
    /** 把任意值**吸到这一根滑杆的刻度上** —— 手动输入那一档用它（见 [snapToStep]）。 */
    fun snap(raw: Int): Int = snapToStep(raw, min, max, step)

    /**
     * 轨道上分成几档（含两端）—— 与 `Slider.steps`、[snapToStep] 同一套。
     *
     *  **提示与真实档距必须同源**：区间不整除时，真实一档是 `span / ticks` 均分出来的，
     *  与参数里的 [step] 不是同一个数。各说各的时输入窗写着「步进 50」、轨道上一档却是 51。
     */
    val ticks: Int get() = sliderTickCount(min, max, step)

    /** 真实的一档多宽（见 [ticks]）——输入窗那句「步进」用它，不用 [step]。 */
    val tickStep: Int get() = ((max - min).coerceAtLeast(1)).toDouble().div(ticks).roundToInt()
}

/**
 * 两端之间分几档（含两端）—— 全项目唯一一份，`Slider.steps` 与 [snapToStep] 都从这里取。
 *
 *  `step` 为 0 时夹到 1（否则除法直接抛 ArithmeticException，崩在组合期）。
 */
internal fun sliderTickCount(
    min: Int,
    max: Int,
    step: Int,
): Int = ((max - min).coerceAtLeast(1) / step.coerceAtLeast(1)).coerceAtLeast(1)

/**
 * 把任意值吸到**刻度**上：两端之间分 `(max-min)/step` 格（与库 `Slider.steps` 同一套，
 * `steps` 不含两个端点）。区间不整除时按格数均分，不是拿 `step` 硬乘 —— 那两套值会差一格。
 *
 *  两个入口共用：`tapSliderToSeek` 把「点在轨道上的相对位置」折成值，以及**手动输入**把用户
 *  打的数吸回档位上。后者是新加的那一档：不吸的话配置里会存进 260 这种「不在档位上」的数，
 *  滑杆画出来滑块夹在两档之间 —— 看着就像 step 失效了。
 */
internal fun snapToStep(
    value: Int,
    min: Int,
    max: Int,
    step: Int,
): Int {
    val span = (max - min).coerceAtLeast(1)
    //  档数与 `Slider.steps` 同一处（见 [sliderTickCount]）：两边各算一份时会在「区间不整除」时差一档。
    val ticks = sliderTickCount(min, max, step)
    val idx = ((value.coerceIn(min, max) - min).toFloat() / span * ticks).roundToInt()
    return (min + idx * span.toDouble() / ticks).roundToInt().coerceIn(min, max)
}
