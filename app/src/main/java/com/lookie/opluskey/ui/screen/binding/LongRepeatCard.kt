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
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.SwitchRow
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.setLongRepeat
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.write
import com.lookie.opluskey.ui.writeAndNotice
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.cardListPositionOf

// 「长按」那一档的设置卡 —— 从 `BindingList.kt` 搬出来（它不属于「网格」，只属于长按这一档）。

/**
 * 「长按」tab 下**唯一**的那张卡：长按连续触发（+ 连续触发时振动 + 触发间隔）+ 长按延迟。
 *
 *  行序：**连续触发在最上面**，「连续触发时振动」与「触发间隔」紧跟着它（都只在开关打开时出现，
 *  顺序是先振动后间隔），「长按延迟」落到最后一行 —— 前三行是一件事（按住不放要不要一直重来、
 *  每一次要不要振、隔多久来一次），而「多久算长按」是另一件事。
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
    // 四行：长按连续触发 +（开关打开时的）连续触发时振动 +（同上）触发间隔 + 长按延迟
    val rows = if (key.longRepeat) 4 else 2
    val tick = rememberTick()

    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        LongRepeatSwitchRow(blockedReason, rows, key, enabled = !blocked)
        // 「触发间隔」与「连续触发时振动」只在**开关打开时**才存在（关着时没有可调的东西）。
        //  它们是上面那一行的参数，所以紧跟着上面那一行，而不是掉到「长按延迟」后面去；
        //  连上面那条分隔线一起显隐（[AnimatedColumn]）—— 只藏行的话会留一条孤零零的线。
        //
        //  这两行的先后：**先「振动」后「间隔」**（用户口径）。先答「每次要不要振」，
        //  再调「隔多久一次」—— 一个是行为的有无，一个是它的数值。
        AnimatedColumn(visible = key.longRepeat) {
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            // 「连续触发时振动」：连发的行为细节（每一次重复要不要振），关着时没有「每一次」可谈。
            SwitchRow(
                title = I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"),
                summaryOn = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_ON, "每次重复都振动"),
                summaryOff = I18n.get(Strings.BINDING_REPEAT_VIBRATE_DESC_OFF, "只在长按触发时振动一次"),
                icon = PageIcons.Vibrate,
                checked = key.longRepeatVibrate,
                position = cardListPositionOf(1, rows),
                enabled = !blocked,
            ) {
                tick()
                setRepeatVibrate(it)
            }
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            SliderRow(
                position = cardListPositionOf(2, rows),
                spec =
                    SliderSpec(
                        title = I18n.get(Strings.BINDING_REPEAT_INTERVAL_TITLE, "触发间隔"),
                        icon = PageIcons.DelayRepeat,
                        summary = I18n.get(Strings.BINDING_REPEAT_INTERVAL_DESC, "长按连续触发的重复间隔"),
                        min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                        max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                        value = key.longRepeatIntervalMs,
                        default = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                        enabled = !blocked,
                    ),
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
 * 「长按连续触发」那一行 —— 从卡里抽出来只为长度（detekt 的 `LongMethod`）。
 *
 *  它的副标题有**三个来源**：置灰原因（为什么点不了）优先，否则按开关真假各一句。
 *  这种「三取一」摊在卡片函数里会把那张卡顶出长度上限，而它自己也确实是一件事，
 *  所以自成一个行函数 —— 与同文件的 `LongPressDelayRow` 同一套写法。
 */
@Composable
private fun SettingsPage.LongRepeatSwitchRow(
    blockedReason: String?,
    rows: Int,
    key: KeyConfig,
    enabled: Boolean,
) {
    val tick = rememberTick()
    SwitchRow(
        title = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"),
        // 真假各一句，都说「现在是什么行为」—— 关着的时候再讲「关闭后会怎样」是错的
        summaryOn = blockedReason ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_ON, "按住不放，按间隔重复执行"),
        summaryOff = blockedReason ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC_OFF, "按住不放只执行一次"),
        icon = PageIcons.LongRepeat,
        checked = key.longRepeat,
        position = cardListPositionOf(0, rows),
        enabled = enabled,
    ) {
        tick()
        setLongRepeat(it)
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
        position = cardListPositionOf(if (key.longRepeat) 3 else 1, rows),
        spec =
            SliderSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_TITLE, "长按延迟"),
                summary = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_DESC, "多久算长按，比它短算单击"),
                icon = PageIcons.DelayHold,
                min = KeyConfig.MIN_LONG_PRESS_MS,
                max = KeyConfig.MAX_LONG_PRESS_MS,
                value = key.longPressMs,
                default = KeyConfig.DEFAULT_LONG_PRESS_MS,
                enabled = enabled,
            ),
    ) { v ->
        val latest = configState.config.value
        val id = KeyWatchState.editing(latest)
        write(
            latest.copy(keys = latest.keys + (id to latest.key(id).copy(longPressMs = v))),
            "long_press_ms=$v key=${id.id}",
        ) != CommandsSave.Failed
    }
}

/**
 * 切「连续触发时振动」—— 与 [setLongRepeat] 同一颗键。
 *
 *  它只在 `KeyConfig.longRepeat` 为真时有意义（那一行也只在那时出现）；关掉「长按连续触发」不清
 *  这一位：再打开时用户之前选的那一档应当还在。
 *  执行侧读它见 `host/GestureReceiver.runAction`。
 */
private fun SettingsPage.setRepeatVibrate(on: Boolean) {
    val cfg = configState.config.value
    val id = KeyWatchState.editing(cfg)
    writeAndNotice(
        cfg.copy(keys = cfg.keys + (id to cfg.key(id).copy(longRepeatVibrate = on))),
        "long_repeat_vibrate=${if (on) 1 else 0} key=${id.id}",
    ) { switchNotice(I18n.get(Strings.BINDING_REPEAT_VIBRATE_TITLE, "连续触发时振动"), on) }
}
