package com.lookie.opluskey.config

import com.lookie.opluskey.XLog

/** 一个动作类型。 */
sealed interface Action {

    /** 配置文件里的原始写法，回写配置时用。 */
    val token: String

    /** 拦截按键，什么都不做。 */
    data object None : Action {
        override val token: String = "none"
    }

    /** 完全不碰这个键，保留系统原生行为。 */
    data object Native : Action {
        override val token: String = "native"
    }

    /** 判定结束后转发一次按键（等于「这个手势放行给系统」）。 */
    data object Passthrough : Action {
        override val token: String = "passthrough"
    }

    /** 注入一个按键事件，[keyCode] 是 `KeyEvent.KEYCODE_*` 的数值。 */
    data class KeyEvent(val keyCode: Int) : Action {
        override val token: String = "keyevent:$keyCode"
    }

    /** 执行 shell 命令。 */
    data class Shell(val command: String) : Action {
        override val token: String = "shell:$command"
    }

    /** 自定义命令动作，[name] 是命令库里的命令名。 */
    data class Custom(val name: String) : Action {
        override val token: String = "${Command.PREFIX}$name"
    }

    /** 发出广播。 */
    data class Intent(val action: String) : Action {
        override val token: String = "intent:$action"
    }

    /** 拉起一个界面，[target] 形如 `包名/Activity` 或 `包名`。 */
    data class App(val target: String) : Action {
        override val token: String = "app:$target"
    }

    /** 执行 ColorOS 小布快捷指令（`com.coloros.shortcuts`），[shortcutId] 是那条指令的 ID。 */
    data class Shortcut(val shortcutId: String) : Action {
        override val token: String = "shortcut:$shortcutId"
    }

    /** 切换手电筒（`flashlight`）：走 CameraManager 的 torch 接口而非 `/sys/class/leds`（后者需要 root）。 */
    data object Flashlight : Action {
        override val token: String = "flashlight"
    }

    /**
     * 把响铃档位切到指定档（`ringer:模式`）；[mode] 取 `normal` / `vibrate` / `silent`，
     * 认不出的值执行侧只记日志、不做任何事。
     */
    data class Ringer(val mode: String) : Action {
        override val token: String = "ringer:$mode"
    }

    companion object {

        /**
         * 解析一个动作字符串，认不出前缀的按 shell 命令执行（手写命令可省 `shell:`）。
         * 不兼容 KSU 的按键名写法（`keyevent:KEYCODE_HOME`），只认数值。
         */
        fun parse(raw: String?): Action {
            val text = raw?.trim().orEmpty()
            return when {
                text.isEmpty() || text == "none" -> None
                text == "native" -> Native
                text == "passthrough" -> Passthrough
                text.startsWith("keyevent:") -> {
                    val code = text.removePrefix("keyevent:").trim().toIntOrNull()
                    if (code == null) {
                        XLog.w("动作 keyevent: 的按键无法识别（按 none 处理）：$text")
                        None
                    } else {
                        KeyEvent(code)
                    }
                }
                text.startsWith("shell:") -> Shell(text.removePrefix("shell:").trim())
                // 必须排在兜底之前，否则 `cmd:` 会被当成 shell 命令执行
                text.startsWith(Command.PREFIX) -> Custom(text.removePrefix(Command.PREFIX).trim())
                text.startsWith("intent:") -> Intent(text.removePrefix("intent:").trim())
                text.startsWith("app:") -> App(text.removePrefix("app:").trim())
                text.startsWith("shortcut:") -> Shortcut(text.removePrefix("shortcut:").trim())
                text == "flashlight" -> Flashlight
                // 必须排在兜底之前，否则 `ringer:` 会被当成 shell 命令执行
                text.startsWith("ringer:") -> Ringer(text.removePrefix("ringer:").trim())
                else -> Shell(text)
            }
        }

        /**
         * 把非单击手势上的 `native` 正常化成 `none`：
         * 能走到双击 / 长按判定说明键已被接管，「不碰这个键」在那些手势上无法成立。
         */
        fun normalizeForGesture(action: Action, gesture: Gesture): Action =
            if (gesture != Gesture.SINGLE && action == Native) None else action
    }
}
