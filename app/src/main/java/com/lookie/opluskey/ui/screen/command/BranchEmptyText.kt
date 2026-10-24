package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.screen.editingKeyName

// 「条件执行」空态那句说明的**拼装** —— 与页面分开一支，只为让 `BranchScreen.kt` 的文件级函数
// 数量留在 detekt 的 `TooManyFunctions` 之下（那一支已经 12 个，只差这几个）。

/**
 * 空态那句「现在会发生什么」—— **键名两边加一对括号**，其余照模板拼成一整句。
 *
 *  括号用**对角方括号**（`「」`）：与停用提示那句「「侧键」已停用，可在菜单里启用它」同一对符号，
 *  同一个东西在两处一个长相。原先画过一个描边键帽，得把句子拆成几段才画得出来，读起来断成几截；
 *  两个符号就够标出「这一段是一颗键」，还不用管字号、行宽和中英词序（英文 `%s` 后面还有半句）。
 */
internal fun SettingsPage.branchEmptyText(): String {
    val template = I18n.get(Strings.COMMAND_BRANCH_EMPTY, "将执行按键绑定里的 %s")
    val (open, close) = keyBrackets()
    val key = "$open${editingKeyName()}$close - ${branchGestureNow().label}"
    return template.substringBefore("%s") + key + template.substringAfter("%s", "")
}

/**
 * 键名外面那对括号 —— 中文用对角方括号（`「」`，与停用提示「「侧键」已停用…」同一对），
 * 英文用弯引号（`“”`，正是英文停用提示里那一对）。同一句话在两种语言里各自像本地写法。
 */
private fun keyBrackets(): Pair<String, String> = if (I18n.effectiveLanguage == AppLanguage.ZH_CN) "「" to "」" else "“" to "”"
