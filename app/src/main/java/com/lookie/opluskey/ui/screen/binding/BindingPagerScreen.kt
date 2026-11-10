package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.HINT_SLOT_PAGE
import com.lookie.opluskey.ui.PageTabBand
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.rememberFabVisibleOnScroll
import com.lookie.opluskey.ui.screen.PagePagers
import com.lookie.opluskey.ui.screen.command.LeaveDiscardDialog
import com.lookie.opluskey.ui.screen.command.branchDraftNow
import com.lookie.opluskey.ui.screen.command.saveScenes
import com.lookie.opluskey.ui.screen.openAppPickerForControl
import com.lookie.opluskey.ui.state.ComboEditState
import com.lookie.opluskey.ui.tickNow
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal

// 「一档手势一屏动作」的那一页 —— **三个入口共用这一份实现**。
//
//  三页（「按键绑定」一颗键 / 「组合键」一档组合 / 「场景动作」一个场景块）的骨架完全一样：
//  顶栏底下一条手势带、下面是可横向翻的动作网格、右下角一颗进「自定义命令」的 FAB。
//  差别只有三处，全是**数据**、不是流程：
//    · 这一页在编的**几档**（三档 / 组合两档 / 场景块按表里那张定）；
//    · 这一页**开不开**（那颗键的 `enabled` / 那一组组合的 / 那一块场景的）；
//    · 动作**写到哪儿**（`BindingTarget` 的三支：配置 / 配置 / 草稿）。
//
//  所以是「一份实现 + 三个 Screen 值」，不是三份页面：
//  `ComboEditScreen` 与 `SceneBindingScreen` 两个副本已经删掉，`PageEntry` 那三支调的是同一个
//  `BindingPagerScreen`。**Screen 值仍然分开**（不是合成一个）—— 栈里这三个位置可能同时存在
//  （按键绑定 → 系统场景 → 场景动作），而页面级的取样源、顶栏高度、分页器都是**按 Screen 索引**的，
//  同一个值出现两次会互相覆盖。

/**
 * 「场景动作」页那颗「确认」按下之后 —— **这时才写配置**，写成之后**退回上一页**。
 *
 *  ## 它只管这一件事
 *
 *  这一颗的全部职责是「把这一块的那几档动作写回去」（落盘时整张草稿一起写，见下一节）——
 *  **不查条件配完没**（用户口径：两件事不该耦合，各自负责自己的）。条件那一侧归「系统场景」页：
 *  那一页那颗「确认」才判「条件没选完 / 有死分支」，这里再判一遍等于把上一页的账挪到这一页来收，
 *  而这两页改的根本不是同一件事（这一页改「这一档做什么」，上一页改「这块在什么时候生效」）。
 *
 *  ## 落盘写的是整张草稿
 *
 *  这一页改的是整表草稿里**第 [SettingsPage.sceneBindingAt] 块**的那几档（见 `BindingTarget.Scene`），
 *  落盘走「系统场景」页那一条（[saveScenes]）：整张草稿写回。
 *  块与块之间没有稳定身份（`SceneRule` 不是可寻址的记录，草稿那套行号也不落盘），按下标去换配置
 *  原表里的那一格，在草稿删过 / 挪过块之后就会写错一块；整表写回时写下去的就是屏幕上那张表本身。
 *  别的键 / 别的组合键一个字节都不动，那一段仍按原样带过去。
 *
 *  @return `true` = 已落盘（调用方随即退回上一页）；`false` = 写盘失败，人留在原地
 */
private fun SettingsPage.saveSceneBinding(): Boolean {
    val draft = branchDraftNow()
    if (!saveScenes()) return false
    //  落盘之后**这一块已经是新的了**：baseline 跟着挪过去。虽然紧跟着就退页，但只要还没退成
    //  （转场被取消 / 用户手快又按了一下），这一位就是「这一页改没改」的唯一判据。
    sceneBindingBaseline = draft.scenes.getOrNull(sceneBindingAt)
    hint(I18n.get(Strings.COMMAND_BRANCH_SAVED, "已保存系统场景"), slot = HINT_SLOT_PAGE)
    return true
}

/**
 * 顶栏底下那条**手势带** —— 三页共用这一条（原来三处分段控件各写一遍）。
 *
 * @param s 这一页是三个入口里的哪一个（取「几档、叫什么、开不开、量到哪份高度」）
 */
@Composable
internal fun SettingsPage.bindingTabBand(
    s: Screen,
    pagers: PagePagers,
) {
    val labels = bindingBandLabels(bindingTargetsOf(s).orEmpty())
    //  在编的那一份已经不在了（退场那一帧）：带子不画 —— 标签为空时分段控件也没有意义，
    //  这一页自己会在下一个 effect 里退掉。
    if (labels.isEmpty()) return
    PageTabBand(
        pagers.gesturesOf(s),
        labels = labels,
        //  与网格同一把尺子：这一份停用 / 整页不可写时带子灰掉，且翻不动
        enabled = bindingTargetEnabled(s),
        band = bindingBandOf(s),
    )
}

/**
 * 一档手势一屏的那一页。
 *
 * @param s 这一趟是三个入口里的哪一个 —— 只用来取「在编谁、几档、开不开、写哪儿」
 * @param pager 这一页的手势分页器（`PagePagers.gesturesOf`）—— 顶上那条手势带在**顶栏**那棵树里，
 *   两边只有共用同一个实例才对得上
 */
@Composable
internal fun SettingsPage.BindingPagerScreen(
    s: Screen,
    scrollBehavior: ScrollBehavior,
    pager: PagerState,
) {
    val targets = bindingTargetsOf(s)
    if (targets == null) {
        //  那一份不见了（组合被删 / 场景块被删、或调试通道直接跳页）：别摆一个空壳页，退回上一页
        BindingGone(s)
        return
    }

    val enabled = bindingTargetEnabled(s)
    //  带子高度由顶栏那条手势带量出来，内容顶上照它让位
    val bandBlock = bindingBandOf(s).dp()
    //  每档一份滚动位置 —— 寄在页面级而不是就地 `remember`，理由见 `bindingScrollsFor`
    val scrolls = bindingScrollsFor(s, targets.size)
    //  页数会变（三档 / 两档），而分页器是页面级的、上一次可能停在第三页 —— 夹一道再取
    val page = pager.currentPage.coerceIn(0, targets.lastIndex)

    /**
     * 进「自定义命令」页**新增**一条 —— 「按键绑定」与「组合键」两页的唯一入口
     * （右下角那颗 FAB 与网格末尾那行）。
     *
     * 这里必须 reset：`goBack` 只在草稿脏时才 reset，「点开一条已保存的命令看一眼、原样返回」
     * 那种不脏的退页不会清 `editingName`，于是下次点这颗 FAB 进来表单还挂着「修改命令」。
     * 「进编辑」那条路（网格里已选中的那一格再点一次）走的是 `draft.load`，不经过这里。
     *
     *  清的是**落下那一页**（`draft.kind`，分页器按它定位 tab），另一页不动 —— 两页是两个独立
     *  会话，把另一页也清等于无提示吞掉那边正编着的东西（整页归零只在出栈时发生）。
     */
    val navigateToCommands: () -> Unit = {
        commandDraft.draft.reset()
        navTo(Screen.COMMANDS)
    }

    //  「这一页停在哪一档」要落到页面级（见 [PersistGesturePage]）
    PersistGesturePage(s, page)

    Box(
        Modifier
            .fillMaxSize()
            // 这一层自带不透明底：push 转场交叠时不透出下层残影（浮层布局下由这个 Box 承担）
            .background(COUITheme.colorScheme.surface),
    ) {
        HorizontalPager(
            state = pager,
            modifier =
                Modifier
                    .fillMaxSize()
                    //  横向也带回弹：几档来回滑到两端时弹一下，与纵向回弹是同一套弹簧
                    .overScrollHorizontal(),
            //  这一份被停用 / 整页不可写时翻不动：判据与网格同一把尺子
            userScrollEnabled = enabled,
        ) { index ->
            BindingList(
                target = targets[index],
                //  pager 会把相邻页一起留在组合里：只有当前那一页该抢焦点、收浮层、吃方向键
                page = BindingPageState(enabled = enabled, active = page == index),
                pickers = BindingPickers { t, stop -> openAppPickerForControl(t, stop) },
                onCreateCommand = navigateToCommands,
                scroll = BindingScroll(scrollBehavior, scrolls[index], bandBlock),
            )
        }

        //  FAB 是**几档共用**的一颗（它不属于某一张 tab 的内容），跟着整页转场一起滑
        BindingPageFab(
            scenePage = s == Screen.SCENE_BINDING,
            //  「场景动作」页只看**这一块**改没改（见 `sceneBindingDirty`），不跟滚动、也不跟
            //  整份草稿的 `dirty`：那一份里还有上一页别的块的改动，与这一颗无关
            sceneDirty = sceneBindingDirty(),
            scrollVisible = rememberFabVisibleOnScroll(scrolls[page]),
            //  FAB 不经过 `CardRow`，那层默认振动管不到它 —— 自己叫一声
            onCommands = {
                tickNow()
                navigateToCommands()
            },
            onConfirm = {
                tickNow()
                //  写完才退：写盘失败时人留在页里（提示已说明原因）
                if (saveSceneBinding()) goBack()
            },
        )
    }

    //  「场景动作」页改了还没确认就按返回时，`goBack` 会先置这一位 —— 这个窗必须挂上，
    //  否则返回被拦住却**没有窗**（与「系统场景」页那张同一份实现）。
    //  另外两页不挂：它们点一格就当场落盘，没有「未确认」这一说。
    if (s == Screen.SCENE_BINDING) {
        LeaveDiscardDialog(
            title = I18n.get(Strings.DIALOG_DISCARD_CONDITION_TITLE, "放弃未保存的场景"),
            //  放弃 = **把这一块还原成进来时那份**。这一页改的是共享草稿的其中一块，
            //  草稿不会随离栈整份清（它属于还留在栈里的「系统场景」页）—— 只弹栈的话，
            //  改动仍躺在草稿里，退回上一页后那颗「确认」照样能把它写下去。
            onDiscard = { discardSceneBindingEdits() },
        )
    }
}

/** 丢掉「场景动作」页这一趟的改动：把在编那一块还原成进来时那份（见 `sceneBindingBaseline`）。 */
private fun SettingsPage.discardSceneBindingEdits() {
    val base = sceneBindingBaseline ?: return
    XLog.w("场景动作：放弃第 ${sceneBindingAt + 1} 块的改动，已还原成进来时那份")
    branchDraftNow().update(sceneBindingAt, base)
}

/**
 * 在编的那一份**不在了** —— 别摆一个空壳页，退回上一页。
 *
 *  只在**本页确实是栈顶**时退：退场途中不再补一刀（与「选择界面」页同一套）。
 *  抽出来只为让 [BindingPagerScreen] 留在 detekt 的长度上限内。
 */
@Composable
private fun SettingsPage.BindingGone(s: Screen) {
    LaunchedEffect(Unit) {
        XLog.w("${s.name}：在编的那一份不在了，栈=$navStack → 退回上一页")
        if (navStack.lastOrNull() == s) goBack()
    }
}

/**
 * 把「这一页停在哪一档」落到页面级 —— 调试通道按它挑手势，「组合键」页的 ⋮「条件执行」按它
 * 挑短按 / 长按（`ComboEditState.page`）。
 *
 *  必须写在 effect 里：pager 状态在这个 composable 里、跨树拿不到，「停在哪一页」只有组合
 *  这一层知道；而组合期只读不写是本项目的纪律。
 */
@Composable
private fun SettingsPage.PersistGesturePage(
    s: Screen,
    page: Int,
) {
    LaunchedEffect(s, page) {
        when (s) {
            Screen.BINDING -> branchGesture = page
            Screen.COMBO_EDIT -> ComboEditState.page = page
            else -> Unit
        }
    }
}
