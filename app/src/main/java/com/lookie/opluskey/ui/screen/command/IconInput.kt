package com.lookie.opluskey.ui.screen.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.ROW_ICON_SIZE
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.InputViewDefaults
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text

// 卡片里那种「行首图标 + 输入」的行 —— 底子是库的搜索输入框（`InputField`）。

/** 错误提示淡入 / 淡出的时长（毫秒）与缓动 —— 与库的 `InputView` 同一组值（其文档给出）。 */
private const val ERROR_FADE_IN_MS = 217

private const val ERROR_FADE_OUT_MS = 283

private val INPUT_ERROR_EASE = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * 一条输入项的**静态那几样**：行首图标、占位文案、长度上限。
 *
 *  打成一个参数只为把 [IconInputView] 的参数个数留在 detekt 之上限以内（那三样天然是一组：
 *  它们说的是「这一栏是什么」，与「这一栏现在是什么值」无关）。
 *
 * @param maxLength 能输入多少字 —— 超出的部分在组件里直接截掉（**不显示计数**，用户口径）
 */
internal class InputSpec(
    val icon: ImageVector,
    val label: String,
    val maxLength: Int,
)

/**
 * **带行首图标的输入项** —— 底子换成库的 `InputField`（搜索框那一副长相）。
 *
 * ## 为什么不再自己拼卡片
 *
 *  先前这一层是「`Card` + `title` + `TextField`」手拼的：`InputView` 的 `leadingIcon` 是直接
 *  透传给内部 `TextField` 的，卡片里那行 `title` 不跟着让位，只好自己拼一份同款。现在这里
 *  不再有独立的标题行 —— 字段名走 [label]（占位文案），于是 `InputField` 一个组件就够了，
 *  与「选择应用 / 选择界面」那两页的搜索框也是同一副长相。
 *
 * ## 行首图标是**原来那颗**
 *
 *  `InputField` 不给 `leadingIcon` 时画的是放大镜 —— 这里是「命令 / 名称」这些具体字段，
 *  所以把原来卡片行首那颗图标原样递进去（[ROW_ICON_SIZE] + 与其他行首同一档的颜色）。
 *
 * ## 只有单行
 *
 *  多行（textarea）那一档**已撤**：命令正文也是一个输入框，换行不再由这里保留。
 *
 * @param icon 行首图标（按 [ROW_ICON_SIZE] 画，与全页图标同一个尺寸纪律）
 * @param label 占位文案（`请输入名称` 这种）—— 现在也是这一栏唯一的文字说明
 */
@Composable
internal fun SettingsPage.IconInputView(
    spec: InputSpec,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String = "",
) {
    val isError = errorMessage.isNotEmpty()
    // 淡出期间要**留着最后那条非空消息**，否则红字先消失、再走淡出（库同此做法）
    var shownError by remember { mutableStateOf(errorMessage) }
    // 组合期**不写状态**（写它会让本次组合再跑一轮）：挪到副作用里做同一件事。
    LaunchedEffect(isError, errorMessage) {
        if (isError && shownError != errorMessage) shownError = errorMessage
    }

    Column(modifier = modifier) {
        InputField(
            query = value,
            // **长度上限在这一层兜**（不靠各调用点自己截）：超出的部分直接吃掉，
            //  于是右下的计数永远与真正存下去的值一致。
            onQueryChange = { raw -> onValueChange(raw.take(spec.maxLength)) },
            // 键盘上的「搜索」键不做额外动作：这一栏不是搜索，输入即生效
            onSearch = { },
            // 展开态那套是「点搜索框撑开一页结果」用的，这里不展开
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier.fillMaxWidth(),
            label = spec.label,
            leadingIcon = { InputLeadingIcon(spec.icon) },
        )
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(tween(ERROR_FADE_IN_MS, easing = INPUT_ERROR_EASE)),
            exit = fadeOut(tween(ERROR_FADE_OUT_MS, easing = INPUT_ERROR_EASE)),
        ) {
            Text(
                text = shownError,
                fontSize = InputViewDefaults.ErrorFontSize,
                color = InputViewDefaults.inputViewColors().errorColor,
                modifier =
                    Modifier.padding(
                        top = InputViewDefaults.ErrorPaddingTop,
                        start = InputViewDefaults.ContentPadding,
                        end = InputViewDefaults.ContentPadding,
                    ),
            )
        }
    }
}

/**
 * 输入框行首那一格图标。
 *
 * ## 内外边距必须自己补
 *
 *  库里自带那颗放大镜是这么画的（`SearchBar.kt` 的默认 `leadingIcon`）：
 *  `Icon(Search, Modifier.padding(start = LeadingIconStartPadding, end = LeadingIconEndPadding))`。
 *  换成自己的图标时**这两档不能省** —— 省了图标就贴着输入框左边缘、跟文字挤在一起
 *  （真机上就是「很诡异」那种观感）。库那两个常量是 public 的，直接用。
 *
 *  尺寸仍取 [ROW_ICON_SIZE]（22dp）：比其他行首图标是同一档，比库自带的 24dp 小一点点，
 *  肉眼看不出来，但全页口径一致。
 */
@Composable
internal fun InputLeadingIcon(icon: ImageVector) {
    // 颜色**取库自己的那一档**（`SearchBarColors.IconColor`）：页面里其他行首用的
    //  `onSurfaceContainer` 在输入框这块底色上偏亮（用户口径「太亮」），与旁边的占位文字
    //  一比就更突兀。用同一个 `searchBarColors()`，将来库调色我们跟着变。
    val colors = SearchBarDefaults.searchBarColors()
    Icon(
        icon,
        contentDescription = null,
        modifier =
            Modifier
                .padding(
                    start = SearchBarDefaults.LeadingIconStartPadding,
                    end = SearchBarDefaults.LeadingIconEndPadding,
                ).size(ROW_ICON_SIZE),
        tint = colors.iconColor,
    )
}
