package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.LocalTick
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import com.lookie.opluskey.ui.model.SUMMARY_REF_GAP
import com.lookie.opluskey.ui.model.SUMMARY_REF_ICON
import com.lookie.opluskey.ui.model.TAB_PAGE_SWITCH_MS
import com.lookie.opluskey.ui.model.TILE_TITLE_GAP
import com.lookie.opluskey.ui.model.commandIcon
import com.lookie.opluskey.ui.model.durationText
import com.lookie.opluskey.ui.model.gridColumnCount
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.state.CommandListEditState
import com.lookie.opluskey.ui.state.CommandUiState
import com.lookie.opluskey.ui.state.TileMenu
import com.lookie.opluskey.ui.tinted
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.suqi8.coui.kmp.anim.AccelerateEasing
import io.github.suqi8.coui.kmp.anim.DecelerateEasing
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Checkbox
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
    /** 点开一条（轻点卡片 / 菜单里的「编辑」）—— 草稿由卡片自己灌好，调用方只管跳回表单页。 */
    onOpen: (Command) -> Unit,
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
            //  **必须自己套一层 `Column`**：`AnimatedContent` 的内容位是**叠放式**的（所有子项都摆
            //  在左上角），直接往里发多行网格会让第 2 行起与第 1 行叠在一起 —— 两条命令（一行）时
            //  看不出来，三条起就糊成一坨（真机反馈「命令列表里超过两条时卡片会叠在一起」）。
            //  卡片网格：每条命令一张卡，宽度平分、高度下限按类型给（单条 = 半个正方形，
            //  多条 = 正方形），上限一律是正方形（卡宽）；行 / 列间距用 `GRID_GAP` / [GRID_ROW_GAP]。
            //  注意：画的是 [list]（目标态那份），不是外层的 `saved` —— 过渡期间退场那一侧
            //  拿到的正是旧列表，用 `saved` 会让它渲染成空网格。
            //  **列数按可用宽度算**（与动作网格同一个 `gridColumnCount`）：窄了落回一列、宽了
            //  自动多一列；固定两列时横屏 / 平板右边会空出一大片。算列数与分块必须是同一个数。
            BoxWithConstraints(Modifier.padding(horizontal = PAGE_MARGIN)) {
                val columns = gridColumnCount(maxWidth)
                Column {
                    list.chunked(columns).forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) Spacer(Modifier.height(GRID_ROW_GAP))
                        CommandTileRow(
                            TileRowSpec(
                                tileCount = row.size,
                                columns = columns,
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
                                // 上限**一律**是卡宽（正方形），单条与多条同一档（用户口径）：一列里长短
                                //  不一时，高的那几张会把同一行撑到两个正方形，网格看着像断了。
                                //  放不下的内容由卡片自己收 —— 单条摘要在 `maxLines` 上封顶、多条把装不下的
                                //  步骤换成一行「…」（见 `CommandTileSummary` / `summaryLineRows`），
                                //  不交给父级硬裁。
                                maxHeightRatio = 1f,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) { tileIndex ->
                            val cmd = row[tileIndex]
                            // 每格按 `key(位次, 命令名)` 认身份 —— 与步骤行 `key(item.id)` 同一个理由：
                            //  这一片是按**位次**排出来的，卡里的 `animateColorAsState` 与卡片槽位都按
                            //  位次记身份 ⇒ 删一条 / 改名之后后面的格子会继承前一格的位置，强调色从上一格
                            //  的颜色动画过去（一帧闪色）。
                            //  **必须带上位次**：命令名不保证唯一（手改 / 导入的同名两条），只按名字给 key
                            //  会让同一组里出现重复 key，Compose 直接抛异常崩掉宿主。
                            key(tileIndex, cmd.name) {
                                CommandTile(cmd, ui, tick, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一张「已保存的命令」卡。
 *
 *  交互走库的交互式 `Card`（自带 `onClick` 与涟漪）；`preciseClickAnchor`
 *  挂在外层，只看不消费（浮层菜单的落点由它给）。
 *
 *  这张卡只有轻点一个手势：长按不接，菜单只从右上角那颗「更多」进 ——
 *  卡片本身不大，长按与轻点抢同一片区域时（草稿脏了要先问一句）容易点错。
 *
 *  **不画「正在编辑」的选中态**：点开一条就跳回表单页去改它，列表本身不停在任何「选中」
 *  状态上，所以卡片既不画边框、标题与类型图标也不随草稿变色。
 */
@Composable
private fun SettingsPage.CommandTile(
    cmd: Command,
    ui: CommandUiState,
    tick: () -> Unit,
    /** 点开这一条之后去哪（跳回表单页）—— 灌草稿在这一层做，跳页由调用方做。 */
    onOpen: (Command) -> Unit,
) {
    // 每一张卡各自一份 `PreciseClickState`（按命令名做 key）：`anchorCoordinates`
    //  只有一格，共用一份会把触点算到别的卡上去。
    val tilePreciseClick = remember(cmd.name) { PreciseClickState() }
    // 右上角那颗「更多」另记一份：菜单要弹在**那颗字上**，不是整张卡上随便一点的位置。
    val dotsAnchor = remember(cmd.name) { PreciseClickState() }
    //  这一页（「命令列表」）没有禁用态：排序态只属于「多条」那张表单，进这一页时已经收掉了。
    Box(Modifier.fillMaxWidth()) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 行高由 [CommandTileRow] 统一定，这里填满行高即可。
                    .fillMaxHeight()
                    .preciseClickAnchor(tilePreciseClick),
            colors = commandTileColors(),
            pressFeedbackType = PressFeedbackType.None,
            showIndication = true,
            //  轻点这一张 = **看预览**（从这一片长出来的那一层）。改内容走顶行那颗铅笔 ——
            //  看是「看一眼就走」，改是「进表单」；以前轻点直接跳表单，想看一眼全文就得进编辑态。
            //  列表编辑态里轻点 = **勾选**（与「按键功能」页同一套）。
            onClick = {
                tick()
                if (CommandListEditState.editing) {
                    CommandListEditState.toggle(cmd.name)
                } else {
                    XLog.w("命令预览：${cmd.name}（轻点卡片）")
                    ui.detail = cmd
                }
            },
        ) {
            CommandTileBody(
                cmd = cmd,
                menuAnchor = dotsAnchor,
                onMenu = {
                    tick()
                    XLog.w("已保存命令菜单：${cmd.name}（右上角那颗「更多」）")
                    ui.menu = TileMenu(cmd.name, dotsAnchor)
                },
                onEdit = {
                    tick()
                    //  草稿里有没保存的改动时先问一句（见 [loadDraft]）—— 那时它**只挂确认窗**、
                    //  返回 false，所以这一下也**先别切页**：切页由那张窗确认之后自己发起。
                    //  否则窗还摆在屏幕上，页面已经跳到表单页、而表单里还是旧内容。
                    if (loadDraft(cmd)) {
                        // 灌完草稿要跳回**这一条所属那一类**的表单页（见调用方）——
                        //  停在列表页等于「点开了一条，眼前什么都没变」。
                        onOpen(cmd)
                    }
                },
            )
        }
        // 浮层挂在零宽内层盒子里（落点由触点决定，这里只是宿主）。
        CommandTileMenu(
            scope = this,
            cmd = cmd,
            ui = ui,
            tileAnchor = tilePreciseClick,
            onOpen = onOpen,
        )
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

/** 卡片内容：顶行（图标 + 两颗动作）、另起一行的名字、以及摘要。 */
@Composable
private fun SettingsPage.CommandTileBody(
    cmd: Command,
    /** 右上角那颗「更多」的触点锚点（菜单弹在它上面）。 */
    menuAnchor: PreciseClickState,
    /** 点那颗「更多」：开这一张卡的菜单（预览 / 测试 / 编辑 / 删除）。 */
    onMenu: () -> Unit,
    /** 点那颗铅笔：进编辑态（灌草稿 + 跳表单页，由调用方做）。 */
    onEdit: () -> Unit,
) {
    // 顶部对齐：顶行定高，同一行的标题永远是一条线；高度包住内容，下限由布局给。
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // 顶行：左边类型图标，右边那两颗动作键 —— **名字不在这行**（见下）。
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommandTileIcon(cmd)
            //  右边两颗：铅笔（进编辑态）在前、「更多」在后 —— 铅笔是**改**，菜单里还收着
            //  预览 / 测试 / 删除，越靠右越是「更多选择」的那一档。
            //  两颗都是 24dp 的格子（20dp 字形 + 2dp 内边距）、格间 2dp：**间距由排版给**，
            //  不是给某颗加偏移量 —— 偏移量只挪绘制，格子还占着原来的宽度，间距照样那么宽。
            //
            //  列表编辑态（见 `CommandListEditState`）里这一格换成**多选框**：与「按键功能」页
            //  那一套一致 —— 勾选框自己不挂点击，整张卡都是它的点击区。
            if (CommandListEditState.editing) {
                Checkbox(
                    state = if (cmd.name in CommandListEditState.picked) ToggleableState.On else ToggleableState.Off,
                    onClick = null,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TILE_ACTION_GAP),
                ) {
                    TileActionButton(
                        icon = PageIcons.CommandEdit,
                        label = I18n.get(Strings.COMMON_EDIT, "编辑"),
                        onClick = onEdit,
                    )
                    //  这颗就是原来右上角那颗「更多」（测试 / 编辑 / 删除都收进它的菜单）——
                    //  换成本地这版只为一件事：`MoreButton` 那格是 40dp 宽、字形居中，与左边那颗
                    //  之间必然空出 20dp 以上。菜单的落点仍然记在这颗字上（`menuAnchor`）。
                    TileActionButton(
                        icon = TablerIcons.Outline.Dots,
                        label = I18n.get(Strings.COMMON_MORE, "更多"),
                        onClick = onMenu,
                        anchor = menuAnchor,
                    )
                }
            }
        }
        //  名字**另起一行**、独占整张卡的宽度：原先挤在图标那一行，格子一窄就只剩前几个字
        //  （真机口径「命令名另起一行」）。它是这张卡的主键，值得自己一行。
        //  名字上下各留一档白就够（见 [TILE_TITLE_GAP]）：它下面紧跟的那一行（多条是间隔时长、
        //  单条是摘要）与它是一段，中间不再垫间距 —— 那一行自己行盒上沿那点白已经分开了。
        Spacer(Modifier.height(TILE_TITLE_GAP))
        CommandTileName(cmd)
        //  多条命令：步骤间隔是执行参数，放在名字下面当小字（用户口径「名称和 delay 换个位置」）。
        if (cmd.kind == CommandKind.SERIAL) {
            Spacer(Modifier.height(TILE_TITLE_GAP))
            CommandTileDelay(cmd)
        }
        //  摘要吃掉**剩下那一截**高度：卡高封在正方形（见布局里的 `maxHeightRatio`），摘要与名字、
        //  delay 抢的是同一块地方 —— 交给 `weight` 分，卡片自己不必猜「还剩几行」。
        //  多条那一档在里面再按行裁（见 [summaryLineRows]），单条靠 `maxLines`。
        Box(Modifier.weight(1f)) {
            CommandTileSummary(cmd)
        }
    }
}

/**
 * 卡片顶行那颗**小动作键**（放大镜）—— 尺寸与右上角那颗「更多」同一格，只是字形不同。
 *
 *  为什么不用库的 `IconButton`：那一档自带一圈自己的内边距与最小触达尺寸（48dp），
 *  摆在 20dp 高的顶行里会把整行撑高，卡片跟着长一截。这里要的是「与「更多」并排、
 *  高度一致」的一格，所以照 `MoreButton` 那套尺寸与手感来。
 */
@Composable
private fun TileActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    /** 浮层菜单的落点锚（只有「更多」那颗要：菜单弹在它上面）。 */
    anchor: PreciseClickState? = null,
) {
    //  振动走页面那条通用通道（[LocalTick]）—— 与卡片行、行尾动作同一份判据
    val tick = LocalTick.current
    Box(
        modifier =
            Modifier
                .size(TILE_ACTION_BOX)
                .then(if (anchor != null) Modifier.preciseClickAnchor(anchor) else Modifier)
                .clip(RoundedCornerShape(TILE_ACTION_CORNER))
                .clickable {
                    tick()
                    onClick()
                },
        //  字形**恒居中**：状态层与字形同心，按下去不会偏一半
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(TILE_ACTION_GLYPH),
        )
    }
}

/**
 * 顶行那两颗小动作键的格子边长 —— 24dp：20dp 字形 + 四周 2dp。
 *
 *  这一格**就是排版用的宽度**（不像 `MoreButton` 那样留 40dp 宽、把字形摆在正中再靠偏移量
 *  去挪）：两颗格子紧挨着排，间距由 `Row` 的 [TILE_ACTION_GAP] 给 —— 加偏移量只挪绘制，
 *  格子仍占着原来的宽度，两颗字看着照样远（真机口径「还是太远没有变化」）。
 */
private val TILE_ACTION_BOX = 24.dp

/** 格子里那颗字形的边长 —— 与「更多」原来那颗同档（20dp）。 */
private val TILE_ACTION_GLYPH = 20.dp

/**
 * 两颗动作键之间的间距 —— 加上格子各 2dp 的内边距，两颗字之间是 12dp。
 *
 *  2dp（字之间 6dp）时两颗字挤在一起，读起来像一个控件的两个部分（真机口径「两个按钮太近了」）；
 *  12dp 让两颗字之间空出一档白，仍是两颗独立的键。
 */
private val TILE_ACTION_GAP = 8.dp

/** 点击区圆角 —— 与「更多」、小标题同一档。 */
private val TILE_ACTION_CORNER = 8.dp

/**
 * 卡片顶行左段那颗**类型图标**（名字在下一行，见 [CommandTileName]）。
 *
 *  「随机图标配色」开着时，类型图标用**它自己那一色**；关着时退回卡片的内容色
 *  （`commandTileColors()` 里的 `onSurfaceContainer`，与摘要那一档同源）。
 *  单条命令按**自己的 root 档**取字形（su / 非 su 两颗字，见 `commandIcon`）：
 *  列表里要能一眼看出哪几条会提权。
 */
@Composable
private fun CommandTileIcon(cmd: Command) {
    val tileIcon = commandIcon(cmd.kind, cmd.steps.size, cmd.root)
    //  「随机图标配色」那一档同样要过渡：一次拨开关，整屏卡片的类型图标一起换色 ——
    //  硬切在这里是一整片跳，比单颗图标明显得多。关着时退回卡片的内容色（见 `commandTileColors`）。
    val iconTint by animateColorAsState(
        targetValue = tileIcon.tinted() ?: COUITheme.colorScheme.onSurfaceContainer,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CommandTileIcon",
    )
    Icon(tileIcon, null, Modifier.size(ROW_ICON_SIZE), tint = iconTint)
}

/**
 * 多条命令**步骤间隔时长** —— 名字下面那一行小字。
 *
 *  行首那枚小图标与下面摘要行同一支笔、同一档尺寸（[SUMMARY_REF_ICON] / [SUMMARY_REF_GAP]）：
 *  它就是「步骤间隔」这个参数自己的字形（[PageIcons.DelayStep]，一枚小时钟）—— 与摘要行里
 *  「间隔」那一步是同一颗图标，读起来是同一件事。
 *  文案走 [durationText]（全局唯一一处）。
 */
@Composable
private fun CommandTileDelay(cmd: Command) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            PageIcons.DelayStep,
            contentDescription = null,
            modifier = Modifier.size(SUMMARY_REF_ICON),
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(SUMMARY_REF_GAP))
        Text(
            // 1000ms 以上写成秒（`3 秒`），一秒以内是毫秒（`400 毫秒`）
            text = durationText(cmd.stepDelayMs),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            //  与行首那枚小图标竖着对中：行盒居中不等于墨迹居中，补一档光学修正
            //  （与摘要行、卡片标题同一支笔 —— 见 `opticalCenter`）
            modifier = Modifier.opticalCenter(COUITheme.textStyles.footnote1),
        )
    }
}

/**
 * 卡片上的**命令名** —— 独占一行，宽度吃满整张卡。
 *
 *  名字比页面里的行标题小一档（`headline1` 16sp → `body2` 14sp）：卡片本身窄，名字再和
 *  行标题一样大，两行名字就把格子塞满了。字号只在这一处定，光学修正跟着同一个 style 走。
 */
@Composable
private fun CommandTileName(cmd: Command) {
    val titleStyle = COUITheme.textStyles.body2
    Text(
        text = cmd.name,
        style = titleStyle,
        fontWeight = FontWeight.Medium,
        //  行数按类型分：单条那一档的摘要就是整段正文（要塞下三行），名字再折一行会把它挤掉；
        //  多条卡一行一步、步骤本身还能再裁（见 [summaryLineRows]），名字给两行不会把摘要挤没。
        maxLines = if (cmd.kind == CommandKind.SINGLE) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.opticalCenter(titleStyle),
    )
}

/**
 * 「已保存的命令」一条都没有时的空状态：一颗字形 + 一句话，**不带卡片背景**。
 *
 *  · 文案通用一句（不按当前那一栏分单条 / 多条）：两栏是同一个命令库的两份视图。
 *  · 不铺卡片：这块地方本来就没东西，画一张空壳卡像「卡在、内容没出来」。
 *  · 字形比「选择应用」页那几块空态小一档（那是整页空态，这里只是页内一段），取色同一份。
 */
@Composable
internal fun SavedCommandEmptyState() {
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
