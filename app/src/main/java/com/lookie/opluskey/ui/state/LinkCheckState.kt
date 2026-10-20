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
    /**
     * 注入侧所在进程的启动时刻（wall clock，见 `ProcClock.startWall`）；0 = 对面是还没带这一项的旧版。
     *
     *  判「换过包但没重启」的**主尺子**：与安装包的 `lastUpdateTime` 比大小即可，
     *  不依赖任何人记得去改 `BUILD_ID`。
     */
    val procStart: Long = 0L,
)

/**
 * 「系统那一侧没回包」这件事到底是哪一档 —— 三种来路，用户要做的事**各不相同**。
 *
 *  光看「没回包」答不了这个：首次安装没重启、装好新版等重启、作用域漏了 `system`，
 *  现象一模一样。分开靠两样事实：**这台机器上它曾经回过话吗**、**那次是不是本轮开机**。
 *  两者都在界面状态文件里（见 [UiState.injectEverAlive]）。
 *
 *  唯一分不开的是 [FIRST] 里那两种可能（作用域没勾 / 勾了但没重启过）—— 作用域只有框架自己知道，
 *  模块没有权限读（`/data/adb` 对宿主进程与 system_server 都不可读，见 `docs/device-findings.md` 六之六）。
 *  所以那一档的文案必须**同时**说清两件事：先去勾，再重启。
 */
internal enum class InjectPendingKind {
    /** 没有结论：已经回包了，或者自检还没问完。 */
    NONE,

    /** 从来没起来过 —— 作用域可能没勾，也可能勾了但从没重启过。要做：勾 + 重启。 */
    FIRST,

    /** 以前起来过，本轮回包没来 —— 而且**包是这次开机之后才装的** ⇒ 装好/改好了在等重启。要做：只重启。 */
    UPDATED,

    /**
     * 以前起来过、本轮回包没来，但**包是这次开机之前就装好的** ⇒ 没有「等重启」这回事。
     *
     *  这一档就是「作用域被撤掉了」（用户把「系统框架」取消勾选）：本轮开机压根没注入它，
     *  与「从没起来过」同一个处置（去勾上 + 重启），但成因不同，所以单独立一档便于看日志。
     */
    SCOPE_GONE,

    /** 本轮开机里刚才还在、这会儿不回了 —— 掉线（作用域被改 / 进程被换掉）。要做：查日志。 */
    LOST,
}

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
