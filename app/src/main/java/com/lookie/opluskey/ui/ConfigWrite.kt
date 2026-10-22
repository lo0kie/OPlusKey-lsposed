package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.state.CommandsSave

// 配置落盘这一族：唯一的写入口 [write]，与落盘之后的收尾 [finishWrite]。
//
// 本文件只管**机制**（只读态 / 落盘 / 广播 / 重问注入侧），不含任何具体开关：
//   · 「改哪颗全局开关」在 `ui/GlobalWrite.kt`；
//   · 「这一颗键的参数」在 `ui/KeyParamsWrite.kt`（写 `keys[id]`）；
//   · 「页面大小」那两颗在 `ui/DisplayWrite.kt`。
//
// 与另外两族分开放：跨进程那两件（广播 + 重问自检）在 `HostLinkActions.kt`，
// 动宿主页面与进程那几件（露出原版页 / 重启服务）在 `PageControl.kt`。

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
    // 语言跟着配置走：落盘这一下就把新档位落到 [I18n] —— 切「语言」当场换（含导入配置带进来的
    //  那一档、以及「清空配置」退回默认档），不必退出页面再进来。字典本身是组合状态，页面随即重画。
    I18n.currentLanguage = AppLanguage.fromCode(next.global.language)
    return finishWrite(next, "设置页写入")
}

/**
 * 落盘成功之后的三件收尾：标记「在途」→ 发广播 → 失败撤标记并提示 → 重问注入侧。
 *
 *  两条写入口（[write] 的整份配置、`saveCommands` 的命令库）共用这一段 —— 两边的账必须一样：
 *  「什么时候算未同步、什么时候该重问」只要各写一份，就会演进成两套口径。
 *
 * ## 为什么必须标记「在途」
 *
 *  从落盘到注入侧回包之间，两边指纹必然不同（见 `HostLinkState.configPending`）。不记的话
 *  那一小段会被判成「没送到」，整页控件打灰、底部提示换一遍 —— 每拨一个开关都闪一次。
 *
 * ## 为什么广播失败要并进返回值
 *
 *  注入侧只认广播（它读不到配置文件）。广播没发出去时它继续按旧配置吞键 —— 界面报「已保存」
 *  而实际行为一点没变，用户会往「模块坏了」的方向排查。
 *
 * ## 为什么写完必须再问一次注入侧
 *
 *  `config.value` 一换本地指纹，页面手上那份 `injectStatus` 就还是写之前那份；
 *  「设置生效」比的是两边指纹，不重问会一直判「未同步」⇒ `linkReady=false` ⇒ **整页当场锁死**。
 *
 * @param subject 日志主语（`设置页写入` / `命令库写入`）
 * @return [CommandsSave.Ok] / [CommandsSave.BroadcastFailed]
 */
internal fun SettingsPage.finishWrite(
    next: Config,
    subject: String,
): CommandsSave {
    link.markConfigPending(localConfigTag)
    if (!publishConfig(next)) {
        // 广播都没发出去 ⇒ 注入侧手上确实不是这一份，别把它算成「在途」的乐观态
        link.clearConfigPending()
        XLog.w("$subject：落盘成功但广播失败，注入侧仍是旧配置")
        //  注意：文案**不指向「重启服务」**：这一刻 `config.value` 刚换成新值、
        //  本地指纹跟着变，而注入侧那份还是旧的 ⇒ `configSideOk=false` ⇒ `linkReady=false`
        //  ⇒ ⋮ 菜单里的「重启服务」**正好被灰掉**，指过去等于让用户点一个点不动的入口。
        //  真正能自愈的是「退出重进」（重进会重发配置并重问自检），所以说这个。
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_BROADCAST_FAILED, "已保存，但通知注入侧失败，退出重进本页即可"))
        return CommandsSave.BroadcastFailed
    }
    resyncInjectStatus()
    return CommandsSave.Ok
}
