package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.lookie.opluskey.ui.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.MoreButton
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.STEP_ROW_ENTER
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT_MS
import com.lookie.opluskey.ui.model.actionCellIcon
import com.lookie.opluskey.ui.model.actionCellTitle
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.state.NO_FRESH_ROW
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

// 「条件执行」那一页的编辑区 —— 从 `CommandPage.kt` 拆出来：那边是「一页的骨架」，
// 这一块是分叉特有的那张卡；四张菜单在 `CommandBranchMenus.kt`。

/** 分叉页在编辑哪一档 —— 当前这颗键（或组合键）与这一档手势，两处（卡片与顶栏小标题）同一份判据。 */
internal fun SettingsPage.branchGestureNow(): Gesture {
    val now = Gesture.entries.getOrElse(branchGesture) { Gesture.SINGLE }
    //  组合键只有短按 / 长按两档（双击在组合上没有定义，见 `ComboKey` 的类注释）。
    //  在编组合键时双击那一档自动落回短按：页面停在一个「写进去也永远不会命中」的档上
    //  比落回短按更糟 —— 用户会以为配了双击。
    return if (now == Gesture.DOUBLE && branchDraft.draft.comboId != null) Gesture.SINGLE else now
}

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
    BranchDeleteEffect(draft = draft, menus = menus)

    //  删**一条条件**的收尾 —— 与上面删整行同一套：先让那一行收起（`exitingCond` 驱动行上的
    //  `visible`），播完再从草稿里摘掉。确认之后菜单已经关了，这 200ms 里加不了也删不了另一条，
    //  所以按下标去删是安全的；仍核一次「要删的还是不是它」（宁可不删，不能错删）。
    LaunchedEffect(menus.exitingCond) {
        val target = menus.exitingCond ?: return@LaunchedEffect
        delay(VISIBILITY_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.exitingCond == target) {
            deleteBranchCondition(target.at, target.index)
            menus.exitingCond = null
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
    //  停用 / 启用一条场景 —— 与删除同一条路（菜单里点、窗里确认），但改的是这一条自己的开合位
    BranchToggleDialog(menus)
    //  删「一条条件」是另一件事（只摘一块里的一条，不连动作、不动下面的行），窗也另开一个
    BranchConditionDeleteDialog(menus)
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
            I18n.get(Strings.COMMAND_BRANCH_NEED_CONDITION, "还有场景没选完"),
            slot = HINT_SLOT_DISABLED,
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
                            //  「停用」优先于「不会执行」：那是用户刚做过的动作，也是更直接的原因
                            state =
                                when {
                                    !rule.enabled -> RuleState.OFF
                                    index in dead -> RuleState.DEAD
                                    else -> RuleState.NORMAL
                                },
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
    //  **播过就把「刚加的那一行」这笔账清掉**：不清的话这一行会一直带着 `fresh = true`，
    //  而 `MutableTransitionState` 是跟着行一起新建的 —— 页面一旦重建（进「选择应用」页挑完
    //  返回、来源页的组合已经注销），它又按 `!fresh = false` 从头播一遍进场。
    //  真机表现：「刚加一行 → 去挑动作 → 回来整行又展开一次」。
    //  清在这里而不是 `BranchRow` 之外：只有这一行知道自己已经上过场了。
    LaunchedEffect(Unit) {
        if (spec.fresh) branchDraft.draft.freshId = NO_FRESH_ROW
    }
    LaunchedEffect(state, spec.exiting) { state.targetState = !spec.exiting }
    AnimatedVisibility(visibleState = state, enter = STEP_ROW_ENTER, exit = STEP_ROW_EXIT) {
        //  块与块之间的间距**不在这里给**：小标题自己带上间距（上 16 / 下 0，见 `SectionTitle`），
        //  这里再补一档就成了 24dp。行距原本挂在行首（第一条不挂）是为了让「行与行之间」
        //  保持一档、最后一条下面只留注脚自己那 6dp —— 那个理由现在由标题那层负责。
        Box {
            //  两格各自「有没有选完」先算出来：文案与行首图标都看它
            //  （条件那格还要 `isUsable` —— 选了种类但没选取值，写下去也存不住）。
            //  一块可能带**好几条**条件，**每条各占卡片里的一行**（一条条件一个 `CardRow.Item`）。
            //  一条都没配时也给一行「未选择条件」（`empty = true`），让用户知道这一格要配什么。
            //  以前是用 ` + ` 拼在同一行里 —— 两条以上时读起来像一句话，看不出是「且」的关系。
            val conditionLines = ruleConditionLines(rule)
            //  命令那格写成「父级 - 子级」（响铃状态 - 循环）：与条件那格同一套写法，
            //  光写「循环」看不出是谁在循环（见 `actionCellTitle`）
            val actionText = if (rule.action == Action.None) null else actionCellTitle(rule.action, ::appNameOf)
            RuleBlock(
                // 「条件 1」「条件 2」—— 一份表里第几条，标题上直接写出来
                index = spec.index,
                state = spec.state,
                text =
                    RuleText(
                        conditionLines = conditionLines,
                        action = actionText ?: I18n.get(Strings.COMMAND_BRANCH_NO_COMMAND, "未选择命令"),
                        actionEmpty = actionText == null,
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
                        //  点的是**第几条**条件那一行 —— 菜单随后就改那一条
                        onCondition = { condIndex ->
                            menus.condIndexAt = condIndex
                            menus.condAnchor = spec.anchors.conditionAt(condIndex)
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
                menus = menus,
            )
        }
    }
}

/**
 * 小标题那一行 —— 「条件 N」+ 右端那颗「更多」。
 *
 *  抽成一支只为让 [RuleBlock] 留在 detekt 的长度上限内。与各页的小标题**同一个组件**
 *  （同一档字号、颜色、行高）：「条件 N」是这一块的小标题，不是正文。横向只把库里那 32dp
 *  改成 20dp —— 默认值里含 12dp 页面边距，而这一页的外层已经有 `PAGE_MARGIN`，照抄会多缩进一格
 *  （真机口径：两者字头都落在 112px @3.5x）。
 */
@Composable
private fun RuleTitleRow(
    index: Int,
    cells: RuleCells,
) {
    SectionTitle(
        text = I18n.format(Strings.COMMAND_BRANCH_ITEM, index + 1),
        modifier = Modifier.heightIn(min = RULE_TITLE_HEIGHT),
        //  左右缩进比页面小一档（外层已有 `PAGE_MARGIN`）
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        //  行尾那颗「更多」—— 通用小标题的 `trailing` 槽位（这一行菜单就挂在小标题右侧，
        //  不再自己套一层 `Row` + 一份 `weight`）；样式在 `MoreButton` 里（场景 / 命令列表共用一份）。
        trailing = {
            MoreButton(
                onClick = cells.onMenu,
                anchor = cells.anchors.menu,
                //  **与「多条命令」那颗「命令列表」小标题的「更多」落在同一位置**（同一套几何）：
                //  那一颗的标题行贴满页宽、`endPadding = PAGE_MARGIN(12)`，字形在 40dp 点击区里
                //  居中 ⇒ 字形右边缘在**页边 − 22dp**。
                //  这一行的标题在外层 `PAGE_MARGIN` 里面（行右边缘已经是页边 − 12），所以
                //  **一个 endPadding 都不给**（默认 0）：点击区贴住行右边缘、字形居中 ⇒ 同样是
                //  页边 − 22dp。给 20 或补 `flushEnd` 都会把它往左推 10dp（「右缩进太多」）。
                //  别按「与字头对称」去调 —— 这一页字头在 32dp，两者本来就不对称，跟多条命令一致才是口径。
            )
        },
    )
}

/**
 * 这一行现在处在哪一档 —— 行尾那半句话显示哪一句（两句话只会出一句）。
 *
 *  合成一个枚举而不是两个布尔：两种状态互斥，各自一个位的话调用点要先想「两个都真时显示哪个」，
 *  而那个优先级（先说「已停用」）只该有一处口径。
 */
private enum class RuleState {
    NORMAL,

    /** 顺序上轮不到（某档取值前面已经凑齐 / 被更宽的一条盖住）—— 红字「不会执行」。 */
    DEAD,

    /** 这一条自己关着（行尾菜单里的停用）—— 弱色「已停用」。 */
    OFF,
}

/** 一行「是第几条、跟哪两格、要不要播进出场」—— 合成一个对象把参数个数压回 detekt 的上限。 */
private class RuleRowSpec(
    val index: Int,
    val anchors: RuleAnchors,
    val fresh: Boolean,
    val exiting: Boolean,
    /** 行尾那半句话说什么（见 [RuleState]）。 */
    val state: RuleState,
)

/** 「没有正在退场的行」的哨兵 —— 与 `BranchMenuState.deletingAt` 的初始值同义。 */
internal const val NO_EXITING_ROW = -1

/** 退场播完再多等一帧才动数据：让收缩的最后一帧先落到屏幕上，删完不会看到一次跳变。 */
internal const val ROW_EXIT_SETTLE_MS = 40L

/** 每一行那几处触点（条件那几格 / 动作那一格 / 中间那颗「更多」）—— 一行一套。 */
private class RuleAnchors {
    /**
     * 条件那几格**各一个** —— 一块可以带好几条条件，每条在卡片里各占一行，
     * 菜单要弹在**被点的那一行**旁边，不能共用一份（共用时后布局的行会覆盖前面的坐标）。
     *
     *  按需增长（条件条数会变）：[conditionAt] 幂等，组合期多调几次也不会多长。
     */
    private val conditions = mutableListOf<PreciseClickState>()
    val right = PreciseClickState()
    val menu = PreciseClickState()

    fun conditionAt(index: Int): PreciseClickState {
        while (conditions.size <= index) conditions += PreciseClickState()
        return conditions[index]
    }
}

/**
 * 小标题那一行的**最小**高度 —— 标题与右端那颗三点都在这一行里竖居中。
 *
 *  32dp 是 [SmallTitle] 自己的下界（12sp 的字 + 上下各 8dp 内边距）；写成固定高度会把字裁掉半行。
 */
private val RULE_TITLE_HEIGHT = 32.dp

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

/**
 * 一行两列那四个「点哪个 / 点了做什么」 —— 合成一个对象只为把参数个数压回 detekt 的上限。
 */
private class RuleCells(
    val anchors: RuleAnchors,
    /** 点了**第几条**条件那一行（一块可以带好几条，各占一行）。 */
    val onCondition: (Int) -> Unit,
    val onAction: () -> Unit,
    val onMenu: () -> Unit,
)

/** 一条规则那两行「写什么、配哪颗字形」—— 合成一个对象，[RuleBlock] 的参数才压得回 5 个以内。 */
private class RuleText(
    /**
     * 条件那几行 —— **一条条件一个**，`CardList` 里一条一个 `CardRow.Item`。
     *
     *  空表（新加的那一行还没条件）时给一条「未选择条件」，让用户知道这一格要配什么。
     */
    val conditionLines: List<ConditionLine>,
    val action: String,
    /** 命令那一格还没选 —— 文字与图标压禁用色。 */
    val actionEmpty: Boolean,
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
    /** 行尾那半句话说什么（见 [RuleState]）—— 「不会执行」/「已停用」/ 什么都不说。 */
    state: RuleState,
    text: RuleText,
    cells: RuleCells,
    /** 条件行的进出场要读它（刚插入的那条 / 正在退场的那条）。 */
    menus: BranchMenuState,
) {
    //  **刚插入的那一条条件**：先以不可见组合、下一帧再显 —— `AnimatedVisibility` 首帧就可见
    //  的话不播进场（与 `BranchRow` 那个 `MutableTransitionState(!spec.fresh)` 同一个道理）。
    val freshIndex = menus.freshCond?.takeIf { it.at == index }?.index
    var freshShown by remember(freshIndex) { mutableStateOf(freshIndex == null) }
    LaunchedEffect(freshIndex) {
        if (freshIndex == null) return@LaunchedEffect
        withFrameNanos { }
        freshShown = true
        //  **播过就把这笔账清掉**（与上面 `freshId` 同一条教训）：不清的话页面一重建
        //  （去「选择应用」页挑完动作返回、来源页的组合已经注销），这一行又按「刚插入」
        //  收起来再展开一次 —— 用户看到的就是「选完命令后还要再播一遍动画」。
        menus.freshCond = null
    }
    Column(Modifier.fillMaxWidth()) {
        RuleTitleRow(index, cells)
        //  一行条件、一行命令，**同一张卡**（与各页的 listview 同一个壳：Card + 行间细线 +
        //  首尾圆角），不再拆成两个胶囊 —— 拆开时中间那道缝读起来像两件不相干的事。
        //  两行各自可点，行尾那枚选择器说明「点开还有一层」（换条件 / 换动作）。
        CardList(
            margin = 0.dp,
            rows =
                buildList {
                    //  **一条条件一个 `CardItem`**：一块可以带好几条条件（逐条都要成立），
                    //  每条各占卡片里的一行 —— 点哪一行就在**那一行**旁边弹条件菜单。
                    //  不是一个 Item 里画多行字：那样每行没有自己的触点，点哪儿都只能改第一条。
                    text.conditionLines.forEachIndexed { condIndex, line ->
                        val disabledTint = COUITheme.colorScheme.disabledOnSecondaryVariant
                        add(
                            CardRow.Item(
                                title = line.text,
                                icon = line.icon,
                                //  没配完的那一条：图标与文字**都压到禁用色**（同一档），不加别的记号 ——
                                //  这一行本身仍可点，靠文案「未选择条件」说清缺什么就够了。
                                iconTint = if (line.empty) disabledTint else null,
                                titleTint = if (line.empty) disabledTint else null,
                                //  行尾一律画那枚「点开还有一层」的选择器（每行都可点、都还有下一层）；
                                //  「不会执行」/「已停用」那半句只挂在**最后一条**条件行 ——
                                //  那说的是「这一行整体」不生效，不是某一条条件的问题。
                                trailing = {
                                    val last = condIndex == text.conditionLines.lastIndex
                                    ConditionTrailing(
                                        dead = state == RuleState.DEAD && last,
                                        off = state == RuleState.OFF && last,
                                    )
                                },
                                modifier = Modifier.preciseClickAnchor(cells.anchors.conditionAt(condIndex)),
                                onClick = { cells.onCondition(condIndex) },
                                //  **进出场走通用动画**（`CardRow` 的 `visible`，与卡里其它行同一套：
                                //  连它上面那条细线一起收起 / 展开）：
                                //   · 正在退场的那一条 ⇒ 收起，播完由上面那个 effect 摘掉数据；
                                //   · 刚插入的那一条 ⇒ 首帧不可见、下一帧显出来；
                                //   · 其余 ⇒ **`true`**（一直走同一条分支，理由见下）。
                                //
                                //  **别给 `null`**：`null` 与 `true` 是 `CardList` 里两条不同的分支
                                //  （直接画 / 套一层 `AnimatedVisibility`），从一条切到另一条会把
                                //  这一行整棵子树重建一次 —— 真机表现是「刚插入的条件行播完、闪一下」。
                                //  代价只是多一层常显的 `AnimatedVisibility`，一块顶多几条条件。
                                visible =
                                    when {
                                        menus.exitingCond == CondRef(index, condIndex) -> false
                                        condIndex == freshIndex -> freshShown
                                        else -> true
                                    },
                            ),
                        )
                    }
                    add(
                        CardRow.Item(
                            title = text.action,
                            icon = text.actionIcon,
                            iconTint = if (text.actionEmpty) COUITheme.colorScheme.disabledOnSecondaryVariant else null,
                            titleTint = if (text.actionEmpty) COUITheme.colorScheme.disabledOnSecondaryVariant else null,
                            trailing = { RowEndIcon(PageIcons.Selector) },
                            modifier = Modifier.preciseClickAnchor(cells.anchors.right),
                            onClick = cells.onAction,
                        ),
                    )
                },
        )
    }
}
