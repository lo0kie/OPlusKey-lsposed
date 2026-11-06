package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.RomProfile
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SectionTitleClick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.actionOf

/**
 * 「已保存的命令」那一块的小标题 —— **两页共用**（按键绑定 / 组合键编辑）。
 *
 *  有命令时可点：进「自定义命令」页去管命令库，落点是**命令列表**那一格（不是上次停着的表单页：
 *  留在列表页看得出全部命令，落在表单页只能看到一条草稿），草稿原样留着。
 *  **一条命令都没有时不接点击**：那一格里已经是「去创建自定义命令」（它会先 `reset()`，
 *  是「新增」那条路），同一趟去处摆两个入口，点哪个都行反而让人以为标题是另一件事。
 *
 *  抽成一支有两个理由：两页的判据必须同一份（组合键那一页原先压根没接点击）；以及
 *  两边各写一遍的话，detekt 的长度上限会先替我们报警。
 */
@Composable
internal fun SettingsPage.SavedCommandsTitle() {
    val hasCommands = configState.commands.value.isNotEmpty()
    SectionTitle(
        text = I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"),
        click =
            if (hasCommands) {
                SectionTitleClick.Jump {
                    commandDraft.openListPage = true
                    navTo(Screen.COMMANDS)
                }
            } else {
                null
            },
    )
}

/**
 * 「按键绑定」页的**纵向顺序** —— 按键设置 → 内置动作网格 → 已保存的命令 → 底部留白 → 入口浮层。
 *
 *  拆出来只是为了让 `BindingList` 只剩「参数 + 三条 effect」：这一页的纵向顺序里有一堆
 *  「哪一段之间该给间距」的细节（标题紧贴内容、内容之间才给档），和焦点账本混在一起时
 *  改一处间距得先把整个 666 行的函数读一遍。
 */
@Composable
internal fun SettingsPage.BindingGroups(
    g: Gesture,
    enabled: Boolean,
    st: BindingGridState,
    onCreateCommand: () -> Unit,
) {
    val current = actionOf(configState.config.value, g)

    // —— 按键设置：**在网格上面** ——
    //  它属于**这一档手势本身**的行为，排在动作网格之前：先看到「这一档怎么判、有什么可调」，
    //  再挑「绑什么」。**三档各有一张**，各写自己那一档：
    //   · 单击档 → `BindingSettingsCard`（单击延迟）
    //   · 双击档 → `InstantSingleCard`（忽视无操作双击手势）
    //   · 长按档 → `LongRepeatCard`（长按延迟 / 连续触发及其间隔）
    //
    //  同一张卡里不放别的档的设置项：用户找它们时带着「我在调哪一档」这个前提，
    //  「双击窗口」也只有一个值，两档各放一个入口就是同一个配置项的第二个入口。
    //
    //  **块与块之间**的间距由小标题自带；标题要不要、放哪儿是各块自己的事。
    Column {
        if (g == Gesture.SINGLE) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            BindingSettingsCard(enabled)
        }

        if (g == Gesture.DOUBLE) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            InstantSingleCard(enabled)
        }

        // 「长按多久算长按」与「长按连续触发 / 触发间隔」同属长按这一档的行为，摆在同一张卡里。
        if (g == Gesture.LONG) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            LongRepeatCard(
                blockedReason =
                    if (enabled) {
                        null
                    } else {
                        // 这里是**主开关自己关着**，不是「模块没生效」：`STATUS_DISABLED` 的正文是
                        //  「确认作用域含 system，换包后需重启」—— 拿它来报这一档，等于把用户赶去
                        //  LSPosed 里翻一圈，而他要做的只是把开关拨回来。
                        I18n.get(Strings.STATUS_KEY_DISABLED, "按键绑定已关闭，侧键交还系统")
                    },
                // 长按动作**自己**不支持连发（多条命令 / 灵感语音）—— 只置灰「连续触发」那一族，
                //  不连坐时序那两行与长按延迟（见 [LongPressSpec.repeatBlockedReason]）。
                repeatBlockedReason = longActionNoRepeatReason(current),
                // 「长按延迟」那行只看主开关（与「是不是多条命令」无关）
                masterEnabled = enabled,
            )
        }

        BuiltInGrid(st, enabled)

        // 「自定义命令」这一段**始终在**：没有命令时给一行「去创建自定义命令」。
        SavedCommandsTitle()
        CustomCommandsGrid(st, enabled, onCreateCommand)
    }

    //  页底留白**不在这里**：这一页原先在末尾补 32 + 12 两个 Spacer（比别的页多出 12dp），
    //  现在统一由 `BindingList` 的 `.padding(bottom = PAGE_BOTTOM_PADDING)` 给 ——
    //  与首页及其它页同一处、同一档。

    // 入口格那一层的菜单（见 `BindingGridState.tileMenu`）。它**不占布局位置** ——
    // 真正的落点由那一格记下的触点决定，挂在这里只是让它跟着这一页一起进 / 出组合。
    BindingTileMenu(st)

    // 「自定义变量」那一格点开的两层抽屉（挑变量 → 挑对它做什么），同样不占布局。
}

/**
 * 这一档的长按动作**自己**不支持「连续触发」的原因；`null` = 支持。
 *
 *  两条判据与判定侧同一个口径（`KeyConfig.longIsSerial` / `KeyConfig.longIsVoiceMemory`）：
 *  · 多条命令：反复重放整条命令链没有意义；
 *  · 灵感语音那一族（开始 / 结束）：按一下就完事，重复拨没有语义。
 *
 *  **它只该管「连续触发」那一族**（开关 + 振动 + 间隔），不该连坐「松手才触发」「松手再执行」
 *  与「长按延迟」—— 那三项跟连不连发没有关系。真机上撞到过：长按=开始灵感语音、松手=结束
 *  灵感语音，整张卡全灰，用户刚配好的松手那一行点都点不了。
 */
private fun SettingsPage.longActionNoRepeatReason(current: Action): String? {
    if (isSerialCommand(configState.commands.value, current.token)) {
        return I18n.get(Strings.BINDING_SERIAL_UNSUPPORTED_REPEAT, "多条命令不支持连续触发")
    }
    val voice = (current as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true
    return if (voice) I18n.get(Strings.BINDING_VOICE_UNSUPPORTED_REPEAT, "灵感语音不支持连续触发") else null
}
