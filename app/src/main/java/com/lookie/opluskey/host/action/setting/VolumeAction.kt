package com.lookie.opluskey.host.action.setting

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.Action
import com.lookie.opluskey.core.model.KeyId
import com.lookie.opluskey.host.action.CommandFailure
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 音量那一族（`volume:50` 媒体 / `ringervolume:50` 铃声）—— 宿主侧唯一实现。
 *
 * ## 两条命令、一个实现
 *
 *  `volume:` 写 `STREAM_MUSIC`，`ringervolume:` 写 `STREAM_RING`；除了「写哪一路」之外，
 *  量程折算、逐级降级、读回校验全都一样，所以只有 [run] / [runRinger] 两个入口分岔，
 *  往下走同一个 `apply`。
 *
 *  为什么拆成两条命令：来电 / 通知响不响与播放器音量是两件事，媒体音量拉满不代表手机能响。
 *  ColorOS 自己的小布指令也是这么拆的（`MediaVolumeTask` / `AudioRingProxy` 两个任务，
 *  一个写 3、一个写 2），这里跟着学 —— 连「flags 一律给 0」那条也是抄它的。
 *
 * ## 百分比说的是「音量条上的位置」
 *
 *  与屏幕亮度那一档同一套口径：`(当前档 / 量程)`，三个档的百分比都按**整条量程**算
 *  （`min + (max - min) * percent / 100`），不是按当前值的比例；越界夹到两端，不报错。
 *
 *  与 `keyevent:24` / `keyevent:25` 的区别就在这里：那两条是「按一下」，一次一格；这一族是
 *  「按百分比一段」。
 *
 * ## 改完要看得见
 *
 *  写入那一档带 `FLAG_SHOW_UI` —— 与按物理音量键同一种反馈，而且弹的就是**这一路流**的面板
 *  （调铃声弹铃声条）。不弹的话，按一下侧键屏幕上什么都没有，分不清「改了」还是「没生效」。
 *
 * ## 落到哪条路：**写这一半一律交给注入侧**
 *
 *  宿主自己的 `setStreamVolume` 会被 ColorOS 静默丢掉（`docs/device/actions/audio.md` 三十四），
 *  「写完读回」在宿主这边只是自欺 —— 值真的变了那一档能靠读回认出来，**值没变的那一档连认都认不出**
 *  （读回本来就等于目标）。所以宿主只做三件事：算目标档位、请注入侧（`system_server`）去写、
 *  写不进去时兜底（宿主自己再写几档 → 注入音量键）。
 *
 *  注入侧那条是系统身份，写得进去、也发得出面板（§三十五）；**值不变**时它改用
 *  `adjustStreamVolume` 把面板叫出来（`setStreamVolume` 同值不发面板，见 §三十七）。
 *  只有最后那条兜底的粒度是一颗键（量程的 1/16），细步进只能靠注入侧那一条精确路。
 */
internal object VolumeAction {
    /** 调哪一路流 —— 媒体（`volume:`）。 */
    private const val STREAM_MEDIA: Int = AudioManager.STREAM_MUSIC

    /** 调哪一路流 —— 铃声（`ringervolume:`）。来电 / 通知走这一路。 */
    private const val STREAM_RINGER: Int = AudioManager.STREAM_RING

    /**
     * 一次兜底注入最多按几颗 —— 16 颗，够从量程一头走到另一头（`0..160` 里一颗走 10 格）。
     *
     *  这是**兜底**那一路的总量上限，不是步进：正常那一下只按一两颗。它兜的是「一次写不进去、
     *  还要跨很大一段」的场合（`volume:0` 那种从满格掉到零）。
     */
    private const val MAX_INJECT_STEPS = 16

    /** 注入之后等系统把音量落下去的单次间隔 —— 读回还没变就再等一次。 */
    private const val SETTLE_MS = 25L

    /** 注入之后最多等几次（`SETTLE_MS` × 这个数 = 一颗键的等待上限）。 */
    private const val SETTLE_TRIES = 4

    /** 直写被丢掉之后再静默重写几次（间隔 [WRITE_RETRY_MS]）。 */
    private const val WRITE_RETRIES = 2

    /** 静默重写的间隔 —— 面板在场时系统会把一档拆成一串中间值连着写，隔一下再写才可能落地。 */
    private const val WRITE_RETRY_MS = 40L

    /** 等注入侧落地：每次间隔（[SYSTEM_SET_TRIES] × 这个数 = 等多久）。 */
    private const val SYSTEM_SET_MS = 30L

    /** 等注入侧落地最多探几次 —— 30ms × 10 = 300ms，跨进程派发 + 一次写够用，再多就只是卡按键。 */
    private const val SYSTEM_SET_TRIES = 10

    /**
     * 界面上音量条分几级 —— **16**：一颗键走 `min..max` 的 1/16（`0..160` 上就是 10 格，
     * 见 `docs/device/actions/audio.md` 三十三节末尾的实测）。
     *
     *  只用来判断「这一步比一颗键还小吗」：比一颗键还小的步进（`±1%` 这种）在注入这条路上
     *  无论如何都要整跳一格，那就不跳、攒着。第一颗键真的落地之后，档距以**实测值**为准，
     *  这里只是还不知道实测值时的起点。
     */
    private const val PANEL_LEVELS = 16

    /**
     * 按 [action] 调一次**媒体**音量。
     *
     *  取不到 `AudioManager` / 最大档为 0 时**直接放弃并记一行**；写入抛异常走 [onFailure] 报出去 ——
     *  这里在按键回调的下游，不向外抛。
     */
    fun run(
        context: Context?,
        action: Action.Volume,
        onFailure: (CommandFailure) -> Unit,
        /** 注入一次音量键（`keyevent` 那条路）—— 几条写都没落下时的最后一档。 */
        inject: (Int) -> Boolean,
        /** 请注入侧（`system_server`）把档位设成 (流, 下标) —— 宿主直写被丢掉时的精确路。 */
        setVolume: (Int, Int) -> Boolean,
    ) = apply(context, Call(STREAM_MEDIA, action.mode, action.percent, inject, setVolume), onFailure)

    /** 按 [action] 调一次**铃声**音量 —— 与 [run] 同一条路，只是换一路流。 */
    fun runRinger(
        context: Context?,
        action: Action.RingerVolume,
        onFailure: (CommandFailure) -> Unit,
        inject: (Int) -> Boolean,
        setVolume: (Int, Int) -> Boolean,
    ) = apply(context, Call(STREAM_RINGER, action.mode, action.percent, inject, setVolume), onFailure)

    /**
     * 一次调整的全部输入 —— 两条命令的差别只有 [stream]，其余一路传下去。
     *
     *  收成一个值而不是七个参数：这一族往下要穿好几层（`apply` → `Job` → 循环），
     *  每层都摊一遍参数表读起来全是噪音，加一档还得到处改签名。
     */
    private class Call(
        val stream: Int,
        val mode: Action.Volume.Mode,
        val percent: Int,
        val inject: (Int) -> Boolean,
        val setVolume: (Int, Int) -> Boolean,
    )

    /**
     * 已经落地的那一半：这一路流的句柄、量程、以及两条兜底通道。
     *
     *  「写目标 → 读回 → 不行再换一条路」这一整段都挂在这里（[write] 与它那几个私有小件），
     *  于是 [VolumeAction] 本体只剩「算目标」与「注入循环」两件事。
     */
    private class Job(
        private val am: AudioManager,
        private val call: Call,
        private val min: Int,
        private val max: Int,
    ) {
        private val stream: Int get() = call.stream

        /** 日志里那两路流怎么念。 */
        val label: String get() = if (stream == STREAM_RINGER) "铃声音量" else "媒体音量"

        /**
         * 一颗键大约走几格：量程 ÷ 界面级数（见 [PANEL_LEVELS]）。
         *
         *  只用在两处判断：这一步比一颗键还小吗（小的就不按，攒着）、欠账最多能攒到多少。
         *  真正按下去之后以实测差值为准。
         */
        val perKey: Int = ((max - min) / PANEL_LEVELS).coerceAtLeast(1)

        /**
         * 把 [target] 落下去 —— **写这一半交给注入侧**，宿主只在它没接上时兜底。
         *
         *  宿主自己那几档写挂在最后只为「模块没注入时还能凑合」：ColorOS 会把它们整条丢掉
         *  （`docs/device/actions/audio.md` 三十四），而读回判据又分不清「写成功」与「被丢掉」
         *  —— 值真变了才判得出来，值没变时恒真。
         *
         *  @return 需要带到下一次的误差格数（只有注入音量键那条路上会有）；不需要带时返回 null
         */
        fun write(
            target: Int,
            current: Int,
        ): Int? {
            //  **档位本来就等于目标**：没有值要改，只剩「把音量条叫出来」一件事，而这一档恰恰是
            //  读回判据唯一失效的地方（读回本来就等于目标）。注入侧那边对同一档走的是
            //  `adjustStreamVolume`（`setStreamVolume` 同值不发面板），见 [showOnly]。
            if (target == current) {
                showOnly(target)
                return null
            }

            //  第一档（正常路径）：**请注入侧（`system_server`）设**。它的身份是系统本身，
            //  那条丢掉宿主写的策略不挡它（§三十五），精确下标也只有这条给得出来
            //  （一颗键的粒度是量程的 1/16）。发出去之后靠读回认结果 ——
            //  注入侧没装 / 是老版本不认识这条广播时，读回自然不变，往下走兜底那几档。
            if (call.setVolume(stream, target) && await(target)) {
                XLog.w("$label：注入侧已把音量设到 $target")
                return null
            }

            //  第二档：宿主自己写（带 UI 的写 → 静默写 → 静默重试两次）。多半也会被丢，
            //  留着只为「注入侧不在」那一档；落在精确档位上仍然比退到整格的注入好。
            val landed =
                applyAndRead(target, AudioManager.FLAG_SHOW_UI) ||
                    applyAndRead(target, 0) ||
                    silentRetries(target)
            if (landed) return null

            //  第三档：注入音量键（铃声流不做，理由收在 [injectFallback] 里）。
            return injectFallback(target, current)
        }

        /**
         * 最后两档：铃声**直接放弃**注入兜底，媒体才一颗一颗按。
         *
         *  注入的音量键调的是「当前活跃的那一路」（多半是媒体流），按下去改的是播放器的音量而不是
         *  铃声 —— 用户要的是铃声响一点，结果音乐炸了，比明确报失败更糟。这一路上只留日志，
         *  失败提示交给 `apply`。
         *
         *  注入的是**虚拟键盘**事件（`deviceId = -1`），拦截路径会按「合成事件」放行给系统 ——
         *  不会自己认领回来形成循环（见 `SideKeyInterceptor.isSynthetic`）。
         *  [current] 是写之前那次读数：不能拿 0 兜底 —— 那会把「该往回调 16 格」算成空循环。
         */
        private fun injectFallback(
            target: Int,
            current: Int,
        ): Int? {
            if (stream == STREAM_RINGER) {
                XLog.w("$label：几条路都没落下去 ⇒ 不做注入兜底（注入音量键调的是当前活跃流，不是铃声流）")
                return null
            }
            return injectUntilReached(this, target, current)
        }

        /**
         * 值没变、只求「把音量条叫出来」—— 例如音量已经是 0 时的「设为 0」与「再减一下」。
         *
         *  这一档只发注入侧：值没有要改的，宿主那次写既判不出结果（读回本来就等于目标），
         *  在同值上也不发面板（`setStreamVolume` 同值不发 —— 真机实测，宿主与注入侧两边都一样）。
         *  注入侧对同一档走的是 `adjustStreamVolume(ADJUST_SAME, FLAG_SHOW_UI)`：物理音量键按到底时
         *  值也没变、面板照样弹，走的就是那一条。
         *
         *  注入侧没接上（模块没启用 / 老版本不认识这条广播）时才退回宿主自己那次带 UI 的写 ——
         *  前台能弹、后台会被丢，所以**不判结果**，只把这件事记一行。
         *
         *  @return 恒 `null` —— 这一档没有「没走完的零头」可带
         */
        private fun showOnly(target: Int): Int? {
            if (call.setVolume(stream, target)) {
                XLog.w("$label：档位没变（$target）⇒ 已请注入侧把音量条叫出来")
                return null
            }
            runCatching { am.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI) }
            XLog.w("$label：档位没变（$target）⇒ 注入侧那条没发出去，只剩宿主自己的带 UI 写（后台不弹）")
            return null
        }

        /**
         * 静默写 [target]，读回等于它就返回 true。
         *
         *  没落下去时把「固定音量 / 静音」一起写进日志：这两种状态下怎么写都不会变，
         *  是排查时唯一能分出来的两条线索。
         */
        private fun applyAndRead(
            target: Int,
            flags: Int,
        ): Boolean {
            am.setStreamVolume(stream, target, flags)
            if (runCatching { am.getStreamVolume(stream) }.getOrNull() == target) return true
            val fixed = runCatching { am.isVolumeFixed }.getOrDefault(false)
            val muted = runCatching { am.isStreamMute(stream) }.getOrDefault(false)
            XLog.w("$label：这次没写进去（flags=$flags，写 $target；固定音量=$fixed，静音=$muted）")
            return false
        }

        /** 静默重写两次（间隔 [WRITE_RETRY_MS]）；任何一次落地就返回 true。 */
        private fun silentRetries(target: Int): Boolean {
            repeat(WRITE_RETRIES) {
                SystemClock.sleep(WRITE_RETRY_MS)
                am.setStreamVolume(stream, target, 0)
                if (runCatching { am.getStreamVolume(stream) }.getOrNull() == target) {
                    XLog.w("$label：静默写第 ${it + 1} 次重试落下了（写 $target）")
                    return true
                }
            }
            return false
        }

        /**
         * 等注入侧把档位落下去 —— 读到 [target] 就算到，最多等 [SYSTEM_SET_TRIES] 次。
         *
         *  广播是异步的：发出到注入侧 worker 真的调用 `setStreamVolume` 之间隔着一次跨进程派发，
         *  立刻读回一定还是旧值（那会被当成「没设成功」，接着又去注入按键、把音量再推一格）。
         */
        private fun await(target: Int): Boolean {
            repeat(SYSTEM_SET_TRIES) {
                SystemClock.sleep(SYSTEM_SET_MS)
                if (runCatching { am.getStreamVolume(stream) }.getOrNull() == target) return true
            }
            return false
        }

        /**
         * 注入一颗键并读回 —— 最多等 [SETTLE_TRIES] 次；投不进去或读不出来时返回 null（这一轮就停）。
         *
         *  两个理由都要等：注入是异步下发的（`INJECT_MODE_ASYNC`），立刻读回拿到的还是注入前的值，
         *  会被当成「这一颗被吞了」而多补一颗、正好多跳一格；而音量面板在场时系统会把一档拆成
         *  一串中间值连着写（`volume_changed` 里能看到 `0 → 52 → 90 → …`），只读一次容易拿到中间值，
         *  下一轮于是少补一颗。所以**连续两次读回同一个值**才算落定。
         */
        fun pressOnce(
            keyCode: Int,
            before: Int,
        ): Int? {
            if (!call.inject(keyCode)) return null
            var last = before
            repeat(SETTLE_TRIES) {
                SystemClock.sleep(SETTLE_MS)
                val now = runCatching { am.getStreamVolume(stream) }.getOrNull() ?: return null
                if (now != before && now == last) return now
                last = now
            }
            return last
        }
    }

    /**
     * 两条命令的公共实现：算目标档位、交给 [Job] 落下去、把没走完的零头记回来。
     */
    private fun apply(
        context: Context?,
        call: Call,
        onFailure: (CommandFailure) -> Unit,
    ) {
        val am =
            context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: run {
                XLog.w("没有 AudioManager，音量调整跳过")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
        try {
            val max = am.getStreamMaxVolume(call.stream)
            if (max <= 0) {
                XLog.w("音量调整跳过：最大档是 $max")
                onFailure(CommandFailure.UNKNOWN)
                return
            }
            //  **按 min..max 这段可用量程折算**（不是 `max * percent / 100`）：抄的是 ColorOS
            //  小布指令 `MediaVolumeTask` 那一条 —— 它先取 `getStreamMinVolume`，再用
            //  `min + (max - min) * p / 100`。这台机器上两路流的 min 都是 0（`dumpsys audio`：
            //  铃声 `0..16`、媒体 `0..160`），两种写法等价；但别的 ROM 上流的 min 不一定是 0，
            //  那时写成 `max * p / 100` 会整体偏高一档。
            val min = am.getStreamMinVolume(call.stream)
            val job = Job(am, call, min, max)
            val current = am.getStreamVolume(call.stream)
            val raw = (max - min) * call.percent / 100.0
            val target =
                when (call.mode) {
                    //  设定是**绝对位置**，没有零头可攒：顺手把攒下的清掉
                    Action.Volume.Mode.FIX -> {
                        remainder.remove(call.stream)
                        min + raw.roundToInt()
                    }

                    Action.Volume.Mode.ADD -> {
                        current + stepFor(call.stream, raw)
                    }

                    Action.Volume.Mode.SUB -> {
                        current - stepFor(call.stream, raw)
                    }
                }
            val clamped = target.coerceIn(min, max)
            //  被量程顶住了 ⇒ 那点「没走完的零头」再也走不完，清掉，免得反方向第一下就跳两格
            if (clamped != target) remainder.remove(call.stream)
            XLog.w("${job.label}：档 $clamped/$max（当前 $current，${call.mode} ${call.percent}% ⇒ 走了 ${clamped - current} 格）")
            //  「落不下的那点」带到下一次：只有注入这条路上会有（见 `injectUntilReached`）。
            //  设定那一档不带 —— 绝对位置没有可攒的余地，与上面 `remainder.remove` 同一口径。
            //  **上限一颗键**：欠账攒过头会让接下来好多下都不动，用户只看到「按了没反应」。
            job.write(clamped, current)?.let { carry ->
                if (call.mode != Action.Volume.Mode.FIX) {
                    val limit = job.perKey.toDouble()
                    remainder[call.stream] =
                        (remainder.getOrDefault(call.stream, 0.0) + carry).coerceIn(-limit, limit)
                }
            }
        } catch (e: Throwable) {
            XLog.w("音量调整失败（已忽略）：${e.javaClass.simpleName}: ${e.message}")
            onFailure(CommandFailure.UNKNOWN)
        }
    }

    /**
     * 「上几次没走完的那点零头」—— 按流记，只服务 [stepFor]。
     *
     *  为什么需要：量程是**整数格**的，而百分比不一定是整数格。铃声流在这台机器上是
     *  `0..16`（`dumpsys audio` 实测），10% = 1.6 格 —— 每一下都四舍五入成 2 格的话，
     *  八下就满了（真机反馈「设成 10 的步进，从 0 到 100 按了八次就满了」），而每一下都
     *  舍成 1 格又会慢一半。攒零头之后：2、1、2、1、2…，十下正好走完 16 格。
     */
    private val remainder = java.util.concurrent.ConcurrentHashMap<Int, Double>()

    /**
     * 这一下该走几格 —— `raw` 加上上次剩下的零头再四舍五入，剩下的接着攒。
     *
     *  读的是**当前实际档位**（调用方刚读的 `current`），所以用户中途用音量键 / 音量面板改过
     *  也不受影响；被量程顶住时调用方会把这笔零头清掉（见 `apply`）。
     *
     *  **攒成负数时一格都不走**（`coerceAtLeast(0)`）：负的零头是「上一次走过头了」欠下的账，
     *  让它把接下来几下的步进抵掉，平均下来才是设的那个百分比；反过来走（升变降）就说不通。
     */
    private fun stepFor(
        stream: Int,
        raw: Double,
    ): Int {
        val acc = remainder.getOrDefault(stream, 0.0) + raw
        val steps = acc.roundToInt().coerceAtLeast(0)
        remainder[stream] = acc - steps
        return steps
    }

    /**
     * 注入音量键把音量挪到 [target] —— **一颗一颗按，每按完读回一次**。
     *
     *  为什么不按「一颗键走几格」一次算好次数：那个折算只在「每颗都落地、档距永远是那个数」
     *  时才成立。注入是异步下发的、也可能被系统吞掉一颗，差一格就会一路累积 —— 按 10% 的步进
     *  从 0 拉到满要按十几次，正是每一下只走到 6% 上下的样子（档距 10 格、只落地一颗）。
     *  读回之后不靠假设：还剩多少、一颗实际走了几格，全都按**实测差值**现算，档距换了也不影响。
     *
     *  **只朝一个方向按**：越过目标就停，绝不反向补一颗。「减 1%」这种比一颗键（10 格）还小的
     *  步进，一颗按下去必然越过 8 格，回头再补一颗就正好回到原处 —— 用户看到的是「减了又被弹回来」。
     *
     *  **比一颗键还小的步进一颗都不按**：注入这条路给不出一格以下的变化，硬按就是「1% 走成 6%」。
     *  那点差值带回去攒着（见返回值），攒够一整颗再真的动 —— 平均下来才是设的那个百分比。
     *
     *  @param base 注入之前的实际档位（不是目标）—— 第一颗的差值就按它算
     *  @return 还差多少格没走到 / 走过头了多少（只在「一步之内差得出来」时才非 null）——
     *    调用方把它带到下一次，小步进才攒得起来
     */
    private fun injectUntilReached(
        job: Job,
        target: Int,
        base: Int,
    ): Int? {
        //  往哪边走在这一层定死：中途不再取反（理由见函数头）
        val dir = if (target >= base) 1 else -1
        val keyCode = if (dir > 0) KeyId.KEYCODE_VOLUME_UP else KeyId.KEYCODE_VOLUME_DOWN
        var current = base
        var perKey = job.perKey
        var measured = false
        var injected = 0
        var still = 0
        //  还差多少格（正数 = 这个方向还没走够；0 或负数 = 已到 / 已越过）
        var gap = (target - current) * dir
        while (keepPressing(injected, gap, perKey, still)) {
            val before = current
            val after = job.pressOnce(keyCode, before) ?: break
            injected++
            current = after
            val moved = abs(after - before)
            still = if (moved == 0) still + 1 else 0
            if (moved > 0) {
                perKey = moved
                measured = true
            }
            gap = (target - current) * dir
        }
        val short = (target - current) * dir
        XLog.w(
            "${job.label}：几条写都没落下去 ⇒ 注入音量键 $injected 颗，落到 $current" +
                "（目标 $target，一颗 $perKey 格${if (measured) "" else "（估值）"}，还差 $short 格）",
        )
        //  **差额带到下一次**：正数是「还没走够」（一颗键 10 格、10% 是 16 格，注入这条路上只能
        //  落在格线上），负数是「走过头了」—— 两种都带过去，下一次的步进自己会多走 / 少走，
        //  连按十几下平均下来才是设的那个百分比。不带的话小步进永远是「按一下跳一格」。
        return short.takeIf { it != 0 && abs(it) <= perKey }
    }

    /** 还要不要再按一颗键 —— 三条停手条件分两处写，只为让每处都不超过判据本身能读的复杂度。 */
    private fun keepPressing(
        injected: Int,
        gap: Int,
        perKey: Int,
        still: Int,
    ): Boolean = injected < MAX_INJECT_STEPS && still < 2 && worthPressing(gap, perKey)

    /** 这一下还值不值得按：越过目标就别按（绝不反向补），差得比半颗键还少也别按。 */
    private fun worthPressing(
        gap: Int,
        perKey: Int,
    ): Boolean = gap > 0 && gap * 2 > perKey
}
