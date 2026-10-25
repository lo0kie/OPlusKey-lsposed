package com.lookie.opluskey.ui.screen.binding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ActionCatalog
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.linkReady
import com.lookie.opluskey.ui.model.COLUMNS
import com.lookie.opluskey.ui.tickNow
import io.github.suqi8.coui.kmp.basic.PreciseClickState

/**
 * 「按键绑定」页那一张**网格**的状态 —— 焦点账本、每格的触点锚点、开着的那层入口菜单。
 *
 *  为什么单列一个对象：这一页的体量来自**焦点**，不是布局。焦点格索引、每格一个
 *  `FocusRequester`、方向键怎么挪、确认键走哪条派发路、翻页时怎么把浮层收掉 —— 这些互相咬合，
 *  以前和「网格怎么排」写在一个 666 行的函数里，改任何一处都得先把整段读一遍。
 *
 *  注意：**只放「这一页这一档手势」的东西**。`current`（当前绑定）/ `icons` / `enabled` 都不进来：
 *  它们每次重组都可能变，存进来就会过期；各段自己在组合期从页面状态里取。
 */
internal class BindingGridState(
    /** 这张网格往哪儿写（一颗键的一档 / 一档组合键的一档）—— `pick` 要它。 */
    val target: BindingTarget,
    /** 这一页的动作清单（内置 + 自定义，见 `ActionCatalog.forGesture`）。 */
    val specs: List<ActionCatalog.Spec>,
    /** 入口格被点开时进「选择应用」页（为**这一档**挑，写回也落在 [target] 上）。 */
    val onOpenAppPicker: (BindingTarget) -> Unit,
    /** 入口格被点开时进「控制中心磁贴」页（为**这一档手势**挑）。 */
    initialFocus: Int,
) {
    /** 这一张网格是哪一档手势 —— 折叠清单 / 入口格菜单都按它判（见 [BindingTarget.gesture]）。 */
    val g: Gesture get() = target.gesture

    /** 焦点格总数 —— 与 [specs] 下标一一对应（见 `BindingList` 里「扁平焦点索引的账」）。 */
    val totalCount: Int get() = specs.size

    /**
     * 内置格子的个数 —— 下标 `[0, builtInCount)` 是内置那一组，之后是自定义命令。
     *
     *  按**这一档手势**算（`ActionCatalog.builtInCount`），不是按 `ALL.size`：清单按手势剔过条目
     *  （「灵感语音」只在长按），用 `ALL.size` 会让切点后移一位 —— 第一条自定义命令就跑到
     *  「快捷功能」那一组里去了。切分与清单必须同源。
     */
    val builtInCount: Int = ActionCatalog.builtInCount()

    /**
     * 格子在界面上的**分行表**（每行是若干个扁平下标）—— 方向键按它挪，不按下标加减。
     *
     *  排布是**按组分行**的：内置那一组组各自 `chunked(COLUMNS)`（末行不满就补空位），自定义命令
     *  再从 `builtInCount` 接着排（见 `BuiltInGrid` / `CustomCommandsGrid`）。所以「下一行」不等于
     *  `index + COLUMNS` —— 跨过组边界时那个式子会落到别的组里（真机表现：按一次下，焦点跳两行、
     *  或者干脆跳到左边去）。这里与界面同源：同一份 `groupsFor` + 同一个 [COLUMNS]。
     */
    val rows: List<List<Int>> =
        buildList {
            var base = 0
            for ((_, group) in ActionCatalog.groupsFor()) {
                for (row in (base until base + group.size).chunked(COLUMNS)) add(row)
                base += group.size
            }
            for (row in (builtInCount until specs.size).chunked(COLUMNS)) add(row)
        }

    /**
     * 每个格子一个 `FocusRequester`。
     *
     *  与 [specs] 同生共死（由创建点按 `specs` 记忆）：重组不会重建这一串 requester，
     * 重建会把「已经请求到的焦点」连同节点一起丢掉。
     */
    val focusers: List<FocusRequester> = List(specs.size) { FocusRequester() }

    /**
     * 当前高亮的格子（方向键据此算下一个）。
     *
     *  `onFocusChanged` 报的是**子树里**那个焦点目标的状态，而焦点目标是 `ActionTile` 里自己加的
     *  `focusTarget()` —— 与 `onFocusChanged` 挂在同一层，它看不见 ⇒ `requestFocus()` 明明返回
     *  true，回调却永远是 `focused=false`。与其去跟修饰符顺序较劲，不如「**谁请求焦点谁记账**」：
     *  焦点只由两处移动（进场重定位 / 方向键）。**点击不挪** —— 焦点圈只跟随选中态，
     *  否则「打开应用」这种入口格会把圈骗过去、退回来又跳回选中项。
     */
    var focusedIndex by mutableIntStateOf(initialFocus)

    /**
     * 每一格自己那份触点锚点，按 token 索引 —— **只给确认键那条路用**。
     *
     *  格子仍在自己那个 `key(spec.token)` 里 `remember` 一份（网格那段注释：不再有「哪一格用哪一支」
     *  的表，加一个入口格不用改这里）；这张表只是把**同一份**登记下来，因为确认键不在格子的组合
     *  作用域里、拿不到它。键是 token，所以新增入口格同样不用改这里。
     */
    val tileAnchors = mutableMapOf<String, PreciseClickState>()

    /**
     * 入口格点开的那一层菜单开着哪一格（`null` = 没开）—— 响铃状态是「循环 / 响铃 / 振动 /
     * 静音」，媒体控制是「播放 / 暂停 / 上一首 / 下一首」（行本身见 `ActionCatalog.MODE_MENUS`）。
     *
     *  状态就放在这一页（每个手势一份）—— 换了 tab 就是另一份，菜单跟着消失。
     *  它带着**那一格自己那份触点锚点**（见 [OpenTileMenu]）：锚点由格子自己 `remember`、
     *  点的时候随这一格一起上报，不再在页面这一层一格格手写 —— 那种写法每加一个入口格就得补
     *  一份锚点、还要在一张 `when` 表里加一行，漏一个菜单就弹到别人身上。
     */
    var tileMenu by mutableStateOf<OpenTileMenu?>(null)

    /**
     * 关掉菜单之后、退场动画期间用的**占位锚点**（它没记过触点，不会真的参与定位）。
     *
     * 那段时间真正生效的是 `MenuPopup` 内部保持住的上一次那份内容与锚点。
     */
    val noAnchor = PreciseClickState()
}

/**
 * 取出（或建出）这一页的网格状态。
 *
 *  **只在 `specs` 变的时候重建** —— 重建会连同 `focusers` / `focusedIndex` 一起重置，
 *  这正是 `remember(specs)` 原来的语义（命令库变了，格子数变了，焦点必须重新落到选中项上）。
 *  不能把 `enabled` / `current` 之类也放进 key：主开关一拨就重建 requester，焦点会当场丢。
 */
@Composable
internal fun SettingsPage.rememberBindingGridState(
    target: BindingTarget,
    onOpenAppPicker: (BindingTarget) -> Unit,
): BindingGridState {
    val specs =
        remember(target.gesture, configState.commands.value) {
            ActionCatalog.forGesture(configState.commands.value)
        }
    val current = target.actionIn(configState.config.value)
    val selectedIndex = specs.indexOfFirst { ActionCatalog.isSelected(it, current) }
    //  **`target` 也要进 key**：单键那一页切到另一颗键时 `specs` 一点都不变（清单只跟命令库
    //  与手势有关），只按 specs 记忆会把上一颗键的 `target` 一直用下去 —— 在 A 上选的动作写进了 B。
    return remember(specs, target) {
        BindingGridState(
            target = target,
            specs = specs,
            onOpenAppPicker = onOpenAppPicker,
            initialFocus = if (selectedIndex in specs.indices) selectedIndex else 0,
        )
    }
}

/**
 * 「焦点圈跟着选中项走」—— 进场把圈放到当前已选中的那一格，之后**选中项一变就收回来**。
 *
 *  为什么必须是共用件：「按键绑定」页与「组合按键」编辑页用的是**同一张网格**，圈怎么动只能有一套。
 *  组合键那一页一开始没接这段，真机上表现为「点了别的动作，边框还留在原来那一格，退出去重进才对」——
 *  因为 `focusedIndex` 只在 [BindingGridState] 建出来的那一刻取过一次 `initialFocus`。
 *
 * ## 几条硬约束（都是真机上踩出来的）
 *
 *  · 请求焦点**只能在 `LaunchedEffect` 里**：Compose 明确禁止在组合期间请求。
 *  · **只有当前页抢焦点**：pager 的相邻页同样在组合里，几页各请求一次的话最后请求到的拿走焦点。
 *  · 浮窗（弹窗 / 菜单 / 提示）开着时**一律不抢**：浮窗里的输入框刚拿到焦点，这里再请求一次就会
 *    把它抢走（真机反馈「添加按键 / 重命名的输入框聚焦后又自动失焦」）。浮窗关掉那一刻 key 变回来，
 *    圈自然落回选中项。
 *  · 先请求**页面 View** 的 Android 焦点，再请求 Compose 内部焦点：按键事件按 Android 焦点派发，
 *    只请求 Compose 焦点的话，在「页面 View 从未拿过焦点」时按键仍然进不来。
 *
 * @param active 这一页是不是 pager 的当前页（非当前页不抢焦点）
 * @param subject 日志主语（`按键绑定` / `组合按键`），排查时能看出是哪一页在抢
 */
@Composable
internal fun SettingsPage.BindingGridFocus(
    st: BindingGridState,
    active: Boolean,
    subject: String,
) {
    val current = st.target.actionIn(configState.config.value)
    // 当前选中的那一项（配置里是清单外的写法时为 -1 ⇒ 焦点退回第一项）
    val selectedIndex = st.specs.indexOfFirst { ActionCatalog.isSelected(it, current) }
    val overlayOpen = overlays.hasOpen()
    LaunchedEffect(st.specs, selectedIndex, active, overlayOpen) {
        if (!active || overlayOpen) return@LaunchedEffect
        val seat = selectedIndex.coerceAtLeast(0)
        if (seat != st.focusedIndex) st.focusedIndex = seat
        val viewFocus = viewTree.rootView?.requestFocus() ?: false
        val tileFocus = st.focusers.getOrNull(st.focusedIndex)?.requestFocus() ?: false
        XLog.w("$subject：焦点落在 index=${st.focusedIndex} viewFocus=$viewFocus tileFocus=$tileFocus")
    }
}

/**
 * 选中某一项 —— 点击与确认键**共用这一个入口**，两条路的手感一致。
 * 先振动再写配置：`pick` 读的是 `config.value`，顺序不影响结果。
 *
 *  **这里不挪焦点圈**：圈只跟随选中态（`selectedIndex` 变了由重定位 effect 挪过来）。
 *  「打开应用」是入口格 —— 点它不改变绑定，要是这里顺手把圈挪过去，从「选择应用」退回来时
 *  圈又会跳回选中项，看着像「从打开应用回到了无操作」。
 */
internal fun SettingsPage.bindingSelect(
    st: BindingGridState,
    spec: ActionCatalog.Spec,
    enabled: Boolean,
) {
    // 主开关为关时整页禁用 —— 这里再兜一道（格子的 `BasicComponent(enabled=false)` 已经不挂
    // `clickable` 了，但确认键那条路是自己派发的，得自己拦）
    if (!enabled) return
    tickNow()
    //  **圈就在这一下挪过去**，不等「选中项变了再由 effect 收回来」：
    //  真机上那条 effect 明明条件都成立，圈却纹丝不动（点了别的动作，边框还留在原来那一格，
    //  退出去重进才对 —— `focusedIndex` 从头到尾没被改过）。与其去赌某条异步路径，
    //  不如在**点下去的那一刻**直接把它写掉：这是事件回调，写 state 完全合法，下一帧就重画。
    st.focusOn(spec.token)
    pick(spec, st.target)
}

/**
 * 把焦点圈挪到 [token] 那一格上 —— 找不到就什么都不做。
 *
 *  「找不到」是正常情况：入口格菜单里的取值（`nav:home` 这种）不在 `specs` 里，
 *  那时候圈该留在**被点开的那一格入口格**上，不能甩到第一格去。
 */
internal fun BindingGridState.focusOn(token: String) {
    val index = specs.indexOfFirst { it.token == token }
    if (index >= 0) focusedIndex = index
}

/**
 * 点一下某一格 —— **点击与确认键共用这一个入口**。
 *
 *  注意：入口格必须在这里分派（「打开应用」进挑选页、其余入口格弹菜单），**不能交给
 *  [bindingSelect]**：`app:` / `nav:` / `key:` / `panel:` / `zoom:` 这些都是**标记** token，
 *  `Action.parse` 认不出 —— `nav:` / `key:` / `zoom:` 落 `else -> Shell(...)`、`app:` 落
 *  `App("")`、`panel:` 落 `Panel("")`，交过去就是把垃圾写进配置再广播出去。
 *
 * @param anchor 这一格自己那份触点锚点（弹菜单要用它定位）；确认键那条路从 `st.tileAnchors` 取
 */
internal fun SettingsPage.bindingActivate(
    st: BindingGridState,
    spec: ActionCatalog.Spec,
    anchor: PreciseClickState,
    enabled: Boolean,
) {
    if (spec.token == ActionCatalog.APP_TILE) {
        if (enabled) {
            tickNow()
            //  不挪焦点圈 —— 与 [bindingSelect] 同一条规矩：圈只跟随选中态。
            st.onOpenAppPicker(st.target)
        }
        return
    }
    if (ActionCatalog.isEntryTile(spec)) {
        // 入口格（系统导航 / 系统按键 / 媒体控制 / 响铃状态 / 前台转小窗）：
        // 只弹二级菜单供选择，不直接改绑第一项；只有在弹出的菜单里真正点了某一项才改绑。
        if (enabled) {
            tickNow()
            st.tileMenu = OpenTileMenu(spec, anchor)
        }
        return
    }
    bindingSelect(st, spec, enabled)
}

/**
 * 方向键 / 确认键的接管 —— 预览阶段就吃掉（`onPreviewKeyEvent`），免得被里层的滚动容器先消费。
 *
 *  三条前置判据（任一不满足就整个放行）：主开关关着、非当前页、入口浮层开着。
 *  确认键另有一条：**链路未就绪时不派发** —— 与格子的 `enabled`（`enabled && linkReady`）同一把尺子。
 */
@Composable
internal fun SettingsPage.rememberBindingKeyHandler(
    st: BindingGridState,
    enabled: Boolean,
    active: Boolean,
): (KeyEvent) -> Boolean =
    onKey@{ e ->
        // 主开关为关：方向键 / 确认键一概不接管（「禁用所有控件」）
        if (!enabled) return@onKey false
        // 非当前页不吃键：pager 的相邻页在组合里，不拦的话按键会被它抢先消费
        if (!active) return@onKey false
        // 浮层开着时把键交给浮层（浮层自己处理上下 + 确认）
        if (st.tileMenu != null) return@onKey false
        val intent = KeyIntent.of(e.key) ?: return@onKey false

        if (e.type == KeyEventType.KeyUp) {
            // 确认键在抬起时派发（与平台「按下不算、抬起才算点」一致）
            if (intent == KeyIntent.CONFIRM) bindingConfirm(st, enabled)
            return@onKey true
        }
        // 按下那一半的确认键也吃掉（否则里层的滚动容器会当成「滚动 / 激活」再处理一遍）
        if (intent == KeyIntent.CONFIRM) return@onKey true

        val target = st.moveFocus(st.focusedIndex, intent)
        st.focusedIndex = target
        st.focusers.getOrNull(target)?.requestFocus()
        true
    }

/**
 * 确认键那一下 —— **与点击同一个入口**（[bindingActivate]）。
 *
 *  入口格必须由 `activate` 分派（进挑选页 / 弹菜单）：直接走 `bindingSelect` 会把
 *  `app:` / `nav:` / `key:` / `panel:` / `zoom:` 这些标记 token 当取值写进配置。
 *  锚点取那一格登记的那份，拿不到就退回占位。
 *
 *  判据与格子**同一把尺子**（`enabled && linkReady`，见 `BindingGrids` 里 `ActionTile` 的
 *  `enabled`）：链路没就绪时格子是灰的、点了不动，而这条路是自己派发的 —— 少判 `linkReady`
 *  就会出现「灰着的格子，按确认键却进了挑选页 / 弹出了菜单」，进去之后每一步都被 `pick` 拦下。
 */
private fun SettingsPage.bindingConfirm(
    st: BindingGridState,
    enabled: Boolean,
) {
    if (!linkReady) return
    // 焦点格索引 == `specs` 下标（跳转按钮撤掉之后一一对应）
    val spec = st.specs.getOrNull(st.focusedIndex) ?: return
    bindingActivate(st, spec, st.tileAnchors[spec.token] ?: st.noAnchor, enabled)
}

/**
 * 从 [from] 往 [intent] 那个方向挪一格 —— **按界面上真实的排布挪**（[BindingGridState.rows]）。
 *
 *  上下换行、左右换列；落点落在更短的那一行之外时收到该行最后一格；到头就停在原地
 *  （「从最后一格继续按下」不动，与原来的钳制同一个手感）。
 */
private fun BindingGridState.moveFocus(
    from: Int,
    intent: KeyIntent,
): Int {
    val row = rows.indexOfFirst { from in it }
    if (row < 0) return from
    val col = rows[row].indexOf(from)
    val line = rows[(row + intent.rowStep).coerceIn(0, rows.lastIndex)]
    return line[(col + intent.colStep).coerceIn(0, line.lastIndex)]
}

/**
 * 这一下按键在我们这套网格里是什么意思 —— `null` = 与我们无关，整个放行。
 *
 *  单列一个枚举而不在处理器里写五个布尔：那五个布尔会同时出现在「是不是方向键」与
 *  「往哪个方向挪」两处判断里，处理器一屏写不完、复杂度也一路顶到阈值。
 */
private enum class KeyIntent(
    /** 换几行（上下），左右是 0。 */
    val rowStep: Int,
    /** 行内挪几格（左右），上下是 0 —— 换行时保持同一列。 */
    val colStep: Int,
) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1),
    CONFIRM(0, 0),
    ;

    companion object {
        fun of(key: Key): KeyIntent? =
            when (key) {
                Key.DirectionUp -> UP
                Key.DirectionDown -> DOWN
                Key.DirectionLeft -> LEFT
                Key.DirectionRight -> RIGHT
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> CONFIRM
                else -> null
            }
    }
}
