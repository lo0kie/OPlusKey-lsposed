package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

// 条件**取值**那半句话 —— 与 `CommandConditionText.kt` 分开：那边是「一条条件整句怎么说」，
// 这里只把某个档位值翻成几个字（「锁屏」「振动」「开」）。条件种类一多，两族各自都在长，
// 而「加一种取值」与「加一种条件」是两件事。
//
// **每一档都把「正面那一档」显式列出来，其余一律交给 [unknownValue]**：
//  执行侧比的是精确串（`else -> false`，见 `ConditionProbe`），界面这边拿 `else ->` 兜成
//  「亮屏」「响铃」的话，一份来自更新版本 / 手改过的配置会显示成「配好了」，而那条规则永远不成立
//  —— 用户照着界面排查，只会找一个不存在的问题。

/**
 * 认不出的取值怎么显示 —— 把原值写出来。
 *
 *  空串是「还没配」（老配置里那几档允许空取值，执行侧按默认档解释），那一档说「未设置」；
 *  其余认不出的取值直接显示原文：它来自更新的版本或手改，原样露出来才看得出「这一条不是我以为的那个档」。
 */
private fun unknownValue(value: String): String = if (value.isBlank()) I18n.get(Strings.COMMAND_COND_VALUE_UNSET, "未设置") else value

/** 响铃三档在界面上叫什么 —— 条件行与条件菜单共用。 */
internal fun ringerLabel(mode: String): String =
    when (mode) {
        "vibrate" -> I18n.get(Strings.COMMAND_COND_RINGER_VIBRATE, "振动")
        "silent" -> I18n.get(Strings.COMMAND_COND_RINGER_SILENT, "静音")
        "normal" -> I18n.get(Strings.COMMAND_COND_RINGER_NORMAL, "响铃")
        else -> unknownValue(mode)
    }

/** 屏幕方向两档在界面上叫什么。 */
internal fun orientationLabel(value: String): String =
    when (value) {
        "landscape" -> I18n.get(Strings.COMMAND_COND_ORIENTATION_LANDSCAPE, "横屏")
        "portrait" -> I18n.get(Strings.COMMAND_COND_ORIENTATION_PORTRAIT, "竖屏")
        else -> unknownValue(value)
    }

/** Wi-Fi 开关两档在界面上叫什么。 */
internal fun wifiLabel(value: String): String =
    when (value) {
        "off" -> I18n.get(Strings.COMMAND_COND_WIFI_OFF, "关")
        "on" -> I18n.get(Strings.COMMAND_COND_WIFI_ON, "开")
        else -> unknownValue(value)
    }

/**
 * 「开 / 关」型系统开关的两档 —— 蓝牙 / 移动数据 / 飞行模式共用。
 *
 *  字典里那两个键（`command.cond_wifi_on` / `_off`）的文案就是「开」「关」，与主语无关，
 *  所以这里直接复用、不再新增一对同义的键。以后要是把这两个键改名成通用的
 *  `..._switch_on`，只改这一处即可。
 */
internal fun switchLabel(value: String): String = wifiLabel(value)

/** 媒体状态两档在界面上叫什么。 */
internal fun mediaLabel(value: String): String =
    when (value) {
        "paused" -> I18n.get(Strings.COMMAND_COND_MEDIA_PAUSED, "暂停中")
        "playing" -> I18n.get(Strings.COMMAND_COND_MEDIA_PLAYING, "播放中")
        else -> unknownValue(value)
    }

/** 屏幕状态两档在界面上叫什么（亮屏 / 锁屏）。 */
internal fun screenLabel(value: String): String =
    when (value) {
        "locked" -> I18n.get(Strings.COMMAND_COND_LOCKED, "锁屏")
        "on" -> I18n.get(Strings.COMMAND_COND_SCREEN_ON, "亮屏")
        else -> unknownValue(value)
    }

/** 充电状态两档在界面上叫什么（充电中 / 没在充电）。 */
internal fun chargingLabel(value: String): String =
    when (value) {
        "idle" -> I18n.get(Strings.COMMAND_COND_CHARGING_OFF, "没在充电")
        "charging" -> I18n.get(Strings.COMMAND_COND_CHARGING, "充电中")
        else -> unknownValue(value)
    }

/** 耳机状态两档在界面上叫什么（已连接耳机 / 未连接耳机）。 */
internal fun headsetLabel(value: String): String =
    when (value) {
        "disconnected" -> I18n.get(Strings.COMMAND_COND_HEADSET_OFF, "未连接耳机")
        "connected" -> I18n.get(Strings.COMMAND_COND_HEADSET, "已连接耳机")
        else -> unknownValue(value)
    }
