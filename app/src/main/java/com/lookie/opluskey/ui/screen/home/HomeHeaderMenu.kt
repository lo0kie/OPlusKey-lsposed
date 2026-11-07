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
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.openStockPage
import com.lookie.opluskey.ui.resetModule
import com.lookie.opluskey.ui.restartModule
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState

/**
 * 首页顶栏右侧的菜单按钮 + 浮层菜单 —— 四项：「默认界面」「重启服务」「导出日志」「重置模块」。
 *
 *  只服务**首页**（`Screen.SHORTCUTS`）那一条顶栏，所以跟着这一屏放 ——
 *  原先它和「选择应用」页那份菜单一起长在那个公共的顶栏文件里，
 *  于是那个「公共组件」文件里躺着两块业务菜单、约 30 处页面字段触点。
 *  现在顶栏只提供 `actions` 槽位，菜单由各页自己摆（口径见 [PageHeader]）。
 *
 *  **「重置配置」与「重置模块」是两个入口、两个范围**：
 *   · 「重置配置」在「更多选项 → 切换配置」菜单的管理行里（见 `AboutMenus.profileManageRows`）——
 *     只重建当前选中的那一份（内容 + 它的命令库），确认窗也在那一页；
 *   · 「重置模块」在这一层（第四行）—— 在它之上再清掉**变量表**（见 `resetModule`）。
 *
 *  ⋮ **本身永远可点**：「默认界面」那一项必须始终够得着
 *  —— 它是模块彻底坏掉时唯一的逃生口（回宿主原版页面）。
 *  跟着 [rowsEnabled] 走的只有那三项**动模块运行时 / 数据**的（重启服务 / 导出日志 / 重置模块）：
 *  模块没就绪时它们改不了什么，放用户去点只会以为生效了。
 *
 *  注意：「更多选项」那一项**已从菜单里删掉**：它只留首页那一行，
 *  且那一行**不吃任何闸** —— 页面整页禁用时，用户仍能从那儿进去看「运行状态」到底断在哪一段
 *  （见 [HomeScreen] 里那一行的注释）。
 *
 *  注意：判据是两个条件的与：`linkReady`（链路）**且** `!configReadOnly`（配置这次读成功了）。
 *  只读态下其余几项也不放出来点：那时什么都写不进去，点了只会得到一句失败。
 *  **两条逃生口不吃这个闸**（各在自己的行上写了 `enabled = true`）：「默认界面」与「重启服务」——
 *  前者是页面坏了时的出口，后者是「执行侧读不懂配置」那一档的修法（那一档整页都被闸住）。
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
internal fun SettingsPage.HomeHeaderMenu() {
    var open by remember { mutableStateOf(false) }
    // 两层的「页面离开收起」各自登记：⋮ 菜单由 MenuPopup 管，确认弹窗由 ConfirmDialog 管（都不是本函数登记）
    val preciseClick = rememberPreciseClickState()
    val rowsEnabled = linkReady && !configState.readOnly

    Box {
        // 触发键同样是库的 IconButton，只是多挂 `preciseClickAnchor`：浮层弹在按下的那一点
        HeaderMenuButton(preciseClick) { open = true }

        PageMenu(
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
                    //  点了**先问一句**：这一下会杀掉本页进程再让宿主拉回来（页面闪一下重开），
                    //  正在编辑、没保存的内容跟着丢 —— 与「清空配置」同一档代价。
                    //  **`enabled = true` 不是漏了闸**：它是「执行侧读不懂配置」那一档的**修法**
                    //  （`LinkFacts.hostConfigReadable` 为 false 时整页被闸住、控件全灰），
                    //  闸住它就成了「提示让你重启服务、而重启服务是灰的」，只能重启手机。
                    //  与上面「默认界面」同档：这两条是逃生口，不吃任何闸。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_RESTART_SERVICE, "重启服务"),
                        icon = PageIcons.ModuleRestart,
                        enabled = true,
                    ) {
                        open = false
                        overlays.confirmRestart = true
                    },
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
                    // 「重置配置」在「更多选项 → 切换配置」那层菜单里（只重建当前那一份）；
                    // 这一行是**另一件事**：重置模块 —— 连变量表一起清（见 `resetModule`）。
                    //  `dividerBefore`：它是破坏性动作，与上面三项不是一类 —— 上面一条加深带。
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_RESET_MODULE, "重置模块"),
                        icon = PageIcons.MenuReset,
                        enabled = rowsEnabled,
                        danger = true,
                        dividerBefore = true,
                    ) {
                        open = false
                        overlays.confirmResetModule = true
                    },
                ).takeIf { open }
                    .orEmpty(),
            preciseClick = preciseClick,
            onDismiss = { open = false },
        )

        HomeHeaderConfirmDialogs()
    }
}

/**
 * 首页 ⋮ 底下那两颗确认窗（「重启服务」/「重置模块」）—— 与菜单同一层 `Box`，各自由
 * `overlays` 里那一位开关控制。
 *
 *  抽出来只为把 [HomeHeaderMenu] 的行数压在 detekt 的 `LongMethod` 之下：菜单只负责「摆哪几行」，
 *  两句「点确定之后会发生什么」在这里。
 */
@Composable
private fun SettingsPage.HomeHeaderConfirmDialogs() {
    ConfirmDialog(
        show = overlays.confirmRestart,
        title = I18n.get(Strings.DIALOG_RESTART_TITLE, "重启服务"),
        summary = I18n.get(Strings.DIALOG_RESTART_MESSAGE, "未保存的内容会丢。"),
        action = I18n.get(Strings.DIALOG_RESTART_CONFIRM, "重启"),
        onDismiss = { overlays.confirmRestart = false },
        onConfirm = {
            overlays.confirmRestart = false
            restartModule()
        },
    )

    //  「重置模块」：与「重置配置」两个入口、两句话 —— 这一句要点名**变量**也会没
    //  （那是它与「重置配置」唯一的差别，不写出来用户看不出两个入口为什么并存）。
    ConfirmDialog(
        show = overlays.confirmResetModule,
        title = I18n.get(Strings.DIALOG_RESET_MODULE_TITLE, "重置模块"),
        summary = I18n.get(Strings.DIALOG_RESET_MODULE_MESSAGE, "当前配置、已保存的命令与变量都会被清空，**不可恢复**。"),
        action = I18n.get(Strings.DIALOG_RESET_MODULE_CONFIRM, "重置"),
        onDismiss = { overlays.confirmResetModule = false },
        onConfirm = {
            overlays.confirmResetModule = false
            resetModule()
        },
    )
}
