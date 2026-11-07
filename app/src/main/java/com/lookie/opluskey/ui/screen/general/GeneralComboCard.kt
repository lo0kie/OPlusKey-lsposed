package com.lookie.opluskey.ui.screen.general

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.combo.setComboWindow
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.writeAndNotice
import io.github.suqi8.coui.kmp.basic.CardListPosition

// 「组合按键设置」那张卡 —— 从「按键功能」页搬来（用户口径）：这两项都是**全局**的，
//  与「有哪几颗键 / 配了哪几组」无关，和「按键设置」里另外两节是同一类东西。

/**
 * 「组合键设置」那张卡 —— 两行，都不针对某一组：
 *
 *  1. **无操作时执行按键命令**：组合绑的是「无操作」时，两颗键怎么办（开关打开 = 各按自己那一档
 *     照常执行；关闭 = 什么都不做）。为什么值得一个开关：「配了组合、但只给长按绑了动作」很常见，
 *     这时短按那一格是空的，用户要么希望「两颗键各干各的」，要么希望「这颗组合键短按没功能」。
 *  2. **按键之间的等待**：两颗键最多隔多久才算「一起按下」。这一行有代价，副标题里写清楚了。
 */
@Composable
internal fun SettingsPage.ComboSettingsCard(enabled: Boolean) {
    val cfg = configState.config.value
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"),
                    summaryOn = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_ON, "两颗键各执行自己的绑定"),
                    summaryOff = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_OFF, "什么都不做"),
                    icon = PageIcons.NoMatch,
                    checked = cfg.global.comboPassthrough,
                    enabled = enabled,
                ) { on ->
                    writeAndNotice(
                        cfg.copy(global = cfg.global.copy(comboPassthrough = on)),
                        "combo_passthrough=${if (on) 1 else 0}",
                        slot = HINT_SLOT_COMBO,
                    ) {
                        switchNotice(I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"), on)
                    }
                },
                //  第 2 行：两颗键之间的等待窗口 —— 滑杆另起一支（这一行比开关长得多）
                CardRow.Custom { position -> ComboWindowRow(position, enabled) },
            ),
    )
}

/**
 * 「按键之间的等待」那一行 —— 两颗键按下最多隔多久才算「一起按下」。
 *
 *  **这一行有代价**，副标题里写明：参与组合的键按下去要先等这么久（等第二颗键会不会来），
 *  这段时间里它自己的单击 / 长按都还没开始判。不参与任何组合的键一点不受影响 ——
 *  这一点也得说，否则用户会以为整页都变慢。
 *
 *  **写的是公共那一份**：判定在「还不知道会组成哪一组」的时候就发生了（按下第一颗键那一刻），
 *  所以它只能是一份全局值。公共页与组合的「独立设置」页**共用这一支**——
 *  两个入口、同一行、同一个落点（用户在那两处找到的是同一件事）。
 */
@Composable
internal fun SettingsPage.ComboWindowRow(
    position: CardListPosition,
    enabled: Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.COMBO_WINDOW_TITLE, "按键之间的等待"),
                //  「所有组合共用」这几个字必须在：两个页面都摆这一行，不写清会被当成「这一组的」
                summary = I18n.get(Strings.COMBO_WINDOW_DESC, "调大会让参与组合的按键多等这么久（所有组合共用）"),
                //  「等一段时间再继续」那一颗钟（与「命令间隔时长」同一个字形）
                icon = PageIcons.DelayStep,
                min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                value = configState.config.value.global.comboWindowMs,
                default = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                //  10ms 一档：20~300 之间 29 档，够细也不至于挪一格要半天
                step = 10,
                enabled = enabled,
                //  行尾写「80 ms」—— 这一行问的就是「等多久」，不写数看不出来
                valueText = { "$it ms" },
            ),
    ) { setComboWindow(it) }
}
