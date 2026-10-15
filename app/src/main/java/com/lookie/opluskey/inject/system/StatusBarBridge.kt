package com.lookie.opluskey.inject.system

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/** 状态栏指令的跨进程桥：「通知中心 / 控制中心」与「最近任务」这几个动作的专用通道。 */
internal object StatusBarBridge {

    /** 状态栏指令的转交广播 —— 宿主发，注入侧接住并在本机执行。 */
    const val ACTION_STATUSBAR = "com.lookie.opluskey.STATUSBAR"

    /** 状态栏指令：展开 / 收起通知中心。 */
    const val CMD_NOTIFICATIONS = "notifications"

    /** 状态栏指令：拉下控制中心（快捷设置那一块）。 */
    const val CMD_CONTROL_CENTER = "control_center"

    /** 状态栏指令：切换最近任务。 */
    const val CMD_RECENTS = "recents"

    /** 状态栏指令：弹系统电源菜单。 */
    const val CMD_POWER_MENU = "power_menu"

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
            XLog.w("statusbar:$cmd 已转给 system_server 执行")
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
                CMD_CONTROL_CENTER -> expandControlCenterLocal(loader)
                CMD_RECENTS -> toggleRecentsLocal(loader)
                CMD_POWER_MENU -> showPowerMenuLocal(loader)
                else -> XLog.w("不认识的状态栏指令：$cmd")
            }
        } catch (e: Throwable) {
            XLog.w("执行状态栏指令失败（忽略）：$cmd", e)
        }
        return true
    }

    /**
     * 展开 / 收起通知中心 —— 调 `IStatusBarService.togglePanel()`。
     */
    private fun toggleNotificationsLocal(loader: ClassLoader?) {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，通知中心跳过")
            return
        }
        try {
            val svc = Reflect.findClass(loader, "android.os.ServiceManager")
                ?.let { Reflect.callStatic(it, "getService", "statusbar") } ?: run {
                XLog.w("拿不到 statusbar 服务对象，通知中心跳过")
                return
            }
            val m = Reflect.methodByNameAndArity(svc.javaClass, "togglePanel", 0) ?: run {
                XLog.w("statusbar 服务（${svc.javaClass.name}）上没有 togglePanel()，通知中心跳过")
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
     * 拉下控制中心（快捷设置那一块）。
     *
     * 注意：**候选表，不是照抄来的结论**：系统侧键动作表里没有 `control_center` 这一行，调用目标是本机试的。
     * 两条候选按「有没有权限闸门」排序：
     *  1. **经 `mBar`** 调 `IStatusBar.animateExpandSettingsPanel(String)` —— 这一层自己不做权限检查
     *     （上面 `togglePanel()` 能通也是同一个原因），真机已定死就是它，别删；
     *  2. `svc.expandSettingsPanel(...)` —— 服务那层，AOSP 在那里有 `enforceExpandStatusBar()`，只当兜底。
     * 两条都落空时把带 `Expand` / `Panel` 的方法名打进日志，照实测补下一轮候选。
     */
    private fun expandControlCenterLocal(loader: ClassLoader?) {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，控制中心跳过")
            return
        }
        try {
            val svc = Reflect.findClass(loader, "android.os.ServiceManager")
                ?.let { Reflect.callStatic(it, "getService", "statusbar") } ?: run {
                XLog.w("拿不到 statusbar 服务对象，控制中心跳过")
                return
            }

            // —— 候选 1：mBar（SystemUI 的 IStatusBar），无权限闸门 ——
            val bar = Reflect.fieldValue(svc, svc.javaClass, "mBar")
            val expanded = bar?.let { b ->
                Reflect.methodByNameAndArity(b.javaClass, "animateExpandSettingsPanel", 1)?.let { m ->
                    m.isAccessible = true
                    m.invoke(b, null)
                    XLog.w("已在本机调 IStatusBar.animateExpandSettingsPanel（控制中心）")
                    true
                }
            }
            if (expanded == true) return
            XLog.w(
                "控制中心候选 1 落空（mBar=${bar?.javaClass?.name ?: "取不到"}）；" +
                    "试候选 2（服务那层可能有 EXPAND_STATUS_BAR 闸门）",
            )

            // —— 候选 2：服务那一层 ——
            val byService = listOf(1, 0).firstNotNullOfOrNull { arity ->
                Reflect.methodByNameAndArity(svc.javaClass, "expandSettingsPanel", arity)
            }
            if (byService != null) {
                byService.isAccessible = true
                if (byService.parameterCount == 1) byService.invoke(svc, null) else byService.invoke(svc)
                XLog.w("已在本机调 IStatusBarService.expandSettingsPanel/${byService.parameterCount}（控制中心）")
                return
            }

            // —— 全落空：把可用的名字打出来，下一轮照它补候选 ——
            XLog.w("控制中心两条候选全落空 —— 下面这份方法名清单就是下一轮的候选来源")
            dumpPanelMethods("statusbar 服务", svc.javaClass)
            bar?.let { dumpPanelMethods("mBar（${it.javaClass.name}）", it.javaClass) }
        } catch (e: java.lang.reflect.InvocationTargetException) {
            XLog.w(
                "控制中心被拒：${e.cause?.javaClass?.name}: ${e.cause?.message}",
            )
        } catch (e: Throwable) {
            XLog.w("拉控制中心失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 把所有带 `Expand` / `Panel` 的方法名 + 参数个数打进日志（上面那条兜底用的）。 */
    private fun dumpPanelMethods(where: String, cls: Class<*>) {
        try {
            val names = cls.methods
                .filter { it.name.contains("Expand", ignoreCase = true) || it.name.contains("Panel", ignoreCase = true) }
                .map { "${it.name}/${it.parameterCount}" }
                .distinct()
            XLog.w("$where 上与面板有关的方法（${names.size} 个）：$names")
        } catch (e: Throwable) {
            XLog.w("列 $where 的方法失败：${e.javaClass.simpleName}")
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

    /**
     * 弹系统电源菜单 —— 在 system_server 里调 `PhoneWindowManager.showGlobalActions()`。
     *
     * 不能走 `WindowManagerService.showGlobalActions()`：它先 `checkCallingPermission`，
     * 而 `ContextImpl.checkCallingPermission` 对同进程调用直接判 DENIED，必抛 SecurityException。
     * PWM 那层没有权限检查，只往自己的 handler 发一条消息，内部再懒建 `LegacyGlobalActions` 弹窗。
     */
    private fun showPowerMenuLocal(loader: ClassLoader?) {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，电源菜单跳过")
            return
        }
        try {
            // 服务注册进去的就是 WindowManagerService 实例本身（不是代理），可以直接读它的字段
            val wms = Reflect.findClass(loader, "android.os.ServiceManager")
                ?.let { Reflect.callStatic(it, "getService", "window") } ?: run {
                XLog.w("拿不到 window 服务对象，电源菜单跳过")
                return
            }
            val policy = Reflect.fieldValue(wms, wms.javaClass, "mPolicy") ?: run {
                XLog.w("window 服务（${wms.javaClass.name}）上没有 mPolicy 字段，电源菜单跳过")
                return
            }
            val m = Reflect.methodByNameAndArity(policy.javaClass, "showGlobalActions", 0) ?: run {
                XLog.w("${policy.javaClass.name} 上没有 showGlobalActions()，电源菜单跳过")
                return
            }
            m.isAccessible = true
            m.invoke(policy)
            XLog.w("已在本机调 PhoneWindowManager.showGlobalActions（电源菜单）")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 服务端抛的异常包在 InvocationTargetException 里 —— 只打外层等于没打
            XLog.w("showGlobalActions 被拒：${e.cause?.javaClass?.name}: ${e.cause?.message}")
        } catch (e: Throwable) {
            XLog.w("弹电源菜单失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
