package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.rom.RomProfile
import java.util.Locale

/**
 * 变量的**类型** —— 新建时选一次，之后**不许改**。
 *
 * ## 为什么强制类型
 *
 *  变量是给命令读写用的：`${序号}` 这种引用一旦可能突然从数字变成文字，命令那边就没法写
 *  （`+1` 到底加什么？）。所以类型是**变量的身份**，与名字一样定死；要换类型就删掉重建一个。
 *  这条约束落在两处：
 *
 *   · [Variable] 里 `type` 是 `val`，全项目没有任何「改类型」的入口；
 *   · 每次写值都过 [coerce]：不符合这一档的写法**拒收**（而不是悄悄转成别的）。这就是「强类型」。
 *
 *  存储格式里写的是 [id]（`string` / `number` / `boolean`），是跨版本契约，别改字面量。
 */
enum class VarType(
    val id: String,
) {
    /** 文本：原样存，引用时原样替换。 */
    STRING("string"),

    /**
     * 数字：十进制，内部一律按 `Double` 解析再归一成文本（整数写出来不带 `.0`）。
     *
     *  用 `Double` 而不是 `Long`：滑块、音量这类「带小数」的取值也得能存。代价是 `0.1 + 0.2`
     *  会有浮点尾数 —— 计数（`+=1`）这类整数用法是精确的，而带小数的累加本来也不该指望精确。
     */
    NUMBER("number"),

    /** 布尔：只认真假两个值（写入时 `1/0`、`on/off`、`yes/no` 也归一过来）。 */
    BOOLEAN("boolean"),
    ;

    /**
     * 把一个外来文本按这一档归一；**归一不了返回 `null`**（调用方据此拒绝写入并留日志）。
     *
     *  STRING 是恒等：文本变量什么都能存。
     */
    fun coerce(raw: String): String? =
        when (this) {
            STRING -> {
                raw
            }

            NUMBER -> {
                toNumber(raw)?.let(::formatNumber)
            }

            BOOLEAN -> {
                when (raw.trim().lowercase(Locale.ROOT)) {
                    "true", "1", "on", "yes" -> TEXT_TRUE
                    "false", "0", "off", "no" -> TEXT_FALSE
                    else -> null
                }
            }
        }

    /** `this + other` —— 只有 NUMBER 能算、STRING 能拼；其余返回 `null`（命令算失败）。 */
    fun add(
        base: String,
        other: String,
    ): String? =
        when (this) {
            STRING -> base + other
            NUMBER -> combine(base, other, ::plus)
            BOOLEAN -> null
        }

    /** `this - other` —— 只有 NUMBER 能做减法。 */
    fun sub(
        base: String,
        other: String,
    ): String? = if (this == NUMBER) combine(base, other, ::minus) else null

    /**
     * 这一档在 `!` 里怎么解释 —— 只有 BOOLEAN 有「取反」，其余返回 `null`。
     */
    fun negate(value: String): String? =
        if (this == BOOLEAN) {
            if (value == TEXT_TRUE) {
                TEXT_FALSE
            } else {
                TEXT_TRUE
            }
        } else {
            null
        }

    /** 两个数按 [op] 算一次，任一边不是数字就整体失败。 */
    private fun combine(
        base: String,
        other: String,
        op: (Double, Double) -> Double,
    ): String? {
        val a = toNumber(base) ?: return null
        val b = toNumber(other) ?: return null
        return formatNumber(op(a, b))
    }

    companion object {
        /**
         * 布尔变量的**存盘写法** —— 界面上显示「真 / 假」，存储与命令写法里只认这两个词。
         *
         *  放在这里而不是各界面自己写一份字面量：这两个词是协议值（见类注释「存储格式」），
         *  散开之后改一处漏一处的表现是「界面存了 `True`、执行侧读不到」。
         */
        const val TEXT_TRUE: String = "true"

        /** 见 [TEXT_TRUE]。 */
        const val TEXT_FALSE: String = "false"

        /** 按线格式里的 id 认一档；认不出返回 `null`（多半是文件来自更新的版本）。 */
        fun fromId(id: String): VarType? = entries.firstOrNull { it.id == id.trim().lowercase(Locale.ROOT) }

        private fun plus(
            a: Double,
            b: Double,
        ): Double = a + b

        private fun minus(
            a: Double,
            b: Double,
        ): Double = a - b

        /**
         * 文本 → 数字；`NaN` / 无穷大 / 空串都算「不是数字」。
         *
         *  只认十进制写法（`1e3` 也认，那是 `Double` 的语法）—— 特意**不认** `0x` 前缀：
         *  用户写 `0x10` 想表达 16 的话，按「不是数字」拒掉比悄悄当成 0 更好。
         */
        private fun toNumber(raw: String): Double? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val value = text.toDoubleOrNull() ?: return null
            return if (value.isNaN() || value.isInfinite()) null else value
        }

        /**
         * 数字 → 文本：整数不带 `.0`（`3` 而不是 `3.0`），其余按 `Double` 的默认写法。
         *
         *  超过 `1e15` 的整数不再转 `Long`（那已经超出「人能读的计数」范围，转过去反而容易丢精度），
         *  原样按科学计数法写。
         */
        private fun formatNumber(value: Double): String {
            val whole = value == Math.floor(value) && Math.abs(value) < 1e15
            return if (whole) value.toLong().toString() else value.toString()
        }
    }
}

/**
 * 变量名 —— 不含语法字符即可，最长 [MAX_LENGTH] 个字符。
 *
 * ## 只挡「会被当成语法」的字符
 *
 *  引用写法是 `${名字}`、赋值写法是 `setvar:<名字><符号><值>`（见 [VarOp]），所以名字里出现
 *  `= + - ! $ : { }` 或空白时，这两种写法就切不开了（`a-=1` 会被读成「把 `a` 减 1」）。
 *  除这几个之外**不另设限制**：中文、日文、数字开头、中间带小数点都可以 —— 名字是给人看的，
 *  只该挡真正会坏事的字符。
 *
 *  这一条只影响「新建时填什么」，不影响已存的变量（老配置里那些 ASCII 名字照样能用）。
 */
object VariableName {
    /** 名字最多多少个字符。 */
    const val MAX_LENGTH: Int = 32

    /** 语法字符与空白 —— 名字里出现任何一个都不合法（见类注释）。 */
    private val FORBIDDEN = Regex("[\\s=+\\-!$:{}]")

    /** 这个名字能不能用（首尾空白不算数：` 序号 ` 与 `序号` 是同一个名字）。 */
    fun isValid(raw: String): Boolean = raw.trim().let { it.isNotEmpty() && it.length <= MAX_LENGTH && !FORBIDDEN.containsMatchIn(it) }

    /** 归一成存盘用的写法（去首尾空白）；不合法返回 `null`。 */
    fun normalize(raw: String): String? = raw.trim().takeIf(::isValid)
}

/**
 * 一个自定义变量：名字 + 类型 + **初始值** + 当前值（+ 开机要不要重置）。
 *
 *  **类型不许改**（见 [VarType] 的类注释）：这是身份，不是内容。
 *  值一律以**文本**存 —— 数字与布尔只是「文本 + 一条校验规则」，读的时候按 [type] 解释，
 *  这样存储格式只有一种，导出 / 导入 / 手改都不会因为类型不同而分叉。
 *
 * ## 初始值为什么是必填
 *
 *  · 有了它，「开机重置」才有地方回（见 [resetOnBoot]）；
 *  · 新建一份变量时也才知道「它一开始是什么」—— 空串对 NUMBER 是不合法的值。
 *
 *  所以 [initial] 也必须过 [VarType.coerce]；建的时候归一一次，之后它就是这一档的合法值。
 *
 * @param initial 初始值（已按 [type] 归一；开机重置 / 手动「恢复初始值」都回到这里）
 * @param value 当前值（同样已归一）
 * @param resetOnBoot 开机后是否自动把 [value] 拉回 [initial]（见 `VariableStore.applyBootResets`）
 */
data class Variable(
    val name: String,
    val type: VarType,
    val initial: String,
    val value: String = initial,
    val resetOnBoot: Boolean = false,
) {
    /** 名字合法、类型认得出、两个值都还是这一档的合法写法 —— 不满足的那条在执行侧按「不存在」处理。 */
    val isUsable: Boolean
        get() = VariableName.isValid(name) && type.coerce(initial) == initial && type.coerce(value) == value

    /** 这个值在**布尔**语境下算不算真（只有 BOOLEAN 变量会用到；其余档恒为 false）。 */
    val isTrue: Boolean get() = type == VarType.BOOLEAN && value == VarType.TEXT_TRUE

    /** 回到初始值（开机重置与「恢复初始值」共用这一处）。 */
    fun reset(): Variable = if (value == initial) this else copy(value = initial)

    companion object {
        /**
         * 最多几个变量 —— 50。
         *
         *  变量页是一列行、每行点开一层菜单，50 个已经要滚好几屏；再多，那页就不再是
         *  「我定义过哪几个」而是一张要检索的表。闸在 [com.lookie.opluskey.core.config.VariableStore.add]
         *  上（导入整份也走同一条路），界面那颗「新建」只是提前拦一次。
         */
        const val MAX_COUNT: Int = 50

        /**
         * 新建一个变量；名字不合法 / 类型不认 / 初始值归一不了都返回 `null`（界面据此拦在提交前）。
         *
         *  新建是**唯一**给变量定类型的入口 —— 之后没有任何「改类型」的方法。
         */
        fun create(
            name: String,
            type: VarType,
            initial: String,
            resetOnBoot: Boolean = false,
        ): Variable? {
            val validName = VariableName.normalize(name) ?: return null
            val validInitial = type.coerce(initial.trim()) ?: return null
            return Variable(validName, type, validInitial, validInitial, resetOnBoot)
        }
    }
}

/**
 * 一次赋值里用的**运算符** —— 写在动作写法 `setvar:<名字><符号><值>` 里。
 *
 *  四种都在这一处定义，解析（`Action.parseVarSet`）与执行（[Vars.assign]）都从这里取，
 *  别处不许再写 `"+="` 这种字面量 —— 散开之后改一处漏一处的表现是「算了但没算」。
 */
enum class VarOp(
    /** 写进配文字符串里的那一段。 */
    val symbol: String,
) {
    /** 直接赋值（`=`）。 */
    SET("="),

    /** 加 / 拼接（`+=`）：NUMBER 相加、STRING 拼接、BOOLEAN 拒绝。 */
    ADD("+="),

    /** 减（`-=`）：只有 NUMBER。 */
    SUB("-="),

    /** 取反（`!`）：只有 BOOLEAN，后面不跟值。 */
    NOT("!"),
    ;

    companion object {
        /**
         * 从 `名字<符号>值` 里切出三段；认不出符号返回 `null`。
         *
         *  **按符号出现的位置认，不按符号种类试**：`setvar:msg=hi!` 里那个 `!` 在 `=` 后面，
         *  它是**值的一部分**（`hi!`），不是取反运算符。按种类先试 `!` 的话，名字会被切成
         *  `msg=hi` —— 那条动作存下去就读不回来（拼 token 的那一侧是直接拼的），
         *  下一次按这颗键什么都不做。
         *
         *  同一位置不会有歧义：四个符号的首字符互不相同，`+=` / `-=` 天然比它们的 `=` 更靠前。
         *  位置相同时取更长的那个（纯防御，当前四个符号构不成这种情况）。
         */
        fun split(body: String): Triple<String, VarOp, String>? {
            var picked: VarOp? = null
            var at = Int.MAX_VALUE
            for (op in entries) {
                val i = body.indexOf(op.symbol)
                if (i < 0) continue
                val longer = i == at && op.symbol.length > (picked?.symbol?.length ?: 0)
                if (i < at || longer) {
                    at = i
                    picked = op
                }
            }
            val op = picked ?: return null
            val name = body.substring(0, at)
            val rest = body.substring(at + op.symbol.length)
            //  `!` 后面不跟值：多写的部分当作「写法有问题」，不猜
            if (op == NOT && rest.isNotBlank()) return null
            return Triple(name.trim(), op, rest.trim())
        }
    }
}

/**
 * 变量被谁引用 —— 删一颗变量之前反查一遍，**别留下空引用**。
 *
 * ## 引用长什么样
 *
 *  1. 改变量：`setvar:<名字><符号><值>`（见 [VarOp]）—— 出现在绑定、命令的某一步、条件分支的动作里；
 *  2. 读变量：任意文本里的 `${名字}`（见 [Vars.expand]）—— 单条命令的正文、shell / toast 那一类
 *     动作的文本里都可能有。
 *
 * ## 查在哪儿
 *
 *  配置里那几处（这颗键的单击 / 双击 / 长按、它那几条条件分支、每个组合键的短按 / 长按）
 *  加上**整份命令库**（每条命令的正文与每一步）。
 *
 *  命令库按**整份**算，不按「有没有被哪一格引用」算：一条命令即使现在没绑在键上，它里面的
 *  `setvar:` 在配置好之后照样会跑 —— 那也是一处会被这次删除变成空引用的地方。
 */
object VariableRefs {
    /** 引用了 [name] 的地方有几处（0 = 可以安全删除）。 */
    fun count(
        name: String,
        config: Config,
        commands: List<Command>,
    ): Int = tokensOf(config, commands).count { it.references(name) }

    /** 这一份配置 + 命令库里**所有**可能带引用的文本（动作 token 与命令正文）。 */
    fun tokensOf(
        config: Config,
        commands: List<Command>,
    ): List<String> =
        buildList {
            //  动作位那份清单收在 `ConfigActions.actionTokens()` 一处：这里再抄一遍，
            //  就会变成「删命令认得组合规则、删变量不认得」那种各漏一处的局面。
            addAll(config.actionTokens())
            commands.forEach { command ->
                add(command.single)
                addAll(command.steps)
            }
        }

    /**
     * 这段文本里有没有指向 [name] 的引用。
     *
     *  两处判据都不做字符串替换、只看写法：`${名字}` 带右花括号收尾，所以 `${计数}` 不会误命中
     *  `${计数器}`；`setvar:` 那条按 [VarOp.split] 切名字，与执行侧同一个切法。
     */
    fun String.references(name: String): Boolean {
        if (name.isEmpty()) return false
        if (contains("\${$name}")) return true
        //  带空白的写法（`${ 计数 }`）在执行侧**照样展开**（`Variables.expand` 会把名字 trim 掉），
        //  所以这里也必须认：漏了它，删变量时弹窗放行，之后命令里就留下一个永远替换不掉的
        //  字面量 —— 正是这个类要防的「引用了却不知道」。
        if (contains("\${") && looseBraceRef(name)) return true
        val prefix = Action.SETVAR_PREFIX
        var at = indexOf(prefix)
        while (at >= 0) {
            val parsed = VarOp.split(substring(at + prefix.length))
            if (parsed?.first == name) return true
            at = indexOf(prefix, at + prefix.length)
        }
        return false
    }

    /** 花括号内的名字**按 trim 后比较** —— 与 `Variables.expand` 同一个口径。 */
    private fun String.looseBraceRef(name: String): Boolean {
        var at = indexOf("\${")
        while (at >= 0) {
            val end = indexOf('}', at + 2)
            if (end < 0) return false
            if (substring(at + 2, end).trim() == name) return true
            at = indexOf("\${", end + 1)
        }
        return false
    }
}

/**
 * 组合按键：**几颗键一起按下**（或先按住一颗、再按另一颗）时执行的动作。
 *
 * ## 触发方式是**身份**的一部分
 *
 *  一档组合只认**一种**触发方式（[mode]）：窗口内先后按下、先按住第一颗、先按住第二颗。
 *  三者互斥、不并存：换一种就是改这一档的 [mode]，不是再叠一条判据。
 *  同一组键眼下只能有一档（`ComboActions.addCombo` 按成员集合去重），所以「两种都要」现在做不到 ——
 *  要去重把 [mode] 也算进身份才行。
 *
 *  为什么不做成「一档同时认两种」：那样每档要带两条开关 + 一组「加过哪几种顺序」，判定侧
 *  还得再定义「两条同时成立时谁优先」，而这两种按法在时间轴上本来就会重叠（窗口与按住下限）。
 *  把它们拆成两档之后，一档只有一条判据、一套动作槽，界面也只剩一条「触发方式」。
 *
 * ## 成员数：界面最多三颗
 *
 *  [MIN_KEYS] = 2 是下限（一颗不叫组合），[MAX_KEYS] = 3 是**界面 / 收集**那一侧的上限，
 *  [MAX_JUDGE_KEYS] 才是判定、落盘、广播三处认的上限（宽松挡垃圾用）。
 *  两个上限分开：界面只造得出三颗，而判定侧从三颗起就走「多成员凑齐」那条路
 *  （`ComboDetector.multiFor`），再多也跑得通 —— 手改配置文件写进来的四颗以上照常生效。
 *
 * ## 按住那一档：**首键一颗 + 后续成员任意多颗**
 *
 *  按住那一档的 [keys] 是**有序**的：第一颗是**首键**（要先按住的那一颗），其余是后续成员。
 *  「谁先按住」不再是一个独立的模式位 —— 它就是把那颗键摆在 [keys] 第一位。
 *  于是「按住 A 后再按 B、C」与「按住 B 后再按 A、C」是**两档不同的组合**（首键不同），
 *  而「先按住第二颗」这个老模式在读配置时被折成「键序反转」（见 `ComboDto`）。
 *
 *  后续成员**没有顺序要求**：首键按住之后，它们在窗口内陆续按下就凑齐（判定见 `ComboDetector`）。
 *  三颗时先按 C 再按 B 与先按 B 再按 C 是同一档 —— 人手做不到「三根手指排好队落下」。
 *
 *  [MODE_HOLD_SECOND] 因此**只剩读旧配置这一个用途**：迁移之后配置里不会再写出它。
 *
 * ## 只支持短按与长按
 *
 * 双击这一档在组合键上不存在：判双击要的是「两次按压都落在同一个窗口内」，而组合键先要判
 * 「这几颗键同时按着」，两个窗口叠在一起没有能讲清楚的口径。所以 [actionOf] 对
 * [Gesture.DOUBLE] 恒返回 [Action.None] —— 不是「还没实现」，是这个档位在这里没有定义。
 *
 * ## 身份是 [keys] 这个集合，不是按下的顺序
 *
 * 两颗键「同时按」时，上报顺序由驱动决定、用户也控制不了。拿顺序当判据等于让配置依赖硬件细节，
 * 所以判等只看集合（[matches]）；**「谁先按住」不由顺序推导**，而是 [mode] 明写的那一位。
 *
 * ## 与单键的关系
 *
 * 参与组合的键同时也是普通的键：它们各自的单击 / 双击 / 长按照常在 `KeyConfig` 里配。谁先响应、
 * 要不要把单键那一下吞掉，是**判定侧**的事（不在本类）—— 这里只回答「这几颗键凑齐了是哪一档」。
 */
data class ComboKey(
    /** 稳定身份（`cmb1` `cmb2`）—— 与 `KeyId.id` 同一套用法：改成员不换身份。 */
    val id: String,
    /** 参与的键（`KeyId.id`，去重后 [MIN_KEYS]..[MAX_JUDGE_KEYS] 颗）。 */
    val keys: List<String>,
    val enabled: Boolean = true,
    /**
     * 这一档**怎么触发** —— [MODE_WAIT] / [MODE_HOLD]（外加只在读旧配置时出现的
     * [MODE_HOLD_SECOND]）。
     *
     *  · [MODE_WAIT]：成员在 [windowMs] 之内先后按下（默认，老配置全是这一档）；
     *  · [MODE_HOLD]：**先按住 [keys] 的第一颗**，其余成员在窗口内陆续按下。
     *
     *  按住那一档的判据是「首键还按着」，不看隔了多久；上界是它自己的长按（判过长按就已经
     *  执行过自己的动作，再认组合就是一次按压两个动作），下限是 [holdMinMs]（把「一起按」
     *  与「真的按住一颗再补另一颗」分开）。**后续成员只要求按下、不要求按住**。
     *
     *  「谁先按住」由 [keys] 的顺序表达，不是另一套模式位（见类注释）。
     */
    val mode: Int = MODE_WAIT,
    /** 短按：按下之后在 [longPressMs] 之内松开。 */
    val single: Action = Action.None,
    /** 长按：一起按住超过 [longPressMs]。 */
    val long: Action = Action.None,
    override val longPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
    /**
     * 「长按连续触发」—— 两颗键一起按住不放时，按 [longRepeatIntervalMs] 反复执行 [long]。
     *
     *  与单键那一档同一套语义（`KeyConfig.longRepeat`）：松手就停，抬手事件丢了有上限兜底。
     *  默认关：按住不放重复执行是个响得很大的行为，不该一开始就有。
     */
    override val longRepeat: Boolean = false,
    /** 连续触发的间隔（毫秒）—— 只在 [longRepeat] 为真时有意义。 */
    override val longRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    /** 连续触发时每一次重复都振动（关 = 只在判出长按那一下振一次）。 */
    override val longRepeatVibrate: Boolean = false,
    /**
     * 「长按**松手才触发**」—— 与单键 `KeyConfig.longFireOnRelease` 同一档语义。
     *
     *  开着时长按那一下不立即执行，抬手时补发一次；与 [longRelease] 互斥（同一刻只能有一个）。
     *  按住那两档**要求起点键开着这一位**：否则按住它到长按阈值时动作已经出去，组合再也凑不成。
     */
    override val longFireOnRelease: Boolean = false,
    /** 「松手执行额外动作」的开关 —— 与单键 `KeyConfig.longReleaseOn` 同一档语义。 */
    override val longReleaseOn: Boolean = false,
    /** 松手那一档执行的动作（`Action.None` = 没挑）—— 三种触发方式都可用，见 [releaseBound]。 */
    override val longRelease: Action = Action.None,
    /**
     * **系统场景** —— 与单键 `KeyConfig.scenes` 同一套 [SceneRule]、同一个判定口径。
     *
     *  按顺序找「条件成立、且这一档有值」的第一块；一块都不中才走 [actionOf] 那一档绑定。
     *  三种触发方式**同一套场景表**：块里分的是短按 / 长按，而「怎么按出来的」已经是这一档的
     *  身份，不构成场景的第二个维度。
     *
     *  手势只有 [Gesture.SINGLE] / [Gesture.LONG] 两档（见类注释），所以块里的 [SceneRule.double]
     *  在这条路上永远不会被读到；手改配置写进去也只是存着。
     */
    val scenes: List<SceneRule> = emptyList(),
    /**
     * 「这一档用自己那套公共项」—— 与单键 `KeyConfig.useOwnSettings` **同一个契约**
     * （[OwnSettings]），执行侧不区分单键还是组合。
     *
     *  默认关 = 跟随公共设置；开着时下面几位说了算。
     */
    override val useOwnSettings: Boolean = false,
    /** 这一档自己的「使用 ColorOS 接口」—— 只在 [useOwnSettings] 开着时算数。 */
    override val colorOsApi: Boolean = GlobalConfig.DEFAULT_COLOR_OS_API,
    /** 这一档自己的「振动反馈」—— 只在 [useOwnSettings] 开着时算数。 */
    override val vibrate: Boolean = false,
    /** 这一档自己的「振动强度」—— 只在 [useOwnSettings] 开着时算数。 */
    override val vibrateStrength: Int = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
    /** 这一组自己的「无操作时振动」—— 只在 [useOwnSettings] 开着时算数。 */
    override val vibrateOnNone: Boolean = GlobalConfig.DEFAULT_VIBRATE_ON_NONE,
    /** 这一档自己的「执行时提示」—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastOnRun: Boolean = GlobalConfig.DEFAULT_TOAST_ON_RUN,
    /** 这一档自己的「提示走流体云」—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastFluid: Boolean = GlobalConfig.DEFAULT_TOAST_FLUID,
    /** 这一档自己的「提示走通知」—— 只在 [useOwnSettings] 开着时算数（四档由 [noticeMode] 派生）。 */
    override val toastNotify: Boolean = GlobalConfig.DEFAULT_TOAST_NOTIFY,
    /** 这一档自己的提示文案模板（空串 = 默认那句）—— 只在 [useOwnSettings] 开着时算数。 */
    override val toastTemplate: String = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
    /** 这一档自己的「多条命令逐条提示」—— 只在 [useOwnSettings] 开着时算数。 */
    override val stepNotice: Boolean = GlobalConfig.DEFAULT_STEP_NOTICE,
    /**
     * 这一档的**四个窗口**（毫秒）—— 等待 / 按住 × 固定窗口起点 开 / 关，各存一份。
     *
     *  一个数同时管两种按法的两种时序，会把四件不同的事压成一件（完整理由见 `ComboWindows`）。
     *  四格都**受 `useOwnSettings` 管**：关着整份看公共；开着就看这四格自己的值，
     *  没写过的那几格取**出厂默认**（不抄公共值）。
     *  [WINDOW_FOLLOW_GLOBAL]（0）因此只剩老配置读到的「没写过」这一种含义。
     *  取值一律走 `Config.comboWindowsFor` —— 判定侧与界面都只走那一处，谁也不自己看全局。
     */
    val waitWindowMs: Int = WINDOW_FOLLOW_GLOBAL,
    /** 等待组合、固定窗口起点**关**：相邻两颗之间（n 颗整串 = 它 × (n-1)）。 */
    val waitPairMs: Int = WINDOW_FOLLOW_GLOBAL,
    /** 按住组合、固定窗口起点**开**：后续成员要在首个成员按下后的这个窗口内落完。 */
    val holdWindowMs: Int = WINDOW_FOLLOW_GLOBAL,
    /** 按住组合、固定窗口起点**关**：后续成员每颗各自往前等多久。 */
    val holdFollowerMs: Int = WINDOW_FOLLOW_GLOBAL,
    /**
     * 这一档的「**起点键要先按住多久**」（毫秒）—— 按住那两档的下限，[MODE_WAIT] 上不参与取值。
     *
     *  含义：起点必须**按满这么久、且还没抬手**，另一颗在这之后落下才算这一档组合。
     *  它把「一起按」（两颗键相差 0~40ms，谁先上报看硬件）与「先按住一颗再按另一颗」分开：
     *  没有这条下限时，一起按会按物理先后落进某一种顺序里 —— 而那个顺序未必是这一档要的，
     *  于是同一物理动作有时命中、有时整条落空。
     *
     *  **受 `useOwnSettings` 管**，与上面那四格同一条：关着整份看公共
     *  （`GlobalConfig.comboHoldMinMs`）；开着而没写过 ⇒ 出厂默认
     *  （`GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS`），不抄公共值。
     *  [HOLD_MIN_FOLLOW_GLOBAL]（0）因此只剩「没写过」这一种含义。
     *
     *  取值一律走 `Config.comboHoldMinFor`（判定侧与界面都只走这一处）。
     */
    val holdMinMs: Int = HOLD_MIN_FOLLOW_GLOBAL,
    /**
     * 这一档的**系统场景在什么时候定下来** —— `true` = 首键按下，`false` = 成员键按下（组合凑成）。
     *
     *  只在**按住**那两档上有意义；等窗口那一档没有「先按住哪一颗」，也不会收到首键那条通知。
     *
     *  `null` = 还没写过 ⇒ 出厂默认（`GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER`）：
     *  布尔没有第三个值，靠 `null` 表达「没写过」。
     *  取值一律走 `Config.comboSceneOnStarterFor`（独立设置关着就看公共那份）。
     */
    val sceneOnStarter: Boolean? = null,
    /**
     * 这一档开了「**首键松手后仍执行它自己的长按**」吗 —— `true` = 不丢弃。
     *
     *  原本的设计：组合凑成时成员键的单键状态机被摘掉（`ComboDetector.resetKey`），
     *  于是首键按多久都不会判出自己的长按 —— 一次按压只执行一个动作（组合的）。
     *  打开这一位之后，**首键松手时**补派一次它自己的长按动作（组合那一下照旧执行）。
     *
     *  **只补首键**：按住组合的模型是「首键一直按着、成员按一下就松」，成员自己有单键状态机、
     *  自己会判长按 —— 不需要谁替它补。首键则因为组合凑成时状态机被摘掉，那一次长按丢了。
     *
     *  只在**按住**那两档上有意义：等窗口那一档没有「首键按住」这回事，界面也不给这一行。
     *
     *  `null` = 还没写过 ⇒ 出厂默认（`GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG`）：
     *  布尔没有第三个值，靠 `null` 表达「没写过」。
     *  取值一律走 `Config.comboStarterKeepLongFor`。
     */
    val starterKeepLong: Boolean? = null,
    /**
     * 这一档是不是**按顺序触发**。
     *
     *  `false`（默认）= 只看集合：几颗键先后按下就凑齐，顺序不限。
     *  `true` = 按 [keys] 的顺序逐位比对：必须按那个先后，反了不成立。
     *
     *  · 等待档（[MODE_WAIT]）：比的是**整组**的按下顺序（`[A, B]` 与 `[B, A]` 是两档）。
     *  · 按住档（[MODE_HOLD]）：首键是 [keys] 第一位、天然最先按住，所以实际比的是**成员键**
     *    （[followers]）之间的先后 —— 成员在两个以上时，`[首键, A, B]` 与 `[首键, B, A]` 是两档。
     *
     *  它是这一档的**身份**（与 [mode] 同一层）：`keys` 的顺序在这一档里就是「按下的先后」，
     *  所以同一批成员键可以建多档（AB / BA / ABC / ACB …），「不分顺序」那一档（`ordered = false`）
     *  与它们并存。
     *
     *  判据在判定侧（`ComboDetector` 的按序比对），这里只存这一位。
     */
    val ordered: Boolean = false,
    /**
     * 这一档**等待组合**的固定窗口起点 —— `true` = 从首个成员按下时起一个固定窗口；
     * `false` = 每个成员按下都清掉计时、重新等一个窗口（逐次等满）。
     *
     *  窗口长度按四格里的哪一格由开关决定（[waitWindowMs] / [waitPairMs]）：开与关只是
     *  「整串共用一个」还是「每两颗各一个」的区别，不再是两个数各管一边。
     *
     *  `null` = 还没写过 ⇒ 出厂默认（`GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START`）。
     *  取值一律走 `Config.comboFixedWindowStartFor`。
     *
     *  **与 [holdFixedWindowStart] 是两个变量**：两张卡各管一种按法（口径与四格窗口同一条）。
     */
    val fixedWindowStart: Boolean? = null,
    /**
     * 这一档**按住组合**的固定窗口起点 —— `true` = 后续成员在首个成员按下后的一个窗口内落完；
     * `false` = 后续成员每颗各自往前等一格。
     *
     *  窗口长度同样按开关挑格（[holdWindowMs] / [holdFollowerMs]）。
     *
     *  `null` = 还没写过 ⇒ 出厂默认（`GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START`）。
     *  取值一律走 `Config.comboHoldFixedWindowStartFor`。
     */
    val holdFixedWindowStart: Boolean? = null,
    /**
     * **成员连击**：按住首键不放，那颗**唯一成员键**在窗口内连击（双击 / 三击 / 四击 /
     * 单击后长按）—— 动作落在**这一档组合自己**的这几格，与成员键单键的多击**完全解耦**。
     *
     *  只在 `mode == MODE_HOLD` 且 `followers.size == 1`（首键 + 一颗成员）时有意义：多成员时
     *  「哪一颗连击」没有定义。窗口与判定时机**复用按住组合已有的配置**（[holdFixedWindowStart]
     *  决定固定起点还是逐次等、[holdWindowMs] / [holdFollowerMs] 决定窗口长度），不再为它另开
     *  一套窗口字段（用户口径：窗口用按住组合的固定窗口起点和等待窗口）。
     *
     *  开关与动作分两格（与单键 `doubleClickOn` / `extraClicksOn` 同一族）：关掉只是不判定、
     *  不派发，配过的动作原样留着，再打开回来。
     *
     *  这一条就是**双击**那一档（`memberDouble`）；3 击起的几档是下面那张表。
     */
    val memberDouble: Action = Action.None,
    val memberDoubleOn: Boolean = true,
    /** 成员第 3 击起的几档（键 = 击数，值 = 动作）。 */
    val memberClicks: Map<Int, Action> = emptyMap(),
    /** 开着哪几档成员多击（第 3 击起）。 */
    val memberClicksOn: Set<Int> = emptySet(),
    /** 成员「单击后长按」那一档（成员第一击抬手后，窗口内再按下并按住）。 */
    val memberClickThenLong: Action = Action.None,
    val memberClickThenLongOn: Boolean = false,
    /** 成员「单击后长按」自己的长按阈值 —— 与这一档普通长按 [longPressMs] 分开。 */
    val memberClickThenLongPressMs: Int = KeyConfig.DEFAULT_LONG_PRESS_MS,
    /**
     * 成员「单击后长按」的**松手时触发** —— 与 [longFireOnRelease] 同义、各自一份。
     *
     *  开着时到点不执行、抬手执行一次；同时把「松手执行额外动作」与「连续触发」两族让位
     *  （判据收在 [memberClickThenLongReleaseAction] / [memberClickThenLongRepeats] 一处）。
     */
    val memberClickThenLongFireOnRelease: Boolean = false,
    /** 成员「单击后长按」的**连续触发**（按住不放重复派发这一档）。 */
    val memberClickThenLongRepeat: Boolean = false,
    val memberClickThenLongRepeatIntervalMs: Int = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
    /** 连发的每一次都振动（只在 [memberClickThenLongRepeat] 为真时有意义）。 */
    val memberClickThenLongRepeatVibrate: Boolean = false,
    /** 成员「单击后长按」松手那一刻再执行一个动作（与 [longReleaseOn] 各自一份）。 */
    val memberClickThenLongReleaseOn: Boolean = false,
    val memberClickThenLongRelease: Action = Action.None,
    /**
     * 成员「单击后长按」绑的是不是**多条命令** —— **只有广播这一列**（与单键那条同一条）：
     * 注入侧要读命令库才知道，而那个文件它看不到，由宿主现算带过来。
     */
    val memberClickThenLongIsSerial: Boolean = false,
) : OwnSettings,
    LongPressParams {
    /** 这一档现在算不算数 —— 关掉了、或成员不足两键（配置文件被手改坏）都不算。 */
    val isUsable: Boolean
        get() = enabled && keys.size >= MIN_KEYS

    /** 这一档靠「窗口内先后按下」触发吗（[MODE_WAIT]）。 */
    val isWait: Boolean get() = mode == MODE_WAIT

    /** 这一档靠「先按住一颗、再补其余」触发吗（[MODE_HOLD]）。 */
    val isHold: Boolean get() = mode != MODE_WAIT

    /**
     * 这一档**有没有长按**那一档（界面摆不摆「长按」页、判定排不排长按那一枪都问它）。
     *
     *  **按顺序触发的组合一律没有**（不分等待 / 按住）：
     *   · 等待档量的是**相邻两颗之间**的间隔（`Config.comboSpanFor`），整组按满多久在它身上
     *     不是一个问题；
     *   · 按住档一旦要求「成员按先后落下」，这一按的语义就是**那一串先后**本身 ——
     *     再叠一档「按住不放」等于给同一串动作配第二个结果，用户配它时也没有这个预期
     *     （用户口径：顺序组合只留单击）。
     *
     *  所以那一档在它身上连页都不摆（见 `GestureTrigger.comboSlots`），判定侧也不排长按枪
     *  （`ComboDetector.scheduleLong`）：留一页写进去也永远不会命中。
     *
     *  其余都算有：不分顺序的等待档、以及不分顺序的按住两档 —— 按住那两档的「首键一直按着」
     *  本来就靠长按收尾。
     */
    val hasLong: Boolean get() = !ordered

    /**
     * 这一档的**首键**（按住那一档才有）—— 就是 [keys] 的第一颗。窗口那一档回 `null`。
     *
     *  一颗按键能当几个按住档的首键？**不设限**：`A` 当首键的 `{A,B}` 与 `{A,B,C}` 是两档，
     *  它们各自的 [keys] 第一位都是 `A`。所以「首键」不是身份的唯一位，`(首键, 其余成员集合)`
     *  才是。
     */
    val starterKey: String?
        get() = if (isHold) keys.firstOrNull() else null

    /**
     * **后续成员**（按住那一档才有）—— 除首键之外的全部，按配置顺序排。
     *
     *  顺序**不进判据**：它们在窗口内陆续按下就凑齐（用户口径「成员没有顺序要求」）。
     *  存着顺序只为界面摆文案与日志好读。
     */
    val followers: List<String>
        get() = if (isHold) keys.drop(1) else emptyList()

    /**
     * **成员连击**那套开关 —— 只在「按住档 + 只有一颗成员」时有意义，其余回 `null`
     * （等待组合 / 多成员组合没有「哪一颗成员连击」这一说）。
     *
     *  界面按它拼手势带（`GestureTrigger.comboSlots` 的第二个参数），判定侧按它决定
     *  那颗成员键的连击链数到第几击。
     */
    val memberGestures: MemberGestures?
        get() =
            if (isHold && followers.size == 1) {
                MemberGestures(memberDoubleOn, memberClicksOn, memberClickThenLongOn)
            } else {
                null
            }

    /**
     * [trigger] 这一档的动作。
     *
     *  组合只认单击 / 长按两档（[COMBO_SLOTS]）：双击与第 3 击起那些档位在这一层没有槽位，
     *  问到它们就是「这一档没定义」—— **不退回单击**，否则按两下会执行一下。
     *  松手那一档回 [longRelease]（与单键 `KeyConfig.actionOf` 同一口径）。
     *
     *  三种触发方式**共用这一对槽**：槽位跟着手势走，触发方式已经是这一档的身份（[mode]），
     *  不再需要为它多开几套。
     */
    fun actionOf(trigger: GestureTrigger): Action =
        when (trigger) {
            is GestureTrigger.Click -> {
                when (trigger.count) {
                    1 -> single

                    //  第 2 击起 = **成员连击**（按住首键、那颗唯一成员键连击）：
                    //  关着时恒回「无操作」（与单键 `doubleClickOn` / `extraClicksOn` 同一族）。
                    2 -> if (memberDoubleOn) memberDouble else Action.None

                    else -> if (trigger.count in memberClicksOn) memberClicks[trigger.count] ?: Action.None else Action.None
                }
            }

            GestureTrigger.LongPress -> {
                long
            }

            GestureTrigger.Release -> {
                longRelease
            }

            //  「单击后长按」= 成员连击的那一档（成员第一击抬手后、窗口内再按下并按住）。
            GestureTrigger.ClickThenLong -> {
                if (memberClickThenLongOn) memberClickThenLong else Action.None
            }

            //  这一档的松手**自成一路**：用它自己那一格，不借组合长按的 [longRelease]
            //  （与单键 `KeyConfig.actionOf` 同一个理由）。
            GestureTrigger.ClickThenLongRelease -> {
                memberClickThenLongReleaseAction
            }
        }

    /** 成员「单击后长按」这一档**本身**有事可做（直接绑定或场景给过动作）。 */
    val memberClickThenLongActionExists: Boolean
        get() = memberClickThenLongOn && (memberClickThenLong.isBound || scenes.any { it.chainBound })

    /**
     * 成员「单击后长按」松手那一刻该执行什么 —— 与单键 `KeyConfig.clickThenLongReleaseAction`
     * 同一套四位判据：这一档开着、松手开关开着、主档有事可做，且**没开「松手时触发」**
     * （那一条把动作本身推到了抬手，再补一个就是同一刻两个动作）。
     */
    val memberClickThenLongReleaseAction: Action
        get() =
            if (memberClickThenLongOn &&
                memberClickThenLongReleaseOn &&
                memberClickThenLongActionExists &&
                !memberClickThenLongFireOnRelease
            ) {
                memberClickThenLongRelease
            } else {
                Action.None
            }

    /**
     * 成员「单击后长按」要不要排连发 —— 与单键 `KeyConfig.clickThenLongRepeats` 同一条口径：
     * 开关关着 / 走「松手时触发」/ 绑的是多条命令 / 绑的是灵感语音，任一就排不出连发。
     */
    val memberClickThenLongRepeats: Boolean
        get() =
            memberClickThenLongRepeat &&
                !memberClickThenLongFireOnRelease &&
                !memberClickThenLongIsSerial &&
                !((memberClickThenLong as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true)

    /**
     * 松手那一下**真的有事可做** —— 开关开着、挑过动作、且不是「松手才触发」那条。
     *
     *  与单键 `KeyConfig.releaseBound` 同一份判据（那一边的解释更细）：四位缺一位都不算，
     *  其中第一位是「**长按这一档本身**有没有动作」—— 长按是无操作时，松手再补一个动作
     *  没有主语（界面那一侧同样把那一族压灰，见 `LongPressSpec.releaseAvailable`）。
     *  场景块里配的松手动作也算一位 —— 组合的场景表**本来就随广播带过来**（`ComboWire`），
     *  所以这一位在注入侧能就地算出来，不必像单键那样另开一个广播字段。
     *
     *  **三种触发方式一视同仁**（用户口径）：按住那两档同样是「长按执行完之后再补一条」，
     *  它的长按照样有动作，抬手照样是那一按的收尾 —— 没有理由把它排除在外。
     *  早先这一位带着 `!isHold`（那时按住档被认为是「抬手只收尾」），
     *  结果是界面上能配、配了却不生效。
     */
    override val releaseBound: Boolean
        get() =
            longActionExists &&
                !longFireOnRelease &&
                ((longReleaseOn && longRelease.isBound) || scenes.any { it.hasRelease })

    /**
     * 长按这一档**本身有事可做** —— 直接绑了动作，或某块场景里给长按配了动作。
     *
     *  与单键 `KeyConfig.longActionExists` 同一个名字、同一个含义，只是主语从「这颗键」换成
     *  「这一组」。组合的场景表**本来就随广播带过来**（`ComboWire`），所以注入侧能就地算出来。
     *
     *  两个读者，都在「这一按到底归谁」这条线上：
     *   · [releaseBound] —— 长按是无操作时，松手再补一个动作没有主语；
     *   · `ComboDetector` —— 长按有动作时，**成员键那一档「单击后长按」让位**（两者是同一个
     *     手势：按住首键、再按住唯一那颗成员，时序完全重合）。
     *
     *  先看 [hasLong]：按顺序触发的组合**根本没有长按这一档**，配过什么值都不该算数
     *  （判定侧不排那一枪，界面也不摆那一页）—— 不算进来的话，中途改过触发方式的那些档会让
     *  上面两处误以为「这一按归组合」。
     */
    val longActionExists: Boolean get() = hasLong && (long.isBound || scenes.any { it.longBound })

    /**
     * **带场景表**的取动作：先看 [scenes]（顺序、「条件成立且这一档有值」的第一块赢），
     * 都不中才落到 [actionOf]。
     *
     *  实现在 [pickAction] 一处 —— 与单键 `KeyConfig.actionFor` 是**同一个函数**。
     *  三种触发方式走同一份：场景块只分短按 / 长按，不构成「怎么按出来的」第二个维度。
     *
     *  `matches` 由调用方给（读系统开关的那一层，见 `ConditionProbe`）：这一层不认识系统接口。
     */
    fun resolve(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Action = pickAction(scenes, trigger, actionOf(trigger), matches)

    /**
     * 这一档**有没有被某个场景块接管** —— 「松手执行额外动作」要靠它让位。
     *
     *  与 [resolve] 同一条判据（条件成立 + 这一档有值），只是不要动作、只要一个是非：
     *  长按那一档被场景接管了，松手那一刻就不该再补一刀。
     */
    fun sceneTakes(
        trigger: GestureTrigger,
        matches: (BranchCondition) -> Boolean,
    ): Boolean = sceneTakes(scenes, trigger, matches)

    /**
     * **松手那一刻执行什么** —— 长按被某一块接管时用那一块的松手格，否则用 [longRelease]。
     *
     *  实现在 [pickReleaseAction] 一处 —— 与单键 `KeyConfig` 那一支是**同一个函数**
     *  （「一次按压一个结果」：块接管了长按，就不再补这一组自己的松手动作）。
     *  [longReleaseOn] 关着同样传 `None`：开关与动作是两位，关掉之后动作还留着。
     */
    fun resolveRelease(matches: (BranchCondition) -> Boolean): Action =
        pickReleaseAction(scenes, if (longReleaseOn) longRelease else Action.None, matches)

    /** [down] 里包含这一档的全部成员吗（不看顺序、允许同时按着别的键）。 */
    fun matches(down: Set<String>): Boolean = keys.all { it in down }

    /**
     * [sequence] 是不是按 [keys] 的**先后顺序**按下这一档的全部成员 —— 只给 [ordered] 那一档用。
     *
     *  [sequence] 是「真实按下顺序」的键 id 序列（判定侧按按下时刻排好）。这一档要求它的
     *  **最后几位**与 [keys] 逐位相同 —— 序列里多出来的那几颗不碍事：可能是这一组开始之前
     *  按过的别的键（还在同一个扫描窗口里），也可能是更宽一档的成员。空序列 / 长度不足都算不成立。
     *
     *  **比的必须是结尾而不是开头**：当前这一下一定是成员里的最后一颗（组合在它落下那一刻成立），
     *  而扫描窗口里可能还兜着这组开始之前按过的键。按开头比时，前面多一颗（比如先点了一下音量键）
     *  就会把整条有序组合判成「顺序不对」，用户看到的是这一组没生效、只落下两颗键各自的单击。
     */
    fun matchesOrdered(sequence: List<String>): Boolean =
        sequence.size >= keys.size &&
            keys.indices.all { sequence[sequence.size - keys.size + it] == keys[it] }

    companion object {
        /** 组合键 id 的前缀：`cmb1` / `cmb2` ……（序号由收集侧维护，只增不减）。 */
        const val ID_PREFIX = "cmb"

        /** 至少两颗 —— 一颗不叫组合。 */
        const val MIN_KEYS = 2

        /** [windowMs] 取这个值表示「跟随公共设置」—— 它不是「等 0 毫秒」。 */
        const val WINDOW_FOLLOW_GLOBAL = 0

        /** [holdMinMs] 取这个值表示「跟随公共设置」（同上：不是「按住 0 毫秒」）。 */
        const val HOLD_MIN_FOLLOW_GLOBAL = 0

        /**
         * 触发方式（[mode]）。
         *
         *  数字进线格式（`ComboDto` / `ComboWire` 各一格），所以只有「加在末尾」这一种改法；
         *  0 是窗口那条，与这一位出现之前写下的配置同义。
         *
         *  2026-11-14 起按住只剩**一种**（[MODE_HOLD]）：首键由 [keys] 第一位表达，
         *  「先按住第二颗」这个老模式在配置迁移时被折成「键序反转 + [MODE_HOLD]」。
         *  所以 [MODE_HOLD_SECOND] 只在**读旧文件**时出现，写出去的值里不会再有它。
         */
        const val MODE_WAIT = 0
        const val MODE_HOLD = 1

        /** 旧编号（= 先按住第二颗）—— 只用于读迁移前的配置，见 [MODE_HOLD] 的说明。 */
        const val MODE_HOLD_SECOND = 2

        /**
         * [raw] 这一档在 [keyCount] 颗成员下**真正能用**的触发方式。
         *
         *  成员够两颗就两种都允许 —— 按住那一档不再限于两颗（首键一颗 + 后续任意多颗）。
         *  认不出的取值一律当 [MODE_HOLD]：老配置里的 2 已经在 `ComboDto` 里迁移成
         *  「键序反转 + 1」，走到这一层还见到非 0 就只能当按住。
         *
         *  夹这一道的地方只有三处：两个解析入口（`ComboDto` / `ComboWire`）与界面改成员那一步。
         */
        fun modeFor(
            raw: Int,
            keyCount: Int,
        ): Int = if (keyCount < MIN_KEYS || raw == MODE_WAIT) MODE_WAIT else MODE_HOLD

        /**
         * 成员上限：**三颗**（用户口径）。两颗仍是最常见的那一档，三颗是给「三键齐按」留的口子。
         *
         *  这是**界面与收集**那一侧的上限，两类共用：等待组合是「三颗一起按下」，按住组合是
         *  「首键一颗 + 后续两颗」。收集入口（`ComboActions` / `KeysScreen`）按它拦，
         *  「最多 %d 颗」那条提示也用它。判定侧不吃这个数 —— 那边认到 [MAX_JUDGE_KEYS]。
         *
         *  两个上限为什么分开：判定侧从三颗起就走「多成员凑齐」那条路（`ComboDetector.multiFor`），
         *  界面放开到三颗之后那条路才真的有人走。数据层要是跟着界面卡在一个小数字上，
         *  解析时就被截断，判定侧永远见不到多成员形态。
         */
        const val MAX_KEYS = 3

        /**
         * **判定侧**认的成员上限：九颗 —— 解析（`ConfigDto` / `ComboWire`）、判定
         * （`ComboDetector` 的多成员路径）、日志（`ComboPlan`）三处都按它收口。
         *
         *  取 9 只是个「挡垃圾」的宽松上限：整机能收集到的键（内置 1 颗 + 自定义 5 颗）本来也
         *  到不了这个数，真正要防的是手改坏的文件里写几十颗，让一档组合吃掉整块键盘。
         *  下限仍是 [MIN_KEYS]：两颗那一档走 `ComboDetector` 的两两配对，3 颗起走多成员凑齐。
         */
        const val MAX_JUDGE_KEYS = 9

        /** 第 [seq] 档组合键的 id。 */
        fun idOf(seq: Int): String = ID_PREFIX + seq
    }
}
