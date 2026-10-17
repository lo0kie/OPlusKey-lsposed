package com.lookie.opluskey.host.action

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ZoomWindowWire
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_VERIFY_MS
import com.lookie.opluskey.host.action.MiniWindowFacts.SHORTCUT_VERIFY_RETRIES

/**
 * `appshortcut:包名/快捷方式id` 的执行 —— 起一个**应用自己注册的**快捷方式（扫一扫 / 收付款）。
 *
 * 只能走 `LauncherApps.startShortcut`：快捷方式那条 intent 里往往只有 action 与
 * `PersistableBundle`（微信就是 `ShortCutDispatchAction` + `LaunchType=launch_type_scan_qrcode`），
 * 按普通 intent 起会把参数丢掉、退化成「打开应用」。
 *
 * 权限：`getShortcuts` 与 `startShortcut` 在系统侧都过 `ensureShortcutPermission`，非桌面应用
 * 要靠 `ACCESS_SHORTCUTS`（宿主声明且已 grant，真机实测过）—— 拿不到就是 `SecurityException`，
 * 这里如实报「没有入口」，不假装成功。
 *
 * ## 小窗那一档（`appshortcut:w:…`）为什么不自己拼 intent
 *
 *  真机实测：宿主拿 `ShortcutInfo.getIntent()` 回来是 **null**（微信的扫一扫就是这样），
 *  所以「先把 intent 捞出来再交给小窗配方」这条路的地基不成立 —— 那版就是在这里静默退回普通起法的。
 *
 *  走的是 **`startShortcut` 自己的 `Bundle startActivityOptions` 那一档**：把小窗的窗口模式写进
 *  ActivityOptions 一起交上去，由系统自己带到柔性窗口那一层 ——
 *  `FlexibleWindowManagerService#startShortcut(Bundle, Intent[], Runnable)` 的**唯一调用方**
 *  就是 ATMS 里那条 `startShortcut`，它在 system_server 里自己取得出 intent。
 *  于是宿主既不用碰那条 intent，也不用为了这件事绕一趟注入侧。
 *
 * 单独一个类而不是挂进 [AppLauncher]：那个类已经贴着 detekt 的函数数上限，而这一档与它
 * 那些「拉起界面 / 发广播」的方法没有共用状态（只借 Context、小窗执行器与失败出口）。
 */
internal class AppShortcutLaunch(
    private val context: () -> Context?,
    /** 小窗那一档的验收与兜底（`appshortcut:w:…`）—— 与 `window:包名` 共用同一套判据。 */
    private val mini: MiniWindowLauncher,
    private val onFailure: (CommandFailure) -> Unit,
) {
    /** @param window true = 用小窗起（`appshortcut:w:…`） */
    fun launch(
        pkg: String,
        shortcutId: String,
        userId: Int,
        window: Boolean,
    ) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，appshortcut:$pkg/$shortcutId 跳过")
                return
            }
        if (window) {
            // 带小窗 options 发一次，发出去就算接手 —— 窗口成不成由 [verifyThenFallback] 接着判。
            //  发不出去（`LauncherApps` 拿不到 / 调用抛）才当场退普通起法。
            if (startWith(ctx, pkg, shortcutId, userId, zoomStartOptions())) {
                verifyThenFallback(ctx, pkg, shortcutId, userId)
                return
            }
            XLog.w("带小窗 options 起快捷方式没走通，退普通起法：$pkg/$shortcutId（user=$userId）")
        }
        startPlain(ctx, pkg, shortcutId, userId)
    }

    /**
     * 带 **ActivityOptions** 起一次快捷方式。
     *
     * @param options 交上去的 `startActivityOptions`；小窗那一档见 [zoomStartOptions]
     * @return 这次调用**发出去了没有**。**不代表窗口已经是小窗** —— 窗口模式是服务端说了算，
     *   判据一律走 [verifyThenFallback] 里那次 `windowingMode` 验收
     *   （返回码与截图都会骗人，见 `docs/device-findings.md` §17.6.1）。
     */
    private fun startWith(
        ctx: Context,
        pkg: String,
        shortcutId: String,
        userId: Int,
        options: Bundle?,
    ): Boolean {
        val launcherApps = launcherAppsOf(ctx) ?: return false
        return try {
            launcherApps.startShortcut(pkg, shortcutId, null, options, AppCloneCatalog.handleOf(userId))
            XLog.w("快捷方式 → 已按小窗 options 发出：$pkg/$shortcutId（user=$userId）")
            true
        } catch (e: Throwable) {
            // 服务端会把这份 options 转成 `ActivityOptions.fromBundle`，畸形时抛的是
            //  `IllegalArgumentException`（不是 `SecurityException`）—— 两条都收在这里，交给调用方降级。
            XLog.w("带 options 起快捷方式失败：$pkg/$shortcutId", e)
            false
        }
    }

    /**
     * 小窗那一档的 `ActivityOptions`。
     *
     *  两个键都是**平台事实**（`ActivityOptions.toBundle()` 自己写的就是这两个名字），收在
     *  [ZoomWindowWire] 里与「原地收成小窗」那条共用 —— 那边是直接塞进
     *  `OplusActivityTaskManager` 的 Bundle，这边是当 ActivityOptions 交上去，**取值必须一致**。
     *
     *  尺寸档给**展开**（[ZoomWindowWire.LAUNCH_FLAG_EXPANDED]）：收起档是右上角那条最小化窗，
     *  「按一下侧键只拿到一条小窄条」不是这个动作的语义。
     */
    private fun zoomStartOptions(): Bundle =
        Bundle().apply {
            putInt(ZoomWindowWire.LAUNCH_WINDOWING_MODE_KEY, ZoomWindowWire.WINDOWING_MODE_ZOOM)
            putInt(ZoomWindowWire.ZOOM_LAUNCH_FLAGS_KEY, ZoomWindowWire.LAUNCH_FLAG_EXPANDED)
        }

    /**
     * 普通起法（全屏）—— 也是小窗没走通时的退路。
     *
     * 不加 options，让系统按默认窗口模式起。**不退成「打开主应用」**：这条走的仍是
     * `startShortcut`，快捷方式那条 intent 与它的参数由系统自己取，参数不会丢。
     */
    private fun startPlain(
        ctx: Context,
        pkg: String,
        shortcutId: String,
        userId: Int,
    ) {
        val launcherApps = launcherAppsOf(ctx)
        if (launcherApps == null) {
            XLog.w("拿不到 LauncherApps，appshortcut:$pkg/$shortcutId 跳过")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        try {
            launcherApps.startShortcut(pkg, shortcutId, null, null, AppCloneCatalog.handleOf(userId))
            XLog.w("快捷方式 → $pkg/$shortcutId（user=$userId）")
        } catch (e: Throwable) {
            // 最常见的是快捷方式已被应用自己删掉（动态快捷方式会随登录状态变）——
            //  报「没有入口」而不是「已卸载」：包明明还在。
            XLog.w("起快捷方式失败：$pkg/$shortcutId（user=$userId）", e)
            onFailure(CommandFailure.APP_NO_ENTRY)
        }
    }

    private fun launcherAppsOf(ctx: Context): LauncherApps? =
        try {
            ctx.getSystemService(LauncherApps::class.java)
        } catch (e: Throwable) {
            XLog.w("取 LauncherApps 失败", e)
            null
        }

    /**
     * 交出去之后的一次验收 —— **窗口模式只能这样判**：`startShortcut` 返回 `void`，
     * 「调用没抛」只说明请求发出去了。
     *
     * 三档，区别在「读到 -1」这一档：
     *
     *  · 任务在、且是小窗 ⇒ 成；
     *  · **连任务都没有** ⇒ 应用还在冷启动，再等一轮（[SHORTCUT_VERIFY_RETRIES]）——
     *    这时候判失败会让下面再起一次全屏，把刚开好的小窗顶掉（真机症状：小窗闪一下就没了）；
     *  · 任务在、但不是小窗 ⇒ 服务端确实没吃这份 options，退普通起法。
     *
     * 代价是「真的不支持小窗」时要多等那几轮；换的是不会把已经开好的小窗顶掉。
     */
    private fun verifyThenFallback(
        ctx: Context,
        pkg: String,
        shortcutId: String,
        userId: Int,
        attempt: Int = 0,
    ) {
        mini.postDelayedGuarded("appshortcut:$pkg/$shortcutId 的小窗验收（第 ${attempt + 1} 次）", MINI_VERIFY_MS) {
            val taskId = mini.foregroundTaskFor(pkg)
            if (taskId > 0 && mini.isTaskInZoomWindow(taskId)) {
                XLog.w("快捷方式 → 小窗 $pkg/$shortcutId（user=$userId，任务 #$taskId）")
            } else if (taskId <= 0 && attempt < SHORTCUT_VERIFY_RETRIES) {
                XLog.w("小窗验收：$pkg 还没有任务（冷启动中），再等一轮")
                verifyThenFallback(ctx, pkg, shortcutId, userId, attempt + 1)
            } else {
                XLog.w("小窗 options 没被吃住（任务 #$taskId），退普通起法：$pkg/$shortcutId")
                startPlain(ctx, pkg, shortcutId, userId)
            }
        }
    }
}
