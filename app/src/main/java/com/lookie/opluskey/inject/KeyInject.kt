package com.lookie.opluskey.inject

import android.os.SystemClock
import android.view.KeyEvent
import com.lookie.opluskey.bridge.inject.SystemContext
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.reflect.Reflect

/**
 * 从 **system_server** 里注入一颗按键 —— 内置「截图」动作走的就是这一条（见 `ModuleWire.ACTION_INJECT_KEY`）。
 *
 * ## 为什么不是宿主自己注入
 *
 *  宿主注入 `KEYCODE_SYSRQ` 时 `injectInputEvent` 返回成功、屏幕却没反应（真机：日志里只有
 *  「截图：注入…」一行，没有任何被拒的记录）—— ColorOS 的截图逻辑不认**普通 app 注入**的那一下。
 *  这一份跑在 `system_server` 里，注入身份就是系统本身，与 `input keyevent` 同级。
 *
 * ## 为什么不是 `su` 去跑 `service call color_screenshot`
 *
 *  内置动作不该为了截一张图去要 root：`su` 会弹一次授权、慢半拍，没有 root 的机器上直接不可用。
 *  这条通道既不经过 shell，也不需要任何授权。
 *
 * ## 为什么要 pin 住 `InputManager` 而不是每次 `getInstance`
 *
 *  `injectInputEvent` 只在这一处调，而它在按键路径上。第一次解析成功之后把它记住（失败不记：
 *  类初始化顺序在 `system_server` 早期还不到，那时取不到是**暂时**的，缓存住会让注入永久失效）。
 *
 * 全程不抛：调用点在 AMS 的广播入口，漏一个异常出去就是 system_server 崩。
 */
internal fun injectKeyFromSystem(
    loader: ClassLoader?,
    keyCode: Int,
) {
    if (keyCode <= 0) {
        XLog.w("注入侧注入：键码不合法（$keyCode），跳过")
        return
    }
    //  **必须在别的线程上跑**：下面用的是 `WAIT_FOR_FINISH`（会等事件被系统处理完），
    //  而调用点是 `system_server` 的主线程 —— 在主线程上等一次截图链路，等于把整机挂住一下。
    //  每次注入起一个线程：这是「按一下键」级别的低频动作，不值得为它维护一个常驻线程。
    Thread {
        try {
            //  **截图先走「system_server 内部截图」**：个别机型（PLG110 实测）不认注入的那一下 ——
            //  注入成功、事件也进了 policy，就是不截。`ScreenshotHelper` 是系统自己截图用的那条，
            //  与按键无关，成了就不必再注入（否则会截两次）。
            //  没成（类不在 / 签名不对 / 发起即抛）就照旧走注入，两条都在日志里留判据。
            if (keyCode == KeyId.KEYCODE_SYSRQ &&
                ScreenshotViaHelper.take(loader) { loader?.let(SystemContext::get) }
            ) {
                return@Thread
            }
            val manager = inputManager(loader) ?: return@Thread
            val now = SystemClock.uptimeMillis()
            // 5 参构造是**公开** API（`KeyEvent(downTime, eventTime, action, code, repeat)`）：
            //  这里不需要宿主那份 10 参反射 —— 少一层参数表，也就少一处 ROM 差异。
            //
            //  **不要额外加 `FLAG_FROM_SYSTEM`**：`input keyevent` 注入的 flags 就是 0，而它在
            //  绝大多数机器上都能截 —— 加了反而与 `input` 那条已验过的路不一致。个别机器
            //  （PLG110 实测）注入成功、事件也进了 policy，就是不截；那是那台机器自己的口径，
            //  不为它改主路径（真机口径：这条注入在大多数设备上通用）。
            injectOnce(manager, KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0), keyCode)
            injectOnce(manager, KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0), keyCode)
        } catch (e: Throwable) {
            XLog.w("注入侧注入失败：keyevent:$keyCode", e)
        }
    }.start()
}

/**
 * 下发一次事件，并按**真实的结果码**留判据。
 *
 * `InputManager.injectInputEvent` 返回的是 `InputEventInjectionResult`：
 *
 *  · `0` = `INPUT_EVENT_INJECTION_SUCCEEDED` —— **成功**
 *  · `1` = `INPUT_EVENT_INJECTION_FAILED`
 *  · `-1` = `INPUT_EVENT_INJECTION_PERMISSION_DENIED`
 *  · 其余负数 = 具体原因（`-2` 目标不存在、`-3` 超时…）
 *
 *  老版本返回 boolean（`true` = 成功）。
 *
 * ## 早先这里判反了
 *
 *  原实现把 `0` 当「失败」、`1` 当「成功」，注释还写着「0 = RESULT_FAILED」—— 于是
 *  **真正失败的那一次（返回 1）反而一行日志都不打**：用户看到的是「请注入侧注入」之后
 *  什么都没有，像是注入成功、屏幕没反应。判据方向错了，比没有判据更难查。
 */
private fun injectOnce(
    manager: Any,
    event: KeyEvent,
    keyCode: Int,
) {
    val r = Reflect.call(manager, "injectInputEvent", event, INJECT_MODE_WAIT_FOR_FINISH)
    when {
        r == null -> {
            XLog.w("注入侧注入：injectInputEvent 没返回（方法没匹配上）：keyevent:$keyCode")
        }

        // 老版 boolean：true = 成功
        r is Boolean -> {
            if (!r) XLog.w("注入侧注入被拒（老版 boolean=false）：keyevent:$keyCode")
        }

        r is Int -> {
            if (r != INPUT_EVENT_INJECTION_SUCCEEDED) {
                XLog.w("注入侧注入被拒（结果码 $r）：keyevent:$keyCode")
            }
        }

        else -> {
            XLog.w("注入侧注入：返回值不认识（${r.javaClass.simpleName}）：keyevent:$keyCode")
        }
    }
}

/**
 * 取一个能注入的对象 —— 第一次取到就记住（理由见 [injectKeyFromSystem]）。
 *
 *  只留两个入口，顺序按**实测命中率**排，不按新旧：
 *
 *   1. `InputManagerGlobal.getInstance()` —— Android 14 起的正主（`injectInputEvent` 同名同签名）。
 *      本机（ColorOS / Android 16）实测走的正是这条；
 *   2. `InputManager.getInstance()` —— Android 13 及更早只有它。
 *
 *  **为什么不能只写一条**：13 上根本没有 `InputManagerGlobal`（只写第一条 ⇒ 新机能用、老机静默失效）；
 *  而 14 之后 `InputManager.getInstance()` 变成了包装、本机直接返回 null（只写它同样失效）。
 *  两条合起来才盖住整个版本区间。
 *
 *  试一次的代价只在**第一次**注入：命中之后 `cachedManager` 一直用下去，后续零反射。
 *  全部按名字调：注入侧不能 import 那些 @hide 签名（ROM 一改名就是 `NoClassDefFoundError`）。
 */
private fun inputManager(loader: ClassLoader?): Any? {
    cachedManager?.let { return it }
    val inst =
        pickInputManager(loader) ?: run {
            XLog.w("注入侧注入：两个入口都拿不到可注入的实例，这条路走不通")
            return null
        }
    cachedManager = inst
    return inst
}

/** 依次试的那两个静态入口（类名 → 静态方法名），按实测命中率排序。 */
private val ENTRIES: List<Pair<String, String>> =
    listOf(
        "android.hardware.input.InputManagerGlobal" to "getInstance",
        "android.hardware.input.InputManager" to "getInstance",
    )

private fun pickInputManager(loader: ClassLoader?): Any? =
    ENTRIES.firstNotNullOfOrNull { (clsName, methodName) ->
        val cls = Reflect.findClass(loader, clsName) ?: return@firstNotNullOfOrNull null
        Reflect.callStatic(cls, methodName)?.also {
            XLog.w("注入侧注入：入口用 $clsName.$methodName()")
        }
    }

/** 上一次取到的 `InputManager`（只缓存成功）。 */
@Volatile
private var cachedManager: Any? = null

/**
 * `InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH` —— 等这一下被系统**处理完**。
 *
 *  **与 `input keyevent` 完全一样**（`InputShellCommand` 传的就是它）。
 *  宿主那份用的是 `0`（ASYNC，不等）：那边注入的 home / 返回都正常，但截图一直没反应 ——
 *  除了「普通 app 注入不被认」之外，模式也是当时唯一没对齐的一处，这里按 `input` 的写法来。
 *
 *  三个值：`0` = ASYNC、`1` = WAIT_FOR_RESULT、`2` = WAIT_FOR_FINISH。
 *  早先这里写的是 `1`（WAIT_FOR_RESULT，等「分发到目标窗口」而不是「处理完」）——
 *  而截图这类被 policy 吞掉的事件本来就没有窗口消费，WAIT_FOR_RESULT 会返回
 *  `INPUT_EVENT_INJECTION_FAILED`，看着像注入失败。
 */
private const val INJECT_MODE_WAIT_FOR_FINISH = 2

/** `InputEventInjectionResult.INPUT_EVENT_INJECTION_SUCCEEDED` —— 只有它算注入成功。 */
private const val INPUT_EVENT_INJECTION_SUCCEEDED = 0
