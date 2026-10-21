package com.lookie.opluskey.ui.screen.command

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.ui.state.AppPickerState
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 分叉编辑那几张菜单的状态。
 *
 *  与 `AppPickerState` 同一条理由：**一组同生共死的字段放进一个对象** ——
 *  散着的时候「谁该在什么时候被清掉」只能靠人读代码。
 */
internal class BranchMenuState {
    /** 左列（条件）的锚点 —— 换成「点的是哪一格」那一份。 */
    var condAnchor by mutableStateOf(PreciseClickState())

    /** 右列（动作）的锚点 —— 同上。 */
    var commandAnchor by mutableStateOf(PreciseClickState())

    /** 行尾那颗「更多」菜单的锚点。 */
    var menuAnchor by mutableStateOf(PreciseClickState())

    /** 选动作菜单在说第几条；`-1` = 没开。 */
    var commandAt by mutableIntStateOf(-1)

    /** 行尾「更多」菜单在说第几条；`-1` = 没开。 */
    var menuAt by mutableIntStateOf(-1)

    /** 「删除」确认窗在问第几条；`-1` = 没在问（关着）。 */
    var deleteAt by mutableIntStateOf(-1)

    /**
     * 正在**播退场**的那一条；`-1` = 没有。
     *
     *  删除是「先播收缩、再动数据」：确认之后这一位先指过去，行从满高收到 0（[STEP_ROW_EXIT_MS] 左右），
     *  播完才真的从草稿里删。反过来的话行会在同帧消失，用户只看到「上面一行跳上来」。
     */
    var deletingAt by mutableIntStateOf(-1)

    /**
     * 条件菜单在给哪一条挑条件：[AppPickerState.NOT_FOR_BRANCH] 关着、
     * [AppPickerState.NEW_BRANCH] 新加一条、`>= 0` 改第几条。
     *
     *  **不能拿 `-1` 当「关着」** —— 那正好是 `NEW_BRANCH` 的取值，新加那一趟会把菜单判成关着。
     */
    var condAt by mutableIntStateOf(AppPickerState.NOT_FOR_BRANCH)
}
