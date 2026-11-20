package com.lookie.opluskey.bridge.host

import android.content.Context
import android.graphics.Rect
import com.lookie.opluskey.bridge.FlexibleWindowBridge
import com.lookie.opluskey.bridge.ProcessBridge
import com.lookie.opluskey.bridge.SettingBridge
import com.lookie.opluskey.bridge.StatusBarBridge
import com.lookie.opluskey.command.CommandPlatform
import com.lookie.opluskey.command.SystemCommands
import com.lookie.opluskey.command.WindowAnimParams
import com.lookie.opluskey.core.log.XLog

/**
 * 本模块给 [CommandPlatform] 的答案 —— 命令能力层那一侧只说要做什么，怎么发全在这里。
 *
 * ## 为什么这份实现必须留在 `:app`
 *
 *  这七条通道的另一半都在**注入侧**：状态栏指令与小窗广播由 `system_server` 里的 hook 接住，
 *  按键注入要借系统的输入服务，而它们的 action 名、附加字段、挂点候选表都是本模块与注入侧
 *  共用的私有协议（`StatusBarBridge` / `FlexibleWindowBridge` / `bridge/Framework`）。
 *  那些东西不能下移到 `:command` —— 注入侧不许依赖执行层（`inject/` 只认 `core/` 与 `bridge/`）。
 *  于是分界正好落在这里：**能搬的搬走，只有本模块才知道怎么发的留在这儿**。
 *
 * ## 这条实现只做「转发」
 *
 *  每一条都只是把语义名翻成本模块那条广播 / 那一次反射调用，不做判断、不报失败提示 ——
 *  判据与提示属于命令能力层（它才知道这一次是哪个动作、该报哪一档失败）。唯一留着的是
 *  「没有 Context」这一类**通道自身缺前提**的日志。
 *
 * @param context 借来的宿主 Context（可能为空：宿主还没起来时执行侧的调用仍然要能走完）
 */
internal class CommandPlatformImpl(
    private val context: () -> Context?,
) : CommandPlatform {
    override fun sendSystemCommand(command: String) {
        val ctx = context() ?: return log("statusbar:$command")
        StatusBarBridge.send(ctx, cmdOf(command))
    }

    /**
     * 语义名 → 本模块那条广播的指令名。
     *
     *  两侧的**字面值**现在恰好一致，但仍然是两份常量：这一处显式翻一次，改任何一边的拼写都会
     *  当场在这里露出来，而不是变成「发了、没人认、也不报错」。
     */
    private fun cmdOf(command: String): String =
        when (command) {
            SystemCommands.NOTIFICATIONS -> StatusBarBridge.CMD_NOTIFICATIONS
            SystemCommands.CONTROL_CENTER -> StatusBarBridge.CMD_CONTROL_CENTER
            SystemCommands.RECENTS -> StatusBarBridge.CMD_RECENTS
            SystemCommands.POWER_MENU -> StatusBarBridge.CMD_POWER_MENU
            SystemCommands.VOICE_ASSIST -> StatusBarBridge.CMD_VOICE_ASSIST
            SystemCommands.STOP_PACKAGE -> StatusBarBridge.CMD_STOP_PACKAGE
            else -> command
        }

    override fun sendTimer(seconds: Int): Boolean {
        val ctx = context() ?: return false.also { log("timer:$seconds") }
        return StatusBarBridge.sendTimer(ctx, seconds)
    }

    override fun sendBrightness(target: Int) {
        val ctx = context() ?: return log("brightness:$target")
        StatusBarBridge.sendBrightness(ctx, target)
    }

    override fun sendSetting(
        id: String,
        on: Boolean?,
    ) {
        val ctx = context() ?: return log("setting:$id")
        SettingBridge.send(ctx, id, on)
    }

    override fun isStopForbidden(pkg: String): Boolean = ProcessBridge.isForbidden(pkg)

    override fun stopPackage(pkg: String): Boolean {
        val ctx = context() ?: return false.also { log("stop:$pkg") }
        return ProcessBridge.send(ctx, pkg)
    }

    override fun injectKey(keyCode: Int): Boolean {
        //  注入器不持有 Context：它自己按 `InputManager` 那条路取服务（见 `KeyInjector` 的类注释）
        return injector.inject(keyCode)
    }

    override fun collapseToMini(
        taskId: Int,
        bounds: Rect?,
        scale: Float?,
    ) {
        val ctx = context() ?: return log("mini:#$taskId")
        FlexibleWindowBridge.send(ctx, FlexibleWindowBridge.CMD_MINI, taskId, bounds, scale)
    }

    override fun animateToMini(
        taskId: Int,
        params: WindowAnimParams,
    ) {
        val ctx = context() ?: return log("mini_anim:#$taskId")
        FlexibleWindowBridge.sendMiniAnim(
            ctx,
            taskId,
            FlexibleWindowBridge.MiniAnimParams(
                startRect = params.startRect,
                finishRect = params.finishRect,
                startScale = params.startScale,
                finishScale = params.finishScale,
                startAlpha = params.startAlpha,
                finishAlpha = params.finishAlpha,
                durationMs = params.durationMs,
            ),
        )
    }

    private val injector = KeyInjector { context() }

    /** 通道缺前提（没有 Context）—— 只记一行，判据与用户可见的失败由命令能力层那一侧报。 */
    private fun log(channel: String) {
        XLog.w("没有 Context，$channel 跳过")
    }
}
