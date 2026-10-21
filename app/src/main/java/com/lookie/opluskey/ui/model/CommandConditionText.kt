package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind

// 条件那一族的显示文本 —— 从 `CommandText.kt` 拆出来：那边是「命令 / 步骤怎么显示」，
// 这一份是「条件怎么显示」。两者只是都在界面上出现，没有共用的状态。

/**
 * 条件在界面上怎么说 —— 分支行、条件类型菜单、已保存命令卡的摘要共用这一份。
 *
 *  **一律写成名词短语**（「亮屏」不是「亮屏中」）：这一句在两格里只占半行，多一个字就吃掉
 *  应用名 / 动作名的位置；菜单里同一份短词也够用，不必再维护两套。
 *
 *  `null` 是兜底那一支；类型认不出（配置来自更新的版本）时给「未选择」，不假装它是某一种。
 */
internal fun conditionTitle(
    condition: BranchCondition?,
    appName: (String) -> String,
): String {
    if (condition == null) return I18n.get(Strings.COMMAND_BRANCH_ELSE, "其他情况")
    return when (condition.kind) {
        ConditionKind.APP -> {
            if (condition.value.isBlank()) {
                I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
            } else {
                I18n.format(Strings.COMMAND_BRANCH_WHEN_APP, appName(condition.value))
            }
        }

        ConditionKind.SCREEN -> {
            I18n.format(Strings.COMMAND_COND_SCREEN, screenLabel(condition.effectiveValue))
        }

        ConditionKind.CHARGING -> {
            // 取值本身就是一句完整的话（充电中 / 没在充电），格子里直接用它
            chargingLabel(condition.effectiveValue)
        }

        ConditionKind.HEADSET -> {
            // 与充电那一档同一个写法：取值本身就是一句完整的话（已连接耳机 / 未连接耳机）
            headsetLabel(condition.effectiveValue)
        }

        ConditionKind.MEDIA -> {
            I18n.format(Strings.COMMAND_COND_MEDIA, mediaLabel(condition.effectiveValue))
        }

        ConditionKind.WIFI -> {
            I18n.format(Strings.COMMAND_COND_WIFI, wifiLabel(condition.effectiveValue))
        }

        ConditionKind.MINI -> {
            I18n.get(Strings.COMMAND_COND_MINI, "小窗")
        }

        ConditionKind.RINGER -> {
            I18n.format(Strings.COMMAND_COND_RINGER, ringerLabel(condition.value))
        }

        ConditionKind.ORIENTATION -> {
            I18n.format(Strings.COMMAND_COND_ORIENTATION, orientationLabel(condition.value))
        }

        null -> {
            // 认不出的条件（配置来自更新的版本）与「还没选」在这里同一句话：都说「未选择」
            I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
        }
    }
}

/**
 * 条件**种类**在菜单里叫什么 —— 「添加分支」第一层那一列。
 *
 *  「前台应用」不能直接用 `conditionTitle`：那要一个包名，而这一层还没挑应用。
 */
internal fun conditionKindLabel(kind: ConditionKind): String =
    when (kind) {
        ConditionKind.APP -> I18n.get(Strings.COMMAND_COND_APP, "前台应用")

        ConditionKind.RINGER -> I18n.get(Strings.COMMAND_COND_RINGER_NAME, "响铃档位")

        ConditionKind.ORIENTATION -> I18n.get(Strings.COMMAND_COND_ORIENTATION_NAME, "屏幕方向")

        // 这两档的名字与「格子里那句话」不是一回事：格子里要带取值（WIFI 开 / 媒体 播放中），
        //  菜单里只是「种类叫什么」。
        ConditionKind.WIFI -> I18n.get(Strings.COMMAND_COND_WIFI_NAME, "WIFI")

        ConditionKind.MEDIA -> I18n.get(Strings.COMMAND_COND_MEDIA_NAME, "媒体播放")

        ConditionKind.SCREEN -> I18n.get(Strings.COMMAND_COND_SCREEN_NAME, "屏幕状态")

        ConditionKind.CHARGING -> I18n.get(Strings.COMMAND_COND_CHARGING_NAME, "充电状态")

        // 耳机这一档在格子里要带取值（「已连接耳机」），菜单里只写「耳机」——
        //  直接落到下面的兜底会把整句话当种类名。
        ConditionKind.HEADSET -> I18n.get(Strings.COMMAND_COND_HEADSET_NAME, "耳机")

        else -> conditionTitle(BranchCondition.of(kind), { it })
    }

/** 有取值的条件，那一档取值在菜单里叫什么。 */
internal fun conditionChoiceLabel(
    kind: ConditionKind,
    value: String,
): String =
    when (kind) {
        ConditionKind.RINGER -> ringerLabel(value)
        ConditionKind.ORIENTATION -> orientationLabel(value)
        ConditionKind.WIFI -> wifiLabel(value)
        ConditionKind.MEDIA -> mediaLabel(value)
        ConditionKind.SCREEN -> screenLabel(value)
        ConditionKind.CHARGING -> chargingLabel(value)
        ConditionKind.HEADSET -> headsetLabel(value)
        else -> value
    }

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

/** 媒体播放两档在界面上叫什么。 */
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
