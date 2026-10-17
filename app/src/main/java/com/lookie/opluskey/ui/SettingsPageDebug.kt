package com.lookie.opluskey.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lookie.opluskey.core.config.CommandLibrary
import com.lookie.opluskey.core.config.ConfigStore
import com.lookie.opluskey.core.i18n.AppLanguage
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.ui.component.MenuPopup
import com.lookie.opluskey.ui.component.PageHeader
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.model.ABOUT_TITLE
import com.lookie.opluskey.ui.model.APP_PICKER_TITLE
import com.lookie.opluskey.ui.model.BINDING_TITLE
import com.lookie.opluskey.ui.model.COMMANDS_TITLE
import com.lookie.opluskey.ui.model.COMMAND_TOOLBAR_BOTTOM
import com.lookie.opluskey.ui.model.DONATE_LIST_TITLE
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.NIGHT_POLL_MS
import com.lookie.opluskey.ui.model.PAGE_COVER_DIM
import com.lookie.opluskey.ui.model.PAGE_TRANSITION
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.SNACKBAR_SHADOW_ROOM
import com.lookie.opluskey.ui.model.specIcon
import com.lookie.opluskey.ui.screen.about.AboutScreen
import com.lookie.opluskey.ui.screen.binding.ActionTile
import com.lookie.opluskey.ui.screen.binding.BindingScreen
import com.lookie.opluskey.ui.screen.binding.BindingTabBand
import com.lookie.opluskey.ui.screen.binding.actionOf
import com.lookie.opluskey.ui.screen.binding.pick
import com.lookie.opluskey.ui.screen.command.CommandActions
import com.lookie.opluskey.ui.screen.command.CommandKindBand
import com.lookie.opluskey.ui.screen.command.CommandScreen
import com.lookie.opluskey.ui.screen.command.addCommand
import com.lookie.opluskey.ui.screen.donate.DonateListScreen
import com.lookie.opluskey.ui.screen.home.HomeScreen
import com.lookie.opluskey.ui.screen.home.appVersionLabel
import com.lookie.opluskey.ui.screen.home.appVersionText
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.CommandDraft
import com.lookie.opluskey.ui.state.CommandDraftState
import com.lookie.opluskey.ui.state.ConfigState
import com.lookie.opluskey.ui.state.FallbackOwners
import com.lookie.opluskey.ui.state.HostLinkState
import com.lookie.opluskey.ui.state.InjectStatus
import com.lookie.opluskey.ui.state.UiState
import com.lookie.opluskey.ui.state.addStep
import com.lookie.opluskey.ui.state.installI18nBridge
import com.lookie.opluskey.ui.state.openAppPickerForStep
import com.lookie.opluskey.ui.state.publishConfig
import com.lookie.opluskey.ui.state.replaceStep
import com.lookie.opluskey.ui.state.saveCommands
import com.lookie.opluskey.ui.state.tickNow
import com.lookie.opluskey.ui.theme.SyncToHost
import com.lookie.opluskey.ui.theme.colorsFor
import com.lookie.opluskey.ui.theme.pageBackgroundColor
import com.lookie.opluskey.ui.theme.systemNight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Snackbar
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.nav.core.NavBackStack
import io.github.suqi8.coui.kmp.nav.core.NavDisplay
import io.github.suqi8.coui.kmp.nav.core.NavDisplayEffects
import io.github.suqi8.coui.kmp.nav.core.navBackStackOf
import io.github.suqi8.coui.kmp.theme.COUITheme

// 调试通道（`debug` 广播）那两条 —— 与页面骨架无关，单独一个文件。

// 顶栏**当前**占掉的高度（含它自己那块状态栏 inset）—— 内容顶部要留出这么多。

/**
 * 调试通道的处理体 —— **只有 debug 包会走到这里**（实现只在 `src/debug/` 源集）。
 *
 * 支持的动作见 [PageDebugChannel] 的类注释（`screen` / `gesture` / `kind` /
 * `name` / `cmd` / `steps` / `del` / `reset` / `save` / `action` / `state`，一次可以带多个）。
 *
 * 执行顺序是**固定的**：先跳页、再切 tab、再填字段、最后才 reset / save —— 这样
 * 「`--es screen COMMANDS --es kind SERIAL --es name X --ez save true`」这一条广播
 * 就是一个完整的用例。认不出的取值只记日志、不抛异常。
 */
internal fun SettingsPage.onDebugCommand(intent: android.content.Intent) {
    intent.getStringExtra("screen")?.let { raw ->
        val target = Screen.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (target != null) {
            XLog.w("调试：跳页 → $target")
            // 走 [navTo]（不是「压一层」）：目标已经在栈里就直接回到它，否则压上去。
            // 于是 `--es screen COMMANDS` 从首页一步落到「自定义命令」页，
            // 上面那条「push 时拨分页状态」的 effect 照样会跑到（栈深变深了）。
            navTo(target)
        } else {
            XLog.w("调试：认不出的 screen=$raw（可用：${Screen.entries.joinToString()}）")
        }
    }
    intent.getStringExtra("gesture")?.let { raw ->
        val target = Gesture.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (target != null) {
            XLog.w("调试：手势 tab → $target")
            debugGesture.value = target
        } else {
            XLog.w("调试：认不出的 gesture=$raw（可用：${Gesture.entries.joinToString()}）")
        }
    }
    intent.getStringExtra("kind")?.let { raw ->
        val target = CommandKind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (target != null) {
            XLog.w("调试：命令类型 → ${target.id}")
            commandDraft.draft.kind = target
        } else {
            XLog.w("调试：认不出的 kind=$raw（可用：${CommandKind.entries.joinToString()}）")
        }
    }
    // 字段一律走**与界面同一条清洗**（名称过 `sanitizeName`），否则填进去的值
    // 与手敲的不是一回事，验出来的结论就不作数
    intent.getStringExtra("name")?.let { commandDraft.draft.name = CommandLibrary.sanitizeName(it) }
    intent.getStringExtra("cmd")?.let { commandDraft.draft.single = it }
    intent.getStringExtra("steps")?.let { raw ->
        commandDraft.draft.steps.clear()
        commandDraft.draft.steps.addAll(raw.split(';').map { it.trim() }.filter { it.isNotEmpty() })
    }
    if (intent.getBooleanExtra("reset", false)) {
        XLog.w("调试：重置表单（等于点「重置 / 取消」）")
        commandDraft.draft.reset()
    }
    // 删一条命令 —— 给「填进去 → 保存 → 验完 → 清掉」这种自测收尾用，
    intent.getStringExtra("del")?.let { raw ->
        val name = CommandLibrary.sanitizeName(raw)
        if (configState.commands.value.none { it.name == name }) {
            XLog.w("调试：没有叫 '$name' 的命令，不用删")
        } else {
            XLog.w("调试：删除命令 '$name'")
            saveCommands(configState.commands.value.filterNot { it.name == name }, "调试删除：$name")
        }
    }
    if (intent.getBooleanExtra("save", false)) {
        XLog.w("调试：触发保存（等于点「保存 / 修改」）")
        addCommand()
    }
    // 通用动作派发：`--es action NAME` —— 页面级调试动作统一走这张表，加新动作只动这里
    intent.getStringExtra("action")?.let { raw ->
        when (raw.uppercase()) {
            "CONFIRM_RESET" -> {
                XLog.w("调试：action=CONFIRM_RESET → 弹出清空确认弹窗")
                confirmReset.value = true
            }

            else -> {
                XLog.w(
                    "调试：认不出的 action=$raw（可用：CONFIRM_RESET）",
                )
            }
        }
    }
    if (intent.getBooleanExtra("state", false)) logDebugState()
}

/**
 * 把当前状态打进日志（TAG `OplusKey`）—— 省掉「`uiautomator dump` 一遍再看」。
 *
 * 打的是**配置里的真值**（三个手势各绑了什么、主开关）与**表单草稿**，
 * 验「保存后到底写进去没有」「返回时有没有把绑定冲掉」这类问题时直接看这一行。
 */
internal fun SettingsPage.logDebugState() {
    val key = configState.config.value.key(KeyId.PLUS)
    XLog.w(
        "调试·状态：screen=$screen 栈=${navStack.joinToString(">")} 主开关=${key.enabled} " +
            "单击=${key.single.token} 双击=${key.double.token} 长按=${key.long.token} | " +
            "表单 kind=${commandDraft.draft.kind.id} editing=${commandDraft.draft.editingName} " +
            "name='${commandDraft.draft.name}' cmd='${commandDraft.draft.single}' steps=${commandDraft.draft.steps.toList()} | " +
            "命令库=${configState.commands.value.map { it.name }}",
    )
}
