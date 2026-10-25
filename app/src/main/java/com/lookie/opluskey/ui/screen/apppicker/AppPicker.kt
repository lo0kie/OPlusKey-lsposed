package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.bridge.AppEntry
import com.lookie.opluskey.bridge.MiniWindowSupport
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.rememberAppPickerUiState
import com.lookie.opluskey.ui.state.AppPickerUiState
import com.lookie.opluskey.ui.state.BandHeight
import com.lookie.opluskey.ui.state.PickedDetail
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 「打开应用」页：应用列表的加载、选择与提交。
//
// 这一页原来是一个 538 行的函数（状态、四段 effect、列表、两处空态、FAB、两张确认窗全在里面）。
// 现在按「状态 / 加载 / 列表 / 外框 / 弹窗」分到五个文件（点行只选中 + 进下一级，FAB 才写）：
//   · 本文件        —— 搜索带、页面骨架、三段 effect；
//   · `AppPickerUiState.kt` —— 这一趟的加载与选择状态（含派生清单）；
//   · `AppPickerList.kt`    —— 下拉刷新 + 列表 + 单行 + 进场定位；
//   · `AppPickerChrome.kt`  —— 两处空态、那句「为什么一条都没有」，以及右下角那颗确认键；
//   · `AppPickerDialogs.kt` —— 两张拦人的确认窗。

/**
 * 问一次框架「这个包支不支持小窗」—— **只在点 FAB 那一刻用**。
 *
 * 注意：读 [MiniWindowSupport] 的注释再用：这个问法**自带一条系统 toast**（判 `false` 时框架弹
 * 「此应用尚未支持浮窗」），所以只能一问一答地用在「用户刚按下确认」的时刻，
 * **绝不能**拿去整表轮询（那会一排一串 toast），也不能挂在「点行」上
 * （那一趟只是往下一级走，用户可能还要挑界面）。
 *
 * `cls` 现解一次：`AppEntry.resolve` 就 1~3 次 binder，而这里一次只问一个包。
 */
internal fun SettingsPage.querySmallWindowSupport(
    pkg: String,
    userId: Int,
): Boolean? =
    MiniWindowSupport.isSupported(
        pkg,
        AppEntry.resolve(context.packageManager, pkg)?.component?.className,
        //  分身那一份要问那个用户：框架按 (包, userId) 判，传 0 会拿到主用户的结论。
        userId,
    )

/** Xposed 模块自带的模块设置入口 category —— 「选择应用」页认模块的判据。 */
internal const val XPOSED_MODULE_SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS"

/**
 * 新式 libxposed 模块在清单里挂的那个 provider 的**类名**。
 *
 *  这一类模块不声明 [XPOSED_MODULE_SETTINGS_CATEGORY]，只靠这个 provider 注册自己
 *  （真机上的 `com.wizpizz.reddidnt` 2.0.0 即此），所以旧判据查不到它们。
 *  名字与 `io.github.libxposed.service` 那个包的实现类一一对应，不随模块变。
 */
internal const val LIBXPOSED_PROVIDER = "io.github.libxposed.service.XposedProvider"

/**
 * 「选择应用」页那条搜索带（固定搜索框）—— 挂在顶栏的 `bottomContent` 里。
 *
 * 位置是折叠栏底边（顶栏自己排的），底色 / 模糊 / 下沿细线都由顶栏那层负责。搜索词是页面级
 * 状态 [SettingsPage.picker]；高度报给 [SettingsPage.appSearchBandHeight]，列表顶上那个
 * 占位 Spacer 读它。
 */
@Composable
internal fun SettingsPage.AppSearchBand() {
    PickerSearchBand(
        query = picker.filter.value,
        onQueryChange = { next ->
            // 空表时**不接受空白开头的输入**：打一个空格等于没打（否则搜索框里先出现一个看不见的
            //  空格，后面打的字全带着它，看着像「搜了但没结果」）。
            //  一旦已经有内容就**照收**：应用名里可能真要空格（`We Chat` 那种），拦掉就打不进去。
            picker.filter.value = if (picker.filter.value.isEmpty()) next.trimStart() else next
        },
        label = I18n.get(Strings.APP_PICKER_SEARCH_APP, "搜索应用"),
        height = appSearchBandHeight,
    )
}

/**
 * 「选择界面」页那条搜索带 —— 与「选择应用」页**同一份**（同一个位置、同一档边距），
 * 只是搜索词与上报的高度各归各的。
 */
@Composable
internal fun SettingsPage.ActivitySearchBand() {
    PickerSearchBand(
        query = picker.activityFilter.value,
        onQueryChange = { next ->
            picker.activityFilter.value =
                if (picker.activityFilter.value.isEmpty()) next.trimStart() else next
        },
        label = I18n.get(Strings.APP_PICKER_SEARCH_ACTIVITY, "搜索应用组件"),
        height = activitySearchBandHeight,
    )
}

/**
 * 两条搜索带共用的那一份 —— 位置、边距、高度上报只有这一处。
 *
 *  两页的搜索框长得必须一样（用户在这两页之间来回跳），所以参数只留「搜什么」与「量到哪」，
 *  尺寸与边距都写死在这里。
 */
@Composable
internal fun SettingsPage.PickerSearchBand(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    height: BandHeight,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { height.report(it.height) },
    ) {
        // 库自己的搜索输入框（`InputField`）。`expanded` 必须给 false：那一套「展开态」是给
        // 「点搜索框撑开一页结果」用的，本页本身就是结果列表；而且 `expanded = true` 会让
        // `label` 一直不显示，占位文案就没了。
        // 上下两档与别处同一套，且两档同值（都是 [PAGE_TOP_PADDING]）。
        InputField(
            query = query,
            onQueryChange = onQueryChange,
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
            label = label,
        )
    }
}

/**
 * 「打开应用」页 —— 列出本机可启动的应用。
 *
 * 用 `LazyColumn` 而不是 `verticalScroll + Card`：这一页可能有 200+ 行，`verticalScroll`
 * 会把所有行都组合一遍（200+ 次组合，每行还要排标签、单选圈与图标格子），首帧直接卡住。
 * 每行各自一张卡。
 *
 * **点**某一行进二级页（`Screen.ACTIVITY_PICKER`）挑这个应用里的具体界面 —— 这一页
 * 不再有「先点选、再按确认」的中间态：点一行就是往下一级走，选完在那一页就写下去。
 * 行尾那枚图标画的是**当前绑定**（箭头 = 还没绑 / 勾 = 绑的是应用本身 /
 * 一块屏 = 绑的是里面某个界面或快捷方式）。
 *
 * **例外是给「条件执行」挑的那一趟**（[AppPickerState.forBranch]）：那一支只存包名
 * （条件是前台应用、动作是 `app:包名`），没有「里面哪个界面」这回事。所以那一趟
 * **点一行就是选中它**（行尾画勾、其余行不画图标），右下角那颗确认键才把选择交出去；
 * 菜单里的「以小窗口打开」也不出现。
 *
 * 其中**给「前台应用」条件挑的那一趟可以多选**（[AppPickerState.branchTarget]）：一条条件
 * 存的是 `包1||包2`，**任一在前台即通过**，所以点一行是**切换**、可以亮好几行
 * （见 [AppPickerState.pickedApps]）。给那一支要执行的应用挑（`branchCommandTarget`）
 * 仍是单选 —— `app:` 只能是一个应用。
 */
@Composable
internal fun SettingsPage.AppPickerScreen(
    /** 点 FAB 确认：按这个**完整目标**写下去（不含界面时就是「打开应用」那一档）。 */
    onPick: (PickedDetail, Boolean) -> Unit,
    /** 点某一行：进二级页看这个应用能打开哪些界面。 */
    onOpenActivities: (String) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    val ui = rememberAppPickerUiState()
    AppListLoader(ui)
    SelectionPrune(ui)

    val searchBandDp = appSearchBandHeight.dp()
    Column(Modifier.fillMaxSize()) {
        // 列表区（含加载提示）—— 两种状态占同一块地方：加载时不留白、加载完不跳一下。
        //
        // 右下角那颗「确定选择」也挂在这个盒子里：盒子就是「页面顶 → 页面底」那一整块，
        // 它的 `BottomEnd` 正好是页面右下角，与「按键绑定」页那颗 FAB 同一个落点。
        Box(Modifier.fillMaxSize()) {
            AppPickerList(ui, onOpenActivities, scrollBehavior)

            // —— 除列表外的三种形态（一条都没查到 / 被搜索词筛没了 / 还在加载）——
            // 它们都挂在列表**外面**（同一层浮层），而不是当列表第一项：列表项一多，
            // 按下标滚到当前绑定应用的偏移就错一行。空态只在查完之后才有意义。
            //
            // 单独一层（[AppPickerPlaceholders]）：里面要读「当前顶栏高度」，而那条读会让
            // 读取者每帧重组 —— 留在这个函数体里就等于整页跟着滚动抖（二级页踩过，见
            // `ActivityEmptyState` 的注释）。
            AppPickerPlaceholders(ui, scrollBehavior, searchBandDp)

            // 这一页唯一的写入入口。**点行不写**（点行只是进下一级去挑界面；给「条件执行」
            // 挑的那一趟是「选中」），所以「就绑这个应用、不挑界面」这条路只能走它。
            //
            // 显隐跟**选中项**走（用户口径「有选中才显示，编辑 / 新增都一样」）：没有选中时
            // 它没有可确认的东西，摆在那儿只会让人按一下、再读一句「先从列表里选一个应用」。
            // 滑入滑出交给 [PageCornerFab] 自己那套 `AnimatedVisibility`。
            PageCornerFab(visible = ui.loaded != null && ui.pending != null) {
                AppConfirmFab(ui, onPick)
            }

            AppPickerDialogs(ui, onPick)
        }
    }
}

/**
 * 两句空态 + 加载态那一层 —— 只为一件事：把「当前顶栏高度」那条订阅关在这里。
 *
 *  [currentHeaderHeight] 读 `scrollBehavior.state.heightOffset`，它逐帧都变；在谁的作用域里读，
 *  谁就每帧重组。原先这句写在 [AppPickerScreen] 的组合体里，代价是本页每帧重组一遍 ——
 *  列表那棵树因为参数全稳被跳过了（所以只是略费），但空态文案（[emptyHint] 里那次
 *  `apps.all { !it.launchable }` 遍历）会跟着每帧算一次。
 *
 *  包 `Box` 是因为两个空态都是 `BoxScope` 扩展（靠 `align` 定位），本函数却要 `SettingsPage`。
 */
@Composable
private fun SettingsPage.AppPickerPlaceholders(
    ui: AppPickerUiState,
    scrollBehavior: ScrollBehavior,
    searchBandDp: Dp,
) {
    Box(Modifier.fillMaxSize()) {
        val topInset = currentHeaderHeight(scrollBehavior) + searchBandDp
        //  空态不在这里 —— 它铺在**列表里**（见 `AppPickerList`），这样拖动时它跟着一起弹。
        //
        //  这里要盖住**两种**「没有列表可铺」的情形，少一种就是整页空白：
        //   · 还在查：转圈 + 「正在加载应用…」；
        //   · 查失败了（`loaded` 仍为 null）：列表那边只在 `loaded != null` 时铺，所以失败那一档
        //     若什么都不画，用户看到的就是一片空白 —— 连「没读出来」那句话都没有。
        //     失败态自己一句（与 `emptyHint` 里那一档同一句），不必退回上一页再猜。
        PickListLoadingState(
            visible = ui.loaded == null && !ui.failed,
            topInset = topInset,
        )
        PickListFailedState(
            visible = ui.loaded == null && ui.failed,
            topInset = topInset,
        )
    }
}

/**
 * 取清单 —— 进页一次、模式翻转一次、下拉刷新一次。
 *
 *  「显示系统应用」开关一变，列表来源就变了，所以直接把它当重查的键。
 *  `refreshTick` 是下拉刷新那一头的触发键：两者复用这同一段逻辑。
 */
@Composable
private fun SettingsPage.AppListLoader(ui: AppPickerUiState) {
    val withSystem = picker.showSystemApps.value
    LaunchedEffect(withSystem, ui.refreshTick) {
        // 日志放在 effect 里而不是组合体里：组合体会随每次重组重跑，那几行会被打好几遍。
        if (ui.lastMode != null && ui.lastMode != withSystem) {
            XLog.w("选择应用：模式翻转（显示全部应用=$withSystem），清单回到加载态")
            ui.loaded = null
        }
        ui.lastMode = withSystem
        //  重查之前先把上一次的失败摘掉：加载态的判据要求 `!failed`（见 `AppPickerChrome`），
        //  不清的话重试时界面还挂着「查询失败」，连转圈都不出现 —— 看起来像按了没反应。
        ui.failed = false
        //  首帧先铺上一份缓存：**只在模式一致时**铺 —— 把「开着系统应用时查的清单」当关着时的
        //  首帧用，退出再进会先闪一屏系统应用（与 `cachedAppsWithSystem` 同一个理由）。
        //  下面照样重查并覆盖它，新装 / 卸载的应用不会因为这份缓存而看不见。
        val cached = picker.cachedApps.value
        if (ui.loaded == null && cached != null && picker.cachedAppsWithSystem == withSystem) {
            ui.loaded = cached
        }
        // 整段查询都在后台调度器上，一行都不许落在主线程：里面有 `getInstalledApplications`、
        // 逐个 `getLaunchIntentForPackage`（开着系统应用时是几百次 binder 调用）与每个包的
        // label 加载，全是阻塞式的。用 `IO` 而非 `Default`：这些活是 IO 型，而 `Default`
        // 是按 CPU 核数限流的共享池，压满它会拖住别的 Default 任务（转场、各页动画）。
        //  图标不在这一轮：它是按行按需取的（见 [AppIcon]）。
        XLog.w("打开应用页：取列表（显示全部应用=$withSystem，先用缓存 ${picker.cachedApps.value?.size ?: 0} 条）")
        val fresh = withContext(Dispatchers.IO) { loadLaunchableApps(context, withSystem) }
        // 查失败（null）不覆盖缓存，也不把当前列表打回空态：那是「这次没查到」，
        //  不是「没有应用可挑」。缓存里那份可能只是模式不同，留着比清空强；
        //  真要一份空清单，[loadLaunchableApps] 会正常返回 emptyList()。
        if (fresh == null) {
            XLog.w("选择应用：这次没查到（保留上一份清单 ${picker.cachedApps.value?.size ?: 0} 条）")
            ui.failed = true
        } else {
            //  图标缓存只在**清单真的变了**时才清（理由见 `APP_ICON_CACHE_SLOTS`：换图标包 / 换主题
            //  之后要重新取）。每次查询成功都清的话，一次下拉刷新会把滚过的几十个 `Drawable` 全丢掉，
            //  滚动回去又逐个问一遍 `PackageManager` —— 而清单没变时一个图标都不会变。
            if (fresh != picker.cachedApps.value) picker.iconCache.clear()
            picker.cachedApps.value = fresh
            picker.cachedAppsWithSystem = withSystem
            ui.loaded = fresh
            ui.failed = false
        }
        // 刷新收尾（成功失败都收）：`isRefreshing` 置回 false ⇒ 指示器结束并播完成动画。
        //  放在这里而不是 onRefresh 里等一个回调：这一段查询就是「刷新」本身，它跑完就算完。
        ui.refreshing = false
    }
}

/**
 * 已选的应用被两个落盘开关藏起来时，把选中清空。
 *
 *  开关一翻，「选中的那个」可能正好落进被藏起来的那一批：行没了，可右下角那颗 FAB 还亮着
 *  —— 按下去就把一个**已经看不见、也不该再选**的应用写进绑定。
 *
 *  注意：判据必须用 [AppTarget.sameApp]（只比用户 + 包名）：`pending` 可能是
 *  `包名/类名`（从二级页挑完退回来时就是），拿 [AppTarget.same] 去和「只有包名的应用行」比
 *  永远不相等 ⇒ 每次挑完界面回到本页，选中就被这里清掉：行尾图标全灭、FAB 消失，
 *  再点一次应用还会因为「没有选中」而落到默认档位上（真机踩到：「每次都是主应用」）。
 *
 *  注意：判据里**不含搜索词**（[AppPickerState.filter]）：搜索是临时找东西用的，被它筛掉就清空
 *  选中反而莫名其妙 —— 换个词那条又会回来。只管两个落盘开关藏掉的那些。
 *  注意：清单还没到手时**不判**（`loaded == null`，含「显示系统应用」翻转之后的加载态）：那时
 *  手里没有清单，判了就是把「还没查完」当成「没有这个应用」。
 */
@Composable
private fun SettingsPage.SelectionPrune(ui: AppPickerUiState) {
    val showBroken = picker.showUnopenable.value
    LaunchedEffect(showBroken, ui.apps, ui.pending) {
        val pending = ui.pending?.target ?: return@LaunchedEffect
        if (ui.loaded == null) return@LaunchedEffect
        if (ui.apps.none { AppTarget.sameApp(it.target, pending) && (showBroken || it.launchable) }) {
            XLog.w("选择应用：选中的应用被这一档藏起来了（显示失效=$showBroken）⇒ 清空选中")
            //  给**条件**挑的那一趟按集合记选中（可多选）：只清 `pending` 会让集合里留着
            //  一个「看不见却还算选着」的包名 —— FAB 消失、确认时却仍把它写进去。
            //  摘掉这一个，剩下的继续亮着。
            val rest = picker.pickedApps.value - AppTarget.pkg(pending)
            picker.pickedApps.value = rest
            ui.pending = rest.firstOrNull()?.let { PickedDetail(it, shortcutId = null) }
        }
    }
}

/** 下拉刷新那四句 —— 库默认是英文（"Pull down to refresh" …），这一页是中文界面。 */
internal fun pickerRefreshTexts(): List<String> =
    listOf(
        I18n.get(Strings.APP_PICKER_PULL_REFRESH, "下拉刷新"),
        I18n.get(Strings.APP_PICKER_RELEASE_REFRESH, "松开刷新"),
        I18n.get(Strings.APP_PICKER_REFRESHING, "正在刷新"),
        I18n.get(Strings.APP_PICKER_REFRESH_DONE, "刷新完成"),
    )
