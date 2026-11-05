package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「命令列表」那一页的**编辑态** —— 顶栏那颗「编辑 / 完成」、卡片上的多选框、右下角那颗「删除」
 * 共用这一份。
 *
 *  与 [KeysEditState] 同一套做法：**界面状态，不落盘**；选中集合存的是**命令名**（它是命令库的主键），
 *  不存对象 —— 每写一次盘命令库都会换一份新对象，存下来的那份会过期。
 *
 *  为什么放在页面级（`object`）而不是 `remember` 在页面里：那颗「编辑 / 完成」长在**顶栏**那棵树
 *  （`PageTopBar` 的 `PageActions`），与列表分处两个组合；只有页面级这一份能把两边接起来。
 *  返回拦截（`SettingsPage.goBack`）读的也是它。
 */
internal object CommandListEditState {
    /** 现在是不是编辑态。 */
    var editing by mutableStateOf(false)

    /** 勾中的命令名（只有编辑态下才有意义；退出即清空）。 */
    var picked by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 两个确认窗的开关 —— 放在这里而不是 `CommandUiState`：**返回拦截**（`SettingsPage.goBack`）
     * 长在页面那一层，够不着页内 `remember` 的那个对象，只有页面级这份能接起来。
     */
    var confirmDeletePicked by mutableStateOf(false)
    var confirmLeaveEdit by mutableStateOf(false)

    /** 一共勾了几条 —— 那颗「删除」与确认窗都只看它。 */
    val count: Int get() = picked.size

    /** 点一张卡：加进选中 / 从选中里去掉。 */
    fun toggle(name: String) {
        picked = if (name in picked) picked - name else picked + name
    }

    /** 顶栏那颗按钮：编辑 ⇄ 完成。退出编辑时选中一起清掉。 */
    fun toggleEditing() {
        editing = !editing
        if (!editing) picked = emptySet()
    }

    /** 只清选中，不动编辑态（落盘之后退出编辑那一趟用）。 */
    fun clearPicked() {
        picked = emptySet()
    }
}
