package com.lookie.opluskey.ui.state

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage

// 「多条命令」那几步的增删改 —— 从 `ui/screen/CommandScreen` / `ui/screen/AppPicker` 搬过来。
// 它们只动页面状态（草稿里的步骤、导航、「选应用」那一档），一行像素都不画，所以归 `ui/state`；
// 搬过来之后 `ui/component`（「添加命令」浮层）可以直接用它们，不必反过来依赖具体屏
// （见 `docs/decoupling.md` 动作 4）。
// /

/** 多条命令加一步（从「添加命令」浮层的「选择应用」挑完回来时调）。 */
internal fun SettingsPage.addStep(token: String) {
    // 界面那一层的闸在别处：「添加命令」那颗按钮满员时自己置灰（`enabled = !stepsFull`），
    //  两个调用点也被它挡着。这里是**兜底** —— 越过界面直接调本函数时不能把步数顶过头。
    if (commandDraft.draft.steps.size >= Command.MAX_STEPS) {
        XLog.w("多条命令：已达上限 ${Command.MAX_STEPS} 步，拒绝再加 → $token")
        return
    }
    commandDraft.draft.steps.add(token)
    XLog.w("多条命令：加第 ${commandDraft.draft.steps.size} 步 → $token")
    navTo(Screen.COMMANDS)
}

/**
 * 多条命令把第 [index] 步换成 [token] —— 点已添加的那一行弹出的选择菜单里挑完时调。
 */
internal fun SettingsPage.replaceStep(
    index: Int,
    token: String,
) {
    if (index !in commandDraft.draft.steps.indices) {
        // 越界不能静默 return：调用方已经 tick / 关了菜单，用户看不到任何反应也无从判断。
        XLog.w("多条命令：第 ${index + 1} 步不存在（当前 ${commandDraft.draft.steps.size} 步），换步已忽略")
        toast(I18n.get(Strings.TOAST_COMMAND_STEP_MISSING, "这一步已经不在列表里了"))
        navTo(Screen.COMMANDS)
        return
    }
    commandDraft.draft.steps[index] = token
    XLog.w("多条命令：第 ${index + 1} 步换成 $token")
    navTo(Screen.COMMANDS)
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
    navTo(Screen.APP_PICKER)
}
