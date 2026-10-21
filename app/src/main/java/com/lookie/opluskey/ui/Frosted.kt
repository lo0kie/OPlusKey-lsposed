package com.lookie.opluskey.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.lookie.opluskey.ui.model.FROSTED_BLUR_RADIUS
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * 磨砂。
 *
 * @param performanceMode 模糊的采样档位（`null` = 库默认的 `Balanced`）。糊是**每帧重算**的：
 *   按屏幕坐标取样，节点每移一点就要重新捕获一遍背面、重新糊一遍。`Performance` 把采样降到
 *   半分辨率 —— Haze 会按缩放同比放大半径，所以**视觉半径不变**，变的只是糊上那点细节，
 *   24dp 这种半径下基本看不出。只有「一直在动」的糊才值得降档；静止的糊只算一次，用默认档。
 */
internal fun Modifier.frosted(
    state: HazeState,
    shape: Shape,
    performanceMode: HazePerformanceMode? = null,
): Modifier =
    this
        .clip(shape)
        .hazeBlur(
            input = HazeInput.Sources(state),
            style = HAZE_FROSTED_STYLE,
            performanceMode = performanceMode,
        )

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
