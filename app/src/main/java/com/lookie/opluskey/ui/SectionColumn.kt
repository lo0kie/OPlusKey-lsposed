package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 列表行之间的默认间距（虚拟列表用）—— 调用点要别的值就传 `gap`。 */
internal val SECTION_GAP = 16.dp

/**
 * **列容器**：竖着排，**只在相邻两列之间**给 [gap]。
 *
 *  只管一件事 —— 间距。三条边界写清楚：
 *  1. **不管内容**：里面放什么由调用方决定（小标题要不要、放哪儿，是调用方自己的事，
 *     这里不做任何「标题 + 内容」的假设）；
 *  2. **不给首尾留白**：第一列之前、最后一列之后都不加（`Arrangement.spacedBy` 的语义），
 *     所以调用点不需要判断「是不是第一块 / 最后一块」；
 *  3. **自己不声明任何边距**：没有 padding、没有 margin —— 页面缩进与块外留白由页面自己给。
 *
 *  ```
 *  SectionColumn {
 *      Column { SectionTitle("主开关"); HomeBindingCard(ui) }
 *      Column { SectionTitle("按键列表"); HomeKeysCard(enabled) }
 *  }
 *  ```
 */
@Composable
internal fun SectionColumn(
    //  默认 0：页面里「块与块」的间距由小标题自带，这一层不再叠一档；
    //  需要行间距的列表显式传 `gap`（见 [spacedItems]）。
    gap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

/**
 * **一组列表行** —— 行与行之间给 [gap]，**第一行不加、最后一行不加**。
 *
 *  首 / 末这一处判断就在这个组件里做一次，调用点只提供行本身 —— 行不需要知道自己是第几行，
 *  也不要在行自己身上挂外边距（那正是之前「标题上间距一会儿大一会儿小」的来源：
 *  行自带的下边距会叠到紧跟其后的标题上）。
 *
 *  为什么不是 `LazyColumn(verticalArrangement = spacedBy(…))`：那样连「标题 → 第一行」也会被
 *  插一档；这里只作用在同一组行内部。
 */
internal fun <T> LazyListScope.spacedItems(
    items: List<T>,
    gap: Dp = 12.dp,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    itemsIndexed(
        items = items,
        key = if (key == null) null else { _, item -> key(item) },
    ) { index, item ->
        Column {
            //  首行不给上间距 —— 标题与第一行之间只该有标题自带的那一档
            if (index > 0) Spacer(Modifier.height(gap))
            itemContent(item)
        }
    }
}
