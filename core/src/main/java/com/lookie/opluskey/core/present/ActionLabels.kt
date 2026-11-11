package com.lookie.opluskey.core.present

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.VarOp

// 「一个动作怎么念」在 [com.lookie.opluskey.core.model.CommandTitle] 那一个文件里。
//
// 这里只剩**三段材料**里那两段「不是从清单读」的：应用目标（名字在 token 里没有，只有调用方查得到）
// 与变量表达式（是配置里写的原文）。拼句子仍然只有 `CommandTitle.compose` 一处。

/**
 * 「应用控制」那一族的一句话 —— **`应用控制: 动词 - 目标`**（`应用控制: 打开应用 - 微信 - 扫一扫`）。
 *
 *  **公开**：「选择应用」页顶栏那行小字也用这一份（它手上只有动词与目标，没有 [Action]）——
 *  页头与场景动作格、执行提示因此逐字一致。动词由调用方给（打开应用 / 结束应用），
 *  目标由 [appTargetLabel] 拼（应用名 + 括号注记）。
 */
fun appControlTitle(
    verb: String,
    target: String,
): String =
    CommandTitle
        .of(
            parent = I18n.get(Strings.TOOL_APP_CONTROL, "应用控制"),
            name = verb,
            state = target,
        ).sentence()

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
 * @param stockAnim 是不是**用原版启动包裹**那一档（`app:a:…`，见 [Action.App]）—— 与 [window]
 *   同一个理由标出来，两者**互斥**（同一格装不下两种起法，界面侧保证最多一个为真）。
 */
fun appTargetLabel(
    appName: String,
    window: Boolean,
    detail: String?,
    stockAnim: Boolean = false,
): String {
    val extras =
        listOfNotNull(
            if (window) I18n.get(Strings.COMMON_WINDOW_TAG, "小窗") else null,
            if (stockAnim) I18n.get(Strings.COMMON_STOCK_ANIM_TAG, "原版动画") else null,
            detail?.takeIf { it.isNotBlank() && it != appName },
        )
    if (extras.isEmpty()) return appName
    return I18n.format(Strings.COMMON_TARGET_PAREN_FORMAT, appName, extras.joinToString(" "))
}

/**
 * 一步变量操作在界面上的说法 —— 四档各一句，值是配置里的原文（可能还写着 `${其它变量}`）。
 *
 *  **公开**：网格里「自定义变量」那一格的小字只要这半句（`「计数」+= 1`）—— 那一格只有一格宽，
 *  套上 `自定义变量: ` 前缀就被截了，而格名本身已经写着「自定义变量」。
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
