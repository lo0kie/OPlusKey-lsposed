package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.model.languageLabel
import com.lookie.opluskey.ui.state.CommandsSave

// 「全局开关」这一族：一颗开关一个入口，全部落到 `global.*` 上。
//
// 与 `ui/ConfigWrite.kt` 的关系：那边是**机制**（[write] 的只读态 / 落盘 / 广播 / 重问），
// 这边是**动作**（改哪个字段、回执说什么）。混在一个文件里会让「加一颗开关」与「改落盘流程」
// 撞在同一处，也会顶破 detekt 的单文件函数数上限。
//
// 「**这一颗键**的参数」那半在 `ui/KeyParamsWrite.kt`（长按连续触发 / 忽视无操作双击）：
// 写的是 `keys[id]` 而不是 `global`，分开放才看得出「这一项属于谁」。
//
// 「页面大小」那两颗（跟随系统显示大小 / 页面缩放）在 `ui/DisplayWrite.kt` —— 它们是一件事的两半，
// 与这里这些互不相干的开关不同类。

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
 * 切「执行时提示」（执行命令时弹一条 Toast）—— 行点击与开关共用这一个入口。
 * 起从每条命令自己的字段提成全局开关（见 `GlobalConfig.toastOnRun`）；
 * 判定侧在 `host/action/CustomCommandRunner`，宿主进程每次执行现读配置，拨完立刻生效。
 */
internal fun SettingsPage.setToastOnRun(on: Boolean) =
    toggleGlobal("toast_on_run=${if (on) 1 else 0}", I18n.get(Strings.HOME_TOAST_TITLE, "执行时提示"), on) {
        it.copy(toastOnRun = on)
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
 *  屏幕上始终是那一条在改字（「已关闭「按键绑定」」↔「已开启「按键绑定」」）。
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
