package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.TILE_HEIGHT
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.STEP_ROW_ENTER
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT
import com.lookie.opluskey.ui.model.STEP_ROW_EXIT_MS
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionTitle
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.model.stepTitle
import com.lookie.opluskey.ui.state.KeyWatchState
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.DotsVertical
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
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
    val addRule: () -> Unit = { freshId = draft.add(StateRule(branchGestureNow(), null, Action.None)) }
    // 删除**先播收缩、再动数据**（见 `BranchMenuState.deletingAt`）：数据晚一步，行才不会瞬间消失。
    //  收尾再核一次「要删的还是不是它」—— 收缩没播完又删了另一条时，这一条留着（宁可不删，不能错删）。
    LaunchedEffect(menus.deletingAt) {
        val at = menus.deletingAt
        if (at < 0) return@LaunchedEffect
        delay(STEP_ROW_EXIT_MS + ROW_EXIT_SETTLE_MS)
        if (menus.deletingAt == at) {
            draft.remove(at)
            menus.deletingAt = NO_EXITING_ROW
        }
    }
    // 一条都没有时先给一块空态 —— 与「自定义命令」页那块同一个样子（字形 + 一行字，不占卡片）。
    //  不画空表格：空白页在设置类界面里读作「坏了」，而不是「还没配」。
    if (rules.isEmpty()) {
        BranchEmptyHint()
    } else {
        // 没有外层卡片：每一行就是左右两个胶囊。行距**挂在行自己身上**（见 [BranchRow]）：
        //  挂在外层 `spacedBy` 的话，退场时那一档空档不会跟着收缩。
        Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
            rules.forEachIndexed { index, rule ->
                // 按**行身份**认（不是位次）：删中间一行时，后面的行不该继承被删那一行的「已退场」状态
                key(draft.ids[index]) {
                    BranchRow(
                        rule = rule,
                        spec = RuleRowSpec(index, anchors[index], fresh = draft.ids[index] == freshId, exiting = menus.deletingAt == index),
                        menus = menus,
                    )
                }
            }
        }
    }
    // 「添加条件」：表里与空态**同一个位置**（表下面 / 空态那句话下面），左右与列表同一档边距
    AddConditionButton(anchors[rules.size].left, addRule)
    // 表尾那句注脚讲的是「一条都不成立时执行什么」—— 一条都没有时这句话由空态自己说，
    //  两句摆在同一个屏幕上只会让人多看一遍（见 `BranchEmptyHint`）。
    if (rules.isNotEmpty()) BranchDefaultTip()
    BranchMenus(rules = rules, menus = menus, tick = tick)
    BranchDeleteDialog(menus)
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
            RuleRow(
                // 条件空着的行写「未选择」：这一行还没配完，与右列「未选择」是同一句话
                condition =
                    rule.condition?.let { conditionTitle(it, ::appNameOf) }
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
 *  **撑满整行**，所以关掉按压缩放（库的口径：填满容器的按钮只留铺满整格的按压着色，见
 *  `pressScaleEnabled`）。左右边距由它自己带（[PAGE_MARGIN]，与列表同一档）——
 *  以前表里那份靠外层 `Column` 的 `padding`、空态那份铺满整宽，于是表一空按钮就贴到屏幕两边。
 */
@Composable
private fun SettingsPage.AddConditionButton(
    anchor: PreciseClickState,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PAGE_MARGIN, end = PAGE_MARGIN, top = ADD_BUTTON_GAP)
                .preciseClickAnchor(anchor),
        pressScaleEnabled = false,
    ) {
        Text(
            text = I18n.get(Strings.COMMAND_BRANCH_ADD, "添加条件"),
            // 与库的文本按钮同一档字（默认就是它，显式写出免得跟着库默认值漂）
            style = COUITheme.textStyles.button,
        )
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

/** 中间那颗三点的**外边距**（只给左右）—— 与两格之间那一小道缝。 */
private val MENU_DOTS_MARGIN = 2.dp

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

/**
 * 一条规则那一行 —— **两列**：左边是条件、右边是动作，中间一枚右箭头。
 *
 *  两列各自可点（左列换条件、右列换动作），所以是两个独立的触点，各挂一份
 *  [preciseClickAnchor]：菜单弹在**被点的那一格**旁边，而不是整行。
 */
@Composable
private fun RuleRow(
    condition: String,
    action: String,
    conditionIcon: ImageVector,
    actionIcon: ImageVector,
    cells: RuleCells,
) {
    Row(
        // 行高按格子那一档**定死**：中间那颗要 `fillMaxHeight()` 填满，而 `fillMaxHeight`
        //  在「父级高度不定」时是空操作（Row 是 warp-content，子项拿到的是无限高度）。
        modifier = Modifier.fillMaxWidth().height(TILE_HEIGHT),
        // 两列之间那道缝 = 中间那颗的 margin 2dp + 它自己的 padding（见下面那颗 Box）。
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleCell(
            text = condition,
            icon = conditionIcon,
            modifier = Modifier.weight(1f).preciseClickAnchor(cells.anchors.left),
            onClick = cells.onCondition,
        )
        // 中间那颗管**这一行本身**（上移 / 下移 / 删除），所以它自己也是一个触点。
        //
        //  · **margin 2dp（只给左右）**：让这颗按键与两格之间各留一道缝，点的时候不会贴着格子边；
        //    纵向不给 margin —— 那一头要填满整行（`fillMaxHeight`，行高由上面定死的那一档给）。
        //  · **padding 保持原值（水平 4dp / 纵向 8dp）**：那是图标与按钮边界之间的距离，
        //    与 margin 是两件事（margin 在 `clip` / `clickable` **外面**，不进点击区）。
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = MENU_DOTS_MARGIN)
                    .preciseClickAnchor(cells.anchors.menu)
                    .clip(RULE_CELL_SHAPE)
                    .clickable(onClick = cells.onMenu)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TablerIcons.Outline.DotsVertical,
                contentDescription = I18n.get(Strings.COMMON_MORE, "更多"),
                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        RuleCell(
            text = action,
            icon = actionIcon,
            modifier = Modifier.weight(1f).preciseClickAnchor(cells.anchors.right),
            onClick = cells.onAction,
        )
    }
}

/** 一行里的一格 —— 与网格的格子同一个壳（[Card] + `surfaceContainer`），文字单行截断。 */
@Composable
private fun RuleCell(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Card(
        // 与网格那格**同高**（[TILE_HEIGHT]）—— 两页的格子摆在一起时不会一高一矮
        modifier = modifier.height(TILE_HEIGHT),
        // 与网格那格**同一套壳**（见 `BindingTile` 的 `ActionTile`）：底色 / 按下色 / 内容色都取自
        //  那三档，圆角与阴影交给库的 `Card` —— 这样两个胶囊与按键绑定页的格子读起来是一族。
        colors =
            CardDefaults.defaultColors(
                color = scheme.surfaceContainer,
                contentColor = scheme.onSurfaceContainer,
                pressedColor = scheme.surfaceContainerHigh,
            ),
        pressFeedbackType = PressFeedbackType.None,
        showIndication = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onSurfaceContainer)
            Text(
                text = text,
                // 与网格里那行标题同一档字号（`body2`）：一行放两格，比整行标题小一档才不挤
                style = COUITheme.textStyles.body2,
                // 与行首那枚图标同一行、竖居中 —— 行盒居中于字却不居中于墨迹（见 `opticalCenter`）
                modifier = Modifier.opticalCenter(COUITheme.textStyles.body2),
                color = scheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
