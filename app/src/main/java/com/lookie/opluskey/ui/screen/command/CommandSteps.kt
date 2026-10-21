package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.IconAction
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.gestureProbe
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.STEP_ROW_ENTER
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.openAppPickerForStep
import com.lookie.opluskey.ui.rememberGestureProbe
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.replaceStep
import com.lookie.opluskey.ui.state.ResumeSheet
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick
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
    val steps = commandDraft.draft.steps
    // 开着的是哪一行的**行尾菜单**（上移 / 下移 / 删除，见 [StepRowMenuPopup]）：`null` = 没开。
    //  与 [stepSheet] 分开：一个是「换这一步」（抽屉）、一个是「动这一行」（浮层菜单）。
    var stepMenu by remember { mutableStateOf<Int?>(null) }
    // 底部抽屉开着没有，以及它是**哪种来处**（见 `StepPickerSheet`）：
    //  `null` = 没开、[STEP_PICK_APPEND] = 追加一步、`>= 0` = 换掉那一步 ——
    //  「添加命令」与「点已添加的步骤」两条入口共用这一支。
    //  初值取页面级那一位：压一页「选择应用」时这一页的组合被注销，`remember` 回到初值，
    //  回来靠 `picker.resumeSheetAt` 把抽屉原样支起来，用户不用再点一次。
    var stepSheet by remember {
        mutableStateOf(
            picker.resumeSheetAt.value
                ?.takeIf { it.page == Screen.COMMANDS }
                ?.index,
        )
    }
    LaunchedEffect(Unit) { picker.resumeSheetAt.value = null }
    // 这张卡的手势账本：抓「滑动时行上的点击照样派发」那种偶发现场（见 [GestureProbe]）。
    //  挂整张卡而不是每一行 —— 卡里的行都在这条手势范围里，且它比行的 `clickable` 更早拿到事件。
    val probe = rememberGestureProbe("步骤卡")

    // 行级动画的展示层：`draft.steps` 的镜像，每行一个稳定 id。steps 每次变化（增 / 删 / 换 / 移）
    // 在这里差分对齐：还在的按多重集匹配保留、匹配不上的播退场、缺的建新条目播入场。
    // 删除立即改 `draft.steps`（数据层马上正确），动画只活在展示层。
    val display = remember { mutableStateListOf<StepRowItem>() }
    var nextId by remember { mutableIntStateOf(0) }

    //  `toList()` **只算一次**：它既是下面那个 effect 的 key，也是差分要用的那份快照。
    //  原来 key 与函数体各算一遍 —— 每遍都要读完整张 `SnapshotStateList` 再新建一个 List。
    val stepsNow = commandDraft.draft.steps.toList()
    LaunchedEffect(stepsNow) {
        // 列表一变，行下标就不一定还落在原来那一行上（插入 / 删除 / 上移下移都挪位）——
        //  抽屉是按下标做事的，**那一步已经不在了**才收（用户口径「选中命令不关闭抽屉」：
        //  换一步本身就会改这个列表，一律收掉的话抽屉每选一次就没了）。
        stepSheet?.let { at -> if (at >= 0 && at !in stepsNow.indices) stepSheet = null }
        //  行尾菜单同理：它是按**行 id**开着、但里面那三行算的是下标的活（上移 / 下移 / 删除），
        //  列表一变就得收 —— 不收的话菜单还开着、点「删除」删的却是挪位之后的那一行。
        if (stepMenu != null) stepMenu = null
        // 未退场的展示行，按展示顺序与 steps 做多重集匹配（同 token 按先后配对）
        val kept = display.filter { !it.exiting }
        val matched = arrayOfNulls<StepRowItem>(stepsNow.size)
        val pool = kept.toMutableList()
        stepsNow.forEachIndexed { si, token ->
            val i = pool.indexOfFirst { it.token == token }
            if (i >= 0) matched[si] = pool.removeAt(i)
        }
        // 没匹配上的 → 退场；锚 = 前面最近的保留行（收缩期间插回它下面）
        var prevId = -1
        kept.forEach { row ->
            if (matched.contains(row)) {
                prevId = row.id
            } else if (!row.exiting) {
                row.exiting = true
                row.anchorId = prevId
                row.state.targetState = false
            }
        }
        // 重建展示顺序：steps 顺序放匹配行（缺的建新行），其间插入挂锚的退场行
        val exitingByAnchor = display.filter { it.exiting }.groupBy { it.anchorId }
        val newOrder = mutableListOf<StepRowItem>()
        newOrder += exitingByAnchor[-1].orEmpty().sortedBy { it.id }
        stepsNow.forEachIndexed { si, token ->
            val m = matched[si]
            if (m == null) {
                newOrder += StepRowItem(nextId++, token).also(display::add)
            } else {
                newOrder += m
            }
            newOrder += exitingByAnchor[m?.id].orEmpty().sortedBy { it.id }
        }
        display.clear()
        display.addAll(newOrder)
    }

    // 整卡重置的挂起落到每一行 → 同样播退场（reset 本体由页面级协程执行）
    LaunchedEffect(commandDraft.resetPending) {
        // 注意：必须**连 `exiting` 一起置**：只置 `targetState` 的话行会淡出，
        //  但摘除那道闸（下面 `if (item.exiting && …)`）永远不成立 —— 而摘除 effect 的 key 是
        //  `item.state.currentState`，它在这里已经变成 false、之后再不会变 ⇒ 那些行**永留展示层**：
        //  分隔线一直画着、`cardListPositionOf(index, display.size + 1)` 一直按旧行数算。
        if (commandDraft.resetPending) {
            display.forEach {
                it.exiting = true
                it.state.targetState = false
            }
        }
    }

    // 收尾被放弃（[CommandDraftState.resetAborted]）⇒ 把上面标掉的行**收回来**：它们本来会被摘除，而
    //  `draft.steps` 没变又不会重建 ⇒ 步骤在界面上凭空消失（数据还在，看不到也删不掉）。
    LaunchedEffect(commandDraft.resetAborted) {
        if (commandDraft.resetAborted) {
            display.forEach {
                if (it.exiting) {
                    it.exiting = false
                    it.anchorId = -1
                    it.state.targetState = true
                }
            }
            commandDraft.resetAborted = false
        }
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
                enter = STEP_ROW_ENTER,
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
                                tick()
                                XLog.w(
                                    "多条命令：点第 ${stepsIndex + 1} 步（弹选择菜单，替换模式）—— ${probe.describe()}",
                                )
                                //  抽屉是按**下标**做事（下移 / 删除 / 换这一步都算下标），而列表一变
                                //  下标就挪位 —— 所以上面那个 effect 会在列表变化时把抽屉收掉。
                                stepSheet = stepsIndex
                            },
                            // 行首图标 = 这一步是什么（内置动作取动作图标、`cmd:` 取**被引用那条命令**的类型图标）。
                            // 没有它标题会顶到卡片左边，与下面那行带 `+` 图标的「添加命令」差一个图标宽，
                            // 同一张卡里两行左边缘不齐。
                            startAction = { Icon(stepIcon(item.token, configState.commands.value), null, Modifier.size(ROW_ICON_SIZE)) },
                            endActions = {
                                // 行尾收成**一颗**竖三点（dots-vertical）：上移 / 下移 / 删除都挪进
                                //  它弹出来的菜单（[StepRowMenuPopup]）。三个按钮并排时占掉近半行宽，
                                //  标题那列被挤到只剩十来个字；收成一颗菜单之后宽度还给标题。
                                IconAction(
                                    PageIcons.Menu,
                                    I18n.get(Strings.COMMON_MORE, "更多"),
                                    enabled = !item.exiting,
                                ) {
                                    tick()
                                    XLog.w("多条命令：点第 ${stepsIndex + 1} 步的行尾菜单")
                                    stepMenu = item.id
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
                        // 浮层挂在零宽内层盒子里（落点由触点决定，这里只是它的宿主）。
                        Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(0.dp)) {
                            // 换这一步的界面**不在这一行了**：两条入口（点整行 / 点「添加命令」）
                            //  统一走抽屉（见文件末尾那个 `StepPickerSheet`，用户口径
                            //  「命令列表也换成这个抽屉」）。
                            // 行尾竖三点那颗弹出来的菜单 —— 上移 / 下移 / 删除（原先行尾那三个按钮）
                            StepRowMenuPopup(
                                show = stepMenu == item.id,
                                preciseClick = rowPreciseClick,
                                spec =
                                    StepRowMenuSpec(
                                        canMoveUp = !item.exiting && stepsIndex > 0,
                                        canMoveDown = !item.exiting && stepsIndex < steps.lastIndex,
                                        onMoveUp = {
                                            tick()
                                            moveStep(stepsIndex, -1)
                                        },
                                        onMoveDown = {
                                            tick()
                                            moveStep(stepsIndex, 1)
                                        },
                                        onDelete = {
                                            tick()
                                            // 从 steps 里立即移除（数据层马上正确）；展示层这一行由差分同步标成退场，shrink 完自己摘除。
                                            //
                                            //  `stepsIndex` 是**这一次组合**算出来的下标：菜单开着这段时间列表也可能被
                                            //  别处改过（上面那个 effect 会收菜单，但同一帧里仍可能撞上），越界就当场忽略、
                                            //  说清是哪一步 —— 抛在点击回调里就是宿主页面崩掉。
                                            if (stepsIndex in steps.indices) {
                                                steps.removeAt(stepsIndex)
                                            } else {
                                                XLog.w(
                                                    "多条命令：要删的第 ${stepsIndex + 1} 步已经不在" +
                                                        "（现在共 ${steps.size} 步），这次点击忽略",
                                                )
                                            }
                                        },
                                    ),
                            ) { stepMenu = null }
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
    // 正常态那一档就是外层的 `onSurfaceContainer`（`contentColor`）；要给过渡就得把两档都写出来。
    //  标题与副标题**不再自己上色**：改走库的 `title` / `summary` 槽之后，置灰由 `enabled` 管
    //  （与「捐赠打赏」那些行同一套，见 `CardRow.Item`）。
    val addIconTint by animateColorAsState(
        targetValue =
            if (stepsFull) {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            } else {
                COUITheme.colorScheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "StepAddIcon",
    )

    // 整行挂上手指锚点 —— 浮层弹在按下那一点（只记不看，行自己的点击照常）。
    //  满员时点这一行说清「最多几步」：整段覆盖 `LocalDisabledClick`，行本身照旧置灰
    CompositionLocalProvider(
        LocalDisabledClick provides {
            hint(I18n.format(Strings.TOAST_COMMAND_STEP_FULL, Command.MAX_STEPS))
        },
    ) {
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
                // 行尾那颗选择器指示（见 [PageIcons.Selector]）—— 这一行点了弹选择器。
                //  **步骤行已经没有这一颗了**（那里的行尾只剩竖三点菜单）；这一行留着：
                //  它没有菜单按钮，整行可点这件事得有个视觉交代。
                //  尺寸取 [ROW_END_ICON_SIZE]（24dp）—— 与「捐赠打赏」那一行的行尾同一档，
                //  不再套 `ICON_ACTION_SIZE` 那颗按钮大的盒子。
                //   tint 走最弱那一档（`onSurfaceVariantActions`），比行首那颗 `+` 弱；不给满员另开一档，
                //   与 `disabledOnSecondaryVariant` **恰好同值**（浅色 0x42 / 深色 0x4D），分了也是同一个颜色。
                endActions = {
                    Icon(
                        PageIcons.Selector,
                        contentDescription = null,
                        modifier = Modifier.size(ROW_END_ICON_SIZE),
                        tint = COUITheme.colorScheme.onSurfaceVariantActions,
                    )
                },
                // `enabled = false` 时 COUI 不挂 `clickableModifier` ⇒ 点不进去，浮层也就打不开。
                enabled = !stepsFull,
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
                commandDraft.draft.steps
                    .getOrNull(at)
                    ?.let { runCatching { Action.parse(it) }.getOrNull() }
            }
    StepPickerSheet(
        show = sheetAt != null,
        sheet =
            StepSheetSpec(
                title =
                    if (sheetAt != null && sheetAt >= 0) {
                        I18n.get(Strings.COMMAND_STEP_REPLACE_TITLE, "选择命令")
                    } else {
                        I18n.get(Strings.COMMAND_ADD, "添加命令")
                    },
                current = currentAction,
                onOpenApp = {
                    //  压一页之前把「回来要开哪一档」记在页面级（这一页的组合会被注销）
                    val at = if (sheetAt != null && sheetAt >= 0) sheetAt else STEP_PICK_APPEND
                    picker.resumeSheetAt.value = ResumeSheet(Screen.COMMANDS, at)
                    openAppPickerForStep(at)
                },
                //  这一支**只由「确定」调**（点行只换高亮）：确定那一刻才真正加一步 / 换掉这一步。
                onPick = { token ->
                    if (sheetAt != null && sheetAt >= 0) replaceStep(sheetAt, token) else addStep(token)
                },
                onDismiss = { stepSheet = null },
            ),
    )
}

/** 步骤上移 / 下移一位（[delta] 为 -1 / +1）；越界不动。 */
internal fun SettingsPage.moveStep(
    index: Int,
    delta: Int,
) {
    val target = index + delta
    if (index !in commandDraft.draft.steps.indices || target !in commandDraft.draft.steps.indices) return
    commandDraft.draft.steps.add(target, commandDraft.draft.steps.removeAt(index))
}
