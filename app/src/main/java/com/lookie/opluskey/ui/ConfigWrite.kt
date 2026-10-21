package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.state.CommandsSave

// 配置落盘这一族：唯一的写入口 [write]，以及所有**全局**开关的动作。
//
// 「**这一颗键**的参数」那半在 `ui/KeyParamsWrite.kt`（长按连续触发 / 忽视无操作双击）：
// 两者写的不是同一段配置（`global` 对 `keys[id]`），搬之前混在一个文件里，
// 加一条全局开关就要跟「按键设置」那两条挤在同一个文件数函数个数。
//
// 「页面大小」那两颗（跟随系统显示大小 / 页面缩放）在 `ui/DisplayWrite.kt` —— 它们是一件事的两半，
// 与这里这些互不相干的开关不同类。
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
 * 写**振动强度**（三档，见 [GlobalConfig.VIBRATE_STRENGTH_RANGE]）。
 *
 *  只改强度、不动 [GlobalConfig.vibrate]：那一行的开关管「振不振」，这一行管「振多重」，
 *  两层各自独立（关掉振动时这一行整体不出现，见 `HomeActionsCard`）。
 *
 *  返回「写盘成没成」—— 滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 */
internal fun SettingsPage.setVibrateStrength(level: Int): Boolean {
    val cfg = configState.config.value
    val next = level.coerceIn(GlobalConfig.VIBRATE_STRENGTH_RANGE)
    return write(
        cfg.copy(global = cfg.global.copy(vibrateStrength = next)),
        "vibrate_strength=$next",
    ) != CommandsSave.Failed
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
 *
 *  主语（「语言」）也一起在写盘之后取：切到英文之后那句应当是 “Language switched to “English””，
 *  不是「语言 switched to…」。回执带主语的理由见 `switchToNotice`。
 */
internal fun SettingsPage.setLanguage(code: String) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(language = code)),
        "language=$code",
    ) {
        switchToNotice(
            I18n.get(Strings.MORE_LANGUAGE, "语言"),
            languageLabel(AppLanguage.fromCode(code)),
        )
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
 * 写**模块总闸**（[GlobalConfig.enabled]）：关闭后绑定页控件置灰，一颗键都不再被接管。
 *
 *  它**不碰**任何一颗键自己的 `enabled`：总闸与逐键是两层，关掉总闸再打开时，
 *  用户之前在 ⋮ 菜单里停用过的那颗键应该还是停用的。
 *
 * **两档都发，而且用同一条**（同一个位 [MODULE_ENABLED_SLOT]）：关 → 开 → 关，
 *  屏幕上始终是那一条在改字（「已关闭「按键绑定」」↔「已开启「按键绑定」」）。
 *
 *  以前「关」这一档刻意不发，是因为常驻那条「按键绑定已关闭，侧键交还系统」会同一时刻出现，
 *  两条说同一件事；发吧是两条、不发吧又像是拨了没反应。**那条常驻提示已经整条删掉**
 *  （见 `HintQueue` 的类注释），于是两档都发、各出一条，正好。
 *
 * 写盘失败仍由 [write] 自己报，拨开关不会没有反馈。
 */
internal fun SettingsPage.setModuleEnabled(on: Boolean) {
    val cfg = configState.config.value
    val saved = write(cfg.copy(global = cfg.global.copy(enabled = on)), "module_enabled=${if (on) 1 else 0}")
    if (saved != CommandsSave.Ok) return
    hint(moduleEnabledNotice(on), slot = MODULE_ENABLED_SLOT)
}

/** 总闸那一句 —— 开 / 关两档同一个句式，与其它开关的回执同一族（[switchNotice]）。 */
private fun moduleEnabledNotice(on: Boolean): String = switchNotice(I18n.get(Strings.SETTING_KEY_BINDING, "按键绑定"), on)

/**
 * 用户在「风险提示与免责声明」上点了确认 —— 「记下这一笔」与「打开总闸」**同一次写盘**。
 *
 * 分两次写会留下「确认了却没启用 / 启用了却没记上」的半截状态，而这两样正是那道闸的两半。
 */
internal fun SettingsPage.acceptDisclaimerAndEnable() {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(
            global = cfg.global.copy(enabled = true),
            disclaimerAccepted = true,
        ),
        "disclaimer_accepted=true",
        //  它做的也是「开总闸」，回执与 [setModuleEnabled] 是同一句话 ⇒ 用**同一个位**
        slot = MODULE_ENABLED_SLOT,
    ) { moduleEnabledNotice(on = true) }
}

/**
 * 「主开关」这个提示位的名字 —— 拨开关（[setModuleEnabled]）与首次启用（[acceptDisclaimerAndEnable]）
 * 共用：两处说的是同一件事，屏幕上就不该出两条。
 */
private const val MODULE_ENABLED_SLOT = "module_enabled"

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
