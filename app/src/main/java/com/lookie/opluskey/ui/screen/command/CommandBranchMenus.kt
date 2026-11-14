package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.BranchCondition
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.feedback.HINT_SLOT_DISABLED
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.state.binding.BranchDraft
import com.lookie.opluskey.ui.state.picker.AppPickerState
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowDown
import io.github.lyxnx.compose.ui.tablericons.outline.ArrowUp
import io.github.lyxnx.compose.ui.tablericons.outline.Plus

// 分叉那几张菜单 —— 第一层（行本身：加条件 / 停用 / 上移 / 下移 / 删除）、条件种类菜单（每种一行）、
// 取值二级。
//
//  **动作那一档不再有菜单**：点动作行进「场景动作」页编那一档（与「按键绑定」页同一张网格、
//  同一条三档手势带），所以这里没有「选命令」那一张了。

/**
 * 两张菜单的装配 —— 一行分两列，各点各的：
 *
 * ```
 * 左列（条件）→ 条件种类菜单 ─┬─ 前台应用 → 进「选择应用」页
 *                             ├─ 其余每一档 → 取值（二级）
 *                             └─ 上移 / 下移 / 删除
 * 右列（动作）→ **换页**（`Screen.SCENE_BINDING`），不弹菜单
 * ```
 */
@Composable
internal fun SettingsPage.BranchMenus(
    /** **这一页**那一份草稿 —— 这一页只有一页，改的就是它的表（见 `BranchScreen`）。 */
    draft: BranchDraft,
    rules: List<SceneRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    BranchConditionFlow(draft = draft, rules = rules, menus = menus, tick = tick)
    BranchRowMenu(draft = draft, rules = rules, menus = menus)
}

/**
 * 行尾那颗「更多」菜单 —— 再加一条条件 / 上移 / 下移 / 删除。
 *
 *  两列各点各的（换条件 / 换动作），这一颗管**这一行本身**：顺序就是判定顺序，
 *  所以要能挪；删也放在这里，不与换值混在一起。
 *
 *  「再加一条条件」也归这一颗而不是条件菜单：它说的是**这一行里多一条条件**（一块可以带好几条，
 *  同种类之间「或」、不同种类之间「且」），而条件菜单只管「当前那一条是什么」。
 */
@Composable
private fun SettingsPage.BranchRowMenu(
    draft: BranchDraft,
    rules: List<SceneRule>,
    menus: BranchMenuState,
) {
    val at = menus.menuAt
    PageMenu(
        entries =
            buildList {
                //  「添加条件」—— 一块可以带**好几条**条件（同种类之间「或」），所以「再加一条」
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
                add(addConditionRow(draft, at, menus))
                //  停用 / 启用紧跟在「添加场景」下面（同属「这一行现在怎么样」）——
                //  行本体在 `CommandBranches.kt`：这边只摆菜单，那边摆行。
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
                        draft.move(at, at - 1)
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
                        draft.move(at, at + 1)
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
    draft: BranchDraft,
    rules: List<SceneRule>,
    menus: BranchMenuState,
    tick: () -> Unit,
) {
    BranchConditionMenu(draft = draft, rules = rules, menus = menus, tick = tick)

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
                applyBranchCondition(draft, timeAt, condIndex, BranchCondition.of(ConditionKind.TIME, range), menus)
            }
        },
    )
}

/**
 * 「添加条件」那一行 —— 点了**直接插一条空条件**，不弹菜单。
 *
 *  抽成一支；判据见那边那段注释：
 *  用户要的是「先占个位」，空条件在执行侧永不成立、会拦住保存，正好是「还没配完」的样子。
 *  这一行**还没有可用的条件**时不再插，只给一句提示把注意力推回那一行。
 */
private fun SettingsPage.addConditionRow(
    draft: BranchDraft,
    at: Int,
    menus: BranchMenuState,
): MenuEntry.Row =
    MenuEntry.Row(
        title = I18n.get(Strings.COMMAND_BRANCH_ADD_CONDITION, "添加场景"),
        icon = TablerIcons.Outline.Plus,
        onPick = { appendCondition(draft, at, menus) },
    )

/**
 * 「这一块再加一条条件」—— 菜单里那一行与**卡片末尾那一行**共用这一支（权威实现只有这份）。
 *
 *  两张入口说的是同一件事：「这一块的场景再多一条」。放在卡片末尾是用户口径
 *  （不必点「更多」再找，列表滚到哪儿就能就地加）。
 *
 *  判据是「**还没有可用的条件**」，不是「最后一条是空的」：条件列表为空时页面同样已经摆了
 *  一行「未选择条件」占位，那也是「有待配的一行」—— 再插一条就变成两行都空，用户还得分清
 *  哪行是刚加的。
 *
 *  空条件在执行侧永不成立（`BranchCondition.isUsable` 假），`isReady` 为假会拦住保存 ——
 *  正好是「还没配完」该有的样子；页面上那一行显示「未选择条件」，点它才开菜单。
 */
internal fun SettingsPage.appendCondition(
    draft: BranchDraft,
    at: Int,
    menus: BranchMenuState,
) {
    menus.menuAt = -1
    //  整页闸门（主开关 / 链路 / 只读）与行上那颗同一个判据：只盖行、不盖这里的话，
    //  只读态下还能往块里塞一条空条件（见 `branchWritableHint`）。
    branchWritableHint()?.let { reason ->
        hint(reason, slot = HINT_SLOT_DISABLED)
        return
    }
    val old = draft.scenes.getOrNull(at) ?: return
    //  **一块到顶了**：九条之后不再加（`SceneRule.MAX_CONDITIONS`）。卡片末尾那一行到这一档
    //  自己就置灰了，这里再拦一道 —— 菜单里那一行与手改配置都不该越过它。
    if (old.conditions.size >= SceneRule.MAX_CONDITIONS) {
        hint(
            I18n.format(Strings.COMMAND_BRANCH_CONDITION_LIMIT, SceneRule.MAX_CONDITIONS),
            slot = HINT_SLOT_DISABLED,
        )
        return
    }
    val pending = old.conditions.isEmpty() || old.conditions.lastOrNull()?.isUsable == false
    if (pending) {
        //  与其它「为什么这一步做不了」共用同一个位
        hint(
            I18n.get(Strings.COMMAND_BRANCH_CONDITION_PENDING, "有场景尚未选择"),
            slot = HINT_SLOT_DISABLED,
        )
        return
    }
    val next = old.addingCondition(BranchCondition(""))
    draft.update(at, next)
    //  刚插的那一条先收起再展开（见 `BranchMenuState.freshCond`）
    menus.freshCond = CondRef(at, next.conditions.lastIndex)
}

/**
 * 「删除这一条条件」那一行（**最后一条不给删**：删到 0 条等于「这条规则还没配完」，
 * 而「不要这条规则」有行尾菜单里那个删除）。
 *
 *  抽成一支只为把 [BranchConditionMenu] 压回 `LongMethod` 的阈值。
 *
 * @return 不该出现这一行时 `null`
 */
internal fun SettingsPage.conditionDeleteRow(
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
 * 把一条条件写到第 [index] 条规则上（[AppPickerState.NEW_BRANCH] = 新加一条）。
 *
 *  @param condIndex 写进**这块里的第几条条件**：`>= 0` = 换那一条，越界 / `-1` = 追加一条。
 *    一块可以带好几条条件（同种类之间「或」），所以「写到哪一条」必须由调用方明确指出来 ——
 *    这里不替它猜（`replacingCondition` 对越界会退回改第 0 条，那会把用户已有的第一条换掉）。
 *
 *  新加的那一条三档动作都是「跟随默认命令」（`SceneRule` 的默认值）：条件与动作是同一个场景的
 *  两半，**先配条件**、动作进「场景动作」页慢慢挑 —— 这一页不该逼用户先挑一条命令。
 */
internal fun SettingsPage.applyBranchCondition(
    draft: BranchDraft,
    index: Int,
    condIndex: Int,
    condition: BranchCondition,
    /** 追加时要把「刚插的是第几条」告诉界面（它要先收起再展开，见 `BranchMenuState.freshCond`）。 */
    menus: BranchMenuState,
) {
    //  这张表就是这一页那一份草稿的表（调用点传进来的 `rules` 与它同一个对象）
    val old = draft.scenes.getOrNull(index)
    //  `NEW_BRANCH` 是 -1，`getOrNull` 对它本来就给 null —— 不必再单判一次
    if (old == null) {
        draft.add(SceneRule(conditions = listOf(condition)))
    } else if (condIndex in old.conditions.indices) {
        //  改第 condIndex 条：只换那一个槽位，其余几条原样留着
        draft.update(index, old.replacingCondition(condIndex, condition))
    } else {
        //  追加一条（「再加一条条件」进来的）。不走 `replacingCondition`：那支越界会退回改第 0 条，
        //  等于把用户已有的第一条悄悄换掉 —— 多条件里这是最不能接受的一种「猜」。
        val next = old.addingCondition(condition)
        draft.update(index, next)
        //  **这一块原来一条条件都没有**时，这一下不是「往表里插一行」，而是把占位行
        //  「未选择场景」原地换成它 —— 不设 `freshCond`，那一行就不播「先收起再展开」的插入动画，
        //  只做内容淡换（真机上先收起再展开的表现是「占位行瞬间消失、高度塌掉，新行再滑进来」）。
        //  已有条件时才是真的插入，照旧让新行从 0 高展开。
        if (old.conditions.isNotEmpty()) {
            menus.freshCond = CondRef(index, next.conditions.lastIndex)
        }
    }
}

/**
 * 删掉这一块的第 [condIndex] 条条件。
 *
 *  **最后一条不删**（调用方也只在两条以上时才给入口）：删到 0 条等于「这条规则还没配完」，
 * 而用户想表达的「不要这条规则」有行尾菜单里那个删除。这里再兜一次，免得以后多一个入口忘了。
 */
internal fun SettingsPage.deleteBranchCondition(
    draft: BranchDraft,
    index: Int,
    condIndex: Int,
) {
    val old = draft.scenes.getOrNull(index) ?: return
    if (old.conditions.size <= 1) return
    if (condIndex !in old.conditions.indices) return
    draft.update(index, old.withoutCondition(condIndex))
    XLog.w("系统场景：第 ${index + 1} 条规则删掉第 ${condIndex + 1} 条场景（剩 ${old.conditions.size - 1} 条）")
}

// 一张场景表里**哪几块是白写的** —— 全项目唯一的判据，分两层：
//   · 块内（[blockKindsOf]）：这一块里还能不能再加一档条件；
//   · 块间（[duplicateBlockIndexes]）：这一块跟上面某一块是不是同一块。
//
//  两件事本来就只有一个问题：判定侧「从上往下第一条成立的赢」，那么哪些块永远轮不到，
//  必须只有一份账。

/**
 * 一级菜单里**这一块已经加满**的那些档 —— 加满了就点不动了。
 *
 *  同一种类可以加**好几条**（判定侧按「或」算，见 `SceneRule.appliesTo`），上限是
 *  [ConditionKind.choices] **少一档**：屏幕那三档全加就是恒成立，那样的条件等于没写；
 *  少一档才说得出一件事（`[亮屏, 熄屏]` = 没停在锁屏界面）。
 *
 *  · 枚举取值的档（屏幕 / 响铃 / 方向 / 开关那几档）⇒ 最多 `choices.size - 1` 条。
 *    两档的那几种（开 / 关）因此仍然只许一条 —— 两条就是「开着或关着」，恒成立。
 *  · 「前台应用」⇒ 仍然只许一条：那一条自己就能多选（`包1||包2`，任一命中即通过），
 *    开两行只是把同一件事写成两处。
 *  · 「时间区间」⇒ 不限：不同时段各一条，各自是不同的情况（两块时间区间重叠另有拦截）。
 *
 *  ## 跨块不禁
 *
 *  判据**只看这一块**：别的行各要一条「亮屏」、各要一个「前台应用」，都是合理的 ——
 *  `[亮屏 + 微信]` 与 `[亮屏 + 相机]` 是两条不同的场景，前者不许把后者那条「亮屏」堵掉。
 *  跨块比是这里犯过的错：拿「整张表里用过没有」当判据，于是第二块想配 `A开 + B关` 时，
 *  「A开」在别的块里出现过就点不动了（与 `branchTimeOverlaps` 那条「跨块比会把合法配置拦成
 *  时间重叠」同一个道理）。跨块那件事由 [duplicateBlockIndexes] 按**整块**管。
 *
 *  @param skipIndex 正在改的那一条（它自己不算「已经占了」）
 */
internal fun blockKindsOf(
    conditions: List<BranchCondition>,
    skipIndex: Int,
): Set<ConditionKind> =
    conditions
        .filterIndexed { index, _ -> index != skipIndex }
        .mapNotNull { it.kind }
        .groupingBy { it }
        .eachCount()
        .filter { (kind, count) -> count >= blockConditionCap(kind) }
        .keys

/**
 * 一块里同一种条件最多几条 —— 见 [blockKindsOf]。
 *
 *  `coerceAtLeast(1)` 是给「只有一档取值」的种类兜的底：那样算出来是 0 条，这一档会在菜单里
 *  永远点不动，等于从界面上删掉了它。现在没有这种种类，别让以后加的那一种撞上。
 */
private fun blockConditionCap(kind: ConditionKind): Int =
    when {
        kind == ConditionKind.TIME -> Int.MAX_VALUE
        kind.choices.isEmpty() -> 1
        else -> (kind.choices.size - 1).coerceAtLeast(1)
    }

/**
 * 一块场景里**一条条件**归一之后的样子 —— 判「两块是不是同一块」就比这个。
 *
 *  ## 三档取值按**取值**比，不按种类比
 *
 *  屏幕状态的 `on` / `off` / `locked` 是三件不同的事（判据见 `ConditionChecks.screenMatches`，
 *  三档互斥）：`[熄屏]` 与 `[锁屏]` 是两块不同的场景。拿种类当答案的话，这两块会被判成同一块，
 *  用户就再也配不出第二块 —— 而这三档里**没有哪一档能代替另一档**。
 *
 *  取值一律走 [BranchCondition.effectiveValue]：老配置里没有取值的（`wifi` / `media` / `screen_on`）
 *  按它当时那个意思解释，与判定侧同一个口径。
 */
internal data class BlockAnswer(
    val kind: ConditionKind,
    val value: String,
)

/**
 * 一块场景的**条件答案**（排好序、去过重）；`null` = 这块还没配完，不参与比较。
 *
 *  排序是为了让条件的书写顺序不影响判定：`[微信 + 充电]` 与 `[充电 + 微信]` 是同一块
 *  （块内几条条件是「且」，先后本来就没有意义）。
 */
internal fun blockAnswersOf(rule: SceneRule): List<BlockAnswer>? {
    if (!rule.isReady) return null
    val answers = ArrayList<BlockAnswer>(rule.conditions.size)
    for (condition in rule.conditions) {
        val kind = condition.kind ?: return null
        answers += BlockAnswer(kind, answerOf(kind, condition))
    }
    answers.sortWith(ANSWER_ORDER)
    return answers.distinct()
}

/**
 * 一条条件的答案取值 —— 「前台应用」那一条要先归一。
 *
 *  那一条是**「或」**（`包1||包2`，任一在前台即通过），所以 `包1||包2` 与 `包2||包1` 是同一组
 *  应用：排个序再比，否则同一组应用写两遍会被当成两块不同的场景。
 */
private fun answerOf(
    kind: ConditionKind,
    condition: BranchCondition,
): String =
    if (kind.isAppFamily) {
        condition.appPackages
            .distinct()
            .sorted()
            .joinToString(BranchCondition.APP_SEPARATOR)
    } else {
        condition.effectiveValue
    }

/** 答案的排序：先按种类（枚举顺序，稳定），同一种再按取值。 */
private val ANSWER_ORDER: Comparator<BlockAnswer> =
    compareBy<BlockAnswer> { it.kind.ordinal }.thenBy { it.value }

/**
 * 表里**条件与上面某一块完全一样**的那几块。
 *
 * ## 为什么比「块」而不比「条件」
 *
 *  条件那一层不能去重：`[微信 + 充电]` 与 `[微信 + 耳机]` 是两条不同的场景，前者不许把后者
 *  那条「微信」堵掉（见 [blockKindsOf] 的「跨块不禁」）。**整块一样**才是真的重复。
 *
 * ## 只看条件，不看手势
 *
 *  一块本来就是「一组条件 + 三档手势」（见 `SceneRule`），同一组条件在表里只该有一块 ——
 *  真要按手势分工，把三档都配在同一块里就行。分成两块的话，判定侧「从上往下第一条成立的赢」
 *  会让后一块那几档轮不到，而界面上两块长得一模一样，用户分不清哪一块在管哪一档。
 *
 *  **这条判据不能看手势**：一块刚加进来时三档全是「跟随默认」，而这一页的顺序本来就是
 *  「先配条件、动作进「场景动作」页慢慢挑」。拿「配了动作的那几档」当判据的话，用户在配条件
 *  这一步永远看不到重复，要等配完动作才冒出来 —— 那时两块之间已经改过一遍了。
 *
 * ## 停用的块不算
 *
 *  停用的块整块跳过（`SceneRule.matches`），既不参与判定、也就不挡后面的块；它自己也不该
 *  被标成重复 —— 启用回来的时候要能照常生效。
 *
 * @return 那几块的下标（**后出现的那一块**才在下标里：先出现的永远赢）
 */
internal fun duplicateBlockIndexes(scenes: List<SceneRule>): Set<Int> {
    val seen = HashSet<List<BlockAnswer>>(scenes.size)
    val duplicated = LinkedHashSet<Int>()
    scenes.forEachIndexed { index, rule ->
        if (!rule.enabled) return@forEachIndexed
        val answers = blockAnswersOf(rule) ?: return@forEachIndexed
        //  只有**先出现**的那一块占得住这个答案，后面的都算重复
        if (!seen.add(answers)) duplicated += index
    }
    return duplicated
}
