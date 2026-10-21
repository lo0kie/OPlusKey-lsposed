package com.lookie.opluskey.ui.screen.command

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
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.ModeMenuPopups
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.conditionChoiceIcon
import com.lookie.opluskey.ui.model.conditionChoiceLabel
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionKindLabel
import com.lookie.opluskey.ui.model.conditionTitle
import com.lookie.opluskey.ui.model.specIcon
import com.lookie.opluskey.ui.state.AppPickerState
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowDown
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.suqi8.coui.kmp.basic.PreciseClickState

// 分叉那几张菜单 —— 第一层（换条件 / 换命令 / 删除）、条件种类菜单（十种）、
// 取值二级、命令菜单（内置动作 + 已保存命令）。

/**
 * 两张菜单的装配 —— 一行分两列，各点各的：
 *
 * ```
 * 左列（条件）→ 条件种类菜单 ─┬─ 前台应用 → 进「选择应用」页
 *                             ├─ 响铃档位 / 屏幕方向 → 取值（二级）
 *                             ├─ 其余（无取值）→ 直接落条件
 *                             └─ 上移 / 下移 / 删除
 * 右列（动作）→ 动作菜单（内置动作 / 已保存的命令）
 * ```
 */
@Composable
internal fun SettingsPage.BranchMenus(
    rules: List<StateRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    BranchConditionFlow(rules = rules, menus = menus, tick = tick)
    BranchCommandMenu(rules = rules, menus = menus, tick = tick)
    BranchRowMenu(rules = rules, menus = menus)
}

/**
 * 行尾那颗「更多」菜单 —— 上移 / 下移 / 删除。
 *
 *  两列各点各的（换条件 / 换动作），这一颗管**这一行本身**：顺序就是判定顺序，
 *  所以要能挪；删也放在这里，不与换值混在一起。
 */
@Composable
private fun SettingsPage.BranchRowMenu(
    rules: List<StateRule>,
    menus: BranchMenuState,
) {
    val at = menus.menuAt
    PageMenu(
        entries =
            buildList {
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_MOVE_UP, "上移"),
                        icon = TablerIcons.Outline.ArrowUp,
                        enabled = at > 0,
                    ) {
                        branchDraft.draft.move(at, at - 1)
                        menus.menuAt = -1
                    },
                )
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_MOVE_DOWN, "下移"),
                        icon = TablerIcons.Outline.ArrowDown,
                        enabled = at >= 0 && at < rules.lastIndex,
                    ) {
                        branchDraft.draft.move(at, at + 1)
                        menus.menuAt = -1
                    },
                )
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_DELETE, "删除"),
                        icon = PageIcons.CommandDelete,
                        danger = true,
                        // 删除与上面两步「挪位置」不是一类：一条加深带隔开
                        dividerBefore = true,
                    ) {
                        // **不在这里删**：删除是这一页唯一不可逆的动作，先收起菜单、弹确认窗
                        //  （窗在 `BranchDeleteDialog`，由 `BranchCards` 挂在页面里）
                        menus.menuAt = -1
                        menus.deleteAt = at
                    },
                )
            }.takeIf { at in rules.indices }
                .orEmpty(),
        preciseClick = menus.menuAnchor,
        onDismiss = { menus.menuAt = -1 },
    )
}

/**
 * 条件那张菜单与它的落条件动作。
 *
 *  「响铃档位 / 屏幕方向」的取值是**二级浮层**（一级还留着，挂在它上面），不先关一次再开一次
 *  —— 那两次的开合在真机上就是「菜单闪一下又换了一张」。
 */
@Composable
private fun SettingsPage.BranchConditionFlow(
    rules: List<StateRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    BranchConditionMenu(
        spec =
            ConditionMenuSpec(
                at = menus.condAt,
                // 整张表交给菜单：标题 / 已用 / 当前那三样都从它算（都在这一层算的话，
                //  `ConditionMenuSpec` 会顶到 detekt 的参数上限）
                rules = rules,
                anchor = menus.condAnchor,
                onDismiss = { menus.condAt = AppPickerState.NOT_FOR_BRANCH },
                onPick = { kind, value ->
                    val at = menus.condAt
                    menus.condAt = AppPickerState.NOT_FOR_BRANCH
                    when (kind) {
                        // 「前台应用」要先挑是哪个应用 —— 那一步走「选择应用」页
                        ConditionKind.APP -> {
                            openAppPickerForBranch(at)
                        }

                        else -> {
                            tick()
                            applyBranchCondition(rules, at, BranchCondition.of(kind, value))
                        }
                    }
                },
            ),
    )
}

/**
 * 「换条件」那张菜单要的那几件事 —— 合成一个对象只为把参数个数压回 detekt 的上限。
 *
 *  [at] 是这一趟在改第几条（[AppPickerState.NOT_FOR_BRANCH] = 菜单关着），[rules] 是整张表 ——
 *  顶部小标题（这一格现在的文字）、要置灰的档、当前那一档，都从它按 [at] 算出来。
 */
private class ConditionMenuSpec(
    val at: Int,
    val rules: List<StateRule>,
    val anchor: PreciseClickState,
    val onDismiss: () -> Unit,
    val onPick: (ConditionKind, String) -> Unit,
)

/**
 * 条件种类菜单 —— 「添加条件」与「换条件」都先经过它。
 *
 *  每档**各给一颗字形**（[conditionIcon]）：十行共用一颗的话，菜单整列长得一样，只有文字能分。
 *
 *  要取值的两档（响铃档位 / 屏幕方向）**在这张菜单里开二级** —— 一级不关，二级挂在它上面；
 *  「前台应用」那档跳「选择应用」页（行尾给右箭头）。
 *
 *  这一条**现在**是哪一档就走 `selected`（行首换对勾、文字上主题色）：改条件时最想知道的是
 *  「现在是什么」，不该还要从十行里自己找一遍。
 */
@Composable
private fun SettingsPage.BranchConditionMenu(spec: ConditionMenuSpec) {
    val show = spec.at != AppPickerState.NOT_FOR_BRANCH
    // 这一格**现在**是什么 —— 标题用它（与格子里那半行同一份文案），也是「选中」那一档
    val editing = spec.rules.getOrNull(spec.at)?.condition
    val title = editing?.let { conditionTitle(it, ::appNameOf) } ?: I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
    val current = editing?.kind
    // 已经用掉的档位置灰：一张表里同一个条件出现两次，后一条永远轮不到
    //  （条件空着的行不算「用掉」—— 它还没选，任何一档都该能选）。
    //  **「前台应用」不参与置灰**：它可以出现多条，只要应用不同 —— 重复的是
    //  「同一个应用」，那一条在挑应用时拦（见 `writeBranchCondition`）。
    val used =
        spec.rules
            .filterIndexed { at, _ -> at != spec.at }
            .mapNotNull { it.condition?.kind }
            .filter { it != ConditionKind.APP }
            .toSet()
    // 二级在说哪一档；`null` = 没开
    var subKind by remember { mutableStateOf<ConditionKind?>(null) }
    LaunchedEffect(show) { if (!show) subKind = null }
    // 二级挂在**一级菜单里的触点**上（触发点在菜单里，没有卡片触点可用）
    val panelTouch = remember { PreciseClickState() }

    PageMenu(
        entries =
            if (!show) {
                emptyList()
            } else {
                listOf(
                    // 顶部小标题 = 这一格**现在**的文字（与格子里那半行同一份文案）：
                    //  打开菜单第一眼该看到的不是十种可选，而是「我现在是什么」。
                    //  小标题下面那条细线、行与行之间的细线都由菜单自己加（见 [MenuEntry.Section]）。
                    MenuEntry.Section(
                        title = title,
                        rows =
                            ConditionKind.entries.map { kind ->
                                MenuEntry.Row(
                                    title = conditionKindLabel(kind),
                                    icon = conditionIcon(kind),
                                    // 表里已经有了的那一档置灰（本条自己原来那档除外 —— 它算「还是它」）
                                    enabled = kind !in used,
                                    disabledHint = I18n.get(Strings.COMMAND_BRANCH_DUPLICATE, "这个条件已经加过了"),
                                    // 这一条现在是这一档 ⇒ 画成选中（行首对勾 + 文字主题色）
                                    selected = kind == current,
                                    // 入口格（进下一层）与跳页面的那档才画行尾那一格
                                    submenu = kind.choices.isNotEmpty() || kind == ConditionKind.APP,
                                    trailingIcon =
                                        if (kind == ConditionKind.APP) PageIcons.EnterArrow else PageIcons.Selector,
                                ) {
                                    // 要取值的两档开二级（一级留着）；其余（含跳页面那档）交给调用点 ——
                                    // 关菜单由调用点做：它得先读 `condAt` 才知道落哪一条。
                                    if (kind.choices.isEmpty()) spec.onPick(kind, "") else subKind = kind
                                }
                            },
                    ),
                )
            },
        preciseClick = spec.anchor,
        columnAnchor = panelTouch,
        // 二级开着时这一层不接关闭请求 —— 否则同一毫秒又把它收回去，菜单打不开。
        onDismiss = { if (subKind == null) spec.onDismiss() },
    )

    // 二级：这一档的取值（只有 [ConditionKind.choices] 非空的两档会走到这里）
    PageMenu(
        entries =
            subKind
                ?.let { kind ->
                    // 当前这一档的取值也标出来（老配置里 WIFI / 媒体没有取值 ⇒ 按默认档高亮）
                    val currentValue = editing?.takeIf { it.kind == kind }?.effectiveValue.orEmpty()
                    conditionChoiceEntries(kind, currentValue) { value -> spec.onPick(kind, value) }
                }.orEmpty(),
        preciseClick = panelTouch,
        onDismiss = { subKind = null },
        dim = true,
    )
}

/**
 * 取值那一层的行 —— [ConditionKind.choices] 非空的那几档用（WIFI / 媒体 / 响铃档位 / 屏幕方向）。
 *
 *  @param currentValue 这一档**现在**的取值（用来画选中）；空值由调用方按默认档给
 */
private fun conditionChoiceEntries(
    kind: ConditionKind,
    currentValue: String,
    onPick: (String) -> Unit,
): List<MenuEntry> =
    // 只有两三行、也没有小标题：直接给行，行间细线由菜单自己加
    kind.choices.map { value ->
        MenuEntry.Row(
            title = conditionChoiceLabel(kind, value),
            icon = conditionChoiceIcon(kind, value),
            selected = value == currentValue,
            onPick = { onPick(value) },
        )
    }

/**
 * 动作菜单：这一条命中后执行什么 —— 与「添加命令」浮层**同一张表**：内置动作按
 * [ActionCatalog.STEP_GROUPS] 分组，末尾接「已保存的命令」。
 *
 *  入口格（系统导航 / 媒体控制 …）照样进二级，二级与「添加命令」共用同一份行构造（`ModeMenuPopups` 里的 `modeEntries`）；
 *  「选择应用」那一行进「打开应用」页挑一个应用。
 */
@Composable
private fun SettingsPage.BranchCommandMenu(
    rules: List<StateRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    val singles = configState.commands.value.filter { it.kind == com.lookie.opluskey.core.model.CommandKind.SINGLE }
    val current = rules.getOrNull(menus.commandAt)?.action
    var subTile by remember { mutableStateOf<ActionCatalog.Spec?>(null) }
    val show = menus.commandAt in rules.indices
    LaunchedEffect(show) {
        if (!show) subTile = null
    }
    val panelTouch = remember { PreciseClickState() }
    val at = menus.commandAt // 这一趟改的是第几条（菜单一关就归 -1，先取出来）
    PageMenu(
        entries =
            if (!show) {
                emptyList()
            } else {
                // 页面离开时二级由 `closeOverlays` 一起收，这里不用另交代
                branchCommandEntries(
                    icons = tileIcons.value,
                    singles = singles,
                    current = current,
                    onPick =
                        BranchPick(
                            onPiece = { token ->
                                tick()
                                subTile = null
                                applyBranchAction(at, token)
                                menus.commandAt = -1
                            },
                            onSubTile = { subTile = it },
                            onOpenApp = {
                                subTile = null
                                menus.commandAt = -1
                                openAppPickerForBranch(at, forCommand = true)
                            },
                        ),
                )
            },
        preciseClick = menus.commandAnchor,
        columnAnchor = panelTouch,
        onDismiss = { if (subTile == null) menus.commandAt = -1 },
    )

    // 二级浮层：入口格那一层的行，与「添加命令」同一条路。系统开关那一族再往下那一层
    //  （切换 / 打开 / 关闭）也在同一个组件里 —— 两层与守卫只有那一份实现。
    ModeMenuPopups(
        preciseClick = panelTouch,
        tile = subTile,
        current = current,
        onPick = { spec ->
            tick()
            subTile = null
            applyBranchAction(at, spec.token)
            menus.commandAt = -1
        },
        onDismiss = { subTile = null },
    )
}

/**
 * 动作菜单那三个回调 —— 合成一个对象只为把参数个数压回 detekt 的上限。
 *
 *  [onPiece] 挑中一个能直接写成动作的 token、[onSubTile] 点了入口格（开二级）、
 *  [onOpenApp] 点了「选择应用」（要进挑应用页）。
 */
private class BranchPick(
    val onPiece: (String) -> Unit,
    val onSubTile: (ActionCatalog.Spec) -> Unit,
    val onOpenApp: () -> Unit,
)

/**
 * 动作菜单的一级表 —— 内置动作按 [ActionCatalog.STEP_GROUPS] 分组 + 「已保存的命令」那一段。
 *
 *  与「添加命令」浮层逐项一致：同一张表、同一批图标、同一套「入口格进下一层」的判据。
 */
private fun SettingsPage.branchCommandEntries(
    icons: Map<ActionCatalog.Spec, ImageVector>,
    singles: List<Command>,
    current: Action?,
    onPick: BranchPick,
): List<MenuEntry> =
    buildList {
        // 每组一个 [MenuEntry.Section]：小标题下面那条细线、组内行间细线、组与组之间那条加深带，
        //  全由菜单自己加（早先这里逐项 `add(RowLine)` / `add(GroupLine)`，漏一处就少一条线）。
        ActionCatalog.STEP_GROUPS.forEach { (groupTitle, group) ->
            add(
                MenuEntry.Section(
                    title = ActionCatalog.groupTitleLabel(groupTitle),
                    rows =
                        group.map { spec ->
                            MenuEntry.Row(
                                title = spec.label,
                                icon = iconOf(spec, icons),
                                submenu = ActionCatalog.isEntryTile(spec),
                                trailingIcon =
                                    when {
                                        spec.token == ActionCatalog.APP_TILE -> PageIcons.EnterArrow
                                        ActionCatalog.modeMenu(spec.token) != null -> PageIcons.Selector
                                        else -> null
                                    },
                                selected = current?.let { ActionCatalog.isSelected(spec, it) } ?: false,
                            ) {
                                when {
                                    spec.token == ActionCatalog.APP_TILE -> onPick.onOpenApp()
                                    ActionCatalog.modeMenu(spec.token) == null -> onPick.onPiece(spec.token)
                                    else -> onPick.onSubTile(spec)
                                }
                            }
                        },
                ),
            )
        }
        add(
            MenuEntry.Section(
                title = I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"),
                rows =
                    if (singles.isEmpty()) {
                        listOf(
                            MenuEntry.Row(
                                title = I18n.get(Strings.BINDING_NEW_CUSTOM_COMMAND, "新建命令"),
                                icon = PageIcons.CommandAdd,
                                onPick = { onPick.onPiece(Command.PREFIX) },
                            ),
                        )
                    } else {
                        singles.map { cmd ->
                            MenuEntry.Row(
                                title = cmd.name,
                                icon = iconOf(ActionCatalog.commandSpec(cmd), icons),
                                selected = current?.token == cmd.token,
                            ) { onPick.onPiece(cmd.token) }
                        }
                    },
            ),
        )
    }

/**
 * 一行菜单的图标 —— 从页面级那份 [icons] 取，缺项时退回 [specIcon] 并留一行凭据。
 */
private fun iconOf(
    spec: ActionCatalog.Spec,
    icons: Map<ActionCatalog.Spec, ImageVector>,
): ImageVector =
    icons.getOrElse(spec) {
        XLog.w("图标表缺项：${spec.token} 用兜底字形")
        specIcon(spec)
    }

/**
 * 把某一档动作写到第 [index] 条规则上 —— 关菜单之前调用（关掉之后下标就取不到了）。
 */
private fun SettingsPage.applyBranchAction(
    index: Int,
    token: String,
) {
    val old = branchDraft.draft.rules.getOrNull(index) ?: return
    branchDraft.draft.update(index, old.copy(action = Action.parse(token)))
}

/**
 * 把一条条件写到第 [index] 条规则上（[AppPickerState.NEW_BRANCH] = 新加一条）。
 *
 *  新加的那一条动作留空（[Action.None]）：条件与动作是同一行的两半，选完条件再去选动作
 *  比「先逼用户挑一条命令」顺。
 */
private fun SettingsPage.applyBranchCondition(
    rules: List<StateRule>,
    index: Int,
    condition: BranchCondition,
) {
    val gesture = branchGestureNow()
    val old = rules.getOrNull(index)
    if (index == AppPickerState.NEW_BRANCH || old == null) {
        branchDraft.draft.add(StateRule(gesture, condition, Action.None))
    } else {
        branchDraft.draft.update(index, old.copy(condition = condition))
    }
}
