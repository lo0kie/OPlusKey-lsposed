package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.model.AppTheme
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
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
                    //  只补左右：竖向留白由 `FrostedDialog` 那三区统一给
                    //  （自己再补一份，与标题 / 按钮之间就对不齐了）
                    .dialogSideMargin(),
        )
    }
}

/**
 * 弹窗底部那组**标准按钮**：左「取消」+ 右动作项，两颗平分横向空间。
 *
 * ## 为什么不再用「两半平分 + 分隔线」那一版
 *
 *  那一版贴边、无圆角、中间一条分隔线，是给「确认一个动作」的小弹窗做的；放到内容更重
 *  （编辑区 / 列表 / 表单）的弹窗里就显得过重。现在统一成 COUI 的两颗标准按钮：
 *  取消是文本按钮、动作项是填充按钮（破坏性动作填错误色），各占一半、垂直居中。
 *
 *  库的 `DialogButtonBar` 仍然用不了：它只给 negative / neutral / positive **同一套**
 *  `TextButtonColors`，分不出「取消是中性色、动作项是红/主题色」（见其 KDoc 里的实测记录）。
 *
 * @param positiveStyle 动作项那一格怎么画：是否按破坏性上色（红）、能不能点、写什么字。
 *   这几件都是「动作项自己的状态」，打成一个类只是为了让这个函数的参数个数留在
 *   detekt 的 `LongParameterList` 之下。
 * @param negative 左半边那颗的字。默认就是「取消」—— 全项目只有更新窗不这么叫（「稍后」），
 *   其余七八处逐字一样，没必要每处各抄一遍 `I18n.get(Strings.DIALOG_CANCEL, "取消")`。
 * @param onReset 最左那颗「重置」—— 不给就没有（那两颗仍是平分）。给「这一格有默认值、
 *   用户可以一键回到默认」的格子用（此刻只有提示文案模板）。它不关窗：调用方自己决定
 *   是写回去再关上，还是把输入框清空留在原地。
 */
@Composable
internal fun DialogButtonRow(
    onNegative: () -> Unit,
    onPositive: () -> Unit,
    positiveStyle: DialogPositiveStyle = DialogPositiveStyle(),
    negative: String = I18n.get(Strings.DIALOG_CANCEL, "取消"),
    onReset: (() -> Unit)? = null,
) {
    //  动作项那颗的字：默认「确定」，调用方要换时走 `positiveStyle.label`
    val positive = positiveStyle.label ?: I18n.get(Strings.DIALOG_CONFIRM, "确定")
    //  **竖向留白与左右内缩都不在这里**：竖向归 `DialogPanel`（相邻两区之间各一档
    //  [DIALOG_REGION_GAP]），左右由底部那一槽自己给（`insideMargin`）。
    //  自己再补一份，两个窗叠起来就对不齐 —— 那正是「上下留白总量不一致」的来源。
    //  **按下就振一下**：与页面里那些能点的行同一份判据（[LocalTick]）。弹窗是浮层，
    //  它自己那一层拿不到页面实例，所以这一下必须从通用通道走 —— 之前这两颗按钮是全项目
    //  唯一「点了没手感」的地方（用户口径「dialog 的点击控件也走通用振动」）。
    val tick = LocalTick.current
    val negFill = negativeFill()
    val negText = negativeText()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DIALOG_BUTTON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        //  「重置」那颗只在调用方给了 `onReset` 时出现（此刻只有「提示文案」）：三颗平分比
        //  挤到标题栏去更好按 —— 标题栏那一格已经被字数统计占着。
        onReset?.let { act -> ResetButton(act, negFill, negText) }
        TextButton(
            text = negative,
            onClick = {
                tick()
                onNegative()
            },
            modifier = Modifier.weight(1f),
            cornerRadius = DIALOG_BUTTON_RADIUS,
            minHeight = DIALOG_BUTTON_HEIGHT,
            //  底色**不用库的 `secondaryVariant`**：那是一个**绝对**色号（浅色 `#E6E6E6`、
            //  深色 20% 白），压在磨砂面板上分不出来 —— 面板在深色下本来就是比它更亮的那一档
            //  （`surfaceContainerHighest`），按钮反而比面板还暗，看着就是「取消那颗没有底」
            //  （真机口径「取消按钮和底色太接近」）。
            //  分两档取色，判据见 [negativeFill] / [negativeText]。
            colors = ButtonDefaults.textButtonColors(color = negFill, textColor = negText),
        )
        //  「禁用了但点了要有说法」：给了 `onDisabledClick` 时这一颗仍可点、只是按禁用色画 ——
        //  用户点下去就能听到「为什么不能确定」。用文本按钮画这一档：填充按钮没有「灰着还能点」
        //  的形态，硬凑要么看起来能用（填色照旧）、要么点不动。
        if (positiveStyle.enabled || positiveStyle.onDisabledClick == null) {
            Button(
                onClick = {
                    tick()
                    onPositive()
                },
                modifier = Modifier.weight(1f),
                enabled = positiveStyle.enabled,
                cornerRadius = DIALOG_BUTTON_RADIUS,
                minHeight = DIALOG_BUTTON_HEIGHT,
                colors =
                    if (positiveStyle.danger) {
                        //  **字要跟着底色走**：不给 `contentColor` 时库取的是常规内容色（深色档是近黑），
                        //  压在 `error` 红上就是「红配黑」—— 浅色模式下几乎看不清（真机反馈）。
                        //  取 `onError` 而不是写死白色：Monet 深色档的 `error` 是一档**浅**红
                        //  （tone 80），配白字同样糊；`onError` 才是与它成对的那一档（深红），
                        //  浅色档仍是白色 —— 两种主题、两种深浅都自己跟着走。
                        ButtonDefaults.buttonColors(
                            color = COUITheme.colorScheme.error,
                            contentColor = COUITheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColorsPrimary()
                    },
            ) {
                Text(positive)
            }
        } else {
            DisabledPositiveButton(
                text = positive,
                onClick = positiveStyle.onDisabledClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 按钮区最左那颗「重置」—— 只在调用方给了 `onReset` 时画（[DialogButtonRow] 的第三颗）。
 *
 *  与「取消」同一档配色：两颗都是中性动作，只有右边那颗是主操作。
 *  抽出来只为让 [DialogButtonRow] 收在 detekt 的 `LongMethod` 长度线内。
 */
@Composable
private fun RowScope.ResetButton(
    onReset: () -> Unit,
    fill: Color,
    textColor: Color,
) {
    val tick = LocalTick.current
    TextButton(
        text = I18n.get(Strings.DIALOG_RESET, "重置"),
        onClick = {
            tick()
            onReset()
        },
        modifier = Modifier.weight(1f),
        cornerRadius = DIALOG_BUTTON_RADIUS,
        minHeight = DIALOG_BUTTON_HEIGHT,
        colors = ButtonDefaults.textButtonColors(color = fill, textColor = textColor),
    )
}

/**
 * 「禁用了但点了要有说法」那一颗 —— 填充按钮没有「灰着还能点」的形态，
 * 硬凑要么看起来能用、要么点不动，所以用文本按钮画这一档。
 *
 *  抽出来只为让 [DialogButtonRow] 收在 detekt 的 `LongMethod` 长度线内。
 *  按下照样走通用振动通道：这一下虽然不执行动作，但它**有回话**（一句禁用原因）。
 */
@Composable
private fun DisabledPositiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = LocalTick.current
    TextButton(
        text = text,
        onClick = {
            tick()
            onClick()
        },
        modifier = modifier,
        cornerRadius = DIALOG_BUTTON_RADIUS,
        minHeight = DIALOG_BUTTON_HEIGHT,
        colors = ButtonDefaults.textButtonColors(textColor = COUITheme.colorScheme.disabledOnSurface),
    )
}

/** 两颗标准按钮之间的间距 —— 两颗都占一半宽，间距太小会读成一整块。 */
private val DIALOG_BUTTON_GAP = 12.dp

/**
 * 按钮高度 —— 比库默认的 44dp 收一档。
 *
 *  弹窗底那两颗是「收尾操作」，不是主行动：44dp 在小弹窗里几乎占掉四分之一屏，
 *  与上面那行内容抢注意力。40dp 仍过得了 40dp 的最小触达线。
 */
private val DIALOG_BUTTON_HEIGHT = 40.dp

/**
 * 非 Monet 档「取消」那颗的底透明度 —— 比原先那档（0.15，与库的 10% / 20% 绝对色号同一量级）
 * **再降一档**。
 *
 *  取消是「否定动作」，不该和右边那颗动作项争视线。这一档是有下限的：**别低于 0.1** ——
 *  再淡就回到用户报过的「取消按钮和底色太接近」，那颗按钮看起来像没有底。
 */
private const val DIALOG_NEGATIVE_FILL_ALPHA = 0.10f

/**
 * Monet 档「取消」那颗的不透明度 —— 角色色（`secondaryContainer`）不动，只压一档浓度。
 *
 *  Monet 那一档是**跟着壁纸取色的实色容器**，整档压在磨砂面板上比动作项还跳；压到七成之后
 *  仍是「一个色块」，只是退回次要动作的位置。压到 0.5 以下就会与 [DIALOG_NEGATIVE_FILL_ALPHA]
 *  撞上同一个毛病（看着没底）。
 */
private const val DIALOG_NEGATIVE_MONET_ALPHA = 0.7f

/**
 * 「取消」那颗的底色 —— **Monet 与其余主题档取法不同**，因为两套色板的「次要容器」不是一个量级。
 *
 *  · Monet：`secondaryContainer` 压到 [DIALOG_NEGATIVE_MONET_ALPHA]。MD3 那一档是「比页面容器
 *    整整一级」的色（浅色 tone 90 / 深色 tone 30），压在磨砂面板上差得出来，而且跟着壁纸取色；
 *    但整档太跳，收一档只留浓度。这里**不能用 `onSurface` 的半透明**：Monet 的 `onSurface` 与
 *    面板只差几级，叠上去「几乎看不出有底」（真机口径「monet 下取消按钮太淡」）。
 *  · 其余主题档：`onSurface` 的 [DIALOG_NEGATIVE_FILL_ALPHA] —— COUI 的 `secondaryContainer`
 *    只有 8% 黑，比这还淡；而 `onSurface` 叠在面板上，面板亮就压暗、暗就提亮，两种深浅都稳。
 */
@Composable
private fun negativeFill(): Color {
    val scheme = COUITheme.colorScheme
    return if (LocalAppTheme.current == AppTheme.MONET) {
        scheme.secondaryContainer.copy(alpha = DIALOG_NEGATIVE_MONET_ALPHA)
    } else {
        scheme.onSurface.copy(alpha = DIALOG_NEGATIVE_FILL_ALPHA)
    }
}

/** 「取消」那颗的字色 —— 跟着 [negativeFill] 那一档走（Monet 用成对的 `onSecondaryContainer`）。 */
@Composable
private fun negativeText(): Color {
    val scheme = COUITheme.colorScheme
    return if (LocalAppTheme.current == AppTheme.MONET) {
        scheme.onSecondaryContainer
    } else {
        scheme.onSurface
    }
}

/** 与高度配套的圆角（胶囊形）：取高度的一半，与库那套 44 / 22 的比例同源。 */
private val DIALOG_BUTTON_RADIUS = DIALOG_BUTTON_HEIGHT / 2

/**
 * 弹窗动作项（右半边那颗按钮）的两种状态。
 *
 * @param danger 按破坏性上色（红）—— 非破坏性动作给红会被读成「这是危险操作」
 * @param enabled false 时灰掉且不吃点击（「名字为空不让确定」这类判据用）
 */
internal data class DialogPositiveStyle(
    val danger: Boolean = false,
    val enabled: Boolean = true,
    /**
     * 禁用时点一下要做什么（`null` = 什么都不做，按钮真的按不动）。
     *
     *  给了这一格之后按钮**仍然可点**、但按禁用色画：用户点下去能听到「为什么还不能确定」——
     *  灰着一颗按钮、点了没反应，是最容易被当成「坏了」的形态。
     */
    val onDisabledClick: (() -> Unit)? = null,
    /**
     * 这一颗的字（`null` = 「确定」）。
     *
     *  放进来而不是留在 [DialogButtonRow] 的参数表里：动作项这一格已经有「什么颜色 / 能不能点 /
     *  点了怎么说」三件事，字是同一格的第四件 —— 挤在参数表里那一头就顶破 detekt 的
     *  `LongParameterList`（口径与 `danger` / `enabled` 打进这个类时同一个理由）。
     *  实际要换字的只有「去下载」「导入」那几处，其余沿用默认。
     */
    val label: String? = null,
)

/** 与库 `DialogContent` 同度量：标题与正文的行高（库 `DialogLineHeight = 1.29.em`）。 */
internal val DIALOG_LINE_HEIGHT = 1.29f.em

/**
 * 弹窗文字的行盒裁剪 —— **行高只用来定行距，不再计入上下间距**。
 *
 *  一行字实际占的高度是 [DIALOG_LINE_HEIGHT]，墨迹只占其中一段，剩下的白落在文字上方与下方。
 *  面板的三档间距按**区的外框**给，于是「字形到字形」看着比一档宽 —— 字号越大白得越多
 *  （标题吃的比正文多），用户看到的就是「有正文时标题离正文比别处远」。
 *
 *  `Trim` 只裁首行上方与末行下方那两段，行与行之间的行距一点不动（多行标题、正文段落都不受影响）。
 *
 *  `includeFontPadding = false` 是同一件事的另一半：行盒里的**字体框本身**还带一层上下 padding，
 *  `Trim` 只裁 `行高 − 字体框` 那部分，裁不掉嵌在框里的这层白 —— 而它同样按字号放大，
 *  光裁行高的话标题那侧仍比正文多。
 */
internal val DIALOG_LINE_HEIGHT_STYLE =
    androidx.compose.ui.text.style.LineHeightStyle(
        androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
    )

/** 与 [DIALOG_LINE_HEIGHT_STYLE] 配套：字体框不再自带上下留白。 */
internal val DIALOG_PLATFORM_STYLE =
    androidx.compose.ui.text
        .PlatformTextStyle(includeFontPadding = false)

/**
 * 弹窗各段之间的**唯一一档**竖向留白。
 *
 *  面板上沿→标题、标题→相邻的那一区（正文或按钮）、那一区→面板下沿，全部取这一个数 ——
 *  与中间有没有正文无关，所以「标题 + 按钮」与「标题 + 正文 + 按钮」量出来视觉等高。
 *  取同一个数是为了「上下留白总量一致」：两个不同的数（外沿 16 / 区内 12）看着是「里面紧外面松」，
 *  落到真机上就是「上边距比下边距少」。
 *
 *  所有权在排三区的那一层（`PageConfirmDialog.kt` 的 `DialogPanel`）：各区**不再自己补**上下边距，
 *  因为空区处两份边距会叠成两档 —— 用户看到的就是「没有正文时标题离按钮太远」。
 */
internal val DIALOG_REGION_GAP = 16.dp

/**
 * 标题与它**紧随的那一格**（如字数统计）之间的间隙。
 *
 *  取 6dp：那一格比标题小一档（10sp 对 title4），挨得太近会与标题末字黏在一起；
 *  再宽就会读成「另一条信息」—— 它本来只是这条标题的附注。
 */
internal val DIALOG_TITLE_TRAILING_GAP = 6.dp

/**
 * 弹窗正文的左右内缩 —— 用库自己那一档，正文才与标题左缘对齐。
 *
 *  库把 `content` 槽留空（好让按钮条横跨整个面板），左右要靠调用方自己让开。
 */
internal fun Modifier.dialogSideMargin(): Modifier =
    padding(
        start = DialogDefaults.insideMargin.width,
        end = DialogDefaults.insideMargin.width,
    )

/** [FocusOnShow] 最多试几帧 —— 浮层通常一两帧就挂好，十帧是给慢机器留的余量。 */
private const val FOCUS_ATTEMPTS = 10

/**
 * 弹窗一打开就把焦点交给 [focus]（输入框那一栏）。
 *
 * ## 为什么不能只等一帧
 *
 *  弹窗正文要等浮层挂上去才进组合树，`requestFocus()` 早几帧会因为「还没挂到可聚焦节点上」
 *  直接抛 `IllegalStateException`；挂上之后也可能**返回 false**（这一帧没抢到焦点）。
 *  两种情况都得再试，所以判据必须是**返回值**而不是「有没有抛异常」——
 *  先前那版只看异常、外层套 `runCatching`，于是「返回 false」被当成成功、第一帧就收工，
 *  用户看到的是「弹窗开了，光标没进去，还得再点一下输入框」。
 *
 * ## 键盘不在这里管
 *
 *  这里只负责**把焦点交过去**；「叫键盘 / 键盘被人收走后补回来」归输入框那一侧
 *  （`IconInputView` 的 `ImeGuard`）—— 那里能读到 IME 的实际高度，也拿得到 `show`。
 *
 * ## 窗口焦点要一起看
 *
 *  弹窗刚露出时这一位还是 false，此时 `requestFocus()` 抢到的是「窗口还没焦点时的那份焦点」，
 *  窗口焦点一到它就没了。把这一位放进 key：它一翻 true 就重跑一遍、重新抢焦点。
 *  （`isWindowFocused` 不是 `State`，不能 `by` —— 但在组合里读它就订阅了它。）
 *
 * ## 只抢一次
 *
 *  窗口焦点在弹窗停留期间会翻（ROM 收键盘、输入法切换、宿主那边抢一下）。以前它也是 key，
 *  于是每翻一次就重新 `requestFocus()` 一遍 —— 输入框反复「刚拿到又被丢」，
 *  观感正是**键盘反复被拉起又消失**（真机反馈）。现在抢到过一次就不再抢：
 *  真丢了焦点时用户点一下输入框就回来了，比自动抢一百遍稳。
 *
 * ## 两个弹窗交替时为什么要「等前一个收场」
 *
 *  「收集按键 → 添加按键」是两个**独立**的 `OverlayDialog`：窗口一销毁，平台会把键盘一起带走。
 *  所以调用点（`CollectKeyDialog`）**等收集窗收场之后才把命名窗的 `show` 打开** ——
 *  两个窗口不叠，这里也就没有「刚聚焦就被前一个窗的销毁收走」那一刻。
 *
 * @param show 弹窗开着没有（关着时不动）
 * @param focus 要拿焦点的那一栏
 */
@Composable
internal fun FocusOnShow(
    show: Boolean,
    focus: FocusRequester,
) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // 这一次「开窗」里抢到过没有 —— 只抢一次（见上），`show` 变了就重来
    var grantedOnce by remember(show) { mutableStateOf(false) }
    LaunchedEffect(show, windowFocused) {
        if (!show || !windowFocused || grantedOnce) return@LaunchedEffect
        var granted = false
        var attempt = 0
        while (!granted && attempt < FOCUS_ATTEMPTS) {
            withFrameNanos { }
            granted = runCatching { focus.requestFocus() }.getOrDefault(false)
            attempt++
        }
        grantedOnce = granted
        if (!granted) {
            XLog.w("弹窗输入框没拿到焦点（这一档得靠用户点一下）")
        }
    }
}

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
