package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.PageTabBand
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addCommand
import com.lookie.opluskey.ui.hideIme
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.isImeVisible
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.commandKindLabel
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import kotlinx.coroutines.launch

// 「自定义命令」页的骨架 —— 类型选择器带、两页的 pager、以及那几条只在整页上跑一次的副作用。
//
// 一页的内容在 `CommandPage.kt`、「已保存的命令」那段网格在 `SavedCommands.kt`、
// 五个确认窗在 `CommandDialogs.kt`。这里只管「pager 在哪一页、那几个位归谁」。
//
// 拆之前这里是 626 行一个函数（含三层 lambda 里的整页内容），改一行得先数括号。

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
 * 「支持的命令格式」卡里两列之间的间距（左列写法与右列说明之间）。
 *
 *  跟着 `footnote1` 的字号走：比一个全角字的宽度略窄，够把两列分开又不像两栏表格。
 */
internal val FORMAT_COLUMN_GAP = 12.dp

/**
 * 「自定义命令」页那条类型选择器带（单条 / 多条）—— 挂在顶栏的 `bottomContent` 里。
 *
 * 亮哪一个由 `pagerState.currentPage` 说了算，点它则去翻页；滑块位置另按浮点页码实时插值
 * （横向滑动时跟手）；高度报给 [SettingsPage.commandBandHeight]，内容顶上那个占位 Spacer 读它。
 */
@Composable
internal fun SettingsPage.CommandKindBand(pagerState: PagerState) {
    PageTabBand(
        pagerState = pagerState,
        labels = CommandKind.FORM_TABS.map { commandKindLabel(it) },
        // 排序态下这一条也禁用 —— 与「按键绑定」页主开关为关时同一套（做法在 [PageTabBand] 里）
        enabled = !commandDraft.stepEditing,
        band = commandBandHeight,
    )
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
    val pageScrolls = CommandKind.FORM_TABS.map { rememberScrollState() }
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
            // 排序态下不许横滑换页：另一页没有步骤可排，而滑过去之后编辑态还没摘
            // （摘是在 `currentPage` 变了之后，见 [CommandTabSync]）—— 那一小段里
            //  单条命令页会莫名其妙地整页灰着。要换 tab 就点上面那条带子，那一下会先退出排序态。
            userScrollEnabled = !commandDraft.stepEditing,
            modifier =
                Modifier
                    .fillMaxSize()
                    // 横向越界回弹：与「按键绑定」页那三个手势页同一套弹簧
                    .overScrollHorizontal(),
        ) { page ->
            // 这一页是哪一类 —— 页内所有 `kind` 判断都指它，不是 `draft.kind`
            CommandPage(
                kind = CommandKind.FORM_TABS[page],
                scroll = CommandPageScroll(pageScrolls[page], scrollBehavior, tabBlockDp),
                ui = ui,
                tick = tick,
                onScrollTop = { scope.launch { pageScrolls[page].animateScrollTo(0) } },
            )
        }

        CommandDialogs(ui)

        CommandFloatingButton(scope = this, tick = tick)
    }
}

/**
 * 右下角那颗 FAB —— 底栏撤掉之后落盘点回到这里。
 *
 *  · 平时是「保存」：先判 [saveBlockedReason]，不满足就用页内提示说明原因；
 *  · 编辑列表（排序态）时它变成「完成」：那一状态下别的控件都禁用，出路只有这一颗。
 *
 *  空表单时**不显示**这颗（那会儿没有可保存的东西）；编辑列表时也要在（文案变「完成」）。
 *  单独抽一支只为让 [CommandScreen] 留在 detekt 的长度上限之内。
 */
@Composable
private fun SettingsPage.CommandFloatingButton(
    /** 落点（页面右下角）由 [PageCornerFab] 决定，它要的是外层那个 `Box` 的作用域。 */
    scope: BoxScope,
    tick: () -> Unit,
) {
    val sortingSteps = commandDraft.stepEditing
    with(scope) {
        PageCornerFab(visible = commandToolbarVisible() || sortingSteps) {
            PageFabButton(
                onClick = {
                    tick()
                    if (sortingSteps) {
                        commandDraft.stepEditing = false
                        XLog.w("多条命令：退出排序态（FAB）")
                    } else {
                        val blocked = saveBlockedReason()
                        if (blocked != null) {
                            //  「为什么不能存」归到页面那个禁用原因位：与行级说法同一条提示
                            hint(blocked, slot = HINT_SLOT_DISABLED)
                        } else {
                            addCommand()
                        }
                    }
                },
            ) {
                Icon(
                    PageIcons.Confirm,
                    contentDescription =
                        if (sortingSteps) {
                            I18n.get(Strings.COMMAND_STEP_SORT_DONE, "完成")
                        } else {
                            I18n.get(Strings.COMMON_CONFIRM, "确定")
                        },
                    tint = COUITheme.colorScheme.onPrimary,
                )
            }
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
            // 只有单条 / 多条这两页（分叉是独立页，见 `Screen.BRANCHES`）
            val kind = CommandKind.FORM_TABS[page]
            //  换 tab 只摘**排序态**：它是「这一页的步骤列表」的态，另一页没有步骤可排，
            //  带着它过去只会让那一页莫名其妙地整页禁用。
            //
            //  名称与编辑态**一个都不清**：它们现在各自属于一页（见 `CommandForm`）——
            //  原先只有一份时，切 tab 必须把载入进来的名字与编辑标记一起摘掉（否则新 tab 拿旧名字
            //  判重必撞），代价就是「翻一下就变成新增态、手输的名字也没了」。
            if (kind != commandDraft.draft.kind) {
                commandDraft.stepEditing = false
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

/** 命令名能输入多少字（超出的部分在输入框里直接吃掉）。 */
private const val MAX_NAME_LENGTH = 24

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
internal fun SettingsPage.CommandNameField(enabled: Boolean = true) {
    IconInputView(
        spec = InputSpec(PageIcons.LabelImportant, I18n.get(Strings.COMMAND_NAME_HINT, "请输入名称"), MAX_NAME_LENGTH),
        value = commandDraft.draft.name,
        // 边输边清洗：只换掉换行、不 trim —— 带 trim 的版本会让空格打不进去。
        // trim 在保存（[addCommand]）与解析（`CommandLibrary.commandOf`）时做。
        onValueChange = { commandDraft.draft.name = CommandLibrary.sanitizeNameInput(it) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
        // 同名冲突与 `cmd:` 成环提示都交给组件画（都只在打字 / 改步骤时才有意义，结果只喂这一个输入框）。
        //  措辞与「保存按钮灰着时那句话」共用 `commandNameProblem` —— 同一件事只说一处。
        errorMessage = commandNameProblem().orEmpty(),
    )
}
