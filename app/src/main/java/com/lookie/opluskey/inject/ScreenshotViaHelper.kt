package com.lookie.opluskey.inject

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect
import java.lang.reflect.Proxy
import java.util.function.Consumer

/**
 * 在 `system_server` 里**直接**发起一次截图 —— 不经过按键注入。
 *
 * ## 为什么还要这一条
 *
 *  `injectInputEvent` 那条路在绝大多数机器上能用（与 `input keyevent 120` 同级），但个别机型
 * （PLG110 / ColorOS）实测：注入成功、事件也确实进了 policy 的 `interceptKeyBeforeQueueing`、
 * 我们这边也放行了，**屏幕就是不截**。那是那台机器自己认的口径，注入参数怎么调都够不着。
 *
 *  所以补一条完全不依赖按键的路：直接调系统的 `ScreenshotHelper`（`system_server` 自己截图用的
 *  就是它）。成了就用它，没成就退回注入 —— 两条都在日志里留判据，下次一看就知道哪条能用。
 *
 * ## 全是反射，且**把签名打进日志**
 *
 *  `ScreenshotHelper` 是 `@hide`（注入侧不许 import 宿主私有类型，见 `AGENTS.md` 红线 1），
 *  只能按名字找。它的 `takeScreenshot` 在 AOSP 14 / 15 上参数个数变过 —— 所以第一次调用时把
 *  **构造器与全部同名方法的签名**各打一行：签名对不上时，日志里直接能看到该补哪个参数，
 *  不用再让用户去抓一次。
 *
 *  ## 失败一律返回 false、不抛
 *
 *  这条路跑在 AMS 的广播入口上，漏一个异常出去就是 system_server 崩（红线同 [injectKeyFromSystem]）。
 *
 * @param contextProvider 现取 Context（注入侧那份借来的 Application Context 就够：这条路只需要
 *   binder 与 Looper，不需要 `ContentResolver`）
 * @return true = 已经发起截图（成败由系统那边定，日志里有回调）
 */
internal object ScreenshotViaHelper {
    /** `ScreenshotHelper.SCREENSHOT_GLOBAL_ACTION` —— 与「电源键菜单里点截图」同一档。 */
    private const val SCREENSHOT_GLOBAL_ACTION = 1

    private const val CLASS_NAME = "com.android.internal.util.ScreenshotHelper"

    /** 签名只在第一次调用时打一遍（这条会被每一次截图打到，刷屏没意义）。 */
    @Volatile
    private var signaturesLogged = false

    fun take(
        loader: ClassLoader?,
        contextProvider: () -> Context?,
    ): Boolean {
        //  找类 / 找构造器 / 找方法这三步各有各的失败，都只记日志并返回 null ——
        //  收进 `resolve` 是为了让这里只剩「发起 + 收尾」两件事（detekt 的 ReturnCount 也数得清）。
        val ready = resolve(loader, contextProvider) ?: return false
        return try {
            ready.method.invoke(
                ready.helper,
                SCREENSHOT_GLOBAL_ACTION,
                Handler(Looper.getMainLooper()),
                completionConsumer(),
            )
            XLog.w("截图（Helper）：已发起（type=$SCREENSHOT_GLOBAL_ACTION）")
            true
        } catch (e: Throwable) {
            XLog.w("截图（Helper）：失败（${e.javaClass.simpleName}）：${e.message}")
            false
        }
    }

    /**
     * 备好「能调的那两样」：Helper 实例、`takeScreenshot` 方法。任一步落空都返回 `null`（已记日志）。
     *
     *  **四道闸各留一句自己的日志**（类不在 / 拿不到 Context / 没那个构造器 / 没那个方法）——
     *  合并成一句「这条路走不通」的话，换 ROM 时看不出该补哪一样。所以这里不压 `return` 的条数。
     */
    @Suppress("ReturnCount")
    private fun resolve(
        loader: ClassLoader?,
        contextProvider: () -> Context?,
    ): Ready? {
        val cls =
            Reflect.findClass(loader, CLASS_NAME) ?: run {
                XLog.w("截图（Helper）：$CLASS_NAME 找不到，这条走不通")
                return null
            }
        logSignaturesOnce(cls)

        val ctx =
            contextProvider() ?: run {
                XLog.w("截图（Helper）：拿不到 Context，这条走不通")
                return null
            }
        val helper =
            cls.constructors
                .firstOrNull { it.parameterCount == 1 && it.parameterTypes[0] == Context::class.java }
                ?.also { it.isAccessible = true }
                ?.newInstance(ctx)
                ?: run {
                    XLog.w("截图（Helper）：没有 (Context) 构造器，这条走不通")
                    return null
                }
        //  **参数个数按 3 找**：ColorOS（PLG110 实测）上是
        //  `takeScreenshot(int type, Handler, Consumer<Uri>)` —— AOSP 14 那份 5 参
        //  （多 `hasStatus` / `hasNav`）在这台机器上不存在。两个版本都认：
        //  优先「首参是 int」的那条，另一条（`ScreenshotRequest`）留给下面那句日志说明。
        val method =
            cls.declaredMethods.firstOrNull {
                it.name == "takeScreenshot" && it.parameterCount == 3 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            } ?: run {
                XLog.w("截图（Helper）：没有 (int, Handler, Consumer) 的 takeScreenshot，这条走不通")
                return null
            }
        method.isAccessible = true
        return Ready(helper, method)
    }

    /** [resolve] 的产物 —— 「能调的那两样」。 */
    private class Ready(
        val helper: Any,
        val method: java.lang.reflect.Method,
    )

    /** 第一次调用时把构造器与全部 `takeScreenshot` 签名打进日志 —— 签名对不上时一眼能看出来。 */
    private fun logSignaturesOnce(cls: Class<*>) {
        if (signaturesLogged) return
        signaturesLogged = true
        XLog.w("截图（Helper）：构造器 = ${cls.constructors.joinToString(" | ") { it.toGenericString() }}")
        val methods = cls.declaredMethods.filter { it.name == "takeScreenshot" }
        XLog.w("截图（Helper）：takeScreenshot = ${methods.joinToString(" | ") { it.toGenericString() }}")
        //  常量也一并打出来：`SCREENSHOT_GLOBAL_ACTION` 在不同版本上是 1 还是别的值，只有日志能说清
        XLog.w(
            "截图（Helper）：常量 = " +
                cls.declaredFields
                    .filter { it.name.startsWith("SCREENSHOT_") && it.type == Int::class.javaPrimitiveType }
                    .joinToString(" ") { f ->
                        f.isAccessible = true
                        "${f.name}=${runCatching { f.getInt(null) }.getOrDefault(-1)}"
                    },
        )
    }

    /**
     * `Consumer<Uri>` 的实现 —— 系统截完图会把落盘的 Uri 回给它。
     *
     * 用动态代理而不是真写一个类：`Consumer` 是 `java.util.function` 里的接口，写个实现要
     * import 它没问题，但代理不必关心泛型擦除后的签名差异，也不会多一个类。
     * 回调这一行是**这条路过没过的最终判据**：发起成功但没回调 = 系统那边没截。
     */
    private fun completionConsumer(): Any =
        Proxy.newProxyInstance(
            Consumer::class.java.classLoader,
            arrayOf(Consumer::class.java),
        ) { _, method, args ->
            if (method.name == "accept") {
                XLog.w("截图（Helper）：回调拿到结果 ${args?.firstOrNull()}")
            }
            null
        }
}
