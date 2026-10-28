package com.lookie.opluskey.ui.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind

// 条件那一族的显示文本 —— 从 `CommandText.kt` 拆出来：那边是「命令 / 步骤怎么显示」，
// 这一份是「条件怎么显示」。两者只是都在界面上出现，没有共用的状态。

/**
 * 「前台应用」条件里那一组包名 → 应用名，多个用顿号连起来。
 *
 *  一条应用条件可以多选（`包1||包2`，任一在前台即通过），所以标题与取值那一行都不能只取
 *  第一个 —— 那会让用户以为只绑了一个。两处共用这一份，免得一处改了另一处没跟上。
 */
private fun appNamesText(
    condition: BranchCondition,
    appName: (String) -> String,
): String = condition.appPackages.joinToString("、") { appName(it) }

/**
 * **卡片里那一行「条件」** 的说法 —— 两级都用「 - 」连起来。
 *
 *  「屏幕状态 - 亮屏」而不是光写「亮屏」：这一行是条件执行页里唯一一处「配完了没有」的凭据，
 *  只写取值的话，「开 / 关」这种字看不出主语，「前台应用 - 微信」也省得再点开菜单确认选了哪一级。
 *
 *  界面上的条件文案只有这一处出口：要拼接版就调它，不要另写一支「只取取值」的变体。
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
        ConditionKind.APP -> {
            if (condition.value.isBlank()) "" else appNamesText(condition, appName)
        }

        ConditionKind.SCREEN -> {
            screenLabel(condition.effectiveValue)
        }

        ConditionKind.CHARGING -> {
            chargingLabel(condition.effectiveValue)
        }

        ConditionKind.HEADSET -> {
            headsetLabel(condition.effectiveValue)
        }

        ConditionKind.MEDIA -> {
            mediaLabel(condition.effectiveValue)
        }

        ConditionKind.WIFI -> {
            wifiLabel(condition.effectiveValue)
        }

        ConditionKind.RINGER -> {
            ringerLabel(condition.value)
        }

        ConditionKind.ORIENTATION -> {
            orientationLabel(condition.value)
        }

        //  时间区间的「取值」就是那一段本身（`22:00-07:00`）—— 不再过任何模板，
        //  它在两级之间已经是一句完整的话。
        ConditionKind.TIME -> {
            condition.value
        }

        ConditionKind.BLUETOOTH -> {
            switchLabel(condition.effectiveValue)
        }

        ConditionKind.CELLULAR -> {
            switchLabel(condition.effectiveValue)
        }

        ConditionKind.AIRPLANE -> {
            switchLabel(condition.effectiveValue)
        }
    }

/**
 * 条件**种类**在菜单里叫什么 —— 「添加分支」第一层那一列。
 *
 *  「前台应用」这一档不能直接用 [conditionRowTitle]：那要一个包名，而这一层还没挑应用。
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

        // 时间区间是唯一「自由取值」又没有下一级菜单的那一档：菜单里点它弹时间选择器。
        ConditionKind.TIME -> I18n.get(Strings.COMMAND_COND_TIME_NAME, "时间区间")

        ConditionKind.BLUETOOTH -> I18n.get(Strings.COMMAND_COND_BLUETOOTH_NAME, "蓝牙")

        ConditionKind.CELLULAR -> I18n.get(Strings.COMMAND_COND_CELLULAR_NAME, "移动数据")

        ConditionKind.AIRPLANE -> I18n.get(Strings.COMMAND_COND_AIRPLANE_NAME, "飞行模式")
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

        // 三个「开 / 关」型系统开关：二级菜单就两行，文案都是「开」「关」
        ConditionKind.BLUETOOTH -> switchLabel(value)

        ConditionKind.CELLULAR -> switchLabel(value)

        ConditionKind.AIRPLANE -> switchLabel(value)

        // 时间区间没有二级菜单（点它直接弹时间选择器），走不到这里
        ConditionKind.TIME -> value

        else -> value
    }
