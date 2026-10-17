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
    preciseClick: PreciseClickState,
    /**
     * 开菜单的那一格（`null` = 退场动画期间 —— 那时 `MenuPopup` 用的是它自己保住的那一份内容）。
     *
     *  行（[ActionCatalog.modeMenu]）与顶上那行小标题（这一格的标题）都从这里算：三者同源，
     *  不会出现「标题写响铃状态、列出来的是媒体控制」。
     */
    spec: ActionCatalog.Spec?,
    current: Action,
    onPick: (ActionCatalog.Spec) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuPopup(
        //  开不开由 `spec` 决定：关掉那一刻调用点传的就是 null（退场期间内容由 `MenuPopup` 自己保住）。
        show = spec != null,
        preciseClick = preciseClick,
        onDismiss = onDismiss,
        entries =
            spec
                ?.let { tile ->
                    ActionCatalog.modeMenu(tile.token)?.let { modes ->
                        modeEntries(tile.label, modes, tileIcons.value, current) { picked ->
                            onDismiss()
                            onPick(picked)
                        }
                    }
                }.orEmpty(),
    )
}
