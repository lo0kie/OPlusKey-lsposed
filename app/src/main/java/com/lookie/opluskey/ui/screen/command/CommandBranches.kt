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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.feedback.DisabledStateText
import com.lookie.opluskey.ui.feedback.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.icon.conditionIcon
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.text.conditionKindLabel
import com.lookie.opluskey.ui.model.text.conditionRowTitle
import com.lookie.opluskey.ui.model.text.gestureLabel
import com.lookie.opluskey.ui.model.text.stepTitleWithTarget
import com.lookie.opluskey.ui.model.theme.STEP_ROW_ENTER
import com.lookie.opluskey.ui.model.theme.STEP_ROW_EXIT
import com.lookie.opluskey.ui.model.theme.STEP_ROW_EXIT_MS
import com.lookie.opluskey.ui.model.theme.VISIBILITY_EXIT_MS
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.ChipFlow
import com.lookie.opluskey.ui.row.ChipLook
import com.lookie.opluskey.ui.row.RowEndIcon
import com.lookie.opluskey.ui.row.SECTION_TITLE_TRAILING_GLYPH_INSET
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.SectionTitleTrailingMore
import com.lookie.opluskey.ui.row.chipStyle
import com.lookie.opluskey.ui.screen.pageWritable
import com.lookie.opluskey.ui.state.binding.BranchDraft
import com.lookie.opluskey.ui.state.binding.NO_FRESH_ROW
import com.lookie.opluskey.ui.style.VisibleAreaCentered
import com.lookie.opluskey.ui.wire.appNameOf
import com.lookie.opluskey.ui.wire.appTargetLabelOf
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

// 「条件执行」那一页的编辑区 —— 从 `CommandPage.kt` 拆出来：那边是「一页的骨架」，
// 这一块是分叉特有的那张卡；四张菜单在 `CommandBranchMenus.kt`。

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
    /** **这一页**那一份草稿（三页同时在场，各编各的表，见 `BranchScreen`）。 */
    draft: BranchDraft,
    tick: () -> Unit,
    emptyHeight: Dp,
) {
    //  **草稿载入之前整块不画**：第一帧 `rules` 必为空（草稿在 `LaunchedEffect` 里才灌进来），
    //  于是先画空态、载入后再换成表格 —— 那一换会把整块内容播一遍转场，
    //  表现就是「刚进页，内容从顶部滑下来」。载入完成后再画，第一帧就是最终形态，
    //  整块没有任何转场。这一档只持续一帧（`load` 在同一次组合的第一帧内跑完）。
    if (draft.owner == null) return
    val rules = draft.scenes
    //  退出中的那一支还要画行与注脚，而这时 `rules` 已经空了（删最后一条是立即移除）——
    //  直接读它会让行瞬间消失、注脚弹回顶部。所以留一份「最后一次非空」的快照，退出那一支用它画。
    //  比较用结构相等：内容没变就不写，不会自己触发重组。
    //
    //  **必须在 `anchors` 之前**：锚点表要按快照的条数建，否则退出那一支会拿到一张空表、
    //  按行下标去取就是 `IndexOutOfBoundsException`（真机崩过一次）。
    val shownRules = remember { mutableStateListOf<SceneRule>() }
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
    //  加的是**第一条**时不播行进场：那一下整块高度由外层（空态 与 表格）那套动画负责，
    //  里层再展开一遍就是同一条动画跑两遍 —— 用户口径「这样两遍动画太拖了」。
    //  第二条起外层不再切换（`isEmpty` 一直是 false），行照常播自己的进场。
    // 删除**先播收缩、再动数据**（见 `BranchMenuState.deletingAt`）：数据晚一步，行才不会瞬间消失。
    //  收尾再核一次「要删的还是不是它」—— 收缩没播完又删了另一条时，这一条留着（宁可不删，不能错删）。
    //
    //  **删的是最后一条时不等**：那一下整块要变成空态，而空态由外层（空态 与 表格）那套动画接管；
    //  要是还照「先收行 220ms + 结算 40ms、再展开空态 220ms」，就是两段串在一起。
    //  直接移除，行不单独收缩、只跟着外层一起淡出。
    BranchDeleteEffect(draft = draft, menus = menus)

    //  删**一条条件**的收尾 —— 与上面删整行同一套，单独成函数只为让本函数留在 `LongMethod` 内。
    BranchConditionDeleteEffect(draft = draft, menus = menus)
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
            //  拖动时跟着弹。居中盒走全项目那一份（[VisibleAreaCentered]）。
            VisibleAreaCentered(emptyHeight) { BranchEmptyHint() }
        } else {
            Column(Modifier.fillMaxWidth()) {
                //  「松手沿用按下时的场景」—— **整张表**一格开关，摆在列表**最上面**：
                //  它修饰的是「按下与松手怎么接」，不是某一块的动作，所以不进任何一块的卡，
                //  也不跟在列表后面（那要滚到底才看得见，读起来还像「第 N+1 块」）。
                //  只在表非空时出现（空态那一支压根不画这一层）。
                //
                //  与顶栏的间距由这张卡自己让（`SECTION_TITLE_PAD_TOP`，与「按键绑定」页那张
                //  「系统场景」条同一个数）；下面第一块的小标题自带 16dp 上间距，这一行与它
                //  之间不再补距离 —— 与块与块之间那一档正好一致，这一行读成「这张表的一行表头」。
                SceneReleasePinCard(
                    rules = shownRules,
                    enabled = pageWritable(),
                    onToggle = { on -> setSceneReleasePin(draft, on) },
                )
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
    BranchMenus(draft = draft, rules = rules, menus = menus, tick = tick)
    BranchDeleteDialog(menus)
    //  停用 / 启用一条场景 —— 与删除同一条路（菜单里点、窗里确认），但改的是这一条自己的开合位
    BranchToggleDialog(draft, menus)
    //  删「一条条件」是另一件事（只摘一块里的一条，不连动作、不动下面的行），窗也另开一个
    BranchConditionDeleteDialog(menus)
}

/**
 * 空态 与 表格切换时**淡入淡出**那一段 —— 两边各淡各的（空态是页面浮层，见 `BranchEmptyOverlay`；
 *  这里是行那一层），时长取同一档，两处一起交代完。
 */
private const val BRANCH_BODY_MS = 150

/**
 * 空态 与 表格那一层的内容转场：**只做交叉淡入淡出**，不做尺寸插值。
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
 * 「添加场景」那一下 —— 末尾那一块**条件还空着**时不再往下加：新加的一样是空的，
 * 堆两块空场景既看不出差别、校验也过不了。说一句就停在这一块上，让人先去把它填了。
 *
 * 加的是**第一块**时不播行进场：那一下整块高度由外层（空态 与 表格）那套动画负责，
 * 里层再展开一遍就是同一条动画跑两遍（见 [NO_FRESH_ROW]）。
 */
internal fun SettingsPage.addScene() {
    val rules = branchDraftNow().scenes
    val onAdded: (Int) -> Unit = { branchDraftNow().freshId = it }
    //  **上限先判**：表满了就不该再听「还有场景没选完」那句 —— 那一句是「你可以再加，但先把这块填了」，
    //  而满了的时候压根加不了，两件事说反了会让人以为填完还能加。
    if (rules.size >= SceneRule.MAX_SCENES) {
        hint(
            I18n.format(Strings.COMMAND_BRANCH_LIMIT, SceneRule.MAX_SCENES),
            slot = HINT_SLOT_DISABLED,
        )
        return
    }
    //  **已经有一块空着就不再加**（用户口径「已有空时不添加」）：任何一块还没选条件都算，
    //  不只看表尾 —— 从别处导入来的表里，空块可能夹在中间，那种表再加一块就变成两个空的。
    //  **同一个语义位**：连点几下是这一句在改字，不是叠出一串
    if (rules.any { it.conditions.isEmpty() }) {
        hint(
            I18n.get(Strings.COMMAND_BRANCH_NEED_CONDITION, "还有场景没选完"),
            slot = HINT_SLOT_DISABLED,
        )
        return
    }
    val first = rules.isEmpty()
    //  新块**沿用这张表此刻的取值**：`SceneRule.EMPTY` 自带「沿用」（那一项的默认值就是开），
    //  而表被拨成关之后再加一块，就会冒出一块开着的 —— 列表那一行读「有任一块开着就算开」，
    //  于是它当场自己跳回开，看着像「加一块就把设置改了」。
    val id = branchDraftNow().add(SceneRule.EMPTY.copy(releasePin = sceneReleasePinOn(rules)))
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
    rules: List<SceneRule>,
    ids: List<Int>,
    freshId: Int,
    menus: BranchMenuState,
    anchors: List<RuleAnchors>,
) {
    //  这一页的行能不能点：**只看整页那一道闸**（主开关 × 链路 × 只读）。这一颗键 / 这一档组合
    //  自己的 `enabled` **不参与**：停用的那一份照样要能把场景配好、再回去启用，拿它去灰掉整张表
    //  等于先把这条顺序堵死（与「独立设置」页同一条口径）。
    val rowsEnabled = pageWritable()
    //  **整块重复**（条件与手势都被上面某一块盖住）先算出来：行尾要标一句「与上面重复」，
    //  点「确认」时也要拦（见 `branchRulesBlockedReason`）—— 两处读同一份判据。
    val duplicated = duplicateBlockIndexes(rules)
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
                            enabled = rowsEnabled,
                            //  停用优先于重复：停用的块根本不参与判定，说「重复」会指错原因
                            state =
                                when {
                                    !rule.enabled -> RuleState.OFF
                                    index in duplicated -> RuleState.DUPLICATE
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
    rule: SceneRule,
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
        if (spec.fresh) branchDraftNow().freshId = NO_FRESH_ROW
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
            //  每一档动作各占一行：**这一块完整地管它能配的每一档**（翻转的核心）。
            val actionLines = ruleActionLines(rule)
            RuleBlock(
                // 「场景 1」「场景 2」—— 一份表里第几块，标题上直接写出来
                index = spec.index,
                state = spec.state,
                text =
                    RuleText(
                        conditionLines = conditionLines,
                        conditions = rule.conditions.size,
                        actions = actionLines,
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
                        //  点的是**哪一档**动作那一行 → 进「场景动作」页编那一档（不再弹抽屉：
                        //  那一页与「按键绑定」页同一张网格 + 同一条手势带，
                        //  场景块的一档本来就该像一颗键的一档那样挑动作）。
                        onAction = { trigger ->
                            openSceneBinding(spec.index, trigger)
                        },
                        onMenu = {
                            menus.menuAnchor = spec.anchors.menu
                            menus.menuAt = spec.index
                        },
                        //  整页闸门关着 ⇒ 几行都置灰（菜单那颗仍可用，见 `RuleCells.enabled`）
                        enabled = spec.enabled,
                        //  **每一块**的场景卡末尾都跟一行「添加条件」—— 它加的是**这一块的一条条件**
                        //  （不是一整块场景，整块那个入口在顶栏），所以每块各有一份才对。
                        addRow =
                            CardRow.Item(
                                title = I18n.get(Strings.COMMAND_BRANCH_ADD_CONDITION, "添加场景"),
                                icon = PageIcons.StepAdd,
                                //  **到顶了（九条）就点不动**：与 `appendCondition` 里那道闸同一句原因
                                //  （`CardRow` 只在置灰时才画 `disabledHint`，所以这里照给就行）。
                                enabled = rule.conditions.size < SceneRule.MAX_CONDITIONS,
                                disabledHint =
                                    I18n.format(
                                        Strings.COMMAND_BRANCH_CONDITION_LIMIT,
                                        SceneRule.MAX_CONDITIONS,
                                    ),
                                onClick = { appendCondition(branchDraftNow(), spec.index, menus) },
                            ),
                    ),
                menus = menus,
            )
        }
    }
}

/**
 * 这一块每一档动作各自那一行 —— 没配的那一档写「跟随默认」并压禁用色。
 *
 *  「跟随默认」与「无操作」是两件事：前者这一档不被这块接管（继续往下找 / 回落按键绑定），
 *  后者是命中后这一档什么都不做、属于配好了的一档（见 `SceneRule` 的类注释）。
 *  **只有「跟随默认」压一档色** —— 压灰会被读成「这一档还没设」。
 *
 *  行首一律是**这一档的手势字形**（不是动作自己的字形）：三行并排时「哪一行是哪一档」得先
 *  看得出来，之后才是它右边写的那个动作（见 `gestureIcon`）。
 *
 */
private fun SettingsPage.ruleActionLines(rule: SceneRule): List<ActionLine> =
    blockGestures().map { trigger ->
        val action = rule.actionOf(trigger)
        ActionLine(
            trigger = trigger,
            text = actionTextOf(action),
            empty = action is Action.Follow,
            icon = PageIcons.gestureIcon(trigger),
        )
    }

/**
 * 小标题那一行 —— 「条件 N」+ 右端那颗「更多」。
 *
 *  「与上面重复」标在**这一行**（不是每一条条件行）：它说的是整块白写，不是某一条条件的问题，
 *  一块只说一次就够。与「已停用」标在每一行上不同 —— 那一条说的是「这一块不参与判定」，
 *  用户扫条件时每一行都想看见。
 *
 *  与各页的小标题**同一个组件**
 *  （同一档字号、颜色、行高）：「条件 N」是这一块的小标题，不是正文。横向只把库里那 32dp
 *  改成 20dp —— 默认值里含 12dp 页面边距，而这一页的外层已经有 `PAGE_MARGIN`，照抄会多缩进一格
 *  （真机口径：两者字头都落在 112px @3.5x）。
 */
@Composable
private fun RuleTitleRow(
    index: Int,
    /** 这一块整体处在哪一档（见 [RuleState]）。 */
    state: RuleState,
    /** 这一块配了几条条件 —— 行尾那个 `3/9` 读它。 */
    conditions: Int,
    cells: RuleCells,
) {
    SectionTitle(
        text = I18n.format(Strings.COMMAND_BRANCH_ITEM, index + 1),
        modifier = Modifier.heightIn(min = RULE_TITLE_HEIGHT),
        //  左右缩进比页面小一档（外层已有 `PAGE_MARGIN`）
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        //  行尾两格：那颗「更多」与一格计数 —— 顺序是**菜单在前、计数在最后**（用户口径
        //  「counter 和菜单换个位置」）；样式在 `MoreButton` / `TitleCounter` 里（场景与命令列表共用一份）。
        //
        //  内缩给的是 [SECTION_TITLE_TRAILING_GLYPH_INSET]（10dp），不是默认那 22dp：这一行本身
        //  已经在外层 `PAGE_MARGIN` 里面（行右边缘 = 页边 − 12），默认那 22dp 是按「整幅宽的一行」
        //  算的，再补一次就成了页边 − 34。
        //
        //  这 10dp 现在补的是**计数那格**：行尾最后一个元素是文字，文字没有菜单格那 10dp 的内缩，
        //  补上之后它的右边缘 = 页边 − 22，与「命令名称」「命令详情」那几行的计数落在同一条线上。
        trailingEndPadding = SECTION_TITLE_TRAILING_GLYPH_INSET,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                //  前置那一格是「已停用」那半句 —— 它是这一页自己的话，不属于「菜单 + 计数」那套几何
                RuleStateHint(state)
                //  菜单与计数的几何由 [SectionTitleTrailingMore] 定 —— 与「命令详情 / 命令步骤」
                //  两行同一份，三处不该各算一遍。
                //
                //  这一行的内缩由上面那 10dp 给到，点击格贴住内容右边缘、字形居中。
                //  别按「与字头对称」去调 —— 这一页字头在 32dp，两者本来就不对称，跟多条命令一致才是口径。
                SectionTitleTrailingMore(
                    anchor = cells.anchors.menu,
                    onMenu = cells.onMenu,
                    length = conditions,
                    max = SceneRule.MAX_CONDITIONS,
                )
            }
        },
    )
}

/**
 * 这一行现在处在哪一档 —— 行尾那半句话显示哪一句。
 *
 *  「顺序上轮不到谁」这一页不替用户算（表里怎么排就怎么执行，判定侧本来就是「从上往下第一条
 *  成立的赢」）；唯一还算的是**整块重复**：两块完全一样时后一块必输，那是配错了、不是排序问题。
 */
internal enum class RuleState {
    NORMAL,

    /** 这一条自己关着（行尾菜单里的停用）—— 弱色「已停用」。 */
    OFF,

    /** 这一块与上面某一块完全一样（见 [duplicateBlockIndexes]）—— 红字「与上面重复」。 */
    DUPLICATE,
}

/** 一行「是第几条、跟哪两格、要不要播进出场」—— 这六样合起来就是「这一行长什么样」，摊平只会让 [BranchRow] 与它下面两层各抄一遍。 */
private class RuleRowSpec(
    val index: Int,
    val anchors: RuleAnchors,
    val fresh: Boolean,
    val exiting: Boolean,
    /** 行尾那半句话说什么（见 [RuleState]）。 */
    val state: RuleState,
    /** 这一块两行能不能点（整页闸门，见 `RuleCells.enabled`）。 */
    val enabled: Boolean = true,
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

/**
 * 一行两列那四个「点哪个 / 点了做什么」，外加那一行末尾的「添加场景」。
 *
 *  它们只在 [RuleBlock] 里组一次、往下传给条件格 / 动作格 / 行尾三点，摊平就是十几个行参。
 */
private class RuleCells(
    val anchors: RuleAnchors,
    /** 点了**第几条**条件那一行（一块可以带好几条，各占一行）。 */
    val onCondition: (Int) -> Unit,
    /** 点了**哪一档**动作那一行（每档各占一行）。 */
    val onAction: (GestureTrigger) -> Unit,
    val onMenu: () -> Unit,
    /**
     * 这一块的两行**能不能点** —— 只看整页那道闸（主开关 × 链路 × 只读）。
     *
     *  这一颗键 / 这一档组合自己的 `enabled` 不参与：停用的那一份照样要能先把场景配好、
     *  再回去启用（与「独立设置」页同一条口径）。菜单那一颗始终可用，否则连「删掉这条场景」
     *  都做不了。
     */
    val enabled: Boolean = true,
    /**
     * 并进「场景」那张卡末尾的一行 —— 表尾那个「添加场景」（`appendCondition`）。
     *
     *  给的是**一行 `CardRow`**、不是一张新卡：它与上面那几条条件同属「场景列表」，
     *  分成两张卡会读成「又多了一块场景」。
     *
     *  寄在这里而不是 [RuleBlock] 的参数：那一格本来也是「这一块里可点的一处」，与上面那几颗同类，
     *  放进 [RuleBlock] 的行参等于把「可点的东西」拆成两处。
     */
    val addRow: CardRow? = null,
)

/** 一条规则那两行「写什么、配哪颗字形」—— [RuleBlock] 只管怎么排，写什么由这一份给。 */
private class RuleText(
    /**
     * 条件那几行 —— **一条条件一个**，`CardList` 里一条一个 `CardRow.Item`。
     *
     *  空表（新加的那一块还没条件）时给一条「未选择条件」，让用户知道这一格要配什么。
     */
    val conditionLines: List<ConditionLine>,
    /**
     * 这一块**真的**配了几条条件 —— 小标题行尾那个 `3/9` 读它（[SceneRule.MAX_CONDITIONS] 是上限）。
     *
     *  不能拿 [conditionLines] 的条数顶：一条都没配时那张表里也有一行「未选择条件」占位，
     *  按行数会读成「已经配了一条」。
     */
    val conditions: Int,
    /** 动作那几行 —— **一档一个**（单击 / 双击 / 长按，组合键只有两档）。 */
    val actions: List<ActionLine>,
)

/**
 * 一档动作那一行 —— 哪一档、写什么、配哪颗字形、是不是还没配。
 *
 *  没配（[empty]）与「无操作」是两件事：前者这一档不被这一块接管，后者是命中后什么都不做
 *  （见 `SceneRule` 的类注释）。两档的文字与配色因此也不同（[empty] 压禁用色）。
 */
internal class ActionLine(
    val trigger: GestureTrigger,
    val text: String,
    val empty: Boolean,
    val icon: ImageVector,
)

/**
 * 一条规则那一块 —— **小标题 + 两张卡**：上面一张是条件、下面一张是命令。
 *
 *  为什么不再左右并排：条件与命令都是长句（「微信 在前台」/「以小窗口打开 微信」），
 *  一行里各占一半时两边都在打省略号，看不出选的是哪一条；竖下来各自整行，读起来才是
 *  「这一条什么时候、做什么」。小标题写「条件 N」，删掉一条之后序号也跟着重排。
 *
 *  为什么拆成两张卡：条件回答「什么情况下」、命令回答「做什么」，是同一个场景的两个问题。
 *  合成一张、中间加一条加深带时，读起来还是「一件事的两段」；拆开之后两问各自成块
 *  （用户口径「场景和动作分隔开，用两个卡片」）。
 *
 *  两格各自可点（上面换条件、下面换动作），所以是两个独立的触点，各挂一份
 *  [preciseClickAnchor]：菜单弹在**被点的那一格**旁边，而不是整块。
 *
 *  这一条自己的菜单（上移 / 下移 / 删除）挪到小标题那一行右端：两张卡都通栏之后，
 *  中间没有位置再放那颗三点了；挂在标题行也不会和两格的点击区抢。
 *
 *  @param text 两张卡写什么、各配哪颗字形（见 [RuleText]）
 *  @param cells 这一块里可点的几处：两格、那颗「更多」，以及并进场景卡末尾的那行「添加场景」
 *    （见 [RuleCells]）
 */
@Composable
private fun RuleBlock(
    index: Int,
    /** 这一块整体处在哪一档（见 [RuleState]）—— 「与上面重复」/「已停用」/ 什么都不说。 */
    state: RuleState,
    text: RuleText,
    cells: RuleCells,
    /** 条件行的进出场要读它（刚插入的那条 / 正在退场的那条）。 */
    menus: BranchMenuState,
) {
    val fresh = rememberFreshRow(menus, index)
    Column(Modifier.fillMaxWidth()) {
        RuleTitleRow(index, state, text.conditions, cells)
        //  场景与动作**各一张卡**（用户口径「场景和动作分隔开，用两个卡片」）：
        //  上面回答「什么情况下」，下面回答「做什么」。原先合成一张、靠一条加深带断开，
        //  读起来仍是同一件事的两段；拆成两张之后「这条场景命中会做什么」一眼分成两问。
        //  两张卡各自可点，行尾那枚选择器说明「点开还有一层」（换条件 / 换动作）。
        CardList(
            margin = 0.dp,
            rows =
                buildList {
                    //  **一条条件一个 `CardItem`**：一块可以带好几条条件（同种类之间「或」、不同种类之间「且」），
                    //  每条各占卡片里的一行 —— 点哪一行就在**那一行**旁边弹条件菜单。
                    //  不是一个 Item 里画多行字：那样每行没有自己的触点，点哪儿都只能改第一条。
                    text.conditionLines.forEachIndexed { condIndex, line ->
                        val disabledTint = COUITheme.colorScheme.disabledOnSecondaryVariant
                        add(
                            CardRow.Item(
                                title = line.text,
                                //  这一行的进出场状态按**这条条件**认，不按位次 —— 中间插一行时
                                //  它才不会接手上一行那份「一直在场」的状态（见 `CardRow.Item.rowKey`）。
                                rowKey = line.rowKey,
                                //  「前台应用」那几行**按框画**应用名（与组合键成员同一个标签，见
                                //  `ConditionRowTitle`）；其余条件仍是一行字。给这一块时行不再画
                                //  `title`，但 `title` 仍留着当无障碍与兜底。
                                titleContent =
                                    if (line.apps.isNullOrEmpty()) {
                                        null
                                    } else {
                                        { ConditionRowTitle(line, cells.enabled) }
                                    },
                                icon = line.icon,
                                //  整页只读 / 闸门关着时这一行点不动（联动见 `RuleCells.enabled`）
                                enabled = cells.enabled,
                                //  没配完的那一条：图标与文字**都压到禁用色**（同一档），不加别的记号 ——
                                //  这一行本身仍可点，靠文案「未选择条件」说清缺什么就够了。
                                iconTint = if (line.empty) disabledTint else null,
                                titleTint = if (line.empty) disabledTint else null,
                                //  行尾一律画那枚「点开还有一层」的选择器（每行都可点、都还有下一层）；
                                //  「已停用」那半句只挂在**最后一条**条件行 ——
                                //  那说的是「这一行整体」不生效，不是某一条条件的问题。
                                trailing = {
                                    ConditionTrailing(
                                        //  「已停用」**每一行都说**（用户口径：停用要标在列表的每一行上）：
                                        //  一块有几条条件就几行都带，不只是最后一行。与动作那一行同一档。
                                        off = state == RuleState.OFF,
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
                                        condIndex == fresh.index -> fresh.shown
                                        else -> true
                                    },
                            ),
                        )
                    }
                    //  表尾那行「添加场景」**并进同一张卡**（用户口径：不要单独按钮、就用 `CardRow`）——
                    //  它与上面几条条件同属「场景列表」，另起一张卡会读成「又多了一块场景」。
                    cells.addRow?.let { add(it) }
                },
        )

        Spacer(Modifier.height(RULE_CARD_GAP))
        RuleActionRows(text, state, cells)
    }
}

/**
 * 动作那几行（**单独一张卡**）—— 一档一行：单击 / 双击 / 长按，组合键只有两档。
 *
 *  翻转之后「一块 = 它能配的每一档」就落在这里：一块场景命中时，这几行各自决定那一档做什么。
 *  没配的那一档写「跟随默认」并压禁用色 —— 它与「无操作」是两件事（见 [ActionLine]）。
 *
 *  这一块自己关着时，行尾先说一句「已停用」：条件行由 [ConditionTrailing] 说，动作这几行在这里补，
 *  用户扫「未设置 / 无操作 / 打开应用…」时也能看出整块已经关掉了。小标题那一行**不说**
 *  （用户口径：标在列表的每一行上）。
 *
 *  **行数与档位名都不写死在这里**：一张卡几行由 `text.actions` 给（源头是 `sceneGesturePages()`），
 *  名字走 `gestureLabel` —— 那两支就是接自定义手势的地方，这一层不必动。
 */
@Composable
private fun RuleActionRows(
    text: RuleText,
    state: RuleState,
    cells: RuleCells,
) {
    val disabled = COUITheme.colorScheme.disabledOnSecondaryVariant
    CardList(
        margin = 0.dp,
        rows =
            text.actions.map { line ->
                CardRow.Item(
                    //  标题前面写档位：「单击 | 手电筒」。三行并排时，光有动作名看不出哪一行是哪一档。
                    //  **档位名走 `gestureLabel`（字典）**，不读 `Gesture.label` —— 那串是写死的中文，
                    //  英文界面下这里会冒出「单击」（真机口径）。那一支也是将来自定义手势的接口。
                    title = "${gestureLabel(line.trigger)} | ${line.text}",
                    //  动作那一行按行内统一的行数上限折行（[ROW_TEXT_MAX_LINES]，3 行 + 省略），
                    //  与整页其余行同一个口径。
                    //  整页只读 / 闸门关着时点不动（与条件行同一道闸，见 `RuleCells.enabled`）
                    enabled = cells.enabled,
                    icon = line.icon,
                    iconTint = if (line.empty) disabled else null,
                    titleTint = if (line.empty) disabled else null,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state == RuleState.OFF) RuleStateHint(RuleState.OFF)
                            //  这一行是**换页**（进「场景动作」编那一档），行尾给「进下一层」那枚右箭头；
                            //  选择器那枚说的是「点了要选点什么」，与这里的去处对不上。
                            RowEndIcon()
                        }
                    },
                    //  这一行是**换页**（进「场景动作」编那一档），不是弹浮层 ——
                    //  所以不挂触点锚点：锚点只服务「菜单弹在被点的那一格旁边」。
                    onClick = { cells.onAction(line.trigger) },
                )
            },
    )
}

/** 场景卡与动作卡之间的间距 —— 与「块与块之间」同一档（小标题自带的上间距是 16dp，这里取更紧的一档）。 */
private val RULE_CARD_GAP = 8.dp

/**
 * 一块里「刚插入的那条条件」的进场开关 —— 首帧不可见、下一帧再显，播完把那笔账清掉。
 *
 *  `AnimatedVisibility` 首帧就可见的话不播进场（与 `BranchRow` 那个
 *  `MutableTransitionState(!spec.fresh)` 同一个道理）。
 *
 *  抽出来：这一支只管一条条件行的进场时机，与那张卡片怎么排毫无关系。
 *
 * @param index 这是第几块（与 `BranchMenuState.freshCond` 里记的那一格对位）
 */
@Composable
private fun rememberFreshRow(
    menus: BranchMenuState,
    index: Int,
): FreshRow {
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
    return FreshRow(freshIndex, freshShown)
}

/**
 * [rememberFreshRow] 的两个答案：刚插入的是哪条条件（`null` = 没有）、它现在显不显示。
 *
 *  两个值一起返回而不是两次调用：它们出自同一笔账（`freshCond` + 那个 `LaunchedEffect`），
 *  分两处各记一次就会一边清空、另一边还在按旧值画。
 */
private class FreshRow(
    val index: Int?,
    val shown: Boolean,
)

// 条件执行行里的那几件小件 —— 条件行写什么、行尾那句「已停用」的注脚、以及「点开还有一层」
// 那枚选择器。整块卡片怎么排在上面，行内文案与行尾记号在这里。

/**
 * 条件那几行写什么 —— 一条条件一行，没配完的那一条 `empty = true`（压禁用色）。
 *
 */
internal fun SettingsPage.ruleConditionLines(rule: SceneRule): List<ConditionLine> =
    if (rule.conditions.isEmpty()) {
        //  一条都没配时也给一行「未选择条件」，让用户知道这一格要配什么
        listOf(
            ConditionLine(
                I18n.get(Strings.COMMAND_BRANCH_PICK_CONDITION, "未选择场景"),
                PageIcons.Scene,
                empty = true,
                //  占位行与「配好之后的那一条」**共用同一个身份**（见下面 `single` 那一支）：
                //  这一下不是插入，而是同一行换内容。
                rowKey = CONDITION_PLACEHOLDER_KEY,
            ),
        )
    } else {
        //  只剩一条时，这一行**就是**原来那个占位行 —— 位置、高度都没变，变的只有内容
        //  （「未选择场景」→「亮屏中」）。给它同一个身份，行状态与子组合都接着用：
        //  那一下才不会「先把高度塌掉、再把新行滑进来」，而是原地淡换内容。
        //  第二条起才是真的往表里插一行，用条件自己当身份（见 `CardRow.Item.rowKey`）。
        val single = rule.conditions.size == 1
        //  同一个取值可能出现两遍（界面里二级会置灰，但手改 / 导入来的配置不管这一道）：
        //  行身份带上「第几次出现」，否则两行的 `rowKey` 相同，`CardList` 会把它们当成同一行。
        val seen = HashMap<BranchCondition, Int>()
        rule.conditions.map { condition ->
            val nth = seen[condition] ?: 0
            seen[condition] = nth + 1
            ConditionLine(
                //  插进来的空条件 / 只选了种类没选取值：这一行写「未选择条件」并压禁用色
                text =
                    if (condition.isUsable) {
                        conditionRowTitle(condition, ::appNameOf)
                    } else {
                        I18n.get(Strings.COMMAND_BRANCH_PICK_CONDITION, "未选择场景")
                    },
                icon = conditionLineIcon(condition),
                empty = !condition.isUsable,
                //  应用那一族（前台 / 前台以外）都能挑好几个，名字按**框**排（与组合键成员同一种标签，
                //  见 [ConditionRowTitle]）—— 给 `null` 的那几档仍是一行字。
                apps =
                    if (condition.kind?.isAppFamily == true && condition.isUsable) {
                        condition.appPackages.map(::appNameOf)
                    } else {
                        null
                    },
                //  框前面那个种类名要念对档：`前台应用 - [微信]` / `前台应用以外 - [微信]`
                kind = condition.kind,
                //  这一行在列表里的身份：行进出场按它认「是不是同一行」，不按位次
                //  （见 `CardRow.Item.rowKey`）。
                rowKey = if (single) CONDITION_PLACEHOLDER_KEY else (condition to nth),
            )
        }
    }

/** 占位行「未选择场景」的身份 —— 与「这一块唯一那条条件」共用（见 [ruleConditionLines]）。 */
private val CONDITION_PLACEHOLDER_KEY = Any()

/**
 * 条件那一行的行首字形 —— 取**父图标**：这一条属于哪一类条件就给哪一类的字形
 * （[conditionIcon]：插头 / 路由器 / 耳机 / 一台手机…），不再按**取值**换（子图标已去掉）。
 *
 *  以前是「跟着取值给」：亮屏与锁屏、WiFi 开与关各一颗，一列条件扫下来全是只差一道斜杠的图，
 *  读起来比文字还费劲。现在一列里每行一眼分得出「是充电还是 WiFi」，具体哪个取值由文字说。
 *
 *  还没选种类的那一条（刚插进来的空条件）给父级那颗 [PageIcons.Scene]。
 */
private fun conditionLineIcon(condition: BranchCondition): ImageVector = condition.kind?.let(::conditionIcon) ?: PageIcons.Scene

/** 条件那**一行**写什么、配哪颗字形、是不是还没配完 —— 一条条件一个，卡片里各占一行。 */
internal class ConditionLine(
    val text: String,
    val icon: ImageVector,
    /** 这一条还没配完（刚插进来的空条件 / 只选了种类没选取值）⇒ 图标与文字压禁用色。 */
    val empty: Boolean,
    /**
     * 「前台应用」那一档的应用名 —— 有值时这一行的标题**按框画**（与组合键成员同一种标签），
     * `null` = 走普通的整行文字（[text]）。
     *
     *  为什么单独给一份而不是让界面去拆 [text]：拆字符串要认「种类 - 取值」那个分隔符，
     *  而应用名里完全可能带 ` - `（`微信 - LauncherUI` 那种写法就在别处出现过）。
     */
    val apps: List<String>? = null,
    /**
     * 这一条条件的**种类** —— 只有应用那一族读它（[ConditionRowTitle] 里框前面那个种类名：
     *  `前台应用 - [微信]` 与 `前台应用以外 - [微信]` 不能念成同一句）。
     *
     *  占位行（「未选择场景」）给 `null`：它没有种类。
     */
    val kind: ConditionKind? = null,
    /**
     * 这一行在卡片里的身份 —— 那条条件 + 它是同一取值的第几次出现（占位行 / 「未选择条件」那一行给 `null`）。
     *
     *  行进场动画要靠它认「这是新插进来的一行，还是刚才就在这里的那一行」：
     *  `CardList` 的进出场状态按位次记，新插一行会落到原来那一行的槽上（见 `CardRow.Item.rowKey`）。
     *  带上出现次序是因为同一个取值能写两遍（同一种类可以加好几条，见 `blockKindsOf`）。
     */
    val rowKey: Any? = null,
)

/**
 * 条件那一行的行尾：先标一句状态，再接那枚选择器。
 *
 *  [off]「已停用」（弱色）—— 它自己关着（行尾「更多」菜单里的停用，`SceneRule.enabled`）。
 *
 *  为什么标在**这一行**而不是小标题：小标题是库组件 `SmallTitle`，只能给一整串字，染不了一半；
 *  而这一行本来就是「这一条什么时候执行」，写在这儿读起来最顺。
 */
@Composable
internal fun ConditionTrailing(off: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (off) RuleStateHint(RuleState.OFF)
        RowEndIcon(PageIcons.Selector)
    }
}

/**
 * 行尾那半句话 —— 「已停用」（弱色）/「与上面重复」（红字）/ 什么都不画。
 *
 *  **只有这一份**：条件行与场景小标题行都显示同一种状态（用户口径「场景停用时行尾也要说」），
 *  两处各写一遍的话，字号 / 颜色 / 与右侧图标的间距迟早会各走各的。
 */
@Composable
internal fun RuleStateHint(state: RuleState) {
    when (state) {
        RuleState.NORMAL -> {
            //  正常那一档什么都不画
        }

        RuleState.OFF -> {
            //  与按键列表行尾那半句同一份（见 `DisabledStateText`）
            DisabledStateText()
        }

        RuleState.DUPLICATE -> {
            //  红色：这不是「暂时不生效」，是**配错了** —— 后一块一个字都不会执行，
            //  点「确认」时也会被拦下来（见 `branchRulesBlockedReason`）
            Text(
                I18n.get(Strings.COMMAND_BRANCH_SAME_SCENE, "与上面重复"),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.error,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

/**
 * 删除一条规则的收尾 —— **先播收缩、再动数据**（见 [BranchMenuState.deletingAt]）。
 *
 *  抽成一支；判据见那边原来那段注释：
 *  收尾再核一次「要删的还是不是它」（这 260ms 里用户还能上移 / 下移 / 再删一条，下标会移位），
 *  宁可不删、不能错删；删的是最后一条时不等 —— 那一下整块要变空态，由外层
 *  （空态 与 表格）那套动画接管，两段串起来就太拖了。
 */
@Composable
internal fun BranchDeleteEffect(
    draft: BranchDraft,
    menus: BranchMenuState,
) {
    LaunchedEffect(menus.deletingAt) {
        val at = menus.deletingAt
        if (at < 0) return@LaunchedEffect
        //  记下「要删的是哪一条」而不是只记下标：这 260ms 里下标会移位，按 at 去删就会删错行。
        //  按**规则本身**找回来，挪过也能删对；找不到（已经被改掉了）就什么都不删。
        val target =
            draft.scenes.getOrNull(at) ?: run {
                menus.deletingAt = NO_EXITING_ROW
                return@LaunchedEffect
            }
        if (draft.scenes.size > 1) delay(STEP_ROW_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.deletingAt == at) {
            val now = draft.scenes.indexOf(target)
            if (now >= 0) draft.remove(now)
            menus.deletingAt = NO_EXITING_ROW
        }
    }
}

/**
 * 删除**一条条件**的收尾 —— 与 [BranchDeleteEffect] 同一套：先让那一行收起
 * （`exitingCond` 驱动行上的 `visible`），播完再从草稿里摘掉。
 *
 *  **不能按「确认那一刻的下标」删**：这几百毫秒里菜单虽然关了，行上的「更多」仍然可点
 *  （上移 / 下移 / 删另一条），下标会漂移 —— 按旧下标删就是删掉了另一条条件。
 *  所以确认那一刻先把**那一条条件本身**记下来，播完之后按身份重新定位；找不到、或者那一位
 *  已经不是它了就不删（宁可不删，不能错删）。
 */
@Composable
internal fun SettingsPage.BranchConditionDeleteEffect(
    draft: BranchDraft,
    menus: BranchMenuState,
) {
    LaunchedEffect(menus.exitingCond) {
        val target = menus.exitingCond ?: return@LaunchedEffect
        val cond =
            draft.scenes
                .getOrNull(target.at)
                ?.conditions
                ?.getOrNull(target.index) ?: run {
                menus.exitingCond = null
                return@LaunchedEffect
            }
        delay(VISIBILITY_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.exitingCond != target) return@LaunchedEffect
        val now = draft.scenes.getOrNull(target.at)?.conditions ?: emptyList()
        val at = now.indexOf(cond).takeIf { it >= 0 } ?: target.index
        if (now.getOrNull(at) != cond) {
            XLog.w("删除条件：那一行在动画期间被改过（位置已变），这一条不删")
        } else {
            deleteBranchCondition(draft, target.at, at)
        }
        menus.exitingCond = null
    }
}

/**
 * 行尾「更多」菜单里「停用 / 启用这一条」那一行 —— 与「按键绑定」页那一行同一个语义：
 * 写的是这一条自己的 `SceneRule.enabled`。放在这个文件而不是菜单那边。
 *
 *  **停用要确认、启用不要**（三处同一条规矩）：停用之后这一条按下去不再命中，而它下面就是
 *  「删除」；启用只是把它开回来，没有可失去的东西。两种都只改**草稿**，
 *  等右下角那颗「确认」才落盘。
 */
internal fun SettingsPage.branchToggleRow(
    rules: List<SceneRule>,
    at: Int,
    menus: BranchMenuState,
): MenuEntry.Row {
    val off = rules.getOrNull(at)?.enabled == false
    return MenuEntry.Row(
        title =
            if (off) {
                I18n.get(Strings.COMMAND_BRANCH_ENABLE, "启用场景")
            } else {
                I18n.get(Strings.COMMAND_BRANCH_DISABLE, "停用场景")
            },
        icon = if (off) PageIcons.KeyUnlocked else PageIcons.KeyLocked,
    ) {
        menus.menuAt = -1
        if (off) {
            rules.getOrNull(at)?.let { branchDraftNow().update(at, it.copy(enabled = true)) }
        } else {
            //  停用先弹确认窗（`BranchToggleDialog`，由 `BranchCards` 挂在页面里）
            menus.toggleAt = at
        }
    }
}

/** 表尾那句注脚 —— 与「自定义命令」页那条权限提示同一个样式（不占卡片、居中、`footnote1`）。 */
@Composable
internal fun SettingsPage.BranchDefaultTip() {
    Text(
        //  只留这一句，不接「：侧键 - 单击」那截详细 —— 那一档顶栏副标题已经写着
        text = I18n.get(Strings.COMMAND_BRANCH_DEFAULT, "以上都不成立时用按键绑定"),
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
 * 这一块里要画哪几档动作 —— **与「场景动作」页的档位清单同一份**（`sceneGesturePages()`）。
 *
 *  组合键只有单击 / 长按（双击与多击在组合上没有定义，见 `ComboKey`）；一颗键则是
 *  `GestureTrigger.slotsOn(...)`（单击 / 双击 / 长按，这颗键打开了多击才多出三击 / 四击）。
 *  判据与判定侧同一条：摆一行写进去也永远不会命中的动作，比少一行更糟。
 */
internal fun SettingsPage.blockGestures(): List<GestureTrigger> = sceneGesturePages()

/**
 * 一档动作那一行写什么 —— 应用那一族要带上「具体是哪一个」（`微信(小窗 扫一扫)`）。
 *
 *  整句由 `stepTitleWithTarget`（= core 的 `CommandTitle`）拼，与步骤行、执行提示同一份实现，
 *  所以这里也是「父动作名: 子动作名」（`系统开关: NFC - 切换`）。
 *  目标**整段**交给它（`appTargetLabelOf(action)` 已含括号）—— 走 `stepTitle` 的话
 *  名字回调那层还会再补一次「(小窗)」，真机上就是 `应用(小窗 组件)(小窗)`。
 */
internal fun SettingsPage.actionTextOf(action: Action): String =
    when {
        action is Action.Follow -> I18n.get(Strings.COMMAND_BRANCH_ACTION_FOLLOW, "跟随默认")
        !action.isBound -> I18n.get(Strings.ACTION_NONE, "无操作")
        else -> stepTitleWithTarget(action.token) { target -> appTargetLabelOf(target) }
    }

/**
 * 条件那一行的**标题** —— 「前台应用」这几行按框画应用名，其余仍是一行字。
 *
 *  应用名用 `NameChip`（`ui/row/ChipLabel.kt`，与组合键成员框是同一个组件，用户口径：改成一样的
 *  标签样式、复用它），名字之间仍是那个 `×`；种类名留在前面，读起来是
 *  `前台应用 - [微信]×[QQ]`。
 *
 *  **为什么走 `titleContent` 而不是拼字符串**：框要能各自省略、各自贴边 ——
 *  拼成一整串时省略号只会落在最后，而且框底色根本画不出来。
 *
 *  @param enabled 整页那道闸（关着时框与字一起压灰 —— 与组合标题同一档判据）
 */
@Composable
internal fun ConditionRowTitle(
    line: ConditionLine,
    enabled: Boolean,
) {
    val scheme = COUITheme.colorScheme
    val color = if (enabled) scheme.onSurfaceContainer else scheme.disabledOnSecondaryVariant
    val apps = line.apps
    if (apps.isNullOrEmpty()) {
        Text(
            text = line.text,
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val inner = chipStyle()
    //  种类名作为 [ChipFlow] 的**第一项**参与换行：应用多的时候框排到下一行，
    //  而「前台应用 - 」留在原处 —— 拆成一个独立的 `Text` + 一个 `Row` 的话，框只能挤在同一行里。
    ChipFlow(
        names = apps,
        look = ChipLook(inner, color, enabled),
        modifier = Modifier.fillMaxWidth(),
        leading = {
            Text(
                //  种类名**按这一条自己的档念**：应用那一族两档都走这里，
                //  写死 `APP` 的话「前台应用以外」那一条会念成「前台应用」——
                //  读起来正好是相反的意思。
                text = "${conditionKindLabel(line.kind ?: ConditionKind.APP)} - ",
                style = COUITheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
            )
        },
    )
}
