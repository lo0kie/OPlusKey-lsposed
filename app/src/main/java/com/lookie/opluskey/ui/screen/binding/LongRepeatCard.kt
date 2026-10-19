package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.AnimatedColumn
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SwitchRow
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.setLongRepeat
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.cardListPositionOf

// 「长按」那一档的设置卡 —— 从 `BindingList.kt` 搬出来（它不属于「网格」，只属于长按这一档）。

/**
 * 「长按」tab 下**唯一**的那张卡：长按连续触发（+ 触发间隔）+ 长按延迟。
 *
 *  行序（调整）：**连续触发在最上面**、触发间隔紧跟着它（只在开关打开时出现），
 * 「长按延迟」落到最后一行 —— 前两行是一件事（按住不放要不要一直重来、隔多久来一次），
 * 而「多久算长按」是另一件事。
 *
 * @param blockedReason 非 null ⇒ **整张卡**置灰、不吃点击（连续触发那行与长按延迟那行），
 * @param masterEnabled 首页那个「按键绑定」主开关 —— 与 [blockedReason] 一起决定「长按延迟」那行
 */
@Composable
internal fun SettingsPage.LongRepeatCard(
    blockedReason: String?,
    masterEnabled: Boolean,
) {
    val blocked = blockedReason != null
    val cfg = configState.config.value
    // **当前在编辑的那一颗**：写死侧键的话，停在自定义键上拨开关 / 拖滑杆改的全是侧键
    //  （卡片上的值却还是侧键的，看着像「拖了没反应」）。
    val editing = KeyWatchState.editing(cfg)
    val key = cfg.key(editing)
    // 三行：长按连续触发 +（开关打开时的）触发间隔 + 长按延迟
    val rows = if (key.longRepeat) 3 else 2
    val tick = rememberTick()

    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        // 置灰的原因写在副标题里（两种原因：主开关关了 / 选中的是多条命令）；
        // 行首图标与行尾开关的置灰都在 [SwitchRow] 里统一处理。
        SwitchRow(
            title = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"),
            summary = blockedReason ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC, "按住不放时按固定间隔重复执行"),
            icon = PageIcons.LongRepeat,
            checked = key.longRepeat,
            position = cardListPositionOf(0, rows),
            enabled = !blocked,
        ) {
            tick()
            setLongRepeat(it)
        }
        // 「触发间隔」只在**开关打开时**才存在（关着时没有可调的间隔）。
        //  它是上面那一行的参数，所以紧跟着上面那一行，而不是掉到「长按延迟」后面去；
        // 连上面那条分隔线一起显隐（[AnimatedColumn]）—— 只藏行的话会留一条孤零零的线。
        AnimatedColumn(visible = key.longRepeat) {
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            SliderRow(
                title = I18n.get(Strings.BINDING_REPEAT_INTERVAL_TITLE, "触发间隔"),
                icon = PageIcons.DelayRepeat,
                summary = I18n.get(Strings.BINDING_REPEAT_INTERVAL_DESC, "长按连续触发的重复间隔"),
                position = cardListPositionOf(1, rows),
                min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                value = key.longRepeatIntervalMs,
                default = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                enabled = !blocked,
            ) { v ->
                val now = configState.config.value
                val id = KeyWatchState.editing(now)
                write(
                    now.copy(keys = now.keys + (id to now.key(id).copy(longRepeatIntervalMs = v))),
                    "long_repeat_interval_ms=$v key=${id.id}",
                ) != CommandsSave.Failed
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        LongPressDelayRow(rows, masterEnabled && !blocked)
    }
}

/**
 * 「长按延迟」那一行 —— 多久算长按。
 *
 *  它的 `enabled` 跟着**整张卡**走（主开关 + 置灰原因）：这个参数对多条命令其实仍然生效
 *  （判定长按的时机与绑的是什么动作无关），但同一张卡里上面那行已经置灰、它独自亮着，
 *  会让人以为「这行还能改 ⇒ 多条命令也许还能连续触发」（用户报的）。
 */
@Composable
private fun SettingsPage.LongPressDelayRow(
    rows: Int,
    enabled: Boolean,
) {
    val now = configState.config.value
    val key = now.key(KeyWatchState.editing(now))
    // 「长按延迟」—— 多久算长按。
    //  它的 enabled 跟着**整张卡**走（主开关 + [blockedReason]）。这个参数对多条命令其实
    //  仍然生效（判定长按的时机与绑的是什么动作无关），但同一张卡里上面那行已经置灰、
    //  它独自亮着，会让人以为「这行还能改 ⇒ 多条命令也许还能连续触发」（用户报的）。
    //
    //  说明里点一句「比它短算单击」：按不出来时用户第一个怀疑的就是这个数。到这一句为止 ——
    //  写全「这一档已短于人一次自然按压」那种话太长，会把行撑成两行、与旁边几行的说明也不齐。
    SliderRow(
        title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_TITLE, "长按延迟"),
        icon = PageIcons.DelayHold,
        summary = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_DESC, "多久算长按，比它短算单击"),
        position = cardListPositionOf(if (key.longRepeat) 2 else 1, rows),
        min = KeyConfig.MIN_LONG_PRESS_MS,
        max = KeyConfig.MAX_LONG_PRESS_MS,
        value = key.longPressMs,
        default = KeyConfig.DEFAULT_LONG_PRESS_MS,
        enabled = enabled,
    ) { v ->
        val latest = configState.config.value
        val id = KeyWatchState.editing(latest)
        write(
            latest.copy(keys = latest.keys + (id to latest.key(id).copy(longPressMs = v))),
            "long_press_ms=$v key=${id.id}",
        ) != CommandsSave.Failed
    }
}
