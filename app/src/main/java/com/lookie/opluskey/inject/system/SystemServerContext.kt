package com.lookie.opluskey.inject.system

import android.content.Context
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect

/**
 * `system_server` **自己那一份** System Context（包名 `android`）—— 注入侧「以系统身份 +
 * 走公开 API」的那几个动作（弹提示、写音量）都借它。
 *
 * ## 为什么不用 `bridge/inject/SystemContext` 那份
 *
 *  那一份是从某个真实 App 进程里**借**来的 Application Context：身份是 system（挂在我们这个进程
 *  上），包名却仍是那个 App —— 弹 Toast 会按宿主的名义走限流、写音量会按宿主的身份判权限。
 *  `ActivityThread.getSystemContext()` 给的这一份两者对得上，正是那两件事需要的。
 *
 * ## 只在注入侧可用
 *
 *  `currentActivityThread()` / `getSystemContext()` 都是 `ActivityThread` 的静态口子：只有
 *  `system_server` 里有那个实例。宿主进程里调会拿到 `null`（那边也不需要它）。
 *
 *  取到就缓存（一份 Context 贯穿进程寿命）；**取不到不缓存**，下次再试 —— 启动早期
 *  `ActivityThread` 可能还没就绪。缓存到失败值会让那两件事整个进程内永久失效。
 */
internal object SystemServerContext {
    @Volatile
    private var cached: Context? = null

    /**
     * @param loader `system_server` 的 ClassLoader
     * @return 拿不到返回 `null`（调用方各自记一句「断在哪一环」的日志，见五、2）
     */
    fun get(loader: ClassLoader): Context? {
        cached?.let { return it }
        val thread = ActivityThreadCompat.currentActivityThread(loader) ?: return null
        val ctx = Reflect.call(thread, "getSystemContext") as? Context ?: return null
        cached = ctx
        return ctx
    }
}
