package com.lookie.opluskey.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.input.hideIme
import com.lookie.opluskey.ui.input.isImeVisible
import com.lookie.opluskey.ui.model.theme.DIALOG_FROSTED_ALPHA
import com.lookie.opluskey.ui.style.frosted
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 项目自己的弹窗外壳 —— **面板 + 居中标题 + 正文槽**，库只管宿主、压暗、进出场与圆角。
 *
 *  **全项目只有这一档面板：磨砂**。确认窗、起名窗、选择器（含动作选择器）都用它 ——
 *  同一个页面里弹出来的东西长得不一样，会像两个应用的控件（用户口径「添加按键/重命名的 dialog
 *  用项目通用那个，不然没有模糊」）。以前选择器那一档是不磨砂的（库自己那块不透明底），
 *  理由是「一列贴着读的选项糊上去会发虚」；真机上两档摆在同一页里更像出了错，已收成一档。
 *
 *  全项目所有弹窗都走它（确认窗、重命名、添加按键、选择器、卡片行弹窗）。以前重命名 / 添加按键
 *  直接用库的 `OverlayDialog(title = …)`，那样面板是库画的不透明底，**磨砂没地方挂** ——
 *  同一页里确认窗是糊的、起名窗是不透明的，看着像两个应用的控件（用户口径「添加按键/重命名的
 *  dialog 用项目通用那个，不然没有模糊」）。
 *
 * ## 为什么参数多到 9 个（`@Suppress("LongParameterList")`）
 *
 *  `show` / `title` / `onDismiss` 之外，`hideImeOnShow`、`onDismissFinished`、`centered`、
 *  `titleTrailing` 都是**这一个外壳自己的开关**（键盘怎么处理、退场播完要不要回报、摆在哪、
 *  标题行尾画什么），`footer` 与 `content` 是底部 / 正文两个槽 —— 全是行参。
 *  这些拆成 options 对象只会让二十多个调用点各多一层壳；Compose 的惯例就是平铺行参。
 *  `onDismissFinished` 是「收集按键 → 添加按键」两个弹窗交替时才需要的那一格（前一个窗收场后
 *  才让后一个窗去抢焦点），按这一个理由它也不能挪进正文槽。
 *
 * ## 为什么面板与标题自己画
 *
 *  糊必须落在库那个**进出场图层里面**：库的进出场是 `DialogContentLayout` 里
 *  `contentModifier = modifier.graphicsLayer { … }` 加在 Column 上的，而 `OverlayDialog`
 *  的 `modifier` 参数在它**外面** —— 挂在那儿就落在动画图层外面：开是一下子出现、
 *  关是一下子消失、全程钉住不动。所以把面板那一串绘制挪进 `content` 槽（它在 Column 里面）。
 *  库仍然负责宿主、压暗、进出场、返回与圆角 —— `squircleSurface` 走的是 shader mask，
 *  会把我们这个盒子一并裁成面板那个圆角，所以这里不必自己裁形状。
 *  代价：标题得自己画，三区的度量也归我们（见 `DialogPanel` 与 [DIALOG_REGION_GAP]）。
 *
 * @param content 正文槽 —— 它是 `ColumnScope`。高度超出剩余空间时由 `DialogPanel` 压到剩余高度，
 *   正文自己的滚动留在正文里（见 [DialogBody] 与 `PickerRows`）；这一层不会把窗撑出可视区。
 */
@Composable
internal fun SettingsPage.FrostedDialog(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    /**
     * 弹窗露出时先把软键盘收掉。
     *
     *  **带输入框的弹窗必须传 `false`**：键盘由输入框那侧的 `ImeGuard` 负责叫，
     *  而这里那句 `hideIme` 是「show 变 true 那一帧」跑的 —— 它要是晚于叫键盘，
     *  输入框拿到的焦点当场又被收走（真机反馈「聚焦后又自动失焦」）。
     *  这个行为本来是给**没有输入**的确认窗写的（那种窗弹出来时键盘还挂在下面，
     *  会跟它抢地方）。
     *
     *  **只在「露出」那一侧做事**：关窗时收键盘是库自己的（`DialogEntry` 在 `showState`
     *  变 false 时无条件 `hide()`）—— 「收集按键」刚关、「添加按键」刚开时那一下会顺手把
     *  键盘收掉，所以命名窗的聚焦要等前者收场（见 [onDismissFinished]）。
     */
    hideImeOnShow: Boolean = true,
    /**
     * 退场动画**播完**时回调（`show` 中途翻回 true 则不回调）。
     *
     *  给「两个弹窗交替」那种流程用：收集窗收场之后才让下一个窗去抢焦点 ——
     *  库在退场动画结束时会顺手 `hide()` 键盘，早一步聚焦就会让键盘刚弹出又被收掉
     *  （聚焦那一侧见 `FocusOnShow`：它按 `show` 变化重抢一次）。
     */
    onDismissFinished: (() -> Unit)? = null,
    /**
     * 强制**屏幕居中**（库在窄屏上默认是底部滑入）。
     *
     *  只有「内容型」窗口要它（命令预览那种要看一整段文字的）；确认窗维持库的默认档 ——
     *  那些窗口的位置一直是库说了算，不在这次改动里动。
     */
    centered: Boolean = false,
    /**
     * 标题行**行尾**那一格（如输入框的字数统计）；`null` = 不占位。
     *
     *  它**叠**在标题那一行的右边线上（`align(CenterEnd)`），**不参与居中计算** ——
     *  标题永远是整行的水平中心，不会因为它多宽就往左让多少。
     *  代价是它不占位：只适合放很短的东西（字数统计那种），标题很长时两者会撞上。
     */
    titleTrailing: (@Composable () -> Unit)? = null,
    /**
     * 底部那一区（通常是 [DialogButtonRow]）；`null` = 这个窗没有底部。
     *
     *  三个区（头 / 正文 / 底）的**竖向留白归排版那一层**（`DialogPanel`）：相邻两区之间、
     *  以及面板上下沿到相邻控件，各一档 [DIALOG_REGION_GAP]；高度为 0 的那一区不参与排版、
     *  也不带走间隙。调用方只往 [content] 里放东西，**不再各自补上下边距** —— 各补一份正是
     *  「gap 一叠加、上下对不齐」的来源。
     */
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 弹之前先把软键盘收掉：确认窗是**模态**的，键盘留在下面会跟它抢地方（「选择应用」页那两句
    // 就是在搜索框聚焦、键盘露着时弹的）。走 [hideIme]（IMM 按 window token 发命令，与宿主 window
    // 自己的 `insetsController` 无关），**不清焦点** —— 关掉窗以后搜索框还能接着输入。
    //
    //  写在这款控件里而不是各调用点：这是它该有的通用行为，**每一处**调用点都要
    //  （清空配置 / 放弃命令 / 换一条命令 / 删除命令 / 选择应用那两句）。判据用 [isImeVisible]
    //  （IME inset），键盘没露着就什么都不做 ——
    //  `InputMethodManager.isActive()` 在键盘已收起时照样是 true，拿它当判据会白发命令刷日志。
    LaunchedEffect(show, hideImeOnShow) {
        if (!show || !hideImeOnShow) return@LaunchedEffect
        val host = activity ?: return@LaunchedEffect
        if (!isImeVisible(host)) return@LaunchedEffect
        XLog.w("弹窗：收键盘（$title）")
        hideIme(host)
    }
    // 页面离开时跟着一起收（见 [SettingsPage.closeOverlays]）—— 确认窗是**模态**的，
    // 留着它比留着菜单更糟：页面都走了它还在那儿等一个不会来的点击。
    OverlayCloser(show = show, onDismiss = onDismiss)
    val frostSource = chrome.pageHazeState
    OverlayDialog(
        show = show,
        title = null,
        // 面板底色由我们画（压在糊上），所以库那一层给它透明
        backgroundColor = Color.Transparent,
        //  **强制居中**：库在窗口宽度不到 840dp 时默认是**底部滑入**（见 COUI 文档
        //  「OverlayDialog → 居中呈现（大屏）」），手机上一律拿不到「页面正中的窗口」。
        //  不传这一位的调用点维持库的默认（底部滑入），需要居中的自己打开。
        largeScreen = centered,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        //  **这里不许套 `TickHapticScope`**：它把 `LocalView` 换成一个**脱离窗口**的 `View`，
        //  而弹窗正文里会出现输入框。Compose 的 `Popup`（输入框的光标 / 选区手柄就是它画的）
        //  要从 `LocalView` 上取 `ViewTreeLifecycleOwner` 与窗口 token —— 假 View 两样都没有，
        //  于是 `PopupLayout.onAttachedToWindow` 抛「Composed into the View which doesn't
        //  propagate ViewTreeLifecycleOwner!」，**崩的是宿主进程**（真机表现：在名字弹窗里点一下
        //  输入框、光标手柄一露就崩）。同一条路上，挪光标那几下按键事件也发给了假 View、等于没发。
        //
        //  库会振的那几处各自收口，不需要在弹窗这一层兜：开关走 `SwitchRow` 的 `NoSwitchHaptic`、
        //  滑杆在 `TrackSlider` 里把库的触觉关成 `None`、勾选框传的是 `onClick = null`（不响应
        //  点击也就不振）。
        DialogPanel(
            title = {
                CenteredTitle(trailing = titleTrailing) {
                    Text(
                        text = title,
                        //  一个 style 决定全部：参数里再给一遍 `lineHeight` 会与 style 里的
                        //  `lineHeightStyle` / `platformStyle` 打架（后者的裁剪就白做了）。
                        style =
                            COUITheme.textStyles.title4.copy(
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                lineHeight = DIALOG_LINE_HEIGHT,
                                lineHeightStyle = DIALOG_LINE_HEIGHT_STYLE,
                                platformStyle = DIALOG_PLATFORM_STYLE,
                                color = COUITheme.colorScheme.onBackground,
                            ),
                    )
                }
            },
            content = content,
            footer = footer,
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 糊 → 半透本色 → 内容。本色与浮层菜单共用（[popupSurfaceColor]），alpha 走弹窗
                    //  自己那一档（[DIALOG_FROSTED_ALPHA]，比菜单更透 —— 面板大、底下平，同档看不出糊）
                    // ——暗色下不能拿 `background`（纯黑）：糊的是接近纯黑的页面，纯黑底会把糊盖成一片黑，
                    //  与菜单那边同一条教训。
                    //  第二参数给矩形：形状交给库的圆角 mask（见上）。
                    .then(frostSource?.let { Modifier.frosted(it, RectangleShape) } ?: Modifier)
                    .background(popupSurfaceColor().copy(alpha = DIALOG_FROSTED_ALPHA)),
        )
    }
}

/**
 * 面板三区（标题 / 正文 / 底）的排版与留白。
 *
 * ## 判据：三处间距**恒为一档**，与「中间有几区」无关
 *
 *  面板上沿→标题、标题→相邻的那一区、相邻的那一区→面板下沿，全部是 [DIALOG_REGION_GAP]。
 *  只写「标题 + 按钮」的窗、带正文的窗、连按钮都没有的窗，量出来都是同一档 —— 视觉上等高。
 *
 * ## 为什么不能各区内缩补一档
 *
 *  每区自己上下各补一档时，两档会在**空区**处叠加：没有正文的窗，标题区下边距 + 底部区上边距
 *  = 两档，而标题上方只有一档 —— 用户看到的是「标题到按钮太远」。
 *
 * ## 所以：间隙归这一层所有，且只插在真实存在的相邻区之间
 *
 *  三区先按自然高度各量一遍，高度为 0 的那一区（正文 lambda 什么都没发射、`footer` 传了个空壳）
 *  当作不存在：不参与排版，也不带走它两侧的间隙。
 *
 *  正文仍保留「短则窗随内容长、长则吃满剩余高度」：量完标题与底部之后把剩下的给它，超出就按
 *  剩余高度重测（正文自己的滚动在它内部，见 [DialogBody]）。
 *
 *  宽度一律取 [Constraints.maxWidth]：面板永远 `fillMaxWidth`。
 */
@Composable
private fun DialogPanel(
    title: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            // 左右内缩只在这一层给一次（与库的 `insideMargin` 同值，正文左缘才与标题左缘对齐）
            Box(Modifier.fillMaxWidth().padding(horizontal = DialogDefaults.insideMargin.width)) {
                title()
            }
            Column(Modifier.fillMaxWidth()) { content() }
            if (footer != null) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = DialogDefaults.insideMargin.width),
                    contentAlignment = Alignment.Center,
                ) {
                    footer()
                }
            }
        },
        measurePolicy = DialogPanelMeasure,
    )
}

/**
 * [DialogPanel] 的测量策略。
 *
 *  单独成一个对象（不用 `Layout { }` 那个 lambda 重载）只为**同时**给出四口 intrinsic：
 *  lambda 只实现 `measure`，父级一旦请求 intrinsic 就直接抛 —— 弹窗外面还套着库的
 *  `DialogContentLayout`，它会不会请求不由我们决定。
 *
 *  子项下标固定为：0 = 标题、1 = 正文、2 = 底部（没有底部时只有两个）。
 */
private object DialogPanelMeasure : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val gap = DIALOG_REGION_GAP.roundToPx()
        val width = constraints.maxWidth
        val heightLimit = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val loose = Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = heightLimit)

        val titleRow = measurables[TITLE_ROW].measure(loose)
        val footerRow = measurables.getOrNull(FOOTER_ROW)?.measure(loose)?.takeIf { it.height > 0 }
        val body =
            measureBody(
                bodyRow = measurables[BODY_ROW],
                footerRow = footerRow,
                loose = loose,
                gap = gap,
                heightLimit = heightLimit,
                titleHeight = titleRow.height,
            )
        val bodyHeight = body?.height ?: 0
        val footerHeight = footerRow?.height ?: 0
        // 上下一档固定，另外每有一个真实存在的相邻区就多一档
        val gaps = PANEL_EDGE_GAPS + (if (bodyHeight > 0) 1 else 0) + (if (footerHeight > 0) 1 else 0)
        val total = titleRow.height + bodyHeight + footerHeight + gap * gaps
        return layout(width, total.coerceAtMost(heightLimit)) {
            var y = gap
            titleRow.place(0, y)
            y += titleRow.height
            if (body != null && bodyHeight > 0) {
                y += gap
                body.place(0, y)
                y += bodyHeight
            }
            if (footerRow != null && footerHeight > 0) {
                y += gap
                footerRow.place(0, y)
            }
        }
    }

    /**
     * 量正文那一区：先按自然高度量一次，放不下就压到剩余高度。
     *
     *  空正文（lambda 什么都没发射）不参与排版，也不能把「标题→正文」那一档 gap 留给它 ——
     *  否则没有正文的窗会比同高度的那几张少一档空白，标题到按钮看着比别处近。
     *
     *  `reserved` 的四档必须与 [measure] 里 `gaps` 的算法一一对应：少算一档时正文能占的空间
     *  比实际多一档，内容刚好卡在边界上的窗会被判成「装得下」、然后整块超出可视区。
     */
    private fun MeasureScope.measureBody(
        bodyRow: Measurable,
        footerRow: Placeable?,
        loose: Constraints,
        gap: Int,
        heightLimit: Int,
        titleHeight: Int,
    ): Placeable? {
        val bodyNatural = bodyRow.measure(loose).takeIf { it.height > 0 }
        val reserved =
            gap * PANEL_EDGE_GAPS +
                titleHeight +
                (if (bodyNatural != null) gap else 0) +
                (footerRow?.let { gap + it.height } ?: 0)
        val bodySpace = (heightLimit - reserved).coerceAtLeast(0)
        return bodyNatural?.let { natural ->
            if (natural.height > bodySpace) bodyRow.measure(loose.copy(maxHeight = bodySpace)) else natural
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.maxOf { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.maxOf { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = intrinsicHeight(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = intrinsicHeight(measurables, width)

    /** 与 [measure] 同一套算法：空区不带走间隙。 */
    private fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        val gap = DIALOG_REGION_GAP.roundToPx()
        val titleHeight = measurables[TITLE_ROW].minIntrinsicHeight(width)
        val bodyHeight = measurables[BODY_ROW].minIntrinsicHeight(width)
        val footerHeight = measurables.getOrNull(FOOTER_ROW)?.minIntrinsicHeight(width) ?: 0
        val gaps = PANEL_EDGE_GAPS + (if (bodyHeight > 0) 1 else 0) + (if (footerHeight > 0) 1 else 0)
        return titleHeight + bodyHeight + footerHeight + gap * gaps
    }
}

/** [DialogPanelMeasure] 里三个区的下标 —— 三处写一串数字容易串位。 */
private const val TITLE_ROW = 0
private const val BODY_ROW = 1
private const val FOOTER_ROW = 2

/** [CenteredTitleMeasure] 里两个子项的下标（尾随那一格可能不存在）。 */
private const val CENTERED_TITLE = 0
private const val CENTERED_TRAILING = 1

/** [DialogPanelMeasure] 里那两档**固定**的间距：面板上沿→标题、最后一个区→面板下沿。 */
private const val PANEL_EDGE_GAPS = 2

/**
 * 标题 + **紧随其后**的那一格（如字数统计）：标题钉在整行的水平中心，那一格跟在标题屁股后面。
 *
 * ## 为什么不能直接用 `Row`
 *
 *  把两者放进 `Row` 再整体居中，那一格多宽标题就往左偏多少 —— 用户口径「不要影响居中」。
 *
 * ## 也不能贴到行尾
 *
 *  `align(CenterEnd)` 那种叠法居中是对的，但那一格跑到面板右边线上去了，与标题隔着一整段空白，
 *  读起来像另一条信息。用户口径「标题改回要尾随」。
 *
 * ## 所以：左右各让出同样一格，再把那一格摆在标题后面
 *
 *  标题的测量宽度扣掉 `2 × (尾随宽 + 间隙)`，两边的让位**相等**，中心仍是整行中心；
 *  于是「居中」与「紧随」同时成立，不需要记录标题宽度、也不会多一次重组。
 *  代价是标题可用宽度少了两格尾随 —— 它本来只是很短的一段字（`0/200` 那种），
 *  真长到要换行时仍是居中的。
 */
@Composable
private fun CenteredTitle(
    trailing: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
) {
    Layout(
        content = {
            title()
            trailing?.invoke()
        },
        measurePolicy = CenteredTitleMeasure,
    )
}

/**
 * [CenteredTitle] 的测量策略 —— 单独成一个对象，只为**同时**给出四口 intrinsic。
 *
 *  lambda 版 `Layout { }` 只有 `measure`，父级一旦请求固有尺寸就直接抛
 *  `IllegalStateException`（与 [DialogPanelMeasure] 同一个理由：这张标题外面还套着库的
 *  `DialogContentLayout`，它会不会问不由我们决定）。
 *
 *  子项下标固定：0 = 标题、1 = 尾随那一格（没有时只有 0）。
 */
private object CenteredTitleMeasure : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val gap = DIALOG_TITLE_TRAILING_GAP.roundToPx()
        val trailingMeasured =
            measurables.getOrNull(CENTERED_TRAILING)?.measure(Constraints(maxWidth = constraints.maxWidth))
        val reserve = trailingMeasured?.let { it.width + gap } ?: 0
        val titlePlaceable =
            measurables[CENTERED_TITLE].measure(
                constraints.copy(
                    minWidth = 0,
                    maxWidth = (constraints.maxWidth - reserve * 2).coerceAtLeast(0),
                ),
            )
        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                titlePlaceable.width + reserve
            }
        val height = maxOf(titlePlaceable.height, trailingMeasured?.height ?: 0)
        return layout(width, height) {
            //  标题落在整行正中（让位左右相等 ⇒ 中心不变）
            val titleX = (width - titlePlaceable.width) / 2
            titlePlaceable.place(titleX, (height - titlePlaceable.height) / 2)
            //  尾随那一格紧随标题，垂直与标题同一中线
            trailingMeasured?.place(
                titleX + titlePlaceable.width + gap,
                (height - trailingMeasured.height) / 2,
            )
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = intrinsicWidth(measurables, height, DIALOG_TITLE_TRAILING_GAP.roundToPx()) { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = intrinsicWidth(measurables, height, DIALOG_TITLE_TRAILING_GAP.roundToPx()) { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicHeight(measurables, width, DIALOG_TITLE_TRAILING_GAP.roundToPx()) { measurable, available ->
            measurable.minIntrinsicHeight(available)
        }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int =
        intrinsicHeight(measurables, width, DIALOG_TITLE_TRAILING_GAP.roundToPx()) { measurable, available ->
            measurable.maxIntrinsicHeight(available)
        }

    /**
     * 无界宽时的那一份：标题固有宽 + 尾随那一格让出来的两倍。
     *
     * @param gap 那一档间距的**像素值** —— 由调用方（`IntrinsicMeasureScope`，它才是 `Density`）
     *   换算好传进来。这两个私有帮手没有 `Density` 接收者，自己算 `roundToPx()` 编不过。
     */
    private fun intrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
        gap: Int,
        titleWidth: (IntrinsicMeasurable) -> Int,
    ): Int {
        val trailing = measurables.getOrNull(CENTERED_TRAILING)?.maxIntrinsicWidth(height) ?: 0
        val reserve = if (trailing > 0) trailing + gap else 0
        return titleWidth(measurables[CENTERED_TITLE]) + reserve
    }

    /** 给一个宽、算高 —— 标题自己那格要让出两倍尾随宽（与 [measure] 同一套）。 */
    private fun intrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        gap: Int,
        titleHeight: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        val trailingWidth = measurables.getOrNull(CENTERED_TRAILING)?.maxIntrinsicWidth(width) ?: 0
        val reserve = if (trailingWidth > 0) trailingWidth + gap else 0
        val title = titleHeight(measurables[CENTERED_TITLE], (width - reserve * 2).coerceAtLeast(0))
        val trailing = measurables.getOrNull(CENTERED_TRAILING)?.minIntrinsicHeight(width) ?: 0
        return maxOf(title, trailing)
    }
}

/**
 * 通用确认窗：一句标题 + 一段正文 + 「取消 / 动作」两颗按钮。
 *
 * ## 为什么参数到 9 个（`@Suppress("LongParameterList")`）
 *
 *  与 [FrostedDialog] 同一个理由：这八样都是「这个窗自己长什么样、点完干什么」，
 *  平铺成行参是 Compose 的惯例；拆成 options 对象只会让二十多个调用点各多一层壳。
 *  最后那个 `onDismissFinished` 是「关掉之后才接着做下一件事」用的（目前只有「填前缀」那一处），
 *  它和 `onConfirm` 是**先后**关系而不是同一件事，不能合并。
 *  `body` 与 `summary` 是同一个槽的两条路（纯文本 / 自己画），给了 `body` 就不看 `summary`。
 */
@Composable
internal fun SettingsPage.ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    /** 动作项文案（「重置」/「删除」…）。 */
    action: String,
    positiveDanger: Boolean = true,
    /**
     * 动作项能不能点（`false` = 灰着）—— 给「前置条件不成立，这一步现在做不了」的确认窗用
     * （例如「还有人在引用这颗变量，先别删」）。
     */
    positiveEnabled: Boolean = true,
    /**
     * 动作项灰着时点一下要做什么（`null` = 按钮真的按不动）。
     *
     *  与 `DialogPositiveStyle.onDisabledClick` 同一个意思：灰着一颗按钮、点了没反应，
     *  最容易让人以为界面坏了 —— 给一句「为什么还不行」比什么都不说好。
     */
    onBlocked: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    /**
     * 退场动画**播完**时回调（`show` 中途翻回 true 则不回调）—— 见 [FrostedDialog]。
     *
     *  要「关掉之后才接着做下一件事」的调用点传它。确认窗是独立窗口，库在退场结束时
     *  会顺手把键盘收掉：**在退场途中**去聚焦 / 叫键盘，等窗一销毁平台又把键盘带走，
     *  用户看到的就是「键盘闪一下又没了」。「填前缀」那一处正是这个形状。
     */
    onDismissFinished: (() -> Unit)? = null,
    /**
     * 正文的**可组合版**（`null` = 用 [summary] 那一整段纯文本）。
     *
     *  给「正文里要画键名框 / 组合列表」那种窗用：`summary` 只收字符串，把 `[侧键]` 写进字里
     *  就成了另一套记号（这个界面里键名一直是圆角框）。两格互斥 —— 给了这个就不再看 `summary`。
     */
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    FrostedDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        //  按钮是**底部那一区**（不是正文的尾巴）：三段式里竖向留白由弹窗统一给
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                onPositive = onConfirm,
                positiveStyle =
                    DialogPositiveStyle(
                        danger = positiveDanger,
                        enabled = positiveEnabled,
                        onDisabledClick = onBlocked,
                        //  动作项那颗的字（「删除」「停止」这类动词由调用方给）
                        label = action,
                    ),
            )
        },
    ) {
        // 正文走 [DialogBody]（可滚、可收缩）
        when {
            body != null -> DialogBody(body)
            summary != null -> DialogBody(summary)
        }
    }
}
