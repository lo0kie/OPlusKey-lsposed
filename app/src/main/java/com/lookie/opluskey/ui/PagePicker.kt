package com.lookie.opluskey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.opticalCenter
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

// 「弹窗里一列可选行」这一套的公共组件 —— 单选（选中即关）与多选（勾几项再确定）共用同一份排布。
//
// 出身：「系统开关」那一层选择器（切换 / 打开 / 关闭）本来独自住在 `ModeMenuPopups` 里，
// 而「选择按键」那个弹窗要的是同一个样子（通栏行、选中整行上色 + 行尾对勾、行间同一档细线）。
// 两处各写一遍的话，「这一行多高、内缩多少、勾多大」迟早分叉 —— 那正是用户现在要求
// 「样式参考系统开关」而不是「再用一套」的原因。所以抽到这里，两边都用它。

/**
 * 选择器里的一行 —— **整行通栏可点**，标题左起、选中的行行尾一个对勾。
 *
 *  不挂行首图标：这一列说的是同一类东西的不同取值，图标会让取值看着像不同类型。
 *  行与行之间那条细线由 [PickerRows] 插（与各页卡片同一档），行自己不画。
 *
 *  通栏是**故意**的：面板的圆角由库那层 shader mask 裁，行铺到边也不会出角，
 *  而两边各让出一条内缩会把选中那条带切成浮在中间的胶囊。
 *
 * @param selected 这一行是不是当前那个（多选时 = 已勾上）
 */
@Composable
internal fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    //  弹窗里这一列也算「能点的行」：默认振一下（见 `LocalTick`）
    val tick = LocalTick.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PICKER_ROW_MIN_HEIGHT)
                .background(
                    if (selected) {
                        colors.primary.copy(alpha = PICKER_ROW_TINT_ALPHA)
                    } else {
                        Color.Transparent
                    },
                ).clickable(
                    onClick = {
                        tick()
                        onClick()
                    },
                ).padding(
                    horizontal = DialogDefaults.insideMargin.width,
                    vertical = PICKER_ROW_VERTICAL_PADDING,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.body1,
            color = if (selected) colors.primary else colors.onSurface,
            //  行高由 [PICKER_ROW_MIN_HEIGHT] 定住、字在里面竖居中 —— 行盒居中不等于墨迹居中，
            //  汉字会看着偏高那一点点。与网格格 / 胶囊 / 表格单元格同一档光学修正（见 `opticalCenter`）。
            modifier = Modifier.weight(1f).opticalCenter(COUITheme.textStyles.body1),
        )
        if (selected) {
            Icon(
                PageIcons.MenuCheck,
                contentDescription = null,
                modifier = Modifier.size(ROW_END_ICON_SIZE),
                tint = colors.primary,
            )
        }
    }
}

/**
 * 一列选项 —— 行 + 行间细线 + 底部那一点面板本色。
 *
 *  单选弹窗与多选弹窗都只调它，于是「行高 / 内缩 / 分隔线 / 底部留白」只有这一处。
 *
 * ## 为什么是 `ColumnScope` 扩展、为什么自己滚
 *
 *  `FrostedDialog` 的正文槽是 `ColumnScope`，它**不替调用方兜滚动**（面板高度有界，超出部分
 *  直接被裁掉）—— 确认窗那边是 `DialogBody` 自己挂 `weight + verticalScroll` 解决的。
 *  选择器这里同理：键一多（组合键那一页能列到几十颗）后半段连同「取消 / 确定」一起被裁在面板外，
 *  点不到、也看不见。所以这一列同样挂 `weight(1f, fill = false) + verticalScroll`：
 *  先给按钮栏让位、行少时不把面板撑满。
 *
 *  行数少时 `weight(fill = false)` 让它维持原高度；超出可见高度才滚。
 *
 *  @param labels 每一行的文案（顺序就是显示顺序）
 *  @param isSelected 第几行是不是选中的那个（多选时 = 已勾上）
 *  @param onClick 点了第几行 —— 单选那边由调用方关窗，多选那边是切换
 */
@Composable
internal fun ColumnScope.PickerRows(
    labels: List<String>,
    isSelected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            //  最后一行下面留一点面板本色：行铺到面板底边的话，圆角那两处会显得被切平
            .padding(bottom = PICKER_ROW_BOTTOM_GAP),
    ) {
        labels.forEachIndexed { index, label ->
            //  行之间那条细线：与各页卡片里同一档（`CardList` 那条 `HorizontalDivider` +
            //  同一份两端内缩），不自己另调一道色。第一行之上不画。
            if (index > 0) {
                HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            }
            PickerRow(label = label, selected = isSelected(index)) { onClick(index) }
        }
    }
}

/**
 * **单选**选择器弹窗 —— 标题 + 一列选项，点一行就算选完（自己关窗再回调）。
 *
 *  与「系统开关」那一层（切换 / 打开 / 关闭）同一个形状：那三档是在问「这个开关怎么用」，
 *  需要看清、选完就作数，不是随手划一下的清单。所以是模态弹窗、不是第二层菜单。
 *
 * ## 为什么参数到 6 个（`@Suppress("LongParameterList")`）
 *
 *  与 `FrostedDialog` / `ConfirmDialog` 同一个理由：`show` / `title` / `onDismiss` 是弹窗自己的
 *  开关与出口，`labels` / `selectedIndex` / `onPick` 是这一列选项的三样 —— 全是行参，
 *  拆成一个 options 对象只会让调用点各多一层壳。
 *
 * @param selectedIndex 当前那一个（`-1` = 一个都不打勾）
 * @param onPick 选中第几行 —— **窗已经关了**再回调，调用方不用自己收
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.SinglePickerDialog(
    show: Boolean,
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    FrostedDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        //  选择器不磨砂：一列贴着读的选项，糊上去只是让字发虚、底下的页面从面板边缘晃过来。
        blurred = false,
    ) {
        PickerRows(
            labels = labels,
            isSelected = { it == selectedIndex },
            onClick = { index ->
                onDismiss()
                onPick(index)
            },
        )
    }
}

/**
 * **多选**选择器弹窗 —— 标题 + 一列可勾的选项 + 「取消 / 确定」。
 *
 *  行本身与单选那份一模一样（[PickerRows]），区别只在点一行不关窗、以及底部多一条按钮栏。
 *  勾几项才作数的那种决定（「这一组组合键由哪几颗键组成」）走它。
 *
 * @param hint 标题下面那句说明（`null` = 不写）
 * @param selected 已勾上的下标
 * @param confirmEnabled 「确定」能不能点（例如不足两颗时不能）
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.MultiPickerDialog(
    show: Boolean,
    title: String,
    hint: String?,
    labels: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    confirmText: String,
    confirmEnabled: Boolean,
    /**
     * 不能确定时，点那颗灰按钮要做什么（`null` = 按钮真的按不动）。
     *
     *  只说一句「为什么不能确定」——**由调用方弹那条通用消息条**，不在弹窗里另画一行：
     *  同一个应用里「被拦住」的说法只有一种形态（见 `SettingsPage.hint`）。
     */
    onBlockedClick: (() -> Unit)?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    FrostedDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        blurred = false,
    ) {
        if (hint != null) {
            Text(
                text = hint,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = DialogDefaults.insideMargin.width,
                            end = DialogDefaults.insideMargin.width,
                            bottom = PICKER_ROW_VERTICAL_PADDING,
                        ),
            )
        }
        PickerRows(
            labels = labels,
            isSelected = { it in selected },
            onClick = onToggle,
        )
        DialogButtonRow(
            negative = I18n.get(Strings.DIALOG_CANCEL, "取消"),
            positive = confirmText,
            onNegative = onDismiss,
            onPositive = onConfirm,
            positiveStyle =
                DialogPositiveStyle(
                    enabled = confirmEnabled,
                    //  禁用了也要能点一下问为什么（见 `DialogPositiveStyle.onDisabledClick`）
                    onDisabledClick = onBlockedClick,
                ),
        )
    }
}

/** 选中那一行的底色 —— 主色压一档，与「整行上主题色」那套观感一致，但不至于盖住面板本色。 */
private const val PICKER_ROW_TINT_ALPHA = 0.12f

/** 行内上下留白 —— 高度不够时靠 [PICKER_ROW_MIN_HEIGHT] 撑，两行字时也不至于贴住。 */
private val PICKER_ROW_VERTICAL_PADDING = 8.dp

/** 选择器里一行的高度下限 —— 与卡片行同一档（56dp），点起来手指不局促。 */
private val PICKER_ROW_MIN_HEIGHT = 56.dp

/** 最后一行下面那一点面板本色 —— 行铺到面板底边的话，圆角那两处会显得被切平。 */
private val PICKER_ROW_BOTTOM_GAP = 8.dp
