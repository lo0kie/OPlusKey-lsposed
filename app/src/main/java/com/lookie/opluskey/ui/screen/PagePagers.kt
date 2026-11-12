package com.lookie.opluskey.ui.screen

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.ui.Screen
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.screen.command.COMMAND_PAGE_COUNT
import com.lookie.opluskey.ui.screen.command.sceneGesturePages
import com.lookie.opluskey.ui.state.binding.KeyWatchState

/**
 * 页面级的三个横向分页器 —— 一路传下去的那个包。
 *
 *  为什么打包：这些都建在 `PageContent` 那一层（页面级，不随页重建），而用它们的是
 *  两棵树 —— 内容（`PageBody`）与顶栏（`PageTopBar` 底下那条分段控件）。原来两个 pager
 *  是各自一个参数，加第三个（组合键的短按 / 长按）时每个签名都要多一格；
 *  打成一个包之后，将来再多一个分页器只改这里与创建点。
 *
 *  各管一页，**不共用**：
 *  · [gestures] —— 「按键绑定」页的几档手势（单击 / 双击 / 长按，打开多击才多出三击 / 四击，
 *    清单见 `GestureTrigger.slotsOn`）；
 *  · [commands] —— 「自定义命令」页的类型那一排；
 *  · [combos] —— 「组合键」编辑页的短按 / 长按；
 *  · [scenes] —— 「场景动作」页的几档（组合键的场景块只有短按 / 长按两档）。
 *
 *  带子底下那三页（按键绑定 / 组合键 / 场景动作）**共用一份页面实现**
 *  （`BindingPagerScreen`），但各持一条分页器 —— 见 [PagePagers.gesturesOf]。
 *
 *  **「系统场景」页没有分页器**：翻转之后一张表里每块自带三档（见 `SceneRule`），
 *  页面不再按手势分页，原来那条 `branches` 分页器跟着手势维度一起删掉了。
 */
internal class PagePagers(
    val gestures: PagerState,
    val commands: PagerState,
    /** 「组合键」那一页短按 / 长按的分页器 —— 与 [gestures] 同一把尺子，各持一份位置。 */
    val combos: PagerState,
    /** 「场景动作」页的手势分页器 —— 页数跟着 `sceneGesturePages()` 走（键三档、组合两档）。 */
    val scenes: PagerState,
) {
    /**
     * 「一档手势一屏」那三页各用哪一条分页器。
     *
     *  **不共用一个实例**：这三页可能同时在栈里（按键绑定 → 系统场景 → 场景动作），
     *  各自要记住自己停在哪一档；共用一条会在转场时被邻居拽走。
     */
    fun gesturesOf(s: Screen): PagerState =
        when (s) {
            Screen.COMBO_EDIT -> combos
            Screen.SCENE_BINDING -> scenes
            else -> gestures
        }
}

/**
 * 几个分页器一次建好 —— 建在页面级（`PageContent`），不随页重建。
 */
@Composable
internal fun SettingsPage.rememberPagePagers(): PagePagers {
    //  按键绑定页那几档**跟着在编那颗键的开关走**（`GestureTrigger.slotsOn`）：
    //  三击 / 四击没打开就只排三页 —— 页数不再是常量，开关一拨页数当场变。
    val gestures = rememberPagerState(pageCount = { bindingSlotCount() })
    val commands = rememberPagerState(pageCount = { COMMAND_PAGE_COUNT })
    //  组合键只有短按 / 长按两档（`Gesture.entries` 是三档，别直接借它）
    val combos = rememberPagerState(pageCount = { 2 })
    //  场景动作页的档数**跟当前那张表走**：一颗键三档、一组组合两档（见 `sceneGesturePages`）。
    //  读的是 `branchDraft.comboId` 这个页面级状态 —— 换了一份表，页数当场跟着变。
    val scenes = rememberPagerState(pageCount = { sceneGesturePages().size })
    //  **这个包本身也要 remember**：`PagePagers` 是普通类（按引用相等），每次重组新建一个实例
    //  就等于「分页器包换了」—— 凡是拿它当 `remember` 键的地方（路由 DSL 那一份）都会跟着重建，
    //  而库那边的路由表是按 lambda 引用记的 ⇒ 整页内容被销毁重建。包里的四个分页器本来就是
    //  `remember` 出来的，这个包只是把它们捆在一起，理应与它们同寿命。
    return remember(gestures, commands, combos, scenes) {
        PagePagers(gestures = gestures, commands = commands, combos = combos, scenes = scenes)
    }
}

/**
 * 「按键绑定」页现在排**几页** —— 在编那颗键的三档还是五档。
 *
 *  与 `bindingTargetsOf` 同一份清单（`GestureTrigger.slotsOn`）：页数与页里摆什么必须同一个判据，
 *  否则会出现「页数还是五、第四页拿到 `null` 目标」。
 */
private fun SettingsPage.bindingSlotCount(): Int {
    val cfg = configState.config.value
    val key = cfg.key(KeyWatchState.editing(cfg))
    return GestureTrigger.slotsOn(key.extraClicksOn, key.clickThenLongOn).size
}
