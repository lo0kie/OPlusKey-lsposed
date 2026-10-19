package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.ui.FocusOnShow
import com.lookie.opluskey.ui.FrostedDialog
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
 * @param focusReady 能不能开始抢焦点了 —— 由调用方给「上一个弹窗收场了没有」。
 *   见 `FocusOnShow` 的 `ready`：收集窗退场动画结束时库会收一次键盘，早于那一步聚焦，
 *   键盘就会出现「弹出来又被收走」的闪。
 * @param onConfirm 只在名字非空时回调，参数已去掉首尾空白
 */
@Composable
internal fun SettingsPage.AddKeyDialog(
    show: Boolean,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    focusReady: Boolean = true,
) {
    var text by remember(show, initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    FocusOnShow(show, focus, ready = focusReady)

    FrostedDialog(
        show = show,
        title = I18n.get(Strings.BINDING_ADD_KEY, "添加按键"),
        onDismiss = onDismiss,
        // 带输入框：键盘由 `ImeGuard` 叫，这里不能收（收晚一步就是「聚焦后又失焦」）
        hideImeOnShow = false,
    ) {
        KeyNameBody(
            text = text,
            onTextChange = { text = it },
            // 行首用标签（`label-important`）：这一格填的是**一个名字**
            icon = PageIcons.LabelImportant,
            focus = focus,
            // 键盘与「失焦补焦点」需要知道弹窗关没关（见 `IconInputView` 的 `show`）——
            //  「添加按键」这条路的弹窗是**按物理键之后**才开的，ROM 会在这之后把 IME 收掉
            show = show,
            // 取消 = 什么都不做：这一步还没写过配置，关掉就是没加
            onCancel = onDismiss,
            onConfirm = { onConfirm(text.trim()) },
        )
    }
}
