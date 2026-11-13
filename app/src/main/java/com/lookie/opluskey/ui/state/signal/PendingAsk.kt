package com.lookie.opluskey.ui.state.signal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 页面级的**一问一答**：一个待确认的载荷，加上「有没有人在等回答」这一位。
 *
 * ## 为什么收成一个
 *
 *  项目里那几问（停用 / 启用键与组合的连带、切触发方式、关掉「松手时触发」）原先各写一个
 *  `object`，每条字段各占一个 `mutableStateOf`，`ask()` 挨个赋值、`clear()` 挨个归零。
 *  三个毛病：
 *
 *  · **组合状态会被读到半截** —— `ask()` 赋完 `kind` 还没赋 `keys` 的那一帧，
 *    已经有人在读「问的是什么、要改哪几项」；
 *  · **归零漏一位就挂住** —— 少清一个字段，下一问就带着上一问的残留（某一问的「连带项」）。
 *  · 同一个模式复制三份，`asking` / `clear` 的正确性要在三处各修一遍。
 *
 *  一个载荷对象换一个字段（`mutableStateOf` 的读写在快照上是一个整体），上面三条一起消失。
 *
 * ## 它只做三件事
 *
 *  · [set] 挂上一问（同一时刻只有一问，后到的覆盖前的）；
 *  · [get] 读那一问（`null` = 没人等回答）；
 *  · [clear] 收掉。
 *
 *  业务判据（问什么、答了之后改哪几位）留在各自的 `object` 里 —— 这一层不认识键、组合或触发方式。
 */
internal class PendingAsk<T : Any> {
    private var payload by mutableStateOf<T?>(null)

    /** 有没有人在等回答 —— 窗该不该露面。 */
    val asking: Boolean get() = payload != null

    /** 当前那一问（`null` = 没有）。 */
    val pending: T? get() = payload

    /** 挂上一问 —— 覆盖前一个（同一时刻只该有一问）。 */
    fun set(value: T) {
        payload = value
    }

    /** 收掉（取消 / 答完都要走这里，唯一的归零点）。 */
    fun clear() {
        payload = null
    }
}
