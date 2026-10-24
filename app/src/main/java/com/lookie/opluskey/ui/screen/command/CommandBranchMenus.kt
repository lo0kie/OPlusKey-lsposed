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
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.StateRule
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.conditionChoiceIcon
import com.lookie.opluskey.ui.model.conditionChoiceLabel
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionKindLabel
import com.lookie.opluskey.ui.model.conditionTitle
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.ResumeSheet
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowDown
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.suqi8.coui.kmp.basic.PreciseClickState

// 分叉那几张菜单 —— 第一层（换条件 / 换命令 / 删除）、条件种类菜单（每种一行）、
// 取值二级、命令菜单（内置动作 + 已保存命令）。

/**
 * 两张菜单的装配 —— 一行分两列，各点各的：
 *
 * ```
 * 左列（条件）→ 条件种类菜单 ─┬─ 前台应用 → 进「选择应用」页
 *                             ├─ 其余每一档 → 取值（二级）
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
 * 条件那两张菜单与它的落条件动作。
 *
 *  一级选**种类**、二级选**那一档取值**，两级拼成一次完整的选择：「响铃状态」+「振动」。
 *  二级是**浮层**（一级还留着，挂在它上面），不是先关一次再开一次 —— 那两次的开合在真机上
 *  就是「菜单闪一下又换了一张」。
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
 * 条件种类菜单 —— 「添加条件」与「换条件」都先经过它，它是**一级**。
 *
 *  每档**各给一颗字形**（[conditionIcon]）：八行共用一颗的话，菜单整列长得一样，只有文字能分。
 *
 *  一级里**每一行都还有下一层**：带候选档的开二级（[ConditionKind.choices]，一级不关、二级挂在
 *  它上面）；「前台应用」跳「选择应用」页（行尾给右箭头）。没有「点了就直接落条件」的行 ——
 *  那需要一个不带取值的条件，而 [ConditionKind] 里已经没有这种档了。
 *
 *  这一条**现在**是哪一档就走 `selected`（行首换对勾、文字上主题色）：改条件时最想知道的是
 *  「现在是什么」，不该还要从这几行里自己找一遍。
 */
@Composable
private fun SettingsPage.BranchConditionMenu(spec: ConditionMenuSpec) {
    val show = spec.at != AppPickerState.NOT_FOR_BRANCH
    // 这一格**现在**是什么 —— 标题用它（与格子里那半行同一份文案），也是「选中」那一档
    //  读的是**第 0 条**：这张菜单改的就是第 0 条（见 `applyBranchCondition`），标题必须与它同源。
    //  一块带好几条时其余几条这份菜单管不着 —— 也就不该拿它们来写标题。
    val editing = spec.rules.getOrNull(spec.at)?.condition
    val title = editing?.let { conditionTitle(it, ::appNameOf) } ?: I18n.get(Strings.COMMAND_BRANCH_ANY, "未选择")
    val current = editing?.kind
    // 置灰的判据是「**这一档的每一种取值都用过了**」，不是「这一档用过没有」：
    //  屏幕状态有亮屏 / 锁屏两档，加过「亮屏」之后还想加「锁屏」，那是两条不同的条件；
    //  按整档灰掉，用户只能回去改已有那条（真机反馈）。
    //  真正不能重复的是**同一条**（同一个种类 + 同一个取值）—— 那一层在二级里挡（见
    //  [conditionChoiceRows]）。
    //  **「前台应用」不参与**：它可以出现多条，只要应用不同 —— 重复的是「同一个应用」，
    //  那一条在挑应用时拦（见 `writeBranchCondition`）。
    val used = usedConditionsOf(spec.rules, skipAt = spec.at)
    val disabled = ConditionKind.entries.filter { it.exhausted(used) }.toSet()
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
                    //  打开菜单第一眼该看到的不是一长串可选，而是「我现在是什么」。
                    //  小标题下面那条细线、行与行之间的细线都由菜单自己加（见 [MenuEntry.Section]）。
                    MenuEntry.Section(
                        title = title,
                        rows = conditionKindRows(current, disabled, spec.onPick) { subKind = it },
                    ),
                )
            },
        preciseClick = spec.anchor,
        columnAnchor = panelTouch,
        // 二级开着时这一层不接关闭请求 —— 否则同一毫秒又把它收回去，菜单打不开。
        onDismiss = { if (subKind == null) spec.onDismiss() },
    )

    // 二级：这一档的取值（一级选中的种类 + 这里选的档位 = 一条完整条件）
    //
    //  顶部小标题写的是**一级里刚点的那个种类**：二级浮层是独立一张菜单，不写这一行的话，
    //  它看着与「独立的一张取值表」没区别（尤其是「开 / 关」这种文字本身不带主语的档）。
    PageMenu(
        entries =
            subKind
                ?.let { kind ->
                    // 当前这一档的取值也标出来（老配置里 WIFI / 媒体没有取值 ⇒ 按默认档高亮）
                    val currentValue = editing?.takeIf { it.kind == kind }?.effectiveValue.orEmpty()
                    listOf(
                        MenuEntry.Section(
                            title = conditionKindLabel(kind),
                            rows = conditionChoiceRows(kind, currentValue, used) { value -> spec.onPick(kind, value) },
                        ),
                    )
                }.orEmpty(),
        preciseClick = panelTouch,
        onDismiss = { subKind = null },
        dim = true,
    )
}

/**
 * 一级菜单那几行 —— 一种条件一行，`used` 里的置灰、`current` 那一档打勾。
 *
 *  抽成一支只为把 [BranchConditionMenu] 压回 `LongMethod` 的阈值：这里只有「行怎么画」，
 *  与「菜单开没开、二级挂在哪儿」无关。
 *
 * @param current 这一条现在那一档（画成选中）
 * @param disabled 表里**取值已经全用掉**的档（点一下只说「这个条件已经加过了」）
 * @param onPick 「前台应用」那一档交给调用点去挑页；其余档交给 [onSub] 开二级
 * @param onSub 要开二级取值的那一档
 */
private fun conditionKindRows(
    current: ConditionKind?,
    disabled: Set<ConditionKind>,
    onPick: (ConditionKind, String) -> Unit,
    onSub: (ConditionKind) -> Unit,
): List<MenuEntry.Row> =
    ConditionKind.entries.map { kind ->
        MenuEntry.Row(
            title = conditionKindLabel(kind),
            icon = conditionIcon(kind),
            // 取值全用掉了才置灰（本条自己原来那档不计入 —— 它算「还是它」）
            enabled = kind !in disabled,
            disabledHint = I18n.get(Strings.COMMAND_BRANCH_DUPLICATE, "这个条件已经加过了"),
            // 这一条现在是这一档 ⇒ 画成选中（行首对勾 + 文字主题色）
            selected = kind == current,
            // 每一行点下去都还有下一层（跳页面 / 弹二级取值）—— 行尾那格一律留出
            submenu = true,
            trailingIcon = if (kind == ConditionKind.APP) PageIcons.EnterArrow else PageIcons.Selector,
        ) {
            // 「前台应用」先去挑是哪个应用（进另一页）；其余每一档都开二级选值。
            //  关一级菜单由调用点做：它得先读 `condAt` 才知道落哪一条。
            if (kind == ConditionKind.APP) onPick(kind, "") else onSub(kind)
        }
    }

/**
 * 取值那一层的行 —— [ConditionKind.choices] 非空的那几档用（WIFI / 媒体状态 / 响铃状态 / 屏幕方向…）。
 *
 *  **用过的取值**在这一层挡，而不是把整档在一级灰掉（理由见 [BranchConditionMenu] 里那段注释）：
 *  同一张表里「屏幕状态 = 亮屏」可以出现一次，「锁屏」是另一条，两条都该能加。
 *
 * @param currentValue 这一档**现在**的取值（用来画选中）；空值由调用方按默认档给
 * @param used 表里已经用掉的「条件 + 取值」（不含正在改的这一条）
 */
private fun conditionChoiceRows(
    kind: ConditionKind,
    currentValue: String,
    used: Set<UsedCondition>,
    onPick: (String) -> Unit,
): List<MenuEntry.Row> =
    kind.choices.map { value ->
        MenuEntry.Row(
            title = conditionChoiceLabel(kind, value),
            icon = conditionChoiceIcon(kind, value),
            // 这一档这一个取值已经有一条了 ⇒ 置灰（重复的两条永远轮不到后一条）
            enabled = UsedCondition(kind, value) !in used,
            disabledHint = I18n.get(Strings.COMMAND_BRANCH_DUPLICATE_VALUE, "这个取值已经加过了"),
            selected = value == currentValue,
            onPick = { onPick(value) },
        )
    }

/**
 * 动作菜单：这一条命中后执行什么。
 *
 *  **与「添加命令」同一支底部抽屉**：同一张候选表、
 *  同一套「入口格进二级」「选择应用进挑应用页」的判据，只把「写回哪儿」交给这里
 *  （`applyBranchAction` 写第几条规则）。当前那一条动作用行首对勾 + 主题色标出来。
 */
@Composable
private fun SettingsPage.BranchCommandMenu(
    rules: List<StateRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    //  从「选择应用」页挑完回来时，这一页的组合是**新的一份** —— `menus.commandAt` 已经回到
    //  `-1`，靠页面级那一位把抽屉原样支起来，用户不用再点一次。
    LaunchedEffect(Unit) {
        picker.resumeSheetAt.value?.takeIf { it.page == Screen.BRANCHES }?.let { back ->
            picker.resumeSheetAt.value = null
            if (back.index in rules.indices) menus.commandAt = back.index
        }
    }
    val at = menus.commandAt // 这一趟改的是第几条（抽屉一关就归 -1，先取出来）
    val show = at in rules.indices
    StepPickerSheet(
        show = show,
        sheet =
            StepSheetSpec(
                title = I18n.get(Strings.COMMAND_STEP_REPLACE_TITLE, "选择命令"),
                current = rules.getOrNull(at)?.action,
                onOpenApp = {
                    //  压一页之前记下「回来要开哪一条」，抽屉本身**不关**
                    picker.resumeSheetAt.value = ResumeSheet(Screen.BRANCHES, at)
                    openAppPickerForBranch(at, forCommand = true)
                },
                //  这一支**只由「确定」调**（点行只换高亮）：确定那一刻才把动作写到这一条规则上
                onPick = { token ->
                    tick()
                    applyBranchAction(at, token)
                },
                onDismiss = { menus.commandAt = -1 },
            ),
    )
}

/**
 * 把某一档动作写到第 [index] 条规则上 —— 关菜单之前调用（关掉之后下标就取不到了）。
 */
private fun SettingsPage.applyBranchAction(
    index: Int,
    token: String,
) {
    val old = branchDraft.draft.rules.getOrNull(index) ?: return
    val parsed = Action.parse(token)
    if (parsed is Action.None) {
        //  认不出的 token（老配置 / 菜单给错了）不能静默落成「无操作」：那样保存时才报
        //  「每条条件都要选一个动作」，既指不出是哪一条、也看不出是解析失败。
        //  这里保留原来那一档不动，只留一行判据日志。
        XLog.w("条件执行：认不出的动作 token（第 ${index + 1} 条保持原样）：$token")
        return
    }
    branchDraft.draft.update(index, old.copy(action = parsed))
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
    //  `NEW_BRANCH` 是 -1，`getOrNull` 对它本来就给 null —— 不必再单判一次
    if (old == null) {
        branchDraft.draft.add(StateRule.single(gesture, condition, Action.None))
    } else {
        //  菜单编辑的是**第一条**条件（这一版一次只编辑一条）：只换那一个槽位，
        //  一块带好几条时不把其余几条抹掉
        branchDraft.draft.update(index, old.replacingCondition(0, condition))
    }
}
