package com.lookie.opluskey.host.action.shell

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.shizuku.ShizukuExec
import com.lookie.opluskey.shizuku.ShizukuShellService
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 宿主侧「以 Shizuku 执行」的入口 —— 决定**在哪个进程跑**，其余交给 [ShizukuExec]。
 *
 * ## 两条通路，先本进程、后模块进程
 *
 *  1. **本进程（Sui）**：Sui 是 zygisk 那一路，桥注进了每个 app 进程 —— 宿主进程自己
 *     `Sui.init(包名)` 就有 binder，命令就在宿主进程里跑。这是 ColorOS 上唯一走得通的一条：
 *     它会在 AMS 里拦「后台应用被别的应用 bind 起进程」（`dumpsys activity services` 里
 *     `mAllowStart_*` 全是 DENIED），跨进程那条连模块的进程都拉不起来。
 *  2. **模块进程（真 Shizuku）**：binder 只被推到声明了 `ShizukuProvider` 的那个进程，所以
 *     命令得转给 [ShizukuShellService]，由它跑完把退出码回过来。
 *
 *  判据只有一条：**本进程拿不拿得到 binder**（[ShizukuExec.available]）。拿不到才回落，
 *  不按「装的是 Sui 还是 Shizuku」猜。
 */
internal object ShizukuHost {
    /**
     * 让**本进程**拿到 Shizuku 的 binder；拿不到就是 false（此时才轮到模块进程那一路）。
     *
     *  `Sui.init` 的入参是 Shizuku 记的「服务端上下文」，库自己传的就是包名（`ShizukuProvider`
     *  `onCreate` 里那句），这里跟着传。
     */
    fun ensureBinder(pkg: String): Boolean {
        if (ShizukuExec.available()) {
            logServer()
            return true
        }
        val viaSui = runCatching { Sui.init(pkg) }.getOrDefault(false)
        val ok = ShizukuExec.available()
        if (ok || !viaSui) XLog.w("Shizuku 本进程：Sui.init=$viaSui ping=$ok（$pkg）")
        if (ok) logServer()
        return ok
    }

    /**
     * 打一次「服务端是谁、什么版本」—— 只在这一进程第一次拿到 binder 时打。
     *
     *  `requestPermission` 在这台机器上发出去没有任何反应（框不来、回执也不来），而回执的投递
     *  方式随服务端 API 版本变过（v11 与 v13 不同）：先把这个事实固定下来，再判断是不是
     *  版本对不上，而不是继续在「线程」「UI 权限」上猜。
     *
     *  `isSui` 一并打：binder 到底是 Sui 给的还是 Shizuku 给的，决定后面该用哪一套说法。
     */
    private fun logServer() {
        if (serverLogged) return
        serverLogged = true
        runCatching {
            XLog.w(
                "Shizuku 服务端：isSui=${Sui.isSui()} version=${Shizuku.getVersion()} " +
                    "latest=${Shizuku.getLatestServiceVersion()} serverUid=${Shizuku.getUid()} " +
                    "preV11=${Shizuku.isPreV11()} selinux=${Shizuku.getSELinuxContext()}",
            )
        }.onFailure { XLog.w("Shizuku 服务端：取版本信息失败", it) }
    }

    /** [logServer] 只打一次（每次进页面都会 `ensureBinder`）。 */
    private var serverLogged = false

    /**
     * 本进程现在**有没有**授权 —— 给界面读勾选状态用（`checkSelfPermission` 不发弹窗）。
     *
     *  会先尝试把 binder 弄到手（第一次可能要 `Sui.init`），所以别在主线程调。
     */
    fun readGranted(pkg: String): Boolean = ensureBinder(pkg) && ShizukuExec.granted()

    /**
     * 跑一条命令，返回退出码（`0` = 成功；负数见 [ShizukuExec] / [ShizukuShellClient]）。
     *
     *  **阻塞**：调用方（`ShellRunner` 的池线程）本来就是后台线程。
     */
    fun exec(
        ctx: Context?,
        command: String,
    ): Int {
        if (ctx != null && ensureBinder(ctx.packageName)) {
            if (!ShizukuExec.granted()) {
                XLog.w("Shizuku 本进程：这个 uid 还没被授权")
                return ShizukuShellService.NO_SHIZUKU
            }
            return ShizukuExec.exec(command)
        }
        XLog.w("Shizuku 本进程拿不到 binder，转交模块进程")
        return ctx?.let { ShizukuShellClient.exec(it, command) } ?: ShizukuShellClient.CONNECT_FAIL
    }

    /**
     * 申请一次授权（「以 Shizuku 执行」打开时）。结果在**主线程**回，只回一次。
     *
     * ## 请求从主线程发，等结果不靠回执
     *
     *  请求那一步（`Shizuku.requestPermission`）**必须在主线程**：Sui 那一档要由它自己在
     *  「有前台窗口的进程」里弹框，实测从后台线程发起时框根本不出现。它本身只把请求交给
     *  Sui / Shizuku，不等用户点，所以不会卡住界面。
     *
     *  等结果**不信回执、只信状态**：`addRequestPermissionResultListener` 不一定被回调
     *  （真机：把授权框直接关掉，两边都没有任何回执），而 `checkSelfPermission()` 可靠 ——
     *  所以另起一条线程轮询它，**谁先到都算数**（[done] 保证只回一次）。
     *
     * ## 为什么区分「没授权」与「没有 Shizuku」
     *
     *  把框关掉是最常见的一种操作，它**不是故障**：界面那边对 [GrantOutcome.DENIED] 什么都不用报
     *  （开关本来就停在「关」上），只对 [GrantOutcome.UNAVAILABLE] 报那句失败提示。
     */
    fun requestGrant(
        ctx: Context,
        onResult: (GrantOutcome) -> Unit,
    ) {
        val main = Handler(Looper.getMainLooper())
        val done = AtomicBoolean(false)

        fun settle(outcome: GrantOutcome) {
            if (done.compareAndSet(false, true)) main.post { onResult(outcome) }
        }

        //  拿 binder 是一次 IPC（首次可能还要 `Sui.init`），放在调用线程上 —— 与弹框同一个进程上下文。
        val local = ensureBinder(ctx.packageName)
        if (!local) {
            worker.execute {
                ShizukuShellClient.requestGrant(ctx) { granted ->
                    settle(if (granted) GrantOutcome.GRANTED else GrantOutcome.UNAVAILABLE)
                }
            }
            return
        }
        main.post {
            ShizukuExec.requestGrantBlocking { granted ->
                settle(if (granted) GrantOutcome.GRANTED else GrantOutcome.DENIED)
            }
        }
        worker.execute {
            var waited = 0L
            while (waited < GRANT_POLL_MS && !done.get()) {
                Thread.sleep(GRANT_POLL_STEP_MS)
                waited += GRANT_POLL_STEP_MS
                if (ShizukuExec.granted()) {
                    settle(GrantOutcome.GRANTED)
                    return@execute
                }
            }
            if (!done.get()) {
                XLog.w("Shizuku：等了 ${waited}ms 还没授权（框可能被关掉了）")
                settle(GrantOutcome.DENIED)
            }
        }
    }

    /** 一次授权申请的三种结局（见 [requestGrant]）。 */
    enum class GrantOutcome {
        /** 拿到了。 */
        GRANTED,

        /** 有 Shizuku，但这次没授权 —— 用户拒绝、或把框关掉了。**不是故障**，不报错。 */
        DENIED,

        /** 压根没有可用的 Shizuku（宿主进程与模块进程都拿不到 binder）。 */
        UNAVAILABLE,
    }

    /** 轮询授权状态的总时长 / 步长 —— 用户在授权框上想几秒都还来得及。 */
    private const val GRANT_POLL_MS = 30_000L

    private const val GRANT_POLL_STEP_MS = 500L

    /** 授权 / 执行那条链路的后台线程（同时只会有一条，串行排着）。 */
    private val worker =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "shizuku-host").apply { isDaemon = true }
        }
}
