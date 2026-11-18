package com.lookie.opluskey.ui.chrome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.ProcClock
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.i18n.applyLanguage
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.feedback.hint
import com.lookie.opluskey.ui.feedback.rememberTick
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.page.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.theme.DISABLED_ALPHA
import com.lookie.opluskey.ui.model.theme.isDarkScheme
import com.lookie.opluskey.ui.state.page.PageLoadSnapshot
import com.lookie.opluskey.ui.state.page.readPageLoadSnapshot
import com.lookie.opluskey.ui.state.signal.BandHeight
import com.lookie.opluskey.ui.style.LocalMonetColors
import com.lookie.opluskey.ui.style.syncNight
import com.lookie.opluskey.ui.wire.primeLocalTag
import com.lookie.opluskey.ui.wire.publishConfig
import io.github.suqi8.coui.kmp.basic.TabRowColors
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 顶栏底下那条**分段控件带子** —— 「按键绑定」（三个手势）、「组合键」编辑页（短按 / 长按）、
// 「自定义命令」（单条 / 多条）共用这一份；「条件执行」用它的**无分页器**那一档（见下）。

/**
 * 一条**分段控件带**：挂在**顶栏的 `bottomContent`** 里（见 `PageHeader` 的 `bottomContent`
 * 与 `SettingsPage.PageBottomBand` 那处分派）。
 *
 * 三件事都在这一层定死，调用点只给「哪几档、可不可点、高度报给谁」：
 *  1. **亮哪一档由分页器说了算**（`currentPage`）：点它去翻页（`animateScrollToPage`），
 *     反过来标签的粗细与颜色跟着 `currentPage` 切 —— 内容才是真相。
 *  2. **滑块跟手**：按 `currentPage + currentPageOffsetFraction` 实时插值（见下面那段），
 *     滑块与页面同进同出。
 *  3. **禁用只能自己造**：库里 `TabRowWithContour` 没有 `enabled` 参数（`TabRowKt` 的签名里没有），
 *     于是整条套 `alpha` 变灰 + 上面压一层**透明的吃事件 Box**（`pointerInput` 里把每个 change 都
 *     consume 掉）。只把 `onTabSelected` 改成空实现是不够的 —— 那样控件看着还能点、点了却没反应，
 *     比灰着更让人困惑。
 *
 * 高度报给页面级那一份 [BandHeight]（每页一份）：内容顶上那个占位 Spacer 读它。带子与内容分处
 * 两棵树（带子挂在顶栏），只有这一条路能对齐。
 *
 * @param labels 每一档的标签 —— 条数必须等于页数
 * @param enabled 整条可不可点（与整页那个闸门同一档：单键页看那颗键的 `enabled`，命令页看排态…）
 * @param band 带子高度报到哪一份
 */
@Composable
internal fun SettingsPage.PageTabBand(
    pagerState: PagerState,
    labels: List<String>,
    enabled: Boolean,
    band: BandHeight,
) {
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    //  页码必须夹在标签表里：场景那一档的页数会随「组合键 / 单键」变（三档与两档），
    //  页数缩小时 `currentPage` 会先停在旧值上 —— 不夹就会画出轨道外的滑块，库按下标取
    //  tab 时还会越界。`BindingPagerScreen` 对同一现象已经夹过一道。
    val lastIndex = (labels.size - 1).coerceAtLeast(0)
    TabBandSurface(
        look = BandLook(labels, enabled, band),
        selectedIndex = pagerState.currentPage.coerceIn(0, lastIndex),
        //  滑块位置按**浮点页码**实时算，滑动时 1:1 跟着页面走。
        //
        //  `currentPage + currentPageOffsetFraction` 是连续量：拖到一半时前者已翻到下一页、
        //  后者恰好变成负数补回来（0.5 附近两段接得上），所以它不会跳。
        //
        //  滑块**不能**只喂 `selectedTabIndex` 让库自己弹过去 —— 库内部是阻尼比 1 的弹簧
        //  （stiffness≈439），起步慢、落点还带余量：手指已经滑过一格，滑块才走到一半，
        //  读起来就是「跟页面对不上拍」。这条路以前走过一次、当时撤掉了，原因就在这。
        //  这次给库加了浮点入参（`patches/coui/0007-fractional-tab-position.patch`）：
        //  浮点位置非空时库跳过弹簧、直接照它画滑块，才真正做到跟手。
        //  浮点位置**同样要夹**：`currentPage` 被夹住之后，它加偏移量仍可能落在
        //  `0..lastIndex` 之外（页数刚缩小、偏移量还指着旧的那一页）—— 补丁版库拿到什么就照什么画。
        selectedPosition =
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, lastIndex.toFloat()),
    ) {
        tick()
        //  点这一下就**摘焦点**：输入框失焦后不再为光标发 `bringIntoView`，
        //  否则分页器（也是滚动容器）会把刚翻过去的那一页又拽回焦点所在那一页
        //  —— 真机口径「输入框有值时切 tab 点也回弹、滑也回弹」。
        //  放在起动画之前：让键盘先收，别在动画半途改布局。
        focusManager.clearFocus()
        scope.launch { pagerState.animateScrollToPage(it) }
    }
}

/**
 * 同 [PageTabBand]，但**没有分页器**：它只换「现在看哪一档」，内容不跟着横滑。
 *
 *  「条件执行」页用它 —— 那一页一屏只有一张表，换手势是把**草稿整份换成另一档**（不是翻页，
 *  也没有可以横滑的第二屏），所以带子只需要「亮哪一档 + 点哪一档」，滑块直接照着 [selected] 画。
 *  换档的后果由调用方定（那里要拦「还没保存」那种情况）。
 */
@Composable
internal fun SettingsPage.PageTabBand(
    selected: Int,
    labels: List<String>,
    enabled: Boolean,
    band: BandHeight,
    onSelect: (Int) -> Unit,
) {
    val tick = rememberTick()
    val focusManager = LocalFocusManager.current
    TabBandSurface(
        look = BandLook(labels, enabled, band),
        selectedIndex = selected,
        //  没有可拖的中间态，浮点位置恒等于整档 —— 库走「照它画」那条，不做弹簧
        selectedPosition = selected.toFloat(),
    ) {
        tick()
        //  与上面那一份同一个理由：换档前先摘焦点，别让输入框把内容拽回去
        focusManager.clearFocus()
        onSelect(it)
    }
}

/**
 * 一条带子的「长什么样 + 高度报给谁」—— 这三样只在**建带子的那一处**定，滑动时不变。
 *
 *  与「亮哪一档 / 滑块画在哪」分开：后者每帧都变，前者整趟就一份。
 */
private class BandLook(
    val labels: List<String>,
    val enabled: Boolean,
    val band: BandHeight,
)

/**
 * 两个入口共用的那一条 —— 只有「亮哪一档 / 滑块画在哪 / 点了干什么」不同，外观与禁用态一份。
 *
 * @param selectedIndex 标签的粗细与配色跟着它
 * @param selectedPosition 滑块的浮点位置（分页器那条会落在两档之间）
 */
@Composable
private fun TabBandSurface(
    /** 「这一条带子长什么样、高度报给谁」—— 与下面两位（随手指变）分开，见 [BandLook]。 */
    look: BandLook,
    selectedIndex: Int,
    selectedPosition: Float,
    onSelect: (Int) -> Unit,
) {
    val labels = look.labels
    //  标签行自己记着滚到哪儿：那一排在**最小宽度下也放不下**时整条可横向滚
    //  （判据在库的 `computeSegmentWidths`，见 `patches/coui/0018-…`）。
    //
    //  这里**不再替库做第二轮滚动**。先收内边距（每格两侧 12dp → 下限 6dp）之后，常见那几档
    //  已经不进滚动分支了；而这层再补一次 `scrollToItem` 只会跟库自己那次滚动抢位置 ——
    //  换档时带子先被瞬移一下、再被库的动画拉回去，读起来就是「抽动」。
    val listState = rememberLazyListState()
    val disabledAlpha by animateFloatAsState(
        targetValue = if (look.enabled) 1f else DISABLED_ALPHA,
        label = "PageTabBandDisabledAlpha",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { look.band.report(it.height) },
    ) {
        //  **左右那一档属于视口，不属于滚动内容**：它垫在滚动区**外面**（`padding`），
        //  再把滚动区沿这条内缩后的边界裁一刀（`clip`）—— 不垫这一层的话，滚到中间时
        //  标签会一直画到屏幕边上，那一档留白只在最左 / 最右才看得见。
        //
        //  带子**里面**还有第二道留白（`contourPadding`，4dp）：滑块与标签都收在那一道
        //  之内，滑块因此不会画满胶囊两端的圆头（见 `patches/coui/0019-…`）。
        //
        //  左右取**卡片那一档**（[PAGE_MARGIN]）：带子里的控件、下面那些卡片、顶栏
        //  两侧的按钮与大标题现在都在同一条竖线上（顶栏那三个 padding 也是这一档，
        //  见 `PageHeader`）。
        //  纵向：**只带下边一档**（[PAGE_TOP_PADDING]）
        //  —— 上边那 8dp 去掉，带子直接贴着折叠栏下沿；
        //  下边这一档就是「带子与内容之间」的间距，与没有带子的页（首页 / 更多）
        //  由内容 Spacer 加的是**同一档**，各页一致（见 [PAGE_TOP_PADDING]）。
        Box(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = PAGE_MARGIN,
                    end = PAGE_MARGIN,
                    bottom = PAGE_TOP_PADDING,
                )
                //  裁在 `padding` **之内**：这两条链顺序不能换 —— 先垫留白再裁，边界才是
                //  内缩后的那一块（换过来就变成「先裁整屏、再挪位置」，留白照样被内容盖住）。
                //
                //  形状是**胶囊**而不是矩形：底下那条带子本身就是两头整圆的胶囊，直边裁在
                //  圆头旁边会漏出两个角（滚到边上的标签从角上透出来，看着像跑到胶囊外面）。
                .clip(RoundedCornerShape(percent = 50)),
        ) {
            TabRowWithContour(
                tabs = labels,
                selectedTabIndex = selectedIndex,
                selectedTabPosition = selectedPosition,
                onTabSelected = onSelect,
                colors = tabBandColors(),
                listState = listState,
                //  **每格的下限从 52dp 收到 40dp**：库的 52dp 是按「三档短标签」定的，六档时它把
                //  五档两字标签统统顶到 182px（比文字 + 内边距还宽），整条于是 1225px > 视口
                //  1160px，只能进滚动分支、右端标签被切。40dp 仍高于「两字 + 两侧内边距」，
                //  收下来之后整条 1155px 装得下，带子保持静止排布。
                minWidth = 40.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(disabledAlpha),
            )
        }
        if (!look.enabled) {
            // 透明吃事件层：不给任何视觉（灰是上面那层 alpha 做的），只负责拦住输入
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}

/**
 * 这条带子的配色 —— 其余主题档交给库的默认值，**Monet 那一档自己给**。
 *
 *  库的默认是两块固定灰（轨道 `0x0F000000` / 滑块纯白，深色下 `0x1FFFFFFF` / `0x1AFFFFFF`）：
 *  整页底色被壁纸染过之后，这条带子是页面上唯一一块「没跟上配色」的灰（与格子小字、浮层面板
 *  同一类问题）。
 *
 *  取法沿用 `popupSurfaceColor` 那条判据 —— **深色必须抬一档**才分得开：
 *  · 浅色：轨道 `surfaceContainerHighest`（比页面底色低一档）、滑块 `surface`（页面底色本身，
 *    读作「从轨道里抠出来的一格」）；
 *  · 深色：轨道 `surfaceContainer`、滑块 `surfaceContainerHighest` —— 深色下明暗差是唯一的分层
 *    线索，滑块必须比轨道**亮**（浅色是反过来的：越亮越靠上）。
 *
 *  文字两档都保持库默认的 `onSurface`：轨道与滑块都在 surface 那一族里，中性字在两者上都读得清，
 *  换成强调色反而会和「选中」这件事抢注意力。
 */
@Composable
private fun tabBandColors(): TabRowColors {
    val scheme = COUITheme.colorScheme
    if (!LocalMonetColors.current) return TabRowDefaults.tabRowColors()
    return if (scheme.isDarkScheme) {
        TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainer,
            selectedBackgroundColor = scheme.surfaceContainerHighest,
        )
    } else {
        TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainerHighest,
            selectedBackgroundColor = scheme.surface,
        )
    }
}

// 建页那次「读盘 + 落状态」的两半。
//
// 为什么要拆成两半：读的那一半要挪到 IO 线程上（以前跑在宿主主线程，见
// `ui/chrome/PageLoad.kt` 的类注释），而写的那一半必须回主线程（写的是 Compose 状态）。
// 中间用 [PageLoadSnapshot] 这份纯数据过河，谁也不认识谁。

/**
 * 建页期的整段初始化 —— 由 [com.lookie.opluskey.ui.screen.Page] 的 `LaunchedEffect(Unit)` 调一次。
 *
 * 三步，前两步都在 IO 上：
 *  1. 读三份文件（主配置 / 命令库 / 界面偏好）；
 *  2. 回主线程把读到的写进页面状态 —— **包括语言档**，它必须在首帧内容画出来之前就位；
 *  3. 把完整配置主动同步一次给注入侧（[publishConfig] 的注释解释了为什么建页就要发）。
 *
 * 第 3 步也放 IO：它内部要 `CommandLibrary.longIsSerial(config)`，那是一次读盘（带指纹缓存，
 * 但缓存没命中时就是真的 open），没必要占着主线程等。
 */
internal suspend fun SettingsPage.loadPageState() {
    val snapshot = withContext(Dispatchers.IO) { readPageLoadSnapshot(context) }
    applyPageLoad(snapshot)
    // 预算一次「本地配置指纹」：组合期会读它（`configSideOk` / 「设置生效」那一行），
    //  而它要读命令库。放在这趟 IO 里，页面组合时就是缓存命中，不碰磁盘。
    withContext(Dispatchers.IO) { primeLocalTag(snapshot.configRead.config, snapshot.commands) }
    // 「装好后没动过任何设置」的状态下，注入侧只能等宿主进程的解锁广播 ——
    // 打开这一页就立刻同步一次，作为兜底（判定在注入侧，见 [publishConfig]）。
    val sent = withContext(Dispatchers.IO) { publishConfig(snapshot.configRead.config) }
    if (!sent) {
        XLog.w("建页同步配置：广播没发出去（注入侧还停在上一份）")
        //  只说一句日志的话，用户看到的是**整页灰掉**：注入侧手上还是旧配置、两边指纹对不上
        //  ⇒ `configSideOk=false` ⇒ `pageWritable()` false，而页面上没有任何话解释这件事。
        //  与 `finishWrite` 那条广播失败同一个口径：给一句能自救的话。
        hint(I18n.get(Strings.TOAST_CONFIG_SYNC_FAILED))
    }
}

/**
 * 把 [PageLoadSnapshot] 落进页面状态。**必须在主线程**（写的是 Compose 状态）。
 *
 * 收尾那句 `loaded = true` 是这一整段的开关：它翻 true 的那一刻，[com.lookie.opluskey.ui.screen.Page]
 * 才会把整棵页面树组合出来 —— 所以这里必须**先**把每一项写完，最后再翻它。
 */
internal fun SettingsPage.applyPageLoad(snapshot: PageLoadSnapshot) {
    configState.config.value = snapshot.configRead.config
    configState.readOnly = snapshot.configRead.readOnly
    configState.configRecovered = snapshot.configRead.recovered
    configState.commands.value = snapshot.commands
    configState.commandsReadOnly = snapshot.commandsReadOnly
    picker.showSystemApps.value = snapshot.prefs.showSystemApps
    picker.showUnopenable.value = snapshot.prefs.showUnopenable

    // 判「某侧跑的是不是盘上这份」要的三样事实：包的更新时间、本轮开机指纹、以及「系统那侧曾经起来过」。
    //  第一项在 IO 线程上问过 PackageManager（见 `readPageLoadSnapshot`），后两项来自界面状态文件。
    val baseline = snapshot.linkBaseline
    link.installedAt = baseline.installedAt
    link.bootWall = ProcClock.bootWall()
    //  「曾经回过话」与「那次是哪一轮开机」是一笔账，一起灌（见 `HostLinkState.seedAlive`）
    link.seedAlive(ever = baseline.injectEverAlive, boot = baseline.injectAliveBoot)

    // 语言档跟着配置走。放这里而不是 `installI18nBridge()` 那一步：那一步只装订阅、不读配置，
    //  而配置现在是异步读回来的 —— 读回来之后立刻换档，首帧文案才是用户选的那个语言。
    applyLanguage(snapshot.configRead.config.global.language)
    //  深浅要在整棵页面树组合出来**之前**按新配置定一次：建页那一步只读过系统那一档，
    //  固定浅色 / 固定深色时它会停在系统那一档上 —— 首帧整页按错的深浅画出来（首页那块大卡
    //  尤其明显：先是一整块暗色，等下面这条修正跑完才变亮）。放 `loaded = true` 之前，
    //  页面组合的第一帧就已经是用户选的那一档。
    syncNight("配置读回")

    XLog.w(
        "界面状态：显示全部应用=${snapshot.prefs.showSystemApps}，显示失效=${snapshot.prefs.showUnopenable}",
    )
    XLog.w(
        "命令库：${snapshot.commands.size} 条" +
            "（单条 ${snapshot.commands.count { it.kind == CommandKind.SINGLE }}" +
            " / 多条 ${snapshot.commands.count { it.kind == CommandKind.SERIAL }}）",
    )

    configState.loaded = true
}
