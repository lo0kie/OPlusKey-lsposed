package com.lookie.opluskey.ui.style

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// 全项目「淡阴影」只有这一档：交给 `Modifier.dropShadow(shape = …, shadow = SOFT_SHADOW)` 画。

/**
 * 压在磨砂 / 页面底色上那层**淡阴影** —— 顶栏圆键与首页大卡片共用这一份。
 *
 *  走的是库自己给浮动栏 / 浮动卡片那一套（`Modifier.dropShadow` + `Shadow`）：`radius` 是模糊
 *  半径、只往下挪 1dp（光从上面来）、`alpha` 压在 0.04 —— 只留一层若有若无的投影，勾出
 *  「这一块浮在底下那层上」，不是一枚端着的按钮或一张压厚的卡（用户口径：边框换成淡阴影，
 *  再淡一点）。要再淡 / 再明显都只动这三个数。
 *
 *  **两处共用一份**：顶栏圆键（`HeaderIconButton`）与首页「模块状态」卡。各写一套的话，
 *  同一个界面上会同时存在两种深浅的投影，而这种差别很难一眼说出是哪儿不对。
 *
 *  深色档下黑色阴影本来就几乎看不见 —— 那时靠块自身的底色与底层的色差分辨，这正是
 *  「去掉描边、只留一层淡阴影」要的观感，不为它另调一套亮色阴影。
 */
internal val SOFT_SHADOW =
    Shadow(
        radius = 4.dp,
        color = Color.Black,
        offset = DpOffset(0.dp, 1.dp),
        alpha = 0.04f,
    )
