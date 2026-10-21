package com.lookie.opluskey.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 操作提示停留多久（毫秒）。库的 `SnackbarDuration.Short` 是 4s，这里短一点：
 * 回执只是一句「已删除」，看完就走，不该一直压着页面顶部。
 */
private const val HINT_HOLD_MS = 2200L

/**
 * **页面上那一叠提示条**，以及它们的作用域。
 *
 *  **只有会自己走的那种**：那条「常驻结论」（页面为什么是灰的）已经整条删掉 —— 它与回执
 *  要么互相顶、要么并列说同一件事（用户口径「去掉常驻提示，不允许发」）。见 `HintQueue` 的类注释。
 *
 *  与「链路 / 报活」那一位（`HostLinkState`）分开：两者的生命周期正相反，原先混在一个类里，
 *  于是「链路诊断状态」莫名其妙带上了 `dispose()` —— 任何一个只想读 `hostServiceUp` 的地方，
 *  都得先背着一份会 `cancel()` 作用域的对象。
 *
 *  这里只管「这些话怎么排」：写什么内容、什么时候该显示，是调用方的业务。排序 / 淘汰 / 计时
 *  都在 [HintQueue] 里（那份类注释讲了为什么自己排而不用库的 host）。
 */
internal class HintCenter {
    /**
     * 提示用的作用域 —— 页面级，关页时由 [dispose] 收掉。
     *
     *  `Main.immediate`：提示是 Compose 状态，写它就该在主线程；而 [hint] 的调用点有一半
     *  在事件回调里（已经在主线程），`immediate` 让那几处不必多绕一次消息队列。
     *
     *  **必须声明在 [queue] 之前**：Kotlin 的属性按书写顺序初始化，放到后面那份
     *  `HintQueue(scope, …)` 拿到的会是 `null`（编译期不报，运行时就是一次 NPE）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 那一叠提示 —— 后来的在上面，最多 [MAX_VISIBLE] 条。 */
    val queue = HintQueue(scope, holdMs = HINT_HOLD_MS)

    /**
     * 弹一条**操作提示**（取代原来的 Android Toast）—— 压到最上面。
     *
     * @param slot 语义位：同一个位上再来一句话是**改那一条的字**，不是再加一条（见 [HintQueue.push]）。
     *   不传 = 每次都是新的一条（保存失败这类一次性回执就该这样）。
     */
    fun hint(
        text: String,
        slot: String? = null,
        holdMs: Long? = null,
    ) {
        queue.push(text, slot, holdMs)
    }

    /** 把某个语义位上的提示提前收掉（见 [HintQueue.retire]）。 */
    fun retire(slot: String) = queue.retire(slot)

    /** 关页时收掉作用域 —— 还在计时的提示跟着停，不会有人再往旧实例里写。 */
    fun dispose() {
        //  先清空队列再收作用域：只 cancel 的话，组合树上那几个 HintSlot 还读着旧字符串 ——
        //  旧页面在拆除前最后一帧会再画一遍（而 `push` 此后已经拒绝写入）。
        queue.clear()
        scope.cancel()
    }
}
