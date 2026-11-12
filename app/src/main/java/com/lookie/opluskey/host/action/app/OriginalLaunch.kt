package com.lookie.opluskey.host.action.app

import android.content.Context
import android.content.Intent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import java.lang.reflect.Modifier

/**
 * 「打开应用」时**交给宿主自己的启动链路**去起 —— 原版那套卡片展开与扫光就在它里面。
 *
 * ## 为什么是「转交」而不是「复刻」
 *
 *  技术文档 `docs/features/launch-animation.md` §9 的结论是那条链路恒不生效（`callback.a()` 与 `z`
 *  都是常量、adapter 恒 `null`、`d.o` 只打一条失败日志就返回）。**真机上不是这样** ——
 *  把模块总闸关掉、让侧键走原生行为，系统日志里会出现：
 *
 *  ```
 *  WindowManagerShell: RemoteTransitionInfo { remoteTransition = …Stub$Proxy@…,
 *                                            debugName = SpruceLauncher }
 *  WindowManagerShell: Delegate animation for (#47) to RemoteTransition { … SpruceLauncher }
 *  ```
 *
 *  `SpruceLauncher` 这个 debugName 全 APK 只有 `d$a.a()` 那一处写，而它只在 `adapter != null`
 *  时被调到 ⇒ 那条链路在运行时是**活的**（静态分析读的是常量分支，运行时的实际取值不是它算的那个）。
 *
 *  既然它活着，就没有复刻的理由：自己造一套 `RemoteAnimationAdapter` 不但要拖进
 *  `IRemoteAnimationRunner` 这种隐藏 AIDL（本工程没有框架 stub 依赖，编不过），
 *  复刻出来的还只是它的近似 —— 而现成那一套就在同一个进程里，一个反射调用就能用。
 *
 * ## 转交的是什么
 *
 *  `remote.transition.d#m(Context, Intent, int, boolean)` —— 原版三个入口最终都落到它
 *  （文档 §2.1），内部依次选动画实现、埋点、配 COE 扫光、建 animator，最后由
 *  `context.startActivityAsUser(intent, options, CURRENT)` 起界面。**界面也由它起**，
 *  所以调用方转交之后不能再自己起一次（会两个任务）。
 *
 * ## 为什么只对主用户
 *
 *  它内部用的是 `UserHandle.CURRENT`，没有 userId 入口 —— 分身目标（`999@包名`）交给它会在
 *  主用户里起。那一档仍然走我们自己的 `AppLaunchAsUser`，没有原版动画（分身本来也没验过）。
 *
 * ## 失败怎么退
 *
 *  反射拿不到 / 调用抛异常 ⇒ 返回 `false`，调用方照旧自己起界面。**不留延时兜底**：
 *  那需要在前台判定与「原版已起但还没到前台」之间猜，猜错就是开两个任务；
 *  而这条链路在真机上是活的，正常路径不会走到兜底里来。
 */
internal object OriginalLaunch {
    /** 原版那套启动控制器的类名（R8 之后的名字，见文档 §12.1 的符号表）。 */
    private const val LAUNCH_CONTROLLER = "com.oplus.gesture.remote.transition.d"

    /** `d#m` 的类型参数：`2` = 打开应用 / 打开设置页（文档 §2.2）。 */
    private const val TYPE_OPEN_APP = 2

    /**
     * 把这次启动交给原版 — `true` = 已经交出（界面由它起），调用方不要再起一次。
     */
    fun launch(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            //  **必须用宿主的 ClassLoader**：模块自己那条 dex 路径里只有模块包（异常信息里那个
            //  `InMemoryDexFile[...]`），默认的 `Class.forName(name)` 会当场 ClassNotFoundException。
            val cls = Class.forName(LAUNCH_CONTROLLER, false, context.classLoader)
            val controller = controllerOf(cls)
            cls
                .getDeclaredMethod(
                    "m",
                    Context::class.java,
                    Intent::class.java,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).apply { isAccessible = true }
                .invoke(controller, context, intent, TYPE_OPEN_APP, false)
            XLog.w("打开应用：已转交原版启动链路（由宿主建 RemoteTransition）")
            true
        } catch (e: Throwable) {
            val cause = Reflect.deepestCause(e)
            XLog.w("打开应用：原版链路调不起来，改由模块自己起：${cause.javaClass.simpleName}: ${cause.message}")
            false
        }

    /**
     * 原版那个控制器实例 —— 优先用宿主自己的单例（静态字段里存着一个 `d`），没有才新建。
     *
     *  用单例的理由：`d` 自己实现着 animator 的生命周期回调（`a()` / `b()` / `c(float)` / `d()`），
     *  新造一个虽然也不影响启动，但那条链路上任何一处按实例做记账的地方都会看不到它。
     */
    private fun controllerOf(cls: Class<*>): Any {
        cls.declaredFields
            .firstOrNull { Modifier.isStatic(it.modifiers) && it.type == cls }
            ?.let { field ->
                field.isAccessible = true
                field.get(null)?.let { return it }
            }
        return cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }
}
