package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.LocalSheetBringIntoView
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.ModeMenuPopups
import com.lookie.opluskey.ui.ModeMenuTarget
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.PageSheet
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.SHEET_TITLE_MARGIN
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.boundAppName
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.actionRowView
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.selectedRowRing
import com.lookie.opluskey.ui.sheetCardColors
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

// 各处下拉/浮层选择菜单（添加命令、响铃状态、命令卡菜单）。
//
// `CommandMenuPopup` 是**命令卡的唯一菜单**：从卡片右上角那颗「更多」进，
// 四行固定是「预览 / 测试 / 编辑 / 删除」—— 菜单本身不关心从哪儿来（落点由调用点给的锚点决定）。
// 三项动作走 `CommandMenuActions` 一个对象：这一支连同 `onDismiss` 原来是七格，而 detekt 的
// 参数上限是六（本仓不许往基线里添条目，就只能把参数收进一个对象）。

/**
 * 命令卡菜单里三行各自「点了往哪儿去」—— 合成一个对象只为把 [CommandMenuPopup] 的参数个数
 * 压回 detekt 的上限，也为了让调用点一处看清这几行都通到哪里。
 *
 *  三项都**不给默认值**：唯一调用点三样全给，默认值只会让「漏传」静默通过。
 *
 *  @param onEdit 进编辑态之后调 —— 调用方借此把页面滚回顶部（表单在页面最上面）
 *  @param onPreview 「预览」：与轻点卡片同一个去处（整屏那一层从卡片长出来）
 *  @param onDelete 「删除」：交给调用点弹确认窗（真删在确认之后）
 */
internal class CommandMenuActions(
    val onEdit: () -> Unit,
    val onPreview: () -> Unit,
    val onDelete: () -> Unit,
)

/** 命令卡那张四行菜单 —— 行内容与去向在 [CommandMenuActions] 那边。 */
@Composable
internal fun SettingsPage.CommandMenuPopup(
    cmd: Command,
    show: Boolean,
    preciseClick: PreciseClickState,
    /** 点了哪一行之后往哪儿去（见 [CommandMenuActions]）。 */
    actions: CommandMenuActions,
    onDismiss: () -> Unit,
) {
    // 页面离开时由 MenuPopup 自己登记收走
    PageMenu(
        entries =
            listOf(
                // 「预览」排第一：与轻点卡片同一件事，菜单里再留一处入口 ——
                //  手指停在卡片上时不必先挪开再点一下，直接从这儿进。
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMAND_DETAIL_TITLE, "命令详情"),
                    icon = PageIcons.ZoomIn,
                ) {
                    onDismiss()
                    actions.onPreview()
                },
                // 「测试」—— 把这条命令当场跑一遍（不写配置、不改绑定）。原先它是卡片右上角单独一颗，
                //  收进菜单之后那颗位置让给了「更多」。
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMAND_TEST_BUTTON, "测试命令"),
                    icon = PageIcons.TestCommand,
                ) {
                    onDismiss()
                    testCommand(cmd)
                },
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_EDIT, "编辑"),
                    icon = PageIcons.CommandEdit,
                ) {
                    onDismiss()
                    //  已经在编这一条、而草稿又**没动过** ⇒ 这一下是「重置」：清掉表单、退出编辑态。
                    //  草稿有改动时不在这里重置 —— 交给 [SettingsPage.loadDraft] 先问一句「放弃未保存的
                    //  命令」（窗见 `CommandDialogs.SwitchDraftDiscardDialog`），确认后才动草稿。
                    if (commandDraft.draft.editingName == cmd.name && !commandDraft.draft.dirty) {
                        XLog.w("已保存命令菜单：再次点「编辑」且草稿无改动 ⇒ 重置表单（${cmd.name}）")
                        //  清的是**这条命令所属那一类**的表单，不是「当前停着的那一页」：
                        //  在「命令列表」页点时，分页器停在列表那格，`form` 指向的是上次离开时那一页，
                        //  按 `cmd.kind` 取才不会清错（`formOf` 见 `CommandDraft`）。
                        commandDraft.draft.formOf(cmd.kind).reset()
                        actions.onEdit()
                    } else if (loadDraft(cmd)) {
                        // 走 [SettingsPage.loadDraft] 而**不是** `draft.load`：表单里可能还有没保存的改动，
                        //  直接灌进去会把它整份覆盖掉。它返回 false 表示只挂起了确认窗 —— 那样
                        //  这一下就**不切页**，等窗确认之后由窗自己发起（见 `CommandDialogs`）。
                        actions.onEdit()
                    }
                },
                // 两项之间那条细线由菜单自己加
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_DELETE, "删除"),
                    icon = PageIcons.CommandDelete,
                    danger = true,
                ) {
                    onDismiss()
                    actions.onDelete()
                },
            ).takeIf { show }
                .orEmpty(),
        preciseClick = preciseClick,
        onDismiss = onDismiss,
    )
}

/**
 * 「已保存的命令」那张卡右上角的浮层菜单 —— [CommandMenuPopup] 的宿主。
 *
 *  单独一支、并且落在本文件的两个理由：`CommandTile` 与 `SavedCommands.kt` 都贴着 detekt 的
 *  「单函数长度」「单文件函数数」上限；而这几行说的正是「菜单里点哪一行往哪儿去」，本来就属于
 *  命令卡菜单这一支。
 *
 *  @param scope 卡外层那个 `Box` 的作用域 —— 浮层要贴右上角（`align`），落点仍由触点决定。
 *  @param tileAnchor 整张卡那一份锚点：菜单自己那次没记到落点时兜一下。
 *  @param onOpen 点「编辑」之后去哪（灌草稿在 `CommandTile` 里做，跳页由调用方做）。
 */
@Composable
internal fun SettingsPage.CommandTileMenu(
    scope: BoxScope,
    cmd: Command,
    ui: CommandUiState,
    tileAnchor: PreciseClickState,
    onOpen: (Command) -> Unit,
) {
    with(scope) {
        Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(0.dp)) {
            CommandMenuPopup(
                cmd = cmd,
                show = ui.menu?.name == cmd.name,
                //  开着的时候这里拿到的必然是**这一次**的锚点（名字对得上才画）
                preciseClick = ui.menu?.anchor ?: tileAnchor,
                actions =
                    CommandMenuActions(
                        // 与「轻点这一张」同一个去处：灌进表单后滚回顶部。
                        onEdit = { onOpen(cmd) },
                        // 与轻点卡片同一个去处：整屏预览（从这张卡长出来）
                        onPreview = {
                            ui.menu = null
                            XLog.w("命令预览：${cmd.name}（菜单）")
                            ui.detail = cmd
                        },
                        onDelete = {
                            ui.menu = null
                            ui.deleting = cmd
                        },
                    ),
                onDismiss = { ui.menu = null },
            )
        }
    }
}

// 行尾菜单那几张浮层同样用 `ListPopupDefaults.DropdownPositionProvider` + 触点锚点。

// --------------------------------------------------------------- 添加命令（底部抽屉）

/**
 * 抽屉候选表的两个开关 —— 与 [StepSheetSpec] 分开，只为把那个类的参数个数留在 detekt 的上限之下。
 *
 *  两样都是「这次列什么」：排除哪几条已保存命令、要不要列「无操作」。
 */
internal class StepSheetFilter(
    /**
     * 这一次**不要列出来**的那几条命令（`cmd:<名字>`），空集 = 不排除。
     *
     *  编辑「命令 A」时，它自己也会出现在「已保存的命令」那一组里 —— 选中它就是把 A 塞进 A 的
     *  某一步。执行侧虽然有一道环闸（`CommandFailure.TOO_DEEP`）会拦住，但那是**事后**报错：
     *  用户点完才收到「套太深」。界面上直接不给这一行，比让他撞一次墙更省事。
     *
     *  **为什么是集合而不是一个名字**：编辑态下草稿名与库里那条的名字可以不同 —— 用户把 A 改成
     *  B、还没保存时，草稿叫 B、库里那条叫 A。只按草稿名排除的话，A 仍留在候选里：选中它会把
     *  A 塞进 B 的步骤，保存时 A 被摘掉、B 里留下一条悬空的 `cmd:A`，而成环检查按名字也查不出来
     *  （目标已经不存在）。两个名字都要排除。
     */
    val excludeTokens: Set<String> = emptySet(),
    /**
     * 候选表里**要不要列「无操作」**。
     *
     *  只有长按卡的「松手执行额外动作」传 `true`：那一步本来就是可选的，「配了但这次不做事」是一档
     *  真取值。多条命令的步骤不传 —— 步骤不是按键，那一档在那里等于白占一格（见 `STEP_GROUPS`）。
     *  （场景块的三档动作在**网格**里挑，不走这张抽屉，见 `ActionCatalog.BRANCH_GROUPS`。）
     *
     *  **它只决定「列不列无操作」，不换表**：以前这一位顺手把整张表换成 [ActionCatalog.BRANCH_GROUPS]，
     *  于是「跟随默认」跟着漏进了长按那一行 —— 而它只属于场景块的三档（真机反馈「松手那一行能挑到
     *  跟随默认」）。
     */
    val includeNone: Boolean = false,
    /**
     * 这一次**不列**「选择应用」「自定义变量」那两格**关卡**（`true` = 不列）。
     *
     *  那两格不是「落一步」，而是「换一页去挑」。有些来处没有那条去路 —— 例如长按卡里的
     *  「松手执行额外动作」：它只写一个动作字段，不经过挑应用 / 挑变量那几页。
     *  列出来等于让人点一下抽屉关门、什么都不发生。
     *
     *  「应用控制」不在此列：它那一层菜单里的打开 / 结束两档，额外动作**也有**去路
     *  （写进 `longRelease`，见 `openAppPickerForRelease`）。
     */
    val excludeTiles: Boolean = false,
)

/**
 * 「添加命令 / 选择命令」那张抽屉的入参 —— 合成一个对象只为把参数个数压回 detekt 的上限，
 * 也为了让两个调用点一眼看出「这几样是这一趟的来处」。
 *
 *  @param title 抽屉标题（「添加命令」/「选择命令」—— 由调用点按来处给）
 *  @param current 当前选中的动作：命中那一行行首换对勾、整行主题色；`null` = 谁都不打勾。
 *   由调用点算好递进来，抽屉自己不读草稿：这一支两处用（多条命令的某一步 / 条件执行的某条动作），
 *   各自的「当前」不是同一份数据。
 *  @param onOpenPicker 抽屉里那两行**关卡**（「选择应用」/「自定义变量」）点下去去哪一页 ——
 *   都由调用点定：写某一步 / 写某一条规则。抽屉自己不认页，只把这一个「哪一关」交出去。
 *  @param onPick 挑中一个动作的 token（由调用方决定是追加还是替换）
 */
internal class StepSheetSpec(
    val title: String,
    val current: Action?,
    val onOpenPicker: (StepPickerPage) -> Unit,
    val onPick: (String) -> Unit,
    val onDismiss: () -> Unit,
    val filter: StepSheetFilter = StepSheetFilter(),
)

/**
 * 抽屉里那几行**关卡** —— 点它们不是「落一步」，而是「换一页去挑」。
 *
 *  这几行的行为形态完全一样（收抽屉 → 压一页），只是目的地不同；合成一个枚举是因为
 *  [StepSheetSpec.onOpenPicker] 只收一个回调 —— 多几个参数会让那个类多好几格，
 *  而「去哪一页」本来就是调用点一个 `when` 里的事。
 */
internal enum class StepPickerPage {
    /** 「打开应用」→ 挑应用页（落 `app:` / `window:` 的一步）。 */
    App,

    /** 「结束应用」→ **同一张**挑应用页，只是落 `proc:stop:包名`（见 `AppPickerState.stopApp`）。 */
    AppStop,

    /** 「自定义变量」→ 「变量操作」页（落 `setvar:` 的一步）。 */
    Vars,
}

/**
 * 「添加命令 / 选择命令」点开的**底部抽屉** —— 挑一个动作落成一步，或换掉某一步。
 *
 *  三个来处共用这一支：「多条命令」那颗「添加命令」（追加）、点某一步（换掉它）、条件执行
 *  某一条的「动作」。三者的差别只在 [title] / [current] / [onOpenPicker] / [onPick] 由调用点给，
 *  抽屉自己不读草稿 —— 「选了之后写回哪儿」是各页的语义，不归容器。
 *
 *  行用项目通用的 `CardList` + `CardRow.Item`，与页面里其它卡同一套行高与分隔线。行尾字形按
 *  「点开去哪」分：选择应用 / 自定义变量给右箭头（进另一页）、入口格给选择器字形（还有一层菜单）、
 *  直接落成一步的不给。
 *
 *  内置动作那几组之后是**「已保存的命令」**：多命令的步骤、条件执行的动作，挑的多半是自己写的那几条，
 *  摆在末尾不用先翻过整张内置表。这一组来自命令库（`configState.commands`），点一行落成 `cmd:<名字>`；
 *  **单条与多条都列** —— 条件执行那一支的动作本来就是 `cmd:<名字>`，多条命令合法；多条命令的
 *  「某一步」引用另一条多条命令，执行侧也认（深度与环都有闸）。
 *
 *  这一支是抽屉的**一次完整事务**：开关登记、六个「点了还没生效」的选中态、点行的三条去向
 *  （挑应用 / 开二级 / 只记高亮）、抽屉面配色、内容滚动。拆开就得把这几个状态连同它们的重置
 *  一起搬出去 —— 那只是把 `remember` 换成参数，调用点反而更难看出「这一下点了会发生什么」。
 *  行与卡已经拆成 `StepSheetGroups` / `SavedCommandsGroup` / `StepSheetCard`，这里剩下的都是编排。
 *
 *  @param sheet 这一趟的标题、当前选中与三条回调（见 [StepSheetSpec]）
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun SettingsPage.StepPickerSheet(
    show: Boolean,
    sheet: StepSheetSpec,
) {
    //  登记（系统返回只收抽屉、下拉跟手那一套）与面板底色都在 [PageSheet] 里，这里只管内容。
    //
    //  抽屉里每一次点击都走通用振动通道（与页面里其它控件同一条，判据只有系统状态）。
    val tick = rememberTick()
    // 开着二级菜单的那一格（入口格那些：系统开关 / 系统导航 / 媒体控制…）。`null` = 没开。
    var subTile by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    //  这一次列哪张候选表：要不要「无操作」只差这一行，**不换表** ——
    //  `BRANCH_GROUPS` 是场景块那张（还带「跟随默认」），只该给场景块的三档动作用（见
    //  [StepSheetFilter.includeNone]）。
    val groups =
        (if (sheet.filter.includeNone) ActionCatalog.GROUPS else ActionCatalog.STEP_GROUPS)
            .let { base ->
                if (!sheet.filter.excludeTiles) {
                    base
                } else {
                    //  这一趟没有「挑变量」那条去路（见 `StepSheetFilter.excludeTiles`）：
                    //  变量那一格剔掉，点了只会关抽屉、什么都不发生。
                    //  「应用控制」**留着** —— 额外动作也能挑应用 / 组件
                    //  （写回见 `PickTarget.Release`，两档都走 `openAppPickerForRelease`）；
                    //  它旁边那个 `APP_TILE`（「选择应用」）是同一件事的另一个入口，留一个就够。
                    base.map { (groupTitle, specs) ->
                        groupTitle to
                            specs.filter {
                                it.token != ActionCatalog.APP_TILE && it.token != ActionCatalog.VARS_TILE
                            }
                    }
                }
            }

    //  这一行画不画成「选中」（行首对勾 + 整行主题色）：看当前动作。
    //  点一下就落盘、抽屉随即收掉（见 `land`），所以这里没有「刚点过、还没生效」那一档了。
    fun isCurrent(spec: ActionCatalog.Spec): Boolean = sheet.current?.let { ActionCatalog.isSelected(spec, it) } ?: false
    // 每一行**自己的**锚点。**不能共用一份**：`preciseClickAnchor` 是靠 `onGloballyPositioned`
    //  把「这一行」的坐标写进 state 的，而 `anchorCoordinates` 只有一格 —— 共用时后布局的行会把
    //  前面的覆盖掉，按下时 `localToWindow(down.position)` 拿的是**别的行**的坐标 ⇒ 菜单弹在
    //  不相干的位置，菜单也就不跟手指。
    //  一次按候选表建好，行的 `modifier` 直接取自己那份。
    val anchors =
        remember(groups) {
            groups
                .flatMap { it.second }
                .associateWith { PreciseClickState() }
        }
    //  「已保存的命令」那一组是**动态**的（来自命令库），静态表里没有它的条目 —— 锚点按当前命令库
    //  现建一份。这一组都是 `cmd:<名字>`，点下去不再开下一层，锚点只用来撑住行组件那个参数。
    val savedSpecs =
        configState.commands.value
            //  单条与多条**都列**：条件执行那一支的动作就是 `cmd:<名字>`，多条命令完全合法；
            //  多条命令的「某一步」引用另一条多条命令，执行侧也认（深度与环都有闸）。
            //  以前这里只留单条，抽屉里于是看不到自己写的命令链。
            //
            //  **正在编辑的那条自己除外**（见 [StepSheetFilter.excludeTokens]）：引用自己就是环，
            //  执行侧会报「套太深」，不如别让它出现在候选里。
            .filter { it.token !in sheet.filter.excludeTokens }
            .map { ActionCatalog.commandSpec(it) }
    val savedAnchors = remember(savedSpecs) { savedSpecs.associateWith { PreciseClickState() } }
    // 开着二级菜单那一行的锚点（开的时候记下来，`ModeMenuPopups` 用）；
    //  没记录到就给 [coldAnchor] 兜一下（真走到那里说明触点丢了）
    var subAnchor by remember { mutableStateOf<PreciseClickState?>(null) }
    val coldAnchor = remember { PreciseClickState() }

    //  关抽屉时**两样一起复位**：开着二级菜单的那一格、它的锚点。
    //  只清一样的话，系统返回 / 下拉把抽屉收掉之后 `subTile` 还挂着 —— 而 `show` 是行参、
    //  这个组合并不随之销毁，于是那一层菜单仍开在半空：随手选一档就把一步**静默加进草稿末尾**。
    LaunchedEffect(show) {
        if (!show) {
            subTile = null
            subAnchor = null
        }
    }

    fun land(
        spec: ActionCatalog.Spec,
        anchor: PreciseClickState,
    ) {
        tick()
        when {
            // 「选择应用」不能落成一个空的 `app:`：交给挑应用页（去哪儿由调用点说）
            spec.token == ActionCatalog.APP_TILE -> {
                sheet.onDismiss()
                sheet.onOpenPicker(StepPickerPage.App)
            }

            // 「自定义变量」同理：它代表的是一整族 `setvar:` 写法，落成一步只会写出一个
            //  `Action.parse` 认不出的废串 —— 交给「变量操作」页挑（挑变量 → 挑操作 → 落盘）
            spec.token == ActionCatalog.VARS_TILE -> {
                sheet.onDismiss()
                sheet.onOpenPicker(StepPickerPage.Vars)
            }

            // 入口格 → 弹它那一层菜单（抽屉原地留着，与锚点浮层那套一致）
            //  判据是「这一档有没有一层菜单」而不是「是不是入口格」：入口格里「选择应用」那一格
            //  早在上一支就被接走了，剩下的正好是「点开还有一层」的那些。
            //  「应用控制」也落在这一支 —— 它的打开 / 结束两档就在那层菜单里（见下面 `onPick`）。
            ActionCatalog.modeMenu(spec.token) != null -> {
                XLog.w("抽屉：点 ${spec.label} → 开二级菜单")
                subAnchor = anchor
                subTile = spec
            }

            //  普通一行：**选中即确认**（用户口径「去掉确认按钮」）—— 这一下就落盘并收抽屉。
            //  抽屉里不再有「点了但没生效」的中间态，所以也不需要那颗「确定」。
            else -> {
                XLog.w("抽屉：选中 ${spec.label} → 应用 ${spec.token}")
                sheet.onPick(spec.token)
                sheet.onDismiss()
            }
        }
    }

    //  「应用控制」那一行的子标题要应用名：与网格那一格同一份（`boundAppName`），
    //  同样带「具体是哪一个」（`微信 - 扫一扫`）—— 两边是同一句话。
    val boundApp = sheet.current?.let { boundAppName(it, withDetail = false) }
    val ctx =
        StepSheetCtx(
            anchorOf = { anchors.getValue(it) },
            isCurrent = { isCurrent(it) },
            onLand = { spec, anchor -> land(spec, anchor) },
            current = sheet.current,
            boundApp = boundApp,
            groups = groups,
        )
    //  「已保存的命令」那一组的锚点是另外一份（见 `savedAnchors`），其余上下文相同
    val savedCtx =
        StepSheetCtx(
            anchorOf = { savedAnchors.getValue(it) },
            isCurrent = { isCurrent(it) },
            onLand = { spec, anchor -> land(spec, anchor) },
            current = sheet.current,
            boundApp = boundApp,
            groups = groups,
        )

    //  **没有搜索框**（用户口径）：抽屉里就那几组候选 + 自己写的命令，按组扫一遍比先想关键词快；
    //  顶上再挂一个过滤框反而把「一眼看全」这件事挡住了。
    PageSheet(
        show = show,
        title = sheet.title,
        onDismiss = sheet.onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            StepSheetGroups(ctx)
            //  **自定义命令排在最后**：这是用户自己写的东西，挑的时候上面几组通常已经扫过一遍。
            //  一条都没存过时整组不出现 —— 这是「挑一条来用」的抽屉，没得挑就别摆一栏空的。
            if (savedSpecs.isNotEmpty()) {
                SavedCommandsGroup(savedSpecs, savedCtx)
            }
        }
    }

    // 入口格那一层菜单（档位）——「系统开关」再往下的那一问是**弹窗**，也在同一个组件里。
    //  锚点用**被点那一行**自己那份 —— 菜单才跟着手指落。
    //  **选中即确认**：点了哪一档就落哪一档，然后连抽屉一起收掉（不再等「确定」）。
    ModeMenuPopups(
        preciseClick = subAnchor ?: coldAnchor,
        // 命令链与网格拿到的是同一份菜单（灵感语音的开始 / 结束都是普通一步）
        target = subTile?.let { ModeMenuTarget(it) },
        current = sheet.current,
        onPick = { spec ->
            tick()
            XLog.w("抽屉二级菜单：选中 ${spec.label} → 应用 ${spec.token}")
            subTile = null
            subAnchor = null
            //  「应用控制」那一层菜单的两行**都不是取值**：打开 / 结束都还要再挑一个
            //  东西，与网格那一格走同一支（收抽屉 → 压一页 → 挑完落一步）。
            when (spec.token) {
                ActionCatalog.APP_CONTROL_OPEN -> {
                    sheet.onDismiss()
                    sheet.onOpenPicker(StepPickerPage.App)
                }

                ActionCatalog.APP_CONTROL_STOP -> {
                    sheet.onDismiss()
                    sheet.onOpenPicker(StepPickerPage.AppStop)
                }

                else -> {
                    sheet.onPick(spec.token)
                    sheet.onDismiss()
                }
            }
        },
        onDismiss = {
            subTile = null
            subAnchor = null
        },
    )
}

/**
 * 抽屉里那三支（内置组 / 已保存组 / 一张卡）共用的「长什么样、点了怎么算」——
 * 合成一个对象，是因为 detekt 的参数上限只有 5 个，而这几样是同一批上下文，不该拆散。
 */
private class StepSheetCtx(
    val anchorOf: (ActionCatalog.Spec) -> PreciseClickState,
    val isCurrent: (ActionCatalog.Spec) -> Boolean,
    val onLand: (ActionCatalog.Spec, PreciseClickState) -> Unit,
    /** 当前那一个动作 —— 入口格那几行的行首字形按它算（见 `StepSheetCard`）。 */
    val current: Action?,
    /**
     * 当前绑的应用名 —— 「选择应用 / 应用控制」那几行的子标题要它（与网格那一格同一份）。
     * 只有那一两行用得到，但一次查好递进来比重组里按行判更省事（`boundAppName` 自己带缓存）。
     */
    val boundApp: String?,
    /** 这一次列哪张候选表（场景那张多一个「无操作」，见 [StepSheetFilter.includeNone]）。 */
    val groups: List<Pair<String, List<ActionCatalog.Spec>>>,
)

/**
 * 内置动作那几组 —— 分组与网格同一张表（[StepSheetCtx.groups]），组名与组内顺序逐项一致。
 *
 *  组与组之间要断开：这一档由小标题自己给（上 16 / 下 0，见 `SectionTitle`）——
 *  与各页同一套，抽屉里也不再单独补 `Spacer`，第一组同样带（不特例）。
 */
@Composable
private fun SettingsPage.StepSheetGroups(ctx: StepSheetCtx) {
    Column {
        ctx.groups.forEach { (groupTitle, group) ->
            //  小标题收一档（左右各一档 `PAGE_MARGIN`）：抽屉自己已经给过一档，再叠库默认那
            //  32dp 就比卡片行文字右移一档（见 `SHEET_TITLE_MARGIN`）
            SectionTitle(ActionCatalog.groupTitleLabel(groupTitle), insideMargin = SHEET_TITLE_MARGIN)
            StepSheetCard(group, ctx)
        }
    }
}

/** 「已保存的命令」那一组 —— 只有一组、标题固定，与内置那几组共用同一张卡。 */
@Composable
private fun SettingsPage.SavedCommandsGroup(
    specs: List<ActionCatalog.Spec>,
    ctx: StepSheetCtx,
) {
    //  与上面那几组同一份内边距
    SectionTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "自定义命令"), insideMargin = SHEET_TITLE_MARGIN)
    StepSheetCard(specs, ctx)
}

/**
 * 抽屉里的一张候选卡 —— 行用项目通用的 [CardList] + [CardRow.Item]：
 * 行高 / 内边距 / 分隔线 / 按下反馈 / 行首与行尾的配色都由那一层给，这里不另立行组件。
 *
 *  `margin = 0.dp`：抽屉自己已有 `PAGE_MARGIN` 的左右内边距（`insideMargin`）。
 */
@Composable
private fun SettingsPage.StepSheetCard(
    specs: List<ActionCatalog.Spec>,
    ctx: StepSheetCtx,
) {
    CardList(
        margin = 0.dp,
        //  暗色下卡片比抽屉面再亮一档（见 `sheetCardColors`）；浅色传 null = 库默认，与页面同一档
        colors = sheetCardColors(),
        rows = specs.map { stepSheetRow(it, ctx) },
    )
}

/**
 * 候选卡里的一行 —— 直接产出通用的 [CardRow.Item]。
 *
 *  **「这一行长什么样」不在这里算**：字形、小字、行尾箭头与选中态都由 [actionRowView] 给，
 *  与网格那一格**同一个函数**。清单同源不等于展示同源 —— 两边各拼一半时走形过三次
 *  （行首字形 / 行尾箭头 / 小字），所以这一层只负责把那几个值塞进 `CardRow.Item` 的槽：
 *  选中态只换三样（行首换成对勾、标题色、行尾色），整行上主题色这件事在行组件里表达得完。
 *
 *  振动仍由 `land` 统一发（[CardRow.Item.vibrate] 传 `false`）：那三档去向（挑应用 / 开二级 /
 *  直接落一步）都走它，行组件再振一次就是两下。
 */
@Composable
private fun stepSheetRow(
    spec: ActionCatalog.Spec,
    ctx: StepSheetCtx,
): CardRow.Item {
    val anchor = ctx.anchorOf(spec)
    val view = actionRowView(spec, ctx.current, ctx.boundApp)
    val colors = COUITheme.colorScheme
    //  抽屉递下来的那一份（见 `PageSheet` 的进场定位）。**已选那一行**才挂它。
    val sheetBringIntoView = LocalSheetBringIntoView.current
    return CardRow.Item(
        title = view.title,
        // 标题下面那行小字 = **当前绑的是什么**，只在这一行正是当前那一档时才有内容
        summary = view.subtitle,
        //  **当前这一步**那一行：行首**保留这一档自己的字形**（换成对勾的话，「当前是哪个」
        //  与「这一行是什么」就只剩颜色能分，扫一列下来全是对勾），选中只由主题色说。
        icon = view.icon,
        //  未选中时给 `null`，让行组件按启用态走默认那档灰
        iconTint = if (view.selected) colors.primary else null,
        titleTint = if (view.selected) colors.primary else null,
        modifier =
            Modifier
                .preciseClickAnchor(anchor)
                //  选中那一行加一圈**内边框** —— 与「选择应用 / 选择界面」两页同一份
                //  （见 `selectedRowRing`）。抽屉里原来只有「行首 + 标题上主题色」这一档，
                //  而这一列的每一行都带字形，扫下来很难定位当前是哪一档。
                .selectedRowRing(view.selected)
                //  抽屉打开时**自动滚到这一行**（用户口径「抽屉自动定位到已选」）：requester
                //  由 `PageSheet` 递下来，只有「当前这一档」那一行挂它 —— 挂两处的话壳不知道
                //  该滚哪个。没挂上的抽屉什么都不发生（`LocalSheetBringIntoView` 为 null）。
                .then(
                    if (view.selected) {
                        sheetBringIntoView?.let { Modifier.bringIntoViewRequester(it) } ?: Modifier
                    } else {
                        Modifier
                    },
                ),
        //  行尾那一颗**照画**，不分选中与否：它说的是「点开还有一层」，而选中之后照样点得进去换一档
        //  —— 藏起来那一行看着像死格子（用户反馈：抽屉里选中有二级菜单的项之后，行尾的选择器不见了）。
        //  颜色仍用最弱那一档：行首的对勾才表示「就是这一档」，行尾只是路标（见 `RowEndIcon`）。
        trailing = view.trailing?.let { end -> { RowEndIcon(icon = end) } },
        //  振一下由 `land` 统一发，这一层不再振（见本函数的说明）
        vibrate = false,
        onClick = { ctx.onLand(spec, anchor) },
    )
}
