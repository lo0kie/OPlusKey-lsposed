package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.present.ActionCatalog
import com.lookie.opluskey.ui.chrome.PageCornerFab
import com.lookie.opluskey.ui.chrome.PageFabButton
import com.lookie.opluskey.ui.model.icon.PageIcons
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 进「自定义命令」页的浮动操作按钮 —— 「按键绑定」与「组合键」两页**共用这一颗**。
 *
 *  **不跟整页的禁用态一起灰掉**（用户口径）：这一颗进的是「自定义命令」页 —— 那一页管的是
 *  命令库，不挂在某颗键的绑定上，主开关关着、或停在收集来的键上时它照样有意义。
 *  灰着却还留着（或灰着又拦点击）比直接放着更难解释。
 *
 *  **「场景动作」页不用这一颗**：那一页改的是草稿，右下角摆的是「确认」（见 [BoundConfirmFab]）。
 */
@Composable
internal fun BoxScope.BindingFab(
    visible: Boolean,
    onClick: () -> Unit,
) {
    PageCornerFab(visible = visible) {
        PageFabButton(onClick = onClick) {
            Icon(
                PageIcons.CommandAdd,
                contentDescription = I18n.get(Strings.COMMAND_SAVED_COMMANDS, "自定义命令"),
                tint = COUITheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * 把改动落盘的「确认」—— 只给**写着草稿**的那一页（「场景动作」）用。
 *
 *  与「按键绑定」「组合键」那两页刚好相反：那两页点一格子就当场写配置，而这一页改的是
 *  上一页那份整表草稿（见 `BindingTarget.Scene`）—— 落盘只有这一个入口。
 *
 *  **只在草稿脏的时候出现**（判据在调用点）：没改过就没有要写回去的东西；写完之后草稿重载、
 *  `dirty` 归零，这颗自己滑出去 —— 与「系统场景」页那颗「确认」同一套观感。
 *
 *  **不做禁用态**：按钮忽明忽暗比「按下去被告知差什么」更难理解（口径同 `BranchSaveFab`）。
 */
@Composable
internal fun BoxScope.BoundConfirmFab(
    visible: Boolean,
    onClick: () -> Unit,
) {
    PageCornerFab(visible = visible) {
        PageFabButton(onClick = onClick) {
            Icon(
                PageIcons.Confirm,
                contentDescription = I18n.get(Strings.COMMON_CONFIRM, "确定"),
                tint = COUITheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * 这一页右下角那颗 —— 「场景动作」页是「确认」，另外两页是「进自定义命令」。
 *
 * ## 场景动作页那颗
 *
 *  这一页改的是上一页那份整表草稿（见 `BindingTarget.Scene`），点一格子不落盘 ——
 *  落盘只有这一颗，**落完就退回那张表**（用户口径「确认并退出」）。
 *  显隐不跟滚动走、也不跟整份草稿的 `dirty` 走，只跟「**这一块**改没改」走
 *  （判据由调用点按 `sceneBindingDirty` 算好递进来，与返回拦截同一把尺子）。
 *
 *  两页的差别只是「哪一颗 + 显隐判据是谁」，所以收成一个参数表，调用点只剩一行。
 */
@Composable
internal fun BoxScope.BindingPageFab(
    scenePage: Boolean,
    sceneDirty: Boolean,
    scrollVisible: Boolean,
    onCommands: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (scenePage) {
        BoundConfirmFab(visible = sceneDirty, onClick = onConfirm)
    } else {
        BindingFab(visible = scrollVisible, onClick = onCommands)
    }
}

/**
 * 一个**入口格**菜单的开合状态：开着的是哪一格 + 它自己那份触点锚点。
 * 锚点跟着格子走（由格子自己 `remember`、点击时随菜单一起上报），而不是在页面这一层按
 * 「哪一格用哪一支」手写一张表 —— 那种表每加一个入口格就得补一行，**漏一个菜单就弹到别人身上**
 *（「前台转小窗」、「通知与控制中心」两次都是这么漏的）。
 *
 * @param spec 开着菜单的那一格（菜单内容按它的 token 从 [ActionCatalog.modeMenu] 取）
 * @param anchor 那一格记下的手指触点 —— 浮层就弹在那一点
 */
internal data class OpenTileMenu(
    val spec: ActionCatalog.Spec,
    val anchor: PreciseClickState,
)
