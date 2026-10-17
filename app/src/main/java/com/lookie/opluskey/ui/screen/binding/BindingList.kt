package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.core.i18n.I18n
import com.lookie.opluskey.core.i18n.Strings
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.CommandKind
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GlobalConfig
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.isSerialCommand
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.component.AnimatedColumn
import com.lookie.opluskey.ui.component.SliderRow
import com.lookie.opluskey.ui.component.SwitchRow
import com.lookie.opluskey.ui.component.currentHeaderHeight
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.model.GRID_GAP
import com.lookie.opluskey.ui.model.GRID_ROW_GAP
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import com.lookie.opluskey.ui.model.PAGE_MARGIN
import com.lookie.opluskey.ui.model.PAGE_TOP_PADDING
import com.lookie.opluskey.ui.model.PageIcons
import com.lookie.opluskey.ui.model.ROW_END_ICON_SIZE
import com.lookie.opluskey.ui.model.durationText
import com.lookie.opluskey.ui.screen.apppicker.appLabelOf
import com.lookie.opluskey.ui.screen.binding.TileMenuPopup
import com.lookie.opluskey.ui.state.deviceSupported
import com.lookie.opluskey.ui.state.linkReady
import com.lookie.opluskey.ui.state.rememberTick
import com.lookie.opluskey.ui.state.setInstantSingle
import com.lookie.opluskey.ui.state.setLongRepeat
import com.lookie.opluskey.ui.state.write
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/**
 * 一个手势下的可选动作**网格**（pager 的一页）。
 */
@Composable
internal fun SettingsPage.BindingList(
    g: Gesture,
    enabled: Boolean,
    /**
     * 这一页是不是 pager 的**当前页**。
     *
     * pager 会把相邻页一起留在组合里（滑动转场要它），于是三页的
     * [LaunchedEffect] / `onPreviewKeyEvent` / 浮层状态全都活着。三个东西必须由它把住：
     *   · 进场请求焦点 —— 否则三页同时 `requestFocus()`，最后请求到的说了算，
     *     真机表现是「翻到第二页，方向键却在动第一页的圈」；
     *   · [tileMenu] —— 上一页开着入口菜单时翻页，浮层会跟着这一页一起活到下一帧，
     *     而它的锚点属于上一页那一格；
     *   · 方向键 / 确认键 —— 非当前页不能吃键，否则按键被它抢先消费掉。
     */
    active: Boolean,
    /** 点「打开应用」那一格时跳「打开应用」页（它是**入口**，不是取值）；把本页的手势带上。 */
    onOpenAppPicker: (Gesture) -> Unit,
    /**
     * 点「自定义命令」那一段里那行「去创建自定义命令」时进「自定义命令」页——**新增**一条。
     * 与右下角那颗 FAB 同一个去处（都是 [navigateToCommands]），那一行只在还没保存过命令时出现。
     */
    onCreateCommand: () -> Unit,
    scrollBehavior: ScrollBehavior,
    /**
     * 本页的滚动状态 —— **每个手势页各持一份**（由 [BindingScreen] 用 `gestures.map { rememberScrollState() }` 建）。
     *
     * 注意：不能写「三个手势页共用同一个实例」—— 与现实相反：共用一份
     * 会让「切回来就滚不动」（`ScrollState` 内部只有一个 `scrollableState`，pager 滑动时相邻两页
     * 同时在场、互相打架，见 [BindingScreen] 里那段）。现在只是把**本页那一份**接上 `verticalScroll`。
     */
    scrollState: ScrollState,
    /**
     * 内容顶上要给分段控件留出的那一块 = 分段控件自己的高度（含它的上下内边距）。
     *
     *  **必须与顶栏里那条分段控件一样高**（同一个值）：它让首屏第一行不被钉住的分段控件压住；
     * 滚起来之后内容会钻到控件背后，由控件自己那层不透明底挡着（见 [BindingScreen]）。
     * 值由 [BindingScreen] 量出来（`onSizeChanged`）。
     */
    tabBlock: Dp,
) {
    val cfg = configState.config.value
    // 记住这份清单：`forGesture` 每次都要 `ALL + commands.map { … }`（一次 concat 加一次 map），
    //  而它在每次重组上（pager 三页同时在场 ⇒ 每帧三组）。key 用结构相等的值，
    //  命令库没变时不重建 —— 下面 `remember(specs)` / `LaunchedEffect(specs, …)` 比的也是结构相等，
    //  所以这份清单的**身份**稳下来只有好处。
    val specs = remember(g, configState.commands.value) { ActionCatalog.forGesture(g, configState.commands.value) }
    // `cmd:<名字>` → 命令。自定义命令那组里每个格子都要按 token 反查一次自己那条命令，
    //  原来是在格子里 `commands.value.firstOrNull { … }` ⇒ n 条命令就是 n×O(n)。
    val commandsByToken = remember(configState.commands.value) { configState.commands.value.associateBy { it.token } }
    val current = actionOf(cfg, g)
    // 「打开应用」格选中时标题下面那行小字 = **当前绑的应用名**
    // 名字查一次就缓存（PM 查询，入参是动作所以只在换动作时重查）。
    //  这一格能写出来的是**两种**动作：`app:` 与 `window:`（小窗，[Action.AppWindow]，
    // 由菜单里那项「小窗打开」定，见 [SettingsPage.picker]）—— 两种都要取名字，
    // 少认一种那一格就没有小标题（实证：开着小窗挑完应用回来，标题下面是空的）。
    val boundAppLabel =
        remember(current) {
            val target =
                when (current) {
                    is Action.App, is Action.AppWindow -> current.appTarget
                    else -> null
                }
            target?.substringBefore('/')?.let { appLabelOf(it) }
        }
    // 「打开应用」格绑的是**小窗打开**（`window:`，[Action.AppWindow]）时，标题右边补一枚「小窗起」标记
    // —— 这一格只有一行标题 + 应用名，不点开「选择应用」菜单看不出绑的是哪一档。
    //  判据直接看当前动作的类型：`isSelected` 对 [ActionCatalog.APP_TILE] 就是「是 App 或 AppWindow」，
    //  所以这一条为真时选中的必然正是那一格，不用再比对 spec。
    val appSmallWindow = current is Action.AppWindow
    // 图标表是页面级那一份（[SettingsPage.tileIcons]），与「添加命令」浮层同源
    val icons = tileIcons.value

    // 当前选中的那一项（配置里是清单外的写法时为 -1 ⇒ 焦点退回第一项）
    val selectedIndex = specs.indexOfFirst { ActionCatalog.isSelected(it, current) }

    // 入口只有 [BindingScreen] 右下角那颗 FAB（两段之间没有跳转格子）。
    // 于是**焦点格索引与 [specs] 下标一一对应**
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
     *  它带着**那一格自己那份触点锚点**（见 [OpenTileMenu]）：锚点由格子自己 `remember`、
     *  点的时候随这一格一起上报，不再在页面这一层一格格手写 —— 那种写法每加一个入口格就得补
     *  一份锚点、还要在一张 `when` 表里加一行，漏一个菜单就弹到别人身上
     *（「通知与控制中心」正是这么漏的，它落到了响铃格那一份上）。
     */
    var tileMenu by remember { mutableStateOf<OpenTileMenu?>(null) }

    /**
     * 关掉菜单之后、退场动画期间用的**占位锚点**（它没记过触点，不会真的参与定位）。
     *
     * 那段时间真正生效的是 [MenuPopup] 内部保持住的上一次那份内容与锚点，见那边的注释。
     */
    val noAnchor = remember { PreciseClickState() }

    /**
     * 每一格自己那份触点锚点，按 token 索引 —— **只给确认键那条路用**。
     *
     *  格子仍在自己那个 `key(spec.token)` 里 `remember` 一份（网格那段注释：不再有「哪一格用哪一支」
     *  的表，加一个入口格不用改这里）；这张表只是把**同一份**登记下来，因为确认键不在格子的组合
     *  作用域里、拿不到它。键是 token，所以新增入口格同样不用改这里。
     */
    val tileAnchors = remember { mutableMapOf<String, PreciseClickState>() }

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

    /**
     * 点一下某一格 —— **点击与确认键共用这一个入口**。
     *
     *  注意：入口格必须在这里分派（「打开应用」进挑选页、其余入口格弹菜单），**不能交给 [select]**：
     *  `app:` / `nav:` / `key:` / `panel:` / `zoom:` 这些都是**标记** token，`Action.parse` 认不出
     *  —— `nav:` / `key:` / `zoom:` 落 `else -> Shell(...)`、`app:` 落 `App("")`、`panel:` 落 `Panel("")`，
     *  交过去就是把垃圾写进配置再广播出去（确认键那条路当时只走 [select]，
     *  外接键盘 / 遥控器按一下就能踩到）。
     *
     * @param anchor 这一格自己那份触点锚点（弹菜单要用它定位）；确认键那条路从 [tileAnchors] 取
     */
    val activate: (ActionCatalog.Spec, PreciseClickState) -> Unit = { spec, anchor ->
        //  被锁住的命令一概不派发（[ActionCatalog.isLocked]，与格子那个 `enabled` 同一判据）：
        //  格子那边已经不可点、库也不给回调，这里兜的是**确认键**那条路 —— 它自己派发。
        //  不弹提示：灰着本身就是那句话（要说原因的是「整页禁用」那一档，见 [pick]）。
        if (ActionCatalog.isLocked(spec)) {
            XLog.w("按键绑定：${spec.token} 在本机没有落点（已锁住），确认键不派发")
        } else if (spec.token == ActionCatalog.APP_TILE) {
            if (enabled) {
                tick()
                //  不挪焦点圈 —— 与 [select] 同一条规矩：圈只跟随选中态。
                // 挑中应用时绑定变了，圈由重定位 effect 挪过来。
                onOpenAppPicker(g)
            }
        } else if (ActionCatalog.isEntryTile(spec)) {
            // 入口格（系统导航 / 系统按键 / 媒体控制 / 响铃状态 / 应用转小窗）：
            // 只弹二级菜单供选择，不直接改绑第一项；
            // 只有在弹出的菜单中真正点击了某一项后才改绑（见 TileMenuPopup.onPick）。
            if (enabled) {
                tick()
                tileMenu = OpenTileMenu(spec, anchor)
            }
        } else {
            select(spec)
        }
    }

    // 进场把焦点放到「当前已选中的那一项」上（[focusedIndex] 的初值就是它）。
    //  必须在 LaunchedEffect 里请求：Compose 明确禁止在组合期间请求焦点
    // （`FocusRequester` 会直接打警告并忽略）。
    //
    // 补：**选中项变了就把焦点圈收回来** —— 绑定被改（点格子 / 挑应用 /
    // 没挑回退）之后，圈跟着 `selectedIndex` 走。
    LaunchedEffect(specs, selectedIndex, active) {
        //  **只有当前页抢焦点**：pager 的相邻页同样在组合里，三页各请求一次的话
        //  最后请求到的那一页拿走焦点 —— 而按键事件是按焦点派发的，方向键于是动的是
        //  另一页的圈（真机表现见 [active] 的注释）。翻页后 `active` 变化会重跑本 effect，
        //  把焦点收回到刚翻到的那一页。
        if (!active) return@LaunchedEffect
        val seat = selectedIndex.coerceAtLeast(0)
        if (seat != focusedIndex) focusedIndex = seat
        // 先把这个页面 View 的 Android 焦点拿回来，再请求 Compose 内部的焦点：
        // 按键事件按 Android 焦点派发，只请求 Compose 焦点的话在「页面 View 从未
        // 拿过焦点」的情况下按键仍然进不来。
        val viewFocus = rootView?.requestFocus() ?: false
        val tileFocus = focusers.getOrNull(focusedIndex)?.requestFocus() ?: false
        XLog.w(
            "按键绑定：进场请求焦点 index=$focusedIndex" +
                " viewFocus=$viewFocus tileFocus=$tileFocus",
        )
    }

    // 翻走这一页时把入口浮层收掉。
    //
    //  `tileMenu` 是**每个手势页各一份**的状态（见它的注释），而 pager 会把上一页留在组合里
    //  —— 浮层于是跟着那一页活过了翻页，锚点还指着上一页那一格（弹到屏幕外 / 弹在别处）。
    //  这里在 `active` 变 false 的那一帧关掉它，用户看到的就是「翻页即收」。
    LaunchedEffect(active) {
        if (!active) tileMenu = null
    }

    // 滚动时把入口浮层收掉。锚点是**按压那一点**换算出来的窗口坐标（见 [OpenTileMenu]）：
    //  行一滚走，那一格就不在锚点处了 —— 菜单留在原地会指着一格已经滚走的内容。
    //  库那层「点外面关」只认 tap、拖动不算，所以这一口得自己收。
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { tileMenu = null }
    }

// 方向键 / 确认键自己接管。
    val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = onKey@{ e ->
        // 主开关为关：方向键 / 确认键一概不接管（「禁用所有控件」）
        if (!enabled) return@onKey false
        // 非当前页不吃键：pager 的相邻页在组合里，不拦的话按键会被它抢先消费
        if (!active) return@onKey false
        // 浮层开着时把键交给浮层（下面 [tileMenu] 那一层自己处理上下 + 确认）
        if (tileMenu != null) return@onKey false
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
                // 焦点格索引 == `specs` 下标（跳转按钮撤掉之后一一对应，见上面的账）。
                //  **与点击同一个入口**（[activate]）：入口格在那边分派（进挑选页 / 弹菜单），
                // 直接走 [select] 会把 `app:` / `nav:` / `key:` / `panel:` / `zoom:` 这些标记 token
                // 当取值写进配置。锚点取那一格登记的那份，拿不到就退回占位。
                specs.getOrNull(focusedIndex)?.let { spec ->
                    activate(spec, tileAnchors[spec.token] ?: noAnchor)
                }
            }
            return@onKey true
        }

        val step =
            when {
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
            //  **纵向 padding 一档都不留**（顶栏改成叠在内容上之后，又去掉底部那一档）：
            //  `padding` 落在 `verticalScroll` **之前**就等于是**滚动视口之外**的内缩 ——
            //  顶部那 12dp 会让内容永远到不了顶栏底下、模糊没东西可糊（滚起来时屏幕最上面
            //  留一条不带内容的空带）；底部那 12dp 同理：**视口比整页矮 12dp**，
            //  屏幕最底下那一条里没有内容，而手势导航栏是**透明**的 ——
            //  那一条就被露成「一块平色」，看着像导航栏不透明、把控件挡住了
            // （真机反馈「只有按键绑定页这样」，其余页的 padding 都在视口**内侧**）。
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

        // —— 按键设置：**在网格上面**（从网格下面挪上来）——
        //
        //  它是一张「设置项列表」（滑杆 / 开关那些行），属于**这一档手势本身**的行为，
        //  排在动作网格之前：先看到「这一档怎么判、有什么可调」，再挑「绑什么」。
        //  只有单击 / 长按两档有（双击那档的两个可调项都在单击档里，见下）。
        //
        //  它下面那一档间距只在**真有这张卡**时给 —— 否则双击档的「内置命令」标题
        //  会白多一截空档（标题紧贴它下面的内容，这是全页的规矩）。
        if (g == Gesture.SINGLE) {
            SmallTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            //  注意：这张卡的两条**写路径**吃与网格同一套「适配清单」闸：
            //  这里只看 `enabled`（主开关）的话，网格那侧的 `pick` 还会拦「本机不在清单里」
            //  —— 两套口径。清单外的组合本来进不了这一页（`deviceSupported=false` 时链路恒不通），
            //  所以这是兜底，不是主闸；带上它参数才不会在「不该写的机器」上落盘。
            //  （`configReadOnly` 由 [write] 单点拦，不在这里重复。）
            //
            //  这里**故意比网格格子少判一道 `linkReady`**（网格那边是
            //  `enabled && linkReady && deviceSupported`，与 [pick] 同一判据）：这张卡写的是
            //  **参数**（延迟 / 立即触发 / 连续触发 / 间隔），写了就落盘，注入侧恢复后照样生效，
            //  不需要注入侧回话；网格格子点了要写动作、拿去执行，未就绪时写了也是白写。
            //  这是有意的不一致，不要再把两边「统一」。
            val rowsEnabled = enabled && deviceSupported
            // 「单击延迟」不跟着「立即触发单击」置灰：两个状态独立 ——
            // 前者管「等多久才判双击」，后者管「双击为无操作时还要不要等」。
            // （开着后者且双击为无操作时前者确实用不上，但那是运行期的组合，
            // 由判定侧按配置自己走，界面不替用户把这一行锁死。）
            Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
                SliderRow(
                    title = I18n.get(Strings.SETTING_DOUBLE_CLICK_DELAY, "单击延迟"),
                    icon = PageIcons.DelayTap,
                    summary = I18n.get(Strings.BINDING_DOUBLE_CLICK_WAIT, "单击后等待双击的时长"),
                    position = cardListPositionOf(0, SINGLE_SETTING_ROWS),
                    min = GlobalConfig.MIN_DOUBLE_CLICK_MS,
                    max = GlobalConfig.MAX_DOUBLE_CLICK_MS,
                    value = cfg.global.doubleClickMs,
                    default = GlobalConfig.DEFAULT_DOUBLE_CLICK_MS,
                    enabled = rowsEnabled,
                ) { v ->
                    write(
                        configState.config.value.copy(
                            global =
                                configState.config.value.global
                                    .copy(doubleClickMs = v),
                        ),
                        "double_click_ms=$v",
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
                // 「双击为无操作时不用等那一截」—— 开关的语义与判定侧见
                // `GlobalConfig.instantSingle`（判定在 `inject/input/GestureDetector.onKeyUp`）。
                SwitchRow(
                    title = I18n.get(Strings.SETTING_TRIGGER_IMMEDIATELY, "立即触发单击"),
                    // 不说「双击窗口」：那是判定侧的内部说法；用户只要知道「什么时候不用等」
                    summary = I18n.get(Strings.BINDING_INSTANT_SINGLE, "双击未绑动作时立即触发单击"),
                    // 与上面那行同一个图标位 —— 同一张卡里两行都有图标时标题才对齐
                    icon = PageIcons.InstantTrigger,
                    checked = cfg.global.instantSingle,
                    position = cardListPositionOf(1, SINGLE_SETTING_ROWS),
                    enabled = rowsEnabled,
                ) {
                    tick()
                    setInstantSingle(it)
                }
            }
            Spacer(Modifier.height(GRID_ROW_GAP))
        }

        //
        // （长按多久算长按、单击抬手后等多久看有没有第二下），摆在首页「按键行为」卡里、
        // 与「振动反馈」并列，会被当成对三个手势都生效的全局项。
        //
        // 它现在是 [LongRepeatCard] 的首行（与「长按连续触发 / 触发间隔」同一张卡）。
        // 双击档下**没有**这张卡：双击窗口就是「单击延迟」那一个值，两边都放会是同一个
        // 配置项的第二个入口（改哪边都一样），所以只留在单击档里（见上）。
        if (g == Gesture.LONG) {
            SmallTitle(I18n.get(Strings.BINDING_SETTINGS_SECTION, "按键设置"))
            LongRepeatCard(
                blockedReason =
                    when {
                        // 主开关为关 ⇒ 整页禁用，这张卡也跟着置灰
                        !enabled -> {
                            I18n.get(Strings.STATUS_DISABLED, "模块未启用")
                        }

                        isSerialCommand(configState.commands.value, current.token) -> {
                            I18n.get(Strings.BINDING_SERIAL_UNSUPPORTED_REPEAT, "多条命令不支持连续触发")
                        }

                        else -> {
                            null
                        }
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
            val localizedGroupTitle = ActionCatalog.groupTitleLabel(groupTitle)
            SmallTitle(localizedGroupTitle)

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
                            // 会弹菜单的那几格各自 remember 一份触点锚点（键是 token）——
                            // `PreciseClickState` 里除触点坐标外还存着「锚点自己的布局位置」，
                            // 两格共用一份就会被算成「最后布局的那一格」，浮层弹到别人身上。
                            //  写在 `key(spec.token)` 里、而不是在页面这一层一格格手写：新增一个入口格
                            // 不用再补一份锚点、也不用改一张 `when` 表（判据就是"这格点了弹不弹菜单"）。
                            //  `Modifier.weight` 必须在 `key` **外面**取 —— 它是 `RowScope` 的扩展，
                            // 而 `key` 的 lambda 已经不在那个作用域里了。
                            val cellModifier = Modifier.weight(1f)
                            key(spec.token) {
                                val tileAnchor = rememberPreciseClickState()
                                // 登记给确认键那条路用（见 [tileAnchors]）—— 写的是普通 map、不是 state，
                                // 组合期写它不触发重组。
                                tileAnchors[spec.token] = tileAnchor
                                //  入口格（点了弹二级菜单的那几格）：`modes` 非空。这一格绑在哪一档也
                                //  从这里算一次 —— **小字与小字前面那枚图标共用它**，同源就不会各说各的。
                                val modes = ActionCatalog.modeMenu(spec.token)
                                val popsMenu = modes != null
                                val boundMode = modes?.firstOrNull { ActionCatalog.isBound(it, current) }
                                ActionTile(
                                    spec = spec,
                                    //  入口格的行首**随当前档位变**（主屏幕 / 静音 / 上一首 …）；
                                    //  一档都没绑时退回这一格自己的大类字形（媒体控制 → 音符、
                                    //  响铃状态 → 铃铛、系统导航 → 指北针…）。
                                    icon = boundMode?.let { icons[it] } ?: icons[spec],
                                    selected = ActionCatalog.isSelected(spec, current),
                                    focused = index == focusedIndex,
                                    focusRequester = focusers[index],
                                    //  锚点**每格一份**：`PreciseClickState` 里存坐标的只有一格，
                                    // 每个挂它的节点都会往里写自己那点 ⇒ 一整片格子共用一份的话，
                                    // 锚点会被算成「最后布局的那一格」。取值格点了不弹浮层，
                                    // 所以只有会弹菜单的格子才挂（`popsMenu`），其余不白记坐标。
                                    modifier =
                                        if (popsMenu) {
                                            cellModifier.preciseClickAnchor(tileAnchor)
                                        } else {
                                            cellModifier
                                        },
                                    //  可用性与 [pick] 同一判据（主开关 + 链路就绪 + 清单命中）：
                                    //  少判一道就会「亮着、点了被拦」—— 点了只弹一句提示，不如先灰着。
                                    //  按键设置那张卡**故意**不同（它只吃主开关 + 清单，见 `rowsEnabled`）。
                                    //  再叠一道「这一条在本机有没有落点」（[ActionCatalog.isLocked]）：
                                    //  本机 ROM 不在落点档位表里、而本包又不猜时，那几条按下去不会有事发生
                                    //  ⇒ 置灰、不吃点击（执行侧会跳过它们）。
                                    enabled = enabled && linkReady && deviceSupported && !ActionCatalog.isLocked(spec),
                                    // 「打开应用」格与入口格都要补一行小字：前者是绑的应用名，
                                    // 入口格那格只写组名，不点开菜单根本看不出绑的是哪一档
                                    subtitle =
                                        when {
                                            spec.token == ActionCatalog.APP_TILE -> boundAppLabel
                                            else -> boundMode?.label
                                        },
                                    // 「打开应用」格绑成小窗打开时，标题右边补一枚「小窗起」（见上面 [appSmallWindow]）。
                                    //  注意：必须连 spec 一起判 —— 光看 `appSmallWindow` 会给**整片**格子都画上。
                                    titleMarker =
                                        if (spec.token == ActionCatalog.APP_TILE && appSmallWindow) {
                                            PageIcons.TileSmallWindow
                                        } else {
                                            null
                                        },
                                    // 入口格虽然是**入口**（点了进下一层），但**不画行尾箭头** ——
                                    // 走 [ActionTile] 的默认值 false，别顺手补回来。
                                    onClick = { activate(spec, tileAnchor) },
                                )
                            }
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
        // 「自定义命令」这一段**始终在**：没有命令时给一行「去创建自定义命令」（整段不出现的话，
        // 网格里就看不出这块地方是干什么的）。样式与网格同一套纵向节奏。
        Spacer(Modifier.height(GRID_ROW_GAP))
        SmallTitle(I18n.get(Strings.COMMAND_SAVED_COMMANDS, "自定义命令"))
        Column(
            Modifier.padding(horizontal = PAGE_MARGIN),
            verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
        ) {
            if (specs.size <= builtInCount) {
                // 还没保存过任何命令 —— 整行可点，进「自定义命令」页新增一条（与右下角那颗 FAB 同一个去处）。
                Card(Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = I18n.get(Strings.BINDING_CREATE_CUSTOM_COMMAND, "自定义命令"),
                        summary = I18n.get(Strings.BINDING_CREATE_CUSTOM_COMMAND_DESC, "一条命令或多个动作"),
                        startAction = { Icon(PageIcons.CommandAdd, null) },
                        // 行尾箭头：「这一行后面还有一页」（与首页「按键绑定」那行同尺寸 24dp、
                        // 同两档取色 —— 它不是弹浮层的入口格，所以不画那颗选择器字形）。
                        endActions = {
                            Icon(
                                PageIcons.EnterArrow,
                                contentDescription = null,
                                modifier = Modifier.size(ROW_END_ICON_SIZE),
                                tint =
                                    if (enabled) {
                                        COUITheme.colorScheme.onSurfaceVariantActions
                                    } else {
                                        COUITheme.colorScheme.disabledOnSecondaryVariant
                                    },
                            )
                        },
                        cardListPosition = cardListPositionOf(0, 1),
                        enabled = enabled,
                        onClick = {
                            if (enabled) {
                                tick()
                                onCreateCommand()
                            }
                        },
                    )
                }
            } else {
                // 焦点索引 `[builtInCount, totalCount)` —— 与 `specs` 下标一一对应
                (builtInCount until totalCount).chunked(COLUMNS).forEach { rowIndices ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                    ) {
                        rowIndices.forEach { index ->
                            val spec = specs.getOrNull(index) ?: return@forEach
                            // 每格按 `key(spec.token)` 认身份 —— 与上面内置那一组同一写法。
                            //  这一段是按**位次**排出来的（`chunked(COLUMNS)` 之后按下标取），
                            //  而自定义命令会增删、会改名重排：不按 token 认身份，格子里的状态
                            //  （焦点圈、进出场）就跟着位次挪到下一条命令身上。
                            key(spec.token) {
                                // 这条命令本身（`cmd:<名字>` 反查）—— 小字与「进编辑」两处都要用
                                val cmd = commandsByToken[spec.token]
                                val selected = ActionCatalog.isSelected(spec, current)
                                // 「进这条命令的编辑页」—— 长按、以及**已选中时再点一次**，共用这一个入口。
                                //
                                //  走 `draft.load` 而**不是** FAB 那条 `navigateToCommands` ——
                                // 后者会先 `draft.reset()`（那是「新增」的入口，见它的注释），
                                // 这里要的正是「把这条命令灌进表单」，
                                // 与「自定义命令」页里轻点已保存命令同一个入口。
                                val openEditor = {
                                    if (cmd != null) {
                                        tick()
                                        XLog.w("手势页打开自定义命令编辑：${cmd.name}")
                                        // 这里直接 `load`、不走 [loadDraft] 那个「先问一句」：
                                        //  草稿脏了必然还在「自定义命令」页 —— 退出那一页时 [goBack] 已经问过
                                        //  并 `draft.reset()`；不脏地退出来则草稿本来就等于「没在编辑」
                                        //  （而且每次走 [navigateToCommands] 进来都会 reset 一次）。
                                        //  走 `loadDraft` 反而更糟：它会把确认窗挂起，而那个窗画在
                                        //  「自定义命令」页上、此刻根本没组合 ⇒ 按下去什么都不发生。
                                        commandDraft.draft.load(cmd)
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
                                    // 与上面内置网格同一判据（见那处注释）。
                                    enabled = enabled && linkReady && deviceSupported,
                                    // 标题下面那行小字 = **命令间隔时长**（`stepDelayMs`）。
                                    // 只有多条命令有这个值 —— 单条只有一步、用不到它
                                    // （见 `core/model/Command.stepDelayMs`），所以那种格子不画。
                                    // 文案走 [durationText]：1000ms 以上写成秒。
                                    subtitle =
                                        cmd
                                            ?.takeIf { it.kind == CommandKind.SERIAL }
                                            ?.let { durationText(it.stepDelayMs) },
                                    //  行尾那支箭头现在只给这一组格子 —— 内置那几个入口格
                                    // （打开应用 / 响铃状态 / 媒体控制）不画箭头（见上面内置网格那段）。
                                    enterArrow = true,
                                    // 长按与「已选中时再点一次」同一个去处（上面那个 `openEditor`）。
                                    // 长按之后那一下抬手由库的 `combinedClickable` 吞掉（不会再触发点击）。
                                    onLongClick = openEditor,
                                    // **已经选中的再点一次 = 「我要改它」**，不是「重新绑一遍」，
                                    // 所以走编辑；没选中时才是「选中绑定」。
                                    onClick = { if (selected) openEditor() else select(spec) },
                                )
                            }
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
        // 不能少这一档：少了的话这一页滚到底时最后一张卡直接贴着屏幕边缘，而「自定义命令」页底下有 32dp）。
        //
        // 内容的底部 padding**：这里与「自定义命令」页一样
        // 只留 [PAGE_BOTTOM_PADDING]（32dp）。
        //  代价：FAB（下边距 64dp、高 56dp，占 64~120dp 那一带）滚到底时会压住
        Spacer(Modifier.height(PAGE_BOTTOM_PADDING))
        //  再补 [GRID_GAP]：这 12dp 若挂在**滚动视口之外**
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
            // 锚点就是**开着的那一格**自己那一份（点它时随菜单一起上报，见 [OpenTileMenu]）——
            // 这里不再有「哪一格用哪一支」的表：那种表漏一个入口格，菜单就弹到别人身上
            // （小窗、通知与控制中心，两次都是这么漏的）。
            //  关掉之后 `tileMenu` 是 null，而退场动画期间真正生效的是 [MenuPopup] 内部保持住的
            // 上一次那份锚点（见那边的注释），所以这里给个没记过触点的占位即可。
            preciseClick = tileMenu?.anchor ?: noAnchor,
            modes = tileMenu?.let { ActionCatalog.modeMenu(it.spec.token) }.orEmpty(),
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
 *  行序（调整）：**连续触发在最上面**、触发间隔紧跟着它（只在开关打开时出现），
 * 「长按延迟」落到最后一行 —— 前两行是一件事（按住不放要不要一直重来、隔多久来一次），
 * 而「多久算长按」是另一件事。
 *
 * @param blockedReason 非 null ⇒ **整张卡**置灰、不吃点击（连续触发那行与长按延迟那行），
 * @param masterEnabled 首页那个「按键绑定」主开关 —— 与 [blockedReason] 一起决定「长按延迟」那行
 */
@Composable
internal fun SettingsPage.LongRepeatCard(
    blockedReason: String?,
    masterEnabled: Boolean,
) {
    val blocked = blockedReason != null
    val cfg = configState.config.value
    val key = cfg.key(KeyId.PLUS)
    // 三行：长按连续触发 +（开关打开时的）触发间隔 + 长按延迟
    val rows = if (key.longRepeat) 3 else 2
    val tick = rememberTick()

    Card(Modifier.fillMaxWidth().padding(horizontal = PAGE_MARGIN)) {
        // 置灰的原因写在副标题里（两种原因：主开关关了 / 选中的是多条命令）；
        // 行首图标与行尾开关的置灰都在 [SwitchRow] 里统一处理。
        SwitchRow(
            title = I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_TITLE, "长按连续触发"),
            summary = blockedReason ?: I18n.get(Strings.BINDING_LONG_PRESS_REPEAT_DESC, "按住不放时按固定间隔重复执行"),
            icon = PageIcons.LongRepeat,
            checked = key.longRepeat,
            position = cardListPositionOf(0, rows),
            enabled = !blocked,
        ) {
            tick()
            setLongRepeat(it)
        }
        // 「触发间隔」只在**开关打开时**才存在（关着时没有可调的间隔）。
        //  它是上面那一行的参数，所以紧跟着上面那一行，而不是掉到「长按延迟」后面去；
        // 连上面那条分隔线一起显隐（[AnimatedColumn]）—— 只藏行的话会留一条孤零零的线。
        AnimatedColumn(visible = key.longRepeat) {
            HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
            SliderRow(
                title = I18n.get(Strings.BINDING_REPEAT_INTERVAL_TITLE, "触发间隔"),
                icon = PageIcons.DelayRepeat,
                summary = I18n.get(Strings.BINDING_REPEAT_INTERVAL_DESC, "长按连续触发的重复间隔"),
                position = cardListPositionOf(1, rows),
                min = KeyConfig.MIN_REPEAT_INTERVAL_MS,
                max = KeyConfig.MAX_REPEAT_INTERVAL_MS,
                value = key.longRepeatIntervalMs,
                default = KeyConfig.DEFAULT_REPEAT_INTERVAL_MS,
                enabled = !blocked,
            ) { v ->
                write(
                    configState.config.value.copy(
                        keys =
                            configState.config.value.keys +
                                (
                                    KeyId.PLUS to
                                        configState.config.value
                                            .key(KeyId.PLUS)
                                            .copy(longRepeatIntervalMs = v)
                                ),
                    ),
                    "long_repeat_interval_ms=$v",
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))
        // 「长按延迟」—— 多久算长按。
        //  它的 enabled 跟着**整张卡**走（主开关 + [blockedReason]）。这个参数对多条命令其实
        //  仍然生效（判定长按的时机与绑的是什么动作无关），但同一张卡里上面那行已经置灰、
        //  它独自亮着，会让人以为「这行还能改 ⇒ 多条命令也许还能连续触发」（用户报的）。
        //
        //  说明里点一句「比它短算单击」：按不出来时用户第一个怀疑的就是这个数。到这一句为止 ——
        //  写全「这一档已短于人一次自然按压」那种话太长，会把行撑成两行、与旁边几行的说明也不齐。
        SliderRow(
            title = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_TITLE, "长按延迟"),
            icon = PageIcons.DelayHold,
            summary = I18n.get(Strings.BINDING_LONG_PRESS_DELAY_DESC, "多久算长按，比它短算单击"),
            position = cardListPositionOf(if (key.longRepeat) 2 else 1, rows),
            min = KeyConfig.MIN_LONG_PRESS_MS,
            max = KeyConfig.MAX_LONG_PRESS_MS,
            value = key.longPressMs,
            default = KeyConfig.DEFAULT_LONG_PRESS_MS,
            enabled = masterEnabled && !blocked,
        ) { v ->
            write(
                configState.config.value.copy(
                    keys =
                        configState.config.value.keys +
                            (
                                KeyId.PLUS to
                                    configState.config.value
                                        .key(KeyId.PLUS)
                                        .copy(longPressMs = v)
                            ),
                ),
                "long_press_ms=$v",
            )
        }
    }
}
