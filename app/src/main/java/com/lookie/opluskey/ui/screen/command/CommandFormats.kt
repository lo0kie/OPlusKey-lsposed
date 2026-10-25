package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.COMMAND_FORMAT_LINES
import com.lookie.opluskey.ui.model.CommandFormatLine
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「支持的命令格式」卡 —— 说的是「命令输入框里能写什么」。
 *
 *  排成**两列**：左列是写法本身（`shell:<命令>`），右列是它干什么（`执行 shell 命令`）。
 *  左列宽度是**量出来的**（取最宽那一行的墨迹宽度）：写死一个 dp，换语言（`keyevent:<按键>` ↔
 *  `keyevent:<key>`）或系统放大字号都会错位，而两列一旦不齐，整张卡就成了斜的。
 *
 *  点某一行 = 把它讲的前缀**整段替换**进「命令」输入框（不是追加 —— 前缀拼在已有内容前面会得到
 *  一条跑不通的命令）。输入框里已经有别的内容时先问一句（`ui.pendingFormatPrefix`）：
 *  这一下是不可逆的，已经敲进去的命令会被一键清掉，没有撤销。
 *
 *  填完之后**输入框自己**会滚回视野、拿焦点（键盘起来）、把光标挪到末尾 —— 三件事都挂在
 *  [CommandUiState.singleFocusRequest] 上，由输入框那一侧做（见 `SingleCommandField`）：
 *  这张卡够不着输入框的 `FocusRequester`，而「填了字却停在半屏外、键盘也没起来」正是
 *  用户点完这一行的下一个动作。
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
            Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        ) {
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider()
                val prefix = line.prefix
                Row(
                    // 每行都撑到 [FORMAT_ROW_TOUCH]，行高一致，也是整行触达高度
                    Modifier
                        .fillMaxWidth()
                        .height(FORMAT_ROW_TOUCH)
                        .then(
                            if (prefix == null) {
                                Modifier
                            } else {
                                // 涟漪走 `LocalIndication`（与库可点组件同一份）
                                Modifier.clickable(
                                    onClickLabel = I18n.format(Strings.COMMAND_PREFIX_FILL, prefix),
                                ) {
                                    tick()
                                    fillPrefix(prefix, ui)
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
        }
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
 * 把 [prefix] 填进「命令」输入框 —— **已有内容就先问**。
 *
 *  判据是「非空且不等于这个前缀」：输入框里正好就是这个前缀时再点一次，替换的是它自己，
 *  没有东西可丢，不该拦着问。
 */
private fun SettingsPage.fillPrefix(
    prefix: String,
    ui: CommandUiState,
) {
    val current = commandDraft.draft.single
    if (current.isNotBlank() && current != prefix) {
        XLog.w("命令格式：输入框里已有内容，填前缀 $prefix 之前先问一句")
        ui.pendingFormatPrefix = prefix
        return
    }
    applyFormatPrefix(prefix, ui)
}

/**
 * 前缀**真的**写进输入框 —— 直接点那一行与「替换」确认窗共用一个出口。
 *
 *  除了改草稿，还要举手让输入框接管下一拍（[CommandUiState.singleFocusRequest]）：滚回视野、
 *  拿焦点、光标落到末尾。这两件事必须一起发生 —— 只填字不聚焦的话，用户看着一行命令出现在
 *  半屏外，还得自己点一下那个框才能接着打。
 *
 *  滚回顶部**也交给输入框那一侧**（不在这里调 `animateScrollTo`）：两处都滚的话，
 *  焦点那次 `bringIntoView` 会和滚动动画抢同一根滚动条，看着像卡了一下。
 */
internal fun SettingsPage.applyFormatPrefix(
    prefix: String,
    ui: CommandUiState,
) {
    setFormatPrefix(prefix)
    ui.singleFocusRequest++
}

/**
 * 只改草稿，**不举手** —— 「替换」确认窗那一支要的就是这个：字立刻换掉（用户在窗后面看得见），
 * 但聚焦得等那个窗**收场之后**再举手。
 *
 *  确认窗是独立窗口，库在退场动画结束时会把键盘收掉。退场途中就叫键盘的话，窗一销毁平台
 *  顺手带走它 —— 真机表现是「键盘弹出来又立刻关掉」（见 `ConfirmDialog` 的
 *  `onDismissFinished`）。所以那一支把「举手」挪到退场结束的回调里。
 */
internal fun SettingsPage.setFormatPrefix(prefix: String) {
    XLog.w("命令格式：把前缀 $prefix 填进「命令」输入框")
    commandDraft.draft.single = prefix
}
