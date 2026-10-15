package com.lookie.opluskey.host.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.lookie.opluskey.core.log.XLog

/** 拉界面 / 发广播 / 触发外部能力这一族动作的执行器（小窗那一族见 [MiniWindowLauncher]）。 */
internal class AppLauncher(
    private val context: () -> Context?,
    /** 失败通知出口。 */
    private val onFailure: (CommandFailure) -> Unit,
) {

    /** 小窗那一族动作的执行器（`window:` 起法与「把前台收成小窗」共用一套反射降级，见 [MiniWindowLauncher]）。 */
    private val mini = MiniWindowLauncher(context, onFailure)

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
            XLog.w("intent:$action → 已作为界面拉起")
            return
        } catch (e: Throwable) {
            XLog.d("intent:$action 当界面拉不起来（${e.message}），改发广播")
        }
        try {
            // 注意：广播上**不挂** `FLAG_ACTIVITY_NEW_TASK`：那是给
            //  `startActivity` 用的，挂在 Intent 上再 `sendBroadcast` 没有任何意义（原样照抄了
            //  上面那条 startActivity 的写法）。无害但误导，去掉。
            ctx.sendBroadcast(Intent(action))
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
        val intent = AppEntry.intentOf(pm, target)
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
            //  注意：报「已卸载」之前先看一眼包还在不在：显式组件
            //  （`app:包名/Activity`）**被停用 / 组件不存在**时抛的也是 ANFE，
            //  一律报 APP_MISSING（「可能已经卸载了」）会把用户带偏 —— 包明明还在。
            //  判据与上面 `intent == null` 那一支同源（`getApplicationInfo` 查得到就是还在）。
            val pkg = target.substringBefore('/')
            val installed = try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e2: Throwable) {
                false
            }
            XLog.w("拉起界面失败：$target（这个包还在=$installed）", e)
            onFailure(if (installed) CommandFailure.APP_NO_ENTRY else CommandFailure.APP_MISSING)
        } catch (e: Throwable) {
            // 最常见的是后台启动限制 / 安全异常，不是「已卸载」—— 报「没执行成功」别误导
            XLog.w("拉起界面失败：$target", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * `window:包名` —— 用小窗打开（有现成任务就复用，没有才新建一份）。
     *
     *  实现整族搬到了 [MiniWindowLauncher]（`window:` 起法、`zoom:mini` 的两档、ColorOS 小窗反射、
     *  几何验收与动画路线，连同它专用的常量与 `MiniAnimMode`）。本类只留转发口。
     */
    fun launchAppInWindow(target: String) = mini.launchAppInWindow(target)

    /** 把**前台**应用收成小窗（`zoom:mini` 那两档）；[collapsed] = 收起档。实现见 [MiniWindowLauncher]。 */
    fun putForegroundInMiniWindow(collapsed: Boolean = false) = mini.putForegroundInMiniWindow(collapsed)

    // `app:` / `window:` 共用的入口解析已挪到 `AppEntry.intentOf` —— 与「包名 → 入口」的
    //  `AppEntry.resolve` 是同一件事的两级，放一处；本类与小窗那一侧都调它。

    /**
     * 执行 ColorOS 小布快捷指令（`com.coloros.shortcuts`）。
     * 早先「三套通道逐个试、哪个成了算哪个」的判据不成立（`sendBroadcast` 是 fire-and-forget，
     * 没接收者也不抛异常，第一条永远 ok、后两套根本不会被尝试）。现在先问系统哪些 action
     * 真有接收者、**只发第一个命中的那条**（避免同一条指令被执行两次），一条都查不到再全部试
     * （非导出接收器对跨 uid 查询不可见）；日志只说「已发」这一步的事实，指令有没有被执行是小布自己的事。
     */
    fun runShortcut(shortcutId: String) {
        if (shortcutId.isBlank()) return
        val ctx = context() ?: run {
            XLog.w("没有 Context，shortcut:$shortcutId 跳过")
            return
        }

        //  注意：**只发一条**：两条 action 都命中时会各发一遍，而同一个
        //  接收器往往同时监听这两条 action ⇒ 指令被执行两次（注释里「避免同一条指令被执行两次」
        //  正好写反了）。既然已经问出「谁有接收者」，就只发给第一个命中的那个。
        //  一条都没查到（非导出接收器对跨 uid 查询不可见）时退回「全部各试一次」——
        //  那种情况下不知道哪条才对，宁可重复也不能不发。
        val resolved = SHORTCUT_ACTIONS.filter { hasReceiver(ctx, it) }
        val channels = if (resolved.isNotEmpty()) {
            listOf(resolved.first())
        } else {
            XLog.w("小布快捷指令：没查到任何接收者（可能是非导出接收器），按全部通道各试一次")
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
     * 这类服务没有 `onStartCommand`，`startService` 对它们是空操作（实测：
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
                // 服务一直连不上就自己收尾，别把连接挂着。
                //  注意：还要**说出来**：只 `unbindService` 的话既不记日志
                //  也不报失败 —— 用户看到的是「按下去什么都没发生」，而日志里一个字都没有。
                if (!handled) {
                    XLog.w("oplus:$feature 的 Messenger 服务 ${MESSAGE_TIMEOUT_MS}ms 没连上，解绑放弃")
                    runCatching { ctx.unbindService(connection) }
                    onFailure(CommandFailure.UNKNOWN)
                }
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
