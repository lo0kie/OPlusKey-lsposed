package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「组合键」那两页的界面状态 —— 现在在看哪一档、选键弹窗开着没有。
 *
 *  与 [KeyWatchState] 同一套：**界面状态，不落盘**。只存 id 不存 `ComboKey` 对象 ——
 *  每次写盘都会换一份新对象，存下来的那份会过期（改完动作回来还是旧值）。
 *
 *  为什么放在页面级（`object`）而不是 `remember` 在页面里：编辑页与列表页是**两个 Screen**，
 *  压栈 / 出栈都会重建组合，参数又带不进 `Screen` 枚举（它是个无参枚举）。
 */
internal object ComboEditState {
    /** 正在编辑哪一档组合（`null` = 没有）。 */
    var editingId by mutableStateOf<String?>(null)
        private set

    /** 「选择按键」弹窗开着没有（列表页那一行「添加组合键」点开它）。 */
    var pickKeysPending by mutableStateOf(false)

    /**
     * 列表页的**筛选**：只看含这颗键的组合（`null` = 全部）。
     *
     *  不落盘 —— 它是「这一趟想找哪一组」，不是配置的一部分。单选（菜单里打勾的那一行）。
     *  那一组被删掉 / 那颗键不再出现在任何组合里时，列表页会把它退回 `null`（见 `ComboScreen`）。
     */
    var filterKey by mutableStateOf<String?>(null)

    /**
     * 编辑页停在**哪一页**：`0` = 短按、`1` = 长按。
     *
     *  进「条件执行」时要用它：那一页编的是「某一档手势的规则」，而这颗 ⋮ 是在**当前这一页**
     *  点下去的 —— 用户停在长按页、点进去却在编短按的规则，是最自然的一种错。组合键只有这两档。
     */
    var page by mutableStateOf(0)

    fun open(id: String) {
        editingId = id
    }

    /** 离开编辑页时清掉 —— 下次进来不会先画上一档一帧。 */
    fun clear() {
        editingId = null
    }
}
