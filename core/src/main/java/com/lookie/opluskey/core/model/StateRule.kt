package com.lookie.opluskey.core.model

/**
 * 判定一次按键时的**环境快照** —— 「按键按状态执行不同命令」的输入。
 *
 * 目前只有一格（前台应用的包名），因为状态维度里只落地了它。以后要加「屏幕是否点亮」
 * 「是否在充电」这类条件，就往这里加一格、在 [StateCondition] 加一个实现 ——
 * 判定侧只需要在**取环境的那一处**把值填上，分支表与匹配逻辑都不用动。
 *
 * 取不到时填 `null`，不要猜：拿不到前台应用（清单读不出来、系统接口拒绝）时传
 * [UNKNOWN]，于是所有「按状态」的分支都不成立、退回这颗键本来的绑定 ——
 * 那与「状态不匹配」是同一个结果，比随便填一个包名安全。
 */
data class KeyState(
    /** 前台应用的包名；`null` = 不知道。 */
    val foregroundPkg: String? = null,
) {
    companion object {
        /** 什么都不知道：所有状态分支都不成立。 */
        val UNKNOWN = KeyState()
    }
}

/**
 * 一个**状态条件** —— 成立时，挂着它的那条分支才有资格顶替这颗键本来的绑定。
 *
 * ## 为什么是 sealed 接口，而不是一个 `pkg: String` 字段
 *
 * 状态这一族天然会长（前台应用只是第一档）。写成接口之后，加一档 = 加一个实现 + 加一个解析分支，
 * 于是每一个 `when` 都会因为不完整而**编译不过**，漏掉的地方立刻暴露；写成裸字段则是
 * 「加一档 = 改数据类 + 改所有读它的地方」，漏一处没有任何提示。
 *
 * ## 为什么不做「永远成立」的兜底档
 *
 * 一条写错的条件被当成「永远成立」，会静默接管这颗键的所有手势 —— 表现是「这颗键怎么按
 * 都不走原来的绑定了」，而且没有任何症状指向那条规则。所以 [parse] 认不出就返回 `null`，
 * 由调用方把这条规则整条丢掉、只留一行日志。
 */
sealed interface StateCondition {
    /** 配置文件里的写法。 */
    val token: String

    /** 这个条件在 [state] 下成不成立。 */
    fun matches(state: KeyState): Boolean

    /** 前台应用就是 [pkg] 时成立。 */
    data class ForegroundApp(
        val pkg: String,
    ) : StateCondition {
        override val token: String = PREFIX + pkg

        override fun matches(state: KeyState): Boolean = pkg.isNotEmpty() && state.foregroundPkg == pkg
    }

    companion object {
        /** 前台应用那一档的前缀 —— **只在这里写一次**（与 `Action` 的前缀同一口径）。 */
        const val PREFIX = "app:"

        /**
         * 解析一条条件写法；认不出的返回 `null`（理由见本接口的类注释）。
         *
         * 只做很轻的归一：去首尾空白、空包名当认不出。真正的「这个包名装没装」不在这里判 ——
         * 配置是跨设备导出的，这台机器上没装不等于那条规则写错了。
         */
        fun parse(raw: String?): StateCondition? {
            val text = raw?.trim().orEmpty()
            if (!text.startsWith(PREFIX)) return null
            val pkg = text.removePrefix(PREFIX).trim()
            if (pkg.isEmpty()) return null
            return ForegroundApp(pkg)
        }
    }
}

/**
 * 一条**状态分支**：这个手势在这个状态下改执行 [action]。
 *
 * 挂在 `KeyConfig.rules` 上，按顺序匹配、**第一条成立的赢**（见 `KeyConfig.actionFor`）。
 * 表是空的（默认）⇒ 行为与没有这一档完全相同。
 *
 * 手势是分支的一部分，而不是「每个手势一张表」：用户的心智是「在微信里，短按改成扫码」，
 * 一条一条写得出来；拆成三张表则要求每条规则先选对表，配置文件和界面上都多一层无意义的分类。
 */
data class StateRule(
    val gesture: Gesture,
    val condition: StateCondition,
    val action: Action,
) {
    /** 这一条在「[gesture] 这次手势 + [state] 这个环境」下成立吗。 */
    fun appliesTo(
        gesture: Gesture,
        state: KeyState,
    ): Boolean = this.gesture == gesture && condition.matches(state)
}
