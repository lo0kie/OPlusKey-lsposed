package com.lookie.opluskey.command.action.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.lookie.opluskey.command.shizuku.ShizukuExec
import com.lookie.opluskey.command.shizuku.ShizukuShellService
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * **宿主侧**的 Shizuku 执行客户端 —— 把一条 `shell:` 转给模块进程跑，回退出码。
 *
 * ## 为什么不在宿主进程直接调 Shizuku
 *
 *  Shizuku 的授权按 **uid**：宿主是 10277、模块是 10537。宿主进程里 `checkSelfPermission`
 *  永远是拒绝，也没有 Activity 能发起授权 —— 所以只能 bind 模块进程那个
 *  [ShizukuShellService]（见它的类注释）。
 *
 *  （Sui 那一档不走这里：Sui 把 binder 注进了每个 app 进程，宿主自己就能 `Sui.init`，
 *  见 `ShizukuHost`。所以这条跨进程路只在「真 Shizuku」下用得上。）
 *
 * ## 时序
 *
 *  bind → 拿到 binder → `send(MSG_EXEC)` → 等回执（[TIMEOUT_SEC] 秒）→ unbind。
 *  整段**同步**：调用方（`ShellRunner` 的池线程）本来就是后台线程，不必再包一层回调。
 *  超时 / 连不上 / 回执带「没有可用 Shizuku」，三种情况都换算成 [result] 里的失败码，
 *  由 `ShellRunner` 报成对应那一档失败原因。
 *
 * ## 这条路成立的两个前提（真 Shizuku 特有）
 *
 *  1. **模块进程得在跑**。ColorOS 的启动管理不放任何调用方拉起它（连 system_server 都被拒，
 *     实测与反编译见 `docs/device/lsposed.md` 四十二），所以它是本应用自己立起来的常驻前台服务
 *     （`ShizukuShellService`）。进程活着时 bind 只是建连接，一通到底；没在跑就是 [CONNECT_FAIL]。
 *  2. **进程还得把 binder 拿到手**。真 Shizuku 的 binder 由服务端**异步**推给本模块的
 *     `ShizukuProvider`：进程刚起来那几百毫秒里 `pingBinder()` 仍是 false，这时候发请求只会
 *     得到「这个进程没有可用的 Shizuku」（模块侧会先等一会儿，见 `ShizukuShellService`）。
 */
internal object ShizukuShellClient {
    /** 连上服务 + 执行完的上限。执行侧自己还有 30s 上限，这一档比它宽。 */
    private const val TIMEOUT_SEC = 40L

    /** 见 [ShizukuShellService.NO_SHIZUKU] —— 「这台机器没有可用的 Shizuku」。 */
    const val NO_SHIZUKU: Int = ShizukuShellService.NO_SHIZUKU

    /** 跑一条命令；返回退出码（`0` = 成功），失败时返回上面那几个负数档。 */
    fun exec(
        ctx: Context,
        command: String,
        onLine: ((String) -> Unit)? = null,
    ): Int {
        val code = AtomicInteger(CONNECT_FAIL)
        val latch = CountDownLatch(1)
        val reply =
            Messenger(
                object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        if (msg.what != ShizukuShellService.MSG_RESULT) return
                        val data = msg.data
                        code.set(
                            if (data?.getBoolean(ShizukuShellService.KEY_NO_SHIZUKU) == true) {
                                NO_SHIZUKU
                            } else {
                                data?.getInt(ShizukuShellService.KEY_CODE) ?: CONNECT_FAIL
                            },
                        )
                        //  标准输出跟回执一起回来（模块进程那边跑完才发），整段交给调用方
                        data
                            ?.getString(ShizukuShellService.KEY_OUTPUT)
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { text -> runCatching { onLine?.invoke(text) } }
                        latch.countDown()
                    }
                },
            )
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?,
                ) {
                    runCatching {
                        val payload =
                            Bundle().apply { putString(ShizukuShellService.KEY_COMMAND, command) }
                        Messenger(service).send(
                            Message.obtain(null, ShizukuShellService.MSG_EXEC).apply {
                                data = payload
                                replyTo = reply
                            },
                        )
                    }.onFailure {
                        XLog.w("Shizuku 通道：请求发不出去", it)
                        code.set(CONNECT_FAIL)
                        latch.countDown()
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    //  连接断了不一定说明这条命令没跑完（模块进程可能自己重启），等回执超时兜底
                    XLog.w("Shizuku 通道：连接断开（等回执或超时兜底）")
                }
            }
        if (!bind(ctx, connection, "执行")) return CONNECT_FAIL
        val done =
            runCatching { latch.await(TIMEOUT_SEC, TimeUnit.SECONDS) }.getOrDefault(false)
        runCatching { ctx.unbindService(connection) }
        if (!done) {
            XLog.w("Shizuku 通道：等回执超时（${TIMEOUT_SEC}s）")
            return TIMEOUT
        }
        return code.get()
    }

    /** 执行异常 / 连不上 —— 与「没有可用 Shizuku」分开报，用户才知道该装 Shizuku 还是看日志。 */
    const val CONNECT_FAIL = -1004

    /** 等回执超时。 */
    const val TIMEOUT = -1005

    /**
     * 问模块进程「**现在**有没有被授权」。**阻塞**，调用方得是后台线程。
     *
     *  为什么要跨进程问：授权按 **uid** 记，而真 Shizuku 那一路的 binder 只推到模块进程。
     *  宿主在自己的进程里查到的永远是「拒绝」，于是「模块已授权、宿主以为没有」这种误判
     *  只会出在这一侧 —— 判据必须问**真正要执行的那个进程**（见 [ShizukuShellService.onQueryGrant]）。
     *
     *  消费方是 `ShizukuHost.readGranted`（申请授权前那次预检）：**只读状态、不弹框**，
     *  所以能直接拿来判「已经有了，就别再弹一次框」。
     *
     *  「没有可用 Shizuku」与「有但没有授权」在这里**合流成 `false`**：预检那一侧两种都只是
     *  「接下来得去申请」，分开只在日志里留一行。
     */
    fun probe(ctx: Context): GrantState {
        val state = AtomicReference(GrantState.UNKNOWN)
        val latch = CountDownLatch(1)
        val reply =
            Messenger(
                object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        if (msg.what != ShizukuShellService.MSG_GRANT_STATE) return
                        val data = msg.data
                        state.set(
                            when {
                                //  模块进程那边**没有 binder** ⇒ 这台机器上 Shizuku 根本没在跑。
                                //  这一档要与「有 Shizuku 但没授权」分开：前者再走一遍请求授权只会
                                //  白等一轮（真机口径：就是这半轮让提示条多停了三秒多）。
                                data?.getBoolean(ShizukuShellService.KEY_NO_SHIZUKU) == true -> {
                                    GrantState.NO_SHIZUKU
                                }

                                data?.getBoolean(ShizukuShellService.KEY_GRANTED) == true -> {
                                    GrantState.GRANTED
                                }

                                else -> {
                                    GrantState.NOT_GRANTED
                                }
                            },
                        )
                        latch.countDown()
                    }
                },
            )
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?,
                ) {
                    runCatching {
                        Messenger(service).send(
                            Message.obtain(null, ShizukuShellService.MSG_QUERY_GRANT).apply {
                                replyTo = reply
                            },
                        )
                    }.onFailure {
                        XLog.w("Shizuku 通道：查询请求发不出去", it)
                        latch.countDown()
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    XLog.w("Shizuku 通道：查询期间连接断开")
                }
            }
        //  连不上模块进程（后台通道没常驻）—— 与「没有可用 Shizuku」分开报：该做的事不一样
        if (!bind(ctx, connection, "授权查询")) return GrantState.CHANNEL_DOWN
        val done =
            runCatching { latch.await(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        runCatching { ctx.unbindService(connection) }
        if (!done) {
            XLog.w("Shizuku 通道：查授权等回执超时（${QUERY_TIMEOUT_MS}ms）")
            return GrantState.UNKNOWN
        }
        return state.get()
    }

    /** [probe] 的四种结果（见 [ShizukuHost.requestGrant] 的分岔）。 */
    enum class GrantState {
        /** 已授权 —— 不用弹框。 */
        GRANTED,

        /** 有 Shizuku，但这个 uid 还没授权 —— 该去弹框。 */
        NOT_GRANTED,

        /** 这个进程 / 这台机器没有可用的 Shizuku（模块进程在跑，但它手上没有 binder）。 */
        NO_SHIZUKU,

        /**
         * **连不上模块进程** —— 后台通道没在跑（最常见：Shizuku 重启过，通道已退场，或用户还没
         * 打开过本应用）。这一档要先换一个新进程（[retire] + 宿主拉起跳板，见
         * `ShizukuHost.requestGrant`），换不出来才是「得让用户动一下」。
         */
        CHANNEL_DOWN,

        /** 问不到（等回执超时）—— 与「确实没有」分开，调用方按「试着弹一次」处理。 */
        UNKNOWN,
    }

    /**
     * 请模块进程**退场** —— 它 bind 得上来、却问不到实时状态（[GrantState.NO_SHIZUKU]）时用：
     * 那是个旧进程，binder 只推给**那之后新起**的进程，留着它永远拿不到。
     *
     *  本方法只管「让它消失」；叫醒新进程由调用方去拉起模块跳板（`ShizukuHost`）——
     * 那条路才不被 ColorOS 的启动管理拦（见 `docs/device/lsposed.md` 四十二）。
     *
     *  **阻塞**：bind 与等断开都是 IPC，调用方得是后台线程。
     *
     *  @return 退场请求发出去了没有（`false` = 连不上，那种情况本来就没有进程挡在路上）
     */
    fun retire(ctx: Context): Boolean {
        val sent = AtomicBoolean(false)
        val down = CountDownLatch(1)
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?,
                ) {
                    runCatching {
                        Messenger(service).send(Message.obtain(null, ShizukuShellService.MSG_RETIRE))
                        sent.set(true)
                    }.onFailure { XLog.w("Shizuku 通道：退场请求发不出去", it) }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    down.countDown()
                }
            }
        if (!bind(ctx, connection, "退场")) return false
        runCatching { down.await(RETIRE_WAIT_MS, TimeUnit.MILLISECONDS) }
        runCatching { ctx.unbindService(connection) }
        XLog.w("Shizuku 通道：退场请求 ${if (sent.get()) "已送出" else "没送到"}")
        return sent.get()
    }

    /**
     * 等它退场最多多久（见 [retire]）。
     *
     *  服务端那边是 `stopSelf()` + [ShizukuShellService] 的退场延时，加起来几百毫秒；等到整个
     *  上限还没断，就按「它没走」继续 —— 换进程那一步会在拉起跳板后自己再判一次状态。
     */
    private const val RETIRE_WAIT_MS = 1_500L

    /**
     * 查授权那条回执最多等多久。
     *
     *  比 [TIMEOUT_SEC] 短得多：它只是「bind 起来 + 两次本地判断就回」，不跑命令。而它挡在
     *  用户点开开关那一刻（预检）—— 等太久就等于「点一下没反应」。
     *
     *  给到 6 秒是为了容下「刚被开机广播拉起来」那一段：进程刚起来时模块侧还要等 binder
     *  （1.5 秒上限，见 `ShizukuShellService` 的 `awaitBinder`）。通道常驻着时实测几十毫秒就回。
     */
    private const val QUERY_TIMEOUT_MS = 6_000L

    /**
     * 请模块进程**申请一次 Shizuku 授权**（「以 Shizuku 执行」那个开关打开时）。
     *
     *  与 [exec] 一样是跨进程的：宿主 uid 发起的请求只会给宿主授权，而真正要执行的是模块进程
     *  （见 [ShizukuShellService] 的类注释）。结果经 [onResult] 回（**主线程**），三态与
     *  本进程那条一致（[ShizukuExec.GrantResult]）：拒绝要能静默，没问成 / 超时要能报一句。
     *
     *  这一路**必须有超时**：用户可能在 Shizuku 那一页停着不动，而连接是本进程持有的 ——
     *  一直不还就会漏一个 `ServiceConnection`。等不满 [GRANT_TIMEOUT_MS] 按「没问成」回。
     */
    fun requestGrant(
        ctx: Context,
        onResult: (ShizukuExec.GrantResult) -> Unit,
    ) {
        //  一次性收尾：回执、超时、连不上三条路只允许走一条，重复回会让界面弹两条提示。
        val done = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        lateinit var connection: ServiceConnection

        /** 收尾：解绑 + 回结果（只生效一次）。回主线程 —— 界面那一侧要写 Compose 状态。 */
        fun settle(result: ShizukuExec.GrantResult) {
            if (!done.compareAndSet(false, true)) return
            runCatching { ctx.unbindService(connection) }
            main.post { onResult(result) }
        }

        val reply =
            Messenger(
                object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        if (msg.what != ShizukuShellService.MSG_GRANT_RESULT) return
                        val granted = msg.data?.getBoolean(ShizukuShellService.KEY_GRANTED) == true
                        val noShizuku = msg.data?.getBoolean(ShizukuShellService.KEY_NO_SHIZUKU) == true
                        if (noShizuku) XLog.w("Shizuku 通道：这一次没问成（没有 binder 或发起失败）")
                        settle(
                            when {
                                granted -> ShizukuExec.GrantResult.GRANTED

                                //  模块进程连问都没问成：结论未知，调用方要报一句
                                noShizuku -> ShizukuExec.GrantResult.FAILED

                                else -> ShizukuExec.GrantResult.DENIED
                            },
                        )
                    }
                },
            )
        connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?,
                ) {
                    runCatching {
                        Messenger(service).send(
                            Message.obtain(null, ShizukuShellService.MSG_REQUEST_GRANT).apply {
                                replyTo = reply
                            },
                        )
                    }.onFailure {
                        XLog.w("Shizuku 通道：授权请求发不出去", it)
                        settle(ShizukuExec.GrantResult.FAILED)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    XLog.w("Shizuku 通道：授权请求期间连接断开")
                }
            }
        if (!bind(ctx, connection, "授权请求")) {
            done.set(true)
            onResult(ShizukuExec.GrantResult.FAILED)
            return
        }
        main.postDelayed({ settle(ShizukuExec.GrantResult.FAILED) }, GRANT_TIMEOUT_MS)
    }

    /**
     * 绑定模块进程那个 Service。
     *
     * ## 绑不上就是绑不上，不再试
     *
     *  模块进程没在跑时，ColorOS 的启动管理**不放任何调用方拉起它**（宿主、shell、连 system_server
     *  自己都被拒，实测与反编译见 `docs/device/lsposed.md` 四十二）。曾经在这里试过「请
     *  system_server 代起」与「排闹钟拉起」两条路，真机上都失败，已删掉 —— 留着只会让每次失败
     *  多等三秒、还多弹一次没用的通知。
     *
     *  所以这条通道的前提只有一个：**模块进程本来就在跑**（`EntryActivity` 与开机广播会把
     *  `ShizukuShellService` 起成常驻前台服务）。绑不上就是它没在跑，调用方按 [CONNECT_FAIL] 报，
     *  界面那边有一句对应的说法。
     */
    private fun bind(
        ctx: Context,
        connection: ServiceConnection,
        what: String,
    ): Boolean = tryBind(ctx, connection, what)

    /**
     * 一次 `bindService`：返回 false 时把「为什么」打出来（判据见 [bind]）。
     *
     *  单独的这一次是为了让 [bind] 的重试循环只表达「试到第几次」，把解释留给这里 ——
     *  「抛了异常 / 返回 false / 目标 uid 是多少」这三样每次重试都要看得到。
     */
    private fun tryBind(
        ctx: Context,
        connection: ServiceConnection,
        what: String,
    ): Boolean {
        val intent = Intent().setComponent(ComponentName(ModuleInfo.PACKAGE, SERVICE_CLASS))
        val ok =
            try {
                ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Throwable) {
                XLog.w("Shizuku 通道：$what bind 抛异常（${e.javaClass.simpleName}: ${e.message}）")
                false
            }
        if (!ok) {
            val uid = runCatching { ctx.packageManager.getPackageUid(ModuleInfo.PACKAGE, 0) }.getOrNull()
            XLog.w("Shizuku 通道：$what bind 返回 false（caller=${ctx.packageName} 目标=$intent uid=$uid）")
        }
        return ok
    }

    /**
     * 等模块进程回报授权结果的上限 —— 超了就当没拿到（见 [requestGrant]）。
     *
     *  **用户把框关掉时模块进程那边没有任何回执**（`addRequestPermissionResultListener` 不回调
     *  ——真机如此），所以这条上限就是「关掉框之后界面还要等多久」。15 秒里已经含了模块进程
     *  等 binder 的那一段（`ShizukuShellService` 的 `awaitBinder`），剩下的是用户点一下的时间。
     *  原先给 60 秒，真机上看到的是「框早没了、开关还在转」。
     */
    private const val GRANT_TIMEOUT_MS = 15_000L

    /**
     * 服务类的全名 —— **取自类本身，不拼包名**。
     *
     *  `ModuleInfo.PACKAGE` 是 **applicationId**（`io.github.lo0kie.opluskey`），而代码所在的
     *  包是 `com.lookie.opluskey`：拿前者去接 `.shizuku.ShizukuShellService` 得到的名字在清单里
     *  根本不存在，真机表现是 AMS 一句
     *  `Unable to start service … /.shizuku.ShizukuShellService … not found`，bind 直接返回 false
     *  ——界面上看起来就是「Shizuku 没装」。
     */
    private val SERVICE_CLASS: String = ShizukuShellService::class.java.name
}
