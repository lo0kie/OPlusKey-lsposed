package com.lookie.opluskey.host

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.seal.Seal
import com.lookie.opluskey.core.seal.Verdict
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.host.action.ActionRunner
import com.lookie.opluskey.host.action.FailureNotice
import com.lookie.opluskey.host.action.actionNoticeText

/** 接收 `system_server` 转来的判好的侧键手势，按配置执行动作。 */
internal object GestureReceiver {
    /** 已注册的接收器 —— 永不移除（注册后随宿主主进程一直活着，`install` 里用非空判断挡住重复注册）。 */
    private var receiver: BroadcastReceiver? = null

    private var runner: ActionRunner? = null

    /** 宿主进程的 Application Context —— 振动反馈要用它取 `Vibrator`。 */
    @Volatile
    private var appContext: Context? = null

    /**
     * 最后一次**真的执行了动作**的时刻（`elapsedRealtime()`，0 = 从未）。
     *
     * 链路自检的两笔端到端实证之一：注入侧那笔（`KeyHooks.lastGestureAt`）证明「判出了手势」，
     * 这一笔证明「动作真被执行了」—— 两笔都有才叫整条链跑通。
     */
    @Volatile
    private var lastActionAt: Long = 0L

    /** 主线程 Handler：动作执行在它上面串行。 */
    private val handler: Handler = Handler(Looper.getMainLooper())

    /**
     * 最近处理过的 (键, 手势) 的 `sent_at` —— 压掉转发侧「立刻 + 补发」两条的重复。
     *
     * 只有 `KeyId.entries` × 三种手势这几档，读写都在主线程，不需要额外同步。
     * 它是进程内的：宿主进程若在这 300ms 里被杀又重启，补发那条会被当成新的。
     */
    private val lastHandledAt = HashMap<String, Long>()

    /** 取 Application Context 的重试次数与间隔。 */
    private const val CTX_RETRY = 10
    private const val CTX_RETRY_DELAY_MS = 500L

    /** 宿主自己的侧键服务 —— keepProcessAwake 启动它以抬高进程级别（名字只在 `Framework` 里写一次）。 */
    private val KEY_SERVICE = Framework.HOST_KEY_SERVICE

    /**
     * 手势的「保质期」（毫秒）：超过它视为冻结期间积压的过期手势，丢弃不执行。
     *
     * 正常链路送达延迟 ≈ 转发侧的 300ms + 排队，2s 已是宽裕上限；
     * 丢动作比「积压后一次性全执行」好（后者是不可预期的连环触发）。
     */
    private const val STALE_MS = 2000L

    /** 触觉反馈的时长（毫秒）—— 与最初宿主侧那路同一档（30ms 短振）。 */
    private const val HAPTIC_MS = 30L

    fun install(loader: ClassLoader) {
        if (receiver != null) {
            XLog.w("手势接收器已经注册过，跳过")
            return
        }
        // `onPackageReady` 时 Application 未必已经挂上（`currentApplication()` 可能是 null），
        // 所以拿不到就退后重试，而不是直接放弃 —— 否则整条链路静默失效。
        tryGetContext(loader, attempt = 0)
    }

    private fun tryGetContext(
        loader: ClassLoader,
        attempt: Int,
    ) {
        val ctx = ActivityThreadCompat.currentApplication(loader)
        if (ctx == null) {
            if (attempt >= CTX_RETRY) {
                XLog.w(
                    "拿不到 Application Context（重试 $attempt 次）—— 手势接收器没装上，" +
                        "侧键会「拦下来但什么都不做」",
                )
                return
            }
            handler.postDelayed({
                XLog.guard("重试取 Application Context") { tryGetContext(loader, attempt + 1) }
            }, CTX_RETRY_DELAY_MS)
            return
        }

        // 签名闸：包被改过并重签时**不装接收器** —— 侧键从此「拦下来但什么都不做」。
        //  判据与核心防线同一处（`core/seal/Seal`）；判在这里是因为**此刻才拿得到 Context**
        //  （`KeyModule.onPackageReady` 那一刻 `currentApplication()` 往往还是 null，见那边的说明）。
        if (Seal.verdict(ctx) == Verdict.UNOFFICIAL) {
            XLog.e("本包签名与出厂指纹不符 ⇒ 不装手势接收器（见「关于 → 签名」；换回官方包即恢复）")
            return
        }

        runner = ActionRunner(context = ctx, handler = handler)
        appContext = ctx

        keepProcessAwake(ctx)

        // 宿主一起来就把当前完整配置广播给注入侧（system_server）。配置在 DE 里 ⇒
        // 解锁前这一次读到的也是真值，侧键从开机起就按配置接管（详见 [publishConfigState]）。
        registerUnlockListener(ctx)
        publishConfigState(ctx)
        // 报活放在配置之后：这一条要带上「读到的 enabled」，先发就读不到真值
        reportAlive(ctx)

        val r =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    // 设置页问「宿主侧的模块在不在」——回一条报活（见 [ModuleWire.ACTION_HOST_SERVICE_QUERY]）。
                    //  要这一问是因为报活广播是**一次性**的：我们发出时设置页可能还没起来。
                    if (intent?.action == ModuleWire.ACTION_HOST_SERVICE_QUERY) {
                        XLog.w("设置页问宿主模块在不在 —— 回一条报活")
                        context?.let { reportAlive(it) }
                        return
                    }
                    // 设置页按了「重启服务」：那边马上要杀自己，由我们（宿主主进程）等一下把页面拉回来，
                    //  拉起之后本进程也重来一遍（见 [PageRelaunch]）。
                    if (intent?.action == ModuleWire.ACTION_PAGE_RELAUNCH) {
                        XLog.w("设置页请求重启服务 —— 由本进程稍后把页面拉回来")
                        context?.let { PageRelaunch.schedule(it) }
                        return
                    }
                    // 注意：取 extra 与派发**同在一个 guard 里**：三句
                    //  `getXxxExtra` 露在 guard 外面，一旦抛出就是宿主主进程的未捕获异常。
                    XLog.guard("处理转来的手势") {
                        val key = intent?.getStringExtra(ModuleWire.EXTRA_KEY)
                        val phase = intent?.getStringExtra(ModuleWire.EXTRA_PHASE)
                        val repeat = intent?.getBooleanExtra(ModuleWire.EXTRA_REPEAT, false) ?: false
                        val sentAt = intent?.getLongExtra(ModuleWire.EXTRA_SENT_AT, 0L) ?: 0L
                        XLog.d("收到 system_server 转来的手势：key=$key phase=$phase repeat=$repeat")
                        handle(key, phase, repeat, sentAt)
                    }
                }
            }
        try {
            // Android 14（API 34）起动态注册必须显式声明导出性。发送方是 system_server（uid 1000，
            // 经 GestureForwarder 发出），不是宿主自己 —— 系统身份对 NOT_EXPORTED 接收器照样可达，
            // 而普通 App 即便发同名广播也进不来（别按「同 uid 宿主」去改成 EXPORTED，那是伪造口子）。
            ctx.registerReceiver(
                r,
                IntentFilter(ModuleWire.ACTION_GESTURE).apply {
                    // 同一个接收器兼收设置页那句「在不在」（见上面那个 action 分支）
                    addAction(ModuleWire.ACTION_HOST_SERVICE_QUERY)
                    // 以及「重启服务」那句「请你把页面拉回来」（同上）
                    addAction(ModuleWire.ACTION_PAGE_RELAUNCH)
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
        } catch (e: Throwable) {
            // 兜底在 `handle` 的过期丢弃：冻结顶多让动作晚执行/被丢，不会集体炸出
            XLog.w("启动宿主服务失败（进程可能仍会被冻结）：${e.message}")
        }
    }

    /**
     * 给设置页报活：**宿主侧的模块加载好了**（这一步只有模块注入成功才跑得到）。
     *
     *  首页据此把「模块未生效」的提示收掉、解除整页禁用。设置页与宿主同包，
     * `setPackage` 指定包名就能送到它注册的接收器（不必轮询）。
     *
     * 顺带带上两个诊断字段，让首页能判「读到的配置是不是真值」与「这个键执行过没有」——
     * 光说一句「我在」判不出那两种（配置没到 / 从来没按过）的真实故障，见 `ui/state/LinkCheck.kt`。
     */
    private fun reportAlive(ctx: Context) = sendAlive(ctx, ConfigStore.read().key(KeyId.PLUS).enabled)

    /**
     * 报活的发送本身。`enabled` 由调用方给。
     *
     *  为什么要把这一位拆出来：每执行一次动作都会补发一条报活（见 [runAction]），而那一层
     *  手上**已经有一份刚读出来的配置**了 —— 再让它自己去读一遍是白费一次剥外皮 + JSON 解析。
     *  「现读」这条语义没有变：同一串同步调用里没有别人会去写那份文件。
     */
    private fun sendAlive(
        ctx: Context,
        enabled: Boolean,
    ) {
        PageWire.send(
            ctx,
            HostApp.PACKAGE,
            Intent(ModuleWire.ACTION_HOST_SERVICE_UP)
                .putExtra(ModuleWire.EXTRA_HOST_ENABLED, enabled)
                .putExtra(ModuleWire.EXTRA_HOST_LAST_ACTION_AT, lastActionAt),
        )
    }

    /**
     * 把当前完整配置广播给注入侧（那边挂的是 AMS 的广播入口）。
     *
     * 配置放在宿主私有 **DE** 目录（`/data/user_de/0/com.oplus.gesture/files/`），与设备加密同源、
     * **开机即可读** —— 所以进程启动时这一次广播带的就是真值，解锁前也接管。
     * 解锁那一刻再发一次是三个时机之一（启动 / 解锁 / 写盘），保证注入侧那份不会落后。
     */
    private fun publishConfigState(ctx: Context) {
        XLog.guard("广播配置给注入侧") {
            val config = ConfigStore.read()
            if (PageWire.send(ctx, HostApp.PACKAGE, ConfigBroadcast.intent(config, CommandLibrary.longIsSerial(config)))) {
                XLog.w("已广播配置给注入侧（用户已解锁=${isUserUnlocked(ctx)}）")
            }
        }
    }

    /**
     * 只在当前还没解锁时注册 —— 已解锁挂它纯属白挂。
     * 两条 action 互为兜底：`ACTION_USER_UNLOCKED` 是正主，`ACTION_BOOT_COMPLETED`
     * 防「解锁早于本进程启动、错过前一条」；都是受保护系统广播，`RECEIVER_NOT_EXPORTED` 照样收得到。
     *
     * 解锁时再广播一次配置 —— 「启动 / 解锁 / 写盘」三个时机之一（`AGENTS.md` 二、9）。
     */
    private fun registerUnlockListener(ctx: Context) {
        if (isUserUnlocked(ctx)) return
        val r =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
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

    private fun isUserUnlocked(ctx: Context): Boolean =
        try {
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
    private fun handle(
        keyId: String?,
        phaseId: String?,
        repeat: Boolean,
        sentAt: Long,
    ) {
        val key =
            KeyId.fromId(keyId.orEmpty()) ?: run {
                XLog.w("不认识的键 id：$keyId")
                return
            }
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略 ${key.label} 的 $phaseId")
                return
            }

        // 过期丢弃：正常送达延迟是几百毫秒；超过 STALE_MS 说明这条在系统里被压了很久
        // （进程曾被冻结）—— 一次性灌进来的那批全在这里拦掉。
        //  注意：`sentAt` 缺失（<= 0）时**不判过期**：`elapsedRealtime() - 0`
        //   必然大于 STALE_MS，缺 extra 的那条会被无条件丢掉 —— 而紧接着那段去重明确写着
        //   「sent_at 缺失时整段跳过（不能让它把后续手势全挡掉）」，两处口径相反。
        //   统一成「缺失 = 不判」：旧版本转发侧 / 手搓的广播仍然能执行。
        if (sentAt > 0L) {
            val age = android.os.SystemClock.elapsedRealtime() - sentAt
            if (age > STALE_MS) {
                XLog.w("${key.label} 的 $phaseId 已过期（发出于 ${age}ms 前），丢弃不执行")
                return
            }
        }

        // 去重：转发侧对同一次手势会发两条（立刻 + 300ms 后补发，见 inject/input/GestureForwarder），
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

        val gesture =
            when (phaseId) {
                Gesture.SINGLE.id -> {
                    Gesture.SINGLE
                }

                Gesture.DOUBLE.id -> {
                    Gesture.DOUBLE
                }

                Gesture.LONG.id -> {
                    Gesture.LONG
                }

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

        runAction(key, gesture, keyConfig, repeat, config)
    }

    /**
     * 执行一个手势对应的动作。
     *
     * 注意：**触觉反馈的唯一一处就在这里**（先说「只要不无操作立即振动」、
     * 又说「整体服务没起来时按键不要振动，不要会误以为功能能用」—— 后者把位置钉死在宿主侧）。
     *
     * 「振了」必须等价于「服务在跑、动作马上要执行」：
     *  · 宿主进程没起来 / 模块没注入进宿主 ⇒ 这个方法根本走不到 ⇒ **不振**（就是要这样：
     *    振了却什么都没发生，用户会以为功能好用，然后去怪系统）；
     *  · 判定侧（system_server）振得不到这个保证 —— 那边判出手势就振，可手势转给宿主之后
     *    宿主在不在、能不能执行，它并不知道（它只发广播）。
     *
     *  代价是比「按下那刻就振」晚一趟广播（宿主常驻时几毫秒，感知不到；宿主刚被拉起时要等
     * 那 300ms 的补发，但那时**动作**也是 300ms 才发生 —— 振与动作仍然同时）。
     *
     * 「振动反馈」开关关着、连续触发的重复、以及「无操作」（吞键什么都不做）都不振。
     */
    private fun runAction(
        id: KeyId,
        gesture: Gesture,
        keyConfig: KeyConfig,
        repeat: Boolean,
        config: Config,
    ) {
        val action = keyConfig.actionOf(gesture)
        val r =
            runner ?: run {
                XLog.w("ActionRunner 还没建好，忽略 ${id.label} 的 ${gesture.id}")
                return
            }
        // 这条是按键的可见证据（release 里也保留），所以只在第一次执行时打 W；
        val log =
            "${id.label}·${gesture.label}${if (repeat) "（连续触发）" else ""} → ${XLog.redactToken(action.token)}" +
                "（宿主进程执行）"
        if (repeat) XLog.d(log) else XLog.w(log)
        // 链路自检：这一笔是「动作真的执行了」的凭证（与注入侧那笔 lastGestureAt 配对）
        lastActionAt = android.os.SystemClock.elapsedRealtime()
        //  报活广播是**一次性**的（装在启动那一刻发一条），单靠它，「设置生效之后真的按过」这一笔
        //  永远送不到设置页 —— 用户按完侧键去看「关于模块」，那行仍写着「未生效」，把成功报成了故障。
        //  所以每执行一次动作补发一条（连续触发的重复不发：那是同一次按压，一条就够）。
        if (!repeat) appContext?.let { sendAlive(it, config.key(KeyId.PLUS).enabled) }
        if (!repeat && action !is Action.None) haptic(config)
        // 「执行时提示」：判据是全局开关（`GlobalConfig.toastOnRun`），内容是**这次执行的是哪个动作**。
        //  钉在这一处（一次手势只走一遍）而不是 `CustomCommandRunner` 里 —— 那一处只覆盖 `cmd:`
        // 自定义命令，绑的是内置动作（小布识屏 / 截图 / 手电筒 …）时按下去一直没动静
        // （用户报「执行命令时系统提示没效果」）。连续触发的重复不弹：那是同一次按压。
        if (!repeat && config.global.toastOnRun) {
            // 语言档跟配置走：设置页那边建页时设过一次，但那是**另一个进程**（`:ui`）里的实例，
            //  宿主主进程这份一直停在 AUTO（跟系统语言）。配置里选了 English 而系统是中文时，
            //  界面英文、提示中文 —— 这里每次执行前对一次，两边同一个口径。
            //  同一个文件不重读（`reload` 里按文件名早返回），所以这一行在按键路径上是白给的。
            I18n.currentLanguage = AppLanguage.fromCode(config.global.language)
            val notice = actionNoticeText(appContext, action)
            if (notice.isNotEmpty()) {
                XLog.w("执行时提示：$notice")
                FailureNotice.toast(appContext, notice)
            }
        }
        r.run(action, "${id.label}·${gesture.label}")
    }

    /**
     * 动作即将执行前那一下触觉反馈 —— 位置与判据见 [runAction] 的 KDoc。
     *
     * 跑在主线程上，一切异常就地吞掉：振不动不该影响动作执行，更不该让宿主进程崩。
     *
     *  `MissingPermission` 这一条按「谁在执行」判：这段跑在**宿主**（`com.oplus.gesture`）里，
     *  而它自己声明并被授予了 `android.permission.VIBRATE`（真机 `dumpsys package` 核对过）。
     *  模块清单里**不**声明这一条 —— 写上它也管不到宿主那个 UID，只会让人以为模块自己有个
     *  进程在振。
     */
    @SuppressLint("MissingPermission")
    private fun haptic(config: Config) {
        if (!config.global.vibrate) return
        try {
            val ctx = appContext ?: return
            val v = ctx.getSystemService(android.os.Vibrator::class.java) ?: return
            v.vibrate(
                android.os.VibrationEffect.createOneShot(
                    HAPTIC_MS,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        } catch (e: Throwable) {
            XLog.w("触觉反馈失败（忽略）：${e.message}")
        }
    }
}
