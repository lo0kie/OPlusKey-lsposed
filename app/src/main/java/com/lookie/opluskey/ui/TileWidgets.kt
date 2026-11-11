package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.theme.COUITheme

// 瓦片类小件：格子尺寸常量、行首图标按钮、焦点圈。

/**
 * 一个格子的高度：`BasicComponent` 的最小高度 48dp + `CardListPosition.Full`
 * 上下各补的 2dp（COUI 的 `coui_list_card_head_or_tail_padding`）。
 * 一屏放得下十来行（这一页是 `verticalScroll`，真放不下也能滚）。
 */
internal val TILE_HEIGHT = 52.dp

internal val TILE_SIDE_PADDING = 12.dp

/**
 * 行尾那颗图标键的**基准边长** —— 一行里并排三颗时按可用宽度往下缩（见 `CommandSteps` 的
 * `stepButtonSize`），放得下就用这一档。
 *
 *  与图标 20dp 配成一对（0.625 的比例），缩的时候图标一起缩 —— 只缩格子会让高亮圈贴着字形。
 */
internal val ICON_ACTION_SIZE = 32.dp

/**
 * 行首 / 行内图标「可用 / 禁用」两档配色 —— 全项目一处。
 *
 *  这一对色（`onSurfaceContainer` / `disabledOnSecondaryVariant`）在开关行、滑杆行、输入框行首
 *  各写了一遍，值还一样：同一页里几个行首图标一拨主开关就该一起变，分散写时改一处就漏一处。
 *
 *  注意：**不是所有图标都走这一对**：行尾那几颗（`onSurfaceVariantActions` 起头）、
 *  选中态那几颗（`onTertiaryContainer`）、以及带 `error` / `tint` 覆盖的那几档各有自己的调色，
 *  它们**不**并到这里来 —— 只有「行首图标 + 只有可用/禁用两态」才是这一对。
 *
 *  @param label 动画标签，便于在 Studio 的动画检查器里分辨是哪一个调用点
 *  @param override 行自己指定的颜色（强调色色块那几行）—— 给了就以它为准，不再看后面几档
 *  @param selected 选中态（网格里选中那一格）—— 只在启用时生效，用主色把它与「只是能用」分开
 */
@Composable
internal fun rememberRowIconColor(
    enabled: Boolean,
    /**
     * 这一行的图标 —— 给了才参与「随机图标配色」（见 `LocalIconTint`）。
     *  `null` = 这一处没有具体图标（或不想让它参与），照旧走主题色。
     */
    icon: ImageVector? = null,
    override: Color? = null,
    selected: Boolean = false,
    label: String = "RowIcon",
): State<Color> =
    animateColorAsState(
        targetValue =
            override
                ?: when {
                    //  禁用优先于选中：灰着的一格不该因为「还记着选过它」而亮主色
                    !enabled -> {
                        COUITheme.colorScheme.disabledOnSecondaryVariant
                    }

                    selected -> {
                        COUITheme.colorScheme.primary
                    }

                    else -> {
                        icon?.takeIf { enabled }?.tinted()
                            ?: COUITheme.colorScheme.onSurfaceContainer
                    }
                },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = label,
    )

/**
 * 格子外面的**焦点圈**（键盘 / 遥控走焦点时那一圈高亮）。
 *
 * 圈的**亮起 / 熄灭带过渡**（`primary` / 透明）—— 硬切的话它会「啪」地跳出来，
 * 而卡片里的标题 / 类型图标这时正在**渐变**（见 `CommandScreen` 里那处），
 * 两者不同步就很难看。过渡参数接入统一的 [COLOR_TRANSITION_SPEC]。
 */
@Composable
internal fun TileFocusRing(focused: Boolean) {
    val ringColor by animateColorAsState(
        targetValue = if (focused) COUITheme.colorScheme.primary else Color.Transparent,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "TileFocusRing",
    )
    Box(
        Modifier
            .fillMaxSize()
            .squircleBorder(
                width = 2.dp,
                color = ringColor,
                cornerRadius = CardDefaults.CornerRadius,
            ),
    )
}
