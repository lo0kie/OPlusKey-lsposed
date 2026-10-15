package com.lookie.opluskey.core.log

import android.util.Log
import com.lookie.opluskey.VERBOSE_LOG
import io.github.libxposed.api.XposedModule

/**
 * 日志统一出口。级别约定：
 *  - 判据级行（装没装上、手势判定、配置到没到）一律用 [w] —— `[i]`/`[d]` 在 release 里被
 *    `VERBOSE_LOG=false` **编译期消除（不分进程）**，debug 包里 `(system)` 侧的 `i`/`d`
 *    又会被 LSPosed 进程级日志级别整档丢掉（见 `docs/device-findings.md` 十五）。
 *  - debug + 宿主进程里 `i`/`d` 才真正可读；高频路径（如「非侧键放行」）留在 `d`，避免刷屏。
 */
object XLog {
    /** LSPosed 日志页里按这个 TAG 过滤。 */
    private const val TAG = "OplusKey"

    @Volatile
    private var module: XposedModule? = null

    fun bind(module: XposedModule) {
        this.module = module
    }

    /**
     * 详细日志（仅 debug 构建）。必须 `inline`：只有内联进调用点，`if (VERBOSE_LOG)` 才能在
     * 编译期把整段连同字符串字面量一起消掉。参数不是函数类型会触发 `NOTHING_TO_INLINE`，
     * 内联收益在死代码消除而非调用开销，故显式压掉。
     *
     * 注意：这个重载**只管得住字面量**。传进来的 `String` 在调用点就已经求值了 ——
     * release 下 `if (VERBOSE_LOG)` 把函数体消掉，但拼串照跑。参数是「拼出来的」时用
     * [d] 的 lambda 重载（或 [i] 的）。
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun d(msg: String) {
        if (VERBOSE_LOG) write(Log.DEBUG, msg, null)
    }

    /**
     * [d] 的**惰性**版：只有真要打这一行时才求值。
     *
     * 用在「参数本身要花点钱」的地方 —— 典型是把一个对象摊平成字符串（Join / 遍历 Bundle /
     * `flattenToShortString()`），而那条路径每次按键、每条消息都走。
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun d(msg: () -> String) {
        if (VERBOSE_LOG) write(Log.DEBUG, msg(), null)
    }

    /** 详细日志（仅 debug 构建）。语义同 [d]。 */
    @Suppress("NOTHING_TO_INLINE")
    inline fun i(msg: String) {
        if (VERBOSE_LOG) write(Log.INFO, msg, null)
    }

    /** [i] 的惰性版；取舍同 [d] 的 lambda 重载。 */
    @Suppress("NOTHING_TO_INLINE")
    inline fun i(msg: () -> String) {
        if (VERBOSE_LOG) write(Log.INFO, msg(), null)
    }

    fun w(
        msg: String,
        tr: Throwable? = null,
    ) = write(Log.WARN, msg, tr)

    fun e(
        msg: String,
        tr: Throwable? = null,
    ) = write(Log.ERROR, msg, tr)

    /** 导出用的内存环形缓冲：只留最近 [BUFFER_MAX] 条（**本进程**的日志）。 */
    private const val BUFFER_MAX = 2000

    private val buffer = java.util.concurrent.ConcurrentLinkedDeque<String>()

    /** 缓冲条数 —— 用计数而不是 `buffer.size`（那是 O(n) 全表遍历，而每条日志都要判一次）。 */
    private val buffered =
        java.util.concurrent.atomic
            .AtomicInteger()

    private val stamp =
        java.time.format.DateTimeFormatter
            .ofPattern("MM-dd HH:mm:ss.SSS")

    /** 导出用：把缓冲拼成文本（宿主进程侧；注入侧在 LSPosed 的日志页里）。 */
    fun snapshot(): String = buffer.joinToString("\n")

    /** 日志的实际出口；`@PublishedApi internal` 是为了让 [d] / [i] 这两个内联函数能调用它。 */
    @PublishedApi
    internal fun write(
        priority: Int,
        msg: String,
        tr: Throwable?,
    ) {
        val text = "$msg${tr?.let { " | " + it } ?: ""}"
        // 三处出口（缓冲 / logcat / 框架日志）都是尽力而为、失败即丢，绝不影响调用方。
        // 用 runCatching 是明确的丢弃语义，不是 AGENTS.md 五之二 2 禁的「以保护代 catch」。
        runCatching {
            buffer.addLast("${level(priority)} ${java.time.LocalDateTime.now().format(stamp)} $text")
            if (buffered.incrementAndGet() > BUFFER_MAX) {
                buffer.pollFirst()
                buffered.decrementAndGet()
            }
        }
        runCatching { Log.println(priority, TAG, text) }
        module?.let { m ->
            runCatching {
                if (tr == null) m.log(priority, TAG, msg) else m.log(priority, TAG, msg, tr)
            }
        }
    }

    /**
     * 执行一段可能碰 system_server 类的代码，任何失败都吞掉、只打日志。读静态字段会触发宿主类
     * 初始化，可能抛 `ExceptionInInitializerError` / `NoClassDefFoundError`，一旦顺着
     * `onPackageReady` 抛回框架就是开机循环。本函数把「模块出错」与「宿主出错」彻底隔开。
     */
    fun guard(
        what: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Throwable) {
            // Throwable 覆盖 Error（类初始化失败属于 Error）。文案写「失败」而非「安装失败」：
            // guard 也包着运行期动作，不能把排查方向带偏到「hook 没装上」。
            e("「$what」失败（已忽略，不影响系统）：${e.javaClass.simpleName}", e)
        }
    }

    /** 缓冲里那一列级别（logcat 的数值档 → 一个字母）。 */
    private fun level(priority: Int): String =
        when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
}
