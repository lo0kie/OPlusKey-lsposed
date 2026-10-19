package com.lookie.opluskey.ui

import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.state.CommandsSave

// 命令库落盘 —— 从 `ui/screen/CommandScreen` 搬过来（行为与注释原样）。
// 它与 [StepActions] 同一档：干的是「写盘 + 重播配置 + 重问注入侧」，属 `ui/state`。
// 原来它住在屏里，于是 `ui/state/ConfigWrite`（清空配置那条路）反过来要依赖屏
// （见 `docs/decoupling.md` 动作 4）。
// /

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
    // 命令库一改，`ConfigBroadcast.tag` 就可能跟着变（「长按绑的是不是串行命令」读的正是它）——
    //  与 `write()` 同一条账：先记成「在路上」，否则这一小段会被判成未同步，整页闪一下。
    link.markConfigPending(localConfigTag)
    // 命令库变了必须重发配置广播：ConfigFields.LONG_SERIAL_EXTRA（长按绑的是不是串行命令）是发广播
    // 那一刻现算的，不重发的话注入侧还拿着旧判定 —— cmd:X 在单条 / 多条之间切换后，
    // 「连续触发」的闸门会一直错下去。
    if (!publishConfig(configState.config.value)) {
        // 广播没发出去 ⇒ 注入侧手上确实不是这一份，撤掉上面那笔乐观态
        link.clearConfigPending()
        XLog.w("命令库写入落盘成功但广播失败，注入侧仍是旧配置：$what")
        // 与 [write] 同一句口径：这一刻 config.value 刚换成新值、本地指纹跟着变，
        //  而注入侧那份还是旧的 ⇒ 页面判「未同步」、⋮ 里那一项会被灰掉，
        //  真正能自愈的是「退出重进」。
        hint(I18n.get(Strings.TOAST_CONFIG_SAVE_BROADCAST_FAILED, "已保存，但通知注入侧失败，退出重进本页即可"))
        return CommandsSave.BroadcastFailed
    }
    //  发完**必须再问一次注入侧**：指纹（`ConfigBroadcast.tag`）
    //  里有一位是「长按绑的是不是串行命令」，它读的正是命令库（见 `ConfigBroadcast.intent`），
    //  所以命令库一改、指纹就可能跟着变：注入侧收到了新的那份，页面手上 `injectStatus` 却还是
    //  上一次问来的旧指纹 ⇒ `configSideOk=false` ⇒ `linkReady=false` ⇒ **整页连同 ⋮ 的重启/清空
    //  一起灰掉**（只能退出重进才自愈）。`write()` 那条路早就有这一句（见 `resyncInjectStatus`）。
    resyncInjectStatus()
    return CommandsSave.Ok
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
