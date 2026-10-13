package com.lookie.opluskey.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
        val intent = if (target.contains('/')) {
            // `app:包名/.Activity` 是相对写法，必须自己把包名拼回去：setClassName /
            // ComponentName 不会展开相对类名（那是 createRelative 的事），照原样传进去就是
            // 「组件不存在」。全限定名（含 `.` 的）原样用。
            val pkg = target.substringBefore('/')
            val raw = target.substringAfter('/')
            val cls = if (raw.startsWith(".") || '.' !in raw) "$pkg.$raw" else raw
            Intent().setClassName(pkg, cls)
        } else {
            pm.getLaunchIntentForPackage(target)
        }
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
        } catch (e: Throwable) {
            XLog.w("拉起界面失败：$target", e)
            onFailure(CommandFailure.APP_MISSING)
        }
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
     * 小布识屏：KSU `am start-foreground-service` 的原生实现 ——
     * 配置里仍写 KSU 那串命令，执行时直接 `startForegroundService`，不需 shell / root。
     */
    fun startSidebar() {
        val ctx = context() ?: run {
            XLog.w("没有 Context，小布识屏 跳过")
            return
        }
        try {
            ctx.startForegroundService(
                Intent(KsuCompat.SIDEBAR_SERVICE)
                    // 必须显式指定组件：Android 8+ 禁止隐式服务，且该 action 的实现在
                    // com.coloros.colordirectservice/.InvokeEntranceService（真机 dumpsys 实测）
                    .setComponent(
                        ComponentName(KsuCompat.SIDEBAR_PKG, KsuCompat.SIDEBAR_CLASS),
                    )
                    .putExtra("extra_entrance_function", "full_screen_ocr")
                    .putExtra("triggered_app", KsuCompat.SIDEBAR_TARGET_APP),
            )
            XLog.i("小布识屏：已 startForegroundService(${KsuCompat.SIDEBAR_SERVICE})")
        } catch (e: Throwable) {
            XLog.w("拉起小布识屏失败（${KsuCompat.SIDEBAR_SERVICE}）", e)
        }
    }

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
    }
}
