package com.lookie.opluskey.inject.key

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.reflect.Reflect
import com.lookie.opluskey.inject.wire.ConfigChannel
import io.github.libxposed.api.XposedInterface
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

    /** 类名只在 `Framework.Calls` 写一次（一、7）：这里只取用，不认名字。 */
    private const val CLASS_NAME = Framework.Calls.SCREENSHOT_HELPER

    /** 见 [completionConsumer]：常量哈希对「只等于自己」的代理是合法值。 */
    private const val CONSUMER_HASH = 0x0f1e2d3

    /** 签名只在第一次调用时打一遍（这条会被每一次截图打到，刷屏没意义）。 */
    @Volatile
    private var signaturesLogged = false

    fun take(
        loader: ClassLoader?,
        contextProvider: () -> Context?,
    ): Boolean {
        //  找类 / 找构造器 / 找方法这三步各有各的失败，都只记日志并返回 null ——
        //  收进 `resolve` 是为了让这里只剩「发起 + 收尾」两件事，也只为它一处写那道兜 Throwable 的 try。
        //  **`resolve` 自己也要兜住**：它里面有 `constructors` / `newInstance`，抛的是 `Throwable`
        //  （含 `Error`），漏出去就是 system_server 崩 —— 与下面 `invoke` 同一道防线。
        val ready =
            try {
                resolve(loader, contextProvider)
            } catch (e: Throwable) {
                XLog.w("截图（Helper）：准备实例时抛错（${e.javaClass.simpleName}）：${e.message}")
                null
            } ?: return false
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
                it.name == Framework.Calls.TAKE_SCREENSHOT && it.parameterCount == 3 &&
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
        val methods = cls.declaredMethods.filter { it.name == Framework.Calls.TAKE_SCREENSHOT }
        XLog.w("截图（Helper）：takeScreenshot = ${methods.joinToString(" | ") { it.toGenericString() }}")
        //  **不去读它的 `SCREENSHOT_*` 常量**：读静态成员会触发这个类的 `<clinit>`，而 system_server
        //  里宿主类初始化失败就被标 erroneous、之后任何访问都是 `NoClassDefFoundError`（AGENTS.md
        //  五之二、1）。本方法只做诊断，不该为此付那条代价 —— 真要核对常量，用法是先按签名调通
        //  （那时类已经初始化过），失败日志会带上 `type` 的取值。
    }

    /**
     * `Consumer<Uri>` 的实现 —— 系统截完图会把落盘的 Uri 回给它。
     *
     * 用动态代理而不是真写一个类：`Consumer` 是 `java.util.function` 里的接口，写个实现要
     * import 它没问题，但代理不必关心泛型擦除后的签名差异，也不会多一个类。
     * 回调这一行是**这条路过没过的最终判据**：发起成功但没回调 = 系统那边没截。
     */
    private fun completionConsumer(): Any {
        //  `equals` 要按**自己这个代理**比，而 lambda 里拿不到「刚建出来的那个」—— 用一个
        //  一次性持有者把它接出来（回调是异步的，建完之后才会被系统调到）。
        val self = arrayOfNulls<Any>(1)
        val proxy =
            Proxy.newProxyInstance(
                Consumer::class.java.classLoader,
                arrayOf(Consumer::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "accept" -> {
                        XLog.w("截图（Helper）：回调拿到结果 ${args?.firstOrNull()}")
                        null
                    }

                    //  `hashCode` / `equals` / `toString` 也会走到这个 handler 上。一律返回 `null`
                    //  的话，系统侧（这一步跑在 system_server）拿 `int` / `String` 一拆箱就是 NPE ——
                    //  而它跟这次截图成不成没关系。哈希给个常量是合法的：这个代理只等于它自己。
                    "hashCode" -> {
                        CONSUMER_HASH
                    }

                    "equals" -> {
                        args?.firstOrNull() === self[0]
                    }

                    "toString" -> {
                        "OPlusKeyScreenshotConsumer"
                    }

                    else -> {
                        null
                    }
                }
            }
        self[0] = proxy
        return proxy
    }
}

/**
 * ROM 自己判的「电源 + 音量」组合键 —— 这一对键**都归模块管**时，把这次判定吞掉。
 *
 *  为什么要在输入层再拦一次：侧键接管点落在 policy 的策略类上，而组合键在**输入层**
 *  （`com.android.server.input` 那一层）就判完并派发下去了，比 policy 早。policy 上吞掉的
 *  那一下对它不可见 —— 表现就是「两颗键都加到自定义按键里了，一起按还是截图」。
 *
 * ## 判据：**接管即归我们**（用户口径）
 *
 *  两颗键都在配置里、且都开着接管 ⇒ 吞。不再额外要求「这一对恰好配了一档组合」：
 *  「接管」这个动作的语义就是「这套键归模块」，系统那一下该让位 —— 接管了却还让 ROM 截一张图，
 *  用户读到的是「接管没生效」。
 *
 *  想让系统那一下照常发生，唯一的出口是把这两颗键里的一颗停用（或关掉总闸）——
 *  「这一档没绑动作」不算出口：留空只表示「这一档什么都不做」，不代表这一次要交回 ROM。
 *
 *  返回值按原方法的**声明返回类型**回填（[swallowValueOf]）：`void` 用 `null`（框架里 `null`
 *  就是「吞掉」），`boolean` 按「已消费」给 `true`，其余原始类型给零值 —— 给原始类型返回点
 *  返回 `null` 会在框架侧拆箱时 NPE，把「这一下不生效」升级成 system_server 崩溃。
 */
internal object ChordGuard {
    /** ROM 会判的两组：电源 + 音量下（截图）、电源 + 音量上（可配的那一档）。 */
    private val PAIRS =
        listOf(
            intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_DOWN),
            intArrayOf(KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP),
        )

    /**
     * 一次 `handleKeyGesture` 调用。
     *
     * @return 吞掉时按 [swallowValueOf] 回填；不吞时原样放行
     */
    fun handle(chain: XposedInterface.Chain): Any? {
        //  键码表是这一层的唯一入口判据：两种参数形态都要认（见 [codesOf]），一只都取不到就当
        //  不是组合键，原样放行。
        val codes = codesOf(chain.args)
        if (codes == null) {
            val method = (chain.executable as? java.lang.reflect.Method)?.toGenericString()
            XLog.w("组合键手势：两种参数形态都取不到键码（$method）")
            return chain.proceed()
        }
        //  判据级：ROM 这一次交给 `handleKeyGesture` 的键码表原样打出来。
        //  下面那条 pair 判据要求「两颗键都在表里」；机型不同、ROM 改版时这一层给出的表也
        //  不一样（有的只报当前按下的那一颗），那时整组都拦不到 —— 这一行是认出那种形态的
        //  唯一证据，排查「某一组组合不生效、另一组正常」时先看它。
        XLog.w("组合键手势：keycodes=[${codes.joinToString(",")}]")
        val pair = PAIRS.firstOrNull { p -> p.all { it in codes } } ?: return chain.proceed()
        val keys =
            try {
                ownedPair(pair)
            } catch (e: Throwable) {
                //  读配置失败按「不归模块管」处理：这一下交给 ROM，与「读不到配置就走默认值」同一口径。
                XLog.w("组合键：判定失败（放行给 ROM）：${e.javaClass.simpleName}", e)
                null
            } ?: return chain.proceed()
        //  判据行：这一行出现就说明「组合键确实走到输入层」且被拦下了 —— 排查时先看它。
        XLog.w("组合键（${pair.joinToString("+")}）：两颗键都归模块管 ⇒ 吞掉 ROM 的这次判定")
        return swallowValueOf(chain, keys.joinToString("+") { it.id.id })
    }

    /**
     * 这一对键是否**都归模块管** —— 是则返回它们对应的键配置。
     *
     *  「都归模块管」= 都在配置里、且都开着接管（`enabledOf` 里已含主开关：总闸关着时一律 false，
     *  行为与原生一致）。这一条成立就吞，不再看「有没有配这一组组合」—— 理由见类注释。
     *
     *  组合那一档**有没有绑动作不参与判断**：配了组合就是「这两颗键交给模块」，
     *  某一格留空只表示「这一档什么都不做」，不该把这一次又交回 ROM。
     */
    private fun ownedPair(pair: IntArray): List<KeyConfig>? {
        val config = ConfigChannel.config
        val found = ArrayList<KeyConfig>(pair.size)
        for (code in pair) {
            val key = config.keyForRawCode(code) ?: return null
            //  `enabledOf` 里已含主开关：总闸关着时一律 false，行为与原生一致。
            if (!ConfigChannel.enabledOf(key.id)) return null
            found += key
        }
        return found
    }
}

/**
 * 「吞掉这一次调用」时该返回什么 —— **按原方法的声明返回类型**给，不能一律 `null`。
 *
 *  见 `Reflect.zeroOf` 的注释：`null` 喂给原始类型返回点就是拆箱 NPE。`void` 那一档
 *  `zeroOf` 本来就返回 `null`，正是框架里「吞掉」的含义；`boolean` 单独给 `true`：
 *  我们对这一下的意图就是「已消费」，给 `false` 会让上游把它当成「没处理」继续派发，
 *  等于白吞一次并丢掉这次判定。
 *
 *  这一处是**唯一**口径：`ChordGuard` 与 `KeyHooks.onIntercept` 都走它，两处各写一份时
 *  曾出现「一处返回 null、一处回填零值」，其中一处必然是错的。
 *
 *  @param label 只进日志：这一次吞掉的是哪条路径（组合键 / 侧键接管点）
 */
internal fun swallowValueOf(
    chain: XposedInterface.Chain,
    label: String,
): Any? {
    val type = (chain.executable as? java.lang.reflect.Method)?.returnType
    val value =
        when (type) {
            java.lang.Boolean.TYPE -> true
            else -> Reflect.zeroOf(type)
        }
    //  一次按压一条：这一行同时是「回填成什么」的唯一凭据（返回类型对不上时先看这里）。
    //  惰性：它在**每一次被吞下的按键**上都走一遍，release 里整段被编译期消灭 ——
    //  `$value` 是任意对象，串在调用点先拼出来的话每条按键都要白调一次 `toString`。
    XLog.d { "$label：按返回类型 $type 回填 $value" }
    return value
}

/** 从实参里挑出第一个 `IntArray`（键码表）—— 热路径，不要 `filterIsInstance`。 */
private fun firstIntArray(args: List<Any?>): IntArray? {
    for (a in args) if (a is IntArray) return a
    return null
}

/**
 * 从实参里取键码表 —— **两种参数形态都要认**。
 *
 *  · 直接给表：`handleKeyGesture(int[] keycodes, …)`（或某个带 `int[]` 的重载）；
 *  · 给事件对象：`handleKeyGesture(AidlKeyGestureEvent, IBinder)` —— 那张表在事件的
 *    `keycodes` 字段里（AOSP 的 `android.hardware.input.AidlKeyGestureEvent` 就是这一格）。
 *
 *  为什么必须认第二种：真机上两个重载**交替**被调（日志里 `keycodes=[…]` 与「取不到键码」
 *  成对出现），只认 `int[]` 的话，事件对象那一路会把判定整支交给 ROM —— 表现就是
 *  「两颗键都接管了，一起按还是截图」。
 *
 *  只对名字里带 `KeyGesture` 的实参尝试读字段：同一份实参表里还有 `IBinder` 之类，
 *  对它们逐个反射只会刷一串「找不到字段」。
 */
private fun codesOf(args: List<Any?>): IntArray? {
    firstIntArray(args)?.let { return it }
    for (a in args) {
        if (a == null || a is IntArray) continue
        if (!a.javaClass.simpleName.contains("KeyGesture")) continue
        (Reflect.fieldValue(a, a.javaClass, "keycodes") as? IntArray)?.let { return it }
    }
    return null
}
