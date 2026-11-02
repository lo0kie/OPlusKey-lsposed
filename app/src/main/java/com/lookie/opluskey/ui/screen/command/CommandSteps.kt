package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.GestureProbe
import com.lookie.opluskey.ui.IconAction
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.gestureProbe
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT_MS
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.openAppPickerForStep
import com.lookie.opluskey.ui.rememberGestureProbe
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.replaceStep
import com.lookie.opluskey.ui.screen.openVarOpsForStep
import com.lookie.opluskey.ui.state.ResumeSheet
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import kotlinx.coroutines.delay

/** [SerialSteps] 里「开着的是添加命令那一行」的哨兵值（步骤下标从 0 起，撞不上）。 */
private const val STEP_PICK_APPEND = -1

/**
 * 多条命令的步骤列表（「步骤」那张卡的内容）。
 */
@Composable
internal fun SettingsPage.SerialSteps() {
    val tick = rememberTick()
    //  **这一页只认「多条」那一份表单**，不走 `draft.steps`。
    //
    //  `draft.steps` 是「`draft.kind` 那一份」的快捷方式，而 `draft.kind` 由分页器**单向同步** ——
    //  翻页动画期间它晚一帧：从「单条」翻过来的那一帧 `kind` 还是单条，于是这里读到的是单条那份
    //  **空表** ⇒ `sync([])` 把展示行全标退场、`rows` 被清空 ⇒ 下一帧读到真的步骤时只能整表新建
    //  ⇒ 整张表从头展开一遍（用户口径「每次从单条切到多条，列表都重新展开了」）。
    //  从「命令列表」过来不会有这一帧（那时 `kind` 已经是多条），所以那条路看着正常。
    val form = commandDraft.draft.serialForm
    val steps = form.steps
    // 这一页现在是不是「排序态」（由右下角那颗 FAB 开关，见 `CommandDraftState.stepEditing`）：
    //  排序态下行尾换成上移 / 下移 / 删除，行的轻点不再换命令。
    val sorting = commandDraft.stepEditing
    // 底部抽屉开着没有，以及它是**哪种来处**（见 `StepPickerSheet`）：
    //  `null` = 没开、[STEP_PICK_APPEND] = 追加一步、`>= 0` = 换掉那一步 ——
    //  「添加命令」与「点已添加的步骤」两条入口共用这一支。
    //  初值恒为 `null`，恢复靠下面那个 `LaunchedEffect(screen)` —— 见它上面那段说明。
    var stepSheet by remember { mutableStateOf<Int?>(null) }
    //  **回到命令页那一刻**才把「回来要开哪一档」这笔账兑现。
    //
    //  · key 是 `screen` 而不是 `Unit`：压着「选择应用」时这一页的组合可能被重建，
    //    也可能不重建 —— 只认「当前页变回命令页」这一件事，两种情况都能兑现；
    //  · **只在命令页上消费**：压在别的页上就把它留着，否则先被那次重建吃掉，
    //    真回来时反而什么都不剩（真机口径「没点确认直接返回，抽屉应该出现，现在没有」）；
    //  · 设这笔账的那一刻 `screen` 还是命令页、随后立刻 push —— 这里不会误触发
    //    （effect 只在 `screen` 变化时重跑）。
    LaunchedEffect(screen) {
        val resume = picker.resumeSheetAt.value
        if (screen == Screen.COMMANDS && resume?.page == Screen.COMMANDS) {
            stepSheet = resume.index
            picker.resumeSheetAt.value = null
        }
    }
    // 这张卡的手势账本：抓「滑动时行上的点击照样派发」那种偶发现场（见 [GestureProbe]）。
    //  挂整张卡而不是每一行 —— 卡里的行都在这条手势范围里，且它比行的 `clickable` 更早拿到事件。
    val probe = rememberGestureProbe("步骤卡")

    // 行级动画的展示层：`draft.steps` 的镜像，每行一个稳定 id（差分规则见 [StepRowDisplay]）。
    //  它住在页面级状态（`commandDraft.stepRows`）而不是这里的 `remember`：切 tab 时这一页的
    //  组合会被注销，展示层跟着没了 —— 切回来重新建一遍就是**整张表重播进场动画**。
    val display = commandDraft.stepRows.rows

    //  `toList()` **只算一次**：它既是下面那个 effect 的 key，也是差分要用的那份快照。
    //  原来 key 与函数体各算一遍 —— 每遍都要读完整张 `SnapshotStateList` 再新建一个 List。
    val stepsNow = steps.toList()
    LaunchedEffect(stepsNow) {
        // 列表一变，行下标就不一定还落在原来那一行上（插入 / 删除 / 上移下移都挪位）——
        //  抽屉是按下标做事的，**那一步已经不在了**才收（用户口径「选中命令不关闭抽屉」：
        //  换一步本身就会改这个列表，一律收掉的话抽屉每选一次就没了）。
        stepSheet?.let { at -> if (at >= 0 && at !in stepsNow.indices) stepSheet = null }
        commandDraft.stepRows.sync(stepsNow)
    }

    //  **整张卡交给 [CardList]**（与页面上其它卡同一套：分隔线、首尾圆角、每行位置都由它算）。
    //
    //  行的进出场仍用这一页自己的状态（`item.state`，规格见 `STEP_ROW_ENTER` / `STEP_ROW_EXIT`）——
    //  以 `visibleState` 传进去，`CardList` 就按它播，不会再用布尔重播一遍。
    //  `visible` 那一位只用来算行位置：退场行（`targetState` 已 false）立刻从「行数」里退出去，
    //  下面那行「添加命令」于是**在过渡开始那一帧**就翻成 `Tail`（原先按展示层行数算，
    //  要等退场播完才翻，卡片高度会在动画结束那一刻再跳 4dp）。
    //  退场播完的行从展示层摘除（`currentState` 收到 false 之后再等一档退场时长）。
    //  按「正在退场的 id 集合」做 key：只在有新行开始退场时才重启这个 effect，
    //  比每行各挂一个 `LaunchedEffect` 少一层组合（行现在是通用的 `CardRow.Item`，
    //  没有可以挂 effect 的内容槽）。
    val exitingIds = display.filter { it.exiting }.map { it.id }
    LaunchedEffect(exitingIds) {
        if (exitingIds.isEmpty()) return@LaunchedEffect
        delay(VISIBILITY_EXIT_MS + STEP_EXIT_SETTLE_MS)
        display.removeAll { it.exiting && it.id in exitingIds && !it.state.currentState }
    }

    //  **整张卡交给 [CardList]**，每一行就是通用的 [CardRow.Item] —— 与页面上其它卡同一套：
    //  卡片、分隔线、行位置、图标与文字的间距都由它给，这里只填「这一行是什么」。
    //  行的进出场仍用这一页自己的状态（`item.state`，规格见 `STEP_ROW_ENTER` / `STEP_ROW_EXIT`）：
    //  以 `visibleState` 传进去，`CardList` 就按它播，不会再用布尔重播一遍。
    CardList(
        rows =
            display.map { item ->
                // 这一行在 steps 里的下标：展示层里排在它前面的未退场行数 ——
                // 退场行还在展示层里占位（收缩中），但已经不在 steps 里了。
                val displayIndex = display.indexOf(item)
                val stepsIndex = display.take(displayIndex).count { !it.exiting }
                // 每一行各自一份 `PreciseClickState`（按行 id 做 key）：`anchorCoordinates` 只有一格，
                // 共用一份会把触点换算到别的行上去。
                val rowPreciseClick = remember(item.id) { PreciseClickState() }
                CardRow.Item(
                    title = stepTitle(item.token, ::appNameOf),
                    summary = I18n.format(Strings.COMMON_STEP_INDEX, stepsIndex + 1),
                    icon = stepIcon(item.token, configState.commands.value),
                    // 排序态与退场中的行**不吃点击**（`onClick = null` = 纯展示行，也不挂按压反馈）：
                    // 排序态下这一行只让位给行尾那三颗按钮，否则「改顺序」与「换这一步」会撞在同一只手上。
                    onClick =
                        if (sorting || item.exiting) {
                            null
                        } else {
                            {
                                XLog.w("多条命令：点第 ${stepsIndex + 1} 步（弹选择菜单，替换模式）—— ${probe.describe()}")
                                //  抽屉是按**下标**做事（换这一步算下标），而列表一变下标就挪位 ——
                                //  所以上面那个 effect 会在列表变化时把抽屉收掉。
                                stepSheet = stepsIndex
                            }
                        },
                    trailing = {
                        // 只读态行尾与「添加命令」那一行**同一颗**选择器指示：整行可点、点了弹抽屉换这一步。
                        // 排序态换成上移 / 下移 / 删除三颗。
                        if (sorting) {
                            if (!item.exiting) {
                                StepRowButtons(index = stepsIndex, lastIndex = steps.lastIndex, tick = tick)
                            }
                        } else {
                            RowEndIcon(PageIcons.Selector)
                        }
                    },
                    modifier = Modifier.preciseClickAnchor(rowPreciseClick).gestureProbe(probe),
                    visible = item.state.targetState,
                    visibleState = item.state,
                )
            } + stepAddRow(steps = steps, sorting = sorting, probe = probe) { stepSheet = STEP_PICK_APPEND },
    )

    // 这一页的「挑一步」只有这一支抽屉 —— 两条入口共用（追加 / 换一步），由 [stepSheet] 说清是哪一种。
    //  挂在这里而不是某一行里：它是浮层，与宿主位置无关，而按行挂会在行退场时连抽屉一起被带走。
    val sheetAt = stepSheet
    // 打开时那一刻的「当前这一步」是哪条动作（换一步才有；追加时谁都不打勾）
    val currentAction =
        sheetAt
            ?.takeIf { it >= 0 }
            ?.let { at ->
                steps
                    .getOrNull(at)
                    ?.let { runCatching { ActionCatalog.actionOf(it) }.getOrNull() }
            }
    StepPickerSheet(
        //  **只有「当前页就是命令页」时才画**：抽屉是压在这一页上的浮层，而这一页的组合在
        //  压了「选择应用」之后仍可能被重建（`resumeSheetAt` 那一手正是为重建准备的）。
        //  只判 `sheetAt != null` 的话，重建那一刻抽屉会**画在选择应用页上面**
        //  （真机口径「从组件界面点确认返回到选择页，抽屉就显示出来」）。
        //  回到命令页时 `screen` 变回 COMMANDS，它才该出现 —— 那也正是「回来把抽屉支起来」。
        show = sheetAt != null && screen == Screen.COMMANDS,
        sheet =
            StepSheetSpec(
                title =
                    if (sheetAt != null && sheetAt >= 0) {
                        I18n.get(Strings.COMMAND_STEP_REPLACE_TITLE, "选择命令")
                    } else {
                        I18n.get(Strings.COMMAND_ADD, "添加命令")
                    },
                current = currentAction,
                //  两行关卡：都只是「收抽屉 → 压一页」，区别只在去哪一页。
                //  压页之前把「回来要开哪一档」记在页面级（这一页的组合会被注销）——没挑成、
                //  直接返回时抽屉原样支起来；挑成了由落盘那一路把这笔账清掉（见 `commitVarOp`）。
                //  `STEP_PICK_APPEND` 表示这一趟是追加，回来照旧从末尾加一步。
                onOpenPicker = { page ->
                    val at = if (sheetAt != null && sheetAt >= 0) sheetAt else STEP_PICK_APPEND
                    //  压页之前把「回来要开哪一档」记在页面级（这一页的组合会被注销）——没挑成、
                    //  直接返回时抽屉原样支起来；挑成了由落盘那一路把这笔账清掉（见 `commitStep`）。
                    picker.resumeSheetAt.value = ResumeSheet(Screen.COMMANDS, at)
                    when (page) {
                        StepPickerPage.App -> openAppPickerForStep(at)
                        StepPickerPage.Vars -> openVarOpsForStep(at)
                    }
                },
                //  这一支**只由「确定」调**（点行只换高亮）：确定那一刻才真正加一步 / 换掉这一步。
                onPick = { token ->
                    if (sheetAt != null && sheetAt >= 0) replaceStep(sheetAt, token) else addStep(token)
                },
                onDismiss = { stepSheet = null },
                //  正在编辑的就是这条命令：候选里不再列它（引用自己＝环，执行侧只会报「套太深」）。
                //  **两个名字都排除**：草稿名是用户刚改的、库里那条还叫 `editingName` —— 只排除草稿名
                //  的话，旧名字那一行仍在候选里，选中它会把旧命令塞进新命令的步骤（见 `excludeTokens`）。
                filter =
                    StepSheetFilter(
                        excludeTokens =
                            setOfNotNull(
                                Command(commandDraft.draft.name, commandDraft.draft.kind).token,
                                commandDraft.draft.editingName?.let { Command(it, commandDraft.draft.kind).token },
                            ),
                    ),
            ),
    )
}

/** 退场动画播完之后再摘的余量 —— 与 `HintQueue` 的 `EXIT_SETTLE_MS` 同一个意思（早摘一帧就是硬切）。 */
private const val STEP_EXIT_SETTLE_MS = 40L

/**
 * 步骤卡最下面那行「添加命令」—— 同样是通用的 [CardRow.Item]。
 *
 *  满员 / 排序态两档禁用的说法走 `disabledHint`（那一行自己的限制，与 `CardRow.Item` 的读法一致），
 *  点开的动作交给调用点。
 *
 * @param onAdd 点开「挑一步」抽屉（追加模式）
 */
@Composable
private fun SettingsPage.stepAddRow(
    steps: List<String>,
    sorting: Boolean,
    probe: GestureProbe,
    onAdd: () -> Unit,
): CardRow {
    val stepsFull = steps.size >= Command.MAX_STEPS
    //  两种点不动的情形各说一句：排序态下「先退出排序」，满员时说「最多几步」。
    //  **文案先算在 remember 外面**：它跟着语言档变，缓存在 `remember` 里会在切语言后留着旧语言那一句。
    val blockedHint =
        if (sorting) {
            I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED, "编辑中，先点右下角完成")
        } else {
            I18n.format(Strings.TOAST_COMMAND_STEP_FULL, Command.MAX_STEPS)
        }
    return CardRow.Item(
        title = I18n.get(Strings.COMMAND_ADD, "添加命令"),
        summary = I18n.get(Strings.COMMAND_STEP_SELECT_TITLE, "从内置命令与自定义命令里选择"),
        icon = PageIcons.StepAdd,
        trailing = { RowEndIcon(PageIcons.Selector) },
        enabled = !stepsFull && !sorting,
        disabledHint = blockedHint,
        onClick = {
            // 诊断：这一下是点出来的还是滑出来的（见 [GestureProbe]）
            XLog.w("添加命令：行被点开 —— ${probe.describe()}")
            onAdd()
        },
        modifier = Modifier.gestureProbe(probe),
    )
}

/**
 * 排序态下步骤行行尾那三颗行内按钮 —— 上移 / 下移 / 删除。
 *
 *  只在排序态出现（见 `CommandDraftState.stepEditing`）：只读态的行尾是空的，换命令靠轻点整行。
 *  首行 / 末行那一颗按 `enabled` 置灰，被点一下时说清为什么 —— 每颗各带一句：外层
 *  「添加命令」那层说的是「最多几步」，套在这里会答错问题。
 */
@Composable
private fun SettingsPage.StepRowButtons(
    index: Int,
    lastIndex: Int,
    tick: () -> Unit,
) {
    //  每一颗被点不动时都说**自己那一句**：外层「添加命令」那层说的是「最多几步」，
    //  套在这里会答错问题 —— 所以各带一句，投递口在 [DisabledHint]。
    DisabledHint(I18n.get(Strings.TOAST_COMMAND_STEP_FIRST, "已经是第一步")) {
        IconAction(
            PageIcons.StepUp,
            I18n.get(Strings.COMMON_MOVE_UP, "上移"),
            enabled = index > 0,
        ) {
            tick()
            moveStep(index, -1)
        }
    }
    DisabledHint(I18n.get(Strings.TOAST_COMMAND_STEP_LAST, "已经是最后一步")) {
        IconAction(
            PageIcons.StepDown,
            I18n.get(Strings.COMMON_MOVE_DOWN, "下移"),
            enabled = index < lastIndex,
        ) {
            tick()
            moveStep(index, 1)
        }
    }
    //  删除这颗是**危险项**：颜色走 `IconAction` 的 `error` 通道（与浮层菜单里
    //  `MenuEntry.Row(danger = true)` 同一档），而不是普通图标的次级色。
    //  它恒可用，所以不需要 [DisabledHint]（那一支存在的意义是「禁用时说一句」）。
    IconAction(
        PageIcons.CommandDelete,
        I18n.get(Strings.COMMON_DELETE, "删除"),
        error = true,
    ) {
        tick()
        // 从 steps 里立即移除（数据层马上正确）；展示层这一行由差分同步标成退场，shrink 完自己摘除。
        //
        //  `index` 是**这一次组合**算出来的下标：越界就当场忽略并说清是哪一步 ——
        //  抛在点击回调里就是宿主页面崩掉。
        //  动的是**多条那一份**表单（与 `SerialSteps` 同一个口径，不跟 `draft.kind` 走）
        val steps = commandDraft.draft.serialForm.steps
        if (index in steps.indices) {
            steps.removeAt(index)
        } else {
            XLog.w("多条命令：要删的第 ${index + 1} 步已经不在（现在共 ${steps.size} 步），这次点击忽略")
        }
    }
}

/** 步骤上移 / 下移一位（[delta] 为 -1 / +1）；越界不动。 */
internal fun SettingsPage.moveStep(
    index: Int,
    delta: Int,
) {
    val target = index + delta
    //  同上：只动「多条」那一份（`draft.steps` 会跟着 `draft.kind` 的镜像走）
    val steps = commandDraft.draft.serialForm.steps
    if (index !in steps.indices || target !in steps.indices) return
    steps.add(target, steps.removeAt(index))
}
