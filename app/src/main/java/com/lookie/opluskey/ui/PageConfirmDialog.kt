package com.lookie.opluskey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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

/**
 * 项目自己的弹窗外壳 —— **面板 + 居中标题 + 正文槽**，库只管宿主、压暗、进出场与圆角。
 *
 *  面板两档：磨砂（默认，确认窗那一类）与不磨砂（`blurred = false`，库自己那块不透明底，
 *  给选择器用 —— 见该参数）。两档的标题度量、进出场、返回与页面离开时自动收掉都是同一份。
 *
 *  全项目所有弹窗都走它（确认窗、重命名、添加按键）。以前重命名 / 添加按键直接用库的
 *  `OverlayDialog(title = …)`，那样面板是库画的不透明底，**磨砂没地方挂** —— 同一页里
 *  确认窗是糊的、起名窗是不透明的，看着像两个应用的控件（用户口径「添加按键/重命名的
 *  dialog 用项目通用那个，不然没有模糊」）。
 *
 * ## 为什么参数到 6 个（`@Suppress("LongParameterList")`）
 *
 *  `show` / `title` / `onDismiss` 之外，`hideImeOnShow`、`onDismissFinished` 都是**这一个外壳
 *  自己的开关**（键盘怎么处理、退场播完要不要回报），`content` 是正文槽。这些拆成 options
 *  对象只会让二十多个调用点各多一层壳；Compose 的惯例就是平铺行参。
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
 *  代价：标题得自己画，度量照抄库 `DialogContent` 那一份（见 [DIALOG_TITLE_MARGIN_BOTTOM] 等）。
 *
 * @param content 正文槽 —— 它是 `ColumnScope`：需要「贴着按钮栏、可滚动」的正文用
 *   `Modifier.weight(1f, fill = false)`（见 [DialogBody]）。
 */
@Suppress("LongParameterList")
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
     * 面板要不要磨砂。
     *
     *  `true`（默认）：糊 + 半透本色，确认窗那一类都是它 —— 面板大、底色平，糊上去能看出「底下是我刚在看的那页」。
     *  `false`：**库自己那块不透明面板**，不挂糊。给「一整列可点的选项」那种选择器用：
     *  一行行贴着读的东西不需要糊，糊了反而把底下那页的内容透过面板边缘晃进来、字也发虚。
     */
    blurred: Boolean = true,
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
        // 磨砂那档的面板底色由我们画（压在糊上），所以库那一层给它透明；
        //  不磨砂那档**交给库**画它自己那块不透明面板（见 [blurred]）。
        backgroundColor = if (blurred) Color.Transparent else DialogDefaults.backgroundColor(),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // 糊 → 半透本色 → 内容。本色与浮层菜单共用（[popupSurfaceColor]），alpha 走弹窗
                //  自己那一档（[DIALOG_FROSTED_ALPHA]，比菜单更透 —— 面板大、底下平，同档看不出糊）
                // ——暗色下不能拿 `background`（纯黑）：糊的是接近纯黑的页面，纯黑底会把糊盖成一片黑，
                //  与菜单那边同一条教训。
                //  第二参数给矩形：形状交给库的圆角 mask（见上）。
                //  不磨砂时这两层都不挂：面板是库那块不透明的，糊上去反而把内容糊花。
                .then(
                    if (blurred) {
                        Modifier
                            .then(frostSource?.let { Modifier.frosted(it, RectangleShape) } ?: Modifier)
                            .background(popupSurfaceColor().copy(alpha = DIALOG_FROSTED_ALPHA))
                    } else {
                        Modifier
                    },
                ),
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
            content()
        }
    }
}

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
    FrostedDialog(show = show, title = title, onDismiss = onDismiss) {
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
