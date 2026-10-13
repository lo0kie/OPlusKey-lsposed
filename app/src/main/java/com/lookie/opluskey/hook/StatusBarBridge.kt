package com.lookie.opluskey.hook

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog

/** 状态栏指令的跨进程桥：「通知栏」与「最近任务」这两个动作的专用通道。 */
internal object StatusBarBridge {

    /** 状态栏指令的转交广播 —— 宿主发，注入侧接住并在本机执行。 */
    const val ACTION_STATUSBAR = "com.lookie.opluskey.STATUSBAR"

    /** 状态栏指令：展开 / 收起通知栏。 */
    const val CMD_NOTIFICATIONS = "notifications"

    /** 状态栏指令：切换最近任务。 */
    const val CMD_RECENTS = "recents"

    /** 上面那条广播里带指令名的 extra。 */
    const val EXTRA_CMD = "cmd"

    /**
     * 宿主进程调：把一条状态栏指令转给注入侧执行。
     *
     * 必须 `setPackage(宿主包名)`：指定包名的广播不算隐式广播，不会被「隐式广播限制」拦掉，
     * 也只有宿主进程里注册的接收器能收到。
     * 任何失败只记日志 —— 最坏是「这一下没反应」，不能影响宿主进程。
     */
    fun send(context: Context, cmd: String) {
        try {
            context.sendBroadcast(
                Intent(ACTION_STATUSBAR)
                    .setPackage(KeyModule.PAGE_PACKAGE)
                    .putExtra(EXTRA_CMD, cmd),
            )
            XLog.i("statusbar:$cmd 已转给 system_server 执行")
        } catch (e: Throwable) {
            XLog.w("发 statusbar 广播失败：$cmd", e)
        }
    }

    /**
     * 调用点在 AMS 广播入口的 hooker 里（热路径 + system_server），
     * 整体再包一层 catch：漏一个异常出去就是系统崩。
     *
     * @param loader system_server 的 ClassLoader（本机反射用；null 则只记日志不执行）
     */
    fun handle(intent: Intent, loader: ClassLoader?): Boolean {
        if (intent.action != ACTION_STATUSBAR) return false
        val cmd = try {
            intent.getStringExtra(EXTRA_CMD).orEmpty()
        } catch (e: Throwable) {
            XLog.w("读状态栏指令 extra 失败：${e.message}")
            ""
        }
        try {
            when (cmd) {
                CMD_NOTIFICATIONS -> toggleNotificationsLocal(loader)
                CMD_RECENTS -> toggleRecentsLocal(loader)
                else -> XLog.w("不认识的状态栏指令：$cmd")
            }
        } catch (e: Throwable) {
            XLog.w("执行状态栏指令失败（忽略）：$cmd", e)
        }
        return true
    }

    /**
     * 展开 / 收起通知栏 —— 调 `IStatusBarService.togglePanel()`。
     */
    private fun toggleNotificationsLocal(loader: ClassLoader?) {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，通知栏跳过")
            return
        }
        try {
            val svc = Reflect.findClass(loader, "android.os.ServiceManager")
                ?.let { Reflect.callStatic(it, "getService", "statusbar") } ?: run {
                XLog.w("拿不到 statusbar 服务对象，通知栏跳过")
                return
            }
            val m = Reflect.methodByNameAndArity(svc.javaClass, "togglePanel", 0) ?: run {
                XLog.w("statusbar 服务（${svc.javaClass.name}）上没有 togglePanel()，通知栏跳过")
                return
            }
            m.isAccessible = true
            m.invoke(svc)
            XLog.w("已在本机调 IStatusBarService.togglePanel（notifications）")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 服务端抛的异常包在 InvocationTargetException 里 —— 只打外层等于没打
            XLog.w(
                "togglePanel 被拒：${e.cause?.javaClass?.name}: ${e.cause?.message}",
            )
        } catch (e: Throwable) {
            XLog.w("调 togglePanel 失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 开 / 关最近任务 —— 走 `LocalServices.getService(StatusBarManagerInternal).toggleRecentApps()`。
     */
    private fun toggleRecentsLocal(loader: ClassLoader?) {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，最近任务跳过")
            return
        }
        try {
            val internalCls = Reflect.findClass(
                loader, "com.android.server.statusbar.StatusBarManagerInternal",
            ) ?: run {
                XLog.w("找不到 StatusBarManagerInternal，最近任务跳过")
                return
            }
            val localServices = Reflect.findClass(loader, "com.android.server.LocalServices")
                ?: run {
                    XLog.w("找不到 LocalServices，最近任务跳过")
                    return
                }
            val svc = Reflect.callStatic(localServices, "getService", internalCls) ?: run {
                XLog.w("LocalServices 里没有 StatusBarManagerInternal（statusbar 服务没起来？）")
                return
            }
            val m = Reflect.methodByNameAndArity(svc.javaClass, "toggleRecentApps", 0) ?: run {
                XLog.w("${svc.javaClass.name} 上没有 toggleRecentApps()，最近任务跳过")
                return
            }
            val token = android.os.Binder.clearCallingIdentity()
            try {
                Reflect.call(svc, m)
            } finally {
                android.os.Binder.restoreCallingIdentity(token)
            }
            XLog.w("已在本机调 StatusBarManagerInternal.toggleRecentApps（recents）")
        } catch (e: Throwable) {
            XLog.w("切换最近任务失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
