package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.state.binding.KeyWatchState
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.write.setWaitMultiWindow
import com.lookie.opluskey.ui.write.write

/**
 * 「按键设置」页里的**单击**那张卡 —— 「单击延迟」+「忽视无操作双击手势」两行。
 *
 *  它挂在那颗键的设置页里（`KeySettingsScreen`），与长按那张卡一起回答「这颗键怎么判」；
 *  「这一档绑什么」在「按键绑定」页的网格那一头。
 *
 *  「忽视无操作双击手势」也在这张卡里（见 `instantSingleRow`）：它决定的是**第二下抬手之后**
 *  怎么办，改它的动机是让单击更快 —— 与「单击延迟」是同一次判定链上的两个参数，
 *  按「用户此刻在调单击」分，比按「它名字里带双击」分更贴实际。
 *
 *  注意：这张卡的**写路径**与网格**故意少判一道 `linkReady`**（格子那边是 `enabled && linkReady`，
 *  与 `pick` 同一判据）：这张卡写的是**参数**（延迟 / 判定开关），写了就落盘，注入侧恢复后照样生效，
 *  不需要注入侧回话；格子点了要写动作、拿去执行，未就绪时写了也是白写。
 *  这是有意的不一致，不要再把两边「统一」。
 *
 *  判据里**没有**「适配清单」那一道：清单只回答「这一组验过没有」，不参与任何闸
 *  （AGENTS 一、13）—— 别照着旧注释把它加回来，那会让清单外的机器整页变灰。
 */
@Composable
internal fun SettingsPage.BindingSettingsCard(
    enabled: Boolean,
    disabledHint: String? = null,
) {
    val cfg = configState.config.value
    // 这一份参数是**每颗键自己的**（`KeyConfig.doubleClickMs`），不再与其他键共用
    val id = KeyWatchState.editing(cfg)
    val key = cfg.key(id)
    val rowsEnabled = enabled
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Custom { position ->
                        SliderRow(
                            position = position,
                            spec =
                                SliderSpec(
                                    title = I18n.get(Strings.SETTING_DOUBLE_CLICK_DELAY, "单击延迟"),
                                    icon = PageIcons.DelayTap,
                                    //  说明写的是**这一位管什么**：它不只是「双击的等待」，而是连击链上
                                    //  每一击到下一击的间隔（配了三击 / 四击，这条间隔一样管用）。
                                    //  只写「等待双击」的话，配了高档位的人会以为它跟那几档无关。
                                    summary =
                                        I18n.get(
                                            Strings.BINDING_DOUBLE_CLICK_WAIT,
                                            "两击之间最多隔多久，超过就重新从头数",
                                        ),
                                    min = KeyConfig.MIN_DOUBLE_CLICK_MS,
                                    max = KeyConfig.MAX_DOUBLE_CLICK_MS,
                                    value = key.doubleClickMs,
                                    default = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                                    enabled = rowsEnabled,
                                    disabledHint = disabledHint,
                                ),
                        ) { v ->
                            val now = configState.config.value
                            val target = KeyWatchState.editing(now)
                            write(
                                now.copy(keys = now.keys + (target to now.key(target).copy(doubleClickMs = v))),
                                "double_click_ms=$v key=${target.id}",
                            ) != CommandsSave.Failed
                        }
                    },
                )
                //  第二行：同一张卡、同一片「这一档怎么判」的语义（见类注释）
                add(instantSingleRow(rowsEnabled, disabledHint))
                //  第三行：判定**时机**（到档即判 / 逐次等满）。
                //  「只参与组合按键」不在这张卡里 —— 它决定这颗键**还算不算一颗单键**，
                //  与「多击之类那一档存不存在」同族，放在独立设置页（见 `KeySettingsScreen`）。
                add(waitMultiWindowRow(rowsEnabled, disabledHint))
            },
    )
}

/** 「逐次等满多击窗口」那一行 —— 判定时序（见 `KeyConfig.waitMultiWindow`）。 */
private fun SettingsPage.waitMultiWindowRow(
    enabled: Boolean,
    disabledHint: String?,
): CardRow {
    val key = configState.config.value.key(KeyWatchState.editing(configState.config.value))
    return CardRow.Switch(
        title = I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW, "逐次等满多击窗口"),
        summaryOn = I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW_ON, "每一下都等满窗口再判，多按的一下也算数"),
        summaryOff = I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW_OFF, "数够要的那一档就立刻判，不等窗口"),
        icon = PageIcons.Click,
        checked = key.waitMultiWindow,
        enabled = enabled,
        disabledHint = disabledHint,
    ) {
        setWaitMultiWindow(it)
    }
}
