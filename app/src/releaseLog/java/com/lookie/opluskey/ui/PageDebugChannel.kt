package com.lookie.opluskey.ui

//  `releaseLog` 源集：与 `src/release/` 那份**逐字相同** —— 那一档多的是日志，不是调试通道。
//  `DEBUG_PROBE` 在它那份 `BuildFlags` 里同样为 false（见 `core/src/releaseLog/.../BuildFlags.kt`）。

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * release 源集：没有调试通道。`register` 恒返回 `null`，不注册任何接收器。
 *
 * 真实实现只在 `src/debug/`，这里必须是空壳 —— 否则 `RECEIVER_EXPORTED` 与它的 action
 * 会留在 release 产物里。
 *
 *  `ACTION` **故意不在这里**（debug 那份有）：它的取值就是那条广播 action，抄到 release 等于
 *  把 action 字符串留在产物里。少这一条带来一个**有用的**副作用 —— main 里谁引用 `ACTION`，
 *  release 就编不过，而不是悄悄把它带进正式包。
 */
internal object PageDebugChannel {
    fun register(
        context: Context,
        onCommand: (Intent) -> Unit,
    ): BroadcastReceiver? = null
}
