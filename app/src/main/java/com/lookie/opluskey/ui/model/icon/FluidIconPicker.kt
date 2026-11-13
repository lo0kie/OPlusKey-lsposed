package com.lookie.opluskey.ui.model.icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.FluidCard
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.FrostedDialog
import com.lookie.opluskey.ui.row.AppScrollBar
import com.lookie.opluskey.ui.row.TitleSmallText
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/** 图标格子那一块最高长到哪 —— 到顶之后由这一格自己滚，不把整张窗撑到屏幕高。 */
private val ICON_GRID_MAX_HEIGHT = 420.dp

/** 一颗格子的边长与里面字形的大小 —— 六列排下来，手指点得中、一行也扫得过去。 */
private val ICON_CELL = 44.dp
private val ICON_GLYPH = 24.dp

/** 网格几列 + 格子之间的横竖间距 —— 高度按这两样算（见 [iconGridHeight]）。 */
private const val ICON_COLUMNS = 6
private val ICON_GAP = 4.dp

/** 滚动条与网格右边缘之间那一档 —— 它落在网格右侧的留白里，不压格子。 */
private val ICON_GRID_EDGE_GAP = 4.dp

/**
 * 这一块网格**该多高** —— 行数 × 格子 + 行间距，封一档 [ICON_GRID_MAX_HEIGHT]。
 *
 *  高度必须在这一层算出来（而不是交给 `matchParentSize` 跟网格量）：滚动条取的是约束里的
 *  `maxHeight`，而在 `Column` 里那个数是「剩下的空间」—— 比网格高，摆出来的滚动条会长出网格一截、
 *  thumb 落在窗中间（真机反馈）。定死高度之后网格与滚动条同高。
 *
 *  行数由图标总数推：图标表变了这里自动跟着变，不必再写一个数。
 */
private fun iconGridHeight(count: Int): Dp {
    if (count <= 0) return ICON_CELL
    val rows = (count + ICON_COLUMNS - 1) / ICON_COLUMNS
    val full = ICON_CELL * rows + ICON_GAP * (rows - 1)
    return full.coerceAtMost(ICON_GRID_MAX_HEIGHT)
}

/**
 * 胶囊图标选择器 —— 把模块在用的那批 Tabler 字形铺成网格，点一颗就**立刻渲染成 png 存下来**
 * 并把它的 id 交给调用方（命令里只记 id）。
 *
 *  壳走项目**通用那一档**（`FrostedDialog`：磨砂面板 + 标题 + 库的进出场）。原先这里是裸的
 *  `Dialog` + 一块不透明底 —— 与同页的确认窗 / 选择器摆在一起像两个应用的控件。
 *
 *  **没有「当前选中」这一档**：这一格点下去就是「换一颗」，关掉窗之后命令里那个 id 才是答案。
 *  给格子加一圈高亮只会让人以为还要再确认一次，而这里没有确认这一步。
 *
 *  预览用主色（弹层是浅底），落地的那份是白色（[exportPainter] 会把描边刷白）——
 *  两个场景的底不一样，各用各的看得清的颜色。
 *
 * @param show 开着没有 —— 原样递给壳，退场动画靠它（调用点别在外面判 true 再调）
 * @param onPick 点中一颗：**png 已经写好了**才回调（写不出去那一颗点了没反应，只在日志里说）
 */
@Composable
internal fun SettingsPage.FluidIconPicker(
    show: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layout = LocalLayoutDirection.current
    //  画图 + 落盘在 `exportPainter` 里自己 `withContext(Dispatchers.IO)`，这里只负责起一个作用域：
    //  回来那一句 `onPick` 改的是 Compose 状态，必须在主线程 —— 协程默认就在主线程恢复。
    val scope = rememberCoroutineScope()
    FrostedDialog(
        show = show,
        title = I18n.get(Strings.COMMAND_FLUID_ICON_TITLE, "图标"),
        onDismiss = onDismiss,
        //  标题行尾那一格：一共几颗可选。与入口行副标题是同一个数，但两处的用法不一样 ——
        //  那一行是「后面有多少东西」的预告，这一格是「现在看见的就是全部」的确认
        //  （网格到了 `ICON_GRID_MAX_HEIGHT` 会自己滚，滚动条不在窄面板上显眼）。
        //  这一格不参与标题的居中计算（见 `FrostedDialog` 的同名参数），所以只放很短的一格。
        //
        //  **套一对半角括号**（`(48)`）：这一格与标题在同一行上、只隔一段空白，裸数字会被读成
        //  标题的一部分（「图标 48」）；括号把「这是附注」这件事写明白。格式与分组标题那条
        //  `titleWithCount`（`响铃状态 (4)`）一致 —— 同一个界面里两种括号，像两套控件。
        //  样式走 `TitleSmallText`（这一格的本体），不自己拼 `Text`。
        titleTrailing = { TitleSmallText("(${FluidIcons.ALL.size})") },
    ) {
        //  这一块的高度**自己算**（[iconGridHeight]），不靠 `matchParentSize` 跟着网格量：
        //  滚动条的高度取的是约束里的 maxHeight，那个数在 Column 里是「剩下的空间」而不是
        //  「网格实际多高」—— 用它摆出来的滚动条会比网格长，看着像挂在窗中间（真机反馈）。
        //  高度定死之后网格与滚动条同高，`fillMaxHeight` 与 `align` 都落到同一个盒子里。
        val gridState = rememberLazyGridState()
        val gridHeight = iconGridHeight(FluidIcons.ALL.size)
        Box(
            modifier =
                Modifier
                    //  与选择器那一列同一个写法：`weight(1f, fill = false)` 让它在窗里按内容长，
                    //  封一档高之后自己滚（到顶不再把窗撑高）。
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .height(gridHeight),
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(ICON_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
                verticalArrangement = Arrangement.spacedBy(ICON_GAP),
                //  左右内缩补一档 `insideMargin`：正文（含标题）都从那条线起，格子不贴面板边。
                //  滚动条落在这段留白里，不压格子。
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = DialogDefaults.insideMargin.width),
            ) {
                items(FluidIcons.ALL, key = { it.first }) { (id, vector) ->
                    val painter = rememberVectorPainter(vector)
                    Box(
                        Modifier
                            .size(ICON_CELL)
                            .clip(RoundedCornerShape(12.dp))
                            //  退场那一段不再派发：那时窗已经要收了，点了也只会把命令改掉一半
                            .clickable(enabled = show) {
                                scope.launch {
                                    if (exportPainter(context, painter, density, layout, FluidCard.nameOf(id))) {
                                        onPick(id)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = vector,
                            contentDescription = id,
                            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(ICON_GLYPH),
                        )
                    }
                }
            }
            //  通用滚动条（`ui/row/AppScrollBar`）：常亮、按住滑块走通用振动、轨道两端留白，
            //  与浮层菜单那条、页面列表那条同一份实现。这里只给几何 —— 贴右、与网格同高。
            AppScrollBar(
                gridState = gridState,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        //  落在网格右侧那段留白里，与菜单里贴边的那条同一条位置口径
                        .padding(end = ICON_GRID_EDGE_GAP),
            )
        }
    }
}
