package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.AppTarget
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.SectionTitle
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.collapseHeader
import com.lookie.opluskey.ui.collapsedHeaderHeight
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.model.APP_TITLE_SUMMARY_GAP
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.LaunchableActivity
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.spacedItems
import com.lookie.opluskey.ui.state.PickedDetail
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
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
    // null = 还在查；空清单 = 查完了、这个应用一个能外部打开的界面都没有
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

    val term = filter.trim()
    // 过滤与分节只在「清单 / 搜索词 / 选中项」变时算一次，**不能**留在这段组合体里直接算：
    //  下面那个空态要读「当前顶栏高度」，而那条读会让本函数每帧都重组一遍（见 [ActivityEmptyState]
    //  的注释）；跟着一起算就等于上下滑动时每帧跑两遍全量 filter，还会让 [PickerListData] 每帧换新
    //  实例 ⇒ [ActivityRows] 那一整棵（含列表 content lambda 与每一行的 `animateItem`）全都跳不过重组。
    val data =
        remember(loaded, term, target, selected, selectedShortcutId, shortcutsFailed) {
            val all = loaded.orEmpty()
            val shown = shownActivities(loaded, term)
            // 「当前绑的是哪一行」一律按**完整清单**判（`all`），不按过滤后的 `shown`：
            //  搜索词只决定显示什么，不决定绑的是什么。
            val normalized = resolveSelection(selected, target, all, shortcutsFailed = shortcutsFailed)
            PickerListData(
                target = target,
                selected = normalized,
                bound = selected != null,
                filterActive = term.isNotEmpty(),
                // 两节（快捷方式 / 界面）由它自己切，顺序固定「界面在前」
                shown = shown,
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
        ActivityRows(
            data = data,
            current = current,
            scrollBehavior = scrollBehavior,
            searchBandDp = searchBandDp,
            onSelect = { picked = it },
        )
        ActivityEmptyState(
            visible = loaded != null && data.isEmpty,
            // 「一条都没有」与「被搜索词筛没了」用不同的字形
            appsEmpty = loaded.isNullOrEmpty(),
            text = emptyText(failed, loaded, term),
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
 * 空态那一层 —— 单独一支只为一件事：把「当前顶栏高度」那条订阅**关进这一层**。
 *
 *  [currentHeaderHeight] 读的是 `scrollBehavior.state.heightOffset`，而它逐帧都在变 ——
 *  在谁的作用域里读，谁就每帧重组一次。原先这句直接写在 [ActivityPickerScreen] 的组合体里，
 *  代价是**整页**跟着每帧重组：分级过滤重跑、[PickerListData] 换新实例、列表 content lambda
 *  重建、可见行的 `Card` + `BasicComponent` + 两个带省略号的 `Text` 重新测量。真机上表现就是
 *  「上下滑动时快捷方式与「自动」那几行明显掉帧」。
 *
 *  挪进这里之后，滚动每帧只重组这一层（空态 / 一句加载文案），列表那棵树原地不动。
 *
 *  判据（显不显示 / 用哪颗字形 / 那句话）全由调用点算好传进来：它们都不读滚动态，
 *  在页面体算不会跟着滚动每帧跑一遍，也不必为了省参数把它们塞成一个只有三个字段的壳。
 *  包 `Box` 是因为 [PickListEmptyState] 要吃 `BoxScope`（它靠 `align` 定位），而本函数是
 *  `SettingsPage` 的扩展 —— 一个函数只能有一个显式接收者。
 *
 * @param visible 显不显示（`null` = 还在查时一律不铺：这一趟只有一次 binder，
 *   闪一下转圈比不铺更难受）
 */
@Composable
private fun SettingsPage.ActivityEmptyState(
    visible: Boolean,
    appsEmpty: Boolean,
    text: String,
    scrollBehavior: ScrollBehavior,
    searchBandDp: Dp,
) {
    Box(Modifier.fillMaxSize()) {
        PickListEmptyState(
            visible = visible,
            // 与「选择应用」页同一笔：空态别顶到带子底下
            topInset = currentHeaderHeight(scrollBehavior) + searchBandDp,
            appsEmpty = appsEmpty,
            text = text,
        )
    }
}

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
}

/**
 * 列表本体 —— 两节：界面（含「自动」）与快捷方式，右边贴一条滚动条。
 *
 *  单独一支是因为 [ActivityPickerScreen] 那个组合函数已经贴着 detekt 的长度上限。
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
    val tick = rememberTick()
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
                tick = tick,
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
 *  单独成函数有两个原因：[ActivityRows] 还要放 `Box` 与滚动条，留着这一整段会顶到 detekt 的
 *  `LongMethod` 上限；以及这几项的铺法要跟 [rowIndexOf] 那套下标账逐项对应，放一起好对。
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
    /** 点击触觉 —— 本层同样拿不到 `SettingsPage`，由调用方取好传来 */
    tick: () -> Unit,
    onSelect: (PickedDetail) -> Unit,
) {
    val activityEntries =
        buildList {
            add(
                ActivityEntry(
                    title = I18n.get(Strings.APP_PICKER_ACTIVITY_AUTO, "自动"),
                    summary = I18n.get(Strings.APP_PICKER_ACTIVITY_AUTO_HINT, "直接打开应用"),
                    // 「自动」= 这个应用本身：绑定里存的是 `包名|名字`，比的是身份不是字符串
                    picked = current?.let { AppTarget.same(it.target, data.target) } == true,
                    onClick = { onSelect(PickedDetail(data.target, null)) },
                ),
            )
            data.interfaces.forEach { activity ->
                add(
                    ActivityEntry(
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
        spacedItems(data.shortcuts, gap = GRID_GAP, key = { it.target }) { shortcut ->
            ActivityRow(
                title = shortcut.label,
                summary = shortcut.summary,
                picked = current?.let { AppTarget.same(shortcut.target, it.target) } == true,
                tick = tick,
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
    //  间距**不分档**：与全项目一样用默认那一套。没有快捷方式时「快捷方式」那条标题
    //  本来就不显示，这一条就是列表第一项 —— 默认档正好，不需要为它算一档。
    item(key = "section-interface") {
        SectionTitle(I18n.get(Strings.APP_PICKER_SECTION_ACTIVITIES, "Activity"))
    }

    // 这一节的第一项固定是「自动」：与绑定一个纯包名完全等价，也是用户最常选的那一项
    spacedItems(activityEntries, gap = GRID_GAP) { entry ->
        ActivityRow(
            title = entry.title,
            summary = entry.summary,
            picked = entry.picked,
            tick = tick,
            onClick = entry.onClick,
        )
    }
}

/**
 * 「自动」+ 各个界面 —— 合成一组交给 [spacedItems]：组的**首行不加**上间距、**末行不加**下间距，
 * 这一处判断只在组件里做一次。
 */
private class ActivityEntry(
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
    index += 1 // 「Activity」小节标题
    // 「自动（启动图标）」那一项：它的取值就是应用本身
    if (AppTarget.same(selected, data.target)) return index
    index += 1
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
    //  取折叠后的顶栏高度：定位会把顶栏折叠到底（与一级那页同一笔）。
    //  与一级页那一笔的差别：**不加 `GRID_GAP / 2`**。一级页每行是一张带行距的卡，
    //  那半档是给卡片间距留的；这一页的行是紧贴的列表项，加上去目标行会高出一截
    //  （真机反馈「定位位置不对」）。
    val listTopInsetPx =
        with(LocalDensity.current) {
            //  与上面那个 `header-space` 占位项**必须逐字一致**（它少了一档 [PAGE_TOP_PADDING]，
            //  这里也跟着少）：多算一档，定位出来的行就会偏下一截。
            (collapsedHeaderHeight(scrollBehavior) + searchBandDp).roundToPx()
        }
    // 「现在」被顶栏 + 搜索带挡住的那一段：此刻顶栏还是**展开**的，判「看不看得见」要用展开高
    //  （与上面那个「滚过去之后该落在哪」是两笔账）。
    val coveredPx =
        with(LocalDensity.current) {
            (currentHeaderHeight(scrollBehavior) + searchBandDp).roundToPx()
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
        if (index <= 0 || listState.rowFullyVisible(index, coveredPx)) {
            located = true
            return@LaunchedEffect
        }
        // `scrollToItem` 是程序性跳转，不走嵌套滚动 ⇒ 顶栏不会自己折叠，这里直接拉满
        collapseHeader(scrollBehavior)
        listState.scrollToItem(index, scrollOffset = -listTopInsetPx)
        XLog.w("选择界面：定位到已绑定的那一行 index=$index inset=$listTopInsetPx")
        located = true
    }
}

/**
 * 一行界面：名字 + 去包名的类名 + 单选圈。与一级那一行（`AppRow`）同一套版式。
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
 *  它的调用点在 [activityRows]（`LazyListScope` 的扩展）里 —— 一个函数只能有一个显式接收者，
 *  所以触觉那一格由调用方用 `rememberTick()` 取好、按 [tick] 传进来。
 *
 * @param tick 点击触觉（`SettingsPage.rememberTick()` 的产物）
 */
@Composable
private fun ActivityRow(
    title: String,
    summary: String,
    picked: Boolean,
    tick: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_MARGIN),
        //  **行自己不带任何外边距** —— 行距由 [spacedItems] 给（首行不加、末行不加）。
        //  这一行**没有行首图标**，高度交给内容 —— `BasicComponent` 自己的内边距上下对称，
        //  定死行高只会把两行字顶到上沿、底部空一截。
        // 点击与涟漪挂在卡片上：`BasicComponent` 只包住内容，点击若挂在它身上，
        // 涟漪画到内容底边就停了，卡片底部那一截摸不到反馈。
        pressFeedbackType = PressFeedbackType.None,
        showIndication = true,
        onClick = {
            tick()
            onClick()
        },
    ) {
        BasicComponent(
            // 行尾选中指示与一级同一套字形（空心 / 实心圈），不用 COUI 的单选圈
            endActions = {
                Icon(
                    if (picked) PageIcons.TileChecked else PageIcons.TileUnchecked,
                    contentDescription = null,
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
            // 不给 `onClick` —— 点击在外面那个 `Card` 上（理由见那里的注释）
        ) {
            Text(
                text = title,
                style = COUITheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = BasicComponentDefaults.titleColor().color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = APP_TITLE_SUMMARY_GAP),
                style = COUITheme.textStyles.body2,
                color = BasicComponentDefaults.summaryColor().color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
