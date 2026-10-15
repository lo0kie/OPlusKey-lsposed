package com.lookie.opluskey.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.hideIme
import com.lookie.opluskey.ui.isImeVisible
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.layout.DialogButtonBarDefaults
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 确认弹窗与它的按钮行。 */

/**
 * 确认弹窗：左侧「取消」中性色、右侧动作项上色（默认 error 红）—— 与「清空配置」
 * 用的是同一份控件。
 *
 * **弹出来时会先收掉软键盘**（键盘露着才收，判据是 IME inset；不清焦点）—— 通用于**每一处**调用点
 * （现在六处：清空配置 / 放弃命令 / 换一条命令 / 删除命令 / 「此应用无法打开」/「不支持以小窗口打开」），
 * 见下面那段 `LaunchedEffect`。注意：数量随调用点变（现在六处）——
 * 加调用点不必回来改这句话，但**别把它当成「只覆盖前四个」**。
 *
 * @param summary 正文，**按 markdown 渲染**（见 [MarkdownBody]）。弹窗正文短、一处一句，
 *   所以常用到的只有 `**加粗**` 与 `- 列表` 这两样；**用户写的东西要塞进来时先过
 *   [escapeMarkdown]**（命令名、应用名），否则名字里的记号会被当成排版。
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
    // 注意：确认窗**不加磨砂**（试了三版挂法、全部撤掉，别再往回加）：
    //  库的进出场是**图层位移**（`graphicsLayer` 平移），而磨砂取样的偏移按**布局坐标**算 ——
    //   (1) 挂在面板上：动画期间糊的内容钉死在面板落定的位置，跟着窗滑走就错位
    //    （真机反馈「没有跟着面板走」「滑出后生硬消失」）；
    //   (2) 滑入期间不挂、落定后再淡入：动画期间没有糊、落定后突然出现
    //    （真机反馈「滑入时透明，结束后瞬变模糊」）；
    //   (3) 铺到**页面**那层（整页、静止）：压不过库自带的压暗（60% 黑把糊盖没），关压暗又得自己补。
    //  面板底色回库默认（不透明），不取样、不动 `enableWindowDim`。
    OverlayDialog(
        show = show,
        title = title,
        // 库默认把标题与摘要都上 `onBackground`（同一档，主次分不出来）—— 正文那一档在
        //  [MarkdownBody] 里给（它自己映射 COUI 色板），所以这里不再传库的 `summaryColor` / `summary`
        titleColor = COUITheme.colorScheme.onBackground,
        backgroundColor = DialogDefaults.backgroundColor(),
        onDismissRequest = onDismiss,
    ) {
        // 正文（原来是交给库的 `summary` 槽 —— 那个槽只收纯文本，标题与正文会被染成同一档色）。
        //  单行居中、多行左对齐：与库 `DialogContentLayout` 自己那条口径一致（见 [MarkdownBody]）。
        summary?.let { text ->
            MarkdownBody(
                text = text,
                textAlign = if (text.contains('\n')) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    // 左右留白**自己补**：库把标题与摘要按 `insideMargin.width`（24dp）内缩，
                    //  而 `content` 槽它是不垫的 —— 不补这一步正文就贴着弹窗边（用户报的）。
                    //  用库那个值而不是写 24.dp，正文左边缘才与标题严格对齐。
                    .padding(horizontal = DialogDefaults.insideMargin.width)
                    .padding(top = 4.dp, bottom = 8.dp),
            )
        }
        DialogButtonRow(
            negative = "取消",
            positive = action,
            positiveDanger = positiveDanger,
            onNegative = onDismiss,
            onPositive = onConfirm,
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
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        TextButton(
            text = negative,
            onClick = onNegative,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            pressScaleEnabled = false,
            cornerRadius = DialogButtonBarDefaults.ButtonCornerRadius,
            minHeight = DialogDefaults.ButtonBarMinHeight,
            colors = ButtonDefaults.textButtonColorsBorderless(
                textColor = COUITheme.colorScheme.onSurfaceSecondary,
                disabledTextColor = COUITheme.colorScheme.disabledOnSurface,
            ),
            insideMargin = DialogDefaults.ButtonBarInsideMargin,
        )
        Box(
            modifier = Modifier
                .padding(
                    top = DialogDefaults.ButtonBarDividerInsetTop,
                    bottom = DialogDefaults.ButtonBarDividerInsetBottom,
                )
                .width(DialogDefaults.ButtonBarDividerThickness)
                .fillMaxHeight()
                .background(COUITheme.colorScheme.dividerLine),
        )
        TextButton(
            text = positive,
            onClick = onPositive,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            pressScaleEnabled = false,
            cornerRadius = DialogButtonBarDefaults.ButtonCornerRadius,
            minHeight = DialogDefaults.ButtonBarMinHeight,
            colors = ButtonDefaults.textButtonColorsBorderless(
                textColor = if (positiveDanger) {
                    COUITheme.colorScheme.error
                } else {
                    COUITheme.colorScheme.primary
                },
            ),
            insideMargin = DialogDefaults.ButtonBarInsideMargin,
        )
    }
}
