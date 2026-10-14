package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.log.XLog

/** 一个动作类型。 */
sealed interface Action {

    /** 配置文件里的原始写法，回写配置时用。 */
    val token: String

    /** 拦截按键，什么都不做。 */
    data object None : Action {
        override val token: String = "none"
    }

    // 「交还系统」（`native` / `passthrough`）**整条已删**（2026-10-14 用户要求）：
    //  判定手势必须先吞掉 DOWN，所以「只放行某一个手势」在原理上做不到 —— 一交还就是整键，
    //  同一把键上双击 / 长按的命令也跟着被系统吞掉。
    //  依据写在 `docs/implementation.md` 的「交还系统为什么不提供」。

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

    /** 同 [App]，但用 ColorOS 的小窗起（`window:目标`）。 */
    data class AppWindow(val target: String) : Action {
        override val token: String = "window:$target"
    }

    /**
     * 把**前台**那个应用收成小窗（`zoom:mini` / `zoom:mini:collapse`）。
     *
     * 与 [AppWindow] 走同一族接口，只差目标：那条按包名解析入口，这条取当前前台活动 ——
     * 所以小窗起不来时它不会退成普通打开（前台那个本来就占着屏，重开一次等于什么都没发生）。
     *
     * [collapsed] 选尺寸档（`ActivityOptions` 里的 `android:activity.mZoomLaunchFlags`，服务端据此分流）：
     *  · `false` = **展开**的普通小窗（居中大面积，`windowType=1`，flags=`2`）；
     *  · `true`  = **收起**的最小化窗（右上角一条，`windowType=2`，flags=`32`）。
     *
     * 收起档的几何由服务端自己算；展开档走 system_server 那条时要**由我们预置**展开档几何
     * （`inject/system/FlexibleWindowBridge` 的 `EXPANDED_BOUNDS`），拿收起档的值喂进去会把窗口
     * 缩成右上角一条。取值与判据见 `docs/device-findings.md` §17.6.3 的尺寸档对照表。
     */
    data class MiniWindow(val collapsed: Boolean = false) : Action {
        override val token: String = if (collapsed) "zoom:mini:collapse" else "zoom:mini"
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

    /**
     * 弹系统电源菜单（关机 / 重启 / 紧急呼叫）：执行侧在 system_server 里直接调
     * `PhoneWindowManager.showGlobalActions()`，不注入电源键、没有长按等待。
     */
    data object PowerMenu : Action {
        override val token: String = "power:menu"
    }

    /**
     * ColorOS 系统能力（`oplus:功能名`），取值见 `host/action/OplusFeature` —— 是不是「侧键场景表」
     * 里的那一档、调哪个组件，都照抄系统自己的记录。
     */
    data class Oplus(val feature: String) : Action {
        override val token: String = "oplus:$feature"
    }

    /**
     * 拉下系统面板（`panel:control_center`）：在 system_server 里调 `IStatusBarService`
     * （见 `inject/system/StatusBarBridge`），与「通知中心」（`keyevent:83`）同一条通道。
     *
     *  为什么不把「通知中心」也并进这一族：它写的 `keyevent:83` 是**历史写法**，改掉会让用户
     * 配置里已有的绑定失效 —— 那边只是显示名从「通知栏」改成「通知中心」，取值不动。
     */
    data class Panel(val mode: String) : Action {
        override val token: String = "panel:$mode"

        companion object {
            /**
             * 控制中心（拉下快捷设置那一块）—— 目前唯一一档。
             *
             * 名字照搬系统自己侧键动作表里的 `control_center`（`docs/device-findings.md` §五），
             * 但**调用目标不是场景表那份**：那张表里没有这一行，落点是我们自己在 system_server 里试的
             * （候选与判据见 [com.lookie.opluskey.inject.system.StatusBarBridge]）。
             */
            const val CONTROL_CENTER = "control_center"
        }
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
                // 必须排在兜底之前，否则 `window:` 会被当成 shell 命令执行
                text.startsWith("window:") -> AppWindow(text.removePrefix("window:").trim())
                text.startsWith("shortcut:") -> Shortcut(text.removePrefix("shortcut:").trim())
                text == "flashlight" -> Flashlight
                // 必须排在兜底之前，否则 `ringer:` 会被当成 shell 命令执行
                text.startsWith("ringer:") -> Ringer(text.removePrefix("ringer:").trim())
                // 同上：认不出的前缀会落到 shell 兜底
                text == "power:menu" -> PowerMenu
                // 同上：`zoom:` 也是认不出就落到 shell 兜底的那一类。
                // 收起档必须排在展开档**之前** —— 前缀相同，反了会把 `zoom:mini:collapse` 判成展开档。
                text == "zoom:mini:collapse" -> MiniWindow(collapsed = true)
                text == "zoom:mini" -> MiniWindow(collapsed = false)
                text.startsWith("oplus:") -> Oplus(text.removePrefix("oplus:").trim())
                // 同上：`panel:` 也是认不出就落到 shell 兜底的那一类
                text.startsWith("panel:") -> Panel(text.removePrefix("panel:").trim())
                else -> Shell(text)
            }
        }

    }
}
