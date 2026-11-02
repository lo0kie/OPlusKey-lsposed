package com.lookie.opluskey.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 「这一颗图标该染成什么色」—— 由页面在根上提供；没提供时返回 `null`（= 用原来的中性色）。
 *
 *  只有「外观 → 随机图标配色」开着时才有值（见 `GlobalConfig.iconTintSeed`）：
 *  关着时页面 provide 的是恒 `null` 的那一支，控件照旧跟着主题走。
 *
 *  按**图标身份**取色（[ImageVector.name]），不按调用点位置：同一颗图标在菜单里、在卡片里
 *  颜色一致，挪个位置也不会换色。
 */
internal val LocalIconTint = staticCompositionLocalOf<(ImageVector) -> Color?> { { null } }

/**
 * 一颗图标的**随机配色**（见 [LocalIconTint]）—— 关着时返回 `null`。
 *
 *  抽成一个扩展只为让调用点写成一行：`tint = row.iconTint ?: icon.tinted() ?: fallback`。
 */
@androidx.compose.runtime.Composable
internal fun ImageVector.tinted(): Color? = LocalIconTint.current(this)
