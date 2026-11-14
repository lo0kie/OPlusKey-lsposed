package com.lookie.opluskey.ui.feedback

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.row.RowEndIcon
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.LocalDisabledClick
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「提示怎么弹」与「点不动时说哪句」—— 控件层拿不到页面实例，这两条通道都从上面传。

/**
 * 「点一下振一次」—— 由页面在根上提供（实现是 `haptics.tick`，见 `PageHaptics`）。
 *
 *  为什么要有这一条：**能点的行默认就该振**（用户口径），而这一下振动发生在
 *  `CardList` / `PickerRow` 这些**控件层**——它们不带 `SettingsPage` 接收者，拿不到页面实例。
 *  所以和 [LocalDisabledHint] 一样从上面传下来，控件只管「点的时候叫一声」。
 *
 *  没提供时是空实现：这些控件在别的上下文里照样能用，只是不振动。
 *
 *  列表里「不振动」的少数几行，用 `CardRow.Item(vibrate = false)` 显式关掉 ——
 *  默认开、个别关，比「每处都要记得振」少一种漏法。
 */
internal val LocalTick = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 一句话 + 要画成**标签**的那几个名字 —— 与 `SettingsPage.hint(text, chips)` 同一份口径。
 *
 *  为什么要有这个载体：文案模板（`%s`）与名字必须**一起**走完「控件层 → 页面」那一段 ——
 *  只传模板会在界面上留下裸 `%s`，只传名字则丢掉了语序（各语言的语序不同）。
 *
 *  只在这一层用：那是「点不动时说的那句话」的两个半边，和 `HintItem` 的 `text` + `chips`
 *  是同一种东西，区别只在它还没有落到队列里。
 */
internal class HintText(
    val template: String,
    val chips: List<String> = emptyList(),
)

/**
 * 「这一下为什么点不动」的投递位 —— 由页面在根上提供，**落到页面那个「禁用原因」语义位**
 * （`HINT_SLOT_DISABLED`，见 `ui/screen/PageContent.kt`）。
 *
 *  控件层只管把**一句原因**交出来（经 [DisabledHint]）；落到哪个位、要不要 `remember`、
 *  连点几下会不会叠条，全在提供方那一条上定死，业务层一个都不用碰。
 *
 *  没提供时是空实现：控件在别的上下文里照样能用，只是不出声。
 */
internal val LocalDisabledHint = staticCompositionLocalOf<(HintText) -> Unit> { {} }

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
) = DisabledHint(text?.let { HintText(it) }, content)

/**
 * 同上，但带上**要画成标签的名字**（`%s` 在 [HintText.template] 里）。
 *
 *  带键名的那几条（「按键 %s 只参与组合按键」「%s 已停用…」）走这一支：名字画成标签，
 *  与提示条里其它回执是同一种长相。纯文字那十几处继续用上面那条重载，一行都不用改。
 */
@Composable
internal fun DisabledHint(
    text: HintText?,
    content: @Composable () -> Unit,
) {
    //  **空串与 `null` 同档**：这一条通道只负责「这一块有自己的说法」；给一句空话等于让这次点击
    //  什么都不说，而屏幕上还留着上一条提示 —— 用户看到的就是「回退到上一个消息文案」。
    if (text == null || text.template.isBlank()) {
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

/**
 * 行尾那半句状态 —— **场景列表与按键列表共用这一份**。
 *
 *  「这一项现在不生效」在两处是同一件事，字号 / 颜色 / 与右侧那颗图标的间距必须一样；
 *  各写一遍的结果是两页的同一句话看起来不是一回事。`RuleStateHint` 里 OFF 那一档也走它。
 *
 *  @param text 那一句话本身 —— 默认「已停用」；按键列表会传「只参与组合」
 *    （见 [keyStateLabel]：两者占同一格、三态互斥）
 */
@Composable
internal fun DisabledStateText(text: String = I18n.get(Strings.COMMAND_BRANCH_DISABLED, "已停用")) {
    Text(
        text,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantActions,
        modifier = Modifier.padding(end = 6.dp),
    )
}

/**
 * 按键列表行尾该写的那半句话 —— `null` = 什么都不写。
 *
 * ## 三态互斥（用户口径「和停用 / 启用三选一，启用不用显示」）
 *
 *  · 停着没 → 「已停用」；
 *  · 「只参与组合按键」→ 「只参与组合」；
 *  · 正常启用 → `null`，这一格整块不占位（行尾回到箭头那条默认路径）。
 *
 *  这两件事**不是一回事**：停用是「这颗键不接管任何按键」，只参与组合是「单键手势交还系统、
 *  模块只用它凑组合」—— 后者照样在干活，只是干的不是单键那一份。所以画在**同一格**里
 *  而不是叠两条：这一格本来就只放一句状态，两条一起写会把标题挤掉一截。
 *
 *  判据放在这里而不是列表那一层：按键列表有两处（内置侧键 / 自定义键）各建一次行，
 *  各写一遍 `when` 的话，加第三种状态时一定会漏一处 —— 真机上就是一行写「已停用」、
 *  另一行什么都不写。组合键列表也调它（`comboOnly` 恒传 `false`：那一位只属于单键）。
 *
 * @param enabled 这一项自己开着没有
 * @param comboOnly 是不是「只参与组合按键」（见 `KeyConfig.comboOnly`）
 */
internal fun keyStateLabel(
    enabled: Boolean,
    comboOnly: Boolean,
): String? =
    when {
        !enabled -> I18n.get(Strings.COMMAND_BRANCH_DISABLED, "已停用")
        comboOnly -> I18n.get(Strings.BINDING_COMBO_ONLY_BADGE, "只参与组合")
        else -> null
    }

/**
 * 行尾那一格：**「已停用」+ 箭头** —— 场景卡、按键列表、组合按键三处共用。
 *
 *  为什么要收成一份：`CardRow.Item` 的行尾**只有一格**（`trailing` 非空时 `arrow` 就不画），
 *  所以「状态 + 箭头」必须自己拼一个 `Row` 塞进 `trailing`。这条规矩三处各写一遍的话，
 *  迟早有一处忘了画箭头（真机上就是这样：填了 `trailing`，箭头整列消失）。
 *
 *  @param text 那半句话 —— 默认「已停用」；按键列表会传 [keyStateLabel] 的结论
 *    （「只参与组合」也走这一格：两者是同一件事的两种状态，不该长得像两个控件）
 *  @see DisabledStateText 只画那半句话（编辑态里它旁边跟的是勾选框，不是箭头）
 */
@Composable
internal fun DisabledStateWithArrow(text: String = I18n.get(Strings.COMMAND_BRANCH_DISABLED, "已停用")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DisabledStateText(text)
        RowEndIcon()
    }
}

/**
 * 行尾那一格：**一句状态 + 勾选框** —— 按键列表（内置侧键 / 自定义键）与组合按键三处共用。
 *
 *  与 [DisabledStateWithArrow] 是同一个位置的两种形态：编辑态里勾选框代替箭头。三处各写一遍
 *  的结果与那条箭头的老毛病一样 —— 迟早有一处忘了先写那句状态，编辑态里就看不出这一项是怎么了。
 *
 *  @param picked 这一行是否被勾中（编辑态）
 *  @param enabled 勾选框本身是否可按 —— 整页闸门关着时按下无意义
 *  @param state 旁边那句状态（见 [keyStateLabel]）；`null` = 只画勾选框
 */
@Composable
internal fun DisabledStateWithPick(
    picked: Boolean,
    enabled: Boolean,
    state: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state != null) DisabledStateText(state)
        Checkbox(
            state = if (picked) ToggleableState.On else ToggleableState.Off,
            onClick = null,
            enabled = enabled,
        )
    }
}
