package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.state.CommandsSave

// 配置落盘这一族：唯一的写入口 [write]，以及所有「拨一个开关 / 清空配置」的动作。
//
// 它与另外两族**不是一类**，所以分开放：跨进程那两件（广播 + 重问自检）在 `HostLinkActions.kt`，
// 动宿主页面与进程那几件（露出原版页 / 重启服务）在 `PageControl.kt`。
// 原先三族挤在一个 `ConfigActions.kt` 里，看文件名猜不出里面有什么。

/** 切「振动反馈」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setVibrate(on: Boolean) {
    val cfg = configState.config.value
    write(cfg.copy(global = cfg.global.copy(vibrate = on)), "vibrate=${if (on) 1 else 0}")
}

/**
 * 切「立即触发单击」（双击为无操作时不等双击窗口）—— 行点击与开关共用这一个入口。
 *
 * 语义与判定侧见 `GlobalConfig.instantSingle` / `inject/input/GestureDetector.onKeyUp`。
 */
internal fun SettingsPage.setInstantSingle(on: Boolean) {
    val cfg = configState.config.value
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
    val cfg = configState.config.value
    write(
        cfg.copy(global = cfg.global.copy(toastOnRun = on)),
        "toast_on_run=${if (on) 1 else 0}",
    )
}

/** 切换界面语言。 */
internal fun SettingsPage.setLanguage(code: String) {
    val cfg = configState.config.value
    write(
        cfg.copy(global = cfg.global.copy(language = code)),
        "language=$code",
    )
}

/** 切「使用 ColorOS 接口」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setColorOsApi(on: Boolean) {
    val cfg = configState.config.value
    write(
        cfg.copy(global = cfg.global.copy(colorOsApi = on)),
        "color_os_api=${if (on) 1 else 0}",
    )
}

/** 写 [KeyConfig.enabled] 总闸：关闭后绑定页控件置灰，侧键不再被接管。 */
internal fun SettingsPage.setKeyEnabled(on: Boolean) {
    val cfg = configState.config.value
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
    val cfg = configState.config.value
    write(
        cfg.copy(
            keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(enabled = true)),
            disclaimerAccepted = true,
        ),
        "disclaimer_accepted=true",
    )
}

/** 切「长按连续触发」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setLongRepeat(on: Boolean) {
    val cfg = configState.config.value
    write(
        cfg.copy(
            keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(longRepeat = on)),
        ),
        "long_repeat=${if (on) 1 else 0}",
    )
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
    val wasConfigReadOnly = configState.readOnly
    if (configState.readOnly) {
        XLog.w("清空：配置原处在只读态（读不懂）—— 明说要重来，放行写回")
        configState.readOnly = false
    }
    if (!write(Config.DEFAULT, "reset_all")) {
        // 没写成 ⇒ 把只读位**还原**：它是「手上这份不是用户的真相」的判据，清着不放的话，
        //  之后任何一次普通写入都会以这份默认值落盘，把用户配置抹掉（比原样留着更糟）。
        configState.readOnly = wasConfigReadOnly
        return
    }
    // 命令库是**另一份文件**、另有自己的只读位（[ConfigState.commandsReadOnly]），清空前一起放开；
    //  这里不还原：主配置已经重建，再退回只读态自相矛盾 —— 失败由下面那句 toast 报出去。
    if (configState.commandsReadOnly) {
        XLog.w("清空：命令库原处在只读态（读不懂）—— 明说要重来，放行写回")
        configState.commandsReadOnly = false
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
 * 写配置并同步 [ConfigState.config] 状态；每次写盘把完整配置广播给注入侧，让参数改动立即生效。
 * 落盘失败就什么都不提交（不更新 [ConfigState.config]、不发广播）—— 早先「先更新状态、失败也照发广播」
 * 会三条腿各说各话（界面新值、文件旧值、注入侧按新广播吞键）。
 *
 * @return 是否真的落盘 —— 调用方据此决定要不要报「已…」（失败时这里已报过「保存失败」）。
 */
internal fun SettingsPage.write(
    next: Config,
    what: String,
): Boolean {
    // 只读态：建页那次配置压根没读成功（见 [SettingsPage.configState]），
    //  此时 [SettingsPage.configState] 里那份是**默认值**、不是用户的真相 —— 写回等于把它固化，
    // 会把用户配的绑定 / 开关整份抹掉。一律拒，并明确告诉用户「没保存」。
    if (configState.readOnly) {
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
    configState.config.value = next
    // 记一笔「这一份在路上」：从落盘到注入侧回包之间，两边指纹必然不同（见 `HostLinkState.configPending`）。
    //  不记的话那一小段会被判成「没送到」，整页控件打灰、底部提示换一遍 —— 每拨一个开关都闪一次。
    link.markConfigPending(localConfigTag)
    // 语言跟着配置走：落盘这一下就把新档位落到 [I18n] —— 切「语言」当场换（含导入配置带进来的
    //  那一档、以及「清空配置」退回默认档），不必退出页面再进来。字典本身是组合状态，页面随即重画。
    I18n.currentLanguage = AppLanguage.fromCode(next.global.language)
    // 落盘成功 ≠ 生效：注入侧只认广播（它读不到配置文件）。广播没发出去时注入侧继续按旧配置
    // 吞键，所以这里必须把结果并进返回值 —— 否则界面报「已保存」而实际行为一点没变，
    // 用户会往「模块坏了」的方向排查。
    if (!publishConfig(next)) {
        // 广播都没发出去 ⇒ 注入侧手上确实不是这一份，别把它算成「在途」的乐观态
        link.clearConfigPending()
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
