package com.lookie.opluskey.core.intercept

// 拦截侧的**归属判据** —— 「这一下归模块哪一档」「压不压住、压到什么时候」。
//
//  与 `core/judge` 同一族（纯函数、不碰状态、不读时钟、不调 Android API），但问的不是
//  「怎么判手势」而是「这一下按键归谁」—— 所以单独一个包，`judge` 那边只留手势算术。
//  两句判据原先都是 `SideKeyInterceptor.decide` / `ComboOnlyNative.onDown` 里的 `when` 条件，
//  `:app` 没有 test 源集，而这两处正是「按一下两个动作」「按了没反应」的直接来源。

/**
 * 这一下归模块的哪一档（前提：已经过了认领闸门 —— 码认得出来、不是保留码、这颗键开着接管）。
 *
 *  `comboOnly` **压过 ROM 手势码**：那一档要的正是「单键仍归模块之外」，
 *  而 ROM 判好的手势码本身就是单键手势 —— 认下来就与这一位反着来。
 */
enum class ClaimKind {
    /** 进时长判定（单击 / 长按 / 多击那一整套）。 */
    CLAIMED,

    /** 「只参与非首键的按住组合」：不判单键，压住这一下再按窗口还回去。 */
    COMBO_ONLY,

    /** ROM 已经判好的手势码：直接按手势派发，不进时长判定。 */
    ROM_GESTURE,

    /** ROM 那几族里的**中间态码**：吞掉，既不派发、也不放行给 ROM。 */
    ROM_SWALLOW,
}

fun claimKindOf(
    comboOnly: Boolean,
    romGesture: Boolean,
    romMember: Boolean,
): ClaimKind =
    when {
        comboOnly -> ClaimKind.COMBO_ONLY
        romGesture -> ClaimKind.ROM_GESTURE
        romMember -> ClaimKind.ROM_SWALLOW
        else -> ClaimKind.CLAIMED
    }

/**
 * 这一下按下要不要**先压住**（「只参与非首键的按住组合」那颗键），以及压到哪一步。
 *
 *  三档的差别只在「什么时候还回去」，见 `ComboOnlyNative` 的类注释：
 *  · [UNTIL_RELEASE]：这一颗可能是某组按住组合里**先按住的那一颗** —— 判据只看它是否还按着，
 *    按多久都算，所以只能压到抬手。代价是这颗键在这一档里不再出原生长按；
 *  · [UNTIL_WINDOW]：它只是等窗口那几档的成员 —— 窗口到点还没凑成，就不必再等了；
 *  · [PASS]：两样都不是，那这一下**永远配不上**（另一颗若正按着，组合在上面已经当场成立），
 *    原生事件原样交给系统，一秒都不压。
 */
enum class HoldStep {
    /** 一下都不压：原生事件原样交给系统。 */
    PASS,

    /** 已经归组合了：永久吞，原生事件一条都不放。 */
    TAKEN,

    /** 压到抬手再还回去（按住那档的起点键）。 */
    UNTIL_RELEASE,

    /** 压到窗口到点再还回去（等窗口那几档的成员）。 */
    UNTIL_WINDOW,
}

/**
 * @param taken 这一下已经被组合当场认领（`ComboDetector.Down.TAKEN`）
 * @param windowMember 这颗键参与的某组认窗口那条（它会被接在后面）
 * @param holdStarter 这颗键可能是某组按住组合里先按住的那一颗
 */
fun holdStepOf(
    taken: Boolean,
    windowMember: Boolean,
    holdStarter: Boolean,
): HoldStep =
    when {
        taken -> HoldStep.TAKEN

        //  「先按住的起点键」优先于「只是窗口成员」：同一颗键两样都占时，压到抬手才还 ——
        //  按窗口到点就漏出原生事件的话，之后凑成的按住组合只能是「一次按压两个动作」。
        holdStarter -> HoldStep.UNTIL_RELEASE

        windowMember -> HoldStep.UNTIL_WINDOW

        else -> HoldStep.PASS
    }
