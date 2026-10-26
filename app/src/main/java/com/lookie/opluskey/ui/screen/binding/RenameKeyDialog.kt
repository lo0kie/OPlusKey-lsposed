package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.NameDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons

/**
 * 「重命名」弹窗 —— 只差一句标题与标签，正文（输入框 + 取消 / 确定）在 [NameDialog] 里。
 *
 *  名字是**存下来的字面量**（见 `WatchedKey` 的说明）：这里改完就写进清单那一项，
 *  切界面语言不会再变。所以这一格是**用户自己的命名**，不是一条字典文案。
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
    NameDialog(
        show = show,
        title = I18n.get(Strings.BINDING_RENAME_KEY, "重命名"),
        label = I18n.get(Strings.BINDING_KEY_NAME, "按键名称"),
        // 行首与「添加按键」同一个标签（`label-important`）：两处填的都是**一个名字** ——
        //  这里换成编号牌面（`number-N`）的话，同一件事在两个入口长得不一样，
        //  而且「第几颗」在标题与副标题里都说过了。
        icon = PageIcons.LabelImportant,
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
