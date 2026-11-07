package com.lookie.opluskey.ui.screen.combo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.screen.binding.BindingList
import com.lookie.opluskey.ui.screen.binding.BindingPageState
import com.lookie.opluskey.ui.screen.binding.BindingPickers
import com.lookie.opluskey.ui.screen.binding.BindingScroll
import com.lookie.opluskey.ui.screen.binding.BindingTarget
import com.lookie.opluskey.ui.screen.openAppPickerForControl
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.ComboEditState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

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

    //  组合只有短按 / 长按两档（`ComboKey` 上没有双击）
    val gestures = listOf(Gesture.SINGLE, Gesture.LONG)
    val onCreateCommand: () -> Unit = {
        //  与「按键绑定」页那颗 FAB 同一套：进命令页是**新增**那条路，草稿要先清干净
        commandDraft.draft.reset()
        navTo(Screen.COMMANDS)
    }

    //  这一页停在哪一档记到页面级状态里 —— 顶栏 ⋮「条件执行」要用它决定编短按还是长按
    //  （那一页的档位是 `branchGesture`，由「按键绑定」页的 pager 写，这里得自己喂一次）。
    LaunchedEffect(pager.currentPage) { ComboEditState.page = pager.currentPage }

    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        //  内容与「按键绑定」页**完全同一份**（`BindingList`）：场景入口、设置卡、网格、
        //  命令网格、焦点圈、入口浮层都在那边。这一页只负责「换成组合这一档」——
        //  两页各写一遍时，行序与闸门迟早对不上（用户口径：按键与组合键功能完全一样）。
        BindingList(
            target = BindingTarget.Combo(combo.id, gestures[page]),
            page = BindingPageState(enabled = enabled, active = pager.currentPage == page),
            pickers = BindingPickers { t, stop -> openAppPickerForControl(t, stop) },
            onCreateCommand = onCreateCommand,
            scroll = BindingScroll(scrollBehavior, scrolls[page], bandBlock),
        )
    }
}
