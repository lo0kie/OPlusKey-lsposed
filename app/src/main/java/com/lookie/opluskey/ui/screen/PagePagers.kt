package com.lookie.opluskey.ui.screen

import androidx.compose.foundation.pager.PagerState

/**
 * 页面级的三个横向分页器 —— 一路传下去的那个包。
 *
 *  为什么打包：这三个都建在 `PageContent` 那一层（页面级，不随页重建），而用它们的是
 *  两棵树 —— 内容（`PageBody`）与顶栏（`PageTopBar` 底下那条分段控件）。原来两个 pager
 *  是各自一个参数，加第三个（组合键的短按 / 长按）时每个签名都要多一格；
 *  打成一个包之后，将来再多一个分页器只改这里与创建点。
 *
 *  三个各管一页，**不共用**：
 *  · [gestures] —— 「按键绑定」页的三档手势；
 *  · [commands] —— 「自定义命令」页的类型那一排；
 *  · [combos] —— 「组合键」编辑页的短按 / 长按。
 */
internal class PagePagers(
    val gestures: PagerState,
    val commands: PagerState,
    val combos: PagerState,
)
