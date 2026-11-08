package com.lookie.opluskey.host.action

import android.content.Context
import com.lookie.opluskey.bridge.MiniWindowSupport
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

// 「小窗」那一族的**验收与几何** —— 从 MiniWindowLauncher 分出来（类不能用两个文件装，走扩展）。
// 它们只回答「窗口成没成 / 落在屏幕内没有 / 现在是什么档」，不参与起窗口那条流程。

/**
 * 等 transition 跑完核一眼：**只报事实，不换路**。
 *
 *  没变成小窗时**不退「按包名另起一份实例」**（用户口径：那会回桌面 + 重开一份，
 *  比没反应更糟），只记一行 W 并报 `APP_NO_MINI_WINDOW`；几何越界同样只记日志。
 *  判据是**窗口的屏幕矩形有没有落在屏幕内**，不是「配置宽占比」——
 * 后者在横屏下会**给出假绿灯**：分母 `Resources.getSystem().displayMetrics.widthPixels`
 * 不随旋转变（恒 1272），而横屏应用的配置矩形宽是 **2240**（= 横屏全屏任务的宽），
 * 比值读到 `1.76`，落在「≥0.95 就算好」那一边。
 *
 * 真机症状（用户报「横屏小窗尺寸大」）：屏幕矩形 `1316 × 741`，
 * **宽 1316 > 屏宽 1272、右侧溢出 185px**。取证与根因见 `docs/device-findings.md` 附十三。
 */
internal fun MiniWindowLauncher.verifyMiniOrFallback(taskId: Int) {
    //  注意：「读不到任务信息」**不是**「没变成小窗」—— 折成后者就等于把「不知道」说成
    //  「服务端受理了但没收起来」，弹一句「这个应用不支持小窗」，把一个读不到的事实说成应用的锅。
    //  三态见 [zoomStateOf]。
    //  三态见 [zoomStateOf]：`null`（读不到）与 `false`（真不是小窗）必须分开处理。
    when (zoomStateOf(taskId)) {
        null -> {
            XLog.w("zoom:mini → 读不到任务 #$taskId 的信息，无法验收（不报「不支持小窗」）")
            return
        }

        false -> {
            // **什么都不做**：服务端受理了（返回 `2`）却没收出窗口，
            // 就是「这个应用不支持小窗」（真机：`com.heytap.market`）。以前这里退
            // 「按包名另起一份实例」⇒ 回桌面 + 重新起一份，用户看到「一按侧键就回桌面重开」，
            // 比没反应更糟。而且那条还会**重复调一次** `startFlexibleFromRecents`。
            XLog.w("zoom:mini → 任务 #$taskId 没变成小窗（服务端受理了但没收起来），什么都不做")
            onFailure(CommandFailure.APP_NO_MINI_WINDOW)
            return
        }

        true -> {
            logZoomGeometry(taskId)
        }
    }
}

/**
 * 只报事实：小窗在**屏幕上**的矩形、以及它有没有超出屏幕。
 *
 * 注意：「超出屏幕」这一档**只记日志、不换路**（定的）：横屏应用走 recents 正门时
 * 比例是对的、几何偏大；换路（launch 配方）会变成竖屏比例但进屏 —— 两者取舍还没收敛，
 * 自动换路会让屏幕上变成「横屏比例一闪 → 竖屏比例」，反而看不出比例对不对。
 * 要顺着这条路继续调时，把这里的 `false` 分支接到降级上即可。
 *
 * **几何的归属要说清**：`getCurrentZoomWindowState()` 给的是「**此刻屏幕上那个小窗**」，
 * 不是「taskId 那一份」（那是个全局单例状态）。所以下面先读状态自带的 taskId：
 *  · 读得到且等于 [taskId] ⇒ 这一行确实是它在说；
 *  · 读得到但不等于 ⇒ 屏幕上那个小窗是**别人**，这一行不作数（会明说）；
 *  · 读不到 ⇒ 只能说「屏幕上那个小窗」，不能替 [taskId] 认领（原先就是直接按 taskId 报的，
 *    同时开两个小窗时会给出「#A 超出屏幕」而屏幕上是 B）。
 */
internal fun MiniWindowLauncher.logZoomGeometry(taskId: Int) {
    val state = zoomState()
    val rect = zoomRectOf(state)
    val display = displaySize()
    val who = geometryOwner(state, taskId)
    when (zoomFitsOnScreen(rect, display)) {
        false -> {
            XLog.w(
                "zoom:mini → $who 的窗口**超出屏幕**：屏幕矩形 $rect > 显示 $display" +
                    "（横屏应用走 recents 正门时会这样，见 docs/device-findings.md 附十三）",
            )
        }

        true -> {
            XLog.w("zoom:mini → $who 的窗口落在屏幕内（屏幕矩形 $rect）")
        }

        null -> {
            XLog.w("zoom:mini → $who 已是小窗，但读不到屏幕矩形 / 显示尺寸，跳过几何验收")
        }
    }
}

/**
 * 这块几何到底属于哪一份任务 —— 日志里那句话的主语。
 *
 *  `getCurrentZoomWindowState()` 那个状态**不一定带 taskId 字段**（没在真机上验过，按名字读，
 *  读不到就是 -1）。读不到时**不替调用方认领**：只写「屏幕上那个小窗」，并说明读不到 id。
 *  不猜的理由与全仓那条一致 —— 把「不知道」说成「#A 超出屏幕」，排查时会被带到别的任务上。
 */
internal fun MiniWindowLauncher.geometryOwner(
    state: Any?,
    taskId: Int,
): String {
    val owner =
        try {
            (state?.let { Reflect.fieldValue(it, it.javaClass, ZOOM_STATE_TASK_ID_FIELD) } as? Int) ?: -1
        } catch (e: Throwable) {
            XLog.d("读小窗状态的 taskId 失败（按读不到处理）：${e.message}")
            -1
        }
    return when {
        owner <= 0 -> "屏幕上当前那个小窗（这块状态里读不到 taskId，无法确认是不是 #$taskId）"
        owner == taskId -> "任务 #$taskId"
        else -> "任务 #$owner（**不是**这次验收的 #$taskId，这一行不作数）"
    }
}

/**
 * `OplusZoomWindowInfo` 上那个 taskId 字段名 —— 按惯例是 `taskId`。
 *
 *  与 `MiniWindowFacts` 里那几个**验证过**的字段名不同：这一个**没在真机上验过**
 *  （读不到就是读不到，不影响任何判定，只影响日志能不能点名），所以留在本文件而不进事实表。
 */
private const val ZOOM_STATE_TASK_ID_FIELD = "taskId"

/**
 * 「此刻屏幕上那个小窗」的原始状态对象 —— 框架
 * `com.oplus.zoomwindow.OplusZoomWindowManager#getCurrentZoomWindowState()` 的返回。
 *
 *  **它是全局单例状态，不带「我要哪一份」的入参**：读到的永远是**当前显示着的那个**小窗。
 *  要把它和某个 taskId 对上，只能看状态自己有没有 taskId 字段（见 [geometryOwner]）。
 *  读不到 / 抛异常都返回 `null`（调用方据此跳过验收，不猜）。
 */
internal fun MiniWindowLauncher.zoomState(): Any? =
    try {
        val cls = Reflect.findClass(javaClass.classLoader, MiniWindowSupport.MANAGER)
        val manager = cls?.let { Reflect.callStatic(it, "getInstance") }
        val method = cls?.let { Reflect.methodByNameAndArity(it, MiniWindowFacts.CURRENT_ZOOM_STATE_METHOD, 0) }
        if (manager != null && method != null) Reflect.call(manager, method) else null
    } catch (e: Throwable) {
        XLog.d("读小窗状态失败：${e.message}")
        null
    }

/**
 * 小窗**在屏幕上的**矩形 —— 取自上面那个状态的 `zoomRect`。
 *
 * 注意：它和 [taskZoomConfigRatio] 读的 `OplusAppInfo.appBounds` **不是一回事**：
 * `appBounds` 是**配置矩形**（不含显示缩放），这个才是**乘过 scale 的屏幕矩形** ——
 * 判「窗口多大 / 装不装得下」只能用它。
 *
 * 用**截图像素级边缘检测**核对过它就是屏幕上那块矩形：真机左边界 `x=141`、
 * 下边界 `y=1006`，与日志里的 `zoomRect = Rect(141, 266 - 1457, 1007)` 对得上。
 */
internal fun MiniWindowLauncher.zoomRectOf(state: Any?): android.graphics.Rect? =
    try {
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

/**
 * 那个任务现在是不是小窗 —— 只看 `windowingMode`（返回码和截图都会骗人）。
 *
 *  **三态**：`true` / `false` / `null` = **读不到**（任务信息取不到、或者那个字段名在这个 ROM
 *  上对不上）。调用方必须把 `null` 与 `false` 分开处理 —— 折成 `false` 就把「不知道」说成了
 *  「不是小窗」，于是一条已经收好的小窗会被当成失败：重起一次全屏把它顶掉（
 *  `verifyMiniOrFallback` 的注释里记着这个坑，AppShortcut / 收起档 / 原地收三处也同一个道理）。
 */
internal fun MiniWindowLauncher.zoomStateOf(taskId: Int): Boolean? {
    val info = zoomInfoOf(taskId) ?: return null
    val mode =
        Reflect.fieldValue(info, info.javaClass, MiniWindowFacts.TOP_APP_WINDOWING_MODE_FIELD) as? Int
            ?: return null
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
