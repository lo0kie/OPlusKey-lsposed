package com.lookie.opluskey.ui

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command

// 「多条命令」那几步的增删改 —— 只动页面状态（草稿里的步骤、导航、「选应用」那一档），一行像素都不画。
//  放在 `ui` 根而不是 `ui/state`：`ui/state` 只装**状态对象**（谁的字段、谁能写），
//  这些是「对状态做的动作」，要读页面导航与草稿两个域，所以与 `PageText` / `PageFeedback` 同级。
//  这样 `screen/*` 与浮层都能直接调它，不必反过来依赖具体屏（见 `docs/decoupling.md` 动作 4）。

/**
 * 多条命令加一步（从「添加命令」浮层的「选择应用」挑完回来时调）。
 *
 * @return true = 真的加进去了；false = 满员被拒（已 toast）。调用方据此决定要不要退页 ——
 *   把「没加」当「加了」会让刚挑的应用凭空消失，用户一条提示都看不到。
 */
internal fun SettingsPage.addStep(token: String): Boolean {
    // 界面那一层的闸在别处：「添加命令」那颗按钮满员时自己置灰（`enabled = !stepsFull`），
    //  两个调用点也被它挡着。这里是**兜底** —— 越过界面直接调本函数时不能把步数顶过头。
    if (commandDraft.draft.steps.size >= Command.MAX_STEPS) {
        XLog.w("多条命令：已达上限 ${Command.MAX_STEPS} 步，拒绝再加 → $token")
        hint(I18n.format(Strings.TOAST_COMMAND_STEP_FULL, Command.MAX_STEPS), slot = HINT_SLOT_COMMAND)
        return false
    }
    commandDraft.draft.steps.add(token)
    XLog.w("多条命令：加第 ${commandDraft.draft.steps.size} 步 → $token")
    navTo(Screen.COMMANDS)
    return true
}

/**
 * 多条命令把第 [index] 步换成 [token] —— 点已添加的那一行弹出的选择菜单里挑完时调。
 *
 * @return true = 换成了；false = 那一步已经不在（已 toast 并退回命令页）
 */
internal fun SettingsPage.replaceStep(
    index: Int,
    token: String,
): Boolean {
    if (index !in commandDraft.draft.steps.indices) {
        // 越界不能静默 return：调用方已经 tick / 关了菜单，用户看不到任何反应也无从判断。
        XLog.w("多条命令：第 ${index + 1} 步不存在（当前 ${commandDraft.draft.steps.size} 步），换步已忽略")
        hint(I18n.get(Strings.TOAST_COMMAND_STEP_MISSING, "这一步已经不在列表里了"), slot = HINT_SLOT_COMMAND)
        navTo(Screen.COMMANDS)
        return false
    }
    commandDraft.draft.steps[index] = token
    XLog.w("多条命令：第 ${index + 1} 步换成 $token")
    navTo(Screen.COMMANDS)
    return true
}

/**
 * 从「添加命令」浮层（或点已添加那一步弹出的同一张选择菜单）进「打开应用」页 ——
 * 挑中的应用作为一步写进当前多条命令：[replaceIndex] < 0 时追加，`>= 0` 时把那一步换成它。
 *
 * 与网格那条入口的区别只有两处：不记手势（[AppPickerState.gesture] = null），以及调用点在
 * 「自定义命令」页上 —— 压进栈之后「打开应用」下面那一页就是「自定义命令」，返回自然退到它。
 */
internal fun SettingsPage.openAppPickerForStep(replaceIndex: Int = -1) {
    XLog.w("添加命令：进「打开应用」页挑一步（替换行=$replaceIndex）")
    picker.openForStep(replaceIndex)
    // 搜索词清空：它是页面级状态，不随页面出栈注销 —— 留着的话下次进来就是一份被过滤过的列表。
    picker.filter.value = ""
    // 「以小窗口打开」按**这次要编辑的那一步**重算（不落盘、不跨次记忆，见 [AppPickerState.smallWindow]；
    // 另一条入口在 SettingsPage 的「打开应用」格）
    resetSmallWindowForPicker()
    // 进页唯一复位点：点过的行回到当前绑定，定位 / 拦人窗 / 刷新指示器归零（清单不清）
    appPickerUi.beginVisit(appPickerSelectedTarget(), bindingShortcutId())
    navTo(Screen.APP_PICKER)
}
