package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 卡片的三档底色 —— 全项目一处。
 *
 *  这三档（常规 / 按下 / 内容色）原先在命令卡、绑定格与 `CardList` 默认值里各写一遍，值一样：
 *  同一页里两张卡按下去的深浅不同、或者一张的内容色漏了跟上主题，都是这么来的。
 *
 *  @param contentColor 卡片内容色。默认是 `onSurfaceContainer`；绑定格那一档会传一个**动画值**
 *  进来（整格的内容色随启用 / 选中变，见 `BindingTile` 的 `contentColor`），其余调用点不用传。
 */
@Composable
internal fun appCardColors(contentColor: Color = COUITheme.colorScheme.onSurfaceContainer): CardColors =
    CardDefaults.defaultColors(
        color = COUITheme.colorScheme.surfaceContainer,
        contentColor = contentColor,
        pressedColor = COUITheme.colorScheme.surfaceContainerHigh,
    )
