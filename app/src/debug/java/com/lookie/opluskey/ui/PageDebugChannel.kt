package com.lookie.opluskey.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.lookie.opluskey.core.log.XLog

/**
 * 设置页的调试通道 —— 本文件只存在于 debug 源集，release 包里没有它。
 *
 * 用 `RECEIVER_EXPORTED` 是为了让 `adb shell am broadcast` 发得进来；因此**绝不能让这段代码
 * 进 release 包**（`-keep class com.lookie.opluskey.**` 会挡住 R8 的死代码消除，靠编译期常量
 * 兜底只能留下形迹）。release 侧由 `src/release/` 的同名文件提供空实现。
 */
internal object PageDebugChannel {

    /** 广播的 action。 */
    const val ACTION = "com.lookie.opluskey.DEBUG"

    /**
     * 注册接收器。`onCommand` 在主线程收到广播时被调用（`registerReceiver` 默认就是主线程）。
     *
     * @return 注册好的接收器，调用方在 `onDispose` 里注销。
     */
    fun register(context: Context, onCommand: (Intent) -> Unit): BroadcastReceiver? {
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
        context.registerReceiver(receiver, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
        XLog.i("调试通道已注册：am broadcast -a $ACTION --es screen …")
        return receiver
    }
}
