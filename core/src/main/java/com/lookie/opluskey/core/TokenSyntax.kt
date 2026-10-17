package com.lookie.opluskey.core

/**
 * 动作 token 里「带正文的那两族」的前缀 —— 全项目只在这里写一次。
 *
 *  放在 `core` 根包而不是 `core/model`：`core/log` 的日志脱敏也要按前缀判断，而脱敏只需要
 *  「冒号前那段字符串」，不需要动作模型本身 —— 前缀留在 `core/model` 会让日志层反过来依赖域模型
 *  （形成 `core/log` 与 `core/model` 之间的包级环）。
 *
 *  [com.lookie.opluskey.core.model.Action.SHELL_PREFIX] / `INTENT_PREFIX` 两个常量由这里派生，
 *  对外名字与取值都没变。
 */
object TokenSyntax {
    /** `shell:` 后面跟整条命令正文。 */
    const val SHELL_PREFIX = "shell:"

    /** `intent:` 后面跟 action 串。 */
    const val INTENT_PREFIX = "intent:"
}
