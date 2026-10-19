package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.COMMAND_FORMAT_LINES
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.opticalTextCenter
import com.lookie.opluskey.ui.state.CommandUiState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「支持的命令格式」卡 —— 说的是「命令输入框里能写什么」。
 *
 *  点某一行 = 把它讲的前缀**整段替换**进「命令」输入框（不是追加 —— 前缀拼在已有内容前面会得到
 *  一条跑不通的命令）。输入框里已经有别的内容时先问一句（`ui.pendingFormatPrefix`）：
 *  这一下是不可逆的，已经敲进去的命令会被一键清掉，没有撤销。
 *
 * @param onScrollTop 填完之后滚回顶部 —— 输入框在这张卡上面，不滚回去看不出改没改。
 */
@Composable
internal fun SettingsPage.CommandFormatsCard(
    tick: () -> Unit,
    ui: CommandUiState,
    onScrollTop: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        Column(
            // 上下只给 8dp：每行固定 [FORMAT_ROW_TOUCH]（32dp）高，小字本身约 17dp，
            // 字与行上下边天然各留约 7.5dp —— 加上 8dp ≈ 左右那 16dp 一档。
            Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        ) {
            COMMAND_FORMAT_LINES.forEachIndexed { index, line ->
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
                                    fillPrefix(prefix, ui, onScrollTop)
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = line.text,
                        // 文字占满整行（点击区在整行上）；行高固定 32dp、字在里面居中，
                        //  汉字墨迹比行盒中心高一档，沉 0.1em 才与两条分隔线等距（[opticalTextCenter]）
                        modifier =
                            Modifier
                                .weight(1f)
                                .opticalTextCenter(COUITheme.textStyles.footnote1),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
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
    onScrollTop: () -> Unit,
) {
    val current = commandDraft.draft.single
    if (current.isNotBlank() && current != prefix) {
        XLog.w("命令格式：输入框里已有内容，填前缀 $prefix 之前先问一句")
        ui.pendingFormatPrefix = prefix
        return
    }
    XLog.w("命令格式：把前缀 $prefix 填进「命令」输入框")
    commandDraft.draft.single = prefix
    onScrollTop()
}
