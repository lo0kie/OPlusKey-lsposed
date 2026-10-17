package com.lookie.opluskey.inject.input

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ModuleWire

/** 把判好的手势从 `system_server` 转给宿主进程执行。 */
internal class GestureForwarder(
    /**
     * 注入侧借到的 Context（`SystemContext` 从真实 App 进程借来的）。
     * 是 lambda 而非现成对象：装 Hook 那一刻还没有任何 App attach 过，那时只能借到
     * `getSystemContext()` 的降级品（`mAppThread == null`，`startService` / `sendBroadcast`
     * 一用就失败）；推迟到真要转交手势时取值，借到好 Context 的机会大得多，且不影响启动时序。
     */
    private val context: () -> Context?,
    /** 注入侧的主线程 Handler —— 延后发送走它。 */
    private val handler: Handler,
) {
    /** 宿主的服务（用来在转发前唤醒宿主进程）。 */
    private val hostService = ComponentName(HostApp.PACKAGE, HOST_SERVICE)

    /**
     * 「唤醒宿主失败」是否已经报过（见 [forward] 里那条日志）。
     *
     *  为什么分档：唤醒失败是个真实故障（宿主起不来 ⇒ 这次手势可能被丢），而 `d` 在 release 里被
     *  编译期消除 —— 只留 `d` 时一直失败也完全无痕；但这条路径**每个手势**都走一次，用 `w` 常报会把
     *  日志刷满。故只让**首次**失败按判据级 `w` 报出来，之后压回 `d`。
     */
    @Volatile
    private var wakeWarned = false

    /**
     * 转交一次手势。
     *
     * @param repeat 是否「长按连续触发」的重复派发（宿主据此跳过振动反馈）
     */
    fun forward(
        id: KeyId,
        gesture: Gesture,
        repeat: Boolean,
    ) {
        val ctx =
            context() ?: run {
                XLog.w("没借到 Context，无法转交 ${id.label} 的手势")
                return
            }

        // 动态接收器要求宿主进程活着；借来的 Context 与宿主同 uid，startService 才过得了 OPLUS_COMPONENT_SAFE。
        try {
            ctx.startService(Intent().setComponent(hostService))
        } catch (e: Throwable) {
            // 唤醒失败 ⇒ 宿主进程可能没起来、这次手势可能被丢：首次用 w 报出来（`d` 在 release 里
            //  被编译期消除，只留 `d` 会让「一直失败、手势静默丢失」完全无痕），之后压回 `d` 免刷屏。
            if (!wakeWarned) {
                wakeWarned = true
                XLog.w("唤醒宿主进程失败，手势可能被丢：${e.message}")
            } else {
                XLog.d("唤醒宿主进程失败（若它已在跑就无所谓）：${e.message}")
            }
        }

        val phase = gesture.id
        val sentAt = SystemClock.elapsedRealtime()
        val intent =
            Intent(ModuleWire.ACTION_GESTURE)
                .setPackage(HostApp.PACKAGE)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra(ModuleWire.EXTRA_KEY, id.id)
                .putExtra(ModuleWire.EXTRA_PHASE, phase)
                .putExtra(ModuleWire.EXTRA_REPEAT, repeat)
                .putExtra(ModuleWire.EXTRA_SENT_AT, sentAt)

        // 立刻发一条（宿主已在跑时即最终送达），再补发一条兜底（宿主刚被拉起、动态接收器
        // 还没注册时）—— 两条带同一个 sent_at，宿主据此去重，都到了也只执行一次。
        //  补发只对「一次按压」有意义：长按连发（repeat）期间宿主早就在跑，每 100ms 再排一条
        //  300ms 后到期的补发，松手后队列里会积一串废消息（且每条都要过一遍接收侧的去重）。
        send(ctx, intent, phase, repeat, delayMs = 0L, retry = false)
        if (!repeat) send(ctx, intent, phase, repeat, delayMs = FORWARD_DELAY_MS, retry = true)
    }

    /**
     * 投一条手势广播（发送抛异常只记日志 —— 这条在按键热路径上）。
     *
     * @param retry true = 补发那条，日志压到 `d`（release 裁掉），免得淹掉日志。
     */
    private fun send(
        ctx: Context,
        intent: Intent,
        phase: String,
        repeat: Boolean,
        delayMs: Long,
        retry: Boolean,
    ) {
        try {
            if (delayMs <= 0L) {
                XLog.guard("发送侧键手势广播 $phase") {
                    ctx.sendBroadcast(intent)
                    XLog.w("已把侧键手势转给宿主：$phase${if (repeat) "（连续触发）" else ""}")
                }
            } else {
                handler.postDelayed({
                    XLog.guard("发送侧键手势广播 $phase") {
                        ctx.sendBroadcast(intent)
                        if (retry) {
                            XLog.d("侧键手势已补发：$phase（(1) 被丢掉时才需要它）")
                        } else {
                            XLog.w("已把侧键手势转给宿主：$phase${if (repeat) "（连续触发）" else ""}")
                        }
                    }
                }, delayMs)
            }
        } catch (e: Throwable) {
            XLog.w("转交手势失败：$phase", e)
        }
    }

    private companion object {
        /**
         * 补发那条的延迟（毫秒）—— 冷进程（开机后第一次按侧键）时它才是有效那条。
         *
         * 300ms 为实测值：够 `startService` 把宿主从缓存进程抬起来。
         */
        const val FORWARD_DELAY_MS = 300L

        /** 宿主的侧键服务（`onBind` 型，启动它只为抬高进程级别）；名字只在 `Framework` 里写一次。 */
        val HOST_SERVICE = Framework.HOST_KEY_SERVICE
    }
}
