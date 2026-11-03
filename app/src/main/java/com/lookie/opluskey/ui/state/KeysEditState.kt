package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「按键功能」页的**编辑态** —— 顶栏那颗「编辑 / 完成」、两张清单里的多选框、右下角那颗「删除」
 * 共用这一份。
 *
 *  与 [KeyWatchState] / [ComboEditState] 同一套：**界面状态，不落盘**。选中集合存的是 id
 *  而不是对象 —— 每写一次盘配置都会换一份新对象，存下来的那份会过期。
 *
 *  ## 为什么分两格装
 *
 *  这一页上能删的东西有两类：收集来的**按键**（`Config.customKeys` 的 id）与**组合按键**
 *  （`ComboKey.id`）。两套 id 空间互不相干，混在一格里就得靠前缀区分、判据也会长出分支 ——
 *  分两格之后「删按键」与「删组合」各自只读自己那一格，删除时再一起算总数。
 *
 *  为什么放在页面级（`object`）而不是 `remember` 在页面里：那颗「编辑 / 完成」长在**顶栏**那棵树
 *  上（`PageTopBar` 的 `PageActions`），与列表分处两个组合；只有页面级这一份能把两边接起来。
 *  返回拦截的判据（`SettingsPage.needsBackInterception` / `goBack`）读的也是它。
 */
internal object KeysEditState {
    /** 现在是不是编辑态。 */
    var editing by mutableStateOf(false)

    /** 勾中的**按键** id（只有编辑态下才有意义；退出即清空）。 */
    var pickedKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 勾中的**组合按键** id。 */
    var pickedCombos by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 一共勾了几项（两类相加）—— 那颗「删除」与确认窗都只看它。 */
    val count: Int get() = pickedKeys.size + pickedCombos.size

    /** 点一颗键那一行：加进选中 / 从选中里去掉。 */
    fun toggleKey(id: String) {
        pickedKeys = if (id in pickedKeys) pickedKeys - id else pickedKeys + id
    }

    /** 点一组组合那一行（同上）。 */
    fun toggleCombo(id: String) {
        pickedCombos = if (id in pickedCombos) pickedCombos - id else pickedCombos + id
    }

    /** 顶栏那颗按钮：编辑 ⇄ 完成。退出编辑时选中一起清掉。 */
    fun toggleEditing() {
        editing = !editing
        if (!editing) clearPicked()
    }

    /** 只清选中，不动编辑态（落盘之后退出编辑那一趟用）。 */
    fun clearPicked() {
        pickedKeys = emptySet()
        pickedCombos = emptySet()
    }

    /** 离开这一页时清掉，下次进来是干净的一页。 */
    fun clear() {
        editing = false
        clearPicked()
    }
}
