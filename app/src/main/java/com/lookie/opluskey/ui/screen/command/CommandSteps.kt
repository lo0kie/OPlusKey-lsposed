package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.GestureProbe
import com.lookie.opluskey.ui.ICON_ACTION_SIZE
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.gestureProbe
import com.lookie.opluskey.ui.model.CARD_GAP
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
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
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
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
    //  `visible` 那一位只用来算行位置：退场行（`targetState` 已 false）立刻从「这一张卡的行数」里
    //  退出去 —— 否则它退场期间末行仍是 `Middle`，卡片末尾那两个圆角要等退场播完才回来。
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

    //  **步骤行交给 [CardList]**，每一行就是通用的 [CardRow.Item] —— 卡片、分隔线、行位置、
    //  图标与文字的间距都由它给，这里只填「这一行是什么」。
    //  行的进出场仍用这一页自己的状态（`item.state`，规格见 `STEP_ROW_ENTER` / `STEP_ROW_EXIT`）：
    //  以 `visibleState` 传进去，`CardList` 就按它播，不会再用布尔重播一遍。
    //
    //  「添加命令」**不在这一张卡里**（用户口径「所有的添加行都另起一个卡片」）：
    //  上面那张是「这一步一步做什么」，那一行是往末尾**添**一步 —— 同卡时读起来像
    //  「最后一步叫添加命令」，而它跟哪一步都不并列。
    Column(Modifier.fillMaxWidth()) {
        val stepRows =
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
                    //  **不限行**（用户口径「命令列表的命令列主标题多行显示」）：这一行写的是
                    //  `打开应用: 微信 - LauncherUI` 这种整句，单行会把「做什么」裁掉一半 ——
                    //  而这一步做的正是核对它做什么。行高由内容撑开，与条件 / 动作那两行同一口径。
                    titleMaxLines = Int.MAX_VALUE,
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
            }
        //  一步都没有时**不摆空卡**：`CardList` 收到空表仍会画一条带内边距的窄条。
        if (stepRows.isNotEmpty()) {
            CardList(rows = stepRows)
            Spacer(Modifier.height(CARD_GAP))
        }
        CardList(
            rows = listOf(stepAddRow(steps = steps, sorting = sorting, probe = probe) { stepSheet = STEP_PICK_APPEND }),
        )
    }

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
                    //  抽屉**先收掉**：留着的话它一直挂在下面，挑成了回来也照样弹出来
                    //  （用户口径「点了确认，抽屉还在」）。回来要不要重开由上面那条
                    //  `LaunchedEffect(screen)` 按这笔账决定 —— 挑成了账已被清，就不开。
                    stepSheet = null
                    when (page) {
                        StepPickerPage.App -> openAppPickerForStep(at)
                        StepPickerPage.AppStop -> openAppPickerForStep(at, stop = true)
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
 * 排序态行尾三颗按钮之间的间距 —— 4dp。
 *
 *  三颗各 32dp 的圆形触达区紧挨着时，两个高亮圈会相切成一条缝，看着像一个控件的三段；
 *  4dp 足够把三颗读成三个独立按钮，又不至于把「上移 → 下移 → 删除」拆到要跨半行去点。
 *  行尾槽本身不给间距（库那层只把它们包进一条 `Row`），所以这一档只能由调用点给。
 */
private val STEP_BUTTON_GAP = 4.dp

/**
 * 三颗按钮收紧之后的下限 —— 28dp。
 *
 *  再小（24dp 以下）就低于手指能稳点的范围，误触会比「挤一点」更让人恼火。
 *  真到放不下这一步时，宁可让三颗略微贴边，也不再往下缩。
 */
private val STEP_BUTTON_MIN_SIZE = 28.dp

/**
 * 三颗按钮这一格能用的边长 —— 按**行尾槽给的实际宽度**倒推（理由见 [StepRowButtons]）。
 *
 *  可用宽度不足 `3 × 32 + 2 × 间距` 时整体收紧到「三等分扣掉间距」；
 *  下限 [STEP_BUTTON_MIN_SIZE]，再往下就不缩了（触达区太小反而点不准）。
 */
private fun stepButtonSize(available: Dp): Dp {
    val roomy = ICON_ACTION_SIZE * 3 + STEP_BUTTON_GAP * 2
    //  还没量出来（首帧，见 [StepRowButtons]）按「放不下」算：先缩一档，量到之后再放开。
    if (available <= 0.dp) return STEP_BUTTON_MIN_SIZE
    if (available >= roomy) return ICON_ACTION_SIZE
    val share = (available - STEP_BUTTON_GAP * 2) / 3
    return share.coerceIn(STEP_BUTTON_MIN_SIZE, ICON_ACTION_SIZE)
}

/**
 * 一颗行尾图标键现在是什么档 —— 只决定**禁用与否**与**用哪一档颜色**（边长由调用方另给）。
 *
 *  三档互斥：一颗按钮不会同时又是禁用又是危险项（禁用的删除键不存在，删除恒可用）。
 *  合成一个枚举而不是两个布尔参数：那个函数的参数一多就过 detekt 的 `LongParameterList`，
 *  而 `enabled = true, error = false` 这种调用点本来也读不出「这颗是普通键」。
 */
private enum class StepButtonTone {
    /** 普通可用（上移 / 下移在能点的位置上）。 */
    NORMAL,

    /** 点不动，说清为什么由外层的 [DisabledHint] 负责。 */
    DISABLED,

    /** 危险项（删除）：走 `error` 通道，与浮层菜单里 `MenuEntry.Row(danger = true)` 同一档。 */
    DANGER,
}

/**
 * 一颗可调边长的行尾图标键 —— 与 [IconAction] 同一套观感（圆形高亮 + 图标居中），
 * 区别只在**触达区边长由调用方给**。
 *
 *  为什么不用 [IconAction]：它的边长写死在 [ICON_ACTION_SIZE]，且注释里明确不给 `modifier`
 *  （「尺寸自己定死」）。三颗挤在行尾槽里时必须能一起缩，所以在这一处另起一支；
 *  它只服务「一行里并排好几颗」这一种情形，别处仍是 [IconAction]。
 *
 *  边长用 `size()` 而不是库的 `defaultMinSize`：后者只是下限，被父级压窄时照样按内容溢出。
 */
@Composable
private fun StepButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    tone: StepButtonTone = StepButtonTone.NORMAL,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val color by animateColorAsState(
        targetValue =
            when (tone) {
                StepButtonTone.DISABLED -> scheme.disabledOnSecondaryVariant
                StepButtonTone.DANGER -> scheme.error
                StepButtonTone.NORMAL -> scheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "StepButton",
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        enabled = tone != StepButtonTone.DISABLED,
        cornerRadius = size / 2,
    ) {
        Icon(icon, contentDescription = label, Modifier.size(size * STEP_BUTTON_GLYPH_RATIO), tint = color)
    }
}

/**
 * 图标相对触达区的比例 —— 与 [IconAction] 那一对（32 / 20）同一个数，收紧之后图标跟着缩，
 * 不会出现「格子小了、图标还是原样」把高亮圈撑满的样子。
 */
private const val STEP_BUTTON_GLYPH_RATIO = 0.625f

/**
 * 「添加命令」—— 步骤卡下面那张**独立卡**里唯一的一行，同样是通用的 [CardRow.Item]。
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
 *
 *  ## 为什么三颗要并成一个 `Row`、还要按可用宽度收格子
 *
 *  库的 `BasicComponent` 把行尾槽限在**行宽的六成**（`endHardCap`，见 `.temp/coui-repo` 里
 *  `Component.kt` 那个 `Layout`），而且槽本身只是一条 `Row`、**不给间距**。三颗 32dp 的触达区
 *  加起来 96dp、再加两档间距就过了那条上限 —— 被压到槽宽之后每颗的 `defaultMinSize` 都撑不开，
 *  三颗叠在同一处，图标互相压住，只有最上面那颗点得到（真机口径「行尾图标重叠」）。
 *
 *  所以这一格按**实际可用宽度**倒推：先用 [ICON_ACTION_SIZE] 试，放得下就照原样；放不下就按
 *  `(可用宽 − 两档间距) / 3` 收紧边长（下限 [STEP_BUTTON_MIN_SIZE]，再小就点不准了）。
 *  三颗一起缩，不会出现「前两颗正常、最后一颗被压扁」。
 *
 *  ## 为什么这里**不能**用 `BoxWithConstraints`
 *
 *  库在量行尾槽之前会先问一次它的**最大固有宽度**（`Component.kt` 那个 `endMeasurable.maxIntrinsicWidth`，
 *  用来算 `endHardCap`）。`BoxWithConstraints` 建在 `SubcomposeLayout` 上，而 `SubcomposeLayout`
 *  被问固有尺寸时直接抛 `IllegalStateException`：「Asking for intrinsic measurements of SubcomposeLayout
 *  layouts is not supported」。于是只要切进排序态、行尾槽里长出这一格，这一帧量下去就崩
 *  （真机口径「多条命令的编辑态 crash」）。
 *
 *  换成 `Modifier.onSizeChanged` 取同一份宽度：它不参与固有尺寸计算，回调里拿到的就是库按
 *  `endHardCap` 定下来的**实际**槽宽 —— 与 `maxWidth` 同一个数，却不会在量固有宽度那一步炸掉。
 *  首帧还没回调时按「放不下」算（[STEP_BUTTON_MIN_SIZE]）：那一帧只有一次极短的收紧，看着不跳。
 */
@Composable
private fun SettingsPage.StepRowButtons(
    index: Int,
    lastIndex: Int,
    tick: () -> Unit,
) {
    var slotWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Row(
        modifier =
            Modifier.onSizeChanged {
                val width = with(density) { it.width.toDp() }
                //  只在这一格真的换了宽度时写回去：否则每帧量下来都是一次 `setValue` ⇒ 重组。
                if (width != slotWidth) slotWidth = width
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val size = stepButtonSize(available = slotWidth)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STEP_BUTTON_GAP),
        ) {
            //  每一颗被点不动时都说**自己那一句**：外层「添加命令」那层说的是「最多几步」，
            //  套在这里会答错问题 —— 所以各带一句，投递口在 [DisabledHint]。
            DisabledHint(I18n.get(Strings.TOAST_COMMAND_STEP_FIRST, "已经是第一步")) {
                StepButton(
                    icon = PageIcons.StepUp,
                    label = I18n.get(Strings.COMMON_MOVE_UP, "上移"),
                    size = size,
                    tone = if (index > 0) StepButtonTone.NORMAL else StepButtonTone.DISABLED,
                ) {
                    tick()
                    moveStep(index, -1)
                }
            }
            DisabledHint(I18n.get(Strings.TOAST_COMMAND_STEP_LAST, "已经是最后一步")) {
                StepButton(
                    icon = PageIcons.StepDown,
                    label = I18n.get(Strings.COMMON_MOVE_DOWN, "下移"),
                    size = size,
                    tone = if (index < lastIndex) StepButtonTone.NORMAL else StepButtonTone.DISABLED,
                ) {
                    tick()
                    moveStep(index, 1)
                }
            }
            //  删除这颗是**危险项**：颜色走 `error` 通道（与浮层菜单里 `MenuEntry.Row(danger = true)`
            //  同一档），而不是普通图标的次级色。
            //  它恒可用，所以不需要 [DisabledHint]（那一支存在的意义是「禁用时说一句」）。
            StepButton(
                icon = PageIcons.CommandDelete,
                label = I18n.get(Strings.COMMON_DELETE, "删除"),
                size = size,
                tone = StepButtonTone.DANGER,
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
