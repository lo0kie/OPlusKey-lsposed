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

    /**
     * 时间区间选择窗在给哪一条挑时间段 —— 与 [condAt] 同一套取值
     * （[AppPickerState.NOT_FOR_BRANCH] = 关着、`>= 0` 改第几条）。
     *
     *  **单独一位**而不是复用 `condAt`：条件菜单与时间窗是两件事 —— 菜单那一层先要关掉
     *  （点「时间区间」时已经 `condAt = NOT_FOR_BRANCH`），窗还开着；用同一位的话，
     *  关菜单就等于把窗也关了，用户看不到输入。
     */
    var timeAt by mutableIntStateOf(AppPickerState.NOT_FOR_BRANCH)

    /**
     * 这一趟在改**这块里的第几条条件**：`>= 0` = 改那一条，`-1` = 再添一条。
     *
     *  只在 [condAt] 有效时有意义 —— 每次打开条件菜单时由菜单自己重置（见 `BranchConditionMenu`）。
     *  一块 = 一行 = 一条规则，它可以带好几条条件（**全部成立**才执行），判定与存储一直都支持，
     *  这里补的是「界面能指到第几条」。
     */
    var condIndexAt by mutableIntStateOf(-1)

    /** 时间区间窗要落到的**那一块里的第几条条件** —— 与 [condIndexAt] 同一套取值。 */
    var timeCondIndex by mutableIntStateOf(-1)

    /**
     * 删除**一条条件**的确认窗在问「哪一行的第几条」；`null` = 关着。
     *
     *  为什么不像删整行那样用两个 `Int`（`-1` 当关着）：行号与条号各自都可以是 `-1`（新加一行是
     *  `NEW_BRANCH`、没选槽位是 `-1`），两个哨兵值凑在一起分不出「关着」与「问第 0 行的第 -1 条」。
     *  这一对只在真的点了「删除」之后才有值，用一个可空对象最直接。
     */
    var deletingCond: CondRef? by mutableStateOf(null)

    /**
     * 正在**播退场**的那一条条件；`null` = 没有。
     *
     *  与删整行那一位（[deletingAt]）同一套：确认之后先让这一行收起，播完才真的从草稿里摘掉。
     *  反过来的话它同帧消失，下面的条件当场往上顶 —— 那正是用户报的那一下。
     */
    var exitingCond: CondRef? by mutableStateOf(null)

    /**
     * **刚插入**的那一条条件；`null` = 没有。
     *
     *  新插的行要先以「不可见」组合一帧、再显出来，否则 `AnimatedVisibility` 首帧就可见、
     *  不会播进场（与 `BranchRow` 里 `MutableTransitionState(!spec.fresh)` 同一个道理）。
     *  播完不清：留着它让那一行停在「可见」态，下一次插入自然覆盖。
     */
    var freshCond: CondRef? by mutableStateOf(null)

    /**
     * 收起条件菜单（并抹掉「改第几条」）—— 两处归一次，末尾谁都不许漏掉下标：
     * 留着上一位的话，下次打开会先落在「上次改的那一条」上。
     */
    fun closeConditionMenu() {
        condAt = AppPickerState.NOT_FOR_BRANCH
        condIndexAt = -1
    }
}

/** 「第 [at] 行里的第 [index] 条条件」—— 删除确认窗要记住的那一对（见 [BranchMenuState.deletingCond]）。 */
internal data class CondRef(
    val at: Int,
    val index: Int,
)
