package com.lookie.opluskey.inject.input

import android.os.Handler
import android.os.SystemClock
import com.lookie.opluskey.core.log.XLog
import com.lookie.opluskey.core.model.ComboKey
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
 * 组合键的判定 —— **几颗已接管的键**在窗口内先后按下，就当作一次组合，不再各自判单击 / 长按。
 *
 *  判出组合之后执行什么，由装配方决定（`onCombo`）：这里只回答「哪一档 + 短按还是长按」。
 *
 *  与 [GestureDetector] 的分工：那个判单颗键的手势，这个只判「几颗键的重叠」。一旦认出组合，
 *  立刻把这几颗键的单键状态机**摘掉**（[resetKey]），否则先按下的那颗会在组合期间判出长按 ——
 *  一次按键执行两个动作。
 *
 * ## 先问 [bindingsFor]
 *
 *  组合成立就会摘掉成员键的单键状态机。没有绑定还照样摘，用户看到的就是「几键同按什么反应都没有」——
 *  而这正是最容易被当成「模块坏了」的那种表现。所以**先判有没有这一档，再动任何状态**：
 *  查不到就把这一下原样交回单键判定器，那几颗键各走各的。
 *
 * ## 顺序按下也算组合（组合的等待压过单键的单击）
 *
 *  判据只是「几颗键的 DOWN 相差不超过窗口」，**不要求同时按着**：先按 A、松手、紧接着按 B
 *  同样算这一档组合。代价面在单键那一侧 —— 参与组合的键按下后要**等满一个窗口**才进单键判定器
 *  （见 `HeldPresses`），所以「A 的单击」不会在 B 到来之前先跑掉。用户口径：先后按几颗组合键，
 *  只该触发组合，不该每颗各来一次。
 *
 *  **这一整条只服务「等窗口」那一档**（`ComboKey.MODE_WAIT`，也就是默认那一档）：
 *  按住那两档的成员根本不等窗口（它们的判据在下一节），所以也不会被压一拍。
 *
 *  **扫**候选时相对当前这一下算（`now - at <= window`）：几颗键之间夹了一颗不相干的键时，
 *  这一档照样成立。**算不算数**则由这一档自己的跨度判 —— 「相邻两颗最多隔一个窗口」
 *  ⇔「最早那一颗到当前这一下最多隔 (n-1) 个窗口」（[spanFor]）。
 *  三颗套用两颗那一个窗口会变成「三根手指必须同时落」，真机上等于永远凑不齐。
 *
 * ## 「先按住某颗」那一档不看窗口，但**要真的按住**
 *
 *  那两档（`MODE_HOLD_FIRST` / `MODE_HOLD_SECOND`）的判据是「**起点键还按着**」（读 `down`），
 *  不拿时间量间隔 —— 按住三秒再补第二颗照样成立。
 *
 *  起点是谁由这一档**明写**（`ComboKey.starterKey`），不由物理先后推：两颗键几乎同时落下时
 *  谁先上报看硬件，推出来的顺序会摇摆；而按住那一档本来就是「我特意先按这颗」的表态。
 *
 *  但**反过来有一条下限**（`holdMinFor`，每档可设）：起点要按满[这么久]才算「按住」——
 *  两颗键几乎同时落下（实测相差 0~40ms）不叫按住，那种「一起按」只归窗口那条。
 *  没有这条下限，一起按会按物理先后落进某一种顺序，于是同一个物理动作有时命中、有时整条落空。
 *
 *  上界由长按接管（[longPressPassed]）：那颗键的长按一旦判出来，它已经执行过自己的动作了，
 *  再认组合就是一次按压两个动作。
 *
 *  **「松手时触发」的键没有这条上界**：那一档到点并不执行（动作押在抬手），所以「已经执行过」不成立
 *  —— 按住手势要的正是「按住久一点再补一颗」。凑成之后那颗键的状态机被摘掉，押着的那次长按跟着没。
 *
 *  这条与「只参与组合」（`KeyConfig.comboOnly`）是两件事：那个说的是「这颗键不判单键」，
 *  这个说的是「按住了多久都还算组合的第一颗」。
 *
 *  长按是另一条判据：必须成员**都还按着**（读 [down]）—— 先松开一颗就凑不出「一起按住不放」。
 *
 * ## 两颗走配对，3 颗起走凑齐
 *
 *  [pairFor]：窗口内挑一颗「配得上、且按下时刻最新」的同伴，两颗成一组 —— 这条是老路径，
 *  「按下时刻由近到远」只在两颗这一档有意义（三颗以上的候选里挑谁都不是唯一的）。
 *
 *  [multiFor]：把窗口内按下的那一组**整体**递给绑定闸，「成员多的赢」由 `Config.comboFor`
 *  判（`maxByOrNull { keys.size }`）。成员表按**命中那一档自己的清单**取，候选里夹的别的键
 *  不算成员（它们还得自己走单键判定）。
 *
 *  界面现在能造三颗（`ComboKey.MAX_KEYS`），四颗以上仍只对手改配置生效 —— 判定、执行、
 *  日志三处都是同一条真链路，不是旁路。判定侧这一层的判据全部**按成员集算**、不认「两颗 / 三颗」
 *  这种数字：多成员那条从 3 颗起（[MULTI_MIN_KEYS]），「等更宽的一档」按**真子集**判，
 *  整串跨度按成员的颗数放大 —— 四颗、五颗走的是同一条路。
 *
 * ## 组合进行中再来的按键只出一组
 *
 *  组合进行中再来的按键整条吞掉（按下 + 抬手），所以「几颗里有两组组合」也只出**先凑成的那一组**。
 *
 *  **唯一的例外是「更大的一档被这一下凑齐了」**（[absorb]）：子集那一档总是先成立，
 *  不让位的话更大的一档永远轮不到 —— 那一档换成更大的、抬手时派发的是它，仍然只出一组。
 *  **另一条例外**是「这一组只在等更宽的一档、而成员已经松过手」（[absorb]）：那一组先收尾派发，
 *  这一颗走正常判定 —— 否则「刚按完 A + B，紧接着单独按 A」会整颗失灵到窗口结束。
 *
 * ## 更宽的一档还来得及 ⇒ 先不派发
 *
 *  【{A,B} 与 {A,B,C} 同时配着，且 {A,B,C} 的窗口被调宽】小的一凑齐就派发，等于大那一档永远
 *  只剩「所有手指同时落」才成立 —— 用户把大那一档的等待时间拉长，手感上却一点没变（先松手的
 *  那颗已经把小的那一档的动作用掉了）。所以凑成之后先问一句「还有更宽的一档吗」：
 *
 *  · 有，且**它整串的跨度还没用完**（从最早那一颗算，[spanFor]）⇒ [awaitWider]
 *    记下这一档、把派发压到那个时刻；
 *  · 期间来了它缺的那颗 ⇒ [absorb] 换成更大的一档（换过去之后接着等更宽的那一档，
 *    于是 `{A,B}` ⊂ `{A,B,C}` ⊂ `{A,B,C,D}` 能一级一级往上走）；
 *  · 到点还没凑成 ⇒ 才派发小那一档（[awaitWider] 里那一枪）；
 *  · 期间成员还都按着 ⇒ 来的按键照老规矩吞掉；**已经松过手** ⇒ 这一组是「待派发」状态，
 *    先派发它、再让新按下的那颗走正常判定（[absorb]）。
 *
 *  等多久只按更宽那一档算，**与这一档自己的窗口无关**：等的是「下一颗还来不来得及」，
 *  而不是「这几颗算不算同时按」。
 *
 *  有一条**不**跟着等：长按。这一档按住超过自己的阈值就照旧派发（[scheduleLong]），
 *  之后也不再升级 —— 长按本身就是「按住不放」的明确表态，到那一刻就不该再等下一颗。
 *
 * ## 长按阈值取组合自己的
 *
 *  `ComboKey.longPressMs` 是这一档自己的（默认 500ms，与单键默认值同档）。不用某颗成员键的阈值：
 *  两颗键可能各配一个值，取谁的都是任选其一；而「一起按住多久算长按」是这一档的语义。
 *
 * 线程：与 [GestureDetector] 同一条输入线程（DOWN/UP 都在它上面进来），长按那一枪走
 * [handler]（main looper）。跨线程的字段一律 `@Volatile`。
 */
internal class ComboDetector(
    private val handler: Handler,
    /**
     * 判出一次组合：哪一档 + 哪一档手势 + 是不是**连续触发**的重复派发。
     *
     *  `repeat` 与单键那条 `onGesture` 的第三位同义：重复派发不再振动、不再报活（那是同一次按压）。
     */
    private val onCombo: (ComboKey, GestureTrigger, Boolean) -> Unit,
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
     * （`Config.comboScanWindowMs`）。它只管「扫多宽」，**算不算数**由 [windowFor] 判。
     */
    private val windowMs: () -> Long,
    /**
     * 某一档组合**自己**的窗口（毫秒）—— 配对命中之后用它复核间隔，没设过的那些跟随公共值
     * （`Config.comboWindowFor`）。为什么扫与判要分成两个数：按下当前这一颗键时还不知道会凑成
     * 哪一组，扫窄了会漏掉宽窗口那一档；而不复核的话，宽窗口那一档会把本该属于短窗口那一组的
     * 配对抢走（谁先遇上算谁）。
     */
    private val windowFor: (ComboKey) -> Long,
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
     * 某一档组合**整串**的容忍跨度（毫秒，= 相邻两颗的窗口 × (成员数-1)，见 `Config.comboSpanFor`）
     * —— 多成员那条路按它判「凑没凑齐」：三颗按一个窗口算的话，三根手指得几乎同时落下，
     * 真机上等于永远凑不齐。
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
     *  `longPressMs` 算；「只参与组合」的键恒返回 `false`（它压根不判单键）。
     */
    private val longPressPassed: (KeyId, Long, Long) -> Boolean = { _, _, _ -> false },
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
            //  `bindingsFor` 已按「成员多的在前」给好：升级就是在这几张里挑一张**更大且认窗口**的。
            //
            //  **升级这条路也吃「等窗口」那一关**：更大的一档（3 颗起）只走窗口那条判据，
            //  它自己不是等窗口就该凑不齐 —— 不判这一位的话，{A,B} 先把组合立起来、再补第三颗，
            //  就会绕过 `multiFor` 的那道闸把不认窗口的一档升出来（「不该按出来的却按出来了」）。
            val bigger =
                bindingsFor(pool).firstOrNull {
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
        if (waiting == null || c.keys.all { down.containsKey(it) }) {
            consumed += id
            return true
        }
        //  这一组等不到更宽的那一档了：先派发（成员已经松过手，动作只能现在补），再放行这一颗
        cancelShots(c)
        if (!c.longFired) {
            XLog.w(
                "组合键：${c.keys.joinToString(" + ") { it.label }} 短按" +
                    "（${id.label} 按下，不再等 ${waiting.key.id}）",
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
                        cancelShots(c)
                        combo = null
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
                    cancelShots(c)
                    combo = null
                }
            }
            recent.remove(id)
            consumed.remove(id)
        }
        recent[id] = now
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
        if (id !in consumed) return false
        consumed.remove(id)
        val c = combo
        if (c != null && id in c.keys) {
            //  还在等更宽的一档、这一组本身又什么都没派发过 ⇒ 抬手也先压着，收尾交给那一枪
            //  （见 [awaitWider]）。**长按已经派发过的不在此列**：那时等的意义已经没有了，
            //  「松手才触发 / 松手额外动作」得照老规矩立刻办。
            val waiting = c.waiting
            if (waiting != null && !c.longFired) {
                //  **取消**也不在此列：取消的含义是「这一下不算数」（窗口失焦 / 来电打断），
                //  留着那一枪会让它到点凭空短按一次。
                if (!short) {
                    XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 这一按被取消 ⇒ 不再等 ${waiting.key.id}")
                    cancelShots(c)
                    combo = null
                    return true
                }
                XLog.w(
                    "组合键：${c.keys.joinToString(" + ") { it.label }} 先松开 ${id.label}，" +
                        "继续等 ${waiting.key.id}",
                )
                return true
            }
            // 组合结束：没判过长按 ⇒ 这一次是「一起短按」（取消的那一档不派发）
            if (short && !c.longFired) {
                XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 短按（先松开 ${id.label}）")
                fire(c, GestureTrigger.Click(1), repeat = false)
            } else if (short && c.longFired) {
                //  长按已经判出来过：这一下按那颗键自己的「松手」两档收尾（与单键同一条口径）——
                //  · 松手才触发：到点只标了状态，动作放到这一下执行一次；
                //  · 松手执行额外动作：按住期间已经执行过，这一下再补一条。
                if (c.key.longFireOnRelease) {
                    XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 长按（松手才触发）")
                    fire(c, GestureTrigger.LongPress, repeat = false)
                } else if (c.key.releaseBound) {
                    //  **只有认窗口那一档才有「松手」这一档**：按住那两档的槽是那一对 `single`/`long`
                    //  本身，抬手只是收尾 —— 再补一次松手动作就成了「按住手势 + 松手命令」两个结果
                    //  （用户口径：只出按住手势那一条）。
                    XLog.w("组合键：${c.keys.joinToString(" + ") { it.label }} 长按松手（${c.key.longRelease.token}）")
                    fire(c, GestureTrigger.Release, repeat = false)
                }
            }
            cancelShots(c)
            combo = null
        }
        // 组合的另一颗键可能还按着 —— 它的抬手同样要吞（`consumed` 里还留着它）
        return true
    }

    /**
     * 清空（配置变化 / 主开关关掉）。
     *
     *  与 `SideKeyInterceptor.cancelPending` 同一处调用：关掉某颗键时，它按住不放的那一下
     *  不能一路走到长按。
     */
    fun reset() {
        combo?.let {
            cancelShots(it)
        }
        combo = null
        down.clear()
        recent.clear()
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
     *  与 [pairFor] 的分工见类注释。**跨度按成员数放大**：两颗那条判的正是「这两颗相差不超过
     *  一个窗口」，n 颗要用同一条判据就得让整串（最早那一颗到当前这一下）不超过 (n-1) 个窗口
     *  —— [spanFor]。三颗也按一个窗口判的话，三根手指必须几乎同时落下，
     *  真机上就是「三颗的组合怎么按都没反应」，而日志里一行都不留。
     *  夹在中间的不相干的键不作废这一档，但它也不算成员。
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
        //  不参与任何组合的键不可能是多成员组合的成员 —— 这一位比扫一遍候选便宜得多
        if (!joinsCombo(id)) return null
        var count = 1
        for ((key, at) in recent.entries) {
            if (key != id && now - at <= window) count++
        }
        if (count < MULTI_MIN_KEYS) return null
        //  候选 = 这一下 + 窗口内按下过的那些（含刚松手的：顺序按下也算凑齐，见 [recent] 的注释）
        val pool = arrayListOf(id)
        for ((key, at) in recent.entries) {
            if (key != id && now - at <= window) pool.add(key)
        }
        //  「成员多的在前」「同数按配置顺序」都在 `Config.combosFor` 里，这一层不重复判。
        //  比 [MULTI_MIN_KEYS] 小的结果不接：那说明这是一档**两颗**的组合，本该由 [pairFor]
        //  认出来（它带着「按下时刻最近」的挑法），走到这里还认成两颗只会挑错成员。
        //
        //  多成员这一条**只走窗口那条判据**：不认窗口的那几档（按住那两种）没有「哪一颗先按住」
        //  这个唯一起点，四颗以上更是如此 —— 挑第一张「够大且认窗口」的。
        val hit = bindingsFor(pool).firstOrNull { it.keys.size >= MULTI_MIN_KEYS && it.isWait } ?: return null
        //  成员按**命中那一档自己的清单**取：候选里夹的别的键不算成员（它们还得自己走单键判定）
        val members = List(hit.keys.size) { i -> pool.first { it.id == hit.keys[i] } }
        //  间隔按**成员**里最早那一颗算（候选里可能夹着不相干的键，那是别人的按下时刻）
        val span = now - members.minOf { recent[it] ?: now }
        //  这一档自己的**整串跨度**复核：扫的时候按最宽的那一档扫，算不算数由它自己说了算
        //  （不成立就整条作废，所以是 `takeIf` 而不是再补一个 `return null`）。
        //  用 `spanFor` 而不是 `windowFor`：n 颗的容忍跨度是 (n-1) 个窗口（见函数注释）。
        return Hit(hit, members, span).takeIf { span <= spanFor(hit) }
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
            //  这一对的判据**按每一档自己的触发方式选**，逐档试 —— 同一组键可以配几档，
            //  各自一种方式（`ComboKey.mode`），只看成员数最大的那一档会把另一种方式整条遮掉。
            //  · 等窗口：两颗的按下相差不超过这一档的窗口（扫按最宽的、判按它自己的）；
            //  · 先按住某颗：先按的那颗**还按着**、且它正是这一档的起点（`starterKey`）、
            //    按住够久（`holdMinFor`）、它的长按还没判出来（判过就已经执行过自己的动作了）。
            //
            //  **按住那几档优先**：两档同时命中时（那一颗既在窗口内、又按满了按住下限）取按住那档 ——
            //  它的前提更严（要求起点键先按着且过了下限），是更明确的表态；等窗口那档只要有第二颗
            //  在窗口内落下就成立，留给「没按住那么久」的情形。
            var hitKey: ComboKey? = null
            for (b in candidates) {
                val ok =
                    if (b.isWait) {
                        span <= window && span <= windowFor(b)
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
