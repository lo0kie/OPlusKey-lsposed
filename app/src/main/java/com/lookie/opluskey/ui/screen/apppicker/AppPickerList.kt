package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.CardList
import com.lookie.opluskey.ui.CardRow
import com.lookie.opluskey.ui.CenteredInInsets
import com.lookie.opluskey.ui.Feedback
import com.lookie.opluskey.ui.RowEndIcon
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.collapseHeader
import com.lookie.opluskey.ui.collapsedHeaderHeight
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.headerStatusBarPadding
import com.lookie.opluskey.ui.model.APP_KIND_TAG_GAP
import com.lookie.opluskey.ui.model.APP_LIST_HEADER_ITEMS
import com.lookie.opluskey.ui.model.APP_ROW_HEIGHT
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.LaunchableApp
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.selectedRowRing
import com.lookie.opluskey.ui.spacedItems
import com.lookie.opluskey.ui.state.AppPickerState
import com.lookie.opluskey.ui.state.AppPickerUiState
import com.lookie.opluskey.ui.state.PickedDetail
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.theme.COUITheme
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
    // 选中那条的判据（[AppPickerUiState.pending]）：进页时它是已绑定的那一档，
    // 在二级页点了确认之后换成刚挑的那一档。它只管一圈内框 + 标题上色，行尾恒是箭头。
    val pending = ui.pending
    val row = rememberRowState(pending)
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
                // 底部留白与别页同一档（行距已经挪到行首，末尾不再自带一档）
                //  末行不留下行距 ⇒ 页底就是整档 [PAGE_BOTTOM_PADDING]，不用再减。
                contentPadding = PaddingValues(bottom = PAGE_BOTTOM_PADDING),
            ) {
                // 列表区 —— 加载中什么都不铺（列表空着），转圈由页面那层叠上去画。
                if (ui.loaded != null) {
                    // **一条都没有时，空态就铺在这里**（而不是浮在列表之上）：浮层不跟着列表动，
                    //  拖动时它一动不动，读起来像「这一页不能拖」（用户口径「空态要跟着拖动一起弹」）。
                    //  它占满一屏（`fillParentMaxHeight`）且顶上那个占位项此时不铺 —— 两者一起
                    //  才能让它在可见区里居中、又不多出一截能拖的空白。
                    //
                    //  这一项只在 `shown` 为空时存在：`LocateSelected` 按下标滚到当前绑定的那一行，
                    //  那时列表必然非空、`APP_LIST_HEADER_ITEMS` 的偏移照样成立。
                    if (ui.shown.isEmpty()) {
                        item(key = "empty-state") {
                            PickerEmptyItem(ui, emptyHint(ui), currentHeaderHeight(scrollBehavior) + searchBandDp)
                        }
                    } else {
                        // —— 头一项：顶栏让位 + 固定搜索框那条带子的占位 ——
                        //
                        // 搜索框在固定层里，所以列表只要在顶上让出「顶栏 + 那条带子」这么多地方；
                        //  滚起来之后列表钻到带子背后，由带子那层不透明底挡住。因此下面所有按下标
                        //  的滚动都要加 [APP_LIST_HEADER_ITEMS] 这个偏移。
                        item(key = "header-space") { PickerHeaderSpace(scrollBehavior, searchBandDp) }

                        // 「查得到但没有匹配项」那句提示不在这里：它一进来就会占掉一个下标，
                        // 而按下标滚到当前绑定应用的偏移 [APP_LIST_HEADER_ITEMS] 只算了顶上那个占位项
                        // —— 多一项就错一行。
                        spacedItems(ui.shown, gap = GRID_GAP, key = { it.target }) { app ->
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
 * 列表顶上那个占位项 —— 让出「顶栏 + 搜索框那条带子」，再补一档 [PAGE_TOP_PADDING]。
 *
 *  用动态值（见 [currentHeaderHeight]）：折叠阶段这一档跟着缩下去。
 *  带子的测量高度里含它自己那 8dp 下内边距；末尾补的那一档是因为这一页第一行是卡片，
 *  没有别页首行那个 `SmallTitle` 自带的上内边距 —— 不补的话卡片会直接贴着搜索框。
 */
@Composable
private fun SettingsPage.PickerHeaderSpace(
    scrollBehavior: ScrollBehavior,
    searchBandDp: Dp,
) {
    Spacer(
        Modifier.height(currentHeaderHeight(scrollBehavior) + searchBandDp + PAGE_TOP_PADDING),
    )
}

/**
 * 一条都没有时铺在列表里的那块空态 —— **占满一屏**，所以它在可见区里居中，而整块仍在列表里
 * （拖动时跟着一起弹，这正是它不做成浮层的原因）。
 *
 *  **要自己让出顶栏 + 搜索带**：内容一直铺到屏幕顶，而那两块是压在列表之上的固定浮层 ——
 *  不让出这一档，居中会落在**整屏**正中，看着偏上（真机反馈「没有垂直居中」）。
 *
 *  字形分两档：清单本身是空的（系统没给出可列的应用）用一摞盒子，被搜索词筛没了用那张脸 ——
 *  两者说的不是一件事。文案由调用点算（`emptyHint` 是 `SettingsPage` 上的扩展）。
 */
@Composable
private fun LazyItemScope.PickerEmptyItem(
    ui: AppPickerUiState,
    text: String,
    topInset: Dp,
) {
    //  摆位走全项目那一层（[CenteredInInsets]）：
    //  上下各让出该让的 —— 上面是顶栏 + 搜索带，下面是列表自己的底部留白（`contentPadding`
    //  那一档不参与这一项的测量，不让掉就会把中线抬高半档）。
    CenteredInInsets(
        modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
        topInset = topInset,
        bottomInset = PAGE_BOTTOM_PADDING,
    ) {
        Feedback(
            glyph = { tint ->
                Icon(
                    imageVector = if (ui.apps.isEmpty()) PageIcons.EmptyApps else PageIcons.NoMatch,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = tint,
                )
            },
            text = text,
        )
    }
}

/**
 * 点一行做什么 —— 两种入口语义不同，判据只在这里。
 *
 *  · **只认应用本身的那几趟**（[AppPickerState.appOnly]：给「条件执行」挑、给「应用控制 → 结束」
 *    挑）：那一支只存应用（条件是前台应用、动作是 `app:包名` 或 `proc:stop:包名`），没有
 *    「里面哪个界面」这回事 ⇒ 点一行就是**选中它**（那一行亮起内框、标题转主题色），
 *    右下角那颗确认键再落盘。
 *    · **给「前台应用」条件挑**（[AppPickerState.branchTarget]）：**多选**，点一下**切换**，
 *      可以亮好几行（一条条件存 `包1||包2`，任一在前台即通过）。去重仍生效：同一个应用
 *      已经用在**别的行**上就只提示、不选中 —— 已经在本条里的那颗必须能点掉，否则没法取消。
 *    · 其余（给那一支要执行的应用挑 / 结束应用）：单选。
 *  · 其余入口：点一行只是往下一级走，真正的选择发生在「选择界面」那一页、那颗 FAB 才落盘
 *    —— 在那一页直接返回，这里什么都不会变（新绑时仍是「无选中」）。
 */
private fun SettingsPage.pickRow(
    app: LaunchableApp,
    ui: AppPickerUiState,
    onOpenActivities: (String) -> Unit,
) {
    if (!picker.appOnly) {
        onOpenActivities(app.target)
        return
    }
    val conditionAt = picker.branchTarget
    // 给**条件**挑：**多选**，点一下切换选中（同一块里「前台应用」只有一条条件，多选装在它自己的
    //  取值里 —— `包1||包2`，任一在前台即通过）。其余入口（给动作挑、给手势挑）仍是单选。
    if (conditionAt != AppPickerState.NOT_FOR_BRANCH) {
        val pkg = AppTarget.pkg(app.target)
        //  点一下切换选中：**不在别的块上做去重** —— `[微信 + 充电]` 与 `[微信 + 耳机]` 是两条
        //  不同的场景，跨块拦会把第二块那条合法条件堵掉（见 `blockKindsOf` 的「跨块不禁」）。
        //  已经在本条条件里的那颗必须能点掉，否则多选之后没法取消其中一个。
        val now = picker.pickedApps.value
        val next = if (pkg in now) now - pkg else now + pkg
        picker.pickedApps.value = next
        // `pending` 跟着集合走：它是「FAB 显不显示 / 滚到哪一行」的判据，空集时不该留一个旧目标。
        ui.pending = next.firstOrNull()?.let { PickedDetail(it, shortcutId = null) }
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
 *  按住**滑块**这一下也走通用振动通道（[rememberTick]，与行、开关同一条）：抓住滑块时才振，
 *  按在轨道空白处不振（那里按下去什么都不会发生，振一下等于骗人）。
 *
 *  判据是那个 DOWN **有没有被 consume**：滚动条自己的指针处理在内层，只有按在滑块上时它才
 *  `consume()` 这个事件（按在轨道上它直接 return）。外层在 Main 阶段拿到的就是那个结果，
 *  于是不必在这里再算一遍滑块的几何 —— 库里改了尺寸，这里跟着对。
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
    val tick = rememberTick()
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier =
            modifier
                .fillMaxHeight()
                .padding(top = currentHeaderHeight(scrollBehavior) + searchBandDp)
                .pointerInput(tick) {
                    awaitEachGesture {
                        //  `requireUnconsumed = false`：要的正是「**已经**被 consume」的那一次按下
                        if (awaitFirstDown(requireUnconsumed = false).isConsumed) tick()
                    }
                },
    )
}

/**
 * 列表行的共享状态 —— 只跟「选中」与「为谁挑」变。
 *
 *  不 remember 的话，每次重组都给每一行做一个新实例 ⇒ 行永远跳不过重组
 *  （`RowState` 是普通 class，只有引用相等）。
 *  给**条件**挑的那一趟按集合算选中（可多选），其余入口按单个 `pending`。
 */
@Composable
private fun SettingsPage.rememberRowState(pending: PickedDetail?): RowState {
    val multi = picker.branchTarget != AppPickerState.NOT_FOR_BRANCH
    val pickedPkgs = picker.pickedApps.value
    return remember(pending, multi, pickedPkgs) {
        RowState(pending, multi, pickedPkgs)
    }
}

/**
 * 一行要用的那一个值 —— 打包递进去，省得 [AppRow] 多一个参数（detekt 上限是 6）。
 */
private class RowState(
    /** **当前选中**的那一条（进页时等于已绑定的那一档；二级页确认后是刚挑的那一条）。 */
    val pending: PickedDetail?,
    /** 这一趟是在给**条件**挑（而不是给那一支要执行的应用挑）—— 只有它支持多选。 */
    val multi: Boolean,
    /** 给条件挑时**已选的全部包名**；其余入口恒为空集（那时看 [pending]）。 */
    val pickedPkgs: Set<String>,
) {
    /**
     * 这一行（[target] 是应用那一级的 `包名` / `999@包名`）是不是当前选中的那个应用。
     *
     *  给**条件**挑的那一趟看集合（可以选中好几个）；其余入口看 [pending] 那一个目标。
     *
     *  这一位管三样：内框（[selectedRowRing]）、标题上色、以及行尾那一格换成实心圆勾
     *  （见 [AppRow] 里那一段说明）。
     */
    fun pickedBy(target: String): Boolean =
        if (multi) {
            AppTarget.pkg(target) in pickedPkgs
        } else {
            pending != null && AppTarget.sameApp(target, pending.target)
        }
}

/**
 * 一行应用 —— 走**通用的一行卡**（[CardRow.Item] + [CardList]），不再自己拼
 * `Card + BasicComponent`：整页的行距、行高、按压与振动、行首 / 行尾的槽位都由那一份定义。
 *
 *  内容仍是原来的四段：行首那颗应用图标（[AppIcon]，不是 `ImageVector` ⇒ 走 `startIcon`）、
 *  名字 + 「分身 / 模块 / 系统 / 用户 / 不可打开」小标签（`titleExtra`）、包名（副标题）、
 *  行尾那支右箭头（`trailing`）。
 *
 *  **点一行即进下一级**（挑这个应用里的具体界面 / 快捷方式），所以行尾画箭头；
 *  **选中那一行**换成实心圆勾（[PageIcons.TileChecked]，与按键列表的选中态同一颗）。
 *  类型不再由行尾说（顶栏那行字已经写了，见 `PickerHeaderText`）—— 原来「勾 / 一块屏 /
 *  表格 + 箭头」那三档区分类型，撤了之后行尾只回答「是不是它」。
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
    val withSystem = picker.showSystemApps.value
    // 这一行是不是当前选中的那个应用 —— 行尾那支箭头的颜色据此走（选中那行给主题色）
    val picked = row.pickedBy(app.target)
    //  「只认应用本身」的那几趟（结束应用、场景 / 条件里的多选）**不画行尾箭头**：
    //  它们点一行就是选中 / 切换，**没有下级页**，而箭头读作「点进去挑界面」——
    //  画上去等于告诉用户一个不存在的去处。判据与 `pickRow` 用的是同一个 `appOnly`。
    val noRowArrow = picker.appOnly
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = app.label,
                    //  标题与包名各**只给一行**、超出省略：这一行定高 [APP_ROW_HEIGHT]，
                    //  行高是按「一行标题 + 一行副标题」算的自然高度（图标要整高）。
                    //  不写明的话，长的折到第二行再被父级高度裁掉 —— 那是靠高度偶然裁出来的版式，
                    //  系统字号一调大，裁的位置就变（见 `CardRow.Item.titleMaxLines`）。
                    titleMaxLines = 1,
                    summary = app.packageName,
                    summaryMaxLines = 1,
                    startIcon = { AppIcon(app) },
                    titleExtra = { AppRowTags(app, withSystem) },
                    //  行高定死 ⇒ 行首那枚图标才能整高（见 [APP_ROW_HEIGHT]）。
                    //  行自己不带外边距：行距由 [spacedItems] 给（首行不加、末行不加）。
                    //  选中那一行加一圈**内边框**（见 [selectedRowRing]）：行尾那格已经不再
                    //  顺带说「选中了」（见下），这一圈就是「我挑的是这一个」的主要凭据。
                    modifier = Modifier.height(APP_ROW_HEIGHT).selectedRowRing(picked),
                    //  标题也跟着上色：内框管「这一行是当前这个」，标题管「读哪个名字」——
                    //  三百行里一眼扫下去，先落到的应该是字，不是那圈线。
                    titleTint = if (picked) COUITheme.colorScheme.primary else null,
                    trailing = {
                        //  行尾那一格**恒占位**，只换内容：
                        //  · 选中那一行 → **实心圆勾**（`TileChecked`，与按键列表的选中态同一颗）；
                        //  · 没有下级页的那几趟 → **空心圆圈**（`TileUnchecked`）：这一页的行是
                        //    「挑一个」，空圈与实心勾才是同一套单选说法 —— 见 [noRowArrow]；
                        //  · 其余行 → 右箭头（点下去还进下一级：挑这个应用里的具体界面 / 快捷方式）。
                        //  三档都是同一格 24dp，宽度不动 —— 否则库按行尾实际宽度算中间那列，
                        //  长包名会折行（真机表现是「勾一出现，两个标题一起上移」）。
                        when {
                            picked -> {
                                RowEndIcon(PageIcons.TileChecked, tint = COUITheme.colorScheme.primary)
                            }

                            noRowArrow -> {
                                RowEndIcon(
                                    PageIcons.TileUnchecked,
                                    tint = COUITheme.colorScheme.onSurfaceVariantActions,
                                )
                            }

                            else -> {
                                RowEndIcon(tint = COUITheme.colorScheme.onSurfaceVariantActions)
                            }
                        }
                    },
                    onClick = onClick,
                ),
            ),
    )
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
    // 顶上挡着的那两段 —— **都取库的常量，不读实测高度**：
    //  · [expandedPx]（展开态）：进场那一刻顶栏是展开的，「这一行是不是已经看得见」按它判；
    //  · [listTopInsetPx]（折叠态 + 一档 [PAGE_TOP_PADDING]）：`scrollToItem` 之前会先把顶栏折到底，
    //    目标行按它落位。
    //
    //  为什么不再读 `currentHeaderHeight`：它是「顶栏盒子 − 固定带」的**实测**值，而折叠会改变
    //  盒子的实测高度（库把外壳一起缩了：展开 684 → 折叠 491）。上报时机不同就读到不同的数 ——
    //  真机同一条路径上出现过 491 与 684 两个「遮挡」值，按它算的落点就会多让一整段折叠量
    //  （用户口径「滚动距离偏多」，目标行被塞到上一行下面）。两个常量是库自己的槽位高度，稳定；
    //  常量与实际差的那几 dp 由 `settleRowBelow` 那次实测校准收掉。
    //
    //  `scrollToItem` 收的是 px，而 `LocalDensity.current` 只能在组合期读，不能挪进 `LaunchedEffect`。
    val (expandedPx, listTopInsetPx) =
        with(LocalDensity.current) {
            val head = headerStatusBarPadding()
            val expanded = (TopAppBarDefaults.ExpandedHeight + head + searchBandDp).roundToPx()
            val collapsed = (TopAppBarDefaults.CollapsedHeight + head + searchBandDp).roundToPx()
            expanded to (collapsed + PAGE_TOP_PADDING.roundToPx())
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
        if (index <= 0 || listState.rowFullyVisible(index + APP_LIST_HEADER_ITEMS, expandedPx)) {
            ui.located = true
            return@LaunchedEffect
        }
        // `scrollToItem` 是程序性跳转，不走嵌套滚动那条路 ⇒ 顶栏不会自己折叠，这里直接把
        //  折叠量拉满（标题跟着收成小字，固定带随外壳一起上移 —— 落点按折叠后的遮挡算）。
        collapseHeader(scrollBehavior)
        listState.scrollToItem(index + APP_LIST_HEADER_ITEMS, scrollOffset = -listTopInsetPx)
        //  落点按**实测**校一次：常量与实际差多少、滚动有没有被顶栏吃掉，都由布局结果说话
        val landed = listState.settleRowBelow(index + APP_LIST_HEADER_ITEMS, listTopInsetPx)
        XLog.w(
            "定位到已选：index=$index 展开=$expandedPx 目标=$listTopInsetPx 落点=$landed" +
                " 顶栏 offset=${scrollBehavior.state.heightOffset}/limit=${scrollBehavior.state.heightOffsetLimit}",
        )
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
 * 行标题右边那几枚小标签： 「分身」+「模块 / 系统」+「不可打开」。
 *
 *  名字本身走 `CardRow.Item.title`（通用行的那一档字体），这一块只补名字后面那串标记 ——
 *  排在同一个 `Row` 里，名字长了先缩名字（见 `CardRow` 的 `titleExtra`）。
 *
 *  名字与**第一枚**标签之间的间隙由 `CardRow` 的 `titleExtra` 那一档给（[ROW_TITLE_EXTRA_GAP]），
 *  这里只管**标签之间**的间隙：只有第二枚起才在前面补 [APP_KIND_TAG_GAP]。
 *  每个标签前都加的话，名字到第一枚之间会是两档叠加（6 + 6 = 12dp），读着「标签离名字远了」
 *  （真机口径）。
 *
 *  **用户级不标**：没有标签就是用户应用，再挂一枚「用户」只是给默认情况加噪音。
 *  「系统」那一枚的显示条件不看「显示系统应用」开关（见下面那一支）。
 *
 *  「不可打开」只在**打开应用**那一趟出现（见下面那一支）：结束那一趟标它没有意义。
 */
@Composable
private fun SettingsPage.AppRowTags(
    app: LaunchableApp,
    withSystem: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 分身：一枚固定色的「分身」标签，**紧贴名字** —— 它回答的是「这一行是不是多开的那一份」，
        //  比「模块 / 系统」更该靠近名字（那两枚排在它后面）。
        //  第几个不看标签：名字里已经写着（`微信 1`），标签再用五种颜色区分只会让整屏更花。
        if (app.clone) {
            AppKindTag(
                text = I18n.get(Strings.APP_PICKER_CLONE_TAG, "分身"),
                tint = cloneTagTint(),
            )
        }
        // 「模块 / 系统」两枚，**用户级不标**（用户口径：默认就是用户级，不需要特别指出）——
        //  没有标签 = 用户应用。
        //
        //  「系统」这一枚**不能只看那个开关**：「结束应用」那一趟会把系统应用一并列出来
        //  （`includesIconless` 里 `stopApp` 强制为真），此时开关关着也得标 ——
        //  否则那一趟里看不出哪些是系统的，而这正是它要停的东西。
        //
        //  这一行是「盒子居中」而非 `alignByBaseline`：基线对齐会把胶囊整体
        //  压低小半档（大标题字面比标签高一截），居中 + 两边各沉 0.1em 之后两个字面的中线重合。
        val kindTag =
            when {
                app.xposed -> {
                    I18n.get(Strings.APP_PICKER_TAB_XPOSED, "模块") to appKindTagTint(true, app.system)
                }

                app.system && (withSystem || picker.stopApp) -> {
                    I18n.get(Strings.APP_PICKER_TAB_SYSTEM, "系统") to appKindTagTint(false, true)
                }

                else -> {
                    null
                }
            }
        if (kindTag != null) {
            //  前面已经画过一枚（分身）才补间隙；它是第一枚时由 `CardRow` 的 titleExtra 那档负责
            if (app.clone) Spacer(Modifier.width(APP_KIND_TAG_GAP))
            AppKindTag(text = kindTag.first, tint = kindTag.second)
        }
        // 「不可打开」：**只在「打开应用」那一趟标**（那一趟可能把这类应用藏起来，看得见的时候得
        //  知道点下去会失败）。「结束应用」那一趟不标 —— 停掉一个没有启动图标的应用正是它的用处，
        //  挂个红标签反而像在劝退。
        if (!picker.stopApp && !app.launchable) {
            //  前面画过任意一枚（分身 / 模块·系统）才补间隙
            if (app.clone || kindTag != null) Spacer(Modifier.width(APP_KIND_TAG_GAP))
            AppKindTag(
                text = I18n.get(Strings.APP_PICKER_TAG_UNOPENABLE, "不可打开"),
                tint = COUITheme.colorScheme.error,
            )
        }
    }
}
