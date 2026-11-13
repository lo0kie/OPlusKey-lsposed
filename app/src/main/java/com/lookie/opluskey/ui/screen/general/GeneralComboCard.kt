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
import com.lookie.opluskey.ui.write.setComboHoldOn
import com.lookie.opluskey.ui.write.setComboPassthrough
import com.lookie.opluskey.ui.write.setComboWindowOn
import io.github.suqi8.coui.kmp.basic.CardListPosition

// 「组合按键设置」那张卡 —— 从「按键功能」页搬来（用户口径）：这两项都是**全局**的，
//  与「有哪几颗键 / 配了哪几组」无关，和「按键设置」里另外两节是同一类东西。

/**
 * 「组合键设置」那张卡 —— 都不针对某一组：
 *
 *  1. **使用等待手势**：两条触发方式之一，紧跟它下面那一行是它的收尾语义（见 2），
 *     再往下是它的窗口参数（见 3）。
 *  2. **无操作时执行按键命令**：组合绑的是「无操作」时，两颗键怎么办（开关打开 = 各按自己那一档
 *     照常执行；关闭 = 什么都不做）。为什么值得一个开关：「配了组合、但只给长按绑了动作」很常见，
 *     这时短按那一格是空的，用户要么希望「两颗键各干各的」，要么希望「这颗组合键短按没功能」。
 *  3. **按键之间的等待**：两颗键最多隔多久才算「一起按下」。这一行有代价，副标题里写清楚了。
 *  4. **使用按住手势**：另一条触发方式。默认「窗口认、按住不认」—— 与这两位出现之前的手感一致。
 *     每组可以在「独立设置」里另设一份（`ComboKey.windowOn` / `holdOn`），所以这里的两颗是**公共默认**。
 *
 *  **第 2、3 行只在「使用等待手势」开着时出现**（并且是滑动进出场，不是凭空换行）：第 2 行说的是
 *  「窗口那条按出来了、可这一格没绑动作」怎么办 —— 只认按住的那一档不经过这个形状（按住按出来的
 *  每一套都有明确的槽），那时它既不该显示、也不参与取值（`Config.comboPassthroughFor` 同一份判据）；
 *  窗口关掉之后那个数也不再有任何作用（参与组合的键根本不按它等），所以滑杆跟着一起收起来。
 *  窗口参数排在「使用按住手势」**上面**：它属于窗口那条，摆到另一条触发方式底下就成了它的参数。
 */
@Composable
internal fun SettingsPage.ComboSettingsCard(enabled: Boolean) {
    val cfg = configState.config.value
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.COMBO_WINDOW_ON_TITLE, "使用等待手势"),
                        summaryOn = I18n.get(Strings.COMBO_WINDOW_ON_DESC_ON, "两颗键在等待窗口内先后按下也算组合"),
                        summaryOff = I18n.get(Strings.COMBO_WINDOW_ON_DESC_OFF, "不认窗口内先后按下"),
                        icon = PageIcons.HoldWindow,
                        checked = cfg.global.comboWindowOn,
                        enabled = enabled,
                    ) { on ->
                        setComboWindowOn(on)
                    },
                )
                //  紧跟「使用等待手势」：它是那一条按法的收尾语义，不是另一条触发方式。
                //  **不在这里 `if (…) add(…)`**：行本身一直在，靠 `visible` 做滑动进出场 ——
                //  直接增删会让下面的行跳一格（`CardList` 的进出场动画正是为这件事准备的）。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"),
                        summaryOn = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_ON, "每颗键各执行自己的绑定"),
                        summaryOff = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_OFF, "什么都不做"),
                        icon = PageIcons.NoMatch,
                        checked = cfg.global.comboPassthrough,
                        enabled = enabled,
                        visible = cfg.global.comboWindowOn,
                    ) { on ->
                        //  走 `setComboPassthrough`（现读配置 + 回执）：与上面那张卡的其它开关同一条路，
                        //  不用这一帧的 `cfg` 快照 —— 同一页两根控件先后动时，快照会把先写的那一位顶回去。
                        setComboPassthrough(on)
                    },
                )
                //  两颗键之间的等待窗口 —— 滑杆另起一支（这一行比开关长得多）。
                //  **跟着「使用等待手势」走**：那一颗关着时这个数不再有任何作用（成员键不再按它等，
                //  见 `ConfigChannel.joinsCombo`），留着只是一根拨了没反应的滑杆。
                add(
                    CardRow.Custom(visible = cfg.global.comboWindowOn) { position ->
                        ComboWindowRow(position, enabled)
                    },
                )
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.COMBO_HOLD_ON_TITLE, "使用按住手势"),
                        summaryOn = I18n.get(Strings.COMBO_HOLD_ON_DESC_ON, "按住其中一颗按键，再按另一颗触发"),
                        summaryOff = I18n.get(Strings.COMBO_HOLD_ON_DESC_OFF, "不认按住一颗再按另一颗"),
                        //  字形与「松手才触发」同一颗（`LongFireOnRelease`）：这一档要的正是「按住不放」
                        icon = PageIcons.LongFireOnRelease,
                        checked = cfg.global.comboHoldOn,
                        enabled = enabled,
                    ) { on ->
                        //  只拨开关：每加一种顺序才动那一颗键的长按时机（见「按法」菜单与
                        //  `HoldGestureAsk`），开这一颗不替用户改任何按键
                        setComboHoldOn(on)
                    },
                )
                //  按住手势的**下限**：首键要按满这么久、且还没抬手，第二颗落下才算这一档组合。
                //  跟着上面那颗开关走 —— 按住那条关着时这个数不起任何作用，留着只是一根拨了
                //  没反应的滑杆（与「按键之间的等待」同一份显隐口径）。
                add(
                    CardRow.Custom(visible = cfg.global.comboHoldOn) { position ->
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
 *  **它是「使用等待手势」那条的参数**：那一颗关着时两件事一起消失 —— 这一行跟着收起来，
 *  成员键也不再按它等（`ConfigChannel.comboMemberIds` 只算认窗口那条的档）。
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
 *  「使用等待手势」那条，而那一位关着时它就不是组合。这一行划的正是那条线。
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
