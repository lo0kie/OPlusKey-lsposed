package com.lookie.opluskey.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.NetReply
import com.lookie.opluskey.core.wire.PageWire
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

// 页面 → 注入侧「替我取一个 URL」的一问一答。
//
// 为什么不能自己发：宿主的清单里没有 `INTERNET`，而界面代码跑在**宿主进程**（真机实证
// `SecurityException`）。能出网的是 system_server 那一侧（`inject/NetChannel`，见
// `docs/device/lsposed.md` 四十一）。协议那一头在 `ModuleWire.ACTION_NET_REQUEST`。

/**
 * 请注入侧取一次 [url] 的正文，最多等 [timeoutMs]。
 *
 *  一问一答靠 [ModuleWire.EXTRA_NET_ID] 配对：同一刻可能有别的代取在跑，回包只认自己那一号。
 *
 *  **收不到就返回 `null`**（不是抛异常）：检查更新是「有就提示、没有就算」的事，
 *  超时、广播发不出去、接收器注册失败在这个调用点全都归成「这次没查到」——
 *  调用方只关心「有没有结论」，不关心断在哪一环（每种断法都有一行 W 日志）。
 *
 *  跑在调用方的协程里（IO 不做 IO 阻塞：真正的网络在注入侧那条后台线程上），
 *  所以这里只等回包，**不占主线程**。
 */
internal suspend fun SettingsPage.fetchText(
    url: String,
    timeoutMs: Long = ModuleWire.NET_TIMEOUT_MS.toLong(),
): NetReply? {
    val id = NEXT_NET_ID.incrementAndGet()
    val reply = CompletableDeferred<NetReply>()
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context?,
                intent: Intent?,
            ) {
                if (intent == null || intent.action != ModuleWire.ACTION_NET_RESULT) return
                if (intent.getLongExtra(ModuleWire.EXTRA_NET_ID, -1L) != id) return
                reply.complete(
                    NetReply(
                        status = intent.getIntExtra(ModuleWire.EXTRA_NET_STATUS, 0),
                        body = intent.getStringExtra(ModuleWire.EXTRA_NET_BODY).orEmpty(),
                        error = intent.getStringExtra(ModuleWire.EXTRA_NET_ERROR).orEmpty(),
                    ),
                )
            }
        }
    val registered = context.registerLocalReceiver("网络代取（这一趟作罢）", receiver, ModuleWire.ACTION_NET_RESULT)
    if (!registered) return null
    try {
        val sent =
            PageWire.send(
                context,
                HostApp.PACKAGE,
                Intent(ModuleWire.ACTION_NET_REQUEST).apply {
                    putExtra(ModuleWire.EXTRA_NET_ID, id)
                    putExtra(ModuleWire.EXTRA_NET_URL, url)
                },
            )
        if (!sent) {
            XLog.w("网络代取：广播发不出去（这一趟作罢）")
            return null
        }
        val result = withTimeoutOrNull(timeoutMs) { reply.await() }
        if (result == null) {
            XLog.w("网络代取：等回包超时 ${timeoutMs}ms（id=$id）")
        } else {
            XLog.w(
                "网络代取：id=$id status=${result.status} 长度=${result.body.length}" +
                    if (result.error.isEmpty()) "" else " 错误=${result.error}",
            )
        }
        return result
    } finally {
        runCatching { context.unregisterReceiver(receiver) }
    }
}

/** 这一趟的编号 —— 进程内自增即可（配对只用它，不跨进程存）。 */
private val NEXT_NET_ID = AtomicLong(0)
