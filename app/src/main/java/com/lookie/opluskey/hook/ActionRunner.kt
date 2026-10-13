package com.lookie.opluskey.hook

import android.content.Context
import android.os.Handler
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.Command

/** 动作派发器，只服务侧键。 */
internal class ActionRunner(
    /** 借来的宿主 Context（`gesture/GestureReceiver` 从 `ActivityThread` 反射取）。 */
    @Volatile var context: Context?,
    handler: Handler,
) {

    private val injector = KeyInjector { context }
    private val flashlight = FlashlightController({ context }, handler)
    private val ringer = RingerController { context }
    private val launcher = AppLauncher({ context }, ::notifyFailure)
    private val custom = CustomCommandRunner(handler, ::runShell, ::dispatch, ::notifyFailure, ::notifyToast)

    /** 失败提示的唯一出口，转成 Toast；只有这里碰 Context 与 UI。 */
    private fun notifyFailure(failure: CommandFailure) = FailureNotice.show(context, failure)

    /** 「执行时提示」出口（全局开关 `GlobalConfig.toastOnRun`），内容是命令名。 */
    private fun notifyToast(text: String) = FailureNotice.toast(context, text)

    /** 执行一个手势对应的动作。 */
    fun run(action: Action, gestureLabel: String) {
        XLog.i("侧键·$gestureLabel → ${action.token}")
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

    /** 放行（passthrough / native）：把原始按键原样重注入一次。 */
    fun injectKeyForForward(keyCode: Int) = injector.injectForForward(keyCode)

    /** 真正干活的那一层；自定义命令的步骤也走同一套派发（无手势标签）。 */
    private fun dispatch(action: Action) {
        try {
            when (action) {
                is Action.None -> Unit // 拦截，什么都不做
                is Action.Native, is Action.Passthrough -> Unit // 由 GestureDetector 处理放行
                is Action.KeyEvent -> runKeyEvent(action.keyCode)
                is Action.Shell -> runShell(action.command)
                is Action.Custom -> custom.run(action.name, 0)
                is Action.Intent -> launcher.openIntent(action.action)
                is Action.App -> launcher.launchApp(action.target)
                is Action.Shortcut -> launcher.runShortcut(action.shortcutId)
                is Action.Flashlight -> flashlight.toggle()
                // `ringer:normal|vibrate|silent` 切到指定一档；循环那条仍是 `keyevent:164`。
                is Action.Ringer -> ringer.setMode(action.mode)
            }
        } catch (e: Throwable) {
            // 必须 catch Throwable：执行环境（Runtime / 反射注入）出错可能是 Error 系，漏出去会崩系统
            XLog.w("执行动作失败（已忽略）：${action.token}", e)
            notifyFailure(CommandFailure.UNKNOWN)
        }
    }

    /** 一个 `keyevent:` 动作：大多走注入，通知栏 / 最近任务 / 静音键走特殊通道。 */
    private fun runKeyEvent(keyCode: Int) {
        when (keyCode) {
            KEYCODE_NOTIFICATION -> sendStatusbar(StatusBarBridge.CMD_NOTIFICATIONS)
            KEYCODE_APP_SWITCH -> sendStatusbar(StatusBarBridge.CMD_RECENTS)
            KEYCODE_VOLUME_MUTE -> ringer.cycle()
            else -> injector.inject(keyCode)
        }
    }

    /**
     * 通知栏 / 最近任务：发一条标记广播让 system_server 执行 —— 这台 ROM 上
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
        when {
            command == KsuCompat.SCREENSHOT_CMD -> {
                XLog.i("截图：注入 KEYCODE_SYSRQ（KSU 那条 service call 在这里不必走 shell）")
                injector.inject(KeyInjector.KEYCODE_SYSRQ)
            }
            command.contains(KsuCompat.SIDEBAR_SERVICE) -> launcher.startSidebar()
            else -> ShellRunner.exec(command, ::notifyFailure)
        }
    }

    private companion object {
        /** 与 `KeyEvent.KEYCODE_NOTIFICATION` 一致（83）。 */
        const val KEYCODE_NOTIFICATION = 83

        /** 最近任务，走 `sendStatusbar`（`KEYCODE_APP_SWITCH` = 187）。 */
        const val KEYCODE_APP_SWITCH = 187

        const val KEYCODE_VOLUME_MUTE = 164
    }
}
