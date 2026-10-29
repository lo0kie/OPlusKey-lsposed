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
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.KsuCompat
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.model.expandedWith
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
    /**
     * 「使用 ColorOS 接口」这一档 —— **全项目只有这一处读它**，再往下（手电筒 / 响铃）都是
     * 构造参数传进来的。
     *
     *  为什么收在这里：它同时被三个执行器用（手电筒、响铃、免打扰），而这三个各自都
     *  `ConfigStore.read()` 一遍的话，「默认值」这件事就有三份副本 —— 改其中一处不知另两处。
     *  宿主进程每次按键都重读配置是这个仓的既定口径（见 `GestureReceiver` 那段说明），
     *  所以这一层直接读盘、不缓存。
     */
    private val isColorOsApi: () -> Boolean = { ConfigStore.read().global.colorOsApi },
) {
    private val injector = KeyInjector { context }
    private val flashlight = FlashlightController({ context }, handler, isColorOsApi)
    private val ringer =
        RingerController(
            { context },
            isColorOsApi,
            // 「振动反馈」与 `isColorOsApi` 同源：都从这一层的读盘口径来，执行器自己不读配置
            { ConfigStore.read().global.vibrate },
        )
    private val launcher = AppLauncher({ context }, ::notifyFailure)
    private val shortcut = AppShortcutLaunch({ context }, launcher.mini, ::notifyFailure)
    private val custom =
        CustomCommandRunner(
            handler,
            ::runShell,
            ::dispatch,
            ::notifyFailure,
        )

    /**
     * 释放执行器持有的**进程级注册**（现在只有手电筒那条 torch 回调）。
     *
     *  宿主主进程里不需要（进程活多久就活多久）；`:ui` 进程里那份「测试命令」执行器在关页时调它，
     *  否则每轮「进设置 → 测试手电筒 → 关页」都会在系统里留一份不会再被用的回调。
     *
     *  名字必须与 [stopVoiceCapture] 区分开：两者都是「松手 / 收尾」，但一个是**进程级注册**、
     *  一个是**这一按的采集**。同名时 Kotlin 允许并存（函数与属性不在同一命名空间），
     *  调用点 `runner.release()` 永远只会命中函数 —— 灵感语音那条收尾因此从不执行。
     */
    fun releaseTorch() {
        flashlight.release()
    }

    /**
     * 「灵感语音」上一次触发的时间（`uptimeMillis`）—— 连按不重复起采集。
     *
     *  它起的是一个**前台麦克风采集服务**：重复拨只会把正在录的那一段打断重来，用户看到的是
     *  「按了两下，第一段没了」。所以这里直接挡：`VOICE_MEMORY_GAP_MS` 内的第二次当没按。
     */
    private var lastVoiceMemoryAt = 0L

    /**
     * 「灵感语音」这一段是不是**我们起的**（= 还没收尾）。
     *
     *  松手要不要发结束，只能按它判，不能按「长按绑的是不是灵感语音」判：
     *  `VOICE_MEMORY_GAP_MS` 内的第二次按下**不会**起采集（见 [startOplusOrVoice]），
     *  那一下松手若照样发结束，掐断的是**上一段**正在录的。状态只归执行侧所有。
     */
    private var voiceMemoryOn = false

    /**
     * 起一条 ColorOS 落点 —— 灵感语音单独有「连按窗口」，其余照旧。
     *
     *  写成 lambda 字段而不是方法：这个类的函数数已经贴着 detekt 的上限。
     */
    private val startOplusOrVoice: (String) -> Unit = { feature ->
        val now = android.os.SystemClock.uptimeMillis()
        val gap = now - lastVoiceMemoryAt
        if (feature == RomProfile.NAME_VOICE_MEMORY && gap in 0 until VOICE_MEMORY_GAP_MS) {
            XLog.w("灵感语音：${gap}ms 内又按了一次，忽略（上一段还在采集）")
        } else {
            if (feature == RomProfile.NAME_VOICE_MEMORY) {
                lastVoiceMemoryAt = now
                voiceMemoryOn = true
            }
            launcher.startOplusFeature(feature)
        }
    }

    /**
     * 松手 —— 给「按下开始、松手结束」的动作收尾（此刻只有灵感语音）。
     *
     *  判据是 [voiceMemoryOn] 而不是配置：这一下松手有没有东西要停，只有执行侧知道
     *  （见那个字段的说明）。绑定变了、主开关关了都由调用方先判过 —— 到这里只管停。
     *
     *  与 [startOplusOrVoice] 同理写成 lambda 字段：这个类的函数数贴着 detekt 的上限。
     */
    val stopVoiceCapture: () -> Unit = {
        if (!voiceMemoryOn) {
            XLog.d("松手：这一段灵感语音不是本进程起的，不收尾")
        } else {
            voiceMemoryOn = false
            XLog.w("灵感语音：松手 → 结束采集")
            launcher.stopOplusFeature(RomProfile.NAME_VOICE_MEMORY)
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
    private fun notifyFailure(failure: CommandFailure) = FailureNotice.show(context, failure)

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
     */
    fun run(
        action: Action,
        gestureLabel: String,
    ) {
        XLog.w("$gestureLabel → ${XLog.redactToken(action.token)}")
        dispatch(action)
    }

    /**
     * 设置页「测试命令」按钮走这里，日志不套「侧键 -」前缀（那一条是「侧键真被按了」的判据）。
     * 只在设置页进程（`:ui`）里跑，不走广播给宿主主进程那条路（那个进程开机后可能还没起来）。
     */
    fun runTest(name: String) {
        XLog.w("测试命令 → ${Command.PREFIX}$name")
        dispatch(Action.Custom(name))
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
                is Action.None -> Unit
                is Action.KeyEvent -> runKeyEvent(act.keyCode)
                is Action.Shell -> runShell(act.command)
                is Action.Custom -> custom.run(act.name, depth)
                is Action.Intent -> launcher.openIntent(act.action)
                is Action.App -> launcher.launchApp(act.target)
                is Action.AppWindow -> launcher.launchAppInWindow(act.target)
                is Action.AppShortcut -> runAppShortcut(act)
                is Action.MiniWindow -> launcher.putForegroundInMiniWindow(act.collapsed)
                is Action.Shortcut -> launcher.runShortcut(act.shortcutId)
                is Action.Flashlight -> flashlight.toggle()
                is Action.Ringer -> ringer.setMode(act.mode)
                is Action.PowerMenu -> sendStatusbar(StatusBarBridge.CMD_POWER_MENU)
                is Action.Oplus -> runOplus(act.feature)
                is Action.Panel -> runPanel(act.mode)
                is Action.Setting -> SettingSwitch.toggleOrFlashlight(context, act, flashlight, ::notifyFailure)
                is Action.VarSet -> runVarSet(act)
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
            KeyId.KEYCODE_NOTIFICATION -> sendStatusbar(StatusBarBridge.CMD_NOTIFICATIONS)
            KeyId.KEYCODE_APP_SWITCH -> sendStatusbar(StatusBarBridge.CMD_RECENTS)
            KeyId.KEYCODE_VOLUME_MUTE -> ringer.cycle()
            KeyId.KEYCODE_POWER -> togglePower(context, ::notifyFailure)
            KeyId.KEYCODE_VOLUME_UP -> adjustVolume(android.media.AudioManager.ADJUST_RAISE)
            KeyId.KEYCODE_VOLUME_DOWN -> adjustVolume(android.media.AudioManager.ADJUST_LOWER)
            else -> injector.inject(keyCode)
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
     */
    private fun runShell(command: String) {
        if (command.isBlank()) {
            // 空命令按下去什么都没发生，要提示一下
            notifyFailure(CommandFailure.EMPTY)
            return
        }
        val cmd = command.trim()
        // 别名按**前缀 + 没串别的命令**认领（口径收在 [KsuCompat] 一处）：容得下参数差异，
        //  又不会把「用户命令正文里正好出现这个串」当成 KSU 那条劫持掉。
        if (KsuCompat.isSidebarAlias(cmd)) {
            launcher.startSidebar()
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
            return
        }
        //  原样交给 shell（不传 trim 过的）：用户写的空白 / 引号是命令的一部分
        ShellRunner.exec(command, ::notifyFailure)
    }

    /**
     * 请注入侧（`system_server`）代注入一颗键 —— 内置「截图」用，宿主自己注入的那一下 ColorOS 不认。
     *
     *  收方是注入侧挂在 AMS 广播入口上的那个 hook（`inject/KeyHooks.readBroadcast`），
     *  它看得见全系统每一条广播，所以这里**不必 `setPackage`**。
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
                        .putExtra(ModuleWire.EXTRA_INJECT_CODE, keyCode),
                )
            }.onFailure { XLog.w("注入请求没发出去：keyevent:$keyCode | ${it.javaClass.simpleName}", it) }
                .isSuccess
        if (sent) XLog.w("截图：注入请求已发给注入侧")
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
            cls
                .getMethod("wakeUp", Long::class.javaPrimitiveType, String::class.java)
                .invoke(pm, now, POWER_WAKE_REASON)
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
