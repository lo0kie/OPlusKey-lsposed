package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.OwnSettingsCard
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.general.ComboWindowRow
import com.lookie.opluskey.ui.screen.settingsTargetLabel
import com.lookie.opluskey.ui.state.SettingsTarget
import com.lookie.opluskey.ui.state.SettingsTargetState
import com.lookie.opluskey.ui.values
import com.lookie.opluskey.ui.withField
import com.lookie.opluskey.ui.withOwn
import com.lookie.opluskey.ui.write
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
    //  那一份被删了 / 找不到：摆一个空壳页没有意义，交给各自的列表页去处理退场
    val owner = target.ownerIn(cfg) ?: return
    PageScrollColumn(scrollBehavior = scrollBehavior) {
        // 顶栏叠在内容上，顶部要自己让出（动态值）
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior)))

        SectionTitle(I18n.get(Strings.KEY_SETTINGS_SECTION, "独立设置"))
        OwnSettingsCard(
            owner = owner,
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
            XLog.w(
                "own_settings 提交：field=$field 提交值=${values.useOwnSettings} " +
                    "现存=${base.useOwnSettings} 已播种=${ownerNow.ownSettingsSeeded} " +
                    "写入=${patched.useOwnSettings}",
            )
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
            val next =
                if (patched.useOwnSettings && !ownerNow.ownSettingsSeeded) {
                    patched.copy(
                        colorOsApi = cfgNow.global.colorOsApi,
                        vibrate = cfgNow.global.vibrate,
                        vibrateStrength = cfgNow.global.vibrateStrength,
                        toastOnRun = cfgNow.global.toastOnRun,
                        toastTemplate = cfgNow.global.toastTemplate,
                        ownSettingsSeeded = true,
                    )
                } else {
                    patched
                }
            //  「改谁」只在写回这一处落成具体表（`keys` 还是 `combos`）——
            //  卡片与页面其余部分都不知道这件事。
            write(applyOwnSettings(cfgNow, target, next), "own_settings target=${settingsTargetLabel(target)}")
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
 *  2. **按键之间的等待**：这一项**是全局的**，理由是判定顺序 —— 按下第一颗键时还不知道
 *     会不会组成哪一组，等多久必须在那一刻就定下来（`ComboDetector` 拿的是公共值）。
 *     所以这一行在这里出现只是「入口挪近」，它写的是公共那一份，副标题里写明。
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
    //  两行共用这一句 —— 同一个原因不该在两个地方各算一遍。
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
                        //  灰着时说明是哪一道闸（与下一行同一句，见 `needOwnHint`）
                        disabledHint = needOwnHint,
                    ) { on ->
                        //  写下去就是「这一组自己的决定」，不再跟随公共设置
                        write(
                            cfg.copy(combos = cfg.combos.map { if (it.id == combo.id) it.copy(passthrough = on) else it }),
                            "combo:${combo.id}:passthrough=${if (on) 1 else 0}",
                        )
                    },
                )
                add(CardRow.Custom { position -> ComboWindowRow(position, rowsEnabled, needOwnHint) })
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
    cfg: com.lookie.opluskey.core.model.Config,
    target: SettingsTarget,
    values: com.lookie.opluskey.ui.OwnSettingsValues,
): com.lookie.opluskey.core.model.Config =
    when (target) {
        is SettingsTarget.Key -> {
            cfg.copy(keys = cfg.keys + (target.id to cfg.key(target.id).withOwn(values)))
        }

        is SettingsTarget.Combo -> {
            cfg.copy(combos = cfg.combos.map { if (it.id == target.id) it.withOwn(values) else it })
        }
    }
