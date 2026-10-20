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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.SLIDER_ICON_INSET
import com.lookie.opluskey.ui.model.SLIDER_INSIDE_MARGIN
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
    position: CardListPosition,
    spec: SliderSpec,
    onCommit: (Int) -> Boolean,
) {
    // 以 config 里的值为 key：写盘成功重组后回到真实值；拖动过程中 config 不变、不受干扰。
    //  注意：只靠这一个 key 不够：**写盘失败时 config 不变** ⇒ key 不变 ⇒ 拖出来的那个值留在滑杆上。
    //  所以下面 commit 失败还要手动拨回 [SliderSpec.value]，那条路不走重组。
    val dragging = remember(spec.value) { mutableFloatStateOf(spec.value.toFloat()) }
    // 提交结果的统一出口：成功不动（等重组把 dragging 归位到新值），失败拨回当前真实值。
    val commit: (Int) -> Unit = { raw ->
        val v = raw.coerceIn(spec.min, spec.max)
        // 判据级：滑杆是「拖了没反应 / 值自己跳」这类问题的唯一锚点 ——
        //  它一次交互只有一条（抬手或点轨道各一条），不会刷屏。
        XLog.w("滑杆提交：${spec.title} → $v")
        dragging.floatValue = v.toFloat()
        if (!onCommit(v)) {
            dragging.floatValue = spec.value.toFloat()
            XLog.w("滑杆值未提交成功，界面拨回 ${spec.value}（滑杆：${spec.title}）")
        }
    }
    val tick = rememberTick()
    //  注意：「点轨道跳值」那条手势的回调必须走 `rememberUpdatedState`：
    //  `tapSliderToSeek` 的 `pointerInput` 键里只有 `min/max/enabled`，闭包**停留在首次组合**
    //  那一刻 —— 直接捕获 `commit` 就等于一直用旧的那份（写盘失败时「拨回」拨到的是旧的 `value`，
    //  因为 `commit` 是个每次重组都新建的 val）。这里让闭包捕获一个**稳定的 State 对象**、
    //  调用时才读值，键不必跟着回调变、也不会在手势中途重启检测。
    val seekLatest by rememberUpdatedState<(Int) -> Unit> { v ->
        // 与 `commit` 那条分开记：点轨道与拖动是两条不同的来路，
        //  出问题时靠这两条就能分出「手势没进来」还是「进来了但没提交」。
        XLog.w("滑杆点轨道跳值：${spec.title} → $v")
        tick()
        commit(v)
    }
    // 拖动触觉与「点轨道跳值」共用同一条通道（`rememberTick`）：判据只有系统静音，
    //  与模块的「振动反馈」开关无关（完整口径见 `tickNow`）。
    val sliderIconColor by rememberRowIconColor(spec.enabled, label = "SliderIcon")
    // 文字三档色，与库的 `BasicComponentDefaults.titleColor()` / `summaryColor()`、
    // 以及 `SliderPreference` 里给当前值用的那一档**同源**（它们那个 `color(enabled)`
    // 是库的内部成员，取不到，所以自己按 `enabled` 选）。
    //  三处都**带过渡**（与上面 [sliderIconColor] 同一组参数）：主开关一拨，
    //  行首图标与这三行字一起渐变，不会出现「图标在淡、字在跳」。
    val colors = sliderTextColors(spec.enabled)

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
        enabled = spec.enabled,
        //  行首图标要自己管颜色：库只给**它自己画的东西**按 `enabled` 上色，
        // `startAction` 槽位原样渲染 —— 不给的话 `enabled = false` 会「字灰了、图标还亮着」
        // （与 [ArrowPreference] 那一行同一个坑）。
        startAction = {
            Icon(
                spec.icon,
                contentDescription = null,
                tint = sliderIconColor,
            )
        },
        // 行尾当前值。文案走 `spec.valueText`（毫秒那几档是 `durationText`：1000ms 以上写成秒）；
        //  给 `null` 的一档（振动强度那种）不画 —— 档位由行首的数字图标表达，再写一遍是重复。
        endActions = {
            spec.valueText?.let { format ->
                Text(
                    text = format(dragging.floatValue.roundToInt()),
                    modifier = Modifier.padding(end = 4.dp),
                    fontSize = COUITheme.textStyles.body2.fontSize,
                    color = colors.value,
                )
            }
        },
        bottomAction = {
            SliderTrack(
                spec = spec,
                value = dragging.floatValue,
                onValueChange = { raw ->
                    // 跨过一档振一次。判据用取整后的值：滑杆是离散刻度，`raw` 已是吸附后的值，
                    //  与上一帧显示的那个值一比就知道有没有跨档（不用另存一份状态）。
                    if (raw.roundToInt() != dragging.floatValue.roundToInt()) tick()
                    dragging.floatValue = raw
                },
                onValueChangeFinished = { commit(dragging.floatValue.roundToInt()) },
            )
        },
        // 点轨道跳值那条路不经过滑杆自己，得自己给一次触觉（拖动那条由 Slider 的 hapticEffect 给）
        //  注意：传**稳定的那份回调**（见上面 `seekLatest` 的注释）：`tick()` / `commit()` 每次重组
        //  都是新实例，直接捕获就停在首次组合那一刻了。
        modifier =
            Modifier.tapSliderToSeek(spec) { v ->
                seekLatest(v)
            },
    ) {
        SliderRowTexts(spec = spec, colors = colors)
    }
}

/** 标题 + 摘要那两行 —— 从 [SettingsPage.SliderRow] 里分出来，让那一支只留接线。 */
@Composable
private fun SliderRowTexts(
    spec: SliderSpec,
    colors: SliderTextColors,
) {
    // 标题与摘要之间那 2dp 是库的私有常量 `MarginBetweenLine`（值取自 `Component.kt`）。
    Text(
        text = spec.title,
        style = COUITheme.textStyles.headline1,
        fontWeight = FontWeight.Medium,
        color = colors.title,
    )
    Text(
        text = spec.summary,
        modifier = Modifier.padding(top = 2.dp),
        style = COUITheme.textStyles.body2,
        color = colors.summary,
    )
}

/** 滑杆轨道 —— 从 [SliderRow] 里分出来，让那一支只留「整行怎么摆」。 */
@Composable
private fun SliderTrack(
    spec: SliderSpec,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    //  注意：这里要的是**配色方案**，不是 [SliderRow] 里那个 `colors`（那是行内三处文字的颜色）。
    val scheme = COUITheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                // 行首图标把滑杆也挤开（见 [SLIDER_ICON_INSET]）
                .padding(start = SLIDER_ICON_INSET),
        enabled = spec.enabled,
        //  刻度点（`keyPoints`）的颜色库**不看 `enabled`**：`keyPointColor` /
        //  `keyPointForegroundColor` 各只有一对值，绘制处只按「这一点是不是当前值」二选一
        //  （见库 `Slider.kt` 里的 `kpColor`）—— 于是滑杆置灰时轨道灰了、那几个点还是主题色，
        //  看着像还能点。
        //  这里自己换一套：置灰时两档都取 `disabledOnSurface`（库给禁用态内容的那一档灰，
        //  与行内那三处禁用文字用的 `disabledOnSecondaryVariant` 恰好同值），
        //  压在 `disabledPrimarySlider` / `disabledSecondary` 这对浅灰轨道上清楚、又不抢眼，
        //  整行也只有一个灰调；与轨道一起退成灰。
        //  其余几项不传，走 `sliderColors` 的默认值（就是库本来用的那一套），不接管它们。
        colors =
            SliderDefaults.sliderColors(
                keyPointColor = if (spec.enabled) scheme.sliderKeyPoint else scheme.disabledOnSurface,
                keyPointForegroundColor =
                    if (spec.enabled) scheme.sliderKeyPointForeground else scheme.disabledOnSurface,
            ),
        valueRange = spec.min.toFloat()..spec.max.toFloat(),
        // 离散刻度：两端之间分 (max-min)/step 档（`steps` 不含两个端点）
        steps = ((spec.max - spec.min) / spec.step - 1).coerceAtLeast(0),
        // 出厂默认值在轨道上点一个圆点（库的 `keyPoints` + `showKeyPoints`）。
        //  只标**不吸附**：`steps > 0` 时库的磁性吸附分支本来就走不到（它只在 `steps == 0`
        //  且 keyPoints 非空时吸附），这里再把 `magnetThreshold` 显式写 0f 兜一道 ——
        //  免得将来哪一档的 steps 变成 0，拖动手感被这个点悄悄改掉。
        showKeyPoints = true,
        // 不给调用方那份时只标出厂默认值一个点（毫秒那几档要的就是这个）
        keyPoints = spec.keyPoints ?: listOf(spec.default.toFloat()),
        magnetThreshold = 0f,
        // 与库默认同值，显式写出来是因为下面「点轨道跳值」要按它们算几何 ——
        // 账在 [SLIDER_TAP_ZONE] / [SLIDER_SIDE_INSET] 的注释里
        height = SliderDefaults.MinHeight,
        // 库那一档关掉：`SliderHapticState.handleStepHaptic` 是**二选一** —— `keyPoints` 非空
        //  就整条切到「只在关键点上振一次」，为空才按「跨一档振一次」。默认的圆点只有一个，
        //  开着它就等于拖动全程只在拖回默认值时振。圆点要留，于是触觉由本行自己给
        //  （见 `onValueChange` 里那次 tick，「点轨道跳值」本来也是这么给的）。
        hapticEffect = SliderDefaults.SliderHapticEffect.None,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/** 滑杆那一行三处文字的颜色 —— 一份，三处同源（理由见 [SliderRow] 里那段注释）。 */
private class SliderTextColors(
    val title: Color,
    val summary: Color,
    val value: Color,
)

@Composable
private fun sliderTextColors(enabled: Boolean): SliderTextColors {
    val title by animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onBackground
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "SliderTitle",
    )
    val summary by animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onSurfaceVariantSummary
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "SliderSummary",
    )
    val value by animateColorAsState(
        targetValue =
            if (enabled) {
                COUITheme.colorScheme.onSurfaceSecondary
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "SliderValue",
    )
    return SliderTextColors(title = title, summary = summary, value = value)
}
