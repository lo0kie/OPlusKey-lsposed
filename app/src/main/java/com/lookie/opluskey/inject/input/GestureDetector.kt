package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import java.util.concurrent.ConcurrentHashMap

/**
 * 「同一次按压的两种形态」之间的时间窗（毫秒）。
 *
 * 策略类那条路，同一次按压会给出两种形态：没有 KeyEvent 的直判与带 KeyEvent 的 DOWN/UP，
 * 两者是同一串调用、前后只差毫秒级。不能拿双击窗口（`doubleClickMs`）当这个窗口 ——
 * 那样紧接着的下一次按压会被当成同一下吞掉。而两次按压之间至少隔着一次抬手：
 * 两者的下限里更小的是 100ms（双击窗口；长按阈值是 200ms）—— 50ms 既盖得住同一串调用，
 * 又碰不到下一次按压。
 */
private const val DIRECT_BURST_MS = 50L

/**
 * 「长按连续触发」的外部终止上限（毫秒）：距按下超过它仍未见抬手，视为 UP 已丢
 * （锁屏打断 / 非FLAG_CANCELED 的丢弃），强制停掉自循环派发链。
 */
private const val REPEAT_MAX_MS = 30_000L

/**
 * 「松手才触发」的**兜底等待**（毫秒）：长按到点之后再等这么久还没见到抬手，就按「UP 已丢」处理 ——
 * 就地执行一次，并把这一次按压封掉（见 `Pending.closedWithoutUp`）。
 *
 *  为什么必需：这一档把**唯一一次**执行押在抬手事件上。默认时序没有这个问题（到点已经执行过），
 *  而这里 UP 一丢就什么都不执行，表现和「这颗键坏了」一模一样 —— 与连续触发那条同一个失败面
 *  （那边有 `REPEAT_MAX_MS` 兜底，这边原本一条都没有）。
 *
 *  为什么是 15s：正常抬手在几十～几百毫秒内到；15s 之外还按着只有两种可能 —— UP 真丢了，
 *  或者用户刻意按住超过 15s。后者提前执行一次也远好过**什么都不做**，所以宁可这样划界。
 */
private const val RELEASE_FALLBACK_MS = 15_000L

/**
 * 唤醒锁比双击窗口多留的余量（毫秒）。
 *
 *  窗口那一枪要等 `postDelayed` 到点才会跑主 looper 上的回调，末段那几毫秒落在余量里，
 *  否则窗口走完、枪还没响的那一瞬设备就能睡着，窗口又白等。
 */
private const val AWAKE_MARGIN_MS = 500L

/**
 * 按键的手势判定状态机 —— 本模块的核心逻辑。
 *
 * 线程归属：`interceptKeyBeforeQueueing` 由 InputReader / Dispatcher 线程调，计时器走调用方
 * 给的 `handler`（main looper）—— `Pending` 的字段被这两条线程**都写也都读**（输入线程写
 * `sawFirstRelease` / `pressAt`，主 looper 那一枪写 `longPressAt` / `longPressFired`）。所以：
 *  · 跨线程的字段一律 `@Volatile`，**没有例外**：`doubleFired` 的写者除了输入线程，
 *    还有 [reset] 的两条外部调用路径 —— 主 looper 的连发任务、以及配置广播线程
 *    （`SideKeyInterceptor.cancelPending`）。它不参与「先判长按还是先判双击」那种读改写竞态，
 *    但清零必须让输入线程看得见，否则上一按的标记会漏到下一按。
 *  · 抬手时**摘掉**长按那一枪（[cancelLongPressShot]）：到点读一眼 `sawFirstRelease` 是「再看一眼」，
 *    摘枪是「到点之前就没了」，后者与线程调度无关；
 *  · 但 `check → act` 那几段仍无锁，极端竞态下「既判长按又判单击」只是变小、没消失 ——
 *    彻底收口要把状态机归到一个线程上。也不能改用 `handler.post`（`onIntercept` 须同步返回是否接管）。
 *
 * ## 时间基准一律 `elapsedRealtime()`
 *
 *  不用 `uptimeMillis()`：它在**深度睡眠里不走**。熄屏按一下、抬手之后设备立刻睡着时，
 *  「双击窗口」那一枪（`postDelayed`，按 uptime 排）根本不会到点，而窗口的截止时刻却还停在
 *  「刚刚」—— 下一次按下（真实过了几秒）会被判成双击、并把前一下的单击吞掉。真机日志：
 *  熄屏按一下没有单击，4.6 秒后再按一下却判成「双击」。
 *  `elapsedRealtime()` 含睡眠，窗口按真实时间走，两处口径也就一致了（转发给宿主的
 *  `sent_at` 用的也是它）。
 *
 * ## 熄屏也判双击（窗口那一段把 CPU 顶醒）
 *
 *  熄屏后设备很快进深睡，`postDelayed` 按 uptime 排、深睡里不走 ⇒ 窗口那一枪不到点。
 *  所以排窗时先请装配方顶住 CPU（[holdAwake]，屏幕亮着时它什么都不做），让 CPU 在这一个
 *  窗口里继续走：熄屏也能判双击，代价是熄屏单击要晚一个窗口（`doubleClickMs`）才执行。
 *  这是取舍 —— 当初「熄屏抬手就当单击」是为了熄屏即时响应，但它把双击整档废掉了。
 *
 *  成员数贴着 `TooManyFunctions` 的阈值：单击 / 双击 / 长按 / 连续触发各有一套计时与取消，
 *  拆文件就得把 `Pending` 与那三个 `Runnable` 一起搬出去 —— 状态机被切成两半更难跟。
 */
@Suppress("TooManyFunctions")
internal class GestureDetector(
    private val handler: Handler,
    /**
     * 每判出一个手势回调一次（链路自检记「最后一次判出手势的时刻」）。
     *
     * 由装配方注入（`KeyHooks` 装配时传 `noteGesture`），判定器自己不认识 `KeyHooks` ——
     * 直接回写那个单例会形成 `inject.input` 与 `inject` 的包级环。
     */
    private val onGestureNoted: () -> Unit,
    /**
     * 判定出一个手势就回调一次。
     *
     * 第四个参数是「长按连续触发」的重复派发标记：只有长按会重复，
     * 重复那几次不能当新的一次按键（据此跳过振动反馈）。
     */
    private val onGesture: (KeyId, GestureTrigger, KeyConfig, Boolean) -> Unit,
    /**
     * 「屏幕黑着的话，这段时间里别让 CPU 睡」—— 熄屏排双击窗口时用（见类注释「熄屏也判双击」）。
     *
     *  亮着时实现里什么都不做：判定器于是不需要单独知道屏幕状态 —— **「熄屏才顶」是唤醒锁
     *  自己的前置条件**，那件事归装配方（它才拿得到 `PowerManager`）。
     *  由装配方注入（拿借来的 Context 取服务，见 `KeyHooks.holdAwake`）：判定器不碰 Context，
     *  注入侧也不许拿系统进程自己的 Context 取服务。实现必须是「带超时、不主动放」的那种 ——
     *  漏放一次就是 CPU 再也不睡，比多醒几百毫秒严重得多。
     */
    private val holdAwake: (Long) -> Unit,
) {
    /**
     * 一个键的待定状态 —— 字段被两条线程读写，跨线程的一律 `@Volatile`（见类注释）。
     *
     *  `@Suppress("LongParameterList")`：这不是「参数太多、该拆对象」那种函数，而是一份**状态表**
     *  ——每一格都是独立的一个状态位 / 一个待摘的定时器句柄，拆成子对象只是把它们挪个地方，
     *  判定处还得一层层解引用。参数个数就是状态位数。
     */
    @Suppress("LongParameterList")
    private class Pending(
        @Volatile var longPressAt: Long = 0L,
        @Volatile var doubleWindowAt: Long = 0L,
        /**
         * 这一枪双击窗口的长度（`onKeyUp` 排窗时按当时的配置写死）。
         *
         * 必须在排窗那一刻**定格**：`doubleClickMs` 是 volatile，排窗时读一次、到点再读一次，
         * 中途用户改了「单击延迟」就会让两处口径不一致 —— 判定用的窗口与排出的窗口不是同一个，
         * 300ms 内的合法新单击可能被误吞。
         */
        @Volatile var doubleWindowMs: Long = 0L,
        @Volatile var sawFirstRelease: Boolean = false,
        /**
         * 本次按下已经判过长按。
         *
         * 必须单独一个字段：长按定时器触发时会清零 `longPressAt`，用 `longPressAt == 0L` 当判据，
         * 抬手时那个判断恒为假，一次按键会既判长按又判单击。
         */
        @Volatile var longPressFired: Boolean = false,
        /**
         * 本次按下已经判过双击。
         *
         * 与 longPressFired 同理、也是必须的：判双击发生在第二次按下那一刻，而第二次抬手照样会走
         * `onKeyUp`，没有这个标记就会再开一个单击窗口、把双击变成「双击 + 单击」。
         */
        @Volatile var doubleFired: Boolean = false,
        /**
         * 这一次连击链已经数到第几击（`reset` 清零）。
         *
         *  「双击」是它的第 2 档特例：`clicks == 2` 时行为与原先完全一致。第 3 击起的档位
         *  只有手改 / 导入的配置才有（`KeyConfig.extraClicks`），界面没有入口。
         */
        @Volatile var clicks: Int = 0,
        /**
         * 本次按下的时刻（`onKeyDown` 里写、`reset` 里清）。
         *
         * 用来判「这一下是不是已经被直判过了」—— 直判与 DOWN/UP 是同一次按压的两种形态。
         * 必须是时刻、不能只用一个布尔：布尔会被上一次按压的直判污染。
         */
        @Volatile var pressAt: Long = 0L,
        /**
         * 这颗键最近一次「当场判单击」（双击为无操作那条路）的时刻，
         * 用来压掉同一次物理按压被转发两遍造成的重复派发。
         *
         * **按颗存**，不是全局一个：全局的话，A 键刚直判过单击，B 键紧接着那一下
         * 会被算成「已派发过」而吞掉（快速在两颗键之间敲、或短按 A 再长按 B 都会中）。
         */
        @Volatile var lastDirectSingleAt: Long = 0L,
        /** 「长按连续触发」正在跑的重复任务（抬手 / 重置时取消）。 */
        @Volatile var repeatRunnable: Runnable? = null,
        /**
         * 「松手才触发」的兜底那一枪（见 [RELEASE_FALLBACK_MS]），抬手 / 重置时取消。
         */
        @Volatile var releaseFallbackRunnable: Runnable? = null,
        /**
         * 这一次按压已经**替系统补过抬手**（UP 丢了，兜底执行过）。
         *
         *  置上之后，那个**迟到**的 UP 只做清理、不再派发 —— 否则这一按会执行两次。
         *  它只与 `longPressFired` 一起读（都在 `synchronized(p)` 里），见 [finishLong]。
         */
        @Volatile var closedWithoutUp: Boolean = false,
        /**
         * 长按那一枪本身（`postDelayed` 的那个 Runnable），抬手 / 重置时摘掉（[cancelLongPressShot]）。
         *
         * 为什么要把 Runnable 存下来：原来只在到点时读一眼 `sawFirstRelease` 决定响不响，而那次读
         * 跨线程 —— 读到陈旧值就是「明明抬手了还判长按」。摘枪是确定的，不依赖那次读。
         */
        @Volatile var longPressRunnable: Runnable? = null,
        /**
         * 双击窗口那一枪（`postDelayed` 的那个 Runnable），抬手 / 重置 / 排新枪时摘掉。
         *
         * 与 [longPressRunnable] 同一个道理，原来它**没存**（只在到点时读状态），于是
         *  `reset()` 摘不掉它、也没法判「这一枪属于哪一次按压」—— 迟到的旧窗口会读到后来那次
         *  按压的 `sawFirstRelease` / `doubleWindowAt`，抢先补一次单击并把标志清掉。
         */
        @Volatile var doubleWindowRunnable: Runnable? = null,
    )

    private val pendingByKey = ConcurrentHashMap<KeyId, Pending>()

    /**
     * 判定器的**串行闸** —— 所有公开入口都过它。
     *
     *  正常按压来自输入线程，但 `HeldPresses` 的「到点补发」跑在 main looper 上：两条线程可能
     *  同时进同一个键的 `Pending`（前一次按压还在窗口里、后一次已经按下来），`synchronized(p)`
     *  只盖住其中两小段，中间那些读改写会交叉。表现是快速连按时「偶尔少判一次 / 多判一次」。
     *  入口处串起来就够：内部不再阻塞（没有 IO、没有等待），不会把输入线程钉住。
     */
    private val lock = Any()

    /**
     * 取（必要时建）某个键的待定状态。
     *
     * 用 `computeIfAbsent` 而不是 Kotlin 的 `getOrPut`：后者是「先取后写」，
     * 并发首按（两条线程同时进）会各建一个 `Pending`、先写进去的那份状态被丢掉。
     */
    private fun pending(id: KeyId): Pending = pendingByKey.computeIfAbsent(id) { Pending() }

    /**
     * ROM **已经把手势判好**的那一档（压感拍照键的 767 短按 / 768 长按 / 769 双击）——
     * 直接派发，不进时长判定。
     *
     *  为什么不能喂 [onKeyDown] / [onKeyUp]：ROM 判成双击时**只发 769 这一个码**（不会先发
     *  两次 767），靠「两次按下落在 doubleClickMs 内」永远等不到第二次 —— 喂进来只会被判成
     *  单击。反过来，把 769 当一次普通按压、又按「双击窗口」等下去，就变成「双击绑的动作
     *  永远不执行、单击绑的动作被双击触发」。
     *
     *  先 [reset]：同一颗键上一次按压可能还挂着长按枪 / 双击窗（例如按住途中被 ROM 判成
     *  双击），不清的话那两枪会在这次派发之后再响一次，一次按压执行两个动作。
     */
    fun onRomGesture(
        id: KeyId,
        gesture: GestureTrigger,
        config: KeyConfig,
    ) = synchronized(lock) { onRomGestureLocked(id, gesture, config) }

    private fun onRomGestureLocked(
        id: KeyId,
        gesture: GestureTrigger,
        config: KeyConfig,
    ) {
        reset(id)
        // 判据级（与「双击」「长按」那几行同一档）：这条是「ROM 换了码 / 码的含义变了」时
        //  唯一能看出来的凭据 —— 只有它会把「按这个码等于这个手势」写进日志。
        XLog.w("${id.label}：${gesture.label}（ROM 已判好的手势码）")
        dispatch(id, gesture, config, repeat = false)
    }

    /** 一次按键按下。 */
    fun onKeyDown(
        id: KeyId,
        config: KeyConfig,
        now: Long = SystemClock.elapsedRealtime(),
    ) = synchronized(lock) { onKeyDownLocked(id, config, now) }

    private fun onKeyDownLocked(
        id: KeyId,
        config: KeyConfig,
        now: Long,
    ) {
        val p = pending(id)

        // 窗口内又来一次按下 → 连击链 +1；数到这颗键配的最高档就地派发（双击就是这一档的 n=2）
        if (p.sawFirstRelease && now <= p.doubleWindowAt) {
            val maxClicks = maxOf(2, config.clicksForJudge)
            p.clicks += 1
            if (p.clicks < maxClicks) {
                //  还没到最高档：继续等下一击。**不 reset、也不再排长按枪**（连击中不会变长按），
                //  抬手上会照常再排一次窗口（见 onKeyUp）。
                XLog.w("${id.label}：第 ${p.clicks} 击（这颗键配到第 $maxClicks 击）")
                p.sawFirstRelease = false
                p.doubleWindowAt = 0L
                return
            }
            // 「判出手势」这几行是验收判据链的第二环（见 AGENTS.md 二、1.2）——
            // 必须是 W：注入侧的 i/d 在 LSPosed 日志页里看不到。
            XLog.w(if (p.clicks == 2) "${id.label}：双击" else "${id.label}：${p.clicks} 击")
            val firedClicks = p.clicks
            reset(id)
            // reset 会把这个标记与击数一起清掉，所以在 reset 之后再置位：
            // 这一下的抬手属于连击链的最后一击，`onKeyUp` 要据此**派发**（而不是再开窗口）。
            p.doubleFired = true
            p.clicks = firedClicks
            //  **派发挪到抬手**（见 onKeyUp 的 doubleFired 那一支）。原来在这里当场派发，
            //  真机踩到两条：
            //   · ROM 的组合键窗口：电源键按着的时候注入音量键，`PhoneWindowManager` 会把它当
            //     「电源 + 音量」那一手（真机日志 `handleKeyGestureEvent: gestureType=56`
            //     紧跟 `mIsPowerKeyDown = true`）整条吞掉 —— 表现就是「自定义电源键双击绑定
            //     系统按键，一点反应都没有」；
            //   · 触发键自己还按着就注入，本身就容易和这对键的组合语义打架。
            //  代价只是把这一下推迟到抬手（几十毫秒），手感上看不出来。
            return
        }

        p.sawFirstRelease = false
        p.longPressFired = false
        p.doubleFired = false
        //  这一位描述的是**某一次按压**：新的一下按下来就作废（上一按的 UP 真丢了时，
        //  `reset` 没机会清它 —— 兜底那条路之后就没有抬手了）
        p.closedWithoutUp = false
        p.clicks = 1
        p.longPressAt = now + config.longPressMs
        p.doubleWindowAt = 0L
        // 记下这一下是什么时候按下的 —— onKeyUp 的「立即触发单击」靠它认领同一次按压的直判
        p.pressAt = now

        scheduleLongPress(id, config)
    }

    /** 一次按键抬起。 */
    fun onKeyUp(
        id: KeyId,
        config: KeyConfig,
        now: Long = SystemClock.elapsedRealtime(),
    ) = synchronized(lock) { onKeyUpLocked(id, config, now) }

    private fun onKeyUpLocked(
        id: KeyId,
        config: KeyConfig,
        now: Long,
    ) {
        val p = pending(id)
        //  **孤立抬手**（只看到 UP、没看到 DOWN）：`pressAt` 只在 `onKeyDown` 里写。
        //  常见来路是「模块在按下之后才装好」或者上一次按压的 DOWN 被别的接管点吞了。
        //  照旧判：用户确实按了这一下，宁可补一次单击，也别让他觉得「按了没反应」；
        //  但这一行必须留着 —— 否则日志里只有 UP、没有 DOWN，看起来像判定器漏了一条。
        //  双击的第二下抬手也满足 `pressAt==0`（判双击时 `reset` 已经清掉它）—— 那不是孤立抬手，
        //  它下面会被 `doubleFired` 那一支收尾，所以这一行只在**没判过双击**时打。
        if (p.pressAt == 0L && !p.doubleFired) {
            XLog.w("${id.label}：只收到抬手（没见到按下）⇒ 按孤立 UP 处理（补判一次单击）")
        }

        // 判据级：这一次按压按住了多久 —— 「长按没反应」最缺的就是这个数。
        //   按住 7ms 而用户自称按住不放 ⇒ 这颗键上报的是「按下即抬起」的一对事件，
        //    靠时长判长按先天不成立（长按枪在抬手那一刻就被摘了），只能靠 ROM 换码；
        //   按住 800ms 却没判出长按 ⇒ 问题在阈值配置或下面那三条栅栏，与这颗键无关。
        //   孤立抬手（`pressAt == 0`）不打：上面那行已经交代清楚了，再打一个负数只会误导。
        //  阈值一起打出来：用户按住 250ms、阈值是 500ms 时，两个数并排就能看出是「按得不够久」。
        if (p.pressAt != 0L) {
            XLog.w("${id.label}：按住 ${now - p.pressAt}ms（长按阈值 ${config.longPressMs}ms）")
        }

        // 抬手 ⇒ 长按那一枪作废。**摘枪**，不是只靠到点读一眼 `sawFirstRelease`：那次读跨线程，
        //  读到陈旧值就是「明明抬手了还判长按」。长按延迟压到 100ms 这一档时，抬手与到点只隔
        //  几毫秒，正是这种读最容易失手的位置。
        //
        //  **这一小段与长按那一枪互斥**（同一把 `p` 锁）：两边都是「读状态 → 派发」，
        //  抬手先拿到锁 ⇒ 枪到点时看到 `sawFirstRelease` 已置、作废；枪先拿到锁 ⇒
        //  `longPressFired` 已置，抬手只收尾、不再补一次单击。没有这把锁时两者能同时通过判定，
        //  一次按压会既执行长按动作、又执行单击动作。
        val longPressed =
            synchronized(p) {
                p.sawFirstRelease = true
                p.longPressFired
            }
        cancelLongPressShot(id)

        // 长按已在按下期间触发过 —— 抬手不再重复判单击（判据是 longPressFired，不是 longPressAt）
        if (longPressed) {
            // 长按这一档在抬手时还要做什么（松手才触发 / 松手再执行另一个动作 / 只清状态）
            finishLong(id, config)
            return
        }

        // 双击已经判过 —— 这次抬手是双击的第二下：**到这里才派发**（理由见 onKeyDown 里那段）。
        // 也不能再开一个单击窗口，否则 `doubleClickMs` 之后会补一次单击，双击变成「双击 + 单击」两下
        if (p.doubleFired) {
            val firedClicks = p.clicks
            p.doubleFired = false
            reset(id)
            dispatch(id, GestureTrigger.Click(firedClicks), config, repeat = false)
            return
        }

        // 「双击 = 无操作」且开了「立即触发单击」时**不等**双击窗口：等下去也没有第二下可用。
        //  **熄屏不再走这一支** —— 那会把双击整档废掉（熄屏下每一下都是一次单击）。
        //  熄屏改由窗口期顶住 CPU（见下面 `holdAwake` 那一段）。
        //  判据多一条 `clicksForJudge <= 1`：配了第 3 击起的键**不能**在这里抢跑，
        //  否则单击会立刻派发、后面的连击永远凑不齐。
        if (config.instantSingle &&
            config.clicksForJudge <= 1 &&
            !config.actionOf(GestureTrigger.Click(2)).isBound
        ) {
            // 这一下是不是已经被直判过了（同一次按压被转发两遍）：
            // 记到了按下时刻 ⇒ 判「按下那一刻前后 DIRECT_BURST_MS 之内有没有直判」，用本次按下当锚点，
            //   才不会把上一次按压的直判算到这一下头上；
            // 没记到按下时刻（只收到抬手的怪形态）⇒ 退回「刚刚这几毫秒内有没有直判」。
            val alreadyDispatched =
                if (p.pressAt != 0L) {
                    p.lastDirectSingleAt >= p.pressAt - DIRECT_BURST_MS
                } else {
                    now - p.lastDirectSingleAt < DIRECT_BURST_MS
                }
            // reset 会取消那个还挂着的长按计时器 —— 不取消的话它到点还会再判一次长按
            reset(id)
            if (alreadyDispatched) {
                // 这一档与紧跟着那句 `单击` 是同一笔判据的两面（判 / 不判），级别必须一致：
                //  注入侧 i/d 在 LSPosed 日志页整档看不到、release 里还被消除，写成 d 就等于
                //  「按下去没反应」时日志里一片空白，分不清是没判还是判了没执行。
                XLog.w("${id.label}：这次按压已直判过单击，不再补判（双击为无操作）")
                return
            }
            XLog.w("${id.label}：单击（双击为无操作 ⇒ 不等双击窗口）")
            // 与上面那条直判去重同一笔账：这一下已经派发过单击了，
            // 紧接着那次 DOWN/UP（同一次按压被转发两遍时）与长按计时器都要据此让路。
            p.lastDirectSingleAt = now
            dispatch(id, GestureTrigger.Click(1), config, repeat = false)
            return
        }

        // `sawFirstRelease` 在上面那段临界区里已经置上了（与长按枪互斥的那一笔）
        // 窗口长度在这一刻定格（见 Pending.doubleWindowMs）：排窗用的窗口与到点判定用的窗口
        // 必须是同一个数，否则中途改「单击延迟」会让两者悄悄变成两个数。
        val window = config.doubleClickMs.toLong()
        p.doubleWindowMs = window
        p.doubleWindowAt = now + window
        //  熄屏时窗口这一枪要在 uptime 上真走到点 —— 深睡里 `postDelayed` 不走，干等就是
        //  「按了没反应」。整段窗口期让 CPU 醒着：熄屏也能判双击，代价是熄屏单击要晚一个
        //  窗口才执行（用户定的取舍：双击不能没有）。亮着时这一下什么都不做。
        //  这一下在输入线程上跑：顶不住最多是「熄屏判不出双击」，绝不能把异常抛回去
        try {
            holdAwake(window + AWAKE_MARGIN_MS)
        } catch (e: Throwable) {
            XLog.w("${id.label}：没顶住 CPU，熄屏这一按可能判不出双击：${e.javaClass.simpleName}", e)
        }
        scheduleDoubleWindow(id, config)
    }

    /**
     * 清空某个键的状态与计时器（含「长按连续触发」正在跑的重复任务）。
     *
     * @param id `null` = **所有键**。配置里任意一颗键从「接管」翻成「不接管」时用
     *   （见 `SideKeyInterceptor.cancelPending`）：只清一颗的话，关掉某一颗自定义键时
     *   它按住不放的那一下会一路派发到连发上限。没有待定手势时是空操作。
     */
    fun reset(id: KeyId?) = synchronized(lock) { resetLocked(id) }

    private fun resetLocked(id: KeyId?) {
        if (id == null) {
            for (k in pendingByKey.keys.toList()) reset(k)
            return
        }
        val p = pending(id)
        cancelLongPressShot(id)
        cancelReleaseFallback(id)
        p.repeatRunnable?.let { handler.removeCallbacks(it) }
        p.repeatRunnable = null
        // 双击窗口那一枪也要摘：它与长按枪一样是「到点才读状态」，留着会在下一次按压期间醒来
        p.doubleWindowRunnable?.let { handler.removeCallbacks(it) }
        p.doubleWindowRunnable = null
        p.longPressAt = 0L
        p.doubleWindowAt = 0L
        p.doubleWindowMs = 0L
        p.sawFirstRelease = false
        p.longPressFired = false
        p.doubleFired = false
        p.closedWithoutUp = false
        p.clicks = 0
        p.pressAt = 0L
    }

    private fun scheduleLongPress(
        id: KeyId,
        config: KeyConfig,
    ) {
        // 这一枪属于哪一次按下：`pressAt` 就是那一次按下的时刻（`onKeyDown` 写、`reset` 清）。
        // 到点时它变了（中途又按过一次）或清零了（抬手 / 重置过）⇒ 这一枪作废；
        // 否则第 1 次排下的枪会在第 N 次按住期间醒来，凭空判出一次长按。
        val downAt = pending(id).pressAt
        val shot =
            Runnable {
                try {
                    val p = pending(id)
                    p.longPressRunnable = null
                    if (p.pressAt != downAt) {
                        XLog.w("${id.label}：这一枪属于上一次按下（已作废），不判长按")
                        return@Runnable
                    }
                    // 到点了还没抬手 → 长按。
                    // 若转发顺序是先 SINGLE 后 DOWN，那个 DOWN 会把 `longPressAt` 重新排上，
                    // 一个 `longPressMs` 之后照样判长按 ⇒ 又是两个动作。
                    val directJustFired =
                        SystemClock.elapsedRealtime() - p.lastDirectSingleAt < config.longPressMs
                    // 状态位在锁里读改（与抬手那一段互斥），**派发留在锁外**：
                    //  dispatch 会发广播，不该占着这把锁。
                    //
                    // 三条栅栏哪一条没过：到点却没判时，这是唯一能把「按住够久但被作废」与
                    //  「压根没排上枪」分开的证据 —— 全静默的话，两种故障在日志里长得一模一样
                    //  （只有一条 DOWN/UP，没有「长按」那行）。攒成一句在**锁外**打：日志出口要走
                    //  logd，与 dispatch 同理，不该占着这把状态锁。
                    var missReason: String? = null
                    val fire =
                        synchronized(p) {
                            if (p.longPressAt != 0L && !p.sawFirstRelease && !directJustFired) {
                                p.longPressAt = 0L
                                p.longPressFired = true
                                true
                            } else {
                                missReason =
                                    "${id.label}：长按到点未判" +
                                    "（长按位在=${p.longPressAt != 0L}" +
                                    " 已抬手=${p.sawFirstRelease}" +
                                    " 刚直判过单击=$directJustFired）"
                                false
                            }
                        }
                    missReason?.let { XLog.w(it) }
                    if (fire) {
                        // 「松手才触发」（`longFireOnRelease`）：到点只把这一按标成长按，**不派发** ——
                        //  执行在抬手那一支（`onKeyUp`）。状态位照旧置上，否则抬手会把它当短按再补一次单击。
                        //  同时排上兜底那一枪：抬手真丢了的时候，这一档不能什么都不做。
                        if (config.longFireOnRelease) {
                            XLog.w("${id.label}：长按到点（松手才触发 ⇒ 等抬手再执行）")
                            scheduleReleaseFallback(id, config, downAt)
                        } else {
                            XLog.w("${id.label}：长按")
                            dispatch(id, GestureTrigger.LongPress, config, repeat = false)
                            // 「长按连续触发」：按住不放就一直重复（松手由 reset 停掉）。
                            //  不许连发的理由（松手才触发 / 多条命令 / 灵感语音）收在
                            //  `KeyConfig.longRepeats` 一处 —— 这里只问「该不该排」。
                            //  「松手执行额外动作」不在此列：它是抬手那一刻的另一件事，与按住期间
                            //  重复多少次互不干扰。
                            if (config.longRepeats) scheduleRepeat(id, config)
                        }
                    }
                } catch (e: Throwable) {
                    // 计时器跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("长按判定失败（忽略）：${id.label}", e)
                }
            }
        pending(id).longPressRunnable = shot
        handler.postDelayed(shot, config.longPressMs.toLong())
    }

    /**
     * 摘掉还挂着的那一枪长按 —— [onKeyUp] 与 [reset] 都走它。
     *
     * `removeCallbacks` 可以从输入线程安全地调（Handler 自己带锁），而且是**确定**的：
     * 只要这一枪还没被 looper 取走，它就再也不会响。
     */
    private fun cancelLongPressShot(id: KeyId) {
        val p = pending(id)
        p.longPressRunnable?.let { handler.removeCallbacks(it) }
        p.longPressRunnable = null
    }

    /** 「长按连续触发」（`plus_long_repeat`）的重复派发。 */
    private fun scheduleRepeat(
        id: KeyId,
        config: KeyConfig,
    ) {
        val interval =
            config.longRepeatIntervalMs
                .coerceAtLeast(KeyConfig.MIN_REPEAT_INTERVAL_MS)
                .toLong()
        val runnable =
            Runnable {
                try {
                    val p = pending(id)
                    // 松手 / 重置过了 → 停
                    if (!p.longPressFired || p.sawFirstRelease) {
                        p.repeatRunnable = null
                        return@Runnable
                    }
                    // 外部终止：UP 丢失（锁屏打断 / 非FLAG_CANCELED 的丢弃）时没有任何回调会停这条链，
                    // 每 interval 自循环派发 —— 距按下超过 30s 视为抬手事件已丢，强制停止。
                    if (p.pressAt != 0L && SystemClock.elapsedRealtime() - p.pressAt > REPEAT_MAX_MS) {
                        XLog.w("${id.label}：连续触发超过 ${REPEAT_MAX_MS}ms 未见抬手，强制停止（UP 可能已丢）")
                        p.repeatRunnable = null
                        // 等价于「替系统补一次抬手」：连发停掉、这一按的状态清干净。
                        //  （松手不再替任何动作收尾 —— 灵感语音拆成开始 / 结束两个显式动作之后，
                        //   抬手只影响判定侧自己的状态。）
                        reset(id)
                        return@Runnable
                    }
                    dispatch(id, GestureTrigger.LongPress, config, repeat = true)
                    scheduleRepeat(id, config)
                } catch (e: Throwable) {
                    XLog.w("长按连续触发派发失败（已停止）：${id.label}", e)
                }
            }
        pending(id).repeatRunnable = runnable
        handler.postDelayed(runnable, interval)
    }

    /**
     * 「松手才触发」的**兜底那一枪** —— 到点之后再等 [RELEASE_FALLBACK_MS]，按着不放就当 UP 丢了。
     *
     *  到点时这一档什么都没执行（执行押在抬手），所以「UP 丢了」的代价是**整次长按零响应**，
     *  用户看到的就是「这颗键坏了」。兜底把这个失败面补上：就地执行一次，再把这一次按压
     *  **封掉**（`Pending.closedWithoutUp`），于是真迟到的那个 UP 走 [finishLong] 时只清状态。
     *
     * ## 为什么整段判据都在 `synchronized(p)` 里
     *
     *  它与抬手那一侧抢同一件事：「到底谁负责这一次执行」。抬手侧在 `synchronized(p)` 里置
     *  `sawFirstRelease`，这里在同一把锁里读它并置 `closedWithoutUp` —— 先到的那个赢：
     *  · 抬手先到 ⇒ 这里看到 `sawFirstRelease`，一声不响地作废；
     *  · 兜底先到 ⇒ 抬手侧的 [finishLong] 看到 `closedWithoutUp`，只清理不派发。
     *
     *  派发留在锁外（会发广播，不该占着状态锁）。
     *
     * @param downAt 这一枪属于哪一次按下（与长按枪同一条身份判据）
     */
    private fun scheduleReleaseFallback(
        id: KeyId,
        config: KeyConfig,
        downAt: Long,
    ) {
        cancelReleaseFallback(id)
        val shot =
            Runnable {
                try {
                    val p = pending(id)
                    p.releaseFallbackRunnable = null
                    val act =
                        synchronized(p) {
                            //  抬手已到 / 又按了一次 / 这一按已经被别的路径收尾 ⇒ 兜底不作数
                            if (p.pressAt != downAt || p.sawFirstRelease || !p.longPressFired) {
                                false
                            } else {
                                p.closedWithoutUp = true
                                true
                            }
                        }
                    if (!act) {
                        XLog.w("${id.label}：松手兜底作废（抬手已到或这一按已收尾）")
                        return@Runnable
                    }
                    XLog.w("${id.label}：长按（松手才触发，但 ${RELEASE_FALLBACK_MS}ms 未见抬手 ⇒ 兜底执行一次）")
                    dispatch(id, GestureTrigger.LongPress, config, repeat = false)
                } catch (e: Throwable) {
                    // 计时器跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("松手兜底失败（忽略）：${id.label}", e)
                }
            }
        pending(id).releaseFallbackRunnable = shot
        handler.postDelayed(shot, RELEASE_FALLBACK_MS)
    }

    /** 摘掉还挂着的那一枪兜底（抬手 / 重置都走它）。 */
    private fun cancelReleaseFallback(id: KeyId) {
        val p = pending(id)
        p.releaseFallbackRunnable?.let { handler.removeCallbacks(it) }
        p.releaseFallbackRunnable = null
    }

    /** 抬手后开一个双击窗口：窗口内没有第二次按下就补一次单击。 */
    private fun scheduleDoubleWindow(
        id: KeyId,
        config: KeyConfig,
    ) {
        // 排窗用的窗口长度在排窗这一刻定格，不再到点重读 `doubleClickMs`
        // （那一读会读到用户中途改过的新值，与 `p.doubleWindowAt` 的口径对不上）。
        //
        // 这一枪属于哪一次按下：与长按枪同一条身份判据（见 [scheduleLongPress] 的 `downAt`）——
        //  `pressAt` 在 `onKeyDown` 写、`reset` 清。不判的话，迟到的旧窗口会在**后来那次**按压
        //  期间醒来，读到它的 `sawFirstRelease` / `doubleWindowAt` 而抢先补一次单击并清掉标志，
        //  真正该补的那次单击（本次按压排的枪）反而落空。
        val p0 = pending(id)
        val pressAt = p0.pressAt
        val window = p0.doubleWindowMs
        p0.doubleWindowRunnable?.let { handler.removeCallbacks(it) }
        val shot =
            Runnable {
                try {
                    val p = pending(id)
                    p.doubleWindowRunnable = null
                    if (p.pressAt != pressAt) {
                        XLog.w("${id.label}：双击窗口属于上一次按压（已作废），不补单击")
                        return@Runnable
                    }
                    // 窗口到了、期间没有第二次按下 → 单击
                    if (p.sawFirstRelease && p.doubleWindowAt != 0L) {
                        // 这一次按压到此为止 —— 把长按那一枪**作废**（清 `longPressAt` 与 `pressAt`）。
                        //  不作废的后果：按住时长 < `longPressMs − doubleClickMs`
                        // （当前默认 400 − 300 = 100ms）时，长按枪排在**本回调之后**到点，而本回调
                        //  只清 `sawFirstRelease` 的话，长按枪的三条栅栏（`longPressAt != 0` /
                        //  `!sawFirstRelease` / `!directJustFired`）**全部通过** ⇒ 一次短按既判单击、
                        //  又判长按。默认配置走 `onKeyUp` 的「立即判单击」分支（那条会 `reset`），
                        //  所以从未暴露；只有「双击绑了动作」或关了 `instantSingle` 时才走这条窗口路径。
                        //  下面两条分支都要作废，所以在进分支时先清。
                        p.longPressAt = 0L
                        p.pressAt = 0L
                        // 这次按压已经当场判过单击（同一次按压被转发两遍）
                        // ⇒ 不再补一次单击，否则一次按压会执行两个单击动作。
                        // 这里的窗口同样用定格值 —— 与排窗一致。
                        if (SystemClock.elapsedRealtime() - p.lastDirectSingleAt < p.doubleWindowMs) {
                            XLog.w("${id.label}：这次按压已直判过单击，跳过补判")
                            p.sawFirstRelease = false
                            p.doubleWindowAt = 0L
                        } else {
                            val firedClicks = p.clicks.coerceAtLeast(1)
                            XLog.w(if (firedClicks == 1) "${id.label}：单击" else "${id.label}：$firedClicks 击")
                            p.sawFirstRelease = false
                            p.doubleWindowAt = 0L
                            dispatch(id, GestureTrigger.Click(firedClicks), config, repeat = false)
                        }
                    }
                } catch (e: Throwable) {
                    // 计时器跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("双击窗口判定失败（忽略）：${id.label}", e)
                }
            }
        p0.doubleWindowRunnable = shot
        handler.postDelayed(shot, window)
    }

    /**
     * 抬手时收尾一次长按 —— 三种时序在这里分开（互斥关系见 `KeyConfig.longFireOnRelease`）。
     *
     *  · 已经**兜底执行过**（UP 丢过）：这一下只清理 —— 不清理会让长按状态漏到下一次按压，
     *    再执行一次就是「一次按压两个动作」；
     *  · 「松手才触发」：到点只标了状态，长按动作放到这一下执行**一次**；
     *  · 「松手执行额外动作」（`releaseBound`）：长按早在按下期间执行过（可能还连发过几轮），
     *    这一下再补一条 `release`；
     *  · 其余：长按也已执行过，这一下只剩清状态。
     */
    private fun finishLong(
        id: KeyId,
        config: KeyConfig,
    ) {
        val p = pending(id)
        //  与兜底那一枪同一把锁：谁先到谁负责，另一侧看得到这个结论（见 [scheduleReleaseFallback]）
        if (synchronized(p) { p.closedWithoutUp }) {
            XLog.w("${id.label}：抬手迟到（这一按已由松手兜底执行过，只清状态）")
            reset(id)
            return
        }
        if (config.longFireOnRelease) {
            XLog.w("${id.label}：长按（松手才触发）")
            reset(id)
            dispatch(id, GestureTrigger.LongPress, config, repeat = false)
            return
        }
        if (config.releaseBound) {
            XLog.w("${id.label}：长按松手（${config.longRelease.token}）")
            dispatch(id, GestureTrigger.Release, config, repeat = false)
        }
        reset(id)
    }

    /**
     * 执行一个手势对应的动作。
     *
     * `repeat` 只影响「长按连续触发」的重复派发，由 `onGesture` 一路带到执行侧。
     */
    private fun dispatch(
        id: KeyId,
        gesture: GestureTrigger,
        config: KeyConfig,
        repeat: Boolean,
    ) {
        // 链路自检的**端到端实证**：真判出一个手势就记一笔。
        //  接管点装上只说明反射找对了类与方法，说明不了这版 ROM 的侧键事件真的走到这里 ——
        //  只有这一笔能证明整条链通了（页面见 `ui/LinkCheck.kt`）。
        onGestureNoted()
        // 触觉反馈**不在这里**：它已经挪回宿主侧、动作即将执行那一刻
        // （`host/GestureReceiver.runAction`）。理由见那边的 KDoc —— 一句话：
        // 判定侧振不出「服务在跑」这个事实（它判出手势就振，可宿主在不在它不知道），
        // 而用户要的是「不振就别让人以为功能能用」。
        onGesture(id, gesture, config, repeat)
    }
}
