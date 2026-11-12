package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.chrome.PageCornerFab
import com.lookie.opluskey.ui.chrome.PageFabButton
import com.lookie.opluskey.ui.chrome.collapseHeader
import com.lookie.opluskey.ui.chrome.currentHeaderHeight
import com.lookie.opluskey.ui.chrome.headerStatusBarPadding
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.model.icon.PageIcons
import com.lookie.opluskey.ui.model.page.GRID_GAP
import com.lookie.opluskey.ui.model.page.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.target.LaunchableActivity
import com.lookie.opluskey.ui.row.CardList
import com.lookie.opluskey.ui.row.CardRow
import com.lookie.opluskey.ui.row.RowEndIcon
import com.lookie.opluskey.ui.row.SectionTitle
import com.lookie.opluskey.ui.row.spacedItems
import com.lookie.opluskey.ui.state.picker.PickedDetail
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 「选择应用」页的第二级：挑**具体哪个界面**。
//
// 与第一级（`AppPickerList.kt`）分开成两个文件：两级的行内容、空态文案、筛选口径都不一样
// （一级筛应用名与包名，二级还要筛类名），挤在一个文件里会变成一堆「这个参数只对二级有效」的分支。
//
// 骨架与第一级**同一套**：搜索框挂在顶栏那条固定带里（`ActivitySearchBand`），列表顶上让出
// 「顶栏 + 带子」那一段，滚动由 `nestedScroll` 喂给顶栏 ⇒ 大标题跟着折叠、上下边距与那一页一致。
//
// 两级都有右下角那颗确认键，规则一致：**点行只挪标记，FAB 才写**。
// 这一页进页就默认选中「自动」（或绑定里记着的具体项），所以那颗 FAB 一进来就能按。

/**
 * 二级页：一个应用能外部打开的界面清单。
 *
 * @param target 应用那一行的目标写法（`包名`、`999@包名`，可能带 `|名字`）——
 *   也用来拼「自动」那一行的取值（原样交回去，就是「不指定界面」）。
 * @param selected 当前**已绑定**的目标写法（可能是 `包名`、也可能是 `包名/类名`）；
 *   为 null 表示还没绑过，此时一行都不点亮。
 * @param onPick 选中后交出去的完整目标写法：`包名`（自动）或 `包名/类名`（具体界面）。
 *
 *  搜索词是页面级状态（[SettingsPage.picker] 的 `activityFilter`）—— 搜索框在顶栏那棵树里，
 *  与页内容分处两棵，只能提上去；进页时由长按那一支清空。
 */
@Composable
internal fun SettingsPage.ActivityPickerScreen(
    target: String,
    selected: String?,
    /**
     * 当前绑定若是一条**快捷方式**，这里是它的 id（见 `SettingsPage.bindingShortcutId`）。
     *
     *  只给 [defaultPickOf] 的兜底用：清单还没到（或查失败）时，行拼不出来，
     *  身份只能靠这一位原样带回去 —— 否则会写成 `app:`（永远解析不了）。
     */
    selectedShortcutId: String?,
    /**
     * 选中一行后交出去的东西。
     *
     * @param picked 完整目标写法：`包名`（自动）、`包名/类名`（界面）、`包名/快捷方式id`（快捷方式）
     * @param shortcutId 非空 = 这是快捷方式那一节的行（写回走 `appshortcut:`）
     * @param label 这一行自己的名字（快捷方式的「扫一扫」）；界面 / 自动那一档为 `null`
     *   —— 它们的名字是**应用名**，落盘那一侧按目标现取（见 `appNameOf`）
     */
    onPick: (picked: String, shortcutId: String?, label: String?) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    val filter = picker.activityFilter.value
    // null = 还在查（含等注入侧那趟快捷方式回包）；空清单 = 查完了、这个应用一个界面都没有
    var loaded by remember(target) { mutableStateOf<List<LaunchableActivity>?>(null) }
    var failed by remember(target) { mutableStateOf(false) }
    //  快捷方式那一节这次没查出来（见 `LoadNotes`）：`resolveSelection` 据此不把
    //  已绑的 `appshortcut:` 降级成 `app:`。
    var shortcutsFailed by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) {
        val notes = LoadNotes()
        val fresh = withContext(Dispatchers.IO) { loadActivities(target, notes) }
        shortcutsFailed = notes.shortcutsFailed
        failed = fresh == null
        loaded = fresh ?: emptyList()
    }
    //  条数交给顶栏那条搜索带（它读不到这里的 `loaded`）：清单没到手时写 0，占位就不显示数字。
    //  写的是**这一页列出来的界面数**，不含快捷方式那一节 —— 占位写的是「搜索应用组件」。
    LaunchedEffect(loaded) { picker.activityCount.value = loaded.orEmpty().size }

    val term = filter.trim()
    // 过滤与分节只在「清单 / 搜索词 / 选中项」变时算一次，**不能**留在这段组合体里直接算：
    //  下面那个空态要读「当前顶栏高度」，而那条读会让本函数每帧都重组一遍（见 [ActivityPlaceholders]
    //  的注释）；跟着一起算就等于上下滑动时每帧跑两遍全量 filter，还会让 [PickerListData] 每帧换新
    //  实例 ⇒ [ActivityRows] 那一整棵（含列表 content lambda 与每一行的 `animateItem`）全都跳不过重组。
    val data =
        remember(loaded, term, target, selected, selectedShortcutId, shortcutsFailed) {
            val all = loaded.orEmpty()
            // 「当前绑的是哪一行」一律按**完整清单**判（`all`），不按过滤后的 `shown`：
            //  搜索词只决定显示什么，不决定绑的是什么。
            val normalized = resolveSelection(selected, target, all, shortcutsFailed = shortcutsFailed)
            PickerListData(
                target = target,
                selected = normalized,
                bound = selected != null,
                filterActive = term.isNotEmpty(),
                // 两节（快捷方式 / 界面）由它自己切，顺序固定「界面在前」
                shown = shownActivities(loaded, term),
                // 身份兜底只在「绑定没被 [resolveSelection] 改写」时给：清单已经查出这一项真不在了
                //  （normalized 退回「自动」）还按快捷方式写，等于给一个不存在的 id 拼 token。
                defaultPick =
                    defaultPickOf(
                        all = all,
                        selected = normalized,
                        target = target,
                        selectedShortcutId = if (normalized == selected) selectedShortcutId else null,
                    ),
            )
        }
    // 带子高度由顶栏那一侧量（`ActivitySearchBand`），这里只读 —— 与「选择应用」页同一条通道
    val searchBandDp = activitySearchBandHeight.dp()

    // 本次点中的那一行 —— **点行只改它，FAB 才写**（与一级页同一套：点行不写任何东西）。
    // 进页时它是 null ⇒ 用 [PickerListData.defaultPick]（「自动」或绑定里记着的具体项）。
    var picked by remember(target) { mutableStateOf<PickedDetail?>(null) }
    val current = picked ?: data.defaultPick

    Box(Modifier.fillMaxSize()) {
        // **拿全才铺列表**：`loaded == null` 这期间 `data` 是空清单，但 `showsAuto` 恒真 ——
        //  铺出去就是「Activity」小节标题 + 一行「自动」，它会从整页转圈底下透出来
        //  （`PageStatePlaceholder` 只做淡入淡出，不是不透明遮罩）。
        //  一级页也是这么做的：`AppPickerList` 只在 `loaded != null` 时铺。
        if (loaded != null) {
            ActivityRows(
                data = data,
                current = current,
                scrollBehavior = scrollBehavior,
                searchBandDp = searchBandDp,
                onSelect = { picked = it },
            )
        }
        ActivityPlaceholders(
            // **拿全才显示**（与「选择应用」页同一口径）：整趟查完 —— 含等注入侧那趟钉住的
            //  快捷方式回包 —— 之前一律整页转圈；`loaded` 为 null 时清单是空的，铺出去只会先列一半。
            loading = loaded == null && !failed,
            // 空态那三样依次是：查完了吗且一条都列不出来 / 是不是「清单本身就空」（选字形）/ 那句话
            empty = EmptySpec(loaded != null && data.isEmpty, loaded.isNullOrEmpty(), emptyText(failed, loaded)),
            scrollBehavior = scrollBehavior,
            searchBandDp = searchBandDp,
        )
        // 这一页唯一的写入入口：**清单真的到了才出现**。
        //  第一版是常驻（`visible = true`）：那几十毫秒里清单还是空的，`current` 走的是
        //  [defaultPickOf] 的兜底分支，一按就把「绑的是哪个界面 / 快捷方式」写成整个应用
        //  （快捷方式连身份都丢，落成一条永远解析不了的 token）。看不见的行不该能写下什么。
        //  **查询失败时也不出现**：失败那条路把 `loaded` 填成空表，只判非空会让用户在
        //  「什么都没查到」的页面上按确定，把绑定改写成「自动」。
        PageCornerFab(visible = loaded != null && !failed) {
            ActivityConfirmFab(current, onPick)
        }
    }
}

/**
 * 列表之外那两态（**整页转圈 / 空态**）—— 单独一支只为一件事：把「当前顶栏高度」那条订阅
 * **关进这一层**。
 *
 *  [currentHeaderHeight] 读的是 `scrollBehavior.state.heightOffset`，而它逐帧都在变 ——
 *  在谁的作用域里读，谁就每帧重组一次。原先这句直接写在 [ActivityPickerScreen] 的组合体里，
 *  代价是**整页**跟着每帧重组：分级过滤重跑、[PickerListData] 换新实例、列表 content lambda
 *  重建、可见行的 `Card` + `BasicComponent` + 两个带省略号的 `Text` 重新测量。真机上表现就是
 *  「上下滑动时快捷方式与「自动」那几行明显掉帧」。
 *
 *  挪进这里之后，滚动每帧只重组这一层（转圈 / 空态），列表那棵树原地不动。
 *
 *  判据（显不显示 / 用哪颗字形 / 那句话）全由调用点算好传进来：它们都不读滚动态，
 *  在页面体算不会跟着滚动每帧跑一遍，也不必为了省参数把它们塞成一个只有几个字段的壳。
 *  包 `Box` 是因为 [PickListEmptyState] 要吃 `BoxScope`（它靠 `align` 定位），而本函数是
 *  `SettingsPage` 的扩展 —— 一个函数只能有一个显式接收者。
 *
 * ## 两态与「选择应用」页共用同一套组件
 *
 *  转圈那颗、摆位、让开顶栏那一档全走 [PickListLoadingState] / [PickListEmptyState] ——
 *  两级页面的「正在读」长得一样是**有意**的：用户在两级之间来回跳，同一个加载态换一张脸
 *  只会让人以为这一页卡住了。这里只换文案（[Strings.APP_PICKER_LOADING_ACTIVITIES]）。
 *
 * @param loading 整页转圈（**拿全才显示**：一趟查询里既有界面那一节、又有等注入侧回包的快捷方式，
 *   整趟跑完之前不铺列表 —— 先铺出去只会先列一半、再自己多几行）
 * @param empty 空态那三样（显不显示 / 是不是「一条都没有」/ 那句话）；`null` = 不铺
 */
@Composable
private fun SettingsPage.ActivityPlaceholders(
    loading: Boolean,
    empty: EmptySpec?,
    scrollBehavior: ScrollBehavior,
    searchBandDp: Dp,
) {
    Box(Modifier.fillMaxSize()) {
        // 与「选择应用」页同一笔：别顶到带子底下
        val topInset = currentHeaderHeight(scrollBehavior) + searchBandDp
        PickListLoadingState(
            visible = loading,
            topInset = topInset,
            text = I18n.get(Strings.APP_PICKER_LOADING_ACTIVITIES, "正在加载应用组件…"),
        )
        if (empty != null) {
            PickListEmptyState(
                visible = empty.visible,
                topInset = topInset,
                appsEmpty = empty.appsEmpty,
                text = empty.text,
            )
        }
    }
}

/**
 * 空态那三样。
 *
 *  三样都是「怎么说这一页」，与「怎么摆」无关；摆法全在 [ActivityPlaceholders] 里。
 */
private class EmptySpec(
    val visible: Boolean,
    /** 清单本身是空的（而不是被搜索词筛没了）—— 决定用哪颗字形。 */
    val appsEmpty: Boolean,
    val text: String,
)

/**
 * 右下角那颗「确定选择」—— 本页**唯一的写入入口**。
 *
 *  点行只把标记挪过去（[PickedDetail]），点它才写配置。与一级页那颗是同一套手势与样式
 *  （`PageFabButton`），只是这一页交出去的是「具体界面 / 快捷方式」。
 *
 *  @param detail 当前选中（一定有值 —— 进页默认选中「自动」）
 */
@Composable
private fun SettingsPage.ActivityConfirmFab(
    detail: PickedDetail,
    onPick: (picked: String, shortcutId: String?, label: String?) -> Unit,
) {
    val tick = rememberTick()
    PageFabButton(
        onClick = {
            tick()
            onPick(detail.target, detail.shortcutId, detail.label)
        },
    ) {
        Icon(
            PageIcons.Confirm,
            contentDescription = I18n.get(Strings.APP_PICKER_CONFIRM_SELECTION, "确定选择"),
            tint = COUITheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 列表要用到的那一组值 —— 打包递进去，省得 [ActivityRows] 变成七个参数的函数。
 *
 *  由调用点 `remember` 出来（键：清单 / 搜索词 / 目标 / 选中项）：它是普通 class，等于只有引用相等，
 *  每帧新建一次就会让 [ActivityRows] 整棵跳不过重组。
 */
private class PickerListData(
    /** 应用那一行的目标（`包名` / `999@包名`，可能带 `|名字`）—— 就是「自动」那一项的取值。 */
    val target: String,
    /** **画勾用**的选中值：已规范过（见 [resolveSelection]）—— 一定落在「自动」或某一行上。 */
    val selected: String?,
    /** 进页那一刻到底**绑没绑过** —— 决定要不要定位（新绑时列表就在顶上，滚一下反而多一次位移）。 */
    val bound: Boolean,
    /** 有搜索词时不定位：过滤后的下标与定位用的下标对不上，滚过去只会滚错。 */
    val filterActive: Boolean,
    /** 过滤之后**要显示**的那些行（两节都从它切出来，见 [interfaces] / [shortcuts]）。 */
    shown: List<LaunchableActivity>,
    /**
     * 进页时**默认选中的那一条**：绑定里记着的具体项（界面 / 快捷方式），否则「自动」。
     *
     *  由调用点按**完整清单**算好（[defaultPickOf]）再传进来 —— 它是「当前绑的是什么」，
     *  与「这次搜索显示出哪几行」是两件事，不能在这两节（已过滤）里找。
     */
    val defaultPick: PickedDetail,
) {
    /** 界面那一节（不含「自动」）。 */
    val interfaces: List<LaunchableActivity> = shown.filter { it.shortcutId == null }

    /** 快捷方式那一节；空的时候整节不画。 */
    val shortcuts: List<LaunchableActivity> = shown.filter { it.shortcutId != null }

    /** 两节都空 —— 空态与「还在查」都靠它判（`loaded == null` 时这个值没有意义）。 */
    val isEmpty: Boolean get() = interfaces.isEmpty() && shortcuts.isEmpty()

    /**
     * 「自动」那一行画不画 —— **搜索时不列**：它说的是「这个应用本身」，与「界面名」不是一类东西。
     *
     *  列着的话，搜一个界面名而界面那一节全空时，页面上会同时出现「没有界面」和这一行可点的
     *  「自动」—— 看着像筛选没生效。
     */
    val showsAuto: Boolean get() = !filterActive

    /**
     * 「Activity」小节标题画不画 —— **那一节一条都没有时整节不画**，与「快捷方式」同一条规矩。
     *
     *  这是「有没有这一节」的唯一判据：铺列表（[activityRows]）与下标账（[rowIndexOf]）都读它。
     *  两处各写一遍的话，搜索空态就会只剩一条小标题浮在那儿（真机反馈「搜索没结果还显示小标题」），
     *  或者反过来下标错一行、定位滚偏。
     */
    val showsActivitySection: Boolean get() = showsAuto || interfaces.isNotEmpty()
}

/**
 * 列表本体 —— 两节：界面（含「自动」）与快捷方式，右边贴一条滚动条。
 *
 *  滚动条起在「折叠后的顶栏 + 搜索带」下面：那两截是叠在列表之上的固定浮层，从窗口顶边画起
 *  会被它们盖住一截（与「选择应用」页同一笔，见 `AppPickerList`）。
 */
@Composable
private fun SettingsPage.ActivityRows(
    data: PickerListData,
    /** 当前选中（画哪一行亮）；点行只改它。 */
    current: PickedDetail?,
    scrollBehavior: ScrollBehavior,
    searchBandDp: Dp,
    /** 点某一行：只把标记挪过去，不写。 */
    onSelect: (PickedDetail) -> Unit,
) {
    val listState = rememberLazyListState()
    LocateActivitySelected(data, scrollBehavior, listState, searchBandDp)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            // 大标题随滚动折叠：顶栏靠这条连接拿位移（`nestedScroll` 挂在滚动容器上，
            //  与「关于模块」/「按键绑定」那几页同一个写法）
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            // 底部留白与别页同一档（行距已经挪到行首，末尾不再自带一档）
            //  末行不留下行距 ⇒ 页底就是整档 [PAGE_BOTTOM_PADDING]，不用再减。
            contentPadding = PaddingValues(bottom = PAGE_BOTTOM_PADDING),
        ) {
            activityRows(
                data = data,
                current = current,
                headerInset = { currentHeaderHeight(scrollBehavior) + searchBandDp },
                onSelect = onSelect,
            )
        }
        // 滚动条：贴在右边，从「顶栏 + 搜索带」下沿开始画（见 [PickerScrollBar]）
        PickerScrollBar(
            listState = listState,
            scrollBehavior = scrollBehavior,
            searchBandDp = searchBandDp,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * 列表里铺的东西 —— 顶栏让位那一项、快捷方式那一节、界面（含「自动」）那一节。
 *
 *  单独成函数有两个原因：[ActivityRows] 还要放 `Box` 与滚动条；以及这几项的铺法要跟
 *  [rowIndexOf] 那套下标账逐项对应，放一起好对。
 *
 *  `currentHeaderHeight` 是在 `item` 的 content 里读的 —— 订阅只落在那一项里，不会让整页
 *  跟着顶栏折叠逐帧重组。
 */
private fun LazyListScope.activityRows(
    data: PickerListData,
    current: PickedDetail?,
    /**
     * 顶栏 + 搜索带那一段的总高。
     *
     *  收成一格 lambda 而不是传 `scrollBehavior` + `searchBandDp` 两个参数：`currentHeaderHeight`
     *  是 `SettingsPage` 的扩展，本层调不到；而且它跟着折叠**逐帧**变，只有在这个 `item` 的
     *  组合里现算，订阅才落在那一项上、不会让整页跟着滚动重组。
     */
    headerInset: @Composable () -> Dp,
    onSelect: (PickedDetail) -> Unit,
) {
    val activityEntries =
        buildList {
            //  **搜索时不列「自动」**（判据 `PickerListData.showsAuto`，与下标账同一处）：
            //  它说的是「这个应用本身」，与「界面名」不是一类东西。
            if (data.showsAuto) {
                add(
                    ActivityEntry(
                        key = "auto",
                        title = I18n.get(Strings.APP_PICKER_ACTIVITY_AUTO, "自动"),
                        summary = I18n.get(Strings.APP_PICKER_ACTIVITY_AUTO_HINT, "直接打开应用"),
                        // 「自动」= 这个应用本身：绑定里存的是 `包名|名字`，比的是身份不是字符串
                        picked = current?.let { AppTarget.same(it.target, data.target) } == true,
                        onClick = { onSelect(PickedDetail(data.target, null)) },
                    ),
                )
            }
            data.interfaces.forEach { activity ->
                add(
                    ActivityEntry(
                        //  **按目标取键，不按下标**：搜索过滤后行身份会按位次错位，
                        //  而且快捷方式与界面可能撞同名 —— 两节各带自己的前缀就不会撞。
                        key = "ac:${activity.target}",
                        title = activity.label,
                        summary = activity.summary,
                        picked = current?.let { AppTarget.same(activity.target, it.target) } == true,
                        onClick = { onSelect(PickedDetail(activity.target, null)) },
                    ),
                )
            }
        }

    // —— 头一项：顶栏让位 + 固定搜索带那一段的占位 ——
    //
    // 搜索框在固定层里，所以列表只要在顶上让出「顶栏 + 带子」这么多地方；滚起来
    // 之后列表钻到带子背后，由带子那层不透明底挡住。
    //
    //  **不再补 [PAGE_TOP_PADDING]**：这一页首行就是小标题（「快捷方式」/「Activity」），
    //  它自己带 16 + 8 的上间距 —— 再加一档就比别的页多出 8dp（用户口径）。
    item(key = "header-space") {
        Spacer(Modifier.height(headerInset()))
    }

    // —— 第一节：快捷方式 ——
    //  放在界面之前：这一节是应用自己给的、名字多半是中文，比一串类名好认。
    //  一个都没查到就整节不画（不是画一个空标题）：应用没注册快捷方式很常见。
    if (data.shortcuts.isNotEmpty()) {
        item(key = "section-shortcut") {
            SectionTitle(I18n.get(Strings.APP_PICKER_SECTION_SHORTCUTS, "快捷方式"))
        }
        //  **带节前缀取键**：快捷方式与界面可能写出同一个 target（快捷方式 id 恰好等于类名时），
        //  两节共用 `it.target` 会让 Compose 当场抛「Key was already used」。
        spacedItems(data.shortcuts, gap = GRID_GAP, key = { "sc:${it.target}" }) { shortcut ->
            ActivityRow(
                title = shortcut.label,
                summary = shortcut.summary,
                picked = current?.let { AppTarget.same(shortcut.target, it.target) } == true,
                onClick = {
                    // 只带它自己的名字（「扫一扫」）：快捷方式名字与应用名是两回事，
                    //  落盘时按这一条的身份（`999@包名/id`）单独记
                    onSelect(PickedDetail(shortcut.target, shortcut.shortcutId, shortcut.label))
                },
            )
        }
    }

    // —— 第二节：Activity ——
    //  两节都带小标题（`SmallTitle`，与「更多选项」「按键绑定」那几页同一颗）：
    //  快捷方式与界面混在一起时，光看行内容分不出「这是页面还是长按桌面图标那个」。
    //  **一条都没有时整节不画**（判据 `PickerListData.showsActivitySection`）：搜索空态时
    //  只剩一条小标题浮在「没有结果」上面，读起来像那一段还有东西没加载出来。
    //  间距**不分档**：与全项目一样用默认那一套。没有快捷方式时「快捷方式」那条标题
    //  本来就不显示，这一条就是列表第一项 —— 默认档正好，不需要为它算一档。
    if (data.showsActivitySection) {
        item(key = "section-interface") {
            SectionTitle(I18n.get(Strings.APP_PICKER_SECTION_ACTIVITIES, "Activity"))
        }
    }

    // 这一节的第一项固定是「自动」：与绑定一个纯包名完全等价，也是用户最常选的那一项
    spacedItems(activityEntries, gap = GRID_GAP, key = { it.key }) { entry ->
        ActivityRow(
            title = entry.title,
            summary = entry.summary,
            picked = entry.picked,
            onClick = entry.onClick,
        )
    }
}

/**
 * 「自动」+ 各个界面 —— 合成一组交给 [spacedItems]：组的**首行不加**上间距、**末行不加**下间距，
 * 这一处判断只在组件里做一次。
 */
private class ActivityEntry(
    /** 列表 key（带节前缀、按目标取）—— 不能用下标：搜索过滤后位次会变。 */
    val key: String,
    val title: String,
    val summary: String,
    val picked: Boolean,
    val onClick: () -> Unit,
)

/**
 * 一行在列表里的下标 —— 「顶栏占位 + 小节标题 + 行」三笔账，与 [ActivityRows] 的铺法逐项对应。
 *
 *  两节谁在前、某一节在不在，都会改变下标，所以只能这么算，不能写死。
 */
private fun rowIndexOf(
    data: PickerListData,
    selected: String,
): Int? {
    // 第一项是顶栏 + 搜索带的占位
    var index = 1
    if (data.shortcuts.isNotEmpty()) {
        index += 1 // 「快捷方式」小节标题
        val hit = data.shortcuts.indexOfFirst { AppTarget.same(it.target, selected) }
        if (hit >= 0) return index + hit
        index += data.shortcuts.size
    }
    // 「Activity」小节标题 —— 一条都没有时那一节整节不画（与 [activityRows] 同一判据）
    if (data.showsActivitySection) index += 1
    // 「自动（启动图标）」那一项：它的取值就是应用本身 —— **搜索时不列**（与 [activityRows] 同一判据）
    if (data.showsAuto) {
        if (AppTarget.same(selected, data.target)) return index
        index += 1
    }
    val hit = data.interfaces.indexOfFirst { AppTarget.same(it.target, selected) }
    return if (hit >= 0) index + hit else null
}

/**
 * 进页把列表滚到「当前绑定的那一行」—— 与「选择应用」页那条（`AppPickerList` 的 `LocateSelected`）
 * 同一套账：只做一次、只在没过滤时做、带一段负偏移把目标行从搜索带底下拉出来。
 */
@Composable
private fun SettingsPage.LocateActivitySelected(
    data: PickerListData,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    searchBandDp: Dp,
) {
    // 定位会顶到视窗顶部，而顶部被固定搜索带盖着 —— 不加负偏移就「滚了却看不见」。
    //  与一级页同一笔账（见 `LocateSelected`）：都取库的槽位常量、不读实测高度 —— 实测值会随
    //  折叠变（展开 684 → 折叠 491），按它算落点会多让一整段折叠量。落点按**折叠后**的遮挡算，
    //  判「看得见吗」按展开算。
    //  与一级页那一笔的差别：**不加 [PAGE_TOP_PADDING]**，与上面那个 `header-space` 占位项
    //  **逐字一致** —— 这一页的行是紧贴的列表项，多算一档目标行就偏下一截（真机反馈「定位位置不对」）。
    val (expandedPx, listTopInsetPx) =
        with(LocalDensity.current) {
            val head = headerStatusBarPadding()
            val expanded = (TopAppBarDefaults.ExpandedHeight + head + searchBandDp).roundToPx()
            val collapsed = (TopAppBarDefaults.CollapsedHeight + head + searchBandDp).roundToPx()
            expanded to collapsed
        }
    var located by remember { mutableStateOf(false) }
    LaunchedEffect(data.selected, data.shortcuts, data.interfaces, searchBandDp) {
        if (located) return@LaunchedEffect
        // 新绑（进页时本来就没绑过）不定位：列表就在顶上，滚一下反而多一次可见位移
        if (!data.bound) {
            located = true
            return@LaunchedEffect
        }
        val selected = data.selected ?: return@LaunchedEffect
        if (data.filterActive || searchBandDp <= 0.dp) return@LaunchedEffect
        // 清单还没到（两节都空）不算定位过：等它到了再走这一次
        if (data.shortcuts.isEmpty() && data.interfaces.isEmpty()) return@LaunchedEffect
        // 等一帧：顶栏的折叠量是**布局期**量出来的，进页那一次组合还读不到
        //  （读到 0 就等于「没折」），不等就会把目标行滚到展开的顶栏底下。
        withFrameNanos { }
        val index =
            rowIndexOf(data, selected) ?: run {
                // 进页本该滚到「已绑定的那一行」—— 找不到说明清单与绑定对不上（不是「没绑过」，
                //  那种在更上面就 return 了）。不说的话，用户只看到「进来停在顶上」。
                XLog.w("选择界面：清单里找不到已绑定的那一行，不定位（$selected）")
                located = true
                return@LaunchedEffect
            }
        // **已经完整可见就一动不动**（连顶栏都不折）—— 与一级页那条 `index > 0` 同一笔账：
        //  目标在头几行时，硬按「落到挡板下面」去滚会先折顶栏、再把上面那个小节标题顶出屏幕，
        //  进来那一下看着像页面自己跳了一下。只有真在屏幕外（或被挡板压着）才滚。
        //  首行（`index <= 0`）本就贴着顶上，滚一下只是多一次可见位移 —— 与「已可见」并成一条。
        if (index <= 0 || listState.rowFullyVisible(index, expandedPx)) {
            located = true
            return@LaunchedEffect
        }
        // `scrollToItem` 是程序性跳转，不走嵌套滚动 ⇒ 顶栏不会自己折叠，这里直接拉满
        collapseHeader(scrollBehavior)
        listState.scrollToItem(index, scrollOffset = -listTopInsetPx)
        //  落点按**实测**校一次（见 [settleRowBelow]）
        val landed = listState.settleRowBelow(index, listTopInsetPx)
        XLog.w("选择界面：定位到已绑定的那一行 index=$index 目标=$listTopInsetPx 落点=$landed")
        located = true
    }
}

/**
 * 一行界面 —— 与一级那一行（`AppRow`）同一个组件：**通用的一行卡**（[CardRow.Item] + [CardList]）。
 * 名字走标题、去包名的类名走副标题、行尾是选中指示（空心 / 实心圈，不用 COUI 的单选圈）。
 *
 * ## 为什么这一页**不给** `Modifier.animateItem()`
 *
 *  一级页给了（那一页的行在过滤时确实会增删换位），这一页有意不给。列表第一项是「顶栏让位」
 *  那个占位（`header-space`），它的高度跟着顶栏折叠**逐帧**变 —— 每变一次，后面所有行的
 *  placement 就跟着变一次。`animateItem` 把这种位移当成「item 换位」，每帧重启一遍 spring，
 *  于是内容一直在追手指：**上下滑动时紧贴顶栏的那几行（快捷方式、自动）明显"慢半拍"**。
 *
 *  真机实测过：滚动的帧耗时是干净的（UI 线程 traversals 中位 0.02ms、RenderThread 绘制
 *  1.85ms，都在 120Hz 的 8.33ms 预算内，`Frame deadline missed` 几乎为 0）—— 所以那不是掉帧，
 *  是内容位移被动画化的滞后感，看帧率数据永远看不出来，只能靠这一行改掉。
 *
 *  代价：搜索过滤时行是瞬切，不再淡入淡出。滚动是高频动作、过滤是低频动作，取舍往滚动这边偏。
 *
 * ## 为什么不是 `SettingsPage` 的扩展
 *
 *  它的调用点在 [activityRows]（`LazyListScope` 的扩展）里 —— 一个函数只能有一个显式接收者。
 *  按下那一声振动不再需要调用方传：`CardRow` 自己走页面那条 `LocalTick` 通道（与别页的行一致）。
 */
@Composable
private fun ActivityRow(
    title: String,
    summary: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    CardList(
        rows =
            listOf(
                CardRow.Item(
                    title = title,
                    summary = summary,
                    //  **行自己不带任何外边距** —— 行距由 [spacedItems] 给（首行不加、末行不加）。
                    //  这一行**没有行首图标**，高度交给内容（定死行高只会把两行字顶到上沿、底部空一截）。
                    //  选中那一行加一圈内边框（与一级页同一份，见 `CardRow.Item.selected`）
                    selected = picked,
                    //  标题跟着上色（与一级页同一条口径）
                    titleTint = if (picked) COUITheme.colorScheme.primary else null,
                    trailing = {
                        RowEndIcon(
                            icon = if (picked) PageIcons.TileChecked else PageIcons.TileUnchecked,
                            tint =
                                if (picked) {
                                    COUITheme.colorScheme.primary
                                } else {
                                    COUITheme.colorScheme.onSurfaceVariantActions
                                },
                        )
                    },
                    onClick = onClick,
                ),
            ),
    )
}

// 「选择界面」这一页的**判据**（纯函数）：只吃清单与几个值、只吐结果，不碰 Compose。
//
// 这几条判据多被**两个**地方读（默认选中 / 定位 / 空态文案），所以单摆一处，
// 免得下一个想加一行判断的人先得读一遍组合代码。
//
// 这里每一条都要拿**完整清单**算：搜索词只决定「显示什么」，不决定「绑的是什么」。

/**
 * 把「当前绑定」规范成**清单里确实存在的某一行** —— 找不到就退回「自动」。
 *
 *  默认选中「自动」是这一页的起点：用户从应用那一级点进来，多半就是要绑整个应用；
 *  绑定里若记着某个具体界面 / 快捷方式，且这次清单里还有它，才点亮那一行。
 *
 *  为什么要判「在不在清单里」：卸载重装、应用改版删掉某个活动、或者换成另一个应用进来 ——
 *  那时绑定里的那条在这份清单里根本不存在。不兜这一下，整页一行都不亮，看起来像从没绑过；
 *  兜成「自动」则始终有一行亮着，用户一眼知道当前是什么档。
 *
 *  ## `all` 必须是**完整清单**，不是搜索过滤之后的那份
 *
 *  过滤只决定「屏幕上显示哪几行」。拿过滤后的清单当判据的话，用户随手打一个字、把当前绑定的
 *  那一行筛掉了，这一页就会判成「那一项已经不在清单里」⇒ 选中态退回「自动」⇒ 他一按 FAB，
 *  `包名/类名` 就被写成了 `包名` —— 他只是搜了一下，绑定被降级了。
 */
internal fun resolveSelection(
    selected: String?,
    target: String,
    all: List<LaunchableActivity>,
    shortcutsFailed: Boolean = false,
): String? =
    when {
        // 没绑过 / 绑的就是这个应用 ⇒ 「自动」
        selected == null || AppTarget.same(selected, target) -> target

        // 清单还没到（或查完了确实一条都没有）⇒ **不知道**，不等于「不在」：
        //  这时保持原值。判成「自动」的话，进页那一瞬（列表还在查）按 FAB 就会把它降级。
        all.isEmpty() -> selected

        // 快捷方式那一节这次没查出来（宿主持有 ACCESS_SHORTCUTS 失败 / binder 抖动）：
        //  清单里缺的可能正是它，分不清「删了」与「没查到」—— 保持原值，不降级。
        //  代价是「真的被删掉的快捷方式」会多留一轮，下次查询成功时自然收敛。
        shortcutsFailed -> selected

        // 绑的是具体某一项，且它还在清单里 ⇒ 用它
        all.any { AppTarget.same(it.target, selected) } -> selected

        // 那一项已经不在清单里（界面被删 / 换了应用）⇒ 退回「自动」
        else -> target
    }

/**
 * 进页默认选中的那一条 —— 在**完整清单**里找 `selected` 对应的行。
 *
 *  按完整清单找（而不是按过滤后的两节）：搜索词筛掉了那一行时，选中态仍要停在它上面，
 *  FAB 才会把「原来绑的东西」原样写回去。快捷方式要连 `shortcutId` 一起带上 ——
 *  少了它，写回会走 `app:`（而不是 `appshortcut:`），`包名/快捷方式id` 这种写法就废了。
 *
 *  @param selected 已经过 [resolveSelection] 规范化 —— 一定在清单里，或就是 [target]（「自动」）
 *  @param selectedShortcutId 当前绑定是快捷方式时的 id（清单没到时唯一的身份来源，见下）
 */
internal fun defaultPickOf(
    all: List<LaunchableActivity>,
    selected: String?,
    target: String,
    selectedShortcutId: String? = null,
): PickedDetail {
    //  **先按快捷方式 id 找，再按 target 找**：同一个 target 底下可能既有「应用本身」那一行、
    //  又有快捷方式那几行（`app:pkg/活动` 与它的快捷方式用同一个 target）。只按 target 取第一行的话，
    //  用户选的那个快捷方式会被当成应用本身 —— id 丢了、名字退回应用名，写回去成了 `app:`。
    val hit =
        selectedShortcutId
            ?.let { id -> all.firstOrNull { it.shortcutId == id } }
            ?: all.firstOrNull { AppTarget.same(it.target, selected) }
    return when {
        // 快捷方式那一行要把它**自己的名字**（「扫一扫」）带上：不点行直接按 FAB 时，
        //  名字不能丢 —— 丢了就退回应用名，用户会看到「扫一扫」变成「微信」。
        hit != null && hit.shortcutId != null -> PickedDetail(hit.target, hit.shortcutId, hit.label)

        hit != null -> PickedDetail(hit.target, null)

        // 清单里找不到这一行（还在查 / 查失败 / 这一项真没了）⇒ 原样交回去。
        //  快捷方式的身份必须**跟着原值走**：清单没到时按 `hit == null` 直接给
        //  `PickedDetail(selected, null)`，写回就成了 `app:<包名>` —— 一行字都没改，
        //  绑定却被换成了一条永远解析不了的 token（图标与名字同时变）。
        //  清单已经查到「这一项真不在了」时调用点不会给 [selectedShortcutId]（见那里的注释），
        //  所以这里不会替一个不存在的 id 拼 token。
        //  **名字这一档拿不到**（清单里没有这一行，`hit` 必然是 null）：退回应用名。
        //  以前这里写 `hit?.label`，读起来像「带了名字」，其实恒为 null —— 是同一个结果的另一种写法。
        selectedShortcutId != null -> PickedDetail(selected ?: target, selectedShortcutId, null)

        else -> PickedDetail(selected ?: target, null)
    }
}

/**
 * 搜索命中 —— 名字、类名、拼音三者任一包含即算。
 *
 *  · 名字：活动自己声明了 label 的那些（「扫一扫」这类）；
 *  · 类名：绝大多数活动没有 label，只能靠类名认（`ui.LauncherUI`）；
 *  · 拼音：名字是汉字时，用户会打 `saoyisao` 而不是 `扫一扫`（键见 [LaunchableActivity.sortKey]）。
 *
 *  单独一支：命中判据并在这里，而不是再拆一个 `hit()`。
 */
internal fun shownActivities(
    all: List<LaunchableActivity>?,
    term: String,
): List<LaunchableActivity> {
    val list = all ?: return emptyList()
    if (term.isEmpty()) return list
    val needle = term.lowercase(java.util.Locale.ROOT)
    return list.filter {
        it.label.contains(term, ignoreCase = true) ||
            it.summary.contains(term, ignoreCase = true) ||
            it.name.contains(term, ignoreCase = true) ||
            it.sortKey.contains(needle)
    }
}

/**
 * 空态那句话 —— 真因优先：**没查出来**不是「一个都没有」。
 *
 *  最后一档**不复述搜索词**：那串字就写在上面的搜索框里，再说一遍只是把同一件事说两次。
 *  所以这里不再收 `term` —— 走到这一档时搜索框必然非空（空着的话 [shownActivities] 原样返回
 *  全部，轮不到空态），有没有词这件事已经不必再问。
 */
internal fun emptyText(
    failed: Boolean,
    all: List<LaunchableActivity>?,
): String =
    when {
        failed -> I18n.get(Strings.APP_PICKER_ACTIVITY_LOAD_FAILED, "列表没读出来，退回再进一次")
        all.isNullOrEmpty() -> I18n.get(Strings.APP_PICKER_ACTIVITY_EMPTY, "这个应用没有可从外部打开的 Activity")
        else -> I18n.get(Strings.APP_PICKER_NO_SEARCH_RESULT_ACTIVITY, "没有匹配的 Activity")
    }
