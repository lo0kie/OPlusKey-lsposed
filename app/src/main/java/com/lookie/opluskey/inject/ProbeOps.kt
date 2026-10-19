package com.lookie.opluskey.inject

import android.content.Intent
import android.os.Handler
import com.lookie.opluskey.bridge.extOf
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

// 探针的四个 op：看任务状态（`state`）、给窗口设圆角（`corner`）、「补壳」（`dress`）与其内部动作。
// 从 ProbeChannel 分出来（object 不能用两个文件装，走扩展）；它用的那几个小工具留在那边。

// ── op: state ────────────────────────────────────────────────────────────

/** 只读打印：目标任务的三样东西 —— 窗口模式、几何、以及 TaskExt 上那组柔性状态字段。 */
internal fun ProbeChannel.state(
    loader: ClassLoader,
    intent: Intent,
) {
    // `taskOf` 第一步就是 `FlexibleWindowManagerService.getInstance()`，那会触发宿主类的 `<clinit>`
    //  —— 与 `call` 同一条险路，所以同样要显式 opt-in（见 [allowUnsafe]）。
    if (!allowUnsafe(SERVICE_CLASS, OP_STATE, intent)) return
    val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
    val task = taskOf(loader, taskId)
    if (task == null) {
        XLog.w("probe/state：拿不到任务 #$taskId")
        return
    }
    XLog.w("probe/state 任务 #$taskId bounds=${Reflect.call(task, "getBounds")}")
    val ext = extOf(task)
    if (ext == null) {
        XLog.w("probe/state：读不到 TaskExt")
        return
    }
    listOf("mScenario", "mHasCaption", "mFlexibleZoomState", "mCaptionName", "mFlexibleWindowState")
        .forEach { f ->
            val v =
                try {
                    Reflect.fieldValue(ext, ext.javaClass, f) ?: "（没有这个字段）"
                } catch (e: Throwable) {
                    "（读失败 ${e.javaClass.simpleName}）"
                }
            XLog.w("probe/state  ext.$f = $v")
        }
    // 操作条视图到底建出来了没有 —— 这是 C 方案要回答的第一个问题
    listOf("getFlexibleCaptionView", "getCaptionView", "mCaptionView").forEach { n ->
        val v =
            try {
                Reflect.call(ext, n) ?: Reflect.fieldValue(ext, ext.javaClass, n)
            } catch (e: Throwable) {
                null
            }
        if (v != null) {
            XLog.w("probe/state  ext.$n → $v")
            // 视图在不在是一回事，它有没有真的显示出来是另一回事 —— 这一位就是「挂上/显示了没有」
            XLog.w("probe/state  ·mCaptionViewSurfaceShow = ${fieldOf(v, "mCaptionViewSurfaceShow")}")
            stateOfCaptionView(v)
        }
    }
}

// ── op: corner ───────────────────────────────────────────────────────────

/**
 * 给目标任务的窗口 surface 设圆角 —— C 方案的第一半。
 *
 * `SurfaceControl.Transaction#setCornerRadius` 是**公开 API**，所以这一半不依赖任何私有签名；
 * 困难只在「从 Task 拿到 SurfaceControl」，候选名逐个试并把命中的写进日志。
 */
internal fun ProbeChannel.corner(
    loader: ClassLoader,
    intent: Intent,
) {
    if (!allowUnsafe(SERVICE_CLASS, OP_CORNER, intent)) return
    val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
    val radius =
        if (intent.hasExtra(EXTRA_RADIUS)) {
            intent.getFloatExtra(EXTRA_RADIUS, DEFAULT_CORNER_RADIUS)
        } else {
            DEFAULT_CORNER_RADIUS
        }
    val task = taskOf(loader, taskId)
    if (task == null) {
        XLog.w("${describe(OP_CORNER)}：拿不到任务 #$taskId")
        return
    }
    cornerOn(task, radius, taskId)
}

/** 给一个任务的窗口 surface 设圆角（`corner` 与 `dress` 共用）。 */
internal fun ProbeChannel.cornerOn(
    task: Any,
    radius: Float,
    taskId: Int,
) {
    val sc = surfaceControlOf(task)
    if (sc == null) {
        XLog.w("${describe(OP_CORNER)}：从任务 #$taskId 上拿不到 SurfaceControl（候选名都没命中）")
        return
    }
    val tx = android.view.SurfaceControl.Transaction()
    try {
        // `setCornerRadius(SurfaceControl, float)` 在公开 SDK 里没有（`SurfaceControl` 类本身有），
        // 所以这里也得走反射 —— 同样按**原始类型**取 Method（Float 装箱坑）。
        val m =
            Reflect.method(
                tx.javaClass,
                "setCornerRadius",
                android.view.SurfaceControl::class.java,
                java.lang.Float.TYPE,
            )
        if (m == null) {
            XLog.w("${describe(OP_CORNER)}：这个 ROM 的 Transaction 上没有 setCornerRadius(SurfaceControl,float)")
            return
        }
        Reflect.call(tx, m, sc, radius)
        Reflect.call(tx, "apply")
        XLog.w("${describe(OP_CORNER)}：任务 #$taskId 的 surface 圆角已设为 $radius")
    } catch (e: Throwable) {
        XLog.w("${describe(OP_CORNER)} setCornerRadius 失败：${e.javaClass.simpleName}: ${e.message}")
    } finally {
        // 这条 Transaction 是**自己建、自己 apply** 的：不关就泄一个 native 对象
        //  （每一个提前 return 的分支都会漏一份）。
        //  保护写 `catch (Throwable)` 而不是 `runCatching`：后者只给日志出口与反射查找用
        //  （AGENTS.md 五之二、2）—— 这里要的是「关不掉也绝不外抛」，与同目录 `KeyWatch` 一致。
        try {
            tx.close()
        } catch (e: Throwable) {
            XLog.d("关闭 Transaction 失败（已忽略）：${e.javaClass.simpleName}")
        }
    }
}

/**
 * `dress` —— 「补壳」：把伪造态小窗缺的那两样补上（**圆角** + **操作条挂载/显示**）。
 *
 * 三个开关分开传，一次重启后就能任意组合试（每一步单独打日志）：
 * `radius`（给了才做圆角）/ `caption`（挂到任务上）/ `show`（显示）。
 *
 * 配方来自用本通道 `dump` 出来的真签名：
 * `FlexibleWindowManagerService.createFlexibleCaptionView(Task, String)`、
 * `FlexibleCaptionView.initCaptionViewAndAttachToTask(Task, Rect)`、`showCaptionViewWithAnim()`。
 */
internal fun ProbeChannel.dress(
    loader: ClassLoader,
    intent: Intent,
) {
    if (!allowUnsafe(SERVICE_CLASS, OP_DRESS, intent)) return
    val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
    val task = taskOf(loader, taskId)
    if (task == null) {
        XLog.w("${describe(OP_DRESS)}：拿不到任务 #$taskId")
        return
    }
    if (intent.hasExtra(EXTRA_RADIUS)) {
        cornerOn(task, intent.getFloatExtra(EXTRA_RADIUS, DEFAULT_CORNER_RADIUS), taskId)
    }
    val doCaption = intent.getBooleanExtra(EXTRA_CAPTION, false)
    val doShow = intent.getBooleanExtra(EXTRA_SHOW, false)
    if (!doCaption && !doShow) {
        XLog.w("${describe(OP_DRESS)}：这一把只做了圆角（没带 caption / show）")
        return
    }

    val view = captionViewOf(loader, task)
    if (view == null) {
        XLog.w("${describe(OP_DRESS)}：既拿不到、也建不出 FlexibleCaptionView")
        return
    }
    XLog.w("${describe(OP_DRESS)}：caption view = $view")
    val doInit = intent.getBooleanExtra(EXTRA_INIT, false)
    // attach 的第二个参数**就是操作条自己的尺寸**（不是任务 bounds）——
    // 真机实测：传任务 bounds 它就把视图撑成整窗（getWidth/Height == 任务尺寸）。
    // 所以这里允许显式传 rect（`--ei rect_r 1272 --ei rect_b 157` 一类），不传才退回任务 bounds。
    val bounds =
        if (intent.hasExtra(EXTRA_RECT_R)) {
            android.graphics.Rect(
                intent.getIntExtra(EXTRA_RECT_L, 0),
                intent.getIntExtra(EXTRA_RECT_T, 0),
                intent.getIntExtra(EXTRA_RECT_R, 0),
                intent.getIntExtra(EXTRA_RECT_B, 0),
            )
        } else {
            Reflect.call(task, "getBounds")
        }

    // 注意：**这两步必须在视图自己的线程上做**。视图属于 `android.ui` 线程，而从 AMS 广播入口进来是
    // binder 线程 —— 直接调会抛 `CalledFromWrongThreadException`（真机实测）。
    // 用**视图自己的 Handler**（`View.getHandler()`）post 过去，线程一定对，不用猜是哪个 looper。
    val work =
        Runnable {
            //  这一段可能被 post 到 **system_server 的 `android.ui` 线程**（见上），而里面每一行
            //  都是一次反射调用 —— 任何一条抛出来都是那个线程上的未捕获异常，代价是整个
            //  system_server 崩掉（开机循环）。所以整段过 guard。
            XLog.guard("${describe(OP_DRESS)}：操作条补壳") {
                if (doCaption) {
                    // 先 init 再 attach：attach 假定视图已经初始化过
                    if (doInit) {
                        XLog.w("${describe(OP_DRESS)}：initCaptionView → ${invokeOn(view, "initCaptionView")}")
                    }
                    XLog.w(
                        "${describe(OP_DRESS)}：initCaptionViewAndAttachToTask($bounds) → " +
                            invokeOn(view, "initCaptionViewAndAttachToTask", task, bounds),
                    )
                }
                if (doShow) {
                    XLog.w("${describe(OP_DRESS)}：showCaptionViewWithAnim → ${invokeOn(view, "showCaptionViewWithAnim")}")
                    XLog.w(
                        "${describe(OP_DRESS)}：updateFlexibleTaskCaptionVisible(true) → " +
                            invokeOn(view, "updateFlexibleTaskCaptionVisible", task, true),
                    )
                }
                // 尺寸对了之后还得让它重排 / 换模式：`handleButtonRegion` 空着说明手柄区没算出来
                if (intent.getBooleanExtra(EXTRA_RELAYOUT, false)) {
                    XLog.w("${describe(OP_DRESS)}：onRelayoutCaptionView → ${invokeOn(view, "onRelayoutCaptionView")}")
                }
                if (intent.getBooleanExtra(EXTRA_MODE, false)) {
                    XLog.w("${describe(OP_DRESS)}：updateCaptionViewMode(true) → ${invokeOn(view, "updateCaptionViewMode", true)}")
                }
                if (intent.getBooleanExtra(EXTRA_TOUCH, false)) {
                    // 触及区域是它算的：不算就没有 handleButtonRegion，操作条画得出来但点不动
                    XLog.w("${describe(OP_DRESS)}：updateTouchRegion → ${invokeOn(view, "updateTouchRegion")}")
                }
                XLog.w("${describe(OP_DRESS)}：mCaptionViewSurfaceShow = ${fieldOf(view, "mCaptionViewSurfaceShow")}")
                stateOfCaptionView(view)
            }
        }
    val viewHandler =
        try {
            Reflect.call(view, "getHandler") as? Handler
        } catch (e: Throwable) {
            null
        }
    if (viewHandler != null) {
        XLog.w("${describe(OP_DRESS)}：把补壳两步 post 到视图自己的线程（${viewHandler.looper.thread.name}）")
        viewHandler.post(work)
    } else {
        XLog.w("${describe(OP_DRESS)}：视图没有 Handler，只能就地做（大概率 WrongThread）")
        work.run()
    }
}
