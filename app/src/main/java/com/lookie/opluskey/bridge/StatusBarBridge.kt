package com.lookie.opluskey.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.reflect.Reflect
import java.lang.reflect.InvocationTargetException

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

    /**
     * 状态栏指令：唤醒小布（语音助手）。
     *
     *  与 [CMD_POWER_MENU] 同属「**必须由 system_server 本人干**」那一类：ROM 里长按电源键是
     *  `PhoneWindowManagerExtImpl.startSpeech` 用自己的身份去起语音服务的，宿主不一定有那个
     *  签名级权限（见 `core/model/RomProfile.NAME_VOICE_ASSIST`）。
     */
    const val CMD_VOICE_ASSIST = "voice_assist"

    /** 状态栏指令：切一个系统开关（`setting:<id>`，见 [SettingBridge]）。 */
    const val CMD_SETTING = "setting"

    /**
     * 状态栏指令：起一个**倒计时**（[EXTRA_TIMER_SECONDS] 秒）。
     *
     *  与 [CMD_POWER_MENU] / [CMD_VOICE_ASSIST] 同属「**必须由 system_server 本人干**」那一类，
     *  但理由不同：`HandleApiActivity` 是**导出**的，谁都能起；拦人的是**后台起活动**（BAL）——
     *  宿主（普通应用 uid）不在前台时，`startActivity` 不抛异常、直接把这一次丢掉，
     *  表现是「按了没反应、也看不到流体云」（真机 2026-11-14 就是这样）。
     *  system_server 自己起没有那道闸，时钟收到之后才会照常出胶囊。
     */
    const val CMD_TIMER = "start_timer"

    /** [CMD_TIMER] 里带「倒计时多少秒」。 */
    const val EXTRA_TIMER_SECONDS = "timer_seconds"

    /**
     * 状态栏指令：把这块屏的背光推到某个 raw 值。
     *
     *  **只推不写库** —— 写库仍归宿主（自动亮度下要写的 `screen_auto_brightness_adj` 是
     *  PRIVATE_SETTINGS，注入侧也没有 `ContentResolver`）。这条指令的唯一职责是让
     *  `DisplayPowerController` 走一次**真实亮度变化**：控制中心的亮度条只在那一刻刷新
     *  （见 `docs/device/actions/brightness.md` 38.3）。
     */
    const val CMD_BRIGHTNESS = "brightness"

    /** [CMD_BRIGHTNESS] 里带「推到多少」（raw 尺度，不是 0..1）。 */
    const val EXTRA_BRIGHTNESS = "brightness"

    /** [CMD_SETTING] 里带「哪一条」（`wifi` / `rotation` …）。 */
    const val EXTRA_SETTING_ID = "setting_id"

    /** [CMD_SETTING] 里带「切成什么」（没有这一项 = 由执行侧自己读状态取反）。 */
    const val EXTRA_SETTING_ON = "setting_on"

    /** 上面那条广播里带指令名的 extra。 */
    const val EXTRA_CMD = "cmd"

    /**
     * 宿主进程调：把一条状态栏指令转给注入侧执行。
     *
     * 必须 `setPackage(宿主包名)`：指定包名的广播不算隐式广播，不会被「隐式广播限制」拦掉，
     * 也只有宿主进程里注册的接收器能收到。
     * 任何失败只记日志 —— 最坏是「这一下没反应」，不能影响宿主进程。
     */
    fun send(
        context: Context,
        cmd: String,
    ) {
        try {
            context.sendBroadcast(
                Intent(ACTION_STATUSBAR)
                    .setPackage(HostApp.PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(EXTRA_CMD, cmd),
            )
            //  只陈述「请求发出去了」：注入侧没装 Hook、方法签名对不上时这一下不会发生，
            //  这里看不到执行结果（不伪造能力，见 AGENTS 五、4）。
            XLog.w("statusbar:$cmd 请求已发出（执行结果由注入侧决定）")
        } catch (e: Throwable) {
            XLog.w("发 statusbar 广播失败：$cmd", e)
        }
    }

    /**
     * 宿主进程调：把这块屏的背光推到 [raw]（raw 尺度，本机 `2..4095`）。
     *
     *  与 [send] 同一个通道、同一个接收器，区别只是带一个数值。
     *  写库**不在这里**（见 [CMD_BRIGHTNESS] 的注释），所以这条指令失败最多是「控制中心没跟着刷新」，
     *  亮度本身仍按宿主写下的值生效。
     */
    fun sendBrightness(
        context: Context,
        raw: Int,
    ) {
        try {
            context.sendBroadcast(
                Intent(ACTION_STATUSBAR)
                    .setPackage(HostApp.PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(EXTRA_CMD, CMD_BRIGHTNESS)
                    .putExtra(EXTRA_BRIGHTNESS, raw),
            )
            XLog.w("brightness:$raw 已转给 system_server 推背光")
        } catch (e: Throwable) {
            XLog.w("发 brightness 广播失败：$raw", e)
        }
    }

    /**
     * 宿主进程调：请 system_server 起一个 [seconds] 秒的倒计时。
     *
     *  与 [send] 同一个通道、同一个接收器，区别是带一个数值。为什么不在宿主里直接
     *  `startActivity`：那一下会被**后台起活动**静默丢掉（见 [CMD_TIMER] 的注释）。
     *
     *  @return 广播**发出去了**没有（`sendBroadcast` 抛 = false）。它**不表示有人接**：
     *   注入侧没装 / 是老版本不认识这一条时，这里照样是 true —— 那种情况只能靠注入侧的日志认。
     *   调用方要按「false 就得自己兜底」处理，别把 true 当成「倒计时已经跑起来了」。
     */
    fun sendTimer(
        context: Context,
        seconds: Int,
    ): Boolean =
        try {
            context.sendBroadcast(
                Intent(ACTION_STATUSBAR)
                    .setPackage(HostApp.PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(EXTRA_CMD, CMD_TIMER)
                    .putExtra(EXTRA_TIMER_SECONDS, seconds),
            )
            XLog.w("timer:$seconds 已转给 system_server 起倒计时")
            true
        } catch (e: Throwable) {
            XLog.w("发 timer 广播失败：$seconds", e)
            false
        }

    /**
     * 调用点在 AMS 广播入口的 hooker 里（热路径 + system_server），
     * 整体再包一层 catch：漏一个异常出去就是系统崩。
     *
     * @param loader system_server 的 ClassLoader（本机反射用；null 则只记日志不执行）
     */
    fun handle(
        intent: Intent,
        loader: ClassLoader?,
    ): Boolean {
        if (intent.action != ACTION_STATUSBAR) return false
        val cmd =
            try {
                intent.getStringExtra(EXTRA_CMD).orEmpty()
            } catch (e: Throwable) {
                XLog.w("读状态栏指令 extra 失败：${e.message}")
                ""
            }
        try {
            when (cmd) {
                CMD_NOTIFICATIONS -> {
                    toggleNotificationsLocal(loader)
                }

                CMD_CONTROL_CENTER -> {
                    expandControlCenterLocal(loader)
                }

                CMD_RECENTS -> {
                    toggleRecentsLocal(loader)
                }

                CMD_POWER_MENU -> {
                    showPowerMenuLocal(loader)
                }

                CMD_VOICE_ASSIST -> {
                    VoiceAssistBridge.start(loader)
                }

                CMD_SETTING -> {
                    SettingBridge.handle(intent, loader)
                }

                CMD_BRIGHTNESS -> {
                    BrightnessBridge.apply(loader, intent.getIntExtra(EXTRA_BRIGHTNESS, -1))
                }

                CMD_TIMER -> {
                    startTimerLocal(intent, loader)
                }

                else -> {
                    XLog.w("不认识的状态栏指令：$cmd")
                }
            }
        } catch (e: Throwable) {
            XLog.w("执行状态栏指令失败（忽略）：$cmd", e)
        }
        return true
    }

    /**
     * 在 system_server 里起一个倒计时 —— 时钟那条 `ACTION_SET_TIMER` + `EXTRA_SKIP_UI`。
     *
     *  为什么这一下必须在本机（见 [CMD_TIMER]）：宿主是普通应用 uid，不在前台时
     *  `startActivity` 会被后台起活动那道闸**静默丢掉**（不抛异常，所以我们那边看不出失败）。
     *  本机这一下是 system_server 自己在起，那道闸不适用。
     *
     *  载荷照 AOSP 标准写：`EXTRA_LENGTH` 是**秒**，`EXTRA_SKIP_UI = true` 让时钟别停在拨盘页
     *  —— 少了它，秒数填好却不走（用户口径「不是打开界面，我要的是开始计时」）。
     */
    private fun startTimerLocal(
        intent: Intent,
        loader: ClassLoader?,
    ) {
        val seconds = intent.getIntExtra(EXTRA_TIMER_SECONDS, -1)
        if (seconds <= 0) {
            XLog.w("倒计时：秒数没带过来（$seconds），跳过")
            return
        }
        val ctx =
            loader?.let { SystemContext.get(it) } ?: run {
                XLog.w("倒计时：还拿不到 system_server 的 Context，跳过")
                return
            }
        ctx.startActivity(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        XLog.w("倒计时：已请 system_server 起 $seconds 秒的倒计时")
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
            val svc =
                Reflect
                    .findClass(loader, Framework.Calls.SERVICE_MANAGER)
                    ?.let { Reflect.callStatic(it, Framework.Calls.GET_SERVICE, Framework.Calls.SERVICE_STATUS_BAR) } ?: run {
                    XLog.w("拿不到 statusbar 服务对象，通知中心跳过")
                    return
                }
            val m =
                Reflect.methodByNameAndArity(svc.javaClass, Framework.Calls.TOGGLE_PANEL, 0) ?: run {
                    XLog.w("statusbar 服务（${svc.javaClass.name}）上没有 togglePanel()，通知中心跳过")
                    return
                }
            m.isAccessible = true
            // 按 system_server 自己的身份去调：这条链路可能是从宿主进程的 Binder 事务进来的，
            //  带着宿主的 uid 一旦撞上权限检查就是 SecurityException（见 [asSystem]）。
            asSystem { m.invoke(svc) }
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
            val svc =
                Reflect
                    .findClass(loader, Framework.Calls.SERVICE_MANAGER)
                    ?.let { Reflect.callStatic(it, Framework.Calls.GET_SERVICE, Framework.Calls.SERVICE_STATUS_BAR) } ?: run {
                    XLog.w("拿不到 statusbar 服务对象，控制中心跳过")
                    return
                }

            // —— 候选 1：mBar（SystemUI 的 IStatusBar），无权限闸门 ——
            val bar = Reflect.fieldValue(svc, svc.javaClass, Framework.Calls.FIELD_M_BAR)
            if (expandViaBar(bar)) return
            XLog.w(
                "控制中心候选 1 落空（mBar=${bar?.javaClass?.name ?: "取不到"}）；" +
                    "试候选 2（服务那层可能有 EXPAND_STATUS_BAR 闸门）",
            )

            // —— 候选 2：服务那一层 ——
            val byService =
                listOf(1, 0).firstNotNullOfOrNull { arity ->
                    Reflect.methodByNameAndArity(svc.javaClass, Framework.Calls.EXPAND_SETTINGS_PANEL, arity)
                }
            if (byService != null) {
                byService.isAccessible = true
                asSystem {
                    if (byService.parameterCount == 1) byService.invoke(svc, null) else byService.invoke(svc)
                }
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

    /**
     * 候选 1：直接调 SystemUI 的 `IStatusBar.animateExpandSettingsPanel`。
     *
     *  **异常自己收住**：漏到外层 catch 会把「试候选 2」整条截断（`InvocationTargetException`
     *  那条只记一句「被拒」就返回了），于是一条候选失败等于两条都没试。
     *
     * @return true = 已调通（调用方直接返回）
     */
    private fun expandViaBar(bar: Any?): Boolean {
        val b = bar ?: return false
        val m = Reflect.methodByNameAndArity(b.javaClass, Framework.Calls.ANIMATE_EXPAND_SETTINGS_PANEL, 1) ?: return false
        m.isAccessible = true
        val ok = runCatching { asSystem { m.invoke(b, null) } }.isSuccess
        if (ok) {
            XLog.w("已在本机调 IStatusBar.animateExpandSettingsPanel（控制中心）")
        } else {
            XLog.w("控制中心候选 1 调不通，继续试候选 2")
        }
        return ok
    }

    /** 把所有带 `Expand` / `Panel` 的方法名 + 参数个数打进日志（上面那条兜底用的）。 */
    private fun dumpPanelMethods(
        where: String,
        cls: Class<*>,
    ) {
        try {
            val names =
                cls.methods
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
            val internalCls =
                Reflect.findClass(
                    loader,
                    Framework.Calls.STATUS_BAR_INTERNAL,
                ) ?: run {
                    XLog.w("找不到 StatusBarManagerInternal，最近任务跳过")
                    return
                }
            val localServices =
                Reflect.findClass(loader, Framework.Calls.LOCAL_SERVICES)
                    ?: run {
                        XLog.w("找不到 LocalServices，最近任务跳过")
                        return
                    }
            val svc =
                Reflect.callStatic(localServices, Framework.Calls.GET_SERVICE, internalCls) ?: run {
                    XLog.w("LocalServices 里没有 StatusBarManagerInternal（statusbar 服务没起来？）")
                    return
                }
            val m =
                Reflect.methodByNameAndArity(svc.javaClass, Framework.Calls.TOGGLE_RECENT_APPS, 0) ?: run {
                    XLog.w("${svc.javaClass.name} 上没有 toggleRecentApps()，最近任务跳过")
                    return
                }
            //  **不能走 `Reflect.call`**：它把 invoke 的异常吞掉、返回 null，于是这一行「已调」
            //  在调用失败时照样打出来（最近任务没反应，日志却说调过了）。直接 invoke，
            //  成功才记账、失败落进下面的 catch。
            asSystem { m.invoke(svc) }
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
            val wms =
                Reflect
                    .findClass(loader, Framework.Calls.SERVICE_MANAGER)
                    ?.let { Reflect.callStatic(it, Framework.Calls.GET_SERVICE, Framework.Calls.SERVICE_WINDOW) } ?: run {
                    XLog.w("拿不到 window 服务对象，电源菜单跳过")
                    return
                }
            val policy =
                Reflect.fieldValue(wms, wms.javaClass, Framework.Calls.FIELD_M_POLICY) ?: run {
                    XLog.w("window 服务（${wms.javaClass.name}）上没有 mPolicy 字段，电源菜单跳过")
                    return
                }
            val m =
                Reflect.methodByNameAndArity(policy.javaClass, Framework.Calls.SHOW_GLOBAL_ACTIONS, 0) ?: run {
                    XLog.w("${policy.javaClass.name} 上没有 showGlobalActions()，电源菜单跳过")
                    return
                }
            m.isAccessible = true
            asSystem { m.invoke(policy) }
            XLog.w("已在本机调 PhoneWindowManager.showGlobalActions（电源菜单）")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 服务端抛的异常包在 InvocationTargetException 里 —— 只打外层等于没打
            XLog.w("showGlobalActions 被拒：${e.cause?.javaClass?.name}: ${e.cause?.message}")
        } catch (e: Throwable) {
            XLog.w("弹电源菜单失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

/**
 * 「把这块屏的背光推到某个值」那一条 —— [StatusBarBridge.CMD_BRIGHTNESS] 的注入侧实现。
 *
 * ## 为什么要走 system_server
 *
 *  `IDisplayManager.setTemporaryBrightness` 在服务那层要
 *  `android.permission.CONTROL_DISPLAY_BRIGHTNESS`（signature|privileged）。宿主
 *  `com.oplus.gesture` 只有 `WRITE_SETTINGS` + `DEVICE_POWER`，调不动；system_server 是 uid 1000，
 *  天然放行（依据见 `docs/device/actions/brightness.md` 38.3）。
 *
 * ## 为什么要调它
 *
 *  控制中心的亮度条只在 `DisplayPowerController.saveBrightnessInfo()` 判成「这块屏的背光变了」时
 *  才刷新（它随后 `sendDisplayEventsIfEnabledLocked(display, 128)` → `onDisplayChanged` →
 *  SystemUI 重读 `screen_brightness`）。自动亮度下 `screen_brightness` 只是「关掉自动后回到的值」，
 *  写它不会让背光按那个值走，条也就不动 —— 小布 `j40.j.r()` 在写库前先调这两个方法，正是为了
 *  这一下（38.2 / 38.3）。
 *
 * ## 独立成一个对象
 *
 *  `StatusBarBridge` 已经贴着 detekt 的「对象内函数数」上限（[StatusBarBridge] 那几条指令各一支
 *  private 实现），再往里塞四个函数就顶破了；这一支与状态栏也本来没关系 —— 它只是借了那条广播。
 */
internal object BrightnessBridge {
    /**
     * 把主屏背光推到 [raw]（raw 尺度，本机 `2..4095`）。
     *
     *  与系统自己拖动亮度条同一条路（`SystemUI/BrightnessController.setBrightness` 调的也是它）。
     *  **不写库**：写库仍归宿主（自动亮度下要写的 `screen_auto_brightness_adj` 是 PRIVATE_SETTINGS，
     *  注入侧也没有 `ContentResolver`）。
     */
    fun apply(
        loader: ClassLoader?,
        raw: Int,
    ) {
        if (raw < 0) {
            XLog.w("亮度指令没带值，跳过")
            return
        }
        val global = displayManager(loader) ?: return
        val value = raw.toFloat()
        try {
            if (!pushTemporaryBrightness(global, value)) return
            XLog.w("已把背光推到 $raw（DisplayManager 临时亮度）")
            pushAutoAdjustment(global, value)
        } catch (e: InvocationTargetException) {
            // 服务端抛的异常包在 InvocationTargetException 里 —— 只打外层等于没打
            XLog.w("setTemporaryBrightness 被拒：${e.cause?.javaClass?.name}: ${e.cause?.message}")
        } catch (e: Throwable) {
            XLog.w("推背光失败（忽略）：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 取 `DisplayManagerGlobal` 实例（`setTemporaryBrightness` 的载体）；取不到就记一行、返回 `null`。 */
    private fun displayManager(loader: ClassLoader?): Any? {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，亮度刷新跳过")
            return null
        }
        val cls =
            Reflect.findClass(loader, Framework.Calls.DISPLAY_MANAGER_GLOBAL) ?: run {
                XLog.w("找不到 ${Framework.Calls.DISPLAY_MANAGER_GLOBAL}，亮度刷新跳过")
                return null
            }
        return Reflect.callStatic(cls, Framework.Calls.GET_INSTANCE) ?: run {
            XLog.w("DisplayManagerGlobal.getInstance() 返回 null，亮度刷新跳过")
            return null
        }
    }

    /** 推临时亮度；方法不存在时记一行并返回 `false`（调用方据此不再往下走）。 */
    private fun pushTemporaryBrightness(
        global: Any,
        value: Float,
    ): Boolean {
        val m =
            Reflect.methodByNameAndArity(
                global.javaClass,
                Framework.Calls.SET_TEMPORARY_BRIGHTNESS,
                2,
            ) ?: run {
                XLog.w("${global.javaClass.name} 上没有 setTemporaryBrightness(int, float)，亮度刷新跳过")
                return false
            }
        m.isAccessible = true
        asSystem { m.invoke(global, 0, value) }
        return true
    }

    /** 第二下：把自动亮度的用户偏移也设成同值；方法不存在时只记一行（不影响上面那一下）。 */
    private fun pushAutoAdjustment(
        global: Any,
        value: Float,
    ) {
        val m =
            Reflect.methodByNameAndArity(
                global.javaClass,
                Framework.Calls.SET_TEMPORARY_AUTO_ADJUSTMENT,
                1,
            )
        if (m == null) {
            XLog.w("${global.javaClass.name} 上没有 setTemporaryAutoBrightnessAdjustment(float)，只推了背光")
            return
        }
        m.isAccessible = true
        asSystem { m.invoke(global, value) }
    }
}

/**
 * 「唤醒小布」（`oplus:voice_assist`）的执行侧 —— **只在 system_server 里跑**。
 *
 *  独立成一个对象而不是并进 [StatusBarBridge]：那一族「一种指令一个函数」，再加一支就顶破
 *  detekt 的函数数上限；而这一支本来就比别的指令多一段（挑包名、组 intent），放在一起
 *  会把那张分派表挤得看不清。通道还是同一条（`CMD_VOICE_ASSIST`），入口在那边。
 *
 *  ## 为什么必须由 system_server 起
 *
 *  ROM 里这条配方（`PhoneWindowManagerExtImpl.startSpeech`）是拿**框架自己的身份**去
 *  `startForegroundServiceAsUser`，而 `checkComponentPermission` 对 `SYSTEM_UID` 直接放行 ——
 *  也就是说「起得来」靠的是调用方是谁，不是目标服务对第三方开放。宿主 `com.oplus.gesture`
 *  没有这份旁路，所以这一档不放给宿主（与通知中心 / 电源菜单同一条纪律）。
 *  配方与取证见 `docs/device/actions/voice.md` 第三十节。
 */
internal object VoiceAssistBridge {
    /**
     * 起一次小布助手（Breeno）。
     *
     *  @param loader system_server 的 ClassLoader —— 取 Context 要用它；null 时只记日志
     *    （注入侧还没初始化完，这一下不执行）
     */
    fun start(loader: ClassLoader?) {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，唤醒小布跳过")
            return
        }
        val ctx = contextOf(loader) ?: return
        val pkg =
            installedSpeechPackage(ctx) ?: run {
                XLog.w("本机没装小布助手（找过 ${Framework.Calls.SPEECH_PACKAGES}），唤醒小布跳过")
                return
            }
        // FLAG_ACTIVITY_NEW_TASK 对服务没有作用 —— ROM 那边也加了（同一份配方），照抄便于对账
        val intent =
            Intent(Framework.Calls.SPEECH_ACTION_ACTIVATE)
                .setComponent(ComponentName(pkg, Framework.Calls.SPEECH_SERVICE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Framework.Calls.SPEECH_EXTRA_CALLER, Framework.Calls.SPEECH_CALLER_VALUE)
                .putExtra(Framework.Calls.SPEECH_EXTRA_START_TYPE, Framework.Calls.SPEECH_START_TYPE_POWER)
        try {
            //  **看返回值**：组件不存在 / 未导出时 AMS 返回 `null` 而**不抛** ——
            //  只判「没抛异常」就会把「什么也没起」记成「已下发」，用户侧一片静默。
            val started = asSystem { ctx.startForegroundService(intent) }
            if (started == null) {
                XLog.w("唤醒小布：系统没接受这个组件（$pkg/${Framework.Calls.SPEECH_SERVICE}），没有起服务")
                return
            }
            XLog.w(
                "已在本机 startForegroundService($pkg/${Framework.Calls.SPEECH_SERVICE}，" +
                    "start_type=${Framework.Calls.SPEECH_START_TYPE_POWER})（唤醒小布）",
            )
        } catch (e: Throwable) {
            // 起不来的两种常见来路：目标服务没导出 / 按调用方判签名 —— 只记日志，不抛
            XLog.w("唤醒小布失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 取一个能起服务的 Context；取不到记一行就返回 null（这一下不执行）。 */
    private fun contextOf(loader: ClassLoader): Context? {
        val ctx =
            try {
                SystemContext.get(loader)
            } catch (e: Throwable) {
                XLog.w("取 Context 失败，唤醒小布跳过：${e.javaClass.simpleName}")
                null
            }
        if (ctx == null) XLog.w("没有 Context，唤醒小布跳过")
        return ctx
    }

    /**
     * 装着的那个语音助手包名 —— 新包优先，老包兜底（[Framework.Calls.SPEECH_PACKAGES]）。
     *
     *  为什么要在运行期挑：ROM 自己也是按 `mBreenoPkg` 走的（新机型 `heytap`、早期 `oppo`），
     *  写死一个的话换一代机器这一格就安静地失效。
     */
    private fun installedSpeechPackage(ctx: Context): String? =
        Framework.Calls.SPEECH_PACKAGES.firstOrNull { pkg ->
            runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
        }
}
