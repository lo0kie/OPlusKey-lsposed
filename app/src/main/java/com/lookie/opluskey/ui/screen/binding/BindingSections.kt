package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.actionOf
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import io.github.suqi8.coui.kmp.basic.SmallTitle

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
    //  下面那一档间距只在**真有这张卡**时给 —— 否则没有这张卡的那一档，
    //  「内置命令」标题会白多一截空档（标题紧贴它下面的内容，这是全页的规矩）。
    if (g == Gesture.SINGLE) {
        SmallTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
        BindingSettingsCard(enabled)
        Spacer(Modifier.height(GRID_ROW_GAP))
    }

    if (g == Gesture.DOUBLE) {
        SmallTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
        InstantSingleCard(enabled)
        Spacer(Modifier.height(GRID_ROW_GAP))
    }

    // 「长按多久算长按」与「长按连续触发 / 触发间隔」同属长按这一档的行为，摆在同一张卡里。
    if (g == Gesture.LONG) {
        SmallTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
        LongRepeatCard(
            blockedReason =
                when {
                    // 主开关为关 ⇒ 整页禁用，这张卡也跟着置灰
                    !enabled -> {
                        // 这里是**主开关自己关着**，不是「模块没生效」：`STATUS_DISABLED` 的正文是
                        //  「确认作用域含 system，换包后需重启」—— 拿它来报这一档，等于把用户赶去
                        //  LSPosed 里翻一圈，而他要做的只是把开关拨回来。
                        I18n.get(Strings.STATUS_KEY_DISABLED, "按键绑定已关闭")
                    }

                    isSerialCommand(configState.commands.value, current.token) -> {
                        I18n.get(Strings.BINDING_SERIAL_UNSUPPORTED_REPEAT, "多条命令不支持连续触发")
                    }

                    else -> {
                        null
                    }
                },
            // 「长按延迟」那行只看主开关（与「是不是多条命令」无关）
            masterEnabled = enabled,
        )
        Spacer(Modifier.height(GRID_ROW_GAP))
    }

    BuiltInGrid(st, enabled)

    // 「自定义命令」这一段**始终在**：没有命令时给一行「去创建自定义命令」。
    Spacer(Modifier.height(GRID_ROW_GAP))
    SmallTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"))
    CustomCommandsGrid(st, enabled, onCreateCommand)

    // 页面底部留白 —— 三个页面统一的那一档是 [PAGE_BOTTOM_PADDING]
    // 不能少这一档：少了的话这一页滚到底时最后一张卡直接贴着屏幕边缘。
    //  代价：FAB（下边距 64dp、高 56dp，占 64~120dp 那一带）滚到底时会压住最后一行。
    Spacer(Modifier.height(PAGE_BOTTOM_PADDING))
    //  再补 [GRID_GAP]：这 12dp 若挂在**滚动视口之外**（`.padding(bottom = …)`），
    // 视口就比整页矮 12dp，屏幕最底下那一条里没内容可画，透明的手势导航栏会把那一条
    // 露成一块平色（真机反馈「导航栏不透明、控件被颜色挡住」）。
    //  挪进内容里：让位量一分不变，但视口铺到屏幕底 —— 滚起来时内容能钻进导航栏底下。
    Spacer(Modifier.height(GRID_GAP))

    // 入口格那一层的菜单（见 `BindingGridState.tileMenu`）。它**不占布局位置** ——
    // 真正的落点由那一格记下的触点决定，挂在这里只是让它跟着这一页一起进 / 出组合。
    BindingTileMenu(st)
}
