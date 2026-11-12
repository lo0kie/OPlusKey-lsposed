package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.ui.ChipFlow
import com.lookie.opluskey.ui.ChipLook
import com.lookie.opluskey.ui.chipStyle
import com.lookie.opluskey.ui.model.conditionKindLabel
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 条件那一行的标题 —— 从 `CommandBranchParts.kt` 拆出来：那边函数数已经贴着 detekt 的
// `TooManyFunctions` 上限，而这一支（框 + 分隔符）本来就自成一件小事。

/**
 * 条件那一行的**标题** —— 「前台应用」这几行按框画应用名，其余仍是一行字。
 *
 *  应用名用 `NameChip`（`ui/ChipLabel.kt`，与组合键成员框是同一个组件，用户口径：改成一样的
 *  标签样式、复用它），名字之间仍是那个 `×`；种类名留在前面，读起来是
 *  `前台应用 - [微信]×[QQ]`。
 *
 *  **为什么走 `titleContent` 而不是拼字符串**：框要能各自省略、各自贴边 ——
 *  拼成一整串时省略号只会落在最后，而且框底色根本画不出来。
 *
 *  @param enabled 整页那道闸（关着时框与字一起压灰 —— 与组合标题同一档判据）
 */
@Composable
internal fun ConditionRowTitle(
    line: ConditionLine,
    enabled: Boolean,
) {
    val scheme = COUITheme.colorScheme
    val color = if (enabled) scheme.onSurfaceContainer else scheme.disabledOnSecondaryVariant
    val apps = line.apps
    if (apps.isNullOrEmpty()) {
        Text(
            text = line.text,
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val inner = chipStyle()
    //  种类名作为 [ChipFlow] 的**第一项**参与换行：应用多的时候框排到下一行，
    //  而「前台应用 - 」留在原处 —— 拆成一个独立的 `Text` + 一个 `Row` 的话，框只能挤在同一行里。
    ChipFlow(
        names = apps,
        look = ChipLook(inner, color, enabled),
        modifier = Modifier.fillMaxWidth(),
        leading = {
            Text(
                //  种类名**按这一条自己的档念**：应用那一族两档都走这里，
                //  写死 `APP` 的话「前台应用以外」那一条会念成「前台应用」——
                //  读起来正好是相反的意思。
                text = "${conditionKindLabel(line.kind ?: ConditionKind.APP)} - ",
                style = COUITheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
            )
        },
    )
}
