package com.lookie.opluskey.config

import java.util.Locale

enum class CommandKind(
    /** 界面上的叫法。 */
    val label: String,
    /** 存文件时用的标识。 */
    val id: String,
) {
    /** 单条命令，选中后直接执行。 */
    SINGLE("单条命令", "single"),

    /**
     * 多条命令，按顺序执行一串步骤。
     * 步骤是引用（动作写法或 `cmd:<名字>`），不是命令文本。
     * 枚举名与文件标识 `serial` 是持久化格式，改了认不出老数据。
     */
    SERIAL("多条命令", "serial"),
    ;

    companion object {
        /** 带 `Locale.ROOT`：文件格式解析不能受设备语言影响。 */
        fun fromId(id: String): CommandKind? =
            entries.firstOrNull { it.id == id.trim().lowercase(Locale.ROOT) }
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
    /** 写进 `plus_single` / `plus_double` / `plus_long` 的取值。 */
    val token: String get() = "$PREFIX$name"

    companion object {
        /** 自定义命令在配置里的前缀；全项目只在这里定义一次，[Action] 一侧统一引用。 */
        const val PREFIX = "cmd:"

        /** 步骤间隔默认值（毫秒）：400ms 实测够广播类动作跨进程落地，又不至于让多步链太慢。 */
        const val DEFAULT_STEP_DELAY_MS = 400

        /** 步骤间隔下限：0 = 不等待。 */
        const val MIN_STEP_DELAY_MS = 0

        /** 步骤间隔上限（毫秒），与连续触发间隔一致。 */
        const val MAX_STEP_DELAY_MS = 3000

        /** 多条命令的步骤数上限；执行侧按文件里实际步数执行。 */
        const val MAX_STEPS = 5
    }
}
