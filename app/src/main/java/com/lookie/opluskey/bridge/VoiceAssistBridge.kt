package com.lookie.opluskey.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog

/**
 * 「唤醒小布」（`oplus:voice_assist`）的执行侧 —— **只在 system_server 里跑**。
 *
 *  独立成一个对象而不是并进 [StatusBarBridge]：那一族「一种指令一个函数」，再加一支就顶破
 *  detekt 的函数数上限；而这一支本来就比别的指令多一段（挑包名、组 intent），放在一起
 *  会把那张分派表挤得看不清。通道还是同一条（`CMD_VOICE_ASSIST`），入口在那边。
 *
 *  ## 为什么必须由 system_server 起
 *
 *  ROM 里这条配方（`PhoneWindowManagerExtImpl.startSpeech`）是拿**框架自己的身份**去
 *  `startForegroundServiceAsUser`，而 `checkComponentPermission` 对 `SYSTEM_UID` 直接放行 ——
 *  也就是说「起得来」靠的是调用方是谁，不是目标服务对第三方开放。宿主 `com.oplus.gesture`
 *  没有这份旁路，所以这一档不放给宿主（与通知中心 / 电源菜单同一条纪律）。
 *  配方与取证见 `docs/device-findings.md` 第三十节。
 */
internal object VoiceAssistBridge {
    /**
     * 起一次小布助手（Breeno）。
     *
     *  @param loader system_server 的 ClassLoader —— 取 Context 要用它；null 时只记日志
     *    （注入侧还没初始化完，这一下不执行）
     */
    fun start(loader: ClassLoader?) {
        if (loader == null) {
            XLog.w("还没有 system_server 的 ClassLoader，唤醒小布跳过")
            return
        }
        val ctx = contextOf(loader) ?: return
        val pkg =
            installedSpeechPackage(ctx) ?: run {
                XLog.w("本机没装小布助手（找过 ${Framework.Calls.SPEECH_PACKAGES}），唤醒小布跳过")
                return
            }
        // FLAG_ACTIVITY_NEW_TASK 对服务没有作用 —— ROM 那边也加了（同一份配方），照抄便于对账
        val intent =
            Intent(Framework.Calls.SPEECH_ACTION_ACTIVATE)
                .setComponent(ComponentName(pkg, Framework.Calls.SPEECH_SERVICE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Framework.Calls.SPEECH_EXTRA_CALLER, Framework.Calls.SPEECH_CALLER_VALUE)
                .putExtra(Framework.Calls.SPEECH_EXTRA_START_TYPE, Framework.Calls.SPEECH_START_TYPE_POWER)
        try {
            asSystem { ctx.startForegroundService(intent) }
            XLog.w(
                "已在本机 startForegroundService($pkg/${Framework.Calls.SPEECH_SERVICE}，" +
                    "start_type=${Framework.Calls.SPEECH_START_TYPE_POWER})（唤醒小布）",
            )
        } catch (e: Throwable) {
            // 起不来的两种常见来路：目标服务没导出 / 按调用方判签名 —— 只记日志，不抛
            XLog.w("唤醒小布失败：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 取一个能起服务的 Context；取不到记一行就返回 null（这一下不执行）。 */
    private fun contextOf(loader: ClassLoader): Context? {
        val ctx =
            try {
                SystemContext.get(loader)
            } catch (e: Throwable) {
                XLog.w("取 Context 失败，唤醒小布跳过：${e.javaClass.simpleName}")
                null
            }
        if (ctx == null) XLog.w("没有 Context，唤醒小布跳过")
        return ctx
    }

    /**
     * 装着的那个语音助手包名 —— 新包优先，老包兜底（[Framework.Calls.SPEECH_PACKAGES]）。
     *
     *  为什么要在运行期挑：ROM 自己也是按 `mBreenoPkg` 走的（新机型 `heytap`、早期 `oppo`），
     *  写死一个的话换一代机器这一格就安静地失效。
     */
    private fun installedSpeechPackage(ctx: Context): String? =
        Framework.Calls.SPEECH_PACKAGES.firstOrNull { pkg ->
            runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
        }

    /**
     * 以 **system_server 自己的身份**（uid 1000）执行 [block]。
     *
     *  与 [StatusBarBridge] 里那个同名约定**是同一件事**：这段跑在 AMS 的广播入口里，调用方
     *  uid 还是发广播的宿主；换个身份再起，目标服务按调用方判权限才不会拒。
     *  换完必须还原（`finally`），否则会把 system 身份漏给同线程后面的调用。
     */
    private inline fun <T> asSystem(block: () -> T): T {
        val token = android.os.Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            android.os.Binder.restoreCallingIdentity(token)
        }
    }
}
