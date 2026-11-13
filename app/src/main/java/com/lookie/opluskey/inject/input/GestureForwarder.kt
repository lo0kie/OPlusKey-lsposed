package com.lookie.opluskey.inject.input

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.GestureTrigger
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
     *  （`docs/device/` 里记的那条待办就是这件事。）
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
     * [bindHost] 那一次绑定还没解 —— 只在这条发送线程（[sender]）上读写，不跨线程。
     *
     *  它的作用是「同一次唤醒不要绑两遍」：两次 `bindService` 同一个 connection 会得到两条
     *  `onServiceConnected`，而 `unbindService` 只能配对解一次，多出来的那次直接抛。
     */
    private var binding = false

    /** [nextSentAt] 的锁与上一笔 —— 判定线程与 main looper（`HeldPresses` 补发）都会来取号。 */
    private val sentAtLock = Any()
    private var lastSentAt = 0L

    /**
     * 转交一次手势。
     *
     * @param repeat 是否「长按连续触发」的重复派发（宿主据此跳过振动反馈）
     */
    fun forward(
        id: KeyId,
        gesture: GestureTrigger,
        repeat: Boolean,
    ) {
        //  **线格式不变**：三档的档位 id（`single` / `double` / `long`）与 `Gesture.id` 逐字相同，
        //  多击是新增的 `clickN` —— 老宿主收到它只会打一行「不认识的手势」，不会误执行。
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

                // 立刻发一条（宿主已在跑时即最终送达），再补发两条兜底（宿主刚被拉起、动态接收器
                // 还没注册时）—— 三条带同一个 sent_at，宿主据此去重，都到了也只执行一次。
                //  补发只对「一次按压」有意义：长按连发（repeat）期间宿主早就在跑，每 100ms 再排一条
                //  「唤醒 + 补发」只会让松手后的队列积一串废消息（且每条都要过一遍接收侧的去重）。
                send(ctx, intent, delayMs = 0L, retry = false)
                if (!repeat) {
                    send(ctx, intent, delayMs = FORWARD_DELAY_MS, retry = true)
                    //  第二条补发（#5）：宿主**进程不在**时，300ms 只够「已经在缓存里的进程」被抬起来；
                    //  冷启动（ART + 注入 + Application 起来）实测要一秒多，那 300ms 那条会打在
                    //  接收器还没注册的空档上。1.5s 这一条是给冷启动留的，仍在宿主 2s 的保质期之内。
                    send(ctx, intent, delayMs = COLD_START_RETRY_MS, retry = true)
                }
            } catch (e: Throwable) {
                XLog.w("转交手势失败（已忽略）：${id.label} ${gesture.id}", e)
            }
        }
    }

    /**
     * 转交一次**组合键**（两颗键一起按）。
     *
     *  与 [forward] 同一条发送线程、同一套「立刻 + 补发」：宿主冷进程时要靠补发那条把它抬起来。
     *  载荷里带的是**组合的 id**（`ModuleWire.EXTRA_COMBO`）而不是键 id —— 组合没有单颗键的
     *  `KeyConfig`，动作挂在 `ComboKey` 上，宿主按 id 去配置里取那一档即可
     *  （它读得到配置文件，判定侧读不到）。
     *
     *  不带 `EXTRA_KEY`：这两条的含义不重叠，混在一起会让宿主那侧的日志变成
     *  「key=cmb1」这种看不懂的东西。
     */
    fun forwardCombo(
        comboId: String,
        trigger: GestureTrigger,
        /** 按住手势的反向那一套（先按住第二颗）；窗口与正向都是 `false`。 */
        hold: Int,
        repeat: Boolean,
    ) {
        val intent = comboIntent(comboId, trigger.id, repeat, hold)
        sender.post {
            try {
                val ctx =
                    context() ?: run {
                        XLog.w("没借到 Context，无法转交组合键 $comboId")
                        return@post
                    }

                wakeHost(ctx)
                send(ctx, intent, delayMs = 0L, retry = false)
                //  连续触发的重复不补发：那期间宿主早就在跑，每 100ms 再排一条「唤醒 + 补发」只会在
                //  松手后积一串废消息（与单键那条同一条规矩）。两条补发与 [forward] 同一口径。
                if (!repeat) {
                    send(ctx, intent, delayMs = FORWARD_DELAY_MS, retry = true)
                    send(ctx, intent, delayMs = COLD_START_RETRY_MS, retry = true)
                }
            } catch (e: Throwable) {
                XLog.w("转交组合键失败（已忽略）：$comboId ${trigger.id}", e)
            }
        }
    }

    /** 唤醒宿主进程（动态接收器要求它活着；借来的 Context 与宿主同 uid，`startService` 才过得了 OPLUS_COMPONENT_SAFE）。 */
    private fun wakeHost(ctx: Context) {
        try {
            ctx.startService(Intent().setComponent(hostService))
        } catch (e: Throwable) {
            // 唤醒失败 ⇒ 宿主进程可能没起来、这次手势可能被丢：首次用 w 报出来（`d` 在 release 里
            //  被编译期消除，只留 `d` 会让「一直失败、手势静默丢失」完全无痕），之后压回 `d` 免刷屏。
            warnOnce("唤醒宿主进程失败，手势可能被丢：${e.message}")
        }
        bindHost(ctx)
    }

    /**
     * 兜底唤醒：**绑定**一次（`BIND_AUTO_CREATE` 会创建进程）。
     *
     * ## 为什么光有 `startService` 不够
     *
     *  宿主进程被系统回收之后（设置页退到后台几分钟，`com.oplus.gesture` 就没了 —— 真机实测
     *  它的 pid 会换），这条转交只剩两条路：`startService` 把进程抬起来，或绑定把进程创建出来。
     *  而 `startService` 在后台**可能被拒**（Android 12 起那条「后台不许起服务」的限制，
     *  system 身份是否豁免随 ROM 而变），表现就是「设置页里按得动、回到桌面按了没反应」：
     *  广播是 `RECEIVER_REGISTERED_ONLY` 的，进程不在就没人收，两条都掉。
     *
     *  绑定不受那条限制（`bindService` 从来不是受限动作），成功之后进程就起来了，
     *  动态接收器随之注册 —— 后面那条（或补发那条）广播才有着落。
     *
     *  **绑完就解**（[WAKE_BIND_MS] 之后）：这里要的只是「把进程叫起来」，不是长期保活。
     *  长期绑定会让宿主常驻，与 `keepProcessAwake` 那条「不预热、不自己弹页」的取舍冲突。
     */
    private fun bindHost(ctx: Context) {
        if (binding) return
        binding = true
        try {
            val ok =
                ctx.bindService(
                    Intent().setComponent(hostService),
                    wakeConnection,
                    Context.BIND_AUTO_CREATE,
                )
            if (!ok) {
                // 返回 false = 系统连试都没试（服务不存在 / 权限不够），这时 unbind 会抛
                binding = false
                warnOnce("绑定宿主服务被拒（唤醒兜底也没生效）：$HOST_SERVICE")
                return
            }
            sender.postDelayed({ releaseBind(ctx) }, WAKE_BIND_MS)
        } catch (e: Throwable) {
            binding = false
            warnOnce("绑定宿主服务失败，手势可能被丢：${e.message}")
        }
    }

    /** 解掉 [bindHost] 那一次绑定（只解自己绑的那次，且只解一次）。 */
    private fun releaseBind(ctx: Context) {
        if (!binding) return
        binding = false
        try {
            ctx.unbindService(wakeConnection)
        } catch (e: Throwable) {
            // 已经自己断了（进程被杀 / 服务被停）⇒ 这次 unbound 必然抛，忽略
            XLog.d("解绑宿主服务失败（若它已断开就无所谓）：${e.message}")
        }
    }

    /**
     * 绑定回调 —— 只打日志：这一条是「唤醒到底成没成」的唯一判据行。
     *
     *  回调在主线程（`Context.bindService` 的约定），这里只写日志、不碰任何状态。
     */
    private val wakeConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                XLog.w("唤醒宿主：已绑上 ${name?.className}（进程已起，补发那条能送到了）")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                XLog.d("唤醒宿主：绑定断开 ${name?.className}")
            }
        }

    /** 同一条失败只报一次（`w`），之后压回 `d` —— 这条路每个手势都走，常报会刷屏。 */
    private fun warnOnce(message: String) {
        if (!wakeWarned) {
            wakeWarned = true
            XLog.w(message)
        } else {
            XLog.d(message)
        }
    }

    /** 造一条**组合键**广播（与 [gestureIntent] 共用 `phase` / `sent_at` 那几格，只是主身份不同）。 */
    private fun comboIntent(
        comboId: String,
        phase: String,
        repeat: Boolean,
        hold: Int,
    ): Intent =
        Intent(ModuleWire.ACTION_GESTURE)
            .setPackage(HostApp.PACKAGE)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
            .putExtra(ModuleWire.EXTRA_COMBO, comboId)
            .putExtra(ModuleWire.EXTRA_PHASE, phase)
            .putExtra(ModuleWire.EXTRA_REPEAT, repeat)
            //  「按住手势的反向」—— 只有组合用得上这一格（单键的 `gestureIntent` 不带它）
            .putExtra(ModuleWire.EXTRA_HOLD, hold)
            .putExtra(ModuleWire.EXTRA_SENT_AT, nextSentAt())

    /** 造一条手势广播（载荷见 `ModuleWire` 的 `EXTRA_*`）。 */
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
            .putExtra(ModuleWire.EXTRA_SENT_AT, nextSentAt())

    /**
     * 这一次派发的 `sent_at` —— 取「现在」，但**必须大于上一条**。
     *
     *  宿主按 `(键, 手势)` 记 `sent_at`，`sent_at <= 上次` 一律当补发丢掉（见 `host/GestureExec`
     *  的 `staleOrDuplicate`）。而同一种手势在同一毫秒里派发两条是**真会发生**的：
     *  「双击为无操作 ⇒ 这两下各算一次单击」那一档就在抬手那一刻连发两条 `Click(1)`
     *  （见 `GestureDetector.onKeyUpLocked`）。两条同毫秒 ⇒ 第二条被当成补发，用户看到的是
     *  「只执行了一下」。
     *
     *  补发那两条仍复用**同一个** Intent（`sent_at` 只在这里打一次），所以去重照旧成立。
     */
    private fun nextSentAt(): Long =
        synchronized(sentAtLock) {
            val now = SystemClock.elapsedRealtime()
            val next = if (now > lastSentAt) now else lastSentAt + 1
            lastSentAt = next
            next
        }

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
         * 第一条补发的延迟（毫秒）—— 冷进程（开机后第一次按侧键）时它才是有效那条。
         *
         *  300ms 那条：够 `startService` 把宿主从**缓存进程**抬起来；冷启动（进程已被回收）
         *  要一秒多，那条会打在接收器注册之前 —— 所以还有下面那条。
         */
        const val FORWARD_DELAY_MS = 300L

        /**
         * 第二条补发（冷启动兜底）的延迟。
         *
         *  1500ms 是实测口径：日志里「判出手势 → 宿主收到」那一段在进程刚起来时约 1.1~2.1 秒。
         *  上限由宿主那边的 `STALE_MS`（2s）卡着 —— 再往后排也没用，只会被当成过期消息丢掉。
         */
        const val COLD_START_RETRY_MS = 1500L

        /** 唤醒用的那次绑定保留多久（只把进程叫起来，不长期保活）。 */
        const val WAKE_BIND_MS = 5000L

        /** 宿主的侧键服务（`onBind` 型，启动它只为抬高进程级别）；名字只在 `Framework` 里写一次。 */
        val HOST_SERVICE = Framework.HOST_KEY_SERVICE
    }
}
