package com.lookie.opluskey.inject.key

import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent
import com.lookie.opluskey.bridge.Framework
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.Config
import com.lookie.opluskey.core.model.Gesture
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.core.model.trigger
import com.lookie.opluskey.core.rom.RomKeyFamilies
import com.lookie.opluskey.inject.input.ComboDetector
import com.lookie.opluskey.inject.input.DetectorBinds
import com.lookie.opluskey.inject.input.GestureDetector
import com.lookie.opluskey.inject.input.HeldPresses
import com.lookie.opluskey.inject.wire.ConfigChannel
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

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
    /** 组合键那一块要把「压着的按下」延后一个窗口再喂给单键判定器，所以这条 Handler 要留着。 */
    private val handler: Handler,
    /** 判出手势后的去向（由 `KeyHooks` 注入，实现是 `GestureForwarder`）。 */
    private val forward: (KeyId, GestureTrigger, Boolean) -> Unit,
    /** 每判出一个手势记一笔（`KeyHooks.noteGesture`）—— 链路自检用，由装配方注入。 */
    private val onGestureNoted: () -> Unit,
    /**
     * 判出一次组合：哪一档组合 + 短按还是长按。
     *
     *  组合没有单颗键的 `KeyConfig`（动作在 `ComboKey` 上、执行在宿主），所以走这条独立回调。
     *  触发方式不必带 —— 它是这一档的身份（`ComboKey.mode`），执行侧读自己那份配置就有。
     */
    private val onCombo: (ComboKey, GestureTrigger, Boolean) -> Unit,
    /** **某颗键刚按下** —— 按住组合的起点键要在这一刻让宿主把场景定下来（见 `ComboDetector.onHoldStarter`）。 */
    private val onHoldStarter: (KeyId) -> Unit,
    /** **某一档组合刚凑成** —— 关掉「首键按下固定」时，场景按这一刻定。 */
    private val onComboFormed: (ComboKey) -> Unit,
    /**
     * **首键松手时补派它自己的长按** —— 只在那一档开了「首键松手仍执行长按」时调
     * （见 `ComboDetector.onStarterLong`）。转给宿主的手势是那颗键的 `long`。
     */
    private val onStarterLong: (KeyId) -> Unit,
    /**
     * 「屏幕黑着的话，这段时间里别让 CPU 睡」（熄屏排双击窗口时用，见 `KeyHooks.holdAwake`）。
     *
     *  熄屏时判定器要靠它把双击窗口那一段顶住 —— 见 `GestureDetector` 类注释。
     */
    private val holdAwake: (Long) -> Unit,
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
            holdAwake = holdAwake,
        )

    /**
     * 参与组合的键按下后「先等一个窗口」那笔账 —— 窗口到点才把按下补交给 [detector]。
     *
     *  单独立一个类：这里只是「到点补发」的簿记，挤进来会把本类顶到 `TooManyFunctions`
     *  （它本来就贴着线）。见 `HeldPresses` 的类注释。
     */
    private val held = HeldPresses(handler, feed = detector::onKeyDown, feedUp = detector::onKeyUp)

    /**
     * 「两颗键一起按」的判定 —— 与单键判定器并排，**先于它**看每一个事件。
     *
     *  组合成立时会把这两颗键的单键状态机摘掉（`resetKey`），所以两颗键那一下只出一个结果：
     *  要么组合，要么各自的手势，不会两边都执行。
     */
    private val combo =
        ComboDetector(
            handler = handler,
            onCombo = onCombo,
            onHoldStarter = onHoldStarter,
            onComboFormed = onComboFormed,
            resetKey = { id ->
                //  这一颗已经归组合了 ⇒ 压着等窗口的那一下不必再喂单键（否则一次按压执行两个动作）
                held.drop(id)
                detector.reset(id)
            },
            // 这一组能凑成哪几档 —— 注入侧手上只有广播来的那份配置（配置文件它读不到），
            //  所以判据收在 `ConfigChannel.combosFor` 一处：空表就交回单键判定器。
            //  一张列表而不是一档：同一组键可以配几档、各自一种触发方式（`ComboKey.mode`）。
            bindingsFor = { keys -> ConfigChannel.combosFor(keys) },
            //  扫的时候按最宽的那一档扫，命中后由「那一组自己的窗口」复核（见 `ComboDetector`）
            windowMs = { ConfigChannel.scanWindowMs() },
            //  按住那两档的下限：起点键必须按满这么久、且还没抬手，另一颗落下才算这一档组合
            //  （「一起按」只归窗口那条）
            holdMinFor = { combo -> ConfigChannel.holdMinFor(combo) },
            //  多成员那一档的**整串跨度**（相邻两颗的窗口 × (成员数-1)）—— 三颗按一个
            //  窗口复核等于要求三根手指同时落，真机上永远凑不齐
            spanFor = { combo -> ConfigChannel.spanFor(combo) },
            //  凑成之后还有更宽的一档来得及时（配了 {A,B} 又配了 {A,B,C}）⇒ 先不派发，
            //  等它窗口结束（见 `ComboDetector.awaitWider`）。判据在 `Config.widerComboOf`
            //  一处（与 `comboFor` 同一个家），这一层只把键 id 递进去。
            widerFor = { keys -> ConfigChannel.config.widerComboOf(keys) },
            //  只有参与某组已启用组合的键才先等一个窗口，其余键一点不受影响
            joinsCombo = { id -> ConfigChannel.joinsCombo(id) },
            //  多成员判定那条路的头道闸：**任何一档**（含按住档）的成员都算 —— 按住档的
            //  后续成员不等窗口，但它照样要能凑齐（见 `ConfigChannel.inAnyCombo`）
            inAnyCombo = { id -> ConfigChannel.inAnyCombo(id) },
            //  「先按住这颗、再按另一颗」的上界：这颗键的长按判出来之后，它已经执行过自己的
            //  长按动作了，再认组合就是一次按压两个动作。判定层读不到 `KeyConfig`，所以由这里
            //  按那颗键自己的阈值算。「只参与非首键的按住组合」的键恒为 false —— 它压根不判单键。
            //
            //  **「松手时触发」的键也不算判过**：那一档到点并不执行（动作押在抬手，见
            //  `KeyConfig.longFireOnRelease`），所以「已经执行过自己的动作」不成立 —— 而按住手势
            //  要的正是「按住久一点再补一颗」。组合一旦凑成，这颗键的状态机会被摘掉（`resetKey`），
            //  押着的那一次长按跟着没，抬手只出组合那一套。
            longPressPassed = { id, at, now ->
                val key = ConfigChannel.config.key(id)
                !key.comboOnly && !key.longFireOnRelease && now - at >= key.longPressMs
            },
            binds =
                DetectorBinds(
                    //  「首键松手仍执行它自己的长按」—— 判据在 `Config.comboStarterKeepLongFor` 一处
                    //  （独立设置关着就看公共那份），派发交给 `KeyHooks` 那条单键通道。
                    starterKeepLong =
                        DetectorBinds.StarterKeepLong(
                            enabled = { c -> ConfigChannel.config.comboStarterKeepLongFor(c) },
                            fire = onStarterLong,
                        ),
                    //  按住档成员的「固定窗口起点」—— 判据在 `Config.comboHoldWindowStartFor` 一处。
                    //  **这一位与等待组合那颗开关是两个变量**：两张卡各管一种按法，共用一个的话
                    //  在「按住组合」卡上拨一下，等待组合那根滑杆也会跟着换一格。它还按
                    //  「这一档是不是按顺序触发」挑一组（顺序组合自己那两颗开关），所以这里取
                    //  的是那支合并后的判据、不是 `comboHoldFixedWindowStartFor`。
                    holdFixedWindowStartFor = { c -> ConfigChannel.config.comboHoldWindowStartFor(c) },
                    //  这一档的四格窗口（等待 / 按住 × 固定窗口起点 开 / 关）—— 取值只在
                    //  `Config.comboWindowsFor` 一处，这里只把那一份递进去
                    windowsFor = { c -> ConfigChannel.windowsFor(c) },
                    //  **成员连击参数**（按住首键、唯一成员键连击）—— 判据在
                    //  `Config.comboMemberChain` 一处（窗口按「独立设置优先」解析），
                    //  这里只把那一份递进去；`null` = 这档不支持成员连击。
                    memberChainFor = { c -> ConfigChannel.config.comboMemberChain(c) },
                ),
        )

    /**
     * 已经报过「这个码不认识」的键码 —— 每个码只报**一次** W，之后退回 `d`。
     *
     *  PM 兜底那条能收到所有按键：一律 W 会把日志刷满、把真正的判据行挤掉；一律 `d` 又在
     *  release 里被编译期消除，表现成「按了键、模块一行都没有」。所以第一颗未知码必须报一次 W
     *  （它是「ROM 换了内部码 / 换了键码」的唯一凭据），同一颗码的后续按压留在 `d`。
     */
    private val unclaimedLogged =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<Int>()

    /**
     * 这个事件是不是**我们自己注入的**合成事件（`KeyInjector` 干的那类按键注入）。
     *
     * 注入的键会**再走一遍**这条按键路径 ⇒ 再被认领、再判一次手势 ⇒ 每 ~100ms 一轮的自持循环
     *（真机实测，见 `docs/device/`）。合成事件带的是虚拟键盘的 `deviceId`
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
            //  `VIRTUAL_KEYBOARD` 就是 -1，`deviceId < 0` 已覆盖它（另两种负值是内核的
            //  BUILT_IN_KEYBOARD / 未指派设备，同样不是物理侧键）。
            keyEvent.deviceId < 0

    /**
     * 收集期被吞掉的那一下「按下」：键码 → 时刻。
     *
     *  为什么要记：收集期 DOWN 被吞、弹窗随即关掉（收下了 / 被手动关掉），而 UP 在关掉之后才到 ——
     *  那条 UP 不再走「收集期一律吞」，判定器会把它当成**孤立抬手**补判一次单击。
     */
    private val swallowedDownAt = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    /**
     * 「只参与非首键的按住组合」那颗键的收尾账本 —— 压住的按下怎么还给系统，全由它一处说了算
     * （先吞的理由、长按为什么要补 DOWN 而不是一对，见 `ComboOnlyNative` 的类注释）。
     */
    private val comboOnlyNative =
        ComboOnlyNative(
            handler = handler,
            combo = combo,
            windowMs = { id -> ConfigChannel.holdWindowMsFor(id) },
            //  这一颗键会不会被接在后面（它参与的某一组认窗口那条）—— 认就压一个窗口再还回去
            windowMember = { id -> ConfigChannel.joinsCombo(id) },
            //  这一颗键可能是按住组合里**先按住的那一颗**：**压到抬手**再还回去 —— 那一档没有
            //  窗口到点这回事，窗口一漏就是「一次按压两个动作」（见 `ComboOnlyNative` 的类注释）
            holdStarter = { id -> ConfigChannel.holdStarter(id) },
        )

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
            //  压着等窗口的那几颗也一起放掉：配置变了就不该再把旧的那一下补进去
            held.drop(null)
            //  组合也一起清：两颗键按住不放时关掉其中一颗，组合的长按枪不能继续响
            combo.reset()
            //  「只参与非首键的按住组合」那本账：压着的不再补发；已经补发过原生的补一条抬起（否则系统那边
            //  那颗键一直按着，音量会一路涨到底）
            comboOnlyNative.reset()
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
                //  每次按压都会走到（这一层收所有按键）⇒ d，不占判据行的额度
                XLog.d { "按键（$point）：参数里没有 KeyEvent，无法确认是哪颗键 → 放行给系统" }
                return false
            }
        // 合成事件（我们自己重注入的）不认领：认领就是自持循环（见 [isSynthetic]）
        if (isSynthetic(keyEvent)) {
            // 这条是判据：release 里也要能看到 —— 「遥控器式的真事件被当合成丢了」正是
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
            is Decision.RomGesture -> runRomGesture(d, keyEvent)
            is Decision.RomSwallow -> swallowRomMiddleCode(d.code, keyEvent)
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
                //  同上：高频路径，判据看的是「认领 / 判成什么手势」那几行
                XLog.d { "按键（$point）：参数里没有 KeyEvent，这一层不做转发（放行给系统）" }
                return false
            }
        // 同上：合成事件不认领。**按 d 记一行**：按 w 会随每次注入刷屏，而不记的话
        //  「自己注入的键被自己认领了」这种自持循环在日志里完全无痕。
        if (isSynthetic(keyEvent)) {
            XLog.d { "按键（$point）：合成事件（deviceId<0），不认领" }
            return false
        }

        // 这一层能收到所有按键，所以按 keyCode 认领 —— 但只认**原始码**（内部注入码归策略类那条路）
        return when (val d = decide(config, keyEvent, point, raw = true)) {
            Decision.Pass -> false
            Decision.Swallow -> true
            is Decision.Claimed -> feedDetector(d.config.id, point, keyEvent, d.config)
            is Decision.RomGesture -> runRomGesture(d, keyEvent)
            is Decision.RomSwallow -> swallowRomMiddleCode(d.code, keyEvent)
        }
    }

    /**
     * 族里的中间态码：**按下与抬手都吞**，一次按压只在按下记一行。
     *
     *  抬手也吞是必须的：放一条没有按下的 UP 过去，判定器会按「孤立抬手」补判一次单击。
     */
    private fun swallowRomMiddleCode(
        code: Int,
        keyEvent: KeyEvent,
    ): Boolean {
        //  **只记第一次按下**：长按的 repeat 同样是 ACTION_DOWN（`repeatCount > 0`），
        //  按住不放会把这行 W 刷成几十条，把真正的判据行冲掉。
        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
            XLog.w("按键：ROM 中间码 $code 已认下并吞掉（不是手势码，不派发）")
        }
        return true
    }

    /**
     * ROM 判好的手势码：**按下与抬手都吞**，只在抬手那一下派发。
     *
     *  为什么派发放在抬手（与判定器把双击挪到抬手同一个理由，见 `GestureDetector.onKeyDown`
     *  里那段）：按下那一刻触发键自己还按着，注入的动作容易和 ROM 的组合键窗口打架。
     *  代价只有几十毫秒，手感上看不出来。
     */
    private fun runRomGesture(
        d: Decision.RomGesture,
        keyEvent: KeyEvent,
    ): Boolean {
        if (keyEvent.action == KeyEvent.ACTION_UP) {
            detector.onRomGesture(d.config.id, d.gesture.trigger(), d.config)
        }
        return true
    }

    /** 一次按键的处置结果 —— 两条认领路径共用（`when` 一出，出口数量也降下来了）。 */
    private sealed interface Decision {
        /** 原样放行给系统。 */
        object Pass : Decision

        /** 吞掉 —— 收集按键期间用（见 `KeyWatch` 的类注释）。 */
        object Swallow : Decision

        // 接管这颗键，交给它自己那一份配置。
        data class Claimed(
            val config: KeyConfig,
        ) : Decision

        /**
         * 接管，且**这个码本身就是 ROM 判好的手势**（压感拍照键的 767/768/769）——
         * 不喂判定器，抬手时直接按 [gesture] 派发。
         *
         *  与 [Claimed] 分开是为了让「喂判定器」那一句在两条认领路径上都只有一种形状：
         *  凡是进判定器的都是「只有按下/抬手、手势靠时长判」的普通码。
         */
        data class RomGesture(
            val config: KeyConfig,
            val gesture: Gesture,
        ) : Decision

        /**
         * 认下、但**不派发**：这个码属于已知族、却不是手势码（轻按 / 重按 / 上滑 / 下滑 …）。
         *
         *  这些码是按压过程的中间态。喂给判定器会补判出一次单击；放行给系统则让 ROM 自己
         *  执行拍照。两条都错，只能吞。
         */
        data class RomSwallow(
            val code: Int,
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
        //  **我们自己补发出去的事件**：认出来就放行、销一次账。
        //  不认就是死循环 —— 吞掉 → 补发 → 又被吞 → 又补发。摆在这一层的最前面：
        //  这不是用户按的，收集期那一道也不该拦它（那一档问的是「用户按了哪些键」）。
        if (comboOnlyNative.consumeEcho(keyEvent.keyCode)) {
            return Decision.Pass
        }
        //  收集期那一段（旁路上报 + 记账 + 判这次该不该整条吞）收在 [swallowWhileCollecting]，
        //  这里的 `decide` 只剩「认领 / 放行」两层判断。
        if (swallowWhileCollecting(swallowedDownAt, keyEvent)) return Decision.Swallow

        val keyConfig =
            if (raw) config.keyForRawCode(keyEvent.keyCode) else config.keyForCode(keyEvent.keyCode)
        //  **保留码一律不认领**（见 `KeyId.RESERVED_CODES`）：那一批是动作自己会注入的码，
        //  认下来就是「按一下等于触发自己」。收集侧已经拦过一次（`CollectKeyDialog.checkKey`），
        //  这里是拦截路径上的**第二道** —— 导进来的、手改过的配置照样可能带着它。
        //  与「这个码不是任何一颗键」并成一处：两者都是「不认领、放行」，分两个出口只会多一个 return。
        if (keyConfig == null || keyEvent.keyCode in KeyId.RESERVED_CODES) {
            if (keyConfig == null) {
                logUnclaimed(point, keyEvent, raw)
            } else {
                XLog.w("${keyConfig.id.label}（$point）：keyCode=${keyEvent.keyCode} 是保留码，不认领，放行给系统")
            }
            return Decision.Pass
        }
        if (ConfigChannel.enabledOf(keyConfig.id)) {
            // ROM 判好的手势码（压感拍照键）：直接按手势派发，不进时长判定 ——
            //  判成双击时 ROM 只发 769 一个码，等第二次按下永远等不到（见 `RomKeyFamilies`）。
            val gesture = RomKeyFamilies.gestureOf(keyEvent.keyCode)
            return when {
                //  **「只参与非首键的按住组合」先判**：ROM 判好的那几颗码本身就是单键手势（双击 / 轻按 / 重按），
                //  认下来就等于「单键仍归模块」—— 这一位要的正好相反。交给 `ComboOnlyNative`
                //  压住这一下再按窗口还回去，组合照旧能凑（它只是不判单键）。
                keyConfig.comboOnly -> Decision.Claimed(keyConfig)

                gesture != null -> Decision.RomGesture(keyConfig, gesture)

                //  族里的**中间态码**：认下就吞，既不派发、也不放行给 ROM
                RomKeyFamilies.isNonGestureMember(keyEvent.keyCode) -> Decision.RomSwallow(keyEvent.keyCode)

                else -> Decision.Claimed(keyConfig)
            }
        }
        //  **停用的键一律放行**，内置侧键也不例外：`KeyConfig.enabled = false` 的语义是
        //  「这颗键不归模块管，行为与原生完全一致」—— 吞掉它等于把键弄失灵。
        //  侧键放行之后 ROM 会照自己的动作走（本机是打开侧键设置页，那一页被模块替换过）——
        //  这是原生行为，不是模块弹的。真正要挡的是**别的键**（模块认领的那些）带出来的场景派发，
        //  那一条判在宿主侧（`SideKeyGestureHooks`：按「这一下是不是模块认领的」判）。
        // 只在按下那一下打：闸门关着时每一按都会走到这里
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            XLog.d { "${keyConfig.id.label}（$point）：这颗键没开接管，放行给系统（走原生行为）" }
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
     *
     *  策略类那条的**抬手**记 `d`（不记就等于漏了一半证据）：一颗键「按下与抬手用两个码」时，
     *  只看按下那一行分不出来 —— 而这正是「长按换了颗码上报」的形状，也是「这颗键根本
     *  不发抬手」与「发了但被我吞了」的唯一分界。log 包里看得到，release 里被消灭，符合
     *  「高频路径留 d」的口径。
     *
     *  PM 那条：它本来就能收到所有按键，按 `d` 记、且仍然只记按下（真机排查时才看）。
     */
    private fun logUnclaimed(
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
        raw: Boolean,
    ) {
        // PM 那条本来就能收到所有按键：**同一颗码只在前几次报 W**，之后退到 d 免得刷屏。
        if (raw) {
            if (keyEvent.action != KeyEvent.ACTION_DOWN) return
            val code = keyEvent.keyCode
            if (unclaimedLogged.add(code)) {
                XLog.w("按键（$point）：keyCode=$code 不在认领白名单，放行给系统（这颗码只报这一次）")
            } else {
                //  惰性：这一行**每条未认领的按键**都走一遍（PM 兜底那条收所有按键），release 里
                //  整段被编译期消灭，字符串不该在调用点先拼出来
                XLog.d { "按键（$point）：不认识的 keyCode=$code，放行给系统" }
            }
            return
        }
        if (keyEvent.keyCode in QUIET_KEY_CODES) return
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            XLog.w("按键（$point）：keyCode=${keyEvent.keyCode} 不在认领白名单，放行给系统")
        } else {
            // 抬手也记一条（`d`，只在 log 包里看得到）：**没认领的键只记按下**会漏掉一类关键证据
            //  ——「按下与抬手的码不同」正是这颗键长按换了个码上报的形状，而按下的那一行看不出来。
            //  同时它也让「这颗键根本不发抬手」变得可见（按住不放也永远等不到 UP）。
            XLog.d { "按键（$point）：keyCode=${keyEvent.keyCode} 未认领键的抬手（放行给系统）" }
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
            //  「只参与非首键的按住组合」的键走另一条更短的路：不判单键手势，收发都交给那本账
            //  （压住 / 什么时候还回去，见 `ComboOnlyNative`）。
            if (keyConfig.comboOnly) {
                return comboOnlyNative.onKey(id, point, keyEvent)
            }
            // 判定参数**不在这里刷**：判定器直接读传进去的 `keyConfig`（每颗键各一份）。
            //  原先判定器上是两个全局槽，每事件前刷一次 —— B 键的 DOWN/UP 夹在 A 键的
            //  DOWN/UP 之间时，A 的抬手会用 B 的窗口判（多颗自定义键同时在用时才撞）。
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount > 0) {
                // 按住不放的 repeat：不能喂判定器（会刷新 pressAt、作废已排的长按枪），
                // 也不能放行给系统（键已被接管）—— 直接吞掉。框架自己的分支也先判 getRepeatCount()==0。
                //
                //  只给**第 1 发**重复留一行：它是「这颗键真的被按住」的唯一证据。有的键上报的是
                //  「按下即抬起」的一对合成事件（DOWN 与 UP 只差几毫秒），那种键永远判不出长按 ——
                //  有这一行就能一眼分开「判定坏了」与「这颗键根本不会保持按下」。
                if (keyEvent.repeatCount == 1) {
                    XLog.w("${id.label}（$point）：按住不放（第 1 发重复）keyCode=${keyEvent.keyCode}")
                }
                true
            } else {
                when (keyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        // DOWN/UP 是验收判据链的第一环（AGENTS.md 二、1.2），必须是 W ——
                        // 注入侧 i/d 在 LSPosed 日志页里看不到；一次按键两行，不会刷屏。
                        //  字面量**不要动**（六、2：判据级日志的字面量是接口，改它等于改判据）。
                        XLog.w("${id.label}（$point）：DOWN keyCode=${keyEvent.keyCode}")
                        // d 级（真机排查才看）：`deviceId` 与 `flags` 是分开「人按的」与
                        //  「ROM 合成的一对事件」的唯一证据 —— 后者的 DOWN/UP 只差几毫秒，
                        //  靠时长判长按先天不成立。字面量是新加的一行，不动上面那条判据。
                        //  拼串必须惰性：release 下整段被编译期消灭，不该在调用点先求值一次。
                        XLog.d {
                            "${id.label}（$point）：DOWN 明细 deviceId=${keyEvent.deviceId}" +
                                " source=${keyEvent.source} repeat=${keyEvent.repeatCount}" +
                                " flags=${keyEvent.flags}"
                        }
                        // 两颗键一起按 ⇒ 这一下归组合，不再喂单键判定器
                        //  （组合成立时那两颗键的单键状态机已被摘掉）
                        when (combo.onDown(id)) {
                            //  归组合：两颗键那一下只出一个结果
                            ComboDetector.Down.TAKEN -> {
                                return true
                            }

                            //  参与组合但还没凑成：先压一个窗口，看第二颗键来不来。
                            //  等多久按**这一颗键**参与的几组里最长的那一档（各组窗口可能不同）
                            ComboDetector.Down.HOLD -> {
                                held.hold(id, keyConfig, ConfigChannel.holdWindowMsFor(id))
                                return true
                            }

                            //  与组合无关：照旧立刻判单键（这条路上没有可等的东西）
                            ComboDetector.Down.PASS -> {}
                        }
                        detector.onKeyDown(id, keyConfig)
                        true
                    }

                    KeyEvent.ACTION_UP -> {
                        //  还压着（在等组合窗口）⇒ 只记「松了」，到点再连着 UP 一起补给单键判定器。
                        //  **不在这里就补**：配对看的只是两颗键的 DOWN 相差（`ComboDetector.recent`），
                        //  松手那一刻还看不出结果 —— 立刻补的话先按的那颗单击会先跑掉，
                        //  窗口内第二颗键再来，组合也跟着响（一次按键三下动作）。
                        val waiting = held.release(id)
                        //  取消优先于一切（`FLAG_CANCELED`）：KeyEvent 没有 ACTION_CANCEL 这个
                        //  action，取消只是这一个位。排在组合 / 等窗之前判，是因为那两条路都会
                        //  把它当成一次**正常松手** —— 组合那边当场派发短按，等窗那边到点补一次
                        //  完整按压，两种都是「窗口失焦 / 来电打断后凭空触发一下」。
                        if (keyEvent.isCanceled) {
                            held.drop(id)
                            combo.onUp(id, short = false)
                            //  取消也是「这一按结束了」：长按若已判过，连发链与这一按的状态都要撤掉。
                            XLog.w("${id.label}（$point）：这一按被取消（FLAG_CANCELED）→ 撤掉判定")
                            detector.reset(id)
                            return true
                        }
                        //  组合那笔账无论走哪条路都要结：它要把「还按着」那颗的状态清掉。
                        //  顺序不能反 —— 组合在窗口内成立时会把压着的那一枪摘掉，
                        //  所以 `waiting` 与它不会同时为真。
                        if (combo.onUp(id) || waiting) return true
                        XLog.w("${id.label}（$point）：UP keyCode=${keyEvent.keyCode}")
                        detector.onKeyUp(id, keyConfig)
                        true
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
}

/** 从实参里挑出第一个 `KeyEvent`（按键热路径，不要 `filterIsInstance`）。 */
private fun firstKeyEvent(args: List<Any?>): KeyEvent? {
    for (a in args) if (a is KeyEvent) return a
    return null
}

/**
 * 这一下抬手是不是「收集期被吞掉那一下的尾巴」—— 判据与记账见 `decide` 里那段注释。
 *
 *  放在对象外只为压 [SideKeyInterceptor] 的成员数；这本来就是一条纯判定。
 */
private fun justSwallowed(
    collecting: Boolean,
    swallowedAt: Long?,
): Boolean {
    if (collecting || swallowedAt == null) return false
    return SystemClock.uptimeMillis() - swallowedAt < COLLECT_SWALLOW_TAIL_MS
}

/**
 * 收集期那一段：**旁路上报 → 按下记账 → 抬手摘表**，返回「这一下该整条吞掉吗」。
 *
 * 「自定义按键」页开着监听时，先把这一下**旁路**报回去。刻意摆在白名单**之前**：
 * 那一页要的是「用户按了哪些键」，摆到白名单后面就只剩侧键自己那份码，别的键一个都听不到。
 *
 * 收集期**一律吞掉**：弹窗是模态的，这时按任何键都是在「报键」，不是在用它。不吞的话音量会
 * 真的调音量、电源会真的弹菜单（还把弹窗盖掉）—— 用户看到的是「按了没加上，还顺手改了别的东西」。
 * 最长影响由 [KeyWatch] 的 60 秒自动关兜着，弹窗一关就恢复。
 *
 * 收集期被吞掉的那一下，它的**抬手也要吞**：只吞 DOWN 的话，判定器收到一条没有按下的 UP，
 * 会按「孤立抬手」补判一次单击 —— 侧键的动作照样执行（真机实测：弹窗一关，侧键立刻跑了一次 OCR）。
 *
 * **摘表与判据分开**：只要是 UP 就先把这一颗的记账摘掉。写成「先判 justSwallowed 才摘」的话，
 * 收集期里到达的那条 UP 会被 `!collecting` 短路掉、记账留着；下一条真实按压缩在窗口内时，
 * 它正常的抬手就会被上一轮那条记账吞掉 —— 判定器收到一条没有抬手的 DOWN，长按于是误触发。
 *
 * **每一颗键的按下都要重写这笔账**：记账只对「这一下被吞掉、它的抬手也要吞」有意义，必须属于
 * **最近这一次按下**。收集期留下的旧账若一直挂到下一次正常按压，那次抬手就会把旧账摘走并判成
 * `justSwallowed` —— 那条 UP 被吞掉，判定器只收到 DOWN（单击不再派发、长按一直挂着）。
 */
private fun swallowWhileCollecting(
    swallowedDownAt: MutableMap<Int, Long>,
    keyEvent: KeyEvent,
): Boolean {
    KeyWatch.report(keyEvent)
    val collecting = KeyWatch.active
    //  记下这一下「按下」：弹窗很可能**因为这一下而关掉**（收下了 / 被手动关掉），
    //  紧跟的 UP 就不再属于「收集期」了。不在收集期时这一颗的账一律清掉 —— 那说明上一次的
    //  记账已经过期（它的 UP 丢了），留着只会吞掉这一下正常的抬手。
    if (keyEvent.action == KeyEvent.ACTION_DOWN) {
        if (collecting) {
            swallowedDownAt[keyEvent.keyCode] = SystemClock.uptimeMillis()
        } else {
            swallowedDownAt.remove(keyEvent.keyCode)
        }
    }
    val swallowedAt =
        if (keyEvent.action == KeyEvent.ACTION_UP) {
            swallowedDownAt.remove(keyEvent.keyCode)
        } else {
            null
        }
    return collecting || justSwallowed(collecting, swallowedAt)
}

/**
 * 「补发出去的原生事件」这份账能活多久（毫秒）—— 见 [ComboOnlyNative.echo]。
 *
 *  异步投递通常几毫秒就回来，取 2 秒是留余量。**必须有上限**：投失败留下的残账会让
 *  「用户下一次真的按这颗键」被当成回声放行，那颗键就静默失效一次。
 */
private const val ECHO_TTL_MS = 2_000L

/**
 * 补发过原生 DOWN 之后，等真实抬手的上限（毫秒）—— 见 [ComboOnlyNative.scheduleHoldGuard]。
 *
 *  取 10 秒：电源长按弹关机菜单最长也就几秒，再久说明这一条抬手根本不会来了（ROM 在长按之后
 *  换了颗码上报抬手，那条 UP 认不到这颗键）。到点必须替它补一条抬起 ——
 *  系统那边「一直按着」的表现是音量一路涨到底，比少一次长按难收拾得多。
 */
private const val HOLD_GUARD_MS = 10_000L

/**
 * 「只参与非首键的按住组合」那颗键的收尾账本 —— **压住的那一下怎么还给系统**，只有这一处说了算。
 *
 * ## 为什么按下要压住
 *
 *  组合判定要在窗口内看「第二颗键会不会来」。这颗键的原生动作（音量 ± / 电源）一旦发生就
 *  撤不回来，所以**还可能配成组合的那几下**先吞，等判定出结论再决定归谁：凑成组合就永久吞，
 *  没凑成就还回去。**已经不可能配成的那几下不压**（见下节第三种情形）—— 压了只会白慢一拍。
 *
 * ## 压到什么时候：两种要压、一种一下都不压
 *
 *  - **等窗口那几档的成员**（`ComboKey.isWait`）压到窗口到点为止：那个数就是「第二颗还来不来得及」
 *    的答案，到点还没凑成就不必再等（用户口径：先吞后补，单按只晚一个窗口）。
 *  - **按住那几档的起点键**（`ComboKey.starterKey`，见 `ConfigChannel.holdStarter`）**没有上界**：
 *    判据只要求那一颗还按着，按住三秒再补第二颗照样成立。它**压到抬手**（[deferred]）——
 *    窗口到点就把原生事件漏出去的话，之后凑成的按住组合只能是「一次按压两个动作」。
 *    代价写在 `docs/runtime/key-to-gesture.md`：那颗键在这一档里**不再出原生长按**
 *    （音量连续调 / 电源长按菜单），只补一次短按。
 *  - **两者都不是 ⇒ 一下都不压**：那一组既不认窗口、这颗键又不是先按住的那一颗，按下那一刻
 *    就已有定论 —— 另一颗若正按着，组合在 `combo.onDown` 里当场认领（这一下照吞）；没按着，
 *    则这一下**永远配不上**（按住那条要求另一颗先按着，而窗口那条根本不认）。所以原生事件
 *    原样交给系统，一秒都不压，也不用再补一条注入事件回去。
 *
 * ## 为什么「还回去」不是一次短按就完事
 *
 *  还回去的那一下要**接着还原用户真正的按法**：
 *
 *  - 窗口到点时用户**已经松手** ⇒ 补一对 DOWN + UP（系统只该看到一闪，这就是原生短按）；
 *  - 窗口到点时用户**还按着** ⇒ 只补 DOWN，抬手那一下等它与真实事件一起收尾。
 *    先补 UP 就等于把这一按截成短按 —— 系统的长按计时从收到 DOWN 起算，UP 一到就结束，
 *    于是原生长按（音量连续调 / 电源长按菜单）永远出不来。
 *
 *  按住的过程中真实重复事件也要转发：注入的 DOWN 不走 InputReader，系统自己产不出 repeat，
 *  而「按住连续调音量」正是靠 repeat 一档档往上走的。
 *
 * ## 几张账本的分工
 *
 *  - [swallowed]：这一下按下被吞过 ⇒ 抬手要跟着吞（否则系统只收到半截，键卡在按住状态）；
 *  - [pending]：压住了、**等着窗口到点补发**，值兼作这一下的版本号；
 *  - [released]：窗口没走完就松手了的那几颗 —— [pending] 的账**不能提前摘**，否则窗口到点那一枪
 *    按 `downAt` 认不到这一笔，补发永远发不出去（快按一下什么都不发生）；
 *  - [holding]：**已经补发过 DOWN**、等真实抬手来收尾 —— 进这张表就意味着系统那边按着，
 *    所以离开它时必须补一条 UP（真实抬手、配置变更、兜底超时三个出口都走同一个函数）；
 *  - [deferred]：按住那条**先按住的那一颗**，**压到抬手**再补一对（没有窗口任务，见上面那段）。
 *
 * ## 线程
 *
 *  写都在输入线程（`SideKeyInterceptor` 的按键回调），读在主 looper 的延迟任务上。单独一张表
 *  各自是原子的，但**一次收尾要同时动两张**（「还按着吗」→「记进 holding」；「从 holding 摘掉」
 *  →「补一条 UP」），两条线程交错时会出现「DOWN 补了、UP 没人补」—— 系统那边一直按着，
 *  表现是音量一路涨到底。所以三张表的转移一律在 [lock] 里做，**投递也放在锁里**：
 *  「公布的顺序」必须等于「系统收到事件的顺序」。
 */
internal class ComboOnlyNative(
    private val handler: Handler,
    private val combo: ComboDetector,
    /** 这一颗键按下之后要压多久才轮到窗口那条还回去（`ConfigChannel.holdWindowMsFor`）。 */
    private val windowMs: (KeyId) -> Long,
    /** 这一颗键会不会被接在后面 —— 即它参与的某一组认窗口那条（`ConfigChannel.joinsCombo`）。 */
    private val windowMember: (KeyId) -> Boolean,
    /** 这一颗键可能是某一组按住组合里**先按住的那一颗**（`ConfigChannel.holdStarter`）—— 是就压到抬手。 */
    private val holdStarter: (KeyId) -> Boolean,
) {
    /**
     * 三张表转移的闸 —— 见类注释的「线程」。
     *
     *  持锁期间只做三件事：改表、投一条事件、排一枪。投递之所以留在锁里是因为它**就是那条
     *  「顺序」本身**；投递是异步的（`INJECT_MODE_ASYNC`），不会在这把锁上回头再进来。
     */
    private val lock = Any()

    /** 这一下已经吞过了的那几颗键 id。 */
    private val swallowed = ConcurrentHashMap.newKeySet<String>()

    /** 压住了、等窗口到点补发 —— 键 id → 按下时刻（兼作版本号）。 */
    private val pending = ConcurrentHashMap<String, Long>()

    /** [pending] 里那几笔**已经松手**的 —— 窗口到点按这一笔补一对 DOWN + UP（见类注释）。 */
    private val released = ConcurrentHashMap.newKeySet<String>()

    /** 已经补发了原生 DOWN、等真实抬手来收尾 —— 键 id → 那一颗的键码。 */
    private val holding = ConcurrentHashMap<String, Int>()

    /** 压着、等**抬手**再补一对 —— 键 id → 那一颗的键码（按住那条的第一颗，见类注释）。 */
    private val deferred = ConcurrentHashMap<String, Int>()

    /** 补发出去、还没被拦截链认领的事件 —— keyCode → 剩余条数与过期时刻。 */
    private val echo = ConcurrentHashMap<Int, Echo>()

    /** 一份回声账 —— 剩余条数与过期时刻。 */
    private class Echo(
        val left: Int,
        val until: Long,
    )

    /**
     * 这一下是不是**我们自己补发出去的回声** —— 是就销一次账并返回 true（调用方放行）。
     *
     *  补发的事件会重新走一遍输入分发、也就是再进一次拦截链：认不出来就是死循环
     *  （吞掉 → 补发 → 又被吞 → 又补发）。
     *
     *  这一支**不进 [lock]**：它只动 [echo]，而 [echo] 自己的 `compute` 就是原子的；
     *  它在拦截链的最前面，每次按键都要问一遍。
     */
    fun consumeEcho(keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        var seen = false
        echo.compute(keyCode) { _, cur ->
            when {
                cur == null || now > cur.until -> {
                    null
                }

                else -> {
                    seen = true
                    if (cur.left <= 1) null else Echo(cur.left - 1, cur.until)
                }
            }
        }
        return seen
    }

    /**
     * 喂一个按键事件。
     *
     * @param point 只用于日志（同一次物理按压可能被不止一层看到）
     * @return true = 吞掉，false = 原样放行给系统
     */
    fun onKey(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
    ): Boolean =
        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> onDown(id, point, keyEvent)
            KeyEvent.ACTION_UP -> onUp(id, point, keyEvent)
            else -> false
        }

    private fun onDown(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
    ): Boolean {
        //  按住不放的重复：**已经补发过原生 DOWN 就转给系统**（长按连续动作靠它），
        //  否则连着吞 —— 放行的话系统会收到一个没有 DOWN 的 repeat，那颗键在它眼里从没被按过。
        //  读 `holding` 与转发同处一把锁：抬手正好在中间就会转出一条「已经松开之后」的 repeat。
        if (keyEvent.repeatCount > 0) {
            synchronized(lock) {
                val code = holding[id.id]
                if (code != null) forward(code, KeyEvent.ACTION_DOWN, keyEvent.repeatCount)
            }
            return true
        }
        val downAt = SystemClock.uptimeMillis()
        val taken = combo.onDown(id) == ComboDetector.Down.TAKEN
        //  两条「要不要压」的判据各查一次表（按键热路径，别为了一句注释查两遍）
        val windowMemberHere = windowMember(id)
        val holdStarterHere = holdStarter(id)
        //  **这一下没有任何理由压住**（见类注释的第三种情形）：那一组不认窗口、这颗键也不是
        //  先按住的那一颗 —— 组合要么已经在上面那一句里当场成立，要么永远配不上。原样交给系统，
        //  一秒都不压，也不必再补一条注入事件回去。
        if (!taken && !windowMemberHere && !holdStarterHere) {
            XLog.d { "${id.label}（$point）：不认窗口、也不是先按住的那颗 → 原样交给系统" }
            return false
        }
        //  压到哪一步：窗口那条压到窗口到点，按住那条压到抬手（见类注释）。
        val waitForRelease = !taken && holdStarterHere
        synchronized(lock) {
            //  走到这里就是「这一下要压」（上面那两级判据已经排除掉不必压的）：先吞，
            //  凑成了永久吞、没凑成再由窗口到点或抬手还回去（用户口径：组合判定不该触发原生事件）。
            swallowed.add(id.id)
            //  上一次那一下的「已松手」止于这次按下：新的一下从按下算起
            released.remove(id.id)
            when {
                taken -> {
                    pending.remove(id.id)
                    deferred.remove(id.id)
                }

                waitForRelease -> {
                    //  **时间戳当版本号**只在窗口那条有用；这一档没有窗口任务，抬手那次自己认这一笔
                    deferred[id.id] = keyEvent.keyCode
                }

                else -> {
                    pending[id.id] = downAt
                }
            }
        }
        if (taken) {
            XLog.w("${id.label}（$point）：这一下归组合（只参与非首键的按住组合），连原生事件一起吞掉")
        } else if (waitForRelease) {
            XLog.w("${id.label}（$point）：按下先压住（只参与非首键的按住组合），抬手时再还回去")
        } else {
            //  时间戳当这一下的**版本号**：同一颗键在窗口内又被按到时，旧任务到点会发现
            //  账已经被新的一次改写了，不会替新的那一下做主（补发错次数比不补发更难查）。
            scheduleFallback(id, keyEvent.keyCode, downAt)
            XLog.w("${id.label}（$point）：按下先压住（只参与非首键的按住组合），窗口内没凑成就把原生事件还回去")
        }
        return true
    }

    private fun onUp(
        id: KeyId,
        point: Framework.InterceptionPoint,
        keyEvent: KeyEvent,
    ): Boolean {
        //  组合那笔账要结（`down` / `consumed` / 待发的短按都在 `onUp` 里）。
        //  返回值 = 这一下被组合认领了 —— 压到抬手那一档据此决定还要不要补发。
        val claimed = combo.onUp(id, short = !keyEvent.isCanceled)
        var heldCode: Int? = null
        var deferredCode: Int? = null
        //  这一下还压着（窗口没走完就松手了）—— 抬手照样吞，等窗口到点补一对
        var stillPending = false
        synchronized(lock) {
            heldCode = holding.remove(id.id)
            deferredCode = deferred.remove(id.id)
            if (claimed) {
                pending.remove(id.id)
            } else if (pending.containsKey(id.id)) {
                //  **这里不能摘账**：窗口到点那一枪按 `downAt` 认这一笔，摘掉之后就对不上，
                //  补发再也发不出去 —— 表现是「这颗键快按一下，原生动作为零、模块也不判单键」。
                //  改记一笔「已松手」，到点补一对（系统只该看到一闪，见类注释）。
                released.add(id.id)
                stillPending = true
            }
            //  补发过 DOWN ⇒ 抬手也跟着补，真实那条吞掉。**不能放行真实抬手**：系统收到的
            //  那个 DOWN 是我们注入的，两条事件的来源对不上，按同一颗键收尾会错位。
            //
            //  压到抬手的那一下（[deferred]）在这里才补一对：系统只该看到一闪，这就是原生短按。
            //  已经被组合认领的（`claimed`）什么都不补 —— 这一下归组合。
            when {
                heldCode != null -> {
                    forward(heldCode, KeyEvent.ACTION_UP, 0)
                }

                deferredCode != null && !claimed -> {
                    forward(deferredCode, KeyEvent.ACTION_DOWN, 0)
                    forward(deferredCode, KeyEvent.ACTION_UP, 0)
                }
            }
        }
        when {
            heldCode != null -> {
                //  这一下从此结清：留着这笔账会让「下一颗键按下照原样放行、抬手却被吞」
                //  —— 系统那边那颗键就卡在按住状态。
                swallowed.remove(id.id)
                XLog.w("${id.label}（$point）：补发的原生按下已松开（只参与非首键的按住组合）")
                return true
            }

            deferredCode != null && !claimed -> {
                XLog.w("${id.label}（$point）：没凑成组合（只参与非首键的按住组合），抬手时把原生短按补发回去")
                return true
            }

            //  被组合认领的这一下：**DOWN 已经被吞**（「要压」那一段必进 `swallowed`），
            //  真实抬手必须跟着吞。认领那一刻 `scheduleFallback` 会把 `swallowed` 摘掉，
            //  于是这里会落到最后那行 `remove` 返回 false ⇒ 系统收到一条**没有 DOWN 的孤立抬手**，
            //  按 UP 判定的原生动作（相机快门、电源短按那一类）可能被误触发。
            claimed -> {
                swallowed.remove(id.id)
                XLog.w("${id.label}（$point）：这一下归组合，真实抬手一并吞掉")
                return true
            }

            stillPending -> {
                XLog.w("${id.label}（$point）：窗口内就松手了（只参与非首键的按住组合），到点补一对原生短按")
                return true
            }
        }
        return swallowed.remove(id.id)
    }

    /**
     * 窗口走完：没被任何一组组合认领 ⇒ 把这一下还给系统。
     *
     * ## 为什么必须等满窗口
     *
     *  抬手早于窗口时组合**还可能凑成**（先后按下也算组合）—— 那时提前补发就是双重触发：
     *  系统先收到一次原生按下，组合又执行一次。
     *
     * ## 窗口按这一颗键自己那一档取
     *
     *  `ConfigChannel.holdWindowMsFor` = 它参与的几组里最长的那个整串跨度。按全局最宽的那一档
     *  等是白等：别的组合成员更多、窗口更宽，与这一颗毫无关系，而多等的每一毫秒都是
     *  这一颗键原生行为被推迟的量。
     *
     *  **只服务窗口那条**：按住那条的第一颗压根不排这一枪（见 [deferred]）。
     */
    private fun scheduleFallback(
        id: KeyId,
        keyCode: Int,
        downAt: Long,
    ) {
        val wait = (downAt + windowMs(id) - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        handler.postDelayed(
            {
                //  这一枪跑在 **system_server 的主 looper** 上：抛出去就是整机崩。同族的
                //  `scheduleHoldGuard` 与 `GestureDetector` 那几枪都包了兜底，这两枪当初漏了 ——
                //  现在只是 callee 恰好不抛，属于潜伏。正文拆到 [fallbackTick] 只为整段包得住。
                try {
                    fallbackTick(id, keyCode, downAt)
                } catch (e: Throwable) {
                    XLog.w("${id.label}：组合窗口到点那一枪失败（已忽略）", e)
                }
            },
            wait,
        )
    }

    /** [scheduleFallback] 那一枪的正文（见那里为什么单独拆出来）。 */
    private fun fallbackTick(
        id: KeyId,
        keyCode: Int,
        downAt: Long,
    ) {
        //  四种结局各自只做一件事，「改表」与「投递」在同一把锁里：
        //  · 账已经换过（同一颗键又被按下）⇒ 什么都不做，别替新的那一下做主；
        //  · 被组合认领了（晚到）⇒ 什么都不做；
        //  · 窗口内就松手了 ⇒ 补一对（系统只该看到一闪）；
        //  · 还按着 ⇒ 补 DOWN，记进 holding，等真实抬手收尾。
        var outcome = 0
        synchronized(lock) {
            if (pending.remove(id.id) == downAt) {
                when {
                    combo.comboCovers(id) -> {
                        swallowed.remove(id.id)
                        released.remove(id.id)
                        outcome = 1
                    }

                    released.remove(id.id) -> {
                        swallowed.remove(id.id)
                        forward(keyCode, KeyEvent.ACTION_DOWN, 0)
                        forward(keyCode, KeyEvent.ACTION_UP, 0)
                        outcome = 3
                    }

                    swallowed.contains(id.id) -> {
                        holding[id.id] = keyCode
                        forward(keyCode, KeyEvent.ACTION_DOWN, 0)
                        outcome = 2
                    }

                    else -> {
                        forward(keyCode, KeyEvent.ACTION_DOWN, 0)
                        forward(keyCode, KeyEvent.ACTION_UP, 0)
                        outcome = 3
                    }
                }
            }
        }
        when (outcome) {
            1 -> {
                XLog.w("${id.label}：这一下被组合认领（晚到），不再补发原生事件")
            }

            2 -> {
                scheduleHoldGuard(id, keyCode)
                XLog.w("${id.label}：没凑成组合（只参与非首键的按住组合），原生按下已补发（按住可出长按）")
            }

            3 -> {
                XLog.w("${id.label}：没凑成组合（只参与非首键的按住组合），已把原生短按补发回去")
            }
        }
    }

    /**
     * 补过 DOWN 之后替它收尾的兜底 —— 见 [HOLD_GUARD_MS]。
     *
     *  正常路径上这一枪到点时账早就被真实抬手摘掉了（空操作）。
     */
    private fun scheduleHoldGuard(
        id: KeyId,
        keyCode: Int,
    ) {
        handler.postDelayed(
            {
                //  同 [scheduleFallback]：主 looper 上不许抛出
                try {
                    holdGuardTick(id, keyCode)
                } catch (e: Throwable) {
                    XLog.w("${id.label}：按住兜底那一枪失败（已忽略）", e)
                }
            },
            HOLD_GUARD_MS,
        )
    }

    /** [scheduleHoldGuard] 那一枪的正文。 */
    private fun holdGuardTick(
        id: KeyId,
        keyCode: Int,
    ) {
        var orphan = false
        synchronized(lock) {
            if (holding.remove(id.id) != null) {
                forward(keyCode, KeyEvent.ACTION_UP, 0)
                orphan = true
            }
        }
        if (orphan) {
            XLog.w("${id.label}：按住超过 ${HOLD_GUARD_MS}ms 仍没等到抬手，替它补了一条抬起")
        }
    }

    /**
     * 配置变了 / 全部键都不接管了 —— 把账清干净。
     *
     *  **已经补发过 DOWN 的那几颗要补一条 UP**：不清的话系统那边那颗键永远按着，
     *  表现是音量一路涨到底。压着还没补的那几下（窗口那条的 [pending] 与按住那条的
     *  [deferred]）直接丢：新配置下不该再替旧的一下做主。
     */
    fun reset() {
        synchronized(lock) {
            pending.clear()
            released.clear()
            deferred.clear()
            swallowed.clear()
            val held = holding.values.toList()
            holding.clear()
            held.forEach { code -> forward(code, KeyEvent.ACTION_UP, 0) }
        }
    }

    /**
     * 投一条原生事件，并记一笔回声账。
     *
     *  **先记账再投**：投出去之后事件可能**立刻**在另一条线程上被拦到，后记就来不及
     *  （漏掉的那一次会被当成新的按下再压一个窗口）。
     */
    private fun forward(
        keyCode: Int,
        action: Int,
        repeat: Int,
    ) {
        echo.compute(keyCode) { _, cur ->
            Echo((cur?.left ?: 0) + 1, SystemClock.uptimeMillis() + ECHO_TTL_MS)
        }
        if (!injectNativeKey(keyCode, action, repeat)) {
            //  投失败：销掉刚记的那一条，否则 2 秒内用户真的按这颗键会被当成回声放行
            echo.compute(keyCode) { _, cur ->
                when {
                    cur == null || cur.left <= 1 -> null
                    else -> Echo(cur.left - 1, cur.until)
                }
            }
            XLog.w("只参与非首键的按住组合：原生事件没补出去（keyevent:$keyCode action=$action）")
        }
    }
}
