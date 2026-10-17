package com.lookie.opluskey.bridge

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

// 本地那条「全屏 → 小窗」：`switchState(task, 1, reason)` + 冒充柔性全屏态与失败回滚。
// 原来都在 FlexibleWindowBridge 里，与发广播 / 收广播那一段无关，搬出来成扩展。

/**
 * 把任务切到小窗态 —— 调框架自己的 `FlexibleTaskController#switchState(task, 1, reason)`。
 *
 * 它在任务不是「带标题栏的柔性任务」时**第一句就返回**，所以对普通全屏任务是一次空转；
 * 柔性任务处于小窗态时同样直接返回（`currentState == nextState`）；只有柔性任务处在**全屏态**时
 * 才会真的播动画。也就是说这条调用在任何状态下都不会乱动窗口，最坏是没反应。
 *
 * @param requestedBounds 宿主下发的应用配置矩形；null 则用 [EXPANDED_BOUNDS] 兜底
 * @param requestedScale  宿主下发的显示缩放；null 则用 [EXPANDED_SCALE] 兜底
 */
internal fun FlexibleWindowBridge.switchToMiniLocal(
    loader: ClassLoader?,
    taskId: Int,
    requestedBounds: android.graphics.Rect?,
    requestedScale: Float?,
) {
    if (loader == null) {
        XLog.w("还没有 system_server 的 ClassLoader，小窗指令跳过")
        return
    }
    if (taskId <= 0) {
        XLog.w("小窗指令没带 taskId，跳过")
        return
    }
    val serviceCls =
        Reflect.findClass(loader, SERVICE) ?: run {
            XLog.w("找不到 $SERVICE，小窗指令跳过")
            return
        }
    val service =
        Reflect.callStatic(serviceCls, "getInstance", null) ?: run {
            XLog.w("$SERVICE.getInstance() 拿不到实例，小窗指令跳过")
            return
        }
    val controller =
        Reflect.call(service, ROM.flexibleControllerGetter) ?: run {
            XLog.w("$SERVICE.getFlexibleTaskController() 拿不到控制器，小窗指令跳过")
            return
        }
    val atms =
        Reflect.fieldValue(controller, controller.javaClass, FIELD_ATMS) ?: run {
            XLog.w("读不到 $SERVICE 的 $FIELD_ATMS，小窗指令跳过")
            return
        }
    val root =
        Reflect.fieldValue(atms, atms.javaClass, FIELD_ROOT) ?: run {
            XLog.w("读不到 $FIELD_ROOT，小窗指令跳过")
            return
        }
    val task =
        anyTask(root, taskId, "小窗指令") ?: run {
            XLog.w("任务 #$taskId 三种 matchMode 都找不到，小窗指令跳过")
            return
        }
    XLog.w(
        "小窗指令：任务 #$taskId bounds=${taskBounds(task)} " +
            "flexibleZoomState=${flexibleZoomState(task)}",
    )

    // 让普通全屏任务「冒充」柔性全屏态：`switchState` 的前置只看 TaskExt 上这三个字段。
    // 改完它就会走框架自己的 full→mini 状态迁移（含原生动画），不需要我们先做一次应用侧转换。
    // 见 docs/device-findings.md §17.6.4 附六。
    val ext = extOf(task)
    val record = if (ext != null) Reflect.call(task, "getTopNonFinishingActivity") else null

    // (1) 预置落点几何：`switchState` 收缩时的 finishRect 取自 `ext.getRememberPosInfo()`，
    //    不预置就走「按应用比例算默认」的分支 —— 用全屏宽高比缩放，窗口被拉得很长。
    //
    //    注意：**不能用 `controller.getMiniFlexibleTaskInfo`**（真机踩过）：
    //    它返回的是**收起档**几何（右上角一条，真机 ≈ `Rect(898,168-1203,710)` / `0.2397`），
    //    喂进去就等于把「展开」的动画落到了收起的位置 —— 真机表现是「按一下直接变成右上角
    //    那条最小化窗」。
    //    注意：也别把**屏幕矩形**直接当参数喂 —— 它要的是应用配置矩形，尺寸还会被再乘一次 scale。
    //    两个坑的取值与推算过程都写在 `core/model/MiniWindowGeometry` 的 [Slot.bounds] 注释里，
    //    改之前先读那段。
    //    `setRememberPosInfo(Rect, float)` 含原始类型参数，必须显式拿 Method（见 [anyTask] 的装箱坑）。
    if (ext != null && record != null) {
        val setPos =
            Reflect.method(
                ext.javaClass,
                "setRememberPosInfo",
                android.graphics.Rect::class.java,
                java.lang.Float.TYPE,
            )
        if (setPos != null) {
            // 宿主下发优先，缺了才用本文件的兜底默认值。
            val bounds = requestedBounds ?: EXPANDED_BOUNDS
            val scale = requestedScale ?: EXPANDED_SCALE
            val from = if (requestedBounds != null || requestedScale != null) "宿主下发" else "注入侧兜底默认"
            // `setRememberPosInfo` 不返回值，而 `Reflect.call` 会把「方法在、调用抛了」吞成 null ——
            // 于是「已预置」无论成败都照打。这里自己 invoke：**成功才打**，抛了就把断点如实报出来。
            try {
                setPos.invoke(ext, bounds, scale)
                // 这条日志要能独立验收：配置矩形 → 乘 scale → 屏幕落点，一眼可核对。
                // 真机上它打的屏幕落点应当是 Rect(126, 319 - 1145, 2131)（宽 1019、高 1812）。
                XLog.w(
                    "小窗指令：已预置展开档几何（$from）配置矩形=$bounds scale=$scale" +
                        " → 屏幕落点应为 ${screenBoundsOf(bounds, scale)}",
                )
            } catch (e: Throwable) {
                val cause = Reflect.deepestCause(e)
                XLog.w(
                    "小窗指令：预置展开档几何失败（setRememberPosInfo(Rect,float) 调用抛了）" +
                        " —— 落点会走默认比例（会偏长）：${cause.javaClass.name}: ${cause.message}",
                )
            }
        } else {
            XLog.w("小窗指令：找不到 setRememberPosInfo(Rect,float)，落点会走默认比例（会偏长）")
        }
    } else {
        val missing = mutableListOf<String>()
        if (ext == null) missing += "TaskExt"
        if (record == null) missing += "getTopNonFinishingActivity"
        XLog.w("小窗指令：没预置展开档几何（缺 $missing）—— 落点会走默认比例（会偏长）")
    }

    // 喂给 `TaskExtImpl#updateFlexibleTaskParams` 的能力配置 —— **操作条（caption）就是它开的**
    // （`androidx.activity.HasCaption`），缩放范围 / 圆角 / 忽略系统栏也在这里。键值抄自真机
    // 上一次真实小窗启动的 `mOplusExtraBundle`（见 docs/device-findings.md §17.6.4 附六）。
    // (2) 操作条的实现类名来自策略配置（`getCaptionName(scenario)`），不喂的话
    //    `mCaptionName = null` → `Class.forName(null)` 失败 → 视图建不出来。
    val getCaption = Reflect.method(serviceCls, "getCaptionName", java.lang.Integer.TYPE)
    val captionName = getCaption?.let { Reflect.call(service, it, CAPTION_SCENARIO_FULL_SCREEN) } as? String
    XLog.w("小窗指令：caption 实现类 = $captionName")
    val bundle =
        android.os.Bundle().apply {
            putBoolean("androidx.activity.HasCaption", true)
            if (captionName != null) putString("androidx.activity.CaptionName", captionName)
            putInt("androidx.activity.LaunchInsensible", 0)
            putInt("androidx.activity.task.attach.blur", 0)
            putInt("androidx.activity.LaunchCornerRadius", 91)
            putInt("androidx.activity.LaunchShadowRadius", 200)
            putInt("androidx.activity.LaunchScenario", SCENARIO_FULL_SCREEN)
            putFloat("androidx.activity.LaunchScale", 0.8016529f)
            putFloat("androidx.activity.flexibleTaskCaptionViewMaxScale", 0.8083333f)
            putFloat("androidx.flexible.Alpha", 1.0f)
            putInt("androidx.flexible.task_snapshot_mode", 0)
            putBoolean("flexible.floatwindow.is_multi_instance", false)
            putBoolean("flexible_super_mini_state", false)
            putInt("androidx.activity.key_flexible_task_pending_exit_to", 0)
        }
    val optionsCls = Reflect.findClass(loader, "android.app.ActivityOptions")
    val options = optionsCls?.let { Reflect.callStatic(it, "makeBasic") }
    if (ext != null && record != null && optionsCls != null && options != null) {
        val upd =
            Reflect.method(
                ext.javaClass,
                ROM.flexibleTaskParamsMethod,
                android.os.Bundle::class.java,
                optionsCls,
                record.javaClass,
            )
        if (upd != null) {
            // 同 setRememberPosInfo：无返回值，自己 invoke 才能把「方法在、调用抛了」与成功分开。
            try {
                upd.invoke(ext, bundle, options, record)
                XLog.w("小窗指令：已喂 updateFlexibleTaskParams（含 HasCaption=true）")
            } catch (e: Throwable) {
                val cause = Reflect.deepestCause(e)
                XLog.w(
                    "小窗指令：喂 updateFlexibleTaskParams 失败（调用抛了）" +
                        " —— 操作条（caption）建不出来：${cause.javaClass.name}: ${cause.message}",
                )
            }
        } else {
            XLog.w("小窗指令：找不到 updateFlexibleTaskParams(Bundle,ActivityOptions,ActivityRecord)")
        }
    } else {
        val missing = mutableListOf<String>()
        if (ext == null) missing += "TaskExt"
        if (record == null) missing += "getTopNonFinishingActivity"
        if (optionsCls == null) missing += "ActivityOptions"
        if (options == null) missing += "ActivityOptions.makeBasic()"
        XLog.w("小窗指令：没喂 updateFlexibleTaskParams（缺 $missing）—— 操作条（caption）建不出来")
    }

    val fake = fakeFlexibleFullScreen(task)
    if (fake.ext == null) {
        XLog.w("小窗指令：读不到任务的 TaskExt，跳过 switchState")
        return
    }
    // 三个字段逐个落到日志里：任何一个没写进去，switchState 的前置就不成立，
    // 后面那一句「已调用」也就不该当作成功来读（三个 `?.set` 静默可选写
    // + 无条件打「已置 …」，失败被伪装成成功，正是降级纪律要消灭的那种）。
    if (fake.missing.isEmpty()) {
        XLog.w(
            "小窗指令：冒充柔性全屏态成功（mScenario=$SCENARIO_FULL_SCREEN / mHasCaption=true" +
                " / mFlexibleZoomState=$ZOOM_STATE_FULL_SCREEN）",
        )
    } else {
        XLog.w("小窗指令：冒充柔性全屏态**只写进去一部分**，缺 ${fake.missing} —— switchState 大概率不生效")
    }
    if (!callSwitchState(controller, task, taskId)) {
        XLog.w(
            "小窗指令：switchState(#$taskId, $STATE_MINI) 没调成功 —— 小窗不会出现（上面一行写了断在哪）；" +
                rollbackFake(fake),
        )
        return
    }
    XLog.w("小窗指令：switchState(#$taskId, $STATE_MINI) 已调用（应播「全屏 → 小窗」原生动画）")
}

// 取任务那一步（`anyTaskForId` 三档 matchMode）**不在本文件**：它与探针共用一份
// （见 `bridge/FlexibleWindowLookup.kt` 的 `anyTask`）—— 两边各写一份时先漂的总是探针。
//
// 同样绕开装箱匹配：`switchState(Task, int, String)`。@return 是否真的调到了。

/**
 * 调 `controller.switchState(task, 1, REASON)`。
 *
 * @return **真的调进去了**（方法找到 **且** 调用没抛）。
 *
 *  注意：不能「方法找到就 `return true`」：`switchState` 不返回值，
 *  而 `Reflect.call` 的 null 既可能是成功也可能是抛了 —— 于是「方法在、调用炸了」也会被
 *  上游那句「已调用（应播原生动画）」当成成功来读，排查时白查一轮。
 *  这里自己 `invoke` 一次，把「找不到方法」与「调用抛了」分开报。
 */
internal fun FlexibleWindowBridge.callSwitchState(
    controller: Any,
    task: Any,
    taskId: Int,
): Boolean {
    val method =
        Reflect.method(
            controller.javaClass,
            ROM.flexibleSwitchStateMethod,
            task.javaClass,
            java.lang.Integer.TYPE,
            String::class.java,
        ) ?: run {
            XLog.w("找不到 switchState(Task,int,String)，动画调不了")
            return false
        }
    return try {
        method.isAccessible = true
        method.invoke(controller, task, STATE_MINI, REASON)
        true
    } catch (e: Throwable) {
        val cause =
            if (e is java.lang.reflect.InvocationTargetException) {
                e.targetException ?: e
            } else {
                e
            }
        XLog.w(
            "switchState(#$taskId, $STATE_MINI) 调用抛了 —— 小窗不会出现：" +
                "${cause.javaClass.simpleName}: ${cause.message}",
        )
        false
    }
}

/** `task.getBounds()` —— 只读来打日志。 */
internal fun FlexibleWindowBridge.taskBounds(task: Any): Any? =
    try {
        Reflect.call(task, "getBounds")
    } catch (e: Throwable) {
        null
    }

/** `TaskExtImpl#getFlexibleZoomState()` —— 只读来打日志，读不到就 null。 */
internal fun FlexibleWindowBridge.flexibleZoomState(task: Any): Any? =
    try {
        extOf(task)?.let { Reflect.call(it, "getFlexibleZoomState") }
    } catch (e: Throwable) {
        null
    }

/**
 * 把普通全屏任务「冒充」成柔性任务、且处于**全屏态**：
 * `mScenario = 1`（launchScenario）、`mHasCaption = true`、`mFlexibleZoomState = 2`。
 *
 * `switchState` 的前置只看这三个字段，而它后半段自己完成窗口几何的切换与动画 ——
 * 所以不需要先做一次应用侧转换，也不会有可见的中间态。转完后任务**真的**是柔性任务，
 * 这三个字段与事实一致，不会留下假状态。
 *
 * @param ext TaskExt；null 表示任务本身读不出来（连 `getExtImpl()` 都没有）。
 * @param missing 没能写进去的字段名；调用方据此决定这条日志该怎么写 ——
 *   `ext != null` **不等于**冒充成功，见本函数的实现注释。
 */
internal fun FlexibleWindowBridge.fakeFlexibleFullScreen(task: Any): FakeResult {
    val ext = extOf(task) ?: return FakeResult(null, listOf("TaskExt 读不出（getExtImpl）"))
    val cls = ext.javaClass
    val missing = mutableListOf<String>()

    // 已写入字段的回滚动作（字段名 → 写回原值，返回 null 表示写回成功）。每个 put 都**在写入前**
    // 先抓一份原值登记进来，`switchState` 失败时按它复位：那三个字段是**假值**（任务其实不是柔性
    // 任务），留在任务上会让它长期对外声称自己是「带 operation bar 的柔性任务」。
    // 字段本来就找不到、或写入抛异常的，不进这里 —— 那些字段没被动过，无需回滚。
    val rollback = mutableListOf<Pair<String, () -> Throwable?>>()

    // 字段找不到（`Reflect.field` 返回 null）与写入抛异常都要落到 missing 里：
    // `Reflect.field(...)?.setInt(...)` 的话，字段名变了就静默不写，而调用方照样
    // 打一行「已置 mScenario=1 …」，把失败伪装成成功。
    fun put(
        name: String,
        value: Int,
    ) {
        val f = Reflect.field(cls, name)
        if (f == null) {
            missing += "$name(找不到字段)"
            return
        }
        val original = runCatching { f.get(ext) }.getOrNull()
        runCatching { f.setInt(ext, value) }
            .onSuccess { rollback += name to { runCatching { f.set(ext, original) }.exceptionOrNull() } }
            .onFailure { missing += "$name(${it.javaClass.simpleName})" }
    }

    fun put(
        name: String,
        value: Boolean,
    ) {
        val f = Reflect.field(cls, name)
        if (f == null) {
            missing += "$name(找不到字段)"
            return
        }
        val original = runCatching { f.get(ext) }.getOrNull()
        runCatching { f.setBoolean(ext, value) }
            .onSuccess { rollback += name to { runCatching { f.set(ext, original) }.exceptionOrNull() } }
            .onFailure { missing += "$name(${it.javaClass.simpleName})" }
    }
    put(ROM.taskExtScenarioField, SCENARIO_FULL_SCREEN)
    put(ROM.taskExtCaptionField, true)
    put(ROM.taskExtZoomStateField, ZOOM_STATE_FULL_SCREEN)
    return FakeResult(ext, missing, rollback)
}

/**
 * 把 [fakeFlexibleFullScreen] 改过的字段写回原值，返回日志里那句结论。
 *
 * `switchState` 失败时必须调：那三个字段是**假值**，任务其实不是柔性任务；留着它会长期对外
 * 声称自己是「带 operation bar 的柔性任务」。写回失败的字段名列进返回值，不静默。
 */
internal fun FlexibleWindowBridge.rollbackFake(fake: FakeResult): String {
    val failed = mutableListOf<String>()
    for ((name, undo) in fake.rollback) {
        undo()?.let { failed += "$name(${it.javaClass.simpleName})" }
    }
    return when {
        fake.rollback.isEmpty() -> "三个假字段一个都没写进去，无需回滚"
        failed.isEmpty() -> "已把 ${fake.rollback.size} 个假字段写回原值"
        else -> "假字段回滚不完全，没写回 $failed —— 任务会留着假状态"
    }
}

/**
 * [fakeFlexibleFullScreen] 的结果。
 *
 * @param ext TaskExt；null 表示任务本身读不出来（连 `getExtImpl()` 都没有）。
 * @param missing 没能写进去的字段名；`ext != null` **不等于**冒充成功，见调用点的日志。
 * @param rollback 已经写进去的字段的回滚动作，`switchState` 失败时按它写回原值。
 */
internal class FakeResult(
    val ext: Any?,
    val missing: List<String>,
    val rollback: List<Pair<String, () -> Throwable?>> = emptyList(),
)
