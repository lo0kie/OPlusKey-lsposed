package com.lookie.opluskey.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import io.github.suqi8.coui.kmp.utils.overScrollHorizontal
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Action
import com.lookie.opluskey.config.CommandKind
import com.lookie.opluskey.config.CommandLibrary
import com.lookie.opluskey.config.Config
import com.lookie.opluskey.config.Gesture
import com.lookie.opluskey.config.GlobalConfig
import com.lookie.opluskey.config.KeyConfig
import com.lookie.opluskey.config.KeyId
import kotlinx.coroutines.launch

/**
 * 单击档「按键设置」那张卡的**行数** —— 单击延迟 + 立即触发单击。
 *
 * 两行都走 `cardListPositionOf`：首尾行各补 2dp（COUI 的 `coui_list_card_head_or_tail_padding`），
 * 与「多条命令」那张卡同一套账（见 `CommandScreen` 的 `SERIAL_CARD_ROWS`）。
 */
private const val SINGLE_SETTING_ROWS = 2

/**
 * 「按键绑定」页那条**分段控件带**（三个手势 tab）—— 挂在**顶栏的 `bottomContent`** 里
 *（见 [PageHeader] 的 `bottomContent` 与 [SettingsPage] 的 `Page()` 里那处分派）。
 *
 *  2026-10-13 从「页内一层浮层」搬过来：位置仍是**折叠栏底边**（顶栏自己排的），
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
            .onSizeChanged { bindingBandHeight.value = it.height },
    ) {
        TabRowWithContour(
            tabs = Gesture.entries.map { it.label },
            // 亮哪一个只看**分页器停在哪一页**（`currentPage`）—— 曾按连续拖动页码做过
            // 一版「跟手」，真机手感不好（滑块跟页面对不上拍），已撤掉。
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = {
                tick()
                scope.launch { pagerState.animateScrollToPage(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                // 左右与网格同一档 [PAGE_MARGIN]。纵向：**只带下边一档**（[PAGE_TOP_PADDING]）
                // —— 上边那 8dp 去掉，带子直接贴着折叠栏下沿（2026-10-13）；
                // 下边这一档就是「带子与内容之间」的间距，与没有带子的页（首页 / 关于模块）
                // 由内容 Spacer 加的是**同一档**，各页一致（见 [PAGE_TOP_PADDING]）。
                .padding(
                    start = PAGE_MARGIN,
                    end = PAGE_MARGIN,
                    bottom = PAGE_TOP_PADDING,
                )
                .alpha(disabledAlpha),
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
 * 从 `SettingsPage.kt` 按职责拆出（2026-10-12 重构）：成员改为 [SettingsPage]
 * 的扩展/同包顶层声明，调用点零改动。
 */
@Composable
internal fun SettingsPage.BindingScreen(
    pagerState: androidx.compose.foundation.pager.PagerState,
    iconsByGesture: Map<Gesture, Map<ActionCatalog.Spec, ImageVector>>,
    enabled: Boolean,
    /** 点「打开应用」那一格时跳「打开应用」页（2026-10-12 新增，见 [Screen.APP_PICKER]）；
     *  带上是**哪个手势**点的 —— 挑中写哪个手势、没挑就返回回退哪个手势都靠它。 */
    onOpenAppPicker: (Gesture) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    // tab 与页互相同步。
    // 挪进了每个手势页自己的滚动内容（见 [BindingList]）—— 它不再是固定的一层。
    // （pagerState / 图标表已在 Page 级预热，见 Page 里的注释 —— 转场首帧不再做重活）
    val gestures = Gesture.entries
    val tick = rememberTick()

    /**
     * 进「自定义命令」页（**新增**一条）。
     *
     * 原来那一格（`CustomCommandsTile`，插在内置命令末尾）撤掉，入口搬到这颗 FAB 上。
     * 逻辑与原 `BindingList` 里的 `navigateToCommands` 一字不差 —— 先清掉上一次没保存完
     * （或改到一半）的草稿，再切页，免得「上次输了一半的命令」出现在这一次的表单里。
     *
     *  挂在 [BindingScreen] 而不是 [BindingList]：FAB 是**三个手势页共用**的一颗
     * （它不属于某一张 tab 的内容），而且它要跟着**整页转场**一起滑 —— 挂在 pager
     * 里面的话每翻一页都会重放一次进/出场，看着像闪。
     */
    val navigateToCommands: () -> Unit = nav@{
        if (!enabled) return@nav
        // 通用通道（判据只有系统状态，见 [hapticsAllowed]），与分段控件点击同一条规矩。
        tick()
        draft.reset()
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
    //（2026-10-12 定案：先前那版让它与内容 1:1 滑走，实测滑到磨砂顶栏底下会透出一层
    // 鬼影，不如直接钉住。）
    //  它在内容里的**占位**由一个等高的 Spacer 顶着（[BindingList] 的 `tabBlock`）——
    // 那个 Spacer 属于**内容**、会随滚动走，所以滚起来之后内容会钻到它背后，
    // 靠它自己那层不透明底挡住（见下面 `background`）。
    //
    //  滚动状态**每个手势页各持一份**（递进 [BindingList] 的 `scrollState`）。
    //  ⚠️ 原来是三页**共用一份**（图的是「翻页时内容停在同一个位置」），但那会**切回来就滚不动**：
    //  `ScrollState` 内部只有一个 `scrollableState`，而 pager 滑动时相邻两页同时在场 ⇒
    //  两处 `verticalScroll` 挂同一个 state、互相打架，回来那一页就再也滚不动了
    //（2026-10-13 用户报的 bug）。改成每页一份，翻页时各自记各自的位置。
    val listScrolls = gestures.map { rememberScrollState() }
    // 分段控件那一块的高度（含自己的上下内边距）—— 内容里那个占位 Spacer 用**同一个值**，
    // 两者才严丝合缝。这条带子现在挂在**顶栏**里（见 [BindingTabBand]），量高度也在那边，
    // 值走页面级的 [SettingsPage.bindingBandHeight]；首帧还没量到时是 0（占位少一截），
    // 下一帧自纠。
    val tabBlockDp = with(LocalDensity.current) { bindingBandHeight.value.toDp() }
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
            //（原来这句挂在里面那个 `Column` 上，改成浮层布局后由这个 `Box` 承担）。
            .background(COUITheme.colorScheme.surface),
    ) {
        // 列表铺满整页（视口从 y=0 起）—— 内容自己能滚过分段控件那一条，见上面那段。
        //  横向也带回弹（`overScrollHorizontal`）：三个手势页来回滑到两端时弹一下，
        // 与三个页面自己的纵向回弹是同一套弹簧。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollHorizontal(),
        ) { page ->
            BindingList(
                g = gestures[page],
                iconsByGesture = iconsByGesture,
                enabled = enabled,
                onOpenAppPicker = onOpenAppPicker,
                scrollBehavior = scrollBehavior,
                // 分段控件在 pager 之外、由外层驱动 —— 它要的滚动量由**当前页**那一份给
                //（见上面 `listScrolls` 的注释）。
                scrollState = listScrolls[page],
                // 内容顶上给分段控件留出的那一块（占位高度，见 [BindingList] 的 `tabBlock`）。
                tabBlock = tabBlockDp,
            )
        }

        //  —— 手势分段控件（`TabRowWithContour`）原来就是叠在这里的那层浮层 ——
        //  2026-10-13 搬进**顶栏的 `bottomContent`**（见 [BindingTabBand]）：
        // 带子成了顶栏这个节点的一部分，顶栏自己的半透明底与模糊连着它一起盖住，
        // 于是原来这一整层（`clipToBounds` 的外壳 + 自己的取样层 / `frosted` /
        // 半透明底 + 自己画的那条下划线 + 内容里的等高占位）全部删掉 ——
        // 位置（折叠栏底边）、观感（底色 + 模糊）、下沿那条细线都由顶栏那份负责。

        // —— 进「自定义命令」页的**唯一入口** ——
        //
        //  放在 **pager 之外、Box 之内**：它不随某个手势页滚动 / 翻页（否则每翻一页
        // 都会重放一次进/出场，看着像闪），但仍然属于「按键绑定」这一页 ——
        // 整页 push / pop 转场时它跟着一起滑（挂到 `Page` 那一层的话它不动，
        // 会看着像「凭空出现 / 消失」，与「每级页面自带顶栏、整页转场」的规矩不一致）。
        //
        // 尺寸 / 形状 / 阴影全部走库的默认值（`FloatingActionButtonDefaults`：56dp 见方、
        // `CircleShape`、12dp 阴影、按下缩到 0.92）；容器色默认取 `colorScheme.primary`，
        // 不自己调色 —— 与「选中态只靠 radio + 文字变蓝、不动底色」那条纪律一致。
        //
        //  库的 `FloatingActionButton` **没有 `enabled` 参数**（签名逐项核对过：
        // `onClick / modifier / shape / containerColor / shadowElevation / minWidth /
        // minHeight / interactionSource / content`）。主开关为关时按分段控件那条老路造禁用：
        // ① 套 `alpha` 让它看着是灰的；② 上面压一层透明 Box 把输入全吃掉
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
                FloatingActionButton(
                    onClick = navigateToCommands,
                    modifier = Modifier.alpha(disabledAlpha),
                ) {
                    // 用「加号」而不是原来那一格上的命令提示符 `>_`：这颗按钮的动作是**新增**
                    // 一条命令（见 `navigateToCommands` 的 `draft.reset()`），加号是 FAB 上
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
                        contentDescription = "自定义命令",
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
    }
}

/**
 * 一个手势下的可选动作**网格**（pager 的一页）。
 */
@Composable
internal fun SettingsPage.BindingList(
    g: Gesture,
    iconsByGesture: Map<Gesture, Map<ActionCatalog.Spec, ImageVector>>,
    enabled: Boolean,
    /** 点「打开应用」那一格时跳「打开应用」页（它是**入口**，不是取值）；把本页的手势带上。 */
    onOpenAppPicker: (Gesture) -> Unit,
    scrollBehavior: ScrollBehavior,
    /**
     * 与本页共用的滚动状态 —— **三个手势页共用同一个实例**（由 [BindingScreen] 创建）。
     *
     * 分段控件已经**钉在顶栏底下**、不读滚动量了（见 [BindingScreen]）；共用一份是为了
     * 让左右翻页时内容停在同一个位置，这里只是把 `verticalScroll` 接到同一份上去。
     */
    scrollState: ScrollState,
    /**
     * 内容顶上要给分段控件留出的那一块 = 分段控件自己的高度（含它的上下内边距）。
     *
     *  **必须与那个浮层一样高**（同一个值）：它让首屏第一行不被钉住的分段控件压住；
     * 滚起来之后内容会钻到控件背后，由控件自己那层不透明底挡着（见 [BindingScreen]）。
     * 值由 [BindingScreen] 量出来（`onSizeChanged`）。
     */
    tabBlock: Dp,
) {
    val cfg = config.value
    //  这里每次重组都会新建一个 List，但 `remember(specs)` / `LaunchedEffect(specs, …)`
    // 比的是**结构相等**（List 的 equals），内容不变时不会重建焦点表。
    val specs = ActionCatalog.forGesture(g, commands.value)
    val current = actionOf(cfg, g)
    // 「打开应用」格选中时标题下面那行小字 = **当前绑的应用名**
    // 名字查一次就缓存（PM 查询，入参是动作所以只在换动作时重查）。
    //  这一格能写出来的是**两种**动作：`app:` 与 `window:`（小窗，[Action.AppWindow]，
    // 由菜单里那项「小窗打开」定，见 [SettingsPage.smallWindow]）—— 两种都要取名字，
    // 少认一种那一格就没有小标题（2026-10-14 实证：开着小窗挑完应用回来，标题下面是空的）。
    val boundAppLabel = remember(current) {
        val target = when (current) {
            is Action.App -> current.target
            is Action.AppWindow -> current.target
            else -> null
        }
        target?.substringBefore('/')?.let { appLabelOf(it) }
    }
    // 图标表已在 Page 级预热（转场首帧减负），这里直接取
    val icons = iconsByGesture[g].orEmpty()

    // 当前选中的那一项（配置里是清单外的写法时为 -1 ⇒ 焦点退回第一项）
    val selectedIndex = specs.indexOfFirst { ActionCatalog.isSelected(it, current) }

    // 原来插在两段之间的那一格跳转按钮（`CustomCommandsTile`）撤掉了，入口搬到
    // [BindingScreen] 右下角那颗 FAB 上。于是**焦点格索引与 [specs] 下标重新一一对应**
    // —— 早先那套「差一格」的换算（`specAtFocus` / `focusOfSpec`）随之取消，
    // 不要再把它加回来。
    val builtInCount = ActionCatalog.ALL.size
    val totalCount = specs.size

    // 每个格子一个 FocusRequester。 键是 `specs` 而不是无参 `remember`：
    // specs 是清单常量（同一个 List 实例）⇒ 重组不会重建这一串 requester，
    // 重建会把「已经请求到的焦点」连同节点一起丢掉。
    val focusers = remember(specs) { List(totalCount) { FocusRequester() } }

    /**
     * 当前高亮的格子（方向键据此算下一个）。
     *
     * `onFocusChanged` 报的是**子树里**那个焦点目标的状态，而焦点目标是 [ActionTile] 里
     * 自己加的 `focusTarget()` —— 与 `onFocusChanged` 挂在同一层，它看不见 ⇒
     * `requestFocus()` 明明返回 true，回调却永远是 `focused=false`。
     * 与其去跟修饰符顺序较劲，不如「**谁请求焦点谁记账**」：焦点只由两处移动
     * （进场重定位 / 方向键）。**点击不挪** —— 焦点圈只跟随选中态（见 [select]），
     * 否则「打开应用」这种入口格会把圈骗过去、退回来又跳回选中项。
     */
    var focusedIndex by remember(specs) {
        //  焦点格索引与 `specs` 下标**一一对应**（跳转按钮撤掉之后不再差一格，
        //    见上面「扁平焦点索引的账」）。别在这里手写 `±1`。
        mutableIntStateOf(if (selectedIndex in specs.indices) selectedIndex else 0)
    }

    val tick = rememberTick()

    /**
     * 入口格点开的那一层菜单开着哪一格（`null` = 没开）—— 响铃状态是「循环 / 响铃 / 振动 /
     * 静音」，媒体控制是「播放 / 暂停 / 上一首 / 下一首」（行本身见 `ActionCatalog.MODE_MENUS`）。
     *
     * 与「打开应用」格一样是**入口格**（点了进下一层，但格子本身不画行尾箭头），
     * 区别是它**不走页面转场**：取值就那么几个，弹一层浮层就够，加一页反而要多一次来回。
     *
     *  状态就放在本页（每个手势一份）—— 换了 tab 就是另一份，菜单跟着消失。
     */
    var tileMenu by remember { mutableStateOf<ActionCatalog.Spec?>(null) }

    /**
     * 入口格的**手指锚点**，一格格一份。
     *
     * `PreciseClickState` 里除触点坐标外还存着「锚点自己的布局位置」（用来把触点换算到窗口
     * 坐标），两格共用一份的话锚点会被算成「最后布局的那一格」—— 浮层就弹在别处了。
     */
    val ringerAnchor = rememberPreciseClickState()
    val mediaAnchor = rememberPreciseClickState()
    val navAnchor = rememberPreciseClickState()
    val keyAnchor = rememberPreciseClickState()

    /**
     * 选中某一项 —— 点击与确认键**共用这一个入口**，两条路的手感一致。
     * 先振动再写配置：`pick` 读的是 `config.value`，顺序不影响结果。
     *
     *  **这里不挪焦点圈**：圈只跟随选中态（[selectedIndex] 变了由下面那个 effect 重定位）。
     * 「打开应用」是入口格 —— 点它不改变绑定，要是这里顺手把圈挪过去，从「选择应用」
     * 退回来时圈又会跳回选中项，看着像「从打开应用回到了无操作」。
     */
    val select: (ActionCatalog.Spec) -> Unit = { s ->
        // 主开关为关时整页禁用 —— 这里再兜一道（格子的 `BasicComponent(enabled=false)`
        // 已经不挂 `clickable` 了，但确认键那条路是自己派发的，得自己拦）
        if (enabled) {
            tick()
            pick(s, g)
        }
    }

    // 进场把焦点放到「当前已选中的那一项」上（[focusedIndex] 的初值就是它）。
    //  必须在 LaunchedEffect 里请求：Compose 明确禁止在组合期间请求焦点
    // （`FocusRequester` 会直接打警告并忽略）。
    //
    //  2026-10-12 补：**选中项变了就把焦点圈收回来** —— 绑定被改（点格子 / 挑应用 /
    // 没挑回退）之后，圈跟着 `selectedIndex` 走。
    LaunchedEffect(specs, selectedIndex) {
        val seat = selectedIndex.coerceAtLeast(0)
        if (seat != focusedIndex) focusedIndex = seat
        // 先把这个页面 View 的 Android 焦点拿回来，再请求 Compose 内部的焦点：
        // 按键事件按 Android 焦点派发，只请求 Compose 焦点的话在「页面 View 从未
        // 拿过焦点」的情况下按键仍然进不来。
        val viewFocus = rootView?.requestFocus() ?: false
        val tileFocus = focusers.getOrNull(focusedIndex)?.requestFocus() ?: false
        XLog.i(
            "按键绑定：进场请求焦点 index=$focusedIndex" +
                " viewFocus=$viewFocus tileFocus=$tileFocus",
        )
    }

// 方向键 / 确认键自己接管。
val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = onKey@{ e ->
        // 主开关为关：方向键 / 确认键一概不接管（「禁用所有控件」）
        if (!enabled) return@onKey false
        val key = e.key
        val up = key == Key.DirectionUp
        val down = key == Key.DirectionDown
        val left = key == Key.DirectionLeft
        val right = key == Key.DirectionRight
        val confirm = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter
        if (!up && !down && !left && !right && !confirm) return@onKey false

        if (e.type == KeyEventType.KeyUp) {
            // 确认键在抬起时派发（与平台「按下不算、抬起才算点」一致）
            if (confirm) {
                // 焦点格索引 == `specs` 下标（跳转按钮撤掉之后一一对应，见上面的账）
                specs.getOrNull(focusedIndex)?.let(select)
            }
            return@onKey true
        }

        val step = when {
            up -> -COLUMNS
            down -> COLUMNS
            left -> -1
            right -> 1
            else -> 0
        }
        if (step != 0) {
            // 焦点范围是 [0, totalCount)；从最后一格继续按「下 / 右」就停在它自己身上。
            val target = (focusedIndex + step).coerceIn(0, totalCount - 1)
            focusedIndex = target
            focusers.getOrNull(target)?.requestFocus()
        }
        true
    }

    Column(
        Modifier
            .fillMaxSize()
            //  **横向 padding 故意不加在外层**：分组标题 [SmallTitle] 也在这里，
            // 加了就让标题的左边距比首页 / 命令页的小标题多 [PAGE_MARGIN]，
            // 各段自己带横向 padding（见下），LongRepeatCard 也单独带
            //
            //  **纵向 padding 一档都不留**（2026-10-12 顶栏改成叠在内容上之后，2026-10-13 又去掉底部那一档）：
            //  `padding` 落在 `verticalScroll` **之前**就等于是**滚动视口之外**的内缩 ——
            //  顶部那 12dp 会让内容永远到不了顶栏底下、模糊没东西可糊（滚起来时屏幕最上面
            //  留一条不带内容的空带）；底部那 12dp 同理：**视口比整页矮 12dp**，
            //  屏幕最底下那一条里没有内容，而手势导航栏是**透明**的 ——
            //  那一条就被露成「一块平色」，看着像导航栏不透明、把控件挡住了
            //（2026-10-13 真机反馈「只有按键绑定页这样」，其余页的 padding 都在视口**内侧**）。
            //  两眼下的让位量改成内容里的 Spacer（顶 [tabBlock] 那一档、底见本函数末尾），
            //  能跟着滚动一起走。
            // 大标题随滚动折叠（见 [PageHeader]；`nestedScroll` 要在 `verticalScroll` 之前）
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            //  用**外面递进来的**那个状态（三个手势页共用一份）—— 分段控件在 pager
            // 之外，要靠它算自己该滑到哪儿，见 [BindingScreen] 里 `listScroll` 的注释。
            //  越界回弹：COUI 自己的弹簧实现（`overScrollVertical`）。它挂在滚动容器**外侧**，
            // 吃的是滚动容器**没吃掉**的那部分位移 —— 所以内容不满一屏时照样弹。
            .overScrollVertical()
            .verticalScroll(scrollState)
            // 预览阶段接管方向键（见 onKey 的注释）
            .onPreviewKeyEvent(onKey),
        //  **不用 `Arrangement.spacedBy`**：它会在**每一对相邻子项**之间插间距，
        // 于是 [SmallTitle] 与它下面那一段之间也被插了 [GRID_ROW_GAP]
        // 面板里（首页 / 命令页）的规矩是：**标题紧贴它下面的内容**，
        // 需要分隔的地方（内容 → 下一个标题）自己给一个显式 Spacer。
    ) {
        //  顶栏叠在内容上，顶部这一块要自己让出来（动态值，见 [currentHeaderHeight]）；
        // 后面再加 [tabBlock] —— 那是**分段控件那条固定带**的高度，它**含自己那 8dp
        // 下内边距**（带子不带**上**内边距，见 [BindingTabBand]），所以「带子与内容之间」
        // 那一档已经在里面了，这里**不再另加** [PAGE_TOP_PADDING]。
        Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + tabBlock))

        // —— 按键设置：**在网格上面**（2026-10-13 从网格下面挪上来）——
        //
        //  它是一张「设置项列表」（滑杆 / 开关那些行），属于**这一档手势本身**的行为，
        //  排在动作网格之前：先看到「这一档怎么判、有什么可调」，再挑「绑什么」。
        //  只有单击 / 长按两档有（双击那档的两个可调项都在单击档里，见下）。
        //
        //  它下面那一档间距只在**真有这张卡**时给 —— 否则双击档的「内置命令」标题
        //  会白多一截空档（标题紧贴它下面的内容，这是全页的规矩）。
        if (g == Gesture.SINGLE) {
            SmallTitle("按键设置")
            // 「单击延迟」不跟着「立即触发单击」置灰：两个状态独立 ——
            // 前者管「等多久才判双击」，后者管「双击为无操作时还要不要等」。
            // （开着后者且双击为无操作时前者确实用不上，但那是运行期的组合，
            // 由判定侧按配置自己走，界面不替用户把这一行锁死。）
            Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
                SliderRow(
                    title = "单击延迟",
                    icon = PageIcons.DelayTap,
                    summary = "单击后等待双击的时长",
                    position = cardListPositionOf(0, SINGLE_SETTING_ROWS),
                    min = GlobalConfig.MIN_DOUBLE_CLICK_MS,
                    max = GlobalConfig.MAX_DOUBLE_CLICK_MS,
                    value = cfg.global.doubleClickMs,
                    enabled = enabled,
                ) { v ->
                    write(
                        config.value.copy(
                            global = config.value.global.copy(doubleClickMs = v),
                        ),
                        "double_click_ms=$v",
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
                // 「双击为无操作时不用等那一截」—— 开关的语义与判定侧见
                // `GlobalConfig.instantSingle`（判定在 `gesture/GestureDetector.onKeyUp`）。
                // 振动见 [NoSwitchHaptic]（库 `Switch` 自己会无条件振一次）。
                NoSwitchHaptic {
                    SwitchPreference(
                        title = "立即触发单击",
                        summary = "双击为无操作时不等双击窗口",
                        checked = cfg.global.instantSingle,
                        onCheckedChange = {
                            tick()
                            setInstantSingle(it)
                        },
                        cardListPosition = cardListPositionOf(1, SINGLE_SETTING_ROWS),
                        // 与上面那行同一个图标位 —— 同一张卡里两行都有 `startAction` 时标题才对齐
                        startAction = { Icon(PageIcons.InstantTrigger, null) },
                        enabled = enabled,
                    )
                }
            }
            Spacer(Modifier.height(GRID_ROW_GAP))
        }

        //
        // （长按多久算长按、单击抬手后等多久看有没有第二下），原来摆在首页「按键行为」卡里、
        // 与「振动反馈」并列，会被当成对三个手势都生效的全局项。
        //
        // 它现在是 [LongRepeatCard] 的首行（与「长按连续触发 / 触发间隔」同一张卡）。
        // 双击档下**没有**这张卡：双击窗口就是「单击延迟」那一个值，两边都放会是同一个
        // 配置项的第二个入口（改哪边都一样），所以只留在单击档里（见上）。
        if (g == Gesture.LONG) {
            SmallTitle("按键设置")
            LongRepeatCard(
                blockedReason = when {
                    // 主开关为关 ⇒ 整页禁用，这张卡也跟着置灰
                    !enabled -> "按键绑定已关闭"
                    CommandLibrary.isSerial(commands.value, current.token) ->
                        "多条命令不支持连续触发"
                    else -> null
                },
                // 「长按延迟」那行只看主开关（与「是不是多条命令」无关）
                masterEnabled = enabled,
            )
            Spacer(Modifier.height(GRID_ROW_GAP))
        }

        // 内置命令按 [ActionCatalog.GROUPS] 分组，每组一个小标题。
        //
        // 焦点格索引仍与 `specs` 下标一一对应（见上面「扁平焦点索引的账」）—— 分组只是
        // 展示层切分，组里的格子用的还是**全局**下标（`builtInBase` 跨组连续累计），
        // 不能在组内从 0 数。
        var builtInBase = 0
        ActionCatalog.GROUPS.forEachIndexed { groupIndex, (groupTitle, group) ->
            // 「上一段 → 这个标题」之间给一档（与网格行距同值），与下面「自定义命令」那一段同一条规矩。
            // 第一组不加 —— 它前面那一段（按键设置卡）已经自带收尾间距，双击档则是页首、不需要。
            if (groupIndex > 0) Spacer(Modifier.height(GRID_ROW_GAP))
            SmallTitle(groupTitle)

            // 这一段用一个**内层 Column** 单独带 [PAGE_MARGIN] 横向 padding，
            // 标题在外层不受影响（见 Column 的 modifier 注释）。
            Column(
                Modifier.padding(horizontal = PAGE_MARGIN),
                verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
            ) {
                (builtInBase until builtInBase + group.size).chunked(COLUMNS).forEach { rowIndices ->
                    Row(
                        Modifier.fillMaxWidth(),
                        // 「上下左右间距保持一致」）；不再给焦点圈留补偿（见 [GRID_GAP] 的注释）
                        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                    ) {
                        rowIndices.forEach { index ->
                            val spec = specs.getOrNull(index) ?: return@forEach
                            ActionTile(
                                spec = spec,
                                icon = icons[spec],
                                selected = ActionCatalog.isSelected(spec, current),
                                focused = index == focusedIndex,
                                focusRequester = focusers[index],
                                //  触点锚点**每格一份**（见上面 `mediaAnchor` 那段）：`PreciseClickState`
                                // 里存坐标的只有一格，每个挂它的节点都会往里写自己那点 ⇒
                                // 一整片格子共用一份的话，锚点会被算成「最后布局的那一格」。
                                // 其余格子是取值，点了不弹浮层，挂上去只是白记一个坐标。
                                modifier = Modifier.weight(1f).let { m ->
                                    when (spec.token) {
                                        ActionCatalog.RINGER_TILE -> m.preciseClickAnchor(ringerAnchor)
                                        ActionCatalog.MEDIA_TILE -> m.preciseClickAnchor(mediaAnchor)
                                        ActionCatalog.NAV_TILE -> m.preciseClickAnchor(navAnchor)
                                        ActionCatalog.KEY_TILE -> m.preciseClickAnchor(keyAnchor)
                                        else -> m
                                    }
                                },
                                enabled = enabled,
                                // 「打开应用」格与入口格都要补一行小字：前者是绑的应用名，
                                // 入口格那格只写组名，不点开菜单根本看不出绑的是哪一档
                                subtitle = when (spec.token) {
                                    ActionCatalog.APP_TILE -> boundAppLabel
                                    else -> ActionCatalog.modeMenu(spec.token)
                                        ?.let { ActionCatalog.modeTitle(it, current) }
                                },
                                // 入口格虽然是**入口**（点了进下一层），但**不画行尾箭头** ——
                                // 走 [ActionTile] 的默认值 false，别顺手补回来。
                                onClick = {
                                    //  「打开应用」是入口、不是取值 —— 靠比常量认领，
                                    // 不能交给 `select`：`app:` 本身就是一个合法的（空）App 动作，
                                    // 走 select 会把配置写成 `app:`（拉不起任何东西，2026-10-12）。
                                    if (spec.token == ActionCatalog.APP_TILE) {
                                        if (enabled) {
                                            tick()
                                            //  不挪焦点圈 —— 与 `select` 同一条规矩：圈只跟随选中态。
                                            // 挑中应用时绑定变了，圈由重定位 effect 挪过来。
                                            onOpenAppPicker(g)
                                        }
                                    } else if (ActionCatalog.isEntryTile(spec)) {
                                        // 再弹菜单让你换档 ——
                                        // 这样「点开看一眼又关掉」也留下了一个能用的取值，
                                        // 而不是什么都不写（菜单第一项写的就是这个值）。
                                        //
                                        // **这个手势已经绑着这一组里某一档时不再改写** —— 否则你刚选完
                                        // 「下一首」，再点一次格子想看菜单，那一下先把配置冲回首档
                                        // （「播放 / 暂停」/「循环」），菜单里的对勾于是指到了错行。
                                        // 判据比**整组**（[ActionCatalog.isSelected]），与格子自己的选中态同一口径。
                                        if (enabled) {
                                            tick()
                                            //  不挪焦点圈：若这次点了会改绑（下面那行 pick），圈由
                                            // 重定位 effect 跟着 `selectedIndex` 走；没改绑圈就不动。
                                            if (!ActionCatalog.isSelected(spec, current)) {
                                                ActionCatalog.modeMenu(spec.token)
                                                    ?.firstOrNull()
                                                    ?.let { pick(it, g) }
                                            }
                                            tileMenu = spec
                                        }
                                    } else {
                                        select(spec)
                                    }
                                },
                            )
                        }
                        // 最后一行不满时补空位：不补的话剩下那格会被 weight 撑到整行宽
                        repeat(COLUMNS - rowIndices.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            builtInBase += group.size
        }
        // 标题空挂在那儿。进「自定义命令」页的入口**不在网格里**（是右下角那颗 FAB，
        // 见 [BindingScreen]），所以「没有自定义命令时进不去命令页」这个问题不存在。
        //  显隐走 [AnimatedColumn]：存下第一条自定义命令时这一段是**长出来**的。
        AnimatedColumn(visible = specs.size > builtInCount) {
            // 上一段 → 这个标题之间给一档（与网格自己的行距同值）；
            // 标题 → 它下面那一段是**紧贴**的（见外层 Column 的注释）
            Spacer(Modifier.height(GRID_ROW_GAP))
            SmallTitle("自定义命令")
            Column(
                Modifier.padding(horizontal = PAGE_MARGIN),
                verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
            ) {
                // 焦点索引 `[builtInCount, totalCount)` —— 与 `specs` 下标一一对应
                (builtInCount until totalCount).chunked(COLUMNS).forEach { rowIndices ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                    ) {
                        rowIndices.forEach { index ->
                            val spec = specs.getOrNull(index) ?: return@forEach
                            // 这条命令本身（`cmd:<名字>` 反查）—— 小字与「进编辑」两处都要用
                            val cmd = commands.value.firstOrNull { it.token == spec.token }
                            val selected = ActionCatalog.isSelected(spec, current)
                            // 「进这条命令的编辑页」—— 长按、以及**已选中时再点一次**，共用这一个入口。
                            //
                            //  走 `draft.load` 而**不是** FAB 那条 `navigateToCommands` ——
                            // 后者会先 `draft.reset()`（那是「新增」的入口），
                            // 这里要的正是「把这条命令灌进表单」，
                            // 与「自定义命令」页里轻点已保存命令同一个入口。
                            val openEditor = {
                                if (cmd != null) {
                                    tick()
                                    XLog.i("手势页打开自定义命令编辑：${cmd.name}")
                                    draft.load(cmd)
                                    navTo(Screen.COMMANDS)
                                }
                            }
                            ActionTile(
                                spec = spec,
                                icon = icons[spec],
                                selected = selected,
                                focused = index == focusedIndex,
                                focusRequester = focusers[index],
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                // 标题下面那行小字 = **命令间隔时长**（`stepDelayMs`）。
                                // 只有多条命令有这个值 —— 单条只有一步、用不到它
                                //（见 `config/Command.stepDelayMs`），所以那种格子不画。
                                // 文案走 [durationText]：1000ms 以上写成秒。
                                subtitle = cmd
                                    ?.takeIf { it.kind == CommandKind.SERIAL }
                                    ?.let { durationText(it.stepDelayMs) },
                                //  行尾那支箭头现在只给这一组格子 —— 内置那几个入口格
                                // （打开应用 / 响铃状态 / 媒体控制）不画箭头（见上面内置网格那段）。
                                enterArrow = true,
                                // 长按与「已选中时再点一次」同一个去处（上面那个 `openEditor`）。
                                // 长按之后那一下抬手由库的 `combinedClickable` 吞掉（不会再触发点击）。
                                onLongClick = openEditor,
                                // **已经选中的再点一次 = 「我要改它」**，不是「重新绑一遍」，
                                // 所以走编辑；没选中时仍是原来的「选中绑定」。
                                onClick = { if (selected) openEditor() else select(spec) },
                            )
                        }
                        repeat(COLUMNS - rowIndices.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 按键设置那两张卡（单击档 / 长按档）已挪到**网格上面**，见本函数开头那段。
        // 页面底部留白 —— 三个页面统一的那一档是 [PAGE_BOTTOM_PADDING]
        // 原来这一页滚到底时最后一张卡直接贴着屏幕边缘，而「自定义命令」页底下有 32dp）。
        //
        // 内容的底部 padding**：原来这里取 `max(PAGE_BOTTOM_PADDING, FAB_SIZE + …)`
        // （88dp，为了让滚到底时最后一行不被 FAB 压住），现在与「自定义命令」页一样
        // 只留 [PAGE_BOTTOM_PADDING]（32dp）。
        //  代价：FAB（下边距 64dp、高 56dp，占 64~120dp 那一带）滚到底时会压住
        Spacer(Modifier.height(PAGE_BOTTOM_PADDING))
        //  再补 [GRID_GAP]：这 12dp 原来是挂在**滚动视口之外**的
        // `.padding(bottom = GRID_GAP)`（见本函数 `Column` 那段的注释）——
        // 它让视口比整页矮 12dp，屏幕最底下那一条里没内容可画，透明的手势导航栏
        // 就把那一条露成一块平色（真机反馈「导航栏不透明、控件被颜色挡住」）。
        //  挪进内容里：让位量一分不变（滚到底仍是 32+12dp），但视口铺到屏幕底 ——
        // 滚起来时内容能钻进导航栏底下（导航栏本来就是透明的）。
        Spacer(Modifier.height(GRID_GAP))

        // 入口格那一层的菜单（见上面 [tileMenu]）。它**不占布局位置** ——
        // 真正的落点由那一格记下的触点决定（`DropdownPositionProvider`），
        // 挂在这里只是让它跟着这一页一起进 / 出组合。
        TileMenuPopup(
            show = tileMenu != null,
            // 锚点按**开着的是哪一格**取（每格一份，见上面 [ringerAnchor] 那段）
            preciseClick = when (tileMenu?.token) {
                ActionCatalog.MEDIA_TILE -> mediaAnchor
                ActionCatalog.NAV_TILE -> navAnchor
                ActionCatalog.KEY_TILE -> keyAnchor
                else -> ringerAnchor
            },
            modes = tileMenu?.let { ActionCatalog.modeMenu(it.token) }.orEmpty(),
            current = current,
            //  走 [pick] 而**不是** [select]：菜单里的取值不在 `specs` 里，
            // `select` 那一步的 `specs.indexOf(spec)` 会取到 −1、把焦点圈甩到第一格去。
            // 写完之后 `selectedIndex` 变了，那个「把焦点收回选中项」的 effect
            // 自然会把圈落回入口格（本来就是点它的那一格）。
            onPick = { spec -> pick(spec, g) },
            onDismiss = { tileMenu = null },
        )
    }
}

/**
 * 「长按」tab 下**唯一**的那张卡：长按连续触发（+ 触发间隔）+ 长按延迟。
 *
 *  行序（2026-10-13 调整）：**连续触发在最上面**、触发间隔紧跟着它（只在开关打开时出现），
 * 「长按延迟」落到最后一行 —— 前两行是一件事（按住不放要不要一直重来、隔多久来一次），
 * 而「多久算长按」是另一件事。
 *
 * @param blockedReason 非 null ⇒ **「长按连续触发」那行**置灰、不吃点击，
 * @param masterEnabled 首页那个「按键绑定」主开关 —— 只作用于**「长按延迟」那行**
 */
@Composable
internal fun SettingsPage.LongRepeatCard(blockedReason: String?, masterEnabled: Boolean) {
    val blocked = blockedReason != null
    val cfg = config.value
    val key = cfg.key(KeyId.PLUS)
    // 三行：长按连续触发 +（开关打开时的）触发间隔 + 长按延迟
    val rows = if (key.longRepeat) 3 else 2
    val tick = rememberTick()

    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        // 手拼的 `BasicComponent` + 行尾 `Switch` 换成库的 `SwitchPreference`。
        //  置灰的原因写在副标题里（两种原因：主开关关了 / 选中的是多条命令）。
        // 振动见 [NoSwitchHaptic]（库 `Switch` 自己会无条件振一次）。
        NoSwitchHaptic {
            SwitchPreference(
                title = "长按连续触发",
                summary = blockedReason ?: "按住不放时按固定间隔重复执行",
                checked = key.longRepeat,
                onCheckedChange = {
                    tick()
                    setLongRepeat(it)
                },
                cardListPosition = cardListPositionOf(0, rows),
                // 行首图标（与下面两行同一个图标位 —— 同一张卡里三行都有 startAction 时标题才对齐）
                startAction = { Icon(PageIcons.LongRepeat, null) },
                // `enabled = false` 由 COUI 负责：标题与副标题走 `disabledColor`、
                // 点击不吃（`Component.kt` 的 `clickableModifier` 只在 enabled 时才挂），
                // 行尾开关也跟着灰。
                enabled = !blocked,
            )
        }
        // 「触发间隔」只在**开关打开时**才存在（关着时没有可调的间隔）。
        //  它是上面那一行的参数，所以紧跟着上面那一行，而不是掉到「长按延迟」后面去；
        // 连上面那条分隔线一起显隐（[AnimatedColumn]）—— 只藏行的话会留一条孤零零的线。
        AnimatedColumn(visible = key.longRepeat) {
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            SliderRow(
                title = "触发间隔",
                icon = PageIcons.DelayRepeat,
                summary = "长按连续触发的重复间隔",
                position = cardListPositionOf(1, rows),
                min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                value = key.longRepeatIntervalMs,
                enabled = !blocked,
            ) { v ->
                write(
                    config.value.copy(
                        keys = config.value.keys +
                            (KeyId.PLUS to
                                config.value.key(KeyId.PLUS).copy(longRepeatIntervalMs = v)),
                    ),
                    "long_repeat_interval_ms=$v",
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        // 「长按延迟」—— 多久算长按。
        //  它的 enabled 只看**主开关**，不看 [blockedReason]：长按延迟与「长按绑的是不是
        // 多条命令」无关（多条命令下只是不能连续触发）。
        SliderRow(
            title = "长按延迟",
            icon = PageIcons.DelayHold,
            summary = "按住多久判定为长按",
            position = cardListPositionOf(if (key.longRepeat) 2 else 1, rows),
            min = KeyConfig.MIN_LONG_PRESS_MS,
            max = KeyConfig.MAX_LONG_PRESS_MS,
            value = key.longPressMs,
            enabled = masterEnabled,
        ) { v ->
            write(
                config.value.copy(
                    keys = config.value.keys +
                        (KeyId.PLUS to config.value.key(KeyId.PLUS).copy(longPressMs = v)),
                ),
                "long_press_ms=$v",
            )
        }
    }
}

/**
 * 入口格行尾那颗指示器的边长 —— 与 COUI `RadioButton` 的 `requiredSize(24.dp)` 同尺寸。
 *
 * 三种指示器在同一位置三选一（见 [ActionTile] 的 `endActions`：两种入口字形与单选圈），
 * 尺寸不一致会让格子里的内容跟着挪。
 */
private val ENTRY_INDICATOR_SIZE = 24.dp

/**
 * 动作网格里的一格。
 *
 * @param enabled 首页那个「按键绑定」主开关为关时传 false —— 整格置灰、不吃点击
 */
@Composable
internal fun SettingsPage.ActionTile(
    spec: ActionCatalog.Spec,
    icon: ImageVector?,
    selected: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 标题下面那行**小字**（空则不画）：内置的「打开应用」格放绑定的应用名、
     * 入口格（系统导航 / 系统按键 / 响铃状态 / 媒体控制）放当前档位，自定义命令那些格子放**命令间隔时长**。
     */
    subtitle: String? = null,
    /**
     * 是不是**带下一层**的格子 —— 是的话在行尾那枚指示器右边再画一支箭头。
     *
     * 现在只有「自定义命令」那一组格子传 true（长按进编辑）。内置那几个入口格
     * （打开应用 / 系统导航 / 系统按键 / 响铃状态 / 媒体控制）同样是入口，但**不画箭头** ——
     * 它们的行尾已经是「圈里三点」，见内置网格那段注释。
     */
    enterArrow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    // 这一档是三态（禁用 / 选中 / 常规）切换，**带过渡** —— 与下面 [tileMutedColor]、
    // 外面那圈 [TileFocusRing]、以及页里其他状态色同一组参数。它同时是 `Card` 的
    // `contentColor`（标题 / 图标那一整片都吃它）⇒ 一次过渡整格同步。
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.disabledOnSecondaryVariant
            selected -> scheme.onTertiaryContainer
            else -> scheme.onSurfaceContainer
        },
        label = "TileContent",
    )
    // 行尾箭头与选中小字共用的那档「弱化色」，可用⇄禁用切换带过渡
    val tileMutedColor by animateColorAsState(
        targetValue = if (enabled) scheme.onSurfaceVariantSummary else contentColor,
        label = "TileMuted",
    )
    // 入口格行尾那颗「圈里三点」的配色 —— 逐档照 COUI 的 `RadioButtonDefaults.radioButtonColors()`
    // 抄（选中 `primary`、未选中 `onSurfaceVariantActions`、禁用 `disabledPrimary` /
    // `disabledSecondaryVariant`）：换掉单选圈之后，颜色语义与占位逐档不变。
    val entryIndicatorColor by animateColorAsState(
        targetValue = when {
            !enabled -> if (selected) scheme.disabledPrimary else scheme.disabledSecondaryVariant
            selected -> scheme.primary
            else -> scheme.onSurfaceVariantActions
        },
        label = "TileEntryIndicator",
    )

    // 外层 `Box` 现在**只为焦点圈**留一个浮层位置（见 [TileFocusRing]）——
    // 长按不再自己写了：库的**交互式 `Card`** 自带 `onLongPress`
    // （v1.1.0 `Card.kt` 第二个重载，内部就是 `Modifier.combinedClickable`，
    // 长按之后那一下抬手不会再触发点击，两个手势互不抢）。
    //
    //  主开关为关时**两个手势一起停**（下面把两个回调都置 null ⇒
    // 库的 `isClickable` 为 false，整个 `Card` 不可点）。
    Box(modifier.height(TILE_HEIGHT)) {
        // 长按的振动**只走通用通道**（[rememberTick] / [tickNow]）：库的 `combinedClickable`
        // 默认还会自己振一次 `HapticFeedbackType.LongPress` —— 那条路不看系统静音。
        // 把这一格的局部触觉反馈换成空实现（见 [NoHapticFeedback]）。
        CompositionLocalProvider(LocalHapticFeedback provides NoHapticFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    //  **必须自己补一个焦点目标**（2026-10-11 真机定位）：
                    // 这个版本的 `Modifier.combinedClickable` / `clickable` **不带 focusable** ——
                    // `androidx.compose.foundation.ClickableKt` 里对 `FocusableKt`
                    // 的引用数为 **0**，`AbstractClickableNode` 也只实现
                    // `KeyInputModifierNode`、不是 `FocusTargetModifierNode`。
                    // 后果：格子里没有 FocusTarget ⇒ `FocusRequester.requestFocus()` 找不到目标、
                    // ⇒ 按键事件只发给根节点（`FocusOwnerImpl.dispatchKeyEvent` 在
                    // `findFocusTargetNode()` 为 null 时只走根节点的 key 节点）
                    // ⇒ [BindingList] 那个 `onPreviewKeyEvent` 一次都不会被调用。
                    // 顺序不能反：`focusRequester` 必须在这个焦点目标**之前**（链上更靠外）。
                    .focusTarget(),
                colors = CardDefaults.defaultColors(
                    color = scheme.surfaceContainer,
                    contentColor = contentColor,
                    pressedColor = scheme.surfaceContainerHigh,
                ),
                // 按压反馈**只要涟漪**：`showIndication` 让 `combinedClickable` 用
                // `LocalIndication`，与原来 `BasicComponent` 那个 `clickable` 同一份，
                // 视觉与换之前逐像素一致。`Tint`（底色渐变到 `pressedColor`）显式关掉 ——
                pressFeedbackType = PressFeedbackType.None,
                showIndication = true,
                onClick = if (enabled) onClick else null,
                onLongPress = if (enabled) onLongClick else null,
            ) {
                BasicComponent(
                    //  **不给 `onClick`**：行内容自己那个 `clickable` 会在 Main 趟
                    // 把事件消费掉，外层 `Card` 的 `combinedClickable` 就永远等不到
                    // 轻点 / 长按 / 涟漪**全部**由外层 `Card` 负责。
                    startAction = {
                        // 图标尺寸沿用全页的 22dp 纪律（见 ActionIcons 的注释）
                        icon?.let { Icon(it, null, Modifier.size(22.dp)) }
                    },
                    endActions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            //  入口格不再画单选圈（那读作「这一格是一个可选项」），按**点开去哪**分两种字形：
                            //  · 弹一层菜单（系统导航 / 系统按键 / 媒体控制 / 响铃状态）→「圈里三点」；
                            //  · 进另一个页面（「打开应用」→ 挑应用）→「窗口 + 加号」。
                            //  尺寸与配色逐档对齐（[ENTRY_INDICATOR_SIZE] / [entryIndicatorColor]），
                            // 换掉之后占位与观感不变。
                            when {
                                spec.token == ActionCatalog.APP_TILE -> Icon(
                                    PageIcons.BrowserPlus,
                                    contentDescription = null,
                                    modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                    tint = entryIndicatorColor,
                                )

                                ActionCatalog.modeMenu(spec.token) != null -> Icon(
                                    PageIcons.DotsCircleHorizontal,
                                    contentDescription = null,
                                    modifier = Modifier.size(ENTRY_INDICATOR_SIZE),
                                    tint = entryIndicatorColor,
                                )

                                // `onClick = null` ⇒ 只有 24dp 的图形、不加触达区，点击交给整行
                                else -> RadioButton(selected = selected, onClick = null, enabled = enabled)
                            }
                            // 「自定义命令」那些格子长按进编辑，箭头表示「这一格后面还有东西」。
                            // 见 [enterArrow] 的说明。
                            if (enterArrow) {
                                Icon(
                                    PageIcons.EnterArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = tileMutedColor,
                                )
                            }
                        }
                    },
                    // 默认 16dp 的内边距在两列的格子里会把标题挤没（见类注释的宽度账）
                    insideMargin = PaddingValues(horizontal = TILE_SIDE_PADDING),
                    // 格子是独立的卡片，按 COUI 的「卡片组里唯一一行」处理（上下各补 2dp）
                    cardListPosition = CardListPosition.Full,
                    // `enabled` 只管这一行**内容**的置灰（标题 / 图标 / radio）；
                    // 能不能点由外层 `Card` 的两个回调（上面按 `enabled` 置 null）决定
                    enabled = enabled,
                ) {
                    Text(
                        text = spec.title,
                        style = COUITheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 账见 [TILE_TEXT_OPTICAL_SHIFT]：`offset` 只挪绘制、不改测量。
                        modifier = Modifier.offset(y = TILE_TEXT_OPTICAL_SHIFT),
                    )
                    // 只写一行标题时看不出这一格绑的是什么（应用名 / 入口格的档位 / 命令间隔时长），
                    // 这行就是那个值。格子高 52dp、BasicComponent 最小高 48dp：两行（14sp + 12sp）装得下。
                    //  ⚠️ 这里**不能**用 [AnimatedFade]（只淡、不动高度）：那样这行字的
                    //  **布局高度是立刻占上的**，上面那行标题会**瞬间**被顶上去 ——
                    //  而要的正是「标题给这行字让位 / 复位」这个**位移本身滑出来**。
                    //  所以走 [VISIBILITY_ENTER] / [VISIBILITY_EXIT]（淡入 + 竖向展开，
                    //  与页里其他显隐同一套规格）：高度从 0 长到满，标题随之平滑上移；
                    //  收起时反向。两行（14sp + 12sp）在 52dp 的格子里装得下，不会溢出。
                    //  `MutableTransitionState(false)` 起手：首次组合也走一遍展开 ——
                    //  `AnimatedVisibility` 直接吃 `visible` 时，初始为 true 不播入场，
                    //  进页面第一眼就会是「啪」地占好位。
                    val subtitleShown = remember { MutableTransitionState(false) }
                    subtitleShown.targetState = !subtitle.isNullOrBlank()
                    AnimatedVisibility(
                        visibleState = subtitleShown,
                        enter = VISIBILITY_ENTER,
                        exit = VISIBILITY_EXIT,
                    ) {
                        Text(
                            text = subtitle.orEmpty(),
                            style = COUITheme.textStyles.footnote1,
                            color = tileMutedColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 与标题同一档视觉居中（两行一起挪，行距不变）
                            modifier = Modifier.offset(y = TILE_TEXT_OPTICAL_SHIFT),
                        )
                    }
                }
            }
        }
        // 禁用时不画焦点圈（`focused` 可能还是旧值，但整格已经不吃输入了）
        TileFocusRing(focused && enabled)
    }
}

// TileFocusRing 已搬到 page/PageWidgets.kt（与 ActionTile 共用 TILE_HEIGHT / TILE_SIDE_PADDING / 焦点圈逻辑）。

internal fun SettingsPage.actionOf(cfg: Config, g: Gesture): Action {
    val key = cfg.key(KeyId.PLUS)
    return when (g) {
        Gesture.SINGLE -> key.single
        Gesture.DOUBLE -> key.double
        Gesture.LONG -> key.long
    }
}

internal fun SettingsPage.pick(spec: ActionCatalog.Spec, g: Gesture) {
    try {
        val cfg = config.value
        val key = cfg.key(KeyId.PLUS)
        val action = Action.parse(spec.token)
        val updated = when (g) {
            Gesture.SINGLE -> key.copy(single = action)
            Gesture.DOUBLE -> key.copy(double = action)
            //  长按换成「多条命令」时必须把「连续触发」一起关掉（纯界面操作就能落进这个洞）：
            // 先给一条单条命令开连续触发、再把长按换成多条命令 —— 换动作只写 `long`、
            // 从不碰 `longRepeat`，界面只是把那一行置灰，而按住不放会一条条重放整个命令链。
            //  只在这一种组合下清：换成别的动作时把用户的开关也一起抹掉没有道理，
            // 而「动作是多条命令 + 连续触发=开」这个不一致由这里和判定侧的
            // `KeyConfig.longIsSerial` 双重保证（判定侧那道闸门见 `GestureDetector`）。
            Gesture.LONG -> key.copy(
                long = action,
                longRepeat = key.longRepeat && !CommandLibrary.isSerial(commands.value, action.token),
            )
        }.copy(
            //  选动作 == 启用（2026-10-10 真机发现）：设置页读不到旧配置时会回落到
            // `Config.DEFAULT`，而默认是 `plus_enabled=0`（"装上模块不改变行为" 是有意的默认值）。
            enabled = true,
        )
        write(cfg.copy(keys = cfg.keys + (KeyId.PLUS to updated)), "${g.id} → ${spec.token}")
    } catch (e: Throwable) {
        XLog.w("选动作失败（忽略）：${spec.token}", e)
    }
}
