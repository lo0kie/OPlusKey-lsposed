package com.lookie.opluskey.ui.wire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import com.lookie.opluskey.core.log.XLog

/**
 * 注册一个**同包内部**的广播接收器，失败只记一行、返回 `false`。
 *
 * ## 它替掉的是四份同一段代码
 *
 *  网络代取（`NetBridge`）、导出日志（`LogPull`）、链路自检（`PageContent`）、收集按键
 *  （`CollectKeyDialog`）原先各写一遍
 *  `runCatching { registerReceiver(receiver, IntentFilter(action), RECEIVER_NOT_EXPORTED) }`。
 *  真正要紧的是第三参数：**它是不导出**（普通 App 发同名广播进不来，`system_server` 仍可达）——
 *  一处抄漏就是一个外部可注入的入口，而它藏在一段一眼扫过去的注册代码里。
 *
 * ## 它不管什么
 *
 *  **注销仍归调用方**：那件事和生命周期绑在一起（组合期 `onDispose`、挂起函数 `finally`、
 *  或者「没注册上就不用注销」），四处的时机各不相同，收进来只会变成一个带开关的通用件。
 *
 * @param what 出问题的是哪一环（原样进日志；有「后果」的调用点自己带上，别让它只说「失败」）
 * @param receiver 要挂上去的接收器
 * @param actions 只收这几类广播
 * @return 注册成功没有 —— 失败已经打过日志，调用方只决定「这一趟还做不做」
 */
internal fun Context.registerLocalReceiver(
    what: String,
    receiver: BroadcastReceiver,
    vararg actions: String,
): Boolean {
    val filter = IntentFilter().apply { actions.forEach(::addAction) }
    return runCatching {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }.onFailure {
        XLog.w("$what：注册接收器失败（已忽略）：${it.javaClass.simpleName}", it)
    }.isSuccess
}
