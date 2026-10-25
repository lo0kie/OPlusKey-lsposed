package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.TimeRange
import com.lookie.opluskey.ui.DialogButtonRow
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialogSideMargin
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TimePicker
import io.github.suqi8.coui.kmp.basic.TimeValue
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「时间区间」那个选择窗 —— 条件菜单里点【时间区间】弹它。
 *
 * ## 为什么是滚轮而不是输入框
 *
 *  两个 [TimePicker] 是库自己的那两个滚轮（`coui` 的 `TimePicker`），不是手写的输入：
 *  时间这种东西打字的出错面（少补零、全角冒号、24:00）远大于拨两下，
 *  而且滚轮本来就只有合法值 —— 不需要再写一遍校验。
 *
 * ## 两个轮子并排，不上下叠
 *
 *  一个 `TimePicker` 在库里的固定高度是 5 × 45dp ≈ 225dp，上下叠两个再加标题与按钮就顶出
 *  弹窗的最大高度了。并排之后高度只占一份，左右各一列，读作「从 … 到 …」。
 *
 * ## 初值
 *
 *  · 这一条**已经有区间** → 用它（改时间时看到的是当前值，不是又跳回默认）；
 *  · 还没有（刚点「时间区间」）→ [DEFAULT_TIME_RANGE]：`22:00-07:00`。
 *    选这个是因为它跨午夜、又是最常写的那种「夜里」；而且一打开就能看见「这是跨午夜的区间」，
 *    比给 `00:00-00:00` 那种看不出语义的值更省一次思考。
 *
 * @param initial 线格式（`22:00-07:00`）；空串 = 还没有区间
 * @param onConfirm 确定时回调，参数是新区间的线格式（见 [TimeRange.format]）
 */
@Composable
internal fun SettingsPage.BranchTimeRangeDialog(
    show: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // 每次打开都以「当前的区间」为起点：改到一半关掉，再打开不该还挂着那半截。
    val seed = remember(show, initial) { TimeRange.parse(initial) ?: DEFAULT_TIME_RANGE }
    var startHour by remember(show, initial) { mutableIntStateOf(seed.startMinute / 60) }
    var startMinute by remember(show, initial) { mutableIntStateOf(seed.startMinute % 60) }
    var endHour by remember(show, initial) { mutableIntStateOf(seed.endMinute / 60) }
    var endMinute by remember(show, initial) { mutableIntStateOf(seed.endMinute % 60) }

    FrostedDialog(
        show = show,
        title = I18n.get(Strings.COMMAND_COND_TIME_NAME, "时间区间"),
        onDismiss = onDismiss,
        // 没有输入框，弹窗照常把键盘收掉
        hideImeOnShow = true,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().dialogSideMargin(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeWheel(
                    spec =
                        TimeWheelSpec(
                            label = I18n.get(Strings.COMMAND_COND_TIME_START, "开始"),
                            hour = startHour,
                            minute = startMinute,
                            onHourChange = { startHour = it },
                            onMinuteChange = { startMinute = it },
                        ),
                    modifier = Modifier.weight(1f),
                )
                TimeWheel(
                    spec =
                        TimeWheelSpec(
                            label = I18n.get(Strings.COMMAND_COND_TIME_END, "结束"),
                            hour = endHour,
                            minute = endMinute,
                            onHourChange = { endHour = it },
                            onMinuteChange = { endMinute = it },
                        ),
                    modifier = Modifier.weight(1f),
                )
            }
            DialogButtonRow(
                negative = I18n.get(Strings.DIALOG_CANCEL, "取消"),
                positive = I18n.get(Strings.DIALOG_CONFIRM, "确定"),
                onNegative = onDismiss,
                onPositive = {
                    onConfirm(
                        TimeRange.format(
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * 一列时间要说的全部东西。
 *
 *  打成一个包而不是六个形参：detekt 的 `LongParameterList` 阈值是 6，而这两列本来就同属
 *  「这一列画什么、转了之后告诉谁」。
 */
private class TimeWheelSpec(
    val label: String,
    val hour: Int,
    val minute: Int,
    val onHourChange: (Int) -> Unit,
    val onMinuteChange: (Int) -> Unit,
)

/**
 * 一列时间 —— 小标题 + 库的 `TimePicker`。
 *
 *  24 小时制：这一页的读者是「配一条规则」，`AM/PM` 那第三个轮子会让两列并排更挤，
 *  而 `00-23` 本来就没有歧义。
 */
@Composable
private fun TimeWheel(
    spec: TimeWheelSpec,
    modifier: Modifier = Modifier,
) {
    val hour = spec.hour
    val minute = spec.minute
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = spec.label,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        TimePicker(
            value = TimeValue(hour, minute),
            onValueChange = { next ->
                //  两个回调分开给：轮子只报「整个时间变了」，而这里两列各自是独立的两个状态
                if (next.hour != hour) spec.onHourChange(next.hour)
                if (next.minute != minute) spec.onMinuteChange(next.minute)
            },
            is24Hour = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 还没配过区间时的起点 —— `22:00-07:00`。
 *
 *  「夜里」是最常见的用法，而这一段跨午夜：一打开就能看出这一档支持跨过 0 点，
 *  比给一个 `00:00-00:00`（全天）更不容易让人以为「时间区间只能写在同一天里」。
 */
private val DEFAULT_TIME_RANGE = TimeRange(22 * 60, 7 * 60)
