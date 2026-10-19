package com.lookie.opluskey.ui.screen.binding

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
import com.lookie.opluskey.ui.DialogButtonRow
import com.lookie.opluskey.ui.DialogPositiveStyle
import com.lookie.opluskey.ui.FocusOnShow
import com.lookie.opluskey.ui.FrostedDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialogSideMargin
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.command.IconInputView
import com.lookie.opluskey.ui.screen.command.InputSpec

/** 按键名能输入多少字（超出的部分在输入框里直接吃掉）。 */
internal const val MAX_KEY_NAME_LENGTH = 24

/**
 * 「重命名」弹窗 —— 一个输入框 + 取消 / 确定。
 *
 *  名字是**存下来的字面量**（见 `WatchedKey` 的说明）：这里改完就写进清单那一项，
 *  切界面语言不会再变。所以这一格是**用户自己的命名**，不是一条字典文案。
 *
 *  弹窗每次打开都以 [initial] 为起点（`remember(show, initial)`）：上一次改到一半关掉，
 *  再打开不该还挂着那半截没提交的字。
 *
 * @param initial 打开时输入框里的初值（当前那颗键的名字）
 * @param onConfirm 只在名字非空时回调，参数已去掉首尾空白
 */
@Composable
internal fun SettingsPage.RenameKeyDialog(
    show: Boolean,
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
        title = I18n.get(Strings.BINDING_RENAME_KEY, "重命名"),
        onDismiss = onDismiss,
        // 带输入框：键盘由输入框那侧的 `ImeGuard` 叫，这里不能收（收晚一步就是「聚焦后又失焦」）
        hideImeOnShow = false,
    ) {
        KeyNameBody(
            text = text,
            onTextChange = { text = it },
            // 行首与「添加按键」同一个标签（`label-important`）：两处填的都是**一个名字** ——
            //  这里换成编号牌面（`number-N`）的话，同一件事在两个入口长得不一样，
            //  而且「第几颗」在标题与副标题里都说过了。
            icon = PageIcons.LabelImportant,
            focus = focus,
            // 键盘与「失焦补焦点」需要知道弹窗关没关（见 `IconInputView` 的 `show`）
            show = show,
            onCancel = onDismiss,
            onConfirm = { onConfirm(text.trim()) },
        )
    }
}

/**
 * 「名字 + 取消 / 确定」那一块正文 —— 重命名与添加按键**共用**。
 *
 *  两处只差标题、行首图标与「确定之后干什么」（一个改名、一个落盘），正文长得必须一模一样：
 *  同一颗键的名字，从哪个入口填都该是同一个输入框、同一套长度上限。
 *
 *  状态（[text]）留给调用方：重命名要以「当前名字」为起点、添加要以「按键 N」为起点，
 *  而「关掉再打开要不要重置」也是各自弹窗的事（`remember(show, initial)` 在各自那边）。
 *
 * @param icon 行首那颗图标 —— 重命名用「正在改的那颗」的编号牌面，添加用标签（`label-important`）
 * @param show 弹窗开着没有 —— 往下透给输入框，供它的键盘守卫判断该不该动手
 *
 * ## 为什么参数到 7 个（`@Suppress("LongParameterList")`）
 *
 *  除了值 / 回调之外，`icon`、`focus`、`show` 都只是「这一格怎么画」的修饰位 —— 与 `IconInputView`
 *  自己那条同一个理由：拆成 options 对象反而让两个调用点各多一层壳，而 Compose 的写法惯例就是
 *  把这些平铺成行参。理由写在这里，不进 detekt 基线（那份基线只管历史遗留）。
 */
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage.KeyNameBody(
    text: String,
    onTextChange: (String) -> Unit,
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
            spec =
                InputSpec(
                    icon,
                    I18n.get(Strings.BINDING_KEY_NAME, "按键名称"),
                    MAX_KEY_NAME_LENGTH,
                ),
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
