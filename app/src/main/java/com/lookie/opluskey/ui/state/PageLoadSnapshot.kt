package com.lookie.opluskey.ui.state

import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.Config

/**
 * 建页那一刻要读的**三份文件**（主配置 / 命令库 / 界面偏好）—— 一份快照，**纯数据**。
 *
 *  ## 为什么中间要隔这一层
 *
 *  这三份以前是 `SettingsPage.createView()` 里**同步**读的。而 `createView()` 跑在宿主
 *  Activity 的**主线程**上（`SettingsPageHooks` 在 `onCreate` / `onResume` 那条路上回调它），
 *  于是一次进设置页就是三趟「open + readText + parseJSON」全压在首帧前的主线程上：
 *  StrictMode 的 diskRead 违规，也是「从设置进模块页面偶尔顿一下」的一个来源。
 *
 *  现在整段读放在 [readPageLoadSnapshot] 里、由组合期的 `loadPageState` 挪到 IO 线程上跑，
 *  回到主线程只做「把这份结果写进状态」（见 `ui/PageLoad.kt`）。
 *
 *  分开还有第二个理由：读的那一半**不能引用 `SettingsPage`** —— `ui.state` 不依赖 `ui`
 *  （`docs/decoupling.md` 的清零包环），所以只能拿这份数据当两边之间的接口。
 */
internal class PageLoadSnapshot(
    val config: Config,
    /** 主配置读不懂（[ConfigStore.Load.Unreadable]）⇒ 页面进只读，本次不许写回。 */
    val configReadOnly: Boolean,
    val commands: List<Command>,
    /** 命令库读不懂 ⇒ 命令库进只读（见 [ConfigState.commandsReadOnly]）。 */
    val commandsReadOnly: Boolean,
    val showSystemApps: Boolean,
    val showUnopenable: Boolean,
)

/**
 * 读齐建页需要的三份文件。**阻塞调用** —— 调用方负责把它放在非主线程（见 `ui/PageLoad.kt`）。
 *
 * ## 三态纪律原样保留
 *
 * 「文件不存在」与「文件在、但读不懂」必须分开：只有前两态（[ConfigStore.Load.Ok] /
 * `Absent`）才允许之后写回，`Unreadable` 一律置只读位。理由见 [ConfigState.readOnly] ——
 * 读不懂时手上这份默认值不是用户的真相，谁写回谁就把用户配的东西整份抹掉。
 *
 * ## 兜底：这个函数**不抛**
 *
 * 真出了意外（文件系统抽风、某处没兜住的 OOM），退回「两份都只读」。那是最保守的一档：
 * 页面照常能看（默认值），但一个字节都写不回去 —— 用户的文件不会被覆盖。
 * 少了这一层，异常会从 `LaunchedEffect` 里冒出去，`configState.loaded` 永远翻不成 true，
 * 页面就停在一屏底色上，连「为什么」都看不到。
 */
internal fun readPageLoadSnapshot(): PageLoadSnapshot =
    try {
        val (config, configReadOnly) =
            when (val l = ConfigStore.load()) {
                is ConfigStore.Load.Ok -> {
                    l.config to false
                }

                ConfigStore.Load.Absent -> {
                    XLog.w("还没有配置文件（首装 / 刚清过数据）—— 用默认值，可以写回")
                    Config.DEFAULT to false
                }

                ConfigStore.Load.Unreadable -> {
                    // 这条必须是 w：它是「页面为什么变只读」的唯一判据行
                    XLog.w("配置读不懂 ⇒ 页面进只读，本次不许写回（否则会把默认值当真相固化）")
                    Config.DEFAULT to true
                }
            }

        val (commands, commandsReadOnly) =
            when (val l = CommandLibrary.loadState()) {
                is CommandLibrary.Load.Ok -> {
                    l.commands to false
                }

                CommandLibrary.Load.Absent -> {
                    XLog.w("还没有命令库文件（首装 / 刚清过数据）—— 用空表，可以写回")
                    emptyList<Command>() to false
                }

                CommandLibrary.Load.Unreadable -> {
                    // 这条必须是 w：它是「命令库为什么进只读」的唯一判据行
                    XLog.w("命令库读不懂 ⇒ 命令库进只读，本次不许写回（否则会把用户的命令覆盖掉）")
                    emptyList<Command>() to true
                }
            }

        // 一次读全：两项各读一遍会把同一个文件 open + parse 两遍（同 [UiState.readAll] 的注释）。
        val (showSystemApps, showUnopenable) = UiState.readAll()

        PageLoadSnapshot(
            config = config,
            configReadOnly = configReadOnly,
            commands = commands,
            commandsReadOnly = commandsReadOnly,
            showSystemApps = showSystemApps,
            showUnopenable = showUnopenable,
        )
    } catch (e: Throwable) {
        XLog.w("建页读盘失败（两份都进只读、界面用默认值）：${e.message}", e)
        PageLoadSnapshot(
            config = Config.DEFAULT,
            configReadOnly = true,
            commands = emptyList(),
            commandsReadOnly = true,
            showSystemApps = false,
            showUnopenable = false,
        )
    }
