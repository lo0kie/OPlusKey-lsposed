package com.lookie.opluskey.ui

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.lookie.opluskey.ui.model.SliderTapTrack
import kotlin.math.roundToInt

/**
 * 「点轨道跳值」手势（滑杆行那条路）：值域整份取自 [SliderSpec]，几何由调用方给的 [SliderTapTrack] 决定
 *（行里传 `SLIDER_ROW_TAP_TRACK`，弹窗自己算那一份）。
 *
 *  这个重载只负责「别把 [spec] 拆开抄」：值域与步长必须一起传；只传 min / max 会让触点折算用错
 *  `step` —— 三档滑杆上 `(max-min)/step` 算成 0、夹到 1，点中间只能落到端点上。
 */
internal fun Modifier.tapSliderToSeek(
    spec: SliderSpec,
    track: SliderTapTrack,
    onSeek: (Int) -> Unit,
): Modifier = tapSliderToSeek(spec.min, spec.max, spec.step, spec.enabled, track, onSeek)

/**
 * 「点轨道跳值」手势：手势节点比滑杆大（整行），这里按 [track] 圈出滑杆占的那条带子，
 * 只有落在带子里的轻点才跳值。
 *
 *  手势挂在**外层节点**上，不挂滑杆自己的 `Modifier` —— 那会与它内部的拖动争事件。
 *  几何由调用方按自己的排版给（行里要算行内边距与行首图标，弹窗里那根滑杆就是节点本身）。
 *
 * ## 按下的去处
 *
 *  · **落在带子里**：这一下归滑杆 —— 拖动全归它（我们不消费按下与移动），
 *    轻点则由这里跳值；**抬手要消费掉**。不消费的话，外层那一层「点整行」的点击
 *    （`BasicComponent` 的 `onClick`，滑杆行用它开手动输入窗）会同时被触发：点轨道
 *    = 跳到那个值 **+** 弹一个输入窗，而用户只是想拖到那儿。
 *  · **不落在带子里**（标题 / 摘要 / 行尾值 / 行内边距）：这里一概不碰，事件原样往上传，
 *    由外层那一层统一处理（波纹 + 振动 + 开窗）。
 */
@Suppress("LongParameterList")
internal fun Modifier.tapSliderToSeek(
    min: Int,
    max: Int,
    step: Int,
    enabled: Boolean,
    track: SliderTapTrack,
    onSeek: (Int) -> Unit,
): Modifier =
    pointerInput(min, max, step, enabled) {
        // 禁用时这条手势也得停 —— 它是**自己挂的**、不走滑杆的 `enabled`，
        // 不停的话置灰的滑杆照样能点着跳值（多条命令下的「触发间隔」）。
        if (!enabled) return@pointerInput
        val slop = viewConfiguration.touchSlop
        // 轨道两端：手势节点边缘往里让出的距离（行里是行内边距 + 行首图标，弹窗里是 0）
        val trackStart = track.startInset.toPx()
        val trackEnd = track.endInset.toPx()
        val zone = track.zone.toPx()
        awaitPointerEventScope {
            while (true) {
                val down =
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                // 没落在滑杆那条带子里 ⇒ 这次按下不归这条手势管
                // （不 consume，让外层那一层照常处理它）
                //  注意：**纵向与横向都要圈** —— 只判纵向的话，横向靠后面的
                //  `coerceIn(0f, 1f)` 兜 —— 在轨道**左/右外侧**、但仍在底部那条带子里轻点会被折成 0 或 1，
                //  也就是**写 min / max**：用户只是点了行首图标那一格或行尾空白，值却被跳到端点。
                if (down.position.y < size.height - zone) continue
                if (down.position.x < trackStart || down.position.x > size.width - trackEnd) continue
                // 滑动 / 没正常抬手 ⇒ 不算轻点（拖动全归滑杆，抬手也不归我们消费）
                val at = awaitTapUp(down, slop) ?: continue
                val span = (size.width - trackStart - trackEnd).coerceAtLeast(1f)
                val frac = ((at.x - trackStart) / span).coerceIn(0f, 1f)
                onSeek(seekValue(frac, min, max, step))
            }
        }
    }

/**
 * 等这次按下的**抬手**；返回抬手时的位置。
 *
 *  确认是轻点之后**把抬手消费掉**（见 [tapSliderToSeek] 的「按下的去处」）：
 *  这一下已经由跳值那一条处理过了，外层的行点击不该再开一次输入窗。
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
        if (change.changedToUpIgnoreConsumed()) {
            if (moved) return null
            change.consume()
            return down.position
        }
        if (!change.pressed) return null
    }
}

/**
 * 轨道上的相对位置（0..1）→ 值：折成值之后交给 [snapToStep] 取整。
 *
 *  刻度必须与 `Slider.steps` 那套同源（`(max-min)/step` 格），不能直接按 `step` 取整 ——
 *  区间不整除时两套值对不上。那份刻度只在 [snapToStep] 写一次，这里只是「位置 → 值」的折算。
 */
private fun seekValue(
    frac: Float,
    min: Int,
    max: Int,
    step: Int,
): Int = snapToStep((min + frac * (max - min)).roundToInt(), min, max, step)
