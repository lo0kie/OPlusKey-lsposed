package com.lookie.opluskey.command.action.app

import android.content.Context
import com.lookie.opluskey.command.CommandFailure
import com.lookie.opluskey.command.WindowAnimParams
import com.lookie.opluskey.command.action.app.MiniWindowFacts.COLLAPSED_BOUNDS
import com.lookie.opluskey.command.action.app.MiniWindowFacts.COLLAPSED_SCALE
import com.lookie.opluskey.command.action.app.MiniWindowFacts.FLEXIBLE_APP_MANAGER
import com.lookie.opluskey.command.action.app.MiniWindowFacts.FLEXIBLE_TOGGLE_ARGS
import com.lookie.opluskey.command.action.app.MiniWindowFacts.FLEXIBLE_TOGGLE_METHOD
import com.lookie.opluskey.command.action.app.MiniWindowFacts.MAIN_USER
import com.lookie.opluskey.command.action.app.MiniWindowFacts.MINI_ANIM_AFTER_CONVERT_DELAY_MS
import com.lookie.opluskey.command.action.app.MiniWindowFacts.MINI_ANIM_DURATION_MS
import com.lookie.opluskey.command.action.app.MiniWindowFacts.MINI_ANIM_FINISH_ALPHA
import com.lookie.opluskey.command.action.app.MiniWindowFacts.MINI_ANIM_START_ALPHA
import com.lookie.opluskey.command.action.app.MiniWindowFacts.MINI_ANIM_START_SCALE
import com.lookie.opluskey.command.action.app.MiniWindowFacts.MINI_VERIFY_MS
import com.lookie.opluskey.command.action.app.MiniWindowFacts.TOP_APP_ACTIVITY_FIELD
import com.lookie.opluskey.command.platform.MiniWindowSupport
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

// 「前台 → 小窗」这一族的三个入口，从 `MiniWindowLauncher` 的类体里搬出来（`LargeClass`）：
//  它们都是**扩展函数**（`internal fun MiniWindowLauncher.xxx`），只经类的既有成员读写，
//  搬出来不改任何语义 —— 类本体只剩「命令入口 + 判定」，这一族单独一份文件更好找。
//
//  搬出去之后仍然住在同一个包：`windowModeOf` / `zoomInfoOf` / `logZoomGeometry` 这些
//  同包扩展照样能用，调用点一个字都不用改。

/**
 * 「前台 → 小窗 + 原生动画」的首选路：调应用侧门面
 * `FlexibleWindowManager#toggleFlexibleWindow(Activity, taskId, true, true)`。
 *
 * 这一支是**计算器首页那颗按钮**走的同一条（`Calculator.F2`）：服务端自己
 * `calcFlexibleTaskBounds` 算落点、`startChangeAnim(task, rect, 2048)` 播切换、再
 * `resetFlexibleTaskAfterChangeAnim(..., 1, 18)` 把任务真转成柔性任务（操作条 / 圆角都由那一步配）。
 * 与 [animateIntoMini] 的差别是**转换与动画是同一次调用**，不用「先收再补播」，也没有
 * 横屏那套落点对不上的问题。
 *
 * Activity 传 `null` 是合法的（门面里 `if-eqz` 保护，只靠 taskId 定位任务），所以宿主不需要
 * 拿到目标的 Activity 实例。权限闸是 `com.oplus.permission.safe.WINDOW` —— 宿主持有。
 *
 * 两处必须说清：
 *  · 返回 `true` 只表示**服务端受理**（任务不在过渡中、不是小窗、不是分屏内嵌），窗口成没成
 *    由 [MINI_VERIFY_MS] 之后那次验收说话；
 *  · 受理了却没进小窗时退回 recents 正门（[retryViaRecents]）—— 这一步保留老路的兜底，
 *    因为它至少能保证「进得去」。
 *
 * @return true = 已接手（调用方不要再退别的路）；false = 这一支没走通，调用方按原样降级
 */
internal fun MiniWindowLauncher.toggleIntoMini(taskId: Int): Boolean {
    val cls =
        Reflect.findClass(javaClass.classLoader, FLEXIBLE_APP_MANAGER) ?: run {
            XLog.w("zoom:mini → 没有 $FLEXIBLE_APP_MANAGER，退下一层")
            return false
        }
    val manager =
        Reflect.callStatic(cls, "getInstance") ?: run {
            XLog.w("zoom:mini → $FLEXIBLE_APP_MANAGER.getInstance() 返回空，退下一层")
            return false
        }
    //  **必须按签名取**（`Reflect.call` 是按运行时类型匹配的）：两个 boolean 是原始类型，
    //  Kotlin 传进去会装箱成 Boolean，永远匹配不上、还静默返回 null。
    val method =
        Reflect.method(
            cls,
            FLEXIBLE_TOGGLE_METHOD,
            android.app.Activity::class.java,
            java.lang.Integer.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE,
        ) ?: run {
            XLog.w("zoom:mini → $FLEXIBLE_APP_MANAGER 上没有 $FLEXIBLE_TOGGLE_METHOD/$FLEXIBLE_TOGGLE_ARGS，退下一层")
            return false
        }
    //  第 3 个参数 = 播不播切换动画（服务端只在它为 true 时 `startChangeAnim`）；
    //  第 4 个 = 任务已是小窗时直接拒绝（不来回切）。
    val accepted =
        try {
            method.invoke(manager, null, taskId, true, true) as? Boolean ?: false
        } catch (e: Throwable) {
            val cause =
                if (e is java.lang.reflect.InvocationTargetException) {
                    e.targetException ?: e
                } else {
                    e
                }
            XLog.w("zoom:mini → 原生切换调用抛了：${cause.javaClass.simpleName}: ${cause.message}")
            false
        }
    if (!accepted) {
        //  服务端这几种都返回 false：任务不在表里 / 正在过渡 / 分屏内嵌 / 正在拖拽。
        //  这一档不能当「应用不支持小窗」—— 退下一层继续试。
        XLog.w("zoom:mini → 原生切换没受理（返回 false，任务不在可切换状态），退下一层")
        return false
    }
    XLog.w("zoom:mini → 原生切换已受理（任务 #$taskId，落点与动画都由框架算），${MINI_VERIFY_MS}ms 后验收")
    postDelayedGuarded("zoom:mini 原生切换的验收", MINI_VERIFY_MS, fresh = true) {
        when (zoomStateOf(taskId)) {
            true -> {
                logZoomGeometry(taskId)
            }

            null -> {
                XLog.w("zoom:mini → 读不到任务 #$taskId 的窗口信息，无法验收")
            }

            false -> {
                XLog.w("zoom:mini → 原生切换受理了却没进小窗，退 recents 正门重收一次")
                retryViaRecents(taskId)
            }
        }
    }
    return true
}

/**
 * 收起档的补播：让框架的动画器把任务从「全屏外观」演到右上角那条。
 *
 * 能这么做的前提与展开档完全一样 —— 转换已经同步做完，任务已是柔性任务，
 * 动画器每帧那道 `isFlexibleTaskAndHasCaption` 守卫才过得去（§12.3 第 2 条）。
 * 差的是**终点**：这里用 [COLLAPSED_BOUNDS] / [COLLAPSED_SCALE]（右上角那条的屏幕矩形）。
 *
 * 三个「不播」的档都只记一行 `w`，不影响已经收起来的窗口：
 *  · 这台机器没量过收起档（[COLLAPSED_SCALE] 为 0）；
 *  · 当前不是竖屏 —— 落点是竖屏标定值，横屏会停在错误的位置；
 *  · 没有 Context。
 */
internal fun MiniWindowLauncher.playCollapsedMiniAnim(taskId: Int) {
    val ctx =
        context() ?: run {
            XLog.w("zoom:mini:collapse → 没有 Context，补播动画跳过")
            return
        }
    if (COLLAPSED_SCALE <= 0f) {
        XLog.w("zoom:mini:collapse → 这台机器没量过收起档的落点，只收不播")
        return
    }
    if (!animGeometryMatchesOrientation(ctx)) {
        XLog.w("zoom:mini:collapse → 当前不是竖屏，收起档的落点对不上 —— 这一下只收、不播动画")
        return
    }
    val params =
        WindowAnimParams(
            startRect = fullScreenRect(ctx),
            finishRect = COLLAPSED_BOUNDS,
            startScale = MINI_ANIM_START_SCALE,
            finishScale = COLLAPSED_SCALE,
            startAlpha = MINI_ANIM_START_ALPHA,
            finishAlpha = MINI_ANIM_FINISH_ALPHA,
            durationMs = MINI_ANIM_DURATION_MS,
        )
    val delay = MINI_ANIM_AFTER_CONVERT_DELAY_MS
    XLog.w("zoom:mini:collapse → 已原地收起，${delay}ms 后补播框架动画（起点全屏、终点右上角那条）")
    if (delay <= 0L) {
        platform.animateToMini(taskId, params)
    } else {
        postDelayedGuarded("收起档：转换后的动画", delay, fresh = true) {
            platform.animateToMini(taskId, params)
        }
    }
}

/**
 * 这个应用**在框架眼里**支不支持小窗 —— 「框架切换」那一条自己的准入闸。
 *
 * 为什么需要它：`toggleFlexibleWindow` 不按应用声明拦人，任务只是全屏就照转（真机表现：
 * 声明「不支持小窗」的应用也被强行收成小窗）。旧的 recents 正门不用这道闸，是因为服务端在
 * `startFreeFormFromRecent` 里判 `invalid flexibleTaskInfo` 直接不转 —— 换了入口，那层保护就没了。
 *
 * 判据是框架自己的 `OplusZoomWindowManager#isSupportZoomMode`（与「以小窗打开」那一页同一把尺子，
 * 见 [MiniWindowSupport]）：只有 `false` 才拦；`null`（判不出来）**不拦** —— 那是「不知道」，
 * 不是「不支持」，按老行为继续试。
 *
 * 注意它**不是纯查询**：判 false 时框架会顺手弹一条系统 toast「此应用尚未支持浮窗」。
 * 这里只在用户刚按键、正要转小窗的时刻问一次，不轮询。
 */
internal fun MiniWindowLauncher.appSupportsMini(taskId: Int): Boolean {
    val info = zoomInfoOf(taskId) ?: return true
    val top = Reflect.fieldValue(info, info.javaClass, TOP_APP_ACTIVITY_FIELD) ?: return true
    val component = componentOf(top, top.javaClass) ?: return true
    val supported = MiniWindowSupport.isSupported(component.packageName, component.className, MAIN_USER)
    if (supported == false) {
        XLog.w(
            "zoom:mini → ${component.flattenToShortString()} 不支持小窗" +
                "（框架 isSupportZoomMode 判 false），这一次不做",
        )
    }
    return supported != false
}

/**
 * 目标已经是前台任务时的**动画档**：原生切换 → 先转换再补播。
 *
 * 两条入口共用这一段：[MiniWindowLauncher.putForegroundInMiniWindow]（`zoom:mini`）与
 * [MiniWindowLauncher.launchAppInWindow] 的「目标就在最前面」那支（`window:包名`）。
 * 各写一份时先漂的必然是其中一条 —— 同一颗「前台转小窗前使用动画」开关在两条路上给出不同观感，
 * 用户只看得到「有时有动画、有时没有」。
 *
 * 准入闸只在这一档需要：[toggleIntoMini] 那条**不按应用声明拦人**（任务只是全屏就照转），
 * 而 recents 正门是服务端自己判 `invalid flexibleTaskInfo` 直接不转。所以判「不支持」就到此为止
 * （提示 + 不动屏幕）—— **不能再往下试**，试就是把它强行收起来。
 *
 * @return true = 已接手（含「框架判不支持、已报错」那一档），调用方不要再退别的路；
 *   false = 动画档没开，或这一档都没走通 —— 调用方照原样走无动画那条
 */
internal fun MiniWindowLauncher.animIntoMiniIfEnabled(
    ctx: Context,
    taskId: Int,
): Boolean {
    if (!isMiniAnim()) return false
    if (!appSupportsMini(taskId)) {
        onFailure(CommandFailure.APP_NO_MINI_WINDOW)
        return true
    }
    // 首选框架自己的切换（转换与动画同一次调用，落点由服务端按当前方向算、横屏也对）；
    // 它没受理才退「先真转换、再补播」（[animateIntoMini]）。
    return toggleIntoMini(taskId) || animateIntoMini(ctx, taskId)
}
