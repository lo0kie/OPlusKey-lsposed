package com.lookie.opluskey.inject

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.wire.ModuleWire
import java.net.HttpURLConnection
import java.net.URL

/**
 * 注入侧代发网络请求 —— 宿主没有 `INTERNET`，只有这里能出网。
 *
 * ## 为什么是注入侧
 *
 *  宿主的清单里没有 `INTERNET`（`reference/gesture.apk` 的 49 条权限里只有
 *  `ACCESS_NETWORK_STATE`），而界面代码跑在**宿主进程** —— 页面自己发请求必然
 *  `SecurityException`（真机实证）。system_server 这一侧实测能连（`device-findings.md` 四十一）：
 *  uid 1000 持有 `INTERNET`、域允许 inet socket。
 *
 * ## 只做「取一段文本」
 *
 *  只认 `https`、只做 `GET`、正文截到 [ModuleWire.NET_BODY_LIMIT]、超时 [ModuleWire.NET_TIMEOUT_MS]。
 *  这不是通用代理：它能干的事**正好**是「检查更新」要的那一件（读一段 JSON），
 *  多一样能力就多一份被别的代码借去干别的事的机会。
 *
 * ## 三条纪律（都是本项目的硬约束）
 *
 *  1. **另起线程**：这条广播在 AMS 的广播入口上跑，联网是阻塞 IO —— 在那一层做等于把整个系统的
 *     广播分发停住几秒（五之二、3）。
 *  2. **整段兜 `Throwable`**：漏一个异常出去就是系统进程挂掉（五之二）。
 *  3. **DNS 必须先绑默认网络**：system_server 的 socket 没有「进程默认网络」，裸
 *     `InetAddress` / `HttpURLConnection` 会 `UnknownHostException`。这里走
 *     `Network#openConnection`，由它把这一趟绑在 `activeNetwork` 上（四十一）。
 */
internal object NetChannel {
    /** 回包在主线程发 —— 与其它注入侧广播同一条纪律（不在 hooker 体内直接 sendBroadcast）。 */
    private val main = Handler(Looper.getMainLooper())

    /**
     * 有没有网络可用 —— 没有就直接回一条「没网」，不去等超时。
     *
     *  `MissingPermission` 这一条按「谁在执行」判：这段跑在 **system_server**（uid 1000），
     *  `ACCESS_NETWORK_STATE` 它本来就有（`device-findings.md` 四十一核过）；模块清单里
     *  **不**声明任何权限 —— 写上它也管不到注入进去的那个身份，只会让人以为模块自己有进程。
     */
    @SuppressLint("MissingPermission")
    private fun activeNetwork(loader: ClassLoader?): android.net.Network? {
        val l = loader ?: return null
        return runCatching {
            val cm =
                SystemContext.get(l)?.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager
            cm?.activeNetwork
        }.getOrNull()
    }

    /**
     * 广播入口那一层的**分派** —— 只把 [handle] 包一层兜底。
     *
     *  为什么兜底放在这里而不是调用点：调用方（`KeyHooks.dispatchAction`）那个 `when` 已经贴着
     *  detekt 的 `CyclomaticComplexMethod` / `TooManyFunctions` 两条上限，再加一个 `try / catch`
     *  或一个私有函数就超；而「这一层在 system_server 里跑」这条纪律本来就该跟着 [NetChannel] 走。
     *
     *  **不套 `postGuarded`**：本类自己开后台线程，这一层只做分派 —— 联网是阻塞 IO，
     *  放在主线程上等于把广播分发停住几秒（五之二、3）。
     */
    fun dispatch(
        intent: Intent,
        loader: ClassLoader?,
    ) {
        try {
            handle(intent, loader)
        } catch (e: Throwable) {
            XLog.w("网络代取分派失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }

    /**
     * 处理一条 [ModuleWire.ACTION_NET_REQUEST]（由 [dispatch] 调，外部不要直接调）。
     *
     *  立刻返回（真正的活在后台线程上），调用方（AMS 广播入口）只留这一次分派。
     */
    private fun handle(
        intent: Intent,
        loader: ClassLoader?,
    ) {
        val id = intent.getLongExtra(ModuleWire.EXTRA_NET_ID, 0L)
        val url = intent.getStringExtra(ModuleWire.EXTRA_NET_URL).orEmpty()
        if (!url.startsWith("https://")) {
            // 只认 https：这条通道只用来读一段公开文本，明文传输没有理由
            XLog.w("网络代取：地址不是 https，拒绝（id=$id）")
            reply(loader, id, 0, "", "只接受 https 地址")
            return
        }
        val network = activeNetwork(loader)
        if (network == null) {
            XLog.w("网络代取：当前没有默认网络（id=$id）")
            reply(loader, id, 0, "", "当前没有网络")
            return
        }
        Thread(
            {
                var status = 0
                var body = ""
                var error = ""
                try {
                    //  **`network.openConnection` 而不是 `url.openConnection`**：前者把这一趟
                    //  绑在默认网络上，DNS 才解析得出来（裸的会 UnknownHostException，见类注释）。
                    val conn = network.openConnection(URL(url)) as HttpURLConnection
                    try {
                        conn.connectTimeout = ModuleWire.NET_TIMEOUT_MS
                        conn.readTimeout = ModuleWire.NET_TIMEOUT_MS
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "OPlusKey")
                        status = conn.responseCode
                        body =
                            conn.inputStream
                                .bufferedReader()
                                .use { it.readText() }
                                .take(ModuleWire.NET_BODY_LIMIT)
                        if (body.length >= ModuleWire.NET_BODY_LIMIT) error = "正文超长已截断"
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Throwable) {
                    error = "${e.javaClass.simpleName}: ${e.message}"
                }
                XLog.w(
                    "网络代取：id=$id status=$status 长度=${body.length}" +
                        if (error.isEmpty()) "" else " 错误=$error",
                )
                reply(loader, id, status, body, error)
            },
            "opluskey-net",
        ).start()
    }

    /** 回包 —— 主线程发，整段兜住（这一层在 system_server 里）。 */
    private fun reply(
        loader: ClassLoader?,
        id: Long,
        status: Int,
        body: String,
        error: String,
    ) {
        main.post {
            try {
                val ctx =
                    loader?.let { SystemContext.get(it) } ?: run {
                        XLog.w("网络代取：借不到 Context，回不了包（id=$id）")
                        return@post
                    }
                ctx.sendBroadcast(
                    Intent(ModuleWire.ACTION_NET_RESULT)
                        .setPackage(com.lookie.opluskey.core.model.HostApp.PACKAGE)
                        .putExtra(ModuleWire.EXTRA_NET_ID, id)
                        .putExtra(ModuleWire.EXTRA_NET_STATUS, status)
                        .putExtra(ModuleWire.EXTRA_NET_BODY, body)
                        .putExtra(ModuleWire.EXTRA_NET_ERROR, error),
                )
            } catch (e: Throwable) {
                XLog.w("网络代取：回包失败（已忽略）：${e.javaClass.simpleName}", e)
            }
        }
    }
}
