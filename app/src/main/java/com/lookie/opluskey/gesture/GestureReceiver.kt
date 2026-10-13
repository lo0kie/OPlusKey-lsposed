package com.lookie.opluskey.gesture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.ConfigBroadcast
import com.lookie.opluskey.config.ConfigStore
import com.lookie.opluskey.config.Gesture
import com.lookie.opluskey.config.KeyConfig
import com.lookie.opluskey.config.KeyId
import com.lookie.opluskey.hook.ActionRunner

/** 接收 `system_server` 转来的判好的侧键手势，按配置执行动作。 */
internal object GestureReceiver {

    /** 已注册的接收器 —— 永不移除（注册后随宿主主进程一直活着，`install` 里用非空判断挡住重复注册）。 */
    private var receiver: BroadcastReceiver? = null

    private var runner: ActionRunner? = null

    /** 宿主进程的 Application Context —— 振动反馈要用它取 `Vibrator`。 */
    @Volatile
    private var appContext: Context? = null

    /** 主线程 Handler：动作执行在它上面串行。 */
    private val handler: Handler = Handler(Looper.getMainLooper())

    /**
     * 最近处理过的 (键, 手势) 的 `sent_at` —— 压掉转发侧「立刻 + 补发」两条的重复。
     *
     * 只有 `KeyId.entries` × 三种手势 + 放行这几档，读写都在主线程，不需要额外同步。
     * 它是进程内的：宿主进程若在这 300ms 里被杀又重启，补发那条会被当成新的。
     */
    private val lastHandledAt = HashMap<String, Long>()

    /** 取 Application Context 的重试次数与间隔。 */
    private const val CTX_RETRY = 10
    private const val CTX_RETRY_DELAY_MS = 500L

    /** 宿主自己的侧键服务 —— keepProcessAwake 启动它以抬高进程级别。 */
    private const val KEY_SERVICE = "com.oplus.gesture.server.ActionButtonKeyService"

    /**
     * 手势的「保质期」（毫秒）：超过它视为冻结期间积压的过期手势，丢弃不执行。
     *
     * 正常链路送达延迟 ≈ 转发侧的 300ms + 排队，2s 已是宽裕上限；
     * 丢动作比「积压后一次性全执行」好（后者是不可预期的连环触发）。
     */
    private const val STALE_MS = 2000L

    /** 自持循环判据：窗口内收到这么多次「放行」就认为环起来了。 */
    private const val PASSTHROUGH_BURST = 3
    private const val PASSTHROUGH_WINDOW_MS = 1000L

    /** 判出环后停掉注入的时长（毫秒）——宁可这一次原生动作拿不到，也不能让环跑起来。 */
    private const val PASSTHROUGH_COOLDOWN_MS = 3000L

    /** 「放行」突发计数（只在主线程读写，不需要额外同步）。 */
    private var passthroughBurst = 0
    private var passthroughBurstAt = 0L
    private var passthroughCooldownUntil = 0L

    /** 见 [PASSTHROUGH_BURST]：真环起来了就返回 true（调用方跳过注入）。 */
    private fun tooManyPassthrough(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < passthroughCooldownUntil) return true
        if (now - passthroughBurstAt > PASSTHROUGH_WINDOW_MS) {
            passthroughBurstAt = now
            passthroughBurst = 0
        }
        passthroughBurst++
        if (passthroughBurst >= PASSTHROUGH_BURST) {
            passthroughCooldownUntil = now + PASSTHROUGH_COOLDOWN_MS
            passthroughBurst = 0
            return true
        }
        return false
    }

    fun install(loader: ClassLoader) {
        if (receiver != null) {
            XLog.i("手势接收器已经注册过，跳过")
            return
        }
        // `onPackageReady` 时 Application 未必已经挂上（`currentApplication()` 可能是 null），
        // 所以拿不到就退后重试，而不是直接放弃 —— 否则整条链路静默失效。
        tryGetContext(loader, attempt = 0)
    }

    private fun tryGetContext(loader: ClassLoader, attempt: Int) {
        val ctx = applicationContext(loader)
        if (ctx == null) {
            if (attempt >= CTX_RETRY) {
                XLog.w("拿不到 Application Context（重试 $attempt 次）—— 手势接收器没装上，" +
                    "侧键会「拦下来但什么都不做」")
                return
            }
            handler.postDelayed({
                XLog.guard("重试取 Application Context") { tryGetContext(loader, attempt + 1) }
            }, CTX_RETRY_DELAY_MS)
            return
        }

        runner = ActionRunner(context = ctx, handler = handler)
        appContext = ctx

        keepProcessAwake(ctx)

        // 宿主一起来就把当前完整配置广播给注入侧（system_server）：
        // 开机时 CE 存储还没解锁，那一读只能读到默认值，不重发一次就永远错下去。
        registerUnlockListener(ctx)
        publishConfigState(ctx)

        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 设置页问「宿主侧的模块在不在」——回一条报活（见 [KeyModule.ACTION_HOST_SERVICE_QUERY]）。
                //  要这一问是因为报活广播是**一次性**的：我们发出时设置页可能还没起来。
                if (intent?.action == KeyModule.ACTION_HOST_SERVICE_QUERY) {
                    XLog.w("设置页问宿主模块在不在 —— 回一条报活")
                    context?.sendBroadcast(
                        Intent(KeyModule.ACTION_HOST_SERVICE_UP)
                            .setPackage(KeyModule.PAGE_PACKAGE),
                    )
                    return
                }
                val key = intent?.getStringExtra(KeyModule.EXTRA_KEY)
                val phase = intent?.getStringExtra(KeyModule.EXTRA_PHASE)
                val repeat = intent?.getBooleanExtra(KeyModule.EXTRA_REPEAT, false) ?: false
                val sentAt = intent?.getLongExtra(KeyModule.EXTRA_SENT_AT, 0L) ?: 0L
                XLog.d("收到 system_server 转来的手势：key=$key phase=$phase repeat=$repeat")
                XLog.guard("处理转来的手势 $key/$phase") { handle(key, phase, repeat, sentAt) }
            }
        }
        try {
            // Android 14（API 34）起动态注册必须显式声明导出性。发送方是 system_server（uid 1000，
            // 经 GestureForwarder 发出），不是宿主自己 —— 系统身份对 NOT_EXPORTED 接收器照样可达，
            // 而普通 App 即便发同名广播也进不来（别按「同 uid 宿主」去改成 EXPORTED，那是伪造口子）。
            ctx.registerReceiver(
                r,
                IntentFilter(KeyModule.ACTION_GESTURE).apply {
                    // 同一个接收器兼收设置页那句「在不在」（见上面那个 action 分支）
                    addAction(KeyModule.ACTION_HOST_SERVICE_QUERY)
                },
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiver = r
            XLog.w("手势接收器已注册（宿主进程）—— 侧键动作执行都在这里")
        } catch (e: Throwable) {
            XLog.w("注册手势接收器失败", e)
        }
    }

    /**
     * 取宿主进程的 Application Context —— 宿主是普通 App 进程，
     * `ActivityThread.currentApplication()` 在这儿有效（system_server 那边拿不到）。
     */
    private fun applicationContext(loader: ClassLoader): Context? = try {
        val cls = Reflect.findClass(loader, "android.app.ActivityThread")
        val app = if (cls == null) null else Reflect.callStatic(cls, "currentApplication")
        app as? Context
    } catch (e: Throwable) {
        XLog.d("取 Application Context 失败：${e.message}")
        null
    }

    /**
     * 把宿主进程从「缓存」提到「持有已启动服务」的级别 —— 防冻结。
     *
     * 缓存进程会被系统冻结（ColorOS 冻得更狠），冻结期间发给它的广播被压住、解冻瞬间一次性灌入，
     * 动作就会积压后连环执行。启动宿主自己的 ActionButtonKeyService（只有 `onBind`，
     * `onStartCommand` 走平台默认空实现）后进程属 service 级而非缓存级、不在可冻结范围里，广播从此实时送达。
     */
    private fun keepProcessAwake(ctx: Context) {
        try {
            ctx.startService(
                Intent().setComponent(
                    android.content.ComponentName(ctx.packageName, KEY_SERVICE),
                ),
            )
            XLog.w("已启动宿主的 $KEY_SERVICE（防进程冻结，见 keepProcessAwake 注释）")
            // 顺手给设置页报个活：**宿主侧的模块加载好了**（这一步只有模块注入成功才跑得到）。
            //  首页据此把「模块未生效」的提示收掉、解除整页禁用。设置页与宿主同包，
            // `setPackage` 指定包名就能送到它注册的接收器（不必轮询）。
            ctx.sendBroadcast(
                Intent(KeyModule.ACTION_HOST_SERVICE_UP).setPackage(KeyModule.PAGE_PACKAGE),
            )
        } catch (e: Throwable) {
            // 兜底在 `handle` 的过期丢弃：冻结顶多让动作晚执行/被丢，不会集体炸出
            XLog.w("启动宿主服务失败（进程可能仍会被冻结）：${e.message}")
        }
    }

    /**
     * 把当前完整配置广播给注入侧（那边挂的是 AMS 的广播入口）。
     *
     * 宿主私有目录 `/data/data/com.oplus.gesture/files/` 在开机时处于 CE（Credential Encrypted）
     * 未解锁状态，那一读只能读到默认值（`enabled=false`）；把这个「假 false」广播上去，
     * 注入侧就按「不接管」处理且没有东西会纠正它。所以：进程启动时发一次（未解锁读到 false，
     * 注入侧不吞键、侧键保持系统原生行为），registerUnlockListener 保证解锁那一刻再发一次切到真值。
     */
    private fun publishConfigState(ctx: Context) {
        XLog.guard("广播配置给注入侧") {
            ctx.sendBroadcast(
                ConfigBroadcast.intent(ConfigStore.read())
                    .setPackage(KeyModule.PAGE_PACKAGE),
            )
            XLog.w("已广播配置给注入侧（用户已解锁=${isUserUnlocked(ctx)}）")
        }
    }

    /**
     * 只在当前还没解锁时注册 —— 已解锁挂它纯属白挂。
     * 两条 action 互为兜底：`ACTION_USER_UNLOCKED` 是正主，`ACTION_BOOT_COMPLETED`
     * 防「解锁早于本进程启动、错过前一条」；都是受保护系统广播，`RECEIVER_NOT_EXPORTED` 照样收得到。
     */
    private fun registerUnlockListener(ctx: Context) {
        if (isUserUnlocked(ctx)) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                XLog.w("用户已解锁（action=${intent?.action}）—— 重读配置并广播给注入侧")
                context?.let { publishConfigState(it) }
            }
        }
        try {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_USER_UNLOCKED)
            filter.addAction(Intent.ACTION_BOOT_COMPLETED)
            ctx.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            XLog.w("尚未解锁：已挂解锁监听，解锁后重读配置再广播一次（CE 存储在此之前读不了）")
        } catch (e: Throwable) {
            // 最坏情况是「主开关停在开机时的默认值」—— 打开快捷键页面仍可手动同步
            XLog.w("注册解锁监听失败：${e.message}")
        }
    }

    private fun isUserUnlocked(ctx: Context): Boolean = try {
        ctx.getSystemService(android.os.UserManager::class.java)?.isUserUnlocked ?: true
    } catch (e: Throwable) {
        true
    }

    /**
     * 一个判好的手势：读配置 → 执行动作。
     *
     * 配置每次都重读（`ConfigStore.read()`）—— 文件在宿主私有目录里、同 uid 直接读，
     * 所以「改完即时生效」在这一侧天然成立。
     * 主开关（`KeyConfig.enabled`）的执行侧闸门：判定在 system_server、配置来自广播，
     * 这里再按文件真值拦一道，两边不会各说各话。
     */
    private fun handle(keyId: String?, phaseId: String?, repeat: Boolean, sentAt: Long) {
        val key = KeyId.fromId(keyId.orEmpty()) ?: run {
            XLog.w("不认识的键 id：$keyId")
            return
        }
        val r = runner ?: run {
            XLog.w("ActionRunner 还没建好，忽略 ${key.label} 的 $phaseId")
            return
        }

        // 过期丢弃：正常送达延迟是几百毫秒；超过 STALE_MS 说明这条在系统里被压了很久
        //（进程曾被冻结）—— 一次性灌进来的那批全在这里拦掉。
        val age = android.os.SystemClock.elapsedRealtime() - sentAt
        if (age > STALE_MS) {
            XLog.w("${key.label} 的 $phaseId 已过期（发出于 ${age}ms 前），丢弃不执行")
            return
        }

        // 去重：转发侧对同一次手势会发两条（立刻 + 300ms 后补发，见 hook/GestureForwarder），
        // 两条 sent_at 相同 —— 按 (键, 手势) 记下最近处理过的时刻，sent_at <= 它的一律忽略。
        // 判据用 <= 而非 ==：两次按压间隔小于补发延迟时，补发那条会晚于下一次按压到达。
        // sent_at 缺失（<= 0）时整段跳过，不能让它把后续手势全挡掉。
        if (sentAt > 0L) {
            val token = "${key.id}/$phaseId"
            val prev = lastHandledAt[token]
            if (prev != null && sentAt <= prev) {
                XLog.d("${key.label} 的 $phaseId 这一条已经处理过（补发的那条），忽略")
                return
            }
            lastHandledAt[token] = sentAt
        }

        // 放行（配了 native / passthrough）：重新注入一次侧键，让系统那套照常跑。
        // 主开关关不关都要放 —— 走到这里说明判定侧已决定放行。
        if (phaseId == KeyModule.PHASE_PASSTHROUGH) {
            // 记一次「放行窗口」：本次按压在框架侧还有一条场景派发（原生动作的载体），
            // 宿主侧的钩子据此放它过去（见 [SideKeyGestureHooks.notePassthrough]）。
            SideKeyGestureHooks.notePassthrough()
            // 自持循环的兜底闸：正常一次「放行」只注入一次；短时间内反复收到放行判定
            // 说明重注入的事件被再认领了一次（旧行为 / 个别 ROM 的 deviceId 判不出合成事件），
            // 这时停掉注入，宁可这一次原生动作拿不到也不能让环跑起来。
            if (tooManyPassthrough()) {
                XLog.w("${key.label}：放行判定 ${PASSTHROUGH_BURST} 次/${PASSTHROUGH_WINDOW_MS}ms —— " +
                    "疑似自持循环，暂停注入 ${PASSTHROUGH_COOLDOWN_MS}ms")
                return
            }
            XLog.w("${key.label}：放行一次（重新注入 keyCode=${key.keyCode}）")
            r.injectKeyForForward(key.keyCode)
            return
        }

        val gesture = when (phaseId) {
            Gesture.SINGLE.id -> Gesture.SINGLE
            Gesture.DOUBLE.id -> Gesture.DOUBLE
            Gesture.LONG.id -> Gesture.LONG
            else -> {
                XLog.w("不认识的手势：$phaseId")
                return
            }
        }

        val config = ConfigStore.read()
        val keyConfig = config.key(key)
        if (!keyConfig.enabled) {
            XLog.d("${key.label}：主开关已关，忽略这次 ${gesture.label}（模块不生效）")
            return
        }

        // 文件里这一档恰好是 `native` / `passthrough`（两份配置一前一后、不同步的瞬间），
        // 而广播里的配置说「这一下要执行动作」（判定侧据此已把键吞了）—— 必须补一次重注入：
        // 否则键被 system_server 吞掉、宿主这边又派发到 Unit，表现是「按了没反应」。
        // `ActionRunner.dispatch` 里 native / passthrough 落到 Unit 是对的（多步命令里那一步本就不该做什么）。
        val action = keyConfig.actionOf(gesture)
        if (action is Action.Native || action is Action.Passthrough) {
            XLog.w(
                "${key.label}·${gesture.label}：文件里这一档是「放行」（与判定侧不同步）" +
                    "—— 补一次重注入，免得键被吞掉又什么都不做",
            )
            r.injectKeyForForward(key.keyCode)
            return
        }

        runAction(key, gesture, keyConfig, repeat, config)
    }

    /** 执行一个手势对应的动作（振动反馈也在这里）。 */
    private fun runAction(
        id: KeyId,
        gesture: Gesture,
        keyConfig: KeyConfig,
        repeat: Boolean,
        config: com.lookie.opluskey.config.Config,
    ) {
        val action = keyConfig.actionOf(gesture)
        val r = runner ?: run {
            XLog.w("ActionRunner 还没建好，忽略 ${id.label} 的 ${gesture.id}")
            return
        }
        // 振动反馈：只在「真的执行一个动作」的那一次给 —— 无操作不震、
        // 连续触发的重复也不震（否则按住不放会一直震，与 KSU 模块同一口径）。
        if (appContext != null && !repeat && isRealAction(action) && config.global.vibrate) {
            vibrate()
        }
        // 这条是按键的可见证据（release 里也保留），所以只在第一次执行时打 W；
        val log = "${id.label}·${gesture.label}${if (repeat) "（连续触发）" else ""} → ${action.token}" +
            "（宿主进程执行）"
        if (repeat) XLog.d(log) else XLog.w(log)
        r.run(action, "${id.label}·${gesture.label}")
    }

    /**
     * 这个动作是不是「真的会做点什么」。
     *
     * `none`（吞掉按键什么都不做）与 `native` / `passthrough`（放行给系统）
     * 都不该给振动反馈 —— 与 KSU 模块 daemon 的 `action_is_noop` 同一口径。
     */
    private fun isRealAction(action: Action): Boolean = when (action) {
        is Action.None, is Action.Native, is Action.Passthrough -> false
        else -> true
    }

    /** `Vibrator` 只取一次（每次按键都取一次不划算）。 */
    @Volatile
    private var vibrator: android.os.Vibrator? = null

    /**
     * 触发一次短振动（`vibrate=1` 时）。
     *
     * 等价于 KSU 模块的 `cmd vibrator_manager synced -f oneshot 30`（30ms），
     * 直接用公开 API —— 宿主持有 `android.permission.VIBRATE`（真机 `aapt2 dump permissions` 确认）。
     * 任何失败只记日志：振动只是反馈，不能因为它把动作执行搅黄。
     */
    private fun vibrate() {
        try {
            val ctx = appContext ?: run {
                XLog.d("还没有 Application Context，跳过振动反馈")
                return
            }
            val v = vibrator ?: ctx.getSystemService(android.os.Vibrator::class.java)?.also {
                vibrator = it
            }
            if (v == null || !v.hasVibrator()) {
                XLog.d("这台设备没有振动器，跳过振动反馈")
                return
            }
            v.vibrate(
                android.os.VibrationEffect.createOneShot(
                    VIBRATE_MS,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
            XLog.d("振动反馈（${VIBRATE_MS}ms）")
        } catch (e: Throwable) {
            XLog.w("振动反馈失败（忽略）", e)
        }
    }

    /** 振动时长（毫秒）—— 与 KSU 模块的 30ms 对齐。 */
    private const val VIBRATE_MS = 30L
}
