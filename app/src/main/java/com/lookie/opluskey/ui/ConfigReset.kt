package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.ConfigProfiles
import com.lookie.opluskey.core.config.ProfileVars
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.state.CommandsSave

// 「重置」这一族单独一个文件：它是**两份文件一起重建**的一条路（当前那份的主配置 + 它的命令库），
// 与 `ConfigWrite.kt` 里那些「拨一个开关」不是一类；分开也让那个文件的函数数回到阈值以内。
//
// **两个入口，作用范围不同，不要合并**（用户口径：「重置模块」与「重置配置」是两个独立功能）：
//  · [resetCurrentProfile] —— 「更多选项 → 切换配置」菜单里的「重置配置」：只重建**当前选中的
//    那一份**（内容 + 它的命令库），名字与位次保留；
//  · [resetModule] —— 首页 `⋮` 菜单里的「重置模块」：在它之上再清掉**变量表**，把整个模块的
//    运行数据回出厂。
//  两者都**只动当前那一槽**：别的配置（config-2 / 3）与它们的命令库、变量一个字节都不碰。

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
internal fun SettingsPage.resetCurrentProfile() = rebuildCurrent(clearVars = false)

/**
 * **重置整个模块** —— 与 [resetCurrentProfile] 同一套重建，另外把**变量表**也清空。
 *
 *  差的就是变量表：配置与命令库说的是「这台机器怎么按键」，变量说的是「命令跑过几轮、攒了什么」——
 *  后者是运行数据，跟着「模块」走，不属于「这一份配置」。两个入口分开正是为了这件事：
 *  只想把这套绑定推倒重来的人，不该顺手把自己攒的计数清零。
 *
 *  **不碰其它配置**：与 [resetCurrentProfile] 同一条边界，这里也不做「删掉全部配置槽」那种事
 *  —— 那不可逆，而「重置模块」指的是当前这套运行数据。
 */
internal fun SettingsPage.resetModule() = rebuildCurrent(clearVars = true)

/**
 * 两个入口共用的一条路。
 *
 * @param clearVars 要不要连变量表一起清（见 [resetModule]）
 */
private fun SettingsPage.rebuildCurrent(clearVars: Boolean) {
    val what = if (clearVars) "重置模块" else "重置配置"
    val tag = if (clearVars) "reset_module" else "reset_current"
    XLog.w("菜单：$what（当前配置 + 命令库${if (clearVars) " + 变量" else ""}按默认重建）")
    // 只读态下这一项是「明说要丢掉重来」的正门：那份「读不懂」的文件本来就该被覆盖。
    //  所以先清掉只读位再写（[write] 的闸是为「不许**悄悄**固化默认值」设的，这是一处有意为之的例外）。
    //  注意：只读态下菜单里这一项现在是**灰的**（首页菜单与「切换配置」菜单同一个闸，用户定的），
    //  所以这条放行走不到；留着是为了「读不懂」的出口**不能只有一条**（重进页面）——
    //  将来若把这行放出来，行为仍然是对的。
    val wasConfigReadOnly = releaseConfigReadOnly(what)
    //  只搬内容、不搬身份：名字与位次属于这份配置自己（见上面 KDoc 与 `Config.profileOrder`）
    val current = configState.config.value
    val rebuilt =
        write(
            Config.DEFAULT.copy(profileName = current.profileName, profileOrder = current.profileOrder),
            tag,
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
    val cleared = saveCommands(emptyList(), tag)
    if (cleared == CommandsSave.Failed && wasCommandsReadOnly) {
        configState.commandsReadOnly = true
        XLog.w("$what：命令库没写成，只读位已还原（避免空表被后续写入固化到盘）")
    }
    //  变量表跟着配置槽走（见 `ProfileVars`）：只清**当前那一份**的，别的配置那几张照旧
    val varsCleared = !clearVars || ProfileVars.save(ConfigProfiles.CURRENT_SLOT, emptyList())
    if (clearVars) XLog.w("$what：变量表清空=$varsCleared")
    XLog.w("$what：主配置已重建=$rebuilt，命令库清空=$cleared")
    reportReset(what, clearVars, varsCleared, rebuilt, cleared)
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
    clearVars: Boolean,
    varsCleared: Boolean,
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

        //  **变量表没清成也要说**：主配置与命令库已经重置了，但上一套计数还在盘上 ——
        //  报「已重置」的话，用户下一次执行 `+= 1` 会接着旧值往上加，还以为已经归零。
        clearVars && !varsCleared -> {
            hint(I18n.get(Strings.TOAST_CONFIG_CLEAR_VARS_FAILED, "配置已重置，变量表清空失败"))
        }

        else -> {
            hint(
                if (clearVars) {
                    I18n.get(Strings.TOAST_MODULE_RESET, "已重置模块")
                } else {
                    I18n.get(Strings.TOAST_CONFIG_CLEARED, "已重置配置")
                },
            )
        }
    }
}
