package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SwitchRow
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.setInstantSingle
import com.lookie.opluskey.ui.state.deviceSupported
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.cardListPositionOf

/**
 * 「按键设置」卡（**单击档**）—— 单击延迟 + 「立即触发单击」。
 *
 *  它是一张「设置项列表」（滑杆 / 开关那些行），属于**这一档手势本身**的行为，排在动作网格之前：
 *  先看到「这一档怎么判、有什么可调」，再挑「绑什么」。双击档没有这张卡
 *  （它的两个可调项都在这里），长按档走 `LongRepeatCard`。
 *
 *  注意：这张卡的两条**写路径**吃与网格同一套「适配清单」闸，但**故意比格子少判一道
 *  `linkReady`**（格子那边是 `enabled && linkReady && deviceSupported`，与 `pick` 同一判据）：
 *  这张卡写的是**参数**（延迟 / 立即触发），写了就落盘，注入侧恢复后照样生效，不需要注入侧回话；
 *  格子点了要写动作、拿去执行，未就绪时写了也是白写。这是有意的不一致，不要再把两边「统一」。
 */
@Composable
internal fun SettingsPage.BindingSettingsCard(enabled: Boolean) {
    val cfg = configState.config.value
    val tick = rememberTick()
    val rowsEnabled = enabled && deviceSupported
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        // 「单击延迟」不跟着「立即触发单击」置灰：两个状态独立 —— 前者管「等多久才判双击」，
        // 后者管「双击为无操作时还要不要等」。（开着后者且双击为无操作时前者确实用不上，
        // 但那是运行期的组合，由判定侧按配置自己走，界面不替用户把这一行锁死。）
        SliderRow(
            title = I18n.get(Strings.SETTING_DOUBLE_CLICK_DELAY, "单击延迟"),
            icon = PageIcons.DelayTap,
            summary = I18n.get(Strings.BINDING_DOUBLE_CLICK_WAIT, "单击后等待双击的时长"),
            position = cardListPositionOf(0, SINGLE_SETTING_ROWS),
            min = GlobalConfig.MIN_DOUBLE_CLICK_MS,
            max = GlobalConfig.MAX_DOUBLE_CLICK_MS,
            value = cfg.global.doubleClickMs,
            default = GlobalConfig.DEFAULT_DOUBLE_CLICK_MS,
            enabled = rowsEnabled,
        ) { v ->
            write(
                configState.config.value.copy(
                    global =
                        configState.config.value.global
                            .copy(doubleClickMs = v),
                ),
                "double_click_ms=$v",
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        // 「双击为无操作时不用等那一截」—— 开关的语义与判定侧见 `GlobalConfig.instantSingle`。
        SwitchRow(
            title = I18n.get(Strings.SETTING_TRIGGER_IMMEDIATELY, "立即触发单击"),
            // 不说「双击窗口」：那是判定侧的内部说法；用户只要知道「什么时候不用等」
            summary = I18n.get(Strings.BINDING_INSTANT_SINGLE, "双击未绑动作时立即触发单击"),
            // 与上面那行同一个图标位 —— 同一张卡里两行都有图标时标题才对齐
            icon = PageIcons.InstantTrigger,
            checked = cfg.global.instantSingle,
            position = cardListPositionOf(1, SINGLE_SETTING_ROWS),
            enabled = rowsEnabled,
        ) {
            tick()
            setInstantSingle(it)
        }
    }
}
