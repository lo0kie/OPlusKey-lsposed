package com.lookie.opluskey.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.SettingsPage
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputViewDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldDefaults
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 卡片式输入项（行首图标 + 标题 + 输入框 + 错误行）与它的尺寸 / 缓动常量。 */

/** 「带图标的卡片输入」（[IconInputView]）里行首图标的边长 —— 与全页图标同一个 22dp 纪律。 */
internal val INPUT_ICON_SIZE = 22.dp

/** 行首图标与右侧内容之间的间距。 */
internal val INPUT_ICON_GAP = 8.dp

/** 错误提示淡入 / 淡出的时长（毫秒）与缓动 —— 与库的 `InputView` 同一组值（其文档给出）。 */
private const val ERROR_FADE_IN_MS = 217

private const val ERROR_FADE_OUT_MS = 283

private val INPUT_ERROR_EASE = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * **带行首图标的卡片输入** —— 库的 `InputView` 做不到这件事，所以在这里拼一个同款的。
 *
 * `InputView` 的 `leadingIcon` 是**直接透传给内部 `TextField`** 的（见库的 `InputView.kt`）：
 * 它只把输入框里的文字挤开，而卡片里那行 `title` 是库在内容左边界自己画的，**不跟着让位**；
 * `insideMargin` 之类的参数又会让两者**一起**平移，分不开。
 *
 * 于是这里用库的公开件自己拼，排版逐项照抄 `InputView`：
 *  · 卡片：[Card]（12dp 圆角 + `surfaceContainer`，与 `InputViewDefaults` 同值）；
 *  · 标题：`body2`（14sp，由库的 16sp `titleTextStyle()` 降一档）+ 上下 12 / 4dp + 最小高 22dp；
 *  · 输入框：[TextField]，`textStyle` 用库默认（16sp）+ `TextFieldMode.None` + `insideMargin = DpSize(0, 0)`；
 *  · 错误提示：卡片**外**那行 10sp 红字，淡入 217ms / 淡出 283ms（库文档给的那组数值）。
 * 差别只有一处：**行首图标占左侧一格，标题与输入框都从它右边起**（图标 [INPUT_ICON_SIZE]
 * + 间距 [INPUT_ICON_GAP]）。
 *
 * @param icon 行首图标（按 [INPUT_ICON_SIZE] 画，与全页图标同一个尺寸纪律）
 */
@Composable
internal fun SettingsPage.IconInputView(
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String = "",
    enabled: Boolean = true,
) {
    val colors = InputViewDefaults.inputViewColors()
    // 注意：标题**不能**用 `colors.titleColor`：它是 `onSurface`，而输入内容的颜色走
    // `TextField` 的 `LocalContentColor`（本组件里就是 `Card` 的 `contentColor` =
    // `onSurfaceContainer`）—— **这两个色值完全相同**（`0xE6000000` / `0xE6FFFFFF`），
    // 撞在一起卡片里就分不出「字段名」和「输入内容」。
    //  于是标题分两档，按**有没有内容**切：
    //  · **空值时**：输入框里什么都没有，标题就替它站那一位 ⇒ 用其他行主标题那一档
    //    （`onBackground`，与 `BasicComponentDefaults.titleColor()` 同色）；
    //  · **有内容时**：退回 `onSurfaceSecondary`（与输入框自己的 label / 占位符同色）——
    //    层级变成「弱标题 + 强内容」，字段名不会跟内容抢。
    //  同样带过渡：打字 / 清空、主开关置灰 / 恢复时都不跳变（与 [IconAction] / `SliderRow`
    // 同一组参数）。
    val titleColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledTitleColor
            value.isEmpty() -> COUITheme.colorScheme.onBackground
            else -> COUITheme.colorScheme.onSurfaceSecondary
        },
        label = "InputTitle",
    )
    //  行首图标**自己一档**：与其他行的行首图标同色（`onSurfaceContainer`）。
    // 不要跟着标题走 —— 那个色比页里别的行首都暗一档。
    val iconColor by animateColorAsState(
        targetValue = if (enabled) {
            COUITheme.colorScheme.onSurfaceContainer
        } else {
            COUITheme.colorScheme.disabledOnSecondaryVariant
        },
        label = "InputIcon",
    )
    val isError = errorMessage.isNotEmpty()
    // 淡出期间要**留着最后那条非空消息**，否则红字先消失、再走淡出（库同此做法）
    var shownError by remember { mutableStateOf(errorMessage) }
    if (isError && shownError != errorMessage) shownError = errorMessage

    Column(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = InputViewDefaults.CornerRadius,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = InputViewDefaults.ContentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(INPUT_ICON_SIZE),
                    tint = iconColor,
                )
                Spacer(Modifier.width(INPUT_ICON_GAP))
                // 标题 + 输入框：**这一列**整体让出图标那一格，所以两者不用各自再加左内缩
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        //  标题**降一档**：库的 `titleTextStyle()` 是 16sp Medium，
                        // 压过下面的输入内容 —— 输入框应该是「内容大、字段名小」。
                        // 降到 body2（14sp）。
                        style = COUITheme.textStyles.body2,
                        color = titleColor,
                        modifier = Modifier
                            .padding(
                                top = InputViewDefaults.TitlePaddingTop,
                                bottom = InputViewDefaults.TitlePaddingBottom,
                            )
                            .defaultMinSize(minHeight = InputViewDefaults.TitleMinHeight),
                    )
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = InputViewDefaults.FieldPaddingBottomWithTitle),
                        backgroundMode = TextFieldMode.None,
                        insideMargin = DpSize(0.dp, 0.dp),
                        label = label,
                        enabled = enabled,
                        isError = isError,
                        //  库默认的 16sp（试过升到 `paragraph` 17sp 又还原）：
                        // `TextField` 会拿 `textStyle` 的字号**顺带推算浮动 label 的大小**
                        //（源码：`labelNormalFontSize = resolvedTextStyle.fontSize`），
                        // value 一升，上面那行「请输入…」跟着变大，
                        // 标题与内容之间的空隙就被撑开了 —— 动这里必须连着 label 一起看。
                        textStyle = TextFieldDefaults.textStyle(TextFieldMode.None),
                        singleLine = true,
                        maxLines = 1,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(tween(ERROR_FADE_IN_MS, easing = INPUT_ERROR_EASE)),
            exit = fadeOut(tween(ERROR_FADE_OUT_MS, easing = INPUT_ERROR_EASE)),
        ) {
            Text(
                text = shownError,
                fontSize = InputViewDefaults.ErrorFontSize,
                color = colors.errorColor,
                modifier = Modifier.padding(
                    top = InputViewDefaults.ErrorPaddingTop,
                    start = InputViewDefaults.ContentPadding,
                    end = InputViewDefaults.ContentPadding,
                ),
            )
        }
    }
}
