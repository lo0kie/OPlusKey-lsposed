package com.lookie.opluskey.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.Command
import com.lookie.opluskey.config.CommandKind

/**
 * 各处下拉/浮层选择菜单（添加命令、响铃状态、长按菜单）。
 */
@Composable
internal fun SettingsPage.CommandMenuPopup(
    cmd: Command,
    show: Boolean,
    preciseClick: PreciseClickState,
    /** 进编辑态之后调 —— 调用方借此把页面滚回顶部（表单在页面最上面）。 */
    onEdited: () -> Unit = {},
    /** 「删除」：交给调用点弹确认窗（真删在确认之后）。 */
    onDelete: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    // 页面离开时由 MenuPopup 自己登记收走
    MenuPopup(
        show = show,
        preciseClick = preciseClick,
        onDismiss = onDismiss,
        entries = listOf(
            MenuEntry.Row(title = "编辑", icon = PageIcons.CommandEdit) {
                onDismiss()
                draft.load(cmd)
                onEdited()
            },
            // 两项之间一条细线，与 ⋮ 菜单同一条 [PopupRowDivider]
            MenuEntry.RowLine,
            MenuEntry.Row(title = "删除", icon = PageIcons.CommandDelete, danger = true) {
                onDismiss()
                onDelete()
            },
        ),
    )
}

// --------------------------------------------------------- 入口格点开的菜单（浮层）

/**
 * 入口格点开的那层菜单：把 [modes] 一行一行列出来（响铃四档 / 媒体三档）。
 *
 * 与「添加命令」浮层里的二级是同一份行、同一个构造入口（[modeEntries]），只是这里由格子直接触发。
 *
 * @param current 该手势当前绑的动作 —— 给菜单里那一行打勾用。
 */
@Composable
internal fun SettingsPage.TileMenuPopup(
    show: Boolean,
    preciseClick: PreciseClickState,
    /** 要列的行（[ActionCatalog.modeMenu] 取出来的那一层）。 */
    modes: List<ActionCatalog.Spec>,
    current: Action,
    onPick: (ActionCatalog.Spec) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPopup(
        show = show,
        preciseClick = preciseClick,
        onDismiss = onDismiss,
        entries = modeEntries(modes, current) { spec ->
            onDismiss()
            onPick(spec)
        },
    )
}

// --------------------------------------------------------------- 添加命令（浮层选择器）

/**
 * 「添加命令」浮层：挑内置命令 / 单条自定义命令作为一步。
 */
@Composable
internal fun SettingsPage.StepPickerPopup(
    show: Boolean,
    preciseClick: PreciseClickState,
    /** `-1` = 挑中的追加为一步；`>= 0` = 挑中的替换这一行（点已添加的步骤时传行号）。 */
    replaceIndex: Int = -1,
    onDismiss: () -> Unit,
) {
    val singles = commands.value.filter { it.kind == CommandKind.SINGLE }
    // 内置组 = 清单里除「无操作」外的全部 + 末尾一行入口「选择应用」
    val builtIn = ActionCatalog.SERIAL_STEPS + ActionCatalog.APP_STEP

    // 「当前那一步」是哪条命令 —— 只有换一步那个模式才有（`replaceIndex >= 0`）。
    // 一级菜单判据用 [ActionCatalog.isSelected]；二级那几行必须一行一行比（`isBound`），不能用 `isSelected`。
    val currentStepAction = if (replaceIndex >= 0) {
        draft.steps.getOrNull(replaceIndex)?.let {
            runCatching { Action.parse(it) }.getOrNull()
        }
    } else {
        null
    }
    fun isCurrentStep(spec: ActionCatalog.Spec): Boolean =
        currentStepAction?.let { ActionCatalog.isSelected(spec, it) } ?: false

    // 挑中一行落哪里：追加 / 替换（日志与落步共用这一个判据）。
    fun landStep(token: String) {
        if (replaceIndex >= 0) replaceStep(replaceIndex, token) else addStep(token)
    }

    // 二级浮层（入口格那一层菜单）开着没有：`null` = 没开。
    var subTile by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    // 浮层关掉后回到一级：`remember` 不因 `show = false` 清除，不重置会停在二级。
    LaunchedEffect(show) { if (!show) subTile = null }

    // 一级面板上最后一次按下的触点，作为二级浮层的锚点：`PreciseClickState`
    // 把 DOWN 换算成窗口坐标，库以它为零尺寸锚点（横向居中、下方放不下就翻到上方）。
    val panelTouch = remember { PreciseClickState() }

    MenuPopup(
        show = show,
        preciseClick = preciseClick,
        maxHeight = STEP_PICKER_MAX_HEIGHT,
        // 整列挂手指锚点：只记按下点（二级浮层弹在那一点）。
        columnAnchor = panelTouch,
        // 二级开着时这一层不接关闭请求 —— 否则同一毫秒又把它收回去，菜单打不开。
        onDismiss = { if (subTile == null) onDismiss() },
        // 页面离开时一级、二级一起收 —— 不套用上面那条守卫。
        onLeave = {
            subTile = null
            onDismiss()
        },
        entries = buildList {
            add(MenuEntry.Group("内置命令"))
            builtIn.forEachIndexed { index, spec ->
                // 相邻两行一条细线，与 ⋮ 菜单 / 长按菜单同一条 [PopupRowDivider]
                if (index > 0) add(MenuEntry.RowLine)
                add(
                    MenuEntry.Row(
                        title = spec.title,
                        icon = specIcon(spec),
                        // 入口格（响铃状态 / 媒体控制 / 选择应用）会进下一层，所以带右箭头。
                        submenu = ActionCatalog.isEntryTile(spec),
                        // 当前那一步就是这一行时标出来（入口行也认）。
                        selected = isCurrentStep(spec),
                    ) {
                        when (spec.token) {
                            // 「选择应用」→ 进「打开应用」页挑一个；不能交给 `landStep`，
                            // 那会把一个空的 `app:` 写成一步。
                            ActionCatalog.APP_TILE -> {
                                onDismiss()
                                openAppPickerForStep(replaceIndex)
                            }
                            else -> {
                                val modes = ActionCatalog.modeMenu(spec.token)
                                if (modes == null) {
                                    onDismiss()
                                    landStep(spec.token)
                                } else {
                                    // 入口格 → 开二级浮层（响铃四档 / 媒体三档）：一级面板原地留着。
                                    XLog.i("添加命令浮层：点 ${spec.title} → 开二级")
                                    subTile = spec
                                }
                            }
                        }
                    },
                )
            }
            if (singles.isNotEmpty()) {
                // 两组之间一条加深的带（COUI 组间分隔带，full-bleed 4dp）。
                add(MenuEntry.GroupLine)
                add(MenuEntry.Group("自定义命令"))
                singles.forEachIndexed { index, cmd ->
                    if (index > 0) add(MenuEntry.RowLine)
                    val spec = ActionCatalog.commandSpec(cmd)
                    add(
                        MenuEntry.Row(
                            title = spec.title,
                            icon = specIcon(spec),
                            selected = isCurrentStep(spec),
                        ) {
                            onDismiss()
                            landStep(cmd.token)
                        },
                    )
                }
            }
        },
    )

    // 二级浮层：入口格那一层的行（[ActionCatalog.modeMenu]）。它是独立的一个浮层，叠在一级
    // 上面（一级 `show` 没变），所以点「媒体控制」后仍看得见整个列表；弹在手指按下那一点，
    // 它自己那层 dim 铺满整屏，顺带把一级也压暗。
    MenuPopup(
        show = subTile != null,
        preciseClick = panelTouch,
        enableWindowDim = true,
        onDismiss = { subTile = null },
        entries = subTile
            ?.let { ActionCatalog.modeMenu(it.token) }
            ?.let { modes ->
                modeEntries(modes, currentStepAction) { spec ->
                    XLog.i("二级浮层：选中 ${spec.title}（替换行=$replaceIndex）")
                    onDismiss()
                    landStep(spec.token)
                }
            }
            .orEmpty(),
    )
}

/**
 * 入口格那一层菜单的行（响铃四档 / 媒体三档）—— 格子上直接弹的那张菜单与「添加命令」的
 * 二级浮层共用这一份。
 *
 * @param current 正在替换的那一步解析出来的动作（`null` = 不是替换模式），用来把那一档标出来。
 */
internal fun modeEntries(
    modes: List<ActionCatalog.Spec>,
    current: Action?,
    onPick: (ActionCatalog.Spec) -> Unit,
): List<MenuEntry> = modes.flatMapIndexed { index, spec ->
    buildList {
        if (index > 0) add(MenuEntry.RowLine)
        add(
            MenuEntry.Row(
                title = spec.title,
                icon = specIcon(spec),
                //  必须用 [ActionCatalog.isBound]（一行一行比），不能用 `isSelected` ——
                // 后者对整组都算选中，会让二级菜单同时勾上多项。
                selected = current?.let { ActionCatalog.isBound(spec, it) } ?: false,
                onPick = { onPick(spec) },
            ),
        )
    }
}

// 三个浮层统一用 `ListPopupDefaults.DropdownPositionProvider` + 触点锚点。
