package com.lookie.opluskey.core.wire

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog

/**
 * 「导出日志」的**回包**那一半 —— 任一进程收到 [ModuleWire.ACTION_LOG_PULL]，就把自己的
 * 环形缓冲切片发回设置页。
 *
 * ## 为什么需要它
 *
 *  设置页那格「导出」原先只导**本进程**的缓冲（`XLog.snapshot`），而判据行散在三个进程：
 *  `system`（接管与按键判定）、`host`（抓取与执行）、`ui`（设置页自己）。用户交回来的日志
 *  因此常常一条按键行都没有 —— 上一次排查「侧键只在电源+侧键时触发」就卡在这里，最后不得不
 *  让对方去 LSPosed 管理器导全进程日志。
 *
 * ## 为什么要分片
 *
 *  缓冲上限 2000 行、几百 KB 量级，一次广播全塞进去会撞 binder 事务上限（发送直接失败，
 *  而且失败是静默的）。所以按**行**切成 [CHUNK_CHARS] 字符左右的片，带上序号与总数，
 *  收方按序号拼回去。单行超过一片时硬切 —— 正常日志不会有这种行，真有时也不能整片丢。
 */
object LogWire {
    /** 一片大概多大 —— 十来 KB，远低于事务上限，也不会切出几百次广播。 */
    private const val CHUNK_CHARS = 12_000

    /**
     * 把本进程的缓冲切好、发回设置页。
     *
     * @param pkg 设置页所在的包名（由调用方传，本层不认识「宿主包名」是谁）
     * @param proc 本进程的标识（`system` / `host`），收方据此分节
     */
    fun reply(
        context: Context,
        pkg: String,
        proc: String,
    ) {
        val chunks = chunk(XLog.lines())
        chunks.forEachIndexed { index, text ->
            PageWire.send(
                context,
                pkg,
                Intent(ModuleWire.ACTION_LOG_CHUNK)
                    .putExtra(ModuleWire.EXTRA_LOG_PROC, proc)
                    .putExtra(ModuleWire.EXTRA_LOG_INDEX, index)
                    .putExtra(ModuleWire.EXTRA_LOG_TOTAL, chunks.size)
                    .putExtra(ModuleWire.EXTRA_LOG_TEXT, text),
            )
        }
        // 一条 W：导出这件事本身要留痕（收方拿到几片、发方发了什么，对不上时先看这两行）
        XLog.w("导出日志：已回 $proc 的缓冲 ${chunks.size} 片")
    }

    /** 按行累积到 [CHUNK_CHARS] 就切一片；单行本身就超长时硬切成多片。 */
    private fun chunk(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (line in lines) {
            var rest = line
            while (rest.length > CHUNK_CHARS) {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                out.add(rest.take(CHUNK_CHARS))
                rest = rest.substring(CHUNK_CHARS)
            }
            if (sb.length + rest.length + 1 > CHUNK_CHARS) {
                out.add(sb.toString())
                sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(rest)
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
