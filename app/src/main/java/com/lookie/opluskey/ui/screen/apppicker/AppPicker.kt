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
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.host.action.AppEntry
import com.lookie.opluskey.host.action.MiniWindowSupport
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 「打开应用」页：应用列表的加载、选择与提交。
//
// 这一页原来是一个 538 行的函数（状态、四段 effect、列表、两处空态、FAB、两张确认窗全在里面）。
// 现在按「状态 / 加载 / 列表 / 外框 / 弹窗」分到五个文件：
//   · 本文件        —— 搜索带、页面骨架、三段 effect；
//   · `AppPickerUiState.kt` —— 这一趟的加载与选择状态（含派生清单）；
//   · `AppPickerList.kt`    —— 下拉刷新 + 列表 + 单行 + 进场定位；
//   · `AppPickerChrome.kt`  —— 两处空态与右下角那颗确认键；
//   · `AppPickerDialogs.kt` —— 两张拦人的确认窗。

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
    val ui = rememberAppPickerUiState(selected)
    // 「已选中那一项」变了（进页 / 绑定被改过）⇒ 本地选中跟着回到它。
    //  用 effect 而不是 `remember(selected)`：重建整份状态会把已经查到的清单一起丢掉，
    //  看起来像「选完又回到加载态」。
    LaunchedEffect(selected) { ui.pending = selected }
    AppListLoader(ui)
    SmallWindowGuard(ui)
    SelectionPrune(ui)

    val searchBandDp = appSearchBandHeight.dp()
    Column(Modifier.fillMaxSize()) {
        // 列表区（含加载提示）—— 两种状态占同一块地方：加载时不留白、加载完不跳一下。
        //
        // 右下角那颗「确定选择」也挂在这个盒子里：盒子就是「页面顶 → 页面底」那一整块，
        // 它的 `BottomEnd` 正好是页面右下角，与「按键绑定」页那颗 FAB 同一个落点。
        Box(Modifier.fillMaxSize()) {
            AppPickerList(ui, selected, scrollBehavior)

            // —— 除列表外的三种形态（一条都没查到 / 被搜索词筛没了 / 还在加载）——
            // 它们都挂在列表**外面**（同一层浮层），而不是当列表第一项：列表项一多，
            // 按下标滚到当前绑定应用的偏移就错一行。空态只在查完之后才有意义。
            val topInset = currentHeaderHeight(scrollBehavior) + searchBandDp
            PickListEmptyState(
                visible = (ui.loaded != null || ui.failed) && ui.shown.isEmpty(),
                topInset = topInset,
                appsEmpty = ui.apps.isEmpty(),
                text = emptyHint(ui),
            )
            PickListLoadingState(
                visible = ui.loaded == null && !ui.failed,
                topInset = topInset,
            )

            // 右下角那颗确认键才是「确定选择」：点行只切换。
            // 显隐：手上有选中的、且清单已经到手 —— 没得确认就不占地方；
            // 清单没到手时 `apps` 是空的，两道拦人的检查会双双跳过（等于把「还没核对」当成
            // 「没问题」照写），所以它是本页唯一的保存入口，必须藏起来。
            PageCornerFab(visible = ui.pending != null && ui.loaded != null) {
                AppConfirmFab(ui, onPick)
            }

            AppPickerDialogs(ui, onPick)
        }
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
        // 整段查询都在后台调度器上，一行都不许落在主线程：里面有 `getInstalledApplications`、
        // 逐个 `getLaunchIntentForPackage`（开着系统应用时是几百次 binder 调用）与每个包的
        // label 加载，全是阻塞式的。用 `IO` 而非 `Default`：这些活是 IO 型，而 `Default`
        // 是按 CPU 核数限流的共享池，压满它会拖住别的 Default 任务（转场、各页动画）。
        //  图标不在这一轮：它是按行按需取的（见 [AppIcon]）。
        XLog.w("打开应用页：取列表（显示全部应用=$withSystem，先用缓存 ${picker.cachedApps.value?.size ?: 0} 条）")
        val fresh = withContext(Dispatchers.IO) { loadLaunchableApps(withSystem) }
        // 查失败（null）不覆盖缓存，也不把当前列表打回空态：那是「这次没查到」，
        //  不是「没有应用可挑」。缓存里那份可能只是模式不同，留着比清空强；
        //  真要一份空清单，[loadLaunchableApps] 会正常返回 emptyList()。
        if (fresh == null) {
            XLog.w("选择应用：这次没查到（保留上一份清单 ${picker.cachedApps.value?.size ?: 0} 条）")
            ui.failed = true
        } else {
            // 图标缓存跟着新清单一起清（理由见 `APP_ICON_CACHE_SLOTS`）：清在 `loaded` 之前，
            //  新组合出来的行才会去取新图标，而不是命中上一批 `Drawable`。
            picker.clearIcons()
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
 * 「以小窗口打开」的**勾选态 + 选中的应用**每变一次，就问一次框架「它支不支持小窗」。
 *
 * 注意：**两个触发点都要，少一个就漏**——「点那一下」与「换应用」是同一件事的两半：
 *  · 先选应用、再点开开关 ⇒ 靠「开关变了」这一下问；
 *  · 先点开开关、再换应用 ⇒ 靠「选中的应用变了」这一下问。
 * 合起来才盖住**每一条会被写成 `window:<包名>` 的路径**。点确认键那一道是**兜底**
 * （见 [AppConfirmFab]）：前面判过不等于提交那一刻还是那个结论。
 *
 *  · **进页那一次不问**：靠 `askedPkg` 的初值兜住（见 [AppPickerUiState.askedPkg]）。
 *  · 关掉开关把它清空，再点开时会重新问；同一个包问过一次不再重复问（免得系统提示反复弹）。
 *  · 判不出来（`null`）放行；一个都没选中 ⇒ 什么都不做。
 *  · `apps` 也进 key：清单还没到手时问不了（拿不到 label），清单到了要补问一次
 *    —— 所以 `askedPkg` 在**真正问出去之后**才记。
 *  · 注意：别把它挪成「整表预判」：这个问法自带系统提示，整表问就是「进页弹一串 toast」。
 */
@Composable
private fun SettingsPage.SmallWindowGuard(ui: AppPickerUiState) {
    LaunchedEffect(picker.smallWindow.value, ui.pending, ui.apps) {
        if (!picker.smallWindow.value) {
            ui.askedPkg = null
            return@LaunchedEffect
        }
        val pkg = ui.pending ?: return@LaunchedEffect
        if (pkg == ui.askedPkg) return@LaunchedEffect
        val app = ui.apps.firstOrNull { it.packageName == pkg } ?: return@LaunchedEffect
        ui.askedPkg = pkg
        //  查询是 **binder 反射**（`AppEntry.resolve` 1~3 次 + `isSupportZoomMode` 一次），不能占
        //  主线程 —— 这里挂在 `LaunchedEffect`（主线程调度器）上，包一个 `IO`。
        //  点确认键那一道**有意留在主线程**：它要按查询结果当场决定「写还是拦」，异步化会造出
        //  「连点两下写两次」的新洞，而那是一次性用户动作、代价有界。
        val supported = withContext(Dispatchers.IO) { querySmallWindowSupport(pkg) }
        if (supported == false) {
            XLog.w("选择应用：$pkg 不支持小窗（勾选态/选中项刚变过），先弹确认")
            ui.blockedSmallWindow = SmallWindowBlock(app, fromPick = false)
        }
    }
}

/**
 * 已选的应用被两个落盘开关藏起来时，把选中清空。
 *
 *  开关一翻，「已选中的那一个」可能正好落进被藏起来的那一批：行没了，可右下角那颗确认键还亮着
 *  —— 点下去就把一个**已经看不见、也不该再选**的应用写进绑定。
 *
 *  注意：判据里**不含搜索词**（`AppPickerState.filter`）：搜索是临时找东西用的，被它筛掉就清空
 *  选中反而莫名其妙 —— 换个词那条又会回来。只管两个落盘开关藏掉的那些。
 *  注意：清单还没到手时**不判**（`loaded == null`，含「显示系统应用」翻转之后的加载态）：那时
 *  手里没有清单，判了就是把「还没查完」当成「没有这个应用」。
 */
@Composable
private fun SettingsPage.SelectionPrune(ui: AppPickerUiState) {
    val showBroken = picker.showUnopenable.value
    LaunchedEffect(showBroken, ui.apps, ui.pending) {
        val pkg = ui.pending ?: return@LaunchedEffect
        if (ui.loaded == null) return@LaunchedEffect
        if (ui.apps.none { it.packageName == pkg && (showBroken || it.launchable) }) {
            XLog.w("选择应用：已选的应用被这一档藏起来了（显示失效=$showBroken）⇒ 清空选中")
            ui.pending = null
        }
    }
}
