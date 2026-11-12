package com.lookie.opluskey.host.action

import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.KsuCompat
import com.lookie.opluskey.core.present.CommonCommands

internal class CustomCommandRunner(
    /**
     * 跑一条 shell 命令（含 KSU 别名翻译）。
     *
     *  第二参 = **是否以 root 执行**（`true` 只走 `su`、`false` 只走 `sh`，互不兜底）：单条命令按
     *  它自己那个开关给（`Command.root`），多条命令的步骤没有各自的开关、一律 `true`。
     *
     *  第三参 = **跑完之后**回调一次（不论成败、也不论「根本没跑」）—— 只在有步骤间隔的多条命令里
     *  给：那时下一步要**等这一步真的结束**才排（见 [runSteps]）。`null` = 不用回执。
     */
    private val execShell: (String, Boolean, (() -> Unit)?) -> Unit,
    /** 派发一个非 shell 的内置动作。 */
    private val dispatch: (Action) -> Unit,
    /** 展开失败时的通知出口。 */
    private val onFailure: (CommandFailure) -> Unit,
) {
    /**
     * 整条命令链跑在哪条线程上 —— **自己一条，不借调用方的**。
     *
     *  原先续步用的是 `ActionRunner` 传进来的那个 Handler，而它是**主线程 Looper**：
     *  第一步在调用方线程上跑、后面的每一步却被 `postDelayed` 扔回宿主 UI 线程 ——
     *  一条五步、间隔 1s 的命令会把其中四步的起进程与 binder 全压在主线程上；设置页
     *  「测试命令」更糟，整条链从第一步起就在主线程（Compose 点击回调里直接进来的）。
     *
     *  单线程而不是线程池：「同时只跑一条链」那道闸（[runningSerial]）与「步骤严格按顺序」
     *  都要求串行，多线程只会让闸变成摆设。
     */
    private val worker: Handler by lazy {
        val thread = android.os.HandlerThread("opluskey-cmd").apply { start() }
        Handler(thread.looper)
    }

    /**
     * 正在跑的多条命令（按命令名）—— 顶上两道闸都看它（见 [enterChain]）：
     *  · **非空即挡**：一次只跑一条命令链；
     *  · 再按名字挡一道：同一条的重入不许叠着跑。
     *
     * 用并发集合：占位与摘除不保证在同一个线程上（`postDelayed` 的续步在另一条路上）。
     */
    private val runningSerial: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet()

    /**
     * 按**名字**执行一条自定义命令（从库里查那一条）。
     */
    fun run(name: String) {
        if (name.isBlank()) {
            // 空名不是「库里没有」：配置里写了空的 `cmd:`（后面没写名字，手改坏了 / 老版本残留）
            //  按下去要有反馈，而那句话说的是**这条引用是空的**，不是「命令已经不在了」
            //  —— 后者会让人去命令库里找一条根本没被写进名字的东西。
            XLog.w("自定义命令名为空（配置里写了空的 ${Command.PREFIX}，后面没写名字），跳过")
            onFailure(CommandFailure.EMPTY)
            return
        }

        //  先在**用户那份命令库**里找，找不到才退到模块内置的那几条（`CommonCommands`：
        //  常用命令那一段绑出来的 `cmd:common.wechat_scan` 就只存在代码里，不进命令库文件）。
        //  顺序不能反：用户自己建的同名命令是他写的，模块带的那几条不许盖掉它。
        val cmd =
            CommandLibrary.loadCached().firstOrNull { it.name == name }
                ?: CommonCommands.commandOf(name)?.also {
                    XLog.d("「$name」不在命令库里，按内置常用命令执行（${XLog.redactCommand(it.single)}）")
                }
        if (cmd == null) {
            XLog.w("命令库里没有「$name」（可能已被删除 / 改名），跳过")
            onFailure(CommandFailure.NOT_FOUND)
            return
        }
        run(cmd)
    }

    /**
     * 跑一条**已经在手上**的命令 —— 不再回库里按名字查。
     *
     *  设置页「测试命令」走这里：草稿态下这条命令还没保存，库里要么没有它、要么存的是**改之前**
     *  那一版，按名字跑测的就不是眼前这份（最容易骗人的一种「看起来测过了」）。
     *  列表菜单那条传进来的也是当前配置里那一条，与按名字查等价。
     */
    fun run(cmd: Command) {
        //  **先挪到自己的线程**（理由见 [worker]）：设置页「测试命令」是从 Compose 的点击回调里
        //  直接进来的 —— 那一次调用在主线程上，而它接下来要读命令库之外还要起进程、发 binder。
        //  已经在执行线程上（续步）就地跑，避免把自己排到自己后面。
        if (Looper.myLooper() != worker.looper) {
            val posted =
                worker.post {
                    try {
                        run(cmd)
                    } catch (e: Throwable) {
                        XLog.w("自定义命令排到执行线程后失败：${cmd.name.ifBlank { UNNAMED }}", e)
                    }
                }
            if (!posted) {
                // 线程要退了（进程在收尾）：这一下确实什么都没发生，报一句而不是静默
                XLog.w("执行线程已退，自定义命令「${cmd.name.ifBlank { UNNAMED }}」这一下没跑")
                onFailure(CommandFailure.UNKNOWN)
            }
            return
        }
        // 草稿可以还没名字（新命令写到一半就要试）—— 名字只进日志与「正在跑」那张表
        val name = cmd.name.ifBlank { UNNAMED }

        // 空命令在这里一次判掉：单条没正文 / 多条没步骤是**同一件事**（这条命令没有可跑的内容），
        //  两个分支各写一遍时，总有一处会漏成「跑完了但没动静」。
        if (!cmd.hasContent) {
            XLog.w("自定义命令「$name」（${cmd.kind.id}）是空的，跳过")
            onFailure(CommandFailure.EMPTY)
            return
        }

        if (cmd.kind == CommandKind.SINGLE) {
            runSingle(cmd, name)
            return
        }

        // 「同时只跑一条命令链」的两道闸 —— 见 [enterChain]。
        if (!enterChain(name)) return
        try {
            //  步骤里的 `cmd:<另一条>` 先展开成扁平表（多层、带环检测）：`runSteps` 只认「一步一步的
            //  动作文本」，引用在进它之前就化成真实步骤，续步 / 间隔 / 合并那套逻辑一行都不用动。
            //  **放在 try 里**：展开要读命令库，抛出去的话占位就还不回来了（所有命令链会一起锁死）。
            val steps = expandStepRefs(cmd, onFailure)
            XLog.w("自定义命令「$name」（多条）共 ${cmd.steps.size} 步，展开后 ${steps.size} 步")
            runSteps(steps, 0, cmd.stepDelayMs, name)
        } catch (e: Throwable) {
            // 同步段抛了：把占位还回去，否则这条命令从此再也跑不起来（永久被自己挡住）
            runningSerial.remove(name)
            throw e
        }
    }

    /**
     * 单条命令那一支（从 [run] 拆出来的，只为让它的 `return` 条数留在 detekt 的 `ReturnCount` 之下）。
     *
     * @param name 日志与「正在跑」那张表用的名字（草稿没名字时是 `UNNAMED`）
     */
    private fun runSingle(
        cmd: Command,
        name: String,
    ) {
        // 空命令已在上一步（`run` 的 `hasContent`）判过，这里只剩真正要跑的正文
        XLog.w("自定义命令「$name」（单条）→ ${XLog.redactCommand(cmd.single)}")
        // 与多条步骤同一套语义（Action.parse）：keyevent: / intent: / app: / shortcut: /
        // shell: 这些前缀写法在单条里同样成立。**没有前缀的写法不再兜底成 shell**
        // （解析成 `none`、不执行）—— 界面在「命令详情」那一栏就拦住了（`Action.hasSupportedFormat`），
        // 这里能收到说明是老配置 / 手改文件，执行侧只留日志。
        val parsed = Action.parse(cmd.single)
        if (parsed is Action.Shell) {
            //  单条自己的「以 root 执行」只在这一支生效：它是**整条命令**的开关，不跟着正文的
            //  `shell:` 前缀走（前缀只是格式，同一个前缀在「多条」的步骤里也一样用）。
            execShell(parsed.command, cmd.root, null)
            return
        }
        //  正文里写 `cmd:<另一条>` 是**老版本 / 手改文件**才有的写法：命令正文不再支持引用另一条
        //  命令（界面那一栏也存不进去）。这里不展开、不递归，报一句就停 —— 否则一条自引的命令
        //  会一路展开到自己。
        if (parsed is Action.Custom) {
            XLog.w("自定义命令「$name」的正文引用了另一条命令（不再支持），跳过：${parsed.name}")
            onFailure(CommandFailure.REF_NOT_ALLOWED)
            return
        }
        dispatch(parsed)
    }

    /**
     * 逐步执行多条命令：每步派发完等 [gapMs] 毫秒再走下一步。
     *
     * **有步骤间隔的 shell 步骤要等它真的结束**（[execShell] 的第三参）：`execShell` 是「起进程
     * 就返回」的，原先这里只按「起完 → 等 gapMs → 起下一条」排，两条其实并发跑 ——
     * 「先建目录、再往里面写文件」这种命令把间隔设多大都可能抢在前面。现在改成等回执再排。
     *
     * 续步一律经 [scheduleNext] 回到 [worker] 上排队，所以整条链始终在同一条线程、同一顺序上。
     *
     * @param index 下一个要派发的步骤下标
     * @param gapMs 步骤间等待时长（毫秒，0 = 不等待）
     * @param name 正在跑的这条命令名 —— 跑完（含异常中止）时把它从 [runningSerial] 里摘掉
     */
    private fun runSteps(
        steps: List<String>,
        index: Int,
        gapMs: Int,
        name: String,
    ) {
        if (index >= steps.size) {
            finishSerial(name)
            return
        }

        val shell = shellStepText(steps[index])
        //  空步骤（`shell:` 后面没写内容 / 手改出来的空行）就地跳过：它没有任何要跑的东西，
        //  而放进下面任何一条分支都会多一次起进程（或被并成以 ` && ` 开头的一条串）。
        if (shell != null && shell.isBlank()) {
            XLog.w("  第 ${index + 1} 步是空命令，跳过")
            scheduleNext(steps, index + 1, gapMs, name)
            return
        }
        // 注意：小布识屏那条别名会被翻译成**原生调用**、不是真的 shell，必须**单独派发**。
        //  并进 `;` 拼接串的话，`runShell` 的前缀认领会把**整串**认成别名，
        //  后面那些真正要跑的 shell 步骤随整串一起被吞掉。
        //  （截图那条别名不在此列：它现在就是一条普通的 `service call`，走 shell 合并没问题。）
        //  它是一条同步的原生调用，所以**不走**下面那个「等回执」的分支。
        if (shell != null && KsuCompat.isSidebarAlias(shell)) {
            XLog.w("  第 ${index + 1} 步（KSU 别名）→ $shell")
            //  多条命令的步骤没有各自的开关 ⇒ 一律按默认（`su`，起不来不退回 `sh`）
            execShell(shell, true, null)
            scheduleNext(steps, index + 1, gapMs, name)
            return
        }

        if (shell != null && gapMs > 0) {
            XLog.w("  第 ${index + 1} 步（shell，等它跑完再等 ${gapMs}ms）→ ${XLog.redactCommand(shell)}")
            execShell(shell, true) { scheduleNext(steps, index + 1, gapMs, name) }
            return
        }

        val next =
            if (shell != null) {
                // 只有「没有步骤间隔」时才合并：`a; b` 是零延迟，合并等于把用户配的 stepDelayMs 抹掉。
                val (joined, nextIndex) = collectShellRun(steps, index)
                XLog.w("  第 ${index + 1}~$nextIndex 步（shell，合并成一条）→ ${XLog.redactCommand(joined)}")
                execShell(joined, true, null)
                nextIndex
            } else {
                val action = Action.parse(steps[index])
                XLog.w("  第 ${index + 1} 步 → ${XLog.redactToken(action.token)}")
                //  正文里写 `cmd:<另一条>` 是老版本 / 手改文件才有的写法：命令正文不再支持引用
                //  另一条命令。这一步跳过并报一句 —— 不展开、不递归，链照常往下走。
                if (action is Action.Custom) {
                    XLog.w("  第 ${index + 1} 步引用了另一条命令（不再支持），跳过：${action.name}")
                    onFailure(CommandFailure.REF_NOT_ALLOWED)
                } else {
                    dispatch(action)
                }
                index + 1
            }
        scheduleNext(steps, next, gapMs, name)
    }

    /**
     * 排下一步 —— **唯一的续步入口**。
     *
     *  · 没有间隔（或已经是最后一步）时就地跑（已经在 [worker] 上）或投给它；
     *  · 有间隔时 `postDelayed` 到 [worker]，**不**用主线程 Handler（理由见 [worker]）。
     *
     * 排不上（线程要退了）⇒ 这一枪永远不会响，占位就留在 [runningSerial] 里。必须在这儿归还：
     * 闸的第一道是「非空即挡」，漏掉一个占位会锁死**所有**多条命令。
     */
    private fun scheduleNext(
        steps: List<String>,
        next: Int,
        gapMs: Int,
        name: String,
    ) {
        val delayed = gapMs > 0 && next < steps.size
        val posted =
            when {
                !delayed && Looper.myLooper() == worker.looper -> {
                    runStepsGuarded(steps, next, gapMs, name)
                    true
                }

                !delayed -> {
                    worker.post { runStepsGuarded(steps, next, gapMs, name) }
                }

                else -> {
                    worker.postDelayed({ runStepsGuarded(steps, next, gapMs, name) }, gapMs.toLong())
                }
            }
        if (!posted) {
            XLog.w("续步排不上（执行线程要退了），中止这条链：$name")
            finishSerial(name)
        }
    }

    /**
     * [runSteps] 的异常壳 —— 续步跑在 [worker] 的 Looper 上，异常抛出去就是那条线程死掉
     * （之后**所有**自定义命令都不再执行）。
     */
    private fun runStepsGuarded(
        steps: List<String>,
        index: Int,
        gapMs: Int,
        name: String,
    ) {
        try {
            runSteps(steps, index, gapMs, name)
        } catch (e: Throwable) {
            // 这一条链到这里断了：把占位还回去（否则这条命令从此永久被自己挡住）
            XLog.w("自定义命令续步失败（已中止这条链）：$name", e)
            finishSerial(name)
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 一条命令链跑完（或中止）—— 摘掉 [runningSerial] 里的占位。 */
    private fun finishSerial(name: String) {
        runningSerial.remove(name)
    }

    /**
     * 「同时只跑一条命令链」的两道闸。
     *
     *  第一道按「有没有链在跑」挡，不是按名字：连着按两下（单击 / 双击都算）会让两条命令链
     *  **交错** —— 第 2 条的第 1 步插在第 1 条的第 2 步前面（间隔越长的链越明显）。
     *  只按名字挡的话，两条**不同的**命令照样同时跑（名字不同，谁都挡不住谁），闸就没达到那句话。
     *  闸设在这里而不是判定侧：判定侧分不出「上一次跑完没有」，只有展开器自己知道。
     *
     *  第二道按名字挡：同一条命令的重入（连着触发同一条）。
     *
     * @return false = 这一下被挡掉了（已经报过 [CommandFailure.OVERLOADED]），调用方直接返回
     */
    private fun enterChain(name: String): Boolean {
        if (runningSerial.isNotEmpty()) {
            XLog.w("已有命令链在跑（${runningSerial.joinToString()}），「$name」这一下跳过（避免两条链交错）")
            onFailure(CommandFailure.OVERLOADED)
            return false
        }
        if (!runningSerial.add(name)) {
            XLog.w("自定义命令「$name」还在跑，这一下跳过（避免两条命令链交错）")
            onFailure(CommandFailure.OVERLOADED)
            return false
        }
        return true
    }

    /**
     * 这一步若是 shell 命令则返回命令文本，否则 null。`cmd:` 开头的是引用必须排除，
     * 否则会被 `Action.parse` 兜底当成一条名叫 `cmd:我的命令` 的 shell 命令。
     */
    private fun shellStepText(step: String): String? =
        if (step.startsWith(Command.PREFIX)) null else (Action.parse(step) as? Action.Shell)?.command

    /**
     * 从 [index] 起收集**连续的** shell 步骤，返回（拼成一条的合并串, 下一个下标）。
     *
     *  遇到非 shell 步骤、或 KSU 别名就断开：别名会被翻译成原生调用（不是真 shell），
     *  并进拼接串会让 `runShell` 的前缀认领把整串认成别名，后面那些步骤随整串被吞掉。
     */
    private fun collectShellRun(
        steps: List<String>,
        index: Int,
    ): Pair<String, Int> {
        val merged = ArrayList<String>()
        var cursor = index
        while (cursor < steps.size) {
            val text = shellStepText(steps[cursor]) ?: break
            if (KsuCompat.isSidebarAlias(text)) break
            //  **空步不能进拼接串**：`shell:` 后面没写内容时命令是空串，`joinToString` 会在开头
            //  留下一个 ` && `（`sh -c " && echo x"` 直接是语法错误），整条链跟着失败。
            if (text.isNotBlank()) merged.add(text)
            cursor++
        }
        //  用 `&&` 而不是 `;`：`sh -c "a; b"` 的退出码只取**最后一条**，前一步失败既不报错、
        //  也不会拦住后一步 —— 用户看到的是「没跑成却当成功」。`&&` 让失败当场停下、退出码非 0。
        //  （用户命令里自带分号/后台符的老问题两种写法都一样，不是这里新引入的。）
        return merged.joinToString(" && ") to cursor
    }

    private companion object {
        /** 草稿还没起名时在日志 / 「正在跑」表里用的占位 —— 只给人看，不落盘。 */
        const val UNNAMED = "（未命名）"
    }
}

/**
 * 把多条命令步骤里的 `cmd:<另一条>` 换成被引用那条命令的正文。
 *
 * ## 规则
 *
 *  · **单条**命令的正文不引用任何命令（`Action.hasSupportedFormat` 与执行期
 *    [CommandFailure.REF_NOT_ALLOWED] 两道拦着，正常操作写不进去）；
 *  · **多条**命令的每一步**只能引用单条命令**（用户口径：「单条命令无法引用任何命令，
 *    多条命令只能引用单条命令」）。
 *
 *  于是引用图只有「多条 → 单条」一个方向、单条没有出边 —— **不可能成环**，所以这里既不递归、
 *  也不需要环检测：一层替换完就结束。抽屉那一侧同样只列单条
 *  （`StepSheetFilter.singleCommandsOnly`），用户根本挑不到会被拒绝的目标；能走到下面那两条
 *  报错分支的，只有手改文件与老配置。
 *
 * ## 换法
 *
 *  · 引用单条：它那一整条正文就是这一步（`cmd.single` 直接落到这个位置）；
 *  · 找不到这条命令（被删 / 改名）：跳过这一步并报 [CommandFailure.NOT_FOUND]；
 *  · 引用的是一条**多条**命令：跳过并报 [CommandFailure.REF_ONLY_SINGLE]。
 *
 *  **步骤间隔只认外层那一条命令自己设的**（`Command.stepDelayMs`）：换过来的一步就是一步，
 *  被引用命令自己的间隔不参与 —— 否则相邻两步之间等多久会变得没法解释。
 *
 * @param cmd 正在跑的这条（外层）命令
 * @param onFailure 展开期的问题出口 —— 只报**第一条**：一条命令里好几处引用坏掉时，
 *   连弹五条同样的提示没有意义（日志里逐条都有）
 * @return 步骤表（原样的非引用步骤 + 换出来的正文）
 */
private fun expandStepRefs(
    cmd: Command,
    onFailure: (CommandFailure) -> Unit,
): List<String> {
    //  没有任何引用时原样返回（最常见的一档：省掉一次命令库解码）
    if (cmd.steps.none { it.startsWith(Command.PREFIX) }) return cmd.steps

    //  命令库整趟只解一次：每步调一次 `loadCached` 会反复解析同一份 JSON
    val library = CommandLibrary.loadCached()
    var reported = false
    val report: (CommandFailure, String, String) -> Unit = { failure, ref, why ->
        XLog.w("多条命令展开：「$ref」$why —— 这一步跳过")
        if (!reported) {
            reported = true
            onFailure(failure)
        }
    }
    val out = ArrayList<String>(cmd.steps.size)
    for (step in cmd.steps) {
        val resolved = resolveStep(step, library, report) ?: continue
        out.add(resolved)
    }
    return out
}

/**
 * 一步该变成什么 —— `null` = 这一步作废（引用坏了，已通过 [report] 报过）。
 *
 *  拆出来只为把 [expandStepRefs] 的圈复杂度留在阈值内，也让「哪几步会作废」一眼看得全。
 */
private fun resolveStep(
    step: String,
    library: List<Command>,
    report: (CommandFailure, String, String) -> Unit,
): String? {
    if (!step.startsWith(Command.PREFIX)) return step
    val ref = step.removePrefix(Command.PREFIX).trim()
    //  用户那份库优先，找不到才退到模块内置的那几条（与 `run(name)` 同一个顺序）
    val target = library.firstOrNull { it.name == ref } ?: CommonCommands.commandOf(ref)
    if (target == null) {
        report(CommandFailure.NOT_FOUND, ref, "在命令库里找不到")
        return null
    }
    if (target.kind != CommandKind.SINGLE) {
        report(CommandFailure.REF_ONLY_SINGLE, ref, "是多条命令，这里只能引用单条")
        return null
    }
    //  空正文没什么可加的（与「空步骤跳过」同一条：它没有任何要跑的东西）
    return target.single.takeIf { it.isNotBlank() }
}
