package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HEADER_ACTION_GAP
import com.lookie.opluskey.ui.chrome.HeaderIconButton
import com.lookie.opluskey.ui.chrome.HeaderMenuButton
import com.lookie.opluskey.ui.kit.MenuEntry
import com.lookie.opluskey.ui.kit.PageMenu
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.theme.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.state.page.UiState
import com.lookie.opluskey.ui.wire.appTargetLabelOf
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「选择应用」页顶栏右侧**两颗键**：
 *
 *  · 左边那颗是「以小窗口打开」的开关（[SmallWindowToggle]）—— **从 ⋮ 菜单里摘出来的**
 *    （用户口径：做成 header 右侧的第二颗）。它描述「这一趟往哪个动作上写」（`window:` 还是
 *    `app:`），挑应用之前就得定下来，藏在菜单里等于每次多按一下；
 *    ⋮ 里**另留了一份同样的入口**（用户口径：菜单里也要能拨），两处读同一个状态、走同一条
 *    [setSmallWindow]。
 *  · 右边那颗 ⋮ 里是「显示系统应用」「显示失效应用」（**列表怎么显示**）与「以小窗口打开」
 *    「使用原版动画」（**写成哪个动作**）两档，中间一条加深带断开。
 *
 *  两颗各出现各的：只认应用本身的那几趟（[AppPickerState.appOnly] —— 给条件挑、结束应用）
 *  不写 `window:`，「以小窗口打开」不出现；⋮ 那几行只在「打开」那一趟出现，一行都不剩时
 *  **连按钮一起摘掉**（留一颗点开是空面板的按钮只会让人以为界面坏了）。于是结束那一趟顶栏
 *  右侧一颗都没有。
 *
 *  只服务**「选择应用」页**那一条顶栏，所以跟着这一屏放 —— 原先它长在
 *  那个公共的顶栏文件里（因此装了两块业务菜单）。顶栏现在只提供
 *  `actions` 槽位，菜单由各页自己摆（口径见 [PageHeader]）。
 *
 * 列表来自 `MAIN + CATEGORY_LAUNCHER` 查询，只给得出有启动图标的应用；没有启动图标的系统应用与第三方应用
 * （典型是 LSPosed 模块）要靠「显示系统应用」才选得到。拉不起来的应用**都不挂 tag**（那一档只说明
 * 「点下去可能打不开」，不替用户判断能不能用）；藏不藏由上面那一项定。
 * 「小窗打开」不筛列表，只决定挑中的应用写成 `app:` 还是 `window:`。
 * 选中态交给 [MenuEntry.Row] 的 `selected`（行首图标恒为菜单各自那颗）。浮层弹在手指按下那一点（`preciseClickAnchor`）。
 */
@Composable
internal fun SettingsPage.AppPickerMenu() {
    var open by remember { mutableStateOf(false) }
    val preciseClick = rememberPreciseClickState()
    //  先摆开关那颗（`Row` 里先摆的靠左，⋮ 仍贴在最外角）
    SmallWindowToggle()
    //  一行都没有时整颗 ⋮ 都不摆（判据见类注释最后一条）
    val entries = pickerMenuEntries { open = false }
    if (entries.isEmpty()) return
    Box {
        HeaderMenuButton(preciseClick) { open = true }

        PageMenu(
            // 点任意一行都先把菜单收掉（`open` 是这一屏自己的状态，不复位的话下次进页它还开着）
            entries = entries.takeIf { open }.orEmpty(),
            preciseClick = preciseClick,
            onDismiss = { open = false },
        )
    }
}

/**
 * 「以小窗口打开」那颗顶栏开关 —— 点一下拨一档，**当下就决定挑中的应用写成哪个动作**。
 *
 *  **不落盘**（与菜单里原来那一行同一口径）：它描述「这一趟要往哪个动作上写」，
 *  每次进页按正在编辑的动作重算（见 [resetSmallWindowForPicker]）。
 *
 *  开着长什么样：字形走**主色**（与菜单行选中、格子选中同一档），关着是顶栏按钮常规的内容色 ——
 *  这颗键没有「禁用」档，所以只靠颜色分两态，与 `KeysHeaderEdit` 那颗同一个做法
 *  （顶栏按钮不给图标上色，颜色得自己给）。
 *
 *  只认应用本身的那几趟（[AppPickerState.appOnly]）整颗不出现：那几趟写的是
 *  `app:包名` / `proc:stop:包名`，`window:` 没人读，摆在那里只会让人以为能开小窗。
 *
 *  末尾自带一档 [HEADER_ACTION_GAP]：后面紧跟 ⋮，那颗间距由这里统一给，
 *  调用点不必再写一遍。
 */
@Composable
private fun SettingsPage.SmallWindowToggle() {
    if (picker.appOnly) return
    val on = picker.smallWindow.value
    val tint by animateColorAsState(
        targetValue = if (on) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceContainer,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "PickerMiniWindowTint",
    )
    HeaderIconButton(onClick = { setSmallWindow(!on) }) {
        Icon(
            PageIcons.SmallWindow,
            contentDescription = I18n.get(Strings.MENU_OPEN_IN_MINI_WINDOW),
            tint = tint,
        )
    }
    Spacer(Modifier.width(HEADER_ACTION_GAP))
}

/**
 * 拨「以小窗口打开」—— 顶栏那颗开关与 ⋮ 里那一行**共用这一处**。
 *
 *  **与小窗互斥**：「原版动画」与「小窗口」是同一格的两种起法，开一个就关掉另一个
 *  （见 `AppPickerState.stockAnim`）。不做成「置灰按不动」：那需要在两处各维护一次灰态，
 *  而用户真正的意图就是「切成这一档」。
 *
 *  不落盘：它描述的是「这一趟往哪个动作上写」，每次进页按正在编辑的动作重算
 *  （见 `resetSmallWindowForPicker`）。
 *
 *  只在这一页里用（顶栏那颗开关与 ⋮ 里那一行），所以是 `private`。
 *
 * @param on 拨到哪一档（两处都传「当前值取反」）
 */
private fun SettingsPage.setSmallWindow(on: Boolean) {
    picker.smallWindow.value = on
    if (on) picker.stockAnim.value = false
    XLog.w(
        "选择应用：以小窗口打开 = $on（不落盘）／原版动画 = ${picker.stockAnim.value}",
    )
}

/**
 * ⋮ 菜单里那两行 —— 都是「列表怎么显示」。
 *
 *  · 「显示系统应用」/「显示失效应用」都**落盘**（本地持久，不进用户配置，见 [UiState]）：
 *    写失败就把开关拨回去 —— 留着新值会让用户以为偏好已经存下来了，下次进页又变回去。
 *  · 两项又都**只在「打开」那一趟出现**：结束那一趟一律全列，它们在那边没有可改的东西。
 *  · 可能**一行都不剩**（结束那一趟）—— 调用点据此连按钮一起摘掉。
 *
 *  「以小窗口打开」**两处都有**：顶栏那颗开关（[SmallWindowToggle]）与这张表里的一行 ——
 *  同一个状态、同一条 [setSmallWindow]，不是两份实现。
 *
 * @param close 收掉菜单（调用点给的是 `open = false`）
 */
@Composable
private fun SettingsPage.pickerMenuEntries(close: () -> Unit): List<MenuEntry> =
    buildList {
        //  「列表怎么显示」那两项**只服务「打开」那一趟**：结束那一趟一律全列，两个开关在那边
        //  都拨不出任何变化（判据是 `stopApp || …`，恒为真）—— 摆出来就是「按了没反应」。
        if (!picker.stopApp) addAll(pickerDisplayEntries(close))
        //  下面两行与上面那两行**不是一类**（那两行管列表怎么显示，这两行管**写成哪个动作**），
        //  所以在第一条前面加一条加深带断开。两行都只在会写 `app:` 的那几趟出现 —— 与顶栏那颗
        //  小窗开关同一个闸（`appOnly`：给条件挑 / 结束应用那两趟写的是包名与 `proc:stop:`，
        //  `app:` 没人读）。
        if (!picker.appOnly) {
            //  与顶栏那颗开关**同一个入口的第二种摆法**（用户口径：菜单里也要能拨）：
            //  互斥、日志、不落盘都在 [setSmallWindow] 里，两处不各写一遍。
            add(
                MenuEntry.Row(
                    title = I18n.get(Strings.MENU_OPEN_IN_MINI_WINDOW),
                    icon = PageIcons.SmallWindow,
                    selected = picker.smallWindow.value,
                    dividerBefore = true,
                    onPick = {
                        close()
                        setSmallWindow(!picker.smallWindow.value)
                    },
                ),
            )
            add(
                MenuEntry.Row(
                    title = I18n.get(Strings.MENU_APP_STOCK_ANIM),
                    icon = PageIcons.StockAnim,
                    selected = picker.stockAnim.value,
                    onPick = {
                        close()
                        picker.stockAnim.value = !picker.stockAnim.value
                        //  **与小窗互斥**：同一格装不下两种起法，开这一档就把「以小窗口打开」关掉
                        //  （顶栏那一颗反向同理，见 `SmallWindowToggle`）。
                        if (picker.stockAnim.value) picker.smallWindow.value = false
                        XLog.w(
                            "选择应用：使用原版动画 = ${picker.stockAnim.value}（不落盘）" +
                                "／以小窗口打开 = ${picker.smallWindow.value}",
                        )
                    },
                ),
            )
        }
    }

/**
 * 菜单里「列表怎么显示」那两项 —— 系统应用 / 失效应用。
 *
 *  单独一支：两项成对出现、同一套「写盘失败就拨回去」的处理，本来就是一件事。
 *
 * @param close 收掉菜单（调用项给的是 `open = false`）
 */
@Composable
private fun SettingsPage.pickerDisplayEntries(close: () -> Unit): List<MenuEntry> =
    listOf(
        MenuEntry.Row(
            title = I18n.get(Strings.MENU_SHOW_SYSTEM_APPS),
            // 行首图标恒为「系统应用」网格，选中态由 `selected` 负责（行尾对勾 + 文字主题色）
            icon = PageIcons.SystemApps,
            selected = picker.showSystemApps.value,
            onPick = {
                close()
                picker.showSystemApps.value = !picker.showSystemApps.value
                if (!UiState.writeShowSystemApps(picker.showSystemApps.value)) {
                    picker.showSystemApps.value = !picker.showSystemApps.value
                    XLog.w("选择应用：显示系统应用改不动（界面状态写盘失败），已拨回 ${picker.showSystemApps.value}")
                } else {
                    // 判据级（i 在 release 被编译期消除，见 XLog 注释）
                    XLog.w("选择应用：显示系统应用 = ${picker.showSystemApps.value}（已落盘，重查列表）")
                }
            },
        ),
        //  默认关；落盘（本地持久），写失败就拨回去。
        MenuEntry.Row(
            title = I18n.get(Strings.MENU_SHOW_INVALID_APPS),
            icon = PageIcons.ShowUnopenable,
            selected = picker.showUnopenable.value,
            onPick = {
                close()
                picker.showUnopenable.value = !picker.showUnopenable.value
                if (!UiState.writeShowUnopenable(picker.showUnopenable.value)) {
                    picker.showUnopenable.value = !picker.showUnopenable.value
                    XLog.w("选择应用：显示失效改不动（界面状态写盘失败），已拨回 ${picker.showUnopenable.value}")
                } else {
                    XLog.w("选择应用：显示失效 = ${picker.showUnopenable.value}（已落盘）")
                }
            },
        ),
    )

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
    //  这一行报**已选数 / 上限**（`已选择 3 / 25 个应用`）：多选有上限，进度写在顶栏才不用自己去数。
    //
    //  不写名字的理由：多选之后「当前那一个」不是唯一的事实，而 `appPickerChosenTarget()` 取的是
    //  第一个（`appPickerSelectedTarget` 只给得出一个）—— 真机上就成了「勾了三个，顶栏还写着第一个」。
    //  名字本来也已经写在条件表那一行里（那里不限行）。
    //
    //  数的是 `pickedApps`（勾选集合）而不是草稿：勾一下就变，顶栏跟着变。
    if (picker.forBranch) {
        val count = picker.pickedApps.value.count { it.isNotBlank() }
        return if (count == 0) {
            I18n.get(Strings.APP_PICKER_NONE_SELECTED)
        } else {
            I18n.format(Strings.APP_PICKER_BRANCH_PICK_COUNT, count, BranchCondition.MAX_APP_PACKAGES)
        }
    }
    val target =
        appPickerChosenTarget()
            ?: return I18n.get(Strings.APP_PICKER_NONE_SELECTED)
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
