package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.model.GlobalConfig

/**
 * 界面颜色的三档 —— 与 `GlobalConfig.theme` 的取值一一对应（那边只存字符串）。
 *
 *  三档的**顺序**就是菜单里的顺序，也是「从松到紧」的顺序：跟随系统 → 固定浅 → 固定深。
 */
internal enum class AppTheme(
    val code: String,
) {
    AUTO(GlobalConfig.DEFAULT_THEME),
    LIGHT(GlobalConfig.THEME_LIGHT),
    DARK(GlobalConfig.THEME_DARK),
    ;

    companion object {
        /**
         * 按 code 找档位；认不出一律落回「跟随系统」。
         *
         *  与 `PagePalette.ofId` 同一个理由：code 存在配置文件里，手改过、从别的版本导回来都可能
         *  带进不认识的档 —— 那时最合理的结果是回到系统给的那一档，而不是整页读不出主题。
         */
        fun ofCode(code: String?): AppTheme = entries.firstOrNull { it.code == code } ?: AUTO
    }
}
