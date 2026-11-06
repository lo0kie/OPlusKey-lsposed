package com.lookie.opluskey.core.model

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/**
 * **「一个动作怎么念」** —— 全项目唯一实现，形态只有一个：**父动作名: 子动作名[- 状态]**。
 *
 *   · `系统开关: WIFI - 打开`（父格 → 开关 → 三态）
 *   · `系统导航: 主屏幕` / `响铃状态: 静音` / `媒体控制: 播放/暂停`
 *   · `小布 AI: 灵感语音 - 开始`（中间夹一层「一个东西 + 一个状态」的，见 [ActionCatalog.ModeHit.path]）
 *   · `打开应用: 微信 - 扫一扫` / `结束应用: 微信`（动词就是父名，目标跟冒号）
 *   · `屏幕亮度: 增加 15%` / `音量: 增加 10%`（数值编进子动作里）
 *   · `录屏 - 开始`：**没有父动作**的那一档 —— 格子本身就是那个动作，三档只是它的状态，
 *     所以只剩一条横杠（见 [nameState]，判据是 [ActionCatalog.stateChoices]）
 *   · 顶层格不套冒号（`截屏` / 自定义命令名）：没有父格，也没有状态可写
 *
 *  说这句话的地方有四处，它们必须是**同一句话**（否则用户会以为执行的是另一条）：
 *  执行时提示（`host/action/ActionNotice`，宿主进程）、自定义命令的步骤行与命令卡摘要
 *  （`ui/model/CommandText.stepTitle`，界面进程）、系统场景那一格（`CommandBranches`）、
 *  以及导出 / 清空配置时的确认文案。
 *
 *  原先这里有两个函数：`actionTitle`（步骤行那些，入口格只写档位名、开关写成「打开:Wi-Fi」）
 *  与 `actionFinalTitle`（只给执行提示与场景格，入口格带父名）。同一个动作因此一句话两个样子。
 *  现在只剩这一份 —— 加一档动作只需要在这里加一支。
 *
 *  ## 为什么参数里带一个 [appName] 回调
 *
 *  「打开应用」那几档要的是应用名，而**只有调用方知道从哪儿查**：宿主进程现查 `PackageManager`
 *  （见 `host/action/ActionNotice` 里的 `appLabel`），界面进程先查这次清单、再查配置里记的名字
 *  （见 `ui/AppNaming.appNameOf`）。所以这一层只管「句子怎么拼」，名字从哪来交给调用方。
 *
 *  回调的入参是**整个 [Action]** 而不是目标字符串：几档应用动作的取名优先级本来就不同
 *  （快捷方式可以先查「落盘时记下的名字」再退 PM，`app:` 则直接问 PM），把目标拍平成字符串
 *  就没法表达这个差别 —— 那会悄悄改掉提示里的名字来源。
 *
 *  ## 为什么 `app:` / `window:` / `appshortcut:` 不能被 `else` 接住
 *
 *  那几档走的是**带 `%s` 的格式键**；清单里那条说明文案的值里没有格式符，拿去 `format` 等于
 *  把应用名丢掉。所以它们必须在 `when` 里显式分出来。
 *
 * @param appName 只在上面那几档被调用；其余分支不读它
 * @param fallback 认不出这一档时原样显示什么 —— **由调用方给**：界面用配置里那串原始 token
 *  （手改过的文件要看得见原始写法），宿主用规范化之后的 token。两者只在「认不出来」时才不同。
 */
fun actionTitle(
    action: Action,
    appName: (Action) -> String,
    fallback: String,
): String =
    when (action) {
        // 「无操作」不是「执行了某条命令」：提示里给空串，调用点据此不弹
        is Action.None -> {
            ""
        }

        // 自定义命令取命令名 —— 界面（「已保存的命令」卡片）也是这么叫的
        is Action.Custom -> {
            action.name
        }

        //  「应用控制」那一族：**父格 + 动词 + 目标**（`应用控制: 打开应用 - 微信(扫一扫)`）。
        //  动词只有两个（`打开应用` / `结束应用`）—— 「以小窗」不进动词，它是**目标的一层注记**
        //  （`微信(小窗)`），写在动词里就是同一件事说两遍（用户口径「怎么最终文案还是以小窗打开，
        //  不是精简了吗」）。目标由调用方拼好（应用名，组件 / 快捷方式 / 小窗见 [appTargetLabel]）。
        is Action.App -> {
            appControlTitle(I18n.get(Strings.TOOL_OPEN_APP, "打开应用"), appName(action))
        }

        //  小窗那一档：动词与「打开应用」相同 —— 「小窗」是**目标的一层注记**（`微信(小窗)`），
        //  由调用方的目标回调拼好（见 [appTargetLabel]）。写在动词里就是同一件事说两遍
        //  （用户口径「怎么最终文案还是以小窗打开，不是精简了吗」）。
        is Action.AppWindow -> {
            appControlTitle(I18n.get(Strings.TOOL_OPEN_APP, "打开应用"), appName(action))
        }

        //  快捷方式与「打开应用」同一个动词：差别只在目标那一段（`微信(扫一扫)`）；小窗快捷方式同理
        is Action.AppShortcut -> {
            appControlTitle(I18n.get(Strings.TOOL_OPEN_APP, "打开应用"), appName(action))
        }

        // 结束应用（`proc:stop:包名`）：换动词，父格与目标那两段不变
        is Action.Process -> {
            appControlTitle(I18n.get(Strings.TOOL_STOP_APP, "结束应用"), appName(action))
        }

        // 自定义变量（`setvar:计数+=1`）：**父名 + 这一步**（`自定义变量: 「计数」+= 1`）。
        //  四档各一句话，值是配置里的原文（里面可能还写着 `${其它变量}`），
        //  照原样显示 —— 展开是执行时的事。
        is Action.VarSet -> {
            prefixName(I18n.get(Strings.TOOL_VARIABLES, "自定义变量"), varSetTitle(action))
        }

        // 屏幕亮度（`bright:+15`）：**父名 + 这一档**（`屏幕亮度: 增加 15%`）。
        //  数值编进句子 —— 「增加」两个字看不出加了多少，而这里正是唯一能说清的地方。
        is Action.Brightness -> {
            prefixName(I18n.get(Strings.CATALOG_BRIGHTNESS, "屏幕亮度"), brightnessTitle(action))
        }

        // 音量（`volume:+10`）：与亮度同一个形状
        is Action.Volume -> {
            prefixName(I18n.get(Strings.CATALOG_VOLUME, "音量"), volumeTitle(action))
        }

        // 系统开关：**父名 + 开关名 + 状态**（`系统开关: WIFI - 打开`）——
        //  与响铃 / 导航那些入口格同一条形态；名字在清单里，认不出就退回 id
        is Action.Setting -> {
            settingTitle(action)
        }

        // 其余内置动作：**父格 + 那一档**（`系统导航: 主屏幕`、`响铃状态: 静音`）。
        //  那一支单独拆出去，只为把这一串分支的圈复杂度留在 detekt 的阈值之下。
        else -> {
            builtInTitle(action, fallback)
        }
    }

/**
 * 入口格那一族的一句话 —— 两种形态，判据只有一条：**这一格自己是不是那个动作**。
 *
 *  · 是（录屏）：没有父动作可写，`动作 - 状态`（`录屏 - 开始`）——
 *    冒号读作「父动作之下的一档」，而录屏那三档不是它下面的东西，是同一条命令的三种状态，
 *    所以只剩一条横杠（用户口径「没有父动作的直接用 动作 - 状态」）；
 *  · 不是（系统导航 / 系统开关 / 灵感语音…）：`父动作: 子动作[- 状态]`（[prefixName]）。
 *
 *  问 [ActionCatalog.modeHitOf]（入口格 → 中间层 → 那一行）；不在任何入口格里时退回清单标题
 *  —— 顶层格本来就只有一个名字（`截屏`），不需要再套一层冒号；认不出来才是调用方的兜底。
 */
private fun builtInTitle(
    action: Action,
    fallback: String,
): String {
    //  不许写成 `?: ...` 串下去：这里两个「没有」的意思不同 —— 没有那一档是「它不属于任何入口格」，
    //  而清单标题也没有才是「谁也认不出」（手改的配置），只有后者可以退到调用方的原始写法。
    val hit = ActionCatalog.modeHitOf(action) ?: return ActionCatalog.label(action) ?: fallback
    val detail = hit.detail(action)
    //  两个格式键都由字典给（`%s: %s` / `%s - %s`），不在这一层拼分隔符
    val key =
        if (ActionCatalog.stateChoices(hit.tile.token) != null) {
            Strings.COMMON_NAME_STATE_FORMAT
        } else {
            Strings.COMMON_PREFIX_NAME_FORMAT
        }
    return I18n.format(key, hit.tile.label, detail)
}

/**
 * 冒号右边那一段 —— 从格子里往下的**每一层**用 ` - ` 连起来，系统开关再补一个状态词。
 *
 *  · 导航 / 响铃 / 媒体那些只有一层：`主屏幕` / `静音` / `播放`；
 *  · 灵感语音夹着中间那层：`灵感语音 - 开始`（只写「开始」看不出在说哪个功能）；
 *  · 系统开关：`WIFI - 打开` —— 状态不在菜单里，而是动作自己带的（`setting:wifi:on`）。
 */
private fun ActionCatalog.ModeHit.detail(action: Action): String =
    (path.drop(1).map { it.label } + listOfNotNull(ActionCatalog.switchStateLabel(action)))
        .joinToString(" - ")

/** `父动作名: 子动作名` —— 全项目唯一的拼法（冒号与两侧格式都由字典给）。 */
private fun prefixName(
    parent: String,
    detail: String,
): String = I18n.format(Strings.COMMON_PREFIX_NAME_FORMAT, parent, detail)

/**
 * 「应用控制」那一族的一句话 —— **`应用控制: 动词 - 目标`**（`应用控制: 打开应用 - 微信 - 扫一扫`）。
 *
 *  **公开**：「选择应用」页顶栏那行小字也用这一份（它手上只有动词与目标，没有 [Action]）——
 *  页头与场景动作格、执行提示因此逐字一致。动词由调用方给（打开应用 / 以小窗打开应用 / 结束应用），
 *  目标由 [appTargetLabel] 拼（应用名 + ` - ` + 具体是哪一个）。
 */
fun appControlTitle(
    verb: String,
    target: String,
): String =
    prefixName(
        I18n.get(Strings.TOOL_APP_CONTROL, "应用控制"),
        I18n.format(Strings.COMMON_NAME_STATE_FORMAT, verb, target),
    )

/**
 * 「打开应用」那一族的**目标**怎么念 —— **应用名(小窗 组件名/快捷方式名)**（`微信(小窗 扫一扫)`）。
 *
 *  这一份同时给四处用，所以它只能是**一个实现**：
 *   · 「选择应用」页顶栏那行小字（`ui/screen/apppicker/PickerHeaderText`）；
 *   · 网格里「应用控制」格点开那层菜单的两行子标题、以及格子的小字（`ui/AppNaming.boundAppName`）；
 *   · 系统场景里动作那一格（`ui/screen/command/CommandBranches`）；
 *   · 执行时提示（`host/action/ActionNotice`）。
 *
 *  **附加段放在括号里、空格分隔**（用户口径「写成 微信(小窗 扫一扫)，不要再加横杠分隔」）：
 *  横杠是「同一句里的两段」那条范式（`系统开关: WIFI - 打开`）的记号，而这里括号里说的是
 *  「这个目标的补充信息」，读起来是一层注记，不该与主句的动作 / 状态混用同一种分隔。
 *
 * @param window 是不是**以小窗**那一档（`window:` / 小窗快捷方式）—— 目标里标出来，
 *   否则同一个应用的两条绑定在列表里长得一模一样
 * @param detail 具体是哪一个：组件写类名最后一段、快捷方式写它自己的名字。
 *   名字只有调用方查得到（token 里没有，见 `AppTarget` 的类注释），这一层只管怎么拼。
 *   `null` / 空 / 与应用名相同时**不写进括号**：「微信(微信)」读起来像两个东西。
 */
fun appTargetLabel(
    appName: String,
    window: Boolean,
    detail: String?,
): String {
    val extras =
        listOfNotNull(
            if (window) I18n.get(Strings.COMMON_WINDOW_TAG, "小窗") else null,
            detail?.takeIf { it.isNotBlank() && it != appName },
        )
    if (extras.isEmpty()) return appName
    return I18n.format(Strings.COMMON_TARGET_PAREN_FORMAT, appName, extras.joinToString(" "))
}

/**
 * 系统开关那一档的说法 —— **父名 + 开关名 + 状态**（`系统开关: WIFI - 打开`）。
 *
 *  与响铃 / 导航那些入口格同一条形态（用户口径：「所有命令的文案都是 父动作名: 子动作名 - 状态」）。
 *  状态那一词与网格小字、菜单子标题、`releaseActionTitle` 同源（[ActionCatalog.switchStateLabel]）。
 *  认不出的 id（手改的配置）退回 id 本身，不让整句空掉。
 *
 *  从 [actionTitle] 里拆出来：那一支要单独取「开关名」，留在主 `when` 里会让圈复杂度顶破
 *  detekt 的阈值。
 */
private fun settingTitle(action: Action.Setting): String =
    prefixName(
        I18n.get(Strings.TOOL_SYSTEM_SWITCHES, "系统开关"),
        I18n.format(
            Strings.COMMON_NAME_STATE_FORMAT,
            SystemSwitches.titleOfId(action.id) ?: action.id,
            ActionCatalog.switchStateLabel(action).orEmpty(),
        ),
    )

/**
 * 屏幕亮度那一档的说法（「增加 15%」/「设置为 50%」）。
 *
 *  **公开**：除 [actionTitle] 之外，网格里那一格的小字也要同一句话（`ui/screen/binding/BindingGrids`
 *  的 subtitle 分支）—— 两处各拼一遍的话，同一条绑定在步骤行与网格里会写成两种说法。
 *  百分比用 `%d%%` 的模板自己带：英文 `Increase by 15%`、中文 `增加 15%`，拼接拼不出这个差别。
 */
fun brightnessTitle(action: Action.Brightness): String =
    I18n.format(
        when (action.mode) {
            Action.Brightness.Mode.ADD -> Strings.CATALOG_BRIGHTNESS_ADD_OF
            Action.Brightness.Mode.SUB -> Strings.CATALOG_BRIGHTNESS_SUB_OF
            Action.Brightness.Mode.FIX -> Strings.CATALOG_BRIGHTNESS_SET_OF
        },
        action.percent,
    )

/**
 * 音量那一档的说法（「增加 10%」/「设置为 50%」）—— 与 [brightnessTitle] 同一个形状。
 *
 *  **公开**：网格小字（`ui/screen/binding/BindingGrids`）与步骤标题都要同一句话。
 */
fun volumeTitle(action: Action.Volume): String =
    I18n.format(
        when (action.mode) {
            Action.Volume.Mode.ADD -> Strings.CATALOG_VOLUME_ADD_OF
            Action.Volume.Mode.SUB -> Strings.CATALOG_VOLUME_SUB_OF
            Action.Volume.Mode.FIX -> Strings.CATALOG_VOLUME_SET_OF
        },
        action.percent,
    )

/**
 * 一步变量操作在界面上的说法 —— 四档各一句，值是配置里的原文（可能还写着 `${其它变量}`）。
 *
 *  **公开**（与 [brightnessTitle] / [volumeTitle] 同一个理由）：网格里「自定义变量」那一格的
 *  小字只要这半句（`「计数」+= 1`）—— 那一格只有一格宽，套上 `自定义变量: ` 前缀就被截了，
 *  而格名本身已经写着「自定义变量」。
 *
 *  单独一支只为把 [actionTitle] 的圈复杂度压回 detekt 的阈值之下。
 */
fun varSetTitle(action: Action.VarSet): String =
    I18n.format(
        when (action.op) {
            VarOp.SET -> Strings.VAR_STEP_SET
            VarOp.ADD -> Strings.VAR_STEP_ADD
            VarOp.SUB -> Strings.VAR_STEP_SUB
            VarOp.NOT -> Strings.VAR_STEP_NOT
        },
        action.name,
        action.value,
    )
