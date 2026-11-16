package com.lookie.opluskey.ui.row

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 行尾那枚小字「已添加」—— 这一格说的是**这一行现在的状态**，不是「点一下会怎样」。
 *
 *  给「选择器里这个动作已经在目标清单里了」那一档：那一行同时灰着（`enabled = false`），
 *  这枚小字把原因摆在明面上，点它再由那一行的 `disabledHint` 说一遍 —— 只靠灰字，用户读到的
 *  是「坏了」；只靠点一下才说的提示，不点的人永远不知道。
 *
 *  用最弱那档小字（`footnote1` + `onSurfaceVariantSummary`）：状态说明不该抢行尾箭头那份注意力。
 *  **行尾只有这一格**（放了它就收起箭头）：同一格既说状态又说「点开还有下一层」会自相矛盾。
 *
 *  选择器的两个来处共用它（常用命令的抽屉 / 内置那几条的勾选窗）—— 同一句话在两处必须长得一样。
 */
@Composable
internal fun AddedTag() {
    Text(
        text = I18n.get(Strings.COMMON_COMMANDS_ADDED),
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
    )
}
