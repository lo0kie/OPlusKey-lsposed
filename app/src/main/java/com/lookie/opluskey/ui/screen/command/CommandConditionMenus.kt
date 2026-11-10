package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.PickerOption
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.SinglePickerDialog
import com.lookie.opluskey.ui.appNameOf
import com.lookie.opluskey.ui.model.conditionKindLabel
import com.lookie.opluskey.ui.model.conditionMenuSummary
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.BranchDraft

// 条件那两张菜单 —— 一级选「种类」、二级选「那一档取值」，外加它们选完之后往哪儿落。
//
//  从 `CommandBranchMenus` 里分出来：那一张文件装的是「一块规则那行」的菜单（换条件 / 换命令 /
//  删除），而这两层是**点进来之后**的事，各自要维护「二级开着哪一档」「哪几档该灰掉」——
//  混在一起时那一个 composable 顶破 detekt 的长度与圈复杂度两条线。

/**
 * 条件菜单 —— 一张菜单里三组：**这一块已有的条件** / **种类** / 「再加一条」与「删除」。
 *
 * ## 一块可以有**好几条**条件（逐条都要成立）
 *
 *  数据层与判定层一直是支持的（`SceneRule.conditions` 是列表、`appliesTo` 是 `all`），
 *  缺的只是界面能指到「第几条」。所以菜单最上面多了那一组：每条一行，点哪条就在下面改哪条
 *  （`menus.condIndexAt`）。之前这里写死「一次只编辑第一条」，第二条往后既改不了也删不掉，
 *  配错了只能删整行重来。
 *
 * ## 只有一条时不出那一组
 *
 *  一条条件时那组只有一行、且它就是当前在改的那条 —— 多一行只是让菜单变长。
 *  按 `conditions.size > 1` 判，不是按「有没有条件」：新加的那一行（0 条）同样不该有那一组。
 *
 * ## 「再加一条」为什么还要再点一次种类
 *
 *  点它只是把 `condIndexAt` 置成 `-1`（下面那组的标题随之变成「再加一条条件」），接着点的
 *  种类才写进这块 —— 与「改某一条」走同一条落盘路径（[applyBranchCondition]），不另开逻辑。
 *
 * ## 最后一条不给删
 *
 *  删到 0 条等于「这条规则还没配完」（`isReady` 假、格子里显示未选择），而用户想表达的
 *  「不要这条规则」有行尾菜单里那个删除。所以删除只在 `conditions.size > 1` 时出现。
 *
 * ## 每一档各给一颗字形（[conditionIcon]）
 *
 *  共用一颗的话菜单整列长得一样，只有文字能分。当前那一档走 `selected`（行首对勾 + 主题色）：
 *  改条件时最想知道的是「现在是什么」，不该还要从这几行里自己找一遍。
 */
@Composable
internal fun SettingsPage.BranchConditionMenu(
    draft: BranchDraft,
    rules: List<SceneRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    val show = menus.condAt != AppPickerState.NOT_FOR_BRANCH
    val at = menus.condAt
    //  这一块现有的条件（空表 = 新加的那一行）
    val conditions = rules.getOrNull(at)?.conditions.orEmpty()
    //  二级在说哪一档；`null` = 没开
    var subKind by remember { mutableStateOf<ConditionKind?>(null) }
    //  一级的「改第几条」与二级的「开着哪一档」**都只跟着开合走**：菜单一关就都复位。
    //  一级那一半：打开前 `onCondition` 已经指了下标（点的是第几行），保持不动 —— 绝不能重置成 0，
    //  那样点第二行改的还是第一条；上一趟的下标越界 / 首次打开时回到「改第一条」，0 条时是「追加」。
    LaunchedEffect(show) {
        //  **`subKind` 不跟着菜单关**：选完种类时菜单会收起来（让位给取值弹窗），
        //  在这里清掉等于把刚弹出来的弹窗一起关掉。它由弹窗自己收（见 `ctx.onDismiss`）。
        if (!show) {
            return@LaunchedEffect
        }
        if (menus.condIndexAt !in conditions.indices) {
            menus.condIndexAt = if (conditions.isEmpty()) -1 else 0
        }
    }
    //  下标收敛：删掉一条之后 `condIndexAt` 可能越界，这里夹一次比在删除那边算更少出错
    val condIndex = menus.condIndexAt.coerceIn(-1, conditions.lastIndex.coerceAtLeast(-1))
    val editing = conditions.getOrNull(condIndex)
    val avail = conditionAvailability(rules, at, conditions, condIndex)
    //  **这一趟写哪一块、第几条，在点种类那一刻快照**：两档取值走的是「先关一级菜单、再弹取值窗」，
    //  关菜单那一下会把 `condAt` / `condIndexAt` 清成哨兵值；等取值回来再读 `menus` 就只能读到
    //  「关着」，于是改条件变成追加一条。快照值随 `ConditionPick` 一路带下去。
    var pickAt by remember { mutableIntStateOf(AppPickerState.NOT_FOR_BRANCH) }
    var pickSlot by remember { mutableIntStateOf(-1) }
    //  二级弹窗要的「当前取值」与「已被别的条件占掉的档」也在这里**一起快照**：
    //  紧接着 `closeConditionMenu()` 会把 `condAt` 清成哨兵值，上面那三个派生值
    //  （`conditions` / `editing` / `avail`）就全部按「关着」重算了 —— 弹窗里「当前取值」
    //  不再打勾，而且它自己被算进 `used` 而置灰：改回原值改不回去，别的档占满时整列全灰。
    var pickedCurrent by remember { mutableStateOf("") }
    var pickedUsed by remember { mutableStateOf<Set<UsedCondition>>(emptySet()) }
    //  选中种类之后怎么落盘 —— 三个去处都在 [commitCondition] 里（挑应用页 / 时间窗 / 直接写）。
    //  顺带把二级收掉：一级点「前台应用 / 时间区间」时二级本来就还没开。
    val commit: (ConditionKind, String) -> Unit = { kind, value ->
        commitCondition(
            ConditionPick(kind, value, pickAt, pickSlot, resetSub = { subKind = null }),
            draft,
            menus,
            tick,
        )
    }

    PageMenu(
        entries =
            if (!show) {
                emptyList()
            } else {
                buildList {
                    //  **没有「这一块的条件」那一组**：一块里的几条条件已经在页面上各占一行了，
                    //  菜单里再列一遍只是把同一份东西摆两处，还得让用户在两处之间对号。
                    //  点哪一行就编哪一行，菜单只回答「这一条是什么」。
                    add(
                        MenuEntry.Section(
                            title =
                                if (condIndex < 0) {
                                    I18n.get(Strings.COMMAND_BRANCH_ADD_CONDITION, "添加条件")
                                } else {
                                    I18n.format(Strings.COMMAND_BRANCH_CONDITION_INDEX, condIndex + 1)
                                },
                            rows =
                                conditionKindRows(
                                    current = editing?.kind,
                                    //  选中那一行的子标题写**这一条现在的取值**（「打开」/「22:00-07:00」）；
                                    //  「前台应用」换成个数（「已选择 3 个应用」）—— 名字全列出来比标题还长。
                                    //  取值没选（刚加的空条件）就是 null，那一行不出小字。
                                    currentSummary = conditionSummaryOf(editing),
                                    disabled = avail.disabled,
                                    //  直接落盘那一档（「前台应用 / 时间区间」）：点下去时菜单还开着，
                                    //  先把落点快照下来再交给 `commitCondition`。
                                    onPick = { kind, value ->
                                        pickAt = at
                                        pickSlot = condIndex
                                        commit(kind, value)
                                    },
                                ) { kind ->
                                    //  弹窗要出来了，菜单让位（用户口径「选择器出现时关掉菜单」）
                                    //  —— 所以落点必须在关之前快照。
                                    pickAt = at
                                    pickSlot = condIndex
                                    pickedCurrent = editing?.takeIf { it.kind == kind }?.effectiveValue.orEmpty()
                                    pickedUsed = avail.used
                                    subKind = kind
                                    menus.closeConditionMenu()
                                },
                        ),
                    )
                    conditionDeleteRow(conditions, condIndex, at, menus)?.let(::add)
                }
            },
        preciseClick = menus.condAnchor,
        // 二级开着时这一层不接关闭请求 —— 否则同一毫秒又把它收回去，菜单打不开。
        onDismiss = { menus.closeConditionMenu() },
    )

    // 二级：这一档的取值（一级选中的种类 + 这里选的档位 = 一条完整条件）
    //  当前这一档的取值也标出来（老配置里 WIFI / 媒体没有取值 ⇒ 按默认档高亮）
    //  **退场要播完**：`subKind` 一变 null 就把这一支从组合里摘掉的话，弹窗是**直接消失**的
    //  （没有收起动画）。所以记住最后那一档、`show` 单独给 —— 与系统开关那三档同一个做法。
    var lastKind by remember { mutableStateOf<ConditionKind?>(null) }
    subKind?.let { lastKind = it }
    val shownKind = subKind ?: lastKind
    shownKind?.let { kind ->
        //  取值与「已占用」用点种类那一刻的快照，不用此刻重算的 `editing` / `avail`（见 [pickedCurrent]）
        BranchValuePicker(
            show = subKind != null,
            ctx = BranchValueCtx(kind, pickedCurrent, pickedUsed, { v -> commit(kind, v) }, { subKind = null }),
        )
    }
}

/**
 * 一级里选中那一行的**子标题** —— 这一条现在的取值（`打开` / `22:00-07:00` /
 * `已选择 3 个应用`）；还没选就是 `null`（那一行不出小字）。
 *
 *  抽成一支只为让 [BranchConditionMenu] 收在 detekt 的 `LongMethod` 长度线内 —— 那一格是
 *  三层嵌套的 `let`，写在里面会把整段的节奏打断。
 */
private fun SettingsPage.conditionSummaryOf(editing: BranchCondition?): String? =
    editing?.let { condition -> condition.kind?.let { kind -> conditionMenuSummary(kind, condition) { appNameOf(it) } } }

/**
 * 二级：这一档的取值 —— **选择器弹窗**（用户口径：不用第二层菜单来选）。
 *
 *  壳用 [SinglePickerDialog] 那一份（与系统开关三档、语言、组合键同一套）：取值这一列每行
 *  自带一颗字形、用过的档置灰 —— 这两样是 [PickerOption] 带来的，壳本身只有一份。
 *
 * @param ctx 这一层要的那五样（见 [BranchValueCtx]）
 */
@Composable
private fun SettingsPage.BranchValuePicker(
    show: Boolean,
    ctx: BranchValueCtx,
) {
    val rows = conditionChoiceRows(ctx.kind, ctx.currentValue, ctx.used, ctx.onPick)
    SinglePickerDialog(
        show = show,
        title = conditionKindLabel(ctx.kind),
        options = rows.map { row -> PickerOption(label = row.title, icon = row.icon, enabled = row.enabled) },
        selectedIndex = rows.indexOfFirst { it.selected },
        onDismiss = ctx.onDismiss,
        onPick = { index -> rows.getOrNull(index)?.onPick?.invoke() },
    )
}

/**
 * [BranchValueMenu] 要的那五样 —— 打成一个对象是因为 detekt 的参数上限只有 5 个，
 * 而这五样本来就是「这一层要说的一件事」（口径与 `StepSheetCtx` 那一套相同）。
 *
 * @param kind 一级里刚点的那一档
 * @param currentValue 这一条**现在**的取值（老配置里可能没有 ⇒ 空串，二级按默认档高亮）
 * @param used 表里已经用掉的「条件 + 取值」（不含正在改的这一条，见 [usedConditionsOf]）
 * @param onPick 选中某一档取值之后怎么落盘
 * @param onDismiss 收掉二级（一级那一层不动）
 */
private class BranchValueCtx(
    val kind: ConditionKind,
    val currentValue: String,
    val used: Set<UsedCondition>,
    val onPick: (String) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * 一级里头**哪几档该灰掉** + 二级那一层要用的「表里已经用掉的取值」。
 *
 *  两条规矩各管一头，但都要先看「这一块现有的条件」，所以一起算：
 *
 *  · **同一块里同一个「父级条件」只许有一条** —— 只有「时间区间」除外（不同时段各一条，
 *    各自是不同的情况）。其余档一条就够，想换就改那一条，加第二条只会让用户多一行要看的、
 *    结果却不变。「前台应用」现在也在这条规矩里：它**本身就能多选**（`包1||包2`，任一在前台
 *    即通过），再开第二行应用条件等于把「且」和「或」两种关系混在同一块里，读不出到底怎么判。
 *    跨块不禁：不同规则行各要一条「亮屏」是合理的，那由下面 `exhausted` 那套去管。
 *  · **这一档的每一种取值都用过了**（跨整张表）：屏幕状态有亮屏 / 锁屏两档，别的行加过
 *    「亮屏」之后还想加「锁屏」，那仍是两条不同的条件，不该在一级就灰掉整档 —— 真正不能重复的
 *    「同一条」在二级里挡（见 [conditionChoiceRows]）。**同一块里的其余几条也算「用过」**
 *    （只跳过正在改的这一条）。
 */
private fun conditionAvailability(
    rules: List<SceneRule>,
    at: Int,
    conditions: List<BranchCondition>,
    condIndex: Int,
): ConditionAvailability {
    val blockKinds = blockKindsOf(conditions, condIndex)
    val used = usedConditionsOf(rules, skipAt = at, skipCondition = condIndex)
    val disabled = ConditionKind.entries.filter { it in blockKinds || it.exhausted(used) }.toSet()
    return ConditionAvailability(used, disabled)
}

/**
 * [conditionAvailability] 的两个答案：表里已用掉的取值、一级里该灰掉的档。
 *
 *  两个值一起返回而不是各算一次：它们出自同一笔「这一块现有的条件」，分两处算会有一天只改一边。
 */
private class ConditionAvailability(
    val used: Set<UsedCondition>,
    val disabled: Set<ConditionKind>,
)

/**
 * 条件定下来之后**往哪儿走** —— 三个去处：挑应用页 / 时间窗 / 直接写进这一块。
 *
 *  与「菜单怎么摆」无关的一支：它只回答「这一次选择落到哪里」。
 *
 * @param pick 选了哪一档、什么取值，以及「顺手把二级收掉」那一下（一级点「前台应用 /
 *   时间区间」时二级本来就还没开，收一下也没副作用）
 */
private fun SettingsPage.commitCondition(
    pick: ConditionPick,
    draft: BranchDraft,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    //  **落点用 `pick` 带下来的快照**，不读 `menus`：两档取值这条路在菜单关掉之后才回来，
    //  那时 `condAt` / `condIndexAt` 已经是哨兵值（读它就变成「追加一条」）。
    val ruleAt = pick.at
    val slot = pick.slot
    menus.condAt = AppPickerState.NOT_FOR_BRANCH
    menus.condIndexAt = -1
    pick.resetSub()
    when (pick.kind) {
        // 「前台应用」先去挑是哪个应用（进另一页），槽位一起带过去：挑完要写回这一条
        ConditionKind.APP -> {
            openAppPickerForBranch(ruleAt, conditionIndex = slot)
        }

        // 「时间区间」没有候选可以列（一天里任选一段），交给时间选择窗
        ConditionKind.TIME -> {
            menus.timeAt = ruleAt
            menus.timeCondIndex = slot
        }

        else -> {
            tick()
            applyBranchCondition(draft, ruleAt, slot, BranchCondition.of(pick.kind, pick.value), menus)
        }
    }
}

/**
 * [commitCondition] 那一件事要的五样 —— 打成一个对象只为让参数个数留在 detekt 之下
 * （口径与 `StepSheetCtx` 那一套相同）。
 *
 * @param at 落在**哪一块**（`AppPickerState.NEW_BRANCH` = 新加一行）；点种类那一刻的快照
 * @param slot 落在那一块里的**第几条条件**（`-1` = 再添一条）；同上
 * @param resetSub 收掉二级取值层（见 [commitCondition] 的说明）
 */
private class ConditionPick(
    val kind: ConditionKind,
    val value: String,
    val at: Int,
    val slot: Int,
    val resetSub: () -> Unit,
)
