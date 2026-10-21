package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

// 条件**取值**那半句话 —— 与 `CommandConditionText.kt` 分开：那边是「一条条件整句怎么说」，
// 这里只把某个档位值翻成几个字（「锁屏」「振动」「开」）。条件种类一多，两族各自都在长，
// 而「加一种取值」与「加一种条件」是两件事。

/** 响铃三档在界面上叫什么 —— 条件行与条件菜单共用。 */
internal fun ringerLabel(mode: String): String =
    when (mode) {
        "vibrate" -> I18n.get(Strings.COMMAND_COND_RINGER_VIBRATE, "振动")
        "silent" -> I18n.get(Strings.COMMAND_COND_RINGER_SILENT, "静音")
        else -> I18n.get(Strings.COMMAND_COND_RINGER_NORMAL, "响铃")
    }

/** 屏幕方向两档在界面上叫什么。 */
internal fun orientationLabel(value: String): String =
    when (value) {
        "landscape" -> I18n.get(Strings.COMMAND_COND_ORIENTATION_LANDSCAPE, "横屏")
        else -> I18n.get(Strings.COMMAND_COND_ORIENTATION_PORTRAIT, "竖屏")
    }

/** Wi-Fi 开关两档在界面上叫什么。 */
internal fun wifiLabel(value: String): String =
    when (value) {
        "off" -> I18n.get(Strings.COMMAND_COND_WIFI_OFF, "关")
        else -> I18n.get(Strings.COMMAND_COND_WIFI_ON, "开")
    }

/** 媒体状态两档在界面上叫什么。 */
internal fun mediaLabel(value: String): String =
    when (value) {
        "paused" -> I18n.get(Strings.COMMAND_COND_MEDIA_PAUSED, "暂停中")
        else -> I18n.get(Strings.COMMAND_COND_MEDIA_PLAYING, "播放中")
    }

/** 屏幕状态两档在界面上叫什么（亮屏 / 锁屏）。 */
internal fun screenLabel(value: String): String =
    when (value) {
        "locked" -> I18n.get(Strings.COMMAND_COND_LOCKED, "锁屏")
        else -> I18n.get(Strings.COMMAND_COND_SCREEN_ON, "亮屏")
    }

/** 充电状态两档在界面上叫什么（充电中 / 没在充电）。 */
internal fun chargingLabel(value: String): String =
    when (value) {
        "idle" -> I18n.get(Strings.COMMAND_COND_CHARGING_OFF, "没在充电")
        else -> I18n.get(Strings.COMMAND_COND_CHARGING, "充电中")
    }

/** 耳机状态两档在界面上叫什么（已连接耳机 / 未连接耳机）。 */
internal fun headsetLabel(value: String): String =
    when (value) {
        "disconnected" -> I18n.get(Strings.COMMAND_COND_HEADSET_OFF, "未连接耳机")
        else -> I18n.get(Strings.COMMAND_COND_HEADSET, "已连接耳机")
    }
