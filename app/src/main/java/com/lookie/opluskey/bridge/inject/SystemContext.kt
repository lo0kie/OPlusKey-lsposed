package com.lookie.opluskey.bridge.inject

import android.content.Context
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.ActivityThreadCompat
import com.lookie.opluskey.core.reflect.Reflect

/** 在 `system_server` 里拿一个真正可用的 `Context`。 */
internal object SystemContext {
    /**
     * 缓存的那份是**别的 App 进程的 Application Context**（来自 `mAllApplicationThreads`），只保证
     * `sendBroadcast` / `startService` / `getSystemService` / `packageManager` 可用；`ContentResolver`
     * （query / insert / update / delete）在 `system_server` 里**必 NPE，与拿的是哪一份 Context 无关**
     * —— 借来的 Context 其 `mMainThread` 仍指向 `system_server` 自己的 `ActivityThread`，那个的
     * `mAppThread` 恒 null（见 `docs/device-findings.md` 六之五）。`getSystemContext()` 那份连
     * `mAppThread` 都没有，只够 `getSystemService`；每次现取、不缓存。
     *
     * 注意：**缓存的那份是别的 App 进程的 Application Context**：那个进程一死，这份 Context 里的
     * binder / `ContentResolver` 就跟着失效，之后 `sendBroadcast` / `startService` 全部抛异常，
     * 表现是**手势静默丢失**且日志里看不出原因。所以每次复用前先验一次「它还在不在
     * `mAllApplicationThreads` 里」—— 进程死时系统会把它的 record 从那个容器里摘掉。
     * 验不出来（反射失败 / 字段改名）时按**「还在」**处理（口径与 [stillHeldByItsProcess] 一致）：
     * 那时连「进程在不在」这个事实都拿不到，丢弃重取只会走同一条探不出的反射 → 落到
     * `getSystemContext()` 那个降级品上，接下来每次广播都失败，比留着缓存更糟。
     *
     * 注入阶段还没有 App attach 过、`mAllApplicationThreads` 为空，故调用方不要在安装阶段调这里，
     * 应拿 lambda 推迟到真正要用 Context 的那一刻。
     */
    @Volatile
    private var cached: Context? = null

    /**
     * 降级品那条 W 是否已经报过。
     *
     * 为什么留这一条：只拿到 `getSystemContext()` 的降级 Context 是个真实且会持续的状态
     * （拿不到好 Context 的机器上一直如此），它是「依赖 Context 的功能为什么会跳过」的判据。
     * 为什么每进程只打一次：这条 W 会被**每一个手势**打到，那种机器上会把日志刷满；
     * 而它的信息量是恒定的（第一次打过就够），后续重复没有新内容。
     */
    @Volatile
    private var degradedWarned = false

    /** 拿一个可用 Context。拿不到返回 null（调用方要能容忍）。 */
    fun get(loader: ClassLoader): Context? {
        val held = cached
        if (held != null) {
            // 注意：缓存这一支也要自己兜：验「那份 Context 的进程还在不在」
            //  要走反射，抛出来就是 system_server 里的未捕获异常。
            val stillThere =
                try {
                    stillHeldByItsProcess(loader, held)
                } catch (e: Throwable) {
                    XLog.w("验缓存 Context 失败（按「还在」处理）：${e.message}")
                    true
                }
            if (stillThere) return held
            cached = null
            XLog.w("缓存的那个 Context 所属进程已经不在了，丢弃重取（否则后续广播 / 起服务全部失败）")
        }

        // 这条链跑在 system_server 里，每一段都**自己兜住 Throwable**（见下面两处 `try`）——
        // 不是「整体兜一层」：取 Context 的两条路各包一次，`stillHeldByItsProcess` 内部再包一次。
        // 首选：ActivityThread 里任一 App 进程的 Application Context。
        // 这一份只对 sendBroadcast / startService / getSystemService / packageManager 负责；
        // ContentResolver 在 system_server 里必 NPE，与拿的是哪一份 Context 无关（借来的 Context
        // 其 mMainThread 仍指向 system_server 自己的 ActivityThread，那个的 mAppThread 恒 null，
        // 见 docs/device-findings.md 六之五）。
        val direct =
            try {
                fromApplicationThreads(loader)
            } catch (e: Throwable) {
                XLog.w("探 mAllApplicationThreads 失败：${e.message}")
                null
            }
        if (direct != null) {
            cached = direct
            XLog.w("拿到 system_server 下的可用 Context（来源：mAllApplicationThreads）")
            return direct
        }

        // 次选：getSystemContext()（降级品，只能 getSystemService）—— 不缓存
        val degraded =
            try {
                fromGetSystemContext(loader)
            } catch (e: Throwable) {
                XLog.w("探 getSystemContext 失败：${e.message}")
                null
            }
        if (degraded != null) {
            // 只报一次（见 [degradedWarned]）：这条会被每个手势打到。
            if (!degradedWarned) {
                degradedWarned = true
                XLog.w(
                    "只拿到 getSystemContext() 的 Context —— 它没有 mAppThread，" +
                        "ContentResolver / startActivity 会失败（不缓存，下次再试首选）",
                )
            }
            return degraded
        }

        XLog.w("拿不到任何 system_server Context（依赖 Context 的功能会跳过）")
        return null
    }

    /**
     * [cached] 那份 Context 的进程还在不在 —— 在 `mAllApplicationThreads` 里找同一个对象。
     *
     * 只做一次身份比较（`===`）：容器里的元素要么直接是 Context，要么是持有它的 record。
     * 探不出容器（反射失败 / 字段改名）时**按「还在」算** —— 那说明我们连事实都拿不到，
     * 这时每次丢弃重取只会得到同一份 Context，白白多跑一次反射，不会更安全。
     */
    private fun stillHeldByItsProcess(
        loader: ClassLoader,
        held: Context,
    ): Boolean =
        try {
            val atCls = ActivityThreadCompat.classOf(loader) ?: return true
            val thread = ActivityThreadCompat.currentActivityThread(loader) ?: return true
            var container: Any? = null
            for (fieldName in listOf("mAllApplicationThreads", "mAllApplications")) {
                container = Reflect.fieldValue(thread, atCls, fieldName)
                if (container != null) break
            }
            val values: Iterable<*> =
                when (container) {
                    is Map<*, *> -> container.values
                    is Collection<*> -> container
                    else -> return true
                }
            // 注意：迭代与 `appContextOf` 的反射调用**也在 try 里** —— 这一段
            //  在 try 之外，`appContextOf` 一抛就从 [get] 的缓存分支直接漏进 system_server。
            var hit = false
            for (v in values) {
                if (v == null) continue
                if (v === held) {
                    hit = true
                    break
                }
                if (appContextOf(v) === held) {
                    hit = true
                    break
                }
            }
            hit
        } catch (e: Throwable) {
            XLog.w("验缓存的 Context 还在不在失败（按「还在」处理）：${e.message}")
            true
        }

    /**
     * 从 `ActivityThread.mAllApplicationThreads` 里挑一个进程取 Application Context。
     *
     * `mAllApplicationThreads` 的值是 `ActivityThread$ActivityClientRecord`，我们只要它的 `mAppContext` 字段。
     */
    private fun fromApplicationThreads(loader: ClassLoader): Context? {
        val atCls = ActivityThreadCompat.classOf(loader) ?: return null
        val thread = ActivityThreadCompat.currentActivityThread(loader) ?: return null

        // ActivityThread 的字段在部分 ROM 上叫 mAllApplicationThreads，早期叫 mAllApplications
        for (fieldName in listOf(
            "mAllApplicationThreads",
            "mAllApplications",
        )) {
            val map = Reflect.fieldValue(thread, atCls, fieldName) ?: continue
            return contextsFromContainer(map) ?: continue
        }
        return null
    }

    /** 从一个容器（ArrayMap / List）里找出第一个能取到 Context 的元素。 */
    private fun contextsFromContainer(container: Any): Context? {
        val values: Collection<*> =
            when (container) {
                is Map<*, *> -> container.values
                is Collection<*> -> container
                else -> return null
            }

        // 容器里存的可能是 ActivityClientRecord（有 mAppContext），也可能直接是 Context
        for (v in values) {
            if (v == null) continue
            if (v is Context) return v
            val c = appContextOf(v)
            if (c != null) return c
        }
        return null
    }

    /** 从一个 `ActivityClientRecord` 之类的东西上读 `mAppContext`。 */
    private fun appContextOf(record: Any): Context? {
        val cls = record.javaClass
        for (fieldName in listOf("mAppContext", "mContext", "appContext")) {
            val v = Reflect.fieldValue(record, cls, fieldName)
            if (v is Context) return v
        }
        return null
    }

    /**
     * 兜底：`ActivityThread.getSystemContext()`，返回的 Context `mAppThread == null`，
     * 因此 ContentResolver 一用就 NPE、`startActivity` 也会被拒。
     */
    private fun fromGetSystemContext(loader: ClassLoader): Context? {
        return try {
            val atCls = ActivityThreadCompat.classOf(loader) ?: return null
            val thread = ActivityThreadCompat.currentActivityThread(loader) ?: return null
            Reflect.call(thread, "getSystemContext") as? Context
        } catch (e: Throwable) {
            XLog.w("调 ActivityThread.getSystemContext() 失败", e)
            null
        }
    }
}
