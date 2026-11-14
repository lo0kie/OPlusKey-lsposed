package com.lookie.opluskey.host.action.app

import android.app.ActivityOptions
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ZoomWindowWire

/**
 * 起一次**快捷方式**（`appshortcut:` 那一族共用的唯一出口）。
 *
 * ## 两条起法，优先级不是随便定的
 *
 *  1) **自发 PendingIntent**（`getShortcutIntent` → `pending.send`）—— 本仓唯一能过 BAL 的起法。
 *     发送时用 `setPendingIntentBackgroundActivityStartMode` 显式声明「允许这次 PI 起后台活动」，
 *     ATMS 日志里读作 `BAL_ALLOW_PERMISSION`。
 *  2) **系统代发**（`LauncherApps.startShortcut`）—— 被 `BAL_BLOCK` 拦时**调用侧完全看不出来**
 *     （返回 `void`、`result code=102`）。只当 1) 拿不到 PI 时的兜底，**不能当主路**：
 *     真机上它常常整发不落地（表里连任务都不会出现），而 PI 那一发 0.7 秒就进表。
 *     取证见 `docs/features/zoom-window/notes.md` 的「快捷方式以小窗打开」一节。
 *
 * ## 窗口模式由这里带上
 *
 *  PI 只带 BAL 开关、不带窗口模式时，那一发必然先起成**全屏**，再靠「收」补救
 *  （屏幕会先闪一屏全屏）。两个 key 都在同一个 `ActivityOptions` bundle 里，一起塞进去即可：
 * 服务端读 `android.activity.windowingMode == 100` 决定这次是不是小窗启动。
 *
 *  注意 `ActivityOptions.toBundle()` 输出的 key 就是点号形态（[ZoomWindowWire]），
 *  与 `extra_window_mode`（下划线形态、直接递给 `OplusZoomWindowManager` 那个）**不是同一个**。
 */
internal object ShortcutStarter {
    /** 尺寸档：展开的普通小窗（服务端 `setUserPositionInfo`）。 */
    fun zoomOptions(): Bundle =
        Bundle().apply {
            putInt(ZoomWindowWire.LAUNCH_WINDOWING_MODE_KEY, ZoomWindowWire.WINDOWING_MODE_ZOOM)
            putInt(ZoomWindowWire.ZOOM_LAUNCH_FLAGS_KEY, ZoomWindowWire.LAUNCH_FLAG_EXPANDED)
        }

    /**
     * 一次「起这条快捷方式」的请求 —— 把 `pkg` / `shortcutId` / `userId` 与那份 options 捆在一起。
     *
     *  四样东西要原样穿过 [send] → `viaPendingIntent` / [viaSystem] 三层，而且 `label()` 是它们
     *  共同派生出来的一句话：摊成四个参数就是每层重抄一遍顺序、每层再拼一次那句话。
     */
    class Request(
        val pkg: String,
        val shortcutId: String,
        val userId: Int,
        /** 想要小窗就给 [zoomOptions]，要全屏给 `null`。 */
        val windowOptions: Bundle?,
    ) {
        fun label(): String = "$pkg/$shortcutId（user=$userId）"
    }

    /**
     * 一发起法的结果 —— **「没发出去」要能分出是哪一种**。
     *
     *  两种失败对用户的含义完全不同：`NO_SERVICE` 是这台机器上取不到 `LauncherApps`（环境问题，
     *  换哪条快捷方式都一样），`REJECTED` 才是「这条快捷方式起不来了」（应用自己删了 / 服务端拒）。
     *  混成一个 `false` 时调用方只能统一报「这个应用没有可启动的 Activity」——
     *  环境问题被说成应用没有界面，用户会去重装应用。
     */
    enum class Result {
        /** 这一发交出去了（不代表界面起来了，见 [send]）。 */
        SENT,

        /** 取不到 `LauncherApps` —— 根本没走到发那一步。 */
        NO_SERVICE,

        /** 两种起法都没成（PI 拿不到 + 系统代发抛错）。 */
        REJECTED,
    }

    /**
     * 起一次。
     *
     * @param request 见 [Request]；`windowOptions` 非 `null` 时会把窗口模式那对 key
     *   合并进 BAL 那份 bundle 一起送给 PI。
     * @return 这一发**发出去了没有**（[Result]）。**不代表界面起来了**（两种起法都是
     *   fire-and-forget，窗口模式更是服务端说了算）—— 判据一律走调用方那次查表验收。
     */
    fun send(
        ctx: Context,
        request: Request,
    ): Result {
        val launcherApps =
            launcherAppsOf(ctx) ?: run {
                XLog.w("拿不到 LauncherApps，appshortcut:${request.label()} 跳过")
                return Result.NO_SERVICE
            }
        if (viaPendingIntent(ctx, launcherApps, request)) return Result.SENT
        return if (viaSystem(launcherApps, request)) Result.SENT else Result.REJECTED
    }

    /**
     * 系统代发那一档（会被 BAL 拦，见类注释）。只当 PI 拿不到时走。
     *
     * @return false = 连调用都没成（最常见是快捷方式已被应用自己删掉 —— 动态快捷方式随登录状态变）。
     */
    private fun viaSystem(
        launcherApps: LauncherApps,
        request: Request,
    ): Boolean =
        try {
            launcherApps.startShortcut(
                request.pkg,
                request.shortcutId,
                null,
                request.windowOptions,
                AppCloneCatalog.handleOf(request.userId),
            )
            XLog.w("快捷方式（系统代发${request.windowOptions?.let { SAME_AS_ZOOM } ?: ""}）→ ${request.label()}")
            true
        } catch (e: Throwable) {
            // 报「没有入口」而不是「已卸载」：包明明还在，是那条快捷方式没了。
            // 畸形 options 抛的是 `IllegalArgumentException`（服务端 `ActivityOptions.fromBundle`），一并收在这里。
            XLog.w("起快捷方式失败：${request.label()}", e)
            false
        }

    /**
     * 取快捷方式那条 **PendingIntent**，自己发一次。
     *
     *  `getShortcutIntent` 是系统给桌面用的那一档：把「这一条快捷方式怎么起」封成一个 PI，
     *  由调用方自己发 —— **自己发**才拿得到 `setPendingIntentBackgroundActivityStartMode`。
     *
     *  `ShortcutInfo.getIntent()` 走不通：真机（PLK110 / Android 16）上支付宝、微信那几条动态
     *  快捷方式取回来是 `null`，所以只能走 PI。
     *
     *  @return 发出去了没有（`false` 时调用方会退系统代发）
     */
    @Suppress("DEPRECATION")
    private fun viaPendingIntent(
        ctx: Context,
        launcherApps: LauncherApps,
        request: Request,
    ): Boolean =
        try {
            val pending =
                launcherApps.getShortcutIntent(
                    request.pkg,
                    request.shortcutId,
                    null,
                    AppCloneCatalog.handleOf(request.userId),
                ) ?: return false
            pending.send(ctx, 0, null, null, null, null, balOptions(request.windowOptions))
            XLog.w("快捷方式（自发 PendingIntent${request.windowOptions?.let { SAME_AS_ZOOM } ?: ""}）→ ${request.label()}")
            true
        } catch (e: Throwable) {
            XLog.w("自发快捷方式 PendingIntent 失败（退系统代发）：${request.label()}", e)
            false
        }

    /** 日志里那句「带了小窗 options」—— 两处共用同一个文案，免得一条有、一条没有。 */
    private const val SAME_AS_ZOOM = "，带小窗 options"

    /**
     * BAL 那份 `ActivityOptions`，把 [windowOptions] 的窗口模式合并进去。
     *
     *  两档常量按系统版本选：34/35 只有 `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`，
     *  **36 起它被标成废弃**（换成语义更细的 `ALLOW_ALWAYS` / `ALLOW_IF_VISIBLE`）。
     *  我们要的是「用户刚按了键，就该起来」⇒ 新版取 `ALLOW_ALWAYS`。旧常量必须留着
     *  （34/35 上没有替代品），所以整块压掉 `DEPRECATION`。
     */
    @Suppress("DEPRECATION")
    private fun balOptions(windowOptions: Bundle?): Bundle {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val mode =
                if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
            options.setPendingIntentBackgroundActivityStartMode(mode)
        }
        val bundle = options.toBundle()
        // 窗口模式那对 key 与 BAL 那个开关在同一个 bundle 里、互不冲突：
        // ATMS 按 key 逐个读，各读各的。
        windowOptions?.let { bundle.putAll(it) }
        return bundle
    }

    private fun launcherAppsOf(ctx: Context): LauncherApps? =
        try {
            ctx.getSystemService(LauncherApps::class.java)
        } catch (e: Throwable) {
            XLog.w("取 LauncherApps 失败", e)
            null
        }
}
