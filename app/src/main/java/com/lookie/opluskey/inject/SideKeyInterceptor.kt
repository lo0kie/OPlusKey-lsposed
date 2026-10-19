package com.lookie.opluskey.inject

import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.inject.input.GestureDetector
import io.github.libxposed.api.XposedInterface

/**
 * 「不在白名单」那条日志**不报**的几个键码 —— 音量 / 电源。
 *
 *  它们同样会走进策略回调（实测音量键 keyCode=25 也会进来），每按一次音量打一行 W 会把日志刷满；
 *  而「用户按的是侧键、模块却不认识」这种情况本来就不该被它们淹没。
 */
private val QUIET_KEY_CODES =
    setOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER,
    )

/**
 * 「收集期吞掉的那一下」还能认多久（毫秒）—— 见 `SideKeyInterceptor.swallowedDownAt`。
 *
 *  取 5 秒：弹窗关掉到抬手之间的间隔就是「人按住一颗键的时长」，长按也就一两秒。
 */
private const val COLLECT_SWALLOW_TAIL_MS = 5_000L

/**
 * 一次按键的判定：这个键是不是侧键、该判成什么手势。
 */
internal class SideKeyInterceptor(
    handler: Handler,
    /** 判出手势后的去向（由 `KeyHooks` 注入，实现是 `GestureForwarder`）。 */
    private val forward: (KeyId, Gesture, Boolean) -> Unit,
    /** 每判出一个手势记一笔（`KeyHooks.noteGesture`）—— 链路自检用，由装配方注入。 */
    private val onGestureNoted: () -> Unit,
    /** 判过长按的这一按松手了（去处与 [forward] 同一处，见 `GestureDetector.onLongReleased`）。 */
    private val onRelease: (KeyId) -> Unit,
) {
    /**
     * 手势判定器 —— 拿到的是 hook 回调里的原始事件，时序是真的，不会被「解冻灌包」搅乱。
     *
     * 线程归属（**真机实测**，PLK110 / ColorOS 17）：按键回调跑在 **`InputReader` 线程**
     * 上 —— 量的时候给 DOWN/UP 两行**临时**加了线程名（判据行的字面量量完即还原），9 次按压、
     * 18 条日志无一例外；而判定器排的那几枪计时器落在装配方给的 `Handler`（**main looper**）上。
     * 所以这是**两条线程 + 共享状态**的结构：
     * `Pending` 的每个字段都必须是 `@Volatile`（见 `GestureDetector` 里那份字段表），
     * 摘枪一律走 `removeCallbacks`（确定），不要退化成「到点读一眼状态」。
     */
    private val detector =
        GestureDetector(
            handler,
            onGestureNoted = onGestureNoted,
            onGesture = { id, gesture, _, repeat -> forward(id, gesture, repeat) },
            onLongReleased = onRelease,
        )

    /**
     * 这个事件是不是**我们自己注入的**合成事件（`KeyInjector` 干的那类按键注入）。
     *
     * 注入的键会**再走一遍**这条按键路径 ⇒ 再被认领、再判一次手势 ⇒ 每 ~100ms 一轮的自持循环
     *（真机实测，见 `docs/device-findings.md`）。合成事件带的是虚拟键盘的 `deviceId`
     *（`KeyInjector` 显式传 -1），认它的号即可放行。
     *
     * ## 判据必须**只作用在我们可能注入的键**上
     *
     *  有 ROM 的**真实**侧键事件同样是 `deviceId = -1`（实测 PLQ110 / ColorOS 17：一次按压的
     *  DOWN、UP 两条都带 -1）。只看 deviceId 会把它们整条当合成事件放行，表现是「单独按没反应、
     *  偶尔凑出一个孤立 UP 才补判一次单击」。
     *
     *  而 `KeyInjector` 下发的永远是**动作里的 keyCode**（主屏幕 / 返回 / 音量 / 媒体 / 截图这类），
     *  侧键那几个码（735 / 780 / 781 / 782）**从不由我们自己注入** —— 所以这几个码即便 deviceId
     *  是 -1 也是真按键。将来若加了「注入侧键码」的动作，这条判据要跟着重看，否则会重新自持。
     *
     *  ## 判据只看「这个码是不是我们会注入的」
     *
     *  能注入的码就是 [KeyId.INJECTABLE_CODES] 那一张表（动作里的主屏幕 / 返回 / 音量 / 媒体 / 截图…）。
     *  所以「码在表里 + 虚拟设备号」= 我们自己注入的那一下。
     *
     *  注意它**不是**「不许收集」那张表：音量±与电源允许被收集（[KeyId.RESERVED_CODES] 里没有
     *  它们），但它们注入的那一下仍要从这里认出来放行 —— 两张表因此不能合并。
     *
     *  以前这条判据问的是「配置认不认识这个码」（`keyForCode == null`），那会把**还没收集的
     *  物理键**也当成合成事件：收集弹窗里按下它时配置里当然还没有它，而这块 ROM 的真按键
     *  （PLQ110 实测）同样带 `deviceId = -1` —— 于是新键永远收不进来，日志里只有一句
     *  「合成事件，放行给系统」。
     */
    private fun isSynthetic(keyEvent: KeyEvent): Boolean =
        keyEvent.keyCode in KeyId.INJECTABLE_CODES &&
            (
                keyEvent.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD ||
                    keyEvent.deviceId < 0
            )

    /**
     * 收集期被吞掉的那一下「按下」：键码 → 时刻。
     *
     *  为什么要记：收集期 DOWN 被吞、弹窗随即关掉（收下了 / 被手动关掉），而 UP 在关掉之后才到 ——
     *  那条 UP 不再走「收集期一律吞」，判定器会把它当成**孤立抬手**补判一次单击。
     */
    private val swallowedDownAt = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    /**
     * 撤掉已经排下的手势 —— 长按枪、双击窗、连发任务（`GestureDetector.reset` 三者都会摘）。
     *
     *  **全部键一起撤**：配置里任意一颗键从「接管」翻成「不接管」都会走到这里（见 `ConfigChannel.read`
     *  的触发点）。只撤侧键的话，「关掉某一颗自定义键」时它按住不放的那一下会一路派发到连发上限。
     *  多做的那点撤销没有代价（本来就没有待定手势时是空操作）。
     *
     *  **不能**改在 [handle] 开头做：那条只在有按键事件走进来时才会执行，
     *  而按住不放期间没有新事件进来，撤销就永远不会发生，计时器与连发任务会继续响下去。
     */
    internal fun cancelPending() {
        try {
            detector.reset(null)
        } catch (e: Throwable) {
            XLog.w("撤销待定手势失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }

    /** @return true = 接管（吞掉原方法）；false = 原样放行给系统。 */
    fun handle(
        point: Framework.InterceptionPoint,
        chain: XposedInterface.Chain,
    ): Boolean {
        //  这里**不再有**「主开关关着就整条放行」的早退：那一句是侧键的主开关，却挡在所有键前面
        //  —— 自定义按键出现之后它会变成两个错误（侧键关着时自定义键完全无效；侧键开着时自定义键
        //  又绕过自己的 enabled）。闸门下移到每个键自己的配置上（见 [onStrategyCall]）。
        // 注意：判定参数（双击窗口 / 立即单击）现在**每颗键各一份**，
        //  要等 `decide` 认出是哪颗键之后才能刷 —— 见 [feedDetector]。
        val config = ConfigChannel.config

        return when (point.kind) {
            Framework.InterceptionPoint.Kind.OPLUS_STRATEGY -> {
                onStrategyCall(chain, point, config)
            }

            Framework.InterceptionPoint.Kind.OPLUS_PM_EXT,
            Framework.InterceptionPoint.Kind.AOSP_PM,
            -> {
                onPhoneWindowManagerCall(chain, point, config)
            }
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
        config: Config,
    ): Boolean {
        val keyEvent =
            firstKeyEvent(chain.args) ?: run {
                // 认领不到 keyCode 就吞键会连带吃掉别的键，放行给系统
                XLog.w("按键（$point）：参数里没有 KeyEvent，无法确认是哪颗键 → 放行给系统")
                return false
            }
        // 合成事件（我们自己重注入的）不认领：认领就是自持循环（见 [isSynthetic]）
        if (isSynthetic(keyEvent)) {
            // 提成 W：这条在 release 里也要能看到 —— 「遥控器式的真事件被当合成丢了」正是
            //   这次 PLQ110 那种故障唯一的一行证据（d 级在正式包里被编译期消灭）。
            XLog.w("按键（$point）：合成事件（deviceId=${keyEvent.deviceId} keyCode=${keyEvent.keyCode}），放行给系统")
            return false
        }

        // 白名单校验而非「必须等于 735」：这条路上报的是 ColorOS 注入的内部码（780/781/782），
        // 且连音量键也会进来（实测 keyCode=25），不能按「能进来就是侧键」直接吞掉。
        // 判据只有一处：每颗键的 `acceptedKeyCodes`（经 `Config.keyForCode` 查询）。
        return when (val d = decide(config, keyEvent, point, raw = false)) {
            Decision.Pass -> false
            Decision.Swallow -> true
            is Decision.Claimed -> feedDetector(d.config.id, point, keyEvent, d.config)
        }
    }

    /** PM 路径：从参数里取 KeyEvent，按 action 喂给判定器。 */
    private fun onPhoneWindowManagerCall(
        chain: XposedInterface.Chain,
        point: Framework.InterceptionPoint,
        config: Config,
    ): Boolean {
        val keyEvent =
            firstKeyEvent(chain.args) ?: run {
                XLog.w("按键（$point）：参数里没有 KeyEvent，这一层不做转发（放行给系统）")
                return false
            }
        // 同上：合成事件不认领
        if (isSynthetic(keyEvent)) return false

        // 这一层能收到所有按键，所以按 keyCode 认领 —— 但只认**原始码**（内部注入码归策略类那条路）
        return when (val d = decide(config, keyEvent, point, raw = true)) {
            Decision.Pass -> false
            Decision.Swallow -> true
            is Decision.Claimed -> feedDetector(d.config.id, point, keyEvent, d.config)
        }
    }

    /** 一次按键的处置结果 —— 两条认领路径共用（`when` 一出，出口数量也降下来了）。 */
    private sealed interface Decision {
        /** 原样放行给系统。 */
        object Pass : Decision

        /** 吞掉 —— 收集按键期间用（见 `KeyWatch` 的类注释）。 */
        object Swallow : Decision

        /** 接管这颗键，交给它自己那一份配置。 */
        data class Claimed(
            val config: KeyConfig,
        ) : Decision
    }

    /**
     * 这一下按键该怎么办 —— **旁路上报、收集期吞键、认领、闸门只在这一次判**。
     *
     *  合成事件在调进来之前就已被两条路径各自放行（那道判据要看 deviceId，与这里的口径不同）。
     *
     * @param raw `true` = 只认**原始键码**（PM 兜底路径；内部注入码 780/781/782 归策略类那条，
     *   见 `Config.keyForRawCode`）
     */
    private fun decide(
        config: Config,
        keyEvent: KeyEvent,
        point: Framework.InterceptionPoint,
        raw: Boolean,
    ): Decision {
        // 「自定义按键」页开着监听时，先把这一下**旁路**报回去。刻意摆在白名单**之前**：
        //  那一页要的是「用户按了哪些键」，摆到白名单后面就只剩侧键自己那份码，
        //  别的键一个都听不到。
        KeyWatch.report(keyEvent)
        // 收集期**一律吞掉**：弹窗是模态的，这时按任何键都是在「报键」，不是在用它。
        //  不吞的话音量会真的调音量、电源会真的弹菜单（还把弹窗盖掉）—— 用户看到的是
        //  「按了没加上，还顺手改了别的东西」。
        //  最长影响由 [KeyWatch] 的 60 秒自动关兜着，弹窗一关就恢复。
        val collecting = KeyWatch.active
        if (collecting && keyEvent.action == KeyEvent.ACTION_DOWN) {
            // 记下这一下「按下」：弹窗很可能**因为这一下而关掉**（收下了 / 被手动关掉），
            // 紧跟的 UP 就不再属于「收集期」了（见下面那段）。
            swallowedDownAt[keyEvent.keyCode] = SystemClock.uptimeMillis()
        }
        // 收集期被吞掉的那一下，它的**抬手也要吞**：只吞 DOWN 的话，判定器收到一条没有按下的
        // UP，会按「孤立抬手」补判一次单击 —— 侧键的动作照样执行（真机实测：弹窗一关，
        // 侧键立刻跑了一次 OCR）。判据是「这颗码的按下刚刚被我们吞过」。
        val justSwallowed =
            !collecting &&
                keyEvent.action == KeyEvent.ACTION_UP &&
                (
                    swallowedDownAt.remove(keyEvent.keyCode)?.let {
                        SystemClock.uptimeMillis() - it < COLLECT_SWALLOW_TAIL_MS
                    } == true
                )
        if (collecting || justSwallowed) return Decision.Swallow

        val keyConfig =
            if (raw) config.keyForRawCode(keyEvent.keyCode) else config.keyForCode(keyEvent.keyCode)
        if (keyConfig == null) {
            logUnclaimed(point, keyEvent, raw)
            return Decision.Pass
        }
        if (ConfigChannel.enabledOf(keyConfig.id)) return Decision.Claimed(keyConfig)
        // 只在按下那一下打：闸门关着时每一按都会走到这里
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            XLog.w("${keyConfig.id.label}（$point）：这颗键没开接管，放行给系统")
        }
        return Decision.Pass
    }

    /**
     * 「这个码不是任何一颗键」那一行日志 —— 两条路径的级别与抑制条件不同，所以分在这里。
     *
     *  策略类那条：只对**不是音量 / 电源**的键、且只在按下那一下打 W。那几个键也走这条回调，
     *  每次都打会把日志刷满；留 `d` 的话在 release 里会被编译期消灭 ——「按了键但模块没认领」
     *  于是**一行都没有**，而这正是排查「侧键没反应」最缺的一条（ROM 换了内部码 / 换了键码时，
     *  用户日志里得能看见那个数）。
     *  PM 那条：它本来就能收到所有按键，按 `d` 记（真机排查时才看）。
     */
    private fun logUnclaimed(
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
        raw: Boolean,
    ) {
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return
        if (raw) {
            XLog.d("按键（$point）：不认识的 keyCode=${keyEvent.keyCode}，放行给系统")
        } else if (keyEvent.keyCode !in QUIET_KEY_CODES) {
            XLog.w("按键（$point）：keyCode=${keyEvent.keyCode} 不在认领白名单，放行给系统")
        }
    }

    /**
     * 把一条原始按键事件喂给判定器。
     *
     * 判定器本体也包一层 catch：它跑在 **InputReader 线程**上（实测，见 `detector` 的注释），
     * 抛出去会顺着输入读取线程把 system_server 带崩。
     * 失败按「这一下不接管」处理（返回 false，放行给系统）。
     */
    private fun feedDetector(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
        keyConfig: KeyConfig,
    ): Boolean =
        try {
            // 判定参数**不在这里刷**：判定器直接读传进去的 `keyConfig`（每颗键各一份）。
            //  原先判定器上是两个全局槽，每事件前刷一次 —— B 键的 DOWN/UP 夹在 A 键的
            //  DOWN/UP 之间时，A 的抬手会用 B 的窗口判（多颗自定义键同时在用时才撞）。
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount > 0) {
                // 按住不放的 repeat：不能喂判定器（会刷新 pressAt、作废已排的长按枪），
                // 也不能放行给系统（键已被接管）—— 直接吞掉。框架自己的分支也先判 getRepeatCount()==0。
                true
            } else {
                when (keyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        // DOWN/UP 是验收判据链的第一环（AGENTS.md 二、1.2），必须是 W ——
                        // 注入侧 i/d 在 LSPosed 日志页里看不到；一次按键两行，不会刷屏。
                        //  字面量**不要动**（六、2：判据级日志的字面量是接口，改它等于改判据）。
                        XLog.w("${id.label}（$point）：DOWN keyCode=${keyEvent.keyCode}")
                        detector.onKeyDown(id, keyConfig)
                        true
                    }

                    KeyEvent.ACTION_UP -> {
                        // 抬手被取消（窗口失焦 / 按压被打断）⇒ 不判任何手势。
                        // KeyEvent 没有 ACTION_CANCEL（那是 MotionEvent 的），取消是 FLAG_CANCELED 位，
                        // 只能从 isCanceled 读；不认它的话已排下的长按照样触发（凭空长按）。
                        if (keyEvent.isCanceled) {
                            // 取消也是「这一按结束了」：长按若已判过，那一段采集还在跑 ⇒ 一样要收尾
                            // （`finish` 里才判「有没有东西要收尾」，这里不重复判一次）。
                            XLog.w("${id.label}（$point）：这一按被取消（FLAG_CANCELED）→ 撤掉判定")
                            detector.finish(id)
                            true
                        } else {
                            XLog.w("${id.label}（$point）：UP keyCode=${keyEvent.keyCode}")
                            detector.onKeyUp(id, keyConfig)
                            true
                        }
                    }

                    else -> {
                        false
                    }
                }
            }
        } catch (e: Throwable) {
            XLog.w("手势判定失败（这一次按键放行给系统）：$point", e)
            false
        }

    /** 从实参里挑出第一个 `KeyEvent`（按键热路径，不要 `filterIsInstance`）。 */
    private fun firstKeyEvent(args: List<Any?>): KeyEvent? {
        for (a in args) if (a is KeyEvent) return a
        return null
    }
}
