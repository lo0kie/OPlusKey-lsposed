package com.lookie.opluskey.inject.input

import android.view.MotionEvent
import com.lookie.opluskey.core.log.XLog

/**
 * 触摸手势判定的**唯一入口** —— 将来任何一个触摸采集点，都只调 [onMotionEvent] 这一个方法。
 *
 *  为什么要这一层：采集点可能不止一个（策略回调、输入事件、将来的按下滑动入口），判定器却只能
 *  有一份。把「适配 → 判定 → 判据日志」串在这里，采集点的职责就只剩「拿到事件就喂进来」，
 *  换判定器、改阈值、改日志口径都不必回头找每个采集点。
 *
 *  ## 现在没有采集点
 *
 *  系统进程里能拿到触摸事件的那个入口还没在真机上确认（`docs/device-findings.md` 里没有这一项），
 *  所以本阶段**只铺路、不装 Hook、不派发动作**：判出来一段滑动只落一行 `w` 日志。
 *  自检入口是诊断广播的 `touch` op（见 `ProbeOps`），它在进程内造几条合成轨迹跑通这整条链。
 *
 *  接入真实采集点时只做两件事：在候选表里登记入口、在回调里调 [onMotionEvent]。
 *  **别在这里派发动作** —— 判定在执行侧之前，动作是宿主进程的事（见 `GestureForwarder` 那条分工）。
 */
internal object TouchJudge {
    /** 单指滑动的判定器 —— 进程内一份，状态就是「当前这一段轨迹」。 */
    private val swipeJudge = SwipeJudge()

    /**
     * 喂一条原始触摸事件；判出一段滑动时返回它。
     *
     *  线程：必须与采集点同线程（状态机不加锁，见 [SwipeJudge] 的「线程」一节）。
     */
    fun onMotionEvent(event: MotionEvent): Swipe? {
        val raw = TouchAdapter.toRaw(event) ?: return null
        return submit(raw)
    }

    /**
     * 喂一条已经归一化的事件 —— 自检（合成轨迹）走这一条，省掉造 `MotionEvent` 的那几步。
     *
     *  注入侧纪律：判定抛出去会顺着采集点的回调打到系统进程里，所以这里兜一层，最坏只是这段没判出来。
     */
    fun submit(input: RawInput): Swipe? {
        val swipe =
            try {
                swipeJudge.submit(input)
            } catch (e: Throwable) {
                //  **判据级**：判定器抛异常意味着这一档手势从此判不出来，而 `d` 在 release 里
                //  被编译期消除 —— 那正是「滑动绑了没反应」唯一该看的一行。真每划都抛的话，
                //  刷屏本身就是要修的症状，不该靠降档把它藏起来。
                XLog.w("滑动判定抛了（已忽略）：${e.javaClass.simpleName}: ${e.message}")
                return null
            } ?: return null
        //  判据级：一段滑动只在这一处落一行（适配器里不打日志 —— MOVE 是 60~120 条/秒）
        XLog.w("滑动：$swipe")
        return swipe
    }
}
