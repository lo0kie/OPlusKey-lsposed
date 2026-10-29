package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.opticalCenter
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.theme.COUITheme

// 设置页那种「一张卡里一列行」的列表 —— 项目自己的一份实现（`CardRow` 是行，`CardList` 是那张卡）。
//
// 替掉的是之前每处都要写的三件胶水：
//   1. 先数一遍这张卡有几行（`rows = BASE + if (extra) 1 else 0`）；
//   2. 给每一行算「我是首行 / 中间 / 末行」（`cardListPositionOf(index, rows)`）；
//   3. 在每两行之间手动插一条 `HorizontalDivider`（还得自己补 `DividerDefaults.CardInset`）。
// 三件事其实全是**列表本身**知道的：它遍历的时候手里就有 `index` 与 `size`。
// 收进来之后调用点只写「这一张卡有哪几行」，行数、位置、分隔线一概不碰；
// 条件行也就自然正确 —— 用 `buildList { if (…) add(…) }` 组出来那份清单就是真相。

/**
 * 一张卡里的**一行**。
 *
 *  三种形态覆盖了现有各页：普通行（跳转 / 打开菜单 / 触发动作）、开关行、以及「自己是一整行」
 *  的自定义内容（滑块、输入框、可展开段）。网格、步骤行那些不是列表，不进这里。
 */
internal sealed interface CardRow {
    /**
     * 普通行：行首图标 + 标题 + 副标题 + 行尾（箭头 / 自定义内容）。
     *
     * @param summary `null` = 不写副标题
     * @param icon `null` = 这一行整张卡里都不放图标（同卡其余行也别放，否则标题左边缘不齐）
     * @param iconTint 行首图标的**指定颜色**（`null` = 跟着启用态走）。
     *   `enabled = false` 时不给就自动用禁用灰 —— 库只给它自己画的东西上色，行首槽位原样渲染。
     * @param danger 行尾 / 标题按危险色画（删除这类）
     * @param arrow 行尾画一枚「进下一页」的箭头
     * @param trailing 行尾自定义内容（与 [arrow] 互斥，实现在这一层只取一个）
     * @param onClick `null` = 不可点（纯展示行），此时也不挂按压反馈
     */
    @Suppress("LongParameterList")
    class Item(
        val title: String,
        val summary: String? = null,
        val icon: ImageVector? = null,
        val iconTint: Color? = null,
        /**
         * 标题的**非禁用**颜色（`null` = 库默认那档）。
         *
         *  用于「这一行还能点、但内容还不完整」：例如条件表里「未选择条件」那一格 ——
         *  文字压到禁用色，行本身仍可点。真要整行不可用请用 [enabled] = false。
         */
        val titleTint: Color? = null,
        val enabled: Boolean = true,
        /**
         * 这一行灰着被点一下时**这一行自己的说法**（`null` = 用整页那句，见 [DisabledHint]）。
         *
         *  为什么需要：整页那条说明的是「这一页现在为什么改不了」（链路没通 / 配置只读），
         *  而有些行是**因为它自己这一行的条件不够**才灰的 —— 例如「一颗自定义键都还没收时，
         *  组合按键配不出来」。拿整页那句回答它，等于答了另一个问题。
         *
         *  与 `MenuEntry.Row.disabledHint` 同一个意思、同一套读法；
         *  只在 `enabled = false` 时生效（能点的行不会被问「为什么点不了」）。
         */
        val disabledHint: String? = null,
        /**
         * 点这一行振不振 —— **默认振**（用户口径：能点的行默认就有触觉反馈）。
         *
         *  只有「点了会连着一串动作、或不希望它有反馈」的极少数行才传 `false`；
         *  默认开、个别关，比每处都记得振少一种漏法（原来每处自己在 `onClick` 里调 `tick()`，
         *  漏一处那一行就是「点了没感觉」）。
         */
        val vibrate: Boolean = true,
        val danger: Boolean = false,
        val arrow: Boolean = false,
        val trailing: (@Composable () -> Unit)? = null,
        val onClick: (() -> Unit)? = null,
        /** 挂在这一行根上的修饰符（`preciseClickAnchor` 这类按行定位的东西）。 */
        val modifier: Modifier = Modifier,
        /**
         * 这一行**会不会时有时无**。`null` = 一直在（不参与显隐动画）；
         * 非 `null` = 由这个布尔控制，**连它上面那条细线一起**做进出场——
         * 只藏行会在卡里留一条孤零零的线。
         */
        val visible: Boolean? = null,
    ) : CardRow

    /**
     * 开关行。
     *
     *  副标题**真假各一句**（由 [CardList] 按 `checked` 取）：开关的两个状态说的是两件事，
     *  一句描述不了。口径与 `SwitchRow` 完全一致，见那边的说明。
     */
    @Suppress("LongParameterList")
    class Switch(
        val title: String,
        val summaryOn: String,
        val summaryOff: String,
        val icon: ImageVector,
        val checked: Boolean,
        val enabled: Boolean = true,
        /** 同 [Item.visible]：非 `null` 时这一行（连分隔线）跟着它进出场。 */
        val visible: Boolean? = null,
        /** 同 [Item.vibrate]：默认振，个别关。 */
        val vibrate: Boolean = true,
        val onCheckedChange: (Boolean) -> Unit,
    ) : CardRow

    /**
     * 自己是一整行的那种（滑块 / 输入框 / 可展开段）。
     *
     *  它拿得到自己在卡里的位置（首行 / 中间 / 末行）—— 需要它的行（滑块）自己取，
     *  不需要的（输入框）忽略即可。**调用点不再自己算**。
     */
    class Custom(
        /** 同 [Item.visible]：非 `null` 时这一行（连分隔线）跟着它进出场。 */
        val visible: Boolean? = null,
        val content: @Composable (CardListPosition) -> Unit,
    ) : CardRow
}

/**
 * 一列行组成的卡片。
 *
 *  左右与网格同一档 [PAGE_MARGIN]（卡片自己的外边距），行间的细线与首尾的额外内边距由这一层统一给。
 *
 * @param rows 这张卡有哪几行 —— 条件行用 `buildList` 组，**行数不用自己数**
 */
@Composable
internal fun CardList(
    rows: List<CardRow>,
    modifier: Modifier = Modifier,
    /** 卡片左右的外边距。默认与页面同一档 [PAGE_MARGIN]；已经在带边距的容器里时传 `0.dp`。 */
    margin: Dp = PAGE_MARGIN,
    /**
     * 这张卡的颜色。`null` = 库默认（`surfaceContainer`，与页面上各卡片同一档）。
     *
     *  给「卡片的宿主不是页面底色」那种容器用：底色比页面高一档时，卡片要再高一档 ——
     *  否则面与卡片同色，卡的边界就没了。加到哪一档由调用点按自己的底色定，这里不预设。
     */
    colors: CardColors? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = margin),
        colors = colors ?: CardDefaults.defaultColors(),
    ) {
        //  **行序只按「此刻画得出来的行」算**，不按 `rows.size`：`visible = false` 的行只是
        //  还挂着把退场播完，它不占位置。拿总行数算的话，卡片末行藏起来时上面那行会被画成
        //  Middle —— 少掉 Tail 那一档的内缩与圆角，看着像卡片缺了一个角
        //  （真机上「更多 → 外观」那张卡就是这么看出来的）。
        val shown = rows.indices.filter { rows[it].visible() != false }
        val positionOf =
            shown.withIndex().associate { (order, index) -> index to cardListPositionOf(order, shown.size) }
        rows.forEachIndexed { index, row ->
            //  看不见的行仍在退场那一小段里要画：它按全表算（= 改之前的行为），只活这段时间
            val position = positionOf[index] ?: cardListPositionOf(index, rows.size)
            // 分隔线与行本身一起进出场（见各行的 `visible`）—— 所以它们必须同在一个块里
            val block: @Composable ColumnScope.() -> Unit = {
                // 首行之上不画线；其余每一行之前一条（左端与卡片内容对齐的细线）
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
                //  行高**不做过渡**（用户口径：试过 `animateContentSize`，观感不好，直接去掉）。
                //  试出来的结论留在这里，免得下次又走一遍：
                //   · 默认 `Alignment.TopStart`：内容按最终高度摆好、只有外框在动
                //     ⇒ 行首图标 / 行尾开关在动画第一帧就跳到最终位置（「瞬移」）；
                //   · 改成 `Alignment.Center` + `clipToBounds()`：控件跟着动了，
                //     但动画途中内容比外框高，上下各被裁掉半个行距，字被切一刀；
                //   · 都不裁剪则收缩时点击波纹画到卡片外面。
                //  根本原因是文本重排不可能逐帧渐变 —— 要么裁字，要么控件瞬移。不值得，去掉。
                RowContent(row, position)
            }
            when (val visible = row.visible()) {
                null -> block()
                else -> AnimatedColumn(visible = visible, content = block)
            }
        }
    }
}

/** 这一行会不会时有时无（`null` = 一直在）—— 三种行同一条判定。 */
private fun CardRow.visible(): Boolean? =
    when (this) {
        is CardRow.Item -> visible
        is CardRow.Switch -> visible
        is CardRow.Custom -> visible
    }

/** 把一行画出来（位置由列表算好递进来）。 */
@Composable
private fun ColumnScope.RowContent(
    row: CardRow,
    position: CardListPosition,
) {
    when (row) {
        is CardRow.Item -> {
            //  这一行自己带了「为什么灰着」的说法时，就地换掉整页那条（见 [DisabledHint]）。
            //  只有**灰着**才换：能点的行不会被问这个问题。
            val disabledHint = if (row.enabled) null else row.disabledHint
            DisabledHint(disabledHint) { ItemRow(row, position) }
        }

        is CardRow.Switch -> {
            SwitchRow(
                title = row.title,
                summaryOn = row.summaryOn,
                summaryOff = row.summaryOff,
                icon = row.icon,
                checked = row.checked,
                position = position,
                enabled = row.enabled,
                vibrate = row.vibrate,
                onCheckedChange = row.onCheckedChange,
            )
        }

        is CardRow.Custom -> {
            row.content(position)
        }
    }
}

/**
 * 普通行 —— 库的 [BasicComponent] 加两样项目口径：行首图标自己上色、行尾箭头自己画。
 *
 *  单独一支只为让 [CardList] 那支短下来；它与 `SwitchRow` 是同级的两条分支。
 */
@Composable
private fun ItemRow(
    row: CardRow.Item,
    position: CardListPosition,
) {
    // 两档色自己管（与整页其余行同一套）：库的箭头图标集与本项目的 Tabler 图标不是一支笔，
    //  而 `startAction` 槽位库不会按 `enabled` 上色 —— 不给就成了「字灰了、图标还亮着」。
    val iconTint by animateColorAsState(
        targetValue =
            row.iconTint ?: if (row.enabled) {
                COUITheme.colorScheme.onSurfaceContainer
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CardRowIcon",
    )
    val arrowTint by animateColorAsState(
        targetValue =
            if (row.enabled) {
                COUITheme.colorScheme.onSurfaceVariantActions
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CardRowArrow",
    )
    //  标题 / 副标题这一块**自己画**（不用库那两个 `String` 参数）：没有副标题的行要让标题
    //  做一次光学修正（见 [ItemText]），而库那一版把 `Text` 写死在内部、插不进修饰符。
    //  样式逐项照抄库那一版（`headline1` + Medium、副标题 `body2`、两行之间 2dp）。
    val titleColors =
        row.titleTint?.let { BasicComponentColors(color = it, disabledColor = it) }
            ?: BasicComponentDefaults.titleColor()
    val summaryColors = BasicComponentDefaults.summaryColor()
    BasicComponent(
        modifier = row.modifier,
        startAction = row.icon?.let { { Icon(it, contentDescription = null, tint = iconTint) } },
        endActions = {
            // `when` 在这里是**语句**（`endActions` 的返回值就是 Unit），不需要 `else` 兜底
            when {
                row.trailing != null -> {
                    row.trailing.invoke()
                }

                row.arrow -> {
                    Icon(
                        PageIcons.EnterArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ROW_END_ICON_SIZE),
                        tint = arrowTint,
                    )
                }
            }
        },
        cardListPosition = position,
        enabled = row.enabled,
        //  **点这一行就振一下**（默认行为，见 `LocalTick`）：控件层不认 `SettingsPage`，
        //  振动从页面那条通道下来。`vibrate = false` 的行不叫这一声。
        onClick =
            row.onClick?.let { action ->
                val tick = LocalTick.current
                {
                    if (row.vibrate) tick()
                    action()
                }
            },
    ) {
        ItemText(row, titleColors, summaryColors)
    }
}

/**
 * 行里的两行字 —— 标题，以及（有的话）副标题。
 *
 * ## 为什么这一块要项目自己画
 *
 *  为了那一句**光学修正**：行里只有标题时（没有副标题），行高由这一行字自己撑起来，
 *  而字在行盒里是**按行盒居中**的 —— 汉字墨迹的重心比行盒中心偏高一点，整行看着像被顶上去了。
 *  [opticalCenter] 按字形边界把它压回来。库那版 `BasicComponent` 的 `Text` 写在内部，
 *  修饰符插不进去，所以这一块自己拼。
 *
 *  有副标题时**不做**修正：那时行高由两行字一起撑，标题与副标题互相平衡，
 *  单独把标题压下来反而让两行的间距看着不匀。
 */
@Composable
private fun ItemText(
    row: CardRow.Item,
    titleColors: BasicComponentColors,
    summaryColors: BasicComponentColors,
) {
    val titleStyle = COUITheme.textStyles.headline1
    Text(
        text = row.title,
        style = titleStyle,
        fontWeight = FontWeight.Medium,
        color = if (row.enabled) titleColors.color else titleColors.disabledColor,
        modifier = if (row.summary == null) Modifier.opticalCenter(titleStyle) else Modifier,
    )
    if (row.summary != null) {
        Text(
            text = row.summary,
            modifier = Modifier.padding(top = ROW_LINE_GAP),
            style = COUITheme.textStyles.body2,
            color = if (row.enabled) summaryColors.color else summaryColors.disabledColor,
        )
    }
}

/** 标题与副标题之间那一档 —— 与库的 `MarginBetweenLine` 同一值（2dp）。 */
private val ROW_LINE_GAP = 2.dp

/**
 * 行尾那颗**指示性图标**（`trailing` / `endActions` 槽里那一颗）—— 全项目只此一份。
 *
 * ## 为什么收在这里
 *
 *  同一颗图标原来有三份实现：`ui/screen/about/AboutScreen` 的 `RowEndIcon`、
 *  `ui/screen/command/CommandBranchParts` 的 `SelectorGlyph`、以及首页 / 变量页里各写一遍的
 *  `Icon(…, size(ROW_END_ICON_SIZE), onSurfaceVariantActions)`。三份的差别只在**字形**，
 *  尺寸与颜色一模一样 —— 而尺寸和颜色正是「行尾那一档」的定义，不该由调用点各记一遍。
 *
 *  位置也顺带修正了：原来那份定义在「关于」页里，别的页要么跨页 import、要么自己再抄一份。
 *  行尾槽是 `CardRow` 的东西，图标跟着它走。
 *
 * ## 字形怎么选
 *
 *   · 进**页内下一层**（或跳出去）→ 默认的 `EnterArrow`；
 *   · 点开的是一层**菜单 / 选择器** → `PageIcons.Selector`（读作「点了要选点什么」）；
 *   · 点了**跳外部链接** → `PageIcons.Link`（链接字形比箭头具体）。
 *  只读的行（不给 `onClick`）一律**不给**这颗图标 —— 它说的是「点了有下一站」。
 *
 * ## 尺寸与颜色
 *
 *  [ROW_END_ICON_SIZE]（24dp）+ `onSurfaceVariantActions`：比行首那颗图标弱一档，
 *  它表示「点这一行还有下一层」，不是动作本身。要更强的读法（比如已选中）由调用方自己传
 *  [tint]，**不要**在这里加分支。
 *
 * @param icon 字形。默认「进入下一层」的箭头
 * @param tint 颜色。默认最弱那一档
 */
@Composable
internal fun RowEndIcon(
    icon: ImageVector = PageIcons.EnterArrow,
    tint: Color = COUITheme.colorScheme.onSurfaceVariantActions,
) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(ROW_END_ICON_SIZE),
        tint = tint,
    )
}
