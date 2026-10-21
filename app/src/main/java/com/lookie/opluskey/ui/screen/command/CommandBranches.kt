package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionRowTitle
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.state.KeyWatchState
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
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
internal fun SettingsPage.BranchCards(tick: () -> Unit) {
    val draft = branchDraft.draft
    val rules = draft.rules
    // 这一份状态在页面里只建一次（里面的锚点会随「点的是哪一格」被换掉）。
    val menus = remember { BranchMenuState() }
    // **每一格一份锚点**：左列（条件）/ 右列（动作）/ 中间那颗「更多」各是一个触点，
    //  各有一套坐标。多出来的一组留给末尾那颗「添加条件」。
    val anchors = remember(rules.size) { List(rules.size + 1) { RuleAnchors() } }
    // 刚加的那一行的身份 —— 只有它播入场（其余行是载入时就有的，进页不该整片展开一遍）。
    var freshId by remember { mutableIntStateOf(NO_FRESH_ROW) }
    // 加一条 = 插一条**条件和动作都空着**的行：不先填默认条件（「亮屏中」看着像用户自己选的，
    //  一成立就悄悄接管这一档），也不弹菜单再落（那会把「加一条」变成两步）。
    //
    //  加的是**第一条**时不播行进场：那一下整块高度由外层（空态 ↔ 表格）那套动画负责，
    //  里层再展开一遍就是同一条动画跑两遍 —— 用户口径「这样两遍动画太拖了」。
    //  第二条起外层不再切换（`isEmpty` 一直是 false），行照常播自己的进场。
    val addRule: () -> Unit = { addBranchRule(rules, tick) { freshId = it } }
    // 删除**先播收缩、再动数据**（见 `BranchMenuState.deletingAt`）：数据晚一步，行才不会瞬间消失。
    //  收尾再核一次「要删的还是不是它」—— 收缩没播完又删了另一条时，这一条留着（宁可不删，不能错删）。
    //
    //  **删的是最后一条时不等**：那一下整块要变成空态，而空态由外层（空态 ↔ 表格）那套动画接管；
    //  要是还照「先收行 220ms + 结算 40ms、再展开空态 220ms」，就是两段串在一起。
    //  直接移除，行不单独收缩、只跟着外层一起淡出。
    LaunchedEffect(menus.deletingAt) {
        val at = menus.deletingAt
        if (at < 0) return@LaunchedEffect
        if (rules.size > 1) delay(STEP_ROW_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.deletingAt == at) {
            draft.remove(at)
            menus.deletingAt = NO_EXITING_ROW
        }
    }
    // 一条都没有时先给一块空态 —— 与「自定义命令」页那块同一个样子（字形 + 一行字，不占卡片）。
    //  不画空卡片：空白页在设置类界面里读作「坏了」，而不是「还没配」。
    //
    //  **空态 ↔ 表格不能硬切**（两处高度本来就不一样）：
    //  空态那一块（上下各 24dp 留白 + 字形 + 一行字）约一百来 dp，表格一行是 [TILE_HEIGHT] 52 +
    //  间距 12 = 64dp —— 两处高度本来就不一样（空态里没有行），以前 `if/else` 一换，页面高度
    //  先掉一截再涨回去，「添加条件」那颗按钮跟着上下跳。
    //  现在不追「一样高」，改成这一块高度**连续地**从旧值走到新值：同一个容器换内容，
    //  尺寸交给 [SizeTransform] 插值（`clip = false` 是为了不把行尾那颗按钮的按压着色裁掉）。
    //  这一下是**唯一**一处动画 —— 里层第一行不播进场（见上面 `addRule`）。
    //
    //  空态**不做整页居中**：它就从顶栏下面开始往下排，与表格同一个起点 ——
    //  「空态 + 添加条件」这一组在屏幕正中漂着时，加第一条会看到整组先往上滑一大段，
    //  而它本来就是这一页的正文，跟着顶栏走才对得齐。
    Column(Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = rules.isEmpty(),
            transitionSpec = {
                (
                    fadeIn(tween(BRANCH_BODY_MS, easing = DecelerateEasing())) togetherWith
                        fadeOut(tween(BRANCH_BODY_MS, easing = AccelerateEasing()))
                ).using(SizeTransform(clip = false) { _, _ -> tween(BRANCH_SIZE_MS, easing = DecelerateEasing()) })
            },
            contentAlignment = Alignment.TopCenter,
            label = "branch-body",
        ) { empty ->
            if (empty) {
                BranchEmptyHint()
            } else {
                BranchRuleColumn(
                    rules = rules,
                    ids = draft.ids,
                    freshId = freshId,
                    menus = menus,
                    anchors = anchors,
                )
            }
        }
        // 「添加条件」：表里与空态**同一个位置**（表下面 / 空态那句话下面），左右与列表同一档边距。
        //  它落在上面那块的外面 ⇒ 块高怎么变，它就跟着平移多少（不再自己跳）。
        //  宽度分两档：有表格时通栏（与表格左右对齐），空态时跟内容宽、居中
        //  （见 [AddConditionButton] 的「宽度分两档」）。
        AddConditionButton(anchors[rules.size].left, fillWidth = rules.isNotEmpty(), addRule)
        // 表尾那句注脚讲的是「一条都不成立时执行什么」—— 一条都没有时这句话由空态自己说，
        //  两句摆在同一个屏幕上只会让人多看一遍（见 `BranchEmptyHint`）。
        //
        //  也不能硬切：它是**多出来的一行高度**，从「没有」到「有」同样会顶一下（同一处抽动）。
        AnimatedVisibility(
            visible = rules.isNotEmpty(),
            enter = fadeIn(tween(BRANCH_TIP_MS, easing = DecelerateEasing())) + BRANCH_TIP_EXPAND,
            exit = fadeOut(tween(BRANCH_TIP_MS, easing = AccelerateEasing())) + BRANCH_TIP_SHRINK,
        ) {
            BranchDefaultTip()
        }
    }
    BranchMenus(rules = rules, menus = menus, tick = tick)
    BranchDeleteDialog(menus)
}

/**
 * 「添加条件」那一下 —— 末尾那条**条件和命令都还空着**时不再往下加：新加的行一样是空的，
 * 堆两条空行既看不出差别、校验也过不了。说一句就停在这一条上，让人先去把它填了。
 *
 * 加的是**第一条**时返回 [NO_FRESH_ROW]：那一下整块高度由外层（空态 ↔ 表格）那套动画负责，
 * 里层再展开一遍就是同一条动画跑两遍。
 */
private fun SettingsPage.addBranchRule(
    rules: List<StateRule>,
    tick: () -> Unit,
    onAdded: (Int) -> Unit,
) {
    val tail = rules.lastOrNull()
    if (tail != null && tail.condition == null && tail.action == Action.None) {
        tick()
        //  **同一个语义位**：连点几下是这一句在改字，不是叠出一串
        hint(
            I18n.get(Strings.COMMAND_BRANCH_NEED_CONDITION, "还有条件没选完"),
            slot = HINT_SLOT_COMMAND,
        )
        return
    }
    val first = rules.isEmpty()
    val id = branchDraft.draft.add(StateRule(branchGestureNow(), null, Action.None))
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
                        ),
                    menus = menus,
                )
            }
        }
    }
}

/**
 * 空态与表格切换时**淡入淡出**那一段 —— 给得比尺寸短：颜色先交代完，剩下的交给高度。
 *
 *  只跑这一套：内容交叉淡入淡出 [BRANCH_BODY_MS]，高度由 `emptyMinHeight` 那一条单独管，
 *  里层第一行不再播进场（见 `addRule`）。
 */
private const val BRANCH_BODY_MS = 150

/** 空态 ↔ 表格之间**高度**那一段 —— 与行的进场（280）/ 退场（220）同一量级。 */
private const val BRANCH_SIZE_MS = 220

/** 表尾注脚进出场的高度那一段 —— 比行的高度动画略短，它是注释不是内容。 */
private const val BRANCH_TIP_MS = 180

/** 表尾注脚进出场（淡入淡出 + 高度）—— 与空态那块同一族写法。 */
private val BRANCH_TIP_EXPAND =
    expandVertically(
        animationSpec = tween(BRANCH_TIP_MS, easing = DecelerateEasing()),
        expandFrom = Alignment.Top,
    )

private val BRANCH_TIP_SHRINK =
    shrinkVertically(
        animationSpec = tween(BRANCH_TIP_MS, easing = AccelerateEasing()),
        shrinkTowards = Alignment.Top,
    )

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
 *  行距挂在 `AnimatedVisibility` **里面**：退场时它跟着高度一起收掉，下面的行是一次平滑上移，
 *  而不是「收完再跳一档间距」。
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
        Box(Modifier.padding(bottom = GRID_ROW_GAP)) {
            RuleBlock(
                // 「条件 1」「条件 2」—— 一份表里第几条，标题上直接写出来
                index = spec.index,
                text =
                    RuleText(
                        // 条件空着的行写「未选择」：这一条还没配完，与下面那格「未选择」是同一句话。
                        //  非空的走 `conditionRowTitle`：卡片里这一行把「种类 - 取值」两级都写出来。
                        condition =
                            rule.condition?.let { conditionRowTitle(it, ::appNameOf) }
                                ?: I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择"),
                        action =
                            if (rule.action == Action.None) {
                                I18n.get(Strings.COMMAND_BRANCH_NO_COMMAND, "未选择")
                            } else {
                                stepTitle(rule.action.token, ::appNameOf)
                            },
                        conditionIcon = rule.condition?.kind?.let(::conditionIcon) ?: PageIcons.CommandBranch,
                        actionIcon =
                            if (rule.action == Action.None) {
                                PageIcons.CommandSingle
                            } else {
                                stepIcon(rule.action.token, configState.commands.value)
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
)

/** 「刚加的那一行」的哨兵（行身份从 0 起，撞不上）。 */
private const val NO_FRESH_ROW = -1

/** 「没有正在退场的行」的哨兵 —— 与 `BranchMenuState.deletingAt` 的初始值同义。 */
private const val NO_EXITING_ROW = -1

/** 退场播完再多等一帧才动数据：让收缩的最后一帧先落到屏幕上，删完不会看到一次跳变。 */
private const val ROW_EXIT_SETTLE_MS = 40L

/**
 * 「添加条件」那颗按钮 —— 表里与空态都用它，锚点由调用方给。
 *
 *  用的是 COUI 的 [Button] 本体（默认就是次要那一档配色），不自己拼颜色 / 圆角 / 字号：
 *  最小高度、圆角、内边距、按压反馈都由 `ButtonDefaults` 给，与页面里别的控件同一族。
 *
 *  ## 宽度分两档：有表时通栏，空态时跟内容
 *
 *   · **有表格**（`fillWidth = true`）：`fillMaxWidth` —— 通栏压在整张表下面，读作「在这张表
 *     末尾再加一条」，上下与表格的左右边缘对齐。此时关掉按压缩放：
 *     库的口径是「填满容器的按钮只留铺满整格的按压着色」（见 `pressScaleEnabled`）。
 *   · **空态**（`fillWidth = false`）：宽度由「添加条件」四个字加上库的内边距决定、水平居中 ——
 *     空态那一组（字形 + 一句话 + 这颗按钮）整体在内容区居中，一颗通栏按钮摆在里面会把
 *     那块留白切开。
 *
 *  左右边距两档都在（外层那个 `Box` 带 [PAGE_MARGIN]），文字再长也碰不到屏幕边。
 *
 * @param fillWidth 这一档是不是「表格下面那颗通栏按钮」（判据是**有没有条件**，见调用点）
 */
@Composable
private fun SettingsPage.AddConditionButton(
    anchor: PreciseClickState,
    fillWidth: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PAGE_MARGIN, end = PAGE_MARGIN, top = ADD_BUTTON_GAP),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            modifier =
                (if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                    .preciseClickAnchor(anchor),
            pressScaleEnabled = !fillWidth,
        ) {
            Text(
                text = I18n.get(Strings.COMMAND_BRANCH_ADD, "添加条件"),
                // 与库的文本按钮同一档字（默认就是它，显式写出免得跟着库默认值漂）
                style = COUITheme.textStyles.button,
            )
        }
    }
}

/** 每一行那三个触点（左列 / 右列 / 中间那颗「更多」）—— 一行一套。 */
private class RuleAnchors {
    val left = PreciseClickState()
    val right = PreciseClickState()
    val menu = PreciseClickState()
}

/** 表与「添加条件」之间的留白 —— 行自己带了 [GRID_ROW_GAP] 的下边距，这里不再加。 */
private val ADD_BUTTON_GAP = 0.dp

/** 小标题那一行的高度 —— 标题 + 右端那颗三点，两者都在这一行里竖居中。 */
private val RULE_TITLE_HEIGHT = 28.dp

/** 小标题行右端那颗三点的大小（点击区）—— 与标题行同高，别再往外扩。 */
private val RULE_MENU_SIZE = 28.dp

/** 表尾那句注脚 —— 与「自定义命令」页那条权限提示同一个样式（不占卡片、居中、`footnote1`）。 */
@Composable
private fun SettingsPage.BranchDefaultTip() {
    Text(
        text =
            I18n.get(Strings.COMMAND_BRANCH_DEFAULT, "以上都不成立时，执行按键绑定里的命令") +
                "：${branchDefaultLabel()}",
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
    text: RuleText,
    cells: RuleCells,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(RULE_TITLE_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = I18n.format(Strings.COMMAND_BRANCH_ITEM, index + 1),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Box(
                modifier =
                    Modifier
                        .size(RULE_MENU_SIZE)
                        .preciseClickAnchor(cells.anchors.menu)
                        .clip(RULE_CELL_SHAPE)
                        .clickable(onClick = cells.onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TablerIcons.Outline.DotsVertical,
                    contentDescription = I18n.get(Strings.COMMON_MORE, "更多"),
                    tint = COUITheme.colorScheme.onSurfaceVariantSummary,
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
                        trailing = { SelectorGlyph() },
                        modifier = Modifier.preciseClickAnchor(cells.anchors.left),
                        onClick = cells.onCondition,
                    ),
                    CardRow.Item(
                        title = text.action,
                        icon = text.actionIcon,
                        trailing = { SelectorGlyph() },
                        modifier = Modifier.preciseClickAnchor(cells.anchors.right),
                        onClick = cells.onAction,
                    ),
                ),
        )
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
