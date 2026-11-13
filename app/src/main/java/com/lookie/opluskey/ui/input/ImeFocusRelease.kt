package com.lookie.opluskey.ui.input

import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * 隔几帧看一眼键盘 —— 6 帧 ≈ 100ms（与 `ImeGuard` 同一档：比 IME 收放动画的一帧一步粗，够用）。
 *
 *  这里**不能**用 `snapshotFlow` 盯 IME inset：判据有一半在 View 层（见 [imeVisibleNow]），
 *  那一半不是快照状态，`snapshotFlow` 收不到它的变化。轮询把两条判据一起看。
 */
private const val IME_TICK_FRAMES = 6

/**
 * 「键盘被收走」= 这一栏编辑完了 —— 键盘从**露着**变成**没了**的那一刻清掉焦点。
 *
 * ## 为什么需要它
 *
 *  收起键盘（返回键 / 键盘上那颗收起键 / 系统收走）之后输入框**仍然持着焦点**：光标还闪、
 *  框还画着聚焦描边，可键盘没了 —— 这时再去点别的输入框，第一个框看着还「在编辑」。
 *  失焦之后状态才对得上：这一栏编辑结束，光标与描边一起收走。
 *
 * ## 判据：先露过，再没了
 *
 *  只看「当前不可见」是不够的：聚焦那一刻键盘还没弹起来（动画还没走完），那也会读到不可见，
 *  于是一聚焦就当场失焦、键盘永远打不开。所以这里记一笔 [seen]：**这一次聚焦期间键盘露过**
 *  才认「被收走」。露过之后再变不可见 ⇒ 用户/系统把键盘收了。
 *
 * ## 为什么两条判据（见 [imeVisibleNow]）
 *
 *  Compose 那份 `WindowInsets.ime` 在弹窗里不一定拿得到值（`OverlayDialog` 是库自己那套窗口 /
 *  浮层，IME 的 inset 未必派发到这一棵子树）—— 只用它的话，弹窗里的长输入框永远读成「键盘
 *  没露过」，于是收起键盘也不失焦。所以再补一条 View 层的：`LocalView` 的
 *  `rootWindowInsets` 里 IME 是否可见。**任一为真即算露着**。
 *
 * ## 为什么有 `holdMs`
 *
 *  弹窗里的输入框在开窗那 800ms 由 [ImeGuard] 管（ROM 会在开窗后把 IME 抢收，那一支负责补回来）。
 *  两边同时动手会打架：这里一清焦点，`ImeGuard` 下一眼发现「聚焦着但键盘没了」又补一次键盘，
 *  表现成「键盘关了又自己弹起来」。所以那一段**先让 `ImeGuard` 管**：窗口内键盘没了不立刻清，
 *  等窗口过去再看一眼 —— 补回来了就什么都不做，还空着才清焦点。
 *
 * @param focused 这一栏此刻有没有焦点（调用方在 `onFocusChanged` 里维护）—— `false` 时整段不跑
 * @param holdMs 开头的守卫窗口（毫秒）：弹窗输入框传 `ImeGuard` 那一档，页面里的输入行传 0
 * @param onRelease 键盘被收走时做什么（通常是 `focusManager.clearFocus()`）
 */
@Composable
internal fun ImeFocusRelease(
    focused: Boolean,
    holdMs: Long = 0L,
    onRelease: () -> Unit,
) {
    val insets = WindowInsets.ime
    val density = LocalDensity.current
    val view = LocalView.current
    // effect 只在 `focused` / `holdMs` 变时重启，闭包里那份回调要取最新的
    val releaseNow by rememberUpdatedState(onRelease)
    LaunchedEffect(focused, holdMs) {
        if (!focused) return@LaunchedEffect
        val start = SystemClock.uptimeMillis()
        //  这一次聚焦期间键盘**露过**没有 —— 没露过就不是「被收走」（见 KDoc）
        var seen = false
        var released = false
        while (!released) {
            repeat(IME_TICK_FRAMES) { withFrameNanos { } }
            when {
                //  露着：记一笔，接着看
                imeVisibleNow(insets.getBottom(density) > 0, view) -> {
                    seen = true
                }

                //  还没露过（刚聚焦、键盘正在弹）—— 不算被收走，直接看下一轮
                //  （写 `continue` 而不是一个空块：空块在本项目里要过 warnings-as-errors，
                //  而 `Unit` 会被判成「没用到这个表达式」）
                !seen -> {
                    continue
                }

                //  守卫窗口内先不动手（见 KDoc 的 `holdMs`）：等到点再看一眼，回来了就当没发生
                SystemClock.uptimeMillis() - start < holdMs -> {
                    delay(holdMs - (SystemClock.uptimeMillis() - start))
                    if (imeVisibleNow(insets.getBottom(density) > 0, view)) seen = true
                }

                else -> {
                    releaseNow()
                    released = true
                }
            }
        }
    }
}

/**
 * 键盘此刻露着没有 —— **两条判据取或**（理由见 [ImeFocusRelease] 的 KDoc）。
 *
 * @param composeSays Compose 那份 `WindowInsets.ime` 的读数（`getBottom > 0`）
 * @param view 这一栏所在的 View —— 用它读**窗口**那一层的 root insets
 */
private fun imeVisibleNow(
    composeSays: Boolean,
    view: View,
): Boolean {
    if (composeSays) return true
    return try {
        //  不在窗口里的 View 读不到 insets（浮层里被顶替掉的那一份，见 `moveCaretToEnd` 的注释）
        view.isAttachedToWindow && view.rootWindowInsets?.isVisible(
            android.view.WindowInsets.Type
                .ime(),
        ) == true
    } catch (e: Throwable) {
        false
    }
}
