package com.lookie.opluskey.inject

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    /** 手动跟随重定向的跳数上限 —— 自家接口正常最多一跳。 */
    private const val MAX_REDIRECTS = 3

    /** 一次读多少字符 —— 只影响拷贝开销，不影响上限（上限是 `NET_BODY_LIMIT`）。 */
    private const val READ_CHUNK = 4096

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
                    val found = follow(network, url)
                    if (found == null) {
                        error = "重定向无法继续"
                    } else {
                        val (conn, code) = found
                        status = code
                        try {
                            val (text, truncated) = readBounded(conn)
                            body = text
                            if (truncated) error = "正文超长已截断"
                        } finally {
                            conn.disconnect()
                        }
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

    /**
     * 取到「最终的连接 + 状态码」—— 重定向**手动跟**，每一跳都重新校验 https。
     *
     *  不能让它自动跟：`followRedirects` 默认开，入口只校验了首跳，`302 → http://…` 会被
     *  静默照取，等于这条「只读一段公开 https 文本」的通道被降级成任意明文代理。
     *
     *  每一跳都用 `network.openConnection` 重开：绑默认网络是 DNS 能解析的前提
     *  （见类注释第三条），跟着重定向换成裸 `URL` 会丢掉这一层。
     *
     * @return 连接与状态码；重定向链断开 / 超过上限时 `null`（调用方按错误回包）
     */
    private fun follow(
        network: android.net.Network,
        start: String,
    ): Pair<HttpURLConnection, Int>? {
        var target = start
        var hops = 0
        var result: Pair<HttpURLConnection, Int>? = null
        while (result == null && hops <= MAX_REDIRECTS) {
            val conn = open(network, target) ?: break
            val code = conn.responseCode
            val location = if (code in 300..399) conn.getHeaderField("Location") else null
            if (location == null) {
                result = conn to code
            } else {
                conn.disconnect()
                val next = redirectTarget(target, location)
                //  `null` = 这一跳不合法（非 https / 解析不出来）：把跳数顶过上限，循环自然结束
                if (next == null) {
                    hops = MAX_REDIRECTS + 1
                } else {
                    target = next
                    hops++
                }
            }
        }
        if (result == null) XLog.w("网络代取：重定向链断了（跳数 $hops，上限 $MAX_REDIRECTS）")
        return result
    }

    /**
     * 下一跳的地址 —— 只认 https、且必须能解析。
     *
     *  每一跳都要重新校验：`302 → http://…` 走的是同一个 `Location` 头，
     *  只看首跳等于把这条通道降级成任意明文代理。
     */
    private fun redirectTarget(
        from: String,
        location: String,
    ): String? {
        val next = runCatching { URL(URL(from), location).toString() }.getOrNull()
        return when {
            next == null -> {
                XLog.w("网络代取：重定向目标解析失败（$location）")
                null
            }

            !next.startsWith("https://") -> {
                XLog.w("网络代取：重定向到非 https 地址，拒绝（$next）")
                null
            }

            else -> {
                next
            }
        }
    }

    /** 开一条 https 连接（超时、方法、UA 在这里一处设定）。 */
    private fun open(
        network: android.net.Network,
        url: String,
    ): HttpURLConnection? =
        try {
            //  **`network.openConnection` 而不是 `url.openConnection`**：前者把这一趟
            //  绑在默认网络上，DNS 才解析得出来（裸的会 UnknownHostException，见类注释）。
            (network.openConnection(URL(url)) as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = ModuleWire.NET_TIMEOUT_MS
                readTimeout = ModuleWire.NET_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "OPlusKey")
            }
        } catch (e: Throwable) {
            XLog.w("网络代取：连接失败（$url）：${e.javaClass.simpleName}")
            null
        }

    /**
     * 读正文，**读到上限就停** —— 不与正文长度赛跑。
     *
     *  不能先 `readText()` 再 `take(limit)`：那一版的上限只截断结果、不截断读取，
     *  对端给一段超大正文（或无限流）时整份 String 先进堆 —— 这段跑在 system_server 里，
     *  一次 OOM 就是软重启。这里按块读、按上限截，内存上限由 [ModuleWire.NET_BODY_LIMIT] 决定。
     *
     *  另加一条**总时长**闸：`readTimeout` 是「两次读之间」的上限，服务端每几秒吐一个字节就能
     *  把这条线程长期占住。
     *
     * @return 正文 + 有没有被截断
     */
    private fun readBounded(conn: HttpURLConnection): Pair<String, Boolean> {
        val limit = ModuleWire.NET_BODY_LIMIT
        val deadline = SystemClock.elapsedRealtime() + ModuleWire.NET_TIMEOUT_MS * 2
        val sb = StringBuilder(minOf(limit, READ_CHUNK))
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buf = CharArray(READ_CHUNK)
            while (sb.length < limit) {
                if (SystemClock.elapsedRealtime() > deadline) return sb.toString() to true
                val room = minOf(buf.size, limit - sb.length)
                val n = reader.read(buf, 0, room)
                if (n <= 0) break
                sb.append(buf, 0, n)
            }
        }
        return sb.toString() to (sb.length >= limit)
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
