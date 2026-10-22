package com.lookie.opluskey.ui.screen.binding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.PageCornerFab
import com.lookie.opluskey.ui.PageFabButton
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.DISABLED_ALPHA
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.gestureLabel
import com.lookie.opluskey.ui.rememberFabVisibleOnScroll
import com.lookie.opluskey.ui.rememberTick
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import kotlinx.coroutines.launch

/**
 * 「按键绑定」页那条**分段控件带**（三个手势 tab）—— 挂在**顶栏的 `bottomContent`** 里
 *（见 [PageHeader] 的 `bottomContent` 与 [SettingsPage] 的 `Page()` 里那处分派）。
 *
 * 从「页内一层浮层」搬过来：位置仍是**折叠栏底边**（顶栏自己排的），
 * 底色 / 模糊 / 下沿那条细线都由顶栏那一份负责（见 [PageHeader] 的 `bottomContent`）。
 *  它**不读纵向滚动量** —— 位置只跟顶栏折叠走；点它去翻页（`animateScrollToPage`），
 * 反过来亮哪一个由 pager 的 `currentPage` 决定（内容才是真相）。
 *
 *  但**滑块是跟手的**：横向滑动页面时按 `currentPage + currentPageOffsetFraction` 实时插值，
 * 滑块与页面同进同出（细节见下面 `selectedTabPosition` 那一段）。
 *
 *  分段控件**没有 `enabled` 参数**（`TabRowKt` 的 `TabRowWithContour` 签名里没有，
 * 反编译核对过）—— 主开关为关时只能自己造禁用：
 *  1. 整条控件套 `alpha` 让它看着是灰的（与右下那颗 FAB 同一套参数、同一目标值）；
 *  2. 上面压一层**透明的吃事件 Box**（`pointerInput` 里把每个 change 都 consume 掉），
 *     把点击 / 滑动全吃掉 —— 只把 `onTabSelected` 改成空实现是不够的，
 *     那样控件看着还能点、点了却没反应，比灰着更让人困惑。
 *
 *  高度报给 [SettingsPage.bindingBandHeight]：内容顶上那个占位 Spacer 读它，
 * 两者才严丝合缝。
 */
@Composable
internal fun SettingsPage.BindingTabBand(
    pagerState: androidx.compose.foundation.pager.PagerState,
    enabled: Boolean,
) {
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val disabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_ALPHA,
        label = "BindingBandDisabledAlpha",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { bindingBandHeight.report(it.height) },
    ) {
        TabRowWithContour(
            tabs = Gesture.entries.map { gestureLabel(it) },
            // 亮哪一个只看**分页器停在哪一页**（`currentPage`）—— 标签的粗细与颜色仍按整页切，
            // 只有滑块（下面那条 `selectedTabPosition`）是连续跟手的。
            selectedTabIndex = pagerState.currentPage,
            // 滑块位置按**浮点页码**实时算，滑动时 1:1 跟着页面走。
            //
            //  `currentPage + currentPageOffsetFraction` 是连续量：拖到一半时前者已翻到下一页、
            //  后者恰好变成负数补回来（0.5 附近两段接得上），所以它不会跳。
            //
            //  滑块**不能**只喂 `selectedTabIndex` 让库自己弹过去 —— 库内部是阻尼比 1 的弹簧
            //  （stiffness≈439），起步慢、落点还带余量：手指已经滑过一格，滑块才走到一半，
            //  读起来就是「跟页面对不上拍」。这条路以前走过一次、当时撤掉了，原因就在这。
            //  这次给库加了浮点入参（`patches/coui/0007-fractional-tab-position.patch`）：
            //  浮点位置非空时库跳过弹簧、直接照它画滑块，才真正做到跟手。
            selectedTabPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            onTabSelected = {
                tick()
                scope.launch { pagerState.animateScrollToPage(it) }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 左右与网格同一档 [PAGE_MARGIN]。纵向：**只带下边一档**（[PAGE_TOP_PADDING]）
                    // —— 上边那 8dp 去掉，带子直接贴着折叠栏下沿；
                    // 下边这一档就是「带子与内容之间」的间距，与没有带子的页（首页 / 更多）
                    // 由内容 Spacer 加的是**同一档**，各页一致（见 [PAGE_TOP_PADDING]）。
                    .padding(
                        start = PAGE_MARGIN,
                        end = PAGE_MARGIN,
                        bottom = PAGE_TOP_PADDING,
                    ).alpha(disabledAlpha),
        )
        if (!enabled) {
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
 * 「按键绑定」页：网格、焦点导航与绑定选择。
 *
 * 从 `SettingsPage.kt` 按职责拆出：成员改为 [SettingsPage]
 * 的扩展/同包顶层声明，调用点零改动。
 */
@Composable
internal fun SettingsPage.BindingScreen(
    pagerState: androidx.compose.foundation.pager.PagerState,
    enabled: Boolean,
    /** 点「打开应用」那一格时跳「打开应用」页（新增，见 [Screen.APP_PICKER]）；
     *  带上是**哪个手势**点的 —— 挑中写哪个手势、没挑就返回回退哪个手势都靠它。 */
    onOpenAppPicker: (Gesture) -> Unit,
    /** 点「控制中心磁贴」那一格时跳「控制中心磁贴」页。 */
    scrollBehavior: ScrollBehavior,
) {
    // tab 与页互相同步。
    // 挪进了每个手势页自己的滚动内容（见 [BindingList]）—— 它不再是固定的一层。
    // （pagerState 与图标表都是页面级的，见 [tileIcons] —— 转场首帧不再做重活）
    val gestures = Gesture.entries
    val tick = rememberTick()

    /**
     * 进「自定义命令」页（**新增**一条）。
     *
     * 「自定义命令」页的入口就只有这颗 FAB。
     * 注意：这里**不问「放弃未保存的命令」、也不清草稿**：那一问在**退出**命令页时（见 [goBack]），
     * 进页永远直接进。
     *
     *  挂在 [BindingScreen] 而不是 [BindingList]：FAB 是**三个手势页共用**的一颗
     * （它不属于某一张 tab 的内容），而且它要跟着**整页转场**一起滑 —— 挂在 pager
     * 里面的话每翻一页都会重放一次进/出场，看着像闪。
     */
    val navigateToCommands: () -> Unit = nav@{
        // 通用通道（判据只有系统状态，见 `PageHaptics.allowed`），与分段控件点击同一条规矩。
        tick()
        //  注意：这是**「新增」的唯一入口**，必须先把草稿清干净：
        //  这里必须 reset —— `goBack` 只在草稿 `dirty` 时才 reset —— 「点开一条已保存的命令
        //  看一眼、原样返回」那种不脏的退页**不会**清 `editingName`，于是下次点这颗 FAB 进来
        //  表单还挂着「修改命令」、保存按钮还是「修改」，一按就把刚看过的那条原样覆盖回去。
        //  「进编辑」那条路（长按 / 再点一次）走的是 `draft.load`，不经过这里。
        commandDraft.draft.reset()
        navTo(Screen.COMMANDS)
    }

    // tab 点击 → 翻页
    // pager 稳定落页 → 回写 tab（这里没有别的状态依赖 currentPage，单向即可）
    //
    //  外层是 `Box`：FAB 与分段控件都要**浮在 pager 之上**（只有 `Box` 才叠得起来，
    // 后声明的画在上面）。pager 自己铺满整页 —— 分段控件是叠在它上面的一层浮层，
    // 见下面那段。

    // —— 分段控件的落点：**钉在顶栏底下**，不跟内容滚 ——
    //
    //  它**整页只有一份**（叠在 pager 之上，不是每页一份跟着 pager 平移）⇒ 左右翻页时纹丝不动。
    //  位置固定在「顶栏底边再让出 [GRID_GAP]」那一档：顶栏折叠时它跟着往上走，
    // 但**不随内容滚动**，始终留在页面上。
    // （定案：先前那版让它与内容 1:1 滑走，实测滑到磨砂顶栏底下会透出一层
    // 鬼影，不如直接钉住。）
    //  它在内容里的**占位**由一个等高的 Spacer 顶着（[BindingList] 的 `tabBlock`）——
    // 那个 Spacer 属于**内容**、会随滚动走，所以滚起来之后内容会钻到它背后，
    // 靠它自己那层不透明底挡住（见下面 `background`）。
    //
    //  滚动状态**每个手势页各持一份**（递进 [BindingList] 的 `scrollState`）。
    //  注意：三页**共用一份**（图「翻页时内容停在同一个位置」）的话会**切回来就滚不动**：
    //  `ScrollState` 内部只有一个 `scrollableState`，而 pager 滑动时相邻两页同时在场 ⇒
    //  两处 `verticalScroll` 挂同一个 state、互相打架，回来那一页就再也滚不动了
    // （用户报的 bug）。改成每页一份，翻页时各自记各自的位置。
    val listScrolls = gestures.map { rememberScrollState() }
    // 分段控件那一块的高度（含自己的上下内边距）—— 内容里那个占位 Spacer 用**同一个值**，
    // 两者才严丝合缝。这条带子现在挂在**顶栏**里（见 [BindingTabBand]），量高度也在那边，
    // 值走页面级的 [SettingsPage.bindingBandHeight]（见 [BandHeight]）。
    val tabBlockDp = bindingBandHeight.dp()

    // 当前停在哪一档手势 —— 页面级记一份：分叉页要从这里知道「进来时停的是单击还是长按」，
    //  它编的是这一档的条件表，顶栏小标题也写这一档。pager 状态在这个 composable 里、
    //  跨页拿不到，所以落到 [SettingsPage.branchGesture] 上。
    LaunchedEffect(pagerState.currentPage) { branchGesture = pagerState.currentPage }

    Box(
        Modifier
            .fillMaxSize()
            // 这一层自带不透明底：push 转场交叠时不透出下层残影
            // （浮层布局下由这个 `Box` 承担）。
            .background(COUITheme.colorScheme.surface),
    ) {
        // 列表铺满整页（视口从 y=0 起）—— 内容自己能滚过分段控件那一条，见上面那段。
        //  横向也带回弹（`overScrollHorizontal`）：三个手势页来回滑到两端时弹一下，
        // 与三个页面自己的纵向回弹是同一套弹簧。
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .overScrollHorizontal(),
            // 主开关为关时整页禁用：分段控件 / FAB / 方向键都停了，pager 也得停 ——
            //  否则还能横滑到下一页，滑完灰着的指示条还会跟着走。判据与那三处同一档（主开关）。
            userScrollEnabled = enabled,
        ) { page ->
            BindingList(
                g = gestures[page],
                //  三个手势页**同时在场**（pager 会预组合相邻页）—— 只有当前那一页该抢焦点、
                // 该收浮层、该吃方向键。不给 `active` 的话进场三页各自 requestFocus，谁最后请求到
                // 谁说了算（真机上表现为「翻页后方向键在动上一页的圈」）。
                page = BindingPageState(enabled = enabled, active = pagerState.currentPage == page),
                pickers = BindingPickers(onOpenAppPicker),
                onCreateCommand = navigateToCommands,
                // 分段控件在 pager 之外、由外层驱动 —— 它要的滚动量由**当前页**那一份给
                // （见上面 `listScrolls` 的注释）。
                // 内容顶上给分段控件留出的那一块（占位高度，见 [BindingScroll] 的 `tabBlock`）。
                scroll = BindingScroll(scrollBehavior, listScrolls[page], tabBlockDp),
            )
        }

        // —— 进「自定义命令」页的唯一入口 FAB ——
        BindingFab(
            visible = rememberFabVisibleOnScroll(listScrolls[pagerState.currentPage]),
            onClick = navigateToCommands,
        )
    }
}

/** 进「自定义命令」页的浮动操作按钮。
 *
 *  **不跟整页的禁用态一起灰掉**（用户口径）：这一颗进的是「自定义命令」页 —— 那一页管的是
 *  命令库，不挂在某颗键的绑定上，主开关关着、或停在收集来的键上时它照样有意义。
 *  灰着却还留着（或灰着又拦点击）比直接放着更难解释。
 */
@Composable
private fun BoxScope.BindingFab(
    visible: Boolean,
    onClick: () -> Unit,
) {
    PageCornerFab(visible = visible) {
        PageFabButton(onClick = onClick) {
            Icon(
                PageIcons.CommandAdd,
                contentDescription = I18n.get(Strings.COMMAND_SAVED_COMMANDS, "已保存的命令"),
                tint = COUITheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * 一个**入口格**菜单的开合状态：开着的是哪一格 + 它自己那份触点锚点。
 *
 * 锚点跟着格子走（由格子自己 `remember`、点击时随菜单一起上报），而不是在页面这一层按
 * 「哪一格用哪一支」手写一张表 —— 那种表每加一个入口格就得补一行，**漏一个菜单就弹到别人身上**
 *（「前台转小窗」、「通知与控制中心」两次都是这么漏的）。
 *
 * @param spec 开着菜单的那一格（菜单内容按它的 token 从 [ActionCatalog.modeMenu] 取）
 * @param anchor 那一格记下的手指触点 —— 浮层就弹在那一点
 */
internal data class OpenTileMenu(
    val spec: ActionCatalog.Spec,
    val anchor: PreciseClickState,
)
