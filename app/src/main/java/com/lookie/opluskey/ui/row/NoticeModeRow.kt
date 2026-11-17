package com.lookie.opluskey.ui.row

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.NoticeMode
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.dialog.MenuEntry
import com.lookie.opluskey.ui.dialog.MenuState
import com.lookie.opluskey.ui.dialog.PageMenu
import com.lookie.opluskey.ui.dialog.rememberMenuState
import com.lookie.opluskey.ui.model.icon.PageIcons
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor

// 「执行时提示」的四档 —— 菜单那一行、菜单里每一行、以及写回配置的三颗布尔，三处共用这一份。
//
//  为什么落点是「一个菜单」而不是原来那两颗开关（提示 / 流体云）：四档是**互斥的一项属性**
//  （不提示 / 通知 / Toast / 流体云），拆成两颗开关时「通知」根本没有位置，而且
//  「不提示 + 走流体云」这种组合在界面上是能摆出来的、语义却没有。菜单把它压成一格。
//
//  ## 存下来的仍是三颗布尔
//
//  四档由 `NoticeMode.of` 从 `toastOnRun` / `toastNotify` / `toastFluid` 派生，写回时三颗一起写
//  （见各页的 `setNoticeMode` 与 `OwnSettingsValues.withNoticeMode`）——
//  这样老配置不用迁移：`on=false` 就是「不提示」，`on=true, fluid=true` 就是「流体云」。

/** 四档的图标 —— 菜单里每一行的行首用它。 */
internal fun noticeModeIcon(mode: NoticeMode): ImageVector =
    when (mode) {
        //  不提示用「眼睛划掉」：与旧版命令输出那四档的「不显示」同一个读法（`PageIcons.OutputNone`）
        NoticeMode.NONE -> PageIcons.OutputNone

        NoticeMode.NOTIFY -> PageIcons.FormatNotify

        NoticeMode.TOAST -> PageIcons.FormatToast

        NoticeMode.FLUID -> PageIcons.FormatFluid
    }

/** 四档的说法 —— 菜单标题与回执都读它。 */
internal fun noticeModeLabel(mode: NoticeMode): String =
    I18n.get(
        when (mode) {
            NoticeMode.NONE -> Strings.HOME_TOAST_MODE_NONE
            NoticeMode.NOTIFY -> Strings.HOME_TOAST_MODE_NOTIFY
            NoticeMode.TOAST -> Strings.HOME_TOAST_MODE_TOAST
            NoticeMode.FLUID -> Strings.HOME_TOAST_MODE_FLUID
        },
    )

/**
 * 「执行时提示」那一行 —— 行尾那颗 [PageIcons.Selector] 读作「点了要选点什么」
 * （口径表见 `CardRow` 的 `RowEndIcon` 注释：弹一层菜单的行一律用它，不画箭头）。
 *
 *  **必须把锚点挂在这一行上**（[Modifier.preciseClickAnchor]）：浮层菜单弹在按下那一点，
 *  不记锚点时菜单只能退到默认位置 —— 与「关于」页那几行（语言 / 配色 / 导出）同一个做法。
 *
 * @param mode 当前那一档（只用于副标题；行尾固定是 selector）
 * @param anchor 这一行的触点锚点，即菜单那一份 `MenuState.anchor`
 * @param disabledHint 这一行灰着的说法（独立设置那份与整页不同，所以由调用方递进来）
 */
@Composable
internal fun SettingsPage.noticeModeRow(
    mode: NoticeMode,
    enabled: Boolean,
    anchor: PreciseClickState,
    disabledHint: String? = null,
    onClick: () -> Unit,
): CardRow =
    CardRow.Item(
        title = I18n.get(Strings.HOME_TOAST_TITLE),
        icon = PageIcons.CommandToast,
        summary = noticeModeLabel(mode),
        enabled = enabled,
        disabledHint = disabledHint,
        //  禁用时行尾那颗一并压暗 —— 由 `CardRow` 这一层给（`RowEndIcon` 读 `LocalRowEnabled`），
        //  调用点不判颜色。
        trailing = { RowEndIcon(PageIcons.Selector) },
        modifier = Modifier.preciseClickAnchor(anchor),
        onClick = onClick,
    )

/**
 * 四档菜单。
 *
 *  流体云那一行在「使用 ColorOS 接口」关着时灰着并说明原因 —— 与原来那颗开关同一条：
 *  它届时会回落 Toast（`NoticeMode.effective`），摆一颗能点的行等于让人去调一个不生效的东西。
 *
 * @param current 现在选中的那一档（打勾）
 * @param colorOsApi 这一档生效的「使用 ColorOS 接口」
 */
@Composable
internal fun SettingsPage.noticeModeMenu(
    state: MenuState,
    current: NoticeMode,
    enabled: Boolean,
    colorOsApi: Boolean,
    onPick: (NoticeMode) -> Unit,
) {
    PageMenu(state) {
        NoticeMode.entries.map { mode ->
            MenuEntry.Row(
                title = noticeModeLabel(mode),
                icon = noticeModeIcon(mode),
                selected = mode == current,
                enabled = enabled && (mode != NoticeMode.FLUID || colorOsApi),
                disabledHint =
                    if (mode == NoticeMode.FLUID && !colorOsApi) {
                        I18n.get(Strings.HOME_TOAST_FLUID_NEEDS_COLOROS)
                    } else {
                        null
                    },
                onPick = {
                    //  先收菜单再写：写盘会触发整页重组，留着菜单会闪一帧「还开着」
                    state.dismiss()
                    onPick(mode)
                },
            )
        }
    }
}

/** 菜单那一行的开合状态 —— 两个调用点（常规页 / 独立设置卡）各持一份。 */
@Composable
internal fun rememberNoticeModeMenu(): MenuState = rememberMenuState()
