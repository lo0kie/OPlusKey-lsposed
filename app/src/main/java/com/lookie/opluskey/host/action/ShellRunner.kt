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

        // 先占坑再起进程。`if (running.get() >= MAX) …` 那种**先查后加**在并发下会一起放行：两条
        //  会同时看到「没满」一起放行。占坑本身见 [acquireSlot]。
        val slot = acquireSlot()
        if (slot < 0) {
            XLog.w("已有 $MAX_CONCURRENT 条 shell 在跑（上限 $MAX_CONCURRENT），拒绝：$command")
            onFailure(CommandFailure.OVERLOADED)
            return
        }

        try {
            val proc = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            // 不给子进程留 stdin：我们从不用它，而有些命令会**等 stdin 到 EOF** 才肯退出
            //（`sh -c "cat"` 之类）—— 管道一直开着就是一条挂到超时的 shell，每次还漏一对管道
            //。
            runCatching { proc.outputStream.close() }
            // 后台线程等子进程结束
            Thread {
                // 无论走哪条出口都要把槽位放回去（finally 兜底）
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
                        // 注意：这条路直接 `return@Thread` 的话**不 destroy 子进程**：
                        //  进程漏在后台、读线程还挂着那对管道。与超时那条一样收掉（destroy 关管道 ⇒ 读到 EOF）。
                        XLog.w("等 shell 结束失败，杀掉它：${e.message}")
                        runCatching { proc.destroy() }
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
                    releaseSlot(slot)
                }
            }.apply { isDaemon = true }.start()
            // 判据级：这条是「动作到底有没有发出去」的唯一凭证，用 w（i/d 在 release 被编译期消除）
            XLog.w("shell 命令已在后台启动：$command")
        } catch (e: Throwable) {
            // 起进程这一步就失败 ⇒ 把刚占的槽位还回去（否则每被拒一次就永久吃掉一个名额）
            releaseSlot(slot)
            XLog.w("执行 shell 失败：$command", e)
            onFailure(CommandFailure.SHELL_REJECTED)
        }
    }

    /**
     * 占一个并发槽位。
     *
     * **优先走共享目录里的锁文件**：模块同时活在宿主主进程与设置页 `:ui` 两个进程里，各存一份
     * 计数器的话上限实际是两倍（两个进程各占一半额度 ⇒ 全机上限翻倍）。锁文件在
     * 两个进程共同的宿主私有目录里，`FileLock` 由内核持有 —— 进程死了锁自动释放，不会留死锁。
     *
     * 锁机制本身出问题时（目录建不出来 / 锁文件开不出来）退回**本进程计数**：绝不因为「锁坏了」
     * 把 shell 全拒掉，那比并发多几条严重得多。
     *
     * @return 槽位号；`-1` = 已满（调用方报 OVERLOADED）
     */
    private fun acquireSlot(): Int {
        val dir = runCatching { com.lookie.opluskey.core.config.ConfigStore.filesDir() }.getOrNull()
        if (dir == null || (!dir.isDirectory && !dir.mkdirs())) return acquireLocalSlot()
        var opened = 0
        for (i in 0 until MAX_CONCURRENT) {
            val raf = runCatching {
                java.io.RandomAccessFile(java.io.File(dir, "$SLOT_PREFIX$i"), "rw")
            }.getOrNull() ?: continue
            opened++
            // 本进程已经占着这个文件时 `tryLock` 会抛 `OverlappingFileLockException`（吞掉、试下一个）
            val locked = runCatching { raf.channel.tryLock() }.getOrNull()
            if (locked == null) {
                runCatching { raf.close() }
                continue
            }
            heldSlots[i] = raf
            return i
        }
        // 一个槽都没占上：`opened == 0` 说明锁文件根本开不出来（不是「满」）⇒ 退回本进程计数
        return if (opened == 0) acquireLocalSlot() else -1
    }

    /** 退回本进程计数时用的哨位（不是真槽号，见 [releaseSlot]）。 */
    private fun acquireLocalSlot(): Int {
        if (running.incrementAndGet() > MAX_CONCURRENT) {
            running.decrementAndGet()
            return -1
        }
        return LOCAL_SLOT
    }

    /** 放掉一个槽位（[slot] 为 [LOCAL_SLOT] 时放的是本进程计数）。 */
    private fun releaseSlot(slot: Int) {
        if (slot == LOCAL_SLOT) {
            running.decrementAndGet()
            return
        }
        heldSlots.remove(slot)?.let { runCatching { it.close() } }
    }

    /** 一条 shell 命令的等待上限（毫秒）—— 超时就杀掉并报失败。 */
    private const val SHELL_TIMEOUT_MS = 10_000L

    /** destroy() 之后留给子进程退出（并让读到 EOF）的宽限期，过了就 destroyForcibly()。 */
    private const val SHELL_KILL_GRACE_MS = 500L

    /** 子进程已退出后，等读线程收完剩余输出的上限 —— 只影响日志完整度，不判定成败。 */
    private const val SHELL_DRAIN_JOIN_MS = 500L

    /**
     * 同时挂着的 shell 子进程上限 —— **全机**（不是每进程，见 [acquireSlot]）。
     * 长按连续触发最密 100ms 一条，不设限会堆上百个进程 / 线程。
     */
    private const val MAX_CONCURRENT = 3

    /** 并发槽位锁文件的前缀（放在宿主私有目录里，两个进程共用）。 */
    private const val SLOT_PREFIX = "opluskey_shell_slot_"

    /** [acquireLocalSlot] 的哨位 —— 与真槽号（0..MAX-1）区分开。 */
    private const val LOCAL_SLOT = -2

    /** 退回本进程计数时用的计数器（见 [acquireLocalSlot]）。 */
    private val running = java.util.concurrent.atomic.AtomicInteger(0)

    /** 已占住的槽位：槽号 → 那个被锁住的锁文件句柄（关掉它 = 释放 `FileLock`）。 */
    private val heldSlots = java.util.concurrent.ConcurrentHashMap<Int, java.io.RandomAccessFile>()
}
