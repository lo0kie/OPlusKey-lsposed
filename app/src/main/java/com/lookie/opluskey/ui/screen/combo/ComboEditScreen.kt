package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.screen.binding.BindingGridFocus
import com.lookie.opluskey.ui.screen.binding.BindingTarget
import com.lookie.opluskey.ui.screen.binding.BindingTileMenu
import com.lookie.opluskey.ui.screen.binding.BuiltInGrid
import com.lookie.opluskey.ui.screen.binding.CustomCommandsGrid
import com.lookie.opluskey.ui.screen.binding.rememberBindingGridState
import com.lookie.opluskey.ui.screen.openAppPickerForTarget
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.ComboEditState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.utils.overScrollVertical

// 「组合键」编辑页 —— 从列表点某一行进来。
//
// 与「按键绑定」页同一个骨架：顶栏底下一条分段控件（短按 / 长按）、下面是可横向翻的网格。
// 差别只有两处（用户口径「和另一个一样，只是去掉双击、去掉单击延迟」）：
//   · 只有两档，没有双击；
//   · 短按那一档没有设置卡，长按那一档有一张（连续触发 / 振动 / 间隔 / 长按延迟）。
//
// 一档一份动作网格：两档各写各的 `ComboKey`（`BindingTarget.Combo`）。

/**
 * 一档组合的编辑页。
 *
 * @param pager 短按 / 长按那个分页器（页面级，见 `PagePagers.combos`）——
 *   顶上那条分段控件在**顶栏**那棵树里，两边只有共用同一个实例才对得上。
 */
@Composable
internal fun SettingsPage.ComboEditScreen(
    scrollBehavior: ScrollBehavior,
    pager: PagerState,
) {
    val cfg = configState.config.value
    val id = ComboEditState.editingId
    val combo = id?.let { cid -> cfg.combos.firstOrNull { it.id == cid } }
    if (combo == null) {
        //  状态丢了（宿主 Activity 重建后栈还在，而这一位不落盘）或者那一档刚被删掉：
        //  别摆一个空壳页，直接退回列表。只在**本页确实是栈顶**时退 —— 退场途中不再补一刀。
        LaunchedEffect(Unit) {
            XLog.w("组合键编辑：editingId=$id 找不到，栈=$navStack → 退回列表")
            if (navStack.lastOrNull() == Screen.COMBO_EDIT) goBack()
        }
        return
    }

    //  闸门是**整页那道 + 这一档自己的开关**（与「按键绑定」页同理：那一页吃的是那颗键的
    //  `enabled`）。停用的组合照样能进来看，但网格 / 设置卡 / 分段控件都不再改得动 ——
    //  要改先在 ⋮ 菜单里启用它（顶上那行小标题旁边会挂一枚禁用标记，见 `PageTopBar`）。
    val enabled = pageWritable() && combo.enabled
    //  带子高度由顶栏那条分段控件量出来（`comboBandHeight`），内容顶上照它让位
    val bandBlock = comboBandHeight.dp()
    //  两页各一份滚动位置（`ScrollState` 内部只有一个 `scrollableState`，共用会互相打架）
    val scrolls = remember { List(2) { ScrollState(0) } }

    //  两段各一份网格状态：`remember` 的 key 里带着 `target`，互不影响（焦点、入口菜单、锚点）
    val short = rememberBindingGridState(BindingTarget.Combo(combo.id, Gesture.SINGLE)) { openAppPickerForTarget(it) }
    val long = rememberBindingGridState(BindingTarget.Combo(combo.id, Gesture.LONG)) { openAppPickerForTarget(it) }
    val onCreateCommand: () -> Unit = {
        //  与「按键绑定」页那颗 FAB 同一套：进命令页是**新增**那条路，草稿要先清干净
        commandDraft.draft.reset()
        navTo(Screen.COMMANDS)
    }

    //  这一页停在哪一档记到页面级状态里 —— 顶栏 ⋮「条件执行」要用它决定编短按还是长按
    //  （那一页的档位是 `branchGesture`，由「按键绑定」页的 pager 写，这里得自己喂一次）。
    LaunchedEffect(pager.currentPage) { ComboEditState.page = pager.currentPage }

    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        val grid = if (page == 0) short else long
        //  焦点圈跟着选中项走 —— 与「按键绑定」页**共用同一份实现**（`BindingGridFocus`）。
        //  少了这一句就会「点了别的动作，边框还留在原来那一格，退出去重进才对」：
        //  `focusedIndex` 只在网格状态建出来那一刻取过一次 `initialFocus`，之后没人挪它。
        BindingGridFocus(grid, pager.currentPage == page, "组合按键")
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .verticalScroll(scrolls[page])
                //  页底留白 —— 与全项目同一档、同一种写法（挂在滚动内容里，不用尾部 Spacer）
                .padding(bottom = PAGE_BOTTOM_PADDING),
        ) {
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + bandBlock))
            //  长按那一档：设置卡在网格**上面**（与「按键绑定」页同一个顺序 ——
            //  先「这一档怎么判」，再挑「绑什么」）。短按没有可调的东西，直接是网格。
            //  块与块之间的间距由小标题自带；网格自己那几组由网格内部管。
            Column {
                if (page == 1) {
                    //  小标题与「按键绑定」页长按那一档同一句（`binding.settings_section`）：
                    //  两页同一张卡，标题各写一套的话切页时会觉得换了一套东西。
                    SectionTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
                    ComboLongCard(combo, enabled)
                }
                BuiltInGrid(grid, enabled)
                // 「自定义命令」这一段**始终在**（用户口径：与「按键绑定」页一样另起一块、同一个
                // 小标题）—— 没有命令时网格自己换成那一行「自定义命令 →」，空着也看得见去处。
                SectionTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"))
                CustomCommandsGrid(grid, enabled, onCreateCommand)
            }
        }
    }

    //  两张网格各自那层入口菜单（入口格点了弹二级菜单）—— 不占布局，挂在这里让它们跟着这一页
    //  一起进 / 出组合。同一时刻只会有一层开着（点开那一格时才写自己的 `tileMenu`）。
    BindingTileMenu(short)
    BindingTileMenu(long)
}
