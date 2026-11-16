package com.lookie.opluskey.ui.state.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「常用命令」页的**编辑态** —— 顶栏那颗「编辑 / 完成」、清单里每一行的勾选框、右下角那两颗
 * （固定 / 取消固定、删除）共用这一份。
 *
 *  与 `KeysEditState` / `CommandListEditState` 同一套做法：**界面状态，不落盘**；选中集合存的是
 * `CommonCommand.token` 而不是对象 —— 每写一次盘配置都会换一份新对象，存下来的那份会过期。
 *
 *  为什么放在页面级（`object`）而不是 `remember` 在页面里：那颗「编辑 / 完成」长在**顶栏**那棵树
 *  上（`PageTopBar` 的 `PageActions`），与清单分处两个组合；只有页面级这一份能把两边接起来。
 *  返回拦截（`SettingsPage.goBack`）与离页清理（`SettingsPage.discardPageStateOf`）读的也是它。
 */
internal object CommonCommandsEditState {
    /** 现在是不是编辑态。 */
    var editing by mutableStateOf(false)

    /** 勾中的那几条（按 token 记：改名 / 换图标之后对象会换，按下标记会指错）。 */
    var picked by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 「退出编辑？」那一问的开关。
     *
     *  放在这里而不是页内 `remember`：拦住返回的判据长在 `SettingsPage` 那一层，它够不着页内的对象。
     */
    var confirmLeaveEdit by mutableStateOf(false)

    /** 一共勾了几条 —— 那两颗 FAB 与确认窗都只看它。 */
    val count: Int get() = picked.size

    /** 点一行：加进选中 / 从选中里去掉。 */
    fun toggle(token: String) {
        picked = if (token in picked) picked - token else picked + token
    }

    /**
     * 顶栏那颗「全选」：把清单里能勾的**一次勾满**，已经勾满时再按一下**收回**。
     *
     *  判据是「现在是不是已经全勾上了」（拿清单现算），不是拿缓存下来的数量比 ——
     *  清单随时在变（删了一条、「全选」的含义也跟着变）。
     */
    fun toggleAll(all: Set<String>) {
        val every = all.isNotEmpty() && picked == all
        picked = if (every) emptySet() else all
    }

    /** 顶栏那颗按钮：编辑 ⇄ 完成。退出编辑时选中一起清掉。 */
    fun toggleEditing() {
        editing = !editing
        if (!editing) clearPicked()
    }

    /** 只清选中，不动编辑态（批量操作落盘之后退出编辑那一趟用）。 */
    fun clearPicked() {
        picked = emptySet()
    }

    /**
     * 离页时整份归位（`SettingsPage.discardPageStateOf` 调）。
     *
     *  与 [toggleEditing] 分开：那个是用户点顶栏按钮的语义（编辑 ⇄ 完成），这个是「这一页走了」——
     *  编辑态、勾选、那一问的开关一起收。留着它们的后果是**跨次误删**：上次勾着几条退出去，
     *  下次进来点删除会把那几条一起删掉，而用户早忘了。
     */
    fun resetForLeave() {
        editing = false
        picked = emptySet()
        confirmLeaveEdit = false
    }
}
