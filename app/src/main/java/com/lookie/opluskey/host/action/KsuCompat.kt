package com.lookie.opluskey.host.action

/** 照搬 KSU 模块（同目录 `OPlusKey`）的那些取值 —— 配置兼容用的别名表。 */
internal object KsuCompat {

    /**
     * KSU 预设里「截图」那条命令的**前缀**（预设原值是 `service call color_screenshot 1`，
     * 模块侧换成注入 `KEYCODE_SYSRQ`）—— 认领时用 `startsWith`，**不要用 `contains`**。
     *
     *  `contains` 会把「用户自己的命令正文里正好出现这个串」也当成 KSU 那条劫持掉
     *（自定义命令写 `echo color_screenshot` 就会被改成注入 SYSRQ，
     *  原命令不执行、也没有任何提示）。前缀匹配照样容得下参数差异
     *（`service call color_screenshot 2` 也认）。
     */
    const val SCREENSHOT_CMD_PREFIX = "service call color_screenshot"

    /** KSU 预设里「小布识屏」那条命令里的 service action。 */
    const val SIDEBAR_SERVICE = "oplus.intent.action.DIRECT_SIDEBAR_SERVICE"

    /** 「小布识屏」那条命令的**前缀**（理由同 [SCREENSHOT_CMD_PREFIX]）。 */
    const val SIDEBAR_CMD_PREFIX = "am start-foreground-service -a $SIDEBAR_SERVICE"

    /** 这条 shell 文本是不是「截图」那条 KSU 别名。 */
    fun isScreenshotAlias(command: String): Boolean = matches(SCREENSHOT_CMD_PREFIX, command)

    /** 这条 shell 文本是不是「小布识屏」那条 KSU 别名。 */
    fun isSidebarAlias(command: String): Boolean = matches(SIDEBAR_CMD_PREFIX, command)

    /** 两条 KSU 别名里任意一条 —— 多条命令的步骤合并 / 派发要按它分流（见 `CustomCommandRunner`）。 */
    fun isAlias(command: String): Boolean = isScreenshotAlias(command) || isSidebarAlias(command)

    /**
     * 别名认领的**唯一口径**：前缀匹配 + **这一条里没串别的命令**。
     *
     *  前缀匹配（不是 `contains`）：容得下参数差异（`service call color_screenshot 2` 也认），
     *  又不会把「用户自己的命令正文里正好出现这个串」当成 KSU 那条劫持掉。
     *
     *  注意：**串了别的命令就不认**：别名会被翻译成一次原生调用
     *  （注入 SYSRQ / 起小布识屏），`service call color_screenshot 1; echo hi` 这种一条里串两条的写法
     *  若整条认成别名，`echo hi` 就被**悄悄丢掉**了 —— 原命令不执行、也没有任何提示。
     *  判据是裸字符（`;` / `&` / `|` / 换行）：引号里的这些字符同样会命中，但两条别名的写法里
     *  不可能出现引号，宁可靠保守的一边（多认一次 shell、少丢一条命令）。
     */
    private fun matches(prefix: String, command: String): Boolean =
        !hasCommandChain(command) && command.trim().startsWith(prefix)

    /** 这条 shell 文本里是不是串了别的命令（`;` / `&` / `|` / 换行）。 */
    private fun hasCommandChain(command: String): Boolean =
        command.any { it == ';' || it == '&' || it == '|' || it == '\n' || it == '\r' }
}
