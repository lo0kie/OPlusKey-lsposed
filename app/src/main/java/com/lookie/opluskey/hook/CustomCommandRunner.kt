package com.lookie.opluskey.hook

import android.os.Handler
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.Command
import com.lookie.opluskey.config.CommandKind
import com.lookie.opluskey.config.CommandLibrary
import com.lookie.opluskey.config.ConfigStore

internal class CustomCommandRunner(
    /** 主线程 Handler，步骤间等待走它（不能用 `Thread.sleep`）。 */
    private val handler: Handler,
    /** 跑一条 shell 命令（含 KSU 别名翻译）。 */
    private val execShell: (String) -> Unit,
    /** 派发一个非 shell 的内置动作。 */
    private val dispatch: (Action) -> Unit,
    /** 展开失败时的通知出口。 */
    private val onFailure: (CommandFailure) -> Unit,
    /** 「执行时提示」的出口（全局开关 `GlobalConfig.toastOnRun`）—— 内容是命令名。 */
    private val onNotice: (String) -> Unit,
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
            XLog.i("自定义命令「$name」（单条）→ ${cmd.single}")
            // 「执行时提示」：与多条那一路同一套（判据是全局开关 `GlobalConfig.toastOnRun`，
            // 内容是命令名）。原来只有多条分支弹，单条这条路上按下去一直没动静
            //（2026-10-14 实证）。配置每次现读 —— 这里跑在宿主进程，读得到配置文件。
            if (ConfigStore.read().global.toastOnRun) onNotice(name)
            // 与多条步骤同一套语义（Action.parse）：keyevent: / intent: / app: / shortcut: /
            // cmd: / shell: 六种前缀写法在单条里同样成立；无前缀兜底成 shell（手写命令可省 shell:）。
            dispatch(Action.parse(cmd.single))
            return
        }

        if (cmd.steps.isEmpty()) {
            XLog.w("自定义命令「$name」（多条）一步都没有，跳过")
            onFailure(CommandFailure.EMPTY)
            return
        }

        XLog.i("自定义命令「$name」（多条）共 ${cmd.steps.size} 步")
        // 「执行时提示」开跑前弹一条（判据是全局开关 `GlobalConfig.toastOnRun`），内容是命令名；
        // 配置每次现读 —— 这里跑在宿主进程，读得到配置文件，注入侧读不到。
        if (ConfigStore.read().global.toastOnRun) onNotice(name)
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
            XLog.i("  第 ${index + 1}~$cursor 步（shell，合并成一条）→ $joined")
            execShell(joined)
            next = cursor
        } else {
            val action = Action.parse(steps[index])
            XLog.i("  第 ${index + 1} 步 → ${action.token}")
            if (action is Action.Custom) {
                run(action.name, depth + 1)
            } else {
                dispatch(action)
            }
            next = index + 1
        }

        if (next >= steps.size || gapMs <= 0) {
            if (next < steps.size) runSteps(steps, next, depth, gapMs)
            return
        }
        handler.postDelayed({ runSteps(steps, next, depth, gapMs) }, gapMs.toLong())
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
