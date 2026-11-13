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

    /** 「选择按键」弹窗开着没有（列表页那一行「添加组合键」点开它）。 */
    var pickKeysPending by mutableStateOf(false)

    /**
     * 列表页的**筛选**：只看含这颗键的组合（`null` = 全部）。
     *
     *  不落盘 —— 它是「这一趟想找哪一组」，不是配置的一部分。单选（菜单里打勾的那一行）。
     *  那一组被删掉 / 那颗键不再出现在任何组合里时，列表页会把它退回 `null`（见 `KeysScreen`）。
     */
    var filterKey by mutableStateOf<String?>(null)

    /**
     * 编辑页停在**哪一页**：`0` = 短按、`1` = 长按。
     *
     *  进「条件执行」时要用它：那一页编的是「某一档手势的规则」，而这颗 ⋮ 是在**当前这一页**
     *  点下去的 —— 用户停在长按页、点进去却在编短按的规则，是最自然的一种错。组合键只有这两档。
     */
    var page by mutableStateOf(0)

    /**
     * 这两档手势各自**在编哪一种按法**（[ComboKey.HOLD_WINDOW] / `HOLD_FIRST` / `HOLD_SECOND`）。
     *
     *  为什么是「每档一份」而不是一位共用：按法是**手势底下的一层**（同一档短按有三种按法），
     *  两档各自停在自己那张动作表上；共用一位会让「在短按页选了先按 A」把长按页也一起换掉，
     *  而人只是想在长按页看窗口那一套。
     *
     *  为什么要在页面级：与 [page] 同一条理由 —— 换按法会把整个 `BindingTarget` 换掉
     *  （网格按它读写那一套动作），而分页器里的两页是**同时在场**的，这一位必须活得比单页久。
     *
     *  不落盘：它是「这一趟在看哪一套」，不是配置的一部分（三套动作本身都在 `ComboKey` 上）。
     */
    private var holds by mutableStateOf(List(HOLD_PAGES) { ComboKey.HOLD_WINDOW })

    /** 第 [page] 档现在选的是哪一种按法。 */
    fun holdOf(page: Int): Int = holds.getOrElse(page) { ComboKey.HOLD_WINDOW }

    /** 把第 [page] 档的按法换成 [hold]。 */
    fun selectHold(
        page: Int,
        hold: Int,
    ) {
        if (holdOf(page) == hold) return
        holds = holds.toMutableList().also { it[page] = hold }
    }

    fun open(id: String) {
        editingId = id
        //  每次从列表页进来都从「等窗口」起步：那是三套里最常用的一套，带着上一次的按法进来
        //  会让人以为窗口那张表丢了（另两套要开「使用按住手势」才选得了，见 `ComboPressMode`）。
        holds = List(HOLD_PAGES) { ComboKey.HOLD_WINDOW }
    }

    /** 离开编辑页时清掉 —— 下次进来不会先画上一档一帧。 */
    fun clear() {
        editingId = null
    }
}

/**
 * 组合键编辑页有几档手势（短按 / 长按）—— 与 `BindingPagerScreen.COMBO_GESTURES` 同一条清单。
 *
 *  只用来给 [ComboEditState.holds] 定长：多一位就是白放着，少一位会让第二页的按法落回默认。
 */
private const val HOLD_PAGES = 2
