package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.SLIDER_ICON_INSET
import com.lookie.opluskey.ui.model.SLIDER_INSIDE_MARGIN
import com.lookie.opluskey.ui.model.STEP_MS
import com.lookie.opluskey.ui.model.durationText
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

@Composable
internal fun SettingsPage.SliderRow(
    title: String,
    summary: String,
    icon: ImageVector,
    position: CardListPosition,
    min: Int,
    max: Int,
    value: Int,
    default: Int,
    enabled: Boolean = true,
    onCommit: (Int) -> Boolean,
) {
    // 以 config 里的值为 key：写盘成功重组后回到真实值；拖动过程中 config 不变、不受干扰。
    //  注意：只靠这一个 key 不够：**写盘失败时 config 不变** ⇒ key 不变 ⇒ 拖出来的那个值留在滑杆上。
    //  所以下面 commit 失败还要手动拨回 [value]，那条路不走重组。
    val dragging = remember(value) { mutableFloatStateOf(value.toFloat()) }
    // 提交结果的统一出口：成功不动（等重组把 dragging 归位到新值），失败拨回当前真实值。
    val commit: (Int) -> Unit = { raw ->
        val v = raw.coerceIn(min, max)
        dragging.floatValue = v.toFloat()
        if (!onCommit(v)) {
            dragging.floatValue = value.toFloat()
            XLog.w("滑杆值未提交成功，界面拨回 $value（滑杆：$title）")
        }
    }
    val tick = rememberTick()
    //  注意：「点轨道跳值」那条手势的回调必须走 `rememberUpdatedState`：
    //  `tapSliderToSeek` 的 `pointerInput` 键里只有 `min/max/enabled`，闭包**停留在首次组合**
    //  那一刻 —— 直接捕获 `commit` 就等于一直用旧的那份（写盘失败时「拨回」拨到的是旧的 `value`，
    //  因为 `commit` 是个每次重组都新建的 val）。这里让闭包捕获一个**稳定的 State 对象**、
    //  调用时才读值，键不必跟着回调变、也不会在手势中途重启检测。
    val seekLatest by rememberUpdatedState<(Int) -> Unit> { v ->
        tick()
        commit(v)
    }
    // 拖动触觉与「点轨道跳值」共用同一条通道（[rememberTick]）：判据只有系统静音，
    //  与模块的「振动反馈」开关无关（完整口径见 `tickNow`）。
    val sliderIconColor by rememberRowIconColor(enabled, label = "SliderIcon")

    // 文字三档色，与库的 `BasicComponentDefaults.titleColor()` / `summaryColor()`、
    // 以及 `SliderPreference` 里给当前值用的那一档**同源**（它们那个 `color(enabled)`
    // 是库的内部成员，取不到，所以自己按 `enabled` 选）。
    //  三处都**带过渡**（与上面 [sliderIconColor] 同一组参数）：主开关一拨，
    // 行首图标与这三行字一起渐变，不会出现「图标在淡、字在跳」。
    val titleColor by animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onBackground
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "SliderTitle",
    )
    val summaryColor by animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onSurfaceVariantSummary
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "SliderSummary",
    )
    val valueColor by animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onSurfaceSecondary
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "SliderValue",
    )

    // 注意：这里**不用** `SliderPreference`（改）：库那一版把滑杆摆在
    // `bottomAction` 里，而 `bottomAction` 在 `Layout { start | center | end }` **之外**、
    // 铺满整行宽度（见 `Component.kt`）—— 行首图标**挤不到它**，滑杆会从图标底下穿过去。
    // 改用库底下的 `BasicComponent`（自定义内容变体）+ 公开的 `Slider` 自己拼：
    // 滑杆走 [SLIDER_ICON_INSET] 左内缩，与标题文字同一个左边界。
    // 代价是标题 / 摘要 / 当前值这三处要自己画 —— 样式逐项照抄库那一版
    // （`BasicComponent` 的 headline1 + Medium、body2、2dp 行距；`SliderPreference` 的
    // `valueText` 走 body2 字号 + `onSurfaceSecondary` + 行尾 4dp）。
    BasicComponent(
        insideMargin = SLIDER_INSIDE_MARGIN,
        cardListPosition = position,
        enabled = enabled,
        //  行首图标要自己管颜色：库只给**它自己画的东西**按 `enabled` 上色，
        // `startAction` 槽位原样渲染 —— 不给的话 `enabled = false` 会「字灰了、图标还亮着」
        // （与 [ArrowPreference] 那一行同一个坑）。
        startAction = {
            Icon(
                icon,
                contentDescription = null,
                tint = sliderIconColor,
            )
        },
        // 行尾当前值。文案走 [durationText]（全局唯一一处）：1000ms 以上写成秒（`3 秒`），
        // 一秒以内仍是毫秒（`400 毫秒`）。
        endActions = {
            Text(
                text = durationText(dragging.floatValue.roundToInt()),
                modifier = Modifier.padding(end = 4.dp),
                fontSize = COUITheme.textStyles.body2.fontSize,
                color = valueColor,
            )
        },
        bottomAction = {
            Slider(
                value = dragging.floatValue,
                onValueChange = { raw ->
                    // 跨过一档振一次。判据用取整后的值：滑杆是离散刻度，`raw` 已是吸附后的值，
                    //  与上一帧显示的那个值一比就知道有没有跨档（不用另存一份状态）。
                    if (raw.roundToInt() != dragging.floatValue.roundToInt()) tick()
                    dragging.floatValue = raw
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // 行首图标把滑杆也挤开（见 [SLIDER_ICON_INSET]）
                        .padding(start = SLIDER_ICON_INSET),
                enabled = enabled,
                valueRange = min.toFloat()..max.toFloat(),
                // 离散刻度：两端之间分 (max-min)/STEP_MS 档（`steps` 不含两个端点）
                steps = ((max - min) / STEP_MS - 1).coerceAtLeast(0),
                // 出厂默认值在轨道上点一个圆点（库的 `keyPoints` + `showKeyPoints`）。
                //  只标**不吸附**：`steps > 0` 时库的磁性吸附分支本来就走不到（它只在 `steps == 0`
                //  且 keyPoints 非空时吸附），这里再把 `magnetThreshold` 显式写 0f 兜一道 ——
                //  免得将来哪一档的 steps 变成 0，拖动手感被这个点悄悄改掉。
                showKeyPoints = true,
                keyPoints = listOf(default.toFloat()),
                magnetThreshold = 0f,
                // 与库默认同值，显式写出来是因为下面「点轨道跳值」要按它们算几何 ——
                // 账在 [SLIDER_TAP_ZONE] / [SLIDER_SIDE_INSET] 的注释里
                height = SliderDefaults.MinHeight,
                // 库那一档关掉：`SliderHapticState.handleStepHaptic` 是**二选一** —— `keyPoints` 非空
                //  就整条切到「只在关键点上振一次」，为空才按「跨一档振一次」。上面的圆点只有出厂默认值
                //  这一个，开着它就等于拖动全程只在拖回默认值时振。圆点要留，于是触觉由本行自己给
                //  （见 `onValueChange` 里那次 [tick]，「点轨道跳值」本来也是这么给的）。
                hapticEffect = SliderDefaults.SliderHapticEffect.None,
                onValueChangeFinished = {
                    commit(dragging.floatValue.roundToInt())
                },
            )
        },
        // 点轨道跳值那条路不经过滑杆自己，得自己给一次触觉（拖动那条由 Slider 的 hapticEffect 给）
        //  注意：传**稳定的那份回调**（见上面 `seekLatest` 的注释）：`tick()` / `commit()` 每次重组
        //  都是新实例，直接捕获就停在首次组合那一刻了。
        modifier =
            Modifier.tapSliderToSeek(min, max, enabled) { v ->
                seekLatest(v)
            },
    ) {
        // 标题与摘要之间那 2dp 是库的私有常量 `MarginBetweenLine`（值取自 `Component.kt`）。
        Text(
            text = title,
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = titleColor,
        )
        Text(
            text = summary,
            modifier = Modifier.padding(top = 2.dp),
            style = COUITheme.textStyles.body2,
            color = summaryColor,
        )
    }
}
