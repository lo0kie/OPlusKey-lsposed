package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.write.setGlobalClickWindow
import com.lookie.opluskey.ui.write.setGlobalWaitMultiWindow
import com.lookie.opluskey.ui.write.setWaitMultiWindow
import com.lookie.opluskey.ui.write.write

// 「单击」那张卡 —— **公共设置页与「独立设置」页（单键）用的是同一份**。
//
//  两行（判定时机 + 等待窗口）的文案、图标、上下限本来就该逐字相同，所以整张卡只写一遍；
//  两侧的差别全收在 [ClickParamSource] 里 —— 值从哪来、写到哪去、能不能动。
//  卡片自己不认识「公共」还是「某一颗键」，也不认识 `Config`：给它一份来源，它照着画。
//
//  **两侧的闸门不一样，这是有意的**：公共那一页是全局默认值，单键也要有个地方改单击延迟，
//  所以它不判「有没有多击」；独立设置页管的是「这颗键的多击怎么判」，一档多击都没开时没有
//  可判的东西（判据见 [clickKeyParams]）。

/**
 * 这张卡的**状态源** —— 同一个界面的两份落点。
 *
 *  公共那一份来自 `GlobalConfig`、写全局配置（[clickGlobalParams]）；某一颗键那一份来自
 *  `KeyConfig`、写回这颗键（[clickKeyParams]）。判定侧两处都读（`KeyConfig.clickWindowMs` 那一条口径）。
 *
 * @param collect 「固定窗口起点」那颗开关的 checked —— 与存储位 `waitMultiWindow` **相反**：
 *   开着 = 从首次按下只等一个窗口（`waitMultiWindow = false`），关着 = 每次松手重新起算。
 *   存储位当初按「要不要等满窗口」命名，界面按用户要的正向说法命名 —— 取反只在写回那一步做。
 * @param windowMs 等待窗口滑杆**显示**的值（按当前判定时机取那一位；`multiWaitMs` 为 0 时回落
 *   到单击延迟 —— 老配置就长这样，滑杆要显示一个具体值）。
 * @param enabled 这两行能不能动。
 * @param disabledHint 灰着被点一下时的说法（`null` = 用整页那句）。
 * @param onCollect 写判定时机。`false` = 没写进去（与开关自身的回执一致）。
 * @param onWindow 写窗口那个数，返回「写盘成没成」：滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 */
internal class ClickParamSource(
    val collect: Boolean,
    val windowMs: Int,
    val enabled: Boolean,
    val disabledHint: String? = null,
    val onCollect: (Boolean) -> Unit,
    val onWindow: (Int) -> Boolean,
)

/**
 * 「单击」那张卡 —— 判定时机 + 延迟滑杆两行。
 *
 *  **判定时机排在最前面**：它决定下面那根滑杆**哪一位算数** —— 关着看「单击延迟」、
 *  开着看「多击窗口」（判据收在 `KeyConfig.clickWindowMs`）。
 *
 *  **写路径故意少判一道 `linkReady`**（按键绑定页的格子那边是 `enabled && linkReady`）：
 *  这张卡写的是**参数**（延迟 / 判定开关），写了就落盘，注入侧恢复后照样生效，不需要注入侧回话；
 *  格子点了要写动作、拿去执行，未就绪时写了也是白写。这是有意的不一致，不要再把两边「统一」。
 *
 *  判据里**没有**「适配清单」那一道：清单只回答「这一组验过没有」，不参与任何闸
 *  （AGENTS 一、13）—— 别照着旧注释把它加回来，那会让清单外的机器整页变灰。
 */
@Composable
internal fun SettingsPage.ClickDelayCard(source: ClickParamSource) {
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW, "固定窗口起点"),
                    summaryOn =
                        I18n.get(
                            Strings.BINDING_WAIT_MULTI_WINDOW_ON,
                            "从首次按下起算一个窗口，不随后续按压延长",
                        ),
                    summaryOff =
                        I18n.get(
                            Strings.BINDING_WAIT_MULTI_WINDOW_OFF,
                            "每次松手重新起算，按压越久等待越长",
                        ),
                    icon = PageIcons.CollectWindow,
                    checked = source.collect,
                    enabled = source.enabled,
                    disabledHint = source.disabledHint,
                    onCheckedChange = source.onCollect,
                ),
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                //  标题**统一叫「等待窗口」**（两处逐字相同）：这一根在两种判定时机下
                                //  写的值不同，但问的是同一件事 —— 「按下去之后等多久」。
                                title = I18n.get(Strings.BINDING_WAIT_WINDOW, "等待窗口"),
                                icon = PageIcons.DelayTap,
                                summary = I18n.get(Strings.BINDING_WAIT_WINDOW_DESC, "多击判定所用的窗口长度"),
                                //  上下限两个判定时机**统一**（100~1000）：一根滑杆写两处，
                                //  切换时机时可用范围跟着跳一截的话，看着像值被改了。
                                min = KeyConfig.MIN_DOUBLE_CLICK_MS,
                                max = KeyConfig.MAX_MULTI_WAIT_MS,
                                value = source.windowMs,
                                default = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                            ),
                        onCommit = source.onWindow,
                    )
                },
            ),
    )
}

/**
 * **公共**那一份来源 —— 值读 `GlobalConfig`，写入落到全局配置（没单独设过的键都用它）。
 *
 *  这一份**不判「有没有多击」**：它是全局默认值，单键也要有个地方改单击延迟。
 */
internal fun SettingsPage.clickGlobalParams(enabled: Boolean): ClickParamSource {
    val g = configState.config.value.global
    val multi = g.waitMultiWindow
    //  0 = 「跟随单击延迟」（老配置长这样），滑杆要显示一个具体值
    val windowValue =
        if (multi) {
            g.multiWaitMs.takeIf { it > KeyConfig.FOLLOW_DOUBLE_CLICK_MS } ?: g.doubleClickMs
        } else {
            g.doubleClickMs
        }
    return ClickParamSource(
        collect = !multi,
        windowMs = windowValue,
        enabled = enabled,
        onCollect = { collect -> setGlobalWaitMultiWindow(!collect) },
        onWindow = { v -> setGlobalClickWindow(v) },
    )
}

/**
 * **某一颗键自己**那一份来源 —— 值读 `KeyConfig`，写入落回这颗键。
 *
 *  **多一道「有没有多击手势」的闸**：这一页管的是「这颗键的多击怎么判」，一档多击都没开时
 *  没有可判的东西。判据与判定侧**同一个**（`judgeCap > 1`）：启用双击 / 三击 / 四击 /
 *  单击后长按任意一个开着，或者场景里给某一档绑了动作，都算「有」—— 不在这里另写一遍
 *  `doubleClickOn || extraClicksOn…`：那样页面说「能调」、判定却按另一个数决定「等不等」，
 *  两处迟早对不上。
 *
 *  三道闸的提示按**更根本的那一条**给：没开独立设置 → 没开多击 → 整页那句。
 *
 * @param pageEnabled 整页闸门（调用方已经算好的，含「只参与非首键的按住组合」那一道）。
 * @param pageDisabledHint 整页给的那句原因（`null` = 用整页那句）。
 */
internal fun SettingsPage.clickKeyParams(
    keyId: KeyId,
    pageEnabled: Boolean,
    pageDisabledHint: String? = null,
): ClickParamSource {
    val key = configState.config.value.key(keyId)
    val noMulti = key.judgeCap <= 1
    //  这一颗键有没有开「使用独立设置」—— 关着时这两项跟随公共设置（那一份在公共设置页改），
    //  这一页只是把生效值摆出来、灰着。
    val own = key.useOwnSettings
    val hint =
        when {
            !own -> I18n.get(Strings.KEY_SETTINGS_NEED_OWN, "先打开 [使用独立设置]")
            pageEnabled && noMulti -> I18n.get(Strings.BINDING_NO_MULTI_GESTURE, "没有启用任何多击手势")
            else -> pageDisabledHint
        }
    //  **不是 [key.multiWaitMs] 的原值**：0 表示「跟随单击延迟」（老配置就长这样），
    //  滑杆要显示一个具体值；「用户没动过就不写」这条靠 `SliderRow` 只在拖动时回调。
    val multi = key.waitMultiWindow && !noMulti
    val windowValue =
        if (multi) {
            key.multiWaitMs.takeIf { it > KeyConfig.FOLLOW_DOUBLE_CLICK_MS } ?: key.doubleClickMs
        } else {
            key.doubleClickMs
        }
    return ClickParamSource(
        collect = !key.waitMultiWindow,
        windowMs = windowValue,
        enabled = pageEnabled && own && !noMulti,
        disabledHint = hint,
        onCollect = { collect -> setWaitMultiWindow(keyId, !collect) },
        onWindow = { v ->
            //  **写哪一位要现读一次配置**，不能用上面那个 `multi` 快照：用户刚拨完判定时机、
            //  紧接着拖这一根时，快照还是旧的 —— 按它写就会写到「当前不生效」的那一位上，
            //  表现正是「滑杆拖了、判定一点没变」（真机报过）。
            //  判据与显示那一侧同一个（`waitMultiWindow && !noMulti`）：没有多击时这一根写的就是
            //  单击延迟，写进 `multiWaitMs` 会是一条谁也读不到的账。
            val now = configState.config.value.key(keyId)
            if (now.waitMultiWindow && !noMulti) writeMultiWaitMs(keyId, v) else writeDoubleClickMs(keyId, v)
        },
    )
}

/** 把「单击延迟」写进**这一颗键**（每颗键各一份）；`false` = 没写进去。 */
internal fun SettingsPage.writeDoubleClickMs(
    keyId: KeyId,
    v: Int,
): Boolean {
    val now = configState.config.value
    return write(
        now.copy(keys = now.keys + (keyId to now.key(keyId).copy(doubleClickMs = v))),
        "double_click_ms=$v key=${keyId.id}",
    ) != CommandsSave.Failed
}

/** 把「多击窗口」写进**这一颗键**（0 = 跟随单击延迟，滑杆不会写回 0）；`false` = 没写进去。 */
internal fun SettingsPage.writeMultiWaitMs(
    keyId: KeyId,
    v: Int,
): Boolean {
    val now = configState.config.value
    return write(
        now.copy(keys = now.keys + (keyId to now.key(keyId).copy(multiWaitMs = v))),
        "multi_wait_ms=$v key=${keyId.id}",
    ) != CommandsSave.Failed
}
