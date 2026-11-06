package com.lookie.opluskey.core.wire

/**
 * 一趟「代取」的结果。
 *
 *  从 `ui` 挪到 `core/wire` 之后**不能再是 `internal`**：`internal` 只在本 Gradle 模块内可见，
 *  而它现在要被 `app` 那一侧（网络适配、检查更新）引用 —— 与 `ModuleWire` / `ConfigBroadcast`
 *  这几个同包类型一样按 public 暴露。
 */
class NetReply(
    /** HTTP 状态码；`0` = 压根没连上（原因见 [error]）。 */
    val status: Int,
    val body: String,
    /** 失败原因；成功时是空串。 */
    val error: String,
) {
    /** 拿到了 2xx 且正文非空 —— 只有这一档才值得去解析。 */
    val ok: Boolean get() = status in 200..299 && body.isNotEmpty()
}
