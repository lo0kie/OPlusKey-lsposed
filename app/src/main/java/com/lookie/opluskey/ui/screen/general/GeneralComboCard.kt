package com.lookie.opluskey.ui.screen.general

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.screen.combo.setComboWindow
import com.lookie.opluskey.ui.write.setComboHoldMin
import com.lookie.opluskey.ui.write.setComboPassthrough
import io.github.suqi8.coui.kmp.basic.CardListPosition

// 「组合按键设置」那张卡 —— 从「按键功能」页搬来（用户口径）：这两项都是**全局**的，
//  与「有哪几颗键 / 配了哪几组」无关，和「按键设置」里另外两节是同一类东西。

/**
 * 「组合键设置」那张卡 —— 都不针对某一组，只剩三个**参数**：
 *
 *  1. **无操作时执行按键命令**：组合绑的是「无操作」时，两颗键怎么办（开关打开 = 各按自己那一档
 *     照常执行；关闭 = 什么都不做）。为什么值得一个开关：「配了组合、但只给长按绑了动作」很常见，
 *     这时短按那一格是空的，用户要么希望「两颗键各干各的」，要么希望「这颗组合键短按没功能」。
 *  2. **按键之间的等待**：两颗键最多隔多久才算「一起按下」。这一行有代价，副标题里写清楚了。
 *  3. **首键按住时长**：按住那两档的下限。
 *
 *  **「使用等待手势 / 使用按住手势」这两颗不在这里了**：一档组合只认一种触发方式，那是它的
 *  身份（`ComboKey.mode`），入口在「组合键」编辑页那颗 ⋮ 的「触发方式」一行。于是这里没有
 *  「公共默认认哪条」这件事，也就没有按它显隐的理由 —— 三个参数都一直在。
 *
 *  上面第 2、3 行是**两条互斥触发方式各自的**参数，只在用到那一条的组合上生效
 *  （等窗口读窗口这个数、按住读下限，见 `Config.comboWindowFor` / `comboHoldMinFor`）；
 *  这里只管**公共默认**，某一组的「独立设置」页管那一组自己的值。
 */
@Composable
internal fun SettingsPage.ComboSettingsCard(enabled: Boolean) {
    val cfg = configState.config.value
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"),
                        summaryOn = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_ON, "每颗键各执行自己的绑定"),
                        summaryOff = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_OFF, "什么都不做"),
                        icon = PageIcons.NoMatch,
                        checked = cfg.global.comboPassthrough,
                        enabled = enabled,
                    ) { on ->
                        //  走 `setComboPassthrough`（现读配置 + 回执）：与同卡其它控件同一条路，
                        //  不用这一帧的 `cfg` 快照 —— 同一页两根控件先后动时，快照会把先写的那一位顶回去。
                        setComboPassthrough(on)
                    },
                )
                //  两颗键之间的等待窗口 —— 滑杆另起一支（这一行比开关长得多）。
                add(
                    CardRow.Custom { position ->
                        ComboWindowRow(position, enabled)
                    },
                )
                //  按住手势的**下限**：首键要按满这么久、且还没抬手，第二颗落下才算这一档组合。
                add(
                    CardRow.Custom { position ->
                        ComboHoldMinRow(position, enabled)
                    },
                )
            },
    )
}

/**
 * 「按键之间的等待」那一行（**公共**那一档）—— 两颗键按下最多隔多久才算「一起按下」。
 *
 *  **这一行写的是公共值**：没单独设过的组合都用它（`Config.comboWindowFor`）—— 所以同一个参数
 *  有两处入口：这里管默认，某一组的「独立设置」页管那一组自己的值（`KeySettingsScreen` 的
 *  `ComboOwnCard`）。两处文案逐字相同，只有改的对象不同。
 *
 *  **这一行有代价**，副标题里写明：参与组合的键按下去要先等这么久（等第二颗键会不会来），
 *  这段时间里它自己的单击 / 长按都还没开始判。不参与任何组合的键一点不受影响 ——
 *  这一点也得说，否则用户会以为整页都变慢。
 *
 *  **它是「等窗口」那一档的参数**：按住那两档的成员根本不按它等 —— 判定侧只把认窗口的档
 *  算进「值得先等一个窗口」的集合（`ConfigChannel.deriveCombos` 的 `windowMembers`）。
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
                summary = I18n.get(Strings.COMBO_WINDOW_DESC, "调大后，参与组合的按键等待更久"),
                //  「等一段时间再继续」那一颗钟（与「命令间隔时长」同一个字形）
                icon = PageIcons.DelayStep,
                min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                value = configState.config.value.global.comboWindowMs,
                default = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                //  10ms 一档：20~1000 之间 98 档。放宽到 1 秒是为了手慢 / 三颗的组合留余地，
                //  精度仍然留在 10ms 这一档（细调靠点轨道 + 手动输入，见 `tapSliderToSeek`）
                step = 10,
                enabled = enabled,
                //  行尾写「80 ms」—— 这一行问的就是「等多久」，不写数看不出来
                valueText = { "$it ms" },
            ),
    ) { setComboWindow(it) }
}

/**
 * 「首键按住时长」那一行（**公共**那一档）—— 按住手势的下限。
 *
 *  两颗键几乎同时落下（实测相差 0~40ms，谁先上报看硬件）不算「按住」：那种「一起按」归
 *  「等窗口」那一档。这一行划的正是那条线。
 *
 *  **这一行写的是公共值**：没单独设过的组合都用它（`Config.comboHoldMinFor`）—— 同一个参数
 *  两处入口：这里管默认，某一组的「独立设置」页管那一组自己的值（`KeySettingsCard` 那张卡里）。
 *  两处文案逐字相同，只有改的对象不同。
 *
 *  上限 500ms 与组合长按阈值同量级：调得比那颗键自己的长按还宽时，它早就先判出长按、
 *  按住那条本来就作废了（`ComboDetector.longPressPassed`）。
 */
@Composable
internal fun SettingsPage.ComboHoldMinRow(
    position: CardListPosition,
    enabled: Boolean,
) {
    SliderRow(
        position = position,
        spec =
            SliderSpec(
                title = I18n.get(Strings.COMBO_HOLD_MIN_TITLE, "首键按住时长"),
                summary = I18n.get(Strings.COMBO_HOLD_MIN_DESC, "先按住的那颗按满这么久、还没抬手，再按另一颗才算组合"),
                //  「按住不放」那只手（与「按键延迟」同一个字形）
                icon = PageIcons.DelayHold,
                min = GlobalConfig.COMBO_HOLD_MIN_MIN_MS,
                max = GlobalConfig.COMBO_HOLD_MIN_MAX_MS,
                value = configState.config.value.global.comboHoldMinMs,
                default = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
                //  10ms 一档：40~500 之间 47 档，够细也够短
                step = 10,
                enabled = enabled,
                valueText = { "$it ms" },
            ),
    ) { setComboHoldMin(it) }
}
