package com.lookie.opluskey.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
