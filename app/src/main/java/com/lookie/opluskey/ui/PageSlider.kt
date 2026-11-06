package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.ROW_START_ICON_GAP
import com.lookie.opluskey.ui.model.SLIDER_ICON_INSET
import com.lookie.opluskey.ui.model.SLIDER_INSIDE_MARGIN
import com.lookie.opluskey.ui.model.SLIDER_ROW_TAP_TRACK
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

/**
 * 滑杆提交结果的统一出口：成功不动（等重组把 `dragging` 归位到新值），失败拨回当前真实值。
 *
 *  抽成一支只为让 [SliderRow] 留在 detekt 的 `LongMethod` 长度线内。
 */
private fun sliderCommit(
    dragging: androidx.compose.runtime.MutableFloatState,
    spec: SliderSpec,
    onCommit: (Int) -> Boolean,
): (Int) -> Unit =
    { raw ->
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
    val commit = sliderCommit(dragging, spec, onCommit)
    val tick = rememberTick()
    //  点标题 / 摘要（或行尾那个当前值）弹「手动输入」小窗 —— 滑杆在这些档上**像素不够分**
    //  （长按延迟 200–2000 一档 50ms，36 档挤在一根轨道上），想落在「恰好 1250」几乎不可能。
    //  小窗就是全项目那一个（`NameDialog` 多收一个 `range`），标题与图标**取原行**。
    var typing by remember { mutableStateOf(false) }
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
    val sliderIconColor by rememberRowIconColor(spec.enabled, icon = spec.icon, label = "SliderIcon")
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
        //  **整行可点**：点行的任何一处（含行首图标那一格、行内边距、行尾那个当前值）都弹
        //  「手动输入」小窗，按下反馈与波纹由库这一层给 —— 与页面其余行同一条口径。
        //  点轨道那一下不算：那条手势会把抬手消费掉（见 `tapSliderToSeek`），
        //  否则点轨道会同时「跳到那个值」与「弹窗」。
        onClick = {
            tick()
            typing = true
        },
        //  行首图标要自己管颜色：库只给**它自己画的东西**按 `enabled` 上色，
        // `startAction` 槽位原样渲染 —— 不给的话 `enabled = false` 会「字灰了、图标还亮着」
        // （与 [ArrowPreference] 那一行同一个坑）。
        startAction = {
            Icon(
                spec.icon,
                contentDescription = null,
                tint = sliderIconColor,
                //  补上库少给的那 8dp，图标两侧留白才一样宽（见 [ROW_START_ICON_GAP]）
                modifier = Modifier.padding(end = ROW_START_ICON_GAP),
            )
        },
        // 行尾当前值。文案走 `spec.valueText`（毫秒那几档是 `durationText`：1000ms 以上写成秒；
        //  振动强度那档写「弱 / 中 / 强」）；给 `null` 的一档不画。
        //  这里的入参是**拖动中的那个值**（`dragging`），所以行尾的档位说法跟着滑杆实时变，
        //  不用等抬手写盘回来 —— 那条回来时 `dragging` 会被重组归位到同一个值，不跳。
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
            Modifier.tapSliderToSeek(spec, SLIDER_ROW_TAP_TRACK) { v ->
                seekLatest(v)
            },
    ) {
        SliderRowTexts(spec = spec, colors = colors)
    }

    //  手动输入：小窗、落盘、触觉都在那一支里（见 [SliderValueInput]）
    SliderValueInput(show = typing, spec = spec, onDismiss = { typing = false }, onValue = commit)
}

/**
 * 滑杆行那个「手动输入」小窗 —— 从 [SettingsPage.SliderRow] 里分出来，让那一支留在 detekt
 * 的长度线内。
 *
 *  小窗本体是**全项目那一个**（`NameDialog` 多收一个 `range`），标题与图标**取原行**
 *  （同一张卡里可能并着两根滑杆，弹出来的是哪一根要先认得出）。写回去走的还是滑杆那条
 *  `commit`（见 `sliderCommit`）—— 钳制、写盘失败拨回、判据日志都在那一处，
 *  手动输入不另开一条落盘路径。
 *
 *  **打进来的数要先吸到刻度上**（`spec.snap`）：不吸的话配置里会存进 260 这种「不在档位上」
 *  的值，滑杆画出来滑块夹在两档之间 —— 看着就像 step 失效了。拖动与点轨道本来就落在刻度上，
 *  所以这条只有手动输入这一路要补。
 */
@Composable
private fun SettingsPage.SliderValueInput(
    show: Boolean,
    spec: SliderSpec,
    onDismiss: () -> Unit,
    onValue: (Int) -> Unit,
) {
    val tick = rememberTick()
    NameDialog(
        show = show,
        title = spec.title,
        label = I18n.get(Strings.SLIDER_INPUT_LABEL, "手动输入"),
        icon = spec.icon,
        initial = spec.value.toString(),
        range = spec.min..spec.max,
        //  值域与步进放在框下面**常驻**：占位在填着字时就看不见了，而这两件事正是打字时要看的。
        hint = I18n.format(Strings.SLIDER_INPUT_RANGE, spec.min, spec.max, spec.step),
        onDismiss = onDismiss,
        onConfirm = { raw ->
            onDismiss()
            raw.toIntOrNull()?.let { v ->
                tick()
                onValue(spec.snap(v))
            }
        },
    )
}

/**
 * 标题 + 摘要那两行 —— 从 [SettingsPage.SliderRow] 里分出来，让那一支只留接线。
 *
 *  **本身不接点击**：整行的点击在 `BasicComponent` 那一层（`onClick`），波纹与按下反馈都归它 ——
 *  在这里再挂一个 `clickable` 只会得到「只有文字那一小块有反馈」的半吊子效果。
 */
@Composable
private fun SliderRowTexts(
    spec: SliderSpec,
    colors: SliderTextColors,
) {
    // 标题与摘要之间那 2dp 是库的私有常量 `MarginBetweenLine`（值取自 `Component.kt`）。
    Column {
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
}

/** 滑杆轨道（行里那一根）—— 规格取自 [SliderSpec]，实现转给 [TrackSlider]。 */
@Composable
private fun SliderTrack(
    spec: SliderSpec,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    TrackSlider(
        value = value,
        min = spec.min,
        max = spec.max,
        step = spec.step,
        enabled = spec.enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                // 行首图标把滑杆也挤开（见 [SLIDER_ICON_INSET]）
                .padding(start = SLIDER_ICON_INSET),
        // 不给调用方那份时只标出厂默认值一个点（毫秒那几档要的就是这个）
        keyPoints = spec.keyPoints ?: listOf(spec.default.toFloat()),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/**
 * 一根滑杆本身 —— **全项目唯一一处**直接碰库 `Slider` 的地方。
 *
 *  行里的那一根（[SliderTrack]）与弹窗里的那一根（`BrightnessDialog`）走同一份实现：
 *  刻度点配色、`steps` 的算法、`magnetThreshold = 0`、触觉交给调用方（`hapticEffect = None`）
 *  这些口径必须一致 —— 各写一份的话，同一个应用里的两根滑杆手感与配色会不一样。
 *
 *  **「点轨道跳值」不在这里**：它要挂在比滑杆大的外层节点上（见 `tapSliderToSeek`
 *  与 `SLIDER_ROW_TAP_TRACK`），挂在滑杆自己的 `modifier` 上会与它内部的拖动争事件。
 *  同理，跨档振动（`rememberTick`）也由调用方在 `onValueChange` 里给。
 *
 * ## 为什么参数到 8 个（`@Suppress("LongParameterList")`）
 *
 *  值域（min / max / step / enabled）、当前值、两个回调、`modifier`、刻度点 —— 全是行参，
 *  拆成 options 对象只会让两个调用点各多一层壳（与 `NameInputBody` 那边同一个理由）。
 *
 * @param keyPoints 要在轨道上**画出来的**刻度点；`null` = 一个点都不画
 *   （库那边 `showKeyPoints` 只在 `keyPoints` 非 null 时有效）
 */
@Suppress("LongParameterList")
@Composable
internal fun TrackSlider(
    value: Float,
    min: Int,
    max: Int,
    step: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyPoints: List<Float>? = null,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    //  注意：这里要的是**配色方案**，不是 [SliderRow] 里那个 `colors`（那是行内三处文字的颜色）。
    val scheme = COUITheme.colorScheme
    // 刻度点分两档，库按「这一点是不是在滑块左边（已走过的那一段）」二选一：
    //  · 满段上那一档（`keyPointForegroundColor`）压在强调色轨道上，值本身是强调色提亮 —— 看得见，不动；
    //  · 空段上那一档（`keyPointColor`）压在**轨道底色**上 —— 库给浅色的是 `#F2F2F2`，
    //    而 `sliderBackground` 是 5% 黑压在卡片上，合成出来**正好也是 `#F2F2F2`**：
    //    同一个颜色，于是「出厂默认值」那个圆点只要落在滑块右边就整个消失
    //    （页面缩放滑杆默认 100%、拖到 70% 时那一格就在白区里，看不见）。
    //  **取前景色（`onSurface`）、按固定不透明度画，不再从 `sliderBackground` 的 alpha 推**：
    //  「同色 × 4」这条路只在库那套自带色板下成立（那边 `sliderBackground` 是 5% / 10% 的
    //  前景色）；Monet 那一档整套 `Colors` 是库按系统配色另算的，`sliderBackground` 在那里是
    //  不透明实色 —— 乘几倍还是同一个颜色，圆点与轨道一模一样，整个看不见
    //  （用户口径「slider 的 step 在 Monet 下看不见」）。
    //  前景色天然与底子反向：浅底上是淡灰点、深底上是淡白点，与主题怎么算配色无关。
    //  不直接用强调色：那一点只是「出厂值在这儿」的记号，染成主题色会亮到像个可点的把手。
    //  置灰时压薄一档（[TRACK_TICK_DIM_ALPHA]）：禁用态的轨道本来就淡
    //  （`disabledSecondary` / `disabledPrimarySlider`），点若还用「启用那一档」的浓度，
    //  会比旁边那根灰轨道还显眼 —— 看着像还能点。压薄之后它只是「隐约还在那儿」。
    val trackTick = scheme.onSurface.copy(alpha = TRACK_TICK_ALPHA)
    val trackTickDim = scheme.onSurface.copy(alpha = TRACK_TICK_DIM_ALPHA)
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        //  刻度点（`keyPoints`）的颜色库**不看 `enabled`**：`keyPointColor` /
        //  `keyPointForegroundColor` 各只有一对值，绘制处只按「这一点是不是当前值」二选一
        //  （见库 `Slider.kt` 里的 `kpColor`）—— 于是滑杆置灰时轨道灰了、那几个点还是主题色，
        //  看着像还能点。
        //  这里自己换一套：置灰时两档都退成 [trackTickDim]（轨道那一色、压薄一档），
        //  压在 `disabledPrimarySlider` / `disabledSecondary` 这对浅灰轨道上只是隐约一个点 ——
        //  整行只有一个灰调，与轨道一起退成灰，也不会有哪一档比轨道更显眼。
        //  其余几项不传，走 `sliderColors` 的默认值（就是库本来用的那一套），不接管它们。
        colors =
            SliderDefaults.sliderColors(
                keyPointColor = if (enabled) trackTick else trackTickDim,
                keyPointForegroundColor = if (enabled) scheme.sliderKeyPointForeground else trackTickDim,
            ),
        valueRange = min.toFloat()..max.toFloat(),
        // 离散刻度：两端之间分 (max-min)/step 档（`steps` 不含两个端点）
        steps = ((max - min) / step - 1).coerceAtLeast(0),
        // 调用方给了刻度点就画（库的 `keyPoints` + `showKeyPoints` 要一起给）。
        //  只标**不吸附**：`steps > 0` 时库的磁性吸附分支本来就走不到（它只在 `steps == 0`
        //  且 keyPoints 非空时吸附），这里再把 `magnetThreshold` 显式写 0f 兜一道 ——
        //  免得将来哪一档的 steps 变成 0，拖动手感被这个点悄悄改掉。
        showKeyPoints = keyPoints != null,
        keyPoints = keyPoints.orEmpty(),
        magnetThreshold = 0f,
        // 与库默认同值，显式写出来是因为「点轨道跳值」要按它们算几何 ——
        // 账在 [SLIDER_TAP_ZONE] / [SLIDER_SIDE_INSET] 的注释里
        height = SliderDefaults.MinHeight,
        // 库那一档关掉：`SliderHapticState.handleStepHaptic` 是**二选一** —— `keyPoints` 非空
        //  就整条切到「只在关键点上振一次」，为空才按「跨一档振一次」。默认的圆点只有一个，
        //  开着它就等于拖动全程只在拖回默认值时振。圆点要留，于是触觉由调用方自己给
        //  （见 `SliderRow` 与 `BrightnessDialog` 的 `onValueChange` 里那次 tick）。
        hapticEffect = SliderDefaults.SliderHapticEffect.None,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/**
 * 轨道上那个刻度点的**不透明度**（颜色取 `onSurface`，见 [SliderTrack] 里 `trackTick` 的说明）。
 *
 *  20%：浅底上是淡灰点、深底上是淡白点 —— 比轨道实一档，不抢眼也不会看不见。
 *  库的浅色档两处同色（点 `#F2F2F2`、轨道合成出来也是 `#F2F2F2`）会整个消失，这一档就是
 *  为了把它拉开；Monet 那套配色下 `sliderBackground` 是不透明实色，只能改用前景色才分得开。
 */
private const val TRACK_TICK_ALPHA = 0.20f

/**
 * 置灰时那个点的不透明度（同上，取 `onSurface`）。
 *
 *  10%：比「启用那一档」淡一半，压在禁用态的浅灰轨道上只剩一个若有若无的点 ——
 *  与「这一行现在不能动」对得上，又还看得出出厂值在哪儿。
 */
private const val TRACK_TICK_DIM_ALPHA = 0.10f

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
