package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 「变量清单该重读一遍了」的记号 —— 两页（「自定义变量」/「变量操作」）各自 `remember` 住一份清单，
 * 而这份清单的**来源**有两种变化方式：
 *
 *  · 自己改的：新建一颗、改一个值、删一颗（同一趟里那两页都不会自己重读）；
 *  · **换了配置**：变量跟着配置走（见 `core/config/ProfileVars`），切一份配置就换了一整张表。
 *
 *  两件事对页面的要求是同一个（重读一遍），所以共用一个计数器而不是各记一份：
 *  分开记的话，切换配置之后只有记得那一页会刷新，另一页还是上一份的变量。
 *
 *  放在 `ui/state` 而不是某一页的页级状态里：改这一位的是**配置**那一侧（`about` 包的切换动作），
 *  读它的是变量那两页 —— 谁都不该去 import 对方的页级对象。
 */
internal object VarListSignal {
    /** 变过几次 —— 页面的 `LaunchedEffect` 拿它当键（初值 0 就是首次进页那一次读盘）。 */
    var generation by mutableIntStateOf(0)
        private set

    /** 记号 +1：让在栈里的变量页下次组合时重读清单。 */
    fun bump() {
        generation++
    }
}
