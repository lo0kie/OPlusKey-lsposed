package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.host.action.AppEntry
import com.lookie.opluskey.host.action.MiniWindowSupport
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.ConfirmDialog
import com.lookie.opluskey.ui.component.PageCornerFab
import com.lookie.opluskey.ui.component.PageFabButton
import com.lookie.opluskey.ui.component.collapsedHeaderHeight
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.component.escapeEmphasis
import com.lookie.opluskey.ui.model.APP_KIND_TAG_GAP
import com.lookie.opluskey.ui.model.APP_KIND_TAG_RAISE
import com.lookie.opluskey.ui.model.APP_LIST_HEADER_ITEMS
import com.lookie.opluskey.ui.model.APP_ROW_HEIGHT
import com.lookie.opluskey.ui.model.APP_STATE_ICON_SIZE
import com.lookie.opluskey.ui.model.APP_TITLE_SUMMARY_GAP
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.state.BandHeight
import com.lookie.opluskey.ui.state.rememberTick
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.RotatingProgressIndicator
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 「打开应用」页：应用列表加载 / 排序 / 选择。

/**
 * 问一次框架「这个包支不支持小窗」—— **三处触发点共用**（切应用 / 点菜单 / 点确认键）。
 *
 * 注意：读 [MiniWindowSupport] 的注释再用：这个问法**自带一条系统 toast**（判 `false` 时框架弹
 * 「此应用尚未支持浮窗」），所以只能一问一答地用在「用户刚做了某个动作」的时刻，
 * **绝不能**拿去整表轮询（那会一排一串 toast）。
 *
 * `cls` 现解一次：`AppEntry.resolve` 就 1~3 次 binder，而这里一次只问一个包，不是热路径。
 */
internal fun SettingsPage.querySmallWindowSupport(pkg: String): Boolean? =
    MiniWindowSupport.isSupported(
        pkg,
        AppEntry.resolve(context.packageManager, pkg)?.component?.className,
    )

/** Xposed 模块自带的模块设置入口 category —— 「选择应用」页认模块的判据。 */
internal const val XPOSED_MODULE_SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS"

/**
 * 「选择应用」页那条搜索带（固定搜索框）—— 挂在顶栏的 `bottomContent` 里。
 *
 * 位置是折叠栏底边（顶栏自己排的），底色 / 模糊 / 下沿细线都由顶栏那层负责。搜索词是页面级
 * 状态 [SettingsPage.picker]；高度报给 [SettingsPage.appSearchBandHeight]，列表顶上那个
 * 占位 Spacer 读它。
 */
@Composable
internal fun SettingsPage.AppSearchBand() {
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { appSearchBandHeight.report(it.height) },
    ) {
        // 库自己的搜索输入框（`InputField`）。`expanded` 必须给 false：那一套「展开态」是给
        // 「点搜索框撑开一页结果」用的，本页本身就是结果列表；而且 `expanded = true` 会让
        // `label` 一直不显示，占位文案就没了。
        // 上下两档与别处同一套，且两档同值（都是 [PAGE_TOP_PADDING]）。
        InputField(
            query = picker.filter.value,
            onQueryChange = { picker.filter.value = it },
            // 键盘上点「搜索」不做额外动作：过滤是边输边做的
            onSearch = { },
            expanded = false,
            onExpandedChange = { },
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 只让开左右。纵向只带下边一档（[PAGE_TOP_PADDING]）：带子贴着折叠栏下沿，
                    // 下边这一档就是带子与内容之间的间距，各页同一档。
                    .padding(
                        start = PAGE_MARGIN,
                        end = PAGE_MARGIN,
                        bottom = PAGE_TOP_PADDING,
                    ),
            label = I18n.get(Strings.APP_PICKER_SEARCH_APP, "搜索应用"),
        )
    }
}

/**
 * 「打开应用」页 —— 列出本机可启动的应用。
 *
 * 点一条只**切换选中的那个**（行尾单选圈跟着走），右下角那颗确认键才把选择交出去
 * （`app:<包名>` 或 `window:<包名>`，看 [SettingsPage.picker]）。
 *
 * 用 `LazyColumn` 而不是 `verticalScroll + Card`：这一页可能有 200+ 行，`verticalScroll`
 * 会把所有行都组合一遍（200+ 次组合，每行还要排标签、单选圈与图标格子），首帧直接卡住。
 * 每行各自一张卡。
 */
@Composable
internal fun SettingsPage.AppPickerScreen(
    selected: String?,
    onPick: (String, Boolean) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    // 这一页是 push 转场的目标页，首帧要把 160 个应用的 label 读出来、还要把中文名逐个转写成
    // 拼音算排序键 —— 全压在首帧上转场就卡住。现在丢到后台调度器去查（`Dispatchers.IO`），
    // 页面立刻进场、行等查完自己出现。
    //
    // 「显示系统应用」开关一变，列表来源就变了，所以直接把它当重查的键。
    val withSystem = picker.showSystemApps.value
    // 初值先铺上一次查到的清单（`AppPickerState.cachedApps`），二次进入这一页就是瞬时的，同时后台照样重查
    // 一遍（新装的应用才会出现）。那份缓存只在模式一致时才能拿来铺首帧，否则会先闪一屏系统
    // 应用（见 `AppPickerState.cachedAppsWithSystem`）。
    var loaded by remember {
        mutableStateOf(if (picker.cachedAppsWithSystem == withSystem) picker.cachedApps.value else null)
    }
    // 上一轮查的是哪个模式（`null` = 还没查过）。只有模式真的翻转才把列表收起来走加载态：
    // 那一刻列表来源换了，旧清单留着是错的。进页那一次不收 —— 那是同一份清单的重查。
    var lastMode by remember { mutableStateOf<Boolean?>(null) }
    // 这一次查询失败了吗（[loadLaunchableApps] 返回 null）。查失败时手里又没有缓存的话，
    // 不能一直挂着转圈 —— 那是在等一个永远不来的结果（见下面那两块 [PageStatePlaceholder]）。
    var failed by remember { mutableStateOf(false) }
    // 下拉刷新（[PullToRefresh]）：`refreshing` 是它的状态源，`refreshTick` 是重查的触发键 ——
    //  重查复用下面那个 effect（把 tick 当第二个 key），下拉与「进页 / 切模式」走同一段逻辑。
    //  ⚠️ 刷新**不**把列表打回加载态（那是模式翻转才做的事）：旧清单留着，只有指示器转圈。
    var refreshing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()
    // 本地选中：点行只改它，右下角那颗确认键才把选择交出去。初值照当前绑定的那个（进页时先亮着它）。
    var pending by remember(selected) { mutableStateOf(selected) }
    // 两张拦人的脸各有一个挂起项（都是 `null` = 没在弹，与别处的 `deleting` / `confirmDiscard`
    // 同一套写法；同一时刻只可能开一张）：
    //  · [blockedUnopenable]：点确认键时挑中的是个**拉不起来**的应用；
    //  · [blockedSmallWindow]：在菜单里**点开「以小窗口打开」那一刻**，当前选中的应用判为不支持小窗。
    //  注意：这两个都是**页面自己的** `remember`：页面出栈就注销，正好是「每次进页都没在弹」，
    //    不需要像 [SettingsPage.picker] 那样手动清（那个提在类上，理由见它的注释）。
    var blockedUnopenable by remember { mutableStateOf<LaunchableApp?>(null) }
    var blockedSmallWindow by remember { mutableStateOf<SmallWindowBlock?>(null) }
    LaunchedEffect(withSystem, refreshTick) {
        // 日志放在 effect 里而不是组合体里：组合体会随每次重组重跑，那几行会被打好几遍。
        if (lastMode != null && lastMode != withSystem) {
            XLog.w("选择应用：模式翻转（显示全部应用=$withSystem），清单回到加载态")
            loaded = null
        }
        lastMode = withSystem
        // 整段查询都在后台调度器上，一行都不许落在主线程：里面有 `getInstalledApplications`、
        // 逐个 `getLaunchIntentForPackage`（开着系统应用时是几百次 binder 调用）与每个包的
        // label 加载，全是阻塞式的。用 `IO` 而非 `Default`：这些活是 IO 型，而 `Default`
        // 是按 CPU 核数限流的共享池，压满它会拖住别的 Default 任务（转场、各页动画）。
        //  图标不在这一轮：它是按行按需取的（见 [AppIcon]）。
        XLog.w("打开应用页：取列表（显示全部应用=$withSystem，先用缓存 ${picker.cachedApps.value?.size ?: 0} 条）")
        val fresh = withContext(Dispatchers.IO) { loadLaunchableApps(withSystem) }
        // 查失败（null）不覆盖缓存，也不把当前列表打回空态：那是「这次没查到」，
        //  不是「没有应用可挑」。缓存里那份可能只是模式不同（显示系统应用开着时查的），
        //  留着比清空强；真要一份空清单，[loadLaunchableApps] 会正常返回 emptyList()。
        if (fresh == null) {
            XLog.w("选择应用：这次没查到（保留上一份清单 ${picker.cachedApps.value?.size ?: 0} 条）")
            failed = true
        } else {
            // 图标缓存跟着新清单一起清（理由见 `APP_ICON_CACHE_SLOTS`）：清在 `loaded` 之前，
            //  新组合出来的行才会去取新图标，而不是命中上一批 `Drawable`。
            picker.clearIcons()
            picker.cachedApps.value = fresh
            picker.cachedAppsWithSystem = withSystem
            loaded = fresh
            failed = false
        }
        // 刷新收尾（成功失败都收）：`isRefreshing` 置回 false ⇒ 指示器结束并播完成动画。
        //  放在这里而不是 onRefresh 里等一个回调：这一段查询就是「刷新」本身，它跑完就算完。
        refreshing = false
    }
    val apps = loaded.orEmpty()

    // 「以小窗口打开」的**勾选态 + 选中的应用**每变一次，就问一次框架「它支不支持小窗」，
    // 判 `false` ⇒ 弹「不支持以小窗口打开」（用户口径：**三处都要查** ——
    // 切应用 / 点菜单 / 点确认；后一处在上面那颗确认键的 `onClick` 里，不在这个 effect 里）。
    //
    // 注意：**这个 effect 里的两个触发点都要，少一个就漏**——「点那一下」与「换应用」是同一件事的两半：
    //  · 先选应用、再点开开关 ⇒ 靠「开关变了」这一下问；
    //  · 先点开开关、再换应用 ⇒ 靠「选中的应用变了」这一下问。
    //  合起来才盖住**每一条会被写成 `window:<包名>` 的路径**：任何最终的「开着 + 选中某应用」
    //  组合，都必然经过这两者之一的**最后一次变化**。之所以不能只挂一处，就是因为用户按的
    //  顺序是自由的（用户指出：只挂「换应用」时，「选完再点开」仍会把不支持小窗的
    //  应用写成 `window:`）。点确认键那一道是**兜底**：前面判过不等于提交那一刻还是那个结论。
    //
    //  · **进页那一次不问**：那是「这次要编辑的动作本来就是 `window:`」，不是用户刚按下去的手
    //    —— 靠 `askedPkg` 的初值兜住（进页就直接记成「这一档已经问过了」）。
    //  · 关掉开关把它清空，再点开时会重新问；同一个包问过一次不再重复问（免得系统提示反复弹）。
    //  · 判不出来（`null`）放行；一个都没选中 ⇒ 什么都不做。
    //  · `apps` 也进 key：清单还没到手时问不了（拿不到 label），清单到了要补问一次
    //    —— 所以 `askedPkg` 在**真正问出去之后**才记，不能在前面就记。
    //  · 注意：别把它挪回「整表预判」：这个问法自带系统提示，整表问就是「进页弹一串 toast」
    //    （用户报的「选择应用里一直弹此应用不支持小窗」就是它，见 [MiniWindowSupport]）。
    var askedPkg by remember { mutableStateOf(if (picker.smallWindow.value) pending else null) }
    LaunchedEffect(picker.smallWindow.value, pending, apps) {
        if (!picker.smallWindow.value) {
            askedPkg = null
            return@LaunchedEffect
        }
        val pkg = pending ?: return@LaunchedEffect
        if (pkg == askedPkg) return@LaunchedEffect
        val app = apps.firstOrNull { it.packageName == pkg } ?: return@LaunchedEffect
        askedPkg = pkg
        //  注意：查询是 **binder 反射**（`AppEntry.resolve` 1~3 次 + `isSupportZoomMode` 一次），不能占主线程
        //  —— 这里挂在 `LaunchedEffect`（主线程调度器）上，包一个 `IO`。
        //  点确认键那一道（下面 FAB 的 `onClick`）**有意留在主线程**：它要按查询结果当场决定「写还是拦」，
        //  异步化会造出「连点两下写两次」的新洞，而那是一次性用户动作、代价有界。
        val supported = withContext(Dispatchers.IO) { querySmallWindowSupport(pkg) }
        if (supported == false) {
            XLog.w("选择应用：$pkg 不支持小窗（勾选态/选中项刚变过），先弹确认")
            blockedSmallWindow = SmallWindowBlock(app, fromPick = false)
        }
    }

    // 搜索词提到页面级（[SettingsPage.picker]）：搜索框挂在顶栏里，与这一页的过滤逻辑分处
    // 两棵树，得共用同一份状态。它跟着进这一页清空（见 [SettingsPage.openAppPickerForStep]）。
    val filter = picker.filter.value
    // 「显示失效应用」：纯显示过滤 —— 判据 [LaunchableApp.launchable] 已经在手上的清单里，
    // 不必重查。它不随进页清空、且落盘（与「显示系统应用」同一套账）。
    //  **关 = 不显示失效的**（这一档的语义是「显示失效应用」，不是「隐藏失效应用」）。
    val showBroken = picker.showUnopenable.value
    val shown =
        remember(apps, filter, showBroken) {
            // 拼音那一档要小写比：排序键是罗马化后的小写串（见 [LaunchableApp.sortKey]）
            val needle = filter.lowercase()
            apps
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

    // —— 已选的应用被上面两个开关藏起来时，把选中清空（用户口径）——
    //
    //  两个开关一翻，「已选中的那一个」可能正好落进被藏起来的那一批：行没了（`shown` 里不再有它），
    //  可右下角那颗确认键还亮着 —— 点下去就把一个**已经看不见、也不该再选**的应用写进绑定。所以这里
    //  对一次账：已选的不在**这一档过滤之后**的清单里 ⇒ 清空选中（等于「还没选」，那颗确认键一起收起）。
    //
    //  注意：判据里**不含搜索词**（[AppPickerState.filter]）：搜索是临时找东西用的，被它筛掉就清空选中反而莫名
    //   其妙 —— 换个词那条又会回来。只管上面两个开关藏掉的那些。
    //  注意：清单还没到手时**不判**（`loaded == null`，含「显示系统应用」翻转之后的加载态）：那时手里
    //   没有清单，判了就是把「还没查完」当成「没有这个应用」。查失败时 `loaded` 仍是上一份清单、
    //   列表画的也正是那一份，两边一致，不会误清。
    //  注意：每次换选中的那一行也会跑一遍（`pending` 在 key 里）：那是为了让这条不变式**始终**成立，
    //   不是多此一举 —— `pending` 会在 `selected` 变了时被重置成新的绑定值（见上面那个 `remember`）。
    LaunchedEffect(showBroken, apps, pending) {
        val pkg = pending ?: return@LaunchedEffect
        if (loaded == null) return@LaunchedEffect
        if (apps.none { it.packageName == pkg && (showBroken || it.launchable) }) {
            XLog.w(
                "选择应用：已选的应用被这一档藏起来了" +
                    "（显示失效=$showBroken，显示全部应用=$withSystem）⇒ 清空选中",
            )
            pending = null
        }
    }

    val listState = rememberLazyListState()
    // 固定搜索框那条带子的高度（含上下两档内边距）—— 列表顶上那个占位 Spacer 用同一个值，
    // 两者才严丝合缝。带子挂在顶栏里，量高度在那边，值走页面级的
    // [SettingsPage.appSearchBandHeight]（见 [BandHeight]）。
    val searchBandDp = appSearchBandHeight.dp()
    val tick = rememberTick()
    // 列表顶上那一整段（顶栏 + 固定搜索带 + 首行上边距）—— 进场定位时目标行要落在它下面。
    // 取折叠后的顶栏高度（[collapsedHeaderHeight]）：定位会把顶栏折叠到底，按展开高度算会多
    // 让出一整段。带子高度含它自己那 8dp 下内边距，再加列表头那个占位项补的 [PAGE_TOP_PADDING]。
    // 再补半个行距（[GRID_GAP] / 2）：行距挂在每一行下边，目标行顶到带子下沿时上面那半个间距
    // 正好被带子吃掉，补一半上下两行的间距才均匀。
    // `scrollToItem` 收的是 px，而 `LocalDensity.current` 只能在组合期读，不能挪进 `LaunchedEffect`。
    val listTopInsetPx =
        with(LocalDensity.current) {
            (collapsedHeaderHeight() + searchBandDp + PAGE_TOP_PADDING + GRID_GAP / 2).roundToPx()
        }
    // 只在没在过滤、且不在第一项时才滚（第一项本就在眼前，滚了反而多一次位移）。
    // 下标要加 [APP_LIST_HEADER_ITEMS]（列表头是「顶栏让位 + 搜索带」那一项）。
    // 还必须带一段负偏移：`scrollToItem` 把目标顶到视窗顶部，而顶部被固定搜索带盖着，不加
    // 偏移目标行正好藏在带子底下，表现就是「滚了却看不见」。带子高度首帧还没量到时先不滚。
    // key 里不放 `listTopInsetPx`：它跟着顶栏折叠走（用户一滚就变），放进去等于每滚一下都被
    // 拽回已选那一行。
    //
    //   `shown` 那一项留着（定位要读它算下标），但它同时是**重查完成**的信号：后台那遍查完
    //   `loaded` 一换、`shown` 跟着换新实例，effect 就会重跑。定位是「进场那一次」的事，
    //   所以用一个一次性闸 [located] 兜住 —— 否则用户自己滚到别处、清单查完又被猛拽回已选行。
    var located by remember { mutableStateOf(false) }
    LaunchedEffect(selected, shown, searchBandDp) {
        if (located) return@LaunchedEffect
        if (selected == null || filter.isNotBlank()) return@LaunchedEffect
        if (searchBandDp <= 0.dp) return@LaunchedEffect
        val index = shown.indexOfFirst { it.packageName == selected }
        // 清单还没到（`shown` 空）不算定位过：等它到了再走这一次
        if (shown.isEmpty()) return@LaunchedEffect
        if (index > 0) {
            // `scrollToItem` 是程序性跳转，不走嵌套滚动那条路 ⇒ 顶栏不会自己折叠，跳完还是
            // 展开的、把目标行压在下面，因此直接把折叠量拉满。
            scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
            XLog.w(
                "定位到已选：index=$index 顶栏 offset=${scrollBehavior.state.heightOffset}" +
                    "/limit=${scrollBehavior.state.heightOffsetLimit} inset=$listTopInsetPx",
            )
            listState.scrollToItem(index + APP_LIST_HEADER_ITEMS, scrollOffset = -listTopInsetPx)
        }
        located = true
    }

    Column(Modifier.fillMaxSize()) {
        // 列表区（含加载提示）—— 两种状态占同一块地方：加载时不留白、加载完不跳一下。
        //
        // 右下角那颗「滚到顶部」的 FAB 也挂在这个盒子里：盒子就是「页面顶 → 页面底」那一整块，
        // 它的 `BottomEnd` 正好是页面右下角，与「按键绑定」页那颗 FAB 同一个落点，不必再套 Box。
        Box(Modifier.fillMaxSize()) {
            // 下拉刷新：重查一次应用清单。
            //  ⚠️ 顶栏折叠那条 connection **交给它**（`topAppBarScrollBehavior`）：库内部会把它
            //  串进自己那条链（`createPullToRefreshConnection`：Idle 时先喂顶栏、再喂刷新），
            //  所以 `LazyColumn` 上原来那份 `nestedScroll` 必须撤掉 —— 留着同一段位移会喂两遍。
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    refreshTick++
                },
                pullToRefreshState = pullToRefreshState,
                topAppBarScrollBehavior = scrollBehavior,
                modifier = Modifier.fillMaxSize(),
                // ⚠️ 指示器要**下移到「顶栏 + 搜索框那条带子」之下**：这两块是叠在列表之上的
                //  固定浮层，指示器默认画在屏幕最顶上 ⇒ 整个被它们盖住。
                //  库把这个参数直接当指示器那一层的 `offset(y = …)` 用（见 `RefreshHeader` 的调用处），
                //  所以这里给的正是「让出固定层」的同一笔账。
                contentPadding =
                    PaddingValues(
                        top = currentHeaderHeight(scrollBehavior) + searchBandDp,
                    ),
                // 库默认那四条是英文（"Pull down to refresh" …），这一页是中文界面
                refreshTexts =
                    listOf(
                        I18n.get(Strings.APP_PICKER_PULL_REFRESH, "下拉刷新"),
                        I18n.get(Strings.APP_PICKER_RELEASE_REFRESH, "松开刷新"),
                        I18n.get(Strings.APP_PICKER_REFRESHING, "正在刷新"),
                        I18n.get(Strings.APP_PICKER_REFRESH_DONE, "刷新完成"),
                    ),
            ) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            // 越界回弹：COUI 自己的弹簧实现（`overScrollVertical`）。挂在这里同样是滚动
                            // 容器外侧，吃的是它没吃掉的那部分位移 —— 列表不满一屏也弹。
                            .overScrollVertical(),
                    // 底部留白与别页一模一样（[PAGE_BOTTOM_PADDING]，32dp）——
                    // 与「按键绑定」页那种「最后一行被 FAB 压住」的取舍保持一致。
                    contentPadding = PaddingValues(bottom = PAGE_BOTTOM_PADDING),
                ) {
                    // —— 头一项：顶栏让位 + 固定搜索框那条带子的占位 ——
                    //
                    // 搜索框在固定层里，所以列表只要在顶上让出「顶栏 + 那条带子」这么多地方；滚起来
                    // 之后列表钻到带子背后，由带子那层不透明底挡住。因此下面所有按下标的滚动都要加
                    // [APP_LIST_HEADER_ITEMS] 这个偏移。
                    item(key = "header-space") {
                        // 用动态值（见 [currentHeaderHeight]）：折叠阶段这一档跟着缩下去。
                        // 顶栏 + 带子（带子的测量高度里含它自己那 8dp 下内边距）再补一档
                        // [PAGE_TOP_PADDING]：这一页第一行是卡片，没有别页首行那个 `SmallTitle`
                        // 自带的上内边距，不补的话卡片会直接贴着搜索框。
                        Spacer(
                            Modifier.height(
                                currentHeaderHeight(scrollBehavior) + searchBandDp + PAGE_TOP_PADDING,
                            ),
                        )
                    }

                    // 列表区 —— 加载中什么都不铺（列表空着），转圈由下面那层叠上去画。
                    if (loaded != null) {
                        // 「查得到但没有匹配项」那句提示不在这个列表里（见下面那层浮层）：它一进来就
                        // 会占掉一个下标，而按下标滚到当前绑定应用的偏移 [APP_LIST_HEADER_ITEMS]
                        // 只算了顶上那个占位项 —— 多一项就错一行。
                        items(shown, key = { it.packageName }) { app ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    // 列表项的出现 / 消失 / 换位都带动画（`animateItem`）：加载完成时
                                    // 一行行淡进来，过滤时也是挪过去的，而不是整屏换掉。
                                    .animateItem()
                                    .padding(horizontal = PAGE_MARGIN)
                                    // 间距在高度之前给：这样 [APP_ROW_HEIGHT] 量的是卡片自己，那 12dp
                                    // 是卡片外面的行距（顺序反了会把卡片压矮 12dp）。
                                    .padding(bottom = GRID_GAP)
                                    // 行高定死 ⇒ 行首那枚图标才能整高（见 [APP_ROW_HEIGHT]）
                                    .height(APP_ROW_HEIGHT),
                            ) {
                                BasicComponent(
                                    startAction = { AppIcon(app) },
                                    // 行尾选中指示跟着**本地选中**走：点一行只是把标记挪过去，交出去靠右下角那颗确认键。
                                    //  字形与网格同一套（空心 `circle` / 实心 `circle-check`），不用 COUI 的单选圈 ——
                                    //  那颗圆点与全页那套 Tabler 线性图标不是一支笔。
                                    endActions = {
                                        val picked = app.packageName == pending
                                        Icon(
                                            if (picked) PageIcons.TileChecked else PageIcons.TileUnchecked,
                                            contentDescription = null,
                                            // 与网格那颗同尺寸：24dp，即原先 COUI `RadioButton` 的 `requiredSize`
                                            modifier = Modifier.size(ROW_END_ICON_SIZE),
                                            tint =
                                                if (picked) {
                                                    COUITheme.colorScheme.primary
                                                } else {
                                                    COUITheme.colorScheme.onSurfaceVariantActions
                                                },
                                        )
                                    },
                                    cardListPosition = CardListPosition.Full,
                                    onClick = {
                                        tick()
                                        pending = app.packageName
                                    },
                                ) {
                                    // 标题 / 摘要自己排，不走库的 `title` / `summary` 两个参数：标题那行要在
                                    // 名字右边再挂一枚小标签，而那两个参数只吃 `String`。样式照库的默认内容来。
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.label,
                                            // `weight` 让长名字先缩，标签始终留在行内可见
                                            // （`fill = false`：名字短时不去撑满，标签紧跟在右边）
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = COUITheme.textStyles.headline1,
                                            fontWeight = FontWeight.Medium,
                                            color = BasicComponentDefaults.titleColor().color,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        // 这一行是「盒子居中」而非 `alignByBaseline`：基线对齐会把胶囊整体
                                        // 压低小半档（大标题字面比标签高一截），居中反而让两个字面的中线
                                        // 基本重合（见 [APP_KIND_TAG_RAISE]）。
                                        // 「模块 / 系统 / 用户」只在「显示系统应用」开着时画；「模块」不看那个开关
                                        // —— 模块默认就在列表里，开关关着也得分得出来。三档各一个颜色（见 [appKindTagTint]）。
                                        if (withSystem || app.xposed) {
                                            Spacer(Modifier.width(APP_KIND_TAG_GAP))
                                            // 三选一：「模块」压过「系统」压过「用户」
                                            AppKindTag(
                                                text =
                                                    when {
                                                        app.xposed -> I18n.get(Strings.APP_PICKER_TAB_XPOSED, "模块")
                                                        app.system -> I18n.get(Strings.APP_PICKER_TAB_SYSTEM, "系统")
                                                        else -> I18n.get(Strings.APP_PICKER_TAB_USER, "用户")
                                                    },
                                                tint = appKindTagTint(app.xposed, app.system),
                                            )
                                        }
                                        // 「不可打开」不看那个开关：用户应用里也有拉不起来的，每一行都该有。
                                        if (!app.launchable) {
                                            Spacer(Modifier.width(APP_KIND_TAG_GAP))
                                            AppKindTag(
                                                text = I18n.get(Strings.APP_PICKER_TAG_UNOPENABLE, "不可打开"),
                                                tint = COUITheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        modifier = Modifier.padding(top = APP_TITLE_SUMMARY_GAP),
                                        style = COUITheme.textStyles.body2,
                                        color = BasicComponentDefaults.summaryColor().color,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // —— 除列表外的三种形态（一条都没查到 / 被搜索词筛没了 / 还在加载）都走
            // [PageStatePlaceholder] 那一份，三者只差上面那颗字形与下面那句话。它们都挂在列表
            // 外面（与 `LazyColumn` 同一层浮层），而不是当列表第一项：列表项一多，按下标滚到当前
            // 绑定应用的偏移 [APP_LIST_HEADER_ITEMS] 就错一行。空态只在查完之后才有意义。
            PageStatePlaceholder(
                // 查失败（failed）且手里没有缓存时也要落到这一块：否则 `loaded` 一直是 null，
                //  页面永远挂在下面的加载态上等一个不会来的结果。
                visible = (loaded != null || failed) && shown.isEmpty(),
                topInset = currentHeaderHeight(scrollBehavior) + searchBandDp,
                // 两种情况各一颗：清单本身是空的 vs 被搜索词筛没了。后一颗不用划掉的放大镜
                // —— 那道斜杠与「无操作」那颗 `Ban` 长得像，摆这儿读起来像「禁止搜索」。
                glyph = { tint ->
                    Icon(
                        imageVector =
                            if (apps.isEmpty()) {
                                PageIcons.EmptyApps
                            } else {
                                PageIcons.NoMatch
                            },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = tint,
                    )
                },
                text =
                    when {
                        // 真因优先：这次是**没查出来**，不是「一个都没有」（后者是有效结论）
                        failed && apps.isEmpty() -> {
                            I18n.get(Strings.APP_PICKER_LOAD_FAILED, "应用列表没读出来，退回上一页再进来试一次")
                        }

                        apps.isEmpty() -> {
                            // 不再说「系统限制了本页可见的应用列表」：那个前提不成立（见 docs/device-findings.md 10.8），
                            // 这一页的包可见性跟着宿主走，真列不出来只会是别的原因。
                            I18n.get(Strings.APP_PICKER_EMPTY_OPENABLE, "没有找到可以打开的应用")
                        }

                        // 措辞与菜单里那一项（「显示失效应用」）对齐，别让用户去猜是哪一个开关
                        !showBroken && apps.all { !it.launchable } -> {
                            I18n.get(Strings.APP_PICKER_EMPTY_HINT_BROKEN, "可以打开的一个都没有；开「显示失效应用」能看到打不开的那些")
                        }

                        else -> {
                            // 用户输入会整体过一遍正文记号，与下面那条空态同一条纪律：先转义
                            I18n.format(Strings.APP_PICKER_NO_SEARCH_RESULT, escapeEmphasis(filter))
                        }
                    },
            )

            // 转圈用 COUI 的 `RotatingProgressIndicator`（ColorOS 默认加载态）。
            PageStatePlaceholder(
                visible = loaded == null && !failed,
                topInset = currentHeaderHeight(scrollBehavior) + searchBandDp,
                text = I18n.get(Strings.APP_PICKER_LOADING_APPS, "正在加载应用…"),
                glyph = { tint ->
                    // 库那套三个数是按 26dp 画布算出来的，只把 `size` 换成 32dp 等于只多留白、
                    // 圈没变大 —— 三个数要按同一比例一起放大（比例由 `APP_STATE_ICON_SIZE` 现算）。
                    val spinnerScale =
                        APP_STATE_ICON_SIZE.value /
                            ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorSize.value
                    RotatingProgressIndicator(
                        color = tint,
                        size = APP_STATE_ICON_SIZE,
                        ringDiameter =
                            ProgressIndicatorDefaults
                                .DefaultRotatingProgressIndicatorRingDiameter * spinnerScale,
                        strokeWidth =
                            ProgressIndicatorDefaults
                                .DefaultRotatingProgressIndicatorStrokeWidth * spinnerScale,
                    )
                },
            )

            // 右下角那颗确认键才是「确定选择」：点行只切换。样式（尺寸 / 形状 / 阴影 / 容器色）
            // 全走 [PageFabButton] 那一份 —— 与「按键绑定」页那颗共用，两处不会走形。图标 tint 显式给
            // `onPrimary`：库内部只给容器色、不给 contentColor，不显式上色会继承到 `onSurface`（深色）。
            // 位置同「右 [PAGE_MARGIN]×2、下 [PAGE_BOTTOM_PADDING]×2」，点一下先振动（[rememberTick]）。
            // 显隐：手上有选中的才出现 —— 没得确认就不占地方。
            //  注意：清单还没到手时（`loaded == null`：正在查，或查失败又没缓存）**一律不出现**
            //  （「加载应用列表时不允许保存」）—— 那一刻 `apps` 是空的，
            //  下面 `apps.firstOrNull { … }` 取到 null ⇒ 两道拦人的检查（不可打开 / 不支持小窗）
            //  会因为 `app != null` 这个前提不成立而**双双跳过**，等于把「还没核对」当成「没问题」
            //  照写下去。它是本页唯一的保存入口，藏起来就等于不允许保存。
            PageCornerFab(visible = pending != null && loaded != null) {
                PageFabButton(
                    onClick = {
                        tick()
                        val pkg = pending ?: return@PageFabButton
                        val app = apps.firstOrNull { it.packageName == pkg }
                        // (1) 拉不起来的应用（被停用 / 非导出入口 / 装了一半）先拦一句：**什么都不写**
                        //   （见下面那张「此应用无法打开」）。判据与行尾那枚「不可打开」标签同源，
                        //   就是清单里这一位，不必再查。
                        if (app != null && !app.launchable) {
                            XLog.w("选择应用：$pkg 拉不起来，弹「此应用无法打开」")
                            blockedUnopenable = app
                            return@PageFabButton
                        }
                        // (2) 开着「以小窗口打开」⇒ 点确认这一刻**再问一次**（三处查询的最后一道）：
                        //   前面问过不等于这一刻还是那个结论（可能判不出来、或用户在那个框上按了取消）。
                        if (picker.smallWindow.value && app != null) {
                            if (querySmallWindowSupport(pkg) == false) {
                                XLog.w("选择应用：$pkg 不支持小窗，点确认时拦下（不写 window:）")
                                blockedSmallWindow = SmallWindowBlock(app, fromPick = true)
                                return@PageFabButton
                            }
                        }
                        onPick(pkg, picker.smallWindow.value)
                    },
                ) {
                    Icon(
                        PageIcons.Confirm,
                        contentDescription = I18n.get(Strings.APP_PICKER_CONFIRM_SELECTION, "确定选择"),
                        tint = COUITheme.colorScheme.onPrimary,
                    )
                }
            }

            // —— 两张拦人的脸，都是「留在这一页」——
            //
            // (1)「此应用无法打开」：点确认键时挑中的是个拉不起来的应用（见上）。**没有正文**
            //    （`summary = null`；给空串会画出一行空白），两颗按钮都只是把这句提示关掉、
            //    回选择页 —— 什么都不写、什么都不改。
            ConfirmDialog(
                show = blockedUnopenable != null,
                title = I18n.get(Strings.DIALOG_APP_UNOPENABLE_TITLE, "此应用无法打开"),
                summary = null,
                action = I18n.get(Strings.COMMON_CONFIRM, "确定"),
                positiveDanger = false,
                onDismiss = { blockedUnopenable = null },
                onConfirm = { blockedUnopenable = null },
            )

            // (2)「不支持以小窗口打开」：**三处触发点共用这一张脸** —— 切应用 / 点菜单里那一项
            //    （`LaunchedEffect` 里）、点确认键（上面那颗按钮里，`fromPick = true`）。
            //    确认 ⇒ 菜单里那一项拨回关；从点确认键来的还顺手按 `app:<包名>` 写下去并离开本页。
            //    取消 ⇒ 什么都不做。正文里的应用名用 `「」` 夹住；动作项不上红（不丢用户的东西）。
            ConfirmDialog(
                show = blockedSmallWindow != null,
                title = I18n.get(Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_TITLE, "不支持以小窗口打开"),
                summary =
                    I18n.format(
                        Strings.DIALOG_APP_UNSUPPORTED_MINI_WINDOW_MESSAGE,
                        escapeEmphasis(blockedSmallWindow?.app?.label ?: ""),
                    ),
                action = I18n.get(Strings.COMMON_CONFIRM, "确定"),
                positiveDanger = false,
                onDismiss = { blockedSmallWindow = null },
                onConfirm = {
                    val blocked = blockedSmallWindow
                    blockedSmallWindow = null
                    // 拨回关：菜单里那一项跟着回去，用户看得见这次选择了什么
                    picker.smallWindow.value = false
                    XLog.w("选择应用：该应用不支持小窗，已把「以小窗口打开」拨回关")
                    // 点确认键那一路：用户要的就是「取消小窗这个勾选、照常存」—— 按普通打开写下去
                    if (blocked?.fromPick == true) {
                        XLog.w("选择应用：${blocked.app.packageName} 改为普通打开并写入")
                        onPick(blocked.app.packageName, false)
                    }
                },
            )
        }
    }
}
