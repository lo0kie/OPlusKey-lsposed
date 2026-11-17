package com.lookie.opluskey.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HeaderIconButton
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.feedback.DisabledHint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.state.page.SettingsTarget
import com.lookie.opluskey.ui.state.page.SettingsTargetState
import com.lookie.opluskey.ui.wire.disabledClickReason
import com.lookie.opluskey.ui.write.write
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

// 三页顶栏那颗「重置」（`eraser`）：更多选项 / 按键设置 / 独立设置。
//
//  一颗按钮 + 一张确认窗，**重置范围按页面各算一份**（[ResetScope]）：重置的是「这一页上的
//  那几项」，不碰按键、组合、命令与别的页的设置 —— 那一档正门在首页 ⋮ 的「重置模块」，
//  两者不该被同一颗按钮混为一谈。
//
//  放在这一层而不是各页各写一颗：三处的位置（顶栏最右）、禁用判据（整页只读）、确认窗形状
//  必须一样，各写一遍迟早有三套。

/** 这颗「重置」管哪一页 —— 决定确认窗正文与写回范围。 */
internal enum class ResetScope {
    /** 「更多选项」：语言、主题、配色与「显示更详细的按键设置」。 */
    ABOUT,

    /** 「按键设置」（公共设置）：接口、振动、提示、单击与组合参数。 */
    GENERAL,

    /** 「独立设置」：关掉主开关，并把**这一档**这一页上所有项写回默认值。 */
    KEY_SETTINGS,
}

/**
 * 顶栏那颗「重置」—— 点一下弹确认窗，确认后按 [scope] 写回。
 *
 *  **只读态灰着**（与页里其它控件同一句原因，见 `disabledClickReason`）：配置压根没读成功时
 *  写回会把默认值固化成用户的真相。
 */
@Composable
internal fun SettingsPage.HeaderReset(scope: ResetScope) {
    var asking by remember { mutableStateOf(false) }
    val writable = pageWritable()
    DisabledHint(if (writable) null else disabledClickReason()) {
        HeaderIconButton(onClick = { asking = true }, enabled = writable) {
            Icon(
                PageIcons.MenuReset,
                contentDescription = I18n.get(Strings.COMMON_RESET),
                tint =
                    if (writable) {
                        COUITheme.colorScheme.onSurfaceContainer
                    } else {
                        COUITheme.colorScheme.disabledOnSecondaryVariant
                    },
            )
        }
    }
    ConfirmDialog(
        show = asking,
        title = I18n.get(Strings.COMMON_RESET),
        summary = I18n.get(messageOf(scope)),
        action = I18n.get(Strings.COMMON_RESET),
        onDismiss = { asking = false },
        onConfirm = {
            asking = false
            applyReset(scope)
        },
    )
}

/** 三页各自的正文 —— 说清「这一下会动什么、不会动什么」。 */
private fun messageOf(scope: ResetScope): String =
    when (scope) {
        ResetScope.ABOUT -> Strings.RESET_ABOUT_MESSAGE
        ResetScope.GENERAL -> Strings.RESET_GENERAL_MESSAGE
        ResetScope.KEY_SETTINGS -> Strings.RESET_KEY_SETTINGS_MESSAGE
    }

/**
 * 按 [scope] 把这一页的那几项写回默认。
 *
 *  默认值一律引 `GlobalConfig` / `KeyConfig` 的 `DEFAULT_*` 常量（DTO 那一层的 `null` 缺省正是
 *  用它们兜底的）—— 不在这里抄一份字面量：抄下来迟早与那边走散。
 */
private fun SettingsPage.applyReset(scope: ResetScope) {
    val cfg = configState.config.value
    val next =
        when (scope) {
            ResetScope.ABOUT -> {
                cfg.copy(
                    global =
                        cfg.global.copy(
                            language = GlobalConfig.DEFAULT_LANGUAGE,
                            theme = GlobalConfig.DEFAULT_THEME,
                            palette = GlobalConfig.DEFAULT_PALETTE,
                            detailedKeySettings = GlobalConfig.DEFAULT_DETAILED_KEY_SETTINGS,
                        ),
                )
            }

            ResetScope.GENERAL -> {
                cfg.copy(
                    global =
                        cfg.global.copy(
                            // 接口
                            colorOsApi = GlobalConfig.DEFAULT_COLOR_OS_API,
                            // 按键行为：振动与提示
                            //  `vibrate` 没有 `DEFAULT_*` 常量，缺省就在 `ConfigFields.F_VIBRATE`（`Bool(false)`）
                            vibrate = false,
                            vibrateStrength = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
                            vibrateOnNone = GlobalConfig.DEFAULT_VIBRATE_ON_NONE,
                            toastOnRun = GlobalConfig.DEFAULT_TOAST_ON_RUN,
                            toastFluid = GlobalConfig.DEFAULT_TOAST_FLUID,
                            toastTemplate = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
                            miniWindowAnim = GlobalConfig.DEFAULT_MINI_WINDOW_ANIM,
                            // 单击
                            doubleClickMs = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
                            //  `waitMultiWindow` 也没有 `DEFAULT_*` 常量：出厂值就是它在 `GlobalConfig`
                            //  上的声明（`true` = 界面那颗「固定窗口起点」**关**，每次松手重新起算）。
                            //  原先这里写 `false`，重置出来的是「固定窗口起点开」—— 与全新装的那一份相反，
                            //  也与下面那颗「独立设置」重置对不上。
                            waitMultiWindow = true,
                            //  「等待窗口」出厂是 250（逐次等那一档，`multiWaitMs`）；0 只留给
                            //  老配置读，复位出来的应当与全新装的那一份逐字一致
                            multiWaitMs = KeyConfig.DEFAULT_MULTI_WAIT_MS,
                            // 组合：等待 / 按住两组参数
                            comboWaitWindowMs = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                            comboWaitPairMs = GlobalConfig.DEFAULT_COMBO_PAIR_MS,
                            comboHoldWindowMs = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                            comboHoldFollowerMs = GlobalConfig.DEFAULT_COMBO_PAIR_MS,
                            comboHoldMinMs = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
                            comboSceneOnStarter = GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER,
                            comboStarterKeepLong = GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG,
                            comboFixedWindowStart = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
                            comboHoldFixedWindowStart = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
                            // 组合：顺序组合那一组（与上面四格 + 两颗开关各一份）
                            comboOrderedWaitWindowMs = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                            comboOrderedWaitPairMs = GlobalConfig.DEFAULT_COMBO_PAIR_MS,
                            comboOrderedHoldWindowMs = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                            comboOrderedHoldFollowerMs = GlobalConfig.DEFAULT_COMBO_PAIR_MS,
                            comboOrderedFixedWindowStart = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
                            comboOrderedHoldFixedWindowStart = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
                        ),
                )
            }

            ResetScope.KEY_SETTINGS -> {
                resetKeySettings(cfg)
            }
        }
    write(next, "reset=${scope.name.lowercase()}")
}

/**
 * 「独立设置」那一档：**关掉主开关，并把这一页上所有项写回默认值**。
 *
 *  「写回默认值」= 把这一档自己的那几格写成**没写过**的形态：
 *  · 那七项公共项（接口 / 振动 / 强度 / 无操作振动 / 提示 / 流体云 / 文案）各自回到 `DEFAULT_*`；
 *  · 单键还有「单击」那张卡的三位（判定时机 / 等待窗口 / 单击延迟）；
 *  · 组合那几格（四格窗口、顺序组合四格、`holdMinMs`、场景时机、首键长按、四颗固定窗口起点）
 *    回到 `0` / `null` —— 那就是「没写过」，取值时按出厂默认算（`Config.combo*For`）。
 *    比写死一堆具体数字更贴近「刚建出来这一档」的样子，也让文件与新建那一档逐字一致。
 *
 *  关主开关与清值是**两件事**：开关只说「此刻用不用这一份」，值仍留在盘上。用户要的重置是
 *  两者一起 —— 关掉之后这一档跟着公共走，重新打开时看到的是默认值，而不是上次留下的那一套。
 *
 *  动作绑定（短按 / 长按 / 场景表 / 多击）**一律不动**：那些是这一颗键 / 这一组键「是什么」，
 *  不是「独立设置」这一页上的项。
 */
private fun SettingsPage.resetKeySettings(cfg: Config): Config =
    when (val target = SettingsTargetState.current) {
        is SettingsTarget.Key -> {
            val key = cfg.key(target.id).ownReset()
            cfg.copy(keys = cfg.keys + (target.id to key))
        }

        is SettingsTarget.Combo -> {
            cfg.copy(
                combos = cfg.combos.map { if (it.id == target.id) it.ownReset() else it },
            )
        }
    }

/**
 * 一颗键的独立设置写回默认 —— 动的是**这一页上所有吃总开关的项**，不是只有那七项公共项。
 *
 *  除了接口 / 振动 / 提示那七项，单键这一页还有一张「单击」（判定时机 + 等待窗口，`ClickDelayCard`）：
 *  它同样吃「使用独立设置」（关着就跟公共走、这一页只把生效值摆出来并灰着），所以重置也得带上
 *  它那三位 —— 漏了的表现正是「按了重置，固定窗口还是原来那档」。
 *
 *  另外三位写成模型上的出厂值：`waitMultiWindow = true`（= 界面那颗「固定窗口起点」**关**，
 *  每次松手重新起算）、`multiWaitMs = 250`（逐次等那一档）、`doubleClickMs = 350`（固定等那一档）。
 */
private fun KeyConfig.ownReset(): KeyConfig =
    copy(
        useOwnSettings = false,
        colorOsApi = GlobalConfig.DEFAULT_COLOR_OS_API,
        //  `vibrate` 没有 `DEFAULT_*` 常量，缺省就在 `ConfigFields.F_KEY_VIBRATE`（`Bool(false)`）
        vibrate = false,
        vibrateStrength = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
        vibrateOnNone = GlobalConfig.DEFAULT_VIBRATE_ON_NONE,
        toastOnRun = GlobalConfig.DEFAULT_TOAST_ON_RUN,
        toastFluid = GlobalConfig.DEFAULT_TOAST_FLUID,
        toastTemplate = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
        //  「单击」那张卡：判定时机 + 等待窗口
        //  `waitMultiWindow` 同样没有 `DEFAULT_*` 常量，出厂值就是它在 `KeyConfig` 上的声明（`true`）
        doubleClickMs = KeyConfig.DEFAULT_DOUBLE_CLICK_MS,
        waitMultiWindow = true,
        multiWaitMs = KeyConfig.DEFAULT_MULTI_WAIT_MS,
    )

/**
 * 一组组合键的独立设置写回默认 —— 上面那七项，加上组合独有的那几格。
 *
 *  组合独有的那几格写成 `0` / `null`（= 没写过）而不是具体的出厂数字：取值侧遇到这两个值
 *  就取出厂默认（`Config.comboWindowsFor` / `comboHoldMinFor` / `comboSceneOnStarterFor` …），
 *  两边的口径只有一处。
 */
private fun ComboKey.ownReset(): ComboKey =
    copy(
        useOwnSettings = false,
        colorOsApi = GlobalConfig.DEFAULT_COLOR_OS_API,
        vibrate = false,
        vibrateStrength = GlobalConfig.DEFAULT_VIBRATE_STRENGTH,
        vibrateOnNone = GlobalConfig.DEFAULT_VIBRATE_ON_NONE,
        toastOnRun = GlobalConfig.DEFAULT_TOAST_ON_RUN,
        toastFluid = GlobalConfig.DEFAULT_TOAST_FLUID,
        toastTemplate = GlobalConfig.DEFAULT_TOAST_TEMPLATE,
        //  不分顺序那四格
        waitWindowMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        waitPairMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        holdWindowMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        holdFollowerMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        //  顺序组合那四格（与上面四格各存各的）
        orderedWaitWindowMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        orderedWaitPairMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        orderedHoldWindowMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        orderedHoldFollowerMs = ComboKey.WINDOW_FOLLOW_GLOBAL,
        holdMinMs = ComboKey.HOLD_MIN_FOLLOW_GLOBAL,
        //  四个开关：`null` = 没写过
        sceneOnStarter = null,
        starterKeepLong = null,
        fixedWindowStart = null,
        holdFixedWindowStart = null,
        orderedFixedWindowStart = null,
        orderedHoldFixedWindowStart = null,
    )
