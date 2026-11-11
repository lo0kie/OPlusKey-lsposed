package com.lookie.opluskey.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
 * 副标题那一行（有的话）—— 一整句话，按 [ROW_TEXT_MAX_LINES] 折行、超出省略。
 *
 *  拆出来只为让 [ItemText] 留在 `LongMethod` 的长度线内。
 */
@Composable
internal fun ItemSummary(
    row: CardRow.Item,
    summaryColor: Color,
) {
    val summary = row.summary ?: return
    Text(
        text = summary,
        modifier = Modifier.padding(top = ROW_LINE_GAP),
        style = COUITheme.textStyles.body2,
        color = summaryColor,
        maxLines = ROW_TEXT_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

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
