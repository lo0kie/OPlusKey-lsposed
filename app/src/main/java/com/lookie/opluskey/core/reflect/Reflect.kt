package com.lookie.opluskey.core.reflect

import com.lookie.opluskey.core.log.XLog
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap

/** 极简反射工具。 */
object Reflect {

    private val methodCache: MutableMap<Class<*>, MutableMap<String, Method?>> =
        Collections.synchronizedMap(IdentityHashMap())

    private val fieldCache: MutableMap<Class<*>, MutableMap<String, Field?>> =
        Collections.synchronizedMap(IdentityHashMap())

    private val methodsCache: MutableMap<Class<*>, MutableMap<String, List<Method>>> =
        Collections.synchronizedMap(IdentityHashMap())

    /**
     * 类名 -> 目标类；不存在时返回 null（不同 ROM 可能缺类，不能直接抛）。
     *
     * `loader` 可空：`Class.getClassLoader()` 对 bootstrap 加载的类返回 null，强制非空会让
     * 调用点写不出 `javaClass.classLoader`（Java 类型不匹配）。传 null 是合法写法 ——
     * `Class.forName(name, false, null)` 即「只查 bootstrap」，查不到一样返回 null。
     */
    fun findClass(loader: ClassLoader?, name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    /** 沿类链收集所有声明的方法（含父类）；用在「不知道方法叫什么」时先探一遍再按名字挑。 */
    fun methodsOf(cls: Class<*>): List<Method> {
        val bucket = bucketOf(methodsCache, cls)
        bucket[""]?.let { return it }
        val out = ArrayList<Method>()
        var cur: Class<*>? = cls
        while (cur != null && cur != Any::class.java) {
            runCatching { out.addAll(cur.declaredMethods) }
            cur = cur.superclass
        }
        bucket[""] = out
        return out
    }

    fun method(cls: Class<*>, name: String, vararg params: Class<*>): Method? {
        val bucket = bucketOf(methodCache, cls)
        val key = "$name(${params.joinToString(",") { it.name }})"
        if (bucket.containsKey(key)) return bucket[key]
        var cur: Class<*>? = cls
        var found: Method? = null
        while (cur != null && cur != Any::class.java) {
            val hit = runCatching { cur.getDeclaredMethod(name, *params) }.getOrNull()
            if (hit != null) {
                hit.isAccessible = true
                found = hit
                break
            }
            cur = cur.superclass
        }
        if (found == null) {
            // 退一步按名字 + 参数个数匹配，兼容参数类型被擦除/泛型化的签名
            found = runCatching {
                cls.methods.firstOrNull { it.name == name && it.parameterTypes.size == params.size }
            }.getOrNull()?.also { it.isAccessible = true }
        }
        bucket[key] = found
        return found
    }

    fun field(cls: Class<*>, name: String): Field? {
        val bucket = bucketOf(fieldCache, cls)
        if (bucket.containsKey(name)) return bucket[name]
        var cur: Class<*>? = cls
        var found: Field? = null
        while (cur != null && cur != Any::class.java) {
            val hit = runCatching { cur.getDeclaredField(name) }.getOrNull()
            if (hit != null) {
                hit.isAccessible = true
                found = hit
                break
            }
            cur = cur.superclass
        }
        bucket[name] = found
        return found
    }

    /**
     * 按「方法名 + 参数个数」查找方法（沿着父类链找第一个声明）。
     *
     * 用在参数类型拿不到、或者 ROM 把形参类型改名（framework 里参数常是内部类）的场景。
     */
    fun methodByNameAndArity(cls: Class<*>, name: String, arity: Int): Method? {
        val bucket = bucketOf(methodCache, cls)
        val key = "$name/$arity"
        if (bucket.containsKey(key)) return bucket[key]
        var cur: Class<*>? = cls
        var found: Method? = null
        while (cur != null && cur != Any::class.java) {
            val hit = runCatching {
                cur.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == arity }
            }.getOrNull()
            if (hit != null) {
                hit.isAccessible = true
                found = hit
                break
            }
            cur = cur.superclass
        }
        bucket[key] = found
        return found
    }

    /** 调用实例方法；任何异常都被吞掉并记录，返回 null。 */
    fun call(receiver: Any?, method: Method?, vararg args: Any?): Any? {
        if (receiver == null || method == null) return null
        return runCatching { method.invoke(receiver, *args) }
            .onFailure { XLog.w("reflect invoke failed: ${method.name}", it) }
            .getOrNull()
    }

    /** 调用目标类上的方法（按名字查找，自动缓存）。 */
    fun call(receiver: Any, name: String, vararg args: Any?): Any? {
        val params = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        return call(receiver, method(receiver.javaClass, name, *params), *args)
    }

    /**
     * 调用静态方法（按名字 + 参数个数查找）。查找走 [methodsOf]（沿类链的 declared 全集，
     * 含私有与 hidden）—— 不用 `cls.methods`：framework 里大量 hidden 方法在那里匹配不上
     * 且静默返回 null，这里补上「没找到就记账」。
     */
    fun callStatic(cls: Class<*>, name: String, vararg args: Any?): Any? {
        val m = methodsOf(cls).firstOrNull {
            it.name == name && it.parameterTypes.size == args.size &&
                java.lang.reflect.Modifier.isStatic(it.modifiers)
        } ?: run {
            XLog.w("找不到静态方法：${cls.name}#$name（${args.size} 参数）")
            return null
        }
        runCatching { m.isAccessible = true }
        return try {
            m.invoke(null, *args)
        } catch (e: Throwable) {
            XLog.w("调静态方法失败：${cls.name}#$name", e)
            null
        }
    }

    /** 读取实例字段。 */
    fun fieldValue(receiver: Any?, cls: Class<*>, name: String): Any? =
        runCatching { field(cls, name)?.get(receiver) }.getOrNull()

    /** 取某个 Class 的缓存桶（同一个 Class 共用一份；用 identity 区分同名不同 ClassLoader 的类）。 */
    private fun <T> bucketOf(
        cache: MutableMap<Class<*>, MutableMap<String, T>>,
        cls: Class<*>,
    ): MutableMap<String, T> = cache.getOrPut(cls) { Collections.synchronizedMap(HashMap()) }
}
