package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage

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
    //  键名那截与顶栏子标题**同一份**（[branchDefaultLabel]）：
    //  在编组合键时它是「「侧键」+「电量 +」- 单击」，单键时是「「快捷键」- 单击」——
    //  这里再拼一次 `editingKeyName()` 的话，组合键那一趟会写成「按键绑定里的「快捷键」」，
    //  而那颗键根本不在这次编辑里。
    return template.substringBefore("%s") + branchDefaultLabel() + template.substringAfter("%s", "")
}
