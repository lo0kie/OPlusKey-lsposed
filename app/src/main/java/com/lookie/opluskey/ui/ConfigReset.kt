package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.config.withIdentityOf
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.state.CommandsSave

// 「重置」这一族单独一个文件：它是**整份文件重建**的一条路（主配置 + 命令库），与 `ConfigWrite.kt`
// 里那些「拨一个开关」不是一类；分开也让那个文件的函数数回到阈值以内。
//
// **两个入口，作用范围不同，不要合并**（用户口径：「重置模块」与「重置配置」是两个独立功能）：
//  · [resetCurrentProfile] —— 「更多选项 → 切换配置」菜单里的「重置配置」：只重建**当前选中的
//    那一份**（内容 + 它的命令库），名字与位次保留；
//  · [resetModule] —— 首页 `⋮` 菜单里的「重置模块」：**这个模块落过的盘全回出厂** ——
//    配置槽（含配置列表里那几份）、命令库、变量、界面状态，一个不留。

/**
 * **重置当前选中的那份配置** —— 内容按 `DEFAULT` 重建、它自己的命令库清空；**名字与位次保留**。
 *
 *  **只动当前那一槽**：`ConfigStore.write` 与 `CommandLibrary.save` 都只认当前配置槽，别的配置
 *  （以及它们的命令库）一个字节都不碰。所以菜单文案是「重置配置」而不是「重置全部」，
 *  确认窗里也点名是哪一份（见 `ProfileResetConfirmDialog`）。
 *
 *  **重置的是内容，不是这份配置的身份**：[Config.profileName] 与 [Config.profileOrder] 跟着
 *  内容走（见那两个字段的注释），直接写回 `DEFAULT` 会把它们一起抹掉 —— 用户看到的是
 *  「重置一下，名字没了、列表里也跳回第一位」。所以只把这两个字段搬过去。
 *
 *  顺序与「导入配置」同一条纪律：主配置先落盘，**它没成功就一个字节都不再动命令库** ——
 *  否则会留下「命令全没了、绑定还是旧的」这种半份状态，而用户看到的是「已重置」。
 *  命令库清空走 [saveCommands]（它自己会把新配置重播一遍，长按那位串行判定跟着重算）。
 */
internal fun SettingsPage.resetCurrentProfile() {
    val what = "重置配置"
    XLog.w("菜单：$what（当前配置 + 命令库按默认重建）")
    // 只读态下这一项是「明说要丢掉重来」的正门：那份「读不懂」的文件本来就该被覆盖。
    //  所以先清掉只读位再写（[write] 的闸是为「不许**悄悄**固化默认值」设的，这是一处有意为之的例外）。
    val wasConfigReadOnly = releaseConfigReadOnly(what)
    //  只搬内容、不搬身份：名字与位次属于这份配置自己（见上面 KDoc 与 `Config.profileOrder`）
    //  **三样都要搬**（走 `withIdentityOf`，与「导入覆盖」同一处推导）：这一句原来只搬了名字与位次，
    //  漏了 id —— 重置「默认」时它靠「名字空 + 位次 0」那条老兜底勉强还算默认，
    //  重置普通配置则把它的 id 抹成空串（下一次写盘才由槽位补一个，中途身份是断的）。
    val current = configState.config.value
    val rebuilt =
        write(
            Config.DEFAULT.withIdentityOf(current),
            "reset_current",
        )
    if (rebuilt == CommandsSave.Failed) {
        // 没写成 ⇒ 把只读位**还原**：它是「手上这份不是用户的真相」的判据，清着不放的话，
        //  之后任何一次普通写入都会以这份默认值落盘，把用户配置抹掉（比原样留着更糟）。
        configState.readOnly = wasConfigReadOnly
        return
    }
    // 命令库是**另一份文件**、另有自己的只读位（[ConfigState.commandsReadOnly]），清空前一起放开；
    //  写失败时**要还原**（见下）：清着不放的话，之后任何一次普通写入都会拿这份空表落盘，
    //  把用户原来那份（只是暂时读不懂的）命令库整份抹掉 —— 与主配置那一支同一条纪律。
    val wasCommandsReadOnly = releaseCommandsReadOnly(what)
    // 命令库清空：空表就是重建后的状态，文件仍按同一份 JSON 格式写一份（不删文件 —— 删了
    // 下次读取走的是「文件不存在」那条分支，与写一份空表不是同一条路，排查时对不上）。
    val cleared = saveCommands(emptyList(), "reset_current")
    if (cleared == CommandsSave.Failed && wasCommandsReadOnly) {
        configState.commandsReadOnly = true
        XLog.w("$what：命令库没写成，只读位已还原（避免空表被后续写入固化到盘）")
    }
    XLog.w("$what：主配置已重建=$rebuilt，命令库清空=$cleared")
    reportReset(what, moduleReset = false, rebuilt, cleared)
}

/**
 * **重置整个模块** —— 这个模块落过的盘**全回出厂**：配置槽（含配置列表里那几份）、命令库
 * （含每份配置各自的）、变量表、界面状态，一个不留。
 *
 *  为什么是「整盘清空」而不是逐个槽推演「删这份要连带删哪几份」：那是「删配置」那条路的账，
 *  而这里要的只是回出厂 —— 当前那份随后会重建，其余全都该消失（用户口径「重置所有的模块配置」）。
 *  按槽逐个删反而会漏：配置列表、每份配置的命令库 / 变量、界面状态各有一套命名，漏一个就是
 *  一份谁也认不出的孤儿文件。
 *
 *  **顺序**：先整盘删，再写回一份出厂配置 —— `write` 那一步同时把新配置广播给注入侧、刷新页面
 *  状态；反过来（先写后删）会把刚写的那份一起删掉。
 */
internal fun SettingsPage.resetModule() {
    val what = "重置模块"
    XLog.w("菜单：$what（模块全部落盘数据回出厂：配置槽 / 命令库 / 变量 / 界面状态）")
    // 只读态下这一项同样是「明说要丢掉重来」的正门（见 [resetCurrentProfile] 那段说明）
    val wasConfigReadOnly = releaseConfigReadOnly(what)
    val wasCommandsReadOnly = releaseCommandsReadOnly(what)
    val wiped = ConfigStore.files().deleteAll()
    if (!wiped) XLog.w("$what：有文件没删掉（继续写回出厂配置，删不掉的留在盘上）")
    //  出厂配置：**连名字与位次一起回默认** —— 配置列表也要回到「只有默认这一份」
    val rebuilt = write(Config.DEFAULT, "reset_module")
    if (rebuilt == CommandsSave.Failed) {
        // 与另一支同一条纪律：没写成 ⇒ 只读位还原（否则后续任何一次普通写入都会拿默认值落盘）
        configState.readOnly = wasConfigReadOnly
        configState.commandsReadOnly = wasCommandsReadOnly
        return
    }
    //  命令表要跟着清空：文件已经没了，但**内存里那份**还在（界面与执行侧都还看得见旧命令）。
    //  写一份空表而不是不管它 —— 与 [resetCurrentProfile] 同一条理由（写一份空表，
    //  不让下一次读取走「文件不存在」那条分支）。
    val cleared = saveCommands(emptyList(), "reset_module")
    if (cleared == CommandsSave.Failed && wasCommandsReadOnly) configState.commandsReadOnly = true
    XLog.w("$what：整盘清空=$wiped，主配置已重建=$rebuilt，命令库清空=$cleared")
    reportReset(what, moduleReset = true, rebuilt, cleared)
}

/**
 * 主配置那一份的只读位放行 —— **返回原来的值**，没写成时调用方要按它还原。
 *
 *  抽出来只为把 [rebuildCurrent] 的分支数压在 detekt 的 `CyclomaticComplexMethod` 之下：
 *  「清着不放会比原样留着更糟」那套理由写在上面那段调用处，这里只做一件事。
 */
private fun SettingsPage.releaseConfigReadOnly(what: String): Boolean {
    val was = configState.readOnly
    if (was) {
        XLog.w("$what：配置原处在只读态（读不懂）—— 明说要重来，放行写回")
        configState.readOnly = false
    }
    return was
}

/** 命令库那一份的同一条（与 [releaseConfigReadOnly] 成对：两份文件各有自己的只读位）。 */
private fun SettingsPage.releaseCommandsReadOnly(what: String): Boolean {
    val was = configState.commandsReadOnly
    if (was) {
        XLog.w("$what：命令库原处在只读态（读不懂）—— 明说要重来，放行写回")
        configState.commandsReadOnly = false
    }
    return was
}

/**
 * 收尾的那一句回执 —— 三态各报各的。
 *
 *  `BroadcastFailed` 那两位已经由 [write] / [saveCommands] 报过「已保存，但通知注入侧失败」，
 *  这里再补一句「已重置」会把它顶掉（底部只有一行回执），所以这一档故意不出声。
 */
private fun SettingsPage.reportReset(
    what: String,
    moduleReset: Boolean,
    rebuilt: CommandsSave,
    cleared: CommandsSave,
) {
    when {
        rebuilt == CommandsSave.BroadcastFailed || cleared == CommandsSave.BroadcastFailed -> {
            XLog.w("$what：有一半没通知到注入侧，保留写盘那条警告（不再补「已重置」）")
        }

        cleared == CommandsSave.Failed -> {
            hint(I18n.get(Strings.TOAST_CONFIG_CLEAR_FAILED, "配置已重置，命令库清空失败"))
        }

        else -> {
            hint(
                if (moduleReset) {
                    I18n.get(Strings.TOAST_MODULE_RESET, "已重置模块")
                } else {
                    I18n.get(Strings.TOAST_CONFIG_CLEARED, "已重置配置")
                },
            )
        }
    }
}
