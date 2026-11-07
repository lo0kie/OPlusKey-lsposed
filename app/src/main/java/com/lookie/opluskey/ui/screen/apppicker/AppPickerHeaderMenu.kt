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
import com.lookie.opluskey.ui.HEADER_ACTION_GAP
import com.lookie.opluskey.ui.HeaderIconButton
import com.lookie.opluskey.ui.HeaderMenuButton
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.UiState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 「选择应用」页顶栏右侧**两颗键**：
 *
 *  · 左边那颗是「以小窗口打开」的开关（[SmallWindowToggle]）—— **从 ⋮ 菜单里摘出来的**
 *    （用户口径：做成 header 右侧的第二颗）。它描述「这一趟往哪个动作上写」（`window:` 还是
 *    `app:`），挑应用之前就得定下来，藏在菜单里等于每次多按一下；
 *  · 右边那颗 ⋮ 里只剩「显示系统应用」「显示失效应用」—— 那两项是**列表怎么显示**，
 *    与「写成哪个动作」不是一件事，不该挤在同一张菜单里。
 *
 *  两颗各出现各的：只认应用本身的那几趟（[AppPickerState.appOnly] —— 给条件挑、结束应用）
 *  不写 `window:`，「以小窗口打开」不出现；⋮ 那两行只在「打开」那一趟出现，两行都不剩时
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
    HeaderIconButton(
        onClick = {
            picker.smallWindow.value = !on
            XLog.w("选择应用：以小窗口打开 = ${picker.smallWindow.value}（顶栏开关，不落盘）")
        },
    ) {
        Icon(
            PageIcons.SmallWindow,
            contentDescription = I18n.get(Strings.MENU_OPEN_IN_MINI_WINDOW, "以小窗口打开"),
            tint = tint,
        )
    }
    Spacer(Modifier.width(HEADER_ACTION_GAP))
}

/**
 * ⋮ 菜单里那两行 —— 都是「列表怎么显示」。
 *
 *  · 「显示系统应用」/「显示失效应用」都**落盘**（本地持久，不进用户配置，见 [UiState]）：
 *    写失败就把开关拨回去 —— 留着新值会让用户以为偏好已经存下来了，下次进页又变回去。
 *  · 两项又都**只在「打开」那一趟出现**：结束那一趟一律全列，它们在那边没有可改的东西。
 *  · 可能**一行都不剩**（结束那一趟）—— 调用点据此连按钮一起摘掉。
 *
 *  「以小窗口打开」**不在这张表里**：它已经挪到顶栏，成了那一颗开关（见 [SmallWindowToggle]）。
 *
 * @param close 收掉菜单（调用点给的是 `open = false`）
 */
@Composable
private fun SettingsPage.pickerMenuEntries(close: () -> Unit): List<MenuEntry> =
    buildList {
        //  「列表怎么显示」那两项**只服务「打开」那一趟**：结束那一趟一律全列，两个开关在那边
        //  都拨不出任何变化（判据是 `stopApp || …`，恒为真）—— 摆出来就是「按了没反应」。
        if (!picker.stopApp) {
            add(
                MenuEntry.Row(
                    title = I18n.get(Strings.MENU_SHOW_SYSTEM_APPS, "显示系统应用"),
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
            )
            //  默认关；落盘（本地持久），写失败就拨回去。
            add(
                MenuEntry.Row(
                    title = I18n.get(Strings.MENU_SHOW_INVALID_APPS, "显示失效应用"),
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
        }
    }
