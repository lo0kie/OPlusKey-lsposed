package com.lookie.opluskey.core.model

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
         * 从 `名字<符号>值` 里切出三段；**符号先按长的认**（`+=` 在 `=` 之前），
         * 认不出符号返回 `null`。
         */
        fun split(body: String): Triple<String, VarOp, String>? {
            //  「长符号优先」不靠顺序巧合：先试 `+=` / `-=` / `!`，最后才是 `=`
            val bySymbol = listOf(ADD, SUB, NOT).firstOrNull { body.contains(it.symbol) }
            if (bySymbol != null) {
                val at = body.indexOf(bySymbol.symbol)
                val name = body.substring(0, at)
                val rest = body.substring(at + bySymbol.symbol.length)
                //  `!` 后面不跟值：多写的部分当作「写法有问题」，不猜
                if (bySymbol == NOT && rest.isNotBlank()) return null
                return Triple(name.trim(), bySymbol, rest.trim())
            }
            val at = body.indexOf(SET.symbol)
            if (at < 0) return null
            return Triple(body.substring(0, at).trim(), SET, body.substring(at + SET.symbol.length).trim())
        }
    }
}
