package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.AppTheme
import com.lookie.opluskey.ui.model.MENU_ICON_SIZE
import com.lookie.opluskey.ui.model.MENU_ROW_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.MENU_ROW_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.POPUP_SURFACE_DARK
import com.lookie.opluskey.ui.model.POPUP_SURFACE_LIGHT
import com.lookie.opluskey.ui.model.isDarkScheme
import com.lookie.opluskey.ui.model.opticalCenter
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 浮层菜单那一族：分隔线、配色、菜单项模型、菜单本体与其中一行。

/**
 * 浮层菜单两项之间的细线 —— 顶栏 ⋮ 菜单、已保存命令的长按菜单、「添加命令」选择器都用它。
 *
 * 左端从**标题文字的左缘**起（内边距 16 + 行首图标 24 + 图标后那一档 12 = 52dp，
 * 见 [MENU_TITLE_INSET]）：线与每一行的标题同一条竖线，图标那一格留空。
 * 右端仍让出行尾那一格（对勾 / 右箭头 / 提示），取库的内边距。
 *
 * 颜色自己给（见 [popupDividerColor]）：面板磨砂后半透明，库默认那一档暗色下几乎看不见。
 *
 * @param modifier 透给库的 `HorizontalDivider`，与这里自己的左右内边距叠加（默认无）。
 */
@Composable
internal fun PopupRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier =
            modifier.padding(
                start = MENU_TITLE_INSET,
                end = DropdownDefaults.InsideHorizontalPadding,
            ),
        color = popupDividerColor(darkAlpha = MENU_ROW_LINE_DARK_ALPHA, lightAlpha = MENU_ROW_LINE_LIGHT_ALPHA),
    )
}

/**
 * 浮层菜单里**小标题下面**那条线 —— 与 [PopupRowDivider] 同一档颜色与粗细，左端**放宽**到
 * 面板自己的内边距（`InsideHorizontalPadding`），也就是把行首图标那一格一起画进去。
 *
 *  为什么不与行间那条共用缩进：小标题自己顶在面板左缘，线却从「行标题」那条竖线起，
 *  看上去是「标题下面只划了半截」。右端与左右留白仍与行间那条一致 —— 只放宽到面板边距，
 *  不贴着面板两侧画满。
 */
@Composable
internal fun PopupSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = DropdownDefaults.InsideHorizontalPadding),
        color = popupDividerColor(darkAlpha = MENU_ROW_LINE_DARK_ALPHA, lightAlpha = MENU_ROW_LINE_LIGHT_ALPHA),
    )
}

/**
 * 浮层菜单里的小标题 —— 排版与库的 `DropdownHeaderItem` 同一条（11sp Medium、左右
 * `InsideHorizontalPadding`），但**上下留白上下同值、且比库那版更松一档**：
 *
 *  库那版上下各 8dp + 最小高 32dp，且首条又额外拿 `FirstLastVerticalPadding`（10dp）只加在
 *  上沿 —— 加上光学修正把墨迹往下压的那一档，首条看着「上多下少」。这里不分首条，
 *  上下都按 [POPUP_SECTION_TITLE_GAP]；那一档按用户口径**再加宽**：小标题是分组的分界，
 *  上下松一点，组与组之间才看得出「这是另一段」。
 *
 * @param count 标题后面那个括号计数（`null` = 不画）。只有「点开一格之后列出它的子动作」那张
 *   菜单给 —— 一眼看出这一格底下有几档，不必先数一遍。别的菜单（顶栏 ⋮、长按命令、条件取值）
 *   各自的条数由它们自己那一层说，不在这里重复。
 */
@Composable
internal fun PopupSectionTitle(
    text: String,
    count: Int? = null,
) {
    // 与库那一版**同一份样式**：只取 `footnote2` 的字号（11sp）+ Medium —— 库那版也是只传
    // `fontSize`，**不带** `footnote2` 的 `lineHeight`（1.143em，比中文字体的自然行高还小，
    // 套上去字形会溢出那一行盒）。光学修正必须按**实际渲染的这份样式**量，量错了修正量就偏。
    val style = TextStyle(fontSize = COUITheme.textStyles.footnote2.fontSize, fontWeight = FontWeight.Medium)
    //  计数**直接拼进标题**（`响铃状态 (4)`），与标题同一份样式、不做任何差异处理：
    //  它本来就是同一句话的一部分（用户口径「改成和标题一样的，直接拼在标题里」）。
    //  拼成一串而不是并排两个控件，还顺带解决两件事：位置天然紧随，不必靠间距去凑；
    //  标题长到要省略时括号跟着一起省略，不会出现「标题被截了、计数孤零零贴在右边」。
    val shown = if (count == null) text else "$text ($count)"
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DropdownDefaults.InsideHorizontalPadding)
                .padding(vertical = POPUP_SECTION_TITLE_GAP),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = shown,
            style = style,
            color = DropdownDefaults.dropdownColors().headerColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            //  这一格是**竖居中**（`CenterStart`）：行盒居中不等于墨迹居中，补一档光学修正
            //  （与页面里那些单行标题同一支笔 —— 见 `opticalCenter`）
            modifier = Modifier.opticalCenter(style),
        )
    }
}

/** 小标题上下那档留白 —— 10dp（库那版 8dp；用户口径要更松，见 [PopupSectionTitle]）。 */
private val POPUP_SECTION_TITLE_GAP = 10.dp

/**
 * 浮层菜单里标题左缘距面板左缘的距离 —— 52dp。
 *
 *  库的 `InsideHorizontalPadding` 16 + 行首图标 24（Tabler 的默认视口）+ 图标后那一档 12
 *  （`DropdownDefaults`）。分隔线按它缩进，线才从标题最左处起。
 */
private val MENU_TITLE_INSET = 52.dp

/**
 * 浮层里分隔的颜色：暗色走**亮**线、浅色走黑线。
 *
 *  「暗色下看不清，加深分隔条」（真机反馈）：面板磨砂后是半透明的深灰（`#2e` 附近），
 *  库默认那一档线压在上面几乎看不见 —— 暗色下压暗只会更糊，得反过来提亮。
 */
@Composable
private fun popupDividerColor(
    darkAlpha: Float,
    lightAlpha: Float,
): Color =
    if (COUITheme.colorScheme.isDarkScheme) {
        Color.White.copy(alpha = darkAlpha)
    } else {
        Color.Black.copy(alpha = lightAlpha)
    }

/**
 * 面板本色（[POPUP_SURFACE_LIGHT] / [POPUP_SURFACE_DARK]）—— 分档照抄库 `ListPopupContent`
 * 的 `popupSurfaceColor()`：按主题背景亮度切。
 *
 *  浮层菜单与确认弹窗**共用这一份**：两处都要「半透本色压在糊上」，色号分家迟早走形。
 */
@Composable
internal fun popupSurfaceColor(): Color {
    val scheme = COUITheme.colorScheme
    //  **Monet 那一档跟着系统取色**：整页底色已经被系统染过（见 `PagePalette.colorsOf`），
    //  面板再摆一块固定的白 / 灰，就成了「页面上飘着的外来板」。
    //  · 浅色：取**页面底色**（`surface`）—— 那一档页面本来就亮，面板靠阴影与磨砂分得开；
    //    再往上抬一档会比页面更白，反而突兀。
    //  · 深色：页面底色几乎与面板同深，取它等于「菜单看不见」—— 抬到 `surfaceContainerHighest`
    //    （容器里最亮的一档），才与页面拉开。深色下明暗差就是唯一的分层线索。
    //  其余主题档仍用那两档常量 —— 它们是为 COUI 底子标定的（见 `PageColors`），换色号会走形。
    if (LocalAppTheme.current == AppTheme.MONET) {
        return if (scheme.isDarkScheme) scheme.surfaceContainerHighest else scheme.surface
    }
    return if (scheme.isDarkScheme) {
        POPUP_SURFACE_DARK
    } else {
        POPUP_SURFACE_LIGHT
    }
}

/**
 * 浮层菜单的**一行**（[MenuPopup] 的入参模型）—— 四处菜单（顶栏 ⋮ / 命令长按 / 响铃档位 /
 * 「添加命令」一级与二级）共用这一张小表。
 *
 * ## 调用方只写「内容」，分隔由渲染层加
 *
 *  一份菜单 = `List<MenuEntry>`，里面只放 [Row] 与 [Section]。**行与行之间的细线、小标题下面那条线、
 *  组与组之间的加深带，全部由渲染层按结构生成** —— 早先这些是调用方自己在 `buildList` 里一项一项
 *  `add(MenuEntry.RowLine)`，八处菜单各写一遍，漏一处就少一条线（「添加命令」那张就漏过）。
 *
 * 一行能表达现状的全部形态：
 *  - [enabled] `false`：置灰 + **吃掉点击**（菜单不关、也不派发）；
 *  - [selected]：单选组那一档（**行首图标换成对勾** + 文字/图标走 `primary`；行尾不再有对勾那一格，
 *    见 `showCheck` 的说明），进场时还会**滚进视窗**
 *    （「换一步」时当前那一步常在第 7 项之后，不滚就看不见自己选的是哪个）；
 *  - [danger]：删除 / 重置这类破坏性动作（红）；
 *  - [summary]：标题下面一行小字（那一行的现状，如「绑的是哪个应用」）。既有的两条「说第二句」
 *    的路子与它分工不同：[Section] 小标题是**组**的名字、`disabledHint` 是**点了才说**的话。
 *  - [submenu]：行尾**留出那一格**（库里就是 `DropdownImpl(hasSubmenu = …)`），不置时那一格不画。
 *  - [trailingIcon]：换掉那一格里的字形（`null` = 用默认的右箭头）。
 *    **注意：两位是分开的 —— 要行尾图标的行必须自己置上 [submenu]**。本组件只把 [submenu] 原样
 *    交给库，不替调用点猜「给了图标就顺手把那一格打开」。库里那一位是**唯一的开关**，只给
 *    [trailingIcon] 的话一个像素都不画（真机踩到过：「反馈问题」菜单三行只传了图标，行尾全空）。
 *    置上之后字形怎么选：
 *     · 进下一层的行（含跳**另一个页面**的行）→ 不传 [trailingIcon]，用默认右箭头
 *       （[com.lookie.opluskey.ui.model.PageIcons.EnterArrow]，与网格、列表同一颗）；
 *     · 弹一层菜单的行 → `PageIcons.Selector`；开外部链接的行 → `PageIcons.Link`。
 */
internal sealed interface MenuEntry {
    data class Row(
        val title: String,
        val icon: ImageVector,
        /**
         * 标题下面那行小字（库的 `DropdownItem.summary`）；`null` = 单行。
         *
         *  只写**这一行代表的那件事的现状**（「应用控制 → 打开应用」绑的是哪个应用），
         *  不写「为什么点不动」—— 那是 [disabledHint]，两者别混用：一条是常驻说明，一条是点了才说的话。
         *  带子标题的行会比单行高，库那边是 `heightIn(min = …)`，字不会被裁；标题与小字各最多两行。
         */
        val summary: String? = null,
        /**
         * 行首图标的**指定颜色**；`null` = 按这一行的状态走（禁用灰 / 危险红 / 选中主色 / 常规色）。
         *
         *  只有「这一行的图形本身就在表达一个颜色」时才给（系统配色那五行：色块就是它的身份）——
         *  给了之后图标**不跟着状态变色**，恒是这一行的色；但**字形**该换还是换：
         *  选中时行首换成对勾、对勾仍用这一行的色（见 `PageMenuPopup.kt` 的 `MenuRow`），
         *  「当前是这一项」与「是哪一套色」两条信息各由形状 / 颜色承担。
         */
        val iconTint: Color? = null,
        val enabled: Boolean = true,
        val selected: Boolean = false,
        val danger: Boolean = false,
        val submenu: Boolean = false,
        val trailingIcon: ImageVector? = null,
        /**
         * [enabled] 为 false 时**点一下要说的话** —— 不填就用页面的统一原因（`blockedReason()`）。
         *
         *  逐行填的那种（「最多只能添加 5 颗自定义按键」「侧键不能改名」）是**这一行自己**的限制，
         *  与「整页为什么改不了」是两回事，不能拿页面那句顶替。
         */
        val disabledHint: String? = null,
        /**
         * 这一行前面用**加深带**而不是默认那条细线（组内再分一小段时用）。
         *
         *  组与组之间的加深带由 [Section] 自动加，不许调用方操心；这一位只管「同一组里也要断开」。
         */
        val dividerBefore: Boolean = false,
        val onPick: () -> Unit,
    ) : MenuEntry

    /**
     * 一组：一行小标题 + 它下面那几行。
     *
     *  渲染层负责三件事，调用方一个分隔项都不用写：
     *   · 小标题与第一行之间那条细线；
     *   · 组内行与行之间的细线；
     *   · 相邻两组（或组与上面那些裸行）之间那条加深带。
     *
     *  没有小标题的菜单直接把若干 [Row] 放进列表即可 —— 那种「裸行」之间默认也是一条细线。
     */
    data class Section(
        val title: String,
        val rows: List<Row>,
        /**
         * 标题后面那个括号计数（`null` = 不画），画成 `响铃状态(4)`。
         *
         *  只有「点开一格之后列出它的子动作」那张菜单给（[ModeMenuPopups]）：那一层就是
         *  「这一格底下有几档」，标题右侧直接写出档数比让用户自己数省事。默认不画 ——
         *  别的菜单（顶栏 ⋮、长按命令、条件取值）的条数各有各的说法，不在这里顺手加。
         */
        val count: Int? = null,
    ) : MenuEntry
}

// 菜单行标题不再按格数预截断 —— 交给库按**两行**折行（`DropdownDefaults.TitleMaxLines`）。
//
//  预截断（原来 14 格）会在真正折行前先把长名字切成一行，第二行永远用不上；库的
//  `ListPopupColumn` 已把面板宽度夹在 `ListPopupDefaults.MaxWidth`（232dp）内，放开后只是让
//  过长的标题折到第二行、再出省略号，不会把整张菜单拉宽。

/**
 * 浮层行**内容区**的高度 —— 库的行高 `DropdownDefaults.PopupItemMinHeight`（48dp）
 * 减掉它自己上下各 `MiddleVerticalPadding`（8dp）的内边距。
 *
 * 只服务「进场定位到当前那一行」（见 [MenuRow]）：`bringIntoView` 只保证**挂 requester
 * 的那个节点**可见，所以那个节点必须**和整行内容区一样高** —— 挂在 20dp 的图标上只会让
 * 图标可见，行被上下各裁掉一截（真机反馈「滚动位置不够，选中的行没有完整显示」）。
 *
 *  行高是 `heightIn(min = …)`，带子标题的行会比这更高 ⇒ 这个常量只是**单行**那一档：
 * 行首图标格定这么高（图标在行里垂直居中），而「滚进视窗」那个矩形是**按实测行高**给的
 * （见 `PageMenuPopup.kt` 的 `rememberMenuRowBringIntoView`）。
 */
internal val MENU_ROW_CONTENT_HEIGHT =
    DropdownDefaults.PopupItemMinHeight -
        DropdownDefaults.MiddleVerticalPadding * 2

/**
 * [MenuPopup] 的「上一次内容」持有 —— 见那边关于退场动画的注释。
 *
 * 刻意**不是** `mutableStateOf`：写它不该触发重组。调用方每次重组都会新建一份
 * entries（里面还有新 lambda），用 state 存就会变成「组合期写 state → 重组 → 再写」
 * 的每帧循环；这里只要「上一次那份」这个事实。
 */
internal class MenuHold(
    var entries: List<MenuEntry>,
    var click: PreciseClickState,
)

/**
 * 「进场把**选中那一行**滚进视窗」—— 那个矩形按**实测行高**给，不按单行常量猜。
 *
 * ## 为什么要量
 *
 *  `bringIntoView(rect)` 只保证**这个矩形**可见，所以矩形必须覆盖整行（含上下各一档内边距）。
 *  行高原来是写死的 [MENU_ROW_CONTENT_HEIGHT]（单行 32dp）—— 带子标题的行比它高
 *  （库的行是 `heightIn(min = …)`，标题下面多一行小字），拿 32dp 去请求，行尾那一截会留在
 *  视口外。量的是图标格的**父节点**（库里那层内层 `Row`）：我们的图标格是定高 32dp，
 *  父节点才是「这一行有多高」。
 *
 * ## 为什么是「先量、量到再请求」
 *
 *  组合刚结束时节点还没 attach / 布局，此时请求是空操作 ⇒ 等一帧；而高度要等布局完才有值，
 *  于是把它写进 state，等它变成非零再来一次（同一行只会重排这一次：值不再变，state 也就不再通知）。
 *
 * @param selected 这一行是不是当前绑的那一档（只有它需要滚进视窗）
 * @return 挂在图标格上的修饰符（会顺带量行高）
 */
@Composable
internal fun rememberMenuRowBringIntoView(
    selected: Boolean,
    requester: BringIntoViewRequester,
): Modifier {
    val heightPx = remember { mutableFloatStateOf(0f) }
    if (selected) {
        val density = LocalDensity.current
        LaunchedEffect(heightPx.floatValue) {
            if (heightPx.floatValue <= 0f) return@LaunchedEffect
            withFrameNanos { }
            val pad = with(density) { DropdownDefaults.FirstLastVerticalPadding.toPx() }
            val rect =
                Rect(
                    left = 0f,
                    // 上下各扩一档：行的上下内边距不属于内容区，不给就只滚到内容区为止
                    top = -pad,
                    right = with(density) { MENU_ICON_SIZE.toPx() },
                    bottom = heightPx.floatValue + pad,
                )
            runCatching { requester.bringIntoView(rect) }
        }
    }
    return Modifier.onGloballyPositioned { c ->
        if (selected) {
            heightPx.floatValue = c.parentLayoutCoordinates
                ?.size
                ?.height
                ?.toFloat() ?: 0f
        }
    }
}
