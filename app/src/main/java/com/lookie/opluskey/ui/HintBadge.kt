package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.BadgeDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「有新版本」那颗提示点 —— 全项目一处画法。
//
//  两处用它：卡片行的行尾（`CardRow.Item.endBadge`）与版本菜单里那一行的 `hint` 槽。
//  点本身只有 6dp，颜色和尺寸必须两处一致 —— 同一条信息在两个地方长得不一样会读成两件事。

/**
 * 一颗提示点（COUI [Badge] 的纯圆点档）。
 *
 *  ## 颜色为什么不吃 `BadgeDefaults` 的默认值
 *
 *  库内置那两颗红（`#EB3B2F` / `#EB493D`）是写死的常量，与主题色板无关 ——
 *  系统配色（Monet）一换，整页都跟着变，只有这一点还停在原来的红，看着像「没跟上主题」。
 *  这里改用 `colorScheme.error`：它本来就是「需要留意」的那一档，而且跟着色板走，
 *  深浅两套都够亮（浅色 `#DB382C`、深色 `#FF6C61`）。
 */
@Composable
internal fun HintBadge(modifier: Modifier = Modifier) {
    Badge(
        modifier = modifier,
        colors = BadgeDefaults.badgeColors(containerColor = COUITheme.colorScheme.error),
    )
}
