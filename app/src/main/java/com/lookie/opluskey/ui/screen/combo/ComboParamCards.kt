package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboWindows
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
import com.lookie.opluskey.ui.write.setComboFixedWindowStart
import com.lookie.opluskey.ui.write.setComboHoldFixedWindowStart
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
 *  （`Config.comboWindowsFor` / `comboHoldMinFor` / `comboSceneOnStarterFor` / `comboStarterKeepLongFor`）。
 *
 * @param windows 这一份来源的**四格窗口**（等待 / 按住 × 固定窗口起点 开 / 关）。某一组那一份给的是
 *   它自己存着的那份（`Config.comboStoredWindowsFor`，没写过的那几格才退回公共），**不是**
 *   `comboWindowsFor` 那个「实际生效」值 —— 取生效值的话，拨一下那颗总开关它就从公共值跳到
 *   这一组自己的值，看着像刚拖好的窗口被改了。
 * @param windowDefaults 双击滑杆回落的四格：公共那一份是出厂默认，某一组那一份是当前公共值。
 * @param holdMinMs 这一根是按住下限（`Config.comboStoredHoldMinFor`）。
 * @param holdMinDefault 同 [windowDefaults]。
 * @param sceneOnStarter 生效值（`Config.comboSceneOnStarterFor`）—— 开关显示的是「现在按下去会怎样」。
 * @param starterKeepLong 同上（`Config.comboStarterKeepLongFor`）。
 * @param starters **两颗「固定窗口起点」开关的取值** —— 等待组合那一侧读
 *   `Config.comboFixedWindowStartFor`、按住组合那一侧读 `Config.comboHoldFixedWindowStartFor`。
 *   两者是**两个变量**：一种按法把窗口改成「整串共用一个」，另一种不跟着变。
 *   每一颗同时决定**它那张卡的哪一格窗口**上滑杆（见 [WaitComboCard] / [HoldComboCard]）。
 * @param enabled 这几行能不能动。某一组那一份传的是「页面闸门 && 这一组的独立设置开着」。
 * @param disabledHint 灰着被点一下时的说法（`null` = 用整页那句）—— 同一个原因不该在几个地方各算一遍。
 * @param onWaitWindow 写「等待组合、起点开」那一格，返回「写盘成没成」：滑杆失败时要拨回原值
 *   （见 `SliderRow` 的 `commit`）。下面三支同形 —— **四格各一支**，卡片按当前那格挑一支，
 *   免得「该写哪一格」的判断散在调用点上。
 * @param onHoldMin 同上。
 * @param onSceneOnStarter 写场景取值的口径。提示由来源那一侧发（公共走 `writeAndNotice`，
 *   某一组写成功后自己弹一句），卡片不管。
 * @param onStarterKeepLong 同上。
 * @param onWaitFixedWindowStart 写等待组合那颗开关 / @param onHoldFixedWindowStart 写按住组合那颗。
 */
internal class ComboParamSource(
    val windows: ComboWindows,
    val windowDefaults: ComboWindows,
    val holdMinMs: Int,
    val holdMinDefault: Int,
    val sceneOnStarter: Boolean,
    val starterKeepLong: Boolean,
    /** 等待组合那张卡的「固定窗口起点」（`Config.comboFixedWindowStartFor`）。 */
    val waitFixedWindowStart: Boolean,
    /** 按住组合那张卡的「固定窗口起点」（`Config.comboHoldFixedWindowStartFor`）。 */
    val holdFixedWindowStart: Boolean,
    val enabled: Boolean,
    val disabledHint: String? = null,
    val onWaitWindow: (Int) -> Boolean,
    val onWaitPair: (Int) -> Boolean,
    val onHoldWindow: (Int) -> Boolean,
    val onHoldFollower: (Int) -> Boolean,
    val onHoldMin: (Int) -> Boolean,
    val onSceneOnStarter: (Boolean) -> Unit,
    val onStarterKeepLong: (Boolean) -> Unit,
    val onWaitFixedWindowStart: (Boolean) -> Unit,
    val onHoldFixedWindowStart: (Boolean) -> Unit,
)

/**
 * 「等待组合」那张卡 —— **等窗口**那一条按法的参数，两行：**固定窗口起点** + **等待窗口**。
 *
 *  行序与原卡一致：原来第一行那根「按键之间的等待」整行删掉，剩下那一颗开关与那一根窗口
 *  原样留在原位（下面那一根改名叫「等待窗口」）。
 *
 *  等待窗口这一根**同时挂着两格数**（`ComboWindows.wait` / `waitPair`），显示与写入都用**当前那格**：
 *  固定窗口起点开着时是「整串共用一个窗口」，关着时是「相邻两颗各一个」。两者分开存
 *  （用户口径：正反分别存），但同一时刻只有一格在起作用 —— 摆两根让人猜哪根算数，
 *  与单击设置里「等满多击窗口」那一根同一条做法。
 *  已经删掉的「按键之间的等待」就是「关」的那一格，现在它随开关露面，不再是一个独立旋钮。
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
    //  这一根滑杆写的是哪一格：开 = 整串（`wait`），关 = 相邻两颗（`waitPair`）。
    //  读的是**等待组合那一侧**那颗开关（`waitFixedWindowStart`），按住组合那颗与此无关
    val wholeString = source.waitFixedWindowStart
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.COMBO_FIXED_WINDOW_START_TITLE),
                    summaryOn =
                        I18n.get(Strings.COMBO_FIXED_WINDOW_START_DESC_ON),
                    summaryOff =
                        I18n.get(Strings.COMBO_FIXED_WINDOW_START_DESC_OFF),
                    //  与单击设置那张卡的「固定窗口起点」同一颗 `window`：两处管的是同一件事
                    icon = PageIcons.CollectWindow,
                    checked = source.waitFixedWindowStart,
                    enabled = source.enabled,
                    disabledHint = source.disabledHint,
                    onCheckedChange = source.onWaitFixedWindowStart,
                ),
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                title = I18n.get(Strings.COMBO_WINDOW_TITLE),
                                //  **不画副标题**：这一格管「整串」还是「相邻两颗」由上面那颗开关说清，
                                //  这一根只回答「等多久」
                                //  「等一段时间再继续」那一颗钟 + 暂停（与「命令间隔时长」的播放记号分开）
                                icon = PageIcons.ComboWait,
                                min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                                max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                                value = if (wholeString) source.windows.wait else source.windows.waitPair,
                                default = if (wholeString) source.windowDefaults.wait else source.windowDefaults.waitPair,
                                //  取值 10ms 一档（20~1000 之间 98 档）—— 这个数同时是「算不算凑齐」与
                                //  「第一颗键等多久才回落单击」，细档只影响手输与落盘。
                                //  **拖动 50ms 一档**：98 档铺在一根轨道上手指根本分不开，粗拖 + 手输
                                //  才是这一行的用法（见 `SliderSpec.dragStep`）
                                step = 10,
                                dragStep = 50,
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                                //  行尾写「80 ms」—— 这一行问的就是「等多久」，不写数看不出来
                                valueText = { "$it ms" },
                            ),
                        onCommit = if (wholeString) source.onWaitWindow else source.onWaitPair,
                    )
                },
            ),
    )
}

/**
 * 「按住组合」那张卡 —— **先按住一颗再按另一颗**那两条按法（`MODE_HOLD_FIRST` /
 * `MODE_HOLD_SECOND`）的参数：
 *
 *  1. **固定窗口起点 + 等待窗口**（头两行）：后续成员要在窗口内陆续落完 —— 起点开着时从首个成员
 *     按下起算一个，关着则每颗重新等一个。**与「等待组合」那张卡那一根不是同一个值**：
 *     两档各有自己的一格（`ComboWindows.hold` / `holdFollower`），调宽「按住组合的成员接收」
 *     不会连带拖钝「等待组合的收集」。
 *  2. **首键按住时长**：按住那两档的下限 —— 首键要按满这么久、且还没抬手，第二颗落下才算这一档。
 *     两颗键几乎同时落下（实测相差 0~40ms，谁先上报看硬件）不算「按住」：那种「一起按」归
 *     等窗口那一档，这一行划的正是那条线。上限 500ms 与组合长按阈值同量级：调得比那颗键自己的
 *     长按还宽时，它早就先判出长按、按住那条本来就作废了（`ComboDetector.longPressPassed`）。
 *  3. **系统场景在首键按下时固定**：按下那一刻固定（开，默认），还是凑成那一刻再取（关）。
 *     取早了不怕「按住期间换了应用」，取晚了才反映「刚切过去」；两种口径各有道理，所以给成开关。
 *  4. **首键松手后仍执行长按**：组合动作走完之后，首键自己那颗长按还留不留。默认关 =
 *     一次按压只执行一个动作；开着时这一次按压被**两件事共享** —— 所以图标是 `pointer-share`，
 *     而不是「松手执行额外动作」那颗 `pointer-up`。
 *
 *  **不按「有没有按住组合」显隐**：这一整张卡都是参数，没有哪一行跟着列表内容变。
 */
@Composable
internal fun SettingsPage.HoldComboCard(source: ComboParamSource) {
    //  这一根滑杆写的是哪一格：开 = 从首个成员起算一个（`hold`），关 = 每颗各自往前（`holdFollower`）。
    //  读的是**按住组合那一侧**那颗开关（`holdFixedWindowStart`），等待组合那颗与此无关
    val wholeString = source.holdFixedWindowStart
    CardList(
        rows =
            listOf(
                //  这两行是**这一档自己的窗口**（按住组合那两格：起点开关 + 窗口滑杆），
                //  与等待组合那张卡不是同一个值
                CardRow.Switch(
                    //  与「等待组合」那张卡**同名、同文案，但是另一颗开关、另一个变量**
                    //  （`Config.comboHoldFixedWindowStartFor`）：一种按法改成整串共用一个，
                    //  另一种不该跟着变，所以两颗各存各的
                    title = I18n.get(Strings.COMBO_FIXED_WINDOW_START_TITLE),
                    summaryOn =
                        I18n.get(Strings.COMBO_FIXED_WINDOW_START_DESC_ON),
                    summaryOff =
                        I18n.get(Strings.COMBO_FIXED_WINDOW_START_DESC_OFF),
                    icon = PageIcons.CollectWindow,
                    checked = source.holdFixedWindowStart,
                    enabled = source.enabled,
                    disabledHint = source.disabledHint,
                    onCheckedChange = source.onHoldFixedWindowStart,
                ),
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                title = I18n.get(Strings.COMBO_FIXED_WINDOW_TITLE),
                                //  **不画副标题**：与「等待组合」那一根同一套 —— 这一格管「整串」还是
                                //  「每颗各自等」由上面那颗开关说清
                                icon = PageIcons.ComboWait,
                                min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                                max = GlobalConfig.COMBO_WINDOW_MAX_MS,
                                value = if (wholeString) source.windows.hold else source.windows.holdFollower,
                                default = if (wholeString) source.windowDefaults.hold else source.windowDefaults.holdFollower,
                                //  与「等待组合」那一根同一套：取值 10ms、拖动 50ms（理由见那边）
                                step = 10,
                                dragStep = 50,
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                                valueText = { "$it ms" },
                            ),
                        onCommit = if (wholeString) source.onHoldWindow else source.onHoldFollower,
                    )
                },
                //  首键按住时长摆在**窗口那两行之后**：先答「成员多快接上」（窗口），再答
                //  「首键要按住多久才算数」—— 两件都是时序，但后者是「凑成」的门槛，
                //  放在窗口后面读起来是「窗口定完，门槛在这」。
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                title = I18n.get(Strings.COMBO_HOLD_MIN_TITLE),
                                summary =
                                    I18n.get(Strings.COMBO_HOLD_MIN_DESC),
                                //  「撑满就定格」那只钟（`clock-stop`，与「长按延迟」那颗闪电分开）
                                icon = PageIcons.ComboHold,
                                min = GlobalConfig.COMBO_HOLD_MIN_MIN_MS,
                                max = GlobalConfig.COMBO_HOLD_MIN_MAX_MS,
                                value = source.holdMinMs,
                                default = source.holdMinDefault,
                                //  50ms 一档：50~500 之间 10 档 —— 这一位只判「按住够不够久」，
                                //  粗档够用；`dragStep` 默认跟 `step` 走，拖动也是同一格
                                step = 50,
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                                valueText = { "$it ms" },
                            ),
                        onCommit = source.onHoldMin,
                    )
                },
                CardRow.Switch(
                    title = I18n.get(Strings.COMBO_SCENE_ON_STARTER_TITLE),
                    summaryOn =
                        I18n.get(Strings.COMBO_SCENE_ON_STARTER_DESC_ON),
                    summaryOff =
                        I18n.get(Strings.COMBO_SCENE_ON_STARTER_DESC_OFF),
                    icon = PageIcons.ScenePin,
                    checked = source.sceneOnStarter,
                    enabled = source.enabled,
                    disabledHint = source.disabledHint,
                    onCheckedChange = source.onSceneOnStarter,
                ),
                CardRow.Switch(
                    title = I18n.get(Strings.COMBO_STARTER_KEEP_LONG_TITLE),
                    summaryOn =
                        I18n.get(Strings.COMBO_STARTER_KEEP_LONG_DESC_ON),
                    summaryOff =
                        I18n.get(Strings.COMBO_STARTER_KEEP_LONG_DESC_OFF),
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
        windows =
            ComboWindows(
                wait = cfg.global.comboWaitWindowMs,
                waitPair = cfg.global.comboWaitPairMs,
                hold = cfg.global.comboHoldWindowMs,
                holdFollower = cfg.global.comboHoldFollowerMs,
            ),
        //  双击滑杆回落的是**出厂默认**（某一组那一份回落的是当前公共值）
        windowDefaults =
            ComboWindows(
                wait = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                waitPair = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                hold = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                holdFollower = GlobalConfig.DEFAULT_COMBO_HOLD_FOLLOWER_MS,
            ),
        holdMinMs = cfg.global.comboHoldMinMs,
        holdMinDefault = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
        sceneOnStarter = cfg.global.comboSceneOnStarter,
        starterKeepLong = cfg.global.comboStarterKeepLong,
        //  两颗开关、两个变量：等待组合读 `comboFixedWindowStart`，按住组合读 `comboHoldFixedWindowStart`
        waitFixedWindowStart = cfg.global.comboFixedWindowStart,
        holdFixedWindowStart = cfg.global.comboHoldFixedWindowStart,
        enabled = enabled,
        onWaitWindow = { setComboWaitWindow(it) },
        onWaitPair = { setComboWaitPair(it) },
        onHoldWindow = { setComboHoldWindow(it) },
        onHoldFollower = { setComboHoldFollower(it) },
        onHoldMin = { setComboHoldMin(it) },
        onSceneOnStarter = { setComboSceneOnStarter(it) },
        onStarterKeepLong = { setComboStarterKeepLong(it) },
        onWaitFixedWindowStart = { setComboFixedWindowStart(it) },
        onHoldFixedWindowStart = { setComboHoldFixedWindowStart(it) },
    )
}

/**
 * **某一组自己**那一份来源 —— 值读这一组的记录（没写过的那几格退回公共），写入落回这一组。
 *
 *  四格给的是 `comboStoredWindowsFor` 而不是 `comboWindowsFor`：显示与写入同源，拨一下总开关
 *  不会跳值。**四格各写各的**（用户口径：独立设置里也正反分别处理）—— 卡片按当前那格挑一支，
 *  于是「开着起点调的那一根」与「关着起点调的那一根」是两个落点，互不改写。
 *  写入成功后自己弹一句回执（位用 [HINT_SLOT_COMBO]，那几行就在这一页上）。
 */
internal fun SettingsPage.comboOwnParams(
    combo: ComboKey,
    enabled: Boolean,
    disabledHint: String?,
): ComboParamSource {
    val cfg = configState.config.value
    val global =
        ComboWindows(
            wait = cfg.global.comboWaitWindowMs,
            waitPair = cfg.global.comboWaitPairMs,
            hold = cfg.global.comboHoldWindowMs,
            holdFollower = cfg.global.comboHoldFollowerMs,
        )
    return ComboParamSource(
        windows = cfg.comboStoredWindowsFor(combo),
        //  回落的是**当前公共值**：这一组没写过的那几格显示的就是它
        windowDefaults = global,
        holdMinMs = cfg.comboStoredHoldMinFor(combo),
        holdMinDefault = cfg.global.comboHoldMinMs,
        sceneOnStarter = cfg.comboSceneOnStarterFor(combo),
        starterKeepLong = cfg.comboStarterKeepLongFor(combo),
        //  两颗各自取生效值：这一组给「等待 / 按住」两档各存过一份，两个 `...For` 分开读
        waitFixedWindowStart = cfg.comboFixedWindowStartFor(combo),
        holdFixedWindowStart = cfg.comboHoldFixedWindowStartFor(combo),
        enabled = enabled,
        disabledHint = disabledHint,
        onWaitWindow = { setComboOwnWaitWindow(combo.id, it) },
        onWaitPair = { setComboOwnWaitPair(combo.id, it) },
        onHoldWindow = { setComboOwnHoldWindow(combo.id, it) },
        onHoldFollower = { setComboOwnHoldFollower(combo.id, it) },
        onHoldMin = { setComboOwnHoldMin(combo.id, it) },
        onSceneOnStarter = { on -> setOwnSceneOnStarter(combo.id, on) },
        onStarterKeepLong = { on -> setOwnStarterKeepLong(combo.id, on) },
        onWaitFixedWindowStart = { on -> setOwnFixedWindowStart(combo.id, on) },
        onHoldFixedWindowStart = { on -> setOwnHoldFixedWindowStart(combo.id, on) },
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
            switchNotice(I18n.get(Strings.COMBO_SCENE_ON_STARTER_TITLE), on),
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
            switchNotice(I18n.get(Strings.COMBO_STARTER_KEEP_LONG_TITLE), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}

/**
 * 写这一组的「固定窗口起点」—— **等待组合那一侧**（`ComboKey.fixedWindowStart`）。
 *
 *  与 [setOwnHoldFixedWindowStart] 是**两个落点**（用户口径：正反分别存）：这一支只改
 *  `MODE_WAIT` 那一档数窗口的方式。
 */
private fun SettingsPage.setOwnFixedWindowStart(
    id: String,
    on: Boolean,
) {
    val ok =
        updateCombo(id, "fixed_window_start=${if (on) 1 else 0}") {
            it.copy(fixedWindowStart = on)
        }
    if (ok) {
        hint(
            switchNotice(I18n.get(Strings.COMBO_FIXED_WINDOW_START_TITLE), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}

/**
 * 写这一组的「固定窗口起点」—— **按住组合那一侧**（`ComboKey.holdFixedWindowStart`）。
 *
 *  与 [setOwnFixedWindowStart] 同一个形状、同一个回执句，但落的是另一个字段：
 *  这一组里「等待组合」与「按住组合」两种按法可以各用各的取窗口方式。
 */
private fun SettingsPage.setOwnHoldFixedWindowStart(
    id: String,
    on: Boolean,
) {
    val ok =
        updateCombo(id, "hold_fixed_window_start=${if (on) 1 else 0}") {
            it.copy(holdFixedWindowStart = on)
        }
    if (ok) {
        hint(
            switchNotice(I18n.get(Strings.COMBO_FIXED_WINDOW_START_TITLE), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}
