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

/**
 * 「点一下振一次」—— 由页面在根上提供（实现是 `haptics.tick`，见 `PageHaptics`）。
 *
 *  为什么要有这一条：**能点的行默认就该振**（用户口径），而这一下振动发生在
 *  `CardList` / `PickerRow` 这些**控件层**——它们不带 `SettingsPage` 接收者，拿不到页面实例。
 *  所以和 [LocalHint] 一样从上面传下来，控件只管「点的时候叫一声」。
 *
 *  没提供时是空实现：这些控件在别的上下文里照样能用，只是不振动。
 *
 *  列表里「不振动」的少数几行，用 `CardRow.Item(vibrate = false)` 显式关掉 ——
 *  默认开、个别关，比「每处都要记得振」少一种漏法。
 */
internal val LocalTick = staticCompositionLocalOf<() -> Unit> { {} }
