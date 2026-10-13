package com.lookie.opluskey.page

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.lookie.opluskey.DEBUG_CHANNEL
import com.lookie.opluskey.XLog

/**
 * 设置页的调试通道 —— 只有 debug 包（[DEBUG_CHANNEL]）会注册。
 */
internal object PageDebugChannel {

    /** 广播的 action。 */
    const val ACTION = "com.lookie.opluskey.DEBUG"

    /**
     * 注册接收器。`onCommand` 在主线程收到广播时被调用（`registerReceiver` 默认就是主线程）。
     *
     * @return 注册好的接收器，调用方在 `onDispose` 里注销；release 包返回 `null`。
     */
    fun register(context: Context, onCommand: (Intent) -> Unit): BroadcastReceiver? {
        if (!DEBUG_CHANNEL) return null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                try {
                    onCommand(intent)
                } catch (e: Throwable) {
                    // 调试口子不许把页面搞崩：出错只记日志
                    XLog.w("调试通道执行失败（忽略）：${e.message}")
                }
            }
        }
        return try {
            context.registerReceiver(receiver, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
            XLog.i("调试通道已注册：am broadcast -a $ACTION --es screen …")
            receiver
        } catch (e: Throwable) {
            XLog.w("调试通道注册失败（忽略）：${e.message}")
            null
        }
    }
}
