package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboWindows
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.isOn
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.text.WindowLabel
import com.lookie.opluskey.ui.model.text.fixedWindowStartTitle
import com.lookie.opluskey.ui.model.text.holdWindowLabelOf
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.screen.binding.DelayParamSource
import com.lookie.opluskey.ui.write.setComboFixedWindowStart
import com.lookie.opluskey.ui.write.setComboHoldFixedWindowStart
import com.lookie.opluskey.ui.write.setComboHoldMin
import com.lookie.opluskey.ui.write.setComboSceneOnStarter
import com.lookie.opluskey.ui.write.setComboStarterKeepLong
import com.lookie.opluskey.ui.write.setGlobalComboMemberThenLongPress

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
 *   它自己存着的那份（`Config.comboStoredWindowsFor`，没写过的那几格取出厂默认），**不是**
 *   `comboWindowsFor` 那个「实际生效」值 —— 取生效值的话，拨一下那颗总开关它就从公共值跳到
 *   这一组自己的值，看着像刚拖好的窗口被改了。
 *   有序无序**共用这一份**：档位要不要按先后落，与「多久之内落完」是两件事。
 * @param windowDefaults 双击滑杆回落的四格：**两份都是出厂默认**（`ComboWindows.DEFAULT`）。
 * @param holdMinMs 这一根是按住下限（`Config.comboStoredHoldMinFor`）。
 * @param holdMinDefault 同 [windowDefaults]（`GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS`）。
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
 * @param holdWindowLive 按住那张卡那两行**有没有实际作用**（见 [ComboParamSource.holdWindowLive]）。
 * @param onWaitFixedWindowStart 写等待组合那颗开关 / @param onHoldFixedWindowStart 写按住组合那颗。
 */
internal class ComboParamSource(
    val windows: ComboWindows,
    val windowDefaults: ComboWindows,
    val holdMinMs: Int,
    val holdMinDefault: Int,
    val sceneOnStarter: Boolean,
    val starterKeepLong: Boolean,
    /** 等待组合那张卡的「固定窗口起点」（这一档自己那份；生效值见 `Config.comboFixedWindowStartFor`）。 */
    val waitFixedWindowStart: Boolean,
    /** 按住组合那张卡的「固定窗口起点」（同上，生效值见 `Config.comboHoldFixedWindowStartFor`）。 */
    val holdFixedWindowStart: Boolean,
    /**
     * 按住那张卡那颗开关标题的前缀（见 [WindowLabel]）；滑杆同样不带前缀。
     *
     *  公共那一页是 [WindowLabel.INTERVAL_MULTI]（「按键间隔和多击…」）：那里的默认值同时服务
     *  三颗以上的按住档与两键档。
     *
     *  独立设置页只写自己的那一个：两键的按住组合写「多击…」（那两行在那档上只服务成员键
     *  自己的连击链），其余一律「按键间隔…」—— 有序与不分顺序都算这一类。
     */
    val holdWindowLabel: WindowLabel,
    /**
     * 按住那张卡那两行（「固定窗口起点」+「等待窗口」）**有没有实际作用**。
     *
     *  它们服务两件事，任一件成立就不空转：
     *  · **成员之间的接收**（`ComboDetector.multiFor`，3 颗起）：`hold` / `holdFollower` 量的是
     *    后续成员之间接得多快；
     *  · **成员连击**（`Config.comboMemberChain`）：那颗唯一成员键的双击 / 三击 / 单击后长按，
     *    窗口与判定时机读的也是这两行（用户口径：这个设置就是给多击手势用的）。
     *
     *  两件都不成立时（只有一颗成员、且成员连击一档都没开）没有可量的间隔，拨了什么都不变。
     *  `false` ⇒ 界面把这两行置灰并说明（[Strings.COMBO_HOLD_WINDOW_IDLE]），
     *  不摆一根空转的旋钮。
     *
     *  **这条判据只对独立设置那一份成立**（公共那一份取默认 `true`）：公共值是「没单独设过的组」与
     *  还没建出来的那一档的默认值，按当前有没有档用得到来禁用它，等于要求先建档再调它的默认值；
     *  而回落走的是出厂默认（`Config.comboWindowsFor`），也不是公共 —— 用「有没有档在用它」推公共的空转
     *  本来就不成立。
     */
    val holdWindowLive: Boolean = true,
    /**
     * 成员那三档「后长按」共用的**按住时长**（「连击后长按延迟」）—— `null` = 这张卡不摆这一行。
     *
     *  两侧都给值，**同一个参数只有一个入口**：公共那一份是「没单独设过的组合」用的默认值，
     *  某一组那一份就是这一档自己的（与单键那张卡的第三行同一条组织，见 `ClickDelayCard`）。
     *
     *  `null` 只出现在**用不到它的组合**上：多成员组合 / 等待组合没有「哪一颗成员在连击」，
     *  这一根拨了什么都不变。
     */
    val memberDelay: DelayParamSource? = null,
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
 *  有序无序**共用这两行**：档位要不要按配置的先后落，与「多久之内落完」是两件事，
 *  所以只有一套数（用户口径：合并掉原来那对「顺序组合…」行）。
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
                    //  等待组合两种（普通 / 有序）量的是同一件事：两次按下之间的间隔 ⇒ 都写
                    //  「按键间隔…」；前缀只标在开关这一行，下面那根滑杆不带前缀。
                    title = I18n.get(WindowLabel.INTERVAL.fixedWindowStartTitle()),
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
                                //  上限按**当前写哪一格**取：固定等 1000 / 逐次等 500
                                //  （见那两个常量 —— 逐次等会被 (n-1) 放大，所以它更窄）
                                max =
                                    if (wholeString) {
                                        GlobalConfig.COMBO_WINDOW_MAX_MS
                                    } else {
                                        GlobalConfig.COMBO_PAIR_MAX_MS
                                    },
                                value = if (wholeString) source.windows.wait else source.windows.waitPair,
                                default = if (wholeString) source.windowDefaults.wait else source.windowDefaults.waitPair,
                                //  取值 10ms 一档（固定等 50~1000 / 逐次等 50~500）—— 这个数同时是
                                //  「算不算凑齐」与「第一颗键等多久才回落单击」，细档只影响手输与落盘。
                                //  **拖动 50ms 一档**：几十档铺在一根轨道上手指根本分不开，粗拖 + 手输
                                //  才是这一行的用法（见 `SliderSpec.dragStep`）
                                step = 10,
                                dragStep = 50,
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                                //  行尾写「80ms」—— 这一行问的就是「等多久」，不写数看不出来
                                valueText = { I18n.format(Strings.COMMON_TIME_MS, it) },
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
 *     不会连带拖钝「等待组合的收集」。有序无序共用这两行。
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
    //  这两行服务两件事（见 [ComboParamSource.holdWindowLive]）：**成员之间的接收**
    //  （两颗以上成员才成立）与**成员连击的窗口**（那颗唯一成员键的双击 / 三击 / 单击后长按，
    //  见 `Config.comboMemberChain`）—— 两件都不成立时才量不到任何东西，置灰并说明：
    //  拨得动却不改行为比摆着不给拨更糟。**多击手势开着就是一件成立**，那时必须可拨。
    //  页面级那句原因（独立设置没开）优先：那一条是用户当下能动手改的。
    val windowHint = if (source.holdWindowLive) source.disabledHint else (source.disabledHint ?: I18n.get(Strings.COMBO_HOLD_WINDOW_IDLE))
    CardList(
        rows =
            listOf(
                //  这两行是**这一档自己的窗口**（按住组合那两格：起点开关 + 窗口滑杆），
                //  与等待组合那张卡不是同一个值
                CardRow.Switch(
                    //  与「等待组合」那张卡**同名、另一颗开关、另一个变量**
                    //  （`Config.comboHoldFixedWindowStartFor`）：一种按法改成整串共用一个，
                    //  另一种不该跟着变，所以两颗各存各的。
                    //  **副标题是这一档自己那两句**：按住档的窗口从**第一个成员**按下起算，
                    //  首键按住多久都不计时（`ComboDetector.multiFor` 的 `followers.firstOrNull()`）；
                    //  借用等待卡那两句「第一颗按下就开始计时」会把首键算进去，那是错的。
                    //
                    //  前缀由来源给（见 [ComboParamSource.holdWindowLabel]）：公共那页写
                    //  「按键间隔和多击…」，独立设置页只写这一档自己那一个。
                    title = I18n.get(source.holdWindowLabel.fixedWindowStartTitle()),
                    summaryOn = I18n.get(Strings.COMBO_HOLD_FIXED_WINDOW_DESC_ON),
                    summaryOff = I18n.get(Strings.COMBO_HOLD_FIXED_WINDOW_DESC_OFF),
                    icon = PageIcons.CollectWindow,
                    checked = source.holdFixedWindowStart,
                    enabled = source.enabled && source.holdWindowLive,
                    disabledHint = windowHint,
                    onCheckedChange = source.onHoldFixedWindowStart,
                ),
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                //  滑杆标题**不随上面那颗开关的前缀变**，恒为「等待窗口」：前缀标的是
                                //  「这一格量什么」（按键间隔 / 多击），标题说的是「这一格本身」。
                                //  两键按住档上它就是多击窗口，而这个数承载的仍是「固定窗口起点」
                                //  开 / 关那两格（整串一个 / 每颗各自一个）—— 名字不必跟着换。
                                title = I18n.get(Strings.COMBO_WINDOW_TITLE),
                                //  这一格管「整串」还是「每颗各自等」由上面那颗开关说清，不另写副标题。
                                //  **不写「成员连击也用它」**：`memberGestures` 只在两键按住档上非空，
                                //  而那一档这一格本来就**只**服务成员连击（`Config.comboMemberChain`；
                                //  「成员之间接收」要三颗起）—— 上面的开关已经叫「多击固定窗口起点」，
                                //  再补一句是同义反复。三颗以上那一档读的是「成员之间接收」，与连击无关。
                                icon = PageIcons.ComboWait,
                                min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                                //  同「等待组合」那一根：上限按当前那一格取（固定等 1000 / 逐次等 500）
                                max =
                                    if (wholeString) {
                                        GlobalConfig.COMBO_WINDOW_MAX_MS
                                    } else {
                                        GlobalConfig.COMBO_PAIR_MAX_MS
                                    },
                                value = if (wholeString) source.windows.hold else source.windows.holdFollower,
                                default = if (wholeString) source.windowDefaults.hold else source.windowDefaults.holdFollower,
                                //  与「等待组合」那一根同一套：取值 10ms、拖动 50ms（理由见那边）
                                step = 10,
                                dragStep = 50,
                                enabled = source.enabled && source.holdWindowLive,
                                disabledHint = windowHint,
                                valueText = { I18n.format(Strings.COMMON_TIME_MS, it) },
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
                                valueText = { I18n.format(Strings.COMMON_TIME_MS, it) },
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
            ) + memberDelayRows(source),
    )
}

/**
 * 「连击后长按延迟」那一行 —— 成员那三档「后长按」共用的按住时长。
 *
 *  与单键那张卡的第三行（`ClickDelayCard` 里那根）同一件事、同一套刻度：先点了几下不影响
 *  手按下去的节奏，所以三档只有这一根；10ms 取值 / 50ms 粗拖也与长按那一档同一手感。
 *  摆在按住卡的最后一行 —— 与单键那张卡把这一根放在末尾同一个读法：窗口 / 门槛定完，再定按住多久。
 *
 *  两页共用这一份：公共那一页给公共值、某一组的独立设置页给这一组自己的（来源见
 *  [ComboParamSource.memberDelay]）；用不到它的组合（多成员 / 等待）来源给 `null`，这一行不摆。
 */
private fun SettingsPage.memberDelayRows(source: ComboParamSource): List<CardRow> =
    source.memberDelay
        ?.let { delay ->
            listOf(
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_MULTI_TITLE),
                                icon = PageIcons.DelayHold,
                                min = KeyConfig.MIN_LONG_PRESS_MS,
                                max = KeyConfig.MAX_LONG_PRESS_MS,
                                value = delay.value,
                                default = KeyConfig.DEFAULT_LONG_PRESS_MS,
                                step = 10,
                                dragStep = 50,
                                enabled = delay.enabled,
                                disabledHint = delay.disabledHint,
                            ),
                        onCommit = delay.onCommit,
                    )
                },
            )
        }.orEmpty()

/**
 * **公共**那一份来源 —— 值读 `GlobalConfig`，写入落到全局配置（没单独设过的组都用它）。
 *
 *  提示由写入那几个函数自己发（`writeAndNotice`）：与页面上其余全局项同一套回执。
 */
internal fun SettingsPage.comboPublicParams(enabled: Boolean): ComboParamSource {
    val cfg = configState.config.value
    return ComboParamSource(
        windows = cfg.global.windows(),
        //  双击滑杆回落的是**出厂默认** —— 公共页与独立设置页现在是同一份口径
        windowDefaults = ComboWindows.DEFAULT,
        holdMinMs = cfg.global.comboHoldMinMs,
        holdMinDefault = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
        sceneOnStarter = cfg.global.comboSceneOnStarter,
        starterKeepLong = cfg.global.comboStarterKeepLong,
        //  两颗开关、两个变量：等待组合读 `comboFixedWindowStart`，按住组合读 `comboHoldFixedWindowStart`
        waitFixedWindowStart = cfg.global.comboFixedWindowStart,
        holdFixedWindowStart = cfg.global.comboHoldFixedWindowStart,
        //  公共这两行**不按现有档位置灰**（`holdWindowLive` 取默认 `true`）：这一份是给「没单独设过的组」
        //  与还没建出来的那一档用的默认值，按「当前有没有档用得到」禁用它，等于要求用户先把档建出来
        //  再调它的默认值。而且那句理由（「未开启多击手势」）说的是别的档的开关，摆在公共页站不住。
        //
        //  公共那张按住卡的前缀（只标在「固定窗口起点」那一行上）：那里的默认值同时服务
        //  「三颗以上的按住档」与「两键档」⇒「按键间隔和多击…」。
        //  等待那张卡两种档量的是同一件事，恒为「按键间隔…」，不取自这里。
        holdWindowLabel = WindowLabel.INTERVAL_MULTI,
        //  成员「连击后长按延迟」的公共那一份：与单键那张卡第三行同一条 —— 公共设置始终可调
        memberDelay =
            DelayParamSource(
                value = cfg.global.comboMemberClickThenLongPressMs,
                enabled = enabled,
                onCommit = { v -> setGlobalComboMemberThenLongPress(v) },
            ),
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
 * **某一组自己**那一份来源 —— 值读这一组的记录（没写过的那几格取出厂默认），写入落回这一组。
 *
 *  四格给的是 `comboStoredWindowsFor` 而不是 `comboWindowsFor`：
 *  显示与写入同源，拨一下总开关不会跳值。**四格各写各的**（用户口径：独立设置里也正反分别处理）
 *  —— 卡片按当前那格挑一支，于是「开着起点调的那一根」与「关着起点调的那一根」是两个落点，
 *  互不改写。写入成功后自己弹一句回执（位用 [HINT_SLOT_COMBO]，那几行就在这一页上）。
 *
 *  有序无序共用这四格：档位要不要按先后落，与「多久之内落完」是两件事。
 */
internal fun SettingsPage.comboOwnParams(
    combo: ComboKey,
    enabled: Boolean,
    disabledHint: String?,
): ComboParamSource {
    val cfg = configState.config.value
    //  「固定窗口起点」两颗的出厂值（模型上就一个默认值，见 `GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START`）
    val fixedStartDefault = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START
    //  成员「后长按」三档开了任意一档 ⇒ 那一根按住时长才有可量的东西（判据见 `memberDelay`）
    val anyThenLongOn = combo.memberClickThenLongOn || combo.memberDoubleThenLongOn || combo.memberTripleThenLongOn
    return ComboParamSource(
        windows = cfg.comboStoredWindowsFor(combo),
        //  回落值也是**出厂默认**：这一组没写过的那几格 = 出厂那一份（`ComboWindows.DEFAULT`）
        windowDefaults = ComboWindows.DEFAULT,
        holdMinMs = cfg.comboStoredHoldMinFor(combo),
        holdMinDefault = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
        //  下面那几颗开关显示的是**这一档自己那份**（没写过 = 出厂默认），**不跟总开关走** ——
        //  与上面那四格窗口同一条口径。读 `...For`（生效值）的话，一拨总开关整片就换成公共那份，
        //  与「切换开关只该禁用 / 启用」相冲（口径见 `OwnSettingsCard`）。
        sceneOnStarter = combo.sceneOnStarter ?: GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER,
        starterKeepLong = combo.starterKeepLong ?: GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG,
        waitFixedWindowStart = combo.fixedWindowStart ?: fixedStartDefault,
        holdFixedWindowStart = combo.holdFixedWindowStart ?: fixedStartDefault,
        //  这一档用不用得到按住那两行：成员多于一颗（成员之间接收），或成员连击开着（连击窗口）
        //  —— 见 [ComboParamSource.holdWindowLive]
        holdWindowLive = combo.followers.size > 1 || combo.memberGestures?.isOn == true,
        //  独立设置页**只写这一档自己的前缀**：两键的按住组合写「多击…」（那两行只服务
        //  那颗成员键的连击），其余写「按键间隔…」。判据见 [holdWindowLabelOf]。
        holdWindowLabel = holdWindowLabelOf(combo),
        //  成员连击那三档共用的按住时长：只有「按住档 + 一颗成员」有成员连击（`memberGestures`），
        //  其余组合摆一根拨了不改行为的滑杆没有意义。
        //
        //  两道闸与单键那张卡的第三行同一条：**没开独立设置**时写下去不生效（生效值跟公共那一份），
        //  所以跟着上面那几根一起压灰；**三档一档都没开**时这一根也没有可量的东西。
        memberDelay =
            if (combo.memberGestures == null) {
                null
            } else {
                DelayParamSource(
                    //  显示的是**这一档自己那份**（没写过 ⇒ 出厂默认），与上面那几格窗口同一口径：
                    //  取生效值的话，拨一下总开关它就从公共值跳到这一组自己的值。
                    value = cfg.comboStoredMemberThenLongPressFor(combo),
                    enabled = enabled && anyThenLongOn,
                    disabledHint =
                        if (enabled && !anyThenLongOn) {
                            I18n.get(Strings.BINDING_NO_THEN_LONG_GESTURE)
                        } else {
                            disabledHint
                        },
                    onCommit = { ms -> setComboOwnMemberThenLongPress(combo.id, ms) },
                )
            },
        enabled = enabled,
        disabledHint = disabledHint,
        onWaitWindow = { ms -> setComboOwnWaitWindow(combo.id, ms) },
        onWaitPair = { ms -> setComboOwnWaitPair(combo.id, ms) },
        onHoldWindow = { ms -> setComboOwnHoldWindow(combo.id, ms) },
        onHoldFollower = { ms -> setComboOwnHoldFollower(combo.id, ms) },
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
    //  回执的名字跟卡片标题走：同一个 `holdWindowLabelOf`（有序 / 两键 / 三颗以上各一种写法）
    val label =
        configState.config.value.combos
            .firstOrNull { it.id == id }
            ?.let { holdWindowLabelOf(it) }
            ?: WindowLabel.PLAIN
    val ok =
        updateCombo(id, "hold_fixed_window_start=${if (on) 1 else 0}") {
            it.copy(holdFixedWindowStart = on)
        }
    if (ok) {
        hint(
            switchNotice(I18n.get(label.fixedWindowStartTitle()), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}
