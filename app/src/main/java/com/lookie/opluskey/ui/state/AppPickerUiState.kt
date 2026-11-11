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
 *  注意：**页面级的 `picker`（搜索词 / 「显示系统应用」那个落盘开关 / 小窗开关）不在这里**，
 *  它是跨进页持久的那一份（见 [AppPickerState]）。这里只有「这一趟」的东西。
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

    /** 这一次查询失败了吗（`loadLaunchableApps` 返回 null）。 */
    var failed by mutableStateOf(false)

    /** 下拉刷新的指示器状态与重查触发键 —— 两者一起喂给同一个加载 effect。 */
    var refreshing by mutableStateOf(false)

    var refreshTick by mutableStateOf(0)

    /**
     * **当前选中**的那一条（装 [PickedDetail]：写回时要靠 `shortcutId` 认「这是快捷方式」）。
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
     *  · [blockedToggle]：点 FAB 时判出**这一档开关这一次用不了** —— 小窗（框架不支持）或
     *    原版动画（分身 / 快捷方式 / 具体界面吃不到，见 `AppPickerState.stockAnimUnsupported`）。
     *
     *  两张都只在**点 FAB** 那一刻弹 —— 点行不校验：那一趟只是往下一级走，
     *  真正会写进配置的是 FAB。第二张也只有在那里才说得准「这一次要写什么」。
     */
    var blockedUnopenable by mutableStateOf<LaunchableApp?>(null)

    var blockedToggle by mutableStateOf<ToggleBlock?>(null)

    /**
     * 进页那一刻的**唯一**复位点 —— 与 [AppPickerState.clearTarget] 一起，是这一份状态仅有的两处清空。
     *
     *  · 点过的行回到「当前绑定的那个」（[selection]）；
     *  · 进场定位、两张拦人的脸、刷新指示器归零；
     *  · **清单不清**（[loaded] 留着）：进页第一帧就有内容，后台照常重查一遍。
     */
    fun beginVisit(
        selection: String?,
        /** 当前绑定是不是一条**快捷方式**；是就带上它的 id（写回走 `appshortcut:` 那一档）。 */
        shortcutId: String? = null,
        /**
         * 这一趟「给条件挑前台应用」时，那条条件**已经选中的全部包名**（可多选，见
         * `BranchCondition.appPackages`）；其余入口传空集。
         */
        picked: Set<String> = emptySet(),
    ) {
        pending = selection?.let { PickedDetail(it, shortcutId) }
        picker.pickedApps.value = picked
        //  多选的基线**紧跟**着它记：退页那一问按「与进来时比变了没有」判（见
        //  `AppPickerState.pickedAppsChanged`），两处必须同一刻写。
        picker.markPickedBaseline()
        // 上一趟的草稿不带到这一趟来（它是「等着被提交」的东西，跨趟存在只会误提交）
        picker.draft = null
        located = false
        blockedUnopenable = null
        blockedToggle = null
        failed = false
        refreshing = false
    }

    /** 「进场滚到已选中那一行」做过了没有 —— 一次性闸，之后用户滚到别处不再被拽回来。 */
    var located by mutableStateOf(false)

    /** 手上的清单（`null` 时按空表用）。 */
    val apps: List<LaunchableApp> get() = loaded.orEmpty()

    /**
     * 过滤之后要画的清单：两个显示开关 + 搜索词（名字 / 包名 / 拼音）。
     *
     *  用 `derivedStateOf` 而不是在调用点 `remember`：这一段被三处读（列表、空态文案、进场定位），
     *  各自 `remember` 会各算一份，而「哪些行该出现」必须三处一致。
     *
     * ## 两档显示开关都只是**过滤**
     *
     *  清单**一次就取全**（见 `loadLaunchableApps`），所以这里只做减法：
     *  · [AppPickerState.showSystemApps]（「显示系统应用」）：放出**没有启动图标**的那一批
     *    （`LaunchableApp.byDefault == false`）—— 系统应用、没有图标的第三方应用。有启动图标的
     *    应用与 Xposed 模块不受它管，默认就该在；
     *  · [AppPickerState.showUnopenable]（「显示失效应用」）：放出**点不开**的那一批
     *    （`launchable == false`）；
     *  · 结束那一趟（[AppPickerState.stopApp]）**一律全列** —— 停掉一个没有启动图标的应用
     *    正是它的用处，藏起来反而没得选。
     *  · **两维取交集**：一行要同时过这两关才留下 —— 拨「显示失效应用」不会顺带放出没有图标的
     *    那一批。这两个开关是「联合筛选」，不是「任一满足即放开」。
     *
     *  三个输入全是 compose 状态，**拨开关只是让这个派生值重算一次**：不查盘、不换缓存、
     *  不需要重查。上一版把这两个开关当成「取哪一档清单」的输入（`includeAll`），于是
     *  「拨了没反应」几乎必然会在某一趟之后出现（真机两次踩到，见 changelog）。
     *
     *  两条判据**各管一维**（见 [AppPickerState.includesIconless]）：同时满足才留下 ——
     *  拨「显示失效应用」不会顺带放出没有图标的那一批。
     */
    val shown: List<LaunchableApp> by derivedStateOf {
        // 首尾空格要去掉：粘贴 / 输入法回车常带一个尾空格，那时整表会「什么都没搜到」
        val filter = picker.filter.value.trim()
        val showIconless = picker.includesIconless
        val showInvalid = picker.includesUnopenable
        // 拼音那一档要小写比：排序键是罗马化后的小写串（见 `LaunchableApp.sortKey`）。
        //  **必须钉 `Locale.ROOT`**：默认 locale 会把字母改成当地写法（土耳其语里 `I` → `ı`、
        //  `i` 的大写是 `İ`），而排序键是固定 ASCII 小写 —— 那台机器上按读音就搜不到东西。
        //  与 `ActivityPickerRules` 里那一处同一份口径。
        val needle = filter.lowercase(java.util.Locale.ROOT)
        loaded
            .orEmpty()
            .asSequence()
            .filter { showIconless || it.byDefault }
            .filter { showInvalid || it.launchable }
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
 * 被「这一档开关这一次用不了」拦下的那一次选 —— [AppPickerUiState.blockedToggle] 装的就是它。
 *
 *  两档共用一份，因为那两张窗本来就是同一件事：**说一句、把开关拨回关、按普通方式写下去**。
 *  只有「说哪一句」与「拨哪一个开关」不同：
 *  · [PickerToggle.MiniWindow]：框架不支持这个应用开小窗；
 *  · [PickerToggle.StockAnim]：分身 / 快捷方式 / 具体界面吃不到原版动画
 *    （判据与理由见 [AppPickerState.stockAnimUnsupported]）。
 *  各写一份的话，「确定之后怎么收尾」那一段就要抄两遍 —— 而它是最容易抄漏的那一段。
 *
 * @param label 正文里念的名字（应用名 / 界面名），用 `「」` 夹住
 * @param toggle 哪一档开关要被拨回关
 */
internal data class ToggleBlock(
    val label: String,
    val toggle: PickerToggle,
)

/** [ToggleBlock] 说的「哪一档开关」。 */
internal enum class PickerToggle {
    /** 「以小窗口打开」—— 菜单里那一项写 `window:`。 */
    MiniWindow,

    /** 「使用原版动画」—— 菜单里那一项写 `app:a:`。 */
    StockAnim,
}
