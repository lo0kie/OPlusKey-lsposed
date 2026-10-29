package com.lookie.opluskey.core.model

import java.util.Locale

enum class CommandKind(
    /** 存文件时用的标识。 */
    val id: String,
) {
    /** 单条命令，选中后直接执行。 */
    SINGLE("single"),

    /**
     * 多条命令，按顺序执行一串步骤。
     * 步骤是引用（动作写法或 `cmd:<名字>`），不是命令文本。
     * 枚举名与文件标识 `serial` 是持久化格式，改了认不出老数据。
     */
    SERIAL("serial"),

    ;

    companion object {
        /**
         * 「自定义命令」页**有页签**的那几档 —— 顺序即页签顺序，`CommandScreen` 的 pager
         * 与那条类型选择器带都读它。
         *
         *  条件执行**不是命令**（它是叠在按键绑定之上的一张顺序表，见 `KeyConfig.rules`），
         *  所以不在这张表里，也不在 `entries` 里。
         */
        val FORM_TABS: List<CommandKind> = listOf(SINGLE, SERIAL)

        /** 带 `Locale.ROOT`：文件格式解析不能受设备语言影响。 */
        fun fromId(id: String): CommandKind? = entries.firstOrNull { it.id == id.trim().lowercase(Locale.ROOT) }
    }
}

/**
 * 分叉能用的**条件种类** —— 存进配置的是 [id]，界面与执行两侧都从这里取，不各写一套字面量。
 *
 *  **每一档都要一个取值**（[choices] 里挑一档，或像「前台应用」那样自由取值）：界面上一次条件
 *  的选择因此天然是两级 —— 先选种类，再选那一档的值。没有取值的条件不放进这张表：
 *  它在一级菜单里会变成唯一一个落不到第二级的行。
 *
 *  [defaultChoice] 是**空取值时按哪一档解释**：只有「以前没有取值、后来才加上」的那几档有
 *  （屏幕 / 充电 / 耳机 / WIFI / 媒体）—— 老配置里写的是 `wifi` / `media`，升级后必须仍按
 *  当时那个意思命中，否则用户那几条规则会静默失效。其余档留空：空取值一律按「用不了」处理。
 *
 *  加一种条件 = 加一行 + 在执行侧补一个判据（`ConditionProbe`）+ 一条文案；
 *  线格式不动（`type` 本来就是字符串）。
 */
enum class ConditionKind(
    val id: String,
    /** 有取值时的候选档；空表 = 自由取值（只有「前台应用」那种）。 */
    val choices: List<String> = emptyList(),
    /** 空取值时按哪一档解释（空 = 没有默认档，空取值即「用不了」）。 */
    val defaultChoice: String = "",
    /**
     * 这几档的取值是不是**互斥且穷尽** —— 也就是「每个取值都有人接」就等于「这条恒成立」。
     *
     *  只有读 `Settings.Global` 的那几个开关键算：取不到时按 `0` 解释（见 `switchMatches`），
     *  所以任何时刻都恰好落在其中一档上。
     *
     *  其余档都**不是**：
     *   · 屏幕：熄屏且没有锁屏时 `on` / `locked` 两边都不成立；
     *   · 充电 / 耳机 / 媒体 / 响铃：系统服务取不到时两边都不成立（判不出就不成立，见 `ConditionProbe`）；
     *   · 方向：`ORIENTATION_UNDEFINED` 时两档都不成立。
     *
     *  这一位只给「死分支」判据用（见 `BranchDeadCause`）：拿它当闸，才不会把一条本来合法的
     *  兜底规则判成死的。
     */
    val exhaustive: Boolean = false,
) {
    /** 某个应用在前台；取值为包名。 */
    APP("app"),

    /** **屏幕状态**：`on`（亮屏）/ `locked`（锁屏）。原来是「亮屏」「锁屏」两档，合成一档带取值。 */
    SCREEN("screen", choices = listOf("on", "locked"), defaultChoice = "on"),

    /** **充电状态**：`charging`（充电中，含充满）/ `idle`（没在充电）。老配置没有取值 ⇒ 按 `charging` 解释。 */
    CHARGING("charging", choices = listOf("charging", "idle"), defaultChoice = "charging"),

    /**
     * **耳机状态**：`connected`（接着耳机）/ `disconnected`（没接）。
     *
     *  接线 / USB / 蓝牙 A2DP 都算「接着」。两档是真正的正反两面（设备要么在、要么不在），
     *  所以 `disconnected` 直接取反 —— 与屏幕那两档不同。
     *  老配置没有取值 ⇒ 按 `connected` 解释（那时这条条件本来就只表示「接着耳机」）。
     */
    HEADSET("headset", choices = listOf("connected", "disconnected"), defaultChoice = "connected"),

    /** 媒体**播放状态**：`playing` / `paused`（老配置没有取值 ⇒ 按 `playing` 解释）。 */
    MEDIA("media", choices = listOf("playing", "paused"), defaultChoice = "playing"),

    /** Wi-Fi **开关状态**：`on` / `off`（老配置没有取值 ⇒ 按 `on` 解释）。 */
    WIFI("wifi", choices = listOf("on", "off"), defaultChoice = "on", exhaustive = true),

    /** 响铃状态等于取值（`normal` / `vibrate` / `silent`）。 */
    RINGER("ringer", choices = listOf("normal", "vibrate", "silent")),

    /** 屏幕方向等于取值（`portrait` / `landscape`）。 */
    ORIENTATION("orientation", choices = listOf("portrait", "landscape")),

    /**
     * **时间区间**：取值 `HH:mm-HH:mm`（见 [TimeRange]）。
     *
     *  与「前台应用」同属**自由取值**那一类（[choices] 空）：区间是两天里任选一段，列不出候选。
     *  界面上这一档不走二级取值菜单，点下去弹时间选择器（见 `BranchTimeRangeDialog`）。
     *
     *  `start > end` 表示跨午夜（`22:00-07:00`）—— 这是这一档唯一需要当心的地方，判定与解析
     *  都在 [TimeRange] 里，不要在别处再写一遍比较。
     */
    TIME("time"),

    /** 蓝牙**开关状态**：`on` / `off`。读 `Settings.Global.bluetooth_on`，与 WIFI 同一条路。 */
    BLUETOOTH("bluetooth", choices = listOf("on", "off"), exhaustive = true),

    /**
     * 移动数据**开关状态**：`on` / `off`。读 `Settings.Global.mobile_data`。
     *
     *  注意它和「飞行模式」不是互补：飞行模式开着时移动数据也可能是 1（只是没网）。
     *  要表达「现在没网」应当两条都判，别拿其中一条取反。
     */
    CELLULAR("cell", choices = listOf("on", "off"), exhaustive = true),

    /** 飞行模式**开关状态**：`on` / `off`。读 `Settings.Global.airplane_mode_on`。 */
    AIRPLANE("airplane", choices = listOf("on", "off"), exhaustive = true),
    ;

    companion object {
        fun fromId(id: String): ConditionKind? = entries.firstOrNull { it.id == id.trim() }

        /**
         * 老配置里的类型名 → 现在这一档 + 它当时那个取值。
         *
         *  只有**合并过**的那一档需要（亮屏 / 锁屏 → 屏幕状态）：老文件里写的是 `screen_on` /
         *  `locked` 两个**独立类型**、都没有取值。读的时候在这一层翻译成 `screen:on` /
         *  `screen:locked`，其余代码只认新写法 —— 用户不动它就一直能用，动过之后自然写成新写法。
         */
        private val LEGACY: Map<String, Pair<ConditionKind, String>> =
            mapOf(
                "screen_on" to (SCREEN to "on"),
                "locked" to (SCREEN to "locked"),
            )

        /** 按老名字翻译一次（不是老名字就原样返回）。 */
        fun normalize(
            type: String,
            value: String,
        ): Pair<String, String> {
            val legacy = LEGACY[type.trim()] ?: return type to value
            return legacy.first.id to legacy.second
        }
    }
}

/**
 * 分叉里的一个**条件** —— 「什么情况下走这一支」。
 *
 *  `type` + `value` 两个字段而不是每种条件一个类：条件会一种一种往上加，
 *  而线格式要稳定（用户机器上的文件）—— 加一种条件只是多一个 [ConditionKind]，
 *  老版本读到认不出的 `type` 时按「这条分支不命中」处理，不会把整条命令读坏。
 */
data class BranchCondition(
    val type: String,
    val value: String = "",
) {
    /** 认得出的那一档；`null` = 这个版本不认识的类型（多半是配置文件来自更新的版本）。 */
    val kind: ConditionKind? get() = ConditionKind.fromId(type)

    /**
     * 认得出、并且取值填了 —— 判不出/没填的条件**永不命中**（宁可走默认那一档，也不乱执行）。
     *
     *  取值看 [effectiveValue]：带 [ConditionKind.defaultChoice] 的那几档空着也算可用
     *  （老配置就是这么写的），其余档空取值即「用不了」。
     */
    val isUsable: Boolean
        get() = kind != null && effectiveValue.isNotBlank()

    /**
     * 真正拿去判定的取值 —— 空的时候退回这一档的默认值（见 [ConditionKind.defaultChoice]）。
     *
     *  为什么需要它：`wifi` / `media` 以前没有取值，老配置里就是光秃秃一个类型名。升级后
     *  线格式**不迁移**（不往用户文件里偷写 `wifi:on`），靠这一层在读取时按当时的意思解释。
     */
    val effectiveValue: String
        get() = value.ifBlank { kind?.defaultChoice.orEmpty() }

    /** 线格式里的写法：无取值是 `type`，有取值是 `type:value`（`app:包名` / `ringer:vibrate`）。 */
    val token: String get() = if (value.isBlank()) type else "$type:$value"

    /**
     * 「前台应用」那一条里的**全部包名**（非 APP 档 / 空取值返回空表）。
     *
     *  一条应用条件可以**多选**：`app:包1||包2`，判定时**满足其一**即通过（「或」）。
     *  分隔符见 [APP_SEPARATOR] —— 包名只含字母数字下划线与点，不会撞上 `|`，不必转义。
     */
    val appPackages: List<String>
        get() =
            if (kind == ConditionKind.APP) {
                value.split(APP_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

    companion object {
        /**
         * 一条「前台应用」条件里多个包名之间的分隔符 —— 双竖线，与「或」这个语义同形。
         *
         *  **语义是「或」**：任意一个在前台就算命中。与块内多条条件之间的「且」不是一回事 ——
         *  后者是「同时成立」，前者是「其中之一」，所以多选必须装在**同一条**条件里。
         *
         *  取两个字符而不是一个 `|`：单竖线在这套配置里另有含义（`AppTarget` 老写法里
         *  「目标 | 显示名」的分隔符），两种串虽然不在同一层，但人工看配置文件时容易读串。
         */
        const val APP_SEPARATOR = "||"

        fun app(pkg: String): BranchCondition = BranchCondition(ConditionKind.APP.id, pkg)

        /** 多选版：几个包名装进同一条条件（判定为「或」）。 */
        fun apps(packages: List<String>): BranchCondition =
            BranchCondition(ConditionKind.APP.id, packages.filter { it.isNotBlank() }.joinToString(APP_SEPARATOR))

        /**
         * 线格式 → 一个条件；认不出（类型不在枚举里 / 该带取值却没带）的返回 `null`。
         *
         *  只做很轻的归一：去首尾空白、空取值按「没带」处理。真正的「这个包名装没装」不在这里判
         *  —— 配置是跨设备导出的，这台机器上没装不等于那条规则写错了。
         */
        fun parse(raw: String?): BranchCondition? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val cut = text.indexOf(':')
            val rawType = if (cut < 0) text else text.substring(0, cut)
            val rawValue = if (cut < 0) "" else text.substring(cut + 1).trim()
            // 合并过的老类型在这里翻译一次（`screen_on` → `screen:on`，见 `ConditionKind.LEGACY`）
            val (type, value) = ConditionKind.normalize(rawType, rawValue)
            val condition = BranchCondition(type, value)
            return condition.takeIf { it.isUsable }
        }

        fun of(
            kind: ConditionKind,
            value: String = "",
        ): BranchCondition = BranchCondition(kind.id, value)
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
    /**
     * 写进配置文件 `single` / `double` / `long` 那一档的取值。
     *
     *  这几个 `id` 与配置里的 `single` / `double` / `long` 三个键**同字面量但无关**：那三个是「手势相位」
     *  的键名，这三个是「命令类型」。别顺手并成一个 —— 它们各自会变。
     */
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
    fun renamedReference(
        from: String?,
        to: String,
    ): Command {
        if (from == null || from == to) return this
        return when (kind) {
            CommandKind.SINGLE -> {
                if (references(single, from)) copy(single = "$PREFIX$to") else this
            }

            CommandKind.SERIAL -> {
                if (steps.any { references(it, from) }) {
                    copy(steps = steps.map { if (references(it, from)) "$PREFIX$to" else it })
                } else {
                    this
                }
            }
        }
    }

    /**
     * 摘掉「对 [token] 这条命令的引用」（删命令时用）：多条命令的步骤直接摘掉那一步，
     * 单条命令的正文改成 `none`（不能被删的正文变成 shell 命令跑出去）。
     *
     * 判据与 [renamedReference] 同源（都过 [Action.parse]），理由见那边。
     */
    fun dropReference(token: String): Command =
        when (kind) {
            CommandKind.SINGLE -> {
                if (references(single, token.removePrefix(PREFIX))) copy(single = Action.None.token) else this
            }

            CommandKind.SERIAL -> {
                if (steps.any { references(it, token.removePrefix(PREFIX)) }) {
                    copy(steps = steps.filterNot { references(it, token.removePrefix(PREFIX)) })
                } else {
                    this
                }
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
    private fun references(
        text: String,
        name: String,
    ): Boolean = referencedName(text) == name

    /**
     * 这条命令引用了哪些别的命令。**两条路都看**：多条命令的步骤列表、单条命令的正文
     * （`cmd:<名字>` 在正文里同样成立）。判据与 [references] 同源 —— 都过 [referencedName]，
     * 所以正文里的内置动作（`keyevent:` …）不算引用，只有 `cmd:<名字>` 才算。
     */
    fun referencedNames(): List<String> =
        when (kind) {
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
        fun cyclePath(
            start: Command,
            library: List<Command>,
        ): List<String>? {
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

/**
 * `cmd:<名字>` 指向的是不是多条命令（[CommandKind.SERIAL]）；非 `cmd:` 或名字查不到一律 `false`。
 *
 *  这是一条**纯规则**（只吃 [Command] 列表、不碰磁盘），所以放在领域层：界面、广播发送侧、
 *  执行侧共用同一份口径。读盘那一步归 `core/config` 的 `CommandLibrary` ——
 *  分开之后 `core/wire` 与注入侧都不必再认识落盘那包（见 `docs/decoupling.md` 动作 3）。
 */
fun isSerialCommand(
    commands: List<Command>,
    token: String,
): Boolean {
    if (!token.startsWith(Command.PREFIX)) return false
    val name = token.removePrefix(Command.PREFIX)
    return commands.firstOrNull { it.name == name }?.kind == CommandKind.SERIAL
}
