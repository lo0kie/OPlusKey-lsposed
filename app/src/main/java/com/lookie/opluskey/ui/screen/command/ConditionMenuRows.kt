package com.lookie.opluskey.ui.screen.command

import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ConditionKind
import com.lookie.opluskey.core.model.SceneRule
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.icon.conditionIcon
import com.lookie.opluskey.ui.model.text.conditionChoiceLabel
import com.lookie.opluskey.ui.model.text.conditionKindLabel

// 条件菜单那两层各自的行 —— 一级「选种类」、二级「选取值」。
// 从 `CommandBranchMenus.kt` 拆出来：那边是「菜单什么时候开、二级挂在哪儿」，
// 这边只是「一行怎么画」，两件事各自涨。

/**
 * 一级菜单里每一档**点不点得动** —— 两个层级的原因合成一个对象。
 *
 *  这一支本来就是「行怎么画」，参数该只有三样 —— 现在选的是谁、哪几档点不动、点下去做什么。
 *
 * @param used **这一块里**已经加满的那几档（同一种类写重了）
 * @param blockFull 这一块**整体**到顶了（[SceneRule.MAX_CONDITIONS] 条）—— 每一档都点不动，
 *   说的那句话也换成整块那一句。只在「再加一条」那一趟成立：改现有的一条不涨条数。
 */
internal class ConditionKindLimits(
    val used: Set<ConditionKind>,
    val blockFull: Boolean,
)

/**
 * 一级菜单那几行 —— 一种条件一行，[ConditionKindLimits.used] 里的置灰、`current` 那一档打勾。
 *
 *  抽成一支只为把 [BranchConditionMenu] 压回 `LongMethod` 的阈值：这里只有「行怎么画」，
 *  与「菜单开没开、二级挂在哪儿」无关。
 *
 * @param current 这一条现在那一档（画成选中）；`-1` 那一趟（再加一条）传 `null`
 * @param currentSummary 选中那一行的**子标题**（已拼好：`打开` / `已选择 3 个应用` / `22:00-07:00`）——
 *   一级菜单里那一行只写种类名，选中了之后看不出选的是哪一档（「开 / 关」这种字本身不带主语）。
 *   `null` = 不出小字（还没选、或那一档不该列取值）。拼法见 `conditionMenuSummary`。
 * @param limits 哪几档点不动、为什么（见 [ConditionKindLimits]）
 * @param onPick 种类定下来之后的落盘（挑应用页 / 时间窗 / 直接写，见 [BranchConditionMenu] 的 `commit`）
 * @param onSub 要开二级取值的那一档
 */
internal fun conditionKindRows(
    current: ConditionKind?,
    currentSummary: String?,
    limits: ConditionKindLimits,
    onPick: (ConditionKind, String) -> Unit,
    onSub: (ConditionKind) -> Unit,
): List<MenuEntry.Row> =
    ConditionKind.entries.map { kind ->
        MenuEntry.Row(
            title = conditionKindLabel(kind),
            // 子标题只给**当前选中的那一档**：其余行写了「取值」会被读成「也选了」。
            summary = if (kind == current) currentSummary else null,
            icon = conditionIcon(kind),
            // 这一块里同一种类加到了上限才置灰（别的行用过哪一档与本块无关）；
            //  整块到顶时每一档都灰，理由换成「这一块最多 N 条」
            enabled = !limits.blockFull && kind !in limits.used,
            disabledHint =
                if (limits.blockFull) {
                    I18n.format(Strings.COMMAND_BRANCH_CONDITION_LIMIT, SceneRule.MAX_CONDITIONS)
                } else {
                    I18n.get(Strings.COMMAND_BRANCH_KIND_FULL, "这一类场景已经加满了")
                },
            // 这一条现在是这一档 ⇒ 画成选中（行首对勾 + 文字主题色）
            selected = kind == current,
            //  行尾**不画选择器**：点下去弹的是选择器弹窗（不是另一层菜单），行尾那枚
            //  「还有一层」的字形会读成「这里会再弹一张菜单」。
            //  「前台应用」仍是进另一页挑应用，那一枚右箭头留着。应用那一族两档一样（见
            //  `ConditionKind.isAppFamily`）：同一个列表、同一套多选，只是写回去的 `kind` 不同。
            submenu = kind.isAppFamily,
            trailingIcon = if (kind.isAppFamily) PageIcons.EnterArrow else null,
        ) {
            // 应用那一族先去挑是哪个应用（进另一页）、「时间区间」弹时间窗 —— 这几档都没有
            //  可列的候选；其余每一档都开二级选值。
            //  关一级菜单由调用点做：它得先读 `condAt` 才知道落哪一条。
            if (kind.isAppFamily || kind == ConditionKind.TIME) onPick(kind, "") else onSub(kind)
        }
    }

/**
 * 取值那一层的行 —— [ConditionKind.choices] 非空的那几档用（WIFI / 媒体状态 / 响铃状态 / 屏幕方向…）。
 *
 *  **只置灰本块里这一档已经用过的取值**：同一种类能加好几条（见 [blockKindsOf]），再加一个
 *  同样的状态就是白占一格 —— `[锁屏, 锁屏]` 与 `[锁屏]` 说的是一件事。
 *  跨块不管：同一个取值可以出现在不同的块里（`[亮屏 + 微信]` 与 `[亮屏 + 相机]` 是两条不同的
 *  场景），拿整张表去比会把第二块那条合法条件堵掉。
 *
 * @param currentValue 这一档**现在**的取值（用来画选中）；空值由调用方按默认档给
 * @param usedValues 本块里**别的**几条已经用掉的取值（正在改的这一条自己不算，否则它自己会灰）
 */
internal fun conditionChoiceRows(
    kind: ConditionKind,
    currentValue: String,
    usedValues: Set<String>,
    onPick: (String) -> Unit,
): List<ChoiceRow> =
    kind.choices.map { value ->
        ChoiceRow(
            title = conditionChoiceLabel(kind, value),
            //  这一块里已经有一条同取值的条件 ⇒ 点不动（判定侧是「或」，加第二条不改变结果）
            enabled = value !in usedValues,
            selected = value == currentValue,
            onPick = { onPick(value) },
        )
    }

/**
 * 取值那一层的一行 —— **不是菜单行**（它是选择器弹窗里的一项，用户口径：二级不用菜单）。
 *
 *  所以它没有图标：选择器那一列现在一律不带行首字形（见 `PickerOption`）。
 *  原先复用 `MenuEntry.Row` 是为了少一个类型，代价是「菜单才能用的图标」被带进了弹窗。
 */
internal class ChoiceRow(
    val title: String,
    val enabled: Boolean,
    val selected: Boolean,
    val onPick: () -> Unit,
)
