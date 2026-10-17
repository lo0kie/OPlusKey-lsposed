package com.lookie.opluskey.ui.screen.command

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.Dp
import io.github.suqi8.coui.kmp.basic.ScrollBehavior

/**
 * 一页的滚动骨架 —— 三样东西在整条链路上是**一起动**的，收成一个参数免得每次往下传三份：
 * 本页那份 [ScrollState]、大标题的折叠行为、以及顶栏那条固定带让出来的高度。
 */
internal class CommandPageScroll(
    /** 本页自己的那一份 —— 两页共用一份会让「切回来就滚不动」（见 `CommandScreen` 的注释）。 */
    val state: ScrollState,
    val behavior: ScrollBehavior,
    /** 顶栏那条类型选择器带的高度，内容顶上要让它出来。 */
    val tabBlockDp: Dp,
)
