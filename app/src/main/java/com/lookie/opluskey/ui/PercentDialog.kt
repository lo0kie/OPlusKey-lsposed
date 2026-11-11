package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.present.actionDetail
import com.lookie.opluskey.ui.model.SliderTapTrack
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
 * 「这一档百分比怎么描述」那三件套 —— 亮度与音量只在这三处不同（见 [PercentDialog]）。
 *
 *  收成一个对象而不是三个平铺参数：它们在调用点永远是同一档一起给的（标题、最小值、
 *  百分比怎么念都跟着 `mode` 走），拆开只会让弹窗那一支的参数表越过 detekt 的 `LongParameterList`。
 *
 * @param title 弹窗标题（「屏幕亮度」/「音量」）
 * @param min 允许的最小百分比（「设置为」是 0，加减档是 [PERCENT_STEP]）
 * @param labelOf 把百分比拼成那一句话（`core/model` 的 `actionDetail`）
 */
internal class PercentPrompt(
    val title: String,
    val min: Int,
    val labelOf: (Int) -> String,
)

/**
 * 「一格百分比」的通用弹窗：一行实时文案 + 一根滑杆，按确定才回调。
 *
 *  亮度与音量两族共用这一支，差别只有三处，都从 [PercentPrompt] 进来。两族各写一份的话，
 *  滑杆手感与退场行为迟早会走岔。
 *
 * ## 为什么三档都要再问一句
 *
 *  「增加 / 减少 / 设置为」本身不带数值，而数值才是这条绑定的全部内容（`bright:+15`）。
 *  菜单里写死成 `+10` / `-10` / `50` 会让人以为改不了 —— 这一层就是那个「改」的地方。
 *
 * ## 范围随档位不同
 *
 *  「设置为」可以到 0；「增加 / 减少」从 [PERCENT_STEP] 起步 —— 「增加 0%」不是一个动作，
 *  摆出来只会让滑杆最左边那一档点下去什么都不发生。
 *
 * ## 数值跟着滑杆实时显示
 *
 *  标题那行写的是**这一档 + 当前状态**（`增加 - 15%`，与网格小字、步骤标题同一句）：
 *  滑杆上只有一个把手、没有刻度数字，不写出来的话用户没法确认自己选的是几。
 *
 * @param show 弹窗开着没有（由调用方按「哪一行点开了」决定）
 * @param prompt 这一档的说明三件套（标题 / 最小值 / 百分比怎么念），见 [PercentPrompt]
 * @param initial 打开时的初值（当前绑定的数值，没绑过时是各档的默认值）
 * @param onConfirm 只在按下「确定」时回调；取消 / 点空白都走 [onDismiss]
 */
@Composable
internal fun SettingsPage.PercentDialog(
    show: Boolean,
    prompt: PercentPrompt,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // 每次打开都以调用方给的初值为起点：上一次拖到一半关掉，再打开不该还挂着那个没提交的数。
    //  **但关闭动画期间不能重置** —— 键写成 `remember(show)` 时，`show` 一翻 false 就重算成
    //  初值，滑杆在退场那一段会「跳回去再消失」（真机看得出来）。所以值只**打开时**取一次初值。
    //  初值也要先夹进这一档的区间：调用方传进来的是配置里的原值，越界时把手在范围内、回传的
    //  却是越界数（`onConfirm(percent)`）。
    var percent by remember { mutableIntStateOf(initial.coerceIn(prompt.min, MAX_PERCENT)) }
    LaunchedEffect(show) {
        if (show) percent = initial.coerceIn(prompt.min, MAX_PERCENT)
    }

    FrostedDialog(
        show = show,
        title = prompt.title,
        onDismiss = onDismiss,
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                onPositive = { onConfirm(percent) },
            )
        },
    ) {
        PercentSliderBody(
            min = prompt.min,
            percent = percent,
            label = prompt.labelOf(percent),
            onPercentChange = { percent = it },
        )
    }
}

/**
 * 弹窗正文：一行实时文案 + 一根滑杆。
 *
 *  滑杆**不直接用库的 `Slider`**：走项目自己那根 [TrackSlider]（刻度点配色、`steps` 算法与设置页
 *  那几根完全一致），外面再包一层「点轨道跳值」与跨档振动 —— 两条都是项目里滑杆的标准手感
 *  （见 `ui/PageSlider.kt` 与 `ui/SliderRow.kt`）。
 *
 *  从 [PercentDialog] 里分出来，只为让那一支只留接线（与 `PageNameDialog` 的 `NameInputBody`
 *  同一个理由）。
 */
@Composable
private fun SettingsPage.PercentSliderBody(
    min: Int,
    percent: Int,
    label: String,
    onPercentChange: (Int) -> Unit,
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
        Box(
            Modifier
                .fillMaxWidth()
                .tapSliderToSeek(
                    min = min,
                    max = MAX_PERCENT,
                    step = PERCENT_STEP,
                    enabled = true,
                    track = DIALOG_TAP_TRACK,
                ) { value ->
                    if (value != percent) {
                        tick()
                        onPercentChange(value)
                    }
                },
        ) {
            TrackSlider(
                value = percent.coerceIn(min, MAX_PERCENT).toFloat(),
                min = min,
                max = MAX_PERCENT,
                step = PERCENT_STEP,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                //  弹窗里不画刻度点：1% 一格是一百个点，密到看不出哪个是哪个
                keyPoints = null,
                onValueChange = { raw ->
                    //  吸附到 1% 的整数倍：库的 `steps` 只画刻度点、不保证传回来的值一定落在刻度上
                    //  （拖动中的插值会给出带小数的位置）。跨档才振，与滑杆行同一判据。
                    val next = (raw / PERCENT_STEP).roundToInt() * PERCENT_STEP
                    if (next != percent) tick()
                    onPercentChange(next)
                },
                //  这一步不落盘（按「确定」才落），抬手不需要额外动作
                onValueChangeFinished = {},
            )
        }
    }
}

/**
 * 「屏幕亮度」三档的百分比弹窗 —— 只是把亮度那一族的三件事喂给 [PercentDialog]。
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
    PercentDialog(
        show = show,
        prompt =
            PercentPrompt(
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
 * 「音量」三档的百分比弹窗 —— 与 [SettingsPage.BrightnessDialog] 同一个形状。
 *
 *  两者的百分比都落在**各自那条量程**上（音量是 `档 / 最大档`，见 `host/action/VolumeAction`），
 *  所以同一句「增加 - 10%」在两边是同一件事。
 */
@Composable
internal fun SettingsPage.VolumeDialog(
    show: Boolean,
    mode: Action.Volume.Mode,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    PercentDialog(
        show = show,
        prompt =
            PercentPrompt(
                title = I18n.get(Strings.CATALOG_VOLUME, "音量"),
                min = if (mode == Action.Volume.Mode.FIX) 0 else PERCENT_STEP,
                labelOf = { actionDetail(Action.Volume(mode, it)).orEmpty() },
            ),
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
