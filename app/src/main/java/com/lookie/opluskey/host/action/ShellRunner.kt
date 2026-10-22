package com.lookie.opluskey.host.action

import com.lookie.opluskey.core.log.XLog
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 执行一条 shell 命令（`shell:` 动作）。 */
internal object ShellRunner {
    /**
     * 跑一条命令。
     *
     * 起进程用哪个解释器见 [startProcess] —— **优先 `su`**，没有 root 才退回 `sh`。
     *
     * 本函数**立刻返回**：占坑、起进程、等结束全在 [runnerPool] 上做（理由见 [execBlocking]）。
     *
     * @param onFailure 失败回调（后台线程里调 —— 实现方自己负责切线程）。
     *   两类时机：`ProcessBuilder.start()` 直接失败；子进程退出码非 0；等待超时。
     */
    fun exec(
        command: String,
        onFailure: (CommandFailure) -> Unit = {},
    ) {
        if (command.isBlank()) {
            //  空命令不该走到这里（`shell:` 那一支解析时已经掉空值）；真走到就是配置里有条空的，
            //  留一行而不是无声无息地什么都不做
            XLog.w("shell：命令为空，跳过")
            return
        }
        //  整条命令都挪进池子：`acquireSlot` 是文件锁 + 目录 IO，`startProcess` 是 fork/exec
        //  （首次 `su` 会停在等授权，可能几秒）。这两步原先在**调用线程**上同步做，而调用链是
        //  宿主主线程的广播回调（`GestureReceiver` 的 Handler 建在主 Looper）—— 一条慢命令
        //  就能把整个宿主卡住。
        runnerPool.execute { execBlocking(command, onFailure) }
    }

    /** [exec] 的实现体 —— 跑在 [runnerPool] 上，所以这里允许阻塞。 */
    private fun execBlocking(
        command: String,
        onFailure: (CommandFailure) -> Unit,
    ) {
        // 先占坑再起进程。`if (running.get() >= MAX) …` 那种**先查后加**在并发下会一起放行：两条
        //  会同时看到「没满」一起放行。占坑本身见 [acquireSlot]。
        val slot = acquireSlot()
        // 判据只能是 [NO_SLOT]：退回本进程计数时给的是 [LOCAL_SLOT]（-2），
        //  拿 `< 0` 去判会把它当成「已满」—— 于是锁文件建不出来的机器上所有 shell 全被拒，
        //  而且那一次 `running` 已经加过、走不到释放（连续三次之后彻底不再放行）。
        if (slot == NO_SLOT) {
            XLog.w("已有 $SHELL_MAX_CONCURRENT 条 shell 在跑（上限 $SHELL_MAX_CONCURRENT），拒绝：${XLog.redactCommand(command)}")
            onFailure(CommandFailure.OVERLOADED)
            return
        }

        try {
            val (proc, via) = startProcess(command)
            // 不给子进程留 stdin：我们从不用它，而有些命令会**等 stdin 到 EOF** 才肯退出
            // （`sh -c "cat"` 之类）—— 管道一直开着就是一条挂到超时的 shell，每次还漏一对管道。
            runCatching { proc.outputStream.close() }
            // 判据级：这条是「动作到底有没有发出去」的唯一凭证，用 w（i/d 在 release 被编译期消除）。
            //  必须在等结束**之前**打：命令挂到超时的那一档，用户要知道它确实起过。
            XLog.w("shell 命令已在后台启动（走的是 $via）：${XLog.redactCommand(command)}")

            awaitProcess(proc, via, command, onFailure)
        } catch (e: Throwable) {
            // 起进程这一步就失败：槽位由下面的 finally 还（只此一处释放，避免两条路径各记一次）
            XLog.w("执行 shell 失败：${XLog.redactCommand(command)}", e)
            // 两个解释器**都起不来**时按 errno 分档（`ProcessBuilder.start()` 抛的 `error=N`）：
            //  · 2  = ENOENT —— 没有 root：KernelSU 对未授权应用直接让 `/system/bin/su` **不可见**
            //  · 13 = EACCES —— `platform_app` 域没有 `/system/bin/sh` 的执行权
            // 两种都是「这台机器上现在没有可用的 shell」，该提示去授权 root，而不是「系统不让执行
            // 这条命令」那种把人往 SELinux 方向带的话。
            val errno =
                ERRNO
                    .find(e.message.orEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            launcher = null
            onFailure(
                if (errno == 2 || errno == 13) CommandFailure.SHELL_NO_ROOT else CommandFailure.SHELL_REJECTED,
            )
        } finally {
            // 唯一的释放点：占坑成功之后，无论从哪条出口离开都还回去。
            //  （原先在「起进程失败」与「等待结束」两处各释放一次，任何一条新出口漏写就是永久少一格。）
            releaseSlot(slot)
        }
    }

    /**
     * 等一个已起来的子进程结束，并把输出收干净。
     *
     * **不释放槽位** —— 那条账归 [execBlocking] 的 `finally` 一处记。
     */
    private fun awaitProcess(
        proc: Process,
        via: String,
        command: String,
        onFailure: (CommandFailure) -> Unit,
    ) {
        // 读输出必须另起一条线程与等待**并行**：`forEachLine` 要读到 EOF 才返回，
        // 而 logcat 不带 -d / 常驻进程 / `tail -f` 这类命令根本不关 stdout ——
        // 一旦先读后等，这个线程就永久卡在 read 上，下面的 waitFor(超时) 与收尾都到不了：
        // 累计 SHELL_MAX_CONCURRENT 条之后所有 shell 动作永久 OVERLOADED，每挂一条还泄一个线程 + 一对管道。
        //
        // 顺带**边读边认**权限拒绝：`am` 被拒时那句 `Permission Denial: …` 在第 4 行，
        // 后面跟着十几行 `at android.…` 栈帧 —— 事后扫「输出尾巴」会把它顶掉（真机踩过：
        // 留 12 行时正好全是栈帧，判据落空，仍然报「命令没跑成功」）。
        val denied =
            java.util.concurrent.atomic
                .AtomicReference<String>()
        // 输出的**尾部**（最多 [SHELL_TAIL_LINES] 行）：只在失败时以 `w` 回放，
        //  理由见那个常量的注释。读写都在读线程里，主线程在 `reader.get` 之后才拿。
        val tail = java.util.Collections.synchronizedList(ArrayDeque<String>())
        val reader = startOutputReader(proc, denied, tail)

        try {
            // 必须带超时：有些命令在这个身份下会直接挂住（真机实测 `cmd audio set-ringer-mode` 就是）；
            // 无超时的 `waitFor()` 每按一次就永久泄一个线程 + 一对管道，反复按也拦不住。
            val done =
                try {
                    proc.waitFor(SHELL_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (e: Throwable) {
                    // 这条路直接 `return` 的话**不 destroy 子进程**：进程漏在后台、读线程还挂着
                    //  那对管道。与超时那条一样收掉（destroy 关管道 ⇒ 读到 EOF），并报一句给用户。
                    XLog.w("等 shell 结束失败，杀掉它：${e.message}")
                    runCatching { proc.destroy() }
                    onFailure(CommandFailure.UNKNOWN)
                    return
                }
            if (!done) {
                XLog.w("shell 超过 ${SHELL_TIMEOUT_MS}ms 没结束，杀掉它：${XLog.redactCommand(command)}")
                try {
                    proc.destroy()
                } catch (e: Throwable) {
                    XLog.d("杀 shell 进程失败（忽略）：${e.message}")
                }
                // 子进程可能不理会 SIGTERM：宽限期内没退就强杀，保证管道被关掉、
                // 读线程能收到 EOF 退出（否则每次超时都永久泄一个读线程）。
                val gone =
                    runCatching {
                        proc.waitFor(SHELL_KILL_GRACE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }.getOrDefault(false)
                if (!gone) {
                    runCatching { proc.destroyForcibly() }
                        .onFailure { XLog.d("强杀 shell 进程失败（忽略）：${it.message}") }
                }
                logOutputTail(tail)
                onFailure(CommandFailure.SHELL_TIMEOUT)
                return
            }
            // 进程已退出，但 stdout 可能被子进程继承后仍开着（`sh -c "... &"`）：
            // 只给读线程一个有限宽限把剩余输出收完，不用它来判定成败。
            runCatching { reader.get(SHELL_DRAIN_JOIN_MS, TimeUnit.MILLISECONDS) }
            // 只有非 0 才算失败：0 是常规路径，不该有任何提示。
            // 合并成一条 `a; b` 的步骤取的是最后一条的退出码（`sh` 的规矩）。
            if (proc.exitValue() != 0) {
                val code = proc.exitValue()
                XLog.w("shell 退出码 $code（走的是 $via）：${XLog.redactCommand(command)}")
                logOutputTail(tail)
                onFailure(classifyFailure(code, via, denied.get()))
            }
        } finally {
            // 收尾时**强关管道**：脚本派生的常驻子进程（`cmd &` / 没有 `nohup` 解除 stdout）
            //  会继承写端，`destroy()` 之后 `inputStream` 未必 EOF —— 读线程就一直卡在 read 上
            //  （daemon 线程同样算泄漏，且占着一对 FD）。close 会让那次 read 抛 IOException 退出。
            //  正常路径上 `use` 已经关过，重复关无害。
            runCatching { proc.inputStream.close() }
            runCatching { proc.errorStream.close() }
        }
    }

    /**
     * 起一个执行 [command] 的子进程，返回「进程 + 实际用的解释器」。
     *
     * **优先 `su -c`，没有 root 才退回 `sh -c`。**
     *
     * 注意：为什么必须优先 `su`：宿主跑在 `platform_app` 域，那个域**没有 `/system/bin/sh` 的执行权**
     * （`system_app` 域同样没有），`ProcessBuilder("sh","-c",…)` 必然 `EACCES`。真机实测：
     * `runcon u:r:platform_app:s0 /system/bin/sh` → `Permission denied`。所以「非 root 的 shell」
     * 在这台机器上**根本起不来**，不是命令写错了。
     * 而 `/system/bin/su` 不受这条限制 —— KernelSU 的 `su_compat` 在 `execve` 上就接管了，
     * 不看域；拿到 uid 0 之后 `am` / `cmd` / `settings` 才跑得起来（真机实测 `am start -a VIEW
     * -d weixin://dl/scan` 退出码 0）。
     *
     * 没有 root 的机器上 `su` 这个二进制压根不存在 ⇒ `start()` 抛 `IOException` ⇒ 自然落到 `sh`。
     * 选中的那个记进 [launcher]，之后不再每次试错（否则非 root 机器每条命令都白起一次失败进程）。
     *
     * 注意：走 `su` 时每次调用都会经 KernelSU 授权一次：如果装了「授权提示」那类模块，每条命令会弹一次
     * toast —— 那是 root 管理器侧的行为，不是本模块的。
     */
    private fun startProcess(command: String): Pair<Process, String> {
        var last: Throwable? = null
        // 上次成功的那个排最前，其余留作兜底（它之后失效时还能退回去）
        val order = launcher?.let { ok -> listOf(ok) + LAUNCHERS.filter { it != ok } } ?: LAUNCHERS
        for (binary in order) {
            try {
                val proc =
                    ProcessBuilder(binary, "-c", command)
                        .redirectErrorStream(true)
                        .start()
                if (launcher != binary) {
                    launcher = binary
                    XLog.w("shell 解释器定为 $binary")
                }
                return proc to binary
            } catch (e: Throwable) {
                last = e
                // 只在**还没定下来**时记：定下来之后那条路不会再试第二个，这一行也就不会重复刷
                XLog.w("用 $binary 起进程失败（${e.javaClass.simpleName}: ${e.message}），试下一个")
            }
        }
        throw last ?: error("没有可用的 shell 解释器")
    }

    /**
     * 失败时把输出尾部以 `w` 回放一遍（**只在这条路径**打，正常退出一个字都不多）。
     *
     *  为什么必须有：`sh: …` 逐行日志是 `d` 级、release 里被编译期消除，于是用户交回来的
     *  release 日志只剩「命令没跑成功 / 这条命令要 root」这一句结论 —— 分不清是组件不存在、
     *  Permission Denial，还是命令拼错。这一句就是给「用户反馈」那张日志用的。
     *
     *  内容**不脱敏**：它已经是命令自己的输出，脱敏会把 `Intent { cmp=… }` 这类判据抹掉
     *  （命令本身在别处已过 `redactCommand`）。
     */
    private fun logOutputTail(tail: List<String>) {
        val lines = synchronized(tail) { tail.toList() }
        if (lines.isEmpty()) return
        XLog.w("shell 输出（尾 ${lines.size} 行）：\n" + lines.joinToString("\n"))
    }

    /** 读输出并逐行匹配权限拒绝提示。 */
    private fun startOutputReader(
        proc: Process,
        denied: java.util.concurrent.atomic.AtomicReference<String>,
        tail: MutableList<String>,
    ): Future<*> =
        readerPool.submit {
            runCatching {
                // `use`：进程退出后那对管道要真正关掉（`sh -c "... &"` 这类会把 stdout
                //  留给子进程继承，EOF 可能来得很晚）—— 不关就是每挂一条泄一个 FD。
                proc.inputStream.bufferedReader().use { r ->
                    r.forEachLine { line ->
                        XLog.d("sh: $line")
                        // 尾部环形保留（同步块在 [logOutputTail] 读的那一头）
                        synchronized(tail) {
                            if (tail.size >= SHELL_TAIL_LINES) tail.removeAt(0)
                            tail.add(line)
                        }
                        if (denied.get() == null) {
                            DENIED_HINTS
                                .firstOrNull { line.contains(it, ignoreCase = true) }
                                ?.let { denied.compareAndSet(null, it) }
                        }
                    }
                }
            }.onFailure { XLog.d("读 shell 输出失败：${it.message}") }
        }

    /**
     * 非 0 退出码 → 失败提示。
     *
     * 注意：**优先看输出，不只看退出码**：`am` / `cmd` 这类在 App uid 下被系统拒时打的是
     * `Permission Denial: …` / `SecurityException`，而退出码只是 255 —— 按退出码分档会报成
     * 「命令没跑成功，检查一下内容」，把「权限不够」说成「内容写错了」。
     *
     * @param deniedHint 读输出时**逐行**认出来的那句权限拒绝（见读线程），`null` = 没认出来。
     *   不在事后扫输出尾巴：`Permission Denial` 后面还有十几行栈帧，尾巴一裁就没了。
     */
    private fun classifyFailure(
        code: Int,
        via: String,
        deniedHint: String?,
    ): CommandFailure {
        if (deniedHint != null) {
            // 走的是 `sh` ⇒ 这台机器上没拿到 su ⇒ 这条命令要 root；
            // 走的是 `su` 还被拒 ⇒ 那就是这条命令自己没有权限。
            XLog.w("shell 输出里认出权限拒绝（$deniedHint），按「权限不够」报（走的是 $via）")
            return if (via == SU) CommandFailure.SHELL_NO_PERMISSION else CommandFailure.SHELL_NO_ROOT
        }
        val byCode = CommandFailure.ofShellExit(code)
        // 退出码落在「泛型失败」那一档、而这次**压根没拿到 root**（`su` 起不来才退回 `sh`）：
        //   报「命令没跑成功，检查一下内容」等于让用户去改一条本来就是对的命令。
        //   `am` / `cmd` / `settings` 这类没有 root 基本都失败，所以这一档按「要 root」报。
        //   126 / 127 不在这里改口 —— 那两档说的是命令本身（不存在 / 没有执行位），比「要 root」更准。
        if (via != SU && byCode == CommandFailure.SHELL_FAILED) {
            XLog.w("shell 退出码 $code、且走的是 sh（没拿到 root）⇒ 按「权限不够」报")
            return CommandFailure.SHELL_NO_ROOT
        }
        return byCode
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
    private fun acquireSlot(): Int =
        //  **进程内也要互斥**：两条线程同时申请时，A 占住槽 0，B 也去开槽 0 —— B 的
        //  `tryLock` 失败、`close()` 那一下会按 POSIX 语义把**本进程在该 inode 上的锁全放掉**，
        //  于是 A 的锁被内核悄悄抹掉、全机上限当场失效。`heldSlots` 的读写也一并被这把锁护住。
        synchronized(slotLock) {
            val dir =
                runCatching {
                    com.lookie.opluskey.core.config.ConfigStore
                        .filesDir()
                }.getOrNull()
            if (dir == null || (!dir.isDirectory && !dir.mkdirs())) return@synchronized acquireLocalSlot()
            var opened = 0
            for (i in 0 until SHELL_MAX_CONCURRENT) {
                // 本进程已经占着这一格就**别再去开它**：Linux 的 fcntl 锁是「按 (进程, inode)」记的，
                //  同一个进程关掉指向该文件的**任意** fd，内核会把这一 inode 上的锁**全部**放掉 ——
                //  再开一次、`tryLock` 抛 `OverlappingFileLockException`、然后 `close()` 那一下，
                //  就把另一个线程正持有的锁悄悄抹了（跨进程上限当场失效）。
                if (heldSlots.containsKey(i)) continue
                val raf =
                    runCatching {
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
            // 只有「一格都没占、且一个文件都没打开」才是锁机制坏了 ⇒ 退回本进程计数。
            //  注意不能只看 `opened == 0`：本进程已经占满三格时循环全被 `continue` 跳过，
            //  `opened` 同样是 0 —— 那是「满」，再退本进程计数等于把上限翻倍（3 + 3）。
            if (opened == 0 && heldSlots.isEmpty()) acquireLocalSlot() else NO_SLOT
        }

    /** 退回本进程计数时用的哨位（不是真槽号，见 [releaseSlot]）。 */
    private fun acquireLocalSlot(): Int {
        if (running.incrementAndGet() > SHELL_MAX_CONCURRENT) {
            running.decrementAndGet()
            return -1
        }
        return LOCAL_SLOT
    }

    /** 放掉一个槽位（[slot] 为 [LOCAL_SLOT] 时放的是本进程计数）。 */
    private fun releaseSlot(slot: Int) {
        synchronized(slotLock) {
            if (slot == LOCAL_SLOT) {
                running.decrementAndGet()
                return
            }
            heldSlots.remove(slot)?.let { runCatching { it.close() } }
        }
    }

    /** 一条 shell 命令的等待上限（毫秒）—— 超时就杀掉并报失败。 */
    private const val SHELL_TIMEOUT_MS = 10_000L

    /** destroy() 之后留给子进程退出（并让读到 EOF）的宽限期，过了就 destroyForcibly()。 */
    private const val SHELL_KILL_GRACE_MS = 500L

    /** 子进程已退出后，等读线程收完剩余输出的上限 —— 只影响日志完整度，不判定成败。 */
    private const val SHELL_DRAIN_JOIN_MS = 500L

    /**
     * 失败时回放多少行输出 —— 只留尾部，且**只在失败路径**打。
     *
     *  逐行的 `sh: …` 是 `d` 级，release 里被 `VERBOSE_LOG=false` 编译期消除 ⇒ 用户交回来的
     *  release 日志里只剩「命令没跑成功 / 这条命令要 root」这一句结论，看不出到底是
     *  「组件不存在」「Permission Denial」还是「命令拼错」。失败是低频路径，回放一次不刷屏。
     *
     *  取 12 行：`am` 报错往往先打 `Starting: Intent …`、再 `Error type 3` / `Error: …`，
     *  几行就到；而 `Permission Denial` 那段后面跟着十几行 `at android.…` 栈帧 ——
     *  判据另有逐行匹配（见 `startOutputReader` 的 `denied`），这里只负责给人看。
     */
    private const val SHELL_TAIL_LINES = 12

    /**
     * 跑 shell 的两条线程池 —— **执行**与**读输出**各一条，各自 [SHELL_MAX_CONCURRENT] 个线程。
     *
     * ## 为什么不共用一个池
     *
     *  执行那条会 `get()` 等读线程结束。共用池时「三条执行线程都在等、读任务还排在队列里」
     *  就是死锁：队列里的任务永远轮不到线程。两个池各有上限，互不相等。
     *
     * ## 为什么核心线程也允许回收
     *
     *  按键是低频动作（长按连发最密 100ms 一条），池不该常驻。`allowCoreThreadTimeOut` 让空闲
     *  线程 60 秒后自己退出 —— 早先是每条命令 `Thread {}` 硬建两条、用完即毁，连发时就是反复
     *  创建 / 销毁 Java 线程。
     *
     * ## 为什么用无界队列而不是 `SynchronousQueue`
     *
     *  `SynchronousQueue` 在池满时抛 `RejectedExecutionException`，而读线程被拒等于「这次命令的
     *  输出没人读」。用 `LinkedBlockingQueue`：超出并发的那一条**排队**，执行侧本来就有
     *  `SHELL_DRAIN_JOIN_MS` 的等待上限，不会因此永久卡住。
     */
    private val runnerPool =
        ThreadPoolExecutor(
            SHELL_MAX_CONCURRENT,
            SHELL_MAX_CONCURRENT,
            SHELL_THREAD_IDLE_SECONDS,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
        ) { r ->
            Thread(r, "opluskey-shell-run").apply { isDaemon = true }
        }.apply { allowCoreThreadTimeOut(true) }

    private val readerPool =
        ThreadPoolExecutor(
            SHELL_MAX_CONCURRENT,
            SHELL_MAX_CONCURRENT,
            SHELL_THREAD_IDLE_SECONDS,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
        ) { r ->
            Thread(r, "opluskey-shell-read").apply { isDaemon = true }
        }.apply { allowCoreThreadTimeOut(true) }

    /** 并发槽位锁文件的前缀（放在宿主私有目录里，两个进程共用）。 */
    private const val SLOT_PREFIX = "opluskey_shell_slot_"

    /** [acquireLocalSlot] 的哨位 —— 与真槽号（0..MAX-1）区分开。 */
    private const val LOCAL_SLOT = -2

    /** [acquireSlot] 的「一个都没占上、且不是锁坏了」—— 调用方据此报 OVERLOADED。 */
    private const val NO_SLOT = -1

    /**
     * 起进程时按顺序试的解释器 —— **`su` 在前**（理由见 [startProcess]）。
     *
     * 两个都认 `-c <命令>`：KernelSU / Magisk 的 `su` 与 Android 的 `sh` 同一种用法。
     */
    private val LAUNCHERS = listOf(SU, "sh")

    /** `su` 这个解释器的名字 —— [LAUNCHERS] 与 [classifyFailure] 共用，别各写一份字面量。 */
    private const val SU = "su"

    /** 输出里出现这几句 = 权限拒绝（`am` 在 App uid 下就是那句 `Permission Denial`）。 */
    private val DENIED_HINTS =
        listOf(
            "Permission Denial",
            "SecurityException",
            "Permission denied",
            "Operation not permitted",
        )

    /** 上一次成功用过的解释器（`null` = 还没定）。定下来之后不再每条命令都试错。 */
    @Volatile
    private var launcher: String? = null

    /** 从 `ProcessBuilder.start()` 的异常里抠 errno（`Cannot run program "su": error=2, …`）。 */
    private val ERRNO = Regex("error=(\\d+)")

    /** 退回本进程计数时用的计数器（见 [acquireLocalSlot]）。 */
    private val running =
        java.util.concurrent.atomic
            .AtomicInteger(0)

    /** 已占住的槽位：槽号 → 那个被锁住的锁文件句柄（关掉它 = 释放 `FileLock`）。 */
    private val heldSlots = java.util.concurrent.ConcurrentHashMap<Int, java.io.RandomAccessFile>()

    /**
     * 槽位申请 / 释放的**进程内**互斥。
     *
     *  没有它的时候，「检查 `heldSlots` → 开文件 → `tryLock` → 写 `heldSlots`」这一段是可交错的；
     *  更糟的是交错那一支会 `close()` 自己刚开的 fd，而 POSIX 规定这会把**本进程在该 inode 上
     *  的锁全部释放** —— 另一条线程正持有的锁就这么没了。叶子锁：只包本地状态与文件操作，
     *  里面不调任何外部代码。
     */
    private val slotLock = Any()
}
