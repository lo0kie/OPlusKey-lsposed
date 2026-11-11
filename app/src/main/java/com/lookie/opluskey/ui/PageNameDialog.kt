package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 一个名字最多能输多少字（超出的部分在输入框里直接吃掉）。 */
internal const val MAX_NAME_LENGTH = 24

/**
 * 「填一个值」这一类弹窗 —— **标题 + 一个输入框 + 取消 / 确定**。
 *
 *  全项目「填一个值」都走它：添加按键、按键重命名、配置重命名（填名字），以及**每一根滑杆行**
 *  （填数值，见 [range]）。各处只差**标题**、**输入框的标签**与「什么算填好了」——
 *  正文长得必须一模一样：同一件事（填一个值）在两个入口长得不一样，用户会以为哪里用法不同。
 *
 *  名字是**存下来的字面量**（按键名、配置名都是用户自己起的），不是字典文案：
 *  改完就落进配置，切界面语言不会再变。
 *
 *  弹窗每次打开都以 [initial] 为起点（`LaunchedEffect(show)` 那一下）：上一次改到一半关掉，
 *  再打开不该还挂着那半截没提交的字；而**退场那一段**不重置（否则输入框会跳回初值再消失）。
 *
 * @param title 标题（「添加按键」/「重命名」…；滑杆那一档取**原行**的标题）
 * @param label 输入框左边的标签（填的是什么名字 / 这一段的值域）
 * @param initial 打开时输入框里的初值
 * @param range 数值那一档的取值区间；`null` = 填名字。非 `null` 时：只收数字、位数按上限算、
 *   标题行尾**不画**那格小字（值域由 [hint] 常驻说明）、越界时「确定」按禁用色画且按不动
 * @param maxLength 名字那档能输多少字（默认 [MAX_NAME_LENGTH]）。比名字长的那种**一句话**
 *   （提示文案模板）单独传一个更大的数
 * @param allowEmpty 允不允许**填空**（默认不许）。给「空 = 用默认那句」的模板用：清空本身就是一种
 *   合法输入，此时「确定」不该是灰的
 * @param hint 输入框**下面**那行小字说明（`null` = 不写）。给「这一格能写什么」说不完的那几档用 ——
 *   占位文案（[label]）只在框空着时看得见，填着东西时那条规矩就消失了
 * @param onReset 给了就在按钮区多一颗「重置」（最左那颗，见 [DialogButtonRow]）。这一格有默认值
 *   时才传 —— 没有默认值的「改名」那档多一颗只会让人以为自己改过什么。
 *   **关窗与落盘都由调用方在这个回调里做**：有的档是「填回输入框」、有的是「直接存默认值并关掉」，
 *   那是每一格的语义，弹窗层猜不出来
 * @param onConfirm 只在 [range] 内（或名字非空、或 [allowEmpty]）时回调，名字那档已去掉首尾空白
 *
 * ## 为什么参数一多就 `@Suppress("LongParameterList")`
 *
 *  `show` / `onDismiss` 是弹窗自己的开关与出口，`title` / `label` / `initial` / `range` /
 *  `maxLength` / `allowEmpty` / `hint` 都是「这一格怎么画」，`onConfirm` 是结果 —— 全是行参。
 *  拆成一个 options 对象只会让调用点各多一层壳，与 `FrostedDialog` / `SinglePickerDialog`
 *  那边同一个理由。
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.NameDialog(
    show: Boolean,
    title: String,
    label: String,
    icon: ImageVector,
    initial: String,
    range: IntRange? = null,
    maxLength: Int = MAX_NAME_LENGTH,
    allowEmpty: Boolean = false,
    hint: String? = null,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    //  **只按开合重置**：把 `initial` 也当键的话，弹窗开着时外面刷新一次配置（滑杆值写回、
    //  改名同步）就会把用户正在输入的内容整段重置回初值、光标也跳走。
    //  **关闭动画期间同样不能重置** —— 键写成 `remember(show)` 时，`show` 一翻 false 就重算成
    //  初值，输入框在退场那一段会「跳回去再消失」（`PercentDialog` 那边踩过同一条）。
    //  所以值只**打开时**取一次初值：`remember` 不带键，重置交给下面那个 effect。
    var text by remember { mutableStateOf(initial) }
    LaunchedEffect(show) {
        if (show) text = initial
    }
    // 打开即聚焦（省掉「还得再点一下输入框」那一步）
    val focus = remember { FocusRequester() }
    FocusOnShow(show, focus)
    // 「什么算填好了」：名字要非空（`allowEmpty` 那档除外）；数值要落在区间内（空 / 越界都不给确定）
    val ready =
        if (range == null) {
            allowEmpty || text.trim().isNotEmpty()
        } else {
            text.toIntOrNull()?.let { it in range } == true
        }

    FrostedDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        // 带输入框：键盘由输入框那侧的 `ImeGuard` 叫，这里不能收（收晚一步就是「聚焦后又失焦」）
        hideImeOnShow = false,
        //  行尾那格只在**填名字**时画（字数统计，与「命令详情」编辑窗同一个位置、同一套）。
        //  填数值时**不画**：值域与步进已经常驻在输入框下面那行（[hint]），再把同一句区间
        //  挂到标题行尾就是两处一样的字（用户口径「现在有两个一样的文案」）。
        titleTrailing = {
            if (range == null) {
                TitleCounter(text.length, maxLength)
            }
        },
        //  按钮是**底部那一区**（三段式里竖向留白由弹窗统一给）
        footer = {
            DialogButtonRow(
                onNegative = onDismiss,
                onPositive = { onConfirm(if (range == null) text.trim() else text) },
                //  空名字 / 越界都没有意义，此时「确定」是灰的
                positiveStyle = DialogPositiveStyle(enabled = ready),
                onReset = onReset,
            )
        },
    ) {
        NameInputBody(
            text = text,
            onTextChange = { text = it },
            label = label,
            icon = icon,
            focus = focus,
            // 键盘与「失焦补焦点」需要知道弹窗关没关（见 `IconInputView` 的 `show`）
            show = show,
            maxLength = range?.last?.toString()?.length ?: maxLength,
            digitsOnly = range != null,
            hint = hint,
        )
    }
}

/**
 * 「值 + 取消 / 确定」那一块正文 —— [NameDialog] 与它的调用方共用。
 *
 *  状态（[text]）留给调用方：初值与「关掉再打开要不要重置」是各自弹窗的事。
 *
 * @param icon 行首那颗图标 —— **取触发这一格的那一行**（滑杆、提示文案、变量值、按键改名、配置改名
 *   都是这么取的）：弹窗与它来处那一行同一个记号，不必替它另挑一颗
 * @param show 弹窗开着没有 —— 往下透给输入框，供它的键盘守卫判断该不该动手
 * @param maxLength 能输多少字 —— 名字是 [MAX_NAME_LENGTH]，数值那一档按上限的位数算
 * @param digitsOnly 只收半角数字（滑杆的手动输入这一档：中文输入法、全角数字、粘贴进来的
 *   「1 500」都在这一层被筛掉）
 *
 * ## 为什么参数一多就 `@Suppress("LongParameterList")`
 *
 *  除了值 / 回调之外，`label`、`icon`、`focus`、`show`、`maxLength`、`digitsOnly`、`hint` 都只是
 *  「这一格怎么画」的修饰位 —— 与 `IconInputView` 自己那条同一个理由：拆成 options 对象反而让
 *  调用点各多一层壳，而 Compose 的写法惯例就是把这些平铺成行参。
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.NameInputBody(
    text: String,
    onTextChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    focus: FocusRequester,
    show: Boolean,
    maxLength: Int = MAX_NAME_LENGTH,
    digitsOnly: Boolean = false,
    hint: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        // 与「名称 / 命令」那两栏**同一个组件**：长度上限与计数也就跟着一起有了。
        //  清除键仍由库自己画（不传 `trailingIcon`）。
        IconInputView(
            spec = InputSpec(icon, label, maxLength),
            value = text,
            onValueChange = { raw ->
                onTextChange(if (digitsOnly) raw.filter { it in '0'..'9' } else raw)
            },
            focusRequester = focus,
            // 打开就聚焦，光标落在**末尾** —— 接着打字是追加，不是在开头插字（真机反馈过
            //  「聚焦在文字起始处」）。`show` 交给输入框自己管键盘：按电源键收集时 ROM 会
            //  在开窗后把 IME 收掉，它要在那之后补回来（见 `ImeGuard`）。
            show = show,
            caretToEndOnFirstFocus = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dialogSideMargin(),
        )
        //  输入框下面那行小字 —— 与弹窗里别的说明同一档（页面上那些行副标题也是它）：
        //  `footnote1` + 次要色。占位文案只在框空着时可见，这条规矩填着东西时也得在。
        hint?.let {
            Text(
                text = it,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                //  水平居中：这行是「这一格怎么填」的说明，不是正文的一段 —— 左对齐时它会与
                //  上面那个输入框的左边缘咬在一起，读起来像框里溢出来的字。
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .dialogSideMargin()
                        .padding(top = 8.dp),
            )
        }
    }
}
