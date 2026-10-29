package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.DisabledHint
import com.lookie.opluskey.ui.IconInputView
import com.lookie.opluskey.ui.InputSpec
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.MoreButton
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.commandKindLabel
import com.lookie.opluskey.ui.moveCaretToEnd
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「自定义命令」页的一页（单条 / 多条各一份）—— 从 `CommandScreen.kt` 拆出来。
//
//  这一块原来整个嵌在 `HorizontalPager` 的 content lambda 里（连同 pager 外面那五个确认窗），
//  一个函数 626 行。按「这一段是谁的内容」切开之后：
//   · 本文件     —— 一页的滚动骨架 + 表单各段（名称 / 单条输入 / 间隔 / 格式 / 步骤 / 已保存）；
//   · `SavedCommands.kt` —— 「已保存的命令」那片网格与里面的单张卡；
//   · `CommandDialogs.kt` —— 页外那五个确认窗。

/**
 * 「自定义命令」页的**表单页** —— 单条与多条各走一次。
 *
 * @param kind 这一页是哪一类。页内所有 `kind` 判断都指它，**不是** `draft.kind`
 *   （后者跟着停下来的那一页走，过渡期两者可能不同）。
 * @param scroll 本页的滚动骨架（见 [CommandPageScroll]）。
 */
@Composable
internal fun SettingsPage.CommandPage(
    kind: CommandKind,
    scroll: CommandPageScroll,
    ui: CommandUiState,
    tick: () -> Unit,
) {
    // 排序态（只可能出现在「多条命令」这一页）：表单与列表一律禁用，底部工具条滑出隐藏。
    val sorting = commandDraft.stepEditing
    //  命令列表那一行的小标题右侧那颗「更多」（与条件执行页的场景块同一套）：
    //  点它弹一层菜单，排序态从菜单里进出 —— 原先挂在右下角那颗 FAB 上，用户口径挪到这里。
    val listAnchor = rememberPreciseClickState()
    var listMenu by remember { mutableStateOf(false) }
    //  排序那颗 FAB 只在**有步骤可排**时出现：列表空着没什么可排的，摆一颗「调整顺序」只是噪音。
    //  但排序态里即使把步骤删空也要留着 —— 那时工具条是隐藏的，它是**唯一的出口**，藏了就出不来。

    Box(Modifier.fillMaxSize()) {
        PageScrollColumn(scrollBehavior = scroll.behavior, state = scroll.state) {
            // 顶栏叠在内容上，顶部这块要自己让出来；再把类型选择器固定带（`tabBlockDp`）一起让出。
            Spacer(Modifier.height(currentHeaderHeight(scroll.behavior) + scroll.tabBlockDp))

            // 表单的每一栏都带一条小标题（「命令名称」「命令详情」）：输入框里的字是**占位文案**
            //  （「请输入名称」），滚到这一屏时那行字可能已经被填满，光看框分不清哪个是名字、
            //  哪个是命令本体。原先这一条写的是「修改命令 / 新增命令」—— 那说的是**这一页在干什么**，
            //  而顶栏已经写着「自定义命令」、工具栏那颗键写着「保存 / 修改」，这里该说的是**这一栏是什么**。
            SectionTitle(I18n.get(Strings.COMMAND_NAME_TITLE, "命令名称"))

            // 输入项用 COUI 的「卡片输入」形态（[IconInputView]）：一个输入项一张卡、无边框，
            // 行首图标会把标题与输入框一起挤开 —— 库的 `InputView` 做不到，所以自己拼。
            // 抽成独立的 composable 是为了把 `draft.name` 的读取关在里面（见它的注释）。
            CommandNameField(enabled = !sorting)

            // 单条命令的正文 —— 与「名称」同一套卡片输入；多条命令走下面那张「步骤」卡。
            if (kind == CommandKind.SINGLE) {
                SectionTitle(I18n.get(Strings.COMMAND_DETAIL_TITLE, "命令详情"))
                SingleCommandField(ui, scroll, enabled = !sorting)
            }

            // `keyevent:` 注入同毫秒生效，而「最近任务 / 通知中心 / 控制中心」要跨四个进程、`shell:` 更
            // 是另起进程 —— 不等待的话连续几步会乱。只有多条命令有这个值，所以只在这一栏显示。
            //  它自己那条行标题（「命令间隔时长」）说的是**这一行是什么**，小标题说的是**这一组是什么**
            //  —— 以后这一组还会长（比如「失败后停止」），先按「命令设置」归拢。
            if (kind == CommandKind.SERIAL) {
                CommandSettingsSection(sorting = sorting, tick = tick)
            }

            // 「支持的命令格式」卡 —— 说的是「命令输入框里能写什么」，多条命令没有那个
            // 输入框，所以只在单条那一栏出现。
            if (kind == CommandKind.SINGLE) {
                SectionTitle(I18n.get(Strings.COMMAND_FORMATS_TITLE, "支持的命令格式"))
                CommandFormatsCard(tick, ui)
            }

            // 多条命令的步骤单独一张卡：它是一串行，塞进上面那张卡会多嵌两层内边距。
            if (kind == CommandKind.SERIAL) {
                SectionTitle(
                    text = I18n.get(Strings.COMMAND_LIST_TITLE, "命令列表"),
                    trailing = {
                        MoreButton(
                            onClick = { listMenu = true },
                            anchor = listAnchor,
                            endPadding = PAGE_MARGIN,
                        )
                    },
                )
                //  加 / 删步骤的过渡不在这里：行让位 / 回位由 SerialSteps 每行的 expand / shrink 驱动，
                // 两层高度动画叠在一起会让卡片背景慢半拍。
                Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
                    SerialSteps()
                }
            }

            //  「已保存的命令」不在这里了 —— 它是第三个页签（见 [CommandListPage]）：
            //  原先挂在两张表单的页尾、每页只列自己那一类，翻页才看得见另一半。
        }
        //  排序态的入口在「命令列表」那条小标题的行尾（见它上面那个 `trailing`），
        //  不再用右下角那颗 FAB —— 与「条件执行」页的场景块同一个形态。
        StepListMenu(
            open = listMenu,
            sorting = sorting,
            anchor = listAnchor,
            tick = tick,
            onClose = { listMenu = false },
        )
    }
}

/**
 * 「命令列表」那一页 —— 整个命令库，按类型分成两段：单条命令在上、多条命令在下。
 *
 *  原先这一片是挂在**两张表单的页尾**、每页只列自己那一类：翻到哪一页就只看得见一半，
 *  要改另一类的那条得先翻页。现在它是独立的一页，两段各给一条小标题。
 *
 *  分段的第二个理由在**行高**上：单条卡是半个正方形、多条卡是正方形，一格混排时整行按
 *  高的那一档算（见 `CommandTileRow` 的 `minHeightRatio`），单条卡就会空出半张。
 *  分段之后每段里只有一种类型，格子高度正好。
 *
 *  与 [CommandPage] 共用同一套滚动骨架（顶栏折叠 + 越界回弹 + 页底留白）：换页时底部留白与
 *  折叠曲线要对得上，各写一份迟早走形。
 *
 * @param onOpen 点开一条（或它菜单里的「编辑」）—— 草稿由卡片那边灌好，这里只管跳回表单页
 *   （见 `CommandScreen` 的 `openSaved`）。
 */
@Composable
internal fun SettingsPage.CommandListPage(
    scroll: CommandPageScroll,
    ui: CommandUiState,
    tick: () -> Unit,
    onOpen: (Command) -> Unit,
) {
    val commands = configState.commands.value
    //  量一次视口高度：空态要**在可见区里居中**，而滚动容器的子项拿到的是无限高度约束
    //  （`fillMaxHeight()` 在那儿没有意义）—— 与「条件执行」页同一个做法。
    //  空态**仍然留在滚动内容里**：它得跟着内容一起拖、一起弹，挪出滚动容器就再也越界不动了。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val emptyHeight =
            (maxHeight - currentHeaderHeight(scroll.behavior) - scroll.tabBlockDp - PAGE_BOTTOM_PADDING)
                .coerceAtLeast(0.dp)
        PageScrollColumn(scrollBehavior = scroll.behavior, state = scroll.state) {
            // 顶栏叠在内容上，顶部这块要自己让出来；再把类型选择器固定带一起让出。
            Spacer(Modifier.height(currentHeaderHeight(scroll.behavior) + scroll.tabBlockDp))
            if (commands.isEmpty()) {
                //  一条都没有：撑满可见区并正中（高度已减掉顶栏、分页带与页底留白，
                //  所以居中落在**看得见的那块**正中间，不多出一段能拖的空白）。
                Box(Modifier.fillMaxWidth().height(emptyHeight), contentAlignment = Alignment.Center) {
                    SavedCommandEmptyState()
                }
            } else {
                //  两段各一条小标题；**空的那一段整段不画**（一条命令都没有的那类，留一条光标题
                //  + 空网格只是占地方）。
                CommandKind.FORM_TABS.forEach { kind ->
                    val group = commands.filter { it.kind == kind }
                    if (group.isEmpty()) return@forEach
                    SectionTitle(commandKindLabel(kind))
                    //  这一页没有表单要禁用：排序态只属于「多条」那张表单，进这一页时已经收掉了。
                    SavedCommandsSection(saved = group, ui = ui, tick = tick, onOpen = onOpen)
                }
            }
        }
    }
}

/**
 * 「命令列表」那条小标题行尾那颗「更多」弹出来的菜单 —— 排序态从这里进出。
 *
 *  与「条件执行」页的场景菜单同一种形态：一层菜单 + 一段带标题的候选
 *  （`MenuEntry.Section` 负责标题与行距，样式来源只有那一处）。
 *  单独抽一支只为让 [CommandPage] 留在 detekt 的长度上限之内。
 */
@Composable
private fun SettingsPage.StepListMenu(
    open: Boolean,
    sorting: Boolean,
    anchor: PreciseClickState,
    tick: () -> Unit,
    onClose: () -> Unit,
) {
    PageMenu(
        entries =
            if (open) {
                listOf(
                    MenuEntry.Section(
                        title = I18n.get(Strings.COMMAND_LIST_TITLE, "命令列表"),
                        rows =
                            listOf(
                                //  文案固定「编辑列表」，排序态里这一行**禁用** ——
                                //  那种状态下要退出编辑，走右下角那颗改成了「完成」的 FAB。
                                MenuEntry.Row(
                                    title = I18n.get(Strings.COMMAND_STEP_SORT, "编辑列表"),
                                    icon = PageIcons.CommandEditCog,
                                    //  列表空着 / 编辑中 ⇒ 禁用；`disabledHint` 是**这一行自己**的限制，
                                    //  不填就会掉到整页那句「系统侧还是旧版」上
                                    enabled = !sorting && commandDraft.draft.steps.isNotEmpty(),
                                    disabledHint =
                                        if (sorting) {
                                            I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED, "编辑中，先点右下角完成")
                                        } else {
                                            I18n.get(Strings.COMMAND_NEED_STEP, "还没添加步骤")
                                        },
                                ) {
                                    onClose()
                                    tick()
                                    commandDraft.stepEditing = !sorting
                                    XLog.w(if (sorting) "多条命令：退出排序态" else "多条命令：进入排序态")
                                },
                            ),
                    ),
                )
            } else {
                emptyList()
            },
        preciseClick = anchor,
        onDismiss = onClose,
    )
}

/**
 * 「命令设置」那一栏 —— 目前只有「命令间隔时长」一张卡。
 *
 *  编辑列表（排序态）时它整块禁用，且禁用态那句话要换成本页的：页面那条
 *  （`disabledClickReason`）讲的是宿主 / 旧版（给「模块没生效」用的），
 *  与「正在编辑列表」完全不是一回事。
 *
 *  单独抽一支只为让 [CommandPage] 留在 detekt 的长度上限之内。
 */
@Composable
private fun SettingsPage.CommandSettingsSection(
    sorting: Boolean,
    tick: () -> Unit,
) {
    SectionTitle(I18n.get(Strings.COMMAND_SETTINGS_TITLE, "命令设置"))
    if (sorting) {
        //  整块共用一句「编辑中…」—— 投递与槽位都在 `DisabledHint` 里
        DisabledHint(I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED, "编辑中，先点右下角完成")) {
            StepDelayCard(tick, enabled = false)
        }
    } else {
        StepDelayCard(tick, enabled = true)
    }
}

/**
 * 单条命令的正文输入（「命令」那一栏）—— **单行**（多行那一档已按用户口径撤掉）。
 *
 *  输入框本体是库的搜索框样式（见 `IconInputView`），一行就够：命令本身不该带换行 ——
 *  带换行的那串以前还得靠 `sh -c` 按行依次执行，语义反而更难说清；要串两步请用「多条命令」。
 *
 *  仍然只归一换行符：粘贴来的多半是 CRLF，混着两种会让存下去的值里夹进不可见的 `\r`。
 *
 * ## 被「支持的命令格式」点了一下之后
 *
 *  那一栏在页面下方，它够不着这里的 `FocusRequester`，所以只能举一个计数
 *  （[CommandUiState.singleFocusRequest]）。这里按这个计数做三件事，**顺序不能换**：
 *
 *   1. **先滚回顶部**。输入框在这张格式卡上面，点完不滚回去等于「填了个看不见」。
 *      滚也放在这一处，是因为焦点一落，Compose 会给文本节点做一次 `bringIntoView` ——
 *      两处同时滚就是在同一根滚动条上打架，看着像卡了一下。
 *   2. **等窗口拿到焦点，再要焦点**（键盘跟着起来）。用户点那一行的下一个动作就是接着打字。
 *      输入框里本来就有值时，这一下前面还隔着「替换」确认窗 —— 那是个独立窗口，
 *      它关掉的那一小段里页面窗口是没焦点的，此时抢到的焦点会被窗口焦点变化一并带走
 *      （「键盘弹一下又没了」）。所以先等 [FOCUS_WAIT_FRAMES]，再抢。
 *   3. **把光标挪到末尾**。`InputField` 只收 `String`，内部那份 `TextFieldValue` 的选区
 *      默认是 0 ⇒ 不挪的话接着打的字会插到 `shell:` 前面去（原因见 `moveCaretToEnd`）。
 *      挪光标走的是按键事件，得等焦点真正落到文本节点上（一两帧）才送得进去。
 *
 * @param scroll 本页的滚动状态 —— 第 1 步要用它滚回顶部
 */
@Composable
private fun SettingsPage.SingleCommandField(
    ui: CommandUiState,
    scroll: CommandPageScroll,
    enabled: Boolean,
) {
    val raw = commandDraft.draft.single
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val windowInfo = LocalWindowInfo.current
    // 初值 0 = 「还没点过」，首次组合不该抢焦点
    LaunchedEffect(ui.singleFocusRequest) {
        if (ui.singleFocusRequest == 0) return@LaunchedEffect
        scroll.state.animateScrollTo(0)
        // 窗口焦点：确认窗刚关掉那一小段里它还是 false，这时候抢到的焦点会随窗口焦点变化一起丢
        //  （`FocusOnShow` 上记着同一条）。等它回来再抢，最多等 [FOCUS_WAIT_FRAMES] 帧。
        var waited = 0
        while (!windowInfo.isWindowFocused && waited < FOCUS_WAIT_FRAMES) {
            withFrameNanos { }
            waited++
        }
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
        // 焦点落地要一两帧：早了那一下 `KEYCODE_MOVE_END` 没有节点接
        withFrameNanos { }
        withFrameNanos { }
        moveCaretToEnd(view)
    }
    // 格式不对时在这一栏下面出红字 —— 保存按钮同时是灰的（见 `commandPayloadReady`），
    //  用户得知道**为什么**存不进去。空白不算错（还没开始写）。
    val formatError =
        if (raw.isNotBlank() && !Action.hasSupportedFormat(raw)) {
            I18n.get(Strings.COMMAND_FORMAT_REQUIRED, "开头要写 shell: / cmd: 这类前缀")
        } else {
            ""
        }
    Column {
        IconInputView(
            spec =
                InputSpec(
                    PageIcons.CommandSingle,
                    I18n.get(Strings.COMMAND_CMD_HINT, "请输入命令"),
                    MAX_SINGLE_LENGTH,
                ),
            value = raw,
            onValueChange = { value ->
                // 粘贴来的多半是 CRLF：只归一，不动内容
                commandDraft.draft.single = value.replace("\r\n", "\n").replace('\r', '\n')
            },
            errorMessage = formatError,
            // 排序态下整页控件禁用（见 `CommandDraftState.stepEditing`）
            enabled = enabled,
            // 只挂 `focusRequester`、不挂 `caretToEndOnFirstFocus`：光标那一下由上面那段
            //  effect 按「填前缀」的时机做。挂上那个开关的话，只有**第一次**聚焦会挪光标，
            //  而这里要的是「每点一次格式行都挪」。
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
        )
        // 权限边界的提示：这一栏写的是 shell 命令，而它跑在宿主进程里、身份是「手势体感」这个 App ——
        // `am` / `cmd` / `settings` 这类要 shell 身份的命令会失败，得提权（root）才跑得起来。
        // 放在输入框下面一行小字：不占卡片、也不挡输入。
        //  左右缩进取 `SmallTitleDefaults.InsideMargin`（32dp），但**文字在这条带子里居中**
        //  —— 与输入框那边对齐的是**边界**，不是第一个字（用户口径：像 CSS 的
        //  `padding: 0 32px` + `text-align: center`）。所以要先铺满再居中，只给 padding 的话
        //  Text 的宽度就是文字本身，居中没有可居的余量。
        //  上下**不取那一档**（32dp 会把提示推得离输入框很远）：只留 6dp，贴着输入框说话。
        Text(
            text = I18n.get(Strings.COMMAND_SINGLE_HINT, "am / cmd 等命令需 root 给「手势体感」授权"),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = HINT_SIDE_PADDING, end = HINT_SIDE_PADDING, top = 6.dp),
            textAlign = TextAlign.Center,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 单条命令能输入多少字（超出的部分在输入框里直接吃掉）。 */
private const val MAX_SINGLE_LENGTH = 512

/**
 * 「等页面窗口重新拿到焦点」最多等几帧（60fps 下约半秒）。
 *
 *  只给「点了『支持的命令格式』之后」那一档用：如果那个确认窗还开着 / 刚关，页面窗口这一刻
 *  是**没有窗口焦点**的，此时 `requestFocus()` 抢到的焦点会随着窗口焦点变化一起丢掉 ——
 *  用户看到的就是「键盘弹一下又没了」。等不到（比如通知栏被拉下来）就照常往下走，不强求。
 */
private const val FOCUS_WAIT_FRAMES = 30

/** 权限提示那行字的左右缩进 —— 与 `SmallTitleDefaults.InsideMargin` 同值（32dp）。 */
private val HINT_SIDE_PADDING = 32.dp

/** 多条命令的「命令间隔时长」卡 —— 只有一行：等多久再派发下一条。 */
@Composable
private fun SettingsPage.StepDelayCard(
    tick: () -> Unit,
    enabled: Boolean,
) {
    CardList(
        rows =
            listOf(
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                title = I18n.get(Strings.COMMAND_DELAY_LABEL, "命令间隔时长"),
                                icon = PageIcons.DelayStep,
                                summary = I18n.get(Strings.COMMAND_DELAY_DESC, "等多久再执行下一条"),
                                min = Command.MIN_STEP_DELAY_MS,
                                max = Command.MAX_STEP_DELAY_MS,
                                //  读**多条那一份**：这张卡只出现在多条页，而 `draft.stepDelayMs`
                                //  跟 `draft.kind` 的镜像走 —— 翻页那一帧会给成单条那份的值
                                //  （滑杆随即又跳回来，看着像自己动了一下）。
                                value = commandDraft.draft.serialForm.stepDelayMs,
                                default = Command.DEFAULT_STEP_DELAY_MS,
                                // 排序态下整页控件禁用（见 `CommandDraftState.stepEditing`）
                                enabled = enabled,
                            ),
                    ) { v ->
                        tick()
                        commandDraft.draft.serialForm.stepDelayMs = v
                        // 只改草稿、不落盘（随「保存」一起入库）—— 没有失败可言，恒 true。
                        true
                    }
                },
            ),
    )
}
