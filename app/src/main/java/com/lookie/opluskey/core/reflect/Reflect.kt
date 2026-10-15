package com.lookie.opluskey.core.reflect

import com.lookie.opluskey.core.log.XLog
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 极简反射工具。
 *
 * 这一层几乎每个调用点都在热路径上（注入侧每次按键、宿主侧每次动作），所以**查找结果一律缓存**：
 * 类 / 方法 / 字段都只在第一次真正解析，之后是一次 map 查找。
 *
 * **落空也缓存**（用 [NOT_FOUND] 哨兵表示）—— 「这个框架类在这台机器上没有」同样是热路径上的
 * 结论（ROM 改名后每次按键都走到那一支），不缓存就每次都重跑一遍类链遍历。
 *
 * 缓存**没有上限**，这是有意的：键是 `Class` / `ClassLoader` / 成员名，取值集合由「这个模块碰过
 * 哪些框架类」决定，是个几十量级的固定集合，不随运行时间增长（所以这里不需要 LRU）。
 */
object Reflect {

    /**
     * 缓存里代表「查过了，没有」的哨兵。
     *
     *  `ConcurrentHashMap` 不收 null 值，而「没有」必须与「还没查」区分开 —— 前者要直接返回，
     *  后者才去解析。
     */
    private val NOT_FOUND: Any = Any()

    /**
     * 类名 → Class。外层键是 **ClassLoader 的身份**（`IdentityHashMap` 的 `===` 语义）。
     *
     *  为什么必须带 loader：同一个类名被两个 ClassLoader 各加载一次会得到**两个不同的 Class**
     *  （宿主 loader 与 system_server loader 都解析 `android.app.ActivityThread` 就是这个情形）。
     *  只按类名缓存会把两者串起来 —— 拿宿主那份去当 system_server 那份用，是个静默错类型的 bug。
     *
     * `null` 是合法 loader（bootstrap），`IdentityHashMap` 允许 null 键，正好对上
     * `findClass(loader = null, …)` 那条路。
     */
    private val classCache: MutableMap<ClassLoader?, ConcurrentHashMap<String, Any>> =
        java.util.Collections.synchronizedMap(IdentityHashMap())

    /** `method()` 的缓存 —— key 按「名字 + 参数类型**身份**」，不再拼字符串（见 [MethodKey]）。 */
    private val exactMethodCache: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>> = ConcurrentHashMap()

    /** 按「名字 + 参数个数」查找的缓存（[methodByNameAndArity]）。 */
    private val arityMethodCache: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>> = ConcurrentHashMap()

    /** 静态方法查找的缓存（[callStatic]）—— 与上面两个分开：选择口径不同（多一个 static 过滤）。 */
    private val staticMethodCache: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>> = ConcurrentHashMap()

    /** 字段名 → Field。 */
    private val fieldCache: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>> = ConcurrentHashMap()

    /** [methodsOf] 的结果（沿类链的 declared 全集）—— 结果不会是 null，直接存列表。 */
    private val methodsCache: ConcurrentHashMap<Class<*>, List<Method>> = ConcurrentHashMap()

    /**
     * 「方法没找到」那几行日志的去重键（`类#方法/参数个数`）。
     *
     *  这条路在**注入侧热路径**上：ROM 改名 / 挪类之后每条按键都会走到「没找到」这一支，
     *  不去重就是一秒几十行的刷屏。并发集合 —— 那条路径不保证在同一个线程上。
     */
    private val reportedMissing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * 类名 -> 目标类；不存在时返回 null（不同 ROM 可能缺类，不能直接抛）。
     *
     * `loader` 可空：`Class.getClassLoader()` 对 bootstrap 加载的类返回 null，强制非空会让
     * 调用点写不出 `javaClass.classLoader`（Java 类型不匹配）。传 null 是合法写法 ——
     * `Class.forName(name, false, null)` 即「只查 bootstrap」，查不到一样返回 null。
     *
     * 结果（含「查不到」）按 loader 身份缓存：同名类在不同 loader 下是不同的 Class，不能混用。
     */
    fun findClass(loader: ClassLoader?, name: String): Class<*>? {
        val bucket = synchronized(classCache) {
            classCache.getOrPut(loader) { ConcurrentHashMap() }
        }
        val hit = bucket[name]
        if (hit != null) {
            @Suppress("UNCHECKED_CAST")
            return if (hit === NOT_FOUND) null else hit as Class<*>
        }
        val cls = runCatching { Class.forName(name, false, loader) }.getOrNull()
        bucket[name] = (cls ?: NOT_FOUND)
        return cls
    }

    /** 沿类链收集所有声明的方法（含父类）；用在「不知道方法叫什么」时先探一遍再按名字挑。 */
    fun methodsOf(cls: Class<*>): List<Method> =
        methodsCache.getOrPut(cls) {
            val out = ArrayList<Method>()
            var cur: Class<*>? = cls
            while (cur != null && cur != Any::class.java) {
                runCatching { out.addAll(cur.declaredMethods) }
                cur = cur.superclass
            }
            out
        }

    fun method(cls: Class<*>, name: String, vararg params: Class<*>): Method? =
        cachedValue(exactMethodCache, cls, MethodKey(name, params)) {
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
            found ?: methodsOf(cls)
                .firstOrNull { it.name == name && it.parameterTypes.size == params.size }
                // 退一步按名字 + 参数个数匹配，兼容参数类型被擦除 / 泛型化的签名。
                //  注意：用 [methodsOf]（沿类链的 declared 全集，含 hidden）而**不是** `cls.methods`
                //  —— 后者只含 public，framework 里大量 hidden 方法在那里匹配不上。
                ?.also { runCatching { it.isAccessible = true } }
        }

    fun field(cls: Class<*>, name: String): Field? =
        cachedValue(fieldCache, cls, name) {
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
            found
        }

    /**
     * 按「方法名 + 参数个数」查找方法（沿着父类链找第一个声明）。
     *
     * 用在参数类型拿不到、或者 ROM 把形参类型改名（framework 里参数常是内部类）的场景。
     */
    fun methodByNameAndArity(cls: Class<*>, name: String, arity: Int): Method? =
        cachedValue(arityMethodCache, cls, ArityKey(name, arity)) {
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
            found
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
        val m = method(receiver.javaClass, name, *params)
        if (m == null) {
            // 与 [callStatic] 同一条纪律（AGENTS.md 五、2）：查不到就记账。
            //  这里是**注入侧热路径**（每次按键都可能走到），ROM 改名 / 挪类之后会静默变 no-op
            //  —— 没有这一行就查不出来。按 `类#方法/参数个数` 去重：`method()` 会把 null 也缓存，
            //  否则每次按键都要刷一行。
            if (reportedMissing.add("${receiver.javaClass.name}#$name/${args.size}")) {
                XLog.w("找不到实例方法：${receiver.javaClass.name}#$name（${args.size} 参数）")
            }
            return null
        }
        return call(receiver, m, *args)
    }

    /**
     * 调用静态方法（按名字 + 参数个数查找）。查找走 [methodsOf]（沿类链的 declared 全集，
     * 含私有与 hidden）—— 不用 `cls.methods`：framework 里大量 hidden 方法在那里匹配不上
     * 且静默返回 null，这里补上「没找到就记账」。
     *
     * 命中与落空**都缓存**：[methodsOf] 的线性扫描（framework 类几十上百个方法）原来每次调用
     * 都要重跑一遍，而这条路在每次按键 / 每次动作上。
     *
     * 「没找到」那行**按 `类#方法/参数个数` 去重**：与 [call] 同一条纪律 —— 它同样可能落在
     * 注入侧热路径上，ROM 改名之后不去重就是一秒几十行刷屏。
     */
    fun callStatic(cls: Class<*>, name: String, vararg args: Any?): Any? {
        val m = cachedValue(staticMethodCache, cls, ArityKey(name, args.size)) {
            methodsOf(cls).firstOrNull {
                it.name == name && it.parameterTypes.size == args.size &&
                    java.lang.reflect.Modifier.isStatic(it.modifiers)
            }?.also { runCatching { it.isAccessible = true } }
        }
        if (m == null) {
            if (reportedMissing.add("${cls.name}#$name/${args.size}")) {
                XLog.w("找不到静态方法：${cls.name}#$name（${args.size} 参数）")
            }
            return null
        }
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

    /**
     * 剥到最内层的 cause —— 反射 `invoke()` 抛出的异常被包在 `InvocationTargetException` 里，
     * 而它的 `message` **恒为 null**：只看最外层，日志上只会留下一句「null」，什么都排查不出来。
     *
     *  为什么要带上限：类初始化失败时异常链可能是**环**（`cause` 指回前面某一层），
     *  不设上限就会在这条（可能落在按键热路径上的）调用里转不出来。
     *
     *  原来这段在 `FlexibleWindowBridge` 与 `ProbeChannel` 里各写了一份（逐字相同，只有名字
     *  与那一行 KDoc 不同），十来处内联的同款也在别处各自剥一遍 —— 收在这里一处。
     */
    fun deepestCause(e: Throwable): Throwable {
        var cur = e
        var guard = 0
        while (cur.cause != null && cur.cause !== cur && guard < 8) {
            cur = cur.cause!!
            guard++
        }
        return cur
    }

    /**
     * 「查一次、连落空一起记住」的通用取法。
     *
     *  并发下**允许重复解析**（两个线程同时首次进入同一个 key）：解析本身是幂等的，
     *  `ConcurrentHashMap` 的写是原子的，后写的覆盖先写的，结果一致。
     *  要避免的是另一件事：把「还没查」(null) 与「查过、没有」([NOT_FOUND]) 混为一谈 —— 那会
     *  让一次未命中的竞态被当成「永久没有」，之后再也不去解析。
     */
    private fun <T : Any> cachedValue(
        cache: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>,
        cls: Class<*>,
        key: Any,
        resolve: () -> T?,
    ): T? {
        val bucket = cache.getOrPut(cls) { ConcurrentHashMap() }
        val hit = bucket[key] ?: run {
            val found = resolve()
            bucket[key] = found ?: NOT_FOUND
            return found
        }
        if (hit === NOT_FOUND) return null
        @Suppress("UNCHECKED_CAST")
        return hit as T
    }

    /**
     * `method()` 的缓存键：方法名 + 参数类型**逐个身份比较**。
     *
     *  为什么不用拼出来的字符串：`params.joinToString(",") { it.name }` 每次调用都要建
     *  StringBuilder、调每个 `Class.name`、再建最终串 —— 三次分配落在按键热路径上。
     *  按身份比还顺带修掉一个隐患：旧键是「类型名」，两个 loader 下的同名类型会撞键。
     */
    private class MethodKey(
        private val name: String,
        private val params: Array<out Class<*>>,
    ) {
        private val hash: Int = 31 * name.hashCode() + params.contentHashCode()

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MethodKey) return false
            if (name != other.name) return false
            if (params.size != other.params.size) return false
            // 用 `!==`（身份）而不是 `!=`：Class 的相等本来就是身份，写 `!==` 更直白也更快
            for (i in params.indices) if (params[i] !== other.params[i]) return false
            return true
        }
    }

    /** 「方法名 + 参数个数」的键（[methodByNameAndArity] 与 [callStatic] 共用形状，各存各的桶）。 */
    private class ArityKey(private val name: String, private val size: Int) {
        private val hash: Int = 31 * name.hashCode() + size

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean =
            other is ArityKey && name == other.name && size == other.size
    }
}
