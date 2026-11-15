package com.lookie.opluskey.ui.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Command
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.model.page.FAB_SCROLL_DIRECTION_THRESHOLD
import com.lookie.opluskey.ui.model.page.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.page.PAGE_MARGIN
import com.lookie.opluskey.ui.model.theme.edgeSlideIn
import com.lookie.opluskey.ui.model.theme.edgeSlideOut
import com.lookie.opluskey.ui.state.host.UpdateState
import com.lookie.opluskey.ui.wire.fetchText
import dev.chrisbanes.haze.HazeState
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

// 右下角那颗 FAB 那一族：按钮本体、页面角落定位、按滚动方向显隐。

/**
 * 页面右下角那颗 FAB 的**本体** —— 形状 / 底色 / 阴影收在这里，各页共用
 *（各自写一份迟早走形：这几样必须一致）。
 *
 *  **不挂磨砂** —— 这颗按钮上它两头都不成立：
 *
 *   · 底色是主题原色（`colorScheme.primary`）。糊要看得见，底色就得压成半透，颜色立刻被底下那页
 *     掺淡；调回实色，糊又被盖得一点不剩。
 *   · 库的按下反馈缩的是 `Surface` **自己**那一层（`PressedScale = 0.92`），而糊只能挂在它外面
 *     —— 缩放只缩里面的面板，糊层不跟着缩，按住时从面板边上露出一圈没缩的糊。
 *
 *  56dp 的实心圆本来也剩不下多少地方给糊看，不值得为它赔上颜色。
 *
 *  形状与阴影走库默认那一档（`CircleShape`、`FloatingActionButtonDefaults.ShadowElevation` 12dp）：
 *  库自己那层 `graphicsLayer(shadowElevation, shape, clip = false)` 外面没有 `clip` 去裁它，
 *  阴影画得出来，不必自己另补一层。
 *
 * @param onClick 点击回调
 * @param modifier 调用点自己那点修饰（尺寸 / 位置这一类；底色与阴影不靠它改）
 * @param content FAB 上的内容（图标）—— 内容色由调用点给（`onPrimary`）
 * @param containerColor 底色，默认主题原色。**禁用态要换底色，不要在外面套 `alpha`**：
 *   `Modifier.alpha` 会单独合成一层，层的边界就是这颗按钮的方框 —— 库内部那层
 *   `graphicsLayer(shadowElevation)` 画的阴影落进层里，就被裁成**正方形**（真机踩过）。
 *   底色 / 字形色都是能跟着状态动画的，阴影不该为「变灰」陪葬。
 */
@Composable
internal fun PageFabButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = COUITheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = containerColor,
    ) { content() }
}

/**
 * [PageFabButton] 上那颗**图标** —— 尺寸与字形色**走同一份**，各页共用。
 *
 *  库的 `FloatingActionButton` 只定容器（56dp），内容尺寸由调用点决定：`Icon` 自己默认 24dp，
 *  摆在实心圆里偏小（圆的内接正方形还有 39dp 可用），所以统一抬到 [FAB_ICON_SIZE]。
 *  **只放大图标、不动本体** —— 56dp 是库的 `FloatingActionButtonDefaults.MinWidth/MinHeight`，
 *  连它一起改会连累右下角定位与两颗一摞的那档间距。
 *
 * @param icon 图标
 * @param contentDescription 无障碍描述（纯装饰的图标传 `null`）
 * @param tint 字形色，默认 `onPrimary`；底色不是主题色的那颗（删除）要显式传自己的那一对
 */
@Composable
internal fun PageFabIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = COUITheme.colorScheme.onPrimary,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(FAB_ICON_SIZE),
        tint = tint,
    )
}

/** 通用 FAB 上那颗图标的边长：56dp 的圆里比库默认的 24dp 大一档，又留得下圆边。 */
private val FAB_ICON_SIZE = 28.dp

/**
 * 页面**右下角那颗 FAB** —— 位置与显隐动画**走同一份**，各页共用。
 *
 *  · 「按键绑定」的「新增命令」：往下滚就藏、往上滚就露（[rememberFabVisibleOnScroll]）；
 *  · 「选择应用」的「滚到顶部」：搜索中、或已经在顶部时不出现。
 *
 *  位置（右 [PAGE_MARGIN]×2、下 [PAGE_BOTTOM_PADDING]×2）、滑动方向、时长全在这一处，
 *  免得两条 FAB 各走各的。
 *
 *  滑动是**竖直**的：从底边**往上滑入**、**往下滑出**（`slideIn/OutVertically`）。
 *  滑出要**滑到屏幕下沿之外**：它们离底边还有 [PAGE_BOTTOM_PADDING]×2 那一档，
 *  只滑自身高度的话会停在半空 —— 那段距离一起算进去。
 *
 *  注意：做成**独立 composable**，而不是让调用点直接写 `AnimatedVisibility`：
 *  这两处都在 `Column` 里的那个 `Box` 中，`AnimatedVisibility` 在那儿会同时看到
 *  `BoxScope` 与 `ColumnScope` 两个隐式接收者，解析到 `ColumnScope` 那个重载并报
 *  「cannot be called with an implicit receiver」。
 *
 * @param visible 显不显示（判据在调用点）
 * @param animate 显隐要不要过渡（`false` = 直接出现 / 消失；当前没有调用点传 `false`）
 *   留这一档是给「显隐跟着当前表单内容变」的页面：翻 tab 就会进 / 出一次，每切一次滑一遍只是噪音。
 * @param content FAB 本体（各页自己那颗）
 */
@Composable
internal fun BoxScope.PageCornerFab(
    visible: Boolean,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    PageCornerFabStack(visible = visible, animate = animate) { content() }
}

/**
 * 同 [PageCornerFab]，但那一角可以摆**一摞**（主 FAB + 它上面那颗次要的）。
 *
 *  两颗各自定位会让下面那颗把上面那颗压住 —— 位置只有一个，所以这一层直接给一个 `Column`，
 *  间距由 [FAB_STACK_GAP] 定。
 *
 *  **整摞共用一次进出场**：两颗同进同出，不会一颗先到、一颗后到。
 */
@Composable
internal fun BoxScope.PageCornerFabStack(
    visible: Boolean,
    animate: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val slidePx = with(LocalDensity.current) { (PAGE_BOTTOM_PADDING * 2).roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PAGE_MARGIN * 2, bottom = PAGE_BOTTOM_PADDING * 2),
        // 与底部工具栏同一份规格（见 [edgeSlideIn]）：差别只在多滑的距离
        enter = if (animate) edgeSlideIn(slidePx) else EnterTransition.None,
        exit = if (animate) edgeSlideOut(slidePx) else ExitTransition.None,
    ) {
        //  靠右对齐 + 自下而上排列（调用点先给下面那颗）：两颗的圆心在同一条竖线上
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(FAB_STACK_GAP),
            content = content,
        )
    }
}

/** 摞在一起的两颗 FAB 之间的间距 —— 与 `PAGE_MARGIN` 同一档（视觉上刚好断开又不散）。 */
private val FAB_STACK_GAP = 12.dp

/**
 * 「**往下滚就藏、往上滚就露**」—— 页面右下角那颗 FAB 的显隐判据（配 [PageCornerFab]）。
 *
 *  用法：`val fabVisible = rememberFabVisibleOnScroll(scrollState)`。
 *
 *  判的是 [ScrollState.value] 的**增量符号**：`value` 变大 = 内容往上走 = 「往下滚」⇒ 藏；
 *  变小 = 「往上滚」⇒ 露。
 *
 *  注意：不能只看「有没有滚过」、也不能不设门槛：手指轻轻抖一下也会换向，FAB 就会一闪一闪。
 *  所以单次增量要超过 [FAB_SCROLL_DIRECTION_THRESHOLD] 才认作换向（同一个方向上的连续
 *  滚动只在那一下判一次，不会反复翻转）。
 *
 * @param scrollState 当前页的滚动状态 —— **传当前那一页的那一份**（三个手势页各持一份）。
 *   换页时它变了 ⇒ [visible] 跟着重置成「露」，不会把上一页藏起来的状态带过来。
 */
@Composable
internal fun rememberFabVisibleOnScroll(scrollState: ScrollState): Boolean {
    val thresholdPx =
        with(LocalDensity.current) {
            FAB_SCROLL_DIRECTION_THRESHOLD.roundToPx()
        }
    var visible by remember(scrollState) { mutableStateOf(true) }
    LaunchedEffect(scrollState, thresholdPx) {
        var last = scrollState.value
        snapshotFlow { scrollState.value }.collect { value ->
            val delta = value - last
            last = value
            when {
                delta > thresholdPx -> visible = false
                delta < -thresholdPx -> visible = true
            }
        }
    }
    return visible
}

/**
 * 把页面能力收成「检查更新要的那三样」—— 见 `UpdateState.Host`。
 *
 *  为什么适配放在 `ui` 根：`ui.state` **不许反向认识页面**（分层是单向的
 *  `ui.screen → ui → ui.state`，见 `docs/architecture/decoupling.md`）。这一层只做「换成 lambda」，
 *  不掺任何判断 —— 判断留在状态层，能力留在页面。
 */
internal fun SettingsPage.updateHost(): UpdateState.Host =
    UpdateState.Host(
        context = context,
        versionName = { moduleVersionName() },
        fetch = { url, timeoutMs -> fetchText(url, timeoutMs) },
    )

/**
 * hook 侧（`ui/hook/`）在**建页之前 / 建页之后**塞给页面的那几件东西 —— 一条宿主标题 + 六个回调。
 *
 *  为什么单列一个对象：这些位以前是七个平铺在 `SettingsPage` 上的 `var`，赋值在 `SettingsPageHooks`
 *  一处、调用散在六处。它们的共同点是「**页面自己实现不了、必须由 hook 侧给**」（开 SAF、
 *  露出宿主原版页、重刷状态栏、注册返回回调）—— 摊开时这条界线看不出来，容易被当成页面自己的状态。
 *
 *  注意：全部可空 / 有默认值 —— 页面在没有 hook 侧的情况下（单测、调试通道直接建页）也要能跑。
 */
internal class PageHooks {
    /**
     * 首页标题的**兜底** —— 宿主 Activity 的原文，hook 侧在建页之前赋值。
     *
     *  页面读的是字典里 `home.title`（现在是模块自己的名字 `OPlusKey`），这一位只在字典缺那一档
     *  （打包漏了语言档）时才轮得到。默认值给同一个品牌名、不再念 `I18n`：没有 hook 侧的场合
     *  （单测 / 调试通道直接建页）标题也得是对的。
     */
    var homeTitle: String = "OPlusKey"

    /** 系统深浅变化回调（给 hook 重刷状态栏配色）。 */
    var onSystemThemeChanged: (() -> Unit)? = null

    /**
     * 「这一刻还要不要自己接返回」变了（进出子页 / 浮层开合）—— 由 `PageEffects` 在组合期回报，
     * hook 侧据此在宿主上注册 / 注销返回回调（见 `SettingsPage.needsBackInterception`）。
     *
     *  **只有需要时的那一档才值得注册**：注册了系统就不做关页动画了，而首页那一退正需要它。
     */
    var onBackInterceptionChanged: (() -> Unit)? = null

    /** 菜单里「打开自带的快捷键界面」—— 由 hook 侧实现（藏模块页面、露出宿主原版页面）。 */
    var onOpenStock: (() -> Unit)? = null

    /** 「导出日志」—— hook 侧实现：弹系统的「保存到」选择器，写完再回报。 */
    var onExportLog: (() -> Unit)? = null

    /**
     * 「导出日志」那一行的副标题：缓冲区现在有多少行、来自几个进程（`menu.log_buffer`）。
     *
     *  要跨进程收一次（最多等几百毫秒），所以是 **suspend**：调用方必须放在 IO 上，
     *  且只在菜单打开那一刻取一次。`null` = 没有 hook 侧（调试建页），那一行就不带副标题。
     */
    var logBufferSummary: (suspend () -> String?)? = null

    /**
     * 「更多 → 导出配置」—— hook 侧实现：弹系统的「保存到」选择器，把**这份配置 + 它的命令库**
     * 写成一份文件。
     *
     * 参数是**导出哪一份的槽名**（`config` / `config-2` …，见 `ConfigProfiles`）：多份配置时
     * 界面先让用户选一份再调这里；只有一份时直接传当前那份。
     *
     * 与 [onExportLog] 同一条 SAF 路（结果只能从宿主的 `onActivityResult` 收）。
     */
    var onExportConfig: ((String) -> Unit)? = null

    /** 「更多 → 导入配置」—— hook 侧实现：弹系统的「打开」选择器，读回文件再写进配置与命令库。 */
    var onImportConfig: (() -> Unit)? = null
}

/**
 * 页面「外观 + 磨砂取样」那一域的状态。
 *
 *  为什么单列一个对象：这四样以前摊在 `SettingsPage` 上，分布在六个文件里读写 ——
 *  「磨砂为什么没糊」这类问题要同时看 `pageHazeState`（谁在栈顶）与 `hazeStatesByPage`
 *  （每页登记了没有），摊开时这两个的**对应关系**从类定义上看不出来。收进来之后，
 *  「取样源是谁、谁登记的、降级日志报过没有」是一眼能读完的一件事。
 *
 *  注意：本类**不引用 `SettingsPage`**，只持状态；写它的仍然是那几个扩展函数。
 */
internal class PageChrome {
    /**
     * 系统此刻是不是深色 —— 建页期由 `createView` 定一次，之后由 `SyncToHost` 跟着系统改。
     *
     *  页面的底色、色板、状态栏配色全读它；**不读 Activity 的 `configuration`**（宿主声明了
     *  `configChanges=uiMode`，那份配置会停在旧值），见 `systemNight` 的注释。
     */
    var dark by mutableStateOf(false)

    /**
     * **当前栈顶页**内容那一层的取样状态 —— 顶栏 / 浮层菜单 / 底部工具栏的磨砂都从它取样。
     *
     *  注意：**必须按「谁在栈顶」来选，不能按「谁最后组合」**。旧写法是
     *  `if (pageBackdrop !== backdrop) pageBackdrop = backdrop` —— 最后组合的那一页说了算；
     *  而深度 2 往返还栈时，被 cull 的首页会重新组合、时机晚于栈顶页，于是取样源被它抢走：
     *  浮层菜单取到的是**另一页**的内容（真机：「磨砂处似乎是取样的两个 switch，但菜单底下
     *  并没有 switch」）。选法见 `PageEffects` 里那个 `LaunchedEffect(navStack.lastOrNull())`。
     *
     *  注意：取样方**不能落进它自己取样的那个 `hazeSource`** —— 落进去就是渲染节点链无限增长、
     *  RenderThread 递归到栈溢出（真机崩过一次）。浮层菜单画在 Scaffold 的 `COUIPopupHost` 里
     *（页面那层之外）才是安全的。
     */
    var pageHazeState: HazeState? by mutableStateOf(null)

    /**
     * 每页上一次量到的**顶栏高度**（像素）—— 只是缓存，不参与组合。
     *
     *  退出子页再回到某一页时，那一页的 `BandHeight` 是新建的（起点 0），只能按兜底常量排一帧；
     *  而实测值与兜底常量差几个 dp，真机表现就是「连续滚动后回首页，内容整体上移一点点」。
     *  有这份缓存就从上次量到的值起步，转场那几帧也不会跳。
     */
    val headerHeights = HashMap<Screen, Int>()

    /**
     * 每页各一份取样状态（键是页）—— 各页在 `DisposableEffect(s)` 里把自己那份登记进来、并在
     *  `onDispose` 摘掉（见 `PageEntry` 里 `NavDisplay` 的 entry）。页被 cull 后重建会换一份新的
     *  进来，旧的那份随 `onDispose` 出表（它的源节点已 detach）。
     */
    val hazeStatesByPage = mutableMapOf<Screen, HazeState>()

    /**
     * 「取样层没就绪 ⇒ 这次用不透明底」那一条日志的闸 —— 建页期只报一次，免得每个消费方各刷一行。
     * 见 [pageHazeState] 与 `PageMenuPopup` / `CommandToolbar` 里那两处判定。
     */
    var frostDegradeLogged = false
}

/**
 * 设置页上**所有盖在页面之上的东西**（`⋮` 菜单、各类确认窗、浮层）那一域。
 *
 *  ## 为什么是一组
 *
 *  这五样原来摊在 [SettingsPage] 上，看上去各是各的（两个开关、一个待确认项、一个计数、一张
 *  登记表）。它们其实说的是同一件事：**「此刻页面上有东西盖着」以及「怎么把它收掉」**。
 *  摊开时那条对应关系在类定义上看不出来 —— 比如「浮层开着时系统返回要整个吞掉」需要同时知道
 *  [openCount] 与 [closers]，而它们隔着一百多行。
 *
 *  ## 几样「问一句」的开关
 *
 *  [confirmReset] / [confirmResetModule] / [confirmRestart] / [confirmLeaveCommands] /
 *  [confirmLeaveEdit] / [confirmLeavePick] / [pendingDraftLoad] 都是「要不要弹一张确认窗」，
 *  但问的是不同的事，**不能合并**：
 *   · [confirmReset] —— 重置配置（由「更多选项 → 切换配置」菜单或调试通道置位）；
 *   · [confirmResetModule] —— 重置模块（由首页 `⋮` 菜单置位，范围比上面那个大：连变量表一起清）；
 *   · [confirmRestart] —— 重启服务（由 `⋮` 菜单置位）；
 *   · [confirmLeaveCommands] —— 退不退页（表单改过还没保存时由 [SettingsPage.goBack] 置位）；
 *   · [confirmLeaveEdit] —— 退不退**编辑态**（「按键功能」页里由 [SettingsPage.goBack] 置位，
 *     那一退不离开页面）；
 *   · [confirmLeavePick] —— 退不退「选择应用」页（这一趟挑中过一条却还没提交时由
 *     [SettingsPage.goBack] 置位）；
 *   · [pendingDraftLoad] —— 换不换内容（要载入另一条命令时由 [SettingsPage.loadDraft] 置位，
 *     存的是**待载入的那一条**，不是布尔）。
 *
 *  后两者必须分开：一个是「退页」，一个是「覆盖表单」，同一个开关会让「我要换命令」被当成
 *  「我要退出」。
 *
 *  ## 为什么这个类不引用 [SettingsPage]
 *
 *  它只持状态与「怎么收起」的登记表，判据（要不要接管返回）留在页面那边 —— 那几条要看
 *  `navStack` 与草稿是否脏，属于页面自己的账。所以这一层可以独立描述，不是把一个大对象
 *  拆成一个小对象。
 */
internal class PageOverlays {
    /**
     * 此刻开着几个浮层 —— [Closer] 在组合期维护，读它的有两处：
     * 预测返回的 `onBackStarted`（决定接不接管）与自己那个 handler。
     *
     *  用计数而不是布尔：浮层可以叠（菜单上再弹一层确认），布尔会让「收掉一层」直接归零。
     */
    val openCount = mutableIntStateOf(0)

    /**
     * 页面里**所有浮窗**的「统一收起」通道，只存「怎么把我关掉」、不存「谁开着」。
     *
     *  关一个本来就关着的浮窗是一次**无副作用的空写**（`mutableStateOf` 写同值不触发重组），
     *  所以收的时候不必先判在不在。返回值 = 这一层刚才**真的开着**（只用来把日志写准）。
     */
    val closers = mutableListOf<() -> Boolean>()

    /**
     * 「重置配置」确认弹窗 —— 由「切换配置」菜单里那一行或调试通道（`action=CONFIRM_RESET`）置位，
     * 窗在「更多选项」页画（见 `ConfigProfileActions.ProfileResetConfirmDialog`）。
     */
    var confirmReset by mutableStateOf(false)

    /**
     * 「重置模块」确认弹窗 —— 由首页 `⋮` 菜单那一项置位，窗在 `HomeHeaderMenu` 里画。
     *
     *  与 [confirmReset] **分开**：两者作用范围不同（一个只重建当前配置，一个连变量表一起清），
     *  文案也不同；混用一个开关会让首页那一项弹出「重置配置」的话。
     */
    var confirmResetModule by mutableStateOf(false)

    /**
     * 「重启服务」确认弹窗 —— 由首页 `⋮` 菜单那一项置位，窗在 `HomeHeaderMenu` 里画。
     *
     *  为什么这一下也要问：它会**杀掉设置页进程**再让宿主把它拉回来（页面会闪一下重开），
     *  正在编辑、还没保存的内容跟着丢 —— 与「重置配置」同一档代价，菜单里点一下就走太轻了。
     */
    var confirmRestart by mutableStateOf(false)

    /**
     * 「表单里有没保存的改动，但用户要退出这一页」—— 待确认。
     * 触发点是 [SettingsPage.goBack]（草稿 `dirty` 时置位并**拦住不退**），窗由 `CommandScreen` 画。
     */
    var confirmLeaveCommands by mutableStateOf(false)

    /**
     * 「编辑态里要退出这一页」—— 待确认。
     * 触发点是 [SettingsPage.goBack]（「按键功能」页正在编辑时置位并**拦住不退**），
     * 窗由 `KeysScreen` 画（`KeysLeaveEditConfirm`）。
     *
     *  与 [confirmLeaveCommands] 分开：那个问的是「不要的命令草稿会不会丢」，这个问的是
     *  「编辑态这一趟的勾选会不会丢」—— 两件事的正文、按钮文案都不同，混用一个开关会让
     *  一边的话跑到另一边去。
     */
    var confirmLeaveEdit by mutableStateOf(false)

    /**
     * 「选择应用」页这一趟已经挑中、但还没提交，用户却要退页 —— 待确认。
     * 触发点是 [SettingsPage.goBack]（`picker.draft` 非空时置位并**拦住不退**），
     * 窗由「选择应用」页自己画（`AppPickerDialogs`）。
     *
     *  与 [confirmLeaveCommands] 分开：那个问的是「命令草稿会不会丢」，这个问的是
     *  「这一趟挑中的那个应用要不要留下」—— 两件事的正文与按钮文案都不同。
     */
    var confirmLeavePick by mutableStateOf(false)

    /**
     * 「表单里有没保存的改动，但用户要换成另一条命令」—— 待确认的那一条（`null` = 没在弹）。
     *
     *  触发点是 [SettingsPage.loadDraft]（草稿脏时置位、**先问一句**），窗由 `CommandScreen` 画。
     *  与 [confirmLeaveCommands] 分开：那个问的是「退不退页」，这个问的是「换不换内容」。
     */
    var pendingDraftLoad by mutableStateOf<Command?>(null)

    /** 有浮层开着吗 —— 开着时系统返回**整个吞掉**（见 `BackInterception.handleDiscreteBack`）。 */
    fun hasOpen(): Boolean = openCount.intValue > 0

    /**
     * 页面离开时**主动收掉所有浮窗**。
     *
     * 为什么必须「主动」：COUI 的 `OverlayListPopup` / `OverlayDialog` 都渲染在
     * **根 Scaffold 的 `popupHost`** 那一层（见库的 `Scaffold` 文档），它**不属于任何一页**
     * —— 既不跟着页面转场走，也不随「那一页出栈」以外的任何事消失；而首页顶栏那颗菜单
     * 更是**永远在栈里**（首页常驻），它的浮窗状态永远不会被组合释放。于是不主动收的话：
     * 从「设置」重进、切后台回来，菜单还开着；换页时浮窗还会**钉在屏幕上不动**，
     * 等那一页出栈才跟着消失。
     *
     * 触发点（都在「页面离开」这条需求路径上）：
     *  · 换页 / 清栈回首页 —— `PageContent` 里那个看着 `screen` 的 `LaunchedEffect`；
     *  · 从设置重进模块（栈顶未变时 LaunchedEffect 不触发）—— [SettingsPage.onHostReentered]；
     *  · 顶栏返回键在首页（关页面）—— `PageHeader`；
     *  · 露出宿主原版界面 / 系统返回关页面 / 宿主 Activity `onStop` —— `SettingsPageHooks`。
     *
     * @param reason 只进日志，便于真机上核对「是哪条路把它收掉的」
     */
    fun closeAll(reason: String) {
        if (closers.isEmpty()) return
        // 复制一份再遍历：关闭过程中会触发重组，进而增删登记表
        val snapshot = closers.toList()
        val closed = snapshot.count { runCatching { it() }.getOrDefault(false) }
        // 一层都没开着时不写日志：页面离开是高频动作，每次都留一行「收掉 0 个」只会淹掉别的
        if (closed > 0) XLog.d { "页面离开（$reason）：已收起 $closed 个浮窗" }
    }

    /**
     * **系统返回**：只收**最上面那一层**。
     *
     *  [closeAll] 是给「页面离开」用的 —— 那一趟本来就该把整页的浮层收干净。系统返回不一样：
     *  用户按一次返回，期望的是**退一层**（对话框关掉、底下那层菜单还在）。一次收光的话，
     *  从「切到某一份配置」这种两层叠着的位置按返回，会连菜单一起消失，看着像返回跳了两级。
     *
     *  「最上面」按登记顺序取**最后一个真的关掉了的**：同一页里子层总是比父层后组合出来
     *  （父菜单点开才建子层），所以登记表末尾就是最上面那层。
     *
     *  @return 有没有收掉一层（`false` = 一层都没真的开着）
     */
    fun closeTop(reason: String): Boolean {
        val snapshot = closers.toList()
        for (closer in snapshot.asReversed()) {
            if (runCatching { closer() }.getOrDefault(false)) {
                XLog.d { "浮层（$reason）：收掉最上面一层" }
                return true
            }
        }
        return false
    }

    /**
     * 浮窗在**组合期**把自己登记进 [closers] —— 各浮窗调用点只需要加一行：
     * `overlays.Closer(show = open) { open = false }`。
     *
     * 登记时机是「宿主组件进组合」，**不是**「浮窗弹出来」：`show` 只是浮窗自己的一个参数，
     * 组件一直挂着（`show = false` 时只是不渲染内容）—— 所以收起时那一下写的是「本来就该是
     * 的值」，不判空也不会误伤。
     *
     * `rememberUpdatedState` 是为了拿到**最新**的 `show` / `onDismiss`：那些值每次重组都会变，
     * 而注册只在第一次组合时做一次。
     *
     * @param show 这一层此刻开着没有 —— 只决定「这一层算不算被收掉了一个」（日志用）
     */
    @Composable
    fun Closer(
        show: Boolean,
        onDismiss: () -> Unit,
    ) {
        val latestShow by rememberUpdatedState(show)
        val latestDismiss by rememberUpdatedState(onDismiss)
        // 「有没有浮层开着」的计数 —— 预测返回的 onBackStarted 要用它决定接不接管
        // （见 [SettingsPage.wouldConsumeBack]）。键是 `show`：翻转时旧的 effect 收尾、新的进来，
        // 一次翻转恰好各加减一次；组件出组合时若还开着也会补减。
        DisposableEffect(show) {
            if (show) openCount.intValue++
            onDispose { if (show) openCount.intValue-- }
        }
        DisposableEffect(Unit) {
            val closer = {
                if (latestShow) {
                    latestDismiss()
                    true
                } else {
                    false
                }
            }
            closers += closer
            onDispose { closers -= closer }
        }
    }

    /**
     * 开着几个「**自己接系统返回**」的浮层 —— 底部抽屉（`PageSheet`）与**全部浮层菜单**
     * （`MenuPopup`，各页的 `⋮` 菜单 / 长按菜单 / 二级菜单都从它出）。
     *
     *  与 [openCount] 分开记，因为两者要的东西正相反：
     *   · [openCount] 一非零，[wouldConsumeBack] 就判「**别**把这一退喂给页面栈」（浮层自己不接，
     *     喂进去只会让底下的页面跟着手指滑）；
     *   · 抽屉与菜单相反 —— 它们的内容布局里挂着库自己的 `NavigationBackHandler`，要**逐帧进度**
     *     才动得起来，事件必须**喂进去**。所以它们额外记这一格，让 [wouldConsumeBack] 放行。
     *
     *  干吗不干脆不登记 [openCount]？那样「三键导航 / 实体返回」那一档会退页、且换页收浮窗
     *  也收不到它（真机踩过：条件执行里按返回把页面退了）。两条账本来就不是一件事。
     *
     *  只给**菜单**登记、不给确认窗登记：确认窗是「必须给个答复」的模态窗，收它要走它自己的
     *  按钮或点外部，不该被一次滑动手势带走（它那一档仍走 [closeTop] 的一次性收起）。
     */
    val backCapturing = mutableIntStateOf(0)

    /**
     * 登记一个「自己接返回」的浮层（见 [backCapturing]）。
     *
     * @param show 这一层此刻开着没有 —— 开着才算「这一退归它」
     */
    @Composable
    fun BackCapturing(show: Boolean) {
        DisposableEffect(show) {
            if (show) backCapturing.intValue++
            onDispose { if (show) backCapturing.intValue-- }
        }
    }
}
