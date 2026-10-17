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
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.HeaderMenuButton
import com.lookie.opluskey.ui.component.MenuEntry
import com.lookie.opluskey.ui.component.MenuPopup
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.state.UiState
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 「选择应用」页顶栏右侧的菜单按钮 + 浮层菜单 —— 三项：「显示系统应用」「隐藏失效应用」「小窗打开」，
 * 都是开关（行标题以本文件里的 `title` 为准）。
 *
 *  只服务**「选择应用」页**那一条顶栏，所以跟着这一屏放 —— 原先它长在
 *  `ui/component/PageHeader.kt` 里（那个文件因此装了两块业务菜单）。顶栏现在只提供
 *  `actions` 槽位，菜单由各页自己摆（口径见 [PageHeader]）。
 *
 * 列表来自 `MAIN + CATEGORY_LAUNCHER` 查询，只给得出有启动图标的应用；没有启动图标的系统应用与第三方应用
 * （典型是 LSPosed 模块）要靠「显示系统应用」才选得到，其中多数按了会弹「找不到应用」，所以另有「隐藏失效应用」。
 * 「小窗打开」不筛列表，只决定挑中的应用写成 `app:` 还是 `window:`。
 * 选中态交给 [MenuEntry.Row] 的 `selected`（行首图标恒为菜单各自那颗）。浮层弹在手指按下那一点（`preciseClickAnchor`）。
 */
@Composable
internal fun SettingsPage.AppPickerMenu() {
    var open by remember { mutableStateOf(false) }
    val preciseClick = rememberPreciseClickState()
    Box {
        HeaderMenuButton(preciseClick) { open = true }

        MenuPopup(
            show = open,
            preciseClick = preciseClick,
            onDismiss = { open = false },
            entries =
                listOf(
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_SHOW_SYSTEM_APPS, "显示系统应用"),
                        // 行首图标恒为「系统应用」网格，选中态由 `selected` 负责（行尾对勾 + 文字主题色）
                        icon = PageIcons.SystemApps,
                        selected = picker.showSystemApps.value,
                        // 先关菜单再派发（`open` 得自己复位，否则下次进这一页它还开着）
                        onPick = {
                            open = false
                            picker.showSystemApps.value = !picker.showSystemApps.value
                            // 落盘（本地持久，不进用户配置 —— 见 [UiState]）：写在翻档这一下，读在进页那一次。
                            //  写失败就**把开关拨回去**：留着新值会让用户以为这份偏好已经存下来了，
                            //  下次进页又变回去。
                            if (!UiState.writeShowSystemApps(picker.showSystemApps.value)) {
                                picker.showSystemApps.value = !picker.showSystemApps.value
                                XLog.w("选择应用：显示系统应用改不动（界面状态写盘失败），已拨回 ${picker.showSystemApps.value}")
                            } else {
                                // 判据级（i 在 release 被编译期消除，见 XLog 注释）
                                XLog.w("选择应用：显示系统应用 = ${picker.showSystemApps.value}（已落盘，重查列表）")
                            }
                        },
                    ),
                    // 相邻两行一条细线 —— 与全项目其它菜单同一件（[MenuEntry.RowLine]）
                    MenuEntry.RowLine,
                    // 把「按下去必弹失败提示」的那些**也列出来** —— 判据 [LaunchableApp.launchable] 与执行端同一把尺子；
                    // 它是纯显示过滤，不重查列表。默认关（= 不显示），用户口径。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_SHOW_INVALID_APPS, "显示失效应用"),
                        icon = PageIcons.ShowUnopenable,
                        selected = picker.showUnopenable.value,
                        onPick = {
                            open = false
                            picker.showUnopenable.value = !picker.showUnopenable.value
                            if (!UiState.writeShowUnopenable(picker.showUnopenable.value)) {
                                picker.showUnopenable.value = !picker.showUnopenable.value
                                XLog.w("选择应用：显示失效改不动（界面状态写盘失败），已拨回 ${picker.showUnopenable.value}")
                            } else {
                                XLog.w("选择应用：显示失效 = ${picker.showUnopenable.value}（已落盘）")
                            }
                        },
                    ),
                    MenuEntry.RowLine,
                    // 开着时挑中的应用写成 `window:<包名>` —— 用小窗起，见执行侧 `MiniWindowLauncher.launchAppInWindow`。
                    // 注意：这一项**不落盘**：它描述的是「这一趟要把动作写成哪个」，
                    //    不是「这一页该怎么显示」—— 每次进这一页都会按正在编辑的动作重算
                    //    （见 [SettingsPage.picker] / [SettingsPage.resetSmallWindowForPicker]）。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_OPEN_IN_MINI_WINDOW, "以小窗口打开"),
                        icon = PageIcons.SmallWindow,
                        selected = picker.smallWindow.value,
                        onPick = {
                            open = false
                            picker.smallWindow.value = !picker.smallWindow.value
                            XLog.w("选择应用：以小窗口打开 = ${picker.smallWindow.value}（临时档，不落盘）")
                        },
                    ),
                ),
        )
    }
}
