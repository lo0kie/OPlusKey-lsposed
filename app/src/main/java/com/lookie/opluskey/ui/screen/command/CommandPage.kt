package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.feedback.DisabledHint
import com.lookie.opluskey.ui.input.IconInputView
import com.lookie.opluskey.ui.input.InputSpec
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.text.commandKindLabel
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.SectionTitleClick
import com.lookie.opluskey.ui.row.SectionTitleTrailingMore
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.row.TitleCounter
import com.lookie.opluskey.ui.state.command.CommandUiState
import com.lookie.opluskey.ui.state.page.CommandForm
import com.lookie.opluskey.ui.state.page.singleLine
import com.lookie.opluskey.ui.style.AnimatedColumn
import com.lookie.opluskey.ui.style.VisibleAreaCentered
import com.lookie.opluskey.ui.style.visibleAreaHeight
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

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
    //  这一页绑的是**它自己**那份表单（`formOf(kind)`），不是 `draft.*` 那条按 `draft.kind` 走的捷径：
    //  `draft.kind` 是 pager 停在哪一页的镜像，翻页过渡期镜像已经指向新页、旧页还在画 ——
    //  按镜像绑的话，过渡期敲进旧页输入框的字会写进**另一份**表单（新页那一条命令里凭空多出几个字）。
    val form = commandDraft.draft.formOf(kind)
    //  这一页那三层浮层的开合位（见 [CommandPageOverlays]）：全屏编辑 / 详情菜单 / 步骤菜单。
    val overlays = remember { CommandPageOverlays() }
    //  两颗「更多」的锚点：浮层弹在**按下的那一点**（与条件执行页的场景块同一套）。
    val listAnchor = rememberPreciseClickState()
    val detailAnchor = rememberPreciseClickState()
    //  开 / 关「命令详情」编辑弹窗时都要用它摘焦点（见下面 `openFullscreen` 与 [CommandOverlayLayer]）
    val focusManager = LocalFocusManager.current
    //  开这个弹窗时**先摘掉页面的焦点**（两条入口都走它）。
    //
    //  页面那个「命令详情」若还握着焦点，库在关窗时会无条件收键盘，那一次重新布局会让输入框
    //  为光标发一次 `bringIntoView` —— 而这一页本身在 `Pager` 里，分页器会把「焦点所在那一页」
    //  重新滚回视野，整行字于是**横向滑一下**（真机口径「关掉弹窗后那行字从左往右动了一下」）。
    //  开窗之前就摘掉，等到关窗时它早就不是焦点，那一下没人发。
    //  与 `CommandScreen` 里「换 tab 先 clearFocus」是同一条账。
    val openFullscreen = {
        focusManager.clearFocus()
        overlays.fullscreen = true
    }

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
            SectionTitle(
                text = I18n.get(Strings.COMMAND_NAME_TITLE, "命令名称"),
                //  行尾是**字数统计**：输入框本身不画计数（`InputSpec.maxLength` 只截断），
                //  上限写在这一行才读得到 —— 与弹窗标题行那一格同一套画法（`TitleCounter`）。
                trailing = { CommandNameCounter(form) },
            )

            // 输入项用 COUI 的「卡片输入」形态（[IconInputView]）：一个输入项一张卡、无边框，
            // 行首图标会把标题与输入框一起挤开 —— 库的 `InputView` 做不到，所以自己拼。
            // 抽成独立的 composable 是为了把 `form.name` 的读取关在里面（见它的注释）。
            CommandNameField(form, enabled = !sorting)

            // 单条命令的那几栏 —— 与「名称」同一套卡片输入；多条命令走下面那张「步骤」卡。
            if (kind == CommandKind.SINGLE) {
                CommandDetailTitle(
                    length = form.single.length,
                    sorting = sorting,
                    anchor = detailAnchor,
                    onFullscreen = openFullscreen,
                    onMenu = { overlays.detailMenu = true },
                )
                SingleCommandField(ui, scroll, form, enabled = !sorting)
                //  「可用变量」紧跟在正文后面：这一档说的是「正文里能写什么值」（系统场景那几档状态），
                //  与下面「支持的命令格式」是一对 —— 那边讲「以什么开头」，这边讲「中间能插什么」。
                //  页面上只有一行入口、点开是一层菜单（见 `CommandVarsCard`）：十一行常驻会把
                //  正文到「命令设置」之间撑得很长。
                CommandVarsCard(form, ui, enabled = !sorting)
                //  「图标」只在正文是 `fluid:` 时出现（那一档决定胶囊左边画什么）：
                //  点开是网格选择器，选中即渲染成 png 落盘，命令里只记图标 id。
                CommandFluidIconCard(form, enabled = !sorting)
                //  「命令设置」对单条只有一件事：这条命令要不要以 root 执行。
                //  它是**每条命令自己的**开关（存在命令上、随命令一起落盘），并且**只在正文是
                //  `shell:` 时出现** —— 那一行的小标题与卡片一起进退，所以这里不再单独留一条
                //  （见 `RootSwitchCard`）。
                RootSwitchCard(form, enabled = !sorting)
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
                CommandStepListSection(anchor = listAnchor, onMenu = { overlays.listMenu = true })
            }

            //  「已保存的命令」不在这里了 —— 它是第三个页签（见 [CommandListPage]）：
            //  原先挂在两张表单的页尾、每页只列自己那一类，翻页才看得见另一半。
        }
        //  排序态的入口在「命令列表」那条小标题的行尾（见它上面那个 `trailing`），
        //  不再用右下角那颗 FAB —— 与「条件执行」页的场景块同一个形态。
        CommandOverlayLayer(overlays, sorting, tick, detailAnchor, listAnchor)
    }
}

/**
 * 「命令详情」那条小标题（单条命令页）—— 标题自己可点（开全屏编辑），行尾一颗「更多」。
 *
 *  抽出来：这一块的条件与文案都在这里，页面那一支只剩「单条这一栏要不要画」。
 */
@Composable
private fun SettingsPage.CommandDetailTitle(
    /** 这一条命令现在写了多少字（上限 [MAX_SINGLE_LENGTH]）。 */
    length: Int,
    sorting: Boolean,
    anchor: PreciseClickState,
    onFullscreen: () -> Unit,
    onMenu: () -> Unit,
) {
    SectionTitle(
        text = I18n.get(Strings.COMMAND_DETAIL_TITLE, "命令详情"),
        //  「点标题」这件事走 [SectionTitleClick] 那一套（与「已保存的命令」同一条）：
        //  标题自己可点，行尾那颗指示按这一档画成**全屏**（`Expand`）——
        //  它是在原地弹一层，不是跳页，所以不用跳页那颗右箭头。
        click = if (sorting) null else SectionTitleClick.Expand(onFullscreen),
        //  行尾那颗「更多」：与「命令列表」同一套（`MoreButton` + 一层菜单）。
        //  标题自己可点（开全屏编辑），菜单里再给一次同样的入口 + 测试命令 ——
        //  「测试」这一档只有菜单里放得下。
        //
        //  计数是「正文写了几个字 / 最多几个字」：与全屏编辑那条标题同一个 `TitleCounter`
        //  （`CommandFullscreen`）—— 输入框只截断、不画计数，上限得在这一行读到。
        trailing = {
            SectionTitleTrailingMore(
                anchor = anchor,
                onMenu = onMenu,
                length = length,
                max = MAX_SINGLE_LENGTH,
                enabled = !sorting,
            )
        },
    )
}

/**
 * 「命令列表」那条小标题 + 步骤卡（多条命令页）—— 行尾那颗「更多」进排序态。
 *
 *  与 [CommandDetailTitle] 同一个理由抽出来：`CommandPage` 只管「哪一栏画什么」。
 */
@Composable
private fun SettingsPage.CommandStepListSection(
    anchor: PreciseClickState,
    onMenu: () -> Unit,
) {
    SectionTitle(
        text = I18n.get(Strings.COMMAND_LIST_TITLE, "命令步骤"),
        //  计数是「几步 / 最多几步」—— 上限是 `Command.MAX_STEPS`，加到最后一步时「添加命令」
        //  那行会自己置灰；计数摆在这里，不用点进去数一遍。
        trailing = {
            SectionTitleTrailingMore(
                anchor = anchor,
                onMenu = onMenu,
                length = commandDraft.draft.serialForm.steps.size,
                max = Command.MAX_STEPS,
            )
        },
    )
    //  **不要再套一层 `Card`**：`SerialSteps` 自己就是两张 `CardList`（步骤卡 + 「添加命令」那张，
    //  各自自带卡片、分隔线与左右 `PAGE_MARGIN`）。外面再包一层就成了「卡里还有一张卡」——
    //  观感是两层卡片、行看着一颗颗独立（真机反馈「看起来有两层 cardlist」）。
    //  加 / 删步骤的过渡仍由 `SerialSteps` 每行的 expand / shrink 驱动，不在这里。
    SerialSteps()
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
    //  （`fillMaxHeight()` 在那儿没有意义）—— 账走全项目那一支（[visibleAreaHeight]）。
    //  空态**仍然留在滚动内容里**：它得跟着内容一起拖、一起弹，挪出滚动容器就再也越界不动了。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val emptyHeight =
            visibleAreaHeight(
                maxHeight = maxHeight,
                topInset = currentHeaderHeight(scroll.behavior) + scroll.tabBlockDp,
                bottomInset = PAGE_BOTTOM_PADDING,
            )
        PageScrollColumn(scrollBehavior = scroll.behavior, state = scroll.state) {
            // 顶栏叠在内容上，顶部这块要自己让出来；再把类型选择器固定带一起让出。
            Spacer(Modifier.height(currentHeaderHeight(scroll.behavior) + scroll.tabBlockDp))
            if (commands.isEmpty()) {
                //  一条都没有：撑满可见区并正中（高度已减掉顶栏、分页带与页底留白，
                //  所以居中落在**看得见的那块**正中间，不多出一段能拖的空白）。
                VisibleAreaCentered(emptyHeight) {
                    //  「一条都没有」与「读不出来」是两件事：后者会让用户以为是自己删光了，
                    //  也看不出为什么存进去的东西不见（见 `ConfigState.commandsReadOnly`）。
                    SavedCommandEmptyState(unreadable = configState.commandsReadOnly)
                }
            } else {
                //  两段各一条小标题；**空的那一段整段不画**（一条命令都没有的那类，留一条光标题
                //  + 空网格只是占地方）。
                CommandKind.FORM_TABS.forEach { kind ->
                    val group = commands.filter { it.kind == kind }
                    if (group.isEmpty()) return@forEach
                    //  条数走**行尾的 `n/50`**（`TitleCounter`）：这一页有上限，光写「有几条」
                    //  看不出还剩几格 —— 与「命令名称 / 命令详情」那两格同一个画法。
                    //  两段各自数各自那几条，空的那段整段不画，所以这里写出来的都是真数。
                    SectionTitle(
                        text = commandKindLabel(kind),
                        trailing = { TitleCounter(group.size, Command.MAX_COMMANDS) },
                    )
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
 */
@Composable
internal fun SettingsPage.StepListMenu(
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
                        title = I18n.get(Strings.COMMAND_LIST_TITLE, "命令步骤"),
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
                                            I18n.get(Strings.COMMAND_NEED_STEP, "尚未添加步骤")
                                        },
                                ) {
                                    onClose()
                                    tick()
                                    commandDraft.stepEditing = !sorting
                                    XLog.w(if (sorting) "多条命令：退出排序态" else "多条命令：进入排序态")
                                },
                                MenuEntry.Row(
                                    title = I18n.get(Strings.COMMAND_TEST_BUTTON, "测试命令"),
                                    icon = PageIcons.TestCommand,
                                    enabled = commandTestable,
                                    disabledHint = commandTestHint,
                                ) {
                                    onClose()
                                    //  跑的是**眼前这份草稿**，不是库里那一条 —— 草稿没保存也测得了
                                    testCommand(draftCommand())
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
 * 「测试命令」那一行的可用判据 —— 两个菜单（「命令列表」与「命令详情」）共用这一处。
 *
 *  测试跑的是**眼前这份草稿**（`testCommand(draftCommand())`，见 `draftCommand`）：草稿没保存也能测，
 *  所以判据只剩「有没有东西可测」—— 单条要写了正文、多条要至少一步；排序态下整页控件都停着，
 *  也一并排掉。
 */
private val SettingsPage.commandTestable: Boolean
    get() {
        if (commandDraft.stepEditing) return false
        val draft = commandDraft.draft
        return when (draft.kind) {
            CommandKind.SINGLE -> draft.single.isNotBlank()
            CommandKind.SERIAL -> draft.steps.isNotEmpty()
        }
    }

/** 灰着时那句「为什么」—— 与 [commandTestable] 同源，别让两处各写一份条件。 */
private val SettingsPage.commandTestHint: String
    get() =
        if (commandDraft.stepEditing) {
            I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED, "编辑中，先点右下角完成")
        } else if (commandDraft.draft.kind == CommandKind.SERIAL) {
            I18n.get(Strings.COMMAND_NEED_STEP, "尚未添加步骤")
        } else {
            I18n.get(Strings.COMMAND_PAYLOAD_INCOMPLETE, "命令内容还不完整")
        }

/**
 * 「命令详情」小标题行尾那颗「更多」弹出来的菜单 —— 全屏编辑 + 测试命令。
 *
 *  标题自己已经可点（开全屏编辑），菜单里这一档是给「手上正在这一行」的人一个统一入口；
 *  真正只有菜单里才有的那一档是**测试命令**。
 */
@Composable
internal fun SettingsPage.DetailMenu(
    open: Boolean,
    anchor: PreciseClickState,
    tick: () -> Unit,
    onClose: () -> Unit,
    onFullscreen: () -> Unit,
) {
    PageMenu(
        entries =
            if (open) {
                listOf(
                    MenuEntry.Section(
                        title = I18n.get(Strings.COMMAND_DETAIL_TITLE, "命令详情"),
                        rows =
                            listOf(
                                MenuEntry.Row(
                                    title = I18n.get(Strings.COMMAND_FULLSCREEN_EDIT, "全屏编辑"),
                                    icon = PageIcons.CommandEdit,
                                    enabled = !commandDraft.stepEditing,
                                    disabledHint =
                                        I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED, "编辑中，先点右下角完成"),
                                ) {
                                    onClose()
                                    tick()
                                    onFullscreen()
                                },
                                MenuEntry.Row(
                                    title = I18n.get(Strings.COMMAND_TEST_BUTTON, "测试命令"),
                                    icon = PageIcons.TestCommand,
                                    enabled = commandTestable,
                                    disabledHint = commandTestHint,
                                ) {
                                    onClose()
                                    //  同样跑草稿：编辑到一半要试一下，不必先保存
                                    testCommand(draftCommand())
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
 * 「以 root 执行」那张卡 —— 单条命令**自己**的一项设置（多条命令的步骤没有各自的开关）。
 *
 *  两档互不兜底：开 = 只走 `su`（拿不到 root 就是这次失败，**不**退回 `sh`），关 = 只走 `sh`。
 *  这台机器的宿主域没有 `/system/bin/sh` 的执行权，关掉之后多数命令会**起不来**
 *  （失败提示会报「要 root」），所以副标题把两种走法都写清楚，不替用户做判断。
 *
 * ## 为什么只在 `shell:` 时出现
 *
 *  这个开关管的是**执行身份**，而只有「另起一个进程跑一段命令」那一档有身份可挑 ——
 *  `toast:` / `notify:` / `fluid:` / `keyevent:` 这些走的是模块自己的通道，从头到尾不经过
 *  shell，开了也不会有任何差别（判据见 [Action.usesRoot]）。
 *
 *  所以正文不是 `shell:` 时整张卡（含上面那条小标题）滑出去 —— 与「图标」那一档同一套
 *  动效（`AnimatedColumn`）：它夹在正文与「支持的命令格式」之间，硬切会把下面那一大块整体顶一下。
 *
 *  显隐**只看前缀、不看 `parse` 的结果**：与 `CommandFluidIconCard` 同一条口径 —— 正文写到
 *  一半（`shell:` 后面还没写字）时这张卡不该忽隐忽现。
 */
@Composable
private fun SettingsPage.RootSwitchCard(
    form: CommandForm,
    enabled: Boolean,
) {
    val text = form.single.trim()
    val show = remember(text) { Action.parse(text).usesRoot }
    //  小标题与卡片一起进退（`AnimatedColumn` 里面自带 `Column`，两行不会叠在一处）
    AnimatedColumn(visible = show) {
        SectionTitle(I18n.get(Strings.COMMAND_SETTINGS_TITLE, "命令设置"))
        CardList(
            rows =
                listOf(
                    CardRow.Switch(
                        title = I18n.get(Strings.COMMAND_ROOT_TITLE, "以 root 执行"),
                        summaryOn = I18n.get(Strings.COMMAND_ROOT_DESC_ON, "只用 su，不再退回 sh"),
                        summaryOff = I18n.get(Strings.COMMAND_ROOT_DESC_OFF, "只用 sh，不申请 root"),
                        icon = PageIcons.CommandRoot,
                        checked = form.root,
                        // 排序态下整页控件禁用（见 `CommandDraftState.stepEditing`）
                        enabled = enabled,
                        //  打开这一档就顺手申请一次授权（没有 root 时那句提示会顶上来）；
                        //  关掉那一档只用 `sh`，没有东西可申请。
                        onCheckedChange = { on ->
                            form.root = on
                            if (on) requestRootGrant()
                        },
                    ),
                ),
        )
    }
}

/**
 * 单条命令的正文输入（「命令」那一栏）—— **单行**（多行那一档已按用户口径撤掉）。
 *
 *  输入框本体是库的搜索框样式（见 `IconInputView`），一行就够：命令本身不该带换行 ——
 *  带换行的那串以前还得靠 `sh -c` 按行依次执行，语义反而更难说清；要串两步请用「多条命令」。
 *
 *  **换行在写进草稿那一刻就折掉**（`CommandForm.single` → `singleLine`）：粘贴一段多行脚本时，
 *  单行输入框拿到带换行的值会一个字都看不见（值还在）。折成 `; ` 串联既保住了「依次执行」，
 *  也保住这一栏能看见。
 *
 * ## 被「支持的命令格式」点了一下之后
 *
 *  那一栏在页面下方，它够不着本页的滚动状态，所以只能举一个计数
 *  （[CommandUiState.singleScrollRequest]）。这里按这个计数**只做一件事：滚回顶部**
 *  —— 输入框在那张格式卡上面，点完不滚回去等于「填了个看不见」。
 *
 *  滚也放在这一处（而不是格式卡那一侧）：卡片够不着本页的滚动状态，而滚与「让输入框可见」
 *  混在两处，就是在同一根滚动条上打架，看着像卡了一下。
 *
 *  **不抢焦点、不许键盘、不挪光标**（用户口径：点命令格式不再聚焦）：点一行格式是
 *  「挑一个前缀」，不是「要在这儿打字」；抢了焦点键盘就顶上来，把刚点的那张格式卡盖掉。
 *
 * @param scroll 本页的滚动状态
 */
@Composable
private fun SettingsPage.SingleCommandField(
    ui: CommandUiState,
    scroll: CommandPageScroll,
    form: CommandForm,
    enabled: Boolean,
) {
    val raw = form.single
    // 初值 0 = 「还没点过」，首次组合不该滚
    LaunchedEffect(ui.singleScrollRequest) {
        if (ui.singleScrollRequest == 0) return@LaunchedEffect
        scroll.state.animateScrollTo(0)
        //  **一次举手只兑现一次**：清回 0。这个组合会随翻页被销毁 / 重建，而计数是页面级的
        //  —— 不清的话「切走再切回来」会按旧计数再滚一次。
        ui.singleScrollRequest = 0
    }
    // 格式不对时在这一栏下面出红字 —— 保存按钮同时是灰的（见 `commandPayloadReady`），
    //  用户得知道**为什么**存不进去。空白不算错（还没开始写）。
    val formatError =
        if (raw.isNotBlank() && !Action.hasSupportedFormat(raw)) {
            I18n.get(Strings.COMMAND_FORMAT_REQUIRED, "必须以支持的格式开头")
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
                //  值进来时已经折过换行（见下面的 `sanitize`），这里只管往上交。
                form.single = value
            },
            errorMessage = formatError,
            // 排序态下整页控件禁用（见 `CommandDraftState.stepEditing`）
            enabled = enabled,
            // **不再挂 `focusRequester`**：这一栏没有「从别处被叫来打字」的场景了（见函数头）。
            // 也不挂 `caretToEndOnFirstFocus`：用户自己点进来时，光标该落在点的地方。
            modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
            // **这一栏换 `singleLine`**：多行脚本粘进来是「依次执行」，要折成 `; `；
            //  `AppInputField` 默认那道折成空格，折成空格就成了一条语法错误的命令。
            sanitize = ::singleLine,
        )
        //  输入框底下那句**格式提示**：现在写的是哪个前缀，就说那一种后面该跟什么
        //  （`keyevent:<keycode>（数字）`）。认不出前缀时整句不画、也不占位 ——
        //  于是它和上面那条红字天然互斥：红字出现时前缀本来就不合法。
        //
        //  画在**这一层**（不是页面那一层）：`raw` 已经在这儿读着了，页面那边再读一次
        //  等于把整页拖进每一次击键的重组里（见 `CommandNameField` 同一个理由）。
        CommandFormatTip(raw)
    }
}

/** 单条命令能输入多少字（超出的部分在输入框里直接吃掉）—— 取 1024，够放下一条带好几个参数的长命令。 */
internal const val MAX_SINGLE_LENGTH = 1024

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
