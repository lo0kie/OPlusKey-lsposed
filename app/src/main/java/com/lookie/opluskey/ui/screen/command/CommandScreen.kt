package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.ConfirmDialog
import com.lookie.opluskey.ui.component.IconAction
import com.lookie.opluskey.ui.component.NoHapticFeedback
import com.lookie.opluskey.ui.component.SliderRow
import com.lookie.opluskey.ui.component.TileFocusRing
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.component.escapeEmphasis
import com.lookie.opluskey.ui.hideIme
import com.lookie.opluskey.ui.isImeVisible
import com.lookie.opluskey.ui.model.APP_STATE_TEXT_GAP
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.model.COMMAND_DISCLAIMER_TEXT
import com.lookie.opluskey.ui.model.COMMAND_DISCLAIMER_TITLE
import com.lookie.opluskey.ui.model.COMMAND_FORMAT_LINES
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.SUMMARY_REF_GAP
import com.lookie.opluskey.ui.model.SUMMARY_REF_ICON
import com.lookie.opluskey.ui.model.TAB_PAGE_SWITCH_MS
import com.lookie.opluskey.ui.model.TILE_TITLE_GAP
import com.lookie.opluskey.ui.model.commandIcon
import com.lookie.opluskey.ui.model.commandKindLabel
import com.lookie.opluskey.ui.model.durationText
import com.lookie.opluskey.ui.model.serialSummarySteps
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.screen.apppicker.appLabelOf
import com.lookie.opluskey.ui.screen.binding.BindingScreen
import com.lookie.opluskey.ui.screen.command.CommandMenuPopup
import com.lookie.opluskey.ui.screen.command.IconInputView
import com.lookie.opluskey.ui.state.acceptCommandDisclaimer
import com.lookie.opluskey.ui.state.rememberTick
import com.lookie.opluskey.ui.state.tickNow
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.SquareRoundedMinus
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SERIAL_CARD_ROWS = 1

/**
 * 「已保存的命令」空状态那一段的上下留白 —— 纯视觉：字形 + 一行字撑起来，比一条命令卡矮一档。
 * （没有卡片背景了，这档留白就是它与上面标题、下面内容之间的距离。）
 */
internal val EMPTY_STATE_VERTICAL_PADDING = 24.dp

/**
 * 空状态那颗字形的大小 —— 比「选择应用」页的整页空态那档（`APP_STATE_ICON_SIZE`，32dp）小一档：
 * 那一档整页只有这一句话，这一档只是页内一段（「图标缩小」）。
 */
internal val EMPTY_STATE_ICON_SIZE = ROW_ICON_SIZE

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
internal fun SettingsPage.CommandKindBand(pagerState: androidx.compose.foundation.pager.PagerState) {
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
    pagerState: androidx.compose.foundation.pager.PagerState,
) {
    val tick = rememberTick()
    // 首次进这一页要先过一道风险提示：这一页能让用户写任意 shell 命令，
    //  而我们**不做任何检验或保护**。不确认就退出这一页（[goBack]），确认了才记下这一笔。
    //  记在配置里（`Config.commandDisclaimerAccepted`）：清空配置按 `DEFAULT` 重建 ⇒ 这一位跟着
    //  没了 ⇒ 下次进这一页照样弹（用户要的「重置配置就要重新确认」就是这么来的）。
    //  `configReadOnly` 时不弹 —— 那时写盘会被拒，确认了也记不下（与首页那次同一口径）。
    var commandDisclaimerPending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!configState.readOnly && !configState.config.value.commandDisclaimerAccepted) {
            XLog.w("自定义命令：还没确认过风险提示，弹窗（不确认就退出这一页）")
            commandDisclaimerPending = true
        }
    }
    // 长按哪一条命令就弹哪一条的菜单（存名字，`null` = 没开着）。名字是主键。
    var menuFor by remember { mutableStateOf<String?>(null) }
    // 待确认的删除：非空 = 弹着确认窗（真删在确认之后）
    var deleting by remember { mutableStateOf<Command?>(null) }
    // 待确认的「填前缀」：非空 = 弹着确认窗。点「支持的命令格式」那一行是把前缀**整段替换**进
    //  输入框（不是插入），输入框里已经有内容时那是不可逆的，先问一句。
    var pendingFormatPrefix by remember { mutableStateOf<String?>(null) }
    //  注意：**不要在这里读 `draft.name`**，也不要在这里算重名。composable 的实参是在调用点求值的，
    //  在函数体里读这个值会把整个 `CommandScreen` 打脏 —— 于是每敲一个字符，pager 的两页、
    //  整片「已保存的命令」网格、以及网格里每一个格子都跟着重组一遍。
    //  它现在关在 [CommandNameField] 那一层里（重名判定也在那边）。
    //  滚动位置**每个类型页各持一份**（与「按键绑定」页那三个手势页同一套做法）。
    //  注意：两页**共用一份**的话**切回来就滚不动**：`ScrollState` 内部只有一个
    //  `scrollableState`，而 pager 滑动时相邻两页同时在场 ⇒ 两处 `verticalScroll` 挂
    //  同一个 state、互相打架（「按键绑定」页踩过同一个坑）。
    //  「点已保存的命令要滚回顶部」那几处也各自读**本页**这一份。
    val pageScrolls = CommandKind.entries.map { rememberScrollState() }
    val scope = rememberCoroutineScope()

    val tabBlockDp = commandBandHeight.dp()

    // —— 两页：单条 / 多条 ——
    // 分页器承载内容，顶栏那条带子驱动它；`draft.kind` 始终跟停下来的那一页走，
    // 保存 / 载入 / 工具条显隐都读它。
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

    // 「重置」收尾：行级退场播完才真正 reset —— 放 SerialSteps 里会因步骤卡隐藏而永远挂起。
    LaunchedEffect(commandDraft.resetPending) {
        if (commandDraft.resetPending) {
            delay(TAB_PAGE_SWITCH_MS + 60L)
            //  注意：这 240ms 里用户可能已经在输入框里敲字了（点「重置」之后焦点还在名称 / 命令框上），
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

    Box(
        Modifier
            .fillMaxSize()
            .background(COUITheme.colorScheme.surface),
    ) {
        // 两页：单条 / 多条 —— 可左右滑动，顶栏里那条类型选择器带驱动它
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    // 横向越界回弹：与「按键绑定」页那三个手势页同一套弹簧
                    .overScrollHorizontal(),
        ) { page ->
            // 这一页是哪一类 —— 页内所有 `kind` 判断都指它，不是 `draft.kind`
            val kind = CommandKind.entries[page]
            // 这一页只列自己那一类（`saved` 与同名的外层变量无关）
            //  记住它：这一页在每次重组上都要 `filter` 一遍整个命令库并新建一个 List，
            //  而下面那几处都会打脏本页（滚动手势、开关、输入……）。
            val saved = remember(configState.commands.value, kind) { configState.commands.value.filter { it.kind == kind } }
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
                val editing = commandDraft.draft.editingName != null
                SmallTitle(if (editing) I18n.get(Strings.COMMAND_TITLE_EDIT, "修改命令") else I18n.get(Strings.COMMAND_TITLE_NEW, "新增命令"))

                // 输入项用 COUI 的「卡片输入」形态（[IconInputView]）：一个输入项一张卡、无边框，
                // 行首图标会把标题与输入框一起挤开 —— 库的 `InputView` 做不到，所以自己拼。
                // 抽成独立的 composable 是为了把 `draft.name` 的读取关在里面（见它的注释）。
                CommandNameField()

                // 单条命令的正文 —— 与「名称」同一套卡片输入；多条命令走下面那张「步骤」卡。
                if (kind == CommandKind.SINGLE) {
                    Spacer(Modifier.height(GRID_GAP))
                    IconInputView(
                        icon = PageIcons.CommandSingle,
                        value = commandDraft.draft.single,
                        //  注意：**多行粘贴要先洗掉换行**：这个输入框是单行的
                        //  （`IconInputView` 内部 `singleLine = true`），多行粘进来的换行**显示不出来**，
                        //  存下去的却带着 —— 界面看着对、跑起来那条命令跑不通。
                        //  折成 `; ` 而**不是空格**：shell 里换行是命令分隔符（等价 `;`），当空白处理会把
                        //  `setenforce 0` + 换行 + `getenforce` 折成一条带多余参数的命令 —— 解析得出、
                        //  执行跑不通（被当成 `setenforce` 的第二个参数），而界面上看不出任何异常。
                        //  再掐掉粘贴带进来的尾随空白；名称框走的是 `sanitizeNameInput`，同一套纪律。
                        onValueChange = { raw ->
                            commandDraft.draft.single =
                                if (raw.any { it == '\n' || it == '\r' }) {
                                    raw.replace(Regex("[\\r\\n]+"), "; ").trimEnd()
                                } else {
                                    raw
                                }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
                        title = I18n.get(Strings.COMMAND_CMD_LABEL, "命令"),
                        label = I18n.get(Strings.COMMAND_CMD_HINT, "请输入命令"),
                    )
                }

                // `keyevent:` 注入同毫秒生效，而「最近任务 / 通知中心 / 控制中心」要跨四个进程、`shell:` 更
                // 是另起进程 —— 不等待的话连续几步会乱。只有多条命令有这个值，所以只在这一栏显示。
                if (kind == CommandKind.SERIAL) {
                    Spacer(Modifier.height(GRID_GAP))
                    // 这张卡只有一行：「等多久再派发下一条」。
                    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
                        SliderRow(
                            title = I18n.get(Strings.COMMAND_DELAY_LABEL, "命令间隔时长"),
                            icon = PageIcons.DelayStep,
                            summary = I18n.get(Strings.COMMAND_DELAY_DESC, "等多久再执行下一条"),
                            position = cardListPositionOf(0, SERIAL_CARD_ROWS),
                            min = Command.MIN_STEP_DELAY_MS,
                            max = Command.MAX_STEP_DELAY_MS,
                            value = commandDraft.draft.stepDelayMs,
                            default = Command.DEFAULT_STEP_DELAY_MS,
                        ) { v ->
                            tick()
                            commandDraft.draft.stepDelayMs = v
                            // 只改草稿、不落盘（随「保存」一起入库）—— 没有失败可言，恒 true。
                            true
                        }
                    }
                }

                // 「支持的命令格式」卡 —— 说的是「命令输入框里能写什么」，多条命令没有那个
                // 输入框，所以只在单条那一栏出现。
                if (kind == CommandKind.SINGLE) {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(I18n.get(Strings.COMMAND_FORMATS_TITLE, "支持的命令格式"))
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
                                                Modifier.clickable(onClickLabel = I18n.format(Strings.COMMAND_PREFIX_FILL, prefix)) {
                                                    tick()
                                                    // 输入框里已经有别的内容 ⇒ 先问一句：这一下是**整段替换**，
                                                    //  已经敲进去的命令会被一键清掉，没有撤销。
                                                    if (commandDraft.draft.single.isNotBlank() && commandDraft.draft.single != prefix) {
                                                        XLog.w("命令格式：输入框里已有内容，填前缀 $prefix 之前先问一句")
                                                        pendingFormatPrefix = prefix
                                                    } else {
                                                        XLog.w("命令格式：把前缀 $prefix 填进「命令」输入框")
                                                        commandDraft.draft.single = prefix
                                                        // 输入框在「格式」卡上面，不滚回去看不出改没改。
                                                        scope.launch { pageScrolls[page].animateScrollTo(0) }
                                                    }
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
                    SmallTitle(I18n.get(Strings.COMMAND_LIST_TITLE, "命令列表"))
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

                // 「已保存的命令」这一段**始终在**：一条都没有时给一张空状态卡 ——
                //  以前是 `if (saved.isNotEmpty())` 整段不出现，用户看不出这块地方该有东西、也分不清
                //  「还没保存过」与「这块地方根本不存在」。
                Spacer(Modifier.height(12.dp))
                SmallTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"))

                //  「一条都没有」与「有若干条」之间走**交叉过渡**：原来是
                //  `if / else` 硬切 —— 删掉最后一条时整片网格「啪」地消失、空状态「啪」地出现，
                //  中间没有任何交代。`AnimatedContent` 会把容器高度从旧值补到新值（[SizeTransform]），
                //  观感就是网格收下去、空状态长出来。
                //  注意：目标态传的是**列表本身**，不是 `saved.isEmpty()` 那个布尔：`AnimatedContent` 把
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
                            targetContentEnter = fadeIn(tween(TAB_PAGE_SWITCH_MS)),
                            initialContentExit = fadeOut(tween(TAB_PAGE_SWITCH_MS)),
                            sizeTransform =
                                SizeTransform(
                                    clip = true,
                                    sizeAnimationSpec = { _, _ -> tween(TAB_PAGE_SWITCH_MS) },
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
                        //  注意：画的是 [list]（目标态那份），不是外层的 `saved` —— 过渡期间退场那一侧
                        //  拿到的正是旧列表，用 `saved` 会让它渲染成空网格。
                        list.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
                            if (rowIndex > 0) Spacer(Modifier.height(GRID_ROW_GAP))
                            CommandTileRow(
                                tileCount = row.size,
                                minHeightRatio =
                                    if (commandDraft.draft.kind == CommandKind.SINGLE) {
                                        SINGLE_CARD_MIN_HEIGHT_RATIO
                                    } else {
                                        1f
                                    },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
                            ) { tileIndex ->
                                val cmd = row[tileIndex]
                                // 每格按 `key(cmd.name)` 认身份（命令名是这条命令的主键）—— 与上面步骤行 `key(item.id)`
                                //  同一个理由：这一片是按**位次**排出来的，卡里的 `animateColorAsState` 与卡片槽位都按
                                //  位次记身份 ⇒ 删一条 / 改名之后后面的格子会继承前一格的位置，强调色从上一格的颜色
                                //  动画过去（一帧闪色）。
                                key(cmd.name) {
                                    // 每一张卡各自一份 `PreciseClickState`（按命令名做 key）：`anchorCoordinates`
                                    // 只有一格，共用一份会把触点算到别的卡上去。
                                    val tilePreciseClick = remember(cmd.name) { PreciseClickState() }
                                    // 选中态（正在表单里编辑的那一条）：三处共用，只算一次 —— 判据是 `draft.editingName`。
                                    val editing = commandDraft.draft.editingName == cmd.name
                                    // 选中时类型图标与标题一起上主题色，带过渡（一处动画喂两个组件）。
                                    val accentColor by animateColorAsState(
                                        targetValue =
                                            if (editing) {
                                                COUITheme.colorScheme.primary
                                            } else {
                                                COUITheme.colorScheme.onSurfaceContainer
                                            },
                                        animationSpec = COLOR_TRANSITION_SPEC,
                                        label = "CommandTileAccent",
                                    )
                                    Box(Modifier.fillMaxWidth()) {
                                        // 长按振动只走通用通道（[tickNow]）：库默认还会自己振一次 `LongPress`，
                                        // 那条不看系统静音 —— 这里把局部触觉反馈换成空实现。
                                        CompositionLocalProvider(LocalHapticFeedback provides NoHapticFeedback) {
                                            Card(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        // 行高由 [CommandTileRow] 统一定，这里填满行高即可。
                                                        .fillMaxHeight()
                                                        .preciseClickAnchor(tilePreciseClick),
                                                colors =
                                                    CardDefaults.defaultColors(
                                                        color = COUITheme.colorScheme.surfaceContainer,
                                                        contentColor = COUITheme.colorScheme.onSurfaceContainer,
                                                        pressedColor = COUITheme.colorScheme.surfaceContainerHigh,
                                                    ),
                                                pressFeedbackType = PressFeedbackType.None,
                                                showIndication = true,
                                                onClick = {
                                                    tick()
                                                    // 草稿里有没保存的改动时先问一句（见 [loadDraft]）
                                                    loadDraft(cmd)
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
                                                                Modifier.size(ROW_ICON_SIZE),
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
                                                                // （单条一闪而过，用不到），而卡片上原本只有步骤列表、看不到它。
                                                                Spacer(Modifier.width(TILE_TITLE_GAP))
                                                                Text(
                                                                    // 文案走 [durationText]（全局唯一一处）：
                                                                    // 1000ms 以上写成秒（`3 秒`），一秒以内是毫秒（`400 毫秒`）
                                                                    text = durationText(cmd.stepDelayMs),
                                                                    style = COUITheme.textStyles.footnote1,
                                                                    color =
                                                                        COUITheme.colorScheme
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
                                                            I18n.get(Strings.COMMAND_TEST_BUTTON, "测试命令"),
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
                                                        CommandKind.SINGLE -> {
                                                            Text(
                                                                text = cmd.single,
                                                                style = COUITheme.textStyles.footnote1,
                                                                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }

                                                        CommandKind.SERIAL -> {
                                                            serialSummarySteps(cmd.steps, ::appLabelOf)
                                                                .forEach { line ->
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                    ) {
                                                                        //  每一步行首都补一枚小图标（与步骤列表同一套 [stepIcon]）：
                                                                        // 引用另一条命令（`cmd:<名字>`）的那一步用**被引用那条的真实
                                                                        // 类型图标**（单条 / 按步数的多条），其余步骤用动作图标 ——
                                                                        // 光看文字分不出两类步骤。
                                                                        Icon(
                                                                            stepIcon(line.token, configState.commands.value),
                                                                            contentDescription = null,
                                                                            modifier =
                                                                                Modifier
                                                                                    .size(SUMMARY_REF_ICON),
                                                                            tint =
                                                                                COUITheme.colorScheme
                                                                                    .onSurfaceVariantSummary,
                                                                        )
                                                                        Spacer(Modifier.width(SUMMARY_REF_GAP))
                                                                        Text(
                                                                            text = line.title,
                                                                            style = COUITheme.textStyles.footnote1,
                                                                            color =
                                                                                COUITheme.colorScheme
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
        }

        // 首次进这一页那一次风险提示（见 [commandDisclaimerPending]）：**不接受就退出去**
        //  （`goBack()`），接受则记下这一笔。与首页那份是两个独立的记录，互不影响。
        //  给红（`positiveDanger`）：这一份说的不是「注入有风险」，而是「你写什么就跑什么」，
        //  比首页那份更该让人停一下。
        ConfirmDialog(
            show = commandDisclaimerPending,
            title = COMMAND_DISCLAIMER_TITLE,
            summary = COMMAND_DISCLAIMER_TEXT,
            action = I18n.get(Strings.DIALOG_DISCLAIMER_ACKNOWLEDGE, "我已了解"),
            positiveDanger = true,
            onDismiss = {
                XLog.w("自定义命令：没确认风险提示 ⇒ 退出这一页")
                commandDisclaimerPending = false
                goBack()
            },
            onConfirm = {
                XLog.w("自定义命令：已确认风险提示")
                commandDisclaimerPending = false
                acceptCommandDisclaimer()
            },
        )

        // 退出这一页时的「放弃未保存的命令」（触发点在 `SettingsPage.goBack`，这里只负责画）。
        //  确认 ⇒ 丢掉草稿并**真退**（`popBackStack` 绕开 goBack 的拦截，否则又弹回自己）；
        //  取消 ⇒ 关掉窗，留在页里继续编。
        ConfirmDialog(
            show = confirmLeaveCommands,
            title = I18n.get(Strings.DIALOG_DISCARD_COMMAND_TITLE, "放弃未保存的命令"),
            action = I18n.get(Strings.COMMON_DISCARD, "放弃"),
            onDismiss = { confirmLeaveCommands = false },
            onConfirm = {
                confirmLeaveCommands = false
                commandDraft.draft.reset()
                popBackStack()
            },
        )

        // 「换成另一条命令」撞上未保存的改动（触发点在 `SettingsPage.loadDraft`，这里只负责画）。
        //  确认 ⇒ 丢掉当前草稿、载入那一条（这里直接 `draft.load`：刚确认过，再走 `loadDraft`
        //  会因为它此刻仍是脏的而**再问一遍**）；取消 ⇒ 关掉窗，表单原样留着继续编。
        ConfirmDialog(
            show = pendingDraftLoad != null,
            title = I18n.get(Strings.DIALOG_DISCARD_COMMAND_TITLE, "放弃未保存的命令"),
            summary =
                I18n.format(
                    Strings.DIALOG_DISCARD_COMMAND_EDIT_MESSAGE,
                    escapeEmphasis(pendingDraftLoad?.name ?: ""),
                ),
            action = I18n.get(Strings.COMMON_DISCARD, "放弃"),
            onDismiss = { pendingDraftLoad = null },
            onConfirm = {
                val next = pendingDraftLoad
                pendingDraftLoad = null
                next?.let { commandDraft.draft.load(it) }
            },
        )

        // 删除确认：与「清空配置」同一套控件（取消中性色 + 动作项红）
        ConfirmDialog(
            show = deleting != null,
            title = I18n.get(Strings.DIALOG_DELETE_COMMAND_TITLE, "删除命令"),
            // 文案分两种：多条命令不会被别的命令引用（浮层里只能挑单条），只提按键绑定那一条路。
            // （`deleting` 是委托属性、智能转换用不了，两边都带 `?: ""`。）
            summary =
                if (deleting?.kind == CommandKind.SERIAL) {
                    I18n.format(
                        Strings.DIALOG_DELETE_COMMAND_SERIAL_MESSAGE,
                        escapeEmphasis(deleting?.name ?: ""),
                    )
                } else {
                    I18n.format(
                        Strings.DIALOG_DELETE_COMMAND_SINGLE_MESSAGE,
                        escapeEmphasis(deleting?.name ?: ""),
                    )
                },
            action = I18n.get(Strings.COMMON_DELETE, "删除"),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting?.let { removeCommand(it) }
                deleting = null
            },
        )

        // 点「支持的命令格式」那一行要整段替换输入框 ⇒ 里面有东西时先确认（不是插入，没有撤销）
        ConfirmDialog(
            show = pendingFormatPrefix != null,
            title = I18n.get(Strings.DIALOG_FILL_PREFIX_TITLE, "替换已输入的命令"),
            summary =
                I18n.format(
                    Strings.DIALOG_FILL_PREFIX_MESSAGE,
                    escapeEmphasis(pendingFormatPrefix ?: ""),
                ),
            action = I18n.get(Strings.COMMON_REPLACE, "替换"),
            onDismiss = { pendingFormatPrefix = null },
            onConfirm = {
                val prefix = pendingFormatPrefix
                pendingFormatPrefix = null
                if (prefix != null) {
                    XLog.w("命令格式：已确认整段替换，把前缀 $prefix 填进「命令」输入框")
                    commandDraft.draft.single = prefix
                    scope.launch { pageScrolls[pagerState.currentPage].animateScrollTo(0) }
                }
            },
        )
    }
}

/**
 * 「名称」那一张卡片输入（连同重名提示）。
 *
 *  单独抽成一个 composable 是为了**把 `draft.name` 的读取关在这一层里**：composable 的实参是在
 *  调用点求值的，而它原来直接写在 [CommandScreen] 的函数体里 —— 于是每敲一个字符就把整页打脏一次
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
 * 「已保存的命令」一条都没有时的空状态：一颗字形 + 一句话，**不带卡片背景**。
 *
 *  · 文案通用一句（不按当前那一栏分单条 / 多条）：两栏是同一个命令库的两份视图。
 *  · 不铺卡片：这块地方本来就没东西，画一张空壳卡像「卡在、内容没出来」。
 *  · 字形比「选择应用」页那几块空态小一档（那是整页空态，这里只是页内一段），取色同一份。
 */
@Composable
internal fun SavedCommandEmptyState() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            TablerIcons.Outline.SquareRoundedMinus,
            contentDescription = null,
            modifier = Modifier.size(EMPTY_STATE_ICON_SIZE),
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(APP_STATE_TEXT_GAP))
        Text(
            text = I18n.get(Strings.COMMAND_NO_SAVED_COMMANDS, "没有保存的命令"),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
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
