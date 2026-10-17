package com.lookie.opluskey.ui.state

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.host.action.ActionRunner

/**
 * 「测试命令」按钮用的执行器（本进程只建一份）。
 *
 *  ## 为什么要缓存
 *
 *  执行器内部持进程级的东西：一个 Handler、几个系统服务的引用，以及 **手电筒那条
 *  `CameraManager.TorchCallback` 注册**（`FlashlightController` 在第一次 toggle 时注册，
 *  进程里不会自己摘）。每点一次命令就新建一个执行器，就是每次多注册一份回调。
 *
 *  ## 为什么要有 [release]
 *
 *  上面这些在**宿主主进程**里是「进程活多久就活多久」的，那里本来就不需要释放。
 *  但这一份活在 `:ui`（设置页进程）里，执行器拿着的是宿主的 Application Context ——
 *  页面关掉之后它仍然钉着那个 Context 与那份回调。开着「手电筒」命令试几次、
 *  再进出设置页几轮，就是几份不会再被用到的注册挂在系统里。
 *
 *  所以页面关掉时（`SettingsPageHooks.clearPageRefs`）显式扔掉这一份：
 *  下次进页第一次点「测试」再新建，代价只是重新注册一次回调。
 *
 *  ## 为什么不用 Application Context 也能接受
 *
 *  [acquire] 只接 `Application.Context`：执行器会把传进来的 Context 一直拿着，
 *  传 Activity Context 就等于永久钉住一个已销毁的 Activity 及其整棵 View 树 ——
 *  那是**无界**泄漏，比上面那点注册严重得多。
 */
internal object TestRunnerCache {
    @Volatile
    private var cached: ActionRunner? = null

    /** 取本进程那一份执行器（没有就建）。 */
    fun acquire(context: Context): ActionRunner {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: ActionRunner(
                context.applicationContext,
                Handler(Looper.getMainLooper()),
            ).also { cached = it }
        }
    }

    /** 扔掉本进程那一份（页面关掉时调）。下一次 [acquire] 会新建。 */
    fun release() {
        synchronized(this) {
            if (cached != null) XLog.w("关页：释放测试命令执行器（下次进页重建）")
            cached = null
        }
    }
}
