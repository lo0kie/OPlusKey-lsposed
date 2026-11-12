package com.lookie.opluskey.inject

import android.view.KeyEvent
import com.lookie.opluskey.bridge.hookGuarded
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.reflect.Reflect
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * 系统那套「按键观察者」派发（`OplusKeyEventUtil`）—— 归模块管的音量键在这里就不往下发。
 *
 * ## 为什么非在这一层拦
 *
 *  游戏里的音量条有**两个**接收者，各自在系统里注册了观察者，互不相干：
 *
 *  · `com.oplus.gesture`（宿主）的那个 —— 日志 tag `GestureKeyEventManager`；
 *  · `com.oplus.games`（游戏空间）**自己也注册了一个**，它把按键转给 `MultiVolumeManager` 画条。
 *
 *  真机实测：只拦宿主那一个时条照样弹（`17:13:52` 那一次宿主侧已经丢了 DOWN，`52.763`
 *  面板还是 `add view success`，而那一轮里既没有 `GestureKeyEventManager`、也没有
 *  `keyEventCallBack` —— 是**另一条**观察者通道喂的）。两个观察者都从 `system_server` 拿事件，
 *  所以只有在这一层拦才是一次拦干净：宿主那边**不需要**另挂一个（挂了也永远收不到事件）。
 *
 * ## 挂哪两个方法
 *
 *  `OplusKeyEventUtil` 里对观察者的派发有两层：外层 `onKeyEvent(KeyEvent)` 与逐条观察者的
 *  `OnKeyEventObserver#onKeyEvent(KeyEvent)`。哪一层真的被 ROM 走到，本机静态看不出（外层那支
 *  的调用方在别处、可能是反射），所以**两处都挂**：谁先命中谁吞，另一处自然不会再被调到。
 *
 *  两个方法都返回 `void` ⇒ 「吞掉」= 不 `proceed()`、返回 `null`。
 *
 * ## 只丢哪些
 *
 *  三条同时成立才丢：音量加 / 减、物理事件、配置里这颗键
 *  归模块管；模块自己注入的音量键（`deviceId < 0`）与停用的键照常放行 —— 前者是
 *  「音量 + / −」动作注入的那一颗，拦了等于把那个动作也一起废掉（与注入侧 `isSynthetic` 同一条口径）。
 */
internal object KeyObserverHooks {
    /**
     * 派发点的候选 —— 类名与方法名成对登记（都是 `com.android.server.am` 下**没有混淆**的名字）。
     *
     *  加一档 ROM 时若这两处都落空，日志里会各留一行 W，音量键本身不受影响。
     */
    private val DISPATCH_CANDIDATES =
        listOf(
            "com.android.server.am.OplusKeyEventUtil\$OnKeyEventObserver" to "onKeyEvent",
            "com.android.server.am.OplusKeyEventUtil" to "onKeyEvent",
        )

    fun install(
        module: XposedModule,
        loader: ClassLoader,
    ): Int {
        val hooked =
            DISPATCH_CANDIDATES.count { (className, methodName) ->
                hookOne(module, loader, className, methodName)
            }
        if (hooked == 0) {
            XLog.w("按键观察者派发一个都没挂上 —— 游戏里音量键仍会弹游戏空间的音量条（音量键本身不受影响）")
        }
        return hooked
    }

    /** 试挂一个派发点 —— 类 / 方法不在或挂失败都只记一行、返回 `false`。 */
    private fun hookOne(
        module: XposedModule,
        loader: ClassLoader,
        className: String,
        methodName: String,
    ): Boolean {
        val cls = Reflect.findClass(loader, className)
        if (cls == null) {
            XLog.w("没找到按键观察者派发类：$className —— 这一档不装")
            return false
        }
        val method = Reflect.method(cls, methodName, KeyEvent::class.java)
        if (method == null) {
            XLog.w("$className 上没有 $methodName(KeyEvent) —— 这一档不装")
            return false
        }
        val ok = module.hookGuarded(method) { chain -> handle(chain) } != null
        if (ok) XLog.w("已 Hook 按键观察者派发：$className#$methodName")
        return ok
    }

    private fun handle(chain: XposedInterface.Chain): Any? {
        val event = chain.args.getOrNull(0) as? KeyEvent ?: return chain.proceed()
        if (!claimedByModule(event)) return chain.proceed()
        if (event.action == KeyEvent.ACTION_DOWN) {
            XLog.w("按键观察者旁路：音量键归模块管（keyCode=${event.keyCode}）⇒ 不派发给观察者")
        }
        return null
    }

    /** 见类注释「只丢哪些」那三条。 */
    private fun claimedByModule(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyId.KEYCODE_VOLUME_UP && keyCode != KeyId.KEYCODE_VOLUME_DOWN) return false
        // 模块自己注入的那一颗（音量动作）要照常放行，否则那个动作会被自己拦住
        if (event.deviceId < 0) return false
        val keyConfig = ConfigChannel.config.keyForCode(keyCode) ?: return false
        return ConfigChannel.enabledOf(keyConfig.id)
    }
}
