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
 *  两颗那一档不走多成员这条路（见 [ComboDetector] 里 `pairFor` 与 `multiFor` 的分工）：
 *  那边要在候选里挑「按下时刻由近到远」，结论更细。3 颗起才需要「整体凑齐」这个判据。
 */
private const val MULTI_MIN_KEYS = ComboKey.MIN_KEYS + 1

/**
 * 判定「这一下算不算组合」要问配置的三件事 —— 三个读数打成一包。
 *
 *  为什么打包：这三支都只读注入侧手上那份配置、都由装配方接 `ConfigChannel` 的同三支，
 *  而 [ComboDetector] 的构造参数个数已经贴着 detekt 的 `LongParameterList` 上限 ——
 *  单独拆一个类比再往构造函数上挂一个 lambda 强，调用点也只多一层壳。
 *
 * @param scanMs 当前的**扫描**窗口（毫秒）—— 找同伴时按这个宽度扫候选，取的是这份配置里最宽
 *   的那一档（`Config.comboScanWindowMs`）。它只管「扫多宽」，**算不算数**由 [forCombo] 判。
 * @param forCombo 某一档组合**自己**的窗口（毫秒）—— 配对命中之后用它复核间隔，没设过的那些
 *   跟随公共值（`Config.comboWindowFor`）。为什么扫与判要分成两个数：按下当前这一颗键时还
 *   不知道会凑成哪一组，扫窄了会漏掉宽窗口那一档；而不复核的话，宽窗口那一档会把本该属于
 *   短窗口那一组的配对抢走（谁先遇上算谁）。
 * @param joins 这一颗键参与某一组已启用的组合吗 —— 决定它按下之后要不要**先等一个窗口**。
 *   参与的键等着看「同伴键会不会来」（这期间不喂单键判定器，用户能感觉到的就是「参与组合的键
 *   按下去晚一点才有反应」）；不参与的键立刻交回单键，一点不受影响。`multiFor` 也拿它当第一道
 *   闸：不参与任何组合的键，不可能是多成员那一档的成员。
 * @param spanFor 某一档组合**整串**的容忍跨度（毫秒，= 相邻两颗的窗口 × (成员数-1)，见
 *   `Config.comboSpanFor`）—— 多成员那条路按它判「凑没凑齐」：三颗按一个窗口算的话，
 *   三根手指得几乎同时落下，真机上等于永远凑不齐。
 */
internal class ComboWindows(
    val scanMs: () -> Long,
    val forCombo: (ComboKey) -> Long,
    val joins: (KeyId) -> Boolean,
    val spanFor: (ComboKey) -> Long,
)

/**
 * 组合键的判定 —— **几颗已接管的键**在窗口内先后按下，就当作一次组合，不再各自判单击 / 长按。
 *
 *  判出组合之后执行什么，由装配方决定（`onCombo`）：这里只回答「哪一档 + 短按还是长按」。
 *
 *  与 [GestureDetector] 的分工：那个判单颗键的手势，这个只判「几颗键的重叠」。一旦认出组合，
 *  立刻把这几颗键的单键状态机**摘掉**（[resetKey]），否则先按下的那颗会在组合期间判出长按 ——
 *  一次按键执行两个动作。
 *
 * ## 先问 [bindingFor]
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
 *  **扫**候选时相对当前这一下算（`now - at <= window`）：几颗键之间夹了一颗不相干的键时，
 *  这一档照样成立。**算不算数**则由这一档自己的跨度判 —— 「相邻两颗最多隔一个窗口」
 *  ⇔「最早那一颗到当前这一下最多隔 (n-1) 个窗口」（[ComboWindows.spanFor]）。
 *  三颗套用两颗那一个窗口会变成「三根手指必须同时落」，真机上等于永远凑不齐。
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
 *  日志三处都是同一条真链路，不是旁路。
 *
 * ## 组合进行中再来的按键只出一组
 *
 *  组合进行中再来的按键整条吞掉（按下 + 抬手），所以「几颗里有两组组合」也只出**先凑成的那一组**。
 *
 *  **唯一的例外是「更大的一档被这一下凑齐了」**（[upgradeTo]）：两颗的子集组合总是先成立，
 *  不让位的话三颗那一档永远轮不到 —— 那一档换成更大的、抬手时派发的是它，仍然只出一组。
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
     * 这几颗键凑成的是哪一档 —— `null` = 没有绑定，组合不成立（见类注释）。
     *
     *  由装配方接 `ConfigChannel.comboFor`（读注入侧手上那份配置的 `combos`）。**成员数不设限**：
     *  两颗那一档一对一地问（`pairFor`），3 颗起把窗口内按下的那一组整体递进来（`multiFor`），
     *  「成员多的赢」由 `Config.comboFor` 判。
     */
    private val bindingFor: (List<KeyId>) -> ComboKey?,
    /** 三个「窗口 / 参与」读数 —— 由装配方接 `ConfigChannel` 那三支，见 [ComboWindows]。 */
    windows: ComboWindows,
) {
    //  摊成几个私有字段，而不是判定路径里到处写 `windows.xxx`：调用点（`windowMs()` /
    //  `windowFor(..)` / `joinsCombo(..)`）一行都不用改，且都是判定热路径。
    private val windowMs = windows.scanMs
    private val windowFor = windows.forCombo
    private val joinsCombo = windows.joins
    private val spanFor = windows.spanFor

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

    private class Combo(
        /** 这一档是谁 —— 执行什么由它决定（`single` / `long` 两格）。 */
        val key: ComboKey,
        val keys: List<KeyId>,
        /** 组合成立那一刻（连发上限从它开始量）。 */
        val pressedAt: Long,
        /** 长按那一枪，抬手 / 重置时摘掉。 */
        @Volatile var shot: Runnable? = null,
        /** 已经判过「一起按住不放」。 */
        @Volatile var longFired: Boolean = false,
        /** 连发那一枪（`longRepeat` 打开时才有），抬手 / 重置时摘掉。 */
        @Volatile var repeatShot: Runnable? = null,
    )

    /**
     * 一次**凑成**：哪一档、真要算成员的那几颗、以及「从最早那一颗到这一下」隔了多久（只进日志）。
     *
     *  [keys] 不一定是候选全体：多成员那条路把窗口内按下的所有键一起递给绑定闸
     *  （`Config.comboFor` 只要求成员都在），夹在中间的不相干的键不算成员 —— 拿候选全体去摘
     * 单键状态机，会把一颗本来该自己响的键也一起吞掉。
     */
    private class Hit(
        val key: ComboKey,
        val keys: List<KeyId>,
        val spanMs: Long,
    )

    /**
     * 在窗口内挑一颗「配得上、且按下时刻最新」的同伴 —— 配上了就是这一档组合。
     *
     *  写成**一趟扫描**而不是 `filter { } .sortedByDescending { } .firstNotNullOfOrNull { }`：
     *  这是每次按下都要走的一段（参与组合的键全都从这儿过），三段流水线一次按压要建两个
     *  中间列表 + 三个 lambda；而候选本来只有窗口内那几颗（常态一两颗），排序毫无意义。
     *
     *  取法与原来逐条相同：配得上（`bindingFor` 有值）的候选里取 `at` 最大的一颗；
     *  `at` 相同时保留**先遇到**的那一颗（原来稳定排序后取第一个，也是先遇到的那一颗）。
     */
    private fun pairFor(
        id: KeyId,
        now: Long,
        window: Long,
    ): Hit? {
        var best: Hit? = null
        var bestAt = 0L
        for ((key, at) in recent.entries) {
            val span = now - at
            //  扫的时候按最宽的那一档，命中后按**这一组自己**的窗口复核（见 [windowFor]）
            val candidate =
                if (key != id && span <= window) {
                    bindingFor(listOf(key, id))?.takeIf { span <= windowFor(it) }
                } else {
                    null
                }
            val current = best
            if (candidate != null && (current == null || at > bestAt)) {
                best = Hit(candidate, listOf(key, id), span)
                bestAt = at
            }
        }
        return best
    }

    /**
     * 3 颗以上的组合：窗口内按下的这一组里，是不是正好凑齐了某一档（[MULTI_MIN_KEYS] 起）。
     *
     *  与 [pairFor] 的分工见类注释。**跨度按成员数放大**：两颗那条判的正是「这两颗相差不超过
     *  一个窗口」，n 颗要用同一条判据就得让整串（最早那一颗到当前这一下）不超过 (n-1) 个窗口
     *  —— [ComboWindows.spanFor]。三颗也按一个窗口判的话，三根手指必须几乎同时落下，
     *  真机上就是「三颗的组合怎么按都没反应」，而日志里一行都不留。
     *  夹在中间的不相干的键不作废这一档，但它也不算成员。
     *
     * ## 先数、后建表
     *
     *  第一趟只数不建表：窗口内凑不够 3 颗就直接收工。参与**两颗**组合的键每次按下都会走到
     *  这里（[pairFor] 没配上就是这一条），那才是常态 —— 让它每次按压分配一个列表说不过去。
     *  最外层还有一道 `joinsCombo`：不参与任何组合的键连数都不数。
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
        //  「成员多的赢」「同数按配置顺序」都在 `Config.comboFor` 里，这一层不重复判。
        //  比 [MULTI_MIN_KEYS] 小的结果不接：那说明这是一档**两颗**的组合，本该由 [pairFor]
        //  认出来（它带着「按下时刻最近」的挑法），走到这里还认成两颗只会挑错成员。
        val hit = bindingFor(pool)?.takeIf { it.keys.size >= MULTI_MIN_KEYS } ?: return null
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
     * 组合进行中又按下 [id] —— 这一下把**更大的一档**凑齐了就换过去，返回 `true`。
     *
     * ## 为什么必须让位
     *
     *  「成员多的赢」是配置侧的取法（`Config.comboFor` 的 `maxByOrNull { keys.size }`），但判定侧
     *  按**时间**走：两颗的子集组合在第二颗按下那一刻就成立了，而三颗那一档要等第三颗。
     *  于是 `combo != null` 那一支会把第三颗整条吞掉 —— `{A,B,C}` 这档在有 `{A,B}` 的条件下
     *  永远轮不到。真机日志就是这么写的：`{A,B}` 同时按下 → 第三颗被吞 → 只有子集那一档响。
     *
     *  ## 判据与两条老规矩一致
     *
     *  · 只在组合**还没松手**（`combo != null`）时升级：抬手之后那一档已经派发过，换不回来了；
     *  · 跨度仍按这一档自己的整串跨度（[ComboWindows.spanFor]）：三颗不是「按着不放多久都能补」，
     *    超出窗口的那一颗照旧只吞不认（与「顺序按下也算凑齐」同一条窗口语义）。
     *
     *  升级只换 `combo` 这一份状态：长按那一枪按**新的**档重排（旧的那一枪摘掉），
     *  `consumed` 补上新成员，成员的单键状态机一并摘掉 —— 抬手时派发的就是更大那一档，
     *  子集那一档一次都不会响。
     */
    private fun upgradeTo(
        c: Combo,
        id: KeyId,
        now: Long,
    ): Boolean {
        val pool = c.keys + id
        //  `bindingFor` 已经含「成员多的赢」：它返回的是这几颗键能凑成的**最大**那一档
        val bigger =
            bindingFor(pool)?.takeIf {
                it.keys.size > c.keys.size && now - c.pressedAt <= spanFor(it)
            } ?: return false
        //  成员按 id 认回 `KeyId` 对象（`matches` 保证了它们都在 pool 里）
        val members = bigger.keys.mapNotNull { kid -> pool.firstOrNull { it.id == kid } }
        //  认不出的成员只可能是配置在两次按下之间换了 —— 那就别升，保持原来那一档
        if (members.size != bigger.keys.size) return false
        cancelShots(c)
        val next = Combo(bigger, members, pressedAt = now)
        combo = next
        members.filterTo(consumed) { down.containsKey(it) }
        for (k in members) resetKey(k)
        XLog.w(
            "组合键：${c.keys.joinToString(" + ") { it.label }} 升级为 " +
                "${members.joinToString(" + ") { it.label }}（${bigger.id}，成员多的赢）",
        )
        scheduleLong(next, startedAt = now)
        return true
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
        //  判据只看时长（与重复链那条 `COMBO_REPEAT_MAX_MS` 同一档）；挂在按键入口上而不是单起
        //  一个定时器 —— 下一条按键事件本来就是自愈的时机，多一条定时器反而多一条要停的链。
        combo?.let { c ->
            if (now - c.pressedAt > COMBO_REPEAT_MAX_MS) {
                XLog.w(
                    "组合键：${c.keys.joinToString(" + ") { it.label }} 挂住超过 ${COMBO_REPEAT_MAX_MS}ms" +
                        "（抬手可能已丢）⇒ 清空组合状态",
                )
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
            XLog.w("组合键：${id.label} 又按下但没等到上一次抬手（UP 丢了）⇒ 清空组合状态重新算")
            reset()
            down[id] = now
        }
        recent[id] = now
        // 已经在组合里：第三颗键按下来默认不掺和，仍按「组合进行中」吞掉。
        //  **记进 [consumed]**：不记的话它没有「按下」，抬手那条 UP 会被单键判定器当成
        //  「孤立抬手」补判一次单击 —— 组合进行中又冒出第三颗键的动作。
        //
        //  **例外是「这一下把更大的一档凑齐了」**（见 [upgradeTo]）：两颗的子集组合总是先凑齐
        //  （第二颗按下那一刻就成立），不让位的话三颗那一档永远轮不到 —— 真机表现就是
        //  「三颗的组合怎么按都没反应」，而日志里只有子集那一档的行。
        val active = combo
        if (active != null) {
            if (!upgradeTo(active, id, now)) consumed += id
            return Down.TAKEN
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
        val hit = pairFor(id, now, window) ?: multiFor(id, now, window)
        // 没凑成：只有「参与组合的键」才值得先等一个窗口，其余立刻交回单键
        if (hit == null) return if (joinsCombo(id)) Down.HOLD else Down.PASS
        val c = Combo(hit.key, hit.keys, pressedAt = now)
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
        XLog.w(
            "组合键：${hit.keys.joinToString(" + ") { it.label }} 同时按下（${hit.key.id}）" +
                "（间隔 ${hit.spanMs}ms，窗口 ${spanFor(hit.key)}ms）",
        )
        scheduleLong(c, startedAt = now)
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
     * 把这一组**两条定时链**都撤掉：长按那一枪、连续触发那一串。
     *
     *  三处调用点（被另一组顶掉 / 抬手 / 重置）一直是成对调用这两条 —— 合成一支，
     *  就不会出现「只撤了一条、另一条还在跑」那种只在一台机器上偶尔复现的怪现象。
     */
    private fun cancelShots(c: Combo) {
        c.shot?.let { handler.removeCallbacks(it) }
        c.shot = null
        c.repeatShot?.let { handler.removeCallbacks(it) }
        c.repeatShot = null
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
                    if (SystemClock.elapsedRealtime() - (c.pressedAt) > COMBO_REPEAT_MAX_MS) {
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
}
