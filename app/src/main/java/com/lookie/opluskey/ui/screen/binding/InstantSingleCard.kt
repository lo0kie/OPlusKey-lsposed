package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.setInstantSingle
import com.lookie.opluskey.ui.state.KeyWatchState

/**
 * 「按键设置」卡（**双击档**）—— 只有一个开关：忽视无操作双击手势。
 *
 * ## 为什么它从单击档搬到了这里
 *
 *  这个开关改的是**双击怎么判**（双击没绑动作时，不承认那是一次双击，按两下单击算），
 *  而不是单击怎么判。它原先和「单击延迟」挤在同一张卡里，理由是两者都落在同一次判定的
 *  两个阶段上；但用户找它时带着的前提是「我在调哪一档」，按档分才对得上。
 *
 *  搬过来之后，单击档那张卡只剩「单击延迟」一行 —— 那一行仍然是**等待**的那一侧；
 *  这里这一行是**等待之后怎么解释**的那一侧。
 *
 * ## 与「单击延迟」的关系
 *
 *  开着它、且双击确实没绑动作时，那个延迟窗口就用不上了（判定侧抬手即判单击）。
 *  但那是**运行期的组合**，界面**不**替用户把「单击延迟」那一行锁死：两个开关状态独立，
 *  用户随时可能把双击再绑回某个动作，那时延迟立刻又生效。
 *
 * ## 写路径
 *
 *  与单击档那张卡同一条纪律：**不判 `linkReady`**（写的是参数不是动作，落盘就生效，
 *  不需要注入侧回话）。理由写全在 `BindingSettingsCard` 的类注释里。
 *  「适配清单」不参与任何闸，这里当然也不判它（AGENTS 一、13）。
 */
@Composable
internal fun SettingsPage.InstantSingleCard(enabled: Boolean) {
    val cfg = configState.config.value
    // 与「单击延迟」同一条：这一位也是**每颗键自己的**（`KeyConfig.instantSingle`）
    val checked = cfg.key(KeyWatchState.editing(cfg)).instantSingle
    val tick = rememberTick()
    val rowsEnabled = enabled
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.SETTING_IGNORE_DOUBLE, "忽视无操作双击手势"),
                    // 说明写**结果**而不是机制：用户要判断的是「开 / 关之后双击会变成什么样」，
                    //  所以两句各说一边的结果 —— 「按两下算单击」与「第二下不生效」。
                    summaryOn = I18n.get(Strings.BINDING_INSTANT_SINGLE_ON, "双击无动作时，按两下算单击"),
                    summaryOff = I18n.get(Strings.BINDING_INSTANT_SINGLE_OFF, "双击无动作时，第二下不生效"),
                    icon = PageIcons.IgnoreDouble,
                    checked = checked,
                    enabled = rowsEnabled,
                ) {
                    tick()
                    setInstantSingle(it)
                },
            ),
    )
}
