package com.lookie.opluskey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.DIALOG_FROSTED_ALPHA
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    val frostSource = chrome.pageHazeState
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
                onNegative = onDismiss,
                onPositive = onConfirm,
                positiveStyle = DialogPositiveStyle(danger = positiveDanger),
            )
        }
    }
}
