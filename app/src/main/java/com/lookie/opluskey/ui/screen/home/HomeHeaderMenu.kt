package com.lookie.opluskey.ui.screen.home

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.HostApp
import com.lookie.opluskey.core.wire.ModuleWire
import com.lookie.opluskey.core.wire.PageWire
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.HeaderMenuButton
import com.lookie.opluskey.ui.chrome.openStockPage
import com.lookie.opluskey.ui.chrome.restartModule
import com.lookie.opluskey.ui.dialog.ConfirmDialog
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.style.EmphasisText
import com.lookie.opluskey.ui.wire.linkReady
import com.lookie.opluskey.ui.write.resetModule
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *   · 「重置模块」在这一层（第四行）—— 模块落过的盘全回出厂：配置列表、命令库、变量、
 *     界面状态（见 `resetModule`）。
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
 *  ⋮ 与返回箭头同为 `(24,24,24)`）—— 真要禁用它，得像 `SheetAction` 那样自己算 tint。
 */
@Composable
internal fun SettingsPage.HomeHeaderMenu() {
    var open by remember { mutableStateOf(false) }
    // 两层的「页面离开收起」各自登记：⋮ 菜单由 MenuPopup 管，确认弹窗由 ConfirmDialog 管（都不是本函数登记）
    val preciseClick = rememberPreciseClickState()
    val rowsEnabled = linkReady && !configState.readOnly

    //  两行副标题都要**现取**：宿主运行时长读 `link.hostProcStart`（宿主报活时带过来的墙钟时刻），
    //  日志缓冲要跨进程收一次（几百毫秒）。两样都在打开菜单那一刻异步算，绝不进组合期。
    var hostUptime by remember { mutableStateOf<String?>(null) }
    var logSummary by remember { mutableStateOf<String?>(null) }
    //  键里带上 `hostProcStart`：菜单可能在这一趟自检还没回来时就打开了（那时它是 0），
    //  宿主一答上就重算一遍，副标题跟着出现 —— 只盯 `open` 的话会一直空着。
    LaunchedEffect(open, link.hostProcStart) {
        if (!open) return@LaunchedEffect
        val start = link.hostProcStart
        if (start <= 0) {
            //  还没拿到宿主的启动时刻（这一趟自检没回来 / 宿主刚重启）—— 现问一声。
            //  问完那一趟会把 `hostProcStart` 填上，键跟着变、这个 effect 自己再跑一遍。
            PageWire.send(context, HostApp.PACKAGE, Intent(ModuleWire.ACTION_HOST_SERVICE_QUERY))
        }
        //  日志缓冲只取一次：跨进程收一次要几百毫秒，没必要每秒重来。
        logSummary = withContext(Dispatchers.IO) { runCatching { hooks.logBufferSummary?.invoke() }.getOrNull() }
        //  运行时长**每秒重算**：菜单开着的时候它得像秒表一样走（关掉菜单这一趟就被取消）。
        //  一分钟以上那两档文案不会变，重算出来的串与上一帧相同，Compose 不会重组。
        while (true) {
            hostUptime = if (start > 0) uptimeText(System.currentTimeMillis() - start) else null
            delay(UPTIME_TICK_MS)
        }
    }

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
                        //  副标题写**宿主进程已经跑了多久**（重启服务重启的就是它）——
                        //  这一句同时是「你按重启之前，它是不是刚重启过」的证据。
                        summary = hostUptime?.let { I18n.format(Strings.MENU_RESTART_UPTIME, it) },
                        icon = PageIcons.ModuleRestart,
                        enabled = true,
                    ) {
                        open = false
                        overlays.confirmRestart = true
                    },
                    // 第三项：导出日志 —— 同样跟着 [rowsEnabled]
                    MenuEntry.Row(
                        title = I18n.get(Strings.MENU_EXPORT_LOG, "导出日志"),
                        //  副标题是这次会导出去多少（几个进程的缓冲合起来）
                        summary = logSummary,
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
                    // 这一行是**另一件事**：重置模块 —— 模块落过的盘全回出厂
                    //  （配置列表 / 命令库 / 变量 / 界面状态，见 `resetModule`）。
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
 *  菜单只负责「摆哪几行」，两句「点确定之后会发生什么」在这里。
 */
@Composable
private fun SettingsPage.HomeHeaderConfirmDialogs() {
    //  「重置模块」先整盘删再写回出厂配置，都是文件 IO（见 `resetModule`）；这里只负责把那一趟发出去。
    val scope = rememberCoroutineScope()
    ConfirmDialog(
        show = overlays.confirmRestart,
        title = I18n.get(Strings.DIALOG_RESTART_TITLE, "重启服务"),
        //  **不带正文**（用户口径）：这一个是「下一步马上就好」的动作，那句「未保存的改动将丢失」
        //  反而像个警告 —— 用户点的是重启，不是要放弃什么。
        action = I18n.get(Strings.DIALOG_RESTART_CONFIRM, "重启"),
        onDismiss = { overlays.confirmRestart = false },
        onConfirm = {
            overlays.confirmRestart = false
            restartModule()
        },
    )

    //  「重置模块」：与「重置配置」两个入口、两句话 —— 这一句要把**范围**说全
    //  （所有配置 / 命令与变量 / 界面状态），否则用户看不出两个入口为什么并存。
    ConfirmDialog(
        show = overlays.confirmResetModule,
        title = I18n.get(Strings.DIALOG_RESET_MODULE_TITLE, "重置模块"),
        //  正文两行、对齐方式不同：第一行说明「重置了什么」（左对齐，长了会折行），
        //  第二行是代价（短，**居中**）—— 所以走 `body` 槽自己排，不用 `summary` 那一整段。
        body = {
            EmphasisText(
                text = I18n.get(Strings.DIALOG_RESET_MODULE_MESSAGE, "配置（含列表）、命令与变量、界面状态回到出厂。"),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            EmphasisText(
                text = I18n.get(Strings.DIALOG_RESET_MODULE_IRREVERSIBLE, "**不可恢复**。"),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        action = I18n.get(Strings.DIALOG_RESET_MODULE_CONFIRM, "重置"),
        onDismiss = { overlays.confirmResetModule = false },
        onConfirm = {
            overlays.confirmResetModule = false
            scope.launch { resetModule() }
        },
    )
}

/** 「已运行」那一行多久重算一次（毫秒）—— 一秒一跳，与秒表的读感一致。 */
private const val UPTIME_TICK_MS = 1_000L

/**
 * 「已运行 2 小时 13 分」那种写法 —— 只服务「重启服务」那一行的副标题。
 *
 *  三档就够：一小时以上给「小时 + 分」，一分钟以上给「分」，再短给「秒」。
 *  与 `durationText`（滑杆那一档，毫秒 / 秒）不是一件事：那里量的是几十毫秒到几秒。
 */
private fun uptimeText(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return when {
        seconds >= 3600 -> I18n.format(Strings.MENU_UPTIME_HOUR_MIN, seconds / 3600, (seconds % 3600) / 60)
        seconds >= 60 -> I18n.format(Strings.MENU_UPTIME_MIN, seconds / 60)
        else -> I18n.format(Strings.MENU_UPTIME_SEC, seconds)
    }
}
