package com.lookie.opluskey.hook

import com.lookie.opluskey.XLog

/** 执行一条 shell 命令（`shell:` 动作）。 */
internal object ShellRunner {

    /**
     * 跑一条命令。
     *
     * @param onFailure 失败回调（后台线程里调 —— 实现方自己负责切线程）。
     *   两类时机：`ProcessBuilder.start()` 直接失败；子进程退出码非 0；等待超时。
     */
    fun exec(command: String, onFailure: (CommandFailure) -> Unit = {}) {
        if (command.isBlank()) return

        // 超过并发上限直接拒绝：这条在按键路径上，宁可使这一次动作失败也不能把进程堆爆
        if (running.get() >= MAX_CONCURRENT) {
            XLog.w("已有 ${running.get()} 条 shell 在跑（上限 $MAX_CONCURRENT），拒绝：$command")
            onFailure(CommandFailure.OVERLOADED)
            return
        }

        try {
            val proc = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            // 后台线程把输出读干、等子进程结束
            running.incrementAndGet()
            Thread {
                // 无论走哪条出口都要把计数放回去（finally 兜底）
                try {
                    runCatching {
                        proc.inputStream.bufferedReader().forEachLine { line -> XLog.d("sh: $line") }
                    }.onFailure { XLog.d("读 shell 输出失败：${it.message}") }
                    // 必须带超时：有些命令在这个身份下会直接挂住（真机实测 `cmd audio set-ringer-mode` 就是）；
                    // 无超时的 `waitFor()` 每按一次就永久泄一个线程 + 一对管道，反复按也拦不住。
                    val done = try {
                        proc.waitFor(SHELL_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (e: Throwable) {
                        XLog.d("等 shell 结束失败：${e.message}")
                        return@Thread
                    }
                    if (!done) {
                        XLog.w("shell 超过 ${SHELL_TIMEOUT_MS}ms 没结束，杀掉它：$command")
                        try {
                            proc.destroy()
                        } catch (e: Throwable) {
                            XLog.d("杀 shell 进程失败（忽略）：${e.message}")
                        }
                        onFailure(CommandFailure.SHELL_TIMEOUT)
                        return@Thread
                    }
                    // 只有非 0 才算失败：0 是常规路径，不该有任何提示。
                    // 合并成一条 `a; b` 的步骤取的是最后一条的退出码（`sh` 的规矩）。
                    if (proc.exitValue() != 0) {
                        XLog.w("shell 退出码 ${proc.exitValue()}：$command")
                        onFailure(CommandFailure.ofShellExit(proc.exitValue()))
                    }
                } finally {
                    running.decrementAndGet()
                }
            }.apply { isDaemon = true }.start()
            XLog.i("shell 命令已在后台启动：$command")
        } catch (e: Throwable) {
            XLog.w("执行 shell 失败：$command", e)
            onFailure(CommandFailure.SHELL_REJECTED)
        }
    }

    /** 一条 shell 命令的等待上限（毫秒）—— 超时就杀掉并报失败。 */
    private const val SHELL_TIMEOUT_MS = 10_000L

    /** 同时挂着的 shell 子进程上限：长按连续触发最密 100ms 一条，不设限会堆上百个进程 / 线程。 */
    private const val MAX_CONCURRENT = 3

    private val running = java.util.concurrent.atomic.AtomicInteger(0)
}
