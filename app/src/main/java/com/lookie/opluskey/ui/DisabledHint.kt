package com.lookie.opluskey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick

// 「提示怎么弹」与「点不动时说哪句」—— 控件层拿不到页面实例，这两条通道都从上面传。

/**
 * 弹一条操作提示 —— 由页面在根上提供（实现是 `hints.hint`，见 `ui/PageFeedback.kt`）。
 *
 *  控件层（`IconAction` 这种不带 `SettingsPage` 接收者的）也拿不到页面实例，所以「提示怎么弹」
 *  从上面传下来。**没提供时是空实现**：控件在别的上下文里照样能用，只是不出声。
 *
 *  「禁用态被点了一下」要说的话不走这一条，走 [LocalDisabledHint]。
 */
internal val LocalHint = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * 「点一下振一次」—— 由页面在根上提供（实现是 `haptics.tick`，见 `PageHaptics`）。
 *
 *  为什么要有这一条：**能点的行默认就该振**（用户口径），而这一下振动发生在
 *  `CardList` / `PickerRow` 这些**控件层**——它们不带 `SettingsPage` 接收者，拿不到页面实例。
 *  所以和 [LocalHint] 一样从上面传下来，控件只管「点的时候叫一声」。
 *
 *  没提供时是空实现：这些控件在别的上下文里照样能用，只是不振动。
 *
 *  列表里「不振动」的少数几行，用 `CardRow.Item(vibrate = false)` 显式关掉 ——
 *  默认开、个别关，比「每处都要记得振」少一种漏法。
 */
internal val LocalTick = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 「这一下为什么点不动」的投递位 —— 由页面在根上提供，**落到页面那个「禁用原因」语义位**
 * （`HINT_SLOT_DISABLED`，见 `ui/screen/PageContent.kt`）。
 *
 *  控件层只管把**一句原因**交出来（经 [DisabledHint]）；落到哪个位、要不要 `remember`、
 *  连点几下会不会叠条，全在提供方那一条上定死，业务层一个都不用碰。
 *
 *  没提供时是空实现：控件在别的上下文里照样能用，只是不出声。
 */
internal val LocalDisabledHint = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * 「这一块里点不动的东西，被点一下就说 [text] 那句。」
 *
 *  投递口（`LocalDisabledClick`）、语义位（`HINT_SLOT_DISABLED`）与 lambda 的 `remember`
 *  都在这里，调用点只给**一句话**：
 *  ```
 *  DisabledHint(I18n.get(Strings.COMMAND_STEP_SORTING_LOCKED, "编辑中，先点右下角完成")) { 整块控件 }
 *  ```
 *  以前每个调用点自己拼 `CompositionLocalProvider(LocalDisabledClick provides remember { … })`
 *  —— 三行、还要自己记住「槽位别漏、lambda 得 `remember`」，漏一处就是连点叠一串提示条
 *  或每次重组让读这条通道的控件白白重组。
 *
 *  @param text `null` = 这一块没有自己的说法（或它根本能点）⇒ 不覆盖，交给页面那条
 *   「整页为什么改不了」；非 null = 这一块自己的限制（「最多三步」「已经是第一步」），
 *   那是**另一件事**，不能拿页面那句顶替。
 */
@Composable
internal fun DisabledHint(
    text: String?,
    content: @Composable () -> Unit,
) {
    if (text == null) {
        content()
    } else {
        val say = LocalDisabledHint.current
        //  文案按**最近一次组合**那一刻取：这条原因本身就是状态派生来的，原因一变，递它的那一层
        //  本来就会重组。`remember` 住 lambda（键是 [say]）—— 每次重组换一个新实例，会让所有读
        //  这条通道的控件跟着白白重组；文案换了也不换实例，点击时才读最新的那句。
        val latest = rememberUpdatedState(text)
        val onClick: () -> Unit = remember(say) { { say(latest.value) } }
        CompositionLocalProvider(LocalDisabledClick provides onClick, content = content)
    }
}
