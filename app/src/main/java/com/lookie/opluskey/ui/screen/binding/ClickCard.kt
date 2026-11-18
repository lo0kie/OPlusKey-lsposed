package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.fixedWindowStart
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.icon.actionIcon
import com.lookie.opluskey.ui.model.text.releaseActionTitle
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.SliderRow
import com.lookie.opluskey.ui.row.SliderSpec
import com.lookie.opluskey.ui.screen.command.StepPickerPage
import com.lookie.opluskey.ui.screen.command.StepPickerSheet
import com.lookie.opluskey.ui.screen.command.StepSheetFilter
import com.lookie.opluskey.ui.screen.command.StepSheetSpec
import com.lookie.opluskey.ui.screen.command.branchDraftNow
import com.lookie.opluskey.ui.wire.appNameOf
import com.lookie.opluskey.ui.write.BindingTarget
import com.lookie.opluskey.ui.write.openAppPickerForRelease
import com.lookie.opluskey.ui.write.setGlobalClickThenLongPress
import com.lookie.opluskey.ui.write.setGlobalClickWindow
import com.lookie.opluskey.ui.write.setGlobalWaitMultiWindow
import com.lookie.opluskey.ui.write.setWaitMultiWindow
import com.lookie.opluskey.ui.write.updateKey

// 「单击」那张卡 —— **公共设置页与「独立设置」页（单键）用的是同一份**。
//
//  两行（判定时机 + 等待窗口）的文案、图标、上下限本来就该逐字相同，所以整张卡只写一遍；
//  两侧的差别全收在 [ClickParamSource] 里 —— 值从哪来、写到哪去、能不能动。
//  卡片自己不认识「公共」还是「某一颗键」，也不认识 `Config`：给它一份来源，它照着画。
//
//  **两侧的闸门不一样，这是有意的**：公共那一页是全局默认值，单键也要有个地方改单击延迟，
//  所以它不判「有没有多击」；独立设置页管的是「这颗键的多击怎么判」，一档多击都没开时没有
//  可判的东西（判据见 [clickKeyParams]）。

/**
 * 这张卡的**状态源** —— 同一个界面的两份落点。
 *
 *  公共那一份来自 `GlobalConfig`、写全局配置（[clickGlobalParams]）；某一颗键那一份来自
 *  `KeyConfig`、写回这颗键（[clickKeyParams]）。判定侧两处都读（`KeyConfig.clickWindowMs` 那一条口径）。
 *
 * @param collect 「固定窗口起点」那颗开关的 checked —— 直接就是 `ClickChainParams.fixedWindowStart`
 *   （开着 = 从首次按下只等一个窗口，关着 = 每次松手重新起算）。它是存储位 `waitMultiWindow`
 *   的反面，**翻的那一次在 core 里**（见 `ClickChainParams.fixedWindowStart` 的说明）；
 *   写回时同一个反面（`setWaitMultiWindow(!collect)`），界面这一层不再各自取反。
 * @param windowMs 等待窗口滑杆**显示**的值（按当前判定时机取那一位；`multiWaitMs` 为 0 时回落
 *   到单击延迟 —— 老配置就长这样，滑杆要显示一个具体值）。
 * @param enabled 前两行能不能动。
 * @param disabledHint 灰着被点一下时的说法（`null` = 用整页那句）。
 * @param onCollect 写判定时机。`false` = 没写进去（与开关自身的回执一致）。
 * @param onWindow 写窗口那个数，返回「写盘成没成」：滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 * @param delay 第三行那根「单击和多击和长按延迟」的来源 —— 它与前两行**不是同一条闸**
 *   （公共页一直可调、独立页还要开着一档后长按），所以各自带 `enabled` / 提示（见 [DelayParamSource]）。
 */
internal class ClickParamSource(
    val collect: Boolean,
    val windowMs: Int,
    val enabled: Boolean,
    val disabledHint: String? = null,
    val onCollect: (Boolean) -> Unit,
    val onWindow: (Int) -> Boolean,
    val delay: DelayParamSource,
)

/**
 * 「单键」那张卡 —— 判定时机 + 等待窗口 + 单击和多击和长按延迟，三行。
 *
 *  **判定时机排在最前面**：它决定第二根滑杆**哪一位算数** —— 关着看「单击延迟」、
 *  开着看「多击窗口」（判据收在 `KeyConfig.clickWindowMs`）。
 *
 *  第三行（三档「后长按」共用的按住时长）**与上面两行同卡**：它问的是同一个「按下去怎么算」的
 *  下一档 —— 先定按几下算几次、等多久，再定按住多久算长按。分两张卡会把同一件事切成两段。
 *  它自己那道闸由 [ClickParamSource.delay] 单独给（公共页不看多击，独立页看）。
 *
 *  **写路径故意少判一道 `linkReady`**（按键绑定页的格子那边是 `enabled && linkReady`）：
 *  这张卡写的是**参数**（延迟 / 判定开关），写了就落盘，注入侧恢复后照样生效，不需要注入侧回话；
 *  格子点了要写动作、拿去执行，未就绪时写了也是白写。这是有意的不一致，不要再把两边「统一」。
 *
 *  判据里**没有**「适配清单」那一道：清单只回答「这一组验过没有」，不参与任何闸
 *  （AGENTS 一、13）—— 别照着旧注释把它加回来，那会让清单外的机器整页变灰。
 */
@Composable
internal fun SettingsPage.ClickDelayCard(source: ClickParamSource) {
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW),
                    summaryOn =
                        I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW_ON),
                    summaryOff =
                        I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW_OFF),
                    icon = PageIcons.CollectWindow,
                    checked = source.collect,
                    enabled = source.enabled,
                    disabledHint = source.disabledHint,
                    onCheckedChange = source.onCollect,
                ),
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                //  标题恒为「连击等待窗口」（不带「双击 / 多击」那种前缀）：这一根在两种
                                //  判定时机下写的值不同，但问的是同一件事 —— 「连击按下去之后等多久」；
                                //  时机那一档由上面那颗开关自己标（它的副标题说清了）。
                                //  **不画副标题**：两种时机的差别在上面那颗开关的副标题里说清了。
                                title = I18n.get(Strings.BINDING_WAIT_WINDOW),
                                icon = PageIcons.ComboWait,
                                //  上限**按当前那一格取**：固定窗口起点开 = `doubleClickMs` 1000
                                //  （整串一次预算）/ 关 = `multiWaitMs` 500（相邻两击，人类两击间隔
                                //  超过约 500 就不再像连击）。两根上限不同 ⇒ 切换时机那一下右端会跳。
                                min = KeyConfig.MIN_DOUBLE_CLICK_MS,
                                max =
                                    if (source.collect) {
                                        KeyConfig.MAX_DOUBLE_CLICK_MS
                                    } else {
                                        KeyConfig.MAX_MULTI_WAIT_MS
                                    },
                                value = source.windowMs,
                                //  双击滑杆的回落值跟着**当前那一格**走：固定窗口起点开 = `doubleClickMs`
                                //  350、关 = `multiWaitMs` 250（两位的取值口径见 `KeyConfig.clickWindowMs`）
                                default =
                                    if (source.collect) {
                                        KeyConfig.DEFAULT_DOUBLE_CLICK_MS
                                    } else {
                                        KeyConfig.DEFAULT_MULTI_WAIT_MS
                                    },
                                enabled = source.enabled,
                                disabledHint = source.disabledHint,
                            ),
                        onCommit = source.onWindow,
                    )
                },
                //  —— 单击和多击和长按延迟 ——
                //  三档「后长按」（单击后长按 / 双击后长按 / 三击后长按）共用那一个按住时长：
                //  先点了几下不影响手按下去的节奏，所以三档只有这一根滑杆（`click_then_long_press_ms`）。
                //  刻度与长按那一档同一套（10ms 取值 / 50ms 粗拖，见 `LongPressDelayRow`）——
                //  同一件事在两处不该有两种手感。
                CardRow.Custom { position ->
                    SliderRow(
                        position = position,
                        spec =
                            SliderSpec(
                                //  **这一段就是滑杆的主标题**：单击后 / 双击后 / 三击后那几档共用这一个
                                //  按住时长（`click_then_long_press_ms` 只有一位），标题用「连击后长按」
                                //  这一族名把它们一起说，不逐档列、也不再起一个小标题。
                                title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_MULTI_TITLE),
                                icon = PageIcons.DelayHold,
                                min = KeyConfig.MIN_LONG_PRESS_MS,
                                max = KeyConfig.MAX_LONG_PRESS_MS,
                                value = source.delay.value,
                                default = KeyConfig.DEFAULT_LONG_PRESS_MS,
                                step = 10,
                                dragStep = 50,
                                enabled = source.delay.enabled,
                                disabledHint = source.delay.disabledHint,
                            ),
                        onCommit = source.delay.onCommit,
                    )
                },
            ),
    )
}

/**
 * **公共**那一份来源 —— 值读 `GlobalConfig`，写入落到全局配置（没单独设过的键都用它）。
 *
 *  这三行**都不判「有没有多击」**：它们是全局默认值，单键也要有个地方改单击延迟与后长按时长
 *  （用户口径「公共设置始终不禁用」）。
 */
internal fun SettingsPage.clickGlobalParams(enabled: Boolean): ClickParamSource {
    val g = configState.config.value.global
    //  正向说法走 `fixedWindowStart`（存储位 `waitMultiWindow` 是它的反面，翻的那一次在 core 里）
    val fixedStart = g.fixedWindowStart
    val multi = !fixedStart
    //  0 = 「跟随单击延迟」（老配置长这样），滑杆要显示一个具体值
    val windowValue =
        if (multi) {
            g.multiWaitMs.takeIf { it > KeyConfig.FOLLOW_DOUBLE_CLICK_MS } ?: g.doubleClickMs
        } else {
            g.doubleClickMs
        }
    return ClickParamSource(
        collect = fixedStart,
        windowMs = windowValue,
        enabled = enabled,
        onCollect = { collect -> setGlobalWaitMultiWindow(!collect) },
        onWindow = { v -> setGlobalClickWindow(v) },
        delay =
            DelayParamSource(
                value = g.clickThenLongPressMs,
                enabled = enabled,
                onCommit = { v -> setGlobalClickThenLongPress(v) },
            ),
    )
}

/**
 * **某一颗键自己**那一份来源 —— 值读 `KeyConfig`，写入落回这颗键。
 *
 *  **多一道「有没有多击手势」的闸**：这一页管的是「这颗键的多击怎么判」，一档多击都没开时
 *  没有可判的东西。判据与判定侧**同一个**（`judgeCap > 1`）：启用双击 / 三击 / 四击 /
 *  单击后长按任意一个开着，或者场景里给某一档绑了动作，都算「有」—— 不在这里另写一遍
 *  `doubleClickOn || extraClicksOn…`：那样页面说「能调」、判定却按另一个数决定「等不等」，
 *  两处迟早对不上。
 *
 *  三道闸的提示按**更根本的那一条**给：没开独立设置 → 没开多击 → 整页那句。
 *
 * @param pageEnabled 整页闸门（调用方已经算好的，含「只参与非首键的按住组合」那一道）。
 * @param pageDisabledHint 整页给的那句原因（`null` = 用整页那句）。
 */
internal fun SettingsPage.clickKeyParams(
    keyId: KeyId,
    pageEnabled: Boolean,
    pageDisabledHint: String? = null,
): ClickParamSource {
    val key = configState.config.value.key(keyId)
    val noMulti = key.judgeCap <= 1
    //  这一颗键有没有开「使用独立设置」—— 关着时这两项跟随公共设置（那一份在公共设置页改），
    //  这一页只是把生效值摆出来、灰着。
    val own = key.useOwnSettings
    val hint =
        when {
            !own -> I18n.get(Strings.KEY_SETTINGS_NEED_OWN)
            pageEnabled && noMulti -> I18n.get(Strings.BINDING_NO_MULTI_GESTURE)
            else -> pageDisabledHint
        }
    //  **不是 [key.multiWaitMs] 的原值**：0 表示「跟随单击延迟」（老配置就长这样），
    //  滑杆要显示一个具体值；「用户没动过就不写」这条靠 `SliderRow` 只在拖动时回调。
    //  判定时机那边读的是正向说法 `fixedWindowStart`（存储位 `waitMultiWindow` 是它的反面，
    //  翻的那一次在 core 里，见 `ClickChainParams.fixedWindowStart`）。
    //
    //  **只看判定时机，不看「有没有开多击」**：`noMulti` 只决定这一行能不能拖（`enabled` 与那句
    //  提示）。带上它的话，关掉最后那档多击手势（双击默认开着）会让 `judgeCap` 掉回 1 ⇒ 这里
    //  改去读 `doubleClickMs`，滑杆数字当场从「多击窗口」跳到「单击延迟」——用户口径
    //  「切换多击手势时等待窗口的值不该跳」。而且那样显示的还是错的：判定侧用的是
    //  `clickWindowMs`（`waitMultiWindow` 为真时优先取 `multiWaitMs`），与开没开多击无关。
    val fixedStart = key.fixedWindowStart
    val multi = !fixedStart
    val windowValue =
        if (multi) {
            key.multiWaitMs.takeIf { it > KeyConfig.FOLLOW_DOUBLE_CLICK_MS } ?: key.doubleClickMs
        } else {
            key.doubleClickMs
        }
    //  —— 第三行（后长按时长）自己那一道闸 ——
    //  它比上面两行多一条：三档「后长按」一个都没开时，这一颗键根本不判后长按，
    //  按住多久无从谈起（用户口径「启用了 x 击后长按才启用」）。
    //  前两行是 `noMulti`（判不判多击），这一行是这几档开关本身（判不判后长按）—— 两件事，
    //  所以不共用 `enabled`：不开多击也能单独开后长按那一档。
    val thenLongOn = key.clickThenLongOn || key.doubleThenLongOn || key.tripleThenLongOn
    return ClickParamSource(
        collect = fixedStart,
        windowMs = windowValue,
        enabled = pageEnabled && own && !noMulti,
        disabledHint = hint,
        onCollect = { collect -> setWaitMultiWindow(keyId, !collect) },
        onWindow = { v ->
            //  **写哪一位要现读一次配置**，不能用上面那个 `multi` 快照：用户刚拨完判定时机、
            //  紧接着拖这一根时，快照还是旧的 —— 按它写就会写到「当前不生效」的那一位上，
            //  表现正是「滑杆拖了、判定一点没变」（真机报过）。
            //  判据与显示那一侧同一个（`!fixedWindowStart`）：没有多击时这一根写的就是
            //  单击延迟，写进 `multiWaitMs` 会是一条谁也读不到的账。
            val now = configState.config.value.key(keyId)
            if (!now.fixedWindowStart) writeMultiWaitMs(keyId, v) else writeDoubleClickMs(keyId, v)
        },
        delay =
            DelayParamSource(
                //  **显示的是生效值**：没开独立设置时就是公共那一份（与判定侧同一个取法）
                value = if (own) key.clickThenLongPressMs else configState.config.value.global.clickThenLongPressMs,
                enabled = pageEnabled && own && thenLongOn,
                disabledHint =
                    when {
                        !own -> I18n.get(Strings.KEY_SETTINGS_NEED_OWN)
                        pageEnabled && !thenLongOn -> I18n.get(Strings.BINDING_NO_THEN_LONG_GESTURE)
                        else -> pageDisabledHint
                    },
                onCommit = { v -> writeClickThenLongPressMs(keyId, v) },
            ),
    )
}

/** 把「单击延迟」写进**这一颗键**（每颗键各一份）；`false` = 没写进去。 */
internal fun SettingsPage.writeDoubleClickMs(
    keyId: KeyId,
    v: Int,
): Boolean = updateKey(keyId, "double_click_ms=$v") { it.copy(doubleClickMs = v) }

/** 把「多击窗口」写进**这一颗键**（0 = 跟随单击延迟，滑杆不会写回 0）；`false` = 没写进去。 */
internal fun SettingsPage.writeMultiWaitMs(
    keyId: KeyId,
    v: Int,
): Boolean = updateKey(keyId, "multi_wait_ms=$v") { it.copy(multiWaitMs = v) }

// 「单击和多击和长按延迟」那一行 —— 「单键」卡里的第三行，**公共设置页与「独立设置」页用的是同一份**。
//
//  它问的是三档「后长按」（单击后长按 / 双击后长按 / 三击后长按）共用那一个按住时长：
//  先点了几下不影响手按下去的节奏，所以三档只有这一根滑杆（见 `click_then_long_press_ms`）。
//  两侧差别只有「值从哪来、写到哪去、能不能动」，全收在 [DelayParamSource] 里，
//  与上面两根（判定时机 / 窗口）一起由 [ClickParamSource] 交给 [ClickDelayCard]。
//
//  **两侧的闸门不一样，这是有意的**：公共设置是全局默认值，单键也要有个地方改它，
//  所以那边一直可调；独立设置页管的是「这颗键的三档后长按怎么判」，一档都没开时
//  没有可判的东西（判据见 [clickKeyParams] 的 `delay`）。

/**
 * 这一行的**状态源** —— 同一根滑杆的两份落点（与 [ClickParamSource] 同一条纪律）。
 *
 *  它单独一个类型而不是并进 [ClickParamSource]：这一行的闸门与前两行不是同一条
 *  （公共页不看多击，独立页看后长按那三档），合成一份 `enabled` 就会让两件事绑在一起。
 *
 * @param value 这一根滑杆显示的按住时长（毫秒）
 * @param enabled 这一根能不能拖
 * @param disabledHint 灰着被拖一下时的说法（`null` = 用整页那句）
 * @param onCommit 写这个数，返回「写盘成没成」：滑杆失败时要拨回原值
 */
internal class DelayParamSource(
    val value: Int,
    val enabled: Boolean,
    val disabledHint: String? = null,
    val onCommit: (Int) -> Boolean,
)

/** 把「单击和多击和长按延迟」写进**这一颗键**（每颗键各一份）；`false` = 没写进去。 */
internal fun SettingsPage.writeClickThenLongPressMs(
    keyId: KeyId,
    v: Int,
): Boolean = updateKey(keyId, "click_then_long_press_ms=$v") { it.copy(clickThenLongPressMs = v) }

// 「场景动作」页里**「单击后长按」的松手**那一族 —— 与长按那张卡的松手族同形，行也逐字相同。
//
//  为什么单独一卡而不是塞进 `LongPressCard`：那一张卡的行、判据、写回都认「长按」这一个槽位
//  （`LongPressParams` / `LongPressEdit`），再塞一档就得让那个契约多一个维度。
//  这一页本来就只有「这一块做什么」，多摆一族不影响阅读 —— 用小标题分清是哪一档即可。

/**
 * 场景块里「单击后长按」的松手 —— 开关 + 动作两行，与长按那一族**同一个形状、同一套文案**。
 *
 *  写回走 `bindChainRelease`（只改草稿）：与这一页其它东西同一条纪律 —— 这一页编的是草稿，
 *  提前落盘等于替用户按了那颗「确认」。
 *
 * @param enabled 这一页能不能改（整页闸门 × 这一块的「跟随默认」）
 * @param disabledReason 灰着被点一下时说的那句（`null` = 用整页那句）
 */
@Composable
internal fun SettingsPage.SceneChainReleaseCard(
    target: BindingTarget.Scene,
    enabled: Boolean,
    disabledReason: String? = null,
) {
    val block = branchDraftNow().scenes.getOrNull(target.sceneAt) ?: return
    val action = block.chainRelease.takeUnless { it is Action.Follow } ?: Action.None
    var picking by remember { mutableStateOf(false) }
    CardList(
        rows =
            listOf(
                CardRow.Switch(
                    title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE),
                    summaryOn = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_ON),
                    summaryOff = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_DESC_OFF),
                    icon = PageIcons.LongRelease,
                    checked = block.chainReleaseOn,
                    enabled = enabled,
                    disabledHint = disabledReason,
                ) { on ->
                    target.bindChainRelease(on = on)
                },
                CardRow.Item(
                    title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_ACTION),
                    summary = releaseActionTitle(action.token) { appNameOf(it) },
                    icon = actionIcon(action.token),
                    arrow = true,
                    enabled = enabled,
                    disabledHint = disabledReason,
                    onClick = { picking = true },
                    visible = block.chainReleaseOn,
                ),
            ),
    )
    //  挑动作：与长按那一族**同一个抽屉**，`chain = true` + `sceneAt` 决定落到草稿的哪一格
    StepPickerSheet(
        show = picking,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE),
                current = action,
                onOpenPicker = { page ->
                    picking = false
                    when (page) {
                        StepPickerPage.App -> {
                            openAppPickerForRelease(chain = true, combo = target.comboId, sceneAt = target.sceneAt)
                        }

                        StepPickerPage.AppStop -> {
                            openAppPickerForRelease(stop = true, chain = true, combo = target.comboId, sceneAt = target.sceneAt)
                        }

                        //  变量那一格这一趟不列（`StepSheetFilter.excludeTiles`），走不到这里
                        StepPickerPage.Vars -> {}
                    }
                },
                onPick = { token -> target.bindChainRelease(action = Action.parse(token)) },
                onDismiss = { picking = false },
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}
