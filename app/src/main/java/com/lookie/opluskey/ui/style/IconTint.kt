package com.lookie.opluskey.ui.style

import androidx.compose.runtime.compositionLocalOf
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
 *
 *  动态档（不是 `staticCompositionLocalOf`）：这一位**会变** —— 拨「随机图标配色」那一下换种子，
 *  而静态 local 一变会把 provide 处**整棵内容**重组合一遍（那一页每个控件都跟着重跑）。
 *  动态 local 只把订阅过它的图标标脏，页面其余部分不动 —— 换一批图标颜色不该拖着整页重画。
 *  代价是每个读者各登记一条，几十颗图标值这个开销。
 */
internal val LocalIconTint = compositionLocalOf<(ImageVector) -> Color?> { { null } }

/**
 * 一颗图标的**随机配色**（见 [LocalIconTint]）—— 关着时返回 `null`。
 *
 *  抽成一个扩展只为让调用点写成一行：`tint = row.iconTint ?: icon.tinted() ?: fallback`。
 */
@androidx.compose.runtime.Composable
internal fun ImageVector.tinted(): Color? = LocalIconTint.current(this)
