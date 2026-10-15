package com.lookie.opluskey.host.action

import android.os.Handler
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind

internal class CustomCommandRunner(
    /** 主线程 Handler，步骤间等待走它（不能用 `Thread.sleep`）。 */
    private val handler: Handler,
    /** 跑一条 shell 命令（含 KSU 别名翻译）。 */
    private val execShell: (String) -> Unit,
    /** 派发一个非 shell 的内置动作（第二参是当前自定义命令的嵌套深度，`cmd:` 引用时继续加深）。 */
    private val dispatch: (Action, Int) -> Unit,
    /** 展开失败时的通知出口。 */
    private val onFailure: (CommandFailure) -> Unit,
) {
    /**
     * 正在跑的多条命令（按命令名）—— 顶上两道闸都看它（见 [run]）：
     *  · 顶层（`depth == 0`）**非空即挡**：一次只跑一条命令链；
     *  · 任何深度都按名字再挡一道：同一条的重入（含手改出来的自引 / 互引）不许叠着跑。
     *
     * 用并发集合：占位与摘除不保证在同一个线程上（`postDelayed` 的续步在另一条路上）。
     */
    private val runningSerial: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet()

    /**
     * 执行一条自定义命令。
     *
     * @param depth 递归深度（界面只允许「多条引用单条」，手改文件可能成环，故须有上限）
     */
    fun run(
        name: String,
        depth: Int,
    ) {
        if (name.isBlank()) {
            // 空名不是「合法但什么都不做」：配置里写了空的 `cmd:`（手改坏了 / 老版本残留）时
            //  按下去应当有反馈，而不是静默无痕。
            XLog.w("自定义命令名为空（配置里写了空的 ${Command.PREFIX}），跳过")
            onFailure(CommandFailure.NOT_FOUND)
            return
        }
        if (depth > MAX_COMMAND_DEPTH) {
            XLog.w("自定义命令嵌套过深（>$MAX_COMMAND_DEPTH），停止展开：$name")
            onFailure(CommandFailure.TOO_DEEP)
            return
        }

        val cmd = CommandLibrary.loadCached().firstOrNull { it.name == name }
        if (cmd == null) {
            XLog.w("命令库里没有「$name」（可能已被删除 / 改名），跳过")
            onFailure(CommandFailure.NOT_FOUND)
            return
        }

        if (cmd.kind == CommandKind.SINGLE) {
            // 空命令也是「按下去什么都没发生」的一种，同样要说一声
            if (cmd.single.isBlank()) {
                XLog.w("自定义命令「$name」（单条）是空的，跳过")
                onFailure(CommandFailure.EMPTY)
                return
            }
            XLog.w("自定义命令「$name」（单条）→ ${cmd.single}")
            // 与多条步骤同一套语义（Action.parse）：keyevent: / intent: / app: / shortcut: /
            // cmd: / shell: 六种前缀写法在单条里同样成立；无前缀兜底成 shell（手写命令可省 shell:）。
            // 注意：这里必须 `depth + 1`（与 `runSteps` 里那条 `cmd:` 步骤同口径）：单条正文里的
            // `cmd:` 引用会在派发侧再进 `run()`，**是一次同步重入**；把 `depth` 原样传下去等于
            // 「每层都还是 0」，上面那道 `depth > MAX_COMMAND_DEPTH` 永远不成立 ——
            // 单条自引（A 的正文写 `cmd:A`）或互引（A→B→A）会一路同步递归到 StackOverflowError，
            // 每层还各读一次配置文件。传别的动作时这个数没人用，多 `+1` 不产生副作用。
            dispatch(Action.parse(cmd.single), depth + 1)
            return
        }

        if (cmd.steps.isEmpty()) {
            XLog.w("自定义命令「$name」（多条）一步都没有，跳过")
            onFailure(CommandFailure.EMPTY)
            return
        }

        // 多条命令的「同时只跑一条」闸，两道。
        //
        //  第一道按「有没有链在跑」挡，不是按名字：连着按两下（单击 / 双击都算）会让两条命令链
        //  **交错** —— 第 2 条的第 1 步插在第 1 条的第 2 步前面（间隔越长的链越明显）。
        //  只按名字挡的话，两条**不同的**多条命令照样同时跑（名字不同，谁都挡不住谁），
        //  闸就没达到它那句话。
        //  只看顶层（`depth == 0`）：一步写 `cmd:<另一条>` 是同一条链的嵌套，不是新的一次触发。
        //  嵌套那条还在跑、外层已经结束的那段尾巴，`runningSerial` 也非空 ⇒ 新的顶层触发照样被挡。
        //  闸设在这里而不是判定侧：判定侧分不出「上一次跑完没有」，只有展开器自己知道。
        if (depth == 0 && runningSerial.isNotEmpty()) {
            XLog.w("已有命令链在跑（${runningSerial.joinToString()}），「$name」这一下跳过（避免两条链交错）")
            onFailure(CommandFailure.OVERLOADED)
            return
        }

        //  第二道按名字挡：同一条命令的重入 —— 顶层那一种已经被上面那道挡掉了，这里主要盖住
        //  `cmd:` 自引 / 互引（手改命令库才写得出来的环）。
        if (!runningSerial.add(name)) {
            XLog.w("自定义命令「$name」（多条）还在跑，这一下跳过（避免两条命令链交错）")
            onFailure(CommandFailure.OVERLOADED)
            return
        }
        XLog.w("自定义命令「$name」（多条）共 ${cmd.steps.size} 步")
        try {
            runSteps(cmd.steps, 0, depth, cmd.stepDelayMs, name)
        } catch (e: Throwable) {
            // 同步段抛了：把占位还回去，否则这条命令从此再也跑不起来（永久被自己挡住）
            runningSerial.remove(name)
            throw e
        }
    }

    /**
     * 逐步执行多条命令：每步派发完等 [gapMs] 毫秒再走下一步。
     *
     * @param index 下一个要派发的步骤下标
     * @param depth 递归深度（`cmd:` 步骤引用另一条命令时加深）
     * @param gapMs 步骤间等待时长（毫秒，0 = 不等待）
     * @param name 正在跑的这条命令名 —— 跑完（含异常中止）时把它从 [runningSerial] 里摘掉
     */
    private fun runSteps(
        steps: List<String>,
        index: Int,
        depth: Int,
        gapMs: Int,
        name: String,
    ) {
        if (index >= steps.size) {
            finishSerial(name)
            return
        }

        val shell = shellStepText(steps[index])
        val next: Int
        if (shell != null && KsuCompat.isAlias(shell)) {
            // 注意：KSU 别名（截图 / 小布识屏）会被翻译成**原生调用**、不是真的 shell，必须**单独派发**。
            //  并进 `;` 拼接串的话，`runShell` 的前缀认领会把**整串**认成别名，
            //  后面那些真正要跑的 shell 步骤随整串一起被吞掉。
            XLog.w("  第 ${index + 1} 步（KSU 别名）→ $shell")
            execShell(shell)
            next = index + 1
        } else if (shell != null) {
            // 连续的 shell 步骤合并成一条（串行由 sh 自己保证）；**遇到别名就断开**，
            // 让别名那一步走上面那条单独派发的路（同上）。
            val merged = ArrayList<String>()
            var cursor = index
            while (cursor < steps.size) {
                val text = shellStepText(steps[cursor]) ?: break
                if (KsuCompat.isAlias(text)) break
                merged.add(text)
                cursor++
            }
            val joined = merged.joinToString("; ")
            XLog.w("  第 ${index + 1}~$cursor 步（shell，合并成一条）→ $joined")
            execShell(joined)
            next = cursor
        } else {
            val action = Action.parse(steps[index])
            XLog.w("  第 ${index + 1} 步 → ${action.token}")
            if (action is Action.Custom) {
                // 引用的若是**多条命令**，它那条链靠自己 `postDelayed` 续步 ⇒ 这一步不等它跑完
                //  就往下走（已知行为，不是漏写 await）。步骤菜单只给内置动作、写不出这种一步，
                //  只有手改命令库才会出现。
                run(action.name, depth + 1)
            } else {
                dispatch(action, depth)
            }
            next = index + 1
        }

        if (next >= steps.size || gapMs <= 0) {
            if (next < steps.size) {
                runSteps(steps, next, depth, gapMs, name)
            } else {
                finishSerial(name)
            }
            return
        }
        // 注意：这个回调体是**直接挂在宿主主线程 Looper 上**的，必须自己兜异常（与
        // `AppLauncher` 那两处 postDelayed 同一条纪律）：抛出去就是主线程未捕获异常，
        // 整页 / 整个宿主进程跟着倒。走 postDelayed 的那一步没有外层 dispatch 的
        // try/catch 兜着（`execShell` 也不是自身带壳的路径）。
        val posted =
            handler.postDelayed({
                try {
                    runSteps(steps, next, depth, gapMs, name)
                } catch (e: Throwable) {
                    // 这一条链到这里断了：把占位还回去（否则这条命令从此永久被自己挡住）
                    XLog.w("自定义命令续步失败（已中止这条链）：$name", e)
                    finishSerial(name)
                }
            }, gapMs.toLong())
        // 排不上（主线程 Looper 正在退）⇒ 这一枪永远不会响，占位就留在 `runningSerial` 里。
        //  必须在这儿归还：闸的第一道是「非空即挡」，漏掉一个占位会锁死**所有**多条命令
        //  （只按名字挡那会儿，漏一个只死它自己）。
        if (!posted) {
            XLog.w("续步排不上（主线程 Looper 要退了），中止这条链：$name")
            finishSerial(name)
        }
    }

    /** 一条多条命令跑完（或中止）—— 摘掉 [runningSerial] 里的占位。 */
    private fun finishSerial(name: String) {
        runningSerial.remove(name)
    }

    /**
     * 这一步若是 shell 命令则返回命令文本，否则 null。`cmd:` 开头的是引用必须排除，
     * 否则会被 `Action.parse` 兜底当成一条名叫 `cmd:我的命令` 的 shell 命令。
     */
    private fun shellStepText(step: String): String? =
        if (step.startsWith(Command.PREFIX)) null else (Action.parse(step) as? Action.Shell)?.command

    private companion object {
        /** 自定义命令展开最大深度 —— 界面不成环，但命令库是纯文本、可能被手改出环。 */
        const val MAX_COMMAND_DEPTH = 4
    }
}
