package com.lookie.opluskey.core.reflect

import android.content.Context
import com.lookie.opluskey.core.log.XLog

/**
 * `android.app.ActivityThread` 上那几处取用，收拢在这一个文件里。
 *
 *  为什么不能直接 `import android.app.ActivityThread`：它是 framework 的 **hidden 类**，编译期没有
 *  stub、写 `import` 直接编译不过；注入侧更是只能按名字解析（AGENTS.md 一、1）。
 *
 *  为什么单开一个文件而不是塞进 [Reflect]：`Reflect` 是通用的「按名字查类 / 方法 / 字段」工具箱，
 *  而这里是**一个具体的框架类该怎么用**。两者混在一起，`Reflect` 就会变成大杂烩。
 *
 *  为什么值得收拢：下面这个方法原来在宿主侧两处、注入侧一处各写了一遍，三份**逐字相同**（只有日志
 *  级别差一档）。它不是平凡代码（多了 loader 参数、`as?` 转型、异常兜底），任一处改错另外两处不会
 *  跟着变 —— 这正是该抽成一处的重复。
 */
object ActivityThreadCompat {
    /** `android.app.ActivityThread` 的类名 —— 全项目只在这里写一次。 */
    private const val ACTIVITY_THREAD = "android.app.ActivityThread"

    /**
     * `ActivityThread.currentApplication()` —— **当前进程**的 Application Context；拿不到返回 null。
     *
     * 取不到的两种正常情形（不是故障）：`onPackageReady` 那一刻 Application 可能还没挂上；
     * 注入侧（`system_server`）本就没有可用的 Application（那边要 Context 走 `SystemContext`）。
     *
     * 这里**只记 `d` 级**：真正「为什么拿不到」由 [Reflect] 那边按 `w` 报（类 / 方法找不到会记账），
     * 本行只是补一句自家前缀，不需要在 release 里留痕。
     */
    fun currentApplication(loader: ClassLoader): Context? =
        try {
            val cls = Reflect.findClass(loader, ACTIVITY_THREAD)
            val app = if (cls == null) null else Reflect.callStatic(cls, "currentApplication")
            app as? Context
        } catch (e: Throwable) {
            XLog.d("取 Application Context 失败：${e.message}")
            null
        }

    /**
     * `ActivityThread` 那个类本身 —— 拿不到返回 null。
     *
     *  给需要**自己读它的字段**的调用方用（如 `SystemContext` 要从 `mAllApplicationThreads`
     *  里挑进程）。类名 [ACTIVITY_THREAD] 只在本文件里出现，调用方不必再写一遍字符串。
     */
    fun classOf(loader: ClassLoader): Class<*>? = Reflect.findClass(loader, ACTIVITY_THREAD)

    /**
     * `ActivityThread.currentActivityThread()` —— 当前进程的那个实例；拿不到返回 null。
     *
     *  `SystemContext` 里原来三处各自跑了一遍「查类 + 调这个静态方法」，**逐字相同**
     *  （这一类不是平凡代码：多了 loader、返回 `Any?`、异常要兜底），任一处改错另两处不会跟着变。
     */
    fun currentActivityThread(loader: ClassLoader): Any? =
        try {
            classOf(loader)?.let { Reflect.callStatic(it, "currentActivityThread") }
        } catch (e: Throwable) {
            XLog.w("取 ActivityThread 实例失败：${e.message}")
            null
        }
}
