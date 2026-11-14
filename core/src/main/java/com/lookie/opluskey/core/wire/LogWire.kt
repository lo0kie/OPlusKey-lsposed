package com.lookie.opluskey.core.wire

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.ModuleInfo
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
 *  收方按序号拼回去。单行超过一片时**它独占一片**、不切在行中间 —— 收方是按行重新拼的，
 *  切在行中间会平白多出一个换行，把一条日志劈成两条。
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
        val lines = XLog.lines()
        val chunks = chunk(lines)
        chunks.forEachIndexed { index, text ->
            PageWire.send(
                context,
                pkg,
                Intent(ModuleWire.ACTION_LOG_CHUNK)
                    .putExtra(ModuleWire.EXTRA_LOG_PROC, proc)
                    .putExtra(ModuleWire.EXTRA_LOG_INDEX, index)
                    .putExtra(ModuleWire.EXTRA_LOG_TOTAL, chunks.size)
                    .putExtra(ModuleWire.EXTRA_LOG_TEXT, text)
                    //  这两项每片都带（只有第一片会被采纳）：收方要的是「这一侧是哪次构建、
                    //  有多少行」，而不是逐片的账 —— 分开传反而要在收方再拼一次。
                    .putExtra(ModuleWire.EXTRA_LOG_BUILD, ModuleInfo.BUILD_ID)
                    .putExtra(ModuleWire.EXTRA_LOG_LINES, lines.size),
            )
        }
        // 一条 W：导出这件事本身要留痕（收方拿到几片、发方发了什么，对不上时先看这两行）
        XLog.w("导出日志：已回 $proc 的缓冲 ${chunks.size} 片（${lines.size} 行 / ${ModuleInfo.BUILD_ID}）")
    }

    /** 按行累积到 [CHUNK_CHARS] 就切一片；单行本身就超长时让它独占一片，绝不切在行中间。 */
    private fun chunk(lines: List<String>): List<String> {
        //  **空缓冲也要回一片空的**：一片都不发的话，收方只能按「收到几片」判，
        //  分不出「这个进程在跑但没日志」与「这个进程根本没起来」（排查时会误判某一段缺失）。
        if (lines.isEmpty()) return listOf("")
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (line in lines) {
            //  单行超上限：把它单独成片。收方拿到的每一片都从行首开始、到行尾结束，
            //  拼回去时不会凭空多出一个换行。
            if (line.length > CHUNK_CHARS) {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                out.add(line)
                continue
            }
            //  空缓冲不切：`0 + line + 1` 也可能超上限（单行正好 CHUNK_CHARS 时），
            //  那时会先发一条空分片出去。切之前必须确认这一段真有内容。
            if (sb.isNotEmpty() && sb.length + line.length + 1 > CHUNK_CHARS) {
                out.add(sb.toString())
                sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
