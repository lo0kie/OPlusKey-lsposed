package com.lookie.opluskey.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * 顶栏那条**固定带**的高度 —— 带子在顶栏 `bottomContent` 那棵树里自己量，页内容那棵树里读。
 *
 *  为什么要有这条通道：三条带子（「按键绑定」的分段控件 / 「自定义命令」的类型选择器 /
 *  「选择应用」的搜索框）都挂在**顶栏**里 —— 位置、底色、模糊、下沿那条细线全由顶栏那一层
 *  负责（见 `PageHeader` 的 `bottomContent`），而页内容顶上那个等高的占位 Spacer 在**另一棵树**
 *  里，两边只有走页面级状态这一条路能对齐。
 *
 *  一页一份，**不共用**：页面转场期间相邻两屏可能同时在组合里，共用一个值只会互相改对方的占位。
 *
 *  首帧还没量到时是 0（占位少一截），下一帧自纠 —— 不要为此在带子里写死高度：
 *  带子高度随字号与语言变过（[report] 进来的就是实测值）。
 */
internal class BandHeight {
    private val px = mutableStateOf(0)

    /** 带子那一侧：把 `Modifier.onSizeChanged` 报来的高度原样交进来。 */
    fun report(heightPx: Int) {
        px.value = heightPx
    }

    /** 页内容那一侧：读成 dp —— 组合期读，跟着测量结果重组。 */
    @Composable
    fun dp(): Dp = with(LocalDensity.current) { px.value.toDp() }
}
