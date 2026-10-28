package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings

/** 一个名字最多能输多少字（超出的部分在输入框里直接吃掉）。 */
internal const val MAX_NAME_LENGTH = 24

/**
 * 「起个名字」这一类弹窗 —— **标题 + 一个输入框 + 取消 / 确定**。
 *
 *  全项目的「填一个名字」都走它：添加按键、按键重命名、配置重命名。三处只差**标题**与
 *  **输入框左边的标签**（「按键名称」/「配置名称」），正文长得必须一模一样 —— 同一件事
 *  （填一个名字）在两个入口长得不一样的话，用户会以为哪里用法不同。
 *
 *  名字是**存下来的字面量**（按键名、配置名都是用户自己起的），不是字典文案：
 *  改完就落进配置，切界面语言不会再变。
 *
 *  弹窗每次打开都以 [initial] 为起点（`remember(show, initial)`）：上一次改到一半关掉，
 *  再打开不该还挂着那半截没提交的字。
 *
 * @param title 标题（「添加按键」/「重命名」…）
 * @param label 输入框左边的标签（填的是什么名字）
 * @param initial 打开时输入框里的初值
 * @param onConfirm 只在名字非空时回调，参数已去掉首尾空白
 *
 * ## 为什么参数到 7 个（`@Suppress("LongParameterList")`）
 *
 *  `show` / `onDismiss` 是弹窗自己的开关与出口，`title` / `label` / `initial` 是这一格怎么画，
 *  `onConfirm` 是结果 —— 全是行参。拆成一个 options 对象只会让三个调用点各多一层壳，
 *  与 `FrostedDialog` / `SinglePickerDialog` 那边同一个理由。
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.NameDialog(
    show: Boolean,
    title: String,
    label: String,
    icon: ImageVector,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(show, initial) { mutableStateOf(initial) }
    // 打开即聚焦（省掉「还得再点一下输入框」那一步）
    val focus = remember { FocusRequester() }
    FocusOnShow(show, focus)

    FrostedDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        // 带输入框：键盘由输入框那侧的 `ImeGuard` 叫，这里不能收（收晚一步就是「聚焦后又失焦」）
        hideImeOnShow = false,
    ) {
        NameInputBody(
            text = text,
            onTextChange = { text = it },
            label = label,
            icon = icon,
            focus = focus,
            // 键盘与「失焦补焦点」需要知道弹窗关没关（见 `IconInputView` 的 `show`）
            show = show,
            onCancel = onDismiss,
            onConfirm = { onConfirm(text.trim()) },
        )
    }
}

/**
 * 「名字 + 取消 / 确定」那一块正文 —— [NameDialog] 与它的调用方共用。
 *
 *  状态（[text]）留给调用方：初值与「关掉再打开要不要重置」是各自弹窗的事。
 *
 * @param icon 行首那颗图标 —— 三处都用标签（`label-important`）：这一格填的是**一个名字**
 * @param show 弹窗开着没有 —— 往下透给输入框，供它的键盘守卫判断该不该动手
 *
 * ## 为什么参数到 8 个（`@Suppress("LongParameterList")`）
 *
 *  除了值 / 回调之外，`label`、`icon`、`focus`、`show` 都只是「这一格怎么画」的修饰位 ——
 *  与 `IconInputView` 自己那条同一个理由：拆成 options 对象反而让调用点各多一层壳，
 *  而 Compose 的写法惯例就是把这些平铺成行参。
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
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val trimmed = text.trim()
    Column(Modifier.fillMaxWidth()) {
        // 与「名称 / 命令」那两栏**同一个组件**：长度上限与计数也就跟着一起有了。
        //  清除键仍由库自己画（不传 `trailingIcon`）。
        IconInputView(
            spec = InputSpec(icon, label, MAX_NAME_LENGTH),
            value = text,
            onValueChange = onTextChange,
            focusRequester = focus,
            // 打开就聚焦，光标落在**末尾** —— 接着打字是追加，不是在开头插字（真机反馈过
            //  「聚焦在文字起始处」）。`show` 交给输入框自己管键盘：按电源键收集时 ROM 会
            //  在开窗后把 IME 收掉，它要在那之后补回来（见 `ImeGuard`）。
            show = show,
            caretToEndOnFirstFocus = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dialogSideMargin()
                    .padding(top = 4.dp),
        )
        DialogButtonRow(
            negative = I18n.get(Strings.DIALOG_CANCEL, "取消"),
            positive = I18n.get(Strings.DIALOG_CONFIRM, "确定"),
            onNegative = onCancel,
            onPositive = onConfirm,
            // 空名字没有意义（菜单里会变成一行空白），此时「确定」是灰的
            positiveStyle = DialogPositiveStyle(enabled = trimmed.isNotEmpty()),
        )
    }
}
