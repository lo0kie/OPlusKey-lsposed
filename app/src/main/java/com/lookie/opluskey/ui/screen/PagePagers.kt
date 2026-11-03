package com.lookie.opluskey.ui.screen

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.ui.SettingsPage
import com.lookie.opluskey.ui.screen.command.COMMAND_PAGE_COUNT

/**
 * 页面级的四个横向分页器 —— 一路传下去的那个包。
 *
 *  为什么打包：这些都建在 `PageContent` 那一层（页面级，不随页重建），而用它们的是
 *  两棵树 —— 内容（`PageBody`）与顶栏（`PageTopBar` 底下那条分段控件）。原来两个 pager
 *  是各自一个参数，加第三个（组合键的短按 / 长按）时每个签名都要多一格；
 *  打成一个包之后，将来再多一个分页器只改这里与创建点。
 *
 *  各管一页，**不共用**：
 *  · [gestures] —— 「按键绑定」页的三档手势；
 *  · [commands] —— 「自定义命令」页的类型那一排；
 *  · [combos] —— 「组合键」编辑页的短按 / 长按；
 *  · [branches] —— 「条件执行」页的手势那几页（与 [gestures] 分开：那是另一页的几页）。
 */
internal class PagePagers(
    val gestures: PagerState,
    val commands: PagerState,
    val combos: PagerState,
    val branches: PagerState,
)

/**
 * 四个分页器一次建好 —— 建在页面级（`PageContent`），不随页重建。
 *
 *  「条件执行」那一条的页数**跟着在编的东西变**：单键三档（单击 / 双击 / 长按），
 *  组合键只有短按 / 长按两档（双击在组合上没有定义，见 `ComboKey` 的类注释）——
 *  摆一页写进去也永远不会命中的表，比少一页更糟。
 */
@Composable
internal fun SettingsPage.rememberPagePagers(): PagePagers {
    val gestures = rememberPagerState(pageCount = { Gesture.entries.size })
    val commands = rememberPagerState(pageCount = { COMMAND_PAGE_COUNT })
    //  组合键只有短按 / 长按两档（`Gesture.entries` 是三档，别直接借它）
    val combos = rememberPagerState(pageCount = { 2 })
    val branches = rememberPagerState(pageCount = { if (branchDraft.comboId != null) 2 else Gesture.entries.size })
    //  **这个包本身也要 remember**：`PagePagers` 是普通类（按引用相等），每次重组新建一个实例
    //  就等于「分页器包换了」—— 凡是拿它当 `remember` 键的地方（路由 DSL 那一份）都会跟着重建，
    //  而库那边的路由表是按 lambda 引用记的 ⇒ 整页内容被销毁重建。包里的四个分页器本来就是
    //  `remember` 出来的，这个包只是把它们捆在一起，理应与它们同寿命。
    return remember(gestures, commands, combos, branches) {
        PagePagers(gestures = gestures, commands = commands, combos = combos, branches = branches)
    }
}
