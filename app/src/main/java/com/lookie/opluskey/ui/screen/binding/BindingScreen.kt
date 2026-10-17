package com.lookie.opluskey.ui.screen.binding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.PageCornerFab
import com.lookie.opluskey.ui.component.PageFabButton
import com.lookie.opluskey.ui.component.rememberFabVisibleOnScroll
import com.lookie.opluskey.ui.model.DISABLED_ALPHA
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.gestureLabel
import com.lookie.opluskey.ui.state.BandHeight
import com.lookie.opluskey.ui.state.rememberTick
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import kotlinx.coroutines.launch

/**
 * 单击档「按键设置」那张卡的**行数** —— 单击延迟 + 立即触发单击。
 *
 * 两行都走 `cardListPositionOf`：首尾行各补 2dp（COUI 的 `coui_list_card_head_or_tail_padding`），
 * 与「多条命令」那张卡同一套账（见 `CommandScreen` 的 `SERIAL_CARD_ROWS`）。
 */
internal const val SINGLE_SETTING_ROWS = 2

/**
 * 「按键绑定」页那条**分段控件带**（三个手势 tab）—— 挂在**顶栏的 `bottomContent`** 里
 *（见 [PageHeader] 的 `bottomContent` 与 [SettingsPage] 的 `Page()` 里那处分派）。
 *
 * 从「页内一层浮层」搬过来：位置仍是**折叠栏底边**（顶栏自己排的），
 * 底色 / 模糊 / 下沿那条细线都由顶栏那一份负责（见 [PageHeader] 的 `bottomContent`）。
 *  它**不读滚动量** —— 位置只跟顶栏折叠走；点它去翻页（`animateScrollToPage`），
 * 反过来亮哪一个由 pager 的 `currentPage` 决定（内容才是真相）。
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
            // 亮哪一个只看**分页器停在哪一页**（`currentPage`）—— 曾按连续拖动页码做过
            // 一版「跟手」，真机手感不好（滑块跟页面对不上拍），已撤掉。
            selectedTabIndex = pagerState.currentPage,
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
        if (!enabled) return@nav
        // 通用通道（判据只有系统状态，见 [hapticsAllowed]），与分段控件点击同一条规矩。
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
    // 主开关为关时，分段控件与右下那颗 FAB 都靠**套 alpha** 造禁用（两者都没有
    // `enabled` 参数，见下面各自那两段）。分段控件带搬进顶栏之后两份 alpha 分处两棵树，
    // 但**参数与目标值完全一致**、又在同一帧被同一个开关作废 ⇒ 淡入淡出仍然同步。
    // 过渡参数与页里其他状态色（[ActionTile] 的 `contentColor` 等）同一组。
    val disabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_ALPHA,
        label = "BindingDisabledAlpha",
    )

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
                enabled = enabled,
                //  三个手势页**同时在场**（pager 会预组合相邻页）—— 只有当前那一页该抢焦点、
                // 该收浮层、该吃方向键。不传这个的话进场三页各自 requestFocus，谁最后请求到
                // 谁说了算（真机上表现为「翻页后方向键在动上一页的圈」）。
                active = pagerState.currentPage == page,
                onOpenAppPicker = onOpenAppPicker,
                onCreateCommand = navigateToCommands,
                scrollBehavior = scrollBehavior,
                // 分段控件在 pager 之外、由外层驱动 —— 它要的滚动量由**当前页**那一份给
                // （见上面 `listScrolls` 的注释）。
                scrollState = listScrolls[page],
                // 内容顶上给分段控件留出的那一块（占位高度，见 [BindingList] 的 `tabBlock`）。
                tabBlock = tabBlockDp,
            )
        }

        //  —— 手势分段控件（`TabRowWithContour`）就叠在这里 ——
        // 搬进**顶栏的 `bottomContent`**（见 [BindingTabBand]）：
        // 带子成了顶栏这个节点的一部分，顶栏自己的半透明底与模糊连着它一起盖住，
        // 于是这一整层（`clipToBounds` 的外壳 + 自己的取样层 / `frosted` /
        // 半透明底 + 自己画的那条下划线 + 内容里的等高占位）全部删掉 ——
        // 位置（折叠栏底边）、观感（底色 + 模糊）、下沿那条细线都由顶栏那份负责。

        // —— 进「自定义命令」页的**唯一入口** ——
        //
        //  放在 **pager 之外、Box 之内**：它不随某个手势页滚动 / 翻页（否则每翻一页
        // 都会重放一次进/出场，看着像闪），但仍然属于「按键绑定」这一页 ——
        // 整页 push / pop 转场时它跟着一起滑（挂到 `Page` 那一层的话它不动，
        // 会看着像「凭空出现 / 消失」，与「每级页面自带顶栏、整页转场」的规矩不一致）。
        //
        // 尺寸 / 形状 / 阴影走库的默认值（`FloatingActionButtonDefaults`：56dp 见方、`CircleShape`、
        // 12dp 阴影、按下缩到 0.92）；这三样收在 [PageFabButton] 那一份里（与「选择应用」页那颗
        // 共用）—— 色相是 `colorScheme.primary`、不自己调色（与「选中态只靠 radio + 文字变蓝、
        // 不动底色」那条纪律一致）。
        //
        //  库的 `FloatingActionButton` **没有 `enabled` 参数**（签名逐项核对过：
        // `onClick / modifier / shape / containerColor / shadowElevation / minWidth /
        // minHeight / interactionSource / content`）。主开关为关时按分段控件那条老路造禁用：
        // (1) 套 `alpha` 让它看着是灰的；(2) 上面压一层透明 Box 把输入全吃掉
        // —— 只把 `onClick` 换成空实现的话，它看着还能点、点了却没反应，比灰着更让人困惑。
        //  下边距取 [PAGE_BOTTOM_PADDING] 的**两倍**（64dp）、右边距取 [PAGE_MARGIN]
        //  显隐：**往下滚就藏、往上滚就露**（判据见 [rememberFabVisibleOnScroll]）——
        //  它压着列表最后几行，而往下滚的时候要看的是内容；往回滚（想找回上面那几格）时
        //  它才该露出来。
        //  滚动状态取**当前那一页**的那一份：三个手势页各持一份（见上面 `listScrolls`），
        //  换页时它变了，[rememberFabVisibleOnScroll] 内部的显隐跟着重置成「露」。
        //  动画与位置走 [PageCornerFab] 那一份（与「选择应用」那颗同一套滑入 / 滑出）。
        PageCornerFab(
            visible = rememberFabVisibleOnScroll(listScrolls[pagerState.currentPage]),
        ) {
            Box {
                PageFabButton(
                    onClick = navigateToCommands,
                    modifier = Modifier.alpha(disabledAlpha),
                ) {
                    // 用「加号」而不是命令提示符 `>_`：这颗按钮的动作是**新增**
                    // 一条命令（`navigateToCommands` 会先 `draft.reset()`），加号是 FAB 上
                    // 最不会误读的字形。目标页的标题仍是「自定义命令」，身份不会丢。
                    //
                    // v1.1.0 源码：`FloatingActionButton` 内部只套一层
                    // `Surface(color = containerColor)`，**没给 contentColor** ——
                    // 而 `Surface` 自己的默认是 `contentColor = onSurface`（`Surface.kt`），
                    // 于是图标继承到 `onSurface`（深色），画在 `primary`（蓝）底上就是一坨深色。
                    // `onPrimary` 才是「画在 primary 上的内容色」，深浅两套色板都取白。
                    //  不要用 `Color.Unspecified` 顶替「不给」—— 那个值在 `Icon` 里是
                    // 「**完全不上色**」（用矢量图自带的填充色），不是「用 LocalContentColor」。
                    Icon(
                        PageIcons.CommandAdd,
                        contentDescription = I18n.get(Strings.COMMAND_SAVED_COMMANDS, "自定义命令"),
                        tint = COUITheme.colorScheme.onPrimary,
                    )
                }
                if (!enabled) {
                    // 透明吃事件层（与分段控件那条同一个做法）：不给任何视觉，只负责拦住输入
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

        // 「放弃未保存的命令」那一问**已挪到「自定义命令」页退出时**：
        //  这里只管进页 —— 不再看草稿、也不再清草稿（见 `CommandScreen` 里那张同名的确认窗）。
    }
}

/**
 * 一个**入口格**菜单的开合状态：开着的是哪一格 + 它自己那份触点锚点。
 *
 * 锚点跟着格子走（由格子自己 `remember`、点击时随菜单一起上报），而不是在页面这一层按
 * 「哪一格用哪一支」手写一张表 —— 那种表每加一个入口格就得补一行，**漏一个菜单就弹到别人身上**
 *（「应用转小窗」、「通知与控制中心」两次都是这么漏的）。
 *
 * @param spec 开着菜单的那一格（菜单内容按它的 token 从 [ActionCatalog.modeMenu] 取）
 * @param anchor 那一格记下的手指触点 —— 浮层就弹在那一点
 */
internal data class OpenTileMenu(
    val spec: ActionCatalog.Spec,
    val anchor: PreciseClickState,
)
