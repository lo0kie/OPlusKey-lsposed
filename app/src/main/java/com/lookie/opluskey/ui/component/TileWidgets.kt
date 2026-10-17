package com.lookie.opluskey.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
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
 * 行尾图标按钮的**触达区边长**（正方形，圆角给一半 ⇒ 圆形高亮圈）。
 *
 *  这两个尺寸现在是给 `IconButton` 的 `minWidth/minHeight` 用的 ——
 * 库的按压高亮是画在 `defaultMinSize` 那一层上的 squircle，**尺寸不由内容决定**，
 * 所以「高亮圈多大」这件事只能靠这两个数说（默认 48dp/24dp 见下）。
 */
internal val ICON_ACTION_SIZE = 32.dp

/** 行尾图标按钮里的图标边长。 */
internal val ICON_ACTION_ICON = 20.dp

/**
 * 行尾的一颗**图标按钮**（32dp 圆形点击区 + 20dp 图标）—— 步骤的
 * 上移 / 下移 / 删除都用它。
 *
 * `Box(size) + clip(CircleShape) + clickable` 拼的圆，现在换成库的
 * [`IconButton`]（`basic/IconButton.kt`：`Box` + `defaultMinSize` + squircle 高亮 + `clickable`）。
 * 「高亮圈超出触达区一大圈」的那一档；给 [ICON_ACTION_SIZE] 与它的一半，
 * 高亮就只落在 32dp 圆内，与换之前手拼的那颗**逐像素一致**。
 *
 * @param enabled false 时上灰、且**不吃点击**（首行的「上移」、末行的「下移」）
 * @param error true 时用 `error` 色 —— 有破坏性的操作按 COUI 的 `couiColorError` 上色
 * @param tint 非 null 时**覆盖**上面那两档配色 —— 给「这颗是主题色的主操作」用
 *   （已保存命令卡片右上角那颗「测试命令」）。`enabled = false` 仍然优先，照样上灰。
 *
 *  注：**不给 `modifier` 参数** —— `IconAction` 是「尺寸自己定死的行尾按钮」（触达区由
 *  [ICON_ACTION_SIZE] 说了算），调用方要的是位置而不是尺寸；加了它只会把参数个数顶到 detekt
 *  的 `LongParameterList` 之上，而本仓不许往基线里添条目。要挪位置在调用点用 `Box` 包一层。
 */
@Composable
internal fun IconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    error: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    //  颜色随 `enabled` / `error` / `tint` 切换，带过渡：置灰与恢复不再「啪」地跳
    // （`tint` 那一档是调用点传进来的常量色，动画从当前值走到它，同样自然）。
    val color by animateColorAsState(
        targetValue =
            when {
                !enabled -> scheme.disabledOnSecondaryVariant
                tint != null -> tint
                error -> scheme.error
                else -> scheme.onSurfaceContainer
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "IconAction",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = ICON_ACTION_SIZE,
        minHeight = ICON_ACTION_SIZE,
        cornerRadius = ICON_ACTION_SIZE / 2,
    ) {
        Icon(icon, contentDescription = description, Modifier.size(ICON_ACTION_ICON), tint = color)
    }
}

/**
 * 行首 / 行内图标「可用 / 禁用」两档配色 —— 全项目一处。
 *
 *  这一对色（`onSurfaceContainer` / `disabledOnSecondaryVariant`）在开关行、滑杆行、输入框行首
 *  各写了一遍，值还一样：同一页里几个行首图标一拨主开关就该一起变，分散写时改一处就漏一处。
 *
 *  ⚠️ **不是所有图标都走这一对**：行尾那几颗（`onSurfaceVariantActions` 起头）、
 *  选中态那几颗（`onTertiaryContainer`）、以及带 `error` / `tint` 覆盖的那几档各有自己的调色，
 *  它们**不**并到这里来 —— 只有「行首图标 + 只有可用/禁用两态」才是这一对。
 *
 *  @param label 动画标签，便于在 Studio 的动画检查器里分辨是哪一个调用点
 */
@Composable
internal fun rememberRowIconColor(
    enabled: Boolean,
    label: String = "RowIcon",
): State<Color> =
    animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onSurfaceContainer
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
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
