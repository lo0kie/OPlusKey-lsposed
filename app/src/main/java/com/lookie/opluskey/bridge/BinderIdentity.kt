package com.lookie.opluskey.bridge

import android.os.Binder

/**
 * 以 **system_server 自己的身份**（uid 1000）执行 [block] —— 注入侧那几条系统服务调用共用这一份。
 *
 * ## 为什么需要换身份
 *
 *  这些反射调用跑在 AMS 的广播 / 按键回调里。整条链路若是从宿主进程的 Binder 事务进来的，
 *  `Binder.getCallingUid()` 拿到的是**宿主的 uid** —— 目标服务一旦按调用方判权限就抛
 *  `SecurityException`，表现是「通知中心 / 控制中心 / 电源菜单按了没反应」。
 *  换成本进程（system_server）的身份之后，目标看到的就是 uid 1000。
 *
 * ## 换完必须还原
 *
 *  `clearCallingIdentity` 改的是**当前线程**的调用方身份，不还原会把 system 身份泄漏给同一线程
 *  后面的调用 —— 那一段代码会以 1000 的身份去访问别人的东西。所以 [block] 用 `try/finally` 包着，
 *  抛异常也照样还原。
 *
 * ## 为什么收成一处
 *
 *  这段原来是 `StatusBarBridge` 与 `VoiceAssistBridge` 各自一份 private 实现，两份逐字相同 ——
 *  而「必须还原」这件事只写在各自的注释里。以后要改（比如加一层日志、或者改成按线程缓存 token），
 *  改一处漏一处就是身份泄漏。
 */
internal inline fun <T> asSystem(block: () -> T): T {
    val token = Binder.clearCallingIdentity()
    return try {
        block()
    } finally {
        Binder.restoreCallingIdentity(token)
    }
}
