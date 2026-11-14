package com.lookie.opluskey.core.model

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
