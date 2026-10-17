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
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.ICON_ACTION_ICON
import com.lookie.opluskey.ui.ICON_ACTION_SIZE
import com.lookie.opluskey.ui.IconAction
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.gestureProbe
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.STEP_ROW_ENTER
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.rememberGestureProbe
import com.lookie.opluskey.ui.rememberTick
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 多条命令的步骤列表（「步骤」那张卡的内容）。
 */
@Composable
internal fun SettingsPage.SerialSteps() {
    val tick = rememberTick()
    val steps = commandDraft.draft.steps
    var picking by remember { mutableStateOf(false) }
    val preciseClick = rememberPreciseClickState()
    var stepPick by remember { mutableStateOf(-1) }
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
                            // 轻点整行 = 弹选择菜单（挑一个替换这一步）；行尾三颗图标（上移 / 下移 / 删除）保留。
                            onClick = {
                                if (item.exiting) return@BasicComponent
                                tick()
                                XLog.w(
                                    "多条命令：点第 ${stepsIndex + 1} 步（弹选择菜单，替换模式）—— ${probe.describe()}",
                                )
                                stepPick = stepsIndex
                            },
                            // 行首图标 = 这一步是什么（内置动作取动作图标、`cmd:` 取**被引用那条命令**的类型图标）。
                            // 没有它标题会顶到卡片左边，与下面那行带 `+` 图标的「添加命令」差一个图标宽，
                            // 同一张卡里两行左边缘不齐。
                            startAction = { Icon(stepIcon(item.token, configState.commands.value), null, Modifier.size(ROW_ICON_SIZE)) },
                            endActions = {
                                IconAction(
                                    PageIcons.StepUp,
                                    I18n.get(Strings.COMMON_MOVE_UP, "上移"),
                                    enabled = !item.exiting && stepsIndex > 0,
                                ) {
                                    tick()
                                    moveStep(stepsIndex, -1)
                                }
                                IconAction(
                                    PageIcons.StepDown,
                                    I18n.get(Strings.COMMON_MOVE_DOWN, "下移"),
                                    enabled = !item.exiting && stepsIndex < steps.lastIndex,
                                ) {
                                    tick()
                                    moveStep(stepsIndex, 1)
                                }
                                IconAction(
                                    PageIcons.CommandDelete,
                                    I18n.get(Strings.COMMON_DELETE, "删除"),
                                    error = true,
                                    enabled = !item.exiting,
                                ) {
                                    tick()
                                    // 从 steps 里立即移除（数据层马上正确）；展示层这一行由差分同步标成退场，shrink 完自己摘除。
                                    steps.removeAt(stepsIndex)
                                }
                                // 行尾收一颗选择器指示 —— 这一行点了会弹选择器把这一步换掉（见 [PageIcons.Selector]）。
                                //  给的是**纯图标**、不是 [IconAction]：它不吃点击（弹选择器的是整行自己）。
                                //  外面套 `ICON_ACTION_SIZE` 的盒子，与前三个按钮的间距才一致 —— 那三个是可点按钮、
                                //  自带这么大一块触达区，只放 20dp 的图标会挤在一起。
                                Box(
                                    Modifier.size(ICON_ACTION_SIZE),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        PageIcons.Selector,
                                        contentDescription = null,
                                        modifier = Modifier.size(ICON_ACTION_ICON),
                                        // 全项目最弱的一档（`onSurfaceVariantActions`，库给 chevron 用的那档）——
                                        //  它是「这一行可点」的指示、不是动作，不该跟上移 / 下移 / 删除那三个按钮抢眼。
                                        //  不给退场另开一档：这一档与 `disabledOnSecondaryVariant` **恰好同值**
                                        // （浅色 0x42 / 深色 0x4D），分了也是同一个颜色。
                                        tint = COUITheme.colorScheme.onSurfaceVariantActions,
                                    )
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
                            StepPickerPopup(
                                show = stepPick == stepsIndex,
                                preciseClick = rowPreciseClick,
                                replaceIndex = stepsIndex,
                            ) { stepPick = -1 }
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
    val addTitleColor by animateColorAsState(
        targetValue =
            if (stepsFull) {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            } else {
                COUITheme.colorScheme.onBackground
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "StepAddTitle",
    )
    val addSummaryColor by animateColorAsState(
        targetValue =
            if (stepsFull) {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            } else {
                COUITheme.colorScheme.onSurfaceVariantSummary
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "StepAddSummary",
    )

    // 整行挂上手指锚点 —— 浮层弹在按下那一点（只记不看，行自己的点击照常）。
    Box(Modifier.fillMaxWidth().preciseClickAnchor(preciseClick).gestureProbe(probe)) {
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
            // 行首 `+` 的 tint 由上面 `addIconTint` 给（两档都显式写出来，为了过渡）：禁用态取
            // `disabledOnSecondaryVariant`（库 `disabledColor` 的取值，与 `IconAction` 禁用色一致），
            // 正常态取 `onSurfaceContainer`（外层 `contentColor`）。正常态不能用 `Color.Unspecified`
            // 顶替 —— 那会变成完全不上色。
            startAction = {
                Icon(PageIcons.StepAdd, null, Modifier.size(ROW_ICON_SIZE), tint = addIconTint)
            },
            // 行尾同样收一颗选择器指示（见 [PageIcons.Selector]）—— 这一行点了也弹选择器。
            //   tint 走最弱那一档（`onSurfaceVariantActions`），比行首那颗 `+` 弱；不给满员另开一档，
            //   与 `disabledOnSecondaryVariant` 同值（见上面步骤行那处注释）。
            endActions = {
                Box(
                    Modifier.size(ICON_ACTION_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PageIcons.Selector,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_ACTION_ICON),
                        tint = COUITheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            },
            // `enabled = false` 时 COUI 不挂 `clickableModifier` ⇒ 点不进去，浮层也就打不开。
            enabled = !stepsFull,
            onClick = {
                tick()
                // 诊断：这一下是点出来的还是滑出来的（见 [GestureProbe]）
                XLog.w("添加命令：行被点开 —— ${probe.describe()}")
                picking = true
            },
        ) {
            Text(
                text = I18n.get(Strings.COMMAND_ADD, "添加命令"),
                style = COUITheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = addTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = I18n.get(Strings.COMMAND_STEP_SELECT_TITLE, "从内置命令与自定义命令里选择"),
                modifier = Modifier.padding(top = 2.dp),
                style = COUITheme.textStyles.body2,
                color = addSummaryColor,
            )
        }
        // 落点已经不看这个盒子 —— 库拿 `preciseClickState` 记下的触点当锚点。留着它只是为了兜底：
        // 触点没记到（如无障碍点击）时，浮层落在这一条竖边的下方，仍是可见的位置。
        Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(0.dp)) {
            StepPickerPopup(
                show = picking,
                preciseClick = preciseClick,
            ) { picking = false }
        }
    }
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
