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
    /** 写进配置文件 `single` / `double` / `long` 那一档的取值（`plus_*` 是广播 extra 名）。 */
    val token: String get() = "$PREFIX$name"

    /**
     * 把「对 [from] 这条命令的引用」改成指向 [to]（改名时用）。
     *
     * 两条路都要看：多条命令的步骤列表，以及单条命令的正文（`cmd:<名字>` 在单条里同样成立）。
     * **两边都先 [Action.parse] 再判**，不做字符串替换 —— 否则一条正文里恰巧含 `cmd:旧名`
     * 字样的 shell 命令会被改坏。
     *
     * 注意：两条路必须同一把尺子（都走
     *  [references]，两边同一个 [Action.parse]）。步骤若走**精确串比**（`oldToken in steps`）、
     *  正文走 `parse`，两条路口径就不一致：步骤里带空白（`  cmd:旧名`，手改文件很容易带出来）
     *  时会漏改，改名之后那一步指向一条已经不在库里的命令，按下去永远 NOT_FOUND。统一走
     *  [references]，两边同一把尺子。
     */
    fun renamedReference(from: String?, to: String): Command {
        if (from == null || from == to) return this
        return when (kind) {
            CommandKind.SINGLE ->
                if (references(single, from)) copy(single = "$PREFIX$to") else this

            CommandKind.SERIAL ->
                if (steps.any { references(it, from) }) {
                    copy(steps = steps.map { if (references(it, from)) "$PREFIX$to" else it })
                } else {
                    this
                }
        }
    }

    /**
     * 摘掉「对 [token] 这条命令的引用」（删命令时用）：多条命令的步骤直接摘掉那一步，
     * 单条命令的正文改成 `none`（不能被删的正文变成 shell 命令跑出去）。
     *
     * 判据与 [renamedReference] 同源（都过 [Action.parse]），理由见那边。
     */
    fun dropReference(token: String): Command = when (kind) {
        CommandKind.SINGLE ->
            if (references(single, token.removePrefix(PREFIX))) copy(single = Action.None.token) else this

        CommandKind.SERIAL ->
            if (steps.any { references(it, token.removePrefix(PREFIX)) }) {
                copy(steps = steps.filterNot { references(it, token.removePrefix(PREFIX)) })
            } else {
                this
            }
    }

    /**
     * 这一条写法引用的是哪条命令（`cmd:<名字>` 才引用，其余一律 `null`）—— 全项目唯一的判据。
     *
     * 先 [Action.parse] 再取名字：`Action.parse` 会 trim、也会把 `cmd:` 认成 [Action.Custom]
     * 而不是兜底的 shell，所以「正文里恰好含 `cmd:名字` 字样的 shell 命令」不会被误认。
     */
    private fun referencedName(text: String): String? = (Action.parse(text) as? Action.Custom)?.name

    /** 这一条写法是不是「对 [name] 这条命令的引用」—— 见 [referencedName]。 */
    private fun references(text: String, name: String): Boolean = referencedName(text) == name

    /**
     * 这条命令引用了哪些别的命令。**两条路都看**：多条命令的步骤列表、单条命令的正文
     * （`cmd:<名字>` 在正文里同样成立）。判据与 [references] 同源 —— 都过 [referencedName]。
     */
    fun referencedNames(): List<String> = when (kind) {
        CommandKind.SINGLE -> listOfNotNull(referencedName(single))
        CommandKind.SERIAL -> steps.mapNotNull(::referencedName)
    }

    companion object {
        /** 自定义命令在配置里的前缀；全项目只在这里定义一次，[Action] 一侧统一引用。 */
        const val PREFIX = "cmd:"

        /**
         * 从 [start] 出发沿 `cmd:` 引用走一遍，走得回已经踩过的名字就是成环。
         *
         * [library] 要传**保存后**的那一份（含 [start] 自己的新定义）—— 判的是「存下去会怎样」，
         * 不是「现在文件里怎样」。引用照 [referencedNames] 取，与改名 / 删除用的是同一把尺子。
         *
         * 成环的后果不是「卡住」：执行侧有 `MAX_COMMAND_DEPTH` 兜底。真正的害处是**副作用重复** ——
         * 整条链会一路跑到深度上限才停，写 shell 的那种动作会真跑满 4~5 遍。
         *
         * @return 环上的名字、首尾同一个（如 `[A, B, A]`）；没有环返回 `null`
         */
        fun cyclePath(start: Command, library: List<Command>): List<String>? {
            val byName = HashMap<String, Command>(library.size + 1)
            for (c in library) byName[c.name] = c
            val path = ArrayList<String>()
            fun walk(name: String): List<String>? {
                val at = path.indexOf(name)
                if (at >= 0) return path.subList(at, path.size) + name
                path.add(name)
                val self = byName[name]
                if (self != null) {
                    for (ref in self.referencedNames()) walk(ref)?.let { return it }
                }
                path.removeAt(path.lastIndex)
                return null
            }
            return walk(start.name)
        }

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
