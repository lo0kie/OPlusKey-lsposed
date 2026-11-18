package com.lookie.opluskey.core.judge

// 两颗那一对组合的判定 —— 「这一档配不配得上这一对按下」「几档都配得上时挑哪一档」
// 「已经成立的那一档还能不能升级成更宽的一档」。
//
//  与 [JudgeMath] / [ClickChainMath] 同一族：纯函数，只吃数字与档位本身的形态，不碰状态、
//  不读时钟、不调 Android API。三处判据原先都是 `ComboDetector` 里 if / for 的条件表达式，
//  `:app` 没有 test 源集，那几个「哪一条先短路」「哪一档优先」的顺序只能靠手按验。
//
//  **不认任何业务类型**：档位一律是泛型 `T`，「这一档是不是等待档」由调用方给的谓词回答。
//  原先这两处读 `ComboKey.isWait` / `ComboKey.mode`，判定层于是被业务模型的字段名绑住。

/**
 * 等窗口那一档：这一对按下算不算这一档。
 *
 *  两条都得满足，量的是**两个不同的数**：
 *  · [scanWindow]：扫候选用的那个最宽窗口 —— 比它还久的那一颗根本进不了候选；
 *  · [ownSpanMs]：这一档**自己的**整串跨度（`Config.comboSpanFor`）—— 扫得到不等于算得上，
 *    判定仍按这一档自己的窗口来（用户把某一档的窗口调窄，那一档就该更严）。
 */
fun pairWaitMatches(
    span: Long,
    scanWindow: Long,
    ownSpanMs: Long,
): Boolean = span <= scanWindow && span <= ownSpanMs

/**
 * 按住那一档：这一对按下算不算这一档（[isStarter] 那一条见下）。
 *
 *  四条都得满足 —— 比等窗口那档严，因为它的语义是「先按住起点键」这个明确的表态：
 *  · [stillDown]：先按的那颗**还按着**（松了手就不是「按住」）；
 *  · [span] 够 [holdMinMs]：按满了这一档的按住下限；
 *  · [isStarter]：先按的那颗正是这一档的**起点键**（`ComboKey.starterKey`）；
 *  · [starterLongPassed] 为假：那颗键的长按**还没判出来** —— 判过就已经执行过它自己的动作，
 *    再认组合就是一次按压两个动作。
 */
fun pairHoldMatches(
    span: Long,
    stillDown: Boolean,
    holdMinMs: Long,
    isStarter: Boolean,
    starterLongPassed: Boolean,
): Boolean = stillDown && span >= holdMinMs && isStarter && !starterLongPassed

/**
 * 逐档试出这一对到底归哪一档 —— **按住那一档优先**。
 *
 *  同一组键可以配几档、各一种触发方式（`ComboBinding.isWait`），只看成员数最大的那一档会把另一种
 *  方式整条遮掉；而两档同时命中时（那一颗既在窗口内、又按满了按住下限）取**按住**那档 ——
 *  它的前提更严，是更明确的表态，等窗口那档留给「没按住那么久」的情形。
 *
 *  实现就是「命中就记下，命中按住档立刻停」：等窗口档继续往后看（后面的档可能更合适），
 *  按住档不看了（不会再有比它更严的）。返回值是最后命中的那一档，`null` = 一档都不配。
 *
 *  @param isWait 这一档是不是等待档（`false` = 按住档）
 *  @param matches 逐档判据（`pairWaitMatches` / `pairHoldMatches` 按这一档的方式挑一个）
 */
fun <T> pickPairHit(
    candidates: List<T>,
    isWait: (T) -> Boolean,
    matches: (T) -> Boolean,
): T? {
    var hit: T? = null
    for (b in candidates) {
        if (!matches(b)) continue
        hit = b
        if (!isWait(b)) break
    }
    return hit
}

/**
 * [absorb] 的升级判据（**等窗口那一支**）：已经成立的那一档，能不能被更宽的一档顶掉。
 *
 *  三条：成员更多（真子集才叫「更宽」）、[elapsedMs] 还没超出那一档的整串跨度
 *  （超了就算按下去也来不及）、以及 —— 由调用方先短的 —— 那一档必须是**等窗口**那种。
 *  **按住那一支不走这里**：它的起点键按住多久都不计时，量的是「后续成员还在不在窗口内」
 *  （`ComboDetector.upgradeTo` 里的 `holdReady` + `followersFresh`），与这一条的时间基准不同。
 *
 *  **跨度按最早那一颗算**（调用方传的是 `now - firstDown`）：成员顺序是配置里的键序，
 *  与真实按下顺序不一定一致，拿首键算会把跨度算小。
 *
 *  @param candidateSize 更宽那一档的成员数
 *  @param currentSize 当前这一档的成员数
 *  @param ownSpanMs 更宽那一档自己的整串跨度
 */
fun isUpgrade(
    candidateSize: Int,
    currentSize: Int,
    elapsedMs: Long,
    ownSpanMs: Long,
): Boolean = candidateSize > currentSize && elapsedMs <= ownSpanMs
