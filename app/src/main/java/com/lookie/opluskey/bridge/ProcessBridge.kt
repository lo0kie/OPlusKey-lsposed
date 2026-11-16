package com.lookie.opluskey.bridge

import android.content.Context
import android.content.Intent
import android.os.Process
import com.lookie.opluskey.core.ModuleInfo
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import java.io.File

/**
 * 「结束应用」（`proc:stop:包名`）的执行侧 —— 宿主发一条广播，`system_server` 用自己的身份去停。
 *
 * ## 为什么不在宿主里停
 *
 *  宿主跑在 `platform_app` 域：别的应用的进程它既看不见也停不掉。`am force-stop` 这条命令对系统
 *  应用还不成立 —— 真机实测 `am force-stop com.android.systemui` **退出码 0**、那一行进程一动
 *  不动（见 `docs/runtime/app-control.md`）。
 *
 *  而模块本来就注入在 `system_server` 里 —— 那个身份（uid 1000）才停得动**任意**应用，系统应用与
 *  常驻应用一样，这正是这条通道比 `am force-stop`（含 Shizuku 那条 uid 2000 的路）强的地方。
 *
 * ## 为什么不需要 root
 *
 *  停应用要的是「能杀别人 uid 的进程」这一件事，`system_server` 本来就有；`su` 只是绕路去借同一个
 *  能力，还要多一份授权。`forceStopPackage` 是 AMS 自己的入口，`am` 走的也是它 —— 区别只在调用方
 *  是谁：`am` 是 uid 2000，`system_server` 是 uid 1000，后者才被当作「系统自己发起」。
 *
 * ## 判据是「这几个 pid 还在不在」
 *
 *  常驻应用被真杀掉之后系统会**立刻**把它重新拉起来（新 pid），所以「这个包没有进程了」不能当成功
 *  判据；这里看的是「我停的那一刻它有没有退出」。判据只落日志 —— 广播是单向的，宿主那一侧收不到
 *  结果，也不会假装收到（见 `ProcessAction`）。
 */
internal object ProcessBridge {
    /**
     * 这几样**停不得** —— 判据只有一条：停了等于把模块或系统自己弄死。
     *
     *  宿主与注入侧调**同一个函数**（[isForbidden]）：宿主那一侧是为了按一下就给出提示（不必等一条
     *  日志），注入侧是权威一道闸（广播的载荷不能当可信输入）。
     */
    private val FORBIDDEN =
        setOf(
            //  `system_server` 自己 —— 这一次执行就在它里面跑，停它等于整机重启
            SYSTEM_PACKAGE,
            //  宿主与设置页进程：模块的界面都在那儿（后一个是进程名 —— 停宿主时它一并没了，
            //  列在这里只是让「什么不该停」一眼看得全）
            HostApp.PACKAGE,
            HostApp.PAGE_PROCESS,
            //  模块自己（链路自检、日志页、Shizuku 执行服务都在这儿）
            ModuleInfo.PACKAGE,
        )

    /** 这个包是不是「停不得」的那几个之一。 */
    fun isForbidden(pkg: String): Boolean = pkg in FORBIDDEN

    /**
     * 宿主进程调：请 `system_server` 停掉 [pkg]。
     *
     * @return 广播**发出去了**没有（`sendBroadcast` 抛 = false）。它**不表示有人接**：注入侧没装 /
     *   是老版本不认识这一条时这里照样是 true —— 那种情况只能靠注入侧的日志认。
     */
    fun send(
        context: Context,
        pkg: String,
    ): Boolean =
        try {
            context.sendBroadcast(
                Intent(StatusBarBridge.ACTION_STATUSBAR)
                    .setPackage(HostApp.PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(StatusBarBridge.EXTRA_CMD, StatusBarBridge.CMD_STOP_PACKAGE)
                    .putExtra(StatusBarBridge.EXTRA_STOP_PACKAGE, pkg),
            )
            true
        } catch (e: Throwable) {
            XLog.w("结束应用：广播发不出去（$pkg）", e)
            false
        }

    /**
     * 注入侧（`system_server`）调：从广播里取包名，再核一次该不该停。
     *
     *  跑在 `KeyHooks` 的 `worker` 线程上（不是 AMS 的广播线程），所以下面那次停人可以就地阻塞。
     *
     * @param loader `system_server` 的 ClassLoader（取 AMS 用）；null 时只记日志
     */
    fun handle(
        intent: Intent,
        loader: ClassLoader?,
    ) {
        val pkg =
            try {
                intent.getStringExtra(StatusBarBridge.EXTRA_STOP_PACKAGE).orEmpty()
            } catch (e: Throwable) {
                XLog.w("结束应用：读包名失败：${e.message}")
                ""
            }
        if (pkg.isEmpty()) {
            XLog.w("结束应用：广播没带包名，跳过")
            return
        }
        if (isForbidden(pkg)) {
            //  说清是「拒绝」而不是「没停成」：这一行不是故障，是挑错了应用
            XLog.w("结束应用：$pkg 是本模块 / 系统自身，拒绝执行")
            return
        }
        if (loader == null) {
            XLog.w("结束应用：还没有 system_server 的 ClassLoader，$pkg 没停")
            return
        }
        stop(loader, pkg)
    }

    /**
     * 真去停：调 AMS 的 `forceStopPackage(包名, userId)`。
     *
     * ## 为什么先清调用方身份
     *
     *  这条链路是从宿主的广播进来的，`Binder.getCallingUid()` 在那条事务里是**宿主的 uid** ——
     *  AMS 那一层按调用方判 `FORCE_STOP_PACKAGES` 与「是不是系统自己发起」（后者决定它肯不肯停
     *  系统应用）。换成本进程身份之后，两样才是 uid 1000 该有的样子（与 `StatusBarBridge` 同一口径，
     *  见 [asSystem]）。
     *
     * ## 用户只有主用户
     *
     *  `Action.Process` 只存包名、不存 userId（分身那一档没有入口），所以这里固定主用户 ——
     *  与本模块的安装范围一致（宿主也在主用户）。
     *
     * ## 为什么还要自己补一刀
     *
     *  `forceStopPackage` 对**普通应用**就够（真机：微信被杀，`OPM` 记了一条 `FORCE STOP` 的
     *  `handleAppExit`）。但它对**常驻应用**不落地：`com.android.systemui` 的 `pkgFlags` 带
     *  `PERSISTENT`，真机上 AMS 已经打印 `Force stopping com.android.systemui … (system)`、进程却
     *  一动没动 —— 判据是「那几个 pid 还在不在」，所以这一档必须再补一刀。
     *
     *  补的这一刀就是 AMS 自己收进程用的那个 syscall（[Process.killProcess] → `kill(pid, SIGKILL)`）：
     *  `system_server` 的 `CapEff` 带 `CAP_KILL`，杀别的 uid 与它平时干的事没有区别。
     *  **先按名字复核再杀**：`/proc/<pid>/cmdline` 现在是本包（或本包子进程）才动手 —— pid 会被
     *  回收再分配给别的进程，拿着旧清单直接杀就是杀无关进程。
     */
    private fun stop(
        loader: ClassLoader,
        pkg: String,
    ) {
        val ams =
            ServiceCalls.service(loader, SERVICE_ACTIVITY, IFACE_ACTIVITY_MANAGER) ?: run {
                XLog.w("结束应用：取不到 activity 服务，$pkg 没停")
                return
            }
        //  两代签名都试（新版本在前）：这一层只按「名字 + 参数个数」匹配，命不中就退下一个候选
        val called = asSystem { ServiceCalls.call(ams, METHOD_FORCE_STOP, listOf(pkg, USER_SYSTEM), listOf(pkg)) }
        if (called == null) {
            XLog.w("结束应用：$pkg 没停成 —— activity 服务拒了（上一行是原因）")
            return
        }
        val left = pidsOf(pkg)
        if (left.isEmpty()) {
            XLog.w("结束应用：$pkg 已停（系统那一刀就够了）")
            return
        }
        val killed = killAll(left)
        val still = pidsOf(pkg)
        if (still.isEmpty()) {
            XLog.w("结束应用：$pkg 已停（系统下发了 ${left.size} 个，补刀杀掉 $killed 个）")
        } else {
            XLog.w(
                "结束应用：$pkg 还有 ${still.size} 个进程（$still）—— 补刀杀了 $killed 个，" +
                    "剩下的要么没杀掉（上一行有原因），要么已被系统重新拉起",
            )
        }
    }

    /**
     * 逐个 `SIGKILL`；跳过本进程（杀它等于把 `system_server` 弄死）。
     *
     * @return 真正发出去了几个
     */
    private fun killAll(pids: List<Int>): Int =
        pids.count { pid ->
            if (pid == Process.myPid()) {
                XLog.w("结束应用：跳过本进程 pid=$pid（那是 system_server 自己）")
                false
            } else {
                runCatching { Process.killProcess(pid) }
                    .onFailure { XLog.w("结束应用：kill pid=$pid 失败：${it.javaClass.simpleName}: ${it.message}") }
                    .isSuccess
            }
        }

    /** [pkg] 现在还有哪几个 pid（进程名等于包名，或以 `包名:` 开头 —— 子进程一并算）。 */
    private fun pidsOf(pkg: String): List<Int> {
        val entries = runCatching { File(PROC).list() }.getOrNull() ?: return emptyList()
        return entries.mapNotNull { it.toIntOrNull() }.filter { belongsTo(it, pkg) }
    }

    /**
     * 这个 pid 是不是 [pkg] 的进程。
     *
     *  读 `/proc/<pid>/cmdline` 而不是 `ps`：这里没有 shell 可借，而 `system_server` 读 `/proc`
     *  是它自己就在做的事。读不到（进程已退出 / 内核刚收尸）一律算「不是」—— 那正是「已经停干净」
     *  这一支要的结论。
     */
    private fun belongsTo(
        pid: Int,
        pkg: String,
    ): Boolean {
        val name =
            runCatching { File("$PROC/$pid/cmdline").readText().trim('\u0000') }
                .getOrNull() ?: return false
        return name == pkg || name.startsWith("$pkg:")
    }

    /** AMS 在 `ServiceManager` 里登记的名字。 */
    private const val SERVICE_ACTIVITY = "activity"

    /** AMS 那一层 AIDL 接口的全名 —— 本地服务对象要拿它过一遍 `asInterface`。 */
    private const val IFACE_ACTIVITY_MANAGER = "android.app.IActivityManager"

    /** 停一个应用的那个方法（`am force-stop` 走的也是它）。 */
    private const val METHOD_FORCE_STOP = "forceStopPackage"

    /** 系统自己的包名（uid 1000 名下就是它）。 */
    private const val SYSTEM_PACKAGE = "android"

    /** 主用户。 */
    private const val USER_SYSTEM = 0

    /** 进程表的根。 */
    private const val PROC = "/proc"
}
