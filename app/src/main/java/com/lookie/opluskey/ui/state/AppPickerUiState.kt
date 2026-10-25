package com.lookie.opluskey.ui.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lookie.opluskey.ui.model.LaunchableApp

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
     * **当前选中**的那一条（装 [PickedDetail]：行尾要靠 `shortcutId` 分开画两种字形）。
     *
     *  存在页面级（[AppPickerState.pending]）：被二级页盖住时组合会被销毁，放在这里就会丢
     *  （挑过的行退回来变成「已绑定的那个」）。
     */
    var pending: PickedDetail?
        get() = picker.pending.value
        set(value) {
            picker.pending.value = value
        }

    /**
     * 两张拦人的脸（都是 `null` = 没在弹，同一时刻只可能开一张）：
     *  · [blockedUnopenable]：点 FAB 时选中的是个**拉不起来**的应用；
     *  · [blockedSmallWindow]：点 FAB 时判出这个应用**不支持小窗**，而「以小窗口打开」开着。
     *
     *  两张都只在**点 FAB** 那一刻弹 —— 点行不校验：那一趟只是往下一级走，
     *  真正会写进配置的是 FAB。
     */
    var blockedUnopenable by mutableStateOf<LaunchableApp?>(null)

    var blockedSmallWindow by mutableStateOf<SmallWindowBlock?>(null)

    /**
     * 进页那一刻的**唯一**复位点 —— 与 [AppPickerState.clearTarget] 一起，是这一份状态仅有的两处清空。
     *
     *  · 点过的行回到「当前绑定的那个」（[selection]）；
     *  · 进场定位、两张拦人的脸、刷新指示器归零；
     *  · **清单不清**（[loaded] 留着）：进页第一帧就有内容，后台照常重查一遍。
     */
    fun beginVisit(
        selection: String?,
        /** 当前绑定是不是一条**快捷方式**；是就带上它的 id，行尾据此画 `table-shortcut`。 */
        shortcutId: String? = null,
        /**
         * 这一趟「给条件挑前台应用」时，那条条件**已经选中的全部包名**（可多选，见
         * `BranchCondition.appPackages`）；其余入口传空集。
         */
        picked: Set<String> = emptySet(),
    ) {
        pending = selection?.let { PickedDetail(it, shortcutId) }
        picker.pickedApps.value = picked
        // 上一趟的草稿不带到这一趟来（它是「等着被提交」的东西，跨趟存在只会误提交）
        picker.draft = null
        located = false
        blockedUnopenable = null
        blockedSmallWindow = null
        failed = false
        refreshing = false
    }

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
        // 首尾空格要去掉：粘贴 / 输入法回车常带一个尾空格，那时整表会「什么都没搜到」
        val filter = picker.filter.value.trim()
        val showBroken = picker.showUnopenable.value
        // 拼音那一档要小写比：排序键是罗马化后的小写串（见 `LaunchableApp.sortKey`）。
        //  **必须钉 `Locale.ROOT`**：默认 locale 会把字母改成当地写法（土耳其语里 `I` → `ı`、
        //  `i` 的大写是 `İ`），而排序键是固定 ASCII 小写 —— 那台机器上按读音就搜不到东西。
        //  与 `ActivityPickerRules` 里那一处同一份口径。
        val needle = filter.lowercase(java.util.Locale.ROOT)
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
 * 被「不支持以小窗口打开」拦下的那一个 —— [AppPickerUiState] 的两张拦人的脸之一。
 *
 * @param fromPick 这次是从**点 FAB** 触发的那一道。它决定「确定」要不要顺手写下去：
 *   从 FAB 来的按 `app:<包名>` 存（用户最初要的「取消勾选保存」），
 *   从别处来的只把开关拨回关 —— 那时还没有「这一次的选择」可存。
 */
internal data class SmallWindowBlock(
    val app: LaunchableApp,
    val fromPick: Boolean,
)
