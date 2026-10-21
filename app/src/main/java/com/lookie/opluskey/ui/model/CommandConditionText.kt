package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind

// 条件那一族的显示文本 —— 从 `CommandText.kt` 拆出来：那边是「命令 / 步骤怎么显示」，
// 这一份是「条件怎么显示」。两者只是都在界面上出现，没有共用的状态。

/**
 * 条件在界面上怎么说 —— 条件菜单的顶部标题、已保存命令卡的摘要共用这一份。
 *
 *  **一律写成名词短语**（「亮屏」不是「亮屏中」）：这几处地方都窄，多一个字就吃掉应用名 /
 *  动作名的位置；菜单里同一份短词也够用，不必再维护两套。
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
 * **卡片里那一行「条件」** 的说法 —— 与 [conditionTitle] 的区别只有一处：把两级**都用「 - 」连起来**。
 *
 *  「屏幕状态 - 亮屏」而不是光写「亮屏」：这一行是条件执行页里唯一一处「配完了没有」的凭据，
 *  只写取值的话，「开 / 关」这种字看不出主语，「前台应用 - 微信」也省得再点开菜单确认选了哪一级。
 *
 *  只有这一处用拼接版：菜单顶部标题与已保存命令的摘要地方窄，仍走 [conditionTitle]。
 */
internal fun conditionRowTitle(
    condition: BranchCondition,
    appName: (String) -> String,
): String {
    val kind = condition.kind ?: return I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
    val value = conditionValueLabel(kind, condition, appName)
    // 取值还没选（只选了种类就会存不下来，但草稿里有这一瞬间）：整句退回「未选择」，
    //  写成「屏幕状态 - 」比不写更让人以为配好了。
    if (value.isBlank()) return I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
    return "${conditionKindLabel(kind)} - $value"
}

/** 二级那一半：这一档实际取到的值叫什么（「锁屏」「振动」「微信」…）。 */
private fun conditionValueLabel(
    kind: ConditionKind,
    condition: BranchCondition,
    appName: (String) -> String,
): String =
    when (kind) {
        // 前台应用取的是包名，显示成应用名；空着就是「未选择」（调用方那一支会退回去）
        ConditionKind.APP -> condition.value.takeIf { it.isNotBlank() }.let { it?.let(appName).orEmpty() }

        ConditionKind.SCREEN -> screenLabel(condition.effectiveValue)

        ConditionKind.CHARGING -> chargingLabel(condition.effectiveValue)

        ConditionKind.HEADSET -> headsetLabel(condition.effectiveValue)

        ConditionKind.MEDIA -> mediaLabel(condition.effectiveValue)

        ConditionKind.WIFI -> wifiLabel(condition.effectiveValue)

        ConditionKind.RINGER -> ringerLabel(condition.value)

        ConditionKind.ORIENTATION -> orientationLabel(condition.value)
    }

/**
 * 条件**种类**在菜单里叫什么 —— 「添加分支」第一层那一列。
 *
 *  「前台应用」不能直接用 `conditionTitle`：那要一个包名，而这一层还没挑应用。
 *
 *  这支 `when` 是**穷尽**的（没有兜底支）：加一种 [ConditionKind] 时编译器会当场要求补一行文案，
 *  不会悄悄拿某一句顶替。
 */
internal fun conditionKindLabel(kind: ConditionKind): String =
    when (kind) {
        ConditionKind.APP -> I18n.get(Strings.COMMAND_COND_APP, "前台应用")

        ConditionKind.RINGER -> I18n.get(Strings.COMMAND_COND_RINGER_NAME, "响铃状态")

        ConditionKind.ORIENTATION -> I18n.get(Strings.COMMAND_COND_ORIENTATION_NAME, "屏幕方向")

        // 这两档的名字与「格子里那句话」不是一回事：格子里要带取值（WIFI 开 / 媒体 播放中），
        //  菜单里只是「种类叫什么」。
        ConditionKind.WIFI -> I18n.get(Strings.COMMAND_COND_WIFI_NAME, "WIFI")

        ConditionKind.MEDIA -> I18n.get(Strings.COMMAND_COND_MEDIA_NAME, "媒体状态")

        ConditionKind.SCREEN -> I18n.get(Strings.COMMAND_COND_SCREEN_NAME, "屏幕状态")

        ConditionKind.CHARGING -> I18n.get(Strings.COMMAND_COND_CHARGING_NAME, "充电状态")

        // 耳机这一档在格子里要带取值（「已连接耳机」），菜单里只写「耳机」。
        ConditionKind.HEADSET -> I18n.get(Strings.COMMAND_COND_HEADSET_NAME, "耳机状态")
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
