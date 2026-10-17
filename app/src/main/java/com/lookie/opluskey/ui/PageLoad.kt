package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.state.PageLoadSnapshot
import com.lookie.opluskey.ui.state.readPageLoadSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 建页那次「读盘 + 落状态」的两半。
//
// 为什么要拆成两半：读的那一半要挪到 IO 线程上（以前跑在宿主主线程，见
// `ui/state/PageLoad.kt` 的类注释），而写的那一半必须回主线程（写的是 Compose 状态）。
// 中间用 [PageLoadSnapshot] 这份纯数据过河，谁也不认识谁。

/**
 * 建页期的整段初始化 —— 由 [com.lookie.opluskey.ui.screen.Page] 的 `LaunchedEffect(Unit)` 调一次。
 *
 * 三步，前两步都在 IO 上：
 *  1. 读三份文件（主配置 / 命令库 / 界面偏好）；
 *  2. 回主线程把读到的写进页面状态 —— **包括语言档**，它必须在首帧内容画出来之前就位；
 *  3. 把完整配置主动同步一次给注入侧（[publishConfig] 的注释解释了为什么建页就要发）。
 *
 * 第 3 步也放 IO：它内部要 `CommandLibrary.longIsSerial(config)`，那是一次读盘（带指纹缓存，
 * 但缓存没命中时就是真的 open），没必要占着主线程等。
 */
internal suspend fun SettingsPage.loadPageState() {
    val snapshot = withContext(Dispatchers.IO) { readPageLoadSnapshot() }
    applyPageLoad(snapshot)
    // 「装好后没动过任何设置」的状态下，注入侧只能等宿主进程的解锁广播 ——
    // 打开这一页就立刻同步一次，作为兜底（判定在注入侧，见 [publishConfig]）。
    val sent = withContext(Dispatchers.IO) { publishConfig(snapshot.config) }
    if (!sent) XLog.w("建页同步配置：广播没发出去（注入侧还停在上一份）")
}

/**
 * 把 [PageLoadSnapshot] 落进页面状态。**必须在主线程**（写的是 Compose 状态）。
 *
 * 收尾那句 `loaded = true` 是这一整段的开关：它翻 true 的那一刻，[com.lookie.opluskey.ui.screen.Page]
 * 才会把整棵页面树组合出来 —— 所以这里必须**先**把每一项写完，最后再翻它。
 */
internal fun SettingsPage.applyPageLoad(snapshot: PageLoadSnapshot) {
    configState.config.value = snapshot.config
    configState.readOnly = snapshot.configReadOnly
    configState.commands.value = snapshot.commands
    configState.commandsReadOnly = snapshot.commandsReadOnly
    picker.showSystemApps.value = snapshot.showSystemApps
    picker.showUnopenable.value = snapshot.showUnopenable

    // 语言档跟着配置走。放这里而不是 `installI18nBridge()` 那一步：那一步只装订阅、不读配置，
    //  而配置现在是异步读回来的 —— 读回来之后立刻换档，首帧文案才是用户选的那个语言。
    I18n.currentLanguage = AppLanguage.fromCode(snapshot.config.global.language)

    XLog.w(
        "界面状态：显示全部应用=${snapshot.showSystemApps}，显示失效=${snapshot.showUnopenable}",
    )
    XLog.w(
        "命令库：${snapshot.commands.size} 条" +
            "（单条 ${snapshot.commands.count { it.kind == CommandKind.SINGLE }}" +
            " / 多条 ${snapshot.commands.count { it.kind == CommandKind.SERIAL }}）",
    )

    configState.loaded = true
}
