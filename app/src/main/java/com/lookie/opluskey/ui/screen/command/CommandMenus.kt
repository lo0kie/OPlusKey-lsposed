package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.IconAction
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.ModeMenuPopups
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.isDarkScheme
import com.lookie.opluskey.ui.model.specIcon
import com.lookie.opluskey.ui.rememberTick
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/**
 * 各处下拉/浮层选择菜单（添加命令、响铃状态、长按菜单）。
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
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_EDIT, "编辑"),
                    icon = PageIcons.CommandEdit,
                ) {
                    onDismiss()
                    // 走 [SettingsPage.loadDraft] 而**不是** `draft.load`：表单里可能还有没保存的改动，
                    //  直接灌进去会把它整份覆盖掉。
                    loadDraft(cmd)
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

/**
 * 「这一行本身」的三件事 + 两头能不能挪 —— 合成一个对象，`StepRowMenuPopup` 的参数才压得回 5 个以内。
 *
 *  @param canMoveUp 这一行能不能往上挪（第一步不能）
 *  @param canMoveDown 这一行能不能往下挪（最后一步不能）
 */
internal class StepRowMenuSpec(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * 步骤行行尾那颗**竖三点**弹出的菜单 —— 上移 / 下移 / 删除。
 *
 * ## 为什么收成菜单
 *
 *  三颗按钮并排时占掉近半行宽，标题那一列被挤到只剩十来个字、长一点的命令名一律省略号；
 *  而这三件事都不是每秒要点的动作。收进菜单之后，行尾只剩「选择器指示 + 竖三点」两格，
 *  宽度还给标题。
 *
 * ## 禁用行照旧说得清
 *
 *  第一 / 最后一步不能上移 / 下移这件事，原先靠置灰的按钮 + `LocalDisabledClick` 解释；
 *  菜单这边是 `MenuEntry.Row.enabled` + [MenuEntry.Row.disabledHint]：点一下只说明原因、
 *  菜单不关，比「一颗点不动的灰按钮」更直白（渲染层的处理见 `PageMenuPopup`）。
 *
 * @param spec 这一行能做的三件事，以及两头能不能挪（见 [StepRowMenuSpec]）
 */
@Composable
internal fun SettingsPage.StepRowMenuPopup(
    show: Boolean,
    preciseClick: PreciseClickState,
    spec: StepRowMenuSpec,
    onDismiss: () -> Unit,
) {
    PageMenu(
        entries =
            listOf(
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_MOVE_UP, "上移"),
                    icon = PageIcons.StepUp,
                    enabled = spec.canMoveUp,
                    disabledHint = I18n.get(Strings.TOAST_COMMAND_STEP_FIRST, "已经是第一步"),
                ) {
                    onDismiss()
                    spec.onMoveUp()
                },
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_MOVE_DOWN, "下移"),
                    icon = PageIcons.StepDown,
                    enabled = spec.canMoveDown,
                    disabledHint = I18n.get(Strings.TOAST_COMMAND_STEP_LAST, "已经是最后一步"),
                ) {
                    onDismiss()
                    spec.onMoveDown()
                },
                // 危险项排在最后，与上面两步「挪位置」不是一类 —— 一条加深带隔开
                //  （与条件执行那张行尾菜单同一套排法，见 `CommandBranchMenus.BranchRowMenu`）
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_DELETE, "删除"),
                    icon = PageIcons.CommandDelete,
                    danger = true,
                    dividerBefore = true,
                ) {
                    onDismiss()
                    spec.onDelete()
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
 *  @param onOpenApp 「选择应用」那一行点下去做什么（两处各有各的去处：写某一步 / 写某一条规则）
 *  @param onPick 挑中一个动作的 token（由调用方决定是追加还是替换）
 */
internal class StepSheetSpec(
    val title: String,
    val current: Action?,
    val onOpenApp: () -> Unit,
    val onPick: (String) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * 「添加命令 / 选择命令」点开的**底部抽屉** —— 挑一个动作落成一步，或换掉某一步。
 *
 *  三个来处共用这一支：「多条命令」那颗「添加命令」（追加）、点某一步（换掉它）、条件执行
 *  某一条的「动作」。三者的差别只在 [title] / [current] / [onOpenApp] / [onPick] 由调用点给，
 *  抽屉自己不读草稿 —— 「选了之后写回哪儿」是各页的语义，不归容器。
 *
 *  行用 `CardList` + [StepSheetRow]，与页面里其它卡同一套行高与分隔线。行尾字形按「点开去哪」分：
 *  选择应用给右箭头（进另一页）、入口格给选择器字形（还有一层菜单）、直接落成一步的不给。
 *
 *  内置动作那几组之后是**「已保存的命令」**：多命令的步骤、条件执行的动作，挑的多半是自己写的那几条，
 *  摆在末尾不用先翻过整张内置表。这一组来自命令库（`configState.commands`），点一行落成 `cmd:<名字>`；
 *  只列**单条命令** —— 串行命令本身是一串步骤，塞成一步没有语义。
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
    //  **登记进页面级浮窗表**：系统返回那条路（`BackInterception`）先问「有没有浮层开着」——
    //  有就**吞掉这次返回、只收浮层**，没有才退页面。抽屉不登记的话，返回会直接退页
    //  （真机：命令页里抽屉像是「吃掉了返回」，条件执行里却是退页 —— 差别就在这层登记与
    //  窗口焦点谁先拿到，不可靠）。登记之后两条路一致：`hasOpen()` 为真 ⇒ 返回只收抽屉；
    //  换页时 `closeAll` 也会把它一起收掉（与浮层菜单 / 确认窗同一条纪律）。
    overlays.Closer(show = show, onDismiss = sheet.onDismiss)
    //  再登记一次「这一退归我、而且我要**跟手**」：抽屉内部那个 `NavigationBackHandler` 是靠
    //  逐帧进度驱动下滑的，事件必须喂进 dispatcher 才动（见 `PageOverlays.backCapturing`
    //  与 `wouldConsumeBack` 里那两行）。少了这一格，返回只剩「一次性收掉」——
    //  真机口径「之前抽屉会有类似预测返回手势的效果，现在没了」。
    overlays.BackCapturing(show = show)

    //  抽屉里每一次点击都走通用振动通道（与页面里其它控件同一条，判据只有系统状态）。
    val tick = rememberTick()
    val icons = tileIcons.value
    //  **暗色下把抽屉面抬一档**：页面底色是 `surface`，COUI 暗色里 `surface` 与抽屉能看到的那点
    //  缝隙几乎是同一个黑，面板浮不起来。
    //  所以只在暗色把面改成 `surfaceContainer`；浅色仍用页面那个 `surface`（上一条口径
    //  「配色和页面一样」在浅色下原样成立）。
    //
    //  **必须 `compositeOver` 合一次**：COUI 暗色的层级色是**半透明白**（`surfaceContainer` =
    //  `0x1AFFFFFF`，`surfaceContainerHigh` = `0x33FFFFFF`），设计上是叠在页面那层实黑上的。
    //  抽屉面板画在独立窗口上，直接把半透明色交给它就是**真透明** —— 底下的页面透上来
    //  所以先按「页面黑底」合出实色，观感与 COUI 原本一致，
    //  只是不再透。
    //  卡片的底也取页面那层（不是取抽屉面）：那样算出来的色与页面里的卡片**同值**，
    //  于是层级是「页面 < 抽屉面 < 卡片」，卡片还是用户熟悉的那一档。
    val dark = COUITheme.colorScheme.isDarkScheme
    val pageSurface = COUITheme.colorScheme.surface
    val sheetColor =
        if (dark) COUITheme.colorScheme.surfaceContainer.compositeOver(pageSurface) else pageSurface
    val sheetCards =
        if (dark) {
            val cardColor = COUITheme.colorScheme.surfaceContainerHigh.compositeOver(pageSurface)
            CardDefaults.defaultColors(
                color = cardColor,
                pressedColor = COUITheme.colorScheme.surfaceContainerHigh.compositeOver(cardColor),
            )
        } else {
            null
        }
    // 开着二级菜单的那一格（入口格那些：系统开关 / 系统导航 / 媒体控制…）。`null` = 没开。
    var subTile by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    //  **刚才点开的是哪个入口格** —— 与 [subTile] 分开存一份。
    //  原因：[ModeMenuPopups] 的契约是「**先** `spec.onDismiss()`、**再** `onPick()`」（看它 `modeEntries`
    //  的两个分支），而这里的 `onDismiss` 正是把 `subTile` 置空那一句。于是 onPick 里读 `subTile`
    //  拿到的是 `null` ⇒ `pickedRow` 写空 ⇒ 选中态不出现（真机口径「抽屉里选择带二级菜单的
    //  没有主题色表示」）。`pickedToken` / `pickedAction` 是独立赋的值，所以「确定」照旧能应用，
    //  只是界面没亮 —— 症状就是这么来的。
    var openedTile by remember { mutableStateOf<ActionCatalog.Spec?>(null) }

    //  用户**这一刻点了哪一行**（只换高亮，还没生效）。`null` = 还没点过 ⇒ 按 [current] 显示原有选中。
    //  点行只换高亮、按右上角「确定」才应用：点一下就落的话，选中态根本来不及看（点完就写走了），
    //  也没法先把几条候选挨个看一遍再决定。
    var pickedRow by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    var pickedToken by remember { mutableStateOf<String?>(null) }
    //  二级菜单里选中的那一档（再打开同一个入口格时用它打勾 —— 此时 [current] 还是旧值）
    var pickedAction by remember { mutableStateOf<Action?>(null) }
    LaunchedEffect(show) {
        if (!show) {
            pickedRow = null
            pickedToken = null
            pickedAction = null
            openedTile = null
        }
    }

    //  这一行画不画成「选中」（行首对勾 + 整行主题色）：点过就以点的那行为准，否则看当前动作。
    fun isCurrent(spec: ActionCatalog.Spec): Boolean {
        pickedRow?.let { return it == spec }
        return sheet.current?.let { ActionCatalog.isSelected(spec, it) } ?: false
    }
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
            .filter { it.kind == CommandKind.SINGLE }
            .map { ActionCatalog.commandSpec(it) }
    val savedAnchors = remember(savedSpecs) { savedSpecs.associateWith { PreciseClickState() } }
    // 开着二级菜单那一行的锚点（开的时候记下来，`ModeMenuPopups` 用）；
    //  没记录到就给 [coldAnchor] 兜一下（真走到那里说明触点丢了）
    var subAnchor by remember { mutableStateOf<PreciseClickState?>(null) }
    val coldAnchor = remember { PreciseClickState() }

    fun land(
        spec: ActionCatalog.Spec,
        anchor: PreciseClickState,
    ) {
        tick()
        when {
            // 「选择应用」不能落成一个空的 `app:`：交给挑应用页（去哪儿由调用点说）
            spec.token == ActionCatalog.APP_TILE -> {
                sheet.onDismiss()
                sheet.onOpenApp()
            }

            // 入口格 → 弹它那一层菜单（抽屉原地留着，与锚点浮层那套一致）
            //  判据是「这一档有没有一层菜单」而不是「是不是入口格」：入口格里「选择应用」那一格
            //  早在上一支就被接走了，剩下的正好是「点开还有一层」的那些。
            ActionCatalog.modeMenu(spec.token) != null -> {
                XLog.w("抽屉：点 ${spec.label} → 开二级菜单")
                subAnchor = anchor
                subTile = spec
                //  高亮要落在**这一行**上，得自己留一份（`subTile` 会在回调前被清，见其注释）
                openedTile = spec
            }

            //  普通一行：**只记下来**，等「确定」再交给调用点
            else -> {
                pickedRow = spec
                pickedToken = spec.token
            }
        }
    }

    val ctx =
        StepSheetCtx(
            icons = icons,
            cards = sheetCards,
            anchorOf = { anchors.getValue(it) },
            isCurrent = { isCurrent(it) },
            onLand = { spec, anchor -> land(spec, anchor) },
        )
    //  「已保存的命令」那一组的锚点是另外一份（见 `savedAnchors`），其余上下文相同
    val savedCtx =
        StepSheetCtx(icons, sheetCards, { savedAnchors.getValue(it) }, { isCurrent(it) }) { spec, anchor ->
            land(spec, anchor)
        }

    OverlayBottomSheet(
        show = show,
        title = sheet.title,
        //  **底色取页面同一个槽**：页面 Scaffold 用的是 `surface`（见 `PageContent` 里那句
        //  `containerColor = COUITheme.colorScheme.surface`），而抽屉默认给的是 `background`
        //  —— COUI 里这是两档色，并排看能看出抽屉比页面深/浅一档。
        //  暗色下再抬一档到 `surfaceContainer`，理由见上面 [sheetColor] 那段注释。
        backgroundColor = sheetColor,
        onDismissRequest = sheet.onDismiss,
        onDismissFinished = sheet.onDismiss,
        //  右上角那颗「确定」：**应用选中的那一行**，然后收抽屉。
        //  没点过任何行时它只是「关掉」—— 不会把当前那一步重新写一遍。
        endAction = {
            IconAction(
                PageIcons.Confirm,
                I18n.get(Strings.COMMON_CONFIRM, "确定"),
            ) {
                tick()
                pickedToken?.let { token ->
                    XLog.w("抽屉：确定 → 应用 $token")
                    sheet.onPick(token)
                }
                sheet.onDismiss()
            }
        },
        //  **抽屉内容的内边距全在这一个参数上**（`BottomSheetDefaults.insideMargin`，它同时套在
        //  把手 / 标题 / 内容上：横向 `padding(horizontal)`、纵向只取 height 做 `padding(bottom)`）：
        //   · 横向默认 24dp + 行自己的 16dp = 离屏幕边 40dp，太宽
        //     ⇒ 收到 [PAGE_MARGIN]，标题与行文字的左边线因此对齐在同一条 16dp 线上；
        //   · 纵向**留在 0**：那一档底边留白挪进**滚动内容里**（见下面 `Column` 的
        //     `.padding(bottom = PAGE_BOTTOM_PADDING)`）—— 挂在 `insideMargin` 上是在滚动视口
        //     **外面**加内缩，视口被压短、最后一行还是被底边吃掉一截。
        insideMargin = DpSize(PAGE_MARGIN, 0.dp),
        //  **断掉抽屉对内容滚动的接管**：库里那条 `NestedScrollConnection` 会把「内容滚不动之后
        //  剩下的余量」当成往下拽抽屉（见 `BottomSheetContentLayout.onPostScroll`），所以滚到底
        //  再松手 = dismiss。只把内容那层设成 `nestedScrollToParent = false` **不够**：那一位只
        //  挡手指拖动（`source == UserInput`）那一支，松手之后的**惯性**走的是另一个分支，那里
        //  无条件往回派发 ⇒ fling 的余量照样喂给抽屉。
        //  这一位是库自己给的开关：false ⇒ 三条回调一律 `Offset.Zero`，内容怎么滚都动不了抽屉。
        //  抽屉仍能用把手下拉（把手那颗 `draggable` 是它自己的手势）、也能点外部 / 返回收掉。
        enableNestedScroll = false,
    ) {
        //  内容自己滚：候选可能有几十行，抽屉高度跟着内容长、长到装不下就停在 `heightIn` 这一档。
        //
        //  `overScrollVertical(nestedScrollToParent = false)`：余量在原地做成橡皮筋回弹，
        //  **不往上传**（默认 true 时会继续派发，抽屉照样收得到、照样被拖下去）。
        //  「按住拖到头也会弹」是这一支必然的行为（库不区分拖动与惯性）—— 试过去掉，
        //  结果连越界回弹一起没了，用户口径「那算了改回去」。
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = STEP_SHEET_MAX_HEIGHT)
                .overScrollVertical(nestedScrollToParent = false)
                .verticalScroll(rememberScrollState())
                //  底边那一档留白**在滚动内容里**（与各页 `.padding(bottom = PAGE_BOTTOM_PADDING)`
                //  同一个位置、同一个值）：滚到底时它垫在最后一行下面，不占滚动视口。
                .padding(bottom = PAGE_BOTTOM_PADDING),
        ) {
            StepSheetGroups(ctx)
            //  **已保存的命令排在最后**：这是用户自己写的东西，挑的时候上面几组通常已经扫过一遍。
            //  一条都没存过时整组不出现 —— 这是「挑一条来用」的抽屉，没得挑就别摆一栏空的；
            //  「还没保存过命令」这件事由「已保存的命令」那张空状态卡去说。
            if (savedSpecs.isNotEmpty()) {
                SavedCommandsGroup(savedSpecs, savedCtx)
            }
        }
    }

    // 入口格那一层菜单（档位）——「系统开关」再往下的那一问是**弹窗**，也在同一个组件里。
    //  锚点用**被点那一行**自己那份 —— 菜单才跟着手指落。
    //  选中的那一档也**只记下来**：高亮落在它所属的入口格那一行上，等「确定」再应用。
    ModeMenuPopups(
        preciseClick = subAnchor ?: coldAnchor,
        tile = subTile,
        current = pickedAction ?: sheet.current,
        onPick = { spec ->
            tick()
            XLog.w("抽屉二级菜单：选中 ${spec.label}（待确定）")
            //  高亮落在**入口格那一行**上（不是点的那一档）：入口格代表整组取值。
            //  取 [openedTile] 而不是 [subTile] —— 后者此刻已被 `onDismiss` 清成 null。
            pickedRow = openedTile
            pickedToken = spec.token
            pickedAction = Action.parse(spec.token)
            subTile = null
            subAnchor = null
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
    val icons: Map<ActionCatalog.Spec, ImageVector>,
    val cards: CardColors?,
    val anchorOf: (ActionCatalog.Spec) -> PreciseClickState,
    val isCurrent: (ActionCatalog.Spec) -> Boolean,
    val onLand: (ActionCatalog.Spec, PreciseClickState) -> Unit,
)

/**
 * 内置动作那几组 —— 分组与网格同一张表（[ActionCatalog.STEP_GROUPS]），组名与组内顺序逐项一致。
 *
 *  组与组之间要断开：`SmallTitle` 自带的上边距只有 8dp，紧贴上一张卡片；这里补一档
 *  [STEP_SHEET_SECTION_GAP]，与各页「Spacer(12.dp) + SmallTitle」同一套；第一组不补 ——
 *  上面就是抽屉标题。
 */
@Composable
private fun SettingsPage.StepSheetGroups(ctx: StepSheetCtx) {
    ActionCatalog.STEP_GROUPS.forEachIndexed { groupIndex, (groupTitle, group) ->
        if (groupIndex > 0) Spacer(Modifier.height(STEP_SHEET_SECTION_GAP))
        SmallTitle(ActionCatalog.groupTitleLabel(groupTitle))
        StepSheetCard(group, ctx)
    }
}

/** 「已保存的命令」那一组 —— 只有一组、标题固定，与内置那几组共用同一张卡。 */
@Composable
private fun SettingsPage.SavedCommandsGroup(
    specs: List<ActionCatalog.Spec>,
    ctx: StepSheetCtx,
) {
    Spacer(Modifier.height(STEP_SHEET_SECTION_GAP))
    SmallTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"))
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
        //  暗色下卡片比抽屉面再亮一档（见 `sheetCards`）；浅色传 null = 库默认，与页面同一档
        colors = ctx.cards,
        rows =
            specs.map { spec ->
                val anchor = ctx.anchorOf(spec)
                val glyph =
                    ctx.icons.getOrElse(spec) {
                        XLog.w("图标表缺项：${spec.token} 用兜底字形")
                        specIcon(spec)
                    }
                // 行尾那颗字形按「点开去哪」分（与页面里那几行同一套口径）：
                //  · 「选择应用」进的是**另一个页面**去挑 → 右箭头（[PageIcons.EnterArrow]）；
                //  · 入口格那几档（系统开关 / 媒体控制…）点开还有**一层菜单** → 选择器字形；
                //  · 其余动作点一下就落成一步、`cmd:<名字>` 同样落成一步 → 不给。
                //  **选中那一行也照画**：选中只把行首换成对勾，行尾该不该有指示不变。
                val trailingIcon =
                    when {
                        spec.token == ActionCatalog.APP_TILE -> PageIcons.EnterArrow
                        ActionCatalog.modeMenu(spec.token) != null -> PageIcons.Selector
                        else -> null
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

/** 组与组之间那一档留白 —— 与各页「Spacer(12.dp) + SmallTitle」同一档。 */
private val STEP_SHEET_SECTION_GAP = 12.dp

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
                animationSpec = STEP_SHEET_LEAD_FADE,
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

/** 行首字形交叉淡入淡出的节奏 —— 与颜色同一条曲线、同一时长（[COLOR_TRANSITION_SPEC] 的浮点版）。 */
private val STEP_SHEET_LEAD_FADE = tween<Float>(durationMillis = 200, easing = DecelerateEasing())

/** 抽屉内容的最高高度 —— 再高就把整屏都盖住了，留一档给「它是个抽屉」这件事。 */
private val STEP_SHEET_MAX_HEIGHT = 460.dp
