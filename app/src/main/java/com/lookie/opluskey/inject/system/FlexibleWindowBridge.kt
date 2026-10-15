package com.lookie.opluskey.inject.system

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 小窗指令的跨进程桥：把前台任务收成小窗这件事，除了应用侧那条路，还有一条要在 `system_server` 里做 ——
 * 只有那边拿得到 `FlexibleTaskController`，用它自己的 `switchState(task, 1, …)` 才是带原生动画的那条。
 *
 * 与 [StatusBarBridge] 同一套：宿主发一条限定包名的广播，注入侧在 AMS 广播入口接住、在本机执行。
 */
internal object FlexibleWindowBridge {
    /** 小窗指令的转交广播 —— 宿主发，注入侧接住并在本机执行。 */
    const val ACTION = "com.lookie.opluskey.FLEXIBLE"

    /** 指令：把指定任务切到小窗态。 */
    const val CMD_MINI = "mini"

    /**
     * 指令：**用框架自己的动画器**把任务从「全屏外观」缩到「小窗外观」。
     *
     * 与 [CMD_MINI] 的分工：`mini` 是**真的转换**（改窗口模式，一步到位、无动画）；
     * 本指令只动**画面**（对 surface 做 `setPosition/setMatrix/setAlpha`），不动窗口模式。
     * 所以两个要配合用 —— 顺序由宿主侧决定（见 `MiniWindowLauncher.MINI_ANIM_MODE`）：
     *
     *  · `ANIM_THEN_CONVERT`（A）：先动画、后 `mini`。动画里是**真·全屏画面**在缩，
     *    但收尾转换时窗口要重排到小窗比例，会跳一下；
     *  · `CONVERT_THEN_ANIM`（B）：先 `mini`、后动画。**落点精确**，
     *    但动画第一帧就是「小窗布局被等比放大」，和你按下去前看到的画面不同。
     *
     * 两条都做了 —— 因为起止状态全是参数，切档不用重启（详见 `docs/zoom-window.md` §十二）。
     */
    const val CMD_MINI_ANIM = "mini_anim"

    /** 上面那条广播里带指令名 / 任务号的 extra。 */
    const val EXTRA_CMD = "cmd"
    const val EXTRA_TASK_ID = "task_id"

    /**
     * 落点几何由**宿主侧下发**，不写死在注入侧。
     *
     * 理由是「改哪边就得重启谁」：注入侧跑在 `system_server` 里，LSPosed 只在**进程启动时**
     * 注入 dex，所以注入侧的代码改一行就要**整机重启**才加载；而宿主侧的改动只要 kill 掉
     * `com.oplus.gesture`，几秒钟就生效。落点几何是要反复试的参数，放宿主侧 = 一轮试错
     * 从「重启一次（几分钟）」降到「几秒钟」。
     *
     * 拆成 4 个 int + 1 个 float，而不是传一个 `Rect`：跨进程传 Parcelable 要管类加载器，
     * 广播这条路上多一事不如少一事。
     */
    const val EXTRA_LEFT = "left"
    const val EXTRA_TOP = "top"
    const val EXTRA_RIGHT = "right"
    const val EXTRA_BOTTOM = "bottom"
    const val EXTRA_SCALE = "scale"

    /**
     * [CMD_MINI_ANIM] 的起止状态，全部由宿主下发 —— 和几何同理，**改参数不用重启**。
     *
     * 只有 `left/top` 参与动画（框架 `updateAnimationLayer` 只读 rect 的 left/top，
     * 尺寸走 scale），但这里仍然四边都带：`FlexibleAnimationInfo` 的 rect 在别处也会被读到，
     * 缺一项就是给框架喂半个 Rect。alpha 用来试「收尾淡出盖住跳变」。
     */
    const val EXTRA_START_LEFT = "start_left"
    const val EXTRA_START_TOP = "start_top"
    const val EXTRA_START_RIGHT = "start_right"
    const val EXTRA_START_BOTTOM = "start_bottom"
    const val EXTRA_START_SCALE = "start_scale"
    const val EXTRA_START_ALPHA = "start_alpha"
    const val EXTRA_FINISH_LEFT = "finish_left"
    const val EXTRA_FINISH_TOP = "finish_top"
    const val EXTRA_FINISH_RIGHT = "finish_right"
    const val EXTRA_FINISH_BOTTOM = "finish_bottom"
    const val EXTRA_FINISH_SCALE = "finish_scale"
    const val EXTRA_FINISH_ALPHA = "finish_alpha"

    /** 动画时长（毫秒）。 */
    const val EXTRA_DURATION = "duration"

    /**
     * 宿主进程调：把一条小窗指令转给注入侧执行。
     *
     * 必须 `setPackage(宿主包名)`：指定包名的广播不算隐式广播，不会被「隐式广播限制」拦掉，
     * 也只有宿主进程里注册的接收器能收到。任何失败只记日志 —— 最坏是「这一下没反应」。
     *
     * @param bounds 应用配置矩形（**不是**屏幕矩形，语义见 [EXPANDED_BOUNDS]）；传 null 用注入侧兜底值
     * @param scale  显示缩放；传 null 用注入侧兜底值
     */
    fun send(
        context: Context,
        cmd: String,
        taskId: Int,
        bounds: android.graphics.Rect? = null,
        scale: Float? = null,
    ) {
        try {
            val intent =
                Intent(ACTION)
                    .setPackage(KeyModule.PAGE_PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(EXTRA_CMD, cmd)
                    .putExtra(EXTRA_TASK_ID, taskId)
            if (bounds != null) {
                intent.putExtra(EXTRA_LEFT, bounds.left)
                intent.putExtra(EXTRA_TOP, bounds.top)
                intent.putExtra(EXTRA_RIGHT, bounds.right)
                intent.putExtra(EXTRA_BOTTOM, bounds.bottom)
            }
            if (scale != null) intent.putExtra(EXTRA_SCALE, scale)
            context.sendBroadcast(intent)
            XLog.w("flexible:$cmd(#$taskId) 已转给 system_server 执行（落点 $bounds scale=$scale）")
        } catch (e: Throwable) {
            XLog.w("发 flexible 广播失败：$cmd", e)
        }
    }

    /**
     * [CMD_MINI_ANIM] 的起止状态 —— 宿主侧构造。**这些就是要反复试的参数**，
     * 所以它们走广播下发（与几何同理），改它们不用重启注入侧。
     */
    class MiniAnimParams(
        val startRect: android.graphics.Rect,
        val finishRect: android.graphics.Rect,
        val startScale: Float,
        val finishScale: Float,
        val startAlpha: Float,
        val finishAlpha: Float,
        val durationMs: Long,
    )

    /** 宿主进程调：让注入侧用框架自己的动画器把任务从「全屏外观」缩到「小窗外观」。 */
    fun sendMiniAnim(
        context: Context,
        taskId: Int,
        p: MiniAnimParams,
    ) {
        try {
            val intent =
                Intent(ACTION)
                    .setPackage(KeyModule.PAGE_PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(EXTRA_CMD, CMD_MINI_ANIM)
                    .putExtra(EXTRA_TASK_ID, taskId)
                    .putExtra(EXTRA_START_LEFT, p.startRect.left)
                    .putExtra(EXTRA_START_TOP, p.startRect.top)
                    .putExtra(EXTRA_START_RIGHT, p.startRect.right)
                    .putExtra(EXTRA_START_BOTTOM, p.startRect.bottom)
                    .putExtra(EXTRA_FINISH_LEFT, p.finishRect.left)
                    .putExtra(EXTRA_FINISH_TOP, p.finishRect.top)
                    .putExtra(EXTRA_FINISH_RIGHT, p.finishRect.right)
                    .putExtra(EXTRA_FINISH_BOTTOM, p.finishRect.bottom)
                    .putExtra(EXTRA_START_SCALE, p.startScale)
                    .putExtra(EXTRA_FINISH_SCALE, p.finishScale)
                    .putExtra(EXTRA_START_ALPHA, p.startAlpha)
                    .putExtra(EXTRA_FINISH_ALPHA, p.finishAlpha)
                    .putExtra(EXTRA_DURATION, p.durationMs)
            context.sendBroadcast(intent)
            XLog.w(
                "flexible:$CMD_MINI_ANIM(#$taskId) 已转给 system_server" +
                    "（start=${p.startRect}@${p.startScale}a${p.startAlpha}" +
                    " → finish=${p.finishRect}@${p.finishScale}a${p.finishAlpha}）",
            )
        } catch (e: Throwable) {
            XLog.w("发 flexible 广播失败：$CMD_MINI_ANIM", e)
        }
    }

    /**
     * 调用点在 AMS 广播入口的 hooker 里（热路径 + system_server），整体再包一层 catch：
     * 漏一个异常出去就是系统崩。
     *
     * @param loader system_server 的 ClassLoader（本机反射用；null 则只记日志不执行）
     */
    fun handle(
        intent: Intent,
        loader: ClassLoader?,
    ): Boolean {
        if (intent.action != ACTION) return false
        val cmd =
            try {
                intent.getStringExtra(EXTRA_CMD).orEmpty()
            } catch (e: Throwable) {
                XLog.w("读小窗指令 extra 失败：${e.message}")
                ""
            }
        val taskId =
            try {
                intent.getIntExtra(EXTRA_TASK_ID, -1)
            } catch (e: Throwable) {
                -1
            }
        // 落点几何随指令一起来（宿主侧下发）。读不到就走注入侧的兜底默认值 ——
        // 这样「宿主是新版、注入侧是旧版」时也不会退化成没有几何。
        val bounds = readBounds(intent)
        val scale =
            try {
                if (intent.hasExtra(EXTRA_SCALE)) intent.getFloatExtra(EXTRA_SCALE, EXPANDED_SCALE) else null
            } catch (e: Throwable) {
                null
            }
        try {
            when (cmd) {
                CMD_MINI -> switchToMiniLocal(loader, taskId, bounds, scale)
                CMD_MINI_ANIM -> miniAnimLocal(loader, taskId, intent)
                else -> XLog.w("不认识的小窗指令：$cmd")
            }
        } catch (e: Throwable) {
            XLog.w("执行小窗指令失败（忽略）：$cmd", e)
        }
        return true
    }

    /**
     * 读宿主下发的**应用配置矩形**；四项缺任何一项就整体当「没下发」，返回 null 让调用方走兜底。
     *
     * 四项是**一起有或一起没有**的（[send] 要么全带上要么全不带），所以是整体兜底、不是逐项兜底 ——
     * 下面 `getIntExtra` 的默认值取不到（前面已判过 `hasExtra`）。
     *
     * 和 [handle] 同理整体包 catch：调用点在 AMS 广播入口，漏一个异常出去就是系统崩。
     */
    private fun readBounds(intent: Intent): android.graphics.Rect? =
        try {
            if (intent.hasExtra(EXTRA_LEFT) && intent.hasExtra(EXTRA_TOP) &&
                intent.hasExtra(EXTRA_RIGHT) && intent.hasExtra(EXTRA_BOTTOM)
            ) {
                android.graphics.Rect(
                    intent.getIntExtra(EXTRA_LEFT, 0),
                    intent.getIntExtra(EXTRA_TOP, 0),
                    intent.getIntExtra(EXTRA_RIGHT, 0),
                    intent.getIntExtra(EXTRA_BOTTOM, 0),
                )
            } else {
                null
            }
        } catch (e: Throwable) {
            XLog.w("读宿主下发的落点几何失败：${e.message}")
            null
        }

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
    private fun switchToMiniLocal(
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
            Reflect.call(service, "getFlexibleTaskController") ?: run {
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
            findTask(root, taskId) ?: run {
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
        //    两个坑的取值与推算过程都写在 [EXPANDED_BOUNDS] 的注释里，改之前先读那段。
        //    `setRememberPosInfo(Rect, float)` 含原始类型参数，必须显式拿 Method（见 [findTask] 的装箱坑）。
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
                Reflect.call(ext, setPos, bounds, scale)
                // 这条日志要能独立验收：配置矩形 → 乘 scale → 屏幕落点，一眼可核对。
                // 真机上它打的屏幕落点应当是 Rect(126, 319 - 1145, 2131)（宽 1019、高 1812）。
                XLog.w(
                    "小窗指令：已预置展开档几何（$from）配置矩形=$bounds scale=$scale" +
                        " → 屏幕落点应为 ${screenBoundsOf(bounds, scale)}",
                )
            } else {
                XLog.w("小窗指令：找不到 setRememberPosInfo(Rect,float)，落点会走默认比例（会偏长）")
            }
        }

        // 喂给 `TaskExtImpl#updateFlexibleTaskParams` 的能力配置 —— **操作条（caption）就是它开的**
        // （`androidx.activity.HasCaption`），缩放范围 / 圆角 / 忽略系统栏也在这里。键值抄自真机
        // 上一次真实小窗启动的 `mOplusExtraBundle`（见 docs/device-findings.md §17.6.4 附六）。
        // (2) 操作条的实现类名来自策略配置（`getCaptionName(scenario)`），不喂的话
        //    `mCaptionName = null` → `Class.forName(null)` 失败 → 视图建不出来。
        val getCaption = Reflect.method(serviceCls, "getCaptionName", java.lang.Integer.TYPE)
        val captionName = getCaption?.let { Reflect.call(service, it, 1) } as? String
        XLog.w("小窗指令：caption 实现类 = $captionName")
        val bundle =
            android.os.Bundle().apply {
                putBoolean("androidx.activity.HasCaption", true)
                if (captionName != null) putString("androidx.activity.CaptionName", captionName)
                putInt("androidx.activity.LaunchInsensible", 0)
                putInt("androidx.activity.task.attach.blur", 0)
                putInt("androidx.activity.LaunchCornerRadius", 91)
                putInt("androidx.activity.LaunchShadowRadius", 200)
                putInt("androidx.activity.LaunchScenario", 1)
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
                    "updateFlexibleTaskParams",
                    android.os.Bundle::class.java,
                    optionsCls,
                    record.javaClass,
                )
            if (upd != null) {
                Reflect.call(ext, upd, bundle, options, record)
                XLog.w("小窗指令：已喂 updateFlexibleTaskParams（含 HasCaption=true）")
            } else {
                XLog.w("小窗指令：找不到 updateFlexibleTaskParams(Bundle,ActivityOptions,ActivityRecord)")
            }
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
            XLog.w("小窗指令：冒充柔性全屏态成功（mScenario=1 / mHasCaption=true / mFlexibleZoomState=2）")
        } else {
            XLog.w("小窗指令：冒充柔性全屏态**只写进去一部分**，缺 ${fake.missing} —— switchState 大概率不生效")
        }
        if (!callSwitchState(controller, task, taskId)) {
            XLog.w("小窗指令：switchState(#$taskId, 1) 没调成功 —— 小窗不会出现（上面一行写了断在哪）")
            return
        }
        XLog.w("小窗指令：switchState(#$taskId, 1) 已调用（应播「全屏 → 小窗」原生动画）")
    }

    // ── 原生动画器直调（[CMD_MINI_ANIM]） ──────────────────────────────────────

    /**
     * 调框架自己的 `FlexibleWindowAnimator#startFlexibleTaskToMiniAnimations`。
     *
     * **为什么这不算「自己画动画」**：插值、矩阵、`apply()`、以及那条 spring
     * （`dampingRatio=0.86 / stiffness=250`）**全是框架跑的**；我们只交起止参数。
     * 每帧到底做什么见 `docs/zoom-window.md` §十二（`updateAnimationLayer` 的 smali）。
     *
     * 三个从 smali 里读出来、不读就会白踩一轮重启的细节：
     *
     * 1. **`info.type` 必须是 `205`。** 动画器里那句
     *    `if (!endAnimation(205)) cancelSpringToMiniAnimation(handler);` 的 205 就是它 ——
     *    `endAnimation` 只在 `info.getType() == 205` 时才走「结束上一段动画」那条分支。
     * 2. **那句 cancel 掐不掉刚起的动画**（已核）：`createSpringAnimation` **只创建、不 `start()`**，
     *    所以此刻 `mSpringToMiniAnimationSet.isAnimRunning()` 恒为 false，
     *    `cancelSpringToMiniAnimation` 第一句就早返回。真正的启动在它后面
     *    （`lambda15/16` → `mSpringToMiniAnimationSet.start()`，前置是
     *    `controller.hasTaskPosChangeAnimRunning() == false`）。
     * 3. **`reason` 用框架自己那个 `"maySwitchStateToMini"`**：动画器拿它在一开始做
     *    `isInTransition(task, reason.equals("maySwitchStateToMini"))` 的过滤，传别的字符串会换一档判定，
     *    而**被过滤掉就是静默什么都不发生**（日志里只有一句 "no animation or leash."）。
     *
     * `handler` 传 `null`：动画器内部对 null 会回退到 `ATMS.mUiHandler`，比我们自己造一个 looper 稳。
     */
    private fun miniAnimLocal(
        loader: ClassLoader?,
        taskId: Int,
        intent: Intent,
    ) {
        if (loader == null) {
            XLog.w("$CMD_MINI_ANIM：还没有 system_server 的 ClassLoader，跳过")
            return
        }
        if (taskId <= 0) {
            XLog.w("$CMD_MINI_ANIM：没带 taskId，跳过")
            return
        }
        val serviceCls =
            Reflect.findClass(loader, SERVICE) ?: run {
                XLog.w("$CMD_MINI_ANIM：找不到 $SERVICE，跳过")
                return
            }
        val service =
            Reflect.callStatic(serviceCls, "getInstance", null) ?: run {
                XLog.w("$CMD_MINI_ANIM：$SERVICE.getInstance() 拿不到，跳过")
                return
            }
        val controller =
            Reflect.call(service, "getFlexibleTaskController") ?: run {
                XLog.w("$CMD_MINI_ANIM：拿不到 FlexibleTaskController，跳过")
                return
            }
        // 动画器就在 controller 上（`mAnimator` 字段，且有 `getAnimator()`）—— 不用自己去 new。
        val animator =
            Reflect.call(controller, "getAnimator") ?: run {
                XLog.w("$CMD_MINI_ANIM：FlexibleTaskController.getAnimator() 拿不到，跳过")
                return
            }
        val atms =
            Reflect.fieldValue(controller, controller.javaClass, FIELD_ATMS) ?: run {
                XLog.w("$CMD_MINI_ANIM：读不到 $FIELD_ATMS，跳过")
                return
            }
        val root =
            Reflect.fieldValue(atms, atms.javaClass, FIELD_ROOT) ?: run {
                XLog.w("$CMD_MINI_ANIM：读不到 $FIELD_ROOT，跳过")
                return
            }
        val task =
            findTask(root, taskId) ?: run {
                XLog.w("$CMD_MINI_ANIM：任务 #$taskId 找不到，跳过")
                return
            }

        val info = buildAnimInfo(loader, intent) ?: return
        XLog.w(
            "$CMD_MINI_ANIM：起止参数 " +
                "start=${rectOf(intent, true)}@${
                    intent.getFloatExtra(
                        EXTRA_START_SCALE,
                        1f,
                    )
                }a${intent.getFloatExtra(EXTRA_START_ALPHA, 1f)} " +
                "finish=${rectOf(intent, false)}@${
                    intent.getFloatExtra(
                        EXTRA_FINISH_SCALE,
                        1f,
                    )
                }a${intent.getFloatExtra(EXTRA_FINISH_ALPHA, 1f)} " +
                "duration=${intent.getLongExtra(EXTRA_DURATION, 0L)}",
        )

        val cbCls =
            Reflect.findClass(loader, CALLBACK_CLS) ?: run {
                XLog.w("$CMD_MINI_ANIM：找不到 $CALLBACK_CLS，跳过")
                return
            }
        // 回调只需要「能被框架调用」；它内部只做通知，我们不需要在结束点插动作
        // （转换的时机由宿主侧排，见 MiniWindowLauncher.MINI_ANIM_MODE）。
        val callback =
            try {
                java.lang.reflect.Proxy.newProxyInstance(
                    loader,
                    arrayOf(cbCls),
                    java.lang.reflect.InvocationHandler { _, _, _ -> null },
                )
            } catch (e: Throwable) {
                XLog.w("$CMD_MINI_ANIM：给回调建 Proxy 失败：${e.javaClass.simpleName}: ${e.message}")
                return
            }

        val map = android.util.ArrayMap<Any, Any>()
        map.put(task, info)
        val transaction = android.view.SurfaceControl.Transaction()

        // 两条入口，**优先 spring 那条**。
        //
        // 为什么不是直接用 `startFlexibleTaskToMiniAnimations`（真机实测踩到）：它开头有一道
        // `FlexibleWindowUtils.isInTransition(task, …)` 过滤，而转换刚做完时任务**正好还在过渡里**
        // ⇒ 被静默剔掉，日志只有一句 `current task is in transition:Task{#N}`，画面纹丝不动。
        // 等过渡收完再调又会让用户先看到一个静止的小窗（多一次跳变）。
        // 而 `startFlexibleSpringAnimation` **没有这道过滤**（离线 smali 已核：它只做 null 检查
        // → 建 `FlexibleSpringAnimationSet` → 取 leash → 开跑），所以转换后可以立刻启动。
        val spring = Reflect.methodByNameAndArity(animator.javaClass, METHOD_SPRING_ANIM, 7)
        val bounds = if (spring == null) null else Reflect.call(task, "getBounds") as? android.graphics.Rect
        if (spring == null) {
            XLog.w("$CMD_MINI_ANIM：没有 $METHOD_SPRING_ANIM（7 参），改用 $METHOD_MINI_ANIM")
        } else if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            // 注意：**不拿空 Rect 喂动画**：`getBounds` 拿不到时静默塞一个
            //  `Rect()`（全 0）进去，动画把「任务的当前边界」当成一个空矩形 —— 起止几何对不上，
            //  观感是画面乱跳或干脆不动，而日志里看不出任何异常。改走下面那条入口（它不需要 bounds）。
            XLog.w(
                "$CMD_MINI_ANIM：任务 #$taskId 的 getBounds 拿不到或为空（$bounds）" +
                    " —— 不拿空 Rect 喂动画，改走另一条入口",
            )
        } else {
            spring.isAccessible = true
            val ok =
                try {
                    spring.invoke(animator, info, bounds, task, null, transaction, callback, REASON_MINI_ANIM)
                    XLog.w("$CMD_MINI_ANIM：已调 $METHOD_SPRING_ANIM(#$taskId)（7 参、无过渡过滤）")
                    true
                } catch (e: Throwable) {
                    XLog.w(
                        "$CMD_MINI_ANIM：$METHOD_SPRING_ANIM 抛了（降级到另一条入口）：" +
                            "${Reflect.deepestCause(e).javaClass.name}: ${Reflect.deepestCause(e).message}",
                    )
                    false
                }
            // 抛了就往下走另一条 —— 这样「换入口」不必再改注入侧（注入侧改一次要重启）。
            if (ok) return
        }
        val m =
            Reflect.methodByNameAndArity(animator.javaClass, METHOD_MINI_ANIM, 5) ?: run {
                XLog.w("$CMD_MINI_ANIM：两条入口都找不到（$METHOD_SPRING_ANIM/7、$METHOD_MINI_ANIM/5）")
                return
            }
        m.isAccessible = true
        try {
            m.invoke(animator, map, null, transaction, callback, REASON_MINI_ANIM)
            XLog.w("$CMD_MINI_ANIM：已调 $METHOD_MINI_ANIM(#$taskId)（5 参，这条有过渡过滤）")
        } catch (e: Throwable) {
            XLog.w("$CMD_MINI_ANIM：$METHOD_MINI_ANIM 抛了：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * 宿主下发的某个 rect（`isStart` 决定读 start_* 还是 finish_*）。
     *
     * 注意：**缺项不能逐项默认 0**：`mini` 那一档的 `readBounds()` 是
     * **整体兜底**（四边缺任一项就整份退回默认），而这里若对四边各自 `getIntExtra(..., 0)`
     * —— 宿主没带参数（旧版宿主 / 手工发广播）时会喂给动画一个「半边为 0」甚至全 0 的矩形：
     * 起止几何对不上，画面乱跳或干脆不动，日志里还看不出来。两档口径统一成
     * **四边齐才认，缺任一项就整份退回兜底几何**。
     */
    private fun rectOf(
        intent: Intent,
        isStart: Boolean,
    ): android.graphics.Rect {
        val l = intent.getIntExtra(if (isStart) EXTRA_START_LEFT else EXTRA_FINISH_LEFT, EDGE_MISSING)
        val t = intent.getIntExtra(if (isStart) EXTRA_START_TOP else EXTRA_FINISH_TOP, EDGE_MISSING)
        val r = intent.getIntExtra(if (isStart) EXTRA_START_RIGHT else EXTRA_FINISH_RIGHT, EDGE_MISSING)
        val b =
            intent.getIntExtra(if (isStart) EXTRA_START_BOTTOM else EXTRA_FINISH_BOTTOM, EDGE_MISSING)
        if (l == EDGE_MISSING || t == EDGE_MISSING || r == EDGE_MISSING || b == EDGE_MISSING) {
            XLog.w(
                "$CMD_MINI_ANIM：${if (isStart) "start" else "finish"} 的四边没给齐" +
                    "（$l,$t,$r,$b）—— 不喂半个 Rect，整份退回兜底几何",
            )
            return if (isStart) fullScreenRect() else EXPANDED_BOUNDS
        }
        return android.graphics.Rect(l, t, r, b)
    }

    /** [rectOf] 的哨位 —— 与任何合法边值都不同（`Int.MIN_VALUE` 不可能是一个屏幕坐标）。 */
    private const val EDGE_MISSING = Int.MIN_VALUE

    /** [rectOf] 兜底用的全屏矩形（起点）。 */
    private fun fullScreenRect(): android.graphics.Rect {
        val dm =
            android.content.res.Resources
                .getSystem()
                .displayMetrics
        return android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    /**
     * 用 `FlexibleAnimationInfo$Builder` 造一个动画参数对象。
     *
     * 反射取 Builder 的实例：`Reflect` 里没有 newInstance，直接走
     * `getDeclaredConstructor()`（Builder 的构造器是公开的，但仍显式 setAccessible —— hidden API）。
     */
    private fun buildAnimInfo(
        loader: ClassLoader,
        intent: Intent,
    ): Any? {
        val cls =
            Reflect.findClass(loader, INFO_BUILDER_CLS) ?: run {
                XLog.w("$CMD_MINI_ANIM：找不到 $INFO_BUILDER_CLS，跳过")
                return null
            }
        val builder =
            try {
                cls.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
            } catch (e: Throwable) {
                XLog.w("$CMD_MINI_ANIM：造 Builder 失败：${e.javaClass.simpleName}: ${e.message}")
                return null
            }
        val missing = mutableListOf<String>()

        // 每个 setter 都单独记账：少设一个就是给框架喂半个参数，而它不会报错、只会行为怪
        // （这正是 §7 里「三个 `?.set` 静默可选写」那一类降级纪律问题）。
        fun call(
            name: String,
            type: Class<*>,
            value: Any,
        ) {
            val m = Reflect.method(cls, name, type)
            if (m == null) {
                missing += "$name(找不到)"
                return
            }
            try {
                m.invoke(builder, value)
            } catch (e: Throwable) {
                missing += "$name(${e.javaClass.simpleName})"
            }
        }
        call("setStartRect", android.graphics.Rect::class.java, rectOf(intent, true))
        call("setFinishRect", android.graphics.Rect::class.java, rectOf(intent, false))
        call("setStartScale", java.lang.Float.TYPE, intent.getFloatExtra(EXTRA_START_SCALE, 1f))
        call("setFinishScale", java.lang.Float.TYPE, intent.getFloatExtra(EXTRA_FINISH_SCALE, EXPANDED_SCALE))
        call("setStartAlpha", java.lang.Float.TYPE, intent.getFloatExtra(EXTRA_START_ALPHA, 1f))
        call("setFinishAlpha", java.lang.Float.TYPE, intent.getFloatExtra(EXTRA_FINISH_ALPHA, 1f))
        call("setDuration", java.lang.Long.TYPE, intent.getLongExtra(EXTRA_DURATION, DEFAULT_ANIM_DURATION))
        // 205 = 那个 TRANSIT_IME 常量，必须设（见本函数的调用方 KDoc 第 1 条）
        call("setType", java.lang.Integer.TYPE, ANIM_TYPE_TRANSIT_IME)
        if (missing.isNotEmpty()) {
            XLog.w("$CMD_MINI_ANIM：Builder 有 ${missing.size} 个 setter 没设上：$missing —— 动画大概率不生效")
        }
        val info =
            try {
                val build =
                    Reflect.method(cls, "build") ?: run {
                        XLog.w("$CMD_MINI_ANIM：Builder 上没有 build()")
                        return null
                    }
                build.invoke(builder)
            } catch (e: Throwable) {
                XLog.w("$CMD_MINI_ANIM：build() 抛了：${e.javaClass.simpleName}: ${e.message}")
                return null
            }
        if (info == null) XLog.w("$CMD_MINI_ANIM：build() 返回 null")
        return info
    }

    // 剥到最内层 cause 的那份实现已收到 `Reflect.deepestCause`（与 `ProbeChannel` 共用一份）。

    /** 动画器 / 参数对象的类名与常量 —— 全部来自 `oplus-services.jar` 的离线反编译（§十二）。 */
    private const val METHOD_MINI_ANIM = "startFlexibleTaskToMiniAnimations"

    /**
     * 首选入口：`startFlexibleSpringAnimation(info, Rect, Task, Handler, Transaction, callback, String)`。
     *
     * 比 [METHOD_MINI_ANIM] 好在**没有 `isInTransition` 过滤**（真机实测：转换刚做完时任务正好还在
     * 过渡里，[METHOD_MINI_ANIM] 会把任务静默剔掉，日志只有 `current task is in transition`）——
     * 于是可以「转换完立刻播」，不会先露出一个静止的小窗。它自带的 spring 是 `0.87 / 261`
     * （[METHOD_MINI_ANIM] 是 `0.86 / 250`）。
     */
    private const val METHOD_SPRING_ANIM = "startFlexibleSpringAnimation"

    private const val CALLBACK_CLS = "com.android.server.wm.floatwindow.FlexibleAnimationCallback"
    private const val INFO_BUILDER_CLS =
        "com.android.server.wm.floatwindow.FlexibleAnimationInfo\$Builder"

    /** `FlexibleAnimationInfo.type` 要等于这个值，`endAnimation(205)` 才会走「结束上一段」分支。 */
    private const val ANIM_TYPE_TRANSIT_IME = 205

    /**
     * `reason` 用框架自己那个字符串 —— 动画器开头用它选
     * `isInTransition(task, reason.equals("maySwitchStateToMini"))` 这一档过滤。
     */
    private const val REASON_MINI_ANIM = "maySwitchStateToMini"

    /** 动画时长兜底（宿主会下发；spring 的实际收敛时长由阻尼/刚度决定，这个值主要用于日志与 A 档的排期）。 */
    private const val DEFAULT_ANIM_DURATION = 400L

    /**
     * `anyTaskForId` 的三种 matchMode 逐档试：0 只找已 attach 的、1 再加 recents、2 还能顺手从 recents
     * 恢复。**不能用按「参数运行时类」找方法的 [Reflect.call] 重载** —— Kotlin 会把 Int 装箱成
     * `Integer`，而真实签名是 `int` 原始类型，永远匹配不上、还静默返回 null（实测踩过）。
     */
    private fun findTask(
        root: Any,
        taskId: Int,
    ): Any? {
        val method =
            Reflect.method(root.javaClass, "anyTaskForId", java.lang.Integer.TYPE, java.lang.Integer.TYPE)
                ?: run {
                    XLog.w("找不到 anyTaskForId(int,int)，小窗指令跳过")
                    return null
                }
        for (mode in listOf(0, 1, 2)) {
            val t = Reflect.call(root, method, taskId, mode)
            if (t != null) {
                XLog.w("小窗指令：anyTaskForId(#$taskId, mode=$mode) 命中")
                return t
            }
        }
        return null
    }

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
    private fun callSwitchState(
        controller: Any,
        task: Any,
        taskId: Int,
    ): Boolean {
        val method =
            Reflect.method(
                controller.javaClass,
                "switchState",
                task.javaClass,
                java.lang.Integer.TYPE,
                String::class.java,
            ) ?: run {
                XLog.w("找不到 switchState(Task,int,String)，动画调不了")
                return false
            }
        return try {
            method.isAccessible = true
            method.invoke(controller, task, 1, REASON)
            true
        } catch (e: Throwable) {
            val cause =
                if (e is java.lang.reflect.InvocationTargetException) {
                    e.targetException ?: e
                } else {
                    e
                }
            XLog.w(
                "switchState(#$taskId, 1) 调用抛了 —— 小窗不会出现：" +
                    "${cause.javaClass.simpleName}: ${cause.message}",
            )
            false
        }
    }

    /** `task.getBounds()` —— 只读来打日志。 */
    private fun taskBounds(task: Any): Any? =
        try {
            Reflect.call(task, "getBounds")
        } catch (e: Throwable) {
            null
        }

    /** `task.getWrapper().getExtImpl()`。 */
    private fun extOf(task: Any): Any? =
        try {
            val wrapper = Reflect.call(task, "getWrapper") ?: return null
            Reflect.call(wrapper, "getExtImpl")
        } catch (e: Throwable) {
            null
        }

    /** `TaskExtImpl#getFlexibleZoomState()` —— 只读来打日志，读不到就 null。 */
    private fun flexibleZoomState(task: Any): Any? =
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
    private fun fakeFlexibleFullScreen(task: Any): FakeResult {
        val ext = extOf(task) ?: return FakeResult(null, listOf("TaskExt 读不出（getExtImpl）"))
        val cls = ext.javaClass
        val missing = mutableListOf<String>()

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
            runCatching { f.setInt(ext, value) }
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
            runCatching { f.setBoolean(ext, value) }
                .onFailure { missing += "$name(${it.javaClass.simpleName})" }
        }
        put("mScenario", 1)
        put("mHasCaption", true)
        put("mFlexibleZoomState", 2)
        return FakeResult(ext, missing)
    }

    /** [fakeFlexibleFullScreen] 的结果：ext 非 null 但 [missing] 非空 = 只写进去一部分。 */
    private class FakeResult(
        val ext: Any?,
        val missing: List<String>,
    )

    /** 按名字反射，不直接引用（注入侧不许碰宿主框架的私有类型）。 */
    private const val SERVICE = "com.android.server.wm.FlexibleWindowManagerService"
    private const val FIELD_ATMS = "mAtms"
    private const val FIELD_ROOT = "mRootWindowContainer"

    /** `switchState` 的 reason 参数，会进服务端日志，用来分辨是不是本模块调的。 */
    private const val REASON = "OplusKey"

    /**
     * 展开档小窗的**兜底默认**几何与缩放 —— PLK110（1272×2772 @560dpi）的真机实测值。
     *
     * 正常路径下这两个值由**宿主侧下发**（见 [EXTRA_LEFT] 一段的说明），这里只是「宿主没带」
     * 时的兜底，避免退化成没有几何。要调几何请改宿主侧的
     * `MiniWindowLauncher.EXPANDED_CONFIG_BOUNDS` / `EXPANDED_SCALE` —— 那边不用重启。
     *
     * 注意：[EXPANDED_BOUNDS] 是**应用配置矩形**，不是屏幕矩形：系统会把它的尺寸
     * **再乘一次 [EXPANDED_SCALE]** 才是屏幕上的实际大小，即
     *
     * ```
     * 屏幕尺寸 = Rect.size × scale
     * 1272 × 2261  ×  0.8017  =  1020 × 1812   与真机一致
     * ```
     *
     * 踩过的坑：这里曾经直接填**屏幕矩形** `Rect(126,319-1146,2131)`（1020×1812），
     * 结果被系统又缩了一次 —— `1020 × 0.8017 = 817`，窗口整整齐齐小了 0.8017 倍（真机实测
     * 817×1452，宽占比 0.6423 而不是 0.8019），表现为「小窗是对的，但比 ColorOS 原生的小一圈」。
     * 正确值是拿真机「原生小窗」的 `dumpsys activity activities` → `Task.mBounds` 反查出来的
     * （= `Rect(126,319 - 1398,2580)`），见 `docs/device-findings.md` 附七。
     *
     * 落点（left/top）则是**直接照搬到屏幕坐标**的，不含缩放 —— 所以 (126,319) 两头都是这个数。
     *
     * **不要**拿 `getMiniFlexibleTaskInfo` 的值代替 —— 那是收起档的几何
     * （约 `Rect(898,168-1203,710)` / `0.2397`），会把窗口缩成右上角一条。
     * 这几个数与适配清单绑定：加机型要连同它们重测（见 `docs/zoom-window.md` §五）。
     */
    private val EXPANDED_BOUNDS = android.graphics.Rect(126, 319, 1398, 2580) // 1272 × 2261
    private const val EXPANDED_SCALE = 0.8017f

    /**
     * 配置矩形 × scale = 屏幕落点，只为打印验收日志用（`left/top` 不参与缩放）。
     *
     * 不缓存成常量：几何现在是宿主下发的，写死一个「预期屏幕矩形」反而会跟实际参数脱节。
     */
    private fun screenBoundsOf(
        bounds: android.graphics.Rect,
        scale: Float,
    ): android.graphics.Rect =
        android.graphics.Rect(
            bounds.left,
            bounds.top,
            bounds.left + (bounds.width() * scale).toInt(),
            bounds.top + (bounds.height() * scale).toInt(),
        )
}
