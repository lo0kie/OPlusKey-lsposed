package com.lookie.opluskey.ui.write

import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.wire.primeLocalTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 命令库落盘：写盘 → 重播配置 → 重问注入侧。
// 两条入口共用同一套账：[saveCommands]（落盘也在调用线程上）与 [saveCommandsOnIo]（落盘在 IO 上）。
// 与 `ui/write/ConfigWrite.kt` 的 [write] 同一档 —— 两族写入口共用 [finishWrite] 那套账；
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
    val unique = commandsToSave(next, what) ?: return CommandsSave.Failed
    return applySavedCommands(unique, what, persistCommands(unique, what))
}

/**
 * 与 [saveCommands] 同一套闸、同一份收尾，只把**落盘那一步**放到 IO 上。
 *
 *  给「整份重建」的入口用（重置配置 / 重置模块把命令库清空）：`CommandLibrary.save` 里要读一次
 *  现有文件留 `.prev` 快照，再整份序列化写下去，不能占主线程。页面状态与提示仍回主线程做。
 */
internal suspend fun SettingsPage.saveCommandsOnIo(
    next: List<Command>,
    what: String,
): CommandsSave {
    val unique = commandsToSave(next, what) ?: return CommandsSave.Failed
    val saved = withContext(Dispatchers.IO) { persistCommands(unique, what) }
    return applySavedCommands(unique, what, saved)
}

/**
 * 落盘之前的闸：只读态拒写 + 名字唯一 —— 返回 `null` = 这一次不许写。
 *
 *  只读态：建页那次命令库压根没读成功（见 [ConfigState.commandsReadOnly]）—— 手上那份空表不是
 *  用户的真相，写回等于把它固化（用户的命令就没了），而用户看不到任何异常。
 *
 *  名字唯一：同名的两条命令里，`cmd:<名字>` 只会命中第一条（执行侧 `CustomCommandRunner` 与
 *  图标表 `CommandText` 都是 `firstOrNull`）—— 第二条从此**永远执行不到**，而卡片还好好列在界面上。
 *  界面自己不许建重名，但**导入配置**与手改文件都绕得过去，所以在「唯一出口」这里收一次。
 */
private fun SettingsPage.commandsToSave(
    next: List<Command>,
    what: String,
): List<Command>? {
    if (configState.commandsReadOnly) {
        XLog.w("命令库处在只读态（这次没读成功），拒绝写回：$what")
        hint(I18n.get(Strings.TOAST_COMMANDS_READ_FAILED, "命令库读取失败，未保存"))
        return null
    }
    val unique = dedupeByName(next)
    if (unique.size != next.size) {
        XLog.w("命令库有重名（同名只留最后一条）：${next.size - unique.size} 条被丢 —— $what")
    }
    return unique
}

/** 落盘这一步本身 —— 纯文件 IO（`.prev` 快照 + 原子写），不碰页面状态。 */
private fun persistCommands(
    unique: List<Command>,
    what: String,
): Boolean {
    val saved =
        try {
            CommandLibrary.save(unique)
        } catch (e: Throwable) {
            XLog.w("命令库写盘抛异常（按落盘失败处理）：$what", e)
            false
        }
    if (saved) XLog.w("命令库写入：$what")
    return saved
}

/**
 * 落盘之后提交页面状态 —— [saveCommands] 与 [saveCommandsOnIo] 共用这一份。
 *
 *  没写成时页面状态一直没动过（与文件同值），只报一句并收手。
 */
private fun SettingsPage.applySavedCommands(
    unique: List<Command>,
    what: String,
    saved: Boolean,
): CommandsSave {
    if (!saved) {
        XLog.w("命令库写入失败（界面状态与文件都没变）：$what")
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_FAILED, "保存失败"))
        return CommandsSave.Failed
    }
    configState.commands.value = unique
    //  写完之后 `localConfigTag` 的缓存必然失效（配置实例没换、命令库那一份换了），而 `finishWrite`
    //  第一件事就是读它。这里先把指纹算好：这条路跑在主线程上，不预算的话它会在主线程现算一次。
    primeLocalTag(configState.config.value, unique)
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
 *
 *  `internal` 而不是 `private`：**导入配置**那条路现在把命令库写进新的一份配置（不经过
 *  [saveCommands]），同一条闸要自己再过一遍 —— 同名的两条里 `cmd:<名字>` 只会命中第一条。
 */
internal fun dedupeByName(commands: List<Command>): List<Command> {
    val seen = HashSet<String>(commands.size)
    val kept = ArrayList<Command>(commands.size)
    for (c in commands.asReversed()) {
        if (seen.add(c.name)) kept += c
    }
    kept.reverse()
    return kept
}
