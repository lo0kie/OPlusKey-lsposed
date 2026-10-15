package com.lookie.opluskey.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.ActionCatalog
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.STEP_PICKER_MAX_HEIGHT
import com.lookie.opluskey.ui.screen.addStep
import com.lookie.opluskey.ui.screen.openAppPickerForStep
import com.lookie.opluskey.ui.screen.replaceStep
import io.github.suqi8.coui.kmp.basic.PreciseClickState

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
            MenuEntry.Row(
                title = I18n.get(Strings.COMMON_EDIT, "编辑"),
                icon = PageIcons.CommandEdit,
            ) {
                onDismiss()
                // 走 [SettingsPage.loadDraft] 而**不是** `draft.load`：表单里可能还有没保存的改动，
                //  直接灌进去会把它整份覆盖掉。
                loadDraft(cmd)
                onEdited()
            },
            // 两项之间一条细线，与 ⋮ 菜单同一条 [PopupRowDivider]
            MenuEntry.RowLine,
            MenuEntry.Row(
                title = I18n.get(Strings.COMMON_DELETE, "删除"),
                icon = PageIcons.CommandDelete,
                danger = true,
            ) {
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
        entries = modeEntries(modes, tileIcons.value, current) { spec ->
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

    // 图标一律取自页面级那一份（[SettingsPage.tileIcons]）—— 与网格同一条队列、同一颗字形，
    // 不在这里各算各的。这一层的每一行都在这条队列里（[ActionCatalog.STEP_GROUPS] 与命令库）。
    val icons = tileIcons.value

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
            // 分组与网格同一张表（[ActionCatalog.STEP_GROUPS]）—— 组名与组内顺序逐项一致。
            ActionCatalog.STEP_GROUPS.forEachIndexed { groupIndex, (groupTitle, group) ->
                if (groupIndex > 0) add(MenuEntry.GroupLine)
                val localizedGroupTitle = when (groupTitle) {
                    "系统操作" -> I18n.get(Strings.CATALOG_SYSTEM_ACTIONS, groupTitle)
                    "快捷功能" -> I18n.get(Strings.CATALOG_QUICK_TOOLS, groupTitle)
                    else -> I18n.get(groupTitle, groupTitle)
                }
                add(MenuEntry.Group(localizedGroupTitle))
                group.forEachIndexed { index, spec ->
                    // 相邻两行一条细线，与 ⋮ 菜单 / 长按菜单同一条 [PopupRowDivider]
                    if (index > 0) add(MenuEntry.RowLine)
                    add(
                        MenuEntry.Row(
                            title = spec.label,
                            icon = icons.getValue(spec),
                            // 入口格会进下一层（二级菜单）
                            submenu = ActionCatalog.isEntryTile(spec),
                            // 行尾那颗按「点开去哪」分：跳另一个页面的「选择应用」用网格里同一颗
                            //  「窗口 + 加号」（见 `PageIcons.BrowserPlus`）；**有二级菜单的那些行**
                            //  （系统导航 / 系统按键 / 媒体控制 / 响铃状态）给选择器字形
                            //  —— 点开还要再选一层，见 [PageIcons.Selector]。
                            trailingIcon = when {
                                spec.token == ActionCatalog.APP_TILE -> PageIcons.BrowserPlus
                                ActionCatalog.modeMenu(spec.token) != null -> PageIcons.Selector
                                else -> null
                            },
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
                                        // 入口格 → 开二级浮层（一级面板原地留着）。
                                        XLog.w("添加命令浮层：点 ${spec.title} → 开二级")
                                        subTile = spec
                                    }
                                }
                            }
                        },
                    )
                }
            }
            // 「自定义命令」这一段始终在：一条都没保存时给一行「去创建」——点它只关菜单即可，
            // 用户本来就在「自定义命令」页上。
            add(MenuEntry.GroupLine)
            add(MenuEntry.Group(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "自定义命令")))
            if (singles.isEmpty()) {
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.BINDING_CREATE_CUSTOM_COMMAND, "创建自定义命令"),
                        icon = PageIcons.CommandAdd,
                        onPick = onDismiss,
                    ),
                )
            } else {
                singles.forEachIndexed { index, cmd ->
                    if (index > 0) add(MenuEntry.RowLine)
                    val spec = ActionCatalog.commandSpec(cmd)
                    add(
                        MenuEntry.Row(
                            title = spec.label,
                            icon = icons.getValue(spec),
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
                modeEntries(modes, icons, currentStepAction) { spec ->
                    XLog.w("二级浮层：选中 ${spec.title}（替换行=$replaceIndex）")
                    onDismiss()
                    landStep(spec.token)
                }
            }
            .orEmpty(),
    )
}

/**
 * 入口格那一层菜单的行（响铃四档 / 媒体五档）—— 格子上直接弹的那张菜单与「添加命令」的
 * 二级浮层共用这一份。
 *
 * 行之间的分隔线按行自己带的那一档给（[ActionCatalog.Spec.dividerBefore]）：标了的用**加深带**
 * 把菜单切成两段（媒体控制的「播放 / 暂停 / 播放 / 暂停」与「上一首 / 下一首」、响铃状态的
 * 「循环」与那三档），其余仍是相邻两行那条细线。
 *
 * @param icons 页面级那一份图标表（[SettingsPage.tileIcons]）—— 各层的行都从它取字形。
 * @param current 正在替换的那一步解析出来的动作（`null` = 不是替换模式），用来把那一档标出来。
 */
internal fun modeEntries(
    modes: List<ActionCatalog.Spec>,
    icons: Map<ActionCatalog.Spec, ImageVector>,
    current: Action?,
    onPick: (ActionCatalog.Spec) -> Unit,
): List<MenuEntry> = modes.flatMapIndexed { index, spec ->
    buildList {
        if (index > 0) {
            add(if (spec.dividerBefore) MenuEntry.GroupLine else MenuEntry.RowLine)
        }
        add(
            MenuEntry.Row(
                title = spec.label,
                icon = icons.getValue(spec),
                //  必须用 [ActionCatalog.isBound]（一行一行比），不能用 `isSelected` ——
                // 后者对整组都算选中，会让二级菜单同时勾上多项。
                selected = current?.let { ActionCatalog.isBound(spec, it) } ?: false,
                onPick = { onPick(spec) },
            ),
        )
    }
}

// 三个浮层统一用 `ListPopupDefaults.DropdownPositionProvider` + 触点锚点。
