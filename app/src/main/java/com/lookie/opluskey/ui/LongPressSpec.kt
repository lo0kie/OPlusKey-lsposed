package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.model.PageIcons
import io.github.suqi8.coui.kmp.basic.CardListPosition

// 「长按」那一档的设置卡 —— 单键页与组合键页**共用这一张**。

/**
 * 一次组合要的四样现行值、两档可点判据，以及四条写回去的路。
 *
 * 为什么要有这一份：这张卡在「按键绑定」的长按档与「组合键」的某一条上各画一次，**行序、文案、
 * 可见性判据必须一模一样**（用户口径「和另一个一样」）。原先两处各写一份同样的 `CardRow` 清单，
 * 改一句副标题就得记着去改另一处 —— 现在「画哪些行」只有这一份，两页只答两件事：读谁的字段、
 * 写完落到哪一条写盘说明（`onRepeat` / `onVibrate` / `onInterval` / `onPressMs` 仍留在各自的页里，
 * 写盘说明与提示语义位的差异不跨页传染）。
 *
 * @param enabled 「长按连续触发」与「连续触发时振动」两行能不能点
 * @param delayEnabled 「长按延迟」那一行能不能点 —— 它比上面两行多一道闸：单键页还看首页那个主开关
 * @param blockedReason 非 null ⇒ 整张卡置灰，且「长按连续触发」的副标题换成它（说清为什么点不了）
 * @param onInterval / @param onPressMs 返回 `false` ⇒ 这一档没落盘（滑杆拨回真实值）
 */
@Suppress("LongParameterList")
internal class LongPressSpec(
    val repeat: Boolean,
    val vibrate: Boolean,
    val intervalMs: Int,
    val pressMs: Int,
    val enabled: Boolean,
    val delayEnabled: Boolean,
    val blockedReason: String? = null,
    val onRepeat: (Boolean) -> Unit,
    val onVibrate: (Boolean) -> Unit,
    val onInterval: (Int) -> Boolean,
    val onPressMs: (Int) -> Boolean,
)

/**
 * 「长按」那一档的**唯一**一张卡：长按连续触发（+ 连续触发时振动 + 触发间隔）+ 长按延迟。
 *
 *  行序：**连续触发在最上面**，「连续触发时振动」与「触发间隔」紧跟着它（都只在开关打开时出现，
 *  顺序是先振动后间隔），「长按延迟」落到最后一行 —— 前三行是一件事（按住不放要不要一直重来、
 *  每一次要不要振、隔多久来一次），而「多久算长按」是另一件事。
 */
@Composable
internal fun SettingsPage.LongPressSettingsCard(spec: LongPressSpec) {
    val blockedReason = spec.blockedReason
    CardList(
        rows =
            listOf(
                // 「长按连续触发」：副标题有**三个来源** —— 置灰原因（为什么点不了）优先，
                //  否则按开关真假各一句。这种「三取一」写成一行的 `summaryOn/Off` 刚好。
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"),
                    summaryOn =
                        blockedReason
                            ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_ON, "按住不放，按间隔重复执行"),
                    summaryOff =
                        blockedReason
                            ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_OFF, "按住不放只执行一次"),
                    icon = PageIcons.LongRepeat,
                    checked = spec.repeat,
                    enabled = spec.enabled,
                    onCheckedChange = spec.onRepeat,
                ),
                // 「连续触发时振动」与「触发间隔」只在**开关打开时**才存在（关着时没有可调的东西）。
                //  它们是上面那一行的参数，所以紧跟着上面那一行，而不是掉到「长按延迟」后面去；
                //  显隐由列表连分隔线一起做（`visible`）—— 只藏行会留一条孤零零的线。
                //
                //  这两行的先后：**先「振动」后「间隔」**（用户口径）。先答「每次要不要振」，
                //  再调「隔多久一次」—— 一个是行为的有无，一个是它的数值。
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"),
                    summaryOn = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_ON, "每次重复都振动"),
                    summaryOff = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_OFF, "只在长按触发时振动一次"),
                    icon = PageIcons.Vibrate,
                    checked = spec.vibrate,
                    enabled = spec.enabled,
                    visible = spec.repeat,
                    onCheckedChange = spec.onVibrate,
                ),
                CardRow.Custom(visible = spec.repeat) { position ->
                    RepeatIntervalRow(position, spec.intervalMs, spec.enabled, spec.onInterval)
                },
                CardRow.Custom { position ->
                    LongPressDelayRow(position, spec.pressMs, spec.delayEnabled, spec.onPressMs)
                },
            ),
    )
}

/**
 * 「触发间隔」那一行 —— 长按连续触发隔多久重放一次。
 *
 *  单独一支只为让 [LongPressSettingsCard] 留在 detekt 的长度上限之内（它自己也要能被 `visible` 藏起来）。
 */
@Composable
private fun SettingsPage.RepeatIntervalRow(
    position: CardListPosition,
    value: Int,
    enabled: Boolean,
    onCommit: (Int) -> Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.BINDING_REPEAT_INTERVAL_TITLE, "触发间隔"),
                icon = PageIcons.DelayRepeat,
                summary = I18n.get(Strings.BINDING_REPEAT_INTERVAL_DESC, "长按连续触发的重复间隔"),
                min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                value = value,
                default = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                enabled = enabled,
            ),
        onCommit = onCommit,
    )
}

/**
 * 「长按延迟」那一行 —— 多久算长按。
 *
 *  说明里点一句「比它短算单击」：按不出来时用户第一个怀疑的就是这个数。到这一句为止 ——
 *  写全「这一档已短于人一次自然按压」那种话太长，会把行撑成两行、与旁边几行的说明也不齐。
 *
 *  它的 `enabled` 由 [LongPressSpec.delayEnabled] 单独给：同一张卡里上面几行灰着、它独自亮着，
 *  会让人以为「这行还能改 ⇒ 上面那几行也许还能点」（用户报的）。
 */
@Composable
private fun SettingsPage.LongPressDelayRow(
    position: CardListPosition,
    value: Int,
    enabled: Boolean,
    onCommit: (Int) -> Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_TITLE, "长按延迟"),
                summary = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_DESC, "多久算长按，比它短算单击"),
                icon = PageIcons.DelayHold,
                min = KeyConfig.MIN_LONG_PRESS_MS,
                max = KeyConfig.MAX_LONG_PRESS_MS,
                value = value,
                default = KeyConfig.DEFAULT_LONG_PRESS_MS,
                enabled = enabled,
            ),
        onCommit = onCommit,
    )
}
