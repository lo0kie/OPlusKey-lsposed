package com.lookie.opluskey.ui

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.lookie.opluskey.ui.model.SLIDER_ICON_INSET
import com.lookie.opluskey.ui.model.SLIDER_SIDE_INSET
import com.lookie.opluskey.ui.model.SLIDER_TAP_ZONE
import kotlin.math.roundToInt

/**
 * 「点轨道跳值」手势：滑杆是 [SliderRow] 自己画的，但这条手势挂在整行外层 —— 挂到滑杆
 * 自己的 `Modifier` 上会与它内部的拖动争事件。这里按几何圈出滑杆占的那条带子
 * （[SLIDER_TAP_ZONE]），只有落在带子里的轻点才跳值；几何取自 [SliderRow] 的排版，
 * 排版若变、最坏是带子偏几 dp —— 跳值不灵或略微提前生效，不会崩。
 * 不消费事件（`requireUnconsumed = false` 且不调 `consume`）：拖动仍全归滑杆；
 * 也正因为不消费，这条挂在外层的手势才不会把滑杆的 `draggable` 顶掉。
 */
internal fun Modifier.tapSliderToSeek(
    spec: SliderSpec,
    onSeek: (Int) -> Unit,
): Modifier =
    pointerInput(spec.min, spec.max, spec.step, spec.enabled) {
        // 全部取值都从 [spec] 现读，**不再逐个收成形参**：以前这里手抄了 `min/max/enabled`，
        //  后来滑杆支持自定义步长时漏抄了 `step` —— 触点折算用的是写死的毫秒步长，
        //  三档滑杆上 `(max-min)/step` 算成 0、夹到 1，点中间只能落到端点上。
        val min = spec.min
        val max = spec.max
        val step = spec.step
        // 禁用时这条手势也得停 —— 它是**自己挂的**、不走滑杆的 `enabled`，
        // 不停的话置灰的滑杆照样能点着跳值（多条命令下的「触发间隔」）。
        if (!spec.enabled) return@pointerInput
        val slop = viewConfiguration.touchSlop
        // 轨道的左右边界：左边比右边多一个 [SLIDER_ICON_INSET]（行首图标把滑杆挤开的那一段）
        val trackStart = (SLIDER_SIDE_INSET + SLIDER_ICON_INSET).toPx()
        val trackEnd = SLIDER_SIDE_INSET.toPx()
        val zone = SLIDER_TAP_ZONE.toPx()
        awaitPointerEventScope {
            while (true) {
                val down =
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                // 没落在滑杆那条带子里 ⇒ 这次按下不归这条手势管
                // （不 consume、也不吞事件，滑杆 / 行自己照常处理它）
                //  注意：**纵向与横向都要圈** —— 只判纵向的话，横向靠后面的
                //  `coerceIn(0f, 1f)` 兜 —— 在轨道**左/右外侧**、但仍在底部那条带子里轻点会被折成 0 或 1，
                //  也就是**写 min / max**：用户只是点了行首图标那一格或行尾空白，值却被跳到端点。
                if (down.position.y < size.height - zone) continue
                if (down.position.x < trackStart || down.position.x > size.width - trackEnd) continue
                // 滑动 / 没正常抬手 ⇒ 不算轻点（拖动全归滑杆）
                val at = awaitTapUp(down, slop) ?: continue
                val track = (size.width - trackStart - trackEnd).coerceAtLeast(1f)
                val frac = ((at.x - trackStart) / track).coerceIn(0f, 1f)
                onSeek(seekValue(frac, min, max, step))
            }
        }
    }

/**
 * 等这次按下的**抬手**；返回抬手时的位置。
 *
 * @return `null` = 这次不算轻点（滑走了 / 被取消 / 中途丢了这一个指针）
 */
private suspend fun AwaitPointerEventScope.awaitTapUp(
    down: PointerInputChange,
    slop: Float,
): Offset? {
    var moved = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > slop) moved = true
        if (change.changedToUpIgnoreConsumed()) return if (moved) null else down.position
        if (!change.pressed) return null
    }
}

/**
 * 轨道上的相对位置（0..1）→ 值：按**这根滑杆自己的刻度**取整。
 *
 *  刻度必须与 `Slider.steps` 那套同源（`(max-min)/step` 格），不能直接按 `step` 取整 ——
 *  区间不整除时两套值对不上。
 */
private fun seekValue(
    frac: Float,
    min: Int,
    max: Int,
    step: Int,
): Int {
    val ticks = ((max - min) / step).coerceAtLeast(1)
    val idx = (frac * ticks).roundToInt()
    return (min + idx * (max - min).toDouble() / ticks).roundToInt().coerceIn(min, max)
}
