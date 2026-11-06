package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.HeaderMenuButton
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.UiState
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 「选择应用」页顶栏右侧的菜单按钮 + 浮层菜单：「显示系统应用」「显示失效应用」「以小窗口打开」，
 * 都是开关（行标题以本文件里的 `title` 为准）。
 *
 *  **按这一趟是做什么，三项各出现各的**：
 *  · 「显示系统应用」/「显示失效应用」只在**打开**那一趟出现 —— 结束那一趟**一律全列**
 *    （停掉一个没有启动图标的应用正是它的用处，藏起来反而没得选），所以那边这两项都没有、
 *    也不读这两个开关。曾经只藏了后一项，「显示系统应用」留着却拨不出任何变化
 *    （判据是 `stopApp || …`，那一趟恒为真）—— 一行拨了没反应的开关比没有这一行更糟；
 *  · 「以小窗口打开」只在**给手势挑应用**时出现（[AppPickerState.appOnly] 的两趟 —— 条件执行、
 *    结束应用 —— 写的是 `app:包名` / `proc:stop:包名`，`window:` 没人读）；
 *  · **三项都不适用时连按钮都不摆**（结束那一趟就是这个情形）：摘掉两颗开关之后它不剩一行，
 *    留一颗点开是空面板的按钮只会让人以为界面坏了。
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
    //  一行都没有时整颗按钮都不摆（判据见类注释最后一条）
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
 * 菜单里那三行 —— 前两项是「列表怎么显示」，末一项是「挑中的写成哪个动作」。
 *
 *  · 「显示系统应用」/「显示失效应用」都**落盘**（本地持久，不进用户配置，见 [UiState]）：
 *    写失败就把开关拨回去 —— 留着新值会让用户以为偏好已经存下来了，下次进页又变回去。
 *    两项又都**只在「打开」那一趟出现**：结束那一趟一律全列，它们在那边没有可改的东西。
 *  · 「以小窗口打开」**不落盘**：它描述「这一趟要往哪个动作上写」，每次进页按正在编辑的动作
 *    重算（见 `resetSmallWindowForPicker`）；开着时写成 `window:<包名>`。
 *  · 末一项在**只认应用本身**的几趟里不出现（[AppPickerState.appOnly]）：那几趟写的是
 *    `app:包名` / `proc:stop:包名`，`window:` 没人读，摆在那里只会让人以为能开小窗。
 *  · 三项可能**一行都不剩**（结束那一趟）—— 调用点据此连按钮一起摘掉。
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
        // 给「条件执行」/「结束应用」挑时不出现：那两趟都不读 `window:`
        if (picker.appOnly) {
            return@buildList
        }
        add(
            MenuEntry.Row(
                title = I18n.get(Strings.MENU_OPEN_IN_MINI_WINDOW, "以小窗口打开"),
                icon = PageIcons.SmallWindow,
                selected = picker.smallWindow.value,
                onPick = {
                    close()
                    picker.smallWindow.value = !picker.smallWindow.value
                    XLog.w("选择应用：以小窗口打开 = ${picker.smallWindow.value}（临时档，不落盘）")
                },
            ),
        )
    }
