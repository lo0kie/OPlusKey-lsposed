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
): String =
    condition?.let { conditionTitleOf(it, appName) }
        ?: I18n.get(Strings.COMMAND_BRANCH_ELSE, "其他情况")

/**
 * 有值那一支 —— 按条件种类分派（「其他情况」那一档在上面）。
 *
 *  只留下「不能只看格式串」的那几档：应用名要拼、充电 / 耳机那两档取值本身就是一句话、
 *  时间要兜空值。其余同构的几档全在 [VALUE_TITLES] 那张表里 —— 一档一个分支写在这里，
 *  复杂度会跟着 `ConditionKind` 涨（detekt 是 15）。
 */
private fun conditionTitleOf(
    condition: BranchCondition,
    appName: (String) -> String,
): String =
    when (condition.kind) {
        ConditionKind.APP -> {
            if (condition.value.isBlank()) {
                I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
            } else {
                I18n.format(Strings.COMMAND_BRANCH_WHEN_APP, appNamesText(condition, appName))
            }
        }

        ConditionKind.CHARGING -> {
            // 取值本身就是一句完整的话（充电中 / 没在充电），格子里直接用它
            chargingLabel(condition.effectiveValue)
        }

        ConditionKind.HEADSET -> {
            // 与充电那一档同一个写法：取值本身就是一句完整的话（已连接耳机 / 未连接耳机）
            headsetLabel(condition.effectiveValue)
        }

        //  时间区间：格子里写「时间 22:00-07:00」。不写成「22:00-07:00 之间」——
        //  这一族一律是名词短语（见文件头），而且短一截，格子那半行更不容易被截断。
        ConditionKind.TIME -> {
            condition.value
                .takeIf { it.isNotBlank() }
                ?.let { I18n.format(Strings.COMMAND_COND_TIME, it) }
                ?: I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
        }

        //  `null`（认不出的条件，配置来自更新的版本）落这里，表里查不到 ⇒ 同样说「未选择」
        else -> {
            VALUE_TITLES[condition.kind]?.let {
                I18n.format(it.format, it.value(condition))
            } ?: I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
        }
    }

/**
 * 「一个格式串 + 取值怎么念」那一档条件 —— 屏幕状态 / 媒体 / Wi-Fi / 响铃 / 方向 / 三个系统开关。
 *
 * @property format 文案模板（`Strings` 里那条 `…%s`）
 * @property value 从条件里取哪一个值再念出来（`effectiveValue` 会在空值时补默认档）
 */
private class ValueTitle(
    val format: String,
    val value: (BranchCondition) -> String,
)

/** 同构的那几档全在这张表里 —— 每加一种条件只是多一行数据，不会顶到谁的分析复杂度。 */
private val VALUE_TITLES: Map<ConditionKind?, ValueTitle> =
    mapOf(
        ConditionKind.SCREEN to ValueTitle(Strings.COMMAND_COND_SCREEN) { screenLabel(it.effectiveValue) },
        ConditionKind.MEDIA to ValueTitle(Strings.COMMAND_COND_MEDIA) { mediaLabel(it.effectiveValue) },
        ConditionKind.WIFI to ValueTitle(Strings.COMMAND_COND_WIFI) { wifiLabel(it.effectiveValue) },
        ConditionKind.RINGER to ValueTitle(Strings.COMMAND_COND_RINGER) { ringerLabel(it.value) },
        ConditionKind.ORIENTATION to ValueTitle(Strings.COMMAND_COND_ORIENTATION) { orientationLabel(it.value) },
        ConditionKind.BLUETOOTH to ValueTitle(Strings.COMMAND_COND_BLUETOOTH) { switchLabel(it.effectiveValue) },
        ConditionKind.CELLULAR to ValueTitle(Strings.COMMAND_COND_CELLULAR) { switchLabel(it.effectiveValue) },
        ConditionKind.AIRPLANE to ValueTitle(Strings.COMMAND_COND_AIRPLANE) { switchLabel(it.effectiveValue) },
    )

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
