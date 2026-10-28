package com.lookie.opluskey.ui

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

// 触觉屏蔽：把库自己发出的振动反馈按住（我们的点击语义与库不一致）。

/**
 * **开关的振动只走通用通道**（[rememberTick] / [tickNow]）。
 *
 * 库的 `Switch` 在 `checked` 变化时**无条件**振一次（经 `LocalView` → 宿主
 * `View.performHapticFeedback`）—— 那条路不看系统静音，于是拨开关会比点别的控件多出一次
 * 不受系统状态约束的振动。
 *
 * 做法：在**那一行**外面把 `LocalHapticFeedback` 换成本实现（见 [NoSwitchHaptic]）。
 * 只包开关那一行 —— 滑杆 / 浮层 / 网格那些格子的触觉不走这里（它们已经没有长按手势了）。
 */
internal val NoHapticFeedback: HapticFeedback =
    object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }

/**
 * 开关那一行的 `LocalView` —— 只为一件事：把库 `Switch` 自己那次振动吞掉。
 *
 * 库的 `Switch` 在 `checked` 变化时**无条件**振一次 `CouiHapticEffect.Switch`（`SwitchKt`
 * 里那个 `LaunchedEffect` 先调 `onCheckedChange`、紧接着调振动），不看系统静音。
 * 于是拨开关会比点别的控件多出一次不受系统状态约束的振动。
 *
 *  它走的是 **`LocalView.performHapticFeedback`**、不是 `LocalHapticFeedback`，
 * 所以 [NoHapticFeedback] 拦不住，必须连 `LocalView` 一起换。
 *  库里读 `LocalView` 的有三处：`CouiHaptic`（就是本行开关那次振动）、coui-core 的
 * `Utils.android.kt`（`getSystemCornerRadius` / `RemovePlatformDialogDefaultEffects`）、
 * coui-nav 的 `NavSystemCornerRadius`。后两处在页面 / 弹窗 / 导航层，**都在这条开关这一行
 * 作用域之外** —— 所以拿 [NoHapticView] 顶替整行的 `LocalView` 仍是安全的。
 *  库的 `ListPopup` 读的是 `LocalViewConfiguration` 而不是 `LocalView`，不在这一列。
 */
internal class NoHapticView(
    context: Context,
) : View(context) {
    override fun performHapticFeedback(feedbackConstant: Int): Boolean = false

    override fun performHapticFeedback(
        feedbackConstant: Int,
        flags: Int,
    ): Boolean = false
}

/**
 * 把一行开关包进「触觉只走通用通道」的作用域 —— 见 [NoHapticView] 与 [NoHapticFeedback]。
 *
 * 包两层是必要的：库自己的振动走 `LocalView`，而 `Modifier.clickable` 那一系的
 * 长按振动走 `LocalHapticFeedback`，两条路都得堵，开关的振动才只剩 [tickNow] 一处。
 */
@Composable
internal fun NoSwitchHaptic(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = remember(context) { NoHapticView(context) }
    CompositionLocalProvider(
        LocalView provides view,
        LocalHapticFeedback provides NoHapticFeedback,
    ) { content() }
}
