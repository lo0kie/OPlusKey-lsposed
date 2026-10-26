package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.NameDialog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.PageIcons

/**
 * 「添加按键」弹窗 —— 听到键之后给它起个名字，点了确定才真的收进清单。
 *
 * ## 为什么不是「按下即收」
 *
 *  按下就落盘太快：误按一下就多出一颗键，还得再进菜单删一遍。用户口径是
 *  **先命名、确认再保存、取消就不添加** —— 于是这一步既是「起名」也是「确认」，
 *  一个弹窗办两件事，不再像先前那样先弹「检测到 XXX，要添加吗」再弹结果。
 *
 * ## 名字的起点
 *
 *  预填「按键 N」（N 是这一颗的序号），光标一进来就落在输入框里：想改名直接打字，
 *  不想改就直接点确定 —— 两条路都只有一步。
 *
 * @param initial 输入框的初值（`按键 N`）
 * @param onConfirm 只在名字非空时回调，参数已去掉首尾空白
 *
 *  **这个窗只在「收集窗已经收场」之后才开**（判据在调用点 [CollectKeyDialog]）：两个弹窗窗口
 *  叠着的那几百毫秒里，键盘会被前一个窗口的销毁带走一次 —— 早先靠「晚一点抢焦点」绕，
 *  绕不干净（焦点抢回来时前一个窗还没退净，键盘照样被收）。所以这里不再有 `focusReady`。
 *
 *  正文（输入框 + 取消 / 确定）与「重命名」是同一个 [NameDialog]：两处填的都是一个名字。
 */
@Composable
internal fun SettingsPage.AddKeyDialog(
    show: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    NameDialog(
        show = show,
        title = I18n.get(Strings.BINDING_ADD_KEY, "添加按键"),
        label = I18n.get(Strings.BINDING_KEY_NAME, "按键名称"),
        // 行首用标签（`label-important`）：这一格填的是**一个名字**
        icon = PageIcons.LabelImportant,
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
