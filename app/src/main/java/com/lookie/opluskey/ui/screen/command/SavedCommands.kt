package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.MoreButton
import com.lookie.opluskey.ui.NoHapticFeedback
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.TileFocusRing
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.model.DISABLED_ALPHA
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.SUMMARY_REF_GAP
import com.lookie.opluskey.ui.model.SUMMARY_REF_ICON
import com.lookie.opluskey.ui.model.StepLine
import com.lookie.opluskey.ui.model.TAB_PAGE_SWITCH_MS
import com.lookie.opluskey.ui.model.TILE_TITLE_GAP
import com.lookie.opluskey.ui.model.commandIcon
import com.lookie.opluskey.ui.model.durationText
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.model.serialSummarySteps
import com.lookie.opluskey.ui.model.stepIcon
import com.lookie.opluskey.ui.state.CommandUiState
import com.lookie.opluskey.ui.state.TileMenu
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType

// 「自定义命令」页底下那一段「已保存的命令」—— 网格、单张卡、空状态。
//
// 从 `CommandScreen.kt` 拆出来：这一段原来埋在 `HorizontalPager` → `Column` → `AnimatedContent`
// 三层 lambda 里，缩进到了 36 列，读的时候得先数括号。它的边界本来就清楚（一个列表 + 一种卡），
// 拆出来之后 `CommandScreen` 只管「pager 与那几个确认窗」。

/**
 * 「已保存的命令」那一段 —— **始终在**：一条都没有时给一张空状态。
 *
 *  以前是 `if (saved.isNotEmpty())` 整段不出现，用户看不出这块地方该有东西、也分不清
 *  「还没保存过」与「这块地方根本不存在」。
 *
 *  「一条都没有」与「有若干条」之间走**交叉过渡**：原来是 `if / else` 硬切 —— 删掉最后一条时
 *  整片网格「啪」地消失、空状态「啪」地出现，中间没有任何交代。`AnimatedContent` 会把容器
 *  高度从旧值补到新值（[SizeTransform]），观感就是网格收下去、空状态长出来。
 *
 *  注意：目标态传的是**列表本身**，不是 `isEmpty()` 那个布尔 —— `AnimatedContent` 把旧值交给
 *  退场那一侧，若只传布尔，退场侧拿到的仍是「非空」分支、而数据已经空了 ⇒ 它渲染不出任何卡片，
 *  那半场会瞬间塌成 0 高，过渡等于没有。
 *  `contentKey` 只看空 / 非空 ⇒ 改某条命令（列表内容变了、空非空没变）不会重播过渡。
 */
@Composable
internal fun SettingsPage.SavedCommandsSection(
    saved: List<Command>,
    ui: CommandUiState,
    tick: () -> Unit,
    onScrollTop: () -> Unit,
    /** 排序态下这一片整片禁用（见 `CommandDraftState.stepEditing`）：点开一条会把正在排的草稿换掉。 */
    enabled: Boolean = true,
) {
    AnimatedContent(
        targetState = saved,
        contentKey = { it.isEmpty() },
        transitionSpec = {
            // 用 `ContentTransform` 显式给尺寸动画，而不是 `using(SizeTransform(...))`：
            // 这个 compose 版本里没有 `using` 那个中缀（实测 `Unresolved reference`）。
            ContentTransform(
                //  缓动取 COUI 的减速 / 加速两档（见 PageMotion 顶部那段）：进的那一栏淡入用减速、
                //  退的那一栏淡出用加速，尺寸过渡跟着进场那一侧走同一条。
                targetContentEnter = fadeIn(tween(TAB_PAGE_SWITCH_MS, easing = DecelerateEasing())),
                initialContentExit = fadeOut(tween(TAB_PAGE_SWITCH_MS, easing = AccelerateEasing())),
                sizeTransform =
                    SizeTransform(
                        clip = true,
                        sizeAnimationSpec = { _, _ -> tween(TAB_PAGE_SWITCH_MS, easing = DecelerateEasing()) },
                    ),
            )
        },
        label = "SavedCommands",
    ) { list ->
        if (list.isEmpty()) {
            SavedCommandEmptyState()
        } else {
            //  两列卡片网格：每条命令一张卡，宽度平分、高度下限按类型给（单条 = 半个正方形，
            // 多条 = 正方形），内容更高时以内容为准；行 / 列间距用 [GRID_GAP] / [GRID_ROW_GAP]。
            //  注意：画的是 [list]（目标态那份），不是外层的 `saved` —— 过渡期间退场那一侧
            //  拿到的正是旧列表，用 `saved` 会让它渲染成空网格。
            list.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(Modifier.height(GRID_ROW_GAP))
                CommandTileRow(
                    tileCount = row.size,
                    // 行高下限按**这一行里那些命令自己的类型**算，不按「当前正在编辑的草稿」是什么：
                    //  网格与草稿是两件事 —— 以前取 `commandDraft.draft.kind`，于是翻到「单条」那栏
                    //  时整片网格的多条命令卡都被压成半个正方形（反过来也一样）。
                    //  一行里两种类型混着时取高的那一档（`1f`）：宁可单条卡偏高，不能把多条卡压扁。
                    minHeightRatio =
                        if (row.any { it.kind != CommandKind.SINGLE }) {
                            1f
                        } else {
                            SINGLE_CARD_MIN_HEIGHT_RATIO
                        },
                    // 上限只压单条那一档（= 卡宽，正方形）；混排与多条都不封顶 —— 多条卡的步骤是
                    //  逐条列出来的，截掉就把「有几步」藏了。
                    maxHeightRatio =
                        if (row.any { it.kind != CommandKind.SINGLE }) {
                            0f
                        } else {
                            1f
                        },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN),
                ) { tileIndex ->
                    val cmd = row[tileIndex]
                    // 每格按 `key(cmd.name)` 认身份（命令名是这条命令的主键）—— 与步骤行 `key(item.id)`
                    //  同一个理由：这一片是按**位次**排出来的，卡里的 `animateColorAsState` 与卡片槽位都按
                    //  位次记身份 ⇒ 删一条 / 改名之后后面的格子会继承前一格的位置，强调色从上一格的颜色
                    //  动画过去（一帧闪色）。
                    key(cmd.name) {
                        CommandTile(cmd, ui, tick, onScrollTop, enabled)
                    }
                }
            }
        }
    }
}

/**
 * 一张「已保存的命令」卡 —— 卡片本体 + 长按菜单 + 选中那圈边框。
 *
 *  交互走库的交互式 `Card`（自带 `onClick` / `onLongPress` 与涟漪）；`preciseClickAnchor`
 *  挂在外层，只看不消费（浮层菜单的落点由它给）。
 */
@Composable
private fun SettingsPage.CommandTile(
    cmd: Command,
    ui: CommandUiState,
    tick: () -> Unit,
    onScrollTop: () -> Unit,
    enabled: Boolean,
) {
    // 每一张卡各自一份 `PreciseClickState`（按命令名做 key）：`anchorCoordinates`
    //  只有一格，共用一份会把触点算到别的卡上去。
    val tilePreciseClick = remember(cmd.name) { PreciseClickState() }
    // 右上角那颗「更多」另记一份：菜单要弹在**那颗字上**，不是长按的手指那一点。
    val dotsAnchor = remember(cmd.name) { PreciseClickState() }
    // 选中态（正在表单里编辑的那一条）：三处共用，只算一次 —— 判据是 `draft.editingName`。
    val editing = commandDraft.draft.editingName == cmd.name
    // 选中时类型图标与标题一起上主题色，带过渡（一处动画喂两个组件）。
    val accentColor by animateColorAsState(
        targetValue = if (editing) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceContainer,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CommandTileAccent",
    )
    // 排序态下整张卡压暗：库的 `Card` 没有 `enabled`，而「点不动」得看得见 ——
    //  与「按键绑定」页那条分段控件同一档（[DISABLED_ALPHA]），不做两套灰。
    Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        // 长按振动只走通用通道（[tickNow]）：库默认还会自己振一次 `LongPress`，
        // 那条不看系统静音 —— 这里把局部触觉反馈换成空实现。
        CompositionLocalProvider(LocalHapticFeedback provides NoHapticFeedback) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // 行高由 [CommandTileRow] 统一定，这里填满行高即可。
                        .fillMaxHeight()
                        .preciseClickAnchor(tilePreciseClick),
                colors = commandTileColors(),
                pressFeedbackType = PressFeedbackType.None,
                // 禁用时连涟漪都不要：这一下本来就不做事，反馈会让人以为「点了、只是没生效」
                showIndication = enabled,
                onClick = {
                    // 排序态下整片禁用：轻点这一张会把正在排的草稿整份换掉。
                    if (!enabled) return@Card
                    tick()
                    // 草稿里有没保存的改动时先问一句（见 [loadDraft]）
                    loadDraft(cmd)
                    // 表单在页面顶部，不滚回去看不出点开了哪一张。
                    onScrollTop()
                },
                onLongPress = {
                    if (!enabled) return@Card
                    tick()
                    XLog.w("已保存命令长按：${cmd.name}（弹测试 / 编辑 / 删除）")
                    ui.menu = TileMenu(cmd.name, tilePreciseClick)
                },
            ) {
                CommandTileBody(cmd, accentColor, enabled, dotsAnchor) {
                    tick()
                    XLog.w("已保存命令菜单：${cmd.name}（右上角那颗「更多」）")
                    ui.menu = TileMenu(cmd.name, dotsAnchor)
                }
            }
        }
        // 浮层挂在零宽内层盒子里（落点由触点决定，这里只是宿主）。
        Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().width(0.dp)) {
            CommandMenuPopup(
                cmd = cmd,
                show = ui.menu?.name == cmd.name,
                //  开着的时候这里拿到的必然是**这一次**的锚点（名字对得上才画）
                preciseClick = ui.menu?.anchor ?: tilePreciseClick,
                // 与「轻点这一张」同一个去处：灌进表单后滚回顶部。
                onEdited = onScrollTop,
                onDelete = {
                    ui.menu = null
                    ui.deleting = cmd
                },
                onDismiss = { ui.menu = null },
            )
        }
        // 选中态：正在表单里编辑的那一条画一圈内缩边框（[TileFocusRing]），
        // 标题与类型图标一起上 `primary`，三处都是渐变过去的。
        // 画在最后才盖得住卡片圆角；未选中时整圈透明，不吃触摸。
        TileFocusRing(editing)
    }
}

/**
 * 命令卡的三档底色 —— **所有卡一样**，抽成一份：卡里原先每次重组都重搭一个 `CardColors`，
 * 而这三档只跟主题走。
 */
@Composable
private fun commandTileColors(): CardColors =
    CardDefaults.defaultColors(
        color = COUITheme.colorScheme.surfaceContainer,
        contentColor = COUITheme.colorScheme.onSurfaceContainer,
        pressedColor = COUITheme.colorScheme.surfaceContainerHigh,
    )

/** 卡片内容：顶行（图标 + 标题或间隔 + 那颗「更多」）、多条命令另起一行的标题、以及摘要。 */
@Composable
private fun SettingsPage.CommandTileBody(
    cmd: Command,
    accentColor: Color,
    enabled: Boolean,
    /** 右上角那颗「更多」的触点锚点（菜单弹在它上面）。 */
    menuAnchor: PreciseClickState,
    /** 点那颗「更多」：开这一张卡的菜单（测试 / 编辑 / 删除）。 */
    onMenu: () -> Unit,
) {
    // 顶部对齐：顶行定高，同一行的标题永远是一条线；高度包住内容，下限由布局给。
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // 顶行：左边类型图标，右边测试按钮；单条命令的标题也塞进这一行。
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommandTileHeadline(cmd, accentColor, Modifier.weight(1f))
            // 右上角那颗「横向三个点」—— 测试 / 编辑 / 删除都收进它的菜单
            //  （原先这里是单独一颗「测试」）。它自己吃掉这一下，不会触发整张卡的 `onClick` 或长按。
            //  **flushEnd**：字形贴住这一格的右边线，右边线才与卡片左侧那颗类型图标对称
            //  （卡片内容有 16dp 内边距，居中的话它会比左边多缩进半个点击区）。
            //  挪的是整个点击区、不是格子里的字形 —— 详情见 `MoreButton`（上一版挪字形，
            //  按下去的底色偏在字左边，看着像「波纹歪」）。
            MoreButton(onClick = onMenu, anchor = menuAnchor, enabled = enabled, flushEnd = true)
        }
        // 多条命令：**名字在图标那一行**，步骤间隔挪到下面另起一行（用户口径「名称和 delay
        //  换个位置」—— 名字是这张卡的主键，跟类型图标同一行才读得出「这是哪一条」；
        //  间隔是执行参数，放在名字下面当小字）
        if (cmd.kind == CommandKind.SERIAL) {
            Spacer(Modifier.height(TILE_TITLE_GAP))
            //  行首那枚小图标与下面摘要行同一支笔、同一档尺寸（[SUMMARY_REF_ICON] / [SUMMARY_REF_GAP]）——
            //  图标就是「命令间隔时长」那一行用的那枚（[PageIcons.DelayStep]）：同一个参数，同一颗图标。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    PageIcons.DelayStep,
                    contentDescription = null,
                    modifier = Modifier.size(SUMMARY_REF_ICON),
                    tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(SUMMARY_REF_GAP))
                Text(
                    // 文案走 [durationText]（全局唯一一处）：
                    // 1000ms 以上写成秒（`3 秒`），一秒以内是毫秒（`400 毫秒`）
                    text = durationText(cmd.stepDelayMs),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    //  与行首那枚小图标同一行、竖居中：行盒居中不等于墨迹居中，补一档光学修正
                    //  （与下面摘要行、单条摘要、卡片标题同一支笔 —— 见 `opticalCenter`）
                    modifier = Modifier.opticalCenter(COUITheme.textStyles.footnote1),
                )
            }
        }
        // 标题与摘要之间那 2dp = 库私有常量 `MarginBetweenLine` 的取值。
        Spacer(Modifier.height(2.dp))
        CommandTileSummary(cmd)
    }
}

/**
 * 卡片顶行左段：类型图标 + **标题**（单条 / 多条都一样）。
 *
 *  多条命令的「步骤间隔」不在这一行 —— 它是执行参数，挪到名字下面那一行小字里
 *  （见卡片正文：用户口径「名称和 delay 换个位置」）。
 *
 *  挂在 [Modifier.weight] 上（调用点传进来）而不是自己声明 `RowScope`：
 *  同一个函数还要读 [SettingsPage] 的东西时，Kotlin 只允许一个显式接收者。
 */
@Composable
private fun CommandTileHeadline(
    cmd: Command,
    accentColor: Color,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            commandIcon(cmd.kind, cmd.steps.size),
            null,
            Modifier.size(ROW_ICON_SIZE),
            // 选中时跟着标题走主题色 —— 不给 tint 会吃 `Card` 的 `contentColor`。
            tint = accentColor,
        )
        Spacer(Modifier.width(TILE_TITLE_GAP))
        // 两种命令的标题都**跟图标同一行**：名字是这张卡的主键，一眼要能认出来。
        //  多条命令独有那位「步骤间隔」挪到名字下面那行小字里去（见卡片正文那一段）。
        Text(
            text = cmd.name,
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            // 选中时上主题色，与类型图标同色、同一动画值。
            color = accentColor,
            maxLines = if (cmd.kind == CommandKind.SINGLE) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            // 与行首那枚类型图标同一行、竖居中：补一档光学修正（见 `opticalCenter`）
            modifier = Modifier.opticalCenter(COUITheme.textStyles.headline1),
        )
    }
}

/** 摘要：一行一条全部列出（卡会跟着长高）；每行截断交给 `Text`，这层不估宽度。 */
@Composable
private fun SettingsPage.CommandTileSummary(cmd: Command) {
    when (cmd.kind) {
        CommandKind.SINGLE -> {
            Text(
                text = cmd.single,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                // 与行首图标对齐（行盒居中不等于墨迹居中，见 `opticalCenter`）
                modifier = Modifier.opticalCenter(COUITheme.textStyles.footnote1),
                // 卡片高度封顶在正方形（见 `CommandTileRow` 的 `maxHeightRatio`），摘要再长也画不下：
                //  三行封顶 + 省略号，好过被父级的高度约束**从中间裁断**（那连省略号都没有）。
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        CommandKind.SERIAL -> {
            summaryLineRows(serialSummarySteps(cmd.steps, ::appNameOf))
        }
    }
}

/**
 * 摘要里的那几行 —— 「多条命令」与「条件执行」同一套画法：一行一条，行首一枚小图标。
 *
 *  行头那枚图标走 [stepIcon]：引用另一条命令（`cmd:<名字>`）的行用**被引用那条的真实类型图标**
 *  （单条 / 按步数的多条 / 分叉），其余用动作图标 —— 光看文字分不出几类内容。
 */
@Composable
private fun SettingsPage.summaryLineRows(lines: List<StepLine>) {
    lines.forEach { line ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                stepIcon(line.token, configState.commands.value),
                contentDescription = null,
                modifier = Modifier.size(SUMMARY_REF_ICON),
                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(SUMMARY_REF_GAP))
            Text(
                text = line.title,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                //  同上：与行首那枚步骤图标竖着对中（行盒 ≠ 墨迹，见 `opticalCenter`）
                modifier = Modifier.opticalCenter(COUITheme.textStyles.footnote1),
            )
        }
    }
}

/**
 * 「已保存的命令」一条都没有时的空状态：一颗字形 + 一句话，**不带卡片背景**。
 *
 *  · 文案通用一句（不按当前那一栏分单条 / 多条）：两栏是同一个命令库的两份视图。
 *  · 不铺卡片：这块地方本来就没东西，画一张空壳卡像「卡在、内容没出来」。
 *  · 字形比「选择应用」页那几块空态小一档（那是整页空态，这里只是页内一段），取色同一份。
 */
@Composable
private fun SavedCommandEmptyState() {
    //  排版走 [Feedback]（与「选择应用」「条件执行」那两块同一份）；这一档只是页内一段，
    //  所以上下留一档白，不当整页空态居中。
    Feedback(
        glyph = { tint ->
            //  与「选择应用」「条件执行」那两块**同一颗脸**：三处说的都是「这里什么都没有」。
            Icon(PageIcons.NoMatch, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = tint)
        },
        text = I18n.get(Strings.COMMAND_NO_SAVED_COMMANDS, "没有保存的命令"),
        modifier = Modifier.fillMaxWidth().padding(vertical = EMPTY_STATE_VERTICAL_PADDING),
    )
}

/**
 * 「已保存的命令」空状态那一段的上下留白 —— 纯视觉：字形 + 一行字撑起来，比一条命令卡矮一档。
 * （没有卡片背景了，这档留白就是它与上面标题、下面内容之间的距离。）
 */
private val EMPTY_STATE_VERTICAL_PADDING = 24.dp
