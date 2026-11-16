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
) {
    /** 某个应用在前台；取值为包名。 */
    APP("app"),

    /**
     * **选中的应用都不在前台**；取值同样是包名（`包1||包2`），判定与 [APP] **相反**。
     *
     *  与 [APP] 是同一族的两面（[isAppFamily]）：取值写法、挑应用页、多选、行内文案全部共用
     *  一份，差别只在判定那一步 —— [APP] 要求「前台是其中之一」，这一档要求「前台哪一个都不是」。
     *  所以界面上凡是「这一档要不要挑应用」的地方都问 [isAppFamily]，不要再各写一遍
     *  `kind == APP || kind == APP_NOT`。
     *
     *  读不到前台应用时**不成立**：判不出不能当成「不是」，否则探针失败时一条本该沉默的规则
     *  会在所有界面上乱接管（与 `ConditionProbe` 头部那条契约同一句）。
     */
    APP_NOT("app_not"),

    /**
     * **屏幕状态**：`on`（亮屏且没锁）/ `off`（灭着）/ `locked`（亮着且锁着）。原来是「亮屏」
     * 「锁屏」两个**独立类型**，合成一档带取值。
     *
     *  这三档**互斥**，合起来覆盖所有情况 —— 判据见 `ConditionChecks.screenMatches`：
     *  两个探针（`isInteractive` / `isKeyguardLocked`）一起才切得开，`off` 只看前者，
     *  `locked` 要求后者为真、`on` 要求后者为假。
     *
     *  互斥是**界面判据的前提**：一块里这一档最多加两条（`choices.size - 1`，见 `blockKindsOf`），
     *  两条之间是**「或」**（`[亮屏, 熄屏]` = 没停在锁屏界面）；三条全加就是恒成立、等于没写。
     *  而 `[熄屏]` 与 `[锁屏]` 是两块**不同**的场景，不能当成同一块
     *  （见 `BlockRules.duplicateBlockIndexes`）—— 别把它们按「同一档条件」合并。
     *  探针读不到时按「判不出」处理，三档都不成立。
     *
     *  ## 这个顺序就是选择器里的自上而下
     *
     *  `on` → `locked` → `off`：先亮着的两种（先在用、再锁着），最后才轮到灭屏。判据上三档
     *  互斥、与顺序无关（见 `ConditionChecks.screenMatches`），这里排的是**读的顺序**。
     */
    SCREEN("screen", choices = listOf("on", "locked", "off"), defaultChoice = "on"),

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
    WIFI("wifi", choices = listOf("on", "off"), defaultChoice = "on"),

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
    BLUETOOTH("bluetooth", choices = listOf("on", "off")),

    /**
     * 移动数据**开关状态**：`on` / `off`。读 `Settings.Global.mobile_data`。
     *
     *  注意它和「飞行模式」不是互补：飞行模式开着时移动数据也可能是 1（只是没网）。
     *  要表达「现在没网」应当两条都判，别拿其中一条取反。
     */
    CELLULAR("cell", choices = listOf("on", "off")),

    /** 飞行模式**开关状态**：`on` / `off`。读 `Settings.Global.airplane_mode_on`。 */
    AIRPLANE("airplane", choices = listOf("on", "off")),

    /**
     * **手电筒状态**：`on`（开着）/ `off`（关着）。
     *
     *  读 `Settings.Secure.flashlight_enabled` —— SystemUI 在 torch 回调里同步的那一位
     *  （`1` 开 / `0` 关，实测见 `docs/device/actions/toggles.md` 23.1）。宿主直接读得到，
     *  不用开相机、也不用 root。
     *
     *  这个键在第一次开灯之前可能根本不存在，那时读不到 ⇒ 按「判不出」处理，两档都不成立
     *  （见 `ConditionProbe` 头部那条契约）。
     */
    FLASHLIGHT("flashlight", choices = listOf("on", "off")),
    ;

    /**
     * **应用那一族** —— [APP] / [APP_NOT] 两档。
     *
     *  这两档的取值是一组包名（`包1||包2`）、挑应用页是同一张、行内按框排应用名也是同一套，
     *  只有判定那一步相反。界面上一律问这一句，别在两处各写一遍 `== APP || == APP_NOT` ——
     *  漏掉一处时的表现是「新那一档点进去弹出一张空的取值弹窗」，不报任何错。
     */
    val isAppFamily: Boolean get() = this == APP || this == APP_NOT

    companion object {
        fun fromId(id: String): ConditionKind? {
            //  与 [CommandKind.fromId] 同一条归一化（`trim` + `Locale.ROOT` 小写）：
            //  两处口径不同时，手改成大写 / 带空格的 id 会一边认得出、另一边认不出。
            val key = id.trim().lowercase(Locale.ROOT)
            return entries.firstOrNull { it.id == key }
        }

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
     * 应用那一族（[ConditionKind.isAppFamily]）那一条里的**全部包名**（其余档 / 空取值返回空表）。
     *
     *  一条应用条件可以**多选**：`app:包1||包2`，判定时**满足其一**即通过（「或」）；
     *  「前台应用以外」那一档是同一组包名的反面（都不在前台才成立）。
     *  分隔符见 [APP_SEPARATOR] —— 包名只含字母数字下划线与点，不会撞上 `|`，不必转义。
     */
    val appPackages: List<String>
        get() =
            if (kind?.isAppFamily == true) {
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

        /**
         * 一条应用条件里**最多**几个包名 —— 25。
         *
         *  挑应用页的多选闸门读它（超了只提示、不选中）。取 25 是按「这条条件在条件表那一格里
         *  还要看得懂」定的：再多，`包1||包2||…` 在界面上只剩一串省略号，判定也是逐条比。
         */
        const val MAX_APP_PACKAGES = 25

        fun app(pkg: String): BranchCondition = BranchCondition(ConditionKind.APP.id, pkg)

        /** 多选版：几个包名装进同一条条件（判定为「或」）。 */
        fun apps(packages: List<String>): BranchCondition = apps(ConditionKind.APP, packages)

        /**
         * 同上，种类由调用点给 —— 挑应用页那一趟写回时要知道自己是在配「前台应用」还是
         * 「前台应用以外」（见 `AppPickerState.branchKind`）。
         */
        fun apps(
            kind: ConditionKind,
            packages: List<String>,
        ): BranchCondition = BranchCondition(kind.id, packages.filter { it.isNotBlank() }.joinToString(APP_SEPARATOR))

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

/**
 * 一条 `shell:` 命令跑完之后，**把返回结果（标准输出）显示到哪** —— 每条命令自己的一档。
 *
 *  四档都是「用户看得见」的落点，只是打扰程度不同；[NONE] 是默认值（不显示），
 *  老配置里缺这一位也落在这里 —— 一条命令跑完就安静地结束，与这一版之前的行为一致。
 *
 *  **只对 `shell:` 有意义**：别的写法（`toast:` / `notify:` / `fluid:` / `keyevent:` …）本身就在
 *  「显示一句话」，没有 stdout 可回显（判据与「以 root 执行」同一个 `Action.usesRoot`）。
 *
 *  多条命令的每一步都是 shell，但**没有逐档配置**（步骤只存写法，见 `CommandKind.SERIAL`）：
 *  它的输出归整条命令那一档（[Command.output]），执行侧只取这一位。
 */
enum class CommandOutput(
    /** 存文件时用的标识。 */
    val id: String,
) {
    /** 不显示（默认）。 */
    NONE("none"),

    /** 系统提示（`toast:` 那一档的落点）—— 最轻，一句话闪一下。 */
    TOAST("toast"),

    /** 通知（`notify:` 那一档的落点）—— 留在通知中心，长输出也读得全。 */
    NOTIFY("notify"),

    /** 流体云（`fluid:` 那一档的落点）—— 状态栏那一颗，适合「结果就一行」的场景。 */
    FLUID("fluid"),

    ;

    companion object {
        /** 老配置 / 缺键时落在哪一档。 */
        val DEFAULT = NONE

        /** 带 `Locale.ROOT`：文件格式解析不能受设备语言影响；认不出的也落回默认档。 */
        fun fromId(id: String?): CommandOutput = entries.firstOrNull { it.id == id?.trim()?.lowercase(Locale.ROOT) } ?: DEFAULT
    }
}

/**
 * 「这次执行的结果送到哪」—— 执行入口那一路要的两个值（落点 + 流体云那一档的图标）。
 *
 *  收成一个值而不是两个平级参数：它们总是一起传（`execShell` 那条 lambda 已经 4 个位置参数了），
 *  分开传的话每加一个落点的附加项都要再改一遍所有调用签名。
 */
data class OutputSink(
    val mode: CommandOutput = CommandOutput.NONE,
    /** 只有 [CommandOutput.FLUID] 读它；空串 = 胶囊不带图标。 */
    val icon: String = "",
)

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
    /**
     * 这条命令**要不要以 root 执行** —— 只有 `shell:` 那一族用得上的**每条命令自己的**开关。
     *
     *  `true`（默认）= 只走 `su`，拿不到 root 就是这次执行失败；`false` = 只走 `sh`，不升权。
     *  **两档互不兜底**：`su` 起不来不会偷偷换成 `sh` —— 换了执行身份却不告诉用户，
     *  同一份配置在两台机器上就成了两种结果。
     *  默认 `true` 是为了让老配置仍落在「以 root 执行」那一档（文件里缺这个键也一样）。
     *  多条命令的步骤不读这一位：步骤没有各自的开关（见 `CustomCommandRunner`）。
     */
    val root: Boolean = DEFAULT_ROOT,
    /**
     * 这条命令**要不要经 Shizuku 执行** —— 与 [root] 同一族（只有 `shell:` 用得上、每条命令自己的）。
     *
     *  打开时不走 `su` / `sh`，而是请**模块自己的进程**用 Shizuku（借 adb 权限）跑这条命令 ——
     *  没 root 的机器也能执行。此时 [root] 不再参与（两条通道互斥，界面上也说清这一点）。
     *
     *  **为什么必须回到模块进程执行**：Shizuku 的授权按 **uid** 记（本机模块 10537、
     *  宿主 `com.oplus.gesture` 10277）。宿主进程里 `checkSelfPermission` 永远是拒绝，
     *  它也没有 Activity 可以发起授权 —— 所以侧键触发时只能把命令转给模块进程去跑。
     *
     *  默认 `false`：老配置与没勾这一档的命令一律走原来的 `su` / `sh`。
     */
    val viaShizuku: Boolean = false,
    /**
     * 这条命令跑完之后**把返回结果显示到哪** —— 与 [root] / [viaShizuku] 同一族（每条命令自己的一档）。
     *
     *  内容是该次执行的**标准输出**（整段、去首尾空白）；没有输出就什么都不弹 ——
     *  「成功但没说话」与「失败了」本来就该长得不一样，失败那一侧另有失败提示。
     *
     *  默认 [CommandOutput.NONE]：老配置与没配这一档的命令一律安静结束。
     */
    val output: CommandOutput = CommandOutput.DEFAULT,
    /**
     * [CommandOutput.FLUID] 那一档用的**胶囊图标**（`FluidIcons` 的 id，空串 = 不带图标）。
     *
     *  只在 `output == FLUID` 时有意义：流体云那颗胶囊左边要画一颗字形，而执行侧拿到的是命令的
     *  输出文本，没有别的地方能告诉它画哪颗。
     */
    val outputIcon: String = "",
) {
    /** 这次执行的结果送到哪（落点 + 流体云那一档的图标）—— 执行侧只认这一份。 */
    val outputSink: OutputSink get() = OutputSink(output, outputIcon)

    /**
     * 写进配置文件 `single` / `double` / `long` 那一档的取值。
     *
     *  这几个 `id` 与配置里的 `single` / `double` / `long` 三个键**同字面量但无关**：那三个是「手势相位」
     *  的键名，这三个是「命令类型」。别顺手并成一个 —— 它们各自会变。
     */
    val token: String get() = "$PREFIX$name"

    /**
     * 这条命令**有没有内容可跑**（单条正文非空 / 多条至少一步）。
     *
     *  执行侧与界面侧共用这一份判据：按键派发与「测试」按钮都要在**跑之前**
     *  认出「这是一条空命令」—— 两处各写一遍的话，总有一处会把空命令当成「跑完了但没动静」，
     *  或者反过来报成「这条命令已经不在了」（那是「库里查不到」，是另一件事）。
     */
    val hasContent: Boolean
        get() =
            when (kind) {
                CommandKind.SINGLE -> single.isNotBlank()
                CommandKind.SERIAL -> steps.isNotEmpty()
            }

    companion object {
        /** 自定义命令在配置里的前缀；全项目只在这里定义一次，[Action] 一侧统一引用。 */
        const val PREFIX = "cmd:"

        /**
         * [Command.root] 的默认值 —— 「以 root 执行」。
         *
         *  单拎出来不只是为了少写一个 `true`：界面在「这条命令用不上这个开关」时**不写这一位**，
         *  落盘与比「改过没改过」都拿它当那一档的取值（见 `draftToLibrary`）。与默认值各写一遍的话，
         *  以后改默认方向时会漏掉界面那一处。
         */
        const val DEFAULT_ROOT = true

        /**
         * 步骤间隔默认值（毫秒）：300。
         *
         *  这个数决定两件事：前一步来不来得及落地，以及多步链有多慢（n 步整条 = (n-1) 倍它）。
         *  广播类动作跨进程落地只有几十毫秒，300 留了几倍余量；五步的链也不过 1.2 秒。
         */
        const val DEFAULT_STEP_DELAY_MS = 300

        /**
         * 步骤间隔下限（毫秒）。
         *
         *  不给 0：`keyevent:` 是同毫秒生效的，间隔为 0 时前一步还没落地下一步就发出去了，
         * 两步会挤在一起（多条命令里「返回 → 最近任务」这类组合尤其明显）。
         * 读配置时也按它钳制，所以老配置里低于 100 的值会被抬上来。
         */
        const val MIN_STEP_DELAY_MS = 100

        /**
         * 步骤间隔上限（毫秒）：1500。
         *
         *  再长就不是「等一步」而是「等一段」了 —— 那种需求真正的解法是把流程拆成几条命令，
         *  而不是让某一步空等两秒。**与连续触发间隔不是同一个上限**：那个管「按住不放多久重复一次」，
         *  节奏由手指定；这个管「两步之间隔多久」，节奏由链的结构定，两者不必对齐。
         */
        const val MAX_STEP_DELAY_MS = 1500

        /** 多条命令的步骤数上限；执行侧按文件里实际步数执行。 */
        const val MAX_STEPS = 9

        /**
         * 命令库里**每一类**最多几条 —— 50（单条与多条各算各的）。
         *
         *  两类分开计：大多数人只写单条，写多条的通常只录几条流程 —— 共用一个池子会让
         *  一边挤掉另一边。50 是「这一页还翻得动」的量级。
         */
        const val MAX_COMMANDS = 50

        /**
         * 这份命令表里**哪一类**已经越过上限（没越过时 `null`）。
         *
         *  每类各算各的（见 [MAX_COMMANDS]）。**判据只有这一处**：界面那条保存出口（含导入覆盖）
         *  与导入那一刻（文件里可能装着 200 条）都要问同一句话 —— 各写一遍的话，
         *  导入超限的那份库会让之后**每一次保存**都被拒，而用户看不到「为什么」。
         */
        fun overLimit(list: List<Command>): CommandKind? =
            CommandKind.entries.firstOrNull { kind -> list.count { it.kind == kind } > MAX_COMMANDS }
    }
}

/**
 * `cmd:<名字>` 指向的是不是多条命令（[CommandKind.SERIAL]）；非 `cmd:` 或名字查不到一律 `false`。
 *
 *  这是一条**纯规则**（只吃 [Command] 列表、不碰磁盘），所以放在领域层：界面、广播发送侧、
 *  执行侧共用同一份口径。读盘那一步归 `core/config` 的 `CommandLibrary` ——
 *  分开之后 `core/wire` 与注入侧都不必再认识落盘那包（见 `docs/architecture/decoupling.md` 动作 3）。
 */
fun isSerialCommand(
    commands: List<Command>,
    token: String,
): Boolean {
    if (!token.startsWith(Command.PREFIX)) return false
    val name = token.removePrefix(Command.PREFIX)
    return commands.firstOrNull { it.name == name }?.kind == CommandKind.SERIAL
}

/**
 * 「执行时提示」那一句的**模板** —— 用户可改，六个占位符各填一段。
 *
 *  拆分之前那句是写死的 `%s → %s`（主语 → 动作名），主语又是 `键名 - 手势` 拼出来的；
 *  于是「换一种说法」「只留动作名」「加个动作前缀」都做不到。现在整句都是一个模板：
 *
 *  ```
 *  $key     键名（内置键走字典，自定义键是用户起的名字）
 *  $gesture 手势（单击 / 双击 / 长按 / 3 击 …）
 *  $action  动作名（整句：系统开关: WIFI - 打开）
 *  $parent  父动作（系统开关）；没有父动作时是空串
 *  $name    子动作（WIFI）；没有名字那一段时回落到整句
 *  $state   状态（打开）；没有状态时回落到**这一档自己的名字**（`媒体控制: 上一首` ⇒ `上一首`）
 *  ```
 *
 *  后三段是 `$action` 的**拆法**，不是另一套说法：它们与整句由同一个出口算出来
 *  （`CommandTitle.reading`），所以「整句」与「拆开」永远自洽。回落的判据在 [valueOf]：
 *  「截屏」这种没有父也没有状态的单一功能动作，`$name` 就是它自己、`$state` 也回落到它，
 *  模板只写其中一段时不会念出一条空提示。**回落不带父名** —— `$state` 答的是「这一下具体
 *  干了什么」，把 `媒体控制:` 一起端出来等于换了个 `$action`。
 *
 *  **默认模板就是拆分之前那一句**：老配置没有这一项时字段表补的也是它，升级前后弹出来的字一模一样。
 *
 *  占位符为什么用 ASCII 名字而不是中文：模板是要**打字**的，`$键名` 在中文输入法下要来回切；
 *  界面里那行说明写中文（框里是 `$key`、框后面是「键名」），两边各用各自顺手的写法。
 *
 *  单趟扫描替换（不是六次 `replace`）：替进去的值里若正好带着 `$action` 这种字样（用户就给自己
 *  那颗键起名叫 `$action`），不会再被展开一次。
 */
object ToastNotice {
    /** 键名。 */
    const val KEY = "\$key"

    /** 手势。 */
    const val GESTURE = "\$gesture"

    /** 动作名 —— 整句。 */
    const val ACTION = "\$action"

    /** 父动作。 */
    const val PARENT = "\$parent"

    /** 子动作。 */
    const val NAME = "\$name"

    /** 状态。 */
    const val STATE = "\$state"

    /**
     * 界面上要逐个列出来的占位符（顺序即说明顺序）。
     *
     *  界面把它当**变量清单**用：说明那一句里的 `%s` 按这个顺序换成这几个变量名
     *  （`ui/row/ChipLabel.ChipLegend`）。所以这里的顺序就是用户读到的那一行。
     */
    val PLACEHOLDERS: List<String> = listOf(KEY, GESTURE, ACTION, PARENT, NAME, STATE)

    /**
     * 默认模板 —— **必须与拆分之前那句逐字一致**：`侧键 - 单击 → 手电筒`。
     *
     *  这一句同时是「用户把模板删空」时退回的那一档，所以逗号 / 空格 / 箭头都别顺手改：
     *  改了就是所有没动过这项的人的提示语气都变一次。
     */
    const val DEFAULT_TEMPLATE = "$KEY - $GESTURE → $ACTION"

    /**
     * 按模板拼这一句提示。
     *
     *  @param template 用户写的模板（空 = 用 [DEFAULT_TEMPLATE]）
     *  @param action 这一次执行的动作（整句 + 拆开的三段，见 [ActionReading]）
     *  @return 拼好的整句；**不会是空串**（模板被删成空白时退回默认那句）。
     *    **认不出的占位符原样留在句子里**：那是用户自己打的字，替他改掉反而看不出打错了
     *    （要对照「有哪些占位符」看界面那一行变量清单，见 [PLACEHOLDERS]）。
     */
    fun render(
        template: String,
        key: String,
        gesture: String,
        action: ActionReading,
    ): String {
        val text = expand(template.ifBlank { DEFAULT_TEMPLATE }, key, gesture, action).trim()
        //  模板可以写得很短（例如只剩一个 `$action`），但也可能被删成空白 —— 那一下弹个空 Toast
        //  等于「按了没反应」，所以退回默认那句。
        return text.ifEmpty { expand(DEFAULT_TEMPLATE, key, gesture, action) }
    }

    /** 单趟扫描替换：只认 [PLACEHOLDERS] 里那几个 token，替进去的值不再二次展开。 */
    private fun expand(
        template: String,
        key: String,
        gesture: String,
        action: ActionReading,
    ): String {
        val out = StringBuilder(template.length + key.length + gesture.length + action.sentence.length)
        var i = 0
        while (i < template.length) {
            val hit = PLACEHOLDERS.firstOrNull { template.startsWith(it, i) }
            if (hit == null) {
                out.append(template[i])
                i++
                continue
            }
            out.append(valueOf(hit, key, gesture, action))
            i += hit.length
        }
        return out.toString()
    }

    /**
     * 一个占位符换成什么 —— 六个 token 的**唯一**取值处，含「那一段不存在时回落到什么」。
     *
     *  父动作没有就是**空串**：那一格是「前缀」，拿名字去顶会念成 `截屏: 截屏`。
     *  子动作 / 状态没有则回落到**这一档自己的名字**（叶子那一段，不是整句）：
     *  `媒体控制: 上一首` 这种两段式动作里 `$state` 就是 `上一首` —— 把父名一起端出来
     *  （`媒体控制: 上一首`）等于换了个 `$action`，用户要的是「这一下具体干了什么」。
     */
    private fun valueOf(
        token: String,
        key: String,
        gesture: String,
        action: ActionReading,
    ): String {
        //  「这一档自己的名字」—— 状态没有时它就是叶子那一段；连名字都空（认不出的手改配置）
        //  才退回整句，那已经是最后一档兜底了。
        val leaf = action.name.ifEmpty { action.sentence }
        return when (token) {
            KEY -> key
            GESTURE -> gesture
            ACTION -> action.sentence
            PARENT -> action.parent.orEmpty()
            NAME -> leaf
            STATE -> action.state ?: leaf
            else -> ""
        }
    }
}

/**
 * 「这一次执行的是什么」—— 整句与拆开的三段。
 *
 *  提示模板两种念法都要：`$action` 要整句（`系统开关: WIFI - 打开`），`$parent` / `$name` /
 *  `$state` 要拆开的各段。三段与整句由同一个出口算出来（`CommandTitle.reading`），
 *  所以它们**必然自洽** —— 分两次取的话，将来哪一处改了拼法，提示里就会出现
 *  「整句是 A、拆开是 B」。
 *
 *  @param sentence 整句（`$action` 念的那一段）
 *  @param parent 父动作名；`null` = 这一档自己就是那个动作
 *  @param name 子动作名；空串 = 没有名字那一段
 *  @param state 状态那一段；`null` = 这一档没有状态
 */
class ActionReading(
    val sentence: String,
    val parent: String?,
    val name: String,
    val state: String?,
)
