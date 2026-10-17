package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.ui.MenuPopup
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.screen.command.modeEntries
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 入口格点开的那层浮层菜单：把 [modes] 一行一行列出来（响铃四档 / 媒体五档）。
 *
 *  与「添加命令」浮层里的二级是同一份行、同一个构造入口（[modeEntries]），只是这里由格子直接触发。
 *
 *  只服务**按键绑定页**（`BindingList` 的入口格），所以从 `ui/CommandMenus.kt`
 *  搬到这一屏来 —— 那一批「页面组件」里凡是只服务一屏的，都按这个口径归位（2026-10-17）。
 *  行构造器 [modeEntries] 与命令页那张浮层共用，仍在命令侧。
 *
 * @param current 该手势当前绑的动作 —— 给菜单里那一行打勾用。
 */
@Composable
internal fun SettingsPage.TileMenuPopup(
    show: Boolean,
    preciseClick: PreciseClickState,
    /** 要列的行（[ActionCatalog.modeMenu] 取出来的那一层）。 */
    modes: List<ActionCatalog.Spec>,
    current: Action,
    onPick: (ActionCatalog.Spec) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPopup(
        show = show,
        preciseClick = preciseClick,
        onDismiss = onDismiss,
        entries =
            modeEntries(modes, tileIcons.value, current) { spec ->
                onDismiss()
                onPick(spec)
            },
    )
}
