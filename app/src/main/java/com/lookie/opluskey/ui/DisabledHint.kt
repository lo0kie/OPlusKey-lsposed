package com.lookie.opluskey.ui

import androidx.compose.runtime.staticCompositionLocalOf

// 「提示怎么弹」—— 控件层拿不到页面实例，从上面传。

/**
 * 弹一条操作提示 —— 由页面在根上提供（实现是 `hints.hint`，见 `ui/PageFeedback.kt`）。
 *
 *  控件层（`IconAction` 这种不带 `SettingsPage` 接收者的）也拿不到页面实例，所以「提示怎么弹」
 *  从上面传下来。**没提供时是空实现**：控件在别的上下文里照样能用，只是不出声。
 *
 *  「禁用态被点了一下」这件事本身不在这里 —— 那个走库里的 `LocalDisabledClick`
 *  （见 `patches/coui/0008-disabled-click-explains.patch`）：库负责把**这一下点击**递出来，
 *  「说什么」仍归页面。控件自己有更具体的原因时（「已经是第一步」这种），就在自己那一段
 * 覆盖 `LocalDisabledClick` 说自己的话。
 */
internal val LocalHint = staticCompositionLocalOf<(String) -> Unit> { {} }
