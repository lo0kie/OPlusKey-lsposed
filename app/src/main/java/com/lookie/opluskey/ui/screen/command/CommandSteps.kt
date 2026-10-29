package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.IconAction
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.gestureProbe
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.STEP_ROW_ENTER
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.openAppPickerForStep
import com.lookie.opluskey.ui.rememberGestureProbe
import com.lookie.opluskey.ui.rememberRowIconColor
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.replaceStep
import com.lookie.opluskey.ui.screen.openVarOpsForStep
import com.lookie.opluskey.ui.state.ResumeSheet
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

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

    //  这一支组合是「刚进来」还是「已经在页上了」：进来的**第一帧**不播入场动画。
    //
    //  展示层住在页面级、行对象也还是原来那些，可切 tab 时这一页的组合会被注销 ——
    //  回来重新组合时 `AnimatedVisibility` 在**新的**组合里仍会把整张表从头展开一遍
    //  （用户口径「每次切到多条命令，列表都重新展开」）。这里只放行「进来之后才新增的行」：
    //  第一帧把所有已有行直接摆到位，之后插入 / 删除仍走 [STEP_ROW_ENTER] / [STEP_ROW_EXIT]。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        //  等这一帧画完再放开：第一帧里出现的行（含 `sync` 刚补上的那些）一律直接到位
        withFrameNanos { }
        entered = true
    }

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

    display.forEachIndexed { index, item ->
        // 每行按 `item.id` 认身份 —— 展示层每轮都 `clear()` 再按新次序重填，槽位就是**位次**：
        //  不套 key 的话，循环体里那个逐行的 `LaunchedEffect`（负责退场播完摘除）与
        //  `AnimatedVisibility` 都挂在位次上，增删 / 上移下移之后会落到另一条命令身上 ——
        //  留幽灵行、分隔线错位（行内那份 `remember(item.id)` 只护住 PreciseClickState，护不到这一层）。
        //  写法同 `BindingScreen` 里的 `key(spec.token)`。
        key(item.id) {
            // 退场播完（currentState 收到 false）→ 从展示层摘除；此刻行高已收到 0，下面的行不会跳
            LaunchedEffect(item.state.currentState) {
                if (item.exiting && !item.state.currentState && !item.state.targetState) {
                    display.remove(item)
                }
            }
            AnimatedVisibility(
                visibleState = item.state,
                //  刚进来那一帧直接到位，之后新增的行才播展开（见上面 `entered`）
                enter = if (entered) STEP_ROW_ENTER else EnterTransition.None,
                exit = STEP_ROW_EXIT,
            ) {
                Column {
                    // 卡片顶边本身就是边界，再画一条就多出来；分隔线的职责只有「行与行之间」。
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
                    // 每一行各自一份 `PreciseClickState`（按行 id 做 key）：`anchorCoordinates` 只有一格，
                    // 共用一份会把触点换算到别的行上去。
                    val rowPreciseClick = remember(item.id) { PreciseClickState() }
                    // 这一行在 steps 里的下标：展示层里排在它前面的未退场行数 ——
                    // 退场行还在展示层里占位（收缩中），但已经不在 steps 里了。
                    val displayIndex = display.indexOf(item)
                    val stepsIndex = display.take(displayIndex).count { !it.exiting }
                    Box(Modifier.fillMaxWidth().preciseClickAnchor(rowPreciseClick).gestureProbe(probe)) {
                        BasicComponent(
                            cardListPosition = cardListPositionOf(index, display.size + 1),
                            // 轻点整行 = 弹选择菜单（挑一个替换这一步）；行尾只剩一颗竖三点（更多菜单）。
                            onClick = {
                                if (item.exiting) return@BasicComponent
                                // 排序态下这一行只让位给行尾那三颗按钮：轻点整行不再换命令，
                                //  否则「改顺序」与「换这一步」两种意图会撞在同一下手上。
                                if (sorting) return@BasicComponent
                                tick()
                                XLog.w(
                                    "多条命令：点第 ${stepsIndex + 1} 步（弹选择菜单，替换模式）—— ${probe.describe()}",
                                )
                                //  抽屉是按**下标**做事（换这一步算下标），而列表一变
                                //  下标就挪位 —— 所以上面那个 effect 会在列表变化时把抽屉收掉。
                                stepSheet = stepsIndex
                            },
                            // 行首图标 = 这一步是什么（内置动作取动作图标、`cmd:` 取**被引用那条命令**的类型图标）。
                            // 没有它标题会顶到卡片左边，与下面那行带 `+` 图标的「添加命令」差一个图标宽，
                            // 同一张卡里两行左边缘不齐。
                            startAction = { Icon(stepIcon(item.token, configState.commands.value), null, Modifier.size(ROW_ICON_SIZE)) },
                            endActions = {
                                // 只读态行尾与「添加命令」那一行**同一颗**选择器指示：整行可点、
                                //  点了弹抽屉换这一步，这件事得有视觉交代。
                                //  排序态换成上移 / 下移 / 删除三颗（原来那颗竖三点菜单已经撤掉）。
                                if (sorting) {
                                    if (!item.exiting) {
                                        StepRowButtons(
                                            index = stepsIndex,
                                            lastIndex = steps.lastIndex,
                                            tick = tick,
                                        )
                                    }
                                } else {
                                    RowEndIcon(PageIcons.Selector)
                                }
                            },
                        ) {
                            // 标题自己画：`BasicComponent(title = String)` 那个重载不给 `maxLines`，标题长了会折行，
                            // 而这一列宽度由行内布局定死（≈173dp，≈13 个汉字格）。样式照抄库的 `BasicComponent`：
                            // 标题 `headline1` + Medium + `onBackground`，摘要 `body2` + `onSurfaceVariantSummary`，
                            // 与标题隔 2dp。
                            Text(
                                text = stepTitle(item.token, ::appNameOf),
                                style = COUITheme.textStyles.headline1,
                                fontWeight = FontWeight.Medium,
                                color = COUITheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = I18n.format(Strings.COMMON_STEP_INDEX, stepsIndex + 1),
                                modifier = Modifier.padding(top = 2.dp),
                                style = COUITheme.textStyles.body2,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
    // 没有步骤时它就成了卡片里的第一条，所以那时不画 —— 它的职责是把步骤与下面那行
    // 「添加命令」分开，没有步骤就没有要分的东西。
    //  注意：判据取**展示层**（`display`）而不是数据（`steps`）：退场行在动画期间还留在展示层里、
    //  还在占位，那条线要陪着它一起走完；按数据算的话行才刚开始缩、线先没了（少一档分隔）。
    if (display.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))

    // 置灰不改变行数（这一行还在，只是不吃点击），所以上面的 cardListPosition 不用动。
    val stepsFull = steps.size >= Command.MAX_STEPS
    // 行首那颗 `+` 的颜色随「满没满」切换、带过渡 —— 否则第 5 步一加它是「啪」地灰下去的。
    //  两档色走全项目那一对（[rememberRowIconColor]）：禁用态与 `IconAction` 的禁用色同一档，
    //  正常态那一档就是外层的 `onSurfaceContainer`（`contentColor`）。
    //  标题与副标题**不再自己上色**：改走库的 `title` / `summary` 槽之后，置灰由 `enabled` 管
    //  （与「捐赠打赏」那些行同一套，见 `CardRow.Item`）。
    val addIconTint by rememberRowIconColor(enabled = !stepsFull && !sorting, label = "StepAddIcon")

    // 整行挂上手指锚点 —— 浮层弹在按下那一点（只记不看，行自己的点击照常）。
    //  两种点不动的情形各说一句：排序态下「先退出排序」，满员时说「最多几步」。
    //  **文案先算在 remember 外面**：它跟着语言档变，缓存在 `remember(sorting)` 里会在切语言后
    //  留着旧语言那一句（`tools/check-i18n-cache.py` 盯的就是这个）；lambda 与语义位都收在 `DisabledHint` 里。
    val addBlockedHint =
        if (sorting) {
            I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED, "编辑中，先点右下角完成")
        } else {
            I18n.format(Strings.TOAST_COMMAND_STEP_FULL, Command.MAX_STEPS)
        }
    DisabledHint(addBlockedHint) {
        Box(Modifier.fillMaxWidth().gestureProbe(probe)) {
            BasicComponent(
                //  注意：位置按**数据**（`draft.steps`）算，不按展示层行数（原 `cardListPositionOf(display.size, display.size + 1)`）：
                //  退场行在动画期间仍留在 `display` 里，按展示层算会让这一行停在 `Tail`，**等退场动画播完、
                //  那行被摘除**才翻成 `Full` —— 而库给圆角边的额外内边距是 `HeadOrTailPadding`（2dp，
                //  且行最小高度同步抬高），`Full` 比 `Tail` 多一份**顶边** ⇒ 卡片高度在动画结束那一刻
                //  又跳 4dp。真机逐帧量过：底边从 2058 一路收到 1847 后**又弹回 1854**，就是这一跳，
                //  看着像「加号行先移过头、再被拉回来」。
                //  按数据算 ⇒ 翻档发生在过渡**开始**那一帧，与行高动画同时进行，看不出来。
                cardListPosition =
                    if (steps.isEmpty()) {
                        CardListPosition.Full
                    } else {
                        CardListPosition.Tail
                    },
                // 标题与副标题走库的两个槽（不再自己画 `Text`）—— 与首页「捐赠打赏」那一行
                //  （`CardRow.Item`）同一套排版：标题 `headline1`、副标题 `body2`、间隙由库给，
                //  置灰也由 `enabled` 负责。
                title = I18n.get(Strings.COMMAND_ADD, "添加命令"),
                summary = I18n.get(Strings.COMMAND_STEP_SELECT_TITLE, "从内置命令与自定义命令里选择"),
                // 行首 `+` 的 tint 由上面 `addIconTint` 给（两档都显式写出来，为了过渡）：禁用态取
                // `disabledOnSecondaryVariant`（库 `disabledColor` 的取值，与 `IconAction` 禁用色一致），
                // 正常态取 `onSurfaceContainer`（外层 `contentColor`）。正常态不能用 `Color.Unspecified`
                // 顶替 —— 那会变成完全不上色。
                startAction = {
                    Icon(PageIcons.StepAdd, null, Modifier.size(ROW_ICON_SIZE), tint = addIconTint)
                },
                // 行尾那颗选择器指示 —— 见 [RowEndIcon]：步骤行与这一行**同一颗**。
                endActions = { RowEndIcon(PageIcons.Selector) },
                // `enabled = false` 时 COUI 不挂 `clickableModifier` ⇒ 点不进去，浮层也就打不开。
                enabled = !stepsFull && !sorting,
                onClick = {
                    tick()
                    // 诊断：这一下是点出来的还是滑出来的（见 [GestureProbe]）
                    XLog.w("添加命令：行被点开 —— ${probe.describe()}")
                    // **临时**：改成弹底部抽屉（原来 `stepPicker = STEP_PICK_APPEND`）
                    stepSheet = STEP_PICK_APPEND
                },
            )
        }
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
                excludeTokens =
                    setOfNotNull(
                        Command(commandDraft.draft.name, commandDraft.draft.kind).token,
                        commandDraft.draft.editingName?.let { Command(it, commandDraft.draft.kind).token },
                    ),
            ),
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
