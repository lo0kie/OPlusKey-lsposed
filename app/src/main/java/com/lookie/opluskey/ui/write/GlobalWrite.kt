package com.lookie.opluskey.ui.write

import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.HINT_SLOT_COMBO
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.switchNotice
import com.lookie.opluskey.ui.feedback.switchToNotice
import com.lookie.opluskey.ui.feedback.writeAndNotice
import com.lookie.opluskey.ui.model.text.languageLabel
import com.lookie.opluskey.ui.state.command.CommandsSave
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
    toggleGlobal("vibrate=${if (on) 1 else 0}", I18n.get(Strings.HOME_VIBRATE_TITLE, "振动反馈"), on) {
        it.copy(vibrate = on)
    }

/**
 * 写**振动强度**（三档，见 [GlobalConfig.VIBRATE_STRENGTH_RANGE]）。
 *
 *  只改强度、不动 [GlobalConfig.vibrate]：那一行的开关管「振不振」，这一行管「振多重」，
 *  两层各自独立（关掉振动时这一行整体不出现，见 `HomeActionsCard`）。
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
            I18n.get(Strings.MORE_LANGUAGE, "语言"),
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
        switchNotice(I18n.get(Strings.COMBO_SCENE_ON_STARTER_TITLE, "系统场景在首键按下时固定"), on)
    }
}

/**
 * 写「首键要先按住多久」（毫秒）**公共那一档** —— 按住手势的下限。
 *
 *  判定在注入侧（`ComboDetector` 的 `holdMinFor`），所以走通用写入通道：落盘 + 广播 + 重问注入侧。
 *  返回「写盘成没成」：滑杆失败时要拨回原值（与「按键之间的等待」那一行同一条）。
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
        I18n.get(Strings.HOME_MINI_ANIM_TITLE, "前台应用转小窗时使用动画"),
        on,
    ) { it.copy(miniWindowAnim = on) }

/** 切「使用 ColorOS 接口」—— 行点击与开关共用这一个入口。 */
internal fun SettingsPage.setColorOsApi(on: Boolean) =
    toggleGlobal("color_os_api=${if (on) 1 else 0}", I18n.get(Strings.HOME_COLOROS_TITLE, "使用 ColorOS 接口"), on) {
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
private fun moduleEnabledNotice(on: Boolean): String = switchNotice(I18n.get(Strings.SETTING_KEY_BINDING, "按键绑定"), on)

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
 * 切「执行时提示」（执行命令时弹一条 Toast）—— 行点击与开关共用这一个入口。
 * 起从每条命令自己的字段提成全局开关（见 `GlobalConfig.toastOnRun`）；
 * 判定侧在 `host/GestureExec`，宿主进程每次执行现读配置，拨完立刻生效。
 */
internal fun SettingsPage.setToastOnRun(on: Boolean) =
    toggleGlobal("toast_on_run=${if (on) 1 else 0}", I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"), on) {
        it.copy(toastOnRun = on)
    }

/**
 * 切「执行时提示」的**落点**（Toast / 流体云）—— 与 [setToastOnRun] 同一张卡、同一档语义：
 * 只换提示长在哪儿，文案仍是 [setToastTemplate] 那一句。
 *
 * 判定侧在 `host/GestureExec.noticeIfNeeded`，宿主进程每次执行现读配置，拨完立刻生效。
 * 图标来自**被执行的那个动作**，由界面层预热（`ui/model/icon/ActionCardArt`）。
 */
internal fun SettingsPage.setToastFluid(on: Boolean) =
    toggleGlobal("toast_fluid=${if (on) 1 else 0}", I18n.get(Strings.HOME_TOAST_FLUID_TITLE, "用流体云提示"), on) {
        it.copy(toastFluid = on)
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

// 「页面怎么显示」这一族的写入口：**跟随系统显示大小** + **页面缩放** + **显示常用命令**。
//
//  这几项改的是页面自己的呈现（`global.followDisplaySize` / `global.pageScale` 在「外观」卡里
//  成对出现，缩放那一行还跟着开关一起进出场；`global.showCommonCommands` 决定绑定页那一段
//  露不露），与上面那批「按键怎么执行」的全局开关不是一类。

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
        switchNotice(I18n.get(Strings.MORE_RANDOM_ICON_TINT, "随机图标配色"), on)
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
    ) { switchNotice(I18n.get(Strings.MORE_FOLLOW_DISPLAY_SIZE, "跟随系统显示大小"), on) }
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
    ) { switchNotice(I18n.get(Strings.SETTING_SHOW_COMMON_COMMANDS, "显示常用命令"), on) }
}

/**
 * 「常用命令」**整张清单**落盘 —— 钉一条 / 移除 / 改名 / 改「在按键绑定页显示」都走这里。
 *
 *  写整份而不是按 token 打补丁：这张表本来就小，而按 token 改要额外处理「用户刚把它删了」
 *  这类中间态；整份替换是一次原子写，与配置其它段同一套。
 *
 *  第一次写就把 `null`（= 用模块自带那几条）换成真列表 —— 从那以后这份清单只认配置，
 *  用户在页面上删掉自带的那几条就不会再冒回来。
 */
internal fun SettingsPage.setCommonCommands(list: List<CommonCommand>) {
    val cfg = configState.config.value
    if (cfg.global.commonCommands == list) return
    writeAndNotice(
        cfg.copy(global = cfg.global.copy(commonCommands = list)),
        "common_commands=${list.size}",
    ) { I18n.get(Strings.COMMON_COMMANDS_SAVED, "常用命令已保存") }
}
