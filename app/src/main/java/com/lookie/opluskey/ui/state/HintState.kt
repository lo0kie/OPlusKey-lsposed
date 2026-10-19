package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 一条提示的**全部状态** —— 就一个字符串，没有队列。
 *
 * ## 为什么不用库的 `SnackbarHostState`
 *
 *  库的 host 是「一个 `LazyColumn` + 每条 item 外面套一层横向 `AnchoredDraggable`」：
 *
 *   · 横滑容器那一层的锚点要等第一帧之后才量出来，出现那一下 offset 还停在锚点之外
 *     ⇒ 胶囊先往右挪一点、再被弹簧拉回（真机：「打开后向右轻微平移又拉回」）；
 *   · 收一条时条目还留在队列里画完退场动画，新的一条**排在它上面**
 *     ⇒ 连续两句时第二句从更高的地方落回底边（真机：「第二个会从垂直高度更高的地方瞬回」）。
 *
 *  这两件都在库内部，外面调不了（`canSwipeToDismiss = false` 也压不住第一条）。
 *  所以提示条自己排：**同一时刻只有一条**，换内容就是换字符串 —— 没有队列就没有叠放，
 *  没有 drag 容器就没有横向漂移。绘制见 `ui/screen/PageSnackbar.kt`。
 *
 * @param holdMs 自动消失的时长；`0` = 常驻（一直挂着，由调用方 [dismiss]）
 */
internal class HintState(
    private val scope: CoroutineScope,
    private val holdMs: Long,
) {
    /** 当前要显示的那句话；`null` = 没有。 */
    var text by mutableStateOf<String?>(null)
        private set

    private var timer: Job? = null

    /** 换一条提示（覆盖旧的，不排队）。 */
    fun show(message: String) {
        if (message.isEmpty()) return
        timer?.cancel()
        text = message
        if (holdMs > 0) {
            timer =
                scope.launch {
                    delay(holdMs)
                    text = null
                }
        }
    }

    fun dismiss() {
        timer?.cancel()
        timer = null
        text = null
    }
}
