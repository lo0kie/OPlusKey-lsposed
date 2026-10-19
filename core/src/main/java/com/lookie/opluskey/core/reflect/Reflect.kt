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

    /**
     * 「这个名字查不到」的记时（键同 [classCache] 里那一把：就是一个类名）—— 负命中**会过期**。
     *
     *  类可能只是**这一刻**还加载不了（挂点太早 / loader 还没就绪），永久记住「没有」会让那个
     *  注入点一直失效到重启。出窗后再查一遍，查到了就把负命中覆盖掉。
     */
    private val missAt: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /** [missAt] 的键：`loader` 的身份 + 类名 —— 同名类在不同 loader 里是两回事。 */
    private fun missKey(
        loader: ClassLoader?,
        name: String,
    ): String = "${System.identityHashCode(loader)}#$name"

    /** 负命中的重试窗口（毫秒）。 */
    private const val MISS_RETRY_MS = 30_000L

    /** [signatureScore] 的三档：类型对不上 / 只差装箱 / 逐个相等。 */
    private const val SIGNATURE_INCOMPATIBLE = 0
    private const val SIGNATURE_BOXED = 1
    private const val SIGNATURE_EXACT = 2

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
    fun findClass(
        loader: ClassLoader?,
        name: String,
    ): Class<*>? {
        val bucket =
            synchronized(classCache) {
                classCache.getOrPut(loader) { ConcurrentHashMap() }
            }
        val hit = bucket[name]
        if (hit != null) {
            if (hit !== NOT_FOUND) {
                @Suppress("UNCHECKED_CAST")
                return hit as Class<*>
            }
            // 负命中要**会过期**：这个类可能只是「这会儿还加载不了」（挂点太早 / loader 还没就绪），
            //  永久记住「没有」会让那个注入点一直失效到重启。窗口内直接返回 null（省一次慢查找），
            //  出窗再查一遍 —— 查到了就把负命中覆盖掉。
            //  负命中按「loader + 类名」记：同名类在两个 loader 里的解析是两件事，
            //  共用一个窗口会让 loader A 的失败压住 loader B 的重试。
            val at = missAt[missKey(loader, name)] ?: 0L
            if (System.nanoTime() / 1_000_000 - at < MISS_RETRY_MS) return null
        }
        val cls = runCatching { Class.forName(name, false, loader) }.getOrNull()
        bucket[name] = (cls ?: NOT_FOUND)
        if (cls == null) {
            missAt[missKey(loader, name)] = System.nanoTime() / 1_000_000
        } else {
            // 键是「loader + 类名」，删也得删同一个键：传裸类名的话这一条永远删不掉，
            //  静态表只涨不落（而且下次同名查找会被一条早已过期的负命中挡住）。
            missAt.remove(missKey(loader, name))
        }
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

    fun method(
        cls: Class<*>,
        name: String,
        vararg params: Class<*>,
    ): Method? =
        cachedValue(exactMethodCache, cls, MethodKey(name, params)) {
            var cur: Class<*>? = cls
            var found: Method? = null
            while (cur != null && cur != Any::class.java) {
                val hit = runCatching { cur.getDeclaredMethod(name, *params) }.getOrNull()
                if (hit != null) {
                    runCatching { hit.isAccessible = true }
                    found = hit
                    break
                }
                cur = cur.superclass
            }
            // 第一段（逐级 `getDeclaredMethod`）要求类型**逐个精确相等**：拿 `Integer::class.java`
            //  去查 `f(int)` 是查不到的 —— 这正是下面这段要兜的坑。
            found ?: methodBySignature(cls, name, params)
                // 最后退一步按名字 + 参数个数匹配，兼容参数类型被擦除 / 泛型化的签名。
                //  注意：用 [methodsOf]（沿类链的 declared 全集，含 hidden）而**不是** `cls.methods`
                //  —— 后者只含 public，framework 里大量 hidden 方法在那里匹配不上。
                ?: pickByArity(cls, name, params.size)
        }

    /**
     * 「名字 + 参数个数」唯一命中时的兜底 —— 类型逐个都对不上时才走到这里（泛型擦除 / 桥接方法）。
     *
     *  **多个候选一律返回 null**：原先这里写的是 `firstOrNull`，等于按反射返回顺序瞎挑一个重载 ——
     *  挑错的后果是 `invoke` 抛 `IllegalArgumentException`，而日志上看起来像「这个方法不存在」，
     *  排查方向直接被带偏。宁可返回 null 让调用方按「本机没有这个方法」降级（五、3 不硬猜）。
     */
    private fun pickByArity(
        cls: Class<*>,
        name: String,
        arity: Int,
    ): Method? {
        val candidates = methodsOf(cls).filter { it.name == name && it.parameterTypes.size == arity }
        if (candidates.size != 1) {
            if (candidates.isNotEmpty()) {
                XLog.w("反射 $name/$arity：${candidates.size} 个候选且类型都对不上 ⇒ 不猜（按没有处理）")
            }
            return null
        }
        return candidates.first().also { runCatching { it.isAccessible = true } }
    }

    /**
     * 按「名字 + 逐个参数类型」在 [methodsOf] 的全集里挑，**原始类型与装箱类型互认**。
     *
     *  为什么需要：`Class.getDeclaredMethod` 的匹配是 `Class` 身份相等 —— `Integer.class != int.class`。
     *  于是两种写法各错一半，且**都静默返回 null**：
     *   · 传 `Integer::class.java`（Kotlin 里 `Int::class.java` 就是它）去查 `f(int)`：查不到；
     *   · 传 `Int::class.javaPrimitiveType` 去查一个形参真的写成 `Integer` 的方法：也查不到。
     *  当前工程里调点都显式写了 `Integer.TYPE`，但那是**每个调用点各自记住的约定**，
     *  漏一个就变成「这条 ROM 没有这个方法」—— 而日志上看起来完全正常。
     *
     *  分两步：[signatureScore] 给每个同名同个数的候选打分，[pickScored] 挑出唯一那个。
     *  打分只有三档（不等 / 只差装箱 / 完全相等），所以「唯一性」是按最高档判的。
     */
    private fun methodBySignature(
        cls: Class<*>,
        name: String,
        params: Array<out Class<*>>,
    ): Method? {
        val scored =
            methodsOf(cls)
                .filter { it.name == name && it.parameterTypes.size == params.size }
                .map { it to signatureScore(it.parameterTypes, params) }
                .filter { it.second > SIGNATURE_INCOMPATIBLE }
        return pickScored(scored)?.also { runCatching { it.isAccessible = true } }
    }

    /**
     * 从「候选 + 分数」里挑一个。
     *
     *  规则：**取分数最高的那一档；那一档只有一个才返回**。
     *
     *  为什么是「那一档只有一个」而不是「只有唯一的候选」：`f(int)` 与 `f(Integer)` 同时在时，
     *  传 `int.class` 进来会得到 `{int: 2分, Integer: 1分}` —— 最高的那档只有 `f(int)` 一个，
     *  该返回它；只有**同分**（传 `Integer.class` 那情形两个都被认成 1 分档）才是真的猜不出来。
     *  早先这里写成「只要不是唯一的候选就返回 null」，会把上面那种本该命中的情形一起判成歧义。
     */
    private fun pickScored(scored: List<Pair<Method, Int>>): Method? {
        if (scored.isEmpty()) return null
        val best = scored.maxOf { it.second }
        val top = scored.filter { it.second == best }
        return if (top.size == 1) top.first().first else null
    }

    /**
     * 一个候选的签名分：`2` = 类型逐个相等，`1` = 只差装箱，`0` = 对不上。
     *
     *  只有在「每个参数都至少到 1」时才可能拿到 2 —— 否则「前两个对、第三个是别的类型」会被
     *  算成 2。
     */
    private fun signatureScore(
        actual: Array<Class<*>>,
        want: Array<out Class<*>>,
    ): Int {
        var exact = true
        for (i in want.indices) {
            if (!sameTypeIgnoringBoxing(actual[i], want[i])) return SIGNATURE_INCOMPATIBLE
            if (actual[i] !== want[i]) exact = false
        }
        return if (exact) SIGNATURE_EXACT else SIGNATURE_BOXED
    }

    /**
     * 两个 `Class` 是否「只是原始类型与装箱类型的区别」。
     *
     *  只认 Java 那八对固定映射（`int`/`Integer` …），**不做**宽化/窄化（`int` 不匹配 `long`）：
     *  宽化匹配会让 `f(int)` 被 `f(long)` 顶上，`invoke` 时按反射规则能过，但语义已经变了。
     *
     *  `void`/`Void` 不成对：参数类型不可能真是 `void`，把它们当同类只会造出假命中。
     */
    private fun sameTypeIgnoringBoxing(
        a: Class<*>,
        b: Class<*>,
    ): Boolean {
        if (a === b) return true
        if (a.isPrimitive == b.isPrimitive) return false
        val primitive = if (a.isPrimitive) a else b
        val wrapper = if (a.isPrimitive) b else a
        return wrapperOf(primitive) === wrapper
    }

    /**
     * 八对原始类型 → 装箱类型；不是原始类型或 `void` 时返回 null。
     *
     *  用 `Int::class.javaObjectType` / `Int::class.javaPrimitiveType` 而不是
     *  `java.lang.Integer::class.java`：后者在 Kotlin 里是**废弃写法**（编译器警告「Use 'kotlin.Int'」），
     *  本工程 `allWarningsAsErrors` 会把它当成错误。`javaObjectType` 取到的正是 `Integer.class`，
     *  `javaPrimitiveType` 取到的正是 `Integer.TYPE` —— 与 Java 那套写法逐字等价。
     */
    private fun wrapperOf(primitive: Class<*>): Class<*>? =
        when (primitive) {
            Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
            Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
            Char::class.javaPrimitiveType -> Char::class.javaObjectType
            Short::class.javaPrimitiveType -> Short::class.javaObjectType
            Int::class.javaPrimitiveType -> Int::class.javaObjectType
            Long::class.javaPrimitiveType -> Long::class.javaObjectType
            Float::class.javaPrimitiveType -> Float::class.javaObjectType
            Double::class.javaPrimitiveType -> Double::class.javaObjectType
            else -> null
        }

    fun field(
        cls: Class<*>,
        name: String,
    ): Field? =
        cachedValue(fieldCache, cls, name) {
            var cur: Class<*>? = cls
            var found: Field? = null
            while (cur != null && cur != Any::class.java) {
                val hit = runCatching { cur.getDeclaredField(name) }.getOrNull()
                if (hit != null) {
                    runCatching { hit.isAccessible = true }
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
    fun methodByNameAndArity(
        cls: Class<*>,
        name: String,
        arity: Int,
    ): Method? =
        cachedValue(arityMethodCache, cls, ArityKey(name, arity)) {
            var cur: Class<*>? = cls
            var found: Method? = null
            while (cur != null && cur != Any::class.java) {
                val candidates =
                    runCatching {
                        cur.declaredMethods.filter { it.name == name && it.parameterTypes.size == arity }
                    }.getOrNull().orEmpty()
                val hit = pickOverload(cur, name, arity, candidates)
                if (hit != null) {
                    runCatching { hit.isAccessible = true }
                    found = hit
                    break
                }
                cur = cur.superclass
            }
            found
        }

    /**
     * 某个**返回类型**的零值 —— 代理 / 钩子里「原方法没返回值可用」时回填用。
     *
     *  为什么必须有这一档：Kotlin 的 `null` 喂给声明了**原始类型**返回值的框架方法，等于在框架侧
     *  拆箱时 NPE（`Boolean b = (Boolean) null` 直接抛）。一个「我们没拿到结果」被升级成
     *  「系统进程崩」，比什么都不做糟得多。
     *
     *  `null` 返回类型的入参（Constructor / 取不到声明）一律给 `null`：那两种情况原本就允许 null。
     */
    fun zeroOf(type: Class<*>?): Any? =
        when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> ' '
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> null
        }

    /** 调用实例方法；任何异常都被吞掉并记录，返回 null。 */
    fun call(
        receiver: Any?,
        method: Method?,
        vararg args: Any?,
    ): Any? {
        if (receiver == null || method == null) return null
        return runCatching { method.invoke(receiver, *args) }
            .onFailure { XLog.w("reflect invoke failed: ${method.name}", it) }
            .getOrNull()
    }

    /** 调用目标类上的方法（按名字查找，自动缓存）。 */
    fun call(
        receiver: Any,
        name: String,
        vararg args: Any?,
    ): Any? {
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
    fun callStatic(
        cls: Class<*>,
        name: String,
        vararg args: Any?,
    ): Any? {
        val m =
            cachedValue(staticMethodCache, cls, ArityKey(name, args.size)) {
                val candidates =
                    methodsOf(cls).filter {
                        it.name == name && it.parameterTypes.size == args.size &&
                            java.lang.reflect.Modifier
                                .isStatic(it.modifiers)
                    }
                pickOverload(cls, name, args.size, candidates)?.also { runCatching { it.isAccessible = true } }
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

    /**
     * 读取实例字段；读不到返回 null。
     *
     *  「没有这个字段」与「字段值真是 null」必须分得开：前者是 ROM 改名 / 挪类，后者是正常值。
     *  所以查找落空与读取抛异常各记一行（按 `类#字段` 去重 —— 这条路可能落在按键热路径上），
     *  否则 ROM 换了字段名之后日志里一行线索都没有（AGENTS.md 五、2）。
     */
    fun fieldValue(
        receiver: Any?,
        cls: Class<*>,
        name: String,
    ): Any? {
        val f = field(cls, name)
        if (f == null) {
            if (reportedMissing.add("field:${cls.name}#$name")) {
                XLog.w("找不到字段：${cls.name}#$name（读值按 null 处理）")
            }
            return null
        }
        return try {
            f.get(receiver)
        } catch (e: Throwable) {
            if (reportedMissing.add("field-err:${cls.name}#$name")) {
                XLog.w("读字段失败：${cls.name}#$name", e)
            }
            null
        }
    }

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
        val hit =
            bucket[key] ?: run {
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

    /**
     * 「名字 + 参数个数」命中**多个重载**时确定地挑一个，并把候选全打进日志。
     *
     *  `declaredMethods` 的顺序在 JVM / ART 上是**未定义**的（同一份代码换个编译版本都可能变），
     *  而这条结果会进缓存、活到进程结束 —— 按「第一个」挑等于把进程的行为绑在一个未定义的顺序上：
     *  这台机器挑到的是对的那个，换台机器可能是另一个签名，表现成「同一个功能时好时坏」。
     *
     *  按「参数类型名」排序后取第一个：至少**同一台机器上稳定、跨版本可比**；候选全打出来，
     *  真挑错时日志里有据可查（AGENTS 五、2 的「说清断在哪一环」）。
     */
    private fun pickOverload(
        owner: Class<*>,
        name: String,
        arity: Int,
        candidates: List<Method>,
    ): Method? {
        if (candidates.size <= 1) return candidates.firstOrNull()
        val sorted = candidates.sortedBy { m -> m.parameterTypes.joinToString(",") { it.name } }
        XLog.w(
            "重载有歧义：${owner.name}#$name（$arity 参数）命中 ${candidates.size} 个，" +
                "按参数类型排序取第一个：${sorted.first().parameterTypes.joinToString(",") { it.name }}" +
                "；候选：${sorted.joinToString(" | ") { m -> m.parameterTypes.joinToString(",") { it.name } }}",
        )
        return sorted.first()
    }

    /** 「方法名 + 参数个数」的键（[methodByNameAndArity] 与 [callStatic] 共用形状，各存各的桶）。 */
    private class ArityKey(
        private val name: String,
        private val size: Int,
    ) {
        private val hash: Int = 31 * name.hashCode() + size

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean = other is ArityKey && name == other.name && size == other.size
    }
}
