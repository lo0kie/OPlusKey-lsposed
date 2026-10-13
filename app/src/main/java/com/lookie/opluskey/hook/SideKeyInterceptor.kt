package com.lookie.opluskey.hook

import android.os.Handler
import android.view.KeyEvent
import com.lookie.opluskey.XLog
import com.lookie.opluskey.config.Gesture
import com.lookie.opluskey.config.KeyConfig
import com.lookie.opluskey.config.KeyId
import com.lookie.opluskey.gesture.GestureDetector
import io.github.libxposed.api.XposedInterface

/**
 * 一次按键的判定：这个键是不是侧键、该判成什么手势。
 */
internal class SideKeyInterceptor(
    handler: Handler,
    /** 判出手势后的去向（由 `KeyHooks` 注入，实现是 `GestureForwarder`）。 */
    private val forward: (KeyId, Gesture?, Boolean) -> Unit,
) {

    /**
     * 手势判定器 —— 拿到的是 hook 回调里的原始事件，时序是真的，不会被「解冻灌包」搅乱。
     *
     * 线程归属：`interceptKeyBeforeQueueing` 由 InputReader / Dispatcher 线程调，只有那几个计时器
     * 在 main looper 上；线程归属尚未在真机上量清，不要按主线程的假设改这里的同步方式。
     */
    private val detector = GestureDetector(
        handler,
        onGesture = { id, gesture, _, repeat -> forward(id, gesture, repeat) },
        onPassthrough = { id, _ -> forward(id, null, false) },
    )

    /**
     * @return true = 接管（吞掉原方法）；false = 原样放行给系统
     */
    /**
     * 这个事件是不是我们自己重注入的合成事件。
     *
     * 「放行」是「吞掉原始键 + 用 `InputManager` 重注入一对 735」（见 `KeyInjector`），
     * 而重注入的事件会**再走一遍**这条按键路径 ⇒ 再被认领、再判一次单击、再放行一次
     * ⇒ 每 ~100ms 一轮的自持循环（真机实测 2026-10-13，见 `docs/device-findings.md`）。
     * 合成事件带的是虚拟键盘的 `deviceId`（`KeyInjector` 显式传 -1），认它的号即可放行。
     */
    private fun isSynthetic(keyEvent: KeyEvent): Boolean =
        keyEvent.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD ||
            keyEvent.deviceId < 0

    fun handle(point: Framework.InterceptionPoint, chain: XposedInterface.Chain): Boolean {
        if (!ConfigChannel.takeOver) return false

        val config = ConfigChannel.config
        val keyConfig = config.key(KeyId.PLUS)
        detector.doubleClickMs = config.global.doubleClickMs
        // 「双击为无操作时立即触发单击」与双击窗口一样，每次按键前按当前配置刷一遍
        detector.instantSingle = config.global.instantSingle

        return when (point.kind) {
            Framework.InterceptionPoint.Kind.OPLUS_STRATEGY ->
                onStrategyCall(chain, point, keyConfig)

            Framework.InterceptionPoint.Kind.OPLUS_PM_EXT,
            Framework.InterceptionPoint.Kind.AOSP_PM,
            -> onPhoneWindowManagerCall(chain, point, keyConfig)
        }
    }

    /**
     * 策略类路径：喂给判定器，不直接转发。
     *
     * 日志带上 `point` 与 keyCode：接管点是分层候选，同一次物理按压可能被不止一层看到，
     * debug 日志靠这两项定位重复转发。
     */
    private fun onStrategyCall(
        chain: XposedInterface.Chain,
        point: Framework.InterceptionPoint,
        keyConfig: KeyConfig,
    ): Boolean {
        val keyEvent = firstKeyEvent(chain.args)
        if (keyEvent == null) {
            // 认领不到 keyCode 就吞键会连带吃掉别的键，放行给系统
            XLog.w("${KeyId.PLUS.label}（$point）：参数里没有 KeyEvent，无法确认是侧键 → 放行给系统")
            return false
        }
        // 合成事件（我们自己重注入的）不认领：认领就是自持循环（见 [isSynthetic]）
        if (isSynthetic(keyEvent)) {
            XLog.d("按键（$point）：合成事件（deviceId=${keyEvent.deviceId}），放行给系统")
            return false
        }

        // 白名单校验而非「必须等于 735」：这条路上报的是 ColorOS 注入的内部码（780/781/782），
        // 且连音量键也会进来（实测 keyCode=25），不能按「能进来就是侧键」直接吞掉。
        // 判据只有一处：KeyId.acceptedKeyCodes（经 KeyId.fromAnyKeyCode 查询）。
        val id = KeyId.fromAnyKeyCode(keyEvent.keyCode) ?: run {
            // 留在 d：这条路上音量键也会进来，改 W 会被连调音量刷屏；
            // 排查「侧键为什么没被接管」看 feedDetector 的 DOWN/UP 两行 W。
            XLog.d("按键（$point）：keyCode=${keyEvent.keyCode} 不是侧键，放行给系统")
            return false
        }

        return feedDetector(id, point, keyEvent, keyConfig)
    }

    /** PM 路径：从参数里取 KeyEvent，按 action 喂给判定器。 */
    private fun onPhoneWindowManagerCall(
        chain: XposedInterface.Chain,
        point: Framework.InterceptionPoint,
        keyConfig: KeyConfig,
    ): Boolean {
        val keyEvent = firstKeyEvent(chain.args)
        if (keyEvent == null) {
            XLog.w("按键（$point）：参数里没有 KeyEvent，这一层不做转发（放行给系统）")
            return false
        }
        // 同上：合成事件不认领
        if (isSynthetic(keyEvent)) return false

        // 这一层能收到所有按键，所以按 keyCode 认领（只认原始码 735）
        val id = KeyId.fromKeyCode(keyEvent.keyCode)
        if (id == null) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                XLog.d("按键（$point）：不认识的 keyCode=${keyEvent.keyCode}，放行给系统")
            }
            return false
        }

        return feedDetector(id, point, keyEvent, keyConfig)
    }

    /**
     * 把一条原始按键事件喂给判定器。
     *
     * 判定器本体也包一层 catch：它跑在 system_server 主线程上，抛出去就是整机崩。
     * 失败按「这一下不接管」处理（返回 false，交还系统）。
     */
    private fun feedDetector(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
        keyConfig: KeyConfig,
    ): Boolean = try {
        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount > 0) {
            // 按住不放的 repeat：不能喂判定器（会刷新 pressAt、作废已排的长按枪），
            // 也不能放行给系统（键已被接管）—— 直接吞掉。框架自己的分支也先判 getRepeatCount()==0。
            true
        } else when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                // DOWN/UP 是验收判据链的第一环（AGENTS.md 二、1.2），必须是 W ——
                // 注入侧 i/d 在 LSPosed 日志页里看不到；一次按键两行，不会刷屏。
                XLog.w("${id.label}（$point）：DOWN keyCode=${keyEvent.keyCode}")
                detector.onKeyDown(id, keyConfig)
                true
            }
            KeyEvent.ACTION_UP -> {
                // 抬手被取消（窗口失焦 / 按压被打断）⇒ 不判任何手势。
                // KeyEvent 没有 ACTION_CANCEL（那是 MotionEvent 的），取消是 FLAG_CANCELED 位，
                // 只能从 isCanceled 读；不认它的话已排下的长按照样触发（凭空长按）。
                if (keyEvent.isCanceled) {
                    XLog.w("${id.label}（$point）：这一按被取消（FLAG_CANCELED）→ 撤掉判定")
                    detector.reset(id)
                    true
                } else {
                    XLog.w("${id.label}（$point）：UP keyCode=${keyEvent.keyCode}")
                    detector.onKeyUp(id, keyConfig)
                    true
                }
            }
            else -> false
        }
    } catch (e: Throwable) {
        XLog.w("手势判定失败（这一次按键交还系统）：$point", e)
        false
    }

    /** 从实参里挑出第一个 `KeyEvent`（按键热路径，不要 `filterIsInstance`）。 */
    private fun firstKeyEvent(args: List<Any?>): KeyEvent? {
        for (a in args) if (a is KeyEvent) return a
        return null
    }
}
