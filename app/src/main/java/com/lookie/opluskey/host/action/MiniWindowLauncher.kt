package com.lookie.opluskey.host.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.inject.system.FlexibleWindowBridge

/**
 * 「小窗」这一族动作的执行器 —— 从 [AppLauncher] 按职责分出来的（原来混在那边，一个类里装了四件事）。
 *
 * 管两条路，共用同一套 ColorOS 小窗反射与几何验收：
 *  · `window:包名`（[launchAppInWindow]）：新起一份小窗，有现成任务就复用；
 *  · `zoom:mini` / `zoom:mini:collapse`（[putForegroundInMiniWindow]）：把**前台**应用收成小窗。
 *
 *  反射面全在这里（`OplusActivityManager` / `OplusActivityTaskManager` / `OplusZoomWindowManager`，
 *  都按**名字**取类，不直接引用宿主框架的私有类型）。它专用的常量与 `MiniAnimMode` 就在本文件的
 *  companion 里 —— 调它们不用改注入侧，`kill` 掉宿主进程即生效。
 */
internal class MiniWindowLauncher(
    private val context: () -> Context?,
    private val onFailure: (CommandFailure) -> Unit,
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
        val ctx = context() ?: run {
            XLog.w("没有 Context，window:$target 跳过")
            return
        }
        val pm = ctx.packageManager
        val intent = AppEntry.intentOf(pm, target)
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
        //  `window:` 要的是普通小窗（展开档），尺寸档固定给 [ZOOM_LAUNCH_FLAG_USER_POSITION]。
        val foregroundTask = foregroundTaskFor(pkg)
        if (foregroundTask > 0 && startFlexibleFromRecents(foregroundTask, ZOOM_LAUNCH_FLAG_USER_POSITION)) {
            XLog.w("window:$target 已在前台，原地收成小窗（不新起实例）")
            postDelayedGuarded("window:$target 的小窗验收", MINI_VERIFY_MS) {
                if (!isTaskInZoomWindow(foregroundTask)) retryAfterHome(ctx, intent, target)
            }
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
            if (!startZoomWindow(ctx, intent)) {
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
    private fun postDelayedGuarded(what: String, delayMs: Long, block: () -> Unit) {
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
     *  · **展开档**（默认）：**原地收**（应用侧 recents 正门 + `mZoomLaunchFlags = 2`，无动画、
     *    **不改显示方向** ⇒ 横屏应用拿到横屏帧的比例）。另有一条「伪造成柔性全屏态 + `switchState`」
     *    的路能拿到原生动画，但窗口只是个半成品，默认关着 —— 见 [USE_FORGED_ANIMATION_ROUTE]。
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
        val ctx = context() ?: run {
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
            putInCollapsedMini(ctx, taskId)
            return
        }
        if (!USE_FORGED_ANIMATION_ROUTE) {
            // 原生动画档优先。注意它**不是「自己画动画」** —— 插值 / 矩阵 / apply / spring
            // 全是框架的 `FlexibleWindowAnimator` 在跑，我们只交起止参数
            // （`FlexibleWindowBridge.CMD_MINI_ANIM`，配方见 `docs/zoom-window.md` §十二）。
            if (putInMiniWithNativeAnim(ctx, taskId)) return
            // 主路：**recents 正门原地收**（`startActivityFromRecentsForFlexible` + flags=2）。
            // 这是「进得去小窗」的那条 —— 它不改显示方向，窗口完整，代价只是没有动画
            // （动画要开就切 [MINI_ANIM_MODE]，现行是 `OFF`）。
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
     * A / B 两档的落地：**同一个动画器、同一组参数，只差「谁先」**。
     *
     * 两条路的取舍是几何决定的（`docs/zoom-window.md` §十二）：小窗在屏上是 `1020×1812`（比例 0.563），
     * 全屏是 `1272×2772`（0.459）—— **小窗不是全屏的等比缩小**，而原生动画器只做等比 `setMatrix`。
     * 所以「全屏外观」和「小窗落点」**不可能同时精确**，只能选一头：
     *
     *  · **[MiniAnimMode.ANIM_THEN_CONVERT]（A）先动画后转换** —— 动画里是**真·全屏画面**在缩，
     *    第一帧和用户按下去前看到的完全一致；代价是收尾转换时窗口要重排到小窗比例，会跳一下。
     *  · **[MiniAnimMode.CONVERT_THEN_ANIM]（B）先转换后动画** —— **落点精确**、无跳变；
     *    代价是动画第一帧就是「小窗布局被等比放大」，和按下去前的画面不同。
     *
     * @return 是否已经接手（`OFF` 返回 false，交给调用方走无动画的正门）
     */
    private fun putInMiniWithNativeAnim(ctx: Context, taskId: Int): Boolean {
        val mode = MINI_ANIM_MODE
        if (mode == MiniAnimMode.OFF) return false
        if (!animGeometryMatchesOrientation(ctx)) {
            XLog.w(
                "zoom:mini → 当前不是竖屏，动画的终点几何（竖屏标定值）对不上 —— " +
                    "这一下只做真转换、不播动画",
            )
            return false
        }
        val params = FlexibleWindowBridge.MiniAnimParams(
            startRect = fullScreenRect(ctx),
            finishRect = EXPANDED_CONFIG_BOUNDS,
            startScale = MINI_ANIM_START_SCALE,
            finishScale = EXPANDED_SCALE,
            startAlpha = MINI_ANIM_START_ALPHA,
            finishAlpha = MINI_ANIM_FINISH_ALPHA,
            durationMs = MINI_ANIM_DURATION_MS,
        )
        when (mode) {
            MiniAnimMode.ANIM_THEN_CONVERT -> {
                val delay = MINI_ANIM_DURATION_MS + MINI_ANIM_SETTLE_MS
                XLog.w("zoom:mini → A 档：先播原生缩小动画，${delay}ms 后再真转换（收尾会跳一下）")
                FlexibleWindowBridge.sendMiniAnim(ctx, taskId, params)
                postDelayedGuarded("A 档：动画结束后的真转换", delay) { retryViaRecents(taskId) }
            }
            MiniAnimMode.CONVERT_THEN_ANIM -> {
                // 转换是同步的 binder 调用，返回时任务已经是柔性任务；但那一瞬可能还有过渡在收，
                // 而动画器开头的 `isInTransition` 过滤会把「在过渡中」的任务**静默剔掉**
                // （日志里只有一句 `no animation or leash.`）。所以留一个可调的间隔。
                val delay = MINI_ANIM_AFTER_CONVERT_DELAY_MS
                XLog.w("zoom:mini → B 档：先真转换（落点精确），${delay}ms 后播原生缩小动画（第一帧会跳一下）")
                retryViaRecents(taskId)
                if (delay <= 0L) {
                    FlexibleWindowBridge.sendMiniAnim(ctx, taskId, params)
                } else {
                    postDelayedGuarded("B 档：转换后的动画", delay) {
                        FlexibleWindowBridge.sendMiniAnim(ctx, taskId, params)
                    }
                }
            }
            MiniAnimMode.OFF -> return false
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
    private fun animGeometryMatchesOrientation(ctx: Context): Boolean {
        val cfg = ctx.resources.configuration
        return cfg.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT ||
            cfg.screenWidthDp == cfg.screenHeightDp
    }

    /**
     * 全屏态的任务矩形 —— 动画的**起点**。框架的 `updateAnimationLayer` 只读 `left/top`，
     * 所以这里真正有用的是 `(0,0)`；宽高带上只是不让 Rect 是半个（见 §十二）。
     */
    private fun fullScreenRect(ctx: Context): android.graphics.Rect {
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
     * 落点几何一并下发：调 [EXPANDED_CONFIG_BOUNDS] / [EXPANDED_SCALE] 只改宿主侧，不用重启。
     */
    private fun putInMiniViaForgedAnimation(ctx: Context, taskId: Int) {
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
                else -> XLog.w(
                    "zoom:mini → 任务 #$taskId 已是小窗、预置几何被吃住（配置宽占比 $ratio）；" +
                        "档位以截图为准",
                )
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
     */
    private fun retryViaRecents(taskId: Int) {
        if (startFlexibleFromRecents(taskId, ZOOM_LAUNCH_FLAG_USER_POSITION)) {
            postDelayedGuarded("recents 正门收小窗的验收", MINI_VERIFY_MS) { verifyMiniOrFallback(taskId) }
        } else {
            XLog.w("zoom:mini → recents 正门没受理（服务端拒了），什么都不做")
            onFailure(CommandFailure.APP_NO_MINI_WINDOW)
        }
    }

    /**
     * 收起档：把任务原地收成右上角那条最小化窗。
     *
     * 只有 recents 正门这一条 —— 服务端凭 `mZoomLaunchFlags == 32` 走 `fullToMini` 分支，
     * 用 `getMiniFlexibleTaskInfo` 那套几何（真机约 `Rect(898,168-1203,710)` / scale `0.2397`）。
     * 收不起就退「按包名另起一份实例」，与展开档同一层兜底。
     */
    private fun putInCollapsedMini(ctx: Context, taskId: Int) {
        if (startFlexibleFromRecents(taskId, ZOOM_LAUNCH_FLAG_FULL_TO_MINI)) {
            postDelayedGuarded("zoom:mini:collapse 的验收", MINI_VERIFY_MS) {
                if (isTaskInZoomWindow(taskId)) {
                    XLog.w("zoom:mini:collapse → 任务 #$taskId 已进入小窗态（收起/展开以截图为准）")
                } else {
                    XLog.w("zoom:mini:collapse → 任务 #$taskId 没变成小窗，什么都不做")
                    onFailure(CommandFailure.APP_NO_MINI_WINDOW)
                }
            }
            return
        }
        XLog.w("zoom:mini:collapse → recents 正门没受理，什么都不做")
        onFailure(CommandFailure.APP_NO_MINI_WINDOW)
    }

    /** 前台那份任务的 taskId —— `getAllTopAppInfos()` 按「最前面」排序，取第 0 项。 */
    private fun foregroundTaskId(): Int {
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
    private fun canEnterMiniWindow(taskId: Int): Boolean {
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
    private fun componentOf(top: Any, cls: Class<*>): android.content.ComponentName? {
        (top as? android.content.ComponentName)?.let { return it }
        val pkg = packageOf(top, cls) ?: return null
        val name = Reflect.call(top, "getClassName") as? String
            ?: Reflect.fieldValue(top, cls, TOP_APP_CLASS_FIELD) as? String
            ?: return null
        return android.content.ComponentName(pkg, name)
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

    /**
     * recents 正门：把已有任务原地收成小窗。返回 false = 这条路没走通，调用方降级。
     *
     * [zoomLaunchFlags] 是尺寸档（见 `docs/device-findings.md` §17.6.3 的对照表）——
     * `2` = 展开的普通小窗（`setUserPositionInfo`）、`32` = 收起的最小化窗（`fullToMini`）。
     * 窗口模式那个 key 必须给：服务端的判据是 `getLaunchWindowingMode(options) == 100`，不是 flags。
     */
    private fun startFlexibleFromRecents(taskId: Int, zoomLaunchFlags: Int): Boolean {
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
            putInt(OPT_ZOOM_LAUNCH_FLAGS, zoomLaunchFlags)
            putInt(ZOOM_EXTRA_WINDOW_MODE, WINDOWING_MODE_ZOOM)
        }
        val code = Reflect.call(manager, method, taskId, options) as? Int
        if (code == null || code < 0) {
            XLog.d("$RECENTS_FLEXIBLE_METHOD($taskId, flags=$zoomLaunchFlags) 被拒（返回 $code），退下一层")
            return false
        }
        XLog.w("zoom:mini → 从 recents 把任务 #$taskId 原地收成小窗（flags=$zoomLaunchFlags，返回 $code）")
        return true
    }

    /**
     * 等 transition 跑完核一眼；没成小窗就退回「按包名另起一份实例」。
     *
     * 判据是**窗口的屏幕矩形有没有落在屏幕内**，不是「配置宽占比」——
     * 后者在横屏下会**给出假绿灯**：分母 `Resources.getSystem().displayMetrics.widthPixels`
     * 不随旋转变（恒 1272），而横屏应用的配置矩形宽是 **2240**（= 横屏全屏任务的宽），
     * 比值读到 `1.76`，落在「≥0.95 就算好」那一边。
     *
     * 真机症状（用户报「横屏小窗尺寸大」）：屏幕矩形 `1316 × 741`，
     * **宽 1316 > 屏宽 1272、右侧溢出 185px**。取证与根因见 `docs/device-findings.md` 附十三。
     */
    private fun verifyMiniOrFallback(taskId: Int) {
        //  注意：「读不到任务信息」**不是**「没变成小窗」：
        //  `isTaskInZoomWindow` 在 `zoomInfoOf` 为 null（ROM 改了字段名 / 任务列表读不到）时
        //  也返回 false —— 会被折成「服务端受理了但没收起来」，弹一句「这个应用不支持小窗」，
        //  把一个读不到的事实说成了应用的锅。这里先把这两种情形分开。
        if (zoomInfoOf(taskId) == null) {
            XLog.w("zoom:mini → 读不到任务 #$taskId 的信息，无法验收（不报「不支持小窗」）")
            return
        }
        if (!isTaskInZoomWindow(taskId)) {
            // **什么都不做**：服务端受理了（返回 `2`）却没收出窗口，
            // 就是「这个应用不支持小窗」（真机：`com.heytap.market`）。以前这里退
            // 「按包名另起一份实例」⇒ 回桌面 + 重新起一份，用户看到「一按侧键就回桌面重开」，
            // 比没反应更糟。而且那条还会**重复调一次** `startFlexibleFromRecents`。
            XLog.w("zoom:mini → 任务 #$taskId 没变成小窗（服务端受理了但没收起来），什么都不做")
            onFailure(CommandFailure.APP_NO_MINI_WINDOW)
            return
        }
        logZoomGeometry(taskId)
    }

    /**
     * 只报事实：小窗在**屏幕上**的矩形、以及它有没有超出屏幕。
     *
     * 注意：「超出屏幕」这一档**只记日志、不换路**（定的）：横屏应用走 recents 正门时
     * 比例是对的、几何偏大；换路（launch 配方）会变成竖屏比例但进屏 —— 两者取舍还没收敛，
     * 自动换路会让屏幕上变成「横屏比例一闪 → 竖屏比例」，反而看不出比例对不对。
     * 要顺着这条路继续调时，把这里的 `false` 分支接到降级上即可。
     */
    private fun logZoomGeometry(taskId: Int) {
        val rect = zoomScreenRect()
        val display = displaySize()
        when (zoomFitsOnScreen(rect, display)) {
            false -> XLog.w(
                "zoom:mini → 任务 #$taskId 已是小窗，但**窗口超出屏幕**：屏幕矩形 $rect > 显示 $display" +
                    "（横屏应用走 recents 正门时会这样，见 docs/device-findings.md 附十三）",
            )
            true -> XLog.w("zoom:mini → 任务 #$taskId 已是小窗、窗口落在屏幕内（屏幕矩形 $rect）")
            null -> XLog.w("zoom:mini → 任务 #$taskId 已是小窗，但读不到屏幕矩形 / 显示尺寸，跳过几何验收")
        }
    }

    /**
     * 小窗**在屏幕上的**矩形 —— 取自框架
     * `com.oplus.zoomwindow.OplusZoomWindowManager#getCurrentZoomWindowState()` 的 `zoomRect`。
     *
     * 注意：它和 [taskZoomConfigRatio] 读的 `OplusAppInfo.appBounds` **不是一回事**：
     * `appBounds` 是**配置矩形**（不含显示缩放），这个才是**乘过 scale 的屏幕矩形** ——
     * 判「窗口多大 / 装不装得下」只能用它。
     *
     * 用**截图像素级边缘检测**核对过它就是屏幕上那块矩形：真机左边界 `x=141`、
     * 下边界 `y=1006`，与日志里的 `zoomRect = Rect(141, 266 - 1457, 1007)` 对得上。
     */
    private fun zoomScreenRect(): android.graphics.Rect? = try {
        val cls = Reflect.findClass(javaClass.classLoader, MiniWindowSupport.MANAGER)
        val manager = cls?.let { Reflect.callStatic(it, "getInstance") }
        val method = cls?.let { Reflect.methodByNameAndArity(it, CURRENT_ZOOM_STATE_METHOD, 0) }
        val state = if (manager != null && method != null) Reflect.call(manager, method) else null
        state?.let { Reflect.fieldValue(it, it.javaClass, ZOOM_STATE_RECT_FIELD) as? android.graphics.Rect }
    } catch (e: Throwable) {
        XLog.d("读小窗屏幕矩形失败：${e.message}")
        null
    }

    /**
     * 当前显示的**真实像素尺寸**（**随旋转变**）。
     *
     * 不拿 `Resources.getSystem().displayMetrics` —— 那套**不跟旋转**，横屏下仍是竖屏值，
     * 正是上面那个假绿灯的来源。
     *
     * 取 `maximumWindowMetrics`（API 30+，本项目 minSdk 33 ⇒ 恒可用）而不是旧的
     * `defaultDisplay.getRealSize`（编译期 deprecated）：在**非 Activity 的 Context** 上，
     * 前者给的正是「默认显示的最大窗口区域」，与后者（默认显示的真实尺寸）是同一个东西，
     * 而且**不受当前窗口大小影响** —— 用 `currentWindowMetrics` 会被分屏 / 小窗裁小，
     * 那会把「小窗有没有超出屏幕」这个验收项误判成超出。取不到就返回 null（跳过验收），不猜。
     */
    private fun displaySize(): android.graphics.Point? = try {
        val wm = context()?.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        val bounds = wm?.maximumWindowMetrics?.bounds
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            android.graphics.Point(bounds.width(), bounds.height())
        } else {
            null
        }
    } catch (e: Throwable) {
        XLog.d("读显示尺寸失败：${e.message}")
        null
    }

    /** 小窗的屏幕矩形有没有落在屏幕内。`null` = 读不到（跳过验收）。 */
    private fun zoomFitsOnScreen(
        rect: android.graphics.Rect?,
        display: android.graphics.Point?,
    ): Boolean? {
        if (rect == null || display == null) return null
        return rect.left >= 0 && rect.top >= 0 &&
            rect.right <= display.x && rect.bottom <= display.y
    }

    /** 那个任务现在是不是小窗 —— 只看 `windowingMode`（返回码和截图都会骗人）。 */
    private fun isTaskInZoomWindow(taskId: Int): Boolean {
        val info = zoomInfoOf(taskId) ?: return false
        val mode = Reflect.fieldValue(info, info.javaClass, TOP_APP_WINDOWING_MODE_FIELD) as? Int
        return mode == WINDOWING_MODE_ZOOM
    }

    /**
     * 那个任务小窗的**应用配置矩形宽 / 屏宽**（`null` = 它不是小窗 / 读不到）。
     *
     * 注意：这个比值**分不出「展开 / 收起」两档**，别拿它当档位判据 —— 那版就是把它
     * 当成档位判据用的，结果给了假绿灯：小窗两档的**配置矩形是同一个** `1272×2261`
     * （屏幕上的大小再由 scale 决定：展开 `0.8017` → `1020×1812`、收起 `0.2397` → `305×542`），
     * 所以两档读出来都是 `1272/1272 ≈ 1.0`，谁也分不出来。
     *
     * 它能验的是另一件事：**system_server 有没有把我们预置的几何吃住**。配置宽应当 ≈ 满屏宽
     * （见 [ZOOM_CONFIG_WIDTH_MIN_RATIO]）—— 若读到明显偏小，就是「屏幕矩形被当成配置矩形喂进去、
     * 又被多缩一次」那个 bug 的签名（真机读到过 `1020`，对应屏幕上只剩 `817`）。
     *
     * 读不到时返回 `null` 而**不是**猜一个值 —— 调用方据此走「没走通」那条降级。
     */
    private fun taskZoomConfigRatio(taskId: Int): Float? {
        val info = zoomInfoOf(taskId) ?: return null
        val mode = Reflect.fieldValue(info, info.javaClass, TOP_APP_WINDOWING_MODE_FIELD) as? Int
        if (mode != WINDOWING_MODE_ZOOM) return null
        val bounds = Reflect.fieldValue(info, info.javaClass, TOP_APP_BOUNDS_FIELD)
            as? android.graphics.Rect ?: return null
        val screen = android.content.res.Resources.getSystem().displayMetrics.widthPixels
        if (screen <= 0 || bounds.width() <= 0) return null
        return bounds.width().toFloat() / screen
    }

    /** `getAllTopAppInfos()` 里那个 taskId 对应的 `OplusAppInfo`；没有就 null。 */
    private fun zoomInfoOf(taskId: Int): Any? {
        val infos = topAppInfos() ?: return null
        for (item in infos) {
            val info = item ?: continue
            val id = Reflect.fieldValue(info, info.javaClass, TOP_APP_TASK_ID_FIELD) as? Int ?: continue
            if (id == taskId) return info
        }
        return null
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
            // 判据级（`d` 在 release 被编译期消除）：ROM 上没这个类 = 小窗这条路整条不通，
            //  用 w 才看得见。
            XLog.w("没有 $ZOOM_MANAGER，跳过 ColorOS 小窗接口")
            return null
        }
        val manager = Reflect.callStatic(cls, "getInstance") ?: return null
        val method = Reflect.methodByNameAndArity(cls, "startZoomWindow", ZOOM_ARGS) ?: run {
            XLog.w("$ZOOM_MANAGER 上没有 startZoomWindow/$ZOOM_ARGS")
            return null
        }
        val userId = MiniWindowSupport.currentUserId()
        val result = Reflect.call(
            manager, method, intent, zoomWindowBundle(), userId, ctx.packageName,
        ) as? Int
        if (result == null || result < 0) {
            XLog.w("startZoomWindow 被拒（返回 $result），退下一层")
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

    /** 公开的退路：`ActivityOptions` 的自由窗口（本机不认它的话就是一次普通打开）。 */
    private fun startFreeform(ctx: Context, intent: Intent): Boolean = try {
        val options = android.app.ActivityOptions.makeBasic()
        // 窗口模式那一对也都是隐藏 API：常量按字段取，设值按方法名调
        val mode = Reflect.field(android.app.ActivityOptions::class.java, "WINDOWING_MODE_FREEFORM")
            ?.getInt(null)
        if (mode == null) {
            XLog.w("取不到 WINDOWING_MODE_FREEFORM，放弃自由窗口这条")
            return false
        }
        Reflect.call(options, "setLaunchWindowingMode", mode) ?: return false
        ctx.startActivity(intent, options.toBundle())
        XLog.w("window:${intent.component?.packageName} → ActivityOptions 自由窗口（mode=$mode）")
        true
    } catch (e: Throwable) {
        // 判据级：这是「小窗两条路都不通、退成普通打开」的原因行，用 w
        XLog.w("自由窗口起法被拒：${e.javaClass.simpleName}: ${e.message}")
        false
    }

    private companion object {

        /** ColorOS 小窗接口的类名 —— 按名字反射，不直接引用（注入侧不许碰宿主框架的私有类型）。 */
        const val ZOOM_MANAGER = "android.app.OplusActivityManager"

        /** `OplusZoomWindowManager#getCurrentZoomWindowState()` → `OplusZoomWindowInfo`。 */
        const val CURRENT_ZOOM_STATE_METHOD = "getCurrentZoomWindowState"

        /** `OplusZoomWindowInfo` 上那个**屏幕矩形**字段（乘过 scale 的；不是 `appBounds`）。 */
        const val ZOOM_STATE_RECT_FIELD = "zoomRect"

        /** `startZoomWindow` 的形参个数：`Intent, Bundle, int, String`。 */
        const val ZOOM_ARGS = 4

        /** Bundle 里指定窗口模式的 key —— `OplusZoomWindowManager.EXTRA_WINDOW_MODE`。 */
        const val ZOOM_EXTRA_WINDOW_MODE = "extra_window_mode"

        /** 上面那个 key 取这个值才是小窗 —— `OplusZoomWindowManager.WINDOWING_MODE_ZOOM`。 */
        const val WINDOWING_MODE_ZOOM = 100

        /** 取各屏前台应用信息：`OplusActivityManager#getAllTopAppInfos()` → `List<OplusAppInfo>`。 */
        const val TOP_APP_INFOS_METHOD = "getAllTopAppInfos"

        /** `OplusAppInfo` 的字段名（按名字取，不引宿主框架的类）。 */
        const val TOP_APP_ACTIVITY_FIELD = "topActivity"

        const val TOP_APP_TASK_ID_FIELD = "taskId"

        const val TOP_APP_WINDOWING_MODE_FIELD = "windowingMode"

        const val TOP_APP_PACKAGE_FIELD = "packageName"

        /**
         * `OplusAppInfo.topActivity` 若是 `ComponentInfo` 形状，类名在这个字段里。
         * 只有拿得到类名才查得到 `ActivityInfo.resizeMode`（那是 activity 级属性），见 [componentOf]。
         */
        const val TOP_APP_CLASS_FIELD = "className"

        /** `ActivityInfo` 上的尺寸可调性字段 —— 编译期 stub 里没有，只能按名字反射读。 */
        const val RESIZE_MODE_FIELD = "resizeMode"

        /**
         * `ActivityInfo.RESIZE_MODE_UNRESIZEABLE` 的值（= 0）。不引常量是因为它和 [RESIZE_MODE_FIELD]
         * 一样不在编译期 stub 里；真机上 `com.heytap.market` 读出来的就是这个值。
         */
        const val RESIZE_MODE_UNRESIZEABLE = 0

        /**
         * 「不可调尺寸（`resizeMode == 0`）的应用**直接不进小窗**」开关 —— **默认关**。
         *
         * 注意：真机教训：这条判据**太宽**，`resizeMode == 0` 不只命中
         * 「转换会失败的 app」，它把**所有游戏**都命中了：
         *
         * ```
         * com.tencent.tmgp.supercell.clashofclans/…GameAppTencent **不可调尺寸**（resizeMode=0）
         * com.happyelements.AndroidAnimal/…SplashActivity      **不可调尺寸**（resizeMode=0）
         * ```
         *
         * 真机上游戏**是能进小窗的**（只是横屏时尺寸会偏大，见 [animGeometryMatchesOrientation]），
         * 所以打开这个开关会导致「横屏的游戏一按下去什么都不发生」。
         *
         * 想开它的人要清楚代价：**它拦的是"所有 resizeMode=0"，不是"转换会失败的那些"** ——
         * 后者只能靠「转换完 350ms 复查 `windowingMode`」那条事后判据（[verifyMiniOrFallback]），
         * 或者把判据换成框架自己的 `FlexibleTaskController` 结论（要改注入侧、要重启）。
         */
        const val MINI_SKIP_UNRESIZEABLE = false

        /**
         * `OplusAppInfo.appBounds` —— 小窗的**应用配置矩形**（不含显示缩放）。
         *
         * 两档小窗都是 `1272×2261`，所以它**分不出档位**；它验的是「预置几何有没有被吃住」，
         * 见 [taskZoomConfigRatio]。
         */
        const val TOP_APP_BOUNDS_FIELD = "appBounds"

        /**
         * 配置宽低于它 × 屏宽，就认为预置几何**没被吃住**（配置矩形被喂小了）。
         *
         * 正常值 ≈ `1.0`（配置宽 = 满屏宽 1272）；那个多缩一次的 bug 读到 `0.80`。
         * 留 5% 余量，免得机型/边距差异误报。
         */
        const val ZOOM_CONFIG_WIDTH_MIN_RATIO = 0.95f

        /**
         * 展开档小窗的**应用配置矩形**与显示缩放 —— 随小窗指令下发给注入侧，供**伪造态**那条路用。
         *
         * 注意：默认路径（recents 正门）**不看这两个值** —— 它的落点由 `setUserPositionInfo` 沿用
         * **系统记住的位置**（= 用户上次的落点）。所以别以为改这里能挪默认路径的窗口。
         *
         *
         * 注意：要调几何就改这里：宿主侧的改动 `kill` 掉 `com.oplus.gesture` 就生效，
         * **不需要整机重启**（注入侧那份同名值只是「宿主没带」时的兜底）。
         * 这正是把它们从注入侧搬过来的原因 —— 见 `FlexibleWindowBridge.EXTRA_LEFT` 一段的说明。
         *
         * 语义（真机实测，`docs/device-findings.md` 附七）：
         * 这是**配置矩形**不是屏幕矩形，`屏幕尺寸 = Rect.size × scale`：
         * `1272 × 2261 × 0.8017 = 1020 × 1812`；`left/top` 不参与缩放、直接照搬屏幕坐标。
         * 与机型允许表绑定（PLK110 / 1272×2772 @560dpi），加机型要连同它们重测。
         */
        val EXPANDED_CONFIG_BOUNDS = android.graphics.Rect(126, 319, 1398, 2580) // 1272 × 2261

        const val EXPANDED_SCALE = 0.8017f

        /**
         * 「缩小动画」走哪一档。**这是宿主侧常量 ⇒ 切档只要 `kill` 宿主，不用整机重启**
         * （注入侧那份实现是档位无关的，见 [FlexibleWindowBridge.sendMiniAnim]）。
         *
         * - [MiniAnimMode.OFF]（**现行**）：不播动画，走 recents 正门（原地收、窗口完整、无动画）。
         * 定回来当现行档的原因有两条：(1) 动画每帧的守卫要求任务是「带标题栏的柔性任务」，
         *   普通全屏任务**每帧直接 bail**，所以只有「先真转换」之后才播得动；(2) 转换与动画叠在一起时，
         *   **横屏应用**（守卫按显示方向判定，判定与事实不一致时会被带进动画档）屡次表现为
         *   「收不成小窗」。**进得去**优先于有动画。
         * - [MiniAnimMode.CONVERT_THEN_ANIM]（B）：先真转换、再播动画。落点精确；留着备选。
         * - [MiniAnimMode.ANIM_THEN_CONVERT]（A）：先动画后转换 —— ** 实测此档结构上不成立**：
         *   动画器每帧的守卫是 `FlexibleWindowUtils.isFlexibleTaskAndHasCaption(task)`，
         *   普通全屏任务**每帧直接 bail**（真机日志刷满 `updateAnimationLayer task is not flexible task`，
         *   画面纹丝不动）。所以**必须先真转换**，A 档只在动画期间"看起来"是任务；留着它只为留档。
         *
         * A / B 的几何取舍（见 `docs/zoom-window.md` §十二）：小窗在屏上是 `1020×1812`（0.563）、
         * 全屏是 `1272×2772`（0.459），**小窗不是全屏的等比缩小**（纵向被缩短），
         * 而动画器只做等比 `setMatrix` ⇒ 两头必有一头对不齐。B 档保的是**落点**，
         * 代价是动画第一帧就是「小窗布局被等比放大」，和按下去前看到的画面不同。
         */
        val MINI_ANIM_MODE = MiniAnimMode.OFF

        /**
         * 动画起点：**全屏外观** —— `startScale=1.0` 配 `startRect` 的 `(0,0)` 就是恒等变换，
         * 起来那一帧和用户按下去前看到的画面完全一致。
         *
         * 真机确认（用户亲眼所见）：动画可见且**可用** —— 全屏应用向屏幕中心缩成小窗。
         * 之所以能确定每帧 transform 真的作用在画面那层 surface 上，靠的是一次诊断：
         * 把这里临时改成 `0.2f`，窗口就从**左上角**冒出来再移到中间 ——
         * 缩放绕的是 surface 自己的原点，所以缩小版停在左上角，正好证明改的就是画面那块
         * （⇒ 不需要 `Builder.setAnimationLeash` 自己建 leash）。
         */
        const val MINI_ANIM_START_SCALE = 1.0f

        const val MINI_ANIM_START_ALPHA = 1.0f

        /**
         * 动画终点的不透明度。设成 0 就是「边缩边淡出」，用来把 A 档收尾那次跳变盖掉；
         * 先给 1.0（不淡出），看真机观感再决定要不要淡。
         */
        const val MINI_ANIM_FINISH_ALPHA = 1.0f

        /**
         * 交给框架的时长。注意：spring 档位的实际收敛时长由**阻尼比 / 刚度**决定
         * （框架写死 `0.86 / 250`），这个值主要进日志、以及给 A 档排期用。
         */
        const val MINI_ANIM_DURATION_MS = 400L

        /**
         * A 档「动画播完 → 真转换」之间的等待余量。
         *
         * spring 收敛（`dampingRatio=0.86 / stiffness=250`）比 [MINI_ANIM_DURATION_MS] 长，
         * 真机实测同一族动画约 `660ms` 收尾。等短了会在动画中途触发转换（内容当场重排，很难看）。
         */
        const val MINI_ANIM_SETTLE_MS = 450L

        /**
         * B 档：真转换之后等多久再启动动画。**0 = 立刻**。
         *
         * 留这个旋钮是因为动画器开头有一道 `isInTransition` 过滤，会把「正在过渡中」的任务
         * **静默剔掉**（`no animation or leash.`）。刚转换完那一瞬可能正好处在过渡里 ——
         * 若真机日志出现那句，把这个值往上调（100 / 200）再试，**不用重启**。
         */
        const val MINI_ANIM_AFTER_CONVERT_DELAY_MS = 0L

        /** 见 [MINI_ANIM_MODE]。 */
        enum class MiniAnimMode {
            OFF,
            ANIM_THEN_CONVERT,
            CONVERT_THEN_ANIM,
        }

        /**
         * 展开档走哪条路：`false`（默认）= 应用侧 **recents 正门**；`true` = `system_server` 伪造态。
         *
         * 为什么默认 `false` —— 真机实测，伪造态那条开出来的窗口是**半成品**：
         * 几何对、档位对，但**没有圆角、没有操作条 / ⋮ 菜单、点一下会被当成拖动**，应用内容还叠两份。
         * 根因是框架的「柔性任务注册」那一步没做（操作条视图 / 圆角 / 拖拽与输入都配在那里），
         * 而伪造态只捅了三个状态字段 + `switchState`。
         *
         * 留着它是因为它是目前唯一能「看起来」拿到**原生动画**的路 —— 但复测把它也判死了，
         * 见下面那段。**不要**在没有新证据前打开它。
         *
         * 注意：复测结论（`device-findings.md` 附十一 / 附十二）：**这条也走不通，而且不是
         * 「缺几个调用」——它压根没真转换任务**。真机日志：`switchState` 之后
         * `onTaskInfoChanged taskid 125 … mWindowingMode=fullscreen` **自始至终是全屏**，
         * 只是把 surface 缩到 `scale=0.8014` 又画了一条操作条 —— 一个「穿着小窗外衣的全屏任务」。
         * 后果就是历史记录里那三条，且都有根因：
         *  · `handleButtonRegion = Rect(0,0-0,0)`（柔性任务注册那步没做）→ 框架退化成
         *    「**整个窗口都是拖拽把手**」→ 点一下就成拖动，一拖就 `exitFlexibleTaskWindowInnerLocked
         *    exitTo:6` 把小窗甩掉、任务退回全屏；`updateTouchRegion()` 补不上它。
         *  · 没圆角 / 没菜单，也同源。
         * 补壳子（`corner` / 操作条）能改善观感，但改不了「它不是真小窗」这个事实。
         */
        const val USE_FORGED_ANIMATION_ROUTE = false

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

        /**
         * `mZoomLaunchFlags = 32`（服务端 `fullToMini` → `getMiniFlexibleTaskInfo`）：
         * 收起的最小化窗（右上角一条）。取值见 `docs/device-findings.md` §17.6.3 的尺寸档对照表。
         */
        const val ZOOM_LAUNCH_FLAG_FULL_TO_MINI = 32

        /**
         * 起完等多久再核「有没有真变成小窗」+「窗口落在屏幕内」。
         *
         * 注意：**不能太早**（真机踩过）：收小窗那一下会先落一个**过渡帧**，约 600ms 后
         * 才定成最终几何。横屏 bilibili 实测：
         *
         * ```
         * +0ms    Rect(119, 730 - 1152, 1311)   scale=0.46116072   ← 过渡帧，1033×581，在屏幕内
         * +591ms  Rect(141, 266 - 1457, 1007)   scale=0.5875       ← 最终态，1316×741，右侧溢出
         * ```
         *
         * 按 350ms 去核会读到过渡帧，把「最后会溢出」的窗口报成「落在屏幕内」—— 又是一条假绿灯。
         */
        const val MINI_VERIFY_MS = 700L

        /** `START_DELIVERED_TO_TOP`：intent 被交给栈顶那个活动消化，屏幕不会有任何变化。 */
        const val START_DELIVERED_TO_TOP = 3

        /** 上面那档时先回桌面，等桌面过渡铺开再起小窗（毫秒）。 */
        const val HOME_SETTLE_MS = 400L
    }
}
