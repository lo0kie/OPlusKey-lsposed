package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.state.CommandsSave

// 命令库落盘：写盘 → 重播配置 → 重问注入侧。
// 与 `ui/ConfigWrite.kt` 的 [write] 同一档 —— 两族写入口共用 [finishWrite] 那套账；
// 住在 `ui/` 而不是屏里，是为了让「清空配置」那条路不必反过来依赖屏。

/**
 * 写命令库（[ConfigState.commands] 状态同步更新 ⇒ 网格与列表自动重组）。
 *
 * @return 落盘与广播的结果，见 [CommandsSave] —— 失败时这里已经报过提示，调用方只管收手/回滚。
 */
internal fun SettingsPage.saveCommands(
    next: List<Command>,
    what: String,
): CommandsSave {
    // 只读态：建页那次命令库压根没读成功（见 [ConfigState.commandsReadOnly]）—— 手上那份空表不是用户的真相，
    //  写回等于把它固化（用户的命令就没了），而用户看不到任何异常。一律拒，并说明「没保存」。
    if (configState.commandsReadOnly) {
        XLog.w("命令库处在只读态（这次没读成功），拒绝写回：$what")
        hint(I18n.get(Strings.TOAST_COMMANDS_READ_FAILED, "命令库读取失败，未保存"))
        return CommandsSave.Failed
    }
    //  落盘前收一次**名字唯一**：同名的两条命令里，`cmd:<名字>` 只会命中第一条
    //  （执行侧 `CustomCommandRunner` 与图标表 `CommandText` 都是 `firstOrNull`）——
    //  第二条从此**永远执行不到**，而卡片还好好列在界面上。界面自己不许建重名，
    //  但**导入配置**与手改文件都绕得过去，所以在「唯一出口」这里收一次。
    val unique = dedupeByName(next)
    if (unique.size != next.size) {
        XLog.w("命令库有重名（同名只留最后一条）：${next.size - unique.size} 条被丢 —— $what")
    }
    val previous = configState.commands.value
    configState.commands.value = unique
    val saved =
        try {
            CommandLibrary.save(unique)
        } catch (e: Throwable) {
            // 落盘本身抛异常也要走同一条回滚：状态已经改成 next，不回滚就是「界面显示改了、文件没改」
            XLog.w("命令库写盘抛异常（按落盘失败处理，已回滚界面状态）：$what", e)
            false
        }
    if (!saved) {
        configState.commands.value = previous
        XLog.w("命令库写入失败（已回滚界面状态）：$what")
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"))
        return CommandsSave.Failed
    }
    XLog.w("命令库写入：$what")
    // 命令库一改，`ConfigBroadcast.tag` 就可能跟着变（「长按绑的是不是串行命令」读的正是它）：
    //  与 `write()` 共用 [finishWrite] 那套账（标记在途 / 发广播 / 失败撤标记 / 重问注入侧）。
    //  重问那一步对这条路径尤其要紧：指纹里有一位读的正是命令库，不重问会让整页连同 ⋮ 一起灰掉。
    return finishWrite(configState.config.value, "命令库写入")
}

/**
 * 按名字去重（同名**留最后一条**）。
 *
 *  留最后一条而不是第一条：导入的那份覆盖旧的是用户的本意（`addCommand` 的「已覆盖」也是
 *  同一档语义）。顺序按原表保持，界面上的排列不会因为去重而跳。
 */
private fun dedupeByName(commands: List<Command>): List<Command> {
    val seen = HashSet<String>(commands.size)
    val kept = ArrayList<Command>(commands.size)
    for (c in commands.asReversed()) {
        if (seen.add(c.name)) kept += c
    }
    kept.reverse()
    return kept
}
