package com.lookie.opluskey.ui.model

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.SliderDefaults

// 尺寸 / 边距 / 行数 / 触达区（拆分后剩下的一档）。 —— 从 PageConst.kt 拆出（同包搬迁，调用点不变）。

/**
 * 页面共享常量（尺寸、文案、动画时长、色板）。
 */

internal const val STEP_MS = 50

/**
 * 「一颗圆形图标键」的触达区边长 —— 圆角取它的一半（= 圆形）。
 *
 *  用在两处：顶栏那几颗（返回 / ⋮ / 添加 / 重置），以及**抽屉标题行右端**那颗
 *  （`PageSheet` 的 `endAction`：新建、删除）。
 *
 * 库 `IconButton` 的按压高亮是画在 `defaultMinSize` 那一层的 squircle，所以高亮圈多大完全由这两个数决定
 * （库默认 48dp / 圆角 24dp）—— **不给这两个数就会大一圈**，摆在标题行右端比标题还高一截。
 */
internal val HEADER_ICON_SIZE = 40.dp

/**
 * 顶栏上**并排两颗**圆键之间的距离（只有「按键绑定」页那一处：设置键 + ⋮）。
 *
 *  取 8dp：库那排按钮自己不留间隙（`Arrangement.End` 一个挨一个），贴在一起时两颗圆底的
 *  描边会连成一条线、读起来像一个控件。8dp 恰好让两圈描边分开又不把这一组推得太宽。
 */
internal val HEADER_ICON_GAP = 8.dp

/**
 * 顶栏里**满格字形**的边长（垃圾桶 / 对勾这类画满一整格的）。
 *
 *  Tabler 那套图标的画布都是 24dp，但**墨量**差得远：「+」「⋮」「<」只占中间一小块，`trash`
 *  几乎画到四边。同样摆进 40dp 的圆键里，满格那颗看着就比旁边大一号（真机口径「删除图标这么大」）。
 *
 *  所以满格字形显式收一档到 20dp —— 抽屉标题行右端那颗也是同一个坑、同一档尺寸
 *  （`PageSheet` 的 `SHEET_ACTION_ICON`），两处不该各写一个数。
 */
internal val HEADER_FULL_ICON_SIZE = 20.dp

/**
 * 磨砂浮层的模糊半径（dp，给 `blur(radiusX, radiusY)` 之前换算成 px）—— 顶栏与浮层菜单共用这一个值。
 *
 * 取 24dp：顶栏高 107dp，再大把底下滚过的文字糊成一片，再小又和没糊差不多。
 */
internal val FROSTED_BLUR_RADIUS = 24.dp

/**
 * 浮层菜单面板的圆角 —— 库 `ListPopupContent` 的 `PopupCornerRadius`（12dp，对应 COUI
 * `couiRoundCornerM`），库内是 internal，这里照抄一份只为**磨砂取样收边**：那一层圆角**不裁子级**，
 * 不自己收边的话糊出来的方块会盖过面板的圆角。
 */
internal val POPUP_PANEL_RADIUS = 12.dp

/**
 * 细滚动条那条窄带的宽度 —— 浮层菜单与「更新说明」弹窗共用这一份。
 *
 *  不能按库的触摸目标（48dp）给：这一层压在可点内容上面，它有多宽就吃掉多宽。
 *  12dp 只占右内边带里的一小条，菜单行与正文都碰不到；拇指本身 3dp + 右端留白，量起来仍够点中。
 */
internal val SCROLLBAR_WIDTH = 12.dp

/**
 * [SliderRow] 那一行的几何契约 ——「点轨道跳值」（[tapSliderToSeek]）按这几笔账圈出滑杆占的那条带子：
 * [SLIDER_SIDE_INSET] / [SLIDER_BOTTOM_INSET] 是行内容的内边距（`insideMargin`），
 * [SLIDER_ICON_INSET] 是行首图标额外挤开的那一段。
 */
internal val SLIDER_SIDE_INSET = 16.dp

internal val SLIDER_BOTTOM_INSET = 10.dp

/**
 * 行首图标把滑杆挤开的距离 —— 图标本身 24dp（Tabler 的默认视口）+ 图标右侧那两道 8dp：
 * 库在图标与标题之间固定的 8dp（`Component.kt` 的 `spacerPx`），加上 [ROW_START_ICON_GAP]
 * 补到左边距的那 8dp。
 * **[ROW_START_ICON_GAP] 改了这里要跟着改**（写成字面量是因为它定义在本文件更后面，
 * 直接引用会踩到顶层属性「先初始化后声明」）。
 *
 * [SliderRow] 的滑杆按它左内缩，于是滑杆与标题文字同一个左边界；「点轨道跳值」的账里也要带上它。
 */
internal val SLIDER_ICON_INSET = 40.dp

internal val SLIDER_INSIDE_MARGIN =
    PaddingValues(
        horizontal = SLIDER_SIDE_INSET,
        vertical = SLIDER_BOTTOM_INSET,
    )

/** 卡片组首尾行在 `BasicComponent` 里额外补的上下边距（`coui_list_card_head_or_tail_padding`）。 */
private val CARD_EDGE_PADDING = 2.dp

/** 「点轨道跳值」认作滑杆的那条带子的高度。 */
internal val SLIDER_TAP_ZONE = SliderDefaults.MinHeight + SLIDER_BOTTOM_INSET + CARD_EDGE_PADDING

/**
 * 「点轨道跳值」要的那条轨道几何 —— 手势节点边缘到轨道两端让出多少，以及贴着底边多高一条算滑杆。
 *
 *  手势挂在**外层节点**上（不挂滑杆自己的 `Modifier`，见 [com.lookie.opluskey.ui.tapSliderToSeek]），
 *  节点比滑杆大；两端让位与带子高度都是「那个节点的排版」决定的，所以按调用点各给一份，
 *  手势本身只认这三个数。
 */
internal class SliderTapTrack(
    val startInset: Dp,
    val endInset: Dp,
    val zone: Dp,
)

/** 滑杆行（[com.lookie.opluskey.ui.SettingsPage.SliderRow]）那份 —— 行内边距 + 行首图标。 */
internal val SLIDER_ROW_TAP_TRACK =
    SliderTapTrack(
        startInset = SLIDER_SIDE_INSET + SLIDER_ICON_INSET,
        endInset = SLIDER_SIDE_INSET,
        zone = SLIDER_TAP_ZONE,
    )

/**
 * 「添加命令」选择器浮层的最大高度 —— 取 320dp。
 *
 * COUI 的 `dropdownPositionProvider` 是「下方放不下翻到上方，两边都放不下就居中」，居中会把浮层压到屏幕中间、盖住锚点；
 * 320dp 下两种情形都能翻到锚点某一侧。行高 48dp + 组标题 32dp ⇒ 一屏约 6~7 行，其余滚动（`ListPopupColumn` 自带滚动）。
 */
internal val STEP_PICKER_MAX_HEIGHT = 320.dp

/**
 * 焦点账本用的**基准列数** —— 取 2。
 *
 *  网格本身按宽度算列数（见 [gridColumnCount]），这个常数只剩「初值」这一处用它：
 *  `BindingGridState.columns` 的初始值（测量出来之前按它排），以及列数算不出来时的下限。
 *  机身 363dp 宽下每格 ≈163dp（焦点圈不占宽度），14sp 放得下最长的一行；16sp
 *  （库 `title` 槽位写死那档）会被省略号截掉，所以格子（`ActionTile`）用 content 重载自己放 `Text`。
 */
internal const val COLUMNS = 2

/**
 * 网格**一格的最小宽度** —— 列数按它算：可用宽度除一下、向下取整，最少一列。
 *
 *  取 150dp 是照 [COLUMNS] 那条注释反推的：363dp 机身在 `PAGE_MARGIN` 之后剩约 331dp，
 *  331 / 150 = 2.2 ⇒ 仍是两列（与改之前一模一样）；横屏 / 平板 / 大字模式下宽到 450dp 才会变三列。
 *  窗口窄到一格都放不下两列时（分屏、超大字号）落回**一列**，不硬塞 —— 那时两列每格只有百来 dp，
 *  14sp 的标题会被省略号截掉。
 */
internal val GRID_MIN_TILE_WIDTH = 150.dp

/**
 * 一行放几格 —— **按「格 + 间距」算**，不是只拿格宽去除；向下取整，**最少一列**。
 *
 *  n 格之间要空掉 n-1 条 [gap]，只除格宽会多算出一列：装出来的格子实际比
 *  [GRID_MIN_TILE_WIDTH] 窄一截（差的那一点正是被间距吃掉的那条），窄屏上尤其明显 ——
 *  文字被挤成两行、图标挤到边上。口径：`(宽 + 间距) / (格 + 间距)`，
 *  等价于「最后一格后面没有间距」。
 *
 *  不拿 [COLUMNS]（2）当下限：窗口窄到一格都塞不下两列时（分屏 / 超大字号）硬排两列，
 *  每格只剩百来 dp，标题全被省略号截掉 —— 那种宽度下排成一列反而读得全。
 *
 *  动作网格与「命令列表」网格共用这一份算式：分开写迟早一边改了另一边没改，同一个页面里
 *  两种排法。算列数与分块必须用**同一个**返回值，否则最后一行会错位。
 *
 * @param availableWidth 已经扣掉页面左右边距之后的可用宽度
 */
internal fun gridColumnCount(
    availableWidth: Dp,
    gap: Dp = GRID_GAP,
): Int {
    val step = (GRID_MIN_TILE_WIDTH + gap).value
    if (step <= 0f) return 1
    return ((availableWidth.value + gap.value) / step).toInt().coerceAtLeast(1)
}

/**
 * 首页「按键行为」卡里「前台转小窗时使用动画」那一行**是否露出来** —— 现行 `false`（先隐藏）。
 *
 *  收起来的理由是这一档还没收敛：动画终点几何是竖屏标定值、横屏播不了，竖屏落点也还没定。
 *  只收**入口**：配置字段（`GlobalConfig.miniWindowAnim`）与执行侧（`MiniWindowLauncher`）
 *  都原样留着，默认值本来就是关 —— 改回 `true` 那一行立刻回来，不必动别处。
 */
internal const val SHOW_HOME_MINI_ANIM = false

// 「一张卡有几行」不再写成常量：行的位置（首行 / 中间 / 末行）与分隔线现在由 `CardList`
// 遍历时自己算（见 `ui/CardRow`），条件行用 `buildList { if (…) add(…) }` 组出来就是真相 ——
// 数错了会让卡片首尾圆角错位的那一类账，整类消失了。

/**
 * 「自定义命令」卡片里图标行到名字的间距 —— 卡片高度至少等于宽度、步骤多时会跟着长高，
 * 所以这里只是排版间距，不承担纵向预算。
 *
 *  取 4dp（比行标题那一档紧）：名字是这张卡的主键，上面那截白一大，整张卡看着就空。
 *  名字下面不再垫间距，摘要紧跟着它（见 `CommandTileBody`）。
 */
internal val TILE_TITLE_GAP = 4.dp

/**
 * 「自定义命令」卡片里，摘要行**每一步**行首小图标的边长 —— 比步骤行的
 * [ROW_ICON_SIZE]（22dp）小一档，它跟着的是摘要那行小字（`footnote1`）。
 */
internal val SUMMARY_REF_ICON = 14.dp

/** 那枚小图标与它后面那行摘要之间的间距。 */
internal val SUMMARY_REF_GAP = 4.dp

/**
 * 页面左右边距 —— 取 12dp，网格列距与分段控件共用同一值。
 *
 * 不用 16dp：网格那边 16dp 撑不下（机身 363dp 宽，中间槽位只剩 ~63dp，而最长的一项「锁屏 / 电源」
 * 在 14sp 下要 ~70dp）。
 */
internal val PAGE_MARGIN = 12.dp

/**
 * 各页内容区底部 padding —— 统一取 32dp（各页不统一的话翻页时会看出来）。
 */
internal val PAGE_BOTTOM_PADDING = 32.dp

/**
 * 内容区距顶栏底边的那一档 —— 8dp。
 *
 * 补不补看首行是什么：首行是卡片的那一页（「选择应用」）要补一档，因为它没有
 * `SmallTitle` 自带的上内边距；首行是 `SmallTitle` 的那几页不补。两条固定带只带下边这一档。
 */
internal val PAGE_TOP_PADDING = 8.dp

/**
 * 「选择应用」页列表头部占的项数 —— 1（「顶栏让位 + 固定搜索带」那一项）。
 *
 * 任何按下标的滚动（进场定位到已选应用）都要加上这个偏移。
 */
internal const val APP_LIST_HEADER_ITEMS = 1

/**
 * 「打开应用」页每行定死的高度 —— 只为让行首那枚应用图标能整高。
 *
 * 图标要 `fillMaxHeight().aspectRatio(1f)` 才跟着行高走，而 `fillMaxHeight()` 在无界约束下退化成 0
 * （`LazyColumn` 的项无界）⇒ 行高必须先定死。64dp 是这一行自然高度向上取整；代价是系统字号调大时行不会变高。
 */
internal val APP_ROW_HEIGHT = 64.dp

/**
 * 「往下滚就藏、往上滚就露」判据的方向翻转门槛 —— 取 0.5dp（真机 560dpi 上是 2px）。
 *
 * 不设门槛时手指轻抖就会把 FAB 翻得一闪一闪，门槛太大又会让慢速拖动判不出来。
 */
internal val FAB_SCROLL_DIRECTION_THRESHOLD = 0.5.dp

/**
 * 「选择应用」页那两处状态图的边长 —— 32dp（加载转圈与空态大图标同档，两者不同框）。
 *
 * 转圈不能只改 `size`：库按 26dp 画布推出来的三个数（ring 24.14 / stroke 1.857）得按同一比例一起放大，
 * 只放大画布等于只多留白、圈没变大。
 */
internal val APP_STATE_ICON_SIZE = 32.dp

/** 那两处状态图与下面那句话之间的间距 —— 12dp，三种形态共用。 */
internal val APP_STATE_TEXT_GAP = 12.dp

/**
 * 应用名右边那枚「模块 / 系统 / 用户」小标签（[AppKindTag]）的间距与外观。
 *
 * 底色走**文字色的低透明度**，所以颜色只有一处来源：三档各给一个色（见 `appKindTagTint`），
 * 用户 = 中性、系统 = 主题蓝、模块 = 下面那对紫。
 */
internal val APP_KIND_TAG_GAP = 6.dp

internal val APP_KIND_TAG_RADIUS = 4.dp

internal val APP_KIND_TAG_INSIDE = PaddingValues(horizontal = 5.dp, vertical = 1.dp)

/**
 * 应用行里标题与摘要之间的间距 —— 2dp，与库 `support_preference_margin_between_line` 同值；
 * 这一页标题行要自己排（往名字右边塞标签），所以这个数自己写一份。
 */
internal val APP_TITLE_SUMMARY_GAP = 2.dp

/**
 * 网格的横向间距：页面边距与列距共用同一个值（[PAGE_MARGIN]）；行距另见 [GRID_ROW_GAP]。
 * 焦点圈画在格子之上、不占空间，格子占满分配宽度，不需要留补偿。
 */
internal val GRID_GAP = PAGE_MARGIN

/** 网格的行距 —— 与横向间距 [GRID_GAP] 同值（改了一边另一边跟着变）。 */
internal val GRID_ROW_GAP = GRID_GAP

/**
 * 两张**相邻卡片**之间的间距 —— 取 8dp。
 *
 *  用在「清单 + 末尾那一行『添加』」拆成两张卡的地方（按键列表 / 组合按键 / 命令步骤），
 *  以及「独立设置」页那颗总开关与它下面那组设置条目之间：它们说的是同一件事的两半
 *  （这一列现在有什么、再怎么加一个；这一档算不算数、算数时是什么样），所以比网格行距
 *  （[GRID_ROW_GAP] 12dp）紧一档 —— 12dp 会把这两张读成互不相干的两块。
 */
internal val CARD_GAP = 8.dp

/**
 * 浮层菜单每项图标的尺寸 —— 取 22dp，与全页行首图标 [ROW_ICON_SIZE] 同一档。
 *
 * 库的规格 24dp（`DropdownDefaults.IconMinSize`）是给两行菜单项配的，这里两行都是单行 16sp 正文，
 * 24dp 比字还高。曾经取 20dp，但菜单图标比网格格子 / 步骤行 / 输入框行首那一档（22dp）小一格，
 * 同一页里来回看是「菜单的图标偏小」（用户口径：放大 2dp）。库的图标槽本身按 24dp 留位，
 * 22dp 仍在格里，不挤标题。
 *
 *  仍留一个独立常量而不是直接引用 [ROW_ICON_SIZE]：浮层行高比网格格子矮得多，将来若要单独
 *  回调这一档，有名字可改。
 *
 * 库没有「每项图标尺寸」参数，只能从 `DropdownItem.icon` 的 `(Modifier) -> Unit` 槽位补（见 [HomeHeaderMenu]）。
 */
internal val MENU_ICON_SIZE = 22.dp

/**
 * 行尾那颗指示图标（右箭头 / 选中圈 / 入口字形）的边长 —— 取 24dp，
 * 与库那颗 `COUIIcons.Basic.ArrowRight` 同为 24dp 下折线占 12dp 的字形。
 *
 *  全页凡是「行尾摆一颗图标」的地方都取它（首页按键绑定、按键绑定网格的入口指示、
 *  选择应用的选中指示、关于页行尾箭头）。原来这几处各写一遍 `24.dp`，改一处不联动。
 */
internal val ROW_END_ICON_SIZE = 24.dp

/**
 * 「捐赠打赏」窗口里那张收款码的边长 —— 二维码要够大才扫得动（也是那个窗口的主要面积）。
 *
 *  图还没有时（`assets/donate/<id>.webp` 不在包里）退成品牌字形，用的是同一个尺寸。
 */
internal val DONATE_CODE_SIZE = 220.dp

/**
 * 行首图标的边长 —— 全页「22dp 图标纪律」。
 *
 *  网格格子、步骤行、输入框行首、空状态那一枚都取它：同一个页面里行首图标忽大忽小，
 *  比整体偏大偏小更明显。原来几处各写一遍 `22.dp`（或各留一个私有常量）。
 */
internal val ROW_ICON_SIZE = 22.dp

/**
 * 卡片行里行首图标**右侧补的那一档** —— 8dp。
 *
 *  卡片行的左右内边距都是 16dp（库的 `BasicComponentDefaults.InsideMargin`），而库在 `startAction`
 *  与文字之间只留 8dp（`Component.kt` 里那个 `spacerPx`）⇒ 图标左边看着比右边宽一倍，整行偏右。
 *  补上这 8dp，图标两侧的视觉留白才一样宽。
 *
 *  它不是「图标与文字的间距」，而是**把这处间距拉齐到左边距**的差额；行内边距若变了，
 *  这个数要跟着重算。
 */
internal val ROW_START_ICON_GAP = 8.dp

/**
 * 卡片行里**标题左缘**距卡片左缘多少 —— 56dp。
 *
 *  行内边距 16 + 行首图标那一列 24（Tabler 图标的默认视口）+ 库在图标后固定的 8（`spacerPx`）
 *  + 上面那档 [ROW_START_ICON_GAP] 8。行间那条细分隔线的左端按它缩进，线才从**标题最左处**起，
 *  不会伸进图标那一格里。
 */
internal val ROW_TITLE_INSET = 56.dp

/**
 * 双行整块要往下挪多少 —— 与 [Modifier.opticalBlockCenter] **同一把尺子**（同一个缓存、同一个
 * 偏差常量），给「行本身不能挪」的那一处用。
 *
 *  开关行的标题 / 副标题由库（`BasicComponent`）内部画，插不进修饰符，原先只能把**整行**挪；
 *  而整行一挪，可点区域与按住时的波纹跟着挪，波纹的上沿于是与上一行让出一道缝（真机口径）。
 *  那一处改成「上下内边距互相让」：上边 +这个值、下边 −这个值 —— 内容照样下移同样多，
 *  行高与可点区域一动不动。
 *
 *  为什么挪在几何这一族（`PageMetrics`）而不是 `TextMetrics`：那边是按行盒量的几张表，
 *  文件级函数已经顶到 detekt 的线；这里只多一个「同一把尺子的出口」。
 */
@Composable
internal fun opticalBlockBias(
    first: TextStyle,
    last: TextStyle = first,
): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val dy = remember(first, last, density) { blockDy(first, last, density, measurer) }
    val bias =
        remember(first, last, density) {
            if (first == last) 0 else with(density) { BLOCK_CENTER_BIAS.roundToPx() }
        }
    return with(density) { (dy + bias).toDp() }
}
