package com.lookie.opluskey.ui.state.binding

import com.lookie.opluskey.ui.state.signal.PendingAsk

/**
 * 「停用 / 启用」之前的那一问 —— 键与组合的连带关系。
 *
 *  用户口径（三条一起看才闭环）：
 *   · **停用键** ⇒ 含它的组合必然也停，但**先说清有哪几个**（不弹窗的话，组合列表会忽然多出
 *     几行「已停用」，用户不知道是这次按出来的）；
 *   · **启用键** ⇒ 含它的组合不自动开（用户可能正想关掉它们），问一句；
 *   · **启用组合** ⇒ 它成员的键若还关着，这组根本跑不起来，同样先问一句要不要把键也开。
 *
 *  ## 为什么分成「一定要改的」与「一起改的」
 *
 *  [keys] / [combos] 是用户**明确点名的**那几项，答「否」也照改；[extraKeys] / [extraCombos]
 *  是「连带」的那一批，只有答「是」才跟着动。这样三种问法共用一句语义，否定也不会把用户
 *  点名要改的东西一并取消（那会让人以为没点上）。
 *
 *  状态住在这里而不是某个页面：触发点在三处（按键列表的开关、按键绑定页的 ⋯ 菜单、组合页的
 *  开关），窗画在各自页面上 —— 几处共用一个待确认对象，逻辑只需要一份。
 *
 *  五项读成一个 [Payload]（而不是五个 `mutableStateOf`）：`ask()` 中间那一帧不能让人读到
 *  「问的是启用、要改的键还是空的」。归零也只有一处（[PendingAsk.clear]）。
 */
object KeysAsk {
    /** 这一批最终是**停用**还是**启用**。 */
    enum class Kind { DISABLE, ENABLE }

    /** 一次待确认的载荷 —— 五项一起挂、一起收。 */
    class Payload(
        val kind: Kind,
        /** 用户点名的键（答「否」也照改）。 */
        val keys: Set<String>,
        /** 用户点名的组合（答「否」也照改）。 */
        val combos: Set<String>,
        /** 连带一起改的键（组合的成员键）—— 只有答「是」才动。 */
        val extraKeys: Set<String>,
        /** 连带一起改的组合 —— 只有答「是」才动。 */
        val extraCombos: Set<String>,
        /** 这一问从哪来（决定回执落在哪一格提示位）。 */
        val slot: String,
    )

    private val pending = PendingAsk<Payload>()

    /** 有没有人在等回答。 */
    val asking: Boolean get() = pending.asking

    /** 这一批最终是**停用**还是**启用**（`null` = 没有待确认的事）。 */
    val kind: Kind? get() = pending.pending?.kind

    /** 用户点名的键（答「否」也照改）。 */
    val keys: Set<String> get() = pending.pending?.keys.orEmpty()

    /** 用户点名的组合（答「否」也照改）。 */
    val combos: Set<String> get() = pending.pending?.combos.orEmpty()

    /** 连带一起改的键（组合的成员键）—— 只有答「是」才动。 */
    val extraKeys: Set<String> get() = pending.pending?.extraKeys.orEmpty()

    /** 连带一起改的组合 —— 只有答「是」才动。 */
    val extraCombos: Set<String> get() = pending.pending?.extraCombos.orEmpty()

    /** 这一问从哪来（决定回执落在哪一格提示位）。 */
    val slot: String get() = pending.pending?.slot ?: "keys_toggle"

    fun ask(
        kind: Kind,
        keys: Set<String>,
        combos: Set<String> = emptySet(),
        extraKeys: Set<String> = emptySet(),
        extraCombos: Set<String> = emptySet(),
        slot: String = "keys_toggle",
    ) {
        pending.set(Payload(kind, keys, combos, extraKeys, extraCombos, slot))
    }

    fun clear() = pending.clear()
}
