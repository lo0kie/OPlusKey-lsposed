package com.lookie.opluskey.inject

import android.content.Intent
import android.os.Handler
import com.lookie.opluskey.bridge.extOf
import com.lookie.opluskey.bridge.instanceOf
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.reflect.Reflect

// 探针的 `call` 那一族（含它用的反射引擎：定位 / 强制转换 / 收包 / 取字段）——
// 从 ProbeChannel 分出来；object 不能用两个文件装，走扩展函数。
// 这些函数只在 `DEBUG_PROBE` 为真时可达（release 里整段被编译期折掉）。

// ── op: call ─────────────────────────────────────────────────────────────

/**
 * [OP_CALL] 的准入闸：`clsName` 命中 [UNSAFE_CLASS_PREFIXES] **且**广播带了 [EXTRA_UNSAFE]=true
 * 才返回 true；否则打一条 W 判据行并拒绝。
 *
 * 判据行必须 W：注入侧的 `i` / `d` 在日志页里整档看不到（AGENTS.md 二、1.2），而这一行正是
 * 「这次为什么没执行」的唯一答案 —— 不许静默 return，也不许只说一句「拒绝」了事，必须写清
 * 哪条 op、哪个类、卡在哪一条。
 */
internal fun ProbeChannel.allowUnsafe(
    clsName: String,
    target: String,
    intent: Intent,
): Boolean {
    val shown = clsName.ifEmpty { "（空类名）" }
    val optedIn =
        try {
            intent.getBooleanExtra(EXTRA_UNSAFE, false)
        } catch (e: Throwable) {
            false
        }
    if (!optedIn) {
        XLog.w(
            "${describe(OP_CALL)}（target=$target）：拒绝执行 —— $shown 要显式 opt-in，" +
                "广播带 extra $EXTRA_UNSAFE=true 才放行",
        )
        return false
    }
    if (UNSAFE_CLASS_PREFIXES.none { clsName.startsWith(it) }) {
        XLog.w(
            "${describe(OP_CALL)}（target=$target）：拒绝执行 —— $shown 不在白名单里，" +
                "只允许 ${UNSAFE_CLASS_PREFIXES.joinToString(" / ")}",
        )
        return false
    }
    return true
}

/**
 * 解析目标类的接收者，并在**正确的时机**过 [allowUnsafe] 准入闸；过不了闸 / 解析不出来都返回
 * `null`（原因由里面的日志给出）。
 *
 *  顺序就是这里的关键，不能合成一次判：`target=singleton` 的接收者本身取自广播里的 `cls`，而
 *  `getInstance()` 那一步**就会触发宿主类 `<clinit>`** —— 所以它必须**先按广播里的类名**过闸，
 *  再去取接收者；其余 target 要等类定下来才知道该拿哪个名字判，只能放在解析之后。也正因为两个
 *  判点分处一前一后，才把它整个收进这一个函数（`call` 里那两个 return 本来会顶到 detekt 的
 *  出口数上限）。
 */
internal fun ProbeChannel.resolveGatedTarget(
    loader: ClassLoader,
    target: String,
    clsName: String,
    intent: Intent,
): Pair<Any?, Class<*>>? {
    // **先验票再开门**：`resolveReceiver` 里那几步（`findClass` + `getInstance`）**本身**就会触发
    //  宿主类的 `<clinit>`（见 [allowUnsafe]），先解析再判等于先开门后验票 —— 闸就白设了。
    //  闸认的类名按 target 分两种：`singleton` 的接收者取自广播里的 `cls`，要碰的就是它；
    //  service / task / ext / caption 四个中间 target 要碰的是 [SERVICE_CLASS]（编译期常量，
    //  不必等解析）；`static` 与不认识的 target 解析期不碰宿主类，仍按解析出来的类名判（下面那一道）。
    val earlyGate =
        when (target) {
            TARGET_SINGLETON -> clsName
            TARGET_SERVICE, TARGET_TASK, TARGET_EXT, TARGET_CAPTION -> SERVICE_CLASS
            else -> null
        }
    if (earlyGate != null && !allowUnsafe(earlyGate, target, intent)) return null

    val receiver = resolveReceiver(loader, target, intent)
    val cls =
        when {
            receiver != null -> receiver.javaClass
            clsName.isNotEmpty() -> Reflect.findClass(loader, clsName)
            else -> null
        }
    if (cls == null) {
        XLog.w("probe/call：既拿不到 target=$target 的接收者，也没给 cls")
        return null
    }
    // `singleton` 已在上面按 `clsName` 判过，这里不重判 —— 单例的实现类名与 `cls` 未必同名，
    //  重判会把它误拒。
    if (target != TARGET_SINGLETON && !allowUnsafe(cls.name, target, intent)) return null
    return receiver to cls
}

/**
 * 按名字找方法（同名多载时按参数个数 + 能不能把 `args` 转过去筛），调完把返回值/异常打出来。
 *
 * `args` 是字符串数组（`--esa args a,b,c`），按方法的形参类型就地转换：
 * int/long/float/boolean/String 直接转，其余类型传 null 并把形参名字打出来提示。
 */
internal fun ProbeChannel.call(
    loader: ClassLoader,
    intent: Intent,
) {
    val clsName = intent.getStringExtra(EXTRA_CLS).orEmpty()
    val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
    val target = intent.getStringExtra(EXTRA_TARGET) ?: TARGET_SERVICE
    val rawArgs = intent.getStringArrayListExtra(EXTRA_ARGS).orEmpty()
    if (name.isEmpty()) {
        XLog.w("probe/call：要带 name")
        return
    }

    // 过闸与解析合成一步（见 [resolveGatedTarget] 里那条顺序的理由），这里只留一条出口
    val gated = resolveGatedTarget(loader, target, clsName, intent) ?: return
    val (receiver, cls) = gated

    val candidates = cls.methods.filter { it.name == name }
    if (candidates.isEmpty()) {
        XLog.w("probe/call：$cls 上没有 $name")
        return
    }
    for (m in candidates.sortedBy { it.parameterCount }) {
        val converted = coerce(rawArgs, m.parameterTypes) ?: continue
        val sig = m.parameterTypes.joinToString(", ") { it.simpleName }
        val doInvoke = {
            try {
                m.isAccessible = true
                val r =
                    if (receiver != null &&
                        !java.lang.reflect.Modifier
                            .isStatic(m.modifiers)
                    ) {
                        m.invoke(receiver, *converted)
                    } else {
                        m.invoke(null, *converted)
                    }
                XLog.w("${describe(OP_CALL)} $name($sig) → $r")
            } catch (e: Throwable) {
                XLog.w("${describe(OP_CALL)} $name 抛了：${Reflect.deepestCause(e).javaClass.name}: ${Reflect.deepestCause(e).message}")
            }
            Unit
        }
        // 注意：**视图上的方法必须在视图自己的线程上跑**，否则抛 `CalledFromWrongThreadException`
        // （真机踩过：从 AMS 广播入口进来是 binder 线程，视图属于 `android.ui`）。
        // 这里统一替调用方 post 过去 —— 有了它，`target=caption` 加上任意方法名就能调，
        // 不必再为每个方法写一个 op。
        val h =
            if (receiver == null) {
                null
            } else {
                try {
                    Reflect.call(receiver, "getHandler") as? Handler
                } catch (e: Throwable) {
                    null
                }
            }
        if (h != null) {
            XLog.w("${describe(OP_CALL)}：$name 是视图方法，post 到 ${h.looper.thread.name}")
            h.post(doInvoke)
        } else {
            doInvoke()
        }
        return
    }
    XLog.w("probe/call：$name 有 ${candidates.size} 个重载，但没有一个能接住 args=$rawArgs")
}

/**
 * 把字符串参数按形参类型转换；某一位转不了就返回 null（表示这个重载不合适）。
 *
 * 除了基本类型，`args` 里还认三种字面写法 —— 不然「要传对象」的方法就只能为它单独写一个 op：
 *  · `null` —— 转成 null（可空对象形参常用，例如 `startFlexibleWindowForRecentTasks(int, Rect)` 的 Rect）
 *  · `rect:l,t,r,b` —— 现构一个 `android.graphics.Rect`
 *  · `bundle:k=v;k=v` —— 现构一个 `android.os.Bundle`（值按 int / boolean / String 猜；
 *    分隔符用 `;` 与 `=`，避开 `--esal` 的逗号）
 */
internal fun ProbeChannel.coerce(
    args: List<String>,
    types: Array<Class<*>>,
): Array<Any?>? {
    if (args.size != types.size) return null
    return Array(types.size) { i ->
        val s = args[i]
        // `null` 对任何非原始类型都成立（原始类型不能为 null，直接判不合适）
        if (s == "null") {
            if (types[i].isPrimitive) return null else return@Array null
        }
        if (s.startsWith(RECT_PREFIX) && types[i] == android.graphics.Rect::class.java) {
            val p = s.removePrefix(RECT_PREFIX).split(",")
            if (p.size != 4) return null
            val v = p.map { it.trim().toIntOrNull() ?: return null }
            return@Array android.graphics.Rect(v[0], v[1], v[2], v[3])
        }
        if (s.startsWith(BUNDLE_PREFIX) && types[i] == android.os.Bundle::class.java) {
            return@Array parseBundle(s.removePrefix(BUNDLE_PREFIX))
        }
        // `Xxx::class.java` 是原始类型 int/long/…，`javaObjectType` 是装箱的 Integer/Long/…
        // （形参两种都可能出现）；写法上按 Kotlin 的类写，`java.lang.*::class.java` 会吃警告。
        when (types[i]) {
            Int::class.java, Int::class.javaObjectType -> {
                s.toIntOrNull() ?: return null
            }

            Long::class.java, Long::class.javaObjectType -> {
                s.toLongOrNull() ?: return null
            }

            Float::class.java, Float::class.javaObjectType -> {
                s.toFloatOrNull() ?: return null
            }

            Double::class.java, Double::class.javaObjectType -> {
                s.toDoubleOrNull() ?: return null
            }

            Boolean::class.java, Boolean::class.javaObjectType -> {
                when (s.lowercase()) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> return null
                }
            }

            String::class.java -> {
                s
            }

            else -> {
                null
            } // 复杂类型不硬转；调用点会看到「接不住」并提示
        }
    }
}

/**
 * `k=v;k=v` → `Bundle`。值按 **int → boolean → String** 的顺序猜（OEM 的 windowingMode / flags 都是 int）。
 *
 * 任一项格式不对就返回 null —— 表示「这个重载不合适」，与 [coerce] 其余分支同语义。
 */
internal fun ProbeChannel.parseBundle(body: String): android.os.Bundle? {
    val b = android.os.Bundle()
    if (body.isEmpty()) return b
    for (kv in body.split(";")) {
        val eq = kv.indexOf('=')
        if (eq <= 0) return null
        val k = kv.substring(0, eq)
        val v = kv.substring(eq + 1)
        val i = v.toIntOrNull()
        val bool =
            when (v.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        when {
            i != null -> b.putInt(k, i)
            bool != null -> b.putBoolean(k, bool)
            else -> b.putString(k, v)
        }
    }
    return b
}

internal fun ProbeChannel.resolveReceiver(
    loader: ClassLoader,
    target: String,
    intent: Intent,
): Any? =
    try {
        when (target) {
            TARGET_STATIC -> {
                null
            }

            // 拿任务 / TaskExt 当接收者 —— 探状态时最常用的两个（否则只能退成静态调用）
            TARGET_TASK -> {
                taskOf(loader, intent.getIntExtra(EXTRA_TASK_ID, -1))
            }

            TARGET_EXT -> {
                taskOf(loader, intent.getIntExtra(EXTRA_TASK_ID, -1))?.let { extOf(it) }
            }

            TARGET_CAPTION -> {
                taskOf(loader, intent.getIntExtra(EXTRA_TASK_ID, -1))
                    ?.let { captionViewOf(loader, it) }
            }

            TARGET_SINGLETON -> {
                singletonOf(loader, intent.getStringExtra(EXTRA_CLS).orEmpty())
            }

            TARGET_SERVICE -> {
                val instance = instanceOf(Reflect.findClass(loader, SERVICE_CLASS))
                instance ?: run {
                    XLog.w("probe：$SERVICE_CLASS.getInstance(...) 拿不到，退成静态路径")
                    null
                }
            }

            else -> {
                XLog.w("probe/call：target=$target 目前只有 static / service / task / ext / caption / singleton")
                null
            }
        }
    } catch (e: Throwable) {
        XLog.w("probe：拿 target=$target 的接收者失败：${e.message}")
        null
    }

/**
 * `zoom` —— 从**当前进程**发起「把某个任务收成小窗」的正门调用。
 *
 * 为什么要单独一个 op：桌面「浮窗」那条路的关键是 options 里的 `mZoomLaunchFlags=2`
 * （见 `device-findings.md` 附九），而 `call` 造不出 options 对象。
 *
 * 为什么必须能发到**桌面进程**（`-p com.android.launcher`）—— 这才是 A 方案的全部意义：
 * 服务端要 `realCallingPid == 桌面进程 pid`；同一个调用从别处发会被 `No transition roots` abort。
 *
 * 注意：**第二个形参是 `Bundle`，不是 `ActivityOptions`**（真机踩过一轮）：
 * `android.app.OplusActivityTaskManager#startActivityFromRecentsForFlexible(int, Bundle)` 是 AIDL 门面，
 * 直接把原始 Bundle 交给服务端，服务端自己 `SafeActivityOptions.fromBundle(options, callingPid, callingUid)`。
 * 传 `ActivityOptions` 会 `argument type mismatch`，而 `Reflect.call` 内部 `runCatching` **把它吞了**
 * ——日志上只看到干干净净的 `→ null`，像「调了但没反应」。所以这里改成**直接 invoke + 显式打 cause**。
 */
internal fun ProbeChannel.zoomFromHere(
    loader: ClassLoader,
    intent: Intent,
) {
    // 下面 `getInstance()` 会触发宿主类 `<clinit>` —— 与 `call` 同一条险路，同样要显式 opt-in
    if (!allowUnsafe(RomProfile.effective.reflection.zoomTaskManager, OP_ZOOM, intent)) return
    zoomFromHereGated(loader, intent)
}

/** [zoomFromHere] 过闸之后的那一半 —— 闸单独一层，免得这条路上的出口数顶到 detekt 上限。 */
private fun ProbeChannel.zoomFromHereGated(
    loader: ClassLoader,
    intent: Intent,
) {
    val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
    val flags = intent.getIntExtra(EXTRA_FLAGS, DEFAULT_ZOOM_LAUNCH_FLAGS)
    if (taskId <= 0) {
        XLog.w("${describe(OP_ZOOM)}：要带 task_id")
        return
    }
    val bundle =
        android.os.Bundle().apply {
            putInt(KEY_WINDOWING_MODE, WINDOWING_MODE_ZOOM)
            putInt(KEY_ZOOM_LAUNCH_FLAGS, flags)
        }
    val mgrCls = Reflect.findClass(loader, RomProfile.effective.reflection.zoomTaskManager)
    val mgr = mgrCls?.let { Reflect.callStatic(it, "getInstance") }
    if (mgrCls == null || mgr == null) {
        XLog.w("${describe(OP_ZOOM)}：OplusActivityTaskManager.getInstance 拿不到")
        return
    }
    // 注意：用 getDeclaredMethod **精确签名**，不要走 Reflect.method —— 它会退化成「名字 + 参数个数」匹配，
    // 把 Bundle 形参误配上，错误再被 runCatching 吞掉（这正是上一轮的空转）。
    val m =
        try {
            mgrCls.getDeclaredMethod(METHOD_RECENTS_FLEXIBLE, java.lang.Integer.TYPE, android.os.Bundle::class.java)
        } catch (e: NoSuchMethodException) {
            null
        }
    if (m == null) {
        XLog.w("${describe(OP_ZOOM)}：没有 $METHOD_RECENTS_FLEXIBLE(int, Bundle)")
        return
    }
    m.isAccessible = true
    val r =
        try {
            m.invoke(mgr, taskId, bundle)
        } catch (e: Throwable) {
            XLog.w("${describe(OP_ZOOM)} 抛了：${Reflect.deepestCause(e).javaClass.name}: ${Reflect.deepestCause(e).message}")
            return
        }
    // 返回值语义：2 = START_TASK_TO_FRONT（服务端受理并已 toggle）；0 = 没做任何事
    XLog.w("${describe(OP_ZOOM)}：$METHOD_RECENTS_FLEXIBLE(#$taskId, flags=$flags) → $r（2=已受理）")
}

/** 取任务的 caption view：先看 TaskExt 上有没有（通常已经有了），没有再让服务建一个。 */
internal fun ProbeChannel.captionViewOf(
    loader: ClassLoader,
    task: Any,
): Any? {
    val ext = extOf(task)
    val existing =
        try {
            ext?.let { Reflect.call(it, "getFlexibleCaptionView") }
        } catch (e: Throwable) {
            null
        }
    if (existing != null) return existing
    val service = serviceOf(loader) ?: return null
    val name =
        try {
            ext?.let { Reflect.call(it, "getCaptionName") } as? String
        } catch (e: Throwable) {
            null
        }
    return invokeOn(service, "createFlexibleCaptionView", task, name)
}

/**
 * 按名字 + 参数个数找方法并调用，返回「结果 / 抛了什么」的可读串（通道里到处要用）。
 *
 * 注意：**反射调用抛的异常会被包成 `InvocationTargetException`，而它的 message 恒为 null** ——
 * 直接打 `e.message` 只会得到「抛了 InvocationTargetException: null」，真实原因全丢。
 * 所以这里把 `cause` 链剥到最内层再打（真机上正是这么踩的）。
 */
internal fun ProbeChannel.invokeOn(
    receiver: Any,
    name: String,
    vararg args: Any?,
): String =
    try {
        val m = receiver.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == args.size }
        if (m == null) {
            "（$name/${args.size} 参不存在）"
        } else {
            m.isAccessible = true
            "${m.invoke(receiver, *args)}"
        }
    } catch (e: Throwable) {
        "抛了 ${Reflect.deepestCause(e).javaClass.name}: ${Reflect.deepestCause(e).message}"
    }

// 剥到最内层 cause 的那份实现已收到 `Reflect.deepestCause`（与 `FlexibleWindowBridge` 共用一份）。

/**
 * 读字段并格式化成一句诊断 —— **要沿父类往上找**（很多状态字段声明在父类上，
 * `getDeclaredField` 只看本类，真机踩过），那一层由 `Reflect.field` 一处负责。
 */
internal fun ProbeChannel.fieldOf(
    receiver: Any,
    name: String,
): String {
    val f = Reflect.field(receiver.javaClass, name) ?: return "（读不到 $name）"
    return try {
        "${f.get(receiver)}"
    } catch (e: Throwable) {
        "（读到但取值失败：${e.javaClass.simpleName}）"
    }
}

/**
 * 把操作条视图的**几何与父容器链**打出来。
 *
 * 「挂上了、也显示了，但位置不对」时第一个要看的就这几项 —— 真机第 3 轮正卡在这里：
 * `mCaptionViewSurfaceShow=true` 但操作条渲染成了左边缘一个小白条，得先知道它被摆在哪儿、谁摆的。
 */
internal fun ProbeChannel.stateOfCaptionView(view: Any) {
    listOf("getLeft", "getTop", "getWidth", "getHeight", "getX", "getY", "getVisibility")
        .forEach { n -> XLog.w("probe/state  ·$n() = ${invokeOn(view, n)}") }
    XLog.w("probe/state  ·handleButtonRegion = ${invokeOn(view, "getFlexibleCaptionViewHandleButtonRegion")}")
    var cur: Any? = view
    var depth = 0
    while (cur != null && depth < 6) {
        XLog.w("probe/state  ·父 $depth = ${cur.javaClass.name}")
        cur =
            try {
                Reflect.call(cur, "getParent")
            } catch (e: Throwable) {
                null
            }
        depth++
    }
}

/**
 * 拿某个类上的单例：先试 **0 参** `getInstance()`，再试 `getInstance(null)`，再试 `getInstance`（任意 1 参）。
 *
 * 每一步都打日志 —— 「哪一步拿到的」比「拿到了」更重要（拿不到时才判得出是方法名不对还是签名不对）。
 */
internal fun ProbeChannel.singletonOf(
    loader: ClassLoader,
    clsName: String,
): Any? {
    val cls = Reflect.findClass(loader, clsName)
    if (cls == null) {
        XLog.w("probe：singleton 找不到类 $clsName")
        return null
    }
    for (arity in listOf(0, 1)) {
        val m = compatMethod(cls, "getInstance", arity) ?: continue
        val args = if (arity == 0) arrayOf<Any?>() else arrayOf<Any?>(null)
        val v =
            try {
                m.isAccessible = true
                m.invoke(null, *args)
            } catch (e: Throwable) {
                XLog.w("probe：$clsName.getInstance($arity 参) 抛了：${Reflect.deepestCause(e).javaClass.simpleName}")
                null
            }
        if (v != null) {
            XLog.w("probe：$clsName 的单例来自 getInstance($arity 参) → ${v.javaClass.simpleName}")
            return v
        }
    }
    XLog.w("probe：$clsName 没有可用的 getInstance（0/1 参都试过）")
    return null
}

/** 找一个静态、参数个数匹配的 `getInstance`（不挑参数类型，够用）。 */
internal fun ProbeChannel.compatMethod(
    cls: Class<*>,
    name: String,
    arity: Int,
): java.lang.reflect.Method? =
    cls.methods.firstOrNull {
        it.name == name && it.parameterCount == arity &&
            java.lang.reflect.Modifier
                .isStatic(it.modifiers)
    }

internal fun ProbeChannel.serviceOf(loader: ClassLoader): Any? =
    try {
        instanceOf(Reflect.findClass(loader, SERVICE_CLASS))
    } catch (e: Throwable) {
        null
    }

/** 候选名逐个试 —— 不同 ColorOS 版本这里的方法名不一样。 */
internal fun ProbeChannel.surfaceControlOf(task: Any): android.view.SurfaceControl? {
    val candidates =
        listOf<Pair<String, Array<Any?>>>(
            "getSurfaceControl" to arrayOf(),
            "getSurfaceControl" to arrayOf(false),
            "getSurfaceControl" to arrayOf(true),
        )
    for ((name, args) in candidates) {
        val v =
            try {
                val m = Reflect.methodByNameAndArity(task.javaClass, name, args.size) ?: continue
                m.isAccessible = true
                m.invoke(task, *args)
            } catch (e: Throwable) {
                null
            }
        if (v is android.view.SurfaceControl) {
            XLog.w("probe/corner：SurfaceControl 来自 $name(${args.size} 参)")
            return v
        }
    }
    return null
}
