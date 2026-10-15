package com.lookie.opluskey.host.action

import android.content.Context
import android.os.Handler
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.inject.input.KeyInjector
import com.lookie.opluskey.inject.system.FlashlightController
import com.lookie.opluskey.inject.system.RingerController
import com.lookie.opluskey.inject.system.StatusBarBridge

/** 动作派发器，只服务侧键。 */
internal class ActionRunner(
    /** 借来的宿主 Context（`host/GestureReceiver` 从 `ActivityThread` 反射取）。 */
    @Volatile var context: Context?,
    handler: Handler,
) {

    private val injector = KeyInjector { context }
    private val flashlight = FlashlightController({ context }, handler)
    private val ringer = RingerController { context }
    private val launcher = AppLauncher({ context }, ::notifyFailure)
    private val custom = CustomCommandRunner(handler, ::runShell, ::dispatch, ::notifyFailure)

    /** 失败提示的唯一出口，转成 Toast；只有这里碰 Context 与 UI。 */
    private fun notifyFailure(failure: CommandFailure) = FailureNotice.show(context, failure)

    /** 执行一个手势对应的动作。 */
    fun run(action: Action, gestureLabel: String) {
        XLog.w("侧键·$gestureLabel → ${action.token}")
        dispatch(action)
    }

    /**
     * 设置页「测试命令」按钮走这里，日志不套「侧键·」前缀（那一条是「侧键真被按了」的判据）。
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
    private fun dispatch(action: Action, depth: Int = 0) {
        try {
            when (action) {
                is Action.None -> Unit // 拦截，什么都不做
                is Action.KeyEvent -> runKeyEvent(action.keyCode)
                is Action.Shell -> runShell(action.command)
                is Action.Custom -> custom.run(action.name, depth)
                is Action.Intent -> launcher.openIntent(action.action)
                is Action.App -> launcher.launchApp(action.target)
                is Action.AppWindow -> launcher.launchAppInWindow(action.target)
                // 只动当前那个窗口（不新建实例）—— 与 AppWindow 是两条路；
                // 尺寸档（展开 / 收起）由动作自己带，执行侧据此走不同配方
                is Action.MiniWindow -> launcher.putForegroundInMiniWindow(action.collapsed)
                is Action.Shortcut -> launcher.runShortcut(action.shortcutId)
                is Action.Flashlight -> flashlight.toggle()
                // `ringer:normal|vibrate|silent` 切到指定一档；循环那条仍是 `keyevent:164`。
                is Action.Ringer -> ringer.setMode(action.mode)
                // 电源菜单同理：本机反射调用，不走按键长按
                is Action.PowerMenu -> sendStatusbar(StatusBarBridge.CMD_POWER_MENU)
                is Action.Oplus -> launcher.startOplusFeature(action.feature)
                // 控制中心与「通知中心」（`keyevent:83`）同一条通道：发广播让 system_server 去调状态栏服务。
                //  只有一档；认不出的档位只记日志（不猜一个调用目标去凑，见 AGENTS.md 五、4）。
                is Action.Panel -> when (action.mode) {
                    Action.Panel.CONTROL_CENTER ->
                        sendStatusbar(StatusBarBridge.CMD_CONTROL_CENTER)
                    else -> XLog.w("不认识的系统面板档位（已忽略）：${action.mode}")
                }
            }
        } catch (e: Throwable) {
            // 必须 catch Throwable：执行环境（Runtime / 反射注入）出错可能是 Error 系，漏出去会崩系统
            XLog.w("执行动作失败（已忽略）：${action.token}", e)
            notifyFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 一个 `keyevent:` 动作：大多走注入，通知中心 / 最近任务 / 静音键走特殊通道。 */
    private fun runKeyEvent(keyCode: Int) {
        when (keyCode) {
            KeyId.KEYCODE_NOTIFICATION -> sendStatusbar(StatusBarBridge.CMD_NOTIFICATIONS)
            KeyId.KEYCODE_APP_SWITCH -> sendStatusbar(StatusBarBridge.CMD_RECENTS)
            KeyId.KEYCODE_VOLUME_MUTE -> ringer.cycle()
            else -> injector.inject(keyCode)
        }
    }

    /**
     * 通知中心 / 控制中心 / 最近任务：发一条标记广播让 system_server 执行 —— 这台 ROM 上
     * `StatusBarManager` 取不到、`expandNotificationsPanel` 被 `EXPAND_STATUS_BAR` 拒、
     * 注入那两个码也打不通，只能走广播。
     */
    private fun sendStatusbar(cmd: String) {
        val ctx = context ?: run {
            XLog.w("没有 Context，statusbar:$cmd 跳过")
            return
        }
        StatusBarBridge.send(ctx, cmd)
    }

    /**
     * 执行一条 `shell:` 命令：截图 / 小布识屏两个 KSU 别名保持配置写法一致，
     * 执行换成原生调用；其余交给 `ShellRunner`（宿主进程里 `sh -c` 可用）。
     */
    private fun runShell(command: String) {
        if (command.isBlank()) {
            // 空命令按下去什么都没发生，要提示一下
            notifyFailure(CommandFailure.EMPTY)
            return
        }
        val cmd = command.trim()
        when {
            // 两个 KSU 别名都按**前缀 + 没串别的命令**认领（口径收在 [KsuCompat] 一处）：
            // 容得下参数差异，又不会把「用户命令正文里正好出现这个串」当成 KSU 那条劫持掉
            //。
            KsuCompat.isScreenshotAlias(cmd) -> {
                XLog.w("截图：注入 KEYCODE_SYSRQ（KSU 那条 service call 在这里不必走 shell）")
                injector.inject(KeyId.KEYCODE_SYSRQ)
            }

            KsuCompat.isSidebarAlias(cmd) -> launcher.startSidebar()
            // 原样交给 shell（不传 trim 过的）：用户写的空白 / 引号是命令的一部分
            else -> ShellRunner.exec(command, ::notifyFailure)
        }
    }
}
