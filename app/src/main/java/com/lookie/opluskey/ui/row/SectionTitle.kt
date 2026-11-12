package com.lookie.opluskey.ui.row

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.feedback.LocalTick
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.Dots
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SmallTitleDefaults
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 区块小标题的圆角（只用来收住点击涟漪）—— 与卡片行同一档。
 *
 *  不加这一层的话，可点时涟漪是个直角矩形，看着像整块被按下了。
 */
private val SECTION_TITLE_SHAPE = RoundedCornerShape(8.dp)

/**
 * 带菜单的标题：点击区在文字与箭头外面各留这一档（横向）。
 *
 *  12dp：6dp 那一版按下去太窄（手指偏半格就落到外面）。**页面缩进不参与点击** ——
 *  那 16 / 32dp 是版式留白，不是这一颗标题的一部分。
 */
private val SECTION_TITLE_TAP_H = 12.dp

/**
 * 点击区往**右**（箭头那一侧）多出多少。
 *
 *  比左边那档小一截：左边那 12dp 被 `offset` 抵消掉了、**看不见**；右边这 12dp 是实打实
 *  多出来的空白 —— 按下去时状态层一直铺到箭头右边 12dp 外，真机口径「右边留白过多」。
 *  收到 4dp：箭头（16dp 的框）后面只留一线，够按住、又不显空。
 */
private val SECTION_TITLE_TAP_END = 4.dp

/**
 * 同上，纵向 —— 2dp。
 *
 *  只留一线：竖着的余量（6dp 那一版）按下去「上下太厚」，涟漪比字高出一大圈。
 *  余下的（8 - 2 = 6dp）由外面那层 padding 补，总高不变。
 */
private val SECTION_TITLE_TAP_V = 2.dp

/**
 * **文字**的光学修正 —— 下移 1dp。
 *
 *  汉字在行盒里视觉重心偏上（行盒下半留着基线以下那点空，汉字并不占满），几何居中时
 *  整行字看着「浮起来一档」，与下面的卡片之间的关系也显得发虚。这一档补的是**文字本身**，
 *  箭头不动 —— 上一版补错了对象（把箭头往下推，字看着更偏上了）。
 */
private val SECTION_TITLE_TEXT_OPTICAL_Y = 1.dp

/** 文字与箭头之间那一档 —— 与行尾那颗选择器的间距同一量级，别让箭头飘出去。 */
private val SECTION_TITLE_ARROW_GAP = 2.dp

/**
 * 小标题**自带的**上下边距 —— 上 **16dp**、下 **0**（上多下少）。
 *
 *  加上库自己那上下各 8dp 的内边距，实际是「离上一块 24dp、离自己那块 8dp」。
 *  这两档由标题自己声明：调用点（页面）不需要在标题前后补 `Spacer`，也不需要判断
 *  「上面那一块带不带行距」—— 这一层就是唯一的地方。
 */
internal val SECTION_TITLE_PAD_TOP = 16.dp
private val SECTION_TITLE_PAD_BOTTOM = 0.dp

/** 箭头字号 —— 跟 12sp 的小标题相称（比行尾那颗 24dp 的选择器小一档）。 */
private val SECTION_TITLE_ARROW_SIZE = 16.dp

/**
 * 小标题那一档**可点** —— 给了它就整块可点，并在文字末尾自动补一颗箭头；`null` = 纯标题。
 *
 *  两种去处互斥，合成一个类型而不是两个可空参数 ——「两样不会同时给」这件事只有合成一个类型
 *  才表达得出来（两个可空参数里总有人两个都传）。
 *
 * @see Menu 点标题开这一块的菜单
 * @see Jump 点标题跳去另一页
 */
internal sealed interface SectionTitleClick {
    /** 点标题做什么。 */
    val onClick: () -> Unit

    /**
     * **开这一块的菜单**：行尾一颗 `chevron-down`，开合时转 180°。
     *
     * @param expanded 菜单现在开着没有（箭头按它转）
     * @param onClick 点标题做什么（通常是 `menuOpen = true`）
     */
    class Menu(
        val expanded: Boolean,
        override val onClick: () -> Unit,
    ) : SectionTitleClick

    /**
     * **跳去另一页**：行尾一颗右箭头、恒不动 —— 与列表里「点了会进另一页」那一颗同一字形。
     *
     * @param onClick 点标题去哪
     */
    class Jump(
        override val onClick: () -> Unit,
    ) : SectionTitleClick

    /**
     * **原地撑开一层**（弹窗 / 全屏编辑）：行尾那颗箭头与 [Jump] 同一个字形，恒不动。
     *
     *  与 [Jump] 的区别只在**去哪儿**：那个去另一页，这个在原地弹一层；两者都不带开合状态
     *  —— 不像 [Menu] 那样有「开着 / 关着」。分两档是为了让调用点说清自己在做哪件事。
     *
     * @param onClick 点标题撑开什么
     */
    class Expand(
        override val onClick: () -> Unit,
    ) : SectionTitleClick
}

/** 那颗「更多」的点击区 —— 小标题行尾与命令卡右上角同一尺寸（40×20、8dp 圆角）。 */
private val MORE_BUTTON_WIDTH = 40.dp
private val MORE_BUTTON_HEIGHT = 20.dp

/**
 * 「贴右端」时整个点击区往右挪多少 —— **半个余量**（40 与 20 之差的一半 = 10dp）。
 *
 *  挪的是格子、不是格子里的字形：这样字形右边线正好落在未挪时格子的右边线（= 内容右边线）上，
 *  而状态层仍与字形同心。反过来挪字形（`CenterEnd`）会让按下去的底色偏在字左边，看着「歪」。
 */
private val MORE_BUTTON_END_SHIFT = (MORE_BUTTON_WIDTH - MORE_BUTTON_HEIGHT) / 2

/**
 * 行尾那一格里**字形右侧的留白** —— 与 [MORE_BUTTON_END_SHIFT] 同一个数（`(40 − 20) / 2`），
 * 但意思不同：那一格是 40 宽、字形 20 居中，所以字形的右边线比格子的右边线还要往里 10dp。
 *
 *  同一行里要跟那颗菜单对齐的**纯文字**（如「命令名称」行尾的字数统计）得把这 10dp 一起算进去，
 *  否则它比菜单的字形更靠外，两个页面的行尾看着不在一条线上。
 */
internal val SECTION_TITLE_TRAILING_GLYPH_INSET = (MORE_BUTTON_WIDTH - MORE_BUTTON_HEIGHT) / 2

/**
 * 行尾「计数」与那颗菜单之间那一档 —— **10dp**（= [MORE_BUTTON_END_SHIFT]）。
 *
 *  这一档不是随便定的：菜单的点击格 40dp、字形 20dp 居中，格子左边**自带** 10dp 空档。
 *  · 带 `flushEnd` 的那颗（「命令详情」「命令步骤」）整格再右移半格，计数到字形是 **20dp**；
 *  · 不带的那颗（场景块「场景 N」）格子贴住行右边缘，只有 **10dp**。
 *
 *  同一页里两行并排看就成了「一边松一边紧」（真机口径「系统场景块的 counter 到菜单的距离
 *  比命令详情的少」）。补这一档把两处拉平 —— 补的只有**不带 `flushEnd`** 的那一行：
 *  带的那几处本来就有这 10dp，再补会变成 30dp。
 */
internal val SECTION_TITLE_COUNTER_GAP = MORE_BUTTON_END_SHIFT

/**
 * 小标题**行尾那一格**的右内缩 —— 与 [MORE_BUTTON_WIDTH] 那套几何同源：
 * 页面缩进 [PAGE_MARGIN]（12）再加字形在格内的那一档（10）= **22**。
 *
 *  取 22 而不是 12，是因为「一颗 40 宽点击格、20 宽字形居中」的字形右边线本来就比格子右边线
 *  往里 10dp：**文字行尾要与那颗字形的右边缘落在一条线上**，就得补这 10dp。
 *  两者共用同一个数之后，「行尾放菜单」与「行尾放计数」在同一列，不用谁再各调各的。
 */
internal val SECTION_TITLE_TRAILING_END = PAGE_MARGIN + SECTION_TITLE_TRAILING_GLYPH_INSET

/**
 * 那颗**横向三个点**（「更多」）—— 尺寸 / 字形 / 圆角 / 取色**只在这里定义**。
 *
 *  两处共用：小标题行尾（「命令列表」「场景」那块）与「已保存的命令」卡片右上角。
 *  点击区就是它自己那一格（不把整行做成可点）；字形与点击区同高，库默认 24dp 塞不进 20dp 的框。
 *
 * @param anchor 触点锚点：浮层菜单弹在按下那一点（只看不消费，见 [preciseClickAnchor]）
 * @param flushEnd 字形贴住**内容右边线**而不是在那一格里居中 —— 卡片右上角那颗用；行尾那一格
 *   由 [SectionTitle] 的内缩负责，传 `true` 时字形正好落在小标题那条行尾线上。
 * @param enabled `false` = 置灰且**不吃点击**（卡片在排序态时整片禁用）
 *
 *   实现见 [MORE_BUTTON_END_SHIFT]：移动的是**整个点击区**，不是格子里的字形。
 */
@Composable
internal fun MoreButton(
    onClick: () -> Unit,
    anchor: PreciseClickState,
    enabled: Boolean = true,
    flushEnd: Boolean = false,
) {
    //  **贴右端 = 整格右移，而不是把字形推到格子右端。**
    //  上一版走的是后者（`contentAlignment = CenterEnd`）：字确实贴住了右边线，可那 40×20 的
    //  状态层仍以格子为中心 ⇒ 按下去那一整块底色偏在字**左边**十格、右边一点没有，
    //  用户的说法就是「波纹歪的」（COUI 的 `COUIIndication` 画的是盖满整格的矩形，不是圆形涟漪）。
    //  现在字留在格子正中、格子整体右移半个余量：字右边线落在原处，底色与字同心。
    val endShift = if (flushEnd) MORE_BUTTON_END_SHIFT else 0.dp
    //  振动走页面那条通用通道（[LocalTick]）—— 与可点标题（[ClickableTitleRow]）、卡片行同一份判据。
    //  漏这一下时这颗是**唯一**点了不响的控件：它挂在标题行尾，与旁边那颗可点标题挨着，
    //  同一行两种手感（真机口径「小标题菜单点击时没有通用振动」）。
    val tick = LocalTick.current
    //  置灰 / 恢复带过渡（卡片排序态、整页闸门关着时这颗会变灰）：与旁边那些行首图标同一档。
    val dotsTint by animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onSurfaceVariantSummary
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "MoreButtonTint",
    )
    Box(
        modifier = Modifier.offset(x = endShift),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = MORE_BUTTON_WIDTH, height = MORE_BUTTON_HEIGHT)
                    .preciseClickAnchor(anchor)
                    .clip(SECTION_TITLE_SHAPE)
                    .clickable(enabled = enabled) {
                        tick()
                        onClick()
                    },
            //  字形**恒居中**：状态层与字形同心，按下去不会偏一半
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TablerIcons.Outline.Dots,
                contentDescription = I18n.get(Strings.COMMON_MORE, "更多"),
                //  置灰取库那一档（与行首图标、菜单行同一个禁用色），不做第三种灰
                tint = dotsTint,
                modifier = Modifier.size(MORE_BUTTON_HEIGHT),
            )
        }
    }
}

/**
 * 小标题行尾那一格：**[「更多」+ 计数]** —— 命令详情、命令步骤、场景块三处共用。
 *
 *  三处原先各拼一个 `Row { MoreButton; Spacer(10dp); TitleCounter }`，差别只有「前面有没有
 *  一句状态」。那种「谁多一行」的复制最后必然走形：菜单与计数之间的距离、菜单能不能点、
 *  计数的上限，改一处就漏两处。
 *
 *  要在菜单**前面**再放一格（场景块的「已停用」那半句），由调用点在 `Row` 里先画它、再调这一个 ——
 *  那一格是那一页自己的话，不属于「菜单 + 计数」这套几何。
 *
 *  @param length 计数里的当前值
 *  @param max 上限；`null` = 只报个数（见 [TitleCounter]）
 *  @param enabled 这颗「更多」能不能点（排序态整片关掉）
 */
@Composable
internal fun SectionTitleTrailingMore(
    anchor: PreciseClickState,
    onMenu: () -> Unit,
    length: Int,
    max: Int? = null,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        //  顺序是**菜单在前、计数在最后**（用户口径「counter 和菜单换个位置」）。
        //  菜单右边还有一格文字，所以**不给 `flushEnd`**：那一下整格右移是为了让字形贴住
        //  内容右边线，而这条线现在归计数那格。
        MoreButton(onClick = onMenu, anchor = anchor, enabled = enabled)
        //  菜单与计数之间那一档 —— 菜单格左边 / 右边自带 10dp，再补这一档，字形到计数都是 20dp
        //  （见 [SECTION_TITLE_COUNTER_GAP]）。
        Spacer(Modifier.width(SECTION_TITLE_COUNTER_GAP))
        TitleCounter(length, max)
    }
}

/**
 * 一页 / 一块的**小标题** —— 全项目包住库里 `SmallTitle` 的唯一一处。
 *
 * ## 为什么包这一层
 *
 *  库那个 `SmallTitle` 只能给一串字：要它可点（点标题弹出这一块的菜单）、要在右边挂一颗
 *  按钮，都得在每个调用点各套一遍 `Row` / `Box` —— 三十来处各写一遍，形状迟早走形。
 *
 *  这里**不重画样式**：文字、字号、行高、内边距仍是库那一份，补的只有三件事 ——
 *  可点、文字末尾那颗箭头、行尾那一格。
 *
 *  可点分三档，形状一样、只有箭头不同：**开菜单**（[SectionTitleClick.Menu]，行尾一颗
 *  `chevron-down`，开合时转 180°）、**跳转**（[SectionTitleClick.Jump]，行尾一颗右箭头，
 *  恒不动 —— 与列表里「点了会进另一页」那颗同形）与**原地撑开**（[SectionTitleClick.Expand]，
 *  行尾一颗全屏图标）。三档合成 [SectionTitleClick] 一个类型。
 *
 * ## 点击区只包住标题本身
 *
 *  可点时，按压面积 = **文字 + 箭头 + 左 12dp / 右 4dp / 上下 2dp**
 *  （[SECTION_TITLE_TAP_H] / [SECTION_TITLE_TAP_END] / [SECTION_TITLE_TAP_V]），
 *  **页面缩进与行高余量都留在外面**——
 *  上一版把它们包在可点区里，真机上就成了「点标题左边一大片空白、点上下一整行也弹菜单」。
 *  行高不变：缩进与余量由外面那层 padding 提供，总高仍是库那一份（16 + 8 + 8 ≈ 33dp）。
 *
 * ## 锚点
 *
 *  谁要弹浮层，就把 `Modifier.preciseClickAnchor(state)` 透过 [modifier] 挂在**这一行**上
 *  （弹层按手指按下的位置落）。行尾那颗自己的锚点由 [trailing] 里的组件自己挂。
 *
 * @param text 小标题的文字（调用方自己 `I18n` 取好）
 * @param insideMargin 库那份内边距（左右版式缩进 + 上下留白）；页面里用默认值
 * @param click 点标题做什么（开菜单 / 跳去别页）；`null` = 纯标题（绝大多数调用点）
 * @param trailing 行尾那一格（一颗「更多」或一格计数）；`null` = 不占位
 * @param trailingEndPadding 行尾那一格的右内缩 —— **默认就对了**，页面里不用给。
 *   嵌在卡片里的标题（条件执行页那几条规则）自己要传 `0.dp`：那一行的右边缘已经让过一档了。
 *
 *  注意：**这一层不管块与块之间的间距**（它以前带过 `gapTop` / `gapBottom`，结果每个调用点
 *  都要算「上一块带不带行距」）。
 */
@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = SmallTitleDefaults.InsideMargin,
    click: SectionTitleClick? = null,
    trailing: (@Composable () -> Unit)? = null,
    trailingEndPadding: Dp = SECTION_TITLE_TRAILING_END,
) {
    //  **区块间距由组件统一给**（上 16 / 下 0，见常量那段）：页面里不再为标题补 `Spacer`，
    //  第一个标题也不做特例 —— 页面顶上那一档由各页的顶栏占位量管，标题这一层只管「两块之间」。
    //  自带上下边距（上多下少）：调用点不用管标题前后该留多少
    val box = modifier.padding(top = SECTION_TITLE_PAD_TOP, bottom = SECTION_TITLE_PAD_BOTTOM)
    //  两样都没有：就是库那颗标题，一点多余布局都不套（这一支是绝大多数调用点）
    if (click == null && trailing == null) {
        SmallTitle(text = text, modifier = box, insideMargin = insideMargin)
        return
    }
    Row(
        modifier = box.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (click == null) {
            SmallTitle(text = text, insideMargin = insideMargin)
        } else {
            ClickableTitleRow(text = text, insideMargin = insideMargin, click = click)
        }
        //  把行尾那一格推到右边（标题自己只占它本身的宽度）
        Spacer(Modifier.weight(1f))
        //  **右内缩由这一层统一给**：调用点不再各传一个 `endPadding`（同一个数写七八份，
        //  漏一处就是那一行行尾贴边）。见 [SECTION_TITLE_TRAILING_END]。
        trailing?.let {
            Box(Modifier.padding(end = trailingEndPadding)) { it() }
        }
    }
}

/**
 * 可点的标题本体 —— 文字 + 行尾那颗箭头。点击区只包住这两样（见 [SECTION_TITLE_TAP_H]），
 * 页面缩进与行高余量都留在外面。
 */
@Composable
private fun ClickableTitleRow(
    text: String,
    insideMargin: PaddingValues,
    click: SectionTitleClick,
) {
    //  振动走页面那条通用通道（[LocalTick]）：这一层不带 `SettingsPage` 接收者 ——
    //  与能点的行（`CardRow`）、开关（`SwitchRow`）同一份判据，不在这里自己 new 一个。
    val tick = LocalTick.current
    val layoutDirection = LocalLayoutDirection.current
    //  页面缩进（16 / 32dp）与行高余量都留在**点击区外面**：
    //  上一版把它们包在可点区里，结果「点标题左边一大片空白、点上下一整行也能弹菜单」。
    val startInset = insideMargin.calculateStartPadding(layoutDirection)
    val outerTop = (insideMargin.calculateTopPadding() - SECTION_TITLE_TAP_V).coerceAtLeast(0.dp)
    val outerBottom = (insideMargin.calculateBottomPadding() - SECTION_TITLE_TAP_V).coerceAtLeast(0.dp)
    Row(
        modifier =
            Modifier
                .padding(start = startInset, top = outerTop, bottom = outerBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    //  **链式顺序是这里唯一的坑**：`clickable` 必须在 `padding` **外面**
                    //  （写在它前面），这样点击区才等于「文字 + 内外那两档 padding」。
                    //  反过来写（padding 在外）时，padding 只是给外圈留白、**不参与点击**——
                    //  真机表现就是「调了半天 padding，点击区一点都不变」。
                    //  `offset` 与 padding 一负一正：按压面积向左右各多出 [SECTION_TITLE_TAP_H]，
                    //  而文字仍落在 [startInset] 上。
                    .offset(x = -SECTION_TITLE_TAP_H)
                    .clip(SECTION_TITLE_SHAPE)
                    .clickable {
                        tick()
                        click.onClick()
                    }.padding(
                        start = SECTION_TITLE_TAP_H,
                        end = SECTION_TITLE_TAP_END,
                        top = SECTION_TITLE_TAP_V,
                        bottom = SECTION_TITLE_TAP_V,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            //  文字本身零内边距：缩进已经在外面了，右边紧接箭头。
            //  外面那层 `offset` 是**光学修正**：整行字往下落一档（见常量的注释）。
            Box(Modifier.offset(y = SECTION_TITLE_TEXT_OPTICAL_Y)) {
                SmallTitle(text = text, insideMargin = PaddingValues(0.dp))
            }
            TitleArrow(click)
        }
    }
}

/**
 * 文字末尾那颗箭头 —— 两档各一颗：
 *  · [SectionTitleClick.Menu]：`chevron-down`，开合时转 180°；
 *  · [SectionTitleClick.Jump] / [SectionTitleClick.Expand]：右箭头、恒不动 —— 与列表里
 *    「点了会进另一页」那一颗同一字形（撑开弹窗那档也是这颗：它只表示「点了还有内容」）。
 */
@Composable
private fun TitleArrow(click: SectionTitleClick) {
    val box = Modifier.padding(start = SECTION_TITLE_ARROW_GAP).size(SECTION_TITLE_ARROW_SIZE)
    val tint = COUITheme.colorScheme.onBackgroundVariant
    //  不开合的那两档（跳页 / 原地撑开）画**同一颗右箭头**：它读作「点了还有内容」，
    //  不承诺是换页还是弹一层；两档的区别只在语义与点击行为上。
    if (click !is SectionTitleClick.Menu) {
        Icon(PageIcons.EnterArrow, contentDescription = null, modifier = box, tint = tint)
        return
    }
    val rotation by animateFloatAsState(
        targetValue = if (click.expanded) 180f else 0f,
        label = "SectionTitleArrow",
    )
    Icon(PageIcons.StepDown, contentDescription = null, modifier = box.rotate(rotation), tint = tint)
}
