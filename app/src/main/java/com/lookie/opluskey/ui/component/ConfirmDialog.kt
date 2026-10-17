package com.lookie.opluskey.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hideIme
import com.lookie.opluskey.ui.isImeVisible
import com.lookie.opluskey.ui.model.DIALOG_FROSTED_ALPHA
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.layout.DialogButtonBarDefaults
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

// 确认弹窗与它的按钮行。

/**
 * 确认弹窗：左侧「取消」中性色、右侧动作项上色（默认 error 红）—— 与「清空配置」
 * 用的是同一份控件。
 *
 * **弹出来时会先收掉软键盘**（键盘露着才收，判据是 IME inset；不清焦点）—— 通用于**每一处**调用点
 * （现在六处：清空配置 / 放弃命令 / 换一条命令 / 删除命令 / 「此应用无法打开」/「不支持以小窗口打开」），
 * 见下面那段 `LaunchedEffect`。注意：数量随调用点变（现在六处）——
 * 加调用点不必回来改这句话，但**别把它当成「只覆盖前四个」**。
 *
 * @param summary 正文，**认两种记号**（见 [EmphasisText]）：`**加粗**`，以及行首的 `- `（项目符号）。
 *   **用户写的东西要塞进来时先过 [escapeEmphasis]**（命令名、应用名），否则名字里的记号会被当成排版。
 *   **给 `null` 就是「没有正文」**；给空串会画出一行空白，别这么用。
 * @param positiveDanger 动作项是否按破坏性上色（红）。默认 `true`（重置 / 删除 / 放弃）；
 *   **不丢用户任何东西**的确认传 `false`，走主题色 —— 「选择应用」页那两句
 *   （「不支持以小窗口打开」/「此应用无法打开」）都是这一档，给红会读成「这个操作有危险」。
 */
@Composable
internal fun SettingsPage.ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    /** 动作项文案（「重置」/「删除」…）。 */
    action: String,
    positiveDanger: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 弹之前先把软键盘收掉：确认窗是**模态**的，键盘留在下面会跟它抢地方（「选择应用」页那两句
    // 就是在搜索框聚焦、键盘露着时弹的）。走 [hideIme]（IMM 按 window token 发命令，与宿主 window
    // 自己的 `insetsController` 无关），**不清焦点** —— 关掉窗以后搜索框还能接着输入。
    //
    //  写在这款控件里而不是各调用点：这是它该有的通用行为，**每一处**调用点都要
    //  （清空配置 / 放弃命令 / 换一条命令 / 删除命令 / 选择应用那两句）。判据用 [isImeVisible]
    //  （IME inset），键盘没露着就什么都不做 ——
    //  `InputMethodManager.isActive()` 在键盘已收起时照样是 true，拿它当判据会白发命令刷日志。
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        val host = activity ?: return@LaunchedEffect
        if (!isImeVisible(host)) return@LaunchedEffect
        XLog.w("确认弹窗：收键盘（$title）")
        hideIme(host)
    }
    // 页面离开时跟着一起收（见 [SettingsPage.closeOverlays]）—— 确认窗是**模态**的，
    // 留着它比留着菜单更糟：页面都走了它还在那儿等一个不会来的点击。
    OverlayCloser(show = show, onDismiss = onDismiss)
    // —— 面板与标题**自己画**（`title = null` + 透明底）——
    //
    //  糊必须落在库那个**进出场图层里面**：库的进出场是 `DialogContentLayout` 里
    //  `contentModifier = modifier.graphicsLayer { … }` 加在 Column 上的，而 `OverlayDialog`
    //  的 `modifier` 参数在它**外面** —— 挂在那儿就落在动画图层外面：开是一下子出现、
    //  关是一下子消失、全程钉住不动。所以把面板那一串绘制挪进 `content` 槽（它在 Column 里面）。
    //  库仍然负责宿主、压暗、进出场、返回与圆角 —— `squircleSurface` 走的是 shader mask，
    //  会把我们这个盒子一并裁成面板那个圆角，所以这里不必自己裁形状。
    //  代价：标题得自己画，度量照抄库 `DialogContent` 那一份（见本文件末尾两个常量）。
    val frostSource = pageHazeState
    OverlayDialog(
        show = show,
        title = null,
        // 面板底色也由我们画（压在糊上），所以库那一层给它透明。
        backgroundColor = Color.Transparent,
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // 糊 → 半透本色 → 内容。本色与浮层菜单共用（[popupSurfaceColor]），alpha 走弹窗
                //  自己那一档（[DIALOG_FROSTED_ALPHA]，比菜单更透 —— 面板大、底下平，同档看不出糊）
                // ——暗色下不能拿 `background`（纯黑）：糊的是接近纯黑的页面，纯黑底会把糊盖成一片黑，
                //  与菜单那边同一条教训。
                //  第二参数给矩形：形状交给库的圆角 mask（见上）。
                .then(frostSource?.let { Modifier.frosted(it, RectangleShape) } ?: Modifier)
                .background(popupSurfaceColor().copy(alpha = DIALOG_FROSTED_ALPHA)),
        ) {
            // 标题：与库 `DialogContent` 里那一份同度量（`insideMargin` 24dp 内缩、下边距 6dp、
            //  `title4` 字号、Medium、居中、1.29em 行高）——那几条在库里是 private 常量，
            //  抄的是它们的值，改库时这里要跟着看。
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DialogDefaults.insideMargin.width)
                        .padding(
                            top = DialogDefaults.insideMargin.height,
                            bottom = DIALOG_TITLE_MARGIN_BOTTOM,
                        ),
                text = title,
                fontSize = COUITheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = DIALOG_LINE_HEIGHT,
                color = COUITheme.colorScheme.onBackground,
            )
            // 正文走 [DialogBody]（可滚、可收缩；按钮栏钉在下面）
            summary?.let { DialogBody(it) }
            DialogButtonRow(
                negative = I18n.get(Strings.DIALOG_CANCEL, "取消"),
                positive = action,
                positiveDanger = positiveDanger,
                onNegative = onDismiss,
                onPositive = onConfirm,
            )
        }
    }
}

/**
 * 弹窗正文那一块 —— **可滚、可收缩**，由调用方把按钮栏钉在它下面。
 *
 *  正文走 [EmphasisText] 而不是库那个 `summary` 槽：那个槽只收纯文本，且标题与正文会被染成同一档色。
 *  短正文居中、长正文左对齐：与库 `DialogContentLayout` 自己那条口径一致（见 [isShortDialogBody]）。
 *
 *  为什么必须能滚能收：[ConfirmDialog] 那一层 `Column` 的高度是有界的（居中档被库限到窗口高的
 *  2/3，非居中档受窗口自身高度约束），而库既不给正文加滚动、也不替调用方兜底 —— 正文一长
 *  （风险提示那种整段说明）会把两颗按钮顶出可见区，用户看不到「确定」，弹窗就卡在那儿出不去。
 *  `weight` 让正文先给后面那些**非加权**子项（按钮栏）让出位置，`fill = false` 让短正文维持原高度、
 *  不把面板撑满。
 */
@Composable
private fun ColumnScope.DialogBody(text: String) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
    ) {
        EmphasisText(
            text = text,
            textAlign = if (isShortDialogBody(text)) TextAlign.Center else TextAlign.Start,
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 左右留白**自己补**：库把标题与摘要按 `insideMargin.width`（24dp）内缩，
                    //  而 `content` 槽它是不垫的 —— 不补这一步正文就贴着弹窗边（用户报的）。
                    //  用库那个值而不是写 24.dp，正文左边缘才与标题严格对齐。
                    .padding(horizontal = DialogDefaults.insideMargin.width)
                    .padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

/**
 * 弹窗底部那条**横向按钮栏**：左「取消」中性色 + 竖分隔线 + 右动作项。
 *
 * 库的 `DialogButtonBar` 只能给 negative / neutral / positive **同一套** `TextButtonColors`，
 * 分不出「取消是中性色、动作项是红/主题色」，所以整条自己拼（见其 KDoc 里的实测记录）。
 *
 * @param positiveDanger 动作项是否按破坏性上色（红）。非破坏性动作走主题色 ——
 *   「前往下载」这类给红会读成「这是危险操作」。
 */
@Composable
internal fun DialogButtonRow(
    negative: String,
    positive: String,
    positiveDanger: Boolean,
    onNegative: () -> Unit,
    onPositive: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
    ) {
        TextButton(
            text = negative,
            onClick = onNegative,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            pressScaleEnabled = false,
            cornerRadius = DialogButtonBarDefaults.ButtonCornerRadius,
            minHeight = DialogDefaults.ButtonBarMinHeight,
            colors =
                ButtonDefaults.textButtonColorsBorderless(
                    textColor = COUITheme.colorScheme.onSurfaceSecondary,
                    disabledTextColor = COUITheme.colorScheme.disabledOnSurface,
                ),
            insideMargin = DialogDefaults.ButtonBarInsideMargin,
        )
        Box(
            modifier =
                Modifier
                    .padding(
                        top = DialogDefaults.ButtonBarDividerInsetTop,
                        bottom = DialogDefaults.ButtonBarDividerInsetBottom,
                    ).width(DialogDefaults.ButtonBarDividerThickness)
                    .fillMaxHeight()
                    .background(COUITheme.colorScheme.dividerLine),
        )
        TextButton(
            text = positive,
            onClick = onPositive,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            pressScaleEnabled = false,
            cornerRadius = DialogButtonBarDefaults.ButtonCornerRadius,
            minHeight = DialogDefaults.ButtonBarMinHeight,
            colors =
                ButtonDefaults.textButtonColorsBorderless(
                    textColor =
                        if (positiveDanger) {
                            COUITheme.colorScheme.error
                        } else {
                            COUITheme.colorScheme.primary
                        },
                ),
            insideMargin = DialogDefaults.ButtonBarInsideMargin,
        )
    }
}

/** 与库 `DialogContent` 同度量：标题与正文的行高（库 `DialogLineHeight = 1.29.em`）。 */
private val DIALOG_LINE_HEIGHT = 1.29f.em

/** 与库 `DialogContent` 同度量：标题的下边距（库 `TitleMarginBottom = 6.dp`）。 */
private val DIALOG_TITLE_MARGIN_BOTTOM = 6.dp

/**
 * 弹窗正文算不算「短」—— 短则**整段居中**，长的左对齐。
 *
 * 判据只有一条：**每一段（`\n` 分开的每一行）按宽度折算后都不超过 [DIALOG_BODY_CENTER_MAX]**。
 *
 *  为什么按「每一段」而不是「整篇」：正文分几段是**版式**（作者想让人一句一行读），不是「长短」——
 *  两段短句分行之后仍该居中。真正要挡的是**长**：某一段一行放不下、要折行，居中之后残尾会吊在
 *  面板中间（比如塞进一个长应用名的「是否取消「…」以小窗口打开？」）。所以判据落在「每一行放得下」
 *  上，与正文分几段无关。
 */
internal fun isShortDialogBody(text: String): Boolean = text.lines().all { dialogBodyDisplayWidth(it) <= DIALOG_BODY_CENTER_MAX }

/**
 * 短正文居中的**上限**（按 [dialogBodyDisplayWidth] 折算的格数）—— 面板一行放得下的量级。
 *
 *  按一行算：屏宽 360dp 减掉面板两侧各 24dp 内缩 = 312dp，除以 `body2` 那个字号，一行约
 *  22 个汉字 = 44 格 —— 阈值就取这个**一行的实际上限**。
 *
 *  取**一行的实际上限**而不是留余量的保守值：留余量会把几处一行放得下的句子
 *  （`设置与已保存的命令都会被清空，**不可恢复**。` 42 格、自定义命令那份第二段 42 格）
 *  误判成长、无谓地靠左。
 */
private const val DIALOG_BODY_CENTER_MAX = 44

/**
 * 去标记之后的**显示宽度**：全角（中日韩、全角标点）算两格，其余算一格；加粗记号不占位。
 *
 * 只为上面那条判据服务，所以**只处理我们正文里出现的那几样记号**（`**` / 行首 `- ` / 反斜杠转义），
 * 不是通用解析器 —— 出现没预料到的记号时只会把句子判得偏长（退回左对齐），不影响读。
 */
private fun dialogBodyDisplayWidth(text: String): Int =
    displayWidth(
        text
            .replace("**", "")
            .removePrefix("- ")
            // 转义用的反斜杠不显示（正文里的命令名、应用名过 [escapeEmphasis]）
            .replace("\\", ""),
    )
