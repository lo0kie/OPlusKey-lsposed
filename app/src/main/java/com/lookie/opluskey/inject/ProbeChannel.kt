package com.lookie.opluskey.inject

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.bridge.anyTask
import com.lookie.opluskey.bridge.instanceOf
import com.lookie.opluskey.core.DEBUG_PROBE
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
