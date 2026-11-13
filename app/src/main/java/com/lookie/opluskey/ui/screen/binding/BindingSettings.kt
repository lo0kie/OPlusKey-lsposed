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
import com.lookie.opluskey.ui.write.setWaitMultiWindow
import com.lookie.opluskey.ui.write.write

/**
 * 「独立设置」页里的**单击**那张卡 —— 判定时机 + 延迟滑杆两行。
 *
 *  **从「按键绑定」页挪过来的**（用户口径「单击的设置都移到独立设置里」）：它改的是
 *  「这颗键怎么判单击」，与那一页上面那组「有几档」是同一件事的两半。
 *  「这一档绑什么」仍在「按键绑定」页的网格那一头。
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
    keyId: KeyId,
    enabled: Boolean,
    disabledHint: String? = null,
) {
    val cfg = configState.config.value
    // 这一份参数是**每颗键自己的**（`KeyConfig.doubleClickMs`），不再与其他键共用。
    //  **改哪一颗由调用方给**（`keyId`）：这张卡原先靠 `KeyWatchState.editing` 现取，
    //  那是「按键绑定页当前选中的那颗」—— 挪到独立设置页之后，那一份状态不再代表这一页在改谁。
    val key = cfg.key(keyId)
    //  **有没有多击手势** —— 与判定侧**同一个判据**（`judgeCap > 1`）：启用双击 / 三击 /
    //  四击 / 单击后长按任意一个开着，或者场景里给某一档绑了动作，都算「有」。
    //  不在这里另写一遍 `doubleClickOn || extraClicksOn…`：那样页面说「能调」、
    //  判定却按另一个数决定「等不等」，两处迟早对不上。
    val noMulti = key.judgeCap <= 1
    //  两道闸都关着时先给**更根本**的那句（整页 / 这一格为什么动不了），再轮到「没开多击」。
    val windowHint =
        if (enabled && noMulti) {
            I18n.get(Strings.BINDING_NO_MULTI_GESTURE, "没有启用任何多击手势")
        } else {
            disabledHint
        }
    val windowEnabled = enabled && !noMulti
    CardList(
        rows =
            buildList {
                //  **判定时机排在最前面**：它决定下面两根滑杆**哪一根算数** ——
                //  关着看「单击延迟」、开着看「多击窗口」（判据收在 `KeyConfig.clickWindowMs`）。
                //  它自己也吃「没开多击」那道闸：一个多击手势都没开时，分成开 / 关两档也无从谈起。
                add(waitMultiWindowRow(keyId, windowEnabled, windowHint))
                //  **一根滑杆管两个值**（用户口径）：拖它写的是上面那颗开关当前用的那一位，
                //  两个值分开存。两根并排时用户看到两个数、只有一个生效，还得先读开关才知道拖哪根。
                add(waitWindowRow(keyId, key, windowEnabled, windowHint))
            },
    )
}

/**
 * 「等待窗口」那一根 —— 两根值（`doubleClickMs` / `multiWaitMs`）**共用一根滑杆**，
 * 拖它写的是**上面那颗判定时机当前用的那一位**：
 * · 固定窗口起点（`waitMultiWindow = false`）⇒ 写 `doubleClickMs`；
 * · 每次松手重新起算（`true`）⇒ 写 `multiWaitMs`（0 = 跟随双击窗口，所以读的时候回落）。
 *
 *  为什么合并成一根：两根滑杆摆在一起时，用户看到两个数、只有一个生效，还得先读上面那颗开关
 *  才知道该拖哪根。一根滑杆 + 一句「判定所用的窗口长度」就没有这个歧义 ——
 *  **两个值仍然分开存**，切回另一种判定时机时各自那份还在。
 *
 *  **上下限两个时机统一**（`MIN_DOUBLE_CLICK_MS` ~ `MAX_MULTI_WAIT_MS`，后者等于
 *  `MAX_DOUBLE_CLICK_MS`）：共用一个滑杆时，范围跟着判定时机跳一截会看着像值被改了。
 */
private fun SettingsPage.waitWindowRow(
    keyId: KeyId,
    key: KeyConfig,
    enabled: Boolean,
    disabledHint: String?,
): CardRow {
    //  **不是 [key.multiWaitMs] 的原值**：0 表示「跟随单击延迟」（老配置就长这样），
    //  滑杆要显示一个具体值；「用户没动过就不写」这条靠 `SliderRow` 只在拖动时回调。
    val multi = key.waitMultiWindow
    val value = if (multi) key.multiWaitMs.takeIf { it > KeyConfig.FOLLOW_DOUBLE_CLICK_MS } ?: key.doubleClickMs else key.doubleClickMs
    return CardRow.Custom { position ->
        SliderRow(
            position = position,
            spec =
                SliderSpec(
                    title = I18n.get(Strings.BINDING_WAIT_WINDOW, "等待窗口"),
                    icon = PageIcons.DelayTap,
                    summary = I18n.get(Strings.BINDING_WAIT_WINDOW_DESC, "多击判定所用的窗口长度"),
                    //  上下限两个判定时机**统一**（100~1000）：两根值共用这一根滑杆，
                    //  切换时机时可用范围跟着跳一截的话，看着像值被改了。
                    min = KeyConfig.MIN_DOUBLE_CLICK_MS,
                    max = KeyConfig.MAX_MULTI_WAIT_MS,
                    value = value,
                    default = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                    enabled = enabled,
                    disabledHint = disabledHint,
                ),
        ) { v ->
            //  **写哪一位要现读一次配置**，不能用上面那个 `multi` 快照：用户刚拨完判定时机、
            //  紧接着拖这一根时，快照还是旧的 —— 按它写就会写到「当前不生效」的那一位上，
            //  表现正是「滑杆拖了、判定一点没变」（真机报过）。
            val now = configState.config.value.key(keyId)
            if (now.waitMultiWindow) writeMultiWaitMs(keyId, v) else writeDoubleClickMs(keyId, v)
        }
    }
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

/**
 * 「固定窗口起点」那一行 —— 判定时序（见 `KeyConfig.waitMultiWindow`）。
 *
 *  **界面上这一颗与存储位是反的**：开着 = 「收集多击」（`waitMultiWindow = false`，
 *  从首次按下只等**一个**窗口，窗口内数到的多击算一次触发）；关着 = 「逐次等」
 *  （`waitMultiWindow = true`，每按一下都重新等一个完整窗口）。
 *  存储位当初按「要不要等满窗口」命名，界面按用户要的正向说法命名 —— 取反只在写回那一步做，
 *  别在调用方再翻一次。
 */
private fun SettingsPage.waitMultiWindowRow(
    keyId: KeyId,
    enabled: Boolean,
    disabledHint: String?,
): CardRow {
    val key = configState.config.value.key(keyId)
    return CardRow.Switch(
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
        checked = !key.waitMultiWindow,
        enabled = enabled,
        disabledHint = disabledHint,
    ) { collect ->
        setWaitMultiWindow(keyId, !collect)
    }
}
