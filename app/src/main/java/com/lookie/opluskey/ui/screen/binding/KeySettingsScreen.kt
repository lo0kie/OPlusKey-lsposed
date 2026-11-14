package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageScrollColumn
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.HINT_SLOT_OWN_SETTINGS
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.OwnCardTitles
import com.lookie.opluskey.ui.row.OwnField
import com.lookie.opluskey.ui.row.OwnSettingsCard
import com.lookie.opluskey.ui.row.OwnSettingsValues
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.row.values
import com.lookie.opluskey.ui.row.withField
import com.lookie.opluskey.ui.row.withOwn
import com.lookie.opluskey.ui.screen.combo.holdCombosStartedBy
import com.lookie.opluskey.ui.screen.combo.setComboOwnHoldMin
import com.lookie.opluskey.ui.screen.combo.setComboOwnWindow
import com.lookie.opluskey.ui.screen.combo.updateCombo
import com.lookie.opluskey.ui.screen.combo.waitCombosUsing
import com.lookie.opluskey.ui.screen.settingsTargetLabel
import com.lookie.opluskey.ui.state.page.SettingsTarget
import com.lookie.opluskey.ui.state.page.SettingsTargetState
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

        //  「只参与组合按键」开着时，这一页**只剩那一行能动**。
        //  这一位说的是「这颗键不判单键手势」—— 那么多击、接口、振动、提示就都没有执行的机会，
        //  亮着就是在骗人「还能调」。留那一行是因为**关回来**得走它（关不掉就成了死路）。
        //  判据与绑定页那一族同一份（`bindingTargetStopped`），两处各判一遍迟早漂。
        //  只有单键有这一位：组合目标（`SettingsTarget.Combo`）不进这一支。
        val comboOnly = target is SettingsTarget.Key && cfg.key(target.id).comboOnly

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
            SectionTitle(I18n.get(Strings.KEY_SETTINGS_MULTI_CLICK, "多击手势"))
            ExtraClicksCard(
                target = target,
                cfg = cfg,
                enabled = pageEnabled,
            )
        }

        //  一个小标题带一张卡（与「按键设置」页同构）—— 页面顶部不再另放一个与顶栏同名的
        //  「独立设置」，那三个小标题由卡片自己画（见 `OwnSettingsCard`）。
        OwnSettingsCard(
            owner = owner,
            //  **显示用生效值、写入仍落到这一档**：总开关关着时那几项不参与取值，直接读
            //  `owner` 会让这一页显示「振动反馈：关」而按下去明明会振（口径见 `OwnSettingsCard`）。
            effective = cfg.effectiveFor(owner),
            //  「只参与组合按键」时整张卡灰掉：接口 / 振动 / 提示都是**单键按下去那一下**的行为，
            //  这颗键不判单键手势，它们一次也用不上。灰着点一下落到页面那句原因上
            //  （`disabledClickReason` 的 comboOnly 那一支），卡片里不必再写一句。
            enabled = pageEnabled && !comboOnly,
            titles = ownCardTitles(),
        ) { values, field -> commitOwnField(target, values, field) }

        //  —— 单击（判定时机 + 等待窗口）——
        //  **排在总开关下面**（用户口径）：这两项也吃那颗「使用独立设置」——
        //  关着时跟随公共设置里那一份、这一页只把生效值摆出来并灰着。
        //  又多一道「有没有多击手势」：这一页管的是「这颗键的多击怎么判」，一档都没开时
        //  没有可判的东西（判据见 `BindingSettingsCard`）。
        if (target is SettingsTarget.Key) {
            SectionTitle(I18n.get(Strings.KEY_SETTINGS_CLICK, "单击"))
            BindingSettingsCard(
                keyId = target.id,
                enabled = pageEnabled && !comboOnly,
            )
        }

        //  —— 多击手势那两颗开关 ——
        //  **挪到页顶去了**（见 `KeySettingsScreen` 开头）：它们决定下面那张卡能管到几档 ——
        //  多击是「有没有这一档」，不是「这一档怎么工作」。留这一句只为说明它们为什么不在这张卡里。

        //  —— 组合专属的两项 ——
        //  它们**不在**上面那张卡里：那张卡装的是「那几项公共项的覆盖」，单键组合都有；
        //  这两项只对组合成立（「两颗键各干各的」「两颗键等多久」单键没有对应物）。
        //  条件只有一个：这一趟改的是一组组合 —— 这是数据差异，不是流程分叉。
        if (target is SettingsTarget.Combo) {
            SectionTitle(I18n.get(Strings.COMBO_SETTINGS_SECTION, "组合按键设置"))
            ComboOwnCard(target = target, cfg = cfg, enabled = pageEnabled, ownOn = owner.useOwnSettings)
        }
    }

    //  「只参与组合」被拦下时的说明窗 —— **必须挂在这一页**：开关就在上面那张卡里，
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
    enabled: Boolean,
) {
    val key = cfg.key(target.id)
    //  「只参与组合按键」开着时，这张卡里**只有那一行自己还能动**：以下几档都是单键手势，
    //  这颗键不判单键手势 ⇒ 配了也没处执行。留中间这三行亮着，用户会以为「多击还能用」。
    //  点一下会落到页面那句原因上（`disabledClickReason` 的 comboOnly 那一支），不必在这重写。
    val restEnabled = enabled && !key.comboOnly
    CardList(
        rows =
            listOf(
                //  「只参与组合按键」排在这一族的最前面：它决定这颗键**还算不算一颗单键** ——
                //  开着时下面那几档配了也没处执行（绑定页整页灰，见 `bindingTargetEnabled`）。
                comboOnlyRow(target, key, enabled),
                //  双击排在三击上面：档位由少到多，与绑定页的页序（单击 / 双击 / 长按 / 三击）同一方向。
                //  它是**基础档**（默认开），与三击 / 四击那种「要自己要」的档位只是同一张卡里的邻居。
                doubleClickOnRow(target, key, restEnabled),
                extraClickRow(target, key, COUNT3, restEnabled),
                extraClickRow(target, key, COUNT4, restEnabled),
                clickThenLongRow(target, key, restEnabled),
            ),
    )
}

/**
 * 「只参与组合按键」那一行 —— 这颗键不判单键手势、只等组合成员（见 `KeyConfig.comboOnly`）。
 *
 *  与上面那张卡同族：**不吃 `useOwnSettings`**（公共设置里没有这一项，跟随无从谈起），
 *  只吃整页闸门。
 *
 *  开着时**绑定页整页灰掉**（`bindingTargetEnabled`）：这颗键不判单键手势，配一档动作没处执行。
 *  但**这一页照旧能改** —— 否则用户想关回来都点不动；页面上那句原因由
 *  `disabledClickReason` 给（「只参与组合按键」那一支）。
 *
 *  ## 打开这一项要拦一道
 *
 *  这颗键如果已经是某档**按住组合的起点**（`Config.holdCombosStartedBy`），这一项就开不了：
 *  按住那一档要求起点键「按住到点」再松手，而只参与组合的键根本不判单键手势 —— 两个要求
 *  互斥，落下去就是一份自相矛盾的配置（与 `ComboActions.addCombo` 里「起点键只参与组合」
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
        title = I18n.get(Strings.BINDING_COMBO_ONLY, "只参与组合按键"),
        summaryOn = I18n.get(Strings.BINDING_COMBO_ONLY_ON, "单键手势交还系统，模块只用它凑组合"),
        summaryOff = I18n.get(Strings.BINDING_COMBO_ONLY_OFF, "单键手势由模块接管"),
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
                    "只参与组合：${target.id.label} 是 ${starters.size} 档按住组合的起点" +
                        "（${starters.joinToString { it.keys.joinToString("+") }}），拒绝开启",
                )
                ComboOnlyBlocked.ask(target.id, ComboOnlyBlocked.Reason.HOLD_STARTER)
            }

            waits.isNotEmpty() -> {
                XLog.w(
                    "只参与组合：${target.id.label} 是 ${waits.size} 档等待组合的成员" +
                        "（${waits.joinToString { it.keys.joinToString("+") }}），拒绝开启",
                )
                ComboOnlyBlocked.ask(target.id, ComboOnlyBlocked.Reason.WAIT_MEMBER)
            }

            else -> {
                val keyNow = cfgNow.key(target.id)
                val cfgNext = cfgNow.copy(keys = cfgNow.keys + (target.id to keyNow.copy(comboOnly = on)))
                val what = "own_settings target=${settingsTargetLabel(target)} combo_only=${if (on) 1 else 0}"
                writeAndNotice(cfgNext, what, slot = HINT_SLOT_OWN_SETTINGS) {
                    switchNotice(I18n.get(Strings.BINDING_COMBO_ONLY, "只参与组合按键"), on)
                }
            }
        }
    }

/**
 * 「单击后长按」那颗开关 —— 与三击 / 四击并列，但它是**另一位**而不是击数集合里的一员：
 *  它描述的是「第 2 击按住不放」这另一种形态，与双击互斥。
 *
 *  **它不延长连击链**：开三击要单击多等一个窗，开这一档不多等 —— 判定侧只在第 2 击
 *  按下时顺手排一枪长按，抬手照样立刻派发双击（见 `GestureDetector`）。
 */
@Composable
private fun SettingsPage.clickThenLongRow(
    target: SettingsTarget.Key,
    key: KeyConfig,
    enabled: Boolean,
): CardRow =
    CardRow.Switch(
        title = I18n.get(Strings.SETTING_CLICK_THEN_LONG, "启用单击后长按"),
        summaryOn = I18n.get(Strings.SETTING_CLICK_THEN_LONG_ON, "第二击按住不放时触发"),
        summaryOff = I18n.get(Strings.SETTING_CLICK_THEN_LONG_OFF, "不识别单击后长按"),
        icon = PageIcons.gestureIcon(GestureTrigger.ClickThenLong),
        checked = key.clickThenLongOn,
        enabled = enabled,
    ) { on ->
        //  与上面两行同一条：现读一次再改这一格，只动这一位
        val cfgNow = configState.config.value
        val keyNow = cfgNow.key(target.id)
        val cfgNext = cfgNow.copy(keys = cfgNow.keys + (target.id to keyNow.copy(clickThenLongOn = on)))
        val what = "own_settings target=${settingsTargetLabel(target)} click_then_long=${if (on) 1 else 0}"
        writeAndNotice(cfgNext, what, slot = HINT_SLOT_OWN_SETTINGS) {
            switchNotice(I18n.get(Strings.SETTING_CLICK_THEN_LONG, "启用单击后长按"), on)
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
        title = I18n.get(Strings.SETTING_CLICK2, "启用双击"),
        summaryOn = I18n.get(Strings.SETTING_CLICK2_ON, "双击可绑定动作"),
        summaryOff = I18n.get(Strings.SETTING_CLICK2_OFF, "不识别双击"),
        //  字形与三击 / 四击同一个来源（`gestureIcon`）：这两行说的就是「要不要多出这一档」。
        icon = PageIcons.gestureIcon(GestureTrigger.Click(2)),
        checked = key.doubleClickOn,
        enabled = enabled,
    ) { on ->
        //  与上面几行同一条：现读一次再改这一格，只动这一位
        val cfgNow = configState.config.value
        val keyNow = cfgNow.key(target.id)
        val cfgNext = cfgNow.copy(keys = cfgNow.keys + (target.id to keyNow.copy(doubleClickOn = on)))
        val what = "own_settings target=${settingsTargetLabel(target)} double_click=${if (on) 1 else 0}"
        writeAndNotice(cfgNext, what, slot = HINT_SLOT_OWN_SETTINGS) {
            switchNotice(I18n.get(Strings.SETTING_CLICK2, "启用双击"), on)
        }
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
                COUNT3 -> I18n.get(Strings.SETTING_CLICK3, "启用三击")
                else -> I18n.get(Strings.SETTING_CLICK4, "启用四击")
            },
        summaryOn =
            when (count) {
                COUNT3 -> I18n.get(Strings.SETTING_CLICK3_ON, "三击可绑定动作")
                else -> I18n.get(Strings.SETTING_CLICK4_ON, "四击可绑定动作")
            },
        summaryOff =
            when (count) {
                COUNT3 -> I18n.get(Strings.SETTING_CLICK3_OFF, "不识别三击")
                else -> I18n.get(Strings.SETTING_CLICK4_OFF, "不识别四击")
            },
        //  字形直接用那一档自己的（`gestureIcon` 里第 3 击起与双击共用两根手指）：
        //  这两行说的就是「要不要多出这一档」，不必新起只此一处的图标。
        icon = PageIcons.gestureIcon(GestureTrigger.Click(count)),
        checked = on,
        enabled = enabled,
    ) { checked ->
        //  **现读一次配置**再改这一格（与「组合」那两行同一条口径）：这颗开关下面
        //  就是那张卡，先拨开关再点卡里的项，用渲染那一帧的 `cfg` 会把卡片刚写进去的值顶回去。
        val cfgNow = configState.config.value
        val keyNow = cfgNow.key(target.id)
        //  只动这一档，另一档原样留着（两行各管各的）。
        val clicksOn = if (checked) keyNow.extraClicksOn + count else keyNow.extraClicksOn - count
        //  表（`extraClicks`）不动：关掉只是「不显示、不判定、不执行」，再打开原样回来 ——
        //  清表等于把用户配过的动作悄悄扔掉。
        val cfgNext =
            cfgNow.copy(keys = cfgNow.keys + (target.id to keyNow.copy(extraClicksOn = clicksOn)))
        val what =
            "own_settings target=${settingsTargetLabel(target)} " +
                "click$count=${if (checked) 1 else 0} 全档=${clicksOn.sorted()}"
        val title =
            if (count == COUNT3) {
                I18n.get(Strings.SETTING_CLICK3, "启用三击")
            } else {
                I18n.get(Strings.SETTING_CLICK4, "启用四击")
            }
        writeAndNotice(cfgNext, what, slot = HINT_SLOT_OWN_SETTINGS) { switchNotice(title, checked) }
    }
}

/** 这两颗开关各管哪一档 —— 与 [KeyConfig.DEFAULT_EXTRA_CLICKS] 同一份清单（3 击 / 4 击）。 */
private val COUNT3: Int = KeyConfig.DEFAULT_EXTRA_CLICKS.first()

private val COUNT4: Int = KeyConfig.DEFAULT_EXTRA_CLICKS.last()

/**
 * 「组合按键设置」那一族 —— 只在改的是**一组组合**时出现。
 *
 *  这一档**怎么按出来**（等窗口 / 先按住第一颗 / 先按住第二颗）不在这张卡里：它是这一档的
 *  身份（`ComboKey.mode`），入口在「组合键」编辑页那颗 ⋮ 的「触发方式」一行。这里只剩
 *  那两种触发方式各自的参数，且**跟着当前那一种**显隐 —— 换方式之后不该看到另一条的参数。
 *
 *  · **按键之间的等待**：这一组自己的一份数。它与公共页那一行是**同一个参数、两个落点** ——
 *    那个数同时管「算不算同时按下」与「第一颗键要等多久才回落单击」，三颗的组合与两颗的
 *    组合对它的要求本来就不一样，共用一个数只能互相将就。
 *    文案两处逐字相同（用户口径：不需要特判），滑杆打开就是这一组自己的值；没拖过就还等于
 *    公共那一档（`Config.comboWindowFor`：独立设置关着看公共，开着看这一组写过没有）。
 *    同样只在等窗口那一档出现。
 *  · **首键按住时长**：按住那两档的下限 —— 起点键要按满这么久、还没抬手，另一颗落下才算组合。
 *    只有按住那两档才有意义，等窗口那一档不出现。
 *  · **系统场景在首键按下时固定**：按住那两档的场景什么时候取（按下那一刻固定 / 凑成那一刻再取）。
 *    与上面那条同一组 —— 它们都是「先按住一颗再按另一颗」这条按法的参数，等窗口那一档不出现。
 *
 *  这一族都吃上面那颗总开关：关着时它们是**公共设置**那一份，改也改不动 —— 不用再给其中某一行
 *  单独加一个「要不要自己设」的开关，那个总开关就是唯一那道闸。
 */
@Composable
private fun SettingsPage.ComboOwnCard(
    target: SettingsTarget.Combo,
    cfg: Config,
    enabled: Boolean,
    ownOn: Boolean,
) {
    val combo = cfg.combos.firstOrNull { it.id == target.id } ?: return
    //  这几项也吃上面那颗总开关：关着时它们是**公共设置**那一份，改也改不动 ——
    //  亮着却改不改都没差别，读起来就是「这一页坏了」。
    val rowsEnabled = enabled && ownOn
    //  灰着时说明是哪一道闸：总开关关着时说「先打开」，页面闸门关着时留空（用整页那句）。
    //  各行共用这一句 —— 同一个原因不该在几个地方各算一遍。
    val needOwnHint =
        if (enabled && !ownOn) {
            I18n.get(Strings.KEY_SETTINGS_NEED_OWN, "先打开 [使用独立设置]")
        } else {
            null
        }
    //  两种触发方式是**互斥的**（`ComboKey.mode` 是这一档的身份），所以参数也按它分两组：
    //  等窗口那一档看窗口那两个数，按住那两档只看首键按住时长。
    val isWait = combo.isWait
    CardList(
        rows =
            buildList {
                //  **按键之间的等待**是窗口那条的参数（相邻两颗最多隔多久）——
                //  换到按住那一档之后这个数不再有任何作用（成员键不再按它等）
                add(
                    CardRow.Custom(visible = isWait) { position ->
                        SliderRow(
                            position = position,
                            spec =
                                SliderSpec(
                                    //  文案与公共页那一行**逐字相同**：同一个参数，两处只是改的对象不同
                                    title = I18n.get(Strings.COMBO_WINDOW_TITLE, "按键之间的等待"),
                                    summary = I18n.get(Strings.COMBO_WINDOW_DESC, "调大后，参与组合的按键等待更久"),
                                    icon = PageIcons.ComboWait,
                                    min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                                    max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                                    //  这一根滑杆显示的是**这一组自己存着**的那个数（没写过才退回公共）——
                                    //  不是 `comboWindowFor` 那个「实际生效」值：取生效值的话，
                                    //  拨一下上面那颗总开关它就从公共值跳到这一组自己的值，
                                    //  看着像刚拖好的窗口被改了（与振动强度那一行同一条口径）。
                                    value = cfg.comboStoredWindowFor(combo),
                                    default = cfg.global.comboWindowMs,
                                    step = 10,
                                    enabled = rowsEnabled,
                                    disabledHint = needOwnHint,
                                    valueText = { "$it ms" },
                                ),
                        ) { v -> setComboOwnWindow(combo.id, v) }
                    },
                )
                //  按住那两档的**下限**：首键要按满这么久、还没抬手，第二颗落下才算这一档组合。
                //  只有那两档才有意义（等窗口那一档不读它，见 `Config.comboHoldMinFor`）。
                add(
                    CardRow.Custom(visible = !isWait) { position ->
                        SliderRow(
                            position = position,
                            spec =
                                SliderSpec(
                                    //  文案与公共页那一行**逐字相同**：同一个参数，两处只是改的对象不同
                                    title = I18n.get(Strings.COMBO_HOLD_MIN_TITLE, "首键按住时长"),
                                    summary =
                                        I18n.get(
                                            Strings.COMBO_HOLD_MIN_DESC,
                                            "先按住的那颗按满这么久、还没抬手，再按另一颗才算组合",
                                        ),
                                    icon = PageIcons.ComboHold,
                                    min = GlobalConfig.COMBO_HOLD_MIN_MIN_MS,
                                    max = GlobalConfig.COMBO_HOLD_MIN_MAX_MS,
                                    //  显示这一组自己存着的数（没写过才退回公共）—— 口径与上面那根
                                    //  「按键之间的等待」一致：不跟着总开关跳值。
                                    value = cfg.comboStoredHoldMinFor(combo),
                                    default = cfg.global.comboHoldMinMs,
                                    step = 10,
                                    enabled = rowsEnabled,
                                    disabledHint = needOwnHint,
                                    valueText = { "$it ms" },
                                ),
                        ) { v -> setComboOwnHoldMin(combo.id, v) }
                    },
                )
                //  按住那两档的**系统场景什么时候取**：首键按下那一刻固定，还是凑成那一刻再取。
                //  与「首键按住时长」同一组（同一条触发方式的参数），文案也与公共页那一行逐字相同。
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.COMBO_SCENE_ON_STARTER_TITLE, "系统场景在首键按下时固定"),
                        summaryOn =
                            I18n.get(
                                Strings.COMBO_SCENE_ON_STARTER_DESC_ON,
                                "首键按下那一刻就定下用哪块场景，按住期间换应用不改变动作",
                            ),
                        summaryOff =
                            I18n.get(
                                Strings.COMBO_SCENE_ON_STARTER_DESC_OFF,
                                "组合凑成那一刻再取场景",
                            ),
                        icon = PageIcons.ScenePin,
                        checked = cfg.comboSceneOnStarterFor(combo),
                        enabled = rowsEnabled,
                        visible = !isWait,
                        disabledHint = needOwnHint,
                    ) { on ->
                        //  写下去就是「这一组自己的决定」，不再跟随公共设置（与上面那颗透传同一条）
                        val ok =
                            updateCombo(combo.id, "scene_on_starter=${if (on) 1 else 0}") {
                                it.copy(sceneOnStarter = on)
                            }
                        if (ok) {
                            hint(
                                switchNotice(
                                    I18n.get(Strings.COMBO_SCENE_ON_STARTER_TITLE, "系统场景在首键按下时固定"),
                                    on,
                                ),
                                slot = HINT_SLOT_COMBO,
                            )
                        }
                    },
                )
            },
    )
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
 * 组合那几位「这一组独有的参数」**跟着总开关一起播种**。
 *
 *  `OwnSettings` 契约里只有那几项，`windowMs` / `holdMinMs` / `sceneOnStarter` 是 `ComboKey` 独有的，
 *  所以它们**不在** [OwnSettingsValues] 里 —— 但「打开独立设置」对用户来说是一件事：
 *  这一档从此自己说了算。只播那几项的话，其余几位仍停在「没设过 ⇒ 跟公共」，同一颗开关下就有了
 *  两套语义（那几项已经是自己的值、另几位还在跟随）——「既然都开了独立设置，还跟随公共」正是这个别扭。
 *
 *  播一次之后不再重播（判据 `ownSettingsSeeded`）：关掉总开关仍回到公共
 *  （`Config.comboWindowFor` 的第一支），重新打开用的是上次这一套。
 *  读取侧那几个 `?: global` 兜底留给**老配置**（这一位出现之前写下的 `windowMs = 0` /
 *  `holdMinMs = 0`），新配置不会再落到那一支。
 *
 * @param values 上面算好的那份独立设置（用它判断「这一次是不是刚打开总开关」）
 * @param before 写之前的这一档 —— 只为看 `ownSettingsSeeded`
 */
private fun withSeededComboFields(
    cfg: Config,
    target: SettingsTarget,
    values: OwnSettingsValues,
    before: OwnSettings,
): Config {
    val base = applyOwnSettings(cfg, target, values)
    if (target !is SettingsTarget.Combo || !values.useOwnSettings || before.ownSettingsSeeded) return base
    //  播的是**这一刻**的公共值：与上面那几项同一个时刻、同一份来源，两边不会错开
    val windowMs = cfg.global.comboWindowMs
    //  按住手势的下限也一起播：它属于「这一档从此自己说了算」那一族，不播就还在跟随公共
    val holdMinMs = cfg.global.comboHoldMinMs
    //  「场景在什么时候取」同样属于那一族：不播的话这一位仍是 `null`，
    //  以后改公共设置还会顺带改到这一档 —— 与「打开独立设置之后整份自己说了算」不符
    val sceneOnStarter = cfg.global.comboSceneOnStarter
    return base.copy(
        combos =
            base.combos.map { c ->
                if (c.id == target.id) {
                    c.copy(
                        windowMs = windowMs,
                        holdMinMs = holdMinMs,
                        sceneOnStarter = sceneOnStarter,
                    )
                } else {
                    c
                }
            },
    )
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
        OwnField.USE_OWN -> switchNotice(I18n.get(Strings.SETTING_USE_OWN, "使用独立设置"), values.useOwnSettings)
        OwnField.COLOR_OS -> switchNotice(I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"), values.colorOsApi)
        OwnField.VIBRATE -> switchNotice(I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"), values.vibrate)
        OwnField.TOAST -> switchNotice(I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"), values.toastOnRun)
        OwnField.FLUID -> switchNotice(I18n.get(Strings.HOME_TOAST_FLUID_TITLE, "用流体云提示"), values.toastFluid)
        OwnField.STRENGTH, OwnField.TEMPLATE -> null
    }

/**
 * 「独立设置」那张卡的一次提交 —— **只把这一项搬过去，其余几项以配置现值为准**。
 *
 *  卡片交回来的是**渲染那一帧时**读到的整份值：写盘 → 重组之间只要错开一步，那份就是上一帧的。
 *  原先整份写回，于是「打开使用独立设置」之后紧接着点「执行时提示」，会把总开关一起写回旧值
 *  （false）—— 真机上就是「刚打开的那颗自己又关回去了」。按字段合并之后，别的项有多旧都不影响，
 *  只有手指真点到的那一格会变。
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
    val base = ownerNow.values()
    val patched = base.withField(field, values)
    //  **第一次打开总开关时，用公共设置把这一档播一次种**；之后开关怎么拨都不再读公共值。
    //
    //  为什么要播：这一页从来没配过时，各项自己的默认值与公共那份往往不同（振动关着、
    //  强度中档、提示关着、模板是默认那句）—— 打开开关却看到一片与公共不一样的旧默认值，
    //  读起来就是「开了没生效」。
    //
    //  为什么**只播一次**（判据是 `ownSettingsSeeded`，不是「当前开关关着」）：用户把这
    //  几项调成自己想要的样子、关掉总开关、过一阵又打开 —— 那时他要的是**上次那套**，
    //  不是公共设置。原先每次「关→开」都重抄一遍，等于把他调过的值悄悄扔掉。
    //  播种后置位写到盘上，所以跨进程、跨重启都只播一次。
    //
    //  **播种之后必须把这一次的改动再叠回去**（`withField` 那一下）：顺序反过来的话，
    //  用户刚拨的那一项会被公共值盖掉。什么时候会撞上：`useOwnSettings` 早就开着、
    //  而 `ownSettingsSeeded` 还是 false —— 老配置里没有这一位（它是后加的），
    //  升级上来的用户第一次拨任何一项都会「拨了没反应」（那一格被公共值顶回去）。
    val next =
        if (patched.useOwnSettings && !ownerNow.ownSettingsSeeded) {
            patched
                .copy(
                    colorOsApi = cfgNow.global.colorOsApi,
                    vibrate = cfgNow.global.vibrate,
                    vibrateStrength = cfgNow.global.vibrateStrength,
                    toastOnRun = cfgNow.global.toastOnRun,
                    toastFluid = cfgNow.global.toastFluid,
                    toastTemplate = cfgNow.global.toastTemplate,
                    ownSettingsSeeded = true,
                ).withField(field, values)
        } else {
            patched
        }
    XLog.w(
        "own_settings 提交：field=$field 提交值=${values.useOwnSettings} " +
            "现存=${base.useOwnSettings} 已播种=${ownerNow.ownSettingsSeeded} " +
            "写入=${next.useOwnSettings}",
    )
    //  「改谁」只在写回这一处落成具体表（`keys` 还是 `combos`）；组合那两项也在这里
    //  跟着一起播种（理由见 [withSeededComboFields]）。
    val cfgNext = withSeededComboFields(cfgNow, target, next, ownerNow)
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
 * 三张小标题 —— **后两个与「按键设置」页用同一个字典键**（`GENERAL_SECTION_INTERFACE` /
 * `HOME_BINDING_CARD_TITLE`）：两页读到的是同一套分类名，用户不会觉得「同一件事换了个说法」。
 * 「总开关」是这一页独有的（公共页没有「这一档算不算数」这件事）。
 *
 *  收成一支 —— 把「这一页有哪几张卡」摊在主线上不值当。
 */
private fun ownCardTitles(): OwnCardTitles =
    OwnCardTitles(
        master = I18n.get(Strings.KEY_SETTINGS_SECTION, "总开关"),
        interfaceTitle = I18n.get(Strings.GENERAL_SECTION_INTERFACE, "接口与显示"),
        behavior = I18n.get(Strings.HOME_BINDING_CARD_TITLE, "按键行为"),
    )
