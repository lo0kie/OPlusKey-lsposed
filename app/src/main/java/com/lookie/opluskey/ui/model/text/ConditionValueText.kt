package com.lookie.opluskey.ui.model.text

import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.present.conditionValueLabel

// 条件**取值**那半句话（「锁屏」「振动」「开」）—— **实现只有一份**，在
// `core/present/ConditionValueText`。
//
//  为什么这一层还在：那边是「两个进程都要念同一段文案」的地方（执行侧把 `${sys.wifi.text}`
//  展开成「开」时宿主进程里也要念），所以实现必须落在 `core`；而这一层留着的是**界面侧沿用
//  多年的那几个名字**（`CommandConditionText` 与条件菜单都按它们写），删掉就要动十几处调用点。
//  于是这里每条只留一行转发 —— 别在这里写分支，加一档取值请去 core 那份。

internal fun ringerLabel(mode: String): String = conditionValueLabel(ConditionKind.RINGER, mode)

internal fun orientationLabel(value: String): String = conditionValueLabel(ConditionKind.ORIENTATION, value)

internal fun wifiLabel(value: String): String = conditionValueLabel(ConditionKind.WIFI, value)

/** 「开 / 关」那四档共用（文案与主语无关，见 `core/present/ConditionValueText.switchLabel`）。 */
internal fun switchLabel(value: String): String = conditionValueLabel(ConditionKind.WIFI, value)

internal fun mediaLabel(value: String): String = conditionValueLabel(ConditionKind.MEDIA, value)

internal fun screenLabel(value: String): String = conditionValueLabel(ConditionKind.SCREEN, value)

internal fun chargingLabel(value: String): String = conditionValueLabel(ConditionKind.CHARGING, value)

internal fun headsetLabel(value: String): String = conditionValueLabel(ConditionKind.HEADSET, value)
