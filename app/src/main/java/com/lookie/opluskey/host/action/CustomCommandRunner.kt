package com.lookie.opluskey.host.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KsuCompat
import com.lookie.opluskey.core.present.CommonCommands
import com.lookie.opluskey.core.wire.ModuleWire

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
    private val execShell: (String, Boolean, Boolean, (() -> Unit)?) -> Unit,
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
     * 单条命令那一支 —— [run] 只管「多条命令链」那半，单条正文怎么解析、怎么执行在这里。
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
            execShell(parsed.command, cmd.root, cmd.viaShizuku, null)
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
            execShell(shell, true, false, null)
            scheduleNext(steps, index + 1, gapMs, name)
            return
        }

        if (shell != null && gapMs > 0) {
            XLog.w("  第 ${index + 1} 步（shell，等它跑完再等 ${gapMs}ms）→ ${XLog.redactCommand(shell)}")
            execShell(shell, true, false) { scheduleNext(steps, index + 1, gapMs, name) }
            return
        }

        val next =
            if (shell != null) {
                // 只有「没有步骤间隔」时才合并：`a; b` 是零延迟，合并等于把用户配的 stepDelayMs 抹掉。
                val (joined, nextIndex) = collectShellRun(steps, index)
                XLog.w("  第 ${index + 1}~$nextIndex 步（shell，合并成一条）→ ${XLog.redactCommand(joined)}")
                execShell(joined, true, false, null)
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
        //  引用解析：用户那份库优先，找不到才退到模块内置的那几条（与 `run(name)` 同一个顺序）。
        //  `null` = 这一步作废（引用坏了，已通过 `report` 报过），跳过。
        if (!step.startsWith(Command.PREFIX)) {
            out.add(step)
            continue
        }
        val ref = step.removePrefix(Command.PREFIX).trim()
        val target = library.firstOrNull { it.name == ref } ?: CommonCommands.commandOf(ref)
        if (target == null) {
            report(CommandFailure.NOT_FOUND, ref, "在命令库里找不到")
            continue
        }
        if (target.kind != CommandKind.SINGLE) {
            report(CommandFailure.REF_ONLY_SINGLE, ref, "是多条命令，这里只能引用单条")
            continue
        }
        //  空正文没什么可加的（与「空步骤跳过」同一条：它没有任何要跑的东西）
        target.single.takeIf { it.isNotBlank() }?.let { out.add(it) }
    }
    return out
}

/**
 * 失败提示表：Toast 里只出现 [hint]（说清哪里不对 + 怎么办），原始信息照旧进日志
 * （按枚举名 grep）。新增一类失败就加一个分量，要按数值分流时才加映射函数。
 */
internal enum class CommandFailure(
    val key: String,
    val fallback: String,
) {
    /** `cmd:<名字>` 在命令库里找不到（被删了 / 改名了 / 文件被手改坏）。 */
    NOT_FOUND(Strings.COMMAND_FAILURE_NOT_FOUND, "命令已不存在"),

    /** 命令是空的（单条没填内容，或多条一步都没有）。 */
    EMPTY(Strings.COMMAND_FAILURE_EMPTY, "这条命令是空的，补上内容再试"),

    /**
     * **单条**命令的正文里写了 `cmd:<另一条>` —— 单条不引用任何命令（老配置 / 手改文件才会出现）。
     */
    REF_NOT_ALLOWED(Strings.COMMAND_FAILURE_REF_NOT_ALLOWED, "单条命令的正文不能引用另一条命令"),

    /**
     * 多条命令的某一步引用了**另一条多条命令** —— 那里只能引用单条。
     *
     *  规则是「单条不引用任何命令、多条只能引用单条」，于是引用图里没有回边、不可能成环 ——
     *  这也是这里不需要环检测的原因。正常操作在抽屉里就挑不到多条（`StepSheetFilter.singleCommandsOnly`），
     *  这一档只对手改文件与老配置生效。
     */
    REF_ONLY_SINGLE(Strings.COMMAND_FAILURE_REF_ONLY_SINGLE, "多条命令的步骤只能引用单条命令"),

    /** `ProcessBuilder` 直接起不来（SELinux / 进程数上限 …）。 */
    SHELL_REJECTED(Strings.COMMAND_FAILURE_SHELL_REJECTED, "系统不让执行这条命令"),

    /** shell 命令超时还没结束（在这个身份下挂住了）—— 进程已被杀掉。 */
    SHELL_TIMEOUT(Strings.COMMAND_FAILURE_SHELL_TIMEOUT, "这条命令卡住了，已经中止"),

    /** shell 退出码 127 —— 命令本身不存在。 */
    SHELL_MISSING(Strings.COMMAND_FAILURE_SHELL_MISSING, "系统里找不到这条命令（检查拼写或路径）"),

    /** shell 退出码 126 —— 找到了但没有执行权限。 */
    SHELL_NO_PERMISSION(Strings.COMMAND_FAILURE_SHELL_NO_PERMISSION, "这条命令要更高权限：检查文件执行位，或给 [手势体感] 授权"),

    /**
     * 起不了**选定的**那个 shell 解释器 —— 最常见的原因是**没有 root 授权**。
     *
     *  未授权时 KernelSU 直接让 `/system/bin/su` **不可见**（`sh` 报 `inaccessible or not found`），
     *  而宿主所在的 `platform_app` 域又没有 `/system/bin/sh` 的执行权 ⇒ 两条路各自都断，
     *  所以这里没有「换另一个解释器再试一次」这种退路（见 `ShellRunner.startProcess`）。
     *  提示里只说**宿主**「手势体感」：命令跑在宿主进程里，授权要授给那个包，不是模块自己的包
     *  （真机实测两条授权互不影响，见 `docs/runtime/key-to-gesture.md`）。
     */
    SHELL_NO_ROOT(Strings.COMMAND_FAILURE_SHELL_NO_ROOT, "手势体感 没有 root 权限"),

    /**
     * 命令自己**没勾**「以 root 执行」，而它要 root 才跑得起来。
     *
     *  与 [SHELL_NO_ROOT] 分开是因为**该做的事完全不同**：这一档去「命令设置」里把「以 root 执行」
     *  打开就行，`root` 早就在了；那一档才是去 root 管理器给宿主授权。
     *  两者按「先看命令的开关，再看机器有没有 root」的顺序判（见 `ShellRunner.classifyFailure`）。
     */
    SHELL_ROOT_OFF(Strings.COMMAND_FAILURE_SHELL_ROOT_OFF, "此命令需要 root 权限"),

    /**
     * 勾了「以 Shizuku 执行」，但这台机器上**没有可用的 Shizuku**（没装 / 服务没起 / 没授权）。
     *
     *  与 [SHELL_NO_ROOT] 分开：这一档该做的事是「装上 Shizuku 或在模块里重新授权」，
     *  不是去 root 管理器。判定在模块进程那一侧（`ShizukuShellService` 里 ping + 查授权）。
     */
    SHELL_NO_SHIZUKU(Strings.COMMAND_FAILURE_SHELL_NO_SHIZUKU, "没有可用的 Shizuku（装上或重新授权后再试）"),

    /** 其它非 0 退出码 —— 命令跑起来了但自己报错。 */
    SHELL_FAILED(Strings.COMMAND_FAILURE_SHELL_FAILED, "命令没跑成功"),

    /** `app:` 解析不到界面（包名错 / 应用已卸载）。 */
    APP_MISSING(Strings.COMMAND_FAILURE_APP_MISSING, "找不到这个应用，可能已经卸载了"),

    /** `app:` 应用装着但没有可启动界面（与 [APP_MISSING] 分开，报「已卸载」会误导）。 */
    APP_NO_ENTRY(Strings.COMMAND_FAILURE_APP_NO_ENTRY, "这个应用没有可启动的 Activity"),

    /** 小布快捷指令那几个 action 都没被系统接受。 */
    SHORTCUT_FAILED(Strings.COMMAND_FAILURE_SHORTCUT_FAILED, "小布快捷指令没能触发"),

    /** 兜底：执行时抛了异常，但说不上是哪一类。 */
    UNKNOWN(Strings.COMMAND_FAILURE_UNKNOWN, "这次没执行成功"),

    /**
     * 这一条**指向本模块自己**（宿主包 / 设置页进程），拒绝执行 —— 与 [UNKNOWN] 分开。
     *
     *  报「这次没执行成功」会把用户推去查 root 与命令，而正确答案是换一个应用（多半是挑错了）。
     */
    SELF_TARGET(Strings.COMMAND_FAILURE_SELF_TARGET, "该命令指向本模块自身，已跳过"),

    /**
     * 小窗：这个应用**收不起来小窗**（服务端没受理，或收完没出窗口）。
     *
     * 只提示、**什么都不做** —— 不再退「按包名另起一份实例」( 用户口径)：
     * 那条会先回桌面再起，用户看到的是「一按侧键就回桌面重开」，比没反应更糟
     * （见 `docs/features/zoom-window/notes.md` §2.2）。
     */
    APP_NO_MINI_WINDOW(Strings.COMMAND_FAILURE_APP_NO_MINI_WINDOW, "前台应用不支持小窗"),

    /**
     * 上一条还没结束又来一条 —— 两种来源：
     *  · `shell:` 并发超上限（`ShellRunner` 那一侧的闸）；
     *  · 命令链还在跑时又触发了一条（`CustomCommandRunner` 的「一次只跑一条」闸）。
     */
    OVERLOADED(Strings.COMMAND_FAILURE_OVERLOADED, "上一条命令尚未执行完毕，请稍后再试"),

    /**
     * 免打扰（AOSP 接口那一档）：宿主没有「勿扰访问」授权。
     *
     *  `ACCESS_NOTIFICATION_POLICY` 装包时就 granted，但那只是权限位；真正管用的是
     *  appop（真机上 `enabled_notification_policy_access_packages` 是空的 ⇒ 没授）。
     *  没授时 `setZenMode` 抛 `SecurityException`，并进 [UNKNOWN] 只会说「这次没执行成功」，
     *  用户不知道要去哪儿开。这一档专门说清「缺的是哪一项授权」。
     */
    DND_NOT_GRANTED(Strings.COMMAND_FAILURE_DND_NOT_GRANTED, "免打扰缺 [勿扰权限]：设置 → 通知与状态栏 → 勿扰，允许 [手势体感]"),
    ;

    open val hint: String
        get() = I18n.getOr(key, fallback)

    companion object {
        /**
         * shell 退出码 → 提示：只认 126 / 127（`sh` 的约定），其余非 0 一律归 [SHELL_FAILED]。
         */
        fun ofShellExit(code: Int): CommandFailure =
            when (code) {
                126 -> SHELL_NO_PERMISSION
                127 -> SHELL_MISSING
                else -> SHELL_FAILED
            }
    }
}

/**
 * 「这个包还在不在」—— 解析不到界面 / 拉起被拒时**报哪一档失败**的判据。
 *
 *  判据本身只有一句：`getApplicationInfo` 问得到就是还在。但有两个坑写在调用点上：
 *  · **分身要用那个用户自己的 PM**：同包名在 0 与 998/999 里各有一份，拿主用户 PM 去问，
 *    答的是另一份的事 —— 调用方要把 [AppCloneCatalog.packageManagerFor] 的结果传进来，
 *    别拿「主用户 PM 兜底」那一份来问分身；
 *  · 显式组件**被停用 / 组件不存在**时抛的也是 `ActivityNotFoundException`，一律报
 *    [CommandFailure.APP_MISSING] 会把用户带偏 —— 包明明还在。
 *
 *  分身拿不到自己那份 PM 时（`createContextAsUser` 在宿主上必失败）**保持现状、按「还在」算**：
 *  报「可能已卸载」是拿一个问不到的答案当结论。这一档记 `d`（它每次开机都可能来）。
 *
 *  `runCatching` 在这里表达的是「预期内的失败 = 没找到」，不是「保护宿主」（那一类用 `XLog.guard`）。
 */
internal fun appInstalled(
    pm: PackageManager?,
    pkg: String,
    userId: Int,
): Boolean {
    if (pm == null) {
        if (userId != 0) {
            XLog.d("appInstalled：分身 user=$userId 没有该用户的 PM，按「还在」处理（保持现状）")
            return true
        }
        return false
    }
    return runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess
}

/** 上面那个布尔 → 失败档位：「还在」= 没有可用入口，「不在」= 可能已卸载。 */
internal fun entryFailure(installed: Boolean): CommandFailure = if (installed) CommandFailure.APP_NO_ENTRY else CommandFailure.APP_MISSING

/**
 * 把失败提示弹出来。
 *
 *  提示**不走本地 `Toast`**（那个受系统的按包限流）：统一交给注入侧的 [requestSystemToast]。
 *  去重也一并交给它那一侧（`inject/SystemToast` 的 1 秒同句去重），这里不再自己记账。
 */
internal object FailureNotice {
    /** 弹一条失败提示；context 为 null 时只记日志。 */
    fun show(
        context: Context?,
        failure: CommandFailure,
    ) {
        // 日志这一行带枚举名，是排查时的锚点
        XLog.w("执行失败：${failure.name} —— ${failure.hint}")
        toast(context, failure.hint)
    }

    /**
     * 弹一条普通提示（非失败）：「执行时提示」（`GlobalConfig.toastOnRun`）、命令自己那行 `tip:`、
     * 以及各类失败原因，全都从这里出去。
     *
     *  **不再本地 `Toast`：一律请注入侧替我们弹**（[requestSystemToast]）。宿主自己的包受系统
     *  的按包限流（3 条 / 20 秒、5 条 / 42 秒、6 条 / 68 秒，见 `docs/device/actions/feedback.md` 四十），
     *  连按几下之后后面那些被 `NotificationService` 直接丢掉 —— 真机表现就是「执行了但没有提示」。
     *  系统自己（包名 `android`）持有 `UNLIMITED_TOASTS`，改由它发话这条路已经现成
     *  （`inject/SystemToast`，命令里 `tip:` 一直走的就是它）。
     *
     * @param context 宿主 Context；null 或空串时什么都不做。
     */
    fun toast(
        context: Context?,
        text: String,
    ) {
        if (context == null || text.isEmpty()) return
        requestSystemToast(context, text)
    }
}

/**
 * 请注入侧（`system_server`）替我们弹这条提示 —— 全模块**唯一的提示出口**。
 *
 *  收方是注入侧挂在 AMS 广播入口上的 hook（`inject/KeyHooks.readBroadcast` → `inject/SystemToast`），
 *  它用系统 Context（包名 `android`，免限流）弹；它自己还带一层 1 秒去重（长按连发时同一句
 *  不刷屏）。hook 挂在 AMS 入口上、在那之前就看见整条 intent，所以 `setPackage` **不影响**它收。
 *
 *  **必须 `setPackage`**：这句话里有用户的应用名 / 命令名，裸着发就是一条谁都能注册接收的隐式广播
 *  （任意应用写个同名 action 的 receiver 就能读到）。
 *
 *  发不出去（没有 Context / 广播被拒）只记一行：提示丢了不报错 —— 它本来就是「顺带说一句」，
 *  不该因为它把执行路径的意思反过来说成失败。
 */
internal fun requestSystemToast(
    context: Context?,
    text: String,
) {
    if (context == null || text.isEmpty()) return
    runCatching {
        context.sendBroadcast(
            Intent(ModuleWire.ACTION_TOAST)
                .setPackage(HostApp.PACKAGE)
                .putExtra(ModuleWire.EXTRA_TOAST_TEXT, text),
        )
    }.onFailure { XLog.w("提示没发出去（注入侧弹不了）：${it.javaClass.simpleName}", it) }
}
