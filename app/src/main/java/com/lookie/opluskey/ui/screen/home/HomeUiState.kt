package com.lookie.opluskey.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.ui.model.DonateTarget
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 首页那几样**一口气开一个**的东西 —— 两次确认 + 捐赠那个菜单与收款码窗口。
 *
 *  收成一个对象而不是几个 `remember { }`：它们分散在「卡片之间」与「页面末尾」两处，
 *  摊开时「这个位是谁置的」得来回看；而且菜单与窗口要共用「选了哪个去处」这一份结论
 *  （`donateTarget`），分开写很容易写成两份。
 *
 */
internal class HomeUiState {
    /**
     * 「启用按键绑定」要的那次确认：true = 弹窗开着，**此时还没写盘**（点了确认才写）。
     *  只在「打开」这一侧要；关闭直接写。
     *
     *  它只服务首页那个主开关（**模块总闸**）—— ⋮ 菜单里「停用这颗键」是逐键开关，
     *  不进这道闸（拆开之后两者是两件事）。
     */
    var disclaimerPending by mutableStateOf(false)

    /**
     * 免责声明**确认之后**接着弹的那一个「温馨提醒」（模块免费、认准官方正版）。
     *
     *  与上面那次确认分开两个开关：这一条**不写盘、不拦任何操作**，两个按钮都只是关掉它；
     *  合成一个开关会让「关掉它」和「没确认免责声明」变成同一件事，总闸就跟着被卡住了。
     */
    var freeNoticePending by mutableStateOf(false)

    /** 「捐赠打赏」那一行的锚点 —— 菜单的落点由它给。 */
    val donateClick = PreciseClickState()

    /**
     * 「按键」卡末行那个「添加按键」弹的收集窗 —— 开着就为 true。
     *
     *  与菜单里那一项**同一套流程**（`CollectKeyDialog`：等按键 → 起名字 → 落盘），只是入口
     *  从「按键绑定」页的 ⋮ 挪到了首页这张清单上。
     */
    var collectKeyPending by mutableStateOf(false)

    var showDonateMenu by mutableStateOf(false)

    /** 选中的捐赠去处（非 `null` = 收款码窗口开着）。 */
    var donateTarget by mutableStateOf<DonateTarget?>(null)
}
