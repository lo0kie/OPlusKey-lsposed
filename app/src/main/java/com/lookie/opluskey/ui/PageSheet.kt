package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.EASING_EMPHASIZED_DECEL
import com.lookie.opluskey.ui.model.HEADER_ICON_SIZE
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER_MS
import com.lookie.opluskey.ui.model.isDarkScheme
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.SmallTitleDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

// 底部抽屉的通用壳 —— 全项目**只有这一份**抽屉骨架。
//
// 这一层只管「抽屉长什么样、怎么进出、怎么滚」，一行业务内容都不认识：面板色、内边距、
// 高度上限、高度过渡、滚动与回弹、返回登记全在这里，调用点只递标题与内容。
//
//  为什么要收：同样的参数块（`show` / 标题 / 面板色 / `insideMargin` / `enableNestedScroll`）
//  加上「登记浮层 + 登记跟手返回」这两行，已经在三处各抄了一遍（步骤选择抽屉、变量的操作抽屉、
//  变量的新建抽屉）—— 其中两处还各抄了一份「暗色下面板抬一档」的配色算法，第三处漏了，
//  暗色下卡片直接透出底下的页面。抄第三遍就是该收的时候（AGENTS.md 一、3 的三次重复原则）。

/**
 * 抽屉面板的最高高度 —— 同一类界面同一档。
 *
 *  原先步骤抽屉写 460、变量抽屉写 480：两个数谁也没比谁更对，只是各自定的。收拢之后取 480
 *  —— 高出那一档全给内容，矮屏上也不会把整页盖死（面板仍按内容长，长了才停在这一档）。
 */
private val SHEET_MAX_HEIGHT = 480.dp

/**
 * 抽屉里小标题（`SectionTitle`）的左右缩进 —— **比页面里收一档**（页面里吃库默认那 32dp）。
 *
 *  抽屉的 `insideMargin` 已经给过一档 [PAGE_MARGIN]，小标题再叠上库那份 32dp 就落到 48dp ——
 *  比它管着的卡片行文字（卡片 16 + 行内 16 = 32）右移一档，真机上就是「标题比内容还靠里」。
 *  这里收成 [PAGE_MARGIN]：两层正好 32，与行文字同一条线。
 *
 *  上下留白原样取库那份（[PaddingValues.calculateTopPadding] / `calculateBottomPadding`），
 *  行高与页面里那几处小标题一致。
 *
 *  **抽屉里的小标题一律传它**：两个抽屉各写各的，就会出现「这一层标题贴左、那一层缩进去」。
 */
internal val SHEET_TITLE_MARGIN: PaddingValues =
    with(SmallTitleDefaults.InsideMargin) {
        PaddingValues(
            start = PAGE_MARGIN,
            top = calculateTopPadding(),
            end = PAGE_MARGIN,
            bottom = calculateBottomPadding(),
        )
    }

/**
 * 一个底部抽屉。
 *
 * ## 它替你做了哪几件事
 *
 *  · **面板与进出**：底色、圆角、把手、进场 / 退场动画都由 `OverlayBottomSheet` 给，
 *    这里只把 `show` 原样递进去 —— 调用点**不要**再写 `if (!show) return`：
 *    一写就整棵子树消失，退场成了硬切（变量的新建抽屉原先就是这样）。
 *  · **高度过渡**：内容高度跳变时（分组列表 ↔ 搜索结果 ↔ 一张卡）面板跟着一跳很扎眼，
 *    这一层统一挂 [animateContentSize]，时长与缓动与全页其它「动态显隐」同一档。
 *  · **滚动**：内容一律可滚，滚到头在原地做橡皮筋回弹（`nestedScrollToParent = false`
 *    —— 余量往上传的话，滚到底再松手 = 把抽屉拽下去）。
 *  · **底部留白**：[PAGE_BOTTOM_PADDING] 挂在**滚动内容里**：挂到外层是给滚动视口内缩，
 *    视口被压短，最后一行照样被底边吃掉一截。
 *  · **返回**：登记进页面级浮窗表（系统返回先收抽屉、再退页；换页时一起收掉），
 *    并登记「这一退归我、而且要跟手」——后一条少了，返回就只剩一次性收起，
 *    下拉跟手那套预测返回效果会没掉。
 *
 * ## 不滚的顶部那一块
 *
 *  [header] 是**不跟着滚**的部分（步骤抽屉顶上那个过滤框：翻几屏找候选时不该把搜索框滚走）。
 *  它下面才是滚动区。不需要固定内容时不用传。
 *
 * @param show 开着没有 —— 原样递给库，退场动画靠它（别在外面判 true 再调）
 * @param title 抽屉标题（把手下面那一行）
 * @param onDismiss 收起：点外部 / 下拉 / 系统返回 / 换页，四条路都走它
 * @param endAction 标题行**右端**那一格（「新建」这类提交键）；`null` = 不占位
 * @param header 顶部不滚的那一块（`null` = 内容直接占满）
 * @param content 抽屉正文（在滚动区里，自带 [ColumnScope]）
 *
 * ## 为什么参数到 6 个（`@Suppress("LongParameterList")`）
 *
 *  `show` / `title` / `onDismiss` 是抽屉自己的开关与出口，`endAction` / `header` / `content`
 *  是「这一层抽屉怎么摆」—— 全是行参，与 `NameDialog` / `IconInputView` 那边同一个理由：
 *  拆成 options 对象只会让四个调用点各多一层壳。
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.PageSheet(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    endAction: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    overlays.Closer(show = show, onDismiss = onDismiss)
    overlays.BackCapturing(show = show)
    OverlayBottomSheet(
        show = show,
        title = title,
        //  标题行右端那一格（右上角）—— 库自己的槽，不从标题里抢位置
        endAction = endAction,
        backgroundColor = sheetSurfaceColor(),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        //  **横向内边距全在这一个参数上**（它同时套在把手 / 标题 / 内容上）：收到 [PAGE_MARGIN]
        //  之后标题与行文字的左边线才落在同一条 16dp 线上。
        insideMargin = DpSize(PAGE_MARGIN, 0.dp),
        //  断掉抽屉对内容滚动的接管：否则「内容滚不动之后的余量」会被当成往下拽抽屉，
        //  滚到底一松手抽屉就没了。硬性 false，不接受调用点覆盖。
        enableNestedScroll = false,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = SHEET_MAX_HEIGHT)
                .animateContentSize(tween(VISIBILITY_ENTER_MS, easing = EASING_EMPHASIZED_DECEL)),
        ) {
            header?.invoke()
            Column(
                Modifier
                    .fillMaxWidth()
                    //  `fill = false`：内容短时抽屉跟着内容长（不撑到上限），长了才吃满剩余空间。
                    //  这里必须给 weight 而不是再套一层 `heightIn`：顶部那一块的高度是它自己算的
                    //  （会随字号 / 语言变），写死一个数去减，换语言就溢出。
                    .weight(1f, fill = false)
                    .overScrollVertical(nestedScrollToParent = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = PAGE_BOTTOM_PADDING),
                content = content,
            )
        }
    }
}

/**
 * 抽屉标题行右端那颗**圆键里的图标**边长。
 *
 *  取 20dp 与行尾那几颗图标键（`ICON_ACTION_ICON`）同一档 —— 两个位置都是「一颗小图标按钮」，
 *  在真机上不该有两种大小。满格字形（`trash` 这种）也是靠这一档收住的，不靠调用点各写一个数。
 */
private val SHEET_ACTION_ICON = 20.dp

/**
 * 抽屉标题行右端那颗圆键（`PageSheet` 的 `endAction` 里就放它）。
 *
 * ## 它把哪些事收了进来
 *
 *  · **触达区**：与顶栏那几颗同一档（[HEADER_ICON_SIZE]）。库的 `IconButton` 默认是 48dp 的
 *    按压圈，摆在标题行里比标题还高一截 —— 每个调用点自己记得给，迟早漏一个。
 *  · **图标大小**：固定 [SHEET_ACTION_ICON]（不按字形默认的 24dp，满格字形会重一圈）。
 *  · **配色**：可用 `onSurfaceContainer`、破坏性动作 `error`、**禁用压暗**
 *    （`disabledOnSecondaryVariant`），三档之间走 [COLOR_TRANSITION_SPEC] 过渡 ——
 *    库的 `IconButton` 只管文字那一档，图标色得自己算，那件事不该落在调用点上。
 *  · **禁用态与它的说法**：[hint] 非 null 就是「灰着 + 点一下说这句」。灰着不给原因是
 *    用户只会反复点的经典坑，所以这里把「灰」和「为什么灰」做成同一个参数 —— 想禁用它，
 *    就必须说得出理由。
 *
 * @param icon 那颗字形
 * @param description 无障碍描述（图标没有可见文字，这一项是唯一的语义来源）
 * @param onClick 点一下做什么（禁用时不会触发，改由 [DisabledHint] 说一句）
 * @param hint `null` = 能点；非 `null` = 灰着，且被点一下时说这句
 * @param error 破坏性动作（删除这类）—— 用 `error` 上色；`hint` 非 null 时仍优先上灰
 */
@Composable
internal fun SheetAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    hint: String? = null,
    error: Boolean = false,
) {
    val scheme = COUITheme.colorScheme
    val enabled = hint == null
    val color by animateColorAsState(
        targetValue =
            when {
                !enabled -> scheme.disabledOnSecondaryVariant
                error -> scheme.error
                else -> scheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "SheetAction",
    )
    DisabledHint(hint) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            minWidth = HEADER_ICON_SIZE,
            minHeight = HEADER_ICON_SIZE,
            cornerRadius = HEADER_ICON_SIZE / 2,
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(SHEET_ACTION_ICON), tint = color)
        }
    }
}

/**
 * 抽屉面板的底色。
 *
 *  页面底色是 `surface`，而 COUI 暗色里 `surface` 与抽屉下面那点缝隙几乎是同一个黑，
 *  面板浮不起来 ⇒ 暗色抬一档到 `surfaceContainer`；浅色仍用页面那个 `surface`
 *  （「配色和页面一样」在浅色下原样成立）。
 *
 *  **必须 `compositeOver` 合一次**：COUI 暗色的层级色是**半透明白**（`surfaceContainer` =
 *  `0x1AFFFFFF`），设计上是叠在页面那层实黑上的。抽屉面板画在独立窗口上，直接把半透明色
 *  交给它就是**真透明** —— 底下的页面会透上来。先按「页面黑底」合出实色，观感与 COUI
 *  原本一致，只是不再透。
 */
@Composable
private fun sheetSurfaceColor(): Color {
    val pageSurface = COUITheme.colorScheme.surface
    return if (COUITheme.colorScheme.isDarkScheme) {
        COUITheme.colorScheme.surfaceContainer.compositeOver(pageSurface)
    } else {
        pageSurface
    }
}

/**
 * 抽屉里**卡片**的颜色 —— 浅色不覆盖（`null` = 库默认，与页面里的卡片同一档）。
 *
 *  暗色下卡片要比面板再亮一档，否则面与卡片同色，卡的边界就没了。底取**页面那层**而不是
 *  抽屉面：那样算出来的色与页面里的卡片**同值**，于是层级是「页面 < 抽屉面 < 卡片」，
 *  卡片还是用户熟悉的那一档。
 *
 *  卡片同样要吃 `compositeOver`（理由见 [sheetSurfaceColor]）：半透明色画在独立窗口上就是真透明。
 *
 *  抽屉里的 `CardList` 一律传它 —— 漏一处就是「暗色下那张卡透出底下的页面」。
 */
@Composable
internal fun sheetCardColors(): CardColors? {
    if (!COUITheme.colorScheme.isDarkScheme) return null
    val pageSurface = COUITheme.colorScheme.surface
    val cardColor = COUITheme.colorScheme.surfaceContainerHigh.compositeOver(pageSurface)
    return CardDefaults.defaultColors(
        color = cardColor,
        pressedColor = COUITheme.colorScheme.surfaceContainerHigh.compositeOver(cardColor),
    )
}
