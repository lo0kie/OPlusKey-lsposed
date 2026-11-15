package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
import com.lookie.opluskey.core.model.ComboPlan
import com.lookie.opluskey.core.model.GestureTrigger
import com.lookie.opluskey.core.model.KeyConfig
import com.lookie.opluskey.core.model.KeyId
import java.util.concurrent.ConcurrentHashMap

/**
 * 窗口的**兜底值**（毫秒）—— 只在配置还没送到、或送来的值不合法时用。
 *
 *  正常取值走 [ComboDetector] 的 `windowMs`（用户可在「组合按键设置」里调）。真机实测：
 *  电源与音量下同时按，两条 DOWN 相差 3ms（同一线程、同一串事件），出厂 80ms 既盖得住人手的
 *  同时按，又不会把「先按 A、松手后再按 B」这种连按算成组合。
 */
private const val FALLBACK_WINDOW_MS = 80L

/**
 * 「长按连续触发」的外部终止上限（毫秒）—— 与单键那条同一个数与同一条理由
 * （见 `GestureDetector.REPEAT_MAX_MS`）：抬手事件丢了、没有任何回调会停这条链。
 */
private const val COMBO_REPEAT_MAX_MS = 30_000L

/**
 * 多成员组合的**最少成员数** —— 3 颗。
 *
 *  两颗那一档不走多成员这条路（见本文件 `pairFor` 与 `multiFor` 的分工）：
 *  那边要在候选里挑「按下时刻由近到远」，结论更细。3 颗起才需要「整体凑齐」这个判据。
 */
private const val MULTI_MIN_KEYS = ComboKey.MIN_KEYS + 1

/**
 * 按住组合里**后续成员**的窗口兜底值（毫秒）—— 只在配置还没送到、或送来的值不合法时用。
 *
 *  正常取值走四格里那一格（见 [DetectorBinds.windowsFor]）：固定窗口起点开着用 `hold`
 *  （从首个成员按下起算一个），关着用 `holdFollower`（每颗各自往前一个）。
 *  逐次重置那一档只判「后续那一颗是不是紧接着落下的」，150ms 够人手一颗一颗按下去，
 *  又不至于把「按完 B 过一会儿再按 C」也算进来。
 */
private const val HOLD_FOLLOWER_WINDOW_MS = 150L

/**
 * 组合键的判定 —— **几颗已接管的键**在窗口内先后按下，就当作一次组合，不再各自判单击 / 长按。
 *
 *  与 [GestureDetector] 的分工：那个判单颗键的手势，这个只判「几颗键的重叠」。判出组合之后
 *  执行什么由装配方决定（`onCombo`），这里只回答「哪一档 + 短按还是长按」；一旦认出组合就
 *  摘掉成员键的单键状态机（[resetKey]），一次按键只出一个动作。
 *
 *  判据的**完整口径**（顺序按下算组合、按住档、配对 / 凑齐、更宽升级、长按阈值、线程模型）
 *  见 `docs/runtime/combo-detector.md` —— 这里不再逐段复述，改判据前先读那一页。
 */
internal class ComboDetector(
    private val handler: Handler,
    /**
     * 判出一次组合：哪一档 + 哪一档手势 + 是不是**连续触发**的重复派发。
     *
     *  `repeat` 与单键那条 `onGesture` 的第三位同义：重复派发不再振动、不再报活（那是同一次按压）。
     */
    private val onCombo: (ComboKey, GestureTrigger, Boolean) -> Unit,
    /**
     * **某颗键刚按下** —— 按住组合的系统场景要按「首键按下」那一刻的条件定下来
     * （见宿主 `GestureExec.handleComboScene`）。
     *
     *  这一条每次按下都会走，装配方**必须**自己先判「这颗键是不是某按住组合的起点」
     *  （`ConfigChannel.holdStarter`），不是就直接返回 —— 只有那几个起点才值得发一条广播。
     */
    private val onHoldStarter: (KeyId) -> Unit,
    /** **某一档组合刚凑成** —— 关掉「首键按下固定」时，场景按这一刻的条件定（同上）。 */
    private val onComboFormed: (ComboKey) -> Unit,
    /** 摘掉某颗键的单键计时器（组合成立时用，见类注释）。 */
    private val resetKey: (KeyId) -> Unit,
    /**
     * 这几颗键凑成的**全部**档位 —— 空表 = 没有绑定，组合不成立（见类注释）。
     *
     *  由装配方接 `ConfigChannel.combosFor`（读注入侧手上那份配置的 `combos`）。**成员数不设限**：
     *  两颗那一档一对一地问（`pairFor`），3 颗起把窗口内按下的那一组整体递进来（`multiFor`）。
     *
     *  **为什么是几张而不是一张**：同一组键可以配几档、各自一种触发方式（`ComboKey.mode`），
     *  三条判据各不相同 —— 只取成员数最大的那一档会把另一种方式的档整条遮掉。
     *  成员多的在前，成员数相同时保持配置顺序（`Config.combosFor` 给的顺序）。
     */
    private val bindingsFor: (List<KeyId>) -> List<ComboKey>,
    /**
     * 当前的**扫描**窗口（毫秒）—— 找同伴时按这个宽度扫候选，取的是这份配置里最宽的那一档
     * （`Config.comboScanWindowMs`）。它只管「扫多宽」，**算不算数**由 [spanFor] 判。
     */
    private val windowMs: () -> Long,
    /**
     * 某一档组合**起点键要先按住多久**（毫秒，`Config.comboHoldMinFor`）—— 按住那两档的下限。
     *
     *  语义是「先按住一颗、再按另一颗」：两颗键**几乎同时**落下（实测相差 0~40ms，
     *  谁先上报看硬件）不算按住 —— 那种「一起按」只归窗口那条；窗口那条的档根本走不到这里。
     *  没有这条下限，一起按会按物理先后落进某一种顺序里，于是同一个动作有时命中、有时整条落空
     *  （真机日志里 53 次一起按，23 次这种落空）。
     */
    private val holdMinFor: (ComboKey) -> Long,
    /**
     * 这一颗键是某一档**等窗口**的已启用组合的成员吗 —— 决定它按下之后要不要**先等一个窗口**。
     *
     *  参与的键等着看「同伴键会不会来」（这期间不喂单键判定器，用户能感觉到的就是「参与组合的键
     *  按下去晚一点才有反应」）；不参与的键立刻交回单键，一点不受影响。[multiFor] 也拿它当第一道
     *  闸：不参与任何窗口组合的键，不可能是多成员那一档的成员（那条只走窗口判据）。
     *
     *  **按住那两档的成员不算**：它们不等窗口（判据是「另一颗先按着」），压一个窗口换不来任何东西。
     */
    private val joinsCombo: (KeyId) -> Boolean,
    /**
     * 这颗键是**任何一档**组合（含按住档）的成员吗 —— 多成员那条路的头道闸
     * （`ConfigChannel.inAnyCombo`）。
     *
     *  与 [joinsCombo] 分开：那个答「按下之后要不要压一个窗口」（只有等待档要），
     *  这个只答「归不归组合管」。按住档的后续成员不等窗口，但它照样要能凑齐 ——
     *  拿 [joinsCombo] 挡的话，按住组合的三颗以上永远凑不成。
     */
    private val inAnyCombo: (KeyId) -> Boolean,
    /**
     * 这一档**整串**的容忍跨度（毫秒）—— 见 `Config.comboSpanFor`：固定窗口起点开着就是那一个
     * 窗口，关着则是「每两颗各一个」（n 颗 = n-1 个窗口）。
     *
     *  多成员那条路（[multiFor]）与「等更宽的一档」（[awaitWider]）都按它判「来不来得及」。
     */
    private val spanFor: (ComboKey) -> Long,
    /**
     * 含有这几颗键、且**成员更多**的那一档（`null` = 没有更宽的一档）—— 「两颗那一档要不要先等
     * 三颗那一档」的判据，见 [awaitWider]。与 [bindingsFor] 分开是因为两者回答的是不同问题：
     * 那个答「这几颗键凑成了哪一档」，这个答「还没凑齐的更宽那一档是什么」。
     */
    private val widerFor: (List<KeyId>) -> ComboKey?,
    /**
     * 这一颗键从按下（第二参）到此刻（第三参）**是不是已经判过长按**了。
     *
     *  组合只在这颗键的长按判出来**之前**成立：判过之后它已经执行了自己的长按动作，再认组合
     *  就是一次按压两个动作。判定层读不到 `KeyConfig`，所以由装配方按那颗键自己的
     *  `longPressMs` 算；「只参与非首键的按住组合」的键恒返回 `false`（它压根不判单键）。
     */
    private val longPressPassed: (KeyId, Long, Long) -> Boolean = { _, _, _ -> false },
    /**
     * 那几样**可选功能**的回调 —— 见 [DetectorBinds]：首键补派、按住档固定窗口起点、四格窗口。
     *
     *  收成一份 data class 而不是三个构造参数：它们不是「判定器必须知道什么」，是可选功能，
     *  早先只能放类体 `var` 再 `apply { }` 设（构造清单已贴 detekt 上限），那样「必须设」被降成
     *  可空、装配也有两个入口。现在一次给全、类型非空。
     */
    private val binds: DetectorBinds = DetectorBinds(),
) {
    /**
     * 一次 DOWN 的处理结果 —— 调用方据此决定「吞掉 / 延迟喂 / 立刻喂」。
     */
    enum class Down {
        /** 归组合：这一下已经由组合接管，调用方**不要再喂单键判定器**。 */
        TAKEN,

        /** 这颗键参与组合、但组合还没凑成：调用方先压住一个窗口，到点再喂单键。 */
        HOLD,

        /** 与组合无关：立刻喂单键判定器，行为与没有组合键时完全一致。 */
        PASS,
    }

    /**
     * 这一刻按着的那几颗键（key → 按下时刻）。
     *
     *  **必须是并发容器**：写在输入线程（`onDown` / `onUp`），读在主 looper 的定时任务里
     *  （`scheduleLong` / `scheduleRepeat` 里那两处「还按着没有」）—— 普通 `HashMap` 在这条路径上
     *  既没有可见性保证、也受并发修改影响，表现是「组合长按有时候不触发」。
     *  类注释那条「跨线程的字段一律 `@Volatile`」对集合不成立（volatile 只管引用），
     *  所以这里用 `ConcurrentHashMap` 顶替，语义不变（`entries` 迭代是弱一致的）。
     *
     *  **判「按着没有」一律写 `containsKey(it)`、不能写 `it in down`**：`ConcurrentHashMap`
     *  继承下来的 `contains` 实际是 `containsValue`（不是 `containsKey`），`in` 会编译报错；
     *  真按值去比也会得到「永远不成立」。
     */
    private val down = ConcurrentHashMap<KeyId, Long>()

    /**
     * 配对的候选（key → 最近一次按下的时刻）—— **抬手也留着**，只按窗口过期。
     *
     *  为什么要与 [down] 分成两张表：「先按 A、松手、紧接着按 B」在用户口径里也算组合
     *  （判据仍是两颗键的 DOWN 相差在窗口内），而「一起按住才算长按」必须看还按着没有 ——
     *  一张表兼两用会让其中一条走样。早先只有 [down] 且抬手就删，于是松手后的 B 配不上 A：
     *  A 的单击先跑掉了，B 也自判单击，用户看到的就是「两颗键各自的单击都触发、组合也触发」。
     */
    private val recent = ConcurrentHashMap<KeyId, Long>()

    /**
     * 配对的按下**序号**（key → 第几次按下）—— 单调递增，只给「按顺序触发」那一档（`ComboKey.ordered`）
     * 比先后。
     *
     *  为什么与 [recent]（时刻）分开：两颗键几乎同时按下时，时刻可能相同或相差 1ms，用它排顺序
     *  会随硬件上报次序摇摆；而序号按**事件到达的先后**单调递增，是「用户手指先后」最稳的近似。
     *  与 [recent] 同一条线程规则：写在输入线程，读也在输入线程（`pairFor` / `multiFor`），
     *  但 `reset` 会从配置广播线程清它，所以用并发容器。
     */
    private val pressSeq = ConcurrentHashMap<KeyId, Int>()

    /** 序号发生器 —— 每写一次 [pressSeq] 递增，只增不减（`reset` 只清表、不清这个数）。 */
    private var seqCounter = 0

    /**
     * 这一次按压已经被组合吃掉的键 —— 它们的抬手也要吞掉。
     *
     *  不清的话：组合的第一颗键先松开、组合结束，第二颗键的抬手会让单键判定器当成
     *  「孤立抬手」补判一次单击。
     *
     *  **必须是并发集合**：写在输入线程（[onDown] / [onUp]），配置广播那条线会经
     *  `reset()` 整体清空 —— 普通 `HashSet` 在这里是未定义行为，表现是「某颗键偶尔按了没反应」。
     */
    private val consumed: MutableSet<KeyId> = ConcurrentHashMap.newKeySet()

    /** 正在进行的组合；`null` = 没有。 */
    @Volatile
    private var combo: Combo? = null

    /**
     * 「首键那次按压自己的长按**还欠着**」—— 组合已经收尾、首键却还按着，等它抬手时补派。
     *
     *  为什么需要跨组合存：按住组合的模型是**首键一直按着、成员按一下就松**。成员松手那一刻
     *  组合就收尾了（`combo = null`），而首键还要过一会儿才抬手 —— 等它抬手时已经找不到
     *  是哪一组、开没开这一档了。所以在收尾那一刻把「欠着的那颗首键」记下来。
     *
     *  **只记首键，不记成员**：成员的按下 / 抬手走的是它自己的单键状态机（组合只摘掉
     *  `resetKey` 那一刻已经排上的长按枪，成员自己再按一次照样判长按）。
     *
     *  写它的只有组合收尾那一支，读它的只有 [onUp] 开头；[reset] 一并清。
     */
    @Volatile
    private var starterKeepPending: KeyId? = null

    /**
     * 这颗键此刻是不是**某一组组合的成员** —— 拦截层据此决定「压着的那一下要不要补发原生事件」。
     *
     *  只回答「归不归组合」，不回答「这一下成没成」：`combo` 一建立就覆盖了它的成员，
     *  所以还在压着的那几颗一旦被吸收，这里立刻为真 —— 补发任务到点时正是要这个结论。
     */
    fun comboCovers(id: KeyId): Boolean = combo?.keys?.contains(id) == true

    private class Combo(
        /** 这一档是谁 —— 执行什么由它决定（`single` / `long` 两格）。 */
        val key: ComboKey,
        val keys: List<KeyId>,
        /** 这一组的两个时刻，见 [Times]。 */
        val times: Times,
        /** 长按那一枪，抬手 / 重置时摘掉。 */
        @Volatile var shot: Runnable? = null,
        /** 已经判过「一起按住不放」。 */
        @Volatile var longFired: Boolean = false,
        /** 连发那一枪（`longRepeat` 打开时才有），抬手 / 重置时摘掉。 */
        @Volatile var repeatShot: Runnable? = null,
    ) {
        /**
         * 正在等的**更宽那一档**（`null` = 没在等）—— 见 [awaitWider]。
         *
         *  与 `shot` / `repeatShot` 同一档：都是这一组的定时链，`cancelShots` 一起撤。
         *  **写在类体里而不是构造参数**：它只在等的期间有意义，没有构造时赋值的需要。
         */
        @Volatile
        var waiting: Waiting? = null

        /**
         * 这一组**当前处在哪个阶段** —— 由 [waiting] / [longFired] / [repeatShot] 三个标志
         * 派生出来，是**唯一**的判据入口。
         *
         *  早先「这一组是什么状态」要靠调用点自己去组合这三个标志（`waiting != null && !longFired`
         *  之类），同一套组合在 [absorb] / [onUp] / [awaitWider] / [scheduleRepeat] 里各写一份，
         *  加一个阶段就要改四处。现在收成这一个属性，判「该走哪条路」只看它。
         *
         *  三个标志仍是**事实来源**（阶段由它们推出、不另存一份可变状态，避免两头漂）：
         *  这里只是把「读三样推理状态」变成「读一个名字」，行为不变。
         *
         *  派生顺序有讲究：`repeatShot` 只在长按判出之后才有，所以 `REPEATING` 必须先于
         *  `LONG_FIRED` 判；`waiting` 只在长按判出**之前**有意义（长按是「按住不放」的明确表态，
         *  到那一刻不再等下一颗），所以 `AWAIT_WIDER` 在 `LONG_FIRED` 之前。
         */
        val phase: ComboPhase
            get() =
                when {
                    repeatShot != null -> ComboPhase.REPEATING
                    longFired -> ComboPhase.LONG_FIRED
                    waiting != null -> ComboPhase.AWAIT_WIDER
                    else -> ComboPhase.PAIRED
                }
    }

    /**
     * 这一组的两个时刻（都取自 `elapsedRealtime`）—— 两件事的量法从一开始就不是一回事，
     * 混用一个数会在**升级**时立刻出错（[absorb] 换的是更大的一档，而它整串的跨度
     * 从最早那一颗算起、与「这一组什么时候成立」无关）。
     *
     *  @param formed 组合成立那一刻（长按阈值的起点由 [scheduleLong] 的 `startedAt` 单独给，
     *    连发上限与「挂住多久算异常」从这里量）
     *  @param firstDown **最早**那一颗按下的时刻 —— 所有「整串跨度」的判断都从它算：
     *    「更宽的那一档还来不来得及」([awaitWider])、「这一颗还能不能升上去」([absorb])。
     *    升级时原样带过去，于是 `{A,B}` → `{A,B,C}` → `{A,B,C,D}` 这条链上等的是同一个起点。
     */
    private class Times(
        val formed: Long,
        val firstDown: Long,
    )

    /**
     * 在等的更宽的那一档 —— 等谁、等到什么时候、那一枪。
     *
     *  为什么把「等到什么时候」存在这里而不是 [Combo] 上：它只在等的期间有意义，跟着 `waiting`
     *  一起清掉最省事（清 `waiting` 就是「不再等」这一个语义）。
     */
    private class Waiting(
        val key: ComboKey,
        /** 这一枪的时刻（`elapsedRealtime`）—— 更宽那一档从最早那一颗算起的整串跨度。 */
        val until: Long,
        val shot: Runnable,
    )

    /**
     * 一组组合在判定侧走过的阶段 —— 见 [Combo.phase]，那是唯一判据入口。
     *
     *  只描述「这一组现在在等什么」，不含「有没有派发过」—— 派发与阶段是两件事：
     *  [PAIRED] 也可能已经按「一起短按」派发过（成员一松就收尾），收尾时照旧再走一遍判据。
     */
    private enum class ComboPhase {
        /** 已凑成，还没等到长按阈值（长按枪还挂着）。 */
        PAIRED,

        /** 已凑成，且还有更宽的一档来来得及 ⇒ 先不派发，等它窗口结束。 */
        AWAIT_WIDER,

        /** 长按已经判出（`longFired`），但没在连发。 */
        LONG_FIRED,

        /** 长按判出后，`longRepeat` 那串自循环连发还在跑。 */
        REPEATING,
    }

    /**
     * 组合进行中又按下 [id] —— `true` = 这一颗归组合（调用方吞掉），`false` = 这一组已经收尾并清掉，
     * 这一颗继续走正常判定。
     *
     *  三种结局：
     *  1) **升成更大的一档**：这一颗正是更宽那一档缺的那颗 —— 换过去，抬手时派发大的那个；
     *  2) **吞掉**：成员还都按着（用户就是在按住这一组），或者这一组没在等更宽的一档 ——
     *     老语义「组合进行中再来的按键只出一组」；
     *  3) **先收尾、再放行**：这一组只在[等更宽的一档][awaitWider]、而成员已经松过手 ⇒
     *     这一组处在「待派发」状态。把它按「一起短按」派发掉，让这一颗走正常路径。
     *     不这么办的话，`{A,B}` 凑成之后紧接着单独按 A、或者按一颗跟它无关的键，都会整颗
     *     失灵到窗口结束 —— 而那几个键在手感上就是「刚按完组合，键坏了」。
     *
     *  ## 升级为什么必须让位、又为什么这样判
     *
     *  「成员多的赢」是配置侧的取法（`Config.comboFor` 的 `maxByOrNull { keys.size }`），但判定侧
     *  按**时间**走：子集那一档在最后一颗按下那一刻就成立了，而更大的一档要等下一颗。不让位的话
     *  `combo != null` 那一支会把它整条吞掉 —— 真机日志就是这么写的：`{A,B}` 同时按下 →
     *  第三颗被吞 → 只有子集那一档响。
     *
     *  · 只在组合**还没松手**（`combo != null`）时升级：抬手之后那一档已经派发过，换不回来了；
     *  · 跨度按这一档**从最早那一颗算起**（[Times.firstDown]，升级时原样带过去）：三颗不是
     *    「按着不放多久都能补」，超出窗口的那一颗照旧只吞不认；
     *  · **长按派发过就不再升**：再升会把「这一档的长按动作」与「更大那一档的动作」各执行一次 ——
     *    一次按压两个动作。长按本身就是「按住不放」的明确表态，到那一刻就不再等下一颗了。
     */
    private fun absorb(
        c: Combo,
        id: KeyId,
        now: Long,
    ): Boolean {
        if (!c.longFired) {
            val pool = c.keys + id
            //  按顺序触发那一档：升级也要按真实按下序比（pool 里是配置序 + 新键，不是按下序）。
            val bigger =
                orderMatch(bindingsFor(pool), pressOrder(pool)).firstOrNull {
                    it.keys.size > c.keys.size && it.isWait && now - c.times.firstDown <= spanFor(it)
                }
            //  成员按 id 认回 `KeyId` 对象（`matches` 保证了它们都在 pool 里）；
            //  认不出的只可能是配置在两次按下之间换了 —— 那就别升，保持原来那一档
            val members = bigger?.keys?.mapNotNull { kid -> pool.firstOrNull { it.id == kid } }.orEmpty()
            if (bigger != null && members.size == bigger.keys.size) {
                cancelShots(c)
                val next = Combo(bigger, members, Times(formed = now, firstDown = c.times.firstDown))
                combo = next
                members.filterTo(consumed) { down.containsKey(it) }
                for (k in members) resetKey(k)
                XLog.w(
                    "组合键：${c.keys.joinToString(" + ") { it.label }} 升级为 " +
                        "${members.joinToString(" + ") { it.label }}（${bigger.id}，成员多的赢）",
                )
                scheduleLong(next, startedAt = now)
                //  更大的那一档自己也可能还差几颗（`{A,B}` ⊂ `{A,B,C}` ⊂ `{A,B,C,D}` 这种链）—— 接着等
                awaitWider(next, now)
                return true
            }
        }
        val waiting = c.waiting
        //  成员还都按着 ⇒ 这一下还在这一组里，新按下的那颗不掺和；没在等更宽的一档也照老规矩吞掉
        if (c.phase != ComboPhase.AWAIT_WIDER || c.keys.all { down.containsKey(it) }) {
            consumed += id
            return true
        }
        //  这一组等不到更宽的那一档了：先派发（成员已经松过手，动作只能现在补），再放行这一颗
        cancelShots(c)
        if (!c.longFired) {
            XLog.w(
                "组合键：${c.keys.joinToString(" + ") { it.label }} 短按" +
                    "（${id.label} 按下，不再等 ${waiting!!.key.id}）",
            )
            fire(c, GestureTrigger.Click(1), repeat = false)
        }
        combo = null
        return false
    }

    /**
     * 更宽的一档还来得及凑齐吗 —— 是则**先不派发这一档**，把收尾压到它窗口结束那一刻。
     *
     *  判据只有一条：含有这几颗键的更大那一档（[widerFor]），**从最早那一颗算起**
     *  的整串跨度还没用完（[spanFor]）—— 那意味着再按一颗还赶得上。用完就照旧
     *  立刻派发（下一颗再来也来不及了，等它只是白慢）。
     *
     *  **成员数不设限**：判的是「这一档的成员集是不是另一档的真子集」，所以 `{A,B}` 等 `{A,B,C}`、
     *  `{A,B,C}` 再等 `{A,B,C,D}` 走的是同一条路（升级之后 [absorb] 会再调一次本函数）。
     *
     *  到点那一枪的四种结局：
     *  · 这一组已经被换掉 / 已经被收掉 ⇒ 作废；
     *  · 长按已经派发过 ⇒ 什么都不做（收尾归抬手那条老路）；
     *  · 成员还都按着 ⇒ 用户在按住这一组，收尾同样交回长按 / 抬手；
     *  · 已经松过手、又没等到更宽的那一档 ⇒ 派发这一档的短按。
     *
     *  @param now 这一组成立的那一刻（等多久从 [Times.firstDown] 算，不是从它算）
     */
    private fun awaitWider(
        c: Combo,
        now: Long,
    ) {
        //  只等「真的更宽」的那一档：成员集合相同的那一档不是「还差几颗」
        val wider = widerFor(c.keys)?.takeIf { it.keys.size > c.keys.size } ?: return
        val until = c.times.firstDown + spanFor(wider)
        if (until <= now) return
        lateinit var waiting: Waiting
        waiting =
            Waiting(
                wider,
                until,
                Runnable {
                    try {
                        val self = waiting
                        //  已经被换掉 / 已经被别处收掉（抬手、重置、另一个组合）⇒ 这一枪作废
                        if (combo !== c || c.waiting !== self) return@Runnable
                        c.waiting = null
                        if (c.longFired || c.keys.all { down.containsKey(it) }) return@Runnable
                        XLog.w(
                            "组合键：${c.keys.joinToString(" + ") { it.label }} 短按" +
                                "（等到点也没凑成 ${self.key.id}）",
                        )
                        fire(c, GestureTrigger.Click(1), repeat = false)
                        finishCombo(c)
                    } catch (e: Throwable) {
                        // 这一枪跑在 system_server 主线程上，抛出去就是整机崩
                        XLog.w("组合键等宽判定失败（忽略）：${e.javaClass.simpleName}", e)
                    }
                },
            )
        c.waiting = waiting
        handler.postDelayed(waiting.shot, (until - now).coerceAtLeast(1L))
        // 判据级：这一行是「按了为什么还没反应」的唯一凭据（只在配了子集关系时才会出现）。
        XLog.w(
            "组合键：${c.keys.joinToString(" + ") { it.label }} 先不派发 —— " +
                "${wider.id} 还差 ${wider.keys.size - c.keys.size} 颗，" +
                "窗口还剩 ${until - now}ms",
        )
    }

    /**
     * @return 见 [Down]：`TAKEN` = 归组合（调用方不要再喂单键）；`HOLD` = 先压一个窗口；
     *   `PASS` = 与组合无关，立刻交给单键判定器。
     */
    fun onDown(
        id: KeyId,
        now: Long = SystemClock.elapsedRealtime(),
    ): Down {
        //  挂住的组合先自愈 —— 成员键的抬手丢了（锁屏打断 / 事件被系统丢掉）之后，`combo` 会一直
        //  挂着，而下面「组合进行中」那一支会把之后**每一颗键**的按下都吞掉：整块键盘不再响应。
        //  两条判据：时长（与重复链那条 `COMBO_REPEAT_MAX_MS` 同一档）、以及**成员一颗都不在 [down] 里**
        //  （抬手全丢，这一组永远等不到收尾）。后一条不另存状态：正常的组合在第一个成员抬手时就已经
        //  清掉了（见 `onUp`），所以「一个都不在」只可能是丢事件。
        //  挂在按键入口上而不是单起一个定时器 —— 下一条按键事件本来就是自愈的时机，多一条定时器
        //  反而多一条要停的链。
        //  **两条都放在下面那条 re-DOWN 之前**：整表清空时，这一下自己的按下记录还没写进去。
        combo?.let { c ->
            val stuck =
                stuckComboLog(
                    keys = c.keys,
                    formedAt = c.times.formed,
                    now = now,
                    anyDown = c.keys.any { down.containsKey(it) },
                    //  在等更宽的一档时成员**可以**全部松手（顺序按下也算组合，见 [awaitWider]）——
                    //  那不是「抬手全丢」。判据合成放在被调方，这一层不添分叉。
                    waiting = c.waiting != null,
                )
            if (stuck != null) {
                XLog.w(stuck)
                //  **清之前把欠下的那一次动作还上**：「松手才触发」那一档到点只标了 `longFired`、
                //  动作押在抬手，抬手真丢时这一次按压是**零响应**（用户看到「这几颗键一起按没反应」）。
                //  兜底挂在这条既有自愈上，不另起定时器 —— 抬手丢失本来就要等下一次按键才救得回来。
                fireLostRelease(c)
                reset()
            }
        }
        //  **同一颗键再次按下、而它的上一次抬手没收到** ⇒ 上一次的 UP 丢了（锁屏打断、
        //  事件被系统丢掉那一类）。这是一条必须清的账：
        //   · 上一次可能正是一组组合 —— `combo` 不清就永远挂着，之后**每一颗键**的按下都走
        //     下面「组合进行中」那一支被吞掉，抬手也被 `consumed` 吞掉；
        //     表现就是「整块键盘像死了一样」，靠 [expireStuck] 的超时自愈（见那一条）；
        //   · 也可能只是单键 —— `consumed` 里留着它，那这一下的抬手会被误吞。
        //  正常输入不会对同一颗键连发两个 DOWN（重复按键带的是 REPEAT 标记，不走这里），
        //  所以这条判据不会误伤长按。
        if (down.put(id, now) != null) {
            XLog.w("组合键：${id.label} 又按下但没等到上一次抬手（UP 丢了）⇒ 只清这一颗的账重新算")
            //  只清**这一颗**：整表清空会把别的仍按着的键一起抹掉（组合长按要求成员都在 [down] 里，
            //  那一抹它们就再也按不出来了，而手指其实没松）。这一颗若还在某一组组合里，那一组也
            //  一并撤掉 —— 它缺了一条抬手，再派发就是替一次说不清的按压收尾。
            combo?.let { c ->
                if (id in c.keys) {
                    finishCombo(c)
                }
            }
            recent.remove(id)
            consumed.remove(id)
        }
        recent[id] = now
        pressSeq[id] = ++seqCounter
        //  「首键按下」报一声：这一颗若是某按住组合的起点，宿主会在这一刻把场景快照下来。
        //  包在 try 里 —— 这条跑在输入线程上，抛出去会把 system_server 带崩。
        try {
            onHoldStarter(id)
        } catch (e: Throwable) {
            XLog.w("组合键首键通知失败（忽略）：${e.javaClass.simpleName}", e)
        }
        // 已经在组合里：第三颗键按下来默认不掺和，仍按「组合进行中」吞掉。
        //  **记进 [consumed]**：不记的话它没有「按下」，抬手那条 UP 会被单键判定器当成
        //  「孤立抬手」补判一次单击 —— 组合进行中又冒出第三颗键的动作。
        //
        //  **例外是「这一下把更大的一档凑齐了」**（见 [absorb]）：两颗的子集组合总是先凑齐
        //  （第二颗按下那一刻就成立），不让位的话三颗那一档永远轮不到 —— 真机表现就是
        //  「三颗的组合怎么按都没反应」，而日志里只有子集那一档的行。
        //  另一条例外是「这一组只在等更宽的一档、而这一颗不属于它」（见 [absorb]）：那一组先收尾，
        //  这一颗继续走下面的正常判定。
        combo?.let { active ->
            //  `absorb` 答「这一颗归组合吗」：归就吞掉；不归说明那一组刚被它收尾并清了，接着判这一颗
            if (absorb(active, id, now)) return Down.TAKEN
        }
        val window = windowMs().takeIf { it > 0 } ?: FALLBACK_WINDOW_MS
        //  配对看 [recent]（含**刚松手**的那一颗）：顺序按下也算组合，见那张表的注释。
        //
        //  **候选要全过一遍、挑「真配得上」的那一颗**，不能只拿窗口内任意一颗去配：
        //  三颗键交错按（A → C → B，只有 A+B 配了组合）时 `ConcurrentHashMap` 的迭代序是散列序，
        //  可能先递给你 C —— 一配不上就整个落回单击，A+B 那一档直接落空（三颗各判各的）。
        //  多颗都配得上时按**按下时刻由近到远**取：最近按下的那颗与当前这一下最像同一次组合。
        //
        //  先按**两颗**配（老路径）：认出来就用它，结论与顺序按下那条语义完全不变。
        //  认不出再问**多成员**：窗口内这几颗 + 当前这一下正好凑齐某一档（3 颗起）。
        val hit =
            pairFor(id, now, window)
                ?: multiFor(id, now, window)
        // 没凑成：只有「参与组合的键」才值得先等一个窗口，其余立刻交回单键
        if (hit == null) return if (joinsCombo(id)) Down.HOLD else Down.PASS
        //  最早那一颗的按下时刻 = 成立这一刻 - 这一组的整串间隔（`Hit.spanMs` 就是它）
        val c = Combo(hit.key, hit.keys, Times(formed = now, firstDown = now - hit.spanMs))
        combo = c
        //  只有**还按着**的那几颗需要吞抬手：早松开的那颗不会再有 UP 到来，记进 [consumed]
        //  会一直留着 —— 下一次按它时那条正常抬手就被吞了（表现是「这颗键偶尔按了没反应」）
        hit.keys.filterTo(consumed) { down.containsKey(it) }
        for (k in hit.keys) {
            //  已经松手的那一颗这一下已经算进这档组合了，别再去配后面按下的键
            if (!down.containsKey(k)) recent.remove(k)
            //  先按下的那颗已经排上了长按枪，组合成立就把它摘掉 —— 否则组合期间会判出单键长按
            resetKey(k)
        }
        // 判据级：这一行是「组合键到底认没认出来」的唯一证据。
        //  窗口报的是**这一档自己的整串跨度**（不是扫候选那个数）：复核用的就是它，
        //  排查「差几毫秒没凑上」时要对得上。
        //
        //  **触发方式也要写出来**：窗口那一档与按住那两档共用上面这一句「同时按下」，光看它分不出
        //  「这一下走的是哪一种、按住那一档判的是谁先」—— 而按住那两档按的是谁的起点全看这个数。
        //
        //  「触发方式」那一段**拼在日志这一句里**、不先算成一个变量：这是一条判据日志（一次组合
        //  成立一条），它的拼接就该跟着这一句走 —— 拎出去单独算等于在按键判定链上多一份
        //  与日志无关的分配（`tools/audit-perf.py` 判据 2）。
        val label = { id: String? -> hit.keys.firstOrNull { it.id == id }?.label ?: (id ?: "?") }
        XLog.w(
            "组合键：" + hit.keys.joinToString(" + ") { it.label } + " 同时按下（" + hit.key.id + "）" +
                "（间隔 " + hit.spanMs + "ms，窗口 " + spanFor(hit.key) + "ms，触发=" +
                (if (hit.key.isWait) "等窗口" else "先按住 " + label(hit.key.starterKey)) +
                "）",
        )
        try {
            onComboFormed(hit.key)
        } catch (e: Throwable) {
            XLog.w("组合键凑成通知失败（忽略）：${e.javaClass.simpleName}", e)
        }
        scheduleLong(c, startedAt = now)
        //  还有更宽的一档来得及时（配了 {A,B} 又配了 {A,B,C}）⇒ 这一次先不派发，等它窗口结束
        awaitWider(c, now)
        return Down.TAKEN
    }

    /**
     * @param short `false` = 这一按被取消（`FLAG_CANCELED`，窗口失焦 / 来电打断那一类）：
     *   只做同一笔清理，**不派发**任何动作 —— 取消的含义就是「这一下不算数」，
     *   按普通抬手处理会让组合凭空短按一次。
     * @return true = 这一下被组合接管。
     *
     *  **没有 `now` 这个参数**：抬手这一侧只用得到「谁松了」，时长全在按下那一侧算
     *  （`onDown` 拿 `now` 排长按）。留一个收了不读的参数，签名就在撒谎。
     */
    fun onUp(
        id: KeyId,
        short: Boolean = true,
    ): Boolean {
        down.remove(id)
        //  组合已经收尾、首键这才抬手 ⇒ 补派它自己那次长按（见 [starterKeepPending]）。
        //  **放在 `consumed` 判断之前**：首键在组合凑成时被记进了 `consumed`，但它此刻的抬手
        //  要先把这笔欠账还上，再按「被组合接管」吞掉。
        //  取消（`short = false`）只清账不派发 —— 与组合收尾那条同一口径。
        val pending = starterKeepPending
        if (pending != null && pending.id == id.id) {
            starterKeepPending = null
            if (short) {
                XLog.w("组合键：首键 ${id.label} 松手，补派它自己的长按（组合已收尾）")
                binds.starterKeepLong?.fire(id)
            }
        }
        if (id !in consumed) return false
        consumed.remove(id)
        val c = combo
        if (c != null && id in c.keys) {
            //  还在等更宽的一档、这一组本身又什么都没派发过 ⇒ 抬手也先压着，收尾交给那一枪
            //  （见 [awaitWider]）。**长按已经派发过的不在此列**：那时等的意义已经没有了，
            //  「松手才触发 / 松手额外动作」得照老规矩立刻办。
            val waiting = c.waiting
            if (c.phase == ComboPhase.AWAIT_WIDER) {
                //  **取消**也不在此列：取消的含义是「这一下不算数」（窗口失焦 / 来电打断），
                //  留着那一枪会让它到点凭空短按一次。
                if (!short) {
                    XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 这一按被取消 ⇒ 不再等 ${waiting!!.key.id}")
                    finishCombo(c)
                    return true
                }
                XLog.w(
                    "组合键：${c.keys.joinToString(" + ") { it.label }} 先松开 ${id.label}，" +
                        "继续等 ${waiting!!.key.id}",
                )
                return true
            }
            // 组合结束：没判过长按 ⇒ 这一次是「一起短按」（取消的那一档不派发）
            if (short && !c.longFired) {
                XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 短按（先松开 ${id.label}）")
                fire(c, GestureTrigger.Click(1), repeat = false)
            } else if (short) {
                //  长按已经判出来过：这一下按那颗键自己的「松手」两档收尾（与单键同一条口径）——
                //  · 松手才触发：到点只标了状态，动作放到这一下执行一次；
                //  · 松手执行额外动作：按住期间已经执行过，这一下再补一条。
                if (c.key.longFireOnRelease) {
                    XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 长按（松手才触发）")
                    fire(c, GestureTrigger.LongPress, repeat = false)
                } else if (c.key.releaseBound) {
                    //  **三种触发方式都有这一档**（用户口径）：按住那两档的长按照样有动作，
                    //  抬手就是那一按的收尾，再补一条松手动作与单键同一套语义。
                    //  判据只看 `releaseBound`（它已经把「长按有动作 / 开关开着 / 挑过动作 /
                    //  不是松手才触发」四条算在一起），这里不再另判触发方式。
                    XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 长按松手（${c.key.longRelease.token}）")
                    fire(c, GestureTrigger.Release, repeat = false)
                }
            }
            //  「首键松手后仍执行它自己的长按」（`ComboKey.starterKeepLong`）——
            //  组合凑成时首键的长按枪被摘掉了（`resetKey`），原设计这一下是丢弃的。
            //
            //  **不论这一组有没有判过长按**（用户口径）：按住组合的常规按法是「首键一直按着、
            //  成员点一下就走」，那时 `longFired` 永远是 `false` —— 它挂在「长按已判出」那一支上
            //  等于这个开关从没生效过（真机反馈：「拨了没效果」）。
            //  取消（`short = false`）仍不补：那一下不算数。
            if (short) keepStarterLong(c, id)
            finishCombo(c)
        }
        // 组合的另一颗键可能还按着 —— 它的抬手同样要吞（`consumed` 里还留着它）
        return true
    }

    /**
     * 「首键松手后仍执行它自己的长按」—— 组合收尾时结算这一笔。
     *
     *  **不论松手顺序**（用户口径）：成员键想怎么按都可以，只要首键抬手就补一次它自己的
     *  长按。成员不用补 —— 它们有自己的单键状态机、自己会判长按。
     *  两条路径覆盖两种先后：
     *   · 松手的就是首键（首键先抬手）⇒ 当场补，见 [StarterKeepLong.fire]；
     *   · 首键还按着（典型：成员先松，这一组当场收尾）⇒ 记进 [starterKeepPending]，
     *     等首键抬手时在 [onUp] 开头补 —— 那时 `combo` 已经没了，只能靠这笔账。
     *
     *  **与 `longFired` 无关**：按住组合的常规按法是「首键按住、成员一点即走」，这一组
     *  从来等不到长按阈值（成员一松就收尾，长按枪被 [cancelShots] 撤掉）。早先它只挂在
     *  「长按已判出」那一支上，于是这个开关在真机上恒不生效。
     *
     * @param released 这一次抬手的是哪颗键
     */
    private fun keepStarterLong(
        c: Combo,
        released: KeyId,
    ) {
        //  四个出口各留一行：这一位是「拨了没效果」唯一抓得住的地方 —— 没有它，
        //  「开关关着」「没接上」「认不出首键」「首键已经不在按着」在这条链路上长得一模一样。
        val keep = binds.starterKeepLong
        if (keep == null) {
            XLog.w("组合键：${c.key.id} 首键长按这一档没接上（装配处没设 starterKeepLong）⇒ 不补")
            return
        }
        if (!keep.enabled(c.key)) {
            //  正常配置状态（这一位默认就是关），压到 d：开着的人在判定日志里看到的那两行
            //  才是判据，关着的人不该被每按一次刷一行 W。
            XLog.d {
                "组合键：${c.key.id} 的「首键松手后仍执行长按」是关的" +
                    "（独立设置=${c.key.useOwnSettings}，这一档写过=${c.key.starterKeepLong}）⇒ 不补"
            }
            return
        }
        //  窗口那一档没有首键（`starterKey` 恒为 null）⇒ 这一位对它不成立
        val starter = c.key.starterKey?.let { s -> c.keys.firstOrNull { it.id == s } }
        if (starter == null) {
            XLog.w(
                "组合键：${c.key.id} 认不出首键（starterKey=${c.key.starterKey}，" +
                    "成员=${c.keys.joinToString { it.id }}）⇒ 不补",
            )
            return
        }
        if (starter.id == released.id) {
            XLog.w(
                "组合键：${c.keys.joinToString(" + ") { it.label }} 首键 ${released.label} 松手，" +
                    "仍执行它自己的长按",
            )
            keep.fire(starter)
        } else if (down.containsKey(starter)) {
            XLog.w(
                "组合键：${c.keys.joinToString(" + ") { it.label }} ${released.label} 松手收尾，" +
                    "首键 ${starter.label} 还按着 ⇒ 它抬手时补派自己的长按",
            )
            starterKeepPending = starter
        } else {
            XLog.w(
                "组合键：${c.keys.joinToString(" + ") { it.label }} ${released.label} 松手时" +
                    "首键 ${starter.label} 已经不在按着的表里 ⇒ 没人可补（它的抬手先到了？）",
            )
        }
    }

    /**
     * 清空（配置变化 / 主开关关掉）。
     *
     *  与 `SideKeyInterceptor.cancelPending` 同一处调用：关掉某颗键时，它按住不放的那一下
     *  不能一路走到长按。
     */
    fun reset() {
        combo?.let(::finishCombo)
        //  这一笔欠账跟着配置一起作废：关掉主开关 / 改了配置之后，那颗键的抬手不该再派旧动作
        starterKeepPending = null
        down.clear()
        recent.clear()
        pressSeq.clear()
        consumed.clear()
    }

    /**
     * 排长按那一枪。
     *
     * @param startedAt 第二颗键按下的时刻（组合成立的时刻）—— 阈值从这一刻开始量，
     *   而不是从第一颗键按下的时刻：两颗键可能相差 80ms，按第一颗算会让长按早到最多 80ms。
     */
    private fun scheduleLong(
        c: Combo,
        startedAt: Long,
    ) {
        //  **这一档没有长按这一档**（按顺序触发的等待组合，判据见 `ComboKey.hasLong`）：不排枪。
        //  排了它也会到点判出「长按」并把 `longFired` 置上，抬手那条路于是不再按「一起短按」派发 ——
        //  而那一档在界面上根本配不出长按动作，结果就是「什么都没发生」。
        if (!c.key.hasLong) return
        val shot =
            Runnable {
                try {
                    if (combo !== c || c.longFired) return@Runnable
                    c.shot = null
                    // 两颗键还都按着才算「一起按住不放」
                    if (!c.keys.all { down.containsKey(it) }) return@Runnable
                    c.longFired = true
                    XLog.w(
                        "组合键：${c.keys.joinToString(" + ") { it.label }} 长按" +
                            "（${SystemClock.elapsedRealtime() - startedAt}ms，阈值 ${c.key.longPressMs}ms）",
                    )
                    //  「松手才触发」这一档到点**不动手**：动作放到抬手那一下执行（见 `onUp`）。
                    if (!c.key.longFireOnRelease) fire(c, GestureTrigger.LongPress, repeat = false)
                    // 「长按连续触发」：两颗键一起按住不放就一直重来。
                    //  串行命令（`cmd:` 指多条命令）与灵感语音那两档不连发 —— 与单键那条同一条判据，
                    //  但组合这边**不做这两档的过滤**：组合动作里串行命令重复重放与单键一样没意义，
                    //  而判定侧读不到命令库（`longIsSerial` 只在单键广播里由发送侧现算）。
                    //  「松手才触发」时不连发：动作要等到抬手才执行，按住期间重放等于把同一件事
                    //  排了几十遍（与单键那三条时序的互斥同一条口径）。
                    if (c.key.longRepeat && !c.key.longFireOnRelease) scheduleRepeat(c)
                } catch (e: Throwable) {
                    // 这一枪跑在 system_server 主线程上，抛出去就是整机崩
                    XLog.w("组合键长按判定失败（忽略）：${e.javaClass.simpleName}", e)
                }
            }
        c.shot = shot
        handler.postDelayed(shot, c.key.longPressMs.toLong())
    }

    /**
     * 抬手丢了之后把「松手才触发」欠下的那一次动作交出去 —— 只对已判过长按、且这一档确实押在
     * 抬手的那一组生效。
     *
     *  [scheduleLong] 到点只置 `longFired`、不派发（见 `KeyConfig.longFireOnRelease`），动作全在
     *  [onUp] 那一支；抬手丢了就没人执行它。自愈（[onDown] 开头两条判据）本来就要清掉这一组，
     *  清之前顺手补一次 —— 与单键那一侧 `GestureDetector` 的松手兜底同一条口径。
     *
     *  只补一次、不连发：连发与「松手才触发」互斥（见 [scheduleLong] 里那条判据）。
     */
    private fun fireLostRelease(c: Combo) {
        if (!c.longFired || !c.key.longFireOnRelease) return
        XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 长按（松手才触发，但抬手已丢 ⇒ 兜底执行一次）")
        fire(c, GestureTrigger.LongPress, repeat = false)
    }

    /**
     * 把这一组**全部**定时链都撤掉：长按那一枪、连续触发那一串、等更宽那一档的那一枪。
     *
     *  三处调用点（被另一组顶掉 / 抬手 / 重置）一直是成对调用这几条 —— 合成一支，
     *  就不会出现「只撤了一条、另一条还在跑」那种只在一台机器上偶尔复现的怪现象：
     *  那一枪到点会按「这一组还没派发过」把动作补出来，于是同一按执行两次。
     */
    private fun cancelShots(c: Combo) {
        c.shot?.let { handler.removeCallbacks(it) }
        c.shot = null
        c.repeatShot?.let { handler.removeCallbacks(it) }
        c.repeatShot = null
        c.waiting?.let { waiting ->
            handler.removeCallbacks(waiting.shot)
            c.waiting = null
        }
    }

    /**
     * 收掉当前这一组、回到空闲 —— [cancelShots] 撤枪 + 清 [combo] 两件事**成对**收在这里。
     *
     *  早先这两个动作在 [absorb] / [onUp] / [awaitWider] / [onDown] 的自愈与重复键分支里
     *  各写一遍（`cancelShots(c); combo = null`），一共六处 —— 漏掉「撤枪」那一半时，
     *  定时链到点会把已经收尾的组合再补派一次（同一按执行两次）。收成一支之后，
     *  「收尾」只有一个入口，不会只撤一半。
     *
     *  不管 [combo] 现在是不是 [c]（可能已经被别处换掉 / 收掉）：调用点已经判断过归属，
     *  这里只做「撤这一组的枪、把当前组合清掉」这一件事。
     */
    private fun finishCombo(c: Combo) {
        cancelShots(c)
        combo = null
    }

    /**
     * 「长按连续触发」的重复派发 —— 与单键那条（`GestureDetector.scheduleRepeat`）同一个形状：
     * 每 [ComboKey.longRepeatIntervalMs] 派一次，抬手 / 重置 / 超过上限时停。
     *
     *  上限兜底是必需的：抬手事件丢了（锁屏打断、非 CANCELED 的丢弃）时没有任何回调会停这条链，
     *  而它每 100ms 自循环派发一次动作 —— 那是用户看不见的连环触发。
     */
    private fun scheduleRepeat(c: Combo) {
        val interval =
            c.key.longRepeatIntervalMs
                .coerceAtLeast(KeyConfig.MIN_REPEAT_INTERVAL_MS)
                .toLong()
        val shot =
            Runnable {
                try {
                    if (combo !== c || !c.longFired || c.keys.any { !down.containsKey(it) }) {
                        c.repeatShot = null
                        return@Runnable
                    }
                    if (SystemClock.elapsedRealtime() - c.times.formed > COMBO_REPEAT_MAX_MS) {
                        XLog.w(
                            "组合键：${c.keys.joinToString(" + ") { it.label }} 连续触发超过" +
                                " ${COMBO_REPEAT_MAX_MS}ms 未见抬手，强制停止（UP 可能已丢）",
                        )
                        c.repeatShot = null
                        return@Runnable
                    }
                    fire(c, GestureTrigger.LongPress, repeat = true)
                    scheduleRepeat(c)
                } catch (e: Throwable) {
                    XLog.w("组合键连续触发失败（已停止）：${e.javaClass.simpleName}", e)
                }
            }
        c.repeatShot = shot
        handler.postDelayed(shot, interval)
    }

    private fun fire(
        c: Combo,
        trigger: GestureTrigger,
        repeat: Boolean,
    ) {
        try {
            onCombo(c.key, trigger, repeat)
        } catch (e: Throwable) {
            // 输入线程上抛异常会把 system_server 带崩
            XLog.w("组合键派发失败（已忽略）：${e.javaClass.simpleName}", e)
        }
    }

    /**
     * 一次**凑成**：哪一档、真要算成员的那几颗、以及「从最早那一颗到这一下」隔了多久（只进日志）。
     *
     *  [keys] 不一定是候选全体：多成员那条路把窗口内按下的所有键一起递给绑定闸
     *  （`Config.comboFor` 只要求成员都在），夹在中间的不相干的键不算成员 —— 拿候选全体去摘
     *  单键状态机，会把一颗本来该自己响的键也一起吞掉。
     */
    private class Hit(
        val key: ComboKey,
        val keys: List<KeyId>,
        val spanMs: Long,
    )

    /**
     * 3 颗以上的组合：窗口内按下的这一组里，是不是正好凑齐了某一档（[MULTI_MIN_KEYS] 起）。
     *
     *  与 [pairFor] 的分工见类注释。**跨度按成员数放大**：固定窗口起点关着时，两颗那条判的是
     *  「这两颗相差不超过一个窗口」，n 颗要用同一条判据就得让整串（最早那一颗到当前这一下）
     *  不超过 (n-1) 个窗口 —— [spanFor]。夹在中间的不相干的键不作废这一档，但它也不算成员。
     *
     * ## 先数、后建表
     *
     *  第一趟只数不建表：窗口内凑不够 3 颗就直接收工。参与**两颗**组合的键每次按下都会走到
     *  这里（[pairFor] 没配上就是这一条），那才是常态 —— 让它每次按压分配一个列表说不过去。
     *  最外层还有一道 `joinsCombo`：不参与任何组合的键连数都不数。
     *
     * @param now 这一下的时刻
     * @param window 扫候选用的窗口（最宽那一档）
     */
    private fun multiFor(
        id: KeyId,
        now: Long,
        window: Long,
    ): Hit? {
        //  不归任何组合管的键不可能是多成员组合的成员 —— 这一位比扫一遍候选便宜得多。
        //  **用 `inAnyCombo` 而不是 `joinsCombo`**：后者的语义是「按下后要压一个窗口」（只有
        //  等待档要），拿它挡会把按住档的后续成员整条挡在门外。
        if (!inAnyCombo(id)) return null
        //  候选 = 这一下 + 窗口内按下过的（含刚松手的：顺序按下也算凑齐，见 [recent] 的注释）
        //        + **仍按着的**（按住档的首键可能已经按了很久，早就不在窗口里了）
        var count = 1
        for ((key, at) in recent.entries) {
            if (key != id && (now - at <= window || down.containsKey(key))) count++
        }
        if (count < MULTI_MIN_KEYS) return null
        val pool = arrayListOf(id)
        for ((key, at) in recent.entries) {
            if (key != id && (now - at <= window || down.containsKey(key))) pool.add(key)
        }
        //  「成员多的在前」「同数按配置顺序」都在 `Config.combosFor` 里，这一层不重复判。
        //  比 [MULTI_MIN_KEYS] 小的结果不接：那说明这是一档**两颗**的组合，本该由 [pairFor]
        //  认出来（它带着「按下时刻最近」的挑法），走到这里还认成两颗只会挑错成员。
        //
        //  **两类都认**（等待档 + 按住档）：按住那一档不再是「只两颗」——首键一颗、后续任意多颗。
        //  按顺序触发那一档：pool 是散列序，按按下序号排出真实顺序再逐位比。
        //  有序档不匹配的整档排除，不分顺序档照旧留下。
        val orderSeq = pressOrder(pool)
        val hit =
            orderMatch(bindingsFor(pool), orderSeq).firstOrNull { c ->
                c.keys.size >= MULTI_MIN_KEYS && (c.isWait || holdReady(c, pool, now))
            } ?: return null
        //  成员按**命中那一档自己的清单**取：候选里夹的别的键不算成员（它们还得自己走单键判定）
        val members = List(hit.keys.size) { i -> pool.first { it.id == hit.keys[i] } }
        //  间隔按**成员**里最早那一颗算（候选里可能夹着不相干的键，那是别人的按下时刻）。
        //  取 `minOf` 而不是 `members.first()`：成员顺序是**配置里的键序**，与真实按下顺序
        //  不一定一致（用户先按配置里的第二颗）。拿配置首键算，跨度会被算小，超过这一档
        //  自己窗口的组合也照样认下 —— 误触发，且这几颗键的单击 / 长按被一起吞掉。
        val span = now - members.minOf { recent[it] ?: now }
        if (hit.isHold) {
            //  按住档：**首键按多久都行**（下限由 [holdReady] 管），但后续成员每颗都得是
            //  窗口内刚落下的。那个窗口是四格里的一格：固定窗口起点开着用 `hold`（从首个成员
            //  按下起算一个），关着用 `holdFollower`（每颗各自往前一个）。
            val w = binds.windowsFor(hit)
            val followers = members.drop(1)
            val fresh =
                if (binds.holdFixedWindowStartFor(hit)) {
                    val firstAt = followers.firstOrNull()?.let { recent[it] } ?: now
                    now - firstAt <= (w?.hold?.toLong() ?: HOLD_FOLLOWER_WINDOW_MS)
                } else {
                    val per = w?.holdFollower?.toLong() ?: HOLD_FOLLOWER_WINDOW_MS
                    followers.all { (now - (recent[it] ?: now)) <= per }
                }
            return Hit(hit, members, span).takeIf { fresh }
        }
        //  这一档自己的**整串跨度**复核：扫的时候按最宽的那一档扫，算不算数由它自己说了算
        //  （不成立就整条作废，所以是 `takeIf` 而不是再补一个 `return null`）。
        //  逐次等满那一档的容忍跨度是 (n-1) 个窗口，见 `Config.comboSpanFor`。
        return Hit(hit, members, span).takeIf { span <= spanFor(hit) }
    }

    /**
     * 按住那一档的**首键条件**成立吗（成员是否齐全由调用方先按 `bindingsFor` 筛过）。
     *
     *  三条：首键**还按着**、按满 [holdMinFor]、且它的长按**还没判出来**（判过就已经执行过
     *  自己的动作，再认组合就是一次按压两个动作）。与 `pairFor` 里那颗「先前按下的键」用的是
     *  同一套判据 —— 两颗与多颗的分叉只在「成员怎么凑齐」，不在「首键合不合格」。
     */
    private fun holdReady(
        c: ComboKey,
        pool: List<KeyId>,
        now: Long,
    ): Boolean {
        val starterId = pool.firstOrNull { it.id == c.starterKey } ?: return false
        val at = recent[starterId] ?: return false
        return down.containsKey(starterId) &&
            now - at >= holdMinFor(c) &&
            !longPressPassed(starterId, at, now)
    }

    /**
     * [pool] 里这几颗键按**按下序号**排出的真实顺序（id 序列）—— 只给「按顺序触发」那档用。
     *
     *  序号查不到的那颗（不太可能：池里的键都在 `recent` 里，也就都在 `pressSeq` 里）排在末尾，
     *  兜住「读不到序号」的边角 —— 那它基本不会命中任何有序档，但不会崩。
     */
    private fun pressOrder(pool: List<KeyId>): List<String> = pool.sortedBy { pressSeq[it] ?: Int.MAX_VALUE }.map { it.id }

    /**
     * 这几档里，[sequence] 这一**真实按下顺序**能命中的有序档（含不分顺序档）—— 见 [pressOrder]。
     *
     *  有序档（`ComboKey.ordered`）用 [ComboKey.matchesOrdered] 按序比；不分顺序档本就不看顺序，
     *  照旧留下。两者合成一张候选，供 `pairFor` / `multiFor` 按触发方式逐档挑。
     */
    private fun orderMatch(
        candidates: List<ComboKey>,
        sequence: List<String>,
    ): List<ComboKey> =
        if (candidates.none { it.ordered }) {
            candidates
        } else {
            candidates.filter { !it.ordered || it.matchesOrdered(sequence) }
        }

    /**
     * 在窗口内挑一颗「配得上、且按下时刻最新」的同伴 —— 配上了就是这一档组合。
     *
     *  写成**一趟扫描**而不是 `filter { } .sortedByDescending { } .firstNotNullOfOrNull { }`：
     *  这是每次按下都要走的一段（参与组合的键全都从这儿过），三段流水线一次按压要建两个
     *  中间列表 + 三个 lambda；而候选本来只有窗口内那几颗（常态一两颗），排序毫无意义。
     *
     *  取法与原来逐条相同：配得上（`bindingsFor` 非空）的候选里取 `at` 最大的一颗；
     *  `at` 相同时保留**先遇到**的那一颗（原来稳定排序后取第一个，也是先遇到的那一颗）。
     *
     * @param now 这一下的时刻
     * @param window 扫候选用的窗口（最宽那一档）
     */
    private fun pairFor(
        id: KeyId,
        now: Long,
        window: Long,
    ): Hit? {
        var best: Hit? = null
        var bestAt = 0L
        for ((key, at) in recent.entries) {
            if (key == id) continue
            val span = now - at
            //  两条判据各自的前提先掂一下，够不着就别问 `bindingsFor`：
            //  · 窗口那条要求 `span <= window`；
            //  · 按住那条要求那一颗**还按着**（`down`）—— 已经松手的一律不算，理由见下面 `held`。
            //
            //  这一层不是省事的写法，是**按键判定链上唯一一处按候选数倍增的分配点**：
            //  `bindingsFor` 每次要走一遍「全归模块管」的判定（建一个 id 集合）再扫一遍组合表
            //  （建一个中间列表）。而 [recent] 只在组合成立 / 同一颗键重复按下时才减项，
            //  正常用起来会攒下最近按过的那几颗 —— 不先掂前提，一次按压就要问它好几遍。
            val stillDown = down.containsKey(key)
            if (!stillDown && span > window) continue
            val candidates = bindingsFor(listOf(key, id))
            if (candidates.isEmpty()) continue
            //  按顺序触发那一档：这一对里 `key` 是先按的、`id` 是当前的，真实顺序就是 `key → id`。
            //  有序档按它逐位比（不匹配的整档排除），不分顺序档照旧留下。
            val ordered = orderMatch(candidates, listOf(key.id, id.id))
            if (ordered.isEmpty()) continue
            //  这一对的判据**按每一档自己的触发方式选**，逐档试 —— 同一组键可以配几档，
            //  各自一种方式（`ComboKey.mode`），只看成员数最大的那一档会把另一种方式整条遮掉。
            //  · 等窗口：两颗的按下相差不超过这一档**自己的跨度**（扫按最宽的、判按它自己的）；
            //  · 先按住某颗：先按的那颗**还按着**、且它正是这一档的起点（`starterKey`）、
            //    按住够久（`holdMinFor`）、它的长按还没判出来（判过就已经执行过自己的动作了）。
            //
            //  **按住那几档优先**：两档同时命中时（那一颗既在窗口内、又按满了按住下限）取按住那档 ——
            //  它的前提更严（要求起点键先按着且过了下限），是更明确的表态；等窗口那档只要有第二颗
            //  在窗口内落下就成立，留给「没按住那么久」的情形。
            var hitKey: ComboKey? = null
            for (b in ordered) {
                val ok =
                    if (b.isWait) {
                        span <= window && span <= spanFor(b)
                    } else {
                        stillDown &&
                            span >= holdMinFor(b) &&
                            b.starterKey == key.id &&
                            !longPressPassed(key, at, now)
                    }
                if (ok) {
                    hitKey = b
                    if (!b.isWait) break
                }
            }
            val bound = hitKey ?: continue
            val current = best
            if (current == null || at > bestAt) {
                best = Hit(bound, listOf(key, id), span)
                bestAt = at
            }
        }
        return best
    }

    /**
     * 挂住的组合要不要自愈 —— 返回该打的那一行，`null` = 不用管。
     *
     *  两条判据：时长（与重复链那条 [COMBO_REPEAT_MAX_MS] 同一档）、以及**成员一颗都不在**
     *  [down] 里（抬手全丢，这一组永远等不到收尾）。后一条不另存状态：正常的组合在第一个成员
     *  抬手时就已经清掉了（见 [onUp]），所以「一个都不在」只可能是丢事件。
     *
     * @param anyDown 这几颗成员里还有按着的吗
     * @param waiting 这一组正在等更宽的那一档 —— 那种状态下成员本来就可以全部松手（顺序按下也算组合），
     *   只有超时那一条还有意义
     * @param formedAt 这一组成立的时刻（超时从它量）
     */
    private fun stuckComboLog(
        keys: List<KeyId>,
        formedAt: Long,
        now: Long,
        anyDown: Boolean,
        waiting: Boolean,
    ): String? {
        val tooLong = now - formedAt > COMBO_REPEAT_MAX_MS
        //  「还按着」与「在等更宽的一档」都算正常状态 —— 两者都不成立才是抬手丢了。
        if (!tooLong && (anyDown || waiting)) return null
        return if (tooLong) {
            "组合键：${keys.joinToString(" + ") { it.label }} 挂住超过 ${COMBO_REPEAT_MAX_MS}ms" +
                "（抬手可能已丢）⇒ 清空组合状态"
        } else {
            "组合键：${keys.joinToString(" + ") { it.label }} 的成员一个都没按着" +
                "（抬手可能全丢）⇒ 清空组合状态"
        }
    }
}

/**
 * 一次按键按下 —— 判定「凑没凑齐」只要这两样：哪颗键、什么时候。
 *
 *  `atMs` 的基准由采集点给，判据只比较**同一条流内**的差值（与 [RawInput] 同一条纪律）。
 */
internal data class ComboDown(
    val keyId: String,
    val atMs: Long,
)

/** 「这一串按下凑成了什么」—— 只回答形态，不含动作、不含长按（那要抬手时刻，见 [ComboSequence]）。 */
internal sealed interface ComboGather {
    /** 凑齐了。`atMs` 是最后一颗到齐的时刻 —— 长按从这一刻起算。 */
    data class Gathered(
        val plan: ComboPlan,
        val atMs: Long,
    ) : ComboGather

    /** 还差几颗，但这条路仍然成立（等下一颗）。 */
    data class Pending(
        val plan: ComboPlan,
        val missing: Int,
        val lastAtMs: Long,
    ) : ComboGather

    /** 一条路都不成立。 */
    data object None : ComboGather
}

/**
 * 「这一串依次按下」算不算某一档组合 —— **纯判据**，无状态、不读配置、不碰事件流。
 *
 * ## 与判定器是同一套规则的两种形态
 *
 *  `ComboDetector` 是**事件状态机**：它管吞键、管退回单键判定、管长按计时与连续触发 ——
 *  那一套必须长在事件流上。同一套「凑齐没有」的规则在这里写成**无状态的纯函数**，用途只有一个：
 *  让 `probe/combo` 在不造假事件流的前提下把规则跑一遍（真机上一次按压只有一条证据，
 *  判错了没人能重放）。
 *
 *  **改动必须两边同改**：判断器那边是 `ComboDetector.pairFor`（两颗）与 `multiFor`（3 颗起），
 *  这里是它的镜像。两边口径差一条，探针就会在「真机坏了」的时候报正常 —— 那比没有探针更糟。
 *
 * ## 判据（与判定器逐条对齐）
 *
 *  1. **顺序按下也算凑齐**（现有语义：几颗的 DOWN 相差不超过窗口，不要求同时按着）；
 *  2. 窗口按**最后一下**算：每一颗与最后那一颗相差不超过这一档的整串跨度 —— 固定窗口起点
 *     开着是一个等待窗口，关着按相邻两颗放大成 (n-1) 个（判定器那边同一个数，
 *     见 `ComboDetector.spanFor` / `Config.comboSpanFor`）；
 *  3. 这一串里每颗都必须是该档成员，**夹在中间的非成员键不影响结果**（对应判定器里
 *     「配不上的候选不占位」；早先写成「作废」是错的：那会让「A → 蹭到 C → B」这种
 *     真实按法整个落回单击）；**同一颗按了两次**按最后那一次算（判定器的 `recent` 只留
 *     最后一条，它的 `down` 认出「上一次的 UP 丢了」时还会整块重置重算）；
 *  4. 几档同时可能时，**成员多的赢**（与 `Config.comboFor` 的 `maxByOrNull { keys.size }` 一致）；
 *     成员数相同取先凑齐的那一档，再相同按 id 定序 —— 不靠列表顺序，配置重排不该改结果。
 *  5. 这一层答的是「最终凑成哪一档」，**不含时序**：判定器那边「已经凑成小的一档、但更宽的一档
 *     还来不及时先不派发」（见 `ComboDetector.awaitWider`）是何时**派发**的规则 —— 更宽那一档
 *     到最后也没凑齐时，结果仍是这里答的那一档。
 */
internal object ComboSequence {
    /**
     * @param downs 这一串按下，按发生顺序
     * @param plans 候选（调用方**先滤掉判定器认不出的形态**：`judgeable == false` 的那几档）
     */
    fun resolve(
        downs: List<ComboDown>,
        plans: List<ComboPlan>,
    ): ComboGather {
        if (downs.isEmpty() || plans.isEmpty()) return ComboGather.None
        val results = plans.mapNotNull { gather(it, downs) }
        //  **先看有没有真凑齐的**：凑齐比「还差几颗」优先，否则一颗键的按下会盖住已经成立的组合
        results
            .filterIsInstance<ComboGather.Gathered>()
            .minWithOrNull(
                compareByDescending<ComboGather.Gathered> { it.plan.size }
                    .thenBy { it.atMs }
                    .thenBy { it.plan.id },
            )?.let { return it }
        return results
            .filterIsInstance<ComboGather.Pending>()
            .minWithOrNull(
                compareByDescending<ComboGather.Pending> { it.plan.size }
                    .thenBy { it.plan.id },
            ) ?: ComboGather.None
    }

    /**
     * 单档的判定：这一串按下对它来说是「凑齐 / 还差几颗 / 此路不通」。
     *
     *  一条路不成立时返回 `null`（而不是 `None`）—— 多档并行判断时，`None` 只有「全都不成立」才算。
     */
    private fun gather(
        plan: ComboPlan,
        downs: List<ComboDown>,
    ): ComboGather? {
        //  一颗不叫组合：单成员的「组合」配得出来也没有判定意义（下限是 [MIN_SIZE]）
        if (plan.size < MIN_SIZE) return null
        //  只看属于这一档的那些按下 —— 夹在中间的非成员键不影响结果（判据 3）。
        //  同一颗按了两次按**最后那一次**算（`associateBy` 留最后一条），与判定器 `recent` 同一条。
        val hits = downs.filter { it.keyId in plan.keys }.associateBy { it.keyId }.values
        val first = hits.minOfOrNull { it.atMs } ?: return null
        val last = hits.maxOf { it.atMs }
        //  窗口按最后一下算：每一颗与最后那一颗相差不超过这一档的**整串跨度**（判据 2）。
        //  跨度已经由 `Config.comboSpanFor` 按成员数与固定窗口起点展开过，这里不再乘。
        if (last - first > plan.spanMs) return null
        return if (hits.size >= plan.size) {
            ComboGather.Gathered(plan, last)
        } else {
            ComboGather.Pending(plan, plan.size - hits.size, last)
        }
    }

    /** 一颗不叫组合 —— 与 `ComboKey.MIN_KEYS` 同一个意思，判据这一层自己留一份。 */
    private const val MIN_SIZE = 2

    /** 自检用：把结论打成人话（诊断通道与将来的日志都用它，别各写一份）。 */
    fun describe(gather: ComboGather): String =
        when (gather) {
            is ComboGather.Gathered -> "凑齐 ${gather.plan.id}（${gather.plan.size} 颗，到齐于 ${gather.atMs}ms）"
            is ComboGather.Pending -> "还差 ${gather.missing} 颗（${gather.plan.id}）"
            ComboGather.None -> "没有凑成任何一档"
        }
}
