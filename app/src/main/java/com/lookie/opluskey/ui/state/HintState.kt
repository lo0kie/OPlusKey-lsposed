package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    /**
     * 这句话**是第几个说出来的**（全局发号，越小越旧）—— 两份 [HintState] 之间判先后就靠它。
     *
     *  为什么不能只看 `text` 非空：主开关一关，常驻结论立刻就有新内容了，可另一条操作回执
     *  还挂在那儿（它自己的两秒还没到点）。这时候该让位的是回执，而「非空」判不出来。
     *
     *  为什么必须是**全局**号：两份状态各发各的号是比不出先后的 —— 各从 0 开始、
     *  各变一次就都是 1（真机上就是「先点振动反馈、再点主开关」这一串：
     *  回执发 1、常驻结论也发 1，平手就判成回执赢，主开关那条又得等两秒）。
     *  同一句话重发（配置重载 / 重复点同一行）不换号：画面没变，不该把刚弹出来的回执顶掉。
     */
    var stamp by mutableIntStateOf(0)
        private set

    private var timer: Job? = null

    /** 换一条提示（覆盖旧的，不排队）。 */
    fun show(message: String) {
        if (message.isEmpty()) return
        //  作用域已经收掉（页面关了）就**不写**：写进去没人计时，会留一条永不过期的提示
        //  （下次开页之前它一直挂在那儿，或者跟着旧实例泄漏）。见 [HostLinkState.dispose]。
        if (!scope.isActive) return
        //  只在内容真的变了才换号（见 [stamp]）
        if (message != text) stamp = HintStamp.issue()
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
        if (text != null) stamp = HintStamp.issue()
        text = null
    }
}

/**
 * 「谁的话更新」的**全局发号器** —— 两份状态共用同一个计数器，号才能比大小
 * （各发各的号时，「各变一次」就都是 1，比不出先后，见 [HintState.stamp]）。
 *
 *  只在主线程写（两份状态都在主线程改：`HintState.show` 由 `hint()` / `showStatus()` 调，
 *  那两处一个在事件回调里、一个在 `LaunchedEffect` 里），所以不用同步。
 */
private object HintStamp {
    private var next = 0

    fun issue(): Int = ++next
}
