package com.lookie.opluskey.ui.screen.binding

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.instantSingleTitle
import com.lookie.opluskey.ui.setInstantSingle
import com.lookie.opluskey.ui.state.KeyWatchState

/**
 * 「忽视无操作多击手势」那一行 —— 挂在**单击**那张卡里（`BindingSettingsCard`）。
 *
 * ## 为什么在单击那一页
 *
 *  它决定的是「第一下抬手之后怎么办」：后面那些档（双击、三击 / 四击、单击后长按）**一档都没绑**
 *  时，是照旧等一个窗口（等不到就白等），还是当场把这一下算成单击。用户改这一位时想要的正是
 *  **单击更快**，所以它与「单击延迟」同一张卡 —— 两者是同一次判定链上的两个参数。
 *
 *  标题写「多击」而不是「双击」：三击 / 四击也是同一个判据（只看「后面还有没有人要」），
 *  只写双击会让人以为那几档不归它管。
 *
 * ## 与「单击延迟」的关系
 *
 *  开着它、且后面那些档确实都没绑动作时，那个延迟窗口就用不上了（判定侧抬手即判单击）。
 *  但那是**运行期的组合**，界面**不**替用户把「单击延迟」那一行锁死：两个开关状态独立，
 *  用户随时可能把某一档再绑回某个动作，那时延迟立刻又生效。
 *
 * ## 写路径
 *
 *  与单击那张卡同一条纪律：**不判 `linkReady`**（写的是参数不是动作，落盘就生效，
 *  不需要注入侧回话）。理由写全在 `BindingSettingsCard` 的类注释里。
 *  「适配清单」不参与任何闸，这里当然也不判它（AGENTS 一、13）。
 */
internal fun SettingsPage.instantSingleRow(
    enabled: Boolean,
    disabledHint: String? = null,
): CardRow {
    val cfg = configState.config.value
    // 这一位也是**每颗键自己的**（`KeyConfig.instantSingle`）
    val id = KeyWatchState.editing(cfg)
    val key = cfg.key(id)
    //  配了三击 / 四击、或「单击后长按**真有动作**」时，正常那句（「每一下各算一次单击」）不成立：
    //  判定侧要数到最高档 / 等第 2 击按住才收线，第一下不会当场算单击。说法换成一句实情，
    //  开关本身**不动**（值留着 —— 把那几档解绑之后它立刻就生效，与「运行期的组合不替用户
    //  锁死」同一条口径，见上面的类注释）。
    //  **动作是无操作的那一档不算「后续手势」**：它不拦单击收线（见 `KeyConfig.clickThenLongCounts`），
    //  这时「每一下各算一次单击」正是它此刻的真实表现。
    val higherBound = key.clicksForJudge > 2 || key.clickThenLongCounts
    val title = instantSingleTitle(key)
    //  说明只写**结论**（开 / 关之后那几下变成什么样），不复述「后面那一档无动作时」那个前提 ——
    //  标题里的「无操作」已经说了这件事。
    return CardRow.Switch(
        title = title,
        summaryOn =
            if (higherBound) {
                I18n.get(Strings.BINDING_HIGHER_CLICKS_NOTE, "配了后续手势时，单击最后执行")
            } else {
                I18n.get(Strings.BINDING_INSTANT_SINGLE_ON, "每一下各算一次单击")
            },
        summaryOff =
            if (higherBound) {
                I18n.get(Strings.BINDING_HIGHER_CLICKS_NOTE, "配了后续手势时，单击最后执行")
            } else {
                I18n.get(Strings.BINDING_INSTANT_SINGLE_OFF, "后续连击不生效")
            },
        icon = PageIcons.IgnoreDouble,
        checked = key.instantSingle,
        enabled = enabled,
        disabledHint = disabledHint,
    ) {
        setInstantSingle(it)
    }
}
