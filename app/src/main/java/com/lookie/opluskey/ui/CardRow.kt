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
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
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
        rows.forEachIndexed { index, row ->
            val position = cardListPositionOf(index, rows.size)
            // 分隔线与行本身一起进出场（见各行的 `visible`）—— 所以它们必须同在一个块里
            val block: @Composable ColumnScope.() -> Unit = {
                // 首行之上不画线；其余每一行之前一条（左端与卡片内容对齐的细线）
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
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
            ItemRow(row, position)
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
    BasicComponent(
        modifier = row.modifier,
        title = row.title,
        //  只有「还能点、但内容不完整」那一档才自定标题色：库的 `BasicComponentColors`
        //  只有「正常 / 禁用」两档，这里把正常色直接换成禁用色，`enabled` 仍为 true
        //  ⇒ 点击、涟漪、无障碍语义都不受影响，只是字压暗了。
        titleColor =
            row.titleTint?.let { BasicComponentColors(color = it, disabledColor = it) }
                ?: BasicComponentDefaults.titleColor(),
        summary = row.summary,
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
        onClick = row.onClick,
    )
}
