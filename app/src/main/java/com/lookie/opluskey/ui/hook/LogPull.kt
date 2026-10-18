package com.lookie.opluskey.ui.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
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
     * 收另外两个进程的日志。
     *
     * @return 进程标识（`system` / `host`）→ 该进程的日志正文；没人应答就是空表
     */
    fun collect(
        context: Context,
        pkg: String,
    ): Map<String, String> {
        // 序号 → 那一片的正文；按进程分组。用并发容器：回调在主线程、读取在这次调用的线程上。
        val chunks = ConcurrentHashMap<String, MutableMap<Int, String>>()
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
                    runCatching {
                        chunks.getOrPut(proc) { ConcurrentHashMap() }[index] = text
                    }.onFailure { XLog.w("导出日志：收到 $proc 的分片但存不下（忽略）：${it.javaClass.simpleName}") }
                }
            }
        // 发送方是宿主主进程（同 uid）与 system_server（uid 1000）—— 后者对 NOT_EXPORTED
        // 接收器照样可达，与 `CollectKeyDialog` 收按键上报同一条口径。
        val registered =
            runCatching {
                context.registerReceiver(
                    receiver,
                    IntentFilter(ModuleWire.ACTION_LOG_CHUNK),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }.onFailure { XLog.w("导出日志：接收器没注册上（这次只能导本进程的）", it) }
                .isSuccess
        PageWire.send(context, pkg, Intent(ModuleWire.ACTION_LOG_PULL))
        Thread.sleep(WAIT_MS)
        if (registered) runCatching { context.unregisterReceiver(receiver) }

        val out = LinkedHashMap<String, String>()
        for ((proc, parts) in chunks) {
            out[proc] = parts.toSortedMap().values.joinToString("\n")
        }
        XLog.w("导出日志：收到 ${out.size} 个进程的回包（${out.map { "${it.key}=${it.value.lines().size} 行" }}）")
        return out
    }

    /**
     * 把本进程与收到的几份**按时间合并**成一节，每行前面标上进程名。
     *
     *  为什么不分成几个小节各自列出：排查时看的是**时序**（按下 → 判定 → 转交 → 执行），
     *  分节之后要在几段之间来回跳，最容易漏掉的正是「另一头压根没动」这种。
     *  每行的时间戳是 `MM-dd HH:mm:ss.SSS`，首尾相接正好 18 个字符，按它排序即可。
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

    /** 取排序键：`W 10-18 22:22:41.955 msg` → `10-18 22:22:41.955`。 */
    private fun timeKey(line: String): String = line.substringAfter(' ', "").take(18)
}
