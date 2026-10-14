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
     * 执行一条自定义命令。
     *
     * @param depth 递归深度（界面只允许「多条引用单条」，手改文件可能成环，故须有上限）
     */
    fun run(name: String, depth: Int) {
        if (name.isBlank()) return
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
            // ⚠️ 这里必须 `depth + 1`（与 `runSteps` 里那条 `cmd:` 步骤同口径）：单条正文里的
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

        XLog.w("自定义命令「$name」（多条）共 ${cmd.steps.size} 步")
        runSteps(cmd.steps, 0, depth, cmd.stepDelayMs)
    }

    /**
     * 逐步执行多条命令：每步派发完等 [gapMs] 毫秒再走下一步。
     *
     * @param index 下一个要派发的步骤下标
     * @param depth 递归深度（`cmd:` 步骤引用另一条命令时加深）
     * @param gapMs 步骤间等待时长（毫秒，0 = 不等待）
     */
    private fun runSteps(steps: List<String>, index: Int, depth: Int, gapMs: Int) {
        if (index >= steps.size) return

        val shell = shellStepText(steps[index])
        val next: Int
        if (shell != null) {
            // 连续的 shell 步骤合并成一条（串行由 sh 自己保证）
            val merged = ArrayList<String>()
            var cursor = index
            while (cursor < steps.size) {
                val text = shellStepText(steps[cursor]) ?: break
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
                run(action.name, depth + 1)
            } else {
                dispatch(action, depth)
            }
            next = index + 1
        }

        if (next >= steps.size || gapMs <= 0) {
            if (next < steps.size) runSteps(steps, next, depth, gapMs)
            return
        }
        // ⚠️ 这个回调体是**直接挂在宿主主线程 Looper 上**的，必须自己兜异常（与
        // `AppLauncher` 那两处 postDelayed 同一条纪律）：抛出去就是主线程未捕获异常，
        // 整页 / 整个宿主进程跟着倒。走 postDelayed 的那一步没有外层 dispatch 的
        // try/catch 兜着（`execShell` 也不是自身带壳的路径）。
        handler.postDelayed({
            XLog.guard("自定义命令续步") { runSteps(steps, next, depth, gapMs) }
        }, gapMs.toLong())
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
