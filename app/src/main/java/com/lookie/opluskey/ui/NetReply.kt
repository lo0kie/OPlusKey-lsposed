package com.lookie.opluskey.ui

/** 一趟「代取」的结果。 */
internal class NetReply(
    /** HTTP 状态码；`0` = 压根没连上（原因见 [error]）。 */
    val status: Int,
    val body: String,
    /** 失败原因；成功时是空串。 */
    val error: String,
) {
    /** 拿到了 2xx 且正文非空 —— 只有这一档才值得去解析。 */
    val ok: Boolean get() = status in 200..299 && body.isNotEmpty()
}
