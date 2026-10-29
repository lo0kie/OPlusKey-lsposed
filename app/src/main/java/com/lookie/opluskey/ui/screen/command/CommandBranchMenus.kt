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
import com.lookie.opluskey.ui.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.MenuEntry
import com.lookie.opluskey.ui.PageMenu
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hint
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.conditionIcon
import com.lookie.opluskey.ui.model.conditionKindLabel
import com.lookie.opluskey.ui.screen.openVarOpsForBranch
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.ResumeSheet
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowDown
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.Plus
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
 * 行尾那颗「更多」菜单 —— 再加一条条件 / 上移 / 下移 / 删除。
 *
 *  两列各点各的（换条件 / 换动作），这一颗管**这一行本身**：顺序就是判定顺序，
 *  所以要能挪；删也放在这里，不与换值混在一起。
 *
 *  「再加一条条件」也归这一颗而不是条件菜单：它说的是**这一行里多一条条件**（一块可以带好几条，
 *  逐条都要成立），而条件菜单只管「当前那一条是什么」。
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
                //  「添加条件」—— 一块可以带**好几条**条件（逐条都要成立），所以「再加一条」
                //  是这一行的操作，和上移 / 下移 / 删除一起放在这里。
                //  它单占第一段（下面用加深带断开）：那三件是「这一行本身怎么摆」，这一件是
                //  「这一行里的条件再多一条」，两类不同。
                //
                //  **点了直接插一条空条件，不弹菜单**：用户要的是「先占个位」。空条件在执行侧
                //  永不成立（`BranchCondition.isUsable` 假），而 `isReady` 为假会拦住保存 ——
                //  正好是「还没配完」该有的样子；页面上那一行显示「未选择条件」，点它才开菜单。
                //
                //  **这一行还没有可用的条件**就不再插：末尾那条还是空的，或者**一条都还没有**
                //  （那种时候页面上那一行「未选择条件」是**占位**，不是真条件 —— 只看
                //  `lastOrNull()` 的话它返回 null、判不出「已经有待配的那一行」，于是照样插进去，
                //  变成两行「未选择条件」，用户还得分清哪行是刚加的）。
                //  两种情况都给同一句提示，把注意力推回那一行。
                add(addConditionRow(at, menus))
                //  停用 / 启用紧跟在「添加场景」下面（同属「这一行现在怎么样」）——
                //  行本体在 `CommandBranchParts.kt`：这一支的行数已经到了 detekt 的上限。
                add(branchToggleRow(rules, at, menus))
                add(
                    MenuEntry.Row(
                        title = I18n.get(Strings.COMMON_MOVE_UP, "上移"),
                        icon = TablerIcons.Outline.ArrowUp,
                        enabled = at > 0,
                        //  禁用的行必须自带说法：不给就会落到整页那条（讲的是模块/宿主链路）
                        disabledHint = I18n.get(Strings.COMMAND_BRANCH_TOP_REACHED, "已经在最上面"),
                        dividerBefore = true,
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
                        disabledHint = I18n.get(Strings.COMMAND_BRANCH_BOTTOM_REACHED, "已经在最下面"),
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
    BranchConditionMenu(rules = rules, menus = menus, tick = tick)

    //  时间区间那个选择窗 —— 独立弹窗，不挂在菜单上（菜单在点它的时候已经关掉了）。
    //  初值取**这一条**现在的区间：改时间时看到的是当前值，而不是又跳回默认。
    val timeAt = menus.timeAt
    BranchTimeRangeDialog(
        show = timeAt != AppPickerState.NOT_FOR_BRANCH,
        initial =
            rules
                .getOrNull(timeAt)
                ?.conditions
                ?.getOrNull(menus.timeCondIndex)
                ?.takeIf { it.kind == ConditionKind.TIME }
                ?.value
                .orEmpty(),
        onDismiss = { menus.timeAt = AppPickerState.NOT_FOR_BRANCH },
        onConfirm = { range ->
            tick()
            val condIndex = menus.timeCondIndex
            menus.timeAt = AppPickerState.NOT_FOR_BRANCH
            //  **时间区间不许重叠**：同一档手势下两条区间一重叠，先命中的那条永远赢、后一条永远
            //  轮不到，而界面上两行都好好列着。选完就拦（与「同一个应用只能当一次条件」同一套），
            //  窗已经关了 —— 提示里说清楚，让用户回去改。
            if (branchTimeOverlaps(range, timeAt, condIndex)) {
                hint(I18n.get(Strings.COMMAND_COND_TIME_OVERLAP, "时间区间不允许重叠"))
            } else {
                applyBranchCondition(rules, timeAt, condIndex, BranchCondition.of(ConditionKind.TIME, range), menus)
            }
        },
    )
}

/**
 * 条件菜单 —— 一张菜单里三组：**这一块已有的条件** / **种类** / 「再加一条」与「删除」。
 *
 * ## 一块可以有**好几条**条件（逐条都要成立）
 *
 *  数据层与判定层一直是支持的（`StateRule.conditions` 是列表、`appliesTo` 是 `all`），
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
private fun SettingsPage.BranchConditionMenu(
    rules: List<StateRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    val show = menus.condAt != AppPickerState.NOT_FOR_BRANCH
    val at = menus.condAt
    //  这一块现有的条件（空表 = 新加的那一行）
    val conditions = rules.getOrNull(at)?.conditions.orEmpty()
    //  打开条件菜单时「改哪一条」怎么定：打开前 `onCondition` **已经指了下标**（点的是第几行），
    //  保持不动 —— 绝不能重置成 0，那样点第二行改的还是第一条。
    //  上一趟的下标越界 / 首次打开时回到「改第一条」，0 条时是「追加」。
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        if (menus.condIndexAt !in conditions.indices) {
            menus.condIndexAt = if (conditions.isEmpty()) -1 else 0
        }
    }
    //  下标收敛：删掉一条之后 `condIndexAt` 可能越界，这里夹一次比在删除那边算更少出错
    val condIndex = menus.condIndexAt.coerceIn(-1, conditions.lastIndex.coerceAtLeast(-1))
    val editing = conditions.getOrNull(condIndex)
    //  **同一块里同一个「父级条件」只许有一条** —— 只有「时间区间」除外（不同时段各一条，
    //  各自是不同的情况）。其余档一条就够，想换就改那一条，加第二条只会让用户多一行要看的、
    //  结果却不变。
    //  「前台应用」现在也在这条规矩里：它**本身就能多选**（`包1||包2`，任一在前台即通过），
    //  再开第二行应用条件等于把「且」和「或」两种关系混在同一块里，读不出到底怎么判。
    //  跨块不禁：不同规则行各要一条「亮屏」是合理的，那由下面 `exhausted` 那套去管。
    val blockKinds = blockKindsOf(conditions, condIndex)
    //  另一套是「**这一档的每一种取值都用过了**」（跨整张表）：屏幕状态有亮屏 / 锁屏两档，
    //  别的行加过「亮屏」之后还想加「锁屏」，那仍是两条不同的条件，不该在一级就灰掉整档。
    //  真正不能重复的「同一条」在二级里挡（见 [conditionChoiceRows]）。
    //  **同一块里的其余几条也算「用过」**（只跳过正在改的这一条）。
    val used = usedConditionsOf(rules, skipAt = at, skipCondition = condIndex)
    val disabled = ConditionKind.entries.filter { it in blockKinds || it.exhausted(used) }.toSet()
    //  二级在说哪一档；`null` = 没开
    var subKind by remember { mutableStateOf<ConditionKind?>(null) }
    LaunchedEffect(show) { if (!show) subKind = null }
    //  二级挂在**一级菜单里的触点**上（触发点在菜单里，没有卡片触点可用）
    val panelTouch = remember { PreciseClickState() }

    /** 选中种类之后怎么落盘 —— 三个去处（挑应用页 / 时间窗 / 直接写）。 */
    fun commit(
        kind: ConditionKind,
        value: String,
    ) {
        val ruleAt = menus.condAt
        val slot = menus.condIndexAt
        menus.condAt = AppPickerState.NOT_FOR_BRANCH
        subKind = null
        when (kind) {
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
                applyBranchCondition(rules, ruleAt, slot, BranchCondition.of(kind, value), menus)
            }
        }
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
                                    I18n.get(Strings.COMMAND_BRANCH_ADD_CONDITION, "添加场景")
                                } else {
                                    I18n.format(Strings.COMMAND_BRANCH_CONDITION_INDEX, condIndex + 1)
                                },
                            rows = conditionKindRows(editing?.kind, disabled, onPick = ::commit) { subKind = it },
                        ),
                    )
                    conditionDeleteRow(conditions, condIndex, at, menus)?.let(::add)
                }
            },
        preciseClick = menus.condAnchor,
        columnAnchor = panelTouch,
        // 二级开着时这一层不接关闭请求 —— 否则同一毫秒又把它收回去，菜单打不开。
        onDismiss = { if (subKind == null) menus.closeConditionMenu() },
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
                            rows = conditionChoiceRows(kind, currentValue, used) { value -> commit(kind, value) },
                        ),
                    )
                }.orEmpty(),
        preciseClick = panelTouch,
        onDismiss = { subKind = null },
        dim = true,
    )
}

/**
 * 「添加条件」那一行 —— 点了**直接插一条空条件**，不弹菜单。
 *
 *  抽成一支只为让 [BranchRowMenu] 留在 detekt 的长度上限内；判据见那边那段注释：
 *  用户要的是「先占个位」，空条件在执行侧永不成立、会拦住保存，正好是「还没配完」的样子。
 *  这一行**还没有可用的条件**时不再插，只给一句提示把注意力推回那一行。
 */
private fun SettingsPage.addConditionRow(
    at: Int,
    menus: BranchMenuState,
): MenuEntry.Row =
    MenuEntry.Row(
        title = I18n.get(Strings.COMMAND_BRANCH_ADD_CONDITION, "添加场景"),
        icon = TablerIcons.Outline.Plus,
        onPick = {
            menus.menuAt = -1
            val old = branchDraft.draft.rules.getOrNull(at) ?: return@Row
            //  判据是「**还没有可用的条件**」，不是「最后一条是空的」：条件列表为空时页面
            //  同样已经摆了一行「未选择条件」占位，那也是「有待配的一行」。
            val pending = old.conditions.isEmpty() || old.conditions.lastOrNull()?.isUsable == false
            if (pending) {
                //  与其它「为什么这一步做不了」共用同一个位
                hint(
                    I18n.get(Strings.COMMAND_BRANCH_CONDITION_PENDING, "有场景还没选"),
                    slot = HINT_SLOT_DISABLED,
                )
                return@Row
            }
            val next = old.addingCondition(BranchCondition(""))
            branchDraft.draft.update(at, next)
            //  刚插的那一条先收起再展开（见 `BranchMenuState.freshCond`）
            menus.freshCond = CondRef(at, next.conditions.lastIndex)
        },
    )

/**
 * 「删除这一条条件」那一行（**最后一条不给删**：删到 0 条等于「这条规则还没配完」，
 * 而「不要这条规则」有行尾菜单里那个删除）。
 *
 *  抽成一支只为把 [BranchConditionMenu] 压回 `LongMethod` 的阈值。
 *
 * @return 不该出现这一行时 `null`
 */
private fun SettingsPage.conditionDeleteRow(
    conditions: List<BranchCondition>,
    condIndex: Int,
    at: Int,
    menus: BranchMenuState,
): MenuEntry.Row? {
    if (conditions.size <= 1 || condIndex < 0) return null
    return MenuEntry.Row(
        title = I18n.get(Strings.COMMON_DELETE, "删除"),
        icon = PageIcons.CommandDelete,
        danger = true,
        dividerBefore = true,
        onPick = {
            //  **先问一句**：删一条条件不可逆，而「删除」这一行就在「换这一条」的正下方，
            //  点歪一下少一条条件、判定结果当场变了。确认窗见 `BranchConditionDeleteDialog`。
            //
            //  菜单同时关掉，不把下标挪到前一条继续开着：用户点的是「删掉这一条」，
            //  再自动支起另一条的条件菜单等于替他又开了一次编辑器。
            menus.deletingCond = CondRef(at, condIndex)
            menus.closeConditionMenu()
        },
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
                //  两行关卡同上（见 `CommandSteps`）：压页之前记下「回来要开哪一条」，抽屉本身**不关**。
                //  挑成了由 `commitVarOp` / `onAppPicked` 清掉这笔账，直接返回时它才会兑现。
                onOpenPicker = { page ->
                    //  压页之前记下「回来要开哪一条」，抽屉本身**不关**：挑成了由
                    //  `commitStep` / `onAppPicked` 清掉这笔账，直接返回时它才会兑现。
                    picker.resumeSheetAt.value = ResumeSheet(Screen.BRANCHES, at)
                    when (page) {
                        StepPickerPage.App -> openAppPickerForBranch(at, forCommand = true)
                        StepPickerPage.Vars -> openVarOpsForBranch(at)
                    }
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
        XLog.w("系统场景：认不出的动作 token（第 ${index + 1} 条保持原样）：$token")
        return
    }
    branchDraft.draft.update(index, old.copy(action = parsed))
}

/**
 * 把一条条件写到第 [index] 条规则上（[AppPickerState.NEW_BRANCH] = 新加一条）。
 *
 *  @param condIndex 写进**这块里的第几条条件**：`>= 0` = 换那一条，越界 / `-1` = 追加一条。
 *    一块可以带好几条条件（逐条都要成立），所以「写到哪一条」必须由调用方明确指出来 ——
 *    这里不替它猜（`replacingCondition` 对越界会退回改第 0 条，那会把用户已有的第一条换掉）。
 *
 *  新加的那一条动作留空（[Action.None]）：条件与动作是同一行的两半，选完条件再去选动作
 *  比「先逼用户挑一条命令」顺。
 */
private fun SettingsPage.applyBranchCondition(
    rules: List<StateRule>,
    index: Int,
    condIndex: Int,
    condition: BranchCondition,
    /** 追加时要把「刚插的是第几条」告诉界面（它要先收起再展开，见 `BranchMenuState.freshCond`）。 */
    menus: BranchMenuState,
) {
    val gesture = branchGestureNow()
    val old = rules.getOrNull(index)
    //  `NEW_BRANCH` 是 -1，`getOrNull` 对它本来就给 null —— 不必再单判一次
    if (old == null) {
        branchDraft.draft.add(StateRule.single(gesture, condition, Action.None))
    } else if (condIndex in old.conditions.indices) {
        //  改第 condIndex 条：只换那一个槽位，其余几条原样留着
        branchDraft.draft.update(index, old.replacingCondition(condIndex, condition))
    } else {
        //  追加一条（「再加一条条件」进来的）。不走 `replacingCondition`：那支越界会退回改第 0 条，
        //  等于把用户已有的第一条悄悄换掉 —— 多条件里这是最不能接受的一种「猜」。
        val next = old.addingCondition(condition)
        branchDraft.draft.update(index, next)
        //  记下「刚插的是第几条」：界面据此让这一行先以不可见组合一帧、再展开
        menus.freshCond = CondRef(index, next.conditions.lastIndex)
    }
}

/**
 * 删掉这一块的第 [condIndex] 条条件。
 *
 *  **最后一条不删**（调用方也只在两条以上时才给入口）：删到 0 条等于「这条规则还没配完」，
 * 而用户想表达的「不要这条规则」有行尾菜单里那个删除。这里再兜一次，免得以后多一个入口忘了。
 */
internal fun SettingsPage.deleteBranchCondition(
    index: Int,
    condIndex: Int,
) {
    val old = branchDraft.draft.rules.getOrNull(index) ?: return
    if (old.conditions.size <= 1) return
    if (condIndex !in old.conditions.indices) return
    branchDraft.draft.update(index, old.withoutCondition(condIndex))
    XLog.w("系统场景：第 ${index + 1} 条规则删掉第 ${condIndex + 1} 条场景（剩 ${old.conditions.size - 1} 条）")
}
