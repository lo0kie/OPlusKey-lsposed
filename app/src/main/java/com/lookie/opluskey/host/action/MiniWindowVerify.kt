package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

// 「小窗」那一族的**验收与几何** —— 从 MiniWindowLauncher 分出来（类不能用两个文件装，走扩展）。
// 它们只回答「窗口成没成 / 落在屏幕内没有 / 现在是什么档」，不参与起窗口那条流程。

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
internal fun MiniWindowLauncher.verifyMiniOrFallback(taskId: Int) {
    //  注意：「读不到任务信息」**不是**「没变成小窗」：
    //  `isTaskInZoomWindow` 在 `zoomInfoOf` 为 null（MiniWindowFacts.ROM 改了字段名 / 任务列表读不到）时
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
internal fun MiniWindowLauncher.logZoomGeometry(taskId: Int) {
    val rect = zoomScreenRect()
    val display = displaySize()
    when (zoomFitsOnScreen(rect, display)) {
        false -> {
            XLog.w(
                "zoom:mini → 任务 #$taskId 已是小窗，但**窗口超出屏幕**：屏幕矩形 $rect > 显示 $display" +
                    "（横屏应用走 recents 正门时会这样，见 docs/device-findings.md 附十三）",
            )
        }

        true -> {
            XLog.w("zoom:mini → 任务 #$taskId 已是小窗、窗口落在屏幕内（屏幕矩形 $rect）")
        }

        null -> {
            XLog.w("zoom:mini → 任务 #$taskId 已是小窗，但读不到屏幕矩形 / 显示尺寸，跳过几何验收")
        }
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
internal fun MiniWindowLauncher.zoomScreenRect(): android.graphics.Rect? =
    try {
        val cls = Reflect.findClass(javaClass.classLoader, MiniWindowSupport.MANAGER)
        val manager = cls?.let { Reflect.callStatic(it, "getInstance") }
        val method = cls?.let { Reflect.methodByNameAndArity(it, MiniWindowFacts.CURRENT_ZOOM_STATE_METHOD, 0) }
        val state = if (manager != null && method != null) Reflect.call(manager, method) else null
        state?.let { Reflect.fieldValue(it, it.javaClass, MiniWindowFacts.ZOOM_STATE_RECT_FIELD) as? android.graphics.Rect }
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
internal fun MiniWindowLauncher.displaySize(): android.graphics.Point? =
    try {
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
internal fun MiniWindowLauncher.zoomFitsOnScreen(
    rect: android.graphics.Rect?,
    display: android.graphics.Point?,
): Boolean? {
    if (rect == null || display == null) return null
    return rect.left >= 0 && rect.top >= 0 &&
        rect.right <= display.x && rect.bottom <= display.y
}

/** 那个任务现在是不是小窗 —— 只看 `windowingMode`（返回码和截图都会骗人）。 */
internal fun MiniWindowLauncher.isTaskInZoomWindow(taskId: Int): Boolean {
    val info = zoomInfoOf(taskId) ?: return false
    val mode = Reflect.fieldValue(info, info.javaClass, MiniWindowFacts.TOP_APP_WINDOWING_MODE_FIELD) as? Int
    return mode == MiniWindowFacts.WINDOWING_MODE_ZOOM
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
 * （见 [MiniWindowFacts.ZOOM_CONFIG_WIDTH_MIN_RATIO]）—— 若读到明显偏小，就是「屏幕矩形被当成配置矩形喂进去、
 * 又被多缩一次」那个 bug 的签名（真机读到过 `1020`，对应屏幕上只剩 `817`）。
 *
 * 读不到时返回 `null` 而**不是**猜一个值 —— 调用方据此走「没走通」那条降级。
 */
internal fun MiniWindowLauncher.taskZoomConfigRatio(taskId: Int): Float? {
    val info = zoomInfoOf(taskId) ?: return null
    val mode = Reflect.fieldValue(info, info.javaClass, MiniWindowFacts.TOP_APP_WINDOWING_MODE_FIELD) as? Int
    if (mode != MiniWindowFacts.WINDOWING_MODE_ZOOM) return null
    val bounds =
        Reflect.fieldValue(info, info.javaClass, MiniWindowFacts.TOP_APP_BOUNDS_FIELD)
            as? android.graphics.Rect ?: return null
    val screen =
        android.content.res.Resources
            .getSystem()
            .displayMetrics.widthPixels
    if (screen <= 0 || bounds.width() <= 0) return null
    return bounds.width().toFloat() / screen
}

/** `getAllTopAppInfos()` 里那个 taskId 对应的 `OplusAppInfo`；没有就 null。 */
internal fun MiniWindowLauncher.zoomInfoOf(taskId: Int): Any? {
    val infos = topAppInfos() ?: return null
    for (item in infos) {
        val info = item ?: continue
        val id = Reflect.fieldValue(info, info.javaClass, MiniWindowFacts.TOP_APP_TASK_ID_FIELD) as? Int ?: continue
        if (id == taskId) return info
    }
    return null
}
