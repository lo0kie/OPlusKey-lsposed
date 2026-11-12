package com.lookie.opluskey.ui.feedback

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

/**
 * 把库那一次振动**转投到通用通道**的 `LocalView` —— 与 [NoHapticView] 是一对：那个吞掉，这个转投。
 *
 * ## 治的是什么
 *
 *  浮层菜单「按住不放、上下滑动换行」时，每换一行库就振一次（`PopupListGestureHost.willChangeRow`
 *  → `CouiHaptic` → `LocalView.performHapticFeedback`）。那条路**不看** [PageHaptics.allowed]
 *  （系统静音照样振），也不与页面里其它点击同一档 —— 用户口径「菜单按住滑动时的振动没走通用振动」。
 *
 *  库那条路读的是 `LocalView`、不是 `LocalHapticFeedback`，所以 [NoHapticFeedback] 拦不住；
 *  把浮层内容那一层的 `LocalView` 换成这一份，那一次振动就落到 [LocalTick] 上，
 *  判据与时长跟点一下菜单行完全一致。
 *
 * ## 换掉 `LocalView` 的边界（重要）
 *
 *  这个作用域**只能包「没有输入框、也不会自己开窗口」的内容** —— 现在只有浮层菜单那一列
 *  （`PageMenuPopup`）用它。**弹窗整体不能套**：
 *
 *   - Compose 的 `Popup`（输入框的光标 / 选区手柄就是它画的）从 `LocalView` 上取
 *     `ViewTreeLifecycleOwner` 与窗口 token，假 View 两样都没有 ⇒ `PopupLayout` 在挂窗口那一刻
 *     抛 `IllegalStateException`，**宿主进程直接崩**；
 *   - 输入框那一侧的「把光标挪到末尾」是往 `LocalView` 派发按键事件，发给假 View 等于没发。
 *
 *  这两条都是真机踩出来的（见 `docs/device/coui.md` 四十三）。库会振的字面量控件各自收口：
 *  开关在 `SwitchRow` 里、滑杆在 `TrackSlider` 里（关成 `None`），不必靠这一层兜整棵子树。
 *
 * @param onHaptic 转投的去处 —— 调用点给 `LocalTick` 那一条（见 [TickHapticScope]）
 */
internal class TickHapticView(
    context: Context,
    private val onHaptic: () -> Unit,
) : View(context) {
    override fun performHapticFeedback(feedbackConstant: Int): Boolean {
        onHaptic()
        return true
    }

    override fun performHapticFeedback(
        feedbackConstant: Int,
        flags: Int,
    ): Boolean {
        onHaptic()
        return true
    }
}

/**
 * 把这一层里**库自己发出的振动**改走通用通道（见 [TickHapticView]）。
 *
 *  `LocalTick` 在这里**跟着读**（`rememberUpdatedState`）：它由页面提供、每次重组可能是新实例，
 *  而这个 View 是 `remember` 住的 —— 直接闭包会把创建那一刻的那个钉死。
 */
@Composable
internal fun TickHapticScope(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tick by rememberUpdatedState(LocalTick.current)
    val view = remember(context) { TickHapticView(context) { tick() } }
    CompositionLocalProvider(LocalView provides view) { content() }
}
