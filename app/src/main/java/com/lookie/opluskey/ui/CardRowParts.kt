package com.lookie.opluskey.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.COLOR_FADE_SPEC
import com.lookie.opluskey.ui.model.ROW_START_ICON_GAP
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// `CardRow` 行内那两块**自己画的**部件：副标题那一行、行首那一格。
//
//  从 `CardRow.kt` 拆出来：那一份文件已经贴着「一个文件 10 个函数」的线，而这两块是
//  「行里长什么样」的事，与列表/动画/状态不是同一件事。

/**
 * 副标题那一行（有的话）。
 *
 *  没挂 `summaryExtra` 时是一整句话；挂了就是「左句 + 右块」两块同行 —— 拆出来只为让
 *  [ItemText] 留在 `LongMethod` 的长度线内。
 */
@Composable
internal fun ItemSummary(
    row: CardRow.Item,
    summaryColor: Color,
) {
    val summary = row.summary ?: return
    val extra = row.summaryExtra
    if (extra == null) {
        Text(
            text = summary,
            modifier = Modifier.padding(top = ROW_LINE_GAP),
            style = COUITheme.textStyles.body2,
            color = summaryColor,
        )
        return
    }
    //  两块同行：**左边占满、右边贴着行尾**（`weight(1f)` 默认 `fill = true`）。
    //  这里要的正是「顶到边再裁」—— 早先照标题行那套写成 `fill = false`，副标题会按**自己的
    //  自然宽度**收住，于是名字还没到行尾就出现省略号、右边空一截（真机口径「还没到顶就裁了」）。
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ROW_LINE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            style = COUITheme.textStyles.body2,
            color = summaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(SUMMARY_EXTRA_GAP))
        extra()
    }
}

/** 副标题那两块之间的间距 —— 名字的省略号与右边那一段之间留一点，读起来才是两段。 */
private val SUMMARY_EXTRA_GAP = 6.dp

/**
 * 行首那一格 —— 自己画的（`startIcon`，应用图标那种）优先，否则是 `ImageVector` 那颗。
 *
 *  两者**都要补一档 [ROW_START_ICON_GAP]**：库在 `startAction` 与文字之间只留 8dp，而行内左边距
 *  是 16dp —— 不补的话图标左边看着比右边宽一倍（见那个常量的说明）。两种行首走同一档，
 *  同一张卡里的左缘才对得齐。
 *
 *  抽出来只为让 [ItemRow] 留在 detekt 的 `LongMethod` 长度线内。
 */
@Composable
internal fun cardRowStartAction(
    row: CardRow.Item,
    iconTint: Color,
): (@Composable () -> Unit)? {
    val startIcon = row.startIcon
    if (startIcon != null) {
        return {
            Box(Modifier.padding(end = ROW_START_ICON_GAP)) { startIcon() }
        }
    }
    val glyph = row.icon ?: return null
    return {
        //  字形会随状态换（停用的键换成禁用图标、序号键换成禁用图标那几处）：形状没法插值，
        //  只能交叉淡过去 —— 与旁边那片图标色同一条曲线（[COLOR_FADE_SPEC]），否则同一次切换
        //  看着是「色在渐变、字形啪地换掉」。两种字形同尺寸，交叉期间这一格宽度不变。
        Crossfade(
            targetState = glyph,
            animationSpec = COLOR_FADE_SPEC,
            label = "CardRowIconGlyph",
        ) { shown ->
            Icon(
                shown,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.padding(end = ROW_START_ICON_GAP),
            )
        }
    }
}
