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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.collapseHeader
import com.lookie.opluskey.ui.collapsedHeaderHeight
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.escapeEmphasis
import com.lookie.opluskey.ui.model.APP_ROW_HEIGHT
import com.lookie.opluskey.ui.model.APP_TITLE_SUMMARY_GAP
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.LaunchableActivity
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.rememberTick
import com.lookie.opluskey.ui.state.PickedDetail
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
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
    LaunchedEffect(target) {
        val fresh = withContext(Dispatchers.IO) { loadActivities(target) }
        failed = fresh == null
        loaded = fresh ?: emptyList()
    }

    val term = filter.trim()
    // 过滤与分节只在「清单 / 搜索词 / 选中项」变时算一次，**不能**留在这段组合体里直接算：
    //  下面那个空态要读「当前顶栏高度」，而那条读会让本函数每帧都重组一遍（见 [ActivityEmptyState]
    //  的注释）；跟着一起算就等于上下滑动时每帧跑两遍全量 filter，还会让 [PickerListData] 每帧换新
    //  实例 ⇒ [ActivityRows] 那一整棵（含列表 content lambda 与每一行的 `animateItem`）全都跳不过重组。
    val data =
        remember(loaded, term, target, selected) {
            val shown = shownActivities(loaded, term)
            // 两节：快捷方式（`shortcutId` 非空）与界面。顺序固定为「界面在前」——
            //  用户多半是来找某个页面的，快捷方式是补充。
            val interfaces = shown.filter { it.shortcutId == null }
            val shortcuts = shown.filter { it.shortcutId != null }
            PickerListData(
                target = target,
                selected = resolveSelection(selected, target, shown),
                bound = selected != null,
                filterActive = term.isNotEmpty(),
                interfaces = interfaces,
                shortcuts = shortcuts,
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
        // 这一页唯一的写入入口：进页就有一颗（默认选着「自动」），点它才算挑了。
        PageCornerFab(visible = current != null) {
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
 *  @param detail 当前选中；`null` 时按钮不可见（理论上不会 —— 进页默认选中「自动」）
 */
@Composable
private fun SettingsPage.ActivityConfirmFab(
    detail: PickedDetail?,
    onPick: (picked: String, shortcutId: String?, label: String?) -> Unit,
) {
    val tick = rememberTick()
    PageFabButton(
        onClick = {
            tick()
            val picked = detail ?: return@PageFabButton
            onPick(picked.target, picked.shortcutId, picked.label)
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
    /** 界面那一节（不含「自动」）。 */
    val interfaces: List<LaunchableActivity>,
    /** 快捷方式那一节；空的时候整节不画。 */
    val shortcuts: List<LaunchableActivity>,
) {
    /** 两节都空 —— 空态与「还在查」都靠它判（`loaded == null` 时这个值没有意义）。 */
    val isEmpty: Boolean get() = interfaces.isEmpty() && shortcuts.isEmpty()

    /**
     * 进页时**默认选中的那一条**：绑定里记着的具体项（界面 / 快捷方式），否则「自动」。
     *
     *  在构造时就查好（而不是每次组合再 `firstOrNull` 一遍）：它只在 [selected] 变化时变。
     *  [selected] 已经过 [resolveSelection] 规范化 —— 一定落在「自动」或列表里某一项上，
     *  所以最后那条兜底分支拿到的就是「自动」。
     */
    val defaultPick: PickedDetail? =
        shortcuts.firstOrNull { AppTarget.same(it.target, selected) }?.let { PickedDetail(it.target, it.shortcutId) }
            ?: interfaces.firstOrNull { AppTarget.same(it.target, selected) }?.let { PickedDetail(it.target, null) }
            ?: PickedDetail(target, null)
}

/**
 * 把「当前绑定」规范成**列表里确实存在的某一行** —— 找不到就退回「自动」。
 *
 *  默认选中「自动」是这一页的起点：用户从应用那一级点进来，多半就是要绑整个应用；
 *  绑定里若记着某个具体界面 / 快捷方式，且这次清单里还有它，才点亮那一行。
 *
 *  为什么要判「在不在列表里」：卸载重装、应用改版删掉某个活动、或者换成另一个应用进来 ——
 *  那时绑定里的那条在这份清单里根本不存在。不兜这一下，整页一行都不亮，看起来像从没绑过；
 *  兜成「自动」则始终有一行亮着，用户一眼知道当前是什么档。
 */
private fun resolveSelection(
    selected: String?,
    target: String,
    shown: List<LaunchableActivity>,
): String? =
    when {
        // 没绑过 / 绑的就是这个应用 ⇒ 「自动」
        selected == null || AppTarget.same(selected, target) -> target

        // 绑的是具体某一项，且它还在这次清单里 ⇒ 用它
        shown.any { AppTarget.same(it.target, selected) } -> selected

        // 那一项已经不在清单里（界面被删 / 换了应用）⇒ 退回「自动」
        else -> target
    }

/**
 * 列表本体 —— 两节：界面（含「自动」）与快捷方式。
 *
 *  单独一支是因为 [ActivityPickerScreen] 那个组合函数已经贴着 detekt 的长度上限。
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
    LazyColumn(
        state = listState,
        // 大标题随滚动折叠：顶栏靠这条连接拿位移（`nestedScroll` 挂在滚动容器上，
        //  与「关于模块」/「按键绑定」那几页同一个写法）
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        // 底部留白与别页一模一样（[PAGE_BOTTOM_PADDING]）
        contentPadding = PaddingValues(bottom = PAGE_BOTTOM_PADDING),
    ) {
        // —— 头一项：顶栏让位 + 固定搜索带那一段的占位 ——
        //
        // 搜索框在固定层里，所以列表只要在顶上让出「顶栏 + 带子」这么多地方；滚起来
        // 之后列表钻到带子背后，由带子那层不透明底挡住。这一项与「选择应用」页那个
        // 占位项**逐字同源**（同样的三笔账），两页的上下边距才对得上。
        item(key = "header-space") {
            Spacer(
                Modifier.height(
                    currentHeaderHeight(scrollBehavior) + searchBandDp + PAGE_TOP_PADDING,
                ),
            )
        }

        // —— 第一节：快捷方式 ——
        //  放在界面之前：这一节是应用自己给的、名字多半是中文，比一串类名好认。
        //  一个都没查到就整节不画（不是画一个空标题）：应用没注册快捷方式很常见。
        if (data.shortcuts.isNotEmpty()) {
            item(key = "section-shortcut") {
                SmallTitle(I18n.get(Strings.APP_PICKER_SECTION_SHORTCUTS, "快捷方式"))
            }
            items(data.shortcuts, key = { it.target }) { shortcut ->
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
        item(key = "section-interface") {
            SmallTitle(I18n.get(Strings.APP_PICKER_SECTION_ACTIVITIES, "Activity"))
        }

        // 这一节的第一项固定是「自动」：与绑定一个纯包名完全等价，也是用户最常选的那一项
        item(key = "auto") {
            ActivityRow(
                title = I18n.get(Strings.APP_PICKER_ACTIVITY_AUTO, "自动"),
                summary = I18n.get(Strings.APP_PICKER_ACTIVITY_AUTO_HINT, "直接打开应用"),
                // 「自动」= 这个应用本身：绑定里存的是 `包名|名字`，比的是身份不是字符串
                picked = current?.let { AppTarget.same(it.target, data.target) } == true,
                onClick = { onSelect(PickedDetail(data.target, null)) },
            )
        }
        items(data.interfaces, key = { it.target }) { activity ->
            ActivityRow(
                title = activity.label,
                summary = activity.summary,
                picked = current?.let { AppTarget.same(activity.target, it.target) } == true,
                onClick = { onSelect(PickedDetail(activity.target, null)) },
            )
        }
    }
}

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
            (collapsedHeaderHeight(scrollBehavior) + searchBandDp + PAGE_TOP_PADDING).roundToPx()
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
 * 搜索命中 —— 名字、类名、拼音三者任一包含即算。
 *
 *  · 名字：活动自己声明了 label 的那些（「扫一扫」这类）；
 *  · 类名：绝大多数活动没有 label，只能靠类名认（`ui.LauncherUI`）；
 *  · 拼音：名字是汉字时，用户会打 `saoyisao` 而不是 `扫一扫`（键见 [LaunchableActivity.sortKey]）。
 *
 *  单独一支是因为上面那个组合函数已经贴着 detekt 的长度上限；命中判据并在这里而不是再拆
 *  一个 `hit()` —— 文件里的函数数量也贴上限了。
 */
private fun shownActivities(
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

/** 空态那句话 —— 真因优先：**没查出来**不是「一个都没有」。 */
private fun emptyText(
    failed: Boolean,
    all: List<LaunchableActivity>?,
    term: String,
): String =
    when {
        failed -> I18n.get(Strings.APP_PICKER_ACTIVITY_LOAD_FAILED, "界面列表没读出来，退回上一页再进来试一次")

        all.isNullOrEmpty() -> I18n.get(Strings.APP_PICKER_ACTIVITY_EMPTY, "这个应用没有可从外部打开的界面")

        // 用户输入会整体过一遍正文记号：先转义
        else -> I18n.format(Strings.APP_PICKER_NO_SEARCH_RESULT_ACTIVITY, escapeEmphasis(term))
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
 */
@Composable
private fun SettingsPage.ActivityRow(
    title: String,
    summary: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    val tick = rememberTick()
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_MARGIN)
            // 间距在高度之前给：行高量的是卡片自己，这 12dp 是卡片外面的行距
            .padding(bottom = GRID_GAP)
            .height(APP_ROW_HEIGHT),
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
            onClick = {
                tick()
                onClick()
            },
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
