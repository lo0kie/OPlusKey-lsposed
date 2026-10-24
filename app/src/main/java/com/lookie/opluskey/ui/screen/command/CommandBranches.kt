package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.HINT_SLOT_COMMAND
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.STEP_ROW_ENTER
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT_MS
import com.lookie.opluskey.ui.model.actionCellIcon
import com.lookie.opluskey.ui.model.actionCellTitle
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionRowTitle
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.NO_FRESH_ROW
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

// 「条件执行」那一页的编辑区 —— 从 `CommandPage.kt` 拆出来：那边是「一页的骨架」，
// 这一块是分叉特有的那张卡；四张菜单在 `CommandBranchMenus.kt`。

/** 分叉页在编辑哪一档 —— 当前这颗键与这一档手势，两处（卡片与顶栏小标题）同一份判据。 */
internal fun SettingsPage.branchGestureNow(): Gesture = Gesture.entries.getOrElse(branchGesture) { Gesture.SINGLE }

/** 分叉页在编辑哪一颗键。 */
internal fun SettingsPage.branchKeyNow(): KeyId = KeyWatchState.editing(configState.config.value)

/**
 * 条件卡 —— 一条规则一行，末尾是「添加条件」与固定的默认那一行。
 *
 *  ## 一行是什么
 *
 *  标题是 `当 微信 在前台 → 手电筒`：条件是这一条的判定，箭头右边是它命中后执行的动作。
 *  与执行侧同一条顺序 —— **从上往下找，第一条成立的就执行**。
 *
 *  ## 末尾那一行（default）
 *
 *  `侧键-单击` —— 所有条件都不成立时执行**按键绑定里这一档绑的命令**。它不可编辑、不可删：
 *  那个命令属于「按键绑定」页，分叉只负责在上面加条件（所以分叉不占用绑定）。
 *
 *  ## 点一行做什么
 *
 *  弹一层菜单：换条件 / 换命令 / 删掉这一条（见 [BranchMenus]）。
 */
@Composable
internal fun SettingsPage.BranchCards(
    tick: () -> Unit,
    emptyHeight: Dp,
) {
    val draft = branchDraft.draft
    //  **草稿载入之前整块不画**：第一帧 `rules` 必为空（草稿在 `LaunchedEffect` 里才灌进来），
    //  于是先画空态、载入后再换成表格 —— 那一换会把整块内容播一遍转场，
    //  表现就是「刚进页，内容从顶部滑下来」。载入完成后再画，第一帧就是最终形态，
    //  整块没有任何转场。这一档只持续一帧（`load` 在同一次组合的第一帧内跑完）。
    if (draft.owner == null) return
    val rules = draft.rules
    //  退出中的那一支还要画行与注脚，而这时 `rules` 已经空了（删最后一条是立即移除）——
    //  直接读它会让行瞬间消失、注脚弹回顶部。所以留一份「最后一次非空」的快照，退出那一支用它画。
    //  比较用结构相等：内容没变就不写，不会自己触发重组。
    //
    //  **必须在 `anchors` 之前**：锚点表要按快照的条数建，否则退出那一支会拿到一张空表、
    //  按行下标去取就是 `IndexOutOfBoundsException`（真机崩过一次）。
    val shownRules = remember { mutableStateListOf<StateRule>() }
    val shownIds = remember { mutableStateListOf<Int>() }
    if (rules.isNotEmpty() && shownRules != rules) {
        shownRules.clear()
        shownRules.addAll(rules)
        shownIds.clear()
        shownIds.addAll(draft.ids)
    }
    // 这一份状态在页面里只建一次（里面的锚点会随「点的是哪一格」被换掉）。
    val menus = remember { BranchMenuState() }
    // **每一格一份锚点**：左列（条件）/ 右列（动作）/ 中间那颗「更多」各是一个触点，
    //  各有一套坐标。按**快照**的条数建 —— 退出那一支画的也是快照。
    val anchors = remember(shownRules.size) { List(shownRules.size) { RuleAnchors() } }
    // 刚加的那一行的身份 —— 只有它播入场（其余行是载入时就有的，进页不该整片展开一遍）。
    // 加一条 = 插一条**条件和动作都空着**的行：不先填默认条件（「亮屏中」看着像用户自己选的，
    //  一成立就悄悄接管这一档），也不弹菜单再落（那会把「加一条」变成两步）。
    //
    //  加的是**第一条**时不播行进场：那一下整块高度由外层（空态 ↔ 表格）那套动画负责，
    //  里层再展开一遍就是同一条动画跑两遍 —— 用户口径「这样两遍动画太拖了」。
    //  第二条起外层不再切换（`isEmpty` 一直是 false），行照常播自己的进场。
    // 删除**先播收缩、再动数据**（见 `BranchMenuState.deletingAt`）：数据晚一步，行才不会瞬间消失。
    //  收尾再核一次「要删的还是不是它」—— 收缩没播完又删了另一条时，这一条留着（宁可不删，不能错删）。
    //
    //  **删的是最后一条时不等**：那一下整块要变成空态，而空态由外层（空态 ↔ 表格）那套动画接管；
    //  要是还照「先收行 220ms + 结算 40ms、再展开空态 220ms」，就是两段串在一起。
    //  直接移除，行不单独收缩、只跟着外层一起淡出。
    LaunchedEffect(menus.deletingAt) {
        val at = menus.deletingAt
        if (at < 0) return@LaunchedEffect
        //  记下「要删的是哪一条」而不是只记下标：这 260ms 里用户还能上移 / 下移 / 再删一条，
        //  下标会移位，按 at 去删就会删错行。按**规则本身**找回来，挪过也能删对；
        //  找不到（已经被改掉了）就什么都不删 —— 宁可不删，不能错删。
        val target =
            draft.rules.getOrNull(at) ?: run {
                menus.deletingAt = NO_EXITING_ROW
                return@LaunchedEffect
            }
        if (rules.size > 1) delay(STEP_ROW_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.deletingAt == at) {
            val now = draft.rules.indexOf(target)
            if (now >= 0) draft.remove(now)
            menus.deletingAt = NO_EXITING_ROW
        }
    }
    //  空态与表格**都在滚动内容里**（用户口径「空态要跟着拖动一起弹」）—— 空态做成浮层虽然
    //  省事，但拖动时它一动不动，读起来像「这一页不能拖」。
    AnimatedContent(
        targetState = rules.isEmpty(),
        transitionSpec = { BRANCH_BODY_TRANSFORM },
        contentAlignment = Alignment.TopCenter,
        label = "branch-body",
    ) { empty ->
        if (empty) {
            //  一屏高（`emptyHeight`）：这样它才在**可见区**里居中，而且整块在滚动内容里，
            //  拖动时跟着弹。
            Box(
                Modifier.fillMaxWidth().height(emptyHeight),
                contentAlignment = Alignment.Center,
            ) {
                BranchEmptyHint()
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                BranchRuleColumn(
                    rules = shownRules,
                    ids = shownIds,
                    freshId = draft.freshId,
                    menus = menus,
                    anchors = anchors,
                )
                //  表尾那句注脚 —— **不给它任何动画**（用户口径「tips 固定在那，让新条件把 tip 挤下去」）。
                //
                //  它自己不参与动画就没有「快慢」可言：位置只由**行高**决定 —— 加一条被新行挤下去、
                //  删一条跟着收上来。以前几版都错在给它单独一套 `AnimatedVisibility`：那是第二套动画，
                //  起跑点、时长、退出时的重新组合全都要和列表对齐，怎么调都会差一拍。
                BranchDefaultTip()
            }
        }
    }
    BranchMenus(rules = rules, menus = menus, tick = tick)
    BranchDeleteDialog(menus)
}

/**
 * 空态 ↔ 表格切换时**淡入淡出**那一段 —— 两边各淡各的（空态是页面浮层，见 `BranchEmptyOverlay`；
 *  这里是行那一层），时长取同一档，两处一起交代完。
 */
private const val BRANCH_BODY_MS = 150

/**
 * 空态 ↔ 表格那一层的内容转场：**只做交叉淡入淡出**，不做尺寸插值。
 *
 *  空态那块是一屏高、表格至少也占满可见区，两边高度差不多；一旦插值，高度会「先收再涨」，
 *  看着就是一次滑。这一层是这一页**唯一**一处整块转场 —— 行自己的进出场在 `BranchRow` 里，
 *  表尾那句注脚不参与任何动画。
 */
private val BRANCH_BODY_TRANSFORM: ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(tween(BRANCH_BODY_MS, easing = DecelerateEasing())),
        initialContentExit = fadeOut(tween(BRANCH_BODY_MS, easing = AccelerateEasing())),
        sizeTransform = null,
    )

/**
 * 「添加条件」那一下 —— 末尾那条**条件和命令都还空着**时不再往下加：新加的行一样是空的，
 * 堆两条空行既看不出差别、校验也过不了。说一句就停在这一条上，让人先去把它填了。
 *
 * 加的是**第一条**时返回 [NO_FRESH_ROW]：那一下整块高度由外层（空态 ↔ 表格）那套动画负责，
 * 里层再展开一遍就是同一条动画跑两遍。
 */
internal fun SettingsPage.addBranchRule(
    rules: List<StateRule>,
    tick: () -> Unit,
    onAdded: (Int) -> Unit,
) {
    val tail = rules.lastOrNull()
    if (tail != null && tail.conditions.isEmpty() && tail.action == Action.None) {
        tick()
        //  **同一个语义位**：连点几下是这一句在改字，不是叠出一串
        hint(
            I18n.get(Strings.COMMAND_BRANCH_NEED_CONDITION, "还有条件没选完"),
            slot = HINT_SLOT_COMMAND,
        )
        return
    }
    val first = rules.isEmpty()
    val id = branchDraft.draft.add(StateRule.single(branchGestureNow(), null, Action.None))
    onAdded(if (first) NO_FRESH_ROW else id)
}

/**
 * 表格那一列（一行一条规则）—— 单独一支只为让 [BranchCards] 那支短下来（它现在还要管
 * 空态与表格的切换）。
 *
 *  行距挂在行自己身上（见 [BranchRow]）：挂在这层 `spacedBy` 的话，退场时那一档空档不会跟着收缩。
 *  `key` 按**行身份**认（不是位次）：删中间一行时，后面的行不该继承被删那一行的「已退场」状态。
 */
@Composable
private fun SettingsPage.BranchRuleColumn(
    rules: List<StateRule>,
    ids: List<Int>,
    freshId: Int,
    menus: BranchMenuState,
    anchors: List<RuleAnchors>,
) {
    //  哪几行是死分支（永远轮不到）—— 小标题上直接标出来，不必等点「确认」才被告知
    val dead = deadIndexesOf(rules)
    Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        // 只剩一条时它**不自己收缩**：那一条的消失由外层空态那一套接管（见 `BranchCards` 里
        //  那个 effect）。不然数据被移除之前先闪一帧收缩，就成了「行先缩、空态再长」两段。
        val shrinkable = rules.size > 1
        rules.forEachIndexed { index, rule ->
            key(ids[index]) {
                BranchRow(
                    rule = rule,
                    spec =
                        RuleRowSpec(
                            index = index,
                            anchors = anchors[index],
                            fresh = ids[index] == freshId,
                            exiting = shrinkable && menus.deletingAt == index,
                            dead = index in dead,
                        ),
                    menus = menus,
                )
            }
        }
    }
}

/**
 * 列表里的一行 —— 行本体 + 它自己那份进出场。
 *
 *  ## 进场只给「刚加的那一行」
 *
 *  [RuleRowSpec.fresh] 是「这一行的身份 == 刚加的那一个」。老行在载入那一刻就该是可见的 ——
 *  进页时整片展开，看着像页面还没准备好。
 *
 *  ## 退场
 *
 *  `exiting` 由「删除确认」那一位驱动，规格与「多条命令」的步骤行**同一套**
 *  （[STEP_ROW_ENTER] / [STEP_ROW_EXIT]）：两个页面上「行走了」应当是同一个观感。
 *  退场期间数据还在（见 `BranchMenuState.deletingAt`），所以这一行仍被组合着，动画播得完。
 *
 *  行距挂在 `AnimatedVisibility` **里面**（且挂在行首）：退场时它跟着高度一起收掉，下面的行是
 *  一次平滑上移，而不是「收完再跳一档间距」；挂行首还让「最后一条 + 表尾注脚」的间距等于注脚
 *  自己的 6dp（与「自定义命令」页的 root 提示同一档）。
 */
@Composable
private fun SettingsPage.BranchRow(
    rule: StateRule,
    spec: RuleRowSpec,
    menus: BranchMenuState,
) {
    // 首帧可见性：老行 true（不播进场），刚加的那一行从 false 起（播一次）
    val state = remember { MutableTransitionState(!spec.fresh) }
    LaunchedEffect(state, spec.exiting) { state.targetState = !spec.exiting }
    AnimatedVisibility(visibleState = state, enter = STEP_ROW_ENTER, exit = STEP_ROW_EXIT) {
        //  行距挂在**行首**而不是行尾（第一条不挂）：挂行尾的话最后一条底下会多出一档，
        //  表尾那句注脚再补 6dp，实际间距就成了 [GRID_ROW_GAP] + 6dp —— 而「自定义命令」页
        //  输入框下面那行 root 提示是 6dp，两处读起来差三倍（用户口径「tips 的上边距和它一样」）。
        //  挂行首之后：行与行之间还是那一档，最后一条下面**只剩注脚自己那 6dp**；
        //  追加一条时也不会让上一条的高度变化（新行自带它那一档）。
        Box(Modifier.padding(top = if (spec.index == 0) 0.dp else GRID_ROW_GAP)) {
            //  两格各自「有没有选完」先算出来：文案与行首图标都看它
            //  （条件那格还要 `isUsable` —— 选了种类但没选取值，写下去也存不住）。
            //  一块可能带**好几条**条件：全部配完才写出来，几条之间用「 + 」连（这个符号两种
            //  语言里都读得通，不必再加一个字典键）。少一条没配完 ⇒ 仍然是「未选择条件」。
            val conditionText =
                rule.conditions
                    .takeIf { rule.isReady }
                    ?.joinToString(CONDITION_JOINER) { conditionRowTitle(it, ::appNameOf) }
            //  命令那格写成「父级 - 子级」（响铃状态 - 循环）：与条件那格同一套写法，
            //  光写「循环」看不出是谁在循环（见 `actionCellTitle`）
            val actionText = if (rule.action == Action.None) null else actionCellTitle(rule.action, ::appNameOf)
            RuleBlock(
                // 「条件 1」「条件 2」—— 一份表里第几条，标题上直接写出来
                index = spec.index,
                dead = spec.dead,
                text =
                    RuleText(
                        // 没选完的那一格写「未选择条件 / 未选择命令」，**行首图标同时置红**：
                        //  这是一条存不下去的半成品，灰色小字读起来像普通占位符。
                        condition = conditionText ?: I18n.get(Strings.COMMAND_BRANCH_PICK_CONDITION, "未选择条件"),
                        action = actionText ?: I18n.get(Strings.COMMAND_BRANCH_NO_COMMAND, "未选择命令"),
                        conditionEmpty = conditionText == null,
                        actionEmpty = actionText == null,
                        conditionIcon =
                            rule.conditions
                                .firstOrNull { it.isUsable }
                                ?.kind
                                ?.let(::conditionIcon) ?: PageIcons.CommandBranch,
                        actionIcon =
                            if (rule.action == Action.None) {
                                PageIcons.CommandSingle
                            } else {
                                //  图标跟着**档位**走：循环是重复箭头，不是响铃那格那颗
                                actionCellIcon(rule.action, configState.commands.value)
                            },
                    ),
                cells =
                    RuleCells(
                        anchors = spec.anchors,
                        onCondition = {
                            menus.condAnchor = spec.anchors.left
                            menus.condAt = spec.index
                        },
                        onAction = {
                            menus.commandAnchor = spec.anchors.right
                            menus.commandAt = spec.index
                        },
                        onMenu = {
                            menus.menuAnchor = spec.anchors.menu
                            menus.menuAt = spec.index
                        },
                    ),
            )
        }
    }
}

/** 一行「是第几条、跟哪两格、要不要播进出场」—— 合成一个对象把参数个数压回 detekt 的上限。 */
private class RuleRowSpec(
    val index: Int,
    val anchors: RuleAnchors,
    val fresh: Boolean,
    val exiting: Boolean,
    /** 这一行**永远轮不到**（它前面某一档条件已经凑齐）—— 小标题上标出来。 */
    val dead: Boolean,
)

/** 「没有正在退场的行」的哨兵 —— 与 `BranchMenuState.deletingAt` 的初始值同义。 */
private const val NO_EXITING_ROW = -1

/** 退场播完再多等一帧才动数据：让收缩的最后一帧先落到屏幕上，删完不会看到一次跳变。 */
private const val ROW_EXIT_SETTLE_MS = 40L

/** 每一行那三个触点（左列 / 右列 / 中间那颗「更多」）—— 一行一套。 */
private class RuleAnchors {
    val left = PreciseClickState()
    val right = PreciseClickState()
    val menu = PreciseClickState()
}

/** 一块里几条条件之间那个符号 —— 语言中立，不用进字典。 */
private const val CONDITION_JOINER = " + "

/**
 * 小标题那一行的**最小**高度 —— 标题与右端那颗三点都在这一行里竖居中。
 *
 *  32dp 是 [SmallTitle] 自己的下界（12sp 的字 + 上下各 8dp 内边距）；写成固定高度会把字裁掉半行。
 */
private val RULE_TITLE_HEIGHT = 32.dp

/**
 * 小标题行右端那颗「更多」的点击区 —— **横比竖宽**。
 *
 *  横 40dp：三点横排，方形的点击区在字尾读起来像一块砖，横向多让一点才像「这一行的尾巴」。
 *  竖 20dp：比标题行（[RULE_TITLE_HEIGHT]）矮一档，上下各让出一点，才是一颗「浮在行尾」的按钮；
 *  里面那颗字也按这个高度画（见 `RuleBlock`）：库默认 24dp 装不进去，会被圆角裁掉上下两条。
 */
private val RULE_MENU_WIDTH = 40.dp
private val RULE_MENU_HEIGHT = 20.dp

/** 表尾那句注脚 —— 与「自定义命令」页那条权限提示同一个样式（不占卡片、居中、`footnote1`）。 */
@Composable
private fun SettingsPage.BranchDefaultTip() {
    Text(
        //  只留这一句，不接「：侧键 - 单击」那截详细 —— 那一档顶栏尾随小标题已经写着
        text = I18n.get(Strings.COMMAND_BRANCH_DEFAULT, "以上都不成立时，执行按键绑定里的命令"),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 6.dp),
        textAlign = TextAlign.Center,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** 两列每一格的圆角 —— 两个圆角块之间那条缝就是「gap」。 */
private val RULE_CELL_SHAPE = RoundedCornerShape(8.dp)

/**
 * 一行两列那四个「点哪个 / 点了做什么」 —— 合成一个对象只为把参数个数压回 detekt 的上限。
 */
private class RuleCells(
    val anchors: RuleAnchors,
    val onCondition: () -> Unit,
    val onAction: () -> Unit,
    val onMenu: () -> Unit,
)

/** 一条规则那两行「写什么、配哪颗字形」—— 合成一个对象，[RuleBlock] 的参数才压得回 5 个以内。 */
private class RuleText(
    val condition: String,
    val action: String,
    /** 条件那一格还没选完 —— 文案换成「未选择条件」，行首图标置红。 */
    val conditionEmpty: Boolean,
    /** 命令那一格还没选 —— 同上。 */
    val actionEmpty: Boolean,
    val conditionIcon: ImageVector,
    val actionIcon: ImageVector,
)

/**
 * 一条规则那一块 —— **小标题 + 上下两格**：上面是条件、下面是命令。
 *
 *  为什么不再左右并排：条件与命令都是长句（「微信 在前台」/「以小窗口打开 微信」），
 *  一行里各占一半时两边都在打省略号，看不出选的是哪一条；竖下来各自整行，读起来才是
 *  「这一条什么时候、做什么」。小标题写「条件 N」，删掉一条之后序号也跟着重排。
 *
 *  两格各自可点（上面换条件、下面换动作），所以是两个独立的触点，各挂一份
 *  [preciseClickAnchor]：菜单弹在**被点的那一格**旁边，而不是整块。
 *
 *  这一条自己的菜单（上移 / 下移 / 删除）挪到小标题那一行右端：两格都通栏之后，
 *  中间没有位置再放那颗三点了；挂在标题行也不会和两格的点击区抢。
 *
 *  @param text 上下两行写什么、各配哪颗字形（见 [RuleText]）
 *  @param cells 两格与那颗「更多」的触点（见 [RuleCells]）
 */
@Composable
private fun RuleBlock(
    index: Int,
    /** 这一行**永远轮不到**：小标题后缀写明，免得用户配完才发现它不会执行。 */
    dead: Boolean,
    text: RuleText,
    cells: RuleCells,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = RULE_TITLE_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            //  与各页的小标题**同一个组件**（同一档字号、颜色、行高）：「条件 N」是这一块的小标题，
            //  不是正文。横向只把库里那 32dp 改成 20dp —— 默认值里含 12dp 页面边距，而这一页的外层
            //  已经有 [PAGE_MARGIN]，照抄会多缩进一格（真机口径：两者字头都落在 112px @3.5x）。
            SmallTitle(
                text = I18n.format(Strings.COMMAND_BRANCH_ITEM, index + 1),
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
            Box(
                modifier =
                    Modifier
                        .size(width = RULE_MENU_WIDTH, height = RULE_MENU_HEIGHT)
                        .preciseClickAnchor(cells.anchors.menu)
                        .clip(RULE_CELL_SHAPE)
                        .clickable(onClick = cells.onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TablerIcons.Outline.Dots,
                    contentDescription = I18n.get(Strings.COMMON_MORE, "更多"),
                    tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                    // 与点击区同高：库默认 24dp 塞不进 20dp 的框，上下会被圆角裁掉
                    modifier = Modifier.size(RULE_MENU_HEIGHT),
                )
            }
        }
        //  一行条件、一行命令，**同一张卡**（与各页的 listview 同一个壳：Card + 行间细线 +
        //  首尾圆角），不再拆成两个胶囊 —— 拆开时中间那道缝读起来像两件不相干的事。
        //  两行各自可点，行尾那枚选择器说明「点开还有一层」（换条件 / 换动作）。
        CardList(
            margin = 0.dp,
            rows =
                listOf(
                    CardRow.Item(
                        title = text.condition,
                        icon = text.conditionIcon,
                        //  没选完的那一格：图标与文字**都压到禁用色**（同一档），不加别的记号 ——
                        //  这一行本身仍可点，靠文案「未选择条件」说清缺什么就够了。
                        iconTint = if (text.conditionEmpty) COUITheme.colorScheme.disabledOnSecondaryVariant else null,
                        titleTint = if (text.conditionEmpty) COUITheme.colorScheme.disabledOnSecondaryVariant else null,
                        //  死分支（前面某一档条件已经凑齐）在条件这一行末尾标红 ——
                        //  不必等点「确认」被拦下才知道这一条永远不会执行。
                        trailing = { ConditionTrailing(dead) },
                        modifier = Modifier.preciseClickAnchor(cells.anchors.left),
                        onClick = cells.onCondition,
                    ),
                    CardRow.Item(
                        title = text.action,
                        icon = text.actionIcon,
                        iconTint = if (text.actionEmpty) COUITheme.colorScheme.disabledOnSecondaryVariant else null,
                        titleTint = if (text.actionEmpty) COUITheme.colorScheme.disabledOnSecondaryVariant else null,
                        trailing = { SelectorGlyph() },
                        modifier = Modifier.preciseClickAnchor(cells.anchors.right),
                        onClick = cells.onAction,
                    ),
                ),
        )
    }
}

/**
 * 条件那一行的行尾：死分支先标一句红的，再接那枚选择器。
 *
 *  为什么标在**这一行**而不是小标题：小标题是库组件 `SmallTitle`，只能给一整串字，染不了一半；
 *  而这一行本来就是「这一条什么时候执行」，写在这儿读起来最顺。
 */
@Composable
private fun ConditionTrailing(dead: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dead) {
            Text(
                I18n.get(Strings.COMMAND_BRANCH_DEAD, "不会执行"),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.error,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        SelectorGlyph()
    }
}

/** 行尾那枚「点开还有一层」的选择器字形 —— 与菜单、网格里同一个记号（[PageIcons.Selector]）。 */
@Composable
private fun SelectorGlyph() {
    Icon(
        PageIcons.Selector,
        contentDescription = null,
        modifier = Modifier.size(ROW_END_ICON_SIZE),
        tint = COUITheme.colorScheme.onSurfaceVariantActions,
    )
}
