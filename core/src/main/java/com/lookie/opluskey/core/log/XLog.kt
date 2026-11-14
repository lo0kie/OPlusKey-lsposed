package com.lookie.opluskey.core.log

import android.util.Log
import com.lookie.opluskey.core.TokenSyntax
import com.lookie.opluskey.core.VERBOSE_LOG
import io.github.libxposed.api.XposedModule

/**
 * 日志统一出口。级别约定：
 *  - 判据级行（装没装上、手势判定、配置到没到）一律用 [w] —— [d] 在 release 里被
 *    `VERBOSE_LOG=false` **编译期消除（不分进程）**，debug 包里 `(system)` 侧的 `d`
 *    又会被 LSPosed 进程级日志级别整档丢掉（见 `docs/device/lsposed.md` 十五）。
 *  - debug + 宿主进程里 [d] 才真正可读；高频路径（如「非侧键放行」）留在 [d]，避免刷屏。
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
     * [d] 的 lambda 重载）。
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

    /** [lastStack] 最多留这么多字符 —— 一条栈通常一两千字符，这个上限够放下半个深链。 */
    private const val LAST_STACK_MAX = 8_000

    /**
     * 缓冲本体 —— **`ArrayDeque` + 一把锁**，不用并发队列。
     *
     *  `ConcurrentLinkedDeque.size()` 是 O(n) 遍历，而这里每条日志都要按它裁一次上界：
     *  满载 2000 条时，单条日志白跑两千次节点访问，而 `w` 这条路径就在按键热路径上。
     *  锁里只做「加一条 / 裁一条」，临界区是常数级；日志本来就是各个线程都在写，
     *  一把短锁比一条 O(n) 的遍历便宜得多（也不能用计数器近似 —— 见下面 [write] 里那段说明）。
     */
    private val buffer = ArrayDeque<String>(BUFFER_MAX)

    private val stamp =
        java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * 缓冲本体的**溢出计数** —— 从进程起来到现在，被 [BUFFER_MAX] 挤掉了多少条。
     *
     *  导出时写进文件头（见 `LogPull.header`）：一份长日志缺了开头时，得能看出是「没发生过」
     *  还是「发生过但被裁了」。原先这件事静默无痕，排查时最容易被误读。
     */
    private var dropped = 0L

    /**
     * 最近一次带异常的日志那一份**完整堆栈**（缓冲里只留「类型 + message」，见 [write]）。
     *
     *  另存一格而不是塞进缓冲：一条栈动辄几百行，进环形缓冲会把整段判据行挤掉；而真出异常时
     *  那一条栈恰恰是最需要看的。导出时附在文件末尾（见 `LogPull.header` / `ConfigTransfer`）。
     */
    @Volatile
    private var lastStack: String? = null

    /** 被挤掉的条数（见 [dropped]）。 */
    fun droppedLines(): Long = synchronized(buffer) { dropped }

    /** 最近一次异常的完整堆栈；`null` = 这个进程里还没出过带 `Throwable` 的日志。 */
    fun lastStack(): String? = lastStack

    /** 导出用：把缓冲拼成文本（**本进程**）。另外两个进程那份由 `wire/LogWire` 一问一答收上来。 */
    fun snapshot(): String = synchronized(buffer) { buffer.joinToString("\n") }

    /**
     * 缓冲里的每一行（拷贝一份快照）。
     *
     *  分片回给设置页时按**行**切：切在行中间会把时间戳劈开，拼回去也对不齐。
     *  拷贝而不是直接遍历 `buffer`（并发队列）：边读边有人在后面写，会把「这次导出的行数」
     *  与「实际拼出来的行数」弄成两个值。
     */
    fun lines(): List<String> = synchronized(buffer) { ArrayList(buffer) }

    /**
     * 日志的实际出口 —— 判据级日志**必须**走 [w]（或直接带 priority 走这里），`d` 在 release 里
     * 会被编译期消除、在 LSPosed 日志页里也整档看不到（AGENTS.md 二、1.2）。
     */
    fun write(
        priority: Int,
        msg: String,
        tr: Throwable?,
    ) {
        //  `Throwable.toString()` 只给类型名 + 自己的 message，而 `InvocationTargetException`
        //  的 message 恒为 null —— 反射调用失败的根因全在 cause 里，只存 toString 等于把
        //  「为什么失败」丢掉。这里按 cause 链拼类型 + message（不存完整堆栈，避免爆缓冲）。
        val text = "$msg${tr?.let { " | " + causeChain(it) } ?: ""}"
        // 三处出口（缓冲 / logcat / 框架日志）都是尽力而为、失败即丢，绝不影响调用方。
        // 用 runCatching 是明确的丢弃语义，不是 AGENTS.md 五之二 2 禁的「以保护代 catch」。
        runCatching {
            //  上界靠「先加再按数裁」，**不靠单独的计数器**：计数器与缓冲的增删不是同一次原子操作，
            //  并发下会偏低 / 偏高，环形缓冲于是少留几条、或多涨出去不受 [BUFFER_MAX] 约束
            //  （高频 `d` 路径尤其明显）。所以这里在**同一把锁**里加与裁 —— 计数与裁剪是一件事。
            synchronized(buffer) {
                buffer.addLast("${level(priority)} ${java.time.LocalDateTime.now().format(stamp)} $text")
                while (buffer.size > BUFFER_MAX) {
                    buffer.removeFirst()
                    //  裁掉的那一条要计数（见 [dropped]）：没有这个数，导出时看不出开头缺了一截
                    dropped += 1
                }
            }
            //  完整堆栈另存一格（见 [lastStack]）。取栈有成本，但这条路径只在真出异常时走一次，
            //  而缓冲里那一行只有「类型 + message」—— 没有这一份，栈就彻底没了。
            //
            //  **超长时不能只留头**：`Log.getStackTraceString` 把「Caused by / 根因」放在**最末**，
            //  从尾部 `take` 会把根因整段切掉，只剩一串自己的调用链 —— 那时日志看不出真正失败的原因。
            //  所以超长就留头留尾，中间标一句「已截断」。
            if (tr != null) lastStack = clipStack("$msg\n${Log.getStackTraceString(tr)}")
        }
        runCatching { Log.println(priority, TAG, text) }
        module?.let { m ->
            runCatching {
                if (tr == null) m.log(priority, TAG, msg) else m.log(priority, TAG, msg, tr)
            }
        }
    }

    /**
     * 把一段日志裁到 [LAST_STACK_MAX]：**留头留尾**，中间标一句截断说明。
     *
     *  `Log.getStackTraceString` 把 `Caused by` / 根因放在**最末** —— 从尾部 `take` 会把它整段切掉，
     * 只剩自己的调用链，那时用户交回来的日志看不出真正失败的原因。
     */
    private fun clipStack(text: String): String {
        if (text.length <= LAST_STACK_MAX) return text
        val marker = "\n…（中段已截断，原文 ${text.length} 字符）…\n"
        //  尾部分三成：根因链通常就在最后一两段 `Caused by` 里。
        val tail = LAST_STACK_MAX / 3
        return text.take(LAST_STACK_MAX - tail - marker.length) + marker + text.takeLast(tail)
    }

    /**
     * 执行一段可能碰 system_server 类的代码，任何失败都吞掉、只打日志。读静态字段会触发宿主类
     * 初始化，可能抛 `ExceptionInInitializerError` / `NoClassDefFoundError`，一旦顺着
     * `onPackageReady` 抛回框架就是开机循环。本函数把「模块出错」与「宿主出错」彻底隔开。
     *
     *  界面层那几处「同一件事的兜底」也走这一份（系统回调、写盘），别再各写一遍 try/catch：
     *  只是它们的失败含义不同，所以留了两个口子 —— [note] 换掉日志末尾那句说明（「不影响系统」
     *  对界面侧并不成立），[onError] 用来做本处特有的收尾（归零会话、弹一句提示）。
     *
     * @param what 出问题的是哪一环（日志里原样带上）
     * @param note 日志末尾那句说明 —— 说明的是「这次失败被怎么处理了」
     * @param onError 失败时本处额外的收尾；同样的异常已经打过日志，这里不必再打
     */
    fun guard(
        what: String,
        note: String = "不影响系统",
        onError: () -> Unit = {},
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (t: Throwable) {
            // Throwable 覆盖 Error（类初始化失败属于 Error）。文案写「失败」而非「安装失败」：
            // guard 也包着运行期动作，不能把排查方向带偏到「hook 没装上」。
            e("「$what」失败（已忽略，$note）：${t.javaClass.simpleName}", t)
            onError()
        }
    }

    /**
     * 命令正文的脱敏形式 —— 正文一律不进日志，只留「长度 + 指纹」。
     *
     * 为什么：命令正文可能内嵌 token / 密码（`curl -H "Authorization: ..."` 这类），而打这几条
     * 日志的地方都是 `w` 级 —— 正文既会进 logcat，也会进「导出日志」那份文件，等于把凭据抄了一份。
     *
     * 为什么保留长度与指纹：同一条命令两次执行的指纹一致，仍能把「同一处、前后两次」的日志对上；
     * 而「哪一步、哪条命令（名字）、退出码」这些判据不依赖正文本身。指纹取 `hashCode()` 的十六进制：
     * 够稳定、够便宜，不必为此引加密库（它只用于对照，不是凭据）。
     *
     * 空串单独给一个可读形式：打成 `<0字符 指纹0>` 看不出那是「空」。
     */
    fun redactCommand(command: String): String =
        if (command.isEmpty()) {
            "<空命令>"
        } else {
            "<${command.length}字符 指纹${Integer.toHexString(command.hashCode())}>"
        }

    /**
     * token 的脱敏形式 —— 只对**带正文的那几族**脱敏，其余原样返回。
     *
     *  `shell:` 后面跟的是整条命令全文、`intent:` 后面跟的是 action 串，都可能内嵌 token / 密码
     *  （理由同 [redactCommand]），所以只把冒号后面那段换成 [redactCommand] 的结果。
     *  其余 token（`keyevent:` / `app:` / `cmd:` / `window:` / `shortcut:` / `ringer:` / `oplus:` /
     *  `panel:` / `zoom:` / `power:menu` / `flashlight` / `none`）本身就是判据 —— 按下的是哪个键、
     *  开哪个 App、引哪条命令 —— 必须原样看得见，脱敏等于把判据抹掉。
     *
     *  判据只看冒号前那段前缀：认不出的前缀（`Action.parse` 会解析成 `none`、不执行）不在此列，
     *  那条正文由调用点自己用 [redactCommand] 收口。
     */
    fun redactToken(token: String): String {
        val prefix =
            when {
                token.startsWith(TokenSyntax.SHELL_PREFIX) -> TokenSyntax.SHELL_PREFIX
                token.startsWith(TokenSyntax.INTENT_PREFIX) -> TokenSyntax.INTENT_PREFIX
                else -> return token
            }
        return prefix + redactCommand(token.removePrefix(prefix))
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

/**
 * 异常的「类型名 + message」按 cause 链拼成一行（最多四层）。
 *
 *  为什么不直接 `toString()`：`InvocationTargetException` 的 message 恒为 null，反射调用失败的
 *  根因全在 cause 里 —— 只存 `toString()` 的导出日志会只剩下一个类型名，用户交回来的那份日志
 *  于是没有任何可用信息。也不存完整堆栈：环形缓冲只有固定条数，堆栈会把判据行挤掉。
 */
private fun causeChain(root: Throwable): String =
    buildString {
        var current: Throwable? = root
        var depth = 0
        while (current != null && depth < 4) {
            if (depth > 0) append(" <- ")
            append(current.javaClass.name)
            append(": ")
            append(current.message ?: "-")
            current = current.cause
            depth++
        }
    }
