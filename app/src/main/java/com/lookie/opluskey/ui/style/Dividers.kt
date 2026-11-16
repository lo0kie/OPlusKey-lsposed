package com.lookie.opluskey.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.chrome.HEADER_BORDER_ALPHA
import com.lookie.opluskey.ui.model.theme.MENU_GROUP_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.theme.MENU_GROUP_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.theme.isDarkScheme
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.theme.COUITheme

// 全项目**所有横向分隔**只有这两支：细线 [AppDivider] 与分组带 [AppHeavyDivider]。
//
//  这两件事以前散在十一处（卡片行、浮层菜单、选择器、对话框、导入目标、命令格式、捐赠名单、
//  首页激活卡）各写一遍 `HorizontalDivider`：颜色 / 粗细一旦要统一，就得一处一处找 ——
//  漏掉一处的结果是「这一块的线还是旧颜色」，而界面上很难一眼看出是哪一块没改。
//  所以收成一支：要调色、调粗细只改这里。

/**
 * 细线分隔 —— 卡片行之间、列表行之间、对话框里那一条。
 *
 *  **颜色与顶栏圆键的描边是同一个表达式**（`dividerLine` × [HEADER_BORDER_ALPHA]）—— 两处读起来
 *  必须是一条线，所以连那个系数也一起取，只留这一个出处可调。
 *
 *  色角色本身是跟主题走的：Monet 档下 `dividerLine` 就是 MD3 的 `outlineVariant`（跟壁纸走），
 *  COUI 两档是标定过的那一条。以前这里取的是圆键的**底色** `surfaceContainer`：Monet 浅色下它和
 *  卡片底（`surfaceContainerLow`）只差两档 tone，压成细线几乎读不出来。
 *
 *  **看着还是不一样是正常的**：圆键的描边压在磨砂顶栏上（半透的页面底色 + 模糊），这条压在卡片
 *  的实色底上 —— 同一个色号合在不同的底上，观感会差一档。要两边都更实，就一起抬
 *  [HEADER_BORDER_ALPHA]（那一个数管着两处）。
 *
 *  **粗细定 1dp**（库默认那档是 0.33dp）：0.33 在 3x 屏上只有一个物理像素，配上这条本就压得很淡
 *  的线，深色下几乎读不出「这里断开了一行」。
 *
 * @param modifier 只放内缩（左右起点）；颜色与粗细不在调用点覆盖 —— 那是这里的事
 */
@Composable
internal fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = appDividerColor(),
    )
}

/**
 * 细线那一档**颜色**本身 —— [AppDivider] 用它，需要「同一条线」但不是横向分隔的地方也用它
 * （捐赠名单那张表：外框与竖线；手写 `border` / `VerticalDivider` 时颜色从这里取，
 * 别各自再去读 `dividerLine`，否则又会长出第二份系数）。
 */
@Composable
internal fun appDividerColor(): Color = COUITheme.colorScheme.dividerLine.let { it.copy(alpha = it.alpha * HEADER_BORDER_ALPHA) }

/**
 * 「加深带」—— 一张卡里**两组不同类的东西**之间那条槽（例如「添加条件」与「上移 / 下移 / 删除」、
 * 导入时「盖掉哪一份」与「另起一份」）。
 *
 *  与 [AppDivider] 不是同一个东西：细线说的是「这里换了一行」，这条说的是「这里换了一类」，
 *  所以它更深、更粗（库的组间那一档），**不跟细线一起统一**。两者都由这一个文件管。
 */
@Composable
internal fun AppHeavyDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = DropdownDefaults.PopupGroupDividerThickness,
        //  暗色下加深（`#333` 面板上压到 `#1f` 才看得出是一道槽），浅色下仍是那点灰
        color =
            if (COUITheme.colorScheme.isDarkScheme) {
                Color.Black.copy(alpha = MENU_GROUP_LINE_DARK_ALPHA)
            } else {
                Color.Black.copy(alpha = MENU_GROUP_LINE_LIGHT_ALPHA)
            },
    )
}
