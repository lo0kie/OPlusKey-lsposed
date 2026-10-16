package com.lookie.opluskey.ui.state

import com.lookie.opluskey.KeyModule
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.wire.ConfigBroadcast
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.screen.CommandsSave
import com.lookie.opluskey.ui.screen.saveCommands

/** 就地露出宿主原版快捷键页面：宿主 Activity 是 singleInstance（真机 aapt 核对过），再 start 只走 onNewIntent、hook 没机会跳过换页，只能藏掉模块页面就地切换、切回靠系统返回拦截。 */
internal fun SettingsPage.openStockPage() {
    XLog.w("菜单：打开自带的快捷键界面（就地露出宿主原版页面）")
    onOpenStock?.invoke()
}

/**
 * **清空配置并按默认重建** —— 主配置与命令库两份文件一起清掉（「按字面意思重置」）。
 *
 *  顺序与「导入配置」同一条纪律：主配置先落盘，**它没成功就一个字节都不再动命令库** ——
 *  否则会留下「命令全没了、绑定还是旧的」这种半份状态，而用户看到的是「已重置」。
 *  命令库清空走 [saveCommands]（它自己会把新配置重播一遍，长按那位串行判定跟着重算）。
 */
internal fun SettingsPage.resetAll() {
    XLog.w("菜单：清空配置（主配置 + 命令库一起按默认重建）")
    // 只读态下这一项是「明说要丢掉重来」的正门：那份「读不懂」的文件本来就该被覆盖。
    //  所以先清掉只读位再写（[write] 的闸是为「不许**悄悄**固化默认值」设的，这是一处有意为之的例外）。
    //  注意：只读态下菜单里这一项现在是**灰的**（`ResetMenu` 只留「默认界面 / 关于模块」，
    // 用户定的），所以这条放行走不到；留着是为了「读不懂」的出口**不能只有一条**
    // （重进页面）——将来若把这行放出来，行为仍然是对的。
    val wasConfigReadOnly = configReadOnly
    if (configReadOnly) {
        XLog.w("清空：配置原处在只读态（读不懂）—— 明说要重来，放行写回")
        configReadOnly = false
    }
    if (!write(Config.DEFAULT, "reset_all")) {
        // 没写成 ⇒ 把只读位**还原**：它是「手上这份不是用户的真相」的判据，清着不放的话，
        //  之后任何一次普通写入都会以这份默认值落盘，把用户配置抹掉（比原样留着更糟）。
        configReadOnly = wasConfigReadOnly
        return
    }
    // 命令库是**另一份文件**、另有自己的只读位（[commandsReadOnly]），清空前一起放开；
    //  这里不还原：主配置已经重建，再退回只读态自相矛盾 —— 失败由下面那句 toast 报出去。
    if (commandsReadOnly) {
        XLog.w("清空：命令库原处在只读态（读不懂）—— 明说要重来，放行写回")
        commandsReadOnly = false
    }
    // 命令库清空：空表就是重建后的状态，文件仍按同一份 JSON 格式写一份（不删文件 —— 删了
    // 下次读取走的是「文件不存在」那条分支，与写一份空表不是同一条路，排查时对不上）。
    val cleared = saveCommands(emptyList(), "reset_all") != CommandsSave.Failed
    XLog.w("清空：主配置已重建，命令库清空=$cleared")
    toast(
        if (cleared) {
            I18n.get(Strings.TOAST_CONFIG_CLEARED, "已清空配置")
        } else {
            I18n.get(Strings.TOAST_CONFIG_CLEAR_FAILED, "配置已清空，命令库清理失败")
        },
    )
}

/**
 * 重启服务：**两层一起重来** —— 宿主主进程（[KeyModule.HOST_PROCESS]，手势劫持装在那一侧）与本页自己
 * 所在的 `:ui` 进程（[KeyModule.PAGE_PROCESS]，界面代码换了新包只有重进才加载得到）。
 * 点了没反应时 `logcat -s OplusKey` 的重启日志就是判据，页面上也会弹一条提示（。
 *
 *  真正动手的是**宿主主进程**（`host/PageRelaunch`）：本页杀了自己就没人能把页面叫回来，
 * 而「从进程外叫」那条路走不通 —— 真机实测闹钟挂 `PendingIntent` 拉起 Activity 会被后台启动策略
 * 拦掉（`ActivityTaskManager: Background activity launch blocked!`，判据见 `docs/device-findings.md`
 * 附十三之五）；宿主的进程自己 `startActivity` 才放行。
 *  所以本页只做三件事：确认宿主主进程在、把那一声请求发出去、然后杀掉自己。
 *
 *  叫「服务」不叫「模块」：这两步都不动 system_server 里那份注入（那个只能整机重启）。
 * 菜单上的文案跟着这条走，别再写回「重启模块」。
 */
internal fun SettingsPage.restartModule() {
    XLog.w("菜单：重启服务 —— 页面进程与宿主主进程各重来一遍")
    tickNow()
    val main = hostMainPids()
    // 只打本包相关的进程（整机那张表一行几 KB，没必要）。真机上报「点了没反应」时，
    // 这三行就是判据：`main` 为空 ⇒ [hostMainPids] 两条路都没摸着。
    val mine =
        context
            .getSystemService(android.app.ActivityManager::class.java)
            ?.runningAppProcesses
            ?.filter { it.processName == KeyModule.PAGE_PACKAGE }
            ?.joinToString(prefix = "[", postfix = "]") { "${it.processName}:${it.pid}" }
            ?: "null"
    XLog.w("重启：宿主主进程 pid=$main（本进程 ${android.os.Process.myPid()}） 本包进程=$mine")
    if (main.isEmpty()) {
        XLog.w("重启：宿主主进程不在，没人能把页面拉回来，放弃")
        // 点了没反应最容易被当成「按钮坏了」—— 这一句把「没找到进程」与「杀掉了」分开。
        //  文案里不提「宿主进程」：那是内部结构（用户界面不需要技术细节），只回答成没成。
        toast(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败，请稍后重试"))
        return
    }
    // 把「把页面拉回来 + 自己重来一遍」整个交给宿主主进程（见 host/PageRelaunch）。
    //  请求发不出去时**不能**杀自己：那样页面没了、也没人再把它叫回来。
    val asked =
        PageWire.send(
            context,
            KeyModule.PAGE_PACKAGE,
            android.content.Intent(KeyModule.ACTION_PAGE_RELAUNCH),
        )
    if (!asked) {
        toast(I18n.get(Strings.TOAST_SERVICE_RESTART_FAILED, "重启失败，请稍后重试"))
        return
    }
    XLog.w("重启：已请宿主主进程稍后拉起页面")
    // 这一条是「菜单那个重启」的反馈：不带它的话，用户只能靠「过一会儿动作生效了没有」猜。
    toast(I18n.get(Strings.TOAST_SERVICE_RESTARTED, "已重启服务"))
    killPageProcess()
}

/**
 * 延迟一点杀掉本页所在的 `:ui` 进程 —— 界面层换了新包之后，只有整个进程重来才会加载新代码。
 *
 *  延迟是留给上面那条提示与那条请求的：提示由系统渲染、请求已经交给宿主主进程，本进程随后死掉
 * 都不影响它们。
 */
private fun SettingsPage.killPageProcess() {
    val pid = android.os.Process.myPid()
    XLog.w("重启：${PAGE_KILL_DELAY_MS}ms 后杀本进程 pid=$pid")
    // 用独立线程而不是主线程 postDelayed：主线程这时可能正忙着收尾这一下点击，睡眠不该占着它；
    // 这个线程活到进程消失为止，不需要回收。
    Thread {
        runCatching { Thread.sleep(PAGE_KILL_DELAY_MS) }
        android.os.Process.killProcess(pid)
    }.start()
}

/** 杀本进程前留给那条提示与请求入队的时间：太短可能还没出门，太长用户会先看到菜单卡在那儿。 */
private const val PAGE_KILL_DELAY_MS = 400L

/** 宿主主进程 pid 列表：runningAppProcesses 与 /proc cmdline 两条路互为兜底，均排除自身 :ui 进程。 */
private fun SettingsPage.hostMainPids(): List<Int> {
    val pkg = KeyModule.PAGE_PACKAGE
    val me = android.os.Process.myPid()
    val out = LinkedHashSet<Int>()

    runCatching {
        context
            .getSystemService(android.app.ActivityManager::class.java)
            ?.runningAppProcesses
            ?.forEach { if (it.processName == pkg && it.pid != me) out += it.pid }
    }.onFailure { XLog.w("重启：runningAppProcesses 失败：${it.javaClass.simpleName}") }

    runCatching {
        java.io
            .File("/proc")
            .listFiles { f ->
                f.isDirectory && f.name.isNotEmpty() && f.name.all { ch -> ch.isDigit() }
            }?.forEach { dir ->
                val cmdline =
                    java.io.File(dir, "cmdline").let { f ->
                        if (f.canRead()) f.readText().trim('\u0000', ' ', '\n') else ""
                    }
                if (cmdline == pkg && dir.name.toInt() != me) out += dir.name.toInt()
            }
    }.onFailure { XLog.w("重启：扫 /proc 失败：${it.javaClass.simpleName}") }

    return out.toList()
}

/** 切「振动反馈」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setVibrate(on: Boolean) {
    val cfg = config.value
    write(cfg.copy(global = cfg.global.copy(vibrate = on)), "vibrate=${if (on) 1 else 0}")
}

/**
 * 切「立即触发单击」（双击为无操作时不等双击窗口）—— 行点击与开关共用这一个入口。
 *
 * 语义与判定侧见 `GlobalConfig.instantSingle` / `inject/input/GestureDetector.onKeyUp`。
 */
internal fun SettingsPage.setInstantSingle(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(global = cfg.global.copy(instantSingle = on)),
        "instant_single=${if (on) 1 else 0}",
    )
}

/**
 * 切「执行时提示」（执行命令时弹一条 Toast）—— 行点击与开关共用这一个入口。
 * 起从每条命令自己的字段提成全局开关（见 `GlobalConfig.toastOnRun`）；
 * 判定侧在 `host/action/CustomCommandRunner`，宿主进程每次执行现读配置，拨完立刻生效。
 */
internal fun SettingsPage.setToastOnRun(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(global = cfg.global.copy(toastOnRun = on)),
        "toast_on_run=${if (on) 1 else 0}",
    )
}

/** 切换界面语言。 */
internal fun SettingsPage.setLanguage(code: String) {
    val cfg = config.value
    write(
        cfg.copy(global = cfg.global.copy(language = code)),
        "language=$code",
    )
}

/** 写 [KeyConfig.enabled] 总闸：关闭后绑定页控件置灰，侧键不再被接管。 */
internal fun SettingsPage.setKeyEnabled(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(enabled = on))),
        "plus_enabled=${if (on) 1 else 0}",
    )
}

/**
 * 用户在「风险提示与免责声明」上点了确认 —— 「记下这一笔」与「打开总闸」**同一次写盘**。
 *
 * 分两次写会留下「确认了却没启用 / 启用了却没记上」的半截状态，而这两样正是那道闸的两半。
 */
internal fun SettingsPage.acceptDisclaimerAndEnable() {
    val cfg = config.value
    write(
        cfg.copy(
            keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(enabled = true)),
            disclaimerAccepted = true,
        ),
        "disclaimer_accepted=true",
    )
}

/**
 * 用户在「自定义命令」那一份风险提示上点了确认 —— 记下这一笔（`commandDisclaimerAccepted`）。
 *
 * 与 [acceptDisclaimerAndEnable] **不是同一份记录**：那个管「打开主开关」，这个只管
 * 「自定义命令」这一页。两者的共同点是「清空配置按 `Config.DEFAULT` 重建 ⇒ 这一位跟着没了 ⇒
 * 下次照样弹」。
 */
internal fun SettingsPage.acceptCommandDisclaimer() {
    write(
        config.value.copy(commandDisclaimerAccepted = true),
        "command_disclaimer_accepted=true",
    )
}

/** 切「长按连续触发」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setLongRepeat(on: Boolean) {
    val cfg = config.value
    write(
        cfg.copy(
            keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(longRepeat = on)),
        ),
        "long_repeat=${if (on) 1 else 0}",
    )
}

/**
 * 写配置并同步 [config] 状态；每次写盘把完整配置广播给注入侧，让参数改动立即生效。
 * 落盘失败就什么都不提交（不更新 [config]、不发广播）—— 早先「先更新状态、失败也照发广播」
 * 会三条腿各说各话（界面新值、文件旧值、注入侧按新广播吞键）。
 *
 * @return 是否真的落盘 —— 调用方据此决定要不要报「已…」（失败时这里已报过「保存失败」）。
 */
internal fun SettingsPage.write(
    next: Config,
    what: String,
): Boolean {
    // 只读态：建页那次配置压根没读成功（见 [SettingsPage.configReadOnly]），
    //  此时 [SettingsPage.config] 里那份是**默认值**、不是用户的真相 —— 写回等于把它固化，
    // 会把用户配的绑定 / 开关整份抹掉。一律拒，并明确告诉用户「没保存」。
    if (configReadOnly) {
        XLog.w("配置处在只读态（这次没读成功），拒绝写回：$what")
        toast(I18n.get(Strings.TOAST_CONFIG_READ_FAILED, "配置读取失败，未保存"))
        return false
    }
    var saved = false
    try {
        saved = ConfigStore.write(next)
        if (saved) {
            XLog.w("设置页写入：$what")
        } else {
            XLog.w("设置页写入失败（所有候选路径都不可写）：$what")
        }
    } catch (e: Throwable) {
        XLog.w("设置页写配置失败：$what", e)
    }
    if (!saved) {
        toast(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"))
        return false
    }
    config.value = next
    // 语言跟着配置走：落盘这一下就把新档位落到 [I18n] —— 切「语言」当场换（含导入配置带进来的
    //  那一档、以及「清空配置」退回默认档），不必退出页面再进来。字典本身是组合状态，页面随即重画。
    I18n.currentLanguage = AppLanguage.fromCode(next.global.language)
    // 落盘成功 ≠ 生效：注入侧只认广播（它读不到配置文件）。广播没发出去时注入侧继续按旧配置
    // 吞键，所以这里必须把结果并进返回值 —— 否则界面报「已保存」而实际行为一点没变，
    // 用户会往「模块坏了」的方向排查。
    if (!publishConfig(next)) {
        XLog.w("设置页写入落盘成功但广播失败，注入侧仍是旧配置：$what")
        //  注意：文案**不指向「重启服务」**：这一刻 `config.value` 刚换成新值、
        //  本地指纹跟着变，而注入侧那份还是旧的 ⇒ `configSideOk=false` ⇒ `linkReady=false`
        //  ⇒ ⋮ 菜单里的「重启服务」**正好被灰掉**，指过去等于让用户点一个点不动的入口。
        //  真正能自愈的是「退出重进」（重进会重发配置并重问自检），所以说这个。
        toast(I18n.get(Strings.TOAST_CONFIG_SAVE_BROADCAST_FAILED, "已保存，但通知注入侧失败，退出重进本页即可"))
        return false
    }
    // 注意：写完必须**再问一次注入侧**：上面 `config.value = next` 一换本地指纹，页面手上那份
    //  `injectStatus` 就还是写之前那份 —— 「设置生效」比的是两边指纹，不重问会一直判「未同步」，
    //  `linkReady` 跟着 false、**整页当场锁死**（写一下之后点哪一格都被拦）。
    resyncInjectStatus()
    return true
}

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
            KeyModule.PAGE_PACKAGE,
            android.content.Intent(KeyModule.ACTION_INJECT_QUERY),
        )
    if (sent) XLog.w("配置已变：向注入侧重问一次自检（把新指纹带回来）")
}

/** 把完整配置广播给注入侧（inject/ConfigChannel，跑在 system_server）。@return 广播是否真的发出去了。 */
internal fun SettingsPage.publishConfig(config: Config): Boolean {
    if (!PageWire.send(context, KeyModule.PAGE_PACKAGE, ConfigBroadcast.intent(config))) return false
    XLog.w("配置广播已发出（enabled=${config.key(KeyId.PLUS).enabled}）")
    return true
}
