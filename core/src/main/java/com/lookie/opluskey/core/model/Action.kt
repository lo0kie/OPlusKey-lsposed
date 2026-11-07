package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.TokenSyntax
import com.lookie.opluskey.core.log.XLog

/** 一个动作类型。 */
sealed interface Action {
    /** 配置文件里的原始写法，回写配置时用。 */
    val token: String

    /**
     * 「打开应用」那一族的目标（[App] / [AppWindow] / [AppShortcut] 三档都是），其余为 `null`。
     *
     *  前两档的目标写法完全一样（`包名/Activity` 或 `包名`），差别只在「用哪种方式起」；
     *  [AppShortcut] 是「比应用更具体的一档」（`包名/快捷方式id`），它**也算这一族** ——
     *  否则绑定页那格「打开应用」认不出它（`isSelected` 为 false），焦点会退回第一格「无操作」。
     *
     *  界面上凡是「取一下目标」的地方都走这里，不再各写一遍 `when`。
     */
    val appTarget: String?
        get() =
            when (this) {
                is App -> target
                is AppWindow -> target
                is AppShortcut -> AppTarget.format("$pkg/$shortcutId", userId)
                else -> null
            }

    /** 是不是「打开应用」那一族（即 [appTarget] 非空）。 */
    val isAppOpen: Boolean
        get() = appTarget != null

    /**
     * 这份动作**只有前缀、载荷是空的**（`cmd:` / `app:` / `shell:` 后面什么都没写）。
     *
     *  这种写法解析得出一个动作对象，但执行时必然落空：`cmd:` 变成 [Custom] 空名字 ⇒ 永远
     *  `NOT_FOUND`；`app:` / `window:` 变成空目标 ⇒ 解析不到界面；`shell:` 变成一条空命令；
     *  `intent:` 变成一个空 action 的广播。界面据此判断「这行字算不算一行能存的命令」
     *  （保存按钮不该为它亮），执行侧不必各自再猜一遍。
     */
    val isBarePrefix: Boolean
        get() =
            when (this) {
                None, is KeyEvent, is MiniWindow, Flashlight, PowerMenu -> false

                is Shell -> command.isBlank()

                is Custom -> name.isBlank()

                is Intent -> action.isBlank()

                is App -> target.isBlank()

                is AppWindow -> target.isBlank()

                is AppShortcut -> pkg.isBlank() || shortcutId.isBlank()

                is Shortcut -> shortcutId.isBlank()

                is Ringer -> mode.isBlank()

                is Brightness -> false

                is Volume -> false

                is Oplus -> feature.isBlank()

                is Panel -> mode.isBlank()

                is Setting -> id.isBlank()

                //  包名在解析那一层就被校验过（`parseProcess` 认不出直接给 None），走到这里必然非空
                is Process -> false

                is VarSet -> name.isBlank()
            }

    /**
     * 这一档**有没有绑东西** —— 全项目唯一判据。
     *
     *  以前这句话散在二十来处，各写成 `!is Action.None` / `== Action.None` / `is Action.None`。
     *  单看某一行都没问题，但它们是**同一条业务规则**：一条键有没有绑定。跟着它走的行为很多
     *  （要不要振动、要不要记「最后一次动作的时刻」、要不要进执行计划、提示弹不弹、组合键是否
     *  透传），而它们必须同时成立 —— 一半按「绑了」处理、一半按「没绑」处理时，表现是
     *  「振动了但没执行」这种没人看得懂的样子。
     *
     *  更要紧的是**以后**：一旦出现第二种「空」（占位动作、停用状态、条件不成立时也算空），
     *  那二十来处得一起改，而漏掉几处不会有任何编译错误。收成属性之后，那种改动只在这里发生。
     *
     *  ## 为什么叫 isBound 而不是 isEmpty
     *
     *  「空」在这套取值里已经有好几个意思：`Action.isBarePrefix` 说的是**写法不完整**
     *  （`app:` 后面没东西），与「什么都没绑」是两件事 —— 前者是坏配置，后者是正当配置。
     *  叫 `isEmpty` 会让这两件事看起来是一家人。
     */
    val isBound: Boolean
        get() = this !is None

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

    /**
     * 拉起一个界面，[target] 形如 `包名/Activity` 或 `包名`。
     *
     * [stockAnim] 为真时用**原版侧键那套启动包裹**起（token 里带 [STOCK_ANIM_MARK]）：
     * 补原版的启动标志与 `key_position` / `android_action_key_launch_type`，并给
     * `startActivityAsUser` 传一个 `ActivityOptions`，让 ROM 自己那层启动转场照常播
     * （技术文档 `SIDE-KEY-OPEN-APP-ANIMATION.md` 第 2.4 / 5 节）。
     *
     *  **与小窗互斥**：[AppWindow] 走 ColorOS 的小窗配方，塞不进这套包裹 —— 同一格只能二选一。
     */
    data class App(
        val target: String,
        val stockAnim: Boolean = false,
    ) : Action {
        override val token: String =
            Action.APP_PREFIX + (if (stockAnim) STOCK_ANIM_MARK else "") + target
    }

    /** 同 [App]，但用 ColorOS 的小窗起（`window:目标`）。 */
    data class AppWindow(
        val target: String,
    ) : Action {
        override val token: String = Action.WINDOW_PREFIX + target
    }

    /**
     * 应用的**快捷方式**（长按桌面图标弹出来的那几个：扫一扫 / 收付款）。
     *
     *  与 [Shortcut] **不是一回事**：那条是 ColorOS 的小布快捷指令（`com.coloros.shortcuts`），
     *  这条是某个应用自己注册的 `ShortcutInfo`，执行时走 `LauncherApps.startShortcut` ——
     *  那条 intent 里往往只有 action + `PersistableBundle`，按普通 intent 起会把参数丢掉、
     *  退化成「打开应用」。
     *
     *  **不带显示名**：名字单独存在 `Config.appNames`（见 `AppTarget` 的类注释）。这条动作的
     *  名字是「扫一扫」这类**快捷方式自己的名字**，与应用的显示名不是一回事，所以由落盘那一侧
     *  按这一条的身份（`999@包名/id`）分别记。
     */
    data class AppShortcut(
        val pkg: String,
        val shortcutId: String,
        val userId: Int = 0,
        /** 用小窗起（`appshortcut:w:…`）—— 与 [AppWindow] 同一档意思，只是目标是快捷方式。 */
        val window: Boolean = false,
    ) : Action {
        /**
         * 目标那一段（**不带**动作前缀）：`999@包名/id`。
         *
         *  界面要拿它跟「选择界面」二级页的行做身份比较（`AppTarget.same`）—— 与 [token] 同源，
         *  拼两套的话，改了一处另一处就对不上，那一行会永远不点亮。
         */
        val plainTarget: String =
            AppTarget.format("$pkg/$shortcutId", userId)

        override val token: String =
            Action.APP_SHORTCUT_PREFIX +
                (if (window) WINDOW_MARK else "") +
                plainTarget
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

    /**
     * 结束一个**应用**（`proc:stop:包名`）—— 由「结束应用」那一格挑出来绑到按键上。
     *
     *  与 [App] 不是一回事：那一档要的是「把某个界面拉起来」，这一档管的是**这个应用还在不在**。
     *  执行侧是整包范围：系统的强行停止（`am force-stop`）+ 把残留的子进程一起收掉，
     *  `:push` / `:service` 那几份一起走 —— 所以它**没有「只结束某一条进程」这一档**。
     *  **只存包名、不存 PID**：PID 每次开机都变，绑定时取的那个数过一夜就是别人的进程。
     */
    data class Process(
        val pkg: String,
    ) : Action {
        override val token: String = Action.PROCESS_PREFIX + Action.STOP_SUFFIX + pkg
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
             * 三个档位名 —— 配置里 `ringer:<名>` 的 `<名>`，与执行侧（`bridge/host/RingerController`）
             * 认的档位一一对应。清单表与图标表都从这一处取，不再各写一遍字符串。
             */
            const val NORMAL = "normal"
            const val VIBRATE = "vibrate"
            const val SILENT = "silent"
        }
    }

    /**
     * 调屏幕亮度（`bright:50` / `bright:+10` / `bright:-10`）。
     *
     *  百分比说的是**亮度条上的位置**（`(raw − min) / (max − min)`），不是框架那个 HLG 感知
     *  百分比；量程、换算与写入见 `host/action/BrightnessAction` 与
     *  `docs/device-findings.md` 三十八、38.2。
     *
     *  [Mode.FIX] 是绝对值，[Mode.ADD] / [Mode.SUB] 在当前值上加减。三个档的百分比都按**量程**
     *  算（`span * percent / 100`），不是按当前值的比例；越界夹到量程两端，不报错。
     */
    data class Brightness(
        val mode: Mode,
        val percent: Int,
    ) : Action {
        enum class Mode { FIX, ADD, SUB }

        /**
         * token 里的百分比**夹在 0..100** —— 与解析侧（`splitPercent`）同一个区间。
         *
         *  不夹的话：`percent < 0` 的 FIX 会写出一串 `bright:-5`，读回来被解析成「减 5」
         *  （另一个档）；`percent > 100` 则解析失败回落成 `none`，绑定静默失效。
         *  夹在这里而不是构造时抛：界面滑杆出界不该让整个动作列表崩掉。
         */
        override val token: String =
            when (mode) {
                Mode.FIX -> "$BRIGHT_PREFIX${percent.coerceIn(0, 100)}"
                Mode.ADD -> "$BRIGHT_PREFIX+${percent.coerceIn(0, 100)}"
                Mode.SUB -> "$BRIGHT_PREFIX-${percent.coerceIn(0, 100)}"
            }
    }

    /**
     * 调媒体音量（`volume:50` / `volume:+10` / `volume:-10`）。
     *
     *  百分比说的是**音量条上的位置**（`当前档 / 最大档`），与亮度那一档同一套口径：三个档都按
     *  **整条量程**算（`max * percent / 100`），不是按当前值的比例；越界夹到两端，不报错。
     *  落在哪一路流（本机是媒体流）与写入见 `host/action/VolumeAction`。
     *
     *  与 `keyevent:24` / `keyevent:25`（注入一次音量键）的区别：那两条走的是系统自己的「按一下」，
     *  一次一格；这一条是「按百分比一段」，与屏幕亮度那一档成对。
     */
    data class Volume(
        val mode: Mode,
        val percent: Int,
    ) : Action {
        enum class Mode { FIX, ADD, SUB }

        /** 与 [Brightness] 同一条：token 里的百分比夹在 0..100，越界值不会变成另一个档。 */
        override val token: String =
            when (mode) {
                Mode.FIX -> "$VOLUME_PREFIX${percent.coerceIn(0, 100)}"
                Mode.ADD -> "$VOLUME_PREFIX+${percent.coerceIn(0, 100)}"
                Mode.SUB -> "$VOLUME_PREFIX-${percent.coerceIn(0, 100)}"
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
             * （候选与判据见 [com.lookie.opluskey.bridge.StatusBarBridge]）。
             */
            const val CONTROL_CENTER = "control_center"
        }
    }

    /**
     * 切一个**系统开关**（`setting:wifi` / `setting:rotation` / `setting:powersave` …）。
     *
     *  `id` 就是清单里那一节的短名，清单本体见 [SystemSwitches]。执行**不走 shell**：宿主读状态
     *  算目标值（`host/action/SettingSwitch`），写状态要么直接落 `Settings.Global`，要么交给
     *  system_server 反射调服务（`bridge/SettingCalls`）。模块不碰 SystemUI，也不需要
     *  把这些开关先加进控制中心。
     *
     *  [on] 是**目标状态**，三档各写一个 token：
     *   · `null` → `setting:wifi`（切换，老写法）；
     *   · `true` → `setting:wifi:on`；
     *   · `false` → `setting:wifi:off`。
     *  切换那一档仍要宿主先读当前状态（读不到就不动），明确的开 / 关不需要读。
     */
    data class Setting(
        val id: String,
        val on: Boolean? = null,
    ) : Action {
        override val token: String =
            when (on) {
                true -> "${Action.SETTING_PREFIX}$id$ON_SUFFIX"
                false -> "${Action.SETTING_PREFIX}$id$OFF_SUFFIX"
                null -> Action.SETTING_PREFIX + id
            }
    }

    /**
     * 改一个**自定义变量**（`setvar:计数+=1`）—— 变量那一套见 [Variable] / [Vars]。
     *
     *  [op] 决定怎么改（赋值 / 相加 / 相减 / 取反），[value] 是符号右边那一段原文：里面可以写
     *  `${其它变量}`，执行时才展开。**变量必须已经存在**：类型在新建那一刻定死，命令不能顺手造
     *  一个出来（那会让「类型不许改」这条约束在运行时被绕过去）。
     */
    data class VarSet(
        val name: String,
        val op: VarOp,
        val value: String = "",
    ) : Action {
        override val token: String = Action.SETVAR_PREFIX + name + op.symbol + value
    }

    companion object {
        // 各 token 的前缀与整串取值 —— **全项目只在这里写**。别处（`Action.parse`、界面层的
        //  图标表 / 文案表 / 清单表、日志脱敏）一律引用这些常量，不许再写字面量：同一个前缀散在
        //  几个文件里时，改一处而漏另一处的表现是「配置读出来是另一档」，而编译器不会提醒。
        //  前缀之外的「入口格标记」（`nav:` / `key:` / `zoom:`）不是动作取值、永不写进配置，
        //  它们归 `core/model/ActionCatalog`（那里是清单层）。

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

        /**
         * 应用的快捷方式（长按桌面图标那几个）。
         *
         *  与 [SHORTCUT_PREFIX] **只差开头四个字母**，`startsWith` 判定时谁先谁后都行
         *  （`appshortcut:` 不以 `shortcut:` 开头），但两支含义完全不同，别顺手合并。
         */
        const val APP_SHORTCUT_PREFIX = "appshortcut:"

        /** 快捷方式用小窗起 —— 紧跟在 [APP_SHORTCUT_PREFIX] 之后（`appshortcut:w:…`）。 */
        const val WINDOW_MARK = "w:"

        /**
         * 「打开应用」时用**原版启动包裹** —— 紧跟在 [APP_PREFIX] 之后（`app:a:目标`）。
         *
         *  为什么是**标记位**而不是另一个前缀（像 `window:` 那样）：这两档是同一件事（按包名/组件
         *  拉起一个界面）的两种起法，目标是同一串 `包名[/Activity]`、解析、命名、图标全都要复用；
         *  拆成两个前缀就得在两处各写一份「取目标」的推导。与小窗那一档的写法也一致
         *  （[WINDOW_MARK] 就是这么放的）。
         *
         *  `a` 是 animation；与小窗的 `w` 一样只占一个字母，省下的宽度都留给目标。
         */
        const val STOCK_ANIM_MARK = "a:"

        /**
         * 结束一个应用（`proc:stop:包名`）—— 见 [Action.Process]。
         *
         *  与「结束应用」那一格**不是同一个东西**：那一格是 `ActionCatalog.STOP_APP_TILE`
         *  （进挑应用那一页的关卡，永不写进配置），这一档是**真动作**，可以绑到按键上。
         */
        const val PROCESS_PREFIX = "proc:"

        /**
         * [PROCESS_PREFIX] 之后那一档 —— **只认「结束」**。
         *
         *  原来还有一档 `start:`（按 launcher 入口把应用拉起来）；它与「打开应用」（`app:`）做的是
         *  同一件事，两格并排只会让人以为「启动进程」和「打开应用」有区别，所以撤掉了。
         *  老配置里残留的 `proc:start:` 由 [parseProcess] 按「认不出」处理（落 `None`，不凭空执行）。
         */
        const val STOP_SUFFIX = "stop:"

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

        /** 系统开关（`setting:wifi`）—— 与「系统开关」那一格的清单一一对应。 */
        const val SETTING_PREFIX = "setting:"

        /**
         * 调屏幕亮度，后接百分比：`bright:50`（设为 50%）、`bright:+10` / `bright:-10`
         * （在当前值上按量程加减 10 个百分点）。
         */
        const val BRIGHT_PREFIX = "bright:"

        /**
         * 调媒体音量，后接百分比：`volume:50`（设为 50%）、`volume:+10` / `volume:-10`
         * （在当前值上按整条量程加减 10 个百分点）。
         */
        const val VOLUME_PREFIX = "volume:"

        /**
         * 改一个自定义变量（`setvar:<名字><符号><值>`）。
         *
         *  与 [SETTING_PREFIX] 一样是「认不出载荷就不落 shell」的那一类：名字 / 符号解析不出来时
         *  返回 [None]，不把一段看不懂的字交给 shell。
         */
        const val SETVAR_PREFIX = "setvar:"

        /** 系统开关后接「打开」这一档（`setting:wifi:on`）。 */
        const val ON_SUFFIX = ":on"

        /** 系统开关后接「关闭」这一档（`setting:wifi:off`）。 */
        const val OFF_SUFFIX = ":off"

        /**
         * `keyevent:<码>` 的拼法 —— 键码本体在 [KeyId] 一处（一、5），这里只负责拼成 token。
         * 清单里那些按键档位一律走它，不许再把数字写进字符串字面量。
         */
        fun keyToken(keyCode: Int): String = "$KEYEVENT_PREFIX$keyCode"

        /**
         * 解析一个动作字符串。
         *
         *  **必须带支持的前缀**（见 [hasSupportedFormat]）：认不出的写法按 [None] 处理、不执行 ——
         *  不再「默认当 shell」（手写省 `shell:` 那种写法已经取消）。
         *  不兼容 KSU 的按键名写法（`keyevent:KEYCODE_HOME`），只认数值。
         */
        fun parse(raw: String?): Action {
            val text = raw?.trim().orEmpty()
            return when {
                text.isEmpty() || text == NONE -> None

                text.startsWith(KEYEVENT_PREFIX) -> parseKeyEvent(text)

                text.startsWith(SHELL_PREFIX) -> Shell(text.removePrefix(SHELL_PREFIX).trim())

                // 必须排在兜底之前，否则 `cmd:` 会被当成 shell 命令执行
                text.startsWith(Command.PREFIX) -> Custom(text.removePrefix(Command.PREFIX).trim())

                text.startsWith(INTENT_PREFIX) -> Intent(text.removePrefix(INTENT_PREFIX).trim())

                text.startsWith(APP_PREFIX) -> parseAppToken(text)

                // 必须排在兜底之前，否则 `window:` 会被当成 shell 命令执行
                text.startsWith(WINDOW_PREFIX) -> AppWindow(text.removePrefix(WINDOW_PREFIX).trim())

                // 同理：`appshortcut:` 认不出也得先落在这里，别掉进 shell 兜底
                text.startsWith(APP_SHORTCUT_PREFIX) -> parseAppShortcut(text)

                text.startsWith(SHORTCUT_PREFIX) -> Shortcut(text.removePrefix(SHORTCUT_PREFIX).trim())

                // 进程控制（`proc:start:包名` / `proc:stop:包名`）：认不出就按不支持处理，不落 shell
                text.startsWith(PROCESS_PREFIX) -> parseProcess(text)

                text == FLASHLIGHT -> Flashlight

                // 必须排在兜底之前，否则 `ringer:` 会被当成 shell 命令执行
                text.startsWith(RINGER_PREFIX) -> Ringer(text.removePrefix(RINGER_PREFIX).trim())

                // 同上：认不出的前缀会落到 shell 兜底
                text == POWER_MENU -> PowerMenu

                // 同上：`zoom:` 也是认不出就落到 shell 兜底的那一类。
                // 两档都是**精确比较**（`zoom:mini` 与 `zoom:mini:collapse` 互不相等），所以这两支
                // 之间**没有顺序要求**；这里真正要守的是它们都排在 `else -> Shell` 兜底之前。
                text == ZOOM_MINI_COLLAPSE -> MiniWindow(collapsed = true)

                text == ZOOM_MINI -> MiniWindow(collapsed = false)

                text.startsWith(OPLUS_PREFIX) -> Oplus(text.removePrefix(OPLUS_PREFIX).trim())

                // 同上：`panel:` 也是认不出就落到 shell 兜底的那一类
                text.startsWith(PANEL_PREFIX) -> Panel(text.removePrefix(PANEL_PREFIX).trim())

                // 系统开关（`setting:<id>` / `setting:<id>:on` / `setting:<id>:off`）：
                //  `id` 认不出时交给执行侧报「没接执行」，不落 shell
                text.startsWith(SETTING_PREFIX) -> parseSetting(text)

                // 屏幕亮度（`bright:50` / `bright:+10`）：百分比认不出就按不支持处理，不落 shell
                text.startsWith(BRIGHT_PREFIX) -> parseBrightness(text)

                // 媒体音量（`volume:50` / `volume:+10`）：与亮度同一套百分比写法
                text.startsWith(VOLUME_PREFIX) -> parseVolume(text)

                // 自定义变量（`setvar:计数+=1`）：认不出就按不支持处理，不落 shell
                text.startsWith(SETVAR_PREFIX) -> parseVarSet(text)

                // **不再默认按 shell 执行**（原先这里是 `Shell(text)`，手写命令可省 `shell:`）。
                //  现在「命令详情」必须显式带前缀，界面那一栏已经拦住（见 [hasSupportedFormat]）；
                //  这里兜的是手改配置文件 / 老版本存下来的裸写法 —— 静默不执行，只留一行日志。
                //  宁可不跑，也不把一段不知道是什么的字符串交给 shell。
                //  独立成 [unsupported] 是为了让本函数留在 `LongMethod` 之内。
                else -> unsupported(text)
            }
        }

        /**
         * [parse] 的兜底：认不出格式的写法**不执行**，留一行日志便于排查旧配置。
         *
         *  日志里把**后果**也写出来：这条写法在模型里就是 `none`，用户下次在界面上动一下配置、
         *  整份文件被重新序列化时，原文就没了（手改文件是本项目支持过的工作流，所以这行必须说清）。
         *  不做「原样保留」是**有意的**：那要新增一个动作类型，属动作契约变更（AGENTS.md 一、4），
         *  而收益只是保住一条本来就执行不了的写法。
         */
        private fun unsupported(text: String): Action {
            XLog.w("动作解析：这条写法没有支持的前缀，不执行；下次保存会归一成 none：${text.take(200)}")
            return None
        }

        /**
         * 这段文本**是不是以支持的格式开头** —— 「命令详情」那一栏的硬性要求。
         *
         *  早先认不出前缀的写法一律按 shell 执行（手写命令可省 `shell:`），「命令格式」那张卡
         *  第一行也就写着「默认为 shell 命令」。现在取消这个默认：命令必须显式带前缀，
         *  否则既不保存、也不执行（用户口径「去掉默认为 shell 和 `cmd:`，对命令详情限制，
         *  必须以支持的格式开头」）。
         *
         *  判据就是 [parse] 里认的那几样：几个**精确比较**的字面量，加上各条 `xxx:` 前缀。
         *  往 [parse] 里加新前缀时，记得同步 [PREFIXES] —— 两处漂移会让新格式「能执行但存不进去」。
         */
        fun hasSupportedFormat(raw: String?): Boolean {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return false
            return text == NONE ||
                text == FLASHLIGHT ||
                text == POWER_MENU ||
                text == ZOOM_MINI ||
                text == ZOOM_MINI_COLLAPSE ||
                PREFIXES.any { text.startsWith(it) }
        }

        /**
         * 这段文本开头那个**支持的前缀**（`shell:` / `cmd:` …）；没有前缀、认不出时返回 `null`。
         *
         *  与 [hasSupportedFormat] 同一份 [PREFIXES]：格式列表点一下要「只换前缀、正文留着」时，
         *  靠它找到该被换掉的那一段 —— 自己再列一遍前缀就会与 [parse] 漂移。
         */
        fun prefixOf(raw: String?): String? {
            val text = raw?.trim().orEmpty()
            return PREFIXES.firstOrNull { text.startsWith(it) }
        }

        /**
         * [parse] 认的所有前缀 —— 与那串 `startsWith` 分支一一对应（顺序无关，只用来判断
         * 「这段字有没有带格式」）。[Command.PREFIX]（`cmd:`）也在这一列里。
         */
        private val PREFIXES: List<String> =
            listOf(
                KEYEVENT_PREFIX,
                SHELL_PREFIX,
                Command.PREFIX,
                INTENT_PREFIX,
                APP_PREFIX,
                WINDOW_PREFIX,
                APP_SHORTCUT_PREFIX,
                SHORTCUT_PREFIX,
                RINGER_PREFIX,
                OPLUS_PREFIX,
                PANEL_PREFIX,
                SETTING_PREFIX,
                BRIGHT_PREFIX,
                VOLUME_PREFIX,
                SETVAR_PREFIX,
                PROCESS_PREFIX,
            )

        /**
         * 这段字能不能当**包名**用 —— 至少两段、每段字母开头、只含标识符字符。
         *
         *  只挡两类东西：`ps` 里那些不是应用的进程名（`/system/bin/…`、`init`），
         *  以及**拼进 shell 之前**那道注入校验（`proc:` 与进程控制页共用这一把尺子）。
         */
        fun isPackageName(text: String): Boolean = PACKAGE_NAME.matches(text)

        /** 见 [isPackageName]。 */
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        /**
         * `setting:<id>[:on|:off]` 那一支 —— 与 [parseKeyEvent] 同一个理由（[parse] 贴着长度上限）。
         *
         *  后缀只在**最后一段**上认：`id` 里不带冒号，所以认不出后缀时整段原样当 id
         *  （老配置与将来新增的 id 都不会被这条规则伤到）。
         */
        private fun parseSetting(text: String): Action {
            val body = text.removePrefix(SETTING_PREFIX).trim()
            val cut = body.lastIndexOf(':')
            if (cut < 0) return Setting(body)
            val id = body.substring(0, cut).trim()
            return when (body.substring(cut + 1).trim()) {
                "on" -> Setting(id, on = true)
                "off" -> Setting(id, on = false)
                else -> Setting(body)
            }
        }

        /**
         * `setvar:<名字><符号><值>` 那一支 —— 与 [parseSetting] 同一个理由（[parse] 贴着长度上限）。
         *
         *  名字 / 符号 / 值任何一段认不出都按 [None] 处理（不执行、留一行日志）：这是「写配置」的
         *  动作，猜错的后果比不执行大。
         */
        private fun parseVarSet(text: String): Action {
            val body = text.removePrefix(SETVAR_PREFIX).trim()
            val (name, op, value) =
                VarOp.split(body) ?: run {
                    XLog.w("动作 setvar: 的写法认不出（按 none 处理）：$text")
                    return None
                }
            //  名字要在**建的时候**就合法（见 `VariableName`）：这里只是提前挡一道，
            //  真正「有没有这一颗」由执行侧的变量表判
            if (VariableName.normalize(name) == null) {
                XLog.w("动作 setvar: 的变量名不合法（按 none 处理）：$text")
                return None
            }
            return VarSet(name, op, value)
        }

        /**
         * `keyevent:<码>` 那一支 —— 与 [parseAppShortcut] 拆出来的理由相同（[parse] 贴着长度上限）。
         */
        private fun parseKeyEvent(text: String): Action {
            val body = text.removePrefix(KEYEVENT_PREFIX).trim()
            if (body.isEmpty()) return None
            val code = body.toIntOrNull()
            return when {
                code == null -> {
                    XLog.w("动作 keyevent: 的按键无法识别（按 none 处理）：$text")
                    None
                }

                //  侧键那一族码（735 / 780 / 781 / 782）**不许注入**：拦截器按
                //  `acceptedKeyCodes` 认领它们，注入出去会再走一遍这条路径 ⇒ 自己触发自己，
                //  而且是**在 system_server 里**每 ~100ms 一轮的自持循环（`isSynthetic` 那道
                //  放行判据只认 `INJECTABLE_CODES`，这几个码不在表里）。
                //  别的码（含音量 / 电源 / 媒体）照旧放行：它们注入的那一下由 `isSynthetic` 认出来。
                //  族码（压感拍照键那类）同样是模块认领的码 —— 漏了这一条，写 `keyevent:767`
                //  绑给那颗键就是自持循环。
                //  「认不认这个码」只有 [KeyId.claimsBuiltin] 一份答案：注入前复核
                //  （`ActionRunner.claimedByModule`）问的是同一句，两处各摊一遍就会漂。
                KeyId.claimsBuiltin(code) || RomKeyFamilies.isFamilyCode(code) -> {
                    XLog.w("动作 keyevent:$code 是模块自己认领的键码（会自持循环，按 none 处理）：$text")
                    None
                }

                else -> {
                    KeyEvent(code)
                }
            }
        }

        /**
         * `appshortcut:包名/快捷方式id` 那一支。
         *
         *  写法与 [AppShortcut] 的 token 同源：`AppTarget.format("包名/id", userId)` ——
         *  用户前缀按 `AppTarget` 的口径剥。老配置里可能还跟在 `|名字` 后面（那时名字是塞在
         *  token 里的），`withoutUser` 会一并剥掉，所以那些值照读。
         *
         *  缺包名或 id 的一律按「认不出」处理（[None]），不猜。
         */
        private fun parseAppShortcut(text: String): Action {
            val raw = text.removePrefix(APP_SHORTCUT_PREFIX).trim()
            val window = raw.startsWith(WINDOW_MARK)
            val body = if (window) raw.removePrefix(WINDOW_MARK) else raw
            val plain = AppTarget.withoutUser(body)
            val pkg = plain.substringBefore('/')
            val id = plain.substringAfter('/', "")
            if (pkg.isEmpty() || id.isEmpty()) {
                if (plain.isNotEmpty()) {
                    XLog.w("动作 appshortcut: 缺包名或快捷方式 id（按 none 处理）：$text")
                }
                return None
            }
            return AppShortcut(pkg, id, AppTarget.userId(body), window)
        }
    }
}

/**
 * `<前缀>[+|-]<百分比>` 拆成「符号 + 数值」；认不出（空 / 不是数字 / 不在 `0..100`）返回 `null`。
 *
 *  亮度与音量两族共用这一处：同一段解析抄两遍，改一处漏一处是迟早的事。
 *  放在文件级而不是 [Action.Companion] 里：它不碰任何 token 取值，只是纯字符串拆分，
 *  塞进那个伴生对象只会让它的函数数一路涨（见 `config/detekt` 的 `TooManyFunctions`）。
 */
private fun splitPercent(
    text: String,
    prefix: String,
): Pair<Char, Int>? {
    val body = text.removePrefix(prefix).trim()
    if (body.isEmpty()) return null
    val sign = body.first()
    val rest = if (sign == '+' || sign == '-') body.substring(1) else body
    val percent = rest.trim().toIntOrNull() ?: return null
    return if (percent in 0..100) sign to percent else null
}

/**
 * `proc:stop:<包名>` 那一支 —— 与 [parseBrightness] 同一个理由
 * （[Action.parse] 贴着长度上限，所以摊到文件级）。
 *
 *  认不出包名、或档位不是 `stop:`（包括老配置里的 `start:`）都按 `None` 处理：包名会被拼进
 *  shell（`am force-stop <包名>`），猜错就是把一段看不懂的东西交给 root shell。
 */
private fun parseProcess(text: String): Action {
    val body = text.removePrefix(Action.PROCESS_PREFIX).trim()
    if (!body.startsWith(Action.STOP_SUFFIX)) {
        XLog.w("动作 proc: 的档位认不出（只认 stop:，按 none 处理）：$text")
        return Action.None
    }
    val pkg = body.removePrefix(Action.STOP_SUFFIX).trim()
    if (!Action.isPackageName(pkg)) {
        XLog.w("动作 proc: 的包名不像包名（按 none 处理）：$text")
        return Action.None
    }
    return Action.Process(pkg)
}

/**
 * `bright:<百分比>` / `bright:+<百分比>` / `bright:-<百分比>` 那一支 —— 与 [parseSetting]
 * 同一个理由（[parse] 贴着长度上限）。
 *
 *  百分比只收 `0..100` 的整数：这是**人写的**一档，超出量程的写法（`bright:300`）几乎都是
 *  笔误，按「认不出」处理比悄悄夹到 100 更像话。加减档的符号就是前缀后面那一个字符，
 *  其余一律当绝对档。
 */
private fun parseBrightness(text: String): Action {
    val (sign, percent) =
        splitPercent(text, Action.BRIGHT_PREFIX) ?: run {
            XLog.w("动作 bright: 的百分比认不出（按 none 处理）：$text")
            return Action.None
        }
    return when (sign) {
        '+' -> Action.Brightness(Action.Brightness.Mode.ADD, percent)
        '-' -> Action.Brightness(Action.Brightness.Mode.SUB, percent)
        else -> Action.Brightness(Action.Brightness.Mode.FIX, percent)
    }
}

/**
 * `volume:<百分比>` / `volume:+<百分比>` / `volume:-<百分比>` 那一支 —— 写法与
 * [parseBrightness] 完全一致，拆法共用 [splitPercent]。
 */
private fun parseVolume(text: String): Action {
    val (sign, percent) =
        splitPercent(text, Action.VOLUME_PREFIX) ?: run {
            XLog.w("动作 volume: 的百分比认不出（按 none 处理）：$text")
            return Action.None
        }
    return when (sign) {
        '+' -> Action.Volume(Action.Volume.Mode.ADD, percent)
        '-' -> Action.Volume(Action.Volume.Mode.SUB, percent)
        else -> Action.Volume(Action.Volume.Mode.FIX, percent)
    }
}

/**
 * `app:目标` / `app:a:目标` 那一支 —— `Action.parse` 的一个分支。
 *
 *  写法与 [Action.App] 的 token 同源：[Action.STOCK_ANIM_MARK] 是夹在前缀与目标之间的标记位，
 *  目标本体**不含**这个标记 —— `AppTarget.pkg`、命名、图标、同一性判据那一整套才不用各认一遍。
 *
 *  目标为空（`app:` 后面什么都没有、或只有那个标记）按「认不出」处理（[Action.None]）：
 *  与 `parseAppShortcut` 同一条口径，不猜。
 *
 *  写成**顶层**函数而不是 `Companion` 的成员：那个伴生对象已经顶着 detekt 的函数数阈值
 *  （`TooManyFunctions`），而它只吃一个字符串、不碰任何状态。
 */
private fun parseAppToken(text: String): Action {
    val raw = text.removePrefix(Action.APP_PREFIX).trim()
    val stockAnim = raw.startsWith(Action.STOCK_ANIM_MARK)
    val body = if (stockAnim) raw.removePrefix(Action.STOCK_ANIM_MARK).trim() else raw
    if (body.isEmpty()) {
        if (raw.isNotEmpty()) XLog.w("动作 app: 缺目标（按 none 处理）：$text")
        return Action.None
    }
    return Action.App(body, stockAnim)
}
