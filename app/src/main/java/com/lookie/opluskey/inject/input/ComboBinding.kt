package com.lookie.opluskey.inject.input

import com.lookie.opluskey.core.judge.matchesOrderedTail

/**
 * 判定器眼里的**一档组合** —— 只说到「哪几颗键、按什么形态、判起来要什么数」。
 *
 * ## 为什么不是 `ComboKey`
 *
 *  `ComboKey` 是业务模型：它带着动作（`longRelease` / 场景块）、带着「这一档怎么执行」的开关、
 *  带着独立设置与继承关系。判定层读它只为了四件事 —— **成员是谁、是不是按住档、长按怎么算、
 *  这一档是不是按顺序触发**。剩下的（拿它当身份、交给宿主执行）都不需要判定层理解它：
 *  身份就落到 [key] 这个原对象上，原样带出去。
 *
 *  所以这一层只包一层壳：判定处读的是这几个**判据字段**，不是配置语义；装配方把 `ComboKey`
 *  翻成 [ComboBinding]，判定器回传 [key] 时调用方再翻回去。翻译只有一处（`inject/key/GestureBridge`）。
 *
 * ## 判据字段各自管什么
 *
 * @param key 这一档在业务里的那个对象（判定层只把它当**身份**传递，从不读它）
 * @param name 日志里怎么称呼这一档 —— 取的是**配置里的档位 id**（`cmb3` 这类）：日志要能对上
 *  导出的配置与广播记录，成员名由 `members[*].name` 各自给
 * @param isWait 这一档是**等待档**吗（几颗键在窗口内依次落下；判据本身与 [isHold] 互补）
 * @param ordered 这一档要求**按顺序触发**吗（装配方从触发方式取出，判定层只按位比）
 * @param members 成员键（顺序即按下顺序）
 * @param isHold `true` = 按住档（首键要按住），`false` = 等待档（几颗键在窗口内依次按下）
 * @param hasLong 这一档**结构上**有没有长按这一格（按顺序触发的那几档没有）
 * @param longActionExists 这一档的长按**真的有事可做**吗（直接绑了动作，或某块场景里配过）——
 *  与 [hasLong] 不是一回事：有这一格不等于这一格配了东西。判「成员连击要不要给组合长按让位」
 *  必须看这一位，否则组合长按派发出去没人执行、成员连击那一下又已经被让掉，两边都空
 * @param longPressMs 这一档的长按阈值
 * @param longFireOnRelease 长按松手才触发
 * @param longReleaseActionExists 长按之后松手还有事可做（决定要不要派发 [Gesture.Release]）
 * @param longRepeats 长按排不排连发
 * @param longRepeatIntervalMs 长按连发的间隔
 * @param starterKeepLong 首键松手后仍执行它自己的长按吗（**这一档写过的那一值**，`null` = 没写过、
 *  跟随公共）—— 只进日志，判据走 [DetectorBinds.StarterKeepLong.enabled]
 * @param useOwnSettings 这一档走自己的独立设置（日志用；判据本身已经解析成上面那几格）
 * @param memberChain 成员连击那一份参数（`null` = 这一档不支持成员连击）
 */
@Suppress("LongParameterList") // 这一档要的就是「把判定读的那几件事一次摆齐」：拆成几个小类只会让装配方多套一层，判据本身还是这些字段。
internal class ComboBinding(
    val key: Any,
    val name: String,
    val isWait: Boolean,
    val ordered: Boolean,
    val members: List<KeyRef>,
    val isHold: Boolean,
    val hasLong: Boolean,
    val longActionExists: Boolean,
    val longPressMs: Long,
    val longFireOnRelease: Boolean,
    val longReleaseActionExists: Boolean,
    val longRepeats: Boolean,
    val longRepeatIntervalMs: Long,
    val starterKeepLong: Boolean?,
    val useOwnSettings: Boolean,
    val memberChain: ChainSpec?,
) {
    /** 这一档按住时为真的「首键」—— 成员清单的头一颗；空成员不成档，返回 `null`。 */
    val starter: KeyRef? get() = members.firstOrNull()

    /**
     * **首键之后**的那些成员 —— 与 `ComboKey.followers` 同一个意思。
     *
     *  **别拿 `members` 顶替它**：两颗组合是「`members` 有 2 个、`followers` 有 1 个」。
     *  判「这一档只有一颗成员键」（成员连击的前提）时写成 `members.size == 1` 会永远不成立
     *  （组合至少两颗），判「要连击的那颗成员」时写成 `members.first()` 拿到的是首键自己
     *  —— 两处都真机上踩过（2026-11-18，按住组合的多击手势整条失效）。
     */
    val followers: List<KeyRef> get() = members.drop(1)

    /**
     * [sequence] 是不是按 [members] 的**先后顺序**按下这一档的全部成员 —— 只给 [ordered] 那一档用。
     *
     *  [sequence] 是「真实按下顺序」的键序列（判定侧按按下时刻排好）。这一档要求它的
     *  **最后几位**与 [members] 逐位相同 —— 序列里多出来的那几颗不碍事：可能是这一组开始之前
     *  按过的别的键（还在同一个扫描窗口里），也可能是更宽一档的成员。空序列 / 长度不足都算不成立。
     *
     *  **比的必须是结尾而不是开头**：当前这一下一定是成员里的最后一颗（组合在它落下那一刻成立），
     *  而扫描窗口里可能还兜着这组开始之前按过的键。按开头比时，前面多一颗（比如先点了一下音量键）
     *  就会把整条有序组合判成「顺序不对」，用户看到的是这一组没生效、只落下两颗键各自的单击。
     *
     *  判据本体在 `core/judge` 的 `matchesOrderedTail` —— 业务侧 `ComboKey.matchesOrdered` 调的是
     *  同一份，两边不会走岔。
     */
    fun matchesOrdered(sequence: List<KeyRef>): Boolean = !ordered || matchesOrderedTail(members, sequence)
}
