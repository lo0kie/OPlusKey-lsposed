package com.lookie.opluskey.inject.input

import android.view.MotionEvent
import com.lookie.opluskey.core.log.XLog

/**
 * 把一条 `MotionEvent` 转成 [RawInput] —— 触摸那一路的**唯一**适配点。
 *
 *  职责只有「翻译」：不改坐标、不做节流、不判断是不是该管的那根手指。多指、指针对不上、
 *  超时这些判据统统在 [SwipeJudge] 里，两边各写一半的话，换判定器就得回来改适配器。
 *
 *  用到的全是公开 API（`MotionEvent` 是框架公开类型，不是宿主的私有类），所以注入侧可以直接编译。
 *
 * ## 时间戳取 `event.eventTime`
 *
 *  不是 `elapsedRealtime()`：滑动判定要的是**这一条轨迹里**的时长，事件自带的时间戳最准；
 *  拿起来那一刻的时间做差，在高频 MOVE 上虽然也近似，但自检（合成事件，见 `probe` 的 `touch`）
 *  就跑不出时长来。跨睡眠的窗口判定不在这里 —— 那是按键那一路的事（`GestureDetector`）。
 *
 * ## 接入时必须注意的两件事
 *
 *  1. **MOVE 是高频**：真机 60~120 条/秒。适配本身极轻（一次装箱），但**不要在适配器里打日志**，
 *     一次滑动就能把 W 级日志刷满。要判据就打在判定结论那一行（一条滑动一条）。
 *  2. **回调线程**：这个函数必须在**采集点自己的线程**上被调，且 [SwipeJudge] 也只在同一条线程上
 *     被喂（见它的「线程」那一节）。别在这里 `post` 到主线程 —— 那样时间戳还在，顺序却会乱。
 */
internal object TouchAdapter {
    /**
     * 转一条；认不出的动作（`ACTION_HOVER_*` 之类）返回 `null`。
     *
     *  注入侧纪律：任何一环抛异常都只能记一行日志（`d` 级），绝不许顺着采集点的回调抛回去。
     */
    fun toRaw(event: MotionEvent): RawInput? =
        try {
            val at = event.eventTime
            val pointers = event.pointerCount
            when (event.actionMasked) {
                //  第一根手指按下：主指针就是 index 0
                MotionEvent.ACTION_DOWN -> {
                    RawInput.TouchDown(event.getPointerId(0), event.getX(0), event.getY(0), pointers, at)
                }

                //  又一根手指按下：把**新按下那根**报上去，手指数 > 1 让判定器自己作废这一段
                MotionEvent.ACTION_POINTER_DOWN -> {
                    RawInput.TouchDown(
                        event.getPointerId(event.actionIndex),
                        event.getX(event.actionIndex),
                        event.getY(event.actionIndex),
                        pointers,
                        at,
                    )
                }

                //  移动只取 index 0：单指时它就是唯一那根；多指时这一段本来就作废，取谁都不影响结论
                MotionEvent.ACTION_MOVE -> {
                    RawInput.TouchMove(event.getPointerId(0), event.getX(0), event.getY(0), pointers, at)
                }

                MotionEvent.ACTION_UP -> {
                    RawInput.TouchUp(event.getPointerId(0), event.getX(0), event.getY(0), pointers, at)
                }

                //  抬起的是**非主指针**：同样报成 TouchUp，靠 pointerId 对不上让判定器作废
                MotionEvent.ACTION_POINTER_UP -> {
                    RawInput.TouchUp(
                        event.getPointerId(event.actionIndex),
                        event.getX(event.actionIndex),
                        event.getY(event.actionIndex),
                        pointers,
                        at,
                    )
                }

                //  这一段被系统收走（来电 / 手势导航抢走 / 窗口切换）：**必须**报上去，
                //  否则判定器会一直挂着一段永远等不到抬手的轨迹。
                MotionEvent.ACTION_CANCEL -> {
                    RawInput.TouchCancel(at)
                }

                else -> {
                    null
                }
            }
        } catch (e: Throwable) {
            XLog.d("触摸适配：这条 MotionEvent 转不了（${e.javaClass.simpleName}: ${e.message}）")
            null
        }
}
