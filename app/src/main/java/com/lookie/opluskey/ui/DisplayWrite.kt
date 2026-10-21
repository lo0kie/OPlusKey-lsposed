package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.state.CommandsSave

// 「页面大小」这一族的两颗写入口：**跟随系统显示大小** + **页面缩放**。
//
// 单独一个文件的理由：这两项改的是同一件事（`global.followDisplaySize` / `global.pageScale`
// 在「外观」卡里成对出现，缩放那一行还跟着开关一起进出场），与 `ConfigWrite.kt` 里那批
// 互不相干的全局开关不是一类；挤回去还会把那个文件顶到 `TooManyFunctions` 的线上
// （那里已经 9 颗，阈值 11）。两族各自的边界见 `ConfigWrite.kt` 头部那段。

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
