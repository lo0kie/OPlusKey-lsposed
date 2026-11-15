package com.lookie.opluskey.shizuku

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.lookie.opluskey.core.log.XLog
import java.util.concurrent.Executors

/**
 * `shell:` 的 **跨进程执行通道（模块进程侧）**。
 *
 * ## 它解决什么
 *
 *  Shizuku 的授权按 **uid** 记：模块与宿主 `com.oplus.gesture` 是两个 uid。真 Shizuku 那一路
 *  的 binder 只会被推到**声明了 `ShizukuProvider` 的那个进程**（也就是本模块自己的进程），
 *  所以命令得转到这里执行，再把退出码回给宿主。
 *
 * ## 它什么时候用得上
 *
 *  只在「宿主进程拿不到 binder」时才走到这里 —— Sui（zygisk）那一档宿主进程自己就能
 *  `Sui.init`，直接在本进程跑（见 `ShizukuHost`）。两档共用 [ShizukuExec] 那同一份实现。
 *
 *  注意这条路有个前提：**本模块的进程得能被拉起来**。部分 ROM（ColorOS 一类）会拦
 *  「后台应用被别的应用 bind 起进程」（dumpsys 里三个 `mAllowStart_*` 全 DENIED），那时
 *  bind 直接返回 false —— 这就是 Sui 那一档存在的理由，别把本服务当成唯一通路。
 *
 * ## 协议
 *
 *  · 执行请求：[MSG_EXEC]，`data` 里 `KEY_COMMAND` = 要跑的命令串；回复 [MSG_RESULT]，
 *    `data` 里 `KEY_CODE` = 退出码，`KEY_NO_SHIZUKU` = true 表示「这个进程没有可用的 Shizuku」。
 *  · 授权请求：[MSG_REQUEST_GRANT]；回复 [MSG_GRANT_RESULT]，`KEY_GRANTED` / `KEY_NO_SHIZUKU`。
 *
 *  两条回复都回给请求里的 `replyTo` —— 服务不持有任何调用方对象。
 *
 * ## 谁能调
 *
 *  只认**自己**与**宿主** 两个 uid（见 [allowedCallers]）：`exported=true` 是为了让宿主
 *  bind 得上，但一条 `sh -c` 的入口不能对全机开放 —— 任何应用能调它，就等于任何应用都能借
 *  Shizuku 的 adb 权限跑命令。
 */
class ShizukuShellService : Service() {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "shizuku-shell") }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private val messenger =
        Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_EXEC -> onExec(msg)
                        MSG_REQUEST_GRANT -> onRequestGrant(msg)
                        else -> super.handleMessage(msg)
                    }
                }
            },
        )

    private fun onExec(msg: Message) {
        val replyTo = msg.replyTo
        if (replyTo == null) {
            XLog.w("Shizuku 通道：请求没带回复信使，丢弃")
            return
        }
        if (!allowedCallers().contains(incomingUid())) {
            XLog.w("Shizuku 通道：拒绝来自 uid=${incomingUid()} 的请求（只认自己与宿主）")
            return
        }
        val command = msg.data?.getString(KEY_COMMAND).orEmpty()
        if (command.isBlank()) {
            reply(replyTo, code = NO_SHIZUKU, noShizuku = false)
            return
        }
        worker.execute {
            if (!ShizukuExec.available() || !ShizukuExec.granted()) {
                XLog.w("Shizuku 通道：本进程不可用（ping=${ShizukuExec.available()} granted=${ShizukuExec.granted()}）")
                reply(replyTo, code = NO_SHIZUKU, noShizuku = true)
                return@execute
            }
            reply(replyTo, code = ShizukuExec.exec(command), noShizuku = false)
        }
    }

    private fun onRequestGrant(msg: Message) {
        val replyTo = msg.replyTo
        if (replyTo == null) {
            XLog.w("Shizuku 通道：授权请求没带回复信使，丢弃")
            return
        }
        if (!allowedCallers().contains(incomingUid())) {
            XLog.w("Shizuku 通道：拒绝来自 uid=${incomingUid()} 的授权请求（只认自己与宿主）")
            return
        }
        if (!ShizukuExec.available()) {
            XLog.w("Shizuku 通道：授权请求时没有 binder")
            replyGrant(replyTo, granted = false, noShizuku = true)
            return
        }
        //  申请本身是阻塞的（binder 调用 + 等用户点那一页），而且结果会投递回本进程主线程
        //  —— 放在 worker 上，别占着主线程等。
        worker.execute {
            ShizukuExec.requestGrantBlocking { granted ->
                replyGrant(replyTo, granted = granted, noShizuku = false)
            }
        }
    }

    private fun reply(
        to: Messenger,
        code: Int,
        noShizuku: Boolean,
    ) {
        runCatching {
            val data =
                Bundle().apply {
                    putInt(KEY_CODE, code)
                    putBoolean(KEY_NO_SHIZUKU, noShizuku)
                }
            to.send(Message.obtain(null, MSG_RESULT).apply { this.data = data })
        }.onFailure { XLog.w("Shizuku 通道：回执发不出去（宿主可能已经走了）", it) }
    }

    /** 授权请求的回执 —— 与执行回执分开两条 `what`，调用方各等各的。 */
    private fun replyGrant(
        to: Messenger,
        granted: Boolean,
        noShizuku: Boolean,
    ) {
        runCatching {
            val data =
                Bundle().apply {
                    putBoolean(KEY_GRANTED, granted)
                    putBoolean(KEY_NO_SHIZUKU, noShizuku)
                }
            to.send(Message.obtain(null, MSG_GRANT_RESULT).apply { this.data = data })
        }.onFailure { XLog.w("Shizuku 通道：授权回执发不出去（宿主可能已经走了）", it) }
    }

    private fun incomingUid(): Int = android.os.Binder.getCallingUid()

    /** 允许调用本服务的 uid —— 自己，以及宿主那一族（`com.oplus.gesture` 及其进程）。 */
    private fun allowedCallers(): Set<Int> {
        val out = mutableSetOf(Process.myUid())
        for (pkg in HOST_PACKAGES) {
            runCatching { out += packageManager.getPackageUid(pkg, 0) }
        }
        return out
    }

    companion object {
        /** 这一档回给宿主的「退出码」：这个进程没有可用的 Shizuku / 没授权。 */
        const val NO_SHIZUKU = -1001

        const val MSG_EXEC = 1
        const val MSG_RESULT = 2
        const val MSG_REQUEST_GRANT = 3
        const val MSG_GRANT_RESULT = 4
        const val KEY_COMMAND = "cmd"
        const val KEY_CODE = "code"
        const val KEY_GRANTED = "granted"
        const val KEY_NO_SHIZUKU = "no_shizuku"

        /** 宿主那一族包名 —— 只有它和被授权过的自身 uid 能进来。 */
        private val HOST_PACKAGES = listOf("com.oplus.gesture")
    }
}
