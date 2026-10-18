package com.lookie.opluskey.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.ConfirmDialog
import com.lookie.opluskey.ui.HeaderMenuButton
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.MenuPopup
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.openStockPage
import com.lookie.opluskey.ui.resetAll
import com.lookie.opluskey.ui.restartModule
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 首页顶栏右侧的菜单按钮 + 浮层菜单 —— 四项：「默认界面」「重启服务」「导出日志」「清空配置」。
 *
 *  只服务**首页**（`Screen.SHORTCUTS`）那一条顶栏，所以跟着这一屏放 ——
 *  原先它和「选择应用」页那份菜单一起长在 `ui/PageHeader.kt` 里，
 *  于是那个「公共组件」文件里躺着两块业务菜单、约 30 处页面字段触点。
 *  现在顶栏只提供 `actions` 槽位，菜单由各页自己摆（口径见 [PageHeader]）。
 *
 *  ⋮ **本身永远可点**：「默认界面」那一项必须始终够得着
 *  —— 它是模块彻底坏掉时唯一的逃生口（回宿主原版页面）。
 *  跟着 [rowsEnabled] 走的只有那三项**动模块运行时 / 数据**的（重启服务 / 导出日志 / 清空配置）：
 *  模块没就绪时它们改不了什么，放用户去点只会以为生效了。
 *
 *  注意：「更多选项」那一项**已从菜单里删掉**：它只留首页那一行，
 *  且那一行**不吃任何闸** —— 页面整页禁用时，用户仍能从那儿进去看「运行状态」到底断在哪一段
 *  （见 [HomeScreen] 里那一行的注释）。
 *
 *  注意：判据是两个条件的与：`linkReady`（链路）**且** `!configReadOnly`（配置这次读成功了）。
 *  只读态下**只留那两项**：那时什么都写不进去，放「清空配置」出来点，正好会把「读不懂的那份」
 *  当成用户的意图覆盖掉。
 *  **「适配清单」不在判据里**（AGENTS 一、13：清单只是「验过没有」的提示，不许拿去置灰 / 禁用）——
 *  清单外的机器照样能点这三项，只注入了界面层时它们本来就会失败并报出来。
 *  这个状态的退出方式是重进页面（会重读一次）或走「默认界面」。
 *
 *  注意：中途试过「整颗 ⋮ 一起禁用」，被用户否掉 —— 那会把上面那两条命脉一起锁掉。
 *  也别把 `enabled` 加到那颗 `IconButton` 上：库里 `startAction` / `actions` 槽位是**原样渲染**的，
 *  它只给它自己画的东西按 `enabled` 上色，图标不会跟着灰（真机量过：`enabled = false` 时
 *  ⋮ 与返回箭头同为 `(24,24,24)`）—— 真要禁用它，得像 `IconAction` 那样自己算 tint。
 */
@Composable
internal fun SettingsPage.ResetMenu() {
    var open by remember { mutableStateOf(false) }
    // 两层的「页面离开收起」各自登记：⋮ 菜单由 MenuPopup 管，确认弹窗由 ConfirmDialog 管（都不是本函数登记）
    val preciseClick = rememberPreciseClickState()
    val rowsEnabled = linkReady && !configState.readOnly

    Box {
        // 触发键同样是库的 IconButton，只是多挂 `preciseClickAnchor`：浮层弹在按下的那一点
        HeaderMenuButton(preciseClick) { open = true }

        MenuPopup(
            show = open,
            preciseClick = preciseClick,
            onDismiss = { open = false },
            entries =
                listOf(
                    // 「默认界面」：打开未替换的宿主原版页面 —— **永远可用**（逃生口，见上面那段）。
                    //  写显式 `enabled = true`：多年后被「统一置灰」顺手改掉时，这一行会提醒改动者它不能吃那个闸。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_DEFAULT_PAGE, "默认界面"),
                        icon = PageIcons.MenuStock,
                        enabled = true,
                    ) {
                        open = false
                        openStockPage()
                    },
                    MenuEntry.RowLine,
                    // 第二项：重启服务 —— 跟着 [rowsEnabled]（它动的是两个进程，配置不丢，见 [restartModule]）。
                    //  叫「服务」不叫「模块」：这一步杀宿主主进程与本页进程、由系统重新拉起，
                    // **不会**重新加载 system_server 里那份注入（那个只能整机重启）。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_RESTART_SERVICE, "重启服务"),
                        icon = PageIcons.ModuleRestart,
                        enabled = rowsEnabled,
                    ) {
                        open = false
                        restartModule()
                    },
                    MenuEntry.RowLine,
                    // 第三项：导出日志 —— 同样跟着 [rowsEnabled]
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_EXPORT_LOG, "导出日志"),
                        icon = PageIcons.MenuExport,
                        enabled = rowsEnabled,
                    ) {
                        open = false
                        hooks.onExportLog?.invoke()
                    },
                    // 「更多」那一项**已删**（「去掉菜单里的更多」）：
                    // 进「更多选项」页只留首页那一行，且那一行**不吃任何闸**（见 [HomeScreen]）——
                    // 它现在同时承担「日常入口」与「模块坏掉时看运行状态的地方」两个角色。
                    // 组间加深带：下面这项是破坏性动作，与上面三项不是一类，细线分不出来
                    MenuEntry.GroupLine,
                    // 第四项：跟着 [rowsEnabled] —— 未就绪时置灰且吃掉点击（由 MenuRow 那层透明吃事件 Box 兜住，菜单不关、不派发）
                    //  文案是「清空」不是「重置」：这一下**真的把两份配置都清掉**
                    // （含已保存的命令），叫「重置」会让人以为只是恢复默认、数据还在。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_CLEAR_CONFIG, "清空配置"),
                        icon = PageIcons.MenuReset,
                        enabled = rowsEnabled,
                        danger = true,
                    ) {
                        open = false
                        overlays.confirmReset = true
                    },
                ),
        )

        ConfirmDialog(
            show = overlays.confirmReset,
            title = I18n.get(Strings.DIALOG_CLEAR_CONFIG_TITLE, "清空配置"),
            summary = I18n.get(Strings.DIALOG_CLEAR_CONFIG_MESSAGE, "设置与已保存的命令都会被清空，**不可恢复**。"),
            action = I18n.get(Strings.DIALOG_CLEAR_CONFIG_CONFIRM, "清空"),
            onDismiss = { overlays.confirmReset = false },
            onConfirm = {
                overlays.confirmReset = false
                resetAll()
            },
        )
    }
}
