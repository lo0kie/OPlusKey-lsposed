package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire

// 页面往宿主 / 注入侧**发话**的那两件：配置广播、重问自检。两条都走 `PageWire`（显式广播）。
//
// 与配置落盘（`ConfigWrite.kt`）分开：那一边只碰文件与页面状态，这一边是跨进程的账，
// 而跨进程只认广播（注入侧读不到配置文件，见 `AGENTS.md` 五之二、9）。

/**
 * 问一次注入侧「你手上那份配置的指纹是多少」——**配置一变就要问**（见 [write]）。
 *
 *  注入侧在 AMS 广播入口上**同步**处理这条问句，所以紧接着 [publishConfig] 发它，回包一定带新指纹；
 *  回包由设置页那个接收器收下，`injectStatus` 与链路段一起刷新。
 *
 *  注意：**凡是让本地指纹变了、又发了配置广播的路径，都要跟着问这一句**（[write] 与
 *  `saveCommands` 两处）。原注释写「重置 / 导入那些**不换本地指纹**的路径不需要它」——
 *  那个前提是错的：指纹里含「长按绑的是不是串行命令」这一位，它读命令库，
 *  所以**换命令库也是换指纹**；导入正是「先 `write`（问一次、按旧命令库算的）再 `saveCommands`
 *  （只发不问）」，后一步一改命令库，刚问回来的那份又过期了。
 */
internal fun SettingsPage.resyncInjectStatus() {
    val sent =
        PageWire.send(
            context,
            HostApp.PACKAGE,
            android.content.Intent(ModuleWire.ACTION_INJECT_QUERY),
        )
    if (sent) XLog.w("配置已变：向注入侧重问一次自检（把新指纹带回来）")
}

/** 把完整配置广播给注入侧（inject/ConfigChannel，跑在 system_server）。@return 广播是否真的发出去了。 */
internal fun SettingsPage.publishConfig(config: Config): Boolean {
    if (!PageWire.send(context, HostApp.PACKAGE, ConfigBroadcast.intent(config) { CommandLibrary.longIsSerial(config, it) })) return false
    XLog.w("配置广播已发出（总闸=${config.global.enabled} 侧键=${config.key(KeyId.PLUS).enabled}）")
    return true
}
