package com.lookie.opluskey.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
 *  状态住在这里而不是某个页面：触发点在两处（按键列表右下角那颗开关、按键绑定页的 ⋯ 菜单），
 *  窗画在各自页面上 —— 两处共用一个待确认对象，逻辑只需要一份。
 */
object KeysAsk {
    /** 这一批最终是**停用**还是**启用**。 */
    enum class Kind { DISABLE, ENABLE }

    var kind: Kind? by mutableStateOf(null)

    /** 用户点名的键（答「否」也照改）。 */
    var keys: Set<String> by mutableStateOf(emptySet())

    /** 用户点名的组合（答「否」也照改）。 */
    var combos: Set<String> by mutableStateOf(emptySet())

    /** 连带一起改的键（组合的成员键）—— 只有答「是」才动。 */
    var extraKeys: Set<String> by mutableStateOf(emptySet())

    /** 连带一起改的组合 —— 只有答「是」才动。 */
    var extraCombos: Set<String> by mutableStateOf(emptySet())

    /** 这一问从哪来（决定回执落在哪一格提示位）。 */
    var slot: String by mutableStateOf("keys_toggle")

    fun ask(
        kind: Kind,
        keys: Set<String>,
        combos: Set<String> = emptySet(),
        extraKeys: Set<String> = emptySet(),
        extraCombos: Set<String> = emptySet(),
        slot: String = "keys_toggle",
    ) {
        this.kind = kind
        this.keys = keys
        this.combos = combos
        this.extraKeys = extraKeys
        this.extraCombos = extraCombos
        this.slot = slot
    }

    fun clear() {
        kind = null
        keys = emptySet()
        combos = emptySet()
        extraKeys = emptySet()
        extraCombos = emptySet()
    }
}
