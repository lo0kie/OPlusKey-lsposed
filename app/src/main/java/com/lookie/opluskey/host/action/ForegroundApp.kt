package com.lookie.opluskey.host.action

import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 「此刻最前面那个应用是哪个包」，以及那张表本身 —— 条件执行与「有小窗」两条判据共用。
 *
 * ## 为什么走 `OplusActivityManager#getAllTopAppInfos()`
 *
 *  模块已经在用这张表（小窗那一族），**权限与反射面都验过**：宿主进程读得到，而
 *  `ActivityManager#getRunningTasks` 那一套在新版本上要么被 `GET_TASKS` 权限挡住、要么
 *  只回本进程的任务，`UsageStatsManager` 则要用户手动授权「使用情况访问」。
 *
 *  表是**按「最前面」排序**的，取第 0 项就是前台应用。注意小窗在场时这张表会同时有
 *  「小窗那份」与「全屏那份」两条，第 0 项是谁取决于此刻谁在最前面 —— 对「前台是谁」
 *  这个问题正是要的那个答案，不要改成「扫整张表」（那是小窗验收的判据，问的不是一回事）。
 *
 * ## 读不到时返回空
 *
 *  没有这个类 / 方法、或表是空的，都会返回空。调用方（分叉）把空当 **「判不出」**：
 *  不命中任何带条件的分支，直接落兜底。宁可走兜底，也不猜一个包名 —— 猜错会执行用户
 *  完全没有预期的那条命令。
 */
internal object ForegroundApp {
    /** 前台应用的包名；判不出时 `null`。 */
    fun packageName(): String? =
        infos().firstOrNull()?.let { top ->
            Reflect
                .fieldValue(top, top.javaClass, MiniWindowFacts.TOP_APP_ACTIVITY_FIELD)
                ?.let { packageOf(it) }
        }

    /**
     * 整张表（原始的 `OplusAppInfo` 对象）；取不到时返回空表。
     *
     *  「有小窗」那条判据要自己扫这张表（找 `windowingMode == 100` 的那一条），所以这里把
     *  原始对象交出去，而不是只给包名。
     */
    fun infos(): List<Any> {
        val cls =
            Reflect.findClass(ForegroundApp::class.java.classLoader, MiniWindowFacts.ZOOM_MANAGER)
                ?: run {
                    warnOnce("没有 ${MiniWindowFacts.ZOOM_MANAGER}，判不出前台应用（分叉按判不出处理）")
                    return emptyList()
                }
        val manager =
            Reflect.callStatic(cls, "getInstance") ?: run {
                warnOnce("${MiniWindowFacts.ZOOM_MANAGER}#getInstance 返回 null，判不出前台应用")
                return emptyList()
            }
        val method =
            Reflect.methodByNameAndArity(cls, MiniWindowFacts.TOP_APP_INFOS_METHOD, 0) ?: run {
                warnOnce("${MiniWindowFacts.ZOOM_MANAGER} 上没有 ${MiniWindowFacts.TOP_APP_INFOS_METHOD}/0")
                return emptyList()
            }
        return (Reflect.call(manager, method) as? List<*>).orEmpty().filterNotNull()
    }

    /**
     * 「这条判据整条不通」的日志只打**一次**。
     *
     *  分叉每按一下都会判一次条件；读不到时每按一下刷一行同样的话没有意义，反而把别的判据行挤走。
     */
    @Volatile
    private var warned = false

    private fun warnOnce(message: String) {
        if (warned) return
        warned = true
        XLog.w(message)
    }

    /**
     * `OplusAppInfo.topActivity` 的包名 —— `ComponentName` 与 `ComponentInfo` 两种形状都认。
     *
     *  与 `MiniWindowLauncher.packageOf` 同一套判法，但那份挂在需要 Context 的实例上；
     *  这里只借「按名字取字段」这一步，不再拖一个执行器进来。
     */
    private fun packageOf(top: Any): String? =
        (top as? android.content.ComponentName)?.packageName
            ?: Reflect.call(top, "getPackageName") as? String
            ?: Reflect.fieldValue(top, top.javaClass, MiniWindowFacts.TOP_APP_PACKAGE_FIELD) as? String
}
