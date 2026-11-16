package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.LongPressEdit
import com.lookie.opluskey.core.model.OwnSettings
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.core.model.withLongPress
import com.lookie.opluskey.core.rom.RomProfile
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.model.icon.actionIcon
import com.lookie.opluskey.ui.model.text.releaseActionTitle
import com.lookie.opluskey.ui.row.LongPressSections
import com.lookie.opluskey.ui.row.LongPressSettingsCard
import com.lookie.opluskey.ui.row.LongPressSpec
import com.lookie.opluskey.ui.screen.command.StepPickerPage
import com.lookie.opluskey.ui.screen.command.StepPickerSheet
import com.lookie.opluskey.ui.screen.command.StepSheetFilter
import com.lookie.opluskey.ui.screen.command.StepSheetSpec
import com.lookie.opluskey.ui.state.binding.KeyWatchState
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.wire.appNameOf
import com.lookie.opluskey.ui.write.BindingTarget
import com.lookie.opluskey.ui.write.openAppPickerForRelease
import com.lookie.opluskey.ui.write.write

// 「长按」那一档的设置卡 —— **单键与组合键共用这一支**。
//
// 卡片本体（行序 / 文案 / 可见性）在 `ui/row/LongPressSpec.kt`；这里只做两件事：
// 把 `target` 那份参数递进去、把改动落回 `target` 指的那一条配置。
// 参数读取走 `LongPressParams`（两者共用的接口），写回走 `LongPressEdit`（变更包）——
// 所以这一支里**没有「是单键还是组合」的分支**。

/**
 * 长按那一档的**唯一**一张卡。
 *
 * @param target 这一档往哪儿写（单键的一档 / 组合键的一档）—— 读参数、写改动都按它来
 * @param pageEnabled 整页闸门（总闸 + 链路就绪 + 配置可写）
 * @param disabledReason 非 null ⇒ **整张卡**再压一道灰（`pageEnabled` 之外的原因，如「跟随默认」）——
 *   点灰行时说的就是这一句
 */
@Composable
internal fun SettingsPage.LongPressCard(
    target: BindingTarget,
    pageEnabled: Boolean,
    disabledReason: String? = null,
) {
    val cfg = configState.config.value
    val owner = target.longPress(cfg) ?: return
    //  「每次触发都振动」的前置：这一档的**振动反馈**（独立设置优先，见 `Config.effectiveFor`）。
    //  场景块不是「一份设置」（`ownerIn` 回 null）⇒ 按公共那一份算。
    val hapticOn = (target.ownerIn(cfg) as? OwnSettings)?.let { cfg.effectiveFor(it).vibrate } ?: cfg.global.vibrate
    //  整张卡的置灰只有两个来源：整页闸门、调用点给的额外原因（这一档根本不归这一页管）。
    val blockedReason = longPressBlockedReason(pageEnabled, disabledReason)
    val blocked = blockedReason != null
    //  「额外动作」这一行的取值**不滤 `Action.None`**：无操作就是这一档的默认值，不是「还没配」。
    //  滤掉它会让界面多出一个「未设置」状态 —— 那与「无操作」是同一个意思，却要另配文案与图标（原先那颗还借了网格里「系统按键」的，撞了）。
    //  「跟随默认」只属于场景块的三档：历史配置里可能已经存进来过（那一版抽屉把它列在了这一行），
    //  而它在这一档没有语义 —— 行上按「无操作」念，与执行侧同一个口径（见到它什么都不做）。
    //
    //  **读的是 `releaseSlot`，不是 `owner`**：这一族在场景块上归**草稿里那一块**（`BindingTarget.releaseSlot`），拿 `owner` 读会读成整颗键那份。
    val slot = target.releaseSlot(cfg)
    val releaseAction = slot.action.takeUnless { it is Action.Follow } ?: Action.None
    var pickingRelease by remember { mutableStateOf(false) }
    LongPressSettingsCard(
        LongPressSpec(
            repeat = owner.longRepeat,
            vibrate = owner.longRepeatVibrate,
            intervalMs = owner.longRepeatIntervalMs,
            pressMs = owner.longPressMs,
            fireOnRelease = owner.longFireOnRelease,
            //  「松手」两档单键与组合都有（判定侧两边都会补发 `GestureTrigger.Release`）
            releaseSupported = true,
            releaseOn = slot.on,
            //  行上写的是**动作名**：用户挑的可能是某条命令，只写 token 等于让他自己读 `cmd:喝水`
            //  回调拿到的是**目标写法**（`999@包名/类名`），不是包名 ⇒ 必须走 `appNameOf`
            //  （`appLabelOf` 只认包名，查不到时**把传进去的整串原样吐回来**，那一行就成了
            //  「松手: 999@com.foo/.Bar」）
            releaseLabel = releaseActionTitle(releaseAction.token) { target -> appNameOf(target) },
            //  配了动作，行首就换成**那个动作自己的图标**（与网格里那一格同一颗，见 `actionIcon`）：
            //  这一行答的是「松手做什么」，图标跟着答案走才读得出来
            releaseIcon = actionIcon(releaseAction.token),
            //  时序的互斥只在数据层有一份：这里把算好的结论递进去，界面不重算
            releaseBound = owner.releaseBound,
            //  长按这一档**本身**没有动作（无操作）时，松手额外动作整族焊死（见 `LongPressSpec.releaseAvailable`）。
            //  取 `actionIn` 而不是 `owner.long`：后者在场景接管时仍是空，而这一行要问的是
            //  「这一按到底做不做事」——`actionIn` 已经把场景那一层算进去了。
            releaseAvailable = target.actionIn(cfg).isBound,
            enabled = !blocked,
            delayEnabled = !blocked,
            sections = sectionsFor(target),
            blockedReason = blockedReason,
            repeatBlockedReason = repeatBlockedReason(target.actionIn(cfg)),
            onRepeat = { on -> edit(target, LongPressEdit(repeat = on)) { "long_repeat=${if (on) 1 else 0}" } },
            onVibrate = { on ->
                //  「每次触发都振动」是「振动反馈」的细分：总闸关着时它开了也不会振（执行侧
                //  `haptic` 第一行就按总闸返回），所以拨开时先拦一下，让他去开总闸。
                if (on && !hapticOn) {
                    RepeatVibrateBlocked.ask()
                } else {
                    edit(target, LongPressEdit(repeatVibrate = on)) { "long_repeat_vibrate=${if (on) 1 else 0}" }
                }
            },
            onInterval = { v -> edit(target, LongPressEdit(repeatIntervalMs = v)) { "long_repeat_interval_ms=$v" } },
            onPressMs = { v -> edit(target, LongPressEdit(pressMs = v)) { "long_press_ms=$v" } },
            //  关掉「松手时触发」是**有代价**的：这颗键若正被某些按住手势当成「先按住的那一颗」，
            //  那些手势会当场失效（判定侧那条上界会把组合判据整条截止）。所以关这一位走
            //  `setFireOnRelease`：它先看有没有受影响的手势，有就先问一句；
            //  开回来没有可失去的东西，直接写。
            onFireOnReleaseToggle = { on ->
                val keyTarget = target as? BindingTarget.Key
                if (!on && keyTarget != null) {
                    setFireOnRelease(keyTarget.id, on = false)
                } else {
                    edit(target, LongPressEdit(fireOnRelease = on)) { "long_fire_on_release=$on" }
                }
            },
            onReleaseToggle = { on -> editRelease(target, on = on) },
            onReleasePick = { pickingRelease = true },
        ),
    )
    //  「关掉松手时触发会让按住手势失效」那一问 —— 整张卡挂一次（它是这一位的唯一入口）
    FireReleaseOffDialog()
    //  「每次触发都振动」拨不动时那一窗（振动反馈没开，见上面 `onVibrate`）
    RepeatVibrateBlockedDialog()
    //  挑额外动作：与命令页「挑一步」**同一个抽屉**（项目里挑动作只有这一个清单）。
    StepPickerSheet(
        show = pickingRelease,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE),
                current = releaseAction,
                onOpenPicker = { page ->
                    pickingRelease = false
                    when (page) {
                        //  目标还是这一档：挑完回来写的就是它的松手格（场景块由 sceneAt 带过去）
                        StepPickerPage.App -> openAppPickerForRelease(combo = target.comboId, sceneAt = target.sceneAt)

                        StepPickerPage.AppStop -> openAppPickerForRelease(stop = true, combo = target.comboId, sceneAt = target.sceneAt)

                        //  变量那一格这一趟不列（见 `StepSheetFilter.excludeTiles`），走不到这里
                        StepPickerPage.Vars -> Unit
                    }
                },
                onPick = { token -> editRelease(target, action = Action.parse(token)) },
                onDismiss = { pickingRelease = false },
                //  「无操作」也要列：松手那一下本来就是可选的一步，「配了但这次不做事」是一档真取值
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}

/** 把一次长按改动写回这一档；滑杆要「写盘成没成」，所以回一个布尔。 */
private fun SettingsPage.edit(
    target: BindingTarget,
    change: LongPressEdit,
    what: () -> String,
): Boolean =
    write(target.withLongPress(configState.config.value, change), "${target.what}:${what()}") !=
        com.lookie.opluskey.ui.state.command.CommandsSave.Failed

/**
 * 「松手执行额外动作」那一族的写路径 —— **场景块只改草稿**，点那一页的「确认」才落盘。
 *
 *  这一族与另外三族落点不同（见 `BindingTarget.releaseSlot`）：时序 / 连发 / 长按延迟归这颗键，
 *  松手归**这一块** —— 所以它走 `bindRelease`（改草稿），不走 `withLongPress`（写那颗键）。
 *  与三档动作同一个规矩：这一页编的是草稿，提前落盘等于替用户按了那颗「确认」。
 */
private fun SettingsPage.editRelease(
    target: BindingTarget,
    on: Boolean? = null,
    action: Action? = null,
): Boolean =
    when (target) {
        is BindingTarget.Scene -> {
            target.bindRelease(on, action)
        }

        else -> {
            edit(target, LongPressEdit(releaseOn = on, release = action)) {
                "long_release_on=$on long_release=${action?.token}"
            }
        }
    }

/**
 * 这一页的卡摆**哪几族**行 —— 「场景动作」页只摆松手那一族（见 [LongPressSections]）。
 *
 *  另外三族（时序 / 连发 / 长按延迟）的参数归**这颗键**，而且注入侧在**按下那一刻**就按它们
 *  排枪（`GestureDetector` 的 `longPressAt`），场景条件改不动它们 —— 摆在那块的动作页上，
 *  改的是整颗键，读起来却像「这一块单独配过」（用户报的）。
 *  长按那一族本身不受影响：三种目标都还走同一张卡，只是摆出来的行数不同。
 */
private fun sectionsFor(target: BindingTarget): LongPressSections =
    if (target is BindingTarget.Scene) LongPressSections.ReleaseOnly else LongPressSections.All

/**
 * 这一档的长按动作**自己**不支持「连续触发」的原因；`null` = 支持。
 *
 *  两条判据与判定侧同一个口径（`KeyConfig.longIsSerial` / `longIsVoiceMemory`）：
 *  多条命令反复重放整条链没有意义；灵感语音那一族按一下就完事。
 *  只该管「连续触发」那一族，不连坐时序与长按延迟。
 */
internal fun SettingsPage.repeatBlockedReason(current: Action): String? {
    if (isSerialCommand(configState.commands.value, current.token)) {
        return I18n.get(Strings.BINDING_SERIAL_UNSUPPORTED_REPEAT)
    }
    val voice = (current as? Action.Oplus)?.feature?.let(RomProfile::isVoiceMemory) == true
    return if (voice) I18n.get(Strings.BINDING_VOICE_UNSUPPORTED_REPEAT) else null
}

/**
 * 长按那张卡**整卡置灰**的那一句（`null` = 能点）。
 *
 *  两个来源，前者优先：调用点给的原因（这一档根本不归这一页管，如「跟随默认」）、
 *  整页闸门（主开关 / 链路 / 只读）。
 *
 *  主开关自己关着不是「模块没生效」：`STATUS_DISABLED` 的正文是「确认作用域含 system，
 *  换包后需重启」—— 拿它来报这一档，等于把用户赶去 LSPosed 里翻一圈。
 */
private fun longPressBlockedReason(
    pageEnabled: Boolean,
    disabledReason: String?,
): String? =
    when {
        disabledReason != null -> {
            disabledReason
        }

        !pageEnabled -> {
            //  三种原因共用这一档（主开关 / 链路未就绪 / 配置只读），所以只能说**通用**的那一句 ——
            //  报「按键绑定已关闭」会让用户去翻一颗本来好好的开关（`STATUS_KEY_DISABLED` 的正文
            //  说的就是「按键绑定」这件事）。
            I18n.get(Strings.STATUS_PAGE_LOCKED)
        }

        else -> {
            null
        }
    }

// 「单击后长按」那一页的设置卡 —— 复用长按那张卡的行，参数换成这一档自己的。
//
//  为什么不新画一张：这几行的**文案、行序、互斥判据**与长按那一档是同一件事
//  （按住多久算数 / 按住期间重复几次 / 松手还做不做别的），各画一遍迟早只在一边生效。
//  差别只剩一个：读写的字段（`clickThenLong*`）。行本身与长按那一页**逐行相同** ——
//  连「松手时触发」那一行也在（用户口径「和长按一样的逻辑」），它开着时同样把
//  「松手执行额外动作」与「连续触发」那三行压灰。

/**
 * 「单击后长按」的整张设置卡 —— 挂在「按键绑定」页的这一档上。
 *
 *  行与长按那一页同一套（`LongPressSections.All`）：松手时触发 / 松手执行额外动作（+ 额外动作）/
 *  连续触发（+ 每次触发都振动 + 连续触发间隔）/ 长按延迟，参数是这一档自己的。
 *
 * @param pageEnabled 整页闸门（总闸 + 链路 + 配置可写）
 * @param disabledReason 非 null ⇒ 整张卡再压一道灰（`pageEnabled` 之外的原因，如「跟随默认」）
 */
@Composable
internal fun SettingsPage.ClickThenLongCard(
    pageEnabled: Boolean,
    disabledReason: String? = null,
) {
    val cfg = configState.config.value
    val id = KeyWatchState.editing(cfg)
    val key = cfg.key(id)
    //  「每次触发都振动」的前置：这一颗键的**振动反馈**（独立设置优先）—— 与长按那张卡同一条
    val hapticOn = cfg.effectiveFor(key).vibrate
    val release = key.clickThenLongRelease
    var picking by remember { mutableStateOf(false) }
    LongPressSettingsCard(
        LongPressSpec(
            repeat = key.clickThenLongRepeat,
            vibrate = key.clickThenLongRepeatVibrate,
            intervalMs = key.clickThenLongRepeatIntervalMs,
            pressMs = key.clickThenLongPressMs,
            //  「松手时触发」与长按那一档同义：到点不执行，抬手执行一次。开着它，
            //  下面「松手执行额外动作」与「连续触发」那三行由 `LongPressGates` 自动压灰。
            fireOnRelease = key.clickThenLongFireOnRelease,
            releaseSupported = true,
            releaseOn = key.clickThenLongReleaseOn,
            //  行上写**动作名**（与长按那张卡同一条）：用户挑的可能是某条命令
            releaseLabel = releaseActionTitle(release.token) { appNameOf(it) },
            releaseIcon = actionIcon(release.token),
            //  「松手时触发」开着时这一位不算占位（`fireEnabled` 会读到它）：否则两颗开关
            //  会互相锁死 —— 都开着时谁也关不掉对方，只能先清配置。「松手额外动作」本身
            //  在抬手那一刻让位（见 `GestureDetector.finishLong` 与 `clickThenLongReleaseAction`）。
            releaseBound = key.clickThenLongReleaseOn && release.isBound && !key.clickThenLongFireOnRelease,
            //  这一档本身是「无操作」时，松手额外动作那一族跟着焊死（同长按那张卡）
            releaseAvailable = key.clickThenLongActionExists,
            enabled = pageEnabled,
            delayEnabled = pageEnabled,
            sections = LongPressSections.All,
            //  多条命令 / 灵感语音不支持连发 —— 与长按那张卡同一份判据（`repeatBlockedReason`）
            repeatBlockedReason = repeatBlockedReason(key.clickThenLong),
            blockedReason =
                disabledReason
                    ?: if (!pageEnabled) I18n.get(Strings.STATUS_KEY_DISABLED) else null,
            onRepeat = { on -> commit(id) { it.copy(clickThenLongRepeat = on) } },
            onVibrate = { on ->
                //  与长按那张卡同一条：振动反馈没开时先拦一下（那一档的取值走 `effectiveFor`）
                if (on && !hapticOn) {
                    RepeatVibrateBlocked.ask()
                } else {
                    commit(id) { it.copy(clickThenLongRepeatVibrate = on) }
                }
            },
            onInterval = { v -> commit(id) { it.copy(clickThenLongRepeatIntervalMs = v) } },
            onPressMs = { v -> commit(id) { it.copy(clickThenLongPressMs = v) } },
            onFireOnReleaseToggle = { on -> commit(id) { it.copy(clickThenLongFireOnRelease = on) } },
            onReleaseToggle = { on -> commit(id) { it.copy(clickThenLongReleaseOn = on) } },
            onReleasePick = { picking = true },
        ),
    )
    //  「每次触发都振动」拨不动时那一窗（振动反馈没开，见上面 `onVibrate`）
    RepeatVibrateBlockedDialog()
    //  挑额外动作：与长按那张卡**同一个抽屉**（项目里挑动作只有这一个清单）
    StepPickerSheet(
        show = picking,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE),
                current = release,
                onOpenPicker = { page ->
                    picking = false
                    when (page) {
                        //  `chain = true`：挑完写的是这一档的松手格（见 `PickTarget.Release.chain`）
                        StepPickerPage.App -> openAppPickerForRelease(chain = true)

                        StepPickerPage.AppStop -> openAppPickerForRelease(stop = true, chain = true)

                        StepPickerPage.Vars -> Unit
                    }
                },
                onPick = { token -> commit(id) { it.copy(clickThenLongRelease = Action.parse(token)) } },
                onDismiss = { picking = false },
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}

/**
 * **组合键的成员「单击后长按」**那张卡 —— 与 [ClickThenLongCard] 逐行相同，主语换成这一档组合。
 *
 *  两边不合并：读写的是两张表（`KeyConfig.clickThenLong*` 对 `ComboKey.memberClickThenLong*`），
 *  合并会让每一行都多判一次「主语是谁」。行序 / 文案 / 互斥判据本来就在 `LongPressSpec` 里，
 *  这里只把这一档的参数递进去、把改动写回这一条组合。
 *
 * @param pageEnabled 整页闸门（总闸 + 链路 + 配置可写）
 * @param disabledReason 非 null ⇒ 整张卡再压一道灰（`pageEnabled` 之外的原因）
 */
@Composable
internal fun SettingsPage.ComboMemberChainCard(
    combo: ComboKey,
    pageEnabled: Boolean,
    disabledReason: String? = null,
) {
    val cfg = configState.config.value
    //  「每次触发都振动」的前置：这一档的**振动反馈**（独立设置优先）
    val hapticOn = cfg.effectiveFor(combo).vibrate
    val release = combo.memberClickThenLongRelease
    var picking by remember { mutableStateOf(false) }
    LongPressSettingsCard(
        LongPressSpec(
            repeat = combo.memberClickThenLongRepeat,
            vibrate = combo.memberClickThenLongRepeatVibrate,
            intervalMs = combo.memberClickThenLongRepeatIntervalMs,
            pressMs = combo.memberClickThenLongPressMs,
            fireOnRelease = combo.memberClickThenLongFireOnRelease,
            releaseSupported = true,
            releaseOn = combo.memberClickThenLongReleaseOn,
            releaseLabel = releaseActionTitle(release.token) { appNameOf(it) },
            releaseIcon = actionIcon(release.token),
            //  「松手时触发」开着时这一位不算占位（`fireEnabled` 会读到它）：否则两颗开关会互相锁死
            releaseBound =
                combo.memberClickThenLongReleaseOn &&
                    release.isBound &&
                    !combo.memberClickThenLongFireOnRelease,
            releaseAvailable = combo.memberClickThenLongActionExists,
            enabled = pageEnabled,
            delayEnabled = pageEnabled,
            sections = LongPressSections.All,
            repeatBlockedReason = repeatBlockedReason(combo.memberClickThenLong),
            blockedReason =
                disabledReason
                    ?: if (!pageEnabled) I18n.get(Strings.STATUS_KEY_DISABLED) else null,
            onRepeat = { on -> commitCombo(combo.id) { it.copy(memberClickThenLongRepeat = on) } },
            onVibrate = { on ->
                if (on && !hapticOn) {
                    RepeatVibrateBlocked.ask()
                } else {
                    commitCombo(combo.id) { it.copy(memberClickThenLongRepeatVibrate = on) }
                }
            },
            onInterval = { v -> commitCombo(combo.id) { it.copy(memberClickThenLongRepeatIntervalMs = v) } },
            onPressMs = { v -> commitCombo(combo.id) { it.copy(memberClickThenLongPressMs = v) } },
            onFireOnReleaseToggle = { on -> commitCombo(combo.id) { it.copy(memberClickThenLongFireOnRelease = on) } },
            onReleaseToggle = { on -> commitCombo(combo.id) { it.copy(memberClickThenLongReleaseOn = on) } },
            onReleasePick = { picking = true },
        ),
    )
    RepeatVibrateBlockedDialog()
    StepPickerSheet(
        show = picking,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.BINDING_LONG_PRESS_RELEASE_TITLE),
                current = release,
                onOpenPicker = { page ->
                    picking = false
                    when (page) {
                        //  带 `combo`：挑完写的是这一档组合的成员松手格（见 `writeReleaseTo`）
                        StepPickerPage.App -> openAppPickerForRelease(chain = true, combo = combo.id)

                        StepPickerPage.AppStop -> openAppPickerForRelease(stop = true, chain = true, combo = combo.id)

                        StepPickerPage.Vars -> Unit
                    }
                },
                onPick = { token -> commitCombo(combo.id) { it.copy(memberClickThenLongRelease = Action.parse(token)) } },
                onDismiss = { picking = false },
                filter = StepSheetFilter(excludeTiles = true, includeNone = true),
            ),
    )
}

/**
 * 把这一档组合的一次改动写回配置 —— **现读一次**再改（理由同 [commit]）。
 *
 *  不调 `updateCombo`：那是 combo 包的东西，这一页只该认识「写配置」这一条路。
 */
private fun SettingsPage.commitCombo(
    id: String,
    change: (ComboKey) -> ComboKey,
): Boolean {
    val now = configState.config.value
    val combo = now.combos.firstOrNull { it.id == id } ?: return false
    val next = change(combo)
    if (next == combo) return true
    return write(
        now.copy(combos = now.combos.map { if (it.id == id) next else it }),
        "member_click_then_long combo=$id",
    ) != CommandsSave.Failed
}

/**
 * 把这一档的一次改动写回配置 —— **现读一次**再改。
 *
 *  与这一页其它卡片同一条口径：先拨开关再拖滑杆时，渲染那一帧的 `key` 会把刚写进去的值顶回去。
 */
private fun SettingsPage.commit(
    id: KeyId,
    change: (KeyConfig) -> KeyConfig,
): Boolean {
    val now: Config = configState.config.value
    val next = change(now.key(id))
    return write(
        now.copy(keys = now.keys + (id to next)),
        "click_then_long key=${id.id}",
    ) != CommandsSave.Failed
}

/**
 * 「每次触发都振动」被拦下来那一窗的开关 —— 与 `ComboStarterBlocked` 同一套写法。
 *
 *  只存一个布尔：这一窗没有「确认之后做什么」，两颗按钮都只是关窗。用页面级 object 而不是
 *  `remember`，因为它是**卡片之外**的窗口（`ConfirmDialog` 是独立 window），而卡片可能在
 *  这一帧被整支重画 —— 状态挂在那里会跟着丢。
 */
internal object RepeatVibrateBlocked {
    private val pending = mutableStateOf(false)

    val open: Boolean get() = pending.value

    fun ask() {
        pending.value = true
    }

    fun clear() {
        pending.value = false
    }
}

/**
 * 「每次触发都振动」拨不动时那一窗 —— 这一档的**振动反馈**没开（独立设置优先，见调用点）。
 *
 *  为什么拦而不是「替他一起开」：振动反馈是总闸，影响的是这一档所有动作的回执，
 *  不该由「开一个连发振动」顺手改掉；说清要开哪一项就够了。
 */
@Composable
internal fun SettingsPage.RepeatVibrateBlockedDialog() {
    ConfirmDialog(
        show = RepeatVibrateBlocked.open,
        title = I18n.get(Strings.BINDING_REPEAT_VIBRATE_BLOCKED_TITLE),
        summary = I18n.get(Strings.BINDING_REPEAT_VIBRATE_BLOCKED_BODY),
        action = I18n.get(Strings.COMMON_GOT_IT),
        positiveDanger = false,
        //  这一窗没有「确认之后做点什么」—— 两颗按钮都只是把窗关掉
        onDismiss = { RepeatVibrateBlocked.clear() },
        onConfirm = { RepeatVibrateBlocked.clear() },
    )
}
