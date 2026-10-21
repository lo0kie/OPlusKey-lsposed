package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.ModeMenuPopups
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.addStep
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.specIcon
import com.lookie.opluskey.ui.openAppPickerForStep
import com.lookie.opluskey.ui.replaceStep
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 各处下拉/浮层选择菜单（添加命令、响铃状态、长按菜单）。
 *
 * 注：`onEdited` / `onDelete` 的 `= {}` 默认值**看起来是死的**（唯一调用点两个都显式传了），
 *  但它撑住的是参数个数这条线 —— 去掉默认值会让 detekt 的 `LongParameterList` 判据换成另一个
 *  签名匹配（基线里那条按**带默认值的签名**记着），而本仓不许往基线里添条目。留着。
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
    PageMenu(
        entries =
            listOf(
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
                // 两项之间那条细线由菜单自己加
                MenuEntry.Row(
                    title = I18n.get(Strings.COMMON_DELETE, "删除"),
                    icon = PageIcons.CommandDelete,
                    danger = true,
                ) {
                    onDismiss()
                    onDelete()
                },
            ).takeIf { show }
                .orEmpty(),
        preciseClick = preciseClick,
        onDismiss = onDismiss,
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
    val singles = configState.commands.value.filter { it.kind == CommandKind.SINGLE }

    // 图标一律取自页面级那一份（[SettingsPage.tileIcons]）—— 与网格同一条队列、同一颗字形，
    // 不在这里各算各的。这一层的每一行都在这条队列里（[ActionCatalog.STEP_GROUPS] 与命令库）。
    val icons = tileIcons.value

    // 「当前那一步」是哪条命令 —— 只有换一步那个模式才有（`replaceIndex >= 0`）。
    // 一级菜单判据用 [ActionCatalog.isSelected]；二级那几行必须一行一行比（`isBound`），不能用 `isSelected`。
    val currentStepAction =
        if (replaceIndex >= 0) {
            commandDraft.draft.steps.getOrNull(replaceIndex)?.let {
                runCatching { Action.parse(it) }.getOrNull()
            }
        } else {
            null
        }

    fun isCurrentStep(spec: ActionCatalog.Spec): Boolean = currentStepAction?.let { ActionCatalog.isSelected(spec, it) } ?: false

    // 挑中一行落哪里：追加 / 替换（日志与落步共用这一个判据）。
    fun landStep(token: String) {
        if (replaceIndex >= 0) replaceStep(replaceIndex, token) else addStep(token)
    }

    // 二级浮层（入口格那一层菜单）开着没有：`null` = 没开。
    //  它上面还能再叠一层（系统开关的三档），那一层在组件内部，调用方看不见。
    var subTile by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    // 浮层关掉后回到一级：`remember` 不因 `show = false` 清除，不重置会停在二级。
    LaunchedEffect(show) {
        if (!show) subTile = null
    }

    // 一级面板上最后一次按下的触点，作为二级浮层的锚点：`PreciseClickState`
    // 把 DOWN 换算成窗口坐标，库以它为零尺寸锚点（横向居中、下方放不下就翻到上方）。
    val panelTouch = remember { PreciseClickState() }

    PageMenu(
        // 整列挂手指锚点：只记按下点（二级浮层弹在那一点）。
        columnAnchor = panelTouch,
        // 二级开着时这一层不接关闭请求 —— 否则同一毫秒又把它收回去，菜单打不开。
        //  页面离开时一级、二级一起收，靠的是 [MenuPopup] 里那个 `OverlayCloser`（默认走 onDismiss）。
        onDismiss = { if (subTile == null) onDismiss() },
        entries =
            buildList {
                // 分组与网格同一张表（[ActionCatalog.STEP_GROUPS]）—— 组名与组内顺序逐项一致。
                //  每组一个 [MenuEntry.Section]：小标题下那条细线、组内行间细线、组与组之间的
                //  加深带全由菜单自己加，这里一项分隔都不写。
                ActionCatalog.STEP_GROUPS.forEach { (groupTitle, group) ->
                    add(
                        MenuEntry.Section(
                            title = ActionCatalog.groupTitleLabel(groupTitle),
                            rows =
                                group.map { spec ->
                                    MenuEntry.Row(
                                        title = spec.label,
                                        //  用 `getOrElse` 兜底而不是 `getValue`：新增一族菜单 / 条目时，若它的 spec
                                        //  没被 [ActionCatalog.queue] 收进图标表，`getValue` 会在**宿主的组合期**
                                        //  直接抛异常（用户手机上）。漏项退回建表那支 [specIcon]，并留一行 W 凭据。
                                        icon =
                                            icons.getOrElse(spec) {
                                                XLog.w("图标表缺项：${spec.token} 用兜底字形")
                                                specIcon(spec)
                                            },
                                        // 入口格会进下一层（二级菜单）
                                        submenu = ActionCatalog.isEntryTile(spec),
                                        // 行尾那颗按「点开去哪」分：跳另一个页面的「选择应用」给**右箭头**
                                        //  （与入口格、列表同一颗，见 `PageIcons.EnterArrow`）；**有二级菜单的那些行**
                                        //  （系统导航 / 系统按键 / 媒体控制 / 响铃状态）给选择器字形
                                        //  —— 点开还要再选一层，见 [PageIcons.Selector]。
                                        trailingIcon =
                                            when {
                                                spec.token == ActionCatalog.APP_TILE -> PageIcons.EnterArrow
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
                                                    XLog.w("添加命令浮层：点 ${spec.label} → 开二级")
                                                    subTile = spec
                                                }
                                            }
                                        }
                                    }
                                },
                        ),
                    )
                }
                // 「自定义命令」这一段始终在：一条都没保存时给一行「去创建」——点它只关菜单即可，
                // 用户本来就在「自定义命令」页上。
                add(
                    MenuEntry.Section(
                        title = I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"),
                        rows =
                            if (singles.isEmpty()) {
                                listOf(
                                    MenuEntry.Row(
                                        title = I18n.get(Strings.BINDING_NEW_CUSTOM_COMMAND, "新建命令"),
                                        icon = PageIcons.CommandAdd,
                                        onPick = onDismiss,
                                    ),
                                )
                            } else {
                                singles.map { cmd ->
                                    val spec = ActionCatalog.commandSpec(cmd)
                                    MenuEntry.Row(
                                        title = spec.label,
                                        icon =
                                            icons.getOrElse(spec) {
                                                XLog.w("图标表缺项：${spec.token} 用兜底字形")
                                                specIcon(spec)
                                            },
                                        selected = isCurrentStep(spec),
                                    ) {
                                        onDismiss()
                                        landStep(cmd.token)
                                    }
                                }
                            },
                    ),
                )
            }.takeIf { show }
                .orEmpty(),
        preciseClick = preciseClick,
    )

    // 二级浮层：入口格那一层的行（[ActionCatalog.modeMenu]）。它是独立的一个浮层，叠在一级
    // 上面（一级 `show` 没变），所以点「媒体控制」后仍看得见整个列表；弹在手指按下那一点，
    // 它自己那层 dim 铺满整屏，顺带把一级也压暗。
    //  「系统开关」那一族还能再往下叠一层（切换 / 打开 / 关闭）—— 两层与它们的守卫都在
    //  [ModeMenuPopups] 里，这里只报「开哪一格」与「选中哪一档」。
    ModeMenuPopups(
        preciseClick = panelTouch,
        tile = subTile,
        current = currentStepAction,
        onPick = { spec ->
            XLog.w("二级浮层：选中 ${spec.label}（替换行=$replaceIndex）")
            subTile = null
            // 组件只收了它自己那两层；一级那张列表还在，这里一并关掉
            onDismiss()
            landStep(spec.token)
        },
        onDismiss = { subTile = null },
    )
}

// 三个浮层统一用 `ListPopupDefaults.DropdownPositionProvider` + 触点锚点。
