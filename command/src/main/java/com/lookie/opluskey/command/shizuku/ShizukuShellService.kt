package com.lookie.opluskey.command.shizuku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.lookie.opluskey.core.log.XLog
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `shell:` 的 **跨进程执行通道（模块进程侧）**。
 *
 * ## 它解决什么
 *
 *  Shizuku 的授权按 **uid** 记：模块与宿主 `com.oplus.gesture` 是两个 uid。真 Shizuku 那一路
 *  的 binder 只会被推到**声明了 `ShizukuProvider` 的那个进程**（也就是本模块自己的进程），
 *  所以命令得转到这里执行，再把退出码回给宿主。
 *
 * ## 它什么时候用得上
 *
 *  只在「宿主进程拿不到 binder」时才走到这里 —— Sui（zygisk）那一档宿主进程自己就能
 *  `Sui.init`，直接在本进程跑（见 `ShizukuHost`）。两档共用 [ShizukuExec] 那同一份实现。
 *
 *  注意这条路有个前提：**本模块的进程得在跑**。ColorOS 的 OplusAppStartupManager 会把
 *  「后台应用被别的应用 bind 起进程」当成启动拦掉（系统日志 `prevent start … Type bs`，
 *  `dumpsys` 里 `mAllowStart_*` 全是 DENIED）—— 那时 bind 直接返回 false。进程没在跑时由
 *  **本应用自己立成常驻前台服务**（[onStartCommand]：`EntryActivity` 与开机广播各起一次）。
 *  进程**活着**时 bind 只是建连接，不受那条限制。
 *
 *  还有一层时序：进程刚起来那一下 `pingBinder()` 还是 false（真 Shizuku 的 binder 由服务端
 *  异步推给本进程的 `ShizukuProvider`），所以三个入口都先 [awaitBinder] 再判。
 *
 * ## 协议
 *
 *  · 执行请求：[MSG_EXEC]，`data` 里 `KEY_COMMAND` = 要跑的命令串；回复 [MSG_RESULT]，
 *    `data` 里 `KEY_CODE` = 退出码，`KEY_NO_SHIZUKU` = true 表示「这个进程没有可用的 Shizuku」。
 *  · 授权请求：[MSG_REQUEST_GRANT]；回复 [MSG_GRANT_RESULT]，`KEY_GRANTED` / `KEY_NO_SHIZUKU`。
 *  · 授权查询：[MSG_QUERY_GRANT]；回复 [MSG_GRANT_STATE]，`KEY_GRANTED` / `KEY_NO_SHIZUKU`
 *    —— 只读状态、**不弹框**，给宿主在申请授权前做一次预检（见 [onQueryGrant]）。
 *  · 退场：[MSG_RETIRE]，**没有回复**（回完这一条进程就没了）—— 宿主发现这条通道问不到
 *    实时状态时用它把这个旧进程换掉（见 [onRetire]）。
 *
 *  两条回复都回给请求里的 `replyTo` —— 服务不持有任何调用方对象。
 *
 * ## 谁能调
 *
 *  只认**自己**与**宿主** 两个 uid（见 [allowedCallers]）：`exported=true` 是为了让宿主
 *  bind 得上，但一条 `sh -c` 的入口不能对全机开放 —— 任何应用能调它，就等于任何应用都能借
 *  Shizuku 的 adb 权限跑命令。
 */
class ShizukuShellService : Service() {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "shizuku-shell") }

    /**
     * 宿主连上了 —— 什么都不用做，通知照旧挂着（这条服务就是「让本进程一直能被 bind」）。
     */
    override fun onCreate() {
        super.onCreate()
        watchBinder()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    /**
     * 被起出来时只做一件事：**转前台，然后一直活着**。
     *
     * ## 为什么非得常驻
     *
     *  真 Shizuku 的 binder 只推到本进程，而宿主那条 `bind` 的前提是本进程在跑。ColorOS 的启动管理
     *  不放任何调用方拉起它（连 system_server 都被拒，实测与反编译见 `docs/device/lsposed.md`
     *  四十二），所以「别让它死」是唯一可行的方向 —— 进程一旦活着，宿主 bind 只是建连接。
     *
     * ## 谁起它
     *
     *  两个地方，都是本应用自己的身份（不撞那道闸）：`EntryActivity`（用户打开模块那一下）与
     *  `BootReceiver`（开机 / 换包，系统广播；在放行的 ROM 上才生效）。
     *
     * ## 为什么必须转前台
     *
     *  走的是 `startForegroundService`：只有那一档能过 AOSP 的「后台不许起服务」，而它要求 5 秒内
     *  `startForeground`，否则进程直接被判 `RemoteServiceException` 崩掉。所以顺序是「先转前台」。
     *  通知压到最低档（`IMPORTANCE_MIN`、不响不亮不角标），它既是对用户的交代，也是进程不被回收的
     *  凭证。
     */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        promoteForeground(intent)
        XLog.w("Shizuku 通道：后台通道已常驻（宿主可以直接 bind，重启前一直有效）")
        //  `START_REDELIVER_INTENT`：进程被杀之后系统带同一个 intent 再来一次，仍旧是常驻
        return START_REDELIVER_INTENT
    }

    /**
     * 转前台（见 [onStartCommand]）。
     *
     *  整段兜 `Throwable`：通知渠道那套 API 的边角情况不该把一次启动变成崩溃。
     */
    private fun promoteForeground(intent: Intent?) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appLabel(),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { setShowBadge(false) },
            )
            val builder =
                Notification
                    .Builder(this, CHANNEL_ID)
                    //  图标与文案的**资源 id 由调用方带进来**（`EntryActivity` / `BootReceiver` 在
                    //  root 包，那边才该认识 `R`）：本服务所在这一档只认识 `core`，直接引 `R` 会
                    //  造出 `root → host → shizuku → root` 的包级环（`tools/audit-coupling.py` 会拦）。
                    //  没带就退到系统给的那几样 —— 通知照样出得来，只是难看一点。
                    .setSmallIcon(intent?.getIntExtra(EXTRA_ICON, 0)?.takeIf { it != 0 } ?: applicationInfo.icon)
                    .setContentTitle(
                        intent?.getIntExtra(EXTRA_TITLE, 0)?.takeIf { it != 0 }?.let(::getString)
                            ?: appLabel(),
                    ).setOngoing(true)
            intent?.getIntExtra(EXTRA_TEXT, 0)?.takeIf { it != 0 }?.let {
                builder.setContentText(getString(it))
            }
            startForeground(NOTIFY_ID, builder.build())
        } catch (e: Throwable) {
            XLog.w("Shizuku 通道：转前台失败（${e.javaClass.simpleName}: ${e.message}）", e)
        }
    }

    /** 应用名 —— 通知渠道与标题的兜底（不引 `R`，见 [promoteForeground]）。 */
    private fun appLabel(): String = applicationInfo.loadLabel(packageManager).toString()

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private val messenger =
        Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_EXEC -> onExec(msg)
                        MSG_REQUEST_GRANT -> onRequestGrant(msg)
                        MSG_QUERY_GRANT -> onQueryGrant(msg)
                        MSG_RETIRE -> onRetire()
                        else -> super.handleMessage(msg)
                    }
                }
            },
        )

    private fun onExec(msg: Message) {
        val replyTo = msg.replyTo
        if (replyTo == null) {
            XLog.w("Shizuku 通道：请求没带回复信使，丢弃")
            return
        }
        if (!allowedCallers().contains(incomingUid())) {
            XLog.w("Shizuku 通道：拒绝来自 uid=${incomingUid()} 的请求（只认自己与宿主）")
            return
        }
        val command = msg.data?.getString(KEY_COMMAND).orEmpty()
        if (command.isBlank()) {
            reply(replyTo, code = NO_SHIZUKU, noShizuku = false)
            return
        }
        worker.execute {
            if (!awaitBinder() || !ShizukuExec.granted()) {
                XLog.w("Shizuku 通道：本进程不可用（ping=${ShizukuExec.available()} granted=${ShizukuExec.granted()}）")
                reply(replyTo, code = NO_SHIZUKU, noShizuku = true)
                return@execute
            }
            //  标准输出一并回给宿主（「显示执行返回结果」那一档要它）—— 跨进程只有这一次回执，
            //  所以整段攒起来一次带走，不逐行发消息。
            val output = StringBuilder()
            val code = ShizukuExec.exec(command) { line -> output.appendLine(line) }
            reply(replyTo, code = code, noShizuku = false, output = output.toString())
        }
    }

    private fun onRequestGrant(msg: Message) {
        val replyTo = msg.replyTo
        if (replyTo == null) {
            XLog.w("Shizuku 通道：授权请求没带回复信使，丢弃")
            return
        }
        if (!allowedCallers().contains(incomingUid())) {
            XLog.w("Shizuku 通道：拒绝来自 uid=${incomingUid()} 的授权请求（只认自己与宿主）")
            return
        }
        //  **整段都要在 worker 上**：等 binder 会 sleep，申请本身又是一次 binder 调用 + 等用户点
        //  那一页。原先那句 `awaitBinder()` 写在主线程（信使的 Handler 就在主线程）—— Shizuku
        //  被停掉时它一卡就是几秒，服务连 `onStartCommand` 都排不进来，宿主那边只能干等到超时
        //  （真机：提示条一直停在「正在检查授权…」，一分钟都没动静）。
        worker.execute {
            if (!awaitBinder()) {
                XLog.w("Shizuku 通道：授权请求时没有 binder")
                replyGrant(replyTo, granted = false, noShizuku = true)
                return@execute
            }
            ShizukuExec.requestGrantBlocking { result ->
                //  三态原样回给宿主：拒绝与「没问成」由它分档（前者静默、后者要报一句）
                replyGrant(
                    replyTo,
                    granted = result == ShizukuExec.GrantResult.GRANTED,
                    noShizuku = result == ShizukuExec.GrantResult.FAILED,
                )
            }
        }
    }

    /**
     * 问一句「这个进程现在有没有被授权」—— 宿主申请授权前那次预检用（不弹框）。
     *
     *  为什么要跨进程问：授权按 **uid** 记，而真 Shizuku 那一路的 binder 只推到本模块进程。
     *  宿主那个 uid 在自己的进程里查到的永远是「拒绝」，于是「本进程已授权、宿主以为没有」
     *  这种误判只会出现在这一侧 —— 判据必须问**真正要执行的那个进程**。
     */
    private fun onQueryGrant(msg: Message) {
        val replyTo = msg.replyTo
        if (replyTo == null) {
            XLog.w("Shizuku 通道：查询请求没带回复信使，丢弃")
            return
        }
        if (!allowedCallers().contains(incomingUid())) {
            XLog.w("Shizuku 通道：拒绝来自 uid=${incomingUid()} 的查询（只认自己与宿主）")
            return
        }
        //  **不等 binder**：这一问挡在用户点开关那一刻，等一轮就是「点了没反应」。有没有 binder
        //  由 [binderPresent] 实时记着（收到 / 断开两个监听维护），这里直接拿它作答。
        //  没有 binder 也不去「请服务端补发」—— 那一次调用读的是本进程自己的 binder，必然空转
        //  （见 [binderPresent] 上方那段）：换新进程才是唯一的路，由宿主那边做。
        worker.execute {
            val present = binderPresent
            replyGrant(
                replyTo,
                granted = present && ShizukuExec.granted(),
                noShizuku = !present,
                what = MSG_GRANT_STATE,
            )
        }
    }

    /**
     * 本进程此刻有没有 Shizuku 的 binder —— 由 [binderWatcher] 维护。
     *
     *  初值 `false`：进程刚起来时 binder 还没推过来（真机实测：`ShizukuProvider` 只在
     *  Shizuku 自己拉起进程时才立刻推）。
     *
     *  为什么用一位缓存而不是每次 `pingBinder()`：服务端死掉时那次调用要等 binder 事务超时
     *  （实测十几秒），而这一位是**被事件驱动的**，读它没有任何 IPC。
     */
    @Volatile
    private var binderPresent = false

    /**
     * 收到 / 断开两个监听各记一次，见 [binderPresent]。
     *
     * ## binder 断了要**让进程自毁**，不能只记账（真机实测，2026-11-16）
     *
     *  Shizuku 关掉又开之后，**活着的老进程永远拿不到新 binder**：
     *  · 库里的 `requestBinderForNonProviderProcess` 在真机上等于空操作（`getBinder` 返回 null）；
     *  · 服务端只会给**那之后新起**的进程推 binder。
     *
     *  用户实测的那句「点 LSPosed 管理器那颗 FAB 就能拿到新状态」，本质就是那一下**开了个新进程**
     *  —— 不是 FAB 有魔力。所以这里干脆自己做同样的事：退场 + 让进程退出。之后无论是用户点模块
     *  入口、还是 Shizuku 自己扫描，起来的都是**新进程**，binder 毫秒级就到（实测 4ms）。
     *
     *  代价：这段时间通道真的没了，宿主 bind 会失败、界面照实报「后台通道没在跑」—— 比留一个
     *  永远拿不到 binder 的僵尸进程诚实。
     */
    private val binderWatcher =
        object {
            val received =
                Shizuku.OnBinderReceivedListener {
                    binderPresent = true
                    XLog.w("Shizuku 通道：binder 到了，通道可用")
                }

            val dead =
                Shizuku.OnBinderDeadListener {
                    binderPresent = false
                    //  只退场：不排闹钟、不请服务端补发 —— 这两条在真机上都已证伪
                    //  （宿主的闹钟被 ColorOS 拦、`getBinder` 直接返回 null）。
                    retire("binder 断了（Shizuku 重启过）")
                }
        }

    private fun watchBinder() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderWatcher.received)
            Shizuku.addBinderDeadListener(binderWatcher.dead)
        }.onFailure { XLog.w("Shizuku 通道：注册 binder 监听失败（${it.javaClass.simpleName}）") }
    }

    /**
     * 宿主请本进程退场（[MSG_RETIRE]）—— 它 bind 得上来、却从这条通道上问不到实时状态
     *  （[onQueryGrant] 回「没有 binder」），说明这是个**旧进程**。
     *
     *  换新进程的办法只有「让这个进程消失」：binder 只推给**那之后新起**的进程（见 [binderWatcher]）。
     *  叫醒新进程那一步由宿主做（它拉起模块跳板，那种起法不被 ColorOS 的启动管理拦）——
     *  本进程自己做不到，见 `docs/device/lsposed.md` 四十二。
     */
    private fun onRetire() {
        retire("宿主说这条通道拿不到实时状态")
    }

    /**
     * 退场：收掉服务与通知，随后让进程退出。
     *
     *  [SELF_EXIT_DELAY_MS] 是留给 `stopSelf()` 走完的（通知收走、服务记录摘掉）。这一段同时
     *  保证进程**真的消失**：被 AMS 复用的「同一个进程」拿不到新 binder，那等于什么都没做。
     *
     *  重复触发（binder 断开与宿主请求撞在一起）只算一次 —— 两条线程各杀一遍没有意义。
     */
    private fun retire(why: String) {
        if (retiring) return
        retiring = true
        XLog.w("Shizuku 通道：$why —— 本进程退场，等新进程重新接上")
        stopSelf()
        Thread {
            Thread.sleep(SELF_EXIT_DELAY_MS)
            android.os.Process.killProcess(android.os.Process.myPid())
        }.apply { isDaemon = true }.start()
    }

    /** [retire] 只生效一次。 */
    @Volatile
    private var retiring = false

    /**
     * 等 binder 到位（最多 [BINDER_WAIT_MS]）。
     *
     *  真 Shizuku 的 binder 是服务端**异步**推给本进程的 `ShizukuProvider`：宿主刚把本进程
     *  bind 起来那一刻，`pingBinder()` 还是 false。那时回一句「没有可用的 Shizuku」是把
     *  「还没准备好」说成了「没有」—— 等一小会儿它就来了。
     *
     *  只在服务端那条单线程上等（[worker]），不占主线程。
     */
    private fun awaitBinder(): Boolean =
        //  `sticky` 版在已经有 binder 时**立刻**回调，等价于「现在就可用」；没有就等到这一档的上限。
        waitForBinder(BINDER_WAIT_MS)

    /**
     * 等「binder 到了」这个**事件**，最多 [ms] 毫秒。
     *
     * ## 为什么不轮询 `pingBinder()`
     *
     *  Shizuku 的服务被停掉之后，`pingBinder()` 不是立刻返回 false —— 它要等一次 binder 事务
     *  超时，实测十几秒一次。原先那个 100ms 步长的轮询因此变成「25 次 × 十几秒」，一条授权请求
     *  在模块进程里挂了 5 分钟（真机日志：16:20:37 收到请求、16:21:44 才回，宿主那边早已等满
     *  60 秒超时，提示条就一直停在「正在检查授权…」）。
     *
     *  库本身就给了事件：`addBinderReceivedListenerSticky` —— 已经有 binder 时立刻回调，之后
     *  binder 一到就回调。整段**不发起任何 binder 调用**，所以服务端死掉时它只会安静地等到超时。
     */
    private fun waitForBinder(ms: Long): Boolean {
        val arrived = CountDownLatch(1)
        val listener = Shizuku.OnBinderReceivedListener { arrived.countDown() }
        return try {
            Shizuku.addBinderReceivedListenerSticky(listener)
            arrived.await(ms, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            XLog.w("Shizuku 通道：等 binder 失败（${e.javaClass.simpleName}: ${e.message}）")
            false
        } finally {
            runCatching { Shizuku.removeBinderReceivedListener(listener) }
        }
    }

    private fun reply(
        to: Messenger,
        code: Int,
        noShizuku: Boolean,
        output: String = "",
    ) {
        runCatching {
            val data =
                Bundle().apply {
                    putInt(KEY_CODE, code)
                    putBoolean(KEY_NO_SHIZUKU, noShizuku)
                    putString(KEY_OUTPUT, output)
                }
            to.send(Message.obtain(null, MSG_RESULT).apply { this.data = data })
        }.onFailure { XLog.w("Shizuku 通道：回执发不出去（宿主可能已经走了）", it) }
    }

    /** 授权请求 / 查询的回执 —— 与执行回执分开几条 `what`，调用方各等各的。 */
    private fun replyGrant(
        to: Messenger,
        granted: Boolean,
        noShizuku: Boolean,
        what: Int = MSG_GRANT_RESULT,
    ) {
        runCatching {
            val data =
                Bundle().apply {
                    putBoolean(KEY_GRANTED, granted)
                    putBoolean(KEY_NO_SHIZUKU, noShizuku)
                }
            to.send(Message.obtain(null, what).apply { this.data = data })
        }.onFailure { XLog.w("Shizuku 通道：授权回执发不出去（宿主可能已经走了）", it) }
    }

    private fun incomingUid(): Int = android.os.Binder.getCallingUid()

    /** 允许调用本服务的 uid —— 自己，以及宿主那一族（`com.oplus.gesture` 及其进程）。 */
    private fun allowedCallers(): Set<Int> {
        val out = mutableSetOf(Process.myUid())
        for (pkg in HOST_PACKAGES) {
            runCatching { out += packageManager.getPackageUid(pkg, 0) }
        }
        return out
    }

    companion object {
        /** 这一档回给宿主的「退出码」：这个进程没有可用的 Shizuku / 没授权。 */
        const val NO_SHIZUKU = -1001

        const val MSG_EXEC = 1
        const val MSG_RESULT = 2
        const val MSG_REQUEST_GRANT = 3
        const val MSG_GRANT_RESULT = 4

        /** 问一句「这个进程现在有没有授权」（见 [onQueryGrant]）。 */
        const val MSG_QUERY_GRANT = 5

        /** [MSG_QUERY_GRANT] 的回执 —— 与授权请求的结果分开两条，调用方各等各的。 */
        const val MSG_GRANT_STATE = 6

        /** 请本进程退场（见 [onRetire]）—— 没有回执，回完这条进程就没了。 */
        const val MSG_RETIRE = 7

        /**
         * 等 binder 的上限 / 步长 —— 只覆盖「进程刚起来、服务端还没把 binder 推过来」那一段。
         *
         *  不能太长：它也挡在宿主那次「查授权」的回执上（那边 6 秒上限，见
         *  `ShizukuShellClient.QUERY_TIMEOUT_MS`），等久了就是「点一下没反应」。1.5 秒足够真机
         *  实测里那段延迟（同一个进程拿到 binder 后再查是 0 毫秒）。
         */
        private const val BINDER_WAIT_MS = 2_500L

        /**
         * binder 断开后隔多久让进程退出（见 `binderWatcher.dead`）。
         *
         *  留这一小段是给 `stopSelf()` 走完（通知收走、服务记录摘掉）；进程随后才退出，
         *  否则下一次启动会被 AMS 认成「同一个进程复用」，那就拿不到新 binder 了。
         */
        private const val SELF_EXIT_DELAY_MS = 300L

        /**
         * 调用方带进来的那几个资源 id（见 [promoteForeground]）。
         *
         *  放 extra 而不是在本类里写 `R.xxx`：这一档（`shizuku/`）只认识 `core`，认识 `R` 就等于
         *  依赖 `root` 包，而那与本模块「注入侧 / 宿主侧 / 模块进程侧」的分层是反着的（会成环）。
         */
        const val EXTRA_ICON = "notify_icon"
        const val EXTRA_TITLE = "notify_title"
        const val EXTRA_TEXT = "notify_text"

        /** 那条常驻通知的渠道与通知 id —— 本模块只用这一处，不与界面层的通知撞。 */
        private const val CHANNEL_ID = "shizuku-wake"

        private const val NOTIFY_ID = 0x4F4B
        const val KEY_COMMAND = "cmd"
        const val KEY_CODE = "code"

        /** 这次执行的**标准输出**（整段，可能为空）—— 与 `KEY_CODE` 同一个回执。 */
        const val KEY_OUTPUT = "output"
        const val KEY_GRANTED = "granted"
        const val KEY_NO_SHIZUKU = "no_shizuku"

        /** 宿主那一族包名 —— 只有它和被授权过的自身 uid 能进来。 */
        private val HOST_PACKAGES = listOf("com.oplus.gesture")
    }
}
