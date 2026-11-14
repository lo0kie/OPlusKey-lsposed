package com.lookie.opluskey.ui.screen.apppicker

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

// 「进场定位」最后那一下**实测校准** —— 「选择应用」与「选择界面」两页共用。
//
// 抽出来是因为两页的几何是同一件事（都是「顶栏 + 固定带」下面起画），而校准只有一份口径。

/**
 * 定位滚完之后，按**实测布局**把目标行收进该在的位置。
 *
 * ## 为什么不只信算术
 *
 *  落点是按库的两个槽位常量算的（展开 / 折叠两档，见两页 `Locate*Selected` 的注释）。
 *  常量与实际之间总会差几 dp（字号档、系统栏高度、库版本），而 `scrollToItem` 的位移还可能被
 *  顶栏吃掉一点。所以滚完读一次布局结果，差多少收多少 —— 判断标准是**那一行此刻在屏幕上的位置**。
 *
 * ## 只往**上**收
 *
 *  目标行比预期低（`arrived > targetPx`）才补：低一点只是多留一段空白，不会看错行；
 *  而往回滚会让顶栏重新展开、遮挡变高，越纠越糟。
 *
 * ## 必须先等一帧
 *
 *  `scrollToItem` 是挂起的，但它返回那一刻 `layoutInfo` 还是滚动**之前**的那一份。
 *  上一版就是因为没等这一帧，读到旧落点、照着它「纠正」，结果首屏多滚了一整屏
 *  （真机反馈「滚动距离偏多」）。
 *
 * @param index 列表里那一项的下标（**含**顶上那个「顶栏让位」占位项，见 `APP_LIST_HEADER_ITEMS`）
 * @param targetPx 这一项的顶边该落在视窗顶下方多少像素
 * @return 收完之后那一项的落点（px）。**第一次就看不到**（这一项还没进视窗）返回 `-1`；
 *   补滚一帧之后仍读不到时返回 [targetPx] —— 那是「按差值补过了」的估计值，
 *   不是「没找到」（写 `-1` 的话调用方会把一次成功的定位当成失败）
 */
internal suspend fun LazyListState.settleRowBelow(
    index: Int,
    targetPx: Int,
): Int {
    withFrameNanos { }
    val arrived = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset ?: return -1
    if (arrived <= targetPx) return arrived
    scrollBy((arrived - targetPx).toFloat())
    //  再等一帧才读得到补完之后的真值；读不到就按「按差值补过」报，日志里不会说谎
    withFrameNanos { }
    return layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset ?: targetPx
}
