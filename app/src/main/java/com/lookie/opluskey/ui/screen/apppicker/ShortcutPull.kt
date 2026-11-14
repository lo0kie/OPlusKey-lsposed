package com.lookie.opluskey.ui.screen.apppicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.wire.registerLocalReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 「这个包在桌面上钉了哪几条」—— 向注入侧（`system_server`）问一趟。
 *
 * ## 为什么必须问注入侧
 *
 *  宿主自己那一趟（`LauncherApps.getShortcuts`）只拿得到 `动态 / 清单`；**钉住的那一档**按
 *  「钉它的那个桌面」存，宿主不是桌面宿主，一条也拿不到 —— 换系统身份走同一个 API 也一样
 *  （真机实测 `动态 3 / 清单 0 / 钉住 0`）。而注入侧住在 `system_server` 里，能直接读快捷方式
 *  服务手上的活数据（见 `inject/ShortcutRead`），这一趟就是去问它。
 *
 * ## 一问一答、有上限
 *
 *  发 [ModuleWire.ACTION_SHORTCUT_QUERY]，等 [WAIT_MS]；等不到就当这一趟没要到（返回 `null`），
 *  页面照旧只列本进程读到的那些 —— **不能因为对面没起来就让清单空掉**。
 *
 *  等待走 `delay` 而不是阻塞线程：调用方整段在 IO 上（见 `ActivityPickerList` 的
 *  `withContext(Dispatchers.IO)`），挂起期间那条线程可以先去干别的。
 */
internal object ShortcutPull {
    /** 等注入侧回包的上限（毫秒）。本地广播实测几十毫秒；这条只是「别无限等」的兜底。 */
    private const val WAIT_MS = 300L

    /** 这一趟的编号 —— 一次进页可能问好几条（翻应用、分身），回包靠它配对。 */
    private val seq = AtomicInteger()

    /**
     * @return `id → 名字`（顺序照系统给的）；`null` = 这一趟**没要到**（没注册上 / 没回包），
     *   与「要到了、但是空的」是两件事，页面据此决定日志怎么说
     */
    suspend fun pull(
        context: Context,
        pkg: String,
        userId: Int,
    ): List<Pair<String, String>>? {
        val req = seq.incrementAndGet().toString()
        //  回包在主线程的广播回调里写、在这次调用的线程上读 —— 用原子引用而不是普通变量：
        //  普通变量在这里既没有可见性保证，也说不清「读到的到底是哪一趟」。
        val reply = AtomicReference<List<Pair<String, String>>?>(null)
        //  **回包一到就醒**，不是固定睡满 [WAIT_MS]：本地广播实测几十毫秒就回来了，
        //  睡满等于给每一次进页白加 300ms（翻应用 / 分身一次要问好几条）。
        //  `WAIT_MS` 只当「别无限等」的上限。
        val done = CompletableDeferred<Unit>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != ModuleWire.ACTION_SHORTCUT_RESULT) return
                    if (intent.getStringExtra(ModuleWire.EXTRA_SHORTCUT_REQ) != req) return
                    val ids = intent.getStringArrayListExtra(ModuleWire.EXTRA_SHORTCUT_IDS).orEmpty()
                    val labels = intent.getStringArrayListExtra(ModuleWire.EXTRA_SHORTCUT_LABELS).orEmpty()
                    reply.set(ids.mapIndexed { i, id -> id to (labels.getOrNull(i) ?: id) })
                    //  先写结果再放行；重复回包（补发那条）时 `complete` 是幂等的
                    done.complete(Unit)
                }
            }
        val registered =
            context.registerLocalReceiver(
                "快捷方式（系统身份那一趟没要到，只列本进程读到的）",
                receiver,
                ModuleWire.ACTION_SHORTCUT_RESULT,
            )
        if (!registered) return null
        val sent =
            PageWire.send(
                context,
                HostApp.PACKAGE,
                Intent(ModuleWire.ACTION_SHORTCUT_QUERY)
                    .putExtra(ModuleWire.EXTRA_SHORTCUT_REQ, req)
                    .putExtra(ModuleWire.EXTRA_SHORTCUT_PKG, pkg)
                    .putExtra(ModuleWire.EXTRA_SHORTCUT_USER, userId),
            )
        try {
            if (sent) withTimeoutOrNull(WAIT_MS) { done.await() }
        } finally {
            //  页面走了（协程被取消）也要把接收器摘掉，否则下一次进来会再注册一份
            runCatching { context.unregisterReceiver(receiver) }
        }
        return reply.get()
    }
}
