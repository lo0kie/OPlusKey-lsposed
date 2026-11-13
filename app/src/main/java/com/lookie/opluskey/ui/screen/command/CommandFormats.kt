package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.icon
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.page.opticalCenter
import com.lookie.opluskey.ui.model.text.COMMAND_FORMAT_LINES
import com.lookie.opluskey.ui.model.theme.CommandFormatLine
import com.lookie.opluskey.ui.state.command.CommandUiState
import com.lookie.opluskey.ui.style.tinted
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「支持的命令格式」卡 —— 说的是「命令输入框里能写什么」。
 *
 *  排成**两列**：左列是写法本身（`shell:<命令>`），右列是它干什么（`执行 shell 命令`）。
 *  左列宽度是**量出来的**（取最宽那一行的墨迹宽度）：写死一个 dp，换语言（`keyevent:<按键>` 与
 *  `keyevent:<key>`）或系统放大字号都会错位，而两列一旦不齐，整张卡就成了斜的。
 *
 *  点某一行 = 把它讲的前缀填进「命令」输入框：输入框里**已经带了别的格式**时只换开头那一段前缀，
 *  后面的正文留着（见 [fillPrefix]）；不带格式的别的内容会被**整段换掉**（不是追加 —— 前缀拼在
 *  已有内容前面会得到一条跑不通的命令），所以那一种情况先问一句（`ui.pendingFormatPrefix`）：
 *  这一下是不可逆的，已经敲进去的命令会被一键清掉，没有撤销。
 *
 *  填完之后**输入框自己**会滚回视野 —— 挂在 [CommandUiState.singleScrollRequest] 上，由输入框
 *  那一侧做（见 `SingleCommandField`）：这张卡够不着页面的滚动状态。**焦点不动**：
 *  用户点的是格式，不是要在这儿打字（见 `applyFormatPrefix`）。
 */
@Composable
internal fun SettingsPage.CommandFormatsCard(
    tick: () -> Unit,
    ui: CommandUiState,
) {
    val lines = COMMAND_FORMAT_LINES
    val style = COUITheme.textStyles.footnote1
    val labelColumn = rememberLabelColumnWidth(lines, style)
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        Column(
            // 上下只给 8dp：每行固定 [FORMAT_ROW_TOUCH]（32dp）高，小字本身约 17dp，
            // 字与行上下边天然各留约 7.5dp —— 加上 8dp ≈ 左右那 16dp 一档。
            //
            // 左右给的是 `FORMAT_ROW_PADDING - FORMAT_ROW_INSET`：**文字**仍落在 16dp 那条竖线上，
            // 而每行自己的圆角底色 / 涟漪比文字往外伸了 [FORMAT_ROW_INSET]，两段加起来还是 16。
            Modifier.padding(
                start = FORMAT_ROW_PADDING - FORMAT_ROW_INSET,
                top = 8.dp,
                end = FORMAT_ROW_PADDING - FORMAT_ROW_INSET,
                bottom = 8.dp,
            ),
        ) {
            lines.forEachIndexed { index, line ->
                //  分隔线跟**文字**那一档对齐（不跟着行的外扩走）：行按下时那一块比线更宽，
                //  正是「一整行是一块」的样子；线跟着外扩会顶到卡片边上，与卡片自己的边线打架。
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = FORMAT_ROW_INSET))
                FormatRow(
                    line = line,
                    labelColumn = labelColumn,
                    style = style,
                    onFill = { prefix ->
                        tick()
                        fillPrefix(prefix, ui)
                    },
                )
            }
        }
    }
}

/**
 * 「支持的命令格式」卡里的**一行** —— 行首图标 + 左列写法 + 右列说明。
 *
 *  一行里的三块本来就自成一体（图标是图例记号、左列是要照抄的东西、右列是解释），
 *  拆开读也更顺。
 *
 * @param onFill 这一行被点中：把它的前缀填进输入框（`prefix == null` 的行不可点）
 */
@Composable
private fun FormatRow(
    line: CommandFormatLine,
    labelColumn: Dp,
    style: TextStyle,
    onFill: (String) -> Unit,
) {
    val prefix = line.prefix
    Row(
        // 每行都撑到 [FORMAT_ROW_TOUCH]，行高一致，也是整行触达高度
        Modifier
            .fillMaxWidth()
            .height(FORMAT_ROW_TOUCH)
            //  正圆角就够了：这一块四周离卡片边还有 `FORMAT_ROW_INSET`，裁不到
            //  （「必须超椭圆」那条说的是贴着卡片轮廓画描边的情形，见 `FORMAT_ROW_CORNER`）。
            .clip(RoundedCornerShape(FORMAT_ROW_CORNER))
            .then(
                if (prefix == null) {
                    Modifier
                } else {
                    // 涟漪走 `LocalIndication`（与库可点组件同一份）
                    Modifier.clickable(
                        onClickLabel = I18n.format(Strings.COMMAND_PREFIX_FILL, prefix),
                    ) {
                        onFill(prefix)
                    }
                },
            )
            //  内容再让回来：行的圆角底色比文字宽 [FORMAT_ROW_INSET]，文字得缩进同一档
            //  才仍在卡片那条 16dp 的竖线上（父容器减掉的正是这一档）。
            .padding(horizontal = FORMAT_ROW_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        //  行首那颗图标 —— 一行一颗，扫一眼就能分出「这行是哪一种」。
        //  颜色：**跟着「随机图标配色」走**（`tinted()`，按图标名取色 —— 每颗名字不同，
        //  于是各是一色）；那一档关着时退回说明那一档的中性色（它是记号，不是要照抄的内容）。
        Icon(
            line.icon,
            contentDescription = null,
            modifier = Modifier.size(FORMAT_ROW_ICON),
            tint = line.icon.tinted() ?: COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(FORMAT_ICON_GAP))
        Text(
            text = line.label,
            // 行高固定 32dp、字在里面居中 —— 行盒居中不等于墨迹居中，
            //  所以再补一档光学修正（见 `opticalCenter`）。两列都要补：
            //  只补一列等于把另一列从基线上挪开。
            modifier = Modifier.width(labelColumn).opticalCenter(style),
            style = style,
            // 左列是「要照抄的东西」，比右边的说明亮一档
            color = COUITheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = line.desc,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = FORMAT_COLUMN_GAP)
                    .opticalCenter(style),
            style = style,
            // 右列靠行尾右对齐：左右两条边界都是直的，从上往下扫不会觉得在飘
            textAlign = TextAlign.End,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

/**
 * 左列该多宽 —— 取所有写法里最宽那一条的**墨迹宽度**。
 *
 *  用 `TextMeasurer` 而不是固定 dp：宽度跟着当前 `TextStyle`、密度、字体放大一起变。
 *  键上 `lines`（列表内容）与 `style`：换语言、换字号会重算，纯重组则直接命中。
 */
@Composable
private fun rememberLabelColumnWidth(
    lines: List<CommandFormatLine>,
    style: TextStyle,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(lines, style, density) {
        val widest = lines.maxOf { measurer.measure(it.label, style).size.width }
        with(density) { widest.toDp() }
    }
}

/**
 * 把 [prefix] 填进「命令」输入框。
 *
 *  分三种情况：
 *  - 输入框里**已经带了前缀**（`Action.prefixOf` 认得出来）：只把开头那一段前缀换掉，
 *    后面的正文原样留着（`shell:pm list` 点 `keyevent:` ⇒ `keyevent:pm list`）。没有东西可丢，
 *    不弹确认窗 —— 用户点格式列表的意图就是「换个格式」，重打一遍正文正是这一步想省掉的。
 *  - 输入框里是**不带格式的别的内容**：整段替换不可逆，先问一句（`ui.pendingFormatPrefix`）。
 *  - 空着 / 正好就是这个前缀：直接填，不必问。
 */
private fun SettingsPage.fillPrefix(
    prefix: String,
    ui: CommandUiState,
) {
    val current = commandDraft.draft.single.trim()
    val existing = Action.prefixOf(current)
    if (existing != null) {
        XLog.w("命令格式：输入框已带前缀 $existing，只把它换成 $prefix")
        applyFormatPrefix(prefix + current.removePrefix(existing).trim(), ui)
        return
    }
    if (current.isNotEmpty()) {
        XLog.w("命令格式：输入框里已有内容，填前缀 $prefix 之前先问一句")
        ui.pendingFormatPrefix = prefix
        return
    }
    applyFormatPrefix(prefix, ui)
}

/**
 * 前缀**真的**写进输入框 —— 直接点那一行与「替换」确认窗共用一个出口。
 *
 *  除了改草稿，还要举手让输入框那一侧把页面滚回顶部（[CommandUiState.singleScrollRequest]）：
 *  输入框在这张卡上面，填完不滚回去用户只看到格式卡还在原地、不知道字填哪去了。
 *
 *  **不聚焦**（用户口径：点命令格式不再聚焦）：点一行格式是「挑一个前缀」，不是「要在这儿打字」，
 *  抢焦点会把键盘顶上来、正好盖住这张格式卡。
 *
 *  滚回顶部**也交给输入框那一侧**（不在这里调 `animateScrollTo`）：两处都滚的话，
 *  同一个滚动条上会有两个动画在抢，看着像卡了一下。
 */
internal fun SettingsPage.applyFormatPrefix(
    prefix: String,
    ui: CommandUiState,
) {
    setFormatPrefix(prefix)
    ui.singleScrollRequest++
}

/**
 * 只改草稿，**不举手** —— 「替换」确认窗那一支要的就是这个：那次滚回顶部得等窗**收场之后**
 * 再举手（窗是模态的，滚在窗后面没人看得见）。
 */
internal fun SettingsPage.setFormatPrefix(prefix: String) {
    XLog.w("命令格式：把前缀 $prefix 填进「命令」输入框")
    commandDraft.draft.single = prefix
}
