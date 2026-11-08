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
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.HINT_SLOT_OWN_SETTINGS
import com.lookie.opluskey.ui.OwnField
import com.lookie.opluskey.ui.OwnSettingsCard
import com.lookie.opluskey.ui.OwnSettingsValues
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SliderRow
import com.lookie.opluskey.ui.SliderSpec
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.combo.setComboOwnWindow
import com.lookie.opluskey.ui.screen.combo.updateCombo
import com.lookie.opluskey.ui.screen.settingsTargetLabel
import com.lookie.opluskey.ui.state.SettingsTarget
import com.lookie.opluskey.ui.state.SettingsTargetState
import com.lookie.opluskey.ui.switchNotice
import com.lookie.opluskey.ui.values
import com.lookie.opluskey.ui.withField
import com.lookie.opluskey.ui.withOwn
import com.lookie.opluskey.ui.write
import com.lookie.opluskey.ui.writeAndNotice
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
        // 顶栏叠在内容上，顶部要自己让出（动态值）
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        SectionTitle(I18n.get(Strings.KEY_SETTINGS_SECTION, "独立设置"))
        OwnSettingsCard(
            owner = owner,
            //  **显示用生效值、写入仍落到这一档**：总开关关着时那五项不参与取值，直接读
            //  `owner` 会让这一页显示「振动反馈：关」而按下去明明会振（口径见 `OwnSettingsCard`）。
            effective = cfg.effectiveFor(owner),
            enabled = pageEnabled,
        ) { values, field ->
            //  **只把这一项搬过去，其余五项以配置现值为准**。
            //
            //  卡片交回来的是**渲染那一帧时**读到的整份值：写盘 → 重组之间只要错开一步，
            //  那份就是上一帧的。原先整份写回，于是「打开使用独立设置」之后紧接着点
            //  「执行时提示」，会把总开关一起写回旧值（false）—— 真机上就是「刚打开的那颗
            //  自己又关回去了」，日志里能同时看到 `values.useOwn=false` 与 `现存.useOwn=true`。
            //  按字段合并之后，别的项有多旧都不影响，只有手指真点到的那一格会变。
            val cfgNow = configState.config.value
            val ownerNow = target.ownerIn(cfgNow) ?: return@OwnSettingsCard
            val base = ownerNow.values()
            val patched = base.withField(field, values)
            //  **第一次打开总开关时，用公共设置把这一档播一次种**；之后开关怎么拨都不再读公共值。
            //
            //  为什么要播：这一页从来没配过时，各项自己的默认值与公共那份往往不同（振动关着、
            //  强度中档、提示关着、模板是默认那句）—— 打开开关却看到一片与公共不一样的旧默认值，
            //  读起来就是「开了没生效」。
            //
            //  为什么**只播一次**（判据是 `ownSettingsSeeded`，不是「当前开关关着」）：用户把这
            //  五项调成自己想要的样子、关掉总开关、过一阵又打开 —— 那时他要的是**上次那套**，
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

        //  —— 组合专属的两项 ——
        //  它们**不在**上面那张卡里：那张卡装的是「五项公共项的覆盖」，单键组合都有；
        //  这两项只对组合成立（「两颗键各干各的」「两颗键等多久」单键没有对应物）。
        //  条件只有一个：这一趟改的是一组组合 —— 这是数据差异，不是流程分叉。
        if (target is SettingsTarget.Combo) {
            SectionTitle(I18n.get(Strings.COMBO_SETTINGS_SECTION, "组合按键设置"))
            ComboOwnCard(target = target, cfg = cfg, enabled = pageEnabled, ownOn = owner.useOwnSettings)
        }
    }
}

/**
 * 「组合按键设置」那两行 —— 只在改的是**一组组合**时出现。
 *
 *  1. **无操作时执行按键命令**：这一组自己说了算（写过就用自己的，没写过跟随公共设置）。
 *  2. **按键之间的等待**：这一组自己的一份数。它与公共页那一行是**同一个参数、两个落点** ——
 *     那个数同时管「算不算同时按下」与「第一颗键要等多久才回落单击」，三颗的组合与两颗的
 *     组合对它的要求本来就不一样，共用一个数只能互相将就。
 *     文案两处逐字相同（用户口径：不需要特判），滑杆打开就是这一组自己的值；没拖过就还等于
 *     公共那一档（`Config.comboWindowFor`：独立设置关着看公共，开着看这一组写过没有）。
 *
 *  两行都吃上面那颗总开关：关着时它们是**公共设置**那一份，改也改不动 —— 不用再给其中某一行
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
    //  这两项也吃上面那颗总开关：关着时它们是**公共设置**那一份，改也改不动 ——
    //  亮着却改不改都没差别，读起来就是「这一页坏了」。
    val rowsEnabled = enabled && ownOn
    //  灰着时说明是哪一道闸：总开关关着时说「先打开」，页面闸门关着时留空（用整页那句）。
    //  各行共用这一句 —— 同一个原因不该在几个地方各算一遍。
    val needOwnHint =
        if (enabled && !ownOn) {
            I18n.get(Strings.KEY_SETTINGS_NEED_OWN, "先打开「使用独立设置」")
        } else {
            null
        }
    CardList(
        rows =
            buildList {
                add(
                    CardRow.Switch(
                        title = I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"),
                        summaryOn = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_ON, "两颗键各执行自己的绑定"),
                        summaryOff = I18n.get(Strings.COMBO_PASSTHROUGH_DESC_OFF, "什么都不做"),
                        icon = PageIcons.NoMatch,
                        checked = cfg.comboPassthroughFor(combo),
                        enabled = rowsEnabled,
                        //  灰着时说明是哪一道闸（与下面两行同一句，见 `needOwnHint`）
                        disabledHint = needOwnHint,
                    ) { on ->
                        //  写下去就是「这一组自己的决定」，不再跟随公共设置。
                        //  **按最新的配置改**（`updateCombo` 现读一次），不用渲染那一帧的 `cfg`：
                        //  同一页下面那根滑杆走的正是现读（`setComboOwnWindow`），一行用快照、
                        //  一行现读时，先拨这一行再拖那根滑杆会把刚写进去的值顶回旧值。
                        val ok = updateCombo(combo.id, "passthrough=${if (on) 1 else 0}") { it.copy(passthrough = on) }
                        //  回执与公共页那一颗**同一个语义位**（`HINT_SLOT_COMBO`）：两处是同一个参数的
                        //  两个落点，连着拨时应当是同一条在改字，而不是各弹一条。
                        if (ok) {
                            hint(
                                switchNotice(I18n.get(Strings.COMBO_PASSTHROUGH_TITLE, "无操作时执行按键命令"), on),
                                slot = HINT_SLOT_COMBO,
                            )
                        }
                    },
                )
                add(
                    CardRow.Custom { position ->
                        SliderRow(
                            position = position,
                            spec =
                                SliderSpec(
                                    //  文案与公共页那一行**逐字相同**：同一个参数，两处只是改的对象不同
                                    title = I18n.get(Strings.COMBO_WINDOW_TITLE, "按键之间的等待"),
                                    summary = I18n.get(Strings.COMBO_WINDOW_DESC, "调大会让参与组合的按键多等这么久"),
                                    icon = PageIcons.DelayStep,
                                    min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                                    max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                                    //  这一组还没写过时显示的是**实际生效**那个数：老配置里
                                    //  `windowMs` 可能是 0（这一位出现之前），读作「跟随公共」；
                                    //  打开过独立设置之后它已经有自己那份了
                                    value = cfg.comboWindowFor(combo),
                                    default = cfg.global.comboWindowMs,
                                    step = 10,
                                    enabled = rowsEnabled,
                                    disabledHint = needOwnHint,
                                    valueText = { "$it ms" },
                                ),
                        ) { v -> setComboOwnWindow(combo.id, v) }
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
 * 组合那两项**跟着总开关一起播种**。
 *
 *  `OwnSettings` 契约里只有那五项，`passthrough` / `windowMs` 是 `ComboKey` 独有的，所以它们
 *  **不在** [OwnSettingsValues] 里 —— 但「打开独立设置」对用户来说是一件事：这一档从此自己说了算。
 *  只播五项的话，透传与窗口仍停在「没设过 ⇒ 跟公共」，同一颗开关下就有了两套语义（五项已经是
 *  自己的值、两项还在跟随）——「既然都开了独立设置，还跟随公共」正是这个别扭。
 *
 *  播一次之后不再重播（判据 `ownSettingsSeeded`）：关掉总开关仍回到公共
 *  （`Config.comboWindowFor` / `comboPassthroughFor` 的第一支），重新打开用的是上次这一套。
 *  读取侧那几个 `?: global` 兜底留给**老配置**（这一位出现之前写下的 `windowMs = 0` /
 *  `passthrough = null`），新配置不会再落到那一支。
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
    //  播的是**这一刻**的公共值：与上面那五项同一个时刻、同一份来源，两边不会错开
    val passthrough = cfg.global.comboPassthrough
    val windowMs = cfg.global.comboWindowMs
    return base.copy(
        combos = base.combos.map { c -> if (c.id == target.id) c.copy(passthrough = passthrough, windowMs = windowMs) else c },
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
        OwnField.STRENGTH, OwnField.TEMPLATE -> null
    }
