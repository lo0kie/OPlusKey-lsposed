package com.lookie.opluskey.ui.row

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.present.COMBO_PAIR_SEPARATOR
import com.lookie.opluskey.ui.feedback.HintText
import com.lookie.opluskey.ui.model.page.opticalCenter
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「一枚名字框」—— 组合键的成员键名、场景条件里的应用名、弹窗里的变量清单共用这一份。
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
internal val CHIP_FLOW_GAP = 4.dp

/** 文案里「这里塞一枚名字框」的占位符 —— 与 `I18n.format` 的 `%s` 同一个写法。 */
internal const val CHIP_SLOT = "%s"

/**
 * 把「一段名字串」的占位模板展开成**逐个名字一枚框**的模板。
 *
 *  文案里那个占位符（组合键一般是 `%s`，带序号时是 `%2$s`）本意是「把这一串名字写进来」；
 *  要让每个名字各画一枚框，就得把它换成 `%s × %s`（`n` 个槽位，分隔符与 [comboLabelOf] 同一份）。
 *
 *  **只有一颗时不展开、也不给 chips**：与 [comboLabelOf] 的「单颗不套框」同一条规则 ——
 *  一个名字旁边没有第二个名字需要分开，框只剩噪声。
 *
 *  @param template 整句模板（含 [slot]）
 *  @param slot 那个名字串占位符
 *  @param names 名字（按显示顺序）
 *  @return 展开后的模板；调用方把它与 [names] 一起交给 `hint(chips = …)` /
 *   `ChipText(chips = …)`（单颗时 chips 传空表）
 */
internal fun namesToChipsTemplate(
    template: String,
    slot: String,
    names: List<String>,
): String =
    if (names.size <= 1) {
        template.replace(slot, names.firstOrNull().orEmpty())
    } else {
        template.replace(slot, names.joinToString(" $COMBO_PAIR_SEPARATOR ") { CHIP_SLOT })
    }

/** [namesToChipsTemplate] 的配套：名字够两颗才画框，单颗时不传 chips（见那条注释）。 */
internal fun chipsOfNames(names: List<String>): List<String> = if (names.size <= 1) emptyList() else names

/**
 * 把「模板 + 名字」还原成**一整句纯文字** —— 给那些只收 `String` 的通道用
 * （菜单行的 `disabledHint`：那一层没有 `chips` 位，收的是排队之前的一句话）。
 *
 *  与 `ChipText` 渲染出来的是同一句话，区别只在没有那几枚框 —— 宁可少一层样式，
 *  也不要让那几条通道继续自己拼一遍「名字怎么念」（判据就分成两份了）。
 */
internal fun HintText.plain(): String {
    if (chips.isEmpty()) return template
    var at = 0
    val out = StringBuilder(template.length)
    var i = 0
    while (i < template.length) {
        if (at < chips.size && template.startsWith(CHIP_SLOT, i)) {
            out.append(chips[at])
            at += 1
            i += CHIP_SLOT.length
        } else {
            out.append(template[i])
            i += 1
        }
    }
    return out.toString()
}

/** 文字段与名字框之间、框与框之间的空隙 —— 4dp 读起来是「两段话」而不是「一个词」。 */
internal val CHIP_TEXT_GAP = 4.dp

/**
 * **一句话里夹着几枚名字框** —— 模板里的 `%s` 按顺序换成 [chips]，装不下就换行。
 *
 *  用在「文案是模板、名字要画成标签」的地方（选择器那一列、操作提示那条胶囊）：
 *  两边各写一遍切分逻辑的话，「按 `%s` 切、去掉段首尾空格、模板与名字个数对不上怎么办」
 *  这三件事会各自漂移。文字段用 [textStyle] / [textColor]；框比它小半号（[chipStyleOf] 那一档）。
 *
 *  **模板与名字个数对不上**（改文案漏了一个 `%s`）时退回整句原文：宁可看见一个裸 `%s`，
 *  也不要把名字按错位置塞进句子里。
 */
@Composable
internal fun ChipText(
    template: String,
    chips: List<String>,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val parts = template.split(CHIP_SLOT)
    if (chips.isEmpty() || parts.size != chips.size + 1) {
        Text(
            text = template,
            style = textStyle,
            color = textColor,
            //  与别处同一档光学修正：行盒居中不等于墨迹居中（见 `opticalCenter`）
            modifier = modifier.opticalCenter(textStyle),
        )
        return
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CHIP_TEXT_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_TEXT_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { at, part ->
            val text = part.trim()
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                    //  **文字段也要光学修正**（`NameChip` 里那枚字是修过的）：一行里一半修、
                    //  一半不修时，文字段会比框里的字看着高一小截 —— 差值就是各自那 `dy`。
                    //  两边各按自己的样式算（框里那档小半号），所以不是「同一个偏移」。
                    modifier = Modifier.opticalCenter(textStyle),
                )
            }
            chips.getOrNull(at)?.let { name ->
                NameChip(
                    text = name,
                    look = ChipLook(chipStyleOf(textStyle), textColor, enabled),
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * 「几枚名字框 + 各自的说明」排成一行（装不下换行）—— **变量清单**那一行用它。
 *
 *  用在「这一格能写什么」那种清单上（提示文案弹窗里输入框下面那一行）：框里是占位符本身
 *  （`$key`），框后面跟着它换成什么（`键名`）。用框而不是纯文字，是因为这一行要读成
 *  「几个能填的变量」，而不是一句要连着读的话 —— 框的样式与组合键的键名同一份（[NameChip]）。
 *  给了 [onClick] 时每一枚框**可点**（点一下把那个变量接进输入框）：这一行于是既是说明、
 *  又是入口，省得用户照着抄一遍 `$gesture`。
 *
 *  与 [ChipText] 的差别只有一处：那里框是**插在一句话中间**的（说明在框前后都可能有），
 *  这里每枚框都跟着自己那句说明。所以两者不合并 —— 合并就得让每个调用点自己说「这段字是给
 *  框的还是给上下文的」。
 *
 *  模板与框的个数对不上（改文案时漏了一个 `%s`）时退回整句原文：宁可看见一个裸 `%s`，
 *  也不要把说明按错位置挂到别的框后面。
 *
 *  @param template 说明那一句，`%s` 是框的位置
 *  @param chips 框里的字，按模板里 `%s` 的先后一一对应
 *  @param onClick 点某一枚框做什么（拿到的是框里的字）—— 给了才可点
 */
@Composable
internal fun ChipLegend(
    template: String,
    chips: List<String>,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: ((String) -> Unit)? = null,
) {
    val parts = template.split(CHIP_SLOT)
    if (chips.isEmpty() || parts.size != chips.size + 1) {
        Text(
            text = template,
            style = textStyle,
            color = textColor,
            modifier = modifier.opticalCenter(textStyle),
        )
        return
    }
    val look = ChipLook(chipStyleOf(textStyle), textColor, enabled)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CHIP_TEXT_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_FLOW_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        //  模板开头那段（第一个 `%s` 之前）照原样画；`%s` 之间的每一段都归**前一枚框**，
        //  于是框与它那句说明绑成一组参与换行，不会出现「框在行尾、说明在下一行开头」。
        parts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { head ->
            Text(
                text = head,
                style = textStyle,
                color = textColor,
                modifier = Modifier.opticalCenter(textStyle),
            )
        }
        chips.forEachIndexed { at, name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHIP_TEXT_GAP),
            ) {
                NameChip(
                    text = name,
                    look = look,
                    modifier = Modifier,
                    onClick = onClick?.let { click -> { click(name) } },
                )
                val label = parts.getOrNull(at + 1)?.trim().orEmpty()
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = textStyle,
                        color = textColor,
                        modifier = Modifier.opticalCenter(textStyle),
                    )
                }
            }
        }
    }
}

/**
 * 框里那档样式 —— 行标题的**字号 × 0.85**、字重 Medium。
 *
 *  字重并进样式而不是写在 `Text` 的参数上：`opticalCenter` 与 `measure` 都按样式算，
 *  写在参数上会让量出来的是常规体、画出来是 Medium，差的那一点正好落在上下留白上。
 */
@Composable
internal fun chipStyle(): TextStyle = chipStyleOf(COUITheme.textStyles.headline1)

/**
 * 从**某一档行文字**算出名字框那档样式 —— 字号 × [CHIP_FONT_SCALE]、字重 Medium。
 *
 *  抽出来给「框要和旁边那行字一起排」的地方用（选择器里那一列：框比正文小半号才看得像一枚标签，
 *  同号时框与字一样重，整行读成三个等权的词）。比例仍是 [CHIP_FONT_SCALE] 那一份：
 *  同一个界面里的框不该有两种大小。
 */
internal fun chipStyleOf(base: TextStyle): TextStyle = base.copy(fontWeight = FontWeight.Medium, fontSize = base.fontSize * CHIP_FONT_SCALE)

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
 *  @param onClick 点了做什么 —— 只有「变量清单」那一档给（点一下把这一枚接到输入框末尾）。
 *   `null` = 这一枚只是**读**的（这一行叫哪几颗键），点它没有语义，连涟漪都不该有
 */
@Composable
internal fun NameChip(
    text: String,
    look: ChipLook,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
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
                //  可点那一档：`clip` 要在 `clickable` **之前**、`background` **之后** ——
                //  涟漪画在底色上、又被框的圆角裁掉（顺序反了就是一块方形的涟漪）。
                .then(if (onClick == null) Modifier else Modifier.clip(shape).clickable(onClick = onClick))
                //  内边距在 `clickable` 之后：触达范围含这一圈，框小字小也好点中
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

/**
 * 一句话里夹几枚名字框 —— 把模板按 `%s` 切成几段，框**排在字流里**（不是另起一格）。
 *
 *  给「按住 A 后再按 B」那一档用（组合列表的行、组合编辑页的顶栏副标题、两个确认窗的正文）。
 *  语序是翻译的事（模板里自带 `%s`），框是界面的事（[NameChip]）。这里不套统一的框宽 ——
 *  那是一列对齐用的（见 `comboTitleWidths`），而这一行是**一句话**，框跟着自己的字宽走才像句子里的名字。
 *
 * ## 为什么是行内占位，不是 `FlowRow`
 *
 *  `FlowRow` 把每一段文字当成**一个整体**：模板前半截一长（`%s 的长按目前会在按住到时就执行…`），
 *  那一整段放不进第一行，于是换行 —— 第一行只剩一枚框，读起来像「键名自己占了一行」
 *  （真机反馈「键名后面直接换行了，整行只剩键名」）。改成 `AnnotatedString` 的
 *  `appendInlineContent` 之后，框是**字流里的一个词**：它前面的字照常排，它后面的字接着同一行排，
 *  到行尾才折行 —— 与句子里的普通词一样。
 *
 *  代价是占位尺寸要自己量（[Placeholder] 只收像素）：宽度按这一枚框的字宽加上左右内边距与描边，
 *  高度同理。量的时候用**画框时同一份样式**（字重 Medium，见 [NameChip]），否则框会被撑窄或留白。
 *
 * @param style / @param color 句子本体那几段文字（框里的字色取 [look]）
 */
@Composable
internal fun ChipSentence(
    template: String,
    names: List<String>,
    look: ChipLook,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    /**
     * 这句话在整行里怎么对齐。
     *
     *  默认左对齐（列表行里那一句要从行首读起）；摆进**居中的那一段**（如「无法删除」弹窗里
     *  每一档组合各占一行）时传 [TextAlign.Center] —— 这个组件自己撑满宽度，光靠外层的
     *  `Arrangement.Center` 居中不了它里面的字。
     */
    textAlign: TextAlign = TextAlign.Start,
) {
    val parts = template.split(CHIP_SLOT)
    //  模板与名字数对不上、或者**有一个名字是空的**时，退回纯文字：`appendInlineContent` 的
    //  `alternateText` **不许为空串**（库直接抛 `IllegalArgumentException`，整页崩掉），
    //  而「这一条暂时还没有名字」是真实会发生的状态（弹窗退场那一帧载荷已经清了）。
    //  拼法就是把 `%s` 直接去掉（`split` 已经把它们切走了），句子读起来仍然是通顺的一句话。
    val usable = names.isNotEmpty() && parts.size == names.size + 1 && names.none { it.isBlank() }
    if (!usable) {
        BasicText(
            text = AnnotatedString(parts.joinToString("")),
            modifier = modifier.fillMaxWidth(),
            style = style.copy(color = color, textAlign = textAlign),
        )
        return
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    //  与 [NameChip] 画字那份一致（它会把字重并进样式）
    val inner = look.style.copy(fontWeight = FontWeight.Medium)
    val padH = with(density) { CHIP_H_PAD.roundToPx() }
    val padV = with(density) { CHIP_V_PAD.roundToPx() }
    //  描边 1dp 两边各一份 —— 不算进去的话框会比占位宽一点，行尾那枚容易被挤到下一行
    val border = with(density) { 1.dp.roundToPx() }
    //  `look` 不参与 `remember` 的键（它是每次组合新建的实例，放进去等于每帧重量一遍）；
    //  但框的颜色要跟着它走，所以用 `rememberUpdatedState` 让缓存的 lambda 读最新那一份。
    val currentLook by rememberUpdatedState(look)
    val inline =
        remember(names, inner, padH, padV, border, density) {
            names
                .mapIndexed { at, name ->
                    val measured = measurer.measure(AnnotatedString(name), style = inner)
                    chipSlotId(at) to
                        InlineTextContent(
                            //  `Placeholder` 要的是 `TextUnit`（新版 API），这里把算好的像素
                            //  按当前密度换回去 —— 用 sp 而不是 dp 只是它唯一的入口，不随字号缩放走
                            //  （`toSp()` 走的是 density 那一条，与 `fontScale` 无关）。
                            Placeholder(
                                width = with(density) { (measured.size.width + padH * 2 + border * 2).toSp() },
                                height = with(density) { (measured.size.height + padV * 2 + border * 2).toSp() },
                                //  框比这一行的字略高，居中对齐才不会让它压着基线往下沉
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            ),
                        ) { NameChip(text = name, look = currentLook, modifier = Modifier) }
                }.toMap()
        }
    val text =
        remember(template, names) {
            buildAnnotatedString {
                parts.forEachIndexed { at, part ->
                    append(part)
                    if (at < names.size) appendInlineContent(chipSlotId(at), names[at])
                }
            }
        }
    BasicText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = style.copy(color = color, textAlign = textAlign),
        inlineContent = inline,
    )
}

/** 行内占位的 id —— 只在本函数内配对（`appendInlineContent` 与 `inlineContent` 的键）。 */
private fun chipSlotId(index: Int): String = "chip:$index"
