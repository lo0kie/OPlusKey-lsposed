package com.lookie.opluskey.core.model

import java.util.Locale

enum class CommandKind(
    /** 界面上的叫法。 */
    val label: String,
    /** 存文件时用的标识。 */
    val id: String,
) {
    /** 单条命令，选中后直接执行。 */
    SINGLE("单条命令", "single"),

    /**
     * 多条命令，按顺序执行一串步骤。
     * 步骤是引用（动作写法或 `cmd:<名字>`），不是命令文本。
     * 枚举名与文件标识 `serial` 是持久化格式，改了认不出老数据。
     */
    SERIAL("多条命令", "serial"),
    ;

    companion object {
        /** 带 `Locale.ROOT`：文件格式解析不能受设备语言影响。 */
        fun fromId(id: String): CommandKind? =
            entries.firstOrNull { it.id == id.trim().lowercase(Locale.ROOT) }
    }
}

data class Command(
    val name: String,
    val kind: CommandKind,
    /** [CommandKind.SINGLE] 的命令文本。 */
    val single: String = "",
    /** [CommandKind.SERIAL] 的步骤（引用）。 */
    val steps: List<String> = emptyList(),
    /**
     * 步骤间隔（毫秒）。
     * `keyevent:` 同毫秒生效，广播类动作要跨进程几百毫秒落地，步骤之间需要间隔。
     * [CommandKind.SINGLE] 用不到它。
     */
    val stepDelayMs: Int = DEFAULT_STEP_DELAY_MS,
) {
    /** 写进 `plus_single` / `plus_double` / `plus_long` 的取值。 */
    val token: String get() = "$PREFIX$name"

    /**
     * 把「对 [from] 这条命令的引用」改成指向 [to]（改名时用）。
     *
     * 两条路都要看：多条命令的步骤列表，以及单条命令的正文（`cmd:<名字>` 在单条里同样成立）。
     * 正文一律**先 [Action.parse] 再判**，不做字符串替换 —— 否则一条正文里恰巧含 `cmd:旧名`
     * 字样的 shell 命令会被改坏。
     */
    fun renamedReference(from: String?, to: String): Command {
        if (from == null || from == to) return this
        val oldToken = "$PREFIX$from"
        return when (kind) {
            CommandKind.SINGLE -> {
                val a = Action.parse(single)
                if (a is Action.Custom && a.name == from) copy(single = "$PREFIX$to") else this
            }
            CommandKind.SERIAL -> if (oldToken in steps) {
                copy(steps = steps.map { if (it == oldToken) "$PREFIX$to" else it })
            } else {
                this
            }
        }
    }

    /**
     * 摘掉「对 [token] 这条命令的引用」（删命令时用）：多条命令的步骤直接摘掉那一步，
     * 单条命令的正文改成 `none`（不能被删的正文变成 shell 命令跑出去）。
     */
    fun dropReference(token: String): Command = when (kind) {
        CommandKind.SINGLE -> {
            val a = Action.parse(single)
            if (a is Action.Custom && a.token == token) copy(single = Action.None.token) else this
        }
        CommandKind.SERIAL -> if (token in steps) copy(steps = steps.filterNot { it == token }) else this
    }

    companion object {
        /** 自定义命令在配置里的前缀；全项目只在这里定义一次，[Action] 一侧统一引用。 */
        const val PREFIX = "cmd:"

        /** 步骤间隔默认值（毫秒）：400ms 实测够广播类动作跨进程落地，又不至于让多步链太慢。 */
        const val DEFAULT_STEP_DELAY_MS = 400

        /**
         * 步骤间隔下限（毫秒）。
         *
         *  不给 0：`keyevent:` 是同毫秒生效的，间隔为 0 时前一步还没落地下一步就发出去了，
         * 两步会挤在一起（多条命令里「返回 → 最近任务」这类组合尤其明显）。
         * 读配置时也按它钳制，所以老配置里低于 100 的值会被抬上来。
         */
        const val MIN_STEP_DELAY_MS = 100

        /** 步骤间隔上限（毫秒），与连续触发间隔一致。 */
        const val MAX_STEP_DELAY_MS = 3000

        /** 多条命令的步骤数上限；执行侧按文件里实际步数执行。 */
        const val MAX_STEPS = 5
    }
}
