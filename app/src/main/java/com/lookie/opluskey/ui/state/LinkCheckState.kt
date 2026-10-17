package com.lookie.opluskey.ui.state

// 链路自检的两种纯数据：注入侧回包与一段检查结果。

/**
 * 注入侧（system_server）回包的内容 —— 应答 `ModuleWire.ACTION_INJECT_QUERY`。
 *
 * @param hooks 装上的侧键接管点数（0 = 一个都没装上）
 * @param configOk 注入侧收到过配置没有（false ⇒ 它还在拿 `Config.DEFAULT` 顶着跑）
 * @param configTag 注入侧手上那份配置的指纹（`ConfigBroadcast.tag`，空 = 没收到过）。
 *   页面拿它与**自己手上那份**比：相等才叫「设置生效」—— 只看 [configOk] 答不了
 *   「后来改的那一次到了没有」。
 * @param lastGestureAt 最后一次判出手势的时刻（`elapsedRealtime()`，0 = 开机以来从未）
 */
internal data class InjectStatus(
    val hooks: Int,
    val configOk: Boolean,
    val configTag: String,
    val lastGestureAt: Long,
    /** 注入侧跑的是哪一次构建（`ModuleInfo.BUILD_ID`）；空串 = 对面是还没带这个字段的旧版。 */
    val buildId: String = "",
)

/**
 * 链路自检里的一段（界面上叫「运行状态」，见 `AboutScreen`）。
 *
 * **文案是给用户看的，不是给开发看的**（两轮）：不出现「注入侧 / 回话 /
 * 接管点 / 真配置 / 就绪 / 同步 / 读取」这类词，也不报时间戳；**「哪一段断了」这件事本身
 * 也只写到段名为止**，往下怎么坏的去看日志（`XLog`）。
 *
 * @param blocking 这一段不通**要不要报成故障**。「功能还没被验证过」是信息、不是故障 ——
 *   拿它去弹常驻提示会把一台正常的机器一直骂着（见 [SettingsPage.linkChecks]）。
 */
internal data class LinkCheck(
    val name: String,
    val ok: Boolean,
    val detail: String,
    val blocking: Boolean = true,
)
