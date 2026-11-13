package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ConditionKind

// 条件**取值**那半句话 —— 「静音」「开」「亮屏」「22:00-07:00」。
//
//  为什么在 `core`：这段文案有两处要念同一份 —— 界面上的条件行（`ui/model/text/CommandConditionText`），
//  以及执行侧把 `${sys.wifi.text}` 展开成「开」的时候（宿主进程里）。执行侧够不着 `ui/`
//  （host 不许引界面层），所以实现落在两个进程共用的这一层；`ui/model/text/ConditionValueText`
//  只留同名的一层转发，界面那边的调用点一行都不用改。
//
// **每一档都把「正面那一档」显式列出来，其余一律交给 [unknownValue]**：
//  执行侧比的是精确串（`else -> false`，见 `ConditionProbe`），界面这边拿 `else ->` 兜成
//  「亮屏」「响铃」的话，一份来自更新版本 / 手改过的配置会显示成「配好了」，而那条规则永远不成立
//  —— 用户照着界面排查，只会找一个不存在的问题。

/**
 * 条件取值在界面上念什么 —— 按**档位**分派到下面那几支。
 *
 *  应用那一族的取值是包名（要过 `appNameOf` 才念得出应用名，那一层在 `ui/`）、时间区间的取值
 *  本身就是一句「22:00-07:00」—— 这两类没有可翻译的那一步，原样返回。
 */
fun conditionValueLabel(
    kind: ConditionKind,
    value: String,
): String =
    when (kind) {
        ConditionKind.RINGER -> ringerLabel(value)

        ConditionKind.ORIENTATION -> orientationLabel(value)

        ConditionKind.SCREEN -> screenLabel(value)

        ConditionKind.CHARGING -> chargingLabel(value)

        ConditionKind.HEADSET -> headsetLabel(value)

        ConditionKind.MEDIA -> mediaLabel(value)

        ConditionKind.WIFI -> wifiLabel(value)

        //  四个「开 / 关」型系统开关共用一个词表（「开」「关」），所以走同一支
        ConditionKind.BLUETOOTH, ConditionKind.CELLULAR,
        ConditionKind.AIRPLANE, ConditionKind.FLASHLIGHT,
        -> switchLabel(value)

        ConditionKind.APP, ConditionKind.APP_NOT, ConditionKind.TIME -> value
    }

/**
 * 认不出的取值怎么显示 —— 把原值写出来。
 *
 *  空串是「还没配」（老配置里那几档允许空取值，执行侧按默认档解释），那一档说「未设置」；
 *  其余认不出的取值直接显示原文：它来自更新的版本或手改，原样露出来才看得出「这一条不是我以为的那个档」。
 */
private fun unknownValue(value: String): String = if (value.isBlank()) I18n.get(Strings.COMMAND_COND_VALUE_UNSET, "未设置") else value

/** 响铃三档在界面上叫什么 —— 条件行、条件菜单与 `${sys.ringer.text}` 共用。 */
fun ringerLabel(mode: String): String =
    when (mode) {
        "vibrate" -> I18n.get(Strings.COMMAND_COND_RINGER_VIBRATE, "振动")
        "silent" -> I18n.get(Strings.COMMAND_COND_RINGER_SILENT, "静音")
        "normal" -> I18n.get(Strings.COMMAND_COND_RINGER_NORMAL, "响铃")
        else -> unknownValue(mode)
    }

/** 屏幕方向两档在界面上叫什么。 */
fun orientationLabel(value: String): String =
    when (value) {
        "landscape" -> I18n.get(Strings.COMMAND_COND_ORIENTATION_LANDSCAPE, "横屏")
        "portrait" -> I18n.get(Strings.COMMAND_COND_ORIENTATION_PORTRAIT, "竖屏")
        else -> unknownValue(value)
    }

/** Wi-Fi 开关两档在界面上叫什么。 */
fun wifiLabel(value: String): String =
    when (value) {
        "off" -> I18n.get(Strings.COMMAND_COND_WIFI_OFF, "关")
        "on" -> I18n.get(Strings.COMMAND_COND_WIFI_ON, "开")
        else -> unknownValue(value)
    }

/**
 * 「开 / 关」型系统开关的两档 —— 蓝牙 / 移动数据 / 飞行模式 / 手电筒共用。
 *
 *  字典里那两个键（`command.cond_wifi_on` / `_off`）的文案就是「开」「关」，与主语无关，
 *  所以这里直接复用、不再新增一对同义的键。
 */
fun switchLabel(value: String): String = wifiLabel(value)

/** 媒体状态两档在界面上叫什么。 */
fun mediaLabel(value: String): String =
    when (value) {
        "paused" -> I18n.get(Strings.COMMAND_COND_MEDIA_PAUSED, "暂停中")
        "playing" -> I18n.get(Strings.COMMAND_COND_MEDIA_PLAYING, "播放中")
        else -> unknownValue(value)
    }

/** 屏幕状态三档在界面上叫什么（亮屏 / 熄屏 / 锁屏）。 */
fun screenLabel(value: String): String =
    when (value) {
        "locked" -> I18n.get(Strings.COMMAND_COND_LOCKED, "锁屏")
        "off" -> I18n.get(Strings.COMMAND_COND_SCREEN_OFF, "熄屏")
        "on" -> I18n.get(Strings.COMMAND_COND_SCREEN_ON, "亮屏")
        else -> unknownValue(value)
    }

/** 充电状态两档在界面上叫什么（充电中 / 没在充电）。 */
fun chargingLabel(value: String): String =
    when (value) {
        "idle" -> I18n.get(Strings.COMMAND_COND_CHARGING_OFF, "没在充电")
        "charging" -> I18n.get(Strings.COMMAND_COND_CHARGING, "充电中")
        else -> unknownValue(value)
    }

/** 耳机状态两档在界面上叫什么（已连接耳机 / 未连接耳机）。 */
fun headsetLabel(value: String): String =
    when (value) {
        "disconnected" -> I18n.get(Strings.COMMAND_COND_HEADSET_OFF, "未连接耳机")
        "connected" -> I18n.get(Strings.COMMAND_COND_HEADSET, "已连接耳机")
        else -> unknownValue(value)
    }
