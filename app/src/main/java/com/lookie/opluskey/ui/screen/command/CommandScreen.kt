package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hideIme
import com.lookie.opluskey.ui.isImeVisible
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT_MS
import com.lookie.opluskey.ui.model.commandKindLabel
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 「自定义命令」页的骨架 —— 类型选择器带、两页的 pager、以及那几条只在整页上跑一次的副作用。
//
// 一页的内容在 `CommandPage.kt`、「已保存的命令」那段网格在 `SavedCommands.kt`、
// 五个确认窗在 `CommandDialogs.kt`。这里只管「pager 在哪一页、那几个位归谁」。
//
// 拆之前这里是 626 行一个函数（含三层 lambda 里的整页内容），改一行得先数括号。

internal const val SERIAL_CARD_ROWS = 1

/**
 * 「已保存的命令」里单条命令卡的行高下限 = 卡宽 × 本值。
 * 单条只有「一行摘要」，占满正方形会空出一大半，取一半即可。
 */
internal const val SINGLE_CARD_MIN_HEIGHT_RATIO = 0.5f

/**
 * 「支持的命令格式」卡里每行的行高（也就是整行的触达高度）。
 * 这一行整行可点，高度不够就等于把触达区缩到手指够不着。
 */
internal val FORMAT_ROW_TOUCH = 32.dp

/**
 * 「自定义命令」页那条类型选择器带（单条 / 多条）—— 挂在顶栏的 `bottomContent` 里。
 *
 * 亮哪一个由 `pagerState.currentPage` 说了算，点它则去翻页；高度报给
 * [SettingsPage.commandBandHeight]，内容顶上那个占位 Spacer 读它。
 */
@Composable
internal fun SettingsPage.CommandKindBand(pagerState: PagerState) {
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { commandBandHeight.report(it.height) },
    ) {
        TabRowWithContour(
            tabs = CommandKind.entries.map { commandKindLabel(it) },
            // 亮哪一个只看分页器停在哪一页（`currentPage`）。
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = {
                tick()
                scope.launch { pagerState.animateScrollToPage(it) }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 左右与卡片同一个 [PAGE_MARGIN]；纵向只带下边一档（[PAGE_TOP_PADDING]）。
                    .padding(
                        start = PAGE_MARGIN,
                        end = PAGE_MARGIN,
                        bottom = PAGE_TOP_PADDING,
                    ),
        )
    }
}

/**
 * 「自定义命令」页：表单、已保存列表与底部操作工具栏 —— 从「按键绑定」页顶栏菜单 push
 * 出来的第三级页面。
 *
 * @param pagerState 单条 / 多条这两页的分页状态，由调用点建好传进来：类型选择器那条带子
 *   挂在顶栏（[CommandKindBand]）、内容在这一页，两棵树共用同一份状态。
 */
@Composable
internal fun SettingsPage.CommandScreen(
    scrollBehavior: ScrollBehavior,
    pagerState: PagerState,
) {
    val tick = rememberTick()
    // 这一页那几个「一次只开一个」的浮层与弹窗（风险提示 / 长按菜单 / 删除 / 填前缀）。
    val ui = remember { CommandUiState() }
    //  滚动位置**每个类型页各持一份**（与「按键绑定」页那三个手势页同一套做法）。
    //  注意：两页**共用一份**的话**切回来就滚不动**：`ScrollState` 内部只有一个
    //  `scrollableState`，而 pager 滑动时相邻两页同时在场 ⇒ 两处 `verticalScroll` 挂
    //  同一个 state、互相打架（「按键绑定」页踩过同一个坑）。
    //  「点已保存的命令要滚回顶部」那几处也各自读**本页**这一份。
    val pageScrolls = CommandKind.entries.map { rememberScrollState() }
    val scope = rememberCoroutineScope()
    val tabBlockDp = commandBandHeight.dp()

    // 首次进这一页要先过一道风险提示：这一页能让用户写任意 shell 命令，
    //  而我们**不做任何检验或保护**。不确认就退出这一页（[goBack]），确认了才记下这一笔。
    //  记在配置里（`Config.commandDisclaimerAccepted`）：清空配置按 `DEFAULT` 重建 ⇒ 这一位跟着
    //  没了 ⇒ 下次进这一页照样弹（用户要的「重置配置就要重新确认」就是这么来的）。
    //  `configReadOnly` 时不弹 —— 那时写盘会被拒，确认了也记不下（与首页那次同一口径）。
    LaunchedEffect(Unit) {
        if (!configState.readOnly && !configState.config.value.commandDisclaimerAccepted) {
            XLog.w("自定义命令：还没确认过风险提示，弹窗（不确认就退出这一页）")
            ui.disclaimerPending = true
        }
    }
    CommandTabSync(pagerState)
    CommandResetTail()

    // —— 两页：单条 / 多条 ——
    // 分页器承载内容，顶栏那条带子驱动它；`draft.kind` 始终跟停下来的那一页走，
    // 保存 / 载入 / 工具条显隐都读它。
    Box(
        Modifier
            .fillMaxSize()
            .background(COUITheme.colorScheme.surface),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    // 横向越界回弹：与「按键绑定」页那三个手势页同一套弹簧
                    .overScrollHorizontal(),
        ) { page ->
            // 这一页是哪一类 —— 页内所有 `kind` 判断都指它，不是 `draft.kind`
            CommandPage(
                kind = CommandKind.entries[page],
                scroll = CommandPageScroll(pageScrolls[page], scrollBehavior, tabBlockDp),
                ui = ui,
                tick = tick,
                onScrollTop = { scope.launch { pageScrolls[page].animateScrollTo(0) } },
            )
        }

        CommandDialogs(ui) {
            scope.launch { pageScrolls[pagerState.currentPage].animateScrollTo(0) }
        }
    }
}

/**
 * 分页器停下时把 `draft.kind` 拨到那一页，顺带处理「换 tab」的两件事。
 */
@Composable
private fun SettingsPage.CommandTabSync(pagerState: PagerState) {
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val kind = CommandKind.entries[page]
            //  换 tab 就把**编辑态摘掉**：`editingName` 是「正在改哪一条」的标记，而它属于**上一个
            // tab 的那份表单** —— 留着会让新 tab 的重名判定把它当成「自己」，于是同名不判重、
            // 一路覆盖掉另一条命令（真机反馈「保存判断应限制在当前 tab 的表单下」）。
            //  摘编辑态时**名称要一起清**：只摘标记不清名字的话，名称格里还留着上一条命令的名字，
            //  新 tab 拿它去判重必然撞上库里那条 ⇒ 名称格报「重名」、保存被禁，而用户只是滑了一下
            //  （看起来毫无理由）。那一格是**用户手输的**（还没保存）时不清 —— 那是他的东西。
            //  表单内容（`single` / `steps`）照旧保留：那是两套互不干扰的字段。
            if (kind != commandDraft.draft.kind) {
                if (commandDraft.draft.editingName != null) {
                    commandDraft.draft.editingName = null
                    commandDraft.draft.name = ""
                }
            }
            commandDraft.draft.kind = kind
            // 换 tab 收掉软键盘：单条那栏的命令输入框可能正开着键盘，翻到另一栏会盖住表单。
            val host = activity
            if (host != null && isImeVisible(host)) {
                XLog.w("自定义命令：换 tab ⇒ 收键盘")
                hideIme(host)
            }
        }
    }
}

/**
 * 「重置」收尾：行级退场播完才真正 reset —— 放 `SerialSteps` 里会因步骤卡隐藏而永远挂起。
 */
@Composable
private fun SettingsPage.CommandResetTail() {
    LaunchedEffect(commandDraft.resetPending) {
        if (commandDraft.resetPending) {
            // 等的是**步骤行退场**（`STEP_ROW_EXIT_MS`）播完，留 40ms 余量收尾；不是换 tab 的时长
            //  （那是另一件事，多等 100ms 只会让表单在原地干等）。
            delay(STEP_ROW_EXIT_MS + 40L)
            //  注意：这段等待里用户可能已经在输入栏里敲字了（点「重置」之后焦点还在名称 / 命令框上），
            //  无条件 `reset()` 会把刚敲进去的内容一起吞掉。
            //  指纹一致才收尾；不一致说明用户已经在写新的了，重置的意图已被覆盖，把表单留给他。
            if (commandDraft.draft.contentFingerprint() == commandDraft.resetBaseline) {
                commandDraft.draft.reset()
            } else {
                XLog.w("重置收尾：退场期间表单又被改过，放弃这次 reset（保住用户刚输入的内容）")
                // 步骤行在退场那边已经被标掉、会被摘除，而重建那道闸的 key 是 `draft.steps` 的内容
                //  —— 内容没变不会重跑 ⇒ 步骤凭空消失、数据还在。让 SerialSteps 把它们收回来。
                commandDraft.resetAborted = true
            }
            commandDraft.resetPending = false
        }
    }
}

/**
 * 「名称」那一张卡片输入（连同重名提示）。
 *
 *  单独抽成一个 composable 是为了**把 `draft.name` 的读取关在这一层里**：composable 的实参是在
 *  调用点求值的，而它原来直接写在 `CommandScreen` 的函数体里 —— 于是每敲一个字符就把整页打脏一次
 *  （pager 的两个类型页、整片「已保存的命令」网格、以及网格里每个格子都跟着重组）。
 *  关进来之后，打字只打脏这一个输入框。
 *
 *  重名判定（[commandNameDuplicated] 要扫一遍命令库）也跟着落在这里：它本来就只在打字时才有意义，
 *  且结果只喂给这一个输入框的 `errorMessage`。
 */
@Composable
internal fun SettingsPage.CommandNameField() {
    IconInputView(
        icon = PageIcons.InputName,
        value = commandDraft.draft.name,
        // 边输边清洗：只换掉换行、不 trim —— 带 trim 的版本会让空格打不进去。
        // trim 在保存（[addCommand]）与解析（`CommandLibrary.commandOf`）时做。
        onValueChange = { commandDraft.draft.name = CommandLibrary.sanitizeNameInput(it) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
        title = I18n.get(Strings.COMMAND_NAME_LABEL, "名称"),
        label = I18n.get(Strings.COMMAND_NAME_HINT, "请输入名称"),
        // 同名冲突与 `cmd:` 成环提示都交给组件画（都只在打字 / 改步骤时才有意义，结果只喂这一个输入框）
        errorMessage =
            when {
                commandNameDuplicated(commandDraft.draft.name, commandDraft.draft.editingName) -> {
                    I18n.get(Strings.COMMAND_NAME_DUPLICATE, "与已有命令重名，请换个名字")
                }

                else -> {
                    commandReferenceCycle()?.let {
                        I18n.format(Strings.COMMAND_CYCLE_ERROR, it.joinToString(" → "))
                    } ?: ""
                }
            },
    )
}

/**
 * 「多条命令」步骤列表里的一行 —— 列表本身（`SerialSteps`）在 `CommandSteps.kt`，行数据留在这边：
 * 它是这一页所有内容共用的展示状态，不是那一块的私有实现。
 */
internal class StepRowItem(
    val id: Int,
    val token: String,
) {
    val state = MutableTransitionState(false).apply { targetState = true }

    /**
     * 退场中：target 已切 false，播完 shrink+fade 就从展示层摘除。
     *
     *  组合期会读它（`count { !it.exiting }`、几处 `enabled = !item.exiting`），所以必须是 state：
     *  普通 `var` 只在「每个写点都恰好与 `state.targetState` 同帧写」时看着能工作（靠那一次重组
     *  顺带把读点重读一遍），那是巧合、不是保证。
     */
    var exiting by mutableStateOf(false)

    /**
     * 退场行的插回锚。**只在 effect 里读写**（差分重建 / 收尾放弃那两处），组合期不读 ⇒ 普通
     *  `var` 就够，不必是 state。
     */
    var anchorId = -1
}
