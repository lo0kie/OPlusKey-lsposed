package com.lookie.opluskey.bridge.host

import android.content.Context
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.reflect.Reflect

/** 按键注入 —— 用 `InputManager.injectInputEvent` 下发一对 DOWN / UP。 */
internal class KeyInjector(
    /** 借来的宿主 Context —— 注入接口从它的 ClassLoader 里解析，权限也靠它的 uid。 */
    private val context: () -> Context?,
) {
    /** 注入一个按键（DOWN + UP 成对）；任何失败只记日志 —— 最坏是这一次没注入成功。 */
    fun inject(keyCode: Int) {
        try {
            injectOrThrow(keyCode)
        } catch (e: Throwable) {
            XLog.w("注入按键失败：keyevent:$keyCode", e)
        }
    }

    /** 真正干活的那一层 —— 失败向上抛，由公开入口兜住。 */
    private fun injectOrThrow(keyCode: Int) {
        val h = handle() ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val down = construct(h, ACTION_DOWN, keyCode, now) ?: return
        val up = construct(h, ACTION_UP, keyCode, now) ?: return
        // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        //  **看返回值**：新版 `injectInputEvent` 返回 int（0 = 失败），被拒时**不抛异常** ——
        //  只判「没抛」会把拒绝当成注入成功，表现为「动作执行了、屏幕没反应」且日志无痕。
        injectOnce(h, down, keyCode)
        injectOnce(h, up, keyCode)
    }

    /** 下发一次事件，并对「被拒」留一行判据（返回值的语义见 [injectOrThrow] 里那条注释）。 */
    private fun injectOnce(
        h: Handle,
        event: Any,
        keyCode: Int,
    ) {
        val r = Reflect.call(h.manager, h.inject, event, INJECT_MODE_ASYNC)
        when (r) {
            null -> XLog.w("注入按键：injectInputEvent 没返回（按已下发处理）：keyevent:$keyCode")
            false -> XLog.w("注入按键被系统拒（injectInputEvent=false）：keyevent:$keyCode")
            0 -> XLog.w("注入按键被系统拒（RESULT_FAILED）：keyevent:$keyCode")
        }
    }

    /**
     * 按参数表造一个 `KeyEvent`；造失败返回 null（[invokeConstructor] 已经记过日志）。
     *
     * 三张参数表对应三档构造器，**参数个数由解析时定下来**，这里不再判断。
     */
    private fun construct(
        h: Handle,
        action: Int,
        keyCode: Int,
        now: Long,
    ): Any? =
        invokeConstructor(
            h.ctor,
            *when (h.argc) {
                10 -> arrayOf<Any?>(now, now, action, keyCode, 0, 0, VIRTUAL_KEYBOARD, 0, 0, SOURCE_KEYBOARD)
                6 -> arrayOf<Any?>(now, now, action, keyCode, 0, 0)
                else -> arrayOf<Any?>(now, now, action, keyCode, 0)
            },
        )

    /**
     * 取「注入句柄」—— `IInputManager` 代理 + `KeyEvent` 构造器 + `injectInputEvent`。
     *
     * 这三样都是定值（框架类名、方法名、签名都不随按键变），原来每次注入都要重新解析一遍：
     * 三次 `Class.forName`、两次静态方法扫描、一次 `constructors` 数组 + 三次按签名线性扫描、
     * 两次 `Reflect.method`。现在只解析一次，之后是一次字段读。
     *
     * 两条口径：
     *  · **只缓存成功**。失败不记住 —— 早期 `context()` 还拿不到（返回 null）时会走系统
     *    ClassLoader，那时解析不出来是**暂时**的；把失败缓存下来会让整个进程再也不注入。
     *  · 缓存里带上解析时用的 [ClassLoader] 身份：`context()` 后来换成宿主的 loader 时重新解析，
     *    不让「先来的那份」一直占位。
     */
    private fun handle(): Handle? {
        val loader = classLoader()
        val cached = cachedHandle
        if (cached != null && cached.loader === loader) return cached
        return build(loader)?.also { cachedHandle = it }
    }

    private fun build(loader: ClassLoader): Handle? {
        val manager = inputManager(loader) ?: return null
        val keyEventCls =
            Reflect.findClass(loader, Framework.Calls.KEY_EVENT) ?: run {
                //  静默 return null 的话，「按了没反应」时根本看不出断在这一环（五、2）。
                XLog.w("找不到 ${Framework.Calls.KEY_EVENT}，keyevent 注入这条路走不通")
                return null
            }

        val ctors = keyEventCls.constructors
        // 按**参数类型**挑，不能只数个数：同一个类里有第二个 10 参构造器时，
        // 按个数挑到的可能是另一个（参数序完全不同），deviceId 会静默变成 0 ——
        // 而 SideKeyInterceptor.isSynthetic 正是靠 deviceId 认自己的重注入事件，
        // 认不出来就是自持循环（见 docs/device-findings.md 十一）。
        val ctor10 = ctors.firstOrNull { it.matchesSignature(LONG, LONG, INT, INT, INT, INT, INT, INT, INT, INT) }
        val ctor6 = ctors.firstOrNull { it.matchesSignature(LONG, LONG, INT, INT, INT, INT) }
        val ctor5 = ctors.firstOrNull { it.matchesSignature(LONG, LONG, INT, INT, INT) }
        val ctor: java.lang.reflect.Constructor<*>
        val argc: Int
        when {
            ctor10 != null -> {
                XLog.d("用 10 参构造器造 KeyEvent（metaState=0，source=SOURCE_KEYBOARD）")
                ctor = ctor10
                argc = 10
            }

            ctor6 != null -> {
                XLog.d("用 6 参构造器造 KeyEvent（第 6 参按 metaState 理解，给 0）")
                ctor = ctor6
                argc = 6
            }

            ctor5 != null -> {
                XLog.w("只有 5 参构造器，注入可能不生效")
                ctor = ctor5
                argc = 5
            }

            else -> {
                XLog.w("KeyEvent 构造器没找到，keyevent 注入这条路走不通")
                return null
            }
        }

        val inject =
            Reflect.method(
                manager.javaClass,
                Framework.Calls.INJECT_INPUT_EVENT,
                Reflect.findClass(loader, Framework.Calls.INPUT_EVENT) ?: run {
                    XLog.w("找不到 ${Framework.Calls.INPUT_EVENT}，keyevent 注入这条路走不通")
                    return null
                },
                Int::class.javaPrimitiveType!!,
            )
                ?: Reflect.methodByNameAndArity(manager.javaClass, Framework.Calls.INJECT_INPUT_EVENT, 2)
                ?: run {
                    XLog.w("InputManager.injectInputEvent 没找到，keyevent 注入这条路走不通")
                    return null
                }

        return Handle(loader, manager, inject, ctor, argc)
    }

    /**
     * 取 InputManager。system_server 里没有现成的 `getSystemService`，走
     * `ServiceManager.getService(Framework.Calls.SERVICE_INPUT)` → `IInputManager.asInterface`；
     * 链路每个版本都可能微调，逐级降级、每级失败都写日志。
     */
    private fun inputManager(loader: ClassLoader): Any? {
        val serviceManager =
            Reflect.findClass(loader, Framework.Calls.SERVICE_MANAGER) ?: run {
                XLog.w("找不到 ServiceManager")
                return null
            }
        val binder =
            Reflect.callStatic(serviceManager, Framework.Calls.GET_SERVICE, Framework.Calls.SERVICE_INPUT) ?: run {
                XLog.w("ServiceManager 里没有 input 服务")
                return null
            }
        val stub =
            Reflect.findClass(loader, Framework.Calls.I_INPUT_MANAGER_STUB) ?: run {
                XLog.w("找不到 IInputManager\$Stub")
                return null
            }
        return Reflect.callStatic(stub, Framework.Calls.AS_INTERFACE, binder)
    }

    /**
     * 解析出来的注入句柄（只缓存成功的那份）。
     *
     * 带 [loader] 是为了「后来换成了另一个 ClassLoader 就重新解析」——见 [handle]。
     */
    private class Handle(
        val loader: ClassLoader,
        val manager: Any,
        val inject: java.lang.reflect.Method,
        val ctor: java.lang.reflect.Constructor<*>,
        val argc: Int,
    )

    @Volatile
    private var cachedHandle: Handle? = null

    private fun classLoader(): ClassLoader = context()?.classLoader ?: ClassLoader.getSystemClassLoader()

    /**
     * 调一个构造器。
     *
     * 单独抽出来是因为 Kotlin 对 `Constructor.newInstance(vararg Any?)` 的重载解析
     * 在参数类型「全是不一样的基本类型装箱」时会失败，显式传 `Array<Any?>` 最稳。
     */
    private fun invokeConstructor(
        ctor: java.lang.reflect.Constructor<*>,
        vararg args: Any?,
    ): Any? =
        try {
            ctor.newInstance(*args)
        } catch (e: Throwable) {
            XLog.w("构造 KeyEvent 失败", e)
            null
        }

    companion object {
        // 截图那个按键码（`KEYCODE_SYSRQ`）不在这儿：键码只定义在 `core/model/KeyId` 一处（一、5），
        // 取用见 `KeyId.KEYCODE_SYSRQ`。

        /** `KeyEvent.ACTION_DOWN` / `ACTION_UP`（避免为了两个常量去引 framework 类）。 */
        private const val ACTION_DOWN = 0
        private const val ACTION_UP = 1

        /** `InputManager.INJECT_INPUT_EVENT_MODE_ASYNC`。 */
        private const val INJECT_MODE_ASYNC = 0

        private const val SOURCE_KEYBOARD = 0x101

        /**
         * `KeyCharacterMap.VIRTUAL_KEYBOARD`（= -1）—— 10 参构造器的 deviceId 位，
         * 与系统 `input keyevent` 造出来的事件一致。
         *
         * 注意：`SideKeyInterceptor.isSynthetic` 就认这个值把「自己重注入的事件」摘出去，
         * 换构造器时不许改这个数（改了就是自持循环）。
         */
        private const val VIRTUAL_KEYBOARD = -1

        private val LONG = Long::class.javaPrimitiveType!!
        private val INT = Int::class.javaPrimitiveType!!
    }
}

/**
 * 构造器参数类型是否**逐个**等于给定类型。
 *
 * 只数参数个数是不够的：`KeyEvent` 里有 `(JJIIIIIIII)V`，将来若再出现第二个 10 参重载，
 * 按个数挑到的可能就是参数序完全不同的那一个 —— deviceId 位会静默错位
 * （轻则注入的是「看不见的键盘」，重则 `isSynthetic` 认不出自己的重注入事件 ⇒ 自持循环）。
 */
private fun java.lang.reflect.Constructor<*>.matchesSignature(vararg types: Class<*>): Boolean {
    val actual = parameterTypes
    if (actual.size != types.size) return false
    for (i in types.indices) if (actual[i] != types[i]) return false
    return true
}
