package com.lookie.opluskey.ui.screen.apppicker

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.collapsedHeaderHeight
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.APP_KIND_TAG_GAP
import com.lookie.opluskey.ui.model.APP_LIST_HEADER_ITEMS
import com.lookie.opluskey.ui.model.APP_ROW_HEIGHT
import com.lookie.opluskey.ui.model.APP_TITLE_SUMMARY_GAP
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.rememberTick
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

// 「选择应用」页的列表 —— 下拉刷新、行、以及进场的「滚到已选中那一行」。
//
// 从 `AppPicker.kt` 拆出来：这一段原来和页面骨架、四段 effect 挤在一个 538 行的函数里，
// 三处「按下标滚动」的偏移账（`APP_LIST_HEADER_ITEMS`）散在列表与 effect 两头。

/**
 * 列表本体 —— 下拉刷新包着 `LazyColumn`。
 *
 *  ⚠️ 顶栏折叠那条 connection **交给 [PullToRefresh]**（`topAppBarScrollBehavior`）：库内部会把它
 *  串进自己那条链（Idle 时先喂顶栏、再喂刷新），所以 `LazyColumn` 上**不能**再挂 `nestedScroll`
 *  —— 留着同一段位移会喂两遍。
 *
 * @param selected 「当前绑定的是哪个包」—— 只用于进场定位；点行改的是 [AppPickerUiState.pending]。
 */
@Composable
internal fun SettingsPage.AppPickerList(
    ui: AppPickerUiState,
    selected: String?,
    scrollBehavior: ScrollBehavior,
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val searchBandDp = appSearchBandHeight.dp()
    LocateSelected(ui, selected, scrollBehavior, listState, searchBandDp)

    PullToRefresh(
        isRefreshing = ui.refreshing,
        onRefresh = {
            ui.refreshing = true
            ui.refreshTick++
        },
        pullToRefreshState = pullToRefreshState,
        topAppBarScrollBehavior = scrollBehavior,
        modifier = Modifier.fillMaxSize(),
        // ⚠️ 指示器要**下移到「顶栏 + 搜索框那条带子」之下**：这两块是叠在列表之上的
        //  固定浮层，指示器默认画在屏幕最顶上 ⇒ 整个被它们盖住。
        //  库把这个参数直接当指示器那一层的 `offset(y = …)` 用，所以这里给的正是「让出固定层」的同一笔账。
        contentPadding = PaddingValues(top = currentHeaderHeight(scrollBehavior) + searchBandDp),
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

            // 列表区 —— 加载中什么都不铺（列表空着），转圈由页面那层叠上去画。
            if (ui.loaded != null) {
                // 「查得到但没有匹配项」那句提示不在这个列表里：它一进来就会占掉一个下标，
                // 而按下标滚到当前绑定应用的偏移 [APP_LIST_HEADER_ITEMS] 只算了顶上那个占位项
                // —— 多一项就错一行。
                items(ui.shown, key = { it.packageName }) { app ->
                    // `animateItem()` 是 `LazyItemScope` 的成员 —— 只能在 `items { }` 里取到，
                    // 所以由调用点建好递进去（`AppRow` 自己没有那个作用域）。
                    AppRow(app, ui.pending, Modifier.animateItem()) { ui.pending = it }
                }
            }
        }
    }
}

/** 一行应用：图标 + 名字（带「模块 / 系统 / 用户」「不可打开」标签）+ 包名 + 单选圈。 */
@Composable
private fun SettingsPage.AppRow(
    app: LaunchableApp,
    pending: String?,
    /** 由调用点给的 `Modifier.animateItem()` —— 出现 / 消失 / 换位都带动画。 */
    itemModifier: Modifier,
    onSelect: (String) -> Unit,
) {
    val tick = rememberTick()
    val withSystem = picker.showSystemApps.value
    Card(
        itemModifier
            .fillMaxWidth()
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
                onSelect(app.packageName)
            },
        ) {
            AppRowTitle(app, withSystem)
            Text(
                text = app.packageName,
                modifier = Modifier.padding(top = APP_TITLE_SUMMARY_GAP),
                style = COUITheme.textStyles.body2,
                color = BasicComponentDefaults.summaryColor().color,
            )
        }
    }
}

/**
 * 进场把列表滚到「当前绑定的那个应用」（**只做一次**）。
 *
 *  只在没在过滤、且不在第一项时才滚（第一项本就在眼前，滚了反而多一次位移）。
 *  下标要加 [APP_LIST_HEADER_ITEMS]（列表头是「顶栏让位 + 搜索带」那一项）。
 *  还必须带一段负偏移：`scrollToItem` 把目标顶到视窗顶部，而顶部被固定搜索带盖着，不加偏移
 *  目标行正好藏在带子底下，表现就是「滚了却看不见」。带子高度首帧还没量到时先不滚。
 *
 *  key 里不放 `listTopInsetPx`：它跟着顶栏折叠走（用户一滚就变），放进去等于每滚一下都被拽回。
 *
 *  `shown` 那一项留着（定位要读它算下标），但它同时是**重查完成**的信号：后台那遍查完
 *  `loaded` 一换、`shown` 跟着换新实例，effect 就会重跑。定位是「进场那一次」的事，
 *  所以用一个一次性闸 `located` 兜住 —— 否则用户自己滚到别处、清单查完又被猛拽回已选行。
 */
@Composable
private fun SettingsPage.LocateSelected(
    ui: AppPickerUiState,
    selected: String?,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    searchBandDp: Dp,
) {
    val shown = ui.shown
    val filter = picker.filter.value
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
    LaunchedEffect(selected, shown, searchBandDp) {
        if (ui.located) return@LaunchedEffect
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
        ui.located = true
    }
}

/**
 * 行标题：名字 + 「模块 / 系统 / 用户」+「不可打开」两枚小标签。
 *
 *  自己排而不是走库的 `title` 参数：那两个参数只吃 `String`，而这里要在名字右边挂标签。
 *  样式照库的默认内容来。
 */
@Composable
private fun SettingsPage.AppRowTitle(
    app: LaunchableApp,
    withSystem: Boolean,
) {
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
        // 压低小半档（大标题字面比标签高一截），居中反而让两个字面的中线基本重合。
        // 「模块 / 系统 / 用户」只在「显示系统应用」开着时画；「模块」不看那个开关
        // —— 模块默认就在列表里，开关关着也得分得出来。
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
}
