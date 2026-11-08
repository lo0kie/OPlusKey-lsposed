package com.lookie.opluskey.ui.screen.apppicker

import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appTargetLabelOf

// 「选择应用」页顶栏那一行小字 —— 从 `ui/screen/PageTopBar.kt` 搬出来：
//  那一支已经贴着 `TooManyFunctions` 的上限，而这一行本来就只属于这一页。

/**
 * 「选择应用」页这一趟**已经选中**的目标（草稿优先）；`null` = 还没选。
 *
 *  **先看本次草稿**（二级页刚挑完、还没提交的那一条），没有才退回「当前绑定」：
 *  这一行说的是「这一趟将要绑成什么」，草稿才是那个答案。只看当前绑定的话，
 *  挑完某个具体界面 / 快捷方式退回来，顶栏仍写着上一个应用的名字（而页里已经亮了新的）。
 *  草稿在每次进页时由 `AppPickerUiState.beginVisit` 清掉，不会跨趟残留在这一行上。
 *
 *  取的是**完整**目标（含 `/类名`，[SettingsPage.appPickerSelectedTarget]）——
 *  包名那一位会把 `/类名` 剥掉，这一行就拿不出「具体是哪一个界面」了。
 */
internal fun SettingsPage.appPickerChosenTarget(): String? = picker.draft?.target ?: appPickerSelectedTarget()

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
 *  判据分两层：草稿（刚挑完、还没提交的那一条）优先 —— 这一行说的是「这一趟将要绑成什么」；
 *  没有草稿才读当前绑定（`SettingsPage.editingAction`）。没挑过时**空着**：
 *  写「已选择: 无」会被读成有个叫「无」的应用。
 */
internal fun SettingsPage.pickerSubtitle(): String {
    val target = appPickerChosenTarget() ?: return ""
    //  **只写目标**（`微信 - 小窗 - 扫一扫`）：大标题已经是「打开应用 / 结束应用」，
    //  动词在这里再说一遍就是重复；「小窗」要标 —— 那一档与普通打开在列表里长得一样。
    return targetLabel(target, pickerKind())
}

/** 「打开的是什么」—— 只决定后半句要不要接 ` - 具体是哪一个`，不影响写下去的 token。 */
private enum class PickerKind { APP, COMPONENT, SHORTCUT }

/**
 * 这一趟绑的是整个应用 / 某个界面 / 某个快捷方式。
 *
 *  草稿与当前绑定**互斥地**算：草稿在时不去读当前绑定 —— 否则「当前绑的是快捷方式、
 *  这一趟刚挑了个普通应用」会给那个应用接上「 - 某个快捷方式名」的后缀。
 */
private fun SettingsPage.pickerKind(): PickerKind {
    val draft = picker.draft
    if (draft != null) {
        //  `shortcutId` 只有草稿有；`包名/类名` 那一档绑的是应用里某个具体界面
        return when {
            draft.shortcutId != null -> PickerKind.SHORTCUT
            AppTarget.activity(draft.target) != null -> PickerKind.COMPONENT
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
 *  「这一趟刚挑的那一行」的名字优先（`picker.draft?.label`）：挑完要立刻看见，而这时它还没落盘。
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
        draftLabel = picker.draft?.label,
        //  「使用原版动画」那一趟同理（两者互斥，最多一个为真）
        stockAnim = picker.stockAnim.value,
    )
