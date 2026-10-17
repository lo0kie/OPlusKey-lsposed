package com.lookie.opluskey.bridge

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.MiniWindowGeometry
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.reflect.Reflect

// 原生动画器直调（`CMD_MINI_ANIM`）：建动画参数、算起止矩形、调框架那两支动画入口。
// 同样是扩展 —— 它只服务小窗那一下动画，与指令通道无关。

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
internal fun FlexibleWindowBridge.miniAnimLocal(
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
        anyTask(root, taskId, CMD_MINI_ANIM) ?: run {
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
            // 这一份 Transaction 还没交出去（下面那条入口会把它交给框架，由框架自己 apply/close）
            //  —— 这条出口上得自己收掉，否则每失败一次漏一个 native 对象。
            runCatching { transaction.close() }
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
 * 用 `FlexibleAnimationInfo$Builder` 造一个动画参数对象。
 *
 * 反射取 Builder 的实例：`Reflect` 里没有 newInstance，直接走
 * `getDeclaredConstructor()`（Builder 的构造器是公开的，但仍显式 setAccessible —— hidden API）。
 */
internal fun FlexibleWindowBridge.buildAnimInfo(
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

/**
 * 宿主下发的某个 rect（`isStart` 决定读 start_* 还是 finish_*）。
 *
 * 注意：**缺项不能逐项默认 0**：`mini` 那一档的 `readBounds()` 是
 * **整体兜底**（四边缺任一项就整份退回默认），而这里若对四边各自 `getIntExtra(..., 0)`
 * —— 宿主没带参数（旧版宿主 / 手工发广播）时会喂给动画一个「半边为 0」甚至全 0 的矩形：
 * 起止几何对不上，画面乱跳或干脆不动，日志里还看不出来。两档口径统一成
 * **四边齐才认，缺任一项就整份退回兜底几何**。
 */
internal fun FlexibleWindowBridge.rectOf(
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
internal const val EDGE_MISSING = Int.MIN_VALUE

/** [rectOf] 兜底用的全屏矩形（起点）。 */
internal fun FlexibleWindowBridge.fullScreenRect(): android.graphics.Rect {
    val dm =
        android.content.res.Resources
            .getSystem()
            .displayMetrics
    return android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
}
