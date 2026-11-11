package com.lookie.opluskey.ui.screen.apppicker

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appTargetLabelOf

// 「选择应用」页顶栏那一行小字 —— 从 `ui/screen/PageTopBar.kt` 搬出来：
//  那一支已经贴着 `TooManyFunctions` 的上限，而这一行本来就只属于这一页。

/**
 * 「选择应用」页这一趟**选中的**目标；`null` = 还没选。
 *
 *  首读取的是**选中位**（`AppPickerUiState.pending`）：进页那一刻它由当前绑定填（`beginVisit`），
 *  之后被两处改 —— 二级页点确认，以及**列表里直接点一行**（结束应用 / 条件执行那一趟：
 *  点一行就是选中它，没有二级页、也就没有草稿）。只看草稿的话，结束应用点了一行之后顶栏
 *  还写着上一个应用名，而页里那一行已经亮了、FAB 也能按。
 *
 *  取的是**完整**目标（含 `/类名`）—— 包名那一位会把 `/类名` 剥掉，
 *  这一行就拿不出「具体是哪一个界面」了。
 *
 *  绑的是**快捷方式**时也照写（「已选择: 微信」）：它属于「打开应用」那一族，
 *  与菜单里那一行的勾同一个判据 —— 菜单说绑着、这一行说没绑，才是最让人困惑的。
 */
internal fun SettingsPage.appPickerChosenTarget(): String? = appPickerUi.pending?.target ?: appPickerSelectedTarget()

/**
 * 「选择应用」页大标题下面那一行 —— **这一趟要做什么: 做给谁**（见 `PageHeader` 的 `subtitle`）。
 *
 *  ## 前半句
 *
 *  只按「怎么开」分两档：普通打开「打开应用」、小窗「以小窗打开应用」；
 *  「结束应用」那一趟换了动词，单独一档。
 *
 *  **打开的是什么不再往前半句写**（用户口径）：组件与快捷方式已经把「具体是哪一个」
 *  写在后半句的 ` - ` 后缀里，前面再说一遍就是同一句话说两次 ——
 *  「打开应用: 微信 - 扫一扫」，而不是「打开快捷方式: 微信 - 扫一扫」。
 *
 *  ## 后半句
 *
 *  应用名；组件与快捷方式再跟一句「具体是哪一个」，用 ` - ` 接（用户口径）。
 *
 *  判据只有一份：**选中位**（`AppPickerUiState.pending`，判据见 [appPickerChosenTarget]）——
 *  它既装了进页时的当前绑定，也装了这一趟在二级页 / 列表里点中的那一条，
 *  「打开应用」与「结束应用」两趟读的都是它。没选中就说没有
 *  （`app_picker.none_selected`）：这一行要**始终在**（用户口径：没有副标题时顶栏会矮一截，
 *  翻页 / 返回时整页跟着跳），而写「已选择: 无」会被读成有个叫「无」的应用。
 */
internal fun SettingsPage.pickerSubtitle(): String {
    //  **给条件挑「前台应用」那一趟是多选**（`picker.forBranch`，见 `AppPickerState.openForBranch`）：
    //  这一行只报个数（`已选择 3 个应用`）。
    //
    //  不写名字的理由：多选之后「当前那一个」不是唯一的事实，而 `appPickerChosenTarget()` 取的是
    //  第一个（`appPickerSelectedTarget` 只给得出一个）—— 真机上就成了「勾了三个，顶栏还写着第一个」。
    //  名字本来也已经写在条件表那一行里（那里不限行），这里报个数正好与一级菜单那行子标题同一句
    //  （`conditionMenuSummary` 用的是同一个 `command.branch_app_count`）。
    //
    //  数的是 `pickedApps`（勾选集合）而不是草稿：勾一下就变，顶栏跟着变。
    if (picker.forBranch) {
        val count = picker.pickedApps.value.count { it.isNotBlank() }
        return if (count == 0) {
            I18n.get(Strings.APP_PICKER_NONE_SELECTED, "没有选择应用")
        } else {
            I18n.format(Strings.COMMAND_BRANCH_APP_COUNT, count)
        }
    }
    val target =
        appPickerChosenTarget()
            ?: return I18n.get(Strings.APP_PICKER_NONE_SELECTED, "没有选择应用")
    //  **只写目标**（`微信 - 小窗 - 扫一扫`）：大标题已经是「打开应用 / 结束应用」，
    //  动词在这里再说一遍就是重复；「小窗」要标 —— 那一档与普通打开在列表里长得一样。
    return targetLabel(target, pickerKind())
}

/** 「打开的是什么」—— 只决定后半句要不要接 ` - 具体是哪一个`，不影响写下去的 token。 */
private enum class PickerKind { APP, COMPONENT, SHORTCUT }

/**
 * 这一趟绑的是整个应用 / 某个界面 / 某个快捷方式。
 *
 *  **选中位与当前绑定互斥地算**：选中位非空时不去读当前绑定 —— 否则「当前绑的是快捷方式、
 *  这一趟刚点了个普通应用」会给那个应用接上「 - 某个快捷方式名」的后缀。
 *  选中位比草稿宽一档：结束应用那一趟点一行就是选中它（没有二级页，也就没有草稿），
 *  而顶栏那一行的判据与它同源，两边必须读到同一份（否则「名字是新的、后缀是旧的」）。
 */
private fun SettingsPage.pickerKind(): PickerKind {
    val pending = appPickerUi.pending
    if (pending != null) {
        //  `shortcutId` 只有「挑中的那一条」才有；`包名/类名` 那一档是应用里某个具体界面
        return when {
            pending.shortcutId != null -> PickerKind.SHORTCUT
            AppTarget.activity(pending.target) != null -> PickerKind.COMPONENT
            else -> PickerKind.APP
        }
    }
    return when (val action = editingAction()) {
        //  快捷方式那一族（`appshortcut:`）没有 `/类名` 的概念，先认它
        is Action.AppShortcut -> {
            PickerKind.SHORTCUT
        }

        is Action.App -> {
            if (AppTarget.activity(action.appTarget.orEmpty()) != null) PickerKind.COMPONENT else PickerKind.APP
        }

        is Action.AppWindow -> {
            if (AppTarget.activity(action.appTarget.orEmpty()) != null) PickerKind.COMPONENT else PickerKind.APP
        }

        else -> {
            PickerKind.APP
        }
    }
}

/**
 * 后半句：应用名 [+ ` - ` 具体组件 / 快捷方式]。
 *
 *  **拼法不在这里**：走 [`appTargetLabelOf`][com.lookie.opluskey.ui.appTargetLabelOf] ——
 *  网格里那一格的小字、点开那层菜单两行的子标题也都是它，三处必须是同一句
 *  （以前这一处只认老写法的 `|名字`，重新进页时名字在 `Config.appNames` 里读不到，
 *  于是页头只剩应用名）。
 *
 *  「这一趟刚挑的那一行」的名字优先（`AppPickerUiState.pending.label`）：挑完要立刻看见，
 *  而这时它还没落盘。选中位而不是草稿 —— 结束应用那一趟点一行就是选中，没有草稿。
 */
private fun SettingsPage.targetLabel(
    target: String,
    kind: PickerKind,
): String =
    appTargetLabelOf(
        target = target,
        shortcut = kind == PickerKind.SHORTCUT,
        //  「以小窗打开」那一趟：目标里标出来（`微信 - 小窗`）
        window = picker.smallWindow.value,
        draftLabel = appPickerUi.pending?.label,
        //  「使用原版动画」那一趟同理（两者互斥，最多一个为真）
        stockAnim = picker.stockAnim.value,
    )
