package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState

// 配置落盘这一族：唯一的写入口 [write]，以及所有「拨一个开关 / 清空配置」的动作。
//
// 它与另外两族**不是一类**，所以分开放：跨进程那两件（广播 + 重问自检）在 `HostLinkActions.kt`，
// 动宿主页面与进程那几件（露出原版页 / 重启服务）在 `PageControl.kt`。
// 原先三族挤在一个 `ConfigActions.kt` 里，看文件名猜不出里面有什么。

/** 切「振动反馈」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setVibrate(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(vibrate = on)),
        "vibrate=${if (on) 1 else 0}",
    ) { switchNotice(I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"), on) }
}

/**
 * 切「立即触发单击」（双击为无操作时不等双击窗口）—— 行点击与开关共用这一个入口。
 *
 *  这一位是**每颗键各一份**（`KeyConfig.instantSingle`）：写在当前编辑的那颗键上，
 *  语义与判定侧见 `inject/input/GestureDetector.onKeyUp`。
 */
internal fun SettingsPage.setInstantSingle(on: Boolean) {
    val cfg = configState.config.value
    val id = KeyWatchState.editing(cfg)
    writeAndNotice(
        cfg.copy(keys = cfg.keys + (id to cfg.key(id).copy(instantSingle = on))),
        "instant_single=${if (on) 1 else 0} key=${id.id}",
    ) { switchNotice(I18n.get(Strings.SETTING_IGNORE_DOUBLE, "忽视无操作双击手势"), on) }
}

/**
 * 切「执行时提示」（执行命令时弹一条 Toast）—— 行点击与开关共用这一个入口。
 * 起从每条命令自己的字段提成全局开关（见 `GlobalConfig.toastOnRun`）；
 * 判定侧在 `host/action/CustomCommandRunner`，宿主进程每次执行现读配置，拨完立刻生效。
 */
internal fun SettingsPage.setToastOnRun(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(toastOnRun = on)),
        "toast_on_run=${if (on) 1 else 0}",
    ) { switchNotice(I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"), on) }
}

/**
 * 切换界面语言。
 *
 *  回执的名字**必须在写盘之后取**：[write] 里那一下 `I18n.currentLanguage` 换了档，
 *  同一句话才会用新语言打出来（切到英文时显示 “English” 而不是「英语」）。
 *  所以这里给的是 lambda，不是先算好的字符串。
 */
internal fun SettingsPage.setLanguage(code: String) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(language = code)),
        "language=$code",
    ) {
        I18n
            .get(Strings.TOAST_LANGUAGE_SWITCHED, "已切换到「%s」")
            .format(languageLabel(AppLanguage.fromCode(code)))
    }
}

/**
 * 切「前台转小窗时使用动画」—— 行点击与开关共用这一个入口。
 *
 *  判定侧在 `host/action/MiniWindowLauncher`（宿主进程里执行时现读配置，拨完立刻生效）。
 */
internal fun SettingsPage.setMiniWindowAnim(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(miniWindowAnim = on)),
        "mini_window_anim=${if (on) 1 else 0}",
    ) { switchNotice(I18n.get(Strings.HOME_MINI_ANIM_TITLE, "前台应用转小窗时使用动画"), on) }
}

/** 切「使用 ColorOS 接口」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setColorOsApi(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(colorOsApi = on)),
        "color_os_api=${if (on) 1 else 0}",
    ) { switchNotice(I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"), on) }
}

/**
 * 写 [KeyConfig.enabled] 总闸：关闭后绑定页控件置灰，侧键不再被接管。
 *
 *  总闸的回执是**专用两句**（「已停用按键绑定，侧键交还系统」），不套「已关闭「启用按键绑定」」
 *  —— 那读起来是句废话，而且「关掉之后会发生什么」才是这一下真正要交代的事。
 */
internal fun SettingsPage.setKeyEnabled(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(enabled = on))),
        "plus_enabled=${if (on) 1 else 0}",
    ) {
        I18n.get(
            if (on) Strings.TOAST_BINDING_ON else Strings.TOAST_BINDING_OFF,
            if (on) "已启用按键绑定" else "已停用按键绑定，侧键交还系统",
        )
    }
}

/**
 * 用户在「风险提示与免责声明」上点了确认 —— 「记下这一笔」与「打开总闸」**同一次写盘**。
 *
 * 分两次写会留下「确认了却没启用 / 启用了却没记上」的半截状态，而这两样正是那道闸的两半。
 */
internal fun SettingsPage.acceptDisclaimerAndEnable() {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(
            keys = cfg.keys + (KeyId.PLUS to cfg.key(KeyId.PLUS).copy(enabled = true)),
            disclaimerAccepted = true,
        ),
        "disclaimer_accepted=true",
    ) { I18n.get(Strings.TOAST_BINDING_ON, "已启用按键绑定") }
}

/**
 * 切「长按连续触发」—— 行点击与开关共用这一个入口。
 *
 *  写的是**当前在编辑的那颗键**：这一行属于「按键设置」卡，卡里三档（长按阈值 / 连续触发 /
 *  忽视无操作双击）都是那颗键自己的参数。
 */
internal fun SettingsPage.setLongRepeat(on: Boolean) {
    val cfg = configState.config.value
    val id = KeyWatchState.editing(cfg)
    writeAndNotice(
        cfg.copy(
            keys = cfg.keys + (id to cfg.key(id).copy(longRepeat = on)),
        ),
        "long_repeat=${if (on) 1 else 0} key=${id.id}",
    ) { switchNotice(I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"), on) }
}

/**
 * 写配置并同步 [ConfigState.config] 状态；每次写盘把完整配置广播给注入侧，让参数改动立即生效。
 * 落盘失败就什么都不提交（不更新 [ConfigState.config]、不发广播）—— 早先「先更新状态、失败也照发广播」
 * 会三条腿各说各话（界面新值、文件旧值、注入侧按新广播吞键）。
 *
 * @return **三态**（与 [CommandsSave] 同一套）：[CommandsSave.Failed] = 没落盘（什么都没变）；
 *   [CommandsSave.BroadcastFailed] = 落盘成了、但注入侧没收到；[CommandsSave.Ok] = 都成。
 *   调用方**只能**在 [CommandsSave.Failed] 时按「什么都没变」处理 —— 广播失败时文件与本地状态
 *   都已经是新值，照旧值回滚会让两边对不上（命令库与 `cmd:` 引用那条就是这么坏的）。
 */
internal fun SettingsPage.write(
    next: Config,
    what: String,
): CommandsSave {
    // 只读态：建页那次配置压根没读成功（见 [SettingsPage.configState]），
    //  此时 [SettingsPage.configState] 里那份是**默认值**、不是用户的真相 —— 写回等于把它固化，
    // 会把用户配的绑定 / 开关整份抹掉。一律拒，并明确告诉用户「没保存」。
    if (configState.readOnly) {
        XLog.w("配置处在只读态（这次没读成功），拒绝写回：$what")
        hint(I18n.get(Strings.TOAST_CONFIG_READ_FAILED, "配置读取失败，未保存"))
        return CommandsSave.Failed
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
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"))
        return CommandsSave.Failed
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
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_BROADCAST_FAILED, "已保存，但通知注入侧失败，退出重进本页即可"))
        return CommandsSave.BroadcastFailed
    }
    // 注意：写完必须**再问一次注入侧**：上面 `config.value = next` 一换本地指纹，页面手上那份
    //  `injectStatus` 就还是写之前那份 —— 「设置生效」比的是两边指纹，不重问会一直判「未同步」，
    //  `linkReady` 跟着 false、**整页当场锁死**（写一下之后点哪一格都被拦）。
    resyncInjectStatus()
    return CommandsSave.Ok
}
