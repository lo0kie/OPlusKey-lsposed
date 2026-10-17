package com.lookie.opluskey.inject

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.DEBUG_PROBE
import com.lookie.opluskey.bridge.anyTask
import com.lookie.opluskey.bridge.extOf
import com.lookie.opluskey.bridge.instanceOf
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.model.ZoomWindowWire
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect

/**
 * # 模块的 debug 通道（debug 包固定能力）
 *
 * **它解决的是这个工程最贵的一个成本**：注入侧跑在 `system_server` / 桌面进程里，而 LSPosed 只在
 * **进程启动那一刻**注入 dex —— 注入侧改一行就要**整机重启**才加载，一轮试错几分钟。
 * 有了这条通道，「拿真实签名 / 试调用 / 在别的进程里发起」都变成**发一条广播**：
 * 不重新编译、不重启。凡是注入侧的实验，先想这条通道，别再靠改代码 + 重启去试。
 *
 * 六个 op（靠 `op` extra 选；完整用法与实例见 `docs/debug-channel.md`）：
 *
 * | op | 作用 | 关键 extra |
 * |---|---|---|
 * | `dump` | 列出某类的声明方法与字段，**连参数类型一起打**（消掉「签名靠猜」） | `cls` `filter` |
 * | `call` | 反射调一个方法并打出返回值 / 异常 | `cls` `name` `target` `args` `unsafe` |
 * | `state` | 只读打印目标任务的状态字段（含操作条视图到底建出来没有） | `task_id` |
 * | `corner` | 给目标任务的窗口 surface 设圆角（`SurfaceControl.Transaction`，公开 API） | `radius` `task_id` |
 * | `dress` | 「补壳」：圆角 + 操作条挂载 / 显示，三个开关分开试 | `task_id` `radius` `caption` `show` |
 * | `zoom` | 从**当前进程**发起「把某任务收成小窗」（`OplusActivityTaskManager#startActivityFromRecentsForFlexible`） | `task_id` `flags` |
 *
 * **两个落点**：`system_server`（经 `KeyHooks` 的 AMS 广播入口接住）与**桌面进程**
 * （[installInProcess]）。桌面那个落点是「调用方 pid == 桌面进程 pid」那条路的前提。
 *
 * **安全边界（四条，缺一条都不能上）**：
 * 1. **只在 debug 包存在** —— [DEBUG_PROBE] 是编译期常量，release 里这些代码连同调用点一起被折掉；
 * 2. **只收 root / system 发的** —— 发送方不是宿主，所以来源校验单列一条
 *    （`KeyHooks.isFromRootOrSystem`），普通 App（uid ≥ 10000）进不来；
 * 3. **每个 op 整体包 catch** —— 调用点在 AMS 广播入口，漏一个异常出去就是系统崩 / 开机循环。
 * 4. **`call` 另有一道准入闸** —— 它会对广播指定的类直接 `invoke`，而 **`invoke` 会触发宿主类的
 *    `<clinit>`**：system_server 类初始化顺序极早，静态块里的单例那时往往还是 null →
 *    `ExceptionInInitializerError` → 类被标 erroneous → 之后真用到它的地方 `NoClassDefFoundError`
 *    → **开机循环**（AGENTS.md 五之二、1）。故只有 `cls` 命中 [UNSAFE_CLASS_PREFIXES] **且**
 *    广播带了 [EXTRA_UNSAFE]=true 才放行，否则打 W 判据行拒绝（见 [allowUnsafe]）。
 */
internal object ProbeChannel {
    /** 调试指令的 action —— 从 adb 用 **root** 发（shell 身份过不了 NOT_EXPORTED 接收器；用法见 `docs/debug-channel.md`）。 */
    const val ACTION = "com.lookie.opluskey.PROBE"

    internal const val OP_DUMP = "dump"
    internal const val OP_CALL = "call"
    internal const val OP_STATE = "state"
    internal const val OP_CORNER = "corner"
    internal const val OP_DRESS = "dress"
    internal const val OP_ZOOM = "zoom"

    internal const val EXTRA_OP = "op"
    internal const val EXTRA_CLS = "cls"
    internal const val EXTRA_NAME = "name"
    internal const val EXTRA_FILTER = "filter"
    internal const val EXTRA_ARGS = "args"
    internal const val EXTRA_TARGET = "target"
    internal const val EXTRA_TASK_ID = "task_id"
    internal const val EXTRA_RADIUS = "radius"
    internal const val EXTRA_CAPTION = "caption"
    internal const val EXTRA_SHOW = "show"
    internal const val EXTRA_INIT = "init"
    internal const val EXTRA_RELAYOUT = "relayout"
    internal const val EXTRA_MODE = "mode"
    internal const val EXTRA_TOUCH = "touch"
    internal const val EXTRA_RECT_L = "rect_l"
    internal const val EXTRA_RECT_T = "rect_t"
    internal const val EXTRA_RECT_R = "rect_r"
    internal const val EXTRA_RECT_B = "rect_b"
    internal const val EXTRA_FLAGS = "flags"

    /**
     * [OP_CALL] 的显式 opt-in extra —— 不带它，这个 op 一律拒绝执行。
     *
     * `call` 会对广播指定的类直接 `invoke`，而 **`invoke` 会触发宿主类的 `<clinit>`**（见
     * [UNSAFE_CLASS_PREFIXES]）：所以「这一把确实要碰宿主类」必须由调用方在广播里**显式选一次**，
     * 而不是默认发生。
     */
    internal const val EXTRA_UNSAFE = "unsafe"

    /** `call` 的 `target`：从哪儿拿方法接收者。 */
    internal const val TARGET_STATIC = "static"
    internal const val TARGET_SERVICE = "service"
    internal const val TARGET_TASK = "task"
    internal const val TARGET_EXT = "ext"

    /**
     * `target=caption` —— 目标任务的操作条视图。
     *
     * 加它是为了**不再为这个类编译**：操作条上有一堆要逐个试的方法
     * （`updateTouchRegion` / `onRelayoutCaptionView` / `updateCaptionViewMode` …），
     * 每次加一个 op 都要改代码 + 整机重启；有了这个 target，它们全都能用广播调。
     */
    internal const val TARGET_CAPTION = "caption"

    /**
     * `target=singleton` —— 拿 `cls` 上的单例当接收者（先试 0 参 `getInstance()`，再试 `getInstance(null)`）。
     *
     * 有它是因为 OEM 那些 `XxxManager` 的方法**是不是静态得试了才知道**；没有这条，
     * 一问「这方法静态吗」就得再重启一次。
     */
    internal const val TARGET_SINGLETON = "singleton"

    /**
     * [OP_CALL] 允许触碰的宿主类前缀白名单 —— 「`cls` 命中它**且**广播带了 [EXTRA_UNSAFE]=true」才放行。
     *
     * 为什么收口：[OP_CALL] 按广播里的 `cls` + 方法名取 `cls.methods` 后直接 `m.invoke(...)`，
     * `target=static` 时 `cls` 就是**任意 system_server 类**，`target=singleton` 还会先走一次
     * `getInstance()`。而 **`invoke` 会触发宿主类的 `<clinit>`** —— system_server 的类初始化顺序
     * 极早，静态块里依赖的单例那时往往还是 null → `ExceptionInInitializerError` → 类被标 erroneous
     * → 之后真用到它的地方 `NoClassDefFoundError` → **开机循环**（AGENTS.md 五之二、1 的红线）。
     *
     * 每个前缀的来由（都是本文件里实际出现过的目标类，不是凭空圈的范围）：
     * - `com.android.server.wm.` —— [SERVICE_CLASS]（`FlexibleWindowManagerService`）以及它
     *   `getInstance()` 出来的任务 / `TaskExt` / caption view 全在这个包；
     * - `android.app.OplusActivityTaskManager` —— `zoom` op 的门面类（`getInstance()` + 精确签名调用）。
     */
    internal val UNSAFE_CLASS_PREFIXES =
        listOf(
            "com.android.server.wm.",
            RomProfile.effective.reflection.zoomTaskManager,
        )

    /** 圆角 / 阴影半径的默认值 —— 与真机那次成功过渡用的 bundle 值一致（`LaunchCornerRadius=91`）。 */
    internal const val DEFAULT_CORNER_RADIUS = 91f

    /** 打签名时的上限，免得一个类几千个方法把日志刷爆。 */
    internal const val DUMP_LIMIT = 90

    /** `call` 的 `args` 里写 `rect:l,t,r,b` 就现构一个 `Rect`（见 [coerce]）。 */
    internal const val RECT_PREFIX = "rect:"

    /**
     * `call` 的 `args` 里写 `bundle:k=v;k=v` 就现构一个 `Bundle`（见 [coerce]）。
     *
     * 为什么必须有它：**OEM 的入口方法大量收 `Bundle`**（`startActivityFromRecentsForFlexible(int, Bundle)`
     * 就是），而没有它，「换一个 key 试一次」都要改代码 + 重编。分隔符特意用 `;` 和 `=`，
     * 避开 `--esal` 自己的逗号分隔 —— 于是整条调用能塞进一个 extra。
     */
    internal const val BUNDLE_PREFIX = "bundle:"

    /**
     * `zoom` op 用的 key / 常量 —— 取值收在 `core/model/ZoomWindowWire` 一处。
     *
     *  这里原来自己写了一遍，注释写着「必须与宿主侧同名项一致」—— 那句话正是耦合的自白：探针试出来的
     *  结论要能代表真正执行的那条路，两边必须逐字相同，而「靠注释提醒」在换 ROM 时不起作用。
     */
    internal const val KEY_WINDOWING_MODE = ZoomWindowWire.LAUNCH_WINDOWING_MODE_KEY
    internal const val KEY_ZOOM_LAUNCH_FLAGS = ZoomWindowWire.ZOOM_LAUNCH_FLAGS_KEY
    internal const val WINDOWING_MODE_ZOOM = ZoomWindowWire.WINDOWING_MODE_ZOOM
    internal const val DEFAULT_ZOOM_LAUNCH_FLAGS = ZoomWindowWire.LAUNCH_FLAG_EXPANDED
    internal val METHOD_RECENTS_FLEXIBLE = RomProfile.effective.reflection.zoomRecentsMethod

    /**
     * 日志前缀统一 `probe/<op>`，grep 这一个词就能把这条通道的输出捞干净。
     * 位置：`/data/adb/lspd/log/modules_*.log`（不进 logcat）。
     */
    internal fun describe(op: String) = "probe/$op"

    // ── 接收侧：system_server（经 KeyHooks 的 AMS 广播入口） ────────────────────

    /**
     * @param loader system_server / 桌面进程的 ClassLoader
     * @return 是否吃掉了这条广播（action 不匹配返回 false）
     */
    fun handle(
        intent: Intent,
        loader: ClassLoader?,
    ): Boolean {
        if (!DEBUG_PROBE) return false
        if (intent.action != ACTION) return false
        val op =
            try {
                intent.getStringExtra(EXTRA_OP).orEmpty()
            } catch (e: Throwable) {
                ""
            }
        if (loader == null) {
            XLog.w("${describe(op)}：还没有本进程的 ClassLoader，跳过")
            return true
        }
        try {
            when (op) {
                OP_DUMP -> dump(loader, intent)
                OP_CALL -> call(loader, intent)
                OP_STATE -> state(loader, intent)
                OP_CORNER -> corner(loader, intent)
                OP_DRESS -> dress(loader, intent)
                OP_ZOOM -> zoomFromHere(loader, intent)
                else -> XLog.w("${describe(op)}：不认识的 op")
            }
        } catch (e: Throwable) {
            XLog.w("${describe(op)} 失败（已忽略）：${e.javaClass.simpleName}: ${e.message}")
        }
        return true
    }

    // ── 接收侧：桌面进程（自带一个接收器） ──────────────────────────────────────

    @Volatile
    internal var launcherReceiver: BroadcastReceiver? = null
    internal val handler = Handler(Looper.getMainLooper())

    /**
     * 在**桌面进程**里装一个接收器 —— 桌面身份是 A 方案的全部意义所在
     * （服务端要看调用方 pid 是不是桌面），所以这条通道必须也能在桌面进程里执行。
     *
     * 用 `RECEIVER_NOT_EXPORTED`：只有 root / 系统的广播进得来，普通 App 发同名广播进不来。
     */
    fun installInProcess(
        loader: ClassLoader?,
        label: String,
    ) {
        if (!DEBUG_PROBE) return
        if (loader == null) return
        if (launcherReceiver != null) return
        tryGetContext(loader, label, attempt = 0)
    }

    internal fun tryGetContext(
        loader: ClassLoader,
        label: String,
        attempt: Int,
    ) {
        // onPackageReady 时 Application 未必挂上（`currentApplication()` 可能是 null），
        // 所以拿不到就退后重试 —— 否则整条通道静默失效（同 host/GestureReceiver 的踩坑）。
        val ctx = ActivityThreadCompat.currentApplication(loader)
        if (ctx == null) {
            if (attempt >= CTX_RETRY) {
                XLog.w("probe：$label 拿不到 Application Context（重试 $attempt 次），通道没装上")
                return
            }
            handler.postDelayed({ tryGetContext(loader, label, attempt + 1) }, CTX_RETRY_MS)
            return
        }
        try {
            val r =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        c: Context?,
                        intent: Intent?,
                    ) {
                        if (intent == null) return
                        // 桌面进程里 ClassLoader 就是本进程的，直接用
                        XLog.guard("probe/$label 执行") { handle(intent, loader) }
                    }
                }
            ctx.registerReceiver(r, android.content.IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)
            launcherReceiver = r
            XLog.w("probe：调试通道已装在 $label（package=${ctx.packageName}）")
        } catch (e: Throwable) {
            XLog.w("probe：$label 注册接收器失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    internal const val CTX_RETRY = 40
    internal const val CTX_RETRY_MS = 250L

    // ── op: dump ─────────────────────────────────────────────────────────────

    /** 把声明方法和字段连**参数类型**一起打出来 —— 这条通道最大的用处就是消掉「签名靠猜」。 */
    internal fun dump(
        loader: ClassLoader,
        intent: Intent,
    ) {
        val clsName = intent.getStringExtra(EXTRA_CLS).orEmpty()
        val filter = intent.getStringExtra(EXTRA_FILTER).orEmpty()
        if (clsName.isEmpty()) {
            XLog.w("probe/dump：要带 cls")
            return
        }
        val cls = Reflect.findClass(loader, clsName)
        if (cls == null) {
            XLog.w("probe/dump：找不到类 $clsName")
            return
        }
        val methods =
            cls.declaredMethods
                .filter { filter.isEmpty() || it.name.contains(filter) }
                .sortedWith(compareBy({ it.name }, { it.parameterCount }))
        XLog.w("probe/dump $clsName filter='$filter' → ${methods.size} 个方法")
        methods.take(DUMP_LIMIT).forEach { m ->
            XLog.w(
                "probe  · ${m.name}(${m.parameterTypes.joinToString(", ") { it.simpleName }})" +
                    " : ${m.returnType.simpleName}",
            )
        }
        val fields =
            cls.declaredFields
                .filter { filter.isEmpty() || it.name.contains(filter) }
                .sortedBy { it.name }
        if (fields.isNotEmpty()) {
            XLog.w("probe/dump $clsName filter='$filter' → ${fields.size} 个字段")
            fields.take(DUMP_LIMIT).forEach { f ->
                XLog.w("probe  · ${f.name} : ${f.type.simpleName}")
            }
        }
    }

    // ── op: call ─────────────────────────────────────────────────────────────

    /**
     * [OP_CALL] 的准入闸：`clsName` 命中 [UNSAFE_CLASS_PREFIXES] **且**广播带了 [EXTRA_UNSAFE]=true
     * 才返回 true；否则打一条 W 判据行并拒绝。
     *
     * 判据行必须 W：注入侧的 `i` / `d` 在日志页里整档看不到（AGENTS.md 二、1.2），而这一行正是
     * 「这次为什么没执行」的唯一答案 —— 不许静默 return，也不许只说一句「拒绝」了事，必须写清
     * 哪条 op、哪个类、卡在哪一条。
     */
    internal fun allowUnsafe(
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
    internal fun resolveGatedTarget(
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
    internal fun call(
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
    internal fun coerce(
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
    internal fun parseBundle(body: String): android.os.Bundle? {
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

    internal fun resolveReceiver(
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
    internal fun zoomFromHere(
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
    internal fun captionViewOf(
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
    internal fun invokeOn(
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
    internal fun fieldOf(
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
    internal fun stateOfCaptionView(view: Any) {
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
    internal fun singletonOf(
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
    internal fun compatMethod(
        cls: Class<*>,
        name: String,
        arity: Int,
    ): java.lang.reflect.Method? =
        cls.methods.firstOrNull {
            it.name == name && it.parameterCount == arity &&
                java.lang.reflect.Modifier
                    .isStatic(it.modifiers)
        }

    internal fun serviceOf(loader: ClassLoader): Any? =
        try {
            instanceOf(Reflect.findClass(loader, SERVICE_CLASS))
        } catch (e: Throwable) {
            null
        }

    /** 候选名逐个试 —— 不同 ColorOS 版本这里的方法名不一样。 */
    internal fun surfaceControlOf(task: Any): android.view.SurfaceControl? {
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

    // ── 小工具：拿任务 ──
    //
    //  `TaskExt`（`extOf`）与「逐档试 matchMode」（`anyTask`）**不在这里**：那两件与生产路径
    //  共用 `bridge/FlexibleWindowLookup.kt` 那一份 —— 探针存在的意义就是「在真机上问出生产
    //  会看到什么」，各写一份时先漂的总是探针（它以前只试 mode=0）。

    internal val SERVICE_CLASS = RomProfile.effective.reflection.flexibleWindowService
    internal const val FIELD_ATMS = "mAtms"
    internal const val FIELD_ROOT = "mRootWindowContainer"

    internal fun taskOf(
        loader: ClassLoader,
        taskId: Int,
    ): Any? =
        try {
            if (taskId <= 0) {
                null
            } else {
                val service = instanceOf(Reflect.findClass(loader, SERVICE_CLASS))
                val controller = service?.let { Reflect.call(it, "getFlexibleTaskController") }
                val atms = controller?.let { Reflect.fieldValue(it, it.javaClass, FIELD_ATMS) }
                val root = atms?.let { Reflect.fieldValue(it, it.javaClass, FIELD_ROOT) }
                root?.let { anyTask(it, taskId, "probe") }
            }
        } catch (e: Throwable) {
            XLog.w("probe：拿任务 #$taskId 失败：${e.message}")
            null
        }
}
