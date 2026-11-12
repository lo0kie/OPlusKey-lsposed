package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.SECTION_TITLE_PAD_TOP
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.BranchDraft

// 「松手沿用按下时的场景」—— **这一张场景表的一格开关**。
//
//  原先它摆在「场景动作」页的两档松手下面（长按一处、单击后长按一处），可它回答的从来不是
//  「这一档的松手做什么」，而是「松手那一刻还算不算按下时命中的这一块」—— 那是**整张表**的事：
//  判定侧一次只命中一块，松手是另一条手势，两块之间该怎么接，一张表一处口径。
//  摆在每一档松手里，等于让人在两处各拨一次，还容易只拨了一处。
//
//  于是提到「系统场景」页：列表下面一行，**表非空时才有**（空表没有可沿用的块）。

/**
 * 这一张表**是不是沿用按下时命中的那一块**（`SceneRule.releasePin`）。
 *
 *  这一位在模型上是**每块一份**（判定侧按块读，见 `GestureExec.consumePressPin`），界面上只给
 *  一个开关 ⇒ 取「有任一块开着就算开」：只要还有一块会沿用，就不能对这一行说「关」。
 *  拨一下是整表一起改（见 [setSceneReleasePin]），拨完就一致了。
 *
 *  空表按**开**念：那是默认值，也是「添加场景」新加那一块继承的取值（见 `addScene`）。
 */
internal fun sceneReleasePinOn(rules: List<SceneRule>): Boolean = rules.isEmpty() || rules.any { it.releasePin }

/**
 * 拨「松手沿用按下时的场景」那一行 —— 摆在场景列表**最上面**（第一块之前）。
 *
 *  只改**草稿**：这一页的规矩是「改完点确认才落盘」（见 `BranchDraft`），提前写进去等于替用户
 *  按了那颗确认。所以拨完只是 `dirty` 亮起来，与改条件 / 改动作同一个待遇。
 *
 *  不挂到 `SettingsPage` 上：它要的东西（读哪张表、能不能点）全由调用点给齐 ——
 *  页面扩展的数量有一道上限在盯着，这一层也没有「页」可言。
 *
 *  @param rules 画这一行时读的表 —— 传的是**快照**（`BranchCards` 的 `shownRules`）：
 *    删最后一块那一支还在播退场动画时读的也是它，否则开关会在那一下自己跳一格
 *  @param enabled 整表那一道闸（只读 / 总闸关着 / 链路没就绪）
 *  @param onToggle 拨下去之后做什么 —— 由调用点交给 [setSceneReleasePin]
 */
@Composable
internal fun SceneReleasePinCard(
    rules: List<SceneRule>,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    CardList(
        //  上边距取小标题那一档：这一张上面没有小标题，不让位就贴着顶栏 ——
        //  与「按键绑定」页那张「系统场景」条同一个数（那边也是 `SECTION_TITLE_PAD_TOP`）。
        modifier = Modifier.padding(top = SECTION_TITLE_PAD_TOP),
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_RELEASE_PIN_TITLE, "松手沿用按下时的场景"),
                    summaryOn = I18n.get(Strings.BINDING_RELEASE_PIN_ON, "松手时不再重新判条件"),
                    summaryOff = I18n.get(Strings.BINDING_RELEASE_PIN_OFF, "松手时按当时的状态重新判一次"),
                    icon = PageIcons.ReleasePin,
                    checked = sceneReleasePinOn(rules),
                    enabled = enabled,
                    onCheckedChange = onToggle,
                ),
            ),
    )
}

/**
 * 把这一位写进**整张表**（草稿里的每一块）。
 *
 *  为什么不只写「当前这一块」：这一页根本没有「当前是哪一块」—— 门在那张列表上，而它说的是整表。
 *  与「上移 / 下移」同一条做法：改的是草稿，落盘由这一页那颗「确认」负责。
 */
internal fun setSceneReleasePin(
    draft: BranchDraft,
    on: Boolean,
) {
    //  按下标走、边改边看：`update` 是「同一块换内容」，不换行身份，行不会重播一次入场。
    draft.scenes.indices.forEach { index ->
        val rule = draft.scenes[index]
        if (rule.releasePin != on) draft.update(index, rule.copy(releasePin = on))
    }
    XLog.w("场景表：松手沿用按下时的场景 = $on（${draft.scenes.size} 块一起改）")
}
