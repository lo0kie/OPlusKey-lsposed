package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.TokenSyntax
import com.lookie.opluskey.core.log.XLog

/** 一个动作类型。 */
sealed interface Action {
    /** 配置文件里的原始写法，回写配置时用。 */
    val token: String

    /**
     * 「打开应用」那一族的目标（[App] 与 [AppWindow] 两档都是），其余为 `null`。
     *
     *  这两档的目标写法完全一样（`包名/Activity` 或 `包名`），差别只在「用哪种方式起」——
     *  界面上凡是「取一下目标」的地方都走这里，不再各写一遍 `when`。
     */
    val appTarget: String?
        get() =
            when (this) {
                is App -> target
                is AppWindow -> target
                else -> null
            }

    /** 是不是「打开应用」那一族（即 [appTarget] 非空）。 */
    val isAppOpen: Boolean
        get() = appTarget != null

    /** 拦截按键，什么都不做。 */
    data object None : Action {
        override val token: String = Action.NONE
    }

    // 「交还系统」（`native` / `passthrough`）**整条已删**：
    //  判定手势必须先吞掉 DOWN，所以「只放行某一个手势」在原理上做不到 —— 一交还就是整键，
    //  同一把键上双击 / 长按的命令也跟着被系统吞掉。
    //  依据写在 `docs/implementation.md` 的「交还系统为什么不提供」。

    /** 注入一个按键事件，[keyCode] 是 `KeyEvent.KEYCODE_*` 的数值。 */
    data class KeyEvent(
        val keyCode: Int,
    ) : Action {
        override val token: String = Action.KEYEVENT_PREFIX + keyCode
    }

    /** 执行 shell 命令。 */
    data class Shell(
        val command: String,
    ) : Action {
        override val token: String = Action.SHELL_PREFIX + command
    }

    /** 自定义命令动作，[name] 是命令库里的命令名。 */
    data class Custom(
        val name: String,
    ) : Action {
        override val token: String = "${Command.PREFIX}$name"
    }

    /** 发出广播。 */
    data class Intent(
        val action: String,
    ) : Action {
        override val token: String = Action.INTENT_PREFIX + action
    }

    /** 拉起一个界面，[target] 形如 `包名/Activity` 或 `包名`。 */
    data class App(
        val target: String,
    ) : Action {
        override val token: String = Action.APP_PREFIX + target
    }

    /** 同 [App]，但用 ColorOS 的小窗起（`window:目标`）。 */
    data class AppWindow(
        val target: String,
    ) : Action {
        override val token: String = Action.WINDOW_PREFIX + target
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
     * （`bridge/FlexibleWindowBridge` 的 `EXPANDED_BOUNDS`），拿收起档的值喂进去会把窗口
     * 缩成右上角一条。取值与判据见 `docs/device-findings.md` §17.6.3 的尺寸档对照表。
     */
    data class MiniWindow(
        val collapsed: Boolean = false,
    ) : Action {
        override val token: String = if (collapsed) Action.ZOOM_MINI_COLLAPSE else Action.ZOOM_MINI
    }

    /** 执行 ColorOS 小布快捷指令（`com.coloros.shortcuts`），[shortcutId] 是那条指令的 ID。 */
    data class Shortcut(
        val shortcutId: String,
    ) : Action {
        override val token: String = Action.SHORTCUT_PREFIX + shortcutId
    }

    /** 切换手电筒（`flashlight`）：走 CameraManager 的 torch 接口而非 `/sys/class/leds`（后者需要 root）。 */
    data object Flashlight : Action {
        override val token: String = Action.FLASHLIGHT
    }

    /**
     * 把响铃档位切到指定档（`ringer:模式`）；[mode] 取 `normal` / `vibrate` / `silent`，
     * 认不出的值执行侧只记日志、不做任何事。
     */
    data class Ringer(
        val mode: String,
    ) : Action {
        override val token: String = Action.RINGER_PREFIX + mode

        companion object {
            /**
             * 三个档位名 —— 配置里 `ringer:<名>` 的 `<名>`，与执行侧（`bridge/RingerController`）
             * 认的档位一一对应。清单表与图标表都从这一处取，不再各写一遍字符串。
             */
            const val NORMAL = "normal"
            const val VIBRATE = "vibrate"
            const val SILENT = "silent"
        }
    }

    /**
     * 弹系统电源菜单（关机 / 重启 / 紧急呼叫）：执行侧在 system_server 里直接调
     * `PhoneWindowManager.showGlobalActions()`，不注入电源键、没有长按等待。
     */
    data object PowerMenu : Action {
        override val token: String = Action.POWER_MENU
    }

    /**
     * ColorOS 系统能力（`oplus:功能名`），取值见 `core/model/RomProfile` —— 是不是「侧键场景表」
     * 里的那一档、调哪个组件，都照抄系统自己的记录。
     */
    data class Oplus(
        val feature: String,
    ) : Action {
        override val token: String = Action.OPLUS_PREFIX + feature
    }

    /**
     * 拉下系统面板（`panel:control_center`）：在 system_server 里调 `IStatusBarService`
     * （见 `bridge/StatusBarBridge`），与「通知中心」（`keyevent:83`）同一条通道。
     *
     *  为什么不把「通知中心」也并进这一族：它写的 `keyevent:83` 是**历史写法**，改掉会让用户
     * 配置里已有的绑定失效 —— 那边只是显示名从「通知栏」改成「通知中心」，取值不动。
     */
    data class Panel(
        val mode: String,
    ) : Action {
        override val token: String = Action.PANEL_PREFIX + mode

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
        // 各 token 的前缀与整串取值 —— **全项目只在这里写**。别处（`Action.parse`、界面层的
        //  图标表 / 文案表 / 清单表、日志脱敏）一律引用这些常量，不许再写字面量：同一个前缀散在
        //  几个文件里时，改一处而漏另一处的表现是「配置读出来是另一档」，而编译器不会提醒。
        //  前缀之外的「入口格标记」（`nav:` / `key:` / `zoom:`）不是动作取值、永不写进配置，
        //  它们归 `ui/model/ActionCatalog`（那里是清单层）。

        /** 「无操作」。 */
        const val NONE = "none"

        /** 注入一次按键，后接 `KeyEvent.KEYCODE_*` 的数值。 */
        const val KEYEVENT_PREFIX = "keyevent:"

        /** 执行 shell 命令（手写时可省这个前缀，见 [parse] 的兜底）。 */
        const val SHELL_PREFIX = TokenSyntax.SHELL_PREFIX

        /** 发出广播。 */
        const val INTENT_PREFIX = TokenSyntax.INTENT_PREFIX

        /** 拉起一个界面。 */
        const val APP_PREFIX = "app:"

        /** 用小窗起一个界面。 */
        const val WINDOW_PREFIX = "window:"

        /** 小布快捷指令。 */
        const val SHORTCUT_PREFIX = "shortcut:"

        /** 切换手电筒。 */
        const val FLASHLIGHT = "flashlight"

        /** 切响铃档位，后接档位名。 */
        const val RINGER_PREFIX = "ringer:"

        /** 弹系统电源菜单。 */
        const val POWER_MENU = "power:menu"

        /** 把前台应用收成小窗（展开档）。 */
        const val ZOOM_MINI = "zoom:mini"

        /** 同上，收起档（前缀与展开档相同，解析时**必须排在它前面**）。 */
        const val ZOOM_MINI_COLLAPSE = "zoom:mini:collapse"

        /** ColorOS 系统能力，后接功能名。 */
        const val OPLUS_PREFIX = "oplus:"

        /** 拉下系统面板，后接面板名。 */
        const val PANEL_PREFIX = "panel:"

        /**
         * `keyevent:<码>` 的拼法 —— 键码本体在 [KeyId] 一处（一、5），这里只负责拼成 token。
         * 清单里那些按键档位一律走它，不许再把数字写进字符串字面量。
         */
        fun keyToken(keyCode: Int): String = "$KEYEVENT_PREFIX$keyCode"

        /**
         * 解析一个动作字符串，认不出前缀的按 shell 命令执行（手写命令可省 `shell:`）。
         * 不兼容 KSU 的按键名写法（`keyevent:KEYCODE_HOME`），只认数值。
         */
        fun parse(raw: String?): Action {
            val text = raw?.trim().orEmpty()
            return when {
                text.isEmpty() || text == NONE -> {
                    None
                }

                text.startsWith(KEYEVENT_PREFIX) -> {
                    val code = text.removePrefix(KEYEVENT_PREFIX).trim().toIntOrNull()
                    if (code == null) {
                        XLog.w("动作 keyevent: 的按键无法识别（按 none 处理）：$text")
                        None
                    } else {
                        KeyEvent(code)
                    }
                }

                text.startsWith(SHELL_PREFIX) -> {
                    Shell(text.removePrefix(SHELL_PREFIX).trim())
                }

                // 必须排在兜底之前，否则 `cmd:` 会被当成 shell 命令执行
                text.startsWith(Command.PREFIX) -> {
                    Custom(text.removePrefix(Command.PREFIX).trim())
                }

                text.startsWith(INTENT_PREFIX) -> {
                    Intent(text.removePrefix(INTENT_PREFIX).trim())
                }

                text.startsWith(APP_PREFIX) -> {
                    App(text.removePrefix(APP_PREFIX).trim())
                }

                // 必须排在兜底之前，否则 `window:` 会被当成 shell 命令执行
                text.startsWith(WINDOW_PREFIX) -> {
                    AppWindow(text.removePrefix(WINDOW_PREFIX).trim())
                }

                text.startsWith(SHORTCUT_PREFIX) -> {
                    Shortcut(text.removePrefix(SHORTCUT_PREFIX).trim())
                }

                text == FLASHLIGHT -> {
                    Flashlight
                }

                // 必须排在兜底之前，否则 `ringer:` 会被当成 shell 命令执行
                text.startsWith(RINGER_PREFIX) -> {
                    Ringer(text.removePrefix(RINGER_PREFIX).trim())
                }

                // 同上：认不出的前缀会落到 shell 兜底
                text == POWER_MENU -> {
                    PowerMenu
                }

                // 同上：`zoom:` 也是认不出就落到 shell 兜底的那一类。
                // 两档都是**精确比较**（`zoom:mini` 与 `zoom:mini:collapse` 互不相等），所以这两支
                // 之间**没有顺序要求**；这里真正要守的是它们都排在 `else -> Shell` 兜底之前。
                text == ZOOM_MINI_COLLAPSE -> {
                    MiniWindow(collapsed = true)
                }

                text == ZOOM_MINI -> {
                    MiniWindow(collapsed = false)
                }

                text.startsWith(OPLUS_PREFIX) -> {
                    Oplus(text.removePrefix(OPLUS_PREFIX).trim())
                }

                // 同上：`panel:` 也是认不出就落到 shell 兜底的那一类
                text.startsWith(PANEL_PREFIX) -> {
                    Panel(text.removePrefix(PANEL_PREFIX).trim())
                }

                else -> {
                    Shell(text)
                }
            }
        }
    }
}
