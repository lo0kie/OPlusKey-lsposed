package com.lookie.opluskey.core.model

/** 引用写法的开头：`${`。 */
private const val OPEN = "\${"

/** 引用写法的结尾。 */
private const val CLOSE = "}"

/**
 * 一份变量的**当前值**，按名字查、按类型改。
 *
 * ## 引用怎么写
 *
 *  在命令的文本里写 `${名字}`，执行前整串替换成它的值（见 [expand]）：
 *
 * ```
 * shell:am broadcast -a test --ei count ${计数}
 * ```
 *
 *  认不出的名字**原样留着**（`${x}` 就这么执行）—— 宁可让用户看见命令里有个没替换的占位，
 *  也不要静默换成空串：那种「命令少了一个参数」在日志里几乎看不出来。
 *
 * ## 修改怎么写
 *
 *  命令里写一条 `setvar:<名字>=<值>`（`+=` 相加 / 拼接、`-=` 相减、`!` 取反，见 [VarOp]），
 *  执行侧走 [assign]。**变量必须已经存在** —— 类型是建的时候定死的，命令不能顺手造一个出来。
 */
data class Vars(
    val all: List<Variable> = emptyList(),
) {
    /** 按名字找（名字首尾空白不算数）；不在返回 `null`。 */
    fun find(name: String): Variable? = all.firstOrNull { it.name == name.trim() }

    /**
     * 把 [text] 里的 `${名字}` 全换成值。
     *
     *  单趟扫描（不是反复 `replace`）：替换进去的值里若也写着 `${...}`，**不再展开** ——
     *  否则两个变量互相引用时这里会一直展开下去。
     */
    fun expand(text: String): String {
        if (!text.contains(OPEN)) return text
        val out = StringBuilder(text.length + 16)
        var at = 0
        while (at < text.length) {
            val start = text.indexOf(OPEN, at)
            val end = if (start < 0) -1 else text.indexOf(CLOSE, start + OPEN.length)
            if (start < 0 || end < 0) {
                //  剩下的部分没有完整引用（或引用没写完）：原样带上、结束
                out.append(text, at, text.length)
                break
            }
            out.append(text, at, start)
            val name = text.substring(start + OPEN.length, end).trim()
            val hit = find(name)
            if (hit == null) {
                //  认不出的名字原样保留（见类注释）
                out.append(text, start, end + CLOSE.length)
            } else {
                out.append(hit.value)
            }
            at = end + CLOSE.length
        }
        return out.toString()
    }

    /**
     * 按 [set] 改一个变量，返回**新的一份**（[Vars] 是不可变的，调用方负责存回去）。
     *
     * @return 变更后的值；变量不存在 / 类型对不上 / 值归一不了都返回 `null`（命令算失败，不改盘）
     */
    fun assign(set: Action.VarSet): Vars? {
        val target = find(set.name) ?: return null
        val next = target.applied(set, this) ?: return null
        return Vars(all.map { if (it.name == target.name) next else it })
    }
}

/**
 * 一个变量被 [Action.VarSet] 改过之后的新样子；类型不符 / 值归一不了都返回 `null`。
 *
 *  值里的 `${...}` 先按 [vars] 展开 —— 于是 `setvar:总数+=${单条}` 这种「拿另一个变量算」
 *  不需要额外的表达式语法。
 */
private fun Variable.applied(
    set: Action.VarSet,
    vars: Vars,
): Variable? {
    if (set.op == VarOp.NOT) return type.negate(value)?.let { copy(value = it) }
    val text = vars.expand(set.value)
    val next =
        when (set.op) {
            VarOp.SET -> type.coerce(text)
            VarOp.ADD -> type.add(value, text)
            VarOp.SUB -> type.sub(value, text)
            VarOp.NOT -> null
        }
    return next?.let { copy(value = it) }
}

/**
 * 把动作里的**文本载荷**按当前变量展开一次。
 *
 *  为什么不在 `Action.parse` 里展开：解析发生在**存配置时**，而值要到**执行时**才知道。
 *  所以解析照旧留下 `${名字}` 原样（那是用户写的东西，得能被编辑），执行前才在这里换一次。
 *
 *  只碰「会进命令 / 目标 / 名字」的那几种：[Action.VarSet] 自己的值不在这里展开
 *  （它在赋值那一步展开，且要用同一份 [Vars]），键码那种数字载荷也不碰。
 */
fun Action.expandedWith(vars: Vars): Action =
    when (this) {
        is Action.Shell -> copy(command = vars.expand(command))
        is Action.Intent -> copy(action = vars.expand(action))
        is Action.App -> copy(target = vars.expand(target))
        is Action.AppWindow -> copy(target = vars.expand(target))
        is Action.Custom -> copy(name = vars.expand(name))
        else -> this
    }
