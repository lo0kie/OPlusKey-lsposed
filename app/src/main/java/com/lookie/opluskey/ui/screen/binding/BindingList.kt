package com.lookie.opluskey.ui.screen.binding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Dp
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.PageScrollColumn
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.currentHeaderHeight
import com.lookie.opluskey.ui.state.KeyWatchState
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

// 「按键绑定」页 —— 一页 = 一个手势档。
//
// 这一页原来是一个 666 行的函数（派生值、焦点账本、方向键派发、两张网格、设置卡、入口菜单全在里面）。
// 现在按「状态 / 格子 / 设置卡 / 纵向顺序」分到五个文件：
//   · 本文件               —— 参数、三条 effect、滚动骨架；
//   · `BindingGridState.kt` —— 焦点账本 + 点击 / 确认键的派发入口 + 方向键处理器；
//   · `BindingGrids.kt`     —— 内置网格 / 自定义命令网格 / 入口菜单；
//   · `BindingSettings.kt` / `InstantSingleCard.kt` / `LongRepeatCard.kt` —— 三档各一张设置卡；
//   · `BindingSections.kt`  —— 纵向顺序（哪一段之间该给间距）。

/** 选择器入口回调（应用 / 磁贴）—— 带上「哪一格」，组合键那一页也用这一份。 */
internal data class BindingPickers(
    val onOpenAppPicker: (BindingTarget) -> Unit,
)

/**
 * 一页手势绑定。
 *
 * @param g 这一页是哪一档手势。
 * @param page 这一页此刻的状态（主开关开着吗、是不是 pager 的当前页）。
 *
 *  pager 会把相邻页一起留在组合里（滑动转场要它），于是三页的 `LaunchedEffect` /
 *  `onPreviewKeyEvent` / 浮层状态全都活着。三样东西必须由它把住：
 *   · 进场请求焦点 —— 否则三页同时 `requestFocus()`，最后请求到的说了算，
 *     真机表现是「翻到第二页，方向键却在动第一页的圈」；
 *   · 入口浮层 —— 上一页开着菜单时翻页，浮层会跟着这一页一起活到下一帧，而它的锚点属于上一页那一格；
 *   · 方向键 / 确认键 —— 非当前页不能吃键，否则按键被它抢先消费掉。
 *
 * @param pickers 选择器入口回调（打开应用 / 控制中心磁贴）。
 * @param onCreateCommand 点「已保存的命令」那段里那行「去创建自定义命令」时进「自定义命令」页。
 * @param scroll 本页的滚动骨架（见 [BindingScroll]）。
 */
@Composable
internal fun SettingsPage.BindingList(
    g: Gesture,
    page: BindingPageState,
    pickers: BindingPickers,
    onCreateCommand: () -> Unit,
    scroll: BindingScroll,
) {
    val st =
        rememberBindingGridState(
            BindingTarget.Key(KeyWatchState.editing(configState.config.value), g),
            pickers.onOpenAppPicker,
        )
    //  焦点圈跟着选中项走（进场 / 改了绑定都收回来）——
    //  与「组合按键」编辑页共用同一份实现，两页的圈只能有一套行为（见 [BindingGridFocus]）。
    BindingGridFocus(st, page.active, "按键绑定")

    // 翻走这一页时把入口浮层收掉：它是**每个手势页各一份**的状态，而 pager 会把上一页留在
    // 组合里 —— 浮层于是跟着那一页活过了翻页，锚点还指着上一页那一格（弹到屏幕外 / 弹在别处）。
    LaunchedEffect(page.active) {
        if (!page.active) {
            st.tileMenu = null
            // 「自定义变量」那两层抽屉不在这一页了也得收：它没有锚点，翻回来时会**自己弹出来**。
        }
    }

    // 滚动时把入口浮层收掉。锚点是**按压那一点**换算出来的窗口坐标：行一滚走，那一格就不在
    // 锚点处了 —— 菜单留在原地会指着一格已经滚走的内容。库那层「点外面关」只认 tap、拖动不算。
    LaunchedEffect(scroll.state) {
        snapshotFlow { scroll.state.value }.collect { st.tileMenu = null }
    }

    val onKey = rememberBindingKeyHandler(st, page.enabled, page.active)

    //  滚动状态用**外面递进来的**那份（三个手势页共用一份）—— 分段控件在 pager 之外，
    //  要靠它算自己该滑到哪儿；预览阶段的方向键接管挂在链尾（见 `rememberBindingKeyHandler`）。
    //  **不用 `Arrangement.spacedBy`**：它会在**每一对相邻子项**之间插间距，于是标题与它下面
    //  那一段之间也被插了间距 —— 全页的规矩是「标题紧贴它下面的内容」，需要分隔的地方
    //  自己给一个显式 Spacer（见 `BindingGroups`）。
    PageScrollColumn(
        scrollBehavior = scroll.behavior,
        state = scroll.state,
        modifier = Modifier.onPreviewKeyEvent(onKey),
    ) {
        //  顶栏叠在内容上，顶部这一块要自己让出来（动态值）；后面再加 `tabBlock` ——
        // 那是**分段控件那条固定带**的高度，它**含自己那 8dp 下内边距**，
        // 所以「带子与内容之间」那一档已经在里面了，这里不再另加 `PAGE_TOP_PADDING`。
        Spacer(Modifier.height(currentHeaderHeight(scroll.behavior) + scroll.tabBlock))
        BindingGroups(g, page.enabled, st, onCreateCommand)
    }
}

/**
 * 一页手势绑定的滚动骨架 —— 三样东西在整条链路上是**一起动**的，收成一个参数免得每次往下传三份。
 *
 *  @param state 本页的滚动状态 —— **每个手势页各持一份**（由 `BindingScreen` 建）。
 *    注意：不能写「三个手势页共用同一个实例」：`ScrollState` 内部只有一个 `scrollableState`，
 *    而 pager 滑动时相邻两页同时在场、互相打架 ⇒「切回来就滚不动」。
 *  @param tabBlock 内容顶上要给分段控件留出的那一块（= 分段控件自己的高度，含它的上下内边距）。
 *    **必须与顶栏里那条分段控件一样高**：它让首屏第一行不被钉住的分段控件压住。
 */
internal class BindingScroll(
    val behavior: ScrollBehavior,
    val state: ScrollState,
    val tabBlock: Dp,
)

/**
 * 这一页此刻的状态 —— 主开关开着吗、是不是 pager 的**当前页**。
 *
 *  合成一个参数是因为它们总是一起决定「这一页能不能动」：主开关关 ⇒ 整页禁用；
 *  非当前页 ⇒ 不吃方向键、不收浮层、不抢焦点。分成两个参数时，每个下游都得判两次。
 */
internal class BindingPageState(
    val enabled: Boolean,
    val active: Boolean,
)
