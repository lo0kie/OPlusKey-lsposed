package com.lookie.opluskey.ui.model

import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.theme.Colors

/**
 * 当前这套配色是不是暗色。
 *
 *  判据取 `background` 的亮度，不读系统设置：配色档可以被强制成浅色 / 深色 / Monet，
 *  系统那一位和实际画出来的颜色不一定一致。
 *
 *  **只有这一处判据**：菜单、抽屉、应用格子里那些「暗色要换一档色」的分支都读它 ——
 *  各自写一遍 `luminance() < 0.5f` 时，改动阈值就得把每个调用点翻一遍。
 */
internal val Colors.isDarkScheme: Boolean
    get() = background.luminance() < 0.5f
