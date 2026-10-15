package com.lookie.opluskey.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * release 源集：没有调试通道。`register` 恒返回 `null`，不注册任何接收器。
 *
 * 真实实现只在 `src/debug/`，这里必须是空壳 —— 否则 `RECEIVER_EXPORTED` 与它的 action
 * 会留在 release 产物里（R8 被 `-keep` 挡住，删不掉）。
 */
internal object PageDebugChannel {
    fun register(
        context: Context,
        onCommand: (Intent) -> Unit,
    ): BroadcastReceiver? = null
}
