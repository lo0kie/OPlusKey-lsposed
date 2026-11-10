package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.SettingsPage

// 「条件执行」空态那句说明的**拼装** —— 与页面分开一支，只为让 `BranchScreen.kt` 的文件级函数
// 数量留在 detekt 的 `TooManyFunctions` 之下（那一支已经 12 个，只差这几个）。

/**
 * 空态那句「现在会发生什么」—— 模板里的 `%s`（原来填「键名 - 手势」）**不填**，去掉它之后
 * 压掉多余空格，其余照模板拼成一整句。
 *
 *  三档都在同一页里，一句话点名其中一档反而误导；这句只说明「没有场景时会回落到按键绑定」。
 */
internal fun SettingsPage.branchEmptyText(): String {
    val template = I18n.get(Strings.COMMAND_BRANCH_EMPTY, "将执行按键绑定里的 %s")
    //  这句不拼「键名 - 手势」：三档都在同一页里，一句话点名其中一档反而误导。
    //  模板里的 `%s` 没有可填的键名：去掉它时连同旁边那一个空格一起收掉，再压掉连续空格，
    //  否则中文会拖着行尾空格、英文会留下「Will run  from …」这种双空格。
    return template.replace("%s", "").replace(Regex(" +"), " ").trim()
}
