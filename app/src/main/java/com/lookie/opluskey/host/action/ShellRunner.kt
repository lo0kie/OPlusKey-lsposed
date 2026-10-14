package com.lookie.opluskey.host.action

import com.lookie.opluskey.core.log.XLog

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
            // 后台线程等子进程结束
            running.incrementAndGet()
            Thread {
                // 无论走哪条出口都要把计数放回去（finally 兜底）
                var reader: Thread? = null
                try {
                    // 读输出必须另起一条线程与等待**并行**：`forEachLine` 要读到 EOF 才返回，
                    // 而 logcat 不带 -d / 常驻进程 / `tail -f` 这类命令根本不关 stdout ——
                    // 一旦先读后等，这个线程就永久卡在 read 上，下面的 waitFor(超时) 与 finally
                    // 的计数回收都到不了：累计 MAX_CONCURRENT 条之后所有 shell 动作永久 OVERLOADED，
                    // 每挂一条还泄一个线程 + 一对管道。
                    reader = Thread {
                        runCatching {
                            proc.inputStream.bufferedReader().forEachLine { line -> XLog.d("sh: $line") }
                        }.onFailure { XLog.d("读 shell 输出失败：${it.message}") }
                    }.apply { isDaemon = true; start() }

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
                        // 子进程可能不理会 SIGTERM：宽限期内没退就强杀，保证管道被关掉、
                        // 读线程能收到 EOF 退出（否则每次超时都永久泄一个读线程）。
                        val gone = runCatching {
                            proc.waitFor(SHELL_KILL_GRACE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                        }.getOrDefault(false)
                        if (!gone) {
                            runCatching { proc.destroyForcibly() }
                                .onFailure { XLog.d("强杀 shell 进程失败（忽略）：${it.message}") }
                        }
                        onFailure(CommandFailure.SHELL_TIMEOUT)
                        return@Thread
                    }
                    // 进程已退出，但 stdout 可能被子进程继承后仍开着（`sh -c "... &"`）：
                    // 只给读线程一个有限宽限把剩余输出收完，不用它来判定成败。
                    runCatching { reader.join(SHELL_DRAIN_JOIN_MS) }
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
            // 判据级：这条是「动作到底有没有发出去」的唯一凭证，用 w（i/d 在 release 被编译期消除）
            XLog.w("shell 命令已在后台启动：$command")
        } catch (e: Throwable) {
            XLog.w("执行 shell 失败：$command", e)
            onFailure(CommandFailure.SHELL_REJECTED)
        }
    }

    /** 一条 shell 命令的等待上限（毫秒）—— 超时就杀掉并报失败。 */
    private const val SHELL_TIMEOUT_MS = 10_000L

    /** destroy() 之后留给子进程退出（并让读到 EOF）的宽限期，过了就 destroyForcibly()。 */
    private const val SHELL_KILL_GRACE_MS = 500L

    /** 子进程已退出后，等读线程收完剩余输出的上限 —— 只影响日志完整度，不判定成败。 */
    private const val SHELL_DRAIN_JOIN_MS = 500L

    /** 同时挂着的 shell 子进程上限：长按连续触发最密 100ms 一条，不设限会堆上百个进程 / 线程。 */
    private const val MAX_CONCURRENT = 3

    private val running = java.util.concurrent.atomic.AtomicInteger(0)
}
