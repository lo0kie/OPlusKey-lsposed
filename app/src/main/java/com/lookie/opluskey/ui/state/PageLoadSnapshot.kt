package com.lookie.opluskey.ui.state

import android.content.Context
import android.content.pm.PackageManager
import com.lookie.opluskey.core.ModuleInfo
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
    /** 界面状态文件读出来的那几项（显示偏好 + 链路历史），整包里带着，不再拆成一个一个参数。 */
    val prefs: UiPrefs,
    /**
     * 判「某一侧跑的是不是盘上这份」要的三样事实，打包在一起：
     * 参数表已经到顶了（detekt 的 `LongParameterList`），而这三项本来也只在同一个判据里用。
     */
    val linkBaseline: LinkBaseline,
)

/**
 * 链路的「基线事实」—— 判「某个进程是不是还在跑旧版」要的那三样。
 *
 * @param installedAt 盘上那份模块包的更新时间（`PackageInfo.lastUpdateTime`，wall clock）。
 *   与某一侧进程的启动时刻一比就知道它加载的是不是盘上这份。0 = 读不到（宿主没有查包可见性之类），
 *   此时**不判**这一档，宁可不说也不乱说。
 * @param injectEverAlive 系统那一侧**曾经**回过话没有（只增不减，见 [UiState]）
 * @param injectAliveBoot 上一次回话时的那轮开机（0 = 从没回话）——
 *   用来把「装好等重启」与「本轮刚才还在、现在不回」分开
 */
internal class LinkBaseline(
    val installedAt: Long,
    val injectEverAlive: Boolean,
    val injectAliveBoot: Long,
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
internal fun readPageLoadSnapshot(context: Context): PageLoadSnapshot =
    try {
        val (config, configReadOnly) = loadConfigOrReadOnly()
        val (commands, commandsReadOnly) = loadCommandsOrReadOnly()

        // 一次读全：逐项各读一遍会把同一个文件 open + parse 好几遍（同 [UiState.readPrefs] 的注释）。
        val prefs = UiState.readPrefs()

        PageLoadSnapshot(
            config = config,
            configReadOnly = configReadOnly,
            commands = commands,
            commandsReadOnly = commandsReadOnly,
            prefs = prefs,
            linkBaseline =
                LinkBaseline(
                    installedAt = readInstalledAt(context),
                    injectEverAlive = prefs.injectEverAlive,
                    injectAliveBoot = prefs.injectAliveBoot,
                ),
        )
    } catch (e: Throwable) {
        XLog.w("建页读盘失败（两份都进只读、界面用默认值）：${e.message}", e)
        PageLoadSnapshot(
            config = Config.DEFAULT,
            configReadOnly = true,
            commands = emptyList(),
            commandsReadOnly = true,
            prefs =
                UiPrefs(
                    showSystemApps = false,
                    showUnopenable = false,
                    injectEverAlive = false,
                    injectAliveBoot = 0L,
                ),
            linkBaseline = LinkBaseline(installedAt = 0L, injectEverAlive = false, injectAliveBoot = 0L),
        )
    }

/**
 * 读主配置，并把「读不懂」翻成只读位。
 *
 *  单独拆出来只为两件事：`readPageLoadSnapshot` 已经贴着 `LongMethod` 的线，
 *  以及这三态的判断（[ConfigStore.Load]）不该和「读命令库」混在同一个函数里读。
 */
private fun loadConfigOrReadOnly(): Pair<Config, Boolean> =
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

/** 读命令库，同上（三态与只读位）。 */
private fun loadCommandsOrReadOnly(): Pair<List<Command>, Boolean> =
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

/**
 * 盘上那份模块包的更新时间 —— 判「某个进程跑的是不是这一份」的那把尺子（见 [PageLoadSnapshot.installedAt]）。
 *
 *  读不到就返回 0（= 不判）。**不抛**：它只是诊断用的一位，为此让整页进退默认值不值得。
 *  查的是**模块自己的包名**（[ModuleInfo.PACKAGE]）而不是 `context.packageName` —— 这段代码跑在
 *  宿主进程里，后者拿到的是宿主包名（同 `ModuleInfo` 那段注释）。
 */
private fun readInstalledAt(context: Context): Long =
    try {
        context.packageManager
            .getPackageInfo(ModuleInfo.PACKAGE, PackageManager.PackageInfoFlags.of(0))
            .lastUpdateTime
    } catch (e: Throwable) {
        XLog.w("读模块包更新时间失败（这一档不判）：${e.message}")
        0L
    }
