package com.lookie.opluskey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.ui.model.COLOR_FADE_SPEC
import com.lookie.opluskey.ui.model.COLOR_TRANSITION_SPEC
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.opticalCenter
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
 * 选择器里的一行 —— 文案 + 能不能点。
 *
 *  **不带行首字形**（用户口径）：这一列本来就是「同一件事的不同取值」（亮屏 / 锁屏、开 / 关），
 *  每行再挂一颗图标会把「一列同族取值」读成「几种不同的东西」；那一列的字形在**列表那一行**
 *  上已经有了（见 `conditionLineIcon`），弹窗里重复一遍只是噪声。
 */
internal class PickerOption(
    val label: String,
    /** `false` = 置灰（点了不响应）：这一档在这一趟里不可选（例如取值已经用过了）。 */
    val enabled: Boolean = true,
)

/**
 * [PickerRow] 上那一样零碎 —— 这一层窗还在不在（退场期间不派发）。
 *
 *  `enabled` 不并进来：它决定这一行长什么样（灰字），`interactive` 只决定派不派发点击 ——
 *  两件事，混成一个布尔会让「置灰」与「退场」互相顶掉。
 */
internal class PickerRowTraits(
    val interactive: Boolean = true,
)

/**
 * 画 [PickerOption] 那一行 —— **整行通栏可点**，标题左起、选中的行行尾一个对勾。
 *
 *  不挂行首图标以外的装饰：这一列说的是同一类东西的不同取值，多挂图标会让取值看着像不同类型。
 *  行与行之间那条细线由 [PickerRows] 插（与各页卡片同一档），行自己不画。
 *
 *  通栏是**故意**的：面板的圆角由库那层 shader mask 裁，行铺到边也不会出角，
 *  而两边各让出一条内缩会把选中那条带切成浮在中间的胶囊。
 *
 * @param selected 这一行是不是当前那个（多选时 = 已勾上）
 * @param enabled 这一行能不能点（`false` = 置灰，**并且收不到点击** —— 可点性挂在 `clickable` 上）。
 *   所以「为什么点不动」这类回执不能写在 `onClick` 里，要写在行上的字里（见「新增配置」已满那一档）。
 * @param traits 行首字形与「这一层窗还在不在」（退场期间不派发但**不置灰**）—— 见 [PickerRowTraits]。
 */
@Composable
internal fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    traits: PickerRowTraits = PickerRowTraits(),
) {
    val colors = COUITheme.colorScheme
    //  弹窗里这一列也算「能点的行」：默认振一下（见 `LocalTick`）
    val tick = LocalTick.current
    //  选中是**在这一层就地发生**的（多选窗点一行不关窗，勾 / 取消勾都看得见）：
    //  底色、文字色、行尾那枚对勾三样一起渐变 / 淡入，与别处状态色同一条曲线。
    val selectedFill by animateColorAsState(
        targetValue =
            if (selected) {
                colors.primary.copy(alpha = PICKER_ROW_TINT_ALPHA)
            } else {
                Color.Transparent
            },
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "PickerRowFill",
    )
    val labelColor by animateColorAsState(
        targetValue = pickerRowLabelColor(selected, enabled),
        animationSpec = COLOR_TRANSITION_SPEC,
        label = "PickerRowLabel",
    )
    //  对勾那一格**恒占位**（淡出到 0 也在）：行尾突然多出一颗字会把文字那列挤窄、
    //  长名字当场重排一次。留一格之后只有它自己在淡。
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = COLOR_FADE_SPEC,
        label = "PickerRowCheck",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PICKER_ROW_MIN_HEIGHT)
                .background(selectedFill)
                .clickable(
                    //  **颜色仍按 `enabled`**：退场期间只是不派发，不把整列刷成灰
                    enabled = enabled && traits.interactive,
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
        PickerRowContent(
            label = label,
            labelColor = labelColor,
            checkAlpha = checkAlpha,
            checkTint = colors.primary,
        )
    }
}

/**
 * [PickerRow] 那一行的内容（文案 + 行尾对勾）—— 行壳与可点性留在 [PickerRow]。
 *
 *  行首**不留图标列**（用户口径）：文案从行内边距直接起，标题左缘就是 [PickerRows] 那条
 *  分隔线的起点（两者同一个数，见那边的注释）。
 */
@Composable
private fun RowScope.PickerRowContent(
    label: String,
    labelColor: Color,
    checkAlpha: Float,
    checkTint: Color,
) {
    Text(
        text = label,
        style = COUITheme.textStyles.body1,
        color = labelColor,
        //  行高由 [PICKER_ROW_MIN_HEIGHT] 定住、字在里面竖居中 —— 行盒居中不等于墨迹居中，
        //  汉字会看着偏高那一点点。与网格格 / 胶囊 / 表格单元格同一档光学修正（见 `opticalCenter`）。
        modifier = Modifier.weight(1f).opticalCenter(COUITheme.textStyles.body1),
    )
    Box(Modifier.size(ROW_END_ICON_SIZE), contentAlignment = Alignment.Center) {
        Icon(
            PageIcons.MenuCheck,
            contentDescription = null,
            modifier = Modifier.size(ROW_END_ICON_SIZE).alpha(checkAlpha),
            tint = checkTint,
        )
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
 *  ## 再加一条自己的上限（[PICKER_ROWS_MAX_HEIGHT]）
 *
 *  只靠上面那两条时，这张窗的高度上限是「整块可视区」：选项一多，弹窗就长成一整屏，
 *  读起来不像弹窗、底下那页也被糊得只剩一条边。所以这一列自己封一档高 —— 到顶就滚，
 *  面板高度只随「标题 + 这一列 + 按钮栏」走，不随屏幕走。
 *
 *  @param labels 每一行的文案（顺序就是显示顺序）
 *  @param isSelected 第几行是不是选中的那个（多选时 = 已勾上）
 *  @param onClick 点了第几行 —— 单选那边由调用方关窗，多选那边是切换
 *  @param interactive 这一层窗还在不在（退场时不派发，见 [PickerRow]）
 */
@Composable
internal fun ColumnScope.PickerRows(
    labels: List<String>,
    isSelected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    interactive: Boolean = true,
) {
    PickerRows(labels.map { PickerOption(it) }, isSelected, onClick, interactive)
}

/** 带图标 / 置灰的那一版（见 [PickerOption]）—— 行怎么画仍然只有 [PickerRow] 一处。 */
@JvmName("PickerRowsWithOptions")
@Composable
internal fun ColumnScope.PickerRows(
    options: List<PickerOption>,
    isSelected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    interactive: Boolean = true,
) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            //  封一档高（理由见上面那段）：到顶之后由这一列自己滚，不再把整张窗撑到屏幕高
            .heightIn(max = PICKER_ROWS_MAX_HEIGHT)
            //  这里**不再自己补底部留白**：上下外沿那一档由 `FrostedDialog` 的三区统一给
            //  （自己再补一份，上下就一边多一边少）。
            .verticalScroll(rememberScrollState()),
    ) {
        options.forEachIndexed { index, option ->
            //  行之间那条细线：左端从**标题最左处**起、右端与左端同一个数（用户口径）。
            //  这个数就是行自己的左右内边距（[DialogDefaults.insideMargin]）——行首不再有图标列，
            //  文案正好从这一条起，于是线与字头对齐、右边也收进来一样多。
            //  不照抄 `CardList` 那条：卡片里的行首有图标列，它那份左内缩是 56dp，套到这儿会
            //  让线从半个行宽处才开始。第一行之上不画。
            if (index > 0) {
                HorizontalDivider(Modifier.padding(horizontal = DialogDefaults.insideMargin.width))
            }
            PickerRow(
                label = option.label,
                selected = isSelected(index),
                onClick = { onClick(index) },
                enabled = option.enabled,
                traits = PickerRowTraits(interactive = interactive),
            )
        }
    }
}

/**
 * **动作**选择器弹窗 —— 行长得与 [SinglePickerDialog] 一模一样（通栏纯文字、行间细线），
 * 区别只在这几行说的不是「选哪一个」而是「做哪件事」：没有对勾、也没有当前值。
 *
 *  给「对某一个东西做一件事」那种窗用（变量操作、以后的同类）：点一行就办，办完窗就收了 ——
 *  所以它是选择器那一族，不是确认窗（确认窗那两颗按钮问的是「要不要」）。
 *
 * ## 为什么参数到 6 个（`@Suppress("LongParameterList")`）
 *
 *  与 [SinglePickerDialog] 同一个理由：全是行参。`onDismissFinished` 是「这一窗后面还要接着
 *  弹下一层」时才需要的（两层窗不叠着开，理由见 `FrostedDialog` 的同名参数）。
 *
 * @param onPick 点了第几行 —— **窗已经开始收了**再回调（与 [SinglePickerDialog] 同一顺序），
 *   要用到「收场之后」的时机请传 [onDismissFinished]
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.PickerActionDialog(
    show: Boolean,
    title: String,
    labels: List<String>,
    onDismiss: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    onPick: (Int) -> Unit,
) {
    FrostedDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        PickerRows(
            labels = labels,
            //  动作没有「当前是哪一个」—— 一行都不打勾
            isSelected = { false },
            //  退场那一段不再派发（见 `PickerRow` 的 `interactive`）
            interactive = show,
            onClick = { index ->
                onDismiss()
                onPick(index)
            },
        )
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
    SinglePickerDialog(
        show = show,
        title = title,
        options = labels.map { PickerOption(it) },
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

/** 带图标 / 置灰的那一版（见 [PickerOption]）—— 壳与上面那个完全同一份。 */
@Suppress("LongParameterList")
@JvmName("SinglePickerDialogWithOptions")
@Composable
internal fun SettingsPage.SinglePickerDialog(
    show: Boolean,
    title: String,
    options: List<PickerOption>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    FrostedDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
    ) {
        PickerRows(
            options = options,
            isSelected = { it == selectedIndex },
            //  退场那一段不再派发（见 `PickerRow` 的 `interactive`）
            interactive = show,
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
        //  按钮是**底部那一区**（三段式里竖向留白由弹窗统一给）
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                onPositive = onConfirm,
                positiveStyle =
                    DialogPositiveStyle(
                        enabled = confirmEnabled,
                        //  禁用了也要能点一下问为什么（见 `DialogPositiveStyle.onDisabledClick`）
                        onDisabledClick = onBlockedClick,
                        //  动作项那颗的字（调用方可能有自己的说法，如「确定删除」）
                        label = confirmText,
                    ),
            )
        },
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
            //  退场那一段不再派发（见 `PickerRow` 的 `interactive`）
            interactive = show,
        )
    }
}

/** 选中那一行的底色 —— 主色压一档，与「整行上主题色」那套观感一致，但不至于盖住面板本色。 */
private const val PICKER_ROW_TINT_ALPHA = 0.12f

/** 选择器一行的文字色：选中 → 主色，禁用 → 禁用灰，其余 → 常规。 */
@Composable
private fun pickerRowLabelColor(
    selected: Boolean,
    enabled: Boolean,
): Color {
    val colors = COUITheme.colorScheme
    return when {
        selected -> colors.primary
        !enabled -> colors.disabledOnSecondaryVariant
        else -> colors.onSurface
    }
}

/** 行内上下留白 —— 高度不够时靠 [PICKER_ROW_MIN_HEIGHT] 撑，两行字时也不至于贴住。 */
private val PICKER_ROW_VERTICAL_PADDING = 8.dp

/**
 * 选择器那一列**自己**的高度上限 —— 取 320dp（[PICKER_ROW_MIN_HEIGHT] 下约 5~6 行）。
 *
 *  判据是「这一窗还得读得出是弹窗」：只交给 `DialogPanel` 压的话，上限就是整块可视区，
 *  选项一多（组合键「由哪几颗键组成」能列到几十颗）弹窗直接长成一整屏。320dp 与「添加命令」
 *  浮层同一档（`STEP_PICKER_MAX_HEIGHT`），超出部分由这一列自己滚 ——
 *  面板总高 ≈ 这一档 + 标题 + 按钮栏，手机上仍在屏幕中段。
 */
private val PICKER_ROWS_MAX_HEIGHT = 320.dp

/** 选择器里一行的高度下限 —— 与卡片行同一档（56dp），点起来手指不局促。 */
private val PICKER_ROW_MIN_HEIGHT = 56.dp
