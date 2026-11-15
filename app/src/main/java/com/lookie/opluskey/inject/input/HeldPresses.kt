package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import android.view.MotionEvent
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import java.util.concurrent.ConcurrentHashMap

/**
 * **参与组合的键按下之后「先等一个窗口」**那笔账（见 `SideKeyInterceptor` 里那张卡片的等待时间）。
 *
 * ## 为什么要压着
 *
 *  组合键的窗口既是「两颗键算不算同时按」的判据，也是「第一颗键要不要等第二颗」的等待：不等的话，
 *  `ComboDetector.Down.HOLD` 这条状态就没有意义 —— 单键已经先跑掉了，第二颗键再来也凑不成组合。
 *
 * ## 为什么松手了还要等满窗口
 *
 *  组合的配对判据是「两颗键的 DOWN 相差不超过窗口」，**不要求两颗同时按着**（见 `ComboDetector`
 *  类注释）。所以手指松开那一刻还看不出结果：窗口内第二颗键仍可能按下来。
 *  早先一抬手就把这一按补进单键判定器，结果就是这个 —— 先按 A、松手、紧接着按 B：
 *  A 的单击已经跑掉（还会再等双击窗口才派发），B 到时组合又成立，用户看到「各自的单击 + 组合」
 *  三下全来了。
 *
 *  现在抬手只记一笔，到点那一枪按「先 DOWN 后 UP」把整按补进去（时长仍是真实按下的时长）；
 *  这期间组合要是成立了，`drop` 会把这一枪摘掉 —— 于是只有组合，没有单击。
 *
 * ## 按住到长按阈值会提前释放（`flushOthers` / 长按枪）
 *
 *  压窗口等第二颗的同时，这一键若**按住不放超过它自己的长按阈值**，就不该再傻等窗口到点 ——
 *  用户已经明确在长按了。长按枪（见 [hold]）到点把它提前补进单键判定器（判长按），并顺手把
 *  其它还压着的键也补发（补它们的单击）。场景：A 按下松手进入等待，B 按下并按住 ——
 *  B 走长按，A 的单击跟着补发，而不是都拖到窗口结束。
 *
 * ## 为什么要把按下时刻一起带过去
 *
 *  补发晚了一个窗口。判定器如果拿**补发这一刻**当按下时刻，`pressAt` / `longPressAt` 就整体
 *  后移一个窗口（窗口 80ms ⇒ 长按 400ms 变成 480ms；窗口 1000ms ⇒ 1400ms），长按阈值跟着漂。
 *  所以 [Press] 记下真实按下时刻，补发时原样交给判定器 —— 等的是窗口，判的还是那一下。
 *
 * ## 为什么单独一个类
 *
 *  它只是一个「到点补发」的小簿记，与拦截器那套「认领 / 派发」没有关系；挤在拦截器里会把那个类
 *  顶到 detekt 的 `TooManyFunctions`（它本来就贴着线）。
 *
 * ## 线程
 *
 *  写在输入线程（DOWN / UP），读在 main looper 的定时任务里 —— 与 `ComboDetector.down` 同一条
 *  理由：volatile 只管引用，集合必须用并发容器。
 *
 * @param feed 到点把压着的按下补交给单键判定器（`GestureDetector::onKeyDown`）；
 *   第三个参数是**原始按下时刻**，判定器据此算长按截止，而不是按补发时刻算
 * @param feedUp 松手早于窗口时，到点连着把抬起一起补交（`GestureDetector::onKeyUp`）
 */
internal class HeldPresses(
    private val handler: Handler,
    private val feed: (KeyId, KeyConfig, Long) -> Unit,
    private val feedUp: (KeyId, KeyConfig) -> Unit,
) {
    /** 压着的那几颗键 —— key → 那一枪 + 按下那一刻的判定参数。 */
    private val held = ConcurrentHashMap<KeyId, Press>()

    /**
     * 压住 [id] 这一下，[windowMs] 之后再补给单键判定器。
     *
     *  同一颗键又按下来时，压着的那一下**先补进去再换新的**：新的 DOWN 就是「上一次按压已经结束」
     *  的证据。直接顶掉的话，快速双击的第一下会整个消失（第二下到来时第一下还在窗口里没补发）。
     */
    fun hold(
        id: KeyId,
        keyConfig: KeyConfig,
        windowMs: Long,
    ) {
        held.remove(id)?.let { prev ->
            handler.removeCallbacks(prev.shot)
            deliver(id, prev)
        }
        //  按下这一刻就记下来（本方法由 DOWN 那条路同步调进来）：补发要晚一个窗口，
        //  这一份时刻是「这一按到底什么时候按的」的唯一凭据（见类注释）。
        val downAt = SystemClock.elapsedRealtime()
        lateinit var press: Press
        press =
            Press(
                keyConfig,
                downAt,
                Runnable {
                    //  与 [release] 抢同一把锁：摘 `held` 与「读 released」必须是一段临界区。
                    //  否则松手恰好落在两者中间时，调用方吞掉了那条 UP，而这里已经读过
                    //  `released = false` 不补 UP —— 单键判定器认为键一直按着，一次单击被判成长按。
                    synchronized(press) {
                        //  被后一次按压顶掉 / 已被组合收走 / 已被 [drop] 撤销 ⇒ 这一枪作废
                        if (press.revoked || !held.remove(id, press)) return@synchronized
                        press.dispatching = true
                        deliver(id, press)
                    }
                },
            )
        held[id] = press
        handler.postDelayed(press.shot, windowMs)
        //  长按检测枪：按住超过长按阈值就提前补发（不再傻等窗口到点，长按才判得出来）。
        //  只在长按阈值**短于**窗口时排 —— 阈值比窗口长的话，窗口枪先到点、这一按已经补发了，
        //  长按枪没有意义（它到点时这一按早不在 `held` 里）。
        val longMs = keyConfig.longPressMs.toLong()
        if (longMs < windowMs) {
            lateinit var longShot: Runnable
            longShot =
                Runnable {
                    var fired = false
                    synchronized(press) {
                        //  已经被窗口枪补发 / 被收走 / 抬手了 ⇒ 不提前补
                        if (press.revoked || press.released || press.dispatching || !held.containsKey(id)) {
                            return@synchronized
                        }
                        //  提前补发这一按：让它走单键长按判定（DOWN 先补进去，长按枪在判定器里接着跑）
                        held.remove(id, press)
                        press.dispatching = true
                        deliver(id, press)
                        fired = true
                    }
                    //  补发其它还压着的键放到锁外：`flushOthers` 会拿别的键的锁，锁内再嵌锁没必要
                    //  （且补发会进单键判定器，那条链不该压在这把锁下）。
                    if (fired) flushOthers(id)
                }
            press.longShot = longShot
            handler.postDelayed(longShot, longMs)
        }
        //  d 级：调大窗口之后这一行会多起来，属于「正常但值得看一眼」的那一档
        XLog.d { "${id.label}：参与组合，先等 ${windowMs}ms 再判单键" }
    }

    /**
     * 抬手只是**记一笔**「松了」，不补发 —— 组合的等待压过单键的单击（见类注释）。
     *
     *  到点那一枪会按原顺序补：先 DOWN 再 UP（单键判定器靠这一对判单击 / 双击）。
     *
     * @return 这一下是不是还压着（是的话调用方吞掉这条 UP，别再走单键那条路）
     */
    fun release(id: KeyId): Boolean {
        val press = held[id] ?: return false
        synchronized(press) {
            //  补发已经开始了（它读到的是 `released = false`）：这一下交回给调用方按普通 UP 处理。
            //  这里若返回 true，那条 UP 就被吞掉，而补发那一侧不会替它补 —— 见那个 Runnable 的注释。
            if (press.dispatching) return false
            press.released = true
            //  抬手了，长按检测枪作废：这一按不会再到长按阈值（手指已经松了）
            press.longShot?.let { handler.removeCallbacks(it) }
            press.longShot = null
            return true
        }
    }

    /**
     * 把**除 [except] 之外**所有还压着的键立即补发（补它们的单击）—— 等窗口被中途打断时用。
     *
     *  与 [drop] 相反：drop 是「作废不补」，这里是「补了再清」。场景见 [onEarlyLongPress]：
     *  某颗成员键长按到点，别的键不该再傻等窗口到点，它们的单击现在就该补出来。
     */
    fun flushOthers(except: KeyId) {
        held.keys.toList().forEach { id ->
            if (id == except) return@forEach
            val press = held[id] ?: return@forEach
            synchronized(press) {
                if (press.revoked || press.dispatching) return@synchronized
                press.dispatching = true
                held.remove(id, press)
                handler.removeCallbacks(press.shot)
                press.longShot?.let { handler.removeCallbacks(it) }
                deliver(id, press)
            }
        }
    }

    /** 丢掉压着的那一颗（`null` = 全部丢掉）—— 组合接管了它，或配置变了不该再补旧的那一下。 */
    fun drop(id: KeyId?) {
        if (id == null) {
            held.keys.toList().forEach { drop(it) }
            return
        }
        val press = held[id] ?: return
        //  与到点补发那一枪**同一把锁**：两边都在摘 `held`，不在锁里会和「已经起跑的那一枪」
        //  交错 —— 同一按既被组合收走、又被补进单键判定器。
        synchronized(press) {
            //  **先记「已撤销」再摘表**：那一枪可能已经被 looper 取走、正等这把锁 —— 只摘 `held`
            //  的话它靠「表里没有这一份了」也会退出，但那条判据只在本类的写法不变时成立；
            //  `revoked` 是补发那一侧亲口看的凭据，两处判据一起用。
            press.revoked = true
            //  `dispatching` = 补发已经开跑（DOWN 已经交出去了，见 [hold] 的 Runnable）：
            //  这一下停不掉 —— 半路掐断会留下「只补了 DOWN、没补 UP」的单键状态，代价比多一下单击更大。
            //  按实例移除：`held` 里可能已经换成了后一次按压的那一份。
            if (!press.dispatching && held.remove(id, press)) {
                handler.removeCallbacks(press.shot)
                //  长按检测枪一并摘掉：这一按都不算数了，别让它到点再补一次
                press.longShot?.let { handler.removeCallbacks(it) }
            }
        }
    }

    /** 把压着的一按按原顺序补交给单键判定器（先 DOWN；松过手再补 UP）。 */
    private fun deliver(
        id: KeyId,
        press: Press,
    ) {
        try {
            //  带上原始按下时刻：判定器的长按截止 = 按下时刻 + `longPressMs`，不是补发时刻 +
            //  `longPressMs`，否则参与组合的键长按阈值会整体后移一个窗口。
            feed(id, press.keyConfig, press.downAt)
            //  松手早于窗口：这一按在窗口里没等到第二颗键，按「一次完整按压」补进去
            if (press.released) feedUp(id, press.keyConfig)
        } catch (e: Throwable) {
            XLog.w("补发单键失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }

    /** 压着等窗口的一次按压。 */
    private class Press(
        val keyConfig: KeyConfig,
        /** 手指按下那一刻（`elapsedRealtime`）—— 补发晚一个窗口，判定仍要用它当按下时刻。 */
        val downAt: Long,
        val shot: Runnable,
    ) {
        /** 手指已经松了（抬手早于窗口）—— 到点补发时要连着 UP 一起补。 */
        @Volatile
        var released: Boolean = false

        /**
         * 这一按**已经不算数**了（[drop]：组合接管了它 / 配置变了不该再补旧的那一下）。
         *
         *  锁内读写，与 [release] / [drop] / 补发那一枪用同一把锁 —— `held` 里查不到这一份
         *  只是「表里没有了」，这个标志表达的是「补发侧看见了、主动放弃」。
         */
        var revoked: Boolean = false

        /** 补发已经开始（锁内读写，见 [hold] 那一枪与 [release]）。 */
        var dispatching: Boolean = false

        /**
         * 压窗口期间排的**长按检测枪** —— 到点仍按着就提前补发（见 [onEarlyLongPress]）。
         *
         *  抬手 / 被组合收走 / 补发开始时摘掉。
         */
        var longShot: Runnable? = null
    }
}

/**
 * 判定层吃的**唯一一种输入** —— 按键与触摸都先归一到这上面。
 *
 *  为什么先归一：判定器（相位 / 组合 / 滑动）本可以各自直接吃 `KeyEvent` 或 `MotionEvent`，
 *  但那样「这事件算不算我管的、时间戳怎么取」就得在**每个采集点**各写一份，而采集点只会越来越
 *  多（策略回调、输入事件、将来的触摸源），判定器却只有一份。归一之后采集点的职责只剩
 *  「原样转成 [RawInput]」，判定的全部前提（时间基准、手指数、是不是主指针）都在这一层说清。
 *
 *  时间基准（[atMs]）由**采集点**给，判定只比较同一条流内的差值：按键那一路用的是
 *  `SystemClock.elapsedRealtime()`（含深睡，见 `GestureDetector` 里那段真机记录：熄屏深睡时
 *  `uptimeMillis()` 不走，跨睡眠的双击窗口会判错）；触摸那一路用事件自己的 `eventTime`
 *  （同一段轨迹里的时长才是准的，合成事件的自检也靠它）。
 *
 *  本阶段只**铺路**：触摸事件暂时没有采集点（系统进程里那个入口还没在真机上确认），
 *  判定结果也不派发任何动作。接入时按「一个采集点 = 一个把原始事件转成 [RawInput] 的适配器」加。
 */
internal sealed interface RawInput {
    /** 事件发生的时刻 —— 基准见类注释，判定只用同一条流内的差值。 */
    val atMs: Long

    /** 一颗键按下。 */
    data class KeyDown(
        val keyCode: Int,
        override val atMs: Long,
    ) : RawInput

    /** 一颗键抬起。 */
    data class KeyUp(
        val keyCode: Int,
        override val atMs: Long,
    ) : RawInput

    /**
     * 一根手指按下。
     *
     * [pointers] 是**这一刻**屏上的手指数。滑动只认单指，多指由判定器作废（见 [SwipeJudge]），
     * 所以采集点要**原样带上手指数**，不能自己先筛掉 —— 筛在采集点就等于「谁来定义什么算单指」
     * 又散回各处。
     */
    data class TouchDown(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val pointers: Int,
        override val atMs: Long,
    ) : RawInput

    /** 一根手指移动（高频：真机 60~120 条/秒）。 */
    data class TouchMove(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val pointers: Int,
        override val atMs: Long,
    ) : RawInput

    /** 一根手指抬起 —— 这一段的结论在它这里算。 */
    data class TouchUp(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val pointers: Int,
        override val atMs: Long,
    ) : RawInput

    /** 这一段被系统收走了（来电、手势导航抢走、窗口切换）—— 判定器要立刻丢掉手上的轨迹。 */
    data class TouchCancel(
        override val atMs: Long,
    ) : RawInput
}

/**
 * 把一条 `MotionEvent` 转成 [RawInput] —— 触摸那一路的**唯一**适配点。
 *
 *  职责只有「翻译」：不改坐标、不做节流、不判断是不是该管的那根手指。多指、指针对不上、
 *  超时这些判据统统在 [SwipeJudge] 里，两边各写一半的话，换判定器就得回来改适配器。
 *
 *  用到的全是公开 API（`MotionEvent` 是框架公开类型，不是宿主的私有类），所以注入侧可以直接编译。
 *
 * ## 时间戳取 `event.eventTime`
 *
 *  不是 `elapsedRealtime()`：滑动判定要的是**这一条轨迹里**的时长，事件自带的时间戳最准；
 *  拿起来那一刻的时间做差，在高频 MOVE 上虽然也近似，但自检（合成事件，见 `probe` 的 `touch`）
 *  就跑不出时长来。跨睡眠的窗口判定不在这里 —— 那是按键那一路的事（`GestureDetector`）。
 *
 * ## 接入时必须注意的两件事
 *
 *  1. **MOVE 是高频**：真机 60~120 条/秒。适配本身极轻（一次装箱），但**不要在适配器里打日志**，
 *     一次滑动就能把 W 级日志刷满。要判据就打在判定结论那一行（一条滑动一条）。
 *  2. **回调线程**：这个函数必须在**采集点自己的线程**上被调，且 [SwipeJudge] 也只在同一条线程上
 *     被喂（见它的「线程」那一节）。别在这里 `post` 到主线程 —— 那样时间戳还在，顺序却会乱。
 */
internal object TouchAdapter {
    /**
     * 转一条；认不出的动作（`ACTION_HOVER_*` 之类）返回 `null`。
     *
     *  注入侧纪律：任何一环抛异常都只能记一行日志（`d` 级），绝不许顺着采集点的回调抛回去。
     */
    fun toRaw(event: MotionEvent): RawInput? =
        try {
            val at = event.eventTime
            val pointers = event.pointerCount
            when (event.actionMasked) {
                //  第一根手指按下：主指针就是 index 0
                MotionEvent.ACTION_DOWN -> {
                    RawInput.TouchDown(event.getPointerId(0), event.getX(0), event.getY(0), pointers, at)
                }

                //  又一根手指按下：把**新按下那根**报上去，手指数 > 1 让判定器自己作废这一段
                MotionEvent.ACTION_POINTER_DOWN -> {
                    RawInput.TouchDown(
                        event.getPointerId(event.actionIndex),
                        event.getX(event.actionIndex),
                        event.getY(event.actionIndex),
                        pointers,
                        at,
                    )
                }

                //  移动只取 index 0：单指时它就是唯一那根；多指时这一段本来就作废，取谁都不影响结论
                MotionEvent.ACTION_MOVE -> {
                    RawInput.TouchMove(event.getPointerId(0), event.getX(0), event.getY(0), pointers, at)
                }

                MotionEvent.ACTION_UP -> {
                    RawInput.TouchUp(event.getPointerId(0), event.getX(0), event.getY(0), pointers, at)
                }

                //  抬起的是**非主指针**：同样报成 TouchUp，靠 pointerId 对不上让判定器作废
                MotionEvent.ACTION_POINTER_UP -> {
                    RawInput.TouchUp(
                        event.getPointerId(event.actionIndex),
                        event.getX(event.actionIndex),
                        event.getY(event.actionIndex),
                        pointers,
                        at,
                    )
                }

                //  这一段被系统收走（来电 / 手势导航抢走 / 窗口切换）：**必须**报上去，
                //  否则判定器会一直挂着一段永远等不到抬手的轨迹。
                MotionEvent.ACTION_CANCEL -> {
                    RawInput.TouchCancel(at)
                }

                else -> {
                    null
                }
            }
        } catch (e: Throwable) {
            XLog.d { "触摸适配：这条 MotionEvent 转不了（${e.javaClass.simpleName}: ${e.message}）" }
            null
        }
}
