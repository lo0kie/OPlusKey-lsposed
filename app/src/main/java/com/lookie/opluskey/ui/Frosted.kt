package com.lookie.opluskey.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.lookie.opluskey.ui.model.FROSTED_BLUR_RADIUS
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

internal fun Modifier.frosted(
    state: HazeState,
    shape: Shape,
): Modifier =
    this
        .clip(shape)
        .hazeBlur(input = HazeInput.Sources(state), style = HAZE_FROSTED_STYLE)

/**
 * 磨砂的模糊规格 —— 顶栏 / 浮层菜单 / 底部工具栏 / 确认弹窗共用这一份。
 *
 * [FROSTED_BLUR_RADIUS] 直接就是 dp：Haze 的 `blurRadius` 收 dp，不必再按 density 换算成像素。
 * 噪点显式置 0 —— 库默认 0.15，会在糊上叠一层细颗粒，与这套观感对不上。
 */
private val HAZE_FROSTED_STYLE: HazeBlurStyle =
    HazeBlurStyle {
        blurRadius(FROSTED_BLUR_RADIUS)
        noiseFactor(0f)
    }
