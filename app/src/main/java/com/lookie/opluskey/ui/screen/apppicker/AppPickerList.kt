package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.collapseHeader
import com.lookie.opluskey.ui.collapsedHeaderHeight
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.hint
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
import com.lookie.opluskey.ui.model.opticalCenter
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.screen.command.branchConditionUsed
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.AppPickerUiState
import com.lookie.opluskey.ui.state.PickedDetail
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import io.github.suqi8.coui.kmp.utils.overScrollVertical

// 「选择应用」页的列表 —— 下拉刷新、行、以及进场的「滚到已选中那一行」。
//
// 从 `AppPicker.kt` 拆出来：这一段原来和页面骨架、四段 effect 挤在一个 538 行的函数里，
// 三处「按下标滚动」的偏移账（`APP_LIST_HEADER_ITEMS`）散在列表与 effect 两头。

/**
 * 列表本体 —— 下拉刷新包着 `LazyColumn`，右边贴一条滚动条。
 *
 *  注意：顶栏折叠那条 connection **交给 [PullToRefresh]**（`topAppBarScrollBehavior`）：库内部会把它
 *  串进自己那条链（Idle 时先喂顶栏、再喂刷新），所以 `LazyColumn` 上**不能**再挂 `nestedScroll`
 *  —— 留着同一段位移会喂两遍。
 *
 * ## 滚动条
 *
 *  挂在右边、从「**此刻的**顶栏 + 搜索带」下面起画 —— 那两截是叠在列表之上的固定浮层，
 *  从窗口顶边画起会被盖住一截。几何与理由都在 [PickerScrollBar] 里，两页共用那一支。
 *
 */
@Composable
internal fun SettingsPage.AppPickerList(
    ui: AppPickerUiState,
    /** 点某一行 → 进二级页挑这个应用里的具体界面。 */
    onOpenActivities: (String) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val searchBandDp = appSearchBandHeight.dp()
    // 行尾那枚图标画的是**当前选中的那个**（[AppPickerUiState.pending]）：进页时它是已绑定的
    // 那一档，在二级页点了确认之后换成刚挑的那一档。
    val pending = ui.pending
    // 行状态只跟「选中」与「为谁挑」变：不 remember 的话，每次重组都给每一行做一个新实例
    //  ⇒ 行永远跳不过重组（`RowState` 是普通 class，只有引用相等）
    val row = remember(pending, picker.forBranch) { RowState(pending, picker.forBranch) }
    LocateSelected(ui, scrollBehavior, listState, searchBandDp)

    Box(Modifier.fillMaxSize()) {
        PullToRefresh(
            isRefreshing = ui.refreshing,
            onRefresh = {
                ui.refreshing = true
                ui.refreshTick++
            },
            pullToRefreshState = pullToRefreshState,
            topAppBarScrollBehavior = scrollBehavior,
            modifier = Modifier.fillMaxSize(),
            // 注意：指示器要**下移到「顶栏 + 搜索框那条带子」之下**：这两块是叠在列表之上的
            //  固定浮层，指示器默认画在屏幕最顶上 ⇒ 整个被它们盖住。
            //  库把这个参数直接当指示器那一层的 `offset(y = …)` 用（`calculateTopPadding` → `offset`，
            //  见 `PullToRefreshKt`），并且**只取 top、不看 bottom** —— 所以下面那个 bottom 恒为 0。
            //
            //  末尾这额外一档 [PAGE_TOP_PADDING] 是为了**上下对称**：只让到「带子下沿」的话，
            //  指示器上边紧贴搜索框（0），下边却离第一行有一整档 [PAGE_TOP_PADDING]，
            //  看起来是「贴在搜索框上」。多让一档之后两边各占一份。
            contentPadding =
                PaddingValues(
                    top = currentHeaderHeight(scrollBehavior) + searchBandDp + PAGE_TOP_PADDING,
                ),
            refreshTexts = pickerRefreshTexts(),
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
                    items(ui.shown, key = { it.target }) { app ->
                        // **不给 `Modifier.animateItem()`** —— 理由写在 [AppRow] 的 KDoc 里：
                        // 它把「过滤之后下标变了」当成「这一行换位了」。
                        AppRow(
                            app = app,
                            row = row,
                            onClick = { pickRow(app, ui, onOpenActivities) },
                        )
                    }
                }
            }
        }
        // 滚动条：贴在右边，从「顶栏 + 搜索带」下沿开始画（见 [PickerScrollBar]）。
        //  库自带淡出 —— 不滚动时它会自己收起来，不用我们管显隐。
        PickerScrollBar(
            listState = listState,
            scrollBehavior = scrollBehavior,
            searchBandDp = searchBandDp,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * 点一行做什么 —— 两种入口语义不同，判据只在这里。
 *
 *  · **给「条件执行」挑**（[AppPickerState.forBranch]）：那一支只存一个包名，没有「里面哪个
 *    界面」这回事 ⇒ 点一行就是**选中它**（行尾画勾），右下角那颗确认键再落盘。
 *    其中**给条件挑**（[AppPickerState.branchTarget]）还要去重：同一个应用只能当一次条件，
 *    点到已经用过的那一行就**只提示、不选中** —— 等到按确认才说，用户已经点完一轮了。
 *  · 其余入口：点一行只是往下一级走，真正的选择发生在「选择界面」那一页、那颗 FAB 才落盘
 *    —— 在那一页直接返回，这里什么都不会变（新绑时仍是「无选中」）。
 */
private fun SettingsPage.pickRow(
    app: LaunchableApp,
    ui: AppPickerUiState,
    onOpenActivities: (String) -> Unit,
) {
    if (!picker.forBranch) {
        onOpenActivities(app.target)
        return
    }
    // 给**条件**挑才去重（给动作挑的是 `branchCommandTarget`，同一应用出现在两条动作里没坏处）
    val conditionAt = picker.branchTarget
    if (conditionAt != AppPickerState.NOT_FOR_BRANCH && branchConditionUsed(AppTarget.pkg(app.target), conditionAt)) {
        hint(I18n.get(Strings.COMMAND_BRANCH_DUPLICATE_APP, "这个应用已经加过了"))
        return
    }
    ui.pending = PickedDetail(app.target, shortcutId = null)
}

/**
 * 列表右边那条滚动条 —— 「选择应用」与「应用组件」两页共用这一支。
 *
 *  起在「**此刻的**顶栏 + 搜索带」下面：那两截是**叠在列表之上的固定浮层**，滚动条从窗口顶边
 *  画起的话上面一截会被它们盖住，看着像「凭空从中间冒出来」。
 *
 *  这里取 [currentHeaderHeight]（当前实际高度）而不是 [collapsedHeaderHeight] 是有过教训的：
 *  后者是**折叠到底**时的高度，而进页那一刻顶栏是展开的 —— 展开比折叠高出一整截，滚动条顶部
 *  那一段正好落在顶栏背后（用户口径「滚动条被 header 挡住了」）。搜索带那笔是页面级状态、
 *  首帧后就不再变。
 *
 *  代价：这一层会跟着顶栏折叠逐帧重组。但它本来就随滚动每帧更新（`rememberScrollBarAdapter`
 *  读的就是列表的滚动位移），重组的是**一个 thumb**，不是列表；把与滚动无关的页面留在外面
 *  才是要紧的那条（见 `ActivityPickerScreen` 里对 `ActivityEmptyState` 的处理）。
 *
 *  单独成函数还有两个原因：两页同一份几何不必各写一遍；以及两个调用点的组合函数都已经贴着
 *  detekt 的 `LongMethod` 上限。
 *
 * @param modifier 只用来传 `align`（调用点在 `Box` 里右对齐）
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun SettingsPage.PickerScrollBar(
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    searchBandDp: Dp,
    modifier: Modifier = Modifier,
) {
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier =
            modifier
                .fillMaxHeight()
                .padding(top = currentHeaderHeight(scrollBehavior) + searchBandDp),
    )
}

/** 下拉刷新那四句 —— 库默认是英文（"Pull down to refresh" …），这一页是中文界面。 */
private fun pickerRefreshTexts(): List<String> =
    listOf(
        I18n.get(Strings.APP_PICKER_PULL_REFRESH, "下拉刷新"),
        I18n.get(Strings.APP_PICKER_RELEASE_REFRESH, "松开刷新"),
        I18n.get(Strings.APP_PICKER_REFRESHING, "正在刷新"),
        I18n.get(Strings.APP_PICKER_REFRESH_DONE, "刷新完成"),
    )

/**
 * 一行要用的那一个值 —— 打包递进去，省得 [AppRow] 多一个参数（detekt 上限是 6）。
 */
private class RowState(
    /** **当前选中**的那一条（进页时等于已绑定的那一档；二级页确认后是刚挑的那一条）。 */
    val pending: PickedDetail?,
    /** 这一趟是在给「条件执行」挑（[AppPickerState.forBranch]）：那一趟没有下一级、也没有小窗。 */
    val forBranch: Boolean,
) {
    /** 这一行（[target] 是应用那一级的 `包名` / `999@包名`）是不是当前选中的那个应用。 */
    fun pickedBy(target: String): Boolean = pending != null && AppTarget.sameApp(target, pending.target)

    /**
     * 行尾那枚图标 —— 选中态四档：没选中 / 整个应用 / 里面某个界面 / 某个快捷方式。
     * 后两者在配置里都长成 `包名/xxx`（一边是类名、一边是快捷方式 id），光看字符串分不出来，
     * 只能靠 [PickedDetail.shortcutId] 判：有 id ⇒ 快捷方式（`table-shortcut`），
     * 没有 ⇒ 具体界面（`device-screen`）。
     *
     *  **给「条件执行」挑时只有两档**：选中画勾、没选中**什么都不画**。那一趟点一行就是选中
     *  （不进二级页），画「箭头 = 点进去能看到里面有什么」等于骗人。
     *
     * @return `null` = 这一行不画图标
     */
    fun iconFor(target: String): ImageVector? {
        // 先排除「没选中 / 选的是别的应用」——`pending` 收窄成非空之后才看它装的是哪一档
        val picked = pending
        if (picked == null || !AppTarget.sameApp(target, picked.target)) {
            // 没选中：给「条件执行」挑时没有下一级，不画箭头；其余入口画箭头（点进去能看到里面有什么）
            return if (forBranch) null else PageIcons.EnterArrow
        }
        if (forBranch) return PageIcons.TileChecked
        return when {
            // 有快捷方式 id ⇒ 快捷方式；否则目标里带 `/类名` 就是某个具体界面
            picked.shortcutId != null -> PageIcons.ShortcutMark

            AppTarget.activity(picked.target) != null -> PageIcons.ActivityScreen

            else -> PageIcons.TileChecked
        }
    }
}

/**
 * 一行应用：图标 + 名字（带「模块 / 系统 / 用户」「不可打开」标签）+ 包名 + 行尾指示。
 *
 *  **点一行即进下一级**（挑这个应用里的具体界面 / 快捷方式），所以行尾那枚图标画的是**当前选中**
 *  的四档之一（箭头 = 没选中 / 勾 = 整个应用 / 一块屏 = 里面某个界面 / 表格+箭头 = 某个快捷方式）。
 *
 * ## 为什么这一行**不给** `Modifier.animateItem()`
 *
 *  `animateItem` 按**下标**记位置：过滤一次（搜「微信」把列表收窄），后面每一行的下标都变了，
 *  它把这种变化当成「这一行**换位**了」，于是给每一行走一遍换位 spring —— 真机上的表现就是
 *  「末行（企业微信）从屏幕**底部**滑进来」，清空搜索时又从底部滑出去。滚动同理：顶栏逐步折叠时
 *  每一项的 placement 每帧都在变，行会一直追着手指跑。
 *
 *  「选择界面」那一页（`ActivityRow`）更早就撤掉了同一件，那边量过滚动帧耗时是干净的
 *  （UI 线程 traversals 中位 0.02ms、RenderThread 1.85ms）—— 说明这不是掉帧，是位移被动画化的滞后。
 *
 *  代价：过滤时行是瞬切，不再淡入淡出。滚动是高频动作、过滤是低频动作，取舍往滚动这边偏。
 */
@Composable
private fun SettingsPage.AppRow(
    app: LaunchableApp,
    row: RowState,
    /** 点这一行：进二级页挑具体界面（拉不起来的应用由调用点拦下）。 */
    onClick: () -> Unit,
) {
    val tick = rememberTick()
    val withSystem = picker.showSystemApps.value
    // 这一行是不是当前选中的那个应用 —— 标题与行尾那枚图标共用这一个判据（两处必须同色）
    val picked = row.pickedBy(app.target)
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_MARGIN)
            // 间距在高度之前给：这样 [APP_ROW_HEIGHT] 量的是卡片自己，那 12dp
            // 是卡片外面的行距（顺序反了会把卡片压矮 12dp）。
            .padding(bottom = GRID_GAP)
            // 行高定死 ⇒ 行首那枚图标才能整高（见 [APP_ROW_HEIGHT]）
            .height(APP_ROW_HEIGHT),
        // 按压反馈只要涟漪，与换之前 `BasicComponent` 那份同一套。
        // 不再有长按：点一下就是进下一级，「轻点 = 选中」那套已经没有了。
        pressFeedbackType = PressFeedbackType.None,
        showIndication = true,
        onClick = {
            tick()
            onClick()
        },
    ) {
        BasicComponent(
            startAction = { AppIcon(app) },
            // 行尾四档（判据见 [RowState.iconFor]）：
            //  · 里面某个**快捷方式** ⇒ 表格 + 箭头；
            //  · 里面某个**界面** ⇒ 一块屏；
            //  · 这个应用本身 ⇒ 一颗勾；
            //  · 没选中 ⇒ 箭头（点下去能看到里面有什么）。
            endActions = {
                // `null` = 这一行不画（给「条件执行」挑时，没选中的行没有「点进去」这回事）
                row.iconFor(app.target)?.let { icon ->
                    Icon(
                        icon,
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
                }
            },
            cardListPosition = CardListPosition.Full,
            // 不给 `onClick` —— 点击在外面那个 `Card` 上（理由见那里的注释）
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
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    searchBandDp: Dp,
) {
    // 「已选中的那个」= 本次选中；进页那一刻它被复位成当前绑定（见 `beginVisit`）
    val selected = ui.pending?.target
    val shown = ui.shown
    // 与 `ui.shown` 的过滤同口径：这里只用来判「有没有搜索词」，不去空格会把 `" "` 当成有词
    val filter = picker.filter.value.trim()
    // 列表顶上那一整段（顶栏 + 固定搜索带 + 首行上边距）—— 进场定位时目标行要落在它下面。
    // 取折叠后的顶栏高度（[collapsedHeaderHeight]）：定位会把顶栏折叠到底，按展开高度算会多
    // 让出一整段。带子高度含它自己那 8dp 下内边距，再加列表头那个占位项补的 [PAGE_TOP_PADDING]。
    // 再补半个行距（[GRID_GAP] / 2）：行距挂在每一行下边，目标行顶到带子下沿时上面那半个间距
    // 正好被带子吃掉，补一半上下两行的间距才均匀。
    // `scrollToItem` 收的是 px，而 `LocalDensity.current` 只能在组合期读，不能挪进 `LaunchedEffect`。
    val listTopInsetPx =
        with(LocalDensity.current) {
            (collapsedHeaderHeight(scrollBehavior) + searchBandDp + PAGE_TOP_PADDING + GRID_GAP / 2).roundToPx()
        }
    // 「现在」被顶栏 + 搜索带挡住的那一段：此刻顶栏还是**展开**的，判「看不看得见」要用展开高
    //  （与上面那个「滚过去之后该落在哪」是两笔账）。
    val coveredPx =
        with(LocalDensity.current) {
            (currentHeaderHeight(scrollBehavior) + searchBandDp).roundToPx()
        }
    LaunchedEffect(selected, shown, searchBandDp) {
        if (ui.located) return@LaunchedEffect
        if (selected == null || filter.isNotBlank()) return@LaunchedEffect
        if (searchBandDp <= 0.dp) return@LaunchedEffect
        // 一行一个包：绑定里可能带着 `/类名`，所以按「同一个应用」比，不按整串比
        // 等一帧：折叠量在布局期才量得出来，进页那一次组合读到的是 0。
        withFrameNanos { }
        val index = shown.indexOfFirst { AppTarget.sameApp(it.target, selected) }
        // 清单还没到（`shown` 空）不算定位过：等它到了再走这一次
        if (shown.isEmpty()) return@LaunchedEffect
        // **已经完整可见就一动不动**（连顶栏都不折）—— 目标挤在头两三行时，硬按「落到挡板
        //  下面」去滚只会把它顶上去半截：先折顶栏（列表整体上移一截）再按展开时的高度补偿，
        //  两边对不上，表现出来就是「选中第二、第三行时那一行被 header 压掉一半多」。
        //  二级页（`LocateActivitySelected`）一直是这么判的，一级页这边原先漏了。
        if (index <= 0 || listState.rowFullyVisible(index + APP_LIST_HEADER_ITEMS, coveredPx)) {
            ui.located = true
            return@LaunchedEffect
        }
        // `scrollToItem` 是程序性跳转，不走嵌套滚动那条路 ⇒ 顶栏不会自己折叠，跳完还是
        // 展开的、把目标行压在下面，因此直接把折叠量拉满。
        collapseHeader(scrollBehavior)
        XLog.w(
            "定位到已选：index=$index 顶栏 offset=${scrollBehavior.state.heightOffset}" +
                "/limit=${scrollBehavior.state.heightOffsetLimit} inset=$listTopInsetPx",
        )
        listState.scrollToItem(index + APP_LIST_HEADER_ITEMS, scrollOffset = -listTopInsetPx)
        ui.located = true
    }
}

/**
 * [index] 那一行此刻是不是**已经完整落在挡板下面** —— 是的话进页就不用滚（也不折顶栏）。
 *
 *  「挡板」= 顶栏 + 固定搜索带（`coveredPx`）：它们叠在列表之上，落进这一段的行看着像被切掉半截。
 *  `offset` 是行顶到视窗顶的距离（`LazyListState` 的口径），所以上下两个边界都得比。
 *
 *  放在这一页是因为**两级共用**：一级的 [LocateSelected] 与二级的 `LocateActivitySelected`
 *  都要判「目标行是不是已经看得见」。二级那页自己的函数数已经贴上限，所以落在这一边。
 */
internal fun LazyListState.rowFullyVisible(
    index: Int,
    coveredPx: Int,
): Boolean {
    val info = layoutInfo
    val row = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return row.offset >= coveredPx && row.offset + row.size <= info.viewportEndOffset
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
            // （`fill = false`：名字短时不去撑满，标签紧跟在右边）。
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .opticalCenter(COUITheme.textStyles.headline1),
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = BasicComponentDefaults.titleColor().color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 分身：一枚固定色的「分身」标签，**紧贴名字** —— 它回答的是「这一行是不是多开的那一份」，
        //  比「模块 / 系统 / 用户」更该靠近名字（那几枚排在它后面）。
        //  第几个不看标签：名字里已经写着（`微信 1`），标签再用五种颜色区分只会让整屏更花。
        if (app.clone) {
            Spacer(Modifier.width(APP_KIND_TAG_GAP))
            AppKindTag(
                text = I18n.get(Strings.APP_PICKER_CLONE_TAG, "分身"),
                tint = cloneTagTint(),
            )
        }
        // 这一行是「盒子居中」而非 `alignByBaseline`：基线对齐会把胶囊整体
        // 压低小半档（大标题字面比标签高一截），居中 + 两边各沉 0.1em 之后两个字面的中线重合。
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
