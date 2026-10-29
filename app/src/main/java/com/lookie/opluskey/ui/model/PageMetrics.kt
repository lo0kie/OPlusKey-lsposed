package com.lookie.opluskey.ui.model

import androidx.compose.foundation.layout.PaddingValues
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
 * [SliderRow] 那一行的几何契约 ——「点轨道跳值」（[tapSliderToSeek]）按这几笔账圈出滑杆占的那条带子：
 * [SLIDER_SIDE_INSET] / [SLIDER_BOTTOM_INSET] 是行内容的内边距（`insideMargin`），
 * [SLIDER_ICON_INSET] 是行首图标额外挤开的那一段。
 */
internal val SLIDER_SIDE_INSET = 16.dp

internal val SLIDER_BOTTOM_INSET = 10.dp

/**
 * 行首图标把滑杆挤开的距离 —— 图标本身 22dp + 库在图标与标题之间固定的那 8dp（`Component.kt` 的 `spacerPx`）。
 *
 * [SliderRow] 的滑杆按它左内缩，于是滑杆与标题文字同一个左边界；「点轨道跳值」的账里也要带上它。
 */
internal val SLIDER_ICON_INSET = 30.dp

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
 * 「添加命令」选择器浮层的最大高度 —— 取 320dp。
 *
 * COUI 的 `dropdownPositionProvider` 是「下方放不下翻到上方，两边都放不下就居中」，居中会把浮层压到屏幕中间、盖住锚点；
 * 320dp 下两种情形都能翻到锚点某一侧。行高 48dp + 组标题 32dp ⇒ 一屏约 6~7 行，其余滚动（`ListPopupColumn` 自带滚动）。
 */
internal val STEP_PICKER_MAX_HEIGHT = 320.dp

/**
 * 「按键行为」网格的列数 —— 取 2：机身 363dp 宽下每格 ≈163dp（焦点圈不占宽度），14sp 放得下最长的一行；
 * 16sp（库 `title` 槽位写死那档）会被省略号截掉，所以 [ActionTile] 用 content 重载自己放 `Text`。
 */
internal const val COLUMNS = 2

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
 * 「已保存的命令」卡片里图标到标题的间距 —— 卡片高度至少等于宽度、步骤多时会跟着长高，
 * 所以这里只是排版间距，不承担纵向预算。
 */
internal val TILE_TITLE_GAP = 8.dp

/**
 * 「已保存的命令」卡片里，摘要行**每一步**行首小图标的边长 —— 比步骤行的
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

/**
 * 种类标签胶囊整体**往下让**的这一档 —— 0.85dp（560dpi 下 3px）。
 *
 *  胶囊里的字按行盒竖直居中，
 *  但胶囊**自己**与旁边那个 16sp 大标题还差一档：真机量到标题墨迹中线在 2021px、
 *  胶囊中线在 2018px —— 差 3px，看着就是「标签整体偏高一点点」。
 *
 *  这个数**跟着标题那档字号走**：标题改成别的档位要重新量（它是「16sp Medium 的墨迹中线
 *  − 11sp Regular 胶囊中线」的差，两个字体文件的 descent 不一样，不能按字号等比推）。
 */
internal val APP_KIND_TAG_SINK = 0.85.dp

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
 * 库没有「每项图标尺寸」参数，只能从 `DropdownItem.icon` 的 `(Modifier) -> Unit` 槽位补（见 [ResetMenu]）。
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
