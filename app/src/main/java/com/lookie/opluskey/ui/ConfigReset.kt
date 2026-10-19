package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState

// 「清空配置」单独一个文件：它是**两份文件一起重建**的一条路（主配置 + 命令库），
// 与 `ConfigWrite.kt` 里那些「拨一个开关」不是一类；分开也让那个文件的函数数回到阈值以内。

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
    if (write(Config.DEFAULT, "reset_all") == CommandsSave.Failed) {
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
    hint(
        if (cleared) {
            I18n.get(Strings.TOAST_CONFIG_CLEARED, "已清空配置")
        } else {
            I18n.get(Strings.TOAST_CONFIG_CLEAR_FAILED, "配置已清空，命令库清理失败")
        },
    )
}

/**
 * **重置当前按键的绑定** —— 三个手势与按键设置全部回到默认，**主开关不动**。
 *
 *  主开关（`enabled`）说的是「这颗键归不归模块管」，不是一条绑定；把它一起关掉的话，
 *  用户点「重置绑定」会顺手把整颗键停用，看起来像模块坏了。
 *
 *  目前只有侧键有配置段（`KeyId` 还是枚举），所以这里写死的也是侧键。
 *
 *  与 [resetAll] 分开：那条是「两份文件一起按默认重建」，这条只动一个键的那一段。
 */
internal fun SettingsPage.resetKeyBindings() {
    val cfg = configState.config.value
    // 作用于**当前在编辑的那颗键**（自定义键也有自己的配置段）。`enabled` 保留：
    //  重置说的是「绑定回到默认」，不是「把这颗键关掉」。
    val id = KeyWatchState.editing(cfg)
    val reset = KeyConfig.defaultFor(id).copy(enabled = cfg.key(id).enabled)
    // 已经是默认值 ⇒ 不落盘、也不报「已重置」：什么都没变，报一句只会让人以为自己刚覆盖了什么。
    //  （菜单里这一项对侧键也一直可点，误触是常事。）
    if (cfg.key(id) == reset) {
        XLog.w("重置绑定：${id.label} 已经是默认值，不用写")
        return
    }
    if (write(cfg.copy(keys = cfg.keys + (id to reset)), "reset_key=${id.id}") == CommandsSave.Failed) return
    hint(I18n.get(Strings.TOAST_KEY_RESET, "已重置当前按键的绑定"))
}
