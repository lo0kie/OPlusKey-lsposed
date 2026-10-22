package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.wire.ModuleWire
import kotlinx.coroutines.delay

// 「等按键」的倒计时 —— 单独一个文件：它有自己的计时循环，与弹窗正文的排版无关；
// 放在 `CollectKeyDialog.kt` 里会把那个文件顶到 detekt 的函数数上限（阈值 11）。

/**
 * 从 [ModuleWire.KEY_WATCH_WINDOW_MS] 起每秒减一，到 0 时调 [onTimeout]；返回还剩几秒。
 *
 *  窗口长度与注入侧**共用同一个常量**：那边到点停发键码（`KeyWatch` 的兜底），这边到点关窗 ——
 *  两处各写一个 60 秒的话，界面上的倒计时会与真实窗口对不上。
 *
 * @param show 弹窗在不在
 * @param waiting 还在等按键（听到键 / 撞上重复就停表）
 * @param onTimeout 到期做什么（收场）
 */
@Composable
internal fun rememberWatchCountdown(
    show: Boolean,
    waiting: Boolean,
    onTimeout: () -> Unit,
): Int {
    val total = (ModuleWire.KEY_WATCH_WINDOW_MS / 1000L).toInt()
    val active = show && waiting
    var secondsLeft by remember(active) { mutableIntStateOf(total) }
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        secondsLeft = total
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        timeout()
    }
    return secondsLeft
}
