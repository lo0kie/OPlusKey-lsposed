package com.lookie.opluskey.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.UserHandle
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog

/** 拉界面 / 发广播 / 触发外部能力这一族动作的执行器。 */
internal class AppLauncher(
    private val context: () -> Context?,
    /** 失败通知出口。 */
    private val onFailure: (CommandFailure) -> Unit,
) {

    /**
     * `intent:ACTION`：先按界面拉起，拉不起来再当广播发。顺序不能反 ——
     * 清单里打开相机那条就是个界面 Intent，只发广播相机不会开。
     */
    fun openIntent(action: String) {
        val ctx = context() ?: run {
            XLog.w("没有 Context，intent:$action 跳过")
            return
        }
        try {
            ctx.startActivity(Intent(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            XLog.i("intent:$action → 已作为界面拉起")
            return
        } catch (e: Throwable) {
            XLog.d("intent:$action 当界面拉不起来（${e.message}），改发广播")
        }
        try {
            ctx.sendBroadcast(Intent(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) {
            XLog.w("发广播失败：$action", e)
        }
    }

    /** `app:包名/Activity` 或 `app:包名` —— 拉起一个界面。 */
    fun launchApp(target: String) {
        val ctx = context() ?: run {
            XLog.w("没有 Context，app:$target 跳过")
            return
        }
        val pm = ctx.packageManager
        val intent = entryIntent(pm, target)
        if (intent == null) {
            // 区分「包还在但没有可启动界面」与「包已不在」，前者提示「已卸载」会误导
            val pkg = target.substringBefore('/')
            val installed = try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: Throwable) {
                false
            }
            XLog.w("解析不到界面：$target（这个包还在=$installed）")
            onFailure(if (installed) CommandFailure.APP_NO_ENTRY else CommandFailure.APP_MISSING)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            XLog.w("拉起界面失败：$target", e)
            onFailure(CommandFailure.APP_MISSING)
        } catch (e: Throwable) {
            // 最常见的是后台启动限制 / 安全异常，不是「已卸载」—— 报「没执行成功」别误导
            XLog.w("拉起界面失败：$target", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * `app:` / `window:` 共用的入口解析。
     *
     * `包名/.Activity` 是相对写法，必须自己把包名拼回去：`setClassName` / `ComponentName` 不会展开
     * 相对类名（那是 `createRelative` 的事），照原样传进去就是「组件不存在」。全限定名原样用。
     */
    private fun entryIntent(pm: PackageManager, target: String): Intent? = if (target.contains('/')) {
        val pkg = target.substringBefore('/')
        val raw = target.substringAfter('/')
        val cls = if (raw.startsWith(".") || '.' !in raw) "$pkg.$raw" else raw
        Intent().setClassName(pkg, cls)
    } else {
        AppEntry.resolve(pm, target)
    }

    /**
     * `window:包名` —— 用小窗打开，**有现成任务就复用**（原地转小窗），没有才新建一份。
     *
     * 先走 ColorOS 自己的小窗接口 `android.app.OplusActivityManager#startZoomWindow`（在
     * `oplus-framework.jar` 里，属宿主框架的私有类型，所以只能按名字反射）；反射不到就退公开的
     * `ActivityOptions` 自由窗口；两条都不行再退普通打开 —— 宁可开成普通界面，也不能按下去什么都不发生。
     * 宿主持有 `MANAGE_ACTIVITY_TASKS` 与 `oplus.permission.OPLUS_COMPONENT_SAFE`，服务端两道闸门都过得去。
     */
    fun launchAppInWindow(target: String) {
        val ctx = context() ?: run {
            XLog.w("没有 Context，window:$target 跳过")
            return
        }
        val pm = ctx.packageManager
        val intent = entryIntent(pm, target)
        if (intent == null) {
            val pkg = target.substringBefore('/')
            val installed = try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: Throwable) {
                false
            }
            XLog.w("解析不到界面：$target（这个包还在=$installed）")
            onFailure(if (installed) CommandFailure.APP_NO_ENTRY else CommandFailure.APP_MISSING)
            return
        }
        val pkg = intent.component?.packageName ?: target.substringBefore('/')

        //  目标就在前台时**不能起**：这次启动会被栈顶那份实例自己消化（`START_DELIVERED_TO_TOP`），
        //  硬起只会多出一份全屏实例。改成走 recents 那条，把它那份任务**原地**收成小窗。
        val foregroundTask = foregroundTaskFor(pkg)
        if (foregroundTask > 0 && startFlexibleFromRecents(foregroundTask)) {
            XLog.w("window:$target 已在前台，原地收成小窗（不新起实例）")
            Handler(Looper.getMainLooper()).postDelayed(
                { if (!isTaskInZoomWindow(foregroundTask)) retryAfterHome(ctx, intent, target) },
                MINI_VERIFY_MS,
            )
            return
        }

        //  其余情况只加 `NEW_TASK`：目标已有任务会被服务端**复用**并原地转成小窗，没有才新建一份。
        //  别加 `MULTIPLE_TASK` —— 那会强制新建，同一个应用被按几次就多出几份实例。
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val code = startZoomWindowCode(ctx, intent)
        if (code != null && code >= 0) {
            if (code == START_DELIVERED_TO_TOP) retryAfterHome(ctx, intent, target)
            return
        }
        if (startFreeform(ctx, intent)) return
        XLog.w("window:$target 的小窗两条路都没走通，退成普通打开")
        try {
            ctx.startActivity(intent)
        } catch (e: Throwable) {
            XLog.w("小窗与普通打开都失败：$target", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 这次启动被栈顶那份实例消化了：先把任务挪出栈顶，再起一次（仍是原地转，不新建）。 */
    private fun retryAfterHome(ctx: Context, intent: Intent, target: String) {
        XLog.w("window:$target 的界面占着前台，先回桌面再起小窗")
        ctx.startActivity(homeIntent())
        Handler(Looper.getMainLooper()).postDelayed({ startZoomWindow(ctx, intent) }, HOME_SETTLE_MS)
    }

    /**
     * `zoom:mini` —— 把**前台**那个应用收成小窗，**原地收**，不另起实例，一步到位。
     *
     * 走系统 recents 那条正门（`startActivityFromRecentsForFlexible`）：服务端直接把那份已有任务改成小窗，
     * taskId 与界面状态都不变。代价是**没有转场动画**（它是改窗口模式，不是一次启动）；
     * 「挪出栈顶再 startZoomWindow」那条有动画但要多回桌面一步，已否掉。见 `docs/device-findings.md` §17.6.4。
     */
    fun putForegroundInMiniWindow() {
        if (context() == null) {
            XLog.w("没有 Context，zoom:mini 跳过")
            return
        }
        val taskId = foregroundTaskId()
        if (taskId > 0 && startFlexibleFromRecents(taskId)) {
            Handler(Looper.getMainLooper()).postDelayed({ verifyMiniOrFallback(taskId) }, MINI_VERIFY_MS)
            return
        }
        fallbackByPackage()
    }

    /** 前台那份任务的 taskId —— `getAllTopAppInfos()` 按「最前面」排序，取第 0 项。 */
    private fun foregroundTaskId(): Int {
        val infos = topAppInfos() ?: return -1
        val info = infos.firstOrNull() ?: return -1
        val id = Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int ?: return -1
        XLog.d("zoom:mini → 前台任务 #$id")
        return id
    }

    /** 前台上如果是这个包，返回它那份任务的 taskId；不是就 -1。 */
    private fun foregroundTaskFor(pkg: String): Int {
        val infos = topAppInfos() ?: return -1
        val info = infos.firstOrNull() ?: return -1
        val top = Reflect.fieldValue(info, info.javaClass, TOP_APP_ACTIVITY_FIELD) ?: return -1
        if (packageOf(top, top.javaClass) != pkg) return -1
        return Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int ?: -1
    }

    /** `OplusAppInfo.topActivity` 的包名 —— `ComponentName` 与 `ComponentInfo` 两种形状都认。 */
    private fun packageOf(top: Any, cls: Class<*>): String? =
        Reflect.call(top, "getPackageName") as? String
            ?: Reflect.fieldValue(top, cls, TOP_APP_PACKAGE_FIELD) as? String

    /** `OplusActivityManager#getAllTopAppInfos()` 的原始列表；拿不到一律返回 null（调用方据此降级）。 */
    private fun topAppInfos(): List<*>? {
        val cls = Reflect.findClass(javaClass.classLoader, ZOOM_MANAGER) ?: return null
        val manager = Reflect.callStatic(cls, "getInstance") ?: return null
        val method = Reflect.methodByNameAndArity(cls, TOP_APP_INFOS_METHOD, 0) ?: run {
            XLog.d("$ZOOM_MANAGER 上没有 $TOP_APP_INFOS_METHOD/0")
            return null
        }
        return Reflect.call(manager, method) as? List<*>
    }

    /** recents 正门：把已有任务原地收成小窗。返回 false = 这条路没走通，调用方降级。 */
    private fun startFlexibleFromRecents(taskId: Int): Boolean {
        val cls = Reflect.findClass(javaClass.classLoader, ZOOM_TASK_MANAGER) ?: run {
            XLog.d("没有 $ZOOM_TASK_MANAGER，跳过 recents 那条")
            return false
        }
        val manager = Reflect.callStatic(cls, "getInstance") ?: return false
        val method = Reflect.methodByNameAndArity(cls, RECENTS_FLEXIBLE_METHOD, RECENTS_FLEXIBLE_ARGS) ?: run {
            XLog.d("$ZOOM_TASK_MANAGER 上没有 $RECENTS_FLEXIBLE_METHOD/$RECENTS_FLEXIBLE_ARGS")
            return false
        }
        val options = Bundle().apply {
            putInt(OPT_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_ZOOM)
            putInt(OPT_ZOOM_LAUNCH_FLAGS, ZOOM_LAUNCH_FLAG_USER_POSITION)
            putInt(ZOOM_EXTRA_WINDOW_MODE, WINDOWING_MODE_ZOOM)
        }
        val code = Reflect.call(manager, method, taskId, options) as? Int
        if (code == null || code < 0) {
            XLog.d("$RECENTS_FLEXIBLE_METHOD($taskId) 被拒（返回 $code），退下一层")
            return false
        }
        XLog.w("zoom:mini → 从 recents 把任务 #$taskId 原地收成小窗（返回 $code）")
        return true
    }

    /** 等 transition 跑完核一眼；没成小窗就退回「按包名另起一份实例」。 */
    private fun verifyMiniOrFallback(taskId: Int) {
        if (isTaskInZoomWindow(taskId)) {
            XLog.w("zoom:mini → 任务 #$taskId 已是小窗（原地收的，没另起实例）")
            return
        }
        XLog.w("zoom:mini → 任务 #$taskId 没变成小窗，退回「按包名另起一份实例」")
        fallbackByPackage()
    }

    /** 那个任务现在是不是小窗 —— 只看 `windowingMode`（返回码和截图都会骗人）。 */
    private fun isTaskInZoomWindow(taskId: Int): Boolean {
        val infos = topAppInfos() ?: return false
        for (item in infos) {
            val info = item ?: continue
            val id = Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int ?: continue
            if (id != taskId) continue
            val mode = Reflect.fieldValue(info, info.javaClass, TOP_APP_WINDOWING_MODE_FIELD) as? Int
            return mode == WINDOWING_MODE_ZOOM
        }
        return false
    }

    /** 兜底：取前台包名 → 按 `window:` 那条另起一份实例的小窗。 */
    private fun fallbackByPackage() {
        val cls = Reflect.findClass(javaClass.classLoader, ZOOM_MANAGER) ?: run {
            XLog.w("没有 $ZOOM_MANAGER，zoom:mini 走不通")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        val manager = Reflect.callStatic(cls, "getInstance") ?: run {
            XLog.w("$ZOOM_MANAGER.getInstance() 拿不到实例，zoom:mini 走不通")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        val top = Reflect.methodByNameAndArity(cls, TOP_ACTIVITY_METHOD, 0)
            ?.let { Reflect.call(manager, it) } as? ComponentName
        if (top == null) {
            XLog.w("取不到前台活动（$TOP_ACTIVITY_METHOD 没返回），zoom:mini 走不通")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        XLog.w("zoom:mini → 前台是 ${top.packageName}，以小窗打开它")
        launchAppInWindow(top.packageName)
    }

    /**
     * ColorOS 小窗：`OplusActivityManager#startZoomWindow(Intent, Bundle, int, String)`。
     *
     * Bundle 必须带 `extra_window_mode`（见 [zoomWindowBundle]），否则服务端当普通启动处理。
     */
    private fun startZoomWindow(ctx: Context, intent: Intent): Boolean {
        val code = startZoomWindowCode(ctx, intent)
        return code != null && code >= 0
    }

    /**
     * 上面那支的原始返回码 —— 是 `ActivityStarter` 的 `START_*`，不是自定义的成功标志：
     * `0` 成功 / `2` 已有任务提到前台 / `3` 交给栈顶那个实例消化（屏幕不变）/ 负数是错误码；
     * `null` = 反射这条路没走通。返回码判不了「有没有真起成小窗」，详见 `docs/device-findings.md` §17.6.1。
     */
    private fun startZoomWindowCode(ctx: Context, intent: Intent): Int? {
        val cls = Reflect.findClass(javaClass.classLoader, ZOOM_MANAGER) ?: run {
            XLog.d("没有 $ZOOM_MANAGER，跳过 ColorOS 小窗接口")
            return null
        }
        val manager = Reflect.callStatic(cls, "getInstance") ?: return null
        val method = Reflect.methodByNameAndArity(cls, "startZoomWindow", ZOOM_ARGS) ?: run {
            XLog.d("$ZOOM_MANAGER 上没有 startZoomWindow/$ZOOM_ARGS")
            return null
        }
        val userId = currentUserId()
        val result = Reflect.call(
            manager, method, intent, zoomWindowBundle(), userId, ctx.packageName,
        ) as? Int
        if (result == null || result < 0) {
            XLog.d("startZoomWindow 被拒（返回 $result），退下一层")
            return result
        }
        XLog.w("window:${intent.component?.packageName} → startZoomWindow=$result")
        return result
    }

    /** 回桌面：某个界面占着前台时，先把它挪出栈顶，小窗才起得来（见 [launchAppInWindow]）。 */
    private fun homeIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * `startZoomWindow` 的 Bundle —— **必须**带 `extra_window_mode = WINDOWING_MODE_ZOOM`。
     *
     * 空 Bundle 时服务端认不出这次是「起小窗」，`FlexibleTaskController` 直接返回 0（= 没做事），
     * 现象就是按下去什么都不发生。两个常量抄自 SystemUI 里那个能用的调用方
     * （`ScenarioAssistantNavigationImpl.startActivityByZoonWindow`）与 SDK 的常量表
     * （`com.oplus.zoomwindow.OplusZoomWindowManager`）。
     */
    private fun zoomWindowBundle(): Bundle =
        Bundle().apply { putInt(ZOOM_EXTRA_WINDOW_MODE, WINDOWING_MODE_ZOOM) }

    /** 当前用户 id —— `UserHandle.getUserId` 是隐藏 API，拿不到就按 `PER_USER_RANGE` 折。 */
    private fun currentUserId(): Int =
        Reflect.callStatic(UserHandle::class.java, "getUserId", Process.myUid()) as? Int
            ?: (Process.myUid() / PER_USER_RANGE)

    /** 公开的退路：`ActivityOptions` 的自由窗口（本机不认它的话就是一次普通打开）。 */
    private fun startFreeform(ctx: Context, intent: Intent): Boolean = try {
        val options = android.app.ActivityOptions.makeBasic()
        // 窗口模式那一对也都是隐藏 API：常量按字段取，设值按方法名调
        val mode = Reflect.field(android.app.ActivityOptions::class.java, "WINDOWING_MODE_FREEFORM")
            ?.getInt(null)
        if (mode == null) {
            XLog.d("取不到 WINDOWING_MODE_FREEFORM，放弃自由窗口这条")
            return false
        }
        Reflect.call(options, "setLaunchWindowingMode", mode) ?: return false
        ctx.startActivity(intent, options.toBundle())
        XLog.w("window:${intent.component?.packageName} → ActivityOptions 自由窗口（mode=$mode）")
        true
    } catch (e: Throwable) {
        XLog.d("自由窗口起法被拒：${e.javaClass.simpleName}: ${e.message}")
        false
    }

    /**
     * 执行 ColorOS 小布快捷指令（`com.coloros.shortcuts`）。
     * 早先「三套通道逐个试、哪个成了算哪个」的判据不成立（`sendBroadcast` 是 fire-and-forget，
     * 没接收者也不抛异常，第一条永远 ok、后两套根本不会被尝试）。现在先问系统哪些 action
     * 真有接收者、只发那些（避免同一条指令被执行两次），一条都查不到再全部试（非导出接收器
     * 对跨 uid 查询不可见）；日志只说「已发」这一步的事实，指令有没有被执行是小布自己的事。
     */
    fun runShortcut(shortcutId: String) {
        if (shortcutId.isBlank()) return
        val ctx = context() ?: run {
            XLog.w("没有 Context，shortcut:$shortcutId 跳过")
            return
        }

        val resolved = SHORTCUT_ACTIONS.filter { hasReceiver(ctx, it) }
        val channels = resolved.ifEmpty {
            XLog.d("小布快捷指令：没查到任何接收者（可能是非导出接收器），仍按全部通道各发一次")
            SHORTCUT_ACTIONS
        }

        var sent = 0
        for (action in channels) {
            val intent = Intent(action)
                .setPackage(SHORTCUT_PKG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 各版本用过不同的 extra key 名，**两条都带上** —— 实现方读哪个都能拿到。
            for (key in SHORTCUT_ID_KEYS) intent.putExtra(key, shortcutId)
            try {
                ctx.sendBroadcast(intent)
                sent++
                XLog.w("已发小布快捷指令：$action（id=$shortcutId）")
            } catch (e: Throwable) {
                XLog.w("发小布快捷指令失败：$action", e)
            }
        }
        if (sent == 0) onFailure(CommandFailure.SHORTCUT_FAILED)
    }

    /** 这条 action 在 `com.coloros.shortcuts` 里有没有接收者（查不到 / 抛异常都按「没有」）。 */
    private fun hasReceiver(ctx: Context, action: String): Boolean = try {
        ctx.packageManager.queryBroadcastReceivers(
            Intent(action).setPackage(SHORTCUT_PKG),
            0,
        ).isNotEmpty()
    } catch (e: Throwable) {
        XLog.d("查小布快捷指令接收者失败（按没有处理）：$action | ${e.message}")
        false
    }

    /**
     * 起一个 ColorOS 系统能力（`oplus:` 动作）；目标组件与 action 见 [OplusFeature]。
     */
    fun startOplusFeature(feature: String) {
        val target = OplusFeature.of(feature) ?: run {
            XLog.w("不认识的 oplus 能力：$feature")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        val ctx = context() ?: run {
            XLog.w("没有 Context，oplus:$feature 跳过")
            return
        }
        // Messenger 绑定型（场景表 type=service 的大部分）必须 bind 后发 Message
        if (target.message != null) {
            sendMessengerMessage(ctx, feature, target)
            return
        }
        val intent = buildFeatureIntent(target)
        for ((key, value) in target.extras) {
            when (value) {
                is Int -> intent.putExtra(key, value)
                else -> intent.putExtra(key, value.toString())
            }
        }

        val short = "${target.owner}/${target.service.substringAfterLast('.')}"
        // 界面型目标：非 Activity 的 Context 起界面必须带 NEW_TASK，否则 ActivityNotFoundException
        if (target.activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(intent)
                XLog.w("oplus:$feature → startActivity($short)")
            } catch (e: Throwable) {
                XLog.w("起 oplus:$feature 的界面失败：${e.javaClass.simpleName}: ${e.message}")
                onFailure(CommandFailure.UNKNOWN)
            }
            return
        }

        // 目标声明了 special-use 前台类型时先走前台 —— 那是它在清单里给自己定的起法，
        // 普通 startService 会被后台启动限制拒。没声明的反过来先试 startService：
        // 对方不调 startForeground 时，前台方式会让**它的进程**抛
        // ForegroundServiceDidNotStartInTimeException。
        var last: Throwable? = null
        for (foreground in if (target.foreground) FOREGROUND_FIRST else SERVICE_FIRST) {
            try {
                if (foreground) ctx.startForegroundService(intent) else ctx.startService(intent)
                XLog.w("oplus:$feature → ${if (foreground) "startForegroundService" else "startService"}($short)")
                return
            } catch (e: Throwable) {
                last = e
                XLog.d("oplus:$feature 的 ${if (foreground) "前台" else "普通"}起法被拒：${e.message}")
            }
        }
        XLog.w("起 oplus:$feature 失败：${last?.javaClass?.simpleName}: ${last?.message}")
        onFailure(CommandFailure.UNKNOWN)
    }

    /**
     * Messenger 绑定型服务：**bind 后发一条 `Message`**（`what=1` 单击 / `2` 长按）。
     * 这类服务没有 `onStartCommand`，`startService` 对它们是空操作（2026-10-13 实测：
     * 调用成功、AMS 也受理，但目标进程毫无动静）。发完立刻解绑 —— `send` 是单向
     * binder 调用，消息已经进了对方的 Handler 队列。
     */
    private fun sendMessengerMessage(ctx: Context, feature: String, target: OplusFeature.Target) {
        val what = target.message ?: return
        val intent = Intent().setComponent(ComponentName(target.owner, target.service))
        val short = "${target.owner}/${target.service.substringAfterLast('.')}"
        val main = Handler(Looper.getMainLooper())
        var handled = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                handled = true
                try {
                    val message = Message.obtain(null, what)
                    // 服务端会向 replyTo 回执，不设会在它那边空指针
                    message.replyTo = Messenger(Handler(Looper.getMainLooper()))
                    Messenger(binder).send(message)
                    XLog.w("oplus:$feature → Messenger.send(what=$what)($short)")
                } catch (e: Throwable) {
                    XLog.w("oplus:$feature 的 Messenger 消息发不出去：${e.javaClass.simpleName}: ${e.message}")
                    onFailure(CommandFailure.UNKNOWN)
                } finally {
                    runCatching { ctx.unbindService(this) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
        try {
            ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            main.postDelayed({
                // 服务一直连不上就自己收尾，别把连接挂着
                if (!handled) runCatching { ctx.unbindService(connection) }
            }, MESSAGE_TIMEOUT_MS)
        } catch (e: Throwable) {
            XLog.w("bind oplus:$feature 失败：${e.javaClass.simpleName}: ${e.message}")
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * 有 action 的按系统自己的写法发 —— `action + setPackage`，由清单 intent-filter 解析；
     * 场景表里 `scene_owner` + action 本来就是系统发给该服务的原样。只有组件名（无 action）
     * 的才用显式组件。
     */
    private fun buildFeatureIntent(target: OplusFeature.Target): Intent =
        if (target.action != null) {
            Intent(target.action).setPackage(target.owner)
        } else {
            Intent().setComponent(ComponentName(target.owner, target.service))
        }

    /**
     * 小布识屏：配置里仍写 KSU 那串 shell 命令（老配置不能失效），执行换成
     * [startOplusFeature] 的精确调用。
     */
    fun startSidebar() = startOplusFeature("screen_ocr")

    private companion object {

        /** ColorOS 小窗接口的类名 —— 按名字反射，不直接引用（注入侧不许碰宿主框架的私有类型）。 */
        const val ZOOM_MANAGER = "android.app.OplusActivityManager"

        /** `startZoomWindow` 的形参个数：`Intent, Bundle, int, String`。 */
        const val ZOOM_ARGS = 4

        /** Bundle 里指定窗口模式的 key —— `OplusZoomWindowManager.EXTRA_WINDOW_MODE`。 */
        const val ZOOM_EXTRA_WINDOW_MODE = "extra_window_mode"

        /** 上面那个 key 取这个值才是小窗 —— `OplusZoomWindowManager.WINDOWING_MODE_ZOOM`。 */
        const val WINDOWING_MODE_ZOOM = 100

        /** 取前台活动组件名：`OplusActivityManager#getTopActivityComponentName()` → `ComponentName`。 */
        const val TOP_ACTIVITY_METHOD = "getTopActivityComponentName"

        /** 取各屏前台应用信息：`OplusActivityManager#getAllTopAppInfos()` → `List<OplusAppInfo>`。 */
        const val TOP_APP_INFOS_METHOD = "getAllTopAppInfos"

        /** `OplusAppInfo` 的字段名（按名字取，不引宿主框架的类）。 */
        const val TOP_APP_ACTIVITY_FIELD = "topActivity"
        const val TOP_APP_TASK_ID_FIELD = "taskId"
        const val TOP_APP_WINDOWING_MODE_FIELD = "windowingMode"
        const val TOP_APP_PACKAGE_FIELD = "packageName"

        /** 「原地收成小窗」那支挂在 AIDL 门面 `OplusActivityTaskManager` 上，不在 `OplusActivityManager`。 */
        const val ZOOM_TASK_MANAGER = "android.app.OplusActivityTaskManager"

        /** `startActivityFromRecentsForFlexible(int taskId, Bundle options)` 及其形参个数。 */
        const val RECENTS_FLEXIBLE_METHOD = "startActivityFromRecentsForFlexible"
        const val RECENTS_FLEXIBLE_ARGS = 2

        /** `ActivityOptions#fromBundle` 认的窗口模式 key —— **点号**，与 `extra_window_mode` 不同。 */
        const val OPT_LAUNCH_WINDOWING_MODE = "android.activity.windowingMode"

        /** `mZoomLaunchFlags` 在 options 里的 key。 */
        const val OPT_ZOOM_LAUNCH_FLAGS = "android:activity.mZoomLaunchFlags"

        /** `mZoomLaunchFlags = 2`（服务端 `setUserPositionInfo`）：展开的普通小窗，沿用上次的位置与尺寸。 */
        const val ZOOM_LAUNCH_FLAG_USER_POSITION = 2

        /** 起完等多久再核「有没有真变成小窗」（服务端过完 transition 才生效）。 */
        const val MINI_VERIFY_MS = 350L

        /** `START_DELIVERED_TO_TOP`：intent 被交给栈顶那个活动消化，屏幕不会有任何变化。 */
        const val START_DELIVERED_TO_TOP = 3

        /** 上面那档时先回桌面，等桌面过渡铺开再起小窗（毫秒）。 */
        const val HOME_SETTLE_MS = 400L

        /** `UserHandle.PER_USER_RANGE` —— 拿不到 `getUserId` 时用它把 uid 折成用户 id。 */
        const val PER_USER_RANGE = 100_000

        /** 小布快捷指令的包名。 */
        const val SHORTCUT_PKG = "com.coloros.shortcuts"

        /**
         * ColorOS 快捷指令的触发 action —— 各版本用过好几套名字（旧的是 `oplus` 命名空间）。
         * 见 [runShortcut]：先按「有接收者」筛，筛不出来才全部各发一次。
         */
        val SHORTCUT_ACTIONS = listOf(
            "com.coloros.shortcuts.action.EXECUTE_SHORTCUT",
            "com.oplus.shortcuts.action.EXECUTE_SHORTCUT",
        )

        /** 指令 id 在广播里的 extra key —— 各版本用过这两个名字，两条都带。 */
        val SHORTCUT_ID_KEYS = listOf("shortcut_id", "shortcutId")

        /** [startOplusFeature] 的尝试顺序：true = 前台起法。 */
        val FOREGROUND_FIRST = listOf(true, false)
        val SERVICE_FIRST = listOf(false, true)

        /** [sendMessengerMessage] 等服务连上的上限，超时就自己解绑。 */
        const val MESSAGE_TIMEOUT_MS = 3000L
    }
}
