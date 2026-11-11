package com.lookie.opluskey.host.action

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import com.lookie.opluskey.bridge.StatusBarBridge
import com.lookie.opluskey.bridge.host.FlashlightController
import com.lookie.opluskey.bridge.host.KeyInjector
import com.lookie.opluskey.bridge.host.RingerController
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.config.VariableStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.KsuCompat
import com.lookie.opluskey.core.model.expandedWith
import com.lookie.opluskey.core.rom.RomKeyFamilies
import com.lookie.opluskey.core.rom.RomProfile
import com.lookie.opluskey.core.wire.ModuleWire

/**
 * 动作派发器，只服务侧键。
 *
 *  成员数贴着 `TooManyFunctions` 的阈值：一种动作一个 `runXxx`，这一层就是那张分派表本身 ——
 *  拆开之后「支持哪些动作」反而要翻好几个文件才数得清。
 */
@Suppress("TooManyFunctions")
internal class ActionRunner(
    /** 借来的宿主 Context（`host/GestureReceiver` 从 `ActivityThread` 反射取）。 */
    @Volatile var context: Context?,
    handler: Handler,
) {
    /**
     * 这一次执行走不走 ColorOS 接口 —— [run] 每次开头写入，往下（手电筒 / 响铃）都读它。
     *
     *  为什么收在这里：它同时被三个执行器用（手电筒、响铃、免打扰），各自读一遍配置的话
     *  「默认值」就有三份副本；而这一次到底走哪条，取决于**按键级**那一格
     *  （`Config.effectiveFor` 的 `.colorOsApi`），只有调用方知道是哪一档 —— 所以由 `run` 传进来存这一格。
     *
     *  **这一格是「本次执行」的槽位**：写入与整段 [dispatch] 由 [runState] 串成一段（见 [run]）。
     *  不串的话，两次执行交叠时后进的那次会改写它，先进的那次在手电筒 / 响铃里读到的是别人的档位。
     */
    @Volatile
    private var runColorOsApi: Boolean = ConfigStore.read().global.colorOsApi

    private val isColorOsApi: () -> Boolean = { runColorOsApi }

    /**
     * 这一次执行的「振动反馈」—— 同样由 [run] 每次开头写入，与 [runColorOsApi] 一条规矩。
     *
     *  它只给「切响铃那一下的触觉」用（`RingerHaptics`，见 [RingerController]）；动作前那一下
     *  通用振动在 `GestureExec.haptic`，那边自己按生效值判。
     *
     *  为什么必须跟着按键走：这个开关在「独立设置」里可以每颗键 / 每组组合各设一份
     *  （`Config.effectiveFor(owner).vibrate`）。原先这里直接读 `global.vibrate` ——
     *  于是「公共关、这颗键开」时切响铃不响（该响），「公共开、这颗键关」时反而响（该静）。
     */
    @Volatile
    private var runVibrate: Boolean = ConfigStore.read().global.vibrate

    /**
     * [run] 里「写这两格 → 整段派发」那把锁。
     *
     *  独立一个对象而不是锁 `this`：本实例被多个执行器持有引用，锁自己等于把它们的等待也串进来。
     */
    private val runState = Any()

    private val injector = KeyInjector { context }
    private val flashlight = FlashlightController({ context }, handler, isColorOsApi)
    private val ringer =
        RingerController(
            { context },
            isColorOsApi,
            // 「振动反馈」与 `isColorOsApi` 同源：都从这一层的这一格来，执行器自己不读配置
            { runVibrate },
        )
    private val launcher = AppLauncher({ context }, ::notifyFailure)
    private val shortcut = AppShortcutLaunch({ context }, launcher.mini, ::notifyFailure)
    private val custom =
        CustomCommandRunner(
            ::runShell,
            ::dispatch,
            ::notifyFailure,
        )

    /**
     * 释放执行器持有的**进程级注册**（现在只有手电筒那条 torch 回调）。
     *
     *  宿主主进程里不需要（进程活多久就活多久）；`:ui` 进程里那份「测试命令」执行器在关页时调它，
     *  否则每轮「进设置 → 测试手电筒 → 关页」都会在系统里留一份不会再被用的回调。
     */
    fun releaseTorch() {
        flashlight.release()
    }

    /**
     * 「灵感语音 · 开始」上一次触发的时间（`uptimeMillis`）—— 连按不重复起采集。
     *
     *  ROM 对重入的「开始」本来就是 no-op（`reentrant collect.`，见
     *  `docs/device/actions/voice.md` 四十六）：这道窗口只省一次 `startCollect`，顺带在日志里
     *  留一行「这一下被挡了」的依据 —— 否则连按两次时看不出是模块挡的还是 ROM 挡的。
     *
     *  `@Volatile`：同一次按压的重复派发可能落在不同线程（执行线程 / 连发链）。
     */
    @Volatile
    private var lastVoiceMemoryAt = 0L

    /**
     * 起一条 ColorOS 落点 —— 灵感语音「开始」单独有「连按窗口」，其余照旧。
     *
     *  写成 lambda 字段而不是方法：这个类的函数数已经贴着 detekt 的上限。
     */
    private val startOplusOrVoice: (String) -> Unit = { feature ->
        val now = android.os.SystemClock.uptimeMillis()
        val gap = now - lastVoiceMemoryAt
        if (feature == RomProfile.NAME_VOICE_MEMORY && gap in 0 until VOICE_MEMORY_GAP_MS) {
            XLog.w("灵感语音：${gap}ms 内又按了一次，忽略（ROM 侧本来也当重入丢掉）")
        } else {
            if (feature == RomProfile.NAME_VOICE_MEMORY) lastVoiceMemoryAt = now
            launcher.startOplusFeature(feature)
        }
    }

    /** 执行 oplus 动作：免打扰走 AOSP 或 ColorOS，其余走落点。 */
    private val runOplus: (String) -> Unit = { feature ->
        if (feature == RomProfile.NAME_VOICE_ASSIST) {
            //  唤醒小布**没有宿主落点**：ROM 是用 system_server 自己的身份去起语音服务
            //  （见 `RomProfile.NAME_VOICE_ASSIST`），宿主未必有那个签名级权限 ⇒ 转给注入侧。
            XLog.w("oplus:$feature → 转给 system_server 起语音服务")
            sendStatusbar(StatusBarBridge.CMD_VOICE_ASSIST)
        } else if (feature == RomProfile.NAME_NO_DISTURB && !isColorOsApi()) {
            XLog.w("oplus:$feature → AOSP 标准接口（ColorOS 接口已关闭）")
            runAospDnd()
        } else {
            startOplusOrVoice(feature)
        }
    }

    /** 失败提示的唯一出口，转成 Toast；只有这里碰 Context 与 UI。 */
    private fun notifyFailure(failure: CommandFailure) {
        //  失败回执仍然走这一条既有通道；顺带记下「这一次失败了」——
        //  [GestureExec] 据此决定要不要置 `lastActionAt` / 补发报活：两者都是「真的做成了」的凭证，
        //  失败时置位等于把成功报成故障的反面（假绿灯）。
        lastRunFailed = true
        FailureNotice.show(context, failure)
    }

    /**
     * 这一次 [run] 有没有在**同步段**内报过失败 —— 由 [notifyFailure] 置位。
     *
     *  为什么要这个标记：动作分支有一整张 `when`（二十来种），逐个改成返回布尔等于把契约铺到
     *  每个执行器里；而失败的**唯一出口**本来就只有 [notifyFailure] 一处，盯住它即可。
     *
     *  **只看同步段**：`shell:` 那类失败在池线程上晚到，`run` 早就返回了 —— 那一笔由它自己的
     *  失败提示负责，这里不冒充知道。
     */
    @Volatile
    private var lastRunFailed = false

    /**
     * `appshortcut:` 那一支 —— 单独一支只为让 [dispatch] 别顶破 detekt 的长度上限
     * （那五个实参一行放不下，折行要占三行）。
     */
    private fun runAppShortcut(action: Action.AppShortcut) = shortcut.launch(action.pkg, action.shortcutId, action.userId, action.window)

    /** 系统面板那一档：目前只有控制中心一条通道，认不出的档位只记日志、不猜目标。 */
    private fun runPanel(mode: String) {
        if (mode == Action.Panel.CONTROL_CENTER) {
            sendStatusbar(StatusBarBridge.CMD_CONTROL_CENTER)
        } else {
            XLog.w("不认识的系统面板档位（已忽略）：$mode")
        }
    }

    /**
     * 一个 `setvar:` 动作：改一个变量并立刻落盘（变量那一套见 `VariableStore`）。
     *
     *  失败（变量不存在 / 类型不符 / 值归一不了）报一句失败提示，与「命令不存在」同一档 ——
     *  按下去确实什么都没发生，不该静默。原因由 `VariableStore.apply` 写进日志。
     */
    private fun runVarSet(action: Action.VarSet) {
        if (VariableStore.apply(action)) return
        XLog.w("变量没写进去（已忽略）：${action.token}")
        notifyFailure(CommandFailure.UNKNOWN)
    }

    /**
     * 执行一个手势对应的动作。
     *
     *  @param gestureLabel 形如 `侧键 - 长按`（**键名 - 手势名**）—— 由 `host/GestureReceiver`
     *    用 [KeyId.label] 与手势名拼好传进来。**这里不写死键名**：本类只服务侧键，但「有几个键、
     *    各叫什么」是模型（[KeyId]）的事，在日志里另抄一份字面量，等第二个键出现时就会对不上
     *    （真机踩到过：两边各拼一次，日志成了 `侧键 - 侧键 - 长按`）。
     *
     *  下面这行与 `GestureReceiver` 里那行（带「（宿主进程执行）」后缀）**是刻意的一对，不要合并**：
     *  调用点在那两行之间还夹着报活广播、触觉、执行时提示，所以「前一行有、后一行没有」正好定位到
     *  那一段出的问题。本行才是「动作真的开始派发」的判据，与 [runTest] 的「测试命令 →」相对。
     *
     *  @return 这一次在**同步段**里成功了没有（失败 = [notifyFailure] 被调用过）。
     *   异步失败（`shell:` 超时 / 被拒）不算进来，理由见 [lastRunFailed]。
     */
    fun run(
        action: Action,
        gestureLabel: String,
        /**
         * 这一档**实际**走不走 ColorOS 接口 —— 由调用方从 `Config.effectiveFor(...).colorOsApi` 取好传进来
         * （单键传 `KeyConfig`、组合传 `ComboKey`，**这一层不区分**，也不自己读配置）。
         *
         *  缺省值读公共设置：只服务「测试命令」那种没有按键上下文的调用。
         */
        colorOsApi: Boolean = ConfigStore.read().global.colorOsApi,
        /**
         * 这一档**实际**的「振动反馈」（`Config.effectiveFor(...).vibrate`）—— 与 [colorOsApi]
         * 同一档来源。只给「切响铃那一下的触觉」用（见 [runVibrate]）。
         *
         *  缺省值读公共设置：只服务「测试命令」那种没有按键上下文的调用。
         */
        vibrate: Boolean = ConfigStore.read().global.vibrate,
    ): Boolean {
        //  「这一次是哪一档」与「派发」必须是一段：两个执行器（手电筒 / 响铃）在构造时就绑定了
        //  `isColorOsApi` / `runVibrate` 这两条闭包，它们不知道这一次是哪一档按键，只能读这两格。
        //  写入与 `dispatch` 分处两段时，交叠的第二次执行会改写这两格 ⇒ 先进的那次拿着别人的
        //  档位去开手电筒 / 切响铃。锁只圈住写入到派发返回这一段（异步回调本来就在其后的线程上）。
        synchronized(runState) {
            runColorOsApi = colorOsApi
            runVibrate = vibrate
            lastRunFailed = false
            XLog.w("$gestureLabel → ${XLog.redactToken(action.token)}")
            dispatch(action)
            return !lastRunFailed
        }
    }

    /**
     * 设置页「测试命令」按钮走这里，日志不套「侧键 -」前缀（那一条是「侧键真被按了」的判据）。
     * 只在设置页进程（`:ui`）里跑，不走广播给宿主主进程那条路（那个进程开机后可能还没起来）。
     *
     * **跑的是传进来的这条命令本身**，不回库里按名字查 —— 草稿态下它还没保存（或者库里存的是
     * 改之前那一版），按名字跑测的就不是眼前这份。
     */
    fun runTest(cmd: Command) {
        XLog.w("测试命令 → ${cmd.name.ifBlank { "（未命名）" }}")
        custom.run(cmd, 0)
    }

    /**
     * 真正干活的那一层；自定义命令的步骤也走同一套派发（无手势标签）。
     *
     * @param depth 当前自定义命令的嵌套深度，只为 `cmd:` 自引 / 互引兜底 ——
     *   单条命令正文里再写 `cmd:` 就是一次同步重入，必须把深度带下去（见 `CustomCommandRunner`）。
     */
    private fun dispatch(
        action: Action,
        depth: Int = 0,
    ) {
        try {
            //  `${变量}` 在这里展开一次（解析发生在存配置时，值要到这一刻才知道，见 `expandedWith`）。
            //  放在 `when` 之前而不是每个分支里：漏一个分支就是「那一种动作里写变量不生效」，
            //  而这种事在界面上完全看不出来。
            when (val act = action.expandedWith(VariableStore.cached())) {
                //  「无操作」：什么都不做（保留这一支只为让 `when` 覆盖全部子类）
                is Action.None -> Unit

                //  「跟随默认」只该出现在场景块里，判定侧见到它就跳过那一档 —— 真跑到执行侧
                //  说明有人把它当成了普通动作绑在别处：什么都不做，与小节标题那句「无操作」同一档
                is Action.Follow -> Unit

                is Action.KeyEvent -> runKeyEvent(act.keyCode)

                is Action.Shell -> runShell(act.command)

                is Action.Custom -> custom.run(act.name, depth)

                is Action.Intent -> launcher.openIntent(act.action)

                is Action.App -> launcher.launchApp(act)

                is Action.AppWindow -> launcher.launchAppInWindow(act.target)

                is Action.AppShortcut -> runAppShortcut(act)

                is Action.MiniWindow -> launcher.putForegroundInMiniWindow(act.collapsed)

                is Action.Shortcut -> launcher.runShortcut(act.shortcutId)

                is Action.Flashlight -> flashlight.toggle()

                is Action.Ringer -> ringer.setMode(act.mode)

                is Action.Brightness -> BrightnessAction.run(context, act, ::notifyFailure)

                is Action.Volume -> runMediaVolume(act)

                is Action.RingerVolume -> runRingerVolume(act)

                is Action.PowerMenu -> sendStatusbar(StatusBarBridge.CMD_POWER_MENU)

                is Action.Oplus -> runOplus(act.feature)

                is Action.Panel -> runPanel(act.mode)

                is Action.Setting -> SettingSwitch.toggleOrFlashlight(context, act, flashlight, ::notifyFailure)

                is Action.VarSet -> runVarSet(act)

                // 结束应用（`proc:stop:…`）：异步发一条 `su`，不等结果
                is Action.Process -> ProcessAction.run(act, ::notifyFailure)
            }
        } catch (e: Throwable) {
            // 必须 catch Throwable：执行环境（Runtime / 反射注入）出错可能是 Error 系，漏出去会崩系统
            XLog.w("执行动作失败（已忽略）：${XLog.redactToken(action.token)}", e)
            notifyFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 一个 `keyevent:` 动作：大多走注入，通知中心 / 最近任务 / 静音键 / 电源键 / 音量走特殊通道。 */
    private fun runKeyEvent(keyCode: Int) {
        when (keyCode) {
            KeyId.KEYCODE_NOTIFICATION -> {
                sendStatusbar(StatusBarBridge.CMD_NOTIFICATIONS)
            }

            KeyId.KEYCODE_APP_SWITCH -> {
                sendStatusbar(StatusBarBridge.CMD_RECENTS)
            }

            KeyId.KEYCODE_VOLUME_MUTE -> {
                ringer.cycle()
            }

            KeyId.KEYCODE_POWER -> {
                togglePower(context, ::notifyFailure)
            }

            KeyId.KEYCODE_VOLUME_UP -> {
                adjustVolume(android.media.AudioManager.ADJUST_RAISE)
            }

            KeyId.KEYCODE_VOLUME_DOWN -> {
                adjustVolume(android.media.AudioManager.ADJUST_LOWER)
            }

            else -> {
                //  **注入前先对照当前配置**：`keyevent:` 只挡住了内置键与 ROM 族码（解析侧拿不到
                //  自定义键的码），而自定义键的码是运行时收集来的 —— 注入同一个码会被拦截路径
                //  再认领一次，于是一次动作变成 system_server 里的自持循环。判据只能用**现读的**
                //  配置：这条路径一次动作走一次，读盘成本可以忽略，缓存反而会漏掉刚收集的键。
                if (claimedByModule(keyCode)) {
                    XLog.w("keyevent:$keyCode 是模块自己认领的键码（注入会自持循环，已拒绝执行）")
                    notifyFailure(CommandFailure.UNKNOWN)
                } else if (!injector.inject(keyCode)) {
                    //  以前这里只看「没抛异常」，于是「动作执行了、屏幕没反应」在用户侧完全没有回执
                    notifyFailure(CommandFailure.UNKNOWN)
                }
            }
        }
    }

    /** 这个键码现在归模块管吗（内置键的码 / 自定义键收集来的码 / ROM 族码）。 */
    private fun claimedByModule(code: Int): Boolean {
        if (KeyId.claimsBuiltin(code)) return true
        if (RomKeyFamilies.isFamilyCode(code)) return true
        return try {
            ConfigStore.read().keyForCode(code) != null
        } catch (e: Throwable) {
            XLog.w("注入前读配置失败（按「不归模块管」继续）：${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * 音量键：**不注入按键**，直接调系统音量。
     *
     *  注入的 24 / 25 会再走一遍输入层：物理电源键按着时，输入层把每一个注入的音量键当成
     *  「电源 + 音量」组合的另一半，音量那一路被拿去做组合判定（而模块为了不截图又把组合吞掉），
     *  表现就是「连续触发只生效一次」。`adjustSuggestedStreamVolume` 不经过输入层，绕开它。
     *
     *  两个参数都与按音量键同口径：`USE_DEFAULT_STREAM_TYPE` = 当前活跃那条流（放音乐调媒体、
     *  通话调通话，不写死媒体流），`FLAG_SHOW_UI` 弹的就是系统自己那条音量条。
     */
    private fun adjustVolume(direction: Int) {
        val ctx =
            context ?: run {
                XLog.w("没有 Context，音量调整跳过（方向 $direction）")
                return
            }
        try {
            val am =
                ctx.getSystemService(android.media.AudioManager::class.java) ?: run {
                    XLog.w("拿不到 AudioManager，音量调整跳过")
                    return
                }
            am.adjustSuggestedStreamVolume(
                direction,
                android.media.AudioManager.USE_DEFAULT_STREAM_TYPE,
                android.media.AudioManager.FLAG_SHOW_UI or android.media.AudioManager.FLAG_PLAY_SOUND,
            )
        } catch (e: Throwable) {
            XLog.w("音量调整失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }

    /**
     * 通知中心 / 控制中心 / 最近任务：发一条标记广播让 system_server 执行 —— 这台 ROM 上
     * `StatusBarManager` 取不到、`expandNotificationsPanel` 被 `EXPAND_STATUS_BAR` 拒、
     * 注入那两个码也打不通，只能走广播。
     */
    private fun sendStatusbar(cmd: String) {
        val ctx =
            context ?: run {
                XLog.w("没有 Context，statusbar:$cmd 跳过")
                return
            }
        StatusBarBridge.send(ctx, cmd)
    }

    /**
     * 执行一条 `shell:` 命令。
     *
     *  「小布识屏」那条别名换成原生调用（`startForegroundService`，不走 shell）。
     *  **「截图」那条别名走真的 shell** —— 见下面 [KsuCompat.isSidebarAlias] 那一支上方的说明。
     *
     * @param preferRoot 是否以 root 执行（`true` = 只走 `su`，`false` = 只走 `sh`；两者互不兜底）。
     *   单条命令按它**自己**那个开关传进来（`Command.root`，界面在「命令设置 → 以 root 执行」）；
     *   内置 `shell:` 动作与多条命令的步骤没有这个开关，一直按默认 `true` 走。
     */
    private fun runShell(
        command: String,
        preferRoot: Boolean = true,
        /**
         * 「这一条跑完了」的回执 —— **只给多条命令的步骤间隔用**（`CustomCommandRunner` 要等这一步
         * 真的结束才排下一步，见 `CustomCommandRunner.runSteps`）。
         *
         *  **每一条出口都要调**：漏一个，那条链就永远停在半路、`runningSerial` 的占位不还，
         *  之后所有多条命令都会被「已有命令链在跑」挡掉。所以下面每一处 `return` 之前都先 settle。
         *  `null` = 不用回执（单条命令、内置 `shell:` 动作都传 null）。
         */
        onSettled: (() -> Unit)? = null,
    ) {
        //  一次性：`ShellRunner.exec` 的 onDone 与下面那几处提前 return 只会走到其中一条，
        //  但这层保护便宜 —— 重复回执会让「等回执」那一档把同一步跑两遍。
        val once =
            java.util.concurrent.atomic
                .AtomicBoolean(onSettled == null)
        val settle: () -> Unit = { if (once.compareAndSet(false, true)) onSettled?.invoke() }
        if (command.isBlank()) {
            // 空命令按下去什么都没发生，要提示一下
            notifyFailure(CommandFailure.EMPTY)
            settle()
            return
        }
        val cmd = command.trim()
        // 别名按**前缀 + 没串别的命令**认领（口径收在 [KsuCompat] 一处）：容得下参数差异，
        //  又不会把「用户命令正文里正好出现这个串」当成 KSU 那条劫持掉。
        if (KsuCompat.isSidebarAlias(cmd)) {
            launcher.startSidebar()
            // 这一支是同步的原生调用 ⇒ 回来就算跑完
            settle()
            return
        }
        //  截图**不**在这里认领，也**不走 shell**：
        //  · 宿主自己注入 `KEYCODE_SYSRQ` —— `injectInputEvent` 返回成功、屏幕没反应
        //    （ColorOS 不认普通 app 注入的那一下，日志里只有「截图：注入…」一行）；
        //  · `su` 跑 `service call color_screenshot` —— 内置动作不该为了截张图去要 root
        //    （弹授权、慢半拍、没 root 的机器上直接不可用）。
        //  改成请**注入侧**代注入：那边在 `system_server`，注入身份就是系统本身，与 `input keyevent`
        //  同级（见 `ModuleWire.ACTION_INJECT_KEY` 与 `inject/KeyInject`）。
        if (KsuCompat.isScreenshotAlias(cmd)) {
            XLog.w("截图：请注入侧代截（优先 system_server 内部截图，没成再注入 SYSRQ；都不走 shell、不需要 root）")
            requestSystemInject(KeyId.KEYCODE_SYSRQ)
            settle()
            return
        }
        //  原样交给 shell（不传 trim 过的）：用户写的空白 / 引号是命令的一部分
        //  命令里写 `echo 'tip: …'` 的那一行，成功时弹成提示 —— 这是 shell 动作唯一能回显结果的
        //  通道（`ShellRunner.TIP_PREFIX`），失败那一侧由 [notifyFailure] 报。
        //  提示**交给注入侧弹**（[requestSystemToast]）：宿主的包受系统 toast 配额，连按会丢。
        ShellRunner.exec(
            command = command,
            onFailure = ::notifyFailure,
            onTip = { text -> requestSystemToast(context, text) },
            //  「以 root 执行」是**每条命令自己的**开关（见 `Command.root`）；内置的 `shell:` 动作没有
            //  这个开关，一直按默认走（`su`）。`su` 起不来就报失败，不退回 `sh`（见 `ShellRunner.startProcess`）。
            preferRoot = preferRoot,
            //  跑完（含起进程失败 / 被拒 / 超时）回执一次 —— 有间隔的多条命令靠它推进下一步
            onDone = settle,
        )
    }

    /**
     * 媒体音量 / 铃声音量 —— 两条命令的「注入按键」「请注入侧设档位」两条兜底每次都一样，收成两支。
     *
     *  写成单独两支**只为一件事**：`dispatch` 里那两行因此还是单行表达式 —— 只要有一个分支写成
     *  多行块，ktlint 就会给整张 `when` 的每个分支套上花括号，那个函数当场撞上 `LongMethod`（60 行）。
     */
    private fun runMediaVolume(act: Action.Volume) =
        VolumeAction.run(context, act, ::notifyFailure, injector::inject, ::requestSystemSetVolume)

    private fun runRingerVolume(act: Action.RingerVolume) =
        VolumeAction.runRinger(context, act, ::notifyFailure, injector::inject, ::requestSystemSetVolume)

    /**
     * 请注入侧（`system_server`）把某一路流设到某一档 —— 宿主自己的 `setStreamVolume` 会被
     * ColorOS 静默丢掉，注入侧的身份是系统本身，那条策略不挡它（见 `ModuleWire.ACTION_SET_VOLUME`）。
     *
     *  **只回「请求发出去了没有」**：真正落没落地由调用方（`VolumeAction`）读回判断 —— 注入侧
     *  未装 / 是老版本不认识这条广播时，不会有人处理它。
     *
     * @return 广播发出去了没有（`false` = 没有 Context / 发广播抛异常）
     */
    private fun requestSystemSetVolume(
        stream: Int,
        index: Int,
    ): Boolean {
        val ctx =
            context ?: run {
                XLog.w("没有 Context，设音量请求跳过：路 $stream → $index")
                return false
            }
        return runCatching {
            ctx.sendBroadcast(
                android.content
                    .Intent(ModuleWire.ACTION_SET_VOLUME)
                    .setPackage(HostApp.PACKAGE)
                    .putExtra(ModuleWire.EXTRA_VOLUME_STREAM, stream)
                    .putExtra(ModuleWire.EXTRA_VOLUME_INDEX, index),
            )
        }.onFailure { XLog.w("设音量请求没发出去：路 $stream → $index | ${it.javaClass.simpleName}", it) }
            .isSuccess
    }

    /**
     * 请注入侧（`system_server`）代注入一颗键 —— 内置「截图」用，宿主自己注入的那一下 ColorOS 不认。
     *
     *  收方是注入侧挂在 AMS 广播入口上的那个 hook（`inject/KeyHooks.readBroadcast`），
     *  它看得见全系统每一条广播，所以 `setPackage` **不影响它收**；
     *  但裸发就是一条谁都能注册接收的隐式广播 —— 定向到本包，别的应用收不到。
     *  发不出去只记一行：这条路没有「命令失败」可报（不在 shell 里），用户感知就是按了没截图。
     */
    private fun requestSystemInject(keyCode: Int) {
        val ctx =
            context ?: run {
                XLog.w("没有 Context，注入请求跳过：keyevent:$keyCode")
                return
            }
        val sent =
            runCatching {
                ctx.sendBroadcast(
                    android.content
                        .Intent(ModuleWire.ACTION_INJECT_KEY)
                        .setPackage(HostApp.PACKAGE)
                        .putExtra(ModuleWire.EXTRA_INJECT_CODE, keyCode),
                )
            }.onFailure { XLog.w("注入请求没发出去：keyevent:$keyCode | ${it.javaClass.simpleName}", it) }
                .isSuccess
        //  只说明「请求发出去了」：注入侧未装 / 未认领时不会真的截图，宿主侧看不到结果。
        if (sent) XLog.w("截图：注入请求已发出（是否真的截图由注入侧决定）")
    }

    /** AOSP 标准免打扰切换（使用 NotificationManager 标准接口）。 */
    private fun runAospDnd() {
        val ctx =
            context ?: run {
                XLog.w("没有 Context，AOSP 免打扰跳过")
                return
            }
        val nm =
            try {
                ctx.getSystemService(NotificationManager::class.java)
            } catch (e: Throwable) {
                XLog.w("拿不到 NotificationManager，AOSP 免打扰跳过", e)
                null
            } ?: run {
                XLog.w("拿不到 NotificationManager，AOSP 免打扰跳过")
                return
            }
        try {
            val currentFilter = nm.currentInterruptionFilter
            val targetFilter =
                if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                    currentFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                ) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
            nm.setInterruptionFilter(targetFilter)
            XLog.w("免打扰（AOSP 接口）：filter $currentFilter → $targetFilter")
        } catch (e: Throwable) {
            // 「勿扰访问」是 appop、不是装了就有（真机实测：`ACCESS_NOTIFICATION_POLICY`
            //  的 granted 是 true，而 `enabled_notification_policy_access_packages` 是空的）。
            //  没授时**只在失败分支里**点破 —— 不预先拦：判据本身也可能在别的 ROM 上失准，
            //  而这条路能成的时候不该被一句检查挡住。
            if (!nm.isNotificationPolicyAccessGranted) {
                XLog.w(
                    "免打扰（AOSP 接口）失败：本模块没有「勿扰访问」授权" +
                        "（设置 → 通知与状态栏 → 勿扰，允许「手势体感」），这条命令在这台机器上不会生效",
                )
                notifyFailure(CommandFailure.DND_NOT_GRANTED)
                return
            }
            XLog.w("切换 AOSP 免打扰失败：${e.javaClass.simpleName}: ${e.message}")
            notifyFailure(CommandFailure.UNKNOWN)
        }
    }
}

/** 「灵感语音」的连按窗口（毫秒）：这段时间内的第二次触发当没按。 */
private const val VOICE_MEMORY_GAP_MS = 3_000L

/**
 * 电源键那一档 —— **不能走注入**。
 *
 *  真机实测（PLK110 / ColorOS 17）：同一套 `injectInputEvent`，音量键生效、**电源键被系统直接忽略**
 *  （`adb shell input keyevent 26` 也一样 —— `dumpsys power` 里 `mWakefulness` 一点没变）。
 *  所以「双击绑定成电源键」在注入那条路上永远没反应；判定与执行其实都跑到了。
 *
 *  改成走 `PowerManager`：亮着就睡（等价按一下），睡着就醒（等价再按一下）。
 *  两个方法都是 `@hide`，只能反射调（`DEVICE_POWER` 权限靠宿主是系统应用）。
 */
internal fun togglePower(
    context: Context?,
    onFailure: (CommandFailure) -> Unit,
) {
    val ctx =
        context ?: run {
            XLog.w("没有 Context，电源键跳过")
            return
        }
    try {
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        if (pm == null) {
            XLog.w("拿不到 PowerManager，电源键跳过")
            return
        }
        val now = android.os.SystemClock.uptimeMillis()
        val cls = android.os.PowerManager::class.java
        if (pm.isInteractive) {
            cls.getMethod("goToSleep", Long::class.javaPrimitiveType).invoke(pm, now)
            XLog.w("电源键：已请求灭屏（goToSleep）")
        } else {
            requestWake(pm, POWER_WAKE_REASON)
            XLog.w("电源键：已请求亮屏（wakeUp）")
        }
    } catch (e: Throwable) {
        // 与其它动作同一条纪律：执行环境出错只记日志，不许抛出去
        XLog.w("电源键执行失败（已忽略）", e)
        onFailure(CommandFailure.UNKNOWN)
    }
}

/** [togglePower] 亮屏那句 reason —— 系统日志里认得出是我们点起来的。 */
private const val POWER_WAKE_REASON = "opluskey:power_key"

/**
 * 发一次 `PowerManager.wakeUp`（`@hide`，只能反射；`DEVICE_POWER` 靠宿主的系统应用身份持有）。
 *
 *  **只发请求，不判断屏幕状态** —— 「该不该点」由调用方判（电源键是「睡着就点」，
 *  起界面那一档是「登记了 `wakeScreen` 且此刻灭着」），日志措辞与失败处理也各归各的。
 *  两处共用这一格只为不让 `wakeUp` 的签名散成两份 —— 它是 `@hide`，改版时漏改一处就是
 *  「亮屏永远失败」而另一处还正常，最难查的那种半坏。
 *
 *  失败按异常抛给调用方（两处都自己 `catch (Throwable)`）。
 */
internal fun requestWake(
    pm: android.os.PowerManager,
    reason: String,
) {
    android.os.PowerManager::class.java
        .getMethod("wakeUp", Long::class.javaPrimitiveType, String::class.java)
        .invoke(pm, android.os.SystemClock.uptimeMillis(), reason)
}
