package com.lookie.opluskey.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
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

    /** 「独立设置」：把**这一档**的独立设置关掉，改回跟随公共。 */
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
                            waitMultiWindow = false,
                            multiWaitMs = KeyConfig.FOLLOW_DOUBLE_CLICK_MS,
                            // 组合：等待 / 按住两组参数
                            comboWaitWindowMs = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                            comboWaitPairMs = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                            comboHoldWindowMs = GlobalConfig.DEFAULT_COMBO_WINDOW_MS,
                            comboHoldFollowerMs = GlobalConfig.DEFAULT_COMBO_HOLD_FOLLOWER_MS,
                            comboHoldMinMs = GlobalConfig.DEFAULT_COMBO_HOLD_MIN_MS,
                            comboSceneOnStarter = GlobalConfig.DEFAULT_COMBO_SCENE_ON_STARTER,
                            comboStarterKeepLong = GlobalConfig.DEFAULT_COMBO_STARTER_KEEP_LONG,
                            comboFixedWindowStart = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
                            comboHoldFixedWindowStart = GlobalConfig.DEFAULT_COMBO_FIXED_WINDOW_START,
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
 * 「独立设置」那一档：**关掉总开关、把播种标记也清掉**。
 *
 *  不逐个清字段：那些值不参与取值（`useOwnSettings` 一关就全走公共），留着等于给用户留一份
 *  「下次再打开还是上次那套」的底稿 —— 而 `ownSettingsSeeded` 清掉之后，下一次打开会按**当前**
 *  公共设置重新播一份，这才是「重置」该有的结果。
 */
private fun SettingsPage.resetKeySettings(cfg: Config): Config =
    when (val target = SettingsTargetState.current) {
        is SettingsTarget.Key -> {
            val key = cfg.key(target.id)
            cfg.copy(
                keys = cfg.keys + (target.id to key.copy(useOwnSettings = false, ownSettingsSeeded = false)),
            )
        }

        is SettingsTarget.Combo -> {
            cfg.copy(
                combos =
                    cfg.combos.map {
                        if (it.id == target.id) it.copy(useOwnSettings = false, ownSettingsSeeded = false) else it
                    },
            )
        }
    }
