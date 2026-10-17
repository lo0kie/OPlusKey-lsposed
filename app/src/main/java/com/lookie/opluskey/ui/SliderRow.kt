package com.lookie.opluskey.ui

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.lookie.opluskey.ui.model.SLIDER_ICON_INSET
import com.lookie.opluskey.ui.model.SLIDER_SIDE_INSET
import com.lookie.opluskey.ui.model.SLIDER_TAP_ZONE
import com.lookie.opluskey.ui.model.STEP_MS
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
    min: Int,
    max: Int,
    enabled: Boolean,
    onSeek: (Int) -> Unit,
): Modifier =
    pointerInput(min, max, enabled) {
        // 禁用时这条手势也得停 —— 它是**自己挂的**、不走滑杆的 `enabled`，
        // 不停的话置灰的滑杆照样能点着跳值（多条命令下的「触发间隔」）。
        if (!enabled) return@pointerInput
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
                var moved = false
                var released = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > slop) {
                        moved = true
                    }
                    if (change.changedToUpIgnoreConsumed()) {
                        released = true
                        break
                    }
                    if (!change.pressed) break
                }
                // 滑动 / 没正常抬手 ⇒ 不算轻点（拖动全归滑杆）
                if (moved || !released) continue
                // 触点 → 值：先按轨道左右边界折到轨道上，再按滑杆自己的刻度取整 ——
                // 刻度必须与上面 Slider.steps 那套同源（(max-min)/STEP_MS 格），不能直接按
                // STEP_MS 取整：区间不整除时两套值对不上。
                val track = (size.width - trackStart - trackEnd).coerceAtLeast(1f)
                val frac = ((down.position.x - trackStart) / track).coerceIn(0f, 1f)
                val ticks = ((max - min) / STEP_MS).coerceAtLeast(1)
                val idx = (frac * ticks).roundToInt()
                val v = min + idx * (max - min).toDouble() / ticks
                onSeek(v.roundToInt().coerceIn(min, max))
            }
        }
    }
