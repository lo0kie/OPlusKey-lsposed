package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import com.lookie.opluskey.ui.SettingsPage
import io.github.suqi8.coui.kmp.basic.PreciseClickState

// 「自定义命令」表单页那**三层浮层**（全屏编辑 / 详情菜单 / 步骤菜单）—— 与页内正文分开一个文件：
// 它们都弹在页面那一层的 `Box` 里，不进滚动内容，改的理由也与表单各栏不同
// （它们跟着「怎么开、怎么关、开的时候摘不摘焦点」变，正文跟着字段变）。

/**
 * 三层浮层各自的开合位 —— **一页一份**（`remember` 在 [CommandPage] 里）。
 *
 *  收成一个小对象而不是三个 `remember { mutableStateOf(...) }`：三者一次只会开一个，
 *  `CommandPage` 里那些「点了就设哪一位」的回调也就只剩一件事，页面函数因此留给排版。
 *  与 `CommandUiState` 里那几个位不是一回事 —— 那几个跨页存活（命令卡菜单、确认窗），
 *  这三位跟着这一页的组合走（单条 / 多条各一份，互不影响）。
 */
internal class CommandPageOverlays {
    /** 「命令详情」那颗展开键 / 菜单项点开的全屏编辑弹窗（见 [CommandFullscreenDialog]）。 */
    var fullscreen by mutableStateOf(false)

    /** 「命令详情」小标题行尾那颗「更多」：菜单里是「全屏编辑 / 测试命令」。 */
    var detailMenu by mutableStateOf(false)

    /** 「命令列表」小标题行尾那颗「更多」：排序态从菜单里进出。 */
    var listMenu by mutableStateOf(false)
}

/**
 * 把这三层摆进页面那一层 `Box` —— 那边只留「哪一栏画什么」，开关与回调在这里对齐。
 *
 * @param overlays 三层浮层的开合位（本页 `remember` 的那一份）
 * @param sorting 排序态：两个菜单都跟着禁用（排序时进不去另一层菜单）
 * @param detailAnchor / @param listAnchor 两颗「更多」的锚点，浮层弹在按下的那一点
 */
@Composable
internal fun SettingsPage.CommandOverlayLayer(
    overlays: CommandPageOverlays,
    sorting: Boolean,
    tick: () -> Unit,
    detailAnchor: PreciseClickState,
    listAnchor: PreciseClickState,
) {
    val focusManager = LocalFocusManager.current
    CommandFullscreenDialog(
        show = overlays.fullscreen,
        onDismiss = {
            //  再摘一次（主防线在开窗那一处，见 `CommandPage` 的 `openFullscreen`）：
            //  弹窗开着这段时间里输入框可能被重新聚焦（点空白 / 返回键的焦点回落），
            //  关窗时同样会发那一下 `bringIntoView`。
            focusManager.clearFocus()
            overlays.fullscreen = false
        },
    )
    DetailMenu(
        open = overlays.detailMenu,
        anchor = detailAnchor,
        tick = tick,
        onClose = { overlays.detailMenu = false },
        onFullscreen = {
            //  与页面那条入口同一条账：开之前先摘焦点，等关窗时它早就不是焦点
            focusManager.clearFocus()
            overlays.fullscreen = true
        },
    )
    StepListMenu(
        open = overlays.listMenu,
        sorting = sorting,
        anchor = listAnchor,
        tick = tick,
        onClose = { overlays.listMenu = false },
    )
}
