package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.trigger
import com.lookie.opluskey.core.present.keyName
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageTabBand
import com.lookie.opluskey.ui.chrome.rememberFabVisibleOnScroll
import com.lookie.opluskey.ui.feedback.HINT_SLOT_PAGE
import com.lookie.opluskey.ui.feedback.HintText
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.tickNow
import com.lookie.opluskey.ui.model.text.gestureLabel
import com.lookie.opluskey.ui.row.plain
import com.lookie.opluskey.ui.screen.PagePagers
import com.lookie.opluskey.ui.screen.command.LeaveDiscardDialog
import com.lookie.opluskey.ui.screen.command.SceneBlockSave
import com.lookie.opluskey.ui.screen.command.branchDraftNow
import com.lookie.opluskey.ui.screen.command.saveSceneBlock
import com.lookie.opluskey.ui.screen.command.sceneBlockEnabled
import com.lookie.opluskey.ui.screen.command.sceneGesturePages
import com.lookie.opluskey.ui.screen.keyEnabled
import com.lookie.opluskey.ui.screen.openAppPickerForControl
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.binding.ComboEditState
import com.lookie.opluskey.ui.state.binding.KeyWatchState
import com.lookie.opluskey.ui.state.page.SettingsTarget
import com.lookie.opluskey.ui.state.signal.BandHeight
import com.lookie.opluskey.ui.write.BindingTarget
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
 * 「场景动作」页那颗「确认」按下之后 —— 写成之后**退回上一页**。
 *
 * ## 它只写**这一块**
 *
 *  这一页改的是「这一档做什么」，上一页改的是「这一块在什么时候生效」—— 两件事，两颗「确认」
 *  各管自己的账。所以这里只把草稿里**这一块的动作那几格**写回配置（`saveSceneBlock`），不整表写回：
 *  整表写回会把上一页还没确认的条件改动一起落盘，等于替用户按了那颗「确认」。
 *  写下去的那一块是「配置里的条件 + 草稿里的动作那几格」—— 条件那半仍归上一页。
 *
 *  也**不查条件配完没**（用户口径：两件事不该耦合，各自负责自己的）。条件那一侧归「系统场景」页。
 *
 * ## 写不了的时候
 *
 *  这一块还没进配置（新加的块，没有配置槽位）或配置在别处变过时，这一趟**不写** ——
 *  改动留在草稿里，退回上一页后那张表仍是「未保存」，上一页那颗「确认」会把它整表写下去。
 *  只有写盘真失败才留在原地（`SceneBlockSave.Failed`）。
 *
 * @return `true` = 可以退页；`false` = 写盘失败，人留在原地
 */
private fun SettingsPage.saveSceneBinding(): Boolean {
    val at = sceneBinding.at
    val result = saveSceneBlock(at)
    if (result == SceneBlockSave.Failed) return false
    //  「这一页改没改」的基准跟着挪到刚确认的那一块：还没退成（转场被取消 / 用户手快又按了一下）
    //  时，这一位就是唯一判据。
    sceneBinding = sceneBinding.withBaseline(branchDraftNow().scenes.getOrNull(at))
    if (result == SceneBlockSave.Saved) {
        hint(I18n.get(Strings.COMMAND_BRANCH_ACTION_SAVED, "已保存这一档动作"), slot = HINT_SLOT_PAGE)
    }
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
            //  这一份自己被停用（键 / 组合）时**整颗不出现**（用户口径）：停用的绑定不接管按键，
            //  「进自定义命令新增一条」这一趟没有去处。整页闸门（主开关 / 链路 / 只读）**不**据此
            //  隐藏 —— 那是另一件事，命令库与某颗键开没开无关（见 `BindingFab` 的说明）。
            scrollVisible = rememberFabVisibleOnScroll(scrolls[page]) && bindingTargetStopped(targets[page]) == null,
            //  FAB 不经过 `CardRow`，那层默认振动管不到它 —— 自己叫一声
            onCommands = {
                tickNow()
                navigateToCommands()
            },
            onConfirm = {
                tickNow()
                //  写完才退：写盘失败时人留在页里（提示已说明原因）
                if (saveSceneBinding()) popBackStack()
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

/** 丢掉「场景动作」页这一趟的改动：把在编那一块还原成进来时那份（见 `sceneBinding.baseline`）。 */
private fun SettingsPage.discardSceneBindingEdits() {
    val base = sceneBinding.baseline ?: return
    XLog.w("场景动作：放弃第 ${sceneBinding.at + 1} 块的改动，已还原成进来时那份")
    branchDraftNow().update(sceneBinding.at, base)
}

/**
 * 在编的那一份**不在了** —— 别摆一个空壳页，退回上一页。
 *
 *  只在**本页确实是栈顶**时退：退场途中不再补一刀（与「选择界面」页同一套）。
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

// 「这一页**是什么**」的那几个纯函数。
//
//  它们只看页面状态、不碰布局：在编哪几档、这一页开不开、要不要压暗、手势带量到哪份高度、
//  那几档叫什么。页面骨架那边只负责把它们接进顶栏与网格。

/**
 * 这一页**每档一份**的滚动位置。
 *
 *  `ScrollState` 内部只有一个 `scrollableState`，几档共用一个会互相打架（滑一档另一档跟着动），
 *  所以按档数一档一份。
 *
 *  **寄在页面级**（[SettingsPage.bindingScrolls]）而不是就地 `remember`：被「选择应用」盖住时
 *  这一页的整棵组合会被销毁，就地 remember 的那份跟着没 —— 退回来列表直接回顶部
 *  （用户口径「点 FAB 返回绑定页后滚动距离会归零」）。
 *
 *  档数会变（组合键三档 / 场景两档 / 按键三档），上一次那一份尺寸对不上就重开一份 ——
 *  把旧的那份按新档数续用会张冠李戴（第 2 档用上第 3 档的偏移）。
 */
@Composable
internal fun SettingsPage.bindingScrollsFor(
    s: Screen,
    pages: Int,
): List<ScrollState> =
    remember(pages) {
        bindingScrolls[s]
            ?.takeIf { it.size == pages }
            ?: List(pages) { ScrollState(0) }.also { bindingScrolls[s] = it }
    }

/**
 * 这一页在编的**几档**，顺序即分页器页序；`null` = 在编的那一份已经不在了（该退页）。
 *
 *  「不在了」只有两处会真发生：组合键在别处被删、场景块被删 / 草稿被重置 ——
 *  一颗键永远回得出一份（`Config.key` 有默认值兜底），所以 `BINDING` 那一支不会给 `null`。
 */
internal fun SettingsPage.bindingTargetsOf(s: Screen): List<BindingTarget>? =
    when (s) {
        Screen.COMBO_EDIT -> {
            val combo = editingComboOrNull() ?: return null
            //  **只有两页**（短按 / 长按）—— 「这一档是怎么按出来的」不占页签，它是 ⋮ 菜单里
            //  那一行「触发方式」（`ComboEditMenu` → `ComboModeDialog`）。
            //
            //  这样做是因为它是这一档的**身份**（`ComboKey.mode`），不是同一条手势底下并列的
            //  第二维度：三种触发方式共用 `single` / `long` 这一对槽，所以换方式改的是这一档的
            //  判据，动作表还是这两页。
            COMBO_GESTURES.map { g -> BindingTarget.Combo(combo.id, g) }
        }

        Screen.SCENE_BINDING -> {
            val draft = branchDraftNow()
            val at = sceneBinding.at
            if (draft.scenes.getOrNull(at) == null) return null
            //  「这一档属于哪一组组合」随目标一起带过去：组合的场景表背后没有某一颗键，
            //  长按那几张卡要写回**那一组**的参数（见 `BindingTarget.Scene.comboId`）
            sceneGesturePages().map { BindingTarget.Scene(draft, at, it, branchDraft.comboId) }
        }

        else -> {
            val cfg = configState.config.value
            val id = KeyWatchState.editing(cfg)
            val key = cfg.key(id)
            //  三击 / 四击 / 单击后长按那几页**跟着这颗键自己那几颗开关走**
            //  （`KeyConfig.extraClicksOn` / `clickThenLongOn`）：没开的档根本不存在，
            //  不是「存在但灰着」—— 页都少一页，带子上也不列。
            GestureTrigger.slotsOn(key.extraClicksOn, key.clickThenLongOn, key.doubleClickOn).map { BindingTarget.Key(id, it) }
        }
    }

/** 这一页现在**能不能改** —— 整页那几道闸 × 在编的那一份自己开没开。 */
internal fun SettingsPage.bindingTargetEnabled(s: Screen): Boolean =
    when (s) {
        // 组合键**不叠加**「这颗键开没开」：它有自己的开关（那一组），所以吃的是整页闸门
        // （主开关 + 链路 + 只读）+ 自己那一位。
        Screen.COMBO_EDIT -> {
            val combo = editingComboOrNull()
            pageWritable() && combo?.enabled != false
        }

        // **场景整条路只看整页闸门**（主开关 + 链路 + 只读）：这一块 / 这一颗键自己停用了
        //  照样要能先把动作配好、再回去启用 —— 与「独立设置」页同一条口径。
        Screen.SCENE_BINDING -> {
            pageWritable()
        }

        // 一颗键那一页吃它自己的 `enabled`（模块总闸另有一层，关着时整页走 `blockedReason`），
        // 外加**「只参与组合按键」**：那颗键不判单键手势，配一档动作也没处执行 —— 整页灰掉。
        // **只灰绑定页**：独立设置 / 按键设置不走这个函数，用户仍要能把这一位关回来。
        else -> {
            val cfg = configState.config.value
            val id = KeyWatchState.editing(cfg)
            keyEnabled(id) && !cfg.key(id).comboOnly
        }
    }

/**
 * 这一份**现在不能给它配动作**的原因 —— 能配回 `null`。
 *
 *  四个读者都在这一页族里，判据必须同一份：页面顶部那条「系统场景」入口、⋮ 菜单里那一行、
 *  「自定义命令」小标题要不要接跳转、右下角那颗「进自定义命令」的 FAB 显不显示。
 *  （用户口径：按键 / 组合键禁用时，这几处一并不给用。）
 *
 * ## 两个来源，同一条结论
 *
 *  · **这一份自己被停用**（`enabled = false`）—— 用户把它关了；
 *  · **「只参与组合按键」**（`KeyConfig.comboOnly`）—— 这颗键不判单键手势，配一档动作没处执行。
 *
 *  后者原先只拦住绑定页整页（见 [bindingTargetEnabled]），这一族四处却还亮着：系统场景能点进去、
 *  FAB 还在、菜单里那行还能选 —— 而那里配出来的东西永远不会被执行。用户口径是「一并不给用」，
 *  所以两者在这里收成同一个答案：**现在不能往它身上配动作**。
 *
 *  说法**不在这里另写**：停用那句与「独立设置」入口、两页顶栏那颗设置键同一个出口
 *  （[SettingsTarget.disabledReason]，`按键「X」已停用` / `组合键「X」已停用`）；
 *  「只参与组合按键」那句与绑定页整页灰共用 `BINDING_COMBO_ONLY_BLOCKED` —— 同一件事在页面上
 *  与这四个入口上说成两句不同的话，用户会以为是两个毛病。
 *
 *  **只认「这一份」**：整页闸门（主开关 / 链路 / 只读）不在这里 —— 那是另一件事，
 *  页面另有整片压灰与自己那句原因（见 [bindingTargetEnabled] / `disabledClickReason`）。
 *  场景块（[BindingTarget.Scene]）永远回 `null`：那一页的闸门只看整页（见 [bindingTargetEnabled]）。
 */
internal fun SettingsPage.bindingTargetStopped(target: BindingTarget): String? {
    val cfg = configState.config.value
    return when (target) {
        is BindingTarget.Key -> {
            val reason =
                SettingsTarget.Key(target.id).disabledReason(cfg)
                    ?: if (cfg.key(target.id).comboOnly) {
                        HintText(
                            I18n.get(Strings.BINDING_COMBO_ONLY_BLOCKED, "按键 %s 只参与组合按键"),
                            listOf(keyName(target.id)),
                        )
                    } else {
                        null
                    }
            reason?.plain()
        }

        is BindingTarget.Combo -> {
            SettingsTarget.Combo(target.id).disabledReason(cfg)?.plain()
        }

        is BindingTarget.Scene -> {
            null
        }
    }
}

/**
 * 在编的那一份**自己被停用**了吗 —— 只服务顶栏那行小标题的压暗。
 *
 *  与 [bindingTargetEnabled] **不是一回事**：压暗只在「这一份被停用」时说，
 *  不掺整页闸门 —— 主开关关掉时内容已经整片在渐变，标题跟着一起压暗会读成「这一份坏了」。
 */
internal fun SettingsPage.bindingTargetOff(s: Screen): Boolean =
    when (s) {
        Screen.COMBO_EDIT -> editingComboOrNull()?.let { !it.enabled } == true
        Screen.SCENE_BINDING -> !sceneBlockEnabled(sceneBinding.at)
        else -> !keyEnabled(KeyWatchState.editing(configState.config.value))
    }

/**
 * 这一页那条手势带**量到哪一份高度** —— 按页索引（[SettingsPage.bands]）。
 *
 *  不共用：转场时相邻两页同时在组合里，共用一个值只会互相改对方的占位。
 */
internal fun SettingsPage.bindingBandOf(s: Screen): BandHeight = bands[s]

/**
 * 手势带上那几档的**文案** —— 组合键那两档叫「短按 / 长按」，其余叫「单击 / 双击 / 长按 / 三击 / 四击」。
 *
 *  这套叫法与「组合键」页其余文案一致（`ComboKey` 只有短按 / 长按），不能统一成一套：
 *  组合没有双击，把短按写成「单击」会让人去找那颗并不存在的单击。
 */
internal fun bindingBandLabels(targets: List<BindingTarget>): List<String> =
    if (targets.firstOrNull() is BindingTarget.Combo) {
        //  组合那一页**只有手势这两档**：触发方式（等窗口 / 先按住第一颗 / 先按住第二颗）不占
        //  页签，它是 ⋮ 菜单里那一行（见 `ComboEditMenu`）。
        targets.map { target ->
            if (target.gesture == GestureTrigger.LongPress) {
                I18n.get(Strings.COMBO_LONG, "长按")
            } else {
                I18n.get(Strings.COMBO_SHORT, "短按")
            }
        }
    } else {
        targets.map { gestureLabel(it.gesture) }
    }

/** 组合键那一页只有两档（`ComboKey` 上没有双击）—— 顺序即分页器页序。 */
internal val COMBO_GESTURES: List<GestureTrigger> = listOf(Gesture.SINGLE, Gesture.LONG).map { it.trigger() }

/** 在编的那一档组合（`null` = 没在编 / 刚被删）—— 三处判据都从这一支取。 */
private fun SettingsPage.editingComboOrNull() =
    ComboEditState.editingId?.let { id ->
        configState.config.value.combos
            .firstOrNull { it.id == id }
    }
