package com.lookie.opluskey.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.present.ClockCatalog
import com.lookie.opluskey.core.present.actionDetail
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.model.page.SliderTapTrack
import com.lookie.opluskey.ui.row.TrackSlider
import com.lookie.opluskey.ui.row.tapSliderToSeek
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

/** 百分比每次挪多少 —— 用户口径「slider，step 为 1%」；亮度与音量共用。 */
internal const val PERCENT_STEP = 1

/** 滑杆的最大值（百分比）。 */
private const val MAX_PERCENT = 100

/**
 * 弹窗里那根滑杆的「点轨道跳值」几何：手势节点就是滑杆本身，
 *
 *  所以两端都不让位（行里那份要让开行内边距与行首图标，见 `SLIDER_ROW_TAP_TRACK`），
 *  带子高度取整块节点 —— 节点本来就只有滑杆那么高。
 */
private val DIALOG_TAP_TRACK =
    SliderTapTrack(
        startInset = 0.dp,
        endInset = 0.dp,
        zone = SliderDefaults.MinHeight,
    )

/**
 * 「这一格数值怎么描述」那几件套 —— 亮度 / 音量 / 倒计时只在这里不同（见 [NumberDialog]）。
 *
 *  收成一个对象而不是几个平铺参数：它们在调用点永远是同一档一起给的（标题、量程、
 *  数值怎么念都跟着这一档走）。
 *
 * @param title 弹窗标题（「屏幕亮度」/「音量」/「倒计时」）
 * @param min 允许的最小值（「设置为」是 0，加减档是 [PERCENT_STEP]，倒计时是 `CLOCK_TIMER_MIN_SECONDS`）
 * @param max 允许的最大值（百分比恒为 [MAX_PERCENT]，倒计时另有一档）
 * @param step 一次挪多少（百分比是 1%，倒计时是 30 秒）
 * @param labelOf 把数值拼成那一句话（`core/model` 的 `actionDetail` / `ClockCatalog.durationLabel`）
 */
internal class NumberPrompt(
    val title: String,
    val min: Int,
    val max: Int = MAX_PERCENT,
    val step: Int = PERCENT_STEP,
    val labelOf: (Int) -> String,
)

/**
 * 「一格数值」的通用弹窗：一行实时文案 + 一根滑杆，按确定才回调。
 *
 *  亮度 / 音量那两族与「倒计时」的时长共用这一支，差别只有**量程与念法**，都从调用方进来
 *  （[NumberPrompt]）。各写一份的话，滑杆手感与退场行为迟早会走岔。
 *
 * ## 为什么这几档都要再问一句
 *
 *  「增加 / 减少 / 设置为」本身不带数值、倒计时那一行也不带时长，而数值才是这条绑定的全部
 *  内容（`bright:+15` / `timer:300`）。菜单里写死成 `+10` / `-10` / `50` / `5 分钟` 会让人
 *  以为改不了 —— 这一层就是那个「改」的地方。
 *
 * ## 数值跟着滑杆实时显示
 *
 *  标题那行写的是**这一档 + 当前数值**（`增加 - 15%`、`倒计时 - 5 分钟`，与网格小字、
 *  步骤标题同一句）：滑杆上只有一个把手、没有刻度数字，不写出来的话用户没法确认自己选的是几。
 *
 * @param show 弹窗开着没有（由调用方按「哪一行点开了」决定）
 * @param prompt 这一格的说明（标题 / 量程 / 数值怎么念），见 [NumberPrompt]
 * @param initial 打开时的初值（当前绑定的数值，没绑过时是各档的默认值）
 * @param onConfirm 只在按下「确定」时回调；取消 / 点空白都走 [onDismiss]
 */
@Composable
internal fun SettingsPage.NumberDialog(
    show: Boolean,
    prompt: NumberPrompt,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // 每次打开都以调用方给的初值为起点：上一次拖到一半关掉，再打开不该还挂着那个没提交的数。
    //  **但关闭动画期间不能重置** —— 键写成 `remember(show)` 时，`show` 一翻 false 就重算成
    //  初值，滑杆在退场那一段会「跳回去再消失」（真机看得出来）。所以值只**打开时**取一次初值。
    //  初值也要先夹进这一档的区间：调用方传进来的是配置里的原值，越界时把手在范围内、回传的
    //  却是越界数（`onConfirm(value)`）。
    //  **初值可能超出滑杆量程**（手写在配置里的 `timer:86400`）：把手画在端点，但**不把用户
    //  没动过的值改写掉** —— 打开又点确定就把他手写的时长悄悄换成量程上限，是这一档最难发现的
    //  一种损失（`touched` 就是「这一次到底动没动」）。
    val inRange = initial.coerceIn(prompt.min, prompt.max)
    var value by remember { mutableIntStateOf(inRange) }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            value = inRange
            touched = false
        }
    }

    FrostedDialog(
        show = show,
        title = prompt.title,
        onDismiss = onDismiss,
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                //  没动过且初值本来就在量程外 ⇒ 原样回传（见上面那段）
                onPositive = { onConfirm(if (touched || initial == inRange) value else initial) },
            )
        },
    ) {
        NumberSliderBody(
            min = prompt.min,
            max = prompt.max,
            step = prompt.step,
            value = value,
            label = prompt.labelOf(value),
            onValueChange = {
                value = it
                touched = true
            },
        )
    }
}

/**
 * 弹窗正文：一行实时文案 + 一根滑杆。
 *
 *  滑杆**不直接用库的 `Slider`**：走项目自己那根 [TrackSlider]（刻度点配色、`steps` 算法与设置页
 *  那几根完全一致），外面再包一层「点轨道跳值」与跨档振动 —— 两条都是项目里滑杆的标准手感
 *  （见 `ui/row/PageSlider.kt` 与 `ui/row/SliderRow.kt`）。
 *
 *  从 [NumberDialog] 里分出来，只为让那一支只留接线（与 `PageNameDialog` 的 `NameInputBody`
 *  同一个理由）。
 */
@Composable
private fun SettingsPage.NumberSliderBody(
    min: Int,
    max: Int,
    step: Int,
    value: Int,
    label: String,
    onValueChange: (Int) -> Unit,
) {
    // 与滑杆行同一条触觉通道：拖动跨档、点轨道跳值各振一次（判据只有系统静音，见 `tickNow`）
    val tick = rememberTick()
    Column(
        Modifier
            .fillMaxWidth()
            .dialogSideMargin(),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        //  「点轨道跳值」挂在外层节点上（挂滑杆自己的 `modifier` 会与它内部的拖动争事件）
        //
        //  回调必须是**稳定的**：`tapSliderToSeek` 的 `pointerInput` 键里只有 `min/max/step/enabled`，
        //  闭包停留在首次组合那一刻 —— 直接捕获上面那几个参数（尤其 `value`）就一直用旧的那份，
        //  于是「先拖动、再点回初值」会被 `next != value` 判成没变、这一下被丢掉（读数也不跳）。
        //  与 `PageSlider` 那一处同一条写法：闭包只捕获这个稳定的 State，调用时才读值。
        val seekLatest by rememberUpdatedState<(Int) -> Unit> { next ->
            if (next != value) {
                tick()
                onValueChange(next)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .tapSliderToSeek(
                    min = min,
                    max = max,
                    step = step,
                    enabled = true,
                    track = DIALOG_TAP_TRACK,
                ) { next -> seekLatest(next) },
        ) {
            TrackSlider(
                value = value.coerceIn(min, max).toFloat(),
                min = min,
                max = max,
                step = step,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                //  弹窗里不画刻度点：1% 一格是一百个点，密到看不出哪个是哪个
                keyPoints = null,
                onValueChange = { raw ->
                    //  吸附到 step 的整数倍：库的 `steps` 只画刻度点、不保证传回来的值一定落在刻度上
                    //  （拖动中的插值会给出带小数的位置）。跨档才振，与滑杆行同一判据。
                    val next = (raw / step).roundToInt() * step
                    if (next != value) tick()
                    onValueChange(next)
                },
                //  这一步不落盘（按「确定」才落），抬手不需要额外动作
                onValueChangeFinished = {},
            )
        }
    }
}

/**
 * 「屏幕亮度」三档的百分比弹窗 —— 只是把亮度那一族的三件事喂给 [NumberDialog]。
 *
 * @param mode 增加 / 减少 / 设置为
 */
@Composable
internal fun SettingsPage.BrightnessDialog(
    show: Boolean,
    mode: Action.Brightness.Mode,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    NumberDialog(
        show = show,
        prompt =
            NumberPrompt(
                title = I18n.get(Strings.CATALOG_BRIGHTNESS, "屏幕亮度"),
                min = if (mode == Action.Brightness.Mode.FIX) 0 else PERCENT_STEP,
                labelOf = { actionDetail(Action.Brightness(mode, it)).orEmpty() },
            ),
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 音量弹窗要写的那一条 —— **哪一档 + 哪一路流**。
 *
 *  收成一个值：这两个字段是一件事的两半：
 *  它们一起决定标题（媒体音量 / 铃声音量）与落盘那一句（`volume:` / `ringervolume:`）。
 */
internal data class VolumeTarget(
    val mode: Action.Volume.Mode,
    /** `true` = 写 `ringervolume:`（铃声流）；`false` = 写 `volume:`（媒体流）。 */
    val ringer: Boolean,
)

/**
 * 音量那一族三档的百分比弹窗 —— 与 [SettingsPage.BrightnessDialog] 同一个形状。
 *
 *  三档都落在**各自那条量程**上（音量是 `档 / 量程`，见 `host/action/VolumeAction`），
 *  所以同一句「增加 - 10%」在媒体与铃声两边是同一件事。
 *
 * @param target 写哪一条命令 + 哪一档（两者一起决定标题与落盘那一句，见 [VolumeTarget]）
 */
@Composable
internal fun SettingsPage.VolumeDialog(
    show: Boolean,
    target: VolumeTarget,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val mode = target.mode
    val ringer = target.ringer
    NumberDialog(
        show = show,
        prompt =
            NumberPrompt(
                title =
                    if (ringer) {
                        I18n.get(Strings.CATALOG_RINGER_VOLUME, "铃声音量")
                    } else {
                        I18n.get(Strings.CATALOG_VOLUME, "媒体音量")
                    },
                min = if (mode == Action.Volume.Mode.FIX) 0 else PERCENT_STEP,
                labelOf = { percent ->
                    if (ringer) {
                        actionDetail(Action.RingerVolume(mode, percent)).orEmpty()
                    } else {
                        actionDetail(Action.Volume(mode, percent)).orEmpty()
                    }
                },
            ),
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** 倒计时最短能选多久 —— 再短就不是「等一会儿」，而是按下去就到点了（最短 30 秒）。 */
internal const val TIMER_STEP_SECONDS = 30

/**
 * 倒计时滑杆最长能选多久（2 小时）。
 *
 *  **这是滑杆的量程，不是写法的上限** —— 别与 `Action.TIMER_MAX_SECONDS`（24 小时）混：
 *  更长的时长仍可从配置里手写，打开这个弹窗再点确定**不会**把它改小（见 [NumberDialog] 里
 *  那段「没动过就原样回传」）。两个名字都叫 `TIMER_MAX_SECONDS` 时，改一处漏一处就是
 *  「手写的 12 小时被悄悄换成 2 小时」。
 */
internal const val TIMER_SLIDER_MAX_SECONDS = 120 * ClockCatalog.MINUTE_SECONDS

/** 没绑过倒计时时滑杆停在哪儿（5 分钟）—— 与「泡面 / 煮蛋」那类常见等待同一量级。 */
internal const val TIMER_DEFAULT_SECONDS = 5 * ClockCatalog.MINUTE_SECONDS

/**
 * 「计时器」那一格的**时长**弹窗 —— 与 [SettingsPage.BrightnessDialog] 同一个形状（[NumberDialog]
 * 换一套量程与念法）。滑杆的单位是**秒**（落盘也是秒），30 秒一格；念出来按分 / 秒分段。
 *
 *  为什么量程卡在 2 小时：这一层是给「按一下就等一会儿」用的，滑杆越长越点不准；
 *  更长的倒计时手写配置照样能起（`Action.Timer` 认到 24 小时）。
 *
 * @param initialSeconds 打开时的秒数（已绑过就是当初那个时长）
 * @param onConfirm 按「确定」时给的是**秒**，由调用方拼成 `timer:<秒>`
 */
@Composable
internal fun SettingsPage.TimerDialog(
    show: Boolean,
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    NumberDialog(
        show = show,
        prompt =
            NumberPrompt(
                title = I18n.get(Strings.CATALOG_CLOCK, "计时器"),
                min = TIMER_STEP_SECONDS,
                max = TIMER_SLIDER_MAX_SECONDS,
                step = TIMER_STEP_SECONDS,
                labelOf = { ClockCatalog.durationLabel(it) },
            ),
        initial = initialSeconds,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
