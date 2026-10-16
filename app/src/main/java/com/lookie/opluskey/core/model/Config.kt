package com.lookie.opluskey.core.model

// 配置的数据结构：三种手势、单键配置、全局配置、整份配置。

/** 三种手势。 */
enum class Gesture(
    val id: String,
    val label: String,
) {
    SINGLE("single", "单击"),
    DOUBLE("double", "双击"),
    LONG("long", "长按"),
}

/** 一个键的完整配置。 */
data class KeyConfig(
    val id: KeyId,
    val enabled: Boolean,
    val single: Action,
    val double: Action,
    val long: Action,
    val longPressMs: Int,
    val longRepeat: Boolean,
    val longRepeatIntervalMs: Int,
    /**
     * 长按绑的是不是「多条命令」（`cmd:<名字>` 且命令库里那条是串行命令）。
     * 只由宿主算好、随配置广播带过来（`ConfigFields.LONG_SERIAL_EXTRA`），注入侧不自己算：
     * 算它要读宿主私有目录的命令库，system_server 进不去带 MCS 分类的目录
     * （`docs/device-findings.md` 六之五 / 九之一），读不到就恒 false，闸门失效。
     * 默认 false = 与早先行为一致（配置文件里没有这一位，不回写）。
     */
    val longIsSerial: Boolean = false,
) {
    fun actionOf(gesture: Gesture): Action =
        when (gesture) {
            Gesture.SINGLE -> single
            Gesture.DOUBLE -> double
            Gesture.LONG -> long
        }

    companion object {
        const val DEFAULT_LONG_PRESS_MS = 500

        /**
         * 长按阈值下限 —— **200ms 已经高于人手一次自然按压（80~150ms）**，所以任何一档下
         * 「正常按一下」都不会被判成长按，单击 / 双击都按得出来。
         *
         * 原来这一档是 100ms（落在自然按压里面）：「稍按久一点就变长按」，单击基本按不出来。
         * 下限抬到 200ms 就是为了堵掉这个组合，用户不必先踩一次才知道。
         */
        const val MIN_LONG_PRESS_MS = 200
        const val MAX_LONG_PRESS_MS = 1000
        const val DEFAULT_REPEAT_INTERVAL_MS = 300
        const val MIN_REPEAT_INTERVAL_MS = 100

        const val MAX_REPEAT_INTERVAL_MS = 3000

        /**
         * 某个键的默认值：**默认不接管**（`enabled = false` ⇒ 拦截路径整条不生效，行为与原生一致）。
         *
         * 注意：单击那档的默认值是「无操作」而不是「交还系统」—— 后者已删（见 `Action` 的注释）。
         * 主开关开着而这一档还没配时，按键被吞掉什么都不做；这也是「还没配」最不容易误读的表现。
         */
        fun defaultFor(id: KeyId) =
            KeyConfig(
                id = id,
                enabled = false,
                single = Action.None,
                double = Action.None,
                long = Action.None,
                longPressMs = DEFAULT_LONG_PRESS_MS,
                longRepeat = false,
                longRepeatIntervalMs = DEFAULT_REPEAT_INTERVAL_MS,
            )
    }
}

/** 全局配置（不属于任何单个键）。 */
data class GlobalConfig(
    val doubleClickMs: Int,
    val vibrate: Boolean,
    /**
     * 双击为 [Action.None] 时抬手立即触发单击，不等 [doubleClickMs] 的双击窗口。
     * 代价：开启后连按两下会变成两次单击（关闭时第二下会被吞掉）。
     */
    val instantSingle: Boolean = DEFAULT_INSTANT_SINGLE,
    /** 执行**任意动作**时弹一条 Toast（内容是动作名；自定义命令取命令名）。 */
    val toastOnRun: Boolean = DEFAULT_TOAST_ON_RUN,
    /** 界面语言（auto = 跟随系统，zh_CN = 简体中文，en = English）。 */
    val language: String = DEFAULT_LANGUAGE,
) {
    companion object {
        const val DEFAULT_DOUBLE_CLICK_MS = 300

        /** 双击窗口下限 —— 100ms 比「两次完整按压 + 抬手」的物理最短时间还短，是给用户留的余量档。 */
        const val MIN_DOUBLE_CLICK_MS = 100
        const val MAX_DOUBLE_CLICK_MS = 1000

        /** [GlobalConfig.instantSingle] 的默认值（不等窗口）。 */
        const val DEFAULT_INSTANT_SINGLE = true

        /** [GlobalConfig.toastOnRun] 的默认值：默认不弹。 */
        const val DEFAULT_TOAST_ON_RUN = false

        /** [GlobalConfig.language] 的默认值：跟随系统。 */
        const val DEFAULT_LANGUAGE = "auto"

        val DEFAULT =
            GlobalConfig(
                doubleClickMs = DEFAULT_DOUBLE_CLICK_MS,
                vibrate = false,
            )
    }
}

/** 整份配置。 */
data class Config(
    val keys: Map<KeyId, KeyConfig>,
    val global: GlobalConfig,
    /**
     * 用户是否已经确认过「风险提示与免责声明」—— 只由**本地**产生，决定「启用」之前要不要弹那一次。
     *
     * 存在配置里（而不是界面状态里）是**有意**的：
     *  · 清空配置按 [DEFAULT] 重建 ⇒ 这一位跟着没了 ⇒ 下次启用照样弹（「清空之后同样弹」就是这么来的）；
     *  · 它是**本机这一次的接受行为**，所以导出／导入一律把它丢掉（见 `ConfigBundle`）——
     *    一份文件不该替别人说「我已经接受了风险」。
     */
    val disclaimerAccepted: Boolean = false,
    /**
     * 用户是否已经确认过「自定义命令」那一页的风险提示 —— 与 [disclaimerAccepted] **分开记**。
     *
     *  两者说的是两件事：那一份讲的是「注入系统进程」这件事本身，这一份讲的是
     *  「自定义命令**不做任何检验或保护**，写什么就跑什么」。在首页确认过前者，不等于知道后者，
     *  所以不能共用一位。
     *
     *  同样存在配置里（理由与 [disclaimerAccepted] 一样）：清空配置按 [DEFAULT] 重建
     *  ⇒ 这一位跟着没了 ⇒ 下次进那一页照样弹；导出／导入也一律丢掉（见 `ConfigBundle`）。
     */
    val commandDisclaimerAccepted: Boolean = false,
) {
    fun key(id: KeyId): KeyConfig = keys[id] ?: KeyConfig.defaultFor(id)

    companion object {
        val DEFAULT =
            Config(
                keys = KeyId.entries.associateWith { KeyConfig.defaultFor(it) },
                global = GlobalConfig.DEFAULT,
            )
    }
}
