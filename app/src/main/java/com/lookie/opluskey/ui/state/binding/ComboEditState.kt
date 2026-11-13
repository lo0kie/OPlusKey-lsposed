package com.lookie.opluskey.ui.state.binding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.core.model.ComboKey

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

    /**
     * 「选择按键」弹窗要为**哪一类**组合选键 —— `null` = 没开。
     *
     *  值是那一类新建时的触发方式：等待组合是 [ComboKey.MODE_WAIT]，按住组合先按
     *  [ComboKey.MODE_HOLD_FIRST] 占位（选完键再问「先按住第一颗还是第二颗」）。
     *  类别在**添加那一刻**就定下来，之后不再改（方式是一档的身份，见 `ComboKey.mode`）。
     */
    var pendingKind by mutableStateOf<Int?>(null)

    /**
     * 等待组合那一列的**筛选**：只看含这颗键的等待组合（`null` = 全部）。
     *
     *  不落盘 —— 它是「这一趟想找哪一组」，不是配置的一部分。单选（菜单里打勾的那一行）。
     *  那一组被删掉 / 那颗键不再出现在该段任何组合里时，列表页会把它退回 `null`（见 `KeysScreen`）。
     *
     *  **两段各一份**：两列各自回答「这一列里有哪些」，共用一个会把另一列一起筛掉。
     */
    var waitFilter by mutableStateOf<String?>(null)

    /** 按住组合那一列的筛选 —— 同上，独立一份。 */
    var holdFilter by mutableStateOf<String?>(null)

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
