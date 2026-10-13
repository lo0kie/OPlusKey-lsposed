package com.lookie.opluskey.hook

import android.content.Context
import com.lookie.opluskey.Reflect
import com.lookie.opluskey.XLog

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

    /** 放行（passthrough / native）用：拦了这个键又决定不拦，把原始按键原样重注入一次让它照常走。 */
    fun injectForForward(keyCode: Int) {
        try {
            injectOrThrow(keyCode)
        } catch (e: Throwable) {
            XLog.w("放行注入失败：keyCode=$keyCode", e)
        }
    }

    /** 真正干活的那一层 —— 失败向上抛，由两个公开入口各自兜住。 */
    private fun injectOrThrow(keyCode: Int) {
        val im = inputManager() ?: run {
            XLog.w("拿不到 InputManager，keyevent:$keyCode 跳过")
            return
        }
        val keyEventCls = Reflect.findClass(classLoader(), "android.view.KeyEvent") ?: return

        val ctors = keyEventCls.constructors
        val now = android.os.SystemClock.uptimeMillis()
        val down: Any
        val up: Any
        val ctor10 = ctors.firstOrNull { it.parameterTypes.size == 10 }
        val ctor6 = ctors.firstOrNull { it.parameterTypes.size == 6 }
        val ctor5 = ctors.firstOrNull { it.parameterTypes.size == 5 }
        when {
            ctor10 != null -> {
                XLog.d("用 10 参构造器造 KeyEvent（metaState=0，source=SOURCE_KEYBOARD）")
                down = invokeConstructor(
                    ctor10, now, now, ACTION_DOWN, keyCode, 0, 0,
                    VIRTUAL_KEYBOARD, 0, 0, SOURCE_KEYBOARD,
                ) ?: return
                up = invokeConstructor(
                    ctor10, now, now, ACTION_UP, keyCode, 0, 0,
                    VIRTUAL_KEYBOARD, 0, 0, SOURCE_KEYBOARD,
                ) ?: return
            }
            ctor6 != null -> {
                XLog.d("用 6 参构造器造 KeyEvent（第 6 参按 metaState 理解，给 0）")
                down = invokeConstructor(ctor6, now, now, ACTION_DOWN, keyCode, 0, 0) ?: return
                up = invokeConstructor(ctor6, now, now, ACTION_UP, keyCode, 0, 0) ?: return
            }
            ctor5 != null -> {
                XLog.w("只有 5 参构造器，注入可能不生效：keyevent:$keyCode")
                down = invokeConstructor(ctor5, now, now, ACTION_DOWN, keyCode, 0) ?: return
                up = invokeConstructor(ctor5, now, now, ACTION_UP, keyCode, 0) ?: return
            }
            else -> {
                XLog.w("KeyEvent 构造器没找到，keyevent:$keyCode 跳过")
                return
            }
        }

        val inject = Reflect.method(
            im.javaClass, "injectInputEvent",
            Reflect.findClass(classLoader(), "android.view.InputEvent") ?: return,
            Int::class.javaPrimitiveType!!,
        )
            ?: Reflect.methodByNameAndArity(im.javaClass, "injectInputEvent", 2)
            ?: run {
                XLog.w("InputManager.injectInputEvent 没找到，keyevent:$keyCode 跳过")
                return
            }

        // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        Reflect.call(im, inject, down, INJECT_MODE_ASYNC)
        Reflect.call(im, inject, up, INJECT_MODE_ASYNC)
    }

    /**
     * 取 InputManager。system_server 里没有现成的 `getSystemService`，走
     * `ServiceManager.getService("input")` → `IInputManager.asInterface`；
     * 链路每个版本都可能微调，逐级降级、每级失败都写日志。
     */
    private fun inputManager(): Any? {
        val loader = classLoader()
        val serviceManager = Reflect.findClass(loader, "android.os.ServiceManager") ?: run {
            XLog.w("找不到 ServiceManager")
            return null
        }
        val binder = Reflect.callStatic(serviceManager, "getService", "input") ?: run {
            XLog.w("ServiceManager 里没有 input 服务")
            return null
        }
        val stub = Reflect.findClass(loader, "android.hardware.input.IInputManager\$Stub") ?: run {
            XLog.w("找不到 IInputManager\$Stub")
            return null
        }
        return Reflect.callStatic(stub, "asInterface", binder)
    }

    private fun classLoader(): ClassLoader =
        context()?.classLoader ?: ClassLoader.getSystemClassLoader()

    /**
     * 调一个构造器。
     *
     * 单独抽出来是因为 Kotlin 对 `Constructor.newInstance(vararg Any?)` 的重载解析
     * 在参数类型「全是不一样的基本类型装箱」时会失败，显式传 `Array<Any?>` 最稳。
     */
    private fun invokeConstructor(
        ctor: java.lang.reflect.Constructor<*>,
        vararg args: Any?,
    ): Any? = try {
        ctor.newInstance(*args)
    } catch (e: Throwable) {
        XLog.w("构造 KeyEvent 失败", e)
        null
    }

    companion object {
        /** 截图按键码（`KEYCODE_SYSRQ`）—— 替代 KSU 那条 `service call color_screenshot 1`。 */
        const val KEYCODE_SYSRQ = 120

        /** `KeyEvent.ACTION_DOWN` / `ACTION_UP`（避免为了两个常量去引 framework 类）。 */
        private const val ACTION_DOWN = 0
        private const val ACTION_UP = 1

        /** `InputManager.INJECT_INPUT_EVENT_MODE_ASYNC`。 */
        private const val INJECT_MODE_ASYNC = 0

        private const val SOURCE_KEYBOARD = 0x101

        /**
         * `KeyCharacterMap.VIRTUAL_KEYBOARD`（= -1）—— 10 参构造器的 deviceId 位，
         * 与系统 `input keyevent` 造出来的事件一致。
         */
        private const val VIRTUAL_KEYBOARD = -1
    }
}
