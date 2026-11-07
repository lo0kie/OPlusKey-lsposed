package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SectionTitleClick
import com.lookie.opluskey.ui.SettingsPage

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
    target: BindingTarget,
    enabled: Boolean,
    st: BindingGridState,
    onCreateCommand: () -> Unit,
) {
    val g = target.gesture

    // —— 按键设置：**在网格上面** ——
    //  先说「这一档怎么判」，再挑「绑什么」。**长按那一档单键与组合共用同一张卡**
    //  （`LongPressCard`，参数走 `LongPressParams`）；单击 / 双击只有单键有（组合没有这两档）。
    Column {
        if (g == Gesture.SINGLE && target is BindingTarget.Key) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            BindingSettingsCard(enabled)
        }

        if (g == Gesture.DOUBLE && target is BindingTarget.Key) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            InstantSingleCard(enabled)
        }

        if (g == Gesture.LONG) {
            SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            LongPressCard(target, enabled)
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
