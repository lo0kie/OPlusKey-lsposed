package com.lookie.opluskey.host.action.shell

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
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.shizuku.ShizukuShellService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * **宿主侧**的 Shizuku 执行客户端 —— 把一条 `shell:` 转给模块进程跑，回退出码。
 *
 * ## 为什么不在宿主进程直接调 Shizuku
 *
 *  Shizuku 的授权按 **uid**：宿主是 10277、模块是 10537。宿主进程里 `checkSelfPermission`
 *  永远是拒绝，也没有 Activity 能发起授权 —— 所以只能 bind 模块进程那个
 *  [ShizukuShellService]（见它的类注释）。
 *
 * ## 时序
 *
 *  bind → 拿到 binder → `send(MSG_EXEC)` → 等回执（[TIMEOUT_SEC] 秒）→ unbind。
 *  整段**同步**：调用方（`ShellRunner` 的池线程）本来就是后台线程，不必再包一层回调。
 *  超时 / 连不上 / 回执带「没有可用 Shizuku」，三种情况都换算成 [result] 里的失败码，
 *  由 `ShellRunner` 报成对应那一档失败原因。
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
     * 请模块进程**申请一次 Shizuku 授权**（「以 Shizuku 执行」那个开关打开时）。
     *
     *  与 [exec] 一样是跨进程的：宿主 uid 发起的请求只会给宿主授权，而真正要执行的是模块进程
     *  （见 [ShizukuShellService] 的类注释）。结果经 [onResult] 回（**主线程**）：
     *  `true` = 已拿到授权。
     *
     *  这一路**必须有超时**：用户可能在 Shizuku 那一页停着不动，而连接是本进程持有的 ——
     *  一直不还就会漏一个 `ServiceConnection`。等不满 [GRANT_TIMEOUT_MS] 按「没拿到」回。
     */
    fun requestGrant(
        ctx: Context,
        onResult: (Boolean) -> Unit,
    ) {
        //  一次性收尾：回执、超时、连不上三条路只允许走一条，重复回会让界面弹两条提示。
        val done = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        lateinit var connection: ServiceConnection

        /** 收尾：解绑 + 回结果（只生效一次）。回主线程 —— 界面那一侧要写 Compose 状态。 */
        fun settle(granted: Boolean) {
            if (!done.compareAndSet(false, true)) return
            runCatching { ctx.unbindService(connection) }
            main.post { onResult(granted) }
        }

        val reply =
            Messenger(
                object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        if (msg.what != ShizukuShellService.MSG_GRANT_RESULT) return
                        if (msg.data?.getBoolean(ShizukuShellService.KEY_NO_SHIZUKU) == true) {
                            XLog.w("Shizuku 通道：授权请求时没有可用的 Shizuku")
                        }
                        settle(msg.data?.getBoolean(ShizukuShellService.KEY_GRANTED) == true)
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
                        settle(false)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    XLog.w("Shizuku 通道：授权请求期间连接断开")
                }
            }
        if (!bind(ctx, connection, "授权请求")) {
            done.set(true)
            onResult(false)
            return
        }
        main.postDelayed({ settle(false) }, GRANT_TIMEOUT_MS)
    }

    /**
     * 绑定模块进程那个 Service；失败时把**判据一次打全**（抛的异常、调用方包名、目标 uid）。
     *
     *  这条链路在别的进程里，界面上只有一句「没有可用的 Shizuku」——不分清「组件找不到 /
     *  看不到包 / 抛了异常」，就只能在两个猜测之间来回：真机踩过一次组件名写错（AMS 报 not found），
     *  那次之后这里就按这句日志定位，不再把失败糊成一句。
     */
    private fun bind(
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

    /** 等用户在那张授权框上做决定的上限 —— 超了就当没拿到（见 [requestGrant]）。 */
    private const val GRANT_TIMEOUT_MS = 60_000L

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
