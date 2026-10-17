package com.lookie.opluskey.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.layout.DialogButtonBarDefaults
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

// 确认弹窗与它的按钮行。

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
internal fun ColumnScope.DialogBody(text: String) {
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
internal val DIALOG_LINE_HEIGHT = 1.29f.em

/** 与库 `DialogContent` 同度量：标题的下边距（库 `TitleMarginBottom = 6.dp`）。 */
internal val DIALOG_TITLE_MARGIN_BOTTOM = 6.dp

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
