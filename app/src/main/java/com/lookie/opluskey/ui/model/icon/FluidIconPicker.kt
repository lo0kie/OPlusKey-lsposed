package com.lookie.opluskey.ui.model.icon

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.FluidCard
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.LocalTick
import com.lookie.opluskey.ui.kit.FrostedDialog
import com.lookie.opluskey.ui.kit.TitleSmallText
import com.lookie.opluskey.ui.row.AppScrollBar
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
    //  点格子那一下走全项目通用振动（`LocalTick`）：这一页是弹层、控件层不认 `SettingsPage`，
    //  只能从页面那层递下来的通道叫一声 —— 与卡片行、菜单行同一处。
    val tick = LocalTick.current
    //  画图 + 落盘在 `exportPainter` 里自己 `withContext(Dispatchers.IO)`，这里只负责起一个作用域：
    //  回来那一句 `onPick` 改的是 Compose 状态，必须在主线程 —— 协程默认就在主线程恢复。
    val scope = rememberCoroutineScope()
    FrostedDialog(
        show = show,
        title = I18n.get(Strings.COMMAND_FLUID_ICON_TITLE),
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
                                tick()
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

/**
 * 「随机图标配色」用的那一套色 —— **手挑的色相**，不是随机 RGB。
 *
 * ## 为什么不是 `Random.nextInt(0xFFFFFF)`
 *
 *  真随机在色相环上是均匀的，于是必然抽到大量「黄绿」「土黄」「灰紫」这类在浅色底上发脏、
 *  在深色底上发闷的颜色；两颗相邻的图标还会撞成几乎同色。随机配色要好看，**随机的是「用哪一个」，
 *  不是「什么颜色」** —— 颜色本身得先挑好。
 *
 * ## 挑法
 *
 *  · 色相：11 档，绕开 60°~120° 那一段（黄 / 黄绿在浅色底上最容易脏），其余按「冷色多、暖色少」排；
 *  · 饱和度 / 亮度：浅色底取 (0.58, 0.46) —— 够艳但不刺眼；深色底整体提亮到 (0.62, 0.66)，
 *    否则整片糊在深色背景里（对比度不够，看着像「没上色」）。
 *
 * ## 怎么保证「一次生成、永久一样」
 *
 *  颜色 = f(种子, 图标身份)。种子存在配置里（`GlobalConfig.iconTintSeed`），图标身份取
 *  `ImageVector.name` —— 同一颗图标每次算出来都一样，与开关、顺序、重组次数都无关。
 *  换一批只发生在「关掉再打开」那一次（那时会重新生成种子）。
 */
internal object IconTint {
    /** 挑好的色相（度）。刻意避开 60~120 的黄绿段。 */
    private val HUES = intArrayOf(212, 246, 268, 292, 322, 344, 14, 32, 172, 192, 142)

    private const val LIGHT_SATURATION = 0.58f
    private const val LIGHT_LIGHTNESS = 0.46f
    private const val DARK_SATURATION = 0.62f
    private const val DARK_LIGHTNESS = 0.66f

    /**
     * 这颗图标该用哪一个色相 —— `seed * 31` 与图标名的散列混合，再取模。
     *
     *  乘 31 是为了让相邻的种子把整个色环错开（直接取模的话，种子 +1 只会让所有图标
     *  一起挪一格，看着像「换了一套整体配色」而不是「重新洗过」）。
     */
    fun colorOf(
        seed: Int,
        key: String,
        dark: Boolean,
    ): Color =
        Color.hsl(
            HUES[Math.floorMod(seed * 31 + key.hashCode(), HUES.size)].toFloat(),
            if (dark) DARK_SATURATION else LIGHT_SATURATION,
            if (dark) DARK_LIGHTNESS else LIGHT_LIGHTNESS,
        )
}

/**
 * 「图标」那一行 —— **凡是某条东西（命令 / 常用命令）能带 `<id>|正文` 这种写法的地方，都画这一行**。
 *
 *  它只做两件事：点开网格选择器（[FluidIconPicker]）、把选中的 id 交回调用方。
 *  怎么把 id 写回去由调用方定（命令改 `form.single`，常用命令改清单里那一条），因为两处的落点
 *  不是同一个对象 —— 但**入口的形状、副标题、选择器**必须只有一份，否则同一次点选用两种说法。
 *
 *  **这一行不显示「现在用的是哪颗」**：选择器那一层本来就没有「当前选中」这一档
 *  （点一颗就是换一颗，关掉窗之后命令里那个 id 才是答案），入口行再摆一次当前值只是重复；
 *  副标题说的是「后面还有多少颗可选」，那是「值不值得点进去」的量。
 *
 * ## 为什么只出现在流体云上
 *
 *  这套图标是**胶囊左边那一格**的东西（`fluid:` 的 `logo`，见 `core/model/FluidCard`）：
 *  字形被渲染成 png 存进宿主的共享目录，执行时按 id 组出 `content://`。别的动作没有这个槽，
 *  给了也画不上去 —— 所以「显不显示这一行」由调用方按动作种类决定。
 *
 * @param show 这一行露不露 —— 交给 `CardList` 播进出场，调用方别在外面 `if`
 * @param enabled 整页闸关着时点不动
 * @param onPick 点中一颗：调用方把它写回去
 */
@Composable
internal fun SettingsPage.FluidIconRow(
    show: Boolean,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    visible = show,
                    //  行标题说的是「点这一行做什么」（选择图标），上面那句小标题说的是这一组是什么
                    //  （图标）—— 两句话各说一件事，别共用一条文案。
                    title = I18n.get(Strings.COMMAND_FLUID_ICON_PICK),
                    //  副标题讲**这一格后面有多少东西**（`当前有 48 个图标`）——它是「值不值得点进去」
                    //  的量，而不是「现在用的是哪一颗」：把 id 念成 `click` 对谁都没帮助，
                    //  想知道当前是哪颗，点开窗看一眼网格就够。
                    summary = I18n.format(Strings.COMMAND_FLUID_ICON_COUNT, FluidIcons.ALL.size),
                    //  行首是「图」本身（`photo`）—— 原先借的是那颗「点一下」的指针，
                    //  它讲的是手势、不是图。
                    icon = PageIcons.FluidIcon,
                    //  这一行也是「点进去还有一层」，与别处进下一层的行同一个记号。
                    //  画与颜色都由 `CardList` 管（按 enabled），调用点不再自己配一份。
                    arrow = true,
                    enabled = enabled,
                    onClick = { picking = true },
                ),
            ),
    )
    //  选择器**不**写成 `if (picking) { … }`：一写整棵子树消失，退场就成了硬切（同 `PageSheet` 那条）。
    //  壳自己按 `show` 播进出场，这里原样递进去。
    FluidIconPicker(
        show = picking,
        onPick = { id ->
            picking = false
            onPick(id)
        },
        onDismiss = { picking = false },
    )
}

/**
 * 把一颗 Tabler 字形画成 png，写进宿主的共享目录 —— `fluid:` 那边只管把这个文件转成 uri。
 *
 * ## 为什么渲染放在界面层
 *
 *  图标源是模块**已经在用**的 Tabler 字形，它是 Compose 的 `ImageVector`：只有界面层
 *  （`com.oplus.gesture.ui`）带 Compose 运行时，而执行 `fluid:` 的宿主进程没有 ——
 *  在那边引等于 `NoClassDefFoundError`。所以这里画好、落成文件，宿主侧只认文件。
 *
 * ## 形态
 *
 *  36×36、**透明底**、纯白描边：胶囊本身有底色（系统那颗 `op_ic_silence.svg` 也是这么做的，
 *  只有白色 path、没有底板），所以不铺底。`tintColor` 给白，是把 Tabler 自带的黑色描边整体刷白。
 *
 * ## 什么时候画
 *
 *  **只在用户点中一颗的时候画**（选择器那道网格，见 `FluidIconPicker`）：命令里记 id，
 *  这里按 id 落一个 `okey-fluid-<id>.png`，宿主执行时按同一个 id 找回来。
 *  「没选图标」不留文件、也不画默认那颗 —— 那种命令（`fluid:正文`）执行时根本不要图标。
 *
 * ## 为什么是 `suspend`，且自己 `withContext`
 *
 *  画一张位图再落盘是几十毫秒起步（首次还有 `mkdirs`），而唯一的调用点是网格里那一下点击
 *  —— 落在主线程上就是当场掉帧。**让函数自己保证这件事**，调用方不必记得包一层：
 *  上一版把 `withContext` 放在调用点（首页那次预热）里，那个调用点撤掉之后，这一段就
 *  裸在主线程上了（`tools/audit-perf.py` 的判据 1 会直接报出来）。
 *
 *  `painter` 只能在 Composable 里 `rememberVectorPainter` 出来，所以由调用方给。
 */
internal suspend fun exportPainter(
    ctx: Context,
    painter: Painter,
    density: Density,
    layout: LayoutDirection,
    name: String,
): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val size = Size(FluidCard.ICON_SIZE.toFloat(), FluidCard.ICON_SIZE.toFloat())
            val image = ImageBitmap(FluidCard.ICON_SIZE, FluidCard.ICON_SIZE)
            CanvasDrawScope().draw(density, layout, Canvas(image), size) {
                with(painter) { draw(size) }
            }
            //  落点：宿主的 `files/share_images/` —— 同包同 uid，界面层进程有写权限。
            val dir = File(ctx.filesDir, FluidCard.SHARE_DIR)
            if (!dir.exists() && !dir.mkdirs()) return@withContext false
            FileOutputStream(File(dir, name)).use {
                whiten(image.asAndroidBitmap()).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Throwable) {
            //  路径直接拼，不再为了一句日志去 stat 一次目录（那又是一处同步 IO）
            XLog.w("写胶囊图标失败（${FluidCard.SHARE_DIR}/$name）", e)
            false
        }
    }

/**
 * 把画好的图标整体刷成白色（保留 alpha）。
 *
 *  Tabler 的 `ImageVector` 自带的是**黑色描边**，而 `rememberVectorPainter(image)` 那个重载
 *  没有 `tintColor` 参数（带 tint 的那个重载要的是 `Dp` 尺寸）。与其去凑重载，不如画完再刷：
 *  `SRC_IN` 只覆盖不透明像素，描边变白、背景仍然透明。
 */
private fun whiten(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawBitmap(src, 0f, 0f, null)
    canvas.drawColor(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
    return out
}
