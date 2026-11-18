package com.lookie.opluskey.core.judge

// 判定里那些**只跟数字有关**的部分 —— 候选窗口、成员新鲜度、整串跨度、派发击数、按序比对。
//
//  为什么收进 `core`：这些函数原先都是 `inject/input/ComboDetector` 与 `GestureDetector`
//  的私有成员，**它们不碰任何状态、不读时钟、不调 Android API**，却因为住在 `:app` 里而
//  一个测试都写不了（本项目 `:app` 没有 test 源集，见 AGENTS 二、1.2 的「没有测试工程」）。
//  搬到这里之后：`ComboDetector` / `GestureDetector` 只保留「什么时候调它」，
//  `:core:test` 里可以直接喂时刻表验证边界。
//
//  **参数一律传数据、不传容器**：`presses` / `held` 传的是判定器自己那两张表的只读视图
//  （函数不复制、不改写）。这样它们既是纯函数，又不会因为多一次 map 拷贝把
//  「每次按下都要走一遍」的那条热路径变慢（`tools/audit-perf.py` 判据 2 管着这件事）。
//
//  ## 为什么这几处是泛型
//
//  这一包里一个业务类型都不该出现 —— 判定是两进程共用的纯数学，业务模型（`ComboKey` / `KeyId`）
//  在 `core.model` 里，会随需求改。调用方（`inject/input`）有自己那套键引用与档位视图，
//  两边只需在这几个函数上对齐**「怎么比、怎么念」**：`K` 是键的类型，`T` 是档位的类型。
//  泛型把它交给调用方，这一层于是只剩索引、排序、比较。

/**
 * 多成员组合的**候选池**：这一下按下的那颗 + 窗口内按下过的 + **仍按着的**。
 *
 *  仍按着的也要收：按住档的首键可能已经按了很久，早就不在窗口里了（见 `ComboDetector.multiFor`）。
 *  含刚松手的那几颗 —— 顺序按下也算凑齐（`presses` 里那颗的抬手不清表，见 `recent` 的注释）。
 *
 *  返回的顺序是「当前这一颗在前，其余按 `presses` 的迭代序」—— 与原来就地拼那个 `ArrayList`
 *  逐字相同；调用方按 `size` 判「够不够多成员」。
 *
 *  @param now 当前这一下的时刻
 *  @param window 扫候选用的窗口（几档里最宽的那个）
 *  @param presses 每颗键最近一次按下的时刻（`ComboDetector.recent`）
 *  @param held 此刻还按着的键（`ComboDetector.down` 的键集）
 *  @param self 当前这一下按下的那颗
 */
fun <K> poolWithinWindow(
    now: Long,
    window: Long,
    presses: Map<K, Long>,
    held: Set<K>,
    self: K,
): List<K> {
    //  只量一次 `now - at`：这是每次按下都要走的一段
    val pool = arrayListOf(self)
    for ((key, at) in presses) {
        if (key != self && (now - at <= window || key in held)) pool.add(key)
    }
    return pool
}

/**
 * 按住档：后续成员**是否都在各自该等的窗口里落完**。
 *
 *  两种判定时机各量一件事（四格窗口里的两格）：
 *  · [fixedStart]（固定窗口起点开着）：从**第一个成员**按下起算一个 [wholeWindowMs]，
 *    其余成员都要落在这段时间内 —— 首键按住多久都不计时；
 *  · 否则：每颗成员各自往前等 [perFollowerWindowMs]（量的是相邻两次按下之间）。
 *
 *  @param followerPressAt 后续成员的按下时刻（按成员清单的顺序，首键不在其中）
 *  @param wholeWindowMs 整串共用的那个窗口（四格里的 `hold`）
 *  @param perFollowerWindowMs 每颗各自往前等的窗口（四格里的 `holdFollower`）
 *  @return `true` = 这一档成立；成员为空时恒 `true`（两颗的组合没有待量的间隔）
 */
fun followersFresh(
    now: Long,
    followerPressAt: List<Long>,
    fixedStart: Boolean,
    wholeWindowMs: Long,
    perFollowerWindowMs: Long,
): Boolean =
    if (fixedStart) {
        val firstAt = followerPressAt.firstOrNull() ?: now
        now - firstAt <= wholeWindowMs
    } else {
        followerPressAt.all { now - it <= perFollowerWindowMs }
    }

/**
 * 整串跨度：从**成员里最早那一刻**算到当前这一下。
 *
 *  取最早那颗而不是配置里的首键：成员顺序是配置里的键序，与真实按下顺序不一定一致
 *  （用户先按配置里的第二颗）。拿配置首键算会把跨度算小，超过这一档自己窗口的组合也照样认下
 *  —— 误触发，且这几颗键的单击 / 长按被一起吞掉。
 *
 *  @param memberPressAt 命中的那几个成员各自的按下时刻（空表 ⇒ 跨度为 0）
 */
fun spanFrom(
    now: Long,
    memberPressAt: List<Long>,
): Long = now - (memberPressAt.minOrNull() ?: now)

/**
 * 到点那一枪**实际派发几击**：落在 `1..cap` 之间。
 *
 *  连击链数出来的击数可能为 0（老配置 / 抬手丢失）、也可能超过这一档的封顶
 *  （用户连按了第 5 下而这一档只认到第 3 击），两处到点判定都按这个数派发。
 *
 *  @param cap 这一档的判定上限（`ChainSpec.judgeCap`，至少 1）
 */
fun fireCount(
    clicks: Int,
    cap: Int,
): Int = clicks.coerceIn(1, cap.coerceAtLeast(1))

/**
 * [pool] 里这几颗键按**按下序号**排出的真实顺序 —— 只给「按顺序触发」那档用。
 *
 *  序号查不到的那颗（不太可能：池里的键都在 `presses` 里，也就都在序号表里）排在末尾，
 *  兜住「读不到序号」的边角 —— 那它基本不会命中任何有序档，但不会崩。
 *
 *  @param seq 每颗键的按下序号（`ComboDetector.pressSeq`，只增不减）
 *  @param ref 怎么把一颗键念成它在这份配置里的身份（有序档按这一串比）
 */
fun <K, R> pressOrder(
    pool: List<K>,
    seq: Map<K, Int>,
    ref: (K) -> R,
): List<R> = pool.sortedBy { seq[it] ?: Int.MAX_VALUE }.map(ref)

/**
 * [sequence] 的**结尾**是不是与 [members] 逐位相同 —— 「按顺序触发」那条判据的唯一实现。
 *
 *  判定侧（`inject/input/ComboBinding.matchesOrdered`）与业务侧（`ComboKey.matchesOrdered`）
 *  都调它。这条判据原先各写一份：线上跑的是判定侧那一条，而单测喂的是业务侧那一条 ——
 *  两份一旦走岔，测试全绿而真机是坏的（2026-11-18 复核发现）。
 *
 *  **比的必须是结尾而不是开头**：当前这一下一定是成员里的最后一颗（组合在它落下那一刻成立），
 *  而扫描窗口里可能还兜着这一组开始之前按过的键。按开头比时，前面多一颗（比如先点了一下音量键）
 *  就会把整条有序组合判成「顺序不对」，用户看到的是一组没生效、只落下两颗键各自的单击。
 *
 *  @param members 这一档的成员（顺序即按下顺序）
 *  @param sequence 真实按下顺序
 */
fun <T> matchesOrderedTail(
    members: List<T>,
    sequence: List<T>,
): Boolean =
    sequence.size >= members.size &&
        members.indices.all { sequence[sequence.size - members.size + it] == members[it] }

/**
 * 这几档里，[sequence] 这一**真实按下顺序**能命中的有序档（含不分顺序档）。
 *
 *  有序档（`ComboBinding.ordered`）按序比（`ComboBinding.matchesOrdered`）；不分顺序档本就不看顺序，
 *  照旧留下。两者合成一张候选，调用方再按触发方式逐档挑。
 *
 *  **一档有序的都没有时直接把原表返回**：这条在「全是等窗口的两颗组合」的常见配置上省掉一次遍历。
 *
 *  @param ordered 这一档要不要求按顺序
 *  @param matches 这一档认不认这个顺序
 */
fun <T, R> orderMatch(
    candidates: List<T>,
    sequence: List<R>,
    ordered: (T) -> Boolean,
    matches: (T, List<R>) -> Boolean,
): List<T> =
    if (candidates.none(ordered)) {
        candidates
    } else {
        candidates.filter { !ordered(it) || matches(it, sequence) }
    }

/**
 * 挂住的组合要不要**自愈**（清空组合状态）。
 *
 *  不清的后果不是「这一次没反应」而是**整块键盘不再响应**：`combo` 一直挂着，之后每一颗键的按下
 *  都走「组合进行中」那一支被吞掉。两种情形等着被救：
 *  · [TIMEOUT]：挂得比上限还久（与重复链那条同一个数）—— 正常的一组在判定完成时就会清掉；
 *  · [NO_MEMBER_DOWN]：成员**一颗都不在按着**、而且这一组也没在等更宽的一档 ——
 *    正常的组合在第一个成员抬手时就清了（见 `ComboDetector.onUp`），「一个都不在」只可能是抬手全丢。
 *
 *  **超时优先**：两条都成立时报超时（用户看到的处置一样，但日志该说的是更确定的那个原因）。
 *  「在等更宽的一档」不算异常：顺序按下也算组合，那种状态下成员本来就可以全部松手。
 *
 *  @param formedAt 这一组成立的时刻（超时从它量，不是从最早那颗按下量）
 *  @param maxMs 上限（`ComboDetector` 的 `COMBO_REPEAT_MAX_MS`）
 */
enum class ComboStuck {
    /** 正常：还按着，或在等更宽的一档。 */
    NONE,

    /** 挂得太久 —— 抬手大概率已经丢了。 */
    TIMEOUT,

    /** 成员一颗都不按着，也没在等更宽的一档 —— 抬手全丢。 */
    NO_MEMBER_DOWN,
}

fun comboStuck(
    formedAt: Long,
    now: Long,
    anyDown: Boolean,
    waiting: Boolean,
    maxMs: Long,
): ComboStuck =
    when {
        //  边界：正好等于上限还不算挂住（判据是严格的 `>`）
        now - formedAt > maxMs -> ComboStuck.TIMEOUT

        anyDown || waiting -> ComboStuck.NONE

        else -> ComboStuck.NO_MEMBER_DOWN
    }
