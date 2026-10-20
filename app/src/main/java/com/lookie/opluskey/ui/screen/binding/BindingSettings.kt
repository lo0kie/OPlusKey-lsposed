package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.CommandsSave
import com.lookie.opluskey.ui.state.KeyWatchState
import com.lookie.opluskey.ui.write
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.cardListPositionOf

/**
 * 「按键设置」卡（**单击档**）—— 只有「单击延迟」一行。
 *
 *  它是一张「设置项列表」（滑杆那些行），属于**这一档手势本身**的行为，排在动作网格之前：
 *  先看到「这一档怎么判」，再挑「绑什么」。
 *
 *  注意：**「忽视无操作双击手势」那个开关不在这里** —— 它讲的是双击怎么判，挂在双击档
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
    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        SliderRow(
            position = cardListPositionOf(0, SINGLE_SETTING_ROWS),
            spec =
                SliderSpec(
                    title = I18n.get(Strings.SETTING_DOUBLE_CLICK_DELAY, "单击延迟"),
                    icon = PageIcons.DelayTap,
                    summary = I18n.get(Strings.BINDING_DOUBLE_CLICK_WAIT, "单击后等待双击的时长"),
                    min = KeyConfig.MIN_DOUBLE_CLICK_MS,
                    max = KeyConfig.MAX_DOUBLE_CLICK_MS,
                    value = key.doubleClickMs,
                    default = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                    enabled = rowsEnabled,
                ),
        ) { v ->
            val now = configState.config.value
            val target = KeyWatchState.editing(now)
            write(
                now.copy(keys = now.keys + (target to now.key(target).copy(doubleClickMs = v))),
                "double_click_ms=$v key=${target.id}",
            ) != CommandsSave.Failed
        }
    }
}
