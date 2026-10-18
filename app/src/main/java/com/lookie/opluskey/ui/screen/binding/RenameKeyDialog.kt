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
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.DialogButtonRow
import com.lookie.opluskey.ui.DialogPositiveStyle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.screen.command.IconInputView
import com.lookie.opluskey.ui.screen.command.InputSpec
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog

/** 按键名能输入多少字（超出的部分在输入框里直接吃掉）。 */
private const val MAX_KEY_NAME_LENGTH = 24

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
    val trimmed = text.trim()

    OverlayDialog(
        show = show,
        title = I18n.get(Strings.BINDING_RENAME_KEY, "重命名"),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 与「名称 / 命令」那两栏**同一个组件**：长度上限与计数也就跟着一起有了。
            //  清除键仍由库自己画（不传 `trailingIcon`），见上一轮那段的说明。
            IconInputView(
                spec =
                    InputSpec(
                        PageIcons.MenuCustomKey,
                        I18n.get(Strings.BINDING_KEY_NAME, "按键名称"),
                        MAX_KEY_NAME_LENGTH,
                    ),
                value = text,
                onValueChange = { text = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = DialogDefaults.insideMargin.width,
                            end = DialogDefaults.insideMargin.width,
                        ).padding(top = 4.dp),
            )
            DialogButtonRow(
                negative = I18n.get(Strings.DIALOG_CANCEL, "取消"),
                positive = I18n.get(Strings.DIALOG_CONFIRM, "确定"),
                onNegative = onDismiss,
                onPositive = { onConfirm(trimmed) },
                // 空名字没有意义（菜单里会变成一行空白），此时「确定」是灰的
                positiveStyle = DialogPositiveStyle(enabled = trimmed.isNotEmpty()),
            )
        }
    }
}
