package com.lookie.opluskey.ui.model.icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.FluidCard
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.FrostedDialog
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            //  与选择器那一列同一个写法：`weight(1f, fill = false)` 让它在窗里按内容长，
            //  封一档高之后自己滚（到顶不再把窗撑高）。
            //  左右内缩补一档 `insideMargin`：正文（含标题）都从那条线起，格子不贴面板边。
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .heightIn(max = ICON_GRID_MAX_HEIGHT)
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
    }
}
