package com.lookie.opluskey.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.COLOR_FADE_SPEC
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_DARK_ALPHA
import com.lookie.opluskey.ui.model.MENU_GROUP_LINE_LIGHT_ALPHA
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.ROW_TITLE_INSET
import com.lookie.opluskey.ui.model.VISIBILITY_ENTER
import com.lookie.opluskey.ui.model.VISIBILITY_EXIT
import com.lookie.opluskey.ui.model.isDarkScheme
import com.lookie.opluskey.ui.model.opticalCenter
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
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
     * @param startIcon 行首那一格**自己画**的东西 —— 应用图标这类不是 `ImageVector` 的图，
     *   或者需要按行自己取色的字形。非空时**优先于** [icon]（两者只需给一个）。
     * @param titleExtra 紧跟在标题右边的内容（「模块 / 系统 / 用户」这类小标签）。
     *   标题长了先缩标题，这一块始终留在行内可见。
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
        /**
         * 标题最多几行 —— 默认 1（单行省略，整页的常态）。
         *
         *  给 [Int.MAX_VALUE] 就是「不限制」：条件表里「前台应用」那一行会把挑中的几个应用名
         *  顿号连起来，名字一多单行必然被省略号吃掉，而这一行正是用来核对自己挑了哪几个的。
         *  放开之后行高由内容撑（库那条行本来就按内容高）。
         */
        val titleMaxLines: Int = 1,
        /**
         * 这一行前面画**加深带**而不是默认那条细线 —— 「这一组 / 下一组」之间断开时用
         * （与浮层菜单里组间那条同一档）。默认 `false` = 细线。
         */
        val dividerBefore: Boolean = false,
        val summary: String? = null,
        /**
         * 副标题行**行尾**那一块 —— 与 [summary] 同一行，`summary` 先给它让位。
         *
         *  给「这一行的信息里有一段**必须始终看得见**」那种用（例如进程页的「已运行 12 分钟」/
         *  「128 MB」）：拼成一整串交给 [summary] 的话，名字一长，`Text` 的省略就把它从**尾部**
         *  裁掉了 —— 那一段恰恰是用户按这个维度排序时想看的东西。拆成两块之后，被裁的是名字。
         *
         *  给了这一块时 `summary` 收成**单行省略**（原来是可折行的）：两块挤在一行里，折行会把
         *  行高撑起来、也说不清谁是谁。没给时行为一切照旧。
         */
        val summaryExtra: (@Composable () -> Unit)? = null,
        val icon: ImageVector? = null,
        /** 行首那一格自己画（见 KDoc）；非空时优先于 [icon]。 */
        val startIcon: (@Composable () -> Unit)? = null,
        /** 紧跟标题右边的内容（见 KDoc）；`null` = 没有。 */
        val titleExtra: (@Composable () -> Unit)? = null,
        /**
         * 标题那一块**自己画**；给了就代替 [title] 那颗 `Text`（[titleExtra] 照旧跟在它右边）。
         *
         *  给「一行里要摆**固定几列**」的那种行：组合按键那一列是「键名 × 键名」，
         *  两个键名各占一列、分隔符落在列边界上 —— 一列下来才竖着对齐。
         *  拼成一整串字符串做不到这件事：分隔符会跟着前一个名字的长短走（真机反馈
         *  「单独某个名字过长时全部都没对齐」）。
         *
         *  [title] 仍要给：它是这一行的**文字内容**（无障碍、以及没给这一块时的兜底）。
         */
        val titleContent: (@Composable () -> Unit)? = null,
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
         * 这一行**在列表里的身份**（`null` = 按位次认）。只服务进出场动画。
         *
         *  为什么需要：行的进出场状态（下面 [visible] / [visibleState]）在 [CardList] 里是
         *  **按位次**（`remember(index)`）记的 —— 中间**插进**一行时，新行会落到原来那一行的槽上、
         *  继承它的状态。那份状态的 `currentState` 多半是 `true`（旧行一直在场），于是新行一帧就
         *  满高出现，再「收缩 → 展开」闪一下，看着就是生硬插入（真机口径「一块里加场景，是生硬
         *  插进去的」）。
         *
         *  给了身份之后，状态按「是哪一行」认：新行拿到一份从 `visible` 起步的新状态，进退场
         *  照常播。改动某一行自己的内容会改变这个值（例如条件行拿条件本身当身份）—— 那没关系，
         *  它换出来的新状态从 `visible` 起步，不播动画。
         */
        val rowKey: Any? = null,
        /**
         * 这一行**会不会时有时无**。`null` = 一直在（不参与显隐动画）；
         * 非 `null` = 由这个布尔控制，**连它上面那条细线一起**做进出场——
         * 只藏行会在卡里留一条孤零零的线。
         */
        val visible: Boolean? = null,
        /** 同 [Custom.visibleState]：这一行自带进出场状态时按它播（`visible` 仍用来算行位置）。 */
        val visibleState: MutableTransitionState<Boolean>? = null,
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
        /**
         * 这一行灰着被点一下时的说法（`null` = 用整页那句）——
         * 与 [Item.disabledHint] 同一个意思、同一套读法，只在 `enabled = false` 时生效。
         */
        val disabledHint: String? = null,
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
        /**
         * 这一行**自带**一份进出场状态时按它播（`visible` 仍用来算行位置）。
         *
         *  给「行的展开 / 收缩规格是这一页单独调的」那种用（自定义命令的步骤行）：
         *  用布尔重播一遍会把它自己的规格换掉。
         */
        val visibleState: MutableTransitionState<Boolean>? = null,
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
        //  **行序按「这一行此刻还在（或正赶来）」算** —— 既不是 `rows.size`，也不是 `visible`
        //  一变就让位。两个边界各自对应一种真机上能看出来的毛病：
        //   · 按 `rows.size` 算：末行藏起来时上面那行还是 Middle —— 少掉 Tail 那一档的内缩与圆角，
        //     看着像卡片缺了一个角（真机上「更多 → 外观」那张卡就是这么看出来的）；
        //   · 让位时机对不上：这一档内缩在换档那一帧整体挪到另一行身上，谁在它下面谁就被顶一下。
        //     退场行让位太早 ⇒ 还在收缩的行先被顶下去；进场行算得太晚 ⇒ 它自己先在低一档的位置
        //     出现、长完再弹回原位。
        //  所以判据是 `currentState || targetState`：**退场要播完才让位，进场从第一帧就算在场**。
        //  两个方向换档都是净零 —— 多出来 / 少掉的那一档落在卡片的边上，那儿没有别的内容。
        val states = arrayOfNulls<MutableTransitionState<Boolean>>(rows.size)
        rows.forEachIndexed { index, row ->
            val visible = row.visible()
            //  状态按「位次 + 这一行的身份」认（身份见 [CardRow.Item.rowKey]）：只按位次的话，
            //  中间**插进**一行会继承原槽那份状态（多半是「一直在场」），新行满高出现一帧、
            //  再收缩再展开 —— 看着像生硬插入。
            val state = remember(index, row.identity) { MutableTransitionState(visible ?: true) }
            //  自带状态的行走它自己那一份（见下面 `ownState`），这里只接管由 `visible` 驱动的行
            if (visible != null && row.explicitState() == null) {
                state.targetState = visible
                states[index] = state
            }
        }
        val shown =
            rows.indices.filter { index ->
                //  自带状态的那几行（步骤行）仍按 `visible` 算：它们的状态从 `false` 起步、
                //  一插进来就要算在场（见 `CommandSteps`），与这里的判据是同一个意思。
                val state = states[index]
                if (state != null) state.currentState || state.targetState else rows[index].visible() != false
            }
        //  行间那条细线的左起点：这张卡的行首有没有图标那一列 —— 有就缩进到**标题左缘**
        //  （[ROW_TITLE_INSET]），没有就与卡片自己的内边距对齐，别凭空让出一格。
        val hasIconColumn = rows.any { it.drawsStartIcon }
        val positionOf =
            shown.withIndex().associate { (order, index) -> index to cardListPositionOf(order, shown.size) }
        rows.forEachIndexed { index, row ->
            //  已经彻底藏好、但这一帧还挂在组合里的行（退场刚播完那一刻）取
            //  [CardListPosition.Middle]：它一高都没有，也不该再去争首 / 末那一档内缩。
            val position = positionOf[index] ?: CardListPosition.Middle
            // 分隔线与行本身一起进出场（见各行的 `visible`）—— 所以它们必须同在一个块里
            val block: @Composable ColumnScope.() -> Unit = {
                // 首行之上不画线；其余每一行之前一条（左端从**标题最左处**起，右端与卡片内边距对齐）。
                //  这一行自己要求「加深带」时换成通栏那一档（见 [HeavyDivider]）。
                if (index > 0) {
                    if (row.wantsHeavyDivider) {
                        HeavyDivider()
                    } else {
                        HorizontalDivider(
                            Modifier.padding(
                                start = if (hasIconColumn) ROW_TITLE_INSET else DividerDefaults.CardInset,
                                end = DividerDefaults.CardInset,
                            ),
                        )
                    }
                }
                //  行高**不做过渡**（用户口径：加上之后开关行自己的动画就没了，观感更差，去掉）。
                //  试出来的结论留在这里，免得下次又走一遍：
                //   · 默认 `Alignment.TopStart`：内容按最终高度摆好、只有外框在动
                //     ⇒ 行首图标 / 行尾开关在动画第一帧就跳到最终位置（「瞬移」）；
                //   · 改成 `Alignment.Center` + `clipToBounds()`：控件跟着动了，
                //     但动画途中内容比外框高，上下各被裁掉半个行距，字被切一刀；
                //   · 都不裁剪则收缩时点击波纹画到卡片外面。
                //  根本原因是文本重排不可能逐帧渐变 —— 要么裁字，要么控件瞬移。不值得，去掉。
                RowContent(row, position)
            }
            //  自带状态的行按它播（规格由调用点定），其余按上面那份 `visible` 状态播
            val ownState = row.explicitState()
            when {
                ownState != null -> {
                    AnimatedVisibility(
                        visibleState = ownState,
                        enter = VISIBILITY_ENTER,
                        exit = VISIBILITY_EXIT,
                    ) {
                        block()
                    }
                }

                else -> {
                    val state = states[index]
                    if (state == null) block() else AnimatedColumn(visibleState = state, content = block)
                }
            }
        }
    }
}

/**
 * 这一行会不会画行首那一格（决定分隔线要不要缩进到标题左缘）。
 *
 *  写成**计算属性**而不是 `fun drawsStartIcon()`：这三支（含下面两支）都只是「这一行是什么」的
 *  纯读取，没有副作用也不带参数，属性才是它的自然形态；顺带把这个文件的函数条数留在
 *  detekt 的 `TooManyFunctions` 之下（口径与 `CardRow.Item.visible` 那些字段一致）。
 */
private val CardRow.drawsStartIcon: Boolean
    get() =
        when (this) {
            is CardRow.Switch -> true
            is CardRow.Item -> icon != null || startIcon != null
            is CardRow.Custom -> false
        }

/** 这一行前面要不要画**加深带**（`false` = 默认那条细线）。 */
private val CardRow.wantsHeavyDivider: Boolean
    get() =
        when (this) {
            is CardRow.Item -> dividerBefore
            is CardRow.Switch -> false
            is CardRow.Custom -> false
        }

/**
 * 卡片里「这一组 / 下一组」之间那条**加深带** —— 与浮层菜单组间那条同一档（同两个 alpha、
 * 同一档粗细），两处读起来才是同一种分隔。
 */
@Composable
private fun HeavyDivider() {
    HorizontalDivider(
        thickness = DropdownDefaults.PopupGroupDividerThickness,
        color =
            if (COUITheme.colorScheme.isDarkScheme) {
                Color.Black.copy(alpha = MENU_GROUP_LINE_DARK_ALPHA)
            } else {
                Color.Black.copy(alpha = MENU_GROUP_LINE_LIGHT_ALPHA)
            },
    )
}

/** 这一行会不会时有时无（`null` = 一直在）—— 三种行同一条判定。 */
private fun CardRow.visible(): Boolean? =
    when (this) {
        is CardRow.Item -> visible
        is CardRow.Switch -> visible
        is CardRow.Custom -> visible
    }

/** 这一行是不是自带一份进出场状态（步骤行那种，规格由调用点定）；`null` = 由 [CardList] 自己管。 */
private fun CardRow.explicitState(): MutableTransitionState<Boolean>? =
    when (this) {
        is CardRow.Item -> visibleState
        is CardRow.Switch -> null
        is CardRow.Custom -> visibleState
    }

/**
 * 这一行**在列表里的身份**（见 [CardRow.Item.rowKey]）—— 只有条件行给了，
 * 其余行按位次认（`null`），行为与加这一位之前完全一致。
 *
 *  与上面两支同一个理由：纯读取、无参，写成计算属性。
 */
private val CardRow.identity: Any?
    get() =
        when (this) {
            is CardRow.Item -> rowKey
            is CardRow.Switch -> null
            is CardRow.Custom -> null
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
            //  与 [CardRow.Item] 同一套：灰着被点时说的那句原因，由行自己带（`null` 用整页那条）
            val disabledHint = if (row.enabled) null else row.disabledHint
            DisabledHint(disabledHint) {
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
        }

        is CardRow.Custom -> {
            row.content(position)
        }
    }
}

/**
 * 卡片行行首图标的颜色 —— 行自己指定的 → 随机配色（**仅启用的行**）→ 常规 / 禁用两档，带过渡。
 *
 *  行自己指定了 `iconTint` 的（强调色色块那几行）以它为准：那块色就是这一行的身份。
 *  禁用行不上随机色 —— 给一颗灰着的行上彩色，读起来像「这一行能用」。
 *
 *  抽成一支只为让 [ItemRow] 留在 detekt 的 `LongMethod` 长度线内。
 */
@Composable
private fun rememberCardRowIconColor(row: CardRow.Item): State<Color> =
    animateColorAsState(
        targetValue =
            row.iconTint
                ?: row.icon?.takeIf { row.enabled }?.tinted()
                ?: if (row.enabled) {
                    COUITheme.colorScheme.onSurfaceContainer
                } else {
                    COUITheme.colorScheme.disabledOnSecondaryVariant
                },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CardRowIcon",
    )

/**
 * 行尾那一格现在是哪一种形态 —— 只用来给 [Crossfade] 一个稳定的目标值（见 [ItemRow] 的 `endActions`）。
 *
 *  `trailing` 那个 lambda 每次重组都是新的，拿它当 `targetState` 会让交叉淡入每帧重启一遍；
 *  这一格真正会换的只有「是哪一档」，所以按档记身份。自有控件优先于箭头（与原来那个 `when` 同序）。
 */
private enum class RowEndSlot { None, Arrow, Content }

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
    val iconTint by rememberCardRowIconColor(row)
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
        startAction = cardRowStartAction(row, iconTint),
        endActions = {
            //  行尾那一格在「箭头 / 自有控件 / 空」三档之间是**就地换控件**（编辑态把箭头换成多选框、
            //  只读时收起箭头都走这里）。箭头与多选框同尺寸 ⇒ 交叉淡过去时盒宽不变，
            //  右侧不会「啪」地换一块，标题也不会被推一下。
            Crossfade(
                targetState =
                    when {
                        row.trailing != null -> RowEndSlot.Content
                        row.arrow -> RowEndSlot.Arrow
                        else -> RowEndSlot.None
                    },
                animationSpec = COLOR_FADE_SPEC,
                label = "CardRowEnd",
            ) { slot ->
                when (slot) {
                    RowEndSlot.Arrow -> RowEndIcon(tint = arrowTint)
                    RowEndSlot.Content -> row.trailing?.invoke()
                    RowEndSlot.None -> Unit
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
 *  只有「标题 + 副标题」那种**不做**修正：那时行高由两行字一起撑，标题与副标题互相平衡，
 *  单独把标题压下来反而让两行的间距看着不匀。但标题右边挂了标签（`titleExtra`）时必须压 ——
 *  标签自己那颗字已经按字形边界压过了，标题不压就与它差半档。
 */
@Composable
private fun ItemText(
    row: CardRow.Item,
    titleColors: BasicComponentColors,
    summaryColors: BasicComponentColors,
) {
    val titleStyle = COUITheme.textStyles.headline1
    //  标题 + 紧跟其后的那一块（小标签）：整块放进一个 `Row`，标题带 `weight(fill = false)`
    //  —— 名字长了先缩标题，标签始终留在行内（与原来那一行自己排的规矩一致）。
    //
    //  这一行里**必须做光学修正**（与「只有标题」那一档同一个理由，见函数 KDoc）：
    //  标签自己那颗字是按字形边界压过的（`AppKindTag`），标题不压就与它差半档 ——
    //  摆在一起读作「标签没有中线对齐标题」。有副标题时也照压：修正只挪这一行字的墨迹，
    //  副标题是另一段、不受影响。
    //  多行标题不做光学修正：那一手是按**一行墨迹**的中线挪的，几行摞起来挪只会整体偏上。
    val titleNeedsOpticalCenter =
        (row.summary == null || row.titleExtra != null) && row.titleMaxLines == 1
    //  标题 / 副标题两档色同样带过渡：整页闸门一拨、或这一行自己从「待补条件」变成「填好了」时，
    //  行首图标与行尾箭头本来就在渐变（见 [rememberCardRowIconColor] 与 `arrowTint`）——
    //  文字硬切就成了「图标在淡、字在跳」。
    val titleColor by animateColorAsState(
        targetValue = if (row.enabled) titleColors.color else titleColors.disabledColor,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CardRowTitle",
    )
    val summaryColor by animateColorAsState(
        targetValue = if (row.enabled) summaryColors.color else summaryColors.disabledColor,
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "CardRowSummary",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (row.titleContent != null) {
            //  **整列占满**：自己画的那一块要能按列分宽度（见 [CardRow.Item.titleContent]），
            //  这里就不能按内容宽度收缩。光学修正也不做 —— 里面是几列并排，不是一行字。
            Box(Modifier.weight(1f)) { row.titleContent.invoke() }
        } else {
            //  **标题换了就淡过去**，不硬切：同一行原地换内容的场合不少（条件行从「未选择场景」
            //  换成真的那一档、动作格从「无操作」换成具体命令），硬切读起来像「这一行被删掉、
            //  又插了一行新的」。行首那颗字形本来就在交叉淡（见 `cardRowStartAction`）——
            //  文字不淡就成了「图标在淡、字在跳」。曲线与那一处同源（[COLOR_FADE_SPEC]）。
            //  两种内容同字号、同边距 ⇒ 交叉期间盒宽取较大者，行高不变。
            Crossfade(
                targetState = row.title,
                modifier = Modifier.weight(1f, fill = false),
                animationSpec = COLOR_FADE_SPEC,
                label = "CardRowTitleText",
            ) { shown ->
                Text(
                    text = shown,
                    style = titleStyle,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = row.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (titleNeedsOpticalCenter) Modifier.opticalCenter(titleStyle) else Modifier,
                )
            }
        }
        row.titleExtra?.let {
            Spacer(Modifier.width(ROW_TITLE_EXTRA_GAP))
            it()
        }
    }
    ItemSummary(row, summaryColor)
}

/** 标题与副标题之间那一档 —— 与库的 `MarginBetweenLine` 同一值（2dp）。 */
internal val ROW_LINE_GAP = 2.dp

/** 标题与紧跟其后那一块（小标签）之间那一档 —— 与标签之间的间距同一值（6dp）。 */
private val ROW_TITLE_EXTRA_GAP = 6.dp

/**
 * 选中那一行外面那一圈**内边框** —— 「选择应用」与「选择界面」两页共用。
 *
 * ## 四角必须是**超椭圆**，不能用 `RoundedCornerShape`
 *
 *  COUI 的圆角全是 squircle（卡片 / 按钮 / 弹窗 / 顶栏都是），而 `Modifier.border` 配一个
 *  `RoundedCornerShape` 画出来的是**正圆角**：两者在四角处的曲率不一样，正圆角那一段会伸到
 *  卡片自己的轮廓**外面**，被卡片的裁剪切掉 ⇒ 描边在四个角上断一截（真机反馈「圆角处不完整」）。
 *  所以这一圈用库的 `addSquircleRect` 自己拼路径 —— 形状定义只有库那一份，与卡片是同一族曲线。
 *
 * ## 为什么还要往里让 2dp
 *
 *  贴着边画等于把描边压在卡片的裁剪线上：抗锯齿会吃掉外侧那一半，深色下看着像「线不实」。
 *  让出 [SELECTED_RING_AIR] 之后整圈描边都落在卡片轮廓**里面**，四个角是完整的弧。
 *  半径同时减去这一档，内外两条曲线才同心 —— 不减的话四角又会被顶到轮廓外。
 *
 *  为什么整块收在这里：一级页的行尾是勾、二级页的行尾是圈，两页的「选中」只有颜色与字形不同，
 *  这一圈必须一模一样（同一种状态在两页长得不一样，用户会以为选中的东西不同）。
 *
 *  @param selected 没选中时**一个修饰符都不挂** —— 这一页几百行，绝大多数都没选中，
 *   不必为它们各加一层绘制节点。
 */
@Composable
internal fun Modifier.selectedRowRing(selected: Boolean): Modifier {
    if (!selected) return this
    val color = COUITheme.colorScheme.primary
    val ring =
        with(LocalDensity.current) {
            RingGeometry(
                stroke = SELECTED_RING_WIDTH.toPx(),
                inset = SELECTED_RING_AIR.toPx(),
                radius = (CardDefaults.CornerRadius - SELECTED_RING_AIR).toPx(),
            )
        }
    return drawWithContent {
        //  `BasicComponent` 自己的底色画在这一层**之前**（真机确认：描边看得见），
        //  所以先让它画完，再把这一圈压在最上面。
        drawContent()
        val width = size.width - ring.inset * 2
        val height = size.height - ring.inset * 2
        if (width <= 0f || height <= 0f) return@drawWithContent
        val path =
            Path().apply {
                addSquircleRect(width = width, height = height, cornerRadius = ring.radius)
            }
        translate(ring.inset, ring.inset) {
            drawPath(path, color, style = Stroke(width = ring.stroke))
        }
    }
}

/** 内边框的三档像素值 —— 每帧要用，先算好（density 变了会重新组合出来）。 */
private class RingGeometry(
    val stroke: Float,
    val inset: Float,
    val radius: Float,
)

/**
 * 选中那一圈内边框的线宽 —— 2dp。
 *
 *  再细一档（1dp）在 2.5x 屏上是 2~3 个像素，与卡片自身的明暗差混在一起看不出来；
 *  再粗就变成「按钮描边」，而这一圈说的是「这一行就是当前这个」，不是「按我」。
 */
private val SELECTED_RING_WIDTH = 2.dp

/** 内边框离卡片边缘那一档 —— 2dp（见 [selectedRowRing] 的「为什么还要往里让」）。 */
private val SELECTED_RING_AIR = 2.dp

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
