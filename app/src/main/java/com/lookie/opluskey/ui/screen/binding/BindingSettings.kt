package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.setSameEvent
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.write

/**
 * 「按键设置」页里的**单击**那张卡 —— 只有「单击延迟」一行。
 *
 *  它挂在那颗键的设置页里（`KeySettingsScreen`），与下面两张卡（忽视双击 / 长按那几项）
 *  一起回答「这颗键怎么判」；「这一档绑什么」在「按键绑定」页的网格那一头。
 *
 *  注意：**「忽视无操作双击手势」那个开关不在这里** —— 它讲的是双击怎么判，挂在双击那张卡
 *  （见 `InstantSingleCard`）。两者都定义在 `doubleClickMs` 附近，但用户找它们时是带着
 *  「我在调哪一档」这个前提来的，所以按档分。
 *
 *  注意：这张卡的**写路径**与网格**故意少判一道 `linkReady`**（格子那边是 `enabled && linkReady`，
 *  与 `pick` 同一判据）：这张卡写的是**参数**（延迟），写了就落盘，注入侧恢复后照样生效，
 *  不需要注入侧回话；格子点了要写动作、拿去执行，未就绪时写了也是白写。
 *  这是有意的不一致，不要再把两边「统一」。
 *
 *  判据里**没有**「适配清单」那一道：清单只回答「这一组验过没有」，不参与任何闸
 *  （AGENTS 一、13）—— 别照着旧注释把它加回来，那会让清单外的机器整页变灰。
 */
@Composable
internal fun SettingsPage.BindingSettingsCard(enabled: Boolean) {
    val cfg = configState.config.value
    // 这一份参数是**每颗键自己的**（`KeyConfig.doubleClickMs`），不再与其他键共用
    val id = KeyWatchState.editing(cfg)
    val key = cfg.key(id)
    val rowsEnabled = enabled
    CardList(
        rows =
            buildList {
                //  「三档同一事件」—— 这一颗键**整体**还要不要分档。它排在单击延迟上面：
                //  开着时那个等待根本不会发生（判定侧按下即执行），先看到它、再看到灰着的滑杆，
                //  读起来才是「为什么这根调不动」。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.SETTING_SAME_EVENT, "三个内置手势同事件时立即执行"),
                        summaryOn = I18n.get(Strings.BINDING_SAME_EVENT_ON, "按下立刻执行，不再等双击窗口与长按"),
                        summaryOff = I18n.get(Strings.BINDING_SAME_EVENT_OFF, "三档各自按自己的时序判定"),
                        icon = PageIcons.Immediate,
                        checked = key.sameEvent,
                        enabled = rowsEnabled,
                    ) {
                        setSameEvent(it)
                    },
                )
                add(
                    CardRow.Custom { position ->
                        SliderRow(
                            position = position,
                            spec =
                                SliderSpec(
                                    title = I18n.get(Strings.SETTING_DOUBLE_CLICK_DELAY, "单击延迟"),
                                    icon = PageIcons.DelayTap,
                                    summary = I18n.get(Strings.BINDING_DOUBLE_CLICK_WAIT, "单击后等待双击的时长"),
                                    min = KeyConfig.MIN_DOUBLE_CLICK_MS,
                                    max = KeyConfig.MAX_DOUBLE_CLICK_MS,
                                    value = key.doubleClickMs,
                                    default = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                                    //  这颗键开了「按下即执行」时那次等待不会发生（判定侧直接派发）——
                                    //  滑杆置灰。判据 `Config.sameEventNow`，与判定侧同一处。
                                    enabled = rowsEnabled && !cfg.sameEventNow(id),
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
            },
    )
}
