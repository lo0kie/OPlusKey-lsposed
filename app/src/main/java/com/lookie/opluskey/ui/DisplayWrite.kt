package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.CommonCommand
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.ui.state.CommandsSave
import kotlin.random.Random

// 「页面怎么显示」这一族的写入口：**跟随系统显示大小** + **页面缩放** + **显示常用命令**。
//
// 单独一个文件的理由：这几项改的是页面自己的呈现（`global.followDisplaySize` / `global.pageScale`
// 在「外观」卡里成对出现，缩放那一行还跟着开关一起进出场；`global.showCommonCommands` 决定
// 绑定页那一段露不露），与 `GlobalWrite.kt` 里那批「按键怎么执行」的全局开关不是一类；
// 挤回去还会把那个文件顶到 `TooManyFunctions` 的线上（阈值 11）。
// 两族各自的边界见 `GlobalWrite.kt` 头部那段。

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
