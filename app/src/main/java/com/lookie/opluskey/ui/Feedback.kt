package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.APP_STATE_ICON_SIZE
import com.lookie.opluskey.ui.model.APP_STATE_TEXT_GAP
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「这里什么都没有 / 正在等」那一块 —— 一颗字形 + 一句标题（可选）+ 一句说明。
 *
 *  全项目**只有这一份**排版：字形 32dp 定框、标题 `body1`、说明 `footnote1` 次要色、整体居中、
 *  说明左右各留 [PAGE_MARGIN] + 16dp（长句到边会难读）。三处空态都走它：
 *  「选择应用」那三块（加载中 / 没查到 / 搜不到）、「条件执行」的空表、「已保存的命令」的空网格。
 *
 *  字形用**插槽**而不是 `ImageVector`：选择应用那块要放转圈（`RotatingProgressIndicator`），
 *  那不是图标。取色由这一份给（次要文字色），调用点只管画形状。
 *
 *  字形占一个**定死的方框**：几种状态互相切换时，下面那行字不会跟着上下跳。
 *
 * @param glyph 上面那颗字形（图标 / 转圈），参数是它该用的颜色
 * @param text 下面那句说明
 * @param title 上面那句标题（`null` = 不要标题，只有一行说明）
 */
@Composable
internal fun Feedback(
    glyph: @Composable (Color) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(APP_STATE_ICON_SIZE),
            contentAlignment = Alignment.Center,
        ) { glyph(COUITheme.colorScheme.onSurfaceVariantSummary) }
        Spacer(Modifier.height(APP_STATE_TEXT_GAP))
        if (title != null) {
            Text(
                text = title,
                style = COUITheme.textStyles.body1,
                //  与下面那句**同色**（用户口径）：空态整块是「说明」而不是「标题 + 正文」，
                //  上面那行只用字号拉开层次，不再用更重的颜色抢注意力。
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            //  标题与说明之间那 2dp = 卡片里「标题 / 摘要」那一档，全项目同一个呼吸
            Spacer(Modifier.height(FEEDBACK_TITLE_GAP))
        }
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = PAGE_MARGIN + 16.dp),
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 标题与说明之间的距离 —— 2dp，与卡片里「标题 / 摘要」那一档同值。 */
private val FEEDBACK_TITLE_GAP = 2.dp
