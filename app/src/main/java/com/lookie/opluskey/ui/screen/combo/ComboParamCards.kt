package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.write.setComboHoldMin
import com.lookie.opluskey.ui.write.setComboSceneOnStarter
import com.lookie.opluskey.ui.write.setComboStarterKeepLong

// 「等待组合」与「按住组合」两张卡 —— **公共设置页与某一组的「独立设置」页用的是同一份**。
//
//  两张卡里的行、文案、图标、区间、步长本来就该逐字相同（用户口径：不需要特判），所以整张卡
//  只写一遍；两侧的差别全收在 [ComboParamSource] 里 —— **值从哪来、写到哪去、能不能动**。
//  卡片自己不认识「公共」还是「某一组」，也不认识 `Config`：给它一份来源，它照着画。
//
//  **摆法也一样**：两张卡按触发方式分开、各配一个小标题（`COMBO_SECTION_WAIT` /
//  `COMBO_SECTION_HOLD`），公共那一页两张都摆，某一组那一页只摆它自己那一种 —— 显隐由调用方
//  决定，卡片不按「有没有按住组合」自己缩。

/**
 * 两张卡的**状态源** —— 同一个界面的两份落点。
 *
 *  公共那一份来自 `GlobalConfig`、写全局配置（[comboPublicParams]）；某一组那一份来自
 *  `ComboKey`、写回这一组的记录（[comboOwnParams]）。判定侧两处都读
 *  （`Config.comboWindowFor` / `comboHoldMinFor` / `comboSceneOnStarterFor` / `comboStarterKeepLongFor`）。
 *
 * @param windowMs 窗口滑杆**显示**的那个数。某一组那一份给的是它自己存着的那份
 *   （`Config.comboStoredWindowFor`，没写过才退回公共），**不是** `comboWindowFor` 那个「实际生效」
 *   值 —— 取生效值的话，拨一下那颗总开关它就从公共值跳到这一组自己的值，看着像刚拖好的窗口被改了。
 * @param windowDefault 双击滑杆回落的那个数：公共那一份是出厂默认，某一组那一份是当前公共值。
 * @param holdMinMs 同 [windowMs]，这一根是按住下限（`Config.comboStoredHoldMinFor`）。
 * @param holdMinDefault 同 [windowDefault]。
 * @param sceneOnStarter 生效值（`Config.comboSceneOnStarterFor`）—— 开关显示的是「现在按下去会怎样」。
 * @param starterKeepLong 同上（`Config.comboStarterKeepLongFor`）。
 * @param enabled 这几行能不能动。某一组那一份传的是「页面闸门 && 这一组的独立设置开着」。
 * @param disabledHint 灰着被点一下时的说法（`null` = 用整页那句）—— 同一个原因不该在几个地方各算一遍。
 * @param onWindow 写窗口那个数，返回「写盘成没成」：滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 * @param onHoldMin 同上。
 * @param onSceneOnStarter 写场景取值的口径。提示由来源那一侧发（公共走 `writeAndNotice`，
 *   某一组写成功后自己弹一句），卡片不管。
 * @param onStarterKeepLong 同上。
 */
internal class ComboParamSource(
    val windowMs: Int,
    val windowDefault: Int,
    val holdMinMs: Int,
    val holdMinDefault: Int,
    val sceneOnStarter: Boolean,
    val starterKeepLong: Boolean,
    val enabled: Boolean,
    val disabledHint: String? = null,
    val onWindow: (Int) -> Boolean,
    val onHoldMin: (Int) -> Boolean,
    val onSceneOnStarter: (Boolean) -> Unit,
    val onStarterKeepLong: (Boolean) -> Unit,
)

/**
 * 「等待组合」那张卡 —— **等窗口**那一条按法的参数，只有一行：
 * **按键之间的等待**：两颗键最多隔多久才算「一起按下」。
 *
 *  **这一行有代价**，副标题里写明：参与组合的键按下去要先等这么久（等第二颗键会不会来），
 *  这段时间里它自己的单击 / 长按都还没开始判。不参与任何组合的键一点不受影响 ——
 *  这一点也得说，否则用户会以为整页都变慢。
 *
 *  按住那两档的成员根本不按它等：判定侧只把认窗口的档算进「值得先等一个窗口」的集合
 *  （`ConfigChannel.deriveCombos` 的 `windowMembers`）。
 *
 *  **「无操作时执行按键命令」不在这张卡里**：它说的是「窗口按出来了、可这一格没绑动作」，
 *  与「等多久」不是同一件事，入口在组合列表页那一侧（`Config.comboPassthroughFor` 的判据仍在）。
 */
@Composable
internal fun SettingsPage.WaitComboCard(source: ComboParamSource) {
    CardList(
        rows =
            listOf(
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                title = I18n.get(Strings.COMBO_WINDOW_TITLE, "按键之间的等待"),
                                summary = I18n.get(Strings.COMBO_WINDOW_DESC, "调大后，参与组合的按键等待更久"),
                                //  「等一段时间再继续」那一颗钟 + 暂停（与「命令间隔时长」的播放记号分开）
                                icon = PageIcons.ComboWait,
                                min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                                max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                                value = source.windowMs,
                                default = source.windowDefault,
                                //  10ms 一档：20~1000 之间 98 档。放宽到 1 秒是为了手慢 / 三颗的组合留余地，
                                //  精度仍然留在 10ms 这一档（细调靠点轨道 + 手动输入，见 `tapSliderToSeek`）
                                step = 10,
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                                //  行尾写「80 ms」—— 这一行问的就是「等多久」，不写数看不出来
                                valueText = { "$it ms" },
                            ),
                        onCommit = source.onWindow,
                    )
                },
            ),
    )
}

/**
 * 「按住组合」那张卡 —— **先按住一颗再按另一颗**那两条按法（`MODE_HOLD_FIRST` /
 * `MODE_HOLD_SECOND`）的参数：
 *
 *  1. **首键按住时长**：按住那两档的下限 —— 首键要按满这么久、且还没抬手，第二颗落下才算这一档。
 *     两颗键几乎同时落下（实测相差 0~40ms，谁先上报看硬件）不算「按住」：那种「一起按」归
 *     等窗口那一档，这一行划的正是那条线。上限 500ms 与组合长按阈值同量级：调得比那颗键自己的
 *     长按还宽时，它早就先判出长按、按住那条本来就作废了（`ComboDetector.longPressPassed`）。
 *  2. **系统场景在首键按下时固定**：按下那一刻固定（开，默认），还是凑成那一刻再取（关）。
 *     取早了不怕「按住期间换了应用」，取晚了才反映「刚切过去」；两种口径各有道理，所以给成开关。
 *  3. **首键松手后仍执行长按**：组合动作走完之后，首键自己那颗长按还留不留。默认关 =
 *     一次按压只执行一个动作；开着时这一次按压被**两件事共享** —— 所以图标是 `pointer-share`，
 *     而不是「松手执行额外动作」那颗 `pointer-up`。
 *
 *  **不按「有没有按住组合」显隐**：这一整张卡都是参数，没有哪一行跟着列表内容变。
 */
@Composable
internal fun SettingsPage.HoldComboCard(source: ComboParamSource) {
    CardList(
        rows =
            listOf(
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                title = I18n.get(Strings.COMBO_HOLD_MIN_TITLE, "首键按住时长"),
                                summary =
                                    I18n.get(
                                        Strings.COMBO_HOLD_MIN_DESC,
                                        "先按住的那颗按满这么久、还没抬手，再按另一颗才算组合",
                                    ),
                                //  「撑满就定格」那只钟（`clock-stop`，与「长按延迟」那颗闪电分开）
                                icon = PageIcons.ComboHold,
                                min = GlobalConfig.COMBO_HOLD_MIN_MIN_MS,
                                max = GlobalConfig.COMBO_HOLD_MIN_MAX_MS,
                                value = source.holdMinMs,
                                default = source.holdMinDefault,
                                //  10ms 一档：40~500 之间 47 档，够细也够短
                                step = 10,
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                                valueText = { "$it ms" },
                            ),
                        onCommit = source.onHoldMin,
                    )
                },
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
                    checked = source.sceneOnStarter,
                    enabled = source.enabled,
                    disabledHint = source.disabledHint,
                    onCheckedChange = source.onSceneOnStarter,
                ),
                CardRow.Switch(
                    title = I18n.get(Strings.COMBO_STARTER_KEEP_LONG_TITLE, "首键松手后仍执行长按"),
                    summaryOn =
                        I18n.get(
                            Strings.COMBO_STARTER_KEEP_LONG_DESC_ON,
                            "组合动作执行后，首键松手时再执行它自己的长按动作",
                        ),
                    summaryOff =
                        I18n.get(
                            Strings.COMBO_STARTER_KEEP_LONG_DESC_OFF,
                            "组合触发后，首键自己的长按被丢弃（一次按压只执行一个动作）",
                        ),
                    icon = PageIcons.ComboKeepLong,
                    checked = source.starterKeepLong,
                    enabled = source.enabled,
                    disabledHint = source.disabledHint,
                    onCheckedChange = source.onStarterKeepLong,
                ),
            ),
    )
}

/**
 * **公共**那一份来源 —— 值读 `GlobalConfig`，写入落到全局配置（没单独设过的组都用它）。
 *
 *  提示由写入那几个函数自己发（`writeAndNotice`）：与页面上其余全局项同一套回执。
 */
internal fun SettingsPage.comboPublicParams(enabled: Boolean): ComboParamSource {
    val cfg = configState.config.value
    return ComboParamSource(
        windowMs = cfg.global.comboWindowMs,
        //  双击滑杆回落的是**出厂默认**（某一组那一份回落的是当前公共值）
        windowDefault = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
        holdMinMs = cfg.global.comboHoldMinMs,
        holdMinDefault = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
        sceneOnStarter = cfg.global.comboSceneOnStarter,
        starterKeepLong = cfg.global.comboStarterKeepLong,
        enabled = enabled,
        onWindow = { setComboWindow(it) },
        onHoldMin = { setComboHoldMin(it) },
        onSceneOnStarter = { setComboSceneOnStarter(it) },
        onStarterKeepLong = { setComboStarterKeepLong(it) },
    )
}

/**
 * **某一组自己**那一份来源 —— 值读这一组的记录（没写过的那几项退回公共），写入落回这一组。
 *
 *  两个滑杆给的是 `comboStoredXxxFor` 而不是 `comboXxxFor`：显示与写入同源，拨一下总开关不会跳值。
 *  写入成功后自己弹一句回执（位用 [HINT_SLOT_COMBO]，那几行就在这一页上）。
 */
internal fun SettingsPage.comboOwnParams(
    combo: ComboKey,
    enabled: Boolean,
    disabledHint: String?,
): ComboParamSource {
    val cfg = configState.config.value
    return ComboParamSource(
        windowMs = cfg.comboStoredWindowFor(combo),
        windowDefault = cfg.global.comboWindowMs,
        holdMinMs = cfg.comboStoredHoldMinFor(combo),
        holdMinDefault = cfg.global.comboHoldMinMs,
        sceneOnStarter = cfg.comboSceneOnStarterFor(combo),
        starterKeepLong = cfg.comboStarterKeepLongFor(combo),
        enabled = enabled,
        disabledHint = disabledHint,
        onWindow = { setComboOwnWindow(combo.id, it) },
        onHoldMin = { setComboOwnHoldMin(combo.id, it) },
        onSceneOnStarter = { on -> setOwnSceneOnStarter(combo.id, on) },
        onStarterKeepLong = { on -> setOwnStarterKeepLong(combo.id, on) },
    )
}

/**
 * 写这一组的「系统场景什么时候取」—— 写下去就是这一组自己的决定，不再跟随公共设置。
 *
 *  与上面两个滑杆同一条：提示只在这一页发（`switchNotice` + [HINT_SLOT_COMBO]）。
 */
private fun SettingsPage.setOwnSceneOnStarter(
    id: String,
    on: Boolean,
) {
    val ok =
        updateCombo(id, "scene_on_starter=${if (on) 1 else 0}") {
            it.copy(sceneOnStarter = on)
        }
    if (ok) {
        hint(
            switchNotice(I18n.get(Strings.COMBO_SCENE_ON_STARTER_TITLE, "系统场景在首键按下时固定"), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}

/** 写这一组的「首键松手后仍执行长按」—— 与 [setOwnSceneOnStarter] 同一个形状。 */
private fun SettingsPage.setOwnStarterKeepLong(
    id: String,
    on: Boolean,
) {
    val ok =
        updateCombo(id, "starter_keep_long=${if (on) 1 else 0}") {
            it.copy(starterKeepLong = on)
        }
    if (ok) {
        hint(
            switchNotice(I18n.get(Strings.COMBO_STARTER_KEEP_LONG_TITLE, "首键松手后仍执行长按"), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}
