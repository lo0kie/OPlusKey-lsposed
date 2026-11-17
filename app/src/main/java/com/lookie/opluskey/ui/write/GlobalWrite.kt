package com.lookie.opluskey.ui.write

import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.NoticeMode
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_BINDING
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMMAND
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.feedback.switchToNotice
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.model.text.WindowLabel
import com.lookie.opluskey.ui.model.text.fixedWindowStartTitle
import com.lookie.opluskey.ui.model.text.languageLabel
import com.lookie.opluskey.ui.row.noticeModeLabel
import com.lookie.opluskey.ui.state.command.CommandsSave
import com.lookie.opluskey.ui.state.picker.AppPickerState
import kotlin.random.Random

// 「全局开关」这一族：一颗开关一个入口，全部落到 `global.*` 上。
//
// 与 `ui/write/ConfigWrite.kt` 的关系：那边是**机制**（[write] 的只读态 / 落盘 / 广播 / 重问），
// 这边是**动作**（改哪个字段、回执说什么）。分开之后「加一颗开关」与「改落盘流程」不会撞在同一处。
//
// 「**这一颗键**的参数」那半在 `ui/write/KeyParamsWrite.kt`（长按连续触发 / 忽视无操作双击）：
// 写的是 `keys[id]` 而不是 `global`，分开放才看得出「这一项属于谁」。

/**
 * 改一个**全局**开关：取当前配置 → 应用 [transform] → 走 [writeAndNotice]。
 *
 *  这一族几个函数的差别只有「改哪个字段 / 日志写什么 / 回执主语是什么」—— 取配置、copy、
 *  回执那套形状收在这里一处。原先每个函数抄一遍，加一颗开关就多一份要同步的副本。
 *
 * @param on 回执要说的目标状态（`switchNotice` 的第二个参数）
 */
internal fun SettingsPage.toggleGlobal(
    what: String,
    title: String,
    on: Boolean,
    transform: (GlobalConfig) -> GlobalConfig,
) {
    val cfg = configState.config.value
    writeAndNotice(cfg.copy(global = transform(cfg.global)), what) { switchNotice(title, on) }
}

/** 切「振动反馈」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setVibrate(on: Boolean) =
    toggleGlobal("vibrate=${if (on) 1 else 0}", I18n.get(Strings.HOME_VIBRATE_TITLE), on) {
        it.copy(vibrate = on)
    }

/**
 * 切「**无操作时振动**」—— 与 [setVibrate] 是两颗开关：
 * 那一颗管「真的执行了动作」，这一颗管「按了、但这一档什么动作都没有」。
 */
internal fun SettingsPage.setVibrateOnNone(on: Boolean) =
    toggleGlobal(
        "vibrate_on_none=${if (on) 1 else 0}",
        I18n.get(Strings.HOME_VIBRATE_ON_NONE_TITLE),
        on,
    ) { it.copy(vibrateOnNone = on) }

/**
 * 切**公共设置里的判定时机**（`GlobalConfig.waitMultiWindow`）—— 落的是「固定窗口起点」的反面。
 *
 *  与独立设置页那颗同一句文案、同一套语义（见 `waitMultiWindowRow`）：开关说「固定窗口起点」，
 *  开着 = 一个窗口内收集、不随后续按压延长；关着 = 每次松手重新起算。
 *  **读那一侧请用 `GlobalConfig.fixedWindowStart`**（正向说法、翻在 core 里），别自己再取反。
 */
internal fun SettingsPage.setGlobalWaitMultiWindow(on: Boolean) =
    toggleGlobal(
        "global_wait_multi_window=${if (on) 1 else 0}",
        I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW),
        //  回执说的是**界面上那颗**的状态（「固定窗口起点」开着没有），而 `on` 是存储位
        //  `waitMultiWindow` —— 两者相反。与逐键那颗同一个坑（见 `setWaitMultiWindow`
        //  那句「别照抄」）：照抄的话，拨开会说「已关闭」。
        !on,
    ) {
        it.copy(waitMultiWindow = on)
    }

/**
 * 写**公共设置里的窗口长度**（那一根滑杆）—— 按当前判定时机写对应那一格。
 *
 *  与逐键那一根同一个判据（`waitMultiWindow` 决定写 `multiWaitMs` 还是 `doubleClickMs`）：
 *  两个值分开存，切回另一种判定时机时各自那份还在。
 *
 * @return 写盘成没成（滑杆失败时要拨回原值）
 */
internal fun SettingsPage.setGlobalClickWindow(v: Int): Boolean {
    val cfg = configState.config.value
    val g = cfg.global
    val next = if (g.waitMultiWindow) g.copy(multiWaitMs = v) else g.copy(doubleClickMs = v)
    return write(cfg.copy(global = next), "global_window_ms=$v") != CommandsSave.Failed
}

/**
 * 写**振动强度**（三档，见 [GlobalConfig.VIBRATE_STRENGTH_RANGE]）。
 *
 *  只改强度、不动 [GlobalConfig.vibrate]：那一行的开关管「振不振」，这一行管「振多重」，
 *  两层各自独立（关掉振动时这一行整体不出现，见 `GeneralScreen.ActionsCard`）。
 *
 *  返回「写盘成没成」—— 滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 */
internal fun SettingsPage.setVibrateStrength(level: Int): Boolean {
    val cfg = configState.config.value
    val next = level.coerceIn(GlobalConfig.VIBRATE_STRENGTH_RANGE)
    return write(
        cfg.copy(global = cfg.global.copy(vibrateStrength = next)),
        "vibrate_strength=$next",
    ) != CommandsSave.Failed
}

/**
 * 切换界面语言。
 *
 *  回执的名字**必须在写盘之后取**：[write] 里那一下 `I18n.currentLanguage` 换了档，
 *  同一句话才会用新语言打出来（切到英文时显示 “English” 而不是「英语」）。
 *  所以这里给的是 lambda，不是先算好的字符串。
 *
 *  主语（「语言」）也一起在写盘之后取：切到英文之后那句应当是 “Language switched to “English””，
 *  不是「语言 switched to…」。回执带主语的理由见 `switchToNotice`。
 */
internal fun SettingsPage.setLanguage(code: String) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(language = code)),
        "language=$code",
    ) {
        switchToNotice(
            I18n.get(Strings.MORE_LANGUAGE),
            languageLabel(AppLanguage.fromCode(code)),
        )
    }
}

/**
 * 切「系统场景在首键按下时固定」**公共那一档** —— 同一参数还有每组的落点
 * （`ComboKey.sceneOnStarter`，见 `Config.comboSceneOnStarterFor`：独立设置开着时那一组自己说了算）。
 *
 *  回执位与其它组合项一致（`HINT_SLOT_COMBO`）：同一个开关在这两处各拨一下，屏幕上始终是
 *  那一条在改字，而不是两条叠着。
 */
internal fun SettingsPage.setComboSceneOnStarter(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(comboSceneOnStarter = on)),
        "combo_scene_on_starter=${if (on) 1 else 0}",
        slot = HINT_SLOT_COMBO,
    ) {
        switchNotice(I18n.get(Strings.COMBO_SCENE_ON_STARTER_TITLE), on)
    }
}

/**
 * 切「首键松手后仍执行长按」**公共那一档** —— 同一参数还有每组的落点
 * （`ComboKey.starterKeepLong`，见 `Config.comboStarterKeepLongFor`：独立设置开着时那一组自己说了算）。
 *
 *  回执位与其它组合项一致（`HINT_SLOT_COMBO`）。
 */
internal fun SettingsPage.setComboStarterKeepLong(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(comboStarterKeepLong = on)),
        "combo_starter_keep_long=${if (on) 1 else 0}",
        slot = HINT_SLOT_COMBO,
    ) {
        switchNotice(I18n.get(Strings.COMBO_STARTER_KEEP_LONG_TITLE), on)
    }
}

/**
 * 切「固定窗口起点」**公共那一档 —— 等待组合那一侧**（`GlobalConfig.comboFixedWindowStart`）。
 *
 *  同一参数还有每组的落点（`ComboKey.fixedWindowStart`，见 `Config.comboFixedWindowStartFor`：
 *  独立设置开着时那一组自己说了算）。
 *
 *  **这一颗只服务等待组合那张卡**：按住组合那张卡的同名开关落的是
 *  [setComboHoldFixedWindowStart]（`comboHoldFixedWindowStart`），两个变量各存各的
 *  —— 把其中一种按法的窗口改成「整串共用一个」，另一种不该跟着变。
 *
 *  回执位与其它组合项一致（`HINT_SLOT_COMBO`）。
 */
internal fun SettingsPage.setComboFixedWindowStart(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(comboFixedWindowStart = on)),
        "combo_fixed_window_start=${if (on) 1 else 0}",
        slot = HINT_SLOT_COMBO,
    ) {
        switchNotice(I18n.get(Strings.COMBO_FIXED_WINDOW_START_TITLE), on)
    }
}

/**
 * 切「固定窗口起点」**公共那一档 —— 按住组合那一侧**（`GlobalConfig.comboHoldFixedWindowStart`）。
 *
 *  与 [setComboFixedWindowStart] 是**两个变量**（用户口径：正反分别存）：这一颗只影响
 *  `MODE_HOLD_*` 那两条按法数窗口的方式，等待组合那张卡一格不动。
 *  判定侧读 `Config.comboHoldFixedWindowStartFor`。
 */
internal fun SettingsPage.setComboHoldFixedWindowStart(on: Boolean) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(comboHoldFixedWindowStart = on)),
        "combo_hold_fixed_window_start=${if (on) 1 else 0}",
        slot = HINT_SLOT_COMBO,
    ) {
        //  回执的名字跟公共那张卡的标题走（那里恒写「多击与顺序…」，见 `WindowLabel`）
        switchNotice(I18n.get(WindowLabel.INTERVAL_MULTI.fixedWindowStartTitle()), on)
    }
}

/**
 * 写「首键要先按住多久」（毫秒）**公共那一档** —— 按住手势的下限。
 *
 *  判定在注入侧（`ComboDetector` 的 `holdMinFor`），所以走通用写入通道：落盘 + 广播 + 重问注入侧。
 *  返回「写盘成没成」：滑杆失败时要拨回原值（与「等待窗口」那一行同一条）。
 */
internal fun SettingsPage.setComboHoldMin(ms: Int): Boolean {
    val cfg = configState.config.value
    val value = ms.coerceIn(GlobalConfig.COMBO_HOLD_MIN_MIN_MS, GlobalConfig.COMBO_HOLD_MIN_MAX_MS)
    if (cfg.global.comboHoldMinMs == value) return true
    return write(cfg.copy(global = cfg.global.copy(comboHoldMinMs = value)), "combo_hold_min_ms=$value") !=
        CommandsSave.Failed
}

/**
 * 切「前台转小窗时使用动画」—— 行点击与开关共用这一个入口。
 *
 *  判定侧在 `host/action/MiniWindowLauncher`（宿主进程里执行时现读配置，拨完立刻生效）。
 */
internal fun SettingsPage.setMiniWindowAnim(on: Boolean) =
    toggleGlobal(
        "mini_window_anim=${if (on) 1 else 0}",
        I18n.get(Strings.HOME_MINI_ANIM_TITLE),
        on,
    ) { it.copy(miniWindowAnim = on) }

/** 切「使用 ColorOS 接口」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setColorOsApi(on: Boolean) =
    toggleGlobal("color_os_api=${if (on) 1 else 0}", I18n.get(Strings.HOME_COLOROS_TITLE), on) {
        it.copy(colorOsApi = on)
    }

/**
 * 写**模块总闸**（[GlobalConfig.enabled]）：关闭后绑定页控件置灰，一颗键都不再被接管。
 *
 *  它**不碰**任何一颗键自己的 `enabled`：总闸与逐键是两层，关掉总闸再打开时，
 *  用户之前在 ⋮ 菜单里停用过的那颗键应该还是停用的。
 *
 * **两档都发，而且用同一条**（同一个位 [MODULE_ENABLED_SLOT]）：关 → 开 → 关，
 *  屏幕上始终是那一条在改字（「已关闭「按键绑定」」与「已开启「按键绑定」」）。
 *
 *  以前「关」这一档刻意不发，是因为常驻那条「按键绑定已关闭，侧键交还系统」会同一时刻出现，
 *  两条说同一件事；发吧是两条、不发吧又像是拨了没反应。**那条常驻提示已经整条删掉**
 *  （见 `HintQueue` 的类注释），于是两档都发、各出一条，正好。
 *
 * 写盘失败仍由 [write] 自己报，拨开关不会没有反馈。
 */
internal fun SettingsPage.setModuleEnabled(on: Boolean) {
    val cfg = configState.config.value
    val saved = write(cfg.copy(global = cfg.global.copy(enabled = on)), "module_enabled=${if (on) 1 else 0}")
    if (saved != CommandsSave.Ok) return
    hint(moduleEnabledNotice(on), slot = MODULE_ENABLED_SLOT)
}

/** 总闸那一句 —— 开 / 关两档同一个句式，与其它开关的回执同一族（[switchNotice]）。 */
private fun moduleEnabledNotice(on: Boolean): String = switchNotice(I18n.get(Strings.SETTING_KEY_BINDING), on)

/**
 * 用户在「风险提示与免责声明」上点了确认 —— 「记下这一笔」与「打开总闸」**同一次写盘**。
 *
 * 分两次写会留下「确认了却没启用 / 启用了却没记上」的半截状态，而这两样正是那道闸的两半。
 */
internal fun SettingsPage.acceptDisclaimerAndEnable() {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(
            global = cfg.global.copy(enabled = true),
            disclaimerAccepted = true,
        ),
        "disclaimer_accepted=true",
        //  它做的也是「开总闸」，回执与 [setModuleEnabled] 是同一句话 ⇒ 用**同一个位**
        slot = MODULE_ENABLED_SLOT,
    ) { moduleEnabledNotice(on = true) }
}

/**
 * 「主开关」这个提示位的名字 —— 拨开关（[setModuleEnabled]）与首次启用（[acceptDisclaimerAndEnable]）
 * 共用：两处说的是同一件事，屏幕上就不该出两条。
 */
private const val MODULE_ENABLED_SLOT = "module_enabled"

// 全局配置里「提示 / 反馈」那几项的写回。

/**
 * 切「执行时提示」的**落点**（四档菜单：不提示 / 通知 / Toast / 流体云）—— 一次写完三颗布尔。
 *
 *  四档由 `NoticeMode.of` 从那三颗派生（唯一出口），这里按选中的那一档把它们写成一致的一份：
 *  手改配置文件写出「通知 + 流体云」那种组合只会在读的时候被折成一档，界面永远只写这一种形状。
 *
 *  判定侧在 `host/GestureExec.noticeIfNeeded`，宿主进程每次执行现读配置，选完立刻生效；
 *  流体云那一档依赖「使用 ColorOS 接口」，关着时执行侧回落 Toast（`NoticeMode.effective`）。
 */
internal fun SettingsPage.setNoticeMode(mode: NoticeMode) {
    val cfg = configState.config.value
    writeAndNotice(
        cfg.copy(
            global =
                cfg.global.copy(
                    toastOnRun = mode != NoticeMode.NONE,
                    toastNotify = mode == NoticeMode.NOTIFY,
                    toastFluid = mode == NoticeMode.FLUID,
                ),
        ),
        "notice_mode=${mode.id}",
    ) {
        switchToNotice(I18n.get(Strings.HOME_TOAST_TITLE), noticeModeLabel(mode))
    }
}

/**
 * 写「执行时提示」的文案模板（`$key` / `$gesture` / `$action` 与 `$action` 拆开的三段，见 `ToastNotice`）。
 *
 *  **空串就是「用默认那句」**：默认文案只在 `ToastNotice.DEFAULT_TEMPLATE` 存一份，
 *  这里不塞字面量 —— 否则「默认是什么」会有两处各写一遍。
 */
internal fun SettingsPage.setToastTemplate(template: String) {
    val cfg = configState.config.value
    write(
        cfg.copy(global = cfg.global.copy(toastTemplate = template)),
        "toast_template=${template.length}字",
    )
}

// 「页面怎么显示」这一族的写入口：**跟随系统显示大小** + **页面缩放** + **界面振动反馈**
// + **显示常用命令**。
//
//  这几项改的是页面自己的呈现（`global.followDisplaySize` / `global.pageScale` 在「更多选项」的
//  「外观」卡里成对出现，缩放那一行还跟着开关一起进出场；`global.uiVibrate` 管页面自己振不振；
//  `global.showCommonCommands` 决定绑定页那一段露不露），与上面那批「按键怎么执行」的全局开关不是一类。

/**
 * 切**界面振动反馈**（`GlobalConfig.uiVibrate`）—— 点行 / 拨开关 / 拖滑杆那一下的触觉。
 *
 *  与 [setVibrate]（命令**执行**时振一次）是两件事：那一颗管「按键触发之后」，这一颗管
 *  「手指点在这个界面上」，所以入口在「更多选项」的「外观」卡里、不在「按键设置」页。
 *  判据在 `PageHaptics.allowed`（它每次现查这一位，拨完立刻生效）。
 */
internal fun SettingsPage.setUiVibrate(on: Boolean) =
    toggleGlobal("ui_vibrate=${if (on) 1 else 0}", I18n.get(Strings.MORE_UI_VIBRATE_TITLE), on) {
        it.copy(uiVibrate = on)
    }

/**
 * 切**显示更详细的按键设置**（`GlobalConfig.detailedKeySettings`）—— 按键设置页收不收全部参数。
 *
 *  纯显示项：这一位现在**还没有读取方**（先把配置键跑通），所以拨完只有回执，页面上看不出变化。
 *  与 [setUiVibrate] 同一档：不吃总闸、不碰链路，落盘就生效。
 */
internal fun SettingsPage.setDetailedKeySettings(on: Boolean) =
    toggleGlobal(
        "detailed_key_settings=${if (on) 1 else 0}",
        I18n.get(Strings.MORE_DETAILED_KEY_SETTINGS_TITLE),
        on,
    ) {
        it.copy(detailedKeySettings = on)
    }

/**
 * 写「使用预测返回手势」。
 *
 *  拨完**立刻重挂一次返回回调**（走 `onPredictiveBackChanged` 那条注入的通道 —— 这一层不许
 *  直接认识 `ui.hook`，见 `SettingsPage.onPredictiveBackChanged` 里那笔账），否则这一档要等
 *  下次进页才换，用户拨了看不见任何变化。重挂本身是幂等的：摘掉旧的、按新配置再挂。
 */
internal fun SettingsPage.setPredictiveBack(on: Boolean) {
    toggleGlobal(
        "predictive_back=${if (on) 1 else 0}",
        I18n.get(Strings.MORE_PREDICTIVE_BACK_TITLE),
        on,
    ) {
        it.copy(predictiveBack = on)
    }
    onPredictiveBackChanged()
}

/**
 * 写**页面缩放**（百分比）。
 *
 *  返回「写盘成没成」—— 滑杆失败时要拨回原值（见 `SliderRow` 的 `commit`）。
 */
internal fun SettingsPage.setPageScale(pct: Int): Boolean {
    val cfg = configState.config.value
    val value = pct.coerceIn(GlobalConfig.PAGE_SCALE_MIN, GlobalConfig.PAGE_SCALE_MAX)
    if (cfg.global.pageScale == value) return true
    return write(cfg.copy(global = cfg.global.copy(pageScale = value)), "page_scale=$value") != CommandsSave.Failed
}

/**
 * 切「随机图标配色」（外观那一行）。
 *
 *  开：**当场生成一个种子**再落盘 —— 这就是「一次生成、之后一直一样」的那一下；
 *      关掉再打开会重新生成，等于「换一批」。
 *  关：写回 0（`0` 就是「关」，见 [GlobalConfig.iconTintSeed]），不另存一个布尔位。
 */
internal fun SettingsPage.setRandomIconTint(on: Boolean) {
    val cfg = configState.config.value
    val seed = if (on) Random.nextInt(1, Int.MAX_VALUE) else GlobalConfig.DEFAULT_ICON_TINT_SEED
    if (cfg.global.iconTintSeed == seed) return
    writeAndNotice(cfg.copy(global = cfg.global.copy(iconTintSeed = seed)), "icon_tint_seed=$seed") {
        switchNotice(I18n.get(Strings.MORE_RANDOM_ICON_TINT), on)
    }
}

/**
 * 切「跟随系统显示大小」（外观那一行）。
 *
 *  两档的语义见 [GlobalConfig.followDisplaySize]：开 = 用系统那份密度（页面随系统「显示大小」
 *  一起放大缩小）；关 = 保持宿主那一份，再由 [setPageScale] 单独调。
 */
internal fun SettingsPage.setFollowDisplaySize(on: Boolean) {
    val cfg = configState.config.value
    if (cfg.global.followDisplaySize == on) return
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(followDisplaySize = on)),
        "follow_display_size=${if (on) 1 else 0}",
    ) { switchNotice(I18n.get(Strings.MORE_FOLLOW_DISPLAY_SIZE), on) }
}

/**
 * 切「显示常用命令」（按键设置页那一行）。
 *
 *  只影响**按键绑定页**那一段露不露：已经绑上去的动作照旧执行、命令库里那些命令一条不动。
 *  纯界面项，注入侧不认识界面，所以它不进广播（见 `ConfigFields.F_SHOW_COMMON_COMMANDS`）。
 */
internal fun SettingsPage.setShowCommonCommands(on: Boolean) {
    val cfg = configState.config.value
    if (cfg.global.showCommonCommands == on) return
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(showCommonCommands = on)),
        "show_common_commands=${if (on) 1 else 0}",
    ) { switchNotice(I18n.get(Strings.SETTING_SHOW_COMMON_COMMANDS), on) }
}

/**
 * 「常用命令」**整张清单**落盘 —— 钉一条 / 移除 / 改名 / 改「在按键绑定页显示」都走这里。
 *
 *  写整份而不是按 token 打补丁：这张表本来就小，而按 token 改要额外处理「用户刚把它删了」
 *  这类中间态；整份替换是一次原子写，与配置其它段同一套。
 *
 *  第一次写就把 `null`（= 用模块自带那几条）换成真列表 —— 从那以后这份清单只认配置，
 *  用户在页面上删掉自带的那几条就不会再冒回来。
 *
 *  @param notice 这一下**做成了什么**（「已固定」「已重命名」…）—— 由调用点给，不在这里写死：
 *   这条通道被四种动作共用，一句「已保存」说的是「文件写下去了」，而不是「你刚才那一下生效了」。
 *   改的是固定位、改的是名字、改的是图标，读到的却是同一句时，用户没法确认动的是不是自己想的那项。
 */
internal fun SettingsPage.setCommonCommands(
    list: List<CommonCommand>,
    notice: String,
) {
    val cfg = configState.config.value
    if (cfg.global.commonCommands == list) return
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(commonCommands = list)),
        "common_commands=${list.size}",
    ) { notice }
}

// 「**这一颗键**的参数」这一族 —— 与 `ui/write/ConfigWrite.kt`（全局开关 + 唯一的写入口 [write]）
// 分开：两者改的不是同一段配置（`keys[id]` 对 `global`），错放会让「这一项属于谁」看不出来。
//
// 长按阈值 / 连续触发间隔那几档是滑杆，各自在 `LongRepeatCard` 里直接调 [write]，不在这里再包一层。
//
// **回执的位一律 [HINT_SLOT_BINDING]**：这一族只在「按键设置」页被调用（那一页的三张卡），
// 回执共用一条消息（理由见 [HINT_SLOT_BINDING] 的注释）。

/**
 * 改**这一颗键**的一个开关：取配置 → 取键 → 应用 [transform] → 写回并回执。
 *
 *  形状收在 `updateKey` 一处（现读配置 / 值没变不落盘 / 只动 `keys[id]`），这里只补两样
 *  这一族特有的东西：回执的位固定 [HINT_SLOT_BINDING]（「按键绑定」页共用一条消息）、
 *  回执的标题由调用方给。
 *
 *  **改哪一颗由调用方给**（`keyId`）：原先在这里现取 `KeyWatchState.editing`，那是「按键绑定页
 *  当前选中的那颗」—— 这一族现在也从独立设置页被调用，那一份状态不再代表那一页在改谁。
 *
 * @param on 回执要说的目标状态（`switchNotice` 的第二个参数）
 */
internal fun SettingsPage.toggleKeyParam(
    keyId: KeyId,
    what: String,
    title: String,
    on: Boolean,
    transform: (KeyConfig) -> KeyConfig,
) {
    updateKey(keyId, what, notice = { switchNotice(title, on) }, slot = HINT_SLOT_BINDING, change = transform)
}

/**
 * 写「固定窗口起点」那一颗 —— 落的是 [KeyConfig.waitMultiWindow]（**反面**）。
 *
 *  判定侧的行为见 `inject/input/GestureDetector.onKeyDownLocked`：`waitMultiWindow = false`
 *  时从首次按下只等一个窗口、窗口内数到的多击算一次触发；`true` 时每一击之后都重新等一个
 *  完整窗口、按实际击数判。
 *
 *  **取反只在界面那一行做**（`waitMultiWindowRow` 的 `checked = !key.waitMultiWindow`）：
 *  这里收到的已经是存储值，回执却要说界面上那颗的状态 —— 所以传给 `toggleKeyParam` 的是
 *  `!waitMultiWindow`，别照抄。
 */
internal fun SettingsPage.setWaitMultiWindow(
    keyId: KeyId,
    waitMultiWindow: Boolean,
) {
    toggleKeyParam(
        keyId,
        "wait_multi_window=${if (waitMultiWindow) 1 else 0}",
        I18n.get(Strings.BINDING_WAIT_MULTI_WINDOW),
        !waitMultiWindow,
    ) { it.copy(waitMultiWindow = waitMultiWindow) }
}

// 「多条命令」那几步的增删改 —— 只动页面状态（草稿里的步骤、导航、「选应用」那一档），一行像素都不画。
//  放在 `ui` 根而不是 `ui/state`：`ui/state` 只装**状态对象**（谁的字段、谁能写），
//  这些是「对状态做的动作」，要读页面导航与草稿两个域，所以与 `PageText` / `PageFeedback` 同级。
//  这样 `screen/*` 与浮层都能直接调它，不必反过来依赖具体屏（见 `docs/architecture/decoupling.md` 动作 4）。

/**
 * 多条命令加一步（从「添加命令」浮层的「选择应用」挑完回来时调）。
 *
 * @return true = 真的加进去了；false = 满员被拒（已 toast）。调用方据此决定要不要退页 ——
 *   把「没加」当「加了」会让刚挑的应用凭空消失，用户一条提示都看不到。
 */
internal fun SettingsPage.addStep(token: String): Boolean {
    // 界面那一层的闸在别处：「添加命令」那颗按钮满员时自己置灰（`enabled = !stepsFull`），
    //  两个调用点也被它挡着。这里是**兜底** —— 越过界面直接调本函数时不能把步数顶过头。
    if (commandDraft.draft.steps.size >= Command.MAX_STEPS) {
        XLog.w("多条命令：已达上限 ${Command.MAX_STEPS} 步，拒绝再加 → $token")
        hint(I18n.format(Strings.TOAST_COMMAND_STEP_FULL, Command.MAX_STEPS), slot = HINT_SLOT_COMMAND)
        return false
    }
    commandDraft.draft.steps.add(token)
    XLog.w("多条命令：加第 ${commandDraft.draft.steps.size} 步 → $token")
    navTo(Screen.COMMANDS)
    return true
}

/**
 * 多条命令把第 [index] 步换成 [token] —— 点已添加的那一行弹出的选择菜单里挑完时调。
 *
 * @return true = 换成了；false = 那一步已经不在（已 toast 并退回命令页）
 */
internal fun SettingsPage.replaceStep(
    index: Int,
    token: String,
): Boolean {
    if (index !in commandDraft.draft.steps.indices) {
        // 越界不能静默 return：调用方已经 tick / 关了菜单，用户看不到任何反应也无从判断。
        XLog.w("多条命令：第 ${index + 1} 步不存在（当前 ${commandDraft.draft.steps.size} 步），换步已忽略")
        hint(I18n.get(Strings.TOAST_COMMAND_STEP_MISSING), slot = HINT_SLOT_COMMAND)
        navTo(Screen.COMMANDS)
        return false
    }
    commandDraft.draft.steps[index] = token
    XLog.w("多条命令：第 ${index + 1} 步换成 $token")
    navTo(Screen.COMMANDS)
    return true
}

/**
 * 从「添加命令」浮层（或点已添加那一步弹出的同一张选择菜单）进「打开应用」页 ——
 * 挑中的应用作为一步写进当前多条命令：[replaceIndex] < 0 时追加，`>= 0` 时把那一步换成它。
 *
 * 与网格那条入口的区别只有两处：不记手势（[AppPickerState.gesture] = null），以及调用点在
 * 「自定义命令」页上 —— 压进栈之后「打开应用」下面那一页就是「自定义命令」，返回自然退到它。
 */
internal fun SettingsPage.openAppPickerForStep(
    replaceIndex: Int = -1,
    stop: Boolean = false,
) {
    XLog.w("添加命令：进「打开应用」页挑一步（替换行=$replaceIndex，结束=$stop）")
    picker.openForStep(replaceIndex, stop)
    // 搜索词清空：它是页面级状态，不随页面出栈注销 —— 留着的话下次进来就是一份被过滤过的列表。
    picker.filter.value = ""
    // 「以小窗口打开」按**这次要编辑的那一步**重算（不落盘、不跨次记忆，见 [AppPickerState.smallWindow]；
    // 另一条入口在 SettingsPage 的「打开应用」格）
    resetSmallWindowForPicker()
    // 进页唯一复位点：点过的行回到当前绑定，定位 / 拦人窗 / 刷新指示器归零（清单不清）
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId())
    navTo(Screen.APP_PICKER)
}

/**
 * 「常用命令」页那层抽屉里点「打开应用 / 结束应用」—— 进「打开应用」页挑一条，
 * 挑中的**追加成清单里的一条**，或**换掉行菜单「修改」那一趟指的那一条**
 * （落点见 `PickTarget.CommonCommand`）。
 *
 *  与另外两条入口同一套进页复位（搜索词清空、小窗开关重算、`beginVisit`）：这一页的挑法本来就
 *  只有一份，差别只在「挑完写到哪儿」。
 *
 *  @param stop `true` = 「结束应用」（写 `proc:stop:包名`，不挑界面、也没有小窗）
 *  @param replaceToken 非空 = 换掉清单里的这一条（「修改」那一趟）；`null` = 追加一条
 */
internal fun SettingsPage.openAppPickerForCommonCommand(
    stop: Boolean = false,
    replaceToken: String? = null,
) {
    XLog.w("常用命令：进「打开应用」页挑一条（结束=$stop，换=${replaceToken ?: "否"}）")
    picker.openForCommonCommand(stop, replaceToken)
    picker.filter.value = ""
    resetSmallWindowForPicker()
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId())
    navTo(Screen.APP_PICKER)
}

/**
 * 长按卡「松手执行额外动作」那一行进「打开应用」页 —— 挑中的应用 / 组件 / 快捷方式写进这一档的
 * 松手格（落点见 `PickTarget.Release`：单键 / 组合是自己的 `longRelease`，场景块是草稿里那一块）。
 *
 *  与 [openAppPickerForStep] 同一套进页复位：搜索词清空、小窗开关按当前那一条重算。
 *  「以小窗口打开」在这一趟是**真生效**的（`window:` 是合法的额外动作）。
 *
 *  @param sceneAt 非负 = 这一趟是给**场景表里第几块**挑（「场景动作」页那一趟，见 `BindingTarget.Scene`）
 */
internal fun SettingsPage.openAppPickerForRelease(
    stop: Boolean = false,
    combo: String? = null,
    sceneAt: Int = AppPickerState.NOT_FOR_SCENE,
    chain: Boolean = false,
) {
    XLog.w("额外动作：进「打开应用」页挑一条（结束=$stop，组合=$combo，场景块=$sceneAt，单击后长按=$chain）")
    picker.openForRelease(stop, combo, sceneAt, chain)
    picker.filter.value = ""
    resetSmallWindowForPicker()
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId())
    navTo(Screen.APP_PICKER)
}
