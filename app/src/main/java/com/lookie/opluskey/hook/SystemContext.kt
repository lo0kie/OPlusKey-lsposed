package com.lookie.opluskey.hook

import android.content.Context
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog

/** 在 `system_server` 里拿一个真正可用的 `Context`。 */
internal object SystemContext {

    /**
     * 缓存「真正可用」的那一份（来自 `mAllApplicationThreads`，`mAppThread` 有值）；
     * `getSystemContext()` 那份是降级品（`ContentResolver` 一用就 NPE），每次现取、不缓存。
     *
     * 注入阶段还没有 App attach 过、`mAllApplicationThreads` 为空，故调用方不要在安装阶段调这里，
     * 应拿 lambda 推迟到真正要用 Context 的那一刻。
     */
    @Volatile
    private var cached: Context? = null

    /** 拿一个可用 Context。拿不到返回 null（调用方要能容忍）。 */
    fun get(loader: ClassLoader): Context? {
        cached?.let { return it }

        // 首选：ActivityThread 里任一进程的 Application Context（mAppThread 有值）
        fromApplicationThreads(loader)?.let {
            cached = it
            XLog.w("拿到 system_server 下的可用 Context（来源：mAllApplicationThreads）")
            return it
        }

        // 次选：getSystemContext()（降级品，只能 getSystemService）—— 不缓存
        fromGetSystemContext(loader)?.let {
            XLog.w(
                "只拿到 getSystemContext() 的 Context —— 它没有 mAppThread，" +
                    "ContentResolver / startActivity 会失败（**不缓存**，下次再试第①条）",
            )
            return it
        }

        XLog.w("拿不到任何 system_server Context（依赖 Context 的功能会跳过）")
        return null
    }

    /**
     * 从 `ActivityThread.mAllApplicationThreads` 里挑一个进程取 Application Context。
     *
     * `mAllApplicationThreads` 的值是 `ActivityThread$ActivityClientRecord`，我们只要它的 `mAppContext` 字段。
     */
    private fun fromApplicationThreads(loader: ClassLoader): Context? {
        val atCls = Reflect.findClass(loader, "android.app.ActivityThread") ?: return null
        val thread = Reflect.callStatic(atCls, "currentActivityThread") ?: return null

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
        val values: Collection<*> = when (container) {
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
            val atCls = Reflect.findClass(loader, "android.app.ActivityThread") ?: return null
            val thread = Reflect.callStatic(atCls, "currentActivityThread") ?: return null
            Reflect.call(thread, "getSystemContext") as? Context
        } catch (e: Throwable) {
            XLog.w("调 ActivityThread.getSystemContext() 失败", e)
            null
        }
    }
}
