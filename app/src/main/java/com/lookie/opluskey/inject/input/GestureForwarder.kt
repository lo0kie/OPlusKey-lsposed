package com.lookie.opluskey.inject.input

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
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
) {
    /**
     * 专用发送线程 —— **两条发送（立刻 + 补发）都只在这里跑**。
     *
     *  原来它们直接跑在判定那一条线程上：单击 / 双击是在 **InputReader 线程**上判的
     *  （见 `SideKeyInterceptor` 的实测结论），而 `startService` / `sendBroadcast` 各是一次
     *  binder 往返 —— 输入线程上做 binder，一旦对面（AMS / 宿主）正忙，就是把整条输入读取
     *  一起按住；补发那条还排在 system_server 主 looper 上，主 looper 一堵，`sent_at` 早就
     *  过期，宿主按 `age > 2000ms` 直接丢弃，表现就是「按了没反应」。
     *
     *  单线程顺序执行还有一笔附带收益：手势与松手落进同一个队列，**先后不会乱**。
     *  （`docs/device-findings.md` 里记的那条待办就是这件事。）
     */
    private val senderThread = HandlerThread("opluskey-gesture-forward").apply { start() }
    private val sender = Handler(senderThread.looper)

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
        val phase = gesture.id
        // 补发那条**复用同一个 Intent**：两条必须带同一个 sent_at，宿主才认得出是一件事。
        //  `sent_at` 也就在这里（判定那一刻）打上 —— 挪进发送线程会变成「排到队里才开始计时」，
        //  主 looper 堵过之后宿主就判不出这是旧消息。
        val intent = gestureIntent(id, phase, repeat)

        sender.post {
            //  这里是注入侧的**自建线程**：任何漏出去的异常都会按 Android 默认策略杀掉进程
            //  （system_server）。与 hook 体同一条纪律：只记日志、绝不上抛。
            try {
                //  取 Context 也挪进来：`SystemContext.get` 要反射扫一遍 `mAllApplicationThreads`，
                //  那不该发生在输入读取线程上。
                val ctx =
                    context() ?: run {
                        XLog.w("没借到 Context，无法转交 ${id.label} 的手势")
                        return@post
                    }

                wakeHost(ctx)

                // 立刻发一条（宿主已在跑时即最终送达），再补发一条兜底（宿主刚被拉起、动态接收器
                // 还没注册时）—— 两条带同一个 sent_at，宿主据此去重，都到了也只执行一次。
                //  补发只对「一次按压」有意义：长按连发（repeat）期间宿主早就在跑，每 100ms 再排一条
                //  300ms 后到期的补发，松手后队列里会积一串废消息（且每条都要过一遍接收侧的去重）。
                send(ctx, intent, delayMs = 0L, retry = false)
                if (!repeat) send(ctx, intent, delayMs = FORWARD_DELAY_MS, retry = true)
            } catch (e: Throwable) {
                XLog.w("转交手势失败（已忽略）：${id.label} ${gesture.id}", e)
            }
        }
    }

    /**
     * 转交一次「松手」（[ModuleWire.PHASE_RELEASE]）。
     *
     *  **只发一条、不补发**：能走到这里说明长按那条已经送到并被宿主执行过了 —— 宿主当时是活的，
     *  这条晚到几毫秒无所谓；而漏掉它的代价只是「那一段采集多录一会儿」。
     *
     *  松手本身不带动作、只带「这一按结束了」，要不要收尾由宿主按配置判（见
     *  `host/GestureReceiver.onRelease`）—— 判定侧不替它判，两边就不会各有一套「哪些动作要收尾」。
     */
    fun release(id: KeyId) {
        val intent = gestureIntent(id, ModuleWire.PHASE_RELEASE, repeat = false)
        sender.post {
            val ctx =
                context() ?: run {
                    // 这里不用 w：手势那条已经报过一次「没借到 Context」，每个松手再报一遍是刷屏
                    XLog.d("没借到 Context，无法转交 ${id.label} 的松手")
                    return@post
                }

            // 也唤醒一次：宿主万一在这几十毫秒里被回收，startService 会把它拉起来（它一起来就注册接收器）
            wakeHost(ctx)
            XLog.d("转交松手：${id.label}")
            send(ctx, intent, delayMs = 0L, retry = false)
        }
    }

    /** 唤醒宿主进程（动态接收器要求它活着；借来的 Context 与宿主同 uid，`startService` 才过得了 OPLUS_COMPONENT_SAFE）。 */
    private fun wakeHost(ctx: Context) {
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
    }

    /** 造一条手势广播（松手与手势共用同一套 extra，见 [ModuleWire.PHASE_RELEASE]）。 */
    private fun gestureIntent(
        id: KeyId,
        phase: String,
        repeat: Boolean,
    ): Intent =
        Intent(ModuleWire.ACTION_GESTURE)
            .setPackage(HostApp.PACKAGE)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
            .putExtra(ModuleWire.EXTRA_KEY, id.id)
            .putExtra(ModuleWire.EXTRA_PHASE, phase)
            .putExtra(ModuleWire.EXTRA_REPEAT, repeat)
            .putExtra(ModuleWire.EXTRA_SENT_AT, SystemClock.elapsedRealtime())

    /**
     * 投一条手势广播（发送抛异常只记日志 —— 这条在按键热路径上）。
     *
     * `phase` / `repeat` 从 [intent] 自己读（刚 put 进去的那两个 extra）：参数表已经贴着
     *  detekt 的上限，再收两个实参只是把同一份数据传两遍。
     *
     * @param delayMs > 0 = 排一条延后投递（补发那条）
     * @param retry true = 补发那条，日志压到 `d`（release 裁掉），免得淹掉日志。
     */
    private fun send(
        ctx: Context,
        intent: Intent,
        delayMs: Long,
        retry: Boolean,
    ) {
        //  两个 extra 也读在 `try` 里：这一支跑在专用投递线程上，抛出去就是那条线程的队列断掉 ——
        //  之后所有手势一起静默丢失，而最坏只该是「这一下没生效」。
        //  `phase` 留在外面只为失败那行日志还能写出是哪一相（读失败时是空串）。
        var phase = ""
        try {
            phase = intent.getStringExtra(ModuleWire.EXTRA_PHASE).orEmpty()
            val repeat = intent.getBooleanExtra(ModuleWire.EXTRA_REPEAT, false)
            if (delayMs <= 0L) {
                XLog.guard("发送侧键手势广播 $phase") {
                    ctx.sendBroadcast(intent)
                    XLog.w("已把侧键手势转给宿主：$phase${if (repeat) "（连续触发）" else ""}")
                }
            } else {
                sender.postDelayed({
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
