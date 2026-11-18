package com.lookie.opluskey.inject.wire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.lookie.opluskey.bridge.anyTask
import com.lookie.opluskey.bridge.extOf
import com.lookie.opluskey.bridge.instanceOf
import com.lookie.opluskey.core.DEBUG_PROBE
import com.lookie.opluskey.core.judge.Swipe
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.ZoomWindowWire
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.core.rom.RomProfile
import com.lookie.opluskey.inject.input.ComboDown
import com.lookie.opluskey.inject.input.ComboPlan
import com.lookie.opluskey.inject.input.ComboSequence
import com.lookie.opluskey.inject.input.KeyRef
import com.lookie.opluskey.inject.input.RawInput
import com.lookie.opluskey.inject.input.TouchJudge
import com.lookie.opluskey.inject.system.ShortcutRead

/**
 * # 模块的 debug 通道（debug 包固定能力）
 *
 * **它解决的是这个工程最贵的一个成本**：注入侧跑在 `system_server` / 桌面进程里，而 LSPosed 只在
 * **进程启动那一刻**注入 dex —— 注入侧改一行就要**整机重启**才加载，一轮试错几分钟。
 * 有了这条通道，「拿真实签名 / 试调用 / 在别的进程里发起」都变成**发一条广播**：
 * 不重新编译、不重启。凡是注入侧的实验，先想这条通道，别再靠改代码 + 重启去试。
 *
 * 六个 op（靠 `op` extra 选；完整用法与实例见 `docs/runtime/debug-channel.md`）：
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
 *    （`BroadcastSender.isFromRootOrSystem`，见 `inject/KeyHooks.kt`），普通 App（uid ≥ 10000）进不来；
 * 3. **每个 op 整体包 catch** —— 调用点在 AMS 广播入口，漏一个异常出去就是系统崩 / 开机循环。
 * 4. **凡是要实例化宿主类的 op 都有一道准入闸** —— `call` 会对广播指定的类直接 `invoke`，
 *    `state` / `corner` / `dress` / `zoom` 则经 `FlexibleWindowManagerService.getInstance()`；两者都会
 *    触发宿主类的 `<clinit>`：system_server 类初始化顺序极早，静态块里的单例那时往往还是 null →
 *    `ExceptionInInitializerError` → 类被标 erroneous → 之后真用到它的地方 `NoClassDefFoundError`
 *    → **开机循环**（AGENTS.md 五之二、1）。故只有类名命中 [UNSAFE_CLASS_PREFIXES] **且**
 *    广播带了 [EXTRA_UNSAFE]=true 才放行，否则打 W 判据行拒绝（见 [allowUnsafe]）。
 */
internal object ProbeChannel {
    /** 调试指令的 action —— 从 adb 用 **root** 发（shell 身份过不了 NOT_EXPORTED 接收器；用法见 `docs/runtime/debug-channel.md`）。 */
    const val ACTION = "com.lookie.opluskey.PROBE"

    internal const val OP_DUMP = "dump"
    internal const val OP_CALL = "call"
    internal const val OP_STATE = "state"
    internal const val OP_CORNER = "corner"
    internal const val OP_DRESS = "dress"
    internal const val OP_ZOOM = "zoom"

    /**
     * `net` —— 在**本进程**（system_server）里连一次网络。
     *
     *  存在的理由只有一个：验「注入侧能不能查更新」。界面层跑在宿主进程，宿主没有 `INTERNET`
     *  （真机实证 `SecurityException`），所以「检查更新」能不能自动做，取决于 system_server
     *  这一侧能不能连出去 —— 权限层 uid 1000 有 `INTERNET`，真正卡人的是 SELinux 域。
     */
    internal const val OP_NET = "net"

    /**
     * `touch` —— 手势判定层的自检：进程内造几条合成轨迹，跑一遍「触摸适配 → 滑动判定」。
     *
     *  触摸事件目前在系统进程里还没有采集点（入口未确认，见 `TouchJudge` 的类注释），
     *  所以这条链的唯一现成驱动就是它：先证明几何算得对，真实入口接上之后只剩一行线。
     */
    internal const val OP_TOUCH = "touch"

    /**
     * `combo` —— 组合键判定层的自检：造几档组合与几串按下，验「凑没凑齐」那条判据
     * （`ComboSequence`，与成员数无关；判定器 `ComboDetector` 那两条路是它的镜像，两边必须同改）。
     */
    internal const val OP_COMBO = "combo"

    /**
     * `shortcuts` —— 按**系统身份**读某个包的快捷方式（`pkg` / `user` 两个 extra）。
     *
     *  存在的理由：`选择界面` 里那份清单在宿主进程读，而宿主不是桌面宿主，**钉住的那一档**拿不到
     *  （真机实测微信 `动态 3 / 钉住 0`）。注入侧这一份跑在 `system_server`，换个身份再读一次 ——
     *  先在**不动界面**的情况下验这一趟值不值得接进正式通道（`ShortcutRead` 是共用的那一份实现）。
     */
    internal const val OP_SHORTCUTS = "shortcuts"

    /** `shortcuts` 的目标包名。 */
    internal const val EXTRA_PKG = "pkg"

    /** `shortcuts` 的用户（0 = 主用户，999 = 分身）。 */
    internal const val EXTRA_USER = "user"

    /** `net` 的目标地址（不填就用模块仓库的 releases 接口 —— 检查更新真正要打的那个）。 */
    internal const val EXTRA_URL = "url"

    internal const val DEFAULT_PROBE_URL =
        "https://api.github.com/repos/Xposed-Modules-Repo/io.github.lo0kie.opluskey/releases/latest"

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
     * 「这一把要碰宿主类」的显式 opt-in extra —— 不带它，凡是要实例化宿主类的 op 一律拒绝执行
     * （`call` 的 `invoke`、`state` / `corner` / `dress` / `zoom` 的 `getInstance()`）。
     *
     * 两者都会触发宿主类的 `<clinit>`（见 [UNSAFE_CLASS_PREFIXES]）：所以「这一把确实要碰宿主类」
     * 必须由调用方在广播里**显式选一次**，而不是默认发生。
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
     * 广播入口那一层的**分派**：丢到后台线程跑、整段兜住（`KeyHooks.dispatchAction` 只留一行调用）。
     *
     *  为什么兜底与 `post` 都收在这里：调用点那个 `when` 贴着 detekt 的
     *  `CyclomaticComplexMethod` 上限，多一个 `try / catch` 就超一格；而「注入侧任何一环
     * 抛出去就是 system_server 崩」这条纪律本来就该跟着这条通道走（五之二、2）。
     *
     *  **不能就地执行、也不投主线程**：`handle` 里是反射与宿主类实例化（`state` / `corner` /
     *  `dress` / `zoom` 都要问宿主服务），放在 AMS 的广播入口上跑等于把广播分发停住几秒，
     *  挂到 `system_server` 主线程上则会被 watchdog 算进卡死。探针是手发的低频指令，一条
     *  单线程池就够（[probeWorker]）。
     */
    fun dispatch(
        intent: Intent,
        loader: ClassLoader?,
    ) {
        probeWorker.execute {
            try {
                handle(intent, loader)
            } catch (e: Throwable) {
                XLog.w("探针指令执行失败（已忽略）：${e.javaClass.simpleName}", e)
            }
        }
    }

    /**
     * 探针的执行器 —— **一条线程、只给 debug 包**（[DEBUG_PROBE] 为 false 时整条通道连同这里
     * 一起被编译期折掉）。
     *
     *  用它而不是常驻 `HandlerThread`：探针一辈子只有几十条指令，线程随取随跑、不占 looper
     *  队列；单线程也顺带保证两条探针不会同时去实例化同一个宿主类。
     */
    private val probeWorker: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "opluskey-probe").apply { isDaemon = true }
        }

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
            runOp(op, intent, loader)
        } catch (e: Throwable) {
            XLog.w("${describe(op)} 失败（已忽略）：${e.javaClass.simpleName}: ${e.message}")
        }
        return true
    }

    /**
     * op 分发表 —— 单拎出来是因为**加一个 op 不该把 [handle] 推过 detekt 的
     * `CyclomaticComplexMethod` 上限**（这一格当初就是这么顶上去的）：入口只保留
     * 「action 对不对 / 有没有 loader / 兜住异常」三件事，这张表只回答「这个 op 交给谁」。
     */
    private fun runOp(
        op: String,
        intent: Intent,
        loader: ClassLoader,
    ) {
        when (op) {
            OP_DUMP -> dump(loader, intent)
            OP_CALL -> call(loader, intent)
            OP_STATE -> state(loader, intent)
            OP_CORNER -> corner(loader, intent)
            OP_DRESS -> dress(loader, intent)
            OP_ZOOM -> zoomFromHere(loader, intent)
            OP_NET -> net(intent, loader)
            OP_TOUCH -> touch()
            OP_COMBO -> combo()
            OP_SHORTCUTS -> shortcuts(intent, loader)
            else -> XLog.w("${describe(op)}：不认识的 op")
        }
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

// 探针的 `call` 那一族（含它用的反射引擎：定位 / 强制转换 / 收包 / 取字段）。
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
 * （见 `docs/features/zoom-window/api.md` 附九），而 `call` 造不出 options 对象。
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
    //  `methodByNameAndArity` 只看「名字 + 参数个数」，所以同一名字同 arity 的候选只有第一条
    //  走得到；`arrayOf(true)` 那一档原先排在 `arrayOf(false)` 后面，永远轮不到。
    val candidates =
        listOf<Pair<String, Array<Any?>>>(
            "getSurfaceControl" to arrayOf(),
            "getSurfaceControl" to arrayOf(false),
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

// 探针的四个 op：看任务状态（`state`）、给窗口设圆角（`corner`）、「补壳」（`dress`）与其内部动作。
// 它们用的那几个小工具（反射引擎）在上半段 `call` 那一族里。

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
        //  `Reflect.call` 把失败吞成 null（见它自己那句「查不到就记账」），而
        //  `Transaction.setCornerRadius` 是**链式返回**（返回 this）：null 就是没成。
        //  不判它的话，上一行已经写了 invoke failed，下一行还打「已设为」，排查时会当成生效了。
        if (Reflect.call(tx, m, sc, radius) == null) {
            XLog.w("${describe(OP_CORNER)}：setCornerRadius 没调成功（上面那行写了断在哪），本次不生效")
            return
        }
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
            XLog.d { "关闭 Transaction 失败（已忽略）：${e.javaClass.simpleName}" }
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

// ── op: net ──────────────────────────────────────────────────────────────

/**
 * 在本进程（system_server）里试一次联网 —— 验「注入侧能不能查更新」。
 *
 *  **必须另起线程**：这条广播在 AMS 的广播入口上跑，而联网是阻塞 IO —— 在主线程上做等于
 *  把整个系统的广播分发停住几秒（五之二、3：注入侧绝不拖慢系统，更不能把异常抛回去）。
 *
 *  分三步打日志，因为这三步是三个不同的闸，一次问清：
 *  1. **裸 TCP**（字面 IP、不经 DNS）—— 过了就说明域允许 inet socket，与 DNS 无关；
 *  2. **DNS**（`Network#getAllByName`，绑在默认网络上解析）—— system_server 自己的 socket
 *     没有「进程默认网络」，裸 `InetAddress.getByName` 会 `UnknownHostException`（实测），
 *     必须走这个 API；
 *  3. **HTTP**（拿第 2 步的地址走 `HttpURLConnection`）—— 真正要用的那一步。
 *
 *  `MissingPermission`（下面那颗抑制）：这段跑在 **system_server**（uid 1000），
 *  `ACCESS_NETWORK_STATE` 它本来就有（`docs/device/lsposed.md` 四十一）；模块清单里不声明任何权限，
 *  同 `NetChannel.activeNetwork`。
 */
@android.annotation.SuppressLint("MissingPermission")
internal fun ProbeChannel.net(
    intent: Intent,
    loader: ClassLoader,
) {
    val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_PROBE_URL
    val host = runCatching { java.net.URI(url).host }.getOrNull() ?: "api.github.com"
    val uid = android.os.Process.myUid()
    Thread(
        {
            //  取默认网络走 `NetChannel.activeNetwork` —— 与「检查更新」那条真实路径同一份实现：
            //  探针要回答的正是「那条路在这台机器上能不能出网」，各写一份就答不到它。
            val network = NetChannel.activeNetwork(loader)
            XLog.w("probe/net：uid=$uid activeNetwork=$network")

            // 1) 裸 TCP：字面 IP，不经 DNS —— 验的是「域允不允许 inet socket」
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress("223.5.5.5", 443), PROBE_NET_TIMEOUT_MS)
                    XLog.w("probe/net：裸 TCP 223.5.5.5:443 通了")
                }
            } catch (e: Throwable) {
                XLog.w("probe/net：裸 TCP 不通 → ${e.javaClass.name}: ${e.message}")
            }

            // 2) DNS：必须绑在默认网络上解析（system_server 的 socket 没有进程默认网络）
            val addrs =
                try {
                    network?.getAllByName(host).also {
                        XLog.w("probe/net：DNS $host → ${it?.joinToString() ?: "null"}")
                    }
                } catch (e: Throwable) {
                    XLog.w("probe/net：DNS 失败 → ${e.javaClass.name}: ${e.message}")
                    null
                }

            // 3) 真正要用的那一步：HTTP
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = PROBE_NET_TIMEOUT_MS
                conn.readTimeout = PROBE_NET_TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "OPlusKey-probe")
                XLog.w("probe/net：HTTP ${conn.responseCode}（$url，DNS 拿到 ${addrs?.size ?: 0} 个地址）")
                conn.disconnect()
            } catch (e: Throwable) {
                XLog.w("probe/net：HTTP 失败 → ${e.javaClass.name}: ${e.message}")
            }
        },
        "probe-net",
    ).start()
}

/** `net` 那一趟的超时 —— 5 秒：够一次 DNS + TLS，又不至于把线程吊在那儿。 */
private const val PROBE_NET_TIMEOUT_MS = 5_000

// ── op: touch ────────────────────────────────────────────────────────────

/**
 * `touch` —— 判定层的**自检**：在进程内造几条合成轨迹，跑一遍
 * 适配（`MotionEvent` → `RawInput`）→ 滑动判定，逐条打出「期望 / 实得」。
 *
 *  为什么在探针里做：触摸事件目前在系统进程里**没有采集点**（入口还没在真机上确认，见 `TouchJudge`
 *  的类注释），可判定与适配不能是「写完没跑过」的代码。合成轨迹把这条链跑通，等于先证明
 *  「同一个几何算出来的结论是对的」，等真实入口确认后只需接上线。
 *
 *  期望值写死在断言里（这些都是判据，不是手感）：点按不产出、快扫报方向、慢拖不产出、
 *  多指整段作废。任何一条与期望不符，日志里那一行就是现场。
 */
internal fun ProbeChannel.touch() {
    //  合成事件的时间轴：判定只取**同一条轨迹内**的差值，基准取哪儿都行
    val down = android.os.SystemClock.uptimeMillis()

    /** 造一条事件喂进去，返回这一段判出来的滑动（只有抬手那一条会有结论）。 */
    fun feed(
        action: Int,
        atMs: Long,
        x: Float,
        y: Float,
    ): Swipe? {
        val e = MotionEvent.obtain(down, down + atMs, action, x, y, 0)
        return try {
            TouchJudge.onMotionEvent(e)
        } finally {
            runCatching { e.recycle() }
        }
    }

    fun report(
        name: String,
        expected: String,
        actual: Swipe?,
    ) {
        XLog.w("probe/touch：$name → 期望 $expected，实得 ${actual ?: "不产出"}")
    }

    // 1) 点按：位移 3px，还在 slop（28px）里
    feed(MotionEvent.ACTION_DOWN, 0, 500f, 1000f)
    report("点按", "不产出", feed(MotionEvent.ACTION_UP, 30, 503f, 1002f))

    // 2) 快扫：向右 340px / 150ms
    feed(MotionEvent.ACTION_DOWN, 200, 500f, 1500f)
    feed(MotionEvent.ACTION_MOVE, 240, 600f, 1502f)
    feed(MotionEvent.ACTION_MOVE, 280, 700f, 1508f)
    feed(MotionEvent.ACTION_MOVE, 320, 840f, 1510f)
    report("右扫", "RIGHT 340px", feed(MotionEvent.ACTION_UP, 350, 840f, 1510f))

    // 3) 慢拖：同样的位移，但跨了 1000ms（超过 800ms 那一档）
    feed(MotionEvent.ACTION_DOWN, 400, 500f, 2000f)
    feed(MotionEvent.ACTION_MOVE, 800, 700f, 2000f)
    feed(MotionEvent.ACTION_MOVE, 1200, 840f, 2000f)
    report("慢拖", "不产出", feed(MotionEvent.ACTION_UP, 1400, 840f, 2000f))

    // 4) 多指：`MotionEvent.obtain(down, …)` 只能造单指事件，这一档直接喂归一化事件
    //    （判据是「手指数 > 1 就作废」，与走不走适配器无关）
    TouchJudge.submit(RawInput.TouchDown(0, 500f, 2500f, pointers = 2, atMs = 2000))
    TouchJudge.submit(RawInput.TouchUp(0, 900f, 2500f, pointers = 2, atMs = 2100))
    XLog.w("probe/touch：多指 → 期望 不产出（上面「滑动：」那行不该出现第二条）")
}

// ── op: combo ────────────────────────────────────────────────────────────

/**
 * `combo` —— 组合键判定层的**自检**：造几档组合与几串按下，跑一遍「凑没凑齐」的判据。
 *
 *  为什么放在探针里：真机上组合判定由 `ComboDetector` 那条事件状态机扛着，一次按压只有一条
 *  证据、判错了没人能重放。同一套规则在这里写成**无状态的纯判据**（`ComboSequence`），
 *  合成序列跑通就等于先证明「同一串按下的结论是对的」—— 判据错了在这一步就红，而不是等真机。
 *
 *  **两边必须同改**（见 `ComboSequence` 的类注释）：这里期望的每一条都是判定器口径的镜像，
 *  多成员（3 颗起）这一轮已经接线，所以「三颗」那一档现在就该凑齐。
 *
 *  期望值写死在断言里（都是判据，不是手感）：两颗窗口内凑齐、超窗不成立、只按一颗报还差、
 *  中间夹一颗不相干的键不影响、三颗凑齐成员多的那一档。
 */
internal fun ProbeChannel.combo() {
    //  成员键 id 全部取自 `KeyId`（内置侧键 + 第 1~3 颗自定义键）—— 这里不写字面量，
    //  免得前缀一改这条自检就悄悄测了一堆不存在的键。
    val plus = KeyId.PLUS.id
    val ck1 = KeyId.customId(1)
    val ck2 = KeyId.customId(2)
    val ck3 = KeyId.customId(3)

    /**
     * 字符串 id → 判定层的 [KeyRef]。
     *
     *  探针从命令行拿的是字符串（`--es` 只有字符串），判定层只认 [KeyRef]；而 [combo] 判的是
     *  「凑没凑齐」，只按**身份相等**比 —— 所以这里拿字符串本身当身份，不绕去 [KeyId]。
     */
    fun ref(id: String): KeyRef = KeyRef(id, id)

    fun plan(
        name: String,
        vararg keys: String,
    ): ComboPlan =
        ComboPlan(
            name = name,
            keys = keys.map(::ref).toSet(),
            //  探针按「相邻两颗一个窗口」摆跨度：与 `Config.comboSpanFor` 在
            //  「固定窗口起点关着」时同一个算法，期望值才好写成人能算的数
            spanMs = 300L * (keys.size - 1),
        )

    val plans = listOf(plan("cmb1", plus, ck1), plan("cmb2", plus, ck1, ck2), plan("cmb3", plus, ck1, ck2, ck3))

    fun report(
        name: String,
        expected: String,
        downs: List<Pair<String, Long>>,
    ) {
        val gather =
            ComboSequence.resolve(
                downs.map { (key, at) -> ComboDown(ref(key), at) },
                plans,
            )
        val actual = ComboSequence.describe(gather)
        //  期望写成 `describe` 的**前缀**（`凑齐 cmb1` / `还差 2 颗（cmb3）`）—— 对不上就打一句
        //  「不符」：只把两串并排写出来，真的漂了也没人看得出哪一行是坏的。
        val hit = actual.startsWith(expected)
        XLog.w("probe/combo：$name → 期望 $expected，实得 $actual${if (hit) "" else "  ← 不符"}")
    }

    // 1) 两颗顺序按下、间隔 100ms（窗口 300）—— 真机上走的就是这条
    report("两颗 / 间隔 100ms", "凑齐 cmb1", listOf(plus to 0L, ck1 to 100L))

    // 2) 同一对超过窗口：**cmb1 不成立**，但三颗 / 四颗那两档仍在等（跨度按成员数放大，
    //    (n-1) 个窗口）—— 多档并行时报的是「成员最多的那一档还差几颗」，所以结论是
    //    「还差 2 颗（cmb3）」而不是「不成立」。
    report("两颗 / 间隔 400ms", "还差 2 颗（cmb3）", listOf(plus to 0L, ck1 to 400L))

    // 3) 只按了第一颗 ⇒ 每一档都还差着，报**成员最多那一档**的缺口（cmb3 差 3 颗）
    report("只按一颗", "还差 3 颗（cmb3）", listOf(plus to 0L))

    // 4) 中间夹一颗不相干的键 ⇒ 不影响（判定器那边叫「配不上的候选不占位」）
    report("中间夹一颗别的键", "凑齐 cmb1", listOf(plus to 0L, KeyId.customId(9) to 50L, ck1 to 100L))

    // 5) 三颗按顺序 ⇒ 凑齐成员多的那一档（`maxByOrNull { keys.size }` 那条）
    report("三颗 / 间隔 80ms", "凑齐 cmb2", listOf(plus to 0L, ck1 to 80L, ck2 to 160L))

    // 6) 同一颗按了两次 ⇒ 按最后那一次算（判定器的 `recent` 只留最后一条）
    report("同一颗按两次", "凑齐 cmb1", listOf(plus to 0L, plus to 40L, ck1 to 80L))

    // 7) 三颗、**每颗之间**刚好隔一个窗口 ⇒ 仍凑齐成员多的那一档
    //    （整串跨度 = 2 个窗口。三颗只给一个窗口时这一串会被判成不成立 —— 那正是真机上
    //    「三颗的组合怎么按都没反应」的那条，所以这一条必须一直是「凑齐 cmb2」）
    report("三颗 / 每颗间隔 300ms", "凑齐 cmb2", listOf(plus to 0L, ck1 to 300L, ck2 to 600L))
}

// ── op: shortcuts ────────────────────────────────────────────────────────

/**
 * `shortcuts` —— 按**系统身份**读某个包的快捷方式（`pkg` / `user` 两个 extra）。
 *
 *  它回答的是「换个身份读不读得到钉住那一档」：宿主进程读出来的是 `动态 3 / 钉住 0`（真机实测微信），
 *  注入侧这一份跑在 `system_server`，与正式通道（`ModuleWire.ACTION_SHORTCUT_QUERY`）共用
 *  [ShortcutRead.read] —— 探针要验的正是真实那条路，不能自己另写一份读法。
 *
 *  用法见 `docs/runtime/debug-channel.md`：
 *  `am broadcast -a com.lookie.opluskey.PROBE --es op shortcuts --es pkg com.tencent.mm --ei user 0 -p <宿主包名>`
 */
internal fun ProbeChannel.shortcuts(
    intent: Intent,
    loader: ClassLoader,
) {
    val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
    if (pkg.isBlank()) {
        XLog.w("probe/shortcuts：没给 pkg（--es pkg <包名>）")
        return
    }
    val userId = intent.getIntExtra(EXTRA_USER, 0)
    //  与正式通道（`ShortcutRead.reply`）同一份实现：探针要验的正是真实那条路。
    val rows = ShortcutRead.pinned(loader, pkg, userId)
    XLog.w(
        "probe/shortcuts：$pkg（user=$userId）→ " +
            (
                rows?.let { r -> "${r.size} 条" + (if (r.isEmpty()) "" else "：" + r.joinToString("、") { (id, label) -> "$id=$label" }) }
                    ?: "读不到（见上面那行）"
            ),
    )
}
