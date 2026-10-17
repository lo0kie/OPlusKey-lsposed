package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.state.AppPickerState

/**
 * 「选择应用」页这一趟的**加载与选择状态** —— 从 `AppPickerScreen` 的函数体里搬出来。
 *
 *  为什么要有这个类：这一页自己有七样瞬时态（加载态 / 上一轮查的是哪一档 / 查失败过没有 /
 *  下拉刷新那一对 / 本地选中 / 两张拦人的脸 / 进场定位做过没有），加上两个**派生**的清单。
 *  它们以前是 `remember { }` 摊在一个 538 行函数的开头，而**读**它们的代码在函数中段、
 *  **写**它们的代码在四段 `LaunchedEffect` 里 —— 想弄清「`loaded` 变 null 有哪几种原因」
 *  得把整个函数扫一遍。
 *
 *  注意：**页面级的 `picker`（搜索词 / 两个落盘开关 / 小窗开关）不在这里**，它是跨进页持久的
 *  那一份（见 [AppPickerState]）。这里只有「这一趟」的东西。
 */
internal class AppPickerUiState(
    private val picker: AppPickerState,
    initialSelection: String?,
) {
    /**
     * 手上的应用清单；`null` = 还没查到（正在查 / 查失败且没有缓存）。
     *
     *  刷新**不**把它打回 null（那是模式翻转才做的事）：旧清单留着，只有指示器转圈。
     */
    var loaded by mutableStateOf<List<LaunchableApp>?>(null)

    /**
     * 上一轮查的是哪个模式（`null` = 还没查过）。
     *
     * 只有模式真的翻转才把列表收起来走加载态：那一刻列表来源换了，旧清单留着是错的。
     * 进页那一次不收 —— 那是同一份清单的重查。
     */
    var lastMode by mutableStateOf<Boolean?>(null)

    /** 这一次查询失败了吗（`loadLaunchableApps` 返回 null）。 */
    var failed by mutableStateOf(false)

    /** 下拉刷新的指示器状态与重查触发键 —— 两者一起喂给同一个加载 effect。 */
    var refreshing by mutableStateOf(false)

    var refreshTick by mutableStateOf(0)

    /**
     * **本地选中**：点行只改它，右下角那颗确认键才把选择交出去。
     * 初值照当前绑定的那个（进页时先亮着它）。
     */
    var pending by mutableStateOf(initialSelection)

    /**
     * 两张拦人的脸（都是 `null` = 没在弹，同一时刻只可能开一张）：
     *  · [blockedUnopenable]：点确认键时挑中的是个**拉不起来**的应用；
     *  · [blockedSmallWindow]：在菜单里**点开「以小窗口打开」那一刻**，或点确认键时，
     *    当前选中的应用判为不支持小窗。
     *
     *  注意：这两个是**这一趟**的（页面出栈就没了），与页面级那些手动清的位不是一回事。
     */
    var blockedUnopenable by mutableStateOf<LaunchableApp?>(null)

    var blockedSmallWindow by mutableStateOf<SmallWindowBlock?>(null)

    /**
     * 小窗支持性已经问过哪个包（`null` = 没问过）。
     *
     *  进页那一次**不问**（那是「这次要编辑的动作本来就是 `window:`」，不是用户刚按下去的手）：
     *  初值在「小窗开关开着」时就直接记成「已经问过进页那个选中项」。
     */
    var askedPkg by mutableStateOf(if (picker.smallWindow.value) initialSelection else null)

    /** 「进场滚到已选中那一行」做过了没有 —— 一次性闸，之后用户滚到别处不再被拽回来。 */
    var located by mutableStateOf(false)

    /** 手上的清单（`null` 时按空表用）。 */
    val apps: List<LaunchableApp> get() = loaded.orEmpty()

    /**
     * 过滤之后要画的清单：两个落盘开关 + 搜索词（名字 / 包名 / 拼音）。
     *
     *  用 `derivedStateOf` 而不是在调用点 `remember`：这一段被三处读（列表、空态文案、进场定位），
     *  各自 `remember` 会各算一份，而「哪些行该出现」必须三处一致。
     */
    val shown: List<LaunchableApp> by derivedStateOf {
        val filter = picker.filter.value
        val showBroken = picker.showUnopenable.value
        // 拼音那一档要小写比：排序键是罗马化后的小写串（见 `LaunchableApp.sortKey`）
        val needle = filter.lowercase()
        loaded
            .orEmpty()
            .asSequence()
            .filter { showBroken || it.launchable }
            .filter {
                filter.isBlank() ||
                    it.label.contains(filter, ignoreCase = true) ||
                    it.packageName.contains(filter, ignoreCase = true) ||
                    // 输拼音也认（`shezhi` → 设置）：名字与包名里都没有那串字母，
                    //  只比前两项等于把「按读音找」这条路整个关掉。
                    (needle.isNotEmpty() && it.sortKey.contains(needle))
            }.toList()
    }
}

/**
 * 取出（或建出）这一趟的状态。
 *
 *  **一个页面实例只建一份**：用 `remember(picker)` 而不是 `remember(picker, selected)` ——
 *  后者会在「已选中那一项」变化时整份重建，把已经查到的清单一起丢掉（看起来像「选完又回到加载态」）。
 *  `pending` 的跟随由调用点那个 `LaunchedEffect(selected)` 负责。
 */
@Composable
internal fun SettingsPage.rememberAppPickerUiState(selected: String?): AppPickerUiState {
    val pagePicker = picker
    return remember(pagePicker) { AppPickerUiState(pagePicker, selected) }
}
