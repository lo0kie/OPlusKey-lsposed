package com.lookie.opluskey.host.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.bridge.AppCloneCatalog
import com.lookie.opluskey.bridge.AppEntry
import com.lookie.opluskey.bridge.FlexibleWindowBridge
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.host.action.MiniWindowFacts.EXPANDED_CONFIG_BOUNDS
import com.lookie.opluskey.host.action.MiniWindowFacts.EXPANDED_SCALE
import com.lookie.opluskey.host.action.MiniWindowFacts.HOME_SETTLE_MS
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_ANIM_AFTER_CONVERT_DELAY_MS
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_ANIM_DURATION_MS
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_ANIM_FINISH_ALPHA
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_ANIM_START_ALPHA
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_ANIM_START_SCALE
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_SKIP_UNRESIZEABLE
import com.lookie.opluskey.host.action.MiniWindowFacts.MINI_VERIFY_MS
import com.lookie.opluskey.host.action.MiniWindowFacts.OPT_LAUNCH_WINDOWING_MODE
import com.lookie.opluskey.host.action.MiniWindowFacts.OPT_ZOOM_LAUNCH_FLAGS
import com.lookie.opluskey.host.action.MiniWindowFacts.RECENTS_FLEXIBLE_ARGS
import com.lookie.opluskey.host.action.MiniWindowFacts.RECENTS_FLEXIBLE_METHOD
import com.lookie.opluskey.host.action.MiniWindowFacts.RESIZE_MODE_FIELD
import com.lookie.opluskey.host.action.MiniWindowFacts.RESIZE_MODE_UNRESIZEABLE
import com.lookie.opluskey.host.action.MiniWindowFacts.ROM
import com.lookie.opluskey.host.action.MiniWindowFacts.START_DELIVERED_TO_TOP
import com.lookie.opluskey.host.action.MiniWindowFacts.TOP_APP_ACTIVITY_FIELD
import com.lookie.opluskey.host.action.MiniWindowFacts.TOP_APP_CLASS_FIELD
import com.lookie.opluskey.host.action.MiniWindowFacts.TOP_APP_INFOS_METHOD
import com.lookie.opluskey.host.action.MiniWindowFacts.TOP_APP_PACKAGE_FIELD
import com.lookie.opluskey.host.action.MiniWindowFacts.TOP_APP_TASK_ID_FIELD
import com.lookie.opluskey.host.action.MiniWindowFacts.TOP_APP_WINDOWING_MODE_FIELD
import com.lookie.opluskey.host.action.MiniWindowFacts.USE_FORGED_ANIMATION_ROUTE
import com.lookie.opluskey.host.action.MiniWindowFacts.WINDOWING_MODE_ZOOM
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_ARGS
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_CONFIG_WIDTH_MIN_RATIO
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_EXTRA_WINDOW_MODE
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_LAUNCH_FLAG_FULL_TO_MINI
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_LAUNCH_FLAG_USER_POSITION
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_MANAGER
import com.lookie.opluskey.host.action.MiniWindowFacts.ZOOM_TASK_MANAGER

/**
 * 「小窗」这一族动作的执行器 —— 从 [AppLauncher] 按职责分出来的（原来混在那边，一个类里装了四件事）。
 *
 * 管两条路，共用同一套 ColorOS 小窗反射与几何验收：
 *  · `window:包名`（[launchAppInWindow]）：新起一份小窗，有现成任务就复用；
 *  · `zoom:mini` / `zoom:mini:collapse`（[putForegroundInMiniWindow]）：把**前台**应用收成小窗。
 *
 *  反射面全在这里（`OplusActivityManager` / `OplusActivityTaskManager` / `OplusZoomWindowManager`，
 *  都按**名字**取类，不直接引用宿主框架的私有类型）。它专用的常量在 `MiniWindowFacts` 里 ——
 *  调它们不用改注入侧，`kill` 掉宿主进程即生效。
 */
internal class MiniWindowLauncher(
    internal val context: () -> Context?,
    internal val onFailure: (CommandFailure) -> Unit,
    /**
     * 「前台转小窗时使用动画」（`GlobalConfig.miniWindowAnim`）—— 宿主进程执行时现读配置
     * （与 `ActionRunner` 读 `colorOsApi` 同一条口径：拨完开关立刻生效，不必重启）。
     */
    private val isMiniAnim: () -> Boolean = { ConfigStore.read().global.miniWindowAnim },
) {
    /**
     * `window:包名` —— 用小窗打开，**有现成任务就复用**（原地转小窗），没有才新建一份。
     *
     * 先走 ColorOS 自己的小窗接口 `android.app.OplusActivityManager#startZoomWindow`（在
     * `oplus-framework.jar` 里，属宿主框架的私有类型，所以只能按名字反射）；反射不到就退公开的
     * `ActivityOptions` 自由窗口；两条都不行再退普通打开 —— 宁可开成普通界面，也不能按下去什么都不发生。
     * 宿主持有 `MANAGE_ACTIVITY_TASKS` 与 `oplus.permission.OPLUS_COMPONENT_SAFE`，服务端两道闸门都过得去。
     */
    fun launchAppInWindow(target: String) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，window:$target 跳过")
                return
            }
        val userId = AppTarget.userId(target)
        //  **分身不能退主用户的 PM**：同包名在 0 与 998/999 里各有一份，主用户 PM 解析出来的
        //  是主用户那份 —— 于是绑在分身上的 `window:` 按下去，收成小窗的是**主应用**（真机反馈）。
        //  分身那条路只能走 binder（`createContextAsUser` 在分身用户里必失败，见 `AppClonePackages`）。
        val pm: PackageManager? = if (userId == 0) ctx.packageManager else AppCloneCatalog.packageManagerFor(ctx, userId)
        val pkgName = AppTarget.pkg(target)
        val intent =
            if (pm != null) {
                AppEntry.intentOf(pm, target)
            } else {
                //  显式给了类名就直接拼（同一个应用在两个用户下的组件名一致）；
                //  没给就按 userId 问一次入口 —— `resolveAsUser` 走 `IPackageManager`。
                val act = AppTarget.activity(target)
                if (act != null) {
                    Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkgName, act))
                } else {
                    AppEntry.resolveAsUser(pkgName, userId)
                }
            }
        if (intent == null) {
            //  分身不问主用户的 PM：同包名在 0 与 998/999 里各有一份，`getApplicationInfo` 必抛，
            //  于是好端端的分身被报成「可能已卸载」（`AppLauncher` 那条路已经踩过这一坑）。
            val installed = appInstalled(pm, pkgName, userId)
            XLog.w("解析不到界面：$target（这个包还在=$installed，user=$userId）")
            onFailure(entryFailure(installed))
            return
        }
        val pkg = intent.component?.packageName ?: AppTarget.pkg(target)

        //  目标就在前台时**不能起**：这次启动会被栈顶那份实例自己消化（`START_DELIVERED_TO_TOP`），
        //  硬起只会多出一份全屏实例。改成走 recents 那条，把它那份任务**原地**收成小窗。
        //  `window:` 要的是普通小窗（展开档），尺寸档固定给 [ZOOM_LAUNCH_FLAG_USER_POSITION]。
        //
        //  分身**不做这一档**：`foregroundTaskFor(pkg)` 按包名比，而主用户与分身**同包名**
        //  —— 分身开着时按侧键，它会把主用户那份任务当成「目标已在后台的任务」收成小窗。
        //  宁可直接起一次（服务端自己会复用该用户下已有的任务），也不要收错人。
        //  真要做对得给 `OplusAppInfo` 找一个 userId 字段，那个没在真机上验过（见 `MiniWindowFacts`）。
        val foregroundTask = if (userId == 0) foregroundTaskFor(pkg) else -1
        if (userId != 0) {
            XLog.w("window:$target 是分身（user=$userId）⇒ 跳过「原地收成小窗」那一档，直接起")
        }
        if (foregroundTask > 0 && startFlexibleFromRecents(foregroundTask, ZOOM_LAUNCH_FLAG_USER_POSITION)) {
            XLog.w("window:$target 已在前台，原地收成小窗（不新起实例）")
            postDelayedGuarded("window:$target 的小窗验收", MINI_VERIFY_MS) {
                // 只有**确认没成小窗**才回桌面重起。读不到窗口信息时重起，会把刚刚收好的
                //  小窗顶掉（还多跑一趟「回桌面」，屏幕上就是「闪一下换了一份」）。
                when (zoomStateOf(foregroundTask)) {
                    true -> XLog.w("window:$target 原地收成小窗成功（任务 #$foregroundTask）")
                    null -> XLog.w("window:$target 读不到任务 #$foregroundTask 的窗口信息，不动它")
                    false -> retryAfterHome(ctx, intent, target)
                }
            }
            return
        }

        //  其余情况只加 `NEW_TASK`：目标已有任务会被服务端**复用**并原地转成小窗，没有才新建一份。
        //  别加 `MULTIPLE_TASK` —— 那会强制新建，同一个应用被按几次就多出几份实例。
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val code = startZoomWindowCode(ctx, intent, userId)
        if (code != null && code >= 0) {
            if (code == START_DELIVERED_TO_TOP) retryAfterHome(ctx, intent, target)
            return
        }
        if (startFreeform(ctx, intent)) return
        XLog.w("window:$target 的小窗两条路都没走通，退成普通打开")
        try {
            // 主用户与分身都走 ATMS（理由同 `AppLauncher.launchApp`：装了分身的应用在主用户下
            //  也会被问「用哪一份」，只有这条路上能挂 skip-chooser 位）。
            if (!AppLaunchAsUser.start(ctx, intent, userId)) error("启动被拒")
        } catch (e: Throwable) {
            XLog.w("小窗与普通打开都失败：$target（user=$userId）", e)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 这次启动被栈顶那份实例消化了：先把任务挪出栈顶，再起一次（仍是原地转，不新建）。 */
    internal fun retryAfterHome(
        ctx: Context,
        intent: Intent,
        target: String,
    ) {
        XLog.w("window:$target 的界面占着前台，先回桌面再起小窗")
        try {
            ctx.startActivity(homeIntent())
        } catch (e: Throwable) {
            // 回桌面这一步失败（`SecurityException` / `ActivityNotFoundException`）不该带走后面那次重起：
            // 这条路径同步跑在 `launchAppInWindow` 里，漏出去就是崩宿主
            XLog.w("window:$target 回桌面失败：${e.javaClass.simpleName}: ${e.message}")
        }
        postDelayedGuarded("window:$target 的回桌面后重起", HOME_SETTLE_MS) {
            // 注意：这一步的成败**必须看**：回桌面重起没成功时用户看到的是
            //  「按下去先回了桌面、然后什么都没发生」，而日志里一句都没有 —— 只能靠猜。
            if (!startZoomWindow(ctx, intent, AppTarget.userId(target))) {
                XLog.w("window:$target 回桌面后重起小窗也没成功（放弃，不再退）")
                onFailure(CommandFailure.APP_NO_MINI_WINDOW)
            }
        }
    }

    /**
     * 主线程 `postDelayed` 的**保护壳**。
     *
     * 回调体跑在宿主 `:ui` 主线程的 Looper 上，一旦抛出（`startActivity` 的 `SecurityException` /
     * `ActivityNotFoundException` 是最常见的两种），异常直达 Looper ⇒ **宿主进程崩**。同步路径
     * 都有 `try/catch` 兜底，异步路径同样必须有 —— 这不是「保护宿主系统进程」那种 guard，
     * 丢的只是这一下没做成的收尾动作（[what] 会写清是哪一环）。
     */
    internal fun postDelayedGuarded(
        what: String,
        delayMs: Long,
        block: () -> Unit,
    ) {
        Handler(Looper.getMainLooper()).postDelayed(
            {
                try {
                    block()
                } catch (e: Throwable) {
                    XLog.w("$what 失败（延迟回调已忽略）：${e.javaClass.simpleName}: ${e.message}")
                }
            },
            delayMs,
        )
    }

    /**
     * `zoom:mini` / `zoom:mini:collapse` —— 把**前台**那个应用收成小窗，**原地收**，不另起实例。
     *
     * 尺寸档（[Action.MiniWindow.collapsed]）决定走哪条：
     *
     *  · **展开档**（默认）：**原地收**（应用侧 recents 正门 + `mZoomLaunchFlags = 2`、
     *    **不改显示方向** ⇒ 横屏应用拿到横屏帧的比例）。「前台转小窗时使用动画」打开时，
     *    收成之后紧接着让框架自己的动画器播那一段缩小动画（[animateIntoMini]）。
     *    另有一条「伪造成柔性全屏态 + `switchState`」的路，窗口只是个半成品，默认关着 ——
     *    见 [USE_FORGED_ANIMATION_ROUTE]。
     *  · **收起档**：recents 正门配 `mZoomLaunchFlags = 32`（服务端 `fullToMini` 分支）——
     *    直接开成右上角那条最小化窗。这条**不**走 system_server：`switchState` 只会切成
     *    `setUserPositionInfo` 那档（展开），没有收起档的入口。
     *
     * **收不起来就什么都不做，只弹一条提示**。判据是「服务端没受理」
     * 或「受理了但没出小窗」—— 有些应用（真机实证：`com.heytap.market`）就是收不起来。
     * 注意：**不许再退「按包名另起一份实例」**：那条会先把界面挪出栈顶（回桌面）、再重新起一份，
     * 用户看到的是「一按侧键就回桌面重开」—— 比没反应更糟。另外它还会**重复调一次**
     * `startFlexibleFromRecents`（真机日志里能看到连续两条 `从 recents … 原地收成小窗`）。
     */
    fun putForegroundInMiniWindow(collapsed: Boolean = false) {
        val ctx =
            context() ?: run {
                XLog.w("没有 Context，zoom:mini 跳过")
                return
            }
        val taskId = foregroundTaskId()
        if (taskId <= 0) {
            // 连前台任务都读不到 ⇒ 无从下手。以前退「按包名另起一份实例」（= 回桌面重开），现在只提示。
            XLog.w("zoom:mini → 取不到前台任务，什么都不做")
            onFailure(CommandFailure.UNKNOWN)
            return
        }
        // 不可调尺寸的应用要不要直接不试 —— 见 [MINI_SKIP_UNRESIZEABLE]。
        if (MINI_SKIP_UNRESIZEABLE && !canEnterMiniWindow(taskId)) {
            XLog.w("zoom:mini → 前台任务 #$taskId 不可调尺寸，按 $MINI_SKIP_UNRESIZEABLE 开关不试")
            return
        }
        if (collapsed) {
            // 收起档没有动画那条配方：框架那支动画只有「展开档」的落点与缩放
            putInCollapsedMini(taskId)
            return
        }
        if (!USE_FORGED_ANIMATION_ROUTE) {
            // 「前台转小窗时使用动画」打开时走**先真转换、再播框架动画**那条（[animateIntoMini]）。
            // 它没接手（当前不是竖屏）就落到下面的原地收 —— 用户至少还能进小窗。
            if (isMiniAnim() && animateIntoMini(ctx, taskId)) return
            // 默认档：**recents 正门原地收**（`startActivityFromRecentsForFlexible` + flags=2）。
            // 这是「进得去小窗」的那条 —— 它不改显示方向，窗口完整，代价只是没有动画。
            // 注意：它有一个已知的几何缺陷：目标本身是全屏任务时，服务端会走
            // `calcDefaultLaunchBounds` 里那条「current is fullscreen task」分支，把全屏那套尺寸
            // 当成小窗配置矩形，横屏应用会因此**撑出屏幕**（`device-findings.md` 附十三）。
            // 试过换成「launch 配方」主路来修它：能进、几何也在屏内，但那条要
            // **先回桌面**，而桌面是竖屏 ⇒ 设备被拽回竖屏、横屏应用拿到**竖屏比例**。
            // 两条路的取舍未收敛，见 `docs/zoom-window.md` §13.1；**现在按「进得去」优先**。
            XLog.w("zoom:mini → 走 recents 正门（原地收、无动画）")
            retryViaRecents(taskId)
            return
        }
        putInMiniViaForgedAnimation(ctx, taskId)
    }

    /**
     * 「前台转小窗时使用动画」那一档：**先真转换、再播框架自己的缩小动画**。
     *
     * 这里**不是「自己画动画」** —— 插值、矩阵、`apply()`、以及那条 spring 全是框架的
     * `FlexibleWindowAnimator` 在跑，我们只交起止参数（`FlexibleWindowBridge.CMD_MINI_ANIM`，
     * 配方与三道坑见 `docs/zoom-window.md` §十二）。
     *
     * 为什么顺序只能是「先转换、后动画」：动画器每帧第一句是
     * `isFlexibleTaskAndHasCaption(task)`，普通全屏任务**每帧都被挡回去** ——
     * spring 在跑、画面纹丝不动（§12.3 第 2 条）。
     *
     * 为什么不是「回桌面 + 真启动一次」：那条要先把界面挪出栈顶，而桌面是竖屏 ⇒
     * 设备被拽回竖屏、横屏应用拿到竖屏比例的落点，目标在前台时还常常压根起不来
     * （§13.1）。现行主路原地收、不改显示方向。
     *
     * @return true = 已经接手（转换没成也已在 [retryViaRecents] 里按失败报过，调用方不要再退别的路）
     */
    internal fun animateIntoMini(
        ctx: Context,
        taskId: Int,
    ): Boolean {
        if (!animGeometryMatchesOrientation(ctx)) {
            XLog.w(
                "zoom:mini → 当前不是竖屏，动画的终点几何（竖屏标定值）对不上 —— " +
                    "这一下只做真转换、不播动画",
            )
            return false
        }
        if (!retryViaRecents(taskId)) return true
        val params =
            FlexibleWindowBridge.MiniAnimParams(
                startRect = fullScreenRect(ctx),
                finishRect = EXPANDED_CONFIG_BOUNDS,
                startScale = MINI_ANIM_START_SCALE,
                finishScale = EXPANDED_SCALE,
                startAlpha = MINI_ANIM_START_ALPHA,
                finishAlpha = MINI_ANIM_FINISH_ALPHA,
                durationMs = MINI_ANIM_DURATION_MS,
            )
        // 转换是同步的 binder 调用，返回时任务已经是柔性任务；而动画器那条 spring 入口
        // **没有** `isInTransition` 过滤，所以可以立刻播，不必先让用户看到一个静止的小窗。
        val delay = MINI_ANIM_AFTER_CONVERT_DELAY_MS
        XLog.w("zoom:mini → 已真转换，${delay}ms 后播框架的缩小动画（起点全屏、终点小窗）")
        if (delay <= 0L) {
            FlexibleWindowBridge.sendMiniAnim(ctx, taskId, params)
        } else {
            postDelayedGuarded("动画档：转换后的动画", delay) {
                FlexibleWindowBridge.sendMiniAnim(ctx, taskId, params)
            }
        }
        return true
    }

    /**
     * 动画的终点几何是不是和当前方向对得上 —— **只有竖屏（或方屏）才播**。
     *
     * 动画的终点是 [EXPANDED_CONFIG_BOUNDS] / [EXPANDED_SCALE] 这组**竖屏标定值**
     * （PLK110 / 1272×2772 @560dpi）。横屏时系统给小窗的是另一套落点与缩放，而这两个值**不跟着变** ⇒
     * 动画会把 surface 停在一个**与真实小窗不符**的变换上。真机症状（用户报）：
     * **从横屏转过来时小窗尺寸特别大**。
     *
     * 横屏就干脆不播 —— 后面的真转换用的是**系统自己的落点**（`setUserPositionInfo`），与方向无关，
     * 所以不播动画的横屏小窗反而是对的。
     *
     * 想彻底修（横屏也能播对）：终点别用我们的常量，改成取框架自己的
     * `FlexibleTaskController#getAnimationInfo(task, 1)` —— 那是按当前方向算好的。
     * 代价是改注入侧、要重启。记在 `docs/zoom-window.md` §12.4。
     */
    internal fun animGeometryMatchesOrientation(ctx: Context): Boolean {
        val cfg = ctx.resources.configuration
        return cfg.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT ||
            cfg.screenWidthDp == cfg.screenHeightDp
    }

    /**
     * 全屏态的任务矩形 —— 动画的**起点**。框架的 `updateAnimationLayer` 只读 `left/top`，
     * 所以这里真正有用的是 `(0,0)`；宽高带上只是不让 Rect 是半个（见 §十二）。
     */
    internal fun fullScreenRect(ctx: Context): android.graphics.Rect {
        val dm = ctx.resources.displayMetrics
        return android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /**
     * 展开档的**伪造态**路：在 `system_server` 里把任务伪装成柔性全屏态，再调框架的 `switchState`。
     *
     * 换来的是原生「全屏 → 小窗」动画（§七），代价是窗口只是个**半成品** —— 真机实测：
     *
     *  · 几何是对的（落点、尺寸都对），也确实是「展开档」而不是右上角那条；
     *  · 但**没有圆角**（直角）、**没有操作条 / 没有 ⋮ 菜单**；
     *  · **点一下会被当成拖动**（整窗跟着手走），而且应用内容叠了两份（同一行标题出现两次）。
     *
     * 这四条指向同一件事：**框架的「柔性任务注册」那一步没做**。操作条视图、圆角、
     * 拖拽与输入处理都是那一步配上去的，而伪造态只捅了三个状态字段 + `switchState` ——
     * 状态位对了，外围那层壳子没人装。所以默认关掉（见 [USE_FORGED_ANIMATION_ROUTE]）。
     *
     * 落点几何一并下发：值在 `core/model/MiniWindowGeometry`（按机型标定），改完 `kill` 掉宿主进程
     * 就生效 —— 注入侧不用重编，也不用整机重启。
     */
    internal fun putInMiniViaForgedAnimation(
        ctx: Context,
        taskId: Int,
    ) {
        FlexibleWindowBridge.send(
            ctx,
            FlexibleWindowBridge.CMD_MINI,
            taskId,
            bounds = EXPANDED_CONFIG_BOUNDS,
            scale = EXPANDED_SCALE,
        )
        // 验收只看得到两件事，**别越界宣称档位**：
        //    · 是不是小窗（`windowingMode == 100`）—— 光看它会把「落成了右上角一条」当成成功；
        //    · 预置几何有没有被吃住（配置宽占比 ≈ 1.0）。
        //    但「展开 / 收起」是**分不出来的**：两档的配置矩形同为 `1272×2261`，屏幕大小只差在
        //    scale 上（见 [taskZoomConfigRatio]）。所以这里只报事实，档位一律以截图为准 ——
        // 那版在这里宣称了「已是展开档」，实际屏幕上只有 817 宽，白放过一个 bug。
        postDelayedGuarded("zoom:mini 的验收", MINI_VERIFY_MS) {
            when (val ratio = taskZoomConfigRatio(taskId)) {
                null -> {
                    XLog.w("zoom:mini → system_server 那条没走通，退回应用侧 recents 正门")
                    retryViaRecents(taskId)
                }

                in 0f..ZOOM_CONFIG_WIDTH_MIN_RATIO -> {
                    XLog.w(
                        "zoom:mini → 任务 #$taskId 的预置几何没被吃住（配置宽占比 $ratio，应 ≈1.0），" +
                            "用 recents 正门重收一次",
                    )
                    retryViaRecents(taskId)
                }

                else -> {
                    XLog.w(
                        "zoom:mini → 任务 #$taskId 已是小窗、预置几何被吃住（配置宽占比 $ratio）；" +
                            "档位以截图为准",
                    )
                }
            }
        }
    }

    /**
     * 应用侧 recents 正门收一次（原地收、无动画、**不改显示方向**）。
     *
     * 之所以让它当唯一主路：它不动方向，所以横屏应用拿到的是**横屏帧**的几何 —— 比例是对的。
     * 代价是目标本身是全屏任务时几何可能撑出屏幕（`device-findings.md` 附十三，用户判定可接受）。
     *
     * **服务端没受理就往回收手**（返回负数）—— 只提示、什么都不做。
     * 以前这里退「按包名另起一份实例」，用户看到的是「一按侧键就回桌面重开」（见 [putForegroundInMiniWindow]）。
     *
     * @return 服务端有没有受理 —— 动画档据此决定要不要接着播；失败已经在这里报过，调用方不必再报一次
     */
    internal fun retryViaRecents(taskId: Int): Boolean {
        if (startFlexibleFromRecents(taskId, ZOOM_LAUNCH_FLAG_USER_POSITION)) {
            postDelayedGuarded("recents 正门收小窗的验收", MINI_VERIFY_MS) { verifyMiniOrFallback(taskId) }
            return true
        }
        XLog.w("zoom:mini → recents 正门没受理（服务端拒了），什么都不做")
        onFailure(CommandFailure.APP_NO_MINI_WINDOW)
        return false
    }

    /**
     * 收起档：把任务原地收成右上角那条最小化窗。
     *
     * 只有 recents 正门这一条 —— 服务端凭 `mZoomLaunchFlags == 32` 走 `fullToMini` 分支，
     * 用 `getMiniFlexibleTaskInfo` 那套几何（真机约 `Rect(898,168-1203,710)` / scale `0.2397`）。
     * 收不起就报失败 —— **不退回「按包名另起一份实例」**（那会多出一份全屏实例，见 [startFlexibleFromRecents]）。
     */
    internal fun putInCollapsedMini(taskId: Int) {
        if (startFlexibleFromRecents(taskId, ZOOM_LAUNCH_FLAG_FULL_TO_MINI)) {
            postDelayedGuarded("zoom:mini:collapse 的验收", MINI_VERIFY_MS) {
                when (zoomStateOf(taskId)) {
                    true -> {
                        XLog.w("zoom:mini:collapse → 任务 #$taskId 已进入小窗态（收起/展开以截图为准）")
                    }

                    // 读不到 ≠ 没收起来：报「不支持小窗」就是拿一个读不到的事实怪应用
                    null -> {
                        XLog.w("zoom:mini:collapse → 读不到任务 #$taskId 的窗口信息，无法验收")
                    }

                    false -> {
                        XLog.w("zoom:mini:collapse → 任务 #$taskId 没变成小窗，什么都不做")
                        onFailure(CommandFailure.APP_NO_MINI_WINDOW)
                    }
                }
            }
            return
        }
        XLog.w("zoom:mini:collapse → recents 正门没受理，什么都不做")
        onFailure(CommandFailure.APP_NO_MINI_WINDOW)
    }

    /** 前台那份任务的 taskId —— `getAllTopAppInfos()` 按「最前面」排序，取第 0 项。 */
    internal fun foregroundTaskId(): Int {
        val infos = topAppInfos() ?: return -1
        val info = infos.firstOrNull() ?: return -1
        val id = Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int ?: return -1
        XLog.d("zoom:mini → 前台任务 #$id")
        return id
    }

    /**
     * 这个任务对应的应用**能不能进小窗** —— 不可调尺寸的直接别试。
     *
     * 判据：`ActivityInfo.resizeMode == RESIZE_MODE_UNRESIZEABLE`。
     * 真机实例：`com.heytap.market` —— 任务标着 `nonResizable`，框架在
     * `startFreeFormFromRecent` 里判 `invalid flexibleTaskInfo`（`mResizeMode=0`）**直接不转**
     * （`FlexibleTaskController` 只打一行 `E ... invalid flexibleTaskInfo, pls check`）。
     * 我们白跑一趟不说，还会退到「按包名另起一份实例」再失败一次 —— 用户看到的就是「按了有反应但没小窗」。
     *
     * 注意：**判不出来时返回 `true`（照旧尝试）**：拿不到 ComponentName / 查不到 `ActivityInfo` 时
     * 宁可保持原行为。这是「少做一件本来就不该做的事」，不是「拦住一个本来能做的」——
     * 判错的代价不对称，所以往宽的一边倒。
     */
    internal fun canEnterMiniWindow(taskId: Int): Boolean {
        val ctx = context() ?: return true
        val info = zoomInfoOf(taskId) ?: return true
        val top = Reflect.fieldValue(info, info.javaClass, TOP_APP_ACTIVITY_FIELD) ?: return true
        val component = componentOf(top, top.javaClass) ?: return true
        return try {
            val ai = ctx.packageManager.getActivityInfo(component, 0)
            // `ActivityInfo.resizeMode` 不在编译期 stub 里（@UnsupportedAppUsage），只能反射读 —— 与
            // 本仓其它隐藏 API 一样走 Reflect；`Reflect.fieldValue` 会沿父类往上找。
            val mode = Reflect.fieldValue(ai, ai.javaClass, RESIZE_MODE_FIELD) as? Int
            if (mode == null) {
                XLog.d("zoom:mini → 读不到 ${component.flattenToShortString()} 的 $RESIZE_MODE_FIELD（照旧尝试）")
                return true
            }
            val ok = mode != RESIZE_MODE_UNRESIZEABLE
            // 只在 D 级记事实 —— 要不要因此拦住由调用方按 [MINI_SKIP_UNRESIZEABLE] 决定，
            // 这里不能打「不进小窗」那种结论式日志（默认不拦，打了就是骗人的）。
            XLog.d("zoom:mini → ${component.flattenToShortString()} resizeMode=$mode（0 = 不可调尺寸）")
            ok
        } catch (e: Throwable) {
            XLog.d("zoom:mini → 查 ${component.flattenToShortString()} 的 ActivityInfo 失败（照旧尝试）：${e.message}")
            true
        }
    }

    /**
     * `OplusAppInfo.topActivity` → `ComponentName`（`ComponentName` / `ComponentInfo` 两种形状都认）。
     *
     * 与 [packageOf] 的区别：这里**包名与类名都要**才返回 —— 因为要用它去查 `ActivityInfo.resizeMode`，
     * 只有包名是查不到的（`resizeMode` 是 activity 级的属性）。
     */
    internal fun componentOf(
        top: Any,
        cls: Class<*>,
    ): android.content.ComponentName? {
        (top as? android.content.ComponentName)?.let { return it }
        val pkg = packageOf(top, cls) ?: return null
        val name =
            Reflect.call(top, "getClassName") as? String
                ?: Reflect.fieldValue(top, cls, TOP_APP_CLASS_FIELD) as? String
                ?: return null
        return android.content.ComponentName(pkg, name)
    }

    /** 前台上如果是这个包，返回它那份任务的 taskId；不是就 -1。 */
    internal fun foregroundTaskFor(pkg: String): Int {
        val infos = topAppInfos() ?: return -1
        val info = infos.firstOrNull() ?: return -1
        val top = Reflect.fieldValue(info, info.javaClass, TOP_APP_ACTIVITY_FIELD) ?: return -1
        if (packageOf(top, top.javaClass) != pkg) return -1
        return Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int ?: -1
    }

    /**
     * 这个包名下的**任一**任务（小窗那份优先），一份都没有才 -1。
     *
     * 与 [foregroundTaskFor] 问的不是同一件事，别合并：
     *  · [foregroundTaskFor] 问「它此刻在最前面吗」—— `window:` 那条「原地收成小窗」靠它决定要不要
     *    新起实例，目标已经在后台时就该走 recents 那条；
     *  · 本函数问「它跑起来没有、在哪一份里」—— 快捷方式的小窗验收要的是后者。
     *
     * **必须扫整张表**：`getAllTopAppInfos()` 在小窗在场时**同时**有小窗那份（`windowingMode=100`）
     * 与全屏那份（`=1`）两条（`docs/device-findings.md` §17.6.1），第 0 项是不是目标包取决于此刻
     * 谁在最前面。只认第 0 项时目标落在第二条就会被读成「还没有任务」。
     */
    internal fun taskOfPackage(
        pkg: String,
        exclude: Set<Int> = emptySet(),
    ): Int {
        val hits = topAppInfos().orEmpty().mapNotNull { taskOf(it, pkg) }.filterNot { it.first in exclude }
        // 小窗那份就是验收要找的；整张表都没有小窗时才退回任意一份（全屏那份）。
        return hits.firstOrNull { it.second == WINDOWING_MODE_ZOOM }?.first
            ?: hits.firstOrNull()?.first
            ?: -1
    }

    /**
     * 这个包名在表里的**全部** taskId —— 分身那条路要用它做「起之前 / 起之后」的差集。
     *
     *  表里没有 userId，`taskOfPackage` 只按包名比；主用户与分身同包名，所以分身只能认
     *  「这一发之后新出现的那一份」，不能按包名直接收（那会收走主应用）。
     */
    internal fun tasksOfPackage(pkg: String): Set<Int> = topAppInfos().orEmpty().mapNotNull { taskOf(it, pkg)?.first }.toSet()

    /**
     * 表里的一条 → `(taskId, windowingMode)`；不是这个包、或读不到 taskId 时 `null`。
     *
     * 单独拆出来只为让 [taskOfPackage] 留在 detekt 的圈复杂度与循环跳转阈值内。
     */
    private fun taskOf(
        raw: Any?,
        pkg: String,
    ): Pair<Int, Int?>? {
        val info = raw ?: return null
        val top = Reflect.fieldValue(info, info.javaClass, TOP_APP_ACTIVITY_FIELD) ?: return null
        val taskId = Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int
        // 包名不符与 taskId 无效是同一档「这条不是我们要的」—— 并成一次判断，别再各开一个 return。
        val mine = taskId != null && taskId > 0 && packageOf(top, top.javaClass) == pkg
        return if (mine) {
            taskId to (Reflect.fieldValue(info, info.javaClass, TOP_APP_WINDOWING_MODE_FIELD) as? Int)
        } else {
            null
        }
    }

    /** `OplusAppInfo.topActivity` 的包名 —— `ComponentName` 与 `ComponentInfo` 两种形状都认。 */
    internal fun packageOf(
        top: Any,
        cls: Class<*>,
    ): String? =
        Reflect.call(top, "getPackageName") as? String
            ?: Reflect.fieldValue(top, cls, TOP_APP_PACKAGE_FIELD) as? String

    /**
     * 表里有没有 `taskId` 这一份任务 —— 不看它在前台还是后台、是不是小窗。
     *
     * 快捷方式的「先起再收」只用这一次查询：拿到 ATMS 回的 taskId 之后，只剩「它还在不在」这一个问题。
     * 与 [taskOfPackage] 的区别是**按 id 点名**而不是按包名扫 —— 同一个包可能同时有多份任务
     * （小窗那份 + 全屏那份 + 跳板链正在起的那份），按包名扫会挑错人。
     */
    internal fun hasTask(taskId: Int): Boolean = topAppInfos().orEmpty().any { idOf(it) == taskId }

    /**
     * 一条表项 → 它的 taskId；读不到返回 -1。
     *
     * 只为 [hasTask] 服务：那边只关心「这个 id 还在吗」，不关心包名与窗口模式。
     */
    private fun idOf(raw: Any?): Int {
        val info = raw ?: return -1
        val id = Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int ?: return -1
        return if (id > 0) id else -1
    }

    /** `OplusActivityManager#getAllTopAppInfos()` 的原始列表；拿不到一律返回 null（调用方据此降级）。 */
    internal fun topAppInfos(): List<*>? {
        val cls =
            Reflect.findClass(javaClass.classLoader, ZOOM_MANAGER) ?: run {
                XLog.w("没有 $ZOOM_MANAGER，topAppInfos 取不到")
                return null
            }
        val manager =
            Reflect.callStatic(cls, "getInstance") ?: run {
                XLog.w("$ZOOM_MANAGER#getInstance 返回 null，topAppInfos 取不到")
                return null
            }
        val method =
            Reflect.methodByNameAndArity(cls, TOP_APP_INFOS_METHOD, 0) ?: run {
                //  判据级（`d` 在 release 被编译期消除）：这一环断了 recents 那条整条降级。
                XLog.w("$ZOOM_MANAGER 上没有 $TOP_APP_INFOS_METHOD/0")
                return null
            }
        return Reflect.call(manager, method) as? List<*>
    }

    /**
     * recents 正门：把已有任务原地收成小窗。返回 false = 这条路没走通，调用方降级。
     *
     * [zoomLaunchFlags] 是尺寸档（见 `docs/device-findings.md` §17.6.3 的对照表）——
     * `2` = 展开的普通小窗（`setUserPositionInfo`）、`32` = 收起的最小化窗（`fullToMini`）。
     * 窗口模式那个 key 必须给：服务端的判据是 `getLaunchWindowingMode(options) == 100`，不是 flags。
     */
    internal fun startFlexibleFromRecents(
        taskId: Int,
        zoomLaunchFlags: Int,
    ): Boolean {
        val code = startFlexibleFromRecentsCode(taskId, zoomLaunchFlags)
        if (code == null || code < 0) {
            XLog.w("$RECENTS_FLEXIBLE_METHOD($taskId, flags=$zoomLaunchFlags) 被拒（返回 $code），退下一层")
            return false
        }
        XLog.w("zoom:mini → 从 recents 把任务 #$taskId 原地收成小窗（flags=$zoomLaunchFlags，返回 $code）")
        return true
    }

    /**
     * 同 [startFlexibleFromRecents]，但把**原始返回码**交出来 —— `null` = 反射这条路没走通，
     * 负数是服务端拒了，`>= 0` 是受理。
     *
     *  快捷方式的「先起再收」要看这个码：`2` 与 `0` 的服务端语义不同（`2` = 已有任务提到前台、
     *  这次确实是新做的一次转换），而 `-1` 一类的拒绝必须能被单独认出来才有排查价值。
     */
    internal fun startFlexibleFromRecentsCode(
        taskId: Int,
        zoomLaunchFlags: Int,
    ): Int? {
        val cls = Reflect.findClass(javaClass.classLoader, ZOOM_TASK_MANAGER)
        val manager = cls?.let { Reflect.callStatic(it, "getInstance") }
        val method = cls?.let { Reflect.methodByNameAndArity(it, RECENTS_FLEXIBLE_METHOD, RECENTS_FLEXIBLE_ARGS) }
        if (cls == null || manager == null || method == null) {
            XLog.w("$ZOOM_TASK_MANAGER 这条路不通（类/实例/方法：${cls != null}/${manager != null}/${method != null}）")
            return null
        }
        val options =
            Bundle().apply {
                putInt(OPT_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_ZOOM)
                putInt(OPT_ZOOM_LAUNCH_FLAGS, zoomLaunchFlags)
                putInt(ZOOM_EXTRA_WINDOW_MODE, WINDOWING_MODE_ZOOM)
            }
        return Reflect.call(manager, method, taskId, options) as? Int
    }

    /**
     * ColorOS 小窗：`OplusActivityManager#startZoomWindow(Intent, Bundle, int, String)`。
     *
     * Bundle 必须带 `extra_window_mode`（见 [zoomWindowBundle]），否则服务端当普通启动处理。
     */
    internal fun startZoomWindow(
        ctx: Context,
        intent: Intent,
        userId: Int,
    ): Boolean {
        val code = startZoomWindowCode(ctx, intent, userId)
        //  只有 0（起成了）与 2（已有任务提到前台）算「进去了」：
        //  3 = `START_DELIVERED_TO_TOP`，服务端把请求交给了栈顶那个实例、**屏幕没变**；
        //  1 = 退回给调用方；负数才是错误码。按 `>= 0` 判会把 3 报成成功 ——
        //  调用方于是不再走全屏兜底，用户看到的是「按了没反应」。
        return code == 0 || code == 2
    }

    /**
     * 上面那支的原始返回码 —— 是 `ActivityStarter` 的 `START_*`，不是自定义的成功标志：
     * `0` 成功 / `2` 已有任务提到前台 / `3` 交给栈顶那个实例消化（屏幕不变）/ 负数是错误码；
     * `null` = 反射这条路没走通。返回码判不了「有没有真起成小窗」，详见 `docs/device-findings.md` §17.6.1。
     */
    internal fun startZoomWindowCode(
        ctx: Context,
        intent: Intent,
        userId: Int,
    ): Int? {
        val cls =
            Reflect.findClass(javaClass.classLoader, ZOOM_MANAGER) ?: run {
                // 判据级（`d` 在 release 被编译期消除）：ROM 上没这个类 = 小窗这条路整条不通，
                //  用 w 才看得见。
                XLog.w("没有 $ZOOM_MANAGER，跳过 ColorOS 小窗接口")
                return null
            }
        val manager =
            Reflect.callStatic(cls, "getInstance") ?: run {
                //  反射链上每一步都要有失败行：这一处静默 return 的话，「小窗没起来」就只能靠猜
                XLog.w("$ZOOM_MANAGER.getInstance() 返回空，跳过 ColorOS 小窗接口")
                return null
            }
        val method =
            Reflect.methodByNameAndArity(cls, ROM.zoomStartMethod, ZOOM_ARGS) ?: run {
                XLog.w("$ZOOM_MANAGER 上没有 startZoomWindow/$ZOOM_ARGS")
                return null
            }
        //  **不能取 `MiniWindowSupport.currentUserId()`**：那是宿主自己的用户（恒 0）。
        //  目标带 `999@` 时小窗会开到主用户上，与 `app:` 那条路的分身语义正好相反 ——
        //  userId 由调用方按 `AppTarget.userId(target)` 传进来。
        val result =
            Reflect.call(
                manager,
                method,
                intent,
                zoomWindowBundle(),
                userId,
                ctx.packageName,
            ) as? Int
        if (result == null || result < 0) {
            XLog.w("startZoomWindow 被拒（返回 $result），退下一层")
            return result
        }
        XLog.w("window:${intent.component?.packageName} → startZoomWindow=$result")
        return result
    }

    /** 回桌面：某个界面占着前台时，先把它挪出栈顶，小窗才起得来（见 [launchAppInWindow]）。 */
    internal fun homeIntent(): Intent =
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
    internal fun zoomWindowBundle(): Bundle = Bundle().apply { putInt(ZOOM_EXTRA_WINDOW_MODE, WINDOWING_MODE_ZOOM) }

    /** 公开的退路：`ActivityOptions` 的自由窗口（本机不认它的话就是一次普通打开）。 */
    internal fun startFreeform(
        ctx: Context,
        intent: Intent,
    ): Boolean =
        try {
            val options = android.app.ActivityOptions.makeBasic()
            // 窗口模式那一对也都是隐藏 API：常量按字段取，设值按方法名调。
            //  常量在**两个类里都可能出现**：`ActivityOptions` 上有过一份，而本机 framework 里
            //  它只属于 `WindowConfiguration`（PUBLIC STATIC FINAL，值 5）。只查前者的话，
            //  这台机器上「取不到常量」⇒ 自由窗口这条退路恒不成立。
            //  `android.app.WindowConfiguration` 在 SDK 里引不到（编译期没有这个符号），按名字找。
            val mode =
                Reflect.field(android.app.ActivityOptions::class.java, "WINDOWING_MODE_FREEFORM")?.getInt(null)
                    ?: runCatching { Class.forName("android.app.WindowConfiguration") }
                        .getOrNull()
                        ?.let { Reflect.field(it, "WINDOWING_MODE_FREEFORM") }
                        ?.getInt(null)
            if (mode == null) {
                XLog.w("取不到 WINDOWING_MODE_FREEFORM，放弃自由窗口这条")
                return false
            }
            //  **不要写成 `Reflect.call(...) ?: return false`**：`setLaunchWindowingMode` 返回 void，
            //  `invoke` 对 void 恒返回 null —— 那样这一行永远当失败，自由窗口这条退路实际上不存在。
            //  先按签名取方法，取不到才算「这台机器没有这条路」；取到了只管 invoke 有没有抛。
            val setter =
                Reflect.method(
                    android.app.ActivityOptions::class.java,
                    "setLaunchWindowingMode",
                    java.lang.Integer.TYPE,
                )
            if (setter == null) {
                XLog.w("ActivityOptions 上没有 setLaunchWindowingMode(int)，放弃自由窗口这条")
                return false
            }
            setter.invoke(options, mode)
            ctx.startActivity(intent, options.toBundle())
            XLog.w("window:${intent.component?.packageName} → ActivityOptions 自由窗口（mode=$mode）")
            true
        } catch (e: Throwable) {
            // 判据级：这是「小窗两条路都不通、退成普通打开」的原因行，用 w
            XLog.w("自由窗口起法被拒：${e.javaClass.simpleName}: ${e.message}")
            false
        }
}
