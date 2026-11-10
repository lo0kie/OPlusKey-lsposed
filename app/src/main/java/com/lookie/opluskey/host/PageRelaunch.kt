package com.lookie.opluskey.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire

/**
 * 「重启服务」的最后一环，跑在**宿主主进程**里（设置页那边发出 [ModuleWire.ACTION_PAGE_RELAUNCH] 后
 * 就杀自己，见 `ui/PageControl`）。
 *
 *  为什么必须由这里来做：杀了自己的进程没法再把自己叫回来，而「从进程外叫」那条路在真机上走不通
 * —— 闹钟挂 `PendingIntent` 拉起 Activity 会被后台启动策略拦掉，判据是
 * `ActivityTaskManager: Background activity launch blocked!`。宿主主进程自己 `startActivity` 则放行
 *（本包拿着 `START_ACTIVITIES_FROM_BACKGROUND`，见 `docs/features/zoom-window/api-extra.md` 附十三之五）。
 *  它本来就活着：[GestureReceiver] 收手势的接收器与设置页那句「在不在」都在它身上，
 * 而「重启服务」的前置条件正是「宿主主进程在跑」（不在时页面直接报失败，不杀自己）。
 *
 *  顺带它也把宿主主进程自己重来一遍 —— 宿主侧的代码同样需要重新加载。
 */
internal object PageRelaunch {
    /**
     * 等页面进程**死透**再叫它。
     *
     *  早于它死就拉起：宿主 Activity 是 `singleInstance`，再 start 只会把**旧进程**里那一个拿到前台，
     * 页面看着回来了，其实一行新代码都没加载。
     */
    private const val START_DELAY_MS = 900L

    /** 页面起来之后本进程再重来一遍（自杀，由系统重新拉起）。 */
    private const val EXIT_DELAY_MS = 500L

    private val handler = Handler(Looper.getMainLooper())

    /**
     * 一次重启只排一次。
     *
     *  刷新出来的新页面若又发一条（用户连点两下），这里挡掉：那条请求要的是「再重启一遍」，
     * 而本进程马上就死了，接了也执行不完 —— 让它死过一遍、由系统拉起的新进程去服务那次请求。
     *
     * 注意：拉起失败时这一位会**复位**（见 [schedule]）：那时本进程不自杀，得让下一次请求还能进来。
     */
    private var scheduled = false

    /** [GestureReceiver] 收到 [ModuleWire.ACTION_PAGE_RELAUNCH] 后调这里（主线程）。 */
    fun schedule(ctx: Context) {
        if (scheduled) {
            XLog.w("页面重启：这一轮已经排过，忽略重复请求")
            return
        }
        scheduled = true
        XLog.w("页面重启：${START_DELAY_MS}ms 后拉起设置页，再过 ${EXIT_DELAY_MS}ms 本进程重来一遍")
        handler.postDelayed({
            // 注意：**拉起失败就不自杀**：本进程死了、页面又没被拉起来，
            //  用户面对的是「设置页没了、也没人再把它叫回来」，只能自己重进 —— 比留着进程糟得多。
            //  复位 `scheduled` 让下一次请求还能被接住。
            if (!startPage(ctx)) {
                XLog.w("页面重启：拉起设置页失败 —— 本进程不自杀（留着服务下一次请求）")
                scheduled = false
                return@postDelayed
            }
            handler.postDelayed({
                //  `startActivity` 没抛只说明请求进了系统，**不说明页面真的起来了**。
                //  本进程一死就没人再把它叫回来，所以自杀之前再确认一次页面进程在跑；
                //  判不出来（拿不到进程表）就跳过自杀 —— 与「拉起失败」同一档处理。
                when (pageProcessAlive(ctx)) {
                    true -> {
                        val pid = android.os.Process.myPid()
                        XLog.w("页面重启：宿主主进程自杀重来 pid=$pid")
                        android.os.Process.killProcess(pid)
                    }

                    false -> {
                        XLog.w("页面重启：页面进程没起来 —— 本进程不自杀（留着服务下一次请求）")
                        scheduled = false
                    }

                    null -> {
                        XLog.w("页面重启：拿不到页面进程的判据 —— 跳过自杀（避免页面没了又没人叫回来）")
                        scheduled = false
                    }
                }
            }, EXIT_DELAY_MS)
        }, START_DELAY_MS)
    }

    /**
     * 设置页进程还在不在 —— `null` = 判断不了（拿不到 `ActivityManager` 或进程表）。
     *
     *  `runningAppProcesses` 要 `REAL_GET_TASKS`；宿主持有它（权限清单见 `AppCloneCatalog` 类注释）。
     *  读不到时**不猜**：这一枪决定本进程要不要自杀，猜错就是「页面没了、也没人再把它叫回来」。
     */
    private fun pageProcessAlive(ctx: Context): Boolean? {
        return try {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java) ?: return null
            val procs = am.runningAppProcesses ?: return null
            procs.any { it.processName == HostApp.PAGE_PROCESS }
        } catch (e: Throwable) {
            XLog.w("页面重启：读进程表失败（按「判断不了」处理）：${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** @return 拉起请求有没有被系统受理（**不代表页面已经起来**，见 [schedule] 里的二次确认）。 */
    private fun startPage(ctx: Context): Boolean =
        try {
            ctx.startActivity(
                Intent()
                    .setComponent(
                        ComponentName(HostApp.PACKAGE, Framework.HOST_SETTINGS_ACTIVITY),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            XLog.w("页面重启：已请求拉起设置页（${Framework.HOST_SETTINGS_ACTIVITY}）")
            true
        } catch (e: Throwable) {
            XLog.w("页面重启：拉起设置页失败（${e.javaClass.simpleName}: ${e.message}）")
            false
        }
}
