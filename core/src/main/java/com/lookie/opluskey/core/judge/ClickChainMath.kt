package com.lookie.opluskey.core.judge

// 连击链上的判据 —— 按一下之后「这一下算第几击、还要不要等、什么时候派发」。
//
//  与 [JudgeMath] 同一族：全是纯函数，不读时钟、不碰状态、不调 Android API。
//  输入是 `GestureDetector.Pending` 里那几个字段的**快照**，输出是一个枚举。
//  搬进 `core` 的理由与那边相同 —— `:app` 没有 test 源集，而这条链是「一次按压执行两个动作」
//  「双击绑的动作永远不执行」这类故障的唯一发源地，靠手按验不出边界。
//
//  **只回答「走哪一支」，不负责改状态**：`sawFirstRelease` / `longPressAt` / `doubleFired`
//  都仍由 `GestureDetector` 按返回的枚举去写。这样同一段判据既能在单测里直接喂，
//  也不会把「哪一刻读状态」这件事挪出那把 `p` 锁。

/**
 * 按下那一刻，这一次按下走哪一支。
 *
 *  三种走向对应时间轴上的三种位置：
 *  · [CONTINUE] 上一击的窗口还开着 ⇒ 这是一次连击，接着数；
 *  · [CATCH_UP] 窗口**已经过期**、可那一枪还没跑到（主 looper 忙，或排在长按枪之后）⇒
 *    先把上一次补派发掉，再按新的一下重置 —— 不补的话 `doubleWindowAt` 一清，
 *    那颗迟到的枪到点就判「属于上一次按压」直接作废，连点两下只出一次动作；
 *  · [FRESH] 上一按已经收干净（或从没按过）⇒ 全新的一轮。
 */
enum class PressEntry {
    CONTINUE,
    CATCH_UP,
    FRESH,
}

/**
 * @param sawFirstRelease 这一次按压已经抬过手（`Pending.sawFirstRelease`）
 * @param windowAt 窗口截止时刻（`Pending.doubleWindowAt`，`0` = 还没排）
 */
fun pressEntry(
    now: Long,
    sawFirstRelease: Boolean,
    windowAt: Long,
): PressEntry =
    when {
        //  截止时刻这一刻仍算「在窗口里」：`<=` 与排窗那一侧写的 `now + window` 是同一个数
        sawFirstRelease && now <= windowAt -> PressEntry.CONTINUE

        //  走到这里 `now > windowAt` 已经成立（不是 `<=` 就只能是 `>`）
        sawFirstRelease && windowAt != 0L -> PressEntry.CATCH_UP

        else -> PressEntry.FRESH
    }

/**
 * 已经进了连击链、击数 +1 之后，这一击怎么收。
 *
 * @param clicks **已经加过这一击**之后的击数
 * @param cap 这一档的判定上限（`ClickChainParams.judgeCap`，按档位开关算出来，至少 1）
 * @param thenLongClicks 哪几击按住会变成「后长按」—— 元素是**按下那一刻的击数**：
 *   2 = 单击后长按、3 = 双击后长按、4 = 三击后长按（见 `GestureTrigger.ClickThenLong.count`）。
 *   判据是**开关**，不是看有没有动作 —— 动作设成无操作时这一档仍要认下那一击，
 *   否则会落到「数到封顶」那一支报普通连击，执行侧对没有动作的档逐级回退，
 *   最终**退成单击命令**。
 */
enum class ChainStep {
    /** 还没数到封顶：继续等下一击，这一击不再排长按枪（连击中不会变长按）。 */
    KEEP_WAITING,

    /** 这一击按住会变「后长按」+ 后面还有档位要等：排上长按枪，同时继续等。 */
    CHAINED_WAITING,

    /** 这一击按住会变「后长按」+ 这一击就是链尾：抬手走连击、按住走长按，两条路都留着。 */
    CHAINED_FINAL,

    /** 数到这一档的封顶（或超出）：当场判，派发交给抬手那一支。 */
    FIRE,
}

fun chainStep(
    clicks: Int,
    cap: Int,
    thenLongClicks: Set<Int>,
): ChainStep =
    when {
        clicks in thenLongClicks && clicks < cap -> ChainStep.CHAINED_WAITING
        clicks in thenLongClicks -> ChainStep.CHAINED_FINAL
        clicks < cap -> ChainStep.KEEP_WAITING
        else -> ChainStep.FIRE
    }

/**
 * 收线那一刻**实际按第几击派发** —— 击数往下取到最近一个**开着的点击档**。
 *
 * ## 为什么需要它
 *
 *  开关是按档独立的：开了双击和四击、没开三击时，连击链照样会数出 `clicks = 3`（四击要等第 4 下，
 *  所以链一路开着）。可 3 这一档没有动作，照原样派发 `Click(3)` 的结果是**这一下什么都不发生**
 *  —— 用户按了三下，既没执行双击、也没执行四击（真机口径「三击手势没有反应，中间档位被丢了」）。
 *
 *  取**最近的更低**那一档（3 → 2 = 双击）：双击是他确实按到过的；往上取（4）等于凭空多按一下。
 *  都比 1 还低时落到**单击**（单击没有开关，恒在）。
 *
 * ## 为什么 1 / 2 不在这里纠正
 *
 *  两击而双击关着时，`GestureDetector.fireClicks` 有自己的收法（按**两次单击**派发 —— 那两下是
 *  用户真按的，各执行一次才不会少一下）。挪到这里会把两击收成一击，等于吃掉一次按压。
 *
 * @param clicks 连击链数出来的击数（已按 `fireCount` 夹到 `[1, cap]`）
 * @param tiers 这一档键**开着的点击档位**（见 `ClickChainParams.clickTiers`，至少含 1）
 */
fun settleClicks(
    clicks: Int,
    tiers: Set<Int>,
): Int =
    when {
        clicks in tiers -> clicks
        clicks <= 2 -> clicks
        else -> tiers.filter { it <= clicks }.maxOrNull() ?: 1
    }

/**
 * 抬手那一刻，这一次抬手走哪一支。
 *
 *  判据顺序就是优先级，与 `GestureDetector.onKeyUpLocked` 里那几条 `return` 逐一对应：
 *  「长按已经触发过」压过一切，「连击链已收线」次之，「后面没有档位要等」再次，
 *  最后才轮到要不要排窗口。
 *
 * @param longPressFired 长按已经在按下期间触发过（`Pending.longPressFired`）
 * @param doubleFired 连击链已经收线、只等抬手派发（`Pending.doubleFired`）
 * @param fixedWindowStart 判定时机是「固定窗口起点」（`ClickChainParams.fixedWindowStart`）
 * @param windowAt 窗口截止时刻（`Pending.doubleWindowAt`，`0` = 还没排）
 */
enum class ReleaseStep {
    /** 长按这一档在抬手时还要收尾（松手才触发 / 松手再执行另一动作 / 只清状态）。 */
    FINISH_LONG,

    /** 连击链已经收线 ⇒ 按数到的击数派发。 */
    FIRE_CHAIN,

    /** 后面没有档位要等 ⇒ 当场判单击，不排窗口。 */
    SINGLE_NOW,

    /** 固定窗口起点、窗口还没到 ⇒ 什么都不做，交给那一枪。 */
    STILL_WAITING,

    /** 固定窗口起点、窗口已经到点而那一枪没跑到 ⇒ 当场按实际击数派发。 */
    FIRE_EXPIRED,

    /** 从这次抬手起算一个窗口 —— 两种判定时机在这一步做的事完全一样。 */
    START_WINDOW,
}

fun releaseStep(
    longPressFired: Boolean,
    doubleFired: Boolean,
    clicks: Int,
    cap: Int,
    fixedWindowStart: Boolean,
    windowAt: Long,
    now: Long,
): ReleaseStep =
    when {
        longPressFired -> ReleaseStep.FINISH_LONG

        doubleFired -> ReleaseStep.FIRE_CHAIN

        //  三样多击开关都关着时 `cap` 就是 1：那个窗口里只可能等来「第二下」，
        //  而第二下没有动作可执行 —— 等它纯粹是把单击推迟一个窗口。
        cap <= 1 && clicks <= 1 -> ReleaseStep.SINGLE_NOW

        //  逐次等：每一次抬手都重新起算
        !fixedWindowStart -> ReleaseStep.START_WINDOW

        //  固定窗口起点、首击抬手：从这里起算唯一那个窗口
        windowAt == 0L -> ReleaseStep.START_WINDOW

        now < windowAt -> ReleaseStep.STILL_WAITING

        else -> ReleaseStep.FIRE_EXPIRED
    }

/**
 * 长按那一枪到点时，这一按**还在等长按**吗。
 *
 *  三条栅栏缺一不可，任何一条没过都只是「作废」，不是「判成别的」：
 *  · 长按位还在（`0` = 已经判过 / 已经重置）；
 *  · 还没抬手（抬手那一刻就摘枪，这里多读一遍兜住跨线程的陈旧值）；
 *  · 击数没超过这一枪所属的那一击（[it]）—— 数到更多击说明用户还在连击，
 *    这一枪必须作废，否则第 N 击那一按排下的枪会在第 N+1 击按住期间醒来，凭空判一次长按。
 *
 *  @param longPressAt 长按截止时刻（`Pending.longPressAt`，`0` = 没排 / 已判过）
 *  @param shotClicks 这一枪是在第几击上排的（= 排枪那一刻的击数；普通长按恒为 1）
 */
fun longShotStillWaiting(
    longPressAt: Long,
    sawFirstRelease: Boolean,
    clicks: Int,
    shotClicks: Int = 1,
): Boolean = longPressAt != 0L && !sawFirstRelease && clicks <= shotClicks

/**
 * 这一枪到点该判「后长按」而不是普通长按。
 *
 *  与 [chainStep] 里那条同一判据：看的是**开关**，不是「有没有动作」——
 *  动作设成无操作时这一档仍然要认，否则会落到「数到封顶」那一支派发普通连击，
 *  执行侧对没有动作的档逐级回退，最终**退成单击命令**。
 */
fun longShotIsChained(
    thenLongClicks: Set<Int>,
    clicks: Int,
): Boolean = clicks in thenLongClicks
