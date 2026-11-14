package com.lookie.opluskey.ui.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.wire.registerLocalReceiver
import java.util.concurrent.ConcurrentHashMap

/**
 * 「导出日志」的**收集**那一半 —— 把另外两个进程的缓冲收上来，与本进程的合并成一份。
 *
 * ## 为什么要跨进程收
 *
 *  设置页那份 `XLog` 缓冲只有**本进程**的行（`:ui`）。真正的判据却散在另外两个进程：
 *  `system`（接管点装没装、按键认没认领）与 `host`（收到手势没有、动作执行了没有）。
 *  用户交回来的日志常常「一条按键行都没有」，上一次排查侧键就是这样卡住的。
 *
 * ## 一问多答，收固定时长
 *
 *  发出 [ModuleWire.ACTION_LOG_PULL] 后等 [WAIT_MS]，期间把 [ModuleWire.ACTION_LOG_CHUNK]
 *  攒起来；到点就不等了。**不按「总数收齐」判断结束**：某个进程没起来时永远等不到，
 *  而导出是一次用户动作，宁可少一节也不要卡住界面。
 *
 *  等待放在 IO 线程上（调用方在 `ConfigTransfer.fileScope` 里），广播回调仍在主线程 —— 不冲突。
 */
internal object LogPull {
    /** 等另外两个进程回包的上限（毫秒）。实测两片以内，本地广播几十毫秒就回来了。 */
    private const val WAIT_MS = 700L

    /**
     * 一次收集的结果 —— 不只是正文，还有「这一侧是哪次构建、缓冲里几行」。
     *
     *  后两项进导出文件头（见 `LogHeader`）：三个进程的版本可以不一致（换包没重启时
     *  `system_server` 里还是旧的），而「某进程没起来」与「它在跑但没日志」也得分开 ——
     *  前者没回包，后者回了一片空的。
     */
    class Pulled(
        /** 进程标识 → 正文。 */
        val text: Map<String, String>,
        /** 进程标识 → 它报的 `ModuleInfo.BUILD_ID`（老版本注入侧不带这一位时缺项）。 */
        val build: Map<String, String>,
        /** 进程标识 → 它缓冲里一共几行。 */
        val lines: Map<String, Int>,
    ) {
        /** 回过包的进程（含「在跑但没日志」那种：它也发了一片空的）。 */
        val answered: Set<String> get() = text.keys
    }

    /**
     * 收另外两个进程的日志。
     *
     * @return 三个进程各自的正文 / 构建标识 / 行数；没人应答就是空表
     */
    fun collect(
        context: Context,
        pkg: String,
    ): Pulled {
        // 序号 → 那一片的正文；按进程分组。用并发容器：回调在主线程、读取在这次调用的线程上。
        val chunks = ConcurrentHashMap<String, MutableMap<Int, String>>()
        // 发送方声明的总片数：只用来**核对完整性**，不拿它当结束条件（见类注释）。
        val totals = ConcurrentHashMap<String, Int>()
        //  这两张表同样是并发容器：每片都带（只有第一次写入生效），后写的覆盖同值无妨
        val builds = ConcurrentHashMap<String, String>()
        val counts = ConcurrentHashMap<String, Int>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != ModuleWire.ACTION_LOG_CHUNK) return
                    val proc = intent.getStringExtra(ModuleWire.EXTRA_LOG_PROC) ?: return
                    val text = intent.getStringExtra(ModuleWire.EXTRA_LOG_TEXT) ?: return
                    val index = intent.getIntExtra(ModuleWire.EXTRA_LOG_INDEX, 0)
                    val total = intent.getIntExtra(ModuleWire.EXTRA_LOG_TOTAL, -1)
                    if (total > 0) totals[proc] = total
                    //  构建标识与行数由**每一片**都带（见 `LogWire.reply`），这里后到覆盖先到 —— 同值
                    intent.getStringExtra(ModuleWire.EXTRA_LOG_BUILD)?.let { builds[proc] = it }
                    val lines = intent.getIntExtra(ModuleWire.EXTRA_LOG_LINES, -1)
                    if (lines >= 0) counts[proc] = lines
                    runCatching {
                        chunks.getOrPut(proc) { ConcurrentHashMap() }[index] = text
                    }.onFailure { XLog.w("导出日志：收到 $proc 的分片但存不下（忽略）：${it.javaClass.simpleName}") }
                }
            }
        // 发送方是宿主主进程（同 uid）与 system_server（uid 1000）—— 后者对 NOT_EXPORTED
        // 接收器照样可达，与 `CollectKeyDialog` 收按键上报同一条口径。
        val registered =
            context.registerLocalReceiver("导出日志（这次只能导本进程的）", receiver, ModuleWire.ACTION_LOG_CHUNK)
        PageWire.send(context, pkg, Intent(ModuleWire.ACTION_LOG_PULL))
        //  收集窗口用 try/finally 兜住：`Thread.sleep` 被中断时抛的是 InterruptedException，
        //  以前那条路会跳过注销 —— 接收器与 `chunks` 就永久留在进程里。
        try {
            Thread.sleep(WAIT_MS)
        } catch (e: InterruptedException) {
            XLog.w("导出日志：收集被中断，提前收尾")
            Thread.currentThread().interrupt()
        } finally {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }

        val out = LinkedHashMap<String, String>()
        for ((proc, parts) in chunks) {
            val expected = totals[proc] ?: -1
            if (expected > 0) {
                val missing = (0 until expected).filter { it !in parts }
                //  少片必须看得见：日志是本模块唯一自救通道，缺几段又看不出来的日志比没有更误导。
                if (missing.isNotEmpty()) {
                    XLog.w("导出日志：$proc 只收到 ${parts.size}/$expected 片，缺编号 $missing —— 这一节不完整")
                }
            }
            out[proc] = parts.toSortedMap().values.joinToString("\n")
        }
        for (proc in totals.keys) {
            if (chunks[proc] == null) XLog.w("导出日志：$proc 声明了 ${totals[proc]} 片，但一片都没收到")
        }
        XLog.w("导出日志：收到 ${out.size} 个进程的回包（${out.map { "${it.key}=${it.value.lines().size} 行" }}）")
        return Pulled(out, builds, counts)
    }

    /**
     * 「导出日志」那一行副标题要的那一句：缓冲区现在多少行、来自几个进程。
     *
     *  与 [collect] 走同一条路（真把几份收上来再数），所以**慢** —— 最多等 `WAIT_MS`。
     *  调用方必须放在 IO 上，且只在菜单打开那一刻取一次，不能进组合期。
     *
     * @return `null` = 连本进程都是空的（这时候没什么可导的），那一行就不带副标题
     */
    fun summary(context: Context): String? {
        val remote = collect(context, HostApp.PACKAGE).text
        val local = XLog.lines().count { it.isNotBlank() }
        val remoteLines = remote.values.sumOf { text -> text.lines().count { it.isNotBlank() } }
        val lines = local + remoteLines
        if (lines <= 0) return null
        return I18n.format(Strings.MENU_LOG_BUFFER, lines)
    }

    /**
     * 把本进程与收到的几份**按时间合并**成一节，每行前面标上进程名。
     *
     *  为什么不分成几个小节各自列出：排查时看的是**时序**（按下 → 判定 → 转交 → 执行），
     *  分节之后要在几段之间来回跳，最容易漏掉的正是「另一头压根没动」这种。
     *  每行的时间戳是 `yyyy-MM-dd HH:mm:ss.SSS`，首尾相接正好 23 个字符，按它排序即可。
     *  带年份：导出的几份日志可能跨年，只取 `MM-dd` 会把次年 1 月的行排到最前面。
     *
     * @param local 本进程的 [XLog.snapshot]
     * @param remote [collect] 的结果
     */
    fun compose(
        local: String,
        remote: Map<String, String>,
    ): String {
        val all = ArrayList<Pair<String, String>>()
        local.lines().forEach { if (it.isNotBlank()) all.add(LOCAL_PROC to it) }
        remote.forEach { (proc, text) ->
            text.lines().forEach { if (it.isNotBlank()) all.add(proc to it) }
        }
        if (all.isEmpty()) return ""
        // stable sort：同一毫秒的行保持原始先后（各进程内部本来就是有序的）
        return all
            .sortedBy { (_, line) -> timeKey(line) }
            .joinToString("\n") { (proc, line) -> "[$proc] $line" }
    }

    /** 设置页自己那一份的进程标识。 */
    private const val LOCAL_PROC = "ui"

    /** 取排序键：`W 2026-10-18 22:22:41.955 msg` → `2026-10-18 22:22:41.955`。 */
    private fun timeKey(line: String): String = line.substringAfter(' ', "").take(23)
}
