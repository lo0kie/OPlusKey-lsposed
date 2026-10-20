package com.lookie.opluskey.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 操作提示停留多久（毫秒）。库的 `SnackbarDuration.Short` 是 4s，这里短一点：
 * 回执只是一句「已删除」，看完就走，不该一直压着页面底部。
 */
private const val HINT_HOLD_MS = 2200L

/**
 * **页面上的两条提示条**（常驻结论 + 操作回执），以及它们的作用域。
 *
 *  与「链路 / 报活」那一位（`HostLinkState`）分开：两者的生命周期正相反，原先混在一个类里，
 *  于是「链路诊断状态」莫名其妙带上了 `dispose()` —— 任何一个只想读 `hostServiceUp` 的地方，
 *  都得先背着一份会 `cancel()` 作用域的对象。
 *
 *  这里只管「怎么显示一句话」：写什么内容、什么时候该显示，是调用方的业务。
 */
internal class HintCenter {
    /**
     * 提示用的作用域 —— 页面级，关页时由 [dispose] 收掉。
     *
     *  `Main.immediate`：提示是 Compose 状态，写它就该在主线程；而 `hint()` 的调用点有一半
     *  在事件回调里（已经在主线程），`immediate` 让那几处不必多绕一次消息队列。
     *
     *  **必须声明在两份 [HintState] 之前**：Kotlin 的属性按书写顺序初始化，放到后面那份
     *  `HintState(scope, …)` 拿到的会是 `null`（编译期不报，运行时就是一次 NPE）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 首页那条「模块未生效」**常驻**结论 —— 一直挂着，直到结论变了或被代码收掉。
     *
     *  与 [actionHint] 分成两份状态，因为两者的生命周期正相反：这条不会自己走，
     *  另一条几秒就消失。放在同一份里会互相顶掉（收常驻那条时把刚做的动作回执一起收掉）。
     *  实现见 [HintState]（为什么不用库的 `SnackbarHostState`，那里写着）。
     */
    val statusHint = HintState(scope, holdMs = 0)

    /**
     * **操作提示** —— 用户做完一个动作之后的回执（已删除、保存失败、已切换到某颗键……）。
     *
     *  两秒多就自己走；新的一句**直接换掉**旧的一句，不排队（见 [HintState] 的类注释）。
     */
    val actionHint = HintState(scope, holdMs = HINT_HOLD_MS)

    /**
     * 弹一条**操作提示**（取代原来的 Android Toast）。
     *
     *  只换字符串：没有队列、没有「先收再弹」—— 那套是给库的 host 准备的，
     *  也正是「第二句从更高的地方瞬回」的来源（见 [HintState]）。
     */
    fun hint(text: String) {
        actionHint.show(text)
    }

    /** 换掉那条常驻提示（结论变了）；传 `null` = 收掉。 */
    fun showStatus(text: String?) {
        if (text == null) statusHint.dismiss() else statusHint.show(text)
    }

    /** 收掉那条常驻提示（离开首页 / 关页时）。 */
    fun dismissStatus() {
        statusHint.dismiss()
    }

    /** 关页时收掉作用域 —— 还在计时的提示跟着停，不会有人再往旧实例里写。 */
    fun dispose() {
        //  先把两条提示收成 null，再收作用域：只 cancel 的话，组合树上那两个 HintSlot
        //  还读着旧字符串 —— 旧页面在拆除前最后一帧会再画一遍（而 `show` 此后已经拒绝写入）。
        statusHint.dismiss()
        actionHint.dismiss()
        scope.cancel()
    }
}
