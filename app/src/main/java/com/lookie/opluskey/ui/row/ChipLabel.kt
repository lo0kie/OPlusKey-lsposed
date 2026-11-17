package com.lookie.opluskey.ui.row

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.present.comboMemberSeparator
import com.lookie.opluskey.ui.feedback.HintText
import com.lookie.opluskey.ui.model.page.CARD_CORNER
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.style.tinted
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「几个名字」怎么排进一行 —— 提示条、选择器、变量清单、场景条件共用这一份排布。
//
// 名字就是**文字**：与旁边的句子同一档字号、同一条基线，读起来是一句话，
// 不是「几枚框拼起来的一行」—— 名字本身不带底色、圆角、描边、内边距。
// 留在这一层的只有「按 `%s` 切开、名字按顺序填、装不下怎么换行」这几件事：几处各写一遍，
// 切分规则与兜底迟早分叉，同一个界面里就会出现两种排法。

/** 名字与文字段之间、名字与名字之间的空隙 —— 换行时行与行之间也是这一档。 */
private val NAME_GAP = 4.dp

/** 文案里「这里填几个名字」的占位符 —— 与 `I18n.format` 的 `%s` 同一个写法。 */
internal const val CHIP_SLOT = "%s"

/**
 * 把「模板 + 名字」还原成**一整句纯文字** —— 给那些只收 `String` 的通道用
 * （菜单行的 `disabledHint`：那一层没有 `chips` 位，收的是排队之前的一句话）。
 *
 *  与 [ChipText] 填出来的是同一句话，区别只在没有按颗排布 —— 宁可少一层，
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

/**
 * **一句话里夹着几个名字** —— 模板里的 `%s` 按顺序换成 [chips]，装不下就换行。
 *
 *  用在「文案是模板、名字要单独归位」的地方（选择器那一列、操作提示那条胶囊）：
 *  两边各写一遍切分逻辑的话，「按 `%s` 切、去掉段首尾空格、模板与名字个数对不上怎么办」
 *  这三件事会各自漂移。文字段与名字用同一份 [textStyle] / [textColor]。
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
) {
    val parts = template.split(CHIP_SLOT)
    if (chips.isEmpty() || parts.size != chips.size + 1) {
        Text(text = template, style = textStyle, color = textColor, modifier = modifier)
        return
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(NAME_GAP),
        verticalArrangement = Arrangement.spacedBy(NAME_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { at, part ->
            val text = part.trim()
            if (text.isNotEmpty()) {
                Text(text = text, style = textStyle, color = textColor)
            }
            chips.getOrNull(at)?.let { name ->
                //  **不叠 `fillMaxWidth()`**：名字按字宽走。在 `FlowRow` 里子项拿到的是整行
                //  可用宽，`fillMaxWidth()` 会把每一个名字都撑成整行宽。
                Text(text = name, style = textStyle, color = textColor)
            }
        }
    }
}

/**
 * 「几个名字 + 各自的说明」排成一行（装不下换行）—— **变量清单**那一行用它。
 *
 *  用在「这一格能写什么」那种清单上（提示文案弹窗里输入框下面那一行）：前面是占位符本身
 *  （`$key`），后面跟着它换成什么（`键名`）。用这一档而不是整句，是因为这一行要读成
 *  「几个能填的变量」，而不是一句要连着读的话。给了 [onClick] 时每一个名字**可点**
 *  （点一下把那个变量接进输入框）：这一行于是既是说明、又是入口，省得用户照着抄一遍 `$gesture`。
 *
 *  与 [ChipText] 的差别只有一处：那里名字**插在一句话中间**（说明在名字前后都可能有），
 *  这里每个名字都跟着自己那句说明。所以两者不合并 —— 合并就得让每个调用点自己说「这段字是给
 *  名字的还是给上下文的」。
 *
 *  模板与名字个数对不上（改文案时漏了一个 `%s`）时退回整句原文：宁可看见一个裸 `%s`，
 *  也不要把说明按错位置挂到别的名字后面。
 *
 *  @param template 说明那一句，`%s` 是名字的位置
 *  @param chips 名字，按模板里 `%s` 的先后一一对应
 *  @param onClick 点某一个名字做什么（拿到的是那个名字）—— 给了才可点
 */
@Composable
internal fun ChipLegend(
    template: String,
    chips: List<String>,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: ((String) -> Unit)? = null,
) {
    val parts = template.split(CHIP_SLOT)
    if (chips.isEmpty() || parts.size != chips.size + 1) {
        Text(text = template, style = textStyle, color = textColor, modifier = modifier)
        return
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(NAME_GAP),
        verticalArrangement = Arrangement.spacedBy(NAME_GAP),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        //  模板开头那段（第一个 `%s` 之前）照原样画；`%s` 之间的每一段都归**前一个名字**，
        //  于是名字与它那句说明绑成一组参与换行，不会出现「名字在行尾、说明在下一行开头」。
        parts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { head ->
            Text(text = head, style = textStyle, color = textColor)
        }
        chips.forEachIndexed { at, name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NAME_GAP),
            ) {
                Text(
                    text = name,
                    style = textStyle,
                    color = textColor,
                    modifier = if (onClick == null) Modifier else Modifier.clickable { onClick(name) },
                )
                val label = parts.getOrNull(at + 1)?.trim().orEmpty()
                if (label.isNotEmpty()) {
                    Text(text = label, style = textStyle, color = textColor)
                }
            }
        }
    }
}

/**
 * 一串名字 —— **装不下就换行**（用户口径：组合列表与场景条件都要换行）。
 *
 *  为什么不是一个 `Row`：`.weight(fill = false)` 那种做法在名字多时会把每个名字压到 0 宽 ——
 *  名字被省略没了，用户什么也读不到。换成换行之后，每个名字都保有自己的字宽，多出来的排到下一行。
 *
 *  ## 分隔符跟在前一个名字后面
 *
 *  分隔符作为**前一个名字的尾巴**参与换行，而不是独立一项：独立一项时换行会让某一行以分隔符
 *  开头，读起来像漏了一个名字。跟在前一个后面，行尾最多出现一个分隔符，方向是清楚的。
 *
 *  @param style / @param color 名字那一档的样式与颜色
 *  @param leading 排在最前面的那一项（场景条件的「前台应用 - 」）；它同样参与换行
 *  @param separator 名字之间的分隔符 —— 默认「和」（一起按下）；按顺序触发那一档传「→」
 */
@Composable
internal fun NameFlow(
    names: List<String>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    separator: String = comboMemberSeparator,
) {
    FlowRow(
        modifier = modifier,
        //  横向间距为 0：名字之间的空隙由那个分隔符自己给，免得叠成两倍
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(NAME_GAP),
        //  首个是「前台应用 - 」那行字时，它与名字的基线要看着一样高
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        names.forEachIndexed { at, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = name, style = style, color = color)
                if (at < names.lastIndex) {
                    Text(
                        //  **符号两边各带一个空格**：与纯文本那几处（`comboLabel` / `comboTitleText`）
                        //  同一个写法 —— 一处「A和B」、一处「A 和 B」摆在一起就是两种排法。
                        //  间距全靠这个空格给（原先另有一档 6dp 的 padding，两者叠加会太松）。
                        text = " $separator ",
                        //  与名字**同档样式**：分隔符比名字大一号时，读起来像「小名字夹在大字里」
                        style = style,
                        color = color,
                    )
                }
            }
        }
    }
}

/**
 * 一句话里夹几个名字 —— 把模板按 `%s` 切成几段，名字**排在字流里**（不是另起一格）。
 *
 *  给「按住 A 后再按 B」那一档用（组合列表的行、组合编辑页的顶栏副标题、两个确认窗的正文）。
 *  语序是翻译的事（模板里自带 `%s`），名字是界面的事（[Text]）。名字跟着自己的字宽走才像句子里的名字。
 *
 * ## 为什么是行内占位，不是 `FlowRow`
 *
 *  `FlowRow` 把每一段文字当成**一个整体**：模板前半截一长（`%s 的长按目前会在按住到时就执行…`），
 *  那一整段放不进第一行，于是换行 —— 第一行只剩一个名字，读起来像「键名自己占了一行」
 *  （真机反馈「键名后面直接换行了，整行只剩键名」）。改成 `AnnotatedString` 的
 *  `appendInlineContent` 之后，名字是**字流里的一个词**：它前面的字照常排，它后面的字接着同一行排，
 *  到行尾才折行 —— 与句子里的普通词一样。
 *
 *  代价是占位尺寸要自己量（`Placeholder` 只收像素）：按名字本身的字宽与行高。
 *  量的时候用的就是**画字那一份样式**（同一个 `style`），否则占位会被撑窄或留白。
 *
 * @param style / @param color 这一句本体的样式与颜色（名字与它同一份）
 */
@Composable
internal fun ChipSentence(
    template: String,
    names: List<String>,
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
    //  名字与句子**同一份样式**（连字重都不改）：占位框按这个名字自己的字宽与行盒量，
    //  框高与这一行的行盒同高，`Center` 之后名字的行盒与句子那段完全重合，基线不会错开。
    val inline =
        remember(names, style, density) {
            names
                .mapIndexed { at, name ->
                    val measured = measurer.measure(AnnotatedString(name), style = style)
                    chipSlotId(at) to
                        InlineTextContent(
                            //  `Placeholder` 要的是 `TextUnit`（新版 API），这里把算好的像素
                            //  按当前密度换回去 —— 用 sp 而不是 dp 只是它唯一的入口，不随字号缩放走
                            //  （`toSp()` 走的是 density 那一条，与 `fontScale` 无关）。
                            Placeholder(
                                width = with(density) { measured.size.width.toSp() },
                                height = with(density) { measured.size.height.toSp() },
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            ),
                        ) {
                            Text(text = name, style = style, color = color)
                        }
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

// 瓦片类小件：格子尺寸常量、行首图标按钮、焦点圈。

/**
 * 一个格子的高度：`BasicComponent` 的最小高度 48dp + `CardListPosition.Full`
 * 上下各补的 2dp（COUI 的 `coui_list_card_head_or_tail_padding`）。
 * 一屏放得下十来行（这一页是 `verticalScroll`，真放不下也能滚）。
 */
internal val TILE_HEIGHT = 52.dp

internal val TILE_SIDE_PADDING = 12.dp

/**
 * 行尾那颗图标键的**基准边长** —— 一行里并排三颗时按可用宽度往下缩（见 `CommandSteps` 的
 * `stepButtonSize`），放得下就用这一档。
 *
 *  与图标 20dp 配成一对（0.625 的比例），缩的时候图标一起缩 —— 只缩格子会让高亮圈贴着字形。
 */
internal val ICON_ACTION_SIZE = 32.dp

/**
 * 行首 / 行内图标「可用 / 禁用」两档配色 —— 全项目一处。
 *
 *  这一对色（`onSurfaceContainer` / `disabledOnSecondaryVariant`）在开关行、滑杆行、输入框行首
 *  各写了一遍，值还一样：同一页里几个行首图标一拨主开关就该一起变，分散写时改一处就漏一处。
 *
 *  注意：**不是所有图标都走这一对**：行尾那几颗（`onSurfaceVariantActions` 起头）、
 *  选中态那几颗（`onTertiaryContainer`）、以及带 `error` / `tint` 覆盖的那几档各有自己的调色，
 *  它们**不**并到这里来 —— 只有「行首图标 + 只有可用/禁用两态」才是这一对。
 *
 *  @param label 动画标签，便于在 Studio 的动画检查器里分辨是哪一个调用点
 *  @param override 行自己指定的颜色（强调色色块那几行）—— 给了就以它为准，不再看后面几档
 *  @param selected 选中态（网格里选中那一格）—— 只在启用时生效，用主色把它与「只是能用」分开
 */
@Composable
internal fun rememberRowIconColor(
    enabled: Boolean,
    /**
     * 这一行的图标 —— 给了才参与「随机图标配色」（见 `LocalIconTint`）。
     *  `null` = 这一处没有具体图标（或不想让它参与），照旧走主题色。
     */
    icon: ImageVector? = null,
    override: Color? = null,
    selected: Boolean = false,
    label: String = "RowIcon",
): State<Color> =
    animateColorAsState(
        targetValue =
            override
                ?: when {
                    //  禁用优先于选中：灰着的一格不该因为「还记着选过它」而亮主色
                    !enabled -> {
                        COUITheme.colorScheme.disabledOnSecondaryVariant
                    }

                    selected -> {
                        COUITheme.colorScheme.primary
                    }

                    else -> {
                        icon?.takeIf { enabled }?.tinted()
                            ?: COUITheme.colorScheme.onSurfaceContainer
                    }
                },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = label,
    )

/**
 * 格子外面的**焦点圈**（键盘 / 遥控走焦点时那一圈高亮）。
 *
 * 圈的**亮起 / 熄灭带过渡**（`primary` / 透明）—— 硬切的话它会「啪」地跳出来，
 * 而卡片里的标题 / 类型图标这时正在**渐变**（见 `CommandScreen` 里那处），
 * 两者不同步就很难看。过渡参数接入统一的 [COLOR_TRANSITION_SPEC]。
 */
@Composable
internal fun TileFocusRing(focused: Boolean) {
    val ringColor by animateColorAsState(
        targetValue = if (focused) COUITheme.colorScheme.primary else Color.Transparent,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "TileFocusRing",
    )
    Box(
        Modifier
            .fillMaxSize()
            .squircleBorder(
                width = 2.dp,
                color = ringColor,
                cornerRadius = CARD_CORNER,
            ),
    )
}
