package com.lookie.opluskey.ui.screen.combo

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboWindows
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.isOn
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
import com.lookie.opluskey.ui.write.setComboOrderedFixedWindowStart
import com.lookie.opluskey.ui.write.setComboOrderedHoldFixedWindowStart
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
 *   它自己存着的那份（`Config.comboStoredWindowsFor`，没写过的那几格取出厂默认），**不是**
 *   `comboWindowsFor` 那个「实际生效」值 —— 取生效值的话，拨一下那颗总开关它就从公共值跳到
 *   这一组自己的值，看着像刚拖好的窗口被改了。
 * @param windowDefaults 双击滑杆回落的四格：**两份都是出厂默认**（`ComboWindows.DEFAULT`）。
 * @param orderedWindows **顺序组合**那四格，口径与 [windows] 逐条相同（另一组数）。
 * @param orderedWindowDefaults 同 [windowDefaults] —— 顺序组合那一份的回落值。
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
 * @param orderedRows 摆不摆第二对窗口行（公共 `true`；独立设置页恒 `false` —— 那一页只有一对）。
 * @param onWaitFixedWindowStart 写等待组合那颗开关 / @param onHoldFixedWindowStart 写按住组合那颗。
 * @param onOrderedWaitFixedWindowStart 写顺序组合那两颗开关（等待那张卡 / 按住那张卡各一颗）。
 *   与上面两颗是**两组变量**：顺序组合自己一组窗口，拨它不动不分顺序那一档。
 */
internal class ComboParamSource(
    val windows: ComboWindows,
    val windowDefaults: ComboWindows,
    /**
     * 这一份来源的**顺序组合**四格窗口（等待 / 按住 × 固定窗口起点 开 / 关）。
     *
     *  与 [windows] 是**两套数**（用户口径：顺序组合单独一组）：两张卡各有两对窗口行 ——
     *  一对给不分顺序那一档、一对给「按顺序触发」那几档。某一组那一份给的是
     *  `Config.comboStoredOrderedWindowsFor`（与 [windows] 同一条：不看总开关）。
     */
    val orderedWindows: ComboWindows,
    /** 双击滑杆回落的顺序组合四格 —— 口径与 [windowDefaults] 相同。 */
    val orderedWindowDefaults: ComboWindows,
    val holdMinMs: Int,
    val holdMinDefault: Int,
    val sceneOnStarter: Boolean,
    val starterKeepLong: Boolean,
    /** 等待组合那张卡的「固定窗口起点」（这一档自己那份；生效值见 `Config.comboFixedWindowStartFor`）。 */
    val waitFixedWindowStart: Boolean,
    /** 按住组合那张卡的「固定窗口起点」（同上，生效值见 `Config.comboHoldFixedWindowStartFor`）。 */
    val holdFixedWindowStart: Boolean,
    /** **顺序组合**、等待档的固定窗口起点（这一档自己那份）。 */
    val orderedWaitFixedWindowStart: Boolean,
    /** **顺序组合**、按住档的固定窗口起点（这一档自己那份）。 */
    val orderedHoldFixedWindowStart: Boolean,
    /**
     * 这一份来源**摆不摆**第二对窗口行（[orderedWaitRows] / [orderedHoldRows]）。
     *
     *  公共设置那片恒为 `true`：公共值是所有档的默认值，没有「这一档是不是顺序组合」可言，
     *  所以两对都摆（第二对才需要标「顺序组合」把两套数分开）。
     *  某一档的独立设置页**恒为 `false`** —— 那一页只有一对行，顺序组合的那几档让第一对直接读
     *  `ordered*` 那一组（见 `comboOwnParams`）：数已经在里面了，再标一句「顺序组合」等于同一件事说两遍。
     */
    val orderedRows: Boolean,
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
     *  公共那一份给的是「配置里存在这样的按住组合吗」（不看启停）：一条都没有时公共这两项同样空转。
     */
    val holdWindowLive: Boolean,
    /**
     * 这一份来源里**有没有开着成员连击的档** —— 只决定「等待窗口」那根滑杆要不要挂一句
     * 「成员连击的单击窗口也用它」。
     *
     *  那句话说的事是真的：按住卡那两格同时是「成员之间接收的窗口」（按下 vs 按下）与
     *  「成员连击的单击窗口」（抬手 vs 按下，见 `Config.comboMemberChain`），起点都不一样，
     *  调一边必然拖动另一边。这层耦合是按用户口径**有意保留**的（组合的连击不另开一套窗口），
     *  所以不拆，只在会受影响的那一档上把它说明白。
     */
    val memberWindowShare: Boolean,
    val enabled: Boolean,
    val disabledHint: String? = null,
    val onWaitWindow: (Int) -> Boolean,
    val onWaitPair: (Int) -> Boolean,
    val onHoldWindow: (Int) -> Boolean,
    val onHoldFollower: (Int) -> Boolean,
    /** **顺序组合**那四格的写入 —— 与上面四支同形，落点换成各自那一格。 */
    val onOrderedWaitWindow: (Int) -> Boolean,
    val onOrderedWaitPair: (Int) -> Boolean,
    val onOrderedHoldWindow: (Int) -> Boolean,
    val onOrderedHoldFollower: (Int) -> Boolean,
    val onHoldMin: (Int) -> Boolean,
    val onSceneOnStarter: (Boolean) -> Unit,
    val onStarterKeepLong: (Boolean) -> Unit,
    val onWaitFixedWindowStart: (Boolean) -> Unit,
    val onHoldFixedWindowStart: (Boolean) -> Unit,
    val onOrderedWaitFixedWindowStart: (Boolean) -> Unit,
    val onOrderedHoldFixedWindowStart: (Boolean) -> Unit,
)

/**
 * 「等待组合」那张卡 —— **等窗口**那一条按法的参数，四行：**固定窗口起点** + **等待窗口**，
 *  后面再接**顺序组合**的同名一对（那一对只在「按顺序触发」的档上生效，读的是另一组数）。
 *  **那一对是条件行**（[orderedWaitRows]）：公共设置页两对都摆；某一档的独立设置页**只有一对**
 *  —— 那一档是顺序组合时，唯一那一对直接读 `ordered*` 那一组，不再另标一句「顺序组合」。
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
                                //  行尾写「80 ms」—— 这一行问的就是「等多久」，不写数看不出来
                                valueText = { "$it ms" },
                            ),
                        onCommit = if (wholeString) source.onWaitWindow else source.onWaitPair,
                    )
                },
                //  —— 顺序组合那一对（用户口径：顺序组合单独一组窗口）——
                //  与上面两行**同名不同数**：这一对只在「按顺序触发」的那几档上生效
                //  （`Config.comboWindowsFor` 按 `ordered` 挑这一份）。**独立设置页不摆**
                //  —— 那一页只有一对，顺序组合的档直接让第一对读它那一组（见 [ComboParamSource.orderedRows]）。
                *orderedWaitRows(source),
            ),
    )
}

/**
 * 「等待组合」卡上**第二对窗口行**（顺序组合那一对：固定窗口起点 + 等待窗口）—— 只有
 * [ComboParamSource.orderedRows] 为真时才摆，也就是**只有公共设置那一片**。
 *
 *  独立设置页只有一对：那一档是顺序组合时，第一对直接读 `ordered*` 那一组
 *  （见 `comboOwnParams`）；再摆一对标着「顺序组合」的行只会把同一件事说两遍。
 *  公共那片两对都摆 —— 公共值是所有档的默认值，两套数得分开调。
 */
private fun SettingsPage.orderedWaitRows(source: ComboParamSource): Array<CardRow> =
    if (!source.orderedRows) {
        emptyArray()
    } else {
        arrayOf(
            CardRow.Switch(
                title = I18n.get(Strings.COMBO_ORDERED_FIXED_WINDOW_TITLE),
                summaryOn =
                    I18n.get(Strings.COMBO_ORDERED_FIXED_WINDOW_DESC_ON),
                summaryOff =
                    I18n.get(Strings.COMBO_ORDERED_FIXED_WINDOW_DESC_OFF),
                //  与上面那颗同一颗 `window`：两处管的是同一件事（窗口从哪一刻起算）
                icon = PageIcons.CollectWindow,
                checked = source.orderedWaitFixedWindowStart,
                enabled = source.enabled,
                disabledHint = source.disabledHint,
                onCheckedChange = source.onOrderedWaitFixedWindowStart,
            ),
            CardRow.Custom { position ->
                val orderedWhole = source.orderedWaitFixedWindowStart
                SliderRow(
                    position = position,
                    spec =
                        SliderSpec(
                            title = I18n.get(Strings.COMBO_ORDERED_WINDOW_TITLE),
                            //  与上面那一根同一条：这一格管「整串」还是「相邻两颗」由它上面那颗开关说清
                            icon = PageIcons.ComboWait,
                            min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                            //  与上面那一根同一条：上限跟着「当前写哪一格」走
                            max =
                                if (orderedWhole) {
                                    GlobalConfig.COMBO_WINDOW_MAX_MS
                                } else {
                                    GlobalConfig.COMBO_PAIR_MAX_MS
                                },
                            value =
                                if (orderedWhole) {
                                    source.orderedWindows.wait
                                } else {
                                    source.orderedWindows.waitPair
                                },
                            default =
                                if (orderedWhole) {
                                    source.orderedWindowDefaults.wait
                                } else {
                                    source.orderedWindowDefaults.waitPair
                                },
                            //  与上面那一根同一套步长（理由见那边）
                            step = 10,
                            dragStep = 50,
                            enabled = source.enabled,
                            disabledHint = source.disabledHint,
                            valueText = { "$it ms" },
                        ),
                    onCommit = if (orderedWhole) source.onOrderedWaitWindow else source.onOrderedWaitPair,
                )
            },
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
 *     **紧接着还有一对同样的行**（顺序组合那一对）：有序的按住档读 `orderedHold` /
 *     `orderedHoldFollower`（`ComboWindows` 里那两格由 `Config.comboWindowsFor` 按 `ordered` 换过来），
 *     与上面这一对互不改写 —— 用户口径是「顺序组合单独一组窗口」。那一对同样是**条件行**
 *     （[orderedHoldRows]）：公共那一片才摆，独立设置页只有一对（顺序组合的档读的就是它那一组）。
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
                    title = I18n.get(Strings.COMBO_FIXED_WINDOW_START_TITLE),
                    summaryOn =
                        I18n.get(Strings.COMBO_HOLD_FIXED_WINDOW_DESC_ON),
                    summaryOff =
                        I18n.get(Strings.COMBO_HOLD_FIXED_WINDOW_DESC_OFF),
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
                                title = I18n.get(Strings.COMBO_FIXED_WINDOW_TITLE),
                                //  这一格管「整串」还是「每颗各自等」由上面那颗开关说清，不另写副标题。
                                //  **唯一例外**：这一档开着成员连击时，同一个数还被 `Config.comboMemberChain`
                                //  当成「连击的单击窗口」用（起点是抬手，与「成员之间接收」的起点不同）——
                                //  调它两边一起变，这层耦合得让人看得见。
                                summary = if (source.memberWindowShare) I18n.get(Strings.COMBO_MEMBER_WINDOW_SHARE) else null,
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
                                valueText = { "$it ms" },
                            ),
                        onCommit = if (wholeString) source.onHoldWindow else source.onHoldFollower,
                    )
                },
                //  —— 顺序组合那一对 ——
                //  与上面两行**同名不同数**：只在「按顺序触发」的按住档上生效。置灰判据与上面
                //  那两行**同一份**（[ComboParamSource.holdWindowLive]）：同一张卡上上下挨着的
                //  两对行，一亮一灰只会让人以为是两根不一样的旋钮坏了。**独立设置页不摆** ——
                //  那一页只有一对，顺序组合的档直接让第一对读它那一组（见 [ComboParamSource.orderedRows]）。
                *orderedHoldRows(source, windowHint),
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
 * 「按住组合」卡上**第二对窗口行** —— 与 [orderedWaitRows] 同一条：只有
 * [ComboParamSource.orderedRows] 为真时才摆，也就是**只有公共设置那一片**（独立设置页只有一对）。
 *
 *  置灰判据与上面那一对**同一份**（[ComboParamSource.holdWindowLive]）：同一张卡上上下挨着的
 *  两对行，一亮一灰只会让人以为是两根不一样的旋钮坏了 —— 所以把那一句原因递进来共用。
 */
private fun SettingsPage.orderedHoldRows(
    source: ComboParamSource,
    windowHint: String?,
): Array<CardRow> =
    if (!source.orderedRows) {
        emptyArray()
    } else {
        arrayOf(
            CardRow.Switch(
                //  与上面那一颗同一条：副标题是**按住档**自己那两句（从第一个成员起算），
                //  不借等待卡那两句（见按住卡那段注释）
                title = I18n.get(Strings.COMBO_ORDERED_FIXED_WINDOW_TITLE),
                summaryOn =
                    I18n.get(Strings.COMBO_ORDERED_HOLD_FIXED_WINDOW_DESC_ON),
                summaryOff =
                    I18n.get(Strings.COMBO_ORDERED_HOLD_FIXED_WINDOW_DESC_OFF),
                icon = PageIcons.CollectWindow,
                checked = source.orderedHoldFixedWindowStart,
                enabled = source.enabled && source.holdWindowLive,
                disabledHint = windowHint,
                onCheckedChange = source.onOrderedHoldFixedWindowStart,
            ),
            CardRow.Custom { position ->
                val orderedWhole = source.orderedHoldFixedWindowStart
                SliderRow(
                    position = position,
                    spec =
                        SliderSpec(
                            title = I18n.get(Strings.COMBO_ORDERED_WINDOW_TITLE),
                            icon = PageIcons.ComboWait,
                            min = GlobalConfig.COMBO_WINDOW_MIN_MS,
                            //  与上面同一条：上限跟着「当前写哪一格」走
                            max =
                                if (orderedWhole) {
                                    GlobalConfig.COMBO_WINDOW_MAX_MS
                                } else {
                                    GlobalConfig.COMBO_PAIR_MAX_MS
                                },
                            value =
                                if (orderedWhole) {
                                    source.orderedWindows.hold
                                } else {
                                    source.orderedWindows.holdFollower
                                },
                            default =
                                if (orderedWhole) {
                                    source.orderedWindowDefaults.hold
                                } else {
                                    source.orderedWindowDefaults.holdFollower
                                },
                            step = 10,
                            dragStep = 50,
                            enabled = source.enabled && source.holdWindowLive,
                            disabledHint = windowHint,
                            valueText = { "$it ms" },
                        ),
                    onCommit = if (orderedWhole) source.onOrderedHoldWindow else source.onOrderedHoldFollower,
                )
            },
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
        windows = cfg.global.windows(),
        //  双击滑杆回落的是**出厂默认** —— 公共页与独立设置页现在是同一份口径
        windowDefaults = ComboWindows.DEFAULT,
        //  顺序组合那一组：同样是「现值 + 出厂回落」，只是另一组数
        orderedWindows = cfg.global.orderedWindows(),
        orderedWindowDefaults = ComboWindows.DEFAULT,
        holdMinMs = cfg.global.comboHoldMinMs,
        holdMinDefault = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
        sceneOnStarter = cfg.global.comboSceneOnStarter,
        starterKeepLong = cfg.global.comboStarterKeepLong,
        //  两颗开关、两个变量：等待组合读 `comboFixedWindowStart`，按住组合读 `comboHoldFixedWindowStart`
        waitFixedWindowStart = cfg.global.comboFixedWindowStart,
        holdFixedWindowStart = cfg.global.comboHoldFixedWindowStart,
        //  顺序组合那两颗同一条：与上面两颗各自独立（拨它不动不分顺序那一档）
        orderedWaitFixedWindowStart = cfg.global.comboOrderedFixedWindowStart,
        orderedHoldFixedWindowStart = cfg.global.comboOrderedHoldFixedWindowStart,
        //  公共这片**两对行都摆**：公共值是所有档的默认值，没有「这一档是不是顺序组合」可言
        orderedRows = true,
        //  公共这两项算不算法：整份配置里**没有一个用得到它们的按住组合**时，它们不会被任何
        //  判定读到（成员之间接收 ≥3 颗、成员连击两颗就够，见 [ComboParamSource.holdWindowLive]）。
        //  这里看「配置里有没有这样一档」，不看启停 —— 临时停用一档不该让滑杆变灰。
        holdWindowLive = cfg.combos.any { it.isHold && (it.followers.size > 1 || it.memberGestures?.isOn == true) },
        memberWindowShare = cfg.combos.any { it.memberGestures?.isOn == true },
        enabled = enabled,
        onWaitWindow = { setComboWaitWindow(it) },
        onWaitPair = { setComboWaitPair(it) },
        onHoldWindow = { setComboHoldWindow(it) },
        onHoldFollower = { setComboHoldFollower(it) },
        //  顺序组合那四格：与上面四支同形，落点换成 `comboOrdered*` 那四格
        onOrderedWaitWindow = { setComboOrderedWaitWindow(it) },
        onOrderedWaitPair = { setComboOrderedWaitPair(it) },
        onOrderedHoldWindow = { setComboOrderedHoldWindow(it) },
        onOrderedHoldFollower = { setComboOrderedHoldFollower(it) },
        onHoldMin = { setComboHoldMin(it) },
        onSceneOnStarter = { setComboSceneOnStarter(it) },
        onStarterKeepLong = { setComboStarterKeepLong(it) },
        onWaitFixedWindowStart = { setComboFixedWindowStart(it) },
        onHoldFixedWindowStart = { setComboHoldFixedWindowStart(it) },
        onOrderedWaitFixedWindowStart = { setComboOrderedFixedWindowStart(it) },
        onOrderedHoldFixedWindowStart = { setComboOrderedHoldFixedWindowStart(it) },
    )
}

/**
 * **某一组自己**那一份来源 —— 值读这一组的记录（没写过的那几格取出厂默认），写入落回这一组。
 *
 *  四格给的是 `comboStoredWindowsFor` / `comboStoredOrderedWindowsFor` 而不是 `comboWindowsFor`：
 *  显示与写入同源，拨一下总开关不会跳值。**四格各写各的**（用户口径：独立设置里也正反分别处理）
 *  —— 卡片按当前那格挑一支，于是「开着起点调的那一根」与「关着起点调的那一根」是两个落点，
 *  互不改写。写入成功后自己弹一句回执（位用 [HINT_SLOT_COMBO]，那几行就在这一页上）。
 *
 *  ## 这一页只摆**一对**窗口行
 *
 *  顺序组合的那几档读的是 `ordered*` 那一组数 —— 那一页里它们就是「这一档的窗口」，
 *  不必再标一句「顺序组合」把同一件事说两遍（用户口径）。所以：
 *  · 这一档**是**顺序组合 ⇒ 第一对（也是唯一一对）直接读 `ordered*`、写 `setComboOwnOrdered*`；
 *  · 不是 ⇒ 照旧读 `windows` / `pair` 那四格；
 *  · 第二对（[ComboParamSource.orderedRows]）在独立设置页**恒不摆**，只有公共那一片摆两对。
 */
internal fun SettingsPage.comboOwnParams(
    combo: ComboKey,
    enabled: Boolean,
    disabledHint: String?,
): ComboParamSource {
    val cfg = configState.config.value
    //  这一档是「按顺序触发」的那几档吗 —— 决定那一对行读哪一组数、写回哪四个字段
    val ordered = combo.ordered
    //  「固定窗口起点」四颗的出厂值（模型上就一个默认值，见 `GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START`）
    val fixedStartDefault = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START
    return ComboParamSource(
        windows = if (ordered) cfg.comboStoredOrderedWindowsFor(combo) else cfg.comboStoredWindowsFor(combo),
        //  回落值也是**出厂默认**：这一组没写过的那几格 = 出厂那一份（`ComboWindows.DEFAULT`）
        windowDefaults = ComboWindows.DEFAULT,
        //  顺序组合那一份仍原样带着（公共那一片才摆第二对）；独立设置页里它不被读
        orderedWindows = cfg.comboStoredOrderedWindowsFor(combo),
        orderedWindowDefaults = ComboWindows.DEFAULT,
        holdMinMs = cfg.comboStoredHoldMinFor(combo),
        holdMinDefault = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
        //  下面那几颗开关显示的是**这一档自己那份**（没写过 = 出厂默认），**不跟总开关走** ——
        //  与上面那四格窗口同一条口径。读 `...For`（生效值）的话，一拨总开关整片就换成公共那份，
        //  与「切换开关只该禁用 / 启用」相冲（口径见 `OwnSettingsCard`）。
        sceneOnStarter = combo.sceneOnStarter ?: GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER,
        starterKeepLong = combo.starterKeepLong ?: GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG,
        //  「按顺序触发」的档第一对读 `ordered*`、其余读不分顺序那两颗 —— 与 `Config.comboWindowStartFor`
        //  挑的是同一对，只是那边按生效值挑、这边按「这一档自己那份」挑。
        waitFixedWindowStart = (if (ordered) combo.orderedFixedWindowStart else combo.fixedWindowStart) ?: fixedStartDefault,
        holdFixedWindowStart = (if (ordered) combo.orderedHoldFixedWindowStart else combo.holdFixedWindowStart) ?: fixedStartDefault,
        //  顺序组合那两颗同一条（与上面两颗各自独立）
        orderedWaitFixedWindowStart = combo.orderedFixedWindowStart ?: fixedStartDefault,
        orderedHoldFixedWindowStart = combo.orderedHoldFixedWindowStart ?: fixedStartDefault,
        //  独立设置页**只摆一对**：上面那对已经是这一档实际用的数，第二对不出现
        orderedRows = false,
        //  这一档用不用得到按住那两行：成员多于一颗（成员之间接收），或成员连击开着（连击窗口）
        //  —— 见 [ComboParamSource.holdWindowLive]
        holdWindowLive = combo.followers.size > 1 || combo.memberGestures?.isOn == true,
        memberWindowShare = combo.memberGestures?.isOn == true,
        enabled = enabled,
        disabledHint = disabledHint,
        //  四格各一支，落点跟着「这一档是不是顺序组合」走 —— 顺序组合那几档写回 `ordered*` 那四位
        onWaitWindow = { ms -> if (ordered) setComboOwnOrderedWaitWindow(combo.id, ms) else setComboOwnWaitWindow(combo.id, ms) },
        onWaitPair = { ms -> if (ordered) setComboOwnOrderedWaitPair(combo.id, ms) else setComboOwnWaitPair(combo.id, ms) },
        onHoldWindow = { ms -> if (ordered) setComboOwnOrderedHoldWindow(combo.id, ms) else setComboOwnHoldWindow(combo.id, ms) },
        onHoldFollower = { ms -> if (ordered) setComboOwnOrderedHoldFollower(combo.id, ms) else setComboOwnHoldFollower(combo.id, ms) },
        onOrderedWaitWindow = { setComboOwnOrderedWaitWindow(combo.id, it) },
        onOrderedWaitPair = { setComboOwnOrderedWaitPair(combo.id, it) },
        onOrderedHoldWindow = { setComboOwnOrderedHoldWindow(combo.id, it) },
        onOrderedHoldFollower = { setComboOwnOrderedHoldFollower(combo.id, it) },
        onHoldMin = { setComboOwnHoldMin(combo.id, it) },
        onSceneOnStarter = { on -> setOwnSceneOnStarter(combo.id, on) },
        onStarterKeepLong = { on -> setOwnStarterKeepLong(combo.id, on) },
        onWaitFixedWindowStart = { on ->
            if (ordered) setOwnOrderedFixedWindowStart(combo.id, on) else setOwnFixedWindowStart(combo.id, on)
        },
        onHoldFixedWindowStart = { on ->
            if (ordered) setOwnOrderedHoldFixedWindowStart(combo.id, on) else setOwnHoldFixedWindowStart(combo.id, on)
        },
        onOrderedWaitFixedWindowStart = { on -> setOwnOrderedFixedWindowStart(combo.id, on) },
        onOrderedHoldFixedWindowStart = { on -> setOwnOrderedHoldFixedWindowStart(combo.id, on) },
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

/**
 * 写这一组的「**顺序组合**固定窗口」—— **等待组合那张卡**上那一颗（`ComboKey.orderedFixedWindowStart`）。
 *
 *  与 [setOwnFixedWindowStart] 同一个形状，落的是顺序组合那一对里的等待档那一位：
 *  拨它只改这一档有序时取窗口的方式，不分顺序那一档（`fixedWindowStart`）一格不动。
 */
private fun SettingsPage.setOwnOrderedFixedWindowStart(
    id: String,
    on: Boolean,
) {
    val ok =
        updateCombo(id, "ordered_fixed_window_start=${if (on) 1 else 0}") {
            it.copy(orderedFixedWindowStart = on)
        }
    if (ok) {
        hint(
            switchNotice(I18n.get(Strings.COMBO_ORDERED_FIXED_WINDOW_TITLE), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}

/**
 * 写这一组的「**顺序组合**固定窗口」—— **按住组合那张卡**上那一颗
 * （`ComboKey.orderedHoldFixedWindowStart`）。
 *
 *  与 [setOwnOrderedFixedWindowStart] 同一个形状、同一个回执句，但落的是另一个字段。
 */
private fun SettingsPage.setOwnOrderedHoldFixedWindowStart(
    id: String,
    on: Boolean,
) {
    val ok =
        updateCombo(id, "ordered_hold_fixed_window_start=${if (on) 1 else 0}") {
            it.copy(orderedHoldFixedWindowStart = on)
        }
    if (ok) {
        hint(
            switchNotice(I18n.get(Strings.COMBO_ORDERED_FIXED_WINDOW_TITLE), on),
            slot = HINT_SLOT_COMBO,
        )
    }
}
