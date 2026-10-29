package com.lookie.opluskey.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lookie.opluskey.ui.model.PAGE_BOTTOM_PADDING
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/**
 * 一页内容的**纵向滚动骨架** —— 全项目所有「整页往下滚」的页面共用这一条链。
 *
 * ## 为什么收成一处
 *
 *  这条链原来是每页各写一遍的（十个页面逐字相同，连注释都是同一句改几个字）：
 *
 *  ```
 *  fillMaxSize → nestedScroll → overScrollVertical → verticalScroll → padding(bottom)
 *  ```
 *
 *  重复的代价不在字符数，在**顺序**：这几个修饰符的先后各自有一条非写不可的理由
 *  （见下面「顺序不能动」），十份拷贝里只要有一份写反，症状是「顶栏不折」或「底部露一块平色」——
 *  都不是崩溃，只在真机上看得出，而且要逐个页面比对才发现是哪一页写错了。
 *
 * ## 顺序不能动
 *
 *  1. **`nestedScroll` 必须在 `verticalScroll` 之前** —— 大标题随滚动折叠靠它把滚动量喂给顶栏。
 *     顺序反了顶栏收不到位移，标题永远停在展开态（这一条真机踩过，见 `BranchScreen` 的注释）。
 *  2. **`overScrollVertical` 挂在滚动容器外侧** —— 它吃的是 `verticalScroll` 没吃掉的那部分位移，
 *     所以内容不满一屏时照样能拖出回弹（只有一两条内容的页面不挂它完全动不了）。
 *  3. **页底留白落在 `verticalScroll` 之后** —— 那样它才是「滚动内容里的内缩」，视口仍铺到屏幕底。
 *     写成尾部 `Spacer` 的话不进滚动内容高度，最大滚动位置会少一截。
 *
 * ## 不负责「让出顶栏」
 *
 *  顶部那个占位 `Spacer` **不在这里**：各页的额外高度不一样（有的要加固定带、有的要加分段控件），
 *  而且有几页（空态居中那种）要拿顶栏高度去算别的。调用方自己写
 *  `Spacer(Modifier.height(currentHeaderHeight(scrollBehavior) + 额外))`。
 *
 * @param scrollBehavior 本页顶栏的滚动行为（`PageHeader` 用的那一份）
 * @param state 滚动状态。默认本页自己一份；**多页共用一个 pager 的场合要传外面那份**
 *   （分段控件在 pager 之外，靠它算自己该滑到哪儿）
 * @param modifier 额外的修饰符，挂在链**尾**（在页底留白之后）。目前只有「按键绑定」页用它挂
 *   `onPreviewKeyEvent` —— 它要拿到已经排好版的那个 `Column`，挂在链首会被 `verticalScroll`
 *   当成滚动内容的一部分
 * @param content 页内容。块与块之间的间距由小标题自带，这里不额外加 `Spacer`
 */
@Composable
internal fun SettingsPage.PageScrollColumn(
    scrollBehavior: ScrollBehavior,
    state: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    //  挂在 `SettingsPage` 上（页面的隐式接收者）而不是 `ColumnScope` 上：有几页把它套在
    //  `Box` / `BoxWithConstraints` 里，那里的接收者是 `BoxScope`，`ColumnScope` 扩展调不到。
    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .overScrollVertical()
            .verticalScroll(state)
            .padding(bottom = PAGE_BOTTOM_PADDING)
            .then(modifier),
        content = content,
    )
}
