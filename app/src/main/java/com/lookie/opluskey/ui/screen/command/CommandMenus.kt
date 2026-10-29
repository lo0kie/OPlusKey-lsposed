package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.ModeMenuPopups
import com.lookie.opluskey.ui.ModeMenuTarget
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.PageSheet
import com.lookie.opluskey.ui.SHEET_TITLE_MARGIN
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.COLOR_FADE_SPEC
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER_MS
import com.lookie.opluskey.ui.model.tileIcon
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.sheetCardColors
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 各处下拉/浮层选择菜单（添加命令、响铃状态、命令卡菜单）。
 *
 *  [CommandMenuPopup] 是**命令卡的唯一菜单**：从卡片右上角那颗「更多」进，
 *  三行固定是「测试 / 编辑 / 删除」—— 菜单本身不关心从哪儿来（落点由调用点给的锚点决定）。
 *
 * 注：`onEdited` / `onDelete` 的 `= {}` 默认值**看起来是死的**（唯一调用点两个都显式传了），
 *  但它撑住的是参数个数这条线 —— 去掉默认值会让 detekt 的 `LongParameterList` 判据换成另一个
 *  签名匹配（基线里那条按**带默认值的签名**记着），而本仓不许往基线里添条目。留着。
 */
@Composable
internal fun SettingsPage.CommandMenuPopup(
    cmd: Command,
    show: Boolean,
    preciseClick: PreciseClickState,
    /** 进编辑态之后调 —— 调用方借此把页面滚回顶部（表单在页面最上面）。 */
    onEdited: () -> Unit = {},
    /** 「删除」：交给调用点弹确认窗（真删在确认之后）。 */
    onDelete: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    // 页面离开时由 MenuPopup 自己登记收走
    PageMenu(
        entries =
            listOf(
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
                    } else {
                        // 走 [SettingsPage.loadDraft] 而**不是** `draft.load`：表单里可能还有没保存的改动，
                        //  直接灌进去会把它整份覆盖掉。
                        loadDraft(cmd)
                    }
                    onEdited()
                },
                // 两项之间那条细线由菜单自己加
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_DELETE, "删除"),
                    icon = PageIcons.CommandDelete,
                    danger = true,
                ) {
                    onDismiss()
                    onDelete()
                },
            ).takeIf { show }
                .orEmpty(),
        preciseClick = preciseClick,
        onDismiss = onDismiss,
    )
}

// 行尾菜单那几张浮层同样用 `ListPopupDefaults.DropdownPositionProvider` + 触点锚点。

// --------------------------------------------------------------- 添加命令（底部抽屉）

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
)

/**
 * 抽屉里那两行**关卡** —— 点它们不是「落一步」，而是「换一页去挑」。
 *
 *  两行的行为形态完全一样（收抽屉 → 压一页），只是目的地不同；合成一个枚举是因为
 *  [StepSheetSpec.onOpenPicker] 只收一个回调 —— 两个参数会让那个类多一格，
 *  而「去哪一页」本来就是调用点一个 `when` 里的事。
 */
internal enum class StepPickerPage {
    /** 「选择应用」→ 挑应用页（落 `app:` / `window:`）。 */
    App,

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
 *  行用 `CardList` + [StepSheetRow]，与页面里其它卡同一套行高与分隔线。行尾字形按「点开去哪」分：
 *  选择应用 / 自定义变量给右箭头（进另一页）、入口格给选择器字形（还有一层菜单）、直接落成一步的不给。
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
    //  抽屉顶上那个过滤框 —— 内置动作 + 已保存命令一共四十多行，翻三屏才找到一条；
    //  边输边筛，清空即回到分组列表。关抽屉时一并清掉：下一次打开还挂着上一个词的话，
    //  用户会以为「列表少了东西」。
    var query by remember { mutableStateOf("") }

    //  这一行画不画成「选中」（行首对勾 + 整行主题色）：看当前动作。
    //  点一下就落盘、抽屉随即收掉（见 `land`），所以这里没有「刚点过、还没生效」那一档了。
    fun isCurrent(spec: ActionCatalog.Spec): Boolean = sheet.current?.let { ActionCatalog.isSelected(spec, it) } ?: false
    // 每一行**自己的**锚点。**不能共用一份**：`preciseClickAnchor` 是靠 `onGloballyPositioned`
    //  把「这一行」的坐标写进 state 的，而 `anchorCoordinates` 只有一格 —— 共用时后布局的行会把
    //  前面的覆盖掉，按下时 `localToWindow(down.position)` 拿的是**别的行**的坐标 ⇒ 菜单弹在
    //  不相干的位置，菜单也就不跟手指。
    //  一次按候选表建好，行的 `modifier` 直接取自己那份。
    val anchors =
        remember {
            ActionCatalog.STEP_GROUPS
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
            //  **正在编辑的那条自己除外**（见 [StepSheetSpec.excludeTokens]）：引用自己就是环，
            //  执行侧会报「套太深」，不如别让它出现在候选里。
            .filter { it.token !in sheet.excludeTokens }
            .map { ActionCatalog.commandSpec(it) }
    val savedAnchors = remember(savedSpecs) { savedSpecs.associateWith { PreciseClickState() } }
    // 开着二级菜单那一行的锚点（开的时候记下来，`ModeMenuPopups` 用）；
    //  没记录到就给 [coldAnchor] 兜一下（真走到那里说明触点丢了）
    var subAnchor by remember { mutableStateOf<PreciseClickState?>(null) }
    val coldAnchor = remember { PreciseClickState() }

    //  关抽屉时**三样一起复位**：搜索词、开着二级菜单的那一格、它的锚点。
    //  只清 `query` 的话，系统返回 / 下拉把抽屉收掉之后 `subTile` 还挂着 —— 而 `show` 是行参、
    //  这个组合并不随之销毁，于是那一层菜单仍开在半空：随手选一档就把一步**静默加进草稿末尾**。
    LaunchedEffect(show) {
        if (!show) {
            query = ""
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

    val ctx =
        StepSheetCtx(
            anchorOf = { anchors.getValue(it) },
            isCurrent = { isCurrent(it) },
            onLand = { spec, anchor -> land(spec, anchor) },
            current = sheet.current,
        )
    //  「已保存的命令」那一组的锚点是另外一份（见 `savedAnchors`），其余上下文相同
    val savedCtx =
        StepSheetCtx(
            anchorOf = { savedAnchors.getValue(it) },
            isCurrent = { isCurrent(it) },
            onLand = { spec, anchor -> land(spec, anchor) },
            current = sheet.current,
        )
    //  搜索态：分组标题全部让位，命中的行拍成**一张卡**（找东西时按组切反而要多扫几行标题）。
    //  两组候选的锚点不在同一张表里，这里按「先查命令、再查动作」挑一份；
    //  两个都没有（理论上不会）就落到 `coldAnchor`，至少不抛。
    val searchCtx =
        StepSheetCtx(
            anchorOf = { savedAnchors[it] ?: anchors[it] ?: coldAnchor },
            isCurrent = { isCurrent(it) },
            onLand = { spec, anchor -> land(spec, anchor) },
            current = sheet.current,
        )
    val term = query.trim()
    val hits =
        if (term.isEmpty()) {
            emptyList()
        } else {
            //  内置那几组按 STEP_GROUPS 的顺序，后面接已保存命令 —— 与不搜时的相对顺序一致
            (ActionCatalog.STEP_GROUPS.flatMap { it.second } + savedSpecs)
                .filter { it.label.contains(term, ignoreCase = true) }
        }

    PageSheet(
        show = show,
        title = sheet.title,
        onDismiss = sheet.onDismiss,
        //  过滤框用库自己的 `InputField`（「选择应用」页那个搜索框就是它）——
        //  放大镜、清除键、占位文案都是它自带的，不另拼一层。
        //  走 `header`：它在滚动区**外面**，列表翻几屏时不该跟着滚走（否则想换关键词得先滚回顶部）。
        header = {
            InputField(
                query = query,
                onQueryChange = { query = it },
                //  键盘上点「搜索」不做额外动作：过滤是边输边做的
                onSearch = { },
                expanded = false,
                onExpandedChange = { },
                modifier = Modifier.fillMaxWidth().padding(bottom = STEP_SHEET_SEARCH_GAP),
                label = I18n.get(Strings.COMMAND_SEARCH, "搜索命令"),
            )
        },
    ) {
        //  **三种内容之间交叉淡入淡出**（列表 ↔ 搜索结果 ↔ 没找到）：高度变化交给壳里那个
        //  `animateContentSize`，这里只做淡入淡出 —— 两层各管一件事，
        //  两个都动高度会变成一段动画套一段。
        Crossfade(
            targetState =
                when {
                    term.isEmpty() -> SheetBody.Groups
                    hits.isEmpty() -> SheetBody.Empty
                    else -> SheetBody.Hits
                },
            animationSpec = tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL),
            label = "step-sheet-body",
        ) { body ->
            Column(Modifier.fillMaxWidth()) {
                when (body) {
                    SheetBody.Groups -> {
                        StepSheetGroups(ctx)
                        //  **已保存的命令排在最后**：这是用户自己写的东西，挑的时候上面几组
                        //  通常已经扫过一遍。一条都没存过时整组不出现 —— 这是「挑一条来用」
                        //  的抽屉，没得挑就别摆一栏空的。
                        if (savedSpecs.isNotEmpty()) {
                            SavedCommandsGroup(savedSpecs, savedCtx)
                        }
                    }

                    //  搜不到时给一句话，不摆一张空卡：这里没有「这里还能放东西」的暗示
                    SheetBody.Empty -> {
                        StepSheetEmptyHint(term)
                    }

                    SheetBody.Hits -> {
                        StepSheetCard(hits, searchCtx)
                    }
                }
            }
        }
    }

    // 入口格那一层菜单（档位）——「系统开关」再往下的那一问是**弹窗**，也在同一个组件里。
    //  锚点用**被点那一行**自己那份 —— 菜单才跟着手指落。
    //  **选中即确认**：点了哪一档就落哪一档，然后连抽屉一起收掉（不再等「确定」）。
    ModeMenuPopups(
        preciseClick = subAnchor ?: coldAnchor,
        // 命令链这一层**没有长按**这一档：灵感语音不在步骤链里出现
        target = subTile?.let { ModeMenuTarget(it, withLongOnly = false) },
        current = sheet.current,
        onPick = { spec ->
            tick()
            XLog.w("抽屉二级菜单：选中 ${spec.label} → 应用 ${spec.token}")
            subTile = null
            subAnchor = null
            sheet.onPick(spec.token)
            sheet.onDismiss()
        },
        onDismiss = {
            subTile = null
            subAnchor = null
        },
    )
}

/** 抽屉正文的三种形态 —— 只为 [Crossfade] 有一个稳定的目标值（见 `StepPickerSheet`）。 */
private enum class SheetBody { Groups, Hits, Empty }

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
)

/**
 * 内置动作那几组 —— 分组与网格同一张表（[ActionCatalog.STEP_GROUPS]），组名与组内顺序逐项一致。
 *
 *  组与组之间要断开：这一档由小标题自己给（上 16 / 下 0，见 `SectionTitle`）——
 *  与各页同一套，抽屉里也不再单独补 `Spacer`，第一组同样带（不特例）。
 */
@Composable
private fun SettingsPage.StepSheetGroups(ctx: StepSheetCtx) {
    Column {
        ActionCatalog.STEP_GROUPS.forEach { (groupTitle, group) ->
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
    SectionTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"), insideMargin = SHEET_TITLE_MARGIN)
    StepSheetCard(specs, ctx)
}

/**
 * 抽屉里的一张候选卡 —— 行用项目标准那套（`CardList` + [StepSheetRow]）：
 * 行高 / 内边距 / 分隔线 / 按下反馈都由它给，不再自己手搓 `Row`。
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
        rows =
            specs.map { spec ->
                val anchor = ctx.anchorOf(spec)
                //  **行首字形与网格那一格同一判据**：入口格（响铃状态 / 媒体控制 / 系统开关…）
                //  按**当前绑的是哪一档**给，其余按清单条目给 —— 抽屉与网格摆的是同一张清单，
                //  同一个「响铃状态」在两处长成两个样子的唯一原因就是这里少判了一下。
                val glyph = tileIcon(ActionCatalog.boundModeOf(spec, ctx.current) ?: spec)
                // 行尾那颗字形按「点开去哪」分（与页面里那几行同一套口径）：
                //  · 「选择应用」/「自定义变量」进的是**另一个页面**去挑 → 右箭头（[PageIcons.EnterArrow]）；
                //  · 入口格那几档（系统开关 / 媒体控制…）点开还有**一层菜单** → 选择器字形；
                //  · 其余动作点一下就落成一步、`cmd:<名字>` 同样落成一步 → 不给。
                //  **选中那一行也照画**：选中只把行首换成对勾，行尾该不该有指示不变。
                val trailingIcon =
                    when {
                        spec.token == ActionCatalog.APP_TILE || spec.token == ActionCatalog.VARS_TILE -> {
                            PageIcons.EnterArrow
                        }

                        ActionCatalog.modeMenu(spec.token) != null -> {
                            PageIcons.Selector
                        }

                        else -> {
                            null
                        }
                    }
                //  **当前这一步**那一行整行上主题色、**行首**那颗图标换成对勾
                //  （与浮层菜单里 `PageMenuPopup` 的选中态同一套）。
                //
                //  两种状态走**同一支** [StepSheetRow]，这里不再二选一 ——
                //  以前选中行走 `CardRow.Custom`、未选中走 `CardRow.Item`，一换就是整棵
                //  子树被替换，`animateColorAsState` 的状态跟着重建，颜色只能硬切。
                CardRow.Custom { position ->
                    StepSheetRow(
                        item = StepSheetItem(spec.label, glyph, trailingIcon),
                        selected = ctx.isCurrent(spec),
                        anchor = anchor,
                        position = position,
                    ) { ctx.onLand(spec, anchor) }
                }
            },
    )
}

/** 抽屉里一行的那三样「长什么样」—— 合成一个对象，行组件的参数才压得回 6 个以内。 */
private class StepSheetItem(
    val label: String,
    val glyph: ImageVector,
    val trailingIcon: ImageVector?,
)

/**
 * 抽屉里行首图标的尺寸 —— **与 `CardRow.Item` 那颗一致**。
 *
 *  普通行走 `CardRow.Item` → `ItemRow` → `Icon(vec)` 不给尺寸，按 Tabler 图标的固有尺寸
 *  24dp 画。选中那一行是自绘的，这颗对勾要是用别的数（原先误用了 `ROW_ICON_SIZE` = 22dp），
 *  选中 / 取消一切换整行宽度就跳 2dp，看着像「来回缩」。
 */
private val STEP_SHEET_ICON_SIZE = 24.dp

/** 搜索框与下面那串候选之间的距离 —— 比小标题自带的那档（12dp）再紧一点：它俩是一块儿。 */
private val STEP_SHEET_SEARCH_GAP = 8.dp

/**
 * 搜不到东西时那一行字。
 *
 *  **不摆空卡**：卡片在别处都表示「这里有东西、只是现在空着」（已保存命令那条空态就是这样），
 *  而搜索没命中完全是另一回事 —— 一行字说明就够了，也别给出「还能往里放东西」的错觉。
 *
 *  用普通 `Text` 而不是 `EmphasisText`：用户输的词要原样显示（搜 `*` 就得看见 `*`），
 *  正文记号那一套在这里没有用武之地。
 */
@Composable
private fun SettingsPage.StepSheetEmptyHint(term: String) {
    //  走全项目唯一那份空态排版（[Feedback]：32dp 字形框 + 居中文案），字形用**那张脸**
    //  （`PageIcons.NoMatch`）—— 与「选择应用」搜不到时同一颗，同一个现象在两处长一个样。
    //  不摆空卡：卡片在别处都表示「这里有东西、只是现在空着」，搜索没命中是另一回事。
    Feedback(
        glyph = { tint ->
            Icon(
                imageVector = PageIcons.NoMatch,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = tint,
            )
        },
        text = I18n.format(Strings.COMMAND_SEARCH_EMPTY, term),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = STEP_SHEET_EMPTY_PADDING),
    )
}

/** 搜不到时那行字的上下留白 —— 与各页空态同一档（24dp）。 */
private val STEP_SHEET_EMPTY_PADDING = 24.dp

/**
 * 抽屉里的一行 —— **选中 / 未选中是同一棵子树**，会变的颜色全部过 `animateColorAsState`。
 *
 *  为什么要合成一支：以前选中行走 `CardRow.Custom` + `BasicComponent`（写死主题色）、未选中走
 *  `CardRow.Item`，两者是两个不同的 composable —— 切换选中时整棵子树被替换，动画状态跟着重建，
 *  颜色只能硬切。收成一支之后动画状态在那个位置连续存在：
 *
 *   · **标题**、**行首图标**、**行尾字形**三处 tint 各一条动画（[COLOR_TRANSITION_SPEC]）；
 *   · 行首那一格换的是**字形**（原图标 ↔ 对勾），形状没法插值 —— 用 [Crossfade] 淡过去。
 *     两个字形都是 [STEP_SHEET_ICON_SIZE]（24dp），交叉期间盒子宽度不变，文字不会被推一下。
 *
 *  未选中时那三档取值与 `CardRow.Item` 完全一致（`onBackground` / `onSurfaceContainer` /
 *  `onSurfaceVariantActions`），所以合并之后配色没有任何变化，只是多了过渡。
 *
 *  @param position 这一行在卡里的位置（首行 / 中间 / 末行，卡片圆角与内缩看它）
 */
@Composable
private fun SettingsPage.StepSheetRow(
    item: StepSheetItem,
    selected: Boolean,
    anchor: PreciseClickState,
    position: CardListPosition,
    onClick: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    val titleTint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onBackground,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "StepSheetTitle",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceContainer,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "StepSheetIcon",
    )
    //  行尾那颗选中时也上主题色：整行只有它还是灰的话，那一行看着只亮了一半
    //  未选中态与 `CardRow.Item` 的箭头同值。
    val endTint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariantActions,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "StepSheetEnd",
    )
    BasicComponent(
        title = item.label,
        titleColor = BasicComponentDefaults.titleColor(color = titleTint),
        startAction = {
            Crossfade(
                targetState = selected,
                animationSpec = COLOR_FADE_SPEC,
                label = "StepSheetLead",
            ) { on ->
                if (on) {
                    Icon(
                        PageIcons.MenuCheck,
                        contentDescription = null,
                        modifier = Modifier.size(STEP_SHEET_ICON_SIZE),
                        tint = iconTint,
                    )
                } else {
                    Icon(
                        item.glyph,
                        contentDescription = null,
                        modifier = Modifier.size(STEP_SHEET_ICON_SIZE),
                        tint = iconTint,
                    )
                }
            }
        },
        endActions = {
            item.trailingIcon?.let { endGlyph ->
                Icon(
                    endGlyph,
                    contentDescription = null,
                    modifier = Modifier.size(ROW_END_ICON_SIZE),
                    tint = endTint,
                )
            }
        },
        cardListPosition = position,
        modifier = Modifier.preciseClickAnchor(anchor),
        onClick = onClick,
    )
}
