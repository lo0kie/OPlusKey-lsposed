package com.lookie.opluskey.host

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.ProcClock
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.config.VariableStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.seal.Seal
import com.lookie.opluskey.core.seal.Verdict
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.core.wire.LogWire
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.host.action.ActionRunner

/**
 * **宿主进程的入口编排**：取 Application Context → 签名闸 → 建执行器 → 保活 → 收配置 / 报活 →
 * 注册广播（手势 / 查询 / 重进页面 / 导出日志 / 打开设置页）。
 *
 *  「一条手势进来之后发生的事」（过期丢弃、去重、读配置、判两级开关、振动、提示、交给执行器）
 *  全在 [GestureExec] 里 —— 两者原先同在这个单例里，入口编排与业务执行混住，读的人分不清
 *  哪一段是「进程起没起来」、哪一段是「这次按键照不照做」。
 */
internal object GestureReceiver {
    /** 已注册的接收器 —— 永不移除（注册后随宿主主进程一直活着，`install` 里用非空判断挡住重复注册）。 */
    private var receiver: BroadcastReceiver? = null

    /** [ModuleWire.ACTION_OPEN_SETTINGS] 那条广播的接收器（EXPORTED，见 [registerOpener]）。 */
    private var opener: BroadcastReceiver? = null

    /** 主线程 Handler：注册、重试、页面拉起这些「轻且要主线程」的事走它。 */
    private val handler: Handler = Handler(Looper.getMainLooper())

    /**
     * **动作执行**用的那条线程 —— 与主线程分开。
     *
     *  一条手势进来之后要做：读配置（`ConfigStore.read`，stat + 解混淆 + JSON）、
     *  读变量表、`ConditionProbe` 的多次 binder、`ActionNotice` 的 PM 查询、`Settings` 读写。
     *  这些全放在广播回调里（主线程）时，慢磁盘 / 慢 binder 就是宿主 ANR，动作还可能被系统丢掉。
     *
     *  **单线程、串行**：手势是有序的（DOWN/UP、连续触发），并发执行会让「同一条命令两次」
     *  与「谁是最后一次」都失去定义。所以这里是一条线程 + 队列，不是线程池。
     *
     *  取 Context / 注册接收器仍留在主线程：那几件事要碰 Looper 建好的东西，
     *  而且只在进程启动时跑一次。
     */
    private val worker: Handler by lazy {
        val thread = android.os.HandlerThread("opluskey-gesture").apply { start() }
        Handler(thread.looper)
    }

    /**
     * 把「这一下要执行什么」丢到 [worker] 上 —— 广播回调只做取 extra 与投递。
     *
     *  已经在 worker 上时直接跑（`handleCombo` 里由子路径回调进来的情形），免得排到自己后面。
     */
    private fun runOnWorker(block: () -> Unit) {
        if (Looper.myLooper() === worker.looper) {
            block()
            return
        }
        worker.post(block)
    }

    /** 取 Application Context 的重试次数与间隔。 */
    private const val CTX_RETRY = 10
    private const val CTX_RETRY_DELAY_MS = 500L

    /**
     * 签名判不出来（[Verdict.UNKNOWN]）时的重试次数与间隔。
     *
     *  读签名本身可能瞬时失败（PackageManager 还没就绪），所以给它几次机会；用完仍判不出来就按
     *  「不是官方包」处理 —— 这一道闸是**fail-closed** 的（见 [tryGetContext] 里那段）。
     */
    private const val SEAL_RETRY = 3
    private const val SEAL_RETRY_DELAY_MS = 1_000L

    /** 宿主自己的侧键服务 —— keepProcessAwake 启动它以抬高进程级别（名字只在 `Framework` 里写一次）。 */
    private val KEY_SERVICE = Framework.HOST_KEY_SERVICE

    fun install(loader: ClassLoader) {
        if (receiver != null) {
            XLog.w("手势接收器已经注册过，跳过")
            return
        }
        // `onPackageReady` 时 Application 未必已经挂上（`currentApplication()` 可能是 null），
        // 所以拿不到就退后重试，而不是直接放弃 —— 否则整条链路静默失效。
        // 首次这条也要包：重试那条（下面）已经包了，首发这次原先露在外面 ——
        //  `currentApplication()` 在安装期抛出来会顺接收器回调外溢。
        XLog.guard("取 Application Context") { tryGetContext(loader, attempt = 0) }
    }

    /**
     * 签名闸：**只有 [Verdict.OFFICIAL] 才放行**。
     *
     *  判据与核心防线同一处（`core/seal/Seal`）；判在这里是因为**此刻才拿得到 Context**
     *  （`KeyModule.onPackageReady` 那一刻 `currentApplication()` 往往还是 null，见那边的说明）。
     *
     *  三档一起判，**判不出来也算不放行**：`UNKNOWN`（读不到本包签名）原先从这条 gate 溜过去
     *  （只拦 `UNOFFICIAL`），一个被改过、又把签名读崩的包于是照常执行动作。读签名可能是瞬时失败，
     *  所以先给 [SEAL_RETRY] 次重试；用完仍判不出来就按「不是本包」处理。
     *
     *  @return `true` = 放行；`false` = 不装（已记账，重试那一档已排好下一次）
     */
    private fun sealPasses(
        ctx: Context,
        loader: ClassLoader,
        attempt: Int,
        sealAttempt: Int,
    ): Boolean =
        when (Seal.verdict(ctx)) {
            Verdict.OFFICIAL -> {
                true
            }

            Verdict.UNOFFICIAL -> {
                XLog.e("本包签名与出厂指纹不符 ⇒ 不装手势接收器（见「关于 → 签名」；换回官方包即恢复）")
                false
            }

            Verdict.UNKNOWN -> {
                if (sealAttempt >= SEAL_RETRY) {
                    XLog.e(
                        "读不到本包签名（已重试 $sealAttempt 次）⇒ 按「不是官方包」处理，不装手势接收器" +
                            "（见「关于 → 签名」）",
                    )
                    false
                } else {
                    XLog.w("读不到本包签名，${SEAL_RETRY_DELAY_MS}ms 后重判（第 ${sealAttempt + 1}/$SEAL_RETRY 次）")
                    handler.postDelayed({
                        XLog.guard("重试签名判定") { tryGetContext(loader, attempt, sealAttempt + 1) }
                    }, SEAL_RETRY_DELAY_MS)
                    false
                }
            }
        }

    private fun tryGetContext(
        loader: ClassLoader,
        attempt: Int,
        sealAttempt: Int = 0,
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
                XLog.guard("重试取 Application Context") { tryGetContext(loader, attempt + 1, sealAttempt) }
            }, CTX_RETRY_DELAY_MS)
            return
        }

        // 签名闸：**只有判成官方包才装接收器**（判不出也算不放行），见 [sealPasses]。
        if (!sealPasses(ctx, loader, attempt, sealAttempt)) return

        // 执行那一段交给 [GestureExec]：它拿到执行器与 Context，执行完再回来叫我们补发一条报活。
        GestureExec.attach(ActionRunner(context = ctx, handler = handler), ctx) { config ->
            sendAlive(ctx, config.global.enabled)
        }

        // 变量：**这台机器重启过**就把带「重启后重置」的那几颗拉回初始值。判据是开机的 wall clock
        //  变了（`VariableStore.applyBootResets`）；同一次开机里反复起进程不会重复重置。
        //  放在执行器装好之后 —— 再早的话这条链路上别的东西都还没就绪。
        XLog.guard("变量开机重置") { VariableStore.applyBootResets() }

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
                    // 设置页要导出日志：把本进程（宿主主进程）那份缓冲回给它。
                    //  与本文件其他分支同一纪律：只做广播与取 extra，不碰配置、不改状态。
                    if (intent?.action == ModuleWire.ACTION_LOG_PULL) {
                        context?.let { LogWire.reply(it, HostApp.PACKAGE, "host") }
                        return
                    }
                    if (intent?.action == ModuleWire.ACTION_PAGE_RELAUNCH) {
                        XLog.w("设置页请求重启服务 —— 由本进程稍后把页面拉回来")
                        context?.let { PageRelaunch.schedule(it) }
                        return
                    }
                    // 注意：取 extra 与派发**同在一个 guard 里**：三句
                    //  `getXxxExtra` 露在 guard 外面，一旦抛出就是宿主主进程的未捕获异常。
                    XLog.guard("处理转来的手势") {
                        val key = intent?.getStringExtra(ModuleWire.EXTRA_KEY)
                        val combo = intent?.getStringExtra(ModuleWire.EXTRA_COMBO)
                        val phase = intent?.getStringExtra(ModuleWire.EXTRA_PHASE)
                        val repeat = intent?.getBooleanExtra(ModuleWire.EXTRA_REPEAT, false) ?: false
                        //  按住手势的反向那一套（只有组合带这一格，缺省 false）
                        val hold =
                            intent?.getIntExtra(ModuleWire.EXTRA_HOLD, ComboKey.HOLD_WINDOW)
                                ?: ComboKey.HOLD_WINDOW
                        val sentAt = intent?.getLongExtra(ModuleWire.EXTRA_SENT_AT, 0L) ?: 0L
                        //  这一下是**模块认领的**（判据侧刚判出手势）：记一笔，宿主那条场景派发
                        //  据此判「要不要吞」（见 [PressTrace]）。
                        //  **记在 `GestureExec` 过完「过期 / 重复」那道闸之后**：转发侧对同一次手势
                        //  会发两条（立刻 + 300ms 补发），在这里无条件记等于把吞键窗口一路推到
                        //  600ms —— 那段时间里别的键的原生派发会被当成「模块认领的」一起吞掉。
                        if (!combo.isNullOrEmpty()) {
                            // 组合键走独立一条：动作在 `ComboKey` 上，没有单键的 `KeyConfig`
                            //  （见 `ModuleWire.EXTRA_COMBO`）。
                            XLog.w("收到 system_server 转来的组合键：combo=$combo phase=$phase repeat=$repeat hold=$hold")
                            //  **执行丢给 worker**：广播回调留在主线程只做「取 extra + 投递」，
                            //  否则一次读盘 + 多次 binder 全压在宿主 UI 线程上（见 [worker] 的注释）。
                            runOnWorker {
                                XLog.guard("执行组合键动作") {
                                    GestureExec.handleCombo(combo, phase, sentAt, repeat, hold)
                                }
                            }
                        } else {
                            XLog.w("收到 system_server 转来的手势：key=$key phase=$phase repeat=$repeat")
                            runOnWorker {
                                XLog.guard("执行侧键动作") {
                                    GestureExec.handle(key, phase, repeat, sentAt)
                                }
                            }
                        }
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
                    // 以及「导出日志」那句「把你这边的缓冲交出来」—— 漏了这一条，
                    //  onReceive 里那个分支永远进不来，导出的日志里永远缺宿主主进程这一段。
                    addAction(ModuleWire.ACTION_LOG_PULL)
                },
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiver = r
            XLog.w("手势接收器已注册（宿主进程）—— 侧键动作执行都在这里")
        } catch (e: Throwable) {
            XLog.w("注册手势接收器失败", e)
        }

        registerOpener(ctx)
    }

    /**
     * 「把侧键设置页拉到前台」那条广播的接收器 —— **必须是 EXPORTED**。
     *
     *  发送方是模块自己的包（`EntryActivity`，另一个 uid），不是设置页那个同 uid 进程，
     *  所以 [ModuleWire.ACTION_HOST_SERVICE_QUERY] 那几条的 `RECEIVER_NOT_EXPORTED` 收不到它。
     *  它只做一件事：起宿主自己的设置页（[Framework.HOST_SETTINGS_ACTIVITY]）；不读配置、不改状态 ——
     *  任何应用发这条，最多把设置页调到前台一次。这是「管理器那颗按钮不依赖 root」的代价。
     *
     *  注意：发起方用的是**有序广播**：起页成败写进 `resultCode`，那边据此决定要不要提示。
     */
    private fun registerOpener(ctx: Context) {
        if (opener != null) return
        val o =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != ModuleWire.ACTION_OPEN_SETTINGS) return
                    val ok = openSettingsPage(context ?: ctx)
                    runCatching { setResultCode(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED) }
                }
            }
        runCatching {
            ctx.registerReceiver(o, IntentFilter(ModuleWire.ACTION_OPEN_SETTINGS), Context.RECEIVER_EXPORTED)
            opener = o
            XLog.w("模块入口接收器已注册（可被模块自己的包唤醒）")
        }.onFailure { XLog.w("注册模块入口接收器失败", it) }
    }

    /**
     * 起**宿主自己的**侧键设置页。
     *
     *  同包起自己的 Activity 不走 `android:permission` 那道跨应用检查，所以这里不需要
     *  `OPLUS_COMPONENT_SAFE` —— 这就是「为什么绕宿主一趟」的全部理由（见
     *  [ModuleWire.ACTION_OPEN_SETTINGS]）。
     */
    private fun openSettingsPage(ctx: Context): Boolean =
        runCatching {
            ctx.startActivity(
                Intent()
                    .setComponent(ComponentName(ctx.packageName, Framework.HOST_SETTINGS_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            XLog.w("模块入口：已把侧键设置页拉到前台")
            true
        }.getOrElse {
            XLog.w("模块入口：打开侧键设置页失败（${it.javaClass.simpleName}: ${it.message}）")
            false
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
     * 顺带带上三个诊断字段，让首页能判「读到的配置是不是真值」「这份文件到底读不读得懂」与
     * 「这个键执行过没有」—— 光说一句「我在」判不出那三种（配置没到 / 读不懂 / 从来没按过）
     * 的真实故障，见 `ui/wire/LinkCheck.kt`。
     */
    private fun reportAlive(ctx: Context) = sendAlive(ctx, ConfigStore.read().global.enabled)

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
        // 「这份配置读得懂吗」—— 必须单独问一句 `load()` 的三态：`enabled` 由调用方给，
        //  而读不懂时它给的是 `Config.DEFAULT` 的 false，与「用户关了主开关」同形（见
        //  `EXTRA_HOST_CONFIG_READABLE` 的说明）。
        //  代价只有一次 `stat`：`load()` 带指纹缓存，同一串调用里刚读过的内容必然命中，
        //  不会重做剥外皮 + JSON 解析 —— 所以与上面那条「别白读一遍」的理由不冲突。
        val readable = ConfigStore.load() !is ConfigStore.Load.Unreadable
        PageWire.send(
            ctx,
            HostApp.PACKAGE,
            Intent(ModuleWire.ACTION_HOST_SERVICE_UP)
                .putExtra(ModuleWire.EXTRA_HOST_ENABLED, enabled)
                .putExtra(ModuleWire.EXTRA_HOST_CONFIG_READABLE, readable)
                .putExtra(ModuleWire.EXTRA_HOST_LAST_ACTION_AT, GestureExec.lastActionAt)
                // 「宿主进程是什么时候起来的」—— 页面拿它与安装包更新时间比，
                //  判「这一侧是不是还跑着旧版」；宿主是普通应用进程，重开就换新代码，不必重启手机。
                .putExtra(ModuleWire.EXTRA_HOST_PROC_START, ProcClock.startWall()),
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
            if (PageWire.send(
                    ctx,
                    HostApp.PACKAGE,
                    ConfigBroadcast.intent(config, {
                        CommandLibrary.longIsSerial(config, it)
                    }, { CommandLibrary.clickThenLongIsSerial(config, it) }),
                )
            ) {
                XLog.w("已广播配置给注入侧（用户已解锁=${isUserUnlocked(ctx)}）")
            }
        }
    }

    /**
     * 只在当前还没解锁时注册 —— 已解锁挂它纯属白挂。
     * 两条 action 互为兜底：`ACTION_USER_UNLOCKED` 是正主，`ACTION_BOOT_COMPLETED`
     * 防「解锁早于本进程启动、错过前一条」；都是受保护系统广播，`RECEIVER_NOT_EXPORTED` 照样收得到。
     *
     * 解锁时再广播一次配置 —— 「启动 / 解锁 / 写盘」三个时机之一（`AGENTS.md` 五之二、9）。
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
            //  查不到就按「已解锁」继续（发布决策不该被这一下卡住），但要留一行：
            //  静默返 true 的话，日志里就只剩「按解锁处理」这个结论、看不出它是猜的
            XLog.w("读解锁状态失败：${e.javaClass.simpleName}，按已解锁继续")
            true
        }
}
