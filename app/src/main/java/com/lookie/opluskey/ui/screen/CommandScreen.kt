package com.lookie.opluskey.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.host.action.ActionRunner
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hideIme
import com.lookie.opluskey.ui.isImeVisible
import com.lookie.opluskey.ui.component.CommandMenuPopup
import com.lookie.opluskey.ui.component.ConfirmDialog
import com.lookie.opluskey.ui.component.IconAction
import com.lookie.opluskey.ui.component.IconInputView
import com.lookie.opluskey.ui.component.NoHapticFeedback
import com.lookie.opluskey.ui.component.SliderRow
import com.lookie.opluskey.ui.component.StepPickerPopup
import com.lookie.opluskey.ui.component.TileFocusRing
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.component.gestureProbe
import com.lookie.opluskey.ui.component.rememberGestureProbe
import com.lookie.opluskey.ui.model.APP_STATE_TEXT_GAP
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.model.COMMAND_FORMAT_LINES
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.SUMMARY_REF_GAP
import com.lookie.opluskey.ui.model.SUMMARY_REF_ICON
import com.lookie.opluskey.ui.model.TILE_TITLE_GAP
import com.lookie.opluskey.ui.model.TOOLBAR_EDGE_GAP
import com.lookie.opluskey.ui.model.TOOLBAR_ICON_SIZE
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT
import com.lookie.opluskey.ui.model.VISIBILITY_MS
import com.lookie.opluskey.ui.model.commandIcon
import com.lookie.opluskey.ui.model.durationText
import com.lookie.opluskey.ui.model.serialSummarySteps
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.state.publishConfig
import com.lookie.opluskey.ui.state.rememberTick
import com.lookie.opluskey.ui.state.tickNow
import com.lookie.opluskey.ui.state.toast
import com.lookie.opluskey.ui.state.write
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.CommandOff
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.FloatingToolbar
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 多条命令表单那张卡的行数（现在只剩「命令间隔时长」一行）。 */
private const val SERIAL_CARD_ROWS = 1

/**
 * 「已保存的命令」空状态那一段的上下留白 —— 纯视觉：字形 + 一行字撑起来，比一条命令卡矮一档。
 * （没有卡片背景了，这档留白就是它与上面标题、下面内容之间的距离。）
 */
private val EMPTY_STATE_VERTICAL_PADDING = 24.dp

/**
 * 空状态那颗字形的大小 —— 比「选择应用」页的整页空态那档（`APP_STATE_ICON_SIZE`，32dp）小一档：
 * 那一档整页只有这一句话，这一档只是页内一段（2026-10-14 用户要求「图标缩小」）。
 */
private val EMPTY_STATE_ICON_SIZE = 22.dp

/**
 * 「已保存的命令」里单条命令卡的行高下限 = 卡宽 × 本值。
 * 单条只有「一行摘要」，占满正方形会空出一大半，取一半即可。
 */
private const val SINGLE_CARD_MIN_HEIGHT_RATIO = 0.5f

/**
 * 「支持的命令格式」卡里每行的行高（也就是整行的触达高度）。
 * 这一行整行可点，高度不够就等于把触达区缩到手指够不着。
 */
private val FORMAT_ROW_TOUCH = 32.dp

/**
 * 「自定义命令」页那条类型选择器带（单条 / 多条）—— 挂在顶栏的 `bottomContent` 里。
 *
 * 亮哪一个由 `pagerState.currentPage` 说了算，点它则去翻页；高度报给
 * [SettingsPage.commandBandHeight]，内容顶上那个占位 Spacer 读它。
 */
@Composable
internal fun SettingsPage.CommandKindBand(
    pagerState: androidx.compose.foundation.pager.PagerState,
) {
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { commandBandHeight.value = it.height },
    ) {
        TabRowWithContour(
            tabs = CommandKind.entries.map { it.label },
            // 亮哪一个只看分页器停在哪一页（`currentPage`）。
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = {
                tick()
                scope.launch { pagerState.animateScrollToPage(it) }
            },
            modifier = Modifier
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
    pagerState: androidx.compose.foundation.pager.PagerState,
) {
    val tick = rememberTick()
    // 长按哪一条命令就弹哪一条的菜单（存名字，`null` = 没开着）。名字是主键。
    var menuFor by remember { mutableStateOf<String?>(null) }
    // 待确认的删除：非空 = 弹着确认窗（真删在确认之后）
    var deleting by remember { mutableStateOf<Command?>(null) }
    val name = draft.name
    // 重名判定（提示与禁保存共用 [commandNameDuplicated]）
    val duplicated = commandNameDuplicated(name, draft.editingName)
    //  滚动位置**每个类型页各持一份**（与「按键绑定」页那三个手势页同一套做法）。
    //  ⚠️ 原来是两页**共用一份**，那会**切回来就滚不动**：`ScrollState` 内部只有一个
    //  `scrollableState`，而 pager 滑动时相邻两页同时在场 ⇒ 两处 `verticalScroll` 挂
    //  同一个 state、互相打架（「按键绑定」页 2026-10-13 踩过同一个坑）。
    //  「点已保存的命令要滚回顶部」那几处也各自读**本页**这一份。
    val pageScrolls = CommandKind.entries.map { rememberScrollState() }
    val scope = rememberCoroutineScope()

    val tabBlockDp = with(LocalDensity.current) { commandBandHeight.value.toDp() }

    // —— 两页：单条 / 多条 ——
    // 分页器承载内容，顶栏那条带子驱动它；`draft.kind` 始终跟停下来的那一页走，
    // 保存 / 载入 / 工具条显隐都读它。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val kind = CommandKind.entries[page]
            //  换 tab 就把**编辑态摘掉**：`editingName` 是「正在改哪一条」的标记，而它属于**上一个
            // tab 的那份表单** —— 留着会让新 tab 的重名判定把它当成「自己」，于是同名不判重、
            // 一路覆盖掉另一条命令（真机反馈「保存判断应限制在当前 tab 的表单下」）。
            //  表单内容（`single` / `steps`）照旧保留：那是两套互不干扰的字段。
            if (kind != draft.kind) draft.editingName = null
            draft.kind = kind
            // 换 tab 收掉软键盘：单条那栏的命令输入框可能正开着键盘，翻到另一栏会盖住表单。
            val host = activity
            if (host != null && isImeVisible(host)) {
                XLog.w("自定义命令：换 tab ⇒ 收键盘")
                hideIme(host)
            }
        }
    }

    // 「重置」收尾：行级退场播完才真正 reset —— 放 SerialSteps 里会因步骤卡隐藏而永远挂起。
    LaunchedEffect(draftResetPending) {
        if (draftResetPending) {
            delay(VISIBILITY_MS + 60L)
            draft.reset()
            draftResetPending = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(COUITheme.colorScheme.surface),
    ) {
        // 两页：单条 / 多条 —— 可左右滑动，顶栏里那条类型选择器带驱动它
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // 横向越界回弹：与「按键绑定」页那三个手势页同一套弹簧
                .overScrollHorizontal(),
        ) { page ->
            // 这一页是哪一类 —— 页内所有 `kind` 判断都指它，不是 `draft.kind`
            val kind = CommandKind.entries[page]
            // 这一页只列自己那一类（`saved` 与同名的外层变量无关）
            val saved = commands.value.filter { it.kind == kind }
            Column(
                Modifier
                    .fillMaxSize()
                    // 大标题随滚动折叠（`nestedScroll` 要在 `verticalScroll` 之前）
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                // 越界回弹：`overScrollVertical` 挂在滚动容器外侧，所以内容不满一屏时照样弹。
                .overScrollVertical()
                .verticalScroll(pageScrolls[page])
                // 页底留白挂在滚动内容内侧（`padding` 在 `verticalScroll` 之后）——
                // 尾部 Spacer 实测不进滚动内容高度，会让最大滚动位置少一截。
                .padding(bottom = PAGE_BOTTOM_PADDING),
            ) {
            // 顶栏叠在内容上，顶部这块要自己让出来；再把类型选择器固定带（`tabBlockDp`）一起让出。
            Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + tabBlockDp))

            // 编辑态：editingName 非 null 即正在改某条已存在的命令。
            val editing = draft.editingName != null
            SmallTitle(if (editing) "修改命令" else "新增命令")

            // 输入项用 COUI 的「卡片输入」形态（[IconInputView]）：一个输入项一张卡、无边框，
            // 行首图标会把标题与输入框一起挤开 —— 库的 `InputView` 做不到，所以自己拼。
            IconInputView(
                icon = PageIcons.InputName,
                value = name,
                // 边输边清洗：只换掉换行、不 trim —— 带 trim 的版本会让空格打不进去。
                // trim 在保存（[addCommand]）与解析（`CommandLibrary.commandOf`）时做。
                onValueChange = { draft.name = CommandLibrary.sanitizeNameInput(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
                title = "名称",
                label = "请输入名称",
                // 同名冲突提示交给组件画
                errorMessage = if (duplicated) "与已有命令重名，请换个名字" else "",
            )

            // 单条命令的正文 —— 与「名称」同一套卡片输入；多条命令走下面那张「步骤」卡。
            if (kind == CommandKind.SINGLE) {
                Spacer(Modifier.height(GRID_GAP))
                IconInputView(
                    icon = PageIcons.CommandSingle,
                    value = draft.single,
                    onValueChange = { draft.single = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
                    title = "命令",
                    label = "请输入命令",
                )
            }

            // `keyevent:` 注入同毫秒生效，而「最近任务 / 通知中心 / 控制中心」要跨四个进程、`shell:` 更
            // 是另起进程 —— 不等待的话连续几步会乱。只有多条命令有这个值，所以只在这一栏显示。
            if (kind == CommandKind.SERIAL) {
                Spacer(Modifier.height(GRID_GAP))
                // 这张卡只有一行：「等多久再派发下一条」。
                Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
                    SliderRow(
                        title = "命令间隔时长",
                        icon = PageIcons.DelayStep,
                        summary = "等多久再执行下一条",
                        position = cardListPositionOf(0, SERIAL_CARD_ROWS),
                        min = Command.MIN_STEP_DELAY_MS,
                        max = Command.MAX_STEP_DELAY_MS,
                        value = draft.stepDelayMs,
                    ) { v ->
                        tick()
                        draft.stepDelayMs = v
                        // 只改草稿、不落盘（随「保存」一起入库）—— 没有失败可言，恒 true。
                        true
                    }
                }
            }

            // 「支持的命令格式」卡 —— 说的是「命令输入框里能写什么」，多条命令没有那个
            // 输入框，所以只在单条那一栏出现。
            if (kind == CommandKind.SINGLE) {
                Spacer(Modifier.height(12.dp))
                SmallTitle("支持的命令格式")
                Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
                    Column(
                        // 上下只给 8dp：每行固定 [FORMAT_ROW_TOUCH]（32dp）高，小字本身约 17dp，
                        // 字与行上下边天然各留约 7.5dp —— 加上 8dp ≈ 左右那 16dp 一档。
                        Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                    ) {
                        COMMAND_FORMAT_LINES.forEachIndexed { index, line ->
                            if (index > 0) HorizontalDivider()
                            // 点这一行 = 把它讲的前缀替换进「命令」输入框（不是追加 ——
                            // 前缀拼在已有内容前面会得到一条跑不通的命令）。
                            // 第一行没有前缀可填，不挂点击。
                            val prefix = line.prefix
                            Row(
                                // 每行都撑到 [FORMAT_ROW_TOUCH]（第一行不可点也照样撑）——
                                // 行高一致，也是整行触达高度。
                                Modifier
                                    .fillMaxWidth()
                                    .height(FORMAT_ROW_TOUCH)
                                    .then(
                                        if (prefix == null) {
                                            Modifier
                                        } else {
                                            // 涟漪走 `LocalIndication`（与库可点组件同一份）
                                            Modifier.clickable(onClickLabel = "填入 $prefix 前缀") {
                                                tick()
                                                XLog.w("命令格式：把前缀 $prefix 填进「命令」输入框")
                                                draft.single = prefix
                                                // 输入框在「格式」卡上面，不滚回去看不出改没改。
                                                scope.launch { pageScrolls[page].animateScrollTo(0) }
                                            }
                                        },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = line.text,
                                    // 文字占满整行（点击区在整行上）
                                    modifier = Modifier.weight(1f),
                                    style = COUITheme.textStyles.footnote1,
                                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            // 多条命令的步骤单独一张卡：它是一串行，塞进上面那张卡会多嵌两层内边距。
            if (kind == CommandKind.SERIAL) {
                Spacer(Modifier.height(12.dp))
                SmallTitle("命令列表")
                //  加 / 删步骤的过渡不在这里：行让位 / 回位由 SerialSteps 每行的 expand / shrink 驱动，
                // 两层高度动画叠在一起会让卡片背景慢半拍。
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PAGE_MARGIN),
                ) {
                    SerialSteps()
                }
            }

            // 「已保存的命令」这一段**始终在**（2026-10-14 用户要求）：一条都没有时给一张空状态卡 ——
            //  以前是 `if (saved.isNotEmpty())` 整段不出现，用户看不出这块地方该有东西、也分不清
            //  「还没保存过」与「这块地方根本不存在」。
            Spacer(Modifier.height(12.dp))
            SmallTitle("已保存的命令")

            //  「一条都没有」与「有若干条」之间走**交叉过渡**（2026-10-14 用户要求）：原来是
            //  `if / else` 硬切 —— 删掉最后一条时整片网格「啪」地消失、空状态「啪」地出现，
            //  中间没有任何交代。`AnimatedContent` 会把容器高度从旧值补到新值（[SizeTransform]），
            //  观感就是网格收下去、空状态长出来。
            //  ⚠️ 目标态传的是**列表本身**，不是 `saved.isEmpty()` 那个布尔：`AnimatedContent` 把
            //  **旧值**交给退场那一侧，若只传布尔，退场侧拿到的仍是「非空」分支、而数据已经空了 ——
            //  它渲染不出任何卡片，那半场会瞬间塌成 0 高，过渡等于没有。
            //  `contentKey` 只看空 / 非空 ⇒ 改某条命令（列表内容变了、空非空没变）不会重播过渡。
            AnimatedContent(
                targetState = saved,
                contentKey = { it.isEmpty() },
                transitionSpec = {
                    // 用 `ContentTransform` 显式给尺寸动画，而不是 `using(SizeTransform(...))`：
                    // 这个 compose 版本里没有 `using` 那个中缀（实测 `Unresolved reference`）。
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(VISIBILITY_MS)),
                        initialContentExit = fadeOut(tween(VISIBILITY_MS)),
                        sizeTransform = SizeTransform(
                            clip = true,
                            sizeAnimationSpec = { _, _ -> tween(VISIBILITY_MS) },
                        ),
                    )
                },
                label = "SavedCommands",
            ) { list ->
                if (list.isEmpty()) {
                    SavedCommandEmptyState()
                } else {
                    //  两列卡片网格：每条命令一张卡，宽度平分、高度下限按类型给（单条 = 半个正方形，
                    // 多条 = 正方形），内容更高时以内容为准；行 / 列间距用 [GRID_GAP] / [GRID_ROW_GAP]。
                    //  交互走库的交互式 `Card`（自带 `onClick` / `onLongPress` 与涟漪）；
                    // `preciseClickAnchor` 挂在外层，只看不消费。
                    //  ⚠️ 画的是 [list]（目标态那份），不是外层的 `saved` —— 过渡期间退场那一侧
                    //  拿到的正是旧列表，用 `saved` 会让它渲染成空网格。
                    list.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) Spacer(Modifier.height(GRID_ROW_GAP))
                        CommandTileRow(
                            tileCount = row.size,
                            minHeightRatio = if (draft.kind == CommandKind.SINGLE) {
                                SINGLE_CARD_MIN_HEIGHT_RATIO
                            } else {
                                1f
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
                        ) { tileIndex ->
                            val cmd = row[tileIndex]
                            // 每一张卡各自一份 `PreciseClickState`（按命令名做 key）：`anchorCoordinates`
                            // 只有一格，共用一份会把触点算到别的卡上去。
                            val tilePreciseClick = remember(cmd.name) { PreciseClickState() }
                            // 选中态（正在表单里编辑的那一条）：三处共用，只算一次 —— 判据是 `draft.editingName`。
                            val editing = draft.editingName == cmd.name
                            // 选中时类型图标与标题一起上主题色，带过渡（一处动画喂两个组件）。
                            val accentColor by animateColorAsState(
                                targetValue = if (editing) {
                                    COUITheme.colorScheme.primary
                                } else {
                                    COUITheme.colorScheme.onSurfaceContainer
                                },
                                label = "CommandTileAccent",
                            )
                            Box(Modifier.fillMaxWidth()) {
                                // 长按振动只走通用通道（[tickNow]）：库默认还会自己振一次 `LongPress`，
                                // 那条不看系统静音 —— 这里把局部触觉反馈换成空实现。
                                CompositionLocalProvider(LocalHapticFeedback provides NoHapticFeedback) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // 行高由 [CommandTileRow] 统一定，这里填满行高即可。
                                            .fillMaxHeight()
                                                .preciseClickAnchor(tilePreciseClick),
                                            colors = CardDefaults.defaultColors(
                                                color = COUITheme.colorScheme.surfaceContainer,
                                                contentColor = COUITheme.colorScheme.onSurfaceContainer,
                                                pressedColor = COUITheme.colorScheme.surfaceContainerHigh,
                                            ),
                                            pressFeedbackType = PressFeedbackType.None,
                                            showIndication = true,
                                            onClick = {
                                                tick()
                                                draft.load(cmd)
                                                // 表单在页面顶部，不滚回去看不出点开了哪一张。
                                                scope.launch { pageScrolls[page].animateScrollTo(0) }
                                            },
                                            onLongPress = {
                                                tick()
                                                XLog.w("已保存命令长按：${cmd.name}（弹编辑 / 删除）")
                                                menuFor = cmd.name
                                            },
                                        ) {
                                        // 顶部对齐：顶行定高，同一行的标题永远是一条线；高度包住内容，
                                        // 下限由布局给。
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        ) {
                                            // 顶行：左边类型图标，右边测试按钮；单条命令的标题也塞进这一行。
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // 左段：图标 +（单条时才有的）标题
                                                Row(
                                                    Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        commandIcon(cmd.kind, cmd.steps.size),
                                                        null,
                                                        Modifier.size(22.dp),
                                                        // 选中时跟着标题走主题色 —— 不给 tint 会吃 `Card` 的 `contentColor`。
                                                        tint = accentColor,
                                                    )
                                                    // 单条命令：标题与图标同一行，只给一行（超了省略号）。
                                                    if (cmd.kind == CommandKind.SINGLE) {
                                                        Spacer(Modifier.width(TILE_TITLE_GAP))
                                                        Text(
                                                            text = cmd.name,
                                                            style = COUITheme.textStyles.headline1,
                                                            fontWeight = FontWeight.Medium,
                                                            // 选中时上主题色，与类型图标同色、同一动画值。
                                                            color = accentColor,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    } else {
                                                        //  多条命令的标题另起一行（见下），所以这一行图标右边空着 ——
                                                        // 正好放这条命令的**步骤间隔**：它是多条命令独有的执行参数
                                                        //（单条一闪而过，用不到），而卡片上原本只有步骤列表、看不到它。
                                                        Spacer(Modifier.width(TILE_TITLE_GAP))
                                                        Text(
                                                            // 文案走 [durationText]（全局唯一一处）：
                                                            // 1000ms 以上写成秒（`3 秒`），一秒以内是毫秒（`400 毫秒`）
                                                            text = durationText(cmd.stepDelayMs),
                                                            style = COUITheme.textStyles.footnote1,
                                                            color = COUITheme.colorScheme
                                                                .onSurfaceVariantSummary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                                // 点一下把这条命令跑一遍（不写配置、不改绑定）。
                                                // 它自己吃掉这一下，不会触发整张卡的 `onClick` 或长按菜单。
                                                IconAction(
                                                    PageIcons.TestCommand,
                                                    "测试命令",
                                                    tint = COUITheme.colorScheme.primary,
                                                ) {
                                                    testCommand(cmd)
                                                }
                                            }
                                            // 多条命令的标题在图标下面另起一行。
                                            if (cmd.kind == CommandKind.SERIAL) {
                                                Spacer(Modifier.height(TILE_TITLE_GAP))
                                                // 标题档位：`headline1` + Medium。
                                                Text(
                                                    text = cmd.name,
                                                    style = COUITheme.textStyles.headline1,
                                                    fontWeight = FontWeight.Medium,
                                                    // 选中时上主题色，与类型图标同色、同一动画值。
                                                    color = accentColor,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            // 标题与摘要之间那 2dp = 库私有常量 `MarginBetweenLine` 的取值。
                                            Spacer(Modifier.height(2.dp))

                                            // 摘要一行一条全部列出（卡会跟着长高）；每行截断交给 `Text`，这层不估宽度。
                                            when (cmd.kind) {
                                                CommandKind.SINGLE -> Text(
                                                    text = cmd.single,
                                                    style = COUITheme.textStyles.footnote1,
                                                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                                    overflow = TextOverflow.Ellipsis,
                                                )

                                                CommandKind.SERIAL -> serialSummarySteps(cmd.steps, ::appLabelOf)
                                                    .forEach { line ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            //  每一步行首都补一枚小图标（与步骤列表同一套 [stepIcon]）：
                                                            // 引用另一条命令（`cmd:<名字>`）的那一步用**被引用那条的真实
                                                            // 类型图标**（单条 / 按步数的多条），其余步骤用动作图标 ——
                                                            // 光看文字分不出两类步骤。
                                                            Icon(
                                                                stepIcon(line.token, commands.value),
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .size(SUMMARY_REF_ICON),
                                                                tint = COUITheme.colorScheme
                                                                    .onSurfaceVariantSummary,
                                                            )
                                                            Spacer(Modifier.width(SUMMARY_REF_GAP))
                                                            Text(
                                                                text = line.title,
                                                                style = COUITheme.textStyles.footnote1,
                                                                color = COUITheme.colorScheme
                                                                    .onSurfaceVariantSummary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }
                                                    }
                                            }
                                        }
                                        }
                                    }
                                    // 浮层挂在零宽内层盒子里（落点由触点决定，这里只是宿主）。
                                    Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(0.dp)) {
                                        CommandMenuPopup(
                                            cmd = cmd,
                                            show = menuFor == cmd.name,
                                            preciseClick = tilePreciseClick,
                                            // 与「轻点这一张」同一个去处：灌进表单后滚回顶部。
                                            onEdited = { scope.launch { pageScrolls[page].animateScrollTo(0) } },
                                            onDelete = {
                                                menuFor = null
                                                deleting = cmd
                                            },
                                            onDismiss = { menuFor = null },
                                        )
                                    }
                                    // 选中态：正在表单里编辑的那一条画一圈内缩边框（[TileFocusRing]），
                                    // 标题与类型图标一起上 `primary`，三处都是渐变过去的。
                                    // 画在最后才盖得住卡片圆角；未选中时整圈透明，不吃触摸。
                                    TileFocusRing(editing)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 退出这一页时的「放弃未保存的命令」（触发点在 `SettingsPage.goBack`，这里只负责画）。
        //  确认 ⇒ 丢掉草稿并**真退**（`popBackStack` 绕开 goBack 的拦截，否则又弹回自己）；
        //  取消 ⇒ 关掉窗，留在页里继续编。
        ConfirmDialog(
            show = confirmLeaveCommands,
            title = "放弃未保存的命令",
            summary = "还有未保存的内容",
            action = "放弃",
            onDismiss = { confirmLeaveCommands = false },
            onConfirm = {
                confirmLeaveCommands = false
                draft.reset()
                popBackStack()
            },
        )

        // 删除确认：与「清空配置」同一套控件（取消中性色 + 动作项红）
        ConfirmDialog(
            show = deleting != null,
            title = "删除命令",
            // 文案分两种：多条命令不会被别的命令引用（浮层里只能挑单条），只提按键绑定那一条路。
            // （`deleting` 是委托属性、智能转换用不了，两边都带 `?: ""`。）
            summary = if (deleting?.kind == CommandKind.SERIAL) {
                "「${deleting?.name ?: ""}」与按键上的绑定。"
            } else {
                "「${deleting?.name ?: ""}」与按键上的绑定、命令对它的引用。"
            },
            action = "删除",
            onDismiss = { deleting = null },
            onConfirm = {
                deleting?.let { removeCommand(it) }
                deleting = null
            },
        )
    }
}

/**
 * 「已保存的命令」一条都没有时的空状态：一颗字形 + 一句话，**不带卡片背景**。
 *
 *  · 文案通用一句（不按当前那一栏分单条 / 多条）：两栏是同一个命令库的两份视图。
 *  · 不铺卡片：这块地方本来就没东西，画一张空壳卡像「卡在、内容没出来」。
 *  · 字形比「选择应用」页那几块空态小一档（那是整页空态，这里只是页内一段），取色同一份。
 */
@Composable
private fun SavedCommandEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            TablerIcons.Outline.CommandOff,
            contentDescription = null,
            modifier = Modifier.size(EMPTY_STATE_ICON_SIZE),
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(APP_STATE_TEXT_GAP))
        Text(
            text = "没有保存的命令",
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 「已保存的命令」卡片右上角那颗 action 按钮 —— 把这条命令跑一遍（不写配置、不改绑定）。
 *
 * 在设置页进程里直接跑：宿主主进程开机后可能没起来，广播会被静默丢掉；设置页进程与宿主
 * 主进程同 uid / 同域，`exec sh`、按键注入、启动应用都通。
 * 不看主开关 —— 这是用户在页面上的显式动作，关着也要能试命令。
 */
internal fun SettingsPage.testCommand(cmd: Command) {
    XLog.w("测试命令：${cmd.name}（${cmd.kind.id}）—— 在设置页进程里直接执行")
    // 与页面其它点击同一条触觉通道（判据只有系统状态）
    tickNow()
    testRunner(context).runTest(cmd.name)
    // 「已执行」不能省：命令可能一点可见动静都没有，没有它分不清「跑了」和「没反应」。
    toast("已执行")
}

/**
 * 测试命令用的执行器 —— 本进程只建一份：内部执行器持进程级的东西（Handler、系统服务），
 * 每点一次新建会平白多出注册（如手电筒 `TorchCallback`）。
 *
 * 必须传 Application Context：`ActionRunner` 会把传进来的 Context 一直拿着，而这里又是
 * 进程级缓存 —— 传 Activity Context 等于永久钉住一个已销毁的 Activity 及其整棵 View 树。
 */
private var testRunnerCache: ActionRunner? = null

private fun testRunner(context: android.content.Context): ActionRunner =
    testRunnerCache ?: ActionRunner(
        context.applicationContext,
        android.os.Handler(android.os.Looper.getMainLooper()),
    ).also { testRunnerCache = it }

/**
 * 「名称与某条已有命令重名」的判定（单一来源）：编辑态下与原命令同名不算重复，
 * 只有改成别的已存在命令的名字才是冲突。
 *
 * ⚠️ 两侧都必须按存库口径（[CommandLibrary.sanitizeName]，含 trim）比：落盘时名字会 trim，
 * 所以「前后各打一个空格」的那份草稿在库里与已有命令**同名** —— 按未 trim 的原样比会判成不重复、
 * 放它写下去，结果静默覆盖掉同名命令（用户只看到一句「已覆盖」）。
 */
internal fun SettingsPage.commandNameDuplicated(name: String, editingName: String?): Boolean {
    val n = CommandLibrary.sanitizeName(name)
    if (n.isEmpty()) return false
    val editing = editingName?.let { CommandLibrary.sanitizeName(it) }
    return commands.value.any { it.name == n && it.name != editing }
}

/**
 * 底部那条浮动工具栏该不该出现（只由 [CommandActions] 用）：编辑态、名称非空、
 * 或正文 / 步骤已填即显示。
 *
 * 页面底部留白（[PAGE_BOTTOM_PADDING]）不跟它走 —— 那是恒定给的：工具栏随时可能因输入
 * 出现，内容必须一直让得开它。
 */
private fun SettingsPage.commandToolbarVisible(): Boolean {
    val payloadReady = when (draft.kind) {
        CommandKind.SINGLE -> draft.single.isNotBlank()
        CommandKind.SERIAL -> draft.steps.isNotEmpty()
    }
    return draft.editingName != null || draft.name.isNotBlank() || payloadReady
}

@Composable
internal fun SettingsPage.CommandActions(modifier: Modifier = Modifier) {
    val tick = rememberTick()
    val editing = draft.editingName != null
    val name = draft.name
    val payloadReady = when (draft.kind) {
        CommandKind.SINGLE -> draft.single.isNotBlank()
        CommandKind.SERIAL -> draft.steps.isNotEmpty()
    }

    val canReset = commandToolbarVisible()
    // 重名（且不是编辑它自己）时按钮直接禁用。
    val canSave = name.isNotBlank() && payloadReady &&
        !commandNameDuplicated(name, draft.editingName)

    // 两个按钮都不可用就整条隐藏；`canSave` 蕴含 `canReset`，判据只看后者。
    val visible = canReset
    // 滑出要滑到屏幕下沿之外：胶囊浮在底边上方 [COMMAND_TOOLBAR_BOTTOM] 处，那段距离要一起算。
    val bottomGapPx = with(LocalDensity.current) { COMMAND_TOOLBAR_BOTTOM.roundToPx() }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // 从底边滑入 / 滑出
        enter = slideInVertically { it + bottomGapPx },
        exit = slideOutVertically { it + bottomGapPx },
    ) {
        // 磨砂要可见，底色必须半透明；而 `showDivider` 的描边是不透明实心胶囊，两者互斥。
        FloatingToolbar(
            // 库默认取 `surfaceContainer`，它两套配色 alpha 不一样（暗色透、亮色近白），
            // 于是按页面背景 `compositeOver(surface)` 合成成不透明 —— 两套配色观感一致。
            // 不能写成 `copy(alpha = 1f)`：那是把半透明变成纯白。
            color = COUITheme.colorScheme.surfaceContainer
                .compositeOver(COUITheme.colorScheme.surface),
            showDivider = true,
        ) {
            // 用图标按钮：文字会把这窄条撑宽。文案进了各自的 `contentDescription`。
            // 按钮 40dp、高亮圆角一半，外留白与按钮间距都是 [TOOLBAR_EDGE_GAP]。
            // 底色 / 字形的可用 ⇄ 禁用切换走 `animateColorAsState` 的默认 spring。
            val resetIconColor by animateColorAsState(
                targetValue = if (canReset) {
                    COUITheme.colorScheme.onSurfaceContainer
                } else {
                    COUITheme.colorScheme.disabledOnSecondaryVariant
                },
                label = "ToolbarResetIcon",
            )
            val saveBackgroundColor by animateColorAsState(
                targetValue = if (canSave) {
                    COUITheme.colorScheme.primary
                } else {
                    COUITheme.colorScheme.surfaceContainerHigh
                },
                label = "ToolbarSaveBackground",
            )
            val saveIconColor by animateColorAsState(
                targetValue = if (canSave) {
                    COUITheme.colorScheme.onPrimary
                } else {
                    COUITheme.colorScheme.disabledOnSecondaryVariant
                },
                label = "ToolbarSaveIcon",
            )
            Row(
                modifier = Modifier.padding(TOOLBAR_EDGE_GAP),
                horizontalArrangement = Arrangement.spacedBy(TOOLBAR_EDGE_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        tick()
                        // 多条命令带步骤时走行级退场：步骤行 shrink 完才真正 reset，否则整批行瞬时消失。
                        if (draft.kind == CommandKind.SERIAL && draft.steps.isNotEmpty()) {
                            draftResetPending = true
                        } else {
                            draft.reset()
                        }
                    },
                    enabled = canReset,
                    minWidth = TOOLBAR_ICON_SIZE,
                    minHeight = TOOLBAR_ICON_SIZE,
                    cornerRadius = TOOLBAR_ICON_SIZE / 2,
                ) {
                    Icon(
                        PageIcons.MenuReset,
                        contentDescription = if (editing) "取消" else "重置",
                        // 禁用时跟着置灰（库不给图标上色）
                        tint = resetIconColor,
                    )
                }
                IconButton(
                    onClick = {
                        tick()
                        addCommand()
                    },
                    enabled = canSave,
                    minWidth = TOOLBAR_ICON_SIZE,
                    minHeight = TOOLBAR_ICON_SIZE,
                    cornerRadius = TOOLBAR_ICON_SIZE / 2,
                    // `primary` + 亮字形；禁用时落回 `surfaceContainerHigh` + 禁用灰（底不消失）。
                    backgroundColor = saveBackgroundColor,
                ) {
                    Icon(
                        PageIcons.Confirm,
                        contentDescription = if (editing) "修改" else "保存",
                        tint = saveIconColor,
                    )
                }
            }
        }
    }
}

/**
 * 命令卡片的一行：宽度平分、同行等高、且不矮于正方形。
 *
 * `Row` 做不到（`weight` 只分宽度、`IntrinsicSize.Min` 量不出正方形下限），所以自己量
 * 两趟：先按宽度平分量各卡自然高度，行高取「最高那张」与卡宽的较大者，再按行高重量。
 *
 * @param tileCount 这一行的卡数（不足 [COLUMNS] 时右侧留空）
 */
@Composable
private fun CommandTileRow(
    tileCount: Int,
    /** 行高下限 = 卡宽 × 本值（单条给 [SINGLE_CARD_MIN_HEIGHT_RATIO]，多条给 `1f`）。 */
    minHeightRatio: Float,
    modifier: Modifier = Modifier,
    tile: @Composable (Int) -> Unit,
) {
    Layout(
        content = { repeat(tileCount) { index -> tile(index) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val gapPx = GRID_GAP.roundToPx()
        // 宽度平分：可用宽 − (COLUMNS−1) 个列距，除以 COLUMNS —— 与这行有几张卡无关。
        val tileWidth = (constraints.maxWidth - gapPx * (COLUMNS - 1)) / COLUMNS
        // 各卡自然高度用固有测量（`minIntrinsicHeight`）拿，不能 `measure()` 两次 —— 真机 R8 包会
        // `IllegalStateException` 崩掉宿主进程；固有测量不占那次唯一的 measure 配额。
        val contentHeights = measurables.map { it.minIntrinsicHeight(tileWidth) }
        val rowHeight = maxOf((tileWidth * minHeightRatio).roundToInt(), contentHeights.max())
        // 唯一一次 measure：按行高定量 —— 同行等高、不矮于下限
        val placeables = measurables.map {
            it.measure(Constraints.fixed(tileWidth, rowHeight))
        }
        layout(constraints.maxWidth, rowHeight) {
            placeables.forEachIndexed { index, p -> p.place(index * (tileWidth + gapPx), 0) }
        }
    }
}

/**
 * 行级动画的展示条目：一条步骤在展示层里的样子。
 *
 * 行状态长在条目对象上、跟着内容走（另带稳定 id 供差分与摘除）—— `draft.steps` 无 id，
 * 增删会让后面的行前移，行状态若按位置记会继承前一行的隐藏态（真机实证）。
 *
 * @param anchorId 退场行专用：收缩期间插在「前面最近的保留行」（其 id）之下，
 *   没有就是 -1（列表最前）—— 顺序重建时按它插回原邻位。
 */
private class StepRowItem(val id: Int, val token: String) {
    val state = MutableTransitionState(false).apply { targetState = true }

    /** 退场中：target 已切 false，播完 shrink+fade 就从展示层摘除。 */
    var exiting = false

    /** 退场行的插回锚。 */
    var anchorId = -1
}

/**
 * 多条命令的步骤列表（「步骤」那张卡的内容）。
 */
@Composable
internal fun SettingsPage.SerialSteps() {
    val tick = rememberTick()
    val steps = draft.steps
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

    LaunchedEffect(draft.steps.toList()) {
        val stepsNow = draft.steps.toList()
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
            if (m == null) newOrder += StepRowItem(nextId++, token).also(display::add)
            else newOrder += m
            newOrder += exitingByAnchor[m?.id].orEmpty().sortedBy { it.id }
        }
        display.clear()
        display.addAll(newOrder)
    }

    // 整卡重置的挂起落到每一行 → 同样播退场（reset 本体由页面级协程执行）
    LaunchedEffect(draftResetPending) {
        if (draftResetPending) display.forEach { it.state.targetState = false }
    }

    display.forEachIndexed { index, item ->
        // 退场播完（currentState 收到 false）→ 从展示层摘除；此刻行高已收到 0，下面的行不会跳
        LaunchedEffect(item.state.currentState) {
            if (item.exiting && !item.state.currentState && !item.state.targetState) {
                display.remove(item)
            }
        }
        AnimatedVisibility(
            visibleState = item.state,
            enter = VISIBILITY_ENTER,
            exit = VISIBILITY_EXIT,
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
                        startAction = { Icon(stepIcon(item.token, commands.value), null, Modifier.size(22.dp)) },
                        endActions = {
                            IconAction(PageIcons.StepUp, "上移", enabled = !item.exiting && stepsIndex > 0) {
                                tick()
                                moveStep(stepsIndex, -1)
                            }
                            IconAction(PageIcons.StepDown, "下移", enabled = !item.exiting && stepsIndex < steps.lastIndex) {
                                tick()
                                moveStep(stepsIndex, 1)
                            }
                            IconAction(PageIcons.CommandDelete, "删除", error = true, enabled = !item.exiting) {
                                tick()
                                // 从 steps 里立即移除（数据层马上正确）；展示层这一行由差分同步标成退场，shrink 完自己摘除。
                                steps.removeAt(stepsIndex)
                            }
                        },
                    ) {
                        // 标题自己画：`BasicComponent(title = String)` 那个重载不给 `maxLines`，标题长了会折行，
                        // 而这一列宽度由行内布局定死（≈173dp，≈13 个汉字格）。样式照抄库的 `BasicComponent`：
                        // 标题 `headline1` + Medium + `onBackground`，摘要 `body2` + `onSurfaceVariantSummary`，
                        // 与标题隔 2dp。
                        Text(
                            text = stepTitle(item.token, ::appLabelOf),
                            style = COUITheme.textStyles.headline1,
                            fontWeight = FontWeight.Medium,
                            color = COUITheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "第 ${stepsIndex + 1} 步",
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
    // 没有步骤时它就成了卡片里的第一条，所以那时不画 —— 它的职责是把步骤与下面那行
    // 「添加命令」分开，没有步骤就没有要分的东西。
    //  ⚠️ 判据取**展示层**（`display`）而不是数据（`steps`）：退场行在动画期间还留在展示层里、
    //  还在占位，那条线要陪着它一起走完；按数据算的话行才刚开始缩、线先没了（少一档分隔）。
    if (display.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))

    // 置灰不改变行数（这一行还在，只是不吃点击），所以上面的 cardListPosition 不用动。
    val stepsFull = steps.size >= Command.MAX_STEPS
    // 行首那颗 `+` 的颜色随「满没满」切换、带过渡 —— 否则第 5 步一加它是「啪」地灰下去的。
    // 正常态那一档就是外层的 `onSurfaceContainer`（`contentColor`）；要给过渡就得把两档都写出来。
    val addIconTint by animateColorAsState(
        targetValue = if (stepsFull) {
            COUITheme.colorScheme.disabledOnSecondaryVariant
        } else {
            COUITheme.colorScheme.onSurfaceContainer
        },
        label = "StepAddIcon",
    )

    // 整行挂上手指锚点 —— 浮层弹在按下那一点（只记不看，行自己的点击照常）。
    Box(Modifier.fillMaxWidth().preciseClickAnchor(preciseClick).gestureProbe(probe)) {
        BasicComponent(
            // 东西本来就是命令，「步骤」是它在多条命令里的身份 —— 浮层两组组标题也写着「内置命令 / 自定义命令」。
            title = "添加命令",
            // 这一行点开的是选择菜单，副标题把内容说在前面，免得「点开才知道里面有什么」。
            summary = "从内置命令与自定义命令里选择",
            //  ⚠️ 位置按**数据**（`draft.steps`）算，不按展示层行数（原 `cardListPositionOf(display.size, display.size + 1)`）：
            //  退场行在动画期间仍留在 `display` 里，按展示层算会让这一行停在 `Tail`，**等退场动画播完、
            //  那行被摘除**才翻成 `Full` —— 而库给圆角边的额外内边距是 `HeadOrTailPadding`（2dp，
            //  且行最小高度同步抬高），`Full` 比 `Tail` 多一份**顶边** ⇒ 卡片高度在动画结束那一刻
            //  又跳 4dp。真机逐帧量过：底边从 2058 一路收到 1847 后**又弹回 1854**，就是这一跳，
            //  看着像「加号行先移过头、再被拉回来」。
            //  按数据算 ⇒ 翻档发生在过渡**开始**那一帧，与行高动画同时进行，看不出来。
            cardListPosition = if (steps.isEmpty()) {
                CardListPosition.Full
            } else {
                CardListPosition.Tail
            },
            // 行首 `+` 的 tint 由上面 `addIconTint` 给（两档都显式写出来，为了过渡）：禁用态取
            // `disabledOnSecondaryVariant`（库 `disabledColor` 的取值，与 `IconAction` 禁用色一致），
            // 正常态取 `onSurfaceContainer`（外层 `contentColor`）。正常态不能用 `Color.Unspecified`
            // 顶替 —— 那会变成完全不上色。
            startAction = {
                Icon(PageIcons.StepAdd, null, Modifier.size(22.dp), tint = addIconTint)
            },
            // `enabled = false` 时 COUI 不挂 `clickableModifier` ⇒ 点不进去，浮层也就打不开。
            enabled = !stepsFull,
            onClick = {
                tick()
                // 诊断：这一下是点出来的还是滑出来的（见 [GestureProbe]）
                XLog.w("添加命令：行被点开 —— ${probe.describe()}")
                picking = true
            },
        )
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

/**
 * 把草稿里的那条命令存进命令库（不是立刻绑到某个手势上）。
 *
 * 重名时表单保存按钮已被 [commandNameDuplicated] 禁掉；本函数保留「按名字写入」的语义，
 * 是给编辑态（名字没变 = 它自己）兜底的。多条命令按名字引用单条命令（步骤里存 `cmd:<名字>`），
 * 所以改名 / 删命令只能走编辑页，别在库层面直接动名字。
 */
internal fun SettingsPage.addCommand() {
    val name = CommandLibrary.sanitizeName(draft.name)
    if (name.isEmpty()) return
    val cmd = when (draft.kind) {
        CommandKind.SINGLE -> Command(name, CommandKind.SINGLE, single = draft.single.trim())
        CommandKind.SERIAL -> Command(
            name = name,
            kind = CommandKind.SERIAL,
            steps = draft.steps.toList(),
            stepDelayMs = draft.stepDelayMs,
        )
    }
    val editingFrom = draft.editingName
    val replaced = commands.value.any { it.name == name }
    // 结果一句话（改 / 覆盖 / 新增各一条；失败由 [saveCommands] 自己报）
    val tip = when {
        editingFrom != null -> "已修改"
        replaced -> "已覆盖"
        else -> "已保存"
    }
    // ① 改名保存必须把**原名那条一起摘掉**：只 `filterNot { it.name == name }` 的话旧名与新名
    //   两条并存在命令库里（列表凭空 +1 条），而手势上绑的仍是旧那条 `cmd:旧名` —— 用户以为改了，
    //   侧键行为一点没变。编辑态下 `editingFrom == name` 时两个条件指同一条，去重天然成立。
    // ② 其余命令里对旧名字的引用（多条命令的步骤 / 单条正文里的 `cmd:旧名`）也要跟着挪，
    //   否则它们指向一条已经不在库里的命令，按下去永远 NOT_FOUND。
    val next = commands.value
        .filterNot { it.name == name || it.name == editingFrom }
        .map { it.renamedReference(editingFrom, name) } + cmd
    val saved = saveCommands(
        next,
        when {
            // 编辑态：提交的是修改（名字没变 = 原地更新；改了名 = 挪到新名字下）
            editingFrom != null && editingFrom != name -> "改名保存：$editingFrom → $name（${cmd.kind.id}）"
            editingFrom != null -> "修改命令：$name（${cmd.kind.id}）"
            replaced -> "覆盖命令：$name（${cmd.kind.id}）"
            else -> "新增命令：$name（${cmd.kind.id}）"
        },
    )
    // ③ 手势上绑的还是 `cmd:旧名`，旧那条已经不在库里 —— 跟 [removeCommand] 同一套做法，
    //   把绑定一起挪到新名字下，否则那句「已修改」是句空话。
    var bindingMoved = true
    if (saved && editingFrom != null && editingFrom != name) {
        val cfg = config.value
        val key = cfg.key(KeyId.PLUS)
        val oldToken = Command.PREFIX + editingFrom
        fun move(a: Action) = if (a.token == oldToken) Action.Custom(name) else a
        val updated = key.copy(
            single = move(key.single),
            double = move(key.double),
            long = move(key.long),
        )
        if (updated != key) {
            bindingMoved = write(
                cfg.copy(keys = cfg.keys + (KeyId.PLUS to updated)),
                "改名后把绑定挪到新名字：$editingFrom → $name",
            )
        }
    }
    // 绑定没挪成时不再报「已修改」：那句会让用户以为侧键行为也跟过来了（write 自己已报过失败）。
    if (saved && bindingMoved) toast(tip)
    // `editing` 必须在 `draft.reset()` 之前读 —— reset 会把 `editingName` 清掉。
    val editing = editingFrom != null
    draft.reset()
    // 只在「新增」这条路上退（从「按键绑定」页右下角那颗 FAB 进来的那条）；编辑态
    //（从「已保存的命令」列表点进来改）留在原地 —— 改完要能立刻在下面那张列表里看到结果。
    if (!editing) goBack()
}

/** 多条命令加一步（从「添加命令」浮层的「选择应用」挑完回来时调）。 */
internal fun SettingsPage.addStep(token: String) {
    draft.steps.add(token)
    XLog.w("多条命令：加第 ${draft.steps.size} 步 → $token")
    navTo(Screen.COMMANDS)
}

/**
 * 多条命令把第 [index] 步换成 [token] —— 点已添加的那一行弹出的选择菜单里挑完时调。
 */
internal fun SettingsPage.replaceStep(index: Int, token: String) {
    if (index !in draft.steps.indices) {
        // 越界原来静默 return：调用方已经 tick / 关了菜单，用户看不到任何反应也无从判断。
        XLog.w("多条命令：第 ${index + 1} 步不存在（当前 ${draft.steps.size} 步），换步已忽略")
        toast("这一步已经不在列表里了")
        navTo(Screen.COMMANDS)
        return
    }
    draft.steps[index] = token
    XLog.w("多条命令：第 ${index + 1} 步换成 $token")
    navTo(Screen.COMMANDS)
}

/** 步骤上移 / 下移一位（[delta] 为 -1 / +1）；越界不动。 */
internal fun SettingsPage.moveStep(index: Int, delta: Int) {
    val target = index + delta
    if (index !in draft.steps.indices || target !in draft.steps.indices) return
    draft.steps.add(target, draft.steps.removeAt(index))
}

/**
 * 删一条命令 —— 三处引用都要清，否则留下的是指向空处的东西：
 *  1. 绑到手势上的 `cmd:<名字>` → 改成「无操作」（不然按下去永远解析不到）；
 *  2. 别的多条命令里对它的引用 → 摘掉那一步；
 *  3. 别的**单条**命令正文里写的 `cmd:<被删名字>` → 改成 `none`（同理；早先只扫 SERIAL 的步骤，
 *    单条正文里那条引用会被留下来）。
 */
internal fun SettingsPage.removeCommand(cmd: Command) {
    val cfg = config.value
    val key = cfg.key(KeyId.PLUS)
    fun clean(a: Action) = if (a.token == cmd.token) Action.None else a
    val updated = key.copy(
        single = clean(key.single),
        double = clean(key.double),
        long = clean(key.long),
    )
    val bindingsCleared = updated != key
    if (bindingsCleared) {
        // 只读态下这次写会被拒（这里原来把返回值丢了）：命令库那份照删，配置里就留下悬空的
        //  `cmd:` —— 按下去永远 NOT_FOUND。所以绑定没清掉时**不能**继续删命令，先告诉用户。
        val ok = write(
            cfg.copy(keys = cfg.keys + (KeyId.PLUS to updated)),
            "删除命令时清掉绑定：${cmd.name}",
        )
        if (!ok) {
            XLog.w("删除命令中止：绑定没能清掉（配置只读），命令库保持原样：${cmd.name}")
            return
        }
    }

    // 删的就是正在编辑的那条 ⇒ 草稿一并清掉：留着的话「修改」按钮还挂着，
    // 点一下会把刚删的命令原样写回去。
    if (draft.editingName == cmd.name) draft.reset()

    val next = commands.value
        .filterNot { it.name == cmd.name }
        .map { it.dropReference(cmd.token) }
    if (saveCommands(next, "删除命令：${cmd.name}")) toast("已删除")
}

/**
 * 写命令库（[commands] 状态同步更新 ⇒ 网格与列表自动重组）。
 *
 * @return 是否真的落盘 —— 失败时这里已经报过「保存失败」，调用方只在 true 时报成功那一句。
 */
internal fun SettingsPage.saveCommands(next: List<Command>, what: String): Boolean {
    val previous = commands.value
    commands.value = next
    val saved = CommandLibrary.save(next)
    if (saved) {
        XLog.w("命令库写入：$what")
        // 命令库变了必须重发配置广播：EXTRA_LONG_SERIAL（长按绑的是不是串行命令）是发广播
        // 那一刻现算的，不重发的话注入侧还拿着旧判定 —— cmd:X 在单条 / 多条之间切换后，
        // 「连续触发」的闸门会一直错下去。
        publishConfig(config.value)
    } else {
        // 落盘失败回滚状态：界面与文件必须一致，否则「界面显示删了、文件还在」
        commands.value = previous
        XLog.w("命令库写入失败（已回滚界面状态）：$what")
        toast("保存失败")
    }
    return saved
}
