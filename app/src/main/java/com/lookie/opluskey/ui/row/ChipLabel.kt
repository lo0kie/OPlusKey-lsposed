package com.lookie.opluskey.ui.row

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.present.COMBO_PAIR_SEPARATOR
import com.lookie.opluskey.ui.model.page.opticalCenter
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「一枚名字框」—— 组合键的成员键名、场景条件里的应用名共用这一份。
//
// 出身：这套框本来是 `ComboListCard.kt` 里私有的 `ComboKeyChip`（组合那一列按颗数排框）。
//  场景条件那一行要的是**同一种长相**（用户口径：应用名改成和组合按键一样的标签样式、复用它），
//  于是把框本身搬到这里，两边都调它 —— 各写一份的话，圆角 / 内边距 / 底色那几档迟早分叉，
//  同一个界面里会出现两种「名字框」。

/** 框左右的内边距 —— 量列宽时也要一起算进去（见 `comboTitleWidths`）。 */
internal val CHIP_H_PAD = 6.dp

/** 框上下的内边距 —— 比左右小得多：框高跟着字高走，别把行撑高。 */
internal val CHIP_V_PAD = 2.dp

/** 框的圆角 —— 圆角矩形：比小标签那档（4dp）大一点点，与标题字号相称，又不像胶囊。 */
internal val CHIP_RADIUS = 6.dp

/**
 * 框里的字号相对行标题的缩放 —— 0.85 倍。
 *
 *  为什么不能直接用行标题那一档：一行里要塞**几枚框 + 几个 `×`**，按 `headline1` 原样量，
 *  4 个字的名字（「音量 +」）在 360dp 屏上就差约 18dp 装不下 —— 于是尾字变省略号
 *  （真机口径「较长的键名显示不全」）。缩到 0.85 之后两列 + 分隔符正好落在标题槽内，
 *  而小一号的字与正文仍是同一档观感，看不出「这一行字小了」。
 *
 *  **量宽度与画字必须用同一个比例**：[chipStyle] 是唯一入口，两边都调它。
 */
private const val CHIP_FONT_SCALE = 0.85f

/** 两枚框之间那个 `×` 的左右间距 —— 框边缘对齐时，这个间距自然就是对称的。 */
internal val CHIP_SEPARATOR_GAP = 6.dp

/** 换行之后行与行之间的距离 —— 框高本身很小，2dp 叠上去刚好读成两排而不散架。 */
private val CHIP_FLOW_GAP = 4.dp

/**
 * 框里那档样式 —— 行标题的**字号 × 0.85**、字重 Medium。
 *
 *  字重并进样式而不是写在 `Text` 的参数上：`opticalCenter` 与 `measure` 都按样式算，
 *  写在参数上会让量出来的是常规体、画出来是 Medium，差的那一点正好落在上下留白上。
 */
@Composable
internal fun chipStyle(): TextStyle {
    val base = COUITheme.textStyles.headline1
    return base.copy(fontWeight = FontWeight.Medium, fontSize = base.fontSize * CHIP_FONT_SCALE)
}

/**
 * 一排框的**长相** —— 框里那档样式、字色、以及整页那道闸（关着时框与字一起压灰）。
 *
 *  合成一个类型是为了别把这三个数一路摊成参数表（`ChipFlow` 本来就要收名字、宽度、前缀位）；
 *  它们本来就是「这一排框长什么样」这一件事。
 */
internal class ChipLook(
    val style: TextStyle,
    val color: Color,
    val enabled: Boolean,
)

/** 造一份默认长相：框里用 [chipStyle]，字色与闸门由调用点给（组合标题 / 场景条件同一套）。 */
@Composable
internal fun chipLook(
    color: Color,
    enabled: Boolean,
): ChipLook = ChipLook(chipStyle(), color, enabled)

/**
 * 一枚名字框 —— 一行、装不下就省略号。
 *
 *  圆角矩形（不是胶囊）：圆角只吃四角，字在框里**居中** —— 短名字那个两边各多出一点白，
 *  两个框因此一样宽、边缘竖着对齐（用户口径「上下宽度不一致时往短的填 padding」）。
 *
 *  底色取 `surfaceContainer`、描边取 `dividerLine` 再减半：与顶栏那颗图标按钮同一档
 *  （「一块可以点的面」在这个界面里只有一种长相）。两档都带过渡 —— 闸门一拨，
 *  框与里面的字在同一段时间里一起变，不会「字在淡、框在跳」。
 *
 *  @param look 框里那档样式与颜色（量过宽度时必须是画字同一份，见 [chipLook]）
 *  @param modifier 宽度由调用方给：组合那一列整列同一个数（对齐），场景条件那一行按各自字宽
 */
@Composable
internal fun NameChip(
    text: String,
    look: ChipLook,
    modifier: Modifier,
) {
    val scheme = COUITheme.colorScheme
    val color = look.color
    val enabled = look.enabled
    val fill by animateColorAsState(
        targetValue = if (enabled) scheme.surfaceContainer else scheme.disabledSecondaryVariant,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ChipFill",
    )
    val border by animateColorAsState(
        targetValue =
            if (enabled) {
                scheme.dividerLine.copy(alpha = scheme.dividerLine.alpha * 0.5f)
            } else {
                scheme.disabledSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "ChipBorder",
    )
    val shape = RoundedCornerShape(CHIP_RADIUS)
    //  字重并进样式里再交给 [opticalCenter]（理由见 [chipStyle]）
    val inner = look.style.copy(fontWeight = FontWeight.Medium)
    Box(
        modifier =
            modifier
                .background(color = fill, shape = shape)
                .border(width = 1.dp, color = border, shape = shape)
                .padding(horizontal = CHIP_H_PAD, vertical = CHIP_V_PAD),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = inner,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            //  **上下留白靠这一行修正**：行盒里汉字墨迹的重心比几何中心偏高一点（下行留白多），
            //  直接居中的结果是「上面紧、下面松」。与 `AppKindTag` 那几枚小标签同一条通道。
            //  **不能再叠 `fillMaxWidth()`**：框宽要跟着字宽走（定宽那一档由外面的 `width()` 给）。
            //  在 `FlowRow` 里子项拿到的是整行可用宽，`fillMaxWidth()` 会把每一枚框都撑成整行宽
            //  （真机口径「每一个应用 tag 都变成全宽了」）。
            modifier = Modifier.opticalCenter(inner),
        )
    }
}

/**
 * 一排名字框，**只占一行**（超宽裁掉）—— 顶栏副标题那一行用它。
 *
 *  与 [ChipFlow] 的唯一差别是「不换行」：顶栏的展开高度是按副标题测出来的，换行会把整条栏顶高，
 *  而这一行装的是「这一页在编哪一颗键」—— 宁可尾巴被裁，也不该让栏高随名字长短跳一下。
 *
 *  @param look 框里那档样式与颜色 —— 顶栏给 `body2`（与纯文字副标题同一档），列表行给 `headline1 × 0.85`
 */
@Composable
internal fun NameChipRow(
    names: List<String>,
    look: ChipLook,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { at, name ->
            NameChip(text = name, look = look, modifier = Modifier)
            if (at < names.lastIndex) {
                Text(
                    text = COMBO_PAIR_SEPARATOR,
                    style = look.style,
                    color = look.color,
                    modifier = Modifier.padding(horizontal = CHIP_SEPARATOR_GAP),
                )
            }
        }
    }
}

/**
 * 一排名字框 —— **装不下就换行**（用户口径：组合列表与场景条件都要换行）。
 *
 *  为什么不是一个 `Row`：`.weight(fill = false)` 那种做法在名字多时会把每枚框压到只剩一个
 *  圆角矩形的壳（真机口径「选多一点只剩框了」）—— 名字被省略到 0 宽，用户什么也读不到。
 *  换成换行之后，每一枚框都保有自己的字宽，多出来的排到下一行。
 *
 *  ## 分隔符跟在框后面
 *
 *  `×` 作为**前一枚框的尾巴**参与换行，而不是独立一项：独立一项时换行会让某一行以 `×` 开头，
 *  读起来像漏了一个名字。跟在前一枚后面，行尾最多出现一个 `×`，方向是清楚的。
 *
 *  @param look 框里那档样式与颜色（[chipLook]）
 *  @param chipWidth 给定时每枚框**同一个宽**（组合列表要横着对齐）；`null` = 按各自字宽（场景条件）
 *  @param leading 排在最前面的那一项（场景条件的「前台应用 - 」）；它同样参与换行
 */
@Composable
internal fun ChipFlow(
    names: List<String>,
    look: ChipLook,
    modifier: Modifier = Modifier,
    chipWidth: Dp? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier,
        //  横向间距为 0：框与框之间的空隙由那个 `×` 自己的左右内边距给，免得叠成两倍
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(CHIP_FLOW_GAP),
        //  首个是「前台应用 - 」那行字时，它与框的基线要看着一样高
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        names.forEachIndexed { at, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameChip(
                    text = name,
                    look = look,
                    modifier = if (chipWidth != null) Modifier.width(chipWidth) else Modifier,
                )
                if (at < names.lastIndex) {
                    Text(
                        text = COMBO_PAIR_SEPARATOR,
                        style = COUITheme.textStyles.headline1,
                        color = look.color,
                        modifier = Modifier.padding(horizontal = CHIP_SEPARATOR_GAP),
                    )
                }
            }
        }
    }
}
