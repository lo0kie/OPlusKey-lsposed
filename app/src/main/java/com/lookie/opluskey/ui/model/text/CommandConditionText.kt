package com.lookie.opluskey.ui.model.text

import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.core.present.ClockCatalog
import com.lookie.opluskey.core.present.CommonCommands
import com.lookie.opluskey.core.present.appTargetLabel
import com.lookie.opluskey.core.present.commandTitleOf
import com.lookie.opluskey.core.present.conditionValueLabel
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.icon.actionIcon
import com.lookie.opluskey.ui.model.icon.commandIcon
import com.lookie.opluskey.ui.model.icon.specIcon
import com.lookie.opluskey.ui.model.icon.tileIcon

// 条件那一族的显示文本 —— 从 `CommandText.kt` 拆出来：那边是「命令 / 步骤怎么显示」，
// 这一份是「条件怎么显示」。两者只是都在界面上出现，没有共用的状态。

/**
 * 应用那一族条件（「前台应用」/「前台应用以外」）里那一组包名 → 应用名，多个用 `×` 连起来（`微信 × QQ`）。
 *
 *  一条应用条件可以多选（`包1||包2`），所以标题与取值那一行都不能只取
 *  第一个 —— 那会让用户以为只绑了一个。两处共用这一份，免得一处改了另一处没跟上。
 *
 *  **分隔符是 `×` 不是顿号**（用户口径）：顿号在一行里太轻，几个应用连起来读成一句话；
 *  `×` 把每个名字隔成一个独立的格子，与组合键那边的成员写法同一个字形。
 *  注意语义差别：组合键那里 `×` 是「两颗一起按」，这两档是「组内任一」/「组内都不是」——
 *  靠条件行左半边的种类名说清，不在分隔符上再表达。
 */
private fun appNamesText(
    condition: BranchCondition,
    appName: (String) -> String,
): String = condition.appPackages.joinToString(" × ") { appName(it) }

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
    val kind = condition.kind ?: return I18n.get(Strings.COMMAND_BRANCH_ANY)
    val value = conditionValueText(kind, condition, appName)
    // 取值还没选（只选了种类就会存不下来，但草稿里有这一瞬间）：整句退回「未选择」，
    //  写成「屏幕状态 - 」比不写更让人以为配好了。
    if (value.isBlank()) return I18n.get(Strings.COMMAND_BRANCH_ANY)
    return "${conditionKindLabel(kind)} - $value"
}

/**
 * 二级那一半：这一档实际取到的值叫什么（「锁屏」「振动」「微信」…）。
 *
 *  [conditionRowTitle] 拿它拼整句；一级菜单也拿它给**选中那一行**当子标题
 *  （那里只要取值、不要种类名，种类名已经在标题上了）。
 */
internal fun conditionValueText(
    kind: ConditionKind,
    condition: BranchCondition,
    appName: (String) -> String,
): String =
    when (kind) {
        // 应用那一族取的是包名，显示成应用名；空着就是「未选择」（调用方那一支会退回去）。
        //  两档的取值写法完全一样（只有判定反向），所以取值这一行不必分两支。
        ConditionKind.APP, ConditionKind.APP_NOT -> {
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

        //  这四个都是「开 / 关」型的系统开关，取值文案共用同一对（「开」/「关」）——
        //  分四支写只是把同一句话抄了四遍，连带把这一处 `when` 的复杂度顶到上限。
        ConditionKind.BLUETOOTH, ConditionKind.CELLULAR,
        ConditionKind.AIRPLANE, ConditionKind.FLASHLIGHT,
        -> {
            switchLabel(condition.effectiveValue)
        }
    }

/**
 * 一级菜单里**选中那一行**的子标题 —— 与 [conditionValueText] 同源，只有应用那一族不同：
 * 那两档能选好几个应用，名字顿号连起来比标题还长，于是只报个数（`已选择 3 个应用`）；
 * 具体是哪几个在条件表那一行里（那里不限行）。空 = 这一行不出小字。
 */
internal fun conditionMenuSummary(
    kind: ConditionKind,
    condition: BranchCondition,
    appName: (String) -> String,
): String? =
    if (kind.isAppFamily) {
        val count = condition.appPackages.count { it.isNotBlank() }
        if (count == 0) null else I18n.format(Strings.COMMAND_BRANCH_APP_COUNT, count)
    } else {
        conditionValueText(kind, condition, appName).ifBlank { null }
    }

/**
 * 条件**种类**在菜单里叫什么 —— 「添加分支」第一层那一列。
 *
 *  「前台应用」这一档不能直接用 [conditionRowTitle]：那要一个包名，而这一层还没挑应用。
 *
 *  这支 `when` 是**穷尽**的（没有兜底支）：加一种 [ConditionKind] 时编译器会当场要求补一行文案，
 *  加一种 [ConditionKind] 时编译器会当场要求补一行文案，不会悄悄拿某一句顶替。
 *
 */
internal fun conditionKindLabel(kind: ConditionKind): String =
    when (kind) {
        ConditionKind.APP -> I18n.get(Strings.COMMAND_COND_APP)

        //  与上一档同一族的反面：读作「前台应用以外 - 微信」，即「前台不是微信」。
        ConditionKind.APP_NOT -> I18n.get(Strings.COMMAND_COND_APP_NOT)

        ConditionKind.RINGER -> I18n.get(Strings.COMMAND_COND_RINGER_NAME)

        ConditionKind.ORIENTATION -> I18n.get(Strings.COMMAND_COND_ORIENTATION_NAME)

        // 这两档的名字与「格子里那句话」不是一回事：格子里要带取值（WIFI 开 / 媒体 播放中），
        //  菜单里只是「种类叫什么」。
        ConditionKind.WIFI -> I18n.get(Strings.COMMAND_COND_WIFI_NAME)

        ConditionKind.MEDIA -> I18n.get(Strings.COMMAND_COND_MEDIA_NAME)

        ConditionKind.SCREEN -> I18n.get(Strings.COMMAND_COND_SCREEN_NAME)

        ConditionKind.CHARGING -> I18n.get(Strings.COMMAND_COND_CHARGING_NAME)

        // 耳机这一档在格子里要带取值（「已连接耳机」），菜单里只写「耳机」。
        ConditionKind.HEADSET -> I18n.get(Strings.COMMAND_COND_HEADSET_NAME)

        // 时间区间是唯一「自由取值」又没有下一级菜单的那一档：菜单里点它弹时间选择器。
        ConditionKind.TIME -> I18n.get(Strings.COMMAND_COND_TIME_NAME)

        ConditionKind.BLUETOOTH -> I18n.get(Strings.COMMAND_COND_BLUETOOTH_NAME)

        ConditionKind.CELLULAR -> I18n.get(Strings.COMMAND_COND_CELLULAR_NAME)

        ConditionKind.AIRPLANE -> I18n.get(Strings.COMMAND_COND_AIRPLANE_NAME)

        ConditionKind.FLASHLIGHT -> I18n.get(Strings.COMMAND_COND_FLASHLIGHT_NAME)
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

        ConditionKind.FLASHLIGHT -> switchLabel(value)

        // 时间区间没有二级菜单（点它直接弹时间选择器），走不到这里
        ConditionKind.TIME -> value

        else -> value
    }

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

/**
 * 「这个动作绑的是什么」—— **网格格子的小字**与**抽屉候选行的子标题**共用这一份。
 *
 *  两处摆的是同一张清单（[ActionCatalog.GROUPS]），同一个「系统开关」在两处写成两句话的话，
 *  用户看到的就是两套说法。所以判据只留在这里，界面两侧都只负责把它填进各自那个槽
 *  （网格走 `ActionTile.subtitle`、抽屉走 `CardRow.Item.summary`）。
 *
 *  只有「格子 / 那一行 + 当前绑定」一起看才知道该写什么：`current` 是同一个动作，不判格子的
 *  那类写法会让整片网格都挂上同一句（见下面「系统开关」那一段）。
 *
 *  @param spec 这一格 / 这一行
 *  @param current 当前绑的动作（非空：调用方先判「有没有绑定」）
 *  @param boundMode 这一格 / 这一行绑的是哪一档（入口格专用；非入口格传 `null`）
 *  @param appLabel 「打开应用 / 选择应用」那几行的应用名，由调用方按当前绑定解析
 *    （要走 `PackageManager`，见 `boundAppName`）
 */
internal fun tileSubtitle(
    spec: ActionCatalog.Spec,
    current: Action,
    boundMode: ActionCatalog.Spec?,
    appLabel: String?,
): String? =
    when {
        // 「应用控制」那一格 / 抽屉里的「应用控制」那一行：小字**只写应用名**（`微信`）。
        //
        //  **不加「打开应用:」前缀、也不带括号注记**（用户口径「只要应用名，不用打开应用
        // 和细节之类的」）：这一行只有一格宽，前缀一占、名字就只剩一半；而「打开 / 结束」这件事
        //  行首那颗字形已经说了（`PageIcons.AppOpen` / `AppStop`），再写一遍就是把同一句说两次。
        //  所以这一支**不走** `CommandTitle` 的任何出口，直接取应用名 —— 那一族的三段材料里
        //  「名字」是动词（`打开应用`）、「状态」是目标，哪一段都不是这一格要的。
        spec.token == ActionCatalog.APP_CONTROL_TILE || spec.token == ActionCatalog.APP_TILE -> {
            appLabel?.takeIf { it.isNotBlank() }
        }

        //  「计时器」那一格：小字只写**时长**（`5 分钟`）—— 格子名已经在标题上。
        //  它没有下一层菜单（格子本身就是那个动作），所以 `modeHitOf` 查不到它，
        //  这里按格子的标记 token 认领；不认识这一格就是「没绑」。
        spec.token == ClockCatalog.TILE -> {
            ClockCatalog.durationOf(current)
        }

        // 其余每一格：**先确认当前动作真的落在这一格**，再问 `forTile`（那一格小字那一支）。
        //
        //  判据是 `modeHitOf(current)?.tile`，不是「这一格叫什么」—— 原来「系统开关」那一支只判
        //  `spec.token`，于是别的格绑了动作（`前台转小窗 - 展开`）它也跟着念一句，系统开关格
        //  下面凭空多出一行小字（真机撞到过）。
        //
        //  一句话由 `CommandTitle` 拼，这里只选出口：开关格要 `WIFI - 打开`、导航格要 `主屏幕`、
        //  变量格要 `「计数」+= 1` —— 都是「不带父动作」那一份，不必再按格子分支。
        belongsToThisTile(spec, current) -> {
            commandTitleOf(current).forTile(current, { "" }, current.token)
        }

        // 这一格此刻没绑到它自己那一族（或者绑的是一个不属于任何入口格的动作）：
        // 退回「这一格绑的是哪一档」的档位名。
        else -> {
            boundMode?.label
        }
    }

/**
 * 当前动作是不是落在 [spec] 这一格里。
 *
 *  不在任何入口格里（顶层格 `截屏`、自定义命令）时返回 `false` —— 那些动作没有「格」可言，
 *  不该让某一格替它显出一行小字。
 */
private fun belongsToThisTile(
    spec: ActionCatalog.Spec,
    current: Action,
): Boolean = ActionCatalog.modeHitOf(current)?.tile?.token == spec.token

/**
 * 「这一行长什么样」—— 网格那一格与抽屉那一行**共用这一份**。
 *
 * ## 为什么要有它
 *
 *  两处摆的是同一张清单（`ActionCatalog.GROUPS`），但渲染是两套代码：网格走 `BuiltInTile`、
 *  抽屉走 `stepSheetRow`。清单同源不等于展示同源 —— 每加一条「怎么显示」的规则（行首随绑定
 *  那一档换、小字写什么、行尾要不要箭头），都得在两边各写一遍，漏一遍就成「同一个系统开关
 *  在两处长不一样」。这个坑踩过三次：行首字形（抽屉早期直接 `tileIcon(spec)`）、行尾箭头
 *  （早先只在抽屉有）、小字（抽屉压根没有）。
 *
 *  现在规则只在这里；两侧各自塞自己的槽（网格 `ActionTile`、抽屉 `CardRow.Item`），
 *  以后加规则只改这一处，从结构上不会再走形。清单本身的差异（抽屉把 `appctl:` 换成
 *  `app:`「选择应用」）仍由 `ActionCatalog.STEP_GROUPS` 负责，那是**内容**不是**展示**。
 *
 * @property trailing 行尾箭头。**网格的格子没有行尾**，只有抽屉那个槽用它。
 */
internal data class ActionRowView(
    val title: String,
    val icon: ImageVector,
    val subtitle: String?,
    val selected: Boolean,
    val trailing: ImageVector?,
)

/**
 * @param spec 这一格 / 这一行
 * @param current 当前绑的动作（`null` = 没绑定过 / 不知道，整行按「未选中的清单条目」画）
 * @param appLabel 「应用控制 / 选择应用」那几行的应用名（要走 `PackageManager`，由调用方查好递进来）
 */
internal fun actionRowView(
    spec: ActionCatalog.Spec,
    current: Action?,
    appLabel: String? = null,
): ActionRowView {
    //  这一格 / 这一行绑的是哪一档 —— 判据只在 `ActionCatalog` 一处
    val boundMode = current?.let { ActionCatalog.boundModeOf(spec, it) }
    val selected = current?.let { ActionCatalog.isSelected(spec, it) } ?: false
    return ActionRowView(
        title = spec.label,
        //  行首那颗：绑在「应用 / 选择应用」这一族时按绑定细分 → 命中那一档的档位字形 →
        //  这一格自己的大类字形。顺序即优先级，两侧不再各拼一遍。
        icon =
            (if (selected) boundAppIcon(spec, current) else null)
                ?: boundMode?.let { tileIcon(it) }
                ?: tileIcon(spec),
        subtitle = current?.let { tileSubtitle(spec, it, boundMode, appLabel) },
        selected = selected,
        trailing = rowTrailingIcon(spec),
    )
}

/**
 * 「应用控制」（网格那一格）与「选择应用」（抽屉那一行）绑成什么样，就画什么样 ——
 * 分**两族**：打开族一颗、结束一颗。
 *
 *  「打开」这一族含整个应用、应用里的某个界面、以及应用注册的快捷方式（`appshortcut:`）——
 *  三者都是「把某个东西叫起来」，不按它再换字形（用户口径：快捷方式不再单独一颗 `pin`，
 *  那个独立的快捷方式页撤掉之后它也就没有对应的菜单行了）。
 *
 *  必须判**是不是这一族**：`current` 对整片网格是同一个动作，不判格子就会让导航 / 响铃 /
 *  媒体那几格全画成应用那一族的字形。
 *  也必须判 `selected`（调用点已判）：抽屉里的「选择应用」是**一步**，当前绑的是「结束应用」
 *  时它不是这一步 —— 那时画一颗「结束」等于说反了。
 */
private fun boundAppIcon(
    spec: ActionCatalog.Spec,
    current: Action?,
): ImageVector? {
    if (current == null) return null
    if (spec.token != ActionCatalog.APP_CONTROL_TILE && spec.token != ActionCatalog.APP_TILE) return null
    return when (current) {
        //  结束那一档：「停掉」的方块 —— 与菜单「结束应用」那一行同一颗
        is Action.Process -> {
            PageIcons.AppStop
        }

        //  打开那一族（整个应用 / 某个界面 / 快捷方式）：与菜单「打开应用」那一行同一颗
        is Action.App, is Action.AppWindow, is Action.AppShortcut -> {
            PageIcons.AppOpen
        }

        else -> {
            null
        }
    }
}

/**
 * 行尾那颗箭头（只有抽屉用）：点了去**另一张页面**的给右箭头，点开还有**一层菜单**的给选择器，
 * 点一下就落成一步的不给。
 *
 *  判据只有 `ActionCatalog.clickKind` —— 那是「这一行点下去会发生什么」的唯一答案。
 *  以前这里自己判 `APP_TILE` 与 `popsSelector`，与网格 `BindingTile` 那份判出了两个结果：
 *  「应用控制」在网格里是选择器（它弹原地菜单，对的），在抽屉里是右箭头（说它跳页，错的）。
 *  同一个格子两种指示，用户只能猜哪个是真的。
 */
private fun rowTrailingIcon(spec: ActionCatalog.Spec): ImageVector? =
    when (ActionCatalog.clickKind(spec)) {
        ActionCatalog.ClickKind.PAGE_APP,
        ActionCatalog.ClickKind.PAGE_VARS,
        -> {
            PageIcons.EnterArrow
        }

        ActionCatalog.ClickKind.MENU -> {
            PageIcons.Selector
        }

        else -> {
            null
        }
    }

// 命令文本的显示层：把一条命令 / 一个步骤压成界面上要显示的那几行字。
// 与 Compose、与页面状态无关 —— 输入是 `Command` / 字符串，输出是字符串 / 图标。

/**
 * 一条命令在卡片里的摘要行：一步一条，同时带着那一步的原始取值。
 *
 * 带 `token` 是因为行首要按它决定补不补那枚命令引用小图标 —— 光看 `title` 分不出
 * 「引用另一条命令」与「恰好同名的命令文本」。
 */
internal data class StepLine(
    val token: String,
    val title: String,
)

/**
 * 「多条命令」的摘要行：一步一条。只管多条 —— 单条那一路显示原始取值。
 *
 * @param appName 透传给 [stepTitle]：步骤里可能有 `app:` / `window:` / `appshortcut:`，
 *   那一步要显示名字。入参是**目标写法**（`999@包名/类名`），不是包名 —— 分身与具体界面都要认，
 *   名字的优先级收在 `appNameOf` 一处。
 */
internal fun serialSummarySteps(
    steps: List<String>,
    appName: (String) -> String,
): List<StepLine> = steps.map { StepLine(it, stepTitle(it, appName)) }

/**
 * 这一步是不是引用另一条命令（`cmd:<名字>`）。
 * 界面据此在行首补一枚小图标，`stepIcon` 也按它选字形。
 */
internal fun isCommandRef(token: String): Boolean = token.startsWith(Command.PREFIX)

/**
 * `cmd:` 后面那一段在界面上叫什么 —— 被引用那条命令的**名字**。
 *
 *  用户命令库里的（含手改出来的）原样显示；模块内置的那几条（`CommonCommands`：
 *  `cmd:common.wechat_scan`）存的是稳定 id，要按当前语言过一遍字典，否则界面上会冒出
 *  `common.wechat_scan` 这种没人读得懂的东西。
 *
 *  与 `core/present/CommandTitle` 的 `Action.Custom` 那一支同一个口径：那一边管提示 / 摘要句，
 *  这一边管步骤行与格子标题，两处都得认。
 */
private fun commandRefName(token: String): String = token.removePrefix(Command.PREFIX).let { raw -> CommonCommands.labelOf(raw) ?: raw }

/**
 * 一个步骤在界面上叫什么。
 *
 *  **除「打开应用」外一律拍平**：菜单里的分组标题（`系统操作` / `快捷功能` /
 *  `自定义命令`）与入口格的格子名（`系统导航` / `系统按键` / `媒体控制` …）都不再进标题 ——
 *  步骤行与卡片摘要都窄，多一段前缀只剩啰嗦，「这一步是什么」靠行首图标（[stepIcon]）加名字
 *  已经说得清。`系统导航:主屏幕` → `主屏幕`、`系统按键:音量加` → `音量加`、`快捷功能:手电筒` → `手电筒`。
 *  唯一的例外是「打开应用」那两档（`app:` 与 `window:`），它们显示成「打开应用:应用名」/
 *  「以小窗口打开:应用名」—— 那一段不是分类，是动作本身：少了它就只剩一个应用名，
 *  看不出这一步是「打开某个应用」，也看不出是小窗那一档。口径与执行时提示
 *  （`host/action/ActionNotice`）**同一份实现**（`core/model/CommandTitle`），不再各写一遍。
 *  - 内置动作取清单里的名字（[ActionCatalog.specOf]）；
 *  - 入口格取那一档的档位名（[ActionCatalog.modeName]）；
 *  - `cmd:` 只显示命令名；
 *  - 都认不出来就原样显示取值（比如手改过的文件）。
 */
internal fun stepTitle(
    token: String,
    appName: (String) -> String,
): String {
    if (isCommandRef(token)) return commandRefName(token)
    //  走 `ActionCatalog` 那份按 token 记忆的解析结果，不在这里现 `Action.parse` ——
    //  步骤行的每一次重组、每一条已保存命令卡都会走到这一句（见 `ActionCatalog.actionOf`）。
    val action = ActionCatalog.actionOf(token)
    return commandTitleOf(action).forSentence(
        action = action,
        appName = appTargetName(appName),
        // 认不出来时用**配置里那串原始写法**（宿主那边用规范化后的 token）
        fallback = token,
    )
}

/**
 * 同 [stepTitle]，但**目标那一段由调用方整段给**（`完整目标`，例如已经拼好括号的
 * `微信(小窗 扫一扫)`）。
 *
 *  为什么要这一支：[stepTitle] 的 `appName` 回调收的是「目标 → 名字」，它自己还会按动作类型
 *  补「(小窗)」注记。而系统场景那一格用 [appTargetLabelOf][com.lookie.opluskey.ui.appTargetLabelOf]
 *  已经把括号拼全了 —— 再从 [appTargetName] 走一遍就会套两层
 *  （真机口径「应用控制: 打开应用 - 应用(小窗 组件)(小窗)，多了一个」）。
 *  所以「谁拼括号」只能有一处：要么这里，要么 `appName` 回调。
 */
internal fun stepTitleWithTarget(
    token: String,
    target: (Action) -> String,
): String {
    if (isCommandRef(token)) return commandRefName(token)
    val action = ActionCatalog.actionOf(token)
    return commandTitleOf(action).forSentence(
        action = action,
        appName = target,
        fallback = token,
    )
}

/**
 * 界面侧那份「目标 → 名字」的回调，包成 [commandTitleOf] 要的形状。
 *
 *  名字从哪儿来是**界面侧**的事（宿主那边现查 PM）：这里先查这次清单、再查配置里记的名字。
 *  只有这一份 —— 各写一遍时，「哪几种动作要名字」这种判据就会各长一套（漏一档的表现是
 *  提示里多出一个空括号）。
 *
 *  **「小窗」在这一层标**（`微信(小窗)`）：动词那一侧只有「打开应用」一个词，小窗是目标的注记。
 *  这条回调只给「名字」—— 已经有完整目标的调用点走 [stepTitleWithTarget]。
 */
private fun appTargetName(appName: (String) -> String): (Action) -> String =
    { a ->
        when (a) {
            is Action.App -> appTargetLabel(appName(a.target), window = false, detail = null, stockAnim = a.stockAnim)
            is Action.AppWindow -> appTargetLabel(appName(a.target), window = true, detail = null)
            is Action.AppShortcut -> appTargetLabel(appName(a.appTarget.orEmpty()), window = a.window, detail = null)
            is Action.Process -> appName(a.pkg)
            else -> ""
        }
    }

/**
 * 「额外动作」那一行的值怎么写 —— **完整文案**，与步骤行、执行提示逐字相同：
 *
 *  · 有父动作：`父动作: 子动作 - 状态`（`系统开关: WIFI - 打开`、`快捷功能: 手电筒 - 关闭`）；
 *  · 没有父动作：`动作 - 状态`（`录屏 - 开始`）；
 *  · 顶层格只有名字：`截屏`；
 *  · 没配动作：`无操作` —— 这一行要有值，不然看不出「确实没配」。
 *
 *  这一行**不省父动作**（用户口径「额外动作的子标题要取完整文案」）：上面那行标题是
 *  「额外动作」四个字，不是某个格子的名字 —— 省掉父名之后 `WIFI - 打开` 读不出是哪一格的东西。
 *  与网格小字（`forTile`，那里格子名就在格子上、省得掉）正好相反。
 *
 *  这里**不再特判「无操作」**：`forSentence` 现在对任何动作都给完整一句（它不判「该不该显示」），
 *  所以这一行直接拿结果就行。以前那两行特判 + `ActionCatalog.label` 是工厂里那个空串逼出来的。
 */
internal fun releaseActionTitle(
    token: String,
    appName: (String) -> String,
): String {
    val action = ActionCatalog.actionOf(token)
    return commandTitleOf(action).forSentence(action, appTargetName(appName), token)
}

/**
 * 一个步骤在界面上的行首图标 —— 与 [stepTitle] 一一对应。
 *
 * `cmd:` 取**被引用那条命令**自己的类型图标（单条 → 终端，多条 → 按步数换的方框）——
 * 光给一个通用「命令」字形的话，步骤行里看不出它引用的是哪一种。
 * 命令库里查不到（那条被删了 / 改了名）时退回通用字形：配置里留着一条指向空气的 `cmd:` 是可能的。
 * 其余内置动作取动作图标，认不出来时 [actionIcon] 会落到兜底字形上。
 *
 * @param commands 当前命令库 —— 与 [stepTitle] 的 `appLabel` 同一条规矩：查名字要用谁由调用方传进来。
 */
internal fun stepIcon(
    token: String,
    commands: List<Command>,
): ImageVector {
    // 走 `ActionCatalog` 那份按 token 记忆的规范化结果，不在这里现 `Action.parse` ——
    //  步骤行的每一次重组都会走到这一句（见 `ActionCatalog.canonicalToken`）。
    if (!isCommandRef(token)) return actionIcon(ActionCatalog.canonicalToken(token))
    val name = token.removePrefix(Command.PREFIX)
    //  **内置那几条常用命令不在用户命令库里**（`cmd:common.wechat_scan` 这类）：按库里查必然落空，
    //  落到通用的「命令」字形上就等于把它们的信息抹掉了。它们自带 root 位与那一步正文 ——
    //  按与库里那条**同一条判据**（`commandIcon`）画，root 角标才不会丢。
    CommonCommands.entryOf(token)?.let { return commandIcon(CommandKind.SINGLE, 1, it.root, it.step) }
    val ref = commands.firstOrNull { it.name == name } ?: return PageIcons.CommandSingle
    //  单条命令按它**自己那种写法**取底图、root 那档加井号角标，与命令列表里那张卡同一条判据
    return commandIcon(ref.kind, ref.steps.size, ref.root, ref.single)
}

/**
 * 与 [stepIcon] 一一对应的行首图标 —— 有父格时用**那一行自己的**字形。
 *
 *  「循环」写的是 `keyevent:164`，与响铃那一格**同一个 token**：按 token 取图标只能拿到格子那颗
 *  （响铃状态），档位自己的字形（重复箭头）只登记在 `Spec.glyph` 上。所以这里先问
 * [ActionCatalog.modeHitOf]，问到就交给 `specIcon`（它认 `glyph`），问不到再按 token 走老路。
 */
internal fun actionCellIcon(
    action: Action,
    commands: List<Command>,
): ImageVector {
    //  计时器**没有入口格**（那一格就是它自己，不进 `MODE_MENUS`），`modeHitOf` 查不到它；
    //  而 token 是 `timer:300`，按 token 走兜底只会拿到通用那颗 —— 它的字形登记在格子那份 [Spec] 上。
    //  这条也决定「执行时提示走流体云」时胶囊左边那颗图（见 `ActionCardArt`）。
    if (action is Action.Timer) return specIcon(ClockCatalog.TILE_SPEC)
    val hit = ActionCatalog.modeHitOf(action)
    return if (hit == null) stepIcon(action.token, commands) else specIcon(hit.mode)
}

/**
 * 时长（毫秒）在界面上怎么写 —— 全局唯一一处，滑杆行尾与网格小字都走它。
 *
 * 1000ms 及以上写成秒（`1500 → 1.5 秒`）：这两处宽度紧、四位数读不出量级；不足 1000ms 仍写
 * 毫秒 —— 步长是 [STEP_MS]（50ms），一秒内换成秒反而更难读。小数只在需要时留、不四舍五入
 *（`1050 → 1.05 秒`）。
 */
internal fun durationText(ms: Int): String {
    if (ms < 1000) return I18n.format(Strings.COMMON_TIME_MS, ms)
    val seconds = ms / 1000
    val rest = ms % 1000
    val secStr =
        if (rest == 0) {
            seconds.toString()
        } else {
            // 余数按三位补零、再去掉末尾的零：1500 → "1.5"、1050 → "1.05"、1005 → "1.005"
            "$seconds.${rest.toString().padStart(3, '0').trimEnd('0')}"
        }
    return I18n.format(Strings.COMMON_TIME_SEC, secStr)
}
