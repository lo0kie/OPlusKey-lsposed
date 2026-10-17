package com.lookie.opluskey.bridge.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.RomProfile

/**
 * `RomProfile.BuiltIn.message` 那条落点的执行器 —— **bind 后发一条 `Message`**。
 *
 * 这类服务没有 `onStartCommand`，`startService` 对它们是空操作（实测：调用成功、AMS 也受理，
 * 但目标进程毫无动静）。`send` 是单向 binder 调用，消息进了对方的 Handler 队列就可以解绑。
 *
 * ## 为什么只能有一份
 *
 *  这段「bind → send(what) → finally unbind」原来在 `host/action/AppLauncher`（侧键场景动作）
 *  与 `bridge/host/FlashlightController`（手电筒）各写了一遍。两份的差别不在写法、在**边界**：
 *  后加的那份没有「一直连不上就收尾」的时限，也没有把失败说出来 —— 它把「bind 被受理」
 *  当成了「消息发到了」，于是服务其实没连上时照样返回成功、把调用方的兜底整条跳过，
 *  而日志里一个字都没有（`AGENTS.md` 五、2 / 五、4）。
 *  所以这一族只此一份：改「怎么发消息」只改这里，两边一起变。
 *
 *  落点选在 `bridge/host` 而不是 `host/action`：`AppLauncher` 与 `FlashlightController` 分属两层，
 *  只有底下这层是两边都依赖得起的。
 */
internal object BuiltInMessenger {
    /** 等服务连上的上限，超时就自己解绑（毫秒）。 */
    const val TIMEOUT_MS = 3000L

    /**
     * 发一条 `Message` 过去。
     *
     * @param tag 日志前缀（`oplus:flash_memory` / `手电筒（ColorOS 接口）` 这类）—— 两条调用方
     *   各自带上自己的上下文，日志里要能一眼分出是谁发的
     * @param onFail 失败出口（bind 被拒 / 绑定抛 / 超时没连上 / 发送抛）。走命令链的调用方传自己的
     *   `onFailure`；只借这条通道、自身另有兜底的调用方传 `null` —— 失败仍有日志
     * @return **bind 被受理了没有**。它**不等于**「消息发出去了」：连接是异步的，
     *   真正的结果与失败都在 [onFail] 与日志里
     */
    fun send(
        ctx: Context,
        target: RomProfile.BuiltIn,
        tag: String,
        onFail: (() -> Unit)? = null,
    ): Boolean {
        val what =
            target.message ?: run {
                XLog.w("$tag：这个落点没有 Message.what，发不了消息")
                return false
            }
        val short = "${target.owner}/${target.service.substringAfterLast('.')}"
        val connection = Watcher(ctx, what, tag, short, onFail)
        val bound =
            try {
                ctx.bindService(
                    Intent().setComponent(ComponentName(target.owner, target.service)),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            } catch (e: Throwable) {
                XLog.w("bind $tag 失败：${e.javaClass.simpleName}: ${e.message}")
                onFail?.invoke()
                return false
            }
        if (!bound) {
            // 同步返回 false = 这次绑定根本没成立（组件不存在 / 权限不够），`onServiceConnected`
            // 永远不会来。不能还等 TIMEOUT_MS：反馈会白白迟到，而且超时那条日志会把原因说成
            // 「没连上」，掩盖真正的错。
            XLog.w("$tag 的 Messenger 服务绑不上（bindService 返回 false）：$short")
            onFail?.invoke()
            return false
        }
        // 「受理」与「连上」是两件事，中间可能隔到 TIMEOUT_MS —— 这一段空白期也要留一行，
        //  否则真机上只看得到 3 秒后的超时，看不出请求到底发出去没有。
        XLog.w("$tag：已请求绑定 $short，等它连接（上限 ${TIMEOUT_MS}ms）")
        // 服务一直连不上就自己收尾，别把连接挂着。
        //  注意：还要**说出来**：只 `unbindService` 的话既不记日志也不报失败 ——
        //  用户看到的是「按下去什么都没发生」，而日志里一个字都没有。
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (!connection.handled) {
                    XLog.w("$tag 的 Messenger 服务 ${TIMEOUT_MS}ms 没连上，解绑放弃")
                    connection.abandon()
                    onFail?.invoke()
                }
            },
            TIMEOUT_MS,
        )
        return true
    }

    /**
     * 这一次绑定的连接对象 —— `handled` 就是「`onServiceConnected` 来过没有」，
     * 上面那个超时用它判断该不该放弃。
     */
    private class Watcher(
        private val ctx: Context,
        private val what: Int,
        private val tag: String,
        private val short: String,
        private val onFail: (() -> Unit)?,
    ) : ServiceConnection {
        @Volatile
        var handled = false

        override fun onServiceConnected(
            name: ComponentName,
            binder: IBinder,
        ) {
            handled = true
            try {
                // 服务端会向 replyTo 回执，不设会在它那边空指针
                val message = Message.obtain(null, what)
                message.replyTo = Messenger(Handler(Looper.getMainLooper()))
                Messenger(binder).send(message)
                XLog.w("$tag → Messenger.send(what=$what)($short)")
            } catch (e: Throwable) {
                XLog.w("$tag 的 Messenger 消息发不出去：${e.javaClass.simpleName}: ${e.message}")
                onFail?.invoke()
            } finally {
                abandon()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // 只连一次、发完就解绑：断开这件事没有后续动作要做（超时由上面那支管）。
        }

        /** 解绑（成功发完与超时放弃两条路共用）。解不掉只记日志 —— 那是系统侧的事。 */
        fun abandon() {
            try {
                ctx.unbindService(this)
            } catch (e: Throwable) {
                XLog.w("$tag 解绑失败（已忽略）：${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}
