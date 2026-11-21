package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.OwnField
import com.lookie.opluskey.core.model.OwnSettingsValues
import com.lookie.opluskey.core.model.values
import com.lookie.opluskey.core.model.withField
import com.lookie.opluskey.core.model.withOwn
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.feedback.HINT_SLOT_OWN_SETTINGS
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.feedback.switchToNotice
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.kit.SectionTitle
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.EditGate
import com.lookie.opluskey.ui.row.OwnCardTitles
import com.lookie.opluskey.ui.row.OwnSettingsCard
import com.lookie.opluskey.ui.row.noticeModeLabel
import com.lookie.opluskey.ui.screen.combo.HoldComboCard
import com.lookie.opluskey.ui.screen.combo.WaitComboCard
import com.lookie.opluskey.ui.screen.combo.comboOwnParams
import com.lookie.opluskey.ui.screen.combo.holdCombosStartedBy
import com.lookie.opluskey.ui.screen.combo.updateCombo
import com.lookie.opluskey.ui.screen.combo.waitCombosUsing
import com.lookie.opluskey.ui.screen.settingsTargetLabel
import com.lookie.opluskey.ui.state.page.SettingsTarget
import com.lookie.opluskey.ui.state.page.SettingsTargetState
import com.lookie.opluskey.ui.write.updateKey
import com.lookie.opluskey.ui.write.write
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

// 「独立设置」页 —— 一颗键 / 一组组合对公共项的覆盖，从它们各自页面顶栏那颗设置键进来。
//
//  这里只有一件事：公共设置页里那几项「按键怎么工作」的开关，这一档可以自己另设一份
//  （走不走 ColorOS 接口 / 振不振、振多重 / 弹不弹提示、提示写什么）。顶上那颗总开关默认关，
//  关着就跟随公共设置。
//
//  **单键与组合走的是同一个页面、同一张卡**：页面只认 `SettingsTarget`（在改谁），
//  写回那一支里那一个 `when` 是配置结构上的差别（keys / combos 两张表），不是功能分叉。

/**
 * 「独立设置」页。
 *
 * @param pageEnabled 整页闸门（主开关 + 链路 + 配置可写）—— 关着时所有行不吃点击
 */
@Composable
internal fun SettingsPage.KeySettingsScreen(
    scrollBehavior: ScrollBehavior,
    pageEnabled: Boolean,
) {
    val cfg = configState.config.value
    val target = SettingsTargetState.current
    //  那一档不见了：别摆一个只剩顶栏的空壳页，直接退回上一页（与「选界面」页同一套处理）。
    //  **只有组合那一支会走到这里**：`SettingsTarget.Key.ownerIn` 是 `Config.key(id)`，
    //  而它有 `defaultFor` 兜底、永远返回得出一份，所以单键这一页不会空。
    val owner = target.ownerIn(cfg)
    if (owner == null) {
        LaunchedEffect(Unit) {
            XLog.w("独立设置页：目标那一档已不存在（$target），退回上一页")
            if (navStack.lastOrNull() == Screen.KEY_SETTINGS) goBack()
        }
        return
    }
    PageScrollColumn(scrollBehavior = scrollBehavior) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）—— 这一页顶上那张卡自己带一档上边距
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        //  「只参与非首键的按住组合」开着时，这一页**只剩那一行能动**。
        //  这一位说的是「这颗键不判单键手势」—— 那么多击、接口、振动、提示就都没有执行的机会，
        //  亮着就是在骗人「还能调」。留那一行是因为**关回来**得走它（关不掉就成了死路）。
        //  判据与绑定页那一族同一份（`bindingTargetStopped`），两处各判一遍迟早漂。
        //  只有单键有这一位：组合目标（`SettingsTarget.Combo`）不进这一支。
        //
        //  **这一页那几道闸一次算清**（见 `EditGate`）：整页闸门 / 这颗键还判不判单键手势 /
        //  这一档用不用自己的独立设置。下面每一行只挑自己那一档，不再各自拼条件。
        val gate =
            EditGate(
                page = pageEnabled,
                keyGestures = !(target is SettingsTarget.Key && cfg.key(target.id).comboOnly),
                ownOn = owner.useOwnSettings,
            )

        //  —— 多击那两颗开关（只有单键有）——
        //  **放在最顶部**：它们决定这一页下面那个「总开关」能管到几档 —— 多击是这一档存不存在，
        //  不是「怎么工作」。先看见「有几档」，再看见「这几档怎么工作」才顺。
        //  它们**不是** `OwnSettings` 那一族里的一项：下面那张卡管「接口 / 振动 / 提示」，
        //  这里管「有没有这一档」，所以也**不吃**那颗总开关（见 `KeyConfig.extraClicksOn`）。
        //
        //  小标题与 `OwnSettingsCard` 里那三个同构（那一支也是「一个标题带一张卡」）。
        //  **卡片不再自己让顶边距**：那个 `SECTION_TITLE_PAD_TOP` 是「上面没有标题」时的替代品，
        //  加了标题之后它自带同一档上间距，两处都给就是双份空隙。
        if (target is SettingsTarget.Key) {
            SectionTitle(I18n.get(Strings.KEY_SETTINGS_MULTI_CLICK))
            ExtraClicksCard(
                target = target,
                cfg = cfg,
                gate = gate,
            )
        }

        //  —— 成员连击开关（单成员按住组合有）——
        //  按住首键、那颗唯一成员键连击的几档开关，与上面单键那一族同名同义，主语换成这一档组合。
        //  多成员组合 / 等待组合没有这几档，不进这一支。
        val memberCombo = (target as? SettingsTarget.Combo)?.let { cfg.combos.firstOrNull { c -> c.id == it.id } }
        if (memberCombo?.memberGestures != null) {
            SectionTitle(I18n.get(Strings.KEY_SETTINGS_MULTI_CLICK))
            MemberClicksCard(
                combo = memberCombo,
                enabled = gate.page,
            )
            //  成员「后长按」三档**共用**的那一个「连击后长按延迟」**不在这张卡里**：它是
            //  下面「按住组合」那张卡的末行（与公共设置页同一份，见 `ComboParamSource.memberDelay`）——
            //  它问的是「按住多久算长按」，与这一张卡「有哪几档」不是同一件事。
        }

        //  一个小标题带一张卡（与「按键设置」页同构）—— 页面顶部不再另放一个与顶栏同名的
        //  「独立设置」，那三个小标题由卡片自己画（见 `OwnSettingsCard`）。
        //  **只交这一档自己那份**：卡片里每一行都从它取数（总开关只管「能不能改」），
        //  所以拨那颗总开关时页面上的数一个都不跳（口径见 `OwnSettingsCard` 的
        //  「显示的是这一档自己那份」）。
        OwnSettingsCard(
            owner = owner,
            //  「只参与非首键的按住组合」时整张卡灰掉：接口 / 振动 / 提示都是**单键按下去那一下**的行为，
            //  这颗键不判单键手势，它们一次也用不上。灰着点一下落到页面那句原因上
            //  （`disabledClickReason` 的 comboOnly 那一支），卡片里不必再写一句。
            gate = gate,
            titles = ownCardTitles(),
        ) { values, field -> commitOwnField(target, values, field) }

        //  —— 单键（判定时机 + 连击等待窗口 + 单击和多击和长按延迟）——
        //  **排在总开关下面**（用户口径）：这三行也吃那颗「使用独立设置」——
        //  关着时跟随公共设置里那一份、这一页只把生效值摆出来并灰着。
        //  前两行多一道「有没有多击手势」，第三行多一道「三档后长按开没开」（各在自己的来源里判，
        //  见 `clickKeyParams`）—— 这是两件事，不共用一道闸。
        if (target is SettingsTarget.Key) {
            SectionTitle(I18n.get(Strings.KEY_SETTINGS_CLICK))
            ClickDelayCard(clickKeyParams(target.id, gate.keyRows))
        }

        //  —— 多击手势那两颗开关 ——
        //  **挪到页顶去了**（见 `KeySettingsScreen` 开头）：它们决定下面那张卡能管到几档 ——
        //  多击是「有没有这一档」，不是「这一档怎么工作」。留这一句只为说明它们为什么不在这张卡里。

        //  —— 组合参数（两张卡，与公共设置页同一份）——
        //  它们**不在**上面那张卡里：那张卡装的是「那几项公共项的覆盖」，单键组合都有；
        //  这几项只对组合成立（「两颗键等多久」「按住下限」单键没有对应物）。
        //  小标题由下面那个函数按触发方式给，与公共页逐字相同。
        //  条件只有一个：这一趟改的是一组组合 —— 这是数据差异，不是流程分叉。
        if (target is SettingsTarget.Combo) {
            ComboParamCards(
                target = target,
                cfg = cfg,
                gate = gate,
            )
        }
    }

    //  「只参与非首键的按住组合」被拦下时的说明窗 —— **必须挂在这一页**：开关就在上面那张卡里，
    //  没有这一行就没人画那张窗，用户只会看到开关自己弹回来（`ComboOnlyBlocked`）。
    ComboOnlyBlockedDialog()
}

/**
 * 「启用三击 / 启用四击」那两行 —— 只在改的是**一颗键**时出现（组合没有第三击起的那几档）。
 *
 *  它与下面那张卡的差别只有一处：**不吃 `useOwnSettings`**。那一族是「公共设置这一项可以另设
 *  一份」，公共设置里根本没有「多击」这一项，跟随无从谈起 —— 这一位只属于这一颗键。
 *  `enabled` 拿到的是整页闸门，不是 `rowsEnabled`。
 *
 *  **逐档一个开关**而不是一位统领：三击与四击的代价不一样（开三击让单击多等一个窗，
 *  开四击还要让三击也等）。只要三击的人不该被迫接受四击那一份延迟。
 *  两档互不依赖：只开四击是合法配置（第 3 下不算数、第 4 下照样派发）。
 *
 *  写回走 `writeAndNotice`（与其它开关同一套回执，位用 [HINT_SLOT_OWN_SETTINGS]：
 *  它就在这一页上，与下面那颗是同一片区域的两行）。
 */
@Composable
private fun SettingsPage.ExtraClicksCard(
    target: SettingsTarget.Key,
    cfg: Config,
    gate: EditGate,
) {
    val key = cfg.key(target.id)
    CardList(
        rows =
            listOf(
                //  「只参与非首键的按住组合」排在这一族的最前面：它决定这颗键**还算不算一颗单键** ——
                //  开着时下面那几档配了也没处执行（绑定页整页灰，见 `bindingTargetEnabled`）。
                comboOnlyRow(target, key, gate.page),
                //  双击排在三击上面：档位由少到多，与绑定页的页序（单击 / 双击 / 长按 / 三击）同一方向。
                //  它是**基础档**（默认开），与三击 / 四击那种「要自己要」的档位只是同一张卡里的邻居。
                doubleClickOnRow(target, key, gate.keyRows),
                extraClickRow(target, key, COUNT3, gate.keyRows),
                extraClickRow(target, key, COUNT4, gate.keyRows),
                thenLongRow(target, key, 1, gate.keyRows),
                thenLongRow(target, key, 2, gate.keyRows),
                thenLongRow(target, key, 3, gate.keyRows),
            ),
    )
    //  三档「后长按」共用的那个按住时长**不在这一张卡里**：它是「单键」那张卡的第三行
    //  （`ClickDelayCard`，与公共设置页同一份）。留这一句只为说明它为什么不在这儿 ——
    //  它说的是「按下去之后多久算按住」，与这一张卡「有哪几档」不是同一件事。
}

/**
 * **成员连击**开关卡 —— 单成员按住组合才有（`ComboKey.memberGestures` 非空）。
 *
 *  按住首键、那颗唯一成员键连击的几档开关，与 [ExtraClicksCard] 那一族同名同义，主语换成
 *  这一档组合。写回落进 `ComboKey` 的 `memberDoubleOn` / `memberClicksOn` /
 *  `memberClickThenLongOn`（走 [updateCombo]，只动这一位）。
 *
 *  与单键那张一样**不吃**「使用独立设置」总开关：公共设置里没有「成员连击」这一项。
 */
@Composable
private fun SettingsPage.MemberClicksCard(
    combo: ComboKey,
    enabled: Boolean,
) {
    CardList(
        rows =
            listOf(
                memberDoubleOnRow(combo, enabled),
                memberClickRow(combo, COUNT3, enabled),
                memberClickRow(combo, COUNT4, enabled),
                memberThenLongRow(combo, 1, enabled),
                memberThenLongRow(combo, 2, enabled),
                memberThenLongRow(combo, 3, enabled),
            ),
    )
}

/** 成员双击那一行。 */
@Composable
private fun SettingsPage.memberDoubleOnRow(
    combo: ComboKey,
    enabled: Boolean,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.SETTING_CLICK2),
        icon = PageIcons.gestureIcon(GestureTrigger.Click(2)),
        checked = combo.memberDoubleOn,
        enabled = enabled,
    ) { on ->
        updateCombo(combo.id, "member_double=${if (on) 1 else 0}") { it.copy(memberDoubleOn = on) }
    }

/** 成员三击 / 四击那一行 —— 与 [extraClickRow] 同族，写回落进 `memberClicksOn`。 */
@Composable
private fun SettingsPage.memberClickRow(
    combo: ComboKey,
    count: Int,
    enabled: Boolean,
): CardRow {
    val on = count in combo.memberClicksOn
    return CardRow.Switch(
        title =
            when (count) {
                COUNT3 -> I18n.get(Strings.SETTING_CLICK3)
                else -> I18n.get(Strings.SETTING_CLICK4)
            },
        icon = PageIcons.gestureIcon(GestureTrigger.Click(count)),
        checked = on,
        enabled = enabled,
    ) { checked ->
        val clicksOn = if (checked) combo.memberClicksOn + count else combo.memberClicksOn - count
        updateCombo(combo.id, "member_click$count=${if (checked) 1 else 0} 全档=${clicksOn.sorted()}") {
            it.copy(memberClicksOn = clicksOn)
        }
    }
}

/**
 * 成员「后长按」那三行（单击后长按 / 双击后长按 / 三击后长按）—— 与单键那一族同一套口径，
 *  [count] 是前面那几击的击数（1/2/3），文案与字段按它取。
 */
@Composable
private fun SettingsPage.memberThenLongRow(
    combo: ComboKey,
    count: Int,
    enabled: Boolean,
): CardRow {
    val title =
        when (count) {
            1 -> Strings.SETTING_CLICK_THEN_LONG
            2 -> Strings.KEY_SETTINGS_DOUBLE_THEN_LONG
            else -> Strings.KEY_SETTINGS_TRIPLE_THEN_LONG
        }
    return CardRow.Switch(
        title = I18n.get(title),
        icon = PageIcons.gestureIcon(GestureTrigger.ClickThenLong(count)),
        checked =
            when (count) {
                1 -> combo.memberClickThenLongOn
                2 -> combo.memberDoubleThenLongOn
                else -> combo.memberTripleThenLongOn
            },
        enabled = enabled,
    ) { on ->
        updateCombo(combo.id, "member_then_long$count=${if (on) 1 else 0}") {
            when (count) {
                1 -> it.copy(memberClickThenLongOn = on)
                2 -> it.copy(memberDoubleThenLongOn = on)
                else -> it.copy(memberTripleThenLongOn = on)
            }
        }
    }
}

/**
 * 「只参与非首键的按住组合」那一行 —— 这颗键不判单键手势、只等组合成员（见 `KeyConfig.comboOnly`）。
 *
 *  与上面那张卡同族：**不吃 `useOwnSettings`**（公共设置里没有这一项，跟随无从谈起），
 *  只吃整页闸门。
 *
 *  开着时**绑定页整页灰掉**（`bindingTargetEnabled`）：这颗键不判单键手势，配一档动作没处执行。
 *  但**这一页照旧能改** —— 否则用户想关回来都点不动；页面上那句原因由
 *  `disabledClickReason` 给（「只参与非首键的按住组合」那一支）。
 *
 *  ## 打开这一项要拦一道
 *
 *  这颗键如果已经是某档**按住组合的起点**（`Config.holdCombosStartedBy`），这一项就开不了：
 *  按住那一档要求起点键「按住到点」再松手，而只参与非首键的按住组合的键根本不判单键手势 —— 两个要求
 *  互斥，落下去就是一份自相矛盾的配置（与 `ComboActions.addCombo` 里「起点键只参与非首键的按住组合」
 *  那一道拦截是同一条账，方向相反）。
 *
 *  拦法是**弹窗说明**而不是悄悄不生效：这一项点下去会变、配置却不变的话，用户看到的是一颗
 *  自己弹回来的开关。弹窗里也给出路（把那档组合改成等待组合，或者删掉它）。
 */
@Composable
private fun SettingsPage.comboOnlyRow(
    target: SettingsTarget.Key,
    key: KeyConfig,
    enabled: Boolean,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.BINDING_COMBO_ONLY),
        icon = PageIcons.ComboOnly,
        checked = key.comboOnly,
        enabled = enabled,
    ) { on ->
        //  与上面几行同一条：现读一次再改这一格，只动这一位
        val cfgNow = configState.config.value
        //  只有在**打开**时才查：关掉这一项永远是安全的（矛盾那一端正是「开着」）
        val starters = if (on) cfgNow.holdCombosStartedBy(target.id) else emptyList()
        //  另一头：这颗键已经是某档**等待组合**的成员 —— 那一档按下要吞一个窗口等第二颗，
        //  与「不判单键手势」叠在一起就是「单击没了、原生还晚一个窗口」（见 `waitCombosUsing`）
        val waits = if (on) cfgNow.waitCombosUsing(target.id) else emptyList()
        when {
            starters.isNotEmpty() -> {
                //  可能不止一档（同一颗键可以是好几个按住组合的起点），日志把挡着的都列出来
                XLog.w(
                    "只参与非首键的按住组合：${target.id.label} 是 ${starters.size} 档按住组合的起点" +
                        "（${starters.joinToString { it.keys.joinToString("+") }}），拒绝开启",
                )
                ComboOnlyBlocked.ask(target.id, ComboOnlyBlocked.Reason.HOLD_STARTER, starters)
            }

            waits.isNotEmpty() -> {
                XLog.w(
                    "只参与非首键的按住组合：${target.id.label} 是 ${waits.size} 档等待组合的成员" +
                        "（${waits.joinToString { it.keys.joinToString("+") }}），拒绝开启",
                )
                ComboOnlyBlocked.ask(target.id, ComboOnlyBlocked.Reason.WAIT_MEMBER, waits)
            }

            else -> {
                updateKey(
                    target.id,
                    "combo_only=${if (on) 1 else 0}",
                    notice = { switchNotice(I18n.get(Strings.BINDING_COMBO_ONLY), on) },
                ) { it.copy(comboOnly = on) }
            }
        }
    }

/**
 * 「后长按」那三颗开关（单击后长按 / 双击后长按 / 三击后长按）—— 与三击 / 四击并列，但它们是
 *  **另一条轴**而不是击数集合里的一员：描述的是「再下一击按住不放」这另一种形态。
 *
 *  **它会把判定上限抬到那一击**（第 2 / 3 / 4 击，见 `KeyConfig.judgeCap` 的 `thenLongClicks` 那一项）：
 *  要判出「第 N 击按住」，判定器就必须数到第 N 击 —— 只开「三击后长按」时，单击也要多等两个窗
 *  才收线。这一条原先在这里写成「不多等」，与判定器的实际口径相反。
 *
 *  [count] 是前面那几击的击数（1/2/3），文案与字段按它取 —— 三档是同一件事的不同击数。
 */
@Composable
private fun SettingsPage.thenLongRow(
    target: SettingsTarget.Key,
    key: KeyConfig,
    count: Int,
    enabled: Boolean,
): CardRow {
    val title =
        when (count) {
            1 -> Strings.SETTING_CLICK_THEN_LONG
            2 -> Strings.KEY_SETTINGS_DOUBLE_THEN_LONG
            else -> Strings.KEY_SETTINGS_TRIPLE_THEN_LONG
        }
    return CardRow.Switch(
        title = I18n.get(title),
        icon = PageIcons.gestureIcon(GestureTrigger.ClickThenLong(count)),
        checked =
            when (count) {
                1 -> key.clickThenLongOn
                2 -> key.doubleThenLongOn
                else -> key.tripleThenLongOn
            },
        enabled = enabled,
    ) { on ->
        updateKey(
            target.id,
            "then_long$count=${if (on) 1 else 0}",
            notice = { switchNotice(I18n.get(title), on) },
        ) {
            when (count) {
                1 -> it.copy(clickThenLongOn = on)
                2 -> it.copy(doubleThenLongOn = on)
                else -> it.copy(tripleThenLongOn = on)
            }
        }
    }
}

/**
 * 「启用双击」那一行 —— 双击这一档存不存在（见 `KeyConfig.doubleClickOn`）。
 *
 *  与下面三击 / 四击那两颗同一族（**不吃** `useOwnSettings`：公共设置里没有「多击」这一项），
 *  只是它默认**开着** —— 双击是基础档，不像三击 / 四击那样会让单击白等窗口。
 *
 *  关掉之后：绑定页不再排双击那一页（`GestureTrigger.slotsOn` 按这一位过滤），判定侧最高只数到
 *  一击，第二下按下就是新的一轮。表里配过的动作留着，再打开原样回来。
 */
@Composable
private fun SettingsPage.doubleClickOnRow(
    target: SettingsTarget.Key,
    key: KeyConfig,
    enabled: Boolean,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.SETTING_CLICK2),
        //  字形与三击 / 四击同一个来源（`gestureIcon`）：这两行说的就是「要不要多出这一档」。
        icon = PageIcons.gestureIcon(GestureTrigger.Click(2)),
        checked = key.doubleClickOn,
        enabled = enabled,
    ) { on ->
        updateKey(
            target.id,
            "double_click=${if (on) 1 else 0}",
            notice = { switchNotice(I18n.get(Strings.SETTING_CLICK2), on) },
        ) { it.copy(doubleClickOn = on) }
    }

/**
 * 三击 / 四击那颗开关 —— 两行只有「击数」不同，收成一支免得两处各写一遍写回逻辑。
 *
 * @param count 第几击（3 / 4，见 [KeyConfig.DEFAULT_EXTRA_CLICKS]）
 */
@Composable
private fun SettingsPage.extraClickRow(
    target: SettingsTarget.Key,
    key: KeyConfig,
    count: Int,
    enabled: Boolean,
): CardRow {
    val on = count in key.extraClicksOn
    return CardRow.Switch(
        title =
            when (count) {
                COUNT3 -> I18n.get(Strings.SETTING_CLICK3)
                else -> I18n.get(Strings.SETTING_CLICK4)
            },
        //  字形直接用那一档自己的（`gestureIcon` 里第 3 击起与双击共用两根手指）：
        //  这两行说的就是「要不要多出这一档」，不必新起只此一处的图标。
        icon = PageIcons.gestureIcon(GestureTrigger.Click(count)),
        checked = on,
        enabled = enabled,
    ) { checked ->
        val title =
            if (count == COUNT3) {
                I18n.get(Strings.SETTING_CLICK3)
            } else {
                I18n.get(Strings.SETTING_CLICK4)
            }
        //  只动这一档，另一档原样留着（两行各管各的）。
        //  表（`extraClicks`）不动：关掉只是「不显示、不判定、不执行」，再打开原样回来 ——
        //  清表等于把用户配过的动作悄悄扔掉。
        updateKey(
            target.id,
            "click$count=${if (checked) 1 else 0}",
            notice = { switchNotice(title, checked) },
        ) { key ->
            key.copy(
                extraClicksOn =
                    if (checked) key.extraClicksOn + count else key.extraClicksOn - count,
            )
        }
    }
}

/** 这两颗开关各管哪一档 —— 与 [KeyConfig.DEFAULT_EXTRA_CLICKS] 同一份清单（3 击 / 4 击）。 */
private val COUNT3: Int = KeyConfig.DEFAULT_EXTRA_CLICKS.first()

private val COUNT4: Int = KeyConfig.DEFAULT_EXTRA_CLICKS.last()

/**
 * 「组合参数」那两张卡 —— 只在改的是**一组组合**时出现。
 *
 *  **卡片本身与公共设置页是同一份**（`:combo` 包的 `WaitComboCard` / `HoldComboCard`），
 *  连小标题也逐字相同；差别只有一个**状态源**：值读这一组的记录、写入落回这一组
 *  （`comboOwnParams`）。所以这里不再逐行写文案 / 图标 / 区间，改一处两边一起变。
 *
 *  摆法也一样：按触发方式分两节两张卡。某一组只认一种触发方式（`ComboKey.mode` 是它的身份），
 *  所以只摆出它自己那一种 —— 另一张全是它用不上的参数。
 *
 *  这一档**怎么按出来**（等窗口 / 先按住第一颗 / 先按住第二颗）不在这里：入口在「组合键」
 *  编辑页那颗 ⋮ 的「触发方式」一行。
 *
 *  这一族都吃上面那颗总开关：关着时它们是**公共设置**那一份，改也改不动 —— 不用再给其中某一行
 *  单独加一个「要不要自己设」的开关，那个总开关就是唯一那道闸。
 */
@Composable
private fun SettingsPage.ComboParamCards(
    target: SettingsTarget.Combo,
    cfg: Config,
    gate: EditGate,
) {
    val combo = cfg.combos.firstOrNull { it.id == target.id } ?: return
    //  这几项也吃上面那颗总开关：关着时它们是**公共设置**那一份，改也改不动 ——
    //  亮着却改不改都没差别，读起来就是「这一页坏了」。
    //  灰着时说明是哪一道闸：总开关关着时说「先打开」，页面闸门关着时留空（用整页那句）。
    //  各行共用这一句 —— 同一个原因不该在几个地方各算一遍（判据见 `EditGate`）。
    val rowsEnabled = gate.comboRows
    val needOwnHint = gate.ownHint
    val source = comboOwnParams(combo, rowsEnabled, needOwnHint)
    //  两种触发方式是**互斥的**，所以只摆这一组自己那一张。
    if (combo.isWait) {
        SectionTitle(I18n.get(Strings.COMBO_SECTION_WAIT))
        WaitComboCard(source)
    } else {
        SectionTitle(I18n.get(Strings.COMBO_SECTION_HOLD))
        HoldComboCard(source)
    }
}

/**
 * 把一份独立设置写回它属于的那条记录 —— 单键 `keys[id]`、组合 `combos[id]`。
 *
 *  **这是两张配置表之间的差别，不是功能差别**：字段完全同名同义，所以这里只做「落到哪张表」，
 *  `copy` 的那六行两边一字不差 —— 多出来的只有外层那层容器。
 */
private fun applyOwnSettings(
    cfg: Config,
    target: SettingsTarget,
    values: OwnSettingsValues,
): Config =
    when (target) {
        is SettingsTarget.Key -> {
            cfg.copy(keys = cfg.keys + (target.id to cfg.key(target.id).withOwn(values)))
        }

        is SettingsTarget.Combo -> {
            cfg.copy(combos = cfg.combos.map { if (it.id == target.id) it.withOwn(values) else it })
        }
    }

/**
 * 这一格改完该弹哪句话 —— **只有开关有回执**，与公共页那一颗同一句话、同一个语义位
 * （[HINT_SLOT_OWN_SETTINGS]）。
 *
 *  滑杆（振动强度）与文案窗不给：公共页那两处也没给 —— 滑杆松手就会 commit 一次，弹回执等于
 *  把提示条刷成跑马灯；文案窗按下即关，屏幕上已经看得到结果。
 */
private fun ownSwitchNotice(
    field: OwnField,
    values: OwnSettingsValues,
): String? =
    when (field) {
        OwnField.USE_OWN -> {
            switchNotice(I18n.get(Strings.SETTING_USE_OWN), values.useOwnSettings)
        }

        OwnField.VIBRATE -> {
            switchNotice(I18n.get(Strings.HOME_VIBRATE_TITLE), values.vibrate)
        }

        OwnField.VIBRATE_ON_NONE -> {
            switchNotice(
                I18n.get(Strings.HOME_VIBRATE_ON_NONE_TITLE),
                values.vibrateOnNone,
            )
        }

        OwnField.MODE -> {
            //  「执行时提示」是一格四档，回执说「执行时提示 → 通知」；与常规页那一份同一个说法
            switchToNotice(I18n.get(Strings.HOME_TOAST_TITLE), noticeModeLabel(values.noticeMode))
        }

        OwnField.STEP_NOTICE -> {
            switchNotice(I18n.get(Strings.HOME_TOAST_STEP_NOTICE_TITLE), values.stepNotice)
        }

        OwnField.STRENGTH, OwnField.TEMPLATE -> {
            null
        }
    }

/**
 * 「独立设置」那张卡的一次提交 —— **只把这一项搬过去，其余几项以配置现值为准**。
 *
 *  卡片交回来的是**渲染那一帧时**读到的整份值：写盘 → 重组之间只要错开一步，那份就是上一帧的。
 *  原先整份写回，于是「打开使用独立设置」之后紧接着点「执行时提示」，会把总开关一起写回旧值
 *  （false）—— 真机上就是「刚打开的那颗自己又关回去了」。按字段合并之后，别的项有多旧都不影响，
 *  只有手指真点到的那一格会变。
 *
 *  **打开总开关不再抄公共值**：这一档自己那几格本来是「没写过 ⇒ 出厂默认」，开着照样是出厂默认
 *  （口径见 `docs/changelog/2026-11-17-独立设置去掉播种.md`）。原先那套「第一次打开时按公共值
 *  播一次种」整段删掉了 —— 它需要一整个 `ownSettingsSeeded` 位来判断「播过没有」，而那个位又得
 *  跟着落盘、跟着迁移、跟着重置清，绕出的状态比它省下的解释多。
 *
 *  抽成一支：这段写回逻辑本来与页面骨架无关。
 */
private fun SettingsPage.commitOwnField(
    target: SettingsTarget,
    values: OwnSettingsValues,
    field: OwnField,
) {
    val cfgNow = configState.config.value
    val ownerNow = target.ownerIn(cfgNow) ?: return
    val next = ownerNow.values().withField(field, values)
    //  「改谁」只在写回这一处落成具体表（`keys` 还是 `combos`）。
    val cfgNext = applyOwnSettings(cfgNow, target, next)
    val what = "own_settings target=${settingsTargetLabel(target)} field=$field"
    //  **开关有回执，滑杆与文案窗没有** —— 与公共页同一口径：那边滑杆也只靠行尾数值、
    //  文案窗按下即关。滑杆松手会连发一次 commit，弹回执只会把提示条刷成跑马灯。
    val notice = ownSwitchNotice(field, next)
    if (notice == null) {
        write(cfgNext, what)
    } else {
        writeAndNotice(cfgNext, what, slot = HINT_SLOT_OWN_SETTINGS) { notice }
    }
}

/**
 * 两张小标题 —— 「按键行为」与「按键设置」页用同一个字典键（`HOME_BINDING_CARD_TITLE`）：
 * 两页读到的是同一套分类名，用户不会觉得「同一件事换了个说法」。
 * 「总开关」是这一页独有的（公共页没有「这一档算不算数」这件事）。
 *
 *  「使用 ColorOS 接口」不在这张页里（它只在公共页调一次），所以这里没有「接口与显示」那一节。
 *
 *  收成一支 —— 把「这一页有哪几张卡」摊在主线上不值当。
 */
private fun ownCardTitles(): OwnCardTitles =
    OwnCardTitles(
        master = I18n.get(Strings.KEY_SETTINGS_SECTION),
        behavior = I18n.get(Strings.HOME_BINDING_CARD_TITLE),
    )
